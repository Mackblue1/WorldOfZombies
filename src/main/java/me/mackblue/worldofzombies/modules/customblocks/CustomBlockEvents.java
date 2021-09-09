package me.mackblue.worldofzombies.modules.customblocks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import me.mackblue.worldofzombies.util.MultiBlockChangeWrap;
import me.mackblue.worldofzombies.WorldOfZombies;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public class CustomBlockEvents implements Listener {

    private final WorldOfZombies main;
    private final CustomBlockHelper helper;
    private final Logger console;
    private final ProtocolManager pm;

    private FileConfiguration config;
    private FileConfiguration customBlockConfig;

    private int debug;

    //private Map<Player, MultiBlockChangeWrap[][][]> subChunkList = new HashMap<>();
    private final Map<String, String> idToDefinitionFilePath;
    private final Map<String, YamlConfiguration> idToDefinitionFile;

    //constructor to initialize fields and load custom block config file
    public CustomBlockEvents(WorldOfZombies main, ProtocolManager pm) {
        this.main = main;
        this.console = main.getLogger();
        this.pm = pm;

        idToDefinitionFilePath = new HashMap<>();
        idToDefinitionFile = new HashMap<>();

        helper = new CustomBlockHelper(main, this);

        reload();
        MultiBlockChangeWrap.init(this.pm, console, debug);
        chunkLoadListener();
        blockChangeListener();
        multiBlockChangeListener();
    }

    //when a player places a block, log the location and disguised-block, and set the server block to actual-data if it exists in the definition
    @EventHandler
    public void blockPlaceEvent(BlockPlaceEvent event) {
        //console.info(ChatColor.DARK_PURPLE + "BPE");
        helper.placeCustomBlock(event.getBlock(), event.getItemInHand(), event.getPlayer(), false);
    }

    //very similar to BlockPlaceEvent handler, but logs multiple placed blocks instead of just one
    @EventHandler
    public void multiBlockPlaceEvent(BlockMultiPlaceEvent event) {
        //console.info(ChatColor.DARK_PURPLE + "MBPE");
        List<BlockState> replacedBlockStates = event.getReplacedBlockStates();
        Block first = replacedBlockStates.get(0).getBlock();
        Block second = replacedBlockStates.get(1).getBlock();

        if (first.getY() > second.getY() && customBlockConfig.getBoolean("Global.fix-inverted-multi-blocks", true)) {
            helper.placeCustomBlock(first, event.getItemInHand(), event.getPlayer(), true);
            helper.placeCustomBlock(second, event.getItemInHand(), event.getPlayer(), false);
            return;
        }

        helper.placeCustomBlock(first, event.getItemInHand(), event.getPlayer(), false);
        helper.placeCustomBlock(second, event.getItemInHand(), event.getPlayer(), true);
    }

    //when a player clicks (damages) a block, break the block if its "instant-break" option is true
    @EventHandler
    public void instaBreakEvent(BlockDamageEvent event) {
        String id = (String) getCustomBlockHelper().getLoggedObjectFromLocation(event.getBlock().getLocation(), "id");
        if (id != null) {
            YamlConfiguration yaml = idToDefinitionFile.get(id);
            if (yaml != null && yaml.getBoolean(id + ".block.options.instant-break", false)) {
                Player player = event.getPlayer();
                //event.setInstaBreak(true);
                //console.info(ChatColor.GOLD + "insta");

                Object secondBlockObj = helper.getLoggedObjectFromLocation(event.getBlock().getLocation(), "secondBlock", false);
                boolean secondBlock = secondBlockObj != null && (boolean) secondBlockObj;
                helper.destroyLoggedBlock(event, true, event.getBlock().getLocation(), player.getGameMode() == GameMode.SURVIVAL && !secondBlock, player, null, true);
            }
        }
    }

    //cancels any xp that would normally drop from a custom block being broken (like an ore block)
    @EventHandler
    public void blockDropXpEvent(BlockExpEvent event) {
        String id = (String) helper.getLoggedObjectFromLocation(event.getBlock().getLocation(), "id");
        if (event.getExpToDrop() != 0) {
            YamlConfiguration yaml = idToDefinitionFile.get(id);
            if (yaml != null && yaml.getBoolean(id + ".block.drops.enabled", true) && yaml.getBoolean(id + ".block.drops.cancel-xp")) {
                event.setExpToDrop(0);
            }
        }

    }

    //handler to check for a player breaking an unbreakable block
    @EventHandler
    public void blockBreakEvent(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();
        //console.info(ChatColor.GOLD + "block break");

        String id = (String) helper.getLoggedObjectFromLocation(loc, "id");
        YamlConfiguration yaml = idToDefinitionFile.get(id);
        if (yaml != null) {
            if (yaml.getBoolean(id + ".block.options.unbreakable", false)) {
                if (player.getGameMode() == GameMode.SURVIVAL) {
                    event.setCancelled(true);
                    if (debug >= 3) {
                        console.info(ChatColor.BLUE + player.getName()  + " was not able to break the custom block \"" + id + "\" because it is unbreakable");
                        return;
                    }
                }
            }
        }

        //fix for blocks not being correctly destroyed sometimes
        if (id != null) {
            //console.info(ChatColor.DARK_PURPLE + "cancelled BBE and spawning new BDIE");
            event.setCancelled(true);
            blockDropItemEvent(new BlockDropItemEvent(block, block.getState(), player, new ArrayList<>()));
        }
    }

    //custom block dropping logic based on options in a custom block's "block.drops" definition section
    @EventHandler
    public void blockDropItemEvent(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        List<Item> originalDrops = event.getItems();
        //console.info(ChatColor.GOLD + "drop items");

        Object secondBlockObj = helper.getLoggedObjectFromLocation(loc, "secondBlock", false);
        boolean secondBlock = secondBlockObj != null && (boolean) secondBlockObj;
        helper.destroyLoggedBlock(event, false, loc, (player.getGameMode() == GameMode.SURVIVAL || !originalDrops.isEmpty()) && !secondBlock, player, originalDrops, true);
    }

    //block dropping/breaking logic for blocks broken indirectly (block under them is broken, multi block break)
    @EventHandler
    public void blockDestroyEvent(BlockDestroyEvent event) {
        Location loc = event.getBlock().getLocation();
        //console.info(ChatColor.GOLD + "destroy");

        Object secondBlockObj = helper.getLoggedObjectFromLocation(loc, "secondBlock", false);
        boolean secondBlock = secondBlockObj != null && (boolean) secondBlockObj;
        helper.destroyLoggedBlock(event, true, loc, !secondBlock, null, null, event.playEffect());
    }


    //handles custom block drops during explosions
    @EventHandler
    public void entityExplodeEvent(EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();
        List<Block> blocksCopy = new ArrayList<>(blocks);
        double yield = event.getYield();

        for (Block block : blocksCopy) {
            Location loc = block.getLocation();
            String id = (String) helper.getLoggedObjectFromLocation(loc, "id");

            if (id != null) {
                blocks.remove(block);
                YamlConfiguration yaml = idToDefinitionFile.get(id);
                if (yaml != null && !yaml.getBoolean(id + ".block.options.blast-resistant", false)) {
                    if (!yaml.getBoolean(id + ".block.options.unbreakable", false)) {
                        //removing block from explosion and setting to air works, but is there a better way to remove original exploded block drops?

                        if (yield > 0) {
                            helper.destroyLoggedBlock(event, false, loc, Math.random() < yield, null, null, false);
                        }
                    } else {
                        if (debug >= 3) {
                            console.info(ChatColor.BLUE + "The custom block \"" + id + "\" was not broken in an explosion because it is unbreakable");
                        }
                    }
                }
            }
        }
    }

    //note: event.getBlocks() returns an immutable list, so how can the blocks be treated like obsidian and not moved?
    //calls handleMovedBlocks() to do most of the work, and cancels the event if one or more blocks has the config option "cancel-piston
    @EventHandler
    public void pistonExtendEvent(BlockPistonExtendEvent event) {
        if (helper.handleMovedBlocks(event.getBlocks(), event.getDirection(), true)) {
            event.setCancelled(true);
        }
    }

    //calls handleMovedBlocks() to check if the event should be cancelled, and cancels if it should
    @EventHandler
    public void pistonRetractEvent(BlockPistonRetractEvent event) {
        if (helper.handleMovedBlocks(event.getBlocks(), event.getDirection(), false)) {
            event.setCancelled(true);
        }
    }

    //when water/lava flows into a custom block to break it, destroy the block unless the block's "disable-fluid-destroy" is true
    @EventHandler
    public void liquidFlowEvent(BlockFromToEvent event) {
        Block block = event.getToBlock();
        String id = (String) helper.getLoggedObjectFromLocation(block.getLocation(), "id");
        if (id != null) {
            YamlConfiguration yaml = idToDefinitionFile.get(id);
            if (yaml != null) {
                if (!yaml.getBoolean(id + ".block.options.disable-fluid-destroy", false)) {
                    if (!yaml.getBoolean(id + ".block.options.unbreakable", false)) {
                        helper.destroyLoggedBlock(event, false, block.getLocation(), true, null, null, false);
                    } else {
                        event.setCancelled(true);
                        if (debug >= 3) {
                            console.info(ChatColor.BLUE + "The custom block \"" + id + "\" was not broken by a liquid because it is unbreakable");
                        }
                    }
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

    //listens for MAP_CHUNK (ChunkData) packets and calls the block loader for that chunk after a delay specified in the customBlockConfig
    public void chunkLoadListener() {
        pm.addPacketListener(
                new PacketAdapter(main, ListenerPriority.MONITOR, PacketType.Play.Server.MAP_CHUNK) {

                    @Override
                    public void onPacketSending(PacketEvent event) {
                        Player player = event.getPlayer();
                        int chunkX = event.getPacket().getIntegers().read(0);
                        int chunkZ = event.getPacket().getIntegers().read(1);
                        Chunk chunk = player.getWorld().getChunkAt(chunkX, chunkZ);

                        int delay = customBlockConfig.getInt("Global.chunk-load-delay", 5);
                        if (delay < 0) {
                            delay = 0;
                        }

                        if (chunk.isLoaded()) {
                            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
                                int blocks = helper.loadLoggedBlocksInChunk(player, chunk);

                                if (debug >= 1 && blocks != 0) {
                                    console.info(ChatColor.DARK_GREEN + "Loaded " + blocks + " blocks in the chunk at " + chunkX + ", " + chunkZ + " in a custom MultiBlockChange packet by " + player.getName());
                                }
                            }, delay);
                        }
                    }
                }
        );
    }


    //listens for BlockChange packets and if its position is logged, edits the packet to contain the logged disguised BlockData
    public void blockChangeListener() {
        pm.addPacketListener(
                new PacketAdapter(main, PacketType.Play.Server.BLOCK_CHANGE) {

                    @Override
                    public void onPacketSending(PacketEvent event) {
                        Player player = event.getPlayer();
                        PacketContainer packet = event.getPacket();
                        WrappedBlockData wrappedBlockData = packet.getBlockData().read(0);
                        BlockPosition position = packet.getBlockPositionModifier().read(0);

                        World world = player.getWorld();
                        int x = position.getX();
                        int y = position.getY();
                        int z = position.getZ();
                        Location loc = new Location(world, x, y, z);
                        Block block = world.getBlockAt(loc);
                        String locString = world.getName() + ", " + x + ", " + y + ", " + z;

                        //console.info(ChatColor.GOLD + "BC:  " + position + "   " + wrappedBlockData.getType() + "     " + block.getBlockData().getMaterial());

                        String id = (String) helper.getLoggedObjectFromLocation(loc, "id");
                        Object secondBlockObj = helper.getLoggedObjectFromLocation(loc, "secondBlock", false);
                        boolean secondBlock = secondBlockObj != null && (boolean) secondBlockObj;
                        String disguisedPathEnd = secondBlock ? "disguised-block2" : "disguised-block";
                        if (id != null) {
                            if (wrappedBlockData.equals(WrappedBlockData.createData(block.getBlockData()))) {
                                BlockData disguisedData = helper.unLogBlockOrCreateDisguisedBlockData(loc, id, secondBlock);
                                if (disguisedData != null) {
                                    if (disguisedData.getMaterial() == Material.AIR) {
                                        if (debug >= 3){
                                            console.warning(ChatColor.YELLOW + "Did not edit the BlockData in an outgoing BlockChange packet for the custom block \"" + id + "\" at " + locString + " because its source \"" + disguisedPathEnd + "\" is empty");
                                        }
                                    } else {
                                        packet.getBlockData().write(0, WrappedBlockData.createData(disguisedData));

                                        String blockDataString = (String) helper.getLoggedObjectFromLocation(loc, "disguised-block");
                                        if (!disguisedData.getAsString().equals(blockDataString)) {
                                            helper.setLoggedInfoAtLocation(loc, "disguised-block", disguisedData.getAsString());
                                        }

                                        if (debug >= 4) {
                                            console.info(ChatColor.AQUA + "Edited the BlockData in an outgoing BlockChange packet for the custom block \"" + id + "\" at " + locString + " by " + player.getName());
                                        }
                                    }
                                }
                            } else if (debug >= 4) {
                                console.warning(ChatColor.YELLOW + "(THIS CAN PROBABLY BE IGNORED IF THIS IS DURING A CHUNK BEING LOADED) Did not load the \"" + disguisedPathEnd + "\" for the \"" + id + "\" at " + locString + " because the BlockData in a BlockChange packet for that location does not match the BlockData of the real block");
                            }
                        }
                    }
                }
        );
    }

    //listens for MultiBlockChange packets edits the packet to contain the logged disguised BlockData for any included logged blocks
    public void multiBlockChangeListener() {
        pm.addPacketListener(
                new PacketAdapter(main, PacketType.Play.Server.MULTI_BLOCK_CHANGE) {

                    @Override
                    public void onPacketSending(PacketEvent event) {
                        Player player = event.getPlayer();
                        World world = player.getWorld();
                        PacketContainer packet = event.getPacket();
                        short[] shortsArr = packet.getShortArrays().read(0);
                        WrappedBlockData[] blockDataArr = packet.getBlockDataArrays().read(0);
                        BlockPosition subChunkPos = packet.getSectionPositions().read(0);
                        int offsetX = (subChunkPos.getX() << 4);
                        int offsetY = (subChunkPos.getY() << 4);
                        int offsetZ = (subChunkPos.getZ() << 4);
                        int blocks = 0;

                        for (int i = 0; i < shortsArr.length; i++) {
                            short location = shortsArr[i];
                            int absoluteX = offsetX + (location >> 8 & 0xF);
                            int absoluteY = offsetY + (location & 0xF);
                            int absoluteZ = offsetZ + (location >> 4 & 0xF);
                            Location loc = new Location(world, absoluteX, absoluteY, absoluteZ);
                            Block block = world.getBlockAt(loc);
                            String locString = world.getName() + ", " + absoluteX + ", " + absoluteY + ", " + absoluteZ;
                            //console.info(ChatColor.GOLD + "MBC:  " + loc + "   packet:" + blockDataArr[i].getType() + "     real:" + block.getBlockData().getMaterial());

                            String id = (String) helper.getLoggedObjectFromLocation(loc, "id");
                            Object secondBlockObj = helper.getLoggedObjectFromLocation(loc, "secondBlock", false);
                            boolean secondBlock = secondBlockObj != null && (boolean) secondBlockObj;
                            String disguisedPathEnd = secondBlock ? "disguised-block2" : "disguised-block";
                            if (id != null) {
                                if (blockDataArr[i].equals(WrappedBlockData.createData(block.getBlockData()))) {
                                    BlockData disguisedData = helper.unLogBlockOrCreateDisguisedBlockData(loc, id, secondBlock);
                                    if (disguisedData != null) {
                                        if (disguisedData.getMaterial() == Material.AIR) {
                                            if (debug >= 3) {
                                                console.warning(ChatColor.YELLOW + "Did not edit the BlockData for the custom block \"" + id + "\" at " + locString + " in a MultiBlockChange packet because its source \"" + disguisedPathEnd + "\" is empty");
                                            }
                                        } else {
                                            blockDataArr[i] = WrappedBlockData.createData(disguisedData);
                                            blocks++;

                                            String blockDataString = (String) helper.getLoggedObjectFromLocation(loc, "disguised-block");
                                            if (!disguisedData.getAsString().equals(blockDataString)) {
                                                helper.setLoggedInfoAtLocation(loc, "disguised-block", disguisedData.getAsString());
                                            }

                                            if (debug >= 4) {
                                                console.info(ChatColor.AQUA + "Edited the BlockData for the custom block \"" + id + "\" at " + locString + " in a MultiBlockChange packet by " + player.getName());
                                            }
                                        }
                                    }
                                } else if (debug >= 4) {
                                    console.warning(ChatColor.YELLOW + "(THIS CAN PROBABLY BE IGNORED IF THIS IS DURING A CHUNK BEING LOADED) Did not load the \"" + disguisedPathEnd + "\" for the \"" + id + "\" at " + locString + " because the BlockData in a MultiBlockChange packet for that location does not match the BlockData of the real block");
                                }
                            }
                        }

                        packet.getBlockDataArrays().writeSafely(0, blockDataArr);
                        if (debug >= 1 && blocks != 0) {
                            console.info(ChatColor.DARK_GREEN + "Edited the BlockData of " + blocks + " blocks in a MultiBlockChange packet in the chunk at " + subChunkPos.getX() + ", " + subChunkPos.getZ() + " by " + player.getName());
                        }
                    }
                }
        );
    }

    //reloads the main and custom block configs, the custom block definition files, and the debug field
    public void reload() {
        main.createConfigs();
        config = main.getConfig();
        customBlockConfig = main.loadYamlFromFile(new File(main.getDataFolder(), "custom-blocks.yml"), false, false, debug, "");

        File customItemsDir = new File(main.getDataFolder() + File.separator + "CustomItems");
        if (customItemsDir.exists() && customItemsDir.isDirectory()) {
            if (customItemsDir.list().length == 0) {
                main.saveResource("CustomItems" + File.separator + "demo.yml", false);
                if (debug >= 1) {
                    console.info(ChatColor.DARK_AQUA + "Added the file WorldOfZombies\\CustomItems" + File.separator + "demo.yml because the CustomItems folder was empty");
                }
            }

            idToDefinitionFilePath.clear();
            idToDefinitionFile.clear();
            for (File file : FileUtils.listFiles(customItemsDir, new String[] {"yml"}, true)) {
                YamlConfiguration yaml = main.loadYamlFromFile(file, true, false, debug, "");
                yaml.getKeys(false).forEach(key -> {
                    if (yaml.isConfigurationSection(key)) {
                        if (idToDefinitionFilePath.containsKey(key)) {
                            console.severe(ChatColor.RED + "A duplicate definition for the custom block \"" + key + "\" was detected in the files " + idToDefinitionFilePath.get(key) + " and " + file.getPath());
                        }
                        idToDefinitionFilePath.put(key, file.getPath());
                        idToDefinitionFile.put(key, yaml);
                    }
                });
            }
        } else {
            main.saveResource("CustomItems" + File.separator + "demo.yml", false);
            console.info(ChatColor.DARK_AQUA + "Created the file CustomItems" + File.separator + "demo.yml because the CustomItems folder did not exist");
        }

        debug = customBlockConfig.getInt("Global.debug", 0);
        MultiBlockChangeWrap.setDebug(debug);
        helper.reload();
    }

    public CustomBlockHelper getCustomBlockHelper() {
        return helper;
    }

    public Map<String, String> getIdToDefinitionFilePath() {
        return idToDefinitionFilePath;
    }

    public Map<String, YamlConfiguration> getIdToDefinitionFile() {
        return idToDefinitionFile;
    }
}
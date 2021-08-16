package me.woz.customplugins.modules.customblocks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTItem;
import me.woz.customplugins.WorldOfZombies;
import me.woz.customplugins.util.MultiBlockChangeWrap;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
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
    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockPlaceEvent(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Chunk chunk = block.getChunk();
        World world = block.getWorld();

        ItemStack item = event.getItemInHand();
        NBTCompound wozItemComp = new NBTItem(item).getOrCreateCompound("WoZItem");
        String id = wozItemComp.getString("CustomItem");
        String sourceFilePath = idToDefinitionFilePath.get(id);

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        String path = "subChunk" + (block.getY() >> 4) + "." + x + "_" + y + "_" + z + "." + "id";
        String chunkString = world.getName() + ", " + chunk.getX() + ", " + chunk.getZ();
        String locString = world.getName() + ", " + x + ", " + y + ", " + z;

        //event.getPlayer().sendMessage(ChatColor.GREEN + "BPE:  " + block.getLocation() + "   " + block.getBlockData().getAsString());

        if (wozItemComp.getBoolean("IsCustomItem") && sourceFilePath != null) {

            YamlConfiguration sourceYaml = idToDefinitionFile.get(id);
            if (sourceYaml == null) {
                console.severe(ChatColor.RED + "An error occurred while trying to load the source file for the custom block \"" + id + "\"");
                return;
            }

            if (!sourceYaml.isConfigurationSection(id)) {
                console.severe(ChatColor.RED + "Could not log the custom block \"" + id + "\" because it does not exist in the file " + sourceFilePath);
                return;
            }
            String actual = sourceYaml.getString(id + ".block.actual-block");

            File file = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + world.getName(), "chunk." + chunk.getX() + "." + chunk.getZ() + ".yml");
            YamlConfiguration yaml = main.loadYamlFromFile(file, true, false, debug, "");
            if (yaml == null) {
                return;
            }

            if (!yaml.contains(path)) {
                if (actual != null) {
                    BlockData actualData = helper.createSyncedBlockData(block.getBlockData().getAsString(), id, false);
                    if (actualData != null) {
                        block.setBlockData(actualData);
                    }
                } else if (debug >= 3) {
                    console.warning(ChatColor.YELLOW + "Did not change the server-side block for the custom block \"" + id + "\" at " + locString + " because its source \"actual-block\" is empty");
                }

                yaml.set(path, id);
                if (debug >= 2) {
                    console.info(ChatColor.AQUA + event.getPlayer().getName() + " added the custom block \"" + id + "\" at " + locString);
                }
            }

            try {
                yaml.save(file);
                if (debug >= 2) {
                    console.info(ChatColor.DARK_AQUA + "Saved the file for the chunk at " + chunkString);
                }
            } catch (IOException e) {
                console.severe(ChatColor.RED + "Could not save the file for the chunk at " + chunkString);
            }
        } else if (wozItemComp.getBoolean("IsCustomItem") && sourceFilePath == null && debug >= 3) {
            console.warning(ChatColor.YELLOW + event.getPlayer().getName() + " placed " + block.getBlockData().getAsString() + " at " + locString + " which contains the tags \"IsCustomBlock:" + wozItemComp.getBoolean("IsCustomItem") + "\" and \"CustomBlock:" + id + "\", but \"" + id + "\" is not a valid custom block");
        }
    }

    //cancels any xp that would normally drop from a custom block being broken (like an ore block)
    @EventHandler
    public void blockDropXpEvent(BlockExpEvent event) {
        String id = helper.getLoggedStringFromLocation(event.getBlock().getLocation(), "id");
        if (event.getExpToDrop() != 0 && id != null) {
            YamlConfiguration yaml = idToDefinitionFile.get(id);
            if (yaml != null && yaml.getBoolean(id + ".block.drops.enabled", true) && yaml.getBoolean(id + ".block.drops.cancel-xp")) {
                event.setExpToDrop(0);
            }
        }

    }

    //custom block dropping logic based on options in a custom block's "block.drops" definition section
    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockDropItemEvent(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        List<Item> originalDrops = event.getItems();

        helper.destroyCustomBlock(loc, player.getGameMode() == GameMode.SURVIVAL || !originalDrops.isEmpty(), player, originalDrops);
    }

    //handles custom block drops during explosions
    @EventHandler
    public void entityExplodeEvent(EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();
        double yield = event.getYield();

        while (blocks.iterator().hasNext()) {
            Block block = blocks.iterator().next();
            Location loc = block.getLocation();
            String id = helper.getLoggedStringFromLocation(loc, "id");

            if (id != null) {
                blocks.remove(block);
                YamlConfiguration yaml = idToDefinitionFile.get(id);
                if (yaml != null && !yaml.getBoolean(id + ".block.options.blast-resistant", false)) {
                    //removing block from explosion and setting to air works, but is there a better way to remove original exploded block drops?

                    if (yield > 0) {
                        helper.destroyCustomBlock(loc, Math.random() < yield, null, null);
                    }
                }
            }
        }
    }

    //note: event.getBlocks() returns an immutable list, so how can the blocks be treated like obsidian and not moved?
    //calls handleMovedBlocks() to do most of the work, and cancels the event if one or more blocks has the config option "cancel-piston
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pistonExtendEvent(BlockPistonExtendEvent event) {
        if (helper.handleMovedBlocks(event.getBlocks(), event.getDirection(), true)) {
            event.setCancelled(true);
        }
    }

    //calls handleMovedBlocks() to check if the event should be cancelled, and cancels if it should
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pistonRetractEvent(BlockPistonRetractEvent event) {
        if (helper.handleMovedBlocks(event.getBlocks(), event.getDirection(), false)) {
            event.setCancelled(true);
        }
    }

    /*@EventHandler(priority = EventPriority.HIGHEST)
    public void middleClickEvent(InventoryCreativeEvent event) {

    }*/

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

                        if (chunk.isLoaded()) {
                            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
                                int blocks = helper.loadLoggedBlocksInChunk(player, chunk);

                                if (debug >= 1 && blocks != 0) {
                                    console.info(ChatColor.DARK_GREEN + "Loaded " + blocks + " blocks in the chunk at " + chunkX + ", " + chunkZ + " in a custom MultiBlockChange packet by " + player.getName());
                                }
                            }, customBlockConfig.getLong("Global.packet-send-delay"));
                        }
                    }
                }
        );
    }


    //listens for BlockChange packets and if its position is logged, edits the packet to contain the logged disguised BlockData
    public void blockChangeListener() {
        pm.addPacketListener(
                new PacketAdapter(main, ListenerPriority.HIGHEST, PacketType.Play.Server.BLOCK_CHANGE) {

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

                        String id = helper.getLoggedStringFromLocation(loc, "id");
                        if (id != null) {
                            if (wrappedBlockData.equals(WrappedBlockData.createData(block.getBlockData()))) {
                                BlockData disguisedData = helper.unLogBlockOrCreateDisguisedBlockData(loc, id);
                                if (disguisedData != null) {
                                    if (disguisedData.getMaterial() == Material.AIR) {
                                        if (debug >= 3){
                                            console.warning(ChatColor.YELLOW + "Did not edit the BlockData in an outgoing BlockChange packet for the custom block \"" + id + "\" at " + locString + " because its source \"disguised-block\" is empty");
                                        }
                                    } else {
                                        packet.getBlockData().write(0, WrappedBlockData.createData(disguisedData));
                                        String blockDataString = helper.getLoggedStringFromLocation(loc, "disguised-block");
                                        if (!disguisedData.getAsString().equals(blockDataString)) {
                                            helper.setLoggedInfoAtLocation(loc, "disguised-block", disguisedData.getAsString());
                                        }

                                        if (debug >= 4) {
                                            console.info(ChatColor.AQUA + "Edited the BlockData in an outgoing BlockChange packet for the custom block \"" + id + "\" at " + locString + " by " + player.getName());
                                        }
                                    }
                                }
                            } else if (debug >= 4) {
                                console.warning(ChatColor.YELLOW + "(THIS CAN PROBABLY BE IGNORED IF THIS IS DURING A CHUNK BEING LOADED) Did not load the \"disguised-data\" for the \"" + id + "\" at " + locString + " because the BlockData in a BlockChange packet for that location does not match the BlockData of the real block");
                            }
                        }
                    }
                }
        );
    }

    //listens for MultiBlockChange packets edits the packet to contain the logged disguised BlockData for any included logged blocks
    public void multiBlockChangeListener() {
        pm.addPacketListener(
                new PacketAdapter(main, ListenerPriority.HIGHEST, PacketType.Play.Server.MULTI_BLOCK_CHANGE) {

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

                            String id = helper.getLoggedStringFromLocation(loc, "id");
                            if (id != null) {
                                if (blockDataArr[i].equals(WrappedBlockData.createData(block.getBlockData()))) {
                                    BlockData disguisedData = helper.unLogBlockOrCreateDisguisedBlockData(loc, id);
                                    if (disguisedData != null) {
                                        if (disguisedData.getMaterial() == Material.AIR) {
                                            if (debug >= 3) {
                                                console.warning(ChatColor.YELLOW + "Did not edit the BlockData for the custom block \"" + id + "\" at " + locString + " in a MultiBlockChange packet because its source \"disguised-block\" is empty");
                                            }
                                        } else {
                                            blockDataArr[i] = WrappedBlockData.createData(disguisedData);
                                            blocks++;
                                            String blockDataString = helper.getLoggedStringFromLocation(loc, "disguised-block");
                                            if (!disguisedData.getAsString().equals(blockDataString)) {
                                                helper.setLoggedInfoAtLocation(loc, "disguised-block", disguisedData.getAsString());
                                            }

                                            if (debug >= 4) {
                                                console.info(ChatColor.AQUA + "Edited the BlockData for the custom block \"" + id + "\" at " + locString + " in a MultiBlockChange packet by " + player.getName());
                                            }
                                        }
                                    }
                                } else if (debug >= 4) {
                                    console.warning(ChatColor.YELLOW + "(THIS CAN PROBABLY BE IGNORED IF THIS IS DURING A CHUNK BEING LOADED) Did not load the \"disguised-data\" for the \"" + id + "\" at " + locString + " because the BlockData in a MultiBlockChange packet for that location does not match the BlockData of the real block");
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
        customBlockConfig = main.loadYamlFromFile(new File(main.getDataFolder(), "custom-block.yml"), false, false, debug, "");

        File customItemsDir = new File(main.getDataFolder() + File.separator + "CustomItems");
        if (customItemsDir.exists()) {
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

        debug = customBlockConfig.getInt("Global.debug");
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
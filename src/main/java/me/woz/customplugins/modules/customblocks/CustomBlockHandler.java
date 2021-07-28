package me.woz.customplugins.modules.customblocks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTItem;
import me.woz.customplugins.WorldOfZombies;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;

public class CustomBlockHandler implements Listener {

    private final WorldOfZombies main;
    private final Logger console;
    private final ProtocolManager pm;

    private FileConfiguration config;
    private FileConfiguration customBlockConfig;

    private int debug;

    //private Map<Player, MultiBlockChangeWrap[][][]> subChunkList = new HashMap<>();
    private final Map<String, String> idToFilePath;

    //constructor to initialize fields and load custom block config file
    public CustomBlockHandler(WorldOfZombies main, ProtocolManager pm) {
        this.main = main;
        this.console = main.getLogger();
        this.pm = pm;

        idToFilePath = new HashMap<>();

        reloadConfigs();
        chunkLoadListener();
        blockChangeListener();
        MultiBlockChangeWrap.init(this.pm, console, debug);
    }

    //adds all the blocks in the file for a chunk to their respective MultiBlockChange packets based on subChunk section
    public int loadCustomBlocksFromChunkFile(Player player, Chunk chunk) {
        int blockCount = 0;
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        World world = chunk.getWorld();
        String chunkString = world.getName() + ", " + chunkX + ", " + chunkZ;

        File chunkFolder = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + world.getName());
        if (main.loadYamlFromFile(chunkFolder, false, true, debug, ChatColor.BLUE + "No custom blocks were loaded because the database folder for this world does not exist") == null) {
            return 0;
        }

        File file = new File(chunkFolder.getPath(), "chunk." + chunkX + "." + chunkZ + ".yml");
        YamlConfiguration logYaml = main.loadYamlFromFile(file, false, false, debug, ChatColor.BLUE + "There are no custom blocks in the chunk at: " + chunkString + "   (no file)");
        if (logYaml == null) {
            return 0;
        }

        if (main.removeEmpty(file, true, debug)) {
            if (debug >= 4) {
                console.info(ChatColor.BLUE + "There are no custom blocks in the chunk at: " + chunkString + "   (removed empty file)");
            }
            return 0;
        }

        Set<String> sections = logYaml.getKeys(false);

        //if a top level key is a ConfigurationSection (by default they all will be), get and use data from its keys
        for (String sectionString : sections) {
            if (logYaml.isConfigurationSection(sectionString)) {
                ConfigurationSection configurationSection = logYaml.getConfigurationSection(sectionString);
                Set<String> data = configurationSection.getKeys(false);
                int subChunkY = Integer.parseInt(sectionString.substring("subChunk".length()));
                String subChunkString = world.getName() + ", " + chunkX + ", " + subChunkY + ", " + chunkZ;

                if (!data.isEmpty()) {
                    MultiBlockChangeWrap packet = new MultiBlockChangeWrap(chunkX, subChunkY, chunkZ);

                    for (String key : data) {
                        String[] locParts = key.split("_");
                        if (locParts.length < 3) {
                            console.severe(ChatColor.RED + "A custom block could not be loaded because the location key \"" + key + "\" in the subChunk at " + subChunkString + " is invalid");
                            continue;
                        }

                        Location loc = new Location(Bukkit.getWorld(file.getParentFile().getName()), Double.parseDouble(locParts[0]), Double.parseDouble(locParts[1]), Double.parseDouble(locParts[2]));
                        String locString = locParts[0] + ", " + locParts[1] + ", " + locParts[2];
                        String id = logYaml.getString(sectionString + "." + key);
                        BlockData disguisedData = createSyncedBlockData(world.getBlockAt(loc).getBlockData().getAsString(), id, true);

                        if (disguisedData == null) {
                            continue;
                        }

                        if (disguisedData.getMaterial().equals(Material.AIR) && debug >= 3) {
                            console.warning(ChatColor.YELLOW + "Did not load the \"" + id + "\" at " + locString + " because its source \"disguised_block\" is empty");
                        } else {
                            packet.addBlock(loc, disguisedData);
                            blockCount++;

                            if (debug >= 4) {
                                console.info(ChatColor.BLUE + "Loaded the \"" + id + "\" at " + locString + " in the subChunk at " + subChunkString + " by " + player.getName());
                            }
                        }
                    }
                    packet.sendPacket(player);
                }
            }
        }

        return blockCount;
    }

    //merges disguised_block or actual_block BlockData for a custom block with the sync states in originalBlockDataString
    //null return value indicates an error, and a BlockData with a Material of AIR indicates that the source disguised_block or actual_block does not exist
    public BlockData createSyncedBlockData(String originalBlockDataString, String id, boolean disguise) {
        YamlConfiguration sourceYaml = main.loadYamlFromFile(new File(idToFilePath.get(id)), false, false, debug, "");
        if (sourceYaml == null) {
            console.severe(ChatColor.RED + "The custom block \"" + id + "\" could not be loaded because its source file does not exist");
            return null;
        }

        if (!sourceYaml.isConfigurationSection(id + ".block")) {
            console.severe(ChatColor.RED + "The custom block \"" + id + "\" could not be loaded because its source \"block\" section is empty");
            return null;
        }
        ConfigurationSection blockSection = sourceYaml.getConfigurationSection(id + ".block");

        String path = disguise ? "disguised_block" : "actual_block";

        if (sourceYaml.contains(id + ".block." + path)) {
            BlockData newData;
            try {
                newData = Bukkit.createBlockData(sourceYaml.getString(id + ".block." + path));
                if (newData.getMaterial().isEmpty()) {
                    console.severe(ChatColor.RED + "The source \"" + path + "\" for the custom block \"" + id + "\" cannot be a type of air");
                    return null;
                }
            } catch (IllegalArgumentException e) {
                console.severe(ChatColor.RED + "Could not load the BlockData for the custom block + \"" + id + "\" because its source \"" + path + "\" is invalid");
                return null;
            }

            List<String> syncList = blockSection.getStringList("sync_states");
            String syncString = newData.getMaterial().toString().toLowerCase() + "[";

            for (String state : syncList) {
                if (originalBlockDataString.contains(state)) {
                    String afterState = originalBlockDataString.substring(originalBlockDataString.indexOf(state));
                    int stateEnd = afterState.indexOf("]");
                    if (afterState.contains(",")) {
                        if (afterState.indexOf(",") < stateEnd) {
                            stateEnd = afterState.indexOf(",");
                        }
                    }
                    syncString += afterState.substring(0, stateEnd) + ",";
                }
            }

            if (syncString.contains(",")) {
                syncString = syncString.substring(0, syncString.lastIndexOf(",")) + "]";
            } else {
                syncString = syncString.substring(0, syncString.length() - 1);
            }

            try {
                BlockData syncData = Bukkit.createBlockData(syncString);
                return newData.merge(syncData);
            } catch (IllegalArgumentException e) {
                console.severe(ChatColor.RED + "Could not sync the states of the custom block \"" + id + "\" because the \"disguised_block\" is incompatible with one or more tags of the server-side block. " + e.getMessage());
                return null;
            }
        } else {
            return Bukkit.createBlockData(Material.AIR);
        }
    }

    //gets the logged string for a location
    public String getLoggedStringFromLocation(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int subChunkY = y >> 4;

        File file = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + world.getName(), "chunk." + chunkX + "." + chunkZ + ".yml");
        YamlConfiguration yaml = main.loadYamlFromFile(file, false, false, debug, "");

        String logPath = "subChunk" + subChunkY + "." + x + "_" + y + "_" + z;
        String locString = world.getName() + ", " + x + ", " + y + ", " + z;
        if (yaml != null) {
            if (yaml.contains(logPath)) {
                return yaml.getString(logPath);
            }
        }
        return null;
    }

    //if a location is logged in a file, it will be removed from the file
    public void unlogBlock(Location loc, Player player) {
        File file = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + loc.getWorld().getName(), "chunk." + loc.getChunk().getX() + "." + loc.getChunk().getZ() + ".yml");

        String chunkString = loc.getWorld().getName() + ", " + loc.getChunk().getX() + ", " + loc.getChunk().getZ();
        String locString = loc.getWorld().getName() + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String path = "subChunk" + (loc.getBlockY() >> 4) + "." + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
            String id = yaml.getString(path);

            if (yaml.contains(path)) {
                yaml.set(path, null);
                if (debug >= 2) {
                    console.info(ChatColor.AQUA + player.getName() + " un-logged the custom block \"" + id + "\" at " + locString);
                }

                try {
                    yaml.save(file);
                    if (debug >= 2) {
                        console.info(ChatColor.DARK_AQUA + "Saved the file for the chunk at " + chunkString);
                    }
                } catch (IOException e) {
                    console.severe(ChatColor.RED + "Could not save the file for the chunk at " + chunkString);
                }
            } else if (debug >= 2) {
                console.severe(ChatColor.RED + "Did not un-log the block at " + locString + " because it does not exist in the file " + file.getPath());
            }
        } else if (debug >= 2) {
            console.info(ChatColor.BLUE + "Did not un-log the block at " + locString + " because its chunk file does not exist");
        }
    }

    public void spawnCustomBlockDrops(String id, Location loc, List<Item> originalDrops, Player player) {
        List<ItemStack> newDrops = new ArrayList<>();
        String path = idToFilePath.get(id);
        YamlConfiguration yaml = main.loadYamlFromFile(new File(path), false, false, debug, "");
        if (yaml == null) {
            console.severe(ChatColor.RED + "The drops for the custom block \"" + id + "\" could not be loaded because the source file " + path + " does not exist");
            return;
        }
        if (yaml.isConfigurationSection(id)) {
            ConfigurationSection idSection = yaml.getConfigurationSection(id);
            if (idSection.isConfigurationSection("block.drops")) {
                ConfigurationSection parentDropsSection = idSection.getConfigurationSection("block.drops");

                if (parentDropsSection.getBoolean("enabled", true)) {
                    Set<String> drops = parentDropsSection.getKeys(false);
                    double xpToDrop = 0;

                    drop:
                    for (String drop : drops) {
                        if (parentDropsSection.isConfigurationSection(drop)) {
                            ConfigurationSection dropSection = parentDropsSection.getConfigurationSection(drop);
                            if (player != null && dropSection.contains("conditions")) {
                                for (String condition : dropSection.getStringList("conditions")) {
                                    Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(condition));
                                    if (enchantment == null) {
                                        console.severe(ChatColor.RED + "The enchantment \"" + enchantment.getKey() + "\" is not a valid enchantment");
                                        continue drop;
                                    } else if (!player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.getByKey(NamespacedKey.minecraft(condition)))) {
                                        continue drop;
                                    }
                                }
                            }

                            if (dropSection.contains("chance")) {
                                double chance = dropSection.getDouble("chance");
                                if (chance > 0 && chance < 1) {
                                    if (Math.random() >= chance) {
                                        continue;
                                    }
                                } else {
                                    console.severe(ChatColor.RED + "The \"chance\" tag in the drop section \"" + drop + "\" for the custom block \"" + id + "\" must be greater than 0 and less than 1");
                                }
                            }

                            ItemStack item;
                            if (dropSection.contains("nbt_item")) {
                                item = NBTItem.convertNBTtoItem(new NBTContainer(dropSection.getString("nbt_item")));
                            } else if (dropSection.contains("item")) {
                                Material material = Material.valueOf(dropSection.getString("item").toUpperCase());
                                item = new ItemStack(material);
                            } else {
                                console.warning(ChatColor.YELLOW + "No item is specified for the custom block \"" + id + "\" in the drop section \"" + drop + "\" in the file " + path);
                                continue;
                            }

                            int count = 1;
                            if (dropSection.contains("count")) {
                                count = dropSection.getInt("count");
                            } else if (dropSection.contains("min") && dropSection.contains("max")) {
                                int max = dropSection.getInt("max");
                                int min = dropSection.getInt("min");
                                count = (int) (Math.random() * (max - min + 1) + min);
                            }
                            item.setAmount(count);

                            if (dropSection.contains("set-xp")) {
                                xpToDrop = dropSection.getDouble("set-xp");
                            }
                            if (dropSection.contains("add-xp")) {
                                xpToDrop += dropSection.getDouble("add-xp");
                            }
                            if (dropSection.contains("multiply-xp")) {
                                xpToDrop *= dropSection.getDouble("multiply-xp");
                            }

                            newDrops.add(item);
                        }
                    }
                    originalDrops.clear();

                    int xpToDropInt = (int) xpToDrop;
                    if (xpToDropInt > 0) {
                        ((ExperienceOrb) loc.getWorld().spawnEntity(loc, EntityType.EXPERIENCE_ORB)).setExperience(xpToDropInt);
                    }
                    newDrops.forEach(item -> loc.getWorld().dropItemNaturally(loc, item));

                    String successMsg = "The custom block \"" + id + "\" successfully dropped ";
                    if (debug >= 3) {
                        if (newDrops.isEmpty()) {
                            successMsg += "no items";
                        } else {
                            successMsg += "the items " + newDrops.toString().replace("[", "").replace("]", "");
                        }

                        successMsg += " and " + xpToDropInt + " xp";

                        console.info(ChatColor.LIGHT_PURPLE + successMsg);
                    }
                } else {
                    console.warning(ChatColor.YELLOW + "The drops for the custom block \"" + id + "\" were not changed because the \"enabled\" option is set to false in its drops section");
                }
            } else {
                console.severe(ChatColor.RED + "The drops for the custom block \"" + id + "\" could not be loaded because its drop section does not exist");
            }
        } else {
            console.severe(ChatColor.RED + "The drops for the custom block \"" + id + "\" could not be loaded because its definition does not exist in the file " + path);
        }
    }

    /*public void test(ItemStack item) {
        File file = new File(MAIN.getDataFolder(), "test.yml");
        YamlConfiguration yaml = MAIN.loadYamlFromFile(file, true, false, debug, "");

        int i = 0;
        while (yaml.contains("test.item" + i)) {
            i++;
        }

        //yaml.set("test.item" + i, item);
        yaml.set("test.item" + i, NBTItem.convertItemtoNBT(item).toString());

        CONSOLE.info(ChatColor.GREEN + "added an item " + i + " " + item.toString());
        try {
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    //when a player places a block, log the location and disguised_block, and set the server block to actual_data if it exists in the definition
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Chunk chunk = block.getChunk();
        World world = block.getWorld();

        ItemStack item = event.getItemInHand();
        NBTItem nbtItem = new NBTItem(item);
        String id = nbtItem.getString("CustomBlock");
        String sourceFilePath = idToFilePath.get(id);

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        String path = "subChunk" + (block.getY() >> 4) + "." + x + "_" + y + "_" + z;
        String chunkString = world.getName() + ", " + chunk.getX() + ", " + chunk.getZ();
        String locString = world.getName() + ", " + x + ", " + y + ", " + z;

        //event.getPlayer().sendMessage(ChatColor.GREEN + "BPE:  " + block.getLocation() + "   " + block.getBlockData().getAsString());

        if (nbtItem.getBoolean("IsCustomBlock") && sourceFilePath != null) {

            YamlConfiguration sourceYaml = main.loadYamlFromFile(new File(sourceFilePath), false, false, debug, "");
            if (sourceYaml == null) {
                console.severe(ChatColor.RED + "An error occurred while trying to load the source file for the custom block \"" + id + "\"");
                return;
            }

            if (!sourceYaml.isConfigurationSection(id)) {
                console.severe(ChatColor.RED + "Could not log the custom block \"" + id + "\" because it does not exist in the file " + sourceFilePath);
                return;
            }
            String actual = sourceYaml.getString(id + ".block.actual_block");

            File file = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + world.getName(), "chunk." + chunk.getX() + "." + chunk.getZ() + ".yml");
            YamlConfiguration yaml = main.loadYamlFromFile(file, true, false, debug, "");
            if (yaml == null) {
                return;
            }

            if (!yaml.contains(path)) {
                if (actual != null) {
                    BlockData actualData = createSyncedBlockData(block.getBlockData().getAsString(), id, false);
                    if (actualData != null) {
                        block.setBlockData(actualData);
                    }
                } else if (debug >= 3) {
                    console.warning(ChatColor.YELLOW + "Did not change the server-side block for the custom block \"" + id + "\" at " + locString + " because its source \"actual_block\" is empty");
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
        } else if (nbtItem.getBoolean("IsCustomBlock") && sourceFilePath == null && debug >= 3) {
            console.warning(ChatColor.YELLOW + event.getPlayer().getName() + " placed " + block.getBlockData().getAsString() + " at " + locString + " which contains the tags \"IsCustomBlock:" + nbtItem.getBoolean("IsCustomBlock") + "\" and \"CustomBlock:" + id + "\", but \"" + id + "\" is not a valid custom block");
        }
    }

    //NOT USED - functionality of un-logging blocks moved to blockDropItem()
    //when a player breaks a block, if the block is logged, un-log the block
    /*@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void blockBreak(BlockBreakEvent event) {
        console.info(ChatColor.DARK_PURPLE + "bb");
        if (getLoggedStringFromLocation(event.getBlock().getLocation()) != null) {
            unlogBlock(event.getBlock().getLocation(), event.getPlayer());
        }
    }*/

    //cancels any xp that would normally drop from a custom block being broken (like a spawner)
    @EventHandler
    public void blockDropXpEvent(BlockExpEvent event) {
        String id = getLoggedStringFromLocation(event.getBlock().getLocation());
        String path = idToFilePath.get(id);
        if (event.getExpToDrop() != 0 && id != null) {
            YamlConfiguration yaml = main.loadYamlFromFile(new File(path), false, false, debug, "");
            if (yaml.getBoolean(id + ".block.drops.enabled", true) && yaml.getBoolean(id + ".block.drops.cancel-xp")) {
                console.info(ChatColor.DARK_PURPLE + "cancelling " + event.getExpToDrop() + " xp");
                event.setExpToDrop(0);
            }
        }
    }

    //custom block dropping logic based on options in a custom block's "block.drops" definition section
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        List<Item> originalDrops = event.getItems();

        String id = getLoggedStringFromLocation(loc);

        if (id != null) {
            unlogBlock(loc, player);

            if (player.getGameMode() == GameMode.SURVIVAL || !originalDrops.isEmpty()) {
                spawnCustomBlockDrops(id, loc, originalDrops, player);
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
                        int chunkX = event.getPacket().getIntegers().readSafely(0);
                        int chunkZ = event.getPacket().getIntegers().readSafely(1);
                        Chunk chunk = player.getWorld().getChunkAt(chunkX, chunkZ);

                        if (chunk.isLoaded()) {
                            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
                                int blocks = loadCustomBlocksFromChunkFile(player, chunk);

                                if (debug >= 1 && blocks != 0) {
                                    console.info(ChatColor.DARK_GREEN + "Loaded " + blocks + " blocks in the chunk at " + chunkX + ", " + chunkZ + " by " + player.getName());
                                }
                            }, customBlockConfig.getLong("Global.packet-send-delay"));
                        }
                    }
                }
        );
    }

    //listens for BlockChange packets and (to do) if its position is logged, edits the packet to contain the logged BlockData
    public void blockChangeListener() {
        pm.addPacketListener(
                new PacketAdapter(main, ListenerPriority.HIGHEST, PacketType.Play.Server.BLOCK_CHANGE) {

                    @Override
                    public void onPacketSending(PacketEvent event) {
                        Player player = event.getPlayer();
                        PacketContainer packet = event.getPacket();
                        WrappedBlockData wrappedBlockData = packet.getBlockData().readSafely(0);
                        BlockPosition position = packet.getBlockPositionModifier().readSafely(0);

                        World world = player.getWorld();
                        int x = position.getX();
                        int y = position.getY();
                        int z = position.getZ();
                        Block block = world.getBlockAt(x, y, z);

                        //player.sendMessage(ChatColor.DARK_PURPLE + "BC:  " + position + "   " + data.getType());

                        String locString = world.getName() + ", " + x + ", " + y + ", " + z;
                        String id = getLoggedStringFromLocation(new Location(world, x, y, z));
                        if (id != null) {
                            //commented because this unlogs the block before the event handlers can read it
                            if (/*wrappedBlockData.getType().isEmpty()*/block.getType().isEmpty()) {
                                unlogBlock(new Location(world, x, y, z), player);
                            } else {
                                BlockData disguisedData = createSyncedBlockData(block.getBlockData().getAsString(), id, true);

                                if (disguisedData == null) {
                                    return;
                                }

                                if (disguisedData.getMaterial().equals(Material.AIR) && debug >= 3) {
                                    console.warning(ChatColor.YELLOW + "Did not edit the BlockData in an outgoing BlockChange packet for the custom block \"" + id + "\" at " + locString + " because its source \"disguised_block\" is empty");
                                } else {
                                    packet.getBlockData().write(0, WrappedBlockData.createData(disguisedData));

                                    if (debug >= 4) {
                                        console.info(ChatColor.AQUA + "Edited the BlockData in an outgoing BlockChange packet for the custom block \"" + id + "\" at " + locString + " by " + player.getName());
                                    }
                                }
                            }
                        }
                    }
                }
        );
    }

    //reloads the main and custom block configs, the custom block definition files, and the debug field
    public void reloadConfigs() {
        main.createConfigs();
        config = main.getConfig();
        customBlockConfig = main.loadYamlFromFile(new File(main.getDataFolder(), "custom_block.yml"), false, false, debug, "");

        File customItemsDir = new File(main.getDataFolder() + File.separator + "CustomItems");
        if (customItemsDir.exists()) {
            if (customItemsDir.list().length == 0) {
                main.saveResource("CustomItems" + File.separator + "demo.yml", false);
                if (debug >= 1) {
                    console.info(ChatColor.DARK_AQUA + "Added the file CustomItems" + File.separator + "demo.yml because the CustomItems folder was empty");
                }
            }

            for (File file : FileUtils.listFiles(customItemsDir, new String[] {"yml"}, true)) {
                YamlConfiguration yaml = main.loadYamlFromFile(file, true, false, debug, "");
                yaml.getKeys(false).forEach(key -> {
                    if (yaml.isConfigurationSection(key)) {
                        idToFilePath.put(key, file.getPath());
                    }
                });
            }
        } else {
            main.saveResource("CustomItems" + File.separator + "demo.yml", false);
            console.info(ChatColor.DARK_AQUA + "Created the file CustomItems" + File.separator + "demo.yml because the CustomItems folder did not exist");
        }

        debug = customBlockConfig.getInt("Global.debug");
        MultiBlockChangeWrap.setDebug(debug);
    }

    //uses the coords of a new and old chunk (usually from a movement event) to determine the direction a player moved between chunks
    /*public void chunkDirectionCheck(World world, Chunk newChunk, Chunk oldChunk, Player player) {
        int chunkCount = 0;
        int blockCount = 0;
        int chunkDiffX = newChunk.getX() - oldChunk.getX();
        int chunkDiffZ = newChunk.getZ() - oldChunk.getZ();

        if (chunkDiffX == 0 && chunkDiffZ < 0) {
            //facing north: -z
            for (int x = -4; x <= 4; x++) {
                Chunk chunk = world.getChunkAt(newChunk.getX() + x, newChunk.getZ() - 4);
                blockCount += LoadCustomBlocksFromChunkFile(player, newChunk, chunk);
                chunkCount++;
            }
        } else if (chunkDiffX > 0 && chunkDiffZ == 0) {
            //facing east: +x
            for (int z = -4; z <= 4; z++) {
                Chunk chunk = world.getChunkAt(newChunk.getX() + 4, newChunk.getZ() + z);
                blockCount += LoadCustomBlocksFromChunkFile(player, newChunk, chunk);
                chunkCount++;
            }
        } else if (chunkDiffX == 0 && chunkDiffZ > 0) {
            //facing south: +z
            for (int x = -4; x <= 4; x++) {
                Chunk chunk = world.getChunkAt(newChunk.getX() + x, newChunk.getZ() + 4);
                blockCount += LoadCustomBlocksFromChunkFile(player, newChunk, chunk);
                chunkCount++;
            }
        } else if (chunkDiffX < 0 && chunkDiffZ == 0) {
            //facing west: -x
            for (int z = -4; z <= 4; z++) {
                Chunk chunk = world.getChunkAt(newChunk.getX() - 4, newChunk.getZ() + z);
                blockCount += LoadCustomBlocksFromChunkFile(player, newChunk, chunk);
                chunkCount++;
            }
        } else if (chunkDiffX > 0 && chunkDiffZ < 0) {
            //facing northeast: +x, -z
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (x == 4 || z == -4) {
                        Chunk chunk = world.getChunkAt(newChunk.getX() + x, newChunk.getZ() + z);
                        blockCount += LoadCustomBlocksFromChunkFile(player, newChunk, chunk);
                        chunkCount++;
                    }
                }
            }
        } else if (chunkDiffX > 0) {
            //facing southeast: +x, +z
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (x == 4 || z == 4) {
                        Chunk chunk = world.getChunkAt(newChunk.getX() + x, newChunk.getZ() + z);
                        blockCount += LoadCustomBlocksFromChunkFile(player, newChunk, chunk);
                        chunkCount++;
                    }
                }
            }
        } else if (chunkDiffX < 0 && chunkDiffZ > 0) {
            //facing southwest: -x, +z
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (x == -4 || z == 4) {
                        Chunk chunk = world.getChunkAt(newChunk.getX() + x, newChunk.getZ() + z);
                        blockCount += LoadCustomBlocksFromChunkFile(player, newChunk, chunk);
                        chunkCount++;
                    }
                }
            }
        } else {
            //facing northwest: -x, -z
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (x == -4 || z == -4) {
                        Chunk chunk = world.getChunkAt(newChunk.getX() + x, newChunk.getZ() + z);
                        blockCount += LoadCustomBlocksFromChunkFile(player, newChunk, chunk);
                        chunkCount++;
                    }
                }
            }
        }

        if (debug) {
            console.info(ChatColor.AQUA + String.valueOf(chunkCount) + " total chunks checked and " + blockCount + " blocks loaded by " + player.getName());
        }
    }*/

    /*NOT USED - REPLACED BY multiBlockChange()
    finds all the correctly tagged armor stands in a chunk and sends a fake block at each entity's location*/
    /*public int loadCustomBlocks(Player player, Chunk chunk) {
        //add ability to edit armor stand tags / block to replace with through a config file - use switch statement with different tags
        //ADD CHECK IF THE CHUNK HAS ALREADY BEEN PARSED - ADD FIELD FOR EACH CHUNK FOR IF PARSED OR NOT?
        int blockCount = 0;

        if (chunk.isLoaded()) {

            for (Entity entity : chunk.getEntities()) {
                if (entity.getType().compareTo(EntityType.ARMOR_STAND) == 0 && entity.getScoreboardTags().contains("woz.custom_block")) {
                    Block block = entity.getLocation().getBlock();
                    try {
                        player.sendBlockChange(block.getLocation(), Bukkit.createBlockData(Material.DIAMOND_BLOCK));
                        blockCount++;
                    } catch (IllegalArgumentException e) {
                        console.info(ChatColor.RED + "There was an error sending a custom block packet");
                    }
                }
            }
        } else {
            console.info(ChatColor.RED + "The chunk at " + chunk.getX() + ", " + chunk.getZ() + " was not checked for custom blocks because it was not loaded");
        }
        return blockCount;
    }*/

    /*EXPERIMENTAL - NOT USED - replaced by file-based loader and MultiBlockChangeWrap#addBlock()
    uses PacketWrap to set up packets and add blocks to them, includes test features for armor stands with a block as a helmet*/
    /*public void multiBlockChange(Player player, int arrX, int arrZ, int arrY, Entity entity) {
        MultiBlockChangeWrap[][][] subChunks = subChunkList.get(player);
        MultiBlockChangeWrap MBCWrap;

        if (subChunks[arrX][arrZ][arrY] == null) {
            MBCWrap = new MultiBlockChangeWrap(entity.getLocation());
            subChunks[arrX][arrZ][arrY] = MBCWrap;

            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
                MBCWrap.sendPacket(subChunks, arrX, arrZ, arrY, player);
            }, customBlockConfig.getLong("Global.packet-send-delay"));
        } else {
            MBCWrap = subChunks[arrX][arrZ][arrY];
        }

        ArrayList<WrappedBlockData> blockData = MBCWrap.getBlockData();
        ArrayList<Short> blockLocs = MBCWrap.getBlockLocs();
        Location location = entity.getLocation();

        if (entity.getScoreboardTags().contains("test")) {
            ItemStack helmet = ((ArmorStand) entity).getEquipment().getHelmet();
            BlockData data = ((BlockDataMeta) helmet.getItemMeta()).getBlockData(Material.DIAMOND_BLOCK);
            blockData.add(WrappedBlockData.createData(data));
            player.sendMessage(ChatColor.AQUA + data.getAsString());
        } else {
            blockData.add(WrappedBlockData.createData(Material.EMERALD_BLOCK));
        }

        Block block = location.add(0, 1, 0).getBlock();
        BlockData data = Material.BROWN_MUSHROOM_BLOCK.createBlockData("[down=false,east=false,north=true,south=false,up=false,west=false]");
        console.info(ChatColor.AQUA + data.getAsString());
        block.setBlockData(data);

        for(String tag : customBlockConfig.getConfigurationSection("Types").getKeys(false)) {
            console.info(ChatColor.AQUA + "in for loop");
            String value = customBlockConfig.getString("Types." + tag);
            if(entity.getScoreboardTags().contains(value)){
                console.info(ChatColor.AQUA + "in if");
                blockData.add(WrappedBlockData.createData(Material.getMaterial(value)));
            } else {
                console.info(ChatColor.AQUA + "in else");
                blockData.add(WrappedBlockData.createData(Material.EMERALD));
            }
        }

        int x = location.getBlockX(); x = x & 0xF;
        int y = location.getBlockY(); y = y & 0xF;
        int z = location.getBlockZ(); z = z & 0xF;
        blockLocs.add((short) (x << 8 | z << 4 | y));
    }*/

    /*HIGHLY EXPERIMENTAL - NOT USED - replaced by file-based loader and MultiBlockChangeWrap#addBlock()
    tester for custom block placing logic as a workaround to the small packet send delay used by MultiBlockChange()*/
    /*public void multiBlockChange2(Player player, int arrX, int arrZ, int arrY, Entity entity) {
        PacketWrap[][][] subChunks = subChunkList.get(player);
        PacketWrap packetWrap = new PacketWrap(entity);
        subChunks[arrX][arrZ][arrY] = packetWrap;

        ArrayList<WrappedBlockData> blockData = packetWrap.getBlockData();
        ArrayList<Short> blockLocs = packetWrap.getBlockLocs();
        Location location = entity.getLocation();

        if (entity.getScoreboardTags().contains("test")) {
            ItemStack helmet = ((ArmorStand) entity).getEquipment().getHelmet();
            BlockData data = ((BlockDataMeta) helmet.getItemMeta()).getBlockData(Material.DIAMOND_BLOCK);
            blockData.add(WrappedBlockData.createData(data));
            player.sendMessage(ChatColor.AQUA + "2     " + data.getAsString());
        } else {
            blockData.add(WrappedBlockData.createData(Material.EMERALD_BLOCK));
        }

        int x = location.getBlockX(); x = x & 0xF;
        int y = location.getBlockY(); y = y & 0xF;
        int z = location.getBlockZ(); z = z & 0xF;
        blockLocs.add((short) (x << 8 | z << 4 | y));

        //Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
            for (Player p : player.getWorld().getNearbyPlayers(player.getLocation(), 64)) {
                packetWrap.sendPacket(subChunks, arrX, arrZ, arrY, p);
                p.sendMessage("sent");
            }
        //}, 10);

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.BLOCK_CHANGE);
        ItemStack helmet = ((ArmorStand) entity).getEquipment().getHelmet();
        BlockData data = ((BlockDataMeta) helmet.getItemMeta()).getBlockData(Material.DIAMOND_BLOCK);
        player.sendMessage(ChatColor.AQUA + "2     " + data.getAsString());
        packet.getBlockData().writeSafely(0, WrappedBlockData.createData(data));
        packet.getBlockPositionModifier().writeSafely(0, new BlockPosition(entity.getLocation().toVector()));
        try {
            pm.sendServerPacket(player, packet);

            if (true) {
                player.sendMessage(ChatColor.YELLOW + "Loaded a block at " + entity.getLocation() + " by " + player.getName());
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        ItemStack helmet = ((ArmorStand) entity).getEquipment().getHelmet();
        if (helmet != null) {
            player.sendMessage(ChatColor.RED + helmet.toString());
            try {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
                    player.sendBlockChange(entity.getLocation(), ((BlockDataMeta) helmet.getItemMeta()).getBlockData(helmet.getType()));
                }, 2);
            } catch (NullPointerException e) {
                console.info(ChatColor.RED + "Could not send a newly placed block packet to " + player.getName());
            }
        }
    }*/

    /*NOT USED - subChunkList phased out
    returns a packet from subChunkList based on the location of a custom block, then sends it after a delay specified in the custom block config*/
    /*public MultiBlockChangeWrap getPacketFromLocation(Player player, Location loc, int chunkX, int chunkZ) {
        MultiBlockChangeWrap[][][] subChunks = subChunkList.get(player);
        MultiBlockChangeWrap MBCWrap;

        int arrX = (chunkX - player.getChunk().getX()) + 4;
        int arrZ = (chunkZ - player.getChunk().getZ()) + 4;
        int arrY = loc.getBlockY() >> 4;

        if (subChunks[arrX][arrZ][arrY] == null) {
            MBCWrap = new MultiBlockChangeWrap(loc);
            subChunks[arrX][arrZ][arrY] = MBCWrap;

            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
                MBCWrap.sendPacket(subChunks, arrX, arrZ, arrY, player);
            }, customBlockConfig.getLong("Global.packet-send-delay"));
        } else {
            MBCWrap = subChunks[arrX][arrZ][arrY];
        }

        return MBCWrap;
    }*/

    /*NOT USED - replaced by ChunkLoadListener()
    if the player moves to a new chunk, find the direction the player moved and load blocks in the new chunks*/
    /*@EventHandler
    public void chunkMove(PlayerMoveEvent event) {
        if (!(event.getTo().getChunk().equals(event.getFrom().getChunk()))) {
            Player player = event.getPlayer();
            World world = event.getTo().getWorld();
            Chunk oldChunk = event.getFrom().getChunk();
            Chunk newChunk = event.getTo().getChunk();

            player.sendMessage(player.getChunk().getX() + " " + newChunk.getX() + "   " + player.getChunk().getZ() + " " + newChunk.getZ());
            ChunkDirectionCheck(world, newChunk, oldChunk, player);
        }
    }*/

    /*NOT USED - replaced by ChunkLoadListener()
    if a player teleports to a chunk with a distance of 1 from the original chunk, get the direction they moved
    if the teleport is farther than 1 chunk away, load blocks in all loaded chunks*/
    /*@EventHandler
    public void chunkMoveTeleport(PlayerTeleportEvent event) {
        if (!(event.getTo().getChunk().equals(event.getFrom().getChunk()))) {
            Player player = event.getPlayer();
            World world = event.getTo().getWorld();
            Chunk oldChunk = event.getFrom().getChunk();
            Chunk newChunk = event.getTo().getChunk();

            if (Math.abs(newChunk.getX() - oldChunk.getX()) == 1 || Math.abs(newChunk.getZ() - oldChunk.getZ()) == 1) {
                ChunkDirectionCheck(world, newChunk, oldChunk, player);
            } else {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        Chunk chunk = world.getChunkAt(newChunk.getX() + x, newChunk.getZ() + z);
                        //MultiBlockChange(player, chunk);
                    }
                }
            }
        }
    }*/

    /*NOT USED - replaced by file-based loader
    when an entity is loaded by a player, if the entity has the correct tags then cancel the rendering,
    then call the multi block change method to set a custom block at the entity location*/
    /*private void entityLoad() {
        pm.addPacketListener(
            new PacketAdapter(main, ListenerPriority.HIGHEST, PacketType.Play.Server.SPAWN_ENTITY_LIVING) {

                @Override
                public void onPacketSending(PacketEvent event) {

                    Player player = event.getPlayer();
                    Entity entity = Bukkit.getEntity(event.getPacket().getUUIDs().readSafely(0));

                    if (event.getPacket().getIntegers().readSafely(1) == 1 && entity.getScoreboardTags().contains("woz.custom_block")) {
                        event.setCancelled(true);
                        int x = (entity.getChunk().getX() - player.getChunk().getX()) + 4;
                        int z = (entity.getChunk().getZ() - player.getChunk().getZ()) + 4;
                        int y = entity.getLocation().getBlockY() >> 4;

                        if (entity.getScoreboardTags().contains("test")) {
                            MultiBlockChange2(player, x, z, y, entity);
                        } else {
                            MultiBlockChange(player, x, z, y, entity);
                        }
                    }
                }
            }
        );
    }*/

    /*EXPERIMENTAL - NOT USED - replaced by ChunkLoadListener()
    if a block with BlockData is placed, summon a tagged armor stand with the item as a helmet
    the new armor stand calls the methods inside EntityLoad() when it is loaded*/
    /*@EventHandler
    public void blockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        BlockData data = block.getBlockData();
        ItemStack itemStack = event.getItemInHand();
        if (((BlockDataMeta) itemStack.getItemMeta()).hasBlockData()) {
            ArmorStand as = block.getWorld().spawn(block.getLocation(), ArmorStand.class, new Consumer<ArmorStand>() {
                @Override
                public void accept(ArmorStand as) {
                    as.getEquipment().setHelmet(itemStack.asOne(), true);

                    as.setInvulnerable(true);
                    as.setGravity(false);
                    as.setInvisible(true);
                    as.setBasePlate(false);
                    as.setSilent(true);
                    as.setMarker(true);

                    as.addScoreboardTag("woz.custom_block");
                    as.addScoreboardTag("test");
                    as.addScoreboardTag("placed");
                }
            });
        }
    }*/

    /*NOT USED - subChunkList phased out
    when a player joins, add them to the subChunkList HashMap*/
    /*@EventHandler
    public void playerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        //World world = player.getWorld();
        //Chunk origChunk = player.getChunk();

        subChunkList.putIfAbsent(player, new MultiBlockChangeWrap[9][9][16]);

        //waits a number of seconds as a buffer for the player to load before loading custom blocks
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    Chunk chunk = world.getChunkAt(origChunk.getX() + x, origChunk.getZ() + z);
                    //MultiBlockChange(player, chunk);
                }
            }
        }, customBlockConfig.getLong("Global.join-delay"));
    }*/

    /*NOT USED - subChunkList phased out
    when a player leaves, remove them from the subChunkList HashMap*/
    /*@EventHandler
    public void playerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        subChunkList.remove(player);
    }*/
}

class MultiBlockChangeWrap {

    private static ProtocolManager pm;
    private static Logger console;
    private static int debug;

    private final PacketContainer packet;
    private BlockPosition subChunkPos;
    private final ArrayList<WrappedBlockData> blockData;
    private final ArrayList<Short> blockLocs;

    public static void init(ProtocolManager manager, Logger theConsole, int debugMode) {
        pm = manager;
        console = theConsole;
        setDebug(debugMode);
    }

    public static void setDebug(int debugMode) {
        debug = debugMode;
    }

    //creates a new packet and initializes all fields except subChunkPos
    public MultiBlockChangeWrap() {
        packet = pm.createPacket(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
        blockData = new ArrayList<>();
        blockLocs = new ArrayList<>();
    }

    //creates a new packet and initializes all fields - directly uses given pre-converted subChunk coords for subChunkPos
    public MultiBlockChangeWrap(int x, int y, int z) {
        packet = pm.createPacket(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
        blockData = new ArrayList<>();
        blockLocs = new ArrayList<>();
        subChunkPos = new BlockPosition(x, y, z);
        packet.getSectionPositions().write(0, subChunkPos);
    }

    //creates a new packet and initializes all fields - converts a given location to subChunk coords for subChunkPos
    public MultiBlockChangeWrap(Location loc) {
        packet = pm.createPacket(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
        blockData = new ArrayList<>();
        blockLocs = new ArrayList<>();
        subChunkPos = new BlockPosition(loc.getChunk().getX(), loc.getBlockY() >> 4, loc.getChunk().getZ());
        packet.getSectionPositions().write(0, subChunkPos);
    }

    //sets subChunkPos to given pre-converted subChunk coords and re-writes to packet
    public void setSubChunkPos(int x, int y, int z) {
        subChunkPos = new BlockPosition(x, y, z);
        packet.getSectionPositions().write(0, subChunkPos);
    }

    //adds a BlockData and local subChunk position to the packet fields
    public void addBlock(Location location, BlockData data) {
        blockData.add(WrappedBlockData.createData(data));

        int x = location.getBlockX(); x = x & 0xF;
        int y = location.getBlockY(); y = y & 0xF;
        int z = location.getBlockZ(); z = z & 0xF;
        blockLocs.add((short) (x << 8 | z << 4 | y));
    }

    //NOT USED - subChunkList replaced by file-based logger
    //writes required fields to the packet, sends the packet to a player, and removes itself from the provided subChunks array
    /*public void sendPacket(MultiBlockChangeWrap[][][] subChunks, int arrX, int arrZ, int arrY, Player player) {
        if (subChunkPos == null) {
            console.warning(ChatColor.YELLOW + "Could not send a packet with " + blockData.size() + " blocks to " + player + " because subChunkPos was not initialized");
            return;
        }

        //convert ArrayLists into arrays used by packet
        WrappedBlockData[] blockDataArr = blockData.toArray(new WrappedBlockData[0]);
        Short[] blockLocsShort = blockLocs.toArray(new Short[0]);
        short[] blockLocsArr = ArrayUtils.toPrimitive(blockLocsShort);

        //write arrays to packet
        packet.getBlockDataArrays().writeSafely(0, blockDataArr);
        packet.getShortArrays().writeSafely(0, blockLocsArr);

        try {
            pm.sendServerPacket(player, packet);
            subChunks[arrX][arrZ][arrY] = null;

            if (debug >= 1) {
                console.info(ChatColor.AQUA + "Sent a packet with " + blockData.size() + " blocks in the subchunk at  " + (arrX - 4) + ", " + (arrZ - 4) + ", " + arrY + " by " + player.getName());
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }*/

    //writes required fields to the packet, and sends the packet to a player if it has more than 0 blocks
    public void sendPacket(Player player) {
        if (subChunkPos == null) {
            console.warning(ChatColor.YELLOW + "Could not send a packet with " + blockData.size() + " blocks to " + player + " because subChunkPos was not initialized");
            return;
        }

        //convert ArrayLists into arrays used by packet
        WrappedBlockData[] blockDataArr = blockData.toArray(new WrappedBlockData[0]);
        Short[] blockLocsShort = blockLocs.toArray(new Short[0]);
        short[] blockLocsArr = ArrayUtils.toPrimitive(blockLocsShort);

        //write arrays to packet
        packet.getBlockDataArrays().writeSafely(0, blockDataArr);
        packet.getShortArrays().writeSafely(0, blockLocsArr);

        try {
            if (blockData.size() != 0) {
                pm.sendServerPacket(player, packet);
                if (debug >= 1) {
                    console.info(ChatColor.DARK_GREEN + "Sent a packet with " + blockData.size() + " blocks in the subchunk at " + subChunkPos.getX() + ", " + subChunkPos.getY() + ", " + subChunkPos.getZ() + " to " + player.getName());
                }
            } else if (debug >= 3) {
                console.warning(ChatColor.YELLOW + "Did not send a packet in the subchunk at " + subChunkPos.getX() + ", " + subChunkPos.getY() + ", " + subChunkPos.getZ() + " to " + player.getName() + " because it was empty");
            }
        } catch (InvocationTargetException e) {
            console.severe(ChatColor.RED + "An error occurred while trying to send a packet with " + blockData.size() + " blocks in the subchunk at " + subChunkPos.getX() + ", " + subChunkPos.getY() + ", " + subChunkPos.getZ() + " to " + player.getName());
            e.printStackTrace();
        }
    }

    public PacketContainer getPacket() {
        return packet;
    }

    public BlockPosition getSubChunkPos() {
        return subChunkPos;
    }

    public ArrayList<WrappedBlockData> getBlockData() {
        return blockData;
    }

    public ArrayList<Short> getBlockLocs() {
        return blockLocs;
    }

}
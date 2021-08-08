package me.woz.customplugins.modules.customblocks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.NbtApiException;
import me.woz.customplugins.WorldOfZombies;
import me.woz.customplugins.util.MultiBlockChangeWrap;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
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
    private double chunkReloadID;
    private List<String> recalculateChunkDisguisesBlacklist;
    private final Map<String, String> idToDefinitionFilePath;
    private final Map<String, YamlConfiguration> idToDefinitionFile;

    //constructor to initialize fields and load custom block config file
    public CustomBlockHandler(WorldOfZombies main, ProtocolManager pm) {
        this.main = main;
        this.console = main.getLogger();
        this.pm = pm;

        idToDefinitionFilePath = new HashMap<>();
        idToDefinitionFile = new HashMap<>();

        reloadConfigs();
        chunkLoadListener();
        blockChangeListener();
        multiBlockChangeListener();
        MultiBlockChangeWrap.init(this.pm, console, debug);
    }

    //adds all the blocks in the file for a chunk to their respective MultiBlockChange packets based on subChunk section
    public int loadLoggedBlocksInChunk(Player player, Chunk chunk) {
        int blockCount = 0;
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        World world = chunk.getWorld();
        String chunkString = world.getName() + ", " + chunkX + ", " + chunkZ;

        File chunkFolder = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + world.getName());
        if (!chunkFolder.exists()) {
            if (debug >= 4) {
                console.info(ChatColor.BLUE + "No custom blocks were loaded because the database folder for the world \"" + world.getName() + "\" does not exist");
            }
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

                        if (disguisedData.getMaterial().equals(Material.AIR)) {
                                if (debug >= 3) {
                                    console.warning(ChatColor.YELLOW + "Did not load the \"" + id + "\" at " + locString + " because its source \"disguised-block\" is empty");
                                }
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

    //wrapper method for destroying a custom block: un-logs the block, drops custom items, plays custom break sound, and spawns custom break particles
    public void destroyCustomBlock(Location loc, boolean dropItems, Player player, List<Item> originalDrops) {
        Block block = loc.getWorld().getBlockAt(loc);
        String id = getLoggedStringFromLocation(loc);

        if (id != null) {
            if (!block.getType().isEmpty()) {
                block.setType(Material.AIR);
            }

            unlogBlock(loc, player);

            if (dropItems) {
                spawnCustomBlockDrops(id, loc, originalDrops, player);
            }

            //play custom break sound

            //spawn custom break particles
        }
    }

    //merges disguised-block or actual-block BlockData for a custom block with the sync states in originalBlockDataString
    //null return value indicates an error, and a BlockData with a Material of AIR indicates that the source disguised-block or actual-block does not exist
    public BlockData createSyncedBlockData(String originalBlockDataString, String id, boolean disguise) {
        YamlConfiguration sourceYaml = idToDefinitionFile.get(id);
        if (sourceYaml == null) {
            console.severe(ChatColor.RED + "The custom block \"" + id + "\" could not be loaded because its source file does not exist");
            return null;
        }

        if (!sourceYaml.isConfigurationSection(id + ".block")) {
            console.severe(ChatColor.RED + "The custom block \"" + id + "\" could not be loaded because its source \"block\" section is empty");
            return null;
        }
        ConfigurationSection blockSection = sourceYaml.getConfigurationSection(id + ".block");

        String path = disguise ? "disguised-block" : "actual-block";

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

            List<String> syncList = blockSection.getStringList("sync-states");
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
                console.severe(ChatColor.RED + "Could not sync the states of the custom block \"" + id + "\" because the \"disguised-block\" is incompatible with one or more tags of the server-side block. " + e.getMessage());
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
        //String locString = world.getName() + ", " + x + ", " + y + ", " + z;
        if (yaml != null && yaml.contains(logPath)) {
            return yaml.getString(logPath);
        }
        return null;
    }

    //gets the BlockData logged in "disguised-data" for a location
    public BlockData getDisguisedBlockDataFromLocation(Location loc, String id) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        Block block = world.getBlockAt(loc);

        if (block.getType().isEmpty()) {
            unlogBlock(new Location(world, x, y, z), null);
        } else {
            BlockData data = createSyncedBlockData(block.getBlockData().getAsString(), id, true);

            if (data == null) {
                return null;
            }

            if (data.getMaterial().equals(Material.AIR)) {
                return Bukkit.createBlockData(Material.AIR);
            } else {
                return data;
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
                    if (player != null) {
                        console.info(ChatColor.AQUA + player.getName() + " un-logged the custom block \"" + id + "\" at " + locString);
                    } else {
                        console.info(ChatColor.AQUA + "The custom block \"" + id + "\" at " + locString + " was un-logged");
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
            } else if (debug >= 2) {
                console.severe(ChatColor.RED + "Did not un-log the block at " + locString + " because it does not exist in the file " + file.getPath());
            }
        } else if (debug >= 2) {
            console.info(ChatColor.BLUE + "Did not un-log the block at " + locString + " because its chunk file does not exist");
        }
    }

    //drops items and/or xp specified in the block.drops section of a custom block definition
    public void spawnCustomBlockDrops(String id, Location loc, List<Item> originalDrops, Player player) {
        List<ItemStack> newDrops = new ArrayList<>();
        String path = idToDefinitionFilePath.get(id);
        YamlConfiguration yaml = idToDefinitionFile.get(id);
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

                    //main drop sections, direct children of "block.drops"
                    drop:
                    for (String drop : drops) {
                        if (parentDropsSection.isConfigurationSection(drop)) {
                            ConfigurationSection dropSection = parentDropsSection.getConfigurationSection(drop);

                            if (dropSection.contains("conditions")) {
                                if (player != null) {
                                    for (String condition : dropSection.getStringList("conditions")) {
                                        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(condition));
                                        if (enchantment == null) {
                                            console.severe(ChatColor.RED + "The enchantment \"" + condition + "\" is not a valid enchantment");
                                            continue drop;
                                        } else if (!player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.getByKey(NamespacedKey.minecraft(condition)))) {
                                            continue drop;
                                        }
                                    }
                                } else {
                                    continue;
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

                            if (dropSection.contains("set-xp")) {
                                xpToDrop = dropSection.getDouble("set-xp");
                            }
                            if (dropSection.contains("add-xp")) {
                                xpToDrop += dropSection.getDouble("add-xp");
                            }
                            if (dropSection.contains("multiply-xp")) {
                                xpToDrop *= dropSection.getDouble("multiply-xp");
                            }

                            /*ItemStack item;
                            if (dropSection.contains("nbt-item")) {
                                item = NBTItem.convertNBTtoItem(new NBTContainer(dropSection.getString("nbt-item")));
                            } else if (dropSection.contains("custom-item")) {
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

                            newDrops.add(item);*/

                            Set<String> keys = dropSection.getKeys(false);
                            //children of a main drop section, includes keys like "chance", "conditions", and items
                            for (String key : keys) {
                                Material material = Material.matchMaterial(key);
                                ItemStack item;
                                if (idToDefinitionFile.containsKey(key)) {
                                    //custom item
                                    item = getItemFromID(key);
                                } else if (material != null) {
                                    //vanilla material
                                    item = new ItemStack(material);
                                } else {
                                    if (!key.equalsIgnoreCase("chance") && !key.equalsIgnoreCase("conditions") && !key.equalsIgnoreCase("set-xp") && !key.equalsIgnoreCase("add-xp") && !key.equalsIgnoreCase("multiply-xp")) {
                                        if (debug >= 3) {
                                            console.warning(ChatColor.YELLOW + "The item \"" + key + "\" in the drop section \"" + drop + "\" of the custom block \"" + id + "\" is not a valid custom item id or vanilla item type");
                                        }
                                    }
                                    continue;
                                }

                                if (dropSection.isConfigurationSection(key)) {
                                    //configuration section format: material or custom item id as key, count and nbt as children
                                    ConfigurationSection itemSection = dropSection.getConfigurationSection(key);
                                    if (itemSection.contains("nbt")) {
                                        NBTItem nbtItem = new NBTItem(item);
                                        String nbtString = itemSection.getString("nbt");
                                        NBTCompound nbtMerge;

                                        if (nbtString.contains("id:") && nbtString.contains("Count:")) {
                                            //"nbt" is a full nbt item string
                                            nbtMerge = new NBTItem(NBTItem.convertNBTtoItem(new NBTContainer(nbtString)));
                                        } else {
                                            //"nbt" is just keys and not a full item
                                            nbtMerge = new NBTContainer(nbtString);
                                        }

                                        nbtItem.mergeCompound(nbtMerge);
                                        item = nbtItem.getItem();
                                    }

                                    if (itemSection.contains("count")) {
                                        Object value = itemSection.get("count");
                                        int count = 1;

                                        if (value instanceof Integer) {
                                            count = (Integer) value;
                                        } else if (value instanceof String && ((String) value).contains("-") && ((String) value).length() >= 3) {
                                            String[] range = ((String) value).split("-");
                                            int min = Integer.parseInt(range[0]);
                                            int max = Integer.parseInt(range[1]);
                                            count = (int) (Math.random() * (max - min + 1) + min);
                                        }
                                        item.setAmount(count);
                                    }

                                    /*if (itemSection.contains("nbt-item")) {
                                        String nbtString = itemSection.getString("nbt-item");
                                        Object value = itemSection.get("count");
                                        int count = 1;

                                        if (value instanceof Integer) {
                                            count = (Integer) value;
                                        } else if (value instanceof String && ((String) value).contains("-") && ((String) value).length() >= 3) {
                                            String[] range = ((String) value).split("-");
                                            int min = Integer.parseInt(range[0]);
                                            int max = Integer.parseInt(range[1]);
                                            count = (int) (Math.random() * (max - min + 1) + min);
                                        }

                                        try {
                                            ItemStack item = NBTItem.convertNBTtoItem(new NBTContainer(nbtString));
                                            item.setAmount(count);
                                            newDrops.add(item);
                                        } catch (NbtApiException e) {
                                            console.severe(ChatColor.RED + "The NBT string in the item section \"" + key + "\" in the drop section \"" + drop + "\" of the custom block \"" + id + "\"");
                                        }

                                    } else if (itemSection.contains("custom-item")) {
                                        String customItemID = itemSection.getString("custom-item");
                                        Object value = itemSection.get("count");
                                        int count = 1;

                                        if (value instanceof Integer) {
                                            count = (Integer) value;
                                        } else if (value instanceof String && ((String) value).contains("-") && ((String) value).length() >= 3) {
                                            String[] range = ((String) value).split("-");
                                            int min = Integer.parseInt(range[0]);
                                            int max = Integer.parseInt(range[1]);
                                            count = (int) (Math.random() * (max - min + 1) + min);
                                        }

                                        try {
                                            ItemStack item = getItemFromID(customItemID);
                                            item.setAmount(count);
                                            newDrops.add(item);
                                        } catch (NbtApiException | NullPointerException e) {
                                            console.severe(ChatColor.RED + "The \"item\" tag for the custom block \"" + customItemID + "\" is invalid or null");
                                        }
                                    }*/

                                } else {
                                    //vanilla item without NBT:   [material]: [count]
                                    Object value = dropSection.get(key);
                                    int count = 1;

                                    if (value instanceof Integer) {
                                        count = (Integer) value;
                                    } else if (value instanceof String && ((String) value).contains("-") && ((String) value).length() >= 3) {
                                        String[] range = ((String) value).split("-");
                                        int min = Integer.parseInt(range[0]);
                                        int max = Integer.parseInt(range[1]);
                                        count = (int) (Math.random() * (max - min + 1) + min);
                                    }

                                    item.setAmount(count);
                                }
                                newDrops.add(item);
                            }
                        }
                    }

                    if (originalDrops != null) {
                        originalDrops.clear();
                    }

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

    //moves the logged location of a logged block (mainly for piston events)
    public void moveLoggedBlock(Location loc, Location newLoc) {
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
        if (yaml != null && yaml.contains(logPath)) {
            String id = getLoggedStringFromLocation(loc);
            yaml.set(logPath, null);

            String newLogPath = "subChunk" + subChunkY + "." + newLoc.getBlockX() + "_" + newLoc.getBlockY() + "_" + newLoc.getBlockZ();
            yaml.set(newLogPath, id);
            try {
                yaml.save(file);
            } catch (IOException e) {
                console.severe(ChatColor.RED + "Could not save the file " + file.getPath() + " while trying to move the logged custom block at " + logPath);
            }
        }
    }

    //handles custom blocks that are being pushed/pulled by a piston
    public /*List<Block>*/boolean handleMovedBlocks(List<Block> blocks, BlockFace blockFace, boolean pushing) {
        //List<Block> cancelled = new ArrayList<>();
        Map<Location, Location> moves = new HashMap<>();
        if (!blocks.isEmpty()) {
            for (Block block : blocks) {
                String id = getLoggedStringFromLocation(block.getLocation());
                if (id != null) {
                    YamlConfiguration yaml = idToDefinitionFile.get(id);
                    Location loc = block.getLocation();
                    Location newLoc = block.getRelative(blockFace).getLocation();
                    String locString = loc.getWorld().getName() + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
                    String newLocString = newLoc.getWorld().getName() + ", " + newLoc.getBlockX() + ", " + newLoc.getBlockY() + ", " + newLoc.getBlockZ();

                    if (pushing && yaml != null) {
                        if (yaml.getBoolean(id + ".block.options.piston-breakable")) {
                            destroyCustomBlock(loc, true, null, null);
                            if (debug >= 3) {
                                console.info(ChatColor.LIGHT_PURPLE + "Broke the custom block \"" + id + "\" because it was pushed by a piston and \"block.options.piston-breakable\" is true");
                            }
                        } else if (!yaml.getBoolean(id + ".block.options.cancel-piston-push")) {
                            moves.put(loc, newLoc);
                            if (debug >= 3) {
                                console.info(ChatColor.LIGHT_PURPLE + "Pushed the custom block \"" + id + "\" from " + locString + " to " + newLocString);
                            }
                        } else {
                            //cancelled.add(block);
                            return true;
                        }
                    } else if (yaml != null) {
                        if (!yaml.getBoolean(id + ".block.options.cancel-piston-pull")) {
                            moves.put(loc, newLoc);
                            if (debug >= 3) {
                                console.info(ChatColor.LIGHT_PURPLE + "Pulled the custom block \"" + id + "\" from " + locString + " to " + newLocString);
                            }
                        } else {
                            //cancelled.add(block);
                            return true;
                        }
                    }
                }
            }
            for (Map.Entry<Location, Location> entry : moves.entrySet()) {
                moveLoggedBlock(entry.getKey(), entry.getValue());
            }
        }
        //return cancelled;
        return false;
    }

    //gets the ItemStack specified in the "item" tag of a block definition from an id, throws a NullPointerException if the tag doesn't exist
    public ItemStack getItemFromID(String id) {
        if (id != null && idToDefinitionFile.containsKey(id)) {
            YamlConfiguration yaml = idToDefinitionFile.get(id);
            String itemString = yaml.getString(id + ".item");

            if (itemString != null) {
                NBTItem nbtItem = new NBTItem(NBTItem.convertNBTtoItem(new NBTContainer(itemString)));
                NBTCompound nbtCompound = nbtItem.getOrCreateCompound("WoZItem");

                if (!nbtCompound.getBoolean("IsCustomItem")) {
                    nbtCompound.setBoolean("IsCustomItem", true);
                }

                if (nbtCompound.getString("CustomItem").equals("")) {
                    nbtCompound.setString("CustomItem", id);
                }

                return nbtItem.getItem();
            } else {
                throw new NullPointerException();
            }
        } else {
            console.severe(ChatColor.RED + "No item was loaded for the custom block \"" + id + "\" because the block ID is null or does not exist");
        }

        return null;
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
        String path = "subChunk" + (block.getY() >> 4) + "." + x + "_" + y + "_" + z;
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

            if (yaml.contains("chunk-reload-id")) {
                if (yaml.getDouble("chunk-reload-id") != chunkReloadID) {
                    yaml.set("chunk-reload-id", 1);
                }
            }

            if (!yaml.contains(path)) {
                if (actual != null) {
                    BlockData actualData = createSyncedBlockData(block.getBlockData().getAsString(), id, false);
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
        String id = getLoggedStringFromLocation(event.getBlock().getLocation());
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

        destroyCustomBlock(loc, player.getGameMode() == GameMode.SURVIVAL || !originalDrops.isEmpty(), player, originalDrops);
    }

    //handles custom block drops during explosions
    @EventHandler
    public void entityExplodeEvent(EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();
        double yield = event.getYield();

        while (blocks.iterator().hasNext()) {
            Block block = blocks.iterator().next();
            Location loc = block.getLocation();
            String id = getLoggedStringFromLocation(loc);

            if (id != null) {
                blocks.remove(block);
                YamlConfiguration yaml = idToDefinitionFile.get(id);
                if (yaml != null && !yaml.getBoolean(id + ".block.options.blast-resistant", false)) {
                    //removing block from explosion and setting to air works, but is there a better way to remove original exploded block drops?

                    if (yield > 0) {
                        destroyCustomBlock(loc, Math.random() < yield, null, null);
                    }
                }
            }
        }
    }

    //note: event.getBlocks() returns an immutable list, so how can the blocks be treated like obsidian and not moved?
    //calls handleMovedBlocks() to do most of the work, and cancels the event if one or more blocks has the config option "cancel-piston
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pistonExtendEvent(BlockPistonExtendEvent event) {
        if (handleMovedBlocks(event.getBlocks(), event.getDirection(), true)) {
            event.setCancelled(true);
        }
    }

    //calls handleMovedBlocks() to check if the event should be cancelled, and cancels if it should
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pistonRetractEvent(BlockPistonRetractEvent event) {
        if (handleMovedBlocks(event.getBlocks(), event.getDirection(), false)) {
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
                                int blocks = loadLoggedBlocksInChunk(player, chunk);

                                if (debug >= 1 && blocks != 0) {
                                    console.info(ChatColor.DARK_GREEN + "Loaded " + blocks + " blocks in the chunk at " + chunkX + ", " + chunkZ + " by " + player.getName());
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

                        String id = getLoggedStringFromLocation(loc);
                        if (id != null) {
                            if (wrappedBlockData.equals(WrappedBlockData.createData(block.getBlockData()))) {
                                BlockData disguisedData = getDisguisedBlockDataFromLocation(loc, id);
                                if (disguisedData != null) {
                                    if (disguisedData.getMaterial() == Material.AIR) {
                                        if (debug >= 3){
                                            console.warning(ChatColor.YELLOW + "Did not edit the BlockData in an outgoing BlockChange packet for the custom block \"" + id + "\" at " + locString + " because its source \"disguised-block\" is empty");
                                        }
                                    } else {
                                        packet.getBlockData().write(0, WrappedBlockData.createData(disguisedData));

                                        if (debug >= 4) {
                                            console.info(ChatColor.AQUA + "Edited the BlockData in an outgoing BlockChange packet for the custom block \"" + id + "\" at " + locString + " by " + player.getName());
                                        }
                                    }
                                }
                            } else if (debug >= 3) {
                                console.warning(ChatColor.YELLOW + "Did not load the \"disguised-data\" for the \"" + id + "\" at " + locString + " because the BlockData in a BlockChange packet for that location does not match the BlockData of the real block");
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

                        for (int i = 0; i < shortsArr.length; i++) {
                            short location = shortsArr[i];
                            int absoluteX = offsetX + (location >> 8 & 0xF);
                            int absoluteY = offsetY + (location & 0xF);
                            int absoluteZ = offsetZ + (location >> 4 & 0xF);
                            Location loc = new Location(world, absoluteX, absoluteY, absoluteZ);
                            Block block = world.getBlockAt(loc);
                            String locString = world.getName() + ", " + absoluteX + ", " + absoluteY + ", " + absoluteZ;

                            String id = getLoggedStringFromLocation(loc);
                            if (id != null) {
                                if (blockDataArr[i].equals(WrappedBlockData.createData(block.getBlockData()))) {
                                    BlockData disguisedData = getDisguisedBlockDataFromLocation(loc, id);
                                    if (disguisedData != null) {
                                        if (disguisedData.getMaterial() == Material.AIR) {
                                            if (debug >= 3) {
                                                console.warning(ChatColor.YELLOW + "Did not edit the BlockData for the custom block \"" + id + "\" at " + locString + " in a MultiBlockChange packet because its source \"disguised-block\" is empty");
                                            }
                                        } else {
                                            blockDataArr[i] = WrappedBlockData.createData(disguisedData);

                                            if (debug >= 4) {
                                                console.info(ChatColor.AQUA + "Edited the BlockData for the custom block \"" + id + "\" at " + locString + " in a MultiBlockChange packet by " + player.getName());
                                            }
                                        }
                                    }
                                } else if (debug >= 3) {
                                    console.warning(ChatColor.YELLOW + "Did not load the \"disguised-data\" for the \"" + id + "\" at " + locString + " because the BlockData in a MultiBlockChange packet for that location does not match the BlockData of the real block");
                                }
                            }
                        }

                        packet.getBlockDataArrays().writeSafely(0, blockDataArr);
                        if (debug >= 1 && shortsArr.length != 0) {
                            console.info(ChatColor.DARK_GREEN + "Loaded " + shortsArr.length + " blocks in the chunk at " + subChunkPos.getX() + ", " + subChunkPos.getZ() + " by " + player.getName());
                        }
                    }
                }
        );
    }

    //reloads the main and custom block configs, the custom block definition files, and the debug field
    public void reloadConfigs() {
        main.createConfigs();
        config = main.getConfig();
        customBlockConfig = main.loadYamlFromFile(new File(main.getDataFolder(), "custom-block.yml"), false, false, debug, "");
        recalculateChunkDisguisesBlacklist = customBlockConfig.getStringList("Global.recalculate-chunk-disguises-blacklist");

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

        chunkReloadID = Math.random();
        debug = customBlockConfig.getInt("Global.debug");
        MultiBlockChangeWrap.setDebug(debug);
    }

    public Map<String, String> getIdToDefinitionFilePath() {
        return idToDefinitionFilePath;
    }

    public Map<String, YamlConfiguration> getIdToDefinitionFile() {
        return idToDefinitionFile;
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
}
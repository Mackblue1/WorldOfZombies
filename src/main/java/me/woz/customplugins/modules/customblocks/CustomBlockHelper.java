package me.woz.customplugins.modules.customblocks;

import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTItem;
import me.woz.customplugins.WorldOfZombies;
import me.woz.customplugins.util.MultiBlockChangeWrap;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class CustomBlockHelper {

    private final WorldOfZombies main;
    private final CustomBlockEvents customBlockEvents;
    private final Logger console;

    private FileConfiguration config;
    private FileConfiguration customBlockConfig;

    private int debug;

    //private Map<Player, MultiBlockChangeWrap[][][]> subChunkList = new HashMap<>();
    private double chunkReloadID;
    private List<String> recalculateChunkDisguisesBlacklist;
    private Map<String, String> idToDefinitionFilePath;
    private Map<String, YamlConfiguration> idToDefinitionFile;

    //constructor to initialize fields and load custom block config file
    public CustomBlockHelper(WorldOfZombies main, CustomBlockEvents customBlockEvents) {
        this.main = main;
        this.console = main.getLogger();
        this.customBlockEvents = customBlockEvents;

        reload();
    }

    public void reload() {
        main.createConfigs();
        config = main.getConfig();
        customBlockConfig = main.loadYamlFromFile(new File(main.getDataFolder(), "custom-block.yml"), false, false, debug, "");

        debug = customBlockConfig.getInt("Global.debug");
        recalculateChunkDisguisesBlacklist = customBlockConfig.getStringList("Global.recalculate-chunk-disguises-blacklist");
        idToDefinitionFilePath = customBlockEvents.getIdToDefinitionFilePath();
        idToDefinitionFile = customBlockEvents.getIdToDefinitionFile();
        chunkReloadID = Math.random();
    }

    //adds all the blocks in the file for a chunk to their respective MultiBlockChange packets based on subChunk section
    public int loadLoggedBlocksInChunk(Player player, Chunk chunk) {
        boolean recalculateDisguises = true;
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

        if (main.removeEmpty(file, true, Arrays.asList("chunk-reload-id"), debug)) {
            if (debug >= 4) {
                console.info(ChatColor.BLUE + "There are no custom blocks in the chunk at: " + chunkString + "   (removed empty file)");
            }
            return 0;
        }

        Set<String> sections = logYaml.getKeys(false);

        if (!recalculateChunkDisguisesBlacklist.contains(world.getName())) {
            if (logYaml.contains("chunk-reload-id")) {
                if (logYaml.getDouble("chunk-reload-id") != chunkReloadID) {
                    //reload id is different, so disguised-block should be recalculated
                    logYaml.set("chunk-reload-id", chunkReloadID);
                } else {
                    //reload id is the same, so disguised-block should not be recalculated
                    recalculateDisguises = false;
                }
            } else {
                logYaml.set("chunk-reload-id", chunkReloadID);
            }
        }

        //if a top level key is a ConfigurationSection (by default they all will be), get and use data from its keys
        for (String sectionString : sections) {
            if (logYaml.isConfigurationSection(sectionString)) {
                ConfigurationSection subChunkSection = logYaml.getConfigurationSection(sectionString);
                int subChunkY = Integer.parseInt(sectionString.substring("subChunk".length()));
                String subChunkString = world.getName() + ", " + chunkX + ", " + subChunkY + ", " + chunkZ;

                Set<String> loggedBlocks = subChunkSection.getKeys(false);
                if (!loggedBlocks.isEmpty()) {
                    MultiBlockChangeWrap packet = new MultiBlockChangeWrap(chunkX, subChunkY, chunkZ);

                    //for each location in the subChunk, get (or recalculate and set) the child "disguised-data"
                    for (String loggedLocationString : loggedBlocks) {
                        if (subChunkSection.isConfigurationSection(loggedLocationString)) {
                            ConfigurationSection locationSection = subChunkSection.getConfigurationSection(loggedLocationString);
                            String[] locParts = loggedLocationString.split("_");
                            if (locParts.length < 3) {
                                console.severe(ChatColor.RED + "A custom block could not be loaded because the location key \"" + loggedLocationString + "\" in the subChunk at " + subChunkString + " is invalid");
                                continue;
                            }

                            Location loc = new Location(world, Double.parseDouble(locParts[0]), Double.parseDouble(locParts[1]), Double.parseDouble(locParts[2]));
                            String locString = world.getName() + ", " + locParts[0] + ", " + locParts[1] + ", " + locParts[2];
                            String id = locationSection.getString("id");

                            BlockData disguisedData;
                            if (recalculateDisguises) {
                                disguisedData = createCustomDisguisedBlockData(world.getBlockAt(loc).getBlockData().getAsString(), id);
                                locationSection.set("disguised-block", disguisedData.getAsString());
                                if (debug >= 4) {
                                    console.info(ChatColor.BLUE + "The \"disguised-block\" for the block at " + locString + " was recalculated because the logged and plugin's chunk reload ID did not match or because this world is included in the \"recalculate-chunk-disguises-blacklist\"");
                                }
                            } else {
                                String disguisedBlockString = locationSection.getString("disguised-block");
                                try {
                                    disguisedData = Bukkit.createBlockData(disguisedBlockString);
                                    if (debug >= 4) {
                                        console.info(ChatColor.BLUE + "The disguised BlockData for the block at " + locString + " was taken directly from the logged \"disguised-block\" because the logged and plugin's chunk reload ID matched");
                                    }
                                } catch (IllegalArgumentException e) {
                                    disguisedData = createCustomDisguisedBlockData(world.getBlockAt(loc).getBlockData().getAsString(), id);
                                    if (debug >= 3) {
                                        console.warning(ChatColor.YELLOW + "The \"disguised-block\" for the block at " + locString + " was invalid or null, so it was recalculated");
                                    }
                                }
                            }

                            if (disguisedData == null) {
                                //no error message because error messages are handled in createBlockData()
                                continue;
                            }

                            if (disguisedData.getMaterial().equals(Material.AIR)) {
                                if (debug >= 4) {
                                    console.warning(ChatColor.YELLOW + "(THIS CAN PROBABLY BE IGNORED IF THIS IS DURING A CHUNK BEING LOADED) Did not load the \"" + id + "\" at " + locString + " because its source \"disguised-block\" is empty");
                                }
                            } else {
                                packet.addBlock(loc, disguisedData);
                                blockCount++;

                                if (debug >= 5) {
                                    console.info(ChatColor.GRAY + "Loaded the \"" + id + "\" at " + locString + " in the subChunk at " + subChunkString + " by " + player.getName());
                                }
                            }
                        }
                    }
                    packet.sendPacket(player);
                }
            }
        }

        try {
            logYaml.save(file);
        } catch (IOException e) {
            console.severe(ChatColor.RED + "Could not save the file for the chunk at " + chunkString);
        }

        return blockCount;
    }

    //wrapper method for calculating a custom block's disguised BlockData, including sync-states and match-states
    public BlockData createCustomDisguisedBlockData(String originalBlockDataString, String id) {
        BlockData syncedData = createSyncedBlockData(originalBlockDataString, id, true);
        BlockData matchedData = checkMatchStates(syncedData.getAsString(), id);

        if (matchedData != null) {
            return matchedData;
        }
        return syncedData;
    }

    //wrapper method for destroying a custom block: un-logs the block, drops custom items, plays custom break sound, and spawns custom break particles
    public void destroyCustomBlock(Location loc, boolean dropItems, Player player, List<Item> originalDrops) {
        Block block = loc.getWorld().getBlockAt(loc);
        String id = getLoggedStringFromLocation(loc, "id");

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

    //checks the match-states section and returns null if no conditions are met or the disguised-block inside the first matching state section
    public BlockData checkMatchStates(String originalBlockDataString, String id) {
        YamlConfiguration yaml = idToDefinitionFile.get(id);
        if (yaml.isConfigurationSection(id + ".block.match-states")) {
            ConfigurationSection matchStatesSection = yaml.getConfigurationSection(id + ".block.match-states");
            Set<String> states = matchStatesSection.getKeys(false);

            for (String state : states) {
                if (matchStatesSection.isConfigurationSection(state)) {
                    BlockData data = checkMatchStatesSection(originalBlockDataString, matchStatesSection.getConfigurationSection(state));
                    if (data != null) {
                        return data;
                    }
                }
            }
        }

        return null;
    }

    //recursive element of checkMatchStates to actually check the states
    public BlockData checkMatchStatesSection(String originalBlockDataString, ConfigurationSection section) {
        if (section.contains("state")) {
            String matchKey = section.getName();
            String matchValue = section.get("state").toString();

            if (originalBlockDataString.contains(matchKey)) {
                String afterState = originalBlockDataString.substring(originalBlockDataString.indexOf(matchKey));
                int stateEnd = afterState.indexOf("]");
                if (afterState.contains(",")) {
                    if (afterState.indexOf(",") < stateEnd) {
                        stateEnd = afterState.indexOf(",");
                    }
                }

                String value = afterState.substring((matchKey + "=").length(), stateEnd);

                if (value.equals(matchValue)) {
                    if (section.contains("disguised-block")) {
                        return Bukkit.createBlockData(section.getString("disguised-block"));
                    } else {
                        for (String state : section.getKeys(false)) {
                            if (section.isConfigurationSection(state)) {
                                BlockData data = checkMatchStatesSection(originalBlockDataString, section.getConfigurationSection(state));
                                if (data != null) {
                                    return data;
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    //gets a logged string from a location
    public String getLoggedStringFromLocation(Location loc, String path) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int subChunkY = y >> 4;

        File file = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + world.getName(), "chunk." + chunkX + "." + chunkZ + ".yml");
        YamlConfiguration yaml = main.loadYamlFromFile(file, false, false, debug, "");

        String basePath = "subChunk" + subChunkY + "." + x + "_" + y + "_" + z;
        //String locString = world.getName() + ", " + x + ", " + y + ", " + z;
        if (yaml != null && yaml.contains(basePath + "." + path)) {
            return yaml.getString(basePath + "." + path);
        }
        return null;
    }

    //sets a value in a log file from a location
    public void setLoggedInfoAtLocation(Location loc, String path, Object value) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int subChunkY = y >> 4;
        String chunkString = world.getName() + ", " + chunkX + ", " + chunkZ;

        File file = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + world.getName(), "chunk." + chunkX + "." + chunkZ + ".yml");
        YamlConfiguration yaml = main.loadYamlFromFile(file, false, false, debug, "");
        yaml.set("subChunk" + subChunkY + "." + x + "_" + y + "_" + z + "." + path, value);
        try {
            yaml.save(file);
        } catch (IOException e) {
            console.severe(ChatColor.RED + "Could not save the file for the chunk at " + chunkString);
        }
    }

    //un-logs a block if the block is air, or returns the newly created disguised BlockData for a block
    public BlockData unLogBlockOrCreateDisguisedBlockData(Location loc, String id) {
        Block block = loc.getWorld().getBlockAt(loc);

        if (block.getType().isEmpty()) {
            unlogBlock(loc, null);
        } else {
            BlockData data = createCustomDisguisedBlockData(block.getBlockData().getAsString(), id);

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
            String id = yaml.getString(path + ".id");

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
        if (yaml != null && yaml.isConfigurationSection(logPath)) {
            Map<String, Object> values = yaml.getConfigurationSection(logPath).getValues(true);
            yaml.set(logPath, null);

            String newLogPath = "subChunk" + subChunkY + "." + newLoc.getBlockX() + "_" + newLoc.getBlockY() + "_" + newLoc.getBlockZ();
            yaml.createSection(newLogPath, values);
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
                String id = getLoggedStringFromLocation(block.getLocation(), "id");
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

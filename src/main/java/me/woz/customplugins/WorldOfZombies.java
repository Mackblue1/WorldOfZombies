package me.woz.customplugins;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import me.woz.customplugins.commands.CommandHandler;
import me.woz.customplugins.commands.SCommand;
import me.woz.customplugins.commands.TestCommand;
import me.woz.customplugins.commands.woz.BlockDatabaseCommands;
import me.woz.customplugins.commands.SCommandTab;
import me.woz.customplugins.modules.customblocks.CustomBlockHandler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class WorldOfZombies extends JavaPlugin {

    private final Logger console = getLogger();
    private ProtocolManager pm;

    private CommandHandler commandHandler;
    private CustomBlockHandler customBlockHandler;

    private File configFile;
    private FileConfiguration config;
    private File customBlockConfigFile;
    private FileConfiguration customBlockConfig;

    @Override
    public void onEnable() {
        createConfigs();
        commandHandler = new CommandHandler();
        pm = ProtocolLibrary.getProtocolManager();

        console.info(ChatColor.GOLD + " __          __  ______ ");
        console.info(ChatColor.GOLD + " \\ \\        / / |___  / ");
        console.info(ChatColor.GOLD + "  \\ \\  /\\  / /__   / /  ");
        console.info(ChatColor.GOLD + "   \\ \\/  \\/ / _ \\ / /   ");
        console.info(ChatColor.GOLD + "    \\  /\\  / (_) / /__  ");
        console.info(ChatColor.GOLD + "     \\/  \\/ \\___/_____|");
        console.info("_______________________________________________________");

        if (config.getBoolean("Modules.custom-blocks")) {
            customBlockHandler = new CustomBlockHandler(this, pm);
            getServer().getPluginManager().registerEvents(customBlockHandler, this);

            BlockDatabaseCommands blockDatabaseCommands = new BlockDatabaseCommands(this);
            commandHandler.registerMultiArgCommand(blockDatabaseCommands, "", "Confirms a previous database command", "database", "confirm");
            commandHandler.registerMultiArgCommand(blockDatabaseCommands, " [world]", "Deletes the custom block database for a world", "database", "delete");
            commandHandler.registerMultiArgCommand(blockDatabaseCommands, " [world1] [world2]", "Clones the database from  world1  to  world2", "database", "clone");
        }

        getCommand("worldofzombies").setExecutor(new SCommand(this, commandHandler, customBlockHandler));
        getCommand("worldofzombies").setTabCompleter(new SCommandTab(this));
        getCommand("bb").setExecutor(new TestCommand(this, customBlockHandler));

        console.info(ChatColor.GREEN + "World of Zombies custom plugin loaded successfully!");
        console.info("‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾");
    }

    @Override
    public void onDisable() {
        console.info(ChatColor.GREEN + "World of Zombies custom plugin unloaded successfully!");
    }

    //creates and loads core config files
    public void createConfigs() {
        if (!getDataFolder().exists()) {
            try {
                getDataFolder().mkdirs();
            } catch (Exception e) {
                console.severe(ChatColor.RED + "World of Zombies data folder could not be created");
            }
        }

        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            console.severe(ChatColor.RED + "The main config file could not be loaded");
        }

        customBlockConfigFile = new File(getDataFolder(), "custom-block.yml");
        if (!customBlockConfigFile.exists()) {
            saveResource("custom-block.yml", false);
        }
        customBlockConfig = new YamlConfiguration();
        try {
            customBlockConfig.load(customBlockConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            console.severe(ChatColor.RED + "The custom block config file could not be loaded");
        }

        saveResource("changelog.txt", true);
    }

    //loads a File, and takes parameters to determine if it should create a new file if it doesn't exist or to create a directory instead of a file
    public YamlConfiguration loadYamlFromFile(File file, boolean createNew, boolean directory, int debug, String notExistMsg) {
        if (!file.exists()) {
            if (createNew) {
                try {
                    if (directory) {
                        file.mkdirs();
                    } else {
                        file.createNewFile();
                    }
                    if (debug >= 1) {
                        console.info(ChatColor.DARK_AQUA + "Successfully created the file or directory " + file.getPath());
                    }
                } catch (Exception e) {
                    console.severe(ChatColor.RED + "Could not create the file or directory " + file.getPath());
                    return null;
                }
            } else {
                if (!notExistMsg.equals("") && debug >= 4) {
                    console.info(notExistMsg);
                }
                return null;
            }
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    //removes an empty file (or empty sections in the file if deep == true), and returns if the file was removed
    public boolean removeEmpty(File file, boolean deep, int debug) {
        int removedSections = 0;
        boolean removedFile = false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        if (deep) {
            for (String section : yaml.getKeys(false)) {
                if (yaml.isConfigurationSection(section)) {
                    ConfigurationSection configSection = yaml.getConfigurationSection(section);
                    if (configSection.getKeys(true).isEmpty()) {
                        yaml.set(section, null);
                        removedSections++;
                    }
                }
            }

            try {
                yaml.save(file);
            } catch (IOException e) {
                console.severe(ChatColor.RED + "Could not save the file at " + file.getPath() + " while trying to remove empty sections");
            }
        }

        if (yaml.getKeys(true).isEmpty()) {
            try {
                file.delete();
            } catch (Exception e) {
                console.severe(ChatColor.RED + "Could not delete the file at " + file.getPath());
            }
            removedFile = true;
        }

        if (removedFile && debug >= 2) {
            console.info(ChatColor.DARK_AQUA + "Deleted the empty file at " + file.getPath());
        } else if (removedSections > 0 && debug >= 2) {
            console.info(ChatColor.DARK_AQUA + "Removed " + removedSections + " empty sections in the file at " + file.getPath());
        }
        return removedFile;
    }
}
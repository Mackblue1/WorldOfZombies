package me.woz.customplugins.commands;

import me.woz.customplugins.WorldOfZombies;
import me.woz.customplugins.commands.woz.ReloadCommand;
import me.woz.customplugins.modules.customblocks.CustomBlockHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SCommand implements CommandExecutor {

    /*
    This class was taken and modified with permission from its author, ryandw11
    https://github.com/ryandw11/CustomStructures/blob/9404ddafbc6e0200c2ad6672d15b10ca5868f365/src/main/java/com/ryandw11/structure/commands/SCommand.java
     */

    private final WorldOfZombies main;
    private final CommandHandler commandHandler;
    private final FileConfiguration config;

    private final InfoCommand info;

    //registers specific subCommands using CommandHandler
    public SCommand(WorldOfZombies main, CommandHandler commandHandler, CustomBlockHandler customBlockHandler) {
        this.main = main;
        this.config = main.getConfig();
        this.commandHandler = commandHandler;
        info = new InfoCommand(main, config, commandHandler);

        commandHandler.registerCommand("info", info, "", "Displays information about this plugin");
        commandHandler.registerCommand("reload", new ReloadCommand(main, customBlockHandler), "", "Reloads the plugin's config files");
    }

    //primary command handler that calls CommandHandler#handleCommand() and handles command error messages
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (args.length == 0) {
            return info.displayInfo(sender);
        }

        try {
            return commandHandler.handleCommand(sender, cmd, s, args);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().equalsIgnoreCase("Invalid command")) {
                sender.sendMessage(ChatColor.RED + "Invalid command! Use " + ChatColor.AQUA + "/woz info" + ChatColor.RED + " for a list of valid commands!");
            } else {
                List<String> commandList = Arrays.asList(e.getMessage().split(","));
                List<String> commandInfo = commandHandler.getCommandInfoMap().get(commandList);
                sender.sendMessage(ChatColor.RED + "Invalid arguments! Usage: " + ChatColor.YELLOW + "/woz " + String.join(" ", commandList) + commandInfo.get(0));
            }
        }

        return false;
    }
}

class InfoCommand implements SubCommand {

    private final WorldOfZombies main;
    private final FileConfiguration config;
    private final CommandHandler commandHandler;

    public InfoCommand(WorldOfZombies main, FileConfiguration config, CommandHandler commandHandler) {
        this.main = main;
        this.config = config;
        this.commandHandler = commandHandler;
    }

    @Override
    public boolean subCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        return displayInfo(sender);
    }

    public boolean displayInfo(CommandSender sender) {
        if (sender.hasPermission("worldofzombies.command.info")) {

            PluginDescriptionFile description = main.getDescription();

            sender.sendMessage(ChatColor.GREEN + "==== World of Zombies custom plugin information ====");

            sender.sendMessage(ChatColor.GOLD + "Authors: " + ChatColor.WHITE + description.getAuthors().toString().replace("[", "").replace("]", ""));

            sender.sendMessage(ChatColor.GOLD + "Version: " + ChatColor.WHITE + description.getVersion());

            sender.sendMessage(ChatColor.GOLD + "Modules:");
            if (config.isConfigurationSection("Modules")) {
                config.getConfigurationSection("Modules").getValues(false).forEach((key, value) -> {
                    String valueString;
                    if ((boolean) value) {
                        valueString = ChatColor.GREEN + "enabled";
                    } else {
                        valueString = ChatColor.RED + "disabled";
                    }
                    sender.sendMessage("   " + key + ": " + valueString);
                });
            }

            sender.sendMessage(ChatColor.GOLD + "Dependencies:");
            description.getDepend().forEach(depend -> sender.sendMessage("   " + depend));

            sender.sendMessage(ChatColor.GOLD + "Permissions:");
            description.getPermissions().forEach(perm -> sender.sendMessage("   " + perm.getName()));

            sender.sendMessage(ChatColor.GOLD + "Commands:");
            for (Map.Entry<List<String>, List<String>> entry : commandHandler.getCommandInfoMap().entrySet()) {
                sender.sendMessage("   " + ChatColor.AQUA + "/woz " + String.join(" ", entry.getKey()) + entry.getValue().get(0) + ChatColor.WHITE + " - " + entry.getValue().get(1));
            }

            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command!");
        }

        return false;
    }
}
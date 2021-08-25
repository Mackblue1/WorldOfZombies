package me.mackblue.worldofzombies.commands.woz;

import me.mackblue.worldofzombies.WorldOfZombies;
import me.mackblue.worldofzombies.commands.SCommandTab;
import me.mackblue.worldofzombies.commands.SubCommand;
import me.mackblue.worldofzombies.modules.customblocks.CustomBlockEvents;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public class ReloadCommand implements SubCommand {

    private final WorldOfZombies main;
    private final CustomBlockEvents customBlockEvents;
    private final GetCustomItemCommand getCustomItemCommand;
    private final SCommandTab sCommandTab;
    private final Logger console;

    public ReloadCommand(WorldOfZombies main, CustomBlockEvents customBlockEvents, GetCustomItemCommand getCustomItemCommand, SCommandTab sCommandTab) {
        this.main = main;
        this.customBlockEvents = customBlockEvents;
        this.getCustomItemCommand = getCustomItemCommand;
        this.sCommandTab = sCommandTab;
        console = main.getLogger();
    }

    @Override
    public boolean subCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        if (sender.hasPermission("worldofzombies.command.reload")) {

            main.reloadConfig();
            main.createConfigs();
            if (customBlockEvents != null) {
                customBlockEvents.reload();
            } else {
                console.info(ChatColor.AQUA + "The custom block config was not reloaded because the custom blocks module is disabled");
            }
            if (getCustomItemCommand != null) {
                getCustomItemCommand.reloadItems();
                sCommandTab.reloadCompletions();
            } else {
                console.info(ChatColor.AQUA + "The \"/woz get\" command was not reloaded because the custom blocks module is disabled");
            }

            if (sender instanceof Player) {
                sender.sendMessage(ChatColor.GREEN + "World of Zombies plugin reloaded!");
            }
            console.info(ChatColor.GREEN + "World of Zombies plugin reloaded!");

            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command!");
        }

        return true;
    }
}
package me.woz.customplugins.commands.woz;

import me.woz.customplugins.WorldOfZombies;
import me.woz.customplugins.commands.SubCommand;
import me.woz.customplugins.modules.customblocks.CustomBlockHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public class ReloadCommand implements SubCommand {

    private final WorldOfZombies main;
    private final CustomBlockHandler customBlockHandler;
    private final Logger console;

    public ReloadCommand(WorldOfZombies main, CustomBlockHandler customBlockHandler) {
        this.main = main;
        this.customBlockHandler = customBlockHandler;
        console = main.getLogger();
    }

    @Override
    public boolean subCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        if (sender.hasPermission("worldofzombies.command.reload")) {

            main.reloadConfig();
            main.createConfigs();
            if (customBlockHandler != null) {
                customBlockHandler.reloadConfigs();
            } else {
                console.info(ChatColor.AQUA + "The custom block config was not reloaded because the custom blocks module is disabled");
            }

            if (sender instanceof Player) {
                sender.sendMessage(ChatColor.GREEN + "World of Zombies plugin reloaded!");
            }
            console.info(ChatColor.GREEN + "World of Zombies plugin reloaded!");

            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command!");
        }

        return false;
    }
}
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
    private final Logger console;

    public ReloadCommand(WorldOfZombies main) {
        this.main = main;
        console = main.getLogger();
    }

    @Override
    public boolean subCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        if (sender.hasPermission("worldofzombies.command.reload")) {

            main.reload();

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
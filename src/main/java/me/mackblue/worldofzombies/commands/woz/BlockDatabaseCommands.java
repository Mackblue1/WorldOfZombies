package me.mackblue.worldofzombies.commands.woz;

import me.mackblue.worldofzombies.WorldOfZombies;
import me.mackblue.worldofzombies.commands.SubCommand;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

public class BlockDatabaseCommands implements SubCommand {

    private final WorldOfZombies main;
    private final Logger console;
    private final HashMap<CommandSender, String> confirmMap;

    private int deleteScheduler, cloneScheduler;

    public BlockDatabaseCommands(WorldOfZombies main) {
        this.main = main;
        this.console = main.getLogger();
        this.confirmMap = new HashMap<>();
    }

    @Override
    public boolean subCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        if (sender.hasPermission("worldofzombies.command.database")) {

            if (args.length == 0) {
                throw new IllegalArgumentException("Invalid command");
            }

            //confirm command logic which does most of the processing
            if (args[0].equalsIgnoreCase("confirm")) {
                if (confirmMap.containsKey(sender)) {
                    String confirmedCommand = confirmMap.get(sender);
                    String[] data = confirmedCommand.split(",");
                    String world = data[1];

                    File dir = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + world);

                    //delete command
                    if (data[0].equalsIgnoreCase("delete")) {
                        if (dir.exists()) {
                            try {
                                FileUtils.deleteDirectory(dir);
                                sender.sendMessage(ChatColor.GREEN + "The custom block database for " + world + " was successfully deleted!");
                                if (!(sender instanceof Logger)) {
                                    console.info(ChatColor.GREEN + "The custom block database for " + world + " was successfully deleted!");
                                }
                            } catch (IOException e) {
                                sender.sendMessage(ChatColor.RED + "Could not delete the custom block database for " + world + " because an error occurred!");
                                if (!(sender instanceof Logger)) {
                                    console.severe(ChatColor.RED + "Could not delete the custom block database for " + world + " because an error occurred!");
                                }
                            }
                        } else {
                            sender.sendMessage(ChatColor.RED + "Could not delete the custom block database for " + ChatColor.YELLOW + world + ChatColor.RED + " because its data folder does not exist!");
                            if (!(sender instanceof Logger)) {
                                console.severe(ChatColor.RED + "Could not delete the custom block database for " + ChatColor.YELLOW + world + ChatColor.RED + " because its data folder does not exist!");
                            }
                        }

                        confirmMap.remove(sender);
                        return true;
                    }

                    //clone command
                    else if (data[0].equalsIgnoreCase("clone")) {
                        String newWorld = data[2];
                        if (dir.exists()) {
                            File newDir = new File(main.getDataFolder() + File.separator + "BlockDatabase" + File.separator + newWorld);
                            if (newDir.exists()) {
                                try {
                                    FileUtils.deleteDirectory(newDir);
                                } catch (IOException e) {
                                    sender.sendMessage(ChatColor.RED + "Could not clone the custom block database for " + world + " because the database for " + newWorld + " could not be cleared!");
                                    if (!(sender instanceof Logger)) {
                                        console.severe(ChatColor.RED + "Could not clone the custom block database for " + world + " because the database for " + newWorld + " could not be cleared!");
                                    }

                                    confirmMap.remove(sender);
                                    return true;
                                }
                            }

                            try {
                                FileUtils.copyDirectory(dir, newDir);
                                sender.sendMessage(ChatColor.GREEN + "The custom block database for " + ChatColor.YELLOW + world + ChatColor.RED + " was successfully cloned to " + ChatColor.YELLOW + newWorld + ChatColor.RED + "!");
                                if (!(sender instanceof Logger)) {
                                    console.info(ChatColor.GREEN + "The custom block database for " + ChatColor.YELLOW + world + ChatColor.RED + " was successfully cloned to " + ChatColor.YELLOW + newWorld + ChatColor.RED + "!");
                                }
                            } catch (IOException e) {
                                sender.sendMessage(ChatColor.RED + "Could not clone the custom block database from " + ChatColor.YELLOW + world + ChatColor.RED + " to " + ChatColor.YELLOW + newWorld + ChatColor.RED + " because an error occurred!");
                                if (!(sender instanceof Logger)) {
                                    console.severe(ChatColor.RED + "Could not clone the custom block database from " + ChatColor.YELLOW + world + ChatColor.RED + " to " + ChatColor.YELLOW + newWorld + ChatColor.RED + " because an error occurred!");
                                }
                            }
                        } else {
                            sender.sendMessage(ChatColor.RED + "Could not clone the custom block database for " + ChatColor.YELLOW + world + ChatColor.RED + " because its data folder does not exist!");
                            if (!(sender instanceof Logger)) {
                                console.severe(ChatColor.RED + "Could not clone the custom block database for " + ChatColor.YELLOW + world + ChatColor.RED + " because its data folder does not exist!");
                            }
                        }

                        confirmMap.remove(sender);
                        return true;
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "You must run a different database command before confirming!");
                    return true;
                }
            }

            else {
                if (confirmMap.containsKey(sender)) {
                    String prev = confirmMap.get(sender).split(",")[0];
                    confirmMap.remove(sender);

                    if (deleteScheduler != 0) {
                        Bukkit.getScheduler().cancelTask(deleteScheduler);
                        deleteScheduler = 0;
                    }

                    if (cloneScheduler != 0) {
                        Bukkit.getScheduler().cancelTask(cloneScheduler);
                        cloneScheduler = 0;
                    }
                    sender.sendMessage(ChatColor.RED + "Canceled the previous database " + ChatColor.YELLOW + prev + ChatColor.RED + " command!");
                }

                if (args[0].equalsIgnoreCase("delete")) {
                    if (args.length != 2) {
                        throw new IllegalArgumentException("database,delete");
                    }

                    confirmMap.put(sender, "delete," + args[1]);
                    sender.sendMessage(ChatColor.AQUA + "Are you sure you want to delete the custom block database for " + ChatColor.YELLOW + args[1] + ChatColor.AQUA + "? To confirm, run " + ChatColor.YELLOW + "/woz database confirm" + ChatColor.AQUA + " within the next 10 seconds");

                    deleteScheduler = Bukkit.getScheduler().scheduleSyncDelayedTask(main, () -> confirmMap.remove(sender), 200L);

                    return true;
                }

                else if (args[0].equalsIgnoreCase("clone")) {
                    if (args.length != 3) {
                        throw new IllegalArgumentException("database,clone");
                    }

                    if (args[1].equalsIgnoreCase(args[2])) {
                        sender.sendMessage(ChatColor.RED + "You cannot clone the custom block database from " + ChatColor.YELLOW + args[1] + ChatColor.RED + " to itself!");
                        return true;
                    }

                    confirmMap.put(sender, "clone," + args[1] + "," + args[2]);
                    sender.sendMessage(ChatColor.AQUA + "Are you sure you want to clone the custom block database from " + ChatColor.YELLOW + args[1] + ChatColor.AQUA + " to " + ChatColor.YELLOW + args[2] + ChatColor.AQUA + "? Any data in the " + ChatColor.YELLOW + args[2] + ChatColor.AQUA + " database will be deleted. To confirm, run " + ChatColor.YELLOW + "/woz database confirm" + ChatColor.AQUA + " within the next 10 seconds");

                    cloneScheduler = Bukkit.getScheduler().scheduleSyncDelayedTask(main, () -> confirmMap.remove(sender), 200L);

                    return true;
                }
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command!");
        }

        throw new IllegalArgumentException("Invalid command");
    }
}


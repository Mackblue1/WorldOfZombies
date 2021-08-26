package me.mackblue.worldofzombies.commands.woz;

import de.tr7zw.nbtapi.NbtApiException;
import me.mackblue.worldofzombies.WorldOfZombies;
import me.mackblue.worldofzombies.commands.SubCommand;
import me.mackblue.worldofzombies.modules.customblocks.CustomBlockEvents;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.logging.Logger;

public class GetCustomItemCommand implements SubCommand {
    
    private final WorldOfZombies main;
    private final CustomBlockEvents customBlockEvents;
    private final Logger console;

    private Set<String> itemStrings;

    public GetCustomItemCommand(WorldOfZombies main, CustomBlockEvents customBlockEvents) {
        this.main = main;
        this.customBlockEvents = customBlockEvents;
        console = main.getLogger();
        itemStrings = customBlockEvents.getIdToDefinitionFile().keySet();
    }

    public void reloadItems() {
        itemStrings = customBlockEvents.getIdToDefinitionFile().keySet();
    }

    @Override
    public boolean subCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        if (sender.hasPermission("worldofzombies.command.get")) {
            if (sender instanceof Player) {
                if (customBlockEvents != null) {
                    if (args.length == 0) {
                        throw new IllegalArgumentException("Invalid command");
                    }

                    String id = args[0];
                    if (itemStrings.contains(id)) {
                        ItemStack item;
                        try {
                            item = customBlockEvents.getCustomBlockHelper().getItemFromID(id);
                            if (args.length >= 2) {
                                try {
                                    int count = Integer.parseInt(args[1]);
                                    if (count < 1) {
                                        sender.sendMessage(ChatColor.RED + "The amount \"" + args[1] + "\" must be greater than 0!");
                                        return true;
                                    }
                                    item.setAmount(count);
                                } catch (NumberFormatException e) {
                                    sender.sendMessage(ChatColor.RED + "\"" + args[1] + "\" is not a valid amount!");
                                    return true;
                                }
                            }
                        } catch (NbtApiException | NullPointerException e) {
                            sender.sendMessage(ChatColor.RED + "The \"item\" tag for the custom block \"" + id + "\" is invalid or null!");
                            console.severe(ChatColor.RED + "The \"item\" tag for the custom block \"" + id + "\" is invalid or null");
                            return true;
                        }

                        if (item != null) {
                            if (((Player) sender).getInventory().addItem(item).isEmpty()) {
                                sender.sendMessage("[WorldOfZombies] " + ChatColor.GREEN + "Obtained " + item.getAmount() + " of the item " + ChatColor.YELLOW + id);
                            } else {
                                if (item.getAmount() == 1) {
                                    sender.sendMessage(ChatColor.RED + "The item \"" + id + "\" could not be added to your inventory because it is full!");
                                } else {
                                    sender.sendMessage(ChatColor.RED + "More than one of the item \"" + id + "\" could not be added to your inventory because it is full!");
                                }
                            }
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "\"" + id + "\" is not a valid custom item!");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "This command cannot be run while the custom blocks module is disabled!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be run by players!");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command!");
        }

        return true;
    }
}

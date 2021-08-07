package me.woz.customplugins.commands.woz;

import com.comphenix.protocol.events.PacketContainer;
import me.woz.customplugins.WorldOfZombies;
import me.woz.customplugins.commands.SubCommand;
import me.woz.customplugins.modules.customblocks.CustomBlockHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.logging.Logger;

public class GetCustomItemCommand implements SubCommand {
    
    private final WorldOfZombies main;
    private final CustomBlockHandler customBlockHandler;
    private final Logger console;

    private Set<String> itemStrings;

    public GetCustomItemCommand(WorldOfZombies main, CustomBlockHandler customBlockHandler) {
        this.main = main;
        this.customBlockHandler = customBlockHandler;
        console = main.getLogger();
        itemStrings = customBlockHandler.getIdToDefinitionFile().keySet();
    }

    public void reloadItems() {
        itemStrings = customBlockHandler.getIdToDefinitionFile().keySet();
    }

    @Override
    public boolean subCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        if (sender.hasPermission("worldofzombies.command.reload")) {
            if (sender instanceof Player) {
                if (customBlockHandler != null) {
                    if (args.length == 0) {
                        throw new IllegalArgumentException("Invalid command");
                    }

                    String id = args[1];
                    if (itemStrings.contains(id)) {
                        if (!((Player) sender).getInventory().addItem(customBlockHandler.getItemFromID(id)).isEmpty()) {
                            sender.sendMessage(ChatColor.RED + "The item \"" + id + "\" could not be added to your inventory because it is full!");
                        }
                        return true;
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

        return false;
    }
}

package me.mackblue.worldofzombies.commands;

import me.mackblue.worldofzombies.WorldOfZombies;
import me.mackblue.worldofzombies.modules.customblocks.CustomBlockEvents;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SCommandTab implements TabCompleter {

    /*
    This class was taken and modified with permission from its author, ryandw11
    https://github.com/ryandw11/CustomStructures/blob/9404ddafbc6e0200c2ad6672d15b10ca5868f365/src/main/java/com/ryandw11/structure/commands/SCommandTab.java
     */

    private final WorldOfZombies main;
    private final CustomBlockEvents customBlockEvents;

    private List<String> customItems;

    public SCommandTab(WorldOfZombies main, CustomBlockEvents customBlockEvents) {
        this.main = main;
        this.customBlockEvents = customBlockEvents;
        reloadCompletions();
    }

    public void reloadCompletions() {
        customItems = new ArrayList<>(customBlockEvents.getIdToDefinitionFile().keySet());
    }

    //handles tab completion based on the length of the currently typed arguments
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2 && args[0].equalsIgnoreCase("database")) {
            completions = new ArrayList<>(Arrays.asList("delete", "clone", "confirm"));
            completions = getApplicableTabCompleter(args[1], completions, args[0], sender);

        } else if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            completions = getApplicableTabCompleter(args[1], customItems, args[0], sender);

        } else if (args[0].equalsIgnoreCase("item")) {
            if (args.length == 2) {
                completions = new ArrayList<>(Arrays.asList("nbt"));
                completions = getApplicableTabCompleter(args[1], completions, args[0], sender);
            } else if (args.length == 3) {
                completions = new ArrayList<>(Arrays.asList("-yaml"));
                completions = getApplicableTabCompleter(args[2], completions, args[0], sender);
            }

        } else if (args.length <= 1) {
            completions = new ArrayList<>(Arrays.asList("reload", "info", "database", "get", "item"));
            completions = getApplicableTabCompleter(args.length == 1 ? args[0] : "", completions, args[0], sender);
        }

        Collections.sort(completions);
        return completions;
    }

    //handles tab completion while the user is typing a word by filtering the full list of completions based on what is already typed and the player's permissions
    private List<String> getApplicableTabCompleter(String arg, List<String> completions, String subCmd, CommandSender sender) {
        List<String> valid = new ArrayList<>();
        if (arg == null || arg.equalsIgnoreCase("")) {
            for (String posib : completions) {
                if (!subCmd.equalsIgnoreCase(arg)) {
                    //if the checked arg isn't the name of a sub command (not the 1st arg), check the permission of the sub command
                    if (sender.hasPermission("worldofzombies.command." + subCmd)) {
                        valid.add(posib);
                    }
                } else if (sender.hasPermission("worldofzombies.command." + posib)) {
                    valid.add(posib);
                }
            }
            return valid;
        }

        for (String posib : completions) {
            if (posib.startsWith(arg)) {
                //if the checked arg is the name of a sub command being typed, check the permission of the full sub command (posib)
                if (subCmd.equalsIgnoreCase(arg)) {
                    subCmd = posib;
                }

                if (sender.hasPermission("worldofzombies.command." + subCmd)) {
                    valid.add(posib);
                }
            }
        }
        return valid;
    }
}

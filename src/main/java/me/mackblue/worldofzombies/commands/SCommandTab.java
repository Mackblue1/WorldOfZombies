package me.mackblue.worldofzombies.commands;

import me.mackblue.worldofzombies.WorldOfZombies;
import me.mackblue.worldofzombies.modules.customblocks.CustomBlockEvents;
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
            completions = getApplicableTabCompleter(args[1], completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            completions = getApplicableTabCompleter(args[1], customItems);
        } else if (args[0].equalsIgnoreCase("item")) {
            if (args.length == 2) {
                completions = new ArrayList<>(Arrays.asList("nbt"));
                completions = getApplicableTabCompleter(args[1], completions);
            } else if (args.length == 3) {
                completions = new ArrayList<>(Arrays.asList("-yaml"));
                completions = getApplicableTabCompleter(args[2], completions);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("")) {

        } else if (args.length <= 1) {
            completions = new ArrayList<>(Arrays.asList("reload", "database", "info", "get", "item"));
            completions = getApplicableTabCompleter(args.length == 1 ? args[0] : "", completions);
        }
        Collections.sort(completions);
        return completions;
    }

    //handles tab completion while the user is typing a word by filtering the full list of completions based on what is already typed
    private List<String> getApplicableTabCompleter(String arg, List<String> completions) {
        if (arg == null || arg.equalsIgnoreCase("")) {
            return completions;
        }
        List<String> valid = new ArrayList<>();
        for (String posib : completions) {
            if (posib.startsWith(arg)) {
                valid.add(posib);
            }
        }
        return valid;
    }
}

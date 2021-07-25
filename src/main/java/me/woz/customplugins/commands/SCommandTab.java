package me.woz.customplugins.commands;

import me.woz.customplugins.WorldOfZombies;
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

    public SCommandTab(WorldOfZombies main) {
        this.main = main;
    }

    //handles tab completion based on the length of the currently typed arguments
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2 && args[0].equalsIgnoreCase("database")) {
            completions = new ArrayList<>(Arrays.asList("delete", "clone", "confirm"));
            completions = getApplicableTabCompleter(args[1], completions);
        } else if (args.length <= 1) {
            completions = new ArrayList<>(Arrays.asList("reload", "database", "info"));
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

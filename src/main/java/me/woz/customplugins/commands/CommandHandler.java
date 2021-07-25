package me.woz.customplugins.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

public class CommandHandler {

    /*
    This class was taken and modified with permission from its author, ryandw11
    https://github.com/ryandw11/CustomStructures/blob/9404ddafbc6e0200c2ad6672d15b10ca5868f365/src/main/java/com/ryandw11/structure/commands/CommandHandler.java
     */

    private final Map<List<String>, SubCommand> commandMap;
    private final Map<List<String>, List<String>> commandInfoMap;

    public CommandHandler() {
        commandMap = new LinkedHashMap<>();
        commandInfoMap = new LinkedHashMap<>();
    }

    //registers a new command without divisions based on arguments, like /woz reload
    public void registerCommand(String s, SubCommand subCommand, String parametersSetup, String description) {
        if (commandMap.containsKey(Collections.singletonList(s.toLowerCase()))) {
            throw new IllegalArgumentException("Command already exists!");
        }

        commandMap.put(Collections.singletonList(s.toLowerCase()), subCommand);
        commandInfoMap.put(Collections.singletonList(s.toLowerCase()), Arrays.asList(parametersSetup, description));
    }

    //registers a new command with divisions based on arguments, like /woz database delete
    public void registerMultiArgCommand(SubCommand subCommand, String parametersSetup, String description, String... args) {
        List<String> list = new ArrayList<>(Arrays.asList(args));
        list = list.stream().map(String::toLowerCase).collect(Collectors.toList());
        if (commandMap.containsKey(list)) {
            throw new IllegalArgumentException("Command already exists!");
        }

        commandMap.put(list, subCommand);
        commandInfoMap.put(list, Arrays.asList(parametersSetup, description));
    }

    //runs the correct subCommand based on the arguments of the original command
    public boolean handleCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (args.length == 0) {
            return false;
        }

        for (Map.Entry<List<String>, SubCommand> entry : commandMap.entrySet()) {
            if (entry.getKey().contains(args[0].toLowerCase())) {
                //should work for getting multi-arg command setup, but doesn't pass the 2nd arg which is used to determine something like: /woz database [what is this]
                /*int i = checkArgsAgainstMap(entry, args, sender);
                if (i == -1) {
                    continue;
                }*/
                String[] newArgs = new String[args.length - 1];
                if (newArgs.length > 0) {
                    System.arraycopy(args, 1, newArgs, 0, newArgs.length);
                }
                return entry.getValue().subCommand(sender, cmd, s, newArgs);
            }
        }
        throw new IllegalArgumentException("Invalid command");
    }

    //checks if all arguments in a map entry and argument list are the same (stopping when either one runs out of elements)
    public int checkArgsAgainstMap(Map.Entry<List<String>, SubCommand> entry, String[] args, CommandSender sender) {
        int i = 0;
        while (entry.getKey().size() - 1 >= i && args.length - 1 >= i) {
            //sender.sendMessage("" + entry.getKey() + ":  " + i);
            if (entry.getKey().contains(args[i].toLowerCase())) {
                //sender.sendMessage("yes");
                i++;
            } else {
                //sender.sendMessage("no");
                return -1;
            }
        }

        return i;
    }

    public Map<List<String>, List<String>> getCommandInfoMap() {
        return commandInfoMap;
    }
}

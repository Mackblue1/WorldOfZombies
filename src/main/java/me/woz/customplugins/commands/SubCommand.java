package me.woz.customplugins.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public interface SubCommand {

    /*
    This interface was taken and modified with permission from its author, ryandw11
    https://github.com/ryandw11/CustomStructures/blob/9404ddafbc6e0200c2ad6672d15b10ca5868f365/src/main/java/com/ryandw11/structure/commands/SubCommand.java
     */

    boolean subCommand(CommandSender sender, Command cmd, String alias, String[] args);
}

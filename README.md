# World of Zombies Custom Plugin

The primary custom plugin for the (unfinished) World of Zombies Minecraft server.

This plugin primarily features a fully custom block system using packet manipulation, meaning users can define unlimited custom blocks with unique properties without interfering with any of the existing blocks in the game. Custom blocks are defined by detailed YAML configuration files, with all options listed and documented in the [demo.yml](src/main/resources/CustomItems/demo.yml) example config. These config options allow the user to curate the appearance, properties (like blast resistance, item drops, and break effects), and functionality (like syncing block states) for custom blocks. With these abilities, users can easily and precisely design blocks that are far from possible in this version of the base game, including functionality for complex multi-block items like custom doors and beds. Each custom block that exists in the world corresponds to a saved entry in one of many YAML database files, which are organized based on their Minecraft "chunk" coordinates and divided into smaller "subchunks" for faster searching.  

This plugin also features various commands with corresponding permissions that can be used to use custom blocks and interact with the block database from within the game. To monitor the status of the plugin from the server terminal, this plugin also features an extensive logging system using different "debug levels" to control the frequency and types of messages that are logged.


## Native API Version

[Purpur API](https://purpurmc.org/) - 1.16.5  
(Compiled with Java 8)



# Installation Guide

This plugin requires a Minecraft server to be set up using the Purpur plugin loader. For information about setting up Purpur and using plugins, see [this guide](https://purpurmc.org/docs/purpur/installation/).

## Plugin Dependencies

  - [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)
  - [NBTAPI](https://www.spigotmc.org/resources/nbt-api.7939/)


### Version History

- For a full version history starting from version 1.1.0, see the [changelog](./src/main/resources/changelog.txt)

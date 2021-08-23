package me.mackblue.worldofzombies.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.logging.Logger;

public class MultiBlockChangeWrap {

    private static ProtocolManager pm;
    private static Logger console;
    private static int debug;

    private final PacketContainer packet;
    private BlockPosition subChunkPos;
    private final ArrayList<WrappedBlockData> blockData;
    private final ArrayList<Short> blockLocs;

    public static void init(ProtocolManager manager, Logger theConsole, int debugMode) {
        pm = manager;
        console = theConsole;
        setDebug(debugMode);
    }

    public static void setDebug(int debugMode) {
        debug = debugMode;
    }

    //creates a new packet and initializes all fields except subChunkPos
    public MultiBlockChangeWrap() {
        packet = pm.createPacket(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
        blockData = new ArrayList<>();
        blockLocs = new ArrayList<>();
    }

    //creates a new packet and initializes all fields - directly uses given pre-converted subChunk coords for subChunkPos
    public MultiBlockChangeWrap(int x, int y, int z) {
        packet = pm.createPacket(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
        blockData = new ArrayList<>();
        blockLocs = new ArrayList<>();
        subChunkPos = new BlockPosition(x, y, z);
        packet.getSectionPositions().write(0, subChunkPos);
    }

    //creates a new packet and initializes all fields - converts a given location to subChunk coords for subChunkPos
    public MultiBlockChangeWrap(Location loc) {
        packet = pm.createPacket(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
        blockData = new ArrayList<>();
        blockLocs = new ArrayList<>();
        subChunkPos = new BlockPosition(loc.getChunk().getX(), loc.getBlockY() >> 4, loc.getChunk().getZ());
        packet.getSectionPositions().write(0, subChunkPos);
    }

    //sets subChunkPos to given pre-converted subChunk coords and re-writes to packet
    public void setSubChunkPos(int x, int y, int z) {
        subChunkPos = new BlockPosition(x, y, z);
        packet.getSectionPositions().write(0, subChunkPos);
    }

    //adds a BlockData and local subChunk position to the packet fields
    public void addBlock(Location location, BlockData data) {
        blockData.add(WrappedBlockData.createData(data));

        int x = location.getBlockX();
        x = x & 0xF;
        int y = location.getBlockY();
        y = y & 0xF;
        int z = location.getBlockZ();
        z = z & 0xF;
        blockLocs.add((short) (x << 8 | z << 4 | y));
    }

    //NOT USED - subChunkList replaced by file-based logger
    //writes required fields to the packet, sends the packet to a player, and removes itself from the provided subChunks array
    /*public void sendPacket(MultiBlockChangeWrap[][][] subChunks, int arrX, int arrZ, int arrY, Player player) {
        if (subChunkPos == null) {
            console.warning(ChatColor.YELLOW + "Could not send a packet with " + blockData.size() + " blocks to " + player + " because subChunkPos was not initialized");
            return;
        }

        //convert ArrayLists into arrays used by packet
        WrappedBlockData[] blockDataArr = blockData.toArray(new WrappedBlockData[0]);
        Short[] blockLocsShort = blockLocs.toArray(new Short[0]);
        short[] blockLocsArr = ArrayUtils.toPrimitive(blockLocsShort);

        //write arrays to packet
        packet.getBlockDataArrays().writeSafely(0, blockDataArr);
        packet.getShortArrays().writeSafely(0, blockLocsArr);

        try {
            pm.sendServerPacket(player, packet);
            subChunks[arrX][arrZ][arrY] = null;

            if (debug >= 1) {
                console.info(ChatColor.AQUA + "Sent a packet with " + blockData.size() + " blocks in the subchunk at  " + (arrX - 4) + ", " + (arrZ - 4) + ", " + arrY + " by " + player.getName());
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }*/

    //writes required fields to the packet, and sends the packet to a player if it has more than 0 blocks
    public void sendPacket(Player player) {
        if (subChunkPos == null) {
            console.warning(ChatColor.YELLOW + "Could not send a packet with " + blockData.size() + " blocks to " + player + " because subChunkPos was not initialized");
            return;
        }

        //convert ArrayLists into arrays used by packet
        WrappedBlockData[] blockDataArr = blockData.toArray(new WrappedBlockData[0]);
        Short[] blockLocsShort = blockLocs.toArray(new Short[0]);
        short[] blockLocsArr = ArrayUtils.toPrimitive(blockLocsShort);

        //write arrays to packet
        packet.getBlockDataArrays().writeSafely(0, blockDataArr);
        packet.getShortArrays().writeSafely(0, blockLocsArr);

        try {
            if (blockData.size() != 0) {
                pm.sendServerPacket(player, packet);
                if (debug >= 1) {
                    console.info(ChatColor.DARK_GREEN + "Sent a packet with " + blockData.size() + " blocks in the subchunk at " + subChunkPos.getX() + ", " + subChunkPos.getY() + ", " + subChunkPos.getZ() + " to " + player.getName());
                }
            } else if (debug >= 2) {
                console.warning(ChatColor.YELLOW + "Did not send a packet in the subchunk at " + subChunkPos.getX() + ", " + subChunkPos.getY() + ", " + subChunkPos.getZ() + " to " + player.getName() + " because it was empty");
            }
        } catch (InvocationTargetException e) {
            console.severe(ChatColor.RED + "An error occurred while trying to send a packet with " + blockData.size() + " blocks in the subchunk at " + subChunkPos.getX() + ", " + subChunkPos.getY() + ", " + subChunkPos.getZ() + " to " + player.getName());
            e.printStackTrace();
        }
    }

    public PacketContainer getPacket() {
        return packet;
    }

    public BlockPosition getSubChunkPos() {
        return subChunkPos;
    }

    public ArrayList<WrappedBlockData> getBlockData() {
        return blockData;
    }

    public ArrayList<Short> getBlockLocs() {
        return blockLocs;
    }
}

package me.woz.customplugins.commands;

import com.destroystokyo.paper.entity.ai.*;
import me.woz.customplugins.WorldOfZombies;
import me.woz.customplugins.modules.customblocks.CustomBlockHandler;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.Collection;
import java.util.EnumSet;
import java.util.logging.Logger;

public class TestCommand implements CommandExecutor {

    private final WorldOfZombies main;
    private final Logger console;
    private final CustomBlockHandler customBlockHandler;

    public TestCommand(WorldOfZombies main, CustomBlockHandler handler) {
        this.main = main;
        this.console = main.getLogger();
        this.customBlockHandler = handler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        if (sender.hasPermission("worldofzombies.command.*")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                return true;
            }

            Player player = (Player) sender;
            World world = player.getWorld();
            Chunk chunk = player.getChunk();

            Block target = player.getTargetBlock(10);
            //BlockData data = target.getBlockData();

            customBlockHandler.moveLoggedBlock(target.getLocation(), target.getLocation().add(0, 1, 0));

            //player.sendMessage(ChatColor.GREEN + "" + target.getDrops(player.getInventory().getItemInMainHand(), player));

            /*player.sendMessage(ChatColor.AQUA + "BlockData string:  " + data.getAsString());
            player.sendMessage(ChatColor.YELLOW + "WrappedBlockData type:  " + WrappedBlockData.createData(data).getType().toString());
            player.sendMessage(ChatColor.DARK_PURPLE + "id:  " + customBlockHandler.getLoggedStringFromLocation(target.getLocation()));
            player.sendMessage(ChatColor.GREEN + "NBT item:  " + NBTItem.convertItemtoNBT(player.getInventory().getItemInMainHand()).toString());*/
            /*String s = data.getAsString().contains("waterlogged=true") ? "[waterlogged=false]" : "[waterlogged=true]";
            data = data.merge(Bukkit.createBlockData(data.getMaterial().toString().toLowerCase() + s));
            target.setBlockData(data);
            player.sendMessage(ChatColor.GREEN + "after: " + data.getAsString());*/

            //loader.test(player.getInventory().getItemInMainHand());

            /*player.sendMessage("" + chunk.getX() + "   " + chunk.getZ());
            player.sendBlockChange(player.getLocation(), Bukkit.createBlockData(Material.IRON_BLOCK));*/

            /*loader.MultiBlockChange(player, 0, 0, player.getLocation().getBlockY()>>4, player);
            loader.LoadCustomBlocksFromChunkFile(player, chunk);*/

            /*Zombie attacker = (Zombie) world.spawnEntity(player.getLocation(), EntityType.ZOMBIE);
            Pig pig = (Pig) world.spawnEntity(player.getLocation(), EntityType.PIG);

            MobGoals mobGoals = Bukkit.getMobGoals();
            mobGoals.addGoal(attacker, 1, new ZombieTargetOtherGoal(main, attacker, Pig.class));*/

        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command!");
        }

        return true;
    }

    static class ZombieTargetOtherGoal implements Goal<Zombie> {
        private final GoalKey<Zombie> key;
        private final Mob self;
        private final Class<? extends Mob> targetClass;

        public ZombieTargetOtherGoal(WorldOfZombies main, Zombie self, Class<? extends Mob> target) {
            this.key = GoalKey.of(Zombie.class, new NamespacedKey(main, "target_other"));
            this.self = self;
            this.targetClass = target;
        }

        @Override
        public boolean shouldActivate() {
            LivingEntity target = getNearestTarget();
            return target != null;
        }

        @Override
        public boolean shouldStayActive() {
            return shouldActivate();
        }

        @Override
        public void start() {}

        @Override
        public void stop() {
            self.getPathfinder().stopPathfinding();
            self.setTarget(null);
        }

        @Override
        public void tick() {
            LivingEntity target = getNearestTarget();
            if (target != null) {
                self.setTarget(target);
            } else {
                self.getPathfinder().stopPathfinding();
            }
        }

        @Override
        public GoalKey<Zombie> getKey() {
            return key;
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.TARGET);
        }

        public LivingEntity getNearestTarget() {
            Collection<Entity> nearbyTargets = self.getLocation().getNearbyEntitiesByType(targetClass, 10);
            Location loc = self.getLocation();
            Entity closestEntity;
            double closestDistance;

            if (!(nearbyTargets.isEmpty())) {
                closestEntity = nearbyTargets.iterator().next();
                closestDistance = loc.distance(closestEntity.getLocation());
            } else {
                return null;
            }

            for (Entity entity : nearbyTargets) {
                double distance = loc.distance(entity.getLocation());
                if (distance < closestDistance) {
                    closestEntity = entity;
                    closestDistance = distance;
                }
            }

            return (LivingEntity) closestEntity;
        }
    }
}
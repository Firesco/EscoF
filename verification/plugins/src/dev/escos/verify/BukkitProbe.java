package dev.escos.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Uses Bukkit/Spigot APIs and plugin.yml. No NMS or Paper-only API dependency. */
public final class BukkitProbe extends JavaPlugin implements Listener {
    private boolean cancelSpawn, cancelTeleport;
    private int spawnCancellations, teleportCancellations, commands, checks;
    @Override public void onEnable() {
        check(Bukkit.isPrimaryThread(), "plugin enable thread");
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("escoprobe").setExecutor((sender,command,label,args) -> {
            check(Bukkit.isPrimaryThread(), "command thread"); ++this.commands; return true;
        });
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { probe(); } catch (Throwable error) { failed(error); }
        }, 40);
    }
    private void probe() {
        check(Bukkit.isPrimaryThread(), "scheduled task thread");
        World world = Bukkit.getWorlds().getFirst();
        Location origin = new Location(world, 5, 90, 5);
        this.cancelSpawn = true;
        Chicken canceled = world.spawn(origin, Chicken.class);
        this.cancelSpawn = false;
        check(this.spawnCancellations == 1 && !canceled.isValid(), "spawn cancellation");
        ArmorStand entity = world.spawn(origin, ArmorStand.class, e -> e.setGravity(false));
        NamespacedKey key = new NamespacedKey(this, "marker");
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, "esco-ok");
        this.cancelTeleport = true;
        check(!entity.teleport(origin.clone().add(10,0,0)), "teleport cancellation result");
        this.cancelTeleport = false;
        check(this.teleportCancellations == 1 && entity.getLocation().distanceSquared(origin) < .0001, "teleport cancellation position");
        World other = Bukkit.createWorld(new WorldCreator("compat_other").type(WorldType.FLAT).generateStructures(false));
        check(other != null, "world creation");
        check(entity.teleport(new Location(other, 3,90,3)), "cross-world teleport");
        check(entity.getWorld() == other, "teleport destination");
        check("esco-ok".equals(entity.getPersistentDataContainer().get(key,PersistentDataType.STRING)), "persistent entity data");
        other.getBlockAt(3,90,3).setType(Material.CHEST, false);
        ((Chest)other.getBlockAt(3,90,3).getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND,7));
        check(entity.teleport(origin), "return teleport");
        entity.remove();
        other.save();
        check(Bukkit.unloadWorld(other,true), "world unload");
        World reloaded = Bukkit.createWorld(new WorldCreator("compat_other"));
        check(reloaded.getBlockAt(3,90,3).getType() == Material.CHEST, "block persisted after reload");
        check(((Chest)reloaded.getBlockAt(3,90,3).getState()).getBlockInventory().contains(Material.DIAMOND,7), "inventory persisted after reload");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "escoprobe");
        check(this.commands == 1, "plugin command dispatch");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                check(!Bukkit.isPrimaryThread(), "async scheduler thread");
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        check(Bukkit.isPrimaryThread(), "return to sync scheduler");
                        Files.writeString(Path.of("compat-bukkit.json"), "{\"status\":\"passed\",\"checks\":"+this.checks+"}");
                        getLogger().info("BUKKIT_PROBE_PASS checks="+this.checks);
                    } catch (Throwable error) { failed(error); }
                });
            } catch (Throwable error) { failed(error); }
        });
    }
    @EventHandler public void spawn(CreatureSpawnEvent event) {
        if (this.cancelSpawn) {
            check(Bukkit.isPrimaryThread() && !event.isAsynchronous(), "synchronous spawn event");
            event.setCancelled(true); ++this.spawnCancellations;
        }
    }
    @EventHandler public void teleport(EntityTeleportEvent event) {
        if (this.cancelTeleport) {
            check(Bukkit.isPrimaryThread() && !event.isAsynchronous(), "synchronous teleport event");
            event.setCancelled(true); ++this.teleportCancellations;
        }
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); ++this.checks; }
    private void failed(Throwable error) {
        error.printStackTrace();
        try { Files.writeString(Path.of("compat-bukkit.json"), "{\"status\":\"failed\"}"); }
        catch (Exception writeError) { throw new RuntimeException(writeError); }
    }
}

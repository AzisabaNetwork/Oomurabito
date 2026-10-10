package net.azisaba.oomurabito;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public final class BossManager implements Listener {
    private final OomurabitoPlugin plugin;
    private OomurabitoBoss currentBoss;
    private BossIntroCutscene currentIntro;
    private BukkitRunnable pendingIntroTask;
    // Bosses whose entity is already dead but whose death finale is still playing.
    private final java.util.List<OomurabitoBoss> finishing = new java.util.ArrayList<>();

    BossManager(OomurabitoPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean spawn(Player player) {
        return spawn(player.getLocation().clone());
    }

    public boolean spawn(Location center) {
        if (currentBoss != null && currentBoss.isAlive()) {
            return false;
        }

        currentBoss = new OomurabitoBoss(plugin, this, center);
        if (plugin.bossConfig().introEnabled()) {
            int lockTicks = plugin.bossConfig().introDurationTicks() + Math.max(0, plugin.bossConfig().introStartDelayTicks());
            currentBoss.spawn(lockTicks);
            scheduleIntroWhenPlayersDetected(currentBoss, center.clone());
        } else {
            currentBoss.spawn();
        }
        return true;
    }

    public boolean killCurrent() {
        if (currentBoss == null) {
            return false;
        }
        cleanupPendingIntro();
        cleanupIntro();
        currentBoss.remove();
        currentBoss = null;
        return true;
    }

    public OomurabitoBoss currentBoss() {
        if (currentBoss != null && !currentBoss.isAlive()) {
            currentBoss = null;
        }
        return currentBoss;
    }

    void clearIfCurrent(OomurabitoBoss boss) {
        if (currentBoss == boss) {
            cleanupPendingIntro();
            cleanupIntro();
            currentBoss = null;
        }
    }

    void finished(OomurabitoBoss boss) {
        finishing.remove(boss);
    }

    void shutdown() {
        cleanupPendingIntro();
        cleanupIntro();
        for (OomurabitoBoss boss : new java.util.ArrayList<>(finishing)) {
            boss.remove();
        }
        finishing.clear();
        if (currentBoss != null) {
            currentBoss.remove();
            currentBoss = null;
        }
    }

    private java.util.List<Player> introPlayers(Location center) {
        double radiusSquared = Math.pow(plugin.bossConfig().arenaRadius() + 16.0, 2.0);
        return center.getWorld().getPlayers().stream()
                .filter(player -> !player.isDead())
                .filter(player -> player.getLocation().distanceSquared(center) <= radiusSquared)
                .toList();
    }

    private void scheduleIntroWhenPlayersDetected(OomurabitoBoss boss, Location center) {
        cleanupPendingIntro();
        pendingIntroTask = new BukkitRunnable() {
            private int delayTicks = -1;

            @Override
            public void run() {
                if (currentBoss != boss || !boss.isAlive()) {
                    cancel();
                    pendingIntroTask = null;
                    return;
                }
                java.util.List<Player> players = introPlayers(center);
                if (players.isEmpty()) {
                    delayTicks = -1;
                    return;
                }
                if (delayTicks < 0) {
                    delayTicks = Math.max(0, plugin.bossConfig().introStartDelayTicks());
                }
                if (delayTicks-- > 0) {
                    return;
                }
                currentIntro = new BossIntroCutscene(plugin, boss, center, players);
                currentIntro.start(() -> currentIntro = null);
                cancel();
                pendingIntroTask = null;
            }
        };
        pendingIntroTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void cleanupPendingIntro() {
        if (pendingIntroTask != null) {
            pendingIntroTask.cancel();
            pendingIntroTask = null;
        }
    }

    private void cleanupIntro() {
        if (currentIntro != null) {
            currentIntro.cleanup();
            currentIntro = null;
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        OomurabitoBoss boss = currentBoss();
        if (boss != null && boss.isBoss(event.getEntity())) {
            boss.onDamage(event);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        OomurabitoBoss boss = currentBoss;
        Entity entity = event.getEntity();
        if (boss != null && boss.isBoss(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            boss.onDeath();
            if (boss.isFinishing()) {
                finishing.add(boss);
            }
            cleanupIntro();
            currentBoss = null;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (currentIntro != null) {
            currentIntro.handleQuit(event.getPlayer());
        }
    }
}

package net.azisaba.oomurabito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BossIntroCutscene {
    private final OomurabitoPlugin plugin;
    private final BossConfig config;
    private final OomurabitoBoss boss;
    private final Location center;
    private final List<CutsceneViewer> viewers = new ArrayList<>();
    private TextDisplay titleDisplay;
    private BukkitRunnable task;
    private int tick;
    private int titleAge = -1;
    private boolean finished;

    BossIntroCutscene(OomurabitoPlugin plugin, OomurabitoBoss boss, Location center, List<Player> players) {
        this.plugin = plugin;
        this.config = plugin.bossConfig();
        this.boss = boss;
        this.center = center.clone();

        for (Player player : players) {
            viewers.add(new CutsceneViewer(player));
        }
    }

    public void start(Runnable onFinish) {
        if (viewers.isEmpty()) {
            onFinish.run();
            return;
        }

        for (CutsceneViewer viewer : viewers) {
            viewer.start();
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick(onFinish);
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    public void cleanup() {
        if (finished) {
            return;
        }
        finished = true;
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (titleDisplay != null && titleDisplay.isValid()) {
            titleDisplay.remove();
        }
        for (CutsceneViewer viewer : viewers) {
            viewer.restore();
        }
        viewers.clear();
    }

    public void handleQuit(Player player) {
        for (CutsceneViewer viewer : viewers) {
            if (viewer.playerId.equals(player.getUniqueId())) {
                viewer.restore();
                return;
            }
        }
    }

    private void tick(Runnable onFinish) {
        tick++;
        Location bossLocation = boss.entity().getLocation().clone();
        if (tick >= config.introTitleAppearTick()) {
            if (titleDisplay == null || !titleDisplay.isValid()) {
                spawnTitle(bossLocation);
                titleAge = 0;
            } else {
                titleAge++;
            }
            updateTitle(bossLocation);
        }

        for (CutsceneViewer viewer : viewers) {
            viewer.tickCamera(tick, bossLocation);
        }
        faceBossToCamera();

        if (tick == config.introRoarTick()) {
            playIntroSound(bossLocation, Sound.ENTITY_RAVAGER_ROAR, 0.65f);
            bossLocation.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, bossLocation.add(0.0, 2.0, 0.0), 40, 1.4, 0.9, 1.4, 0.02);
        }
        if (tick == config.introSlamTick()) {
            if (titleDisplay == null || !titleDisplay.isValid()) {
                spawnTitle(bossLocation);
                titleAge = 0;
            }
            playIntroSound(bossLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.9f);
            bossLocation.getWorld().spawnParticle(Particle.EXPLOSION, bossLocation.add(0.0, 0.8, 0.0), 3, 1.4, 0.2, 1.4, 0.0);
            for (CutsceneViewer viewer : viewers) {
                viewer.slamShakeTicks = Math.max(viewer.slamShakeTicks, config.introCameraShakeTicks());
            }
        }

        if (tick >= config.introDurationTicks()) {
            cleanup();
            onFinish.run();
        }
    }

    private void spawnTitle(Location bossLocation) {
        Location titleLocation = bossLocation.clone().add(0.0, config.introTitleHeight(), 0.0);
        titleDisplay = (TextDisplay) center.getWorld().spawnEntity(titleLocation, EntityType.TEXT_DISPLAY);
        titleDisplay.text(Component.text(config.name(), NamedTextColor.GOLD));
        titleDisplay.setBillboard(Display.Billboard.CENTER);
        titleDisplay.setSeeThrough(true);
        titleDisplay.setShadowed(true);
        titleDisplay.setLineWidth(160);
        setTitleScale(0.05);
    }

    private void updateTitle(Location bossLocation) {
        if (titleDisplay == null || !titleDisplay.isValid()) {
            return;
        }
        titleDisplay.teleport(bossLocation.add(0.0, config.introTitleHeight(), 0.0));
        double progress = Math.max(0.0, Math.min(1.0, titleAge / (double) Math.max(1, config.introTitlePopTicks())));
        double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
        double overshoot = progress < 1.0 ? Math.sin(progress * Math.PI) * 0.25 : 0.0;
        setTitleScale(config.introTitleScale() * (eased + overshoot));
    }

    private void setTitleScale(double scale) {
        if (titleDisplay == null || !titleDisplay.isValid()) {
            return;
        }
        float value = (float) Math.max(0.05, scale);
        titleDisplay.setTransformation(new org.bukkit.util.Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(value, value, value),
                new Quaternionf()
        ));
    }

    private void playIntroSound(Location location, Sound sound, float pitch) {
        location.getWorld().playSound(location, sound, Math.max(0.1f, config.soundVolume()), pitch);
    }

    private void faceBossToCamera() {
        if (viewers.isEmpty()) {
            return;
        }
        ArmorStand camera = viewers.getFirst().camera;
        if (camera == null || !camera.isValid()) {
            return;
        }
        Location bossLocation = boss.entity().getLocation();
        Vector direction = camera.getLocation().toVector().subtract(bossLocation.toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() <= 0.01) {
            return;
        }
        Location facing = bossLocation.clone();
        facing.setDirection(direction.normalize());
        boss.entity().teleport(facing);
    }

    private final class CutsceneViewer {
        private final UUID playerId;
        private final GameMode previousGameMode;
        private final Location previousLocation;
        private final Entity previousSpectatorTarget;
        private final Vector baseAway;
        private ArmorStand camera;
        private int slamShakeTicks;

        private CutsceneViewer(Player player) {
            this.playerId = player.getUniqueId();
            this.previousGameMode = player.getGameMode();
            this.previousLocation = player.getLocation().clone();
            this.previousSpectatorTarget = player.getSpectatorTarget();
            Vector away = player.getLocation().toVector().subtract(center.toVector());
            away.setY(0.0);
            if (away.lengthSquared() <= 0.01) {
                away = new Vector(1.0, 0.0, 0.0);
            }
            this.baseAway = away.normalize();
        }

        private void start() {
            Player player = player();
            if (player == null) {
                return;
            }
            Location cameraLocation = cameraLocation(boss.entity().getLocation(), 0.0, 0);
            camera = (ArmorStand) player.getWorld().spawnEntity(cameraLocation, EntityType.ARMOR_STAND);
            camera.setInvisible(true);
            camera.setInvulnerable(true);
            camera.setGravity(false);
            camera.setSilent(true);
            camera.setMarker(true);
            player.setGameMode(GameMode.SPECTATOR);
            player.setSpectatorTarget(camera);
        }

        private void tickCamera(int tick, Location bossLocation) {
            Player player = player();
            if (player == null || camera == null || !camera.isValid()) {
                return;
            }
            double shake = 0.0;
            if (slamShakeTicks > 0) {
                shake = Math.sin(slamShakeTicks * 1.7) * 0.45;
                slamShakeTicks--;
            }
            Location cameraLocation = cameraLocation(bossLocation, shake, tick);
            camera.teleport(cameraLocation);
            if (player.getSpectatorTarget() != camera) {
                player.setSpectatorTarget(camera);
            }
        }

        private void restore() {
            Player player = player();
            if (player != null) {
                player.setSpectatorTarget(previousSpectatorTarget);
                player.setGameMode(previousGameMode);
                player.teleport(previousLocation);
            }
            if (camera != null && camera.isValid()) {
                camera.remove();
            }
        }

        private Player player() {
            return plugin.getServer().getPlayer(playerId);
        }

        private Location cameraLocation(Location bossLocation, double shake, int tick) {
            double progress = Math.max(0.0, Math.min(1.0, tick / (double) Math.max(1, config.introDurationTicks())));
            double orbitRadians = Math.toRadians(config.introCameraOrbitDegrees()) * progress;
            Vector away = rotateY(baseAway, orbitRadians);
            double distance = lerp(config.introCameraDistance(), config.introCameraEndDistance(), progress);
            Location cameraLocation = bossLocation.clone()
                    .add(away.multiply(distance + shake))
                    .add(0.0, config.introCameraHeight() + Math.abs(shake) * 0.35, 0.0);
            cameraLocation.setDirection(bossLocation.clone().add(0.0, 1.6, 0.0).toVector().subtract(cameraLocation.toVector()));
            return cameraLocation;
        }

        private Vector rotateY(Vector vector, double radians) {
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            return new Vector(vector.getX() * cos - vector.getZ() * sin, 0.0, vector.getX() * sin + vector.getZ() * cos).normalize();
        }

        private double lerp(double from, double to, double progress) {
            return from + (to - from) * progress;
        }
    }
}

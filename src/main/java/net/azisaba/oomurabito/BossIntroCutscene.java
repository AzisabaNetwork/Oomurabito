package net.azisaba.oomurabito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Three shots: the boss descending out of the sky seen from far below, a closer orbit as it winds up and dives,
 * and a low hero shot that cuts in on the landing and holds for the title.
 */
public final class BossIntroCutscene {
    private static final int SHOTS = 3;
    private static final int DIVE_TICKS = 8;
    private static final int GROUND_HOLD_TICKS = 12;
    /** Display entities need a moment on the client before a transformation change can be interpolated. */
    private static final int SETTLE_TICKS = 2;

    private final OomurabitoPlugin plugin;
    private final BossConfig config;
    private final OomurabitoBoss boss;
    private final Fx fx;
    private final Location ground;
    private final int durationTicks;
    private final int roarTick;
    private final int slamTick;
    private final int titleTick;
    private final List<CutsceneViewer> viewers = new ArrayList<>();
    private TextDisplay titleDisplay;
    private TextDisplay subtitleDisplay;
    private BukkitRunnable task;
    private int tick;
    private int titleAge = -1;
    private int shakeTicks;
    private boolean finished;

    BossIntroCutscene(OomurabitoPlugin plugin, OomurabitoBoss boss, Location center, List<Player> players) {
        this.plugin = plugin;
        this.config = plugin.bossConfig();
        this.boss = boss;
        this.fx = new Fx(config.soundVolume());
        this.ground = boss.safeGroundLocation(center);
        this.ground.setYaw(0.0f);
        this.ground.setPitch(0.0f);
        this.durationTicks = Math.max(40, config.introDurationTicks());
        this.slamTick = clamp(config.introSlamTick(), DIVE_TICKS + 12, durationTicks - 8);
        this.roarTick = clamp(config.introRoarTick(), 2, slamTick - DIVE_TICKS - 2);
        this.titleTick = clamp(config.introTitleAppearTick(), slamTick, durationTicks - 1);

        for (Player player : players) {
            viewers.add(new CutsceneViewer(player));
        }
    }

    public void start(Runnable onFinish) {
        if (viewers.isEmpty()) {
            onFinish.run();
            return;
        }

        boss.beginCutscene();
        boss.entity().teleport(bossPose());
        for (CutsceneViewer viewer : viewers) {
            viewer.start();
        }
        Location body = boss.body();
        fx.sound(body, Sound.BLOCK_BELL_RESONATE, 1.0, 0.6f);
        fx.sound(body, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8, 0.5f);

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
        removeDisplay(titleDisplay);
        removeDisplay(subtitleDisplay);
        for (CutsceneViewer viewer : viewers) {
            viewer.restore();
        }
        viewers.clear();
        boss.endCutscene();
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
        if (!boss.isAlive()) {
            cleanup();
            onFinish.run();
            return;
        }

        boss.entity().teleport(bossPose());
        Location body = boss.body();
        directBoss(body);

        int shot = tick < roarTick ? 0 : tick < slamTick ? 1 : 2;
        double shotProgress = switch (shot) {
            case 0 -> tick / (double) roarTick;
            case 1 -> (tick - roarTick) / (double) (slamTick - roarTick);
            default -> (tick - slamTick) / (double) (durationTicks - slamTick);
        };
        double shake = 0.0;
        if (shakeTicks > 0) {
            shake = shakeTicks / (double) Math.max(1, config.introCameraShakeTicks());
            shakeTicks--;
        }
        for (CutsceneViewer viewer : viewers) {
            viewer.tickCamera(shot, shotProgress, body, shake);
        }

        if (tick >= titleTick) {
            tickTitle(boss.entity().getLocation());
        }

        if (tick >= durationTicks) {
            cleanup();
            onFinish.run();
        }
    }

    /** The boss sinks out of the sky, rears up, dives into the ground, and rises back to its fighting height. */
    private Location bossPose() {
        double floor = ground.getY();
        double hover = floor + config.hoverHeight();
        int diveStart = slamTick - DIVE_TICKS;
        int riseStart = slamTick + GROUND_HOLD_TICKS;
        double y;
        if (tick <= roarTick) {
            y = lerp(floor + config.skyHeight(), hover + 2.0, Easing.inOut(tick / (double) roarTick));
        } else if (tick <= diveStart) {
            y = lerp(hover + 2.0, hover + 4.0, Easing.out((tick - roarTick) / (double) Math.max(1, diveStart - roarTick)));
        } else if (tick < slamTick) {
            y = lerp(hover + 4.0, floor, Easing.in((tick - diveStart) / (double) DIVE_TICKS));
        } else if (tick <= riseStart) {
            y = floor;
        } else {
            y = lerp(floor, hover, Easing.inOut((tick - riseStart) / (double) Math.max(1, durationTicks - riseStart)));
        }

        Location pose = ground.clone();
        pose.setY(y);
        if (!viewers.isEmpty()) {
            // The boss plays to the first viewer's camera.
            Vector toCamera = viewers.getFirst().cameraPosition().toVector().subtract(pose.toVector());
            toCamera.setY(0.0);
            if (toCamera.lengthSquared() > 0.01) {
                pose.setDirection(toCamera);
            }
        }
        return pose;
    }

    private void directBoss(Location body) {
        int diveStart = slamTick - DIVE_TICKS;
        if (tick < roarTick) {
            fx.spawn(Particle.CLOUD, body, 3, 1.0, 0.3, 1.0, 0.01);
            if (tick % 12 == 0) {
                fx.sound(body, Sound.ENTITY_PHANTOM_FLAP, 0.6, 0.5f);
            }
        }
        if (tick == roarTick) {
            boss.shakeHead();
            boss.punchScale(0.3);
            fx.sound(body, Sound.ENTITY_RAVAGER_ROAR, 1.0, 0.65f);
            fx.spawn(Particle.ANGRY_VILLAGER, body, 40, 1.4, 0.9, 1.4, 0.02);
            fx.radialBurst(Particle.CLOUD, body, 24, 0.5, 0.0);
            shakeTicks = Math.max(shakeTicks, config.introCameraShakeTicks() / 2);
        }
        if (tick == roarTick + 1) {
            // Curls up while it gathers height for the dive.
            boss.scaleTarget(0.85);
        }
        if (tick > roarTick && tick <= diveStart) {
            fx.spawn(Particle.ENCHANT, body, 8, 0.3, 0.3, 0.3, 3.5);
        }
        if (tick == diveStart) {
            boss.scaleTarget(1.15);
            fx.sound(body, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0, 0.7f);
        }
        if (tick > diveStart && tick < slamTick) {
            fx.dust(body, Color.WHITE, 2.0f, 8, 0.8);
        }
        if (tick == slamTick) {
            Location impact = ground.clone().add(0.0, 0.08, 0.0);
            boss.squash(0.6);
            boss.scaleTarget(1.0);
            boss.impactEffect(impact, 2.0);
            boss.ringEffect(impact, 14.0, 18);
            fx.sound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0, 0.9f);
            fx.sound(impact, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0, 0.7f);
            fx.sound(impact, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6, 0.7f);
            shakeTicks = Math.max(shakeTicks, config.introCameraShakeTicks());
        }
    }

    private void tickTitle(Location bossLocation) {
        Location titleLocation = bossLocation.clone().add(0.0, config.introTitleHeight(), 0.0);
        titleLocation.setYaw(0.0f);
        titleLocation.setPitch(0.0f);
        float scale = (float) config.introTitleScale();
        float subtitleScale = scale * 0.4f;
        int popTicks = Math.max(1, config.introTitlePopTicks());

        if (titleDisplay == null || !titleDisplay.isValid()) {
            titleDisplay = spawnText(titleLocation, Component.text(config.name(), NamedTextColor.GOLD, TextDecoration.BOLD));
            subtitleDisplay = spawnText(titleLocation, Component.text("― " + config.phaseOneTitle() + " ―", NamedTextColor.WHITE));
            titleAge = 0;
            fx.sound(titleLocation, Sound.BLOCK_BELL_USE, 1.0, 0.5f);
        } else {
            titleAge++;
            titleDisplay.teleport(titleLocation);
            if (subtitleDisplay != null && subtitleDisplay.isValid()) {
                subtitleDisplay.teleport(titleLocation);
            }
        }

        // Slams in past full size, then settles. The subtitle follows a beat later.
        if (titleAge == SETTLE_TICKS) {
            morph(titleDisplay, text(scale * 1.25f, 0.0f), popTicks);
        }
        if (titleAge == SETTLE_TICKS + popTicks) {
            morph(titleDisplay, text(scale, 0.0f), 4);
        }
        if (titleAge == SETTLE_TICKS + popTicks + 2) {
            morph(subtitleDisplay, text(subtitleScale, -(0.25f * subtitleScale + 0.1f)), 5);
        }
    }

    private TextDisplay spawnText(Location location, Component text) {
        return location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setLineWidth(400);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(2);
            display.setPersistent(false);
            display.setTransformation(text(0.02f, 0.0f));
        });
    }

    private Transformation text(float scale, float offsetY) {
        return new Transformation(new Vector3f(0.0f, offsetY, 0.0f), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private void morph(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private void removeDisplay(Display display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private final class CutsceneViewer {
        private final UUID playerId;
        private final GameMode previousGameMode;
        private final Location previousLocation;
        private final Entity previousSpectatorTarget;
        private final Vector baseAway;
        // One camera per shot: switching between them is a clean cut, while each one glides on its own.
        private final ItemDisplay[] cameras = new ItemDisplay[SHOTS];
        private int activeShot;

        private CutsceneViewer(Player player) {
            this.playerId = player.getUniqueId();
            this.previousGameMode = player.getGameMode();
            this.previousLocation = player.getLocation().clone();
            this.previousSpectatorTarget = player.getSpectatorTarget();
            Vector away = player.getLocation().toVector().subtract(ground.toVector());
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
            Location body = boss.body();
            for (int shot = 0; shot < SHOTS; shot++) {
                cameras[shot] = player.getWorld().spawn(cameraLocation(shot, 0.0, body, 0.0), ItemDisplay.class, display -> {
                    // The client eases between the per-tick positions, which is what keeps the camera from stuttering.
                    display.setTeleportDuration(2);
                    display.setPersistent(false);
                });
            }
            activeShot = 0;
            player.setGameMode(GameMode.SPECTATOR);
            player.setSpectatorTarget(cameras[0]);
        }

        private Location cameraPosition() {
            ItemDisplay camera = cameras[activeShot];
            return camera != null && camera.isValid() ? camera.getLocation() : ground.clone().add(baseAway.clone().multiply(config.introCameraDistance()));
        }

        private void tickCamera(int shot, double shotProgress, Location body, double shake) {
            Player player = player();
            if (player == null) {
                return;
            }
            // Upcoming cameras keep tracking the boss from their opening position, so a cut lands already framed.
            for (int index = shot; index < SHOTS; index++) {
                ItemDisplay camera = cameras[index];
                if (camera != null && camera.isValid()) {
                    camera.teleport(cameraLocation(index, index == shot ? shotProgress : 0.0, body, index == shot ? shake : 0.0));
                }
            }
            ItemDisplay camera = cameras[shot];
            if (camera == null || !camera.isValid()) {
                return;
            }
            activeShot = shot;
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
            for (ItemDisplay camera : cameras) {
                if (camera != null && camera.isValid()) {
                    camera.remove();
                }
            }
        }

        private Player player() {
            return plugin.getServer().getPlayer(playerId);
        }

        private Location cameraLocation(int shot, double shotProgress, Location body, double shake) {
            double progress = Easing.clamp(shotProgress);
            double orbit = Math.toRadians(config.introCameraOrbitDegrees());
            double angle;
            double distance;
            double height;
            switch (shot) {
                case 0 -> {
                    // Far away and low, looking up at the descent.
                    angle = lerp(-orbit * 0.5, -orbit * 0.2, progress);
                    distance = config.introCameraDistance() * lerp(1.6, 1.35, progress);
                    height = lerp(1.2, 2.0, progress);
                }
                case 1 -> {
                    // Closer, circling, and high enough to follow the dive down.
                    angle = lerp(orbit * 0.3, orbit, Easing.inOut(progress));
                    distance = config.introCameraDistance() * lerp(1.0, 0.9, progress);
                    height = config.introCameraHeight() + 2.0;
                }
                default -> {
                    // Head on from the ground, pushing in on the boss as it rises.
                    angle = 0.0;
                    distance = config.introCameraEndDistance() * lerp(1.0, 0.85, Easing.out(progress));
                    height = 1.0;
                }
            }

            Location cameraLocation = ground.clone()
                    .add(rotateY(baseAway, angle).multiply(distance))
                    .add(0.0, height, 0.0);
            if (shake > 0.0) {
                cameraLocation.add(Math.sin(tick * 2.3) * 0.45 * shake, Math.cos(tick * 3.1) * 0.3 * shake, Math.sin(tick * 1.7) * 0.45 * shake);
            }
            cameraLocation.setDirection(body.toVector().subtract(cameraLocation.toVector()));
            return cameraLocation;
        }

        private Vector rotateY(Vector vector, double radians) {
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            return new Vector(vector.getX() * cos - vector.getZ() * sin, 0.0, vector.getX() * sin + vector.getZ() * cos).normalize();
        }
    }
}

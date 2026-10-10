package net.azisaba.oomurabito;

import java.util.Collection;
import java.util.function.Consumer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Particle and sound helpers. Every particle is sent with force=true so the arena stays readable from far away.
 */
final class Fx {
    private static final double TWO_PI = Math.PI * 2.0;

    private final float baseVolume;

    Fx(float baseVolume) {
        this.baseVolume = baseVolume;
    }

    /** weight 1.0 is a headline sound heard across the arena; small accents use a fraction of it. */
    void sound(Location location, Sound sound, double weight, float pitch) {
        location.getWorld().playSound(location, sound, (float) Math.max(0.05, baseVolume * weight), pitch);
    }

    void personalSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    void spawn(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, null, true);
    }

    <T> void spawn(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data, true);
    }

    /** A single particle that flies along direction. Only works for particles that honor velocity. */
    void directed(Particle particle, Location location, Vector direction, double speed) {
        spawn(particle, location, 0, direction.getX(), direction.getY(), direction.getZ(), speed);
    }

    void dust(Location location, Color color, float size, int count, double spread) {
        spawn(Particle.DUST, location, count, spread, spread, spread, 0.0, new Particle.DustOptions(color, size));
    }

    void dustFade(Location location, Color from, Color to, float size, int count, double spread) {
        spawn(Particle.DUST_COLOR_TRANSITION, location, count, spread, spread, spread, 0.0, new Particle.DustTransition(from, to, size));
    }

    void flash(Location location, Color color) {
        spawn(Particle.FLASH, location, 1, 0.0, 0.0, 0.0, 0.0, color);
    }

    /** Calls point for evenly spaced positions on a horizontal circle; density is points per block of circumference. */
    void ring(Location center, double radius, double density, int minimumPoints, Consumer<Location> point) {
        int points = Math.max(minimumPoints, (int) Math.ceil(TWO_PI * radius * density));
        for (int index = 0; index < points; index++) {
            double angle = TWO_PI * index / points;
            point.accept(center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
        }
    }

    void dustRing(Location center, double radius, Color color, float size) {
        ring(center, radius, 1.2, 12, point -> dust(point, color, size, 1, 0.0));
    }

    /** Particles thrown outward along the ground from center. */
    void radialBurst(Particle particle, Location center, int count, double speed, double lift) {
        for (int index = 0; index < count; index++) {
            double angle = TWO_PI * index / count;
            directed(particle, center, new Vector(Math.cos(angle), lift, Math.sin(angle)), speed);
        }
    }

    void sphereBurst(Particle particle, Location center, int count, double speed) {
        for (int index = 0; index < count; index++) {
            directed(particle, center, Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize(), speed);
        }
    }

    void line(Location start, Location end, double step, Consumer<Location> point) {
        Vector delta = end.toVector().subtract(start.toVector());
        double length = delta.length();
        if (length <= 0.01) {
            return;
        }
        Vector unit = delta.normalize().multiply(step);
        Location cursor = start.clone();
        for (double traveled = 0.0; traveled < length; traveled += step) {
            point.accept(cursor.clone());
            cursor.add(unit);
        }
    }

    /** Fragments of whatever block the location is standing on. Nothing is thrown up from thin air. */
    void debris(Location ground, int count, double spread, double speed) {
        BlockData data = ground.clone().add(0.0, -0.5, 0.0).getBlock().getBlockData();
        if (data.getMaterial().isAir()) {
            return;
        }
        spawn(Particle.BLOCK, ground.clone().add(0.0, 0.2, 0.0), count, spread, 0.15, spread, speed, data);
    }

    /** Camera kick without damage, for players close enough to feel an impact. */
    void shake(Collection<Player> players, Location source, double radius) {
        double radiusSquared = radius * radius;
        for (Player player : players) {
            if (player.getWorld() == source.getWorld() && player.getLocation().distanceSquared(source) <= radiusSquared) {
                player.sendHurtAnimation(0.0f);
            }
        }
    }
}

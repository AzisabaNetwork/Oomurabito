package net.azisaba.oomurabito;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Short-lived display entity effects. The owner ticks this once per server tick and clears it on cleanup.
 */
final class Visuals {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final float VIEW_RANGE = 3.0f;
    /** A freshly spawned display must reach the client before a transformation change can be interpolated. */
    private static final int SETTLE_TICKS = 2;
    private static final int FADE_TICKS = 3;

    interface Visual {
        /** @return false once the visual is finished and should be removed */
        boolean tick();

        void remove();
    }

    private final List<Visual> active = new ArrayList<>();

    <T extends Visual> T add(T visual) {
        active.add(visual);
        return visual;
    }

    void tick() {
        Iterator<Visual> iterator = active.iterator();
        while (iterator.hasNext()) {
            Visual visual = iterator.next();
            if (!visual.tick()) {
                visual.remove();
                iterator.remove();
            }
        }
    }

    void clear() {
        for (Visual visual : active) {
            visual.remove();
        }
        active.clear();
    }

    private static BlockDisplay block(Location at, BlockData data, boolean fullBright, Transformation initial) {
        Location location = at.clone();
        location.setYaw(0.0f);
        location.setPitch(0.0f);
        return location.getWorld().spawn(location, BlockDisplay.class, display -> {
            display.setBlock(data);
            if (fullBright) {
                display.setBrightness(new Display.Brightness(15, 15));
            }
            display.setViewRange(VIEW_RANGE);
            display.setPersistent(false);
            display.setTransformation(initial);
        });
    }

    private static void morph(Display display, Transformation transformation, int ticks) {
        if (!display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, ticks));
        display.setTransformation(transformation);
    }

    private static void discard(Display display) {
        if (display.isValid()) {
            display.remove();
        }
    }

    /** A flat polygon ring on the ground that grows from one radius to another, then holds and fades. */
    static final class Ring implements Visual {
        private static final int SEGMENTS = 24;

        private final BlockDisplay[] segments = new BlockDisplay[SEGMENTS];
        private final double toRadius;
        private final double thickness;
        private final double height;
        private final int growTicks;
        private final int holdTicks;
        private int age;

        Ring(Location center, Material material, double fromRadius, double toRadius, double thickness, double height,
             int growTicks, int holdTicks) {
            this.toRadius = toRadius;
            this.thickness = thickness;
            this.height = height;
            this.growTicks = Math.max(0, growTicks);
            this.holdTicks = Math.max(0, holdTicks);
            BlockData data = material.createBlockData();
            for (int index = 0; index < SEGMENTS; index++) {
                segments[index] = block(center, data, true, segment(index, fromRadius, height));
            }
        }

        @Override
        public boolean tick() {
            age++;
            if (age == SETTLE_TICKS) {
                morphAll(height, growTicks);
            }
            int end = SETTLE_TICKS + growTicks + holdTicks;
            if (age == end) {
                morphAll(0.0, FADE_TICKS);
            }
            return age < end + FADE_TICKS;
        }

        @Override
        public void remove() {
            for (BlockDisplay segment : segments) {
                discard(segment);
            }
        }

        private void morphAll(double segmentHeight, int ticks) {
            for (int index = 0; index < SEGMENTS; index++) {
                morph(segments[index], segment(index, toRadius, segmentHeight), ticks);
            }
        }

        private Transformation segment(int index, double radius, double segmentHeight) {
            double phi = TWO_PI * index / SEGMENTS;
            double sin = Math.sin(phi);
            double cos = Math.cos(phi);
            double length = 2.0 * (radius + thickness / 2.0) * Math.tan(Math.PI / SEGMENTS);
            double inner = radius - thickness / 2.0;
            // Local +X runs along the tangent and local +Z points outward.
            Vector3f translation = new Vector3f(
                    (float) (-length / 2.0 * sin + inner * cos),
                    0.0f,
                    (float) (length / 2.0 * cos + inner * sin)
            );
            return new Transformation(
                    translation,
                    new Quaternionf().rotationY((float) (Math.PI / 2.0 - phi)),
                    new Vector3f((float) length, (float) segmentHeight, (float) thickness),
                    new Quaternionf()
            );
        }
    }

    /** A square ground marker that pops in after a delay. */
    static final class Marker implements Visual {
        private final BlockDisplay display;
        private final double size;
        private final double height;
        private final int showAge;
        private final int hideAge;
        private int age;

        Marker(Location ground, Material material, double size, double height, int delayTicks, int lifeTicks) {
            this.size = size;
            this.height = height;
            this.showAge = Math.max(SETTLE_TICKS, delayTicks);
            this.hideAge = Math.max(showAge + 1, lifeTicks);
            this.display = block(ground, material.createBlockData(), true, square(0.0));
        }

        @Override
        public boolean tick() {
            age++;
            if (age == showAge) {
                morph(display, square(size), 3);
            }
            if (age == hideAge) {
                morph(display, square(0.0), FADE_TICKS);
            }
            return age < hideAge + FADE_TICKS;
        }

        @Override
        public void remove() {
            discard(display);
        }

        private Transformation square(double side) {
            return new Transformation(
                    new Vector3f((float) (-side / 2.0), 0.0f, (float) (-side / 2.0)),
                    new Quaternionf(),
                    new Vector3f((float) side, (float) height, (float) side),
                    new Quaternionf()
            );
        }
    }

    /** A ground strip pointing away from its start, re-aimable every tick. */
    static final class Strip implements Visual {
        private final BlockDisplay display;
        private final double length;
        private final double height;
        private Vector direction = new Vector(1.0, 0.0, 0.0);
        private double width;
        private boolean dirty;
        private int age;
        private int closingTicks = -1;

        Strip(Location start, Material material, double length, double height) {
            this.length = length;
            this.height = height;
            this.display = block(start, material.createBlockData(), true, strip());
        }

        void aim(Vector direction, double width) {
            if (direction.lengthSquared() <= 0.0001) {
                return;
            }
            this.direction = direction.clone();
            this.width = width;
            this.dirty = true;
        }

        void close() {
            if (closingTicks < 0) {
                closingTicks = 0;
                width = 0.0;
                morph(display, strip(), FADE_TICKS);
            }
        }

        @Override
        public boolean tick() {
            age++;
            if (closingTicks >= 0) {
                return ++closingTicks <= FADE_TICKS;
            }
            if (dirty && age >= SETTLE_TICKS) {
                dirty = false;
                morph(display, strip(), 2);
            }
            return true;
        }

        @Override
        public void remove() {
            discard(display);
        }

        private Transformation strip() {
            double theta = Math.atan2(-direction.getZ(), direction.getX());
            // Local +X runs along the direction; the strip is centered on it.
            Vector3f translation = new Vector3f(
                    (float) (-width / 2.0 * Math.sin(theta)),
                    0.0f,
                    (float) (-width / 2.0 * Math.cos(theta))
            );
            return new Transformation(
                    translation,
                    new Quaternionf().rotationY((float) theta),
                    new Vector3f((float) length, (float) height, (float) width),
                    new Quaternionf()
            );
        }
    }

    /** A solid beam made of a bright core inside a translucent sleeve. */
    static final class Beam implements Visual {
        private final BlockDisplay core;
        private final BlockDisplay glow;
        private Vector direction = new Vector(0.0, -1.0, 0.0);
        private double length;
        private double coreWidth;
        private double glowWidth;
        private boolean dirty;
        private int age;
        private int closingTicks = -1;

        Beam(Location start, Material coreMaterial, Material glowMaterial) {
            this.core = block(start, coreMaterial.createBlockData(), true, beam(0.0));
            this.glow = block(start, glowMaterial.createBlockData(), true, beam(0.0));
        }

        void aim(Vector direction, double length, double coreWidth, double glowWidth) {
            if (direction.lengthSquared() <= 0.0001) {
                return;
            }
            this.direction = direction.clone().normalize();
            this.length = length;
            this.coreWidth = coreWidth;
            this.glowWidth = glowWidth;
            this.dirty = true;
        }

        void coreMaterial(Material material) {
            if (core.isValid()) {
                core.setBlock(material.createBlockData());
            }
        }

        void close() {
            if (closingTicks < 0) {
                closingTicks = 0;
                morph(core, beam(0.0), FADE_TICKS);
                morph(glow, beam(0.0), FADE_TICKS);
            }
        }

        @Override
        public boolean tick() {
            age++;
            if (closingTicks >= 0) {
                return ++closingTicks <= FADE_TICKS;
            }
            if (dirty && age >= SETTLE_TICKS) {
                dirty = false;
                morph(core, beam(coreWidth), 2);
                morph(glow, beam(glowWidth), 2);
            }
            return true;
        }

        @Override
        public void remove() {
            discard(core);
            discard(glow);
        }

        private Transformation beam(double width) {
            // Local +Z runs along the beam; the cross-section is centered on it.
            Quaternionf rotation = new Quaternionf().rotationTo(
                    0.0f, 0.0f, 1.0f,
                    (float) direction.getX(), (float) direction.getY(), (float) direction.getZ()
            );
            Vector3f translation = rotation.transform(new Vector3f((float) (-width / 2.0), (float) (-width / 2.0), 0.0f));
            return new Transformation(
                    translation,
                    rotation,
                    new Vector3f((float) width, (float) width, (float) length),
                    new Quaternionf()
            );
        }
    }

    /** Chunks of the ground that heave up around an impact and sink back. The world itself is not modified. */
    static final class Rubble implements Visual {
        private static final int RISE_TICKS = 3;
        private static final int HOLD_TICKS = 10;
        private static final int SINK_TICKS = 8;

        private final List<BlockDisplay> blocks = new ArrayList<>();
        private final List<Transformation> raised = new ArrayList<>();
        private final List<Transformation> sunk = new ArrayList<>();
        private int age;

        Rubble(Location center, double radius, int count, double size, Random random) {
            World world = center.getWorld();
            for (int index = 0; index < count; index++) {
                double angle = TWO_PI * (index + random.nextDouble() * 0.6) / count;
                double distance = radius * (0.85 + random.nextDouble() * 0.3);
                double x = center.getX() + Math.cos(angle) * distance;
                double z = center.getZ() + Math.sin(angle) * distance;
                int surface = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
                BlockData data = world.getBlockAt((int) Math.floor(x), surface, (int) Math.floor(z)).getBlockData();
                if (data.getMaterial().isAir()) {
                    data = Material.STONE.createBlockData();
                }

                double side = size * (0.6 + random.nextDouble() * 0.6);
                Quaternionf rotation = new Quaternionf().rotationXYZ(
                        (float) (random.nextDouble() - 0.5),
                        (float) (random.nextDouble() * TWO_PI),
                        (float) (random.nextDouble() - 0.5)
                );
                Transformation hidden = chunk(rotation, side, -side * 0.95);
                sunk.add(hidden);
                raised.add(chunk(rotation, side, side * (0.1 + random.nextDouble() * 0.3)));
                // The entity sits just above the surface so it is lit like the ground, not like the inside of a block.
                blocks.add(block(new Location(world, x, surface + 1.05, z), data, false, hidden));
            }
        }

        @Override
        public boolean tick() {
            age++;
            if (age == SETTLE_TICKS) {
                morphAll(raised, RISE_TICKS);
            }
            int sinkAge = SETTLE_TICKS + RISE_TICKS + HOLD_TICKS;
            if (age == sinkAge) {
                morphAll(sunk, SINK_TICKS);
            }
            return age < sinkAge + SINK_TICKS;
        }

        @Override
        public void remove() {
            for (BlockDisplay block : blocks) {
                discard(block);
            }
        }

        private void morphAll(List<Transformation> targets, int ticks) {
            for (int index = 0; index < blocks.size(); index++) {
                morph(blocks.get(index), targets.get(index), ticks);
            }
        }

        private static Transformation chunk(Quaternionf rotation, double side, double centerY) {
            float half = (float) (side / 2.0);
            Vector3f translation = new Vector3f(0.0f, (float) centerY, 0.0f).sub(rotation.transform(new Vector3f(half, half, half)));
            return new Transformation(
                    translation,
                    new Quaternionf(rotation),
                    new Vector3f((float) side, (float) side, (float) side),
                    new Quaternionf()
            );
        }
    }
}

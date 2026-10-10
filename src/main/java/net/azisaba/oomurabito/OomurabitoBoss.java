package net.azisaba.oomurabito;

import dev.twme.textdisplayshape.bukkit.BukkitTriangle;
import dev.twme.textdisplayshape.shape.Shape;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

public final class OomurabitoBoss {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double SVG_RAY_LENGTH = 340.0;
    private static final LightRaySpec[] LIGHT_RAYS = {
            new LightRaySpec(0.0, 55.0, 0.13, 255, 255, 230, 22.0, 0.55, 255, 255, 245),
            new LightRaySpec(40.0, 55.0, 0.12, 255, 252, 220, 20.0, 0.48, 255, 255, 240),
            new LightRaySpec(80.0, 60.0, 0.10, 255, 248, 210, 18.0, 0.62, 255, 255, 250),
            new LightRaySpec(120.0, 50.0, 0.14, 255, 253, 225, 24.0, 0.50, 255, 255, 238),
            new LightRaySpec(160.0, 58.0, 0.11, 255, 250, 215, 20.0, 0.60, 255, 255, 248),
            new LightRaySpec(200.0, 52.0, 0.13, 255, 251, 218, 22.0, 0.52, 255, 255, 242),
            new LightRaySpec(240.0, 56.0, 0.10, 255, 249, 212, 19.0, 0.58, 255, 255, 246),
            new LightRaySpec(280.0, 50.0, 0.15, 255, 252, 222, 23.0, 0.54, 255, 255, 240),
            new LightRaySpec(320.0, 54.0, 0.11, 255, 250, 216, 21.0, 0.56, 255, 255, 244)
    };

    private final OomurabitoPlugin plugin;
    private final BossManager bossManager;
    private final BossConfig config;
    private final Random random = new Random();
    private final Location center;
    private final List<Entity> ownedEntities = new ArrayList<>();
    private final List<VillagerProjectile> projectiles = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final List<Shape> deathLightRayShapes = new ArrayList<>();
    private final Map<UUID, Deque<Location>> playerHistory = new HashMap<>();
    private final Map<UUID, Integer> spinHitCooldowns = new HashMap<>();

    private LivingEntity boss;
    private UUID bossId;
    private BossBar bossBar;
    private BukkitRunnable task;
    private Attack currentAttack = Attack.IDLE;
    private Attack lastAttack = Attack.IDLE;
    private int attackTick;
    private int idleTicks;
    private int noPlayerTicks;
    private int introLockTicks;
    private boolean invulnerable;
    private boolean phaseTwoTransformed;
    private boolean dying;
    private int deathTick;
    private Location deathStart;

    private int teleportCount;
    private int teleportLimit;
    private int chargeCount;
    private int chargePhaseTick;
    private int stompCount;
    private Location stompTarget;
    private Location movementStart;
    private Location movementEnd;
    private List<Vector> fangDirections = List.of();
    private Vector chargeDirection = new Vector();
    private Vector spinDirection = new Vector(1.0, 0.0, 0.0);
    private int positioningTicksRemaining;

    OomurabitoBoss(OomurabitoPlugin plugin, BossManager bossManager, Location center) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.config = plugin.bossConfig();
        this.center = center.clone();
    }

    public void spawn() {
        spawn(0);
    }

    public void spawn(int introLockTicks) {
        World world = center.getWorld();
        Location spawnLocation = safeGroundLocation(center).add(0.0, config.hoverHeight(), 0.0);
        boss = (LivingEntity) world.spawnEntity(spawnLocation, EntityType.VILLAGER);
        configureBossEntity(boss);
        boss.setHealth(config.maxHealth());
        this.introLockTicks = Math.max(0, introLockTicks);

        bossBar = Bukkit.createBossBar(config.name(), BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
        idleTicks = config.phaseOneIdleTicks();

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    public boolean isAlive() {
        return boss != null && !boss.isDead() && boss.isValid();
    }

    public boolean isBoss(Entity entity) {
        return bossId != null && entity.getUniqueId().equals(bossId);
    }

    public LivingEntity entity() {
        return boss;
    }

    public void onDamage(EntityDamageEvent event) {
        if (invulnerable) {
            event.setCancelled(true);
            boss.getWorld().spawnParticle(Particle.END_ROD, boss.getLocation().add(0.0, 1.5, 0.0), 10, 0.7, 0.7, 0.7, 0.02);
            return;
        }
        double guardMult = guardMultiplier();
        if (guardMult <= 0.0) {
            event.setCancelled(true);
            boss.getWorld().spawnParticle(Particle.END_ROD, boss.getLocation().add(0.0, 1.5, 0.0), 10, 0.7, 0.7, 0.7, 0.02);
            return;
        }
        if (guardMult < 1.0) {
            event.setDamage(event.getDamage() * guardMult);
        }
        if (boss.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            startDeathAnimation();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, (Runnable) this::updateBossBar);
    }

    public void onDeath() {
        cleanup(false);
    }

    public void remove() {
        cleanup(true);
    }

    public String statusLine() {
        return String.format(
                Locale.ROOT,
                "大村人 HP %.1f/%.1f Phase %d Attack %s Center %.1f %.1f %.1f",
                boss.getHealth(),
                config.maxHealth(),
                phase(),
                currentAttack.name().toLowerCase(Locale.ROOT),
                center.getX(),
                center.getY(),
                center.getZ()
        );
    }

    private void tick() {
        if (!isAlive()) {
            cleanup(false);
            bossManager.clearIfCurrent(this);
            return;
        }

        if (dying) {
            List<Player> players = activePlayers();
            updateDeathBossBar(players);
            tickShockwaves(players);
            tickDeathAnimation();
            return;
        }

        if (phase() == 2 && !phaseTwoTransformed) {
            transformToEvoker();
        }

        List<Player> players = activePlayers();
        recordPlayerHistory(players);
        updateBossBar(players);
        tickProjectiles(players);
        tickShockwaves(players);

        if (introLockTicks > 0) {
            introLockTicks--;
            faceNearestPlayer(players);
            return;
        }

        if (players.isEmpty()) {
            noPlayerTicks++;
            if (noPlayerTicks >= config.despawnSecondsWithoutPlayers() * 20) {
                cleanup(true);
                bossManager.clearIfCurrent(this);
            }
            return;
        }
        noPlayerTicks = 0;

        if (currentAttack == Attack.IDLE) {
            hoverNearCenter();
            faceNearestPlayer(players);
            idleTicks--;
            if (idleTicks <= 0) {
                Attack nextAttack = chooseAttack();
                if (nextAttack == Attack.IDLE) {
                    idleTicks = 20;
                } else {
                    beginAttack(nextAttack, players);
                }
            }
            return;
        }

        if (positioningTicksRemaining > 0) {
            int totalTicks = Math.max(1, config.groundAttackPositionTicks());
            int elapsedTicks = totalTicks - positioningTicksRemaining + 1;
            moveBossBetween(movementStart, movementEnd, elapsedTicks, totalTicks);
            faceNearestPlayer(players);
            positioningTicksRemaining--;
            if (positioningTicksRemaining <= 0 && movementEnd != null) {
                teleport(movementEnd);
            }
            return;
        }

        attackTick++;
        switch (currentAttack) {
            case TELEPORT -> tickTeleport();
            case KNOCKBACK -> tickKnockback(players);
            case GOLEM -> tickGolem();
            case VILLAGER_CANNON -> tickVillagerCannon(players);
            case CHARGE -> tickCharge(players);
            case BEAM -> tickBeam(players);
            case STOMP -> tickStomp(players);
            case FANG -> tickFang(players);
            case SPIN -> tickSpin(players);
            default -> endAttack();
        }
    }

    private List<Player> activePlayers() {
        double radiusSquared = Math.pow(config.arenaRadius() + 16.0, 2.0);
        return center.getWorld().getPlayers().stream()
                .filter(player -> player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                .filter(player -> !player.isDead())
                .filter(player -> player.getLocation().distanceSquared(center) <= radiusSquared)
                .sorted(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(boss.getLocation())))
                .toList();
    }

    private void recordPlayerHistory(List<Player> players) {
        int maxHistory = Math.max(Math.max(config.cannonHomingDelayTicks(), config.beamTargetDelayTicks()), config.spinTargetDelayTicks()) + 5;
        for (Player player : players) {
            Deque<Location> history = playerHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
            history.addFirst(player.getLocation().clone());
            while (history.size() > maxHistory) {
                history.removeLast();
            }
        }
        playerHistory.keySet().removeIf(uuid -> players.stream().noneMatch(player -> player.getUniqueId().equals(uuid)));
    }

    private Location delayedLocation(Player player, int delayTicks) {
        return delayedLocation(player.getUniqueId(), delayTicks, player.getLocation());
    }

    private Location delayedLocation(UUID playerId, int delayTicks, Location fallback) {
        Deque<Location> history = playerHistory.get(playerId);
        if (history == null || history.isEmpty()) {
            return fallback.clone();
        }
        int index = Math.max(0, delayTicks);
        int current = 0;
        for (Location location : history) {
            if (current >= index) {
                return location.clone();
            }
            current++;
        }
        return history.getLast().clone();
    }

    private Attack chooseAttack() {
        List<WeightedAttack> candidates = attackCandidates(true);
        if (candidates.isEmpty()) {
            candidates = attackCandidates(false);
        }
        double totalWeight = candidates.stream().mapToDouble(WeightedAttack::weight).sum();
        if (totalWeight <= 0.0) {
            return Attack.IDLE;
        }

        double roll = random.nextDouble() * totalWeight;
        for (WeightedAttack candidate : candidates) {
            roll -= candidate.weight();
            if (roll <= 0.0) {
                return candidate.attack();
            }
        }
        return candidates.getLast().attack();
    }

    private List<WeightedAttack> attackCandidates(boolean excludeLastAttack) {
        List<WeightedAttack> candidates = new ArrayList<>();
        int phase = phase();
        addWeighted(candidates, Attack.TELEPORT, attackWeight(Attack.TELEPORT, phase), excludeLastAttack);
        addWeighted(candidates, Attack.KNOCKBACK, attackWeight(Attack.KNOCKBACK, phase), excludeLastAttack);
        addWeighted(candidates, Attack.GOLEM, attackWeight(Attack.GOLEM, phase), excludeLastAttack);
        addWeighted(candidates, Attack.VILLAGER_CANNON, attackWeight(Attack.VILLAGER_CANNON, phase), excludeLastAttack);
        addWeighted(candidates, Attack.CHARGE, attackWeight(Attack.CHARGE, phase), excludeLastAttack);
        addWeighted(candidates, Attack.STOMP, attackWeight(Attack.STOMP, phase), excludeLastAttack);
        addWeighted(candidates, Attack.BEAM, attackWeight(Attack.BEAM, phase), excludeLastAttack);
        addWeighted(candidates, Attack.FANG, attackWeight(Attack.FANG, phase), excludeLastAttack);
        addWeighted(candidates, Attack.SPIN, attackWeight(Attack.SPIN, phase), excludeLastAttack);
        return candidates;
    }

    private double attackWeight(Attack attack, int phase) {
        return switch (attack) {
            case TELEPORT -> phase == 1 ? config.teleportPhaseOneWeight() : config.teleportPhaseTwoWeight();
            case KNOCKBACK -> phase == 1 ? config.knockbackPhaseOneWeight() : config.knockbackPhaseTwoWeight();
            case GOLEM -> phase == 1 ? config.golemPhaseOneWeight() : config.golemPhaseTwoWeight();
            case VILLAGER_CANNON -> phase == 1 ? config.villagerCannonPhaseOneWeight() : config.villagerCannonPhaseTwoWeight();
            case CHARGE -> phase == 1 ? config.chargePhaseOneWeight() : config.chargePhaseTwoWeight();
            case BEAM -> phase == 1 ? config.beamPhaseOneWeight() : config.beamPhaseTwoWeight();
            case STOMP -> phase == 1 ? config.stompPhaseOneWeight() : config.stompPhaseTwoWeight();
            case FANG -> phase == 1 ? config.fangPhaseOneWeight() : config.fangPhaseTwoWeight();
            case SPIN -> phase == 1 ? config.spinPhaseOneWeight() : config.spinPhaseTwoWeight();
            case IDLE -> 0.0;
        };
    }

    private void addWeighted(List<WeightedAttack> candidates, Attack attack, double weight, boolean excludeLastAttack) {
        if (weight <= 0.0 || (excludeLastAttack && attack == lastAttack)) {
            return;
        }
        candidates.add(new WeightedAttack(attack, weight));
    }

    private void beginAttack(Attack attack, List<Player> players) {
        currentAttack = attack;
        attackTick = 0;
        invulnerable = false;
        movementStart = null;
        movementEnd = null;
        positioningTicksRemaining = 0;

        switch (attack) {
            case TELEPORT -> {
                teleport(groundLocation(boss.getLocation()));
                teleportCount = 0;
                int min = Math.max(1, config.teleportMinCount());
                int max = Math.max(min, config.teleportMaxCount());
                teleportLimit = min + random.nextInt(max - min + 1);
                playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f);
            }
            case KNOCKBACK, GOLEM -> prepareGroundAttackPosition(players);
            case CHARGE -> {
                chargeCount = 0;
                chargePhaseTick = 0;
                prepareGroundAttackPosition(players);
                playSound(boss.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f);
            }
            case STOMP -> {
                prepareGroundAttackPosition(players);
                stompCount = 0;
                stompTarget = null;
            }
            case BEAM, VILLAGER_CANNON -> {
                movementStart = boss.getLocation().clone();
                movementEnd = skyLocation();
            }
            case FANG -> {
                prepareGroundAttackPosition(players);
                fangDirections = List.of();
                playSound(boss.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f);
            }
            case SPIN -> {
                prepareGroundAttackPosition(players);
                spinHitCooldowns.clear();
                invulnerable = true;
                playSound(boss.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f);
            }
            default -> {
            }
        }
        faceNearestPlayer(players);
    }

    private void endAttack() {
        invulnerable = false;
        positioningTicksRemaining = 0;
        lastAttack = currentAttack;
        currentAttack = Attack.IDLE;
        attackTick = 0;
        idleTicks = phase() >= 2 ? config.phaseTwoIdleTicks() : config.phaseOneIdleTicks();
    }

    private void startDeathAnimation() {
        if (dying) {
            return;
        }
        dying = true;
        deathTick = 0;
        currentAttack = Attack.IDLE;
        attackTick = 0;
        positioningTicksRemaining = 0;
        invulnerable = true;
        removeProjectiles();
        removeOwnedEntities();
        shockwaves.clear();
        spinHitCooldowns.clear();

        boss.setHealth(1.0);
        boss.setAI(false);
        boss.setGravity(false);
        boss.setCollidable(false);
        boss.setVelocity(new Vector());

        deathStart = boss.getLocation().clone();
        spawnDeathLightRays(safeGroundLocation(deathStart).add(0.0, 0.15, 0.0));
        if (bossBar != null) {
            bossBar.setColor(BarColor.RED);
            bossBar.setTitle(ChatColor.DARK_RED + config.name() + ChatColor.GRAY + "  STOP");
            bossBar.setProgress(0.0);
        }
        playSound(deathStart, Sound.ENTITY_VILLAGER_HURT, 0.45f);
        boss.getWorld().spawnParticle(Particle.END_ROD, deathStart.clone().add(0.0, 1.5, 0.0), 35, 1.0, 0.8, 1.0, 0.01);
    }

    private void tickDeathAnimation() {
        deathTick++;
        int stopTicks = Math.max(0, config.deathStopTicks());
        if (deathStart == null) {
            cleanup(true);
            bossManager.clearIfCurrent(this);
            return;
        }

        if (deathTick <= stopTicks) {
            Location frozen = deathStart.clone();
            frozen.setPitch(deathStart.getPitch());
            teleport(frozen);
            boss.setVelocity(new Vector());
            if (deathTick % 4 == 0) {
                boss.getWorld().spawnParticle(Particle.CRIT, frozen.clone().add(0.0, 1.2, 0.0), 8, 0.9, 0.7, 0.9, 0.0);
            }
            if (deathTick % 5 == 1) {
                float progress = stopTicks <= 0 ? 1.0f : deathTick / (float) stopTicks;
                playSound(frozen, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f + progress * 1.25f);
            }
            return;
        }

        if (deathTick == stopTicks + 1) {
            Location impact = safeGroundLocation(boss.getLocation()).add(0.0, 0.15, 0.0);
            playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.55f);
            playSound(impact, Sound.ENTITY_WITHER_DEATH, 0.75f);
            boss.getWorld().spawnParticle(Particle.EXPLOSION, impact, 5, 1.8, 0.5, 1.8, 0.0);
            boss.getWorld().spawnParticle(Particle.POOF, impact, Math.max(1, config.deathParticleCount()), 2.2, 1.0, 2.2, 0.12);
            createShockwave(impact, config.deathShockwaveRadius(), 0.0, config.deathShockwavePush());
            removeDeathLightRays();
            boss.setInvulnerable(false);
            boss.setCollidable(true);
            boss.setGravity(true);
            boss.setHealth(0.0);
        }
    }

    private void spawnDeathLightRays(Location impact) {
        if (!config.deathLightRaysEnabled()) {
            return;
        }
        removeDeathLightRays();
        Location origin = impact.clone().add(0.0, config.deathLightRaysHeight(), 0.0);
        origin.setYaw(0.0f);
        origin.setPitch(0.0f);

        Vector forward = deathStart == null ? boss.getLocation().getDirection() : deathStart.getDirection();
        forward.setY(0.0);
        if (forward.lengthSquared() <= 0.01) {
            forward = new Vector(0.0, 0.0, 1.0);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX()).normalize();
        Vector up = new Vector(0.0, 1.0, 0.0);

        List<Shape> shapes = new ArrayList<>();
        double scale = Math.max(0.001, config.deathLightRaysScale());
        float viewRange = (float) Math.max(1.0, config.deathLightRaysViewRange());
        for (LightRaySpec spec : LIGHT_RAYS) {
            spawnRayTriangle(shapes, origin, right, up, spec.angleDegrees(), spec.wideHalfWidth(), scale,
                    color(spec.wideAlpha(), spec.wideRed(), spec.wideGreen(), spec.wideBlue()), viewRange);
            spawnRayTriangle(shapes, origin, right, up, spec.angleDegrees(), spec.narrowHalfWidth(), scale,
                    color(spec.narrowAlpha(), spec.narrowRed(), spec.narrowGreen(), spec.narrowBlue()), viewRange);
        }
        deathLightRayShapes.addAll(shapes);
    }

    private void removeDeathLightRays() {
        for (Shape shape : deathLightRayShapes) {
            shape.remove();
        }
        deathLightRayShapes.clear();
    }

    private void spawnRayTriangle(List<Shape> shapes, Location origin, Vector right, Vector up, double angleDegrees,
                                  double halfWidth, double scale, Color color, float viewRange) {
        Vector3f p1 = toVector3f(origin);
        Vector3f p2 = toVector3f(svgPoint(origin, right, up, -halfWidth, -SVG_RAY_LENGTH, angleDegrees, scale));
        Vector3f p3 = toVector3f(svgPoint(origin, right, up, halfWidth, -SVG_RAY_LENGTH, angleDegrees, scale));
        Shape shape = new BukkitTriangle.Builder(origin.clone(), p1, p2, p3)
                .color(color)
                .doubleSided(true)
                .brightness(15, 15)
                .seeThrough(true)
                .viewRange(viewRange)
                .build();
        shape.spawn();
        shapes.add(shape);
    }

    private Location svgPoint(Location origin, Vector right, Vector up, double x, double y, double angleDegrees, double scale) {
        double radians = Math.toRadians(angleDegrees);
        double rotatedX = x * Math.cos(radians) - y * Math.sin(radians);
        double rotatedY = x * Math.sin(radians) + y * Math.cos(radians);
        return origin.clone()
                .add(right.clone().multiply(rotatedX * scale))
                .add(up.clone().multiply(-rotatedY * scale));
    }

    private Vector3f toVector3f(Location location) {
        return new Vector3f((float) location.getX(), (float) location.getY(), (float) location.getZ());
    }

    private Color color(double alpha, int red, int green, int blue) {
        return Color.fromARGB(
                Math.max(0, Math.min(255, (int) Math.round(alpha * 255.0))),
                red,
                green,
                blue
        );
    }

    private void prepareGroundAttackPosition(List<Player> players) {
        Location current = groundLocation(boss.getLocation());
        Player target = nearestPlayer(players);
        if (target == null) {
            teleport(current);
            return;
        }

        Location targetGround = safeGroundLocation(target.getLocation());
        Vector awayFromTarget = current.toVector().subtract(targetGround.toVector());
        awayFromTarget.setY(0.0);
        if (awayFromTarget.lengthSquared() <= 0.01) {
            awayFromTarget = new Vector(1.0, 0.0, 0.0);
        }
        awayFromTarget.normalize();

        double distance = Math.sqrt(horizontalDistanceSquared(current, targetGround));
        boolean needsPositioning = distance < config.groundAttackMinDistance() || distance > config.groundAttackMaxDistance();
        Location destination = needsPositioning
                ? targetGround.clone().add(awayFromTarget.multiply(config.groundAttackPreferredDistance()))
                : current;
        destination = groundLocation(destination);
        destination.setDirection(targetGround.toVector().subtract(destination.toVector()));

        movementStart = boss.getLocation().clone();
        movementEnd = destination;
        if (needsPositioning || Math.abs(movementStart.getY() - movementEnd.getY()) > 0.2) {
            positioningTicksRemaining = Math.max(1, config.groundAttackPositionTicks());
        } else {
            teleport(destination);
        }
    }

    private void tickTeleport() {
        int interval = Math.max(1, config.teleportIntervalTicks());
        if (attackTick % interval == 1 && teleportCount < teleportLimit) {
            teleportCount++;
            Location from = boss.getLocation().clone();
            Location destination = randomArenaLocation(0.0);
            boss.getWorld().spawnParticle(Particle.PORTAL, from, 45, 1.0, 1.0, 1.0, 0.08);
            drawLine(from, destination, Particle.PORTAL, Math.max(0.2, config.teleportTrailStep()));
            teleport(destination);
            boss.getWorld().spawnParticle(Particle.PORTAL, destination, 45, 1.0, 1.0, 1.0, 0.08);
            playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f);
        }
        if (teleportCount >= teleportLimit && attackTick > teleportLimit * interval + 12) {
            endAttack();
        }
    }

    private void tickKnockback(List<Player> players) {
        Location source = boss.getLocation().add(0.0, 1.3, 0.0);
        if (attackTick <= 20) {
            boss.getWorld().spawnParticle(Particle.CLOUD, source, 8, 1.1, 0.4, 1.1, 0.02);
        }
        if (attackTick == 22) {
            playSound(source, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.7f);
            Location shockwave = safeGroundLocation(boss.getLocation()).add(0.0, 0.08, 0.0);
            createShockwave(shockwave, 8.0, config.knockbackDamage(), 1.8);
        }
        if (attackTick >= 38) {
            endAttack();
        }
    }

    private void tickGolem() {
        if (attackTick < 24) {
            boss.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, boss.getLocation().add(0.0, 1.5, 0.0), 5, 1.2, 0.6, 1.2, 0.02);
        }
        if (attackTick == 24) {
            int aliveGolems = (int) ownedEntities.stream()
                    .filter(entity -> entity instanceof IronGolem)
                    .filter(entity -> entity.isValid() && !entity.isDead())
                    .count();
            int spawnCount = Math.min(config.golemsPerCast(), Math.max(0, config.maxGolems() - aliveGolems));
            for (int i = 0; i < spawnCount; i++) {
                Location location = randomArenaLocation(0.0);
                IronGolem golem = (IronGolem) boss.getWorld().spawnEntity(safeGroundLocation(location), EntityType.IRON_GOLEM);
                golem.setCustomName(ChatColor.GRAY + "大村人のゴーレム");
                golem.setCustomNameVisible(true);
                ownedEntities.add(golem);
                boss.getWorld().spawnParticle(Particle.POOF, golem.getLocation().add(0.0, 1.0, 0.0), 30, 0.7, 0.7, 0.7, 0.04);
            }
            playSound(boss.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 0.8f);
        }
        if (attackTick >= 48) {
            endAttack();
        }
    }

    private void tickVillagerCannon(List<Player> players) {
        int skyTicks = Math.max(0, config.skyMoveTicks());
        if (attackTick <= skyTicks) {
            moveBossBetween(movementStart, movementEnd, attackTick, skyTicks);
            faceNearestPlayer(players);
            return;
        }

        int actionTick = attackTick - skyTicks;
        if (actionTick <= config.cannonWarmupTicks()) {
            boss.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, boss.getLocation().add(0.0, 1.3, 0.0), 3, 0.8, 0.5, 0.8, 0.01);
            return;
        }

        int fireTick = actionTick - config.cannonWarmupTicks() - 1;
        int interval = Math.max(1, config.cannonShotIntervalTicks());
        if (fireTick >= 0 && fireTick % interval == 0) {
            int shotIndex = fireTick / interval;
            if (shotIndex < config.cannonShotCount()) {
                faceNearestPlayer(players);
                for (Player target : players) {
                    fireVillagerProjectile(target);
                }
            }
        }

        if (fireTick > config.cannonShotCount() * interval + 18) {
            endAttack();
        }
    }

    private void tickCharge(List<Player> players) {
        chargePhaseTick++;
        if (chargeCount >= config.chargeRepeats()) {
            invulnerable = false;
            boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().add(0.0, 1.4, 0.0), 6, 0.8, 0.5, 0.8, 0.02);
            if (chargePhaseTick >= config.chargeStunTicks()) {
                endAttack();
            }
            return;
        }

        if (chargePhaseTick <= config.chargeWarmupTicks()) {
            invulnerable = false;
            teleport(groundLocation(boss.getLocation()));
            updateChargeDirection(players);
            boss.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, boss.getLocation().add(0.0, 1.5, 0.0), 6, 0.8, 0.5, 0.8, 0.01);
            return;
        }

        int runEnd = config.chargeWarmupTicks() + config.chargeRunTicks();
        if (chargePhaseTick <= runEnd) {
            invulnerable = true;
            if (chargePhaseTick == config.chargeWarmupTicks() + 1) {
                updateChargeDirection(players);
            }
            Player target = nearestPlayer(players);
            if (target != null && isMovingAwayFrom(target, chargeDirection)) {
                updateChargeDirection(players);
            }
            Location next = moveOnGround(boss.getLocation(), chargeDirection, config.chargeSpeed());
            teleport(next);
            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0.0, 1.0, 0.0), 5, 0.5, 0.3, 0.5, 0.02);
            for (Player player : players) {
                if (player.getLocation().distanceSquared(boss.getLocation()) <= 6.25) {
                    player.damage(config.chargeDamage(), boss);
                    player.setVelocity(chargeDirection.clone().multiply(1.2).setY(0.45));
                }
            }
            return;
        }

        invulnerable = false;
        if (chargePhaseTick >= runEnd + config.chargeRecoveryTicks()) {
            chargeCount++;
            chargePhaseTick = 0;
            if (chargeCount >= config.chargeRepeats()) {
                playSound(boss.getLocation(), Sound.ENTITY_VILLAGER_HURT, 0.6f);
            }
        }
    }

    private void updateChargeDirection(List<Player> players) {
        Player target = nearestPlayer(players);
        if (target == null) {
            return;
        }
        Vector direction = horizontalDirection(boss.getLocation(), target.getLocation());
        if (direction.lengthSquared() <= 0.01) {
            direction = horizontalDirection(boss.getLocation(), delayedLocation(target, 0));
        }
        if (direction.lengthSquared() <= 0.01) {
            chargeDirection = directionFromYaw(boss.getLocation().getYaw());
            face(chargeDirection);
            return;
        }
        chargeDirection = direction;
        face(chargeDirection);
    }

    private void tickBeam(List<Player> players) {
        int skyTicks = Math.max(0, config.skyMoveTicks());
        if (attackTick <= skyTicks) {
            moveBossBetween(movementStart, movementEnd, attackTick, skyTicks);
            faceNearestPlayer(players);
            return;
        }

        int actionTick = attackTick - skyTicks;
        if (players.isEmpty()) {
            endAttack();
            return;
        }

        Location beamStart = boss.getLocation().clone().add(0.0, 1.0, 0.0);
        if (actionTick <= config.beamWarningTicks()) {
            faceNearestPlayer(players);
            for (Player player : players) {
                Location targetLocation = delayedLocation(player, config.beamTargetDelayTicks()).add(0.0, 1.0, 0.0);
                drawLine(beamStart, targetLocation, Particle.END_ROD, 0.8);
                boss.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, targetLocation, 8, 0.8, 0.8, 0.8, 0.02);
            }
            return;
        }

        int fireTick = actionTick - config.beamWarningTicks();
        if (fireTick <= config.beamDurationTicks()) {
            if (fireTick == 1) {
                playSound(beamStart, Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f);
            }
            Player nearest = nearestPlayer(players);
            for (Player beamTarget : players) {
                Location targetLocation = delayedLocation(beamTarget, config.beamTargetDelayTicks()).add(0.0, 1.0, 0.0);
                if (beamTarget == nearest) {
                    faceLocation(targetLocation);
                }
                drawLine(beamStart, targetLocation, Particle.SONIC_BOOM, 1.4);
                if (fireTick % Math.max(1, config.beamDamageIntervalTicks()) == 1) {
                    for (Player player : players) {
                        if (distanceToSegment(player.getLocation().add(0.0, 1.0, 0.0), beamStart, targetLocation) <= 1.6) {
                            player.damage(config.beamDamage(), boss);
                            Vector push = player.getLocation().toVector().subtract(beamStart.toVector()).normalize().multiply(0.8).setY(0.35);
                            player.setVelocity(push);
                        }
                    }
                }
            }
            return;
        }

        if (fireTick >= config.beamDurationTicks() + config.beamRecoveryTicks()) {
            endAttack();
        }
    }

    private void tickStomp(List<Player> players) {
        int cycleLength = config.stompAscendTicks() + config.stompWarningTicks() + config.stompFallTicks() + config.stompRecoveryTicks();
        int cycleTick = attackTick - stompCount * cycleLength;
        if (cycleTick == 1) {
            Player target = randomPlayer(players);
            if (target == null) {
                endAttack();
                return;
            }
            stompTarget = safeGroundLocation(target.getLocation());
            movementStart = boss.getLocation().clone();
            movementEnd = stompTarget.clone().add(0.0, config.stompHeight(), 0.0);
            playSound(movementEnd, Sound.ENTITY_VILLAGER_CELEBRATE, 0.55f);
        }
        if (stompTarget == null) {
            endAttack();
            return;
        }

        int ascendEnd = config.stompAscendTicks();
        int warningEnd = ascendEnd + config.stompWarningTicks();
        int fallEnd = warningEnd + config.stompFallTicks();

        if (cycleTick <= ascendEnd) {
            moveBossBetween(movementStart, movementEnd, cycleTick, ascendEnd);
        } else if (cycleTick <= warningEnd) {
            teleport(movementEnd);
            boss.getWorld().spawnParticle(Particle.CRIT, stompTarget.clone().add(0.0, 0.15, 0.0), 15, 1.8, 0.05, 1.8, 0.0);
        } else if (cycleTick <= fallEnd) {
            moveBossBetween(movementEnd, stompTarget, cycleTick - warningEnd, config.stompFallTicks());
            boss.getWorld().spawnParticle(Particle.CLOUD, boss.getLocation().add(0.0, 0.3, 0.0), 6, 1.2, 0.1, 1.2, 0.02);
        } else if (cycleTick == fallEnd + 1) {
            playSound(stompTarget, Sound.ENTITY_GENERIC_EXPLODE, 1.1f);
            Location shockwave = stompTarget.clone().add(0.0, 0.08, 0.0);
            createShockwave(shockwave, 4.0, config.stompDamage(), 1.1);
        } else if (cycleTick >= cycleLength) {
            stompCount++;
            stompTarget = null;
            if (stompCount >= config.stompCount()) {
                movementStart = boss.getLocation().clone();
                movementEnd = randomArenaLocation(config.hoverHeight());
                moveBossBetween(movementStart, movementEnd, 1, Math.max(1, config.stompAscendTicks()));
                endAttack();
            }
        }
    }

    private void tickFang(List<Player> players) {
        if (fangDirections.isEmpty()) {
            Player target = nearestPlayer(players);
            if (target == null) {
                endAttack();
                return;
            }
            Vector centerDirection = target.getLocation().toVector().subtract(boss.getLocation().toVector());
            centerDirection.setY(0.0);
            if (centerDirection.lengthSquared() <= 0.01) {
                centerDirection = new Vector(1.0, 0.0, 0.0);
            }
            centerDirection.normalize();
            double angle = Math.toRadians(config.fangSpreadDegrees());
            fangDirections = List.of(rotateY(centerDirection, -angle), centerDirection, rotateY(centerDirection, angle));
        }

        Location origin = groundLocation(boss.getLocation());
        if (attackTick <= config.fangWarmupTicks()) {
            for (Vector direction : fangDirections) {
                drawGroundWarning(origin, direction, config.fangRows(), config.fangSpacing());
            }
            boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation().add(0.0, 1.4, 0.0), 8, 0.8, 0.5, 0.8, 0.02);
            return;
        }

        int fangTick = attackTick - config.fangWarmupTicks() - 1;
        int delay = Math.max(1, config.fangRowDelayTicks());
        if (fangTick >= 0 && fangTick % delay == 0) {
            int row = fangTick / delay + 1;
            if (row <= config.fangRows()) {
                for (Vector direction : fangDirections) {
                    spawnFang(origin.clone().add(direction.clone().multiply(config.fangSpacing() * row)), direction, players);
                }
            }
        }

        if (fangTick > config.fangRows() * delay + 20) {
            endAttack();
        }
    }

    private void tickSpin(List<Player> players) {
        invulnerable = true;
        spinHitCooldowns.replaceAll((uuid, ticks) -> Math.max(0, ticks - 1));

        Player target = nearestPlayer(players);
        if (target != null) {
            Location delayedTarget = delayedLocation(target, config.spinTargetDelayTicks());
            Vector direction = horizontalDirection(boss.getLocation(), delayedTarget);
            if (direction.lengthSquared() <= 0.01) {
                direction = horizontalDirection(boss.getLocation(), target.getLocation());
            }
            if (direction.lengthSquared() > 0.01) {
                spinDirection = direction;
            }
        }
        if (spinDirection.lengthSquared() <= 0.01) {
            spinDirection = directionFromYaw(boss.getLocation().getYaw());
        }
        Location previous = boss.getLocation().clone();
        Location next = moveOnGround(previous, spinDirection, config.spinSpeed());
        if (horizontalDistanceSquared(previous, next) <= 0.0001) {
            spinDirection = nextSpinFallbackDirection(players);
            next = moveOnGround(previous, spinDirection, Math.max(config.spinSpeed(), 0.75));
        }
        next.setYaw(boss.getLocation().getYaw() + 42.0f);
        teleport(next);

        boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation().add(0.0, 1.0, 0.0), 3, 1.0, 0.3, 1.0, 0.0);
        double hitRadiusSquared = config.spinHitRadius() * config.spinHitRadius();
        for (Player player : players) {
            if (player.getLocation().distanceSquared(boss.getLocation()) <= hitRadiusSquared
                    && spinHitCooldowns.getOrDefault(player.getUniqueId(), 0) <= 0) {
                player.damage(config.spinDamage(), boss);
                Vector push = player.getLocation().toVector().subtract(boss.getLocation().toVector());
                if (push.lengthSquared() < 0.01) {
                    push = new Vector(0.0, 0.0, 1.0);
                }
                player.setVelocity(push.normalize().multiply(1.0).setY(0.45));
                spinHitCooldowns.put(player.getUniqueId(), Math.max(1, config.spinDamageIntervalTicks()));
            }
        }

        if (attackTick >= config.spinDurationTicks()) {
            endAttack();
        }
    }

    private void fireVillagerProjectile(Player target) {
        Location origin = boss.getLocation().clone().add(0.0, 1.2, 0.0);
        Villager projectile = (Villager) boss.getWorld().spawnEntity(origin, EntityType.VILLAGER);
        projectile.setCustomName(ChatColor.YELLOW + "小村人");
        projectile.setAI(false);
        projectile.setGravity(false);
        projectile.setBaby();
        projectile.setInvulnerable(true);
        projectile.setSilent(true);
        projectile.setCollidable(false);
        setAttribute(projectile, "scale", 0.55);
        Location delayedTarget = delayedLocation(target, config.cannonHomingDelayTicks()).add(0.0, 1.0, 0.0);
        Vector velocity = delayedTarget.toVector().subtract(origin.toVector()).normalize().multiply(config.cannonProjectileSpeed());
        projectiles.add(new VillagerProjectile(projectile, target.getUniqueId(), velocity));
        ownedEntities.add(projectile);
        playSound(origin, Sound.ENTITY_VILLAGER_YES, 1.6f);
    }

    private void tickProjectiles(List<Player> players) {
        Iterator<VillagerProjectile> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            VillagerProjectile projectile = iterator.next();
            if (!projectile.entity.isValid() || projectile.entity.isDead()) {
                projectile.entity.remove();
                iterator.remove();
                continue;
            }
            if (projectile.lodged) {
                projectile.lodgedTicks++;
                projectile.entity.getWorld().spawnParticle(Particle.CLOUD, projectile.entity.getLocation().add(0.0, 0.4, 0.0), 1, 0.1, 0.05, 0.1, 0.0);
                if (projectile.lodgedTicks >= config.cannonProjectileLodgeTicks()) {
                    projectile.entity.getWorld().spawnParticle(Particle.POOF, projectile.entity.getLocation().add(0.0, 0.5, 0.0), 10, 0.25, 0.25, 0.25, 0.02);
                    projectile.entity.remove();
                    iterator.remove();
                }
                continue;
            }
            if (projectile.life++ > config.cannonProjectileLifeTicks()) {
                lodgeProjectile(projectile, projectile.entity.getLocation());
                continue;
            }

            Location delayedTarget = delayedLocation(projectile.targetId, config.cannonHomingDelayTicks(), projectile.entity.getLocation()).add(0.0, 1.0, 0.0);
            Vector desired = delayedTarget.toVector().subtract(projectile.entity.getLocation().toVector());
            if (desired.lengthSquared() > 0.01) {
                double strength = Math.max(0.0, Math.min(1.0, config.cannonHomingStrength()));
                Vector blended = projectile.velocity.clone().normalize().multiply(1.0 - strength)
                        .add(desired.normalize().multiply(strength));
                if (blended.lengthSquared() > 0.01) {
                    projectile.velocity = blended.normalize().multiply(config.cannonProjectileSpeed());
                }
            }

            Location next = projectile.entity.getLocation().add(projectile.velocity);
            Location ground = safeGroundLocation(next);
            if (next.getY() <= ground.getY() + 0.2) {
                lodgeProjectile(projectile, next);
                continue;
            }
            teleport(projectile.entity, next);
            projectile.entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, next, 2, 0.2, 0.2, 0.2, 0.0);

            for (Player player : players) {
                if (player.getLocation().distanceSquared(next) <= 2.25) {
                    player.damage(config.villagerCannonDamage(), boss);
                    player.setVelocity(projectile.velocity.clone().multiply(1.1).setY(0.45));
                    lodgeProjectile(projectile, player.getLocation());
                    break;
                }
            }
        }
    }

    private void createShockwave(Location center, double maxRadius, double damage, double pushStrength) {
        double startRadius = Math.max(0.0, Math.min(maxRadius, config.shockwaveStartRadius()));
        double speed = Math.max(0.01, config.shockwaveSpeed());
        int duration = Math.max(1, config.shockwaveTicks());
        double effectiveMaxRadius = Math.max(maxRadius, startRadius + speed * duration);
        shockwaves.add(new Shockwave(center.clone(), startRadius, effectiveMaxRadius, speed, duration, damage, pushStrength));
        center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0.0, 0.3, 0.0), 1, 0.4, 0.1, 0.4, 0.0);
    }

    private void tickShockwaves(List<Player> players) {
        Iterator<Shockwave> iterator = shockwaves.iterator();
        while (iterator.hasNext()) {
            Shockwave shockwave = iterator.next();
            shockwave.age++;
            double previousRadius = shockwave.radius;
            double radius = Math.min(shockwave.maxRadius, shockwave.startRadius + shockwave.age * shockwave.speed);
            shockwave.radius = radius;
            drawShockwaveRing(shockwave.center, radius);

            double radiusSquared = radius * radius;
            double innerRadius = Math.max(0.0, Math.max(previousRadius, radius - config.shockwaveRingThickness()));
            double innerRadiusSquared = innerRadius * innerRadius;
            for (Player player : players) {
                if (shockwave.hitPlayers.contains(player.getUniqueId())) {
                    continue;
                }
                double distanceSquared = horizontalDistanceSquared(player.getLocation(), shockwave.center);
                if (distanceSquared <= radiusSquared
                        && distanceSquared >= innerRadiusSquared
                        && isOnShockwaveHeight(player, shockwave.center)) {
                    shockwave.hitPlayers.add(player.getUniqueId());
                    if (shockwave.damage > 0.0) {
                        player.damage(shockwave.damage, boss);
                    }
                    Vector push = player.getLocation().toVector().subtract(shockwave.center.toVector());
                    push.setY(0.0);
                    if (push.lengthSquared() < 0.01) {
                        push = new Vector(0.0, 0.0, 1.0);
                    }
                    player.setVelocity(push.normalize().multiply(shockwave.pushStrength).setY(config.shockwaveKnockbackY()));
                }
            }

            if (shockwave.age >= shockwave.duration) {
                iterator.remove();
            }
        }
    }

    private void drawShockwaveRing(Location center, double radius) {
        int particles = Math.max(8, config.shockwaveRingParticles());
        for (int index = 0; index < particles; index++) {
            double angle = TWO_PI * index / particles;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(Particle.CLOUD, point, 1, 0.08, 0.01, 0.08, 0.0);
        }
    }

    private void lodgeProjectile(VillagerProjectile projectile, Location impactLocation) {
        if (projectile.lodged) {
            return;
        }
        Location lodgedLocation = safeGroundLocation(impactLocation).add(0.0, -0.55, 0.0);
        lodgedLocation.setDirection(projectile.velocity);
        projectile.lodged = true;
        projectile.lodgedTicks = 0;
        teleport(projectile.entity, lodgedLocation);
        projectile.entity.getWorld().spawnParticle(Particle.BLOCK, lodgedLocation.clone().add(0.0, 0.6, 0.0), 14, 0.35, 0.15, 0.35, 0.02, lodgedLocation.getBlock().getBlockData());
        playSound(lodgedLocation, Sound.BLOCK_GRAVEL_HIT, 0.8f);
    }

    private void spawnFang(Location location, Vector direction, List<Player> players) {
        Location ground = safeGroundLocation(location);
        ground.setDirection(direction);
        Entity fang = boss.getWorld().spawnEntity(ground, EntityType.EVOKER_FANGS);
        ownedEntities.add(fang);
        playSound(ground, Sound.ENTITY_EVOKER_FANGS_ATTACK, 0.9f);
        for (Player player : players) {
            if (player.getLocation().distanceSquared(ground) <= 2.25) {
                player.damage(config.fangDamage(), boss);
                player.setVelocity(direction.clone().multiply(0.35).setY(0.35));
            }
        }
    }

    private void drawGroundWarning(Location origin, Vector direction, int rows, double spacing) {
        for (int row = 1; row <= rows; row++) {
            Location location = origin.clone().add(direction.clone().multiply(spacing * row));
            boss.getWorld().spawnParticle(Particle.CRIT, safeGroundLocation(location).add(0.0, 0.1, 0.0), 1, 0.2, 0.02, 0.2, 0.0);
        }
    }

    private void hoverNearCenter() {
        Location location = boss.getLocation();
        double maxDistance = config.arenaRadius() * 1.15;
        if (location.distanceSquared(center) > maxDistance * maxDistance) {
            teleport(randomArenaLocation(config.hoverHeight()));
            return;
        }
        if (location.getY() < safeGroundLocation(location).getY() + 1.5) {
            teleport(location.add(0.0, 0.3, 0.0));
        }
    }

    private int phase() {
        double ratio = boss.getHealth() / config.maxHealth();
        return ratio <= config.phaseTwoHealthRatio() ? 2 : 1;
    }

    private void transformToEvoker() {
        phaseTwoTransformed = true;
        Location location = boss.getLocation().clone();
        double health = Math.max(1.0, boss.getHealth());
        Entity oldBoss = boss;

        boss.getWorld().spawnParticle(Particle.POOF, location.add(0.0, 1.0, 0.0), 80, 1.4, 1.0, 1.4, 0.08);
        playSound(location, Sound.ENTITY_EVOKER_CAST_SPELL, 0.55f);

        LivingEntity evoker = (LivingEntity) oldBoss.getWorld().spawnEntity(oldBoss.getLocation(), EntityType.EVOKER);
        oldBoss.remove();
        boss = evoker;
        configureBossEntity(boss);
        boss.setHealth(Math.min(health, config.maxHealth()));
        bossBar.setColor(BarColor.RED);
    }

    private void configureBossEntity(LivingEntity entity) {
        bossId = entity.getUniqueId();
        entity.setCustomName(ChatColor.GOLD + config.name());
        entity.setCustomNameVisible(true);
        entity.setAI(false);
        entity.setGravity(false);
        entity.setSilent(false);
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);
        entity.setCollidable(true);
        if (entity.getEquipment() != null) {
            entity.getEquipment().clear();
        }
        setAttribute(entity, "max_health", config.maxHealth());
        setAttribute(entity, "scale", config.scale());
    }

    private Player nearestPlayer(List<Player> players) {
        return players.isEmpty() ? null : players.get(0);
    }

    private Player randomPlayer(List<Player> players) {
        return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
    }

    private Location randomArenaLocation(double heightAboveGround) {
        double angle = random.nextDouble() * TWO_PI;
        double distance = 5.0 + random.nextDouble() * Math.max(5.0, config.arenaRadius() - 5.0);
        Location location = center.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
        return safeGroundLocation(location).add(0.0, heightAboveGround, 0.0);
    }

    private Location skyLocation() {
        double angle = random.nextDouble() * TWO_PI;
        double distance = config.arenaRadius() * 0.75;
        return safeGroundLocation(center.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance))
                .add(0.0, config.skyHeight(), 0.0);
    }

    private Location safeGroundLocation(Location input) {
        World world = input.getWorld();
        int x = input.getBlockX();
        int z = input.getBlockZ();
        int y = Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt(x, z) + 1);
        return new Location(world, x + 0.5, y, z + 0.5, input.getYaw(), input.getPitch());
    }

    private Location groundLocation(Location input) {
        Location ground = safeGroundLocation(input);
        ground.setYaw(input.getYaw());
        ground.setPitch(input.getPitch());
        return ground;
    }

    private Location moveOnGround(Location from, Vector direction, double distance) {
        Vector horizontal = direction.clone();
        horizontal.setY(0.0);
        if (horizontal.lengthSquared() <= 0.01) {
            horizontal = directionFromYaw(from.getYaw());
        } else {
            horizontal.normalize();
        }
        Location next = from.clone().add(horizontal.multiply(Math.max(0.0, distance)));
        next = groundLocationAtExactXZ(next);
        next.setDirection(horizontal);
        return next;
    }

    private Location groundLocationAtExactXZ(Location input) {
        World world = input.getWorld();
        int y = Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt(input.getBlockX(), input.getBlockZ()) + 1);
        return new Location(world, input.getX(), y, input.getZ(), input.getYaw(), input.getPitch());
    }

    private void teleport(Location location) {
        teleport(boss, location);
    }

    private void teleport(Entity entity, Location location) {
        entity.teleport(location);
    }

    private void moveBossBetween(Location from, Location to, int elapsedTicks, int durationTicks) {
        if (from == null || to == null) {
            return;
        }
        if (durationTicks <= 0) {
            teleport(to);
            return;
        }
        double progress = Math.max(0.0, Math.min(1.0, elapsedTicks / (double) durationTicks));
        Location next = from.clone().add(to.toVector().subtract(from.toVector()).multiply(progress));
        next.setYaw(to.getYaw());
        next.setPitch(to.getPitch());
        teleport(next);
        boss.getWorld().spawnParticle(Particle.CLOUD, next, 2, 0.2, 0.2, 0.2, 0.0);
    }

    private void face(Vector direction) {
        Location location = boss.getLocation();
        location.setDirection(direction);
        teleport(location);
    }

    private void faceNearestPlayer(List<Player> players) {
        Player target = nearestPlayer(players);
        if (target != null) {
            faceLocation(target.getLocation());
        }
    }

    private void faceLocation(Location target) {
        Vector direction = horizontalDirection(boss.getLocation(), target);
        if (direction.lengthSquared() > 0.01) {
            face(direction);
        }
    }

    private Vector horizontalDirection(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        direction.setY(0.0);
        return direction.lengthSquared() <= 0.01 ? new Vector() : direction.normalize();
    }

    private Vector directionFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0, Math.cos(radians)).normalize();
    }

    private boolean isMovingAwayFrom(Player target, Vector direction) {
        Vector towardTarget = horizontalDirection(boss.getLocation(), target.getLocation());
        Vector movement = direction.clone();
        movement.setY(0.0);
        if (towardTarget.lengthSquared() <= 0.01 || movement.lengthSquared() <= 0.01) {
            return false;
        }
        return movement.normalize().dot(towardTarget) < -0.15;
    }

    private Vector nextSpinFallbackDirection(List<Player> players) {
        Player target = nearestPlayer(players);
        if (target != null) {
            Vector toward = horizontalDirection(boss.getLocation(), target.getLocation());
            if (toward.lengthSquared() > 0.01) {
                return toward;
            }
        }
        Vector fallback = rotateY(spinDirection.lengthSquared() > 0.01 ? spinDirection : directionFromYaw(boss.getLocation().getYaw()), Math.toRadians(35.0));
        return fallback.lengthSquared() <= 0.01 ? new Vector(1.0, 0.0, 0.0) : fallback.normalize();
    }

    private Vector rotateY(Vector vector, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(vector.getX() * cos - vector.getZ() * sin, 0.0, vector.getX() * sin + vector.getZ() * cos).normalize();
    }

    private void drawLine(Location start, Location end, Particle particle, double step) {
        Vector delta = end.toVector().subtract(start.toVector());
        double length = delta.length();
        if (length <= 0.01) {
            return;
        }
        Vector unit = delta.normalize().multiply(step);
        Location cursor = start.clone();
        for (double traveled = 0.0; traveled < length; traveled += step) {
            start.getWorld().spawnParticle(particle, cursor, 1, 0.0, 0.0, 0.0, 0.0);
            cursor.add(unit);
        }
    }

    private double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private boolean isOnShockwaveHeight(Player player, Location shockwave) {
        return player.getLocation().getY() <= shockwave.getY() + config.shockwaveHeight();
    }

    private double distanceToSegment(Location point, Location start, Location end) {
        Vector p = point.toVector();
        Vector a = start.toVector();
        Vector b = end.toVector();
        Vector ab = b.clone().subtract(a);
        double abLengthSquared = ab.lengthSquared();
        if (abLengthSquared <= 0.01) {
            return p.distance(a);
        }
        double t = Math.max(0.0, Math.min(1.0, p.clone().subtract(a).dot(ab) / abLengthSquared));
        return p.distance(a.add(ab.multiply(t)));
    }

    private void playSound(Location location, Sound sound, float pitch) {
        location.getWorld().playSound(location, sound, Math.max(0.1f, config.soundVolume()), pitch);
    }

    private double guardMultiplier() {
        double value = switch (currentAttack) {
            case IDLE -> config.idleGuardMult();
            case TELEPORT -> config.teleportGuardMult();
            case KNOCKBACK -> config.knockbackGuardMult();
            case GOLEM -> config.golemGuardMult();
            case VILLAGER_CANNON -> config.villagerCannonGuardMult();
            case CHARGE -> config.chargeGuardMult();
            case BEAM -> config.beamGuardMult();
            case STOMP -> config.stompGuardMult();
            case FANG -> config.fangGuardMult();
            case SPIN -> config.spinGuardMult();
        };
        return Math.max(0.0, Math.min(1.0, value));
    }

    private void updateBossBar() {
        updateBossBar(activePlayers());
    }

    private void updateBossBar(List<Player> players) {
        bossBar.setTitle(ChatColor.GOLD + config.name() + ChatColor.YELLOW + "  Phase " + phase());
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, boss.getHealth() / config.maxHealth())));
        for (Player existing : new ArrayList<>(bossBar.getPlayers())) {
            if (!players.contains(existing)) {
                bossBar.removePlayer(existing);
            }
        }
        for (Player player : players) {
            bossBar.addPlayer(player);
        }
    }

    private void updateDeathBossBar(List<Player> players) {
        if (bossBar == null) {
            return;
        }
        bossBar.setTitle(ChatColor.DARK_RED + config.name() + ChatColor.GRAY + "  DEFEATED");
        bossBar.setProgress(0.0);
        for (Player existing : new ArrayList<>(bossBar.getPlayers())) {
            if (!players.contains(existing)) {
                bossBar.removePlayer(existing);
            }
        }
        for (Player player : players) {
            bossBar.addPlayer(player);
        }
    }

    private void setAttribute(LivingEntity entity, String key, double value) {
        Attribute attribute = findAttribute(key);
        if (attribute == null) {
            plugin.getLogger().warning("Attribute not found: " + key);
            return;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private Attribute findAttribute(String key) {
        NamespacedKey namespacedKey = NamespacedKey.minecraft(key);
        return Registry.ATTRIBUTE.get(namespacedKey);
    }

    private void cleanup(boolean removeBoss) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        removeDeathLightRays();
        removeProjectiles();
        shockwaves.clear();
        removeOwnedEntities();
        if (removeBoss && boss != null && boss.isValid()) {
            boss.remove();
        }
    }

    private void removeProjectiles() {
        for (VillagerProjectile projectile : projectiles) {
            projectile.entity.remove();
        }
        projectiles.clear();
    }

    private void removeOwnedEntities() {
        for (Entity entity : ownedEntities) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        ownedEntities.clear();
    }

    private enum Attack {
        IDLE,
        TELEPORT,
        KNOCKBACK,
        GOLEM,
        VILLAGER_CANNON,
        CHARGE,
        BEAM,
        STOMP,
        FANG,
        SPIN
    }

    private record WeightedAttack(Attack attack, double weight) {
    }

    private record LightRaySpec(
            double angleDegrees,
            double wideHalfWidth,
            double wideAlpha,
            int wideRed,
            int wideGreen,
            int wideBlue,
            double narrowHalfWidth,
            double narrowAlpha,
            int narrowRed,
            int narrowGreen,
            int narrowBlue
    ) {
    }

    private static final class VillagerProjectile {
        private final Villager entity;
        private final UUID targetId;
        private Vector velocity;
        private int life;
        private boolean lodged;
        private int lodgedTicks;

        private VillagerProjectile(Villager entity, UUID targetId, Vector velocity) {
            this.entity = entity;
            this.targetId = targetId;
            this.velocity = velocity;
        }
    }

    private static final class Shockwave {
        private final Location center;
        private final double startRadius;
        private final double maxRadius;
        private final double speed;
        private final int duration;
        private final double damage;
        private final double pushStrength;
        private final List<UUID> hitPlayers = new ArrayList<>();
        private int age;
        private double radius;

        private Shockwave(Location center, double startRadius, double maxRadius, double speed, int duration, double damage, double pushStrength) {
            this.center = center;
            this.startRadius = startRadius;
            this.maxRadius = maxRadius;
            this.speed = speed;
            this.duration = duration;
            this.damage = damage;
            this.pushStrength = pushStrength;
            this.radius = startRadius;
        }
    }
}

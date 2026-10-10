package net.azisaba.oomurabito;

import dev.twme.textdisplayshape.bukkit.BukkitTriangle;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

public final class OomurabitoBoss {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double SVG_RAY_LENGTH = 340.0;
    private static final Color WHITE = Color.fromRGB(255, 255, 255);
    private static final Color GOLD = Color.fromRGB(255, 200, 60);
    private static final Color EMBER = Color.fromRGB(255, 110, 30);
    private static final Color DANGER = Color.fromRGB(255, 40, 40);
    private static final Color VOID = Color.fromRGB(130, 50, 210);
    private static final Color DUSK = Color.fromRGB(40, 10, 70);
    private static final Color GRASS = Color.fromRGB(110, 220, 90);
    private static final int TRANSITION_RISE_TICKS = 20;
    private static final int TRANSITION_SWAP_TICK = 55;
    private static final int TRANSITION_END_TICK = 85;
    private static final int AFTERMATH_TICKS = 70;
    private static final long NIGHT_TIME = 18000L;
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
    private final Fx fx;
    private final Visuals visuals = new Visuals();
    private final Random random = new Random();
    private final Location center;
    private final List<Entity> ownedEntities = new ArrayList<>();
    private final List<VillagerProjectile> projectiles = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final List<BukkitTriangle> deathLightRayShapes = new ArrayList<>();
    private final List<List<RayPart>> deathRays = new ArrayList<>();
    private final Map<UUID, Visuals.Beam> beams = new HashMap<>();
    private final Set<UUID> darkenedPlayers = new HashSet<>();
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

    private int age;
    private double scaleMult = 1.0;
    private double scaleVelocity;
    private double scaleTarget = 1.0;
    private double appliedScale = -1.0;
    private int hitStopTicks;
    private int guardFeedbackCooldown;
    private boolean cutscene;
    private int transitionTick = -1;
    private Location transitionStart;
    private Location transitionEnd;
    private boolean aftermath;
    private int aftermathTick;
    private Location aftermathCenter;
    private UUID lastDamagerId;
    private float deathRayYaw;
    private Visuals.Strip chargeStrip;
    private boolean chargeImpactDone;

    OomurabitoBoss(OomurabitoPlugin plugin, BossManager bossManager, Location center) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.config = plugin.bossConfig();
        this.fx = new Fx(config.soundVolume());
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

        bossBar = Bukkit.createBossBar(bossBarTitle(), BarColor.YELLOW, BarStyle.SEGMENTED_10);
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
        recordDamager(event);
        if (invulnerable || cutscene || transitionTick >= 0) {
            event.setCancelled(true);
            guardFeedback(true);
            return;
        }
        double guardMult = guardMultiplier();
        if (guardMult <= 0.0) {
            event.setCancelled(true);
            guardFeedback(true);
            return;
        }
        if (guardMult < 1.0) {
            event.setDamage(event.getDamage() * guardMult);
            guardFeedback(false);
        }
        if (boss.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            startDeathAnimation();
            return;
        }
        punchScale(0.05);
        Bukkit.getScheduler().runTask(plugin, (Runnable) this::updateBossBar);
    }

    public void onDeath() {
        if (aftermath) {
            // The death sequence killed the entity itself and still has its finale to play.
            return;
        }
        cleanup(false);
    }

    boolean isFinishing() {
        return aftermath;
    }

    void beginCutscene() {
        cutscene = true;
    }

    void endCutscene() {
        cutscene = false;
        introLockTicks = 0;
        scaleTarget = 1.0;
    }

    void punchScale(double impulse) {
        scaleVelocity += impulse;
    }

    void scaleTarget(double target) {
        scaleTarget = target;
    }

    /** Sets the size outright; the spring then carries it back to the current target. */
    void squash(double mult) {
        scaleMult = mult;
    }

    void shakeHead() {
        if (boss instanceof Villager villager) {
            villager.shakeHead();
        }
    }

    Location body() {
        return boss.getLocation().add(0.0, boss.getHeight() * 0.5, 0.0);
    }

    /** Ground burst shared by every heavy landing: debris, dust, heaved blocks and a camera kick. */
    void impactEffect(Location ground, double power) {
        fx.spawn(Particle.EXPLOSION, ground.clone().add(0.0, 0.3, 0.0), 1, 0.4, 0.1, 0.4, 0.0);
        fx.debris(ground, (int) (30 * power), 0.8 * power, 0.25);
        fx.radialBurst(Particle.CLOUD, ground.clone().add(0.0, 0.2, 0.0), (int) (16 * power), 0.3 + 0.1 * power, 0.05);
        fx.spawn(Particle.CAMPFIRE_COSY_SMOKE, ground.clone().add(0.0, 0.3, 0.0), (int) (6 * power), 0.8 * power, 0.2, 0.8 * power, 0.02);
        visuals.add(new Visuals.Rubble(ground, 1.6 * power, (int) (6 + 3 * power), 0.8, random));
        fx.shake(ground.getWorld().getPlayers(), ground, 8.0 * power);
    }

    /** A harmless ring that sweeps outward along the ground. */
    void ringEffect(Location ground, double radius, int ticks) {
        visuals.add(new Visuals.Ring(ground, Material.WHITE_STAINED_GLASS, 0.5, radius, 0.6, 0.35, ticks, 0));
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
        age++;
        if (aftermath) {
            tickAftermath();
            return;
        }
        if (!isAlive()) {
            cleanup(false);
            bossManager.clearIfCurrent(this);
            return;
        }

        tickScale();
        if (guardFeedbackCooldown > 0) {
            guardFeedbackCooldown--;
        }

        if (dying) {
            visuals.tick();
            List<Player> players = activePlayers();
            updateDeathBossBar(players);
            tickShockwaves(players);
            tickDeathAnimation();
            return;
        }

        List<Player> players = activePlayers();
        recordPlayerHistory(players);
        updateBossBar(players);
        syncSky(players);
        if (cutscene) {
            visuals.tick();
            return;
        }
        if (hitStopTicks > 0) {
            // Everything but the scale wobble holds still for a few ticks so heavy hits read as heavy.
            hitStopTicks--;
            return;
        }
        visuals.tick();
        tickProjectiles(players);
        tickShockwaves(players);

        if (transitionTick >= 0) {
            tickTransition(players);
            return;
        }
        if (phase() == 2 && !phaseTwoTransformed) {
            startTransition();
            return;
        }
        if (phaseTwoTransformed && age % 2 == 0) {
            fx.dustFade(boss.getLocation().add(0.0, 0.3, 0.0), VOID, DUSK, 1.3f, 2, 0.5);
        }

        if (introLockTicks > 0) {
            introLockTicks--;
            hoverNearCenter();
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
            moveBossBetween(movementStart, movementEnd, elapsedTicks, totalTicks, Easing::inOut);
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
            case KNOCKBACK -> prepareGroundAttackPosition(players);
            case GOLEM -> {
                prepareGroundAttackPosition(players);
                cast(Spellcaster.Spell.SUMMON_VEX);
            }
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
                punchScale(0.12);
                fx.sound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6, 0.7f);
            }
            case FANG -> {
                prepareGroundAttackPosition(players);
                fangDirections = List.of();
                cast(Spellcaster.Spell.FANGS);
                playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.8f);
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
        scaleTarget = 1.0;
        cast(Spellcaster.Spell.NONE);
        closeAttackVisuals();
    }

    private void closeAttackVisuals() {
        if (chargeStrip != null) {
            chargeStrip.close();
            chargeStrip = null;
        }
        for (Visuals.Beam beam : beams.values()) {
            beam.close();
        }
        beams.clear();
    }

    private void hitStop(int ticks) {
        hitStopTicks = Math.max(hitStopTicks, ticks);
    }

    private void cast(Spellcaster.Spell spell) {
        if (boss instanceof Spellcaster caster) {
            caster.setSpell(spell);
        }
    }

    private void tickScale() {
        // A damped spring, so every change of size overshoots a little and settles.
        scaleVelocity = (scaleVelocity + (scaleTarget - scaleMult) * 0.35) * 0.62;
        scaleMult = Math.max(0.2, Math.min(2.0, scaleMult + scaleVelocity));
        double value = config.scale() * scaleMult;
        if (Math.abs(value - appliedScale) > 0.004) {
            appliedScale = value;
            setAttribute(boss, "scale", value);
        }
    }

    private void recordDamager(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            lastDamagerId = shooter.getUniqueId();
        } else if (damager instanceof Player player) {
            lastDamagerId = player.getUniqueId();
        }
    }

    private void guardFeedback(boolean blocked) {
        if (guardFeedbackCooldown > 0) {
            return;
        }
        guardFeedbackCooldown = 4;
        Location body = body();
        if (blocked) {
            fx.spawn(Particle.ENCHANTED_HIT, body, 14, 0.7, 0.9, 0.7, 0.3);
            fx.spawn(Particle.ELECTRIC_SPARK, body, 10, 0.7, 0.9, 0.7, 0.15);
            fx.sound(body, Sound.ITEM_SHIELD_BLOCK, 0.25, 1.1f);
            fx.sound(body, Sound.BLOCK_ANVIL_PLACE, 0.15, 1.9f);
            punchScale(-0.04);
        } else {
            fx.spawn(Particle.ENCHANTED_HIT, body, 6, 0.7, 0.9, 0.7, 0.2);
            fx.sound(body, Sound.BLOCK_ANVIL_PLACE, 0.1, 1.5f);
        }
    }

    /** Damage plus the feedback that makes it register: sparks on the victim and a heavy thud only they hear. */
    private void strike(Player player, double damage, Vector knockback) {
        boolean fresh = player.getNoDamageTicks() <= player.getMaximumNoDamageTicks() / 2;
        if (damage > 0.0) {
            player.damage(damage, boss);
        }
        player.setVelocity(knockback);
        if (damage > 0.0 && fresh) {
            Location chest = player.getLocation().add(0.0, 1.0, 0.0);
            fx.spawn(Particle.CRIT, chest, 14, 0.3, 0.4, 0.3, 0.5);
            fx.dust(chest, EMBER, 1.6f, 6, 0.35);
            fx.personalSound(player, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.7f, 0.6f);
        }
    }

    /** A fixed outline of the reach plus a fill that meets it on the tick the attack lands. */
    private void telegraphCircle(Location ground, double radius, int ticks) {
        visuals.add(new Visuals.Ring(ground, Material.RED_CONCRETE, radius, radius, 0.18, 0.06, 0, ticks));
        visuals.add(new Visuals.Ring(ground, Material.RED_STAINED_GLASS, 0.4, radius, 0.5, 0.04, Math.max(1, ticks - 2), 0));
    }

    private void startTransition() {
        endAttack();
        visuals.clear();
        shockwaves.clear();
        removeProjectiles();
        transitionTick = 0;
        transitionStart = boss.getLocation().clone();
        transitionEnd = safeGroundLocation(center).add(0.0, config.hoverHeight() + 2.5, 0.0);
        scaleTarget = 0.85;
        Location body = body();
        fx.sound(body, Sound.ENTITY_VILLAGER_HURT, 1.0, 0.5f);
        fx.sound(body, Sound.BLOCK_BEACON_DEACTIVATE, 0.8, 0.6f);
        fx.spawn(Particle.CRIT, body, 30, 1.0, 1.0, 1.0, 0.4);
        hitStop(4);
    }

    private void tickTransition(List<Player> players) {
        int tick = ++transitionTick;
        if (tick < TRANSITION_SWAP_TICK) {
            double gather = tick / (double) TRANSITION_SWAP_TICK;
            double rise = Easing.inOut(tick / (double) TRANSITION_RISE_TICKS);
            double tremble = 0.02 + 0.12 * gather;
            Location pose = transitionStart.clone()
                    .add(transitionEnd.toVector().subtract(transitionStart.toVector()).multiply(rise))
                    .add((random.nextDouble() - 0.5) * tremble, (random.nextDouble() - 0.5) * tremble, (random.nextDouble() - 0.5) * tremble);
            // The spin winds up faster and faster into the swap.
            pose.setYaw(transitionStart.getYaw() + (float) (900.0 * gather * gather * gather));
            pose.setPitch(0.0f);
            teleport(pose);
            scaleTarget = 0.85 + 0.1 * Math.sin(tick * 0.8) * gather;

            Location body = body();
            fx.spawn(Particle.ENCHANT, body, 14, 0.3, 0.3, 0.3, 4.0);
            fx.spawn(Particle.PORTAL, body, 10, 0.3, 0.5, 0.3, 1.5);
            fx.dustFade(body, GOLD, VOID, 1.6f, 3, 0.9);
            if (tick % 2 == 0) {
                fx.dustRing(safeGroundLocation(pose).add(0.0, 0.1, 0.0), 0.5 + 7.0 * (1.0 - gather), VOID, 1.3f);
            }
            if (tick % 10 == 1) {
                fx.sound(body, Sound.ENTITY_WARDEN_HEARTBEAT, 0.9, (float) (0.8 + gather * 0.6));
            }
            if (tick == TRANSITION_RISE_TICKS) {
                fx.sound(body, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 1.0, 0.7f);
                shakeHead();
            }
            return;
        }

        if (tick == TRANSITION_SWAP_TICK) {
            teleport(transitionEnd);
            transformToEvoker();
            faceNearestPlayer(players);
            cast(Spellcaster.Spell.WOLOLO);
            scaleMult = 1.5;
            scaleTarget = 1.0;

            Location body = body();
            Location ground = safeGroundLocation(boss.getLocation()).add(0.0, 0.08, 0.0);
            boss.getWorld().strikeLightningEffect(ground);
            fx.flash(body, WHITE);
            fx.spawn(Particle.EXPLOSION_EMITTER, body, 1, 0.0, 0.0, 0.0, 0.0);
            fx.sphereBurst(Particle.SOUL_FIRE_FLAME, body, 50, 0.5);
            fx.sphereBurst(Particle.END_ROD, body, 40, 0.7);
            impactEffect(ground, 1.6);
            ringEffect(ground, 12.0, 16);
            fx.sound(body, Sound.ENTITY_RAVAGER_ROAR, 1.0, 0.6f);
            fx.sound(body, Sound.ENTITY_WITHER_SPAWN, 0.7, 1.2f);

            Title title = Title.title(
                    Component.text(config.name(), NamedTextColor.DARK_RED),
                    Component.text(config.phaseTwoTitle(), NamedTextColor.RED),
                    Title.Times.times(java.time.Duration.ofMillis(250), java.time.Duration.ofMillis(2000), java.time.Duration.ofMillis(750))
            );
            for (Player player : players) {
                player.showTitle(title);
                if (horizontalDistanceSquared(player.getLocation(), ground) <= 144.0) {
                    Vector away = horizontalDirection(ground, player.getLocation());
                    player.setVelocity(away.multiply(1.2).setY(0.5));
                }
            }
            return;
        }

        if (tick < TRANSITION_END_TICK) {
            faceNearestPlayer(players);
            Location body = body();
            fx.spawn(Particle.ANGRY_VILLAGER, body, 2, 1.0, 1.0, 1.0, 0.0);
            if ((tick - TRANSITION_SWAP_TICK) % 8 == 0) {
                fx.radialBurst(Particle.SOUL_FIRE_FLAME, body, 24, 0.4, 0.0);
                punchScale(0.12);
            }
            return;
        }

        cast(Spellcaster.Spell.NONE);
        transitionTick = -1;
        idleTicks = 20;
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
        hitStopTicks = 0;
        transitionTick = -1;
        cast(Spellcaster.Spell.NONE);
        closeAttackVisuals();
        visuals.clear();
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
        spawnDeathLightRays(deathStart.clone().add(0.0, config.deathLightRaysHeight(), 0.0));
        if (bossBar != null) {
            bossBar.setColor(BarColor.RED);
            bossBar.setTitle(ChatColor.DARK_RED + config.name());
            bossBar.setProgress(0.0);
        }
        Location body = body();
        fx.sound(body, Sound.ENTITY_VILLAGER_HURT, 1.0, 0.45f);
        fx.sound(body, Sound.BLOCK_BELL_RESONATE, 1.0, 0.7f);
        fx.sound(body, Sound.BLOCK_ANVIL_LAND, 0.5, 0.5f);
        fx.spawn(Particle.END_ROD, body, 35, 1.0, 0.8, 1.0, 0.01);
        fx.spawn(Particle.CRIT, body, 40, 1.0, 1.0, 1.0, 0.6);
        punchScale(-0.2);
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
            double progress = deathTick / (double) Math.max(1, stopTicks);
            double tremble = 0.03 + 0.14 * progress;
            Location frozen = deathStart.clone().add(
                    (random.nextDouble() - 0.5) * tremble,
                    Easing.out(progress) * 1.5 + (random.nextDouble() - 0.5) * tremble,
                    (random.nextDouble() - 0.5) * tremble
            );
            teleport(frozen);
            boss.setVelocity(new Vector());
            // Swelling, with a flicker that gets more violent toward the end.
            scaleTarget = 1.0 + 0.3 * progress + 0.08 * Math.sin(deathTick * 1.9) * progress;

            Location body = body();
            fx.directed(Particle.END_ROD, body, Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize(), 0.25 + 0.4 * progress);
            fx.dustFade(body, WHITE, GOLD, 1.8f, 2 + (int) (6 * progress), 0.9);
            if (deathTick % 4 == 0) {
                fx.spawn(Particle.CRIT, body, 8, 0.9, 0.7, 0.9, 0.0);
            }
            if (deathTick % 5 == 1) {
                fx.sound(body, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8, (float) (0.55 + progress * 1.25));
            }
            tickDeathLightRays(stopTicks);
            return;
        }

        if (deathTick == stopTicks + 1) {
            burst();
        }
    }

    /** The end of the death sequence: the boss dies for real here, hidden behind the flash. */
    private void burst() {
        Location body = body();
        Location ground = safeGroundLocation(boss.getLocation()).add(0.0, 0.15, 0.0);
        fx.flash(body, WHITE);
        fx.spawn(Particle.EXPLOSION_EMITTER, body, 1, 0.0, 0.0, 0.0, 0.0);
        fx.spawn(Particle.EXPLOSION, body, 5, 1.8, 0.5, 1.8, 0.0);
        fx.spawn(Particle.POOF, body, Math.max(1, config.deathParticleCount()), 2.2, 1.0, 2.2, 0.12);
        fx.sphereBurst(Particle.END_ROD, body, 80, 0.6);
        fx.sphereBurst(Particle.FIREWORK, body, 60, 0.4);
        fx.sound(body, Sound.ENTITY_GENERIC_EXPLODE, 1.0, 0.55f);
        fx.sound(body, Sound.ENTITY_WITHER_DEATH, 1.0, 0.75f);
        fx.sound(body, Sound.ITEM_TOTEM_USE, 0.6, 1.2f);
        impactEffect(ground, 2.0);
        createShockwave(ground, config.deathShockwaveRadius(), 0.0, config.deathShockwavePush());
        removeDeathLightRays();
        restoreSky();

        aftermath = true;
        aftermathTick = 0;
        aftermathCenter = body;
        Player killer = lastDamagerId == null ? null : Bukkit.getPlayer(lastDamagerId);
        if (killer != null) {
            boss.setKiller(killer);
        }
        // setHealth(0) runs the vanilla death, so EntityDeathEvent fires as usual. The entity is removed right after
        // so the flash is the last thing seen of it, not the vanilla fall-over.
        boss.setInvisible(true);
        boss.setHealth(0.0);
        boss.remove();
    }

    private void tickAftermath() {
        aftermathTick++;
        List<Player> players = activePlayers();
        updateDeathBossBar(players);
        tickShockwaves(players);
        visuals.tick();

        if (aftermathTick <= AFTERMATH_TICKS - 20) {
            for (int index = 0; index < 4; index++) {
                double angle = random.nextDouble() * TWO_PI;
                double distance = random.nextDouble() * 7.0;
                Location point = aftermathCenter.clone().add(Math.cos(angle) * distance, random.nextDouble() * 6.0, Math.sin(angle) * distance);
                fx.directed(Particle.END_ROD, point, new Vector(0.0, -1.0, 0.0), 0.05);
                fx.spawn(Particle.ITEM, point, 1, 0.0, 0.0, 0.0, 0.05, new ItemStack(Material.EMERALD));
            }
        }
        if (aftermathTick == 25) {
            Title title = Title.title(
                    Component.text(config.name(), NamedTextColor.GOLD),
                    Component.text("討伐完了", NamedTextColor.GRAY),
                    Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofMillis(2500), java.time.Duration.ofMillis(1000))
            );
            for (Player player : players) {
                player.showTitle(title);
                fx.personalSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
        if (aftermathTick >= AFTERMATH_TICKS) {
            cleanup(false);
            bossManager.finished(this);
        }
    }

    private void spawnDeathLightRays(Location origin) {
        if (!config.deathLightRaysEnabled()) {
            return;
        }
        removeDeathLightRays();
        origin.setYaw(0.0f);
        origin.setPitch(0.0f);
        deathRayYaw = 0.0f;

        Vector forward = deathStart == null ? boss.getLocation().getDirection() : deathStart.getDirection();
        forward.setY(0.0);
        if (forward.lengthSquared() <= 0.01) {
            forward = new Vector(0.0, 0.0, 1.0);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX()).normalize();
        Vector up = new Vector(0.0, 1.0, 0.0);

        double scale = Math.max(0.001, config.deathLightRaysScale());
        float viewRange = (float) Math.max(1.0, config.deathLightRaysViewRange());
        for (LightRaySpec spec : LIGHT_RAYS) {
            List<RayPart> parts = new ArrayList<>();
            spawnRayTriangle(parts, origin, right, up, spec.angleDegrees(), spec.wideHalfWidth(), scale,
                    color(spec.wideAlpha(), spec.wideRed(), spec.wideGreen(), spec.wideBlue()), viewRange);
            spawnRayTriangle(parts, origin, right, up, spec.angleDegrees(), spec.narrowHalfWidth(), scale,
                    color(spec.narrowAlpha(), spec.narrowRed(), spec.narrowGreen(), spec.narrowBlue()), viewRange);
            deathRays.add(parts);
        }
    }

    /** Rays shoot out one after another, the whole fan turns slowly, and everything flares just before the burst. */
    private void tickDeathLightRays(int stopTicks) {
        if (deathRays.isEmpty()) {
            return;
        }
        int gap = Math.max(1, (int) (stopTicks * 0.55) / deathRays.size());
        int revealTick = deathTick - 3;
        if (revealTick >= 0 && revealTick % gap == 0 && revealTick / gap < deathRays.size()) {
            for (RayPart part : deathRays.get(revealTick / gap)) {
                morphRay(part, 1.0f, 8);
            }
            fx.sound(body(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8, 0.6f + 0.1f * (revealTick / gap));
        }
        if (deathTick == stopTicks - 5) {
            for (List<RayPart> parts : deathRays) {
                for (RayPart part : parts) {
                    morphRay(part, 1.4f, 5);
                }
            }
        }
        if (deathTick % 5 == 0) {
            deathRayYaw += 10.0f;
            for (List<RayPart> parts : deathRays) {
                for (RayPart part : parts) {
                    if (part.display().isValid()) {
                        part.display().setRotation(deathRayYaw, 0.0f);
                    }
                }
            }
        }
    }

    private void morphRay(RayPart part, float size, int ticks) {
        if (!part.display().isValid()) {
            return;
        }
        part.display().setInterpolationDelay(0);
        part.display().setInterpolationDuration(ticks);
        part.display().setTransformation(scaledFromOrigin(part.full(), size));
    }

    private Transformation scaledFromOrigin(Transformation full, float size) {
        return new Transformation(
                new Vector3f(full.getTranslation()).mul(size),
                full.getLeftRotation(),
                new Vector3f(full.getScale()).mul(size),
                full.getRightRotation()
        );
    }

    private void removeDeathLightRays() {
        for (BukkitTriangle shape : deathLightRayShapes) {
            shape.remove();
        }
        deathLightRayShapes.clear();
        deathRays.clear();
    }

    private void spawnRayTriangle(List<RayPart> parts, Location origin, Vector right, Vector up, double angleDegrees,
                                  double halfWidth, double scale, Color color, float viewRange) {
        Vector3f p1 = toVector3f(origin);
        Vector3f p2 = toVector3f(svgPoint(origin, right, up, -halfWidth, -SVG_RAY_LENGTH, angleDegrees, scale));
        Vector3f p3 = toVector3f(svgPoint(origin, right, up, halfWidth, -SVG_RAY_LENGTH, angleDegrees, scale));
        BukkitTriangle shape = new BukkitTriangle.Builder(origin.clone(), p1, p2, p3)
                .color(color)
                .doubleSided(true)
                .brightness(15, 15)
                .seeThrough(true)
                .viewRange(viewRange)
                .build();
        shape.spawn();
        deathLightRayShapes.add(shape);
        // Every ray starts collapsed into the origin so it can be shot out later.
        for (TextDisplay display : shape.getEntities()) {
            Transformation full = display.getTransformation();
            display.setPersistent(false);
            display.setTeleportDuration(5);
            display.setTransformation(scaledFromOrigin(full, 0.0f));
            parts.add(new RayPart(display, full));
        }
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
        Location destination = needsPositioning ? groundAttackDestination(targetGround, awayFromTarget) : current;
        // Outside the arena the ground can be far below the battlefield, so the boss never lands out there.
        destination = groundLocation(clampToArena(destination));
        destination.setDirection(targetGround.toVector().subtract(destination.toVector()));

        movementStart = boss.getLocation().clone();
        movementEnd = destination;
        if (needsPositioning || movementStart.distanceSquared(movementEnd) > 0.04) {
            positioningTicksRemaining = Math.max(1, config.groundAttackPositionTicks());
        } else {
            teleport(destination);
        }
    }

    /** A spot at the preferred distance from the target, swung around it until it lands inside the arena. */
    private Location groundAttackDestination(Location targetGround, Vector awayFromTarget) {
        double distance = config.groundAttackPreferredDistance();
        for (int step = 0; step <= 6; step++) {
            for (int side = -1; side <= 1; side += 2) {
                Vector direction = rotateY(awayFromTarget, Math.toRadians(30.0 * step * side));
                Location candidate = targetGround.clone().add(direction.multiply(distance));
                if (horizontalDistanceSquared(candidate, center) <= config.arenaRadius() * config.arenaRadius()) {
                    return candidate;
                }
            }
        }
        return targetGround.clone().add(awayFromTarget.clone().multiply(distance));
    }

    private void tickTeleport() {
        int interval = Math.max(1, config.teleportIntervalTicks());
        if (teleportCount < teleportLimit && (attackTick + 4) % interval == 1) {
            // Shrinks away just before each blink.
            scaleTarget = 0.55;
        }
        if (attackTick % interval == 1 && teleportCount < teleportLimit) {
            teleportCount++;
            Location from = boss.getLocation().clone();
            Location fromBody = body();
            Location destination = randomArenaLocation(0.0);
            fx.spawn(Particle.PORTAL, fromBody, 60, 0.6, 1.0, 0.6, 1.2);
            fx.spawn(Particle.REVERSE_PORTAL, fromBody, 40, 0.3, 0.8, 0.3, 0.15);
            fx.dustRing(from.clone().add(0.0, 0.1, 0.0), 1.6, VOID, 1.4f);
            teleport(destination);
            Location destinationBody = body();
            fx.line(fromBody, destinationBody, Math.max(0.2, config.teleportTrailStep()), point -> {
                fx.dustFade(point, VOID, DUSK, 1.6f, 1, 0.08);
                fx.spawn(Particle.PORTAL, point, 2, 0.1, 0.1, 0.1, 0.3);
            });
            fx.spawn(Particle.REVERSE_PORTAL, destinationBody, 50, 0.3, 0.8, 0.3, 0.2);
            fx.radialBurst(Particle.END_ROD, destinationBody, 16, 0.25, 0.0);
            fx.dustRing(destination.clone().add(0.0, 0.1, 0.0), 2.0, VOID, 1.5f);
            fx.sound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8, 0.8f);
            fx.sound(destination, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.6, 1.2f);
            // Pops back to full size on arrival.
            scaleMult = 0.35;
            scaleTarget = 1.0;
        }
        if (teleportCount >= teleportLimit && attackTick > teleportLimit * interval + 12) {
            endAttack();
        }
    }

    private void tickKnockback(List<Player> players) {
        Location source = body();
        Location ground = safeGroundLocation(boss.getLocation()).add(0.0, 0.08, 0.0);
        if (attackTick == 1) {
            telegraphCircle(ground, config.knockbackShockwaveRadius(), 21);
            scaleTarget = 0.8;
            shakeHead();
            fx.sound(source, Sound.ENTITY_BREEZE_INHALE, 0.8, 0.6f);
        }
        if (attackTick <= 20) {
            // Air being drawn in toward the boss.
            for (int index = 0; index < 3; index++) {
                double angle = attackTick * 0.9 + index * TWO_PI / 3.0;
                Vector offset = new Vector(Math.cos(angle) * 3.5, 0.0, Math.sin(angle) * 3.5);
                fx.directed(Particle.CLOUD, source.clone().add(offset), offset.clone().normalize().multiply(-1.0), 0.35);
            }
        }
        if (attackTick == 22) {
            scaleTarget = 1.0;
            punchScale(0.3);
            fx.sound(source, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0, 0.7f);
            fx.sound(source, Sound.ENTITY_BREEZE_WIND_BURST, 0.8, 0.7f);
            fx.spawn(Particle.GUST_EMITTER_SMALL, source, 1, 0.0, 0.0, 0.0, 0.0);
            impactEffect(ground, 1.2);
            hitStop(2);
            createShockwave(ground, config.knockbackShockwaveRadius(), config.knockbackDamage(), 1.8);
        }
        if (attackTick >= 38) {
            endAttack();
        }
    }

    private void tickGolem() {
        if (attackTick < 24) {
            Location body = body();
            fx.spawn(Particle.HAPPY_VILLAGER, body, 5, 1.2, 0.6, 1.2, 0.02);
            fx.spawn(Particle.ENCHANT, body, 6, 0.3, 0.3, 0.3, 3.0);
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
                Location spot = golem.getLocation();
                fx.debris(spot, 30, 0.7, 0.2);
                fx.spawn(Particle.POOF, spot.clone().add(0.0, 1.0, 0.0), 30, 0.7, 0.7, 0.7, 0.04);
                fx.dustRing(spot.clone().add(0.0, 0.1, 0.0), 1.5, WHITE, 1.3f);
            }
            punchScale(0.15);
            playSound(boss.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 0.8f);
        }
        if (attackTick >= 48) {
            endAttack();
        }
    }

    private void tickVillagerCannon(List<Player> players) {
        int skyTicks = Math.max(0, config.skyMoveTicks());
        if (attackTick <= skyTicks) {
            moveBossBetween(movementStart, movementEnd, attackTick, skyTicks, Easing::inOut);
            faceNearestPlayer(players);
            return;
        }

        int actionTick = attackTick - skyTicks;
        if (actionTick == 1) {
            cast(Spellcaster.Spell.SUMMON_VEX);
            scaleTarget = 0.88;
            shakeHead();
            fx.sound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.8, 1.1f);
        }
        if (actionTick <= config.cannonWarmupTicks()) {
            Location body = body();
            fx.spawn(Particle.ENCHANT, body, 10, 0.2, 0.2, 0.2, 3.0);
            fx.spawn(Particle.ANGRY_VILLAGER, body, 3, 0.8, 0.5, 0.8, 0.01);
            return;
        }

        int fireTick = actionTick - config.cannonWarmupTicks() - 1;
        int interval = Math.max(1, config.cannonShotIntervalTicks());
        if (fireTick == 0) {
            scaleTarget = 1.0;
        }
        if (fireTick >= 0 && fireTick % interval == 0) {
            int shotIndex = fireTick / interval;
            if (shotIndex < config.cannonShotCount()) {
                faceNearestPlayer(players);
                for (Player target : players) {
                    fireVillagerProjectile(target);
                }
                // Recoil.
                punchScale(-0.06);
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
            // Dizzy: stars circling the head.
            Location head = boss.getLocation().add(0.0, boss.getHeight() + 0.3, 0.0);
            for (int index = 0; index < 3; index++) {
                double angle = age * 0.45 + index * TWO_PI / 3.0;
                fx.dust(head.clone().add(Math.cos(angle) * 0.9, 0.0, Math.sin(angle) * 0.9), GOLD, 1.3f, 1, 0.0);
            }
            if (chargePhaseTick >= config.chargeStunTicks()) {
                endAttack();
            }
            return;
        }

        if (chargePhaseTick <= config.chargeWarmupTicks()) {
            invulnerable = false;
            teleport(groundLocation(boss.getLocation()));
            // The aim locks shortly before the run, so a player who keeps sprinting gets clear of the lane.
            if (chargePhaseTick == 1 || chargePhaseTick <= config.chargeWarmupTicks() - config.chargeAimLockTicks()) {
                updateChargeDirection(players);
            }
            if (chargePhaseTick == 1) {
                scaleTarget = 0.85;
                chargeStrip = visuals.add(new Visuals.Strip(boss.getLocation().add(0.0, 0.06, 0.0), Material.RED_STAINED_GLASS,
                        config.chargeSpeed() * config.chargeRunTicks(), 0.05));
                fx.sound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6, 1.4f);
            }
            if (chargeStrip != null) {
                // The lane widens to the real hit width as the windup completes.
                double widen = Easing.out(chargePhaseTick / (double) Math.max(1, config.chargeWarmupTicks()));
                chargeStrip.aim(chargeDirection, 0.3 + 4.7 * widen);
            }
            if (chargePhaseTick % 4 == 0) {
                // Pawing the ground.
                fx.debris(boss.getLocation(), 8, 0.5, 0.1);
                fx.sound(boss.getLocation(), Sound.ENTITY_RAVAGER_STEP, 0.3, 0.8f);
            }
            fx.spawn(Particle.ANGRY_VILLAGER, boss.getLocation().add(0.0, 1.5, 0.0), 2, 0.8, 0.5, 0.8, 0.01);
            return;
        }

        int runEnd = config.chargeWarmupTicks() + config.chargeRunTicks();
        if (chargePhaseTick <= runEnd) {
            invulnerable = true;
            if (chargePhaseTick == config.chargeWarmupTicks() + 1) {
                if (config.chargeWarmupTicks() <= 0) {
                    updateChargeDirection(players);
                }
                if (chargeStrip != null) {
                    chargeStrip.close();
                    chargeStrip = null;
                }
                chargeImpactDone = false;
                scaleTarget = 1.0;
                punchScale(0.25);
                fx.sound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8, 0.6f);
            }
            Location next = moveOnGround(boss.getLocation(), chargeDirection, config.chargeSpeed());
            teleport(next);
            fx.debris(next, 6, 0.5, 0.15);
            fx.directed(Particle.CLOUD, next.clone().add(0.0, 0.3, 0.0), chargeDirection.clone().multiply(-1.0), 0.25);
            fx.dustFade(body(), EMBER, GOLD, 1.8f, 4, 0.5);
            fx.spawn(Particle.FLAME, boss.getLocation().add(0.0, 1.0, 0.0), 3, 0.5, 0.3, 0.5, 0.02);
            if (chargePhaseTick % 3 == 0) {
                fx.sound(next, Sound.ENTITY_RAVAGER_STEP, 0.5, 0.7f);
            }
            for (Player player : players) {
                if (player.getLocation().distanceSquared(boss.getLocation()) <= 6.25) {
                    strike(player, config.chargeDamage(), chargeDirection.clone().multiply(1.2).setY(0.45));
                    if (!chargeImpactDone) {
                        chargeImpactDone = true;
                        fx.sound(next, Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.6, 0.6f);
                        hitStop(2);
                    }
                }
            }
            return;
        }

        invulnerable = false;
        if (chargePhaseTick == runEnd + 1) {
            // Skidding to a halt.
            fx.debris(boss.getLocation(), 20, 0.8, 0.2);
            fx.spawn(Particle.CLOUD, boss.getLocation().add(0.0, 0.3, 0.0), 10, 0.8, 0.1, 0.8, 0.05);
            punchScale(-0.1);
        }
        if (chargePhaseTick >= runEnd + config.chargeRecoveryTicks()) {
            chargeCount++;
            chargePhaseTick = 0;
            if (chargeCount >= config.chargeRepeats()) {
                scaleTarget = 0.9;
                playSound(boss.getLocation(), Sound.ENTITY_VILLAGER_HURT, 0.6f);
                fx.sound(boss.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 0.7, 1.0f);
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
            moveBossBetween(movementStart, movementEnd, attackTick, skyTicks, Easing::inOut);
            faceNearestPlayer(players);
            return;
        }

        int actionTick = attackTick - skyTicks;
        if (players.isEmpty()) {
            endAttack();
            return;
        }

        Location beamStart = boss.getLocation().clone().add(0.0, 1.0, 0.0);
        int warningTicks = config.beamWarningTicks();
        pruneBeams(players);
        if (actionTick == 1) {
            cast(Spellcaster.Spell.WOLOLO);
            scaleTarget = 0.9;
            fx.sound(beamStart, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 0.8, 0.8f);
        }
        if (actionTick == Math.max(1, warningTicks - 34)) {
            // The warden's charge-up runs about 34 ticks, so it ends as the beam fires.
            fx.sound(beamStart, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0, 1.0f);
        }
        if (actionTick <= warningTicks) {
            faceNearestPlayer(players);
            double charge = actionTick / (double) Math.max(1, warningTicks);
            for (Player player : players) {
                Location targetLocation = delayedLocation(player, config.beamTargetDelayTicks()).add(0.0, 1.0, 0.0);
                aimBeam(beamFor(player, beamStart, false), beamStart, targetLocation, 0.06 + 0.1 * charge, 0.0);
                fx.dust(targetLocation, DANGER, 1.2f, 2, 0.25);
                fx.spawn(Particle.ELECTRIC_SPARK, targetLocation, 3, 0.5, 0.5, 0.5, 0.02);
            }
            fx.spawn(Particle.ENCHANT, beamStart, 8, 0.2, 0.2, 0.2, 3.0);
            if (actionTick % 8 == 1) {
                fx.sound(beamStart, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4, (float) (0.8 + charge));
            }
            return;
        }

        int fireTick = actionTick - warningTicks;
        if (fireTick <= config.beamDurationTicks()) {
            if (fireTick == 1) {
                scaleTarget = 1.0;
                punchScale(0.2);
                for (Visuals.Beam beam : beams.values()) {
                    beam.coreMaterial(Material.WHITE_CONCRETE);
                }
                fx.flash(beamStart, GOLD);
                fx.sound(beamStart, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0, 0.9f);
                fx.sound(beamStart, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0, 0.6f);
                fx.sound(beamStart, Sound.BLOCK_BEACON_ACTIVATE, 0.7, 1.6f);
            }
            if (fireTick % 12 == 1) {
                fx.sound(beamStart, Sound.ENTITY_GUARDIAN_ATTACK, 0.6, 0.7f);
            }
            double pulse = 1.0 + 0.12 * Math.sin(fireTick * 1.3);
            Player nearest = nearestPlayer(players);
            for (Player beamTarget : players) {
                Location targetLocation = delayedLocation(beamTarget, config.beamTargetDelayTicks()).add(0.0, 1.0, 0.0);
                if (beamTarget == nearest) {
                    faceLocation(targetLocation);
                }
                // The sleeve is as wide as the hit radius, so what you see is what hits.
                aimBeam(beamFor(beamTarget, beamStart, true), beamStart, targetLocation, 1.1 * pulse, 3.0 * pulse);
                fx.spawn(Particle.FLAME, targetLocation, 6, 0.4, 0.4, 0.4, 0.08);
                fx.spawn(Particle.ELECTRIC_SPARK, targetLocation, 6, 0.6, 0.6, 0.6, 0.1);
                fx.debris(targetLocation.clone().add(0.0, -1.0, 0.0), 5, 0.5, 0.1);
                fx.line(beamStart, targetLocation, 4.0, point -> fx.spawn(Particle.END_ROD, point, 1, 0.5, 0.5, 0.5, 0.03));
                if (fireTick % Math.max(1, config.beamDamageIntervalTicks()) == 1) {
                    for (Player player : players) {
                        if (distanceToSegment(player.getLocation().add(0.0, 1.0, 0.0), beamStart, targetLocation) <= 1.6) {
                            Vector push = player.getLocation().toVector().subtract(beamStart.toVector()).normalize().multiply(0.8).setY(0.35);
                            strike(player, config.beamDamage(), push);
                        }
                    }
                }
            }
            return;
        }

        if (fireTick == config.beamDurationTicks() + 1) {
            closeAttackVisuals();
            cast(Spellcaster.Spell.NONE);
            fx.sound(beamStart, Sound.BLOCK_BEACON_DEACTIVATE, 0.7, 1.4f);
        }
        if (fireTick >= config.beamDurationTicks() + config.beamRecoveryTicks()) {
            endAttack();
        }
    }

    private Visuals.Beam beamFor(Player player, Location start, boolean firing) {
        return beams.computeIfAbsent(player.getUniqueId(), ignored -> visuals.add(new Visuals.Beam(
                start, firing ? Material.WHITE_CONCRETE : Material.RED_CONCRETE, Material.YELLOW_STAINED_GLASS)));
    }

    private void aimBeam(Visuals.Beam beam, Location start, Location target, double coreWidth, double glowWidth) {
        Vector delta = target.toVector().subtract(start.toVector());
        beam.aim(delta, delta.length(), coreWidth, glowWidth);
    }

    private void pruneBeams(List<Player> players) {
        beams.entrySet().removeIf(entry -> {
            if (players.stream().anyMatch(player -> player.getUniqueId().equals(entry.getKey()))) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
    }

    private void tickStomp(List<Player> players) {
        int cycleLength = config.stompAscendTicks() + config.stompWarningTicks() + config.stompFallTicks() + config.stompRecoveryTicks();
        int cycleTick = attackTick - stompCount * cycleLength;
        int ascendEnd = config.stompAscendTicks();
        int warningEnd = ascendEnd + config.stompWarningTicks();
        int fallEnd = warningEnd + config.stompFallTicks();
        if (cycleTick == 1) {
            Player target = randomPlayer(players);
            if (target == null) {
                endAttack();
                return;
            }
            stompTarget = safeGroundLocation(clampToArena(target.getLocation()));
            movementStart = boss.getLocation().clone();
            movementEnd = stompTarget.clone().add(0.0, config.stompHeight(), 0.0);
            telegraphCircle(stompTarget.clone().add(0.0, 0.08, 0.0), config.stompShockwaveRadius(), fallEnd);
            scaleTarget = 1.0;
            punchScale(0.15);
            playSound(movementEnd, Sound.ENTITY_VILLAGER_CELEBRATE, 0.55f);
            fx.sound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6, 0.9f);
        }
        if (stompTarget == null) {
            endAttack();
            return;
        }

        if (cycleTick <= ascendEnd) {
            moveBossBetween(movementStart, movementEnd, cycleTick, ascendEnd, Easing::out);
        } else if (cycleTick <= warningEnd) {
            teleport(movementEnd);
            // Curls up while it hangs over the target.
            scaleTarget = 0.82;
            fx.line(movementEnd, stompTarget, 1.5, point -> fx.dust(point, DANGER, 1.0f, 1, 0.0));
        } else if (cycleTick <= fallEnd) {
            scaleTarget = 1.15;
            moveBossBetween(movementEnd, stompTarget, cycleTick - warningEnd, config.stompFallTicks(), Easing::in);
            fx.dust(boss.getLocation().add(0.0, boss.getHeight(), 0.0), WHITE, 2.0f, 6, 0.6);
        } else if (cycleTick == fallEnd + 1) {
            // Squashed flat by the landing, then springs back.
            scaleMult = 0.62;
            scaleTarget = 1.0;
            fx.sound(stompTarget, Sound.ENTITY_GENERIC_EXPLODE, 0.8, 1.1f);
            fx.sound(stompTarget, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0, 0.8f);
            fx.sound(stompTarget, Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.6, 0.5f);
            Location shockwave = stompTarget.clone().add(0.0, 0.08, 0.0);
            impactEffect(shockwave, 1.5);
            hitStop(3);
            createShockwave(shockwave, config.stompShockwaveRadius(), config.stompDamage(), 1.1);
        } else if (cycleTick >= cycleLength) {
            stompCount++;
            stompTarget = null;
            if (stompCount >= config.stompCount()) {
                movementStart = boss.getLocation().clone();
                movementEnd = randomArenaLocation(config.hoverHeight());
                moveBossBetween(movementStart, movementEnd, 1, Math.max(1, config.stompAscendTicks()), Easing::clamp);
                endAttack();
            }
        }
    }

    private void tickFang(List<Player> players) {
        Location origin = groundLocation(boss.getLocation());
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

            // Warning tiles run out along each line, and each one stays until its fang has bitten.
            int rowDelay = Math.max(1, config.fangRowDelayTicks());
            for (Vector direction : fangDirections) {
                for (int row = 1; row <= config.fangRows(); row++) {
                    Location tile = safeGroundLocation(origin.clone().add(direction.clone().multiply(config.fangSpacing() * row))).add(0.0, 0.06, 0.0);
                    visuals.add(new Visuals.Marker(tile, Material.RED_STAINED_GLASS, 1.5, 0.05, row + 1, config.fangWarmupTicks() + row * rowDelay + 4));
                }
            }
        }

        if (attackTick <= config.fangWarmupTicks()) {
            Location body = body();
            fx.spawn(Particle.WITCH, body, 4, 0.8, 0.8, 0.8, 0.0);
            fx.spawn(Particle.ENCHANT, body, 6, 0.3, 0.3, 0.3, 3.0);
            return;
        }

        int fangTick = attackTick - config.fangWarmupTicks() - 1;
        int delay = Math.max(1, config.fangRowDelayTicks());
        if (fangTick == 0) {
            punchScale(0.15);
            fx.sound(boss.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.8, 0.8f);
        }
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

        // Two blades sweeping around at exactly the hit radius.
        Location middle = boss.getLocation().add(0.0, 1.0, 0.0);
        for (int blade = 0; blade < 2; blade++) {
            double angle = attackTick * 0.8 + blade * Math.PI;
            for (double reach = 0.8; reach <= config.spinHitRadius(); reach += 0.5) {
                fx.dustFade(middle.clone().add(Math.cos(angle) * reach, 0.0, Math.sin(angle) * reach), GOLD, EMBER, 1.5f, 1, 0.0);
            }
        }
        fx.spawn(Particle.SWEEP_ATTACK, middle, 2, 1.0, 0.3, 1.0, 0.0);
        if (attackTick % 2 == 0) {
            fx.dustRing(boss.getLocation().add(0.0, 0.1, 0.0), config.spinHitRadius(), EMBER, 1.0f);
        }
        if (attackTick % 3 == 0) {
            fx.debris(boss.getLocation(), 5, 0.8, 0.15);
        }
        if (attackTick % 6 == 1) {
            fx.sound(middle, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6, 0.6f);
        }

        double hitRadiusSquared = config.spinHitRadius() * config.spinHitRadius();
        for (Player player : players) {
            if (player.getLocation().distanceSquared(boss.getLocation()) <= hitRadiusSquared
                    && spinHitCooldowns.getOrDefault(player.getUniqueId(), 0) <= 0) {
                Vector push = player.getLocation().toVector().subtract(boss.getLocation().toVector());
                if (push.lengthSquared() < 0.01) {
                    push = new Vector(0.0, 0.0, 1.0);
                }
                strike(player, config.spinDamage(), push.normalize().multiply(1.0).setY(0.45));
                spinHitCooldowns.put(player.getUniqueId(), Math.max(1, config.spinDamageIntervalTicks()));
            }
        }

        if (attackTick >= config.spinDurationTicks()) {
            punchScale(-0.15);
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
        fx.spawn(Particle.FIREWORK, origin, 6, 0.15, 0.15, 0.15, 0.08);
        fx.directed(Particle.CLOUD, origin, velocity.clone().normalize(), 0.5);
        fx.sound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.45, 1.3f);
        fx.sound(origin, Sound.ENTITY_VILLAGER_YES, 0.35, 1.6f);
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
                fx.spawn(Particle.CLOUD, projectile.entity.getLocation().add(0.0, 0.4, 0.0), 1, 0.1, 0.05, 0.1, 0.0);
                if (projectile.lodgedTicks >= config.cannonProjectileLodgeTicks()) {
                    fx.spawn(Particle.POOF, projectile.entity.getLocation().add(0.0, 0.5, 0.0), 10, 0.25, 0.25, 0.25, 0.02);
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
            // Tumbles through the air.
            next.setYaw(next.getYaw() + 38.0f);
            teleport(projectile.entity, next);
            fx.dustFade(next.clone().add(0.0, 0.4, 0.0), GOLD, GRASS, 1.3f, 2, 0.12);
            fx.spawn(Particle.HAPPY_VILLAGER, next, 1, 0.2, 0.2, 0.2, 0.0);

            for (Player player : players) {
                if (player.getLocation().distanceSquared(next) <= 2.25) {
                    strike(player, config.villagerCannonDamage(), projectile.velocity.clone().multiply(1.1).setY(0.45));
                    lodgeProjectile(projectile, player.getLocation());
                    break;
                }
            }
        }
    }

    private void createShockwave(Location center, double maxRadius, double damage, double pushStrength) {
        double startRadius = Math.max(0.0, Math.min(maxRadius, config.shockwaveStartRadius()));
        double speed = Math.max(0.01, config.shockwaveSpeed());
        // The wave travels exactly as far as its radius says, which is also what the telegraph draws.
        int duration = Math.max(1, (int) Math.ceil((maxRadius - startRadius) / speed));
        shockwaves.add(new Shockwave(center.clone(), startRadius, maxRadius, speed, duration, damage, pushStrength));

        // The display ring takes a couple of ticks to reach clients, so it starts where the wave will be by then.
        double thickness = config.shockwaveRingThickness();
        double lead = Math.min(maxRadius, startRadius + speed * 2.0);
        visuals.add(new Visuals.Ring(center, Material.WHITE_STAINED_GLASS, lead - thickness / 2.0, maxRadius - thickness / 2.0,
                thickness, 0.35, Math.max(1, duration - 2), 0));
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

            // The first tick is the impact itself, so it also covers everyone standing inside the starting radius.
            boolean impact = shockwave.age == 1;
            for (Player player : players) {
                double distance = Math.sqrt(horizontalDistanceSquared(player.getLocation(), shockwave.center));
                Double lastDistance = shockwave.lastDistances.put(player.getUniqueId(), distance);
                if (shockwave.hitPlayers.contains(player.getUniqueId())) {
                    continue;
                }
                // Hit when the front has swept over the player since the last tick, whichever of the two moved.
                boolean swept = impact || (lastDistance == null ? distance : lastDistance) > previousRadius;
                if (swept && distance <= radius && isOnShockwaveHeight(player, shockwave.center)) {
                    shockwave.hitPlayers.add(player.getUniqueId());
                    Vector push = player.getLocation().toVector().subtract(shockwave.center.toVector());
                    push.setY(0.0);
                    if (push.lengthSquared() < 0.01) {
                        push = new Vector(0.0, 0.0, 1.0);
                    }
                    strike(player, shockwave.damage, push.normalize().multiply(shockwave.pushStrength).setY(config.shockwaveKnockbackY()));
                }
            }

            if (shockwave.age >= shockwave.duration) {
                iterator.remove();
            }
        }
    }

    private void drawShockwaveRing(Location center, double radius) {
        fx.ring(center, radius, 1.5, config.shockwaveRingParticles(), point -> fx.dust(point, WHITE, 1.3f, 1, 0.05));
        fx.ring(center, radius, 0.4, 8, point -> fx.spawn(Particle.CLOUD, point, 1, 0.08, 0.01, 0.08, 0.0));
        fx.ring(center, radius, 0.3, 6, point -> fx.debris(point, 2, 0.2, 0.05));
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
        Location surface = lodgedLocation.clone().add(0.0, 0.55, 0.0);
        fx.debris(surface, 18, 0.35, 0.15);
        fx.dustRing(surface.clone().add(0.0, 0.1, 0.0), 0.9, GOLD, 1.0f);
        fx.sound(lodgedLocation, Sound.BLOCK_GRAVEL_HIT, 0.5, 0.8f);
        fx.sound(lodgedLocation, Sound.ENTITY_VILLAGER_HURT, 0.3, 1.8f);
    }

    private void spawnFang(Location location, Vector direction, List<Player> players) {
        Location ground = safeGroundLocation(location);
        ground.setDirection(direction);
        Entity fang = boss.getWorld().spawnEntity(ground, EntityType.EVOKER_FANGS);
        ownedEntities.add(fang);
        fx.debris(ground, 5, 0.3, 0.1);
        fx.sound(ground, Sound.ENTITY_EVOKER_FANGS_ATTACK, 0.45, 0.9f);
        for (Player player : players) {
            if (player.getLocation().distanceSquared(ground) <= 2.25) {
                strike(player, config.fangDamage(), direction.clone().multiply(0.35).setY(0.35));
            }
        }
    }

    private void hoverNearCenter() {
        Location location = boss.getLocation();
        double maxDistance = config.arenaRadius() * 1.15;
        if (location.distanceSquared(center) > maxDistance * maxDistance) {
            teleport(randomArenaLocation(config.hoverHeight()));
            return;
        }
        // A slow bob so the boss never hangs perfectly still.
        double y = location.getY() + (Math.sin(age * 0.11) - Math.sin((age - 1) * 0.11)) * 0.25;
        double floor = safeGroundLocation(location).getY() + 1.5;
        if (y < floor) {
            y += Math.min(0.3, (floor - y) * 0.35 + 0.02);
        }
        location.setY(y);
        teleport(location);
    }

    private int phase() {
        double ratio = boss.getHealth() / config.maxHealth();
        return ratio <= config.phaseTwoHealthRatio() ? 2 : 1;
    }

    private void transformToEvoker() {
        phaseTwoTransformed = true;
        double health = Math.max(1.0, boss.getHealth());
        Entity oldBoss = boss;

        LivingEntity evoker = (LivingEntity) oldBoss.getWorld().spawnEntity(oldBoss.getLocation(), EntityType.EVOKER);
        oldBoss.remove();
        boss = evoker;
        appliedScale = -1.0;
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

    Location safeGroundLocation(Location input) {
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
        next = groundLocationAtExactXZ(clampToArena(next));
        next.setDirection(horizontal);
        return next;
    }

    /** Pulls a point back to the arena's edge if it lies beyond it. */
    private Location clampToArena(Location input) {
        double dx = input.getX() - center.getX();
        double dz = input.getZ() - center.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= config.arenaRadius()) {
            return input;
        }
        double shrink = config.arenaRadius() / distance;
        Location clamped = input.clone();
        clamped.setX(center.getX() + dx * shrink);
        clamped.setZ(center.getZ() + dz * shrink);
        return clamped;
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

    private void moveBossBetween(Location from, Location to, int elapsedTicks, int durationTicks, DoubleUnaryOperator easing) {
        if (from == null || to == null) {
            return;
        }
        if (durationTicks <= 0) {
            teleport(to);
            return;
        }
        double progress = easing.applyAsDouble(Easing.clamp(elapsedTicks / (double) durationTicks));
        Location next = from.clone().add(to.toVector().subtract(from.toVector()).multiply(progress));
        next.setYaw(to.getYaw());
        next.setPitch(to.getPitch());
        teleport(next);
        fx.spawn(Particle.CLOUD, next, 2, 0.2, 0.2, 0.2, 0.0);
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
            turnToward(direction, 20.0f);
        }
    }

    /** Turns at a limited rate, so the boss tracks its target instead of snapping to it. */
    private void turnToward(Vector direction, float maxDegrees) {
        Location location = boss.getLocation();
        Location facing = location.clone();
        facing.setDirection(direction);
        float delta = ((facing.getYaw() - location.getYaw()) % 360.0f + 540.0f) % 360.0f - 180.0f;
        location.setYaw(location.getYaw() + Math.max(-maxDegrees, Math.min(maxDegrees, delta)));
        location.setPitch(0.0f);
        teleport(location);
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
        fx.sound(location, sound, 1.0, pitch);
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
        bossBar.setTitle(bossBarTitle());
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, boss.getHealth() / config.maxHealth())));
        syncBossBarPlayers(players);
    }

    private String bossBarTitle() {
        String phaseTitle = phaseTwoTransformed
                ? ChatColor.RED + config.phaseTwoTitle()
                : ChatColor.YELLOW + config.phaseOneTitle();
        return ChatColor.GOLD + config.name() + ChatColor.DARK_GRAY + " ― " + phaseTitle;
    }

    private void updateDeathBossBar(List<Player> players) {
        if (bossBar == null) {
            return;
        }
        bossBar.setTitle(aftermath
                ? ChatColor.DARK_RED + config.name() + ChatColor.DARK_GRAY + " ― " + ChatColor.GRAY + "討伐"
                : ChatColor.DARK_RED + config.name());
        bossBar.setProgress(0.0);
        syncBossBarPlayers(players);
    }

    private void syncBossBarPlayers(List<Player> players) {
        for (Player existing : new ArrayList<>(bossBar.getPlayers())) {
            if (!players.contains(existing)) {
                bossBar.removePlayer(existing);
            }
        }
        for (Player player : players) {
            bossBar.addPlayer(player);
        }
    }

    /** Phase two is fought under a night sky. Only the players in the fight see it. */
    private void syncSky(List<Player> players) {
        boolean dark = phaseTwoTransformed && config.phaseTwoDarkenSky();
        darkenedPlayers.removeIf(uuid -> {
            if (dark && players.stream().anyMatch(player -> player.getUniqueId().equals(uuid))) {
                return false;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.resetPlayerTime();
            }
            return true;
        });
        if (dark) {
            for (Player player : players) {
                if (darkenedPlayers.add(player.getUniqueId())) {
                    player.setPlayerTime(NIGHT_TIME, false);
                }
            }
        }
    }

    private void restoreSky() {
        for (UUID uuid : darkenedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.resetPlayerTime();
            }
        }
        darkenedPlayers.clear();
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
        visuals.clear();
        beams.clear();
        chargeStrip = null;
        restoreSky();
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

    private record RayPart(TextDisplay display, Transformation full) {
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
        private final Map<UUID, Double> lastDistances = new HashMap<>();
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

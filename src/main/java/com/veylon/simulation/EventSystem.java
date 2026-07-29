package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.settlement.SettlementManager;
import com.veylon.world.BlockType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static com.veylon.simulation.EventConstants.*;

/**
 * State-driven world events. Triggers consider weather, moisture, trust,
 * season and time instead of being purely random timers. Every event changes
 * real systems: temperature, growth, fire spread, wildlife, NPC behavior.
 */
public class EventSystem implements SlowTickSystem {

    public enum EventType {
        STORM_FRONT("Storm Front"),
        COLD_SNAP("Cold Snap"),
        HEAT_WAVE("Heat Wave"),
        DROUGHT("Drought"),
        BERRY_BLOOM("Berry Bloom"),
        PREDATOR_MIGRATION("Predator Migration"),
        TRADER_VISIT("Trader Visit"),
        METEOR_SHARD("Meteor Shard"),
        FOREST_FIRE("Forest Fire"),
        TOXIC_FOG("Toxic Fog"),
        ASHFALL("Ashfall"),
        METEOR_SHOWER("Meteor Shower"),
        PREDATOR_RAID("Predator Raid"),
        SCAVENGER_RAID("Scavenger Raid"),
        NPC_ILLNESS("Camp Illness");

        public final String displayName;

        EventType(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class ActiveEvent {
        public final EventType type;
        public float remaining;
        public float severity;

        public ActiveEvent(EventType type, float remaining, float severity) {
            this.type = type;
            this.remaining = remaining;
            this.severity = severity;
        }
    }

    public final List<ActiveEvent> active = new ArrayList<>();
    private final Random rng = new Random();
    private float cooldown = INITIAL_COOLDOWN;
    private float meteorTimer;
    public int totalEventsTriggered = 0;

    /** Seeded per world so a given world seed replays identically. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    @Override
    public void reset() {
        active.clear();
        cooldown = INITIAL_COOLDOWN;
        meteorTimer = 0f;
        totalEventsTriggered = 0;
    }

    public boolean isActive(EventType type) {
        for (ActiveEvent e : active) {
            if (e.type == type) {
                return true;
            }
        }
        return false;
    }

    public float severity(EventType type) {
        for (ActiveEvent e : active) {
            if (e.type == type) {
                return e.severity;
            }
        }
        return 0;
    }

    /**
     * Ages out finished events, keeps a meteor shower dropping shards, and — once
     * the inter-event cooldown has elapsed — rolls at most one new event.
     *
     * <h4>The roll ladder</h4>
     *
     * <p>A single {@code rng.nextFloat()} is walked against {@link
     * #DEFINITIONS}, an ordered table of {@link EventDefinition}s. Their bounds
     * come from the {@code *_ROLL} values in {@link EventConstants}, which are
     * <em>cumulative</em> upper bounds in ascending order rather than per-event
     * probabilities. Each event's actual share of the probability space is the
     * gap between its bound and the previous one, so a bound can only be
     * understood relative to its neighbours: moving one silently re-weights the
     * event above it as well. A roll above {@link
     * EventConstants#LONE_METEOR_ROLL} (0.43) means no event this tick, which is
     * the common case.
     *
     * <p>Season bias is applied to the <em>bound</em>, not to the roll, and only
     * on the rungs that name a season. Widening one rung's bound takes space
     * from the rung after it, because the walk stops at the first match: during
     * a cold season, {@code COLD_SNAP_ROLL + 0.08} eats into the heat wave's
     * window, which is the intent.
     *
     * <h4>Fall-through is load-bearing</h4>
     *
     * <p>A rung whose precondition fails does not abort the roll — the walk
     * continues and the <em>next</em> event gets a chance at the same roll.
     * Every later bound is higher than the one that just failed, so the next
     * rung's range test passes automatically and the roll effectively slides
     * upward until some event accepts it. This is why an already-active cold
     * snap turns a low roll into a drought rather than into nothing, and it is
     * the behaviour any further restructuring must preserve.
     * {@code EventRollLadderTest} records the resulting distribution precisely
     * so that a change to it cannot pass unnoticed.
     *
     * <p>The one deliberate exception is camp illness: its "a healthy camp NPC
     * exists" test lives in the rung's <em>effect</em> rather than its
     * precondition, so a camp with nobody left to fall ill consumes the roll and
     * no event fires. Moving that test into the precondition would let the roll
     * fall through and carve a lone meteor crater instead — a real gameplay
     * change.
     *
     * <h4>Rungs, in table order</h4>
     *
     * <table>
     *   <caption>Each rung's bound, extra precondition and side effects</caption>
     *   <tr><th>Bound</th><th>Event</th><th>Precondition beyond "not already
     *       active"</th><th>Side effects beyond starting the event</th></tr>
     *   <tr><td>0.06 (+0.08 cold)</td><td>Cold snap</td><td>no heat wave active
     *       </td><td>none; {@link #tempOffset()} reads the active event</td></tr>
     *   <tr><td>0.10 (+0.08 dry)</td><td>Heat wave</td><td>no cold snap active
     *       </td><td>none; drives {@link #tempOffset()} and {@link #thirstMul()}
     *       </td></tr>
     *   <tr><td>0.14 (+0.08 dry)</td><td>Drought</td><td>average soil moisture
     *       below {@link EventConstants#DROUGHT_MAX_MOISTURE}</td><td>none;
     *       drives {@link #growthMul()} and {@link #fireSpreadMul()}</td></tr>
     *   <tr><td>0.19 (+0.06 wet)</td><td>Berry bloom</td><td>average soil
     *       moisture above {@link EventConstants#BERRY_BLOOM_MIN_MOISTURE}</td>
     *       <td>none; drives {@link #berryMul()}</td></tr>
     *   <tr><td>0.23</td><td>Predator migration</td><td>none</td><td>spawns
     *       {@link EventConstants#MIGRATION_WOLVES} hungry wolves on a ring
     *       around the player and raises the wolf cap</td></tr>
     *   <tr><td>0.27</td><td>Trader visit</td><td>camp trust above
     *       {@link EventConstants#TRADER_MIN_TRUST} and the camp not hostile</td>
     *       <td>spawns a trader NPC with a leave timer, subject to the NPC
     *       budget</td></tr>
     *   <tr><td>0.30</td><td>Toxic fog</td><td>none</td><td>none here; the
     *       sickness and fog tint are read from {@link #toxicFog()}</td></tr>
     *   <tr><td>0.325</td><td>Ashfall</td><td>none</td><td>none here; drives
     *       {@link #growthMul()} and {@link #skyLightMul()}</td></tr>
     *   <tr><td>0.345</td><td>Meteor shower</td><td>none</td><td>arms
     *       {@code meteorTimer}, so subsequent slow ticks carve craters until the
     *       event expires</td></tr>
     *   <tr><td>0.365</td><td>Predator raid</td><td>the starter camp exists</td>
     *       <td>spawns {@link EventConstants#CAMP_RAID_WOLVES} wolves around the
     *       camp and raises the wolf cap</td></tr>
     *   <tr><td>0.385</td><td>Scavenger raid</td><td>the starter camp exists and
     *       its upgrade stage is at least 1</td><td>spawns 2-3 hostile raider
     *       NPCs around the camp, subject to the NPC budget</td></tr>
     *   <tr><td>0.405</td><td>Camp illness</td><td>see the exception above</td>
     *       <td>marks one healthy, non-trader, non-raider NPC sick; cleared by
     *       {@link #onCampCured()}</td></tr>
     *   <tr><td>0.43</td><td>Lone meteor</td><td>none</td><td>carves a crater
     *       with an ore core near the player and imposes the longer meteor
     *       cooldown; silently does nothing if the target chunk is unloaded</td>
     *       </tr>
     * </table>
     *
     * <p>Adding an event means adding one {@link EventDefinition} at the right
     * position and raising every bound above it, rather than splicing a branch
     * into the middle of a conditional chain. The table above still has to be
     * updated by hand, because JavaDoc cannot read the definitions.
     */
    @Override
    public void slowTick(Game g, float dt) {
        for (Iterator<ActiveEvent> it = active.iterator(); it.hasNext(); ) {
            ActiveEvent e = it.next();
            e.remaining -= dt;
            if (e.remaining <= 0) {
                it.remove();
                g.log(e.type.displayName + " has ended.");
            }
        }

        // Meteor showers keep dropping shards while active.
        if (isActive(EventType.METEOR_SHOWER)) {
            meteorTimer -= dt;
            if (meteorTimer <= 0) {
                meteorTimer = METEOR_INTERVAL_MIN + rng.nextInt(METEOR_INTERVAL_RANGE);
                meteorShard(g, false);
            }
        }

        cooldown -= dt;
        if (cooldown > 0) {
            return;
        }

        SeasonSystem.Season season = g.seasons.current(g.time);

        // Roll one potential new event, weighted by world state and season.
        // The roll walks DEFINITIONS in order; the first rung whose bound and
        // precondition both accept it fires, and a rejected rung hands the same
        // roll to the next one. See the method JavaDoc before reordering
        // anything: position in this table is part of each event's odds.
        float r = rng.nextFloat();
        for (EventDefinition def : DEFINITIONS) {
            if (def.accepts(this, g, r, season)) {
                def.effect().apply(this, g);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // The selection ladder, as data
    // ------------------------------------------------------------------

    /** The selection ladder, for tests that check the table's own invariants. */
    static List<EventDefinition> definitions() {
        return DEFINITIONS;
    }

    /** True when {@code type} is not already running. The common guard. */
    private static EventDefinition.Precondition idle(EventType type) {
        return (events, g) -> !events.isActive(type);
    }

    /**
     * The selection ladder. Ordered by ascending bound, which is also the order
     * a rejected roll walks. Each entry is the complete definition of one
     * event's odds, eligibility and consequences.
     */
    private static final List<EventDefinition> DEFINITIONS = List.of(

            // Cold snap and heat wave are mutually exclusive: the world cannot
            // be having both, and whichever is running blocks the other's rung.
            EventDefinition.seasonal(EventType.COLD_SNAP, COLD_SNAP_ROLL,
                    SeasonSystem.Season.COLD, COLD_SEASON_BIAS,
                    (events, g) -> !events.isActive(EventType.COLD_SNAP)
                            && !events.isActive(EventType.HEAT_WAVE),
                    (events, g) -> events.start(g, EventType.COLD_SNAP,
                            COLD_SNAP_SECONDS_MIN + events.rng.nextInt(COLD_SNAP_SECONDS_RANGE),
                            1f, "A cold snap grips the land. Stay warm!")),

            EventDefinition.seasonal(EventType.HEAT_WAVE, HEAT_WAVE_ROLL,
                    SeasonSystem.Season.DRY, DRY_SEASON_BIAS,
                    (events, g) -> !events.isActive(EventType.HEAT_WAVE)
                            && !events.isActive(EventType.COLD_SNAP),
                    (events, g) -> events.start(g, EventType.HEAT_WAVE,
                            HEAT_WAVE_SECONDS_MIN + events.rng.nextInt(HEAT_WAVE_SECONDS_RANGE),
                            1f, "A heat wave shimmers over Veylon. Thirst drains faster.")),

            // Drought and berry bloom read the same moisture average from
            // opposite sides, so at most one of them is ever eligible.
            EventDefinition.seasonal(EventType.DROUGHT, DROUGHT_ROLL,
                    SeasonSystem.Season.DRY, DRY_SEASON_BIAS,
                    (events, g) -> !events.isActive(EventType.DROUGHT)
                            && g.plants.lastAvgMoisture < DROUGHT_MAX_MOISTURE,
                    (events, g) -> events.start(g, EventType.DROUGHT,
                            DROUGHT_SECONDS_MIN + events.rng.nextInt(DROUGHT_SECONDS_RANGE),
                            1f, "Drought! Soil dries out and fire spreads easily.")),

            EventDefinition.seasonal(EventType.BERRY_BLOOM, BERRY_BLOOM_ROLL,
                    SeasonSystem.Season.WET, WET_SEASON_BIAS,
                    (events, g) -> !events.isActive(EventType.BERRY_BLOOM)
                            && g.plants.lastAvgMoisture > BERRY_BLOOM_MIN_MOISTURE,
                    (events, g) -> events.start(g, EventType.BERRY_BLOOM,
                            BERRY_BLOOM_SECONDS_MIN + events.rng.nextInt(BERRY_BLOOM_SECONDS_RANGE),
                            1f, "Berry bloom! Bushes regrow rapidly.")),

            EventDefinition.of(EventType.PREDATOR_MIGRATION, PREDATOR_MIGRATION_ROLL,
                    idle(EventType.PREDATOR_MIGRATION),
                    (events, g) -> {
                        events.start(g, EventType.PREDATOR_MIGRATION,
                                PREDATOR_MIGRATION_SECONDS, 1f,
                                "Predator migration - wolf howls echo in the distance...");
                        for (int i = 0; i < MIGRATION_WOLVES; i++) {
                            events.spawnWolfAtEdge(g, MIGRATION_SPAWN_DIST);
                        }
                    }),

            // A camp that distrusts you, or has turned on you, sends no trader.
            EventDefinition.of(EventType.TRADER_VISIT, TRADER_VISIT_ROLL,
                    (events, g) -> !events.isActive(EventType.TRADER_VISIT)
                            && g.faction.trust > TRADER_MIN_TRUST && !g.faction.hostile,
                    (events, g) -> {
                        events.start(g, EventType.TRADER_VISIT, TRADER_VISIT_SECONDS, 1f,
                                "A wandering trader is approaching - look for them nearby "
                                        + "(press F to trade).");
                        events.spawnTrader(g);
                    }),

            EventDefinition.of(EventType.TOXIC_FOG, TOXIC_FOG_ROLL,
                    idle(EventType.TOXIC_FOG),
                    (events, g) -> events.start(g, EventType.TOXIC_FOG,
                            TOXIC_FOG_SECONDS_MIN + events.rng.nextInt(TOXIC_FOG_SECONDS_RANGE),
                            1f, "TOXIC FOG rolls in! Stay indoors or risk sickness.")),

            EventDefinition.of(EventType.ASHFALL, ASHFALL_ROLL,
                    idle(EventType.ASHFALL),
                    (events, g) -> events.start(g, EventType.ASHFALL,
                            ASHFALL_SECONDS_MIN + events.rng.nextInt(ASHFALL_SECONDS_RANGE),
                            1f, "Ashfall darkens the sky. Plants choke under the grey dust.")),

            EventDefinition.of(EventType.METEOR_SHOWER, METEOR_SHOWER_ROLL,
                    idle(EventType.METEOR_SHOWER),
                    (events, g) -> {
                        events.start(g, EventType.METEOR_SHOWER,
                                METEOR_SHOWER_SECONDS_MIN
                                        + events.rng.nextInt(METEOR_SHOWER_SECONDS_RANGE),
                                1f, "METEOR SHOWER! Shards are streaking down around you.");
                        events.meteorTimer = METEOR_SHOWER_FIRST_SHARD;
                    }),

            // Both camp raids need a camp to raid.
            EventDefinition.of(EventType.PREDATOR_RAID, PREDATOR_RAID_ROLL,
                    (events, g) -> !events.isActive(EventType.PREDATOR_RAID)
                            && g.world.campPos != null,
                    (events, g) -> {
                        events.start(g, EventType.PREDATOR_RAID, PREDATOR_RAID_SECONDS, 1f,
                                "Wolves are raiding the NPC camp! Help defend it for trust.");
                        for (int i = 0; i < CAMP_RAID_WOLVES; i++) {
                            events.spawnWolfAtCamp(g);
                        }
                    }),

            // Scavengers only bother with a camp worth robbing.
            EventDefinition.of(EventType.SCAVENGER_RAID, SCAVENGER_RAID_ROLL,
                    (events, g) -> !events.isActive(EventType.SCAVENGER_RAID)
                            && g.world.campPos != null && g.faction.upgradeStage >= 1,
                    (events, g) -> {
                        events.start(g, EventType.SCAVENGER_RAID, SCAVENGER_RAID_SECONDS, 1f,
                                "Hostile scavengers are attacking the camp!");
                        int count = RAIDERS_MIN + events.rng.nextInt(RAIDERS_RANGE);
                        for (int i = 0; i < count; i++) {
                            events.spawnRaider(g, i);
                        }
                    }),

            // The victim search stays in the effect on purpose: a camp with
            // nobody left to fall ill consumes the roll silently instead of
            // falling through and dropping a meteor on the player. Hoisting it
            // into the precondition would be a real gameplay change.
            EventDefinition.of(EventType.NPC_ILLNESS, NPC_ILLNESS_ROLL,
                    idle(EventType.NPC_ILLNESS),
                    EventSystem::afflictCampWithIllness),

            // The bottom of the table: no "already active" guard, because a lone
            // meteor is an instant, and every roll this far up is spent.
            EventDefinition.of(EventType.METEOR_SHARD, LONE_METEOR_ROLL,
                    (events, g) -> true,
                    (events, g) -> events.meteorShard(g, true)));

    /** Makes one healthy camp resident sick, or does nothing if there is none. */
    private static void afflictCampWithIllness(EventSystem events, Game g) {
        Npc victim = null;
        for (Npc n : g.entities.npcs) {
            if (!n.isTrader && !n.raider && !n.sick && !n.dead) {
                victim = n;
                break;
            }
        }
        if (victim == null) {
            return;
        }
        victim.sick = true;
        victim.sickTimer = 0;
        events.start(g, EventType.NPC_ILLNESS, NPC_ILLNESS_SECONDS, 1f,
                victim.name + " has fallen ill. The camp needs MEDICINE (herbalist bench).");
    }

    private void start(Game g, EventType type, float duration, float severity, String message) {
        active.add(new ActiveEvent(type, duration, severity));
        totalEventsTriggered++;
        cooldown = COOLDOWN_MIN + rng.nextInt(COOLDOWN_RANGE);
        g.log("EVENT: " + message);
    }

    public void onStormStarted(Game g) {
        if (!isActive(EventType.STORM_FRONT)) {
            active.add(new ActiveEvent(EventType.STORM_FRONT, STORM_FRONT_SECONDS, 1f));
            totalEventsTriggered++;
            g.faction.alert = Math.min(100, g.faction.alert + STORM_ALERT_INCREASE);
        }
    }

    public void onLightningFire(Game g) {
        if (!isActive(EventType.FOREST_FIRE)) {
            active.add(new ActiveEvent(EventType.FOREST_FIRE, FOREST_FIRE_SECONDS, 1f));
            totalEventsTriggered++;
        }
    }

    /** Clears the camp-illness event once everyone is cured. */
    public void onCampCured() {
        active.removeIf(e -> e.type == EventType.NPC_ILLNESS);
    }

    private void meteorShard(Game g, boolean announce) {
        double ang = rng.nextDouble() * Math.PI * 2;
        double dist = METEOR_DIST_MIN + rng.nextDouble() * METEOR_DIST_RANGE;
        int x = (int) (g.player.pos.x + Math.cos(ang) * dist);
        int z = (int) (g.player.pos.z + Math.sin(ang) * dist);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            return;
        }
        int y = g.world.surfaceHeight(x, z);
        // Carve a small crater with a rich ore core.
        for (int dx = -CRATER_RADIUS; dx <= CRATER_RADIUS; dx++) {
            for (int dz = -CRATER_RADIUS; dz <= CRATER_RADIUS; dz++) {
                for (int dy = -CRATER_DEPTH; dy <= CRATER_HEIGHT; dy++) {
                    if (dx * dx + dz * dz + dy * dy <= CRATER_SHAPE_CUTOFF) {
                        g.world.setBlock(x + dx, y + dy, z + dz, BlockType.AIR, true);
                    }
                }
            }
        }
        g.world.setBlock(x, y - 1, z, BlockType.IRON_ORE, true);
        g.world.setBlock(x + 1, y - 1, z, BlockType.COPPER_ORE, true);
        g.world.setBlock(x, y - 1, z + 1, BlockType.IRON_ORE, true);
        g.world.setBlock(x - 1, y - 1, z, BlockType.COAL_ORE, true);
        g.world.setBlock(x, y - 2, z, BlockType.STONE, true);
        g.fire.ignite(g, x + 1, y, z + 1);
        g.particles.meteorImpact(x + 0.5f, y + 0.2f, z + 0.5f);
        g.audio.playThunder();
        if (announce) {
            active.add(new ActiveEvent(EventType.METEOR_SHARD, METEOR_SHARD_SECONDS, 1f));
            totalEventsTriggered++;
            cooldown = METEOR_COOLDOWN_MIN + rng.nextInt(METEOR_COOLDOWN_RANGE);
            g.log("EVENT: A meteor shard crashed near (" + x + ", " + z + ")! Rich ore at the impact site.");
        } else {
            g.log("A meteor shard slams down near (" + x + ", " + z + ")!");
        }
    }

    private void spawnWolfAtEdge(Game g, float dist) {
        double ang = rng.nextDouble() * Math.PI * 2;
        int x = (int) (g.player.pos.x + Math.cos(ang) * dist);
        int z = (int) (g.player.pos.z + Math.sin(ang) * dist);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            return;
        }
        int y = g.world.surfaceHeight(x, z) + 1;
        Creature wolf = g.entities.spawnCreature(g.world, Creature.CreatureType.WOLF, x + 0.5f, y, z + 0.5f);
        wolf.hunger = MIGRATION_WOLF_HUNGER;
        g.audio.playHowl(x + 0.5f, y, z + 0.5f);
    }

    private void spawnWolfAtCamp(Game g) {
        var camp = g.world.campPos;
        double ang = rng.nextDouble() * Math.PI * 2;
        int x = (int) (camp.x() + Math.cos(ang) * CAMP_WOLF_SPAWN_DIST);
        int z = (int) (camp.z() + Math.sin(ang) * CAMP_WOLF_SPAWN_DIST);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            return;
        }
        int y = g.world.surfaceHeight(x, z) + 1;
        Creature wolf = g.entities.spawnCreature(g.world, Creature.CreatureType.WOLF, x + 0.5f, y, z + 0.5f);
        wolf.hunger = CAMP_WOLF_HUNGER;
        g.audio.playHowl(x + 0.5f, y, z + 0.5f);
    }

    private void spawnRaider(Game g, int idx) {
        if (!g.settlementManager.canSpawnNpc(g, SettlementManager.NpcCategory.LEGACY)) {
            return;
        }
        var camp = g.world.campPos;
        double ang = rng.nextDouble() * Math.PI * 2;
        int x = (int) (camp.x() + Math.cos(ang) * RAIDER_SPAWN_DIST);
        int z = (int) (camp.z() + Math.sin(ang) * RAIDER_SPAWN_DIST);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            x = camp.x() + RAIDER_FALLBACK_OFFSET;
            z = camp.z();
        }
        int y = g.world.surfaceHeight(x, z) + 1;
        String[] names = {"Vex", "Hark", "Snare", "Rook"};
        Npc raider = g.entities.spawnNpc(g.world, names[idx % names.length] + " (Scavenger)",
                x + 0.5f, y, z + 0.5f);
        raider.raider = true;
        raider.leaveTimer = RAIDER_LEAVE_SECONDS;
        raider.maxHealth = RAIDER_HEALTH;
        raider.health = RAIDER_HEALTH;
    }

    private void spawnTrader(Game g) {
        if (!g.settlementManager.canSpawnNpc(g, SettlementManager.NpcCategory.LEGACY)) {
            return;
        }
        double ang = rng.nextDouble() * Math.PI * 2;
        int x = (int) (g.player.pos.x + Math.cos(ang) * TRADER_SPAWN_DIST);
        int z = (int) (g.player.pos.z + Math.sin(ang) * TRADER_SPAWN_DIST);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            x = (int) g.player.pos.x + TRADER_FALLBACK_OFFSET;
            z = (int) g.player.pos.z + TRADER_FALLBACK_OFFSET;
        }
        int y = g.world.surfaceHeight(x, z) + 1;
        Npc trader = g.entities.spawnNpc(g.world, "Trader Ressk", x + 0.5f, y, z + 0.5f);
        trader.isTrader = true;
        trader.leaveTimer = TRADER_LEAVE_SECONDS;
    }

    // ---- Modifier queries used by other systems ----

    public float tempOffset() {
        float t = 0;
        if (isActive(EventType.COLD_SNAP)) {
            t -= TEMP_SWING;
        }
        if (isActive(EventType.HEAT_WAVE)) {
            t += TEMP_SWING;
        }
        return t;
    }

    public float thirstMul() {
        return isActive(EventType.HEAT_WAVE) ? HEAT_WAVE_THIRST_MULT : 1f;
    }

    public float growthMul() {
        float m = 1f;
        if (isActive(EventType.DROUGHT)) {
            m *= DROUGHT_GROWTH_MULT;
        }
        if (isActive(EventType.ASHFALL)) {
            m *= ASHFALL_GROWTH_MULT;
        }
        return m;
    }

    public float berryMul() {
        return isActive(EventType.BERRY_BLOOM) ? BERRY_BLOOM_MULT : 1f;
    }

    public float fireSpreadMul() {
        return isActive(EventType.DROUGHT) ? DROUGHT_FIRE_SPREAD_MULT : 1f;
    }

    public int wolfCapBonus() {
        int bonus = 0;
        if (isActive(EventType.PREDATOR_MIGRATION)) {
            bonus += MIGRATION_WOLF_CAP_BONUS;
        }
        if (isActive(EventType.PREDATOR_RAID)) {
            bonus += RAID_WOLF_CAP_BONUS;
        }
        return bonus;
    }

    public boolean isDrought() {
        return isActive(EventType.DROUGHT);
    }

    public boolean forcesClear() {
        return isActive(EventType.DROUGHT) || isActive(EventType.HEAT_WAVE);
    }

    /** Extra darkening of the sky during ashfall. */
    public float skyLightMul() {
        return isActive(EventType.ASHFALL) ? ASHFALL_SKYLIGHT_MULT : 1f;
    }

    /** Green tint and shortened fog while toxic fog is active. */
    public boolean toxicFog() {
        return isActive(EventType.TOXIC_FOG);
    }

    public boolean ashfall() {
        return isActive(EventType.ASHFALL);
    }

    public String summary() {
        if (active.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (ActiveEvent e : active) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.type.displayName).append(" (").append((int) e.remaining).append("s)");
        }
        return sb.toString();
    }
}

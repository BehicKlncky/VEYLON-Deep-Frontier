package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.world.BlockType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * State-driven world events. Triggers consider weather, moisture, trust,
 * season and time instead of being purely random timers. Every event changes
 * real systems: temperature, growth, fire spread, wildlife, NPC behavior.
 */
public class EventSystem {

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
    private float cooldown = 60;
    private float meteorTimer;
    public int totalEventsTriggered = 0;

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
                meteorTimer = 15 + rng.nextInt(15);
                meteorShard(g, false);
            }
        }

        cooldown -= dt;
        if (cooldown > 0) {
            return;
        }

        SeasonSystem.Season season = g.seasons.current(g.time);

        // Roll one potential new event, weighted by world state and season.
        float r = rng.nextFloat();
        float coldBias = season == SeasonSystem.Season.COLD ? 0.08f : 0f;
        float dryBias = season == SeasonSystem.Season.DRY ? 0.08f : 0f;
        float wetBias = season == SeasonSystem.Season.WET ? 0.06f : 0f;

        if (r < 0.06f + coldBias && !isActive(EventType.COLD_SNAP) && !isActive(EventType.HEAT_WAVE)) {
            start(g, EventType.COLD_SNAP, 120 + rng.nextInt(120), 1f,
                    "A cold snap grips the land. Stay warm!");
        } else if (r < 0.10f + dryBias && !isActive(EventType.HEAT_WAVE) && !isActive(EventType.COLD_SNAP)) {
            start(g, EventType.HEAT_WAVE, 120 + rng.nextInt(120), 1f,
                    "A heat wave shimmers over Veylon. Thirst drains faster.");
        } else if (r < 0.14f + dryBias && !isActive(EventType.DROUGHT) && g.plants.lastAvgMoisture < 0.55f) {
            start(g, EventType.DROUGHT, 180 + rng.nextInt(120), 1f,
                    "Drought! Soil dries out and fire spreads easily.");
        } else if (r < 0.19f + wetBias && !isActive(EventType.BERRY_BLOOM) && g.plants.lastAvgMoisture > 0.45f) {
            start(g, EventType.BERRY_BLOOM, 150 + rng.nextInt(120), 1f,
                    "Berry bloom! Bushes regrow rapidly.");
        } else if (r < 0.23f && !isActive(EventType.PREDATOR_MIGRATION)) {
            start(g, EventType.PREDATOR_MIGRATION, 180, 1f,
                    "Predator migration - wolf howls echo in the distance...");
            for (int i = 0; i < 2; i++) {
                spawnWolfAtEdge(g, 55);
            }
        } else if (r < 0.27f && !isActive(EventType.TRADER_VISIT) && g.faction.trust > 20 && !g.faction.hostile) {
            start(g, EventType.TRADER_VISIT, 240, 1f,
                    "A wandering trader is approaching - look for them nearby (press F to trade).");
            spawnTrader(g);
        } else if (r < 0.30f && !isActive(EventType.TOXIC_FOG)) {
            start(g, EventType.TOXIC_FOG, 90 + rng.nextInt(80), 1f,
                    "TOXIC FOG rolls in! Stay indoors or risk sickness.");
        } else if (r < 0.325f && !isActive(EventType.ASHFALL)) {
            start(g, EventType.ASHFALL, 120 + rng.nextInt(100), 1f,
                    "Ashfall darkens the sky. Plants choke under the grey dust.");
        } else if (r < 0.345f && !isActive(EventType.METEOR_SHOWER)) {
            start(g, EventType.METEOR_SHOWER, 70 + rng.nextInt(40), 1f,
                    "METEOR SHOWER! Shards are streaking down around you.");
            meteorTimer = 5;
        } else if (r < 0.365f && !isActive(EventType.PREDATOR_RAID) && g.world.campPos != null) {
            start(g, EventType.PREDATOR_RAID, 90, 1f,
                    "Wolves are raiding the NPC camp! Help defend it for trust.");
            for (int i = 0; i < 3; i++) {
                spawnWolfAtCamp(g);
            }
        } else if (r < 0.385f && !isActive(EventType.SCAVENGER_RAID) && g.world.campPos != null
                && g.faction.upgradeStage >= 1) {
            start(g, EventType.SCAVENGER_RAID, 110, 1f,
                    "Hostile scavengers are attacking the camp!");
            int count = 2 + rng.nextInt(2);
            for (int i = 0; i < count; i++) {
                spawnRaider(g, i);
            }
        } else if (r < 0.405f && !isActive(EventType.NPC_ILLNESS)) {
            Npc victim = null;
            for (Npc n : g.entities.npcs) {
                if (!n.isTrader && !n.raider && !n.sick && !n.dead) {
                    victim = n;
                    break;
                }
            }
            if (victim != null) {
                victim.sick = true;
                victim.sickTimer = 0;
                start(g, EventType.NPC_ILLNESS, 600, 1f,
                        victim.name + " has fallen ill. The camp needs MEDICINE (herbalist bench).");
            }
        } else if (r < 0.43f) {
            meteorShard(g, true);
        }
    }

    private void start(Game g, EventType type, float duration, float severity, String message) {
        active.add(new ActiveEvent(type, duration, severity));
        totalEventsTriggered++;
        cooldown = 70 + rng.nextInt(60);
        g.log("EVENT: " + message);
    }

    public void onStormStarted(Game g) {
        if (!isActive(EventType.STORM_FRONT)) {
            active.add(new ActiveEvent(EventType.STORM_FRONT, 90, 1f));
            totalEventsTriggered++;
            g.faction.alert = Math.min(100, g.faction.alert + 15);
        }
    }

    public void onLightningFire(Game g) {
        if (!isActive(EventType.FOREST_FIRE)) {
            active.add(new ActiveEvent(EventType.FOREST_FIRE, 60, 1f));
            totalEventsTriggered++;
        }
    }

    /** Clears the camp-illness event once everyone is cured. */
    public void onCampCured() {
        active.removeIf(e -> e.type == EventType.NPC_ILLNESS);
    }

    private void meteorShard(Game g, boolean announce) {
        double ang = rng.nextDouble() * Math.PI * 2;
        double dist = 25 + rng.nextDouble() * 35;
        int x = (int) (g.player.pos.x + Math.cos(ang) * dist);
        int z = (int) (g.player.pos.z + Math.sin(ang) * dist);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            return;
        }
        int y = g.world.surfaceHeight(x, z);
        // Carve a small crater with a rich ore core.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (dx * dx + dz * dz + dy * dy <= 5) {
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
        g.audio.playThunder();
        if (announce) {
            active.add(new ActiveEvent(EventType.METEOR_SHARD, 30, 1f));
            totalEventsTriggered++;
            cooldown = 100 + rng.nextInt(80);
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
        wolf.hunger = 70;
        g.audio.playHowl(x + 0.5f, y, z + 0.5f);
    }

    private void spawnWolfAtCamp(Game g) {
        var camp = g.world.campPos;
        double ang = rng.nextDouble() * Math.PI * 2;
        int x = (int) (camp.x() + Math.cos(ang) * 18);
        int z = (int) (camp.z() + Math.sin(ang) * 18);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            return;
        }
        int y = g.world.surfaceHeight(x, z) + 1;
        Creature wolf = g.entities.spawnCreature(g.world, Creature.CreatureType.WOLF, x + 0.5f, y, z + 0.5f);
        wolf.hunger = 90;
        g.audio.playHowl(x + 0.5f, y, z + 0.5f);
    }

    private void spawnRaider(Game g, int idx) {
        var camp = g.world.campPos;
        double ang = rng.nextDouble() * Math.PI * 2;
        int x = (int) (camp.x() + Math.cos(ang) * 24);
        int z = (int) (camp.z() + Math.sin(ang) * 24);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            x = camp.x() + 20;
            z = camp.z();
        }
        int y = g.world.surfaceHeight(x, z) + 1;
        String[] names = {"Vex", "Hark", "Snare", "Rook"};
        Npc raider = g.entities.spawnNpc(g.world, names[idx % names.length] + " (Scavenger)",
                x + 0.5f, y, z + 0.5f);
        raider.raider = true;
        raider.leaveTimer = 90;
        raider.maxHealth = 28;
        raider.health = 28;
    }

    private void spawnTrader(Game g) {
        double ang = rng.nextDouble() * Math.PI * 2;
        int x = (int) (g.player.pos.x + Math.cos(ang) * 35);
        int z = (int) (g.player.pos.z + Math.sin(ang) * 35);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            x = (int) g.player.pos.x + 6;
            z = (int) g.player.pos.z + 6;
        }
        int y = g.world.surfaceHeight(x, z) + 1;
        Npc trader = g.entities.spawnNpc(g.world, "Trader Ressk", x + 0.5f, y, z + 0.5f);
        trader.isTrader = true;
        trader.leaveTimer = 240;
    }

    // ---- Modifier queries used by other systems ----

    public float tempOffset() {
        float t = 0;
        if (isActive(EventType.COLD_SNAP)) {
            t -= 12;
        }
        if (isActive(EventType.HEAT_WAVE)) {
            t += 12;
        }
        return t;
    }

    public float thirstMul() {
        return isActive(EventType.HEAT_WAVE) ? 1.6f : 1f;
    }

    public float growthMul() {
        float m = 1f;
        if (isActive(EventType.DROUGHT)) {
            m *= 0.3f;
        }
        if (isActive(EventType.ASHFALL)) {
            m *= 0.5f;
        }
        return m;
    }

    public float berryMul() {
        return isActive(EventType.BERRY_BLOOM) ? 4f : 1f;
    }

    public float fireSpreadMul() {
        return isActive(EventType.DROUGHT) ? 1.9f : 1f;
    }

    public int wolfCapBonus() {
        int bonus = 0;
        if (isActive(EventType.PREDATOR_MIGRATION)) {
            bonus += 4;
        }
        if (isActive(EventType.PREDATOR_RAID)) {
            bonus += 3;
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
        return isActive(EventType.ASHFALL) ? 0.7f : 1f;
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

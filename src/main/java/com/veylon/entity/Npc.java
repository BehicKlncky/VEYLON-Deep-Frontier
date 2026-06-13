package com.veylon.entity;

import com.veylon.ai.FactionSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import org.joml.Vector3f;

public class Npc extends Entity {

    public enum NpcState {
        IDLE, GATHER_FOOD, GATHER_WOOD, HUNT, HEAL, BUILD,
        WARM_BY_FIRE, SLEEP, GUARD, FLEE, ATTACK, TRADE, RAID
    }

    public final String name;
    public NpcState state = NpcState.IDLE;
    /** 0..100. */
    public float mood = 65;
    public float hunger = 20;
    public boolean isTrader;
    /** Hostile scavenger attacking the camp during raids. */
    public boolean raider;
    /** Sick NPCs work slowly and need medicine (NPC illness event). */
    public boolean sick;
    public float sickTimer;
    /** Null for wandering traders and raiders. */
    public FactionSystem faction;
    /** Index within the camp; used to assign jobs (0 guard, 1 hunter, 2 gatherer, 3 medic). */
    public int campIndex;

    public final Vector3f target = new Vector3f();
    public boolean hasTarget;
    public Vec3i targetBlock;
    public float decideTimer;
    public float workTimer;
    public float attackCooldown;
    /** While > 0 the NPC stands still (talking/trading with the player). */
    public float interactFreeze;
    /** Seconds before a trader or raider leaves. */
    public float leaveTimer;
    public Entity combatTarget;
    /** Animation phase for walking bob. */
    public float bobPhase;

    public Npc(World world, String name) {
        super(world);
        this.name = name;
        width = 0.55f;
        height = 1.75f;
        maxHealth = 35;
        health = 35;
    }

    public boolean hostileToPlayer() {
        return raider || (faction != null && faction.hostile);
    }

    public String jobName() {
        if (isTrader) {
            return "Trader";
        }
        if (raider) {
            return "Scavenger";
        }
        return switch (campIndex % 4) {
            case 0 -> "Guard";
            case 1 -> "Hunter";
            case 2 -> "Gatherer";
            default -> "Medic";
        };
    }
}

package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.UrbanContainers;

import java.util.List;

/**
 * The bounded needs of a survivor site's people (urban milestone 16), simulated in whole steps of
 * game time whether they are on screen or not, so activation, dormancy, sleep and a save never
 * change what happens to them.
 *
 * <p>Every hour each person present grows hungrier (empty after {@value #HUNGRY_HOURS} hours) and
 * thirstier ({@value #THIRSTY_HOURS} hours). At {@value #EAT_AT} they eat one ration or drink one
 * waterskin from the site's stash: the real containers of the plan, whose contents the player can
 * search, take, refill or destroy. A drunk waterskin is left empty in the stash. Someone who is
 * starving or parched loses {@value #STARVING} health an hour for each need at its limit and dies
 * when it is gone; someone fed and watered recovers {@value #RECOVERY} an hour. People on the road
 * grow hungry but neither eat nor starve. A camp forages: every {@value #FORAGE_HOURS} hours, with
 * two or more people present, it adds a ration for every three people and refills a waterskin for
 * every two, up to {@value #FOOD_CAP} rations and {@value #WATER_CAP} waterskins. A hidden refuge
 * cannot go out, so what it has is all it has. Fear and alarm fade by the hour; trust that fell
 * drifts back by {@value #TRUST_DRIFT_PER_DAY} a day, never above a betrayal's cap.
 *
 * <p>Simulation starts when the people are first met ({@link UrbanSite#simMinute}), so nobody
 * starves before the player could know they exist. The director steps every site hour by hour in
 * order of game minute, at most {@value #MAX_STEPS_PER_TICK} steps per slow tick in all, and only
 * while a site's stash columns are loaded; a lagging site catches up once its chunk exists again,
 * with the same result, because no step reads anything but the site, its guests and its stash.
 */
final class UrbanSiteNeeds {
    static final int STEP_MINUTES = 60;
    static final int MAX_STEPS_PER_TICK = 1_024;
    static final int HUNGRY_HOURS = 36, THIRSTY_HOURS = 24;
    static final float HUNGER_PER_STEP = 100f / HUNGRY_HOURS, THIRST_PER_STEP = 100f / THIRSTY_HOURS;
    static final float EAT_AT = 50f, RELIEF = 50f, STARVING = 1.5f, RECOVERY = 1f;
    static final int FORAGE_HOURS = 6, FOOD_CAP = 16, WATER_CAP = 12;
    static final float FEAR_DECAY = 10f, ALERT_DECAY = 25f, TRUST_DRIFT_PER_DAY = 2f;

    private UrbanSiteNeeds() {
    }

    /** True when every stash container of the site's home lies in a generated column. */
    static boolean stashLoaded(Game g, UrbanSite site) {
        for (Vec3i p : stashOf(g, site)) {
            if (!g.world.isColumnLoaded(p.x(), p.z())) { return false; }
        }
        return true;
    }

    /**
     * One hour of a site's life, with the guests who moved into it: a guest is fed by its camp
     * only for an hour its own clock has not reached, so nobody eats twice and nobody is skipped
     * whichever order the director steps them in.
     */
    static void step(Game g, UrbanSite site, List<UrbanSite> guests) {
        site.simMinute += STEP_MINUTES;
        long hour = Math.round(site.simMinute / STEP_MINUTES);
        boolean onRoad = !site.atHome();
        int present = feed(g, site, site, onRoad);
        for (UrbanSite guest : guests) {
            if (guest.simMinute >= site.simMinute) { continue; }
            guest.simMinute = site.simMinute;
            present += feed(g, guest, site, false);
            fade(guest);
        }
        if (site.camp() && present >= 2 && hour % FORAGE_HOURS == 0) {
            forage(g, site, (present + 2) / 3, (present + 1) / 2);
        }
        fade(site);
    }

    /** One hour of needs for a site's living people, fed from {@code stash}'s storage; returns how many were present. */
    private static int feed(Game g, UrbanSite people, UrbanSite stash, boolean onRoad) {
        int present = 0;
        for (Settlement.Resident base : people.residents) {
            UrbanSite.Resident r = (UrbanSite.Resident) base;
            if (!r.alive) { continue; }
            present += onRoad ? 0 : 1;
            r.hunger = Math.min(100f, r.hunger + HUNGER_PER_STEP);
            r.thirst = Math.min(100f, r.thirst + THIRST_PER_STEP);
            if (onRoad) { continue; }
            if (r.hunger >= EAT_AT && take(g, stash, false)) { r.hunger = Math.max(0f, r.hunger - RELIEF); }
            if (r.thirst >= EAT_AT && take(g, stash, true)) { r.thirst = Math.max(0f, r.thirst - RELIEF); }
            int limits = (r.hunger >= 100f ? 1 : 0) + (r.thirst >= 100f ? 1 : 0);
            Npc live = r.live != null && !r.live.dead ? r.live : null;
            float health = live != null ? live.health : r.health;
            float max = r.archetype.maxHealth;
            if (limits > 0) {
                health -= STARVING * limits;
            } else if (r.hunger < EAT_AT && r.thirst < EAT_AT) {
                health = Math.min(max, health + RECOVERY);
            }
            if (live != null) {
                live.hunger = r.hunger;
                live.health = Math.max(0f, health);
            }
            r.health = Math.max(0f, health);
            if (health <= 0f) {
                starve(g, people, r, live);
            }
        }
        return present;
    }

    /** A death from hunger or thirst: a tombstone, the body down where it stood, a line when they were known. */
    private static void starve(Game g, UrbanSite site, UrbanSite.Resident r, Npc live) {
        r.alive = false;
        r.health = 0f;
        if (live != null) {
            live.health = 0f;
            live.dead = true;
            live.lastHitByPlayer = false;
        }
        site.fear = Math.min(100f, site.fear + 30f);
        // A body on screen falls and is reported where it falls; one off screen is news when known.
        if (live == null && site.discovered) {
            g.log(r.name + " of the " + site.plan.kind().label.toLowerCase(java.util.Locale.ROOT) + " has died of "
                    + (r.thirst >= 100f ? "thirst." : "hunger."));
        }
    }

    private static void fade(UrbanSite site) {
        site.fear = Math.max(0f, site.fear - FEAR_DECAY);
        site.alertLevel = Math.max(0f, site.alertLevel - ALERT_DECAY);
        if (site.localReputation < 0f) {
            site.localReputation = Math.min(0f, site.localReputation + TRUST_DRIFT_PER_DAY / 24f);
        }
        site.settleAlignment();
    }

    // ---- the stash -------------------------------------------------------------------------

    /** The containers a site's people eat from: their own, or their camp's once they moved in. */
    static List<Vec3i> stashOf(Game g, UrbanSite site) {
        return site.home(g.world.urban).stash();
    }

    /**
     * The live inventory of one of a site's stash containers, created empty when {@code create};
     * null when the column is unloaded, the furniture is gone or replaced, or it is no longer the
     * site's own storage.
     */
    static Inventory inventory(Game g, UrbanSite owner, Vec3i pos, boolean create) {
        if (!usable(g, owner, pos)) { return null; }
        Inventory inventory = g.world.crateContents.get(pos);
        if (inventory == null && create) {
            inventory = new Inventory(UrbanContainers.SLOTS);
            g.world.crateContents.put(pos, inventory);
        }
        return inventory;
    }

    /**
     * Whether a container is still the site's own storage: loaded, the original furniture, not
     * destroyed or moved, and owned by the site. Its first use rolls its planned contents.
     */
    static boolean usable(Game g, UrbanSite owner, Vec3i pos) {
        if (!g.world.isColumnLoaded(pos.x(), pos.z())) { return false; }
        UrbanContainers.Entry entry = g.world.urban.containers.touch(g.world, pos);
        return entry != null && !entry.state.retired() && entry.owner == UrbanContainers.Owner.SURVIVOR
                && entry.ownerId == owner.plan.id();
    }

    static void compact(Game g, Vec3i pos) {
        UrbanContainers.Entry entry = g.world.urban.containers.at(pos);
        if (entry != null) { g.world.urban.containers.compact(g.world, entry); }
    }

    /** A ration anyone would eat: anything edible but spoiled meat. */
    static boolean ration(ItemType type) {
        return type.isEdible() && type != ItemType.SPOILED_MEAT;
    }

    /** Takes one ration or clean waterskin from the home stash; a drunk skin stays behind empty. */
    static boolean take(Game g, UrbanSite site, boolean water) {
        UrbanSite owner = stashOwner(g, site);
        for (Vec3i pos : owner.plan.stash()) {
            Inventory inv = inventory(g, owner, pos, false);
            if (inv == null) { continue; }
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.get(i);
                if (s == null || (water ? s.type != ItemType.WATERSKIN_CLEAN : !ration(s.type))) { continue; }
                inv.shrink(i, 1);
                if (water) { inv.add(ItemType.WATERSKIN_EMPTY, 1); }
                compact(g, pos);
                return true;
            }
        }
        return false;
    }

    /** Rations and clean waterskins in the home stash. */
    static int[] supplies(Game g, UrbanSite site) {
        UrbanSite owner = stashOwner(g, site);
        int food = 0, water = 0;
        for (Vec3i pos : owner.plan.stash()) {
            Inventory inv = inventory(g, owner, pos, false);
            if (inv == null) { continue; }
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.get(i);
                if (s == null) { continue; }
                if (s.type == ItemType.WATERSKIN_CLEAN) { water += s.count; }
                else if (ration(s.type)) { food += s.count; }
            }
        }
        return new int[]{food, water};
    }

    /** A camp's foraging: rations, then refilled skins (fresh ones only when no empty is left). */
    private static void forage(Game g, UrbanSite camp, int rations, int skins) {
        int[] have = supplies(g, camp);
        rations = Math.min(rations, FOOD_CAP - have[0]);
        skins = Math.min(skins, WATER_CAP - have[1]);
        for (Vec3i pos : camp.plan.stash()) {
            if (rations <= 0 && skins <= 0) { break; }
            Inventory inv = inventory(g, camp, pos, true);
            if (inv == null) { continue; }
            while (skins > 0 && inv.remove(ItemType.WATERSKIN_EMPTY, 1) == 1) {
                int left = inv.add(ItemType.WATERSKIN_CLEAN, 1);
                if (left > 0) { inv.add(ItemType.WATERSKIN_EMPTY, 1); break; }
                skins--;
            }
            if (rations > 0) { rations = inv.add(ItemType.DRIED_BERRY, rations); }
            if (skins > 0) { skins = inv.add(ItemType.WATERSKIN_CLEAN, skins); }
            compact(g, pos);
        }
    }

    /** Puts a stack into the home stash, as much as fits; returns how many went in. */
    static int give(Game g, UrbanSite site, ItemStack stack) {
        UrbanSite owner = stashOwner(g, site);
        int before = stack.count;
        for (Vec3i pos : owner.plan.stash()) {
            if (stack.count <= 0) { break; }
            Inventory inv = inventory(g, owner, pos, true);
            if (inv == null) { continue; }
            stack.count = inv.addStack(stack);
            compact(g, pos);
        }
        return before - stack.count;
    }

    /** The site whose storage feeds this one: its camp once it moved in, else itself. */
    static UrbanSite stashOwner(Game g, UrbanSite site) {
        if (site.relocation == UrbanSite.Relocation.ARRIVED
                && g.world.settlements.get(site.relocatedTo) instanceof UrbanSite camp) {
            return camp;
        }
        return site;
    }
}

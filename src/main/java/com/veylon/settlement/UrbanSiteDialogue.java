package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;

/**
 * What the player can do in conversation with the people of a survivor camp or hidden refuge
 * (urban milestone 16). Each command is the gameplay seam the conversation screen's keys and the
 * tests share; it returns the line the person answers with. Lines are fixed text chosen from the
 * site's state (kind, fear, trust, what is left in the stash), never generated.
 */
public final class UrbanSiteDialogue {
    /** Supplies handed over in one "give food and water". */
    public static final int AID_ITEMS = 6;
    /** Health a dressing or medicine restores to a hurt resident. */
    public static final float TENDED = 15f, TEND_TRUST = 8f;

    private UrbanSiteDialogue() {
    }

    /** A line about how they are; talk alone never changes trust. */
    public static String talk(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (s == null || n.dead) { return "..."; }
        g.settlementManager.urbanSites.onResidentSeen(g, s);
        int[] stash = UrbanSiteNeeds.supplies(g, s);
        int people = Math.max(1, s.present());
        int pick = (int) Math.floorMod((long) (g.time.totalMinutes / 3) + n.residentIndex, 2L);
        if (s.refuge()) {
            if (s.frightened()) { return "Please... we have nothing. Just go."; }
            if (stash[1] < people) { return pick == 0 ? "We're down to the last of the water." : "Is there any water out there? Any at all?"; }
            if (stash[0] < people) { return pick == 0 ? "We haven't eaten since yesterday." : "We ration what's on the shelf. It won't last."; }
            if (s.localReputation >= UrbanSiteRelocation.TRUST_TO_MOVE) {
                return pick == 0 ? "You came back. We were afraid you wouldn't." : "If there were somewhere safe, we would go.";
            }
            return pick == 0 ? "How did you find us? Keep your voice down." : "They walk right over us at night. We can hear them.";
        }
        if (s.frightened()) { return "Not now. Keep your voice down and your hands empty."; }
        if (stash[1] < people) { return pick == 0 ? "We're short on water. Bring any you can spare." : "The collector's dry. We need clean water."; }
        if (stash[0] < people) { return "The shelves are nearly bare. Food would go a long way."; }
        if (s.grantsAccess()) {
            return pick == 0 ? "Good to see you. What we have is yours to use." : "Stay a while. The street's quieter from in here.";
        }
        if (s.localReputation < 10f) {
            return pick == 0 ? "We keep the street door locked. Come round the back if you mean no harm."
                    : "Supplies are tight. Bring water and we'll talk.";
        }
        return pick == 0 ? "We hold this floor. Infected come down the street most nights."
                : "If you find clean water, we'll trade for it.";
    }

    /**
     * Hands over up to {@value #AID_ITEMS} rations, clean waterskins and dressings or medicine,
     * into the site's stash as far as it has room: aid, after anything owed.
     */
    public static String giveSupplies(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (s == null || n.dead) { return "..."; }
        if (s.hostile()) { return "We want nothing from you."; }
        Inventory inv = g.player.inventory;
        int rations = 0, medicine = 0, moved = 0;
        boolean full = false;
        for (int i = 0; i < inv.size() && moved < AID_ITEMS; i++) {
            ItemStack stack = inv.get(i);
            if (stack == null) { continue; }
            boolean food = UrbanSiteNeeds.ration(stack.type) || stack.type == ItemType.WATERSKIN_CLEAN;
            boolean med = healing(stack.type);
            if (!food && !med) { continue; }
            ItemStack offer = stack.copy();
            offer.count = Math.min(stack.count, AID_ITEMS - moved);
            int given = UrbanSiteNeeds.give(g, s, offer);
            full |= given < Math.min(stack.count, AID_ITEMS - moved);
            if (given <= 0) { continue; }
            inv.shrink(i, given);
            moved += given;
            if (food) { rations += given; } else { medicine += given; }
        }
        if (moved == 0) {
            return full ? "We've no room left to keep anything." : "You have no food, water or dressings to give.";
        }
        g.audio.playClick();
        g.log("You give " + moved + " supplies to the " + s.plan.kind().label.toLowerCase(java.util.Locale.ROOT) + ".");
        UrbanSiteRelations.aided(g, s, rations, medicine);
        return s.refuge() ? "Thank you. Thank you. This buys us days." : "That helps. We won't forget it.";
    }

    /**
     * Gives the held item to this person: a dressing or medicine tends a hurt one; food or water
     * goes to the stash; anything else is a small kindness put on the shelf.
     */
    public static String giveHeld(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (s == null || n.dead) { return "..."; }
        if (s.hostile()) { return "We want nothing from you."; }
        ItemStack held = g.player.selected();
        if (held == null) { return "You hold out an empty hand."; }
        ItemType type = held.type;
        if (healing(type) && n.health < n.maxHealth) {
            g.player.inventory.shrink(g.player.hotbarSel, 1);
            n.health = Math.min(n.maxHealth, n.health + TENDED);
            UrbanSite.Resident r = s.resident(n.residentIndex);
            r.health = n.health;
            UrbanSiteRelations.frighten(s, -10f);
            UrbanSiteRelations.trust(g, s, TEND_TRUST, "You tend to " + n.name + "'s wounds.");
            return "That's... better. Thank you.";
        }
        ItemStack one = held.copy();
        one.count = 1;
        if (UrbanSiteNeeds.give(g, s, one) != 1) { return "We've no room left to keep it."; }
        g.player.inventory.shrink(g.player.hotbarSel, 1);
        boolean food = UrbanSiteNeeds.ration(type) || type == ItemType.WATERSKIN_CLEAN;
        if (food || healing(type)) {
            UrbanSiteRelations.aided(g, s, food ? 1 : 0, food ? 0 : 1);
        } else if (s.localReputation < 10f) {
            UrbanSiteRelations.trust(g, s, 1f, null);
        }
        return "We'll put it to use.";
    }

    // ---- trade ---------------------------------------------------------------------------

    /** A camp trader's standing offer: the player gives, the camp gives back from its stores. */
    public record Offer(ItemType give, int giveCount, ItemType get, int getCount) {
        public String describe() {
            return giveCount + " " + give.displayName + " for " + getCount + " " + get.displayName;
        }
    }

    /** What a camp wants for what its stash holds: only goods a camp's stash is stocked with are sold. */
    private static final Offer[] OFFERS = {
        new Offer(ItemType.SCRAP, 3, ItemType.WATERSKIN_CLEAN, 1),
        new Offer(ItemType.PLANK, 2, ItemType.DRIED_BERRY, 2),
        new Offer(ItemType.FIBER, 4, ItemType.BANDAGE, 1),
        new Offer(ItemType.TORCH, 2, ItemType.DRIED_BERRY, 2),
        new Offer(ItemType.MEDICINE, 1, ItemType.WATERSKIN_CLEAN, 2),
        new Offer(ItemType.SCRAP, 4, ItemType.BANDAGE, 1),
    };

    /**
     * A camp trader's two standing offers, a pure pick from the site id; a friend gives one less.
     * Empty for anyone but a camp's trader.
     */
    public static java.util.List<Offer> offers(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (s == null || !s.camp() || s.resident(n.residentIndex).role != com.veylon.world.urban.SurvivorSites.Role.TRADER) {
            return java.util.List.of();
        }
        int first = com.veylon.world.urban.UrbanHash.range(0, OFFERS.length - 1, g.world.seed,
                com.veylon.world.urban.UrbanRevision.LAYOUT_SURVIVORS, com.veylon.world.urban.UrbanSalts.SITE_TRADE, s.id, 0, 0);
        int second = (first + 1 + com.veylon.world.urban.UrbanHash.range(0, OFFERS.length - 2, g.world.seed,
                com.veylon.world.urban.UrbanRevision.LAYOUT_SURVIVORS, com.veylon.world.urban.UrbanSalts.SITE_TRADE, s.id, 1, 0))
                % OFFERS.length;
        java.util.List<Offer> out = new java.util.ArrayList<>(2);
        for (int index : new int[]{first, second}) {
            Offer o = OFFERS[index];
            out.add(s.grantsAccess() ? new Offer(o.give(), Math.max(1, o.giveCount() - 1), o.get(), o.getCount()) : o);
        }
        return out;
    }

    /**
     * One trade with a camp's trader: the player's goods go into the camp's stash, the camp's go
     * to the player from its own ground-floor storage, all or nothing. Finite by construction:
     * when the camp is out of something, it is out.
     */
    public static String trade(Game g, Npc n, int index) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        java.util.List<Offer> offers = offers(g, n);
        if (s == null || n.dead || index < 0 || index >= offers.size()) { return "I have nothing to trade."; }
        if (s.hostile()) { return "We want nothing from you."; }
        if (s.frightened()) { return "Not now. Not with things as they are."; }
        Offer o = offers.get(index);
        Inventory inv = g.player.inventory;
        if (!inv.has(o.give(), o.giveCount())) { return "Bring " + o.giveCount() + " " + o.give().displayName + " and we'll talk."; }
        java.util.List<com.veylon.util.Vec3i> stores = storesOf(g, s);
        int have = 0;
        for (com.veylon.util.Vec3i p : stores) {
            Inventory store = UrbanSiteNeeds.inventory(g, s, p, false);
            if (store != null) { have += store.count(o.get()); }
        }
        if (have < o.getCount()) { return "We're out of " + o.get().displayName + "."; }
        // All or nothing: both sides must have room before anything moves.
        if (fits(inv, o.get(), o.getCount()) < o.getCount()) { return "You can't carry it."; }
        if (stashRoom(g, s, o.give(), o.giveCount()) < o.giveCount()) { return "We've no room to keep that."; }
        inv.remove(o.give(), o.giveCount());
        UrbanSiteNeeds.give(g, s, new ItemStack(o.give(), o.giveCount()));
        int owed = o.getCount();
        for (com.veylon.util.Vec3i p : stores) {
            Inventory store = UrbanSiteNeeds.inventory(g, s, p, false);
            if (store != null && owed > 0) {
                owed -= store.remove(o.get(), owed);
                UrbanSiteNeeds.compact(g, p);
            }
        }
        inv.add(o.get(), o.getCount());
        g.audio.playClick();
        g.log("Traded " + o.describe() + ".");
        UrbanSiteRelations.trust(g, s, 1f, null);
        return "A fair trade.";
    }

    /** How many of a fresh stack would fit in an inventory, tried on a copy. */
    private static int fits(Inventory inv, ItemType type, int count) {
        Inventory copy = new Inventory(inv.size());
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            copy.set(i, s == null ? null : s.copy());
        }
        return count - copy.add(type, count);
    }

    /** How many of a stack the site's stash could take, tried on copies in fill order. */
    private static int stashRoom(Game g, UrbanSite s, ItemType type, int count) {
        int left = count;
        for (com.veylon.util.Vec3i p : UrbanSiteNeeds.stashOf(g, s)) {
            if (left <= 0) { break; }
            if (!UrbanSiteNeeds.usable(g, UrbanSiteNeeds.stashOwner(g, s), p)) { continue; }
            Inventory store = g.world.crateContents.get(p);
            left -= fits(store == null ? new Inventory(com.veylon.world.urban.UrbanContainers.SLOTS) : store, type, left);
        }
        return count - left;
    }

    /** A camp's ground-floor storage, which its trader sells from. */
    static java.util.List<com.veylon.util.Vec3i> storesOf(Game g, UrbanSite s) {
        java.util.List<com.veylon.util.Vec3i> out = new java.util.ArrayList<>(s.plan.stash());
        com.veylon.world.urban.BuildingPlan b = g.world.urban.buildingAt(s.plan.anchor().x(), s.plan.anchor().z());
        if (b == null) { return out; }
        for (com.veylon.world.urban.BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() == com.veylon.world.urban.BuildingPlan.FixtureKind.CONTAINER
                    && com.veylon.world.urban.UrbanIds.floorIndex(f.id()) == s.plan.floor() && !out.contains(f.position())) {
                out.add(f.position());
            }
        }
        return out;
    }

    // ---- a safe move, a threat, the medic -------------------------------------------------

    /** The camp hidden civilians would go to: a known, friendly enough camp with room, nearest first. */
    public static UrbanSite campFor(Game g, UrbanSite refuge) {
        UrbanSite best = null;
        double bestD = Double.MAX_VALUE;
        for (UrbanSite c : UrbanSiteDirector.sites(g.world)) {
            if (!c.camp() || !c.discovered) { continue; }
            double d = c.plan.anchor().distSq(refuge.plan.anchor().x(), refuge.plan.anchor().y(), refuge.plan.anchor().z());
            boolean ok = UrbanSiteRelocation.refusal(g, refuge, c) == null;
            boolean bestOk = best != null && UrbanSiteRelocation.refusal(g, refuge, best) == null;
            if (best == null || ok && !bestOk || ok == bestOk && d < bestD) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    /** Tells hidden civilians about a camp the player knows: they go if they trust the player and it would have them. */
    public static String guide(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (s == null || n.dead || !s.refuge()) { return "..."; }
        UrbanSite camp = campFor(g, s);
        String refusal = UrbanSiteRelocation.refusal(g, s, camp);
        if (refusal != null) { return refusal; }
        UrbanSiteRelocation.guide(g, s, camp);
        return "Then we'll go. Not now, not while anyone could follow. Thank you.";
    }

    /** Items a threatened refuge hands over at most. */
    public static final int DEMANDED = 6;
    /** Trust a demand costs. */
    public static final float THREAT = 40f;

    /**
     * Demands their supplies. Hidden civilians cannot refuse: they hand over food, water and
     * medicine, terrified, and never trust the player the same way again if they did before. A
     * camp refuses and turns on the player.
     */
    public static String demand(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (s == null || n.dead) { return "..."; }
        if (s.camp()) {
            UrbanSiteRelations.harm(g, s, THREAT, "You threatened the camp.");
            if (!s.hostile()) {
                s.localReputation = Math.min(s.localReputation, UrbanSite.HOSTILE_TRUST);
                UrbanSiteRelations.trust(g, s, 0f, null);
            }
            UrbanSiteRelations.frighten(s, 20f);
            UrbanSiteRelations.alarm(g, s, 100f);
            return "You'll have to come through us.";
        }
        int handed = 0;
        for (com.veylon.util.Vec3i p : s.plan.stash()) {
            Inventory store = UrbanSiteNeeds.inventory(g, s, p, false);
            if (store == null) { continue; }
            for (int i = 0; i < store.size() && handed < DEMANDED; i++) {
                ItemStack st = store.get(i);
                if (st == null || !(UrbanSiteNeeds.ration(st.type) || st.type == ItemType.WATERSKIN_CLEAN || healing(st.type))) {
                    continue;
                }
                ItemStack part = st.copy();
                part.count = Math.min(st.count, DEMANDED - handed);
                int moved = part.count - g.player.inventory.addStack(part);
                if (moved <= 0) { continue; }
                store.shrink(i, moved);
                handed += moved;
            }
            UrbanSiteNeeds.compact(g, p);
        }
        s.owed = Math.min(UrbanSiteRelations.MAX_OWED, s.owed + handed);
        UrbanSiteRelations.harm(g, s, THREAT, handed > 0 ? "You frightened them into handing over " + handed + " supplies."
                : "You threatened them, and they had nothing left to give.");
        UrbanSiteRelations.frighten(s, 100f);
        return handed > 0 ? "Take it! Just take it and go..." : "We have nothing! Please!";
    }

    /** Health a camp medic restores to a friend, once a game day. */
    public static final float TREATMENT = 25f;

    /** A friendly camp's medic sees to the player's wounds, once a game day. */
    public static String treat(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (s == null || n.dead || !s.camp() || s.resident(n.residentIndex).role != com.veylon.world.urban.SurvivorSites.Role.MEDIC) {
            return "I'm no medic.";
        }
        if (!s.grantsAccess()) { return "We save our dressings for our own."; }
        int today = g.time.day();
        if (s.lastTreatmentDay == today) { return "I've done what I can for today. Come back tomorrow."; }
        if (g.player.health >= g.player.maxHealth) { return "You're in one piece. Keep it that way."; }
        s.lastTreatmentDay = today;
        g.player.health = Math.min(g.player.maxHealth, g.player.health + TREATMENT);
        g.log(n.name + " cleans and dresses your wounds.");
        return "Hold still. There - that will hold.";
    }

    static boolean healing(ItemType type) {
        return type == ItemType.MEDICINE || type == ItemType.BANDAGE || type == ItemType.ANTISEPTIC
                || type == ItemType.HERBAL_POULTICE;
    }

    /** The one-line state the conversation screen shows under a resident's name. */
    public static String status(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (s == null) { return ""; }
        UrbanSite.Resident r = s.resident(n.residentIndex);
        return "Trust " + Math.round(s.localReputation) + "   Fear " + Math.round(s.fear) + "   Hunger "
                + Math.round(r.hunger) + "   Thirst " + Math.round(r.thirst) + "   HP " + Math.round(n.health);
    }
}

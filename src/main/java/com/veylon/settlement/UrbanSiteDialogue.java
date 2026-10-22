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

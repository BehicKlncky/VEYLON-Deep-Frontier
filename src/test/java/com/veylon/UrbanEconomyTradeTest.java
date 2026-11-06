package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDialogue;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A camp's trade in the economy city (urban milestone 23): finite, restocked only by the camp's own
 * foraging, and never at the cost of the people who sell. In generator 20 a trader keeps back two
 * rations and two waterskins for everyone its stash feeds, so buying cannot take a camp's last day of
 * food or water; generator 19's trader still sells until its stores are bare. Either way nothing is
 * conjured: what the player receives left the camp's own containers, and what the player gave went in.
 */
class UrbanEconomyTradeTest {
    private static final long SEED = 20_260_731L;

    /** A met camp whose trader sells food or water, with its trader and the index of that offer. */
    private record Market(UrbanSite camp, Npc trader, int offer, UrbanSiteDialogue.Offer terms) { }

    private static Market market(Game g) {
        List<SurvivorSites.Site> camps = new ArrayList<>();
        for (int rz = -2; rz <= 2; rz++) {
            for (int rx = -2; rx <= 2; rx++) {
                for (SurvivorSites.Site s : g.world.urban.sites(rx, rz).sites()) {
                    if (s.kind() == SurvivorSites.Kind.CAMP) { camps.add(s); }
                }
            }
        }
        camps.sort(Comparator.comparingDouble(s -> s.anchor().distSq(0, s.anchor().y(), 0)));
        for (SurvivorSites.Site plan : camps) {
            SurvivorWorldArena.standAt(g, plan.entry());
            SurvivorWorldArena.slowTick(g);
            UrbanSite camp = SurvivorWorldArena.site(g, plan);
            if (camp == null) { continue; }
            for (Settlement.Resident base : camp.residents) {
                UrbanSite.Resident r = (UrbanSite.Resident) base;
                if (r.role != SurvivorSites.Role.TRADER || r.live == null) { continue; }
                List<UrbanSiteDialogue.Offer> offers = UrbanSiteDialogue.offers(g, r.live);
                for (int i = 0; i < offers.size(); i++) {
                    ItemType get = offers.get(i).get();
                    if (get == ItemType.WATERSKIN_CLEAN || get.isEdible()) { return new Market(camp, r.live, i, offers.get(i)); }
                }
            }
        }
        throw new AssertionError("no camp within two regions sells food or water");
    }

    /** How many of an item the camp's ground-floor storage, which its trader sells from, holds. */
    private static int stock(Game g, UrbanSite camp, ItemType item) {
        BuildingPlan b = g.world.urban.buildingById(camp.id);
        java.util.Set<Vec3i> stores = new java.util.LinkedHashSet<>(camp.plan.stash());
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.CONTAINER && UrbanIds.floorIndex(f.id()) == camp.plan.floor()) {
                stores.add(f.position());
            }
        }
        int n = 0;
        for (Vec3i p : stores) {
            g.world.urban.containers.touch(g.world, p); // a stash rolls on first touch, by the player or its people
            Inventory inv = g.world.crateContents.get(p);
            if (inv != null) { n += inv.count(item); }
        }
        return n;
    }

    @Test
    void aTraderKeepsBackWhatItsPeopleLiveOnInTheEconomyCityOnly() {
        for (int generator : new int[]{World.GEN_URBAN_MISSIONS, World.GEN_URBAN_ECONOMY}) {
            Game g = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, generator, new AudioManager());
            Market m = market(g);
            ItemType give = m.terms().give(), get = m.terms().get();
            g.player.inventory.set(20, new ItemStack(give, Math.min(give.maxStack, 64)));
            g.player.inventory.set(21, new ItemStack(give, Math.min(give.maxStack, 64)));
            int people = m.camp().present();
            String last = null;
            int sold = 0, before = stock(g, m.camp(), get);
            for (int i = 0; i < 40; i++) {
                last = UrbanSiteDialogue.trade(g, m.trader(), m.offer());
                if (!"A fair trade.".equals(last)) { break; }
                sold += m.terms().getCount();
            }
            int left = stock(g, m.camp(), get);
            String context = "generator " + generator + " " + m.terms().describe() + " to " + people + " people: sold "
                    + sold + " of " + before + ", " + left + " left, last word '" + last + "'";
            System.out.println("[economy-trade] " + context);
            assertEquals(before - sold, left, context + ": every item sold left the camp's own storage");
            if (generator == World.GEN_URBAN_ECONOMY) {
                int reserve = UrbanSiteDialogue.RESERVE_PER_MOUTH * people;
                assertTrue(last.startsWith("We can't spare"), context);
                assertTrue(left >= Math.min(before, reserve), context + ": one of each kept back for each of them");
                assertTrue(left - m.terms().getCount() < reserve, context + ": and no more than that");
                // Their foraging restocks them by the hour; what rises above the reserve is for sale again.
                int later = 0;
                for (int hour = 0; hour < 48 && later == 0; hour++) {
                    g.time.totalMinutes += 60;
                    SurvivorWorldArena.standAt(g, m.camp().plan.entry());
                    SurvivorWorldArena.slowTick(g);
                    while (later < 40 && "A fair trade.".equals(last = UrbanSiteDialogue.trade(g, m.trader(), m.offer()))) {
                        later += m.terms().getCount();
                    }
                    if (later > 0) {
                        int now = stock(g, m.camp(), get);
                        System.out.println("[economy-trade] after " + (hour + 1) + " h of foraging: sold " + later + " more, "
                                + now + " left, last word '" + last + "'");
                        assertTrue(now >= reserve && now - m.terms().getCount() < reserve, context + ": again only the surplus");
                    }
                }
                assertTrue(later > 0, context + ": two days of foraging put something above the reserve to sell");
            } else {
                assertTrue(last.startsWith("We're out of"), context + ": generator 19 sells until its stores are bare");
                assertTrue(left < m.terms().getCount(), context);
            }
        }
    }

    @Test
    void whatATraderKeptBackFeedsItsPeopleThroughTheNextDay() {
        Game g = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, World.GEN_URBAN_ECONOMY, new AudioManager());
        Market m = market(g);
        g.player.inventory.set(20, new ItemStack(m.terms().give(), Math.min(m.terms().give().maxStack, 64)));
        for (int i = 0; i < 40 && "A fair trade.".equals(UrbanSiteDialogue.trade(g, m.trader(), m.offer())); i++) { }
        Vec3i stand = m.camp().plan.entry();
        for (int hour = 0; hour < 24; hour++) {
            g.time.totalMinutes += 60;
            SurvivorWorldArena.standAt(g, stand);
            SurvivorWorldArena.slowTick(g);
        }
        for (Settlement.Resident base : m.camp().residents) {
            UrbanSite.Resident r = (UrbanSite.Resident) base;
            assertTrue(r.alive && r.hunger < 100f && r.thirst < 100f,
                    r.name + " is fed and watered a day after the trader sold all it could: hunger " + r.hunger
                            + ", thirst " + r.thirst);
        }
    }
}

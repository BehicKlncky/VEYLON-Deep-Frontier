package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanRevision;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The first session's encounter pressure in the economy city (urban milestone 23, D-97's finding).
 * Encounter catalogue 6 is catalogue 5 everywhere but the starter block, where only the apartment
 * whose street door lies farthest from the refuge's courtyard door keeps its two slots: the one or two
 * bodies DESIGN §4.4 asks for, against up to eight before. Pure plan checks on the six design seeds.
 */
class UrbanEconomyEncounterTest {
    private static World city(long seed, int generator) {
        World w = new World(seed, generator);
        w.urban.resolveContentRevision(UrbanRevision.contentFor(generator));
        return w;
    }

    private static List<EncounterAnchors.Anchor> starter(World w) {
        List<EncounterAnchors.Anchor> out = new ArrayList<>();
        for (ParcelPlan parcel : w.urban.regionPlan(0, 0).parcels()) {
            if (UrbanStarterSite.isNeighbour(parcel)) { out.addAll(EncounterAnchors.forParcel(w, parcel)); }
        }
        return out;
    }

    @Test
    void theStarterBlockHoldsOneApartmentsTwoSlotsFarFromTheRefugeDoor() {
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            World economy = city(seed, World.GEN_URBAN_ECONOMY), missions = city(seed, World.GEN_URBAN_MISSIONS);
            assertEquals(6, EncounterAnchors.catalogue(economy));
            assertEquals(5, EncounterAnchors.catalogue(missions));
            List<EncounterAnchors.Anchor> eased = starter(economy), before = starter(missions);
            Vec3i door = UrbanStarterSite.of(economy.urban).refuge().approaches().getLast();
            double nearest = Double.MAX_VALUE;
            for (EncounterAnchors.Anchor a : eased) {
                nearest = Math.min(nearest, Math.sqrt(a.cell().distSq(door.x() + .5, door.y(), door.z() + .5)));
            }
            System.out.printf("[economy-encounters] seed=%d starter slots %d (catalogue 5: %d), nearest %.1f blocks from"
                    + " the refuge door%n", seed, eased.size(), before.size(), nearest);
            assertEquals(2, eased.size(), "seed " + seed + ": one apartment's street and room slots");
            assertEquals(1, eased.stream().map(EncounterAnchors.Anchor::building).distinct().count());
            assertTrue(before.size() > eased.size(), "seed " + seed + ": catalogue 5 held " + before.size());
            assertTrue(before.containsAll(eased), "the slots kept are catalogue five's own, not new ones");
            assertTrue(nearest >= 16, "seed " + seed + ": the threat stands across the block, " + nearest + " blocks away");
        }
    }

    @Test
    void everywhereButTheStarterBlockCatalogueSixIsCatalogueFive() {
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            World economy = city(seed, World.GEN_URBAN_ECONOMY), missions = city(seed, World.GEN_URBAN_MISSIONS);
            for (int rz = -1; rz <= 1; rz++) {
                for (int rx = -1; rx <= 1; rx++) {
                    for (ParcelPlan parcel : economy.urban.regionPlan(rx, rz).parcels()) {
                        if (UrbanStarterSite.isNeighbour(parcel)) { continue; }
                        assertEquals(EncounterAnchors.forParcel(missions, parcel), EncounterAnchors.forParcel(economy, parcel),
                                "seed " + seed + " parcel " + parcel.id());
                    }
                    assertEquals(EncounterAnchors.forStation(missions, rx, rz), EncounterAnchors.forStation(economy, rx, rz));
                    for (int superblock = 0; superblock < 16; superblock++) {
                        assertEquals(EncounterAnchors.forEcology(missions, rx, rz, superblock),
                                EncounterAnchors.forEcology(economy, rx, rz, superblock), "seed " + seed + " creatures");
                    }
                }
            }
        }
    }
}

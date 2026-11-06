package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelAccess;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout thirteen's compounds (urban milestone 17) on the design seeds: a guarded stronghold of
 * six held buildings in every civic region, exactly one raider yard within three regions of the
 * spawn, every person on a planned cell of their own compound, and in a generated world a
 * compound that only its gate (or, for raiders, its back way) lets anyone walk into.
 */
class CompoundSitesTest {
    static final long[] DESIGN_SEEDS = SurvivorSitesTest.DESIGN_SEEDS;
    private static final int RANGE = DistrictMap.ALL_DISTRICTS_RING;

    @Test
    void everyCivicRegionNearTheSpawnHoldsAGuardedStrongholdOfSixHeldBuildings() {
        int strongholds = 0;
        for (long seed : DESIGN_SEEDS) {
            World w = new World(seed, World.GEN_URBAN_STRONGHOLDS);
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    DistrictPlan plan = w.urban.regionPlan(rx, rz);
                    CompoundSites.Compound c = w.urban.sites(rx, rz).stronghold();
                    if (plan.district != UrbanDistrict.CIVIC) {
                        assertNull(c, "only the civic precinct keeps a stronghold");
                        continue;
                    }
                    assertNotNull(c, "every civic region near the spawn keeps a stronghold: " + seed + " " + rx + "," + rz);
                    strongholds++;
                    assertEquals(SurvivorSites.Kind.STRONGHOLD, c.kind());
                    assertEquals(6, c.members().size(), "the whole courtyard block is held");
                    boolean clinic = false, apartment = false;
                    for (long id : c.members()) {
                        BuildingPlan b = building(w, id);
                        assertEquals(BuildingPlan.Variant.OCCUPIED, b.variant(), "every wall of the compound is a held building");
                        assertTrue(streetDoorLocked(b), "a held building's street door is locked: " + UrbanIds.format(id));
                        clinic |= b.family() == BuildingPlan.Family.CLINIC;
                        apartment |= b.family() == BuildingPlan.Family.APARTMENT;
                    }
                    assertTrue(clinic && apartment, "a clinic and barracks");
                    assertEquals(c.members().getFirst(), c.site().id(), "the site is named by its first building");
                    assertEquals(BuildingPlan.Family.CLINIC, building(w, c.clinic()).family());
                    int people = c.site().residents().size();
                    assertTrue(people >= CompoundSites.MIN_STRONGHOLD && people <= CompoundSites.MAX_STRONGHOLD, "8 to 16: " + people);
                    int guards = 0;
                    Set<String> names = new HashSet<>();
                    for (SurvivorSites.Resident r : c.site().residents()) {
                        if (r.role() == SurvivorSites.Role.GATEKEEPER || r.role() == SurvivorSites.Role.SENTRY
                                || r.role() == SurvivorSites.Role.PATROL) { guards++; }
                        assertTrue(names.add(r.name()), "names are unique within a stronghold");
                        assertEquals(UrbanIds.resident(c.site().id(), r.slot(), 0), r.id());
                    }
                    assertTrue(guards >= 4, "at least four guards: " + guards);
                    assertEquals(SurvivorSites.Role.GATEKEEPER, c.site().residents().getFirst().role());
                    assertEquals(c.gate().outside(), c.site().residents().getFirst().day(), "the gatekeeper keeps the porch by day");
                    assertEquals(2, c.barricades().size());
                    assertTrue(c.barricades().getFirst().gated() && !c.barricades().get(1).gated());
                    assertTrue(c.signText().contains("Ward"), c.signText());
                    checkCompoundCells(w, plan, c);
                }
            }
        }
        assertTrue(strongholds >= DESIGN_SEEDS.length, "a stronghold near every design seed's spawn: " + strongholds);
    }

    @Test
    void exactlyOneRaiderYardLiesWithinThreeRegionsOfTheSpawnInTheReservedIndustrialRegion() {
        for (long seed : DESIGN_SEEDS) {
            World w = new World(seed, World.GEN_URBAN_STRONGHOLDS);
            int yards = 0;
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    CompoundSites.Compound c = w.urban.sites(rx, rz).raiders();
                    if (c == null) { continue; }
                    yards++;
                    DistrictPlan plan = w.urban.regionPlan(rx, rz);
                    assertEquals(UrbanDistrict.INDUSTRIAL, plan.district);
                    assertEquals(UrbanDistrict.INDUSTRIAL, DistrictMap.reserved(seed, rx, rz), "the district map's reserved one");
                    assertEquals(SurvivorSites.Kind.RAIDERS, c.kind());
                    int people = c.site().residents().size();
                    assertTrue(people >= CompoundSites.MIN_RAIDERS && people <= CompoundSites.MAX_RAIDERS, "4 to 8: " + people);
                    assertEquals(SurvivorSites.Role.BOSS, c.site().residents().getFirst().role());
                    assertEquals(SurvivorSites.Role.SENTRY, c.site().residents().get(1).role());
                    assertEquals(c.gate().outside(), c.site().residents().get(1).day(), "a sentry keeps the street outside the gate");
                    for (long id : c.members()) {
                        assertEquals(BuildingPlan.Variant.OCCUPIED, building(w, id).variant());
                    }
                    BuildingPlan back = building(w, c.backWay());
                    assertNotEquals(BuildingPlan.Variant.OCCUPIED, back.variant(), "the back way is nobody's");
                    assertFalse(back.family() == BuildingPlan.Family.CLINIC || back.family() == BuildingPlan.Family.SECURITY);
                    assertFalse(c.members().contains(c.backWay()));
                    assertTrue(c.inYard(c.backDoor().x(), c.backDoor().z()), "the back way's rear door opens into the yard");
                    assertEquals(3, c.patrol().size(), "the patrol walks the alley end to end: " + c.patrol());
                    checkCompoundCells(w, plan, c);
                }
            }
            assertEquals(1, yards, "one bounded hostile site near the spawn of seed " + seed);
        }
    }

    @Test
    void campsAndEncountersNeverClaimACompoundsBuildings() {
        for (long seed : DESIGN_SEEDS) {
            World w = new World(seed, World.GEN_URBAN_STRONGHOLDS);
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    SurvivorSites sites = w.urban.sites(rx, rz);
                    CompoundClaims claims = w.urban.routes(rx, rz).claims();
                    if (sites.camp() != null) {
                        assertFalse(claims.claims(UrbanIds.parcelIndex(sites.camp().id())), "a camp is never a compound's building");
                    }
                    for (CompoundSites.Compound c : sites.compounds()) {
                        for (ParcelPlan parcel : w.urban.regionPlan(rx, rz).parcels()) {
                            long building = UrbanIds.building(parcel.id(), 0);
                            if (!c.members().contains(building)) { continue; }
                            assertTrue(EncounterAnchors.forParcel(w, parcel).isEmpty(),
                                    "no infected waits inside a held compound building");
                        }
                    }
                }
            }
        }
    }

    @Test
    void compoundsAreTheSameWhateverOrderTheCityIsPlannedIn() {
        for (long seed : new long[]{DESIGN_SEEDS[0], DESIGN_SEEDS[2]}) {
            World a = new World(seed, World.GEN_URBAN_STRONGHOLDS), b = new World(seed, World.GEN_URBAN_STRONGHOLDS);
            List<String> forward = new ArrayList<>(), backward = new ArrayList<>();
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) { forward.add(String.valueOf(a.urban.sites(rx, rz).compounds())); }
            }
            for (int rz = RANGE; rz >= -RANGE; rz--) {
                for (int rx = RANGE; rx >= -RANGE; rx--) { backward.add(0, String.valueOf(b.urban.sites(rx, rz).compounds())); }
            }
            assertEquals(forward, backward, "plans never depend on visit order or cache eviction");
        }
    }

    @Test
    void earlierGeneratorsPlanNoCompoundAndKeepTheirSites() {
        World survivors = new World(7L, World.GEN_URBAN_SURVIVORS);
        World strongholds = new World(7L, World.GEN_URBAN_STRONGHOLDS);
        for (int rz = -RANGE; rz <= RANGE; rz++) {
            for (int rx = -RANGE; rx <= RANGE; rx++) {
                assertSame(CompoundClaims.NONE, survivors.urban.routes(rx, rz).claims());
                assertTrue(survivors.urban.sites(rx, rz).compounds().isEmpty());
                SurvivorSites.Site refuge = survivors.urban.sites(rx, rz).refuge();
                // Compounds stand only in civic and industrial regions; hidden cellars only in residential ones.
                assertEquals(refuge, strongholds.urban.sites(rx, rz).refuge(), "the hidden civilians are where they were");
            }
        }
    }

    @Test
    void aGeneratedStrongholdIsWalledButForItsLockedGate() {
        World w = new World(DESIGN_SEEDS[0], World.GEN_URBAN_STRONGHOLDS);
        CompoundSites.Compound c = nearest(w, SurvivorSites.Kind.STRONGHOLD);
        generate(w, c);
        checkStamped(w, c, BlockType.CONCRETE);
        for (Vec3i leaf : c.gate().leaves()) {
            assertEquals(FixtureStates.LOCK_KEYED, w.fixtureLockAt(leaf.x(), leaf.y(), leaf.z()), "its guards keep the gate locked");
            assertEquals(FixtureStates.LOCK_KEYED, w.fixtureLockAt(leaf.x(), leaf.y() + 1, leaf.z()));
        }
        assertEquals(c.signText(), w.urban.compoundSignAt(c.sign().x(), c.sign().z()));
        Vec3i street = street(c);
        RouteProof.Box box = box(c);
        RouteProof.Result shut = RouteProof.prove(opened(w, false), box, List.of(street));
        assertTrue(shut.reached(c.gate().laneOutside()), "the porch is the street's");
        assertFalse(shut.reached(c.gate().laneInside()), "nobody walks in past a locked gate");
        for (Vec3i p : c.patrol()) { assertFalse(shut.reached(p), "or into the courtyard by any other way: " + p); }
        RouteProof.Result open = RouteProof.prove(opened(w, true), box, List.of(street));
        assertTrue(open.roundTrip(c.gate().laneInside()), "through the open gate and back out");
        for (Vec3i post : c.posts()) { assertTrue(open.roundTrip(post), "every post is walkable: " + post); }
        for (Vec3i p : c.patrol()) { assertTrue(open.roundTrip(p), "every patrol point is walkable: " + p); }
        assertTrue(open.roundTrip(c.market()), "the trader's pitch");
        for (long id : c.members()) {
            BuildingPlan b = building(w, id);
            for (Vec3i approach : b.approaches().subList(1, b.approaches().size())) {
                assertTrue(open.roundTrip(approach), "every rear door is reached from the courtyard: " + approach);
            }
        }
    }

    @Test
    void aGeneratedRaiderYardLetsTheBackWayInWithItsGateShut() {
        World w = new World(DESIGN_SEEDS[0], World.GEN_URBAN_STRONGHOLDS);
        CompoundSites.Compound c = nearest(w, SurvivorSites.Kind.RAIDERS);
        generate(w, c);
        checkStamped(w, c, BlockType.RUBBLE);
        for (Vec3i leaf : c.gate().leaves()) {
            assertEquals(FixtureStates.LOCK_KEYED, w.fixtureLockAt(leaf.x(), leaf.y(), leaf.z()), "the raiders keep their gate locked");
        }
        Vec3i street = street(c);
        RouteProof.Box box = box(c);
        BuildingPlan back = building(w, c.backWay());
        RouteProof.Result sealed = RouteProof.prove(shutting(opened(w, false), c.backLot()), box, List.of(street));
        assertTrue(sealed.reached(c.gate().outside()), "the sentry's street is open");
        assertFalse(sealed.reached(c.gate().laneInside()), "the gate and the held buildings hold");
        for (Vec3i p : c.patrol()) { assertFalse(sealed.reached(p), "no other way into the yard: " + p); }
        // The way round: in by the back way's street door, through its ground floor, out of its rear door.
        RouteProof.Result round = RouteProof.prove(opened(w, false), box, List.of(back.approaches().getFirst()));
        assertTrue(round.roundTrip(c.backDoor()), "the back way reaches the yard and back with the gate shut");
        assertTrue(round.reached(c.gate().laneInside()), "and the whole alley with it");
        for (Vec3i p : c.patrol()) { assertTrue(round.roundTrip(p), "patrol point " + p); }
        RouteProof.Result street2 = RouteProof.prove(opened(w, false), box, List.of(street));
        assertTrue(street2.roundTrip(c.backDoor()), "from the gate's street round to the back way and into the yard");
    }

    /** The doors of one lot shut, as if they were walls: what holds the yard closed without the back way. */
    private static VoxelAccess shutting(VoxelAccess inner, ParcelPlan.Rect lot) {
        return new VoxelAccess() {
            @Override public BlockType getBlock(int x, int y, int z) {
                BlockType b = inner.getBlock(x, y, z);
                return lot.contains(x, z) && Fixtures.kindOf(b) != null && Fixtures.kindOf(b).door ? BlockType.BRICK : b;
            }
            @Override public boolean isColumnLoaded(int x, int z) { return inner.isColumnLoaded(x, z); }
        };
    }

    @Test
    void stampedCompoundsAreTheSameInAnyChunkOrder() {
        World a = new World(DESIGN_SEEDS[2], World.GEN_URBAN_STRONGHOLDS), b = new World(DESIGN_SEEDS[2], World.GEN_URBAN_STRONGHOLDS);
        for (SurvivorSites.Kind kind : new SurvivorSites.Kind[]{SurvivorSites.Kind.STRONGHOLD, SurvivorSites.Kind.RAIDERS}) {
            CompoundSites.Compound c = nearest(a, kind);
            List<int[]> chunks = chunksOf(c);
            for (int[] ch : chunks) { a.getOrCreateChunk(ch[0], ch[1]); }
            for (int i = chunks.size() - 1; i >= 0; i--) { b.getOrCreateChunk(chunks.get(i)[0], chunks.get(i)[1]); }
            for (CompoundSites.Barricade line : c.barricades()) {
                for (Vec3i cell : line.cells()) {
                    for (int y = cell.y() - 1; y <= cell.y() + CompoundSites.HEIGHT; y++) {
                        assertSame(a.getBlock(cell.x(), y, cell.z()), b.getBlock(cell.x(), y, cell.z()), cell + " y " + y);
                    }
                }
            }
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    /** Every bed and day cell is a standable planned cell of its compound, and every yard cell open ground. */
    private static void checkCompoundCells(World w, DistrictPlan plan, CompoundSites.Compound c) {
        Set<Vec3i> beds = new HashSet<>();
        for (SurvivorSites.Resident r : c.site().residents()) {
            assertTrue(beds.add(r.bed()), "one person a bed");
            assertTrue(planned(w, plan, c, r.bed()), r.role() + " bed " + r.bed());
            assertTrue(planned(w, plan, c, r.day()), r.role() + " day cell " + r.day());
            assertTrue(c.inside(r.bed().x(), r.bed().y(), r.bed().z()), "beds are inside the compound: " + r.bed());
        }
        assertTrue(planned(w, plan, c, c.site().safe()));
        for (Vec3i post : c.posts()) { assertTrue(CompoundSites.yardCell(plan, post), "post " + post); }
        for (Vec3i p : c.patrol()) {
            assertTrue(CompoundSites.yardCell(plan, p) && c.inYard(p.x(), p.z()), "patrol " + p);
        }
        assertEquals(CompoundSites.Zone.PORCH, c.zoneAt(c.gate().outside().x(), c.gate().outside().y(), c.gate().outside().z()));
        assertEquals(CompoundSites.Zone.YARD, c.zoneAt(c.gate().inside().x(), c.gate().inside().y(), c.gate().inside().z()));
        assertFalse(c.site().stash().isEmpty());
        for (Vec3i s : c.site().stash()) {
            assertTrue(c.members().contains(w.urban.buildingAt(s.x(), s.z()).id()), "the stash is the compound's own");
        }
    }

    private static boolean planned(World w, DistrictPlan plan, CompoundSites.Compound c, Vec3i cell) {
        if (c.inYard(cell.x(), cell.z()) || c.porch().contains(cell.x(), cell.z())) {
            return CompoundSites.yardCell(plan, cell);
        }
        BuildingPlan b = w.urban.buildingAt(cell.x(), cell.z());
        return b != null && c.members().contains(b.id()) && SurvivorSites.open(b.voxels(), cell);
    }

    private static boolean streetDoorLocked(BuildingPlan b) {
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.DOOR && UrbanIds.floorIndex(f.id()) == 0 && UrbanIds.roomIndex(f.id()) == 0
                    && (UrbanIds.slotIndex(f.id()) == 8 || UrbanIds.slotIndex(f.id()) == 10) && !f.locked()) {
                return false;
            }
        }
        return true;
    }

    private static BuildingPlan building(World w, long id) {
        DistrictPlan plan = w.urban.regionPlan(UrbanIds.regionX(id), UrbanIds.regionZ(id));
        ParcelPlan p = plan.parcel(UrbanIds.parcelIndex(id));
        return w.urban.buildingAt(p.x0(), p.z0());
    }

    static CompoundSites.Compound nearest(World w, SurvivorSites.Kind kind) {
        CompoundSites.Compound best = null;
        for (int rz = -RANGE; rz <= RANGE; rz++) {
            for (int rx = -RANGE; rx <= RANGE; rx++) {
                for (CompoundSites.Compound c : w.urban.sites(rx, rz).compounds()) {
                    if (c.kind() == kind && (best == null || c.site().anchor().distSq(0, 41, 0) < best.site().anchor().distSq(0, 41, 0))) {
                        best = c;
                    }
                }
            }
        }
        assertNotNull(best, "a " + kind + " near the spawn");
        return best;
    }

    /** The street cell in front of the gate's porch. */
    private static Vec3i street(CompoundSites.Compound c) {
        ParcelPlan.Side out = c.gate().outward();
        Vec3i lane = c.gate().laneOutside();
        Vec3i cell = lane;
        while (c.porch().contains(cell.x(), cell.z())) { cell = cell.offset(out.dx, 0, out.dz); }
        return cell.offset(out.dx, 0, out.dz);
    }

    private static RouteProof.Box box(CompoundSites.Compound c) {
        ParcelPlan.Rect b = c.site().bounds();
        return new RouteProof.Box(b.x0() - 4, CompoundSites.FEET, b.z0() - 4, b.x1() + 4, CompoundSites.FEET + 2, b.z1() + 4);
    }

    private static List<int[]> chunksOf(CompoundSites.Compound c) {
        ParcelPlan.Rect b = c.site().bounds();
        List<int[]> out = new ArrayList<>();
        for (int cz = Math.floorDiv(b.z0() - 6, Chunk.SZ); cz <= Math.floorDiv(b.z1() + 6, Chunk.SZ); cz++) {
            for (int cx = Math.floorDiv(b.x0() - 6, Chunk.SX); cx <= Math.floorDiv(b.x1() + 6, Chunk.SX); cx++) {
                out.add(new int[]{cx, cz});
            }
        }
        return out;
    }

    private static void generate(World w, CompoundSites.Compound c) {
        for (int[] ch : chunksOf(c)) { w.getOrCreateChunk(ch[0], ch[1]); }
    }

    /** The barricade lines, the gate leaves, the sign and the torch stand as planned. */
    private static void checkStamped(World w, CompoundSites.Compound c, BlockType base) {
        for (CompoundSites.Barricade line : c.barricades()) {
            for (Vec3i cell : line.cells()) {
                boolean leaf = c.gate().column(cell.x(), cell.z());
                for (int h = 0; h < CompoundSites.HEIGHT; h++) {
                    BlockType got = w.getBlock(cell.x(), cell.y() + h, cell.z());
                    if (leaf && h < 2) {
                        assertSame(BlockType.METAL_DOOR, got, "gate leaf " + cell);
                    } else if (cell.x() == c.sign().x() && cell.z() == c.sign().z() && cell.y() + h == c.sign().y()) {
                        assertSame(BlockType.SIGN, got);
                    } else {
                        assertSame(h == 0 && !leaf ? base : BlockType.SHEET_METAL, got, "barricade " + cell + " h " + h);
                    }
                }
            }
        }
        assertSame(BlockType.TORCH, w.getBlock(c.sign().x(), c.sign().y() + 1, c.sign().z()));
    }

    /** Closed unlocked doors open, as a body walking through opens them; with {@code gate}, the gate's leaves too. */
    private static VoxelAccess opened(World w, boolean gate) {
        return new VoxelAccess() {
            @Override public BlockType getBlock(int x, int y, int z) {
                BlockType b = w.getBlock(x, y, z);
                boolean unlocked = w.fixtureLockAt(x, y, z) == FixtureStates.LOCK_NONE
                        || gate && w.urban.compoundLockAt(x, y, z) != FixtureStates.LOCK_NONE;
                return Fixtures.isClosedDoor(b) && unlocked ? Fixtures.kindOf(b).open : b;
            }
            @Override public boolean isColumnLoaded(int x, int z) { return w.isColumnLoaded(x, z); }
        };
    }

    @Test
    void theStandingCheckIsTheSharedOne() {
        // The compound's open-ground cells are what the voxel clearance stands bodies on.
        World w = new World(DESIGN_SEEDS[0], World.GEN_URBAN_STRONGHOLDS);
        CompoundSites.Compound c = nearest(w, SurvivorSites.Kind.STRONGHOLD);
        generate(w, c);
        for (Vec3i p : c.patrol()) { assertTrue(VoxelClearance.standable(w, p.x(), p.y(), p.z()), "patrol " + p); }
        assertTrue(VoxelClearance.standable(w, c.market().x(), c.market().y(), c.market().z()));
    }
}

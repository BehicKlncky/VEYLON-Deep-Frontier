package com.veylon.world.urban;

import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Variant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which parcels of a layout-thirteen region a guarded compound holds (urban milestone 17), decided
 * from the district plan and the district catalogue alone, before any building is planned. The
 * route catalogue reads it, so a claimed parcel's building is planned held from the start; the
 * compound's cells, gate and people come later from the planned buildings ({@link CompoundSites}).
 *
 * <ul>
 *   <li><b>A stronghold</b> in every civic region: one courtyard block whose six buildings are all
 *       held, with a clinic and at least one apartment for barracks among them, a school or a
 *       security post preferred. One of its two passages becomes the gate, the other is barricaded.</li>
 *   <li><b>A raider yard</b> in one industrial region within three regions of the spawn (the
 *       district map's reserved one) and in one industrial region in four beyond: a depot row
 *       whose service alley is the raiders' yard. They hold every lot of the row but one, the
 *       <em>back way</em>, a building left unheld whose rear door opens into the yard.</li>
 * </ul>
 * Pure: a function of the seed and the district plan, with its own salts, so neighbours, chunk
 * order and cache eviction never change it.
 */
public final class CompoundClaims {
    /** The claims of a region without compounds, and of every layout before thirteen. */
    public static final CompoundClaims NONE = new CompoundClaims(null, null);
    /** Share of industrial regions beyond the third ring that keep a raider yard. */
    static final double RAIDER_SHARE = 0.25;
    private static final int LAYOUT = UrbanRevision.LAYOUT_STRONGHOLDS;

    /**
     * One compound's claim.
     *
     * @param kind       {@link SurvivorSites.Kind#STRONGHOLD} or {@link SurvivorSites.Kind#RAIDERS}
     * @param superblock the superblock it holds
     * @param members    the parcel indices it holds, ascending
     * @param backWay    a raider yard's unheld parcel, -1 for a stronghold
     * @param gateMouth  which mouth carries the gate: a stronghold's north (0) or south (1)
     *                   passage, a raider yard's west (0) or east (1) alley end
     */
    public record Claim(SurvivorSites.Kind kind, int superblock, List<Integer> members, int backWay, int gateMouth) {
        public Claim {
            members = List.copyOf(members);
        }
    }

    private final Claim stronghold;
    private final Claim raiders;

    private CompoundClaims(Claim stronghold, Claim raiders) {
        this.stronghold = stronghold;
        this.raiders = raiders;
    }

    /** The stronghold claim of a civic region, or null. */
    public Claim stronghold() { return stronghold; }

    /** The raider yard claim of a chosen industrial region, or null. */
    public Claim raiders() { return raiders; }

    /** True when a compound holds the parcel. */
    public boolean member(int parcel) {
        return stronghold != null && stronghold.members.contains(parcel)
                || raiders != null && raiders.members.contains(parcel);
    }

    /** True for a raider yard's back way. */
    public boolean backWay(int parcel) {
        return raiders != null && raiders.backWay == parcel;
    }

    /** True for any parcel a compound claims: its members and a raider yard's back way. */
    public boolean claims(int parcel) {
        return member(parcel) || backWay(parcel);
    }

    /**
     * The variant a parcel's building is planned with: held for a member, never held for the back
     * way (an abandoned building, or a damaged one where the district would have held it), and
     * whatever the district gives everywhere else.
     */
    public Variant variant(ParcelPlan parcel, Variant expected) {
        if (member(parcel.index())) { return Variant.OCCUPIED; }
        if (backWay(parcel.index()) && expected == Variant.OCCUPIED) { return Variant.DAMAGED; }
        return expected;
    }

    /** Plans one region. Only civic and industrial regions of layout thirteen keep compounds. */
    public static CompoundClaims of(long seed, DistrictPlan district) {
        if (district.district == null) { return NONE; }
        Claim stronghold = district.district == UrbanDistrict.CIVIC ? stronghold(seed, district) : null;
        Claim raiders = district.district == UrbanDistrict.INDUSTRIAL && raiderRegion(seed, district)
                ? raiders(seed, district) : null;
        return stronghold == null && raiders == null ? NONE : new CompoundClaims(stronghold, raiders);
    }

    /**
     * Whether an industrial region keeps a raider yard: exactly the district map's reserved
     * industrial region within three regions of the spawn, so one hostile site is always near
     * and never more, and one industrial region in four farther out.
     */
    static boolean raiderRegion(long seed, DistrictPlan district) {
        int ring = DistrictMap.ring(district.rx, district.rz);
        if (ring <= 1) { return false; }
        if (ring <= DistrictMap.ALL_DISTRICTS_RING) {
            return DistrictMap.reserved(seed, district.rx, district.rz) == UrbanDistrict.INDUSTRIAL;
        }
        return UrbanHash.unit(seed, LAYOUT, UrbanSalts.COMPOUND_RAIDERS, district.rx, district.rz, 0) < RAIDER_SHARE;
    }

    // ---- the stronghold -----------------------------------------------------------------------

    private static Claim stronghold(long seed, DistrictPlan district) {
        Claim best = null;
        int bestScore = -1;
        long bestKey = 0;
        for (SuperblockPlan block : district.superblocks()) {
            if (block.kind() != SuperblockPlan.Kind.COURTYARD_HOUSING) { continue; }
            List<ParcelPlan> ring = parcelsOf(district, block.index());
            if (ring.size() != 6 || !passage(ring, CityPlanner.SLOT_NORTH_WEST, CityPlanner.SLOT_NORTH_EAST)
                    || !passage(ring, CityPlanner.SLOT_SOUTH_WEST, CityPlanner.SLOT_SOUTH_EAST)) {
                continue;
            }
            int clinics = 0, apartments = 0, score = 0;
            boolean ordinary = true;
            for (ParcelPlan p : ring) {
                if (UrbanStarterSite.isNeighbour(p) || p.use() == ParcelPlan.Use.REFUGE) { ordinary = false; }
                Family f = DistrictCatalogue.family(seed, district, p);
                if (f == Family.CLINIC) { clinics++; }
                if (f == Family.APARTMENT) { apartments++; }
                if (f == Family.SCHOOL) { score |= 2; }
                if (f == Family.SECURITY) { score |= 1; }
            }
            if (!ordinary || clinics == 0 || apartments == 0) { continue; }
            long key = UrbanHash.mix(seed, LAYOUT, UrbanSalts.COMPOUND_STRONGHOLD, district.rx, district.rz, block.index());
            if (best == null || score > bestScore || score == bestScore && Long.compareUnsigned(key, bestKey) < 0) {
                List<Integer> members = new ArrayList<>();
                for (ParcelPlan p : ring) { members.add(p.index()); }
                members.sort(null);
                int gate = UrbanHash.range(0, 1, seed, LAYOUT, UrbanSalts.COMPOUND_GATE, district.rx, district.rz, block.index());
                best = new Claim(SurvivorSites.Kind.STRONGHOLD, block.index(), members, -1, gate);
                bestScore = score;
                bestKey = key;
            }
        }
        return best;
    }

    /** A strip's two parcels leave exactly a passage between them, with matching depths. */
    static boolean passage(List<ParcelPlan> ring, int westSlot, int eastSlot) {
        ParcelPlan a = slot(ring, westSlot), b = slot(ring, eastSlot);
        return a != null && b != null && b.x0() - a.x1() - 1 == CityPlanner.PASSAGE_WIDTH
                && a.z0() == b.z0() && a.z1() == b.z1();
    }

    static ParcelPlan slot(List<ParcelPlan> parcels, int slot) {
        for (ParcelPlan p : parcels) {
            if (p.slot() == slot) { return p; }
        }
        return null;
    }

    static List<ParcelPlan> parcelsOf(DistrictPlan district, int superblock) {
        List<ParcelPlan> out = new ArrayList<>();
        for (ParcelPlan p : district.parcels()) {
            if (p.superblock() == superblock) { out.add(p); }
        }
        out.sort(Comparator.comparingInt(ParcelPlan::index));
        return out;
    }

    // ---- the raider yard --------------------------------------------------------------------

    private static Claim raiders(long seed, DistrictPlan district) {
        Claim best = null;
        long bestKey = 0;
        for (SuperblockPlan block : district.superblocks()) {
            ParcelPlan.Rect alley = block.open();
            if (block.kind() != SuperblockPlan.Kind.SHOP_ROW || alley == null
                    || alley.z1() - alley.z0() + 1 != CityPlanner.PASSAGE_WIDTH
                    || alley.x0() != block.bounds().x0() || alley.x1() != block.bounds().x1()) {
                continue;
            }
            List<ParcelPlan> row = parcelsOf(district, block.index());
            if (row.size() < 3) { continue; }
            int gate = UrbanHash.range(0, 1, seed, LAYOUT, UrbanSalts.COMPOUND_GATE, district.rx, district.rz, block.index());
            int gateX = gate == 0 ? alley.x0() : alley.x1();
            ParcelPlan back = null;
            int backDistance = -1;
            boolean fits = true;
            for (ParcelPlan p : row) {
                ParcelPlan.Entrance rear = rear(p);
                // Every rear door must open into the yard, well clear of the gate's porch.
                if (rear == null || Math.min(rear.x(), rear.x2()) < alley.x0() + CompoundSites.PORCH + 2
                        || Math.max(rear.x(), rear.x2()) > alley.x1() - CompoundSites.PORCH - 2) {
                    fits = false;
                    break;
                }
                Family f = DistrictCatalogue.family(seed, district, p);
                // An unheld clinic or security post locks its rear door: it cannot be the back way.
                if (f == Family.CLINIC || f == Family.SECURITY) { continue; }
                int d = Math.abs(rear.x() - gateX);
                if (d > backDistance) {
                    back = p;
                    backDistance = d;
                }
            }
            if (!fits || back == null) { continue; }
            long key = UrbanHash.mix(seed, LAYOUT, UrbanSalts.COMPOUND_RAIDERS, district.rx, district.rz, block.index() + 1L);
            if (best == null || Long.compareUnsigned(key, bestKey) < 0) {
                List<Integer> members = new ArrayList<>();
                for (ParcelPlan p : row) {
                    if (p != back) { members.add(p.index()); }
                }
                best = new Claim(SurvivorSites.Kind.RAIDERS, block.index(), members, back.index(), gate);
                bestKey = key;
            }
        }
        return best;
    }

    /** A parcel's rear entrance onto its courtyard or alley. */
    static ParcelPlan.Entrance rear(ParcelPlan p) {
        for (ParcelPlan.Entrance e : p.entrances()) {
            if (!e.primary()) { return e; }
        }
        return null;
    }
}

package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * How exposed a claimed refuge is (urban milestone 18), read from the world as it stands: no score
 * is kept, so nothing drifts from what a body could actually do.
 *
 * <p>Its <b>ways in</b> are the building's own outer doors, on any storey (the street and rear
 * doors, a basement's service door, the roof bulkhead), and the windows of the claimed storey. Each
 * is, in its live cells:
 * <ul>
 *   <li><b>secured</b>: boarded with a barricade, or a closed door that is locked, barred or braced;</li>
 *   <li><b>closed</b>: a closed glass window, which no threat breaks;</li>
 *   <li><b>unbarred</b>: a closed door anyone may shoulder open, as the infected do;</li>
 *   <li><b>open</b>: an open, forced or shattered one, or a bare hole.</li>
 * </ul>
 * A refuge with no open or unbarred way in is <b>shut tight</b>. A <b>light</b> shows when a lit
 * light stands on the claimed storey and a window of it is not boarded up, or a way in stands open.
 *
 * <p>Pure over the plans, live blocks, fixture state and the chunks' light lists; bounded by one
 * building's fixtures and the chunks under its parcel. It never creates a chunk.
 */
public final class RefugeExposure {
    /** How a way in stands. */
    public enum State { OPEN, UNBARRED, CLOSED, SECURED }

    /**
     * One way in.
     *
     * @param cell    its bottom cell
     * @param door    true for a door, false for a window
     * @param state   how it stands
     * @param outside the cell just outside it, where something drawn to it would come
     */
    public record Way(Vec3i cell, boolean door, State state, Vec3i outside) {
        /** True when a body could come in this way: it stands open or unbarred. */
        public boolean admits() {
            return state == State.OPEN || state == State.UNBARRED;
        }

        /** True when a light inside would show through it: open, or glass. */
        public boolean showsLight() {
            return state == State.OPEN || !door && state == State.CLOSED;
        }
    }

    /** The ways in of one claimed refuge and whether a light burns on its storey. */
    public record Assessment(List<Way> ways, boolean lit) {
        public Assessment {
            ways = List.copyOf(ways);
        }

        /** No way in stands open or unbarred. */
        public boolean shutTight() {
            for (Way w : ways) {
                if (w.admits()) { return false; }
            }
            return true;
        }

        /** A lit light on the storey can be seen from outside. */
        public boolean lightShows() {
            if (!lit) { return false; }
            for (Way w : ways) {
                if (w.showsLight()) { return true; }
            }
            return false;
        }

        public int count(State state) {
            int n = 0;
            for (Way w : ways) {
                if (w.state() == state) { n++; }
            }
            return n;
        }
    }

    private RefugeExposure() {
    }

    /** The exposure of a claim, or null when its building is not planned or not loaded. */
    public static Assessment assess(World w, UrbanRefuges.Claim claim) {
        BuildingPlan b = w.urban.buildingById(claim.building());
        if (b == null) { return null; }
        ParcelPlan parcel = b.parcel();
        int storey = UrbanIds.floorIndex(claim.floor);
        List<Way> ways = new ArrayList<>();
        for (BuildingPlan.Fixture f : b.fixtures()) {
            int floor = UrbanIds.floorIndex(f.id());
            boolean outer = onEdge(parcel, f.position());
            boolean door = f.kind() == BuildingPlan.FixtureKind.DOOR && (outer || floor == b.storeys());
            boolean window = f.kind() == BuildingPlan.FixtureKind.WINDOW && floor == storey;
            if (!door && !window) { continue; }
            Vec3i p = f.position();
            if (!w.isColumnLoaded(p.x(), p.z())) { return null; }
            ways.add(new Way(p, door, state(w, p, Math.max(1, f.height()), door), outside(parcel, f)));
        }
        return new Assessment(ways, lit(w, parcel, claim.floor));
    }

    private static boolean onEdge(ParcelPlan parcel, Vec3i p) {
        return p.x() == parcel.x0() || p.x() == parcel.x1() || p.z() == parcel.z0() || p.z() == parcel.z1();
    }

    private static State state(World w, Vec3i bottom, int height, boolean door) {
        boolean hole = false;
        for (int i = 0; i < height; i++) {
            BlockType t = w.getBlock(bottom.x(), bottom.y() + i, bottom.z());
            if (UrbanFortifications.isBarricade(t)) { return State.SECURED; }
            hole |= !t.solid;
        }
        if (hole) { return State.OPEN; }
        if (!door) { return State.CLOSED; }
        return w.fixtureLockAt(bottom.x(), bottom.y(), bottom.z()) != FixtureStates.LOCK_NONE ? State.SECURED : State.UNBARRED;
    }

    /** The cell outside a way in: beyond the parcel's edge, or for a roof door its planned approach mirrored. */
    private static Vec3i outside(ParcelPlan parcel, BuildingPlan.Fixture f) {
        Vec3i p = f.position();
        if (p.x() == parcel.x0()) { return p.offset(-1, 0, 0); }
        if (p.x() == parcel.x1()) { return p.offset(1, 0, 0); }
        if (p.z() == parcel.z0()) { return p.offset(0, 0, -1); }
        if (p.z() == parcel.z1()) { return p.offset(0, 0, 1); }
        Vec3i t = f.target();
        return new Vec3i(2 * p.x() - t.x(), p.y(), 2 * p.z() - t.z());
    }

    /** A lit light, as the chunks' light lists hold them, on the claimed storey. */
    private static boolean lit(World w, ParcelPlan parcel, long floor) {
        for (int cx = Math.floorDiv(parcel.x0(), Chunk.SX); cx <= Math.floorDiv(parcel.x1(), Chunk.SX); cx++) {
            for (int cz = Math.floorDiv(parcel.z0(), Chunk.SZ); cz <= Math.floorDiv(parcel.z1(), Chunk.SZ); cz++) {
                Chunk c = w.getChunk(cx, cz);
                if (c == null) { continue; }
                for (int[] light : c.lights) {
                    UrbanIndoorQueries.Location at = UrbanIndoorQueries.location(w, light[0], light[1], light[2]);
                    if (at != null && at.floor().id() == floor) { return true; }
                }
            }
        }
        return false;
    }
}

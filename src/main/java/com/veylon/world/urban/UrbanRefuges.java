package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The refuges a layout-fourteen city's survivor has claimed (urban milestone 18): at most
 * {@value #MAX_CLAIMS}, each one storey of one building, named by its stable floor id, never by a
 * plan object or a position that could drift. A claim keeps the cell its chosen bed is slept in,
 * and one claim is home, where the survivor wakes after death.
 *
 * <p>A claim holds the storey's rooms as the plan draws them, whatever is later broken, built or
 * moved inside them: claims survive every edit, and a bed taken away only leaves a sleeping cell
 * that respawn checks against the live world. Nothing about a claim changes who owns anything
 * else; storage inside a claimed storey stays as the container ledger records it.
 *
 * <p>Persistent through {@code urban.refuges} version 2; a new or loaded world gets a fresh ledger
 * with its world. Main thread only.
 */
public final class UrbanRefuges {
    /** Most refuges a survivor keeps at once (DESIGN §10). */
    public static final int MAX_CLAIMS = 3;

    /** One claimed storey. */
    public static final class Claim {
        public final long floor;
        /** Where the claim's bed is slept in, or null when none was chosen. */
        public Vec3i sleep;
        /** The game minute it was claimed. */
        public final double claimedMinute;

        public Claim(long floor, Vec3i sleep, double claimedMinute) {
            this.floor = UrbanIds.requireKind(floor, UrbanIds.Kind.FLOOR);
            this.sleep = sleep;
            this.claimedMinute = claimedMinute;
        }

        /** The claimed building's id. */
        public long building() {
            return UrbanIds.parent(floor);
        }
    }

    private final List<Claim> claims = new ArrayList<>();
    private long home = UrbanIds.NONE;
    private boolean sectionRestored;

    /** Every claim, in the order they were made. */
    public List<Claim> claims() {
        return Collections.unmodifiableList(claims);
    }

    public int size() {
        return claims.size();
    }

    public boolean full() {
        return claims.size() >= MAX_CLAIMS;
    }

    /** The claim on a storey, or null. */
    public Claim claim(long floor) {
        for (Claim c : claims) {
            if (c.floor == floor) { return c; }
        }
        return null;
    }

    /** The claim the survivor wakes in, or null when there is none. */
    public Claim home() {
        return claim(home);
    }

    public long homeFloor() {
        return home;
    }

    /** The claim whose storey holds a loaded cell, or null. Reads plans and the loaded column only. */
    public Claim at(World w, int x, int y, int z) {
        if (claims.isEmpty()) { return null; }
        UrbanIndoorQueries.Location here = UrbanIndoorQueries.location(w, x, y, z);
        return here == null ? null : claim(here.floor().id());
    }

    /**
     * Claims a storey and makes it home.
     *
     * @throws IllegalStateException when every claim is taken or the storey is already claimed
     */
    public Claim add(long floor, Vec3i sleep, double minute) {
        if (full() || claim(floor) != null) { throw new IllegalStateException("cannot claim " + UrbanIds.format(floor)); }
        Claim c = new Claim(floor, sleep, minute);
        claims.add(c);
        home = floor;
        return c;
    }

    /** Gives a claim up; when it was home, the most recent other claim becomes home. */
    public void remove(long floor) {
        claims.removeIf(c -> c.floor == floor);
        if (home == floor) {
            home = claims.isEmpty() ? UrbanIds.NONE : claims.getLast().floor;
        }
    }

    /** Makes a claim home. */
    public void setHome(long floor) {
        if (claim(floor) == null) { throw new IllegalArgumentException("not a claim: " + UrbanIds.format(floor)); }
        home = floor;
    }

    public boolean sectionRestored() {
        return sectionRestored;
    }

    /** Load only: the validated claims of {@code urban.refuges} version 2 and their home. */
    public void restore(List<Claim> saved, long savedHome) {
        if (saved.size() > MAX_CLAIMS) { throw new IllegalArgumentException("too many claims"); }
        claims.clear();
        claims.addAll(saved);
        home = savedHome;
        sectionRestored = true;
    }
}

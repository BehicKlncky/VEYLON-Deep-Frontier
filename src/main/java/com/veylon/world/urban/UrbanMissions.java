package com.veylon.world.urban;

/**
 * Layout sixteen's missions ledger (urban milestone 20): what the survivor has learned and done
 * toward restoring the city's communications, kept as facts. The stage a player sees is derived from
 * them, never stored, so a fact learned out of order (the relay found before anyone spoke of it, a
 * part picked up before the rumour) counts the moment it is true.
 *
 * <p>Persistent through {@code urban.missions}; a new world and a load each start with a fresh
 * ledger, because the ledger belongs to the world's {@link UrbanWorldState}.
 */
public final class UrbanMissions {
    /** Who told the survivor the relay's codes. Codes are what the save stores. */
    public static final int CODES_NONE = 0, CODES_STRONGHOLD = 1, CODES_REFUGE = 2;
    /** The parts fitted to the relay, as bits; both together repair it. */
    public static final int RELAY_ANTENNA = 1, RELAY_CELLS = 2, RELAY_REPAIRED = RELAY_ANTENNA | RELAY_CELLS;
    /**
     * Seconds a powered radio must transmit through the repaired relay: with the relay's codes, and
     * without them, when the radio has to hunt the band for a channel the relay will carry.
     */
    public static final float BROADCAST_SECONDS = 60f, SCAN_SECONDS = 240f;

    /** The communications objective's facts (DESIGN §3.3, C1 to C5). */
    public static final class Comms {
        /** C1: someone spoke of the relay, or the survivor found it. */
        public boolean rumoured;
        /** The site whose people spoke of it, or {@link UrbanIds#NONE}. */
        public long rumourFrom = UrbanIds.NONE;
        /** The survivor has stood at the relay itself, so its place is known, not rumoured. */
        public boolean relayFound;
        /** C2: the parts recovered at least once, as {@link CommsComponents.Kind} bits. */
        public int recovered;
        /** C3: who gave the relay's codes ({@link #CODES_NONE} until someone did), and their site. */
        public int codes = CODES_NONE;
        public long codesFrom = UrbanIds.NONE;
        /** C4: the parts fitted to the relay ({@link #RELAY_ANTENNA}, {@link #RELAY_CELLS}). */
        public int installed;
        /** C4: a radio workstation has been put down on a building's wiring. */
        public boolean radioBuilt;
        /** C5: seconds transmitted through the repaired relay so far; kept when the power fails. */
        public float broadcast;
        /** C5: the call went out and the city answered, at {@link #completeMinute}; -1 before. */
        public boolean complete;
        public double completeMinute = -1;

        /**
         * The number of part kinds recovered, a part fitted to the relay or built into a radio counting
         * as recovered.
         */
        public int partsRecovered() {
            int fitted = ((installed & RELAY_ANTENNA) != 0 ? CommsComponents.Kind.ANTENNA.bit : 0)
                    | ((installed & RELAY_CELLS) != 0 ? CommsComponents.Kind.CELLS.bit : 0)
                    | (radioBuilt ? CommsComponents.Kind.TRANSMITTER.bit : 0);
            return Integer.bitCount((recovered | fitted) & CommsComponents.ALL);
        }

        /** Both parts are fitted: the relay will carry a call. */
        public boolean relayRepaired() {
            return (installed & RELAY_REPAIRED) == RELAY_REPAIRED;
        }

        /** Seconds the broadcast needs: shorter with the relay's codes. */
        public float required() {
            return codes == CODES_NONE ? SCAN_SECONDS : BROADCAST_SECONDS;
        }
    }

    // ------------------------------------------------------------------
    // Missions
    // ------------------------------------------------------------------

    /** Missions in hand at once: the journal's bound (DESIGN §10, D-30). */
    public static final int MAX_ACTIVE = 4;
    /** Mission records kept, finished and failed ones included; past it nobody offers another. */
    public static final int MAX_RECORDS = 256;

    /** What a mission asks; the code is what saves store, the title what the journal shows. */
    public enum Kind {
        /** A camp asks for medicine from a named clinic's stores. */
        CLINIC_SUPPLIES(1, "Medicine from the clinic", 1, 12f),
        /** A camp asks the survivor to find and help people hiding below a building. */
        HIDDEN_AID(2, "The people below", 3, 10f),
        /** A camp asks for clean water: a purifier working on its own building's wiring. */
        CAMP_UTILITY(3, "Clean water for the camp", 2, 15f),
        /** A camp or stronghold asks whether the old relay mast still stands. */
        LANDMARK(4, "The old relay mast", 1, 8f),
        /** A stronghold keeps a transmitter module, for a price or for a friend. */
        STRONGHOLD_COMPONENT(5, "The Wardens' transmitter", 1, 5f);

        public final int code;
        public final String title;
        /** How much evidence finishes it: items, litres or one deed. */
        public final int required;
        /** The trust its giver gives once, on its report. */
        public final float reward;

        Kind(int code, String title, int required, float reward) {
            this.code = code;
            this.title = title;
            this.required = required;
            this.reward = reward;
        }

        public static Kind fromCode(int code) {
            for (Kind k : values()) {
                if (k.code == code) { return k; }
            }
            return null;
        }
    }

    /** Where a mission stands. */
    public enum Status {
        ACTIVE(1), READY(2), COMPLETED(3), FAILED(4);

        public final int code;

        Status(int code) {
            this.code = code;
        }

        public static Status fromCode(int code) {
            for (Status s : values()) {
                if (s.code == code) { return s; }
            }
            return null;
        }
    }

    /** Why a mission failed; explicit, logged once, and kept. */
    public enum Failure {
        NONE(0, ""), GIVER_GONE(1, "no one is left to report to"), GIVER_HOSTILE(2, "they want nothing more to do with you"),
        TARGET_GONE(3, "what it asked for is gone"), TARGET_DEAD(4, "the people it was for are dead"),
        BETRAYED(5, "they will never trust anyone you send"), THEFT(6, "you took it without leave");

        public final int code;
        public final String reason;

        Failure(int code, String reason) {
            this.code = code;
            this.reason = reason;
        }

        public static Failure fromCode(int code) {
            for (Failure f : values()) {
                if (f.code == code) { return f; }
            }
            return null;
        }
    }

    /**
     * One mission accepted from a giver: the kind, the stable id of what it is about (a clinic, a
     * refuge, the camp's building, the relay's building or a stronghold's container) and the site that
     * gave it. A kind and target are asked for once in a world, whoever asks.
     */
    public static final class Mission {
        public final Kind kind;
        public final long target;
        public final long giver;
        public final double acceptedMinute;
        public Status status = Status.ACTIVE;
        /** Evidence so far, up to {@link Kind#required}; only deeds done after accepting count. */
        public float progress;
        public Failure failure = Failure.NONE;
        /** Its reward was given; a completed mission always has been, once. */
        public boolean rewarded;

        public Mission(Kind kind, long target, long giver, double acceptedMinute) {
            this.kind = kind;
            this.target = target;
            this.giver = giver;
            this.acceptedMinute = acceptedMinute;
        }

        /** In hand: accepted and neither finished nor failed. */
        public boolean active() {
            return status == Status.ACTIVE || status == Status.READY;
        }

        /** The stable text id of the mission. */
        public String id() {
            return "urban:mission:" + kind.code + ":" + UrbanIds.format(target);
        }
    }

    private final java.util.List<Mission> missions = new java.util.ArrayList<>();

    /** Every mission record, in the order they were accepted. */
    public java.util.List<Mission> missions() {
        return java.util.Collections.unmodifiableList(missions);
    }

    /** Missions in hand. */
    public int activeCount() {
        int n = 0;
        for (Mission m : missions) {
            if (m.active()) { n++; }
        }
        return n;
    }

    /** The record of a kind and target, or null when it was never accepted. */
    public Mission find(Kind kind, long target) {
        for (Mission m : missions) {
            if (m.kind == kind && m.target == target) { return m; }
        }
        return null;
    }

    /** Whether another mission may be accepted: room in the journal and in the records. */
    public boolean canAccept() {
        return activeCount() < MAX_ACTIVE && missions.size() < MAX_RECORDS;
    }

    /**
     * Records a newly accepted mission.
     *
     * @throws IllegalStateException with no room, or for a kind and target already asked for
     */
    public Mission accept(Kind kind, long target, long giver, double minute) {
        if (!canAccept() || find(kind, target) != null) {
            throw new IllegalStateException("no room for, or a repeat of, " + kind + " " + UrbanIds.format(target));
        }
        Mission m = new Mission(kind, target, giver, minute);
        missions.add(m);
        return m;
    }

    public final Comms comms = new Comms();
    private boolean sectionRestored;

    /** Whether a load restored this ledger from its section. */
    public boolean sectionRestored() {
        return sectionRestored;
    }

    /** Load only: the validated objective and mission records of {@code urban.missions}. */
    public void restore(Comms saved, java.util.List<Mission> records) {
        missions.clear();
        missions.addAll(records);
        restore(saved);
    }

    /** The validated objective of {@code urban.missions}. */
    private void restore(Comms saved) {
        comms.rumoured = saved.rumoured;
        comms.rumourFrom = saved.rumourFrom;
        comms.relayFound = saved.relayFound;
        comms.recovered = saved.recovered;
        comms.codes = saved.codes;
        comms.codesFrom = saved.codesFrom;
        comms.installed = saved.installed;
        comms.radioBuilt = saved.radioBuilt;
        comms.broadcast = saved.broadcast;
        comms.complete = saved.complete;
        comms.completeMinute = saved.completeMinute;
        sectionRestored = true;
    }
}

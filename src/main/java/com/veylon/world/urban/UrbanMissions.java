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

    public final Comms comms = new Comms();
    private boolean sectionRestored;

    /** Whether a load restored this ledger from its section. */
    public boolean sectionRestored() {
        return sectionRestored;
    }

    /** Load only: the validated objective of {@code urban.missions}. */
    public void restore(Comms saved) {
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

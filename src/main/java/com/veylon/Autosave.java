package com.veylon;

import com.veylon.ai.Pursuit;
import com.veylon.entity.Player;
import com.veylon.save.AutosaveSettings;

import java.util.Locale;

/**
 * Saves the live world into its own slot by itself (0.9.1 milestone 05, decision D-03): after
 * waking from sleep, after claiming a refuge at a bed, and once a set interval of play has passed
 * since the slot was last written. An autosave is an ordinary save through
 * {@link SaveSession#saveActive}, so it rotates the slot's backups like F5 and the save it replaces
 * stays recoverable.
 *
 * <p>Asking and writing are apart. {@link SleepSystem} and {@link RefugeSystem} only
 * {@linkplain #request ask}; the interval runs on the frames that advance the world. Once a frame
 * ends with an autosave due, {@link #frame} writes it at the first moment that is safe
 * ({@link #blocker}): a blocked autosave waits, however long, and is never dropped. The HUD shows
 * "SAVING..." for {@value #LEAD_SECONDS} s before the write, which stalls the frame on the main
 * thread, so the player sees the stall announced rather than a frozen picture.
 *
 * <p>A session autosaves only when a player started it: {@link AutomatedRunDriver} turns it off for
 * every scripted scene, capture, frontend route and the release smoke unless
 * {@code VEYLON_AUTOSAVE=on} asks for it, and a game that was never run (every headless test)
 * autosaves only once a test {@linkplain #permit permits} it. It never writes before the live world
 * belongs to a slot ({@link SaveSession#bound}).
 */
public final class Autosave {

    /** What asked for an autosave. */
    public enum Trigger {
        /** The player woke from sleep, however the night ended. */
        WAKE,
        /** The player claimed a refuge at a bed. */
        CLAIM,
        /** The interval of play has passed since the slot was last written. */
        TIMER
    }

    /** Why a due autosave waits; {@link #blocker} reports the first that applies, in this order. */
    public enum Blocker {
        /** No live world is being played. */
        NOT_PLAYING,
        /** The player is dead, or the death card is up. */
        DEAD,
        /** An end card is up. */
        END_CARD,
        /** The pause menu or a screen opened from it, or the simulation pause, holds the world. */
        PAUSED,
        /** The player is asleep; waking asks for its own autosave. */
        SLEEPING,
        /** A timed search of a container is under way. */
        SEARCHING,
        /** A screen is open over play: a container, a trade, the pack, crafting or the map. */
        SCREEN_OPEN,
        /** The player is on fire. */
        BURNING,
        /** The player is off the ground: falling, jumping, climbing, flying or swimming. */
        AIRBORNE,
        /** A body is still tumbling, which a save would settle where it is. */
        BODIES_FALLING,
        /** Something is going for the player ({@link Pursuit}). */
        PURSUED,
        /** The player was hurt within the last {@value #GRACE_SECONDS} seconds. */
        HURT
    }

    /** Seconds of play after a hit or a burn during which nothing autosaves. */
    public static final float GRACE_SECONDS = 10f;
    /** Seconds "SAVING..." shows before the write. */
    public static final float LEAD_SECONDS = 0.35f;

    private final Game game;
    private final SaveSession session;
    private AutosaveSettings settings = new AutosaveSettings();
    /** A player's session, or automation that asked for autosaves. */
    private boolean permitted;
    /** A wake or a claim waiting for a safe moment; null when none. */
    private Trigger requested;
    /** Real seconds of play since the live world's slot was last written. */
    private double sinceSave;
    /** Seconds left of the damage grace. */
    private float grace;
    /** Seconds "SAVING..." has shown, or negative while it is not shown. */
    private float lead = -1;
    /** The blocker last reported, so the console names each wait once. */
    private Blocker waitingFor;
    private int written;
    private double lastMillis = -1;

    Autosave(Game game, SaveSession session) {
        this.game = game;
        this.session = session;
    }

    // ------------------------------------------------------------------
    // The session and the player's settings
    // ------------------------------------------------------------------

    /**
     * Called once when the game starts: reads the player's settings and decides whether this session
     * may autosave at all.
     *
     * @param automated whether any {@code VEYLON_*} scene, capture, route or smoke run drives it
     * @param override  {@code VEYLON_AUTOSAVE}: "on" or "off", or null to follow {@code automated}
     */
    void startSession(boolean automated, String override) {
        settings = AutosaveSettings.loadOrDefaults();
        permitted = permitted(automated, override);
        if (automated) {
            System.out.println("[autosave] automated session: autosave " + (permitted ? "on" : "off")
                    + (permitted ? "" : " (VEYLON_AUTOSAVE=on turns it on)"));
        }
    }

    /** A player's session autosaves; an automated one only when {@code VEYLON_AUTOSAVE=on} asks. */
    static boolean permitted(boolean automated, String override) {
        if (override == null || override.isBlank()) {
            return !automated;
        }
        return switch (override.trim().toLowerCase(Locale.ROOT)) {
            case "on" -> true;
            case "off" -> false;
            default -> throw new IllegalArgumentException("VEYLON_AUTOSAVE must be on or off: " + override);
        };
    }

    /** The explicit switch for a test or a scene: whether this session may autosave. */
    void permit(boolean permitted) {
        this.permitted = permitted;
    }

    public boolean permitted() {
        return permitted;
    }

    /** The settings the options screen edits; the live object, read every frame. */
    public AutosaveSettings settings() {
        return settings;
    }

    /** Whether autosaves happen now: permitted, turned on and the live world in a slot. */
    public boolean active() {
        return permitted && settings.enabled && session.bound();
    }

    // ------------------------------------------------------------------
    // Asking, and the slot being written
    // ------------------------------------------------------------------

    /** A wake or a claim asks for an autosave; ignored while autosaves are off. */
    void request(Trigger trigger) {
        if (active() && requested == null) {
            requested = trigger;
        }
    }

    /** Any save into the live world's slot, F5 included, starts the interval again. */
    void saved() {
        requested = null;
        sinceSave = 0;
        lead = -1;
        waitingFor = null;
    }

    /** A new or loaded world: nothing is asked for, and the interval and the grace start over. */
    void reset() {
        saved();
        grace = 0;
    }

    /** QA's staged timer: the play counted since the slot was last written. */
    void stageElapsed(double seconds) {
        sinceSave = seconds;
    }

    // ------------------------------------------------------------------
    // The frame
    // ------------------------------------------------------------------

    /**
     * The end of a frame: counts the play, keeps the damage grace, and writes a due autosave once it
     * is safe and "SAVING..." has shown. Frames that do not advance the world count nothing.
     */
    void frame(float dt) {
        if (!game.simulates()) {
            lead = -1;
            return;
        }
        sinceSave += dt;
        Player p = game.player;
        grace = p.damageFlash > 0 ? GRACE_SECONDS : Math.max(0, grace - dt);
        if (!active()) {
            requested = null;
            lead = -1;
            return;
        }
        Trigger due = due();
        if (due == null) {
            return;
        }
        Blocker blocker = blocker();
        if (blocker != null) {
            if (blocker != waitingFor) {
                System.out.println("[autosave] " + name(due) + " waits: " + name(blocker));
            }
            waitingFor = blocker;
            lead = -1;
            return;
        }
        waitingFor = null;
        if (lead < 0) {
            lead = 0; // "SAVING..." is drawn from this frame on
            return;
        }
        lead += dt;
        if (lead >= LEAD_SECONDS) {
            write(due);
        }
    }

    /** The autosave due now: a wake or a claim first, else the interval's; null when none is. */
    public Trigger due() {
        if (requested != null) {
            return requested;
        }
        return sinceSave >= settings.intervalSeconds() ? Trigger.TIMER : null;
    }

    /** Why an autosave would wait at this moment, or null when it is safe. */
    public Blocker blocker() {
        Player p = game.player;
        if (game.world == null || p == null) {
            return Blocker.NOT_PLAYING;
        }
        if (game.appState == Game.AppState.DEATH || p.dead) {
            return Blocker.DEAD;
        }
        if (game.appState == Game.AppState.VICTORY) {
            return Blocker.END_CARD;
        }
        if (game.appState != Game.AppState.PLAYING) {
            return Blocker.NOT_PLAYING;
        }
        if (game.uiMode.pausesSimulation() || game.simPaused) {
            return Blocker.PAUSED;
        }
        if (game.sleeping) {
            return Blocker.SLEEPING;
        }
        if (game.crateSearching()) {
            return Blocker.SEARCHING;
        }
        if (game.uiMode != Game.UiMode.NONE) {
            return Blocker.SCREEN_OPEN;
        }
        if (game.combustion.isBurning(p)) {
            return Blocker.BURNING;
        }
        if (!p.onGround || p.abilities.flying()) {
            return Blocker.AIRBORNE;
        }
        if (game.ragdolls.liveCount() > 0 || game.fragments.liveCount() > 0) {
            return Blocker.BODIES_FALLING;
        }
        if (Pursuit.hunted(game)) {
            return Blocker.PURSUED;
        }
        return grace > 0 ? Blocker.HURT : null;
    }

    private void write(Trigger due) {
        int slot = session.activeSlot();
        long start = System.nanoTime();
        boolean ok = session.saveActive();
        lastMillis = (System.nanoTime() - start) / 1e6;
        // Written or not, this autosave is spent: a failed one tries again after the next interval.
        saved();
        if (ok) {
            written++;
        }
        game.log(ok ? savedLine(slot) : failedLine(slot));
        System.out.println(String.format(Locale.ROOT, "[autosave] slot=%d trigger=%s ok=%b ms=%.1f",
                slot, name(due), ok, lastMillis));
    }

    // ------------------------------------------------------------------
    // What the HUD and the QA read
    // ------------------------------------------------------------------

    /** Whether the HUD shows "SAVING..." this frame. */
    public boolean announcing() {
        return lead >= 0;
    }

    /** Autosaves written in this session. */
    public int written() {
        return written;
    }

    /** Milliseconds the last autosave took, or negative before the first. */
    public double lastMillis() {
        return lastMillis;
    }

    /** Real seconds of play since the live world's slot was last written. */
    public double sinceSave() {
        return sinceSave;
    }

    /** The log line after an autosave. */
    static String savedLine(int slot) {
        return "Autosaved to slot " + slot + ".";
    }

    /** The log line when an autosave failed; the console says why. */
    static String failedLine(int slot) {
        return "Could not autosave to slot " + slot + ".";
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}

package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSlots;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanIds;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;

/**
 * 0.9.1 milestone 05 ({@code urban_autosave}): every autosave trigger in a city, played with
 * ordinary controls into slot 1 of an isolated data directory. The new city is saved into the slot
 * as NEW FRONTIER saves one; then crouch and F on the bed of the abandoned flat beside the starter
 * refuge claims it, and the claim's autosave follows; at night F on that bed sleeps until morning,
 * and the waking's autosave follows; a staged timer runs out and its autosave follows; and in
 * Survival a staged infected keeps hunting the player while the next timer runs out, which holds
 * that autosave until the body is taken away. Each autosave is captured with "SAVING..." up and
 * again with its log line, and F9 then opens what the last one wrote.
 *
 * <p>It refuses to run without an isolated data directory or without {@code VEYLON_AUTOSAVE=on},
 * the switch that lets automation autosave at all. Staged: poses between legs, the clock (forward),
 * the timer and the hunting body; everything else is the frame step's own.
 */
final class AutosaveQaScene extends UrbanPeopleScene {
    static final double DEADLINE_SECONDS = 240;
    static final String CLAIM_PROMPT = "[F] Rest here  (crouch: claim these rooms as a refuge";
    static final String SLEEP_PROMPT = "[F] Sleep in your refuge";
    /** Seconds before the interval ends that a staged timer is set to. */
    static final double STAGED_LEFT = 4;

    private BuildingPlan flat;
    private BuildingPlan.Fixture bed;
    private boolean passed;
    private final List<String> written = new ArrayList<>();

    AutosaveQaScene(Game game) {
        super(game);
    }

    @Override protected String tag() { return "urban-autosave"; }

    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }

    /** The milliseconds of each autosave written, in order, with its trigger. */
    List<String> written() { return written; }

    void stage() {
        if (!AppPaths.isolatedFromPlayerData()) {
            throw new IllegalStateException("urban_autosave writes into the save slot: run it with an isolated data directory");
        }
        if (!game.saves.autosave().permitted()) {
            throw new IllegalStateException("urban_autosave needs VEYLON_AUTOSAVE=on: automation does not autosave otherwise");
        }
        World world = game.world;
        if (!RefugeSystem.applies(world)) {
            throw new IllegalStateException("urban_autosave needs a city with refuges");
        }
        flat = abandonedFlat(world);
        bed = flat.fixtures().stream()
                .filter(f -> UrbanIds.floorIndex(f.id()) == 1 && RefugeSystem.isBed(f.block()))
                .min(Comparator.comparingLong(BuildingPlan.Fixture::id)).orElseThrow();
        game.saves.autosave().settings().enabled = true;
        if (!game.saves.startIn(1)) {
            throw new IllegalStateException("the new city could not be saved into slot 1");
        }
        Inventory inv = game.player.inventory;
        inv.clear();
        inv.set(7, new ItemStack(ItemType.IRON_SPEAR, 1));
        inv.add(ItemType.DRIED_BERRY, 6);
        inv.add(ItemType.WATERSKIN_CLEAN, 2);
        System.out.println("[urban-autosave] stage seed=" + world.seed + " generator=" + world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT) + " slot=1 interval="
                + game.saves.autosave().settings().intervalMinutes() + "min flat=" + flat.template() + " bed="
                + bed.position() + " evidence=walked(W,F,LeftCtrl,Esc,F9,mouse-look);"
                + " staged-poses-between-legs,staged-clock(forward),staged-timer"
                + (survival() ? ",staged-infected(hunting)" : "") + ",staged-kit(spear,food,water); normal-simulation");
        Vec3i approach = flat.approaches().getFirst();
        add(new Clock("day", 10 * 60), new Pose(approach, bed.position()), new Wait("stream", 1f),
                new Walk(bed.target()), new Claim(), new Autosaved("01-claim", Autosave.Trigger.CLAIM),
                new Clock("night", 23 * 60 + 15), new Sleep(), new Autosaved("03-wake", Autosave.Trigger.WAKE),
                new StageTimer(), new Autosaved("04-timer", Autosave.Trigger.TIMER));
        if (survival()) {
            add(new Hunted(), new Autosaved("06-after-the-pursuit", Autosave.Trigger.TIMER));
        }
        add(new Reopen(), new Report());
        staged = true;
    }

    private boolean survival() { return game.gameMode() == GameMode.SURVIVAL; }

    /** The abandoned apartment with a bed on its first floor nearest the starter refuge. */
    private static BuildingPlan abandonedFlat(World world) {
        Vec3i home = world.urban.refugeAnchor;
        BuildingPlan best = null;
        long bestD = Long.MAX_VALUE;
        DistrictPlan district = world.urban.regionPlan(0, 0);
        for (ParcelPlan parcel : district.parcels()) {
            if (parcel.use() != ParcelPlan.Use.RESIDENTIAL) { continue; }
            BuildingPlan b = world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (b == null || b.family() != BuildingPlan.Family.APARTMENT || b.variant() == BuildingPlan.Variant.OCCUPIED
                    || b.fixtures().stream().noneMatch(f -> UrbanIds.floorIndex(f.id()) == 1 && RefugeSystem.isBed(f.block()))) {
                continue;
            }
            long dx = parcel.x0() - home.x(), dz = parcel.z0() - home.z();
            if (dx * dx + dz * dz < bestD) {
                bestD = dx * dx + dz * dz;
                best = b;
            }
        }
        if (best == null) { throw new IllegalStateException("no abandoned flat with a bed in the starter's district"); }
        return best;
    }

    private String prompt() {
        return game.interactPrompt == null ? "" : game.interactPrompt;
    }

    // ------------------------------------------------------------------
    // The triggers
    // ------------------------------------------------------------------

    /** Crouch and F on the flat's bed: the flat is claimed and asks for its autosave. */
    private final class Claim extends Task {
        private Object mark;

        Claim() { super("claim the flat at its bed"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            hold(GLFW_KEY_LEFT_CONTROL, true);
            lookAtBlock(bed.position(), .4f);
            if (age < .5f) { return false; }
            if (mark == null) {
                if (!prompt().startsWith(CLAIM_PROMPT)) {
                    if (age > 6) { throw fail("the bed reads '" + prompt() + "'"); }
                    return false;
                }
                mark = logMark();
                press(GLFW_KEY_F);
                return false;
            }
            if (!loggedSince(mark, "as a refuge")) {
                if (age > 6) { throw fail("the flat was not claimed: " + loggedAfter(mark)); }
                return false;
            }
            hold(GLFW_KEY_LEFT_CONTROL, false);
            deeds.add("claimed: " + loggedAfter(mark).getLast());
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** F on the claimed bed at night, then the night until the sleeper is up. */
    private final class Sleep extends Task {
        private Object mark;
        private boolean slept;
        private int before;

        Sleep() { super("sleep in the claimed flat"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            hold(GLFW_KEY_LEFT_CONTROL, false);
            if (mark == null) {
                lookAtBlock(bed.position(), .4f);
                if (age < .6f) { return false; }
                if (!prompt().startsWith(SLEEP_PROMPT)) {
                    if (age > 6) { throw fail("at night the bed reads '" + prompt() + "'"); }
                    return false;
                }
                mark = logMark();
                before = game.saves.autosave().written();
                press(GLFW_KEY_F);
                return false;
            }
            if (!slept) {
                if (!game.sleeping) {
                    if (age > 4) { throw fail("no sleep: " + loggedAfter(mark)); }
                    return false;
                }
                slept = true;
                capture("02-asleep");
                return false;
            }
            if (game.sleeping) {
                if (game.saves.autosave().written() > before) { throw fail("an autosave in the night"); }
                if (age > 90) { throw fail("the night never ended"); }
                return false;
            }
            deeds.add("woke: " + loggedAfter(mark).getLast());
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** The staged timer: a few seconds of the interval left. */
    private final class StageTimer extends Task {
        StageTimer() { super("stage the timer"); }

        @Override boolean step(float dt) {
            Autosave autosave = game.saves.autosave();
            autosave.stageElapsed(autosave.settings().intervalSeconds() - STAGED_LEFT);
            System.out.println("[urban-autosave] staged-timer left=" + STAGED_LEFT + "s of "
                    + autosave.settings().intervalMinutes() + "min");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * Waits for one autosave of a trigger: "SAVING..." must show before it is written, the log must
     * say so, and each is captured twice. A threat may interrupt the wait; the autosave then waits too.
     */
    private final class Autosaved extends Task {
        private final String label;
        private final Autosave.Trigger trigger;
        private int before = -1;
        private Object mark;
        private boolean announced;
        private float after = -1;

        Autosaved(String label, Autosave.Trigger trigger) {
            super("autosave " + label);
            this.label = label;
            this.trigger = trigger;
        }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            Autosave autosave = game.saves.autosave();
            if (before < 0) {
                before = autosave.written();
                mark = logMark();
            }
            if (after >= 0) {
                after += dt;
                if (after < .8f) { return false; } // the log line drawn before the capture
                capture(label + "-autosaved");
                return true;
            }
            if (autosave.written() == before) {
                if (autosave.announcing() && !announced) {
                    if (autosave.due() != trigger) { throw fail(label + ": announced " + autosave.due() + ", not " + trigger); }
                    announced = true;
                    capture(label + "-saving");
                }
                if (age > 30) {
                    throw fail(label + ": no autosave; due=" + autosave.due() + " waits on " + autosave.blocker());
                }
                return false;
            }
            if (!announced) { throw fail(label + ": written with no SAVING... shown"); }
            if (!loggedSince(mark, Autosave.savedLine(1))) { throw fail(label + ": the log reads " + loggedAfter(mark)); }
            written.add(trigger.name().toLowerCase(Locale.ROOT) + "=" + String.format(Locale.ROOT, "%.1fms", autosave.lastMillis()));
            timeline.add(String.format(Locale.ROOT, "%.0f:%s", elapsed, written.getLast()));
            after = 0;
            return false;
        }
    }

    /**
     * Survival only: a staged infected hunts the player, so the timer that runs out meanwhile holds
     * its autosave; the body is taken away (a staged body is no encounter record a save may hold)
     * once the autosave has waited a few seconds.
     */
    private final class Hunted extends Task {
        private Npc body;
        private int before;
        private float held;
        private boolean captured;

        Hunted() { super("hunted while the timer runs out"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            Autosave autosave = game.saves.autosave();
            if (body == null) {
                Vec3i at = huntingGround();
                body = game.entities.spawnNpc(game.world, NpcArchetype.VEILBOUND.displayName, at.x() + .5f, at.y() + .02f, at.z() + .5f);
                body.archetype = NpcArchetype.VEILBOUND;
                body.health = body.maxHealth = NpcArchetype.VEILBOUND.maxHealth;
                before = autosave.written();
                autosave.stageElapsed(autosave.settings().intervalSeconds() - 1);
                System.out.println("[urban-autosave] staged-infected hunting from " + at + " distance="
                        + String.format(Locale.ROOT, "%.1f", distanceTo(body)));
            }
            body.searchTimer = Math.max(body.searchTimer, 10); // it keeps hearing the player: staged
            body.lastKnown.set(game.player.pos);
            body.lastKnownAge = 0;
            if (autosave.written() > before) { throw fail("an autosave while hunted"); }
            if (autosave.due() == Autosave.Trigger.TIMER && autosave.blocker() == Autosave.Blocker.PURSUED) {
                held += dt;
            }
            if (held > 1.5f && !captured) {
                captured = true;
                capture("05-hunted-no-autosave");
            }
            if (held >= 4f) {
                game.entities.removeNpc(game, body);
                System.out.println("[urban-autosave] unstaged-infected after holding the autosave "
                        + String.format(Locale.ROOT, "%.1f", held) + "s");
                timeline.add(String.format(Locale.ROOT, "%.0f:held-while-hunted=%.1fs", elapsed, held));
                return true;
            }
            if (age > 20) { throw fail("the hunt never held the due autosave: due=" + autosave.due() + " blocker=" + autosave.blocker()); }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }

        /** A standable cell 12 to 20 blocks off, on the player's storey or the street below. */
        private Vec3i huntingGround() {
            Vec3i feet = feet();
            for (int r = 12; r <= 20; r++) {
                for (int[] d : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
                    for (int dy = 0; dy >= -8; dy--) {
                        int x = feet.x() + d[0], y = feet.y() + dy, z = feet.z() + d[1];
                        if (game.world.isColumnLoaded(x, z) && VoxelClearance.standable(game.world, x, y, z)) {
                            return new Vec3i(x, y, z);
                        }
                    }
                }
            }
            throw fail("no ground for the hunting body");
        }
    }

    /** F9 opens the slot: the world must hold exactly what the last autosave wrote. */
    private final class Reopen extends Task {
        private List<String> saved;
        private World before;
        private Object mark;

        Reopen() { super("F9 opens the last autosave"); }

        @Override boolean step(float dt) {
            if (saved == null) {
                saved = SaveAuditQaScene.durable(game);
                before = game.world;
                mark = logMark();
                press(GLFW_KEY_F9);
                return false;
            }
            if (game.world == before || !loggedSince(mark, SaveSession.loadedLine(1))) {
                if (age > 6) { throw fail("F9 did not open slot 1: " + loggedAfter(mark)); }
                return false;
            }
            List<String> loaded = SaveAuditQaScene.durable(game);
            if (!loaded.equals(saved)) { throw fail("the slot holds something else: saved " + saved + " loaded " + loaded); }
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            SaveSlots slots = game.saves.slots();
            boolean backups = Files.isRegularFile(slots.backup(1, 1)) && Files.isRegularFile(slots.backup(1, 2));
            if (!backups) { throw fail("the autosaves did not keep two backups"); }
            for (int slot = 2; slot <= SaveSlots.COUNT; slot++) {
                if (Files.exists(slots.current(slot))) { throw fail("slot " + slot + " was written"); }
            }
            passed = true;
            reported = true;
            releaseAll();
            System.out.println("[urban-autosave] pass=true mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                    + " seed=" + game.world.seed + " autosaves=" + String.join(",", written) + " backups=2 captures="
                    + captures + " " + String.join(" / ", deeds) + " timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }
    }
}

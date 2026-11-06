package com.veylon;

import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanMissions;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Urban package 25 ({@code save_audit}): a session begun from a save already in an isolated slot,
 * a genuine legacy world or the integrated city, loaded as F9 loads one. Then ordinary controls: a
 * walk to a stored container with W and mouse look, its screen opened with the use button (F would
 * serve a carcass or a person within reach first), or with no container near a short walk with W;
 * the pack opened with E and the map with M, each captured; then F5 and F9, after which the world
 * must hold exactly what F5 wrote. It refuses to run without an isolated data directory, since it reads and writes the slot.
 */
final class SaveAuditQaScene extends UrbanRouteScene {
    static final double DEADLINE_SECONDS = 150;

    private Vec3i container;
    private boolean passed;

    SaveAuditQaScene(Game game) {
        super(game);
    }

    @Override protected String tag() { return "save-audit"; }

    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }

    void stage() {
        if (!AppPaths.isolatedFromPlayerData()) {
            throw new IllegalStateException("save_audit reads and writes the save slot: run it with an isolated data directory");
        }
        if (!Files.exists(SaveSystem.SAVE_PATH)) {
            throw new IllegalStateException("save_audit needs a save in the isolated slot: " + SaveSystem.SAVE_PATH);
        }
        if (!SaveSystem.load(game)) { throw new IllegalStateException("the slot's save did not load"); }
        game.closeScreens();
        game.log("Game loaded.");
        game.appState = Game.AppState.PLAYING;
        container = nearestContainer();
        if (container == null) { System.out.println("[save-audit] no stored container within 24 blocks: a short walk instead"); }
        System.out.println("[save-audit] loaded slot=" + SaveSystem.SAVE_PATH.getFileName() + " generator="
                + game.world.generatorVersion + " seed=" + game.world.seed + " mode="
                + game.gameMode().name().toLowerCase(Locale.ROOT) + " pos=" + game.player.pos + " container=" + container
                + " evidence=walked(W,RMB,E,M,Esc,F5,F9,mouse-look;F-at-doors)");
        for (String line : durable()) { System.out.println("[save-audit] loaded " + line); }
        // E opens the pack, or in Creative the catalog that stands in for it.
        Game.UiMode pack = game.player.abilities.unlimitedItems() ? Game.UiMode.CREATIVE_CATALOG : Game.UiMode.INVENTORY;
        add(new Wait("settle", 1.5f), new Capture("01-loaded"));
        if (container == null) {
            add(new Stroll());
        } else {
            add(new Walk(beside(container)), new Screen("02-container", GLFW_MOUSE_BUTTON_RIGHT, Game.UiMode.CRATE));
        }
        add(new Screen("03-pack", GLFW_KEY_E, pack),
                new Screen("04-map", GLFW_KEY_M, Game.UiMode.MAP),
                new SaveAndLoad(), new Wait("after the load", 1.5f), new Capture("05-after-f9"), new Report());
        staged = true;
    }

    /** The stored container nearest the player at least two blocks off, within 24, the nearest, or null. */
    private Vec3i nearestContainer() {
        var p = game.player.pos;
        Comparator<Vec3i> near = Comparator.comparingDouble(c -> c.distSq(p.x, p.y, p.z));
        List<Vec3i> all = game.world.crateContents.keySet().stream()
                .filter(c -> c.distSq(p.x, p.y, p.z) <= 24 * 24 && hasSide(c)).sorted(near).toList();
        if (all.isEmpty()) { return null; }
        return all.stream().filter(c -> c.distSq(p.x, p.y, p.z) >= 4).findFirst().orElse(all.getFirst());
    }

    private boolean hasSide(Vec3i c) {
        return beside(c) != null;
    }

    /** A standing cell beside a container on its own level, nearest the player. */
    private Vec3i beside(Vec3i c) {
        Vec3i best = null;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Vec3i side = c.offset(d[0], 0, d[1]);
            if (VoxelClearance.standable(game.world, side.x(), side.y(), side.z())
                    && (best == null || side.distSq(game.player.pos.x, side.y(), game.player.pos.z)
                    < best.distSq(game.player.pos.x, best.y(), game.player.pos.z))) {
                best = side;
            }
        }
        return best;
    }

    /**
     * What a save must hold, without what moves on by itself between two frames: blocks, containers and
     * what they hold, the mode and its controls, and the city's or the wilderness's records.
     */
    List<String> durable() {
        List<String> out = new ArrayList<>();
        World w = game.world;
        out.add("world seed=" + w.seed + " generator=" + w.generatorVersion + " mode=" + game.gameMode()
                + " marked=" + game.creativeMarked() + " flying=" + game.player.abilities.flying()
                + " controls=" + game.daylightFrozen() + "/" + game.weatherLocked() + "/" + game.spawningPaused());
        out.add("edits=" + w.changedBlocks.size() + " crates=" + w.crateContents.size() + " fixtures=" + w.fixtures.size()
                + " pack=" + contents(game.player.inventory));
        w.crateContents.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Vec3i::x)
                .thenComparingInt(Vec3i::y).thenComparingInt(Vec3i::z)))
                .forEach(e -> out.add("crate " + e.getKey() + " " + contents(e.getValue())));
        if (w.urban == null) {
            out.add("settlements=" + w.settlements.size() + " beacon=" + w.beaconStage + " quest="
                    + (game.faction.quest == null ? "none" : game.faction.quest.instanceId + " " + game.faction.quest.status));
            return out;
        }
        var u = w.urban;
        long dead = game.entities.encounters.residents().stream().filter(r -> r.dead()).count();
        out.add("content=" + u.contentRevision() + " containers=" + u.containers.size() + " claims=" + u.refuges.size()
                + " home=" + u.refuges.homeFloor() + " tapped=" + u.utilities.tappedCount() + " devices="
                + u.utilities.deviceCount() + " damaged=" + u.fortifications.damagedCount() + " braces="
                + u.fortifications.braceCount());
        out.add("encounters=" + game.entities.encounters.size() + " dead=" + dead + " sites="
                + UrbanSiteDirector.sites(w).size() + " discovered=" + u.discovery.size() + " rumoured="
                + u.missions.comms.rumoured);
        for (UrbanMissions.Mission m : u.missions.missions()) {
            out.add("mission " + m.kind + " " + m.status + " rewarded=" + m.rewarded);
        }
        return out;
    }

    private static String contents(Inventory inv) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null) { out.append(out.isEmpty() ? "" : ",").append(s.type).append('x').append(s.count); }
        }
        return out.isEmpty() ? "empty" : out.toString();
    }

    /** Opens a screen with its key (the use button aimed at the container), captures it, then closes it. */
    private final class Screen extends Task {
        private final String label;
        private final int key;
        private final Game.UiMode mode;
        private int phase;

        Screen(String label, int key, Game.UiMode mode) {
            super("screen " + label);
            this.label = label;
            this.key = key;
            this.mode = mode;
        }

        @Override boolean step(float dt) {
            if (phase == 0) {
                // The use button acts on the aim the last frame computed: look first, use it once that has
                // settled. F would serve a carcass or a person within reach before the container.
                boolean use = key == GLFW_MOUSE_BUTTON_RIGHT;
                if (use) { lookAtBlock(container, .5f); }
                if (use && age < .3f) { return false; }
                if (use) { rightClick(); } else { press(key); }
                phase = 1;
                age = 0;
                return false;
            }
            if (phase == 1) {
                if (game.uiMode != mode) {
                    if (age > 3) {
                        var log = game.eventLog.all();
                        throw fail(label + ": the key did not open " + mode + " but " + game.uiMode + "; aimed at "
                                + (game.targetHit == null ? "nothing" : game.targetHit.type() + " " + game.targetHit.x()
                                + "," + game.targetHit.y() + "," + game.targetHit.z()) + "; last line "
                                + (log.isEmpty() ? "" : log.getLast()));
                    }
                    return false;
                }
                if (age < 2.5f) { return false; } // a search finishes and the screen settles before the capture
                capture(label);
                phase = 2;
                age = 0;
                return false;
            }
            if (phase == 2) {
                // The capture is drawn at the end of a frame: nothing closes until a few have passed.
                if (age < .4f) { return false; }
                // A screen opened by a key closes with it (the Creative catalog keeps Escape for itself);
                // the container's closes with Escape.
                press(key == GLFW_MOUSE_BUTTON_RIGHT ? GLFW_KEY_ESCAPE : key);
                phase = 3;
                age = 0;
                return false;
            }
            if (game.uiMode != Game.UiMode.NONE) {
                if (phase == 3 && age > 1) {
                    press(GLFW_KEY_ESCAPE);
                    phase = 4;
                } else if (phase == 4 && age > 2) {
                    // The Creative catalog reads its own keys in the render pass, which a headless frame has
                    // not; a native run closes it with E before this and never logs the note.
                    game.closeScreens();
                    timeline.add(String.format(Locale.ROOT, "%.0f:%s-closed-by-command", elapsed, label));
                }
                if (age > 6) { throw fail(label + ": neither its key nor Escape closed " + game.uiMode); }
                return false;
            }
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** W held for a moment along the current heading: the player must travel at least a block. */
    private final class Stroll extends Task {
        private float startX, startZ;

        Stroll() { super("stroll"); }

        @Override boolean step(float dt) {
            if (age <= dt) {
                startX = game.player.pos.x;
                startZ = game.player.pos.z;
                game.camera.pitch = 8;
            }
            if (age < 1.2f) {
                hold(GLFW_KEY_W, true);
                return false;
            }
            hold(GLFW_KEY_W, false);
            double moved = Math.hypot(game.player.pos.x - startX, game.player.pos.z - startZ);
            if (moved < 1) { throw fail("W moved the player only " + moved + " blocks"); }
            timeline.add(String.format(Locale.ROOT, "%.0f:strolled=%.1f", elapsed, moved));
            return true;
        }
    }

    /** F5, then F9: the loaded world must be a new one holding exactly what F5 wrote. */
    private final class SaveAndLoad extends Task {
        private int phase;
        private List<String> saved;
        private World before;

        SaveAndLoad() { super("F5 and F9"); }

        @Override boolean step(float dt) {
            String last = game.eventLog.all().isEmpty() ? "" : game.eventLog.all().getLast();
            switch (phase) {
                case 0 -> {
                    saved = durable();
                    before = game.world;
                    press(GLFW_KEY_F5);
                    phase = 1;
                }
                case 1 -> {
                    if (!last.endsWith("Game saved.")) {
                        if (age > 2) { throw fail("F5 did not save: " + last); }
                        return false;
                    }
                    press(GLFW_KEY_F9);
                    phase = 2;
                }
                default -> {
                    if (game.world == before || !last.endsWith("Game loaded.")) {
                        if (age > 6) { throw fail("F9 did not load: " + last); }
                        return false;
                    }
                    List<String> loaded = durable();
                    if (!loaded.equals(saved)) {
                        throw fail("F9 restored something other than F5 wrote: saved " + saved + " loaded " + loaded);
                    }
                    timeline.add(String.format(Locale.ROOT, "%.0f:f5-f9 lines=%d", elapsed, loaded.size()));
                    return true;
                }
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            passed = true;
            reported = true;
            releaseAll();
            System.out.println("[save-audit] pass=true generator=" + game.world.generatorVersion + " seed="
                    + game.world.seed + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                    + " timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }
    }
}

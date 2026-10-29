package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.ui.CraftingScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanUtilities;
import com.veylon.world.urban.UrbanUtilities.Device;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-19 utility tour ({@code VEYLON_SCENE=urban_utilities}) on a generator-18 world from
 * {@code VEYLON_GENERATOR=urban-dev}, in either game mode, played with ordinary controls (W, F, C, the
 * number keys, left Control, both mouse buttons, F5, F9 and mouse look through the production input
 * seam), in the starter refuge, the first claimed refuge and home:
 * <ol>
 *   <li>by day, at the kitchen's workbench, a generator rebuilt from salvage through the crafting
 *       screen's own command and put down in the far corner of the kitchen; two work lamps and a
 *       water purifier built the same way and put down in the kitchen, the bedroom and beside the
 *       kitchen tap, the lamps switched on and all of it dark;</li>
 *   <li>the wreck nearest the spawn that did not burn: its fuel tank taken apart with a pry tool,
 *       crouched, holding the attack button, for a fuel can (a Creative break takes nothing, so
 *       there the can is staged);</li>
 *   <li>the kitchen tap: two waterskins filled with the stale water left in the building's pipes;</li>
 *   <li>at night, the fuel can poured into the generator and the generator started: the lamps come
 *       on, it runs, and it is heard;</li>
 *   <li>the purifier switched on and found with its filter spent, the filter packed with fibre, its
 *       repair, the stale water poured in, treated on the generator's power while the pump draws up
 *       more of the building's, and a waterskin of clean water filled from it;</li>
 *   <li>a real threat: one Veilbound in the courtyard (staged and printed) hears the running
 *       generator through the wall and turns to it, and is taken away again (a Creative builder's
 *       generator is heard by nobody);</li>
 *   <li>the generator switched off, the refuge dark again; started once more and run until its tank
 *       is dry and it dies; its wear from the evening rebuilt with a spare part and the pry tool;</li>
 *   <li>F5 and F9: every device's switch, fuel, wear, filter and water, and the litres drawn from the
 *       building's pipes, the same after the load.</li>
 * </ol>
 * The poses between legs, the clock (only ever forward), the weather, one infected body and a kit of
 * salvage, a pry tool, fibre and empty waterskins are staged and printed as staged. No block is
 * staged. Exactly {@value #CAPTURES} labelled captures. Failures throw.
 */
final class UrbanUtilityQaScene extends UrbanPeopleScene {
    static final double DEADLINE_SECONDS = 900;
    static final int CAPTURES = 13;
    /** Waterskins the tour fills at the tap, and empty ones it brings. */
    static final int TAP_LITRES = 2, SKINS = 3;
    /** Spare parts kept back from the bench for the generator's rebuild. */
    static final int SPARE_FOR_REPAIR = 1;

    private UrbanUtilityTourPlan plan;
    private boolean passed, rebuilt, wired, fuelled, tapped, lit, packed, treated, heard, shutDown, ranDry, repaired, reloaded;
    private String before = "";
    private Npc veilbound;

    UrbanUtilityQaScene(Game game) { super(game); }

    @Override protected String tag() { return "urban-utilities"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    UrbanUtilityTourPlan plan() { return plan; }

    /** Plans the tour on the current generated world and stages the kit; no block is changed. */
    void stage() {
        plan = UrbanUtilityTourPlan.of(game.world);
        if (!plan.withinReach()) { throw new IllegalStateException("the tour's devices are out of the generator's reach: " + plan); }
        Inventory inv = game.player.inventory;
        inv.clear();
        inv.set(7, new ItemStack(ItemType.IRON_PICKAXE, 1));
        UrbanUtilityTourPlan.materials().forEach(inv::add);
        inv.add(ItemType.SPARE_PARTS, SPARE_FOR_REPAIR);
        inv.add(ItemType.FIBER, UrbanUtilities.FILTER_FIBRE);
        inv.add(ItemType.WATERSKIN_EMPTY, SKINS);
        System.out.println("[urban-utilities] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=walked-legs(W,F,C,1-9,LeftCtrl,LMB,RMB,F5,F9,mouse-look); screen-commands(craft);"
                + " staged-poses-between-legs,staged-clock(forward),staged-infected(a-veilbound),"
                + "staged-kit(salvage-for-the-bench,a-spare-part,pry-tool,fibre,empty-waterskins"
                + (survival() ? ")" : ",creative-fuel-can)") + "; generated-geometry; normal-simulation");
        System.out.println("[urban-utilities] plan " + plan);
        BuildingPlan.Fixture bench = plan.bench();
        add(new Clock("day", 10 * 60), new Walk(bench.target()),
                new Craft(Map.of(ItemType.GENERATOR, 1), "01-generator-rebuilt"),
                new Put(ItemType.GENERATOR, plan.generator(), plan.generatorFrom()),
                new Walk(bench.target()),
                new Craft(Map.of(ItemType.WORK_LAMP, 2, ItemType.WATER_PURIFIER, 1), null),
                new Put(ItemType.WORK_LAMP, plan.kitchenLamp(), plan.kitchenLampFrom()), new SwitchOn(plan.kitchenLamp()),
                new Put(ItemType.WORK_LAMP, plan.bedroomLamp(), plan.bedroomLampFrom()), new SwitchOn(plan.bedroomLamp()),
                new Put(ItemType.WATER_PURIFIER, plan.purifier(), plan.purifierFrom()), new WiredUp(),
                new Pose(plan.atTank(), plan.tank()), new Wait("stream", 1f), new Siphon(),
                new Pose(plan.tap().target(), plan.tap().position()), new Wait("stream", 1f), new Tap(),
                new Clock("night", 21 * 60 + 30), new Walk(plan.generatorFrom()), new Start(true),
                new Walk(plan.purifierFrom()), new Purify(),
                new Walk(plan.generatorFrom()), new Threat(), new Stop(),
                new Start(false), new RunDry(), new Rebuild(),
                new Record(), new Save(), new Load(), new Reloaded(), new Report());
        staged = true;
    }

    // ------------------------------------------------------------------
    // Reading the utilities
    // ------------------------------------------------------------------

    boolean survival() { return game.gameMode() == GameMode.SURVIVAL; }

    private UrbanUtilities ledger() {
        return game.world.urban.utilities;
    }

    private Device generator() {
        return ledger().device(plan.generator());
    }

    private Device purifier() {
        return ledger().device(plan.purifier());
    }

    private String prompt() {
        return game.interactPrompt == null ? "" : game.interactPrompt;
    }

    private BlockType block(Vec3i p) {
        return game.world.getBlock(p.x(), p.y(), p.z());
    }

    private boolean aimedAt(Vec3i p) {
        var t = game.targetHit;
        return t != null && t.x() == p.x() && t.y() == p.y() && t.z() == p.z();
    }

    private Map<ItemType, Integer> counts() {
        Map<ItemType, Integer> out = new EnumMap<>(ItemType.class);
        Inventory inv = game.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null) { out.merge(s.type, s.count, Integer::sum); }
        }
        return out;
    }

    /** Puts the first stack of a type on the hotbar, as dragging it there would, and selects it. */
    private boolean hand(ItemType type) {
        if (select(type)) { return true; }
        Inventory inv = game.player.inventory;
        for (int i = 9; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type == type) {
                ItemStack moved = inv.get(8);
                inv.set(8, s);
                inv.set(i, moved);
                return select(type);
            }
        }
        return false;
    }

    /**
     * Everything the refuge's utilities keep, compared across the save: each device's switch, fuel,
     * wear, filter and water, the litres drawn from the building's pipes and the waterskins carried.
     */
    private String utilities() {
        StringBuilder b = new StringBuilder();
        for (Device d : ledger().devices()) {
            b.append(d.kind).append('@').append(d.pos).append(d.on ? " on" : " off")
                    .append(String.format(Locale.ROOT, " c=%.2f f=%.2f u=%.2f w=%.2f; ", d.condition, d.fuel, d.untreated, d.clean));
        }
        b.append("drawn=").append(ledger().drawn(plan.refuge().id()));
        Map<ItemType, Integer> c = counts();
        b.append(" skins=").append(c.getOrDefault(ItemType.WATERSKIN_EMPTY, 0)).append('/')
                .append(c.getOrDefault(ItemType.WATERSKIN_DIRTY, 0)).append('/').append(c.getOrDefault(ItemType.WATERSKIN_CLEAN, 0));
        return b.toString();
    }

    @Override
    protected IllegalStateException fail(String reason) {
        System.out.println("[urban-utilities] deeds " + String.join(" | ", deeds));
        return super.fail(reason);
    }

    // ------------------------------------------------------------------
    // Building and putting down
    // ------------------------------------------------------------------

    /** The crafting screen opened with C at the bench, and things made through its own command. */
    private final class Craft extends Task {
        private final Map<ItemType, Integer> make;
        private final String label;
        private int phase;
        private float wait;

        Craft(Map<ItemType, Integer> make, String label) { super("build " + make.keySet()); this.make = make; this.label = label; }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            switch (phase) {
                case 0 -> {
                    lookAtBlock(plan.bench().position(), .6f);
                    press(GLFW_KEY_C);
                    phase = 1;
                    wait = 0;
                }
                case 1 -> {
                    if (game.uiMode != Game.UiMode.CRAFTING) {
                        if ((wait += dt) > .5f) {
                            if (age > 5) { throw fail("the crafting screen never opened: " + game.uiMode); }
                            phase = 0;
                        }
                        return false;
                    }
                    for (Map.Entry<ItemType, Integer> e : make.entrySet()) {
                        var recipe = UrbanUtilityTourPlan.recipe(e.getKey());
                        for (int i = 0; i < e.getValue(); i++) {
                            if (CraftingScreen.craft(game, recipe, game.nearbyStations()) == null) {
                                throw fail("could not build a " + e.getKey() + ": " + counts());
                            }
                        }
                        if (game.player.inventory.count(e.getKey()) < e.getValue()) { throw fail("no " + e.getKey() + ": " + counts()); }
                    }
                    deeds.add("built " + make + " carrying " + counts());
                    if (label != null) {
                        rebuilt = true;
                        capture(label);
                    }
                    phase = 2;
                }
                case 2 -> {
                    press(GLFW_KEY_C);
                    phase = 3;
                    wait = 0;
                }
                default -> {
                    if (game.uiMode == Game.UiMode.NONE) { return true; }
                    if ((wait += dt) > .5f) { phase = 2; }
                }
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Walks to a cell, aims at the top of the floor cell under the device's cell and presses the use button. */
    private final class Put extends Task {
        private final ItemType item;
        private final Vec3i at, from;
        private Walk walk;
        private float wait;
        private int presses;

        Put(ItemType item, Vec3i at, Vec3i from) { super("put the " + item + " down at " + at); this.item = item; this.at = at; this.from = from; }

        @Override boolean step(float dt) {
            if (walk == null) { walk = new Walk(from); }
            if (!walk.step(dt)) { return false; }
            Device d = ledger().device(at);
            if (d != null && block(at) == item.places()) {
                deeds.add("put " + item + "@" + at);
                return true;
            }
            if (!hand(item)) { throw fail("no " + item + " to put down: " + counts()); }
            look(at.x() + .5f, at.y() + .02f, at.z() + .5f);
            var t = game.targetHit;
            boolean floor = t != null && t.x() == at.x() && t.y() == at.y() - 1 && t.z() == at.z() && t.ny() == 1;
            if (!floor) {
                if ((wait += dt) > 4) { throw fail("the floor under " + at + " is not in reach: aimed " + t); }
                return false;
            }
            if ((wait += dt) > .4f) {
                if (++presses > 6) { throw fail("the " + item + " was never put down at " + at + ": " + loggedAfter(null)); }
                rightClick();
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** F on a work lamp just put down: switched on, it stays dark while nothing feeds it. */
    private final class SwitchOn extends Task {
        private final Vec3i lamp;
        private boolean pressed;
        private float wait;

        SwitchOn(Vec3i lamp) { super("switch on the lamp at " + lamp); this.lamp = lamp; }

        @Override boolean step(float dt) {
            hand(ItemType.IRON_PICKAXE);
            lookAtBlock(lamp, .45f);
            if ((wait += dt) < .4f) { return false; }
            Device d = ledger().device(lamp);
            if (!pressed) {
                if (!aimedAt(lamp) || !prompt().startsWith("[F] Switch the work lamp on")) {
                    if (age > 6) { throw fail("the lamp reads '" + prompt() + "' aimed at " + game.targetHit); }
                    return false;
                }
                press(GLFW_KEY_F);
                pressed = true;
                return false;
            }
            if (!d.on) { throw fail("F did not switch the lamp on"); }
            if (game.world.isLampLit(lamp.x(), lamp.y(), lamp.z())) { throw fail("a lamp lit with nothing feeding it"); }
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Every device in place, wired to the building, and nothing lit: the generator has no fuel. */
    private final class WiredUp extends Task {
        WiredUp() { super("wired up and dark"); }

        @Override boolean step(float dt) {
            if (ledger().deviceCount() != 4) { throw fail("devices " + ledger().deviceCount() + ": " + utilities()); }
            for (Vec3i lamp : new Vec3i[]{plan.kitchenLamp(), plan.bedroomLamp()}) {
                if (game.world.isLampLit(lamp.x(), lamp.y(), lamp.z())) { throw fail("a lamp lit with no power: " + lamp); }
            }
            lookAtBlock(plan.generator(), .5f);
            if (age < .6f) { return false; }
            wired = true;
            views.add("02-wired-up-and-dark=" + prompt());
            capture("02-wired-up-and-dark");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Fuel and water
    // ------------------------------------------------------------------

    /** Crouched, holding the attack button with the pry tool on the wreck's tank, for its fuel can. */
    private final class Siphon extends Task {
        private Map<ItemType, Integer> start;
        private float work, aim;
        private boolean midway;

        Siphon() { super("take apart the wreck's fuel tank at " + plan.tank()); }

        @Override boolean step(float dt) {
            Vec3i tank = plan.tank();
            if (start == null) {
                start = counts();
                if (!hand(ItemType.IRON_PICKAXE)) { throw fail("no pry tool"); }
                hold(GLFW_KEY_LEFT_CONTROL, true);
                return false;
            }
            lookAtBlock(tank, .5f);
            if (block(tank) == BlockType.CAR_WRECK) {
                if ((aim += dt) < .3f) { return false; }
                if (survival() && work == 0 && !prompt().contains("fuel can")) {
                    if (aim > 6) { throw fail("the tank reads '" + prompt() + "'"); }
                    return false;
                }
                leftMouse(true);
                work += dt;
                if (!midway && game.blockActions.dismantling.progress() >= .5f) {
                    midway = true;
                    views.add("03-fuel-from-a-wreck=" + prompt());
                    capture("03-fuel-from-a-wreck");
                }
                if (work > 20) { throw fail("the tank never came apart"); }
                return false;
            }
            leftMouse(false);
            hold(GLFW_KEY_LEFT_CONTROL, false);
            int got = counts().getOrDefault(ItemType.FUEL_CAN, 0) - start.getOrDefault(ItemType.FUEL_CAN, 0);
            if (survival() ? got != 1 : got != 0) { throw fail("the tank gave " + got + " fuel cans"); }
            if (!survival()) { // a Creative break takes nothing: the can its salvage would have given is staged
                game.player.inventory.add(ItemType.FUEL_CAN, 1);
                System.out.println("[urban-utilities] staged-creative-fuel-can 1");
                capture("03-fuel-from-a-wreck");
            }
            fuelled = true;
            deeds.add("fuel can from the tank at " + tank);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** F at the kitchen tap with empty waterskins in the pack: stale, untreated water from the pipes. */
    private final class Tap extends Task {
        private int filled;
        private float wait;

        Tap() { super("fill waterskins at the kitchen tap"); }

        @Override boolean step(float dt) {
            lookAtBlock(plan.tap().position(), .5f);
            if ((wait += dt) < .4f) { return false; }
            wait = 0;
            int dirty = game.player.inventory.count(ItemType.WATERSKIN_DIRTY);
            if (dirty > filled) {
                filled = dirty;
                if (filled == 1) {
                    views.add("04-stale-tap=" + prompt());
                    capture("04-stale-tap");
                }
            }
            if (filled >= TAP_LITRES) {
                if (ledger().drawn(plan.refuge().id()) != TAP_LITRES) { throw fail("drawn " + utilities()); }
                tapped = true;
                return true;
            }
            if (!prompt().startsWith("[F] Fill a waterskin: stale tap water")) {
                if (age > 6) { throw fail("the tap reads '" + prompt() + "'"); }
                return false;
            }
            press(GLFW_KEY_F);
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Power
    // ------------------------------------------------------------------

    /** The fuel can poured in with the use button and F to start: the lamps come on and it is heard. */
    private final class Start extends Task {
        private final boolean first;
        private int phase;
        private float wait;
        private Object mark;

        Start(boolean first) { super(first ? "fuel and start the generator" : "start the generator again"); this.first = first; }

        @Override boolean step(float dt) {
            Device g = generator();
            lookAtBlock(plan.generator(), .5f);
            if ((wait += dt) < .4f) { return false; }
            wait = 0;
            switch (phase) {
                case 0 -> {
                    if (!first) { phase = 1; return false; }
                    if (g.fuel > 0) { phase = 1; return false; }
                    if (!hand(ItemType.FUEL_CAN)) { throw fail("no fuel can: " + counts()); }
                    if (!prompt().startsWith("[RMB] Pour in the fuel can")) {
                        if (age > 6) { throw fail("the generator reads '" + prompt() + "'"); }
                        return false;
                    }
                    rightClick();
                }
                case 1 -> {
                    if (!aimedAt(plan.generator())) { return false; }
                    mark = logMark();
                    hand(ItemType.IRON_PICKAXE);
                    press(GLFW_KEY_F);
                    phase = 2;
                }
                case 2 -> {
                    if (!g.running()) {
                        if (age > 8) { throw fail("the generator did not start: " + loggedAfter(mark)); }
                        return false;
                    }
                    if (!first) { return true; }
                    phase = 3;
                }
                case 3 -> {
                    for (Vec3i lamp : new Vec3i[]{plan.kitchenLamp(), plan.bedroomLamp()}) {
                        if (!game.world.isLampLit(lamp.x(), lamp.y(), lamp.z())) { throw fail("lamp " + lamp + " is dark: " + utilities()); }
                    }
                    if (survival() && game.noise.countCategory("generator") == 0) { throw fail("nothing heard it start"); }
                    lookAtBlock(plan.kitchenLamp(), .4f);
                    phase = 4;
                }
                case 4 -> {
                    lit = true;
                    views.add("05-lights-on=" + prompt());
                    capture("05-lights-on");
                    phase = 5;
                }
                default -> {
                    views.add("06-generator-running=" + prompt());
                    capture("06-generator-running");
                    return true;
                }
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * The purifier: F finds its filter spent, fibre packs it, the stale water is poured in and treated
     * on the generator's power, and a waterskin is filled with clean water.
     */
    private final class Purify extends Task {
        private int phase;
        private float wait;

        Purify() { super("pack, fill and draw clean water from the purifier"); }

        @Override boolean step(float dt) {
            Device p = purifier();
            lookAtBlock(plan.purifier(), .5f);
            if ((wait += dt) < .4f) { return false; }
            wait = 0;
            switch (phase) {
                case 0 -> {
                    hand(ItemType.IRON_PICKAXE); // nothing in hand that the use button would work on it
                    if (!aimedAt(plan.purifier())) { return false; }
                    if (!prompt().startsWith("Purifier: filter spent")) { throw fail("the purifier reads '" + prompt() + "'"); }
                    press(GLFW_KEY_F);
                    phase = 1;
                }
                case 1 -> {
                    if (!p.on) { throw fail("F did not switch it on"); }
                    if (!hand(ItemType.FIBER)) { throw fail("no fibre"); }
                    if (!prompt().startsWith("[RMB] Pack the filter")) { return false; }
                    rightClick();
                    phase = 2;
                }
                case 2 -> {
                    if (p.condition < UrbanUtilities.SOUND) { throw fail("the filter was not packed: " + utilities()); }
                    packed = true;
                    views.add("07-filter-packed=" + prompt());
                    capture("07-filter-packed");
                    phase = 3;
                }
                case 3 -> {
                    if (game.player.inventory.count(ItemType.WATERSKIN_DIRTY) == 0) { phase = 4; return false; }
                    if (!hand(ItemType.WATERSKIN_DIRTY)) { throw fail("no dirty water"); }
                    if (!prompt().startsWith("[RMB] Pour in untreated water")) { return false; }
                    rightClick();
                }
                case 4 -> {
                    // Treated on the generator's power, while the pump draws more of the building's own.
                    if (p.clean < 1f) {
                        if (age > 90) { throw fail("nothing treated: " + utilities()); }
                        return false;
                    }
                    if (!hand(ItemType.WATERSKIN_EMPTY)) { throw fail("no empty waterskin"); }
                    if (!prompt().startsWith("[RMB] Fill a waterskin with clean water")) { return false; }
                    rightClick();
                    phase = 5;
                }
                default -> {
                    if (game.player.inventory.count(ItemType.WATERSKIN_CLEAN) < 1) { throw fail("no clean water: " + utilities()); }
                    treated = true;
                    deeds.add("purified " + utilities());
                    capture("08-treated-water");
                    return true;
                }
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * One Veilbound in the courtyard (staged and printed) while the generator runs: in Survival it
     * hears the noise through the wall and turns to it; a Creative builder's generator is heard by
     * nobody. Then it is taken away again.
     */
    private final class Threat extends Task {
        private float since;

        Threat() { super("a body in the courtyard while the generator runs"); }

        @Override boolean step(float dt) {
            if (veilbound == null) {
                hand(ItemType.IRON_PICKAXE);
                Vec3i at = plan.threat();
                veilbound = game.entities.spawnNpc(game.world, "Veilbound", at.x() + .5f, at.y() + .02f, at.z() + .5f);
                veilbound.archetype = NpcArchetype.VEILBOUND;
                veilbound.health = veilbound.maxHealth = veilbound.archetype.maxHealth;
                veilbound.searchTimer = 0;
                System.out.println("[urban-utilities] staged-infected veilbound at " + at);
                return false;
            }
            lookAtBlock(plan.generator(), .5f);
            since += dt;
            Vec3i g = plan.generator();
            boolean drawn = veilbound.searchTimer > 0 && Math.abs(veilbound.lastKnown.x - (g.x() + .5f)) < 1.5f
                    && Math.abs(veilbound.lastKnown.z - (g.z() + .5f)) < 1.5f;
            if (survival()) {
                if (!drawn) {
                    if (since > 8) { throw fail("the Veilbound never heard the generator: knows " + veilbound.lastKnown); }
                    return false;
                }
                if (since < 1.5f) { return false; }
                heard = true;
                deeds.add("drawn to " + veilbound.lastKnown + " from " + plan.threat());
                capture("09-drawn-by-the-noise");
            } else {
                if (drawn) { throw fail("something heard a Creative builder's generator"); }
                if (since < 6f) { return false; }
                heard = true;
                deeds.add("heard by nobody");
                capture("09-heard-by-nobody");
            }
            if (game.entities.removeNpc(game, veilbound)) {
                System.out.println("[urban-utilities] unstaged-infected veilbound at " + cell(veilbound.pos.x, veilbound.pos.y, veilbound.pos.z));
            }
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** F on the running generator: it stops and the refuge goes dark. */
    private final class Stop extends Task {
        private boolean pressed;
        private float wait;

        Stop() { super("switch the generator off"); }

        @Override boolean step(float dt) {
            hand(ItemType.IRON_PICKAXE);
            lookAtBlock(plan.generator(), .5f);
            if ((wait += dt) < .4f) { return false; }
            if (!pressed) {
                if (!aimedAt(plan.generator()) || !prompt().startsWith("[F] Switch off")) { return false; }
                press(GLFW_KEY_F);
                pressed = true;
                return false;
            }
            if (generator().on) { throw fail("F did not stop it"); }
            for (Vec3i lamp : new Vec3i[]{plan.kitchenLamp(), plan.bedroomLamp()}) {
                if (game.world.isLampLit(lamp.x(), lamp.y(), lamp.z())) { throw fail("lamp " + lamp + " stayed lit"); }
            }
            shutDown = true;
            capture("10-shut-down");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** The generator left running until its tank is dry: it dies and the lamps go dark by themselves. */
    private final class RunDry extends Task {
        private Object mark;

        RunDry() { super("run it until the tank is dry"); }

        @Override boolean step(float dt) {
            if (mark == null) { mark = logMark(); }
            lookAtBlock(plan.kitchenLamp(), .4f);
            Device g = generator();
            if (g.on) {
                if (age > 400) { throw fail("it never ran dry: " + utilities()); }
                return false;
            }
            if (g.fuel != 0f || !loggedSince(mark, "The generator sputters and dies")) { throw fail("it stopped, not dry: " + utilities()); }
            if (game.world.isLampLit(plan.kitchenLamp().x(), plan.kitchenLamp().y(), plan.kitchenLamp().z())) {
                throw fail("the lamp outlived the fuel");
            }
            ranDry = true;
            deeds.add("ran dry at condition " + Math.round(g.condition));
            capture("11-tank-dry");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** The use button with a spare part and the pry tool in the pack: the evening's wear rebuilt. */
    private final class Rebuild extends Task {
        private float wait, worn = -1;

        Rebuild() { super("rebuild the generator's worn parts"); }

        @Override boolean step(float dt) {
            Device g = generator();
            if (worn < 0) {
                worn = g.condition;
                if (worn >= UrbanUtilities.SOUND) { throw fail("the generator never wore"); }
            }
            lookAtBlock(plan.generator(), .5f);
            if ((wait += dt) < .4f) { return false; }
            wait = 0;
            if (g.condition > worn) {
                repaired = true;
                deeds.add(String.format(Locale.ROOT, "rebuilt %.1f -> %.1f", worn, g.condition));
                capture("12-rebuilt");
                return true;
            }
            if (!hand(ItemType.SPARE_PARTS)) { throw fail("no spare part: " + counts()); }
            if (!prompt().startsWith("[RMB] Rebuild its worn parts")) {
                if (age > 6) { throw fail("the generator reads '" + prompt() + "'"); }
                return false;
            }
            rightClick();
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Saving and reporting
    // ------------------------------------------------------------------

    private final class Record extends Task {
        Record() { super("record what the utilities keep"); }

        @Override boolean step(float dt) {
            before = utilities();
            System.out.println("[urban-utilities] before-save " + before);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** After the load: the same devices, water and skins. */
    private final class Reloaded extends Task {
        Reloaded() { super("the same utilities after the load"); }

        @Override boolean step(float dt) {
            String after = utilities();
            if (!after.equals(before)) { throw fail("the utilities changed across the save:\n before " + before + "\n after  " + after); }
            lookAtBlock(plan.generator(), .5f);
            if (age < .8f) { return false; }
            reloaded = true;
            capture("13-after-reload");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            releaseAll();
            if (!rebuilt || !wired || !fuelled || !tapped || !lit || !packed || !treated || !heard || !shutDown || !ranDry
                    || !repaired || !reloaded || captures != CAPTURES) {
                throw fail("tour incomplete: rebuilt=" + rebuilt + " wired=" + wired + " fuelled=" + fuelled + " tapped=" + tapped
                        + " lit=" + lit + " packed=" + packed + " treated=" + treated + " heard=" + heard + " shutDown=" + shutDown
                        + " ranDry=" + ranDry + " repaired=" + repaired + " reloaded=" + reloaded + " captures=" + captures);
            }
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-utilities] pass=true mode=%s seed=%d poses=%d captures=%d"
                            + " health=%.1f routeSeconds=%.0f%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, poses, captures,
                    game.player.health, elapsed);
            System.out.println("[urban-utilities] utilities " + utilities());
            System.out.println("[urban-utilities] deeds " + String.join(" | ", deeds));
            System.out.println("[urban-utilities] views " + String.join(" | ", views));
            System.out.println("[urban-utilities] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }
}

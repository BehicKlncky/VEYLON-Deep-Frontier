package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.ui.CraftingScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureGeometry;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.RefugeExposure;
import com.veylon.world.urban.SalvageTable;
import com.veylon.world.urban.UrbanFortifications;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanPlaces.Place;
import com.veylon.world.urban.UrbanRefuges;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-18 refuge tour ({@code VEYLON_SCENE=urban_refuge}) on a generator-17 world from
 * {@code VEYLON_GENERATOR=urban-dev}, in either game mode, played with ordinary controls (W, F, C,
 * Escape, left Control, the mouse buttons, F5, F9 and mouse look through the production input seam):
 * <ol>
 *   <li>by day, into the abandoned apartment beside the starter refuge and up to its first-floor
 *       flat; crouch and F on its bed claims the flat, the second of three refuges, and makes it
 *       home;</li>
 *   <li>its cupboard, searched, is the refuge's storage: food and water are put in it through the
 *       container screen's own command;</li>
 *   <li>the flat's furniture taken apart, crouched, holding the attack button, until salvage has
 *       given the planks and scrap for a door brace and three plank barricades, which are made by
 *       hand through the crafting screen's own command (Creative salvage takes nothing, so there the
 *       same materials are staged);</li>
 *   <li>the street door braced on one leaf and barred on the other, the rear door barred, and the
 *       bed saying the refuge is shut tight;</li>
 *   <li>a real threat: one Breacher on the street (staged and printed) hears the footsteps in the
 *       hall, hammers at the braced leaf, splinters the brace before the door's own blows, breaks
 *       it and is put down at the breach; the bed then counts the way in open, the broken doorway
 *       is boarded up with two of the barricades and the refuge is shut tight again (a Creative
 *       builder is heard by nobody, so there the door stands untouched);</li>
 *   <li>a barricade put up in the hall where it is in the way, and taken down again crouched with
 *       no tool, giving back most of what it cost;</li>
 *   <li>one Veilbound on the street (staged and printed), which cannot break anything: it hears the
 *       footsteps and comes to the boarded doorway, finds no way in, never touches a defence and
 *       loses the trail once the hall falls quiet;</li>
 *   <li>at night, F on the bed: the refuge is shut tight, the Veilbound outside neither keeps the
 *       sleeper awake nor is drawn, and the night passes until a quiet waking;</li>
 *   <li>F5 and F9 (the staged bodies still about taken out first, printed, as a save refuses a body
 *       that is no encounter record): the same claims and home, storage, salvaged furniture,
 *       barricades and bars;</li>
 *   <li>by day the camp nearest the spawn: crouch and F on a bed of theirs is refused, the rooms
 *       being theirs, and nothing is claimed.</li>
 * </ol>
 * The poses between legs, the clock (only ever forward), the weather, two infected bodies and a kit
 * of a spear, food and water are staged and printed as staged. No block is staged. Exactly
 * seventeen labelled captures. Failures throw.
 */
final class UrbanRefugeQaScene extends UrbanPeopleScene {
    static final double DEADLINE_SECONDS = 900;
    static final int CAPTURES = 17;
    static final String CLAIM_PROMPT = "[F] Rest here  (crouch: claim these rooms as a refuge, 2 of 3)";
    static final String SHUT_PROMPT = "[F] Sleep in your refuge  (shut tight; crouch: give it up)";
    static final String THEIRS = "claiming them would be taking what is theirs";
    /** Food and water put into the refuge's storage. */
    static final int STORED_FOOD = 4, STORED_WATER = 1;

    private UrbanRefugeTourPlan plan;
    private boolean passed, claimed, stored, salvaged, crafted, braced, shut, hammered, breached, breacherDown,
            boarded, misplaced, takenDown, keptOut, slept, woke, reloaded, refused;
    private final Map<ItemType, Integer> gained = new EnumMap<>(ItemType.class);
    private String before = "";
    private Npc breacher, veilbound;
    private int insideFrames;

    UrbanRefugeQaScene(Game game) { super(game); }

    @Override protected String tag() { return "urban-refuge"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    UrbanRefugeTourPlan plan() { return plan; }
    long floor() { return UrbanIds.floor(plan.flat().id(), UrbanRefugeTourPlan.STOREY); }

    /** Plans the tour on the current generated world and stages the kit; no block is changed. */
    void stage() {
        plan = UrbanRefugeTourPlan.of(game.world);
        Inventory inv = game.player.inventory;
        inv.clear();
        inv.set(7, new ItemStack(ItemType.IRON_SPEAR, 1));
        inv.add(ItemType.DRIED_BERRY, 6);
        inv.add(ItemType.WATERSKIN_CLEAN, 2);
        if (!survival()) { // Creative salvage takes nothing: the materials it would have given are staged
            UrbanRefugeTourPlan.materials().forEach(inv::add);
        }
        System.out.println("[urban-refuge] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=walked-legs(W,F,C,Esc,LeftCtrl,LMB,RMB,F5,F9,mouse-look);"
                + " screen-commands(crate-transfers,craft); staged-poses-between-legs,staged-clock(forward),"
                + "staged-infected(a-breacher,a-veilbound),staged-kit(spear,food,water"
                + (survival() ? ")" : ",creative-materials)") + "; generated-geometry; normal-simulation");
        System.out.println("[urban-refuge] plan " + plan);
        Vec3i near = plan.hallNear();
        add(new Clock("day", 10 * 60), new Pose(plan.street(), plan.braced().position()), new Wait("stream", 1f),
                new Walk(plan.bed().target()), new Claim(), new Store());
        for (int i = 0; i < plan.pieces().size(); i++) {
            add(new Salvage(plan.pieces().get(i), i == 0, i == plan.pieces().size() - 1));
        }
        add(new Craft(), new Walk(near), new Brace(), new Bar(plan.barred()));
        for (Fixture leaf : plan.rear()) { add(new Bar(leaf)); }
        add(new Inside(plan.bed().target()), new ShutTight("07-shut-tight"),
                new Inside(near), new BreacherAtTheDoor(), new BoardUp(), new Misplaced(), new KeptOut(),
                new Inside(plan.bed().target()), new Clock("night", 23 * 60 + 15), new Night(),
                new Unstage(), new Record(), new Save(), new Load(), new Reloaded());
        add(new Clock("morning", 9 * 60), new Pose(plan.camp().yard(), plan.camp().door()), new Wait("stream", 1f),
                new Walk(plan.campBed().target()), new TheirBed(), new Report());
        staged = true;
    }

    // ------------------------------------------------------------------
    // Reading the refuge
    // ------------------------------------------------------------------

    boolean survival() { return game.gameMode() == GameMode.SURVIVAL; }

    private UrbanRefuges.Claim claim() {
        return game.world.urban.refuges.claim(floor());
    }

    private RefugeExposure.Assessment exposure() {
        UrbanRefuges.Claim c = claim();
        return c == null ? null : RefugeExposure.assess(game.world, c);
    }

    private String prompt() {
        return game.interactPrompt == null ? "" : game.interactPrompt;
    }

    private BlockType block(Vec3i p) {
        return game.world.getBlock(p.x(), p.y(), p.z());
    }

    private int lock(Fixture door) {
        Vec3i p = door.position();
        return game.world.fixtureLockAt(p.x(), p.y(), p.z());
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

    private int stack(Inventory inv, ItemType type) {
        int n = 0;
        for (int i = 0; inv != null && i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type == type) { n += s.count; }
        }
        return n;
    }

    private String slots() {
        StringBuilder s = new StringBuilder();
        Inventory inv = game.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.get(i);
            if (st != null) { s.append(i).append('=').append(st.type).append('x').append(st.count).append(' '); }
        }
        return s.append("sel=").append(game.player.hotbarSel).toString();
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

    /** True while the body stands within the flat's parcel, on any storey of the building. */
    private boolean insideTheFlat(Npc n) {
        ParcelPlan parcel = plan.flat().parcel();
        int x = (int) Math.floor(n.pos.x), z = (int) Math.floor(n.pos.z);
        return parcel.contains(x, z) && n.pos.y >= 36 && n.pos.y < plan.flat().roofY() + 3;
    }

    /**
     * Everything the refuge keeps, compared across the save: the claims and home, the storage's
     * contents, the salvaged furniture, every barricade and brace and the doors' bars.
     */
    private String fortress() {
        StringBuilder b = new StringBuilder();
        UrbanRefuges refuges = game.world.urban.refuges;
        for (UrbanRefuges.Claim c : refuges.claims()) {
            b.append(UrbanIds.format(c.floor)).append(" sleep=").append(c.sleep)
                    .append(String.format(Locale.ROOT, " at=%.1f; ", c.claimedMinute));
        }
        b.append("home=").append(UrbanIds.format(refuges.homeFloor()));
        Inventory store = game.world.crateContents.get(plan.store().position());
        b.append(" store=").append(stack(store, ItemType.DRIED_BERRY)).append('/').append(stack(store, ItemType.WATERSKIN_CLEAN))
                .append(" entry=").append(game.world.urban.containers.at(plan.store().position()) == null ? "none"
                        : game.world.urban.containers.at(plan.store().position()).state);
        for (Fixture f : plan.pieces()) { b.append(' ').append(block(f.position())); }
        UrbanFortifications forts = game.world.urban.fortifications;
        for (Fixture f : List.of(plan.braced(), plan.barred())) {
            for (int h = 0; h < 2; h++) {
                Vec3i c = f.position().offset(0, h, 0);
                BlockType t = block(c);
                b.append(' ').append(t);
                if (UrbanFortifications.isBarricade(t)) { b.append('(').append(Math.round(forts.health(t, c))).append(')'); }
            }
            b.append(" lock=").append(lock(f));
            Float brace = Fortification.braceAt(game, f.position());
            if (brace != null) { b.append(" brace=").append(Math.round(brace)); }
        }
        for (Fixture f : plan.rear()) { b.append(' ').append(block(f.position())).append(" lock=").append(lock(f)); }
        return b.toString();
    }

    @Override
    protected IllegalStateException fail(String reason) {
        System.out.println("[urban-refuge] deeds " + String.join(" | ", deeds));
        return super.fail(reason);
    }

    @Override
    protected void observe() {
        if (veilbound != null && !veilbound.dead && insideTheFlat(veilbound)) { insideFrames++; }
    }

    // ------------------------------------------------------------------
    // Claiming, storing, salvage and crafting
    // ------------------------------------------------------------------

    /** Crouch and F on the flat's bed: the flat is claimed, the second refuge of three, and is home. */
    private final class Claim extends Task {
        private Object mark;

        Claim() { super("claim the flat at its bed"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            hold(GLFW_KEY_LEFT_CONTROL, true);
            lookAtBlock(plan.bed().position(), .4f);
            if (age < .5f) { return false; }
            if (mark == null) {
                if (!prompt().startsWith(CLAIM_PROMPT)) {
                    if (age > 6) { throw fail("the bed reads '" + prompt() + "'"); }
                    return false;
                }
                views.add("01-the-bed=" + prompt());
                mark = logMark();
                press(GLFW_KEY_F);
                return false;
            }
            UrbanRefuges refuges = game.world.urban.refuges;
            if (claim() == null) {
                if (age > 8) { throw fail("the flat was not claimed: " + loggedAfter(mark)); }
                return false;
            }
            if (refuges.size() != 2 || refuges.homeFloor() != floor() || game.sleeping
                    || !loggedSince(mark, "You claim the first-floor flat as a refuge (2 of 3)")) {
                throw fail("a wrong claim: claims=" + refuges.size() + " home=" + UrbanIds.format(refuges.homeFloor())
                        + " sleeping=" + game.sleeping + " log=" + loggedAfter(mark));
            }
            hold(GLFW_KEY_LEFT_CONTROL, false);
            if (game.player.place != Place.REFUGE) {
                if (age > 10) { throw fail("the HUD names " + game.player.place + " in the claimed flat"); }
                return false;
            }
            claimed = true;
            views.add("01-a-flat-claimed=" + game.player.locationLine());
            capture("01-a-flat-claimed");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** F on the cupboard: searched, it is the refuge's storage, and food and water go in. */
    private final class Store extends Task {
        private Walk walk;
        private int phase, presses;
        private float wait;

        Store() { super("store food and water in the refuge"); }

        @Override boolean step(float dt) {
            Vec3i p = plan.store().position();
            switch (phase) {
                case 0 -> {
                    if (walk == null) { walk = new Walk(plan.store().target()); }
                    if (walk.step(dt)) { phase = 1; }
                }
                case 1 -> {
                    lookAtBlock(p, .5f);
                    if (++presses > 8) { throw fail("the cupboard at " + p + " never opened"); }
                    press(GLFW_KEY_F);
                    phase = 2;
                    wait = 0;
                }
                case 2 -> {
                    wait += dt;
                    if (game.uiMode == Game.UiMode.CRATE && p.equals(game.openCratePos)) {
                        phase = 3;
                    } else if (wait > .5f) {
                        phase = 1;
                    }
                }
                case 3 -> {
                    if (game.uiMode != Game.UiMode.CRATE) { phase = 1; return false; }
                    if (game.crateSearching()) { return false; }
                    String status = game.crateStatus();
                    if (!status.startsWith("Your refuge's storage")) { throw fail("the cupboard reads '" + status + "'"); }
                    deposit(ItemType.DRIED_BERRY, STORED_FOOD);
                    deposit(ItemType.WATERSKIN_CLEAN, STORED_WATER);
                    Inventory crate = game.world.crateContents.get(p);
                    if (stack(crate, ItemType.DRIED_BERRY) < STORED_FOOD || stack(crate, ItemType.WATERSKIN_CLEAN) < STORED_WATER) {
                        throw fail("the supplies did not go in: " + counts());
                    }
                    stored = true;
                    views.add("02-supplies-stored=" + status);
                    capture("02-supplies-stored");
                    phase = 4;
                    wait = 0;
                }
                default -> {
                    if (game.uiMode == Game.UiMode.NONE) { return true; }
                    if ((wait += dt) > .3f) {
                        press(GLFW_KEY_ESCAPE);
                        wait = 0;
                    }
                }
            }
            return false;
        }

        private void deposit(ItemType type, int count) {
            Inventory inv = game.player.inventory;
            for (int slot = 0; slot < inv.size(); slot++) {
                ItemStack s = inv.get(slot);
                if (s != null && s.type == type) {
                    if (game.transferPlayerItemToCrate(slot, count) != count) { throw fail("the storage refused " + type); }
                    return;
                }
            }
            throw fail("no " + type + " to store");
        }

        @Override boolean threatsInterrupt() { return phase < 3 && game.uiMode == Game.UiMode.NONE; }
    }

    /**
     * Crouched, holding the attack button on a piece of the flat's furniture until it comes apart:
     * in Survival exactly what the salvage table says it gives, in Creative no materials; storage
     * spills whatever was left in it either way, as a broken container always has. It is gone as a
     * recorded edit.
     */
    private final class Salvage extends Task {
        private final Fixture piece;
        private final boolean first, last;
        private Walk walk;
        private Map<ItemType, Integer> before;
        private List<ItemStack> yields;
        private float work, aim;
        private boolean midway;

        Salvage(Fixture piece, boolean first, boolean last) {
            super("take apart the " + piece.block().displayName.toLowerCase(Locale.ROOT) + " at " + piece.position());
            this.piece = piece; this.first = first; this.last = last;
        }

        @Override boolean step(float dt) {
            Vec3i p = piece.position();
            if (before == null) {
                if (walk == null) { walk = new Walk(piece.target()); }
                if (!walk.step(dt)) { return false; }
                before = counts();
                yields = SalvageTable.yields(game.world, p, piece.block());
                hold(GLFW_KEY_LEFT_CONTROL, true);
                return false;
            }
            lookAtBlock(p, .5f);
            if (block(p) == piece.block()) {
                if ((aim += dt) < .3f) { return false; } // the aim takes a frame to reach the prompt
                boolean hint = prompt().contains("Hold to take apart the ") || prompt().contains("hold LMB for ");
                if (survival() && work == 0 && !hint) {
                    if (aim > 6) {
                        throw fail("the " + piece.block() + " at " + p + " reads '" + prompt() + "' aimed at " + aimed());
                    }
                    return false;
                }
                leftMouse(true);
                work += dt;
                if (first && !midway && game.blockActions.dismantling.progress() >= .5f) {
                    midway = true;
                    views.add("03-taking-it-apart=" + prompt());
                    capture("03-taking-it-apart");
                }
                if (work > 20) { throw fail("the " + piece.block() + " at " + p + " never came apart"); }
                return false;
            }
            leftMouse(false);
            hold(GLFW_KEY_LEFT_CONTROL, false);
            Byte edit = game.world.changedBlocks.get(p);
            if (block(p) != BlockType.AIR || edit == null || edit != BlockType.AIR.id()) {
                throw fail("the " + piece.block() + " at " + p + " left " + block(p) + " behind");
            }
            Map<ItemType, Integer> after = counts();
            // Storage spills what it held into the pack in either mode, as a broken container always has.
            boolean container = piece.kind() == BuildingPlan.FixtureKind.CONTAINER;
            for (ItemStack s : yields) {
                int got = after.getOrDefault(s.type, 0) - before.getOrDefault(s.type, 0);
                if (survival() ? got < s.count || !container && got != s.count : !container && got != 0) {
                    throw fail("the " + piece.block() + " gave " + got + " " + s.type + " for " + s.count + ": " + after);
                }
                gained.merge(s.type, got, Integer::sum);
            }
            if (!survival() && !container && !after.equals(before)) {
                throw fail("Creative salvage took something: " + before + " -> " + after);
            }
            if (first && !midway) { // a Creative break is instant
                midway = true;
                capture("03-taking-it-apart");
            }
            if (last) {
                for (Map.Entry<ItemType, Integer> need : UrbanRefugeTourPlan.materials().entrySet()) {
                    if (after.getOrDefault(need.getKey(), 0) < need.getValue()) {
                        throw fail("salvage left too little " + need.getKey() + ": " + after);
                    }
                }
                salvaged = true;
                deeds.add("salvaged " + plan.pieces().size() + " pieces for " + gained);
                capture("04-what-salvage-gave");
            }
            return true;
        }

        @Override boolean threatsInterrupt() { return !leftHeld; }
    }

    /** C, the crafting screen's own command for a door brace and the barricades, then C again. */
    private final class Craft extends Task {
        private int phase;
        private float wait;

        Craft() { super("make a door brace and barricades by hand"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            switch (phase) {
                case 0 -> {
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
                    make(ItemType.DOOR_BRACE, 1);
                    make(ItemType.BARRICADE, UrbanRefugeTourPlan.BARRICADES);
                    crafted = true;
                    deeds.add("crafted " + counts() + " slots " + slots());
                    capture("05-crafted");
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

        private void make(ItemType type, int times) {
            var recipe = UrbanRefugeTourPlan.recipe(type);
            for (int i = 0; i < times; i++) {
                if (CraftingScreen.craft(game, recipe, game.nearbyStations()) == null) {
                    throw fail("could not make a " + type + ": " + counts());
                }
            }
            if (game.player.inventory.count(type) < times) { throw fail("no " + type + " after crafting: " + counts()); }
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Fortifying
    // ------------------------------------------------------------------

    /** Mouse look at an opening: the middle of its leaf or panel where it has one, else the cell's middle. */
    private void lookAtOpening(Vec3i p, float height) {
        float[] box = new float[6];
        if (FixtureGeometry.thinBox(game.world, p.x(), p.y(), p.z(), block(p), box)) {
            look((box[0] + box[3]) / 2, p.y() + height, (box[2] + box[5]) / 2);
        } else {
            lookAtBlock(p, height);
        }
    }

    private String aimed() {
        var t = game.targetHit;
        return (t == null ? "nothing" : t.type() + "@" + t.x() + "," + t.y() + "," + t.z())
                + String.format(Locale.ROOT, " yaw=%.1f pitch=%.1f eye=%.2f crouch=%s cam=%s", game.camera.yaw, game.camera.pitch,
                game.player.pos.y + game.player.eyeHeight(), game.player.crouching, game.camera.position);
    }

    /** Closes a door leaf left open on the way in: F on the open leaf, standing. */
    private final class Closer {
        private float wait;

        /** @return true once the leaf is shut */
        boolean shut(Fixture door, float dt) {
            if (!Fixtures.isOpen(block(door.position()))) { return true; }
            hold(GLFW_KEY_LEFT_CONTROL, false);
            lookAtOpening(door.position(), .9f);
            if ((wait += dt) > .45f) {
                press(GLFW_KEY_F);
                wait = 0;
            }
            return false;
        }
    }

    /** The use button with the door brace on the street door's first leaf, from inside. */
    private final class Brace extends Task {
        private final Closer closer = new Closer();
        private Object mark;

        Brace() { super("brace the street door"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            Fixture door = plan.braced();
            if (!closer.shut(door, dt)) {
                if (age > 8) { throw fail("the street door's leaf would not close"); }
                return false;
            }
            lookAtBlock(door.position(), .9f);
            if (mark == null) {
                if (!hand(ItemType.DOOR_BRACE)) { throw fail("no door brace to hand: " + slots()); }
                if (!prompt().contains("Brace the door from this side")) {
                    if (age > 8) { throw fail("the street door reads '" + prompt() + "'"); }
                    return false;
                }
                mark = logMark();
                rightClick();
                return false;
            }
            Float brace = Fortification.braceAt(game, door.position());
            if (brace == null || brace != UrbanFortifications.BRACE_STRENGTH || lock(door) != FixtureStates.LOCK_BARRED
                    || !loggedSince(mark, "You bar the door and brace it from this side.")) {
                if (age > 10) {
                    throw fail("the door was not braced: brace=" + brace + " lock=" + lock(door) + " " + loggedAfter(mark));
                }
                return false;
            }
            if (survival() && game.player.inventory.count(ItemType.DOOR_BRACE) != 0
                    || !survival() && game.player.inventory.count(ItemType.DOOR_BRACE) != 1) {
                throw fail("the brace was " + (survival() ? "not used up" : "spent in Creative"));
            }
            if (age < .8f) { return false; }
            braced = true;
            views.add("06-the-street-door-braced=" + prompt());
            capture("06-the-street-door-braced");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Crouch and F on a door leaf from inside: barred (a Creative crouch locks it instead). */
    private final class Bar extends Task {
        private final Closer closer = new Closer();
        private final Fixture door;
        private Walk walk;
        private float wait;
        private int presses;

        Bar(Fixture door) { super("bar the door at " + door.position()); this.door = door; }

        @Override boolean step(float dt) {
            if (walk == null) { walk = new Walk(UrbanSlicePlan.inside(door)); }
            if (!walk.step(dt)) { return false; }
            if (!closer.shut(door, dt)) {
                if (age > 20) {
                    throw fail("the door at " + door.position() + " would not close: '" + prompt() + "' aimed at " + aimed());
                }
                return false;
            }
            int expected = game.player.abilities.instantBuild() ? FixtureStates.LOCK_KEYED : FixtureStates.LOCK_BARRED;
            if (lock(door) == expected) {
                hold(GLFW_KEY_LEFT_CONTROL, false);
                return true;
            }
            hold(GLFW_KEY_LEFT_CONTROL, true);
            lookAtBlock(door.position(), .9f);
            if ((wait += dt) > .45f) {
                if (++presses > 8) { throw fail("the door at " + door.position() + " was never barred: " + prompt()); }
                press(GLFW_KEY_F);
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** At the bed: its prompt says the refuge is shut tight, as its ways in read. */
    private final class ShutTight extends Task {
        private final String label;

        ShutTight(String label) { super("the refuge shut tight"); this.label = label; }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(plan.bed().position(), .4f);
            if (age < .6f) { return false; }
            RefugeExposure.Assessment a = exposure();
            if (a == null || !a.shutTight() || !prompt().equals(SHUT_PROMPT)) {
                if (age > 6) { throw fail("the refuge is not shut tight: '" + prompt() + "' " + (a == null ? null : a.ways())); }
                return false;
            }
            shut = true;
            views.add(label + "=" + prompt());
            capture(label);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A walk inside the shut refuge: a threat outside it cannot be reached, so it is not chased. */
    private final class Inside extends Task {
        private final Walk walk;

        Inside(Vec3i goal) { super("walk inside the refuge to " + goal); walk = new Walk(goal); }

        @Override boolean step(float dt) { return walk.step(dt); }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Threats
    // ------------------------------------------------------------------

    private Npc spawn(NpcArchetype archetype, Vec3i at) {
        Npc n = game.entities.spawnNpc(game.world, archetype.displayName, at.x() + .5f, at.y() + .02f, at.z() + .5f);
        n.archetype = archetype;
        n.health = n.maxHealth = archetype.maxHealth;
        System.out.println("[urban-refuge] staged-infected " + archetype.id + " at " + at);
        return n;
    }

    /** Walks to and fro in the hall behind the braced leaf: ordinary footsteps anything outside may hear. */
    private final class Pacing {
        private Walk walk;
        private boolean far;

        void step(float dt) {
            if (walk == null) { walk = new Walk(far ? plan.hallFar() : plan.hallNear()); }
            if (walk.step(dt)) {
                walk = null;
                far = !far;
            }
        }
    }

    /** True while every other defence stands as it was set: the barred leaf and the rear door. */
    private boolean othersStand() {
        int expected = game.player.abilities.instantBuild() ? FixtureStates.LOCK_KEYED : FixtureStates.LOCK_BARRED;
        if (!Fixtures.isClosedDoor(block(plan.barred().position())) || lock(plan.barred()) != expected) { return false; }
        for (Fixture f : plan.rear()) {
            if (!Fixtures.isClosedDoor(block(f.position())) || lock(f) != expected) { return false; }
        }
        return true;
    }

    /**
     * One Breacher on the street before the braced leaf (printed as staged). In Survival it hears
     * the footsteps in the hall, hammers at the braced leaf (the brace takes its blows before the
     * door does), breaks in and is put down at the breach; nothing else is touched. Nobody hears a
     * Creative builder: there the door stands untouched.
     */
    private final class BreacherAtTheDoor extends Task {
        private final Pacing pacing = new Pacing();
        private float since;
        private String exposed;

        BreacherAtTheDoor() { super("a breacher at the street door"); }

        @Override boolean step(float dt) {
            if (breacher == null) {
                breacher = spawn(NpcArchetype.BREACHER, plan.threat());
                return false;
            }
            Fixture door = plan.braced();
            Float brace = Fortification.braceAt(game, door.position());
            if (!othersStand()) { throw fail("a defence the breacher never reached gave way"); }
            if (!survival()) {
                pacing.step(dt);
                if (breacher.breachDoor != null || breacher.searchTimer > 0 || brace == null
                        || brace != UrbanFortifications.BRACE_STRENGTH) {
                    throw fail("something hunted a Creative builder: search=" + breacher.searchTimer + " brace=" + brace);
                }
                if (!hammered && age > 8) {
                    hammered = true;
                    capture("08-a-breacher-outside");
                }
                if (age < 20) { return false; }
                hold(GLFW_KEY_W, false);
                breached = breacherDown = true;
                deeds.add("creative: the breacher never heard the builder; brace " + Math.round(brace));
                capture("09-after-the-breacher");
                return true;
            }
            if (age > 150) { throw fail("the breacher never broke in: brace=" + brace + " leaf=" + block(door.position())); }
            if (!breached) {
                pacing.step(dt);
                if (!hammered && brace != null && brace < UrbanFortifications.BRACE_STRENGTH) {
                    hammered = true;
                    if (Fixtures.isBroken(block(door.position()))) { throw fail("the door broke before its brace"); }
                    deeds.add(String.format(Locale.ROOT, "the brace took the first blow at %.0fs: %d/%d", age, Math.round(brace),
                            Math.round(UrbanFortifications.BRACE_STRENGTH)));
                    hold(GLFW_KEY_W, false);
                    lookAtBlock(door.position(), .9f); // the capture faces the leaf under the blows
                    capture("08-a-breacher-at-the-brace");
                }
                if (Fixtures.isBroken(block(door.position()))) {
                    if (!hammered || brace != null) { throw fail("the door gave way with its brace " + brace); }
                    breached = true;
                    hold(GLFW_KEY_W, false);
                    exposed = RefugeSystem.exposureLine(exposure(), false);
                    deeds.add(String.format(Locale.ROOT, "the door gave way at %.0fs; the refuge then: %s", age, exposed));
                    if (exposed.equals("shut tight")) { throw fail("a broken door left the refuge shut tight"); }
                }
                return false;
            }
            if (!breacher.dead) {
                if (!game.entities.npcs.contains(breacher)) { throw fail("the breacher left without being put down"); }
                look(breacher.pos.x, breacher.pos.y + 1f, breacher.pos.z);
                return false;
            }
            if ((since += dt) < 1f) { return false; }
            breacherDown = true;
            deeds.add("put down at " + cell(breacher.pos.x, breacher.pos.y, breacher.pos.z)
                    + " health " + Math.round(game.player.health));
            capture("09-the-breacher-put-down");
            return true;
        }

        @Override boolean threatsInterrupt() { return breached; }
    }

    /**
     * Survival: two barricades into the broken doorway's two cells with the use button, and the
     * refuge is shut tight again. Creative: the leaf the breacher never reached still stands, braced.
     */
    private final class BoardUp extends Task {
        private Walk walk;
        private int cell;
        private float wait, aim;
        private Object mark;

        BoardUp() { super("board up the broken doorway"); }

        @Override boolean step(float dt) {
            Fixture door = plan.braced();
            if (walk == null) { walk = new Walk(plan.hallNear()); }
            if (!walk.step(dt)) { return false; }
            if (!survival()) {
                hold(GLFW_KEY_W, false);
                lookAtBlock(door.position(), .9f);
                if ((wait += dt) < .6f) { return false; }
                if (!Fixtures.isClosedDoor(block(door.position())) || Fortification.braceAt(game, door.position()) == null) {
                    throw fail("the Creative refuge's braced leaf did not stand");
                }
                boarded = true;
                capture("10-the-street-door-after");
                return true;
            }
            if (cell < 2) {
                Vec3i c = door.position().offset(0, cell, 0);
                lookAtBlock(c, .5f);
                if (block(c) == BlockType.BARRICADE) {
                    cell++;
                    wait = aim = 0;
                    return false;
                }
                if (!hand(ItemType.BARRICADE)) { throw fail("no barricade to hand: " + slots()); }
                aim += dt;
                if ((wait += dt) < .4f) { return false; }
                if (!prompt().contains("Board up the broken doorway")) {
                    if (aim > 6) { throw fail("the broken doorway reads '" + prompt() + "'"); }
                    return false;
                }
                if (mark == null) { mark = logMark(); }
                rightClick();
                wait = 0;
                return false;
            }
            RefugeExposure.Assessment a = exposure();
            if (a == null || !a.shutTight() || !loggedSince(mark, "You barricade the broken doorway.")) {
                throw fail("the boarded doorway left the refuge open: " + (a == null ? null : a.ways()));
            }
            boarded = true;
            deeds.add("boarded the breach: " + fortress());
            capture("10-the-breach-boarded-up");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * A barricade put up on the hall floor, in the way of the hall, then taken down crouched by
     * holding the attack button with no tool: in Survival it gives back what the salvage table says.
     */
    private final class Misplaced extends Task {
        private Walk walk;
        private int phase;
        private float wait, work, up;
        private Map<ItemType, Integer> before;
        private List<ItemStack> back;

        Misplaced() { super("a barricade in the way, and down again"); }

        @Override boolean step(float dt) {
            Vec3i c = plan.hallFar();
            if (walk == null) { walk = new Walk(plan.hallNear()); } // never on the cell it goes up in
            if (!walk.step(dt)) { return false; }
            hold(GLFW_KEY_W, false);
            switch (phase) {
                case 0 -> {
                    look(c.x() + .5f, c.y() - .02f, c.z() + .5f); // the floor's top face, so it goes up on the hall floor
                    if (block(c) != BlockType.BARRICADE && !hand(ItemType.BARRICADE)) {
                        throw fail("no barricade left for the hall: " + slots());
                    }
                    up += dt;
                    if ((wait += dt) < .5f) { return false; }
                    if (block(c) == BlockType.BARRICADE) {
                        misplaced = true;
                        capture("11-a-barricade-in-the-way");
                        phase = 1;
                        wait = 0;
                        return false;
                    }
                    if (up > 8) { throw fail("the barricade never went up at " + c + ": " + block(c) + " '" + prompt() + "'"); }
                    rightClick();
                    wait = 0;
                }
                case 1 -> {
                    hold(GLFW_KEY_LEFT_CONTROL, true);
                    lookAtBlock(c, .5f);
                    if ((wait += dt) < .4f) { return false; }
                    if (before == null) {
                        before = counts();
                        back = SalvageTable.yields(game.world, c, BlockType.BARRICADE);
                    }
                    if (block(c) == BlockType.BARRICADE) {
                        leftMouse(true);
                        if ((work += dt) > 10) { throw fail("the misplaced barricade never came down: '" + prompt() + "'"); }
                        return false;
                    }
                    leftMouse(false);
                    hold(GLFW_KEY_LEFT_CONTROL, false);
                    Map<ItemType, Integer> after = counts();
                    for (ItemStack s : back) {
                        int got = after.getOrDefault(s.type, 0) - before.getOrDefault(s.type, 0);
                        if (got != (survival() ? s.count : 0)) { throw fail("taking it down gave " + got + " " + s.type); }
                    }
                    if (block(c) != BlockType.AIR) { throw fail("the barricade left " + block(c)); }
                    takenDown = true;
                    deeds.add("taken down for " + (survival() ? SalvageTable.describe(back) : "nothing (Creative)"));
                    capture("12-taken-down");
                    return true;
                }
                default -> { }
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * One Veilbound on the street (printed as staged), which cannot break anything. In Survival it
     * hears the footsteps in the hall and comes looking, finds no way into the shut refuge, never
     * touches a defence and loses the trail once the hall falls quiet; a Creative builder it never
     * notices at all.
     */
    private final class KeptOut extends Task {
        private final Pacing pacing = new Pacing();
        private float heard = -1, quiet;
        private String defences;

        KeptOut() { super("a veilbound kept out"); }

        @Override boolean step(float dt) {
            if (veilbound == null) {
                defences = fortress();
                veilbound = spawn(NpcArchetype.VEILBOUND, plan.threat());
                return false;
            }
            if (veilbound.dead || !game.entities.npcs.contains(veilbound)) { throw fail("the veilbound is gone"); }
            if (insideFrames > 0) { throw fail("the veilbound got in at " + veilbound.pos); }
            if (!fortress().equals(defences)) { throw fail("a defence changed:\n before " + defences + "\n now    " + fortress()); }
            if (!survival()) {
                pacing.step(dt);
                if (veilbound.searchTimer > 0) { throw fail("a veilbound noticed a Creative builder"); }
                if (age < 10) { return false; }
                hold(GLFW_KEY_W, false);
                keptOut = true;
                capture("13-a-veilbound-outside");
                return true;
            }
            if (heard < 0) {
                pacing.step(dt);
                if (veilbound.searchTimer > 0) { heard = age; }
                if (age > 40) { throw fail("the veilbound never heard the hall: at " + veilbound.pos); }
                return false;
            }
            if (age - heard < 15) {
                pacing.step(dt);
                return false;
            }
            hold(GLFW_KEY_W, false);
            hold(GLFW_KEY_LEFT_CONTROL, true); // still and quiet: it loses the trail
            if (!keptOut) {
                keptOut = true;
                deeds.add(String.format(Locale.ROOT, "the veilbound heard the hall at %.0fs and was kept out, at %s", heard,
                        cell(veilbound.pos.x, veilbound.pos.y, veilbound.pos.z)));
                capture("13-a-veilbound-kept-out");
            }
            quiet = veilbound.searchTimer > 0 ? 0 : quiet + dt;
            if (quiet < 1f) {
                if (age > 120) { throw fail("the veilbound never gave up: search=" + veilbound.searchTimer); }
                return false;
            }
            hold(GLFW_KEY_LEFT_CONTROL, false);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // The night, the save and the camp
    // ------------------------------------------------------------------

    /**
     * F on the bed at night: the sleep starts with the refuge shut tight though the Veilbound is
     * outside, nothing is drawn to it, and the sleeper wakes quietly.
     */
    private final class Night extends Task {
        private Object mark;

        Night() { super("sleep in the refuge"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            if (mark == null) {
                lookAtBlock(plan.bed().position(), .4f);
                if (age < .6f) { return false; }
                if (!prompt().equals(SHUT_PROMPT)) {
                    if (age > 6) { throw fail("at night the bed reads '" + prompt() + "'"); }
                    return false;
                }
                mark = logMark();
                press(GLFW_KEY_F);
                return false;
            }
            if (!slept) {
                if (!game.sleeping) {
                    if (age > 4) { throw fail("no sleep: " + loggedAfter(mark)); }
                    return false;
                }
                if (!loggedSince(mark, "Your refuge is shut tight.")) { throw fail("the night began exposed: " + loggedAfter(mark)); }
                if (veilbound != null && !veilbound.dead && veilbound.searchTimer > 0) { throw fail("the night drew the veilbound"); }
                slept = true;
                deeds.add("asleep: " + String.join(" / ", loggedAfter(mark)));
                capture("14-asleep-in-the-refuge");
                return false;
            }
            if (game.sleeping) {
                if (age > 60) { throw fail("the night never ended"); }
                return false;
            }
            List<String> lines = loggedAfter(mark);
            if (lines.stream().noneMatch(l -> l.contains("You wake after"))
                    || lines.stream().anyMatch(l -> l.contains("with a start") || l.contains("attacked"))) {
                throw fail("not a quiet night: " + lines);
            }
            if (age < 1.5f || game.sleepFade > .05f) { return false; } // the screen clear of the night's fade
            woke = true;
            deeds.add("woke: " + lines.getLast());
            capture("15-morning-in-the-refuge");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * Takes the staged bodies still about out of the world, printed as unstaged: a staged body is
     * no encounter record of the city, so a save would refuse it.
     */
    private final class Unstage extends Task {
        Unstage() { super("unstage the staged infected"); }

        @Override boolean step(float dt) {
            for (Npc n : new Npc[]{breacher, veilbound}) {
                if (n != null && !n.dead && game.entities.removeNpc(game, n)) {
                    System.out.println("[urban-refuge] unstaged-infected " + n.archetype.id + " at "
                            + cell(n.pos.x, n.pos.y, n.pos.z));
                }
            }
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Record extends Task {
        Record() { super("record what the refuge keeps"); }

        @Override boolean step(float dt) {
            before = fortress();
            System.out.println("[urban-refuge] before-save " + before);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** After the load: the same claims, storage, salvage and defences. */
    private final class Reloaded extends Task {
        Reloaded() { super("the same refuge after the load"); }

        @Override boolean step(float dt) {
            String after = fortress();
            if (!after.equals(before)) { throw fail("the refuge changed across the save:\n before " + before + "\n after  " + after); }
            lookAtBlock(plan.bed().position(), .4f);
            if (age < .8f) { return false; }
            reloaded = true;
            capture("16-after-reload");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Crouch and F on a bed of the camp's: refused, the rooms being theirs, and nothing is claimed. */
    private final class TheirBed extends Task {
        private Object mark;
        private float wait;

        TheirBed() { super("a bed in the camp"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            hold(GLFW_KEY_LEFT_CONTROL, true);
            lookAtBlock(plan.campBed().position(), .4f);
            if ((wait += dt) < .5f) { return false; }
            if (mark == null) {
                if (!prompt().contains(THEIRS)) {
                    if (age > 6) { throw fail("the camp's bed reads '" + prompt() + "'"); }
                    return false;
                }
                views.add("17-their-rooms-refused=" + prompt());
                mark = logMark();
                press(GLFW_KEY_F);
                return false;
            }
            if (!loggedSince(mark, THEIRS)) {
                if (age > 8) { throw fail("no refusal: " + loggedAfter(mark)); }
                return false;
            }
            UrbanSite camp = UrbanSiteDirector.registered(game.world, plan.camp().site().id());
            if (game.world.urban.refuges.size() != 2 || game.sleeping || camp != null && camp.hostile()) {
                throw fail("the camp's bed was taken: claims=" + game.world.urban.refuges.size() + " sleeping=" + game.sleeping);
            }
            hold(GLFW_KEY_LEFT_CONTROL, false);
            refused = true;
            capture("17-their-rooms-refused");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            releaseAll();
            if (!claimed || !stored || !salvaged || !crafted || !braced || !shut || !hammered || !breached || !breacherDown
                    || !boarded || !misplaced || !takenDown || !keptOut || !slept || !woke || !reloaded || !refused
                    || captures != CAPTURES) {
                throw fail("tour incomplete: claimed=" + claimed + " stored=" + stored + " salvaged=" + salvaged
                        + " crafted=" + crafted + " braced=" + braced + " shut=" + shut + " hammered=" + hammered
                        + " breached=" + breached + " down=" + breacherDown + " boarded=" + boarded + " misplaced=" + misplaced
                        + " takenDown=" + takenDown + " keptOut=" + keptOut + " slept=" + slept + " woke=" + woke
                        + " reloaded=" + reloaded + " refused=" + refused + " captures=" + captures);
            }
            if (game.world.urban.refuges.homeFloor() != floor()) { throw fail("the flat is no longer home"); }
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-refuge] pass=true mode=%s seed=%d poses=%d captures=%d"
                            + " engaged=%s defeated=%s health=%.1f routeSeconds=%.0f%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, poses, captures,
                    engaged, defeated, game.player.health, elapsed);
            System.out.println("[urban-refuge] refuge " + fortress());
            System.out.println("[urban-refuge] deeds " + String.join(" | ", deeds));
            System.out.println("[urban-refuge] views " + String.join(" | ", views));
            System.out.println("[urban-refuge] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }
}

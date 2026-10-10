package com.veylon;

import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.FixtureGeometry;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the player and residents do with doors, windows and urban furniture
 * (urban package 04, decisions D-48 to D-53).
 *
 * <p>One decision, {@link #decide}, drives both the F key and the HUD prompt,
 * so the hint can never promise something the key does not do.
 *
 * <ul>
 *   <li>F on a closed door or window opens the whole opening; F on an open one
 *       closes it unless a body stands in it.</li>
 *   <li>A keyed lock holds from both sides: F fails (a rattle, audible to
 *       residents) and the way in is to force it or use another opening.</li>
 *   <li>Crouch and F bars a door or latches a window from the side the player
 *       stands on; only that side can lift it. In Creative, crouch and F
 *       toggles a keyed lock instead, so builders can stage locked doors.</li>
 *   <li>Breaking (holding attack) in Survival forces a closed door into a
 *       broken doorway, shatters one pane of a closed window, and dismantles an
 *       open door or window into its item. Creative removes the block.</li>
 *   <li>Residents shoulder closed, unlocked doors on their path open; those
 *       doors swing shut again by themselves and are saved closed.</li>
 * </ul>
 *
 * <p>Salvage never conjures material: an opening yields at most the one item
 * that places it, and a break whose items do not all fit in the inventory is
 * refused rather than losing them.
 */
public final class FixtureActions {

    /** What pressing F on an operable fixture would do. */
    enum Action {
        NONE, OPEN, CLOSE, BLOCKED, LOCKED, BAR, UNBAR, BARRED_OTHER_SIDE, LOCK, UNLOCK, FULL
    }

    /** Seconds a resident's door stays open behind them. */
    static final float RESIDENT_DOOR_SECONDS = 4f;
    /** Retry delay while someone still stands in a closing doorway. */
    private static final float OCCUPIED_RETRY_SECONDS = 1.5f;
    /** Damage from punching out a pane with a bare hand. */
    static final float BARE_HAND_GLASS_DAMAGE = 1.5f;

    private static final float DOOR_NOISE_RADIUS = 8f;
    private static final float DOOR_NOISE = 0.15f;
    private static final float METAL_DOOR_NOISE_RADIUS = 12f;
    private static final float METAL_DOOR_NOISE = 0.25f;
    private static final float WINDOW_NOISE_RADIUS = 5f;
    private static final float WINDOW_NOISE = 0.08f;
    private static final float RATTLE_NOISE_RADIUS = 10f;
    private static final float RATTLE_NOISE = 0.25f;
    static final float FORCED_NOISE_RADIUS = 36f;
    static final float FORCED_NOISE = 0.7f;
    static final float GLASS_NOISE_RADIUS = 28f;
    static final float GLASS_NOISE = 0.6f;

    private FixtureActions() {
    }

    // ------------------------------------------------------------------
    // The player's F key
    // ------------------------------------------------------------------

    /** The action F would perform on the fixture at a position; NONE for anything else. */
    static Action decide(Game g, int x, int y, int z) {
        World w = g.world;
        BlockType t = w.getBlock(x, y, z);
        Fixtures.Kind kind = Fixtures.kindOf(t);
        if (kind == null) {
            return Action.NONE;
        }
        int bottom = Fixtures.stackBottom(w, x, y, z, kind);
        int height = Fixtures.stackHeight(w, x, bottom, z, kind);
        // Every state change below writes each cell of the opening to the map.
        boolean room = roomFor(w, x, bottom, z, height);
        if (Fixtures.isOpen(t)) {
            return bodyInOpening(g, x, bottom, z, height) ? Action.BLOCKED
                    : room ? Action.CLOSE : Action.FULL;
        }
        int lock = w.fixtureLockAt(x, bottom, z);
        if (g.player.abilities.instantBuild() && g.player.crouching) {
            return lock != FixtureStates.LOCK_NONE ? Action.UNLOCK : room ? Action.LOCK : Action.FULL;
        }
        if (lock == FixtureStates.LOCK_KEYED) {
            return Action.LOCKED;
        }
        if (lock == FixtureStates.LOCK_BARRED) {
            return onBarSide(g, w.fixtures.stateAt(x, bottom, z), x, z)
                    ? Action.UNBAR : Action.BARRED_OTHER_SIDE;
        }
        if (!room) {
            return Action.FULL;
        }
        return g.player.crouching ? Action.BAR : Action.OPEN;
    }

    /** The HUD line for the fixture at a position, or null when it is not one. */
    static String prompt(Game g, int x, int y, int z) {
        BlockType t = g.world.getBlock(x, y, z);
        Fixtures.Kind kind = Fixtures.kindOf(t);
        if (kind == null) {
            return null;
        }
        String noun = kind.door ? "door" : "window";
        String bar = kind.door ? "bar" : "latch";
        String forceTool = t.requiresTool
                ? " with a " + t.preferredTool.name().toLowerCase(Locale.ROOT) : "";
        return switch (decide(g, x, y, z)) {
            case OPEN -> "[F] Open " + noun + "  (crouch: " + bar + " it from this side)";
            case CLOSE -> "[F] Close " + noun;
            case BLOCKED -> "Something is in the " + (kind.door ? "doorway" : "window");
            case LOCKED -> kind.door
                    ? "Locked. Force it open" + forceTool + " (loud) or find another way in"
                    : "Locked. Break the glass to get in (loud)";
            case BAR -> "[F] " + capitalize(bar) + " the " + noun + " from this side";
            case UNBAR -> kind.door ? "[F] Lift the bar" : "[F] Unlatch the window";
            case BARRED_OTHER_SIDE -> kind.door
                    ? "Barred from the other side. Force it" + forceTool + " (loud) or go around"
                    : "Latched from the other side. Break the glass (loud) or go around";
            case LOCK -> "[F] Lock " + noun + " (Creative)";
            case UNLOCK -> "[F] Unlock " + noun + " (Creative)";
            case FULL -> "This " + noun + " cannot change state: too many fixtures remembered";
            case NONE -> null;
        };
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /**
     * Performs the F-key action on an operable fixture.
     *
     * @return true when the position holds an operable fixture (the key press is
     *         consumed, even by a failed attempt on a locked door)
     */
    static boolean interact(Game g, int x, int y, int z) {
        World w = g.world;
        BlockType t = w.getBlock(x, y, z);
        Fixtures.Kind kind = Fixtures.kindOf(t);
        if (kind == null) {
            return false;
        }
        int bottom = Fixtures.stackBottom(w, x, y, z, kind);
        int height = Fixtures.stackHeight(w, x, bottom, z, kind);
        float cx = x + 0.5f, cy = bottom + 1f, cz = z + 0.5f;
        String noun = kind.door ? "door" : "window";
        switch (decide(g, x, y, z)) {
            case OPEN -> {
                setOpening(w, x, bottom, z, height, kind, true, FixtureStates.LOCK_NONE);
                w.fixtures.transitTimers().remove(new Vec3i(x, bottom, z));
                sound(g, kind, cx, cy, cz, true);
                emitNoise(g, kind, cx, cy, cz, "door");
            }
            case CLOSE -> {
                setOpening(w, x, bottom, z, height, kind, false, FixtureStates.LOCK_NONE);
                w.fixtures.transitTimers().remove(new Vec3i(x, bottom, z));
                sound(g, kind, cx, cy, cz, false);
                emitNoise(g, kind, cx, cy, cz, "door");
            }
            case BLOCKED -> g.log("Something is in the way.");
            case LOCKED, BARRED_OTHER_SIDE -> {
                g.audio.playDoorRattle(cx, cy, cz);
                g.noise.emit(g, cx, cy, cz, RATTLE_NOISE_RADIUS, RATTLE_NOISE, "door-rattle",
                        true, g.player);
                g.log(prompt(g, x, y, z) + ".");
            }
            case BAR -> {
                setLock(w, x, bottom, z, height, barState(g, w, x, bottom, z));
                sound(g, kind, cx, cy, cz, false);
                g.log(kind.door ? "You bar the door from this side."
                        : "You latch the window from this side.");
            }
            case UNBAR -> {
                setLock(w, x, bottom, z, height, FixtureStates.LOCK_NONE);
                sound(g, kind, cx, cy, cz, false);
                g.log(kind.door ? "You lift the bar." : "You unlatch the window.");
            }
            case LOCK -> {
                setLock(w, x, bottom, z, height, FixtureStates.LOCK_KEYED);
                g.log("The " + noun + " is now locked.");
            }
            case UNLOCK -> {
                setLock(w, x, bottom, z, height, FixtureStates.LOCK_NONE);
                g.log("The " + noun + " is now unlocked.");
            }
            case FULL -> g.log(prompt(g, x, y, z) + ".");
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    /** Opens or closes every cell of an opening with record = false, keeping the state map in step. */
    private static void setOpening(World w, int x, int bottom, int z, int height,
                                   Fixtures.Kind kind, boolean open, int lock) {
        int state = (open ? FixtureStates.OPEN : 0) | (open ? FixtureStates.LOCK_NONE : lock);
        for (int i = 0; i < height; i++) {
            w.setBlock(x, bottom + i, z, kind.variant(open), false);
            w.fixtures.set(x, bottom + i, z, state);
        }
    }

    private static void setLock(World w, int x, int bottom, int z, int height, int lockState) {
        for (int i = 0; i < height; i++) {
            w.fixtures.set(x, bottom + i, z, lockState);
        }
    }

    /** True when every cell of an opening can be written to the state map. */
    private static boolean roomFor(World w, int x, int bottom, int z, int height) {
        int missing = 0;
        for (int i = 0; i < height; i++) {
            if (w.fixtures.stateAt(x, bottom + i, z) < 0) {
                missing++;
            }
        }
        return missing == 0 || w.fixtures.size() + missing <= FixtureStates.MAX_ENTRIES;
    }

    /** A bar set from the player's side of the opening. */
    private static int barState(Game g, World w, int x, int bottom, int z) {
        boolean facesZ = FixtureGeometry.wallAlongX(w, x, bottom, z);
        boolean positive = playerOffset(g, facesZ, x, z) >= 0;
        return FixtureStates.LOCK_BARRED
                | (positive ? FixtureStates.BAR_POSITIVE : 0)
                | (facesZ ? FixtureStates.BAR_AXIS_Z : 0);
    }

    private static boolean onBarSide(Game g, int state, int x, int z) {
        boolean facesZ = (state & FixtureStates.BAR_AXIS_Z) != 0;
        boolean positive = (state & FixtureStates.BAR_POSITIVE) != 0;
        return (playerOffset(g, facesZ, x, z) >= 0) == positive;
    }

    private static float playerOffset(Game g, boolean facesZ, int x, int z) {
        return facesZ ? g.player.pos.z - (z + 0.5f) : g.player.pos.x - (x + 0.5f);
    }

    private static boolean bodyInOpening(Game g, int x, int bottom, int z, int height) {
        if (overlaps(g.player, x, bottom, z, height)) {
            return true;
        }
        for (Npc n : g.entities.npcs) {
            if (!n.dead && overlaps(n, x, bottom, z, height)) {
                return true;
            }
        }
        for (var c : g.entities.creatures) {
            if (!c.dead && overlaps(c, x, bottom, z, height)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(Entity e, int x, int bottom, int z, int height) {
        float hw = e.width / 2f;
        return e.pos.x + hw > x && e.pos.x - hw < x + 1
                && e.pos.y + e.height > bottom && e.pos.y < bottom + height
                && e.pos.z + hw > z && e.pos.z - hw < z + 1;
    }

    private static void sound(Game g, Fixtures.Kind kind, float x, float y, float z, boolean opening) {
        if (kind.door) {
            g.audio.playDoor(x, y, z, opening, kind == Fixtures.Kind.METAL_DOOR);
        } else {
            g.audio.playWindow(x, y, z);
        }
    }

    private static void emitNoise(Game g, Fixtures.Kind kind, float x, float y, float z, String category) {
        float radius = kind == Fixtures.Kind.METAL_DOOR ? METAL_DOOR_NOISE_RADIUS
                : kind.door ? DOOR_NOISE_RADIUS : WINDOW_NOISE_RADIUS;
        float strength = kind == Fixtures.Kind.METAL_DOOR ? METAL_DOOR_NOISE
                : kind.door ? DOOR_NOISE : WINDOW_NOISE;
        g.noise.emit(g, x, y, z, radius, strength, category, true, g.player);
    }

    // ------------------------------------------------------------------
    // Residents
    // ------------------------------------------------------------------

    /**
     * A resident opens the closed, unlocked door whose cell they are about to
     * enter. The door swings shut again after {@link #RESIDENT_DOOR_SECONDS}.
     * Windows and locked or barred doors are left alone; the path finder never
     * routes through them.
     *
     * @return true when a door was opened
     */
    public static boolean residentOpens(Game g, int x, int y, int z) {
        World w = g.world;
        BlockType t = w.getBlock(x, y, z);
        if (!Fixtures.isClosedDoor(t)) {
            return false;
        }
        Fixtures.Kind kind = Fixtures.kindOf(t);
        int bottom = Fixtures.stackBottom(w, x, y, z, kind);
        int height = Fixtures.stackHeight(w, x, bottom, z, kind);
        if (w.fixtureLockAt(x, bottom, z) != FixtureStates.LOCK_NONE
                || !roomFor(w, x, bottom, z, height)) {
            return false;
        }
        setOpening(w, x, bottom, z, height, kind, true, FixtureStates.LOCK_NONE);
        Map<Vec3i, Float> timers = w.fixtures.transitTimers();
        Vec3i key = new Vec3i(x, bottom, z);
        if (timers.containsKey(key) || timers.size() < FixtureStates.MAX_TRANSIT) {
            timers.put(key, RESIDENT_DOOR_SECONDS);
        }
        g.audio.playDoor(x + 0.5f, bottom + 1f, z + 0.5f, true, kind == Fixtures.Kind.METAL_DOOR);
        return true;
    }

    /** Swings residents' doors shut once nobody stands in them. Runs on the fast tick. */
    public static void tickResidentDoors(Game g, float dt) {
        Map<Vec3i, Float> timers = g.world.fixtures.transitTimers();
        if (timers.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Vec3i, Float>> it = timers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Vec3i, Float> e = it.next();
            float left = e.getValue() - dt;
            if (left > 0) {
                e.setValue(left);
                continue;
            }
            Vec3i p = e.getKey();
            BlockType t = g.world.getBlock(p.x(), p.y(), p.z());
            Fixtures.Kind kind = Fixtures.kindOf(t);
            if (kind == null || !kind.door || !Fixtures.isOpen(t)) {
                it.remove(); // closed, forced or removed meanwhile
                continue;
            }
            int height = Fixtures.stackHeight(g.world, p.x(), p.y(), p.z(), kind);
            if (bodyInOpening(g, p.x(), p.y(), p.z(), height)) {
                e.setValue(OCCUPIED_RETRY_SECONDS);
                continue;
            }
            it.remove();
            setOpening(g.world, p.x(), p.y(), p.z(), height, kind, false, FixtureStates.LOCK_NONE);
            g.audio.playDoor(p.x() + 0.5f, p.y() + 1f, p.z() + 0.5f, false,
                    kind == Fixtures.Kind.METAL_DOOR);
        }
    }

    // ------------------------------------------------------------------
    // Breaking: forcing, shattering, dismantling and salvage
    // ------------------------------------------------------------------

    /** True for a block a Survival break of which forces or shatters rather than removes. */
    static boolean forcesOpen(BlockType t) {
        return Fixtures.isClosed(t);
    }

    /**
     * What a player's break leaves in the broken cell: a forced doorway, a
     * shattered pane, or nothing. Creative removes every block outright.
     */
    static BlockType remainsOf(BlockType t, boolean creative) {
        if (creative) {
            return BlockType.AIR;
        }
        Fixtures.Kind kind = Fixtures.kindOf(t);
        return kind != null && t == kind.closed ? kind.broken : BlockType.AIR;
    }

    /** Doors act as one object over their whole opening; window panes break one at a time. */
    static boolean breaksWholeOpening(BlockType t) {
        Fixtures.Kind kind = Fixtures.kindOf(t);
        return kind != null && kind.door;
    }

    /**
     * The salvage a Survival break gives for an urban block: its own item for
     * materials and furniture, the opening's item for a dismantled open door or
     * window, nothing for a forced or shattered one. Legacy blocks return null
     * and keep their own drop rules.
     */
    static ItemStack salvageOf(World w, Vec3i pos, BlockType t) {
        if (!isUrbanBlock(t)) {
            return null;
        }
        Fixtures.Kind kind = Fixtures.kindOf(t);
        if (kind != null) {
            if (t != kind.open) {
                return null; // a closed one is forced or shattered: nothing to carry away
            }
            if (kind.door) {
                int bottom = Fixtures.stackBottom(w, pos.x(), pos.y(), pos.z(), kind);
                if (Fixtures.stackHeight(w, pos.x(), bottom, pos.z(), kind) < 2) {
                    return null; // half a door is not a door
                }
            }
            ItemType item = itemOf(kind);
            return new ItemStack(item, 1);
        }
        return t.drop == null || t.dropCount <= 0 ? null : new ItemStack(t.drop, t.dropCount);
    }

    private static ItemType itemOf(Fixtures.Kind kind) {
        return switch (kind) {
            case WOOD_DOOR -> ItemType.WOOD_DOOR;
            case METAL_DOOR -> ItemType.METAL_DOOR;
            case WINDOW -> ItemType.WINDOW;
        };
    }

    /** The urban materials and fixtures of package 04 (appended after the legacy blocks). */
    static boolean isUrbanBlock(BlockType t) {
        return t.ordinal() >= BlockType.ASPHALT.ordinal();
    }

    /**
     * Whether a break of an urban block may go ahead: everything it would put in
     * the inventory (a container's contents in either mode, and in Survival the
     * salvage item) must fit. Otherwise nothing moves, nothing is lost, and the
     * player is told why.
     */
    static boolean mayBreak(Game g, Vec3i pos, BlockType t, ItemStack held) {
        boolean managedContainer = com.veylon.world.urban.UrbanContainers.manages(g.world) && Fixtures.isContainer(t);
        if (!isUrbanBlock(t) && !managedContainer) {
            return true;
        }
        boolean creative = g.player.abilities.instantBuild();
        List<ItemStack> incoming = new ArrayList<>();
        if (Fixtures.isUrbanContainer(t) || managedContainer) {
            Inventory contents = g.world.crateContents.get(pos);
            if (contents != null) {
                for (int i = 0; i < contents.size(); i++) {
                    if (contents.get(i) != null) {
                        incoming.add(contents.get(i).copy());
                    }
                }
            }
        }
        if (!creative) {
            boolean toolOk = !t.requiresTool || (held != null && held.type.tool == t.preferredTool);
            ItemStack salvage = toolOk ? t == BlockType.CRATE ? new ItemStack(ItemType.CRATE,1)
                    : salvageOf(g.world, pos, t) : null;
            if (salvage != null) {
                incoming.add(salvage);
            }
        }
        if (incoming.isEmpty() || fits(g.player.inventory, incoming)) {
            return true;
        }
        g.log(Fixtures.isUrbanContainer(t) || managedContainer
                ? "No room for what is inside. Empty the " + t.displayName.toLowerCase(Locale.ROOT)
                + " first."
                : "No room to carry the " + t.displayName.toLowerCase(Locale.ROOT) + ".");
        return false;
    }

    /** True when every stack would fit, tried on a copy so the real inventory is untouched. */
    static boolean fits(Inventory inventory, List<ItemStack> stacks) {
        Inventory trial = new Inventory(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack s = inventory.get(i);
            trial.set(i, s == null ? null : s.copy());
        }
        for (ItemStack s : stacks) {
            if (trial.addStack(s.copy()) > 0) {
                return false;
            }
        }
        return true;
    }

    /** True when a Survival player breaks glass with nothing in hand, or with something that is no tool. */
    static boolean bareHanded(ItemStack held) {
        return held == null || held.type.tool == ToolKind.NONE;
    }

    /**
     * Replaces every cell of a door's opening with what a break leaves, as
     * recorded edits: forcing and dismantling are irreversible.
     */
    static void replaceOpening(World w, Vec3i pos, BlockType t, BlockType remains) {
        Fixtures.Kind kind = Fixtures.kindOf(t);
        int bottom = Fixtures.stackBottom(w, pos.x(), pos.y(), pos.z(), kind);
        int height = Fixtures.stackHeight(w, pos.x(), bottom, pos.z(), kind);
        for (int i = 0; i < height; i++) {
            w.setBlock(pos.x(), bottom + i, pos.z(), remains, true);
        }
    }

    // ------------------------------------------------------------------
    // Placing
    // ------------------------------------------------------------------

    /** True when a door placed with its bottom at this cell has room for its upper half. */
    static boolean doorFits(World w, int x, int y, int z) {
        return y >= 0 && y < Chunk.SY - 1 && w.isColumnLoaded(x, z)
                && w.getBlock(x, y + 1, z).isReplaceable();
    }
}

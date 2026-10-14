package com.veylon.world.urban;

import com.veylon.item.Inventory;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.World;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Sparse, per-world identity ledger. Inventories have exactly one owner: World.crateContents.
 * Presence means rolled, including null/empty contents. Retired identities are never evicted.
 * Player ids are negative monotonically decreasing numbers, disjoint from packed fixture ids.
 */
public final class UrbanContainers {
    public static final int MAX_ENTRIES = 32_768;
    public static final int SLOTS = 12;
    /** Codes are explicit and append-only; never serialize enum ordinals. */
    public enum State {
        UNSEARCHED(1), SEARCHING(2), SEARCHED(3), EMPTY(4), MOVED(5), DESTROYED(6);
        public final int code;
        State(int code) { this.code = code; }
        public boolean retired() { return this == MOVED || this == DESTROYED; }
        public boolean revealed() { return this == SEARCHED || this == EMPTY; }
        public static State fromCode(int code) {
            for (State value : values()) { if (value.code == code) { return value; } }
            throw new IllegalArgumentException("unknown container state " + code);
        }
    }
    public enum Owner {
        ABANDONED(1), SURVIVOR(2), PLAYER(3);
        public final int code;
        Owner(int code) { this.code = code; }
        public static Owner fromCode(int code) {
            for (Owner value : values()) { if (value.code == code) { return value; } }
            throw new IllegalArgumentException("unknown container owner " + code);
        }
    }
    public static final class Entry {
        public final long id;
        public final Vec3i position;
        public final BlockType block;
        public final Owner owner;
        /** The owning building, or NONE for abandoned/player property. */
        public final long ownerId;
        public State state;
        public float progress;
        public Entry(long id, Vec3i position, BlockType block, Owner owner, long ownerId, State state, float progress) {
            this.id=id; this.position=position; this.block=block; this.owner=owner;
            this.ownerId=ownerId; this.state=state; this.progress=progress;
        }
    }
    public record Planned(BuildingPlan building, BuildingPlan.Fixture fixture, LootTables.Table table) { }

    private final Map<Long, Entry> entries = new HashMap<>();
    private final Map<Vec3i, Entry> active = new HashMap<>();
    private long nextPlayerId = -1;
    private boolean sectionRestored;

    public int size() { return entries.size(); }
    public long nextPlayerId() { return nextPlayerId; }
    public boolean sectionRestored() { return sectionRestored; }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(entries.values()); }
    public Entry byId(long id) { return entries.get(id); }
    public Entry at(Vec3i position) { return active.get(position); }
    /** Attribution before destruction, without rolling contents or allocating an inventory. */
    public static long survivorOwner(World world, Vec3i position) {
        if (!manages(world)) { return UrbanIds.NONE; }
        Entry entry=world.urban.containers.at(position);
        if (entry!=null) { return entry.owner==Owner.SURVIVOR ? entry.ownerId : UrbanIds.NONE; }
        Planned plan=planned(world,position);
        return plan!=null && !world.urban.containers.entries.containsKey(plan.fixture.id())
                && plan.building.survivorOwned(plan.fixture) ? plan.building.id() : UrbanIds.NONE;
    }
    public static boolean manages(World world) {
        return world.urban != null && world.urban.hasGeneratedBuildings();
    }

    /** Resolves immutable reservations without creating inventories or chunks. */
    public static Planned planned(World world, Vec3i position) {
        if (!manages(world)) { return null; }
        BuildingPlan building = world.urban.buildingAt(position.x(),position.z());
        if (building == null) { return null; }
        for (var fixture : building.fixtures()) {
            if (fixture.kind() == BuildingPlan.FixtureKind.CONTAINER && fixture.position().equals(position)) {
                return new Planned(building,fixture,LootTables.table(building,fixture,
                        world.urban.districtAt(position.x(),position.z())));
            }
        }
        return null;
    }

    /** First touch commits a single deterministic roll. Legacy inventories, even empty, are authoritative. */
    public Entry touch(World world, Vec3i position) {
        Entry current = at(position);
        if (current != null) { return current; }
        BlockType block = world.getBlock(position.x(),position.y(),position.z());
        if (!Fixtures.isContainer(block) || entries.size() >= MAX_ENTRIES) { return null; }
        Planned plan = planned(world,position);
        if (plan == null || plan.fixture.block() != block || world.changedBlocks.containsKey(position)) {
            return player(position,block);
        }
        if (entries.containsKey(plan.fixture.id())) { return null; }
        boolean legacy = world.crateContents.containsKey(position);
        Entry entry = generated(plan,legacy ? State.SEARCHED : State.UNSEARCHED);
        put(entry);
        if (!legacy) {
            Inventory rolled = world.urban.hasStarter() && world.urban.contentRevision() == 3
                    ? UrbanStarterSite.supplies(plan.building, plan.fixture) : null;
            if (rolled == null) {
                rolled = LootTables.roll(world.seed,world.urban.contentRevision(),entry.id,plan.table,
                        world.urban.districtAt(position.x(),position.z()),plan.building.variant());
            }
            if (rolled != null) { world.crateContents.put(position,rolled); }
        }
        compact(world,entry);
        return entry;
    }

    /** Reserve all required identities before a block mutation. At the cap, fail closed without moving anything. */
    public boolean changing(World world, Vec3i position, BlockType old, BlockType replacement) {
        if (!manages(world) || !world.urban.contentRevisionResolved()) { return true; }
        Entry prior = at(position);
        Planned plan = Fixtures.isContainer(old) && prior == null ? planned(world,position) : null;
        boolean newTombstone = plan != null && !entries.containsKey(plan.fixture.id());
        int needed = (newTombstone ? 1 : 0) + (Fixtures.isContainer(replacement) ? 1 : 0);
        if (entries.size() + needed > MAX_ENTRIES) { return false; }
        if (newTombstone) { prior = generated(plan,State.DESTROYED); put(prior); }
        if (prior != null && !prior.state.retired()) {
            prior.state = State.DESTROYED; prior.progress = 0; active.remove(position);
        }
        if (Fixtures.isContainer(old)) { world.crateContents.remove(position); }
        if (Fixtures.isContainer(replacement)) { player(position,replacement); }
        return true;
    }

    private Entry generated(Planned plan, State state) {
        boolean owned = plan.building.survivorOwned(plan.fixture);
        return new Entry(plan.fixture.id(),plan.fixture.position(),plan.fixture.block(),
                owned ? Owner.SURVIVOR : Owner.ABANDONED, owned ? plan.building.id() : UrbanIds.NONE,state,0);
    }
    private Entry player(Vec3i position, BlockType block) {
        if (entries.size() >= MAX_ENTRIES || nextPlayerId == Long.MIN_VALUE) { return null; }
        Entry entry = new Entry(nextPlayerId--,position,block,Owner.PLAYER,UrbanIds.NONE,State.EMPTY,0);
        put(entry); return entry;
    }
    private void put(Entry entry) {
        entries.put(entry.id,entry);
        if (!entry.state.retired()) { active.put(entry.position,entry); }
    }

    /** Successful hand salvage moves contents to the player; the original fixture identity stays retired. */
    public void moved(Entry entry) {
        entry.state=State.MOVED; entry.progress=0; active.remove(entry.position);
    }
    /** Removes empty Inventory allocations, retaining an explicit empty fact. Safe outside map iteration. */
    public void compact(World world, Entry entry) {
        Inventory inventory = world.crateContents.get(entry.position);
        boolean empty=inventory==null || inventory.isEmpty();
        if (entry.state.revealed()) {
            entry.state=empty ? State.EMPTY : State.SEARCHED;
            // Old content-one saves have optional sections. Keep a core empty marker
            // for touched empties so dropping/ignoring the new section cannot refill them.
            if (empty && world.urban.contentRevision()==1 && inventory==null) {
                inventory=new Inventory(SLOTS); world.crateContents.put(entry.position,inventory);
            }
        }
        if (inventory!=null && empty && world.urban.contentRevision()!=1) { world.crateContents.remove(entry.position); }
    }
    public void prepareSave(World world) {
        for (Vec3i position : world.crateContents.keySet()) {
            if (at(position) != null) { continue; }
            if (entries.size() >= MAX_ENTRIES) { throw new IllegalArgumentException("too many container inventories"); }
            Planned plan=planned(world,position);
            BlockType live=world.getBlock(position.x(),position.y(),position.z());
            if (!Fixtures.isContainer(live)) { throw new IllegalArgumentException("orphan container inventory"); }
            if (plan != null && plan.fixture.block() == live && !entries.containsKey(plan.fixture.id())) {
                put(generated(plan,State.SEARCHED));
            } else { player(position,live); }
        }
        for (Entry entry : entries.values()) { if (!entry.state.retired()) { compact(world,entry); } }
    }

    /** Reader applies validated rows only; new/load owns a fresh ledger, never a session-global map. */
    public void restore(Entry entry) {
        if (entries.size() >= MAX_ENTRIES || entries.containsKey(entry.id)
                || !entry.state.retired() && active.containsKey(entry.position)) {
            throw new IllegalArgumentException("duplicate or excessive container state");
        }
        put(entry);
    }
    public void restoreNextPlayerId(long next) {
        if (next >= 0 || next == Long.MIN_VALUE) { throw new IllegalArgumentException("invalid next player container id"); }
        for (Entry entry : entries.values()) {
            if (entry.id < 0 && entry.id <= next) { throw new IllegalArgumentException("reused player container id"); }
        }
        nextPlayerId=next;
        sectionRestored=true;
    }

    /** Missing old section: preserve core inventories, and retire edited generated reservations, never refill them. */
    public void adoptLegacy(World world) {
        for (Vec3i position : world.crateContents.keySet().toArray(Vec3i[]::new)) {
            world.getOrCreateChunk(Math.floorDiv(position.x(),16),Math.floorDiv(position.z(),16));
            BlockType live = world.getBlock(position.x(),position.y(),position.z());
            if (!Fixtures.isContainer(live)) { world.crateContents.remove(position); continue; }
            if (entries.size() >= MAX_ENTRIES) { throw new IllegalArgumentException("too many legacy container inventories"); }
            Planned plan = planned(world,position);
            if (plan != null && plan.fixture.block() == live) { put(generated(plan,State.SEARCHED)); }
            else { player(position,live); }
        }
        for (var edit : world.changedBlocks.entrySet()) {
            Vec3i position=edit.getKey();
            if (at(position) != null || position.y() < UrbanDatum.slabY(-1) || position.y() > UrbanDatum.slabY(5)) { continue; }
            Planned plan=planned(world,position);
            if (plan != null && !entries.containsKey(plan.fixture.id())) {
                if (entries.size() >= MAX_ENTRIES) { throw new IllegalArgumentException("too many legacy container edits"); }
                put(generated(plan,State.DESTROYED));
            }
        }
        prepareSave(world);
    }
}

package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.Creature;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.entity.UrbanEncounterDirector;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanFortifications;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanRefuges;
import com.veylon.world.urban.UrbanSpecies;
import com.veylon.world.urban.UrbanUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The integrated urban fixture of package 25: one economy city (generator 20, seed 20260731,
 * Survival) carrying every kind of irreversible fact a city records, each made through the game's
 * own commands. In the abandoned flat beside the starter refuge: a cupboard searched and partly
 * emptied, a search left half done, a container broken open, a door opened, another barred and
 * braced, a pane shattered, a wall breached, the street door barricaded and battered, the first
 * floor claimed as home, a generator fuelled and running a lamp, and water drawn from its taps.
 * Out in the city: an infected and an alien killed where they lived; the nearest camp told of the
 * relay and giving missions, one of them done for the hidden civilians found behind their loose
 * panel; and the nearest raider yard's people all dead, the yard taken and the nearest stronghold
 * told of it and grateful once.
 *
 * <p>Only the player's pose, aim and kit are staged; everything else goes through the frame step,
 * the interaction commands, block breaking, placement, conversation and the damage path.
 * {@link #describe} renders every one of those facts as text, so a loaded world can be compared
 * with the world that was saved line by line.
 */
public final class IntegratedCityFixture {
    public static final long SEED = 20_260_731L;
    public static final int GENERATOR = World.GEN_URBAN_ECONOMY;

    /** Where each fact lives: stable ids from the plans, and cells. */
    public record Facts(long flat, Vec3i looted, long lootedId, Vec3i interrupted, long interruptedId,
                        Vec3i destroyed, long destroyedId, Vec3i openDoor, Vec3i barredDoor, Vec3i window,
                        Vec3i wall, Vec3i barricade, long claimFloor, Vec3i generator, Vec3i lamp,
                        long deadInfected, long deadAlien, long hiddenRefuge, long hiddenRoom, long camp,
                        long yard, long stronghold) { }

    public record Built(Game game, Facts facts) { }

    private IntegratedCityFixture() {
    }

    /** A fresh session of the fixture's city. */
    public static Game session() {
        return sessionOf(SEED);
    }

    /** A fresh Survival session of an economy city of any seed. */
    public static Game sessionOf(long seed) {
        return SurvivorWorldArena.session(seed, GameMode.SURVIVAL, GENERATOR, new AudioManager());
    }

    /** Builds the fixture: about a game day of history, ending with the survivor at home. */
    public static Built build() {
        return build(session());
    }

    /**
     * Builds the fixture on a fresh Survival session of the fixture's city however it was made: by
     * {@link #session()} or by NEW FRONTIER's defaults (urban milestone 26).
     */
    public static Built build(Game g) {
        if (g.world.seed != SEED || g.world.generatorVersion != GENERATOR) {
            throw new IllegalArgumentException("the fixture needs seed " + SEED + " on generator " + GENERATOR);
        }
        BuildingPlan flat = RefugeWorldArena.neighbour(g);
        g.player.inventory.add(ItemType.IRON_PICKAXE, 1);

        // A cupboard searched and partly emptied, a search left half done, a container broken open.
        List<BuildingPlan.Fixture> boxes = containers(g, flat);
        BuildingPlan.Fixture looted = boxes.stream().filter(f -> stacks(rolled(g, f)) >= 2).findFirst()
                .orElseThrow(() -> new AssertionError("no container holding two stacks in the flat"));
        search(g, looted, 4f);
        Inventory left = g.world.crateContents.get(looted.position());
        int first = firstStack(left);
        assertThat(g.transferCrateItemToPlayer(first) > 0, "took a stack from " + looted.position());
        g.closeScreens();
        assertThat(stacks(g.world.crateContents.get(looted.position())) >= 1, "something is left behind");
        BuildingPlan.Fixture interrupted = boxes.stream().filter(f -> f != looted).findFirst().orElseThrow();
        search(g, interrupted, 0.6f);
        g.closeScreens();
        assertThat(entry(g, interrupted).state == UrbanContainers.State.SEARCHING, "a search left half done");
        // Destroyed where it stood, as a blast or a fire removes one, before anyone searched it.
        BuildingPlan.Fixture destroyed = boxes.stream().filter(f -> f != looted && f != interrupted).findFirst()
                .orElseThrow();
        SurvivorWorldArena.standAt(g, destroyed.target());
        Vec3i gone = destroyed.position();
        g.world.setBlock(gone.x(), gone.y(), gone.z(), BlockType.AIR, true);
        assertThat(entry(g, destroyed).state == UrbanContainers.State.DESTROYED, "the destroyed container is gone");

        // Doors, a pane and a wall.
        List<BuildingPlan.Fixture> doors = flat.fixtures().stream().filter(f -> f.kind() == FixtureKind.DOOR
                && UrbanIds.floorIndex(f.id()) == 0 && f.position().z() > flat.parcel().z0()
                && f.position().x() != f.target().x()).toList();
        BuildingPlan.Fixture open = doors.get(0), barred = doors.get(1);
        SurvivorWorldArena.standAt(g, open.target());
        g.player.crouching = false;
        SurvivorWorldArena.pressF(g, open.position());
        assertThat(FixtureStates.isOpen(state(g, open.position())), "opened a door");
        SurvivorWorldArena.standAt(g, barred.target());
        g.player.crouching = true;
        SurvivorWorldArena.pressF(g, barred.position());
        g.player.crouching = false;
        assertThat(FixtureStates.lock(state(g, barred.position())) == FixtureStates.LOCK_BARRED, "barred a door");
        assertThat(g.world.urban.fortifications.setBrace(g.world, barred.position(),
                UrbanFortifications.BRACE_STRENGTH - 20f), "braced the barred door");
        BuildingPlan.Fixture window = flat.fixtures().stream().filter(f -> f.kind() == FixtureKind.WINDOW
                && f.block() == BlockType.WINDOW && UrbanIds.floorIndex(f.id()) == 1).findFirst().orElseThrow();
        SurvivorWorldArena.standAt(g, window.target());
        hold(g, ItemType.IRON_PICKAXE);
        assertThat(g.blockActions.completePlayerBlockBreak(window.position()), "shattered a pane");
        Vec3i wall = wall(g, flat);
        g.world.setBlock(wall.x(), wall.y(), wall.z(), BlockType.AIR, true);

        // The street door barricaded, then battered.
        BuildingPlan.Fixture street = flat.fixtures().stream().filter(f -> f.kind() == FixtureKind.DOOR
                && f.position().z() == flat.parcel().z0()).findFirst().orElseThrow();
        Vec3i barricade = street.position();
        g.world.setBlock(barricade.x(), barricade.y(), barricade.z(), BlockType.BARRICADE, true);
        g.world.urban.fortifications.damage(BlockType.BARRICADE, barricade, 25f);

        // The first floor claimed at its bed, and home.
        BuildingPlan.Fixture bed = RefugeWorldArena.piece(flat, 1, BlockType.CAMP_BED);
        SurvivorWorldArena.standAt(g, bed.target());
        g.player.crouching = true;
        SurvivorWorldArena.pressF(g, bed.position());
        g.player.crouching = false;
        long claimFloor = UrbanIds.floor(flat.id(), 1);
        assertThat(g.world.urban.refuges.homeFloor() == claimFloor, "the first floor is home");

        // Water from the taps; on the second floor a generator, fuelled and running a lamp.
        BuildingPlan.Fixture tap = UtilityWorldArena.basins(flat).getFirst();
        UtilityWorldArena.giveEmptySkins(g, 2);
        SurvivorWorldArena.standAt(g, tap.target());
        SurvivorWorldArena.pressF(g, tap.position());
        SurvivorWorldArena.pressF(g, tap.position());
        assertThat(g.world.urban.utilities.drawn(flat.id()) == 2, "two litres drawn");
        Vec3i genCell = RefugeWorldArena.piece(flat, 2, BlockType.CAMP_BED).target();
        List<Vec3i> lanes = new ArrayList<>(UtilityWorldArena.lanes(flat, 2, genCell));
        lanes.remove(genCell);
        Vec3i lampCell = lanes.getFirst();
        SurvivorWorldArena.standAt(g, lanes.getLast());
        UtilityWorldArena.put(g, ItemType.GENERATOR, genCell);
        UtilityWorldArena.useOn(g, genCell, ItemType.FUEL_CAN, 1);
        SurvivorWorldArena.pressF(g, genCell);
        UtilityWorldArena.put(g, ItemType.WORK_LAMP, lampCell);
        RefugeWorldArena.holdItem(g, ItemType.IRON_PICKAXE, 1);
        SurvivorWorldArena.pressF(g, lampCell);
        SurvivorWorldArena.run(g, 2f);
        UrbanUtilities.Device generator = g.world.urban.utilities.device(genCell);
        assertThat(generator.running(), "the generator runs");
        assertThat(g.world.urban.utilities.device(lampCell).lit(), "and lights the lamp");

        // An infected and an alien killed where they lived.
        EncounterAnchors.Anchor infectedAt = EcologyWorldArena.first(g, UrbanSpecies.VEILBOUND);
        EcologyWorldArena.observeAway(g, infectedAt);
        Npc infected = g.entities.encounters.tryActivate(g, infectedAt);
        assertThat(infected != null, "an infected came out at " + infectedAt.cell());
        infected.hurt(999, true);
        EncounterAnchors.Anchor alienAt = EcologyWorldArena.first(g, UrbanSpecies.VEILMAW);
        EcologyWorldArena.observeAway(g, alienAt);
        Creature alien = g.entities.encounters.tryActivateCreature(g, alienAt);
        assertThat(alien != null, "a Veilmaw came out at " + alienAt.cell());
        alien.hurt(999, true);
        SurvivorWorldArena.run(g, 1f);
        assertThat(g.entities.encounters.resident(infectedAt.id()).dead()
                && g.entities.encounters.resident(alienAt.id()).dead(), "both deaths are recorded");

        // The camp: the relay's rumour and three missions; the hidden civilians found and aided; a report.
        SurvivorSites.Site campPlan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        UrbanSite camp = meet(g, campPlan);
        say(g, camp.resident(0).live, 9);
        for (int i = 0; i < 3; i++) { say(g, camp.resident(0).live, 10); }
        UrbanMissions.Mission aid = g.world.urban.missions.missions().stream()
                .filter(m -> m.kind == UrbanMissions.Kind.HIDDEN_AID).findFirst().orElseThrow();
        SurvivorSites.Site hidden = SurvivorSites.resolve(g.world.urban, aid.target);
        g.player.inventory.add(ItemType.DRIED_MEAT, 12);
        g.player.inventory.add(ItemType.WATERSKIN_CLEAN, 10);
        SurvivorWorldArena.standAt(g, SurvivorWorldArena.hall(g, hidden));
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.pressF(g, SurvivorWorldArena.panel(g, hidden));
        SurvivorWorldArena.standAt(g, hidden.entry());
        UrbanSite civilians = SurvivorWorldArena.site(g, hidden);
        assertThat(civilians.discovered, "found behind the panel");
        say(g, civilians.resident(0).live, 2);
        assertThat(aid.status == UrbanMissions.Status.READY, "they have what they need");
        camp = meet(g, campPlan);
        say(g, camp.resident(0).live, 10);
        assertThat(aid.status == UrbanMissions.Status.COMPLETED && aid.rewarded, "the aid is reported and rewarded");

        // The raider yard: every raider dead, the yard taken, the stronghold told once.
        CompoundSites.Compound yardPlan = CompoundWorldArena.nearest(g, SurvivorSites.Kind.RAIDERS);
        CompoundWorldArena.nextDay(g, 12 * 60);
        CompoundWorldArena.standFacing(g, yardPlan, CompoundWorldArena.street(yardPlan, 8), yardPlan.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite yard = CompoundWorldArena.site(g, yardPlan);
        for (Settlement.Resident p : yard.residents) {
            if (p.live != null) { p.live.hurt(999, true); }
        }
        SurvivorWorldArena.run(g, 1f);
        assertThat(yard.cleared, "with every raider dead the yard is taken");
        CompoundSites.Compound wardPlan = CompoundWorldArena.nearest(g, SurvivorSites.Kind.STRONGHOLD);
        CompoundWorldArena.nextDay(g, 10 * 60);
        CompoundWorldArena.standFacing(g, wardPlan, CompoundWorldArena.street(wardPlan, 1), wardPlan.gate().outside());
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.run(g, 15f);
        UrbanSite ward = CompoundWorldArena.site(g, wardPlan);
        assertThat(g.npcScreen.performSiteChoice(g, ward.resident(0).live, 8), "told the Wardens the yard is taken");
        assertThat(yard.rewarded, "and they were grateful");

        // Home, where the survivor is when the game is saved.
        SurvivorWorldArena.standAt(g, bed.target());
        SurvivorWorldArena.run(g, 1f);
        Facts facts = new Facts(flat.id(), looted.position(), looted.id(), interrupted.position(), interrupted.id(),
                destroyed.position(), destroyed.id(), open.position(), barred.position(), window.position(), wall,
                barricade, claimFloor, genCell, lampCell, infectedAt.id(), alienAt.id(), hidden.id(), hidden.room(),
                campPlan.id(), yardPlan.site().id(), wardPlan.site().id());
        return new Built(g, facts);
    }

    /**
     * Every fact of the fixture as it stands in a game, one line each: container states, progress and
     * what is left in them; door, lock, pane and wall blocks; claims and home; barricade and brace
     * strength; taps and devices; the dead; what the camp, the yard, the Wardens and the hidden
     * civilians remember; the missions and the relay's rumour; and the player's pack.
     */
    public static List<String> describe(Game g, Facts f) {
        List<String> out = new ArrayList<>();
        World w = g.world;
        for (long id : new long[]{f.lootedId(), f.interruptedId(), f.destroyedId()}) {
            UrbanContainers.Entry e = w.urban.containers.byId(id);
            out.add("container " + UrbanIds.format(id) + " " + (e == null ? "untouched"
                    : e.state + " progress=" + e.progress + " owner=" + e.owner)
                    + " holds " + contents(e == null ? null : w.crateContents.get(e.position)));
        }
        for (Vec3i cell : List.of(f.looted(), f.interrupted(), f.destroyed(), f.openDoor(), f.barredDoor(), f.window(),
                f.wall(), f.barricade(), f.generator(), f.lamp())) {
            w.getOrCreateChunk(Math.floorDiv(cell.x(), 16), Math.floorDiv(cell.z(), 16));
            out.add("cell " + cell + " " + w.getBlock(cell.x(), cell.y(), cell.z()) + " state="
                    + w.fixtures.stateAt(cell.x(), cell.y(), cell.z()));
        }
        UrbanRefuges refuges = w.urban.refuges;
        for (UrbanRefuges.Claim c : refuges.claims()) {
            out.add("claim " + UrbanIds.format(c.floor) + " bed=" + c.sleep + " at minute " + c.claimedMinute);
        }
        out.add("home " + UrbanIds.format(refuges.homeFloor()));
        UrbanFortifications fort = w.urban.fortifications;
        out.add("barricade " + fort.health(BlockType.BARRICADE, f.barricade()) + " brace "
                + fort.brace(w, f.barredDoor()) + " damaged=" + fort.damagedCount() + " braces=" + fort.braceCount());
        UrbanUtilities u = w.urban.utilities;
        out.add("taps drawn=" + u.drawn(f.flat()) + " tapped=" + u.tappedCount() + " devices=" + u.deviceCount());
        for (Vec3i cell : List.of(f.generator(), f.lamp())) {
            UrbanUtilities.Device d = u.device(cell);
            out.add("device " + cell + " " + (d == null ? "none" : d.kind + " on=" + d.on + " fuel=" + d.fuel
                    + " condition=" + d.condition));
        }
        UrbanEncounterDirector encounters = g.entities.encounters;
        for (long id : new long[]{f.deadInfected(), f.deadAlien()}) {
            UrbanEncounterDirector.Resident r = encounters.resident(id);
            out.add("encounter " + UrbanIds.format(id) + " " + (r == null ? "unrecorded"
                    : r.species + " dead=" + r.dead() + " health=" + r.health + " body=" + (r.live != null || r.creature != null)));
        }
        for (long id : new long[]{f.camp(), f.hiddenRefuge(), f.yard(), f.stronghold()}) {
            UrbanSite s = UrbanSiteDirector.registered(w, id);
            if (s == null) {
                out.add("site " + UrbanIds.format(id) + " unregistered");
                continue;
            }
            StringBuilder people = new StringBuilder();
            for (Settlement.Resident r : s.residents) {
                people.append(r.alive ? 'A' : 'D');
            }
            out.add(String.format(Locale.ROOT, "site %s %s trust=%.4f fear=%.4f discovered=%b cleared=%b"
                            + " rewarded=%b clock=%.4f people=%s", UrbanIds.format(id), s.plan.kind(), s.localReputation,
                    s.fear, s.discovered, s.cleared, s.rewarded, s.simMinute, people));
        }
        out.add("hidden room found=" + w.urban.discovery.discovered(f.hiddenRoom()) + " of " + w.urban.discovery.size());
        UrbanMissions missions = w.urban.missions;
        out.add("comms rumoured=" + missions.comms.rumoured + " found=" + missions.comms.relayFound + " parts="
                + missions.comms.recovered + " codes=" + missions.comms.codes + " complete=" + missions.comms.complete);
        for (UrbanMissions.Mission m : missions.missions()) {
            out.add("mission " + m.kind + " " + m.status + " progress=" + m.progress + " rewarded=" + m.rewarded
                    + " target=" + UrbanIds.format(m.target) + " accepted=" + m.acceptedMinute);
        }
        out.add("pack " + contents(g.player.inventory));
        return out;
    }

    // ------------------------------------------------------------------
    // Steps
    // ------------------------------------------------------------------

    /** The flat's planned containers away from the refuge's storage, on its first floor first. */
    private static List<BuildingPlan.Fixture> containers(Game g, BuildingPlan flat) {
        return flat.fixtures().stream().filter(f -> f.kind() == FixtureKind.CONTAINER)
                .sorted(java.util.Comparator.comparingInt(f -> Math.abs(UrbanIds.floorIndex(f.id()) - 1)))
                .toList();
    }

    private static Inventory rolled(Game g, BuildingPlan.Fixture f) {
        return UrbanContainers.firstRoll(g.world, UrbanContainers.planned(g.world, f.position()));
    }

    /** Opens a container through the production command and lets the search run. */
    private static void search(Game g, BuildingPlan.Fixture f, float seconds) {
        SurvivorWorldArena.standAt(g, f.target());
        assertThat(g.openCrateAt(f.position()), "opened " + f.position());
        SurvivorWorldArena.run(g, seconds);
    }

    private static UrbanContainers.Entry entry(Game g, BuildingPlan.Fixture f) {
        return g.world.urban.containers.byId(f.id());
    }

    private static int state(Game g, Vec3i cell) {
        return g.world.fixtures.stateAt(cell.x(), cell.y(), cell.z());
    }

    /** A plain wall cell of the flat's first-floor outline with wall above it, away from every opening. */
    private static Vec3i wall(Game g, BuildingPlan flat) {
        int y = com.veylon.world.urban.UrbanDatum.slabY(1) + 1;
        var p = flat.parcel();
        for (int column = p.x0() + 2; column <= p.x1() - 2; column++) {
            final int x = column;
            for (int z : new int[]{p.z1(), p.z0()}) {
                g.world.ensureChunks(x, z, 1, 10_000);
                BlockType t = g.world.getBlock(x, y, z);
                boolean plain = t == BlockType.BRICK || t == BlockType.PLASTER || t == BlockType.CONCRETE;
                if (plain && g.world.getBlock(x, y + 1, z) == t
                        && flat.fixtures().stream().noneMatch(f -> f.position().x() == x && f.position().z() == z)) {
                    return new Vec3i(x, y, z);
                }
            }
        }
        throw new AssertionError("no plain wall on the flat's first-floor outline");
    }

    private static UrbanSite meet(Game g, SurvivorSites.Site plan) {
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.slowTick(g);
        return SurvivorWorldArena.site(g, plan);
    }

    private static void say(Game g, Npc n, int choice) {
        assertThat(n != null, "someone to talk to");
        assertThat(g.npcScreen.performSiteChoice(g, n, choice), "choice " + choice + " offered");
    }

    /** Selects a stack of an item already in the pack, swapping it into the hotbar's first slot. */
    private static void hold(Game g, ItemType item) {
        Inventory inv = g.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type == item) {
                ItemStack held = inv.get(0);
                inv.set(0, s);
                inv.set(i, held);
                g.player.hotbarSel = 0;
                return;
            }
        }
        throw new AssertionError("no " + item + " in the pack");
    }

    private static int stacks(Inventory inv) {
        int n = 0;
        for (int i = 0; inv != null && i < inv.size(); i++) {
            if (inv.get(i) != null) { n++; }
        }
        return n;
    }

    private static int firstStack(Inventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            if (inv.get(i) != null) { return i; }
        }
        throw new AssertionError("an empty container");
    }

    /** An inventory's stacks in slot order, with their wear and freshness. */
    public static String contents(Inventory inv) {
        if (inv == null) { return "nothing"; }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            out.append(i == 0 ? "" : ",").append(s == null ? "-" : s.type + "x" + s.count + "/" + s.durability
                    + "/" + s.freshness + "/" + s.charge);
        }
        return out.append(']').toString();
    }

    private static void assertThat(boolean condition, String what) {
        if (!condition) { throw new AssertionError("integrated fixture: " + what); }
    }
}

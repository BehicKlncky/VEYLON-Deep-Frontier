package com.veylon.save;

import com.veylon.FixtureActions;
import com.veylon.Game;
import com.veylon.UrbanFixtureArena;
import com.veylon.entity.GameMode;
import com.veylon.entity.Player;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code world.fixtures} v1 (urban package 04): open, locked and barred doors
 * and windows survive save and load through the production save path; forced
 * and shattered ones survive as block edits; a resident's passing door is saved
 * shut; malformed sections fail the whole load and keep the live world.
 */
class FixtureStatesSectionTest {

    @TempDir
    Path dir;

    @Test
    void openBarredShatteredAndPassingFixturesRoundTripThroughTheSaveFile() throws IOException {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Game g = a.game;
        Vec3i front = a.house.frontDoor(), inner = a.house.innerDoor();
        Vec3i window = a.house.window(), pane = a.house.secondWindow();

        g.interactWithBlockAt(inner);
        assertTrue(FixtureActions.residentOpens(g, front.x(), front.y(), front.z()));
        a.standAt(a.house.eastRoom());
        g.player.crouching = true;
        a.aim(window);
        a.pressF();
        int latch = g.world.fixtures.stateAt(window.x(), window.y(), window.z());
        assertEquals(FixtureStates.LOCK_BARRED, FixtureStates.lock(latch));
        g.player.crouching = false;
        assertTrue(g.completePlayerBlockBreak(pane));

        Path save = save(g, "fixtures.sav");
        Game loaded = new Game();
        loaded.newWorld(1L, true, World.GEN_CAVE_IDENTITIES);
        assertTrue(SaveSystem.load(loaded, save), "loads over a live world (with the rehearsal)");
        World w = loaded.world;

        assertSame(BlockType.WOOD_DOOR_OPEN, w.getBlock(inner.x(), inner.y(), inner.z()));
        assertSame(BlockType.WOOD_DOOR_OPEN, w.getBlock(inner.x(), inner.y() + 1, inner.z()));
        assertSame(BlockType.METAL_DOOR, w.getBlock(front.x(), front.y(), front.z()),
                "a resident's passing door is saved shut");
        assertSame(BlockType.METAL_DOOR, w.getBlock(front.x(), front.y() + 1, front.z()));
        assertTrue(w.fixtures.transitTimers().isEmpty());
        assertSame(BlockType.WINDOW, w.getBlock(window.x(), window.y(), window.z()));
        assertEquals(latch, w.fixtures.stateAt(window.x(), window.y(), window.z()),
                "the latch keeps its side");
        assertEquals(latch, w.fixtures.stateAt(window.x(), window.y() + 1, window.z()));
        assertSame(BlockType.BROKEN_WINDOW, w.getBlock(pane.x(), pane.y(), pane.z()));
        assertSame(BlockType.WINDOW, w.getBlock(pane.x(), pane.y() + 1, pane.z()));
        assertEquals(g.world.fixtures.size(), w.fixtures.size(), "nothing gained or lost");

        // Saving the reloaded world writes the same, sorted section bytes again;
        // the original still holds its resident's door open and writes it shut.
        assertArrayEquals(FixtureStatesSection.write(g), FixtureStatesSection.write(loaded));
        assertSame(BlockType.METAL_DOOR_OPEN, g.world.getBlock(front.x(), front.y(), front.z()),
                "saving does not close the live door");
    }

    @Test
    void searchableFurnitureKeepsItsContentsAndIdentityThroughTheSaveFile() throws IOException {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i cabinet = a.house.cabinet(), locker = a.house.locker();
        a.game.openCrateAt(cabinet);
        a.game.openCrate.add(com.veylon.item.ItemType.BANDAGE, 4);
        a.game.closeScreens();
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save(a.game, "furniture.sav")));
        assertSame(BlockType.CABINET, loaded.world.getBlock(cabinet.x(), cabinet.y(), cabinet.z()));
        assertEquals(4, loaded.world.crateContents.get(cabinet).count(com.veylon.item.ItemType.BANDAGE));
        assertSame(BlockType.LOCKER, loaded.world.getBlock(locker.x(), locker.y(), locker.z()));
        assertTrue(loaded.openCrateAt(cabinet), "still searchable after the load");
    }

    @Test
    void aKeyedLockAndAForcedDoorKeepTheirEntryRulesAfterReload() throws IOException {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i front = a.house.frontDoor(), inner = a.house.innerDoor();
        for (int dy = 0; dy < 2; dy++) {
            a.game.world.fixtures.set(front.x(), front.y() + dy, front.z(), FixtureStates.LOCK_KEYED);
            a.game.world.fixtures.set(inner.x(), inner.y() + dy, inner.z(), FixtureStates.LOCK_KEYED);
        }
        assertTrue(a.game.completePlayerBlockBreak(inner), "force the wooden interior door");
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save(a.game, "locked-and-forced.sav")));
        assertTrue(loaded.interactWithBlockAt(front), "the locked handle consumes the interaction");
        for (int dy = 0; dy < 2; dy++) {
            assertSame(BlockType.METAL_DOOR, loaded.world.getBlock(front.x(), front.y() + dy, front.z()));
            assertEquals(FixtureStates.LOCK_KEYED,
                    loaded.world.fixtures.lockAt(front.x(), front.y() + dy, front.z()));
            assertSame(BlockType.BROKEN_DOOR, loaded.world.getBlock(inner.x(), inner.y() + dy, inner.z()));
            assertEquals(-1, loaded.world.fixtures.stateAt(inner.x(), inner.y() + dy, inner.z()));
        }
        assertEquals(1, loaded.noise.countCategory("door-rattle"), "entry still fails audibly");
        assertFalse(loaded.interactWithBlockAt(inner), "a broken doorway cannot close or relock");
        assertEquals(0, loaded.player.inventory.count(com.veylon.item.ItemType.WOOD_DOOR),
                "forcing gives no reusable door");
    }

    @Test
    void worldsWithoutFixtureStateWriteNoSection() throws IOException {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        byte[] untouched = Files.readAllBytes(save(a.game, "untouched.sav"));
        assertThrows(IOException.class,
                () -> CreativeSaveSections.replace(untouched, FixtureStatesSection.ID, null),
                "doors built but never touched need no state");
        Game legacy = new Game();
        legacy.newWorld(77L, true, World.GEN_CAVE_IDENTITIES);
        byte[] legacyBytes = Files.readAllBytes(save(legacy, "legacy.sav"));
        assertThrows(IOException.class,
                () -> CreativeSaveSections.replace(legacyBytes, FixtureStatesSection.ID, null),
                "a legacy save keeps exactly its sections");
    }

    @Test
    void anAbsentSectionLeavesEveryFixtureAsGeneratedOrEdited() throws IOException {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i inner = a.house.innerDoor();
        a.game.interactWithBlockAt(inner);
        byte[] bytes = Files.readAllBytes(save(a.game, "open.sav"));
        Path stripped = write("stripped.sav", CreativeSaveSections.replace(bytes, FixtureStatesSection.ID, null));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, stripped));
        assertSame(BlockType.WOOD_DOOR, loaded.world.getBlock(inner.x(), inner.y(), inner.z()),
                "without the section the door is as built: closed");
        assertTrue(loaded.world.fixtures.isEmpty());
    }

    @Test
    void staleAndNotYetGeneratedEntriesNeverLoadAChunkOrChangeAnotherBlock() throws IOException {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i inner = a.house.innerDoor();
        a.game.interactWithBlockAt(inner);
        byte[] bytes = Files.readAllBytes(save(a.game, "base.sav"));
        Vec3i street = a.house.outsideFront();
        Vec3i far = new Vec3i(5_000, 45, 5_000);
        byte[] payload = section(1,
                entry(street.x(), street.y() - 1, street.z(), FixtureStates.OPEN),
                entry(far.x(), far.y(), far.z(), FixtureStates.LOCK_KEYED),
                entry(inner.x(), inner.y(), inner.z(), FixtureStates.OPEN));
        Path edited = write("stale.sav", CreativeSaveSections.replace(bytes, FixtureStatesSection.ID, payload));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, edited));
        World w = loaded.world;
        assertSame(BlockType.ASPHALT, w.getBlock(street.x(), street.y() - 1, street.z()), "not a door: untouched");
        assertEquals(-1, w.fixtures.stateAt(street.x(), street.y() - 1, street.z()), "and the stale entry dropped");
        assertNull(w.getChunk(Math.floorDiv(far.x(), 16), Math.floorDiv(far.z(), 16)), "no chunk was generated");
        assertEquals(FixtureStates.LOCK_KEYED, w.fixtures.stateAt(far.x(), far.y(), far.z()),
                "kept until its chunk exists");
        assertSame(BlockType.WOOD_DOOR_OPEN, w.getBlock(inner.x(), inner.y(), inner.z()));
        assertSame(BlockType.WOOD_DOOR, w.getBlock(inner.x(), inner.y() + 1, inner.z()),
                "an entry applies to its own cell only");
    }

    @Test
    void malformedSectionsFailTheWholeLoadAndKeepTheLiveWorld() throws IOException {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i inner = a.house.innerDoor();
        a.game.interactWithBlockAt(inner);
        byte[] bytes = Files.readAllBytes(save(a.game, "valid.sav"));
        int x = inner.x(), y = inner.y(), z = inner.z();

        Map<String, byte[]> damaged = new LinkedHashMap<>();
        damaged.put("newer section version", section(2, entry(x, y, z, FixtureStates.OPEN)));
        damaged.put("negative count", ints(1, -1));
        damaged.put("count above the cap", ints(1, FixtureStates.MAX_ENTRIES + 1));
        damaged.put("count larger than the entries", concat(ints(1, 2), entry(x, y, z, FixtureStates.OPEN)));
        damaged.put("trailing byte", concat(section(1, entry(x, y, z, FixtureStates.OPEN)), new byte[]{0}));
        damaged.put("truncated header", new byte[]{0, 0, 0});
        damaged.put("unknown state bit", section(1, entry(x, y, z, 0x20)));
        damaged.put("open and locked", section(1, entry(x, y, z,
                FixtureStates.OPEN | FixtureStates.LOCK_KEYED)));
        damaged.put("reserved lock value", section(1, entry(x, y, z, FixtureStates.LOCK_MASK)));
        damaged.put("bar side on a keyed lock", section(1, entry(x, y, z,
                FixtureStates.LOCK_KEYED | FixtureStates.BAR_POSITIVE)));
        damaged.put("below the world", section(1, entry(x, -1, z, FixtureStates.OPEN)));
        damaged.put("above the world", section(1, entry(x, 96, z, FixtureStates.OPEN)));
        damaged.put("duplicate cell", section(1, entry(x, y, z, FixtureStates.OPEN),
                entry(x, y, z, FixtureStates.OPEN)));

        Game live = new Game();
        live.newWorld(99L, true, World.GEN_CAVE_IDENTITIES);
        World liveWorld = live.world;
        Player livePlayer = live.player;
        for (Map.Entry<String, byte[]> e : damaged.entrySet()) {
            Path file = write("damaged.sav",
                    CreativeSaveSections.replace(bytes, FixtureStatesSection.ID, e.getValue()));
            assertFalse(SaveSystem.load(live, file), e.getKey() + " must fail the load");
            assertSame(liveWorld, live.world, e.getKey() + " replaced the live world");
            assertSame(livePlayer, live.player, e.getKey() + " replaced the live player");
        }
        Game title = new Game();
        assertFalse(SaveSystem.load(title, write("title.sav", CreativeSaveSections.replace(bytes,
                FixtureStatesSection.ID, section(1, entry(x, y, z, 0x20))))), "no rehearsal on the title screen");
    }

    // ------------------------------------------------------------------

    private Path save(Game game, String name) {
        Path path = dir.resolve(name);
        assertTrue(SaveSystem.save(game, path));
        return path;
    }

    private Path write(String name, byte[] bytes) throws IOException {
        Path path = dir.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private static byte[] entry(int x, int y, int z, int state) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(z);
        out.writeByte(state);
        return b.toByteArray();
    }

    private static byte[] section(int version, byte[]... entries) throws IOException {
        byte[] head = ints(version, entries.length);
        return concat(head, concat(entries));
    }

    private static byte[] ints(int... values) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        for (int v : values) {
            out.writeInt(v);
        }
        return b.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            b.writeBytes(p);
        }
        return b.toByteArray();
    }
}

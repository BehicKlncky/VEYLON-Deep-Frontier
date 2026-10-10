package com.veylon.world;

import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkBlockEditsTest {
    @Test
    void replaysOnlyTheRequestedChunkWithNegativeCoordinatesAndLastWriteWins() {
        ChunkBlockEdits edits = new ChunkBlockEdits();
        Vec3i p = new Vec3i(-1, 45, -17);
        edits.put(p, BlockType.BRICK.id());
        edits.put(p, BlockType.AIR.id());
        edits.put(new Vec3i(0, 45, 0), BlockType.CABINET.id());
        Chunk target = new Chunk(-1, -2);
        target.set(15, 45, 15, BlockType.BRICK);
        edits.applyTo(target);
        assertEquals(BlockType.AIR, target.get(15, 45, 15));
        assertEquals(BlockType.AIR, target.get(0, 45, 0));
        assertEquals(2, edits.size());
        Chunk untouched = new Chunk(9, 9);
        edits.applyTo(untouched);
        assertEquals(BlockType.AIR, untouched.get(0, 45, 0));
    }

    @Test
    void mapViewsAndDefaultMutatorsKeepTheReplayIndexInSync() {
        ChunkBlockEdits edits = new ChunkBlockEdits();
        Vec3i p = new Vec3i(1, 41, 1);
        edits.putAll(Map.of(p, BlockType.BRICK.id()));
        edits.entrySet().iterator().next().setValue(BlockType.WINDOW.id());
        assertReplay(edits, p, BlockType.WINDOW);
        edits.replaceAll((pos, block) -> BlockType.CABINET.id());
        assertReplay(edits, p, BlockType.CABINET);
        edits.keySet().remove(p);
        assertReplay(edits, p, BlockType.AIR);
        edits.compute(p, (pos, block) -> BlockType.BRICK.id());
        edits.entrySet().removeIf(entry -> entry.getKey().equals(p));
        assertReplay(edits, p, BlockType.AIR);
        edits.put(p, BlockType.BRICK.id());
        edits.values().clear();
        assertTrue(edits.isEmpty());
        assertReplay(edits, p, BlockType.AIR);
    }

    private static void assertReplay(ChunkBlockEdits edits, Vec3i p, BlockType block) {
        Chunk target = new Chunk(0, 0);
        edits.applyTo(target);
        assertEquals(block, target.get(p.x(), p.y(), p.z()));
    }
}

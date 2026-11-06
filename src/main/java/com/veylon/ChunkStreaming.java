package com.veylon;

/**
 * The frame's streaming step: generates the missing chunks nearest the player within the frame's
 * generation budget, lets a generated city drop its farthest chunks once it holds too many
 * ({@link ChunkRetention}), then rebuilds the nearest dirty meshes within the meshing budget, and
 * times it so city QA can attribute a frame (urban milestone 24). It runs once a frame on the main
 * thread, after the control and simulation step and before rendering.
 */
final class ChunkStreaming {
    /** Chunks generated in one frame at most. */
    static final int GENERATE_PER_FRAME = 2;
    /** Chunk meshes rebuilt in one frame at most. */
    static final int MESH_PER_FRAME = 3;

    /**
     * How far streaming generates: one ring beyond the render radius, so every chunk the renderer
     * draws has its eight neighbours and its mesh is built once, final (urban milestone 24).
     */
    static int generationRadius(int renderRadius) {
        return renderRadius + 1;
    }

    private final Game game;
    /** Drops a generated city's far chunks once it holds too many (urban milestone 24). */
    final ChunkRetention retention;
    /** When the last frame's streaming step began, by {@link System#nanoTime}. */
    long startedAt;
    /** What the last frame's generation and meshing cost, in nanoseconds. */
    long generationNanos, meshNanos;
    /** Chunks the last frame generated, and every frame since this session began. */
    int generatedLastFrame;
    long generatedTotal;

    ChunkStreaming(Game game) {
        this.game = game;
        this.retention = new ChunkRetention(game);
    }

    void frame() {
        var player = game.player;
        startedAt = System.nanoTime();
        generatedLastFrame = game.world.ensureChunks((int) player.pos.x, (int) player.pos.z,
                generationRadius(game.renderer.renderRadius()), GENERATE_PER_FRAME);
        if (generatedLastFrame > 0) {
            retention.afterStreaming();
        }
        long meshStart = System.nanoTime();
        game.renderer.buildDirtyMeshes(game.world, Math.floorDiv((int) player.pos.x, 16),
                Math.floorDiv((int) player.pos.z, 16), MESH_PER_FRAME);
        long end = System.nanoTime();
        generationNanos = meshStart - startedAt;
        meshNanos = end - meshStart;
        generatedTotal += generatedLastFrame;
    }
}

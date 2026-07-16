package com.veylon.engine;

import com.veylon.Game;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.Track;
import com.veylon.gfx.Environment;
import com.veylon.gfx.GraphicsSettings;
import com.veylon.gfx.MaterialRegistry;
import com.veylon.gfx.ParticleRenderer;
import com.veylon.gfx.PostProcessor;
import com.veylon.gfx.ShadowMap;
import com.veylon.gfx.SkyRenderer;
import com.veylon.gfx.model.Animator;
import com.veylon.gfx.model.CreatureModels;
import com.veylon.gfx.model.EntityModel;
import com.veylon.gfx.model.HeldItemModels;
import com.veylon.gfx.model.NpcModels;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;
import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.ChunkMesher;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Forward PBR-lite pipeline: sun shadow pass -> HDR scene (sky, terrain,
 * entities, water, particles, held item) -> bloom/tonemap/FXAA composite.
 */
public class Renderer {

    /** Default chunk radius; live value comes from settings. */
    public static final int RENDER_RADIUS = 6;

    public final GraphicsSettings settings = GraphicsSettings.loadOrDefaults();
    public final Environment env = new Environment();

    /** QA-only vertex-AO kill switch (VEYLON_AO=0) for controlled comparisons. */
    private final boolean qaAoOff = "0".equals(System.getenv("VEYLON_AO"));

    private ShaderProgram chunkShader;
    private ShaderProgram entityShader;
    private ShaderProgram shadowShader;
    private ShaderProgram waterShader;
    private final SkyRenderer sky = new SkyRenderer();
    private final PostProcessor post = new PostProcessor();
    private final ParticleRenderer particles = new ParticleRenderer();
    private ShadowMap shadowMap;

    private Mesh cubeMesh;
    private Mesh centeredCubeMesh;
    private Mesh lineCube;
    private Mesh crackMesh;
    private final ChunkMesher mesher = new ChunkMesher();
    private final Matrix4f model = new Matrix4f();
    private final Matrix4f identity = new Matrix4f();
    private final Matrix4f projView = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Vector3f camRight = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private final Vector3f shadowFocus = new Vector3f();
    private final List<Chunk> visible = new ArrayList<>();

    private float fogStart = 60, fogEnd = 110;

    // Render stats (read by the debug overlay / smoke test).
    public int chunksRendered;
    public int drawCalls;
    public long trianglesRendered;
    public int particlesDrawn;
    /** Actual particle instanced submissions made this frame (0..2). */
    public int particleDrawCalls;
    public int chunkMeshRebuildsLastFrame;
    public long chunkMeshRebuildsTotal;

    public int renderRadius() {
        return settings.renderDistance;
    }

    public void init() {
        if (qaAoOff) {
            System.out.println("[settings] QA vertex AO disabled (VEYLON_AO=0)");
        }
        MaterialRegistry.init(settings.crispTextures);
        chunkShader = ShaderProgram.load("chunk");
        entityShader = ShaderProgram.load("entity");
        shadowShader = ShaderProgram.load("shadow");
        waterShader = ShaderProgram.load("water");
        sky.init();
        post.init();
        particles.init();
        shadowMap = new ShadowMap(settings.shadowMapSize());

        // Initialize shadow samplers while both candidate units hold a valid
        // comparison texture. NVIDIA validates sampler state at glUseProgram,
        // before the following uniform write, so this also avoids a one-time
        // undefined-state warning from the default sampler value (unit zero).
        shadowMap.bindTexture(0);
        shadowMap.bindTexture(1);
        chunkShader.bind();
        chunkShader.set("uShadow", 1);
        chunkShader.setVec2Array("uLayerProps", MaterialRegistry.layerProps());
        entityShader.bind();
        entityShader.set("uShadow", 1);
        glActiveTexture(GL_TEXTURE0);

        cubeMesh = buildCube();
        centeredCubeMesh = buildCenteredCube();
        lineCube = buildLineCube();
        crackMesh = buildCrackMesh();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    /** Bottom-anchored unit cube used by block-like effects and world marks. */
    private Mesh buildCube() {
        return buildCuboidMesh(0f, 1f);
    }

    /** Centered unit cube used by ModelPart, whose box coordinates are centers. */
    private Mesh buildCenteredCube() {
        return buildCuboidMesh(-0.5f, 0.5f);
    }

    private Mesh buildCuboidMesh(float y0, float y1) {
        float x0 = -0.5f, x1 = 0.5f, z0 = -0.5f, z1 = 0.5f;
        float[][][] faces = {
                {{0, 1, 0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                {{0, -1, 0}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                {{0, 0, -1}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                {{0, 0, 1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                {{-1, 0, 0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                {{1, 0, 0}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},
        };
        com.veylon.util.FloatList list = new com.veylon.util.FloatList(36 * 6);
        for (float[][] f : faces) {
            float[] n = f[0];
            for (int idx : new int[]{1, 2, 3, 1, 3, 4}) {
                list.add(f[idx][0], f[idx][1], f[idx][2]);
                list.add(n[0], n[1], n[2]);
            }
        }
        Mesh m = new Mesh(new int[]{3, 3});
        m.upload(list.array(), list.size());
        return m;
    }

    /** Small branching line network rendered once over the face currently being mined. */
    private Mesh buildCrackMesh() {
        float[][] segments = {
                {0.00f, 0.02f, -0.12f, 0.18f},
                {-0.12f, 0.18f, -0.30f, 0.28f},
                {-0.12f, 0.18f, -0.20f, 0.38f},
                {0.00f, 0.02f, 0.15f, 0.13f},
                {0.15f, 0.13f, 0.34f, 0.08f},
                {0.15f, 0.13f, 0.25f, 0.32f},
                {0.00f, 0.02f, -0.05f, -0.18f},
                {-0.05f, -0.18f, -0.22f, -0.34f},
                {-0.05f, -0.18f, 0.10f, -0.38f},
                {0.10f, -0.38f, 0.28f, -0.31f}
        };
        com.veylon.util.FloatList list = new com.veylon.util.FloatList(segments.length * 12);
        for (float[] s : segments) {
            list.add(s[0], s[1], 0);
            list.add(0, 0, 1);
            list.add(s[2], s[3], 0);
            list.add(0, 0, 1);
        }
        Mesh m = new Mesh(new int[]{3, 3});
        m.upload(list.array(), list.size());
        return m;
    }

    private Mesh buildLineCube() {
        float[][] e = {
                {0, 0, 0, 1, 0, 0}, {1, 0, 0, 1, 0, 1}, {1, 0, 1, 0, 0, 1}, {0, 0, 1, 0, 0, 0},
                {0, 1, 0, 1, 1, 0}, {1, 1, 0, 1, 1, 1}, {1, 1, 1, 0, 1, 1}, {0, 1, 1, 0, 1, 0},
                {0, 0, 0, 0, 1, 0}, {1, 0, 0, 1, 1, 0}, {1, 0, 1, 1, 1, 1}, {0, 0, 1, 0, 1, 1},
        };
        com.veylon.util.FloatList list = new com.veylon.util.FloatList(24 * 6);
        for (float[] edge : e) {
            list.add(edge[0], edge[1], edge[2]);
            list.add(0, 1, 0);
            list.add(edge[3], edge[4], edge[5]);
            list.add(0, 1, 0);
        }
        Mesh m = new Mesh(new int[]{3, 3});
        m.upload(list.array(), list.size());
        return m;
    }

    /** Rebuilds up to budget dirty chunk meshes near the player. */
    public void buildDirtyMeshes(World world, int pcx, int pcz, int budget) {
        chunkMeshRebuildsLastFrame = 0;
        List<Chunk> dirty = new ArrayList<>();
        for (Chunk c : world.loadedChunks()) {
            if (c.dirty && c.generated
                    && Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) <= renderRadius()) {
                dirty.add(c);
            }
        }
        dirty.sort((a, b2) -> Integer.compare(
                Math.abs(a.cx - pcx) + Math.abs(a.cz - pcz),
                Math.abs(b2.cx - pcx) + Math.abs(b2.cz - pcz)));
        for (int i = 0; i < Math.min(budget, dirty.size()); i++) {
            mesher.buildChunk(world, dirty.get(i));
            chunkMeshRebuildsLastFrame++;
            chunkMeshRebuildsTotal++;
        }
    }

    public void render(Game game, float dt) {
        Camera cam = game.camera;
        cam.fovDeg = settings.fov;
        int w = game.window.width(), h = game.window.height();
        drawCalls = 0;
        trianglesRendered = 0;

        env.update(game, dt);
        game.particles.density = settings.particleDensity;
        glEnable(GL_CULL_FACE); // UI pass disables it each frame

        // Fog capped so the chunk-load horizon is never visible.
        float maxFog = renderRadius() * 16f - 6f;
        fogEnd = Math.min(env.fogEnd, maxFog);
        fogStart = Math.min(env.fogStart, fogEnd * 0.62f);

        Matrix4f proj = cam.projectionMatrix((float) w / h);
        Matrix4f view = cam.viewMatrix();
        projView.set(proj).mul(view);
        frustum.set(projView);
        // Billboard axes from the view matrix rows.
        camRight.set(view.m00(), view.m10(), view.m20());
        camUp.set(view.m01(), view.m11(), view.m21());

        int pcx = Math.floorDiv((int) Math.floor(cam.position.x), 16);
        int pcz = Math.floorDiv((int) Math.floor(cam.position.z), 16);

        visible.clear();
        chunksRendered = 0;
        for (Chunk c : game.world.loadedChunks()) {
            if (!c.generated || c.meshOpaque == null) {
                continue;
            }
            if (Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > renderRadius()) {
                continue;
            }
            float minX = c.cx * 16, minZ = c.cz * 16;
            if (!frustum.testAab(minX, 0, minZ, minX + 16, Chunk.SY, minZ + 16)) {
                continue;
            }
            visible.add(c);
        }

        // ---- Shadow pass ----
        boolean shadowsOn = settings.shadowQuality > 0;
        if (shadowsOn) {
            if (shadowMap.size != settings.shadowMapSize()) {
                shadowMap.delete();
                shadowMap = new ShadowMap(settings.shadowMapSize());
            }
            shadowFocus.set(cam.position).add(cam.front().mul(14f, new Vector3f()));
            shadowMap.updateMatrix(shadowFocus, env.lightDir);
            shadowMap.begin();
            shadowShader.bind();
            shadowShader.set("uSunMatrix", shadowMap.lightMatrix);
            shadowShader.set("uModel", identity);
            glDisable(GL_CULL_FACE); // include thin geometry both ways
            for (Chunk c : visible) {
                c.meshOpaque.draw();
                drawCalls++;
            }
            glEnable(GL_CULL_FACE);
            shadowMap.end();
        }

        // ---- HDR scene ----
        post.resize(w, h);
        post.beginScene();
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glDisable(GL_BLEND);

        sky.render(proj, view, cam.position, env, (float) game.totalTime);
        drawCalls++;

        // Terrain.
        chunkShader.bind();
        chunkShader.set("uProj", proj);
        chunkShader.set("uView", view);
        chunkShader.set("uSunMatrix", shadowsOn ? shadowMap.lightMatrix : identity);
        chunkShader.set("uTime", (float) game.totalTime);
        chunkShader.set("uTiles", 0);
        chunkShader.set("uShadow", 1);
        chunkShader.set("uShadowsOn", shadowsOn ? 1f : 0f);
        chunkShader.set("uAoOn", qaAoOff ? 0f : 1f);
        chunkShader.set("uLightDir", env.lightDir);
        chunkShader.set("uLightColor", env.lightColor);
        chunkShader.set("uAmbientSky", env.ambientSky);
        chunkShader.set("uAmbientGround", env.ambientGround);
        chunkShader.set("uBlockLightColor", env.blockLightColor);
        chunkShader.set("uCamPos", cam.position);
        chunkShader.set("uFoliageTint", env.foliageTint);
        chunkShader.set("uWetness", env.wetness);
        chunkShader.set("uFrost", env.frost);
        chunkShader.set("uFogColor", env.fogColor);
        chunkShader.set("uFogStart", fogStart);
        chunkShader.set("uFogEnd", fogEnd);
        MaterialRegistry.textureArray().bind(0);
        // Keep the samplerShadow backed by a comparison-enabled depth texture
        // even when shadow contribution is disabled; some 3.3 drivers validate
        // sampler bindings before uniform flow control.
        shadowMap.bindTexture(1);
        for (Chunk c : visible) {
            c.meshOpaque.draw();
            drawCalls++;
            trianglesRendered += c.meshOpaque.vertexCount() / 3;
            chunksRendered++;
        }

        renderEntities(game, proj, view);

        // Water (transparent, both faces visible, no depth writes).
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        waterShader.bind();
        waterShader.set("uProj", proj);
        waterShader.set("uView", view);
        waterShader.set("uTime", (float) game.totalTime);
        waterShader.set("uCamPos", cam.position);
        waterShader.set("uLightDir", env.lightDir);
        waterShader.set("uLightColor", env.lightColor);
        waterShader.set("uAmbientSky", env.ambientSky);
        waterShader.set("uDeepColor", env.waterDeep);
        waterShader.set("uShallowColor", env.waterShallow);
        waterShader.set("uFogColor", env.fogColor);
        waterShader.set("uFogStart", fogStart);
        waterShader.set("uFogEnd", fogEnd);
        for (Chunk c : visible) {
            if (c.meshWater != null && c.meshWater.vertexCount() > 0) {
                c.meshWater.draw();
                drawCalls++;
                trianglesRendered += c.meshWater.vertexCount() / 3;
            }
        }
        glEnable(GL_CULL_FACE);
        glDepthMask(true);
        glDisable(GL_BLEND);

        // Particles (instanced, zero to two actual draw submissions).
        float pAmbient = Math.max(0.25f,
                (env.ambientSky.x + env.ambientSky.y + env.ambientSky.z) / 3f + env.flash);
        particles.render(game.particles, proj, view, camRight, camUp, pAmbient);
        particlesDrawn = particles.drawnLastFrame;
        particleDrawCalls = particles.drawCallsLastFrame;
        drawCalls += particles.drawCallsLastFrame;

        renderHeldItem(game, proj);

        // ---- Composite to backbuffer ----
        post.composite(env, settings.bloom, settings.fxaa);
        drawCalls += settings.bloom ? 4 : 1;
    }

    // ------------------------------------------------------------------
    // Entities (hierarchical cuboid models posed immediately before each draw)
    // ------------------------------------------------------------------

    private void bindEntityCommon(Matrix4f proj, Matrix4f view, boolean shadowsOn) {
        entityShader.bind();
        entityShader.set("uProj", proj);
        entityShader.set("uView", view);
        entityShader.set("uSunMatrix", shadowsOn ? shadowMap.lightMatrix : identity);
        entityShader.set("uShadow", 1);
        entityShader.set("uShadowsOn", shadowsOn ? 1f : 0f);
        entityShader.set("uLightDir", env.lightDir);
        entityShader.set("uLightColor", env.lightColor);
        entityShader.set("uAmbientSky", env.ambientSky);
        entityShader.set("uAmbientGround", env.ambientGround);
        entityShader.set("uBlockLightColor", env.blockLightColor);
        entityShader.set("uFogColor", env.fogColor);
        entityShader.set("uFogStart", fogStart);
        entityShader.set("uFogEnd", fogEnd);
        entityShader.set("uTintMul", 1f, 1f, 1f);
        entityShader.set("uEmissive", 0f);
    }

    private void renderEntities(Game game, Matrix4f proj, Matrix4f view) {
        bindEntityCommon(proj, view, settings.shadowQuality > 0);

        renderTracks(game);
        renderCarcasses(game);

        Vector3f camPos = game.camera.position;
        float entityRange = fogEnd + 12f;
        for (Creature c : game.entities.creatures) {
            float boundsHalf = Math.max(0.45f, c.width * 0.75f);
            float boundsHeight = c.height + (c.type == Creature.CreatureType.DEER ? 0.55f : 0.35f);
            if (c.dead || !entityVisible(camPos, c.pos.x, c.pos.y, c.pos.z,
                    boundsHalf, boundsHeight, entityRange)) {
                continue;
            }
            EntityModel creatureModel = CreatureModels.of(c.type);
            Animator.poseCreature(creatureModel, c, game.totalTime);
            setEntityLight(game, c.pos.x, c.pos.y + c.height * 0.5f, c.pos.z);
            entityShader.set("uTintMul", 1f, 1f, 1f);
            model.identity().translate(c.pos.x, c.pos.y, c.pos.z)
                    .rotateY((float) Math.toRadians(-c.yaw));
            drawModel(creatureModel, model);
        }

        for (Npc n : game.entities.npcs) {
            if (n.dead || !entityVisible(camPos, n.pos.x, n.pos.y, n.pos.z,
                    0.55f, n.height + 0.25f, entityRange)) {
                continue;
            }
            EntityModel npcModel = NpcModels.get();
            Animator.poseNpc(npcModel, n, game.totalTime);
            setEntityLight(game, n.pos.x, n.pos.y + 1f, n.pos.z);
            entityShader.set("uTintMul", 1f, 1f, 1f);
            model.identity().translate(n.pos.x, n.pos.y, n.pos.z)
                    .rotateY((float) Math.toRadians(-n.yaw));
            drawModel(npcModel, model);
        }

        // Burning blocks: emissive flames handled by particles; keep a core glow cube.
        float pulse = 0.78f + 0.22f * (float) Math.sin(game.totalTime * 9.0);
        entityShader.set("uEmissive", 0.9f);
        for (Vec3i p : game.fire.burningCells()) {
            model.identity().translate(p.x() + 0.5f, p.y(), p.z() + 0.5f)
                    .scale(0.95f * pulse, 1.05f * pulse, 0.95f * pulse);
            drawCube(1.0f, 0.45f + 0.15f * pulse, 0.08f);
        }

        // Active beacon: pulsing cyan light column.
        if (game.world.beaconStage >= 3 && game.world.beaconPos != null) {
            Vec3i bp = game.world.beaconPos;
            float beam = 0.65f + 0.35f * (float) Math.sin(game.totalTime * 2.4);
            entityShader.set("uEmissive", 1.2f);
            model.identity().translate(bp.x() + 0.5f, bp.y() + 1, bp.z() + 0.5f)
                    .scale(0.22f * beam, Chunk.SY, 0.22f * beam);
            drawCube(0.35f, 0.85f, 0.95f);
        }
        entityShader.set("uEmissive", 0f);

        // Target block outline.
        Raycaster.Hit hit = game.targetHit;
        if (hit != null) {
            setEntityLight(game, hit.x() + 0.5f, hit.y() + 0.5f, hit.z() + 0.5f);
            entityShader.set("uTintMul", 1f, 1f, 1f);
            model.identity().translate(hit.x() - 0.002f, hit.y() - 0.002f, hit.z() - 0.002f)
                    .scale(1.004f);
            entityShader.set("uModel", model);
            entityShader.set("uColor", 0.05f, 0.05f, 0.05f);
            entityShader.set("uEmissive", 0.35f);
            lineCube.draw(GL_LINES);
            entityShader.set("uEmissive", 0f);
            drawCalls++;
            if (game.miningProgress > 0f) {
                renderMiningCracks(hit, game.miningProgress);
            }
        }
    }

    /** One line draw over the struck face; scale/intensity communicate mining progress. */
    private void renderMiningCracks(Raycaster.Hit hit, float progress) {
        float p = Math.min(1f, Math.max(0f, progress));
        model.identity().translate(
                hit.x() + 0.5f + hit.nx() * 0.505f,
                hit.y() + 0.5f + hit.ny() * 0.505f,
                hit.z() + 0.5f + hit.nz() * 0.505f);
        if (hit.nx() > 0) {
            model.rotateY((float) Math.PI * 0.5f);
        } else if (hit.nx() < 0) {
            model.rotateY((float) -Math.PI * 0.5f);
        } else if (hit.ny() > 0) {
            model.rotateX((float) -Math.PI * 0.5f);
        } else if (hit.ny() < 0) {
            model.rotateX((float) Math.PI * 0.5f);
        } else if (hit.nz() < 0) {
            model.rotateY((float) Math.PI);
        }
        model.scale(0.62f + p * 0.38f);
        entityShader.set("uModel", model);
        entityShader.set("uColor", 0.045f, 0.035f, 0.03f);
        entityShader.set("uEmissive", 0.18f + p * 0.18f);
        crackMesh.draw(GL_LINES);
        entityShader.set("uEmissive", 0f);
        drawCalls++;
    }

    private void renderTracks(Game game) {
        Vector3f camPos = game.camera.position;
        for (Track t : game.entities.tracks) {
            float dx = t.x - camPos.x, dz = t.z - camPos.z;
            if (dx * dx + dz * dz > 42 * 42
                    || !frustum.testAab(t.x - 0.3f, t.y - 0.05f, t.z - 0.3f,
                    t.x + 0.3f, t.y + 0.15f, t.z + 0.3f)) {
                continue;
            }
            float fade = 1f - t.age / Track.MAX_AGE;
            if (fade <= 0) {
                continue;
            }
            setEntityLight(game, t.x, t.y + 0.5f, t.z);
            entityShader.set("uTintMul", 1f, 1f, 1f);
            model.identity().translate(t.x, t.y + 0.01f, t.z)
                    .rotateY((float) Math.toRadians(-t.yaw))
                    .scale(0.12f + fade * 0.06f, 0.012f, 0.2f + fade * 0.05f);
            if (t.blood) {
                drawCube(0.45f * fade + 0.1f, 0.05f, 0.05f);
            } else {
                float d = 0.4f + 0.6f * fade;
                drawCube(0.16f * d, 0.12f * d, 0.09f * d);
            }
        }
    }

    private void renderCarcasses(Game game) {
        Vector3f camPos = game.camera.position;
        float range = fogEnd + 8f;
        for (Carcass c : game.entities.carcasses) {
            var t = c.type;
            if (!entityVisible(camPos, c.pos.x, c.pos.y, c.pos.z,
                    Math.max(0.5f, t.width), t.height + 0.5f, range)) {
                continue;
            }
            setEntityLight(game, c.pos.x, c.pos.y + 0.3f, c.pos.z);
            float rot = c.rotten() ? 0.6f : 1f;
            entityShader.set("uTintMul", rot, rot * 0.9f, rot * 0.85f);
            EntityModel carcassModel = CreatureModels.of(t);
            Animator.poseCarcass(carcassModel);
            // Carcass currently stores no death yaw; derive a stable orientation from position.
            float yaw = (c.pos.x * 0.37f + c.pos.z * 0.73f) % ((float) Math.PI * 2f);
            model.identity().translate(c.pos.x, c.pos.y + 0.05f, c.pos.z)
                    .rotateY(yaw);
            drawModel(carcassModel, model);
        }
        entityShader.set("uTintMul", 1f, 1f, 1f);
    }

    /** First-person held item, drawn in camera space over the scene. */
    private void renderHeldItem(Game game, Matrix4f proj) {
        ItemStack held = game.player.selected();
        if (held == null || game.player.dead) {
            return;
        }
        glClear(GL_DEPTH_BUFFER_BIT);
        entityShader.bind();
        entityShader.set("uProj", proj);
        entityShader.set("uView", identity);
        entityShader.set("uShadowsOn", 0f);
        entityShader.set("uFogStart", 1000f);
        entityShader.set("uFogEnd", 2000f);
        entityShader.set("uTintMul", 1f, 1f, 1f);
        // Camera-space: fake a from-above light.
        entityShader.set("uLightDir", 0.3f, 0.8f, 0.5f);
        setEntityLight(game, game.player.pos.x, game.player.pos.y + 1.2f, game.player.pos.z);

        float motion = settings.motion;
        float swing = game.swingTimer > 0 ? (0.35f - game.swingTimer) / 0.35f : 0;
        float swingArc = (float) Math.sin(swing * Math.PI) * 0.9f
                * (0.45f + settings.motion * 0.55f);
        float bob = (float) Math.sin(game.walkBob * 6) * 0.015f * motion
                * (Math.abs(game.player.vel.x) + Math.abs(game.player.vel.z) > 0.5f ? 1 : 0);
        float sideBob = (float) Math.cos(game.walkBob * 3) * 0.012f * motion;
        float viewScale = heldViewScale(held.type);

        EntityModel heldModel = HeldItemModels.of(held.type);
        heldModel.resetPose();
        model.identity()
                .translate(0.26f + sideBob - swingArc * 0.16f,
                        -0.50f + bob - swingArc * 0.09f, -0.96f)
                .rotateZ(-0.18f + sideBob * 1.8f + swingArc * 0.24f)
                .rotateY(-0.38f - swingArc * 0.85f)
                .rotateX(0.10f - swingArc * 0.72f)
                .scale(viewScale);
        drawModel(heldModel, model);
        entityShader.set("uEmissive", 0f);
    }

    private float heldViewScale(ItemType type) {
        if (type.tool == ToolKind.WEAPON) {
            return 0.68f; // long spear stays inside the viewmodel frame
        }
        if (type.tool == ToolKind.PICKAXE || type.tool == ToolKind.AXE) {
            return 0.85f;
        }
        if (type.tool == ToolKind.KNIFE) {
            return 1.2f;
        }
        if (type == ItemType.TORCH) {
            return 1.0f;
        }
        if (type.places() != null) {
            return 0.82f;
        }
        if (type.isEdible() || type.isMedical()) {
            return 1.05f;
        }
        if (type.isEquippable()) {
            return 0.95f;
        }
        return 0.9f;
    }

    private boolean entityVisible(Vector3f camPos, float x, float y, float z,
                                  float halfWidth, float height, float maxDistance) {
        float dx = x - camPos.x;
        float dy = y + height * 0.5f - camPos.y;
        float dz = z - camPos.z;
        if (dx * dx + dy * dy + dz * dz > maxDistance * maxDistance) {
            return false;
        }
        return frustum.testAab(x - halfWidth, y - 0.15f, z - halfWidth,
                x + halfWidth, y + height, z + halfWidth);
    }

    private void drawModel(EntityModel entityModel, Matrix4f base) {
        int submitted = entityModel.render(base, entityShader, centeredCubeMesh, true);
        drawCalls += submitted;
        trianglesRendered += (long) submitted * 12L;
    }

    private void drawCube(float r, float g, float b) {
        entityShader.set("uModel", model);
        entityShader.set("uColor", r, g, b);
        cubeMesh.draw();
        drawCalls++;
        trianglesRendered += 12;
    }

    private void setEntityLight(Game game, float x, float y, float z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        entityShader.set("uSkyLight", game.world.skyLight(bx, by, bz));
        entityShader.set("uBlockLight", game.world.blockLight(bx, by, bz));
    }

    public void delete() {
        chunkShader.delete();
        entityShader.delete();
        shadowShader.delete();
        waterShader.delete();
        sky.delete();
        post.delete();
        particles.delete();
        shadowMap.delete();
        cubeMesh.delete();
        centeredCubeMesh.delete();
        lineCube.delete();
        crackMesh.delete();
        MaterialRegistry.delete();
    }
}

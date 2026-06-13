package com.veylon.engine;

import com.veylon.Game;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.Track;
import com.veylon.item.ItemStack;
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
 * Renders the voxel world, entities (with walk-bob and posture animation),
 * carcasses, animal tracks, particles, the first-person held item, fire and
 * the target outline.
 */
public class Renderer {

    public static final int RENDER_RADIUS = 6;

    private static final String CHUNK_VS = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec3 aColor;
            layout(location=2) in float aSky;
            layout(location=3) in float aBlock;
            uniform mat4 uProj;
            uniform mat4 uView;
            out vec3 vColor;
            out float vSky;
            out float vBlock;
            out float vDist;
            void main() {
                vec4 viewPos = uView * vec4(aPos, 1.0);
                gl_Position = uProj * viewPos;
                vColor = aColor;
                vSky = aSky;
                vBlock = aBlock;
                vDist = length(viewPos.xyz);
            }
            """;

    private static final String CHUNK_FS = """
            #version 330 core
            in vec3 vColor;
            in float vSky;
            in float vBlock;
            in float vDist;
            uniform float uDayLight;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uAlpha;
            out vec4 FragColor;
            void main() {
                float light = max(vSky * uDayLight, vBlock);
                light = max(light, 0.035);
                vec3 col = vColor * light;
                float fog = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                col = mix(col, uFogColor, fog);
                FragColor = vec4(col, uAlpha);
            }
            """;

    private static final String ENTITY_VS = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec3 aNormal;
            uniform mat4 uProj;
            uniform mat4 uView;
            uniform mat4 uModel;
            out vec3 vNormal;
            out float vDist;
            void main() {
                vec4 world = uModel * vec4(aPos, 1.0);
                vec4 viewPos = uView * world;
                gl_Position = uProj * viewPos;
                vNormal = aNormal;
                vDist = length(viewPos.xyz);
            }
            """;

    private static final String ENTITY_FS = """
            #version 330 core
            in vec3 vNormal;
            in float vDist;
            uniform vec3 uColor;
            uniform float uLight;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            out vec4 FragColor;
            void main() {
                float shade = 1.0;
                if (length(vNormal) > 0.5) {
                    shade = 0.55 + 0.45 * clamp(dot(normalize(vNormal), normalize(vec3(0.35, 0.8, 0.45))), 0.0, 1.0);
                }
                vec3 col = uColor * shade * uLight;
                float fog = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                col = mix(col, uFogColor, fog);
                FragColor = vec4(col, 1.0);
            }
            """;

    private ShaderProgram chunkShader;
    private ShaderProgram entityShader;
    private Mesh cubeMesh;
    private Mesh lineCube;
    private final ChunkMesher mesher = new ChunkMesher();
    private final Matrix4f model = new Matrix4f();
    private final Matrix4f projView = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();

    private float fogStart = 60, fogEnd = 110;
    private float skyR, skyG, skyB;
    public int chunksRendered;

    public void init() {
        chunkShader = new ShaderProgram(CHUNK_VS, CHUNK_FS);
        entityShader = new ShaderProgram(ENTITY_VS, ENTITY_FS);
        cubeMesh = buildCube();
        lineCube = buildLineCube();
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
    }

    private Mesh buildCube() {
        // Unit cube: x,z in [-0.5, 0.5], y in [0, 1]. Position + normal.
        float[][] faces = {
                // nx, ny, nz, then 4 corners
                {0, 1, 0, -0.5f, 1, -0.5f, 0.5f, 1, -0.5f, 0.5f, 1, 0.5f, -0.5f, 1, 0.5f},
                {0, -1, 0, -0.5f, 0, -0.5f, -0.5f, 0, 0.5f, 0.5f, 0, 0.5f, 0.5f, 0, -0.5f},
                {0, 0, -1, -0.5f, 0, -0.5f, 0.5f, 0, -0.5f, 0.5f, 1, -0.5f, -0.5f, 1, -0.5f},
                {0, 0, 1, -0.5f, 0, 0.5f, -0.5f, 1, 0.5f, 0.5f, 1, 0.5f, 0.5f, 0, 0.5f},
                {-1, 0, 0, -0.5f, 0, -0.5f, -0.5f, 1, -0.5f, -0.5f, 1, 0.5f, -0.5f, 0, 0.5f},
                {1, 0, 0, 0.5f, 0, -0.5f, 0.5f, 0, 0.5f, 0.5f, 1, 0.5f, 0.5f, 1, -0.5f},
        };
        com.veylon.util.FloatList list = new com.veylon.util.FloatList(36 * 6);
        for (float[] f : faces) {
            float nx = f[0], ny = f[1], nz = f[2];
            int[][] order = {{0, 1, 2}, {0, 2, 3}};
            for (int[] tri : order) {
                for (int v : tri) {
                    int base = 3 + v * 3;
                    list.add(f[base], f[base + 1], f[base + 2]);
                    list.add(nx, ny, nz);
                }
            }
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
            list.add(0, 0, 0);
            list.add(edge[3], edge[4], edge[5]);
            list.add(0, 0, 0);
        }
        Mesh m = new Mesh(new int[]{3, 3});
        m.upload(list.array(), list.size());
        return m;
    }

    /** Rebuilds up to budget dirty chunk meshes near the player. */
    public void buildDirtyMeshes(World world, int pcx, int pcz, int budget) {
        List<Chunk> dirty = new ArrayList<>();
        for (Chunk c : world.loadedChunks()) {
            if (c.dirty && c.generated
                    && Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) <= RENDER_RADIUS) {
                dirty.add(c);
            }
        }
        dirty.sort((a, b2) -> Integer.compare(
                Math.abs(a.cx - pcx) + Math.abs(a.cz - pcz),
                Math.abs(b2.cx - pcx) + Math.abs(b2.cz - pcz)));
        for (int i = 0; i < Math.min(budget, dirty.size()); i++) {
            mesher.buildChunk(world, dirty.get(i));
        }
    }

    public void render(Game game) {
        Camera cam = game.camera;
        int w = game.window.width(), h = game.window.height();
        glViewport(0, 0, w, h);

        float dayLight = (float) game.time.dayLight() * game.weather.lightMul()
                * game.events.skyLightMul();
        float flash = game.weather.flashLight();

        // Sky/fog color.
        float dl = Math.max(dayLight, 0.05f);
        skyR = lerp(0.025f, 0.47f, dl) + flash * 0.45f;
        skyG = lerp(0.03f, 0.65f, dl) + flash * 0.45f;
        skyB = lerp(0.07f, 0.88f, dl) + flash * 0.45f;
        float grayness = game.weather.grayness();
        float lum = (skyR + skyG + skyB) / 3f;
        skyR = lerp(skyR, lum, grayness);
        skyG = lerp(skyG, lum, grayness);
        skyB = lerp(skyB, lum, grayness);
        if (game.events.toxicFog()) {
            // Sickly green cast.
            skyR *= 0.6f;
            skyG = Math.min(1f, skyG * 1.1f + 0.06f);
            skyB *= 0.55f;
        } else if (game.events.ashfall()) {
            float ash = (skyR + skyG + skyB) / 3f * 0.8f;
            skyR = lerp(skyR, ash, 0.7f);
            skyG = lerp(skyG, ash, 0.7f);
            skyB = lerp(skyB, ash, 0.7f);
        }

        fogStart = game.weather.fogStart();
        fogEnd = game.weather.fogEnd();
        if (game.events.toxicFog()) {
            fogStart = Math.min(fogStart, 16f);
            fogEnd = Math.min(fogEnd, 46f);
        }

        glClearColor(skyR, skyG, skyB, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);

        Matrix4f proj = cam.projectionMatrix((float) w / h);
        Matrix4f view = cam.viewMatrix();
        projView.set(proj).mul(view);
        frustum.set(projView);

        int pcx = Math.floorDiv((int) Math.floor(cam.position.x), 16);
        int pcz = Math.floorDiv((int) Math.floor(cam.position.z), 16);

        chunkShader.bind();
        chunkShader.set("uProj", proj);
        chunkShader.set("uView", view);
        chunkShader.set("uDayLight", Math.min(1f, dayLight + flash));
        chunkShader.set("uFogColor", skyR, skyG, skyB);
        chunkShader.set("uFogStart", fogStart);
        chunkShader.set("uFogEnd", fogEnd);
        chunkShader.set("uAlpha", 1f);

        chunksRendered = 0;
        List<Chunk> visible = new ArrayList<>();
        for (Chunk c : game.world.loadedChunks()) {
            if (!c.generated || c.meshOpaque == null) {
                continue;
            }
            if (Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > RENDER_RADIUS) {
                continue;
            }
            float minX = c.cx * 16, minZ = c.cz * 16;
            if (!frustum.testAab(minX, 0, minZ, minX + 16, Chunk.SY, minZ + 16)) {
                continue;
            }
            visible.add(c);
            c.meshOpaque.draw();
            chunksRendered++;
        }

        renderEntities(game, proj, view, dayLight, flash);
        renderParticles(game, dayLight + flash);

        // Transparent water pass.
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        chunkShader.bind();
        chunkShader.set("uAlpha", 0.55f);
        for (Chunk c : visible) {
            if (c.meshWater != null) {
                c.meshWater.draw();
            }
        }
        glDepthMask(true);
        glDisable(GL_BLEND);

        renderHeldItem(game, proj, dayLight + flash);
    }

    private void renderEntities(Game game, Matrix4f proj, Matrix4f view, float dayLight, float flash) {
        entityShader.bind();
        entityShader.set("uProj", proj);
        entityShader.set("uView", view);
        entityShader.set("uFogColor", skyR, skyG, skyB);
        entityShader.set("uFogStart", fogStart);
        entityShader.set("uFogEnd", fogEnd);

        renderTracks(game, dayLight + flash);
        renderCarcasses(game, dayLight + flash);

        for (Creature c : game.entities.creatures) {
            if (c.dead) {
                continue;
            }
            float light = entityLight(game, c.pos.x, c.pos.y + c.height * 0.5f, c.pos.z, dayLight + flash);
            boolean moving = Math.abs(c.vel.x) > 0.2f || Math.abs(c.vel.z) > 0.2f;
            float bob = moving ? (float) Math.abs(Math.sin(c.bobPhase * 3.2f)) * 0.07f : 0f;
            // Stalking/charging predators drop low; resting deer sit down.
            float posture = switch (c.state) {
                case STALK, TRACK -> 0.78f;
                case CHARGE -> 0.85f;
                case REST -> 0.6f;
                default -> 1f;
            };
            // Body.
            model.identity().translate(c.pos.x, c.pos.y + bob, c.pos.z)
                    .rotateY((float) Math.toRadians(-c.yaw))
                    .scale(c.width, c.height * 0.62f * posture, c.width * 1.35f);
            drawCube(c.type.r, c.type.g, c.type.b, light);
            // Head.
            model.identity().translate(c.pos.x, c.pos.y + bob + c.height * 0.5f * posture, c.pos.z)
                    .rotateY((float) Math.toRadians(-c.yaw))
                    .translate(0, 0, -c.width * 0.62f)
                    .scale(c.width * 0.55f, c.height * 0.34f, c.width * 0.55f);
            drawCube(c.type.r * 0.8f, c.type.g * 0.8f, c.type.b * 0.8f, light);
            // Bird wings flap.
            if (c.type.flying) {
                float wing = (float) Math.sin(game.totalTime * 18 + c.bobPhase) * 0.6f;
                for (int side = -1; side <= 1; side += 2) {
                    model.identity().translate(c.pos.x, c.pos.y + c.height * 0.55f, c.pos.z)
                            .rotateY((float) Math.toRadians(-c.yaw))
                            .rotateZ(side * (0.5f + wing))
                            .translate(side * c.width * 0.75f, 0, 0)
                            .scale(c.width * 1.3f, 0.04f, c.width * 0.55f);
                    drawCube(c.type.r * 0.9f, c.type.g * 0.9f, c.type.b * 0.9f, light);
                }
            }
            // Thornhorn horns.
            if (c.type == Creature.CreatureType.THORNHORN) {
                for (int side = -1; side <= 1; side += 2) {
                    model.identity().translate(c.pos.x, c.pos.y + bob + c.height * 0.72f, c.pos.z)
                            .rotateY((float) Math.toRadians(-c.yaw))
                            .translate(side * 0.22f, 0, -c.width * 0.7f)
                            .scale(0.08f, 0.4f, 0.08f);
                    drawCube(0.85f, 0.82f, 0.7f, light);
                }
            }
        }

        for (Npc n : game.entities.npcs) {
            if (n.dead) {
                continue;
            }
            float light = entityLight(game, n.pos.x, n.pos.y + 1f, n.pos.z, dayLight + flash);
            boolean moving = Math.abs(n.vel.x) > 0.2f || Math.abs(n.vel.z) > 0.2f;
            float bob = moving ? (float) Math.abs(Math.sin(n.bobPhase * 3.2f)) * 0.05f : 0f;
            float r, g, b;
            if (n.raider) {
                r = 0.45f;
                g = 0.18f;
                b = 0.14f;
            } else if (n.isTrader) {
                r = 0.75f;
                g = 0.6f;
                b = 0.2f;
            } else if (n.hostileToPlayer()) {
                r = 0.7f;
                g = 0.2f;
                b = 0.15f;
            } else if (n.sick) {
                r = 0.35f;
                g = 0.5f;
                b = 0.45f;
            } else {
                r = 0.25f;
                g = 0.45f;
                b = 0.65f;
            }
            model.identity().translate(n.pos.x, n.pos.y + bob, n.pos.z)
                    .rotateY((float) Math.toRadians(-n.yaw))
                    .scale(n.width, n.height * 0.72f, n.width * 0.7f);
            drawCube(r, g, b, light);
            model.identity().translate(n.pos.x, n.pos.y + bob + n.height * 0.72f, n.pos.z)
                    .rotateY((float) Math.toRadians(-n.yaw))
                    .scale(0.32f, 0.30f, 0.32f);
            drawCube(0.85f, 0.68f, 0.55f, light);
        }

        // Burning blocks: pulsing orange cubes.
        float pulse = 0.78f + 0.22f * (float) Math.sin(game.totalTime * 9.0);
        for (Vec3i p : game.fire.burningCells()) {
            model.identity().translate(p.x() + 0.5f, p.y(), p.z() + 0.5f)
                    .scale(0.95f * pulse, 1.05f * pulse, 0.95f * pulse);
            drawCube(1.0f, 0.45f + 0.15f * pulse, 0.08f, 1.0f);
        }

        // Active beacon: a pulsing light column reaching the sky.
        if (game.world.beaconStage >= 3 && game.world.beaconPos != null) {
            Vec3i bp = game.world.beaconPos;
            float beam = 0.65f + 0.35f * (float) Math.sin(game.totalTime * 2.4);
            model.identity().translate(bp.x() + 0.5f, bp.y() + 1, bp.z() + 0.5f)
                    .scale(0.22f * beam, Chunk.SY, 0.22f * beam);
            drawCube(0.55f, 0.9f, 1.0f, 1.2f);
        }

        // Target block outline.
        Raycaster.Hit hit = game.targetHit;
        if (hit != null) {
            model.identity().translate(hit.x() - 0.002f, hit.y() - 0.002f, hit.z() - 0.002f)
                    .scale(1.004f);
            entityShader.set("uModel", model);
            entityShader.set("uColor", 0.05f, 0.05f, 0.05f);
            entityShader.set("uLight", 1f);
            lineCube.draw(GL_LINES);
        }
    }

    private void renderTracks(Game game, float dayLight) {
        Vector3f camPos = game.camera.position;
        for (Track t : game.entities.tracks) {
            float dx = t.x - camPos.x, dz = t.z - camPos.z;
            if (dx * dx + dz * dz > 42 * 42) {
                continue;
            }
            float fade = 1f - t.age / Track.MAX_AGE;
            if (fade <= 0) {
                continue;
            }
            float light = entityLight(game, t.x, t.y + 0.5f, t.z, dayLight);
            model.identity().translate(t.x, t.y + 0.01f, t.z)
                    .rotateY((float) Math.toRadians(-t.yaw))
                    .scale(0.12f + fade * 0.06f, 0.012f, 0.2f + fade * 0.05f);
            if (t.blood) {
                drawCube(0.55f * fade + 0.1f, 0.06f, 0.06f, light);
            } else {
                drawCube(0.16f, 0.12f, 0.09f, light * (0.4f + 0.6f * fade));
            }
        }
    }

    private void renderCarcasses(Game game, float dayLight) {
        for (Carcass c : game.entities.carcasses) {
            float light = entityLight(game, c.pos.x, c.pos.y + 0.3f, c.pos.z, dayLight);
            var t = c.type;
            float rot = c.rotten() ? 0.6f : 1f;
            // Body lying on its side.
            model.identity().translate(c.pos.x, c.pos.y + 0.05f, c.pos.z)
                    .scale(t.width * 1.3f, t.width * 0.55f, t.height * 1.1f);
            drawCube(t.r * rot, t.g * rot * 0.9f, t.b * rot * 0.85f, light);
            model.identity().translate(c.pos.x, c.pos.y + 0.04f, c.pos.z + t.height * 0.6f)
                    .scale(t.width * 0.5f, t.width * 0.4f, t.width * 0.5f);
            drawCube(t.r * 0.75f * rot, t.g * 0.7f * rot, t.b * 0.7f * rot, light);
        }
    }

    private void renderParticles(Game game, float dayLight) {
        ParticleSystem ps = game.particles;
        if (ps.count == 0) {
            return;
        }
        entityShader.bind();
        float ambient = Math.max(0.25f, dayLight);
        for (int i = 0; i < ps.count; i++) {
            float s = ps.size[i] * ps.fade(i);
            if (s < 0.005f) {
                continue;
            }
            model.identity().translate(ps.px[i], ps.py[i], ps.pz[i]).scale(s);
            drawCube(ps.cr[i], ps.cg[i], ps.cb[i], ambient);
        }
    }

    /** First-person held item with swing/bob animation, drawn over the world. */
    private void renderHeldItem(Game game, Matrix4f proj, float dayLight) {
        ItemStack held = game.player.selected();
        if (held == null || game.player.dead) {
            return;
        }
        glClear(GL_DEPTH_BUFFER_BIT);
        entityShader.bind();
        entityShader.set("uProj", proj);
        // Identity view: position the item in camera space directly.
        model.identity();
        entityShader.set("uView", model);
        entityShader.set("uFogStart", 1000f);
        entityShader.set("uFogEnd", 2000f);

        float swing = game.swingTimer > 0 ? (0.35f - game.swingTimer) / 0.35f : 0;
        float swingArc = (float) Math.sin(swing * Math.PI) * 0.9f;
        float bob = (float) Math.sin(game.walkBob * 6) * 0.015f
                * (Math.abs(game.player.vel.x) + Math.abs(game.player.vel.z) > 0.5f ? 1 : 0);

        boolean isTool = held.type.tool != com.veylon.item.ToolKind.NONE;
        model.identity()
                .translate(0.42f - swingArc * 0.18f, -0.38f + bob - swingArc * 0.10f, -0.62f)
                .rotateY(-0.5f - swingArc * 0.9f)
                .rotateX(-swingArc * 0.8f);
        if (isTool) {
            // Handle.
            model.scale(0.05f, 0.34f, 0.05f);
            entityShader.set("uModel", model);
            entityShader.set("uColor", 0.45f, 0.33f, 0.2f);
            entityShader.set("uLight", Math.max(0.4f, dayLight));
            cubeMesh.draw();
            // Head.
            model.identity()
                    .translate(0.42f - swingArc * 0.18f, -0.07f + bob - swingArc * 0.10f, -0.62f)
                    .rotateY(-0.5f - swingArc * 0.9f)
                    .rotateX(-swingArc * 0.8f)
                    .scale(0.14f, 0.09f, 0.07f);
            entityShader.set("uModel", model);
            entityShader.set("uColor", held.type.r, held.type.g, held.type.b);
            cubeMesh.draw();
        } else {
            model.scale(0.16f, 0.16f, 0.16f);
            entityShader.set("uModel", model);
            entityShader.set("uColor", held.type.r, held.type.g, held.type.b);
            entityShader.set("uLight", Math.max(0.4f, dayLight));
            cubeMesh.draw();
        }
    }

    private void drawCube(float r, float g, float b, float light) {
        entityShader.set("uModel", model);
        entityShader.set("uColor", r, g, b);
        entityShader.set("uLight", Math.min(1.2f, light));
        cubeMesh.draw();
    }

    private float entityLight(Game game, float x, float y, float z, float dayLight) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        float sky = game.world.skyLight(bx, by, bz) * dayLight;
        float blk = game.world.blockLight(bx, by, bz);
        return Math.max(0.08f, Math.max(sky, blk));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public void delete() {
        chunkShader.delete();
        entityShader.delete();
        cubeMesh.delete();
        lineCube.delete();
    }
}

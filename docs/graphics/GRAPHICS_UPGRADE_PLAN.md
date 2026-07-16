# VEYLON: Deep Frontier — Graphics Upgrade Plan

Goal: transform flat-colored programmer art into a coherent, atmospheric, stylized
voxel/low-poly sci-fi survival look (Valheim lighting × Astroneer silhouettes ×
Vintage Story readability) while preserving all gameplay, simulation, and saves.

Hard constraints:

- OpenGL 3.3 Core only. No engine port. No enum reordering (chunk bytes = BlockType
  ordinals, saved to disk). Visual data keyed by **stable string IDs** in a separate
  registry (`MaterialRegistry`), never by ordinal-dependent tables that could shift.
- Forward PBR-lite renderer (sun + hemisphere ambient + one PCF shadow map), not
  deferred, not full PBR.
- All assets original (procedurally generated in-code) or documented OFL/CC0.
- Buildable + smoke-testable at the end of every phase.

## Baseline (recorded 2026-07-14, before any change)

- `gradlew build`: PASS.
- `VEYLON_SMOKE=20 gradlew run`: PASS — `chunks=169 creatures=12 npcs=4 fps=100`,
  save/load round-trip OK, no crashes. 1280×720 windowed, vsync on.
- Renderer: 2 inline shader programs. Chunk vertex = pos+rgb+sky+block (8 floats).
  No textures, no normals, no AO, no shadows, no sRGB. Particles = 1 draw call per
  particle (up to 4000). UI = colored rects + stb_easy_font.
- Baseline screenshots were captured as `baseline_*.png` for the acceptance
  pass and intentionally removed before the 0.2.0 release.

## Phases

### Phase 0 — Instrumentation (prerequisite)
- `VEYLON_SEED` env var for deterministic worlds; F2 screenshot key;
  `VEYLON_SHOT=<sec[,sec...]>` automated screenshot capture during smoke runs.
- Acceptance: baseline screenshots captured on a fixed seed. **Status: VERIFIED**

### Phase 1 — Asset & material foundation
- `com.veylon.gfx` package: `ResourceManager` (classpath loading, good errors),
  `Texture2D` (STBImage + magenta/black missing-texture fallback), `TextureArray`
  (64×64 layers, mipmaps, configurable filtering, anisotropy when available),
  `MaterialRegistry` + `BlockMaterial` (string IDs, top/side/bottom layers, variants,
  tint category, alpha-cutout, emissive, roughness), external GLSL files under
  `assets/shaders/`.
- Original starter texture pack: deterministic **procedural tile generator**
  (`ProceduralTextures`) producing every needed 64×64 surface (grass, dirt, stone,
  sand, gravel, clay, snow, ice, log side/end, leaves, tall grass, bush, berry bush,
  ores, plank, wall, ash, pod hull, scrap, ruin stone, resonant core, campfire,
  torch, crate, workbench, furnace, beacon…). PNG files in
  `assets/textures/blocks/<id>.png` override the generator when present, so tiles
  can be replaced one at a time later.
- Acceptance: registry validates at startup (every visible BlockType maps to a
  material; missing → visible fallback + console report). Build passes.
  **Status: VERIFIED** — startup reported 42 materials / 57 layers without a
  missing-material warning; build and a live 30 s smoke passed on 2026-07-14.

### Phase 2 — Voxel renderer & lighting  (depends on 1)
- New chunk vertex format: pos(3) uv(2) layer(1) faceDir(1) sky(1) block(1) ao(1)
  tint(3) flags(1) = 14 floats. Per-vertex ambient occlusion, texture array layers,
  natural variants by world-position hash, biome/foliage tint.
- Lighting: gamma-correct (shader-side sRGB decode/encode via post), directional
  sun/moon with day-colored temperature, hemisphere sky/ground ambient, dynamic
  block light (warm tint), one 2048² PCF shadow map following the camera,
  weather-driven intensity. Back-face culling on, correct winding.
- Wet-surface darkening + specular from rain; frost/snow accent on upward faces in
  cold conditions (shader-driven, no remesh).
- Acceptance: terrain clearly textured with AO and sun shadows; smoke test passes;
  no frame-time regressions beyond target. **Status: VISUALLY APPROVED +
  PERFORMANCE-VERIFIED (2026-07-14)** — controlled `ao_shadow` comparison trio
  (`final2c_ao_on/only_off/off`, identical seed/camera/time, VEYLON_SHADOWS +
  VEYLON_AO toggles) and fullscreen 1080 timing in
  ACCEPTANCE_REPORT_2026-07-14.md.

### Phase 3 — Sky, water, post-processing  (depends on 2)
- `Environment`: single place computing sun dir, light colors, fog profile, grade
  from time/weather/season/events/biome.
- `SkyRenderer`: fullscreen procedural gradient, sun disk + glow, moon, stars,
  drifting 2-layer clouds, horizon haze, lightning flash, toxic/ash palettes.
- Water: dedicated shader — animated waves, fresnel, depth-tinted color (CPU column
  depth), shoreline foam, sun specular; underwater grade via post.
- `PostProcessor`: RGBA16F HDR buffer → bright-pass bloom (emissive-weighted) →
  ACES tonemap + exposure + color grade + FXAA + soft state vignettes
  (damage/cold/heat/poison/smoke).
- Acceptance: five benchmark scenes read correctly (meadow day, forest dawn fog,
  campfire night rain, ruin emissive, toxic fog). **Status: VISUALLY APPROVED
  (2026-07-14)** — `final2_scene_meadow/dawnfog/campfire_rain/ruin/toxic_2s`
  each staged and individually inspected; see
  ACCEPTANCE_REPORT_2026-07-14.md.

### Phase 4 — Creatures, NPCs, held items, VFX  (depends on 2)
- Data-driven hierarchical cuboid models (`ModelPart` tree: pivot, offset, size,
  per-part color/emissive) + procedural animation clips (idle/walk/run/stalk/
  charge/attack/rest/death…) driven by entity state.
- Distinct silhouettes: Glowdeer (antlers, glow spots), Ashwolf (low skulk, tail,
  ears), Skitterwing (4 wings), Murkhare (ears, hop), Thornhorn (bulk + horns),
  Gloomstalker (long limbs, glowing eyes), NPC humanoid (role-colored, head/arms/
  legs swing), trader pack, raider mask.
- First-person: recognizable pickaxe/axe/spear/knife/torch/food/medicine/block
  shapes, swing arcs, bob, simple forearm.
- `ParticleRenderer`: instanced camera-facing quads, alpha+additive passes, soft
  procedural sprites (smoke puff, ember, drop, flake, blood, dust, beacon motes).
- Mining crack overlay decal on the targeted block; tracks/blood as flat quads.
- Acceptance: Ashwolf + NPC recognizable at 30 m by silhouette; particles = 2 draw
  calls, not thousands. **Status: VISUALLY APPROVED (2026-07-14)** — 30 m test
  passes at native pixel scale (`final2_p4_silhouette30m_1s`); pose lineup,
  per-state sequence with carcass/pose-reset proof, full held-item matrix at
  720p/1080-class, mining/blood/beacon/fire-rain VFX all inspected; particle
  submissions 0–2 in every logged frame. Trader satchels + raider back spear
  added for structural role silhouettes.

### Phase 5 — UI & presentation  (depends on 1)
- `FontRenderer`: stb_truetype atlas of Source Sans 3 (Latin + Turkish), crisp at
  720p–1440p, UI scale setting. UiRenderer keeps its old API (all screens keep
  working) and gains sprite/icon drawing + nine-slice panels.
- `IconAtlas`: every item gets an icon — block items get isometric mini-cube renders
  composited from their actual block textures; others get shaped category templates
  (tools, food, medical, materials) tinted per item. No colored rectangles.
- HUD redesign: compact icon+bar vitals, contextual telemetry, cleaner prompts,
  shape+icon (not color-only) affliction chips.
- Title screen, loading state, death & victory presentation, graphics options page
  (render distance, shadows, bloom, FXAA, particles, FOV, UI scale, vsync,
  fullscreen, motion intensity) persisted to `veylon_graphics.properties`.
- Acceptance: no stb_easy_font anywhere; all 70 items have icons; HUD readable at
  720p and 1440p. **Status: VISUALLY APPROVED / RUNTIME-VERIFIED (2026-07-14)**
  — STBTrueType Source Sans 3 with rendered Turkish coverage, 70/70 icons, all
  screens at 1280×720 / 1920×1061 / 2560×1061, UI scale 0.75–1.5, front-end
  flows, relaunch persistence via the real APPLY→save path. Exact 1440-tall
  output and automated click simulation deferred (hardware/tooling — see
  report limitations).

### Phase 6 — QA & performance  (depends on all)
- GL error polling per frame in dev builds (KHR_debug when available), render stats
  (chunks, draw calls, triangles, particles) in F3, startup asset validation report,
  deterministic-seed benchmark scenario, performance capture in PROGRESS.md.
- Acceptance: 30 s smoke run with zero GL errors and zero missing assets; 1080p ≥ 60
  FPS on the dev machine. **Status: PERFORMANCE-VERIFIED (2026-07-14)** —
  fullscreen exact 1920×1080 30 s smoke: 843.5 avg FPS, p99 1.74 ms, zero GL
  errors, zero missing assets; movement/chunk-churn run shows bounded mesh
  retention (1272 rebuilt / 243 deleted / peak resident 329). NVIDIA 131218 =
  one-time first-use shader-state recompile warnings (non-errors), 131154 = QA
  screenshot readback. Historical records in ACCEPTANCE_REPORT_2026-07-14.md;
  screenshot/log artifacts are intentionally not shipped.

## Definition of done, deferred stretch goals

Done = all phase acceptance criteria + docs updated + PROGRESS.md log complete.
Stretch (explicitly deferred): glTF skeletal animation, KTX2 compression, cascaded
shadow maps, SSR/SSAO/volumetrics, weighted-blended OIT.

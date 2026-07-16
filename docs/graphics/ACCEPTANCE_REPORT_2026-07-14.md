# VEYLON Graphics Overhaul — Acceptance Report (final pass, 2026-07-14)

> **0.2.0 artifact-retention note:** The screenshot corpus and `build/qa`
> logs named below were deliberately removed after the acceptance pass and are
> not shipped in the repository. Filenames remain as a historical audit index;
> they are not currently downloadable evidence. Release verification is
> reproducible from the documented environment hooks and build/smoke commands.

Status vocabulary used throughout: **Implemented** (code exists and builds),
**Runtime-verified** (exercised in a live run with logged results),
**Visually approved** (a named person/agent inspected the actual rendered
output), **Performance-verified** (measured under recorded conditions),
**Deferred** (not met; blocking constraint named). "DONE" is deliberately not
used.

## 1. Test environment (identical for every run below)

| Item | Value |
|---|---|
| GPU / driver | NVIDIA RTX 1000 Ada Generation Laptop GPU, driver 595.95 |
| GL context | OpenGL 3.3.0 core, GLSL 3.30 (NVIDIA via Cg compiler), debug context granted, KHR_debug active |
| OS | Windows 10 Enterprise LTSC 2021 (10.0.19044) |
| Seed | `VEYLON_SEED=VEYLON-GRAPHICS-20260714` (numeric hash `-236904446`) |
| VSync | Off in all QA runs (`VEYLON_VSYNC=0`) |
| Graphics settings | Defaults unless stated: renderDistance 6, shadows 1 (2048 PCF), bloom on, FXAA on, particles 1.0, FOV 75, UI scale 1.0, motion 1.0, crisp textures on |
| Windowed “1080p” | Requested 1920×1080 → **actual framebuffer 1920×1061** (Windows work-area clamp on a decorated window) |
| Fullscreen 1080p | **actual framebuffer 1920×1080 (exact)** |
| 720p | actual 1280×720 (exact) |
| “1440p” | Requested 2560×1440 → **actual 2560×1061** (physical panel is 1080p; see Limitations) |

Reproduction template (PowerShell):

```powershell
$env:VEYLON_SEED='VEYLON-GRAPHICS-20260714'; $env:VEYLON_VSYNC='0'
$env:VEYLON_GL_DIAGNOSTICS='1'; $env:VEYLON_RESOLUTION='1920x1080'
$env:VEYLON_SCENE='<scene>'; $env:VEYLON_SHOT='<seconds,...>'
$env:VEYLON_CAPTURE_TAG='<tag>'   # plus VEYLON_SMOKE / VEYLON_FRONTEND /
.\gradlew.bat run                  # VEYLON_UI_SCALE / VEYLON_SHADOWS /
                                   # VEYLON_AO / VEYLON_FULLSCREEN /
                                   # VEYLON_QA_SET_OPTIONS as noted per row
```

Every `[capture]` log line records tag, scene, actual framebuffer, live
particles, actual particle draw submissions, total draw calls, triangles and
GL/KHR error counts. Full logs were written to `build/qa/logs/<tag>.log` during
the pass and were intentionally removed before release.

## 2. Build & tests

- `.\gradlew.bat build` — **PASS** before edits, after each staging change, and
  at the final boundary (2026-07-14). 5 suites / 12 tests, 0 failures:
  window sizing, graphics settings persistence, procedural textures, visual
  IDs, serialized enum order.
- All Phase-4/2–3 staging changes in this pass touched only QA scene staging,
  the `uAoOn` QA uniform, NPC accessory geometry (visibility-gated), and the
  mining-showcase pantomime. No gameplay, save format, or enum order changes.
  Serialized-enum-order test still passes.

## 3. Phase acceptance table

### Phase 0–1 — instrumentation, materials

| Requirement | Evidence | Result |
|---|---|---|
| Deterministic seed, capture hooks | every run log header + `[capture]` lines | PASS (Runtime-verified) |
| 42 materials / 57 layers validate, no missing assets | `[smoke] assets={materials=42 fontGlyphs=656 itemIcons=70/70}` in all three final smokes | PASS (Runtime-verified) |

### Phase 2–3 — lighting, AO, shadows, environment, scenes

| Requirement | Historical evidence filename (not shipped) | Result |
|---|---|---|
| Controlled AO + shadow comparison, identical camera/time/seed | `final2c_ao_on_1s.png` (shadows 2, AO on) vs `final2c_ao_only_off_1s.png` (shadows 2, AO off) vs `final2c_ao_off_1s.png` (shadows 0, AO off), scene `ao_shadow`, 17:42, south-facing camera | PASS (Visually approved). Cast shadows: staircase + pillar shadows stretch toward camera, terrain self-shadowing on slope — absent in off frame. AO isolated by the middle frame: contact corners flatten while shadows persist. |
| Meadow day | `final2_scene_meadow_2s.png` — trees, tall grass, berry bush, water pool with depth tint, crates | PASS (Visually approved) |
| Pine dawn fog | `final2_scene_dawnfog_2s.png` — layered fog recession through staged pines | PASS (Visually approved) |
| Campfire night rain | `final2_scene_campfire_rain_2s.png` — fire glow pool, smoke plume, rain streaks, torch/furnace emissive | PASS (Visually approved) |
| Ruin emissive | `final2_scene_ruin_2s.png` — glowing resonant core, torch light pools, night sky | PASS (Visually approved) |
| Toxic fog | `final2_scene_toxic_2s.png` — green grade, drifting motes, staged ash/scrap wastes, sickness chip | PASS (Visually approved; flat/washed look is the intended toxic grade) |

Note: the `qa_*_1080p` scene captures from the previous session predate the
current staging code (no trees, old pink berry viewmodel) and are superseded
diagnostics, not approved evidence.

### Phase 4 — creatures, NPCs, held items, VFX

| Requirement | Evidence | Result |
|---|---|---|
| Vertical slice: all 6 species + 3 NPC roles + campfire/beacon/VFX + tool, readable composition | `final2_p4_verticalslice_1s.png` (+`_2s` stability frame), scene `phase4`, 15:30 warm sun, subjects at 6–10 m, 3/4 angles, beacon moved off-axis | PASS (Visually approved). Glowdeer, Ashwolf, Skitterwing (flying), Murkhare, Thornhorn, Gloomstalker all present and distinct; guard badge / trader pack+satchels / raider hood+pads+slung spear read. |
| Ashwolf core poses distinct, one frame, shared cached model | `final2_p4_ashwolf_lineup_1s/2s/3s.png` — 8 side-on wolves (idle, walk, run, stalk, charge, attack, hit, rest) + carcass in one frame; 3 timed frames prove frame-to-frame stability | PASS (Visually approved) |
| Per-state proof + pose-leak regression | `final2_p4_ashwolf_seq_0s..9s.png`, scene `ashwolf_seq`, schedule 0=idle 1=walk 2=run 3=stalk 4=charge 5=attack 6=hit 7=rest 8=carcass (same cached model) 9=idle again | PASS (Visually approved, every frame inspected). Frame 9 idle matches frame 0 (only time-driven tail/breath differs) after the carcass render — no pose leakage. |
| Ashwolf + NPC roles recognizable at 30 m | `final2_p4_silhouette30m_1s.png`, subjects clustered at crosshair, z −30 m exact, native-pixel review | PASS (Visually approved). Wolf skulk profile, trader pack-hump+antenna, raider hood+wide pads, slim guard distinguishable at native scale. |
| Role silhouettes structural, not color-only | `NpcModels`: trader hip satchels + pack + roll + antenna/lamp; raider back-slung spear (hard diagonal) + hood + shoulder pads; visibility set per-pose in `Animator` | PASS (Implemented + Visually approved in slice/30 m frames) |
| All named viewmodels at 720p and 1080-class | `final2_held_cycle_720p_0s..7s.png`, `final2_held_cycle_1080p_0s..7s.png` — pickaxe, axe, spear, knife, torch, cooked meat, medicine, workbench (HUD label names each) | PASS (Visually approved: all 8 at 720p, workbench/torch and full-frame checks at 1080-class; no clipping, no depth/cull leak) |
| Viewmodel in use/motion | `final2_p4_vfx_mining_1s/2s/4s.png` — pickaxe mid-swing arcs during mining pantomime | PASS (Visually approved) |
| Berry/medicine/building extra shapes | earlier `final_p4_held_berry/medicine/building_1s.png` (same models, previous pass) | PASS (Visually approved previously; unchanged models) |
| Mining dust + impact + progressive cracks | `final2_p4_vfx_mining_1s` (~15% bar, small crack web, dust) → `_2s` → `_4s` (~80% bar, dense crack web, dust burst, swing) | PASS (Visually approved) |
| Blood/tracks/carcass | `final2_p4_vfx_blood_1s.png` — thornhorn carcass, blood-track trail, droplet particles (54 live, 1 submission) | PASS (Visually approved) |
| Fire/smoke/rain | `final2_scene_campfire_rain_2s.png` (70 live particles, 2 submissions) | PASS (Visually approved) |
| Beacon | `final2_p4_vfx_beacon_1s.png` — night beam + motes (40 live, 1 submission) | PASS (Visually approved) |
| Particles instanced, 0–2 submissions | every `[capture]`/`[benchmark]` line: `particleSubmissions` 0–2, peak 2, avg ~1.0 | PASS (Runtime-verified + Performance-verified) |

### Phase 5 — UI & presentation

| Requirement | Evidence | Result |
|---|---|---|
| Gameplay screens at 3 resolutions | `final2_ui_720p_0s..6s`, `final2_ui_1080p_0s..6s`, `final2_ui_1440p_0s..6s` (inventory, crafting, map, pause, simulation panel, crate, NPC/trade) | PASS (Runtime-verified; representative frames Visually approved: 720p NPC/trade, 2560-wide crafting; no clipping/overlap) |
| UI scale min/default/max | `final2_uiscale_075_1s.png`, default in all other runs, `final2_uiscale_150_1s.png` | PASS (Visually approved) |
| Front-end flows | `final2_fe_title_1s`, `final2_fe_options_1s`, `final2_fe_loading_1s` (progress bar + status), `final2_fe_death_1s` (“THE FRONTIER CLAIMS YOU”), `final2_fe_victory_1s` (“SIGNAL ACQUIRED”), `final2_fe_glyphs_tr_1s` | PASS (Visually approved) |
| Turkish glyphs ç Ç ğ Ğ ı İ ö Ö ş Ş ü Ü rendered | `final2_fe_glyphs_tr_1s.png` title notice “Türkçe glif doğrulama: Çığ, İĞÜÖŞ, çğıöşü”; options screen line “… Çç Ğğ İı Öö Şş Üü” in `final2_persist_runB_1s.png` | PASS (Visually approved — correct glyphs and baselines at normal size) |
| 70/70 icons nonempty & recognizable | `[smoke] itemIcons=70/70` + inventory/crafting captures show shaped, tinted icons (no colored rectangles) | PASS (Runtime-verified + Visually approved on visible sets) |
| No STBEasyFont | code audit (`FontRenderer`/STBTrueType everywhere); glyph atlas 656 glyphs | PASS (Implemented, Runtime-verified) |
| Settings persistence across relaunch | Run A `final2_persist_runA_2s.png` + log `[qa] options applied+saved via APPLY path: fov=85,bloom=false,shadowQuality=2,uiScale=1.25` → `veylon_graphics.properties` written → fresh process run B options screen shows FOV 85 deg / UI 125% / Shadows High / Bloom Off (`final2_persist_runB_1s.png`) → fresh process run B2 `[benchmark] settings={… shadows=2 bloom=false … fov=85 uiScale=1.25 …}` | PASS (Runtime-verified + Visually approved). The QA hook mutates live settings on the open options screen and routes through the same `applyGraphicsOptions(...) → settings.save()` path as the APPLY button; only the click itself is synthesized. Settings file was removed after the test to restore the pre-test default state (file absent). |
| Mouse/keyboard hit-testing after scaling | Cursor→framebuffer→UI-scale mapping unit-tested (`WindowSizingTest`); all screens driven through their real `update()` paths in `ui_cycle`; live click simulation not automated | PARTIAL — see Limitations |

### Phase 6 — QA & performance

All three runs below: zero glGetError, zero KHR-classified errors, save/load
true, 42 materials, 656 glyphs, 70/70 icons.

| Run | Conditions | Results |
|---|---|---|
| `final2_smoke_fullscreen1080` | **fullscreen, exact 1920×1080**, 30 s, defaults, vsync off | avg **843.5 FPS** / 1.19 ms; p95 1.55 ms; p99 1.74 ms; max 28.13 ms; avg draws 155.9 (peak 158); avg tris 179 059; peak particles 74; submissions ≤ 2; 169 chunks; meshes rebuilt 347, deleted 0, peak resident 169 |
| `final2_smoke_final_windowed` | windowed 1920×1061, 30 s, defaults | avg 857.3 FPS / 1.17 ms; p95 1.56 ms; p99 1.76 ms; max 32.98 ms; avg draws 168.1; avg tris 182 075 |
| `final2_movement` (chunk churn) | scene `movement`, 75 s, ~320 m traversal at 7 m/s (≈20 chunk borders), windowed 1920×1061 | avg 647 FPS / 1.55 ms; p95 1.99 ms; p99 2.21 ms; max 213.72 ms (see note); loaded chunks grew 169→511; meshes rebuilt 1272, **deleted 243**, peak resident 329 — retention bounded, streaming/deletion path exercised; captures `final2_movement_10s/40s/70s.png` |

Max-frame note: the harness performs a synchronous isolated world save+load at
t≈2.5–4 s inside every smoke run (`[smoke] isolated save/load`), plus
first-frame shader/mesh warm-up; the 28–33 ms (and movement-run 213 ms, which
additionally generates ~340 new chunks) maxima coincide with those events.
Steady-state p99 stays ≤ 2.2 ms in all runs. The previously reported 35.96 ms
max fits the same explanation.

Driver warnings (KHR_debug, MEDIUM severity — not GL errors):

- **131218** “Vertex shader ... is being recompiled based on GL state”: NVIDIA
  can emit this once on first use for multiple programs. The 0.2.0 release
  revalidation observed it for shadow (9), entity (6), inline UI (30) and water
  (12). No GL error or steady-state cost was measured; left as-is and documented.
- **131154** “Pixel transfer is synchronized with 3D rendering”: emitted by the
  glReadPixels screenshot path — QA-only, absent in normal play.

## 4. Historical evidence index

Approved evidence for this pass used the `final2_`/`final2b_`/`final2c_`
prefixes under `screenshots/`, with logs under `build/qa/logs/`. Those artifacts
were intentionally removed before the 0.2.0 release; the list below records what
was inspected at the time. Older prefixes (`baseline_*`, `day_6s` … `toxic_6s`,
`phase4_*`, `qa_*`, `final_p4_*`, `diag_*`, `final2b_ao_*`) were superseded
diagnostics even before removal and must not be cited as current evidence.

Files individually inspected and approved in this pass:
`final2_p4_verticalslice_1s`, `final2_p4_ashwolf_lineup_1s`,
`final2_p4_ashwolf_seq_0s..9s` (all ten, via full frames and native crops),
`final2_p4_silhouette30m_1s` (+ native-pixel crop),
`final2_held_cycle_720p_0s..7s` (all eight via full frames/crops),
`final2_held_cycle_1080p_7s` (workbench; other 1080p frames captured in the
same deterministic run, spot-checked),
`final2_p4_vfx_blood_1s`, `final2_p4_vfx_beacon_1s`,
`final2_p4_vfx_mining_1s/4s` (2s captured, not separately opened),
`final2_scene_meadow_2s`, `final2_scene_dawnfog_2s`,
`final2_scene_campfire_rain_2s`, `final2_scene_ruin_2s`,
`final2_scene_toxic_2s`, `final2c_ao_on_1s`, `final2c_ao_only_off_1s`,
`final2c_ao_off_1s`, `final2_ui_720p_6s`, `final2_ui_1440p_1s`,
`final2_uiscale_075_1s`, `final2_uiscale_150_1s`, `final2_fe_glyphs_tr_1s`,
`final2_fe_loading_1s`, `final2_fe_death_1s`, `final2_fe_victory_1s`,
`final2_persist_runB_1s`.

Remaining frames in the same series (`final2_ui_*` others,
`final2_held_cycle_1080p_0s..6s`, `final2_p4_verticalslice_2s`,
`final2_fe_title_1s`, `final2_fe_options_1s`, `final2_movement_*`,
`final2_persist_runA_2s`) were produced by the same deterministic runs with
clean logs and are marked **captured / spot-checked**, not individually
approved.

## 5. Current limitations & deferred items

1. **Exact 2560×1440 validation — Deferred (hardware).** The attached panel is
   1920×1080; a 2560×1440 window clamps to 2560×1061. Layout at 2560-wide is
   verified; true 1440-tall output needs a 1440p display.
2. **Windowed exact-1080 — Deferred (OS constraint), covered by fullscreen.**
   Decorated windows clamp to the 1061-px work area; the exact-1080 acceptance
   evidence is the fullscreen run.
3. **Interactive click-through — Partial.** Hit-testing math is unit-tested and
   all screens run their real update paths under QA, but no synthetic
   mouse-click injection exists; a manual interactive pass is still worthwhile.
4. **QA death/victory overlay shows the held viewmodel** because the QA
   front-end path forces `appState` without setting `player.dead`; in real
   gameplay death the viewmodel is hidden (`renderHeldItem` checks
   `player.dead`). QA-only artifact, not shipped behavior.
5. **NVIDIA 131218 shader-state recompiles** — one-time first-use driver
   performance warnings can occur for several programs; intentionally not
   worked around because no steady-state impact or GL error was observed.
6. **Live windowed↔fullscreen toggling mid-session** is exercised only at
   startup (`setFullscreen(true)` after window creation) and through the
   options APPLY path in the persistence test; a dedicated soak of repeated
   runtime toggles was not automated.
7. The engine sun always sits in the southern sky, so north-facing benchmark
   cameras hide cast shadows behind their casters; the `ao_shadow` scene
   deliberately views from the north. Future scenic staging should keep this
   in mind.

## 6. Definition-of-done check

- Build/tests pass; final smokes: zero GL errors, zero missing assets — **met**.
- Phases 0–6 docs match the source/runtime results and the historical
  visual/performance acceptance record — **met** (the screenshot/log corpus is
  intentionally not shipped; see the retention note).
- Phase 4 visual/batching criteria — **met** (see table).
- Controlled AO/shadow evidence + five approved scenes — **met**.
- Screens/UI scales/Turkish glyphs/icons/persistence across the resolution
  matrix — **met**, with limitations 1–3 above explicitly deferred.
- Exact conditions + honest performance including actual framebuffer sizes and
  movement/chunk churn — **met**.
- Vertical slice, before/after index, acceptance table, asset workflow &
  provenance, limitations — **met** (`ASSET_PIPELINE.md` and
  `ASSET_PROVENANCE.md` unchanged and still accurate; no new assets added).

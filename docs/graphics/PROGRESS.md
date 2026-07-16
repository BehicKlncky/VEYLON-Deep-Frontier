# VEYLON Graphics Upgrade — Progress Log

> Artifact-retention note: screenshots and `build/qa` logs named in this work
> log were deliberately removed before the 0.2.0 release. Their names remain as
> a historical record of the acceptance pass, not as files shipped in the repo.

## 2026-07-14 — Baseline
- Build: PASS. Smoke (`VEYLON_SMOKE=20`): PASS, fps=100 @1280×720,
  chunks=169, creatures=12, npcs=4, save/load OK.
- Current visuals: flat RGB blocks, no textures/normals/AO/shadows, cube
  creatures, per-particle draw calls, stb_easy_font UI.
- Docs created (plan, art direction, pipeline, provenance).
- Fonts (Source Sans 3, OFL) bundled.

## Work log
- [x] Phase 0 — **Runtime-verified**: VEYLON_SEED, F2 screenshots, VEYLON_SHOT
  auto-capture, VEYLON_CAPTURE_TAG; baseline shots.
- [x] Phase 1 — **Runtime-verified**: gfx package, texture arrays, 42 materials /
  57 procedural layers; startup validation, build, and live smoke pass.
- [x] Phase 2 — **Visually approved + Performance-verified** (2026-07-14 final
  pass): textured terrain, vertex AO, PCF sun shadows. Controlled comparison
  `final2c_ao_on/only_off/off` (identical seed/camera/time; QA toggles
  VEYLON_SHADOWS + new VEYLON_AO) makes corner AO and cast shadows
  unmistakable. Timings in ACCEPTANCE_REPORT_2026-07-14.md.
- [x] Phase 3 — **Visually approved**: five fresh scene captures
  (`final2_scene_meadow/dawnfog/campfire_rain/ruin/toxic_2s`), each staged
  with foreground/midground/background and inspected. Older `qa_*` scene
  captures are superseded diagnostics.
- [x] Phase 4 — **Visually approved**: vertical slice
  (`final2_p4_verticalslice_1s`), Ashwolf 8-pose lineup + deterministic
  per-state sequence with carcass/pose-reset proof
  (`final2_p4_ashwolf_lineup_*`, `final2_p4_ashwolf_seq_0s..9s`), 30 m
  silhouette test (`final2_p4_silhouette30m_1s`), full held-item matrix at
  720p and 1080-class (`final2_held_cycle_*`), mining dust/impact/progressive
  cracks, blood/tracks/carcass, beacon, fire/smoke/rain. Particles instanced,
  submissions 0–2 in every logged frame. Structural role silhouettes added
  this pass: trader hip satchels, raider back-slung spear.
- [x] Phase 5 — **Visually approved / Runtime-verified**: STBTrueType Source
  Sans 3 (656 glyphs incl. Turkish, verified in rendered output), 70/70 icons,
  all gameplay screens at 1280×720 / 1920×1061 / 2560×1061, UI scale
  0.75/1.0/1.5, title/options/loading/death/victory, relaunch persistence
  proven across three processes via the real APPLY→save path. Interactive
  click simulation not automated (see report limitations).
- [x] Phase 6 — **Performance-verified**: exact-1920×1080 fullscreen 30 s smoke
  (843.5 avg FPS, p99 1.74 ms, max 28.13 ms, zero GL/KHR errors), windowed
  smoke, and a 75 s movement/chunk-churn run (511 chunks loaded, 1272 meshes
  rebuilt, 243 deleted, peak resident 329 — bounded retention). NVIDIA 131218
  identified as one-time first-use shader-state recompile warnings (non-errors);
  131154 comes from the QA screenshot readback. Max-frame spikes coincide with
  the harness's synchronous save/load step; steady-state p99 ≤ 2.2 ms.

## 2026-07-14 — Continuation checkpoint (before new implementation)

- Pre-edit build: PASS — `.\\gradlew.bat build`.
- Fixed-seed OpenGL smoke: PASS — `VEYLON_SEED=VEYLON-GRAPHICS-20260714`,
  `VEYLON_SMOKE=30`; save/load true, 248 chunks, 7 creatures, 4 NPCs, 45 tracks,
  68 live particles, reported 100 FPS at the existing 1280x720 window.
- Smoke did not report GL errors, missing-asset totals, draw calls, or triangles,
  so those acceptance claims remain open.
- `screenshots/day_6s.png`, `nightfire_6s.png`, and `toxic_6s.png` are newer
  textured output. `screenshots/dawnfog_6s.png` and `ruin_6s.png` are SHA-256
  identical to their baselines and invalid as after evidence. No current capture
  demonstrates the mandatory Ashwolf/NPC/tool/icon vertical slice.
- Risks found: old-world chunk meshes are not freed on load; cleanup uses fixed
  radius 6 and can churn meshes at larger settings; particle draw-call accounting
  assumes two submissions even when a bucket is empty.

## 2026-07-14 — Final acceptance pass (evidence-driven)

- Pre-edit and final `.\gradlew.bat build`: PASS (12 tests, 0 failures).
- Fixes this pass (all QA/staging/visual, no gameplay or save changes):
  showcase times moved from washed-out dusk to 15:30 side sun; Ashwolf lineup
  turned side-on and de-occluded (held pickaxe removed from scenic captures);
  new deterministic `ashwolf_seq` scene (1 s per core state + carcass +
  pose-reset proof); 30 m silhouette scene clustered at the crosshair;
  vertical-slice beacon moved off the glowdeer, toxic motes moved off the
  lineup, 3/4 subject angles; mining showcase now animates progressive cracks
  with dust bursts and swing arcs; `ao_shadow` scene re-staged with a
  south-facing camera (the sun always sits in the southern sky, so
  north-facing cameras hide every cast shadow behind its caster) plus a
  QA-only vertex-AO kill switch (`VEYLON_AO=0` → `uAoOn` uniform in
  chunk.frag); NpcModels gained trader hip satchels and a raider back-slung
  spear (visibility-gated per pose, no ID/save impact); relaunch-persistence
  QA hook `VEYLON_QA_SET_OPTIONS` routes through the options screen's real
  APPLY→save path.
- Authoritative evidence, per-requirement acceptance, evidence index,
  performance records, and limitations:
  **docs/graphics/ACCEPTANCE_REPORT_2026-07-14.md**.

## Performance notes
- Baseline: 100 fps during 20 s smoke (window likely occluded; vsync on).
- Continuation: 100 reported FPS during 30 s at 1280x720 (viability check only).
- Final (2026-07-14, RTX 1000 Ada laptop, driver 595.95, vsync off, defaults):
  - Fullscreen **exact 1920×1080**, 30 s: avg 843.5 FPS / 1.19 ms, p95 1.55,
    p99 1.74, max 28.13 ms; ~156 draw calls, ~179 k triangles; 0 GL errors.
  - Windowed 1920×1061, 30 s: avg 857.3 FPS, p99 1.76 ms, max 32.98 ms.
  - Movement/chunk churn, 75 s, ~320 m: avg 647 FPS, p99 2.21 ms; chunks
    169→511, meshes rebuilt 1272 / deleted 243 / peak resident 329 (bounded).
  - Max-frame spikes coincide with the smoke harness's synchronous isolated
    save/load and first-use warm-up, not steady-state rendering.

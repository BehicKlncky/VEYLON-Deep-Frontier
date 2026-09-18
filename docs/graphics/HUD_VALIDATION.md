# Gameplay HUD visual overhaul — 2026-09-18

The HUD uses 280×18 logical-pixel vital bars, 56px hotbar slots with 42px item
icons, semibold labels, dark panel skins and restrained teal/amber accents.
Primary vitals have symbols, numbers, quarter marks and explicit LOW/CRITICAL
words. Afflictions have warning symbols and two-line chips. Durability is a
continuous teal rail; freshness is a segmented amber rail. Temperature, wetness,
fatigue, shelter and load share a compact telemetry grid.

`HudLayout` owns the geometry and measured ellipsis fitting. The bottom-left
module and the hotbar keep separate bounds; recent events sit above the module
and reduce from six to two lines at 720p when all affliction rows are needed. Mission,
navigation, event and focus text is bounded. Creative uses the same normal
module footprint for flight/environment/shelter/terrain, without medical status.
The renderer's default scale, screen layouts, input, simulation and save formats
are unchanged. The optional panel corner inset keeps the existing skin's cyan
rivets inside fixed corners on tall HUD panels; existing screen callers retain
their original inset.

## Reproduce

Use Java 25 and the existing Gradle wrapper. On PowerShell:

```powershell
$env:VEYLON_SEED='20260918'
$env:VEYLON_SCENE='hud_showcase'
$env:VEYLON_GAME_MODE='survival'
$env:VEYLON_VSYNC='0'
$env:VEYLON_GL_DIAGNOSTICS='1'
$env:VEYLON_SHOT='3,7,11,16'
$env:VEYLON_RESOLUTION='1280x720'
$env:VEYLON_UI_SCALE='1'
$env:VEYLON_FULLSCREEN='0'
$env:VEYLON_CAPTURE_TAG='hud_final_survival_720'
.\gradlew.bat run
```

`hud_showcase` freezes the simulation on the deterministic meadow stage.
Seconds 0–4 show a 72% bow draw, 5–9 a 60% reload, 10–14 a thrown weapon and
15 onward mining progress. Vitals, secondary telemetry, a sprain, loaded
inventory, condition/freshness rails and a bound quest are fixed. Focus feedback
is scripted after the paused input path clears it. Creative stages flight and
environment without creating medical pressure. `hud_showcase_night` uses the
existing nightfire stage; `hud_showcase_fog` uses pinefog.

Set the mode to `creative`, resolution to `1920x1080`, or scale to `0.75`/`1.5`
for the other checks. Use windowed captures: fullscreen follows the monitor's
native resolution, which is 2560×1440 on this host. The existing renderer clamps
requested 1.5 scale to 1.0 at 1280×720; it honors 1.5 at 1920×1080.

Screenshots are in ignored `screenshots/`; logs/build output are in ignored
`build/`. No generated artifacts are tracked.

## Validation record

The original `hud_baseline_day_720_3s.png` and
`hud_baseline_showcase_720_3s.png` were captured and opened before changing the
HUD. Both were 1280×720 with zero GL/KHR errors. The first redesign inspection
identified stretched cyan panel corners; the final captures use fixed corners.

Every PNG below was opened and visually inspected. PNG header dimensions were
also checked against the native capture logs. No overlapping modules, clipped
controls or offscreen text were observed. Labels and rails remain legible over
bright meadow terrain, fog and nightfire. The selected slot's thicker border
and lift remain distinct at all tested scales. The crosshair's original aim
and spread geometry is retained, with a dark shadow for bright backgrounds.

All 19 final captures report **one UI submission, zero GL errors and zero KHR
errors**, including the shutdown diagnostic summaries. The NVIDIA driver emits
shader compilation/readback performance messages (131218/131154); these are
not GL/KHR errors. No framebuffer pixel assertions were added.

| Inspected filename in `screenshots/` | Actual framebuffer | Requested → effective scale |
| --- | --- | --- |
| `hud_final_survival_720_3s.png` | 1280×720 | 1 → 1 |
| `hud_final_survival_720_7s.png` | 1280×720 | 1 → 1 |
| `hud_final_survival_720_11s.png` | 1280×720 | 1 → 1 |
| `hud_final_survival_720_16s.png` | 1280×720 | 1 → 1 |
| `hud_final_creative_720_3s.png` | 1280×720 | 1 → 1 |
| `hud_final_survival_1080_3s.png` | 1920×1080 | 1 → 1 |
| `hud_final_creative_1080_3s.png` | 1920×1080 | 1 → 1 |
| `hud_final_survival_720_scale075_3s.png` | 1280×720 | 0.75 → 0.75 |
| `hud_final_creative_720_scale075_3s.png` | 1280×720 | 0.75 → 0.75 |
| `hud_final_survival_720_scale150_3s.png` | 1280×720 | 1.5 → 1 |
| `hud_final_creative_720_scale150_3s.png` | 1280×720 | 1.5 → 1 |
| `hud_final_survival_1080_scale075_3s.png` | 1920×1080 | 0.75 → 0.75 |
| `hud_final_creative_1080_scale075_3s.png` | 1920×1080 | 0.75 → 0.75 |
| `hud_final_survival_1080_scale150_3s.png` | 1920×1080 | 1.5 → 1.5 |
| `hud_final_creative_1080_scale150_3s.png` | 1920×1080 | 1.5 → 1.5 |
| `hud_final_survival_night_720_3s.png` | 1280×720 | 1 → 1 |
| `hud_final_creative_night_1080_3s.png` | 1920×1080 | 1 → 1 |
| `hud_final_survival_fog_1080_3s.png` | 1920×1080 | 1 → 1 |
| `hud_final_creative_fog_720_3s.png` | 1280×720 | 1 → 1 |

## Technical verification

Passed on Java 25, Windows, using the installed JDK explicitly in
`JAVA_HOME` because the shell did not initially expose Java on PATH:

```powershell
.\gradlew.bat test --tests '*HudQaSceneTest'
.\gradlew.bat test --tests '*Hud*Test' --tests '*BowGameplayWorkflowTest' --tests '*CreativeUnlimitedUseTest'
.\gradlew.bat test
.\gradlew.bat build
```

The complete suite passed **762 tests in 109 classes**, with zero failures,
errors or skips. The build passed, including Javadoc/doclint. The 58 new test
cases cover the opt-in fixture and Creative twin, 48 resolution/scale/mode/chip
combinations, all nine selected-slot positions, edge margins and non-overlap,
explicit warning thresholds, real Source Sans 3 semibold width fitting,
Unicode-safe truncation and exact bow/firearm/reload-hint/thrown label behavior.

Limits: requested 150% at 720p is intentionally capped by the pre-existing
renderer policy. At 75%, text is naturally smaller. Long HUD lines use a visible
ellipsis. The native captures exercise a representative sprain; the maximum
affliction count is covered by geometry tests. These captures verify HUD
presentation rather than a new performance benchmark or full gameplay smoke.

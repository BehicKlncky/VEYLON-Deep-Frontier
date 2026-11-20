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

## Urban milestone 21 — city map, place lines and prompts (2026-10-30)

Package 21 of the urban roadmap adds, in generator-19 cities only, a city map
screen (ground plan, entered buildings, found sites, objectives and rumours) with
a journal and a sketch of the storey underfoot, and two place lines in the field
panel (district; building and floor). Two changes reach every world: the
interaction box is 580 logical pixels wide instead of 440, and the storage
screen shows its owner in a warning or own-property colour, a search progress
bar and wear and freshness rails on its slots. The type scale, palette, panel
skin and the other modules' geometry are unchanged. `HudLayoutTest` still checks
every module, the wider prompt included, inside the margins and apart at 1280×720
and 1920×1080, at 0.75, 1 and 1.5 scale, in both modes and with 0, 1, 7 and 8
afflictions; `CityMapLayoutTest` checks the map screen's panels at 1280×720,
1707×960, 1920×1080, 2560×1440 and 1280×1024 logical, and that journal text wraps
within its panel in the bundled semibold face.

### Reproduce

```bash
export VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_SCENE=urban_onboarding
export VEYLON_GAME_MODE=survival VEYLON_RESOLUTION=1280x720 VEYLON_UI_SCALE=1
export VEYLON_CAPTURE_TAG=m21-onboarding-survival-720-2
./gradlew.bat run -PveylonDataDir=build/qa/m21-onboarding-survival-720-2 --console=plain
```

The tour writes 14 labelled captures into the QA directory's `screenshots/` and
closes itself (see `docs/DEVELOPING.md`). The other runs change the mode,
resolution and scale; the new-world and pause screens use `VEYLON_FRONTEND`
(`newworld-save`, `pause`, `pause-creative`) with `VEYLON_SHOT=3`, with and
without `VEYLON_GENERATOR=urban-dev`. Every QA directory was new.

### Validation record

All runs on 2026-10-30, NVIDIA GeForce RTX 3060 Ti, driver 610.88, OpenGL 3.3.0,
windowed. Each onboarding run printed `[urban-onboarding] pass=true` with 14
captures, every one `glErrors=0 khrErrors=0`, and a shutdown summary of
`glGetError=0` with 17 KHR messages and 0 KHR errors (the usual MEDIUM
pixel-transfer notes). Each screen run wrote one capture at 3 s with the same
error-free summary.

| Run (`build/qa/<tag>/screenshots/`) | Mode | Framebuffer | Requested → effective scale |
| --- | --- | --- | --- |
| `m21-onboarding-survival-720-2` | Survival | 1280×720 | 1 → 1 |
| `m21-onboarding-creative-720-2` | Creative | 1280×720 | 1 → 1 |
| `m21-onboarding-survival-720-scale075-2` | Survival | 1280×720 | 0.75 → 0.75 |
| `m21-onboarding-survival-1080-2` | Survival | 1920×1080 | 1 → 1 |
| `m21-onboarding-survival-1080-scale150-2` | Survival | 1920×1080 | 1.5 → 1.5 |
| `m21-onboarding-creative-1080-scale075-2` | Creative | 1920×1080 | 0.75 → 0.75 |
| `m21-newworld-city`, `m21-newworld-wilderness` | — | 1280×720 | 1 → 1 |
| `m21-pause-city`, `m21-pause-city-creative`, `m21-pause-wilderness` | Survival, Creative, Survival | 1280×720 | 1 → 1 |

Each tour covers day (10:00), night (23:00) and fog: captures `11-night-hud`,
`12-night-map` and `13-fog-hud`. The captures inspected and what they show are
listed in `docs/urban-survival/VALIDATION.md` (milestone 21). In them no module
overlapped another, no text left its panel, the wider interaction box held the
lock and search lines whole, the event log held the first-session lines whole
beside their timestamps, and the map's panels kept their layout at 0.75, 1 and
1.5. The map, storage and conversation screens are opaque UI panels, so night and
fog change only the world around them. Two faults were found in the captures
and fixed before the final runs: the map's journal and sketch cards first used
the slot skin, whose teal corner sat under their cyan headings, and now use the
HUD's panel skin; flipped edge labels on the map were clipped and are now whole.

Limits: the storage screen's description of the item under the cursor needs the
mouse, which the scripted tour cannot move, so no capture shows it; requested
1.5 at 1280×720 is still clamped to 1 by the renderer, so it is not a separate
run. These captures check presentation; behaviour is covered by the headless
tests named in the urban validation record.

## Urban milestone 26 — NEW FRONTIER's world choice and the city's death card (2026-11-06)

The abandoned city became the default new world. NEW FRONTIER now shows two world
tabs above the unchanged mode cards: "THE ABANDONED CITY", preselected, and "THE
WILDERNESS  (legacy)", switched with `W` or a click. The cards and a note under them
describe the chosen world; the replace notice names a new city. The tabs stand where
the title's rule stood, and the rule now parts the world from its mode. A city's death
card reads "THE CITY CLAIMS YOU / You wake at your refuge, with what you carried."

### Reproduce

As recorded at the time. 0.9.1 milestone 04 removed the replace notice with the single
save slot, and with it `newworld-save`; `newworld` shows the same screen today, and the
slot picker that follows it is validated in the 0.9.1 section below.

```bash
export VEYLON_SEED=20260731 VEYLON_SHOT=3 VEYLON_FRONTEND=newworld-save
VEYLON_RESOLUTION=1280x720 VEYLON_CAPTURE_TAG=m26-newworld-720-s1 \
  ./gradlew.bat run -PveylonDataDir=build/qa/m26-newworld-720-s1
# likewise 1920x1080 with VEYLON_UI_SCALE=0.75, 1.0 and 1.5; VEYLON_GENERATOR=wilderness
# for the wilderness preselected; VEYLON_FRONTEND=death for the city's death card
```

`VEYLON_FRONTEND=route` (then `route-load`) captures the same screens in play: the
title, both tabs, Creative chosen, loading, the refuge, a search, the map, the death
card, the wake, the pause menu and its graphics and audio screens (see
`docs/DEVELOPING.md`, "Default-path acceptance").

### Validation record

| Tag | Screen | Resolution | Scale requested → applied |
| --- | --- | --- | --- |
| `m26-newworld-720-s1` | City, replace notice | 1280×720 | 1 → 1 |
| `m26-newworld-1080-s075` | City, replace notice | 1920×1080 | 0.75 → 0.75 |
| `m26-newworld-1080-s1` | City, replace notice | 1920×1080 | 1 → 1 |
| `m26-newworld-1080-s15` | City, replace notice | 1920×1080 | 1.5 → 1.5 |
| `m26-newworld-wild-720` | Wilderness, its own notice | 1280×720 | 1 → 1 |
| `m26-death-city-720` | The city's death card over the refuge | 1280×720 | 1 → 1 |

Every capture: 0 `glGetError`, 0 KHR errors. In them the tabs, cards, notes, notice
and buttons keep their order without overlap, every line stays whole, and the key
line at the foot names `W`. Two faults were found in the captures and fixed before
the runs above: the title's rule first ran through the tabs (it now lies between the
tabs and the cards), and at 0.75 the tabs stood just under the title with a wide gap
above the cards (they now take the rule's old place at every scale).
`ui.CityOnboardingScreensTest` holds every new line within 820 units with the game's
own font. Limit: as in milestone 21, a requested 1.5 at 1280×720 is clamped to 1.

## 0.9.1 milestone 04 — the slot picker, its confirmations and the pause menu's slot (2026-11-20)

LOAD FRONTIER, and NEW FRONTIER's Enter, open the slot picker: one card per slot with its name,
its state at the right (READY, EMPTY, ANOTHER VERSION, or DAMAGED / BACKUP AVAILABLE) and up to
four lines from the slot's own summary: the world and mode, the day, time and time played, the
place, and when and by which build it was saved. Under the cards come the last notice (amber),
the note about VEYLON 0.9.0's `saves/veylon.sav` when it is there, and LOAD or START HERE,
RESTORE (loading only), DELETE and BACK; a button that does nothing for the selected slot is
dimmed. Replacing, deleting and restoring open a confirmation over the list, which names what
the slot holds. The block is centred on a canvas taller than 720 logical rows. The pause menu's
save keys name the slot: "[F5] Save to slot 1   [F9] Load slot 1   [Q] Quit".

### Reproduce

```bash
export VEYLON_SEED=20261120 VEYLON_FRONTEND=route-slots
VEYLON_RESOLUTION=1280x720 VEYLON_UI_SCALE=1 VEYLON_CAPTURE_TAG=v091-m04-slots-720-s1 \
  ./gradlew.bat run -PveylonDataDir=build/qa/v091-m04-slots-720-s1
# likewise 1280x720 at 0.75 and 1.5, and 1920x1080 at 0.75, 1 and 1.5, each in a new directory;
# copy 0.9.0's saves/veylon.sav into a directory first to see slot 3 as an older version and
# the note under the cards. VEYLON_FRONTEND=slots or slots-new with VEYLON_SHOT=2 shows the
# picker statically on a directory's slots as they are.
```

### Validation record

All runs on 2026-11-20, NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed, each
in a new directory `build/qa/v091-m04-<tag>/`. Every `route-slots` run printed
`[frontend-route] launch=slots pass=true` with `glErrors=0 khrErrors=0` and took 18 to 20 s;
every run's shutdown summary read `glGetError=0` and 0 KHR errors (only the usual MEDIUM
pixel-transfer notes).

| Tag | Screens | Framebuffer | Requested → effective scale |
| --- | --- | --- | --- |
| `slots-720-s1` | `01-title` to `10-delete-confirm`; slot 3 is 0.9.0's file, with the note | 1280×720 | 1 → 1 |
| `slots-720-s075` | the same; slot 3 is a later epoch's save | 1280×720 | 0.75 → 0.75 |
| `slots-720-s15` | the same | 1280×720 | 1.5 → 1 |
| `slots-1080-s075` | the same | 1920×1080 | 0.75 → 0.75 |
| `slots-1080-s1` | the same; slot 3 is 0.9.0's file, with the note | 1920×1080 | 1 → 1 |
| `slots-1080-s15` | the same | 1920×1080 | 1.5 → 1.5 |
| `screen-slots`, `screen-slots-new` | the static picker to load and to start, on the first run's slots | 1280×720 | 1 → 1 |

Each `route-slots` set holds the picker with every slot empty (`02-new-all-empty`), the pause
menu naming slot 1 (`03-pause-slot-1`), the picker with one slot used (`04-new-one-used`), a
damaged slot with its backup beside a ready slot and another version (`05-load-states`), the
reason under the cards (`06-other-version`), the restore confirmation (`07-restore-confirm`), the
restored city (`08-restored`), and the replace and delete confirmations (`09-replace-confirm`,
`10-delete-confirm`). In them the cards, the notice, the note and the buttons keep their order
without overlap and every line stays whole. Two faults were found in the first captures and
fixed before the runs above: a dimmed button kept its red channel and read as a warning, and at
0.75 on 1080p the block sat at the top of the screen. `ui.SlotPickerScreenTest` holds every line
within its room with the game's own font, and the picker cuts a place name that would not fit.
Limit: a requested 1.5 at 1280×720 is clamped to 1, as before.

## 0.9.1 milestone 05 — SAVING..., the autosave options and their keys (2026-11-20)

Before an autosave stalls a frame, the HUD shows **SAVING...** for 0.35 s in an amber-on-panel
strip in the gap between the two top cards, where SIMULATION PAUSED [P] goes (an autosave never
happens while P holds the world, so the two never show together); the log then reads
"Autosaved to slot N." The **AUTOSAVE** screen, from the title's new AUTOSAVE button (`A`) and
`A` in the pause menu, has two rows, Autosave (On/Off) and Every (5, 10, 15 or 30 minutes; dimmed
while autosave is off), a line on when it waits, the keys, and APPLY [F5] / BACK [Esc]. The title
has six buttons, its notice moves below the sixth and its key line reads "N / L / O / V / A / Q";
the pause menu's save line reads "[F5] Save to slot 1   [F9] Load slot 1   [A] Autosave   [Q] Quit".

### Reproduce

```bash
export VEYLON_SEED=20261120
VEYLON_SCENE=urban_autosave VEYLON_AUTOSAVE=on VEYLON_RESOLUTION=1280x720 \
  VEYLON_CAPTURE_TAG=v091-m05-autosave-survival-1280x720 \
  ./gradlew.bat run -PveylonDataDir=build/qa/v091-m05-autosave-survival-1280x720
# likewise 1920x1080 at 1 and 0.75, and VEYLON_GAME_MODE=creative, each in a new directory.
# VEYLON_FRONTEND=autosave (the options screen), pause (the pause menu) or glyphs (the title)
# with VEYLON_SHOT=2 show the screens statically.
```

### Validation record

All runs on 2026-11-20, NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed, seed
20261120, each in a new directory `build/qa/v091-m05-<tag>/`; logs are
`build/v091-m05-native-<tag>.log`. Every run's shutdown summary read `glGetError=0` and 0 KHR
errors; every tour printed `[urban-autosave] pass=true`.

| Tag | Screens | Framebuffer | Requested → effective scale |
| --- | --- | --- | --- |
| `autosave-survival-1280x720` | `01-claim-saving` to `06-after-the-pursuit-autosaved` (10) | 1280×720 | 1 → 1 |
| `autosave-survival-1920x1080` | the same | 1920×1080 | 1 → 1 |
| `autosave-survival-1920x1080-s075` | the same | 1920×1080 | 0.75 → 0.75 |
| `autosave-creative-1280x720` | the same without the hunt (7) | 1280×720 | 1 → 1 |
| `screen-autosave-720`, `screen-autosave-1080-s075` | the options screen | 1280×720, 1920×1080 | 1, 0.75 |
| `screen-pause-720`, `screen-title-720` | the pause menu in a city, the title (`glyphs`) | 1280×720 | 1 → 1 |

In each `-saving` capture SAVING... sits centred in the strip between the two top cards and
clears both; in each `-autosaved` capture the log's last line is "Autosaved to slot 1.", after
"You claim the first-floor flat as a refuge…" or "You wake after 2.0 hours." The hunted capture
shows the timer's autosave due with no strip up. The options screen keeps its rows, the waiting
line, the keys and the buttons apart at both sizes; the title's six buttons, its notice and its
key line do not touch; the pause menu's save line fits its panel. At 1920×1080 the strip spans the
whole gap, as the pause panel always has. `ui.AutosaveOptionsScreenTest` holds every new line within
its room with the game's own font.

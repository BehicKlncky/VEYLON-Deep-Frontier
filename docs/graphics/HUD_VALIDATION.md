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

## 0.9.1 milestone 06 — the seed field, the way back to the title and the controls (2026-11-20)

NEW FRONTIER has a seed row under the world's note: SEED, a field holding the offered seed's
number (or the words typed, with a caret while typing and the placeholder "a number or any
words" while empty) and REROLL [R]; a line under it says where the seed comes from, or for
words, "These words make seed N."; the slot line and the buttons follow, and the key line names
`E` and `R` (while typing it lists the typing keys instead). The pause menu names every world
with its seed under the mode, "The abandoned city  /  seed N" in amber or "The wilderness  /
seed N" in teal, without the development notice, and its keys take three lines: playing on and
the mode, the settings ("[O] Graphics   [V] Audio   [C] Controls   [A] Autosave"), and saving
and leaving ("[F5] Save to slot 1   [F9] Load slot 1   [R] Return to title   [Q] Quit"); the
panel is 590 units tall. `R` opens **RETURN TO THE TITLE** over the world: how much play the
slot's save is behind, what returning without saving leaves, three stacked buttons (SAVE AND
RETURN [Enter], RETURN WITHOUT SAVING [N], CANCEL [Esc]), room for a failed save's line, and the
keys. The **CONTROLS** screen, the title's seventh button (`C`) and `C` in the pause menu, has
Mouse sensitivity (10–300%) and Invert mouse Y, a line on what invert does, the keys, and
APPLY [F5] / BACK [Esc]. After a return the title's notice reads "Saved to slot 1." or "Slot 1
keeps its last save." under QUIT.

### Reproduce

```bash
export VEYLON_SEED=20261120
VEYLON_FRONTEND=route-session VEYLON_RESOLUTION=1280x720 VEYLON_CAPTURE_TAG=v091-m06-session-720 \
  ./gradlew.bat run -PveylonDataDir=build/qa/v091-m06-session-720
VEYLON_FRONTEND=route-session-relaunch VEYLON_RESOLUTION=1280x720 \
  VEYLON_CAPTURE_TAG=v091-m06-session-720-relaunch ./gradlew.bat run -PveylonDataDir=build/qa/v091-m06-session-720
# likewise 1920x1080 at 1 and 0.75, each pair in a new directory. VEYLON_FRONTEND=newworld,
# controls, pause, pause-creative, or pause with VEYLON_GENERATOR=wilderness, with VEYLON_SHOT=2
# or 3, show the screens statically. The script is build/v091-m06-native.sh.
```

### Validation record

All runs on 2026-11-20, NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed, seed
20261120, each in a new directory `build/qa/v091-m06-<tag>/` (a relaunch in its launch's); logs
are `build/v091-m06-native-<tag>.log`. Every route printed `pass=true` with `glErrors=0
khrErrors=0`, and every run's shutdown summary read `glGetError=0` and 0 KHR errors (only the
usual MEDIUM pixel-transfer notes).

| Tag | Screens | Framebuffer | Requested → effective scale |
| --- | --- | --- | --- |
| `session-720`, `session-720-relaunch` | `01-title` to `10-title-returned`, then `01-controls-persisted` | 1280×720 | 1 → 1 |
| `session-1080`, `session-1080-relaunch` | the same | 1920×1080 | 1 → 1 |
| `session-1080-s075`, `session-1080-s075-relaunch` | the same | 1920×1080 | 0.75 → 0.75 |
| `slots-720` | milestone 04's `01-title` to `10-delete-confirm`, each world now left with `R` and `N` | 1280×720 | 1 → 1 |
| `route-720`, `route-720-load` | the default path's two launches (15 and 7 captures) | 1280×720 | 1 → 1 |
| `screen-pause-wild-720`, `screen-pause-creative-720` | the pause menu in a Survival wilderness and a Creative city | 1280×720 | 1 → 1 |
| `screen-newworld-720`, `screen-controls-1080-s075` | NEW FRONTIER and the title's controls screen, statically | 1280×720, 1920×1080 | 1, 0.75 |

Each `session` set holds the title with its seven buttons (`01-title`), NEW FRONTIER offering
20261120 (`02-new-frontier-offered`), the words "deep frontier" typed with "These words make seed
287000769." under them (`03-new-frontier-typed`), the rolled seed 4110970681813779
(`04-new-frontier-rerolled`), the pause menu in the city of seed 287000769 (`05-pause-city`), the
controls at their defaults and at 170% with invert on (`06-controls`, `07-controls-changed`), the
question with "Since slot 1 was last saved: under a minute of play." (`08-return-question`), and
the title after Save and return and after Return without saving (`09-title-saved`,
`10-title-returned`); each relaunch shows 170% and On read at launch. In them the seed row, its
line, the slot line and the buttons keep their order without overlap, the field holds every
seed whole, and every line stays inside its panel; the title's notice sits between QUIT and the
key line. At 720 rows the taller pause panel reaches to within a few units of the hotbar's top,
which the menu's dim covers. `ui.CityOnboardingScreensTest`, `ui.ReturnToTitlePromptTest` and
`ui.ControlsOptionsScreenTest` hold every new line within its room with the game's own font, the
seed field and the pause menu's world line for the longest seed there is. Limits: a requested 1.5
at 1280×720 is clamped to 1, as before; mouse look is off in automated sessions, so the captures
show the settings, not a turn.

## 0.9.1 milestone 07 — the end cards without the aimed block or negative health (2026-11-26)

Two HUD defects showed on the end cards. The name of the block aimed at (the target label under
the crosshair) was drawn under the death card, the city's answer and the pause menu, whose
translucent backdrops let it through, though none of them updates the aim (B06); it is now drawn
only while neither card is up and no pausing screen is open. After an overkill hit the vitals
printed negative health, "-900 / 100" after the route's 1,000-damage kill, and the low-health
pulse grew past its full strength (B15); the panel now prints health clamped at 0 and the pulse
reads health as shown. Nothing else on either card changed.

### Reproduce

```bash
export VEYLON_SEED=20261126
VEYLON_FRONTEND=route VEYLON_RESOLUTION=1280x720 VEYLON_CAPTURE_TAG=v091-m07-after-route-720 \
  ./gradlew.bat run -PveylonDataDir=build/qa/v091-m07-after-route-720
VEYLON_SCENE=urban_missions VEYLON_RESOLUTION=1280x720 VEYLON_CAPTURE_TAG=v091-m07-after-missions-720 \
  ./gradlew.bat run -PveylonDataDir=build/qa/v091-m07-after-missions-720
# The script, build/v091-m07-native.sh, runs these and the milestone's other scenes on a given
# checkout; the "before" runs used a worktree at bb17c31.
```

### Validation record

All runs on 2026-11-26, NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed, seed
20261126, each in a new directory `build/qa/v091-m07-<phase>-<tag>/`; logs are
`build/v091-m07-<phase>-<tag>.log`. Both routes printed `pass=true` with `glErrors=0
khrErrors=0` before and after, and every run's shutdown summary read `glGetError=0` and 0 KHR
errors.

| Tag | Capture | Before (`bb17c31`) | After |
| --- | --- | --- | --- |
| `route-720` | `11-death`, the city's death card after the 1,000-damage kill | "-900 / 100" under Health, the edges solid red, and "Brick" under the card's sentence | "0 / 100", the edges tinted as at any death, no label |
| `missions-720` | `12-the-city-answers`, the city's answer over the radio | "Radio Workstation" under the card's sentence | no label |

The pause menu's panel covers the label's place, so `14-pause` looked the same before and after;
`HudTargetLabelTest` proves the label is not drawn there, nor under the screens opened from it.
`ui.HudVitalsTest` holds the figures and the pulse. Limits: the crosshair still shows through
both cards, as it always has, and the HUD under them is otherwise unchanged.

## 0.9.1 milestone 09 — the release's acceptance route (2026-11-27)

No screen or HUD element changed in this milestone. The release route
(`VEYLON_FRONTEND=route-release`, its relaunch and its restore) captured every screen of
0.9.1's save flow in one sequence, on the release tree, for each world kind and mode: NEW
FRONTIER with a typed seed, the slot picker, the night's sleep and the autosave's SAVING...,
the pause menu naming each slot and its world's seed, the question on the way back to the
title, the picker with two ready slots and 0.9.0's file, a slot of another version refused, the
CONTROLS screen before and after, the settings read back at a relaunch, and a damaged slot
restored from its backup.

### Reproduce

```bash
# build/v091-m09-native.sh copies the matching 0.9.0 save into each directory first; the saves
# came from the tagged 0.9.0 build (build/v091-m09-v090-saves.sh).
VEYLON_SEED=20261127 VEYLON_AUTOSAVE=on VEYLON_FRONTEND=route-release VEYLON_RESOLUTION=1280x720 \
  VEYLON_CAPTURE_TAG=v091-m09-city-survival ./gradlew.bat run -PveylonDataDir=build/qa/v091-m09-city-survival
VEYLON_SEED=20261127 VEYLON_FRONTEND=route-release-relaunch VEYLON_RESOLUTION=1280x720 \
  VEYLON_CAPTURE_TAG=v091-m09-city-survival-relaunch ./gradlew.bat run -PveylonDataDir=build/qa/v091-m09-city-survival
cp -r build/qa/v091-m09-city-survival build/qa/v091-m09-city-survival-restore
VEYLON_SEED=20261127 VEYLON_FRONTEND=route-release-restore VEYLON_RESOLUTION=1280x720 \
  VEYLON_CAPTURE_TAG=v091-m09-city-survival-restore ./gradlew.bat run -PveylonDataDir=build/qa/v091-m09-city-survival-restore
# Creative adds VEYLON_GAME_MODE=creative, the wilderness VEYLON_GENERATOR=wilderness.
```

### Validation record

All runs on 2026-11-27, NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed,
1280 × 720, seed 20261127, each world kind and mode in its own directory
`build/qa/v091-m09-<tag>/` and its copy `…-restore/`; logs are
`build/v091-m09-native-<tag>[-relaunch|-restore].log` and the summary
`build/v091-m09-native-summary.txt`. Every launch printed `pass=true` with `glErrors=0
khrErrors=0`, and every shutdown summary read `glGetError=0` and 0 KHR errors.

| Tag | `route-release` | `-relaunch` | `-restore` |
| --- | --- | --- | --- |
| `city-survival` | 35 s, 21 captures | 11 s, 4 | 8 s, 3 |
| `city-creative` | 34 s, 21 | 11 s, 4 | 9 s, 3 |
| `wild-survival` | 30 s, 20 (no search) | 10 s, 4 | 8 s, 3 |
| `wild-creative` | 31 s, 20 (no search) | 11 s, 4 | 8 s, 3 |

- `02-new-frontier-typed`: the field holds "safe saves" and the note gives its seed,
  1788257347; `03-world-a` is that world in slot 1.
- `06-saving` and `07-autosaved`: SAVING... in the top gap while the waking's fade is still
  dark, then "Autosaved to slot 1." in the log under "You wake after 2.0 hours". In the
  wilderness the staged bedroll is in view with "[F] Sleep (bedroll)".
- `08-pause-slot-1` and `12-pause-slot-2`: the pause menu names the world and its seed (the
  wilderness's "The wilderness  /  seed 20261127") and its keys name the slot, "[F5] Save to
  slot 2   [F9] Load slot 2   [R] Return to title   [Q] Quit".
- `13-return-question`: "Since slot 2 was last saved: under a minute of play." over SAVE AND
  RETURN, RETURN WITHOUT SAVING and CANCEL.
- `15-load-two-slots`: two READY cards with their world, mode, day and time, place and "a
  development build, save epoch 1" (a run from Gradle has no jar version), the third empty, and
  under them "saves/veylon.sav is a save from VEYLON 0.9.0 or earlier: this version cannot open
  it and leaves it as it is."
- `18-slot-3-other-version` and `19-slot-3-refused`: slot 3 reads ANOTHER VERSION in orange,
  "This save was written by an older version of VEYLON. Start a new world." on its card and,
  after Enter, under the cards; LOAD is dimmed.
- `20-controls`, `21-controls-changed` and the relaunch's `02-controls-persisted`: 100% and Off,
  then 170% and On, then 170% and On read at launch.
- The restore's `01-slot-2-damaged`: "DAMAGED / BACKUP AVAILABLE", "This save is damaged and
  cannot be opened." and the backup's time and contents, with RESTORE [R] lit;
  `02-restore-confirm` and `03-restored`, the world F5 saved.

Limits: the city's SAVING... capture falls under the waking's fade, as a player sees it; the
wilderness's bedroll is staged beside the survivor, since that world has no refuge bed.

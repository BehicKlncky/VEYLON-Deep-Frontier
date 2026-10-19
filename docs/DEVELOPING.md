# Developing VEYLON: Deep Frontier

Practical guide for working on the codebase. Read
[ARCHITECTURE.md](ARCHITECTURE.md) first if you need the control flow; this
document is about getting things done.

Setup requirements (JDK 25, OpenGL 3.3, the proxy workaround) are in
[README.md](../README.md#requirements).

---

## Running

```bash
./gradlew run            # play the game
./gradlew test           # 886 deterministic tests, headless, ~4 min
./gradlew performanceTest # 5 wall-clock benchmarks + the per-system tick
                          # profile; reference PC only
./gradlew build          # compile + test
./gradlew fatJar         # self-contained JAR in build/libs/
```

On Windows use `.\gradlew.bat`. Tests need no display or GPU — they construct
`Game` directly and never enter the render loop.

### Running a single test

```bash
./gradlew test --tests "com.veylon.GameLoopIntegrationTest"
./gradlew test --tests "*.newWorldClears*"
```

### QA and capture runs

The game reads `VEYLON_*` environment variables for reproducible, unattended
runs. All are inert when unset. Everything below is handled by `QaHarness`;
`VEYLON_AO` (renderer ambient-occlusion toggle) and `VEYLON_GL_DIAGNOSTICS`
(GL debug output) are read by `Renderer` and `Window` instead.

| Variable | Effect |
|---|---|
| `VEYLON_SEED` | Fixed world seed (a non-numeric value is hashed) |
| `VEYLON_GAME_MODE=survival\|creative` | Initial mode for automated world runs only; absent means Survival. Invalid values fail explicitly. Ignored for normal title sessions and frontend-only captures. |
| `VEYLON_GENERATOR=urban-dev` | Development selector: NEW FRONTIER and automated worlds use the newest urban generator (`UrbanRevision.DEVELOPMENT_GENERATOR`, now `World.GEN_URBAN_ENCOUNTERS`: generator 10, layout 7 / template 4 / content 3, encounter catalogue 1). Absent keeps the ordinary default. Any other value fails, and so does `urban-dev` without an isolated data directory (see below). |
| `VEYLON_SMOKE=<seconds>` | Release smoke gate: save/load, fire, storm, a fortress approach, then a pass/fail report. Throws on failure |
| `VEYLON_SCENE=<name>` | Stage a deterministic benchmark scene (`day`, `pinefog`, `nightfire`, `ruin`, `toxic`, `ao_shadow`, `phase4`, `ashwolf`, `silhouette30`, `vfx_blood`, `vfx_mining`, `vfx_beacon`, `death_ragdoll_showcase`, `death_ragdoll_sequence`, `dismember_showcase`, `dismember_wall`, `dismember_bomb` (a real scrap bomb thrown into a group of six, one outside the lethal radius; it goes off at 2.4 s), `dismember_species`, `dismember_species_wall` and `dismember_species_close` (every species, a guard and the player's remains blown apart at 1 s; open meadow, a step and a wall, and a close view of the small bodies), `molotov_ground`, `molotov_tree` and `molotov_rain` (a real fire bomb thrown at 0.5 s onto a dry clearing at dusk, beside a round tree that catches, or with rain from 3 s that puts the pool out; shots `0.7,1.2,3,7,11`, `1,4,10,20` and `2,3.5,5`), the burning-body scenes `body_fire_row`, `body_fire_row_night`, `body_fire_close`, `body_fire_out`, `body_fire_blast`, `body_fire_panic`, `body_fire_bird`, `body_fire_rain`, `body_fire_ragdoll` and `body_fire_player` (see "Burning bodies QA" below), `movement`, `inventory`, `ui_cycle`, `held_*`, `creative_flight` with `VEYLON_GAME_MODE=creative`, which flies east at Shift speed and prints a `[flight]` streaming report at 29 s, and the urban scenes `urban_streets`, `urban_intersection`, `urban_seam_walk`, `urban_fixtures`, `urban_fixtures_night`, `urban_apartments`, `urban_apartments_night`, `urban_apartments_lit_night`, `urban_apartments_manual`, `urban_apartments_manual_night`, `urban_loot`, the playable-slice route `urban_slice` and the catalogue walk `urban_catalogue` with `VEYLON_GENERATOR=urban-dev` (see "Urban planning QA" below)) |
| `VEYLON_SHOT="5,10"` | Capture screenshots at those elapsed seconds |
| `VEYLON_FRONTEND=<name>` | Pin a front-end screen (`options`, `audio`, `loading`, `death`, `victory`, `glyphs`, `newworld`). `newworld-save` shows the replace-save notice without writing a save. `pause` and `gamemode` stage a Survival world with the pause menu or the mode confirmation open; `pause-creative`, `gamemode-creative` and `victory-creative` stage a Creative world; `catalog`, `catalog-tools`, `catalog-search` (query "iron") and `catalog-inventory` open the Creative catalog; `worldcontrols` and `worldcontrols-held` open the Creative world controls, the second with dusk, a frozen clock, a locked storm and paused spawning already applied |
| `VEYLON_RESOLUTION=1920x1080` | Framebuffer override |
| `VEYLON_UI_SCALE`, `VEYLON_VSYNC`, `VEYLON_FULLSCREEN`, `VEYLON_SHADOWS` | Settings overrides |
| `VEYLON_AUDIO_QA=1` | Add three positional audio reports per second to the smoke, without gameplay effects |
| `VEYLON_NO_EFX=1` | Force the documented dry audio fallback for device QA |
| `VEYLON_MIN_FPS` | Stricter FPS gate layered over the mandatory 60 |
| `VEYLON_CAPTURE_TAG` | Filename prefix for captures |
| `VEYLON_QA_SET_OPTIONS="fov=85,bloom=false"` | Mutate and save settings through the real APPLY path, to prove persistence across relaunch |

Example:

```bash
VEYLON_SEED=20260716 VEYLON_SCENE=day VEYLON_SHOT=6 ./gradlew run
```

#### Isolated QA data

Saves, settings and captures live in the data directory, which on Windows is the
working directory — the checkout itself when you run from Gradle. Keep QA away
from the real save slot:

- Tests always run with `-Dveylon.dataDir=build/test-data/<task>`, set by
  `build.gradle` for every `Test` task and checked by `SaveSlotIsolationTest`.
- For a native QA session pass `-PveylonDataDir=build/qa/<name>` to `run`. The
  game then reads and writes only there, including F5 saves.
- `VEYLON_GENERATOR=urban-dev` refuses to start unless the data directory is
  set and differs from the player's own (`AppPaths.isolatedFromPlayerData`).

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
.\gradlew.bat run -PveylonDataDir=build/qa/urban
```

#### Urban planning QA

The urban scenes need the street network (`VEYLON_GENERATOR=urban-dev`) and an
isolated data directory; on any other world they print a notice and do nothing.
Each prints `[urban-qa] plan ...` diagnostics for the four regions that meet at
the origin (overlaps, parcel rules, routes and seams, all expected to be 0) and
the spawn column.

| Scene | What it shows |
|---|---|
| `urban_streets` | Eye level on a corner sidewalk, looking north up a local street to the avenue on the region edge |
| `urban_intersection` | An elevated view of the avenue crossing where four regions meet at the origin |
| `urban_seam_walk` | Real player movement east along a local street from x −40 to x 40, across the region seam at x 0; at 24 s it prints `[urban-qa] seam-walk ... pass=true` when the walk crossed the seam and at least four chunk borders without a collision, an unloaded column or a change of ground height |
| `urban_fixtures` | Package 04's `FixtureShowcase` house beside the spawn: the facade (0–4 s); at 4 s the front door and a window are opened and the other window shattered through the production commands (facade again to 7 s); the door closed and seen from inside with its HUD prompt (7–10 s); the windows from inside (10–13 s); the stairs from their foot (13–16 s) and from the roof (16–19 s); at 19 s a real walk in through the front door and up the stairs, reporting `[urban-qa] fixtures-walk ... pass=true` at 32 s |
| `urban_fixtures_night` | The same house at 23:00 with lanterns lit inside: facade, windows from inside, stairs, the door from inside (shots at 3, 6, 9 and 12 s) |
| `urban_apartments`, `urban_apartments_night` | Actual generated boundary-spanning multi-storey apartment with B1. Seven scripted camera holds: street, entrance, kitchen, upper floor, basement, exit, stair foot. Capture at `3,7,11,15,19,23,27`. Logs and default filenames explicitly label scripted-camera appearance evidence; these views do not prove navigation. No staged structure, world lights or loot. |
| `urban_apartments_lit_night` | Supplemental 23:00 appearance sequence with the same seven scripted views. Adds exactly four QA portable torches through ordinary recorded block edits in previously empty cells, preserving structure and fixtures. Logs and default filenames identify the portable-light instrumentation; this is readability evidence, not generated utilities or human navigation. |
| `urban_apartments_manual`, `urban_apartments_manual_night` | Start-only positioning outside the same generated apartment, forced Survival, normal mouse look/movement/F interactions afterward. Without `VEYLON_SHOT`, stays open for a human walk. Record the seed, building id, mode, actual route, clipping/stair findings and exit. The scene itself never records a manual success. |
| `urban_loot` | Survival on real generated cupboards: abandoned kitchen search/take at 0–4 s, survivor cupboard search/take at 5–9 s, simulation/noise/ownership gate at 10 s. No structure or loot injection; the normal scheduler must advance searches. Capture `1,4,6,10,14`. Throws if the gate fails or searches have not completed by 13 s. Scripted positioning/commands, not a human walk. |
| `urban_starter_manual` | Fresh starter-compatible Survival, normal controls and read-only five-second position/inventory evidence. No pose, weather, clock or supply injection. Walk from the bedroom through the courtyard to an apartment/shop, search with F, take supplies and return. Use a fresh isolated directory, capture the spawn, save with F5 and capture the return. |
| `urban_slice` | The package-11 slice gate in either mode on a fresh generator-10 world, with its own clock, weather and events. The route forces an interior door and searches a multi-floor apartment that hosts an encounter. It handles an infected, searches the shop, bars the refuge's street door, eats, drinks, crafts the reinforced spear, quick-saves with F5 and quick-loads with F9 over the live world. It then revisits in reverse order. Prints `[urban-slice] pass=true`, three `memory` checkpoints and a timeline, writes 16 labelled captures and closes itself. |
| `urban_catalogue` | The package-12 catalogue walk in either mode on a generator-11 world. It visits the nearest building of each commercial and civic family: a damaged grocer, a clinic and a security post that keep their locks, a school and an office block with upper floors, and a warehouse. Moving between buildings is a staged pose at each street door, and the run starts with a staged spear and pry tool; both are printed as staged. Inside it plays through the production input seam: the sign, a shop floor, the clinic's locked staff door, the street door, the dispensary forced by holding the left button and its stock searched with F, a treatment room, ground and upper classrooms and offices, the armoury lock, the squad room, a rack aisle and the workbench. Prints `[urban-catalogue] pass=true`, writes 16 labelled captures and closes itself. |

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_SCENE='urban_seam_walk'; $env:VEYLON_SHOT='3,9,14,25'
.\gradlew.bat run -PveylonDataDir=build/qa/seam-walk
```

Keep a shot at or after 25 s for the walk, because the run closes after its last
shot (33 s for `urban_fixtures`). Generators 4–5 keep their unbuilt parcels;
6 has structural interiors, 7 furnished interiors with cube stairs, and 8
directional half-height treads. Generator 9 adds the ground-floor
refuge and finite starter supplies. Its HUD identifies the refuge; full district
climate/presentation remains in packages 13 and 21. Generator 10
keeps that geometry/loot and adds catalogue-1 Veilbound encounters. Generator 11
(`urban-dev`) keeps generator 10's walk-ups and starter superblock and adds the
commercial and civic catalogue with encounter catalogue 2; its frontage signs read
through the interaction prompt.

For infected acceptance, run `urban_infected_street` and
`urban_infected_apartment` separately with generator `urban-dev`, seed `20260731`,
Survival, shots `0.7,3,4.1,8,14,17`, distinct capture tags and fresh isolated data
directories. The scenes use production anchor admission and unchanged generated
geometry. Scripted observer poses expose normal-scheduler pursuit/strike, an
unarmed player pain hit at 4 s, instrumented direct ignition at 6 s and a
head-height projectile at 10 s. These inputs supply no scavenging or ammunition
progression evidence. At 12 s a real save/load must preserve the defeated id
and non-harvestable humanoid remains; `[urban-infected] pass=true` is required
before 16 s. Wall-clock updates without simulation must fail. Inspect the tilted
head/braced shoulder, readable windup/pain/fire poses and settled body, plus
GL/KHR/native-audio diagnostics. Human playtesting/listening is deferred until after 22 (D-80).

For indoor navigation QA, run `VEYLON_SCENE=urban_navigation` with generator
`urban-dev`, seed `20260731`, Survival, resolution `1280x720`, shots
`2,12,20,21,24,54,84` and an isolated directory such as
`-PveylonDataDir=build/qa/m10-navigation-verified`. A fixture Veilbound starts at a
generated apartment entrance; scripted player poses and positioned gunshot
events supply evidence upstairs. The ordinary scheduler must move it through
doors and two floors. The target then relocates to an occluded basement pose;
the infected must retain its old evidence and give up. Require
`[urban-navigation] pass=true` before 85 s and zero GL/KHR/native-audio errors.
This is scripted pursuit/occlusion/expiry evidence, not a human escape walk.
No structure edits, path injection, supplied loot or save publication are used.
The runtime snapshot reports path queries/expansions/deferred/failures and
unsupported entries; the hard per-tick allowance is four queries / 2,800 nodes.

For contextual scavenging QA, use seed `20260731`, generator `urban-dev` and
`VEYLON_GAME_MODE=survival`. Set `VEYLON_SCENE=urban_loot`,
`VEYLON_SHOT=1,4,6,10,14`, a distinct `VEYLON_CAPTURE_TAG`, and run
`.\gradlew.bat run -PveylonDataDir=build/qa/urban-loot --console=plain`.
Keep the last shot at 14 s so an interrupted search cannot evade the deadline.
Unset frontend/smoke variables. Inspect hidden contents and progress during each
search, ownership warnings, readable instructions and the remaining supplies.
The log must report kitchen/owned noise, no abandoned theft, owned reputation
loss and `loot pass=true` with advancing clock and hunger. This scene teleports
between two real interaction targets; navigation evidence stays separate.
Closing with F/Esc keeps search progress. Left click transfers a stack; right
click transfers one. Full destinations and interruptions are covered through
production transaction/save tests rather than inferred from screenshots.

For a fresh starter route through normal controls:

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_starter_manual'
$env:VEYLON_SHOT='5,1200'; $env:VEYLON_CAPTURE_TAG='urban-starter-normal-controls'
.\gradlew.bat run -PveylonDataDir=build/qa/urban-starter-fresh --console=plain
```

Use a new directory for each fresh route. The scene forces Survival but does not
drive movement, reset conditions or grant supplies. Follow room hints, open doors
with F, search cupboards, click supplies, return and save with F5. Its five-second
log records position, camera, screen, searched rows, food, water and first upgrade.
The final timed shot closes the session; close earlier after capturing the return.
Treat OS-scripted normal controls and human playtests as distinct evidence.

For the playable-slice gate (package 11), run each mode in a fresh directory:

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_slice'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m11-slice-survival'
Remove-Item Env:VEYLON_SHOT -ErrorAction SilentlyContinue
.\gradlew.bat run -PveylonDataDir=build/qa/m11-slice-survival --console=plain
```

Repeat with `creative` in the mode, tag and directory for the twin. The runner
plays through the production input seam. It holds W, Ctrl, F, C, 1–9, F5, F9
and the left and right mouse buttons, and turns the camera as mouse look would.
Container and crafting clicks use the commands those screens call. Nothing is
staged: no item, pose, block, body, clock or weather. The frozen capture list,
written as `<tag>_<label>.png` under the data directory's `screenshots/`, is:

`01-refuge-start`, `02-courtyard`, `03-apartment-entry`, `04-forced-door`,
`05-search-hidden`, `06-search-revealed`, `07-upper-floor`, `08-threat`,
`09-defeated`, `10-shop`, `11-barred-door`, `12-craft`, `13-after-load`,
`14-revisit-shop`, `15-revisit-locker` and `16-end`.

Require `[urban-slice] pass=true` and zero GL, KHR and native-audio errors.
Record the host, the three `[urban-slice] memory` lines and the route
observations (engaged and defeated ids, damage, harm events, minimum body
temperature, events, walked blocks and game minutes). The memory lines give
retained chunks and a post-GC best-effort heap sample; they are host
measurements, not budgets. `UrbanSliceRouteTest` plays the same scene headlessly
in `Game.run` order on the frozen slice seeds. This scripted route is not the
human playtest deferred by D-80. `urban-dev` now creates generator 11, whose starter
superblock is generator 10's; the frozen headless gate still names generator 10.

For the commercial and civic catalogue (package 12), run each mode in a fresh
directory:

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_catalogue'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m12-catalogue-survival'
Remove-Item Env:VEYLON_SHOT -ErrorAction SilentlyContinue
.\gradlew.bat run -PveylonDataDir=build/qa/m12-catalogue-survival --console=plain
```

Repeat with `creative` for the twin; a Creative break removes the dispensary door
instead of forcing it. The log names each chosen building (template, family,
variant, parcel, sign) and every staged pose. The frozen capture list is:

`01-grocery-sign`, `02-grocery-shelves`, `03-clinic-staff-door-locked`,
`04-clinic-lobby`, `05-clinic-dispensary-forced`, `06-clinic-dispensary-stock`,
`07-clinic-treatment`, `08-school-classroom`, `09-school-upper-floor`,
`10-office-desks`, `11-office-upper-floor`, `12-security-armoury-lock`,
`13-security-squad-room`, `14-warehouse-racks`, `15-warehouse-workshop` and
`16-end`.

Require `[urban-catalogue] pass=true` (forced dispensary, searched stock, highest
floor, two refused locked entries) and zero GL, KHR and native-audio errors.
`UrbanCatalogueQaSceneTest` plays the same walk headlessly in both modes. For a
failing template, `BuildingPlanDiagnostics.catalogue(seed, rx, rz)` and
`familyMatrix` name it (template, family, variant, parcel id, frontage, size and
storeys) instead of requiring a walk through the city.

For the generated apartment appearance sequence use an isolated directory and a
tag such as `m06_scripted_camera_day`. Change the scene to
`urban_apartments_night` and use a separate night directory/tag for night evidence.
The night scene equips a handheld torch; the held model does not light terrain.
Native lighting/readability must therefore be assessed from actual captures.
Capture `urban_apartments_lit_night` separately to inspect interiors with portable
lights. Retain the raw night views; the supplemental scene does not establish
that abandoned buildings have power or that the player walked the route.

For the required human route, start with:

```powershell
$env:JAVA_HOME = (Resolve-Path build/tools/jdk25/jdk-25.0.4.1+1).Path # or another JDK 25
$env:VEYLON_GENERATOR = 'urban-dev'
$env:VEYLON_SEED = '20260731'
$env:VEYLON_SCENE = 'urban_apartments_manual'
Remove-Item Env:VEYLON_SHOT -ErrorAction SilentlyContinue
Remove-Item Env:VEYLON_FRONTEND -ErrorAction SilentlyContinue
Remove-Item Env:VEYLON_SMOKE -ErrorAction SilentlyContinue
.\gradlew.bat run -PveylonDataDir=build/qa/m06-manual --console=plain
```

Walk from the street through the front door (F), find the kitchen, climb to an
upper room, descend to B1, then return outside. WASD/mouse and normal Survival
auto-step apply; no flight, teleport or scripted movement is used by this scene.
Headless `UrbanApartmentQaSceneTest` walks a continuous generated route through
production movement and door commands with swept-clearance routing;
`GeneratedApartmentTraversalTest` covers all four frontages and both modes.
These are automated physical checks, distinct from the human native gate.

For gameplay HUD work, use `VEYLON_SCENE=hud_showcase` with
`VEYLON_SHOT=3,7,11,16` for bow draw, reload, thrown weapon and mining states.
`hud_showcase_night` and `hud_showcase_fog` reuse the same fixed HUD fixture over
dark and foggy stages. Both Survival and Creative are supported. See
[HUD validation](graphics/HUD_VALIDATION.md) for the capture matrix, scale policy
and headless layout checks.

---

#### Headless building-plan diagnostics

After `classes`, run the read-only diagnostic command with JDK 25:

```powershell
java -cp build/classes/java/main com.veylon.world.urban.BuildingPlanDiagnostics
```

It prints ground-floor diagrams, room functions, stable identities/fingerprints,
bounded accessibility counters and an intentionally invalid stair with its finite
repair. It creates no world, save, GL or OpenAL resources. `BuildingPlanTraversalTest`
stages proposal voxels only in headless worlds and drives real player movement up
and down all four stair orientations in Survival and Creative without jumping.
These are planning checks; generated furnished exploration and native day/night
walks remain milestone 06. Examples are in
[BUILDING_PLANS.md](urban-survival/BUILDING_PLANS.md).

## How to add…

### A block

1. Append a constant to `BlockType` — **never insert or reorder**, ordinals are
   the save format. Set hardness, `requiresTool`, `preferredTool`, `drop`,
   `dropCount`.
2. Give it a material in `MaterialRegistry` (texture, tint, light emission).
3. If it is not a full cube, handle its shape in `ChunkMesher`.
4. If breaking it should have side effects, add a case to the right helper in
   `PlayerBlockActions`: `spillContainerContents` for stored contents,
   `awardDrops` for yields, `applyVandalismConsequences` for reputation.
5. If it is interactable, add a prompt case in `InteractPromptBuilder` and a
   handler case in `WorldInteractions.rightClick` /
   `WorldInteractions.interactWithTargetBlock`.
6. Append its name to `SerializedEnumOrderTest.BLOCK_TYPE_ORDER`, and give it an
   item form (an appended `ItemType` with `places`) or a written reason in
   `CreativePalette.EXCLUDED` — `CreativePaletteTest` requires exactly one.
   `UrbanMaterialsTest` builds the whole material registry headlessly
   (`MaterialRegistry.census()`) and fails on a missing texture or material or on
   more than `MAX_LAYERS` layers; a weathered or damaged look is a material
   variant, not a new id.
7. Urban blocks (appended after `CAGE_BARS`) follow package 04's salvage rule in
   `FixtureActions`: a break yields at most the one item that places the block,
   and is refused when that item, or a container's contents, would not fit. A new
   door or window kind goes into `Fixtures.Kind` with its closed, open and broken
   ids; lock and bar state then comes for free from `World.fixtures`.

### An item

1. Append to `ItemType` (same ordinal rule) and fill in `ItemProps`.
2. Add an icon in `IconAtlas` — the smoke gate fails if any item lacks one.
   A placeable item gets a block projection for free from `MaterialRegistry`.
3. Add a `Recipe` in `CraftingSystem` and pick its `Station`.
4. Equipment also needs an `EquipSlot`; food needs a `FoodGroup`.
5. **Give it a Creative catalog category.** `CreativeCatalog.classify` decides
   membership in one place and `CreativeCatalogTest` fails if any item lands in
   none or in two. A Creative-only block form also belongs in `CreativePalette`,
   whose test fails if a `BlockType` is neither buildable nor given a written
   exclusion reason.

### A new way to hurt the player

Route it through `Player.hurt`, `Player.hurtPhysical` or `Player.addAffliction`
rather than writing `health` directly. Those three are where Creative
invulnerability is enforced, and a direct subtraction bypasses it silently.
`tickNeeds` is the exception that proves the rule: it subtracted health in
several helpers and each one needed its own gate.

### A projectile kind

1. Append to `ProjectileSystem.Kind` and give it a case in `fire`, `step`,
   `onBlockHit` and `onEntityHit`.
2. **Give it a row in the torso-wound table, or say it has none.**
   `ProjectileLethality.torsoWounds` and `torsoHealthFraction` switch over
   `Kind` with no default on purpose, so a new kind does not compile until it
   either states what a torso hit of it costs a person or joins the kinds that
   deal no impact damage and throw. A kind that deals impact damage to an `Npc`
   must also be classified in `ProjectileSystem.step`, which is where
   `HitZone.classify` is called with the entry point of the hit.
3. Decide whether it is a *bomb* for `ExplosionSystem`: a blast that passes
   `lethalToLiving` kills and dismembers every living body inside
   `power × LETHAL_RADIUS_FACTOR`, a Creative player excepted. The fire bomb
   deliberately is not one.

### A new way for AI to notice the player

Ask `Player.isPerceivableByAi()` before the detection, and gate the player-sourced
`WorldNoise.emit` or scent write the same way. It is the single predicate behind
R11, so a new sight cone, hearing check or targeting pass that skips it will make
a Creative player visible again. Consequences that do not depend on someone
noticing — reputation, ownership, theft, vandalism, bounty — stay ungated.

### A new way to consume an item

Decide which side of D4 it is on. Using an item up *for its own effect* (placing,
firing, throwing, eating, drinking, treating, wearing out) checks
`PlayerAbilities.unlimitedItems()` at the call site and skips the `shrink`,
`remove` or `consumeDurability`. *Transforming, trading or moving* it (crafting,
cooking, fuelling, drying, smelting, trade, gifts, quest delivery, crate
transfers, equipping) keeps the Survival rule in both modes and needs no gate.

### A creature

1. Append to `Creature.CreatureType` (health, damage, speed, `predator`,
   `flying`, drops).
2. Build its model in `CreatureModels`.
3. Map its bones in `BodySkeleton.buildCreature` so a dead one ragdolls instead
   of dropping as a single rigid block. An entry names real `ModelPart` names,
   its parent joint, its pivot **in that parent's frame**, its rest vector,
   collision radius and hinge/cone limits — all readable from the model builder.
   Root joint pivots use the model frame. See
   [joint authoring](engineering/RAGDOLL_JOINTS.md) for the complete format.
   A type with no entry falls through to
   `BodySkeleton.rigid` and tumbles as one piece, which is a legitimate choice
   for something tiny rather than an oversight. All rest directions, including
   the skitterwing's sideways wings, are supported.
4. Describe how it comes apart in `FragmentAnatomy`: append a `BodyFamily`
   constant (the build fails until `BodyFamily.of` and the table switch cover the
   new type), list the model's joints parent first with the builder's own pivots
   and boxes, including any part the living animation moves, and declare the
   pieces core first. `AnatomyModels.validate` and `FragmentAnatomyModelTest`
   hold the table to the model and put the pieces back together; a split half
   shorter than `BodyFragmentConstants.MIN_SEPARATE_PIECE` must stay with its
   parent piece. Drawing the pieces needs nothing more: `FragmentModels`
   derives wound depths and culling bounds from the table and the model, and
   `SpeciesFragmentDrawTest` covers every `BodyFamily`. Add the type to the
   row in `SpeciesDismemberQaScene` and capture it (see the lethal combat QA
   section below). The family's ordinal and its pieces' ids are what
   `world.remains` saves, so append the family, pin it and its piece names in
   `SerializedEnumOrderTest`, and only ever append pieces afterwards. A save
   stores each pose joint by joint: changing a family's joint table later makes
   older saves load that family's pieces in its rest pose (in place, without
   their articulation) unless `RemainsSection` learns the old layout.
5. Add spawn rules to `EntityManager` (biome, time of day, density cap).
6. Only touch `CreatureAI` if it needs behaviour the existing states don't
   cover, and put any new decision after the `FirePanic` call at the top of
   `CreatureAI.update`.

Fire needs nothing more: `FlameAnchors` places the new species' flames on the
boxes of its anatomy table, `CombustionSystem` burns any `Creature` at the
creature rates, and `FirePanic` makes it flee (a `flying` type by
`flightReach`, climbing). `AllLivingEndToEndTest`, `BodyCombustionTest` and
`FirePanicTest` build their cases from `CreatureType.values()`, so the new
species is thrown at, blown apart, set alight, made to panic and killed through
the production commands as soon as it exists — a missing anatomy table, flame
anchor or death payment fails there.

### An NPC role

1. Append to `NpcArchetype` (loadout, job, dialogue).
2. Add its daily schedule to `SettledNpcAI`. `FirePanic` runs before the whole
   settlement brain, so a burning resident of the new role flees rather than
   working, fighting or sleeping; nothing is needed for that.
3. Add visual distinction in `NpcModels`. Kit it wears is a box of the humanoid
   model, so its flames, char and blast pieces follow automatically.
4. If it should be placed by settlement generation, update
   `SettlementBuilder`'s resident roster.

`AllLivingEndToEndTest` stands every archetype in a settlement on the side its
kind takes (headhunters, scavengers and captives hostile; settlers friendly or
neutral) and blows it apart and burns it to death through the player's
commands, checking that its death is paid for exactly once.

### A biome

1. Append to `Biome` with surface/subsurface blocks and display name.
2. Add selection to `WorldGenerator` (temperature/moisture thresholds).
3. Add its vegetation pass and creature spawn table.
4. Check `SettlementBuilder` adapts — layouts are biome-aware.

### A settlement type

1. Append to `SettlementType`.
2. Add placement rules to `SettlementPlanner` (`plan(seed, gen, rx, rz)` must
   stay a pure function of its arguments — see the invariant below).
3. Add a layout to `SettlementBuilder`.
4. Add its resident roster and defensive behaviour to `SettlementManager`.

### An affliction

1. Append to `Affliction` with display name.
2. Add its damage rate to `PlayerConstants` and a case in
   `Player.tickAfflictions`.
3. Add a cure path in `PlayerTreatmentSystem` and the item that applies it.

### A world event

1. Append to `EventSystem.EventType`.
2. Add a roll bound and a duration to `EventConstants`. The bounds are
   cumulative and tested in ascending order, so inserting one means shifting
   every bound after it — only the gaps between them are meaningful.
3. Add an `EventDefinition` to `EventSystem.DEFINITIONS`, in bound order. Keep
   eligibility in its side-effect-free precondition and mutations in its effect;
   a rejected definition deliberately hands the same roll to the next rung.
4. Update the hand-maintained rung table in `EventSystem.slowTick`'s JavaDoc and
   extend `EventRollLadderTest`'s characterization/invariant coverage.
5. Add whatever modifier query other systems need (`growthMul`, `thirstMul`,
   `wolfCapBonus`, …) rather than having them check `isActive` directly.

### A flame that sets bodies alight

Only a real, exposed flame qualifies: light, warmth, an enclosed flame (a
lantern, a furnace), a glowing block or a held item never does (the list and
its reasons are in the
[combat and fire contract](engineering/ALL_LIVING_COMBAT_FIRE_CONTRACT.md) §4).

1. Give the flame a volume in the world: the part of its cell where the flame
   stands, reaching only into open neighbours so it never crosses a wall, a
   floor or the ground (`FireSystem.touchBurningBlock` is the model).
2. Report contacts from the system that owns the flame, in an
   `exposeContacts(Game, Entity, BodySweep)` method called from
   `CombustionSystem.sampleFlames`. Test the swept box with
   `BodySweep.touches` so a body crossing the flame between two fast ticks still
   touches it, and report with `CombustionSystem.expose(e, kind, intensity,
   byPlayer, sourceId, contactX/Y/Z)`. Look only at the cells under the swept
   box or at the flame's own bounded list; never load a chunk, never allocate.
3. Decide its weather: a flame the rain puts out asks
   `FireSystem.isRainedOn` and touches nobody while it is rained on or still
   wet; a sheltered one (a torch, a campfire) has no rule and the rain on the
   body's head puts the body out instead.
4. Pick its `CombustionSource` kind. The declaration order is the dominance
   order when several flames touch a body in one tick; each kind sets the heat
   gain (infinite lights on first contact), the afterburn it grants (at most
   `CombustionConstants.MAX_FUEL_SECONDS`) and its intensity. The enum is not
   saved, so a new kind may go wherever its dominance belongs.
5. Attribute it: `byPlayer` and the bottle's id for a flame the player's
   bottle started (so the player's attack is counted once per bottle),
   environmental with a stable per-cell id otherwise.
6. If people and animals should not walk into it, treat it the way torches and
   campfires are treated (`Entity.keepsOutOfFlames` in `VoxelPhysics`,
   `Pathfinder.passable`, `Steering.moveToward`'s sidestep).
7. Add its cases to `FlameSourceIgnitionTest` (catches the way it should, not
   through a wall or floor, not when out or rained on, overlapping flames burn
   as one), keep `CombustionAllocationTest` at zero, and add its row to the
   contract's §4.1 source table.

### A simulation system

1. Add a class in `simulation/`, with a `reset()` and a constants class beside it.
2. Implement `FastTickSystem`, `MediumTickSystem` or `SlowTickSystem` — pick the
   *slowest* cadence that still feels responsive. A stateful system without a
   scheduler bucket implements the base `SimulationSystem`.
3. Call its tick from the matching `Game` bucket and its `reset()` from
   `Game.newWorld`. Add it to `SimulationSystemContractTest`, including a
   behavior-level assertion for every per-world cache or counter it clears.
4. If it holds a `Random`, keep it on an instance reachable from `Game`, give
   that owner a seed hook and seed it from `WorldBootstrap` with a salt no other
   stream uses. Never put outcome randomness in a static field.
5. If it holds state that must survive a save, add a v3 extension section in
   `SaveSystem` — do not change the base v3 layout.

---

## Common pitfalls

**Reordering an enum breaks every save.** `BlockType`, `ItemType`, `Affliction`,
`Biome`, `SettlementType`, `NpcArchetype`, `CreatureType`, `BodyFamily` and the
creature/NPC state enums are persisted by ordinal, and so is each body family's
fragment piece list. Append only. `SerializedEnumOrderTest` will catch you.

**Bulk block edits with `notify = true` are quadratic.** Each notified
`setBlock` triggers listener work. For anything larger than a few blocks, pass
`notify = false` and fix up heightmaps and lights once at the end.

**Settlement planning must stay order-independent.**
`SettlementPlanner.plan(seed, generator, rx, rz)` must return the same result
regardless of which regions were planned before it. Caching intermediate state
across calls will make worlds diverge depending on the player's walking route.

**Never start a thread.** The renderer, world and entity state have no
synchronization and the GL context is bound to the main thread. Budget the work
across frames instead — see `ensureChunks(..., budget)`.

**Case-insensitive text needs `Locale.ROOT`.** The host may run a Turkish default
locale, where `"IRON".toLowerCase()` is `"ıron"` and the Creative catalog stops
finding iron. Every `toLowerCase`/`toUpperCase` in comparison code passes
`Locale.ROOT`; `CreativeCatalogTest` sets a Turkish default and restores it.

**`World.getBlock` returns `AIR` for unloaded chunks.** That is not "there is
nothing there", it is "nobody has generated it yet". Anything that moves or
places by block lookup — Creative flight in particular — must check that the
chunk column is loaded first, or it will happily fly into ungenerated space.

**Tests must not touch GL.** `Game` is constructible headless, but `renderer`,
`ui` and `audio` methods that allocate GPU or OpenAL resources will fail without
a context. Drive gameplay through the tick methods and the command seams
(`updateBowCommand`, `performPlayerAttack`, `completePlayerBlockBreak`,
`interactWithBlockAt`, …), which exist precisely so tests don't have to
synthesize GLFW input.

**Any new `Random` must be seeded from the world seed.**
`WorldBootstrap` seeds every simulation and AI stream with a distinct salt so
their streams stay independent. Add yours there.
`WorldSeedDeterminismTest` reflects over every `Random` reachable from `Game`
and fails with your field's name if you forget — that test exists because ten
generators were previously missed, which made a settlement test intermittently
fail depending on what ran before it. Presentation-only randomness
(`AudioManager`, `NpcScreen`) is exempt and listed in that test.

**Adding transient player state?** Decide explicitly whether it survives a save.
Reload progress and bow draw deliberately do not.

**A straight split limb draws as one box, so hiding its tip hides nothing.**
`ModelPart.split` halves a box, and while the chain is straight the parent
draws the original whole cuboid — which is what keeps living models
pixel-identical. Hide a forearm and the upper arm still draws the whole arm.
Set `forceSplitDraw` on the parent (as `Animator.isolatePart` does for a piece
of a dismembered body) to make it draw only its own half; `resetPose` clears
it, so nothing leaks into the next thing posed from the shared model.

**Shelter from the rain needs headroom.** `FireSystem.isRainedOn` samples the
sky light of the cell *above* the one being tested, and the top of a column is
always fully lit, so a roof resting directly on a block does not shelter it —
leave a block of air between. This catches out test fixtures more than
gameplay: a tree's own canopy shelters the trunk beneath it because there is
air between the logs and the leaves.

**Wounds and blast records die with the `Npc` that carries them.** Torso wound
counts, the shot id that last wounded a torso and the blast record that marks a
body to be blown apart are transient fields on `Npc`, never saved. A person who
leaves the world and comes back — through a save, a load, or a settlement going
dormant and reactivating — returns at their stored health with no wounds. That
is deliberate, but it means anything you attach to an `Npc` instance is lost at
those three points.

---

## Testing conventions

- The default `test` suite is deterministic and headless; a fixed seed goes in
  the test.
- Wall-clock budgets are tagged `performance`, run only through
  `performanceTest`, and are calibrated for the machine recorded in
  `docs/PERFORMANCE_BENCHMARKS.md`. Do not add them to portable release
  packaging or re-baseline them on an arbitrary CI runner.
- Build an isolated arena rather than relying on generated terrain when the test
  is about mechanics — see `CombatSystemsTest.setUp`, which flattens four chunks
  to stone at y=40.
- Drive production entry points, not reimplementations of them. If a test needs
  a seam that doesn't exist, add the seam to the production class rather than
  making the test reach into private state.
- Name the test after the behaviour it pins, not the method it calls.
- Assertion messages should say what invariant broke.
- A Creative behaviour is pinned twice: the Creative case and its Survival twin
  from the same fixture. `SurvivalCreativeParityTest` and the `Creative*Test`
  classes are built in pairs for exactly that reason — a gate that accidentally
  fires in Survival is the failure mode worth catching.
- The suite is 1523 tests across 143 classes at 0.8.1.

---

## Known rough edges

Tracked honestly so nobody rediscovers them:

- **The event probability table is documented twice.** `EventSystem.DEFINITIONS`
  is executable authority, while the `slowTick` JavaDoc table is maintained by
  hand for readers. `EventRollLadderTest` protects the executable ordering and
  distribution, but cannot prove that the prose table was updated.

- **Performance budgets are single-machine.** `performanceTest` catches large
  simulation/save regressions on the recorded reference PC, not portable timing
  differences. Rendering still requires the fixed-seed OpenGL smoke gate.

- **Cadence interfaces are contracts, not dependency injection.** Systems still
  receive `Game` for cross-system coordination. Introducing a service locator
  or one facade per system would add indirection without narrowing that
  dependency and remains intentionally out of scope.

Audio preferences use `veylon_audio.properties` in the same AppPaths directory as graphics preferences. Use V from the title or pause menu, or the title AUDIO button. `VEYLON_FRONTEND=audio` with `VEYLON_SHOT=2` captures the audio editor without a world; Apply saves and Back cancels live edits.

## Physical rain QA and contributor rules (0.7.1)

Use `VEYLON_SCENE=physical_rain`, `VEYLON_GAME_MODE=creative`, `VEYLON_SEED=711`
and `VEYLON_SHOT=4,9,14,19,24,29,34,39,44,49,54,59` for the 60-second native
sequence. Five-second phases cover ordinary rain, storm, shelter entrance,
elevated roof view, uneven terrain/foliage/water, moving at running speed, rapid
turning, 25% density, full density, clear-to-rain, stopping rain and snow. Captures
are wall-clock scheduled, so particle pixels vary with frame cadence; the stage,
seed and phase inputs are repeatable. Headless tests use fixed time steps.

Run `gradlew test --tests '*PhysicalRainTest'` and the reference-machine
`gradlew performanceTest --tests '*RainPerformanceTest'` during development.
Keep new environmental physics in the packed particle arrays, use world-aware
updates in gameplay/QA, and keep all hot-loop scratch bounded. An impact must
come from loaded voxel traversal, never a predicted heightmap splash. Keep
cosmetic randomness out of simulation RNG, reserve shared particle space, and
update instance-packing tests whenever GPU attributes change. Cached columns
must follow World lifetime rules; never cache voxel contents across edits.

For a close inspection of one causal impact, use `VEYLON_SCENE=rain_impact` with
`VEYLON_SHOT=1,3,5,7`. It recreates the same seeded drop and freezes four physics
snapshots: falling, contact, ballistic scatter, expired. This scene deliberately
uses fixed simulation steps each frame so the short spray can be inspected in
native captures; the ordinary rain sequence exercises live continuous updates.

## Death ragdoll QA and contributor rules (0.7.2)

Use `VEYLON_SCENE=death_ragdoll_showcase`, `VEYLON_SEED=20260918` and
`VEYLON_SHOT=1,2,3,5,8` for the staged meadow: four animals and one settler
killed from known directions on one frame. Shots 1, 2, 3 and 5 freeze the
solver at 0.25, 0.8, 2.0 and 5.0 seconds; shot 8 repeats the last snapshot.
`death_ragdoll_sequence` does the same to a single wolf, side-on and close, for
reading one body frame by frame. Both keep the simulation paused and advance the
solver in fixed steps tied to elapsed wall-clock seconds, the way `rain_impact`
does, because a two-second tumble is too short to inspect at live frame cadence
and a wall-clock screenshot would otherwise land somewhere different every run.

0.7.4 adds close fixtures for the joints, with the same seed and shots:
`death_ragdoll_human` (a close human fall), `death_ragdoll_ledge` (a human at a
one-block shelf), `death_ragdoll_drape` (a fall across the shelf edge onto a
contrasting lower floor) and `death_ragdoll_quadruped` (the sequence wolf from
the side). `ragdoll_living` and `ragdoll_living_species` pin idle, walk and
attack poses at shots 1, 3 and 5, for one settler and a wolf or for every species.
Compare them before and after any model change: a straight split limb must still
draw the original cuboid. [Joint authoring](engineering/RAGDOLL_JOINTS.md)
documents the skeleton format and tuning, and the
[validation record](engineering/RAGDOLL_VALIDATION.md) holds the 0.7.4
before/after captures.

Run `gradlew test --tests '*Ragdoll*' --tests '*DeathRagdoll*' --tests
'*OverloadedDeadFlag*' --tests '*BloodBurst*' --tests '*BodiesSection*' --tests
'*CreativeBody*'` while working on this. On the reference machine,
`gradlew performanceTest --tests '*RagdollAllocationTest'` times a full field of
twelve bodies against its 1 ms budget.

Contributor rules this feature adds:

- **Gate anything that happens on death on `health <= 0`, never on
  `Entity.dead`.** The flag also means "remove me from the world" at seven sites
  where health is untouched — expired traders, fading raiders, stood-down war
  parties, routed survivors. `EntityManager.reallyDied` is the predicate;
  `OverloadedDeadFlagTest` drives the real AI paths that set the flag and fails
  if a despawn starts leaving a body.
- **Consequences fire on the tick of death; only the object left behind waits.**
  Reputation, loot, mission failure and resident bookkeeping that lagged a second
  behind the kill would read as a bug.
- **A body is positioned by its torso and drawn through its own `BodyPose`.**
  Orientation belongs on the draw transform, not the model root, or a roll
  becomes a rotation about world Z instead of the body's spine. Models stay
  shared singletons — never clone one per body.
- **Anything that poses the humanoid must run `Animator.applyAppearance`.**
  `resetPose` makes all 22 archetype accessories visible again, so a body posed
  without it wears a trader's pack, a raider's hood and a leader's mantle at once.
- **Keep the per-frame solver allocation-free.** `RagdollAllocationTest` holds
  the airborne and sustained ground-contact paths at zero with a 4 KB allowance,
  in the default suite rather than the performance one, because what it pins is a
  property of the code.
- **Limits and contacts decide the landing; nothing steers it (0.7.4).** There
  is no rest-pose spring and no roll or pitch target; the only assist is the
  one-time minimum toppling speed at launch. A body that lands wrongly needs a
  better limit, radius or torso box, not a pull toward a preferred pose.
- **A joint needs a real model part.** Split an existing box with
  `ModelPart.split` instead of adding a separate cuboid, list the joint
  parent-first in `BodySkeleton`, and keep
  `DeathRagdollTest.renderedParentChainsReachTheSolvedHandlesForEverySpeciesAndHumans`
  passing so the drawn limb sits where the solver put it.
- **Bump the body section when the joint layout changes, and keep reading old
  versions.** `BodiesSectionTest` keeps a handcrafted version 1 fixture for that.
  Every build fails the whole load on a body-section version newer than its own,
  so a bump is a downgrade limit and belongs in the release notes.

## Lethal combat and molotov QA and contributor rules (0.8.0)

Use `VEYLON_SEED=20260919` for every scene below; each one keeps the simulation
paused and advances the systems it is about in fixed steps tied to elapsed
wall-clock seconds, the way `rain_impact` does, so a shot requested at 1.2 s
shows the same moment on every run.

- `dismember_showcase` and `dismember_wall` (`VEYLON_SHOT=1,3,8`) blow four
  people apart at one second, the second with a stone wall three blocks behind
  them. `dismember_bomb` (`VEYLON_SHOT=2.2,4,9`) throws a real scrap bomb into
  a group of six, one of them outside the lethal radius; it goes off at 2.4 s.
- `dismember_species`, `dismember_species_wall` and `dismember_species_close`
  (`VEYLON_SHOT=0.5,1,3,8`, plus `1.3` in a second run with its own
  `VEYLON_CAPTURE_TAG` for pieces in flight) stand a hare, a bird in flight, a
  gloomstalker, the player's remains, a guard, a wolf, a deer and a thornhorn
  in a row, each in its own pose and heading, and blow them all apart at one
  second. The second scene adds a stone step and a wall behind the row, the
  third views the small end of the row from close by. The living clock is
  pinned, so the 0.5 s and 1 s shots must show the same bodies — the second
  with blood and, where the player's remains appear, nothing else changed.
- `molotov_ground`, `molotov_tree` and `molotov_rain`
  (`VEYLON_SHOT=0.7,1.2,3,7,11`, `1,4,10,20` and `2,3.5,5`) throw a real fire
  bomb onto a dry clearing at dusk, beside a tree that catches, and with rain
  from 3 s that puts the pool out.

Captures are wall-clock scheduled, so a slow first frame can push a shot past
the moment it was aimed at — rerun it, and use `VEYLON_CAPTURE_TAG` when two
shots in one run would round to the same filename (`VEYLON_SHOT` names files by
whole seconds, so 1.2 and 1.6 overwrite each other).

Run `gradlew test --tests '*ProjectileHitZone*' --tests '*BlastLethality*'
--tests '*BodyFragment*' --tests '*Fragment*' --tests '*Dismember*' --tests
'*Molotov*' --tests '*FireWeather*' --tests '*LiquidFire*' --tests
'*CombatFireIntegration*'` while working on any of this.

Contributor rules this feature adds:

- **Give a new projectile kind a row in the torso-wound table**, as "How to
  add…" describes. The table is the only place a person's wound cost lives.
- **Kill bodies through the damage path, never by writing `dead`.**
  `Entity.killBy` hurts for more than the remaining health, so `health <= 0`,
  `lastHitByPlayer` and `EntityManager.reallyDied` all agree and the death
  pipeline treats a head shot or a blast exactly like any other kill.
- **What kills a body decides how it falls.** `dismemberOnDeath` is set only by
  a lethal blast, through `Entity.recordBlastDeath`, and only on a body that
  blast killed; everything else ragdolls. Nothing may set it on a living body,
  and a second blast never overwrites the first one's record.
- **Light fires through `FireSystem.ignite`.** It is what holds every block
  fire — from bottles, blasts, lightning and spread alike — under one
  `MAX_ACTIVE_FIRES`, and what refuses a cell that is already burning.
- **Ask `FireSystem.isRainedOn` before anything catches.** It is the one rain
  predicate, shared by burning blocks, campfires and pools of burning liquid;
  do not write another sky-light test. A body asks the same question of its
  head's cell through `FireSystem.isPrecipitationReaching`.
- **Keep the fragment step allocation-free.** `BodyFragmentAllocationTest` holds
  the airborne and ground-contact paths at zero with a 4 KB allowance, and
  `RuntimeBoundsTest` measures the same step over real blast trajectories with
  the fire and liquid systems at full load.
- **Cap every new pile of debris and prove it.** Pieces and pools each have a
  hard cap with a `RuntimeBoundsTest` case that reaches it exactly and cannot
  pass it, and `RuntimeBudgetSnapshot` reports them (with ragdolls, burning and
  panicking bodies and remains' fires) on the F3 overlay and in the smoke
  report. (0.8.0's remembered (NPC, spill) pairs are gone: each body now
  remembers its last four bottles in a fixed array.)
- **Draw a piece only through `Animator.poseFragment` and the
  `FragmentModels` frames.** `poseFragment` picks the piece's own body's shared
  model, resets whatever the last body left on it, puts on a person's look and
  applies the pose the body died in; `rootFrame`, `pieceFrame` and `cutFrame`
  place it, its wounds and a thin piece's ground contact. Posing a shared model
  any other way leaves a missing leg or a raider's hood on the next body drawn
  from it. `SpeciesFragmentDrawTest` checks a piece's first frame against the
  living body and a live body drawn after a piece against one drawn alone.

## Living bodies in combat and fire: contributor rules

Every living body — each species, every kind of person and the Survival player —
comes apart in a lethal blast, catches fire from real flames and (people and
animals) panics. The rules, numbers and APIs are in the
[combat and fire contract](engineering/ALL_LIVING_COMBAT_FIRE_CONTRACT.md); the
[engineering record](engineering/ALL_LIVING_COMBAT_FIRE.md) summarises them with
the validation. What a change must respect:

- **Burn a body only through `CombustionSystem`.** Flames report contacts
  (`expose`); a bottle breaking on a body lights it (`ignite`). One fire per
  body, the strongest contact of a tick wins, a later contact refreshes it up to
  a cap; never add a second timer, damage a body for standing near a flame, or
  treat light or warmth as a flame.
- **Panic comes first.** `FirePanic.update` is the first decision of
  `NpcAI.update`, `SettledNpcAI.update` and `CreatureAI.update`, after the dead
  guard; a new AI branch goes after it. Panic draws only from
  `EntityManager.nextPanicFloat`, reads neither the player nor the camera, and
  resets a body's plans when it ends. The player never panics: nothing a fire
  does may move the player, change speed or sprint, or turn the camera.
- **A death is paid for once, then becomes one body.** Credit, quests, trust,
  reputation and loot run in `EntityManager`'s death routing before the body is
  chosen; the body is pieces if the death carries a blast record and a ragdoll
  otherwise. Pieces and ragdolls never pay anything; an animal's harvest record
  is the one carcass tied to its torso piece.
- **Leave the world through `EntityManager.depart`.** A body that leaves without
  dying (dormancy, a rescue, a party retired or gone abstract, a trader moving
  on, a despawn) goes through `removeNpc`, `removeNpcs` or the entity tick's
  administrative path, which forget its fire, panic, targets and conversation.
  Never take a body out of `npcs` or `creatures` directly.
- **Fire, panic and remains' flames are transient.** They are not saved; a load,
  a new world, respawn and Creative start without them. A new field on
  `BodyCombustion`, `PanicIntent` or `BurnResidue` must be cleared where they
  are (`clear`, `depart`, the recovery, `WorldBootstrap`) and must stay
  primitive: nothing may hold an entity past a tick.
- **Enumerate, do not list.** Tests that must cover every body build their
  cases from `CreatureType.values()`, `NpcArchetype.values()`,
  `Settlement.Alignment.values()` and `Npc.PartyKind.values()`, as
  `AllLivingEndToEndTest` does.

Run `gradlew test --tests 'com.veylon.AllLivingEndToEndTest' --tests
'*AllLivingBlastDeath*' --tests '*Combustion*' --tests '*FlameSource*'
--tests '*FirePanic*' --tests '*Remains*' --tests '*RuntimeBounds*'` after any
change to them, and `gradlew performanceTest --tests
'com.veylon.AllLivingFullLoadBenchmarkTest'` for their frame cost at full load.

## Burning bodies QA and contributor rules

Use `VEYLON_SEED=20260919`. Every scene sets bodies alight through
`CombustionSystem.ignite` (a fire bomb breaking low on each body's near side)
and steps in fixed 1/60 s steps tied to elapsed seconds; `VEYLON_SHOT` names
files by whole seconds, so pick shots with different whole parts.

- *Held* scenes keep the bodies where they were put (only the fire, remains,
  particles and ambience run; health is raised so nobody dies in the capture):
  `body_fire_row` and `body_fire_row_night` (five NPC families in front, every
  species behind, a bird hovering; alight at 1 s, burnt out at about 7 s;
  shots `0.5,1.3,3,6,8,10`), `body_fire_close` (a guard, a wolf, a deer and a
  bird close to the camera; `1.4,3,6,8`), `body_fire_out` (rain from 2.5 s on
  four bodies in the open, a roof over three, one with its feet in water;
  `2,3.3,4.3,5.5,8`) and `body_fire_blast` (a burning row blown apart at 2 s;
  `1.5,2.3,3.5,5.5,9`).
- *Live* scenes run the whole world step: `body_fire_panic` (a walled yard seen
  from a pillar; `0.5,1.5,2.5,4,6,9`), `body_fire_rain` (the same crowd, a roof
  over half the yard, rain from 1.5 s; `1.2,2.2,3.2,5,8`), `body_fire_ragdoll`
  (the yard's crowd too weak to outlive its fire; `1.3,2.1,3,4.5,7,10`),
  `body_fire_bird` (three birds alight in flight; `0.4,1.2,2.2,3.1,4.1`) and
  `body_fire_player` (the Survival player alight with an axe in hand, a burning
  guard ahead, rain from 5 s; `0.5,1.3,3,5.6,6.8,8`).
- Each prints `[scene] <name> t=…` lines once a second and whenever the number
  of bodies alight changes: bodies alight, residues, ragdolls, pieces,
  particles, what body fire gave off last pass, bodies heard, flames drawn and
  the rain and burnout tallies.

Run `gradlew test --tests '*FlameAnchors*' --tests '*BodyFire*' --tests
'*BodyFlames*' --tests '*Combustion*' --tests '*RuntimeBounds*'` while working
on any of it.

Contributor rules this feature adds:

- **Pose a body only through `BodyPosing`.** The renderer, the attached flames
  and the emitters all pose through it, which is what keeps flames on the pose
  that is drawn; a second posing path would put them on another.
- **Presentation reads the fire; it never writes it.** Anything a look needs
  that the simulation does not keep belongs on `BodyCombustion` or
  `BurnResidue` as a read-only value the simulation maintains (as the out
  memory, flicker number and touch offset are), never as presentation state
  holding entities past a pass.
- **Share the frame, do not queue for it.** `BodyFlames.begin` shares its cap
  over every burning body in range, a piece by its `burnShare`; draw the
  living first. New per-frame or per-pass work goes in fixed arrays, and
  `BodyFlamesTest`, `BodyFireEffectsTest` and `FlameAnchorsTest` hold the
  paths at zero allocation.
- **Nothing inside the camera.** The player's own fire is shown at the edges
  of the view (`post_final.frag`) and round a held item's grip; never emit
  world particles at the player's body.

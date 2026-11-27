# VEYLON 0.9.2 "Clean Slate" — Design

Written by milestone 01 on 2026-11-27 against `main` at `2f78150` (build version 0.9.1, released
the same day as `v0.9.1`). Source line numbers and counts are as of `2f78150`: re-verify one before
relying on it. The commands behind the counts and the measurements are in [PROGRESS.md](PROGRESS.md),
milestone 01. The index of all versions and the owner decisions O1–O9 is
[the roadmap index](../README.md); 0.9.1's own records are in [its DESIGN](../v0.9.1/DESIGN.md).

## 1. Intent

The owner's save policy (O1) has been in force since 0.9.1: only saves written by the current
version must open. Twenty generator versions and four kinds of urban revision existed only so that
worlds of earlier builds kept loading. Under O1 they are dead weight, and they make every later
change to the city cost more. 0.9.2 removes them and leaves two worlds, the wilderness
(generator 3) and the city (generator 20), each generating exactly what 0.9.1 generates. It also
takes the QA tooling out of the shipped game, smooths travel and saving in the city, and gives
testers a playtest kit. 0.9.3 then rebuilds the city on the one city generator this release
leaves (O7, O8), so nothing here improves a layout that 0.9.3 replaces.

## 2. Owner decisions

Only the owner can change these.

| Id | Decision |
| --- | --- |
| O1 | Only saves written by the current version must open; saves of earlier releases and of the development generators 4–19 may be refused, and a refused save is never modified or deleted. The authoritative text is the [save compatibility policy](../../ARCHITECTURE.md#save-compatibility-policy-owner-decision-2026-11-06). |
| O2 | Forgotten defects are closed; anything this release finds and leaves is recorded with its reason (§9). |

The decisions of 2026-11-19 (O6–O9) bind 0.9.2 in three ways: milestone 03 stays strictly
mechanical; 06's look-ahead planner must accept 0.9.3's own kinds of plan work (the basin, the
streets, the building grammar) without being changed; and 07's journal records the district of
each event, so that playtests can compare the old city with the new one.

## 3. Decisions

D-01 to D-05 are the defaults the prompt pack adopted for the owner; the owner may change any of
them. D-06 onward are this release's own.

| Id | Decision | Milestone |
| --- | --- | --- |
| D-01 | **Two world kinds.** The city keeps generator 20's behaviour and the wilderness generator 3's. Generators 1, 2 and 4–19 go, with every layout, building-template, encounter-catalogue and content revision. The stored ids 3 and 20 stay, so nothing is renumbered. | 02–04 |
| D-02 | **Identical output.** Each removal leaves both worlds generating byte for byte what they generated before it, proven by the checks of D-12. Only 04 re-baselines pins, and only as current-version regression pins. | 02–04 |
| D-03 | **The development selector.** `VEYLON_GENERATOR=wilderness` stays and `urban-dev` becomes an alias of the city (D-15). The QA isolation rules stay. | 03 |
| D-04 | **QA tooling.** Tours, showcases and capture scenes leave the shipped game for a source set and launcher of their own. The release smoke (`VEYLON_SMOKE`) stays in the game, because release preparation runs it on the packaged builds (D-16 corrects the pack's reason). | 05 |
| D-05 | **Playtest journal.** Local only, plain text, in the data directory, switched on in the options; no network and no personal data. | 07 |
| D-06 | **The audit's actions.** Every item of §5 gets one action. *Delete*: only worlds of a retired generator reach it. *Collapse*: the branch new worlds take stays and its condition goes. *Keep*: new worlds need it as it is. A test is *deleted* when its subject is gone, *retargeted* to the city or the wilderness when what it proves still holds there, or *kept*. A collapse keeps the surviving branch's text as close to its current form as it can, so a review sees that nothing else moved. | 01 |
| D-07 | **What a gate collapses to.** Every city predicate (`UrbanWorldState.hasX()`) is true for the one city. `urban != null && urban.hasX()` therefore becomes `urban != null`, and `urban.hasX()` where the world is known to be a city becomes true; the city keeps its branch and the wilderness keeps its own. The wilderness gates `World.hasDeepFrontier` and `World.hasCaveIdentities` are true for generator 3 and false for the city, so they collapse to "not a city", never to true: `World.settlementForRegion`, `World.isBasaltFumarole`, `EntityManager`'s basalt spawn rule and the smoke's fortress approach also run in a city and must keep answering there as they do now. Inside `WorldGenerator`, `deep()` and `caveIdentities()` are reached only after `generate` has returned for a city, so there they collapse to true. | 02, 03 |
| D-08 | **Revision numbers that are hash inputs keep their values.** Many revision constants select nothing: they are the second argument of `UrbanHash` and so decide what the city is (§5.6). The street lattice hashes with layout 2; districts, overlays and the threat budget with 9; transit and routes with 10; ecology with 11; survivor sites with 12; compounds with 13; wrecks and salvage with 14; the relay with 16; building variants and basements with template 2; the civic and district catalogues with 5 and 6; loot with content 4; and two encounter rolls with the literals 1 and 2. 03 keeps every one of these numbers, renamed for what it is, a fixed hash domain of the one city, and never again as a revision. Changing one would regenerate every city. | 03 |
| D-09 | **Plan data that names a template stays.** `BuildingPlan.templateRevision` (4 for walk-ups and the starter block, 5 for commercial and civic families, 7 for buildings planned with routes and for transit stations) and the template names are hashed by `BuildingPlanDiagnostics.fingerprint`, which the plan pins and the epoch guard read, and `AccessibilityValidator` branches on them. In the one city they tell kinds of plan apart, not versions. 03 keeps their values; renaming the field is fine as long as every fingerprint stays equal. | 03 |
| D-10 | **Save bytes that name a revision stay.** A city save writes layout 17 and content 4 in `urban.meta`, content 4 in `urban.containers`, codec 2 and catalogue 6 in `urban.population`, and version 2 of `urban.refuges`. They stay in the file as constants, and the readers accept only those values. Removing a field would change the save layout, which D-12 forbids in 02–04. Stored enums keep every constant. | 03 |
| D-11 | **The epoch.** `SaveEpochGuardTest` digests, inside both of its generation inputs, the generators a load accepts and the layout, template and content revision of each city (`loadableGenerators`), and epoch 1's digest has shipped. Retiring generators 1 and 2 in 02 therefore changes a released input, and under 0.9.1's D-17 that needs a new epoch. **Default:** 02 raises `SaveHeader.EPOCH` to 2 and records epoch 2's inputs; 03 and 04 re-record only what D-12 lets change; 0.9.2 then refuses 0.9.1's saves as older, which O1 allows, and 08 says so in the release notes. **The alternative is the owner's** (Q-01): no run of the shipped 0.9.1 can make a world of any generator but 3 or 20 (§5.1), so 0.9.2 would still open every save a 0.9.1 player can have. Keeping epoch 1 needs the guard to accept loadable generators that are a subset of epoch 1's recorded line containing both world kinds, instead of equal to it. That weakens a released check, so it is not done without the owner. | 02, 08 |
| D-12 | **What proves identity at each step.** (1) The guard: 02 moves the loadable line into an input of its own (`loadable`), so that `generation.city` and `generation.wilderness` digest only what the two generators make. From then on they must equal `6eb9e20cc2c825216b3daede0990e341857f3028599bc99954a5fb34a5338da8` and `0d671ebdb5a76c49210e4e89f1133886612cee2134f138153560d810a7f76eb0`, which 0.9.1's tree gives for the same text without that line, and `enums`, `layout.city` and `layout.wilderness` must keep their recorded values (`b2c78dd0…`, `d92581c6…`, `2aa9698e…`; the epoch's bytes are masked). Only `loadable` and the epoch may change in 02–04. (2) `WorldGeneratorFingerprintTest`'s generator-3 pins (terrain and column queries) and the generator-20 pins of D-13. (3) `ChunkMesherFingerprintTest`, which meshes generator 20. (4) The determinism and chunk-order tests of both worlds (`WorldSeedDeterminismTest`, the urban generation tests' chunk-order cases, `DeepCaveGenTest`'s order cases once retargeted). A step that moves any of them is a bug, not a simplification. | 02–04 |
| D-13 | **Direct generator-20 pins come first.** Generator 20 has five pins of its own today (terrain, wrecks, wreck chunks, relay, relay chunk), each checked against arrays recorded for generators 14–19. Its building plans, routes, ecology plans, compounds and compound chunks equal arrays that only generators 13, 14 and 16 assert. Its street plans, furnished buildings, districts, encounter anchors and survivor sites match no recorded array, so only the guard's digest covers them. Before 03 deletes any pin of generators 4–19, it adds a generator-20 pin for every fingerprint kind with the values in PROGRESS (measured on `2f78150`; they reproduce the guard's digest), in a commit of its own, so that no deletion can hide a change. | 03 |
| D-14 | **Tests that used an old city as a convenient world.** The test arenas build old cities: `SurvivorWorldArena` generator 15, `CompoundWorldArena` 16, `RefugeWorldArena` 17, `UtilityWorldArena` 18, `EcologyWorldArena` 14, `UrbanEncounterArena` and `UrbanSliceRun` 10, `UrbanLootFixture` 8 and `UrbanFixtureArena` 5. 71 test files use them, 35 of them without naming an old generator themselves. 03 moves one arena at a time to the city and fixes what each move shows. Generator 20 adds to each older city what came after it: compounds from generator 16, wrecks from 17, the relay mast from 19, and the economy's loot (content 4) and catalogue six's quieter starter block from 20. A test that read a position, a roll or an encounter of an older city therefore needs its expectation re-derived from the city, never loosened. A test whose subject is the difference between two generators is deleted. | 03 |
| D-15 | **`urban-dev`.** It stays as an alias of the city, with its isolation rule, because the QA commands recorded in `docs/DEVELOPING.md`, `docs/graphics/HUD_VALIDATION.md` and the urban records name it; DEVELOPING's new examples stop using it. `UrbanRevision.DEVELOPMENT_GENERATOR` goes. | 03 |
| D-16 | **The release smoke.** The pack says that the release workflow runs the smoke on packaged builds. It does not: `.github/workflows/release-build.yml` builds, tests and packages, then checks the archive's contents. The release milestones run the smoke by hand on the fat JAR and the unpacked app (0.9.1 milestone 09), and D-04 holds for that reason. Besides `AutomatedRunDriver`'s smoke phases, the smoke needs `QaHarness`'s smoke report, fortress approach, render statistics and settings overrides, `CreativeQaScenes`' body sampling, smoke flight and save and load checks, `SaveSlots`, and the window's GL error counters (§7). | 05 |
| D-17 | **A QA source set in the same package.** The project has no `module-info.java` and the game runs from the class path, so a `qa` source set can keep the package `com.veylon` and reach `Game`'s package-private members without new public API. The production hooks into QA objects are six calls on `AutomatedRunDriver` and seven on `QaHarness` in `Game`, and four in `FrontendController` (§7); 05 puts them behind one QA provider that the shipped game finds only when it is present. The test source set must see the `qa` source set, because 20 ordinary test classes use QA classes as fixtures (`SaveAuditQaScene.durable` among them). `OrchestratorSizeTest` reads `src/main/java`, so `QaHarness`'s budget moves with it. | 05 |
| D-18 | **The before-figures for 06.** Measured on `2f78150` on the owner's host with nothing else running: `cityPerformanceTest` twice and the native `urban_performance` tour once in Survival, with the commands in PROGRESS. 06 compares against them with the same commands on the same host. | 01, 06 |
| D-19 | **Revisions to this pack.** 01 revised the pack's prompts where they no longer fit; §10 lists each change and why. | 01 |

## 4. What 0.9.2 does not include

- 0.9.3 and 0.9.4: the rebuilt city, its countryside and natural land, its landmarks and towns,
  and the canon's names in player-facing text (O6–O8). 0.9.2 keeps the working names "The
  abandoned city" and "The wilderness" (0.9.1 D-26).
- Any new feature or better layout for either world (D-02; the rule of O6–O9).
- 0.10.0: the infection (O4). 0.11.0: graphics and the longer view (O5); 06's planner prepares for
  it but does not raise the render distance.
- Balance retuning: 07 gathers the data that 0.10.0's balance pass needs.
- Merging, tagging, pushing and publishing, which need the owner. Only 08 bumps the version and
  writes the release notes.

## 5. Retirement inventory

Paths are under `src/main/java/com/veylon/` unless another root is named; `UrbanWorldState` and
`UrbanRevision` are in `world/urban/`. "Refs" counts occurrences of the identifier as a whole word.

### 5.1 Generators

| Id | Constant | What it is | Who makes one in 0.9.1 | Refs, main / test | Action |
| --- | --- | --- | --- | --- | --- |
| 1 | `GEN_LEGACY` | The terrain of the releases before 0.3.0; v2 saves loaded as it until 0.9.1 milestone 03 | Tests only | 3 in 1 file / 17 in 12 files | Delete (02) |
| 2 | `GEN_DEEP` | The pre-identity 0.3.0 development generator | Tests only | 3 in 1 / 22 in 7 | Delete (02) |
| 3 | `GEN_CAVE_IDENTITIES` | The wilderness: the generator of 0.3.0 to 0.8.1 | NEW FRONTIER, `VEYLON_GENERATOR=wilderness`, the wilderness scenes | 5 in 1 / 223 in 118 | Keep, stored id 3 |
| 4–19 | `GEN_URBAN` … `GEN_URBAN_MISSIONS` | Urban development layouts 1–16 | Tests only (production: `world/urban/BuildingPlanDiagnostics.java:205` builds a generator-6 `UrbanWorldState`, never a world, for a catalogue report) | 4–6 each in 2–3 / 13–47 each in 9–27; 103 test files in all | Delete (03) |
| 20 | `GEN_URBAN_ECONOMY` | The city, layout 17 | NEW FRONTIER's default, `urban-dev`, the city scenes | 8 in 2 / 52 in 24 | Keep, stored id 20 |
| — | `DEFAULT_NEW_WORLD_GENERATOR` (20), `WILDERNESS_NEW_WORLD_GENERATOR` (3), `MAX_SUPPORTED_GENERATOR` (20) | NEW FRONTIER's two worlds and the loader's ceiling | — | 7 in 4, 5 in 4, 1 in 1 / 19 in 10, 30 in 17, 9 in 6 | Keep until 04 replaces them with world kinds |

**No run of the shipped 0.9.1 makes a world of generator 1, 2 or 4–19.** NEW FRONTIER offers 20
and 3 (`FrontendController.java:69-71, 209-212`); `VEYLON_GENERATOR` gives 20 when unset or
`urban-dev` and 3 for `wilderness` (`AutomatedRunDriver.java:171-189`); every scene plays the
session's world (`AutomatedRunDriver.java:199-212`); and a load opens only saves of epoch 1, which
only those worlds write. Only tests build the others.

### 5.2 World's gates

| Gate | True for | Callers in production code | 02 | 03 | 04 |
| --- | --- | --- | --- | --- | --- |
| `isSupportedGenerator(v)` | 1–20 | `World` constructor (`World.java:204`), `WorldBootstrap.newWorld` (`WorldBootstrap.java:63`), `SaveHeader.read` (`save/SaveHeader.java:107`, a refusal reported as damaged), the core reader (`save/SaveSystem.java:508`) | 3 and 4–20 | 3 and 20 | Replaced by the world kind |
| `isUrbanGenerator(v)` | 4–20 | `World` constructor (`:210`), `WorldGenerator.generate` (`world/WorldGenerator.java:186`), `AutomatedRunDriver.java:205`, `FrontendController.java:142, 209, 365`, `FrontendRouteQa.java:611, 1402`, `ui/SlotPickerScreen.java:325` | — | 20 only | Replaced by the world kind |
| `hasDeepFrontier(v)` | 2, 3 | `World.settlementForRegion` (`:272`), `WorldGenerator.deep()` (`:90`), `QaHarness.java:376, 521, 536` | Collapse (D-07): "not a city" in `World` and `QaHarness`, true in `WorldGenerator`; the method goes | — | — |
| `hasCaveIdentities(v)` | 3 | `World.isBasaltFumarole` (`:950`), `WorldGenerator.caveIdentities()` (`:94`), `entity/EntityManager.java:575` | Collapse as above; the method goes | — | — |

### 5.3 Generator-dependent branches outside the city state

| Where | Branch | Action |
| --- | --- | --- |
| `WorldGenerator.generate` (`:186-195`) | The city's early return; its climate is the district's (`world.biomeAt`) from layout 9 on, the noise climate's before | 03 keeps `world.biomeAt` only |
| `WorldGenerator` (`:197-257`) | `deep` and `identities` select basalt by depth for generator 2 (`:213-215`), `carveCaves` for generator 1 (`:238-242`), and the underground POIs, cave mouths and settlements for 2 and 3 (`:253-257`) | 02 keeps generator 3's branches; `carveCaves` and the depth-only basalt test go. `BASALT_TOP` stays: the sulfur veins use it (`:527`) |
| `WorldGenerator.carveCavesDeep` (`:297-326`) | Zone thresholds for 3, depth thresholds for 2 | 02 keeps the zone thresholds |
| `WorldGenerator` (`:525, 569, 623-640, 698`) | Powder ores, settlement clearance, POI reservations, relic loot: generators 2 and 3 | 02 collapses to true |
| `WorldGenerator.rollPoi` (`:830-848`) | Generator 3 places a stalker nest and a resonant shrine by depth band and draws no random number for their height; generators 1 and 2 draw one | 02 keeps generator 3's branch, so the random stream stays as it is |
| `World.biomeAt` (`:926-935`) | District climate (layout 9 on), the starter's meadow (generators 9–11), the noise climate (4–8) | 03 keeps the district climate for a city |
| `World` (`:137`, `:178-182`) | Comments that still describe v2 saves and the optional extension | 02 corrects them (B19) |
| `WorldBootstrap.newWorld` (`:63-74`) | Support check; a fresh city records `contentFor(generator)` | 03: the city's content is fixed (§5.6) |
| `WorldBootstrap.grantStartingKit` (`:359`, `:382`) and `grantUrbanKit` (`:396`) | Starter kit from layout 6; `developmentNotice` reached only by generators 4–8; the hints of generators 9–18 | 03 keeps the city's kit and `CITY_HINTS`; the notice and the older hints go |
| `UrbanBootstrap` (`:22-30`, `:41-71`, `:78-83`, `:105-108`) | Three development notices; the street spawn of generators 4–8 and its yaw; respawn rules from layouts 6 and 14 | 03 keeps the starter refuge's spawn and the claimed-refuge respawn; the rest goes |
| `entity/EntityManager.java:575-578` | Basalt stalker spawns: by cave zone for 3, by depth for 1 and 2, never in a city | 02 keeps the zone rule for the wilderness and false for a city |
| `QaHarness.java:376, 521, 536` | The smoke's fortress approach applies to 2 and 3; its snapshot names a world "urban" or "legacy" | 02: the approach applies to the wilderness, and "legacy" goes |
| Settlements | Planned only through `World.settlementForRegion` and `WorldGenerator`'s deep path. `SettlementManager.NpcCategory.LEGACY` names the crash camp's people, not a generator | Covered by the gates; `NpcCategory` is kept |
| `FrontendController`, `ui/SlotPickerScreen`, `AutomatedRunDriver`, `FrontendRouteQa`, `SaveHeader`, `save/SaveSystem` | Choose or name the two worlds through the named constants | Kept until 04's world kinds |

### 5.4 The city's predicates

Each predicate is true for its own layout and, through the chain, for every later one, so all
fourteen are true for generator 20. 191 references in production code: 64 inside
`UrbanWorldState`, 127 in 70 other files. 12 references in 4 test files.

| Predicate | True for generators | Refs inside `UrbanWorldState` | Refs elsewhere (files) | Test refs (files) |
| --- | --- | ---: | ---: | ---: |
| `hasCityPlan` | 5–20 | 8 | 3 (2) | 1 (1) |
| `hasGeneratedBuildings` | 7–20 | 3 | 22 (13) | 0 |
| `hasStarter` | 9–20 | 5 | 13 (11) | 0 |
| `hasEncounters` | 10–20 | 2 | 4 (4) | 0 |
| `hasCivicCatalogue` | 11–20 | 3 | 2 (2) | 0 |
| `hasDistricts` | 12–20 | 4 | 8 (8) | 1 (1) |
| `hasUnderground` | 13–20 | 9 | 5 (4) | 2 (2) |
| `hasEcology` | 14–20 | 5 | 4 (4) | 2 (1) |
| `hasSurvivors` | 15–20 | 4 | 10 (7) | 2 (1) |
| `hasStrongholds` | 16–20 | 6 | 9 (8) | 0 |
| `hasRefuges` | 17–20 | 5 | 14 (11) | 0 |
| `hasUtilities` | 18–20 | 2 | 9 (6) | 1 (1) |
| `hasMissions` | 19–20 | 4 | 17 (16) | 1 (1) |
| `hasEconomy` | 20 | 4 | 7 (7) | 2 (1) |

**Action: collapse every one** (D-07), after 03 has made generator 20 the only city a world can be
built with (§6). From then on each is true for every city, so the order is free; working from
`hasEconomy` inward removes one link of the chain per step.

### 5.5 The predicates' call sites

Grouped by the form the collapse takes. Every file outside `UrbanWorldState` that names a
predicate is listed, under each form its lines take; a number is the count of such lines where it
is more than one.

| Form | Files | Collapse |
| --- | --- | --- |
| A city or the wilderness (`urban != null && urban.hasX()`) | `AmbienceSystem`, `AudioSceneState` (2), `CitySoundscape`, `CrateTransactionSystem`, `DayCurveQaScene`, `Dismantling`, `Game` (death card), `PlayerEnvironmentSystem`, `PlayerLifeCycle`, `RefugeSystem`, `SaveSession`, `UtilitySystem`, `ai/InfectedAI`, `entity/Npc`, `entity/Player`, `entity/UrbanEncounterDirector` (2), `gfx/Environment`, `item/CraftingSystem` (5), `settlement/UrbanComms`, `settlement/UrbanSiteDialogue`, `settlement/UrbanSiteDirector` (3), `simulation/EventSystem`, `simulation/ShelterSystem`, `simulation/TemperatureSystem`, `ui/CityMap`, `ui/MapScreen` (2), `ui/PauseMenu`, `world/ChunkMesher`, `world/World` (12), `world/WorldGenerator`, `world/urban/CommsComponents` (2), `RelaySites`, `SalvageTable` (7), `SurvivorSites` (3), `SurvivorStash` (2), `UrbanBuildingStamper`, `UrbanContainers` (4), `UrbanIndoorQueries`, `UrbanPlaces` (6), `UrbanTraces` (2), `UrbanUtilities` (2), `UrbanVisits`, `WorldBootstrap` (2), `UrbanBootstrap` (7) | `urban != null`, or true where the world is known to be a city; the city's branch stays |
| A city's save section (`appliesTo`) | `save/UrbanCompoundsSection`, `UrbanDiscoverySection`, `UrbanFortificationsSection`, `UrbanMissionsSection`, `UrbanUtilitiesSection` (2), `UrbanVisitsSection`, `UrbanRefugesSection` (4), `V3ExtensionSections` | `g.world.urban != null`; §5.7 |
| A QA scene's or tour's precondition (`urban == null \|\| !urban.hasX()` refuses) | `UrbanApartmentQaScene`, `UrbanAtmosphereQaScene`, `UrbanEconomyQaScene`, `UrbanInfectedQaScene`, `UrbanNavigationQaScene`, `UrbanPerformanceQaScene`, `UrbanQaScenes` (2), `UrbanCataloguePlan`, `UrbanSlicePlan`, and the tour plans `UrbanDistrict-`, `UrbanEcology-`, `UrbanMission-`, `UrbanRefuge-`, `UrbanStronghold-`, `UrbanSurvivor-`, `UrbanUnderground-` and `UrbanUtilityTourPlan` | `urban == null` refuses: a scene refuses the wilderness |
| A selector of an older layout's value | `world/urban/EncounterAnchors` (3: the catalogue number), `item/CraftingSystem` (the recipe list), `UrbanContainers` (the content revision), `save/UrbanRefugesSection` (the version) | The city's value as a constant (§5.6, §5.7) |

Borderline cases:

- **`CraftingSystem.recipesFor`** picks one of five nested recipe lists. The city keeps
  `UrbanCrafting.MISSION_RECIPES`, in its present order (the lists are built by appending), and the
  wilderness `RECIPES`; the four smaller city lists go once nothing else reads them.
- **`World.biomeAt`** and **`WorldGenerator.generate`**: the district climate stays (§5.3).
- **`UrbanSiteDirector`** also asks `contentRevisionResolved()`; that guard goes with the content
  lifecycle (§5.6).
- **The development notices and older hints** (`UrbanBootstrap`, `WorldBootstrap`) are text only
  generators 4–18 show: delete.

### 5.6 Revisions

`UrbanRevision` (236 lines) maps a generator to its layout (`layoutRevisionOf`, 1–17), its
building-template revision (`buildingTemplateRevisionOf`, 0–7) and the content revision a new world
records (`contentFor`: 3, or 4 for generator 20), with `requireSupportedContent` (1–4) and
`DEVELOPMENT_GENERATOR`. The encounter catalogue (1–6) is not in `UrbanRevision`:
`EncounterAnchors.catalogue` derives it from the predicates (`world/urban/EncounterAnchors.java:44-49`).

References in production code (test code): `layoutRevision` 75 in 9 files (15 in 11),
`contentRevision` 26 in 8 (17 in 7), `templateRevision` 16 in 6 (7 in 4); `LAYOUT_*` 88;
`BUILDING_TEMPLATE_*` 62; `STARTER_CONTENT`, `ECONOMY_CONTENT`, `CURRENT_CONTENT`,
`MIN_SUPPORTED_CONTENT` 24; `contentFor` 5 (16), `layoutRevisionOf` 3 (19),
`buildingTemplateRevisionOf` 2 (11), `resolveContentRevision` 4 (21), `contentRevisionResolved` 11
(5).

Every revision number plays one or more of four parts, and each part has its own action:

| Part | What it is | Action |
| --- | --- | --- |
| **Selector** | Picks an older layout's behaviour: `layoutRevisionOf`, `buildingTemplateRevisionOf`, `contentFor`, `requireSupportedContent`, `EncounterAnchors.catalogue`, `CityPlanner.plan` (layout 2 or 9), `BuildingPlanner.plan(seed, revision, …)` for revisions 1–3, 5 and 6, `BuildingTemplates.supportsRevision`, `AccessibilityValidator`'s checks for plans of templates 2 and 3 (`:334-335`), `LootTables`' tables of content 1–3 and `UrbanContainers`' content-1 empty markers (`:210-216`), `UrbanStarterSite.supplies` (content 3), `EcologySites.of`'s layout check (`:115`) | Collapse to the current branch, then delete the selector |
| **Hash input** | The second argument of `UrbanHash.mix`, `unit` or `range` (list below) | Keep the number (D-08) |
| **Plan data** | `BuildingPlan.templateRevision` and template names; `DistrictPlan.layoutRevision`, 9 in every current plan | Keep (D-09); `DistrictPlan.layoutRevision` may go once nothing reads it (`WorldFingerprints` does not hash it) |
| **Save bytes** | `urban.meta`, `urban.containers`, `urban.population`, `urban.refuges` | Keep as constants (D-10, §5.7) |

**Hash inputs**, each of which must keep its number (D-08):

| Number | Constant today | Used by |
| ---: | --- | --- |
| 1 | literal | `EncounterAnchors.selected`, `entity/UrbanEncounterDirector` |
| 2 | `LAYOUT_STREET_NETWORK` | `CityPlanner`'s builder (`rev`: lattice, subdivisions, trees; `planDistricts` passes it), `StreetNetwork` |
| 2 | `BUILDING_TEMPLATE_FURNISHED` | `BuildingPlanner` (variant and basement of templates 2–4: `selectionRevision`; `planDistrict`'s basement), `DistrictCatalogue`'s walk-up variant |
| 2 | literal | `EncounterAnchors` (one roll) |
| 4 | `ECONOMY_CONTENT` | `UrbanStarterSite.economySupplies`, `LootTables.roll` (the content argument) |
| 5 | `BUILDING_TEMPLATE_CIVIC` | `CivicCatalogue` (`CivicTemplates` writes the same number into its plans as plan data, D-09) |
| 6 | `BUILDING_TEMPLATE_DISTRICTS` | `DistrictCatalogue` |
| 9 | `LAYOUT_DISTRICTS` | `DistrictMap`, `DistrictOverlays`, `CityPlanner` (district kinds and uses), `EncounterAnchors` (threat budget), `UrbanOverlayStamper` |
| 10 | `LAYOUT_UNDERGROUND` | `RouteCatalogue`, `TransitNetwork`, `TransitStation` |
| 11 | `LAYOUT_ECOLOGY` | `EcologySites`, `EncounterAnchors` (Breachers, roof wildlife) |
| 12 | `LAYOUT_SURVIVORS` | `SurvivorSites`, `SurvivorStash`, `settlement/UrbanSiteDialogue` (trade) |
| 13 | `LAYOUT_STRONGHOLDS` | `CompoundClaims`, `CompoundSites`, `CivicCatalogue` (gate names) |
| 14 | `LAYOUT_REFUGES` | `VehicleSites`, `SalvageTable` |
| 16 | `LAYOUT_MISSIONS` | `RelaySites` |

**The current city composes several revisions.** Its buildings come from
`BuildingPlanner.planRoutes` (template 7), which plans the starter block and the refuge through
`planDistrict` (template 6) and every other parcel on a template-4 or template-5 base. A template-4
walk-up is furnished (`BuildingFurnishing`, template 2), given shaped stairs (`BuildingStairShapes`,
3) and the starter's treatment (`UrbanStarterSite`, 4). Templates 2–7 are therefore live layers of
the one city, not dead revisions: 03 removes only their entry points for other generators and
keeps the layers. Template 1 (`BuildingTemplates.REVISION`, the bare walk-up) is the skeleton the
layers start from and stays as that. Likewise `CityPlanner`'s builder keeps its layout-2 rules,
which plan the residential and commercial regions of `planDistricts`; only the entry points `plan`
and `planStarter` go.

**Content.** The city's content revision is 4 from creation, so the lifecycle of
`resolveContentRevision` and `contentRevisionResolved` (unresolved until `urban.meta` is read)
guards nothing. 03 fixes the content at construction and removes the lifecycle and its 11
references; `urban.meta` still checks the stored value. This is borderline only because it
changes when a load may read the content: before, never until `urban.meta` was read; after,
always the same 4.

### 5.7 Save code that names generators or revisions

| Where | What it names | Action |
| --- | --- | --- |
| `save/SaveHeader.java:21, 106-108` | A header's generator must pass `isSupportedGenerator`, else the save is reported damaged | Keep; 02 and 03 narrow the set. Only a save of the new epoch could name a retired id, so "damaged" stays true |
| `save/SaveSystem.java:502-511` | The core reader checks the generator before it builds the world | Keep |
| `save/UrbanMetaSection.java:30-90` | Writes the layout (17) and the content (4); a load requires the layout of the save's generator and `contentFor` its content | Keep the bytes; check against the constants (D-10) |
| `save/UrbanContainersSection.java:36, 54` | Writes and checks the content revision | Keep the byte, as the constant 4 |
| `save/UrbanPopulationSection.java:22-30, 43-56, 79-81` | Codec 1 for catalogues 1–4, codec 2 for 5 and 6; writes the catalogue | Keep codec 2 and catalogue 6 as written constants; codec 1 goes |
| `save/UrbanRefugesSection.java:17-24, 34-40, 54, 72, 154` | Version 1 for generators 9–16, version 2 from 17 | Keep version 2; version 1 goes |
| `appliesTo` of `UrbanCompoundsSection:45`, `UrbanDiscoverySection:28`, `UrbanFortificationsSection:48`, `UrbanMissionsSection:55`, `UrbanUtilitiesSection:53` (and its `hasMissions` row rule at `:149`), `UrbanVisitsSection:35`, and `UrbanSitesSection` through `UrbanSiteDirector.manages` | Each city section from the generator that introduced it | `g.world.urban != null`: every city writes and requires every city section, as generator 20 does today, so a save's sections are unchanged |
| `save/V3ExtensionSections.java:97-103` | `urban.refuges` from layout 6 | Every city |
| The sections' JavaDoc ("required from generator 16 on", …) | History | Reworded in 03 |

None of these changes a byte a generator-20 or generator-3 save holds; `SaveEpochGuardTest`'s
layout inputs prove it (D-12).

### 5.8 The `urban-dev` selector

`AutomatedRunDriver.configuredGenerator` (`:155-189`) accepts `wilderness` anywhere and `urban-dev`
only with an isolated data directory, and returns `UrbanRevision.DEVELOPMENT_GENERATOR`, which is
generator 20. `sceneGenerator` (`:199-212`) needs isolation for every city scene and the save
audit. Tests: `UrbanDevelopmentSelectorTest`. Documents naming `urban-dev`: `docs/DEVELOPING.md`
(22 lines), `docs/graphics/HUD_VALIDATION.md` and the urban records. Action: D-15.

### 5.9 Tests and fixtures

No save fixture remains on disk: `src/test/resources` holds only the JUnit configuration (0.9.1
milestone 03 removed the historical saves). The fixtures that pin an old generator are the arenas
of D-14. 113 test files name an old generator: 15 name generator 1 or 2, 103 an urban generator
4–19 (5 name both). 71 test files use one of the arenas, some only for its helpers or with
generator 20; together, 148 test files name an old generator or use an arena that builds one.

**Generators 1 and 2 (02).**

| Test | Uses | Action |
| --- | --- | --- |
| `world/WorldGeneratorFingerprintTest` | Pins of 1 and 2 (`olderGeneratorsMatchTheirRecordedFingerprints`, `legacyGeneratorsStillProduceTheirOwnTerrain`) | Delete those two; keep generator 3's |
| `world/DeepCaveGenTest` | 2 (11 refs), 1 | Delete `legacyWorldsKeepLegacyTerrain`; retarget the other seven to 3 (same seed, powder and cave life, lakes, bounded caves, climbable entrances, chunk order, the connector) |
| `settlement/SettlementPlannerTest` | 2, as its world factory | Retarget to 3 |
| `settlement/FortressGameplayIntegrationTest` | 2 beside 3 | Retarget the generator-2 worlds to 3 |
| `entity/EntityEcologyTest` | 2, one world | Retarget to 3 |
| `ai/PathfinderTest`, `entity/PlayerMovementSystemTest`, `entity/PlayerTreatmentSystemTest`, `simulation/EnvironmentalSimulationTest`, `world/RaycasterTest`, `world/WorldCoordinateContractTest` | 1 as a plain world | Retarget to 3. A test that builds its arena in generated terrain clears its own volume, since generator 3 may put a cave, a settlement or a POI there |
| `world/urban/UrbanRevisionTest`, `world/UrbanGeneratorDispatchTest` | 1 and 2 as "not a city", and the gate tables | Rewrite for 3, 20 and the refused ids |
| `UrbanIndoorEnvironmentTest` | 1–6 in one loop | Delete `historicalGeneratorsKeepTheirOriginalGlassAndRoofDepthQueries` in 03 (it also covers 4–6) |
| `save/UrbanRefugesSectionTest` | 1 as the world without a city | Retarget to 3 |

**Urban generators 4–19 (03).** Three groups.

*Delete: the subject is an older generator or the difference between two.*
`WorldGeneratorFingerprintTest` (every pin of 4–19, once D-13's pins exist);
`ShapedStairGenerationTest.newCatalogueChangesOnlyStairCells…`;
`BuildingPlannerTest.revisionOneGeometryAndAllReservationMetadataMatchTheirRecordedFingerprints`;
`BuildingFurnishingTest.frozenRevisionTwoPinsGeometry…`;
`UrbanStarterGenerationTest.starterRulesLeaveOtherRegionsAndHistoricalCityPlansUnchanged`;
`UrbanCivicGenerationTest.theStarterSliceAndEveryWalkUpKeepGeneratorTenBlocks…`;
`UrbanEcologyGenerationTest.…AndGeneratorThirteenHasNoGrowth` (its generator-13 half);
`UrbanStreetNetworkGenerationTest.theDatumGeneratorKeepsItsLayoutAndHasNoPlan`;
`VehicleSitesTest.generatorSixteenIsTheSameCityWithoutTheWrecks`;
`CompoundSitesTest.earlierGeneratorsPlanNoCompoundAndKeepTheirSites`;
`SurvivorSitesTest.earlierGeneratorsHaveNoSurvivorSites`; `RelaySitesTest.earlierGeneratorsHaveNoRelay`;
`EcologySitesTest.theEcologyPlanIsPureAndEarlierCataloguesNeverKnowItsIds` (its earlier-catalogue
half); `RoofOpeningFallbackTest`'s loop over generators 13–19 (generator 20 stays);
`UrbanSliceRouteTest`'s nine `theSliceLoopStillCompletesOn…` tests for generators 11–19 (the
economy city's stays); `UrbanEconomyEncounterTest.everywhereButTheStarterBlockCatalogueSixIsCatalogueFive`
(re-expressed as generator-20 facts if not already pinned); `UrbanCivicSaveTest.eachEncounterGeneratorReadsOnlyItsOwnFrozenCatalogue`;
`UrbanDistrictSaveTest.generatorTwelveReadsOnlyEncounterCatalogueThree`;
`UrbanEcologySaveTest.earlierCataloguesKeepVersionOneAndRejectVersionTwo` and `.aVersionOneRowNeverNamesANewSpecies`;
`UrbanPopulationSaveTest.oldWorldsRemainEncounterFreeAndAbsenceOfNewStateIsNotAnUpgrade`;
`UrbanMetaSectionTest`'s cases about layouts 1–3 and the template selection of generator 6 (its
round trips through the production bootstrap move to the city);
`UrbanMissionsTest.earlierCitiesGiveNoMissions`;
`UrbanBroadcastTest.earlierCitiesCannotWireARadio`; `UrbanCommsTest.earlierCitiesHaveNoRadioToAskAbout`
and the earlier-city half of `aStrongholdKeepsAModuleInARestrictedStashAndEarlierCitiesKeepNoParts`;
`UrbanCompoundPeopleTest.earlierGeneratorsKeepTheirInfectedOutOfFightsWithTheLiving`;
`UrbanDiscoveryTest.earlierWorldsNameNoRoutePlace`; `UrbanTracesTest.citiesBeforeTheSurvivorSitesHaveNoMarks`;
the earlier-city halves of `UrbanSurvivorCluesTest.steppingIntoACamp…` and
`UrbanVisitsTest.anUnloadedColumnAndAnEarlierCityRecordNothing`;
`UrbanPowerTest.fuelComesOnlyFromTheTankOfACarThatDidNotBurnUnderGeneratorEighteen`;
`UrbanRefugeClaimsTest.creativeClaimsAlikeAndGeneratorSixteenHasNoClaims` (its generator-16 half);
`UrbanSalvageTest.generatorSixteenStillCarriesFurnitureAwayWholeAndKeepsItsRecipes`;
`item/MembraneSalveTest.earlierUrbanWorldsKeepTheirFifteenRecipesWithoutIt`;
`UrbanIndoorEnvironmentTest.historicalGenerators…`.

*Rewrite: the fourteen QA scene tests named `theTourNeeds…`, `theSliceNeeds…` or
`theWalkNeeds…`* (`UrbanAtmosphere-`, `UrbanCatalogue-`, `UrbanDistrict-`, `UrbanEcology-`,
`UrbanEconomy-`, `UrbanMission-`, `UrbanOnboarding-`, `UrbanPerformance-`, `UrbanRefuge-`,
`UrbanSlice-`, `UrbanStronghold-`, `UrbanSurvivor-`, `UrbanUnderground-` and
`UrbanUtilityQaSceneTest`) prove that a scene refuses an older city. They become proofs that it
refuses the wilderness, or go where another test already proves that.
`UrbanCatalogueQaSceneTest.theWalkStillVisitsEveryFamilyOnTheSixDistrictGenerator` moves to the city.
The section tests that reject a city section in an earlier city
(`UrbanFortificationsSectionTest`, `UrbanMissionsSectionTest`, `UrbanUtilitiesSectionTest`,
`UrbanVisitsSectionTest`: `theSectionInAnEarlierWorldIsRejected` and `anEarlierCityRefusesTheSection…`)
and `CityMapTest.earlierWorldsKeepTheirMap…` and `UrbanDistrictPresenceTest.otherWorldsKeepTheirBiomeLineAndNoDistrict`
prove the same rule in the wilderness. The required-section tests named after a generator
(`aGeneratorFifteenSaveWithoutItsSites…` to `aGeneratorNineteenSaveWithoutItsMissions…`) stay, on
the city.

*Retarget to the city: everything else*, through the arenas (D-14) or by changing the generator a
test builds: among them `GameLoopIntegrationTest` (4, 13), `WorldSeedDeterminismTest` (4–7),
`qa/RuntimeBoundsTest` (5, 6, 13–17, 19), `simulation/UrbanSurfaceSpawnGateTest` (4–6),
`world/FixturesTest` and `ai/IndoorNavigationTest` (5, 10, 11), `StairPlacementSaveTest`,
`save/GeneratedApartmentStateTest`, `save/GeneratedShapedStairStateTest`,
`entity/GeneratedApartmentTraversalTest`, `entity/BuildingPlanTraversalTest`,
`entity/ShapedStairBodyTest`, `combat/ShapedStairProjectileTest`, `world/StairGeometryTest`,
`world/UrbanApartmentGenerationTest`, `UrbanCivicGenerationTest`, `UrbanDistrictGenerationTest`,
`UrbanUndergroundGenerationTest`, `UrbanEcologyGenerationTest` and `UrbanStreetNetworkGenerationTest`
(their properties: entry and exit proof, chunk order, idempotent stamping, water), the urban site,
compound, wreck, relay and ecology plan tests, the save round trips of the city sections, and the
gameplay tests of refuges, salvage, utilities, power, missions, comms, visits, maps, acoustics and
the economy. `CitySoundscapeTest` (7, 19), `UrbanEconomyCensusTest`, `UrbanEconomyCombatTest`,
`UrbanEconomySalvageTest`, `UrbanEconomyTradeTest` and `UrbanFirstSessionHintsTest` keep their
generator-20 side and lose the older one.

A file can fall in more than one group; its methods decide. If a retargeted test shows a
difference that is not one of D-14's, it has found a behaviour that differs between generator 20
and the older city, and 03 records it before deciding.

### 5.10 Documents

Lines naming a generator other than 3 or 20, a layout, template or catalogue number, a content
revision or `urban-dev`: `docs/ARCHITECTURE.md` 65, `docs/DEVELOPING.md` 33, `README.md` 1,
`docs/urban-survival/DESIGN.md` 51 and `docs/urban-survival/DECISIONS.md` 88 (history: closing notes only, 03).
`CHANGELOG.md`, `docs/PERFORMANCE_BENCHMARKS.md` and the three skills name none.

## 6. The removal plan

Each step builds, passes the full portable build and keeps D-12's checks equal. Commit boundaries
follow [the shared workflow](../../COMMIT_WORKFLOW.md); a step may be several commits.

**02, the wilderness.**
1. The guard (D-11, D-12): split the loadable line into its own `loadable` input and, under the
   default, raise `SaveHeader.EPOCH` to 2 and record epoch 2's inputs. `generation.*`, `enums` and
   `layout.*` must come out at the values of D-12. Two tests name epoch 1 as the build's:
   `SaveEpochRefusalTest` asserts it (`:65`) and `ui/SlotPickerScreenTest`'s labels read "save
   epoch 1" (`:158-160`); they move with the epoch. The other refusal tests derive "another epoch"
   from `SaveHeader.EPOCH` and keep proving the same rule.
2. Retarget the generator-1 and generator-2 tests to 3 and delete those whose subject is gone
   (§5.9), while both generators still exist, so a retargeted test is proven on generator 3 before
   anything changes.
3. Collapse `hasDeepFrontier` and `hasCaveIdentities` (D-07) and delete generator 1's and 2's
   branches in `WorldGenerator`, `World`, `EntityManager` and `QaHarness` (§5.3).
4. Remove `GEN_LEGACY` and `GEN_DEEP`; `isSupportedGenerator` accepts 3 and 4–20. Correct B19.
   Re-record `loadable`.
5. Documents (02's prompt, step 4).

**03, the city.**
1. Add the direct generator-20 pins (D-13).
2. Move the arenas to the city one at a time, newest first (`UtilityWorldArena`,
   `RefugeWorldArena`, `CompoundWorldArena`, `SurvivorWorldArena`, `EcologyWorldArena`,
   `UrbanEncounterArena` and `UrbanSliceRun`, `UrbanLootFixture`, `UrbanFixtureArena`), then
   retarget the tests that build old cities themselves, and delete or rewrite the tests of §5.9.
   After this step no test builds a world of generators 4–19 except `UrbanRevisionTest` and
   `UrbanGeneratorDispatchTest`.
3. Make generator 20 the only city: `isUrbanGenerator` and `isSupportedGenerator` accept 20 alone
   among the cities (`loadable` re-recorded), and `BuildingPlanDiagnostics` plans with the city's
   state. From here every predicate is true for every city.
4. Collapse the predicates from `hasEconomy` inward (§5.4, §5.5), deleting each false branch.
5. Collapse the revisions (§5.6): selectors to their current branch; hash inputs renamed as fixed
   domains with their numbers; plan data kept; the content lifecycle removed; save bytes kept as
   constants (§5.7). Then delete `UrbanRevision`'s selector API, `UrbanPlaceholderTerrain`,
   `CityPlanner.plan` and `planStarter`, the older `BuildingPlanner` entry points, the content-1
   to content-3 tables and codec 1 of `urban.population`, and version 1 of `urban.refuges`.
6. Remove `GEN_URBAN` … `GEN_URBAN_MISSIONS` and `DEVELOPMENT_GENERATOR`; `urban-dev` becomes an
   alias of the city (D-15).
7. Documents (03's prompt, step 6) and the closing notes in the urban records.

**04, world kinds and pins.** Introduce the world kind and map it to the stored ids 3 and 20 in
one place; replace the generator constants and `isUrbanGenerator` in production code and tests;
turn the pins into current-version regression pins whose messages say that a generation change
needs the pins re-recorded and the epoch raised; `loadable` names the two kinds. `generation.*`,
`enums` and `layout.*` stay as D-12 fixed them.

## 7. QA tooling inventory (for 05)

50 classes and 18,729 lines in `com.veylon` are QA tooling: 30 `*QaScene` classes, the scene
collections `UrbanQaScenes` and `CreativeQaScenes`, 9 tour plans (`*TourPlan`), `UrbanCataloguePlan`,
`UrbanSlicePlan`, `UrbanPeopleScene`, `UrbanRouteScene`, `FrontendRouteQa` (1,431 lines, no budget),
`QaHarness` (1,425 of its 1,500-line budget), `AutomatedRunDriver` (392), `BenchmarkSite` and
`FixtureShowcase`. No QA code lives in another package: `gfx/ScreenshotUtil` also serves the
player's screenshot key, and `qa/RuntimeBudgetSnapshot` serves the debug overlay and the smoke.

**What the shipped game keeps.**
- The smoke (`VEYLON_SMOKE`): `AutomatedRunDriver`'s smoke phases, isolated smoke slots and gate;
  from `QaHarness`, `resetSmokeRun`, `recordFortressApproach`, `emitSmokeReport`,
  `beginSmokeFortressApproach` and its helpers, `configuredMinimumSmokeFps` (`VEYLON_MIN_FPS`),
  `equipFromInventoryFirst`, `sampleRenderStats` and `configuredSeed`; from `CreativeQaScenes`,
  `sampleBody`, `updateSmokeFlight`, `beforeSave` and `afterLoad`.
- The settings overrides any run reads (`VEYLON_RESOLUTION`, `VEYLON_UI_SCALE`, `VEYLON_VSYNC`,
  `VEYLON_FULLSCREEN`, `VEYLON_SHADOWS`, `VEYLON_AO`: `QaHarness.applyResolutionOverride`),
  `VEYLON_SEED`, `VEYLON_GAME_MODE`, `VEYLON_GENERATOR`, `VEYLON_AUTOSAVE`, `VEYLON_SHOT` and
  `VEYLON_CAPTURE_TAG` captures, `VEYLON_GL_DIAGNOSTICS` (`engine/Window`) and `VEYLON_NO_EFX`
  (`engine/AudioManager`).

**What moves to QA.** `VEYLON_SCENE` (`QaHarness.applyBenchmarkScene` and its staging, showcases
and cycles; every scene and tour class), `VEYLON_FRONTEND` (`FrontendRouteQa`,
`FrontendController.openForQa`, `CreativeQaScenes.openCatalogForQa` and `openWorldControlsForQa`),
`VEYLON_QA_SET_OPTIONS` and the static loading capture (`QaHarness.updateTitleOptionsQa`,
`staticLoadingQa`), `VEYLON_AUDIO_QA`, the save audit, `BenchmarkSite` and `FixtureShowcase`.

**Production seams.** `Game` constructs `QaHarness`, `AutomatedRunDriver` and `CreativeQaScenes`
(`Game.java:210, 223-224`) and calls `automation.configure`, `beginSession`, `elapsed`,
`captureDueScreenshots`, `advanceSmokeRun` and `assertSmokeGatePassed`, and `qa.configuredSeed`,
`applyResolutionOverride`, `updateShowcases`, `updateMiningShowcase`, `sampleRenderStats`,
`staticLoadingQa` and `recordFortressApproach`. `FrontendController` calls
`qa.updateTitleOptionsQa`, `qa.setStaticLoadingQa`, `creativeQa.openCatalogForQa` and
`creativeQa.openWorldControlsForQa`. `UrbanRouteScene` calls `automation.requestCapture`. `Game` is
at 998 of its 1,000 lines, so the provider's hook must not add lines to it.

**Package-private access.** Every QA class is in `com.veylon` and reads `Game`'s package-private
state: the QA classes other than `AutomatedRunDriver` use about ninety of its members, most of all
`player` (479 uses), `world` (431), `entities` (132), `camera` (104), `uiMode` (98), `weather`
(79), `time` (58) and `appState` (55). D-17 keeps the package, so none of it needs to become
public.

**Tests.** 45 test files reference QA classes: the scene and tour tests, and 20 ordinary ones that
use QA classes as fixtures (`SaveAuditQaScene` in `DefaultCityAcceptanceTest`, `NewFrontierSeedTest`,
`ReturnToTitleTest`, `ReturnToTitleRoutingTest` and `SlotPickerRoutingTest`; tour plans in
`UrbanAcousticsTest`, `UrbanCityPerformanceTest`, `UrbanTracesTest` and `DayTemperatureCurveTest`;
`AutomatedRunDriver` in `GameModeModelTest` and `UrbanDevelopmentSelectorTest`; `QaHarness` in
`OrchestratorSizeTest` and `WorldSeedTest`; others in `AmbienceRainTest`, `ChunkRetentionTest`,
`DismemberPlayerSceneTest`, `UrbanFixtureArena`, `UrbanSliceRouteTest`, `UrbanSliceRun` and
`UrbanStarterProgressionTest`).

## 8. Before-figures for 06

Measured on `2f78150` on the owner's host (AMD Ryzen 5 5600X, 12 threads; NVIDIA GeForce RTX 3060
Ti, driver 621.01, OpenGL 3.3.0; Windows 11 Pro; Temurin 25.0.4.1+1); commands and every figure in
PROGRESS, milestone 01.

| Measure | Figure |
| --- | --- |
| Region plan, first touch, by district (two runs) | 1.50–3.07 ms |
| A region's buildings, first touch (64–94 buildings) | 98.5–202.4 ms, 1.4–2.8 ms a building |
| A region's sites and routes, first touch | 0.52–6.70 ms |
| Headless streaming, simulation step max | 80.5–89.2 ms at every pace: one frame, which the performance record puts down to the first slow tick planning the spawn's sites and anchors |
| Headless 8 km flight, step and stream max per kilometre after the first | 16.9–29.7 ms |
| Native fast travel (21.6 blocks a second), simulation step max per minute | 28.1–33.7 ms (frame max 29.8–38.4 ms, p99 2.28–3.91 ms) |
| Native street walk across the region seam, simulation step max | 15.0 ms |
| Native stronghold yard and quarantine growth, simulation step max | 21.5 and 19.9 ms |
| Save through the slot at 1, 4 and 8 km (12.5, 36.1 and 43.8 KB) | 27.9–102.2, 64.2–74.6 and 122.6–129.6 ms |
| Load at 1, 4 and 8 km | 141.9–142.3, 324.9–333.6 and 465.3–478.2 ms |
| Native F5 and F9 after five minutes of fast travel | 189.7 and 821.4 ms |

## 9. Defect ledger

Numbers continue 0.9.1's ledger, so that an id names one defect across releases.

| Id | Defect | Evidence | Owner |
| --- | --- | --- | --- |
| B19 | Comments in `World` still describe save compatibility that 0.9.1 removed: generator 2 "retained for existing v3 saves" (`world/World.java:34`), "legacy (v2) saves always load as `GEN_LEGACY`" (`:137`), and keg attribution kept in "the optional extension" so that "older v3 saves default safely" (`:178-182`); `UrbanWorldState`'s class comment still names `urban.meta`'s "absent default" (`world/urban/UrbanWorldState.java:18`), which 0.9.1 D-18 removed. | Source; 0.9.1 D-16 to D-18. Found by this audit. | 02 (`World`), 03 (`UrbanWorldState`) |

The audit found no behavioural defect. The pack's own errors (D-16, §10) are not defects of the
game.

## 10. Revisions to the pack

The pack in `build/release-roadmap/v0.9.2-clean-slate/` was written on 2026-11-06, before 0.9.1
existed. 01 changed it where it no longer fits (D-19):

| Prompt | Change | Why |
| --- | --- | --- |
| 00 | §1's reason for keeping the smoke is corrected, and a closing section names the verified baseline and what binds the later milestones | D-16; 0.9.1 is complete and released |
| 02 | The guard comes first (split `loadable`, raise the epoch under D-11's default) and the retarget list is 01's | A removal of 1 and 2 fails the released epoch otherwise (D-11) |
| 03 | Direct generator-20 pins first; the arenas' order; hash inputs, plan data and save bytes; the content lifecycle | D-08 to D-10, D-13, D-14 |
| 04 | The epoch was decided in 02; 04 re-records only `loadable` | D-11, D-12 |
| 05 | The smoke's dependencies, the same-package source set, the test class path and the size budget's path | D-16, D-17 |
| 06 | 01's before-figures and their commands | D-18 |
| 08 | The epoch step follows D-11 and Q-01 | D-11 |

## 11. Open owner questions

| Id | Question | Default until answered |
| --- | --- | --- |
| Q-01 | **Should 0.9.1's saves open in 0.9.2?** Retiring generators changes an input the released epoch 1 digests. Either 0.9.2 raises the epoch and refuses 0.9.1's saves as older, or the guard learns that narrowing the accepted generators to those a 0.9.1 player could make keeps the epoch, which weakens a released check (D-11). | Raise the epoch in 02 (D-11). The choice can be reversed until 08 prepares the release. |

Carried from 0.9.1 and still open: its Q-01 to Q-04 (blast torso speed, the self-blast radius, the
iron arrow against the bullet, fire tuning) and the readings it flagged (its D-33, D-41, D-43, D-44
and D-45). 0.9.2 changes none of them; 07's journal gathers data for those that 0.10.0's balance
pass takes up.

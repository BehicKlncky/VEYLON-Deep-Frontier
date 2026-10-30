# Urban Survival — Progress

This is the running record for the 26-package urban-survival roadmap. The pack is the `VEYLON Urban Survival — Agent Prompt Pack`, kept outside the repository, with `00_BASE_KNOWLEDGE.md` and prompts `01`–`26`. This file holds status, owners, gates and handoffs. The design is in [DESIGN.md](DESIGN.md), the decisions in [DECISIONS.md](DECISIONS.md), and the evidence in [VALIDATION.md](VALIDATION.md).

## Current status

| Item | Value |
| --- | --- |
| Last completed package | **21 — Map, HUD and onboarding** (2026-10-30): in a generator-19 city, M opens a city map of the streets around the survivor with the buildings they entered named, home, the sites they found, every objective (rumours as rough areas), a journal read from the saved ledger and a sketch of the storey underfoot; the HUD names the district and the building and floor; container and lock lines say what a press does and costs; NEW FRONTIER, the pause menu and the first session speak of the city. Secrets stay secret: the map knows only what the survivor walked, entered (`urban.visits` v1, new and optional) or found, and two mission markers became rumours until confirmed. The native tour passed at 720p and 1080p, scale 0.75 to 1.5, by day, night and in fog, in both modes. Human-only checks are deferred by D-80. |
| Source revision | 21: `e764168` 20's records → `4ee6b89` visits → `2749358` rumours → `8f0d89b` city map → `d762cdf` interactions → `1df1e21` onboarding text → `dd121c1` onboarding tour; the records follow in their own commit. 20: `ff1edff` 19's gate → `71cf2ca` relay → `6d5a317` communications → `cc874ac` missions → `3888a28` acceptance route → `b8967b8` mission tour; records `e764168`. |
| Default new world | Legacy (`World.DEFAULT_NEW_WORLD_GENERATOR = GEN_CAVE_IDENTITIES`), unchanged until 26 passes. Isolated `VEYLON_GENERATOR=urban-dev` creates generator 19 (`GEN_URBAN_MISSIONS`, layout 16 / template 7 / content 3, encounter catalogue 5). Generators 1–18 stay loadable with their original geometry and encounters. |
| Next package | **22 — Ruined-city visuals and sound, NOT STARTED.** Keep `UrbanSliceRouteTest` (generators 10–19), `UrbanCatalogueQaSceneTest`, `UrbanDistrictQaSceneTest`, `UrbanUndergroundQaSceneTest`, `UrbanEcologyQaSceneTest`, `UrbanSurvivorQaSceneTest`, `UrbanStrongholdQaSceneTest`, `UrbanRefugeQaSceneTest`, `UrbanUtilityQaSceneTest`, `UrbanMissionRouteTest`, `UrbanMissionQaSceneTest`, `UrbanOnboardingQaSceneTest`, `ui.CityMapTest` and `UrbanInteractionPromptsTest` as regression gates. Owner question P-15 (the inverted day/night temperature curve) is open. The consolidated human playtest (D-80) follows 22. |
| Human testing schedule | Owner instruction (2026-10-11, D-80): consolidated human playtesting/listening after milestone 22. Through 22, run automated and scripted native validation and record human-only checks as deferred; do not request interim human walks. |
| Commits | Separate `feat`, `test`, `docs` and `chore` commits on `feature/urban-survival`; see the reconstruction ledger below. `main` stays at `923345f`. Original combined commit retained at `backup/urban-m01-m04-before-split`. No merge, push, tag or publication. |

## Package ledger

The status vocabulary is NOT STARTED, IN PROGRESS, PARTIAL, BLOCKED or COMPLETE. Execution is sequential (pack README). "Depends on" lists the most relevant direct prerequisites. "Code owners" are the existing classes to extend and the proposed new ones (DESIGN §12), with paths relative to `src/main/java/com/veylon/`. A gate is met only with the evidence in [VALIDATION.md §5](VALIDATION.md#5-validation-matrix).

| Id | Package | Depends on | Code owners | Observable gate | Status |
| --- | --- | --- | --- | --- | --- |
| 01 | Design and repository audit | — | `docs/urban-survival/` | The four records exist, are cross-linked and pass the link and path check; the entry point for 02 is recorded | **COMPLETE** |
| 02 | Versioned urban foundation | 01 | `world/World` (`GEN_URBAN`, predicates, default and maximum constants), `world/WorldGenerator` (dispatch), `Game`, `WorldBootstrap`, `FrontendController` (explicit generator path), `save/SaveSystem`, `save/V3ExtensionSections` (`urban.meta`), `util/AppPaths`, `build.gradle` (QA data dir), new `world/urban/UrbanIds`, `UrbanRevision`, `UrbanBootstrap` skeleton | Production bootstrap creates an urban-dev world and its generator and revision round-trip. Legacy dispatch, fingerprints and the new gen-2 pin are unchanged. Old saves without urban state load safely. Unsupported or corrupt revisions fail per policy. Reset over a live world clears foundation state. A tested QA data-dir seam exists. | **COMPLETE** (2026-10-07) |
| 03 | City plans and street network | 02 | New `world/urban/CityPlanner`, `DistrictPlan`, `StreetNetwork`, `ParcelPlan`, `UrbanChunkStamper`; `World` urban queries and the loaded-column query; `EntityManager` and `EventSystem` urban gating (D-28); `UrbanQaScenes` | Across the seed, region and order matrix: roads join at borders, parcels respect reservations, blocks and ids are identical in every chunk order, and no wilderness POI, ore, cave or legacy settlement appears. A walkable starter street network exists. Streetscape captures and a native seam walk. | **COMPLETE** (2026-10-08) |
| 04 | Urban materials and interactions | 03 | `world/BlockType` (appended, budgeted), `gfx/MaterialRegistry`, `gfx/ProceduralTextures`, `engine/AudioManager` footsteps, `WorldInteractions`, `InteractPromptBuilder`, `PlayerBlockActions`, `ai/Pathfinder` door passability, new `FixtureStates` and `world.fixtures` | A fixture shows a player and an NPC opening a door, using a window entry and climbing stairs; breaking a fixture; recognising a searchable object. Collision, raycast, rendering and navigation agree. Fixture state round-trips. Creative and Survival gates hold. Natives show both sides of doors and night readability. | **COMPLETE** (final verification 2026-10-10) |
| 05 | Building plans and accessibility | 03–04 | `world/urban/{BuildingPlanner,BuildingTemplates,BuildingPlan,PlanVolume,AccessibilityValidator,BuildingPlanDiagnostics}`; shared `VoxelAccess`/`VoxelClearance`; generator 6 / layout 3 / template 1 | No unreachable room/container/quest reservation, invalid stairs, overlap or overflow; invalid fixtures rejected; stable shuffled access/eviction; bounded work. Native plan debug optional, furnished exploration is 06. | **COMPLETE** (2026-10-10; focused checks, portable build, graph and scoped local handoff) |
| 06 | Generated apartments and interiors | 04–05 | `UrbanBuildingStamper`, `BuildingFurnishing`, `UrbanIndoorQueries`; `World`/`Chunk` height/light/state hooks; shelter, temperature, ambience/audio; generated QA scenes | Generated apartments and a shop have recognisable rooms, usable stairs, matching windows and exits. Blocks and ids are identical in every order. Persisted breaks survive. Native street-to-basement walks in day and night. | **COMPLETE** (2026-10-11; final build, day/night appearance and full human Survival route passed) |
| 07 | Contextual loot and persistence | 06, 02 | `UrbanContainers`, `LootTables`, `urban.containers`; `CrateTransactionSystem`, `ui/CrateScreen`, `simulation/ItemConditionSystem`, one removal path for hand, blast, fire and salvage | Open, partially loot, leave, reload, revisit, break and replace: no refill or duplication. Abandoned property is not theft; owned property has a consequence. Interrupted search, full-inventory and corruption tests. Native searching while the world runs. | **COMPLETE** (2026-10-11; focused checks, final build and generated native searches passed) |
| 08 | Urban spawn and survival progression | 07 | `UrbanBootstrap`, `UrbanStarterSite`, generator 9 / template 4 / content 3, `urban.refuges`, `item/UrbanCrafting`, atomic transfers, indoor safety/prompts, read-only native QA and production route tests; no enum additions | Six starter seeds reach shelter, food, water, treatment, sleep and a first upgrade from urban sources only. Legacy/Creative/save/build pass. Fresh normal-controls native search/take/return with captures. | **COMPLETE** (2026-10-11) |
| 09 | Infected threats and encounters | 08 | Appended `NpcArchetype.VEILBOUND`, `ai/InfectedAI`, humanoid models/anatomy, `engine/InfectedSounds`, `UrbanEncounterDirector`, `EncounterAnchors`, `urban.population`, `RuntimeBudgetSnapshot` | Infected notice the player fairly, pursue in a fixture, attack through the damage gates, react to fire and die cleanly. Defeated state survives save, load and reactivation. Caps and sanctuary hold. Creative twin. Natives in a street and a ground-floor apartment. | **COMPLETE** (2026-10-12; human-only validation deferred by D-80) |
| 10 | Indoor navigation and perception | 09, 04–06 | `ai/Pathfinder`, `PortalGraph`, `PathBudget`, `NavigationCache`, `IndoorNavigation`; `combat/WorldNoise`; AI and lifecycle guards | Multi-floor pursuit through doors and stairs, losing sight, searching and giving up. Door edits change routes. A shared measured budget. No roof or ground confusion and no void pathing. A native pursuit and escape. | **COMPLETE** (2026-10-12; human-only validation deferred by D-80) |
| 11 | Playable vertical slice gate | 02–10 | Integration: `UrbanQaScenes`, `AutomatedRunDriver` urban route, integration tests | The DESIGN §14 slice holds headless and native with normal controls; save, revisit, no refill and no resurrection; Creative twins. Regression seeds frozen. Memory per explored area recorded (§4.3). | **COMPLETE** (2026-10-13; human-only validation deferred by D-80) |
| 12 | Commercial and civic buildings | 11 | `BuildingTemplates` catalog (6 archetypes), `LootTables` | Every family has connected interiors with reachable containers and exits across the family matrix. Earlier urban layouts and legacy fingerprints are unchanged. | **COMPLETE** (2026-10-16; human-only validation deferred by D-80) |
| 13 | Urban biome differentiation | 12, 03 | New `DistrictRules`, overlays, district-to-climate mapping (D-08) | Six distinct districts change player choices. Seams connect. Basics never depend on rare districts. Overlays keep accessibility. Legacy biome consumers are unchanged. | **COMPLETE** (2026-10-19; human-only validation deferred by D-80) |
| 14 | Basements, transit and rooftop routes | 13, 05–06, 10 | Basement, transit and roof planners; hidden entrances; portals; `urban.discovery` | Street → basement → service passage → street and street → fire escape → roof → exit work without clipping or traps. Underground joins agree across borders. Secrets are discoverable through real clues. | **COMPLETE** (2026-10-21; human-only validation deferred by D-80) |
| 15 | Alien ecology and urban wildlife | 14, 09–10 | Alien kind (appended `BodyFamily`), growth-node state, wildlife anchors | Infected, alien and wildlife behaviour are distinct. The alien has a tell and a counter. Caps, deaths and revisits hold. No edible hostile meat. Creative gates. | **COMPLETE** (2026-10-22; human-only validation deferred by D-80) |
| 16 | Survivor camps and hidden civilians | 15, 06, 14, 10 | Urban site registry over `settlement/Settlement`, `SettlementManager`, `DormantSettlementSimulation` (D-21); `ai/SettledNpcAI`; aid interaction | A generated camp and a secret basement refuge are found through play. Helping or stealing has a consequence. The same people, needs and deaths remain after save and dormancy. | **COMPLETE** (2026-10-22; human-only validation deferred by D-80) |
| 17 | Guarded strongholds and hostile factions | 16 | `SettlementManager` guards, reputation, theft and counterattacks; urban faction ids | Approach, access rules, entry, trade and exploration. Guards react fairly. A hostile site with an alternative route. State persists without duplicate guards or infinite counterattacks. | **COMPLETE** (2026-10-23; human-only validation deferred by D-80) |
| 18 | Safehouses, salvage and fortification | 17, 07, 04 | New `RefugeSystem`, `urban.refuges`; salvage in `PlayerBlockActions`; barricades; `SleepSystem` safety | Claim, store, dismantle, barricade from salvage and sleep. Threats attack or route around defences. Claims, salvage and defences persist. Survivor property has consequences. | **COMPLETE** (2026-10-27; human-only validation deferred by D-80) |
| 19 | Water, power and refuge utilities | 18 | `world/urban/UrbanUtilities` (taps, devices, circuits), `UtilitySystem` (owned by `RefugeSystem`, one medium-tick line in `Game`), `urban.utilities` v1; `World.emitterOn` and `Chunk` light lists for lamps; `SalvageTable` (fuel tanks, devices); `UtilitySounds` | One water utility and one power source and consumer run from urban supplies, conserve resources, persist, follow the pause and dormancy policy and respect Creative. | **COMPLETE** (2026-10-29; human-only validation deferred by D-80) |
| 20 | Urban missions and long-term purpose | 19, 16–17, 02 | `world/urban/{RelaySites,CommsComponents,UrbanMissions}`, `settlement/{UrbanComms,UrbanMissionBoard}`, `CommsObjective` (owned by `RefugeSystem`), `urban.missions` v1, a fourth `UrbanUtilities` device kind, hooks in the storage, aid, guide and purifier paths, `NpcScreen`, `Hud`, `QuestObjectiveView` and the victory flow | An integration route advances C1–C5 without trees or ore. A hidden-refuge mission and a guarded-access mission. Failure alternatives. Rewards exactly once. The legacy beacon is intact. | **COMPLETE** (2026-10-30; human-only validation deferred by D-80) |
| 21 | Map, HUD and onboarding | 20 | `world/urban/UrbanVisits` and `urban.visits` v1; `ui/{CityMap,CityMapView,FloorSketch}` behind `ui/MapScreen`; `ui/{Hud,HudLayout,QuestObjectiveView,CrateScreen,NpcScreen,NewFrontierScreen,PauseMenu}`; `item/ItemNames`; `InteractPromptBuilder`, `CrateTransactionSystem`, `FixtureActions`, `WorldBootstrap`; `settlement/UrbanMissionBoard` targets | A new player finds supplies, floors, the refuge and the next step through ordinary UI. Secrets stay secret. `docs/graphics/HUD_VALIDATION.md` captures. | **COMPLETE** (2026-10-30; human-only validation deferred by D-80) |
| 22 | Ruined-city visuals and sound | 21 | Materials, lighting (D-19), `AmbienceSystem`, `AudioSceneState`, `engine/ReverbPresets`, `engine/AudioManager` | Districts, sites and threats are recognisable in day, night and fog. Props, lights and audio stay bounded. Dry fallback works. A manual listening pass. | NOT STARTED |
| 23 | Survival economy and extended play | 22 | Loot tables, recipes, trade stock, encounter pressure; balance record in DESIGN | One route reaches the objective plus several days without trees or ore. Other seeds have reachability. Conservation holds. Fresh extended play recorded. | NOT STARTED |
| 24 | Dense-city performance and streaming | 23 | `world/World` retention and eviction or a finite city (D-24); plan caches; queues; `docs/PERFORMANCE_BENCHMARKS.md` | Bounded queues and caches. Measured scenes meet calibrated gates. A long-travel CPU, GPU and save-growth policy with no lost state. | NOT STARTED |
| 25 | Integrated save and regression audit | 24 | `save/*`, all `urban.*` sections, fixtures | Historical worlds and earlier urban revisions are preserved. An integrated urban fixture survives every order and revisit. Corruption policy and atomic replacement hold. | NOT STARTED |
| 26 | Default urban rollout and final acceptance | 25, 11, 23, 24 | `DEFAULT_NEW_WORLD_GENERATOR` switch; frontend labels; README and docs | A normal new Survival session starts in the city. The pack README final scenario passes on the default path. Legacy and Creative pass. Fresh evidence and a local milestone commit. No tag or publication. | NOT STARTED |

### Group gates

- **Foundation, 01–02.** Defaults recorded, old generators isolated, and ids, revisions and state ownership pinned. Do not build content on an unstable save contract.
- **Playable slice, 03–11.** A real generated apartment, searchable supplies, a working infected encounter, a reachable refuge and a persisted return trip. Pause expansion if the loop is not enjoyable or accessible. A failed 11 blocks 12.
- **City content, 12–17.** Distinct districts, underground and rooftop routes, alien ecology, camps, hidden civilians and strongholds. Every archetype keeps explorable interiors.
- **Purpose and presentation, 18–22.** A defensible home, utilities, missions, the long-term objective, readable navigation and atmosphere.
- **Hardening and switch, 23–26.** Sustainable scarcity, measured runtime limits, durable saves and full regression evidence, then the default switch.

## Milestone 01 — design and repository audit (2026-10-07)

**Status: COMPLETE** (documentation only).

### Start state

- Branch `main` at `923345f` (`chore(agents): add project guidance and Graphify configuration`). The working tree was clean.
- The pack snapshot was `ff42cd8`. The single intervening commit touches only agent guidance and Graphify configuration (VALIDATION §3).
- The pack was read in full: its `00_BASE_KNOWLEDGE.md`, `README.md`, `MILESTONE_STATUS.md` and prompts `01`–`26`. Two identical copies exist, `C:/Users/behic/Desktop/VEYLON-Urban-Survival-Roadmap/` and the ignored `build/urban-survival-roadmap/`.
- `graphify-out/graph.json` was present (built 2026-10-07 00:36, same source).

### Method

1. Orientation with `graphify query` and `graphify explain`, then focused source reads. The main session verified the anchor facts directly: chunk layout, block-id capacity, generator constants and the load-time version check, save limits and slot path, chunk retention, pathfinder rules, colliders, line budgets, section ids and enum and recipe counts.
2. A first read-only audit workflow (18 areas with adversarial verifiers) produced nothing: every agent stopped on an account session limit. A lean rerun (9 merged areas) completed. Its claims were treated as inputs. A recount disproved one of them (a recipe count of 60; the real count is 64). Facts in VALIDATION §2 carry source citations.
3. The design was written against those facts. Proposed features and verified existing capabilities are labelled separately.

### Commands and results

The exact checks are listed in [VALIDATION.md §7](VALIDATION.md#7-evidence-log). In summary:
- the repository and snapshot checks;
- the source audit;
- a link, anchor, path and line-range check;
- an id cross-reference check;
- a keyword-anchored citation check over all 109 B and H rows;
- targeted numeric re-checks.

All passed after two omissions were fixed. A planned multi-agent review of the design text failed on the account session limit and was not rerun. No Gradle build, test, benchmark, native run or capture was performed. None is claimed.

### Deliverables

| File | Content |
| --- | --- |
| [DESIGN.md](DESIGN.md) | Mandatory requirements R1–R10; adopted defaults; the first-15-minute route; the multi-day loop and the C1–C5 communications goal; vertical datum; planning hierarchy; six districts; bounded starter area; accessibility rules A1–A12; site roles; threat themes; scarcity rules; legacy disposition; initial quantities; seed-matrix criteria; interface map; data inventory |
| [DECISIONS.md](DECISIONS.md) | Decisions D-01–D-31, compatibility policy C-1–C-13, capacity budgets, owner questions P-01–P-14, change procedure |
| [VALIDATION.md](VALIDATION.md) | Baseline B-01–B-76 with citations, snapshot deviations, risk register R-01–R-25 and roof-height sites H-01–H-33, validation matrix for 02–26, seed registry, evidence log |
| `PROGRESS.md` | This ledger and handoff |

### Chosen defaults

These are all in DECISIONS.md:
- the pack's design defaults (D-01, D-06, D-07, D-20, D-25 to D-27);
- generator dispatch through a new explicit version with split default and maximum constants (D-02, D-03);
- the revision policy (D-04);
- the vertical datum at `STREET_Y = 40` with a 4-block storey pitch (D-05);
- 256-block planning regions with 64-block superblocks (D-06);
- the sparse fixture container model (D-12);
- a structural QA data-dir seam (D-23).

### Decisions and compatibility

There are no code or format changes. The compatibility policy C-1–C-13 is binding on every later package. The open owner questions P-01–P-14 have stated defaults.

### Limitations

- The texture-layer count (about 73) is a static count and was not measured. The chunk-memory figures are arithmetic.
- Most citations come from the audit and main-session reads at `923345f`. Line numbers drift as code changes, so later packages re-verify before relying on a citation.
- No historical document was edited. The `docs/ARCHITECTURE.md` `setBlock(..., notify)` wording and the `docs/DEVELOPING.md` test count are recorded as historical deviations (VALIDATION §3), not fixed.
- No product decision was made on the owner's behalf beyond the stated defaults.
- The design text (districts, quantities, route timings) was not independently reviewed by a second agent; that review failed on the account limit. Its numbers are *initial* targets that later gates test.

### Next unmet prerequisite: entry point for 02

Milestone 02 starts here, in this order:

1. Read `00_BASE_KNOWLEDGE.md`, `AGENTS.md`, then DESIGN §4.1, §12 and §13; DECISIONS D-02 to D-05, D-16, D-17, D-22, D-23 and §2; VALIDATION §4. Apply `.agents/skills/veylon-save-compatibility/SKILL.md`. Check `git status` and preserve unrelated work.
2. **Safety net, with no behaviour change.** Retarget the tests that mean "the v3 generator" but reference `World.CURRENT_GENERATOR` to `GEN_CAVE_IDENTITIES` (C-2: `WorldGeneratorFingerprintTest`, `CaveIdentityGenerationTest`, `SaveMigrationTest`, `RuntimeBoundsTest`; `CorruptSaveResilienceTest`'s future-generator case becomes `MAX_SUPPORTED_GENERATOR + 1` in step 3). Add an absolute gen-2 fingerprint (C-1). Run them green on the unchanged generator.
3. Split the constants (D-03): `DEFAULT_NEW_WORLD_GENERATOR = GEN_CAVE_IDENTITIES` and `MAX_SUPPORTED_GENERATOR`. Add `GEN_URBAN` (expected to be 4; confirm it is unused). Convert every monotonic gate to an explicit predicate (B-13 list, D-02). Make `SaveSystem` accept versions up to the maximum.
4. Dispatch `GEN_URBAN` by exact match in `WorldGenerator.generate` to a minimal deterministic placeholder that follows the datum (D-05): solid fill and a flat street at y 40, nothing else. Real planning is 03.
5. Add the explicit new-world path (seed, generator, mode) through `Game`, delegating to the existing 4-argument `WorldBootstrap.newWorld`. Add a documented development or QA selector. Mind `Game.java` at 990 of 1,000 lines (D-16). In `WorldBootstrap`, urban worlds skip `setupCamp` and the camp faction track (D-22), with a provisional street-level spawn on the placeholder.
6. Add `urban.meta` v1 (content revision) through the section envelope with an absent default (D-04, C-5). Add `UrbanIds` with the identity layout and validation (DESIGN §13), and its tests.
7. Add the structural QA data-dir seam and its test (D-23), and document the native QA recipe (`-Dveylon.dataDir`).
8. Validate with the 02 row of VALIDATION §5: focused tests, then `.\gradlew.bat build --console=plain`. Run `graphify update . --no-cluster` once at the end. Write the 02 handoff here and fill the D-02 to D-04 implementation details in DECISIONS. Do not claim playable urban content.

## Milestone 02 — versioned urban foundation (2026-10-07)

**Status: COMPLETE.** No user-visible selector was added, so the gate needs no native evidence (VALIDATION §5). An optional isolated native smoke and capture were run anyway (VALIDATION §7). Urban content is **not** playable: an urban world is the street datum and nothing else.

### Start state

- `main` at `923345f`. The only working-tree change was milestone 01's uncommitted `docs/urban-survival/`.
- Temp cleanup had half-deleted the portable JDK from 2026-09. Temurin `jdk-25.0.4.1+1` was fetched again into this session's scratchpad, and its SHA-256 matched Adoptium's published checksum.

### Implemented behaviour

- **Generator split and explicit gates** (D-32):
  - Added `World.GEN_URBAN = 4`, `DEFAULT_NEW_WORLD_GENERATOR = 3` and `MAX_SUPPORTED_GENERATOR = 4`. `CURRENT_GENERATOR` is gone.
  - Every former `>=` / `<` gate is now an explicit predicate. A truth table per gate keeps generators 1–3 unchanged.
  - Unsupported versions are refused by the loader, by `WorldBootstrap` before it resets anything, and by the `World` constructor.
- **Exact dispatch** to `UrbanPlaceholderTerrain`: stone at y 0–39, a stone-brick street at y 40, air above, and soil moisture from the climate biome. Its output is frozen and fingerprint-pinned. Urban worlds get no caves, ores, decoration, POIs, cave mouths, settlements, fumaroles or stalker depth.
- **Revisions** (D-33, D-34):
  - The layout revision follows from the generator. The content revision is resolved before chunk generation for a fresh world. For a load it stays unreadable until `urban.meta` or its absent default (1) has been read.
  - `urban.meta` v1 holds the version, layout and content. Only urban worlds write it. The rejection rules are recorded in D-34.
- **Identities**: `UrbanIds` packs district, parcel, building, floor, room, fixture and resident ids into frozen 64-bit values, with a strict canonical text form, validation by range and kind, and parent navigation (DESIGN §13).
- **Ownership** (D-38): `World.urban` (`UrbanWorldState`) exists only in urban worlds, so a new world or a load always starts clean. `BoundedPlanCache` is the LRU primitive for derived plans. Nothing was added to `Game` apart from the 4-argument `newWorld`.
- **Bootstrap** (D-37): urban worlds skip the dry-land search and the crash camp, and spawn provisionally on the street at y 41.2. `FactionSystem.campPos` is now cleared for every new world and load. The camp hint becomes a development notice.
- **Development selector and QA isolation** (D-36):
  - `VEYLON_GENERATOR=urban-dev` applies to NEW FRONTIER, automated worlds and `VEYLON_FRONTEND` staging.
  - It refuses to start unless `veylon.dataDir` differs from the player's directory, judged by file identity when both directories exist.
  - Every Gradle `Test` task runs with `build/test-data/<task>`.
  - `run` accepts `-PveylonDataDir`.
- **Documentation**: `docs/ARCHITECTURE.md` (world creation, generator versions, `urban.meta`, compatibility directions, the id policy) and `docs/DEVELOPING.md` (`VEYLON_GENERATOR`, "Isolated QA data").

### Changed paths

- **Production**, under `src/main/java/com/veylon/`:
  - New: `world/urban/{UrbanDatum,UrbanRevision,UrbanIds,UrbanWorldState,UrbanPlaceholderTerrain,BoundedPlanCache}.java`, `UrbanBootstrap.java`, `save/UrbanMetaSection.java`.
  - Edited: `world/World.java`, `world/WorldGenerator.java`, `entity/EntityManager.java`, `QaHarness.java`, `save/SaveSystem.java`, `save/V3ExtensionSections.java`, `Game.java`, `WorldBootstrap.java`, `FrontendController.java`, `AutomatedRunDriver.java`, `util/AppPaths.java`.
- **Build**: `build.gradle`.
- **Tests**, under `src/test/java/com/veylon/`:
  - New: `world/urban/{UrbanIdsTest,UrbanRevisionTest,BoundedPlanCacheTest}`, `world/UrbanGeneratorDispatchTest`, `save/UrbanMetaSectionTest`, `save/SaveSlotIsolationTest`, `UrbanDevelopmentSelectorTest`.
  - Extended: `world/WorldGeneratorFingerprintTest`, `GameLoopIntegrationTest`, `WorldSeedDeterminismTest`, `util/AppPathsTest`.
  - Retargeted (C-2): `world/CaveIdentityGenerationTest`, `save/SaveMigrationTest`, `qa/RuntimeBoundsTest`, `save/CorruptSaveResilienceTest`.
- **Documents**: `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`, and these four records.

### Stable ids, schema and compatibility

- **New section**: `urban.meta` v1. Saves now carry 13 section ids at most, and legacy saves still carry 12.
- **Enums**: none changed. The `UrbanIds.Kind` codes are explicit, never ordinals, and are pinned by `UrbanIdsTest`.
- **Verified by tests on this build**:
  - Legacy saves load with no urban state.
  - Legacy saves written now contain no `urban.meta`.
  - Urban saves round-trip their generator, layout and content.
  - Loads over a live world work in both directions.
  - Every rejection case listed in D-34, plus generators 0 and `MAX + 1`, fails while the live world survives.
  - An absent `urban.meta` gives the content default.
- **Established by reading code, not by running it**:
  - Build 0.8.1 (`923345f`) rejects generator 4 before it replaces the live world: its check is `generatorVersion > CURRENT_GENERATOR (3)`.
  - An older urban-capable build that meets a newer `urban.meta` version fails the whole load, through the existing known-section rule.
  - No older binary was run.

### Commands and results

See [VALIDATION.md §7, milestone 02](VALIDATION.md#milestone-02-2026-10-07). Gradle ran with the scratchpad Temurin 25.0.4.1 on the Windows host.

### Limitations and deferred items

- The placeholder has no city, buildings or interiors (03, 05, 06). Package 03 must allocate a new generator version for its street network (D-35).
- Legacy surface wildlife and surface events still run in urban worlds. They are harmless on the flat placeholder; the native smoke counted 3 creatures. Package 03 gates them (D-28).
- The fresh kit and the Survival intro ("Gather wood…") are still legacy (08). The HUD operations panel still shows the legacy "Camp / Neutral" trust line although an urban world has no camp (08, 21). Respawn uses the provisional spawn (08).
- No consumer reads the content revision or `BoundedPlanCache` yet; the first are 03 and 07. No urban id is persisted yet. The id-validation policy is documented in `docs/ARCHITECTURE.md` and DESIGN §13 for the sections that will store ids.
- The isolation guard decides by file identity only when both directories exist. A not-yet-created alias of a not-yet-created player directory falls back to comparing normalised paths.
- A milestone-02 review workflow (3 reviewers and 1 verifier) returned 3 low-severity findings, two of them the same issue. Both distinct issues were fixed: `VEYLON_FRONTEND` staging now honours the selector, and the guard now compares file identity. A further remark from the first pass was also applied: new tests that mean a legacy world now name generator 3. Every other point the reviewers checked held.

### Next unmet prerequisite: entry point for 03

1. Read `00_BASE_KNOWLEDGE.md`, prompt 03, DESIGN §4.2–4.4, §11 and §12, DECISIONS D-06, D-07, D-09, D-15, D-28, D-35 and D-38, and VALIDATION §4.1 (H-04, H-05, H-08, H-28, H-32) and §6.
2. Allocate the next generator version for the street network (D-35), and decide whether to keep or retire generator 4. Repoint `UrbanRevision.DEVELOPMENT_GENERATOR` and `MAX_SUPPORTED_GENERATOR`, and add its fingerprint pin.
3. Build `CityPlanner` plans on `BoundedPlanCache` (≤ 16 regions) owned by `UrbanWorldState`. Add the first salt class with its distinctness test (D-38). Add the loaded-column query (R-14). Gate surface wildlife and events in urban worlds (D-28).
4. Freeze the urban design seed set in VALIDATION §6.

## Milestone 03 — city plans and street network (2026-10-08)

**Status: COMPLETE.** Urban development worlds (generator 5) are a connected city of districts, streets, courtyards, parks, plazas and reserved parcels. **Buildings do not exist yet:** every parcel is a flat reserved lot (a stone outline around dirt, with plank entrance markers and a ruin-stone stair-core pad) until packages 05 and 06 build on it.

### Start state

- `main` at `923345f`, with milestones 01 and 02 uncommitted. `git status` showed only that work; nothing unrelated was present.
- The scratchpad Temurin `25.0.4.1+1` from 2026-10-07 still ran.

### Implemented behaviour

- **Generator 5** (D-39): `World.GEN_URBAN_STREETS = 5`, layout revision 2, `MAX_SUPPORTED_GENERATOR = 5`; the development selector now builds it. Generator 4 is kept, unchanged and pinned.
- **Pure regional planning** (D-40, D-46): `CityPlanner.plan(seed, layoutRevision, rx, rz)` reads no world state and no other region. Every decision hashes (seed, layout revision, salt, stable coordinates) through `UrbanHash`, with the salts in `UrbanSalts`.
  - Avenues, 12 wide, run on every region edge; each region stamps its half, and the plain or boulevard profile of a segment is a function of the edge and segment, so both neighbours agree.
  - Local streets, 8 wide, keep their position across a whole region column or row, so every street continues through the avenues and the lattice is connected.
  - Sixteen superblocks per region: courtyard housing, shop rows with a through alley, at most one park or plaza.
  - Six district ids with stable codes (`UrbanDistrict`); residential and commercial are planned, region (0, 0) is residential, and the other four wait for package 13.
- **Reservations** (D-41): each parcel (12–32 blocks a side, index `superblock * 8 + slot`, a packed parcel id) has a primary street entrance, a rear entrance onto its courtyard walkway or alley, a 4 × 6 stair-core footprint and a storey envelope. One service parcel per region. Region (0, 0) reserves the starter refuge, a corner shop and the spawn anchor. A fixed claim order resolves overlaps, and every collision is counted.
- **Chunk-local stamping** (D-42): a chunk writes only its own blocks from one region plan; `pendingGen` stays empty. Everything is flush at y 40 over stone fill, with planned trees whose crowns start at y 44. The blocks are a temporary mapping to existing ids; no block id was added.
- **Queries** (D-43): `UrbanWorldState.groundY`, `plannedRoofY`, `surfaceAt`, `parcelAt`, `districtAt`, `spawnColumn`, `regionPlan`; `UrbanDatum.floorContaining`; `World.isColumnLoaded`. None generates a chunk. `surfaceHeight` is unchanged for every world.
- **Spawn** (D-44): the walkway cell outside the reserved refuge's courtyard door, recomputed from the plan on new world and load.
- **D-28 gates** (D-45): no surface wildlife and no migration, trader or meteor events in urban worlds. Legacy odds are unchanged.
- **Bounds and diagnostics** (D-47): at most 16 region plans cached, reported by `RuntimeBudgetSnapshot` (`urbanPlans`). `CityPlanDiagnostics` checks overlaps, parcel rules, routes and seams against the four neighbours, keeping at most 16 messages.
- **QA** (`UrbanQaScenes`): `VEYLON_SCENE=urban_streets`, `urban_intersection` and `urban_seam_walk` (real player movement across the region seam at x 0, with a pass or fail report); see `docs/DEVELOPING.md` "Urban planning QA".

### Changed paths

- **Production**, under `src/main/java/com/veylon/`:
  - New: `world/urban/{UrbanSalts,UrbanHash,UrbanDistrict,UrbanSurface,UrbanSurfaceMaterials,StreetNetwork,ParcelPlan,SuperblockPlan,DistrictPlan,CityPlanner,UrbanChunkStamper,CityPlanDiagnostics}.java`, `UrbanQaScenes.java`.
  - Edited: `world/urban/{UrbanWorldState,UrbanRevision,UrbanDatum}.java`, `UrbanBootstrap.java`, `world/World.java`, `world/WorldGenerator.java`, `entity/EntityManager.java`, `simulation/EventSystem.java`, `qa/RuntimeBudgetSnapshot.java`, `QaHarness.java` (4 lines; 1,492 of 1,500), `AutomatedRunDriver.java`, `WorldBootstrap.java`. `Game.java` is untouched (994).
- **Tests**, under `src/test/java/com/veylon/`:
  - New: `world/urban/{CityPlannerTest,UrbanSaltsTest}`, `world/UrbanStreetNetworkGenerationTest`, `simulation/UrbanSurfaceSpawnGateTest`.
  - Extended: `world/WorldGeneratorFingerprintTest` (generator-5 terrain and plan pins), `world/urban/UrbanRevisionTest`, `world/UrbanGeneratorDispatchTest`, `save/UrbanMetaSectionTest`, `UrbanDevelopmentSelectorTest`, `WorldSeedDeterminismTest`, `qa/RuntimeBoundsTest`.
- **Documents**: `docs/ARCHITECTURE.md` (generator list, world creation step 7, "Urban city plans"), `docs/DEVELOPING.md` (`VEYLON_GENERATOR`, the urban scenes, "Urban planning QA"), and these four records.

### Stable ids, schema and compatibility

- No save section, section version, enum or block id changed. `urban.meta` v1 carries layout 2 for generator 5; a layout that does not match the generator still fails the load.
- Parcel ids are `UrbanIds.parcel(district(rx, rz), superblock * 8 + slot)`, pinned with the rest of the plan by the generator-5 plan fingerprint. No section stores one yet.
- **Verified by tests on this build**: generators 1–4 fingerprints unchanged; a generator-5 world round-trips its generator, layout, content revision, edits, geometry, parcel identity and spawn; a generator-5 save whose `urban.meta` names layout 1 fails without touching the live world; legacy event odds unchanged.
- **Established by reading code, not by running it**: the package-02 build (maximum generator 4) and 0.8.1 (maximum 3) both refuse a generator-5 save as an unsupported generator before replacing the live world. No older binary was run.

### Commands and results

See [VALIDATION.md §7, milestone 03](VALIDATION.md#milestone-03-2026-10-08). In short:
- focused tests, three mutation checks (all caught), native streetscape, intersection and seam-walk runs (`pass=true`) and an urban smoke;
- the portable build before and after the review;
- a 12-agent read-only review. It confirmed one low-severity code defect, a meteor shower restored from an old development save still dropping shards in a city, which is now fixed and tested. Three test-hardening suggestions were applied.

### Limitations and deferred items

- **No buildings.** Parcels, entrances, stair cores and storey envelopes are reservations; package 05 plans buildings on them and package 06 generates them, under a later generator version.
- **Temporary materials.** Streets use existing blocks; footsteps on stone brick, basalt, clay and gravel still sound like grass (B-74). Real urban materials are package 04, and changing street blocks then needs a new generator (D-42).
- **Climate under the city is the legacy biome noise** (D-08). For seed `20260731` the starter region reads Rocky Highlands, −3.5 °C at noon. Package 13 owns the district-to-climate mapping; package 08 must check that the starter area is survivable, or bring the mapping forward with a recorded decision.
- **Planned greenery is still under legacy `PlantSystem` regrowth** (H-24): tall grass and, near planted trees, saplings can appear on lawns and medians over time. Walkways, passages, alleys, streets and lots are not grass, so routes stay clear. Package 13 owns greenery behaviour.
- **The legacy HUD** still shows the camp line and the crash-landing intro (packages 08, 21).
- **Canals and level changes** do not exist yet (no flooded district). The avenue profile is where package 13 adds them so both sides of a seam agree.
- **The seam walk is scripted**: real player movement through the production movement path in a native window, not a manual keyboard walk.
- Memory per explored area is not measured (package 11). A plan costs about 1 ms to build; the urban smoke saw one 115 ms first-frame spike while the spawn area generated.

### Next unmet prerequisite: entry point for 04

1. Read `00_BASE_KNOWLEDGE.md`, prompt 04, DESIGN §5.1 (A1–A12) and §12, DECISIONS D-18, D-31, D-42 and §3 (block budget ≤ 24 ids), and VALIDATION §4.2 (B-11, B-23, B-39, B-73, B-74). Check `git status` and preserve the uncommitted 01–03 work.
2. Decide whether 04 changes any stamped street or parcel block. If it does, allocate generator 6 with layout revision 3 and keep generator 5 pinned (D-04, D-39). If 04 only adds fixture blocks for later buildings, generator 5 stays as it is.
3. Build doors, windows, stairs and fixtures as appended `BlockType` ids within budget, with materials and textures; measure the texture-layer count at boot. Add the `FixtureState` side map and the `urban.fixtures` section with its codec, absence default, reset and corruption tests (D-17, D-18). Prove D-31 (AI stair climbing) on a stair fixture.
4. Use the plan queries (D-43) for any placement, never `surfaceHeight`; use `World.isColumnLoaded` before trusting `getBlock`.

## Milestone 04 — urban materials and interactions (2026-10-08)

**Status: COMPLETE**, with final verification and commit handoff resumed on 2026-10-10 (see the continuation below). Doors, windows, stairs, urban materials and searchable furniture exist and behave in every world. **No generated building uses them yet**: package 05 plans buildings and 06 stamps them. The fixtures are proven on `FixtureShowcase`, a small house that the tests and the QA scenes build beside the spawn.

### Start state

- `main` at `923345f`, with milestones 01 to 03 uncommitted. `git status` showed only that work.
- The scratchpad Temurin `25.0.4.1+1` from 2026-10-07 still ran.

### Implemented behaviour

- **Block ids** (D-54): 21 appended at 57–77 (`ASPHALT` … `DEBRIS`). Weathered and damaged looks are material variants, not ids. Three unpersisted shapes (`LEAF`, `PANE`, `TABLE`). 16 item forms: 14 salvage forms and 2 Creative palette forms.
- **Doors and windows** (D-48, D-50):
  - Closed, open and broken ids per kind. Openings are stacks of up to 3 cells.
  - F opens and closes the whole opening, but never onto a body.
  - A keyed lock holds from both sides: F is a failed entry, with a rattle and a noise event.
  - Crouch and F bars a door or latches a window from the player's side, and only that side can lift it. In Creative it toggles a keyed lock, for staging.
  - A Survival break forces a door (loud, irreversible), shatters a pane (loud; a bare hand is cut through the damage gate), or dismantles an open fixture into its single item. Creative removes the block.
  - An aimed door or window outranks a nearby person for F, and the HUD prompt comes from the same decision.
- **State and persistence** (D-48, D-49):
  - Toggles are fixture state, not block edits. `World.fixtures` is keyed by position and saved in `world.fixtures` v1, only when non-empty.
  - Saved state is applied on load and, for chunks not yet generated, at generation; stale entries are dropped.
  - Forced, shattered and dismantled fixtures are recorded edits.
  - Capped at 32,768 cells on write and read.
- **Residents** (D-52, D-53):
  - `Pathfinder` passes closed unlocked doors at the gate's cost and walls off locked or barred ones and closed glass. An open window over a sill is an ordinary step-up.
  - Residents open doors on their path, and the doors swing shut behind them after 4 s (transient, at most 256 timers, saved shut).
  - In urban worlds residents step up one full block, so they climb stairs by walking (D-31).
- **Stairs**: `STAIRS` is a full-block step with worn safety-orange edge strips (A3).
- **Containers and salvage** (D-51):
  - Shelving, cabinets and lockers are searched through the crate backend.
  - A break that cannot fit its contents or its salvage is refused, never lossy.
  - Salvage is at most the item that places the block.
  - A blast or a fire destroys an urban container's contents with it. The legacy crate is unchanged.
- **Agreement** (D-55): the mesher and the targeting raycast share `FixtureGeometry`, so the crosshair passes an open doorway and hits only the drawn leaf. Collision and navigation read the same ids.
- **Presentation** (D-54, D-56):
  - 31 procedural tiles (`UrbanTextures`); the measured total is 104 of 160 layers.
  - Door, window and stair icons.
  - Seven fixture sounds from their own seed.
  - Footsteps: urban materials, stone brick and basalt now sound like stone.
  - Glass dust no longer shows black specks.
- **QA** (`UrbanFixtureQaScene`, `FixtureShowcase`): `VEYLON_SCENE=urban_fixtures` and `urban_fixtures_night`; see `docs/DEVELOPING.md`.
- **Bounds**: `RuntimeBudgetSnapshot` reports `fixtures={states,residentDoors}` against their caps.

### Changed paths

- **Production**, under `src/main/java/com/veylon/`:
  - New: `world/{Fixtures,FixtureStates,FixtureGeometry}.java`, `FixtureActions.java`, `FixtureShowcase.java`, `UrbanFixtureQaScene.java`, `gfx/UrbanTextures.java`, `engine/UrbanFixtureSounds.java`, `save/FixtureStatesSection.java`.
  - Edited: `world/{BlockType,World,ChunkMesher,Raycaster}.java`, `item/{ItemType,CreativePalette,CreativeCatalog}.java`, `gfx/{MaterialRegistry,ProceduralTextures,IconAtlas}.java`, `engine/{AudioManager,ProceduralAudio,ParticleSystem}.java`, `PlayerBlockActions.java`, `WorldInteractions.java`, `InteractPromptBuilder.java`, `CrateTransactionSystem.java`, `ui/CrateScreen.java`, `ai/{Pathfinder,SettledNpcAI,FirePanic}.java`, `settlement/SettlementManager.java` (one line; 1,420 of 1,500), `entity/{Entity,Npc}.java`, `save/V3ExtensionSections.java`, `qa/RuntimeBudgetSnapshot.java`, `QaHarness.java` (one line; 1,493 of 1,500), `UrbanQaScenes.java`. `Game.java` is untouched (994).
- **Tests**, under `src/test/java/com/veylon/`:
  - New: `UrbanFixtureArena`, `UrbanFixtureGameplayTest`, `UrbanFixtureQaSceneTest`, `ai/UrbanResidentFixtureTest`, `save/FixtureStatesSectionTest`, `world/FixturesTest`, `gfx/UrbanMaterialsTest`.
  - Extended: `qa/SerializedEnumOrderTest` (appended names), `item/CreativePaletteTest` (palette 21), `qa/RuntimeBoundsTest`, `GameLoopIntegrationTest`.
- **Documents**: `docs/ARCHITECTURE.md` ("Doors, windows and urban fixtures"), `docs/DEVELOPING.md` (scenes; adding a block), `docs/graphics/ASSET_PIPELINE.md` (the real icon hook), and these four records.

### Stable ids, schema and compatibility

- **Enums**: `BlockType` 57 → 78 and `ItemType` 110 → 126, appended only and pinned by `SerializedEnumOrderTest`. `BlockType.Shape` is not persisted.
- **New section**: `world.fixtures` v1 (D-49), written only when a fixture has state, so legacy and untouched urban saves keep exactly their sections.
- **Generators**: no stamped street or parcel block changed, so generator 5 is unchanged and keeps its fingerprints (D-54).
- **Verified by tests on this build**:
  - Open, barred, shattered and resident-held fixtures round-trip through the production save path; a resident's door is saved shut.
  - Saves without fixture state carry no section.
  - An absent section leaves fixtures as built.
  - Stale entries are dropped, and entries for chunks that do not exist yet never generate them.
  - 13 malformed payloads fail the load without touching the live world, on the title screen too.
- **Established by reading code, not by running it**:
  - A package-03 build knows generator 5, so it loads a 04 urban save. It reads the new block ids as air (`BlockType.byId`) and skips `world.fixtures` by its length, so doors, windows and fixtures vanish and their state is lost on re-save.
  - 0.8.1 still refuses any urban save as an unsupported generator.
  - A legacy save holding placed fixtures loads in 0.8.1 with those blocks as air.
  - No older binary was run.

### Commands and results

See [VALIDATION.md §7, milestone 04](VALIDATION.md#milestone-04-2026-10-08).

### Limitations and deferred items

- **No generated building uses the fixtures** until 05 and 06. Only Creative staging creates a keyed lock. Keys, tools that open a lock, and planned lock defaults (an absent entry falling back to the building plan) arrive with 05 and 06. Barricades and infected breaking doors are 18 and 09.
- **Streets keep generator 5's temporary materials** (D-42). They switch to asphalt and paving only under a later generator.
- **Interior light is still the legacy model** (D-19, package 06):
  - Windows let in no daylight.
  - Lantern light passes through walls, visible in the night captures (B-20).
  - Cutout glass casts a full shadow.
  - The target outline and crack overlay draw a full cell around thin fixtures.
- **Open leaves have no collider**: a body hugging the jamb may overlap the 0.125-thick leaf.
- **Containers use the crate backend until 07**: every placed container is saved (B-30) and spoils its food (B-47). The legacy crate keeps its orphan-on-blast and full-inventory loss (D-12).
- **Residents**: step-up only for people in urban worlds. Animals neither open doors nor step. Native evidence of residents is headless only; the native scenes show the player.
- **Climate is the legacy biome noise** (cold breath at −3.5 °C on the facade capture). That is packages 08 and 13.

### Next unmet prerequisite: entry point for 05

1. Read `00_BASE_KNOWLEDGE.md`, prompt 05, DESIGN §5 (A1–A12) and §13 rows 4 and 6, DECISIONS D-10, D-11, D-41 and D-48 to D-57, and VALIDATION §5 row 05. Check `git status` and preserve unrelated work. The accumulated 01–04 commit handoff is required before starting 05.
2. Plan with the 04 vocabulary:
   - doors and windows are `Fixtures.Kind` stacks;
   - a storey's window sits over a one-block sill in rows +2 and +3 of the storey;
   - a flight is four full-block steps per storey under an opening in the slab above, as `FixtureShowcase` shows;
   - containers are `Fixtures.isUrbanContainer`.
3. Give locked openings a plan default: an absent `World.fixtures` entry should fall back to the building plan's lock, with saved entries as overrides. Make sure every generated lock has an attainable tool or alternate entry (A5).
4. Build `AccessibilityValidator` on `Pathfinder.passable` and `standable` semantics (doors included) and the real colliders. Stamping geometry needs a new generator version (D-04); generator 5 stays pinned.

### Continuation and local commit handoff (2026-10-10)

- **Recovered checkpoint:** `main` still at `923345f`, no staged changes and no urban commits. All dirty paths matched the 01–04 handoffs; no unrelated work was found. The previous session's last edits guarded transfers from destroyed furniture and strengthened raycast/state and container-save regressions. Its final focused run and mutation probes were recorded only in ignored logs; the full build and native runs in the 2026-10-08 record preceded those edits. Fresh verification was therefore required.
- **Context read:** the revised urban `00_BASE_KNOWLEDGE.md` and pack README are in the ignored `build/urban-survival-roadmap/` copy, rather than the repository root. The current `AGENTS.md`, urban records and save-compatibility skill were read. The base file's section 6 explicitly authorizes the local catch-up commit; historical no-commit notes above describe the earlier policy only.
- **Final correction:** `FixtureActions.doorFits` now requires a loaded column and room for both halves below `Chunk.SY`. A ceiling placement previously could spend a door item on only its lower half. `UrbanFixtureGameplayTest` now exercises ceiling and unloaded-column refusals through the production placement command, preserving the item and world. `FixtureStatesSectionTest` now checks that a keyed lock still refuses entry audibly after reload and a forced whole door remains broken without a surviving lock or salvage.
- **Documentation:** corrected the `World.setBlock` record/notification contract in `ARCHITECTURE.md`, documented invalidated storage screens, and replaced the current superseded no-commit summary. D-57 records the placement contract; generator output, existing ids and the fixture codec layout did not change.
- **Fresh native evidence:** 10 day captures and 4 night captures at 1280 × 720, zero GL/KHR errors and zero native audio errors in both runs. Day captures at 3, 6, 9, 12, 15 and 18 s were inspected for the closed/open front door, closed/open/shattered windows from both sides, and the stair flight from below and above. All four night captures were inspected for facade, glass, step edges and door/HUD readability. The scripted production walk reached 5/5 waypoints on the roof (`pass=true`); this was not a manual keyboard or listening pass. Existing light leakage, cutout shadows and thin-leaf collision limitations remain explicitly deferred above.
- **Fresh portable verification:** the broad focused selection passed, followed by the two affected fixture suites after the placement correction. `.\gradlew.bat build --console=plain` passed in 6 min 40 s: 1,650 tests in 160 classes, zero failures/errors, one skipped Windows symlink-privilege case in `AppPathsTest`; Javadoc/doclint included. All required headless and native gates for 04 are met. Exact commands, logs and visual evidence are in [VALIDATION.md](VALIDATION.md#milestone-04-completion-2026-10-10).
- **Graph:** `graphify update . --no-cluster` completed (7,054 nodes, 26,691 edges), with no document/image extraction or clustering. Its Groovy parser could not extract `build.gradle`; `settings.gradle` has no symbols. The Gradle build is the authority for those files.
- **Commit scope:** all accumulated 01–04 task-owned paths, including coupled production code, regression tests and the four records. No later package was implemented. The final handoff supplies the actual new hash and subject; a commit cannot contain its own hash.

## Local commit history reconstruction (2026-10-10)

The owner requested the same feature-branch workflow used by earlier large updates.
The original combined commit `e452dc1` was local and unpushed; it is preserved at
`backup/urban-m01-m04-before-split`. `main` was restored to `923345f`, also the
unchanged local `origin/main` reference. Development continues on
`feature/urban-survival`; integration belongs to a separate authorized task.

The preceding continuation describes the original implementation session and its
evidence. Its one-commit handoff is superseded by this series, reconstructed from
the same content in dependency order. Shared source/test edits are split by
milestone: generator 4 precedes generator 5, fixture state precedes its commands,
and the shared showcase builder accompanies the tests that need it. The urban
smoke generator guards land with the foundation. Behavior, save layouts,
serialized ordinals, generator fingerprints and final test sources are unchanged.

| Milestone | Commit | Subject |
| --- | --- | --- |
| 01 | `939b185` | `docs(urban): define the city survival roadmap and compatibility contract` |
| 02 | `c85d442` | `chore(qa): isolate test and native development data directories` |
| 02 | `e60909f` | `test(world): pin legacy generator output before the urban split` |
| 02 | `c2c4463` | `feat(world): add the versioned urban foundation and save metadata` |
| 02 | `8a7de3f` | `test(save): cover urban revisions, identities and bootstrap isolation` |
| 02 | `c401b8f` | `docs(urban): record the versioned foundation and save compatibility policy` |
| 03 | `09e60c7` | `feat(world): generate deterministic districts and connected city streets` |
| 03 | `525063a` | `test(world): cover street seams, parcel reservations and bounded planning` |
| 03 | `d4f68d6` | `feat(qa): stage city streets and a production seam walk` |
| 03 | `02a7e7f` | `docs(urban): record city planning invariants and the street-network gate` |
| 04 | `d13411d` | `feat(world): add urban materials and persistent fixture geometry` |
| 04 | `c941d34` | `test(gfx): pin urban material budgets and shared fixture geometry` |
| 04 | `ca93289` | `feat(world): connect urban entry, salvage and resident traversal commands` |
| 04 | `3f26102` | `test(world): cover urban entry, salvage, resident movement and fixture saves` |
| 04 | `e510b77` | `feat(qa): stage urban fixtures by day and night with a production roof walk` |
| 04 / handoff | This documentation commit | Architecture, development guidance, final fixture records and reconstruction evidence |

Fresh focused checks and Javadoc/doclint verified the reconstructed foundation,
city-plan, material, interaction and fixture-QA snapshots. Exact final build,
tree-equality, documentation and Git checks are recorded in
[VALIDATION.md](VALIDATION.md#local-history-reconstruction-2026-10-10). Earlier native
captures remain dated historical evidence for the identical final implementation;
no new native session, performance benchmark or release package is claimed here.

The first four packages remain complete. Package 05 is the next work item and was
not started by this history-only task. Future milestone work should commit scoped
changes on `feature/urban-survival` and preserve `main` until integration is
explicitly authorized. No co-author trailers or agent signatures were added.

## Milestone 05 — building plans and accessibility (2026-10-10)

**Status: COMPLETE.** Implementation, tests and these records form the scoped
local milestone commit, separate from the committed 01–04 prerequisite work. These are
validated proposals; no generated furnished exploration is claimed.

### Prerequisites and start state

- Read the full revised base knowledge from the adjacent
  `../VEYLON-Urban-Survival-Roadmap/00_BASE_KNOWLEDGE.md` (no root copy exists),
  current `AGENTS.md`, pack README, relevant urban records, architecture/development
  guidance and the save-compatibility skill.
- Started on `feature/urban-survival` at `bdecf6f`, clean index and working tree.
  History and the preceding reconstruction record establish the accumulated
  01–04 handoff; no catch-up commit was duplicated. The agent list showed only
  this worker, no Git index lock existed, and no concurrent 04/index mutation
  was observed. Process command-line inspection was denied by the sandbox;
  completed handoff/agent/Git evidence supplied the work-state check.
- No unrelated staged/unstaged work was present. The revised owner policy
  explicitly authorizes the scoped local 05 commit.

### Implemented behaviour and paths

- `BuildingTemplates` revision 1 adapts compact/standard/court apartment layouts
  to reserved 12–32-cell parcels, retaining frontage, primary/rear entries and the
  4 x 6 stair core. Up to five storeys, optional B1, roof access; one-storey service
  parcels reuse the family. Shops have one storey or a flat above, sales floor,
  stock/utility rooms and rear entry. All twelve functions exist; shallow parcels
  combine functions. D-59 records the initial one-flat-per-storey quantity change.
- `BuildingPlan` contains immutable floor/room/fixture/stair records and exact
  proposed `PlanVolume` blocks. Room/fixture/container/quest slots use stable
  `UrbanIds`. Variants retain common reservations and never roll loot. Container
  and furniture targets reserve real adjacent standing clearance.
- `AccessibilityValidator` shares Entity's AABB collision and Pathfinder's
  standing/passability via `VoxelAccess`/`VoxelClearance`, without changing their
  live semantics. Directed transitions additionally sweep the real body, require
  step departure headroom, and cap drops at three. Forward/reverse floods prove
  entry/exit for room interiors, anchors, floor/roof landings and fixture targets.
  Missing room floor/headroom is invalid, rather than ignored as non-standing
  space. Flights need reversible swept movement and two-wide lanes. Identity,
  floor/window bands, physical opening actions, footprint/envelope, approaches
  and adjacent parcel overlap are checked too.
- Occupied front locks retain an unlatched rear route; no key/tool is assumed.
  `BuildingPlan.fixtureStateAt` resolves touched saved entries before defaults,
  including explicit unlock. 06 connects defaults to stamped generated fixtures.
- Intact, damaged-window, picked-clean-reservation and occupied-table/lock
  variants keep the contract. Repair tries candidate, intact alternate, minimal
  rooms on the same footprint/envelope; at most three validations. Exhaustion
  throws with diagnostics, never returns a sealed shell.
- `UrbanWorldState.buildingAt` caches at most 256 pure plans; eviction rebuilds
  identically, never creates chunks/edits/fixture states/contents. New/load worlds
  own fresh caches. `RuntimeBudgetSnapshot` reports `buildingPlans`.
- Generator 6 selects layout 3 / template 1 before bootstrap, explicitly reuses
  layout-2 city plans and stamps streets alone. Generators 4/5 and ordinary default
  3 retain their contracts. `urban.meta` v1, frozen v3 base bytes, section layouts,
  block/item enums and simulation RNG streams are unchanged.
- `BuildingPlanDiagnostics` prints pure diagrams, room functions, fingerprints
  and invalid-stair/repair evidence. [BUILDING_PLANS.md](BUILDING_PLANS.md) records
  current floor examples and diagnostic counters.

Production paths under `src/main/java/com/veylon/`:
`world/{World,VoxelAccess,VoxelClearance}.java`, `world/urban/{BuildingPlan,
BuildingTemplates,BuildingPlanner,PlanVolume,AccessibilityValidator,
BuildingPlanDiagnostics,UrbanRevision,UrbanSalts,UrbanWorldState}.java`,
`entity/{Entity,PlayerConstants}.java`, `ai/Pathfinder.java`,
`qa/RuntimeBudgetSnapshot.java`. No `Game`, renderer, mesher or audio changes.
Tests add `world/urban/{BuildingPlannerTest,AccessibilityValidatorTest}` and
`entity/BuildingPlanTraversalTest`, extending urban revision/dispatch/selector,
runtime-bound, save-meta, generator-fingerprint, seed and spawn regressions.
Documents: architecture/development guidance, the four urban records and examples.

### Compatibility and verification

- The saved generator pins template 1 even without optional meta; `urban.meta`
  validates layout 3. Production round-trips preserve plans/reservations, reject
  mismatches without replacing a live world and reset caches. Generator 5 never
  acquires building plans. Builds supporting at most generator 5 reject a gen-6
  save; that direction is established from exact dispatch, without running an
  older binary.
- Existing generator 1–5 pins are retained. Generator 6 stamping is pinned to
  generator 5's street fingerprints; six new pins cover both families at seeds
  `20260731`, `-4242`, `7`.
- 768 nominal size/family/frontage/variant/seed proposals and 4,112 actual city
  parcels across the frozen six-seed/eight-region matrix passed. An early matrix
  caught occupied tables obstructing a window interaction cell; the target now
  stands one cell in from the furniture line, within interaction reach. Final
  ordinary candidates validate without repair. Invalid fixtures exercise repair
  and three-attempt exhaustion.
- `PlayerMovementSystem` walks staged proposal flights up/down in all four
  frontages, both Survival/Creative, without jumping or flight. This is headless
  plan-debug staging, not a 06 generated/furnished exploration result.
- Exact fresh commands/results are in
  [VALIDATION.md](VALIDATION.md#milestone-05-2026-10-10).
- Portable build passed in 6 min 47 s, including Javadoc/doclint: 163 classes,
  1,672 tests, zero failures/errors, one Windows symlink-privilege skip.
  The code-only graph update completed once (7,224 nodes / 27,334 edges).

### Limits and next concrete step

- No buildings are stamped in generator 6. 06 needs a later generator and owns
  furnishing, generated fixture defaults, indoor light/shelter and native day/night
  street-to-basement/roof walks. Contextual contents/depletion are 07. Native
  plan-debug captures are optional for 05 and were not run.
- One flat per floor is the initial family. No multi-unit, elevator, B2, flooding,
  destructive collapse or key-acquisition system. Damage removes no playable
  rooms or reservations. Local plan collision arithmetic at far coordinates
  does not prove live far-coordinate floating-point physics.
- Per validation: at most 73,984 cells, seven floors, 32 rooms/floor, 512 fixtures,
  four approaches, six flights of at most 32 nodes, 295,936 directed transitions,
  32 retained messages. At most three candidate checks. Planning is on demand on
  the main thread with no per-frame planning tick.
- Resume at 06: add a later generator for stamping, keeping generator 6 and
  template 1 frozen. Stamp each whole proposal clipped to the parcel/chunk
  intersection; the paving halo is validation context, not owned geometry.
  Connect plan fixture defaults with saved overrides, guard loaded columns,
  preserve stable reservations and cross-chunk order, then validate furnishing,
  indoor light/shelter and native day/night basement/roof exploration.
  No 06 implementation was started here.

## Milestone 06 — generated apartments and interiors (2026-10-10)

**Status: PARTIAL.** Implemented geometry, furnishings, indoor integration and
automated physical/state validation. Scripted native views are appearance
evidence only; no human walk has been performed or claimed by this session.
Do not start 07 until the remaining native gate is met.

### Start state and decisions

- Read the full base from `../VEYLON-Urban-Survival-Roadmap/00_BASE_KNOWLEDGE.md`,
  pack README, current `AGENTS.md` and commit workflow, urban design/progress/
  decisions and relevant architecture/development/save-compatibility guidance.
  The linked root base file does not exist; the progress record located the pack.
  The owner later identified `build/`; the current
  `build/urban-survival-roadmap/00_BASE_KNOWLEDGE.md` was read in full and its
  README checked. Its current local-commit section delegates to the shared
  workflow already used for this dependency-ordered handoff.
- Clean branch/index/worktree at `8ef0cba`; no unrelated changes were present.
  Local commits are authorized by the base's local-commit policy. No push, merge,
  tag or release.
- D-62–D-66 record generator 7/template 2, whole-plan furnishings and owned slices,
  indexed edit replay, live indoor observations and evidence/matrix boundaries.

### Resulting behavior and paths

- `UrbanRevision` selects layout 4/furnished template 2 before bootstrap;
  generators 1–6, template 1 and the ordinary default retain their contracts.
  `BuildingFurnishing` adds modest beds, basin/collector forms, tables, cabinets,
  shelves, lockers, varied floors, clutter, broken partition panels and sparse
  growth. Every apartment/service/shop parcel is furnished and explorable.
  Contextual reservations are searchable through the existing empty backend;
  no starter-crate contents or permanent loot policy is introduced before 07.
- `UrbanBuildingStamper` clips the whole validated proposal to the parcel/chunk
  intersection, carving B1 and stamping foundations, floors/walls/doors/windows,
  switchback stairs and roof. Streets receive real urban materials in generator
  7 only. No neighbour writes, creation-order choices or generation-state entries.
- `ChunkBlockEdits` indexes deltas by chunk. Structure is followed by recorded
  destruction, then saved fixture overrides and one height/light finalisation.
  Defaults consume no fixture-state capacity; explicit saved unlock and player
  replacement defeat the original lock. Recorded breaks/removals persist.
- `UrbanIndoorQueries`, `World` and `Chunk` add loaded-only opening light,
  roof/glass weather cover, derived precipitation bounds and room/floor location.
  Shelter uses live enclosure; temperature/audio use street depth, so roofs never
  turn all indoor floors into deep caves. Sky edits dirty neighbouring meshes,
  including diagonal samples. No codec, enum, simulation stream or persistent
  indoor state is added. CPU chunk retention and block-light wall leakage remain
  the existing limits owned by 24 and 22 respectively.
- `UrbanApartmentQaScene`, `UrbanQaScenes`, `QaHarness`, `AutomatedRunDriver`
  provide generated scripted views and start-only manual Survival scenes.
  Raw day/night scenes do not stage lights; a separately labelled night variant
  adds four portable QA torches in empty cells for supplemental readability.
  The scripts do not claim player navigation. See [DEVELOPING](../DEVELOPING.md#urban-planning-qa)
  for commands and [VALIDATION](VALIDATION.md) for actual evidence.

### Verification and handoff

- The furnished proposal matrix covers 320 nominal cases and 4,112 actual parcels
  over six seeds/eight regions with zero repairs. Actual generated spatial checks
  cover all rooms/reservations of every parcel in six complete origin regions.
  The 16 x 16 region exception to the usual 15 x 15 test window prevents omitting
  boundary parcels. Seven chunk orders, negatives, region seams and far regions
  compare blocks, heights, lights and reservation fingerprints.
- Production player movement walks generated stairs in four frontages and both
  modes, plus one continuous Survival street/kitchen/upper/B1/exit route with
  real F actions. The old pathfinder selected an unswept stair-side drop; the
  physical route test uses production swept-clearance transitions. This does not
  implement or prove package 10's multi-floor AI routing.
- Generated save round-trip over a live world and reverse-order regeneration
  preserve explicit unlocks, open doors, broken windows and removed furniture.
  Empty inventories are not refilled. Generator 7 terrain/full-building and six
  template-2 reservation fingerprints are pinned; earlier pins are retained.
- Commands, outcomes, captures and pending checks are in the 06 evidence entry
  of [VALIDATION.md](VALIDATION.md). Commit boundaries are the reusable edit
  index, generation/indoor behavior with coupled assertions, additional generated
  regressions, generated QA behavior, and documentation. Each code boundary is
  checked against its actual contents; the final portable build includes doclint.
- Final native appearance matrix: 36 captures at two fixed seeds, six views each
  in daylight, raw night and explicitly instrumented portable-light night. All
  six sessions exited successfully with zero GL, KHR error and native audio
  errors. Day/portable-light interiors read clearly; raw night interiors remain
  very dark because the held torch does not illuminate terrain.
- Code graph updated once with `graphify update . --no-cluster`: 7,375 nodes,
  28,105 edges, no clustering or document/image extraction. Existing Gradle
  parser limitations remain; build configuration is authoritative.
- Final `.\gradlew.bat build --console=plain` passed in 7 min 6 s: 1,704 tests,
  zero failures/errors, one Windows symlink-privilege skip, plus Javadoc/doclint.
  All code was frozen before this run. Documentation resolved 53 relative links
  and passed whitespace checks. No task-owned source work remains uncommitted.

### Pending gate and exact resume step

Run `urban_apartments_manual` in the documented isolated directory with seed
`20260731`, using normal Survival input only. Walk street → entrance → kitchen →
upper floor → B1 → outside; record building id, findings and actual controls.
Repeat at night as needed for the native gate. No desktop/manual-input tool is
available to this agent, so camera holds and headless movement cannot supply this
evidence. The owner explicitly requested keeping this gate pending. Fix any
actual clipping/stair defect, rerun affected checks, then close 06 before
starting contextual loot in 07.

## 06 native-QA continuation — doors and shaped stairs (2026-10-11)

**Status: COMPLETE.** The owner explicitly instructed completing 06's gate before
07. Manual feedback found a misplaced street entrance, then a cubic closed door,
then requested real stair blocks after confirming the door repair. The symmetric
opening, thin closed panels and shaped stairs are human-confirmed. The owner
explicitly reported **“Yes, the full route passed”** for the normal-input Survival
street → kitchen → upper floor → B1 → outside route, without clipping or getting
stuck. The final portable build passed; **07's prerequisites are satisfied.**

Implemented:

- `FixtureGeometry` swings paired leaves against opposite outer jambs, including
  when a partner is broken or removed. Closed doors use shared thin panels for
  mesh/targeting; `ChunkMesher` retains adjacent jamb faces. Collision, lighting
  opacity, locks and saved door bytes retain their previous contracts.
- `StairGeometry`, `BlockType`, `ChunkMesher`, `Raycaster`, `VoxelClearance`,
  `VoxelPhysics`, `RagdollCollision` and `ProjectileSystem` share real tread
  bounds. Placement, block picking and salvage share the existing stair item.
  Direction persists in appended block ids 78–81, within the signed-byte budget.
- `BuildingStairShapes` and generator 8 select template 3 before bootstrap.
  Only stair cells change from template 2; landings stay full concrete. Room,
  building, floor and fixture identities and occupation/basement choices stay
  identical. Old generators are frozen; no save is upgraded. The v3 layouts,
  `urban.meta` v1, content revision and collection limits are unchanged.
- Generated QA accepts both furnished revisions and appends a scripted stair-foot
  view at 27 s. Manual scenes still position only once and use normal Survival
  input. These are distinct evidence paths. Decisions D-67–D-70 record the scope.

Actual validation and logs are in the new continuation section of
[VALIDATION.md](VALIDATION.md): door fixes passed 26 then 73 focused checks and
portable builds of 1,706 then 1,707 tests (one skipped in each); stair mechanics
passed 103 focused checks on their exact exported staged revision; generated
stairs passed 56 focused checks and five camera-scene checks. The initial arrow
test assumed guaranteed recovery; its assertion was corrected to test impact
independently of the existing random recovery policy.

The first integrated portable build found one stale test expecting template 3
to be unsupported. Its rejection case now uses the next unsupported revision;
the focused validator check and final build passed. The final build took
7 min 12 s: 1,715 tests, zero failures/errors, one Windows privilege skip.
The final code graph update completed with 7,433 nodes / 28,400 edges; the
subsequent test-only unsupported-revision literal correction changes no code
relationships. All 54 relative documentation links/anchors resolved.

Native appearance runs use isolated directories under `build/qa/`, seed
`20260731`, Survival and building `urban:building:0,0:0:0`, INTACT, three storeys
plus B1, bounds `(6,6)..(32,17)`. Day, raw night and portable-light night remain
separate; camera captures cannot confirm the human route. The raw night
readability and held-torch terrain-light limitations remain unchanged.

Next concrete step: implement 07's bounded lazy container ledger, contextual
tables, non-pausing search/transfer commands and extension codec, with durable
depletion, replacement and owner attribution through production save paths.
No release, merge, push, tag or
publication is authorized by this continuation.

## 07 — Contextual loot and persistence (2026-10-11)

**Status: COMPLETE.** Prerequisite 06 is complete: the owner
explicitly confirmed the full normal-input Survival route. 07 now uses actual
generated room furniture, with durable depletion from its first implementation.

Implemented behavior and source ownership:

- `world/urban/{UrbanContainers,LootTables,UrbanRevision,UrbanSalts}` supplies the
  bounded lazy ledger and twelve room/building/district tables. Stable generated
  fixture ids, negative player ids, explicit states and three owner classes are
  separate from item storage. Picked-clean shelves remain empty. No Inventory is
  allocated for untouched furniture or content-2 empty records. The 32,768-row
  cap includes permanent tombstones; cap refusal precedes block/item mutation.
- `CrateTransactionSystem`, `Game`, `WorldInteractions` and `ui/CrateScreen`
  search/take/deposit through production commands while normal simulation keeps
  ticking. Search emits real noise, hides contents, resumes durable progress and
  interrupts on closing, damage/death, distance, unloaded/replaced targets.
  Left click moves a stack; right click one item. Full and partial destinations
  preserve undelivered supplies and stack metadata.
- `World`, `PlayerBlockActions`, `combat/ExplosionSystem`, `simulation/FireSystem`
  and `simulation/ItemConditionSystem` share authoritative storage/removal.
  Hand salvage refuses losses, moves contents once and retires the identity;
  blast/fire destroy contents and persist tombstones. Replacement starts empty
  as player property. Spoilage empties and compacts only materialized storage.
  `Inventory` retains charge/durability and weighted freshness during merging.
- `save/{UrbanContainersSection,V3ExtensionSections,SaveSystem}` retains frozen
  core bytes and atomic publication. The version-1 section stores only bounded
  metadata (20-byte header / 36-byte rows), cross-checking the core inventory.
  Content 2 requires the section; old content-1 absence adopts inventories,
  including empties, and edited reservations. Content-1 touched empties retain
  core markers. Invalid loads preserve live state and failed saves preserve the
  previous file. New/load replaces the world-owned ledger and resets UI targets.
- Survivor taking affects Free Settlers faction reputation, once per logical
  event; stable building ownership survives reload. Abandoned/player property
  bypasses legacy proximity theft. Player destruction has a consequence;
  natural fire does not. Legacy camp trust remains separate (D-74).
- Final verification found `Game` at 1,014 lines against its unchanged 1,000-line
  budget. Existing death/respawn rules now live in `PlayerLifeCycle`; storage
  destruction attribution lives in `CrateTransactionSystem`. Game retains the
  same delegates and tick order at 991 lines. This extraction changes no saved
  state or player policy; the existing production death/Creative checks validate it.

Compatibility and policy are D-71–D-74. New content revision 2 does not change
geometry, generator dispatch, existing ordinals, or legacy-world crate rules.
Older binaries were not executed and downgrading new content-2 saves is not
supported. The authentic pre-loot fixture is retained unchanged with provenance
and SHA-256 in `src/test/resources/saves/README.md`.

Validation: exact foundation revision, 34 focused checks;
production gameplay/save/ownership/runtime checks, 162; independent generated
regression revision, 3; QA/generation/gameplay checks, 14; exact extracted
death/respawn/Creative/scavenging revision, 108. All passed with zero
failures/errors/skips. Exact commands, iteration corrections and artifacts are
in [VALIDATION.md](VALIDATION.md). The first portable build failed only the
orchestration budget; after extraction, final `.\gradlew.bat build --console=plain`
passed in 7 min 21 s: **1,737 tests, zero failures/errors, one Windows symlink-
privilege skip**, plus Javadoc/doclint. Code was frozen before that run. The
authentic fixture hash still matches; all 55 relative documentation links and
anchors resolve and the diff passes whitespace checks.

Native `urban_loot` used seed `20260731`, generator 8 and Survival in isolated
`build/qa/` directories. It searches a generated abandoned kitchen and survivor
cupboard through normal transaction commands, with scripted positioning only.
Both emitted noise; the clock advanced about 16 game minutes and hunger fell
0.4478836 while the screens stayed open. Abandoned taking incurred no theft;
one owned item cost four faction reputation. Five final captures show hidden
search contents, progress, ownership and remaining supplies; panel corners and
text were corrected after the first run. The source after extraction was checked
again in `build/qa/m07-contextual-loot-verified-20261011`; its five captures and
live-search gate also passed. All three sessions exited cleanly with zero
GL/KHR/native-audio errors. No human 07 walk or threat encounter is claimed.

The completion code graph update (`graphify update . --no-cluster`) has 7,574
nodes / 29,010 edges (`build/m07-graph-final-20261011.log`). It supersedes the
premature pre-budget-fix update (7,568 / 29,004); no further code changed.
Existing Gradle parser limitations remain; no clustering or semantic
document/image extraction ran.

No required 07 gate remains pending. Code is committed in the scoped series
listed above, followed by this record update; no task-owned changes remain.
Next is **08**: implement the
starter refuge/source guarantees and a first urban upgrade without tree/ore
progression. Current initial tables are not final economy tuning (23). Actual
specialist building families (12), full district rules (13), resident/guard
responses (16/17), onboarding (08/21) and CPU chunk eviction (24) retain their
own gates. No release, merge, push, tag or publication was performed.

## Milestone 08 — urban spawn and survival progression (2026-10-11)

**Status: COMPLETE.** No 09 implementation has started.
Baseline was clean `feature/urban-survival` at `0d76b66`; 07 had no pending gate.
The independently validated capacity fix is committed as `89a0edd`
(`fix(crafting): preserve supplies when craft output cannot fit`). Starter
behavior/regressions are `bc0a363` (`feat(urban): start survival in a scavenging refuge`);
documentation follows separately. Both code revisions were validated against
their actual contents before committing; no history was rewritten.

Implemented behavior and source ownership:

- `world/urban/{CityPlanner,BuildingPlanner,BuildingTemplates,UrbanStarterSite,UrbanWorldState,UrbanRevision}`
  and `World` introduce opt-in generator 9 / layout 6 / template 4 / content 3.
  The original street lattice contains one refuge, a shop and four three-storey
  apartments with B1 in the starter superblock. Validated room lanes provide
  workbench/light and unlocked finite supplies in ordinary ground-floor storage.
  No city reroll or debug giveaway is involved. Default stays generator 3;
  generations 1–8 and content 1/2 remain supported/frozen. No enums were appended.
- `UrbanBootstrap`, `WorldBootstrap`, `PlayerLifeCycle` and
  `save/{UrbanRefugesSection,V3ExtensionSections,SaveSystem}` own the loaded,
  supported bedroom spawn and persisted starter anchor. Respawn checks at most
  4,225 actual ground-floor cells, avoiding roofs, basements and unloaded air.
  `urban.refuges` v1 is mandatory for generator 9, 5/25 bytes, with strict
  identity/coordinate/version/presence/length checks and atomic load isolation.
  Load never grants the kit or replenishes fire/water; inventory retention on
  death follows the existing policy.
- `item/{UrbanCrafting,CraftingSystem}`, `ui/CraftingScreen`, `SurvivalTransfers`,
  `WorldInteractions` and `PlayerConsumables` supply fifteen repair/reuse recipes,
  recovered-wood fuel and atomic capacity handling. Food, reusable containers,
  water/boiling, cooking, bandages/splints, light, bedroll, tool/weapon repair and
  the first reinforced spear have urban paths. Antiseptic is scavenged. Earlier
  worlds retain the original recipe set and ingredient requirements.
- `SleepSystem`, `PlayerEnvironmentSystem`, `InteractPromptBuilder`, `ui/Hud`
  and fresh-session logs adapt room safety, smoke, refill/fuel and shelter hints.
  Rain/cold reuse the existing room shelter query. A courtyard fire does not add
  indoor smoke; a covered fire does. Creative bootstrap/catalog/world controls,
  free consumption, flight and damage gates remain covered.
- `UrbanQaScenes`, `QaHarness` and `UrbanApartmentQaScene` provide the read-only
  `urban_starter_manual` scene. New starter/progression, refuge-codec and generated
  order/accessibility tests exercise production mechanics; selector/revision and
  unsupported-version expectations track the newly supported versions.

**Exact provisional camp:** zero NPCs, no legacy camp stamping, camp faction
track, settlement or trade/quest grant. This is an unattended flat with ordinary
destructible doors/walls, beds, storage, workbench/light and courtyard fire plus
rain collector. It has no invulnerability radius. The current prompt makes
friendly roles optional; populations and camp encounters remain 16 (D-76).

Validation and evidence are in [VALIDATION.md](VALIDATION.md#milestone-08--starter-refuge-and-survival-progression-2026-10-11).
Six fixed seeds complete continuous apartment/shop scavenging, treatment, sleep,
upgrade, save/load and death return without trees/ore, after spending the kit.
Full-inventory failures preserve inputs and water. Legacy/bootstrap, Creative,
save, geometry/accessibility and bounded-runtime checks run in the portable build.
The normal-controls native route uses OS keyboard/mouse, not injected poses or
items; its captures show the spawn, searched cupboard and saved bedroom return.
The optional human playtest was not performed. Native treatment/sleep/upgrade
remain unclaimed; their production routes passed headless.

Initial balance assumptions: the kit has five units; food/water/treatment and
salvage are distributed across the six starter buildings, with no replenishing
container rolls. The collector starts with 3 litres and renews only through
existing rain. Fire starts at 240 seconds; a plank adds 60, or 450 to a lantern.
The guarantees are deliberately generous before scarcity tuning in 23. Prolonged
native input-calibration pauses reached ordinary cold/toxic events; interiors
protected the returned player, while an outside-acquired sickness persisted.
No new safe-zone exemption was added. Claims/fortification are 18; utilities 19;
full climate differentiation 13; infected encounter and sanctuary behavior 09–10.

Final `.\gradlew.bat build --console=plain` passed in 7 min 28 s on the committed
source: **1,751 tests, zero failures/errors, one existing Windows privilege skip**,
plus Javadoc/doclint (`build/m08-build-verified.log`). The corrected final focused
starter/legacy-lantern invocation passed ten checks in five seconds. Native route
and final-source startup both exited cleanly with zero GL/KHR/native-audio errors.
The single completion graph refresh (`graphify update . --no-cluster`) produced
**7,635 nodes / 29,324 edges** (`build/m08-graph-final-20261011.log`); existing
Gradle extractor limitations remain. No clustering or semantic extraction ran.
All 56 relative documentation links/anchors and whitespace checks pass. No
required 08 gate remains pending; the optional human balance walk is unclaimed.
Task-owned changes are committed in the scoped code series followed by this
handoff. No push, merge, tag, release or publication was performed.

Next concrete step: **09**, append the first
infected kind and bounded encounter state, prove damage/Creative gates and a
persisted encounter at least 24 walking blocks from the refuge door without
spawn visibility. Do not switch the ordinary generator default.

## Milestone 09 — infected threats and encounters (2026-10-12)

**Status: COMPLETE.** Decisions D-81–D-85; human-only validation deferred by D-80.

The Veilbound is a former colonist altered by alien membrane tissue. Its tilted
pale head, forward torso and single braced shoulder distinguish it from people.
The shared humanoid infrastructure supplies hit zones, melee/projectiles, pain,
fire/panic, dismemberment and non-harvestable remains. Explicit infected dispatch
excludes settlement allegiance, jobs, hunger, trust rewards and animal food.
It has 28 health, moves at 1.65 blocks/s and strikes for 6 physical damage after
a 0.65-second windup, then recovers for 1.8 seconds. Sight uses light, crouching,
facing and loaded occlusion; hearing investigates the real noise position.
Pain interrupts attacks; range, sight and Creative immunity are checked again
at impact. No new disease or kill drops were added; existing bleeding/treatment
and finite household belongings remain the resource paths.

Generator 10 / layout 7 / template 4 / content 3 adds frozen encounter catalogue
1 while retaining generator-9 blocks and loot. Earlier generators remain
encounter-free; the ordinary default remains generator 3. Two seeded slots per
selected residential building use a street approach and a ground-floor room
anchor. Activation requires loaded whole footprints, supported clear bodies,
16 blocks of player breathing room, 24 from the refuge approach/anchor, exclusion
of the refuge parcel and occupied buildings, spacing from people and occlusion
from the camera's forward half-space. At most 12 are active, with two admissions,
16 parcel checks and 16 saved-position checks per slow tick. Shared NPC limits
and counterattack reservation remain effective. Creative natural-spawn pause
also gates infected activation.

`urban.population` v1 requires codec/catalogue/count headers and 47-byte rows
with packed encounter id, stable species, injury/position/yaw and optional core
NPC binding. Dormant bodies pause at their saved injury and position, including
pursuers away from their original parcel. Health-zero tombstones never refill.
The 32,768-record cap refuses new encounters rather than evicting deaths. Counts,
lengths, planned identities, species, finite scalars, wounds and core bindings
are validated before replacing the live world. New/load resets state. Saving
immediately after a lethal impact commits the death and humanoid remains once.
Unloaded living bodies freeze before dormancy, preventing unloaded AIR from
changing the saved floor.

The independently validated humanoid slice is committed as `e3211a7`. Its exact
intermediate worktree passed the portable build: 1,762 tests, zero failures/errors,
one existing Windows symlink-privilege skip, plus Javadoc/doclint. The final
encounter/save/Creative/reset/selector focused run passed 38 tests with zero
failures/errors/skips. Street and ground-floor apartment native scenes both
passed attack, pain, fire, projectile death and save/load/depletion checks, with
zero GL/KHR/native-audio errors. Creative controls were captured at 1280×720 and
1920×1080. Exact commands and artifacts are in
[VALIDATION.md](VALIDATION.md#milestone-09--infected-threats-and-encounters-2026-10-12).

Final `.\gradlew.bat build --console=plain` passed in **7 min 50 s** on
`7827f3c`: **1,773 tests, zero failures/errors, one existing Windows
symlink-privilege skip**, including Javadoc/doclint. Source stayed unchanged
through this run and the final native captures. The single completion graph
refresh, `graphify update . --no-cluster`, exited 0 with **7,748 nodes / 29,822
edges**; existing Gradle extractor warnings remain. No clustering or semantic
extraction ran. Scoped code commits are `e3211a7` and `7827f3c`, followed by the
independent record handoff. All 57 relative links/anchors in the seven changed
documents and staged/unstaged whitespace checks pass. The exact intermediate
validation checkout was verified and removed, retaining its log/counts locally.
No push, merge, tag or publication was performed.

These native scenes use scripted observer poses, instrumented ignition and a
projectile; they do not prove scavenging, ammunition economy or human comfort.
Human playtesting/listening remains deferred until after 22 under D-80. Pursuit
is supported on the same floor; multi-floor portals, vertical routing and sound
attenuation are pending 10. Closed doors stop pursuit; destruction is 18. No
migration or repopulation is implemented.

Next concrete step: **10**, introduce
budgeted indoor portal routing and perception through edited doors and stairs,
with multi-floor pursuit/escape fixtures and native evidence. Preserve catalogue-1
identities, tombstones and generator-9/10 geometry. Do not switch the default.

## Milestone 10 — indoor navigation and perception (2026-10-12)

**Status: COMPLETE** — focused checks, final portable build, scripted native
validation and completion graph refresh passed. Human-only validation remains
deferred by D-80.

`Pathfinder` remains bounded voxel A*. Derived `PortalGraph` waypoints connect
building approaches, rooms and adjacent stair landings; long exterior missions
advance through loaded supported local anchors. `IndoorNavigation` is the common
resident/infected follower. It validates swept bodies and actual support, opens
supported unlocked doors/gates through production actions, and waits safely on
blocked, unloaded or deferred routes. A bounded search may return a connected
prefix to make progress through complicated legacy settlements. It never caches
that prefix as a complete route to the original goal. Stair height is explicit;
no roof-height substitution or direct steering fallback remains in this follower.

Every entity fast tick shares **four queries / 2,800 expansions**, with **700 per
query** and staggered replans. At most **64 transient routes of 512 cells** are
cached. Block/damage edits and lock/bar changes invalidate intersecting route
envelopes, preserving unrelated routes and equivalent unlocked door swings.
Fresh worlds/load rebuild derived plans and caches; departures clear live routes,
combat targets and hearing/search references. No save layout, serialized enum,
generator revision, seeded geometry or encounter identity changed.

Indoor `WorldNoise` hearing consumes positioned evidence once, with **16 queries /
4,096 ray samples per tick**, closed-door/glass/solid losses of 6/4/8 distance
units per cell, and no sound through unloaded columns. Existing rummage, footstep,
forced-entry and gunshot producers use this queue. Sight checks loaded partitions
and floors. Veilbound investigate sound for 14 seconds or sight for 18, search
the last-known place, and stop after expiry. Teleports supply no new observation;
Creative invisibility clears intent. Residents use occluded last-known evidence
instead of following an unseen live player coordinate. D-86–D-90 record the policy.

The exact navigation-only intermediate tree passed **58 tests**, including
generated stair traversal in all frontages/both directions, roof/basement bands,
ladder/drop and loaded-boundary safety, edited barred doors, connected search
prefixes, daily routines and counterattack travel. Expanded final focused checks
passed **76 tests**, followed by **36 perception/navigation checks** after the
repeated-noise route-retention change. The final build includes an additional
departure-reference regression. Logs/counts are retained under ignored `build/`.

Deterministic contention with 24 mixed resident/infected bodies used exactly
**4 queries / 2,800 expansions**, deferring **20** safely. Final generated native
QA reached floor 2 in **17.0628 seconds**, then lost sight after an explicitly
scripted basement relocation, searched the unchanged evidence and stopped.
Peaks were **1 query / 33 expansions / 25 hearing samples** in a tick. The run
passed in **1 min 26 s** with zero GL/KHR/native-audio errors. These are workload
counts and scripted mechanics evidence, not calibrated frame-time or human
escape/listening evidence. Commands, host and artifacts are in
[VALIDATION.md](VALIDATION.md#milestone-10--indoor-navigation-and-perception-2026-10-12).

Final `.\gradlew.bat build --console=plain` passed in **7 min 26 s**:
**1,787 tests, zero failures/errors, one existing Windows symlink-privilege skip**,
including Javadoc/doclint. Production source stayed unchanged from the final
native run through this build. Local code commits are `edf3d45` and `ae80b14`;
the exact navigation-only checkout matched its committed tree before removal,
with its log/counts retained locally. The single completion graph update,
`graphify update . --no-cluster`, exited 0 with **7,814 nodes / 30,097 edges**.
Existing Gradle extraction warnings remain; no clustering or semantic extraction
ran. All **58 relative links/anchors** in the six changed documents and staged/
unstaged whitespace checks pass. Records follow in their own commit. No push,
merge, tag or publication occurred.

Supported traversal is loaded walking, one-block steps, existing safe drops,
generated switchback stairs/landings, unlocked doors/gates and supported ladders.
Intact windows, locks/bars and impassable edits block routes; destruction remains
18. Broken entries need actual body clearance and support. The modest hierarchy
does not promise arbitrary citywide detours or whole-building acoustics. Crowded
replan/hearing latency and attenuation need tuning in the integrated slice.
Human pursuit/escape, comfort and listening remain deferred until after 22 under
D-80. No required automated/native 10 gate remains pending.

Next concrete step after the local handoff: **11**, build the generated
scavenge/return/save/revisit integration route with a real infected encounter,
preserved depletion, Creative twin and explored-area memory measurements.

## Milestone 11 — playable vertical slice gate (2026-10-13)

**Status: COMPLETE.** A generated starter neighbourhood now supports the full
scavenging → risk → return loop on all six frozen seeds headless, in both modes,
and natively on seed `20260731` in both modes. Human-only validation remains
deferred by D-80. This gate does not claim the city transformation is complete.

### Start state

- Branch: `feature/urban-survival` at `d16be92`; the tree was clean.
- Context read: the full revised base
  (`build/urban-survival-roadmap-revision/00_BASE_KNOWLEDGE.md`, which holds the
  local-commit section), the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md`, and
  the urban design, decisions, progress and validation records.
- A read-only, five-area mapping workflow and headless probes measured the
  neighbourhood, encounter density, climate and a natural loot-and-fight policy
  before the route was written.

### The slice and its route

- **The neighbourhood (D-91).** The generator-10 starter superblock exactly as
  generated:
  - the unattended refuge, its provisional camp, with bed, workbench, storage,
    courtyard fire and collector;
  - four three-storey apartments with B1;
  - the corner shop and the street lattice;
  - the courtyard ring reached through two passages. That ring is the alley
    alternative: every destination can be reached both around the streets and
    across the courtyard.
- **Choosing the apartment (D-91).** `UrbanSlicePlan` chooses, from the frozen
  plan alone, the apartment whose ground-floor kitchen encounter clears the
  sanctuary, plus the fixtures and doors the route uses. Its route search is
  bounded: at most 60,000 cells within 128 blocks.
- **The scene (D-96).** `UrbanSliceQaScene` (`VEYLON_SCENE=urban_slice`) plays
  the route with normal controls:
  - held keys and mouse buttons through `Input`;
  - mouse-look camera turns;
  - the crate transfer and craft commands those screens call.

  The route itself:
  1. Walks from the refuge across the courtyard.
  2. Forces an interior apartment door.
  3. Searches the utility locker, kitchen, bathroom and first-floor kitchen,
     leaving two torches behind.
  4. Handles a Veilbound.
  5. Searches the shop, leaving fibre and planks behind.
  6. Bars the refuge's street door and stores planks.
  7. Eats, drinks and treats a wound if bleeding.
  8. Crafts the reinforced spear.
  9. Quick-saves (F5), then quick-loads (F9) over the live world.
  10. Checks for stale state.
  11. Revisits in reverse order and verifies there was no refill or
      resurrection.
  12. Prints memory checkpoints and a timeline, writes 16 labelled captures and
      closes.

  It stages no item, pose, block, body, clock or weather.
- **Shared seams (D-96).**
  - `Game.playStep` is the frame's control and simulation step, moved verbatim
    (Game is at 995 lines).
  - `Input.onMouseButton` is public.
  - `CraftingScreen.craft` is the screen's Enter command.
  - `AutomatedRunDriver` gains labelled scene captures and a scripted finish.
- **Regression set and capture list (D-96, VALIDATION §6).** Frozen as the six
  urban design seeds in both modes headless, plus native `20260731` in both
  modes. The 16-capture list is in DEVELOPING.

### Blockers fixed while playing it

Each fix is in existing package scope, with a regression test proven to fail
without it:

- **B1 (07), D-92.** Searches now stop only on discrete harm
  (`Player.harmEvents`). Cold-snap, bleeding and hunger drains previously
  cancelled every search.
- **B2 (09), D-93.** Camp illness no longer chooses a Veilbound, which had
  produced "The camp needs MEDICINE" in a city with no camp.
- **B3 (09), D-94.** `urban.population` accepts projectile torso wounds. A torso
  arrow made F5 fail, and a shot-down tombstone broke every later save.
- **B4 (07), D-95.** F closes a container the player is facing. Before, the same
  frame's interaction pass reopened it.

### Findings assigned rather than tuned (D-97)

None of these blocks the loop.

- **Encounter pressure → 23.** Continuous melee pain-locks a Veilbound: only two
  of twelve headless routes took a blow. Up to eight starter slots exceed
  DESIGN §4.4's "1–2 bodies".
- **Economy → 23.** The refuge's own starter containers already fund the spear,
  and supplies are generous.
- **Event drains → climate 13 and utilities 19.** Event cold and toxic fog drain
  up to 41 health on some seeds.
- **No locked flat in the starter block → 12 and 18.**
- **HUD → 21.** A brief "Exposed" after a load, a "Search" prompt on searched
  furniture, and a doubled broken-door label.
- **Lighting → 22.** Dark ground-floor interiors at noon.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `UrbanSlicePlan.java`, `UrbanSliceQaScene.java`.
- Edited: `Game.java`, `HotkeyRouter.java`, `CrateTransactionSystem.java`,
  `AutomatedRunDriver.java`, `QaHarness.java` (one label on an existing line,
  still 1,496), `UrbanQaScenes.java`, `engine/Input.java`, `entity/Player.java`,
  `save/UrbanPopulationSection.java`, `simulation/EventSystem.java`,
  `ui/CraftingScreen.java`.

**Tests** (`src/test/java/com/veylon/`):

- New: `ContainerCloseKeyRoutingTest`, `UrbanSliceQaSceneTest`,
  `UrbanSliceRouteTest`, `UrbanSliceRun`.
- Extended: `UrbanLootGameplayTest`, `save/UrbanPopulationSaveTest`,
  `simulation/UrbanSurfaceSpawnGateTest`.

**Documents:** `docs/ARCHITECTURE.md` (the frame step, the consumed F press,
harm-based interruption), `docs/DEVELOPING.md` (`urban_slice`, its command and
capture list), and these four records.

### Compatibility

- **Unchanged:** generator, layout, template, content revision, encounter
  catalogue, section layout and version, serialized enums, and the new-world
  default. Generators 1–10 keep their fingerprints; the portable build covers
  this.
- **D-94 widens accepted wound values only.**
  - This build reads every older save.
  - A pre-11 urban build rejects a new save that holds projectile wounds; it
    already failed to write one.
  - No older binary was run.
- **Transient only.** `harmEvents` is never saved. Input and screen behaviour
  changed only as D-92 and D-95 describe.

### Commands and results

The exact commands, iteration failures, mutation results, review outcome,
memory measurements, host and capture inspection are in
[VALIDATION.md](VALIDATION.md#milestone-11--playable-vertical-slice-gate-2026-10-13).

In short:

- **Focused checks.** Each blocker's regression test fails without its fix.
  Refill and resurrection mutations fail the route gate.
- **Review.** The four-dimension adversarial review confirmed seven
  low-severity runner and test issues; all are fixed.
- **Native routes.** Both modes pass with 16 captures each and zero GL, KHR
  and native-audio errors.
- **Portable build.** Final run on 2026-10-14: 8 min 53 s, 1,796 tests in 195
  classes, zero failures or errors, one existing Windows symlink-privilege
  skip.
- **Graph.** The single completion update produced 7,966 nodes and 30,714
  edges.

The code commits are below. Each was validated against its exact tree in a
detached worktree before it was committed:

| Commit | Subject |
| --- | --- |
| `0318966` | `fix(save): keep projectile torso wounds in Veilbound population rows` |
| `01c42ec` | `fix(events): keep camp illness away from Veilbound encounters` |
| `2f63652` | `fix(loot): interrupt searches on blows rather than slow drains` |
| `51578ad` | `refactor(game): share the frame's control and simulation step` |
| `1e60097` | `fix(input): let F close a faced container without reopening it` |
| `51c5a9c` | `feat(qa): play the urban slice route through normal controls` |
| `406803b` | `test(urban): gate the slice loop on frozen seeds, reloads and chunk orders` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

**Evidence types.**

- The headless and native routes are scripted, through the production input
  seam. They are not a human playtest: human legibility, comfort, threat
  fairness and listening judgements stay deferred until after 22 (D-80).
- Mouse clicks inside the crate and crafting screens are represented by those
  screens' commands.
- The reload regenerates the spawn ring, which contains the starter superblock,
  in its fixed order. Order independence of the saved slice is proven by the
  headless five-order matrix. The native revisit changes only the walking and
  streaming order.

**Memory.**

- Memory is recorded for this one explored area on one host. CPU chunk
  retention and the streaming policy remain package 24.

**Next.**

- **Package 12, commercial and civic buildings,** is unblocked. Add the six
  families under a new generator. Keep the slice route and its frozen seeds as
  the regression gate for the starter loop, and keep the ordinary default at
  generator 3.

## Milestone 12 — commercial and civic buildings (2026-10-14 to 2026-10-16)

**Status: COMPLETE.** Every family generates real connected interiors with
reachable containers and exits, under a new generator that leaves every earlier
urban layout and legacy fingerprint unchanged. Human-only validation remains
deferred by D-80. Decisions D-98–D-106.

### Start state

- Branch `feature/urban-survival` at `daadff0`, clean tree; the 11 gate met.
- Read the full revised base (`build/urban-survival-roadmap-revision/00_BASE_KNOWLEDGE.md`),
  the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and these records.
- Throwaway probes measured the parcel supply per region (about 10–20
  commercial parcels in a residential region, about 50 downtown, one service
  parcel, mostly shallow residential strips) before the catalogue was sized.
- Generators 8–10 had only relative pins. Their absolute fingerprints were
  recorded first, from an untouched `daadff0` worktree, and committed before any
  shared code changed.

### Catalogue and behaviour

- **Generator 11 / layout 8 / template 5 (D-98).** Six families: grocer or
  convenience store, clinic or pharmacy, school, office block, security post and
  warehouse with workshop. `CivicCatalogue` places one school, one security post
  and one warehouse per region and turns commercial parcels into grocers,
  clinics and offices. The starter superblock and every walk-up are exactly the
  template-4 plans, so the slice neighbourhood is unchanged. `urban-dev` creates
  generator 11; the ordinary default stays generator 3.
- **Reuse (D-99).** The walk-up builder now takes a `FloorProgram`; the
  refactor reproduces revision one exactly. Each family runs that skeleton with
  its own program, then `CivicTemplates` overlays fittings, desks, counters,
  racks, a secured room, a facade and a painted `SIGN` (block id 82, D-103),
  then the frozen stair shapes.
- **Rooms.** Shop floors, stockroom and cold store; dispensary, treatment rooms,
  washroom, utility and records; staff office, classrooms and canteen; work
  floors, utility and break rooms; armoury, squad and interview rooms, evidence
  and equipment stores and bunks; workshop and rack halls. Upper floors use the
  same switchback core. Schools and offices may have a basement.
- **Access costs (D-100).** Dispensary medicine is behind a hand-forcible
  wooden lock and armoury ammunition behind a metal lock that needs a pry tool,
  both loud, with concrete walls and bars. `AccessibilityValidator` proves the
  lock is the only way in and that forcing it opens entry and exit routes.
  Clinics lock a rear staff door and security posts a sally port, never together
  with the street door.
- **Variants (D-101).** Damaged, picked clean (forced secured door, empty stock)
  and partially occupied (held street floor and basement, upper storeys
  abandoned; `BuildingPlan.survivorOwned` is the single ownership rule).
- **Loot and threats (D-102).** New container uses map onto unchanged content-3
  tables; necessities keep unsecured sources in several families. Encounter
  catalogue 2 keeps catalogue 1 for walk-ups and adds lobby guards to unheld
  clinics and security posts and to one in four other specialist buildings.
- **Diagnostics (D-104).** Repairs and failures name template, revision, family,
  variant, parcel id, frontage, size and storeys; `BuildingPlanDiagnostics`
  reports per region (`catalogue`) and per family matrix (`familyMatrix`).
- **QA (D-105).** `UrbanRouteScene` shares the slice runner's input-seam route
  machinery. `urban_catalogue` walks one building of every family in both modes.
- **Review fix (D-106).** A guard by a clinic's locked staff door stalled there;
  `PortalGraph` now picks the nearest *usable* entrance.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `world/urban/CivicTemplates.java`, `world/urban/CivicCatalogue.java`,
  `UrbanRouteScene.java`, `UrbanCatalogueQaScene.java`, `UrbanCataloguePlan.java`.
- Edited: `world/urban/{BuildingTemplates,BuildingPlan,BuildingPlanner,BuildingFurnishing,
  AccessibilityValidator,BuildingPlanDiagnostics,LootTables,UrbanRevision,UrbanSalts,
  UrbanWorldState,UrbanContainers,EncounterAnchors}.java`, `world/World.java`,
  `world/BlockType.java`, `ai/PortalGraph.java`, `save/{UrbanPopulationSection,
  UrbanContainersSection}.java`, `gfx/{MaterialRegistry,UrbanTextures}.java`,
  `engine/AudioManager.java`, `item/CreativePalette.java`, `InteractPromptBuilder.java`,
  `CrateTransactionSystem.java`, `UrbanSliceQaScene.java`, `UrbanQaScenes.java`,
  `QaHarness.java` (one label on an existing line, still 1,496).

**Tests** (`src/test/java/com/veylon/`):

- New: `world/urban/CivicCatalogueTest`, `world/UrbanCivicGenerationTest`,
  `UrbanCivicGameplayTest`, `save/UrbanCivicSaveTest`, `UrbanCatalogueQaSceneTest`.
- Extended: `world/WorldGeneratorFingerprintTest` (generators 8–11),
  `ai/IndoorNavigationTest`, `UrbanSliceRouteTest` and `UrbanSliceRun` (generator
  11), `qa/SerializedEnumOrderTest`, `gfx/UrbanMaterialsTest`.
- Expectations: `world/urban/{BuildingPlannerTest,BuildingFurnishingTest}` iterate
  the walk-up families; `UrbanRevisionTest`, `UrbanGeneratorDispatchTest` and
  `UrbanDevelopmentSelectorTest` name generator 11.

**Documents:** `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md` and the five urban
records, including [BUILDING_PLANS.md](BUILDING_PLANS.md).

### Compatibility

- Generators 1–10 are unchanged: their absolute pins pass, and every walk-up
  under generator 11 equals its template-4 plan.
- New: generator 11, layout 8, template 5, encounter catalogue 2 and block id
  82. Appended plan enums are metadata, never saved ordinals.
- Unchanged: the v3 base, every section layout and version, content revision 3
  and its tables, the default new world.
- An older build rejects a generator-11 save before replacing its live world;
  generators 10 and 11 each reject the other's population catalogue. No older
  binary was run, and there is no downgrade promise.

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-12--commercial-and-civic-buildings-2026-10-14-to-2026-10-16).
In short:

- Each commit was validated against its exact tree (a detached worktree, or a
  working tree that held only that change).
- The final portable build passed at `a3a091e` on 2026-10-16: 1,820 tests in
  200 classes, zero failures or errors, and one existing Windows skip.
- Native `urban_catalogue` passed in Survival and Creative on seed `20260731`,
  16 captures each, with zero GL, KHR and native-audio errors: first on
  2026-10-16, and again on the final source on 2026-10-18.
- An adversarial review of five dimensions confirmed one defect, fixed in
  `a3a091e` (D-106), and refuted the other two claims. Its QA-refactor and test
  reviewers stopped on a usage limit, twice; those two dimensions were reviewed
  directly on 2026-10-19, without independent skeptics, and found no defect.
- `graphify update . --no-cluster` on the final code: 8,158 nodes and
  31,711 edges.

The commits:

| Commit | Subject |
| --- | --- |
| `8d87857` | `test(urban): pin generator 8-10 blocks and building plans` |
| `880602f` | `refactor(urban): let a floor program choose skeleton room functions` |
| `7046b3b` | `feat(urban): add a painted frontage sign block` |
| `82e5594` | `feat(urban): plan a commercial and civic building catalogue` |
| `6e635d5` | `feat(urban): generate the civic catalogue under generator 11` |
| `e8dd714` | `refactor(qa): share the urban route driver between scenes` |
| `b82fa31` | `feat(qa): walk every commercial and civic family with ordinary controls` |
| `76dd7d3` | `test(urban): hold the slice loop on the civic catalogue generator` |
| `a3a091e` | `fix(navigation): leave and enter buildings by an unlocked entrance` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The native walk stages its poses between buildings and a
  spear and pry tool; inside it uses the production input seam. It is not a
  human playtest, which D-80 defers until after 22.
- **Distribution.** Only residential and commercial districts exist, so every
  region has a school, security post and warehouse; package 13 redistributes
  families by district under its own generator.
- **Presentation.** Offices and classrooms share desk islands; signs carry
  their words in the prompt, not on the board. Both are package 22.
- **Scale.** Offices stop at five storeys until package 24 measures taller ones.
- **Balance.** Clinic, armoury and warehouse stock and the guard density are
  initial values for package 23.
- **Starter block.** It still has no locked flat (D-97); claims and
  fortification are 18.

**Next:** package 13, urban biome differentiation: the six districts, with
district rules deciding which families appear where.

## Milestone 13 — urban biome differentiation (2026-10-16 to 2026-10-19)

**Status: COMPLETE.** Generator 12 plans six districts that change what the
player carries, where they go for each supply and which hazards they meet,
on the frozen street lattice, with every building still connected and
validated. Human-only validation remains deferred by D-80. Decisions
D-107–D-117; owner question P-15.

### Start state

- Branch `feature/urban-survival` at `6fd26c9`, clean tree; the 12 gate met.
- Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records. The work spanned sessions: plans, block, catalogue, generator
  and presence were committed on 2026-10-19 and the tour, the review fix and
  these records followed on the same day.

### Districts and behaviour

- **District map (D-108).** `DistrictMap` is pure: the spawn region is
  residential, its neighbours residential or commercial with at least one
  commercial, and rings two and three always hold an industrial, a flooded, a
  civic and a quarantine region (the quarantine in ring three). Farther regions
  draw from fixed weights. Basics never depend on a rare district.
- **Plans (D-109).** Layout 9 keeps the layout-2 street lattice, so
  residential and commercial regions and the starter superblock are exactly
  their earlier plans and seams are unchanged. Industrial regions get depot rows
  and fenced yards, flooded regions low housing around canal basins crossed by
  causeways, civic regions a fenced square, and the quarantine cordoned
  housing and clinic rows.
- **Overlays (D-110).** A separate per-column raster: standing water and
  duckboards, fences and checkpoints, contaminated ground, overgrowth, plants
  and powder kegs. It keeps two cells from every parcel, off walkways, alleys,
  passages, causeways and the starter superblock, and leaves avenues dry and
  clean. Every water surface lies at one height on a solid bed, so the
  existing water rules never move it.
- **Climate and presence (D-111, D-117).** Each district reports one existing
  `Biome` (no ordinal changes), so temperature, weather, fog tint, ambience and
  plants follow. The HUD names the district, fog thickens by district for
  presentation only, and an arrival line says what to expect once the player
  steps off the boundary avenue.
- **Buildings and supplies (D-112).** Template 6 picks proven template-4 and
  template-5 plans by district: warehouses in the yards, clinics, schools,
  posts and offices in the civic precinct, field clinics and checkpoint posts
  in the quarantine. Every district keeps at least two unsecured sources of
  food, water, treatment, light and pry tools.
- **Threats (D-113).** Encounter catalogue 3 spends each district's DESIGN
  §4.3 budget on unheld buildings. **Placeholder:** every district's threat is
  still the Veilbound; alien organisms, growth nodes and alien props are
  package 15.
- **Hazards (D-114).** `CONTAMINATED_GROUND` (id 83) sickens a Survival player
  who stands on it (boots help); the toxic-fog event only sickens inside a
  quarantine district. Wading in flooded streets soaks and slows the player
  through the existing water rules.
- **Persistence (D-115).** No new section: the generator fixes the layout and
  overlay revision, player edits to overlays are ordinary block edits, and
  sickness is an existing saved affliction.
- **QA (D-116).** `VEYLON_SCENE=urban_districts` walks a district transition
  and samples all six districts by day and four of them again at night and in fog.

How each district changes a decision is in DESIGN §4.3.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `world/urban/{DistrictMap,DistrictOverlays,DistrictCatalogue,DistrictRules,
  UrbanOverlay,UrbanOverlayStamper}.java`, `UrbanDistrictQaScene.java`,
  `UrbanDistrictTourPlan.java`.
- Edited: `world/urban/{CityPlanner,CityPlanDiagnostics,DistrictPlan,SuperblockPlan,
  UrbanDistrict,UrbanSurface,UrbanSurfaceMaterials,UrbanRevision,UrbanSalts,
  BuildingPlanner,BuildingPlanDiagnostics,EncounterAnchors,UrbanBuildingStamper,
  UrbanWorldState,StreetNetwork}.java`, `world/{World,WorldGenerator,BlockType}.java`,
  `entity/{Player,PlayerConstants}.java`, `PlayerEnvironmentSystem.java`,
  `simulation/EventSystem.java`, `gfx/{Environment,MaterialRegistry,UrbanTextures}.java`,
  `item/CreativePalette.java`, `ui/Hud.java`, `UrbanQaScenes.java`,
  `QaHarness.java` (one label on an existing line).

**Tests** (`src/test/java/com/veylon/`):

- New: `world/urban/{DistrictPlannerTest,DistrictCatalogueTest}`,
  `world/UrbanDistrictGenerationTest`, `save/UrbanDistrictSaveTest`,
  `ContaminatedGroundTest`, `UrbanDistrictPresenceTest`, `UrbanDistrictQaSceneTest`.
- Extended: `world/WorldGeneratorFingerprintTest` (generator 12),
  `UrbanSliceRouteTest` (generator 12), `UrbanCatalogueQaSceneTest`
  (generator 12), `CreativeHazardsTest`, `qa/SerializedEnumOrderTest`,
  `gfx/UrbanMaterialsTest`.
- Expectations: `world/urban/{CityPlannerTest,UrbanRevisionTest}`,
  `world/UrbanGeneratorDispatchTest` and `UrbanDevelopmentSelectorTest` name
  generator 12.

**Documents:** `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md` and the urban
DESIGN, DECISIONS, PROGRESS and VALIDATION records.

### Compatibility

- Generators 1–11 are unchanged: their absolute pins pass, catalogues 1 and 2
  behave as before, and generators 9–11 keep their frozen climates. Legacy
  biome consumers see the same `Biome` values in every earlier world.
- New: generator 12, layout 9, template 6, encounter catalogue 3 and block id
  83 (84 of 128 used; 110 of 160 texture layers). Appended plan enums are
  metadata, never saved ordinals.
- Unchanged: the v3 base, every section layout and version, content revision 3
  and its tables, the default new world (generator 3).
- An older build rejects a generator-12 save before replacing its live world;
  generators 10–12 each reject the others' population catalogues. No older
  binary was run, and there is no downgrade promise.
- Toxic-fog and legacy roll sequences are unchanged outside generator 12
  (`EventRollLadderTest`, `PlayerOutcomeDeterminismTest`).

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-13--urban-biome-differentiation-2026-10-16-to-2026-10-19).
In short:

- Each code commit was validated against its exact tree.
- The final portable build passed at `dbd0746` on 2026-10-19: 1,852 tests in
  207 classes, zero failures or errors, and one existing Windows skip. The
  tour's tree (`6ab1f49`) passed its own build with 1,851 tests.
- Native `urban_districts` passed in Survival and Creative on seed `20260731`, at
  `6ab1f49` and again at `dbd0746`,
  16 captures each, with zero GL, KHR and native-audio errors.
- A direct review of the shared-code changes found one defect, fixed in
  `dbd0746` (D-117). No reviewer subagents ran.
- `graphify update . --no-cluster` on the final code (`dbd0746`): 8,368 nodes and
  32,739 edges, with the existing `build.gradle` extractor warning.

The commits:

| Commit | Subject |
| --- | --- |
| `ba84727` | `feat(urban): plan six districts with their overlays under layout nine` |
| `3dd0d36` | `feat(world): add contaminated ground that sickens a survivor who stands on it` |
| `f987887` | `feat(urban): choose each district's buildings from the proven catalogues` |
| `8017601` | `feat(urban): generate the six districts under generator 12` |
| `da8ff47` | `feat(urban): name the district underfoot and keep toxic fog in the quarantine` |
| `6ab1f49` | `feat(qa): tour the six districts by day, night and fog` |
| `dbd0746` | `fix(urban): announce a district once the survivor leaves the boundary avenue` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The tour stages poses between stops, the clock, the
  weather and a spear; it is not the human playtest D-80 defers until after 22.
- **Threats.** The Veilbound stands in for every district's threat; the
  quarantine differs by budget, contamination and toxic fog only (package 15).
- **Presentation.** No district map colouring (21), district ambience or
  ruined-city props (22).
- **Balance.** Weights, budgets, loot biases and wading penalties are initial
  values for 23.
- **Day/night temperature (P-15).** The existing diurnal curve peaks at 02:00 in
  every world; left unchanged pending the owner's answer.
- **Default.** The ordinary new world stays generator 3 until 26.

**Next:** package 14, basements, transit and rooftop routes, on generator 12's
districts under a new generator.

## Milestone 14 — basements, transit and rooftop routes (2026-10-19 to 2026-10-21)

**Status: COMPLETE.** Generator 13 adds basements, service passages, a hidden
cellar per residential region, abandoned transit tunnels and stations, and
guarded roofs with fire escapes and connectors to generator 12's districts. Every
route is walked with the existing controls and proven free of unguarded drops.
Human-only validation remains deferred by D-80. Decisions D-118–D-128; owner
question P-15 is still open.

### Start state

- Branch `feature/urban-survival` at `b7c3e80` (the 13 records), clean tree; the
  13 gate met.
- Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records. The work spanned sessions: blocks, transit, routes, generator,
  discovery, lockers, encounters and the ladder fix were committed on
  2026-10-19; the tour, the runtime-cache report and these records followed on
  2026-10-21.

### Routes and behaviour

- **Transit (D-119, D-120).** Tunnels run only under boundary avenues, so they
  never cut a building. Each segment is a pure function of its name, the seed
  and its two bordering plans, so neighbours agree in any chunk order and
  crossing tunnels join. Every region owns one segment: at most one station and
  two segments per region. A segment exists only with its station: a service
  room behind a metal door, a platform one step above the track and a guarded
  stair up to the sidewalk, each proven by `TransitValidator` before it is
  sited. Rubble mounds slow a tunnel; only its ends can be plugged. Flooded
  districts' tunnels hold one block of standing water.
- **Basements and passages (D-121).** Every apartment outside the starter
  superblock has a first basement. Two-wide service passages join basements
  under courtyard passages through metal service doors, locked in occupied
  buildings.
- **Hidden cellar (D-122).** One apartment per residential region seals a back
  room behind a loose panel (`HIDDEN_PANEL`). Clues are generated cells:
  drag-marked debris, the panel's seam and chalk, and a back room with no door.
  F slides it aside. The room is package 16's refuge reservation and holds no
  civilians.
- **Roofs (D-121, D-123).** Parapets carry a guard rail, stairs end in a concrete
  bulkhead with a metal roof door, and the stair core's open well has a rail.
  Fire escapes are rope ladders up a rear wall to a parapet gap, caged at the
  head; connectors open abutting parapets or bridge a courtyard passage. The
  controller climbs a ladder but has no way down one, so a fire escape is an
  ascent, and the way down is the stairs.
- **Supplies and threats (D-120, D-125).** Station service rooms and route roofs
  hold maintenance lockers on the existing utility table. Encounter catalogue 4
  gives a region's station two slots inside its district budget, outside the
  spawn region. Nothing underground is required; there are no resource mines.
- **Costs.** Tunnels and stations are dark and need a light; flooded tunnels and
  flooded basements soak and slow through the existing water rules. Every route
  keeps a stair exit to the street.
- **Places and discovery (D-124).** The HUD names the route underfoot
  ("District / Reclaimed Residential / Service passage"); shelter, weather and
  sound follow it. Stations and cellars are recorded in `urban.discovery` only
  when observed.
- **QA (D-127).** `VEYLON_SCENE=urban_underground` walks both acceptance routes,
  the cellar, a station by night and a flooded tunnel.
- **Runtime caps (D-128).** The route-catalogue and transit-segment caches are
  reported and checked by the runtime budget snapshot.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `world/urban/{TransitNetwork,TransitStation,TransitValidator,RouteProof,
  RouteCatalogue,RouteTemplates,UrbanRouteStamper,UrbanPlaces,
  UrbanDiscovery}.java`, `save/UrbanDiscoverySection.java`,
  `UrbanUndergroundQaScene.java`, `UrbanUndergroundTourPlan.java`.
- Edited: `world/urban/{AccessibilityValidator,BuildingFurnishing,BuildingPlan,
  BuildingPlanner,BuildingTemplates,CivicCatalogue,CivicTemplates,
  EncounterAnchors,PlanVolume,UrbanContainers,UrbanIndoorQueries,UrbanRevision,
  UrbanSalts,UrbanWorldState}.java`, `world/{World,BlockType,Fixtures,
  ChunkMesher}.java`, `entity/{Player,UrbanEncounterDirector}.java`,
  `save/V3ExtensionSections.java`, `gfx/{MaterialRegistry,UrbanTextures}.java`,
  `engine/AudioManager.java`, `item/CreativePalette.java`,
  `FixtureActions.java`, `CrateTransactionSystem.java`,
  `qa/RuntimeBudgetSnapshot.java`, `UrbanQaScenes.java`, `QaHarness.java` (one
  label on an existing line).

**Tests** (`src/test/java/com/veylon/`):

- New: `world/urban/{TransitNetworkTest,RouteCatalogueTest}`,
  `world/UrbanUndergroundGenerationTest`,
  `save/{UrbanUndergroundSaveTest,UrbanDiscoverySaveTest}`,
  `UrbanLoosePanelTest`, `UrbanDiscoveryTest`,
  `UrbanUndergroundEnvironmentTest`, `UrbanStationTest`,
  `UrbanUndergroundQaSceneTest`.
- Extended: `world/WorldGeneratorFingerprintTest` (generator 13),
  `UrbanSliceRouteTest` (generator 13), `GameLoopIntegrationTest`,
  `qa/{SerializedEnumOrderTest,RuntimeBoundsTest}`, `gfx/UrbanMaterialsTest`.
- Expectations: `world/urban/UrbanRevisionTest`,
  `world/UrbanGeneratorDispatchTest` and `UrbanDevelopmentSelectorTest` name
  generator 13.

**Documents:** `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md` and the urban
DESIGN, DECISIONS, PROGRESS and VALIDATION records.

### Compatibility

- Generators 1–12 are unchanged: their absolute pins pass, catalogues 1–3
  behave as before (the director's station scan runs only under catalogue 4),
  and `UrbanPlaces` answers none in every earlier world.
- New: generator 13, layout 10, template 7, encounter catalogue 4, block ids
  84–86 (87 of 128 used; 114 of 160 texture layers) and section
  `urban.discovery` v1. Appended plan enums (`TRANSIT`, four room functions,
  `FixtureKind.ROUTE`) and `Fixtures.Kind.HIDDEN_PANEL` are metadata, never
  saved ordinals.
- Unchanged: the v3 base, every existing section layout and version, content
  revision 3 and its tables, and the default new world (generator 3).
- Generator 13's seed-7 terrain pin was re-recorded once on the day it was
  introduced, before any save outside the tests used it; it is frozen from
  this handoff (D-118).
- An older build rejects a generator-13 save before replacing its live world;
  generators 10–13 each reject the others' population catalogues. No older
  binary was run, and there is no downgrade promise.

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-14--basements-transit-and-rooftop-routes-2026-10-19-to-2026-10-21).
In short:

- Each code commit was validated against its exact tree.
- The final portable build passed at `747932e` on 2026-10-21: 1,897 tests in 217
  classes, zero failures or errors, and the existing Windows skip.
  The tour's tree (`38e1f84`) passed its own build with 1,896 tests in 217
  classes.
- Native `urban_underground` passed in Survival and Creative on seed
  `20260731`, 16 captures each, with zero GL, KHR and native-audio errors.
- No reviewer subagents ran. A direct check of the records against the code
  found the unreported route caches (D-128).
- `graphify update . --no-cluster` on the final code: 8,745 nodes and 34,459
  edges, with the existing `build.gradle` extractor warning.

The commits:

| Commit | Subject |
| --- | --- |
| `9999bdc` | `feat(world): add guard rails and a loose panel that hides a doorway` |
| `da30934` | `feat(urban): plan transit tunnels and stations under the boundary avenues` |
| `00ee530` | `feat(urban): plan basements, a hidden cellar and roof routes as template seven` |
| `04ceb6e` | `fix(urban): rail the stair core's open well in template seven` |
| `91d058e` | `feat(urban): generate underground and rooftop routes under generator 13` |
| `aef00bf` | `feat(urban): name the route underfoot and record found stations and cellars` |
| `d59dc02` | `feat(urban): stock transit stations with maintenance lockers` |
| `1ddcd8d` | `feat(urban): keep infected in transit stations with encounter catalogue 4` |
| `bd8fd6a` | `fix(urban): climb fire escapes up only and cage the ladder head` |
| `38e1f84` | `feat(qa): tour basements, a hidden cellar, roofs and transit routes` |
| `747932e` | `fix(urban): check route and transit plan caches in the runtime snapshot` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The tour stages poses between legs, the clock, the
  weather and a kit of a spear and torches; it is not the human playtest D-80
  defers until after 22. Low light and flood were walked by the script, not
  judged by a person.
- **Ladders.** Fire escapes are ascent routes only: the controller has no
  ladder descent, and none was invented. Residents' pathing still climbs both
  ways.
- **Hidden refuge.** The cellar is an empty reservation; its civilians, needs
  and aid are package 16. The per-region guarantee is shown on the tested
  seeds only (D-122).
- **Threats.** Station slots hold the Veilbound (package 15).
- **Presentation.** No map layer for finds (21); no underground ambience
  beyond the existing shallow-underground sound (22).
- **Balance.** Route, presence, plug and flood rates are initial values for 23.
- **Day/night temperature (P-15).** Still inverted in every world: the tour's
  HUD read 9.1 °C on the street at 12:42 and 20.7 °C on the street at 23:02.
  Left unchanged pending the owner's answer.
- **Default.** The ordinary new world stays generator 3 until 26.

**Next:** package 15, alien ecology and urban wildlife, on generator 13's
city under a new generator.

## Milestone 15 — alien ecology and urban wildlife (2026-10-21 to 2026-10-22)

**Status: COMPLETE.** Generator 14 grows the quarantine: growth nodes on
membrane carpets in its courtyards and squares, each guarded by a Veilmaw, an
original territorial ambush organism with a clear telegraph and counters that
need no rare gear. One infected variant, the blind door-breaking Breacher,
holds some street doors, and three existing wildlife species live in parks,
courtyards, canals and on roofs inside leashed habitats. Deaths, burnt nodes
and caps hold across dormancy, saves and revisits. Human-only validation
remains deferred by D-80. Decisions D-129–D-140; owner questions P-01 and P-09
carry notes; P-15 is still open.

### Start state

- Branch `feature/urban-survival` at `9e2d3b0` (the 14 records), clean tree;
  the 14 gate met.
- Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records. The work spanned sessions on 2026-10-21; the commits and
  these records followed on 2026-10-21 and 2026-10-22.

### Ecology and behaviour

- **Generator 14 (D-129).** Layout 11 is layout 10 plus each region's pure
  ecology plan; template 7 and content 3 are unchanged; encounter catalogue 5.
  `urban-dev` now creates it.
- **Growth sites (D-130).** Up to two per quarantine region, in courtyards and
  fenced squares: a `GROWTH_NODE` on a `VEIL_MEMBRANE` carpet and the cell where
  its Veilmaw folds; a nine-block territory. Nodes break with an axe for a
  membrane sac; node and membrane burn to ash.
- **The Veilmaw (D-131, D-132).** Folded beside its node, it wakes to touch,
  placed light on a player in its sight, noise and lights on its ground;
  telegraphs for 1.1 s; lunges once at a committed point; recoils exposed;
  walks home. It creeps to a light and smothers a torch, withers when its node
  burns or breaks, heals at its node and never leaves its territory. Its
  strike goes through the damage gates and may bring the existing sickness.
  Counters: crouching, darkness, backing off during the telegraph, a torch
  beside the node, a light as a lure, and blows during the recoil. Its remains
  give two membrane sacs and never meat. It joins the all-living matrix
  (skeleton, twelve fragment pieces, ragdoll, fire, remains), with a hood-fan
  model whose veins glow by pose and its own procedural cues.
- **The Breacher (D-133).** A blind Veilbound variant: touch and sound only,
  longer hearing, slow heavy blows. It plans through closed wooden doors and
  hammers a locked or barred one down in four loud blows, through the
  player's forced-entry edits; metal doors hold.
- **City wildlife (D-134).** Skitterwings, Murkhares and Glowdeer in parks, one
  residential courtyard in three, canal basins and over tall walk-ups, each
  leashed to its habitat. Hunting is optional and finite; nothing respawns.
- **Encounters (D-135).** Catalogue 5 tags anchors with a species and habitat,
  puts Breachers at street doors in three districts, pays for the quarantine's
  Veilmaws from its budget and caps infected, alien organisms and wildlife
  separately (12, 4, 12). A Veilmaw whose node is gone is a tombstone and never
  appears.
- **Persistence (D-136).** `urban.population` codec 2 under catalogue 5 stores
  the species and binds rows to the NPC or creature list; growth state is
  generated blocks plus recorded edits. No new section.
- **Treatment and words (D-137, D-138).** A membrane salve (two poultices from
  a sac and a fibre) in generator 14's recipes; the HUD's "Veil growth" place,
  prompts that teach the counters, and log lines for the warning, strike,
  sting, smother, withering and death.
- **Runtime caps (D-139).** The ecology plan cache (≤ 16) and the active alien
  and wildlife counts are in the runtime snapshot.
- **QA (D-140).** `VEYLON_SCENE=urban_ecology` tours both growth sites, a park,
  a roof flock and a Breacher.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `ai/{VeilmawAI,BreacherAI}.java`, `engine/EcologySounds.java`,
  `world/urban/{EcologySites,UrbanEcologyStamper,UrbanSpecies,Habitat}.java`,
  `UrbanEcologyQaScene.java`, `UrbanEcologyTourPlan.java`.
- Edited: `world/{BlockType,World}.java`,
  `world/urban/{EncounterAnchors,UrbanPlaces,UrbanRevision,UrbanSalts,
  UrbanWorldState}.java`, `entity/{BodyFamily,BodySkeleton,Creature,
  EntityManager,FragmentAnatomy,Npc,UrbanEncounterDirector}.java`,
  `ai/{CreatureAI,IndoorNavigation,InfectedAI,NavigationCache,Pathfinder}.java`,
  `combat/WorldNoise.java`, `engine/{AudioManager,ProceduralAudio}.java`,
  `gfx/{IconAtlas,MaterialRegistry,UrbanTextures}.java`,
  `gfx/model/{Animator,CreatureModels,ModelPart,NpcModels}.java`,
  `item/{CraftingSystem,CreativePalette,ItemType,UrbanCrafting}.java`,
  `settlement/NpcArchetype.java`, `simulation/FireSystem.java`,
  `save/UrbanPopulationSection.java`, `qa/RuntimeBudgetSnapshot.java`,
  `FixtureActions.java`, `InteractPromptBuilder.java`, `WorldInteractions.java`,
  `SpeciesDismemberQaScene.java`, `UrbanRouteScene.java` (a walk may be kept
  to allowed cells), `UrbanQaScenes.java`, `QaHarness.java` (one label on an
  existing line).

**Tests** (`src/test/java/com/veylon/`):

- New: `VeilmawBehaviorTest`, `ai/BreacherBehaviorTest`,
  `world/urban/EcologySitesTest`, `world/UrbanEcologyGenerationTest`,
  `UrbanEcologyPopulationTest`, `save/UrbanEcologySaveTest`,
  `engine/EcologySoundsTest`, `item/MembraneSalveTest`,
  `UrbanEcologyQaSceneTest`, and the fixtures `EcologyArena` and
  `EcologyWorldArena`.
- Extended: `world/WorldGeneratorFingerprintTest` (generator 14),
  `UrbanSliceRouteTest` (generator 14), `qa/{SerializedEnumOrderTest,
  RuntimeBoundsTest}`, `AllLivingBlastDeathTest`, `AllLivingEndToEndTest`,
  `SpeciesDismemberQaSceneTest`, `save/RemainsSectionTest`,
  `entity/FragmentAnatomyTest`.
- Expectations: `world/urban/UrbanRevisionTest`,
  `world/UrbanGeneratorDispatchTest` and `UrbanDevelopmentSelectorTest` name
  generator 14.

**Documents:** `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md` and the urban
DESIGN, DECISIONS, PROGRESS and VALIDATION records.

### Compatibility

- Generators 1–13 are unchanged: their absolute pins pass, catalogues 1–4 keep
  their scan schedule, cap and codec 1, and none of them knows an ecology id,
  a species other than the Veilbound or the salve.
- New: generator 14, layout 11, encounter catalogue 5, `urban.population`
  codec 2, block ids 87–88 (89 of 128 used; 118 of 160 texture layers), item
  `MEMBRANE_SAC`, `CreatureType`/`BodyFamily` `VEILMAW`, `NpcArchetype.BREACHER`
  and the Veilmaw's fragment-piece order, all appended and pinned by
  `SerializedEnumOrderTest`. `CreatureState` values are appended but never saved.
- Unchanged: the v3 base, every section id, content revision 3 and its tables,
  template 7, and the default new world (generator 3).
- An older build rejects a generator-14 save before replacing its live world;
  no older binary was run, and there is no downgrade promise.

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-15--alien-ecology-and-urban-wildlife-2026-10-21-to-2026-10-22).
In short:

- Each code commit was validated against its exact tree in an isolated
  worktree.
- The final portable build passed on `d02462b`'s exact tree on 2026-10-22:
  1,991 tests in 226 classes, zero failures or errors, and the existing
  Windows skip.
- Native `urban_ecology` passed in Survival and Creative on seed `20260731`,
  16 captures each, with zero GL, KHR and native-audio errors. In Survival the
  Veilmaw lunged at the heard step and missed the backing player (no damage).
- No reviewer subagents ran.
- `graphify update . --no-cluster` on the final code: 9,123 nodes and 36,226
  edges, with the existing `build.gradle` extractor warning.

The commits:

| Commit | Subject |
| --- | --- |
| `2855198` | `feat(world): add growth nodes, veil membrane and membrane sacs` |
| `1f84c16` | `feat(alien): add the Veilmaw, a territorial ambush organism` |
| `2da5df8` | `feat(infected): add the Breacher, a blind door-breaking variant` |
| `83cd7d4` | `feat(urban): grow the quarantine and plan city wildlife under generator 14` |
| `c8b65ec` | `feat(encounters): admit Veilmaws, Breachers and city wildlife under catalogue 5` |
| `76d601b` | `feat(crafting): work a membrane salve from Veilmaw sacs in generator 14` |
| `d02462b` | `feat(qa): tour growth sites, city wildlife and a Breacher` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The tour stages admission through the production gate,
  poses between legs, the clock, the weather, the fire stream's seed and a kit;
  it is not the human playtest D-80 defers until after 22. The telegraph's
  readability, the glow at night and the cues were checked by script and
  captures, not judged by a person.
- **Veilmaw movement.** It steers in straight lines inside its open territory
  and never pathfinds (D-131); a territory is open ground by construction.
- **Balance for 23.** Strike, sickness roll, telegraph and lure values,
  Breacher density, park and courtyard wildlife counts (a residential region
  can plan about eleven Murkhares) and the salve are initial values.
- **Presentation.** No map layer for growth or wildlife (21); no ambience bed
  for growth (22). Names are provisional (P-01).
- **Day/night temperature (P-15).** Still inverted in every world; unchanged.
- **Default.** The ordinary new world stays generator 3 until 26.

**Next:** package 16, survivor camps and hidden civilians, starting from the
hidden cellar reservation of package 14 on generator 14's city under a new
generator.

## Milestone 16 — survivor camps and hidden civilians (2026-10-22)

**Status: COMPLETE.** Generator 15 brings people into the city: a camp of
three to six on the reclaimed ground floor of a held walk-up in every region
outside the quarantine, and two to five hidden civilians in every residential
region's sealed cellar. They keep fixed routines on planned cells, live on
their real stores hour by hour whether on screen or not, and judge the player
by deeds alone. A refuge is found through its traces, its people's muffled
sounds and its loose panel. Helping a camp makes it a friend; helping hidden
civilians earns their trust and lets them move to a known camp. The same
people, needs, deaths and standing hold across dormancy, saves and loads.
Native survivor tours pass in both modes. Human-only validation remains
deferred by D-80. Decisions D-141–D-153; D-21 is implemented; P-14 carries a
note; P-15 is still open.

### Start state

- Branch `feature/urban-survival` at `c5b1782` (the 15 records), clean tree;
  the 15 gate met.
- Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records. The work and its commits ran on 2026-10-22 in one session,
  across a context compaction.

### Sites and behaviour

- **Generator 15 (D-141).** Layout 12 is layout 11 plus each region's pure
  survivor-site plan; template 7, content 3 and encounter catalogue 5 are
  unchanged, and sites stamp no block. `urban-dev` now creates it.
- **Where they live (D-142).** A camp holds a held walk-up's ground floor,
  entered by the unlatched rear door (its street door is locked), with a
  lookout's post inside it, a safe cell farthest from it, beds, day cells, a
  kitchen stash and up to five spare beds upstairs: a lookout, a trader, a
  medic when there are four or more, and foragers. A refuge's people rest out
  of reach across the cellar's walls. Courtyard and rooftop camps were not
  built.
- **Registration (D-143).** A site becomes an `UrbanSite` settlement record,
  keyed by its building id, when the player first comes near, steps onto its
  storeys or finds its cellar. No legacy planner, codec or settlement loop
  touches these records; resident index and id come from the planned slot.
- **Activation and discovery (D-144).** Bodies appear on their scheduled cells
  within 96 blocks of their home, measured in three dimensions, and go dormant
  beyond 130. A camp is found by sight within 24 blocks, entry or talk; a
  refuge only by its panel or its cellar.
- **Routines (D-145).** Beds at night with the lookout at the post, meals at
  the stash, day cells otherwise; the frightened gather at the safe cell; a
  lookout fights infected near its post, and a hostile camp's lookout the
  player. They move only along indoor routes between planned cells, and
  talking to or striking one needs a clear line.
- **Needs (D-146).** Hourly lockstep from the first meeting, active or dormant,
  from the real stash containers: hunger, thirst, starvation and death, camp
  foraging, and fear, alarm and trust that fade or drift.
- **Standing (D-147).** Aid, theft, wreckage, attacks, killings, defence and
  trade move trust; harm while trusted is a lasting betrayal; a camp at −40
  turns hostile and at 40 shares its storage, its trader's friend price and its
  medic. What was taken is owed, so no deed can be farmed.
- **Conversation (D-148).** Fixed lines from the site's state; talk, food and
  water, the held item, two finite trades, the medic's dressing once a day,
  word of a camp, a demand for supplies and an attack.
- **The safe move (D-149).** Trusting hidden civilians move, unseen, to a known
  camp's spare beds, leave their shelf to the player and raise the camp's trust
  in the player when they arrive.
- **Persistence (D-150).** `urban.sites` v1, required for generator 15, keeps
  every registered site and its people; it binds live bodies by index and
  fails the whole load on anything malformed.
- **Clues, places and caps (D-151, D-152).** Hidden civilians can be heard near
  their panel (`SurvivorSounds`), never found by it; the HUD names a camp's
  storeys; the site-plan cache and site records are in the runtime snapshot.
- **QA (D-153).** `VEYLON_SCENE=urban_survivors` finds, helps, moves, saves,
  revisits and empties the two sites with ordinary controls.
- **Sleepers on the floor.** The tour showed that the sleep pose drew every
  sleeping person half a block under the floor they slept on, legacy settlers
  beside their camp beds included, so nobody asleep could be seen. The pose now
  rests the body on the floor.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `settlement/{UrbanSite,UrbanSiteDirector,UrbanSiteNeeds,
  UrbanSiteRelations,UrbanSiteDialogue,UrbanSiteRelocation}.java`,
  `ai/UrbanResidentAI.java`, `save/UrbanSitesSection.java`,
  `world/urban/{SurvivorSites,SurvivorStash}.java`, `engine/SurvivorSounds.java`,
  `UrbanSurvivorQaScene.java`, `UrbanSurvivorTourPlan.java`.
- Edited: `world/World.java`, `world/urban/{UrbanRevision,UrbanWorldState,
  UrbanSalts,UrbanContainers,UrbanPlaces}.java`, `settlement/SettlementManager.java`,
  `ai/NpcAI.java`, `entity/{EntityManager,Player}.java`,
  `save/{SaveSystem,UrbanContainersSection,V3ExtensionSections}.java`,
  `simulation/EventSystem.java`, `ui/NpcScreen.java`,
  `engine/{AudioManager,ProceduralAudio}.java`, `gfx/model/Animator.java` (the
  sleep pose), `qa/RuntimeBudgetSnapshot.java`, `Game.java`,
  `CrateTransactionSystem.java`, `FixtureActions.java`, `PlayerCombatSystem.java`,
  `WorldInteractions.java`, `UrbanQaScenes.java`, `QaHarness.java` (one label on
  an existing line).

**Tests** (`src/test/java/com/veylon/`):

- New: `world/urban/SurvivorSitesTest`, `UrbanSurvivorSitesTest`,
  `save/UrbanSitesSaveTest`, `settlement/UrbanSiteRelocationTest`,
  `UrbanSiteDialogueTest`, `UrbanSurvivorCluesTest`,
  `engine/SurvivorSoundsTest`, `gfx/model/SleepPoseTest`,
  `UrbanSurvivorQaSceneTest`, and the fixture `SurvivorWorldArena`.
- Extended: `world/WorldGeneratorFingerprintTest` (generator 15),
  `UrbanSliceRouteTest` (generator 15), `qa/RuntimeBoundsTest`,
  `GameLoopIntegrationTest`, `simulation/UrbanSurfaceSpawnGateTest`.
- Expectations: `world/urban/UrbanRevisionTest`,
  `world/UrbanGeneratorDispatchTest` and `UrbanDevelopmentSelectorTest` name
  generator 15.

**Documents:** `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`,
`docs/audio/AUDIO_DESIGN.md` and the urban DESIGN, DECISIONS, PROGRESS and
VALIDATION records.

### Compatibility

- Generators 1–14 are unchanged: their absolute pins pass, and none of them
  registers a survivor site, writes `urban.sites`, plays the hidden people's
  sounds or names the camp place.
- New: generator 15, layout 12, the section `urban.sites` v1 (required for
  generator 15), the `SurvivorSounds` bank (appended after every earlier bank)
  and the place `UrbanPlaces.Place.CAMP` (never saved). No block, item, enum
  ordinal or persisted id was appended.
- Unchanged: the v3 base, every existing section id and version, content
  revision 3, template 7, encounter catalogue 5 and `urban.population` codec 2,
  the legacy settlement codec, and the default new world (generator 3).
- The sleep-pose fix is presentation only: sleepers in every world are drawn
  lying on their floor instead of under it. No simulation, save or hit-zone
  rule changed.
- An older build rejects a generator-15 save before replacing its live world;
  no older binary was run, and there is no downgrade promise.

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-16--survivor-camps-and-hidden-civilians-2026-10-22).
In short:

- Each code commit was validated against its exact tree in an isolated
  worktree with the full portable build.
- The final portable build passed on `8a15656`'s exact tree on 2026-10-22:
  2,033 tests in 235 classes, zero failures or errors, and the existing
  Windows skip.
- Native `urban_survivors` passed in Survival and Creative on seed
  `20260731`, 16 captures each, with no survivor in a wall or out of its home
  and zero GL, KHR and native-audio errors. The tour also showed every sleeper
  drawn under its floor, in every world; `8a15656` fixes that.
- `graphify update . --no-cluster` on the final code: 9,594 nodes and 38,434
  edges, with the existing `build.gradle` extractor warning.
- No reviewer subagents ran.

The commits:

| Commit | Subject |
| --- | --- |
| `dc1c447` | `feat(urban): plan survivor camps and hidden refuges under generator 15` |
| `660d365` | `feat(settlement): bring survivor camps and hidden refuges to life` |
| `3e82426` | `feat(settlement): trade, threaten and guide survivors in conversation` |
| `1e4dfa3` | `feat(settlement): let hidden civilians be heard and camps be named` |
| `48d9533` | `feat(qa): tour a survivor camp and a hidden refuge` |
| `8a15656` | `fix(render): lay sleepers on the floor instead of under it` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The tour stages poses between legs, a forward-only
  clock, the weather and a kit, and makes its conversation choices and shelf
  transfer through the screens' own commands; it is not the human playtest
  D-80 defers until after 22. Whether the routines read as lived-in, the
  dialogue's tone and the muffled cues were checked by script and captures, not
  judged by a person.
- **Site forms.** Camps only hold reclaimed ground floors; courtyard and
  rooftop camps are not built (D-142). Strongholds in package 17 may revisit
  open-ground sites with walls of their own.
- **The move.** It is persistent relocation while nobody watches, not an
  escort through the streets; no one follows the player.
- **Conversation.** Fixed text with no rumours or quests yet; the relay rumour
  (C1) and the hidden-civilian aid mission belong to package 20.
- **Balance for 23.** Stash sizes, foraging, trust values, trade offers, the
  medic's healing and the threat's yield are initial values.
- **Presentation.** No map layer for sites (21); no camp ambience (22); names
  are provisional (P-01).
- **Day/night temperature (P-15).** Still inverted in every world; unchanged.
- **Default.** The ordinary new world stays generator 3 until 26.

**Next:** package 17, guarded strongholds and hostile factions, building its
guards, access rules and counterattacks on the urban site director and
`urban.sites` (D-143, D-150) under a new generator.

## Milestone 17 — guarded strongholds and hostile factions (2026-10-22 to 2026-10-23)

**Status: COMPLETE.** Generator 16 adds guarded compounds to the inhabited
city: a stronghold of 8–16 people in every civic region, held in a courtyard
block walled by its own buildings with a metal gate, a barricade, posts, a
trader, a clinic and barracks; and a raider yard of 4–8 in an industrial depot
row's service alley within three regions of the spawn, with an unheld back way
into it. A visitor is let in by day to the yard and the clinic; guards who see
them in the barracks warn them and then put them out for the day, and drive
out intruders; theft is judged by what the stronghold's people saw, or by
stores found short after a guard's recent sighting; and alarms are answered by
at most three guards. Raiders
are hostile, send at most two hunters once a day, and a yard whose raiders are
all dead is taken, which a stronghold rewards once. Passes, wanted status,
evidence, missing stores, cooldowns, deaths, standing and capture survive
dormancy, saves and loads without duplicate guards. Native stronghold tours
pass in both modes. Human-only validation remains deferred by D-80. Decisions
D-154–D-168; P-11's default is implemented; P-15 is still open.

### Start state

- Branch `feature/urban-survival` at `9bc3517` (the 16 records), clean tree;
  the 16 gate met.
- Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records. The work and its commits ran on 2026-10-22 and 2026-10-23 in
  one session, across context compactions.

### Compounds and behaviour

- **Generator 16 (D-154).** Layout 13 is layout 12 plus each region's guarded
  compounds; template 7, content 3 and encounter catalogue 5 are unchanged.
  `urban-dev` now creates it.
- **Claims and placement (D-155, D-156).** `CompoundClaims` decides which
  parcels a compound holds before any building is planned, so the route
  catalogue plans them held: street doors locked, rear doors open inward. A
  stronghold takes a civic courtyard block with a clinic and an apartment; a
  raider yard takes the reserved industrial region's depot row near the spawn,
  and one industrial region in four beyond. Camps and encounters skip claimed
  parcels.
- **Ground and people (D-157).** Gate, porch, three-course barricades from
  existing blocks, yard, posts, patrol, trader's pitch, clinic, stash, beds
  spread over rooms, and stable people with roles: gatekeeper, sentries,
  patrols, trader, medic and foragers, or a boss, sentries, a patrol and
  raiders. The gate's lock is a plan default.
- **Sides (D-158).** The urban Wardens and Wreckers, site-local, with their own
  display names; no legacy faction id, name or saved record changed.
- **Duty and senses (D-159, D-160).** Posts, patrols and night watches; the
  gate swung, shut and locked by its guards; a knock answered; sight through
  the shared fair test and occluded hearing; Creative players never perceived;
  raiders' leash and forgetting; infected fought inside the leash and, in
  layout 13, striking back at the guard fighting them.
- **Access (D-161, D-162).** A visitor's pass until 22:00 for the yard and the
  clinic; friends anywhere at any hour; a warning, then the pass lost and the
  player wanted for 12 hours; intruders wanted at once; a wanted player driven
  out while inside. A visitor's own footsteps, doors and knock where they may
  be raise no alarm.
- **Theft, responses, hunts and capture (D-163–D-166).** Caught in sight or
  found short and suspected only with a recent sighting; at most three
  responders for 30 seconds; at most two raider hunters within 48 blocks for 90
  seconds, then a 24-hour cooldown; a yard with no living raider taken, its
  stores nobody's, and one stronghold grateful once.
- **Persistence (D-167).** Compound rows in `urban.sites` v1, validated per
  kind, and the new `urban.compounds` v1, required for generator 16; transient
  warnings, responses and hunts end on load and dormancy.
- **QA (D-168).** `VEYLON_SCENE=urban_strongholds` tours a stronghold, its
  restricted barracks and a threat at its gate, avoids a raider yard through
  its back way, saves and loads, leaves both compounds to go dormant and knocks
  at the gate at night, with ordinary controls. The survivor and stronghold
  tours share `UrbanPeopleScene`.
- **Found by the tour.** A visitor's own footsteps drove a stronghold's alert
  to 100 in half a minute and sent its trader and medic into hiding (D-162).
  On native timing the patrol stood nearer than the trader, and F, which greets
  the nearest person and works an aimed door or window first, greeted her; the
  shared talk step now presses F only while the prompt names its person and
  otherwise steps round (`8a858fc`).
- **Found in review.** The world noises of a guard's blows and gate swings,
  documented as heard, were below every listener's threshold and heard by
  nobody; they were removed (the player still hears both). The theft rule's
  documentation said only guards witness a theft; any of a stronghold's people
  do, and it now says so.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `world/urban/{CompoundClaims,CompoundSites,UrbanCompoundStamper}.java`,
  `settlement/{UrbanFactions,CompoundGate,CompoundAccess}.java`,
  `ai/CompoundResidentAI.java`, `save/UrbanCompoundsSection.java`,
  `UrbanPeopleScene.java`, `UrbanStrongholdQaScene.java`,
  `UrbanStrongholdTourPlan.java`.
- Edited: `world/World.java`, `world/urban/{UrbanRevision,UrbanWorldState,
  UrbanSalts,RouteCatalogue,EncounterAnchors,SurvivorSites,SurvivorStash,
  UrbanContainers,CivicCatalogue,UrbanPlaces}.java`,
  `settlement/{SettlementManager,UrbanSite,UrbanSiteDirector,UrbanSiteNeeds,
  UrbanSiteRelations,UrbanSiteDialogue}.java`,
  `ai/{InfectedAI,UrbanResidentAI}.java`, `entity/{EntityManager,Player}.java`,
  `save/{SaveSystem,UrbanSitesSection,V3ExtensionSections}.java`,
  `ui/NpcScreen.java`, `FixtureActions.java`, `InteractPromptBuilder.java`,
  `UrbanSurvivorQaScene.java` (its shared tasks moved to `UrbanPeopleScene`),
  `UrbanQaScenes.java`, `QaHarness.java` (one label on an existing line).

**Tests** (`src/test/java/com/veylon/`):

- New: `world/urban/CompoundSitesTest`, `UrbanCompoundPeopleTest`,
  `save/UrbanCompoundSaveTest`, `UrbanCompoundAccessTest`,
  `save/UrbanCompoundsSectionTest`, `UrbanStrongholdQaSceneTest`, and the
  fixture `CompoundWorldArena`.
- Extended: `world/WorldGeneratorFingerprintTest` (generator 16),
  `UrbanSliceRouteTest` (generator 16), `qa/RuntimeBoundsTest`,
  `GameLoopIntegrationTest`.
- Expectations: `world/urban/UrbanRevisionTest`,
  `world/UrbanGeneratorDispatchTest` and `UrbanDevelopmentSelectorTest` name
  generator 16.

**Documents:** `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md` and the urban
DESIGN, DECISIONS, PROGRESS and VALIDATION records.

### Compatibility

- Generators 1–15 are unchanged: their absolute pins pass; none of them claims
  a compound, writes `urban.compounds`, names the stronghold or raider-yard
  place, or lets an infected strike back at a guard. The stronghold's
  everyday-sounds rule applies only to compounds.
- New: generator 16, layout 13, the section `urban.compounds` v1 (required for
  generator 16), compound kind codes 3 and 4 in `urban.sites` v1 rows (accepted
  only for generator 16), the places "Guarded stronghold" and "Raider yard"
  (never saved) and the urban side ids `urban.wardens` and `urban.wreckers`
  (site records only). No block, item, enum ordinal or persisted id was
  appended, and no legacy faction changed.
- Unchanged: the v3 base, every existing section id and version, content
  revision 3, template 7, encounter catalogue 5, the legacy settlement codec,
  and the default new world (generator 3).
- An older build rejects a generator-16 save before replacing its live world;
  no older binary was run, and there is no downgrade promise.

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-17--guarded-strongholds-and-hostile-factions-2026-10-22-to-2026-10-23).
In short:

- Each code commit was validated against its exact tree: the full portable
  build, or, for the comment-only `c2575cd`, compile and Javadoc with its
  class file unchanged.
- The final portable build passed on `8a858fc`'s exact tree on 2026-10-23:
  2,071 tests in 241 classes, zero failures or errors, and the existing
  Windows skip.
- Native `urban_strongholds` passed in Survival and Creative on seed
  `20260731`, 16 captures each, with no compound person in a wall or out of
  place and zero GL, KHR and native-audio errors. The first native runs, on
  the tour's own commit, failed in both modes when the stronghold's patrol
  stood nearer than the trader and F greeted her; `8a858fc` fixes the tour.
- `graphify update . --no-cluster` on the final code: 9,991 nodes and
  40,429 edges, with the existing `build.gradle` extractor warning.
- No reviewer subagents ran.

The commits:

| Commit | Subject |
| --- | --- |
| `90ea27d` | `feat(urban): plan guarded strongholds and a raider yard under generator 16` |
| `380ade8` | `feat(settlement): man strongholds and raider yards with guards and raiders` |
| `9b360a0` | `feat(settlement): admit visitors and answer intrusion, theft and raids at compounds` |
| `4ed1957` | `fix(settlement): keep a stronghold calm at a visitor's own footsteps` |
| `64851d5` | `refactor(settlement): drop compound guards' noises that nothing can hear` |
| `c2575cd` | `docs(settlement): say any of a stronghold's people can witness a theft` |
| `44b3e50` | `refactor(qa): share the survivor tour's people tasks in UrbanPeopleScene` |
| `cd63d2d` | `feat(qa): tour a guarded stronghold and a raider yard's back way` |
| `8a858fc` | `fix(qa): press F only when the prompt names the person to talk to` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The tour stages poses between legs, a forward-only
  clock, the weather, one infected and a kit, and makes its conversation
  choices through the screen's own command; it is not the human playtest D-80
  defers until after 22. Whether the guards read as fair, the compounds as
  lived-in and the raider yard as a threat worth avoiding was checked by
  script, logs and captures, not judged by a person.
- **Hostile routes.** The tour shows the raider yard's back way and its
  unlocked rear door but does not go into the yard, fight the raiders or take
  their stores; the hunts, their cooldown and capture rest on the headless
  tests (`UrbanCompoundAccessTest`, `UrbanCompoundSaveTest`).
- **Responses.** Guards answer an alarm from inside their wall and never leave
  the compound; they do not come out to help a gatekeeper fighting on the
  street, and a fight makes no sound anyone hears.
- **Factions.** Wardens and Wreckers are site-local: no standing carries from
  one stronghold to another, and no faction-wide reputation, war or truce
  exists. The stronghold's relay component and the guarded-access mission
  belong to 20.
- **Balance for 23.** People counts, passes, warning grace, trust costs,
  responder and hunter counts, the cooldown, the reward and the raiders'
  stores are initial values.
- **Presentation.** No map layer for compounds (21); no compound ambience
  (22); names are provisional (P-01).
- **Day/night temperature (P-15).** Still inverted in every world; unchanged.
- **Default.** The ordinary new world stays generator 3 until 26.

**Next:** package 18, safehouses, salvage and fortification, building claims,
salvage and barricades of the player's own on the refuge and fixture state
(D-48, D-76) under a new generator.

## Milestone 18 — safehouses, salvage and fortification (2026-10-23 to 2026-10-27)

**Status: COMPLETE.** Generator 17 lets the player make a defensible home of
the city. Up to three refuges can be claimed, each one storey of one building,
at a bed; the claiming bed is where it is slept in, and the refuge last chosen
by sleeping is home, where the survivor wakes after death. Survivors' rooms
cannot be taken while anyone of theirs is left, nothing is confiscated, and a
claimed storey lets no new encounter appear inside it. Furniture, doors, rails,
signs, beds, basins, the workbench and the burnt-out cars now left in the
local streets come apart for planks, scrap, sheet metal, fibre and spare parts,
by time, tool, wear and noise, through one transfer that pays once. Plank and
sheet-metal barricades and door braces made from that salvage board up
openings and reinforce barred doors; a Breacher hammers them down blow by blow,
every other threat walks round, and the player can mend or remove them with no
tool. A refuge's safety is read from its real ways in and light: shut tight it
rests better and draws nothing, left open it may draw a real infected body that
wakes the sleeper. Claims, home, storage, depletion, damaged barricades and
braces survive saves and loads. Native refuge tours pass in both modes.
Human-only validation remains deferred by D-80. Decisions D-169–D-182; the
defaults of P-08 and P-13 are implemented; P-15 is still open.

### Start state

- Branch `feature/urban-survival` at `2012dcd` (the 17 records), clean tree;
  the 17 gate met.
- Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records. The work and its commits ran from 2026-10-23 to 2026-10-27 in
  one session, across context compactions.

### Refuges and behaviour

- **Generator 17 (D-169).** Layout 14 is layout 13 plus the burnt-out cars of
  the local streets, and every rule below is gated on it; template 7, content 3
  and encounter catalogue 5 are unchanged. `urban-dev` now creates it.
- **Burnt-out cars (D-170).** At most six wrecks a region, each one lane wide
  and four cells long with an engine and a cabin, on planned roadway clear of
  crossings, overlays and compounds, so both sidewalks and the other lane stay
  open; one in three burnt out. A new block, `CAR_WRECK`.
- **Salvage (D-171, D-172).** One table for every furniture kind, the doors,
  rails, signs, barricades and each part of a wreck: work time, the tool that
  halves it and must be held for metal, and yields by wear, which is derived
  from the plan. Crouch and hold the attack button; a piece with no item form
  comes apart under any hold. Every blow is heard. What a piece gives and what
  a container still holds must fit before anything moves, and a container's
  contents and identity leave the world once. A survivor's piece wrecks their
  stores. Creative removes the piece and gives no materials. Spare Parts is a
  new item, and the carried-table plank recipe is gone under generator 17.
- **Fortification (D-173–D-176).** A plank barricade (60) and a sheet-metal one
  (160), solid and opaque, go into broken frames or open cells; a door brace
  (90) bars and reinforces a closed door from the player's side. A Breacher
  plans through plank barricades and hammers them and braces at 15 a blow;
  metal holds and every other threat walks round. Planks and scrap repair; a
  salvaged door or window refills a broken frame; a crouched hold takes a
  barricade down from either side with no tool. Only damage is extra state,
  in the new `urban.fortifications` v1.
- **Claims (D-177–D-179).** At most three, each a storey's floor id, claimed with
  crouch and F at a bed; refused for survivors' rooms until their site is
  abandoned, for something hostile on the storey, roofs, stations and unloaded
  parcels. Giving up takes a second crouched F. Home moves to the refuge last
  slept in and is the respawn. A fresh world claims the starter refuge. The
  HUD says "Your refuge", the map marks claims, storage is labelled only where
  nobody else owns it, and no new encounter appears inside a claim.
  `urban.refuges` v2 holds the claims for generator 17.
- **The night (D-180, D-181).** Ways in are the building's outer and roof doors
  and the claimed storey's windows, read live; shut tight means none open or
  unbarred. Shut tight with no light showing rests better and draws nothing;
  otherwise the nearest real infected within 32 blocks of an open way (48 for a
  light at night) comes to look and wakes the sleeper, sooner the nearer it
  was. A body beyond a refuge's walls no longer keeps its sleeper awake when it
  is shut tight, and the refusal now names an infected body as such.
- **QA (D-182).** `VEYLON_SCENE=urban_refuge` claims a flat, stocks it, salvages
  its furniture, crafts and puts up the defences, meets a Breacher and a
  Veilbound, sleeps, saves and loads and is refused a camp's bed, with ordinary
  controls in both modes.
- **Found while writing the tour.** Any infected within 12 blocks kept a city
  sleeper awake through walls, and the refusal called it a hostile survivor, so
  a barred refuge could not be slept in with a body in the street
  (`ab1f4bc`).
- **Found by the native tour.** The HUD shows one line of about 56
  characters, and a salvage hint behind an F action was cut off before saying
  what the piece gives; it now reads "hold LMB for 2 scrap metal" there
  (`ed8999d`). The first native Creative run failed when native frame timing left
  the player standing on the cell the tour meant to put a barricade in; the
  tour now walks to the hall cell first, faces the hammered door for its
  capture and waits for the sleep fade to clear.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `world/urban/{VehicleSites,UrbanVehicleStamper,SalvageTable,
  UrbanFortifications,UrbanRefuges,RefugeExposure}.java`,
  `{Dismantling,Fortification,RefugeSystem}.java`,
  `save/UrbanFortificationsSection.java`, `UrbanRefugeQaScene.java`,
  `UrbanRefugeTourPlan.java`.
- Edited: `world/{World,BlockType,FixtureGeometry}.java`,
  `world/urban/{UrbanRevision,UrbanSalts,UrbanWorldState,UrbanPlaces}.java`,
  `item/{ItemType,UrbanCrafting,CraftingSystem,CreativePalette}.java`,
  `gfx/{MaterialRegistry,UrbanTextures,IconAtlas}.java`,
  `engine/AudioManager.java`, `ai/{Pathfinder,IndoorNavigation,BreacherAI}.java`,
  `entity/{Npc,Player,UrbanEncounterDirector}.java`,
  `save/{SaveSystem,V3ExtensionSections,UrbanRefugesSection}.java`,
  `settlement/UrbanSiteDirector.java`, `ui/MapScreen.java`,
  `qa/RuntimeBudgetSnapshot.java`, `{Game,WorldBootstrap,UrbanBootstrap,
  WorldInteractions,PlayerBlockActions,FixtureActions,InteractPromptBuilder,
  CrateTransactionSystem,SleepSystem}.java`, `UrbanPeopleScene.java` (the
  quick-save failure prints what was logged), `UrbanSurvivorTourPlan.java`
  (its nearest-camp step is shared), `UrbanQaScenes.java`, `QaHarness.java`
  (one label on an existing line).

**Tests** (`src/test/java/com/veylon/`):

- New: `world/urban/VehicleSitesTest`, `UrbanSalvageTest`,
  `UrbanFortificationTest`, `save/UrbanFortificationsSectionTest`,
  `UrbanRefugeClaimsTest`, `UrbanRefugeNightTest`, `UrbanRefugeQaSceneTest`, and
  the fixture `RefugeWorldArena`.
- Extended: `world/WorldGeneratorFingerprintTest` (generator 17),
  `UrbanSliceRouteTest` (generator 17), `save/UrbanRefugesSectionTest`
  (version 2), `qa/RuntimeBoundsTest`, `qa/SerializedEnumOrderTest` (the
  appended blocks and items).
- Expectations: `world/urban/UrbanRevisionTest`,
  `world/UrbanGeneratorDispatchTest` and `UrbanDevelopmentSelectorTest` name
  generator 17.

**Documents:** `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md` and the urban
DESIGN, DECISIONS, PROGRESS and VALIDATION records.

### Compatibility

- Generators 1–16 are unchanged: their absolute pins pass; none of them plans a
  wreck, takes a piece apart, braces a door, keeps a barricade's damage,
  claims a refuge, writes `urban.fortifications` or `urban.refuges` v2, or reads
  a refuge's night (a barricade placed there is an ordinary block). Generators
  9–16 keep `urban.refuges` v1 and their recipes; a Breacher in an earlier
  layout plans and hammers exactly as before. The one change an earlier city
  shows is wording: a sleep refused for a nearby infected body now says
  "Something infected is too close to sleep safely."
- New: generator 17, layout 14, the blocks `CAR_WRECK`, `BARRICADE` and
  `BARRICADE_METAL` (ids 89–91), the items Spare Parts, Plank Barricade,
  Sheet-Metal Barricade and Door Brace (appended), the section
  `urban.fortifications` v1 and `urban.refuges` v2 (both required for, and
  accepted only with, generator 17), the place "Your refuge" (never saved) and
  the vehicle salts. Every enum was appended; no ordinal moved.
- Unchanged: the v3 base, every other section id and version, content revision
  3, template 7, encounter catalogue 5, and the default new world (generator 3).
- An older build rejects a generator-17 save before replacing its live world;
  no older binary was run, and there is no downgrade promise.

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-18--safehouses-salvage-and-fortification-2026-10-23-to-2026-10-27).
In short:

- Each code commit was validated against its exact tree with the full portable
  build in the isolated worktree `build/m14-check`.
- The final portable build passed on `f084ff6`'s exact tree on 2026-10-27:
  2,126 tests in 248 classes, zero failures or errors, and the existing
  Windows skip.
- Native `urban_refuge` passed in Survival and Creative on seed `20260731`, 17
  captures each, with zero GL, KHR and native-audio errors, on the final code.
  The first native Survival run passed, but its captures showed the salvage
  hint cut off; the first Creative run failed at the hall barricade. Both were
  fixed before the final runs.
- `graphify update . --no-cluster` on the final code: 10,464 nodes and
  42,720 edges, with the existing `build.gradle` extractor warning.
- No reviewer subagents ran.

The commits:

| Commit | Subject |
| --- | --- |
| `c771b14` | `feat(urban): leave burnt-out cars in the local streets under generator 17` |
| `4fa6057` | `feat(salvage): take furniture, doors and wrecks apart for materials` |
| `746d896` | `feat(fortification): board up openings, brace doors and mend them` |
| `47abd8b` | `feat(refuge): claim up to three refuges at their beds` |
| `414c248` | `feat(refuge): judge a refuge's night by its real ways in` |
| `ab1f4bc` | `fix(sleep): let a refuge shut tight keep out a body beyond its walls` |
| `ed8999d` | `fix(salvage): keep what a piece gives readable beside an F action` |
| `f084ff6` | `feat(qa): tour a refuge claimed, salvaged, fortified and slept in` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The tour stages poses between legs, a forward-only
  clock, the weather, two infected and a kit (and, in Creative, the materials
  salvage would have given), and uses the container and crafting screens' own
  commands; it is not the human playtest D-80 defers until after 22. Whether a
  refuge feels safe, salvage worth the noise and the threats fair was checked
  by script, logs and captures, not judged by a person.
- **Tour coverage.** The native tour shows a Breacher breaking a braced door
  and a Veilbound kept out; a Breacher hammering a barricade, a Veilbound
  walking round half a barricaded doorway, repairs, a hung door, a glazed
  window, wrecks, the light rule and a body drawn to an open refuge rest on
  the headless tests (`UrbanFortificationTest`, `UrbanSalvageTest`,
  `UrbanRefugeNightTest`).
- **Boarding glass.** A barricade goes into a broken frame or an open cell;
  intact glass must be broken first, and the light rule counts a window only
  by its own cells.
- **Interior doors.** A refuge's exposure counts only the building's
  entrances and the claimed storey's windows; a flat's own door does not secure
  it.
- **Owners.** The salvage prompt does not warn that a piece belongs to
  survivors; the consequence follows the act, with its log line.
- **Balance for 23.** Work times, yields, wear, strengths, blow damage, repair
  amounts, the draw radii and the wake timing are initial values.
- **Presentation.** Map markers for claims only (the plan layer is 21); no
  refuge ambience (22).
- **Day/night temperature (P-15).** Still inverted in every world; unchanged.
- **Default.** The ordinary new world stays generator 3 until 26.

**Next:** package 19, water, power and refuge utilities: one water utility and
one power source with a consumer, run from urban supplies in a claimed refuge,
under a new generator.

## Milestone 19 — water, power and refuge utilities (2026-10-29)

**Status: COMPLETE** (the final portable build and the native tour in both modes
passed later the same day; see "Closing the gate" below). Generator 18 gives
scavenging a practical long-term use in the refuge. A building's taps now hold
only the stale, untreated water left in its pipes, shared by every tap and never
refilled. A generator rebuilt from
salvage runs on fuel siphoned from the tanks of cars that did not burn, feeds the
work lamps and the water purifier of its own building, burns fuel for every
second and every load, wears and is rebuilt with spare parts, and is a real
noise that draws whatever hears it through the walls. A lamp gives light exactly
while it is fed. The purifier, built with its filter spent and repaired by
packing it with fibre, pumps the building's stale water up and treats it into
clean water on the generator's power; water only ever moves. Everything is kept
by `urban.utilities` v1 and survives saves and loads exactly, with no catch-up
or refill. Rain alone keeps a survivor in water, so power is never a condition
of survival. The tour passes headlessly and natively in both modes. Human-only
validation remains deferred by D-80. Decisions D-183–D-196; P-15 is still open.

### Start state

- Branch `feature/urban-survival` at `666dd9c` (the 18 records), clean tree;
  the 18 gate met.
- Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records. The work and its commits ran on 2026-10-29 in one session.

### Utilities and behaviour

- **Generator 18 (D-183).** Layout 15 is layout 14 exactly, every block, plan
  and id; the rules below are gated on it. `urban-dev` now creates it.
- **Taps (D-184, D-185).** A building's planned basins share one finite store
  of stale, untreated water: two litres a basin when intact or held, one when
  picked clean, one for every two when damaged. F fills an empty waterskin with
  it or drinks it as untreated water. It never refills; survivors' taps are
  theirs; a basin put down by hand is still a rain collector.
- **The circuit (D-186).** A building is the service area: one generator to a
  building feeds its four nearest consumers within 16 blocks. Devices go down
  only inside buildings, at most 64 in the world. Nothing is wired by hand and
  nothing is solved city-wide.
- **The generator (D-187, D-188).** Rebuilt at a workbench from three spare
  parts, three scrap and a sheet of metal; three cans of 300 seconds, a quarter
  more burn for each consumer drawing; a point of wear every 15 seconds, seized
  at none, rebuilt 50 a spare part with a pry tool in the pack. Fuel comes only
  from the tank of a car that did not burn. Running, it is a player-attributed
  noise of 28 blocks every three seconds, heard through walls by the existing
  rules; a Creative builder's is heard by nobody. New cues play it starting,
  running and dying.
- **Light (D-189).** A work lamp is registered as a light, and drawn lit, only
  while it is switched on and fed; every change refreshes its light and meshes.
- **The purifier (D-190).** Built with its filter spent; two fibre pack it, its
  repair, worn four points a litre. Fed and switched on, it pumps its building's
  stale water a litre every 10 seconds and treats a litre every 20, tanks of
  eight litres; poured water and filled skins go through the use button.
- **Removal, time and Creative (D-191–D-193).** Nothing carries a device away:
  taking one apart returns most of its parts and a generator's whole cans, and
  any removal leaves its lamps dark at once. Utilities run on the world's own
  seconds, never paused, by real seconds through a sleep, and hold still beyond
  128 blocks or in an unloaded column; nothing is caught up and a load restores
  exactly. Creative spends nothing and is unheard; the world's fuel, wear and
  water run as ever.
- **Saves (D-194).** `urban.utilities` v1, required for generator 18: the tap
  ledger and the devices, every row validated.
- **Scope and sustainability (D-195).** Two consumer kinds, the lamp and the
  purifier-pump; the radio workstation is package 20's, a refrigerator is not
  added. One rain collector emptied once a day keeps a survivor in water on
  every design seed.
- **QA (D-196).** `VEYLON_SCENE=urban_utilities` plays it all in the starter
  refuge with ordinary controls in both modes.
- **Found while writing the tour.** The tour first left the lamps as put down,
  switched off, so starting the generator lit nothing; lamps are now switched on
  with F as soon as they are down, still dark until fed.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `world/urban/UrbanUtilities.java`, `UtilitySystem.java`,
  `save/UrbanUtilitiesSection.java`, `engine/UtilitySounds.java`,
  `UrbanUtilityQaScene.java`, `UrbanUtilityTourPlan.java`.
- Edited: `world/{World,BlockType,Chunk,ChunkMesher}.java`,
  `world/urban/{UrbanRevision,UrbanWorldState,SalvageTable,VehicleSites}.java`,
  `item/{ItemType,UrbanCrafting,CraftingSystem}.java`,
  `gfx/{MaterialRegistry,UrbanTextures,IconAtlas}.java`,
  `engine/{AudioManager,ProceduralAudio}.java`,
  `save/{SaveSystem,V3ExtensionSections}.java`, `qa/RuntimeBudgetSnapshot.java`,
  `{Game,RefugeSystem,WorldInteractions,InteractPromptBuilder,PlayerBlockActions,
  Dismantling,PlayerConsumables,UrbanQaScenes}.java`, `QaHarness.java` (one label
  on an existing line).

**Tests** (`src/test/java/com/veylon/`):

- New: `UrbanTapsTest`, `UrbanPowerTest`, `UrbanPurifierTest`,
  `UrbanUtilitySustainabilityTest`, `UrbanUtilityQaSceneTest`,
  `save/UrbanUtilitiesSectionTest`, `engine/UtilitySoundsTest`, and the fixture
  `UtilityWorldArena`.
- Extended: `world/WorldGeneratorFingerprintTest` (generator 18),
  `UrbanSliceRouteTest` (generator 18), `qa/RuntimeBoundsTest` (the tap and
  device ledgers), `qa/SerializedEnumOrderTest` (the appended blocks and items).
- Expectations: `world/urban/UrbanRevisionTest`,
  `world/UrbanGeneratorDispatchTest` and `UrbanDevelopmentSelectorTest` name
  generator 18.

**Documents:** `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`,
`docs/audio/AUDIO_DESIGN.md` and the urban DESIGN, DECISIONS, PROGRESS and
VALIDATION records.

### Compatibility

- Generators 1–17 are unchanged: their absolute pins pass; none of them has
  taps, devices, fuel in a wreck or `urban.utilities`. A generator, lamp or
  purifier block a Creative player sets down in an earlier world is an inert
  block, and a lamp there never lights.
- New: generator 18, layout 15, the blocks `GENERATOR`, `ELECTRIC_LAMP` and
  `WATER_PURIFIER` (ids 92–94), the items Fuel Can, Generator, Work Lamp and Water
  Purifier (appended), the section `urban.utilities` v1 (required for, and
  accepted only with, generator 18), four texture layers (126 of 160) and three
  audio buffers. Every enum was appended; no ordinal moved.
- Unchanged: the v3 base, every other section id and version, content revision
  3, template 7, encounter catalogue 5, and the default new world (generator 3).
- An older build rejects a generator-18 save before replacing its live world;
  no older binary was run, and there is no downgrade promise.

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-19--water-power-and-refuge-utilities-2026-10-29).
In short:

- The first four code commits were validated with the full portable build on
  their exact trees: `7258c34` 2,139 tests in 250 classes, `d4c5d7b` 2,155 in
  252, `4c93639` 2,163 in 253 (each in the main tree while it held exactly that
  commit) and `640bf67` 2,165 in 254 (in the isolated worktree
  `build/m14-check`), each with no failures and the existing Windows skip.
- The tour commit `def0278` was first checked only by `UrbanUtilityQaSceneTest`
  (Survival and Creative pass, 13 captures each, and generator 17 refused).
- A first native Survival run reached ten of its thirteen captures and ended
  with a clean exit when the owner stopped testing; the gate was closed later
  that day (below).
- `graphify update . --no-cluster` on the final code: 10,769 nodes and 44,168
  edges, with an extractor warning for `settings.gradle`.
- No reviewer subagents ran.

The commits:

| Commit | Subject |
| --- | --- |
| `7258c34` | `feat(water): keep a utility city's taps to the stale water left in their pipes` |
| `d4c5d7b` | `feat(power): run a refuge's work lamps from a salvaged generator` |
| `4c93639` | `feat(water): treat a refuge's stale water in a powered purifier` |
| `640bf67` | `test(utilities): prove a utility city's rain keeps a survivor in water` |
| `def0278` | `feat(qa): tour a refuge powered, lit and supplied with treated water` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The tour stages poses between legs, a forward-only
  clock, the weather, one infected and a kit (salvage for the bench, a spare
  part, a pry tool, fibre and empty waterskins, and in Creative the fuel can
  salvage would have given), and builds through the crafting screen's own
  command; it is not the human playtest D-80 defers until after 22. Whether
  power is worth its noise, the purifier worth its fibre and the taps' water
  worth boiling was checked by script, logs and captures, not judged by a
  person.
- **Tour coverage.** The native tour shows the wear rebuilt after an evening's
  running, not a seized generator; seizing, the load cap, reach, a second
  generator refused, removal leaving lamps dark, dormancy, pause, clogged
  filters, spills and survivors' pipes rest on the headless tests
  (`UrbanPowerTest`, `UrbanPurifierTest`, `UrbanTapsTest`).
- **Moving devices.** Nothing carries a device away whole; moving one costs
  part of its salvage.
- **Noise model.** The generator's noise is the existing direct-ray hearing:
  walls cost eight blocks each, and it is not propagated round a building.
- **Sleep.** A night's sleep costs only its few real seconds of fuel, as for
  lanterns and fires; a generator left running through the night lights the
  refuge and shows in its night rule.
- **The Veilmaw's lure** seeks torches, lanterns and fires, not lamps, though a
  lit lamp's light wakes it.
- **Balance for 23.** Fuel per can, burn, wear, repair, filter life, pump and
  treatment rates, tap stores and recipe costs are initial values; rain water is
  plentiful.
- **Presentation.** The lamp reuses the lantern's glass; the generator and
  purifier have their first tiles; machinery ambience is 22.
- **Day/night temperature (P-15).** Still inverted in every world; unchanged.
- **Default.** The ordinary new world stays generator 3 until 26.

### Closing the gate (2026-10-29)

Resumed at the start of package 20, with the branch at `ef29fe8` (records only on
top of `def0278`, so the code is the tour commit's):

- The portable build on `ef29fe8`'s exact tree in the isolated worktree
  `build/m14-check` passed in 14 min 7 s: 2,168 tests in 255 classes, zero
  failures or errors and the existing Windows skip (`m19-final-build.log`).
- Native `urban_utilities` on seed `20260731` printed `pass=true` in Survival
  (205 route seconds) and in Creative (202), 13 captures each, every capture
  `glErrors=0 khrErrors=0`, `nativeErrors=0`, each in a QA directory checked not to
  exist beforehand; all thirteen Survival captures and the Creative wreck, the
  unheard generator and the reload were inspected
  ([VALIDATION.md](VALIDATION.md#native-utility-tour-both-modes)).

**Next:** package 20, urban missions and long-term purpose: the communications
chain and its radio workstation on the refuge circuit, and a modest mission
catalogue, under a new generator.

## Milestone 20 — urban missions and long-term purpose (2026-10-29 to 2026-10-30)

**Status: COMPLETE.** Generator 19 gives the city a long-term goal and its
survivors work to offer. One old relay mast stands on a precinct roof. Its
rumour comes from anyone of a camp, a stronghold or a hidden refuge; its three
parts lie in two independent kinds of source each across the districts; a
friendly stronghold or trusting hidden civilians share its codes, which only
shorten the call; the parts are fitted at its cabinet and a radio workstation,
built round the transmitter module, goes on the refuge's own wiring. On the
generator's power the radio carries a call, a real noise every three seconds,
until the city answers once: the survivors the player knows trust them more and
the existing ending card shows, from which Enter or Escape carries on in the
same world. Beside the chain, camps and strongholds give five kinds of mission,
each naming a real object, counting only the deed itself on that object,
rewarded once and failing explicitly and for good; the journal holds four. A
fixed route reaches the city's answer from the city's own stores, people and
wrecks with no tree or ore, through a mission that visits a hidden refuge and
one through a stronghold's guarded stores, and a save and a load repeat
nothing. The native tour passes in both modes. Human-only validation remains
deferred by D-80. Decisions D-197–D-208; P-01 and P-07 carry package-20 notes;
P-15 is still open.

### Start state

- Branch `feature/urban-survival` at `ef29fe8` (the 19 records), clean tree.
  Package 19's gate was closed first (`ff1edff`, above).
- Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records. The work and its commits ran on 2026-10-29 and 2026-10-30.

### Missions and behaviour

- **Generator 19 (D-197).** Layout 16 is layout 15 plus one relay mast; every
  generator-18 rule holds in it. `urban-dev` now creates it.
- **The relay (D-198).** Planned from the seed and the frozen plans: the civic
  region nearest the spawn, the roof of its tallest unheld office block outside
  every compound (else a school, security post or clinic), an open roof cell near
  the stair door. An unbreakable cabinet under a three-cell guard-rail mast.
- **C1 and C3, the rumour and the codes (D-200).** "[9] Ask about the old radio
  relay" names a district and a compass direction, never the building; standing
  beside the relay finds it too. A friendly stronghold, or hidden civilians who
  trust the player 30 or more, share the codes; without them the call takes 240
  seconds instead of 60. Later answers point at the parts still missing.
- **C2, the parts (D-199).** A transmitter module (security posts' armoury
  lockers; every stronghold's stores), power cells (warehouse tool chests; clinic
  utility lockers) and antenna cable (office utility lockers; roof maintenance
  lockers), each in the first roll of its container, never put back.
- **C4, the repair and the radio (D-201).** The use button at the cabinet fits
  each part (a pry tool in the pack in Survival); a radio workstation, built at a
  workbench from the module, a spare part and two scrap, is a fourth device kind
  on its building's wiring and a load while on.
- **C5, the call and the answer (D-202).** Switched on and fed in a building the
  player holds, with the relay repaired, the radio counts the generator's running
  seconds and is a player-attributed noise of 32 blocks every three seconds
  (nobody hears Creative). When the call is long enough the city answers once,
  known friendly camps and strongholds trust the player 10 more, and the existing
  ending card shows with urban text. The audit of `AppState.VICTORY`: the card
  pauses the simulation, Enter continued and Escape went to the title; in a city
  Escape continues too, and a load never shows the card again. The legacy beacon
  is unchanged.
- **Missions (D-203–D-206).** "[0] Ask if they need help": a camp gives, in turn,
  medicine from a named clinic, help for the hidden people near it, news of the
  relay and clean water for its own building; a stronghold offers its module (six
  supplies, or taken by a friend). Evidence is only the deed on the named object
  after accepting; reports pay trust once and the clinic's two medicines go into
  the camp's stash. Failures (givers dead, gone or hostile; stores or container
  destroyed; the hidden people dead or betrayed; the module stolen) are logged
  once and final. Four in hand, 256 records; the HUD and map show the first in
  hand before the objective.
- **Saves (D-207).** `urban.missions` v1, required for generator 19: the
  objective's facts and every record, cross-checked; anything else fails the
  load.
- **QA (D-208).** `VEYLON_SCENE=urban_missions` plays the chain and three
  missions with ordinary controls in both modes.
- **Found while writing the tour.** Aiming at the relay cabinet's centre from its
  stand cell hits the mast's bottom rail, so the tour aims at the cabinet's face
  toward the stand, as a player would. The tour first put the radio down out of reach and now reuses the utility tour's
  generator and lamp cells, and its report first left the native run open; it
  now closes it like the other tours.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `world/urban/{RelaySites,CommsComponents,UrbanMissions}.java`,
  `settlement/{UrbanComms,UrbanMissionBoard}.java`, `CommsObjective.java`,
  `save/UrbanMissionsSection.java`, `UrbanMissionQaScene.java`,
  `UrbanMissionTourPlan.java`.
- Edited: `world/{World,BlockType}.java`,
  `world/urban/{UrbanRevision,UrbanWorldState,UrbanSalts,UrbanContainers,UrbanUtilities,SalvageTable}.java`,
  `item/{ItemType,UrbanCrafting,CraftingSystem,CreativePalette}.java`,
  `gfx/{MaterialRegistry,UrbanTextures,IconAtlas}.java`,
  `save/{SaveSystem,V3ExtensionSections,UrbanUtilitiesSection}.java`,
  `settlement/{UrbanSiteDialogue,UrbanSiteRelations,UrbanSiteRelocation}.java`,
  `ui/{Hud,NpcScreen,QuestObjectiveView,PresentationOverlay}.java`,
  `qa/RuntimeBudgetSnapshot.java`,
  `{Game,RefugeSystem,UtilitySystem,WorldInteractions,InteractPromptBuilder,
  PlayerBlockActions,CrateTransactionSystem,UrbanQaScenes}.java`, `QaHarness.java`
  (one label on an existing line).

**Tests** (`src/test/java/com/veylon/`):

- New: `world/urban/RelaySitesTest`, `UrbanCommsTest`, `UrbanBroadcastTest`,
  `UrbanMissionsTest`, `save/UrbanMissionsSectionTest`, `UrbanMissionRouteTest`,
  `UrbanMissionQaSceneTest`.
- Edited: `world/WorldGeneratorFingerprintTest`, `world/UrbanGeneratorDispatchTest`,
  `world/urban/UrbanRevisionTest`, `UrbanDevelopmentSelectorTest`,
  `UrbanSliceRouteTest`, `qa/SerializedEnumOrderTest`, `qa/RuntimeBoundsTest`.

**Records:** this file, [DESIGN.md](DESIGN.md) (§3.3, §10, §12, §13),
[DECISIONS.md](DECISIONS.md) (D-197–D-208, §3, P-01, P-07),
[VALIDATION.md](VALIDATION.md#milestone-20--urban-missions-and-long-term-purpose-2026-10-29-to-2026-10-30),
`docs/ARCHITECTURE.md` and `docs/DEVELOPING.md`.

### Compatibility

- Generators 1–18 are unchanged: their absolute pins pass, and none of them has a
  relay, parts in its containers, missions or `urban.missions`. A radio is refused
  in a generator-18 city, and the relay is not in the Creative palette.
- New: generator 19, layout 16, the blocks `RELAY` and `RADIO` (ids 95–96), the
  items Transmitter Module, Power Cells, Antenna Cable and Radio Workstation
  (appended), the section `urban.missions` v1 (required for, and accepted only
  with, generator 19), radio rows in `urban.utilities` v1 (generator 19 only) and
  four texture layers (130 of 160). Every enum was appended; no ordinal moved. No
  audio buffer was added: the radio and the fitting reuse existing cues.
- Unchanged: the v3 base, every other section id and version, content revision
  3, template 7, encounter catalogue 5, the legacy beacon quest and its card, and
  the default new world (generator 3).
- An older build rejects a generator-19 save before replacing its live world; no
  older binary was run, and there is no downgrade promise.

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-20--urban-missions-and-long-term-purpose-2026-10-29-to-2026-10-30).
In short:

- Every code commit was validated with the full portable build on its exact
  tree in the isolated worktree `build/m14-check`, with no failures and the
  existing Windows skip: `71cf2ca` and `6d5a317` (VALIDATION has their counts),
  `cc874ac` 2,200 tests in 260 classes, `3888a28` 2,201 in 261 and `b8967b8` 2,204 in 262.
- Native `urban_missions` on seed `20260731` passed in Survival and Creative, 15
  captures each, with zero GL, KHR and native-audio errors.
- `graphify update . --no-cluster` on the final code: 11,169 nodes and 46,334
  edges, with an extractor warning for `settings.gradle`.
- No reviewer subagents ran.

The commits:

| Commit | Subject |
| --- | --- |
| `ff1edff` | `docs(urban): close package 19's gate with the final build and native tours` |
| `71cf2ca` | `feat(urban): raise a relay mast on a precinct roof under generator 19` |
| `6d5a317` | `feat(comms): restore a city's communications through its old relay mast` |
| `cc874ac` | `feat(missions): give camp and stronghold missions on real city objects` |
| `3888a28` | `test(missions): restore a city's comms from its own stores, people and wrecks` |
| `b8967b8` | `feat(qa): tour the missions and the relay until the city answers` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The tour stages poses between legs (into the Wardens'
  stores too), a forward-only clock, the weather and a kit (food to share, a pry
  tool, salvage for the bench and a fuel can) and builds through the crafting
  screen's command; the route test gathers everything in the city but stages
  poses and puts down infected that come near. Neither is the human playtest D-80
  defers until after 22: whether the missions feel worth their walk, the relay
  worth its climb and the ending worth the wait was checked by script, logs and
  captures, not judged by a person.
- **Tour coverage.** The tour reports two missions and leaves the clinic's open;
  the clinic's medicine, the camp's water, the Wardens' price, every failure, the
  band scan without codes, the refuge's codes, a broadcast cut by a power loss
  and the save rules rest on the headless tests (`UrbanMissionsTest`,
  `UrbanCommsTest`, `UrbanBroadcastTest`, `UrbanMissionsSectionTest`).
- **Navigation.** A hidden refuge's mission points the map at the building above
  it; the panel must still be found, and whether that says too much is for 21's
  onboarding review.
- **Infiltration.** Stealing the module from the Wardens' stores is the existing
  intrusion and theft; no separate tunnel or roof route to it is planned.
- **One relay.** A world plans exactly one; the call needs it repaired.
- **Text.** Giving supplies to a stronghold logs "The camp takes in your
  supplies.", a package-16 line package 17 did not adjust; left for 21's text and
  HUD pass.
- **Balance for 23.** Prices, rewards, broadcast seconds, noise and part
  placement are initial values.
- **Presentation.** The relay and radio have their first tiles and reuse
  existing cues; a radio's own sound and the city's answer as audio are 22.
- **Day/night temperature (P-15).** Still inverted in every world; unchanged.
- **Default.** The ordinary new world stays generator 3 until 26.

**Next:** package 21, map, HUD and onboarding: a new player finds supplies,
floors, the refuge and the next step (now including the relay, the parts and
the missions) through ordinary UI, with secrets kept secret.

## Milestone 21 — map, HUD and onboarding (2026-10-30)

**Status: COMPLETE.** A new survivor in a generator-19 city can now read their
way through it with ordinary UI. M opens a city map: the streets around them as
far as the city has been generated near them, the buildings they have entered
named by what they are, their refuges and home, the camps, strongholds and
raider yards they have found, and hidden people only once their cellar is found;
the objective and every mission in hand, a known place as a ring and a rumour as
a rough dashed area. Beside it a journal reads the saved ledger (the radio's
next step, each mission's concrete objective, who it is for and where, the last
finished or failed with the reason, and first steps for a new survivor), and a
sketch of the storey underfoot shows its stairs, ways out, live door states and
the rooms they have entered, never a container or an unfound cellar. The HUD
names the district and the building and floor. Interaction lines say what a
container is, whose it is and what searching costs; locks name the control and
the tool, and the city's pry tool is named so in the pack. The storage and
conversation screens show owner, progress, condition and whole answers; NEW
FRONTIER, the pause menu and the first session speak of the city. Secrets stay
secret: what the map knows comes from the survivor's own walking
(`urban.visits`, new and optional) and the existing discovery ledgers, and two
mission markers that pointed at secret or unfound places are now rumours until
confirmed. The native tour passed at 720p and 1080p, at 0.75, 1 and 1.5 scale,
by day, at night and in fog, in both modes. Human-only validation remains
deferred by D-80. Decisions D-209–D-218; P-05 carries a package-21 note; P-15 is
still open.

### Start state

- Branch `feature/urban-survival` at `e764168` (the 20 records), clean tree.
- Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md`,
  these records, `docs/graphics/HUD_VALIDATION.md` and the save-compatibility
  skill. The work and its commits ran on 2026-10-30.

### Map, HUD and onboarding

- **Where it applies (D-209).** Generator 19, the city of package 20; earlier
  cities and the wilderness keep their map, HUD and hints. Corrections reach
  every world where their screen appears: the wider interaction box, lock lines
  that name the control and the tool, the city's "Pry Tool", no take-apart hint
  on a closed door, wrapped answers, the storage screen's owner, progress and
  condition, and a city's pause menu.
- **What the survivor walked and entered (D-210).** `UrbanVisits` records, as
  the feet cell changes in a loaded column, the district underfoot and a
  building only from inside one of its rooms; nothing planned, generated, seen
  from the street or told counts. It is saved as the new optional section
  `urban.visits` v1, bounded at 1,024 districts and 4,096 buildings.
- **The city map (D-211).** M opens a 128 m ground plan of the streets around
  the survivor where the city was generated near them, the buildings they
  entered named by family (others are unnamed blocks), the edges and names of
  the other districts walked, home and the other refuges, camps, strongholds
  and raider yards once found, hidden people only once their cellar is found,
  and the objective and every mission in hand: a known place as a ring, a rumour
  as a dashed area with "Rumour:", away ones at the edge with their distance.
  Never a container. The HUD behind it is dimmed and the world keeps running.
- **Rumours (D-212, refines D-206).** A hidden-people mission marks the middle
  of their city block until their cellar is found; the relay's mission marks
  its district until the survivor stands beside it; the navigation line writes
  "~120m NE (rumour)". Giving supplies to the Wardens no longer logs "the camp".
- **The journal (D-211, D-217).** The radio's next step and where it points;
  each mission in hand with its concrete objective, who it is for and where; the
  last two finished or failed with the reason; for a new survivor, "First
  steps", "Your refuge" and "Survivors". It reads the saved ledger, so a load
  shows exactly what is true.
- **The floor sketch (D-213).** Inside a building, the storey underfoot: its
  outline, stairs up and down under one label, doors with their live state and
  outer ones and fire escapes as ways out, the survivor and their facing, a strip
  of the storeys with the one underfoot lit, and only the rooms entered this
  session; an unfound hidden cellar and its panel are never drawn.
- **The HUD (D-214).** The field panel shows the district and "Clinic · floor 2"
  (or "your refuge", "survivor camp", "hidden cellar", "Street", "Transit
  tunnel"); the empty navigation line reads "[M] Map & journal".
- **Interactions (D-215).** A container's line names its use, its owner and
  what searching costs or how far it got, touching nothing; locks name the
  control and the tool; the interaction box is 580 px wide.
- **Screens (D-216).** The storage screen's owner line warns of theft in amber,
  a search shows a progress bar and its noise, slots show wear and freshness,
  the item under the cursor is described; conversation answers wrap.
- **Onboarding text (D-217).** Five short first-session lines (Creative four),
  each whole on the HUD's log; NEW FRONTIER describes the city when the isolated
  development selector makes the session's new worlds cities, with the replace
  notice unchanged and no user-visible selector; a city's pause menu.
- **QA (D-218).** `VEYLON_SCENE=urban_onboarding`.
- **Found while touring it.** The first native captures showed the stair labels
  colliding ("up" over "down"), truncated room names in the sketch, the
  current district's label colliding with an edge marker, clipped edge labels,
  "[M]" inside the journal and the slot skin's teal corner under the journal's
  heading; all were fixed before the map's commit and the tours rerun. A cupboard
  in a city named the iron pickaxe "pry tool" while the pack said "Iron Pickaxe",
  and a closed door offered a take-apart hint that crouching there refuses; both
  are fixed for every city.

### Changed paths

**Production** (`src/main/java/com/veylon/`):

- New: `world/urban/UrbanVisits.java`, `save/UrbanVisitsSection.java`,
  `ui/{CityMap,CityMapView,FloorSketch}.java`, `item/ItemNames.java`,
  `UrbanOnboardingQaScene.java`.
- Edited: `entity/Player.java`, `world/urban/UrbanWorldState.java`,
  `save/V3ExtensionSections.java`, `qa/RuntimeBudgetSnapshot.java`,
  `settlement/{UrbanMissionBoard,UrbanComms,UrbanSiteRelations}.java`,
  `ui/{QuestObjectiveView,MapScreen,Hud,HudLayout,CrateScreen,NpcScreen,InventoryScreen,CraftingScreen,NewFrontierScreen,PauseMenu}.java`,
  `{FixtureActions,Fortification,InteractPromptBuilder,CrateTransactionSystem,Dismantling,FrontendController,WorldBootstrap,UrbanQaScenes}.java`,
  `QaHarness.java` (one label on an existing line).

**Tests** (`src/test/java/com/veylon/`):

- New: `UrbanVisitsTest`, `save/UrbanVisitsSectionTest`, `UrbanMissionRumourTest`,
  `ui/{CityMapTest,FloorSketchTest,CityMapLayoutTest,CrateScreenToneTest,CityOnboardingScreensTest}`,
  `UrbanInteractionPromptsTest`, `UrbanFirstSessionHintsTest`,
  `UrbanOnboardingQaSceneTest`.
- Edited (the older lock wording): `UrbanCivicGameplayTest`,
  `UrbanFixtureGameplayTest`, `UrbanFortificationTest`.

**Records:** this file, [DESIGN.md](DESIGN.md) (§3.1, §12, §13),
[DECISIONS.md](DECISIONS.md) (D-209–D-218, §3, P-05, D-206's pointer),
[VALIDATION.md](VALIDATION.md#milestone-21--map-hud-and-onboarding-2026-10-30),
`docs/ARCHITECTURE.md`, `docs/DEVELOPING.md` and
`docs/graphics/HUD_VALIDATION.md`.

### Compatibility

- No generator, layout, template, content revision or encounter catalogue
  changed; no block, item, enum constant, ordinal, texture layer, icon or audio
  buffer was added. Generators 1–19 keep their geometry and pins; the default
  new world stays generator 3.
- New: the optional section `urban.visits` v1, written only for generator 19 and
  rejected by an earlier city. A generator-19 save of package 20 has none and
  loads with nothing visited; a package-20 build skips the unknown section and
  loads the world (re-saving there drops it). No older binary was run.
- Earlier cities and the wilderness keep their map, HUD panel and first-session
  lines; the corrections of D-209 reach them where their screens appear, and the
  wilderness's wording ("Requires pickaxe", the beacon's reference) is unchanged.

### Commands and results

Exact commands and outputs are in
[VALIDATION.md](VALIDATION.md#milestone-21--map-hud-and-onboarding-2026-10-30).
In short:

- Every code commit was validated with the full portable build on its exact
  tree in a detached worktree, with no failures and the existing Windows skip:
  `4ee6b89` 2,212 tests in 264 classes, `2749358` 2,215 in 265, `8f0d89b`
  2,227 in 268, `d762cdf` 2,232 in 270, `1df1e21` 2,237 in 272 and `dd121c1`
  2,240 in 273. The last four were built in parallel worktrees, each tree
  assembled from the one before it and compared with the main index.
- The native onboarding tour passed at 1280 × 720 (scale 1 and 0.75) and
  1920 × 1080 (scale 1, 1.5 and 0.75), in Survival and Creative, 14 captures
  each with zero GL and KHR errors; the new-world and pause screens were captured
  with and without the selector.
- `graphify update . --no-cluster` on the final code: 11,423 nodes and 47,599
  edges, with extractor warnings for `build.gradle` and `settings.gradle`.
- No reviewer subagents ran.

The commits:

| Commit | Subject |
| --- | --- |
| `4ee6b89` | `feat(urban): remember the districts walked and buildings entered` |
| `2749358` | `fix(missions): mark a mission's unconfirmed places as rumours` |
| `8f0d89b` | `feat(map): draw a city map with a journal and a sketch of the storey` |
| `d762cdf` | `feat(ui): explain urban interactions, owners and search progress` |
| `1df1e21` | `feat(frontend): describe the city on the new-world and pause screens` |
| `dd121c1` | `feat(qa): tour what a new survivor reads, from the refuge to a mission` |

These records follow in their own commit. No push, merge, tag or publication
was performed.

### Limitations and next concrete step

- **Evidence types.** The tour stages two poses, a forward-only clock, fog and a
  spear; the headless tests stage positions. Whether a new player actually finds
  their way is the human playtest D-80 defers until after 22; it was checked by
  script, tests and captures, not judged by a person.
- **The map's reach.** It shows 128 m around the survivor, only where the city
  was generated near them; it cannot pan or zoom, and a rumour of the relay's
  district is usually an edge marker with a distance.
- **The sketch.** Only the storey underfoot; other storeys are not browsed. Rooms
  are remembered for this session only (64 floors), so after a load they are
  sketched again as they are entered; the outline, stairs and ways out always
  show.
- **What the map does not know.** A searched or picked-clean building is not
  marked; buildings are named by family, not by their signs.
- **One line per prompt.** With a usable item in hand at a device, a part at the
  relay or a fortification item at a door, the line is the use button's; F's
  action still works but is not shown beside it.
- **Hover details.** The storage screen describes the item under the cursor; the
  scripted tour cannot move the mouse, so no capture shows it.
- **Earlier cities** (generators 4–18) keep the wilderness-style map, their
  field panel and their first lines.
- **Day/night temperature (P-15).** Still inverted in every world; unchanged.
- **Default.** The ordinary new world stays generator 3 until 26.

**Next:** package 22, ruined-city visuals and sound: districts, sites and threats
recognisable in day, night and fog, bounded props, lights and audio, a working
dry fallback; after it the consolidated human playtest (D-80).

## Resume rules

- D-80 governs human validation timing: defer human-only gates until after 22 and carry them explicitly in handoffs. These owner-authorized deferrals permit implementation to advance through 22; other unmet gates retain the blocking rules below. Do not count scripted native evidence as a human pass.
- On resume, read `00_BASE_KNOWLEDGE.md`, the current package prompt, the sections of these four records that the prompt names, and current source. Do not reload every plan.
- Continue a PARTIAL package before starting the next. Record the exact resume step whenever a package spans sessions.
- Each package handoff appends a section here containing: id; status; implemented behaviour; changed paths; stable-id and schema decisions; exact commands and results; native evidence; limitations; next concrete step. The package also updates its ledger row.
- An unrun or failed mandatory gate stays pending. Historical evidence never closes it.

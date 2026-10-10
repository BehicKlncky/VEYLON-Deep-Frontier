# Urban Survival — Progress

This is the running record for the 26-package urban-survival roadmap. The pack is the `VEYLON Urban Survival — Agent Prompt Pack`, kept outside the repository, with `00_BASE_KNOWLEDGE.md` and prompts `01`–`26`. This file holds status, owners, gates and handoffs. The design is in [DESIGN.md](DESIGN.md), the decisions in [DECISIONS.md](DECISIONS.md), and the evidence in [VALIDATION.md](VALIDATION.md).

## Current status

| Item | Value |
| --- | --- |
| Last completed package | **05 — Building plans and accessibility** (2026-10-10). Deterministic apartment/shop proposals have bounded, physical entry/exit validation, stable reservations and finite repair. Generated building stamping and furnished exploration remain 06. |
| Source revision | Milestones 01–04 are committed on `feature/urban-survival`, ending at `bdecf6f`; their reconstructed implementation matched `e452dc1`. Milestone 05 starts from that clean checkpoint and has its own scoped handoff below. |
| Default new world | Legacy (`World.DEFAULT_NEW_WORLD_GENERATOR = GEN_CAVE_IDENTITIES`), unchanged until 26 passes. Isolated `VEYLON_GENERATOR=urban-dev` now creates generator 6 (`GEN_URBAN_PLANS`, layout 3 / template 1, with layout-2 streets stamped alone). Generators 4 and 5 stay loadable. |
| Next package | **06 — Generated apartments and interiors**. Start from the completed template-1 proposals and preserve generator 6; see the concrete handoff below. |
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
| 06 | Generated apartments and interiors | 04–05 | `UrbanChunkStamper`; `World` height and light hooks; `simulation/TemperatureSystem`, `AmbienceSystem`, `AudioSceneState` urban indoor state (D-19); `UrbanBootstrap` | Generated apartments and a shop have recognisable rooms, usable stairs, matching windows and exits. Blocks and ids are identical in every order. Persisted breaks survive. Native street-to-basement walks in day and night. | NOT STARTED |
| 07 | Contextual loot and persistence | 06, 02 | New `UrbanContainers`, `LootTables`, `urban.containers`; `CrateTransactionSystem`, `ui/CrateScreen`, `simulation/ItemConditionSystem`, one removal path for hand, blast, fire and salvage | Open, partially loot, leave, reload, revisit, break and replace: no refill or duplication. Abandoned property is not theft; owned property has a consequence. Interrupted search, full-inventory and corruption tests. Native searching while the world runs. | NOT STARTED |
| 08 | Urban spawn and survival progression | 07 | `UrbanBootstrap` (spawn, refuge, provisional camp), `item/CraftingSystem` urban recipe set, `item/ItemType` (appended), `WorldInteractions` fuel, water and cooking, `SleepSystem`, onboarding text | Fixed starter seeds reach shelter, food, water, treatment, sleep and a first upgrade from urban sources only, on a production route with no tree and no ore. Creative bootstrap is intact. A fresh native session. | NOT STARTED |
| 09 | Infected threats and encounters | 08 | Entity kind (D-20, appended enums), a new AI brain, `gfx/model`, `engine/AudioManager`, new `UrbanEncounterDirector` and `urban.population`, caps in `qa/RuntimeBudgetSnapshot` | Infected notice the player fairly, pursue in a fixture, attack through the damage gates, react to fire and die cleanly. Defeated state survives save, load and reactivation. Caps and sanctuary hold. Creative twin. Natives in a street and a ground-floor apartment. | NOT STARTED |
| 10 | Indoor navigation and perception | 09, 04–06 | `ai/Pathfinder` plus new `PortalGraph` and `PathBudget`; `ai/Steering` guards; `combat/WorldNoise` attenuation; AI loaded-column guard | Multi-floor pursuit through doors and stairs, losing sight, searching and giving up. Door edits change routes. A shared measured budget. No roof or ground confusion and no void pathing. A native pursuit and escape. | NOT STARTED |
| 11 | Playable vertical slice gate | 02–10 | Integration: `UrbanQaScenes`, `AutomatedRunDriver` urban route, integration tests | The DESIGN §14 slice holds headless and native with normal controls; save, revisit, no refill and no resurrection; Creative twins. Regression seeds frozen. Memory per explored area recorded (§4.3). | NOT STARTED |
| 12 | Commercial and civic buildings | 11 | `BuildingTemplates` catalog (6 archetypes), `LootTables` | Every family has connected interiors with reachable containers and exits across the family matrix. Earlier urban layouts and legacy fingerprints are unchanged. | NOT STARTED |
| 13 | Urban biome differentiation | 12, 03 | New `DistrictRules`, overlays, district-to-climate mapping (D-08) | Six distinct districts change player choices. Seams connect. Basics never depend on rare districts. Overlays keep accessibility. Legacy biome consumers are unchanged. | NOT STARTED |
| 14 | Basements, transit and rooftop routes | 13, 05–06, 10 | Basement, transit and roof planners; hidden entrances; portals; `urban.discovery` | Street → basement → service passage → street and street → fire escape → roof → exit work without clipping or traps. Underground joins agree across borders. Secrets are discoverable through real clues. | NOT STARTED |
| 15 | Alien ecology and urban wildlife | 14, 09–10 | Alien kind (appended `BodyFamily`), growth-node state, wildlife anchors | Infected, alien and wildlife behaviour are distinct. The alien has a tell and a counter. Caps, deaths and revisits hold. No edible hostile meat. Creative gates. | NOT STARTED |
| 16 | Survivor camps and hidden civilians | 15, 06, 14, 10 | Urban site registry over `settlement/Settlement`, `SettlementManager`, `DormantSettlementSimulation` (D-21); `ai/SettledNpcAI`; aid interaction | A generated camp and a secret basement refuge are found through play. Helping or stealing has a consequence. The same people, needs and deaths remain after save and dormancy. | NOT STARTED |
| 17 | Guarded strongholds and hostile factions | 16 | `SettlementManager` guards, reputation, theft and counterattacks; urban faction ids | Approach, access rules, entry, trade and exploration. Guards react fairly. A hostile site with an alternative route. State persists without duplicate guards or infinite counterattacks. | NOT STARTED |
| 18 | Safehouses, salvage and fortification | 17, 07, 04 | New `RefugeSystem`, `urban.refuges`; salvage in `PlayerBlockActions`; barricades; `SleepSystem` safety | Claim, store, dismantle, barricade from salvage and sleep. Threats attack or route around defences. Claims, salvage and defences persist. Survivor property has consequences. | NOT STARTED |
| 19 | Water, power and refuge utilities | 18 | New `UtilitySystem` (`MediumTickSystem`), `urban.utilities`; `simulation/WaterSystem`; light emitters | One water utility and one power source and consumer run from urban supplies, conserve resources, persist, follow the pause and dormancy policy and respect Creative. | NOT STARTED |
| 20 | Urban missions and long-term purpose | 19, 16–17, 02 | New `UrbanMissions`, `CommsObjective`, `urban.missions`; `ai/Quest`, `ai/FactionSystem` model; `ui/QuestObjectiveView`; victory flow | An integration route advances C1–C5 without trees or ore. A hidden-refuge mission and a guarded-access mission. Failure alternatives. Rewards exactly once. The legacy beacon is intact. | NOT STARTED |
| 21 | Map, HUD and onboarding | 20 | `ui/MapScreen` plan layer, `ui/Hud`, `InteractPromptBuilder`, `ui/NewFrontierScreen`, `ui/PauseMenu` | A new player finds supplies, floors, the refuge and the next step through ordinary UI. Secrets stay secret. `docs/graphics/HUD_VALIDATION.md` captures. | NOT STARTED |
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

## Resume rules

- On resume, read `00_BASE_KNOWLEDGE.md`, the current package prompt, the sections of these four records that the prompt names, and current source. Do not reload every plan.
- Continue a PARTIAL package before starting the next. Record the exact resume step whenever a package spans sessions.
- Each package handoff appends a section here containing: id; status; implemented behaviour; changed paths; stable-id and schema decisions; exact commands and results; native evidence; limitations; next concrete step. The package also updates its ledger row.
- An unrun or failed mandatory gate stays pending. Historical evidence never closes it.

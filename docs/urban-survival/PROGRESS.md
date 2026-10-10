# Urban Survival — Progress

This is the running record for the 26-package urban-survival roadmap. The pack is the `VEYLON Urban Survival — Agent Prompt Pack`, kept outside the repository, with `00_BASE_KNOWLEDGE.md` and prompts `01`–`26`. This file holds status, owners, gates and handoffs. The design is in [DESIGN.md](DESIGN.md), the decisions in [DECISIONS.md](DECISIONS.md), and the evidence in [VALIDATION.md](VALIDATION.md).

## Current status

| Item | Value |
| --- | --- |
| Last completed package | **01 - Design and repository audit**; this commit series reconstructs the accumulated 01-04 work in dependency order. |
| Branch | `feature/urban-survival`, based on `923345f`; `main` remains at that baseline until a separately authorized integration. |
| Source revision | The source and configuration are reconstructed from `e452dc1`; final equality is checked after the split. Dated checks below belong to the original implementation sessions. |
| Default new world | Legacy generator 3 until package 26. The explicit urban development selector requires an isolated data directory. |
| Next package | **02 - Versioned urban foundation**; see the handoff below. |
| Commits | Local, scoped `feat`, `test`, `docs` and `chore` commits on the feature branch. No merge, push, tag or publication is part of this reconstruction. |

## Package ledger

The status vocabulary is NOT STARTED, IN PROGRESS, PARTIAL, BLOCKED or COMPLETE. Execution is sequential (pack README). "Depends on" lists the most relevant direct prerequisites. "Code owners" are the existing classes to extend and the proposed new ones (DESIGN §12), with paths relative to `src/main/java/com/veylon/`. A gate is met only with the evidence in [VALIDATION.md §5](VALIDATION.md#5-validation-matrix).

| Id | Package | Depends on | Code owners | Observable gate | Status |
| --- | --- | --- | --- | --- | --- |
| 01 | Design and repository audit | — | `docs/urban-survival/` | The four records exist, are cross-linked and pass the link and path check; the entry point for 02 is recorded | **COMPLETE** |
| 02 | Versioned urban foundation | 01 | `world/World` (`GEN_URBAN`, predicates, default and maximum constants), `world/WorldGenerator` (dispatch), `Game`, `WorldBootstrap`, `FrontendController` (explicit generator path), `save/SaveSystem`, `save/V3ExtensionSections` (`urban.meta`), `util/AppPaths`, `build.gradle` (QA data dir), new `world/urban/UrbanIds`, `UrbanRevision`, `UrbanBootstrap` skeleton | Production bootstrap creates an urban-dev world and its generator and revision round-trip. Legacy dispatch, fingerprints and the new gen-2 pin are unchanged. Old saves without urban state load safely. Unsupported or corrupt revisions fail per policy. Reset over a live world clears foundation state. A tested QA data-dir seam exists. | NOT STARTED |
| 03 | City plans and street network | 02 | New `world/urban/CityPlanner`, `DistrictPlan`, `StreetNetwork`, `ParcelPlan`, `UrbanChunkStamper`; `World` urban queries and the loaded-column query; `EntityManager` and `EventSystem` urban gating (D-28); `UrbanQaScenes` | Across the seed, region and order matrix: roads join at borders, parcels respect reservations, blocks and ids are identical in every chunk order, and no wilderness POI, ore, cave or legacy settlement appears. A walkable starter street network exists. Streetscape captures and a native seam walk. | NOT STARTED |
| 04 | Urban materials and interactions | 03 | `world/BlockType` (appended, budgeted), `gfx/MaterialRegistry`, `gfx/ProceduralTextures`, `engine/AudioManager` footsteps, `WorldInteractions`, `InteractPromptBuilder`, `PlayerBlockActions`, `ai/Pathfinder` door passability, new `FixtureStates` and `world.fixtures` | A fixture shows a player and an NPC opening a door, using a window entry and climbing stairs; breaking a fixture; recognising a searchable object. Collision, raycast, rendering and navigation agree. Fixture state round-trips. Creative and Survival gates hold. Natives show both sides of doors and night readability. | NOT STARTED |
| 05 | Building plans and accessibility | 03–04 | New `world/urban/BuildingPlanner`, `BuildingTemplates`, `FloorPlan`, `RoomPlan`, `FixtureSlot`, `AccessibilityValidator` (reusing `Pathfinder` semantics and `PlayerConstants`) | Across the template × rotation × seed matrix: no unreachable room, container or quest reservation; valid stairs; no overlap or overflow. Invalid fixtures are rejected. Plans match under shuffled access. Validator work is bounded. | NOT STARTED |
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

## Resume rules

- On resume, read `00_BASE_KNOWLEDGE.md`, the current package prompt, the sections of these four records that the prompt names, and current source. Do not reload every plan.
- Continue a PARTIAL package before starting the next. Record the exact resume step whenever a package spans sessions.
- Each package handoff appends a section here containing: id; status; implemented behaviour; changed paths; stable-id and schema decisions; exact commands and results; native evidence; limitations; next concrete step. The package also updates its ledger row.
- An unrun or failed mandatory gate stays pending. Historical evidence never closes it.

# World Expansion 0.3.0 — Implementation Plan

Working plan for the settlements / deep caves / hostile factions / ranged combat /
black-powder / explosives update. Kept current as phases land.

## Baseline (Phase 0)

- `.\gradlew.bat build` on the pristine tree: **PASS, zero failures** (2026-07-16).
- Save format is binary v2; `BlockType`/`ItemType`/`CreatureType`/`Affliction`/
  `Quest.Type`/`EventSystem.EventType`/`WeatherSystem.Weather` ordinals are persisted.
- World gen: pure-function `heightAt`/`biomeAt` + per-chunk noise caves and POIs.
  `World.pendingGen` handles cross-chunk decoration spill.
- AI: direct steering only (`Steering.moveToward` + auto-jump). Single-camp
  `FactionSystem`. NPCs: camp members, wandering traders, scavenger raiders.

## Architecture decisions

1. **Generator versioning.** `World.generatorVersion`: `1` = legacy (v2 saves),
   `2` = deep-frontier (pre-identity 0.3.0 development saves), `3` =
   `GEN_CAVE_IDENTITIES` (release 0.3.0, current). Deep caves, new ores,
   underground POIs and planned settlements generate for version >= 2; the
   depth-relative Root/Basalt/Resonant cave zone identities, basalt fumarole
   hazards and cave-life dens generate **only** for version 3 worlds. Every
   save keeps the exact generator it was created with (byte-identical future
   chunks are asserted for both v2/legacy and pre-identity v3 saves); new
   content requires a new world (documented).
2. **Save v3.** `SaveSystem.VERSION = 3` with an explicit v2 read path that maps
   old saves onto v3 runtime state (generator version 1, no settlements, defaults
   for all new NPC/faction fields). v1 still rejected.
3. **Settlements are planned per region** (24×24 chunks), deterministically from
   `(worldSeed, regionX, regionZ)`. At most one settlement per region; plans are
   pure functions so any chunk can independently compute the slice of any
   settlement that intersects it — order-independent, no duplicates. Static
   structure re-derives from the seed; only dynamic state (alignment, stocks,
   cleared/occupied, residents, alert) is saved.
4. **Residents are dormant records** (`Settlement.Resident`) simulated abstractly;
   live `Npc` entities are materialized when the player is near the settlement and
   written back when they leave. Bounded active-NPC counts.
5. **Weapons are data** (`WeaponDefinition` keyed by stable string id in
   `WeaponRegistry`), shared by player and NPCs. Projectiles/explosions are
   engine systems (`ProjectileSystem`, `ExplosionSystem`) with hard caps.
6. **Noise events** (`WorldNoise`) carry position/radius/category; human AI
   perception (LOS + hearing) consumes them; gunshots and explosions emit them.
7. **Batch world edits**: `World.beginBatch()/endBatch()` coalesces heightmap,
   light and dirty-mesh work for explosions.
8. **Deterministic AI jitter.** The static decision RNGs in `SettledNpcAI`,
   `CreatureAI` and `NpcAI` are reseeded from the world seed in
   `Game.newWorld(...)` (which the save-load path also uses). A fixed seed
   therefore replays identically, and tests cannot inherit RNG state from
   earlier worlds in the same JVM.

## New serialized enum entries (append-only)

- `BlockType`: BASALT, SULFUR_ORE, SALTPETER_ORE, GLOW_FUNGUS, LADDER, LANTERN,
  TRAIL_MARKER, POWDER_KEG, GATE, GATE_OPEN, STONE_BRICK, ALARM_BELL, CAGE_BARS
- `ItemType`: SULFUR, SALTPETER, BLACK_POWDER, MUSKET_BALL, SCRAP_SHOT,
  PRIMITIVE_BOW, ARROW, IRON_ARROW, MUSKET, FLINTLOCK_PISTOL, BLUNDERBUSS,
  POWDER_KEG, SCRAP_BOMB, FIRE_BOMB, ROPE_LADDER, LANTERN, TRAIL_MARKER,
  RELIC_CARBINE, RELIC_RIFLE, RIFLE_CARTRIDGE, RELIC_PARTS
- `Quest.Type`: appended settlement quest kinds.
- `Poi.PoiType`: appended cave/underground POI kinds (type itself not persisted,
  discovery is by position; append anyway for consistency).
- `CreatureType`, `EquipSlot`, `Affliction`, weather/events: unchanged.

## Pre-correction audit baseline (2026-07-16)

- `git status --short`: 37 tracked files modified plus untracked settlement,
  combat, AI, save, world and test sources. These changes pre-date this audit and
  are being preserved.
- `.\gradlew.bat test --rerun-tasks`: **PASS** after Gradle was allowed to use its
  external distribution/dependency cache. The sandbox-only attempt failed while
  downloading Gradle (`SocketException: Permission denied`), not in project code.
- `.\gradlew.bat build`: **PASS**. The existing suite is green, but the coverage
  gaps below mean that this is not evidence of feature completion.
- The earlier 30-second smoke result is historical evidence only. A fresh smoke
  run is still required after the corrective work.

The two matrices in this section are the evidence snapshot taken before corrective
work. The final matrices below supersede them.

Status meanings: **Complete** = reachable, behaviorally correct, persisted where
needed, meaningfully tested and runtime-validated; **Partial** = useful production
work exists but one or more completion conditions are unproved; **Defective** = a
known implementation contradicts the requirement; **Missing** = no meaningful
gameplay implementation was found.

## Original phase requirements matrix

| Phase / requirement | Status | Production evidence | Automated evidence | Manual/runtime validation | Remaining work |
|---|---|---|---|---|---|
| 0 — baseline and repository audit | Complete | Existing architecture and user-owned diff inspected; graphics guidance retained | Forced test rerun and build pass | Fresh post-fix smoke still belongs to Phase 20 | Keep this matrix and validation record current |
| 1 — extensible data, generator versioning and save v3/v2 migration | Partial | `Settlement*`, `HumanFaction`, `NpcArchetype`, `Weapon*`, `World.generatorVersion`, `SaveSystem` | `SaveMigrationTest`, `SerializedEnumOrderTest` | Load a real historical v2 save and a transitioned v3 world | Persist/validate all objective, route, sickness, quest, reload and explosive state; reject corrupt stable IDs safely |
| 2 — deterministic regional settlement planning and starter guarantee | Defective | `SettlementPlanner`, `World.settlementForRegion` | `SettlementPlannerTest` covers determinism/rarity but permits a null starter and checks no full POI bounds | Traverse from actual spawn and inspect negative regions/order reversal | Force a valid starter plan, reserve full settlement/POI footprints, prove reachability and order independence |
| 3 — modular, biome-adapted settlement generation | Partial | `SettlementBuilder`, `WorldGenerator.applySettlementSlice` | Block-order comparison indirectly covers one generated layout | Inspect every tier/biome, foundations, routes and tactical approaches | Prove tier module counts, functional interiors, full-footprint protection and non-floating foundations |
| 4 — factions, relationships, ownership, stocks and replenishment | Defective | `SettlementManager`, `Settlement`, `HumanFaction` | `SettlementRuntimeTest` calls manager methods directly | Exercise trade/theft/combat and save/load through UI | Unify NPC side/hostility, add missing action hooks, medicine/metal/safety simulation and supported de-escalation rules |
| 5 — navigation, daily life and dormant simulation | Partial | `Pathfinder`, `SettledNpcAI`, activation/dormancy in `SettlementManager` | `PathfinderTest`, `SettlementActivationTest` run AI ticks | Observe sleep/eat/work/patrol/trade/heal/return-home and long-run budgets | Implement real consumption/output/sickness/death/events/recovery; bound caches/work and preserve assignments/state |
| 6 — positioned perception and human combat AI | Partial | `WorldNoise`, LOS/hearing/search logic in `SettledNpcAI` | One hostile-garrison integration test and direct noise tests | Validate darkness/crouch/occlusion/search/return/friendly-fire cases | Make side-aware target/friendly-fire decisions and cover all perception transitions |
| 7 — Headhunters, patrol origin/mission/return, bounty and rescue | Defective | Archetypes and party spawning exist in `SettlementManager` | Archetype/bounty/rescue assertions only | Follow a party from origin through return/report | Persist origin/destination/mission; outbound/search/retreat/report/return; survivor escalation and distant abstraction |
| 8 — capture, siege, occupation and counterattacks | Defective | Clear/occupy/counterattack methods and fortress geometry exist | Capture test directly marks resident deaths through bookkeeping | Complete alarm, rescue, command, central-control and occupation loop in play | Add explicit objective state, surrender/rout rules, safe services/trade, persistent outcomes and multi-approach integration tests |
| 9 — connected deep caves, guaranteed entrances and reachable POIs | Defective | Deep density/worm carving and POI chambers in `WorldGenerator`; entrance candidate in `SettlementPlanner` | `DeepCaveGenTest` proves density/resources/lake seal only; entrance test accepts 75% | Traverse surface-to-depth and entrance-to-required-POI for required seeds | Guarantee reserved walkable entrances; deterministically connect mandatory POIs; prove bounds, order independence and negative coordinates |
| 10 — rope ladder, lantern and trail marker | Partial | Items/blocks, recipes, movement/light hooks and procedural visuals exist | Enum/visual registration tests only | Place, climb, fuel/use and read in a dark cave | Add gameplay-facing movement/light/durability tests and document actual fuel behavior |
| 11 — unified ranged foundation and player controls/HUD | Defective | `WeaponRegistry`, `ProjectileSystem`, input/HUD wiring in `Game`/`Hud` | Projectile tests call `fire` directly | Exercise bow/firearm/thrown inputs and HUD | Bind reload to initiating stack/slot, test normal input/cooldowns/ammo/durability/auto-fire and side-aware NPC fire |
| 12 — primitive bow and recoverable basic/iron arrows | Partial | Bow definition, recipes, draw/release and arrow recovery exist | Direct projectile stick/recovery test | Craft, draw, short-draw, fire both arrow types and harvest/recover | Test the player-facing inventory/input path, short draw, ammo selection and bounded cleanup |
| 13 — sulfur/saltpeter/black powder and firearms | Partial | Ores, recipes, musket/pistol/blunderbuss definitions and noise hooks exist | Resource density/registry/direct projectile assertions | Mine, craft, load, shoot, switch and alert AI | Correct reload identity, multi-round reload, durability/recoil/cooldown and normal-play ammo/noise coverage |
| 14 — reusable bounded explosion system and batched edits | Partial | `ExplosionSystem`, resistance table, batch edits and caps exist | Falloff/cover/protection/chain/batch tests | Detonate near structures, entities, lakes and chained kegs | Prove fire ignition/reputation, stale fuse cleanup, block-edit/mesh bounds and side attribution |
| 15 — kegs and thrown scrap/fire bombs | Defective | Keg fuse and bomb projectiles exist | Keg-chain test; no bomb collision regression tests | Hit NPC, creature, wall and ground with each bomb; ignite keg via every route | Preserve bomb after entity impact, prevent repeated collision, detonate once, distinguish effects, test fire-adjacent keg ignition |
| 16 — restrained procedural explosion presentation | Partial | Particle/audio/camera-shake hooks exist | Registration/cap tests are indirect | Compare indoor/outdoor audio and low-particle/motion settings | Record peak particles and verify density/motion settings, visibility and no GL errors |
| 17 — rare distinct relic weapons | Partial | Loot-only definitions and rare loot rolls exist | Registry marks relics; no acquisition/economy test | Find/repair/fire both relic roles | Prove fortress/research loot only, scarcity, repair path, auto-fire and non-craftability |
| 18 — map, rumors, services, prompts and eleven quest categories | Missing | Map markers/prompts and four appended quest types are partial | No discovery-leak, prompt or settlement-quest lifecycle coverage | Inspect undiscovered/discovered/captured UI states and complete every quest | Implement remaining quest categories and full acquire/progress/reward/fail/save hooks; hide undiscovered alignment/services |
| 19 — procedural visuals/audio and asset provenance | Partial | Materials, textures, icons, cuboid models and synthesized audio were added; provenance updated | Visual ID/procedural texture tests | Startup validation at supported resolutions/night/fog | Verify every new asset/model/silhouette/audio hook and correct any missing or misleading provenance claims |
| 20 — deterministic QA, build, smoke and performance gates | Defective | Hard caps exist in path/projectile/explosion/particle systems | Existing green suite omits required gameplay and traversal cases | Fresh 30-second GL smoke plus deterministic QA seeds required | Add all defect regressions/integration tests, long-run boundedness, exact count/version validation, build and smoke measurements |

## Seventeen acceptance scenarios matrix

| # / scenario | Status | Production evidence | Automated evidence | Manual/runtime validation | Remaining work |
|---|---|---|---|---|---|
| 1. New seeded world always provides a valid friendly starter settlement | Defective | Spawn-region override runs only after `rawPlan` succeeds | `starterRegionIsFriendlyAndBalanceControlled` permits `null` | Walk from actual spawn on all required seeds | Deterministic fallback plan plus non-null/reachability assertions |
| 2. Common villages/uncommon forts/rare castles/fortresses | Partial | Weighted regional planner and separation rules | Fixed-seed rarity hierarchy test | Explore/map-discover representative tiers | Multi-seed distribution bounds and discovery validation |
| 3. Friendly residents sleep/work/patrol/trade/return home | Partial | AI state machine, roles and pathfinder exist | Activation test only proves survival/deactivation | Observe every routine across a day | Real eating/work outputs/social/trade/home integration and stuck/budget tests |
| 4. Neutral village becomes friendly or hostile through play | Defective | Reputation thresholds exist | Direct manager calls prove numeric flips only | Trade/assist/theft/attack through gameplay and save/load | Coherent side/interaction/friendly-fire/clear behavior after flips; explicit recovery path |
| 5. Hostile patrol visibly originates and returns | Defective | Party spawns at a real gate | No route/return test | Follow outbound and returning survivors | Origin/destination/mission/report persistence and distant travel abstraction |
| 6. Infiltrate fort, disable alarm, rescue captive, rout/defeat defenders, clear permanently | Defective | Geometry, bell, captive and clear methods exist separately | Tests bypass objective flow | Complete staged fort objective | Explicit alarm/command/central objective and real-combat integration/save coverage |
| 7. Cleared fort remains cleared after save/load | Partial | `SaveSystem` writes cleared state | v3 round trip mutates `cleared` directly | Clear through gameplay, save, load and revisit | Pair real objective flow with persistence/no-respawn assertion |
| 8. Supply a cleared fort into an allied outpost | Partial | `occupy` consumes food/logs and adds guards | Direct occupation test | Claim via campfire and use bed/storage/trade | Safe services, ownership transition and save/counterattack coverage |
| 9. Fortress has staged objectives and multiple approaches | Partial | Builder creates zones, gate, breach, prison and magazine | No gameplay geometry/objective test | Assault by gate, rear, alarm, rescue, keg and patrol attrition | Tactical route assertions and staged objective logic |
| 10. New caves have connected zones/POIs/resources/tools | Defective | Zones/resources/tools/POI chambers exist | Density/resource/lake tests; no connectivity | Surface-to-depth-to-POI traversal | Guaranteed entrance and bounded POI connectors, movement BFS and order tests |
| 11. Craft/use bow, recover arrows, quiet hunting | Partial | Recipe, input and recovery hooks exist | Direct projectile recovery only | Craft/fire/harvest in normal play | Gameplay-facing ammo/draw/noise/recovery/cleanup tests |
| 12. Craft black powder from deep resources | Partial | Ores/drops/recipe exist | Resource generation only | Mine and craft at intended station | Recipe-path and progression integration test |
| 13. Musket/pistol ammo, reload, recoil, sound and AI alert | Defective | Definitions/input/HUD/noise exist | Direct fire/registry tests | Switch weapons during reload and alert a settlement | Weapon-bound reload, multi-round behavior, durability/recoil and player-facing tests |
| 14. Place/ignite keg and breach a weak hostile gate | Partial | Placement/fuse/explosion/resistance hooks exist | Protected/chain tests only | Place, light and breach generated gate | Fire-adjacent ignition, normal interaction and hostile-structure/reputation test |
| 15. Explosions damage/occlude/protect/ignite with bounded VFX | Partial | Reusable blast and presentation caps exist | Damage/cover/protection/chain/batch assertions | Observe fire, particles, GL and mesh work | Fire/reputation/VFX peak/mesh-budget regressions |
| 16. Rare relic automatics remain late-game loot | Partial | Rare loot-only rolls and distinct definitions exist | Relic flags only | Acquire/repair/fire and check economy | Non-craftability, loot source/rate, repair/ammo/auto-fire tests |
| 17. v2 saves load and preserve legacy generation | Partial | Explicit v2 reader pins generator v1 | Synthetic byte-level v2 fixture and legacy-block test | Load an actual 0.2.0 save and explore new chunks | Historical fixture, corruption behavior and no-new-content regression |

## Post-correction phase matrix

This is the current source/test evidence. “Partial” is retained where no automated
test or practical runtime pass exercises the complete player-facing path.

| Phase | Current status | Corrective evidence | Remaining validation |
|---|---|---|---|
| 0 — baseline/audit | Complete | Dirty-tree snapshot preserved; original spec and relevant production/test/docs reviewed; baseline test/build passed | None |
| 1 — data/save/migration | Complete | Optional compatible v3 tail persists objectives, quests, rumors, routed/sick residents, gate timers, patrol/counterattack missions and lantern fuel; old v3 EOF loads; corrupt archetype/faction IDs fail safely; an authentic SHA-256-pinned 0.2.0 fixture (from tag `v0.2.0`) loads and keeps byte-identical legacy terrain | None |
| 2 — deterministic planning/starter | Complete | Starter is non-null/friendly and selected from a bounded walk/swim-reachable surface set for required seeds; rarity/separation/order tests pass | None |
| 3 — modular settlement generation | Complete | Deterministic layouts, full reservations, foundations and reverse-order block comparison pass; the seeded smoke run renders a fortress approach with zero GL/asset errors | Exhaustive human visual review of every tier × biome pairing remains desirable QA, not a behavioral gap |
| 4 — factions/economy/ownership | Complete | Live hostility/side changes immediately on alignment flips; explicit restitution; theft/violence/assistance and save round trips covered | None |
| 5 — navigation/daily/dormant life | Complete | Bounded A*, cached paths, eating, role output, medicine use, trade state, illness death, population caps, activation/deactivation and no double simulation tested | None |
| 6 — perception/combat AI | Complete | Darkness/crouch/LOS/noise code retained; garrison response, side-aware projectiles and friendly-fire logic covered by integration/regression tests | None |
| 7 — Headhunters/patrols/bounty/rescue | Complete | Explicit outbound/search/return/report lifecycle, real gate origin, coarse unloaded travel and mission persistence; rescue and bounty tests | None |
| 8 — siege/capture/occupation | Complete | Live entity deaths cannot bypass command/alarm/central objectives; staged clear persists; occupation consumes supplies and reactivates guards/trader; every tactical route (gate, postern, alarm, prison, magazine, command) is traversed per required seed and a full clearance completes through player-facing actions; counterattacks run the full dispatch→assault→resolve→cleanup lifecycle with save/load | Routes proven by movement-rule traversal, not human playthrough |
| 9 — caves/entrances/POIs | Complete | Guaranteed reserved ladder shafts, movement-rule traversal, bounded regional POI connectors, negative coordinates and reverse chunk order pass; generator 3 adds depth-relative Root/Basalt/Resonant zone identities, minable fumarole smoke hazards and gloomstalker nest/den ecology, all seed-deterministic and order-independent | None |
| 10 — cave tools | Complete | Ladder climbability, lantern light, trail-marker passability and recipes tested | None |
| 11 — ranged foundation/controls | Complete | Weapon registry, projectile cleanup, side-aware fire, weapon-bound reload/cancel/exact-once and HUD state production paths covered | None |
| 12 — bow/arrows | Complete | The production input seam (same commands the mouse path calls) is tested for hold/draw/release, deliberate basic/iron ammo selection with HUD display, short-draw cancel, cooldowns, noise, durability, F-key recovery and bounded cleanup | Native OS mouse events call the tested seam directly |
| 13 — powder/firearms | Complete | Deep resources, black-powder craft, firearm definitions, ammo/reload/save/noise/damage paths tested | None |
| 14 — explosion system | Complete | Falloff, cover, protected blocks, batch edits, bounded chains, stale fuses, adjacent-fire ignition and deterministic fire-bomb ignition tested | None |
| 15 — kegs/bombs | Complete | NPC/creature/wall/floor impacts keep one fuse; exactly-once detonation; scrap/fire distinction; fuse persistence and fire ignition covered | None |
| 16 — presentation | Complete | Particle cap and motion-aware shake remain bounded; smoke peaked at 67 particles with audio initialized and zero GL/KHR errors | None |
| 17 — relic weapons | Complete | Restoration requires the damaged relic itself; no ordinary crafting path; distinct marksman/automatic roles and rare-tier crate distribution tested | None |
| 18 — map/prompts/quests | Complete | Unknown rumors disclose only `?`; discovered services display; restricted/capture prompts added; all quest categories have acquisition, lifecycle and save tests | None |
| 19 — procedural assets/provenance | Complete | Procedural registrations/provenance retained; visual tests pass; smoke validated 55 materials, 72 texture layers, 656 glyphs and 91/91 item icons | None |
| 20 — QA/release | Complete | 160 tests pass twice consecutively; forced build and the Windows x64 self-contained package succeed; 30-second seeded smoke reports 823 FPS average, 1.74 ms p95, 0 GL errors, 0 KHR errors and `withinHardLimits=true`; AI decision RNGs reseed per world seed so QA runs replay deterministically; tag-gated CI builds, validates and publishes Windows x64, macOS arm64 and macOS x64 archives with SHA-256 checksums | The two macOS packages are built and validated on GitHub-hosted macOS runners after the tag is pushed |

## Post-correction acceptance scenarios

| # | Result | Evidence | Honest caveat |
|---|---|---|---|
| 1 | Pass | Required-seed starter non-null/friendly plus bounded surface traversal proof | — |
| 2 | Pass | Deterministic rarity hierarchy and fortress/castle separation tests | Fixed-seed statistical evidence, not a manual survey |
| 3 | Pass | Active-life test covers work, meals, medicine, trade, path bounds and return/deactivation | Day-long visual observation is not automated |
| 4 | Pass | Neutral assistance/hostility transitions update live AI/interaction immediately and persist; explicit restitution required | — |
| 5 | Pass | Patrol spawns at a real gate, searches, returns/reports and persists mission identity | — |
| 6 | Pass | Live NPC death processing plus alarm and central objectives clears a fort; rescue has its own gameplay hook/test | — |
| 7 | Pass | That staged clear, objectives and zero active defenders survive save/load | — |
| 8 | Pass | Occupation consumes actual food/logs, changes ownership and activates safe trader/garrison services | Counterattack abstract resolution is separately code-covered, not manually observed |
| 9 | Pass | `FortressGameplayIntegrationTest` proves deterministic gate/postern/alarm/prison/magazine/command routes for every required seed and completes a full clearance through player-facing actions (melee, alarm sabotage, rescue, campfire objective); a provider-targeted capture quest completes and persists | Routes are proven by movement-rule traversal, not a human playthrough |
| 10 | Pass | Entrance-to-depth and entrance-to-POI movement traversal, resources, tools, order/bounds tests | — |
| 11 | Pass | `BowGameplayWorkflowTest` drives the same press/hold/release command seam the mouse uses: ammo-type cycling with HUD state, short-draw cancel without cost, cooldown enforcement, and F-key arrow recovery with bounded cleanup; `BowHuntProgressionIntegrationTest` hunts quietly end-to-end | Native OS mouse events themselves are not synthesized; they call the tested seam directly |
| 12 | Pass | Sulfur/saltpeter generate and the workbench recipe consumes them with charcoal into black powder | — |
| 13 | Pass | Firearm ammo, reload switching/cancel, exact-once completion, persistence, projectile damage and long-range noise tests | — |
| 14 | Pass | A keg is placed and ignited through the normal item/interaction path, its fuse survives save/load, and it breaches a generated hostile gate while protected structures survive, with correct attribution and batched edits (`FortressGameplayIntegrationTest`, `CombatSystemsTest`) | — |
| 15 | Pass | Damage, cover, protection, ignition and edit bounds pass; smoke peaked at 67 particles with audio active and zero GL/KHR errors | — |
| 16 | Pass | Relics have loot/restoration-only constraints, distinct automatic/marksman stats and bounded rare-armory distribution | Acquisition was statistically tested rather than manually found |
| 17 | Pass | An authentic 0.2.0 fixture (generated from tag `v0.2.0`, SHA-256-pinned) loads, pins the legacy generator and produces byte-identical future chunks; corrupt IDs fail safely; pre-identity v3 saves keep generator 2 terrain (`HistoricalV020SaveCompatibilityTest`, `SaveMigrationTest`) | — |

## Final validation record (release pass, 2026-07-17)

- `.\gradlew.bat test --rerun-tasks`: **PASS twice consecutively** —
  **160 tests, 0 failures, 0 skipped** across 39 suites.
- `.\gradlew.bat build --rerun-tasks`: **PASS**.
- `git diff --check`: clean.
- `VEYLON_SMOKE=30`, `VEYLON_VSYNC=0`, `VEYLON_SEED=20260716`,
  `.\gradlew.bat run`: **PASS**.
- Smoke hardware/context: NVIDIA RTX 1000 Ada, OpenGL 3.3, KHR debug active.
- Performance: 823.2 average FPS, 1.21 ms average, 1.74 ms p95, 2.61 ms p99,
  30.91 ms maximum; the 60 FPS target and performance gate passed. The scripted
  fortress-approach QA completed (830 avg FPS during approach, 606 mesh
  rebuilds, no frame-time collapse).
- Runtime integrity: isolated save/load both true; 55 materials, 73 texture
  layers, 656 glyphs, 91/91 icons; 0 `glGetError`, 0 KHR errors (three NVIDIA
  medium shader-state recompilation notices are known non-error diagnostics).
- Boundedness snapshot at exit: `withinHardLimits=true`; NPCs 29/40
  (25 residents, 4 legacy), projectiles 0, stuck arrows 0, missions 0, fuses 0,
  noise events 0, path cache 1270/20480 nodes, particles 79/4000.
- An earlier interim record (86 tests, 987 FPS) from the 2026-07-16 corrective
  pass is superseded by this section.

## Corrective order used

The highest-risk order was: projectile/fuse correctness; coherent settlement sides
and capture bookkeeping; guaranteed traversable cave entrances and POI connectors;
gate timer mutation safety; weapon-bound reloads; save validation; then the wider
gameplay/quest/UI audit. The post-correction matrices preserve partial status wherever
the available automated and runtime evidence does not cover the whole requirement.

## Valid limitations (not acceptance waivers)

- v2 worlds intentionally remain on generator version 1 and do not gain v3 terrain.
- Gates are block swaps rather than animated meshes.
- Aim-down-sights is outside the specification; bow/firearm controls remain LMB + R.
- Settlement navigation is intentionally bounded voxel A* with steering fallback.

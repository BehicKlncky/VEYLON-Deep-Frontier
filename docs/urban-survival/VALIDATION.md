# Urban Survival — Validation

This file covers:
- the verified baseline;
- deviations from the pack snapshot;
- the risk register;
- the validation matrix per package;
- the seed registry;
- the evidence log of the urban-survival roadmap.

Design: [DESIGN.md](DESIGN.md). Decisions: [DECISIONS.md](DECISIONS.md). Status and handoffs: [PROGRESS.md](PROGRESS.md).

## 1. Evidence rules

- Source and build configuration are authoritative. Historical documents, release notes and old test counts are not current evidence.
- Paths are relative to `src/main/java/com/veylon/` unless they start with `src/`, `docs/` or `.agents/`, or name a repository-root file (`AGENTS.md`, `CLAUDE.md`, `build.gradle`). `00_BASE_KNOWLEDGE.md`, `MILESTONE_STATUS.md` and the pack `README.md` are files of the roadmap pack, outside the repository. Line numbers are for `923345f`.
- A claim is **verified** only when it was read in current source. Derived numbers (arithmetic from constants) and static counts that were not measured at runtime are labelled as such.
- Headless tests show behaviour. Native captures show appearance. Manual play and listening show playability. They are complementary; none substitutes for another.
- An unrun or failed mandatory check keeps its gate **pending**. Historical evidence never closes a gate.

## 2. Verified baseline at `923345f`

The milestone-01 audit described in §7 produced these facts. The design records cite them by id.

### Lifecycle, frontend and budgets

| Id | Fact | Evidence |
| --- | --- | --- |
| B-01 | New game: title "NEW FRONTIER" → `FrontendController.openNewFrontier` → `ui/NewFrontierScreen`, which offers only Survival or Creative, with no seed or world-type choice → `performLoad` → `game.newWorld(sessionSeed, true, mode)` | `FrontendController.java:71-107,144-198`; `ui/NewFrontierScreen.java:25-32` |
| B-02 | `Game.newWorld(seed, fresh)` and `newWorld(seed, fresh, mode)` hard-code `World.CURRENT_GENERATOR`. Only `newWorld(seed, fresh, int)`, used by save load, passes a version, and it is fixed to Survival. The package-private `WorldBootstrap.newWorld(seed, fresh, generatorVersion, initialMode)` exists but no `Game` method delegates to it. `World(long seed)` also defaults to `CURRENT_GENERATOR`. | `Game.java:402-417`; `WorldBootstrap.java:55-59`; `world/World.java:95` |
| B-03 | `WorldBootstrap.newWorld` order: `resetForNewWorld` (meshes released, systems reset, reseed) → mode reset → `new World(seed, version)` → listener and `Player` → `clearPerWorldState` → `placePlayerOnDryLand` → `setupCamp(fresh)` → starting kit if fresh | `WorldBootstrap.java:59-76,84-109,317-342` |
| B-04 | 22 seeded world-lifetime random streams with distinct salts: 18 calls in `reseedSimulation` (one fans out to 4 AI streams), plus dormant-settlement and player-affliction salts. Only `AudioManager` and `NpcScreen` are exempt as presentation. `WorldSeedDeterminismTest` checks every reachable `Random` reflectively. | `WorldBootstrap.java:138-159`; `entity/EntityManager.java:556-561`; `src/test/java/com/veylon/WorldSeedDeterminismTest.java:30-60` |
| B-05 | `sessionSeed` is assigned once per process, from `VEYLON_SEED` or at random | `Game.java:342`; `QaHarness.java:119-132` |
| B-06 | Ticks: fast 1/20 s (≤ 10 catch-up), medium 0.5 s, slow 10 s. Only the pause, options, audio, game-mode and world-controls screens pause the world. The crate, inventory, crafting, map, NPC and catalog screens keep it running. | `simulation/SimulationScheduler.java:9-53`; `Game.java:71-80` |
| B-07 | Beacon stage 3 places `BEACON_LIT` and sets `VICTORY`. Enter returns to `PLAYING` in the same world; Esc returns to the title. Nothing is saved. | `WorldInteractions.java:647-665`; `Game.java:471-479` |
| B-08 | Death respawns at `spawnPos` with health 55, afflictions cleared and the inventory kept. `spawnPos` is not saved; the dry-land search recomputes it on every new world and load. | `Game.java:910-940`; `WorldBootstrap.java:198-226` |
| B-09 | `OrchestratorSizeTest` line budgets (budget, then `wc -l` at `923345f`): `Game` 1,000 / 990; `QaHarness` 1,500 / 1,488; `save/SaveSystem` 1,800 / 1,117; `settlement/SettlementManager` 1,500 / 1,419; `ai/FactionSystem` 1,400 / 1,265; `world/WorldGenerator` 1,300 / 1,178 | `src/test/java/com/veylon/qa/OrchestratorSizeTest.java:51-57` |

### World, generation and presentation capacity

| Id | Fact | Evidence |
| --- | --- | --- |
| B-10 | `Chunk` is 16 × 96 × 16 backed by a `byte[]`. `heightMap` is the highest opaque block per column: leaves count, water does not. Reads outside y 0–95 return `AIR`. | `world/Chunk.java:10-17,37-63` |
| B-11 | `BlockType` has 57 constants (`AIR` … `CAGE_BARS`) and stores the ordinal as a byte. `byId` returns `AIR` for negative or unknown bytes, so 128 ids are usable and 71 are free. No door, window, glass, stairs or slab block exists. | `world/BlockType.java:9-77,98,120-126` |
| B-12 | `SEA_LEVEL = 30`; `GEN_LEGACY = 1`, `GEN_DEEP = 2`, `GEN_CAVE_IDENTITIES = 3`, `CURRENT_GENERATOR = GEN_CAVE_IDENTITIES` | `world/World.java:24,30-35` |
| B-13 | Version gates are monotonic: `WorldGenerator.deep()` (≥ 2) and `caveIdentities()` (≥ 3); `World.settlementForRegion` returns null only below 2; `World.isBasaltFumarole` and the `EntityManager` stalker zone gate on 3; the `QaHarness` fortress smoke checks assume ≥ 2. Terrain, lakes, base ores, trees and plants, and surface POIs are ungated. Pins: gen 1 by a historical fixture hash, gen 3 by fingerprints that call `CURRENT_GENERATOR`, gen 2 only relatively. | `world/WorldGenerator.java:89-95,183-250`; `world/World.java:108-123,595`; `entity/EntityManager.java:445`; `QaHarness.java:370-372,515,530`; `src/test/java/com/veylon/world/WorldGeneratorFingerprintTest.java:36-56,103-115,133-190`; `src/test/java/com/veylon/save/SaveMigrationTest.java:263-295` |
| B-14 | Terrain height is `33 + 9·base + 36·mountain² + 2.2·detail`, clamped to 4–84. y ≤ 1 is stone; no bedrock block exists. | `world/WorldGenerator.java:170-181,197-198` |
| B-15 | Chunks generate synchronously in `World.getOrCreateChunk`. Cross-border decoration is queued in `pendingGen`. Heights and lights are finalised after generation. | `world/World.java:190-214,220-239` |
| B-16 | Legacy settlements use pure regional plans: 24-chunk regions, symmetric neighbour resolution, and region `(0,0)` always a friendly camp or village. Each chunk stamps its part by filtering the whole cached layout, and crates are seeded with `putIfAbsent`. The multi-floor keep uses a 4-block floor pitch joined by a ladder. | `settlement/SettlementPlanner.java:16-59,151-258`; `world/WorldGenerator.java:1116-1154`; `settlement/SettlementBuilder.java:452-495` |
| B-17 | `World.chunks` (a `HashMap`) only grows. Unloading frees GPU meshes beyond render radius + 3 and keeps block data. Streaming generates ≤ 2 chunks and meshes ≤ 3 per frame; render distance is 4–10, default 6. `settlements`, `plannedRegions`, `settlementLayouts`, `pois`, `changedBlocks` and `crateContents` also grow with travel. | `world/World.java:41-68,157-159,490-508`; `Game.java:523-527`; `PlayerEnvironmentSystem.java:125-140`; `gfx/GraphicsSettings.java:24,58` |
| B-18 | `World.getBlock` returns `AIR` for a chunk not in the map. There is no public loaded-column API; callers repeat `getChunk(...) != null`. `World.surfaceHeight` calls `getOrCreateChunk`, so it generates chunks. | `world/World.java:242-251,532-536`; `entity/VoxelPhysics.java:195-199`; `ai/Pathfinder.java:70-73` |
| B-19 | `setBlock(x, y, z, type, record)` updates one column's height, the dirty flags, the light list, and `changedBlocks` when `record` is set, then always calls the single listener (`Game`, which notifies water only). Bulk edits use `beginBatch` and `endBatch`. | `world/World.java:257-346`; `Game.java:977-980` |
| B-20 | Sky light is 1 at or above the column top, else `max(0.04, 0.72^depth)`, with no lateral spread. Block light comes from emitters within ±1 chunk, with no occlusion and no emitter cap. | `world/World.java:538-577`; `world/Chunk.java:18-19,83-128`; `world/ChunkMesher.java:215-230` |
| B-21 | Player collider 0.6 × 1.8, eye 1.62, auto-step 1.0, jump apex about 1.29 (derived). NPC collider 0.55 × 1.75; creatures 0.34–1.1 wide. NPCs and creatures have no step-up and jump about 1.05 (derived) when blocked. | `entity/PlayerConstants.java:29-33`; `entity/PlayerMovementSystem.java:13-16,94-110`; `entity/Npc.java:123-124`; `entity/Creature.java:11-16`; `entity/Entity.java:135-137`; `ai/Steering.java:52-59` |
| B-22 | Walking bodies have no unloaded-column guard; only Creative flight checks | `entity/VoxelPhysics.java:183-200`; `entity/Entity.java:56-78` |
| B-23 | Texture array: 64 px layers, `MAX_LAYERS = 160` (also `uLayerProps[160]` in the chunk shader). Overflow logs and uses layer 0. About 73 layers are in use by static count; this was not measured at runtime. | `gfx/MaterialRegistry.java:61-62,196-219`; `src/main/resources/assets/shaders/chunk.frag:33` |
| B-24 | Item icons use stable `item/<name>` ids in a 512 × 512 atlas of 32 px icons; overflow throws | `gfx/IconAtlas.java:36-38,280-282`; `src/test/java/com/veylon/gfx/VisualIdTest.java:16-25` |
| B-76 | `Biome` is `MEADOW`, `PINE_FOREST`, `ROCKY_HIGHLANDS`, `MARSH`, `COLD_RIDGE`, `SCRUBLAND`. It is not saved, but fingerprints hash it. Consumers include player state and HUD, temperature, weather, sky and fog, ambience, wildlife spawn chance, plants and the settlement palette. | `world/Biome.java:3-32`; `simulation/TemperatureSystem.java:81`; `simulation/WeatherSystem.java:132`; `entity/EntityManager.java:609-610` |

### Save, compatibility and the save slot

| Id | Fact | Evidence |
| --- | --- | --- |
| B-25 | Magic `VEYL`; writer version 3; minimum readable 2; extension tail `W3EX` v2; `MAX_SERIALIZED_ENTRIES = 100_000`; `MAX_SERIALIZED_BLOCK_EDITS = 4_000_000`; `MAX_SAVE_BYTES` 128 MiB; `SAVE_PATH = <dataDir>/saves/veylon.sav`, a static field | `save/SaveSystem.java:92-108` |
| B-26 | The generator version is written in the base. On load it is validated against `GEN_LEGACY`…`CURRENT_GENERATOR` before `newWorld(seed, false, version)` builds anything. | `save/SaveSystem.java:141,425-430`; `src/test/java/com/veylon/save/CorruptSaveResilienceTest.java:221` |
| B-27 | A load over a live world is first rehearsed on a throwaway `new Game()`. A save goes to a sibling temp file, is flushed and fsynced, then moved atomically (with a non-atomic replace as fallback). | `save/SaveSystem.java:117-137,342-407` |
| B-28 | Section envelope `S3EC`: ≤ 256 sections, each a `writeUTF` id plus an int length (≤ 16 MiB) and the payload. There are 12 ids: `world.lanterns`, `npc.party-state`, `missions.counterattacks`, `quest.target-state`, `settlement.reputation-state`, `combat.active-explosives`, `combat.keg-fuse-attribution`, `player.game-mode`, `world.creative-controls`, `world.bodies` (v2), `world.fragments`, `world.remains`. An unknown id is skipped. A known id with an unsupported version, a duplicate or blank id, a bad length or trailing bytes fails the whole load. | `save/V3ExtensionSections.java:43-92,267-354` |
| B-29 | `changedBlocks` is a last-write log (int x, y, z plus 1 byte), never pruned even when a block returns to its generated type. Every edited chunk is generated synchronously on load. | `world/World.java:43,316-342`; `save/SaveSystem.java:188-193,488-498` |
| B-30 | Every `crateContents` entry is saved as a 12-slot inventory, untouched generated loot included. Load replaces the whole map after the changed blocks. The writer emits counts without a cap; the reader rejects counts above 100,000. | `save/SaveSystem.java:202-207,507-517,968-986` |
| B-31 | `SerializedEnumOrderTest` pins these ordinal-persisted enums: `BlockType` (byte), `ItemType` (110), `Quest.Type` (17), `CreatureType`, `BodyFamily`, fragment pieces, and `NpcArchetype` order. `Weather`, `Affliction` and `EventType` are persisted by ordinal but not pinned. Settlement type, factions, archetype ids, alignment and game mode are persisted by name or id. | `src/test/java/com/veylon/qa/SerializedEnumOrderTest.java:25-171`; `save/SaveSystem.java:145-146,170-174,316,1014-1055`; `save/GameModeSection.java:24` |
| B-32 | `AppPaths`: `veylon.dataDir` overrides; macOS uses Application Support; Windows uses the working directory. Only F5 writes the slot: no autosave, death save or quit save. Tests pass explicit paths. The smoke run writes `<dataDir>/build/qa/smoke-save.dat`. The Gradle test task does not set `veylon.dataDir`. | `util/AppPaths.java:9-31`; `HotkeyRouter.java:124-135`; `AutomatedRunDriver.java:63-64`; `build.gradle:103-121` |
| B-33 | The new-world screen shows "Saving this new frontier will replace your current save." only when the save file exists. Starting a world writes nothing. | `ui/NewFrontierScreen.java:21-23,103-105` |

### Settlements, containers, crafting and quests

| Id | Fact | Evidence |
| --- | --- | --- |
| B-34 | Settlement dynamic state persists by stable names and ids. Load re-plans each region and throws if the plan is now null or its type differs. | `save/SaveSystem.java:716-848,777-801,1014-1055` |
| B-35 | A resident's identity is `(settlementId, residentIndex)`, its position in an append-only list. Dead records stay; attacker victory clears and reseeds the list. | `entity/Npc.java:45-51`; `settlement/SettlementManager.java:762-813`; `settlement/CounterattackDirector.java:312-327` |
| B-36 | Village and fort layouts read persisted resident state (`residents.size()`, captive presence), so a region first built after a load may stamp differently | `settlement/SettlementBuilder.java:142,153,176,239-246` |
| B-37 | 15 `NpcArchetype` roles with stable ids. Activation 96, deactivation 130 and discovery 70 blocks, all 2D. Caps: 40 active NPCs, 32 residents, 12 war party, 8 patrol, 4 bounty hunters, 8 counterattackers, 8 legacy, 16 non-resident. | `settlement/NpcArchetype.java:9-28`; `settlement/SettlementManager.java:33-58` |
| B-38 | Theft is attributed by the crate's 2D position inside settlement bounds, with no per-container owner, against an aggregate stock ledger. It is deduplicated per logical event (≤ 16 transfers). Restricted zones are 5-block XZ radii that ignore Y. | `settlement/SettlementManager.java:72,351-432,500-530`; `CrateTransactionSystem.java:94-146` |
| B-39 | Gates swap `GATE` ↔ `GATE_OPEN` with `record = true` and a persisted 4 s auto-close timer. NPCs open gates on their path, and `Pathfinder` treats `GATE` as passable (cost 2.5). Hostile settlements bar the player, and the player cannot close or lock a gate. | `settlement/SettlementManager.java:60,1336-1384`; `ai/Pathfinder.java:60-97`; `WorldInteractions.java:311-323` |
| B-40 | `CounterattackDirector` allows ≤ 6 missions, ≤ 24 dormant and ≤ 8 active attackers. Outcomes are applied exactly once and persisted in `missions.counterattacks`. | `settlement/CounterattackDirector.java:21-130,293-330` |
| B-41 | `setupCamp` runs on every new world and load. It uses `Random(seed*31+7)`, flattens a 9 × 9 pad from `surfaceHeight` with `record = false`, and spawns 4 named NPCs when fresh. Trust, stocks and upgrades live in `FactionSystem`, which places upgrades at `surfaceHeight`. | `WorldBootstrap.java:232-315`; `ai/FactionSystem.java:26-104,1182-1264` |
| B-42 | Containers are `CRATE` blocks keyed by `Vec3i` in `World.crateContents`. Generation fills them eagerly with `putIfAbsent` (POIs, underground POIs, settlement crates, starter camp). Opening an unknown crate creates an empty 12-slot inventory. | `world/World.java:46`; `CrateTransactionSystem.java:61-71`; `world/WorldGenerator.java:643-750,977-1049,1138-1146` |
| B-43 | Explosions set crates to `AIR` and fire burns them to `ASH` without touching `crateContents`. The orphaned inventories stay saved and spoil-ticked. Only hand mining removes and spills them. | `combat/ExplosionSystem.java:259-271`; `simulation/FireSystem.java:220-226`; `PlayerBlockActions.java:255-268` |
| B-44 | There are no world item entities. Drops and spills go into the player inventory, and anything that does not fit is silently lost. | `PlayerBlockActions.java:255-303` |
| B-45 | Crate transfers move one whole slot per click (partially when the target is full), with no search time. The crate screen does not pause the world and never auto-closes. | `CrateTransactionSystem.java:148-194`; `ui/CrateScreen.java:30-46`; `Game.java:71-80,609-617` |
| B-46 | Player inventory: 36 slots (hotbar 9) plus 5 equipment slots. Stacks carry durability, freshness and charge. Carry capacity is 28 kg plus gear (backpack +14). | `item/Inventory.java`; `item/ItemStack.java:3-27`; `entity/PlayerConstants.java:22,28-29,48` |
| B-47 | Spoilage runs every slow tick over the player inventory and every `crateContents` entry, each with an environment-temperature lookup | `simulation/ItemConditionSystem.java:27-85` |
| B-48 | `ItemType` has 110 constants. There are no keys, pry tools, canned food, bottled water, batteries or urban salvage parts. | `item/ItemType.java:11-184` |
| B-49 | `CraftingSystem.RECIPES` has 64 entries, including 2 relic restorations and 1 blueprint decode. There are 8 `Station` values, not persisted. A recipe needs its station within 3 blocks horizontally and 2 vertically. | `item/CraftingSystem.java:20-158`; `item/Station.java:4-12`; `PlayerEnvironmentSystem.java:43-44,143-176` |
| B-50 | Every iron or copper item needs furnace smelting (the furnace is made from stone and clay) of mined ore with coal. The anvil has no recipe; it exists only in village workshops. The beacon needs ingots, a ruin crystal and camp trust. | `item/CraftingSystem.java:70-90`; `settlement/SettlementBuilder.java:333-338`; `WorldInteractions.java:620-667` |
| B-51 | Hidden tree dependencies: campfire fuel accepts only `LOG` and lantern fuel only `CHARCOAL`. Cooking and boiling need a lit campfire. The water purification, antiseptic and medicine recipes need charcoal. | `WorldInteractions.java:511-547`; `item/CraftingSystem.java:93-116` |
| B-52 | No repair or salvage system exists, except the recipes that restore relic weapons | `item/CraftingSystem.java:149-153` |
| B-53 | Both modes start with 4 berries, 2 logs, 2 sticks, 4 fiber, 2 torches, 1 bandage and an empty waterskin. The Survival intro says "Gather wood and berries; craft tools with [C]." | `WorldBootstrap.java:302-339` |
| B-54 | `Quest.Type` has 17 values (ordinal persisted). There is one global quest slot. Identities and dedupe (≤ 64 credited events; reward claimed once) live in `quest.target-state`. Targets resolve to the nearest matching settlement. A dead giver or a destroyed target never fails a quest. | `ai/Quest.java:18-64,96-174`; `ai/FactionSystem.java:47,111-117,570-574,766-791` |

### Survival, population, AI, combat and UI

| Id | Fact | Evidence |
| --- | --- | --- |
| B-55 | Seven afflictions: bleeding, infection (meaning wound infection), sprain, burn, food poisoning, sickness, smoke. They are persisted by ordinal. Treatments: bandage, splint, antiseptic, poultice, medicine. | `entity/Affliction.java:7-21`; `entity/PlayerTreatmentSystem.java:42-99`; `save/SaveSystem.java:170-174` |
| B-56 | Creative invulnerability gates `Player.hurt`, `hurtPhysical`, `addAffliction`, `onLanded`, knockback and burn injury. Creature attacks also check `isPerceivableByAi()` and invulnerability before hurting. | `entity/Player.java:199-249`; `ai/CreatureAI.java:211-226`; `entity/PlayerAbilities.java:24-33` |
| B-57 | `ShelterSystem` is geometric (an opaque block 2–14 above; 8 rays reaching 4 blocks) and does not read the heightmap. Drinking from open water uses a raycast. | `simulation/ShelterSystem.java:37-69`; `WorldInteractions.java:389-412` |
| B-58 | Open `WATER` is infinite, with a 25 % poisoning chance per drink. Rain collectors hold ≤ 3 L and fill only at the column top. `WaterSystem` moves ≤ 80 cells per medium tick and spreads sideways only at y ≤ 30. | `WorldInteractions.java:389-412`; `simulation/ItemConditionSystem.java:117-131`; `simulation/WaterSystem.java:63-104` |
| B-59 | Temperature cools 0.28 °C per block above y 40, and blends toward 9 °C when the column top is more than 2 blocks above the position | `simulation/TemperatureSystem.java:89-103`; `simulation/TemperatureConstants.java:27-36` |
| B-60 | Audio zone and music use `surfaceHeight − y`: above 5 is a shallow cave; 18 or more is a deep cave with deep-cave music. The cave ambience bed plays above 5. | `engine/AudioEnvironment.java:6-21`; `AudioSceneState.java:18,38,43-53`; `AmbienceSystem.java:76,288-297` |
| B-61 | Sleep is refused when a predator creature is within 12 blocks (3D, no wall check). Sleep quality uses shelter coverage. | `SleepSystem.java:84-136` |
| B-62 | `CreatureType`: `DEER`, `WOLF`, `BIRD`, `HARE`, `THORNHORN`, `STALKER` (ordinal persisted). `BodyFamily`: `HUMANOID` plus one per species, append-only. | `entity/Creature.java:9-48`; `entity/BodyFamily.java:5-44` |
| B-63 | Wildlife spawns at `surfaceHeight + 1` in a 40–100-block ring, with soft per-species quotas, no global creature cap and despawn beyond 170 blocks. Events spawn wolves, raiders and traders at `surfaceHeight + 1`. | `entity/EntityManager.java:384-451,600-621`; `simulation/EventSystem.java:452-515` |
| B-64 | Creature perception is 3D distance plus global noise and scent scalars, with no line of sight. Creatures never pathfind: they steer straight with a 1-block jump. | `ai/CreatureAI.java:31-40,307-316`; `ai/Steering.java:20-73` |
| B-65 | `Pathfinder` is a 4-direction A* with range 56, 700 expansions per query, drops ≤ 3 and paths ≤ 512 nodes. A step up needs 3-high headroom at the departure cell. `GATE` is passable at cost 2.5, water costs 4, and ladders allow vertical moves. Only `SettledNpcAI.moveTo` searches. There is no shared per-tick budget, and each query allocates its own maps. | `ai/Pathfinder.java:19-218`; `ai/SettledNpcAI.java:880-925` |
| B-66 | `WorldNoise` holds ≤ 64 events, each living 6 s. Only `SettledNpcAI` consumes them; creatures use scalars plus a one-shot reaction to radius ≥ 40. Opening and closing gates emits no noise. | `combat/WorldNoise.java:14-147`; `ai/SettledNpcAI.java:172-187`; `settlement/SettlementManager.java:1335-1343` |
| B-67 | Settled NPCs see through an opaque-block ray sampled along the line, scaled by light and halved when the player crouches | `ai/SettledNpcAI.java:120-219` |
| B-68 | Explosions destroy blocks by blast resistance (≤ 900 blocks per blast, batched). Fire spreads through 22 flammable types including `WALL`, `PLANK`, `LADDER`, `GATE` and furniture, with ≤ 220 burning cells. Rain puts a fire out only where nothing opaque is overhead. | `combat/ExplosionSystem.java:30-126,153-305`; `simulation/FireSystem.java:37-240`; `world/BlockType.java:11-77` |
| B-69 | A new species needs cases in `BodyFamily.of`, `FragmentAnatomy.table`, `BodySkeleton.buildCreature`, `CreatureModels.build`, `CreatureAI.update` and the `EntityManager` counters, plus audio methods and save ordinals | `entity/BodyFamily.java:30-39`; `entity/FragmentAnatomy.java:635-645`; `entity/BodySkeleton.java:101-114`; `gfx/model/CreatureModels.java:21-33` |
| B-70 | Hard runtime caps checked by `RuntimeBudgetSnapshot.withinHardLimits` and `RuntimeBoundsTest`: NPC roles (B-37); projectiles 96 live and 40 stuck; path nodes; missions; noise 64; fuses 64; fires 220; liquid-fire patches 160; particles 4,000; fragments 120 live and 600 settled; burn residues 32; ragdolls 12; body flames 1,024. Creatures and block-light emitters have no hard cap. | `qa/RuntimeBudgetSnapshot.java:95-121,155-177`; `src/test/java/com/veylon/qa/RuntimeBoundsTest.java:55-160` |
| B-71 | `MapScreen` draws 64 × 64 cells at 2 blocks per cell around the player, coloured by the column-top block. Markers cover discovered POIs, settlements (discovered or rumoured), the camp, the beacon and the quest. Nothing is cached. | `ui/MapScreen.java:17-226` |
| B-72 | Onboarding is log lines plus pause-menu help: "Explore for blueprints. Befriend the camp. Repair the distress beacon." | `WorldBootstrap.java:325-337`; `ui/PauseMenu.java:53-65` |
| B-73 | Each `BlockType` has one string-id material. Visual variety comes only from position-hash variants or special-shape tiles. There is no per-block state channel. | `gfx/MaterialRegistry.java:15-70,192-194`; `world/ChunkMesher.java:92-164` |
| B-74 | Footsteps distinguish stone, wood, snow and water. Everything else, including stone brick and basalt, sounds like grass. | `engine/AudioManager.java:284-308` |
| B-75 | The game reads 18 `VEYLON_*` environment variables plus the `veylon.dataDir` and `veylon.glDiagnostics` properties. Scenes are a string switch in `QaHarness.applyBenchmarkScene`. Captures go to `<dataDir>/screenshots/<tag>_<N>s.png`. | `QaHarness.java:100-178,575-775`; `AutomatedRunDriver.java:77-152`; `gfx/ScreenshotUtil.java:49,59` |

## 3. Deviations from the pack snapshot (`00_BASE_KNOWLEDGE.md`)

| Snapshot statement | Current finding |
| --- | --- |
| HEAD `ff42cd8`, working tree with unrelated `.gitignore` changes, deleted engineering documents and captures, and untracked instruction and skill files | HEAD is `923345f`, a docs/config-only commit adding `AGENTS.md`, `CLAUDE.md`, three `.agents/skills`, `.graphifyignore` and a `.gitignore` line. `git diff ff42cd8 HEAD` touches no `src/` or build files, and the working tree is clean. The engineering documents are present (22 tracked, 22 on disk). |
| Generator builds heightmap terrain, six biomes, caves, ore, trees, plants, POIs and settlement slices | Confirmed, with additions: lakes and ice, gen-3 cave-identity decoration, underground POIs and per-region cave-entrance shafts (gen ≥ 2). Heights and lights are finalised in `World.getOrCreateChunk` (B-15). |
| `>= GEN_DEEP` checks could leak into an urban branch | Confirmed and wider: also `World.isBasaltFumarole`, the `EntityManager` stalker zone and the `QaHarness` smoke checks. Terrain, lakes, base ores, trees and surface POIs are ungated (B-13). Many tests pin "v3" through `CURRENT_GENERATOR`, and gen 2 has no absolute fingerprint. |
| Settlements: a pure regional planner | The planner is pure. The builder is not: layout reads persisted resident state (B-36). Residents are list indexes (B-35). |
| Pathfinder: range 56, expansion 700, steps, drops, gates, ladders | Confirmed. In addition, only settled NPCs use it, creatures never pathfind, there is no shared budget (B-64, B-65), and its "loaded" test means "ever generated", because chunks are never removed (B-17, B-18). |
| Loot: `crateContents`, transactions, crate screen, POI and settlement loot | Confirmed. In addition: every crate is saved, blast and fire orphan contents, full inventories silently lose items, and there are no world items (B-30, B-42 to B-44). |
| `CraftingSystem` includes beacon progression | Partly. The beacon stages and victory live in `WorldInteractions`. The anvil has no recipe (B-50). There are 64 recipes; a milestone-01 auditor's count of 60 was rejected on recount (§7). |
| Bounded `WorldNoise` and perception gates | Confirmed. However, only settled NPCs consume `WorldNoise` events (B-66). |
| Frontend: new world and mode screen, one save slot, HUD, NPC, quest, container and map screens | Confirmed. There is no quest log screen; quests show through `NpcScreen` and the `QuestObjectiveView` overlay. |
| Stable material ids | Material ids are strings and never ordinals, but no test pins block material ids. `VisualIdTest` pins item icon ids only (B-23, B-24). |
| Writer v3; reader v2 and v3; unknown sections vs unsupported known versions; limits; byte block ids | All confirmed (B-25 to B-30). In addition: ≤ 256 sections of ≤ 16 MiB each; the effective block-id capacity is 128, not 256 (B-11); and the crate writer is uncapped against the reader's 100,000 cap (B-30). |
| Heightmap reports the highest opaque block; spawn, navigation, water access, shelter and indoor weather cannot use it as ground | The semantics are confirmed (leaves count, water does not). Shelter is already geometric and drinking uses a raycast (B-57). Column-top rain exclusion is correct indoors. The real breakages are listed in §4.1. |
| `VEYLON_SEED`, `VEYLON_GAME_MODE`, `VEYLON_SCENE`, `VEYLON_SHOT` and `VEYLON_CAPTURE_TAG` exist | Confirmed, along with 13 more variables and 2 properties (B-75) |
| `docs/ARCHITECTURE.md` §4 says `setBlock(..., notify)` and that bulk edits pass `notify = false` | The parameter is `record`. The listener always fires. Bulk edits use `beginBatch`/`endBatch` (B-19). This is a historical documentation deviation; milestone 01 records it and does not edit that file. |
| `docs/DEVELOPING.md` says "886 deterministic tests" | This is a historical figure. No test count is asserted here (pack rule). |

## 4. Risk register

| Id | Risk | Evidence | Required handling | Owner |
| --- | --- | --- | --- | --- |
| R-01 | Column height is a roof: heightmap and `surfaceHeight` consumers misplace or misclassify things in a city | §4.1 | Plan-based street, floor and room queries; urban-gated classification; legacy byte-identical | 02, 03, 06, 08, 09, 13, 21, 22 |
| R-02 | Block-id and visual capacity | B-11, B-23, B-24 | Budgets in DECISIONS §3; measure layers at boot in 04; no per-variant ids; widening only through C-8 | 04 and every content package |
| R-03 | CPU chunk retention and growth collections | B-17, B-29, B-30 | §4.3 | 11 (measure), 24 (decide); **24:** decided, D-241 |
| R-04 | Single real save slot | B-25, B-32, B-33 | §4.4 | 02 |
| R-05 | Monotonic generator gates leak legacy features | B-13 | D-02 plus tests that `GEN_URBAN` plans no settlements, caves, ores, POIs or stalkers | 02 |
| R-06 | `CURRENT_GENERATOR` is both the new-world default and the load ceiling | B-02, B-26 | D-03 | 02 |
| R-07 | Legacy pins reference `CURRENT_GENERATOR`; gen 2 is only relatively pinned | B-13 | C-1, C-2 before any bump or refactor | 02 |
| R-08 | Line budgets nearly exhausted | B-09 | D-16: new collaborators and classes; never raise budgets to fit | All |
| R-09 | Container defects: orphaned contents, full-inventory loss, eager saving against the reader cap, spoilage cost | B-30, B-43, B-44, B-47 | D-12 | 07 |
| R-10 | Dark interiors, block light through walls, no emitter cap | B-20 | D-19; emitter cap in `RuntimeBoundsTest` | 06, 22 |
| R-11 | Interiors treated as caves by temperature, ambience, reverb and music | B-59, B-60 | D-19 indoor state | 06, 22 |
| R-12 | Hostiles built on creature AI would see through walls and walk into walls | B-64, B-66 | D-20 | 09, 10 |
| R-13 | No shared path budget; per-query allocation; range and expansion limits | B-65 | Portal graph plus shared budget (DESIGN §10) | 10 |
| R-14 | No loaded-column API; AI bodies can walk into unloaded columns; `surfaceHeight` generates chunks | B-18, B-22 | A public loaded-column query (03, done: `World.isColumnLoaded`, D-43); AI movement guard (10) | 03, 10 |
| R-15 | Fire spreads through wooden interiors and is never rained out under roofs | B-68 | Explicit flammability per urban material (04); indoor fire budget (22) | 04, 13, 22 |
| R-16 | Legacy layout depends on persisted resident state (existing legacy defect) | B-36 | Urban layouts are pure (D-21). Fixing legacy needs owner approval and a save-compatibility review; not in roadmap scope. | 16 (urban) |
| R-17 | Resident identity is a list index | B-35 | Stable slot ids (D-21) | 16 |
| R-18 | One quest slot, nearest-settlement targeting, no failure on giver death | B-54 | D-30 | 20 |
| R-19 | The map shows roofs; its per-frame cost is uncached | B-71 | A cached plan layer | 21 |
| R-20 | `Weather`, `Affliction` and `EventType` ordinals are not pinned | B-31 | C-7 | 08, 09 |
| R-21 | `Locale.ROOT` missing in display code, for example `InteractPromptBuilder.java:232` ("Requires <tool>") and settlement display strings | Audit findings | Fix in the package that next touches the code | 04, 21 |
| R-22 | `WaterSystem` spreads sideways without limit at y ≤ 30 | B-58 | Datum D-05 keeps urban voids at y ≥ 33; flooded overlays proven stable (13) | 03, 13 |
| R-23 | No bedrock | B-14 | y 0–1 always solid (D-05) | 03, 14 |
| R-24 | One seed per process | B-05 | P-06 | 02, 21 |
| R-25 | Per-chunk planning scans: legacy re-rolls 576 underground plans and filters whole layouts per chunk; `registerPoi` does a linear duplicate scan | `world/WorldGenerator.java:852-868,1128-1138`; `world/World.java:642-650` | Urban plans use bounded caches, chunk-bucketed layouts and indexed maps | 03, 06 |

### 4.1 Roof-height and absolute-height sites

Every site below assumes that the column top (or the noise terrain height, or an absolute y) is ground. The owner package must replace or gate it for urban worlds, or record why the legacy semantics stay correct. Legacy worlds stay unchanged.

| Id | Site | Behaviour | Urban effect | Handling (owner) |
| --- | --- | --- | --- | --- |
| H-01 | `WorldBootstrap.placePlayerOnDryLand` (`WorldBootstrap.java:198-226`) | Spawns at `surfaceHeight + 1.2` after a ring search | Spawns on roofs; respawn reuses it | Plan-based spawn (02 skeleton, 08) |
| H-02 | `WorldBootstrap.setupCamp` and pad flattening (`WorldBootstrap.java:232-300`) | Clears 7 blocks above and fills 3 below a 9 × 9 pad on every load | Carves through buildings | Disabled for urban (02) |
| H-03 | `FactionSystem` camp upgrades (`ai/FactionSystem.java:1209-1264`) | Walls and watch post at `surfaceHeight + 1`; comforts at camp y | Rooftops or floating blocks | Disabled for urban (02) |
| H-04 | `EntityManager.trySpawn` (`entity/EntityManager.java:600-621`) | Wildlife at `surfaceHeight + 1` | Rooftop deer and wolves | Disabled in urban (03, done: D-45); anchor spawns (15) |
| H-05 | `EntityManager.trySpawnWolf` (`entity/EntityManager.java:455-480`) | Den test mixes noise height and heightmap | Rooftop wolves | Disabled in urban (03, done: D-45) |
| H-06 | Stalker zone and darkness spawn (`entity/EntityManager.java:442-451,534-548`) | Noise depth (gen ≥ 3) or `y < surface − 6`, plus darkness | Gloomstalkers in basements and dark rooms | Explicit predicate excludes urban (02) |
| H-07 | `World.isBasaltFumarole`, `WorldGenerator.caveZone` (`world/World.java:595-599`; `world/WorldGenerator.java:98-121`) | Depth below noise height | Fumaroles in deep basements | Excluded for urban (02) |
| H-08 | `EventSystem` meteor, wolf, raider and trader (`simulation/EventSystem.java:415-515`) | Placed at `surfaceHeight` | Roof spawns; meteors carve roofs | Disabled in urban (03, done: D-45); adapted 09, 17 |
| H-09 | `NpcAI.findBlock` (`ai/NpcAI.java:489-510`) | Scans a band around the heightmap | Camp NPC jobs miss interiors | Legacy camp is not used in urban (02) |
| H-10 | `SettledNpcAI` abstract travel height (`ai/SettledNpcAI.java:368-376,462-469`) | Heightmap or noise height + 1.4 | Parties jump between roof and street | Urban sites use plan heights (16, 17) |
| H-11 | `SettledNpcAI` sight light scale (`ai/SettledNpcAI.java:143-146`) | `skyLight` × daylight | Players indoors nearly invisible by day | Decide with D-19 (10, 22) |
| H-12 | `CreatureAI` bird target; `FirePanic.flightRise` (`ai/CreatureAI.java:582-593`; `ai/FirePanic.java:628-637`) | Column top + offset | Birds circle roofs (acceptable) | Review in 15 |
| H-13 | `CreatureAI` light exposure (`ai/CreatureAI.java:511-518`) | `skyLight`-based darkness refuge | Interiors count as caves for light-averse predators | 15 |
| H-14 | `World.skyLight`, `ChunkMesher` vertex light, entity light (`world/World.java:538-551`; `world/ChunkMesher.java:215-230`; `engine/Renderer.java:1088-1091`) | Depth below the column top | Black interiors; no window light | D-19 (06, 22) |
| H-15 | `TemperatureSystem` underground blend (`simulation/TemperatureSystem.java:94-103`) | Toward 9 °C below the column top | Every interior holds 9 °C; seasons vanish indoors | Indoor model (06) |
| H-16 | `TemperatureSystem` altitude cooling (`simulation/TemperatureSystem.java:89-91`) | 0.28 °C per block above y 40 | Upper floors colder (y 85 is about 12.6 °C colder) | Decide keep or scale per D-05 (06, 13) |
| H-17 | `AmbienceSystem` cave bed (`AmbienceSystem.java:76,288-297`) | Depth > 5 | Cave drone in ground floors | Indoor state (06, 22) |
| H-18 | `AudioSceneState` and `AudioEnvironment` zone and music (`AudioSceneState.java:18,38,43-53`; `engine/AudioEnvironment.java:6-21`) | Depth > 5 shallow; ≥ 18 deep cave | Cave reverb and deep-cave music indoors | Indoor zones (06, 22) |
| H-19 | `Player.exposedToSky` (`entity/Player.java:288-296`) | Column top above the head | Correct indoors; balconies and overhangs count as cover | Retain; review with glass (04, 22) |
| H-20 | `FireSystem.isPrecipitationReaching` and consumers (`simulation/FireSystem.java:146-156`) | `skyLight > 0.9` | Indoor fires never rained out (correct); fires on balconies too | Retain; indoor fire budget (22) |
| H-21 | Rack and collector rain (`simulation/ItemConditionSystem.java:100-131`) | Column top | Rooftop collectors work; under eaves they never fill | Retain (19) |
| H-22 | `RainField` particles (`engine/RainField.java:41-46`) | Spawn above the column top; skip columns more than 24 above the player | No visible rain between tall towers | 22 |
| H-23 | `WaterSystem` rain refill and spread (`simulation/WaterSystem.java:82-104`) | Refill at `surfaceHeight + 1` ≤ 30; sideways spread at ≤ 30 | Floods connected low voids | Datum D-05 (03); flooded overlays (13) |
| H-24 | `PlantSystem.growChunk` (`simulation/PlantSystem.java:79-140`) | Regrowth at the column top | No regrowth under overhangs; rooftop soil grows | 13 |
| H-25 | `WeatherSystem.strikeLightning` (`simulation/WeatherSystem.java:171-192`) | Strikes `surfaceHeight` | Strikes roofs (ignites logs and leaves only) | 22 |
| H-26 | `MapScreen` colour and shade (`ui/MapScreen.java:35-60`) | Column-top block, shaded by y | Shows roofs, not streets or floors | Plan layer (21) |
| H-27 | `SettlementPlanner` and `SettlementBuilder` siting (`settlement/SettlementPlanner.java:113-144`; `settlement/SettlementBuilder.java:252-273`) | Noise terrain height | Not used in urban | Gated off (02) |
| H-28 | `WorldGenerator` surface and underground POIs, decoration, caves, lakes (`world/WorldGenerator.java:183-250,543-641,795-906`) | Noise terrain height | Not used in urban | Urban branch skips them (02, 03) |
| H-29 | `CounterattackDirector` regional-force origin (`settlement/CounterattackDirector.java:86-92`) | Origin y = target centre y | Parties materialise on the wrong floor | 17, 18 |
| H-30 | `PlayerEnvironmentSystem.nearbyStations` (`PlayerEnvironmentSystem.java:143-156`) | ±2 y box | Stations work through floors | Accept or same-room rule (18) |
| H-31 | `ShelterSystem` thresholds (`simulation/ShelterSystem.java:43-67`) | Roof ≤ 14 above; rays ≤ 4 | Large halls and atriums read as outdoors | Room-plan indoor state (06, 12) |
| H-32 | QA staging: smoke stack and scene actors (`AutomatedRunDriver.java:244-252`; `QaHarness.java:815-835`; `CreativeQaScenes.java:120,138`) | `surfaceHeight` or noise height | Actors on roofs in urban seeds | Urban QA scenes in their own class (03, 11). **03:** `UrbanQaScenes` and the smoke stress stack use the plan ground; the legacy scenes are unchanged |
| H-33 | `Pathfinder` snap and drop (`ai/Pathfinder.java:26,150-166`) | Snaps ≤ 3 down or 1 up; drops ≤ 3 | Floors more than 3 apart need stairs the pathfinder can follow | Stair rules A3 (05); portals (10) |

### 4.2 Byte-id and visual capacity

Block ids: 57 used, and `byId` rejects negative bytes, so 71 ids remain (B-11). Every block state that changes collision needs its own id, because there is no per-block state channel (B-73). This is why doors use id pairs and side state (D-18).

Texture layers: 160, coupled to the shader. Overflow silently renders the fallback layer. About 73 layers are used by static count (B-23). Package 04 must log the real count at boot and add a check that `layerCount ≤ MAX_LAYERS`. **04:** the boot line now reads `[materials] 76 materials, 104 of 160 texture layers` (measured in the native runs of 2026-10-08), and `UrbanMaterialsTest` builds the registry headlessly through `MaterialRegistry.census()` and fails above `MAX_LAYERS` or on any missing texture or material. Block ids after 04: 78 used, 50 free (D-54).

Icons: about 225 shelf slots (an estimate), and overflow throws at startup (B-24). Budgets are in DECISIONS §3.

### 4.3 CPU chunk retention and growth

Chunk block data is never freed (B-17).

Arithmetic, not a measurement: one retained chunk is 24,576 B of blocks + 1,024 B of height map + objects and light lists ≈ 26 KB. At the default render distance 6 the streamed swath is 13 chunks wide. So:
- 1 km of straight travel retains about 820 chunks, about 21 MB;
- exploring a 2 × 2 km area retains about 15,600 chunks, about 400 MB, of a 2 GB launcher heap (`build.gradle:40`).

Dense interiors do not enlarge block arrays. They do add plan caches and container and fixture metadata. Legacy collections that grow with travel: settlements, `plannedRegions`, `settlementLayouts`, `pois`, `changedBlocks`, `crateContents` (B-17, B-29, B-30).

Required handling:
- urban caches are LRU-bounded;
- urban state is sparse (touched only);
- package 11 records retained chunks and heap after the slice route (measured in [Milestone 11](#milestone-11--playable-vertical-slice-gate-2026-10-13); its fixed arrays are 26,624 B per chunk, because the precipitation height map adds 1,024 B to the ≈26 KB above);
- package 24 decides eviction or a finite metropolis (D-24, P-03) and proves that no state is lost. **24:** decided and proven (D-241, D-242): a generated city drops its farthest chunks past 2,048 and regenerates them identically; `ChunkRetentionTest` finds the save of a world that dropped 1,000+ chunks on a 3 km journey identical to its twin's that kept every chunk. Measured in [milestone 24](#milestone-24--dense-city-performance-and-streaming-2026-11-05): 27 KB a retained chunk; a fast 8 km journey holds 1,803 chunks and 72.6 MiB after collection instead of 6,664 and 199.4 MiB.

### 4.4 The single real save slot

There is one slot, `<dataDir>/saves/veylon.sav` (static). On Windows, the data directory is the process working directory, which is the repository checkout when run from Gradle. Only F5 writes the slot (B-25, B-32). The replace notice is truthful because starting a world writes nothing (B-33). Isolation of tests and QA is by convention only today.

Required handling:
- package 02 adds the structural seam of D-23;
- every native QA run in this roadmap sets `-Dveylon.dataDir` to a directory under `build/qa/`;
- no package claims isolation through an environment variable that does not exist;
- during development, urban worlds follow P-05.

## 5. Validation matrix

The **always** set runs for every code package, together with the portable build (`.\gradlew.bat build --console=plain`, which includes Javadoc and doclint):

`com.veylon.world.WorldGeneratorFingerprintTest`, `com.veylon.save.HistoricalV020SaveCompatibilityTest`, `com.veylon.WorldSeedDeterminismTest`, `com.veylon.qa.SerializedEnumOrderTest`, `com.veylon.qa.RuntimeBoundsTest`, `com.veylon.qa.OrchestratorSizeTest`.

| Package | Also keep green | New evidence required | Native evidence |
| --- | --- | --- | --- |
| 02 | `save.SaveMigrationTest`, `save.CorruptSaveResilienceTest`, `GameLoopIntegrationTest`, `world.CaveIdentityGenerationTest`, `world.DeepCaveGenTest`, `util.AppPathsTest`, `ModeSelectionRoutingTest` | Absolute gen-2 fingerprint; dispatch and revision round trip through production bootstrap; unsupported-revision rejection; absent urban section in old saves; reset over a live world; QA data-dir isolation test | Only if a user-visible selector is added |
| 03 | `settlement.SettlementPlannerTest`, `world.WorldCoordinateContractTest` | Plan and generated-chunk seam and order matrix (DESIGN §11); "no wilderness features" assertions | Streetscape and intersection captures; a seam walk |
| 04 | `input.PlayerInteractionSystemTest`, `CreativeBuildingTest`, `ai.PathfinderTest`, `world.RaycasterTest`, `gfx.VisualIdTest`, `gfx.ProceduralTexturesTest`, `SurvivalCreativeParityTest` | Door, window, stair and fixture interaction; collision, raycast and navigation agree; fixture-state round trip; failed entry; full-inventory drop; texture-layer count check | Both sides of doors, stairs and windows; night readability |
| 05 | `ai.PathfinderTest`, `world.urban.{BuildingPlannerTest,AccessibilityValidatorTest}`, `entity.BuildingPlanTraversalTest`, `save.UrbanMetaSectionTest`, `qa.RuntimeBoundsTest` | Production validator on valid/invalid voxels; 768 nominal proposals; 4,112 frozen-matrix parcels; shuffled access/eviction; real player stairs; saved revision; bounded work | Optional plan-debug geometry; no 06 exploration claim |
| 06 | `simulation.EnvironmentalSimulationTest`, `PlayerEnvironmentSystemTest`, `engine.AudioEnvironmentTest` | Generation, accessibility and state checks over all playable rooms; persisted breaks survive regeneration; indoor classification | Street → entrance → kitchen → upper floor → basement/exit, day and night; a manual Survival walk |
| 07 | `settlement.SettlementTheftTransactionTest`, `settlement.SettlementReputationGameplayTest`, `save.*` | Round trip; interrupted search; full inventory; destroyed-container tombstone through hand, blast and fire; corruption; ownership | Searching a generated kitchen and an owned cupboard while the world runs |
| 08 | `ModeSelectionRoutingTest`, `item.ExpansionProgressionTest`, `BeaconEndgameIntegrationTest`, `entity.PlayerTreatmentSystemTest`, `SurvivalCreativeParityTest` | Starter guarantees per seed; a production route with no tree and no ore; consumed or full-inventory supplies never strand progression | A fresh opt-in urban session: spawn and first scavenging return |
| 09 | `CreativePerceptionTest`, `CreativeHazardsTest`, `combat.*`, `AllLivingEndToEndTest`, `entity.FragmentAnatomyTest`, `gfx.FragmentAnatomyModelTest`, `gfx.FlameAnchorsTest` | Fair senses, pursuit, attack through the damage gates, fire reaction, clean death; encounter persistence; caps; sanctuary; Creative twin | A generated street and a ground-floor apartment |
| 10 | `ai.PathfinderTest`, `ai.HumanPerceptionGameplayTest`, `settlement.VillageDailyLifeGameplayTest`, `ai.FirePanicTest` | Multi-floor, occlusion, loaded-boundary, dynamic-door and work-bound tests; perception twins | Pursuit and escape in a generated building |
| 11 | Everything above | Slice integration route through production entry points; save, reload over a live world and revisit in a different order | The complete §3.1 route with captures; hardware and settings recorded |
| 12 | 05–07 tests | Family × rotation × seed accessibility and order matrix; loot and save tests | Every family, an upper floor, a locked alternative entry and a damaged variant |
| 13 | `simulation.EnvironmentalSimulationTest`, 03 tests | District distribution and reachability; overlay accessibility; order; save | One transition; all six identities in day, night and fog |
| 14 | 05, 06 and 10 tests | Plan, portal and accessibility checks across boundaries and orders; pathing; save | Underground and roof routes in low light and flood |
| 15 | `entity.EntityEcologyTest`, 09 tests | AI, combat and habitat; death and harvest rules; save and reset; parity | A quarantine encounter with its tell and counter; one wildlife habitat |
| 16 | `settlement.*` | Site distribution and accessibility; activation and dormancy; aid transactions; identity and save; parity | Finding the basement from clues; visiting a camp |
| 17 | `settlement.*`, `ai.HumanPerceptionGameplayTest` | Entry, guard AI, reputation and theft, response caps, capture and dormancy, save; parity | Peaceful entry, a restricted route and a defended intrusion |
| 18 | 04 and 07 tests, `CreativeBuildingTest` | Claims and ownership; salvage transactions; fortification collision and navigation; sleep; save; parity | Full home setup with normal resources and a real threat |
| 19 | `simulation.*` | Resource accounting; repair prerequisites; pause and dormancy; light and state; save and corruption; parity | Repair, powered behaviour, depletion, shutdown and noise |
| 20 | `ai.FactionQuestTest`, `ai.QuestGameplayEventIntegrationTest`, `save.QuestTargetPersistenceTest`, `BeaconEndgameIntegrationTest` | Event, target, save and idempotency tests; prerequisites and failure; completion; integration route | Representative missions and the final objective |
| 21 | `ui.HudLayoutTest`, `ui.MapScreenTest`, `ui.HudTextTest`, `HudQaSceneTest` | Input, routing, layout, discovery and persistence | `docs/graphics/HUD_VALIDATION.md` capture matrix in both modes |
| 22 | `engine.*` audio tests, `gfx.*` | Material, model and state checks; audio selection and reset | Before and after captures; manual listening with normal and dry-fallback devices |
| 23 | 08 and 20 tests | Resource, prerequisite and transaction tests; a multi-day production integration | Fresh extended play with day count, revision, seed, gear and choices |
| 24 | `qa.RuntimeBoundsTest` and allocation tests | Bounds, allocation and state; eviction and revisit order if eviction is chosen. **24:** `ChunkRetentionTest`, `ChunkStreamingScheduleTest`, `ChunkMesherFingerprintTest`, `world.ChunkMapTest`, `entity.ChunkUnloadCachesTest`, `ai.NavigationCacheUnloadTest`, `UrbanPerformanceQaSceneTest` and `RuntimeBoundsTest`'s threat budgets | `.\gradlew.bat performanceTest --console=plain` (opt-in, calibrated host) plus native city measurements |
| 25 | `save.*`, fingerprints, quest, settlement and parity tests | Integrated urban fixture across orders and revisits; corruption and atomic replacement. **25:** `UrbanIntegratedSaveTest`, `save.UrbanSaveCorruptionTest`, `save.CorruptSaveResilienceTest`, `save.HistoricalV3SaveCompatibilityTest`, `save.UrbanEarlierRevisionSaveTest`, `world.urban.RoofOpeningFallbackTest` and `SaveAuditQaSceneTest` | A genuine legacy world and the integrated urban state, from isolated saves. **25:** `VEYLON_SCENE=save_audit` on the integrated city and on the v0.3.0 and v0.8.1 Survival and Creative saves |
| 26 | Everything relevant | Rollout path tests | Default-path smoke and the manual acceptance scenario from the pack README |

Test names in the table are relative to `com.veylon.`. Run them with `.\gradlew.bat test --tests "com.veylon.<name>" --console=plain`.

## 6. Seed matrix registry

| Set | Seeds | Frozen by | Status |
| --- | --- | --- | --- |
| Legacy pins | Gen 3: `20260731`, `-4242`, `7`. Gen 1 fixture: `1122334455`. Gen 2 deep-cave tests: `987654321`. Since 02, absolute gen-1 and gen-2 terrain fingerprints over `20260731`, `-4242`, `7` (recorded at `923345f` from unchanged code). | Existing tests; 02 | In force |
| Urban generator 4 (placeholder) | `20260731`, `-4242`, `7` (fingerprint); the six design candidates for the datum, dispatch and order checks (`world.UrbanGeneratorDispatchTest`) | 02 | In force, frozen with generator 4 |
| Urban design | `20260731`, `-4242`, `7`, `0`, `987654321`, `-9000000000000000000`, over the regions `(0,0)`, `(-1,-1)`, `(1,-1)`, `(-1,1)`, `(2,3)`, `(-3,-2)`, `(4096,-4096)` and `(-4097,4095)` (`world.urban.CityPlannerTest`, `world.UrbanStreetNetworkGenerationTest`); generator-5 fingerprints over `20260731`, `-4242`, `7` | 03 | **Frozen** (2026-10-08) |
| Starter | The six urban design seeds `20260731`, `-4242`, `7`, `0`, `987654321`, `-9000000000000000000` (`UrbanStarterProgressionTest.SEEDS`, `world.urban.UrbanStarterGenerationTest`, `UrbanEncounterPopulationTest`) | 08 (exercised); recorded by 11 | **Frozen** (2026-10-13) |
| Building template 1 | Family fingerprints at `20260731`, `-4242`, `7`; nominal matrix at all six urban-design seeds, four footprint sizes, four frontages, four variants and both families; actual parcels across all eight frozen design regions | 05 | Frozen by generator 6 / layout 3; existing generator 1–5 pins retained |
| Slice regression | The same six seeds in Survival and Creative, headless (`UrbanSliceRouteTest`; chunk-order matrix on `20260731` and `7`), plus native `20260731` in both modes with the 16-capture list in DEVELOPING ("playable-slice gate") | 11 | **Frozen** (2026-10-13) |
| Districts | — | 13 | Not yet frozen |
| Economy | — | 23 | Not yet frozen |

## 7. Evidence log

### Milestone 01 (2026-10-07, documentation only)

Commands and checks actually run (Git Bash and PowerShell on the Windows host):

| Check | Result |
| --- | --- |
| `git status --short`, `git log --oneline -5` | Clean tree; HEAD `923345f` |
| `git diff --name-status ff42cd8 HEAD` | 7 files: agent skills, `AGENTS.md`, `CLAUDE.md`, `.gitignore`, `.graphifyignore`; no source or build change |
| `diff -rq` of the two roadmap pack copies (Desktop folder and `build/urban-survival-roadmap`) | Identical |
| Existence check of every path in the pack's source navigation map | All exist |
| `graphify query` and `graphify explain` (generator dispatch, `Chunk`, `Pathfinder`) followed by focused source reads | Facts recorded in §2 |
| Main-session recount: line budgets (`wc -l`), section ids, `ItemType`, `Quest.Type`, `RECIPES` | 990 / 1,488 / 1,117 / 1,419 / 1,265 / 1,178 lines; 12 sections; 110; 17; **64** (an auditor reported 60; rejected) |
| Read-only audit workflow: 18 areas, each followed by an adversarial verifier | **Failed.** All agents stopped on an account session limit before returning anything; no evidence came from this run |
| Lean read-only audit workflow: 9 merged areas | Completed. Its claims were inputs, then cross-checked against source as recorded below |

Document verification of these four records:

| Check | Result |
| --- | --- |
| Markdown links and heading anchors in all four records | All resolve |
| Every backticked source citation (218 distinct path or path:line forms): file exists and line ranges are within file length | All pass. The only unresolved names are roadmap-pack files and repository-root files, which §1 now names explicitly. |
| Cross-references: every `B-`, `D-`, `H-`, `R-` and `P-` id used is defined | All defined (76 / 31 / 33 / 25 / 14) |
| Keyword-anchored citation check: for each B-row and H-row, the identifiers it names must appear in its cited ranges (±3 lines) | All 57 identifier-bearing B rows pass. The 19 prose-only B rows were then checked with targeted patterns, and all have their evidence at the cited lines. H-row misses were class names absent from method bodies (citations exist). |
| Targeted numeric re-checks: settlement caps and radii, streaming budgets, render distance, section limits, water budget, collector cap, spawn ring and despawn, noise limits, texture and icon atlas, carry capacity, sleep radius, projectile and fire caps | All match source |
| Recounts and lists by the main session | Found and fixed two omissions: `CorruptSaveResilienceTest` also references `CURRENT_GENERATOR` (C-2), and `World(long seed)` defaults to it (D-03) |
| Five-lens read-only review workflow (two fact-checkers, feasibility, completeness, consistency) | **Failed.** All agents stopped on the account session limit before returning anything. It was replaced by the checks above; no independent semantic review of the design text was obtained. |

Not run: no Gradle build, test, benchmark, native session or capture. Milestone 01 changes documentation only and makes no claim of fresh build or runtime evidence.

### Milestone 02 (2026-10-07)

Host: Windows 11 Pro, NVIDIA GeForce RTX 3060 Ti (driver 610.88, OpenGL 3.3). JDK: Temurin `25.0.4.1+1`, fetched again into the session scratchpad with its SHA-256 checked against Adoptium's published value. Commands ran from the repository root. Logs are in `build/m02-*.log` (ignored by git, never cleaned).

| Check | Result |
| --- | --- |
| Safety net on the **unchanged** generator: the C-2 retargets, then `WorldGeneratorFingerprintTest`, `CaveIdentityGenerationTest`, `SaveMigrationTest`, `CorruptSaveResilienceTest`, `RuntimeBoundsTest` and `HistoricalV020SaveCompatibilityTest` | Pass: 5 + 3 + 5 + 13 + 5 + 1 tests. The absolute gen-1 and gen-2 terrain fingerprints were read from this unchanged code, then recorded as constants |
| `grep` for `CURRENT_GENERATOR` in `src/` and `docs/`, apart from these records | No references left |
| First focused run of the new tests | The gen-4 fingerprint placeholder failed, as intended, and was recorded. `UrbanIdsTest` found a real bug: `parse` expected 7 fields for a resident instead of 6. Fixed |
| The 02 validation set from §5, the always set and `com.veylon.save.*`, after the fix | 146 tests, 0 failures |
| Read-only review workflow: 3 reviewers (save and load; legacy gates and runtime; bootstrap, selector and ids) and 1 adversarial verifier | 3 low-severity findings, 2 of them the same issue. (1) Confirmed: `VEYLON_FRONTEND` staging ignored the selector; fixed. (2) Plausible: the isolation guard compared path text, so an alias got through; it now compares file identity. A remark from the first pass was also applied: new tests that mean "legacy world" now name generator 3. The reviewers' truth tables for every converted gate on versions 1–3 matched |
| `.\gradlew.bat build` on the tree before the review fixes | BUILD SUCCESSFUL in 6 min 27 s. 150 test classes, 1,562 tests, 0 failures, 0 skipped; javadoc included |
| Focused tests on the review fixes: `AppPathsTest`, `UrbanDevelopmentSelectorTest`, `UrbanMetaSectionTest`, `GameLoopIntegrationTest`, `WorldSeedDeterminismTest`, `ModeSelectionRoutingTest`, `SaveSlotIsolationTest` | Pass. On this host a symbolic link needs a privilege, so the link case skips through an assumption |
| Manual alias check: a junction (`mklink /J`) from the scratchpad to the checkout, passed as `-Dveylon.dataDir` to `jshell` calling `AppPaths.isolatedFromPlayerData()`; the junction was removed afterwards | Junction: `false` (not isolated). The scratchpad itself and `build/qa/m02-check`: `true` |
| **Final** `.\gradlew.bat build --console=plain` on the finished tree | BUILD SUCCESSFUL in 6 min 24 s. 150 test classes, **1,565 tests, 0 failures, 1 skipped** (the symlink case above); javadoc and doclint included |
| Native negative check: `VEYLON_GENERATOR=urban-dev .\gradlew.bat run` without `-PveylonDataDir` | Refused at start with "VEYLON_GENERATOR=urban-dev needs an isolated data directory…". No `saves/` was created |
| Native isolated urban smoke: `VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_SMOKE=10 VEYLON_SHOT=4 VEYLON_CAPTURE_TAG=m02_urban_dev .\gradlew.bat run -PveylonDataDir=build/qa/m02-urban` | Smoke gate passed (exit 0). Save and load were both true at `build/qa/m02-urban/build/qa/smoke-save.dat`; `fortressPlan={available=false,generator=urban}`. Average 140.3 fps, 1280 × 720, VSync on; 0 GL and 0 KHR errors; runtime within hard limits; 3 wildlife creatures (the legacy spawner, D-28). The capture `build/qa/m02-urban/screenshots/m02_urban_dev_4s.png` shows the stone-brick street datum to the horizon and the development notice. Nothing was written outside `build/qa/m02-urban`; the checkout has no `saves/` |
| `graphify update . --no-cluster`, run once after the code was final | Graph rebuilt: 6,471 nodes, 24,448 edges. The new classes are indexed |

Not run: `performanceTest` (02 adds no per-frame work, and this host is not the calibrated reference) and `releaseArtifacts`. No older binary was executed: the rejection of an urban save by 0.8.1 is established by reading code (PROGRESS, milestone 02).

### Milestone 03 (2026-10-08)

Host and JDK as for 02: Windows 11 Pro, NVIDIA GeForce RTX 3060 Ti, Temurin `25.0.4.1+1` from the scratchpad. Commands ran from the repository root through Git Bash. Logs are in `build/m03-*.log` (ignored by git, never cleaned); native captures are under `build/qa/m03-*/screenshots/`.

| Check | Result |
| --- | --- |
| Planner probe through `jshell` on the compiled classes: `CityPlanDiagnostics.inspect` for the six design seeds × eight matrix regions, and an ASCII render of the starter superblock | 48 of 48 regions `ok=true`; the render showed the courtyard ring, two passages, rear doors onto the walkway and the spawn anchor at the refuge door |
| First focused run: `world.urban.*`, `world.UrbanStreetNetworkGenerationTest`, `world.UrbanGeneratorDispatchTest`, `world.WorldGeneratorFingerprintTest` (`build/m03-focused-1.log`) | Only the generator-5 terrain fingerprint failed, as intended: its placeholder was then replaced by the generated values. Every other test passed |
| Affected existing classes: `WorldGeneratorFingerprintTest`, `save.UrbanMetaSectionTest`, `UrbanDevelopmentSelectorTest`, `WorldSeedDeterminismTest`, `qa.RuntimeBoundsTest`, `simulation.UrbanSurfaceSpawnGateTest`, `simulation.EventRollLadderTest`, `GameLoopIntegrationTest` (`build/m03-focused-2.log`, `-3.log`) | Only the new plan-fingerprint placeholder failed, then was recorded; `WorldGeneratorFingerprintTest` 8 of 8 afterwards. The event-ladder pins for legacy worlds were unchanged |
| Mutation checks, each reverted afterwards: (1) an asymmetric east avenue half in `StreetNetwork.bandPart`; (2) a courtyard passage shifted one cell into its parcel; (3) a hidden call counter that perturbs `UrbanHash` (`build/m03-mutation-{seam,overlap,order}.log`) | Each was caught: (1) 3 tests, including the generated-block seam test; (2) 2 tests; (3) 3 tests, including the chunk-order test |
| Native `urban_streets`: `VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_SCENE=urban_streets VEYLON_SHOT=4,8 VEYLON_CAPTURE_TAG=m03_streets .\gradlew.bat run -PveylonDataDir=build/qa/m03-streets` | The four origin regions printed `overlaps=0 parcelRules=0 routeRules=0 seams=0/4 ok=true` (residential, commercial, residential with a park, residential). Spawn `81,96` on `COURTYARD_PATH`. `m03_streets_8s.png`: a corner sidewalk looking north up a local street, basalt roadway, stone-brick sidewalks, crosswalk stripes, dirt lots inside stone outlines, park trees, the avenue at the end |
| Native `urban_intersection` (same seed, `VEYLON_SHOT=5`, `build/qa/m03-intersection`) | `m03_intersection_5s.png`: the four-way avenue crossing where four regions meet at the origin, with dashed centre lines, crosswalks on every approach, a boulevard median on one segment, a park, lots and stair-core pads |
| Native `urban_seam_walk` (same seed, `VEYLON_SHOT=3,9,14,25`, `build/qa/m03-seamwalk`) | `[urban-qa] seam-walk frames=2631 endX=40.02 z=60.50 chunkCrossings=5 regionCrossed=true blockedFrames=0 enteredUnloaded=false feetOffset=0.000..0.000 loadedChunks=274 pass=true`. It is real player movement through `Game.applyMovementCommand` in a native window, driven by script and not by a keyboard. The seam surfaces were `ROADWAY|ROADWAY`; `m03_seamwalk_9s.png` shows the crossing of the boulevard avenue on the seam |
| Native isolated urban smoke: `VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_SMOKE=10 VEYLON_SHOT=4 VEYLON_CAPTURE_TAG=m03_urban_smoke .\gradlew.bat run -PveylonDataDir=build/qa/m03-smoke` | Exit 0: save and load true, `creatures=0 npcs=0`, 0 GL and 0 KHR errors, average 141.9 fps (p95 7.02 ms, max 115.35 ms on the first frame) at 1280 × 720 with VSync on, runtime within every hard limit including `urbanPlans=16`, `fortressPlan={available=false,generator=urban}`. Nothing was written outside `build/qa/m03-smoke`; the checkout has no `saves/` |
| `.\gradlew.bat build --console=plain` before the review (`build/m03-build-1.log`) | BUILD SUCCESSFUL in 6 min 41 s. 154 test classes, 1,593 tests, 0 failures, 1 skipped (the symlink case that needs a privilege on this host); javadoc and doclint included |
| Read-only review workflow: 3 reviewers (planner correctness and determinism; integration and compatibility; test adequacy), each finding adversarially verified, 12 agents | The planner reviewer found nothing. Two findings were confirmed. (1) Low: a meteor shower restored from an older development save still dropped shards in an urban world; the continuation is now gated and `UrbanSurfaceSpawnGateTest.aMeteorShowerRestoredIntoACityDropsNoShard` pins it. (2) Low: this evidence section was missing when the reviewers read the records. Six test-adequacy findings were refuted as defects, but three were applied as hardening: negative diagnostics tests on repainted copies of a production plan (a missing door, a cut-off courtyard, a gap, and the 16-message cap with true totals); a real evict-and-rebuild in the identity test; every plan query checked not to generate a chunk |
| Focused run after the review fixes (`build/m03-focused-4.log`) | BUILD SUCCESSFUL; `world.urban.*`, `UrbanStreetNetworkGenerationTest`, `UrbanSurfaceSpawnGateTest` and `EventRollLadderTest` all passed (13 tests in `CityPlannerTest`) |
| **Final** `.\gradlew.bat build --console=plain` on the finished tree (`build/m03-build-final.log`) | BUILD SUCCESSFUL in 6 min 43 s. 154 test classes, **1,595 tests, 0 failures, 1 skipped** (the symlink case); javadoc and doclint included |
| `graphify update . --no-cluster`, run once after the code was final | Graph rebuilt: 6,753 nodes, 25,327 edges (`build/m03-graphify.log`) |

Not run: `performanceTest` (03 adds a bounded per-chunk planning cost, not a per-frame cost, and this host is not the calibrated reference) and `releaseArtifacts`. No older binary was executed: the rejection of a generator-5 save by the package-02 build and by 0.8.1 is established by reading their version checks. No manual keyboard play was done.

### Milestone 04 (2026-10-08)

Host and JDK as for 02 and 03: Windows 11 Pro, NVIDIA GeForce RTX 3060 Ti, Temurin `25.0.4.1+1` from the scratchpad. Commands ran from the repository root through Git Bash. Logs are in `build/m04-*.log` (ignored by git, never cleaned); native captures are under `build/qa/m04-*/screenshots/`.

| Check | Result |
| --- | --- |
| Read-only survey workflow before design (7 readers: rendering, physics, navigation, interaction, persistence, items and audio, QA) | 5 of 7 returned; the navigation and physics readers stopped on the account session limit, and those areas (`Pathfinder`, `Entity`, `VoxelPhysics`, `Raycaster`, `Steering`) were read directly in the main session instead |
| Focused existing suites after the enum, material, audio and palette changes: `SerializedEnumOrderTest`, `item.*`, `gfx.*`, `AudioCharacterizationTest`, `BodyFireSoundsTest`, `CreativeBuildingTest`, `CreativePaletteWorldTest`, `ai.PathfinderTest`, `SurvivalCreativeParityTest`, `OrchestratorSizeTest` (`build/m04-focused-1.log`) | BUILD SUCCESSFUL. Appended enums pinned, palette now 21, existing sounds unchanged, line budgets held |
| New suites: `UrbanFixtureGameplayTest`, `ai.UrbanResidentFixtureTest`, `save.FixtureStatesSectionTest`, `world.FixturesTest`, `gfx.UrbanMaterialsTest`, with `qa.RuntimeBoundsTest` and `GameLoopIntegrationTest` (`build/m04-focused-2.log`) | 59 tests, 1 failure. In `anOpenWindowLetsThePlayerClimbOverTheSillIntoTheRoom` the player had climbed through, but was still dropping off the sill (y 41.13) when the walk reached its target column. The test now lets the body land before checking the floor; it passed in `build/m04-focused-3.log` (17 of 17) |
| `UrbanFixtureQaSceneTest` (`build/m04-focused-4.log`) | The headless day timeline opened, shattered and closed fixtures through the production commands, and its walk reported `waypoints=5/5 ... onRoof=true pass=true`. One assertion was wrong: it expected no recorded edits, but the two production pane breaks are recorded. It now asserts exactly those two |
| Native `urban_fixtures`, first run: `VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_SCENE=urban_fixtures VEYLON_SHOT=3,6,9,12,15,18,23,27,33 VEYLON_CAPTURE_TAG=m04_fixtures ./gradlew.bat run -PveylonDataDir=build/qa/m04-fixtures` | `[materials] 76 materials, 104 of 160 texture layers`. `fixtures-walk frames=573 waypoints=5/5 blockedFrames=20 onRoof=true pass=true`; 0 GL errors. The captures showed the facade, the open and shattered windows, both sides of the doors and the stairs. The cracked-concrete variant and the stair edge strips read too heavy and were toned down before the rerun |
| Native `urban_fixtures_night` (`VEYLON_SHOT=3,6,9,12,14`, `build/qa/m04-night`) | 23:00 with lanterns lit. `m04_night_3s.png`: the facade reads at night, with warm light behind the window panes. `m04_night_6s.png`: the closed panes from inside, prompt `[F] Open window (crouch: latch it from this side)`. `m04_night_9s.png`: the stair edges and the interior door, lit by the lantern. `m04_night_12s.png`: the closed metal door from inside, prompt `[F] Open door (crouch: bar it from this side)`. Lantern light also shows through the brick, the legacy unoccluded block light (B-20, D-19). 0 GL errors |
| Native `urban_fixtures`, rerun with the toned textures and shots during the walk (`VEYLON_SHOT=3,6,9,12,15,18,20,21,22,33`, `build/qa/m04-fixtures-2`) | `fixtures-walk frames=554 waypoints=5/5 blockedFrames=20 end=95.38,45.00,108.77 onRoof=true pass=true`; 0 GL errors. `m04_fixtures_3s.png`: the facade with its closed metal door and four-pane window. `m04_fixtures_6s.png`: the door leaf open on its jamb, a casement open and a shattered pane. `m04_fixtures_9s.png`: the door from inside with its prompt. `m04_fixtures_12s.png`: the windows from inside, with the open sash against the jamb and shards in the broken frame. `m04_fixtures_15s.png` and `_18s.png`: the stairs from their foot and from the roof. `m04_fixtures_20s.png`: the walk, through the door and in the west room at the stair foot. The walk is real player movement through `Game.applyMovementCommand`, driven by script, not by a keyboard |
| `.\gradlew.bat build --console=plain` before the review (`build/m04-build-1.log`) | BUILD SUCCESSFUL in 6 min 47 s. 160 test classes, 1,646 tests, 0 failures, 1 skipped (the symlink case that needs a privilege on this host); javadoc and doclint included |
| Native isolated urban smoke: `VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_SMOKE=10 VEYLON_SHOT=4 VEYLON_CAPTURE_TAG=m04_urban_smoke ./gradlew.bat run -PveylonDataDir=build/qa/m04-smoke` (`build/m04-native-smoke.log`) | Exit 0: isolated save and load true; `assets={materials=76 ... itemIcons=126/126}`; 0 GL and 0 KHR errors; runtime within every hard limit, the new `fixtureStates=32768 residentDoors=256` caps listed; average 141.9 fps (p95 7.00 ms, max 121.01 ms on the first frame while the spawn area generates) at 1280 × 720 with VSync on; `performanceGate=true`. Nothing was written outside `build/qa/m04-smoke` |

### Milestone 04 completion (2026-10-10)

These are fresh checks from the resumed working tree on `923345f`, including the late container-transfer guard and the final door-placement bounds correction. The earlier 2026-10-08 results above remain historical. Windows PowerShell used the existing Temurin `25.0.4.1+1` at `build/tools/jdk25/jdk-25.0.4.1+1` through `JAVA_HOME`; the restricted process could not read its security configuration, so Gradle checks used approved execution with JDK/cache access. All test and native save/settings paths remained under `build/`.

| Check | Result |
| --- | --- |
| `.\gradlew.bat test --tests 'com.veylon.UrbanFixtureGameplayTest' --tests 'com.veylon.UrbanFixtureQaSceneTest' --tests 'com.veylon.ai.UrbanResidentFixtureTest' --tests 'com.veylon.world.FixturesTest' --tests 'com.veylon.gfx.UrbanMaterialsTest' --tests 'com.veylon.save.*' --tests 'com.veylon.qa.SerializedEnumOrderTest' --tests 'com.veylon.qa.RuntimeBoundsTest' --tests 'com.veylon.GameLoopIntegrationTest' --tests 'com.veylon.world.WorldGeneratorFingerprintTest' --console=plain` (`build/m04-resume-focused.log`) | BUILD SUCCESSFUL in 1 min 20 s. This verified the recovered checkpoint before the final placement correction. |
| `.\gradlew.bat test --tests 'com.veylon.UrbanFixtureGameplayTest' --tests 'com.veylon.save.FixtureStatesSectionTest' --console=plain` (`build/m04-resume-final-focused.log`) | BUILD SUCCESSFUL in 4 s after the correction. Includes ceiling/unloaded placement conservation, locked/forced door round-trip, failed entry, full-inventory salvage/spill refusals and destroyed open storage. |
| `.\gradlew.bat build --console=plain` (`build/m04-resume-build.log`) | BUILD SUCCESSFUL in 6 min 40 s. XML report aggregation: 160 classes, 1,650 tests, 0 failures, 0 errors, 1 skipped (`AppPathsTest` symbolic-link creation requires a host privilege). Javadoc/doclint included. Covers the full always set and all required 04 regression suites. |
| Day native: `VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_SCENE=urban_fixtures VEYLON_SHOT=3,6,9,12,15,18,20,21,22,33 VEYLON_CAPTURE_TAG=m04_resume_day .\gradlew.bat run -PveylonDataDir=build/qa/m04-resume-day --console=plain` (`build/m04-resume-native-day.log`) | Exit 0, BUILD SUCCESSFUL. `[materials] 76 materials, 104 of 160 texture layers`; 10 captures, 1280 × 720. `fixtures-walk frames=555 waypoints=5/5 blockedFrames=19 end=95.38,45.00,108.77 onRoof=true pass=true`. 0 GL, 0 KHR errors, 0 native audio errors. Capture synchronization produced KHR performance messages, not errors. |
| Night native: same generator/seed, `VEYLON_SCENE=urban_fixtures_night VEYLON_SHOT=3,6,9,12 VEYLON_CAPTURE_TAG=m04_resume_night .\gradlew.bat run -PveylonDataDir=build/qa/m04-resume-night --console=plain` (`build/m04-resume-native-night.log`) | Exit 0, BUILD SUCCESSFUL. 4 captures, 1280 × 720, 23:00. 0 GL, 0 KHR errors, 0 native audio errors. |
| Visual inspection through `view_image` | Inspected day captures 3/6/9/12/15/18 s in `build/qa/m04-resume-day/screenshots/`: front door closed/open outside and closed inside with the F hint, windows closed outside and open/shattered from both sides, stairs from foot and roof. Inspected all four night captures in `build/qa/m04-resume-night/screenshots/`: readable facade, window frame and latch hint, stair edges, inside door and bar hint. The known unoccluded lantern light remains; no new visual mismatch was found. The native walk was scripted through production movement, not manual exploration or listening. |
| `graphify update . --no-cluster` (`build/m04-resume-graph.log`) | Exit 0. Code graph updated to 7,054 nodes and 26,691 edges; no LLM/document/image extraction or clustering. Parser warnings for Groovy `build.gradle` and symbol-free `settings.gradle`; Java extraction completed. |
| PowerShell relative Markdown path/anchor check over the seven changed documentation files | 38 local links/anchors resolved, including the completion entry. |
| `git diff --cached --stat`, staged diff review and `git diff --cached --check` | Reviewed the explicit 102-path catch-up scope for milestones 01–04. No unrelated work or generated build/QA/graph output was staged; whitespace checks passed. |

The completion handoff groups the coupled, accumulated 01–04 code/tests/docs in a local commit under the owner's revised policy. Existing generator fingerprints and historical save fixtures are preserved; older binaries were not executed. Generated buildings, lock-plan defaults and room planning remain packages 05 and 06, and atmospheric/light polish remains deferred as already recorded.

### Local history reconstruction (2026-10-10)

The owner's subsequent request supersedes the combined-commit arrangement above.
This task reorganizes the completed 01-04 implementation; it adds no gameplay
and does not begin 05. The unpushed original `e452dc1` is retained at
`backup/urban-m01-m04-before-split`. `main` and the local `origin/main` reference
remain at `923345f`; development continues on `feature/urban-survival`. The scoped
commit ledger is in [PROGRESS.md](PROGRESS.md#local-commit-history-reconstruction-2026-10-10).

Fresh checks below ran on reconstructed intermediate snapshots using Windows
PowerShell, the existing Temurin 25.0.4.1+1 JDK and the Gradle wrapper. JDK/cache
access required approved execution; test data remained below `build/`. Logs are
retained under ignored `build/history-split/`; each corresponding feature/test
commit records the exact test command in its `Validation:` body. Earlier native
and build results above remain evidence from their original sessions.

```powershell
$env:JAVA_HOME = (Resolve-Path build/tools/jdk25/jdk-25.0.4.1+1).Path
```

| Reconstructed snapshot / focused selection | Fresh result | Log under `build/history-split/` |
| --- | --- | --- |
| QA isolation: `AppPathsTest`, `SaveSlotIsolationTest` | Passed, 8 s | `isolation-test.log` |
| Before generator changes: generator fingerprints, cave identities, migration, runtime bounds | Passed, 8 s with unchanged production generator code | `legacy-pins-test.log` |
| Generator-4 foundation: `com.veylon.save.*`, fingerprints, cave identities, runtime bounds and game-loop integration | Passed, 1 min 12 s | `foundation-existing-test.log` |
| Foundation ids/cache/revisions, dispatch, selector, metadata, seeded replay and reset; Javadoc/doclint | Passed, 16 s | `foundation-regressions.log` |
| Generator-5 city plans: foundation suites, all five terrain pins and city-plan pins; Javadoc/doclint | Passed, 15 s | `city-existing-test.log` |
| City planner/salts, generated street seams and order, urban spawn gates, legacy event ladder, runtime bounds | Passed, 7 s | `city-regressions.log` |
| Street QA: `.\gradlew.bat classes compileTestJava javadoc --console=plain` | Passed, 6 s on the milestone-03-only harness | `city-qa-compile.log` |
| Urban materials/state: serialized enums, Creative palette, unchanged generator fingerprints; Javadoc/doclint | Passed, 10 s | `material-existing-test.log` |
| `FixturesTest`, `UrbanMaterialsTest` | Passed, 1 s | `material-regressions.log` |
| Player/resident commands: fixture/material tests, runtime bounds, `com.veylon.ai.*`, game-loop integration; Javadoc/doclint | Passed, 39 s | `fixture-existing-test.log` |
| Fixture gameplay, resident traversal, `FixtureStatesSectionTest`, runtime bounds and game-loop integration | Passed, 9 s after including the shared showcase dependency in the same commit | `fixture-regressions.log` |
| `UrbanFixtureQaSceneTest`; Javadoc/doclint | Passed, 8 s; production interaction/break commands and the roof walk exercised headlessly | `fixture-qa-test.log` |
| Foundation smoke-guard placement: generator fingerprints and corrupt-save resilience; Javadoc/doclint | Passed, 27 s after moving the shared urban smoke guards into the foundation commit | `foundation-smoke-guards.log` |
| Final portable build: `.\gradlew.bat build --console=plain` | BUILD SUCCESSFUL in 6 min 49 s; fresh XML aggregation: 160 classes, 1,650 tests, 0 failures, 0 errors, 1 skipped Windows symlink-privilege case in `AppPathsTest`. Javadoc/doclint executed successfully | `final-build.log` |
| `graphify update . --no-cluster` | Completed once: 7,054 nodes, 26,691 edges; no clustering or document/image extraction. Existing Groovy parsing limitations remain; Gradle is authoritative | `graphify-update.log` |
| `git diff --exit-code e452dc1 -- src build.gradle gradle.properties settings.gradle gradlew gradlew.bat gradle` | Exit 0, no implementation differences | Git comparison |
| `build/history-split/audit-history.ps1` | The 15 commits preceding this final documentation commit passed subject, `Validation:`, per-commit whitespace and trailer checks. Original 102-path scope preserved; `main` and local `origin/main` at `923345f` | Git audit |
| `build/history-split/check-docs.ps1 -IncludeGeneral`, `git diff --check`, `git diff --cached --check` | 40 local links/anchors resolved across all seven documentation files; working and staged whitespace checks passed | Documentation/Git checks |

Preparatory checks caught and resolved a misplaced Gradle `--tests` option and
the regression arena's dependency on the shared `FixtureShowcase` builder. The
builder now lands with those tests, before native QA registration. These
corrections changed grouping only; final production and test sources remain
identical to the original combined commit.

No new native session/capture, manual keyboard or listening pass, older binary,
wall-clock performance benchmark, release package, merge, push or tag was run.

### Milestone 05 (2026-10-10)

Fresh evidence from `feature/urban-survival`, baseline `bdecf6f`, initially clean
index/tree. The Git ledger and final 04 handoff establish committed prerequisites
01–04. No other live agent or index lock was present; no catch-up commit was
duplicated. The installed Temurin `25.0.4.1+1` at
`build/tools/jdk25/jdk-25.0.4.1+1` supplied JDK 25 through `JAVA_HOME`.
The sandbox initially denied reading `conf/security/java.security`; approved
execution with JDK/Gradle-cache access ran the checks below. Saves/settings stayed
under the existing isolated `build/` test-data seam.

| Check | Fresh result / evidence |
| --- | --- |
| `.\gradlew.bat test --tests 'com.veylon.world.urban.*' --tests 'com.veylon.entity.BuildingPlanTraversalTest' --tests 'com.veylon.entity.PlayerMovementSystemTest' --tests 'com.veylon.ai.PathfinderTest' --tests 'com.veylon.ai.UrbanResidentFixtureTest' --tests 'com.veylon.save.*' --tests 'com.veylon.world.WorldGeneratorFingerprintTest' --tests 'com.veylon.world.UrbanGeneratorDispatchTest' --tests 'com.veylon.UrbanDevelopmentSelectorTest' --tests 'com.veylon.WorldSeedDeterminismTest' --tests 'com.veylon.simulation.UrbanSurfaceSpawnGateTest' --tests 'com.veylon.qa.RuntimeBoundsTest' --tests 'com.veylon.qa.SerializedEnumOrderTest' --tests 'com.veylon.GameLoopIntegrationTest' --console=plain` | BUILD SUCCESSFUL in 1 min 25 s (`build/m05-final-focused.log`). All save suites, legacy pins, production movement, revision/reset, spawn and bound checks passed. Precedes the final room-floor/headroom safeguard. |
| `.\gradlew.bat test --tests 'com.veylon.world.urban.BuildingPlannerTest' --tests 'com.veylon.world.urban.AccessibilityValidatorTest' --tests 'com.veylon.entity.BuildingPlanTraversalTest' --console=plain` | BUILD SUCCESSFUL in 9 s after the safeguard and bounded stair-diagnostic priority correction (`build/m05-final-clearance.log`). Includes all matrix, fingerprint, invalid-fixture, fallback and actual stair-physics checks. |
| Nominal template matrix (`BuildingPlannerTest`) | 4 sizes (12 x 16, 16 x 20, 20 x 24, 12 x 12) x 4 frontages x 4 variants x 2 families x 6 seeds = 768 valid proposals, each reproduced identically. Apartment storeys span 2–5, optional B1; shops use two with the ordinary one-storey choice also covered by actual parcels. All twelve room functions occur. Counts stay within explicit cell/edge bounds. |
| Actual city plan matrix (`BuildingPlannerTest`) | `building-city-matrix plans=4112 repaired=0`, six frozen design seeds x all eight frozen design regions, including negative and far coordinates. Production validator checks every parcel, external route reservation and neighbouring footprint. No unreachable target, invalid flight, missing floor, overlap or overflow accepted. Final ordinary candidates require no repair. |
| Intentionally invalid plans (`AccessibilityValidatorTest`) | Reject solid walls merely declared as doors; all entries locked with reservations behind the lock; low doors and windows crossing slab bands; low stair departure ceiling, missing tread and narrow flight; blocked furniture/quest targets; an attainable three-block drop without an exit; missing room footing/headroom; missing floor metadata, vertical overflow, invalid core, unsupported template and oversized metadata. Message cap and pre-voxel bound rejection are asserted. A malformed core exhausts exactly three candidates without returning a shell. |
| Production movement (`BuildingPlanTraversalTest`) | Headless proposal staging in loaded worlds, all four frontages x Survival/Creative. `PlayerMovementSystem` walks every B1-to-roof flight up/down without jumping or flight; `Pathfinder` accepts each flight. This is not native generated/furnished exploration. |
| Determinism, persistence and runtime (`BuildingPlannerTest`, `UrbanMetaSectionTest`, `RuntimeBoundsTest`, legacy suites) | Common reservation ids/positions survive variants; plans match reversed/shuffled chunk/region access and cache eviction. Queries do not create chunks, block edits, fixture states or contents. The 256-building cache fills and remains bounded. Production save/load selects template 1 from generator 6 before bootstrap; absent meta retains it, mismatched layout rejects without touching the live world, new/load caches reset, and generator 5 still has no buildings/plans. Existing 1–5 fingerprints and historical save fixtures are retained. Gen-6 stamped streets match the existing gen-5 pins; six new plan pins cover both families at three seeds. |
| `java -cp build/classes/java/main com.veylon.world.urban.BuildingPlanDiagnostics` | Exit 0 after `.\gradlew.bat classes --console=plain`; output `build/m05-plan-examples.txt`. Pure ASCII floors, functions, ids/fingerprints and counters reproduced in [BUILDING_PLANS.md](BUILDING_PLANS.md). Invalid departure ceiling: one stair violation; deterministic alternate succeeds at attempt 2. No World, save, GL or OpenAL resource is created by this command. |
| `.\gradlew.bat build --console=plain` | BUILD SUCCESSFUL in 6 min 47 s (`build/m05-build.log`). Fresh XML aggregation: 163 classes, 1,672 tests, zero failures, zero errors, one skipped Windows symlink-privilege case in `AppPathsTest`. Javadoc/doclint executed successfully. |
| `graphify update . --no-cluster` | Exit 0 once: 7,224 nodes, 27,334 edges (`build/m05-graph.log`). Local AST extraction, no clustering, semantic agents or document/image extraction. Existing Groovy `build.gradle` parser warning and symbol-free `settings.gradle`; Gradle remains authoritative. |
| Existing read-only `build/history-split/check-docs.ps1 -IncludeGeneral` (invoked inline under the local script policy); `git diff --check`, `git diff --cached --stat`, staged diff review, `git diff --cached --check` | 47 local links/anchors resolved. Reviewed all 34 explicitly staged milestone paths; working/staged whitespace checks passed. No unrelated changes or generated build/QA/graph output is included. The local commit uses the configured Git identity and has no co-author trailer or agent signature. |

The broad matrix initially found window interaction targets on occupied tables;
targets now stand one cell inward, within F reach, leaving furniture clearances
intact. Final review added missing-room-floor/headroom rejection. Its first check
exposed stair diagnostics being displaced by the 32-message cap on a deliberately
impossible core; prioritizing flight diagnostics fixed the evidence test, with
the final focused run above passing. No accepted template voxel or fingerprint
changed in that diagnostic correction.

No native run/capture, manual exploration/listening, older executable, wall-clock
performance benchmark or release package was run. Native plan debug is optional
for 05. Generated/furnished exploration, lock-default application to stamped
fixtures and native day/night street-to-basement/roof walks belong to 06, with a
new generator. Contents/depletion are 07. Saved generator 6 pins template 1 and
uses unchanged `urban.meta` v1; builds supporting at most generator 5 reject that
save, established from dispatch code, not older-binary execution. Ordinary new
worlds remain generator 3. No merge, push, tag or publication is authorized here.

### Milestone 06 (2026-10-10)

Fresh work began at clean `8ef0cba` on `feature/urban-survival`, with 04–05
committed. Read the complete external base at
`../VEYLON-Urban-Survival-Roadmap/00_BASE_KNOWLEDGE.md` before the current project
instructions and relevant records; the requested repository-root copy is absent.
The installed Temurin 25.0.4.1+1 supplied JDK 25. An initial command without
`JAVA_HOME` and a sandboxed JDK security-file read failed before validation;
approved execution supplied the existing JDK/cache access. All data and artifacts
below remain isolated under ignored `build/`.

After the owner clarified the pack location, read
`build/urban-survival-roadmap/00_BASE_KNOWLEDGE.md` in full and checked its README.
The build copy replaces the external copy's older section-6 commit wording with
the shared `docs/COMMIT_WORKFLOW.md`; the independent commit order and validation
used here follow that current policy. The owner explicitly chose to keep the
human-walk gate pending.

```powershell
$env:JAVA_HOME = (Resolve-Path build/tools/jdk25/jdk-25.0.4.1+1).Path
```

| Check | Fresh result / evidence |
| --- | --- |
| Isolated edit-index boundary: `.\gradlew.bat -p build/m06-index-check/tree test --tests 'com.veylon.world.ChunkBlockEditsTest' --console=plain` | Passed in 11 s, two tests. Snapshot contained baseline `8ef0cba` plus only the reusable index and its tests. Local commit `88ef0b8`. XML: `build/m06-index-check/tree/build/test-results/test/TEST-com.veylon.world.ChunkBlockEditsTest.xml`. |
| Isolated generation/environment boundary: `.\gradlew.bat -p build/m06-behavior-check/tree test --tests 'com.veylon.world.urban.BuildingFurnishingTest' --tests 'com.veylon.UrbanIndoorEnvironmentTest' --tests 'com.veylon.world.WorldGeneratorFingerprintTest' --tests 'com.veylon.world.urban.AccessibilityValidatorTest' --tests 'com.veylon.world.urban.BuildingPlannerTest' --tests 'com.veylon.UrbanDevelopmentSelectorTest' --tests 'com.veylon.world.UrbanGeneratorDispatchTest' --tests 'com.veylon.world.urban.UrbanRevisionTest' --tests 'com.veylon.WorldSeedDeterminismTest' --tests 'com.veylon.save.FixtureStatesSectionTest' --console=plain` | Passed in 42 s: 75 tests, zero failures/errors/skips across ten suites. Snapshot contained `88ef0b8` plus exactly the 24 generation/environment paths, checked by file hashes before staging; no generated QA or later regression suites. Local commit `7cad9ea`. Log: `build/m06-behavior-check/test.log`. |
| Isolated additional regressions: `.\gradlew.bat -p build/m06-behavior-check/tree test --tests 'com.veylon.world.UrbanApartmentGenerationTest' --tests 'com.veylon.save.GeneratedApartmentStateTest' --tests 'com.veylon.entity.GeneratedApartmentTraversalTest' --console=plain` | Passed in 5 s, seven tests. Added only these three suites to the validated generation tree; hashes matched before staging. Local commit `2a53f80`. Log: `build/m06-regressions-check.log`. |
| Integrated focused selection: `.\gradlew.bat test --tests 'com.veylon.world.ChunkBlockEditsTest' --tests 'com.veylon.world.UrbanApartmentGenerationTest' --tests 'com.veylon.world.urban.BuildingFurnishingTest' --tests 'com.veylon.world.urban.BuildingPlannerTest' --tests 'com.veylon.world.urban.AccessibilityValidatorTest' --tests 'com.veylon.world.WorldGeneratorFingerprintTest' --tests 'com.veylon.world.UrbanGeneratorDispatchTest' --tests 'com.veylon.world.urban.UrbanRevisionTest' --tests 'com.veylon.UrbanDevelopmentSelectorTest' --tests 'com.veylon.UrbanApartmentQaSceneTest' --tests 'com.veylon.entity.GeneratedApartmentTraversalTest' --tests 'com.veylon.UrbanIndoorEnvironmentTest' --tests 'com.veylon.EnvironmentalSimulationTest' --tests 'com.veylon.AudioSceneStateTest' --tests 'com.veylon.CreativeBodyTest' --tests 'com.veylon.CreativeHazardsTest' --tests 'com.veylon.AmbienceRainTest' --tests 'com.veylon.save.GeneratedApartmentStateTest' --tests 'com.veylon.save.FixtureStatesSectionTest' --console=plain` | Passed in 52 s (`build/m06-focused-2.log`). Precedes the final replacement-lock assertion, new fingerprint pins and supplemental QA. Those are covered by the later isolated checks and final build. |
| Initial portable build: `.\gradlew.bat build --console=plain` | Passed in 7 min 7 s: 1,703 tests, zero failures/errors, one skipped Windows symlink-privilege case (`AppPathsTest`), 170 suites; Javadoc/doclint included. Log: `build/m06-build.log`; XML archived in `build/m06-initial-build-results/`. Compiled before the final entrance-camera adjustment and supplemental night scene. |
| Final generated-QA selection: `.\gradlew.bat test --tests 'com.veylon.UrbanApartmentQaSceneTest' --tests 'com.veylon.entity.GeneratedApartmentTraversalTest' --console=plain` | Passed in 5 s: five QA tests plus the frontage/mode traversal test, zero failures/errors. Includes inherited city-plan diagnostic dispatch and explicit portable-light instrumentation at both capture seeds. Log: `build/qa/m06-qa-final-tests.log`. |
| Final portable build: `.\gradlew.bat build --console=plain` | Passed in 7 min 6 s after all code and final QA sources were frozen: 1,704 tests across 170 suites, zero failures/errors, one Windows symlink-privilege skip in `AppPathsTest`. Javadoc/doclint passed. Log: `build/m06-final-build.log`. |

`BuildingFurnishingTest` covers 320 nominal combinations, all room functions and
4,112 actual parcels across six seeds and eight regions, with zero repairs.
`UrbanApartmentGenerationTest` generates each complete origin region at six
seeds and validates every parcel's rooms and interaction targets against live
blocks through the production entry/return proof. Its 16 x 16 chunk window is an
explicit exception to the usual 15 x 15 bound so boundary parcels are included.
Seven loading orders cover row, reverse, column, three shuffles and spiral, with
additional negative/seam/far samples. Blocks, heightmaps, light lists, moisture
and plan/reservation fingerprints match; no pending neighbour edits or initial
inventories are created. Repeated stamping is identical and generation finalises
height/light once.

Production save/load over an existing Creative world preserves the saved
Survival mode, template-2 identity, explicit unlock, open doors, shattered glass
and removed storage. A fresh world with restored deltas recreates the parcel in
reverse chunk order without resurrection or refill. Saved generator versions
1–6 and template 1 retain their existing fingerprints; new generator-7 terrain,
complete refuge/shop geometry and six template-2 plan fingerprints are pinned.
No codec or persisted enum ordinal changed; an older executable was not run.

`GeneratedApartmentTraversalTest` walks every stair flight in both directions,
four frontages and Survival/Creative using production physics without jump or
flight. `UrbanApartmentQaSceneTest` walks one continuous Survival route from
street to kitchen, upper room, B1 and outside, opening actual doors through the
production interaction command. These are headless automated physical checks.
The legacy NPC pathfinder initially selected an unswept stair-side drop; the
test now routes with production swept-clearance transitions. Multi-floor AI
routing remains package 10 and is not proved here.

The first focused run failed four checks: the now-supported template revision
was still expected to throw, the weather assertion omitted CLEAR's +1 offset,
proposal halo ownership was incorrectly applied to neighbouring live blocks,
and the old pathfinder chose the invalid drop described above. The corrected
52 s run passed. The first native launch stopped before any capture because its
diagnostic used layout 4 with the frozen layout-2 city planner; the dispatcher
now reports the inherited plan revision, with a regression test. Direct execution
of the local matrix `.ps1` was denied by PowerShell policy; identical inline
commands ran without changing that policy. These failures are not passing
evidence.

**Native appearance and remaining gate.** The six holds are street, entrance,
kitchen, upper floor, basement and exit at `3,7,11,15,19,23` seconds. Logs and
filenames explicitly identify scripted camera evidence. Raw day/night scenes
use actual generator-7 geometry with no staged structure, lights or loot. The
separate `urban_apartments_lit_night` scene adds exactly four portable QA torches
through ordinary recorded edits in empty cells, preserving generated fixtures
and routes. It is supplemental light instrumentation, not generated utilities.
The raw night views remain part of the evidence: a held torch model does not
illuminate terrain, and unlit interior readability is poor.

Final native matrix used the following common environment and one `run` per row
below. Set `VEYLON_SEED`, `VEYLON_SCENE` and `VEYLON_CAPTURE_TAG` from the row,
then run `.\gradlew.bat run -PveylonDataDir=build/qa/<directory> --console=plain`.
Every row completed in 25 s with six 1280 x 720 captures, zero GL/KHR errors and
zero native audio errors. KHR pixel-transfer synchronization performance
messages came from screenshot capture; these runs are not frame benchmarks.
Logs are `build/qa/<directory>.log`; images are
`build/qa/<directory>/screenshots/<tag>_<seconds>s.png`.

```powershell
$env:VEYLON_GENERATOR = 'urban-dev'
$env:VEYLON_GAME_MODE = 'survival'
$env:VEYLON_SHOT = '3,7,11,15,19,23'
$env:VEYLON_RESOLUTION = '1280x720'
$env:VEYLON_UI_SCALE = '1'
$env:VEYLON_FULLSCREEN = '0'
$env:VEYLON_VSYNC = '0'
$env:VEYLON_GL_DIAGNOSTICS = '1'
Remove-Item Env:VEYLON_FRONTEND -ErrorAction SilentlyContinue
Remove-Item Env:VEYLON_SMOKE -ErrorAction SilentlyContinue
```

| Seed | Scene | Directory | Capture tag |
| --- | --- | --- | --- |
| `20260731` | `urban_apartments` | `m06_generated_day_final` | `m06_scripted_camera_day_final` |
| `20260731` | `urban_apartments_night` | `m06_generated_night_final` | `m06_scripted_camera_night_final` |
| `20260731` | `urban_apartments_lit_night` | `m06_generated_lit_night` | `m06_scripted_camera_portable_torches_night` |
| `-4242` | `urban_apartments` | `m06_seed_minus4242_day` | `m06_seed_minus4242_scripted_camera_day` |
| `-4242` | `urban_apartments_night` | `m06_seed_minus4242_night` | `m06_seed_minus4242_scripted_camera_night` |
| `-4242` | `urban_apartments_lit_night` | `m06_seed_minus4242_lit_night` | `m06_seed_minus4242_scripted_camera_portable_torches_night` |

The selected real apartments are `urban:building:0,0:0:0`, INTACT, three storeys
and B1, bounds `(6,6)..(32,17)` at seed `20260731`; and
`urban:building:0,0:2:0`, PICKED_CLEAN, five storeys and B1, bounds
`(6,43)..(33,60)` at seed `-4242`. Both cross chunk boundaries. Day and
portable-light views show readable cabinets, room floors, windows, upper rooms,
basement storage and exits. Visual inspection covered entrance, kitchen and
basement across both seeds, including the first seed's search-cabinet prompt.
The original first-seed day retry and raw night captures remain in
`build/qa/m06_generated_day/` and `build/qa/m06_generated_night/`; the final matrix
above supersedes their earlier entrance-camera pose, not their recorded outcome.

`graphify update . --no-cluster` completed once, exit 0, with 7,375 nodes and
28,105 edges (`build/m06-graph.log`). This was local code analysis only, without
clustering, semantic extraction agents or document/image extraction. Existing
Groovy `build.gradle` parsing and symbol-free `settings.gradle` warnings remain;
Gradle and source are authoritative. The read-only relative Markdown link/anchor
checker resolved 53 links. Working and staged whitespace checks passed; all six
documentation paths were explicitly staged and reviewed. The four local code
commits have validation bodies, coherent dependency-ordered scopes and no
co-author trailers. The documentation handoff follows separately; no task-owned
source edits remain outside those commits.

The mandatory human Survival walk remains **PENDING**. This session has no
desktop/manual-input capability, so neither camera holds nor automated physics
close that gate. Use the start-only scene and command in
[DEVELOPING.md](../DEVELOPING.md#urban-planning-qa), record the actual route,
building id, controls and clipping/stair findings, fix any defect and rerun the
affected checks. Milestone 06 remains **PARTIAL**; do not begin 07 yet.

No manual listening, wall-clock performance benchmark, native release package,
merge, push, tag or publication was performed. Existing block-light wall leakage
and CPU chunk retention remain the recorded package-22 and package-24 limits.

## 06 native-QA continuation — 2026-10-11

Started from clean `197f8da` on `feature/urban-survival`. Read the base in
`build/urban-survival-roadmap-revision/00_BASE_KNOWLEDGE.md`, repository rules,
relevant urban records and the save-compatibility skill. The owner instructed
**“Complete 06’s gate first”** before 07. Native feedback identified the street
entrance; the owner subsequently confirmed symmetric opening, supplied the cubic
closed-door screenshot, confirmed the thin-panel repair, and requested shaped
stairs. After the shaped-stair repair the owner explicitly confirmed **“Yes, the
full route passed”**, completing the normal-input Survival street/kitchen/upper/
B1/exit gate without clipping or getting stuck.

All commands used JDK 25 via
`$env:JAVA_HOME=(Resolve-Path 'build/tools/jdk25/jdk-25.0.4.1+1').Path`.
Portable commands were run with `--console=plain`; logs below are ignored local
artifacts, not committed fixtures. Git whitespace checks passed at each code
staging boundary.

### Door revisions

`9e5c332` was tested with:

```powershell
.\gradlew.bat test --tests 'com.veylon.GeneratedEntranceGeometryTest' --tests 'com.veylon.UrbanFixtureGameplayTest' --tests 'com.veylon.UrbanApartmentQaSceneTest' --console=plain
.\gradlew.bat build --console=plain
```

26 focused checks passed (`build/m06-entrance-focused-20261011.log`). Its portable
build passed in 7 min 20 s: 1,706 tests, zero failures/errors, one skip
(`build/m06-entrance-build-20261011.log`; XML totals preserved in
`build/m06-hinge-build-summary-20261011.json`). Both paired door heights/frontages
are exercised through production interactions and raycasts, including a removed
or broken partner. `graphify update . --no-cluster` then completed with 7,384
nodes / 28,165 edges (`build/m06-entrance-graph-20261011.log`). Later owner findings
superseded that source snapshot, requiring the final graph refresh below.

`f850626` was tested with:

```powershell
.\gradlew.bat test --tests 'com.veylon.GeneratedEntranceGeometryTest' --tests 'com.veylon.world.FixturesTest' --tests 'com.veylon.UrbanFixtureGameplayTest' --tests 'com.veylon.UrbanApartmentQaSceneTest' --tests 'com.veylon.world.RaycasterTest' --tests 'com.veylon.gfx.UrbanMaterialsTest' --tests 'com.veylon.qa.SerializedEnumOrderTest' --tests 'com.veylon.world.WorldGeneratorFingerprintTest' --tests 'com.veylon.save.GeneratedApartmentStateTest' --console=plain
.\gradlew.bat build --console=plain
```

73 focused checks passed (`build/m06-door-panels-focused-20261011.log`). Portable
build passed in 7 min 21 s: 1,707 tests, zero failures/errors, one skip
(`build/m06-door-panels-build-20261011.log`; totals preserved in
`build/m06-door-panels-build-summary-20261011.json`). This includes old generator
fingerprints and serialized ordinals, not rewritten pins. Generated/isolated
raycasts hit the panel while passing through its former perpendicular cube faces.

Native door runs used seed `20260731`, generator 7 and Survival. The three
scripted scenes used shots `3,7`, resolution `1280x720`, UI scale `1`, fullscreen
`0`, vsync `0`, GL diagnostics `1`, and no frontend/smoke setting:

| Scene | Data directory under `build/qa/` | Capture tag | Result |
| --- | --- | --- | --- |
| `urban_apartments` | `m06-door-panels-day-20261011` | `m06_closed_panels_scripted_day` | Clean exit, 14 s |
| `urban_apartments_night` | `m06-door-panels-raw-night-20261011` | `m06_closed_panels_scripted_raw_night` | Clean exit, 9 s |
| `urban_apartments_lit_night` | `m06-door-panels-lit-night-20261011` | `m06_closed_panels_scripted_portable_torches_night` | Clean exit, 9 s |

Each used `.\gradlew.bat run -PveylonDataDir=build/qa/<directory> --console=plain`.
Logs are `build/m06-door-panels-native-{day,raw-night,lit-night}-20261011.log`.
All reported zero GL/KHR errors and zero native audio errors. Inspection covered
day 3/7 s and portable-light night 7 s; scripted poses are appearance evidence.
The owner confirmed the thin-door appearance in the subsequent normal-input
`urban_apartments_manual` session, directory
`build/qa/m06-door-panels-manual-20261011`. That session closed normally after
10 min 31 s with zero GL/KHR/audio errors; it supplies no complete route report.

### Stair mechanics and generated integration

The reusable mechanics commit `1a58b53` was exported from its exact staged tree
using `git checkout-index --all --prefix=build/m06-stair-mechanics-check/`, before
including generator 8. This exported revision passed:

```powershell
.\build\m06-stair-mechanics-check\gradlew.bat -p build/m06-stair-mechanics-check test --tests 'com.veylon.world.StairGeometryTest' --tests 'com.veylon.StairPlacementSaveTest' --tests 'com.veylon.entity.ShapedStairBodyTest' --tests 'com.veylon.combat.ShapedStairProjectileTest' --tests 'com.veylon.UrbanFixtureGameplayTest' --tests 'com.veylon.entity.*Movement*' --tests 'com.veylon.entity.*Ragdoll*Test' --tests 'com.veylon.combat.ProjectileHitZoneTest' --tests 'com.veylon.qa.SerializedEnumOrderTest' --tests 'com.veylon.gfx.UrbanMaterialsTest' --console=plain
```

103 tests passed, zero failures/errors/skips, 30 s
(`build/m06-stair-mechanics-check-final.log` and
`build/m06-stair-mechanics-summary-20261011.json`). The first run failed because
the new projectile test asserted guaranteed arrow recovery, which is actually
probabilistic. The final assertion tests flight consumption on impact, preserving
the production recovery policy. Tread raycasts test actual hit heights/normals
and empty quarters in all orientations; fallen bodies settle on the low tread;
placement/break/replacement and production save/load preserve direction and one
shared item form. Existing cube-stair collision remains covered.

Generated integration passed:

```powershell
.\gradlew.bat test --tests 'com.veylon.world.StairGeometryTest' --tests 'com.veylon.StairPlacementSaveTest' --tests 'com.veylon.entity.ShapedStairBodyTest' --tests 'com.veylon.combat.ShapedStairProjectileTest' --tests 'com.veylon.world.urban.ShapedStairGenerationTest' --tests 'com.veylon.save.GeneratedShapedStairStateTest' --tests 'com.veylon.entity.GeneratedApartmentTraversalTest' --tests 'com.veylon.entity.BuildingPlanTraversalTest' --tests 'com.veylon.world.UrbanGeneratorDispatchTest' --tests 'com.veylon.world.urban.UrbanRevisionTest' --tests 'com.veylon.UrbanDevelopmentSelectorTest' --tests 'com.veylon.UrbanApartmentQaSceneTest' --tests 'com.veylon.world.WorldGeneratorFingerprintTest' --tests 'com.veylon.qa.SerializedEnumOrderTest' --tests 'com.veylon.gfx.UrbanMaterialsTest' --console=plain
.\gradlew.bat test --tests 'com.veylon.UrbanApartmentQaSceneTest' --console=plain
```

56 focused checks passed in 9 s (`build/m06-shaped-stairs-focused-final-20261011.log`
and its `-summary-` JSON); after appending the stair camera pose, all five QA
scene checks passed (`build/m06-shaped-stairs-qa-check-20261011.log`). The generated
tests preserve every non-stair cell, room/fixture identity and occupation choice
across three seeds and all origin-region parcels; compare both chunk load
orders; climb/descend every frontage in both modes through production movement
and pathfinding; and save/load a destroyed generated tread over a live old world
without regeneration. Generator 1–7 fingerprint constants were not changed.

The first integrated `.\gradlew.bat build --console=plain` failed one of 1,715
tests: `AccessibilityValidatorTest` still expected literal template 3 to be
unsupported. Production correctly supports that revision now. The rejection
case was changed to `BUILDING_TEMPLATE_SHAPED_STAIRS + 1`, then verified with:

```powershell
.\gradlew.bat test --tests 'com.veylon.world.urban.AccessibilityValidatorTest' --console=plain
.\gradlew.bat build --console=plain
graphify update . --no-cluster
```

The focused validator check passed (`build/m06-shaped-stairs-revision-check-20261011.log`).
Final portable build passed in 7 min 12 s: **1,715 tests, zero failures/errors,
one skip**, including Javadoc/doclint (`build/m06-shaped-stairs-build-final-20261011.log`
and `build/m06-shaped-stairs-build-final-summary-20261011.json`). The earlier
failed totals remain in `build/m06-shaped-stairs-build-initial-summary-20261011.json`.
The code graph update completed with 7,433 nodes / 28,400 edges
(`build/m06-shaped-stairs-graph-20261011.log`), before the final test-only literal
correction; no semantic extraction or clustering ran. Existing Gradle parser
limitations remain. Documentation resolved 54 relative links/anchors across
six changed records and passed whitespace checks. Code integration is `8388f93`.

### Native stair appearance and completed human route

Common settings: generator `urban-dev` (8), seed `20260731`, Survival,
`VEYLON_SHOT=27`, resolution `1280x720`, UI scale `1`, fullscreen `0`, vsync `0`,
GL diagnostics `1`, with frontend/smoke unset. Set scene/tag from each row and run
`.\gradlew.bat run -PveylonDataDir=build/qa/<directory> --console=plain`.

| Scene | Directory under `build/qa/` | Capture tag | Result |
| --- | --- | --- | --- |
| `urban_apartments` | `m06-shaped-stairs-day-20261011` | `m06_shaped_stairs_scripted_day` | Clean exit, 34 s |
| `urban_apartments_night` | `m06-shaped-stairs-raw-night-20261011` | `m06_shaped_stairs_scripted_raw_night` | Clean exit, 29 s |
| `urban_apartments_lit_night` | `m06-shaped-stairs-lit-night-20261011` | `m06_shaped_stairs_scripted_portable_torches_night` | Clean exit, 29 s |

All three captures were inspected. Logs
`build/m06-shaped-stairs-native-{day,raw-night,lit-night}-20261011.log` report zero
GL/KHR errors and zero native audio errors. Day and portable-light night show
the stepped tread/riser geometry. Raw night remains dimmer; the existing held
torch model does not light terrain. Supplemental night still adds exactly four
QA portable torches, without replacing a fixture or staging stairs. KHR shader
recompile and screenshot pixel-transfer performance messages are not errors or
frame benchmarks. Native boot uses the existing 104/160 texture layers and
126/126 item icons; four directional materials reuse existing tiles.

The new start-only manual session is
`build/qa/m06-shaped-stairs-manual-20261011`, log
`build/m06-shaped-stairs-native-manual-20261011.log`, with `VEYLON_SHOT` unset and
vsync `1`. The owner was asked to walk the entire route and report appearance,
clipping, stairs and exit. Building remains `urban:building:0,0:0:0`, INTACT,
three storeys plus B1, bounds `(6,6)..(32,17)`, now template 3. The owner confirmed
the stair appearance and explicitly reported **“Yes, the full route passed”**
using normal WASD/mouse/F controls. The session closed normally after 3 min 25 s,
with zero GL/KHR/native-audio errors. **Human route gate PASSED.** Day and night
scripted captures supply separate appearance evidence. No listening pass,
calibrated benchmark or older-binary
execution is claimed. Existing saves stay on their frozen generator; older
builds cannot read generator 8, and downgrading old-generator worlds edited with
new stair ids is unsupported. Save base/section layouts and content revision
are unchanged.

## Milestone 07 — Contextual loot and persistence (2026-10-11)

Prerequisite 06's human route is passed as recorded above. These are fresh 07
checks, not reused 06 counts. Windows PowerShell used Temurin `25.0.4.1+1` at
`build/tools/jdk25/jdk-25.0.4.1+1` through `JAVA_HOME`. Approved execution supplied
JDK/cache access; all native saves/settings/captures stayed under isolated
`build/qa/` directories. Source/codec policies are D-71–D-74.

### Production checks and revision boundaries

Each code revision was checked against its actual contents before its local
commit; independent generated regression coverage used an exported index tree.
Logs and JSON totals remain under ignored `build/`:

| Revision/check | Actual command | Result |
| --- | --- | --- |
| Foundation `5adfcbb` | `.\gradlew.bat test --tests 'com.veylon.save.UrbanContainersSectionTest' --tests 'com.veylon.save.GeneratedApartmentStateTest' --tests 'com.veylon.save.UrbanMetaSectionTest' --tests 'com.veylon.world.urban.UrbanRevisionTest' --tests 'com.veylon.world.urban.UrbanSaltsTest' --tests 'com.veylon.world.WorldGeneratorFingerprintTest' --console=plain` | 34 tests, zero failures/errors/skips, 10 s; `m07-state-revision-focused-20261011.log`, matching `-summary-` JSON |
| Transaction revision `602c12c` | `.\gradlew.bat test --tests 'com.veylon.UrbanLootGameplayTest' --tests 'com.veylon.UrbanFixtureGameplayTest' --tests 'com.veylon.save.*' --tests 'com.veylon.settlement.SettlementTheftTransactionTest' --tests 'com.veylon.CombustionLifecycleTest' --tests 'com.veylon.GameLoopIntegrationTest' --tests 'com.veylon.qa.RuntimeBoundsTest' --console=plain` | 162 tests, zero failures/errors/skips, 1 min 30 s; `m07-transactions-final-focused-20261011.log`, matching `-summary-` JSON |
| Independent regression `73ee6a3` | `.\build\m07-source-regression-check\gradlew.bat -p build/m07-source-regression-check test --tests 'com.veylon.world.urban.ContextualLootGenerationTest' --console=plain` | Exact staged-tree export: 3 tests, zero failures/errors/skips, 6 s; `m07-source-regression-check-20261011.log` |
| QA revision `df7e2b7` | `.\gradlew.bat test --tests 'com.veylon.UrbanLootQaSceneTest' --tests 'com.veylon.world.urban.ContextualLootGenerationTest' --tests 'com.veylon.UrbanLootGameplayTest' --console=plain` | 14 tests, zero failures/errors/skips, 7 s; `m07-qa-generation-focused-20261011.log`, matching `-summary-` JSON. First native run below validated the same QA source before presentation polish. |
| Extraction `dddb812` | `.\build\m07-orchestrator-check\gradlew.bat -p build/m07-orchestrator-check test --tests 'com.veylon.qa.OrchestratorSizeTest' --tests 'com.veylon.AllLivingBlastDeathTest' --tests 'com.veylon.CreativeBodyTest' --tests 'com.veylon.GameLoopIntegrationTest' --tests 'com.veylon.UrbanLootGameplayTest' --console=plain` | Exact staged-tree export: 108 tests, zero failures/errors/skips, 44 s; `m07-orchestrator-focused-20261011.log`, matching `-summary-` JSON |
| Final tree through `0ebb6a9` | `.\gradlew.bat build --console=plain` | Exit 0, 7 min 21 s; 1,737 tests, zero failures/errors, one Windows symlink-privilege skip, Javadoc/doclint passed. `build/m07-build-final-20261011.log` and `build/m07-build-final-summary-20261011.json`. Code frozen before the run. |
| Code graph | `graphify update . --no-cluster` | Exit 0, 7,574 nodes / 29,010 edges; `build/m07-graph-final-20261011.log`. Supersedes the premature pre-budget-fix 7,568 / 29,004 update in `m07-graph-update-20261011.log`. Existing `build.gradle` syntax/no-symbol and `settings.gradle` no-symbol limitations; no clustering or semantic extraction. |
| Documentation/fixture integrity | `powershell -NoProfile -ExecutionPolicy Bypass -File build/check-m06-record-links.ps1`; `git diff --check`; `Get-FileHash -LiteralPath src/test/resources/saves/urban-preloot-m06.sav -Algorithm SHA256` | 55 relative links/anchors resolved across six changed documents; whitespace check passed; authentic SHA-256 unchanged. The existing checker filename is historical; it checks the six current records. |

Production behavior coverage includes open/partial/leave/load/revisit/break/place
cycles without refill or duplicate contents; hidden/interrupted searches with
durable progress; normal scheduler clock, hunger and actual noise; full inventory
and full storage; one-item transfers both ways; condition/charge/freshness;
spoilage-to-empty; hand refusal/salvage; blast/fire destruction and closed ghost
screens in Creative/Survival; player versus natural-fire attribution; abandoned
versus survivor property; deduplicated theft and saved reputation. Three seeds
(`20260731`, `7`, `-4242`) prove several generated room sources for necessities,
clothing availability and secured-only firearm/ammunition tables. A boundary-
spanning building retains ids and every stack field under reversed chunk/search
order. Pure planning and picked-clean content-2 shelves retain no Inventories.

Persistence checks use real `SaveSystem.save/load`, including load over a live
world and failed save preserving its prior file. Corruption cases reject unknown
known-section versions, bad length/count/id/owner/progress/flags/references,
duplicate ids/cells, missing required content-2 sections, oversized core slots,
invalid item ordinals/count/metadata and duplicate core inventory rows. Failed
loads preserve the live session. Section order is irrelevant. The 32,768-entry
ledger refuses mutation before changing blocks/contents at capacity.

Authentic fixture `src/test/resources/saves/urban-preloot-m06.sav` was generated
by unchanged production `SaveSystem` exported from `cfcd591`: seed `20260731`,
generator 7, Survival, content 1, no new container section. It contains one empty
core inventory, one with three bandages and one destroyed generated reservation.
SHA-256: `361199bd25a754161713475958697c241c4535cb832fd8c0b97427b7e61302a3`.
It is retained unchanged, with provenance in that directory's README. Current
reader migration preserves empties/depletion, then round-trips them; content-1
core empty markers also prevent refills if the optional section is removed.
Fresh content 2 requires even an empty ledger. Older binaries were not run;
new-save downgrade is unsupported, independently of unknown-section skipping.

Iteration corrections before the successful checks: new tests initially called
a nonexistent mode setter and used integer camp-trust expectations; they now use
the actual mode API/float field. The headless generated fixture initially stayed
on TITLE, preventing normal simulation, and now explicitly enters PLAYING. The
shared `interactWithBlockAt` path initially omitted container opening; timed
search now uses that production command as well as normal F/RMB routing. The
successful results above follow these corrections; failed drafts are not passes.

The first portable build (`build/m07-build-initial-20261011.log`, 7 min 19 s)
ran 1,737 tests, with one failure, zero errors and one Windows privilege skip.
Its sole failure was `OrchestratorSizeTest`: Game had 1,014 lines against the
unchanged 1,000-line ceiling. Existing death/respawn rules were extracted to
`PlayerLifeCycle`, and storage destruction attribution moved into its transaction
owner. Game keeps its public commands/tick order and now has 991 lines. No limit,
save layout or gameplay policy changed. The focused exported-revision check and
final build reported above supersede this failed result.

### Native generated kitchen and survivor cupboard

Common environment: `VEYLON_GENERATOR=urban-dev` (8), `VEYLON_SEED=20260731`,
`VEYLON_GAME_MODE=survival`, `VEYLON_SCENE=urban_loot`,
`VEYLON_SHOT=1,4,6,10,14`, resolution `1280x720`, UI scale `1`, fullscreen `0`,
vsync `0`, GL diagnostics `1`, frontend/smoke unset. For each row set the capture
tag and run `.\gradlew.bat run -PveylonDataDir=build/qa/<directory> --console=plain`.

| Run | Directory under `build/qa/` | Capture tag | Result |
| --- | --- | --- | --- |
| First | `m07-contextual-loot-20261011` | `m07_contextual_loot_survival_720` | Exit 0, 16 s, five captures; `build/m07-contextual-loot-native-20261011.log` |
| Presentation correction | `m07-contextual-loot-final-20261011` | `m07_contextual_loot_final_survival_720` | Exit 0, 17 s, five captures; `build/m07-contextual-loot-native-final-20261011.log` |
| Final source after extraction | `m07-contextual-loot-verified-20261011` | `m07_contextual_loot_verified_survival_720` | Exit 0, 24 s including compilation, five captures; `build/m07-contextual-loot-native-verified-20261011.log` |

The driver selects actual INTACT/abandoned kitchen
`urban:fixture:0,0:0:0:0:6:2` and OCCUPIED/survivor cupboard
`urban:fixture:0,0:1:0:0:6:2`, owner `urban:building:0,0:1:0`, without injecting
structure, lights or loot. It positions once per interaction, opens through the
production command, waits for ordinary simulation to search, and takes one item.
Wall-clock camera updates alone fail the headless driver test; the deadline at
13 s also fails an interrupted native search. This is scripted production
interaction evidence, not a manual walk or a hostile encounter.

Final source log: kitchen searched/taken 1, `noise=true`, clock delta
6.399755840001831 minutes; owned searched/taken 1 with faction reputation
`-4.0`; `loot pass=true`, clock delta **15.999404799996682 game minutes**,
`hungerUsed=0.4478836`, `inventories=2`. Abandoned taking did not change reputation.
The 1 s/6 s captures show hidden contents and live progress for each ownership;
10 s shows searched storage with remaining food/water/treatment. These final
views were inspected at 1280 × 720. The first run exposed oversized cyan panel
corners and small instructions; final explicit eight-pixel corners and larger
status/instruction text are readable and unclipped. Search/transfer behavior did
not change with that polish.

All three runs reported zero GL, KHR error and native audio errors on NVIDIA GeForce
RTX 3060 Ti, OpenGL 3.3 / driver 610.88. Screenshot pixel-transfer performance
messages are not errors or benchmark evidence. No calibrated benchmark/listening
pass or cross-platform native run is claimed. Legacy camp/biome/startup HUD text
remains for 08/13/21. Specialist building families (12), full district gameplay
(13), resident/guard responses (16/17), final scarcity (23) and CPU streaming
eviction (24) retain their separate gates. **All required 07 gates passed.**
The next concrete step is 08's starter refuge/guaranteed supplies and first
urban upgrade; final scarcity tuning stays in 23.

## Milestone 08 — starter refuge and survival progression (2026-10-11)

Baseline: clean `feature/urban-survival` at `0d76b66`; 07 was complete. The pack's
full base knowledge was read from the sibling roadmap directory because the
linked root `00_BASE_KNOWLEDGE.md` is absent. AGENTS, current urban records and the
save-compatibility skill governed this work. D-75–D-79 record the new contracts.

### Production route and compatibility

`UrbanStarterProgressionTest` exercises seeds `20260731`, `-4242`, `7`, `0`,
`987654321` and `-9000000000000000000`. Each creates a normal Survival world,
spends the starter food/water, follows continuous production movement and door
commands through an apartment and shop, searches/takes finite generated supplies,
returns, treats bleeding/infection, crafts a reinforced spear at the existing
workbench, sleeps, saves/loads and respawns with the upgrade retained. No pose,
structure or resource injection occurs in that route after bootstrap. Live
ecology may grow plants; the route mines no ore and harvests no tree. It checks
≥ 6 food servings / ≥ 3 containers / 2 buildings, ≥ 3 clean-water units, treatment,
an unlocked pry tool and walking paths ≤ 96 blocks from the refuge courtyard door.

Separate isolated fixtures cover full-inventory cooking/refill/drinking failure,
rain renewal, wood-fuel cooking/boiling/lanterns, damaged-anchor fallback,
room rain/smoke and Survival/Creative twins. The original recipes are still
selected for legacy and generator-8 worlds. New/load does not restock consumed
fire/water. `UrbanRefugesSectionTest` checks identity/coordinate/version/strict
presence/length corruption and missing-required-section failure without replacing
the live world. `UrbanStarterGenerationTest` checks actual starter voxels and
accessibility over six seeds in opposite chunk orders, plus unchanged street and
parcel plans outside the origin starter region. Older binaries were not run;
they reject generator 9, and no downgrade support is claimed. No enum was added.

### Commands and iteration results

All Gradle commands used the bundled JDK 25.0.4.1 and Windows wrapper. Logs are
local ignored artifacts under `build/`.

- `test --tests com.veylon.item.CraftingTransactionTest --tests com.veylon.RelicProgressionGameplayTest --console=plain`
  passed on the independent transactional-crafting revision committed as `89a0edd`;
  `m08-crafting-tests.log` records that exact intermediate check.
- `test --tests com.veylon.UrbanStarterProgressionTest --tests com.veylon.world.urban.UrbanStarterGenerationTest --tests com.veylon.save.UrbanRefugesSectionTest --tests com.veylon.save.UrbanContainersSectionTest --tests com.veylon.UrbanDevelopmentSelectorTest --tests com.veylon.GameLoopIntegrationTest --tests com.veylon.world.WorldGeneratorFingerprintTest --console=plain`
  passed in 16 seconds (`m08-starter-final-focused.log`).
- The broader iteration run had 231 checks and one stale content-revision
  expectation. The first portable build had 1,751 checks and one stale
  unsupported-template assertion (`m08-build.log`). Both assertions were updated
  for the explicitly added revisions; production budgets/rules were not relaxed.
  `test --tests com.veylon.world.urban.AccessibilityValidatorTest --console=plain`
  then passed (`m08-accessibility-final.log`).
- The next `.\gradlew.bat build --console=plain` passed in 7 min 53 s:
  1,751 tests, zero failures/errors, one existing Windows symlink-privilege skip,
  with Javadoc/doclint (`m08-build-final.log`).
- Final review corrected the lantern log to name recovered wood. The subsequent
  focused run executed all seven starter progression tests, including actual
  450-second lantern refuel/lighting, with zero failures/errors/skips in nine
  seconds (`m08-fuel-final-focused.log`). Its additional filter
  `com.veylon.LanternGameplayTest` matched no class; the actual legacy class is
  `com.veylon.world.LanternGameplayTest`, covered by the portable build.
- Final `.\gradlew.bat build --console=plain` passed in **7 min 28 s** on
  `bc0a363`: **1,751 tests, zero failures/errors, one existing Windows privilege
  skip**, including Javadoc/doclint (`m08-build-verified.log`, counts retained in
  `m08-build-verified-counts.json`). Source did not change after this run started.
- `test --tests com.veylon.UrbanStarterProgressionTest --tests com.veylon.world.LanternGameplayTest --console=plain`
  resolved the earlier unmatched filter and passed **10 tests**, zero
  failures/errors/skips in five seconds (`m08-starter-lantern-verified.log`).
- `graphify update . --no-cluster` ran once on the final code: **7,635 nodes /
  29,324 edges**, exit 0 (`m08-graph-final-20261011.log`). Existing Gradle syntax/
  symbol-extraction warnings remain. No clustering or document/image semantic
  extraction ran. The six-document path/anchor checker resolved **56** relative
  links; staged/unstaged `git diff --check` passed. Code commits are `89a0edd`
  and `bc0a363`; documentation is the following independent handoff commit.

### Fresh native evidence

The final route command was `.\gradlew.bat run -PveylonDataDir=build/qa/m08-starter-final-20261011 --console=plain`,
with `VEYLON_GENERATOR=urban-dev`, `VEYLON_SEED=20260731`,
`VEYLON_GAME_MODE=survival`, `VEYLON_SCENE=urban_starter_manual`,
`VEYLON_SHOT=5,1200`, and `VEYLON_CAPTURE_TAG=m08-starter-final`.
The scene only observes: it grants no items and changes no position, time or
weather. OS keyboard/mouse events were restricted to the foreground game window.
This is a scripted walk through normal controls, not a human playtest.

The spawn was `(65.5,41,83.5)` in the refuge bedroom. Movement keys, mouse and F crossed
the courtyard, opened generated apartment doors and searched the kitchen cabinet
at `(65,41,75)`. Normal UI clicks emptied it into the player's inventory:
four food servings, two clean-water containers, two planks and two scrap including
the initial kit. Search state remained depleted. The route returned to
`(65.58,41,83.67)` by t=495 and saved with F5. Health/needs/weather continued normally;
ashfall, a cold snap and toxic fog occurred during pauses for tool inspection.
The refuge read Indoors on return; sickness already acquired outside persisted.
No invulnerability, time reset, teleport or tree/ore action was used.

Artifacts in that directory's `screenshots/`:
`m08-starter-final_5s.png` (spawn), `m08-first-search.png` (hidden live search),
`m08-first-supplies.png` (depleted cupboard and transferred supplies),
`m08-first-scavenging-return-verified.png` and `m08-return-torch.png` (bedroom return).
The return is a dark night/toxic-fog view; the position log independently confirms
the bedroom. Earlier `m08-first-scavenging-return.png` / `m08-refuge-return.png`
were premature doorway-alignment attempts, not successful return evidence.
The route exited cleanly in 8 min 44 s (`m08-starter-final-native.log`), with zero
GL/KHR and native-audio errors. The screenshot pixel-transfer warning is not an error.

After the lantern text correction, the same environment with shots `5,10`, tag
`m08-starter-source-final` and data directory
`build/qa/m08-starter-source-final-20261011` launched the final source and exited
cleanly in 16 seconds (`m08-starter-source-final-native.log`), again with zero
GL/KHR/native-audio errors. This confirms final-source spawn/rendering; the longer
normal-controls route above preceded only that wording correction. Two earlier
fresh sessions were startup/input-calibration attempts, not completed routes.
Native treatment, sleep, upgrade, a shop visit and human comfort/balance testing
were not performed; the required mechanics are covered by the production route
tests. Infected encounters remain 09, claims 18, utilities 19 and economy tuning 23.

### Owner scheduling update after the 08 handoff

On 2026-10-11, the owner deferred human testing until after milestone 22 (D-80).
The optional 08 human walk remains unperformed and joins that consolidated pass.
Upcoming human-only playtest/listening checks must be recorded as deferred rather
than passed; implementation may advance through 22 under this instruction.
Automated checks, builds and scripted native evidence remain required during
development. This scheduling update adds no validation result and does not alter
the actual human evidence previously recorded for 06.

## Milestone 09 — infected threats and encounters (2026-10-12)

### Scope and compatibility

The implementation uses an appended, ordinal-pinned `NpcArchetype.VEILBOUND`
and stable species id `veilbound`. The existing humanoid body/combat/anatomy
and remains paths apply, with explicit infected allegiance/AI/death dispatch.
There is no new disease, animal carcass, harvest resource or kill drop. Four
procedural positioned cues have independent presentation randomness. D-81–D-85
record the initial biology, combat values, encounter and persistence policies.

Generator 10 / layout 7 enables encounter catalogue 1. Template 4 and content 3
remain unchanged; six seeds compare actual starter block bytes against generator
9 in opposite chunk orders. The packed encounter kind appends code 8; existing
kind codes, body enum ordinals and frozen core bytes stay unchanged. Default new
world creation remains generator 3. Earlier generators load without population
state and gain no encounters. Older binaries were not run; no downgrade support
is claimed for generator 10 or the new body profile.

`urban.population` v1 is required even when empty for generator 10. It stores
codec/catalogue/count and fixed 47-byte resident rows. The live-NPC core binding
is one-to-one; dormant injuries/positions and permanent death tombstones remain
authoritative. Saturation refuses new encounters instead of discarding deaths.
Actual save-envelope corruption tests cover missing/truncated state, negative,
oversized and maximum-int counts, unknown codec/catalogue/species, invalid or
duplicate anchors, nonfinite scalars, invalid wounds, bad/duplicate core indices,
unbound bodies and mismatched health. Failed loads preserve the original live
world/player. Active, dormant and defeated residents survive repeated saves;
save immediately after a lethal hit preserves exactly one humanoid corpse.

### Behavior and population evidence

- `InfectedBehaviorTest` exercises production senses, supported pursuit, actual
  player melee, interrupted windup, dodging, wall occlusion and noise-position
  investigation. Creative hides the player and blocks physical damage; Survival
  receives the strike through the normal damage/bleeding path. Trust stays
  unchanged. The all-living combat/fire/projectile/anatomy matrix includes the
  infected profile without staging it as a human crowd member.
- `UrbanEncounterPopulationTest` covers supported street and actual ground-floor
  room anchors over six seeds; visible/breathing-room/occupied/refuge guards;
  missing floor, blocked headroom and unloaded footprints; caps and shared NPC
  counterattack reservation; paused Creative spawning; injured dormancy,
  death/depletion and new-world reset. A moved pursuer reactivates at its durable
  position away from its home parcel. An unloaded body cannot fall before
  dormancy. Fresh production starter ticks create encounters on all six seeds
  while preserving sanctuary, spacing and bounded scan/admission work.
- Runtime snapshots include the 12-active and 32,768-record limits. Admissions
  are capped at two per slow tick, with 16 parcel and 16 saved-position checks.
  Same-floor route searches share two queries per fast tick, 400 expansions each.
  No runtime, allocation or orchestrator line budget was raised.
- `GameLoopIntegrationTest` checks reset; `UrbanInfectedQaSceneTest` prevents
  elapsed wall time without simulation from reporting a native success. Enum,
  generator and development-selector expectations pin the appended revisions.

### Commands and iteration results

All Gradle commands used the Windows wrapper and local JDK 25.0.4.1+1. Logs and
captures below are ignored local `build/` artifacts, not historical evidence
substituted for current checks.

- The independent humanoid slice passed focused body/combat/enum/audio checks
  (263 tests) and an exact staged-revision worktree build before commit
  `e3211a7`: `.\gradlew.bat build --console=plain`, **8 min 13 s, 1,762 tests,
  zero failures/errors, one existing Windows symlink-privilege skip**, including
  Javadoc/doclint (`m09-body-build.log`, `m09-body-build-counts.json`).
- Encounter/save/generated geometry/NPC-budget checks passed in 15 seconds
  (`m09-encounter-focused.log`); state/runtime/revision checks passed in 11 seconds
  (`m09-state-tests.log`). An earlier population fixture incorrectly filled the
  shared nonresident budget with legacy bodies; it was corrected to 24 residents
  plus eight legacy bodies without changing production limits. An early audio
  expectation also assumed the final three cues were the fire cues; the added
  independent cue catalogue is now checked explicitly.
- The first complete encounter build failed in 9 min 24 s with **1,771 tests,
  three failures** (`m09-build-final.log`). The failures were stale expectations
  in `UrbanDevelopmentSelectorTest` (two) and `UrbanGeneratorDispatchTest` (one)
  after adding generator 10; those checks were updated to pin the actual new
  supported revision. Two further streaming/starter safety tests were added.
- `.\gradlew.bat test --tests '*UrbanEncounterPopulationTest' --tests '*UrbanPopulationSaveTest' --tests '*UrbanDevelopmentSelectorTest' --tests '*UrbanGeneratorDispatchTest' --tests '*InfectedBehaviorTest' --tests '*GameLoopIntegrationTest' --console=plain`
  passed **38 tests, zero failures/errors/skips, 26 seconds**
  (`m09-safety-focused.log`, `m09-safety-focused-counts.json`).
- Final `.\gradlew.bat build --console=plain` passed in **7 min 50 s** on the
  source committed as `7827f3c`: **1,773 tests, zero failures/errors, one existing
  Windows symlink-privilege skip** (`AppPathsTest.aLinkToThePlayersDirectoryIsNotIsolated`),
  including Javadoc/doclint (`m09-build-verified.log`,
  `m09-build-verified-counts.json`). Source did not change during or after this
  run; the final native captures use that same source.
- `graphify update . --no-cluster` ran once on the final code, exit 0: **7,748
  nodes / 29,822 edges** (`m09-graph-final-20261012.log`). Existing `build.gradle`
  syntax and `settings.gradle` no-symbol extraction warnings remain. No
  clustering, document/image or LLM semantic extraction ran. Code commits are
  `e3211a7` and `7827f3c`; the records follow independently.
- The seven-document Markdown path/anchor checker resolved **57 relative links**
  (`m09-record-links.log`). Staged and unstaged `git diff --check` passed. The
  exact intermediate validation worktree matched the committed body tree before
  removal; its build log and XML counts were retained locally. `main` remains
  `923345f`; no push, merge, tag or publication occurred.

### Fresh native evidence

The final scene commands were:

```powershell
.\gradlew.bat run -PveylonDataDir=build/qa/m09-street-final --console=plain
.\gradlew.bat run -PveylonDataDir=build/qa/m09-apartment-final --console=plain
```

Each used `VEYLON_GENERATOR=urban-dev`, `VEYLON_SEED=20260731`,
`VEYLON_GAME_MODE=survival`, `VEYLON_RESOLUTION=1280x720` and
`VEYLON_SHOT=0.7,3,4.1,8,14,17`. Scene/tag pairs were
`urban_infected_street` / `m09-street-final` and
`urban_infected_apartment` / `m09-apartment-final`.

Both use production anchor admission and unchanged generated blocks. Observer
poses expose normal-scheduler attack windup and Survival damage, an actual
unarmed player pain hit, direct instrumented ignition with panic/damage, then a
head-height projectile death. Real save/load at 12 seconds preserves the dead
id and exactly one humanoid remains record; reactivation refuses that slot.
Both logged `[urban-infected] pass=true`, `savedDead=true`, `respawned=false`
and `humanoidRemains=1`. Another slot may remain active; that is a distinct
resident. The street run exited in **24 seconds**, apartment in **19 seconds**,
with **zero GL, KHR and native-audio errors** (`m09-native-street-final.log`,
`m09-native-apartment-final.log`). Driver shader/pixel-transfer performance
messages are warnings, not errors or calibrated benchmark results.

Each data directory has six captures under `screenshots/`, suffixed
`_0s.png`, `_3s.png`, `_4s.png`, `_8s.png`, `_14s.png`, `_17s.png` (fractional
shot times are truncated in filenames). Final street windup/pain/fire captures
and apartment initial pose/settled corpse were visually inspected. The head
tilt and braced shoulder, attack/pain/fire poses and humanoid corpse are visible.
Earlier 2026-10-11 scene runs also passed; the first apartment attempt failed
because the QA observer unnecessarily demanded the original room after panic
movement. The observer restriction was limited to initial setup, then both
final scenes were rerun. This changed QA observation, not world geometry or
production navigation.

Creative World Controls captures ran separately with scene unset,
`VEYLON_FRONTEND=worldcontrols`, `VEYLON_GAME_MODE=creative`, the same generator
and seed, and `VEYLON_SHOT=2`. Commands used isolated directories
`build/qa/m09-controls-720` and `build/qa/m09-controls-1080`, resolutions
1280×720 and 1920×1080, and tags `m09-natural-spawning-720` /
`m09-natural-spawning-1080`. Both exited in **4 seconds**, with zero
GL/KHR/native-audio errors (`m09-native-controls-720.log`,
`m09-native-controls-1080.log`). Both screenshots were inspected: the updated
Pause natural spawning control is legible and contained in the panel.

### Limits and next gate

These are scripted native mechanics fixtures with observer poses and
instrumented fire/projectile inputs; they supply no scavenging or ammunition
economy evidence. Human attack readability, comfort and listening are deferred
until after 22 under D-80, not claimed as passed. Spawn sanctuary guards do not
prevent an already active infected from pursuing into an exposed refuge.
Supported same-floor pursuit is verified. Multi-floor portals, edited-door/stair
routes, vertical pursuit and sound attenuation remain **10**; door/barricade
destruction remains **18**. No healing, migration or repopulation is supported.
The next concrete step is milestone 10 with budgeted portal/navigation fixtures
and a native pursuit/escape, preserving the existing encounter identities and
permanent depletion state.

## Milestone 10 — indoor navigation and perception (2026-10-12)

### Scope and regression coverage

Baseline: `feature/urban-survival` at `9651f6b`, clean before editing. Navigation
is committed as `edf3d45`, perception/lifecycle/native QA as `ae80b14`; records
follow independently. The pack's
`00_BASE_KNOWLEDGE.md` was absent from the repository root; the full sibling
roadmap copy was read, followed by current AGENTS and the urban records. D-80
continues to defer human-only testing until after 22. The performance skill was
used for bounded-work accounting; no calibrated wall-clock benchmark is claimed.

New production fixtures cover two-floor generated pursuit and Creative twins;
all four building frontages and both stair directions, including roof/basement
bands; ladder travel and rejection of a four-block drop; exact floor goals;
loaded-column refusal without generation; support edits; barred/unbarred doors
and preservation of unrelated cached routes; long local legs and connected
prefixes from exhausted searches. Perception fixtures cover occluded floors,
quiet rummage versus gunfire, consistent locked/unlocked closed-door attenuation,
consumed evidence, hearing work ceilings and departed pursuit references.
Existing daily-life, patrol, counterattack, fire-panic, Creative and save tests
remain required by the portable build. Save formats, enum order, generation and
encounter identities are unchanged.

`PathBudget` is reset once by `EntityManager.fastTick` and enforced by Pathfinder:
four queries / 2,800 expansions in total, 700 per local query. Cache hits consume
a query but no expansions. There are 64 cached routes, each at most 512 cells.
The mixed 24-body contention fixture measured **4 queries, 2,800 expansions,
4 incomplete searches and 20 deferred requests**, all with safe stopped bodies
on that initial tick. Partial searches return only connected swept-safe prefixes,
cached to their real endpoints. They do not claim the final goal is reachable.
The generated headless pursuit measured a peak of 1 query / 33 expansions after
the repeated-noise route-retention fix. Hearing caps are 16 queries / 4,096 shared
samples; unloaded or exhausted rays are inaudible. These deterministic work counts
do not substitute for frame-time or allocation benchmarks in 24.

### Commands and iteration results

Host: Windows NT 10.0.26200.0, AMD Ryzen 5 5600X 6-Core Processor, bundled JDK
25.0.4.1+1 at `build/tools/jdk25/jdk-25.0.4.1+1`. `JAVA_HOME` was set for each
Gradle shell. The sandbox denied the JDK security file on the first attempt;
authorized Gradle execution then worked. No budget was raised to pass tests.

The final expanded focused command was:

```powershell
.\gradlew.bat test --tests '*IndoorNavigationTest' --tests '*IndoorPerceptionTest' --tests '*VillageDailyLifeGameplayTest' --tests '*CounterattackLifecycleTest' --tests '*SettlementRuntimeTest' --tests '*FirearmGameplayWorkflowTest' --tests '*UrbanResidentFixtureTest' --tests '*InfectedBehaviorTest' --tests '*CreativePerceptionTest' --tests '*PerformanceInvariantTest' --tests '*HumanPerceptionGameplayTest' --tests '*UrbanEncounterPopulationTest' --tests '*UrbanPopulationSaveTest' --console=plain
```

It passed **76 tests, zero failures/errors/skips**, in 27 seconds
(`m10-focused-verified.log`, `m10-focused-verified-counts.json`). Removing an
unnecessary route reset for repeated identical sound evidence was followed by
`IndoorNavigationTest`, `IndoorPerceptionTest`, `InfectedBehaviorTest`,
`CreativePerceptionTest` and `HumanPerceptionGameplayTest`: **36 tests, zero
failures/errors/skips**, 12 seconds (`m10-evidence-final.log` and counts).
The departure-reference test was then added for the final portable build.

The exact navigation-only staged tree was reproduced in detached
`build/m10-nav-validation`, keeping the perception changes out. Its focused
command used `IndoorNavigationTest`, `UrbanResidentFixtureTest`,
`InfectedBehaviorTest`, `RuntimeBoundsTest`, `PerformanceInvariantTest`,
`UrbanEncounterPopulationTest`, `UrbanPopulationSaveTest`,
`VillageDailyLifeGameplayTest`, `CounterattackLifecycleTest` and
`SettlementRuntimeTest`: **58 tests, zero failures/errors/skips**, 29 seconds
(`m10-navigation-intermediate-final.log` and counts). This validates the actual
intermediate revision, rather than inferring it from the final tree.

The first portable build failed: **1,785 tests, four failures, one existing
Windows symlink-privilege skip** (`m10-build.log`). Two perception fixtures had
used unloaded AIR as a clear gunshot/patrol lane; they now load and prepare their
actual lane. Two movement regressions were fixed in production: distant legacy
missions can advance through loaded local anchors before their final goal loads,
and a villager whose home search exceeded 700 expansions can follow a safe
connected prefix before replanning. No direct steering fallback was restored.
An earlier event-reset regression was also fixed by keeping noise sequence ids
monotonic when live listeners survive queue clearing. All affected focused
checks were rerun successfully. The initial multi-floor noise fixture used a
radius too small after slab attenuation; its explicit gunshot now uses radius
128. This changes supplied test evidence, not production gunshot radius.

Final `.\gradlew.bat build --console=plain` passed in **7 min 26 s**:
**1,787 tests, zero failures/errors, one existing Windows symlink-privilege skip
in AppPathsTest**, including Javadoc/doclint (`m10-build-final.log`,
`m10-build-final-counts.json`). The always-required compatibility, fingerprint,
determinism, runtime and orchestration checks ran as part of that build.
Production source stayed unchanged from the final native run through the build;
the additional departure regression is included. No opt-in `performanceTest`
or calibrated FPS benchmark was run or claimed.

The exact intermediate validation tree was
`4e095fd2f8711675cd0a833e810975d720cff901`; it matched `edf3d45` before the
disposable checkout was removed. Logs and XML counts were retained locally.
`graphify update . --no-cluster` ran once at completion and exited 0 with
**7,814 nodes / 30,097 edges** (`m10-graph-update.log`). Existing `build.gradle`
syntax and `settings.gradle` no-symbol warnings remain. No clustering,
document/image or LLM semantic extraction ran. The six-document relative
Markdown path/anchor check resolved **58 links** (`m10-record-links.log`);
staged/unstaged `git diff --check` passed. `main` remains `923345f`. No push,
merge, tag or publication occurred.

### Final native generated pursuit and escape

```powershell
.\gradlew.bat run -PveylonDataDir=build/qa/m10-navigation-verified --console=plain
```

Settings: `VEYLON_GENERATOR=urban-dev`, `VEYLON_SEED=20260731`,
`VEYLON_GAME_MODE=survival`, `VEYLON_RESOLUTION=1280x720`,
`VEYLON_SCENE=urban_navigation`, `VEYLON_CAPTURE_TAG=m10-navigation-verified`,
`VEYLON_SHOT=2,12,20,21,24,54,84`. The final run passed in **1 min 26 s**
(`m10-native-verified.log`), with **zero GL, KHR and native-audio errors**.
The KHR pixel-transfer/shader messages are driver performance warnings.

The generated building is `3458764513820540928`; the fixture Veilbound starts
at `(18,41,5)` and investigates positioned gunshot evidence at the floor-2
bedroom `(11,49,7)`. No blocks or paths are injected. Normal AI/physics reaches
that floor in **17.0628138 seconds**. The scripted target then moves to an
occluded basement pose and stops emitting evidence. The observer checks unchanged
last-known position, expired timer, cleared path and stopped horizontal velocity
after 20 seconds. The run logged `reachedTwoFloors=true` and
`pass=true lostSight=true expired=true`, with peaks **1 query / 33 expansions /
25 hearing samples** in a tick. Earlier native runs passed before the final
route-retention change; they are iteration evidence only.

Seven captures are under the isolated data directory's `screenshots/`. The
12-second upstairs door/partition capture was inspected; other captures mostly
show the occluded basement observer pose. The captures alone do not prove the
actor's route: the normal-scheduler position/collision/evidence assertions and
log supply that mechanics evidence. Scripted player poses and emitted events
are explicit; no human escape walk, ammunition economy or listening pass is
claimed. The real save slot was not used.

### Supported traversal, tuning and next gate

Supported: loaded walking, one-block steps, at most the existing three-block
safe drop, generated stair/landing clearance, unlocked doors/gates and supported
ladders. Intact windows and barred/keyed doors block body traversal; an opening
must have actual support and body clearance. Block/damage and lock changes
invalidate intersecting route envelopes; ordinary unlocked door swings preserve
equivalent routes. Unsupported entries and blocked routes wait/retry safely.
New/load and activation rebuild transient routes; departures clear live pursuit
references. Teleports/flight add no observation, and hidden/unreachable evidence
expires rather than becoming a ground/roof target.

The hierarchy is modest: no arbitrary citywide detour guarantee, automatic
forced-door/barricade destruction or whole-building sound propagation. Destruction
is 18. Attenuation and crowded replan/hearing latency remain tuning for 11/24;
calibrated dense-city timing is 24. Human pursuit/escape, comfort and listening
are deferred after 22 under D-80. The next concrete step is **11**: the integrated
generated scavenging/refuge/save/revisit route, permanent depletion, Creative
twin, frozen regression seeds and explored-area memory accounting.

## Milestone 11 — playable vertical slice gate (2026-10-13)

### Scope and baseline

Baseline: clean `feature/urban-survival` at `d16be92`. I read the full revised
base knowledge (`build/urban-survival-roadmap-revision/00_BASE_KNOWLEDGE.md`)
and the prompt, then AGENTS, the commit workflow and these records. D-80 still
defers human playtesting and listening until after 22; D-91–D-97 record this
package's contracts.

Nothing below changed in this package, and ordinary new worlds remain
generator 3:

- generator, layout, template and content revision;
- encounter catalogue;
- save section layout and serialized enums;
- the new-world default.

### Probes before the route

These were throwaway headless probes, deleted before commit and not repository
tests.

- **Starter neighbourhood on generator 10, six seeds.**
  - Every seed has the refuge (parcel 44), the shop (41) and four three-storey
    apartments with B1 (40, 42, 43, 45). No starter door is locked.
  - Every apartment has two encounter slots.
  - After 64 slow ticks from the bedroom spawn, 6–12 Veilbound were active about
    25–48 blocks from the refuge door. That count includes selected parcels in
    neighbouring superblocks.
  - Parcel 40's anchors never activate, because they lie inside the refuge
    sanctuary.
- **Climate on generators 9 and 10.** I stood in the refuge bedroom for ten
  minutes on both generators, with identical results. A cold snap lowered the
  outside temperature to −3.4 °C and body temperature fell below the 33 °C
  freezing threshold. On seed 0, health dropped to 95.9.
- **A natural loot-and-fight policy.** I searched all four apartments and the
  shop using continuous melee.
  - The player took 0–2 damage, even unarmed, because melee pain-locks a
    Veilbound.
  - Two runs stalled because a cold-snap drain cancelled every search (B1).

### Blockers fixed in existing package scope

For each blocker, the regression test fails against the old code and passes
with the fix. I reverted each mutation afterwards.

| Id | Defect | Fix | Regression |
| --- | --- | --- | --- |
| B1 (07) | Any health decrease cancelled a search, including freezing drains of about 0.002 HP per tick. During a cold snap nothing could be searched. | `Player.harmEvents`; only discrete harm interrupts (D-92) | `UrbanLootGameplayTest.overTimeDrainsDoNotInterruptASearchThatABlowWould` fails with the original `CrateTransactionSystem` |
| B2 (09) | The camp-illness event chose a Veilbound and logged "The camp needs MEDICINE" in a city without a camp. | The victim search skips infected (D-93) | `UrbanSurfaceSpawnGateTest.campIllnessNeverChoosesAVeilboundInACity` fails when the skip is removed; `EventRollLadderTest` is unchanged |
| B3 (09) | `urban.population` validation (live ≤ 1, dead ≤ 2) rejected projectile torso wounds. One torso arrow made F5 fail. A shot-down tombstone made every later save fail. | Bounds derived from `ProjectileLethality` (D-94) | `UrbanPopulationSaveTest.projectileTorsoWoundsSaveWhileAliveAndAfterAShotKill` fires real arrows and fails with the old bounds; the corruption cases moved to 6 and −1 |
| B4 (07) | F closed a container the player was facing, and the same frame's interaction pass reopened it. The screen says "[F or Esc] close". | The press is consumed (D-95) | `ContainerCloseKeyRoutingTest` fails without the consume |

### The route and its evidence

`UrbanSlicePlan` and `UrbanSliceQaScene` (`VEYLON_SCENE=urban_slice`) play the
route described in DESIGN §14 through these production paths, and stage
nothing:

- `Input.onKey` and `Input.onMouseButton`;
- camera turns as mouse look;
- the crate transfer commands;
- `CraftingScreen.craft`.

`Game.playStep` is the frame's control and simulation step, moved verbatim.
`UrbanSliceRun` drives the scene headlessly in `Game.run` order and streams at
the render radius.

`UrbanSliceRouteTest` runs the six frozen seeds in both modes. Besides the
route's own checks, it asserts:

- tombstones, and reactivation refused;
- the reinforced spear in the inventory, and no log, coal or ore;
- the changed doors: `BROKEN_DOOR` (Creative: `AIR`) and `LOCK_BARRED`
  (Creative: `LOCK_KEYED`);
- the Creative body;
- walking reachability of every apartment room after the edits;
- runtime bounds.

The neighbourhood test proves, for every seed:

- a courtyard route between the refuge and the apartment that crosses the
  courtyard;
- a street route that never enters the superblock;
- shop access;
- every slice target within 96 walking cells of the refuge's courtyard door;
- `AccessibilityValidator.inspectGenerated` passing for the refuge, apartment
  and shop.

The order matrix:

1. Saves after the route and loads the save into a new `Game`.
2. Regenerates the starter superblock's chunks in row-major, reverse,
   column-major, shuffled and far-corner-spiral orders, comparing every block
   and height map.
3. Checks that the forced door and the barred leaves are present and that no
   container inventory is created.

Headless route observations from the final focused run. Game time runs at 1.6
minutes per real second.

| Seed | Mode | Engaged / defeated | Damage | Harm events | Events | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 20260731 | Survival | 2 / 2 | 0.0 | 0 | ashfall | apartment 42 |
| -4242 | Survival | 1 / 1 | 0.0 | 0 | cold snap | body 33.2 °C |
| 7 | Survival | 3 / 3 | 8.0 | 1 | toxic fog | one blow landed; sickness |
| 0 | Survival | 2 / 2 | 10.9 | 0 | cold snap, storm | body 29.6 °C (freezing drain) |
| 987654321 | Survival | 1 / 1 | 0.0 | 0 | heat wave | — |
| -9000000000000000000 | Survival | 2 / 2 | 41.3 | 1 | toxic fog | bleeding and sickness; minimum 58.7 HP |
| all six | Creative | 1 / 1 each | 0.0 | 0 | as Survival | eating declined while full; drinking free; no tool wear |

Every Survival route:

- walked 563–657 blocks in 168–188 route seconds (268–300 game minutes);
- searched six containers;
- verified three on the revisit;
- ended with the spear.

Two production mutations each failed `UrbanSliceRouteTest`; I restored both
with `git checkout`:

- reopening an emptied, searched container re-rolled its starter supplies
  ("refill or stale container at 65, 41, 114");
- dead residents reactivated ("encounter … came back").

### Commands and iteration results

All Gradle commands used the bundled Temurin 25.0.4.1+1
(`build/tools/jdk25/jdk-25.0.4.1+1`) and the Windows wrapper. Logs are local
ignored artifacts under `build/`.

- `test --tests com.veylon.save.UrbanPopulationSaveTest`: passed with B3 and
  failed with the old bounds (`m11-wounds.log`, `m11-wounds-mutant.log`).
- `test --tests com.veylon.simulation.UrbanSurfaceSpawnGateTest --tests com.veylon.simulation.EventRollLadderTest`:
  passed with B2 and failed without it (`m11-illness*.log`).
- `test --tests com.veylon.UrbanLootGameplayTest --tests com.veylon.UrbanLootQaSceneTest --tests com.veylon.CreativeHazardsTest --tests com.veylon.SurvivalCreativeParityTest`:
  passed with B1. The B1 test failed against the original
  `CrateTransactionSystem` (`m11-search-drain*.log`).
- `test --tests com.veylon.ContainerCloseKeyRoutingTest --tests com.veylon.CreativeCatalogRoutingTest --tests com.veylon.AudioMenuRoutingTest`:
  passed with B4 and failed without it (`m11-fclose*.log`).
- `test --tests com.veylon.UrbanSliceRouteTest --tests com.veylon.UrbanSliceQaSceneTest`:
  5 tests passed (`m11-slice-tests.log`); the route class takes about 11 s.
  Earlier failures were in the runner or test, not the game:
  - the save check compared the whole timestamped log line;
  - `inspectGenerated` rejects player-opened and forced doors as fixture
    mismatches, so post-route reachability now walks routes instead;
  - the Creative eat step is declined while the body is full.

An adversarial review covered four dimensions (fixes, runner, tests, contracts),
with a skeptic for each finding. It confirmed seven distinct low-severity runner
or test issues and refuted three. All seven are fixed:

- per-target approach walkers;
- releasing the replaced world before the memory checkpoints;
- counting a resumed search once;
- checking F5, F9 and consumption by log identity;
- an explicit Creative decline;
- a street route restricted to cells outside the block;
- an isolation guard before the quick-save.

The final portable build ran on 2026-10-14:
`.\gradlew.bat build --console=plain` passed in 8 min 53 s with 1,796 tests in
195 classes, zero failures or errors, and one existing Windows
symlink-privilege skip in `AppPathsTest` (`m11-build.log`). Its Javadoc task
was up to date from an earlier run on identical inputs, which was then
interrupted inside `:test` when the session restarted; that interrupted run is
not counted. Production source did not change between the final native runs
and this build.

Each code commit was checked against its exact tree in the detached worktree
`build/m11-check`. Each check compiled main and test sources, ran Javadoc and
ran the commit's focused tests. Every tree hash matched the index that was then
committed. All checks had zero failures, errors or skips.

| Commit | Tests | Notes |
| --- | --- | --- |
| `0318966` | 11 | |
| `01c42ec` | 15 | |
| `2f63652` | 53 | |
| `51578ad` | 45 | Also a line comparison proving a pure move |
| `1e60097` | 29 | |
| `51c5a9c` | 34 | |
| `406803b` | 39 | Includes the always-required compatibility set |

`graphify update . --no-cluster` ran once on the final code and produced
7,966 nodes and 30,714 edges (`m11-graph-update.log`), with the existing
Gradle-file extractor warnings. No clustering or semantic extraction ran.

### Final native route, both modes

Host and settings:

- Windows 11 Pro 10.0.26200, AMD Ryzen 5 5600X 6-core;
- NVIDIA GeForce RTX 3060 Ti, driver 610.88, OpenGL 3.3.0 with KHR_debug on;
- Temurin 25.0.4.1+1, launcher `-Xmx2G`;
- 1280 × 720, default render distance 6, fresh data directory.

I ran the command once with `survival` and once with `creative`:

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='<mode>'; $env:VEYLON_SCENE='urban_slice'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m11-slice-<mode>'
.\gradlew.bat run -PveylonDataDir=build/qa/m11-slice-<mode> --console=plain
```

| Mode | Result | Route s | Captures | GL / KHR / native-audio errors | Defeated | Damage |
| --- | --- | --- | --- | --- | --- | --- |
| Survival | `pass=true`, exit 0, 3 min 9 s | 187 | 16 | 0 / 0 / 0 | 2 | 0 |
| Creative | `pass=true`, exit 0, 2 min 50 s | 168 | 16 | 0 / 0 / 0 | 1 | 0 |

Memory checkpoints. Heap is a best-effort sample after a GC. Retained chunks
are `World.loadedCount()`, at 26,624 B of fixed arrays per chunk.

| Mode | Phase | Retained chunks | Meshed | Chunk arrays | Containers / edits / fixture states | Heap used MiB |
| --- | --- | --- | --- | --- | --- | --- |
| Survival | route end | 254 | 239 | 6.76 MB | 6 / 29 / 24 | 22.2 |
| Survival | after F9 | 129 | 12 | 3.43 MB | 6 / 29 / 24 | 27.5 |
| Survival | after revisit | 238 | 238 | 6.34 MB | 6 / 40 / 26 | 21.7 |
| Creative | route end | 239 | 239 | 6.36 MB | 6 / 24 / 26 | 21.6 |
| Creative | after F9 | 129 | 12 | 3.43 MB | 6 / 24 / 26 | 26.9 |
| Creative | after revisit | 238 | 238 | 6.34 MB | 6 / 30 / 28 | 31.0 |

At every checkpoint region plans stayed at 4 or fewer (2 after the load),
building plans at 104 or fewer, routes at 4 or fewer and encounter records at 6
or fewer, all with `withinHardLimits=true`.

- These numbers cover this one explored area on this host; they are not
  budgets.
- The edits added after the load are the revisit's doors and ecology.

Captures I inspected:

- **Survival 04:** a forced doorway with debris.
- **Survival 06:** the locker with two torches left and the pry tool in hand.
- **Survival 08:** a Veilbound faced in reach on the street.
- **Survival 09:** blood on the street after the kill.
- **Survival 11:** both leaves barred, the "Lift the bar" prompt and its log
  lines.
- **Survival 12:** the spear crafted after one drink.
- **Survival 13:** "Game loaded." at the workbench.
- **Survival 14:** the 4 fibre and 4 planks left on the shelf before the save.
- **Survival 15:** the locker still holding exactly two torches.
- **Creative 08:** an idle Veilbound, with the pick at 300/300.
- **Creative 11:** the keyed lock and its log.

The revisit re-opened containers without a new search. The world reloaded from
the isolated `saves/veylon.sav`; the real save slot was not used.

The production reload regenerates the spawn ring in its fixed ring order, and
that ring holds the whole starter superblock. The native revisit changes the
walking and streaming order only for chunks outside that ring. That the saved
slice does not depend on order comes from the headless five-order matrix.

### Native observations (scripted, not a human playtest)

- **Legibility:** room hints, prompts ("Search cabinet", "Lift the bar",
  "Broken doorway"), container titles, ownership state and the search progress
  bar read well at 1280 × 720.
- **Distances:** refuge to the apartment's courtyard door is about 11 s on foot,
  and to the shop's courtyard door also about 11 s. The whole route walks
  616–671 blocks.
- **Stairs:** the switchback to the first floor was climbed without help.
- **Loot:** generous. Each kitchen holds three berries, water, planks and scrap,
  and every utility locker holds a pry tool.
- **Noise:** rummaging, the forced door and melee drew pursuers on some seeds.
- **Threat:** fair but weak. Melee pain-locks a Veilbound; only seeds 7 and
  −9e18 saw a blow land.
- **Return:** coming back matters for the workbench, bed, storage and barred
  door. But the refuge's own starter containers already fund the spear.
- **Minor presentation issues:**
  - for about half a second after a load the HUD shows "Exposed" until the next
    shelter evaluation;
  - searched furniture still prompts "Search";
  - the forced door shows two near-identical labels;
  - the ground-floor utility room is dark at noon.

D-97 assigns these findings to later packages. Human comfort and listening
remain deferred until after 22.


## Milestone 12 — commercial and civic buildings (2026-10-14 to 2026-10-16)

### Scope and baseline

Baseline: clean `feature/urban-survival` at `daadff0`, the 11 gate met. I read
the full revised base, the prompt, `AGENTS.md`, the commit workflow and these
records. Decisions D-98–D-106. JDK: the repository-local Temurin 25.0.4.1.

Unchanged: the v3 base, every section layout and codec version, content
revision 3 and its tables, serialized enums except the appended `SIGN`, and the
new-world default (generator 3).

### Probes before the catalogue (throwaway, never committed)

- **Parcel supply** over six seeds and six regions: residential regions hold
  about 70–88 residential, 7–22 commercial and one service parcel; commercial
  regions about 48–57 commercial and 14–19 residential. Residential parcels are
  wide shallow strips (frontage 12–32, depth mostly 12–19); commercial and
  service parcels reach 28 deep, with storey envelopes 1–8.
- **Grocer parcel areas** under the final catalogue: about one in ten is under
  320 cells and is named a convenience store.

### Compatibility pins recorded before shared code changed

Generators 8–10 had only relative pins. On an untouched `daadff0` worktree a
probe recorded, for seeds `20260731`, `-4242` and `7`:

- the four terrain chunks;
- every chunk of the refuge and of the first commercial parcel;
- every building plan fingerprint of regions (0, 0), (−1, −1) and (1, −1).

Generators 9 and 10 share their values (D-83). The extended
`WorldGeneratorFingerprintTest` passed there (12 tests) and was committed first
(`8d87857`). Every later commit kept it green.

### Plan evidence

`CivicCatalogueTest`, with `BuildingPlannerTest` and `BuildingFurnishingTest`
narrowed to the walk-up families:

- **Family matrix.** 1,344 proposals: every family × 8 sizes × 4 frontages ×
  4 variants × candidate and minimal, at the family's tallest storeys and with
  a basement where allowed. All pass the production validator with bounded
  cells and transitions, and are pure. `familyMatrix` over 8 sizes, 2 seeds and
  every storey count and basement option returns no failing identity.
- **Region matrix.** All 4,112 parcels of the frozen six seeds × eight regions
  (including the ±4,096-region pairs) plan within the three-attempt fallback and
  validate against their district (approaches, neighbours, halo).
  - Every walk-up equals its template-4 fingerprint.
  - No specialist building is in the starter superblock.
  - Each region has exactly one school, one security post and one warehouse.
  - Families: 2,830 apartments, 6 starter shops, 551 grocers, 226 clinics,
    355 offices, 48 schools, 48 security posts and 48 warehouses; 4 walk-up
    repairs.
- **Secured rooms.**
  - Removing one bar makes the dispensary or armoury reachable, and the
    validator reports "SECURED: room is reachable without forcing its lock".
  - Walling the corridor side reports the forcible door off an ordinary route.
  - Relabelling the room as storage leaves it "ROOM: unreachable".
  - Picked-clean buildings have a broken doorway and validate ordinarily.
- **Loot purpose.** Across three seeds × six families × four variants:
  - the secured table appears only in secured rooms or survivor-held
    containers, and the clinic table only in dispensary cabinets;
  - racks roll the warehouse table, workshops the utility table, grocer
    shelves the grocer table and grocer stockrooms the pantry table;
  - unsecured food, treatment and pry-tool sources each span at least three
    families.
- **Partial occupation, revisions and diagnostics.**
  - A held office owns its street floor and basement only, locks its street
    door, keeps the rear open and has bedrolls.
  - Revisions 1–4 reject civic families, and revision 5 rejects walk-ups.
  - A sealed clinic repairs at attempt 2, with the template, family, variant,
    parcel id and frontage in its repair line.
- **Pins.** The first building of each family for seeds `20260731`, `-4242` and
  `7` (18 fingerprints), recorded at introduction.

### Generated-world, gameplay and save evidence

- **`UrbanCivicGenerationTest`.**
  - Seven complete 16 × 16-chunk regions (each design seed's origin, plus the
    downtown region (1, −1) of `20260731`) match their proposals in every owned
    cell. `inspectGenerated` proves every parcel on the actual blocks.
  - All six families were generated: 468 apartments, 6 shops, 79 grocers,
    33 clinics, 23 offices, 7 schools, 7 security posts and 7 warehouses.
    Nothing rolled loot, touched fixture state or recorded an edit.
  - A seven-order chunk matrix (row, reverse, column, three shuffles, outward
    spiral) around one building per family, for two seeds, gives identical
    blocks, heights, lights and plan fingerprints.
  - The starter superblock's chunks, every walk-up plan and every catalogue-1
    anchor equal generator 10's on all six seeds.
- **`UrbanCivicGameplayTest`** (production commands):
  - F rattles the dispensary. A bare-hand break forces it: both cells broken,
    one `door-forced` event and a recorded edit. Its cabinet rolls the clinic
    table (bandages and only dispensary supplies). Save and load keep the door
    broken and the stock taken.
  - The armoury refuses a bare hand and gives way to a pickaxe; its locker
    rolls the secured table. Creative meets the lock, then removes the door
    silently.
  - The sign prompt reads its plan, and bars read as security bars. The staff
    door is locked while the street door opens with F.
  - A held building's street floor is survivor property and its upper floor
    abandoned, through a save and load.
  - A clinic lobby guard activates through production admission and survives a
    save under catalogue 2.
- **`UrbanCivicSaveTest`.** Generators 10 and 11 write catalogues 1 and 2 with
  codec 1. Each rejects the other's without replacing the live world.
- **`WorldGeneratorFingerprintTest`.** Generator 11's terrain, building chunks
  and plans are pinned at introduction; generators 1–10 are unchanged.
- **`IndoorNavigationTest`** (review fix, D-106). Before the fix, the waypoint
  from beside a clinic's locked staff door was the locked rear approach. After
  it, a Veilbound there reaches the street through the open street door, and an
  outsider is routed to the street door.
- **`UrbanSliceRouteTest`.**
  - The frozen slice route still passes on generator 10, on all six seeds in
    both modes.
  - Extracting `UrbanRouteScene` reproduced milestone 11's recorded Survival
    ranges exactly: 563–657 blocks, 168–188 route seconds, 268–300 game minutes.
  - On generator 11 the route passes on all six seeds in Survival (a permanent
    test) and, in a throwaway probe, in Creative. Some runs now meet a
    specialist building's guard.
- **`UrbanCatalogueQaSceneTest`** plays the catalogue walk headlessly in
  `Game.run` order in both modes and checks the generator guard.

### Native catalogue walk, both modes

Host: Windows 11 Pro 10.0.26200, AMD Ryzen 5 5600X, NVIDIA GeForce RTX 3060 Ti,
driver 610.88, OpenGL 3.3.0 with KHR_debug; 1280 × 720.

Both modes first ran on 2026-10-16, before the routing fix `a3a091e`
(`m12-native-<mode>.log`). They were rerun on 2026-10-18 on the final source,
from fresh directories (`m12-final-native-<mode>.log`):

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_catalogue'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m12-final-catalogue-survival'
.\gradlew.bat run -PveylonDataDir=build/qa/m12-final-catalogue-survival --console=plain
```

The Creative twin used the same command with `creative` in the mode, tag and
directory; the 2026-10-16 runs used `m12-catalogue-<mode>` for both. The
buildings chosen for seed `20260731`:

| Family | Parcel | Variant | Notes |
| --- | --- | --- | --- |
| Grocer | `urban:parcel:-1,0:89` | Damaged | "Calder Grocers", two storeys |
| Clinic | `urban:parcel:0,0:97` | Damaged | "Halvard Clinic & Pharmacy", three storeys |
| School | `urban:parcel:0,0:105` | Damaged | Three storeys and B1 |
| Office block | `urban:parcel:0,-1:107` | Intact | Five storeys |
| Security post | `urban:parcel:0,0:13` | Damaged | Two storeys |
| Warehouse | `urban:parcel:0,0:49` | Picked clean | Two storeys |

All four runs printed `[urban-catalogue] pass=true`, with the same values
before and after the fix:

- seven staged poses;
- the dispensary forced at (45, 41, 199);
- searched stock `{BANDAGE=2, SPLINT=1, ANTISEPTIC=2, MEDICINE=2}`;
- highest floor 4;
- the staff door and the armoury lock refused, each with its prompt;
- no threat engaged, and full health;
- 71 route seconds in Survival and 68 in Creative.

Each run wrote 16 captures. The logs held only the usual MEDIUM
shader-recompile and pixel-transfer notes: zero GL errors, KHR errors or
native-audio errors. The final runs report `glGetError=0`, 19 KHR messages
with 0 errors, and `nativeErrors=0` in each mode; Gradle exited 0 after
1 min 24 s (Survival) and 1 min 16 s (Creative).

Every capture of the 2026-10-16 runs was inspected. Of the final runs, all 16
Survival captures and the Creative sign, forced dispensary, stock screen,
upper floors, armoury lock and rack captures were inspected; they show the
same scenes:

- The sign shows its board, with the prompt `Sign: "Calder Grocers"`.
- The grocer floor shows produce tables and shelves.
- The refused staff door and the armoury lock show their prompts.
- The forced dispensary shows its counter and cabinets beyond a broken doorway,
  and the stock screen is titled "DISPENSARY CABINET".
- The treatment room shows its cabinet, the exam bed and barred plaster walls.
- Classrooms and offices show desk rows on the ground and upper floors.
- The rack capture looks down an aisle between sheet-metal racks to the
  workshop lockers and benches; the workbench prompt is "Use workbench".
- In Creative the dispensary doorway is empty, because a Creative break removes
  the door.

A first run framed three rooms too closely (one shelf, debris in front of the
bed, one rack upright). The views were widened and both modes rerun from fresh
directories.

One climate observation for package 13: the office block stands in the
downtown region (0, −1), whose climate is still the legacy noise biome. Its
captures read "Biome / Rocky Highlands" with the air at about 1 °C (−0.5 °C in
Creative), against Meadow and about 14 °C in the residential regions.

### Review and commands

All Gradle commands used the bundled Temurin 25.0.4.1+1
(`build/tools/jdk25/jdk-25.0.4.1+1`) and the Windows wrapper. Logs are local
ignored artifacts under `build/`.

An adversarial review covered five dimensions (plans and validator,
compatibility and saves, gameplay and runtime, the QA refactor, tests), with
three independent skeptics per finding:

- **Plans and validator:** no finding.
- **Compatibility and saves:** one claim, refuted by all three skeptics: a held
  civic building admits no encounter anywhere inside, by design (D-101).
- **Gameplay and runtime:** two claims.
  - Guards were routed to a clinic's locked staff door and stalled there. Its
    three skeptics stopped on a usage limit, so it was checked directly: a new
    `IndoorNavigationTest` case failed before the change and passes after it.
    `a3a091e` fixes it (D-106), and a second pass after the fix refuted it.
  - The secured-room prompt's "another way in" was refuted: bars and concrete
    can be broken.
- **QA refactor and tests:** both reviewers stopped on the same usage limit on
  2026-10-16, and a rerun stopped on it again on 2026-10-18. On 2026-10-19 the
  two dimensions were reviewed directly against `a3a091e`, by one reviewer
  without independent skeptics. No defect was found:
  - Every line removed from `UrbanSliceQaScene` reappears in `UrbanRouteScene`
    apart from visibility and the scene tag. The log prefix, failure text,
    deadline, the forced-door capture order and the first-threat capture are
    unchanged.
  - Each staged pose of the catalogue walk releases held keys and clears fight
    and approach state. Its lock steps assert the door stays shut with a
    "Locked." prompt, and its search asserts the ledger reveal. The rattle the
    lock step's comment mentions is asserted by `UrbanCivicGameplayTest`.
  - The catalogue, generation and save tests end their loops in per-family,
    per-region and count assertions; none is vacuous.
  - Two notes for package 13. The walk searches only the regions next to the
    spawn and requires the civic catalogue. `LootTables.table` maps a civic
    district's bathroom cabinets and shop shelves to the clinic table, which
    generator 11 never reaches but a civic district will.

The final portable build ran on 2026-10-16 at `a3a091e`, with only these
records modified in the working tree: `.\gradlew.bat build --console=plain`
passed in 11 min with 1,820 tests in 200 classes, zero failures or errors, and
the existing Windows symlink-privilege skip in `AppPathsTest`
(`m12-build-final.log`). Production source has not changed since; the
2026-10-18 native reruns used the same tree.

Each code commit was checked against its exact tree, in the detached worktree
`build/m12-check` or in a working tree that held only that change, as its
message records:

| Commit | Tests | Notes |
| --- | --- | --- |
| `8d87857` | 12 | Values recorded on an untouched `daadff0` worktree |
| `880602f` | 39 | Plus Javadoc |
| `7046b3b` | 38 | Plus Javadoc |
| `82e5594` | 100 | Plus Javadoc |
| `6e635d5` | 402 in 75 classes | Plus Javadoc |
| `e8dd714` | 5 | Reproduced milestone 11's Survival ranges |
| `b82fa31` | 26 | Plus the first native runs in both modes |
| `76dd7d3` | 4, then 40 | Code matched `HEAD` apart from its two test files |
| `a3a091e` | 313 in 53 classes | The new case failed before the change |

`graphify update . --no-cluster` ran once on the final code on 2026-10-18 and
produced 8,158 nodes and 31,711 edges (`m12-graph-update.log`), with the
existing Gradle-file extractor warnings. No clustering or semantic extraction
ran.

### Limits and next gate

- The native walk stages poses between buildings and a starting spear and pry
  tool. It is scripted evidence, not the human playtest deferred by D-80.
- Offices and classrooms look alike apart from cabinets and signs (22).
- Distribution by district (13), final stock and guard pressure (23), taller
  offices (24) and a locked starter flat (18) belong to later packages.

Next: package 13.

## Milestone 13 — urban biome differentiation (2026-10-16 to 2026-10-19)

### Scope and baseline

- Branch `feature/urban-survival` at `6fd26c9` (the 12 records), clean tree.
  Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records.
- Generator 12 (layout 9, template 6, encounter catalogue 3) is new; content
  revision 3, every section layout and codec version and the default new world
  are unchanged. One block id was appended (`CONTAMINATED_GROUND`, 83).
- Generators 8–11 already had absolute pins (12). Generator 12 was pinned at
  introduction, including the first region of every district near the spawn.

### Plan, generation, gameplay and save evidence

Each code commit ran its tests against its exact tree (the detached worktree
`build/m13-check` with the tree hash compared to the index, or a working tree
that held only that change). Its message records the classes:

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `ba84727` | 42 tests, compile and Javadoc; with one unused class beside it, all 160 `world.*` tests in 27 classes | `DistrictPlannerTest`: the district guarantees on 300 seeds, far-field weights, zero diagnostics and mirrored seams on every region within three of the spawn and two far ones for six seeds, layout-2 identity beneath the overlays, flooding, cordons, yards, and mutations that the diagnostics must catch |
| `3dd0d36` | 63 tests, compile and Javadoc | `ContaminatedGroundTest` and its `CreativeHazardsTest` twin; `SerializedEnumOrderTest`, `UrbanMaterialsTest` (110 of 160 layers), `PlayerOutcomeDeterminismTest`, fingerprints |
| `f987887` | 46 tests, compile and Javadoc | `DistrictCatalogueTest`: every parcel of one region per district for three seeds validates in its district; each district's mix; at least two unsecured source types for food, water, treatment, light and pry tools in every district; all six families around every design spawn; starter plans unchanged |
| `8017601` | 102 tests in 19 classes, compile and Javadoc; before staging, 516 tests in 90 classes | `UrbanDistrictGenerationTest`: six complete generated regions, 458 buildings passing the production entry and exit proof among 19,083 overlay cells; flood water and canals unmoved by a full `WaterSystem` run; seven chunk orders at a cordon corner and a canal; budgets and starter slots. `UrbanDistrictSaveTest`: catalogue-3 saves, rejections across generators 10–12, overlay edits that persist. The slice loop on generator 12 for all six seeds; the catalogue walk on generator 12 |
| `da8ff47` | 126 tests in 13 classes, compile and Javadoc | `UrbanDistrictPresenceTest` (arrival lines on real transitions only, district climates, toxic fog harmless outside and harmful inside the cordon, other worlds unchanged), `EventRollLadderTest`, `PlayerOutcomeDeterminismTest`, HUD and audio tests |

The district tour and the boundary-avenue fix are recorded below.

### Native district tour, both modes

Host: Windows 11 Pro 10.0.26200, AMD Ryzen 5 5600X, NVIDIA GeForce RTX 3060 Ti,
driver 610.88, OpenGL 3.3.0 with KHR_debug; 1280 × 720.

A first Survival run on 2026-10-19 (`m13-native-survival-first.log`) passed
but framed four stops poorly: a shop-row alley instead of a plaza, a keg seen
from beside it, and the civic and quarantine checkpoints at an angle. The tour
was changed to look through gateways and square-on at checkpoints, and the
rerun (`m13-native-survival.log`) passed. After a cleanup of the tour planner
that kept every stop, both modes ran again from fresh directories
(`m13-final-native-<mode>.log`):

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_districts'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m13-final-districts-survival'
.\gradlew.bat run -PveylonDataDir=build/qa/m13-final-districts-survival --console=plain
```

The Creative twin used `creative` in the mode, tag and directory. Seed
`20260731` gives:

| Stop | Stand | Subject | View |
| --- | --- | --- | --- |
| Transition | (216, 41, 128) → (296, 41, 128) | — | Residential region (0, 0) to commercial (1, 0), walked with W and mouse look |
| Residential | (144, 41, 103) | (147, 41, 106) | Tall grass and bushes on a courtyard lawn by a walk-up door |
| Commercial | (−94, 41, 194) | (−94, 41, 201) | Across a tree-lined plaza from its edge |
| Industrial | (31, 41, −449) | (31, 41, −439) | Through a fenced yard's gateway at its contaminated patches |
| Flooded | (−383, 41, −641) | (−383, 41, −638) | A plank duckboard over a flooded street, more water beyond |
| Civic | (286, 41, 577) | (286, 41, 587) | Through a checkpoint into the fenced, tree-planted square |
| Quarantine | (−701, 41, −251) | (−701, 41, −256) | Square-on to a cordon checkpoint where a local street crosses, from inside |

Both runs printed `[urban-districts] pass=true` with 15 staged poses, 16
captures, the arrival line "Entering Commercial Downtown. Downtown: grocers,
offices and a police post along long, open avenues. More infected, and they see
you coming.", no threat engaged, full health and 42 route seconds. The wade
reported `inWater=true` with wetness 1.00 in Survival and 0.00 in Creative,
where the player stays dry. Every `views` entry named the expected district
and climate: Meadow for residential, commercial and civic, Dry Scrubland for
industrial, Wet Marsh for flooded and Rocky Highlands for quarantine. Each log
reports `glGetError=0`, 20 KHR messages with 0 errors (the usual MEDIUM
pixel-transfer and shader-recompile notes) and `nativeErrors=0`; Gradle exited
0 after 44 s in each mode. These runs used the code committed as `6ab1f49`.

After the boundary-avenue fix (`dbd0746`, below), both modes ran once more
from fresh directories with the tag and directory `m13-head-districts-<mode>`
(`m13-head-native-<mode>.log`). They passed with the same stops, poses,
captures, wade, health, route seconds and diagnostics (20 KHR messages, 0
errors, `nativeErrors=0`), except that the arrival line is now logged at
12:18 instead of 12:16, once the walk has left the avenue. The Survival
transition capture shows the line in the log panel under `District /
Commercial Downtown`.

Inspected captures: from the second Survival run the transition pair, day
industrial, flooded, civic and quarantine, night flooded and fog quarantine;
from the final runs Survival day residential and commercial and night
industrial, and Creative fog flooded. They show:

- `District / <name>` on the Survival HUD in each, and the district on the
  Creative panel's terrain row.
- A residential street, then a downtown street with the arrival line logged.
- Tall grass on the residential lawn; sheet-metal fences with open gateways at
  the yard and the civic square; dark contaminated patches in the yard; plank
  duckboards over standing water; the cordon fence beside the quarantine
  checkpoint.
- Dark night views that keep the fences and duckboards readable, and fog that
  closes in at the flooded and quarantine stops.

Two observations, neither a tour failure:

- The HUD's air temperature is lower at noon than at 23:00 in every district
  (industrial 20.2 °C at 12:38 and 31.8 °C at 23:06; flooded 7.1 °C and
  18.7 °C). `TimeSystem.tempOffset` has peaked at 02:00 rather than at
  `WARMEST_HOUR` since the initial commit, in every world. Recorded as owner
  question P-15 and left unchanged; the district climates are right relative
  to each other.
- No inspected quarantine capture shows contaminated ground in frame; the yard
  captures do. `UrbanDistrictGenerationTest` checks that every planned
  contamination cell, the quarantine's included, generates as contaminated
  ground.

### Review and the boundary-avenue fix

A direct review of the shared-code changes (world, generator, events, player,
environment and HUD) found one defect: the arrival line was logged on every
crossing of a district boundary. Every district boundary runs down an avenue's
centre line, so drifting along an avenue between two districts logged an
arrival each time. The fix keeps the HUD, fog and toxic-fog district exact and
announces a new district only once the player stands off the boundary avenue
on its side. `UrbanDistrictPresenceTest` drifts four times across the centre
line of an avenue between two districts without a line, then steps off on
each side for exactly one line each. With the fix set aside the new case
failed at its first drift assertion (`m13-fix-before.log`); with it, Javadoc
and 74 tests in 10 classes passed (`m13-fix-focused.log`):
`UrbanDistrictPresenceTest`, `UrbanDistrictQaSceneTest`, `CityPlannerTest`,
`DistrictPlannerTest`, `UrbanDistrictGenerationTest`, `UrbanSliceRouteTest`,
`GameLoopIntegrationTest`, `HudQaSceneTest`, `CreativeHazardsTest` and
`PlayerOutcomeDeterminismTest`. No reviewer subagents were run; the planner,
overlay, catalogue and encounter code was not re-reviewed line by line and
rests on its tests and the commits' own checks.

### Commands and portable builds

All Gradle commands used the bundled Temurin 25.0.4.1+1
(`build/tools/jdk25/jdk-25.0.4.1+1`) and the Windows wrapper. Logs are local
ignored artifacts under `build/`.

- `.\gradlew.bat build --console=plain` on 2026-10-19 with the tour's code
  (committed as `6ab1f49`) and only these records edited beside it: passed in
  9 min 25 s, 1,851 tests in 207 classes, zero failures or errors, and the
  existing Windows symlink-privilege skip in `AppPathsTest`
  (`m13-build-final.log`).
- The same command at `dbd0746`, the final code, with only these records
  edited: passed in 9 min 24 s, 1,852 tests in 207 classes, zero failures or
  errors, the same skip (`m13-build-head.log`). Javadoc was up to date from
  the focused run on that source, where it passed.
- `UrbanDistrictQaSceneTest` alone after the tour cleanup: 3 tests passed
  (`m13-step7-iter.log`).
- `graphify update . --no-cluster` once on the final code: 8,368 nodes and
  32,739 edges, with the existing `build.gradle` extractor warning
  (`m13-graph-update.log`). No clustering or semantic extraction ran.

### Limits and next gate

- The tour stages poses between stops, the clock, the weather and a spear. It
  is scripted evidence, not the human playtest deferred by D-80.
- Every district's threat is still the Veilbound (D-113); alien organisms and
  their props are package 15. Map colouring of districts is package 21;
  district ambience and sound are package 22; stock, budgets and starter-slot
  density are package 23.
- Clustering of like districts beyond ring three is left to tuning (D-108).

Next: package 14.

## Milestone 14 — basements, transit and rooftop routes (2026-10-19 to 2026-10-21)

### Scope and baseline

- Branch `feature/urban-survival` at `b7c3e80` (the 13 records), clean tree.
  Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records.
- Generator 13 (layout 10, template 7, encounter catalogue 4) is new. Content
  revision 3, every existing section layout and codec version and the default
  new world are unchanged. One section is new (`urban.discovery` v1) and three
  block ids were appended (84–86).
- Generators 1–12 keep their absolute pins. Generator 13 was pinned at
  introduction for terrain, plans and routes; its seed-7 terrain pin was
  re-recorded once, the same day, by `bd8fd6a` (D-118).

### Plan, generation, gameplay and save evidence

Each code commit ran its tests against its exact tree: a detached worktree in
`build/m14-check` whose tree hash matched the index, or a working tree that
held only that change. Its message records the classes:

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `9999bdc` | 50 tests, compile and Javadoc | `UrbanLoosePanelTest`: a wall until slid aside; no route or resident through a closed panel; forcing and salvage; open state across a save and a load. `UrbanMaterialsTest` (camouflage, 114 of 160 layers), `SerializedEnumOrderTest`, `CreativePaletteTest`, fixture tests |
| `da30934` | 107 tests in 16 classes, Javadoc | `TransitNetworkTest`: one owned segment per region; both sides plan the same segment; mirrored cross-sections; validated stations inside the owner's band and clear of parcels for six seeds; the spawn station; the guarded stair; the proof catching an eight-block drop and a blocked stair; junction union; flooded tracks with dry platforms |
| `00ee530` | 378 tests in 66 classes, Javadoc | `RouteCatalogueTest` over three seeds and a region of every district: 1,428 buildings validate; the starter superblock matches template 6; apartments always have basements; paired, correctly locked service doors over passages that cut no parcel; a sealed cellar only its panel opens; guarded roofs with bulkheads; 295 fire escapes and 169 connectors (45 bridges), declared by every building they touch |
| `04ceb6e` | 27 tests, Javadoc | No cell a body reaches from the entrances of any template-7 building, in a region of every district, stands beside an unguarded drop apart from declared route heads |
| `91d058e` | Full portable build: 1,881 tests in 212 classes, one existing skip | `UrbanUndergroundGenerationTest`: the spawn region's transit cells equal their plan; no parcel's own cells change; every building passes the production entry and exit proof in the generated world, and the spawn station passes its proof there; the route proof walks street → basement → service passage → back, roof to roof over every connector and bridge, and every fire escape, with no unguarded edge; the tunnel is walkable along the whole segment and both halves agree across the region boundary; seven chunk orders at the spawn station and a junction; flood water unmoved. `UrbanUndergroundSaveTest`: a walled-up passage, a dug platform and an opened station door survive a save and a load. The slice loop on generator 13 for all six seeds |
| `aef00bf` | 507 tests in 90 classes, Javadoc | `UrbanDiscoveryTest` (a station found only by standing in it; a cellar found by its panel, not by walking past; other places named without recording; earlier worlds unchanged), `UrbanDiscoverySaveTest` (round trip, empty and absent sections, six kinds of damage and a foreign section refused with the live world kept), `UrbanUndergroundEnvironmentTest` (stations and tunnels dark, roofed and shallow-underground in sound; a basement sheltered; a roof open; the bulkhead keeping rain off the stair head) |
| `d59dc02` | 45 tests, Javadoc | `UrbanStationTest`: a station locker resolves to the utility table, opens as a maintenance locker and keeps its contents across a save and a load |
| `1ddcd8d` | 514 tests in 92 classes, Javadoc | `UrbanStationTest`: every anchor of 25 regions around the spawn for three seeds resolves and stays within its district maximum with the station's share; a Veilbound activates on a station platform and survives a save and a load under catalogue 4. The slice loop on generator 13 for all six seeds |
| `bd8fd6a` | 43 tests, Javadoc | The fire-escape leg reworked: the ladder leads up to the roof and the stairs down to the street, with no unguarded edge; fingerprints with the re-recorded seed-7 terrain pin; transit, route catalogue, slice and save classes |
| `38e1f84` | Full portable build: 1,896 tests in 217 classes, one existing skip | `UrbanUndergroundQaSceneTest` plays the tour headlessly in both modes and refuses an older generator |
| `747932e` | `RuntimeBoundsTest`, 9 tests; then the full portable build: 1,897 tests in 217 classes, one existing skip | `RuntimeBoundsTest`: both route caches, driven past their caps by pure plan queries, stop at 80 together with the snapshot within its hard limits at every step and no chunk generated. With the transit cache's capacity doubled, the case failed at its first hard-limit check (`m14-snap-mutant.log`) |

### Native route tour, both modes

Host: Windows 11 Pro 10.0.26200, AMD Ryzen 5 5600X, NVIDIA GeForce RTX 3060 Ti,
driver 610.88, OpenGL 3.3.0 with KHR_debug; 1280 × 720.

Walking the tour down a fire escape during the ladder work (2026-10-19) found that the
controller cannot climb down a rope ladder; that led to `bd8fd6a` (D-123).
A Survival run on 2026-10-21 (`m14-native-survival.log`, tag
`m14-underground-survival`) then passed, with two framing faults: the mid-climb
capture showed only the wall, and the station's tunnel capture was black. The
tour was changed to look up the ladder while climbing and to place a second
torch on the track. Both modes then ran from fresh directories
(`m14-native-s2.log`, `m14-native-c2.log`):

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_underground'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m14-underground-survival2'
.\gradlew.bat run -PveylonDataDir=build/qa/m14-underground-survival2 --console=plain
```

The Creative twin used `creative` in the mode and `m14-underground-creative2`
as the tag and directory. Seed `20260731` gives these legs (from the `plan`
line):

| Leg | Cells | Walked |
| --- | --- | --- |
| Service passage | street (14, 41, 56) → basement door (23, 37, 47) → service door (24, 37, 47) → corridor (26, 37, 47) → street (41, 41, 56) | W through both basements and the passage, F at doors |
| Hidden cellar | hall (42, 37, 45), panel (43, 37, 45), room (44, 37, 45) | F slides the panel aside; W into the cellar |
| Roof | street (79, 41, 194) → roof (79, 61, 208) → over a bridge → roof (110, 61, 208) | W up the stairs, through the bulkhead door, across the bridge |
| Fire escape | foot (29, 41, 18) → gap (29, 53, 17) → roof (29, 53, 16) → street (18, 41, 5) | W held against the wall up the ladder, then down by the stairs |
| Station, 23:00 | street (3, 41, 150) → platform (3, 34, 136) → room (4, 34, 129) → tunnel (1, 33, 121) | W down the stair; torches placed with a right click on the platform and the track |
| Flooded tunnel | (−452, 33, −1023), Flooded Quarter | Standing in the water |

Both runs printed `[urban-underground] pass=true` with 6 staged poses, 16
captures, 2 sites discovered (the cellar and the spawn station), no threat
engaged, full health and 79 route seconds. Each `views` entry named the
expected place: `Basement`, `Service passage`, `Hidden cellar`, `Rooftop`,
`Transit station`, `Transit tunnel`, and the bare district on the street. The
flood leg reported `inWater` with wetness 1.0 in Survival and 0.0 in Creative,
where the player stays dry. The event log showed "The loose panel slides aside.
A hidden cellar. Someone has been sheltering here." and "An abandoned transit
station. …". Each log reports `glGetError=0`, 20 KHR messages with 0 errors (the
usual MEDIUM pixel-transfer and shader-recompile notes) and `nativeErrors=0`;
Gradle exited 0 after 1 min 21 s (Survival) and 1 min 22 s (Creative). These
runs used the tour committed as `38e1f84`, before a Javadoc sentence changed and
its scene label moved to another line of `QaHarness`'s case list.

Inspected captures, from the final Survival run unless noted:

- `03`: a two-wide brick passage under the courtyard, HUD `Service passage`.
- `05`: the plaster panel with its seam and the drag-marked debris at its foot;
  the prompt reads "Slide the loose panel aside (crouch: bar it from this
  side)". `06` (Creative): inside the cellar, looking back through the open panel at the debris in the hall.
- `08` (Creative): parapets with guard rails, the bridge's rails and the
  bulkhead on the next roof.
- `09`: looking up the rope ladder from halfway, the rail cage at its head
  against the sky. `10`: from the roof, the parapet gap and the caged ladder
  head, the courtyard below.
- `12`: the station stair from the sidewalk at night, closed by rails on three
  sides. `13`: the platform almost black. `14`: the platform and the service
  door lit by the placed torch. `15`: the tunnel's walls and track lit
  by the track torch, darkness beyond.
- `16`: black apart from the held torch and the prompt "Fill waterskin
  (untreated water)", with wetness 100 % on the HUD: an unlit flooded tunnel.

Observations, none a tour failure:

- `11` frames the building's brick wall from below rather than the parapet gap.
  The HUD shows the player back on the street, which is what the leg checks.
- The held torch does not light terrain (as recorded for package 06); only
  placed torches do, which is the cost of darkness the tour shows.
- P-15 is still visible: 9.1 °C on the street at 12:42 and 20.7 °C on the
  street at 23:02.

### Commands and portable builds

All Gradle commands used the bundled Temurin 25.0.4.1+1
(`build/tools/jdk25/jdk-25.0.4.1+1`) and the Windows wrapper. Logs are local
ignored artifacts under `build/`.

- `.\gradlew.bat test --tests "com.veylon.UrbanUndergroundQaSceneTest"` after
  the tour changes: 3 tests passed, routeSeconds 79 in both modes
  (`m14-g5.log`).
- `.\gradlew.bat build --console=plain` on 2026-10-21 with the tour's code:
  passed in 9 min 52 s, 1,896 tests in 217 classes, zero failures or errors and
  the existing Windows symlink-privilege skip in `AppPathsTest`
  (`m14-build-final.log`). The scene label then moved to an existing line of
  `QaHarness` to keep it at 1,496 lines, and the same command passed again on
  that tree, committed as `38e1f84`, in 10 min 2 s with the same 1,896 tests,
  zero failures or errors and the same skip (`m14-build-head.log`).
- `.\gradlew.bat test --tests "com.veylon.qa.RuntimeBoundsTest"` with the
  route-cache case: 9 tests passed (`m14-snap-focused.log`); with the transit
  cache's capacity doubled, the new case failed (`m14-snap-mutant.log`), and
  the code was restored. Then `.\gradlew.bat build --console=plain` on the final
  code, committed as `747932e`: passed in 9 min 50 s, 1,897 tests in 217
  classes, zero failures or errors and the same skip (`m14-build-snap.log`).
- `graphify update . --no-cluster` once on the final code: 8,745 nodes and
  34,459 edges, with the existing `build.gradle` extractor warning
  (`m14-graph-update.log`). No
  clustering or semantic extraction ran.

### Limits and next gate

- The tour stages poses between legs, the clock, the weather and a kit; it is
  scripted evidence, not the human playtest deferred by D-80. Low light and
  flood were walked by the script, not judged by a person.
- Fire escapes are ascent routes only (D-123).
- The hidden cellar is an empty reservation for 16; its per-region guarantee
  is shown on the tested seeds (D-122).
- Station slots hold the Veilbound (package 15). Map presentation of finds is
  package 21; underground ambience is package 22; route and flood rates are
  package 23.
- No reviewer subagents ran. The commits rest on their own exact-tree checks,
  the final build and the native tours.

Next: package 15.

## Milestone 15 — alien ecology and urban wildlife (2026-10-21 to 2026-10-22)

### Scope and baseline

- Branch `feature/urban-survival` at `9e2d3b0` (the 14 records), clean tree.
  Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records.
- Generator 14 (layout 11, template 7, encounter catalogue 5) is new, with
  `urban.population` codec 2 under catalogue 5. Content revision 3, every
  section id and the default new world are unchanged. Two block ids (87–88),
  one item, one creature type and body family, and one archetype were
  appended.
- Generators 1–13 keep their absolute pins. Generator 14 was pinned at
  introduction for terrain, its ecology plans and catalogue 5's anchors
  (D-129).

### Behaviour, generation, population and save evidence

Each code commit ran its checks against its exact tree in a detached
worktree (`build/m14-check`) whose tree hash matched the index. Its message
records the classes:

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `2855198` | 62 tests in 11 classes, compile and Javadoc | The appended blocks and item hold their ordinals (`SerializedEnumOrderTest`); the materials fit (118 of 160 layers, `UrbanMaterialsTest`); visual ids, the Creative palette and catalogue (both blocks excluded with reasons), Creative building, mode routing and the fire and simulation contracts are unchanged |
| `1f84c16` | Full portable build: 1,958 tests in 219 classes, one existing skip | `VeilmawBehaviorTest`: a crouched player in the dark crosses its ground unheard; footsteps wake it with a telegraph before the lunge; backing out of reach means no lunge; a lit player in sight wakes it but daylight does not; a torch on its ground lures it and is smothered; a flame beside the node burns it and the Veilmaw withers into harvestable remains; breaking the node gives a sac and withers it; it never leaves its territory; Creative silences its senses and cancels a telegraph aimed at the player; its strike goes through the damage and affliction gates; it heals at its node and goes home badly hurt. `EcologySoundsTest`; the all-living species matrix with the Veilmaw |
| `2da5df8` | 246 tests in 36 classes, compile and Javadoc | `BreacherBehaviorTest`: blind to a silent player in plain sight but it hears footsteps; it hammers a locked wooden door down with blows the others hear while a Veilbound waits; a locked metal door holds; its blow goes through the damage gates and Creative forgets it at once. Every `ai` test, the infected, fixture, loose-panel, discovery and encounter-population tests, traversal and fixture-state saves, settlement activation, the all-living matrix, the cue banks, enum order and runtime bounds |
| `83cd7d4` | Full portable build: 1,970 tests in 222 classes, one existing skip | `EcologySitesTest`: growth only in quarantine regions, on open lawn or square two cells clear of every parcel and off every walkway, path and tree bed, the node on its carpet with the fold cell beside it; plans identical in any query order and cache history, and catalogue 4 never resolving an ecology id. `UrbanEcologyGenerationTest`: the node and carpet stamped from the plan in four chunk orders, the courtyard still walked round the node, and generator 13 without growth. The generator-14 terrain and plan pins, dispatch, revision and selector naming generator 14, and every earlier pin |
| `c8b65ec` | Full portable build: 1,986 tests in 224 classes, one existing skip | `UrbanEcologyPopulationTest`: a growth site admits its Veilmaw folded beside its node with its territory; every kind keeps its own cap and the scan stays bounded; dormancy keeps injuries, death is permanent and nothing replaces it; a Veilmaw whose node is gone withers and never returns, active or not; wildlife keeps to its habitat and hunting it is finite; the Breacher appears from its planned street slot and Creative pauses every kind. `UrbanEcologySaveTest`: every species keeps its injuries, bindings and tombstones across repeated loads; a burnt node keeps its Veilmaw dead whether it burnt while active or before it was seen; malformed version-two data fails the whole load and keeps the live world; earlier catalogues keep version one and reject version two; a version-one row never names a new species. `EcologySitesTest` (wildlife habitats and roof flocks outside the quarantine and starter; Breachers only at street doors; the quarantine budget counting its Veilmaws), `RuntimeBoundsTest` (the ecology plan cache and city creatures stop at their caps), the generator-14 anchor pin, and the slice loop on generator 14 for all six frozen seeds |
| `76d601b` | 36 tests in 9 classes, compile and Javadoc | `MembraneSalveTest`: two poultices from a sac and a fibre by hand in generator 14, and earlier urban worlds keep their fifteen recipes without it. Every item test, the urban starter progression, treatment and crafting tests |
| `d02462b` | Full portable build: 1,991 tests in 226 classes, one existing skip | `UrbanEcologyQaSceneTest` plays the tour headlessly in Survival and Creative and refuses an older generator. This tree is the final code; the final native runs below used it |

### Native ecology tour, both modes

Host: Windows 11 Pro 10.0.26200, AMD Ryzen 5 5600X, NVIDIA GeForce RTX 3060 Ti,
driver 610.88, OpenGL 3.3.0 with KHR_debug; 1280 × 720.

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_ecology'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m15-ecology-survival-3'
.\gradlew.bat run -PveylonDataDir=build/qa/m15-ecology-survival-3 --console=plain
```

The runs used the same variables from Git Bash. The Creative twin used
`creative` and the tag and directory `m15-ecology-creative-3`. Seed
`20260731` gives these legs (from the `plan` line):

| Leg | Cells | Played |
| --- | --- | --- |
| First growth site, 11:00 | node (−547, 41, −32), fold cell (−547, 41, −31), carpet of 29 cells; stand (−557, 41, −38); crouch cell (−550, 41, −31); torch floor (−548, 40, −32) | W crouched on a route kept 2.6 blocks from the fold cell; S uncrouched (Survival) or W beside the body (Creative); a right click; F; F5; F9 |
| Second growth site, 23:00 | node (−673, 41, 220), carpet of 37 cells; lure stand (−663, 41, 217), just beyond the territory; lure floor (−667, 40, 218) | a right click |
| Park, 10:00 | three Murkhares, a Glowdeer and two Skitterwings; a hare at (67, 41, −152), stand (67, 41, −161) on the park path | W crouched to 4.7 blocks of the hare, then standing (Survival); W to 3 blocks (Creative) |
| Roof flock | two Skitterwings over a walk-up, (75, 65, 143); its roof (76, 61, 150) | staged pose on the roof |
| Breacher | street slot (199, 41, −419) in the Industrial Yards; stand (199, 41, −424) | crouched and still |

Both runs printed `[urban-ecology] pass=true` with 5 staged poses, 11 bodies
admitted through the production gate, 16 captures, no infected engaged, full
health at the end and two membrane sacs. Survival: `lunged=true struck=0.0`
(the Veilmaw lunged at the heard step and missed the backing player) in 73
route seconds; Creative: `lunged=false` in 58. Each log reports
`glGetError=0`, 19 KHR messages with 0 errors (the usual MEDIUM
pixel-transfer and shader-recompile notes) and `nativeErrors=0`; Gradle exited
0 after 1 min 19 s (Survival) and 1 min 5 s (Creative). The `views` lines name
"District / Quarantine Zone / Veil growth" inside each territory and the bare
district outside it, "Reclaimed Residential" in the park, "Rooftop" on the
roof and "Industrial Yards" at the Breacher.

Inspected captures, from the final Survival run unless noted:

- `01`: the node, a purple block veined in pale green, on its dark membrane
  carpet in a quarantine courtyard, the folded Veilmaw a low shape beside it.
- `02`: crouched on the membrane three blocks away, HUD "Veil growth", the
  prompt "Veil membrane: it wakes to footsteps and light"; the body lies flat
  beside the node, hood down. `03` (Creative): standing beside it; it stays
  folded.
- `03`: two thirds through the telegraph, the hood raised with its veins lit,
  beside the node; the log reads "The membrane ripples: something on it is
  waking." `04`: recoiling, exposed, 7.2 blocks from the player after its
  lunge fell short.
- `05`: the torch beside the node and the node smoking; the Veilmaw, hood up,
  shying from the fire; the prompt names its fresh tracks.
- `06`, `07`: the node burnt to ash, smoke over the carpet, and the withered
  remains beside it ("The Veilmaw convulses and withers with its node").
- `08`: the remains up close, "Veilmaw remains (nothing left to take)", two
  membrane sacs in the hotbar and "You cut 2 membrane sacs from the Veilmaw
  remains." `09`: after F5 and F9, the charred node cell and the torch, "Game
  loaded." in the log.
- `10`: the second site at 23:00, the node's veins glowing. `11`: the
  Veilmaw creeping, hood raised, toward the torch set inside its ground.
  `12`: the torch gone and the body recoiling, "The Veilmaw smothers the torch
  under its hood."
- `13`: the park, the Glowdeer under the trees. `14`: a Murkhare in the grass
  as it bolts; Creative `14`: a hare grazing beside the unseen player.
- `15`: a Skitterwing against the sky above the roof's guard rail, HUD
  "Rooftop". `16`: the Breacher at its street door, face sealed, hump and ram
  visible, five blocks from the crouched player.

Earlier native runs (`m15-native-survival.log`, `m15-native-creative.log`,
then `-2`) passed but showed framing faults, each fixed in the tour, not the
game: the telegraph was captured as it began, before the hood rose, with the
node partly in front of the body; the hares were lost in tall grass at
eleven blocks; and the roof flock could not be seen from any street, since
the walk-up hides its own roof. The tour now captures two thirds through the
windup from a crouch cell beside the fold cell, creeps up on a hare to just
outside a crouched player's spook distance before standing, and stands on
the walk-up's roof.

Observations, none a tour failure:

- The quarantine's air read −2.5 °C at 11:13 (Rocky Highlands; P-15 still
  inverts the day curve), and the player's breath shows as a white puff.
- The membrane fire spread to 20–23 cells of carpet before burning out; the
  harvest waited for the fire near the remains to go out.
- The Veilmaw leaves the generic creature tracks on its membrane, which the
  prompt names. Harmless, and left as it is.

### Commands and portable builds

All Gradle commands used the bundled Temurin 25.0.4.1+1
(`build/tools/jdk25/jdk-25.0.4.1+1`) and the Windows wrapper. Logs are local
ignored artifacts under `build/`.

- `.\gradlew.bat build --console=plain` on the working tree on 2026-10-21,
  before the tour's framing changes and the last tests were added: passed in
  11 min 55 s, 1,986 tests in 224 classes, zero failures or errors and the
  existing Windows symlink-privilege skip in `AppPathsTest` (`m15-build.log`).
- `.\gradlew.bat test --tests "com.veylon.UrbanEcologyQaSceneTest"` after each
  tour change: the first runs found an empty plan (stand sight aimed inside the
  node block), a Creative route out of the Veilmaw's reach that could not start
  beside it, a check that ran after the node had burnt out, a hare bolt read at
  one instant, the smother line withheld from a Creative player (D-138), and
  no street cell with a view of a roof flock over its walk-up; then 3 tests
  passed in both modes (`m15-tour2.log` to `m15-tour5.log`).
- `MembraneSalveTest` and the generator-14 slice loop: 3 tests passed
  (`m15-focused-salve-slice.log`). `WorldGeneratorFingerprintTest` with the
  split generator-14 pins: 17 tests passed (`m15-tour5.log`).
- Each commit's exact-tree checks, in the worktree: `m15-c1-check.log`,
  `m15-c2-build.log`, `m15-c3-check.log`, `m15-c4-build.log`,
  `m15-c5-build.log`, `m15-c6-check.log` and `m15-c7-build.log` (the table
  above).
- The final code is `d02462b`'s tree. Its exact-tree build passed in 12 min
  6 s: 1,991 tests in 226 classes, zero failures or errors and the same skip
  (`m15-c7-build.log`).
- Native runs: `m15-native-survival.log`, `m15-native-creative.log` and
  `-2` (framing faults, see above), then `m15-native-survival-3.log` and
  `m15-native-creative-3.log` (final).
- `graphify update . --no-cluster` once on the final code: 9,123 nodes and
  36,226 edges, with the existing `build.gradle` extractor warning
  (`m15-graph-update.log`). No clustering or semantic extraction ran.

### Limits and next gate

- The tour stages admission through the production gate, poses between legs,
  the clock, the weather, the fire stream's seed and a kit; it is scripted
  evidence, not the human playtest deferred by D-80. Whether the telegraph
  reads in time, the glow at night and the cues were checked by script and
  captures, not judged by a person.
- The Veilmaw never pathfinds (D-131). Balance values (strike, sickness roll,
  windup, lure light, Breacher density, wildlife counts, the salve) are
  initial values for 23.
- Map presentation of growth and wildlife is package 21; an ambience bed for
  growth is 22.
- No reviewer subagents ran. The commits rest on their exact-tree checks, the
  final build and the native tours.

Next: package 16.

## Milestone 16 — survivor camps and hidden civilians (2026-10-22)

### Scope and baseline

- Branch `feature/urban-survival` at `c5b1782` (the 15 records), clean tree.
  Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records.
- Generator 15 (layout 12, template 7, content 3, encounter catalogue 5) is new,
  with the section `urban.sites` v1. No block, item, enum ordinal, persisted id
  or existing section changed; the default new world is unchanged.
- Generators 1–14 keep their absolute pins. Generator 15 was pinned at
  introduction: its terrain equals generator 14's at every pinned chunk, and its
  site plans are pinned on three seeds (D-141).

### Behaviour, persistence and clue evidence

Each code commit ran the full portable build against its exact tree in a
detached worktree (`build/m14-check`) whose tree hash matched the index. Its
message records the classes:

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `dc1c447` | Full portable build: 1,998 tests in 227 classes, one existing skip | `SurvivorSitesTest`: a camp only in a held walk-up outside the quarantine with every planned cell standable, dry and inside the building, roles by head count, refuge beds out of reach across one wall, plans identical in any order and resolvable by id, and a camp and a refuge within two regions of the spawn on all six design seeds. The generator-15 terrain and site pins, dispatch, revision and selector naming generator 15, `RuntimeBoundsTest` (the site-plan cache) and every earlier pin |
| `660d365` | Full portable build: 2,015 tests in 230 classes, one existing skip | `UrbanSurvivorSitesTest` (9): bodies appear and go dormant in three dimensions keeping health and needs; the daily routine on planned cells; hidden civilians stay in their cellar, are not met through its wall and are found by the panel; needs match hour for hour whether active or dormant over 96 hours; starvation leaves tombstones; theft, aid and debts; a hostile camp in Survival and Creative; refuge stash ownership. `UrbanSitesSaveTest` (3): repeated loads keep the people, injuries, needs, deaths, trust, fear and debts; a missing section fails the load and keeps the live world; every malformed payload fails the whole load. `UrbanSiteRelocationTest` (2), camp illness never choosing a survivor, `RuntimeBoundsTest` (site records), the slice loop on generator 15 for the six frozen seeds and `GameLoopIntegrationTest` |
| `3e82426` | Full portable build: 2,020 tests in 231 classes, one existing skip | `UrbanSiteDialogueTest` (5): a trader's finite stock in Survival and Creative that a load does not refill; hidden civilians move only once aid has earned their trust; a threat robs a refuge with a lasting betrayal and turns a camp hostile; the medic once a game day |
| `1e4dfa3` | Full portable build: 2,029 tests in 233 classes, one existing skip | `UrbanSurvivorCluesTest` (4): hiding people heard only near their panel, from inside their room, in turn, silenced by fear, never from a camp's door and never once inside; the log line once; nothing discovered by sound; a camp named and known on entry in both modes while generator 14's same building is not. `SurvivorSoundsTest` (2), `EcologySoundsTest`, `UrbanDiscoveryTest`, `UrbanSurvivorSitesTest` |
| `48d9533` | Full portable build: 2,032 tests in 234 classes, one existing skip | `UrbanSurvivorQaSceneTest` plays the tour headlessly in Survival and Creative and refuses generator 14. Natively on this tree the Survival tour passed (`m16-native-survival-3.log`), with every sleeper still drawn out of sight |
| `8a15656` | Full portable build: 2,033 tests in 235 classes, one existing skip | `SleepPoseTest`: asleep, every appearance's body rests within 0.05 of its floor and lies below 0.6, while standing its feet meet that floor; `FragmentAnatomyModelTest` and `SpeciesFragmentDrawTest` still reassemble the asleep pose. This tree is the final code; the final native runs below used it |

### Native survivor tour, both modes

Host: Windows 11 Pro 10.0.26200, AMD Ryzen 5 5600X, NVIDIA GeForce RTX 3060 Ti,
driver 610.88, OpenGL 3.3.0 with KHR_debug; 1280 × 720.

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_survivors'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m16-survivors-survival-4'
.\gradlew.bat run -PveylonDataDir=build/qa/m16-survivors-survival-4 --console=plain
```

The runs used the same variables from Git Bash. The Creative twin used
`creative` and the tag and directory `m16-survivors-creative`. Seed
`20260731` gives these legs (from the `plan` and `staged-pose` lines):

| Leg | Cells | Played |
| --- | --- | --- |
| Camp, 10:00 | camp `urban:building:0,0:53:0`: Teodor the lookout, Greta the trader and Zev, five spare beds upstairs; rear door leaf (176, 41, 93); yard pose (172, 41, 93); way in (177, 41, 93) | W with mouse look; F on the rear door; F to talk; the trade and three gifts through the conversation's command; Esc; F on the first stash container; Esc |
| Refuge | refuge `urban:building:0,0:3:0`: Fenna, Saskia and Esme; street approach (41, 41, 56); hall (42, 37, 45); loose panel (43, 37, 45); inside (44, 37, 45) | W from the street door down to the hall; F on the panel; W; F to talk; three gifts and word of the camp through the command; Esc, F5, F9 |
| Away | sidewalk corner (317, 41, 326), 170 blocks or more from both | staged pose, waiting for dormancy |
| Camp, 23:00, then 09:00 | as above | W; a right click placing a torch beside a sleeper; W up the stairs to the spare beds |
| Empty refuge, day 2 | as above | W; F on the shelf; one item through the storage screen's transfer command |

Both runs printed `[urban-survivors] pass=true` with 5 staged poses and 16
captures, `wallFrames=0 strayFrames=0` over 39,254 (Survival) and 39,275
(Creative) resident-frames, no infected engaged and full health, in 99 route
seconds. Their `deeds` lines are identical: the offers "4 Fiber for 1 Bandage"
and "2 Plank for 2 Dried Berries"; "A fair trade."; the camp's trust 55 after
three gifts; the civilians' "How did you find us? Keep your voice down.";
"We don't know you well enough to follow you anywhere." three times, each
answered by a gift ("Thank you. Thank you. This buys us days."); then "Then
we'll go. Not now, not while anyone could follow. Thank you."; setting out at
game minute 691 to arrive at 737; all three camp members at their beds and the
post at night; and Dried Berries taken from the shelf left behind with no
theft. After F9 every person, need, debt, stash count and plan matched the
`before-save` line. Both `after` lines are identical too: the camp's trust 65
(the arrival added 10) and the three civilians living at the camp. Each log
reports `glGetError=0`, 19 KHR messages with 0 errors (the usual MEDIUM
pixel-transfer notes) and `nativeErrors=0`; Gradle exited 0 after 1 min 42 s
in both modes. The `views` lines name "District / Reclaimed Residential /
Survivor camp" in the camp, "Basement" in the hall and "Hidden cellar" in the
refuge, and the storage owner lines "Camp storage: shared with you as a
friend" and "Left to you by the people who hid here".

Inspected captures, from the final Survival run unless noted:

- `01`: the camp's rear door from the yard, "Open door (crouch: bar it from
  this side)". `02`: inside, HUD "Survivor camp", Teodor the lookout in the
  corridor by the door and "Survivors hold this building: three people keep a
  camp here." in the log.
- `03`: Greta's conversation, "Trader - Survivor camp", Trust 0, with the two
  trades, the demand and the attack. `04`: "A fair trade." and the log's
  "Traded 4 Fiber for 1 Bandage." `05`: "(friends)", Trust 55, the friend
  price ("3 Fiber for 1 Bandage", "1 Plank for 2 Dried Berries") and "The
  survivor camp counts you as a friend" in the log.
- `06`: the kitchen cupboard, "Camp storage: shared with you as a friend",
  holding rations, waterskins and the traded fibre.
- `07`: the plaster wall in the basement hall, the panel's prompt, HUD
  "Basement" and "Somewhere behind the wall, someone is trying very hard to
  be quiet." in the log, before anything was found. `08`: the panel slid
  aside, two civilians beyond it. `09`: the cellar, HUD "Hidden cellar",
  "Three people huddle at the back of the cellar, watching you."
- `10`: Esme, "Villager - Hidden refuge", Trust −10, "[4] Tell them about the
  camp you found", "How did you find us? Keep your voice down." `11`:
  "(friends)", Trust 59, "Then we'll go." and "They will go to the survivor
  camp once it is quiet" in the log. `12`: a civilian after F9, "Game
  loaded."
- `13`: a camp bedroom at 23:27 lit by the placed torch, a sleeper lying on
  the floor. `14`: Esme upstairs in the camp at 09:19 the next morning, HUD
  "Survivor camp", and the log's "Word reaches you: the people from the
  hidden cellar reached the camp safely."
- `15`: the empty cellar by day. `16`: the pantry shelf, "Left to you by the
  people who hid here", rations and waterskins on it.
- Creative: the same frames with the Creative panel; the placed torch is not
  spent.

Earlier native runs on the tour's earlier code (`m16-native-survival.log`,
`-2`) passed but showed faults, fixed in the tour, and one in the game: the
staged kit weighed 43 kg against a 28 kg capacity; the night view aimed at a
sleeper through a wall and the spare beds were visited in the dark; and, at
night, nobody asleep could be seen anywhere, because the sleep pose drew every
sleeper half a block under its floor. The tour now carries 16.5 kg, walks to a
sleeper with a clear view and lights the room, and visits the spare beds the
next morning; the sleep pose was fixed in the game (the `fix(render)` commit
above). `m16-native-survival-3.log` passed on the tour's own commit, before
that fix, with every sleeper still out of sight.

Observations, none a tour failure:

- Hunger and thirst move on the hour: by the end the camp's members had
  eaten and drunk from their stash on schedule (hunger 13.9, thirst 45.8),
  and the travellers arrived hungry (33.3) but watered.
- The arrival is reported when the travellers' clock catches up with the
  camp's after a long absence, so its log time varies with chunk loading
  (23:31 in the first native run, 09:04 the next morning in the final ones);
  the arrival minute (737) and every need are the same.
- A sleeper's position is its feet, so the night view's crosshair rests at
  the sleeper's feet with the body beside it.

### Commands and portable builds

All Gradle commands used the bundled Temurin 25.0.4.1+1
(`build/tools/jdk25/jdk-25.0.4.1+1`) and the Windows wrapper. Logs are local
ignored artifacts under `build/`.

- Focused runs while building each step, among them
  `.\gradlew.bat test --tests "com.veylon.UrbanSiteDialogueTest"`
  (`m16-stage3-focused.log`: 5 passed), the clue and sound tests
  (`m16-stage4-focused.log`: `UrbanSurvivorCluesTest` 4, `SurvivorSoundsTest`
  2 and `EcologySoundsTest` 1 passed) and
  `.\gradlew.bat test --tests "com.veylon.UrbanSurvivorQaSceneTest"` after each
  tour change (`m16-stage5-focused.log`: 3 passed).
- The sleep pose: a throwaway probe measured the posed body (the old pose
  drew it from 0.94 to 0.27 blocks under the feet; laid flat without a drop,
  the body's lowest point lies 0.09 under them in every appearance, and a
  trader's pack 0.29), and was deleted. `m16-sleep-probe.log` holds the
  second, per-part measurement; it overwrote the first probe's log. `SleepPoseTest`,
  `FragmentAnatomyModelTest`, `SpeciesFragmentDrawTest`, `FirePanicTest`,
  `InfectedBehaviorTest`, `ProjectileHitZoneTest` and
  `VillageDailyLifeGameplayTest` then passed (`m16-sleep-fix-focused.log`).
- Each commit's exact-tree build, in the worktree: `m16-stage1-build.log` to
  `m16-stage6-build.log` (the table above).
- The final code is `8a15656`'s tree. Its exact-tree build passed in
  12 min 15 s: 2,033 tests in 235 classes, zero failures or errors and the existing Windows
  symlink-privilege skip in `AppPathsTest` (`m16-stage6-build.log`).
- Native runs: `m16-native-survival.log` and `-2` (framing faults and the
  hidden sleepers, see above), `m16-native-survival-3.log` (the tour's own
  commit), then `m16-native-survival-4.log` and `m16-native-creative.log`
  (final).
- `graphify update . --no-cluster` once on the final code: 9,594 nodes and
  38,434 edges, with the existing `build.gradle` extractor warning
  (`m16-graph-update.log`). No clustering or semantic extraction ran.

### Limits and next gate

- The tour stages poses between legs, a forward-only clock, the weather and a
  kit, and makes its conversation choices and the shelf transfer through the
  screens' own commands; it is scripted evidence, not the human playtest
  deferred by D-80. Whether the routines read as lived-in, the tone of the
  lines and the muffled cues were checked by script, logs and captures, not
  judged or heard by a person.
- Camps hold reclaimed ground floors only (D-142). The move to a camp happens
  unseen and is not an escort. Balance values (stash sizes, foraging, trust
  steps, offers, the medic, a threat's yield) are initial values for 23.
- Map presentation of sites is package 21; camp ambience is 22; rumours and the
  hidden-civilian aid mission are 20.
- No reviewer subagents ran. The commits rest on their exact-tree builds, the
  native tours and the inspected captures.

Next: package 17.

## Milestone 17 — guarded strongholds and hostile factions (2026-10-22 to 2026-10-23)

### Scope and baseline

- Branch `feature/urban-survival` at `9bc3517` (the 16 records), clean tree.
  Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records.
- Generator 16 (layout 13, template 7, content 3, encounter catalogue 5) is new,
  with the section `urban.compounds` v1 and compound rows (kind codes 3 and 4)
  in `urban.sites` v1. No block, item, enum ordinal, persisted id, legacy
  faction or existing section version changed; the default new world is
  unchanged.
- Generators 1–15 keep their absolute pins. Generator 16 was pinned at
  introduction: its terrain equals generator 14's at every shared pinned chunk,
  and its compound plans and gate-line chunks are pinned on three seeds (D-154).

### Behaviour, persistence and access evidence

Each code commit was checked against its exact tree: the full portable build,
or, for the comment-only `c2575cd`, compile and Javadoc. The commits were
built in the main working tree in order; while an earlier commit was checked,
later work was parked in `git stash` and only record drafts (documents, which
no build step reads) were otherwise changed. Each message records the
classes:

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `90ea27d` | Full portable build: 2,044 tests in 236 classes, one existing skip | `CompoundSitesTest` (9): a stronghold and a raider yard within three regions of the spawn on all six design seeds; every member building held, walls, posts and people on standing cells; camps and encounter budgets kept out of claimed parcels; plans identical in any order; generated stamping in any chunk order; a stronghold walled but for its locked gate; a raider yard entered by its back way with its gate shut. The generator-16 terrain, plan and gate-line pins, the slice loop on generator 16, `RuntimeBoundsTest` and the dispatch, revision and selector expectations, with every earlier pin |
| `380ade8` | Full portable build: 2,055 tests in 238 classes, one existing skip | `UrbanCompoundPeopleTest` (8): posts kept and the same guards after dormancy; the gatekeeper passing and relocking his gate; the patrol and the night watch; raiders striking a Survival player and never a Creative one; their leash and giving up; guards and an infected fighting at the gate; generator 15 listing no compound. `UrbanCompoundSaveTest` (3): repeated loads and reactivation without duplicates, the raiders' dead and the locked gate, malformed compound rows |
| `9b360a0` | Full portable build: 2,067 tests in 240 classes, one existing skip | `UrbanCompoundAccessTest` (8 methods, 9 cases): a visitor let in by day, kept to the yard and the clinic, the gate shut and locked behind them, the pass ending at dusk; a barracks warning, then the pass lost and the player wanted without the stronghold turning hostile; an intruder wanted and struck in Survival and unperceived in Creative; theft caught in sight, unseen theft only missing until the hour and blamed only after a recent sighting; forced entry answered by at most three guards (never the gate's two) who return; a friend let in at night by a knock and a stranger turned away; at most two raider hunters, never the boss, once a day, the cooldown surviving a load; a taken yard that stays taken and a single reward. `UrbanCompoundsSectionTest` (3): an exact round trip, a missing section failing the load and keeping the live world, and 14 malformed payloads each failing it |
| `4ed1957` | Full portable build: 2,068 tests in 240 classes, one existing skip | `UrbanCompoundAccessTest`'s new case: a visitor pacing the yard's inner passage for 30 seconds leaves the alert below 60 and the trader at the pitch (it reached 100 before the fix); the same steps in the barracks, a wanted player's steps and a forced door are not everyday |
| `64851d5` | Full portable build: 2,068 tests in 240 classes, one existing skip | The compound people, access and save tests unchanged without the two inaudible guard noises (the gate's swing and a guard's blows) |
| `c2575cd` | `compileJava` and `javadoc` (a comment-only change) | Doclint accepts the corrected `CompoundAccess` documentation; the code is that of the commit before |
| `44b3e50` | Full portable build: 2,068 tests in 240 classes, one existing skip | `UrbanSurvivorQaSceneTest` plays the milestone-16 tour headlessly in Survival and Creative on the shared `UrbanPeopleScene` and refuses generator 14. The same test run on `c2575cd` in a separate worktree (`build/m14-check`) printed the same 64 tour lines, positions and times included |
| `cd63d2d` | Full portable build: 2,071 tests in 241 classes, one existing skip | `UrbanStrongholdQaSceneTest` plays the stronghold tour headlessly in Survival and Creative and refuses generator 15. The first native runs on this tree failed (below) |
| `8a858fc` | Full portable build: 2,071 tests in 241 classes, one existing skip | Both people tours pass headlessly in both modes, the survivor tour printing its same 64 lines; a throwaway probe played the stronghold tour under uneven frame steps nine times, all passing. This tree is the final code; the final native runs below used it |

### Native stronghold tour, both modes

Host: Windows 11 Pro 10.0.26200, AMD Ryzen 5 5600X, NVIDIA GeForce RTX 3060 Ti,
driver 610.88, OpenGL 3.3.0 with KHR_debug; 1280 × 720.

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_strongholds'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m17-strongholds-survival-2'
.\gradlew.bat run -PveylonDataDir=build/qa/m17-strongholds-survival-2 --console=plain
```

The runs used the same variables from Git Bash. The Creative twin used
`creative` and the tag and directory `m17-strongholds-creative-2`. Seed
`20260731` gives these legs (from the `plan` and `staged-pose` lines):

| Leg | Cells | Played |
| --- | --- | --- |
| Stronghold, 10:00 | stronghold `urban:building:1,2:112:0`, "Calder Ward", 9 people: Gideon the gatekeeper, Ronan and Saskia the sentries, Calla the patrol, Oona the trader, Esme the medic, Kaito, Yusra and Bastien; gate leaves (416, 41, 706) and (417, 41, 706) facing north; street (416, 41, 702); sign (418, 43, 706); yard (416, 41, 709) | W with mouse look to the porch; F to talk; the rules and leave to come in through the conversation's command; Esc; W through the open gate |
| Trade and clinic | the trader's pitch on the courtyard edge; the clinic's courtyard door, inside (396, 41, 732) | F to talk; the trade through the command; Esc; W; F on the clinic door; F to talk, after one step round a window behind the medic |
| Barracks | door inside (427, 41, 716), yard cell (427, 41, 718); patrol waypoints (398, 41, 718), (433, 41, 718), (433, 41, 747) and (398, 41, 747) | W with F through the door; waiting; W back out |
| Threat | porch pose (416, 41, 705); one Veilbound staged at (416, 41, 698) | watching only |
| Raider yard | raiders `urban:building:0,-2:40:0`, 4 of them; back way approach (108, 41, -446); rear door (108, 41, -421) | W and F through the street door standing; left Control for the last 10 blocks; F5, F9 |
| Away | sidewalk corner (317, 41, 582), at least 170 blocks from both | staged pose, waiting for dormancy |
| Stronghold, 23:15 | porch (416, 41, 705) | F on the gate leaf |

Both runs printed `[urban-strongholds] pass=true` with 5 staged poses and 16
captures, `wallFrames=0 strayFrames=0` over 144,934 (Survival) and 156,082
(Creative) resident-frames, no infected engaged by the player and full
health, in 129 and 137 route seconds. Their `deeds` lines agree but where the
modes differ: the gatekeeper's rules ("This is a ward of the precinct.
Visitors come in by day and keep to the yard and the clinic; the buildings
are ours."); "In you come. Keep to the yard and the clinic, and be out by
dark." with a pass until game minute 1,320 (22:00); the offers "1 Medicine for
2 Waterskin (clean water)" and "2 Plank for 2 Dried Berries", the first traded;
one step round the window the aim had lined up behind Esme; then in Survival
"warned at (427, 41, 716) after 30s; nearest guard in sight Calla@1.8", and in
Creative "unperceived at (427, 41, 716) by Calla@1.9"; the Veilbound met by
Gideon at (417, 41, 700) (Creative (417, 41, 699)), put down, and Gideon back
on the porch; at night all 9 people back once with the watch at the gate; and
the knock "answered through the gate" in Survival (Ronan: "We don't open
after dark. Come back at first light.") and "the knock went unheard" in
Creative. After F9 every compound's memories, people and stores matched the
`before-save` line: the stronghold's trust 1, its pass until 1,320, the last
sighting inside at minute 740.2 (12:20), Gideon at 39 health from the
Veilbound's one blow; the raider yard hostile at trust −60 with no cooldown.
By the `after` line the stronghold, dormant meanwhile, had eaten and foraged on
the hour (stash 39 to 47 items) and Gideon was back at 45. Each log reports
`glGetError=0`, 19 KHR messages with 0 errors (the usual MEDIUM pixel-transfer
notes) and `nativeErrors=0`; Gradle exited 0 after 2 min 12 s (Survival) and
2 min 20 s (Creative). The `views` lines read the sign "Calder Ward. Visitors
by day: ask at the gate.", "District / Civic Precinct / Guarded stronghold" in
the yard and "District / Industrial Yards" at the back way, whose rear door
prompts "[F] Bar the door from this side" (crouched; Creative: "[F] Lock door
(Creative)").

Inspected captures, from the final Survival run unless noted:

- `01`: the gate post, its torch on top and the painted sign, "Sign: \"Calder
  Ward. Visitors by day: ask at the gate.\"". `02`: Gideon, "Guard - Guarded
  stronghold", with "[4] Ask to be let in" and the rules; the log's "A guarded
  stronghold: 9 people hold t…". `03`: "In you come…", the choice now "[4] Ask
  to be let through (you have a visitor's pass)" and "Gideon lets you in as a
  visitor until dus…" in the log.
- `04`: inside the gate passage, HUD "Guarded stronghold", the courtyard
  beyond. `05`: Oona, "Trader - Guarded stronghold", her two offers. `06`: "A
  fair trade." and "Traded 1 Medicine for 2 Waterskin" in the log. `07`: Esme,
  "Medic - Guarded stronghold", "[4] Ask to have your wounds dressed",
  indoors.
- `08`: the barracks doorway at 12:19, Calla just outside it and her "Visitors
  keep to the yard and th…" in the log. `09`: back in the yard, looking into
  the barracks left behind.
- `10`: Gideon on the street striking the Veilbound. `11`: Gideon back on the
  porch beside the barricade, "The Veilbound collapses." in the log.
- `12`: the back way's wooden street door, "District / Industrial Yards".
  `13`: the hall's metal rear door into the yard, crouched, "Bar the door from
  this side". `14`: the same after "Game loaded."
- `15`: the gate post and sign at 23:30, the barricade's sheet metal. `16`:
  the locked leaf, "Locked. Force it open with a pickaxe (loud) or find another
  way in", and "Ronan calls through the gate: \"We don…\"" in the log.
- Creative: the same frames with the Creative panel, but `08` has no warning in
  the log and `16` no answer.

The first native runs (`m17-native-survival.log`, `m17-native-creative.log`)
on `cd63d2d` failed in both modes at the trader with "no conversation with
Oona: [F] Talk to Calla (Guard)": F greets the nearest person within 3.2
blocks, and on native frame times Calla's round passed the trader's pitch as
the player reached it, so every F greeted her. Headless runs at a fixed step
never put her there. `8a858fc` makes the tour press F only when the prompt
names its person; the final runs then needed one step round a window behind
the medic and none at the trader.

Observations, none a tour failure:

- An "Ashfall" event darkened the sky from 11:52; the tour pins clear weather
  only where it stages the clock, and the event is presentation here.
- The raider yard's stash reads 0 and its people's hunger and thirst 0.0 in
  both lines: the player never saw a raider or set foot in its porch or yard,
  so the yard was registered but not discovered, its needs clock had not
  started (D-146) and its containers had never been rolled.
- A dormant compound's people keep health and needs, not positions: at night
  they reappear on their scheduled cells (D-144), the watch at the gate.

### Commands and portable builds

All Gradle commands used the bundled Temurin 25.0.4.1+1
(`build/tools/jdk25/jdk-25.0.4.1+1`) and the Windows wrapper from Git Bash. Logs
are local ignored artifacts under `build/`.

- Two throwaway probes surveyed the candidate blocks and rows on the design
  seeds (`m17-survey.log`, `m17-survey2.log`) and were deleted.
- Focused runs while building each step, among them the compound classes after
  each change (`m17-tests1.log` to `m17-tests7.log`; the first failed the
  generator-16 plan pin after the raider patrol's far waypoint was moved inside
  the yard, and the pins were recorded again before the first commit), the
  access tests before and after the everyday-sounds fix (`m17-everyday1.log`:
  the new case failed with the alert at 100; `m17-everyday2.log`: the compound
  access, people and save tests passed, and the stronghold tour run with them
  now reached the raiders' back way, where a crouched F would not open its
  street door), the survivor tour after the refactor (`m17-refactor-tour.log`: 3
  passed) and the stronghold tour while it was written (`m17-tour1.log`
  failed: the medic fled in Survival and the Creative check was stricter than
  the game's sight rule; `m17-tour2.log`: 3 passed; `m17-tour3.log`: 3 passed
  after the waits for compounds to wake).
- The talk fix: both people tours after the change (`m17-talk1.log`: 6
  passed), and a throwaway probe (deleted) playing the stronghold tour under
  uneven frame steps of 1/35 to 1/144 second (`m17-jitter1.log`: 7 runs,
  `m17-jitter2.log`: 1 with positions in the step line, `m17-jitter3.log`: 9
  runs with both tours, all passing).
- The survivor tour before the move: the same test on `c2575cd` in the
  detached check worktree `build/m14-check` (`m17-survivors-before.log`); its
  64 printed tour lines equal the refactored run's, positions and times
  included.
- Each commit's exact-tree check, in commit order: `m17-c1-build.log` to
  `m17-c9-build.log`, with `m17-c6-javadoc.log` in place of a build for the
  comment-only commit (the table above). A first run of the noise-removal
  build, with only the blow noise removed, was stopped part way when the gate
  swing's noise was found to be inaudible too; the build was run again on the
  tree that removes both, and its log replaced the first. `CompoundGate`'s
  comment was corrected while that build ran, keeping its line count;
  recompiled afterwards (`m17-c5-recompile.log`), its class file hashed
  identically.
- The final code is `8a858fc`'s tree. Its exact-tree build passed in 12 min
  48 s: 2,071 tests in 241 classes, zero failures or errors and the existing
  Windows symlink-privilege skip in `AppPathsTest` (`m17-c9-build.log`).
- Native runs: `m17-native-survival.log` and `m17-native-creative.log` (on
  `cd63d2d`, failed at the trader, see above), then
  `m17-native-survival-2.log` and `m17-native-creative-2.log` (final), run by
  a script that refuses an existing QA directory.
- `graphify update . --no-cluster` once on the final code: 9,991 nodes and
  40,429 edges, with the existing `build.gradle` extractor warning
  (`m17-graph-update.log`). No clustering or semantic extraction ran.

### Limits and next gate

- The tour stages poses between legs, a forward-only clock, the weather, one
  infected and a kit, and makes its conversation choices through the screen's
  own command; it is scripted evidence, not the human playtest deferred by
  D-80. Whether guards read as fair, compounds as lived-in and the raider yard
  as a threat worth avoiding was checked by script, logs and captures, not
  judged by a person.
- The tour does not go into the raider yard, fight the raiders or take their
  stores; hunts, their cooldown and capture rest on the headless tests.
- Guards answer alarms from inside their wall and never come out to help a
  gatekeeper fighting on the street. Balance values are initial values for 23.
  Map presentation is 21, ambience 22, the stronghold's relay component and the
  guarded-access mission 20.
- No reviewer subagents ran. The commits rest on their exact-tree builds, the
  native tours and the inspected captures.

Next: package 18.

## Milestone 18 — safehouses, salvage and fortification (2026-10-23 to 2026-10-27)

### Scope and baseline

- Branch `feature/urban-survival` at `2012dcd` (the 17 records), clean tree.
  Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records.
- Generator 17 (layout 14, template 7, content 3, encounter catalogue 5) is new,
  with three appended blocks (`CAR_WRECK`, `BARRICADE`, `BARRICADE_METAL`, ids
  89–91), four appended items, the section `urban.fortifications` v1 and
  `urban.refuges` v2. No existing ordinal, persisted id or other section
  version changed, and no earlier generator's behaviour but one line of
  wording: a city sleep refused for a nearby infected now names it as such. The
  default new world is unchanged.
- Generators 1–16 keep their absolute pins. Generator 17 was pinned at
  introduction: its terrain equals generator 14's at every shared pinned chunk,
  and its vehicle plans and the stamped chunks under the wreck nearest the
  origin are pinned on three seeds (D-169).

### Behaviour, persistence and fortification evidence

Each code commit was checked against its exact tree. Its staged diff was
applied to its parent in the detached validation worktree `build/m14-check`,
the two `git write-tree` hashes were compared equal, and the full portable
build ran there while later work went on in the main checkout, untouched by
that build. Each message records the classes:

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `c771b14` | Full portable build: 2,079 tests in 242 classes, one existing skip | `VehicleSitesTest` (5): wrecks in one lane of a local street, clear of crossings, water, hazards, lots and compound gates; the same wrecks in any planning order; an engine, a body and a cabin over the middle; a generated wreck whole in every chunk order with the street past it open; generator 16 the same city without wrecks. The generator-17 terrain, vehicle-plan and wreck-chunk pins, the slice loop on generator 17, `RuntimeBoundsTest`'s vehicle-plan cap and the dispatch, revision and selector expectations, with every earlier pin |
| `4fa6057` | Full portable build: 2,089 tests in 243 classes, one existing skip | `UrbanSalvageTest` (10): a table apart for planks after steady loud work, an axe halving the time; metal needing a pry tool and a locker giving what it held; a cupboard searched, emptied and taken apart paying its contents once; a full pack refusing and moving nothing; a wreck's engine, cabin and body; a camp's furniture wrecking what is theirs; Creative removing and taking nothing; generator 16 still carrying furniture away whole with its recipes; the HUD prompt; pieces taken apart staying gone through a save and a load |
| `746d896` | Full portable build: 2,103 tests in 245 classes, one existing skip | `UrbanFortificationTest` (9): a shattered window boarded up with a salvaged barricade that stops bodies, sight and light loudly; a Breacher hammering a barricaded doorway down blow by blow while a Veilbound waits outside; a Veilbound walking round half a barricaded doorway untouched; a brace taking a Breacher's blows before the door and coming down with its bar; from the far side the player's own blows going into the brace; repairs, a hung door and a glazed window; a misplaced barricade down from either side for most of its cost; Creative fortifying free with no Breacher coming; battle damage through a save and a load. `UrbanFortificationsSectionTest` (4): an exact round trip, a missing section failing the load and keeping the live world, the section refused in an earlier world, and every malformed row failing the whole load. The fortification caps in `RuntimeBoundsTest` |
| `47abd8b` | Full portable build: 2,116 tests in 246 classes, one existing skip | `UrbanRefugeClaimsTest` (10): the starter claim of a fresh world; an abandoned flat claimed at its bed and made home; a storey underfoot required and never a roof or station; survivors keeping their rooms until gone and a held building's upper floors anybody's; something hostile on the storey; three refuges at most and a second press to give one up; sleeping choosing home and death waking there; claims outlasting edits, saves and loads, home falling back to its storey; nothing new appearing inside a claim; Creative claiming alike and generator 16 without claims. `UrbanRefugesSectionTest`'s generator-17 round trip and malformed-claim cases, and the claim cap |
| `414c248` | Full portable build: 2,122 tests in 247 classes, one existing skip | `UrbanRefugeNightTest` (6): shut tight drawing nothing and resting better than one door unbarred; the nearest infected out there drawn to an unbarred door and waking the sleeper sooner; a quiet night with nothing out there and nothing made to appear; a torch drawing from farther through glass at night (not by day) until the windows are shattered and boarded up; a Creative sleeper drawing nothing and nothing changing outside a claim; the bed saying how the refuge stands |
| `ab1f4bc` | Full portable build: 2,123 tests in 247 classes, one existing skip | `UrbanRefugeNightTest`'s new case: an infected outside the unbarred starter refuge keeps its sleeper awake with "Something infected is too close to sleep safely.", the same body outside it shut tight does not and is not drawn, and a body inside the shut refuge still does |
| `ed8999d` | Full portable build: 2,123 tests in 247 classes, one existing skip | `UrbanSalvageTest`'s HUD case now expects the exact short line "[F] Search cabinet  ·  hold LMB for …" after a cupboard's search |
| `f084ff6` | Full portable build: 2,126 tests in 248 classes, one existing skip | `UrbanRefugeQaSceneTest` plays the refuge tour headlessly in Survival and Creative and refuses generator 16. This tree is the final code; the final native runs below used it |

### Native refuge tour, both modes

Host: Windows 11 Pro 10.0.26200, AMD Ryzen 5 5600X, NVIDIA GeForce RTX 3060 Ti,
driver 610.88, OpenGL 3.3.0 with KHR_debug; 1280 × 720.

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_refuge'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m18-refuge-survival-2'
.\gradlew.bat run -PveylonDataDir=build/qa/m18-refuge-survival-2 --console=plain
```

The runs used the same variables from Git Bash, each in a QA directory checked
not to exist beforehand. The Creative twin used `creative` and the tag and
directory `m18-refuge-creative-2`. Seed `20260731` gives these legs (from the
`plan` and `staged-pose` lines):

| Leg | Cells | Played |
| --- | --- | --- |
| The flat, 10:00 | apartment `urban:building:0,0:40:0` (intact, three storeys) beside the starter refuge; street pose (75, 41, 62); first-floor bed (65, 45, 65), slept in at (66, 45, 65); cupboard (65, 45, 66) | W with mouse look through the street door (F) and up the stairs; left Control and F on the bed; F on the cupboard, the transfers through the screen's command, Esc |
| Salvage and crafting | basins (65, 45, 68) and (65, 45, 74), cabinets (65, 45, 70) and (65, 45, 75), tables (65, 45, 73) and (65, 45, 77) | W to each; left Control and the left button held until it comes apart; C, the screen's craft command for a brace and three barricades, C |
| Defences | street leaves (75, 41, 64) braced and (76, 41, 64) barred; rear leaves (83, 41, 80) and (84, 41, 80) barred; hall cells (75, 41, 66) and (75, 41, 68) | F to close the leaf left open; the right button with the brace; left Control and F on each other leaf; back to the bed |
| Threats | one Breacher, then one Veilbound, staged at (75, 41, 60) on the street | pacing the hall with W; the spear when the Breacher came through; the right button with two barricades on the broken doorway; the right button on the hall floor and the left button held, crouched, to take that barricade down |
| Night | the bed at 23:15 | F; sleeping until the waking |
| Save | the bed | F5, F9 |
| Camp, 09:00 | camp `urban:building:0,0:53:0`; yard pose before its rear door; bed (190, 41, 79) | W through the rear door; left Control and F on the bed |

Both final runs printed `[urban-refuge] pass=true` with 2 staged poses and 17
captures, full health, in 132 (Survival) and 106 (Creative) route seconds.
The Survival `deeds` line: six pieces taken apart for 12 planks and 6 scrap; a
door brace and three plank barricades made, leaving a plank and a scrap; the
brace taking the first blow 4 seconds after the Breacher was staged (75/90);
the door giving way at 15 seconds, the refuge then counting "1 way in open";
the Breacher put down at the breach (75, 41, 63), the player at 100 health;
the breach boarded up with two barricades at 60/60; the hall barricade taken
down for 2 planks; the Veilbound hearing the hall at once and kept out, at
(75, 41, 63); the night starting "You settle down to sleep comfortably." and
"Your refuge is shut tight." at 23:15 with the Veilbound outside; and a quiet
"You wake after 2.0 hours. Fatigue 0." at 01:17. The Veilbound, still about,
was taken out before F5 (`unstaged-infected veilbound at 75, 41, 63`; in
Creative also the Breacher, never having heard the builder). After F9 the
claims (the starter ground floor at minute 480 and the flat at 612.4), home,
the storage's four berries and waterskin (searched), the six salvaged pieces'
air, both barricades at 60, the barred leaf and the rear leaves matched the
`before-save` line. Creative: salvage gave no materials (the staged ones were
used), the Breacher never hunted the builder and the brace stayed at 90, crouch
and F locked the leaves, the hall barricade came down for nothing, and the
night, save and camp went as in Survival. Each log reports `glGetError=0`, 20
KHR messages with 0 errors (the usual MEDIUM pixel-transfer notes) and
`nativeErrors=0`; Gradle exited 0 after 2 min 19 s (Survival) and 1 min 49 s
(Creative). The boot logs read 122 of 160 texture layers and 131 of 131 item
icons.

The `views` lines read the crouched bed "[F] Rest here  (crouch: claim these
rooms as a refuge, 2 of 3)  ·  hold LMB for 4 fiber, 1 plank, 1 scrap metal";
"District / Reclaimed Residential / Your refuge"
after the claim; "Your refuge's storage | Searched"; "[F] Rain collector:
0.0/3.0 L  ·  hold LMB for 2 scrap metal" while taking the first basin apart;
"[F] Take down the brace and lift the bar (brace 90/90)  ·  crouch and hold
LMB to take it apart"; "[F] Sleep in your refuge  (shut tight; crouch: give it
up)"; and at the camp "[F] Rest here  (these rooms are the camp's: claiming
them would be taking what is theirs)  ·  hold LMB for 4 fiber, 1 plank, 1 scrap
metal".

Inspected captures, from the final Survival run unless noted:

- `01`: the camp bed in the flat's bedroom, the HUD "Your refuge", the prompt
  "Sleep in your refuge  (4 ways in open; crouch: give it up)" and "You claim
  the first-floor flat as a refuge…" in the log. `02`: the cupboard's screen,
  "Your refuge's storage | Searched", the berries and waterskin in it beside
  what it held.
- `03`: a basin half taken apart, the progress bar and "Rain collector:
  0.0/3.0 L · hold LMB for 2 scrap metal" whole on the one line. `04`: where the
  last table stood, the log listing each piece and what it gave, 12 planks and
  6 scrap on the hotbar. `05`: the crafting screen and "Crafted 1 x Door Brace",
  "Crafted 1 x Plank Barricade" three times.
- `06`: the braced street leaf, "Take down the brace and lift the bar (brace
  90/90)", the other leaf still open. `07`: the bed, "shut tight", and the four
  bars in the log.
- `08`: the braced leaf from the hall, "(brace 75/90)", and "Something hammers
  at a door nearby." `09`: the Breacher fallen in the broken doorway, "A brace
  splinters behind a door.", "A door gives way with a crash." and "The Breacher
  collapses." `10`: the doorway boarded up, "Plank Barricade (60/60) · hold LMB
  for 2 planks", "You barricade the broken doorway." twice.
- `11`: a barricade standing in the hall. `12`: the hall clear again, "You take
  apart the plank barricade: 2 p…". `13`: the boarded doorway still at 60/60
  from inside, the Veilbound beyond it.
- `14`: "Sleeping… (Esc to wake)" over "Your refuge is shut tight." `15`: the
  bedroom at 01:18 after the fade, "You wake after 2.0 hours." `16`: the same
  after "Game loaded.", still shut tight.
- `17`: the camp's bed, the HUD "Survivor camp", "Survivors hold this
  building: three peo…" and "These rooms are the camp's: claiming…".
- Creative: `08` and `10` show the braced leaf, "Already braced" with the
  brace kept in hand, and "The door is now locked." for the other leaves; `04`
  shows the staged planks and scrap untouched by salvage.

The first native runs, on the tour before its last fixes
(`qa-m18-refuge-survival.log`, `qa-m18-refuge-creative.log`, 2026-10-23):
Survival passed with the same outcomes, but its captures showed the salvage
hint behind an F action cut off ("hold LMB to take apart the b…"), capture `08`
looking down the hall instead of at the hammered door, and `15` still dark with
the sleep fade. Creative failed at the hall barricade, "the barricade never
went up at 75, 41, 68: AIR": on native frame times the pacing had ended with
the player standing on that cell. `ed8999d` shortens the hint, and the tour now
walks to the near hall cell before both board-up steps, faces the door for
`08`, waits for the fade before `15`, and keeps walks inside the shut refuge
from chasing a threat outside. The final runs above passed on that code.

Observations, none a tour failure:

- An "Ashfall" event darkened the sky from 11:52; the tour pins clear weather
  only where it stages the clock, and the event is presentation here.
- The Veilbound noticed at once because the Breacher's last noises were still
  fresh; it then stood at the boarded doorway and lost the trail once the hall
  was quiet.
- A fresh survivor wakes rested after two hours, so the night ends at 01:17;
  a tired one sleeps to morning (`UrbanRefugeNightTest`).

### Commands and portable builds

All Gradle commands used the bundled Temurin 25.0.4.1+1
(`build/tools/jdk25/jdk-25.0.4.1+1`) and the Windows wrapper from Git Bash. Logs
are local ignored artifacts under `build/`.

- Throwaway probes surveyed the starter superblock's apartments, the salvage
  wear of the flat's furniture and the survivor buildings near the spawn
  (`m18-probe.log`, `m18-probe2.log`, `probe.log`) and were deleted.
- Focused runs while building each step (`m18-c1-focus*.log`,
  `m18-c2-salvage.log`, `m18-c3-*.log`, `m18-c4-*.log`, `m18-c5-*.log`), then the
  tour while it was written (`tour.log`, the last of several runs: the claim
  prompt carried the bed's salvage hint; a Creative container spills what it
  held, as every broken container does; the salvage aim was read before it
  settled; the brace was asked for again after it was used; and the first
  save refused the two staged bodies, which are no encounter records) and the
  last focused check of salvage, fortification, claims and the tour
  (`focused.log`: 10, 9, 10 and 3 passed).
- Each commit's exact-tree check, in commit order: `m18-c1-build.log` to
  `m18-c5-build.log`, `m18-c5b-build.log`, `m18-c5c-build.log` and
  `m18-c6-build.log` (the table above).
- The final code is `f084ff6`'s tree. Its exact-tree build passed in 13 min 18 s:
  2,126 tests in 248 classes, zero failures or errors and the existing
  Windows symlink-privilege skip in `AppPathsTest` (`m18-c6-build.log`).
- Native runs: `qa-m18-refuge-survival.log` and `qa-m18-refuge-creative.log`
  (first, see above), then `qa-m18-refuge-survival-2.log` and
  `qa-m18-refuge-creative-2.log` (final).
- `graphify update . --no-cluster` once on the final code: 10,464 nodes and
  42,720 edges, with the existing `build.gradle` extractor warning
  (`m18-graph-update.log`). No clustering or semantic extraction ran.

### Limits and next gate

- The tour stages poses between legs, a forward-only clock, the weather, two
  infected and a kit (and in Creative the materials), and uses the container
  and crafting screens' own commands; it is scripted evidence, not the human
  playtest deferred by D-80. Whether a refuge feels safe, salvage worth its
  noise and the threats fair was checked by script, logs and captures, not
  judged by a person.
- The native tour does not hammer a barricade, repair, hang a door, glaze a
  window, salvage a wreck or draw a body to an open refuge; those rest on the
  headless tests above.
- Balance values are initial values for 23. Map presentation is 21 and refuge
  ambience 22; utilities are 19.
- No reviewer subagents ran. The commits rest on their exact-tree builds, the
  native tours and the inspected captures.

Next: package 19.

## Milestone 19 — water, power and refuge utilities (2026-10-29)

### Scope and baseline

- Branch `feature/urban-survival` at `666dd9c` (the 18 records), clean tree.
  Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and
  these records.
- Generator 18 (layout 15, template 7, content 3, encounter catalogue 5) is new,
  with three appended blocks (`GENERATOR`, `ELECTRIC_LAMP`, `WATER_PURIFIER`, ids
  92–94), four appended items (Fuel Can, Generator, Work Lamp, Water Purifier),
  three audio buffers and the section `urban.utilities` v1. No existing ordinal,
  persisted id or other section version changed, and no earlier generator's
  behaviour. The default new world is unchanged.
- Generators 1–17 keep their absolute pins. Generator 18 was pinned at
  introduction to generator 17's city: its terrain, wreck, site and compound
  plans and the stamped chunks under the nearest wreck equal generator 17's pins
  on three seeds (D-183).

### Behaviour, persistence and utility evidence

The first three commits were validated in the main checkout while it held
exactly that commit's tree, with nothing else changed, the build log written
under `build/`; the sustainability check was validated in the detached worktree
`build/m14-check` checked out at its commit. The tour commit was not built (see
the next gate).

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `7258c34` | Full portable build: 2,139 tests in 250 classes, one existing skip (`m19-c1-build.log`) | `UrbanTapsTest` (6): a building's taps sharing the stale water left in its pipes, drawn dry and never refilled by rain or time; drinking straight from a tap as untreated water; a basin set down by hand and the starter courtyard's collector staying rain collectors; generator 17's basins the empty collectors they were; survivors' taps running only once nobody of theirs is left; worn buildings holding less. `UrbanUtilitiesSectionTest` (4): an exact round trip with nothing refilled, a missing section failing the load and keeping the live world, the section refused in generator 17, and every malformed row failing the whole load. The tap-ledger cap in `RuntimeBoundsTest`, the generator-18 pins, the slice loop on generator 18 and the dispatch, revision and selector expectations, with every earlier pin |
| `d4c5d7b` | Full portable build: 2,155 tests in 252 classes, one existing skip (`m19-c2-build.log`) | `UrbanPowerTest` (10): a running generator lighting its building's lamps, its light registered and its fuel burnt by time and load, worn and heard, one lamp switched off lightening the load, F stopping it and darkening every lamp and the meshes; the tank running dry through the game loop; the circuit as one building's four nearest loads within reach, a second generator and a lamp in the street refused; taking the generator away leaving the lamps dark and giving back its whole cans; seizing and a rebuild needing spare parts and a pry tool; pause and distance holding the circuit still with nothing caught up; Creative spending nothing and heard by nobody while the world's fuel burns the same; a body in the generator's room drawn to it and one far down the street not; fuel only from the tank of a car that did not burn, once, under generator 18. The section's device rows and their corruption cases, the device cap, `UtilitySoundsTest` (2) and the appended enum order |
| `4c93639` | Full portable build: 2,163 tests in 253 classes, one existing skip (`m19-c3-build.log`) | `UrbanPurifierTest` (8): a purifier built with its filter spent working only once packed with two fibre; water moving from the pipes and skins through its tanks into clean skins with nothing made or lost and the filter worn by the litre; no treatment without power and its work weighing on the fuel; a clogged filter stopping it until fresh fibre; taking it apart spilling its water and giving back its parts; survivors' pipes not pumped; Creative packing the filter for nothing. The section's purifier rows and their corruption cases |
| `640bf67` | Full portable build in `build/m14-check`: 2,165 tests in 254 classes, one existing skip (`m19-c4-build.log`) | `UrbanUtilitySustainabilityTest` (2): twenty days of each design seed's own weather filling the starter courtyard's collector, emptied once a day, giving 2.40–3.00 litres a day against a waking day's 1.13, the survivor never below 1.88 litres in hand; a can treating twelve litres, a filter more than a week's water, and 22–32 wrecks with fuel within a region of the spawn |
| `def0278` | `UrbanUtilityQaSceneTest` (`m19-tour2.log`): Survival and Creative pass, 13 captures each, about 205 simulated seconds; generator 17 refused. Later the same day, the full portable build on `ef29fe8`'s tree (records only over this code) in `build/m14-check`: 2,168 tests in 255 classes, one existing skip (`m19-final-build.log`) | The tour, headlessly: built, wired and dark; fuel from the wreck; two litres from the tap; lights on; the filter packed and a litre treated; the Veilbound drawn (heard by nobody in Creative); shut down; run dry; rebuilt from 88.5 to 100; the same utilities after F5 and F9 (the purifier holding 7.00 clean litres after 8 litres in, 1 drawn off, its filter at 68) |

### Native utility tour, both modes

GPU and driver from the run's `[gl]` line: NVIDIA GeForce RTX 3060 Ti, driver
610.88, OpenGL 3.3.0 with KHR_debug; 1280 × 720.

```bash
export VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_GAME_MODE=survival
export VEYLON_SCENE=urban_utilities VEYLON_RESOLUTION=1280x720 VEYLON_CAPTURE_TAG=m19-utilities-survival
./gradlew.bat run -PveylonDataDir=build/qa/m19-utilities-survival --console=plain
```

The QA directory was checked not to exist beforehand. Seed `20260731` gives
these cells (from the `plan` line): the starter refuge
`urban:building:0,0:44:0`, the bench (73, 41, 82) worked from (73, 41, 83), the
kitchen tap (74, 41, 82), the generator at (79, 41, 94) put down from (78, 41,
92), lamps at (76, 41, 88) and (67, 41, 88), the purifier at (75, 41, 82) put
down from (75, 41, 84), the wreck at (58, 43) with its tank at (58, 41, 46)
worked from (58, 41, 47), and the staged Veilbound at (85, 41, 96).

First run (`qa-m19-utilities-survival.log`, on the tour code of `def0278`): ten
of the thirteen captures, `01-generator-rebuilt` to `10-shut-down`, each with
`glErrors=0 khrErrors=0` and only the driver's medium-severity performance
notices; the Veilbound was drawn from (85, 41, 96) to (83, 41, 96) before it was
taken away. The process then exited cleanly during the run-dry leg (`11` would
follow about 140 seconds later), with no `pass=false` line or exception logged,
and the owner asked to stop testing.

Final runs (2026-10-29, the same code at `ef29fe8`): Survival with the tag and
directory `m19-utilities-survival-2` from the main checkout, Creative
(`VEYLON_GAME_MODE=creative`, `m19-utilities-creative`) from the isolated worktree
`build/m14-check` at the same tree, each directory checked not to exist
beforehand. Both printed `[urban-utilities] pass=true` with 2 staged poses, 13
captures and full health, in 205 (Survival) and 202 (Creative) route seconds;
every capture reports `glErrors=0 khrErrors=0`, each log `glGetError=0`, 16 KHR
messages with 0 errors (the usual MEDIUM pixel-transfer notes) and
`nativeErrors=0`; Gradle exited 0 after 3 min 29 s and 3 min 24 s
(`qa-m19-utilities-survival-2.log`, `qa-m19-utilities-creative.log`). The
Survival `deeds` line: the generator built and put down at (79, 41, 94), two lamps
and the purifier at (76, 41, 88), (67, 41, 88) and (75, 41, 82); a fuel can from
the tank at (58, 41, 46); the purifier at 2.92 litres in and its filter at 95.70
after the first treatment; the Veilbound drawn from (85, 41, 96) toward the
generator; the tank run dry at condition 89 and rebuilt from 88.5 to 100. The
`before-save` and post-load `utilities` lines match: generator off at condition
100 with an empty tank, both lamps on, the purifier on with its filter at 68 and
7.00 clean litres, 8 litres drawn from the pipes, skins 2/0/1. Creative staged its
fuel can (`staged-creative-fuel-can 1`), nobody heard its generator (`09-heard-by-nobody`)
and the rest went as in Survival.

Inspected captures, all thirteen from the Survival run: `01` the crafting screen
and "Crafted 1 x Generator"; `02` a work lamp put down dark, "Water purifier put
down"; `03` the worn wreck's fuel tank half taken apart; `04` the kitchen tap,
"stale tap water, 7 L left", two skins filled; `05`–`06` the generator running at
21:39, "1.0 can left", the room lit; `07` the filter packed; `08` "treating, 2.9 L
in", a clean waterskin filled; `09` the generator still running at 22:28 (the
Veilbound, behind the courtyard wall, is not in frame; the log line records its
move); `10` the refuge dark after "You switch the generator off."; `11` at 02:13
"The generator sputters and dies: its tank is dry", a lamp reading "(no power)";
`12` "You rebuild the generator's worn parts…", tank dry; `13` the same after
"Game loaded.". From Creative: `03` the tank cell gone in dust with the staged can
on the hotbar, `09` the running generator, and `13` the dry tank after the load.
An Ashfall event darkened the night sky from 22:41 in both runs; it is
presentation here.

### Commands and portable builds

The JDK is the repository-local Temurin 25.0.4.1 in `build/tools/jdk25`:

```bash
export JAVA_HOME="$PWD/build/tools/jdk25/jdk-25.0.4.1+1" PATH="$JAVA_HOME/bin:$PATH"
./gradlew.bat build --console=plain > build/m19-c1-build.log 2>&1   # c2, c3 likewise
(cd build/m14-check && ./gradlew.bat build --console=plain) > build/m19-c4-build.log 2>&1
./gradlew.bat test --tests "com.veylon.UrbanUtilityQaSceneTest" -i --console=plain > build/m19-tour2.log 2>&1
graphify update . --no-cluster > build/m19-graphify.log 2>&1
# Closing the gate (2026-10-29): the final build, then the native runs (Creative from the worktree).
(cd build/m14-check && ./gradlew.bat build --console=plain) > build/m19-final-build.log 2>&1
VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_GAME_MODE=survival VEYLON_SCENE=urban_utilities \
  VEYLON_RESOLUTION=1280x720 VEYLON_CAPTURE_TAG=m19-utilities-survival-2 \
  ./gradlew.bat run -PveylonDataDir=build/qa/m19-utilities-survival-2 --console=plain > build/qa-m19-utilities-survival-2.log 2>&1
# Creative likewise from build/m14-check with VEYLON_GAME_MODE=creative, the tag m19-utilities-creative and an
# absolute -PveylonDataDir under the main checkout's build/qa.
```

- Focused classes while iterating: `UrbanTapsTest`, `UrbanPowerTest`,
  `UrbanPurifierTest`, `UrbanUtilitiesSectionTest`, `UtilitySoundsTest`,
  `UrbanUtilitySustainabilityTest`, `RuntimeBoundsTest`,
  `SerializedEnumOrderTest`, `UrbanMaterialsTest` (126 of 160 layers),
  `WorldGeneratorFingerprintTest` and the dispatch, revision and selector tests.
- The portable builds above, each `BUILD SUCCESSFUL` with no failures.
- `graphify update . --no-cluster` on the final code: 10,769 nodes and 44,168
  edges, with an extractor warning for `settings.gradle`
  (`m19-graphify.log`). No clustering or semantic extraction ran.

### Limits and next gate

- The gate is met: the portable build on the tour's code and the native tour in
  both modes passed on 2026-10-29 (above). Package 19 is COMPLETE.
- The tour stages poses between legs, a forward-only clock, the weather, one
  infected and a kit, and builds through the crafting screen's own command; it
  is scripted evidence, not the human playtest deferred by D-80.
- The native tour shows the evening's wear rebuilt, not a seized generator;
  seizing, the load cap, reach, removal, dormancy, pause, clogging, spills and
  survivors' pipes rest on the headless tests above.
- Balance values are initial values for 23; the radio workstation is 20;
  machinery ambience is 22.
- No reviewer subagents ran.

Next: package 20.

## Milestone 20 — urban missions and long-term purpose (2026-10-29 to 2026-10-30)

### Scope and baseline

- Branch `feature/urban-survival` at `ef29fe8` (the 19 records), clean tree.
  Package 19's gate was closed first (`ff1edff`). Read the revised base, the
  prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and these records.
- Generator 19 (layout 16, template 7, content 3, encounter catalogue 5) is new,
  with two appended blocks (`RELAY`, `RADIO`, ids 95–96), four appended items
  (Transmitter Module, Power Cells, Antenna Cable, Radio Workstation), four
  texture layers (130 of 160), no audio buffer, the section `urban.missions` v1
  and radio rows in `urban.utilities` v1 for generator 19 only. No existing
  ordinal, persisted id or other section version changed, and no earlier
  generator's behaviour. The default new world is unchanged.
- Generators 1–18 keep their absolute pins. Generator 19 was pinned at
  introduction on three seeds: its terrain, wreck, site and compound plans and
  the stamped chunks under the nearest wreck equal the pins generators 17 and 18
  share, and the relay's plan and the stamped chunk holding its cabinet have
  their own (D-197).

### Behaviour, persistence and mission evidence

Every commit was validated in the detached worktree `build/m14-check`, checked
out at the commit before it and given exactly the staged diff, whose tree was
compared with the main index (`git write-tree`) before the build and committed
unchanged after it.

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `71cf2ca` | Full portable build: 2,174 tests in 256 classes, one existing skip (`m20-c1-build.log`) | `RelaySitesTest` (4): every design seed has one relay on an unheld precinct roof, stamped in any chunk order with its roof still walkable, none before generator 19, and a cabinet that cannot be broken, blasted, burnt or taken from the palette; the fingerprint, dispatch, revision, selector, enum-order and slice tests extended to generator 19. |
| `6d5a317` | Full portable build: 2,190 tests in 259 classes, one existing skip (`m20-c2-build.log`) | `UrbanCommsTest` (6): the rumour names a district, never the building; only the survivor's body at the relay finds it; each part in the first roll of its real sources and recovered when taken; a stronghold's module in a restricted stash; codes only from a friendly stronghold or trusting civilians; nothing in earlier cities. `UrbanBroadcastTest` (4): fitting takes a pry tool and the parts (Creative spends nothing); a powered radio in the refuge carries the call, heard (by nobody in Creative), kept through a power cut, and the city answers once, a save and load changing nothing; without codes four times as long, and nothing out unrepaired or away from a refuge; no radio wired in earlier cities. `UrbanMissionsSectionTest` (the facts' round trip and rejections). `BeaconEndgameIntegrationTest` unchanged. |
| `cc874ac` | Full portable build: 2,200 tests in 260 classes, one existing skip (`m20-c3-build.log`) | `UrbanMissionsTest` (8): a camp's missions in turn, each naming a real object once; clinic medicine only from the named clinic after accepting, paid once; the hidden people's mission visiting their refuge; the Wardens' module by price or friendship and a theft failing it; failures explicit and final; the landmark only by the body on the roof; camp water only on the camp's wiring; no missions in earlier cities. `UrbanMissionsSectionTest` (every mission field, malformed rows failing the whole load). `RuntimeBoundsTest` (four in hand, records capped at 256). |
| `3888a28` | Full portable build: 2,201 tests in 261 classes, one existing skip (`m20-c4-build.log`) | `UrbanMissionRouteTest` (1): on seed `20260731` the objective from rumour to the city's answer through the city's own containers, wrecks and furniture, its people and the relay, with no log, coal, ore, sulfur or saltpeter in the pack; the hidden refuge behind its panel and the Wardens' restricted stores; four missions completed and rewarded once across a save and load, no second ending. |
| `b8967b8` | Full portable build: 2,204 tests in 262 classes, one existing skip (`m20-c5-build.log`); before it `UrbanMissionQaSceneTest` on the same code (`m20-tour-test-2.log`): Survival and Creative pass, 15 captures each, about 118 and 128 simulated seconds; generator 18 refused | The scripted tour below, headless, with the ending card's Escape answered as `Game.frame` does. |

### Native mission tour, both modes

GPU and driver from the run's `[gl]` line: NVIDIA GeForce RTX 3060 Ti, driver
610.88, OpenGL 3.3.0 with KHR_debug; 1280 × 720.

```bash
export VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_GAME_MODE=survival
export VEYLON_SCENE=urban_missions VEYLON_RESOLUTION=1280x720 VEYLON_CAPTURE_TAG=m20-missions-survival-3
./gradlew.bat run -PveylonDataDir=build/qa/m20-missions-survival-3 --console=plain
```

Each QA directory was checked not to exist beforehand. Seed `20260731` gives
these places (from the `plan` line): the camp `urban:building:0,0:53:0` met at
(177, 41, 93); the stronghold `urban:building:1,2:112:0`, its gate street (416,
41, 702) and its module in the stash at (386, 41, 749) worked from (387, 41, 749);
the power cells' tool chest at (7, 41, −269); the antenna cable's utility locker
at (−31, 37, −13); the relay on `urban:building:1,2:49:0`, its cabinet at (427,
61, 583) and stand at (428, 61, 583); in the starter refuge
`urban:building:0,0:44:0` the bench at (73, 41, 82), the generator at (79, 41,
94) put down from (78, 41, 92) and the radio at (76, 41, 88) from (76, 41, 90).
The camp's mission named the hidden refuge whose basement hall is at (42, 37, 45).

First run (`qa-m20-missions-survival.log`, tag `m20-missions-survival`): every
leg passed and the scene printed `[urban-missions] pass=true` with 15 captures,
each `glErrors=0 khrErrors=0`, but its report did not ask the harness to close,
so the session stayed open and was stopped; it has no shutdown summary. The
report now calls `finishScriptedRun()` like the other tours.

Second runs, with that call: Survival (`m20-missions-survival-2`) and Creative
(`VEYLON_GAME_MODE=creative`, `m20-missions-creative`) both passed with the
results below (117 and 127 route seconds, Gradle exit 0 after 2 min 13 s and
2 min 19 s). Two helpers of the tour were then tidied without changing what it
does (an unused mode check removed, the gatekeeper lookup rewritten), so both
runs were repeated on the tour commit's exact code.

Final runs (2026-10-30, the tour commit's exact code): Survival
(`m20-missions-survival-3`) and Creative (`m20-missions-creative-2`), both from
the main checkout. Survival printed `[urban-missions] pass=true` with 10 staged
poses, 15 captures and full health in 117 route seconds; every capture reports
`glErrors=0 khrErrors=0`, the log `glGetError=0`, 18 KHR messages with 0 errors
(the usual MEDIUM pixel-transfer notes) and `nativeErrors=0`; Gradle exited 0
after 2 min 7 s (`qa-m20-missions-survival-3.log`). Creative printed `pass=true` with 10 poses, 15 captures and full health
in 127 route seconds, the same error-free summaries (18 KHR messages, 0 errors)
and exit 0 after 2 min 23 s (`qa-m20-missions-creative-2.log`); its
`before-save` and `state` lines match, the city answering at game minute 778.40.

The Survival `deeds` line: the rumour ("There's an old relay mast on a rooftop in
the Civic Precinct south of here…"); the three missions (the Northlight Clinic &
Pharmacy to the east, people under the street to the west, the mast); the hidden
civilians reached past a neighbour and given six supplies ("Thank you. Thank you.
This buys us days."), the mission ready; the ward a friend after three handovers
(trust 54), its codes ("The ward kept the relay's codes…") and its module offered
for six supplies or as a friend, taken from (386, 41, 749) and reported ("Use the
module well. The ward doesn't give twice."); the cells from (7, 41, −269) and the
cable from (−31, 37, −13); the relay found (the mast's mission ready); the
generator and radio built and put down at (79, 41, 94) and (76, 41, 88); the call
on the Wardens' codes for 60 seconds of generator time, the city answering at
game minute 763.21; the camp's reports ("You found them…", "It still stands?…").
The `before-save` and final `state` lines match: rumoured, found, all three parts
recovered, both fitted, codes from the stronghold, the radio built, 60.00 seconds
broadcast, complete; the clinic's mission active, the hidden people's, the
mast's and the Wardens' completed and rewarded.

Inspected captures, all fifteen across the three Survival runs, which show the
same frames: `01` Teodor of the camp
with "[9] Ask about the old radio relay" and "[0] Ask if they need help", the
rumour and "Comms / recover the relay's parts: 0 of 3", the map line "Rumoured
relay - 585m S"; `02` "Missions 3/4 / Medicine from the clinic" and the three
logged missions, the navigation line on the clinic; `03` the slid panel and the
hidden people at the end of the cellar passage; `04` Fenna of the hidden refuge,
"You give 6 supplies to the hidden refuge.", "Mission ready: The people below.";
`05` Gideon, "Guarded stronghold (friends)", trust 54, the codes; `06` the
Wardens' kitchen cupboard, "Stronghold stores: shared with you as a friend |
Searched", the module on the hotbar, "Mission ready: The Wardens' transmitter";
`07`–`08` the tool chest and utility locker with their parts; `09` the relay's
cabinet under its mast, "Relay mast: needs antenna cable and power cells",
"Mission ready: The old relay mast"; `10` "Relay mast: repaired, listening for a
radio" and "Comms / build a radio workstation at a workbench"; `11` the radio in
the refuge's kitchen, "Comms / broadcasting: 5 of 60 s"; `12` the ending card,
"THE CITY ANSWERS", "Enter or Esc: continue surviving"; `13` the same room after
Escape, "Comms / restored: the city answered", "Word of your call reaches 2 of
the survivors' holds you know"; `14` Teodor's "It still stands? Then maybe it can
still talk.", "Missions 1/4"; `15` the same after "Game loaded.". From Creative (its first and final runs): `06` the Wardens' cupboard shared as
with a friend, `11` the parts, generator and fuel can still on the hotbar
(Creative keeps what it holds) and no salvage hint on the radio, `12` the card
marked "Creative world", `13` the radio after Escape, and `15` the same state
after the load.
An Ashfall event darkened the sky from 11:52 in Survival; it is presentation
here.

### Commands and portable builds

The JDK is the repository-local Temurin 25.0.4.1 in `build/tools/jdk25`:

```bash
export JAVA_HOME="$PWD/build/tools/jdk25/jdk-25.0.4.1+1" PATH="$JAVA_HOME/bin:$PATH"
# Each commit: its staged diff applied with `git apply --index` in build/m14-check, trees compared, then
(cd build/m14-check && ./gradlew.bat build --console=plain) > build/m20-c1-build.log 2>&1   # c2–c5 likewise
./gradlew.bat test --tests "com.veylon.UrbanMissionRouteTest" -i --console=plain > build/m20-route.log 2>&1
./gradlew.bat test --tests "com.veylon.UrbanMissionQaSceneTest" -i --console=plain > build/m20-tour-test-2.log 2>&1
VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_GAME_MODE=survival VEYLON_SCENE=urban_missions \
  VEYLON_RESOLUTION=1280x720 VEYLON_CAPTURE_TAG=m20-missions-survival-3 \
  ./gradlew.bat run -PveylonDataDir=build/qa/m20-missions-survival-3 --console=plain > build/qa-m20-missions-survival-3.log 2>&1
# Creative likewise with VEYLON_GAME_MODE=creative and the tag and directory m20-missions-creative-2;
# the earlier runs used the tags m20-missions-survival, m20-missions-survival-2 and m20-missions-creative.
graphify update . --no-cluster > build/m20-graphify.log 2>&1
```

- Focused classes while iterating: `RelaySitesTest`, `UrbanCommsTest`,
  `UrbanBroadcastTest`, `UrbanMissionsTest`, `UrbanMissionsSectionTest`,
  `UrbanUtilitiesSectionTest`, `UrbanPowerTest`, `UrbanPurifierTest`,
  `BeaconEndgameIntegrationTest`, `RuntimeBoundsTest`, `SerializedEnumOrderTest`,
  `UrbanMaterialsTest` (130 of 160 layers), `WorldGeneratorFingerprintTest`, the
  dispatch, revision and selector tests, `UrbanMissionRouteTest` and
  `UrbanMissionQaSceneTest`.
- The portable builds above, each `BUILD SUCCESSFUL` with no failures.
- `graphify update . --no-cluster` on the final code: 11,169 nodes and 46,334
  edges, with an extractor warning for `settings.gradle` (`m20-graphify.log`). No clustering or semantic extraction ran.

### Limits and next gate

- The gate is met: every code commit's portable build, the acceptance route and
  the native tour in both modes passed. Package 20 is COMPLETE.
- The tour stages poses between legs, a forward-only clock, the weather and a
  kit, and builds through the crafting screen's command; the route stages poses
  and puts down infected that come near. Both are scripted evidence, not the
  human playtest deferred by D-80.
- The tour reports two missions; the clinic's medicine, the camp's water, the
  Wardens' price, failures, the band scan, the refuge's codes and a power cut
  rest on the headless tests above.
- Balance values are initial values for 23; a radio's own sound and the answer
  as audio are 22; the map and onboarding for the objective are 21.
- No reviewer subagents ran.

Next: package 21.

## Milestone 21 — map, HUD and onboarding (2026-10-30)

### Scope and baseline

- Branch `feature/urban-survival` at `e764168` (the 20 records), clean tree.
  Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md`,
  these records, `docs/graphics/HUD_VALIDATION.md` and the save-compatibility
  skill.
- No generator, layout, template, content revision or encounter catalogue
  changed, and no block, item, texture layer, icon or audio buffer was added. The
  one new state is the optional section `urban.visits` v1, written for generator
  19 only. The default new world is unchanged (generator 3).
- Generators 1–18 keep their map, HUD and first-session lines; the corrections of
  D-209 (the 580 px interaction box, lock lines, a city's "Pry Tool", the closed
  door's take-apart hint, wrapped answers, the storage screen, a city's pause
  menu) reach them where their screens appear. Wilderness wording such as
  "Requires pickaxe" is unchanged.

### Behaviour, persistence and onboarding evidence

Every code commit was validated with the full portable build on its exact tree
in a detached worktree. The first two used `build/m14-check`, checked out at the
commit before them and given exactly the staged diff. The last four were built
in parallel in `build/m21-check` (a new worktree), `build/m13-check`,
`build/m12-check` and `build/m14-check`, each assembled from the commit before
the series plus the earlier changes of the series and its own; every tree was
compared with the main index (`git write-tree`) and committed unchanged.

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `4ee6b89` | Full portable build: 2,212 tests in 264 classes, one existing skip (`m21-a-build.log`) | `UrbanVisitsTest` (5): a fresh city knows only the refuge it wakes in and its district, in both modes; the street walks a district but enters nothing, a room enters its building and is remembered for the sketch; an unloaded column and a generator-18 city record nothing; the ledger and the sketch memory stop at their caps, within the runtime snapshot's hard limits, and a restore refuses wrong kinds, duplicates and excess. `UrbanVisitsSectionTest` (3): round trip, empty and absent sections, nine damaged payloads refused with the live world kept, a generator-18 save writing none and refusing one. `RuntimeBoundsTest` unchanged. |
| `2749358` | Full portable build: 2,215 tests in 265 classes, one existing skip (`m21-b-build.log`) | `UrbanMissionRumourTest` (3): the hidden people's mission marks their city block, not their building, until the panel is slid aside, then their refuge; the relay's mission marks its district until the survivor stands beside it, a ready mission its givers; the navigation line marks rumours; the Wardens are not called a camp. `UrbanMissionsTest`, `UrbanCommsTest` and `MapScreenTest` unchanged. |
| `8f0d89b` | Full portable build: 2,227 tests in 268 classes, one existing skip (`m21-c2-build.log`; two earlier drafts of the drawing passed it too, `m21-c-build-first.log` and `-second.log`) | `ui.CityMapTest` (6): a new survivor sees home and their refuge only, no site, no objective, the journal's radio step and first steps; entering a building names it, the cheap family agreeing with the plan; hidden people stay off the map until their panel is slid aside; a found camp, its rumour and two missions fill the map and the journal, a destroyed dispensary fails its mission with its reason, and a save and load show the same journal and markers; earlier worlds keep their map and no map screen pauses the world. `ui.FloorSketchTest` (4): real stairs and ways out, never a container, only rooms entered; a door opened in the world is open on the sketch; a hidden cellar and its panel only once found; no sketch outside. `ui.CityMapLayoutTest` (2): every panel on screen and apart at five logical sizes; journal text wraps in the bundled face. `HudLayoutTest` (51) and `HudTextTest` unchanged. |
| `d762cdf` | Full portable build: 2,232 tests in 270 classes, one existing skip (`m21-d-build.log`) | `UrbanInteractionPromptsTest` (4): a city calls the pry tool what its prompts ask for, the wilderness keeps "Requires pickaxe"; locked doors name the control and any tool and offer no take-apart hint; a container's line names its use, owner and cost, its progress and its searched state, touching nothing, the camp's stash warning of theft; the lines fit the 580 px box and a long answer wraps whole. `ui.CrateScreenToneTest` (1). The lock-wording expectations of `UrbanCivicGameplayTest`, `UrbanFixtureGameplayTest` and `UrbanFortificationTest` updated with the behaviour; `HudLayoutTest` keeps every module apart with the wider box. |
| `1df1e21` | Full portable build: 2,237 tests in 272 classes, one existing skip (`m21-e-build.log`) | `ui.CityOnboardingScreensTest` (2): NEW FRONTIER describes the city only for a city session, keeps the replace notice and Survival as the default; the pause menu keeps the wilderness reference and gives a city its own, naming the relay only where there is one; every line fits. `UrbanFirstSessionHintsTest` (3): a new city's lines in each mode, earlier cities' unchanged, each whole on the HUD's log beside its timestamp; the controller opens the city variant when the session makes cities. `UrbanDevelopmentSelectorTest` and `ModeSelectionRoutingTest` unchanged. |
| `dd121c1` | Full portable build: 2,240 tests in 273 classes, one existing skip (`m21-f-build.log`) | `UrbanOnboardingQaSceneTest` (3): the tour passes headlessly in both modes, 14 captures and 49 simulated seconds each, and refuses generator 18. |

### Native onboarding tour and screens

GPU and driver from the runs' `[gl]` lines: NVIDIA GeForce RTX 3060 Ti, driver
610.88, OpenGL 3.3.0 with KHR_debug; windowed, from the main checkout on its
final code.

```bash
export VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_SCENE=urban_onboarding
export VEYLON_GAME_MODE=survival VEYLON_RESOLUTION=1280x720 VEYLON_UI_SCALE=1
export VEYLON_CAPTURE_TAG=m21-onboarding-survival-720-2
./gradlew.bat run -PveylonDataDir=build/qa/m21-onboarding-survival-720-2 --console=plain
```

Each QA directory was new. Seed `20260731` gives (from the `plan` line): the
starter refuge `urban:building:0,0:44:0`; the flats beside it
`urban:building:0,0:40:0`, their ground-floor wardrobe at (65, 41, 66) and a
first-floor cupboard at (65, 45, 66); the starter district's nearest locked
wooden door at (40, 41, 68), in a clinic; the camp `urban:building:0,0:53:0`.

Two scratch runs (`m21-onboarding-try1`, `-try2`, Survival 1280 × 720) passed
before the map's commit; their captures showed the faults fixed then (the stair
labels colliding, truncated room names, the district label under an edge marker,
clipped edge labels, "[M]" in the journal). A first matrix on the code before
the last map fix passed in all six configurations; its map captures showed the
slot skin's teal corner under the journal's and the sketch's headings (cyan on
teal at 0.75 and 1.5 scale), so the cards took the HUD's panel skin and the six
tours were rerun. The new-world and pause captures of that matrix stand, as
nothing they draw changed.

Final tours (2026-10-30), each `[urban-onboarding] pass=true` with 2 staged
poses, 14 captures, full health and 49 route seconds; every capture
`glErrors=0 khrErrors=0`, and the shutdown summary `glGetError=0`, 17 KHR
messages, 0 KHR errors (the usual MEDIUM pixel-transfer notes); Gradle exit 0:

| Tag (`build/qa/<tag>/screenshots/`) | Mode | Framebuffer | Requested → effective scale | Wall time |
| --- | --- | --- | --- | --- |
| `m21-onboarding-survival-720-2` | Survival | 1280×720 | 1 → 1 | 72 s |
| `m21-onboarding-creative-720-2` | Creative | 1280×720 | 1 → 1 | 61 s |
| `m21-onboarding-survival-720-scale075-2` | Survival | 1280×720 | 0.75 → 0.75 | 61 s |
| `m21-onboarding-survival-1080-2` | Survival | 1920×1080 | 1 → 1 | 64 s |
| `m21-onboarding-survival-1080-scale150-2` | Survival | 1920×1080 | 1.5 → 1.5 | 63 s |
| `m21-onboarding-creative-1080-scale075-2` | Creative | 1920×1080 | 0.75 → 0.75 | 63 s |

Screens, one capture each at 3 s, 1280 × 720, scale 1, `glGetError=0` and 0 KHR
errors: `m21-newworld-city` (`VEYLON_FRONTEND=newworld-save` with the selector),
`m21-newworld-wilderness` (without it), `m21-pause-city`, `m21-pause-city-creative`
and `m21-pause-wilderness`.

The Survival `deeds` line: the rumour ("There's an old relay mast on a rooftop in
the Civic Precinct south of here…"); "Our dressings are gone. The Northlight
Clinic & Pharmacy to the east had a dispensary…"; "Some nights we hear people
under the street to the west…". The `views` line: the wardrobe's line "[F] Search
the wardrobe (noisy, about 4 s)", "Abandoned property | Searching 26%" and
"| Searched", the HUD's "Flats · floor 1", the locked door's "Locked. Hold [LMB]
to force it (loud), or go around", and the journal's headings at each map. The
`before-save` and final `state` lines match in both modes: district (0, 0)
walked; four buildings entered (the refuge, the flats, the clinic before the
locked door and the camp's); the journal COMMS, then the clinic's and the people
below's missions open.

Inspected captures, by run. The scratch runs drew the HUD, storage, prompt and
conversation screens with the final code and the map with an earlier draft:
`try1` `01`, `02`, `04`, `07`, `08`, `09`, `10` and `13`; `try2` `07`, `10`, `12` and
`14`. The first matrix (final code but for the map's cards): Creative 0.75 at
1080p `10`, Survival 1.5 at 1080p `05` and `07`, Survival 0.75 at 720p `13`, and the
new-world and pause captures but `m21-pause-city-creative`. The final reruns:
Survival 1280 × 720 `02` and `10`, Survival 1.5 at 1080p `07` and Creative 1280 ×
720 `01`. What they show: `01` the refuge at 08:02, the field panel's "Reclaimed
Residential" and "Flats · ground floor · your refuge", the five first-session
lines whole on the log (Creative its four), "[M] Map & journal"; `02` the city map
with the courtyard blocks and streets near the refuge, the refuge tinted and named
"Flats", home ringed white, the journal's radio step and first steps, and the
sketch of the woken-in storey with "stairs up / down" and the storey strip; `04`
"WARDROBE", "Abandoned property", an amber bar at 26 % with its noise and reach
notes and the pack's wear and freshness rails; `05` its contents (fibre, a vest
with its wear rail, bandages) and "Searched"; `07` the storey upstairs with the
bedroom entered, the corridor's doors and the strip lit at 1; `08` "Locked. Hold
[LMB] to force it (loud), or go around" under "Wooden Door", whole in the wider
box; `09` Teodor of the camp, the rumour wrapped over two lines inside the panel;
`10` the map at the camp: the camp marker, both flats named, "Medicine from the
clinic 122 m E" at the edge, "Rumour: The people below ~160 m NW" and "Rumour: Old
relay mast ~585 m S", the journal's three entries with "For the camp · ~160m NW
(rumour)"; `12` the map at night, as legible as by day; `13` the HUD in fog at
23:06 ("Mild Season, 6 days left / Fog"), small but whole at 0.75; `14` the map
after "Game loaded.", unchanged. At 0.75 the map's text is small but whole; at
1.5 the map, journal and sketch keep their layout. The new-world capture with the
selector shows "THE ABANDONED CITY / development build", the city's descriptions,
the default note and the replace notice; without it the wilderness screen is
unchanged. The city pause menu names the world and gives the city's keys and
goals; the wilderness pause menu is unchanged.

### Commands and portable builds

```bash
export JAVA_HOME="$PWD/build/tools/jdk25/jdk-25.0.4.1+1" PATH="$JAVA_HOME/bin:$PATH"
# 4ee6b89, 2749358: the staged diff applied with `git apply --index` in build/m14-check, trees compared, then
(cd build/m14-check && ./gradlew.bat build --console=plain) > build/m21-a-build.log 2>&1   # m21-b-build.log likewise
# 8f0d89b, d762cdf, 1df1e21, dd121c1: their trees assembled in build/m21-check, build/m13-check,
# build/m12-check and build/m14-check and built in parallel the same way
#   (m21-c2-build.log, m21-d-build.log, m21-e-build.log, m21-f-build.log)
./gradlew.bat test --tests "com.veylon.UrbanOnboardingQaSceneTest" -i --console=plain > build/m21-tour-test.log 2>&1
bash m21-natives.sh      # the first matrix: six tours and five screens (qa-m21-natives.log)
bash m21-natives-2.sh    # the six tours again, tags ending -2 (qa-m21-natives-2.log)
graphify update . --no-cluster > build/m21-graphify.log 2>&1
```

The two scripts (kept in the session scratchpad, not the repository) run, per
configuration, `VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731
VEYLON_SCENE=urban_onboarding` with the mode, `VEYLON_RESOLUTION`,
`VEYLON_UI_SCALE` and `VEYLON_CAPTURE_TAG=<tag>`, and `./gradlew.bat run
-PveylonDataDir=build/qa/<tag> --console=plain > build/qa-<tag>.log`; the screens
use `VEYLON_FRONTEND` and `VEYLON_SHOT=3` instead of the scene.

- Focused classes while iterating: the new classes above, `RuntimeBoundsTest`,
  `UrbanMissionsTest`, `UrbanCommsTest`, `MapScreenTest`, `HudLayoutTest`,
  `HudTextTest`, `HudQaSceneTest`, `UrbanCivicGameplayTest`,
  `UrbanFixtureGameplayTest`, `UrbanFortificationTest`, `UrbanSalvageTest`,
  `UrbanSurvivorSitesTest`, `UrbanRefugeClaimsTest`, `UrbanStationTest`,
  `ContainerCloseKeyRoutingTest`, `UrbanDevelopmentSelectorTest`,
  `ModeSelectionRoutingTest`, and the scene tests of the catalogue, refuge,
  utility, slice, survivor and mission tours with `UrbanMissionRouteTest`.
- The portable builds above, each `BUILD SUCCESSFUL` with no failures.
- `graphify update . --no-cluster` on the final code: 11,423 nodes and 47,599
  edges, with extractor warnings for `build.gradle` (no symbols) and
  `settings.gradle`. No clustering or semantic extraction ran.

### Limits and next gate

- The gate is met: every code commit's portable build, the headless tour in both
  modes and the native captures across the HUD matrix passed. Package 21 is
  COMPLETE.
- Scripted evidence, not the human playtest deferred by D-80: whether a new
  player finds their way was checked by tests, logs and captures, not judged by a
  person.
- The storage screen's item description needs the mouse, so no capture shows it;
  requested 1.5 at 1280 × 720 is clamped to 1 by the renderer and was not a
  separate run.
- The map reaches 128 m and only where the city was generated near the survivor;
  the sketch draws only the storey underfoot and remembers rooms for the session.
- With a usable item in hand the interaction line shows the use button's action
  only.
- No reviewer subagents ran.

Next: package 22.

## Milestone 22 — ruined-city visuals and sound (2026-10-30)

### Scope and baseline

- Branch `feature/urban-survival` at `728c47b` (the 21 records), clean tree.
  Read the revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md`,
  these records, `docs/graphics/ART_DIRECTION.md`, `ASSET_PIPELINE.md`,
  `ASSET_PROVENANCE.md` and `docs/audio/AUDIO_DESIGN.md`.
- No generator, layout, template, content revision, encounter catalogue, block,
  item, icon, enum ordinal or save section was added or changed (D-219). Added:
  13 texture layers (143 of 160), 19 audio buffers (167 in all, 43,443,964 PCM
  bytes), four transient acoustic zones. The default new world is unchanged.
- Host: Windows 11, NVIDIA GeForce RTX 3060 Ti, driver 610.88, OpenGL 3.3.0 with
  KHR_debug, the repository-local Temurin 25.0.4.1; windowed 1280 × 720 natives
  from the main checkout with isolated data directories.

### Before: the atmosphere tour at its own commit

`6903ac8` added `VEYLON_SCENE=urban_atmosphere` and nothing else visible, so its
captures are the "before" set (`build/qa/m22-before-survival`, `-creative`):

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_atmosphere'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m22-before-survival'
Remove-Item Env:VEYLON_SHOT -ErrorAction SilentlyContinue
.\gradlew.bat run -PveylonDataDir=build/qa/m22-before-survival --console=plain
```

Both modes printed `[urban-atmosphere] pass=true ... poses=24 captures=24`, every
capture `glErrors=0 khrErrors=0`, `nativeErrors=0`, and
`[audio] occlusion peakRays=0/4`: not one positioned sound played in the tour.
The listener report: every interior (kitchen, upper floor, stair hall, basement,
hidden cellar) `zone=STONE_STRUCTURE`; `crickets=0.80` in the hidden cellar and
the kitchen at night; rain 0.27 upstairs against 0.60 in the street. The
captures: the quarantine's streets like any clean street; the kitchen floor
beside its window dark and glossy in rain; the stronghold gate, cellar and
growth as package 21 left them.

### Behaviour evidence and portable builds

Every code commit was validated with the full portable build on its exact tree
in a detached worktree (`build/m21-check`, `m12-check`, `m13-check`,
`m14-check`), assembled from the commit before the series plus the series' own
changes in order; every tree was compared with the main index (`git write-tree`)
and committed unchanged with `git read-tree`. Up to four builds ran in parallel.

| Commit | Checks on the exact tree | What they prove |
| --- | --- | --- |
| `6903ac8` | Full portable build: 2,243 tests in 274 classes, one existing skip; `FixtureStatesSectionTest` failed only to delete its temp directory and passed on rerun; `build -x test` passed (`m22-c1-build.log`, `-rerun.log`, `-rest.log`) | `UrbanAtmosphereQaSceneTest` (3): the tour reaches and reports all its views on the design seed in both modes, staging changes no block, the places are the ones meant; it refuses generator 18 |
| `99a8f24` | Full portable build: 2,246 tests in 275 classes, one existing skip (`m22-c2-build.log`) | `world.urban.UrbanBlockLightTest` (3): a torch lights its side of a wall and not the other, doorways, open leaves and glass pass light, closed doors stop it, no ray squeezes between blocks meeting at an edge, the wilderness is unchanged; `UrbanApartmentQaSceneTest` with the porch torch no longer lighting the hall |
| `2877256` | Full portable build: 2,247 tests in 275 classes, one existing skip (`m22-c3-build.log`) | `UrbanDistrictPresenceTest` (5): the toxic fog drawn as a haze downtown, as murk with the vignette in the cordon, as before in the wilderness |
| `9b48d0b` | Full portable build: 2,252 tests in 276 classes, one existing skip (`m22-c4-build.log`) | `UrbanAcousticsTest` (4): at the tour's real places the street is open, rooms rooms, the stair hall and a station halls, the cellar and basement basements, the track a tunnel; insects outdoors in the housing, faint through a kitchen window, none in the cellar or downtown; rain loudest in the street, softer upstairs, a trace below; the reach ordering. `engine.AudioEnvironmentTest` (5) adds the city zones' ordering; the indoor and underground environment tests expect the new zones |
| `ea0e6a2` | Full portable build: 2,277 tests in 278 classes, one existing skip (`m22-c5-build.log`) | `engine.CitySoundsTest` (3): the bank comes last from its own seed, every buffer clean and distinct, the hum at its mains pitch, metal ringing above rubble, the throb low; `engine.FootstepSurfaceTest` (3): every wilderness block keeps its step, the city's surfaces, loudness ordered like the AI's hearing |
| `eabf5cc` | Full portable build: 2,282 tests in 279 classes, one existing skip; `UrbanContainersSectionTest` failed only to delete its temp directory and passed on rerun; `build -x test` passed (`m22-c6-build.log`, `-rerun.log`, `-rest.log`) | `CitySoundscapeTest` (5): district sounds, a resting Veilbound's breath and its silence once hunting, one bark per turn, the per-pass and follower bounds, forgetting a world; two sessions with different presentation streams play differently and simulate identically frame by frame |
| `a97d86d` | Full portable build: 2,283 tests in 280 classes, one existing skip (`m22-c7-build.log`) | `gfx.model.InfectedTellTest` (1): the veil glows at rest and brightens through the windup for both infected kinds; a person has no veil |
| `d7a3a0a` | Full portable build: 2,284 tests in 281 classes, one existing skip (`m22-c8-build.log`) | `gfx.CityEnvironmentTest` (1): the city flag and murky water in a city, neither in the wilderness or a city without generated buildings |
| `8d0dcbf` | Full portable build: 2,290 tests in 282 classes, one existing skip (`m22-c9-build.log`) | `world.urban.UrbanDressingTest` (6): sparse decals in their districts, unbroken rooted ivy, the loose panel's exact parity with plaster, subtle grades, nothing on glass, doors, floors or ceilings, every tile within the layer budget |
| `6a13d71` | Full portable build: 2,294 tests in 283 classes, one existing skip (`m22-c10-build.log`) | `UrbanTracesTest` (4): every site's mark near the spawn sits on a real wall cell facing the street, each line fits the one-line prompt, a mark is read through normal play, older layouts have none; `UrbanAtmosphereQaSceneTest` reads the door mark's words in both modes |
| `a90ca1c` | Full portable build: 2,295 tests in 283 classes, one existing skip (`m22-c11-build.log`) | `CitySoundscapeTest` (6) adds a stronghold guard fighting an infected (no bark), going for the survivor (one bark) and standing down |
| `96d7c4a` | Full portable build: 2,295 tests in 283 classes, one existing skip (`m22-c12-build.log`) | `gfx.model.InfectedTellTest` (1) pins the resting glow at 0.1 or below |
| `bfe6e5c` | Full portable build: 2,295 tests in 283 classes, one existing skip (`m22-c13-build.log`) | `world.urban.UrbanDressingTest` (6) requires a clear four-pixel border round every pinned-up decal; the old painter fails it at the poster's left edge |

Against the §5 matrix: the always set and row 22's `engine.*` audio and `gfx.*`
tests ran in every build above; its new evidence is the material census and
dressing tests, the model test, the zone, mix and soundscape tests and the
soundscape's reset (`CitySoundscapeTest`); its native evidence is the before and
after captures below. Its manual listening is the pending human check.

Two JUnit runs (`FixtureStatesSectionTest` at `6903ac8` and
`UrbanContainersSectionTest` at `eabf5cc`) failed only to delete their
`@TempDir` on Windows ("Failed to delete temp directory", no assertion failed)
while four builds and the native runs shared the machine; each class passed on
rerun in its worktree, and `build -x test` (Javadoc, assemble, check) passed
there.

The two docs commits (`4f6299e`, `21cbb0d`) and these records changed
documentation only: relative links and anchors were checked and
`git diff --check` was clean; no build was needed for them.

### After: the atmosphere tour on the final code

The same command with `m22-final-*` tags, from the final code tree:

| Run | Data directory | Result | Audio |
| --- | --- | --- | --- |
| Survival, EFX | `build/qa/m22-final-survival` | `pass=true poses=25 captures=25`, 25 captures `glErrors=0 khrErrors=0`, 143 of 160 layers | 167 buffers, 43,443,964 PCM bytes, 10 zones, no voice steals, occlusion peak 1/4 rays, `nativeErrors=0`; mean update 0.030 ms (one 11.9 ms spike while four builds shared the host) |
| Creative, EFX | `build/qa/m22-final-creative` | The same | The same, mean update 0.026 ms, peak 2.4 ms |
| Survival, dry route (`VEYLON_NO_EFX=1`) | `build/qa/m22-final-dry` | The same | `EFX unavailable; dry playback enabled.`, no occlusion filters, cues played (`shutter_bang:2,masonry_fall:1,pipe_knock:1`), `nativeErrors=0` |
| `urban_infected_apartment`, Survival | `build/qa/m22-final-infected` | `[urban-infected] pass=true`, 6 shots clean | 4/4 occlusion rays at peak, `nativeErrors=0` |

The earlier after-runs from the tree before the glow and poster fixes
(`build/qa/m22-after-survival`, `-creative`, `-dry`, `m22-infected-apartment`)
passed the same way and are kept for comparison.

What the 25 views show against the 24 before:

- **Streets (01–06, 15, 20–22, 24).** Residential brick with a mossy foot and
  ivy columns; commercial grime with posters and notices; industrial soot and
  rust; the flooded quarter's murky water and tide line; the
  civic precinct clean. The quarantine checkpoint (06, 21) stands on a boundary
  avenue whose fence panels face the camera on their unmarked side (a probe found
  15 cordon marks on the 35 fence panels there); the quarantine reads in its own
  courtyard (14, 19).
- **Apartments (07–11, 16, 23).** Rooms take `zone=ROOM` (the stair hall `HALL`,
  the basement `BASEMENT` with its 0.45 room tone), a little more daylight in the
  ground-floor rooms at noon, a dry floor beside the window in rain, rain at 0.12
  upstairs against 0.60 in the street, insects at 0.17 through the kitchen window
  at night.
- **Basement civilians (12, 17).** The hidden cellar takes `zone=BASEMENT`, no
  insects at night, the same people.
- **Stronghold (13, 18).** The gate as before; the notice beside it shows its
  paper only.
- **Quarantine growth (14, 19).** The veil's growth on the courtyard walls,
  cordon marks, and at night the contaminated ground's blooms and the node glow.
- **Door mark (25).** The chalk ringed dot beside the refuge building's door, and
  the line `Chalk by the door: a ringed dot. "Knock twice."`.
- **City sounds.** Over the tour the soundscape played distant masonry, metal and
  shutters and a pipe in the cellar (`cityCues=` per view); no tour view stands
  near a resting infected or a folded Veilmaw.

The dry route (`VEYLON_NO_EFX=1`) logged `EFX unavailable; dry playback enabled.`,
no occlusion line (no filters exist without EFX), the same 25 captures and the
city's cues, with `nativeErrors=0`.

The package-09 apartment scene (`VEYLON_SCENE=urban_infected_apartment`, shots
`0.7,3,4.1,8,14,17`) printed `[urban-infected] pass=true` and showed the
Veilbound's veil a little paler than its body by day (0.07 at rest after the
follow-up; 0.18 read almost white in the first after-run).

### Measurements

- **Block light.** Evaluating `World.blockLight` for every cell of the densest
  generated chunk near the spawn and the stronghold (one light within reach of
  its 3 × 3 chunks), y 30–69: 10,240 cells in 2.6–3.5 ms after warm-up, about
  0.3 µs per cell (a throwaway timing test, not kept).
- **Audio.** The native logs report 167 buffers and 43,443,964 PCM bytes (148 and
  42,325,588 before); synthesis and upload took 0.72 s before and 0.8 to 1.1 s
  after on this loaded host; audio update mean about 0.02 ms per frame, no voice
  steals, no native errors.

### Limits and next gate

- **Human checks (D-80).** No person looked or listened. The prompt's manual
  positional and indoor listening pass, with normal and dry-fallback devices, is
  the consolidated human playtest D-80 schedules now; it is pending, not passed.
  Signal tests prove waveform properties and scheduling policy only.
- **Not captured natively.** The infected's glow at night, the soundscape's tells
  (breath, throb, bark) and a lit lamp behind a wall; tests cover them.
- `graphify update . --no-cluster` on the final code: 11,670 nodes and 48,522
  edges, with the extractor warnings for `build.gradle` and `settings.gradle`.
  No reviewer subagents ran.

Next: the consolidated human playtest and listening pass (D-80), then package 23.

## Milestone 23 — survival economy and extended play (2026-10-30 to 2026-10-31)

### Scope and baseline

- Branch `feature/urban-survival` at `cf1a96f` (the 22 records), clean tree. Read the
  revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md` and these records.
- Added: generator 20 (layout 17), content revision 4, encounter catalogue 6, one salt
  (`UrbanSalts.STARTER_PICKED_OVER`), one QA scene. No block, item, icon, ordinal or
  save section (D-232). The default new world is unchanged.
- Host: Windows 11, NVIDIA GeForce RTX 3060 Ti, OpenGL 3.3.0 with KHR_debug, the
  repository-local Temurin 25.0.4.1; windowed 1280 × 720 natives from the main
  checkout with isolated data directories.

### Before: a census of generator 19

A throwaway census test (never committed) read every container of region (0, 0)'s
starter block and of the nine regions round the spawn through the production rolls on
the six design seeds (content 3):

| Measure | Seed 20260731 | −4242 | 7 | 0 | 987654321 | −9e18 |
| --- | --- | --- | --- | --- | --- | --- |
| Starter block food, waking days | 18.5 | 20.5 | 20.5 | 18.1 | 15.0 | 26.1 |
| Starter block pry tools | 28 | 26 | 27 | 27 | 27 | 22 |
| Refuge alone: scrap, planks, fibre | 25, 6, 4 | 22, 6, 16 | 21, 6, 4 | 23, 6, 4 | 27, 6, 4 | 21, 6, 22 |
| Pry tools in nine regions | 1,627 | 1,543 | 1,721 | 1,545 | 1,673 | 1,509 |

The refuge's own stores funded the reinforced spear (three scrap, two planks, two
fibre) on every seed, and the residential district's flats averaged about 2.8 servings
and 1.7 litres.

### Behaviour evidence

Each figure is printed by the test named and re-run by every build.

- `UrbanEconomyCensusTest` (content 4): starter block food 6.3, 7.0, 7.7, 4.9, 5.2,
  9.7 waking days and pry tools 7, 7, 6, 4, 4, 4 on the six seeds; the refuge's stores
  `{PLANK 6, DRIED_BERRY 3, WATERSKIN_CLEAN 1, BANDAGE 2, ANTISEPTIC 1, TORCH 1}` (fibre
  on two seeds), no scrap; stocked flats in nine regions 1.42–1.66 servings and
  0.57–0.65 litres, treatment in 52–58 % of bathrooms, 23–28 % of buildings picked
  clean, no spare part in a flat; two kinds of source for each necessity in every
  district.
- `UrbanEconomySalvageTest`: put down and taken apart, "Repair Workbench → 3 planks,
  2 scrap metal; Textile Bedroll → 4 fiber; Rain Collector → 2 scrap metal; Plank
  Barricade → 2 planks; Sheet-Metal Barricade → 1 sheet metal, 1 scrap metal; Rebuild
  Generator → 2 spare parts, 2 scrap metal, 1 sheet metal; Wire Work Lamp → 1 spare
  parts; Build Water Purifier → 1 sheet metal, 1 spare parts, 1 scrap metal; Build Radio
  Workstation → 1 transmitter module, 1 scrap metal"; generator 19's rebuilt bench
  still gives a spare part; a worn table comes apart under a standing hold.
- `UrbanEconomyTradeTest`: "generator 19 3 Scrap Metal for 1 Waterskin (clean water)
  to 4 people: sold 4 of 4, 0 left"; "generator 20 … sold 0 of 4, 4 left, last word
  'We can't spare water. What's left is what we drink.'"; "after 4 h of foraging: sold
  2 more, 4 left"; everyone fed and watered a day later.
- `UrbanEconomyCombatTest`: "pry tool, generator 19: damageTaken=0.0, seconds=2.3,
  blows=6"; "pry tool, generator 20: damageTaken=6.0, blows=6"; "reinforced spear,
  generator 20: damageTaken=0.0, seconds=0.95, blows=3".
- `UrbanEconomyEncounterTest`: "starter slots 2 (catalogue 5: 8), nearest 33.0, 32.0,
  29.2, 33.0, 33.0, 26.4 blocks from the refuge door"; catalogue 6 equals catalogue 5
  everywhere else.
- `UrbanEconomyAuditTest`: unheld part sources within three regions (modules 27, 16,
  43, 28, 22, 14 buildings; cells 306–411; cable 149–256), cars that did not burn within
  one region ring 32, 25, 27, 25, 30, 22; spawn region food against hunting 5,356/180,
  5,936/330, 5,470/210, 5,394/180, 7,236/60, 6,270/180 and fuel against park trees
  597,780/38,160 s … 575,940/39,000 s; two unplannable parcels reported and skipped.
- `UrbanEconomyRouteTest` (seed 20260731, Survival, accelerated headless days): the
  refuge's stores (no scrap) then the neighbour's ground floor funded the spear; the
  camp's rumour and three missions; "stole 11 … trust now −23", "stole 6 … trust now
  −37", a demand ("You'll have to come through us."); "every mission the camp gave
  failed: [CLINIC_SUPPLIES, HIDDEN_AID, LANDMARK]"; the armoury door forced; four
  engines, two tanks, two bodies and a locker taken apart; generator and radio built;
  cable and cells fitted; "the city answered on day 1" without codes; then four days:

| Day | Time | Health | Hunger | Thirst | Food in hand | Clean water | Searched that day |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 (game day 2) | 20:05 | 100 | 71 | 61 | 20 | 12 | 15 |
| 2 (game day 3) | 20:25 | 100 | 80 | 97 | 19 | 14 | 8 |
| 3 (game day 4) | 20:28 | 100 | 76 | 92 | 20 | 16 | 8 |
| 4 (game day 5) | 20:29 | 100 | 71 | 89 | 21 | 17 | 10 |

  A save and load after day 2 kept the objective, the failed missions and a searched
  container as they were; no block that was a tree or an ore vein changed.
- `UrbanSliceRouteTest`: the slice completes on generator 20 on every slice seed, one
  infected engaged and defeated each, the spear crafted.
- The earlier tours on generator 20 (a throwaway probe, never committed): `urban_missions`,
  `urban_onboarding`, `urban_utilities` and `urban_refuge` each printed `pass=true` when
  `urban-dev` hands them a generator-20 world, headless in Survival.

### Commands and portable builds

Every code commit was validated by the full portable build on its exact tree
in a detached worktree (`build/m12-check`, `m13-check`, `m14-check`,
`m21-check`, reset to `cf1a96f`, the staged tree applied and its `git
write-tree` hash compared with the main index before building), four at a
time, then committed with `git read-tree <tree>` and `git commit -F`:

| Commit | Tree | Tests | Classes | Build |
| --- | --- | --- | --- | --- |
| `e214f52` | `ea09a5d` | 2,302 | 284 | BUILD SUCCESSFUL in 27m 50s |
| `c382717` | `99cdf3b` | 2,304 | 285 | BUILD SUCCESSFUL in 27m 54s |
| `de7eff9` | `f13ec5d` | 2,306 | 286 | BUILD SUCCESSFUL in 27m 46s |
| `0873c1e` | `fd1fe0b` | 2,307 | 287 | BUILD SUCCESSFUL in 28m 1s |
| `6f60e54` | `32404c9` | 2,309 | 288 | BUILD SUCCESSFUL in 37m 59s |
| `b3de774` | `eac6050` | 2,313 | 289 | BUILD SUCCESSFUL in 39m 18s |
| `7d10039` | `3ad310f` | 2,314 | 290 | BUILD SUCCESSFUL in 39m 48s |
| `24540b0` | `a8b616b` | 2,317 | 291 | BUILD SUCCESSFUL in 39m 46s |

No test failed or errored in any of them (the existing Windows skip applies).
The second batch shared the machine with native runs. Before the tour existed,
the main checkout's full build (the `7d10039` tree plus documents) passed:
2,314 tests, `BUILD SUCCESSFUL in 17m 41s`.

Focused runs while iterating (`.\gradlew.bat test --tests ... --console=plain`):
the census, revision, meta-section, dispatch, selector, fingerprint, slice-route,
starter-progression, game-loop, containers-section and runtime-bounds classes;
then each new economy class with its neighbours (`UrbanSalvageTest`,
`UrbanSiteDialogueTest`, `UrbanMissionsTest`, `InfectedBehaviorTest`,
`BreacherBehaviorTest`, `UrbanEcologyPopulationTest`). They found, and these
commits fix: an additive district bias that erased flooded pry tools (now a
percentage), bathrooms holding treatment two times in three (now about half),
a reserve of two skins a mouth larger than a camp's first stock (now one), a
route that waited outdoors in the rain and counted a rested 00:34 waking as
morning, and a tour that pressed F three times a press and captured during a
load.

`graphify update . --no-cluster` on the final code: 11,846 nodes and 49,588
edges, with the usual extractor warnings for `build.gradle` and
`settings.gradle`. No reviewer subagents ran.

### Native extended play

Scripted runs of `VEYLON_SCENE=urban_economy` on the committed code
(`24540b0`), seed 20260731, generator 20 through `urban-dev`, 1280 × 720,
isolated data directories:

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_GAME_MODE='survival'; $env:VEYLON_SCENE='urban_economy'
$env:VEYLON_RESOLUTION='1280x720'; $env:VEYLON_CAPTURE_TAG='m23-economy-survival-2'
Remove-Item Env:VEYLON_SHOT -ErrorAction SilentlyContinue
.\gradlew.bat run -PveylonDataDir=build/qa/m23-economy-survival-2 --console=plain
```

**Survival** (`build/qa/m23-economy-survival-2`), 1,062 s of real time, the
day's ledger lines:

| Ledger | Day | Time | Health | Hunger | Thirst | Food | Clean water | Searched | Meals | Drinks | Nights |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| day 1 morning | 1 | 08:02 | 100 | 100 | 100 | 1 | 1 | 0 | 0 | 0 | 0 |
| day 1 evening | 1 | 20:30 | 100 | 79 | 65 | 5 | 5 | 7 | 0 | 0 | 0 |
| day 2 morning | 2 | 05:10 | 100 | 78 | 64 | 5 | 5 | 7 | 0 | 0 | 1 |
| day 2 evening | 2 | 20:30 | 100 | 73 | 87 | 7 | 4 | 11 | 1 | 2 | 1 |
| day 3 morning | 3 | 05:31 | 100 | 73 | 86 | 7 | 4 | 11 | 1 | 2 | 2 |

- **Revision and seed:** `24540b0` (tree `a8b616b`), seed 20260731, generator 20,
  content revision 4.
- **Gear:** the starting kit (a serving, a skin of water, an empty skin, a
  bandage, a torch); a pry tool, scrap, planks and fibre from the neighbour's
  ground floor; the reinforced spear, crafted at 09:49 on day 1.
- **Choices:** the refuge's stores; the quiet apartment's inner door forced
  (`forced=BROKEN_DOOR`) and its locker, kitchen and bathroom searched; the
  corner shop's shelf; a skin filled at the rain collector each morning ("Filled
  a waterskin with clean rainwater"; no empty skin was left over for the tap and
  the fire); the days spent indoors; the nights slept in the refuge's bed, woken
  rested twice a night ("You wake after 2.0 hours") and back to sleep; on day 2
  the next block's flat `urban:building:0,0:72:0`, its street door the one staged
  pose, searched on its two lowest storeys; F5 and F9 with
  `before-save containers=11 food=6 clean-water=4 day=3` equal to `after-load`.
- **Failures:** none. No infected came within reach (engaged and defeated
  empty), and the refuge, never barred, read "4 ways in open" each night without
  drawing anything. A toxic-fog event ran outside the quarantine without harm.
- **Frames:** 11 captures, every one `glErrors=0 khrErrors=0`;
  `[audio] ... nativeErrors=0`. Reviewed: the refuge morning with the kit, the
  searched bathroom cabinet ("empty · hold LMB for 2 planks"), the spear at the
  bench, the rain collector (2.0/3.0 L), waking at dawn in the refuge, the next
  block's pantry upstairs, and the reloaded refuge on day 3 ("Game loaded",
  berries 75 % fresh).

**Creative** (`build/qa/m23-economy-creative-2`), 1,022 s of real time: the same
route with nothing consumed (health, hunger and thirst at 100 throughout, no
meal or drink taken), the inner door broken outright (`forced=AIR`, as a
Creative break is), food 8 and water 5 in hand on day 3,
`before-save containers=11 food=8 clean-water=5 day=3` equal to `after-load`;
11 captures with zero GL and KHR errors and `nativeErrors=0`.

A first pair of runs on an earlier tree of the tour (`build/qa/m23-economy-survival`,
`-creative`) passed the same way but captured `11-after-load` during the load's
transition, a black frame; the tour now waits 1.5 s and compares the saved
state before that capture, and the runs above are its evidence.

This is scripted native evidence of two and a half in-game days, separate from
the accelerated headless days of `UrbanEconomyRouteTest` and never a human
playtest (D-80).

### Limits and next gate

- Human extended play, listening and judgement of how the economy feels are pending
  under D-80; the scripted native run is not one.
- The two unplannable parcels (DESIGN §16.6) are packages 24 and 25's.

Next: the consolidated human playtest and listening pass (D-80), with package 23's extended play, then package 24.

## Milestone 24 — dense-city performance and streaming (2026-11-05)

### Scope and baseline

- Branch `feature/urban-survival` at `71ce6af` (the 23 records), clean tree. Read the
  revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md`, the performance and
  save-compatibility skills, `docs/PERFORMANCE_BENCHMARKS.md` and these records. The
  consolidated human playtest (D-80) has not run; this package went ahead on the owner's
  instruction to execute 24.
- Added: two instruments (the city profile, the native tour), three exact optimizations,
  a streaming schedule and bounded chunk retention (D-241 to D-247). No block, item,
  icon, ordinal, save section, generator or plan change; the default new world is
  unchanged.
- Host, the second machine: AMD Ryzen 5 5600X, 31.9 GB, Windows 11 Pro 10.0.26200,
  NVIDIA GeForce RTX 3060 Ti, OpenGL 3.3.0 NVIDIA 621.01 (driver 32.0.16.2101) with
  KHR_debug, the repository-local Temurin 25.0.4.1+1, launcher `-Xmx2G`; windowed
  1280 × 720 natives from the main checkout with isolated data directories, VSync off,
  render distance 6, shadows 1, bloom and FXAA on, seed 20260731, no concurrent build.

### Before: measuring first

`cityPerformanceTest` on the instruments alone (`.\gradlew.bat cityPerformanceTest
--console=plain`: the profile of `ab42cb8` with the tour of `3438751`) and the native
tour on the same engine located the costs:

```powershell
$env:VEYLON_GENERATOR='urban-dev'; $env:VEYLON_SEED='20260731'
$env:VEYLON_SCENE='urban_performance'; $env:VEYLON_RESOLUTION='1280x720'
$env:VEYLON_VSYNC='0'; $env:VEYLON_GAME_MODE='survival'
.\gradlew.bat run -PveylonDataDir=build/qa/m24-before-survival --console=plain
```

(log `build/qa/m24-before-survival.log`; the after run used `m24-after-survival`).
Java Flight Recorder (`JAVA_TOOL_OPTIONS` with `-XX:StartFlightRecording=settings=profile`,
recordings under `build/m24-jfr/`) attributed them:

- streaming: light sampling during meshing about a quarter (`ChunkMesher.ensureLight`,
  `World.getChunk`, `World.blockLight`) and building planning about a quarter
  (`DistrictCatalogue.best`, `AccessibilityValidator`), with `HashMap$TreeNode.find`
  under `World.getChunk`: the chunk map's buckets had treeified;
- a long journey: building planning about half, and the renderer's chunk selections and
  their map iteration about a fifth by 8 km;
- the slow tick: a throwaway probe (never committed) timed each part at a street and a
  stronghold; `EntityManager.slowTick` (the encounter director) cost 10–34 ms on most
  ticks, every other part under 3 ms;
- the save: the sites, containers and population writers reading their bytes back with
  the load's checks, re-planning what the plan caches had dropped.

Every figure is in `docs/PERFORMANCE_BENCHMARKS.md` ("Dense city").

### Behaviour evidence and portable builds

Every code commit was validated by the full portable build on its exact tree in a
detached worktree (`build/m12-check`, `m13-check`, `m14-check`, `m21-check`, reset to
`71ce6af` and laid over by one script, its `git write-tree` hash recorded), four and
then three at a time, then committed with `git read-tree <tree>` and `git commit -F`:

| Commit | Tree | Tests | Classes | Build |
| --- | --- | --- | --- | --- |
| `ab42cb8` | `1d9d518` | 2,318 | 292 | BUILD SUCCESSFUL in 22m 1s |
| `3438751` | `e5312f1` | 2,320 | 293 | BUILD SUCCESSFUL in 22m 7s |
| `6babe85` | `54ff7cf` | 2,322 | 294 | BUILD SUCCESSFUL in 22m 4s |
| `da53372` | `ea12e60` | 2,323 | 294 | BUILD SUCCESSFUL in 20m 13s |
| `34b4c34` | `e13148f` | 2,323 | 294 | BUILD SUCCESSFUL in 18m 0s |
| `ee280ee` | `1d0a2c5` | 2,324 | 295 | see below |
| `0dd758c` | `51b119c` | 2,329 | 298 | BUILD SUCCESSFUL in 18m 33s |

No test failed in them but one at `ee280ee`: `save.UrbanEcologySaveTest` could not have
its JUnit temporary directory deleted (`DirectoryNotEmptyException`, the Windows cleanup
flake recorded since package 22) while three builds ran; run alone in that worktree it
passed its 5 tests, javadoc and check had passed, and `build -x test` succeeded. The
existing Windows skip applies throughout. The test sources of `ab42cb8` were compiled
again after its profile's guards were settled.

Focused runs while iterating (`.\gradlew.bat test --tests ... --console=plain`, the
classes read back from `build/test-results`): `ChunkRetentionTest` (3), `ChunkMapTest`
(2), `ChunkMesherFingerprintTest` (1), `ChunkStreamingScheduleTest` (1),
`entity.ChunkUnloadCachesTest`, `ai.NavigationCacheUnloadTest`,
`UrbanPerformanceQaSceneTest` (2), `qa.RuntimeBoundsTest` (19), `gfx.UrbanMaterialsTest`,
`qa.OrchestratorSizeTest`, `GameLoopIntegrationTest` and `WorldSeedDeterminismTest`. They
found, and the commits avoid: a tour that died of a fall when the journey's last pose
set a player carried at 90 blocks down on the street (the tour now resets fall state
while it carries the player), and twin worlds compared over chunks the evicting one had
not regenerated yet (both now generate the compared area first).

The fingerprint was recorded with the mesher forced to ask the world for every cell's
block light (the unoptimized path), then held with the shortcut: 5943656294887753686.
The twin journey: 3 km out at 21.6 blocks a second and back, one world dropping chunks
and one keeping them all; the evicting twin dropped over 1,000 chunks, never held more
than 2,050, kept the burning campfire's chunk and regenerated most of home, and its save
is byte for byte the keeping twin's; a fresh game loads it.

### After: measurements on the final code

- `cityPerformanceTest`, run by `.\gradlew.bat performanceTest --console=plain` (log
  `build/m24-final-performanceTest.log`): every target holds (streaming p99 6.47, 6.57
  and 7.64 ms against 8.3; retained chunks within the limit at every kilometre).
- The native tour (log `build/qa/m24-after-survival.log`): `pass=true`, 16 legs,
  `glGetError=0`, 0 KHR errors, audio `nativeErrors=0`.
- `performanceTest`: the calibrated gates pass but the durable save (5.879 ms against
  2.20, the host gap; 5.217 ms on `3438751`), which stops its test before `load`. With
  `-x cityPerformanceTest -x rainPerformanceTest` on both trees, then
  `PerformanceBenchmarkTest` alone twice on each, the chunk and entity ticks are the same
  on both trees; the whole-task difference came from test-class order
  (`docs/PERFORMANCE_BENCHMARKS.md`).
- Visual check of the schedule: `VEYLON_SCENE=urban_atmosphere` on the final code
  (`build/qa/m24-atmosphere-survival`, 25 captures, `pass=true`, 0 GL and KHR errors)
  framed the views of package 22's `build/qa/m22-final-survival`; the residential and
  flooded streets and the growth site, compared side by side, match but for grass sway
  and a minute of clock.
- The release smoke on a legacy world (`VEYLON_SEED=20260910`, `VEYLON_SMOKE=30`,
  1280 × 720, VSync off, `build/qa/m24-smoke-legacy.log`): passed, 1,495 FPS, p99
  0.88 ms, the fortress approach at 1,580 FPS, save and load, every hard limit, 0 GL and
  KHR errors, no chunk dropped.

`graphify update . --no-cluster` ran on the final code (PROGRESS). No reviewer subagents
ran.

### Limits and next gate

- Measured on the second machine only; the reference machine has not run the city
  profile, the tour or the calibrated gates for this package (D-245).
- First-touch planning is bounded by count, not time (D-247): 23–30 ms slow ticks in
  fast travel and 15–18 ms ones arriving somewhere new remain.
- Save and load time grow with the places a save names (D-246), package 25's to cut.
- The two unplannable parcels (DESIGN §16.6) are package 25's.
- Human checks under D-80 are unchanged by this package and still pending.

Next: package 25, the integrated save and regression audit, with the consolidated human
playtest and listening pass still due under D-80.

## Milestone 25 — integrated save and regression audit (2026-11-05 to 2026-11-06)

### Scope and baseline

- Branch `feature/urban-survival` at `40b110c` (the 24 records), clean tree. Read the
  revised base, the prompt, `AGENTS.md`, `docs/COMMIT_WORKFLOW.md`, the
  save-compatibility skill, every save codec and its tests, and these records. The
  consolidated human playtest (D-80) has not run; this package went ahead on the
  owner's instruction to execute 25.
- Added: a planner fallback for two unplannable parcels (D-248), write-side guards in
  the core (D-249), corrected codec documentation, an integrated urban fixture with
  its round-trip and corruption suites, genuine saves of v0.3.0, v0.8.1 and package 20
  (D-251) and a native save audit (D-252). No block, item, icon, ordinal, save
  section, generator, plan or content change; the default new world is unchanged.
- Host, the second machine: AMD Ryzen 5 5600X, Windows 11 Pro 10.0.26200, NVIDIA
  GeForce RTX 3060 Ti, OpenGL 3.3.0 NVIDIA 621.01 with KHR_debug, the repository-local
  Temurin 25.0.4.1+1; windowed 1280 × 720 natives with isolated data directories.

### The two unplannable parcels

A scratch sweep (compiled classes, no test) asked `UrbanWorldState.buildingAt` for every
parcel of seeds 20260731, −4242, 7, 0, 987654321, −9000000000000000000, 1, 42, 999 and
−1234567, regions −3..3 on both axes: 39,776 parcels a generator.

| Generators | Before | After |
| --- | --- | --- |
| 13, 14, 15, 16, 17, 18, 19, 20 | 2 unplannable each: seed 987654321 `urban:parcel:-2,-3:44` and seed −9000000000000000000 `urban:parcel:-3,0:116`, all three attempts `ROUTE: unreachable` at the opening's first cell | 0 on 20 and 13 (the others plan the same buildings) |

Diagnosis: both openings sit on the parcel's side edge (u = width − 1) of a 14- and a
13-cell building whose bulkhead's far wall stands at u = core + 4 = width − 2, rows 6–7
of 0–7, so the two parapet cells open onto the stair house's wall. The repaired plans
keep their variants (DAMAGED, OCCUPIED), storeys (4, 2) and basements, plan in the
fourth attempt, and fingerprint 7865289980778632171 and 6754843376405436927 on every
generator 13–20. The roof next door (parcels 42 and 114) keeps its opening and declares
its route; in a generated chunk its two cells are open and the repaired roof's are a
whole parapet with its rail (`RoofOpeningFallbackTest`). A catalogue scan of the same
seeds and regions finds those two openings facing a stair house and no other, and no
bridge opening does.

### Integrated fixture, corruption and limits

- The integrated save is 8,536 bytes with 24 sections; the city's are `world.fixtures`,
  `urban.population`, `urban.meta`, `urban.refuges`, `urban.containers`, `urban.sites`,
  `urban.compounds`, `urban.fortifications`, `urban.utilities`, `urban.missions`,
  `urban.discovery` and `urban.visits`. Its facts, as described after the save: a
  cupboard SEARCHED with four fibre left, a search at 0.6 s holding medicine, a
  container DESTROYED, a door open (state 1), a door barred (state 4) and braced at 70,
  a broken window, a breached wall, a barricade at 35, claims on the starter's ground
  floor and the flat's first floor (home), 2 litres drawn, a generator on with 296.25 s
  of fuel at condition 99.8 and a lamp lit, a dead Veilbound and a dead Veilmaw without
  bodies, the camp (trust 10), the hidden refuge (found, trust 8), the raider yard
  (taken, rewarded, four dead), the stronghold (trust 25), the comms rumour, two
  missions active and the hidden-aid mission completed and rewarded.
- `UrbanIntegratedSaveTest` (7): round trips with every extension section rewritten
  byte for byte and the core equal through a digest; revisits camp-first and
  flat-first with three rounds of dormancy (every site's people asleep far away and
  awake again one body each, the dead infected and Veilmaw never activated again, no
  report or reward paid twice); loads over the same city carried on, another seed's
  city and a legacy world; chunks generated forward, reversed, shuffled, dropped by
  retention and dropped entirely, the same hashes each time and the same as in the
  world the facts were made in; new worlds of the same and another seed equal to a
  fresh session; three failed loads over the populated world (a truncated file, a cut
  `urban.missions`, no `urban.containers`) leaving it, its save and the good file
  unchanged; four section orders.
- `UrbanSaveCorruptionTest` (6): each of the twelve city sections missing (all refused
  but `urban.visits`, whose absence loads as nothing visited), repeated, at its version
  ±1 and `Integer.MIN_VALUE`, cut by a byte, cut to three bytes, empty and followed by a
  stray byte; every first count at −1, one more than held, 2^20 and `Integer.MAX_VALUE`;
  bytes flipped at fixed points (refused 92, accepted as other valid state 13, each of
  those saved and loaded again); an unknown section skipped and dropped by the next
  save; a clock set back before the claims, refused by the writer with the last save
  byte for byte and no temporary file. Every refusal kept the live world, its player and
  the good file.
- `CorruptSaveResilienceTest` (16, three new): 100,001 discovered places refused by the
  writer and 100,000 saved and loaded (D-249); a file one byte over 128 MiB refused
  before any world is replaced; a slot path that is a folder holding a file refused at
  the final replacement with the folder untouched and no temporary file left.

### Genuine saves and older builds

Detached worktrees of `v0.3.0` (`bc11020`), `v0.8.1` (`615c146`) and package 20
(`e764168`); each tree's main sources (and package 20's test sources) compiled alone
with javac 25.0.4.1 against LWJGL 3.3.6 and JOML 1.10.8; a writer compiled against
those classes only and run with `-Dveylon.dataDir=build/m25-historical/<build>/data`,
which every run left empty.

| Fixture | Writer | SHA-256 | Bytes | What it holds |
| --- | --- | --- | --- | --- |
| `v0.3.0-authentic.sav.b64` | v0.3.0 | `b149f4611908dbe5620f6f495b9de49a2ff65ea9f7c1be22b715191a732576a3` | 44,606 | Seed 30017717, generator 3, day 4 13:25: needs, blueprints, an affliction, a crate, a campfire, a dug block, beacon stage 1, a discovered POI, the camp's FETCH request, 92 settlements of regions −6..6 with one hostile changed, reputation, a resident, a deer, a carcass, an event |
| `v0.8.1-survival-authentic.sav.b64` | v0.8.1 | `2a82e1f5a580ea6223b581d40e5bd5d5efd706e9427876962e388974e3f207c3` | 37,466 | The same staging on seed 81081928, 74 settlements |
| `v0.8.1-creative-authentic.sav.b64` | v0.8.1 | `0d1f33df0cd8a3ef5f6b9c0aecd54abb54ea39f43463d459e7cf4eafa53da7d3` | 6,236 | Seed 81081929, Creative, marked, flying, dusk frozen, weather locked, spawning paused, four planks placed |
| `urban-gen19-m20-authentic.sav.b64` | package 20 | `8678b640e2600491e4ebd939fbd2dcaf6749603f0b2dffad04c611c468671ad1` | 8,458 | The integrated fixture on generator 19 (content 3, no `urban.visits`), with package 20's fact locations and description |

The v0.3.0 writer ran the release's faction tick to open its quest offer, which let
the camp's alarm fall from 12 to 0 before the save; the test expects what the file
holds. `HistoricalV3SaveCompatibilityTest` (3) and `UrbanEarlierRevisionSaveTest` (1)
pass, and the v0.2.0 test still does.

The same archived classes then tried, over a live world of their own, three saves this
build wrote (`build/m25-historical/downgrade/`):

| Build | City, generator 20 | City, generator 19, saved by this build | v0.8.1 world saved by this build |
| --- | --- | --- | --- |
| v0.8.1 | refused: unsupported world generator version 20; live world kept | refused: version 19; live world kept | loaded |
| package 20 | refused: version 20; live world kept | loaded, `urban.visits` skipped | loaded |

The legacy world saved again by this build is 37,466 bytes, as the release wrote it.

### Native save audit

From the main checkout with the package's final code, each slot filled first:

```powershell
$env:VEYLON_SCENE='save_audit'
.\gradlew.bat run -PveylonDataDir=build/qa/m25-save-audit-<name> --console=plain
```

| Slot | Result | Timeline |
| --- | --- | --- |
| `city` (the integrated save) | `pass=true generator=20` | walk to the half-searched cabinet, its screen (search finished: medicine), pack, map, F5 and F9 (12 lines equal) |
| `v0.8.1-survival` | `pass=true generator=3` | walk to the release's crate, its screen (three bandages), pack, map, F5 and F9 (10 lines) |
| `v0.8.1-creative` | `pass=true generator=3 mode=creative` | no container within 24 blocks: a walk of 11 blocks; the catalog opened and closed with E; map; F5 and F9 (12 lines) |
| `v0.3.0-survival` | `pass=true generator=3` | walk to the release's crate, its screen, pack, map, F5 and F9 (8 lines) |

Every run: 0 `glGetError`, 0 KHR errors (7–8 medium performance notes), captures
`01-loaded` to `05-after-f9` checked by eye: the claimed flat at day 3 with the yard
taken and "Game loaded", the cabinet's screen, the city map with home, the found refuge
and the journal (two missions under way, "The people below" done), the release worlds'
crates, quest, camp standing, storm event and bleeding, and the Creative catalog at the
frozen dusk. A first city run showed the tour's own bug: it pressed the closing key in
the frame it captured, so the screens were already shut when drawn; the tour now waits
before closing, and the runs above are after the fix.

### Commands and portable builds

Every code commit was validated by the full portable build (`.\gradlew.bat build
--console=plain`, javadoc and check included) on its exact tree in a detached worktree
(`build/m12-check`, `m13-check`, `m14-check`, `m21-check`), each tree assembled by one
script from `40b110c` and the chosen file versions through a temporary index (its `git
write-tree` hash recorded) and laid in with `git read-tree -u --reset`, four at a time;
each was then committed with `git read-tree <tree>` and `git commit -F`, its tree checked:

| Commit | Tree | Tests | Classes | Build |
| --- | --- | --- | --- | --- |
| `5b4b6ad` | `d34b52e` | 2,332 | 299 | BUILD SUCCESSFUL in 21m 50s |
| `00f476a` | `0bd3d7a` | 2,333 | 299 | BUILD SUCCESSFUL in 21m 50s |
| `f9ef49b` | `df89d9e` | 2,333 | 299 | BUILD SUCCESSFUL in 21m 55s |
| `14f1bcb` | `80a8f96` | 2,340 | 300 | BUILD SUCCESSFUL in 21m 51s |
| `1ea0bce` | `69fe11c` | 2,348 | 301 | BUILD SUCCESSFUL in 20m 33s |
| `7695db4` | `6bfd3d1` | 2,351 | 302 | BUILD SUCCESSFUL in 20m 36s |
| `69b7753` | `3e8b347` | 2,352 | 303 | BUILD SUCCESSFUL in 20m 30s |
| `0041013` | `0bac6ca` | 2,355 | 304 | see below |

Each skipped one test, the existing Windows skip. At `0041013` every assertion passed, but
JUnit could not delete `save.UrbanEarlierRevisionSaveTest`'s temporary directory
(`DirectoryNotEmptyException`, the Windows cleanup flake recorded since package 22) while
four builds ran, so that build reported BUILD FAILED in 20m 46s; the class run alone on
the same tree passed (BUILD SUCCESSFUL in 3s) and `build -x test` completed javadoc and
check. The final code was then built once more alone in the main checkout: 2,355 tests in 304 classes, one skipped, BUILD SUCCESSFUL in 17m 29s.

Focused runs while iterating (`.\gradlew.bat test --tests ... --console=plain`, the
classes read back from `build/test-results`): `world.urban.RoofOpeningFallbackTest`,
`UrbanEconomyAuditTest`, `world.urban.RouteCatalogueTest`,
`world.WorldGeneratorFingerprintTest`, `world.urban.BuildingPlannerTest`,
`world.UrbanUndergroundGenerationTest`, the whole `save.*` package with
`UrbanIntegratedSaveTest` (BUILD SUCCESSFUL in 3m 46s, every class passing),
`save.HistoricalV3SaveCompatibilityTest`, `save.HistoricalV020SaveCompatibilityTest`,
`save.UrbanEarlierRevisionSaveTest`, `save.UrbanSaveCorruptionTest` and
`SaveAuditQaSceneTest`. They found, and the commits avoid: a byte-for-byte re-save check
that the core's hash-ordered edits cannot meet (the sections are compared instead), a
dormancy check that accepted a mission the check itself offered, an eviction check that
expected chunks the running generator pins, a v3 fixture check that expected a later
release's sections in a v0.3.0 save, the release fixture's alarm, which its writer's own
faction tick had lowered, and the tour's crate opened with F beside a carcass, which F
butchers first.

`graphify update . --no-cluster` on the final code: 12,157 nodes and 51,159 edges, with the usual extractor warnings for `build.gradle` and `settings.gradle`.

### Limits and next gate

- Human checks (D-80) of packages 09–23 are still pending.
- The writer's 128 MiB check has no test (D-249).
- Generator 2 was never a release default and has no genuine save; its fingerprints
  stand.
- Save and load time still grow with the places a save names (D-246, D-250); planning
  hitches (D-247) and the reference machine's rerun are unchanged.
- Next gate: package 26 (default urban rollout and final acceptance), after the
  consolidated human pass.

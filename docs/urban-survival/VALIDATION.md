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
| R-03 | CPU chunk retention and growth collections | B-17, B-29, B-30 | §4.3 | 11 (measure), 24 (decide) |
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
- package 11 records retained chunks and heap after the slice route;
- package 24 decides eviction or a finite metropolis (D-24, P-03) and proves that no state is lost.

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
| 05 | `ai.PathfinderTest` | Production validator on valid and intentionally invalid fixtures; template × rotation × seed matrix; shuffled order; bounded validator work | Optional plan-debug geometry |
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
| 24 | `qa.RuntimeBoundsTest` and allocation tests | Bounds, allocation and state; eviction and revisit order if eviction is chosen | `.\gradlew.bat performanceTest --console=plain` (opt-in, calibrated host) plus native city measurements |
| 25 | `save.*`, fingerprints, quest, settlement and parity tests | Integrated urban fixture across orders and revisits; corruption and atomic replacement | A genuine legacy world and the integrated urban state, from isolated saves |
| 26 | Everything relevant | Rollout path tests | Default-path smoke and the manual acceptance scenario from the pack README |

Test names in the table are relative to `com.veylon.`. Run them with `.\gradlew.bat test --tests "com.veylon.<name>" --console=plain`.

## 6. Seed matrix registry

| Set | Seeds | Frozen by | Status |
| --- | --- | --- | --- |
| Legacy pins | Gen 3: `20260731`, `-4242`, `7`. Gen 1 fixture: `1122334455`. Gen 2 deep-cave tests: `987654321`. Since 02, absolute gen-1 and gen-2 terrain fingerprints over `20260731`, `-4242`, `7` (recorded at `923345f` from unchanged code). | Existing tests; 02 | In force |
| Urban generator 4 (placeholder) | `20260731`, `-4242`, `7` (fingerprint); the six design candidates for the datum, dispatch and order checks (`world.UrbanGeneratorDispatchTest`) | 02 | In force, frozen with generator 4 |
| Urban design | `20260731`, `-4242`, `7`, `0`, `987654321`, `-9000000000000000000`, over the regions `(0,0)`, `(-1,-1)`, `(1,-1)`, `(-1,1)`, `(2,3)`, `(-3,-2)`, `(4096,-4096)` and `(-4097,4095)` (`world.urban.CityPlannerTest`, `world.UrbanStreetNetworkGenerationTest`); generator-5 fingerprints over `20260731`, `-4242`, `7` | 03 | **Frozen** (2026-10-08) |
| Starter | — | 08 | Not yet frozen |
| Slice regression | — | 11 | Not yet frozen |
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

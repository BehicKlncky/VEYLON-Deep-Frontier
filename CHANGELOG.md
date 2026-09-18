# Changelog

All notable user-facing changes to VEYLON: Deep Frontier are recorded here.

## [Unreleased]

### Changed

- Death ragdolls have articulated elbows, knees, head joints and linked tails;
  bird wings now flop. Joint limits replace the pose-restoring spring, while
  limb and torso contacts determine how a body lands and drapes over steps.
- Settled bodies retain their full joint pose through saves. Older body poses
  still load, and living silhouettes and animations retain their original shape.

### Added

- Close human, ledge and living-animation ragdoll QA scenes, joint-limit and
  collision regression tests, and a full-population ragdoll tick benchmark.

## [0.7.3] - 2026-09-18

### Changed

- The gameplay HUD uses larger, framed survival vitals with procedural icons,
  numeric values and explicit low/critical warnings. Nutrition, temperatures,
  wetness, fatigue, shelter and load are grouped, with larger affliction chips.
- All nine hotbar slots are larger, with clearer selection, stack counts,
  condition and freshness indicators, and held-item text.
- Weapon status, reload and bow-draw progress share a readable panel above the
  hotbar. Interaction prompts use keycaps; target and mining feedback have
  clearer backgrounds while retaining the crosshair's aim geometry.
- Context and mission cards organize time, weather, biome, camp, quests and
  navigation. Long text truncates safely; recent events stay above the status
  module without overlapping it.
- Creative has a matching status panel for flight, environment and shelter,
  without survival or medical pressure. Other screens and default UI scale are
  unchanged.

### Added

- Deterministic HUD showcase scenes for daylight, fog and night, plus headless
  layout, text-fitting and weapon-label regression tests.

## [0.7.2] - 2026-09-18

### Added

- Animals and people you kill now fall. A dying body is simulated as a jointed
  ragdoll that carries the force of the blow that killed it, tumbles, catches on
  ledges, slides down slopes, sinks in water and comes to rest where the terrain
  puts it — and only then becomes the carcass or body you walk up to.
- Human bodies stay in the world. Someone you kill leaves a corpse lying where
  they fell instead of vanishing mid-stride. Corpses cannot be harvested, are not
  seen or fought by anyone, and rot away over time like carcasses.
- A burst of blood at the moment of death, thrown upward and along the direction
  of the killing blow, with drips that follow the body while it is still moving
  and a lasting stain under it once it settles.
- A `death_ragdoll_showcase` capture scene, plus `death_ragdoll_sequence` for a
  single body, with frozen physics snapshots for repeatable screenshots.

### Changed

- Dead animals no longer snap instantly into one fixed pose at the exact spot
  they died. A carcass is drawn lying the way its body actually landed, facing
  the way it fell, and is saved that way. Carcasses in existing saves look
  exactly as they always have.
- An animal you shoot lands a little away from where you hit it, so a killing
  shot at a running deer reads as a killing shot rather than an instant stop.
- Harvesting waits for the body. The `[F]` prompt says a body is still falling
  rather than offering a carcass that does not exist yet; lodged arrows are
  carried through the fall and still come back when you skin it.
- Saving while a body is mid-fall settles it first, so a save can never lose one.

## [0.7.1] - 2026-09-17

### Added

- World-space rain with smooth wind, varied fall speeds and velocity-aligned
  streaks. Storms produce a denser, more wind-deflected field than ordinary rain.
- Swept voxel impacts on roofs, terrain, leaves and water. A drop terminates at
  contact and creates a small, short-lived ballistic spray, subdued on soft surfaces.
- A repeatable `physical_rain` QA sequence and headless physics, rendering-data,
  density, determinism, pool-budget and allocation/performance coverage.
- The project is now licensed under the PolyForm Noncommercial License 1.0.0
  (`LICENSE.md`). Noncommercial use, modification and sharing are allowed; commercial
  use requires written permission. The license ships inside every jar and archive.

### Changed

- Outdoor rain remains visible while sheltered; nearby loaded columns determine
  emission, independently of gameplay exposure. Deep cave columns are rejected.
- Removed predicted ground splashes and the legacy HUD rain overlay. Rain now
  belongs to the depth-tested scene; the existing snow behavior is preserved.
- Weather emissions reserve room for combat/fire effects in the unchanged
  4,000-particle pool and still use at most two instanced render submissions.

## [0.7.0] - 2026-09-16

### Added

- **Creative mode.** Choose Survival or Creative when you start a new frontier, or
  switch an existing world from the pause menu with `G`. The first switch to Creative
  asks for confirmation and permanently marks the world.
- In Creative you cannot be hurt or killed, and hunger, thirst, temperature, fatigue,
  carry weight and injuries no longer apply. The HUD shows a Creative badge instead of
  the survival bars.
- Wildlife and settlement residents ignore Creative players: no detection, pursuit,
  alarms or attacks, while the frontier keeps living around you.
- Creative flight: double-tap `Space` to take off or land, hold `Space` to rise, `Ctrl`
  to descend and `Shift` to fly faster.
- Creative catalog: press `E` to browse every item by category or search by name, take
  full stacks or single items, and delete unwanted stacks.
- Creative building: blocks break instantly without drops or tool wear, placing never
  uses up the held stack, and the middle mouse button picks the block you are looking at.
- In Creative, arrows, ammunition, bombs, food and medicine are never used up, and
  carried items keep their durability and freshness.
- The Creative catalog includes natural and structural blocks that had no item form
  before: grass, leaves, ice, basalt, stone brick, ruin stone, ores and wreckage.
- Creative world controls (`T` while paused): set the time of day, freeze the daylight
  cycle, choose and lock the weather, and pause wildlife spawning.

### Changed

- Reputation, ownership, theft and vandalism rules still apply in Creative; only the
  consequences that need an NPC to notice you are gone.
- Crafting, cooking, drying, smelting, fuelling, trade, gifts and quest deliveries keep
  their normal costs in Creative. Only actions that use an item up for its own effect
  are free.
- The pause menu names the world's mode and its permanent Creative mark.

### Quality

- Survival behaviour is unchanged: the same rules, the same RNG draw order and the same
  outcome for the same seed. 638 tests across 97 classes, and every performance budget
  is met at its 0.6.0 value.

### Compatibility

- The save format stays binary v3. Game mode, the permanent mark, flight and the world
  controls live in two new optional sections; a save without them loads as an unmarked
  Survival world, so every existing save keeps working.
- Older builds skip the new sections, so they open a 0.7.0 Creative save as Survival and
  discard the mark, flight and world controls if they save over it. A save holding the
  new Creative-only block items loads there too, but silently drops those stacks;
  blocks already placed in the world survive.

## [0.6.11] - 2026-09-15

### Added

- Creative world controls in the pause menu: set the time of day, freeze the daylight
  cycle, choose and lock the weather, and pause wildlife spawning.

## [0.6.10] - 2026-09-15

### Added

- The Creative catalog now includes natural and structural blocks such as grass,
  leaves, ice, basalt, stone brick, ruin stone, ores and wreckage.

## [0.6.9] - 2026-09-15

### Added

- In Creative, arrows, ammunition, bombs, food and medicine are never used up, and
  carried items keep their durability and freshness. Crafting, cooking, trading and
  station inputs still follow the normal rules.

## [0.6.8] - 2026-09-15

### Added

- Creative building: blocks break instantly without drops or tool wear, placing never
  uses up the held stack, and the middle mouse button picks the block you are looking at.

## [0.6.7] - 2026-09-15

### Added

- Creative catalog: press E in Creative to browse every item by category or search by
  name, take full stacks or single items, and delete unwanted stacks.

## [0.6.6] - 2026-09-14

### Added

- Creative flight: double-tap Space to take off or land, hold Space to rise, Ctrl
  to descend and Shift to fly faster.

## [0.6.5] - 2026-09-14

### Added

- Choose Survival or Creative when starting a new frontier.
- Switch an existing world from the pause menu with G. The first switch to Creative
  asks for confirmation and permanently marks the world.

## [0.6.4] - 2026-09-14

### Added

- Wildlife and settlement residents ignore Creative players: no detection, pursuit,
  alarms or attacks, while the frontier keeps living around you. Reputation and
  ownership rules still apply.

## [0.6.3] - 2026-09-14

### Added

- Creative worlds: the player cannot be hurt or killed and has no hunger,
  thirst, temperature, fatigue, carry-weight or medical pressure. The HUD
  shows a Creative badge instead of survival bars.

## [0.6.2] - 2026-09-14

### Quality

- Worlds now record a game mode in an optional save section. Existing saves load
  unchanged as Survival worlds.

## [0.6.1] - 2026-09-11

### Quality

- Survival behavior that Creative mode will interact with (damage, needs, mining,
  placement, ammunition, wear and hostile perception) is pinned by deterministic tests
  before any change.
- Recorded the Creative mode research, design decisions and milestone plan.

## [0.6.0] - 2026-09-11

### Added

- Audio controls on the title and pause menus: master, sound effects, ambience,
  music and mute, with live changes and saved preferences.
- Six sparse, fully synthesized musical phrases for exploration, night, nearby
  danger, deep caves, the first night and the repaired beacon. Long stretches of
  silence leave room for the frontier; music can be disabled completely.
- Optional reverb for open land, forest, shallow underground, deep caves,
  shelters and large stone structures.

### Changed

- All sounds now run at 44.1 kHz, with filters tuned in real frequency units.
- Weather uses longer layered beds, wide independent emitters, irregular gusts
  and separately scheduled details. Intensity changes the balance of rain body
  and sizzle, not just volume.
- Frequent footsteps, block hits, impacts, swings and firearm reports rotate
  through four independently generated takes.
- A 24-voice priority pool gives injury, explosions and alarms precedence over
  footsteps, fading an occupied voice before reusing it.

### Fixed

- Fire ambience comes from the nearest audible fire as you move around it.
- Walls muffle positional sounds and distance absorbs high frequencies when EFX
  is available. Sheltered rain loses its outdoor brightness.
- Thunder comes from the lightning strike after a distance-based delay and grows
  darker and quieter with distance.
- Procedural buffers receive click-safe edges, DC correction and peak headroom.
  Old-world thunder, music and pending sounds are cleared on world replacement.

### Quality

- Deterministic headless audio tests cover signal safety, spectra, durations,
  variants, voice priority, weather queues, settings and sparse music.
- Startup, PCM payload, ray work and music scheduling have measured budgets.
  Native smoke runs exercise audio, save/load, voice pressure and graphics limits.
- Every milestone passed its full build and documentation gate. Listening-based
  tuning still needs human acceptance; automated checks do not establish taste.

### Compatibility

- Every sound and musical phrase is generated by project code. No recordings,
  sound fonts, decoding library or other dependency was added.
- Missing audio devices remain safe; devices without EFX use dry playback.
- Save format v3, generator version 3, persisted enum order, combat and AI rules
  are unchanged. Java 25, LWJGL 3.3.6 and OpenGL 3.3 remain the baseline.

## [0.5.11] - 2026-09-10

### Changed

- Sparse, fully synthesized musical phrases respond to exploration, night, danger, deep caves and the beacon, with long periods of silence.

## [0.5.10] - 2026-09-10

### Changed

- Adjust master, sound effects, ambience and music live from title or pause menus, with saved levels and a mute toggle.

## [0.5.9] - 2026-09-10

### Changed

- Thunder now arrives from the lightning strike after a distance-based delay; sheltered rain loses its outdoor brightness.

## [0.5.8] - 2026-09-10

### Changed

- Explosions, injury and alarms take precedence over footsteps in a larger voice pool, with brief fades before a busy voice is reused.

## [0.5.7] - 2026-09-10

### Changed

- Frequent footsteps, impacts, swings and firearm reports rotate through four independently generated takes.

## [0.5.6] - 2026-09-10

### Changed

- Solid terrain muffles positional sounds through bounded voxel occlusion, while air absorption softens distant reports.

## [0.5.5] - 2026-09-10

### Changed

- Forests, shelters, ruins and the cave depths now have distinct smoothly changing reverberation, with automatic dry fallback on devices without EFX.

## [0.5.4] - 2026-09-09

### Changed

- Weather surrounds the listener with independent directions, and nearby fueled fires now crackle from their world position.

## [0.5.3] - 2026-09-09

### Changed

- Rain and wind now change texture with weather strength; long continuous beds surround irregular droplets, fire details, cave drips and night insects.

## [0.5.2] - 2026-09-09

### Changed

- Audio now uses 44.1 kHz synthesis with frequency-calibrated filters and corrected wildlife chirp phase.

## [0.5.1] - 2026-09-09

### Fixed

- Sounds now retain headroom instead of flattening loud peaks, with softened
  boundaries and removed DC offset to reduce clicks.

### Quality

- All 48 procedural sounds can be tested without an audio device. The new
  harness checks durations, amplitude, seams, spectra and silent playback.
- Recorded the original audio measurements and the Signal & Silence upgrade plan.

## [0.5.0] - 2026-07-31

A stability and technical-baseline release. No new content; existing saves and
worlds are unchanged.

### Fixed

- A corrupt or truncated save no longer crashes the game. Five readers in the
  save format used a number taken straight from the file as an array index or a
  list capacity, so a file damaged by a power loss during a quick save could
  take the running game down with it — after the live world had already been
  released. Dangerous counts, ordinals and player scalars are now validated,
  and a damaged file fails the load with a message instead.
- Save publication is failure-atomic: a complete payload is flushed to a
  sibling temporary file before it replaces the destination. A failed write
  therefore keeps the previous save intact, and a failed quick load validates
  in isolation before it can replace the live session.
- Unsupported world-generator versions are rejected at the save boundary
  instead of being interpreted as current terrain rules.
- Bleeding, sprains, wound infection, food and water poisoning, sleep sickness,
  toxic-fog sickness and break drops now replay from the world seed. Fourteen
  gameplay rolls used the global random number generator, which meant the
  survival layer's entire failure model was the one part of the game a seed did
  not reproduce.
- An under-drawn bow shot no longer slows an arrow that is already in flight.
  Once 96 projectiles were airborne, the draw-power adjustment was applied to
  the newest earlier arrow instead of the one just fired.
- A save that loads with a non-finite player value is now rejected rather than
  producing a character who cannot die, heal or eat.

### Changed

- Loading a world is about 27% faster; the simulation tick is 18–24% cheaper.
  World terrain is bit-identical, so existing saves are unaffected.
- `Game` is down from 1,496 lines to 886, with world setup, the automated-run
  driver, the front-end states, hotkey routing and the player-environment tick
  extracted into named collaborators. No gameplay rule moved.

### Quality

- 302 deterministic tests across 61 classes, up from 266 across 55.
- Generated terrain is now pinned against recorded fingerprints, so a refactor
  that reshapes worlds fails the build instead of silently rewriting the ground
  under existing saves.
- A per-system tick profile joins the benchmark suite, and the wall-clock
  baselines were lowered to match the new figures rather than left where a
  regression to the previous release would still pass.
- A source-size budget holds `Game` under 1,000 lines.
- AI decision randomness is owned by each `Game` instance rather than three
  mutable static generators, preserving the seeded sequences without allowing
  validation or test instances to perturb one another.
- `javadoc` doclint is clean and is now part of the ordinary `check`/release
  build rather than an optional broken task.
- The Gradle wrapper pins the official 9.1.0 binary distribution checksum; a
  substituted build-tool archive now fails verification before execution.
- The fixed-seed 30-second OpenGL release smoke passes at 1600x900 with save/load
  complete, all hard limits respected and zero GL/KHR errors.

### Compatibility

- Save format v3 and world generator version 3 are unchanged. Existing saves
  load with identical terrain.
- Persisted enum order is unchanged.
- Java 25, OpenGL 3.3 Core, LWJGL 3.3.6 and the dependency set are unchanged.

## [0.4.1] - 2026-07-30

### Fixed

- Starting or loading a world now clears the player's melee cooldown, so the first
  swing cannot be silently consumed by combat state from the previous world.
- New-world reset now clears weather lightning/flash state and cached heat-source
  positions from the world being replaced.

### Changed

- Converted the ordered 13-branch event roll ladder into an explicit
  `EventDefinition` table while preserving cumulative bounds, season bias,
  rejected-rung fall-through and the empty-camp illness exception.
- Extracted off-screen food, illness, morale and replenishment rules into
  `DormantSettlementSimulation`. Replenishment uses its own world-seeded RNG stream;
  its distribution is unchanged, but a given seed's interval sequence differs from
  0.4.0.
- Added `SimulationSystem` cadence contracts and moved time, weather and temperature
  world-reset ownership onto the systems themselves.
- Reduced internal UI-screen and debug-toggle fields on `Game` from public to
  package-private. `Game` remains the application composition root; these fields were
  not a supported external API.

### Quality

- Added characterization coverage around all event-roll rungs before the data-driven
  conversion, plus focused dormant-settlement and simulation-reset contract tests.
- Added production-path integration coverage for the complete beacon endgame,
  save/load during a partial repair and melee cooldown reset.
- The portable deterministic suite contains 266 tests across 55 classes. Four
  hardware-calibrated wall-clock benchmarks run separately through
  `performanceTest`, keeping platform packaging independent of single-machine
  budgets.

### Compatibility

- Binary save version remains v3; historical v0.2.0/v2 and original v3 saves retain
  their existing migration paths.
- World generator version remains 3, serialized enum order is unchanged and existing
  world edits and beacon progress continue to load.
- OpenGL 3.3 Core, Java 25, LWJGL 3.3.6 and the dependency set are unchanged.

## [0.4.0] - 2026-07-29

### Changed

- Reduced `Game` from 4,380 to 1,489 lines by extracting nine focused
  collaborators for QA automation, combat, block actions, world interaction,
  consumables, crate transactions, prompts, ambience and sleep. Existing `Game`
  commands remain as compatibility delegates for input, UI, saves and tests.
- Moved player and simulation tuning values into eight focused constants classes.
  Weather-specific values now live on the `Weather` enum so new weather states must
  supply their complete configuration.
- Added architecture and development guides covering initialization, tick ordering,
  save/load contracts, extension recipes, deterministic RNG rules and known rough
  edges.
- Java sources are normalized to LF through the repository attributes policy.

### Fixed

- Every simulation RNG reachable from `Game` is now reseeded from the world seed with
  an independent salt. Recreating a seed after another world has run in the same
  process no longer inherits hidden random state.

### Quality

- Expanded the deterministic suite from 223 to 240 tests across 51 suites. New
  coverage exercises tick-bucket ordering, world replacement/reset behavior,
  save/load through production tick paths, projectile pool bounds and complete
  simulation RNG reseeding.
- Added reflection-backed protection that fails when a newly introduced simulation
  `Random` is omitted from world reseeding.

### Compatibility

- Binary save version remains v3 and the historical v2 migration path is retained.
- World generator version remains 3; existing saves and seed output are unchanged.
- Serialized enums retain their existing order and append-only contract.
- OpenGL 3.3 Core, Java 25, LWJGL 3.3.6 and the dependency set are unchanged.

### Known limitations

- `EventSystem` still selects events through an ordered 13-branch roll ladder.
  Converting it to data-driven rules requires boundary and precondition regression
  tests because failed conditions deliberately fall through differently.
- The proposed nine interfaces, nine facades and service locator were not added.
  That shape would retain the existing `Game` coupling while hiding dependencies
  behind another delegation layer; future interfaces should follow concrete
  substitution or testing needs.

## [0.3.1] - 2026-07-22

### Fixed

- Settlement crate transfers now separate bounded event and transfer identities, so
  duplicate callbacks cannot repeat effects, distinct same-type stacks all update stock,
  and one logical theft applies reputation/alert penalties only once.
- Bounded A* reconstruction now returns failure when its defensive cache cap is reached
  instead of returning a reversed path suffix disconnected from the NPC's start.
- Voxel DDA raycasts correctly hit an adjacent voxel when starting exactly on a boundary.
- Settled NPC perception no longer decrements its schedule twice per simulation tick.
- Player collision now supports a tested one-voxel step while rejecting taller ledges.

### Changed

- Extracted headless player movement/voxel physics, medical treatment and high-level
  gameplay action routing from `Game`; `Game.updateActions()` is now orchestration only.
- Added caller-owned mutable raycast results to the per-frame target and fluid queries;
  the immutable compatibility API remains available for occasional callers.
- Runtime light emitter changes update one chunk-list entry instead of rescanning every
  cell; bulk generation/load retains the safe full rebuild.
- Chunk meshing reuses per-face scratch arrays, projectile objects return to a strict
  bounded pool, and OpenAL listener/ambience update arrays are retained.
- Human LOS evaluation keeps the documented deterministic 0.3-second cadence.

### Quality

- Expanded the deterministic suite from 160 to 223 tests. New coverage includes player
  stepping/collision/falling/swimming/ladders/chunk edges, DDA axes/boundaries/fluids/
  result reuse, theft transactions, reconstruction limits, fire and afflictions, water,
  temperature, shelter, plant growth, coordinate safety and performance invariants.
- Added the v0.3.1 engineering validation matrix and release notes.

### Compatibility

- Save format remains binary v3 and historical v2 migration remains supported.
- World generator version remains 3; existing seeds and generated output are unchanged.
- Serialized enums remain append-only with no reordered or removed constants.

## [0.3.0] - 2026-07-17

### Added

- **Procedural settlements**: deterministic regional planning places at most one
  settlement per 384-block region — camps and homesteads (25%), common villages
  (45%), uncommon forts (18%), rare castles (9%) and very rare fortresses (3%) —
  with minimum-separation rules for castles/fortresses, a guaranteed friendly
  starter area and no hostiles beside spawn. Modular biome-adapted construction:
  timber palisades in pine forest, stone in the highlands, stilted platforms in
  the marsh, clay in scrubland; houses, storage, workshops, medic huts, trader
  stalls, wells, farms, watchtowers, walls, gates, barracks, prisons, powder
  magazines, two-story keeps, campfire squares and connecting paths.
- **Living residents**: every settled human has a home settlement, an archetype,
  a bed and a duty point. Residents sleep at night, work, patrol, guard gates,
  heal the wounded, investigate sounds, raise alarms, defend their walls, flee
  when morale breaks, eat from real stocks and produce role-specific supplies —
  and are simulated abstractly (stocks, hunger, illness, mortality, replenishment,
  morale) while the player is away. Population never insta-respawns.
- **Headhunter faction** with distinct silhouettes and roles: trackers that read
  your footprints and share your position, bow scouts that sprint for the alarm
  bell, spear hunters, armored brutes on the gates, musket powdermen guarding
  the magazine, and leaders whose death breaks garrison morale.
- **Capture and siege**: forts and larger holds require command neutralization,
  alarm suppression and central-objective control in addition to defeating or
  routing the garrison. Supply the cleared campfire with food and logs to claim a
  **friendly outpost** with safe beds, storage and limited trade. Expect
  counterattacks; distant raids resolve abstractly against your garrison.
  Fortresses offer staged assaults: outer walls, gatehouse, courtyard, inner
  wall, keep, powder magazine, prison with rescuable captives and a concealed
  rear breach for infiltration. A regional **bounty** system sends Headhunter
  hunting parties out from real settlements when you make enemies. Parties travel
  outbound, search, retreat/return and let surviving scouts report contact.
- **Deep caves 2.0** (new worlds): three depth identities measured below the
  local surface — root caves (roots, dirt intrusions, animal dens), basalt
  depths (sulfur, saltpeter, extra iron, glow fungus, larger chambers, smoking
  **fumarole** vents that build up the smoke affliction until mined out) and
  rare resonant depths with crystal formations — carved from density noise plus
  domain-warped worm tunnels; guaranteed reserved ladder-shaft **cave mouth** and
  bounded deterministic POI connectors per region; new
  underground POIs (abandoned mine, smuggler cache, Headhunter hideout, resonant
  shrine, gloomstalker nest, lost expedition camp). Lakes remain sealed.
- **Gloomstalker ecology**: nests claim dark territory with visible bone
  evidence, stalkers ambush laterally from darkness, refuse to chase brightly
  lit prey, retreat from fire and strong light, and the population stays capped;
  root-cave dens spawn their wolves underground.
- **Ranged combat**: shared data-driven weapon stats for player and NPCs.
  Primitive bow with draw-and-release, arrow drop, arrow recovery from carcasses
  and surfaces, and iron arrows. **Black powder** (sulfur + saltpeter + charcoal)
  unlocks the muzzle-loading **Veylan musket**, **scrap flintlock** and
  **scrap blunderbuss** — high damage, long reloads, and gunshots are huge noise
  events that alert settlements and wildlife. HUD shows ammo, loaded rounds,
  reload progress, bow draw and crosshair spread; `R` reloads.
- **Explosives**: placeable **powder kegs** with visible sputtering fuses (lit by
  interaction, adjacent fire, or other blasts; bounded chain reactions), thrown
  **scrap bombs** and **fire bombs**. A reusable explosion system applies
  distance falloff, line-of-sight cover, per-block blast resistance (ancient and
  progression-critical blocks are immune; reinforced stone resists), bounded
  fire ignition, reputation consequences and a single batched world edit per
  blast (one heightmap/light/mesh pass).
- **Relic weapons** (loot-only, never craftable): the accurate semi-auto
  **Frontier Carbine** and the loud full-auto **Scavenged Auto-Rifle**, found
  extremely rarely in research pods and castle/fortress armories, dependent on
  scarce relic cartridges and restorable at an anvil with relic components.
- **Cave gear**: climbable **rope ladders**, long-lived placeable **lanterns**
  and cheap emissive **trail markers**.
- **Perception**: positional world-noise events (gunshots, explosions, alarms,
  fuses, shouts) with radius and intensity; humans hear them, wildlife flees or
  investigates; human sight is block-occluded, cone-limited and darkness- and
  crouch-aware, with last-known-position searching and return-to-duty.
- **Factions & reputation**: per-faction reputation and bounty plus per-settlement
  local standing. Trade, gifts, healing, defense and rescues improve relations;
  theft, property damage and violence sour them. Neutral Free Settler communities
  flip friendly or hostile at thresholds, while hostile Free Settlers require an
  explicit restitution offer to de-escalate. Regional traders/leaders offer delivery,
  village defense, trader escort, fort scouting, rescue, patrol clearance, alarm
  sabotage, stolen-supply recovery, fort capture, outpost defense and cave quests.
- **Navigation**: bounded voxel A* for settlement NPCs (1-block step-up, limited
  drops, water costs, gate and rope-ladder awareness, strict per-query budget,
  cached paths, repath cooldowns) with steering fallback. Gates open for NPCs
  and the player and close on their own — never on someone standing in them.
- 13 new blocks and 21 new items, every one with procedural textures, icons and
  held models; new synthesized audio for bow draw/release, arrow and bullet
  impacts, musket/pistol reports (long-carry), dry fire, reload, fuses,
  explosions, alarm bells and gate creaks. Explosion VFX reuse the instanced
  particle pipeline (flash, sparks, debris, dust, embers) under the existing
  4000-particle cap, with restrained motion-setting-aware camera shake.
- Map screen: settlement markers with alignment colors, tier letters (C/V/F/K/X),
  cleared/occupied states and a wrapping POI legend; debug overlay reports
  settlements, projectiles, noise events and lit fuses.
- The deterministic test suite grew to **160 tests across 39 suites**
  (settlement planning determinism/order-independence/rarity/spacing, deep-cave
  identity/connectivity/lake protection, v2→v3 migration incl. an authentic
  0.2.0 fixture, v3 round-trips, pathfinding, perception, combat and bow/firearm
  workflows, explosions and keg breaches, faction reputation, captive rescue,
  counterattack lifecycle, NPC budgets, lantern fuel, quest targeting and
  long-run boundedness). AI decision jitter is reseeded from the world seed on
  world creation/load, so a fixed seed replays identically.

### Compatibility

- Save format is now **binary v3**; v2 saves load through an explicit migration
  path and keep their exact legacy terrain (the generator version is persisted).
  Settlements, deep caves, sulfur/saltpeter and cave POIs appear only in worlds
  created on 0.3.0+. `BlockType`/`ItemType` and all other serialized enums are
  append-only; ordinals from v0.1.0 are unchanged.
- OpenGL 3.3 Core remains the platform baseline; all new art and audio is
  procedural/synthesized in code.

### Known limitations

- v2 worlds never gain the new world content (documented in the README).
- Human pathfinding is bounded local A*, not a navmesh; very rough terrain can
  still stall an NPC, which then falls back to direct steering.
- Gates are block swaps rather than animated doors; no aim-down-sights.
- Switching slots or loading a save cancels an in-progress reload; loaded rounds
  persist. Open-gate close timers persist and resume.

## [0.2.0] - 2026-07-16

### Added

- Procedural 64×64 terrain material system with stable visual IDs, variants,
  biome/season tinting and documented PNG override support.
- Forward PBR-lite rendering with vertex AO, directional lighting, PCF shadows,
  wet/frost response and gamma-correct HDR output.
- Procedural sky, animated depth-tinted water, bloom, tonemapping, FXAA and
  gameplay-state color/vignette effects.
- Articulated procedural models and animation states for all creatures, NPC
  roles and first-person held items.
- Instanced particle rendering, mining cracks, tracks, blood, beacon and weather
  effects.
- Source Sans 3 UI rendering, generated icons for all 70 items, redesigned HUD,
  title/loading/death/victory presentation and persistent graphics options.
- Deterministic capture scenes, OpenGL diagnostics, render statistics, automated
  save/load smoke testing and a configurable `VEYLON_MIN_FPS` release gate.
- Unit tests protecting graphics settings, procedural textures, stable visual IDs,
  window sizing and serialized enum ordering.
- Self-contained native packages for Windows x64, macOS Apple Silicon and macOS
  Intel, built on matching GitHub-hosted runners.

### Changed

- Default presentation now starts at a title screen instead of entering a world
  immediately.
- Chunk meshes now use textures, normals, lighting, AO and dedicated water data.
- Far GPU chunk meshes are released according to the configured render distance.
- Release tooling now selects LWJGL natives by operating system and CPU architecture
  and produces a self-contained app-image ZIP for each supported target.

### Compatibility

- Save format remains binary v2; `BlockType` and `ItemType` serialized order is
  unchanged from v0.1.0.
- Windows 10/11 and macOS (Apple Silicon or Intel) are supported; OpenGL 3.3 Core
  remains the platform/API baseline.
- The regular distribution and fat JAR require JDK 25. The `-windows.zip`
  and `-macos-*.zip` app-images include their own Java runtime.

### Known limitations

- Exact 2560×1440 visual validation was not available on the 1080p validation
  display; 2560-wide layout and exact fullscreen 1920×1080 were tested.
- Lighting is not flood-filled, water uses full cells, and entity animation is
  procedural cuboid animation rather than skeletal animation.
- NVIDIA may emit one-time shader-state recompilation performance messages;
  validation observed no OpenGL errors or steady-state frame-time impact.
- macOS packages are not yet Developer ID signed or notarized, so Gatekeeper may
  require explicit first-launch approval.

## [0.1.0]

- Initial playable survival simulation release.

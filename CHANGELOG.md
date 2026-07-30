# Changelog

All notable user-facing changes to VEYLON: Deep Frontier are recorded here.

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

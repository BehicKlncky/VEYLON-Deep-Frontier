# All-living dismemberment and combustion — implementation contract

Milestone 01 deliverable, 2026-09-26. Branch `feature/all-living-dismemberment-combustion`,
base `3704f4dea3b7870777b20656813937368a83a171` (`merge: release v0.8.0`). Progress,
commands and evidence live in
[ALL_LIVING_COMBAT_FIRE_PROGRESS.md](ALL_LIVING_COMBAT_FIRE_PROGRESS.md).

This document freezes what milestones 02–11 build and the rules they share. Sections 2–4
state **verified facts** about the base revision, each tagged with how it was established:
*source* (read at the cited symbol), *test* (an existing assertion that ran green at the
base) or *probe* (a throwaway headless run on the base, recorded in the progress file).
Sections 5–18 are **decisions**. Every number marked *proposed* is new tuning chosen here,
not a value discovered in source; later milestones may retune it with evidence and must
record the change in this file. Class and method names for new code are proposals too:
the implementing milestone records the real symbol.

Paths are relative to `src/main/java/com/veylon/` unless they start with `src/` or `docs/`.

## 1. Requirements and fixed policies

| # | Rule |
| --- | --- |
| R1 | A lethal bomb or keg blast dismembers every living body at its authored joints: all six `Creature.CreatureType` species, every human NPC family, and a Survival player. The human NPC effect is the quality reference. |
| R2 | Molotovs and exposed flames set every living body alight: a sustained state with damage over time and visible flames that continue after the body leaves the source. |
| R3 | Burning NPCs and animals panic with irregular, obstacle-aware escape; birds escape by flying. |
| R4 | **The player keeps full control.** No forced movement, sprint, steering, velocity impulse, speed change, aim displacement or camera rotation from combustion. Existing blast knockback (`Entity.knockback`) is unchanged and is not a combustion response. |
| R5 | **Creative immunity is unchanged**: no player damage, combustion, medical injury, lethal-blast record or player remains while `PlayerAbilities.invulnerable()` is true. NPCs and animals stay vulnerable in both modes. |
| R6 | Combustion and panic are transient: saving leaves them running in memory; loading, a new world, respawn and a switch to Creative clear them. Settled fragments and harvest state persist. |
| R7 | One death's worth of consequences: quests, trust, reputation, loot, harvest yield and lodged arrows happen once per body, never per fragment or per burn tick. |
| R8 | Burning bodies do not spread fire to blocks or to other bodies. Visual residue on remains never becomes a gameplay source. |

## 2. Verified diagnosis at the base revision

### 2.1 Scrap bomb and powder keg (source, test, probe)

| Step | Symbol | Behaviour |
| --- | --- | --- |
| Throw command | `PlayerCombatSystem.updateThrownWeaponCommand` → `throwBomb` | From `camera.position` with direction `dir + (0, 0.18, 0)`; `WeaponRegistry` `scrap_bomb`: speed 13, gravity 16, spread 3°, damage 0, interval 1.2 s. Consumes one item outside unlimited-items mode. |
| Flight | `ProjectileSystem.fire` / `update` / `step` / `entityAt` | `Kind.BOMB`, fuse 2.4 s, life ≈ 3.58 s. Sweep samples every ≤ 0.45 block against padded entity AABBs (creatures first, then NPCs; the player only for non-player projectiles). |
| Body impact | `ProjectileSystem.onEntityHit` (BOMB branch) | Bomb stops at the victim's feet (`vx = vz = 0`), `impactedEntity = true`, keeps cooking. A moving victim walks on during the fuse; a bird hit in the air drops the bomb to the ground below it. |
| Fuse end | `ProjectileSystem.detonate` → `ExplosionSystem.explode(..., 2.6, 14, 0, fromPlayer, true)` | Kegs: `ExplosionSystem.tickFuses` and the chain queue call `detonate` with `KEG_POWER` 3.8, `KEG_DAMAGE` 30, `KEG_IGNITE` 0.35, always lethal (`MAX_CHAIN` 8). |
| Victims | `ExplosionSystem.detonate` entity loop | Skips `e.dead` and an invulnerable player. Falloff range `power × 2.4` measured from the **feet** (`Entity.distSqTo(pos)`), damage `entityDamage × falloff × (0.15 + 0.85 × exposure)`, exposure = share of 5 rays to the body centre that are clear, `< 0.5` ignored, knockback `3 + falloff × 5`. Player: `hurtPhysical` (armour, bleed roll) plus `renderer.addShake`. Creatures also get `fear = 1`, `bleedTimer ≥ 12`. |
| Lethal gate | `detonate` line with `lethalToHumans && e instanceof Npc n && insideLethalRadius(n, …)` | Only an `Npc` whose **body centre** (`pos.y + height / 2`) is within `power × LETHAL_RADIUS_FACTOR` (1.5 → 3.9 / 5.7 blocks) dies outright via `killOutright` → `Npc.killBy` (= `hurt(health + 1)`), ignoring health and cover, and gets the transient record `Npc.dismemberOnDeath` + `blastX/Y/Z/Strength` (first fatal blast kept). |
| Death routing | `EntityManager.fastTick` | NPC: log/trust/raider loot/`onSettledNpcDied`, then `reallyDied && dismemberOnDeath` → `BodyFragmentSystem.spawnFromNpc` (10 `BodyFragment.Piece`s), else `RagdollSystem.spawn(g, Npc)`. Creature: `onCreatureDied` → quest credit, bird meat, harvest log, `RagdollSystem.spawn(g, Creature)`. `reallyDied` = `health <= 0` separates deaths from administrative removal. |
| Player death | `Game.frame` (`player.dead && !invulnerable` → `AppState.DEATH`, `deathTimer` 3 s) → `Game.respawn` | First-person death overlay; **no player body, ragdoll or fragment exists**. |
| Presentation | `particles.explosion`, `audio.playExplosion`, `renderer.addShake(0.6)`, `noise.emit(120)`; fragments: `BodyFragmentSystem` blood bursts, `Renderer.drawFragment` → `Animator.poseFragment(NpcModels.get(), f)` + `FragmentModels` | Humanoid-only fragment geometry, pose and cut faces. |

**Why only people are blown apart.** The gate is typed on `Npc`; `insideLethalRadius`,
`killOutright`, `killBy` and the death record exist only on `Npc`; `EntityManager` only routes
NPCs to `spawnFromNpc`; `BodyFragment.Piece`, `FragmentModels`, `Renderer.drawFragment` and
`save/FragmentsSection` (`world.fragments` v1) are humanoid tables. Creatures and the Survival
player inside the lethal radius take ordinary falloff damage and, if it kills, fall whole.
`BlastLethalityTest.creaturesAndPlayerKeepTheExistingExplosionModel` deliberately asserts this,
and `docs/engineering/COMBAT_LETHALITY_AND_MOLOTOV.md` lists "Only people are dismembered" as a
deliberate limit. Both are superseded by R1.

Probe (production throw command, scrap bomb landing at a thornhorn's feet): a 70-health brute
NPC about 2.5 blocks from the blast died and became 10 fragments; the thornhorn inside the
radius survived at 36.6/42; the Survival player 2.57 blocks from the blast took 17.1 and
survived; a deer that started about 2 blocks from where the bomb landed had fled to 11.5
blocks by detonation.

### 2.2 Fire bomb (source, test, probe)

| Step | Symbol | Behaviour |
| --- | --- | --- |
| Throw | same command; `fire_bomb` definition | Log: "Rag lit — it bursts into flame where it lands!". Rag flames in flight are particles only (`ragFlameDue`). |
| Impact | `onEntityHit` FIRE_BOMB branch / `onBlockHit` / fuse fallback | `shatter(g, p, x, y, z)` exactly once (`detonated`): glass/splash particles, bottle and fuse sounds, `LiquidFireSystem.spill`. **The hit body receives nothing**: no damage, no ignition, no attack notification. |
| Spill | `LiquidFireSystem.spill` / `landing` | From the break cell, searches `SURFACE_SEARCH_DEPTH` 3 blocks down for an air cell on solid ground; water or solid in the way, or no ground in reach, returns 0 patches. Priority fill: ≤ 22 patches, radius 3.2, drop ≤ 2, burn 7–12 s, intensity 1 → 0.5 at the rim, global cap 160 (oldest spill evicted). |
| Contact | `LiquidFireSystem.mediumTick` → `tick` → `burnOccupants` / `standsIn` | Only on the **medium tick (0.5 s)**. Feet footprint must overlap the cell with feet in `[y − 0.1, y + 0.6]`. Damage `ENTITY_DPS_* × 0.5 s`: NPC 5, creature 6, player 3 per tick, once per entity per tick (`burnedThisTick`). Player: `damageFlash`, 50 % roll for `Affliction.BURN` 60–100 s. NPC: first burn per (NPC, spill) points `lastKnown` at the pool unless the bottle was thrown by an imperceptible player, and reports a player attack for settled NPCs. |
| Rain | `FireSystem.isRainedOn` | Exposed patch soaks, does nothing while `wet > 0`, goes out after 1 s. |
| Presentation | `AmbienceSystem.emitLiquidFire`, `Renderer.renderLiquidFire`, `ParticleSystem.liquidFlame` | Ground sheets and flames only. **No body-attached fire exists anywhere.** |

**"Loses health in a pool" is not "catches fire".** Nothing in the base revision stores a
burning state on a body. Damage exists only while a medium-tick sample finds the feet in a
patch. Probe: an NPC hit directly lost 10 health over two ticks in the pool and **nothing** in
the 5 s after it left.

**Existing molotov tests are acknowledged, not over-read.**
`MolotovTest.aMolotovShattersOnAnEntityAndSpillsAtItsFeet`,
`patchesBurnEntitiesOncePerTickAndRespectCreativeInvulnerability`,
`playerMolotovOnSettlementNpcsCountsAsOneAttackPerNpc`,
`aCreativeThrowerIsNotNoticedButStillPaysForTheAttack` and
`CombatFireIntegrationTest.aBurnDeathInLiquidFireFallsWholeEvenWithTorsoWounds` prove that
NPCs, creatures and the player take pool contact damage per medium tick, with attribution,
de-duplication and Creative immunity. They call `liquidFire.mediumTick` directly with bodies
placed in the pool. They do not prove sustained ignition, contact reliability for moving or
airborne bodies in the real loop, body flames or panic.

### 2.3 Moving and airborne targets (probe)

| Case | Observed at the base |
| --- | --- |
| Body crossing a real pool at 4.4 / 7 / 12 blocks/s | Identical crossings took different damage depending on the medium-tick phase: 7 b/s through the centre (0.78 s contact) took 1 or 2 ticks; 12 b/s (0.37–0.45 s contact) took **0** ticks in some phases. Maximum sampling gap is 0.5 s. |
| Bird hit directly 6 blocks above the floor | Bottle shattered on the bird; `spill` found no ground within 3 blocks: **0 patches, bird untouched**. |
| Bird hit directly 3 blocks above the floor | 22 patches spilled on the floor below; bird untouched (airborne feet are outside the contact band). |
| Scrap bomb vs moving deer | The bomb stopped at the first body it hit and cooked for 2.4 s; the fleeing deer was 11.5 blocks away at detonation. |

### 2.4 Other verified gaps

- `FireSystem.damageNear` (source, probe): distance from the **feet** to each burning cell's
  centre `< √2.4` (1.55 blocks), no occlusion and no per-entity de-duplication. Probe: an NPC
  standing on solid stone over a buried burning log lost 4 per tick through the floor; an NPC
  between three burning logs lost 12 in one tick (3 × 4); burning leaves beside an NPC's head
  did nothing. NPC and creature damage is always `hurt(…, false)`, so a player-lit fire never
  gives kill credit; `FireSystem.Burn` stores no attribution. Rained-on cells still damage
  until they go out.
- `FireSystem.tickCampfires` only burns fuel and arms kegs. **Fueled campfires and placed
  torches never damage or ignite anyone** (probe: 5 s standing in each cell, no damage).
- AI fire response: `CreatureAI.panicFromFire` (deer, hare, thornhorn, wolf via
  `updatePredatorCommon`) flees `FireSystem` cells within 8 blocks; `updateStalker` checks
  within 10; **birds have none; NPCs have none**. None of them considers liquid patches,
  campfires or the creature's own state. This is avoidance, not a body-fire response.
- `EntityManager.fastTick` runs `CreatureAI.update` and physics on a creature that died since
  the previous tick before removing it (`CreatureAI.update` has no `dead` guard; `NpcAI.update`
  does). A burn or blast death between ticks can therefore get one more creature AI step.
- `UiMode.NPC` does not pause simulation, and `NpcScreen.update` re-sets the speaker's
  `interactFreeze` to 1 s every frame until the screen closes.
- Carcasses have no count cap (only `decay` 420 s and emptiness), and unlike corpses they are
  not culled by distance.

## 3. Target coverage

### 3.1 Living targets

| Target | Identification | AI route (first to last) | Death route today | Lethal blast today | Fire today |
| --- | --- | --- | --- | --- | --- |
| Glowdeer `DEER` | `Creature.type` | `CreatureAI.update` → `updateDeer` (`panicFromFire` first) | `onCreatureDied` → ragdoll → `Carcass` (2 meat, 1 hide) | falloff only, falls whole | pool/block contact |
| Ashwolf `WOLF` | " | `updatePredatorCommon` (`panicFromFire` first; hunts, scavenges carcasses) | ragdoll → carcass (2, 1) | " | " |
| Skitterwing `BIRD` | " (`flying`, no gravity) | `updateBird` (startle from a perceivable player < 6, else wander 5–13 above ground); **no fire check** | +1 raw meat on death if the player killed it; ragdoll tumbles and leaves nothing (`leavesBody = false`) | " | contact only if its feet are in the band — never while flying |
| Murkhare `HARE` | " | `updateHare` (`panicFromFire` first) | ragdoll → carcass (1, 0) | " | pool/block contact |
| Thornhorn `THORNHORN` | " | `updateThornhorn` (`panicFromFire` first; charges) | ragdoll → carcass (4, 2) | " | " |
| Gloomstalker `STALKER` | " | `updateStalker` (own 10-block fire check, light avoidance) | ragdoll → carcass (2, 1) | " | " |
| Interacting NPC (any) | `Npc.interactFreeze > 0` | `NpcAI.update` returns after stop/face, before every family below | per family | per family | per family |
| Legacy camp member | not settled, not trader/raider; `campIndex` job | `NpcAI` flee at < 30 % health, attack if hostile, defend vs raiders, guard/hunt/gather/medic | trust −40 if player kill; ragdoll or 10 fragments | **dies + 10 fragments** | contact |
| Legacy wandering trader | `isTrader` | `NpcAI.updateTrader` | escorted-mission failure; leaving = administrative `dead` with health > 0 | " | " |
| Legacy raider | `raider` | `NpcAI.updateRaider` | scrap/bandage loot, `onRaiderKilled`; fade = administrative | " | " |
| Settlement resident | `settled()`: archetype + `residentIndex ≥ 0`; alignment from `Settlement.hostile()` | `SettledNpcAI.update`: perceive → `combat` (hostile) → friendly defence (`engageMelee`/`engageCreature`) → non-combatant hide → search → `dailyLife` (work, `SLEEP` at night) | `onSettledNpcDied` resident bookkeeping and archetype loot | " | " |
| Settlement trader | archetype `TRADER` (settled) | as resident | as resident | " | " |
| Captive | archetype `CAPTIVE` | `SettledNpcAI` early return: stop, face a perceivable player | as resident | " | " |
| War party (patrol, bounty hunter, counterattack) | `warParty`, `PartyKind` | `SettledNpcAI` → `combat` or `partyTravel`; `abstractTravel` skips physics when far or unloaded | mission/counterattack bookkeeping; routed/returned = administrative | " | " |
| Survival player | `Player`, not invulnerable | user input only (`PlayerMovementSystem.update`) | `Game.frame` → `AppState.DEATH` → `Game.respawn` (health 55, afflictions cleared) | falloff only (`hurtPhysical`) | contact; medical `Affliction.BURN` |
| Creative player | `abilities.invulnerable()` | user input | none (`restoreCreativeBody`) | skipped | skipped |

Settlement archetypes (`settlement/NpcArchetype`): VILLAGER, GUARD, MEDIC, TRADER, FARMER, SMITH,
ARCHER, SCAVENGER, TRACKER, SCOUT, HUNTER, BRUTE, POWDERMAN, LEADER, CAPTIVE. Ranged fire comes
from `SettledNpcAI` via `ProjectileSystem.fire` (archers, scouts, powdermen).

### 3.2 Anatomy (source)

`DeathRagdollTest.renderedParentChainsReachTheSolvedHandlesForEverySpeciesAndHumans` already
checks, for every species and humans, that each skeleton bone names a real model part and that
the posed model's part frames land on the solved ragdoll points within 0.001, so skeleton
pivots agree with the model hierarchy.

Model root for every body is the box-less `root`. `BodySkeleton` root pivots are model
coordinates; child pivots are parent-relative. Split halves come from `ModelPart.split` (the
tip is a child pivoted at the unsplit box centre).

| Body | Entity w × h, health | Torso box (model) | Skeleton bones = cuttable joints | Box-less pivots | Accessories (no joint; stay with parent) |
| --- | --- | --- | --- | --- | --- |
| Human (`NpcModels`) | 0.55 × 1.75, 35 default (archetype 24–90) | `torso` 0.46 × 0.62 × 0.26 on hip 0.86 | `neck`, `head`, `arm_l/r` + split `forearm_l/r`, `leg_l/r` + split `shin_l/r` (10) | `root`, `neck` | torso: vest, pack, traderRoll, traderSatchelL/R, raiderPadL/R, raiderSpear (+Tip), friendlyBadge, quiver (+Fletch), slungBow, kegPack, slungGun, brutePadL/R, bruteChest, leaderMantle, trophies, medicSash, guardPlate; head: hood, visor, traderAntenna, traderLamp, leaderCrest, warPaint |
| Deer | 0.7 × 1.05, 14 | `body` 0.42 × 0.42 × 0.85, legs 0.11 × 0.58 × 0.11 | `leg_fl/fr/bl/br` + `_lower`, `neck`, `head`, `tail` + split `tail_tip` (12) | `root`, `neck` | head: snout, antler_l/r (+ `tine-1`/`tine1`); body: six emissive `glow*` spots |
| Wolf | 0.65 × 0.95, 22 | 0.34 × 0.34 × 0.80, legs 0.10 × 0.42 × 0.10, tail 0.10 × 0.10 × 0.34 | as deer (12) | `root`, `neck` | head: muzzle, ear_l/r, `eye-1`/`eye1` (emissive); body: ruff |
| Thornhorn | 1.1 × 1.5, 42 | 0.72 × 0.72 × 1.25, legs 0.20 × 0.55 × 0.20, tail 0.14 × 0.14 × 0.26 | as deer (12) | `root`, `neck` | head: horn_l/r (+ `horn_tip-1`/`horn_tip1`); body: plate0–2 |
| Gloomstalker | 0.7 × 1.0, 30 | 0.26 × 0.26 × 0.70, legs 0.06 × 0.72 × 0.06, tail 0.05 × 0.05 × 0.40 | as deer (12) | `root`, `neck` | head: `eye-1`/`eye1` (emissive 1.0); body: quill0–3 |
| Hare | 0.4 × 0.4, 6 | `body` 0.22 × 0.20 × 0.34 (+0.02 z offset), legs 0.05 × 0.12 × 0.05, tail 0.07 × 0.07 × 0.06 | legs + `_lower`, **`head_joint`** (not `neck`), `head`, `tail` + `tail_tip` (12) | `root`, `head_joint` | body: haunch; head: ear_l/r |
| Bird | 0.34 × 0.34, 4 | `body` 0.16 × 0.14 × 0.30 | `head`, `tail`, `wing0_l/r`, `wing1_l/r` (6); **no neck, no legs, no tail tip** | `root` | head: beak |

Verified pivot agreement: quadruped `neck` at `(0, legH + 0.8 × bodyH, −0.48 × bodyLen)` and
species head/tail offsets match `BodySkeleton.quadruped` arguments for all four quadrupeds;
hare `head_joint` (0, 0.23, −0.18), legs (±0.07/±0.09, 0.12, −0.10/0.14) and tail
(0, 0.22, 0.22) match `BodySkeleton.hare`; bird head (0, 0.23, −0.17), tail (0, 0.18, 0.17)
and wings (±0.08, 0.23, −0.06 / 0.08) match `BodySkeleton.bird`. Quadruped and hare legs are
children of `root`, not `body`; tails and accessories are children of `body`. Bird wing boxes
are offset ±0.16 along X from their pivots (span 0.01–0.31), and every long quadruped part
lies along Z — `BodyFragment.Piece`'s "centre at `pivotX`, `pivotZ = 0`, box straight above or
below" assumption holds only for humans.

## 4. Fire source inventory

| Source | Producer | Active when | Flame volume (proposed for contact) | Water / rain | Attribution today | Damage today | Contact policy / missing integration |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Liquid patch | `LiquidFireSystem.spill` | in `patches()`, `wet == 0`, ground still solid, cell not water/solid | cell footprint `[x, x+1] × [z, z+1]`, feet band `[y − 0.1, y + 0.6]` (the existing `standsIn` shape) | goes out if flooded; rained-on patch inert while wet, out after 1 s | `Patch.byPlayer`, `spillId` | medium tick, per entity once | Sample at fast cadence with a swept body box; ignite on first contact (07). |
| Direct molotov body hit | `ProjectileSystem.onEntityHit` FIRE_BOMB | the hit itself | the struck body | a hit on a body whose torso is under water ignites nothing (proposed) | `Projectile.fromPlayer` | none | Ignite the victim at the hit, then shatter/spill unchanged (07). No blast, no shake. |
| Burning block cell | `FireSystem.ignite` (spread, patches, keg blasts, lightning `WeatherSystem.strikeLightning`, meteor `EventSystem`) | in `burningCells()`, block still flammable, **not currently rained on** (`Burn.wet == 0`) | cell inflated by 0.25 on every side, plus 0.75 above the top face only if the block above is not solid | frozen and put out by rain (2 s) | none (always environmental) | medium tick, feet distance < 1.55, stacked per cell, no occlusion | Replace `damageNear`'s `hurt` calls with exposure; add `byPlayer` to `Burn`, inherited from the igniting patch or parent cell (07). |
| Fueled campfire | block `CAMPFIRE` with `world.campfireFuel > 0` | fuel > 0 (fuel drains 2.2× in exposed rain; stays lit) | block-local `[0.25, 0.75] × [0.15, 1.10] × [0.25, 0.75]` | lit until fuel runs out | environmental | **none** | New adapter (07); cell lookup from the body box, not a world-wide fuel-map scan. |
| Placed torch | block `TORCH` | always (no lit/fuel state exists) | flame head `[0.38, 0.62] × [0.66, 1.05] × [0.38, 0.62]` (mesh head is 0.40–0.60 × 0.68–0.86) | no rain behaviour exists; none invented | environmental | **none** | New adapter (07), slow ignition so walking past is safe (section 15). |

Not sources, by decision: a held torch or any held item (no world flame volume); `LANTERN`
(flame enclosed in glass, `World.isLanternLit` notwithstanding); `FURNACE`; `TRAIL_MARKER`
(paint); `GLOW_FUNGUS`, `RUIN_CORE`, `BEACON`/`BEACON_LIT` (light only); emissive model parts;
`TemperatureSystem.fireHeatAt` warmth; keg fuse embers; the molotov rag in flight; blast
flashes; burning bodies, ragdolls and fragments (R8). There is no lava. Light level or
`BlockType.heat` alone never implies a flame.

## 5. Data owners

| Data | Owner (proposed name) | Notes |
| --- | --- | --- |
| Lethal-blast death record | `Entity` (shared transient record replacing `Npc.dismemberOnDeath` / `blastX/Y/Z/Strength`) | Set once, only on the alive → dead transition caused by the lethal kill. |
| Species fragment definitions | `entity/FragmentAnatomy` per `entity/BodyFamily` (pieces `FragmentPiece`, wounds `FragmentCut`), built once when the class loads (02) | Explicit joint tables repeating the model builders' numbers, because `entity` must not import `gfx`; `gfx/model/AnatomyModels.validate` holds them to the real models. No per-frame rebuilding; no references to live model instances. |
| Death pose | `entity/FragmentPose`, one immutable snapshot shared by a body's pieces (02) | Captured by `gfx/model/AnatomyModels.captureCreature`/`captureNpc`; §7.1. |
| Fragment physics, caps, settling, decay | `BodyFragmentSystem` (extended, not duplicated) | One step, one sweep, one set of caps for every family. |
| Harvest yield and lodged arrows of a blasted animal | the existing `Carcass` record, flagged as fragmented and anchored to its torso fragment by a stable remains id | Section 9. |
| Player remains | `BodyFragmentSystem` pieces with an explicit neutral player appearance | Never references the live `Player`. |
| Active combustion per body | one embedded state object per `Entity` (`Entity.combustion`, class `BodyCombustion`) | Dies with the entity; no registry to purge; bounded by the entity lists. |
| Combustion rules, exposure resolution, damage | `simulation/CombustionSystem` owned by `Game`, reset and seeded in `WorldBootstrap` | Tuning in `simulation/CombustionConstants`. |
| Panic intent (goal, timers, recovery) | embedded per `Npc`/`Creature` (or inside `BodyCombustion`) | Written only by AI; seeded panic stream on `EntityManager` or the combustion system. |
| Burn visuals, scorch rendering, audio | presentation layer (`AmbienceSystem`/`ParticleSystem`/`Renderer`/`AudioManager`) reading a read-only snapshot | Presentation never writes gameplay state. |
| Death-time visual residue | a bounded list owned by the presentation or fragment/ragdoll layer (09) | Snapshot values only, no entity references. |

## 6. Update cadence and order

Frame (`Game.frame`, simulate gate unchanged): player movement and actions →
`scheduler.update` (up to 10 fast ticks, then at most one medium and one slow tick) →
particles → projectiles → ragdolls → fragments → keg fuses → noise → emitters → player-death
transition.

Fast tick (20 Hz, `FAST_DT` 0.05), **new order**:

1. `player.tickNeeds` — needs and medical afflictions; reads the previous combustion state for
   the medical BURN rule (section 11).
2. `combustion.fastTick` (new) — for every body in `creatures`, `npcs` and the player: skip dead
   bodies and an invulnerable player (clearing its state); sample exposures with the body box
   swept from its previous sample position; resolve one dominant exposure; apply extinction
   (water, exposed rain); ignite/refresh; apply damage through `hurt`; store the sample
   position. Ignition and first-attack notifications happen here.
3. `entities.fastTick` — AI (panic reads this tick's burning state first), physics, then death
   routing. A body killed in step 2 is removed in this same pass. 06 adds a `dead` guard
   before `CreatureAI.update` when it wires this order, so no dead creature acts; 09 audits it.
4. `settlementManager.fastTick`.

Medium tick (2 Hz): `FireSystem` and `LiquidFireSystem` keep block/patch simulation (spread,
burn-down, rain, keg arming, block ignition) but **stop damaging bodies** once 07 moves
contact to step 2. No medium-tick system advances combustion timers.

Direct molotov hits ignite inside `ProjectileSystem` during the frame; damage starts on the
next fast tick. The ignition tick itself applies contact damage for that tick, so a sampled
contact always costs something. Invalid `dt` (non-finite or ≤ 0) is ignored; combustion only
ever advances by `FAST_DT` steps, so equal simulated time under different frame partitions
gives the same state up to the frame-vs-fast-tick placement of direct hits.

Sweep: the body box is swept linearly from the previous sample position to the current one;
a segment longer than 2 blocks (teleport, respawn, load) samples only its end. Maximum sampling
gap: one fast tick (0.05 s).

## 7. Fragment identity strategy

- A **body family** is `HUMANOID` or one `Creature.CreatureType`. Each family has its own
  stable, append-only piece list. Identity on disk is (family, piece), never an ordinal shared
  across families.
- Pieces follow the authored model: one piece per skeleton bone that owns a visible box, plus
  the torso (the `body`/`torso` box and every accessory without a joint). A box-less bone
  (`neck`, hare `head_joint`) is never its own piece: its boxed child's piece owns it and the
  cut sits at that pivot, exactly as the human `HEAD` piece owns `neck`.
- Split halves are separate pieces, except that a split half whose longest full dimension is
  under `MIN_SEPARATE_PIECE` 0.10 m (*proposed*) stays with its parent piece; the ragdoll keeps
  that hinge. This keeps 3–6 cm debris out of the physics.
- Every visible model box belongs to exactly one piece; accessories go with their parent.
- Legacy `BodyFragment.Piece` values and ordinals 0–9 stay the human mapping of
  `world.fragments` v1.

Resulting cut sets (*proposed*, validated in 02):

| Family | Pieces | Count |
| --- | --- | --- |
| Human | unchanged: TORSO, HEAD (neck+head), UPPER_ARM_L/R, FOREARM_L/R, THIGH_L/R, SHIN_L/R | 10 |
| Deer | torso, head (neck+head+snout+antlers), 4 upper legs, 4 lower legs, tail (tip merged: halves 0.08 × 0.08 × 0.06) | 11 |
| Wolf | torso, head, 4 upper, 4 lower, tail, tail_tip | 12 |
| Thornhorn | torso, head (with horns), 4 upper, 4 lower, tail, tail_tip | 12 |
| Gloomstalker | torso, head, 4 upper, 4 lower, tail, tail_tip | 12 |
| Hare | torso (body+haunch), head (head_joint+head+ears), 4 whole legs (halves 0.05 × 0.06 × 0.05 merged), tail (halves merged) | 7 |
| Bird | torso, head (with beak), tail, wing0_l, wing0_r, wing1_l, wing1_r | 7 |

Maximum 12 pieces per body. A piece's collision box is its own box (plus merged halves), never
a bone radius; `BodyFragmentConstants.MIN_HALF_EXTENT` may inflate the sweep only, and 04 must
keep rendered contact consistent with it. Death pose: a bounded CPU snapshot captured at the
death transition, owned by the fragment, never a shared mutable model; computing it must not
consume gameplay random streams. (02 widened it from bone rotations to one full part transform
per anatomy joint; see §7.1.)

### 7.1 As built in milestone 02

The cut sets above are confirmed exactly, including the deer's merged tail tip and the hare's
whole legs and tail; `MIN_SEPARATE_PIECE` is `BodyFragmentConstants.MIN_SEPARATE_PIECE` = 0.10
and `FragmentAnatomy` refuses a table that breaks it in either direction. Piece names and order:

| Family | Pieces (id order) |
| --- | --- |
| `HUMANOID` | torso, head, upper_arm_l, upper_arm_r, forearm_l, forearm_r, thigh_l, thigh_r, shin_l, shin_r (= `BodyFragment.Piece` ordinals) |
| `DEER` | torso, head, upper_leg_fl/fr/bl/br, lower_leg_fl/fr/bl/br, tail |
| `WOLF`, `THORNHORN`, `STALKER` | as the deer, then tail, tail_tip |
| `HARE` | torso, head, leg_fl, leg_fr, leg_bl, leg_br, tail |
| `BIRD` | torso, head, tail, wing0_l, wing0_r, wing1_l, wing1_r |

- **Identity.** A piece is (`BodyFamily` ordinal, `FragmentPiece.id`); both append only. The
  family enum is `HUMANOID` then the six `CreatureType`s in their order. Nothing persists it yet;
  05 decides the byte encoding and pins the order in `SerializedEnumOrderTest`.
- **Joints.** A table lists every model part a piece is cut at, hangs from or flies as, plus
  every part the living `Animator` moves (the wolf's and hare's ears), parent first, at most
  `FragmentAnatomy.MAX_JOINTS` = 24. The model root belongs to the core piece; `neck` and
  `head_joint` belong to the head piece. Accessories are not listed and ride with their parent
  part through the model subtree.
- **Collision box.** The box part's own box, grown by the split halves merged into it; mass is
  that volume × `DENSITY`. Box-less parts never become pieces.
- **Wounds.** Unchanged human rule, with one extension: a joint buried inside its parent's box
  (the hare's hips) is cut on the face the severed piece leaves through instead of the face it
  is nearest. The cross-section laid on the face is the severed piece's size across the way it
  points from its joint. The table stores a flat rectangle on the face; the renderer adds depth
  and stands it proud of clothing (`FragmentModels.CUT_PROUD` over the vest; §7.2).
- **Death pose.** Not bones only: `FragmentPose` holds each joint's `ModelPart` transform
  (rotation, pose offset, scale) as the living animation drew it, so the root, the torso part
  and the ears are included. Each piece's rigid frame is its box part's frame in that pose, so a
  running leg or a beating wing leaves the body where it was drawn. The collision box follows
  the captured scale (breathing, ≤ 1.2 %).
- **Capture clock.** 03 should capture with `Game.totalTime`, the clock the renderer animates the
  living with, so separation shows no jump. That clock is presentation time, so the initial
  orientation of time-animated joints (wing beat, tail wag, idle sway, attack swing, breathing)
  depends on it. It feeds only debris placement: no random stream is drawn and no death, loot,
  yield or credit depends on it, and headless tests fix `totalTime`. Debris was already not
  bit-reproducible across live runs, because projectiles integrate frame time.

Save strategy for 05: the base reader skips unknown section IDs by length
(`V3ExtensionSections`, "Unknown stable IDs are intentionally skipped") but throws on an
unknown version of a known ID (`FragmentsSection.read`: "unsupported fragments section
version"). Therefore keep `world.fragments` v1 byte-identical for human pieces and put species
pieces, player remains and anchored-harvest links in a **new optional section ID**, so v0.8.0
builds still load new saves (dropping only the new remains). Built as planned in 05: §14.1.

### 7.2 As built in milestone 04 (drawing the pieces)

- **Which model.** `Animator.poseFragment(BodyFragment)` draws a piece from
  `AnatomyModels.modelOf(f.definition.family)`: the humanoid for a person or the player's
  remains, the species' `CreatureModels` model for an animal, with that species' own colours,
  glow and accessories. The two-argument form refuses any other model. Each draw runs
  `resetPose` → for the humanoid `Animator.applyAppearance(m, f.appearance)` →
  `AnatomyModels.applyPose(m, f.pose)` → `Animator.isolatePart`. Every live, corpse, ragdoll,
  carcass and piece draw starts with `resetPose` (and a person's with the look), so hidden
  parts, forced split halves, joint transforms and the vest colour never carry over to the next
  body; tint and emission are shader uniforms set per draw, and wounds are drawn with emission 0.
- **Frames.** `FragmentModels.rootFrame(f)` is `f.rootTransform` lowered by `contactDrop`, so
  every box is drawn where the living model drew it at death and then where the simulation has
  the piece. `pieceFrame(f)` = translate(pos − drop) × rotate(orientation) × scale(pieceScale)
  × translate(−restCentre) is the collision box's own frame; wounds (rest-pose model space) are
  placed in it with `cutFrame`, so they lie on the faces of the box as drawn and tumble with it.
  A wound on a piece cut at its own box part covers that joint in every pose; a head cut at a
  box-less neck keeps its stump under the head, where the neck met it.
- **Wound depth.** The table's rectangle, unchanged across the face (sizes come from the
  severed piece's cross-section: a 1.6 × 8 cm wing socket, a head-sized stump where a head sat
  on its box-less connector), from the face to `CUT_PROUD` (1 cm) beyond the outermost *shell*
  over it: a box of the piece standing at most `SHELL_REACH` (3 cm, new) beyond that face and
  overlapping the wound. A person's shells are the vest only, because every other kit part comes
  and goes with the look; this reproduces the pre-04 human wounds exactly. Any part of an animal
  may be one; in practice only the hare's haunch is (2 cm over the tail stump). A box reaching
  further, a shoulder pad over an arm's stump, lies over the joint and is ignored. The wound's
  inner face is back-facing against the box face and its outer face is 1 cm in front of it, so
  there is no depth fighting.
- **Culling bound.** Per family and piece, once: the largest of the collision box's
  half-diagonal, each wound's far corner, and for every part below the box part
  |pivot − box centre| + Σ|pivots below| + |box offset| + box half-diagonal, which holds however
  those joints turn (antlers, horns, ears, the raider's spear at its slung angle), times
  `pose.pieceScale`, about the drawn centre. A table in which a drawn box does not hang below
  its piece's box part fails when `FragmentModels` loads.
- **Ground contact.** The fragment sweep never shrinks below `MIN_HALF_EXTENT` (0.05), so a piece
  thinner than that (bird wings and tail, hare legs, gloomstalker legs and tail, the deer's tail)
  rests up to 4 cm above the floor. `contactDrop` = (0.05 − the turned box's vertical half
  extent) × clamp(1 − |vel| / `CONTACT_SPEED`, 0, 1), `CONTACT_SPEED` = 1 m/s (new): drawn at
  the bottom of its sweep box when slow, not moved while fast. Every piece leaves a body faster
  than `UPWARD_BIAS` (4.5 m/s), so separation is untouched. Horizontal inflation is not
  compensated: a thin piece resting against a wall may stand up to 2.5 cm off it.
- **Player remains.** `NpcAppearance.NEUTRAL_CAMP_INDEX` is now −2 (was 2 in 03): no camp, so
  `applyAppearance` shows no camp badge (it now needs `campIndex >= 0`), and `floorMod(−2, 4) = 2`
  keeps the plain gatherer vest. `world.fragments` v1 stores the index as an int, so the look
  survives a save with no format change; a v0.8.0 build reading such a save draws the badge.
- **One harvest representation.** `Renderer.renderCarcasses` keeps skipping
  `Carcass.fragmented()`; the anchored torso piece is the body, the record lies at the drawn
  torso's lowest point, and `EntityManager.nearestCarcass` finds it once the torso is at rest.
- **QA.** `SpeciesDismemberQaScene` (`dismember_species`, `dismember_species_wall`,
  `dismember_species_close`), registered in `QaHarness.applyBenchmarkScene`.

## 8. Lethal-blast rules

1. Keep every number: lethal radius `power × 1.5` from the body centre, inclusive (`<=`);
   outside it the existing feet-distance falloff over `power × 2.4`, 5-ray occlusion,
   `< 0.5` cut-off and knockback. Kegs stay lethal whatever set them off.
2. The gate applies to every living `Entity` in the victim list: all creatures including
   airborne birds, every NPC family, and the player **only** when not invulnerable. The Creative
   check stays before any kill, record or remains.
3. Kill through the ordinary damage path (`hurt(health + 1, byPlayer)`; for the player
   `Player.hurt`, bypassing armour like the NPC rule bypasses cover). Record the blast only if
   that call moved the body from alive to dead. Already-dead or administratively removed
   bodies are skipped (existing `e.dead` check), so a body killed earlier by fire, a bullet or
   an earlier chained keg never gains a record, and the first record is never overwritten.
4. Consequences stay single: settled NPC attack notifications through the existing
   `playerHitNpcs` set, perception update for NPCs, creature `fear`; kill credit through the
   normal death routing.
5. Non-blast deaths, and blast deaths from falloff damage outside the radius, stay whole-body
   (ragdoll → corpse/carcass).
6. **Balance consequence to surface to the user:** a Survival player inside 3.9 blocks of their
   own scrap bomb, or 5.7 of a keg, now dies. This follows R1 and is not softened here.

### 8.1 As built in milestone 03

- **Record.** The record stays the v0.8.0 fields, moved from `Npc` to `Entity`:
  `dismemberOnDeath`, `blastX/Y/Z`, `blastStrength` (names kept, so every v0.8.0 caller and
  test still reads them). Only `Entity.recordBlastDeath(x, y, z, strength)` sets it, and it
  refuses (returns false) unless the body is `dead` with `health <= 0` and has no record yet.
  `Entity.clearBlastDeath()` forgets it; `Npc.killBy` moved to `Entity.killBy`.
- **Gate.** `ExplosionSystem.explode(..., boolean lethalToLiving)` (renamed from
  `lethalToHumans`); `insideLethalRadius(Entity, ...)` and `killOutright(Game, Entity, ...)` are
  typed on `Entity`. The existing skip of `e.dead` and of an invulnerable player runs first, so a
  Creative player is never killed, recorded, flashed or thrown. Numbers unchanged.
- **Consequences in `killOutright`.** NPC: `playerHitNpcs` + `lastKnown` (unchanged). Creature:
  `fear = 1`, `bleedTimer ≥ 12`, as for a survivable hit. Player: `Player.hurt` via `killBy`
  (armour bypassed, no armour wear, no bleed roll), `damageFlash = 1`, `renderer.addShake`.
  Knockback as before for everyone.
- **Player record lifetime.** Spent by `BodyFragmentSystem.spawnPlayerRemains` at the death
  transition; also cleared by `Game.respawn` and every `Player.restoreCreativeBody`, so a record
  can never survive into a later, non-blast death.
- **Stale targets.** `EntityManager.forgetTarget` clears every NPC `combatTarget` and creature
  `targetEntity` that pointed at a body leaving the world (any death or departure, not only
  blasts). Target acquisition was already re-queried each tick through `nearestCreature` /
  `nearestNpc`, which skip dead bodies.

## 9. Death, rewards and one-body harvest

- Death routing stays in `EntityManager.fastTick` (NPC, creature) and the `Game.frame` player
  transition. Each chooses exactly one representation from the record: fragments if the lethal
  blast record is set and the death is real, otherwise the existing ragdoll. Bookkeeping (quest
  credit, bird meat, trust, loot, `onSettledNpcDied`, mission callbacks) runs once, before and
  independent of the representation.
- Harvestable species (all but BIRD): the fragmenting death creates **one** `Carcass`
  immediately with the species' unchanged `meatYield`/`hideYield` and the animal's
  `stuckArrows`/`stuckArrowType`. It is flagged fragmented (never drawn as an intact carcass)
  and anchored to the torso fragment by a stable remains id; its position follows the torso
  when it settles. Harvesting keeps using `EntityManager.nearestCarcass` and
  `WorldInteractions.harvestCarcass`; limbs carry no yield.
- The anchor torso is exempt from the settled-fragment cap and distance cull while its carcass
  exists; emptying the carcass releases the torso to ordinary decay; carcass decay removes
  both. Anchored remains are capped at `MAX_ANCHORED_REMAINS` 60 (*proposed*); beyond it the
  oldest anchored carcass and its torso are removed together, deliberately and tested.
- BIRD keeps its rule: meat on the death tick when the player killed it, no carcass, lodged
  arrows lost as today; its fragments are visual only.
- Player remains: spawned once at the real death transition from humanoid geometry with an
  explicit neutral appearance; independent of the live `Player`, which respawns as today.

### 9.1 As built in milestone 03

- **Routing.** `EntityManager.fastTick`: NPC → `fragments.spawnFromNpc(g, n,
  fragments.deathPose(n), blast…)`; creature (`onCreatureDied`, after credit, bird meat and the
  harvest log) → `fragments.spawnFromCreature(g, c, fragments.deathPose(c), blast…)`; otherwise
  the ragdoll as before. Player: `Game.enterDeathIfDue()` (extracted from `frame`, package
  private) performs the unchanged `AppState.DEATH` transition and calls
  `fragments.spawnPlayerRemains(this, player)`.
- **Death pose.** `BodyFragmentSystem.DeathPoses` (interface, `of(Creature)`, `of(Npc)`), set once
  by `Game`'s constructor to `AnatomyModels.deathPoses(() -> totalTime)`. Without a source (bare
  fixture) a body uses its rest pose. `spawnFromNpc(g, n, x, y, z, strength)` keeps its v0.8.0
  meaning — the standing rest pose — for QA scenes and fixtures; production deaths use the
  explicit-pose overload. Player remains use `FragmentAnatomy.humanoid().restPose()` and
  `NpcAppearance.setNeutral()` (no archetype, no raider/trader/sick look,
  `NEUTRAL_CAMP_INDEX` 2 = the plain gatherer vest every unlisted archetype wears; 04 changed
  it to −2, the same vest without a camp badge, §7.2).
- **Launch.** One generic `launch(Game, Entity, FragmentPose, …)` for every family. The blast
  speed divides by `max(mass, MIN_LAUNCH_MASS)`, `MIN_LAUNCH_MASS` = 1.25 kg (*new tuning*):
  below every human piece (lightest 1.39 kg), so people launch exactly as before, while a wing or
  a hare leg now leaves at most `9 × strength / 1.25` (18.7 m/s scrap bomb, 27.4 m/s keg) instead
  of the 42 m/s clamp. Heavy pieces keep the inverse-mass rule, so a thornhorn torso still barely
  moves (the v0.8.0 tuning question, unchanged).
- **Harvest record.** `Carcass.remains` (torso piece) ↔ `BodyFragment.harvest` (carcass), both
  transient references; `Carcass.fragmented()`, `Carcass.atRest()`. Created by
  `spawnFromCreature` for `CreatureType.leavesCarcass()` (all but BIRD, the rule
  `RagdollSystem` now also reads), with species yield and lodged arrows. Its position follows the
  torso on every step and at rest — `(torso.x, torso.y − halfHeight, torso.z)`, the ground under
  the torso, which is also where a carcass drawn without a solved pose stands. The torso is
  piece 0. **Deviation from §9:** the record exists at once, but `EntityManager.nearestCarcass`
  (harvest, the F prompt, scavenging) skips it until the torso settles — the same "still
  falling" rule a ragdoll's carcass has — and `InteractPromptBuilder` shows "The *species* is
  still falling" meanwhile.
- **Lifetime.** Settled cap: `admitSettled` evicts the oldest piece without a record (fallback,
  unreachable while `MAX_ANCHORED_REMAINS < MAX_SETTLED_FRAGMENTS`: release the record to a
  whole carcass rather than orphan it). `slowTick`: a torso whose record is still in
  `entities.carcasses` is neither rotted nor distance-culled and copies the record's decay;
  once the record has left the list the torso is removed if the record rotted (`decay <= 0`),
  otherwise released to ordinary decay (emptied, or cleared by other code). Over
  `MAX_ANCHORED_REMAINS` (60) at creation, the oldest record and its torso are removed together.
- **Rendering and saving before 04/05.** `Renderer.renderCarcasses` skips fragmented records.
  `Renderer.drawFragment` skipped pieces with `piece == null` (every animal piece) until 04,
  which draws every family (§7.2). `FragmentsSection.readable` still leaves animal pieces out of
  `world.fragments` v1 (unchanged format); since 05 they, the poses and the record links are
  saved in `world.remains` (§14.1). Only a save without that section — v0.8.0's, or one re-saved
  by it — still loads a fragmented record as a whole carcass in the fixed sprawl: one body,
  reward kept. Player remains are human pieces with the neutral look, in both sections.

## 10. Combustion model

State per body (`BodyCombustion`, *proposed* fields): `heat` [0, 1] (ignition progress),
`burning`, `fuel` seconds [0, `MAX_FUEL`], `peakIntensity` and effective `intensity`
[`MIN_INTENSITY`, 1], `soak` seconds of exposed rain, `owner` (source kind, `byPlayer`,
`sourceId`), `burnSeconds` this episode, `scorch` [0, 1] (monotonic presentation input),
`lastExposureX/Y/Z`, previous sample position. No lists, no entity references.

**Exposure resolution (per body per fast tick).** Every qualifying source contact produces a
candidate (kind, intensity, byPlayer, sourceId, point). Exactly one dominant candidate is
applied, chosen by: (1) kind priority DIRECT_HIT > LIQUID > BLOCK_FIRE > CAMPFIRE > TORCH;
(2) higher intensity; (3) player-attributed before environmental; (4) lower `sourceId` (older
spill or cell). Contacts never add up, so straddling four patches or three burning cells burns
like one.

**Ignition.** Non-burning body: `heat += gainRate(kind) × intensity × dt` while in contact,
`heat -= HEAT_DECAY × dt` otherwise; ignite when `heat ≥ 1`. LIQUID and DIRECT_HIT ignite on
first contact. On ignition: `burning = true`, `heat = 0`, `fuel = grant(kind)`,
`peakIntensity = intensity`, `owner = candidate`.

**Refresh (already burning).** `fuel = min(MAX_FUEL, max(fuel, grant(kind)))`,
`peakIntensity = max(peakIntensity, intensity)`, `owner = candidate` (the latest dominant
contact owns the burn). Never a second timer, never additive stacking.

**Decay.** Without contact: `fuel -= dt × drain`, where `drain` is 1, or `SHALLOW_WATER_DRAIN`
when only the feet cell is water. Effective intensity:
`I = MIN_INTENSITY + (peakIntensity − MIN_INTENSITY) × clamp(fuel / FADE_SECONDS, 0, 1)`.
Extinguished when `fuel ≤ 0`.

**Damage equation (per fast tick, only while burning and alive):**

```
dmg = (contactThisTick ? CONTACT_DPS[family] : AFTERBURN_DPS[family]) × I × dt
NPC / creature: hurt(dmg, owner.byPlayer)     Player: hurt(dmg, false) + damageFlash (no armour, no bleed)
```

Death from `dmg` is an ordinary whole-body death. It can never carry a blast record: only the
killing blast writes one, and combustion stops ticking a body the moment it is dead. Kill
credit = `owner.byPlayer` at the killing tick, carried through `hurt` → `lastHitByPlayer` like
every other damage source (a later environmental hit can take credit, as with any damage
today).

**Extinction.** Torso immersion — water in the body-centre cell (`pos.y + height / 2`, the
same cell `VoxelPhysics.refreshEnvironment` samples) — extinguishes at once and clears `heat`.
Feet-only water drains fuel faster. Exposed rain — `FireSystem.isRainedOn` at the head cell
(`floor(pos.y + height − 0.01)`) — accumulates `soak`; `soak ≥ RAIN_BODY_EXTINGUISH` puts the
body out; `soak` decays when sheltered. Roofed or sheltered bodies keep burning in rain.
Rain-frozen patches and cells give no exposure, so they cannot re-ignite a body.

**Attribution and crime.** Ignition by a player-owned candidate, or an owner change to the
player, reports at most one attack per burn episode for a settled NPC and updates
`lastKnown` only when the player is perceivable (`Player.isPerceivableByAi`). Environmental
owners never report player attacks. Block fires ignited by a player's patch carry `byPlayer`
and pass it to cells they spread to.

**Death and removal.** Dead or removed bodies leave combustion immediately (their state is not
ticked). What remains visible after death is presentation residue only (09/10).

## 11. Medical BURN versus active combustion

- `Affliction.BURN` stays the medical injury; active combustion is not an affliction.
- A Survival player receives `BURN` once per burn episode when `burnSeconds` reaches
  `BURN_INJURY_AFTER` 1.0 s (*proposed*, deterministic, no roll), with the existing duration
  `FireConstants.BURN_AFFLICTION_SECONDS_MIN` + range drawn from the player's seeded
  affliction stream. A longer duration never shortens an existing one (`addAffliction` merges
  with max).
- **No double charge:** while the player is actively burning, `Player.tickAfflictions` neither
  applies `BURN_DAMAGE_PER_SECOND` (0.18) nor counts the BURN timer down; both resume when the
  flames stop. Combustion damage is the only burn damage during flames. The `BURN_REGEN_MULT`
  stamina penalty applies whenever BURN is present (it is not damage).
- The herbal poultice (`PlayerTreatmentSystem`) cures the injury only; it never extinguishes.
- NPCs and creatures have no afflictions: combustion damage is their only burn damage.

## 12. Panic policy (NPCs and animals only)

- Priority: immediately after the `dead` guard and before `interactFreeze`, `SettledNpcAI`
  dispatch, captive idling, trader/raider logic, combat, work, sleep and species decisions
  (`NpcAI.update`, `CreatureAI.update`). `SettledNpcAI` has a single external caller
  (`NpcAI.update`), so the `NpcAI` hook covers every NPC family.
- While burning, and for `PANIC_RECOVERY` after extinction: no melee, no ranged fire or reload
  completion, no trading freeze (close an open NPC screen through `Game.closeScreens` when its
  speaker ignites), no work or sleep. Bookkeeping (resident records, missions, timers owned by
  other systems) is not touched.
- Goals: every `PANIC_GOAL_INTERVAL` (+ seeded jitter) or on blocked progress (≤ one replan per
  `PANIC_REPLAN_COOLDOWN`), pick a point `PANIC_GOAL_DISTANCE` away, heading away from the last
  exposure point plus a seeded angle within ± `PANIC_SPREAD`; move with existing `Steering`
  (and `Pathfinder` where it is already used), limited to `PANIC_TURN_RATE`; reject targets in
  unloaded columns (`World.getBlock` is AIR there). Birds use `Steering.flyToward` with a
  climbing goal. Captives stay caged: they move inside the cage, physics blocks the bars, panic
  never frees them.
- Randomness: one new panic stream seeded from the world seed with salt `0x50414e494353L`
  ("PANICS"), drawn in entity-list order; rendering and camera never draw from it.
- Recovery: after `PANIC_RECOVERY`, re-run the ordinary decision from current world state
  (fresh target validity, settlement existence, player perceivability). Stale combat targets or
  paths are not restored. A medical injury alone never causes panic.
- Panic never targets or reveals an imperceptible player; heat avoidance uses exposure points.

## 13. Player control and Creative

- Combustion never touches `PlayerMovementSystem`, `Player.moveSpeedMul`, `canSprint`,
  `sprinting`, `vel`, camera yaw/pitch or input. Burning feedback is damage, `damageFlash`,
  log text and presentation (10).
- Creative: `CombustionSystem` clears and skips the player's state while invulnerable; the
  lethal gate skips the player before any record; no player remains are spawned. Returning to
  Survival starts with a clear state.

## 14. Persistence and transient state

| State | Saved? | On load / new world | On respawn / Creative |
| --- | --- | --- | --- |
| Active combustion, heat, soak, panic, owners | no | cleared (fresh entities) | cleared |
| Burning block cells, liquid patches | no (existing) | cleared | unchanged |
| Health (player, NPCs, creatures), player afflictions | yes (existing) | restored | existing rules |
| Settled fragments, species remains, player remains, anchored carcass link | yes (05) | restored; human v1 unchanged | unaffected |
| Death-time visual residue | no | cleared | n/a |

Saving never extinguishes a live burn: `SaveSystem.write` only settles ragdolls and fragments.

### 14.1 As built in milestone 05 (remains persistence)

**Sections.** Both live in the v3 extension envelope (`V3ExtensionSections`), after
`world.bodies`; the frozen v3 body is unchanged.

| ID | Version | Holds | Written | Read |
| --- | --- | --- | --- | --- |
| `world.fragments` | 1, unchanged | people's settled pieces by legacy `BodyFragment.Piece` ordinal, rest pose | always, byte for byte as v0.8.0 wrote the same pieces (pinned against a hand-composed literal) | always validated; restored only when `world.remains` is absent |
| `world.remains` | 1, **new ID** | every settled piece of every family, the poses they died in, the harvest links | always, after `world.fragments` | authoritative when present |

A new ID rather than `world.fragments` version 2, because the v1 reader refuses any other
version of its section — and with it the whole save — while every reader skips an unknown ID
by its length. People are therefore written twice (at most 600 × 47 bytes of duplication).
`V3ExtensionSections.readSettledPieces` resolves the two after the envelope loop, so the result
does not depend on the order sections appear in; the carcasses a link points at come from the
v3 body, read before any section.

**Layout of `world.remains` v1** (all numeric, big-endian `DataOutputStream`):

```text
int version = 1
int poses (<= 600);  per pose:  int BodyFamily ordinal, int joints (0 = the family's rest pose,
                                otherwise the table's joint count),
                                joints x 7 floats: rotX, rotY, rotZ, poseX, poseY, poseZ, scale
int pieces (<= 600); per piece: int pose index, int FragmentPiece.id, 3 floats position,
                                4 floats orientation (unit quaternion), float decay,
                                appearance as world.bodies writes it (int, 3 booleans, int)
int links (<= 60);   per link:  int piece index, int carcass index (v3 body order),
                                int lodged arrows (<= 1024), int arrow item (0 = none, else ordinal + 1)
```

- **Identity** is (`BodyFamily` ordinal, `FragmentPiece.id`), both append only and pinned in
  `SerializedEnumOrderTest` (family order and every family's piece names); a person's piece id
  is its v1 ordinal. The family comes from the pose entry, so nothing can be read as a person's
  piece unless its pose says `HUMANOID`.
- **Pose**: stored once per pose object — the pieces of one body share one, and share one again
  after loading; a rest-valued pose is stored as 0 joints and loads as `anatomy.restPose()`.
  Bounds: angles ±40 rad, offsets ±4 m, joint scale and every piece's derived scale in
  [0.5, 2] (live breathing is about ±1.2 %). The collision box follows the stored scale, so the
  loaded box and its drawn frame equal the saved ones.
- **Settled order** is kept, so the settled cap evicts after a load what it would have evicted
  before the save. Pieces keep position (exact), orientation (normalised on write, ≤ 1e-6),
  decay, look, family, piece and therefore cuts; the sweep box is refit by `restoreSettled`.
- **Harvest link**: re-ties `Carcass.remains` ↔ `BodyFragment.harvest` through
  `BodyFragmentSystem.restoreSettled(torso, record)` and puts the record back under the torso.
  Only a torso whose record is in the carcass list and tied back to it is written. The link also
  carries `stuckArrows`/`stuckArrowType`, which the base carcass record never stored: an
  anchored record now keeps its arrows across a save; a **whole** carcass still loses them, as
  it always has (pre-existing, unchanged). Meat, hide and rot stay in the v3 carcass record, so
  a partly harvested animal comes back partly harvested; a load never creates a carcass.
- **Player remains** are humanoid pieces with the neutral look (`NEUTRAL_CAMP_INDEX` −2) in both
  sections. Nothing refers to the `Player`, whose health and position are saved independently;
  the blast record stays transient, so a player loaded dead does not come apart again.

**Policy for what a reader cannot use** (documented in the `RemainsSection` class comment):

- *Malformed* fails the whole load (the live world is untouched: a live-session load proves the
  payload on a throwaway `Game` first): counts over their caps or negative, indexes out of range,
  a negative family or piece id, non-finite or out-of-bound numbers, a quaternion whose squared
  length is more than 1e-3 from 1, a pose drawing a piece out of scale, two links to one torso or
  one record, a record on a limb, on another species or on a body that leaves no carcass,
  trailing or missing bytes, an unknown section version.
- *Unknown but well formed* is skipped, bounded by the same caps: a family ordinal past
  `BodyFamily` (its pieces are read, checked and dropped; a link to one leaves its record a whole
  carcass, arrows kept); a piece id past its family's list (dropped). A known family whose pose
  lists another joint count — a table from another build — keeps its pieces in the family's rest
  pose, as version 1 people load. Piece and link records are fixed-size, so skipping never loses
  the stream position.
- *Writer*: a piece the reader would refuse is left out (as v1 does); a pose the reader would
  refuse is written as the family's rest pose (the piece keeps its place, not its articulation).
  Pieces in flight are never written.

**Settle-before-save** is unchanged: `SaveSystem.save` settles ragdolls and fragments before it
writes, so a mid-flight save writes each piece where it lands, once, and the anchored record
where its torso lands. **Load** runs `newWorld` (`WorldBootstrap` resets both fragment lists and
clears carcasses) before any section, so loading another world keeps none of the last one's
pieces or links.

**Compatibility matrix** (verified by tests unless marked):

| Save | Loaded by this build |
| --- | --- |
| v2, or v3 without either section (before 0.8.0) | no pieces (`SaveMigrationTest`, `HistoricalV020SaveCompatibilityTest`, `FragmentsSectionTest`) |
| v0.8.0 (`world.fragments` only), or this build's save re-saved by v0.8.0 | people in the rest pose at their saved place; no animal pieces; each animal's record a whole carcass in the fixed sprawl — one body, same meat and hide (`RemainsSectionTest`) |
| this build | everything above, animals and poses included |

| Save | Loaded by an older build |
| --- | --- |
| this build, by v0.8.0 | skips `world.remains` by length: people in the rest pose (a player's remains wear the camp badge there, §7.2), animal pieces absent, records whole carcasses. *Source reasoning, not run* — no v0.8.0 binary is exercised by the suite. |

**APIs** (package `save` is package-private): `RemainsSection` (`ID`, `VERSION`, `write(Game)`,
`read(byte[], Game)`, `MAX_PIECES` 600, `MAX_POSES` 600, `MAX_LINKS` 60, `MAX_POSE_JOINTS` 64,
`MAX_POSE_ANGLE` 40, `MAX_POSE_OFFSET` 4, `MIN_POSE_SCALE`/`MAX_POSE_SCALE` 0.5/2,
`QUATERNION_TOLERANCE` 1e-3, `MAX_STUCK_ARROWS` 1024); `FragmentsSection.parse(byte[])`
(validate without restoring), `placementReadable`, `readQuaternion`;
`V3ExtensionSections.readSettledPieces`; public `BodyFragmentSystem.restoreSettled(BodyFragment,
Carcass)` (throws `IllegalArgumentException` unless the piece is the core of the record's
species and both sides are free).

## 15. Proposed tuning (all values proposed)

| Constant | Value | Why |
| --- | --- | --- |
| Heat gain to ignite: DIRECT_HIT, LIQUID | immediate | a molotov must ignite |
| Heat gain: BLOCK_FIRE | 4.0 /s (0.25 s of contact) | stepping into a burning bush catches |
| Heat gain: CAMPFIRE | 2.0 /s (0.5 s) | standing in the fire catches, brushing past does not |
| Heat gain: TORCH | 1.0 /s (1.0 s) | only deliberate contact with the flame head |
| `HEAT_DECAY` | 2.0 /s | brief grazes do not accumulate |
| Source intensity: DIRECT_HIT / LIQUID / BLOCK_FIRE / CAMPFIRE / TORCH | 1.0 / patch intensity (0.5–1) / 1.0 / 0.8 / 0.6 | |
| Afterburn fuel granted | 6 / 6 / 4 / 3 / 2 s | visible burning after leaving |
| `MAX_FUEL` (refresh cap) | 8 s | no endless stacking |
| `FADE_SECONDS`, `MIN_INTENSITY` | 3 s, 0.35 | full flames, then a visible taper |
| `CONTACT_DPS` NPC / creature / player | 10 / 12 / 6 | equals the legacy pool rates, so pool lethality is unchanged |
| `AFTERBURN_DPS` NPC / creature / player | 3.0 / 2.5 / 2.0 | survivable afterburn for sturdy bodies |
| `SHALLOW_WATER_DRAIN` | 3 × | feet in water help, torso immersion ends it |
| `RAIN_BODY_EXTINGUISH` | 1.5 s exposed | between the patch (1 s) and block (2 s) values |
| `BURN_INJURY_AFTER` | 1.0 s | section 11 |
| `PANIC_GOAL_INTERVAL` | 0.6 s + seeded 0–0.6 s | irregular, not per frame |
| `PANIC_GOAL_DISTANCE` | 4–8 blocks (birds 6–10 horizontal, climb 3–6) | |
| `PANIC_SPREAD` | ± 70° | crowds do not mirror each other |
| Panic speed | creature `type.speed × 1.6`; NPC `speed × 1.35` | matches existing flee multipliers |
| `PANIC_TURN_RATE` | 270 °/s | coherent motion |
| `PANIC_REPLAN_COOLDOWN` | 0.25 s | bounded replanning |
| `PANIC_RECOVERY` | 1.5 s | brief settling after extinction |
| `MIN_SEPARATE_PIECE` | 0.10 m | section 7 |
| `MAX_ANCHORED_REMAINS` | 60 | section 9 (as built: `BodyFragmentConstants`) |
| `MIN_LAUNCH_MASS` | 1.25 kg | section 9.1; added in 03 |
| `FragmentModels.SHELL_REACH` | 0.03 m | section 7.2; added in 04 (vest and haunch are 0.02) |
| `FragmentModels.CONTACT_SPEED` | 1 m/s | section 7.2; added in 04, well under the 4.5 m/s launch bias |
| Combustion salt / panic salt | `0x4255524e494eL` / `0x50414e494353L` | distinct from every existing salt in the source tree |

Worked outcomes with these values (0.5 s of pool contact at intensity 1, then a full
afterburn): villager (30) survives with ≈ 10 after ≈ 6 s of visible burning; captive (24)
survives with ≈ 4; thornhorn (42) with ≈ 23; wolf (22) with ≈ 3; stalker (30) with ≈ 11;
player (100) loses ≈ 13 plus the medical injury. Deer (14) dies after ≈ 3.7 s of burning;
hare (6) and bird (4) die quickly. The required surviving test actor is a villager or
thornhorn; no species is immune.

## 16. Finite budgets

Gameplay (enforced by tests; wall-clock numbers only in opt-in `performanceTest`):

- Combustion tick: zero steady-state allocation; per body at most the liquid patch list
  (≤ 160 AABB tests) plus the cells under its swept, source-inflated box (≤ 5 × 5 × 5 = 125
  lookups at the 2-block sweep limit); never generates chunks, never scans `campfireFuel`
  world-wide. *Proposed* wall-clock target: ≤ 0.2 ms per fast tick with 40 NPCs, 35 creatures,
  160 patches and 220 burning cells on the reference host.
- Existing caps unchanged: `MAX_LIVE_FRAGMENTS` 120, `MAX_SETTLED_FRAGMENTS` 600,
  `RagdollConstants.MAX_LIVE` 12, `FireSystem.MAX_ACTIVE_FIRES` 220,
  `LiquidFireConstants.MAX_PATCHES` 160 (22 per spill), `MAX_TRACKED_NPC_SPILLS` 64,
  `ProjectileSystem.MAX_LIVE` 96, `ParticleSystem.MAX` 4000 (splash limit 2800, blood 3600),
  `SettlementManager.MAX_ACTIVE_NPCS` 40, `ExplosionSystem.MAX_ACTIVE_FUSES` 64. With ≤ 12 pieces
  per body, 120 live fragments hold 10 bodies in flight.
- New collections must be capped and covered by `RuntimeBoundsTest`; extend
  `RuntimeBudgetSnapshot` with burning bodies (≤ living bodies) and anchored remains (≤ 60).
- Panic: ≤ 1 goal per actor per 0.25 s; path work within existing `Pathfinder` limits.
- Eviction and caps never undo a death or skip a living body's combustion; only presentation
  degrades.

Presentation (*proposed*, 10 may retune with captures):

- Full attached emitters for the 24 nearest burning bodies within 48 blocks; reduced cadence to
  96 blocks; none beyond, while gameplay continues.
- Per body per 0.12 s emitter pass: flames `1 + round(4 × surfaceArea × I)`, capped at 6;
  smoke rising as `I` falls; embers at ≤ 0.15 chance. Body fire stops adding at
  `ParticleSystem.SPLASH_LIMIT` and never touches the blood reserve.
- At most 4 simultaneous body-fire audio sources nearest the listener.
- Death residue ≤ 4 s flame + ≤ 3 s smoke, at most 32 residues.
- *Proposed* cost target: ≤ 0.5 ms CPU per frame for 24 burning bodies on the reference host.

## 17. Tests that encode old behaviour

Replace, keeping unrelated coverage:

- `BlastLethalityTest.creaturesAndPlayerKeepTheExistingExplosionModel` (03): becomes the
  all-living lethal-radius contract. *Done:* replaced by
  `creaturesAndThePlayerInsideTheLethalRadiusDieAndComeApartToo`; the per-family matrix is
  `AllLivingBlastDeathTest`.
- `BlastLethalityTest.blastKillsBecomeTenFragmentsNotARagdoll` and
  `BodyFragmentPhysicsTest.anNpcSplitsIntoExactlyTenPiecesAtItsJoints` stay true for humans;
  species counts come from the definitions (02/03).
- `MolotovTest` assertions of `ENTITY_DPS_* × TICK` per medium tick, the per-tick BURN roll,
  and `CombatFireIntegrationTest.aBurnDeathInLiquidFireFallsWholeEvenWithTorsoWounds` timing
  (07): contact damage moves to the fast-tick combustion equation; one-burn-per-tick
  de-duplication, attribution and Creative immunity must still be asserted.
- `FireSystem.damageNear` behaviour relied on by fire/weather tests (07): stacking and
  through-floor damage are intentionally removed.

## 18. Dependency handoff

| Milestone | Consumes | Produces for later milestones |
| --- | --- | --- |
| 02 anatomy (done) | §3.2, §7; `BodySkeleton.of`/`humanoid`, `CreatureModels.of`, `NpcModels.get`, `EntityModel.part`, `ModelPart` pivot/box/split fields, `BodyFragment.Piece`, `FragmentModels` | `BodyFamily`, `FragmentAnatomy`, `FragmentPiece`, `FragmentCut`, `FragmentPose`, `AnatomyModels` (§7.1; exact API in the progress file); tests over `CreatureType.values()` + humanoid. |
| 03 blast deaths (done) | §8, §9; 02 definitions | Shared `Entity` blast record, generalized `ExplosionSystem` gate, `BodyFragmentSystem` spawn for any family, anchored `Carcass`, player-remains spawn at the `Game.frame` death transition (§8.1, §9.1). |
| 04 fragment rendering (done) | 02 definitions, 03 fragments | Definition-driven model selection and isolation in `Renderer.drawFragment`, cut faces per family, bounds from full geometry, suppressed intact carcass, all-species QA scene (§7.2). |
| 05 persistence (done) | §7 save strategy, 03 remains ids | `world.remains` v1 for every family's settled pieces, their poses and the anchored harvest links (with lodged arrows); `world.fragments` v1 unchanged and pinned by a literal fixture (§14.1). |
| 06 combustion state | §5, §6, §10, §11, §15 | `Entity.combustion`, `CombustionSystem` (expose/ignite/extinguish/query/snapshot), fast-tick wiring, medical-BURN gating, reset/seed in `WorldBootstrap`. |
| 07 sources | §4, §10; 06 API | Fast-tick swept contact for patches, burning cells, campfires, torches; direct-hit ignition; `damageNear`/`burnOccupants` stop hurting; `Burn.byPlayer`; attack de-dup per burn episode. |
| 08 panic | §12; 06 snapshot, 07 ignition | Panic hooks in `NpcAI.update`/`CreatureAI.update`, per-actor intent, seeded stream, NPC screen closure, bird flight escape. |
| 09 lifecycle | §6, §9, §14 | Dead guard before creature AI, residue snapshot at death, resets on load/new world/respawn/Creative/deactivation, pause and sleep behaviour. |
| 10 presentation | §16 presentation budgets, 02/04 geometry, 06/09 snapshots | Attached flames, scorch, smoke, embers, audio, first-person cues, QA scenes and captures. |
| 11 validation | the whole contract | Production-path matrix tests, bounds/perf evidence, docs, updated `COMBAT_LETHALITY_AND_MOLOTOV.md` limits. |

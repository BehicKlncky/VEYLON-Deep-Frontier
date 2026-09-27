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

### 4.1 As built in milestone 07 (sources connected)

Every source below reports through the 06 API (§10.1); nothing else hurts a body for touching
a flame. Sampling runs at the start of each body's fast tick in `CombustionSystem`
(`sampleFlames`), before the candidate is resolved, for every body that can burn.

| Source | Production entry point | Flame volume | Touches only while | Kind, heat, intensity, afterburn | Owner and `sourceId` |
| --- | --- | --- | --- | --- | --- |
| Fire bomb breaking on a body | `ProjectileSystem.onEntityHit` (FIRE_BOMB) calls `CombustionSystem.ignite` **before** `shatter`, whether or not the spill finds ground | the struck body, at the hit sample | the torso is not under water (§10.1); not the Creative player | `DIRECT_HIT`, at once, 1.0, 6 s | the thrower (`Projectile.fromPlayer`); the id the bottle's spill will get (`LiquidFireSystem.nextSpillId()`) |
| Burning liquid | `LiquidFireSystem.exposeContacts` | the patch cell's footprint from `CONTACT_BELOW` 0.1 under its floor to `CONTACT_HALF_HEIGHT` 0.6 over it | in `patches()`; `wet == 0` **and** not rained on now; its cell still open and dry over solid ground | `LIQUID`, at once, the patch's intensity (1 → 0.5 at the rim), 6 s | `Patch.byPlayer`; the spill id |
| Burning block | `FireSystem.exposeContacts` → `touchBurningBlock` | the block's own cell when it is not solid (grass, bush); `FLAME_FACE_REACH` 0.25 out of each side and bottom face **into a non-solid neighbour only**; `FLAME_PLUME_HEIGHT` 0.75 over the top **only if the cell above is not solid** | in the burning list; `wet == 0` and not rained on now; still flammable | `BLOCK_FIRE`, 4 /s, 1.0, 4 s | `Burn.byPlayer` and `Burn.origin` (the bottle), else environmental; the bottle for a player's fire, else a packed cell id |
| Fueled campfire | `FireSystem.exposeContacts` → `touchFlameBlocks` | `[0.25, 0.75] × [0.15, 1.0] × [0.25, 0.75]` of its cell | block `CAMPFIRE` with a `campfireFuel` entry above 0; rain does not stop it (world rule unchanged) | `CAMPFIRE`, **1 /s** (1.25 s at 0.8), 0.8, 3 s | environmental; packed cell |
| Placed torch | same | `[0.38, 0.62] × [0.66, 1.0] × [0.38, 0.62]` of its cell | block `TORCH` (no lit state exists; rain does not stop it) | `TORCH`, 1 /s (1.67 s at 0.6), 0.6, 2 s | environmental; packed cell |

The "not sources" list above was rechecked in source and stands: no other block, item or
effect reports a contact. A held torch has no world volume; a lantern, trail marker, glow
fungus, alarm bell and a furnace are covered by a test that stands people in or beside them
for ten seconds.

**Sweep** (`entity/BodySweep`, one reused instance). The **flame box** is the entity box —
`width` square, `height` tall from the feet, the box physics, projectiles and the 0.8.0 pool
contact use — except for a flying creature (`CreatureType.flying`): its half-width grows to
the widest lateral reach of its anatomy table's rest pose (bird: 0.39 against a body half of
0.17), and it reaches the same wing length (0.22) below the feet and above the back, the
envelope a flapping wing sweeps. The box is swept in a straight line from where the previous
fast tick sampled it (its bottom centre, `BodyCombustion.sample*`) to where it is now; each
source asks `touches(volume)`, an exact slab test on open intervals (touching faces do not
count) that also yields the contact point (the centre of the common volume half-way through
the overlap). A move longer than `MAX_SWEEP` 2 blocks samples only its end (teleport,
respawn, a jump across the map). **Maximum sampling gap:** none in space along the sampled
path, one fast tick (0.05 s) in time; linear motion between two samples is assumed (a turn or
collision slide inside 0.05 s bends it by centimetres). A tick in which the sweep touched a
flame counts as a whole tick of contact, so heat is over-credited by at most one tick.
**Order:** the player has moved by the frame before the fast tick; people and animals move in
the entity step after combustion, so their sweep covers the previous tick's move. Every move
is swept exactly once. Tested: a crossing of a one-cell patch at 12 blocks/s that falls wholly
between two medium ticks catches every kind of body; a dash from just short of it to just past
it in one fast tick catches; the same dash a hair to the side does not; a 4-block jump does not.

**Legacy contact removed.** `FireSystem.damageNear` (with `CONTACT_RANGE_SQ`,
`PLAYER/CREATURE/NPC_BURN_DPS`, `BURN_AFFLICTION_CHANCE` and its random roll) and
`LiquidFireSystem.burnOccupants` / `standsIn` / the (NPC, spill) memory
(`MAX_TRACKED_NPC_SPILLS`, `trackedNpcSpills()`, `ENTITY_DPS_*`) are gone. The medium ticks
keep spread, burn-down, rain, keg arming and block ignition only. Burn damage is the
combustion equation alone, and the medical injury is `Player.inflictBurnInjury` alone (a test
throws a real bottle through the player's command and the whole game loop and finds exactly
the contact damage after four medium ticks, over a burning log buried under the floor). The
two removed random rolls shift the fire systems' streams only in scenes where the player stood
in a flame.

**Ownership.** Several flames touching one body in one tick resolve by §10.1's dominance
(kind, intensity, player, source id, point), so the order flames appeared in decides nothing
(test: the same three overlapping flames made in two orders). A refresh hands the fire to the
latest dominant contact, as §10.1 built. A block fire keeps the attribution of whatever lit it
first: a patch passes on `byPlayer` and its spill id (`FireSystem.ignite(g, x, y, z, byPlayer,
origin)`), and a spreading cell inherits its parent's, so a resident walking into grass the
player's bottle set alight, well clear of the liquid, burns for the player: the player's
attack, the player's kill. Fires lit by blasts, lightning and meteors stay environmental
(decision: a player's blast already reports its own victims; 0.8.0 behaviour).

**Attacks.** When a candidate is applied to a person and the fire is (now) the player's,
`CombustionSystem.reportAttack` runs once per (person, bottle): `BodyCombustion.firstReportOf`
remembers the last `REPORTED_BOTTLES` 4 bottles per body (cleared with `clear()`). It points
`lastKnown` at the flame's contact point only if the player is perceivable, then calls
`SettlementManager.onNpcAttackedByPlayer` (settled people only; its reputation cost is not
perception-gated, its alert and its own `lastKnown` are). A direct hit, its pool and every
block fire the pool starts are one bottle, so one attack; a second bottle on a person already
burning is a second attack. Environmental flames report nothing — 0.8.0 also pointed a person's
`lastKnown` at an environmental pool, which that field (where the player was last known) never
meant; only the player throws bottles in production.

**Water and rain.** A patch or burning cell the rain reaches, or that is still wet, touches
nobody, even before its first medium tick has started soaking it (the 0.8.0 pool was inert only
from its first soak; a rained-on block fire kept hurting until out). A campfire and a torch have
no weather rule, so they still touch in rain, and the body's own rain rule puts it out after
1.5 s of rain on the head, after which heat can light it again (the §10.1 limitation). A bottle
breaking on a body whose torso is under water ignites nothing and its liquid finds no ground;
water to the hips does not save a person.

**People and animals keep out of torches and campfires** (not proposed; found by a probe). A
throwaway headless probe ran four seeds' first three settlements for three minutes each with
the day clock at 20 minutes a second (577 000 NPC ticks): with only the sources connected,
**21 residents caught fire in normal life** — legacy camp guards crossing the camp's campfire at
1.8 blocks/s, traders walking past the trading campfire, guards sleeping in a barracks torch's
cell next to their bed, a brute at a gate post treading in a torch's tip. After the changes
below: **0 flame contacts, 0 ignitions** in the same run.

- `Pathfinder.passable` refuses torch and campfire cells: routes, and the sleeping places
  `residentSleepPosition` picks, keep out of them.
- `Entity.keepsOutOfFlames` (true for `Npc` and `Creature`, false for the player):
  `VoxelPhysics.integrate` treats torch and campfire cells as solid for the step, unless the
  body began the step in one; then it walks to the nearest of that cell's four neighbours it
  can stand in, at 1.5 blocks/s or its own speed. People and animals therefore cannot be
  shoved or knocked into one either; the player walks where they choose.
- `Steering.moveToward` looks 0.45 ahead (`FireSystem.standingFlameAhead`) and sidesteps away
  from a torch or campfire cell, so walkers go round rather than bump and hop over.
- `PlayerBlockActions.placeSelectedBlockAt` refuses a torch or campfire whose flame would touch
  a body that can burn: the Survival player placing it at their own feet, or any person or
  animal (`FireSystem.flameWouldTouch`); the Creative player can place one at their feet.

**Changes from the proposals above** (reasons from the probe unless stated):

- `CAMPFIRE` heat gain 2 → 1 /s: 0.58 s in a campfire's flame at a walker's 1.8 blocks/s was
  0.93 heat plus the tick the sweep credits, so crossing a camp lit the guards. People and
  animals no longer walk into campfires at all (above), so the rate now matters for the player:
  crouching straight through one (0.52 s in its flame) comes to about 0.46 of the way to
  catching instead of 0.92, while standing in it catches after 1.25 s. Tuning, not pinned by a
  test.
- Flame tops 1.05 / 1.10 → 1.0: a body standing on the block beside a torch trod in its tip.
- Burning-block volume: face slabs into open neighbours and a plume over an open top, instead
  of the cell inflated on every side, so no corner reaches round an edge and no solid face — a
  wall, a floor, the ground over a buried log — passes heat (the 0.8.0 check burned a person on
  stone over a buried log, or on a slab laid over a burning one; tested).
- `World.getChunk` keeps a small direct-mapped cache behind its one-entry cache, so per-body
  queries across a crowd's chunks stop boxing map keys (§16).

## 5. Data owners

| Data | Owner (proposed name) | Notes |
| --- | --- | --- |
| Lethal-blast death record | `Entity` (shared transient record replacing `Npc.dismemberOnDeath` / `blastX/Y/Z/Strength`) | Set once, only on the alive → dead transition caused by the lethal kill. |
| Species fragment definitions | `entity/FragmentAnatomy` per `entity/BodyFamily` (pieces `FragmentPiece`, wounds `FragmentCut`), built once when the class loads (02) | Explicit joint tables repeating the model builders' numbers, because `entity` must not import `gfx`; `gfx/model/AnatomyModels.validate` holds them to the real models. No per-frame rebuilding; no references to live model instances. |
| Death pose | `entity/FragmentPose`, one immutable snapshot shared by a body's pieces (02) | Captured by `gfx/model/AnatomyModels.captureCreature`/`captureNpc`; §7.1. |
| Fragment physics, caps, settling, decay | `BodyFragmentSystem` (extended, not duplicated) | One step, one sweep, one set of caps for every family. |
| Harvest yield and lodged arrows of a blasted animal | the existing `Carcass` record, flagged as fragmented and anchored to its torso fragment by a stable remains id | Section 9. |
| Player remains | `BodyFragmentSystem` pieces with an explicit neutral player appearance | Never references the live `Player`. |
| Active combustion per body | one embedded state object per `Entity` (`Entity.combustion`, class `entity/BodyCombustion`) | Dies with the entity; no registry to purge; bounded by the entity lists. As built in 06 (§10.1). |
| Combustion rules, exposure resolution, damage | `entity/CombustionSystem` owned by `Game` (`Game.combustion`), reset in `WorldBootstrap`; no random stream | Tuning in `entity/CombustionConstants` and `entity/CombustionSource`. Package `entity`, not the proposed `simulation`, so the state's mutators are package-private (§10.1). |
| Panic intent (goal, timers, recovery) | embedded per `Npc`/`Creature` (or inside `BodyCombustion`) | Written only by AI; seeded panic stream on `EntityManager` or the combustion system. As built in 08 (§12.1): `ai/PanicIntent` as `Npc.panic` / `Creature.panic`, package-private fields written only by `ai/FirePanic`; stream `EntityManager.nextPanicFloat`. |
| Burn visuals, scorch rendering, audio | presentation layer (`AmbienceSystem`/`ParticleSystem`/`Renderer`/`AudioManager`) reading a read-only snapshot | Presentation never writes gameplay state. |
| Death-time visual residue | a bounded list owned by the presentation or fragment/ragdoll layer (09) | Snapshot values only, no entity references. As built in 09 (§14.2): `entity/BurnResidue`, carried by `Ragdoll.burn` → `HumanCorpse.burn` / `Carcass.burn`, or shared by a body's pieces (`BodyFragment.burn`, `burnShare`); aged and bounded by `entity/BurnResidueSystem` (`Game.burnResidues`, ≤ 32). |
| Leaving the world without dying | `EntityManager.depart` (via `removeNpc`, `removeNpcs`, the entity tick's administrative `dead` path, the creature despawn) (09) | Forgets fire and panic, purges AI targets and a conversation; spawns, credits and reports nothing (§14.2). |

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

As built in 06 (§10.1): steps 1–3 are wired, with source sampling in step 2 left to 07 (which
adds it before resolution) and the `dead` guard in `CreatureAI.update`. A `dt` above `FAST_DT`
advances one `FAST_DT`, so "only ever advances by `FAST_DT` steps" holds for direct callers too.
As built in 09 (§14.2): the frame's world step is `Game.advanceWorld`, run only when
`Game.simulates()`; after fragments it ages the burn residues (`burnResidues.update`). Every
explosion happens in the frame (projectile impacts, then keg fuses), after that frame's fast
ticks, so within a frame a burn death always precedes a blast; a body the fire killed is
already out of the entity lists (or, for the player, dead and skipped by the blast), and a body a
blast killed is never ticked by its fire again. `Player.tickNeeds` now stops for a dead player,
so the fast ticks left in the frame a player died in neither heal nor hurt the body.
As built in 07 (§4.1): step 2 samples every source for each body before resolving it, with the
sweep, gap and shape above; the medium tick no longer damages bodies.

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
ticked). What remains visible after death is presentation residue only (09/10). As built in 09
(§14.2): the residue is a `BurnResidue` captured at the death transition; a body removed without
dying has its fire cleared by `EntityManager.depart` and leaves none.

### 10.1 As built in milestone 06 (state, rules, API)

**Production ignition** was connected by 07 (§4.1): every source now reports here and the
legacy medium-tick contact damage is gone. As 06 left it, no game system reported a contact and
06's tests proved the mechanics, not the sources.

**Owner and types** (package `entity`, all transient, none saved):

| Symbol | Role |
| --- | --- |
| `Entity.combustion` | `public final BodyCombustion`, one per body, created with it and garbage with it. |
| `BodyCombustion` | The state. Public getters only; every mutator and field is package-private, so AI, sources and presentation (other packages) can only read it. |
| `CombustionSource` | `DIRECT_HIT`, `LIQUID`, `BLOCK_FIRE`, `CAMPFIRE`, `TORCH`. Declaration order is the dominance order. Per kind: `heatGainPerSecond` (infinite = lights on first contact, `ignitesOnContact()`), `fuelSeconds` granted, `nominalIntensity`. Not a save format: its order may change with the rules. |
| `CombustionConstants` | Tuning (§15). |
| `CombustionSystem` | `Game.combustion`, a `FastTickSystem`. Rules, commands, queries; diagnostic counters `totalIgnitions`, `totalBurnouts`, `totalDoused`, `totalRainedOut` (`reset()` zeroes them, called from `WorldBootstrap.resetForNewWorld`). No `Random`: every rule is deterministic. |

**State fields** (`BodyCombustion`; bounds enforced by the rules and checked by a 3000-tick
randomized test):

| Field (getter) | Meaning | Range |
| --- | --- | --- |
| `burning` | alight | — |
| `fuel` | seconds of afterburn left | [0, `MAX_FUEL_SECONDS` 8] |
| `peakIntensity` | strongest contact intensity this episode | (0, 1] while burning, 0 otherwise |
| `intensity()` | derived, not stored: `floor + (peak − floor) × clamp(fuel / FADE_SECONDS, 0, 1)` with `floor = min(MIN_INTENSITY, peak)`; 0 when not burning | [0, 1] |
| `heat` | ignition progress of a body that is not burning | [0, 1] |
| `soak` | seconds of exposed rain on a burning body's head | [0, 1.5) |
| `burnSeconds` | seconds alight in the current, or else the last, episode | [0, `MAX_BURN_SECONDS` 600] |
| `scorch` | presentation input; grows by `I × dt / SCORCH_SECONDS` while burning, never falls; kept by `extinguish`, cleared only by `clear` | [0, 1] |
| `contact` (`inContact()`) | a flame touched the body on the last fast tick | — |
| `owner`, `ownerByPlayer`, `ownerSourceId` | the contact that last lit or refreshed the fire; null / false / 0 when not burning | — |
| `exposed` (`hasExposure()`), `exposureX/Y/Z` | where the last applied contact touched the body (08's heat-avoidance input) | finite |
| pending candidate (package-private) | the strongest contact offered since the last fast tick: kind, intensity, byPlayer, sourceId, point | one per body, never a list |

**Commands and queries** (`CombustionSystem`, public):

| Method | Effect |
| --- | --- |
| `expose(e, kind, intensity, byPlayer, sourceId, x, y, z)` | Offers a contact for the next fast tick; returns false for a body that cannot burn or a contact that is not a flame. The only entry point 07's sources should need for contact. |
| `ignite(g, e, kind, intensity, byPlayer, sourceId, x, y, z)` | Alight **now** (a body already burning is refreshed and taken over instead, never lit twice) and the same contact is offered for the next fast tick, so the first tick costs contact damage. Also refuses a torso under water. For direct hits (07) and QA staging. |
| `extinguish(e)` | Flames out now; returns whether it was burning. Scorch, exposure point and `burnSeconds` stay. |
| `clear(e)` | Forgets everything including scorch: a new life (`Game.respawn`; `Player.restoreCreativeBody` calls the state's own `clear`). |
| `isBurning(e)` | Alive **and** burning. `BodyCombustion.burning()` alone keeps the value the body died with (09's residue input). |
| `burningBodies(g)` | Count for `RuntimeBudgetSnapshot`. |
| `isLivingBody(e)`, `canBurn(e)` (static) | `Player`, `Npc` or `Creature` of any species; `canBurn` adds alive and not the invulnerable player. |

`expose` and `ignite` refuse a null or dead body, the invulnerable (Creative) player, a null kind,
an intensity that is non-finite or ≤ 0, and a non-finite point; an intensity above 1 is clamped to
1. `extinguish` and `clear` act on any body and ignore null.

**Cadence** (`Game.fastTick`): `player.tickNeeds` → `combustion.fastTick` → `entities.fastTick`
(AI, physics, death routing) → `settlementManager.fastTick`. Bodies are ticked player first, then
creatures, then NPCs, in list order. No medium or slow tick advances a fire, so a medium tick
cannot re-apply a fast tick's time. A non-finite or non-positive `dt` is ignored; a larger one
advances exactly one `FAST_DT`. `CreatureAI.update` now returns at once for a dead creature, as
`NpcAI.update` already did, so a body the fire kills in step 2 is routed and removed in step 3 of
the same tick without a last AI step.

**One body, one fast tick**, in order:

1. Dead: drop the candidate, clear `contact`, stop (the state is frozen as it died).
2. Invulnerable player: `clear`, stop.
3. Idle (not burning, no heat, no candidate): stop. This is the whole cost for a body that has
   never been near a flame.
4. Water: in a loaded column, `wet` = share of the body's height inside `WATER` cells of the column
   under its centre (≤ 3 lookups). `wet ≥ IMMERSION_FRACTION` (0.6): `extinguish` (counts
   `totalDoused` if it was burning), candidate dropped, stop.
5. Candidate: `contact = true`, exposure point recorded. Burning → `refresh`. Not burning → heat
   to 1 for an immediate kind, else `heat += heatGainPerSecond × intensity × dt` (capped at 1);
   at heat ≥ 1 − 1e-4 → `ignite` (`totalIgnitions`). No candidate and not burning → heat decays
   by `HEAT_DECAY_PER_SECOND × dt`. Still not burning: stop — **heating up does no damage**.
6. Rain: in a loaded column, if `FireSystem.isPrecipitationReaching(g, x, headCell, z)` (head
   cell `floor(pos.y + height − 0.01)`): `soak += dt`, and at `RAIN_EXTINGUISH_SECONDS` (1.5)
   `extinguish` (`totalRainedOut`), stop. Otherwise `soak` decays by `dt`.
7. Damage (below), at the intensity before this tick's fuel drain. A body killed here stops.
8. `burnSeconds += dt`; the Survival player crossing `BURN_INJURY_AFTER_SECONDS` gets the medical
   burn (§11); `scorch` grows.
9. No contact this tick: `fuel -= dt × (wet > 0 ? SHALLOW_WATER_DRAIN : 1)`; at ≤ 1e-4 →
   `extinguish` (`totalBurnouts`). Fuel never drains on a contact tick.

**Damage equation** (per fast tick):

```
dmg = (contact ? CONTACT_DPS : AFTERBURN_DPS)[player 6 / 2 | NPC 10 / 3 | creature 12 / 2.5] × I × dt
NPC, creature:  Entity.hurt(dmg, ownerByPlayer)
player:         Player.hurt(dmg, false)   (Creative gate, no armour, no bleed);
                damageFlash = max(damageFlash, contact ? 1 : AFTERBURN_FLASH 0.5 × I)
```

Every creature species shares the creature rates; the family test is `instanceof`, never a
species list. A burn death is an ordinary whole-body death: no blast record, the ragdoll path in
`EntityManager`, the player's death transition without remains. Kill credit is `ownerByPlayer` at
the killing tick, carried by `hurt` into `lastHitByPlayer`, so a body that leaves the flame and
dies in its afterburn still credits whoever owned its fire (test: a bird's meat).

**Refresh and dominance.** Refresh sets `fuel = min(MAX_FUEL, max(fuel, grant))`, raises the
peak, and hands the fire to the new contact (a weaker kind can take the fire over, and so the
credit; its grant only raises fuel to its own value). With the current grants refresh never goes
past 6 s; `MAX_FUEL_SECONDS` 8 is the hard ceiling for any grant. Candidates compare by kind
(declaration order), then intensity, then player over environment, then lower `sourceId`, then
the lower point (x, then y, then z) — so contacts reported in any order leave the same winner,
exposure point included (a test runs all 120 orders of five contacts). Contacts never add up:
eight simultaneous flames burn like one.

**Ignition tick.** A contact's first fast tick lights an immediate kind and deals contact damage
in that tick. `ignite` lights during the frame; the next fast tick deals contact damage. Measured:
villager contact tick −0.5, player −0.3, creature −0.6.

**Player feedback** is damage, the red flash, and one log line each when the player catches fire,
is put out by water or rain, or burns out. Nothing touches movement, sprint, velocity, camera or
input (§13): a test moves a burning and an unburned player with the same 90 commands and compares
position, velocity, sprint, crouch, speed multiplier and `canSprint` every frame.

**Changes from the proposals above** (reasons in the progress file):

- Package `entity`, not `simulation`; `Entity.combustion` is `public final`, its mutators
  package-private.
- Immersion is **the share of body height under water ≥ 0.6** in the centre column, not "water in
  the body-centre cell". Water is whole cells: a person standing in one cell of water (1 of 1.75
  blocks — legs and hips; the torso box starts at 0.86 m) would count as immersed by the centre
  cell. At 0.6 a person or the player keeps burning there with a 3× drain, and every animal
  standing in one cell of water (0.67–1.0 of its height) is put out.
- Rain is sampled with the new shared predicate `FireSystem.isPrecipitationReaching` in the head
  cell (`isRainedOn(x, y, z)` now delegates to it for the cell above a block, unchanged). The
  block rule's "a roof resting directly on a block does not shelter it" artefact would have
  counted a person under a two-high ceiling as rained on; a body is sheltered by anything opaque
  above its head. `soak` only runs while burning and resets on ignition and extinction.
- The combustion salt `0x4255524e494eL` is **not used**: combustion draws no random numbers. The
  one roll, the burn injury's length, is the player's own affliction stream (§11), which is where
  `Player` keeps every injury roll.
- Unloaded columns: water and rain rules are skipped (an unloaded column reads as air and open sky),
  so a body there burns down normally.
- `TIMER_EPSILON` (1e-4) slack on fuel, soak, heat and injury thresholds makes tick counts exact.

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

As built in 06: `Player.inflictBurnInjury(Game)` (package-private, called by `CombustionSystem`
when `burnSeconds` crosses 1.0 s, so once per episode) rolls `BURN_AFFLICTION_SECONDS_MIN` 60 +
`RANGE` 40 × the player's affliction stream and logs only when the injury is new;
`Player.tickAfflictions` skips `BURN` entirely — no damage, no countdown — while
`combustion.burning()` is true. `tickNeeds` runs before the combustion tick, so it reads the
previous tick's fire: the injury resumes on the first tick after the flames go out. Measured on
the player (test `theBurnInjuryWaitsOutTheFlamesAndThePoulticeOnlyTreatsTheInjury`): 0.5 s in a
pool then the full afterburn cost 13.08 health against a calm twin (formula 13.05), the injury
came at exactly 1.0 s, its seconds did not move while alight, and afterwards it cost 0.18/s. A
poultice mid-flame cured the injury, the flames burned on, and no second injury came that
episode. Since 07 the legacy contact paths and their own 50 % `BURN` roll are gone (§4.1).

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

### 12.1 As built in milestone 08

**Owner and types** (package `ai`, all transient, none saved):

| Symbol | Role |
| --- | --- |
| `FirePanic` | The rules. `update(Game, Npc, dt)` and `update(Game, Creature, dt)` (package-private) run one tick of panic and return true when it decided the tick. |
| `PanicIntent` | The state, `public final` on every `Npc` (`panic`) and `Creature` (`panic`); none on `Player`. Package-private fields, public getters: `active()`, `recovering()`, `recoverySeconds()`, `hasGoal()`, `goalX/Y/Z()`, `headingX/Z()`, and this panic's tallies `goals()`, `replans()`, `trappedGoals()`. A new body starts calm; the intent clears itself when a recovery ends. |
| `PanicConstants` | Tuning (§15). |
| `EntityManager.nextPanicFloat()` | The panic stream ("PANICS", salt `0x50414e494353L`), seeded in `setAiRandomSeed` beside the three AI decision streams. Drawn only when a goal is chosen, in the entity tick's order; nothing else draws from it. |

**Hooks (exact).** `NpcAI.update`: after the `dead` guard and the per-tick upkeep (`decideTimer`,
`attackCooldown`, hunger, `bobPhase`), **before** `interactFreeze`, so before the conversation freeze,
the whole `SettledNpcAI` route (captives, hostile combat, war parties and counterattacks, friendly
defence, search, residents' and settlement traders' duty, sleep), wandering traders, raiders and the
camp jobs. `SettledNpcAI.update`: the same call first, for direct callers; the game's one caller is
`NpcAI.update` (unchanged since 01), where it is a no-op because a panicking NPC never gets there.
`CreatureAI.update`: after the `dead` guard and the upkeep (`decideTimer`, `attackCooldown`, hunger,
fear decay, `bobPhase`), before the species switch. `WorldInteractions.canOpenNpcInteraction` refuses a
panicking person (no talk prompt, F does nothing); `NpcScreen.update` closes on one too.

**Phases.** Calm → panicking while `CombustionSystem.isBurning` (alive and alight) → recovering for
`RECOVERY_SECONDS` (1.5) after the flames go out, still panicking but easing to `RECOVERY_STRIDE` of the
speed → recovered: the intent is cleared, the body's plans are reset and its **ordinary AI runs in that
same tick**. Catching again while recovering is alight again, and the recovery restarts when the flames
are out. A medical burn is not a fire: nothing panics without active flames (tested for 10 s after).

**While panicking.** People: `state = FLEE`; `interactFreeze = 0`; if the open NPC screen is this
person's, `Game.closeScreens()` and one log line; `lastKnownAge` keeps aging and `repathCooldown` keeps
counting (what they know stays dated); everything else waits — perception, search, work, meals,
sleep, party mission timers, departure timers and the reload timer (so no reload completes);
`abstractTravel` is cleared in a loaded column (panic is physical) and a body in an unloaded column
stands and burns out where it is. Animals: `state = FLEE`; `fear` is left alone. No attack, shot,
trade or interaction code runs, because none of the ordinary AI does.

**Recovery reset (`settle`).** Person: `state = IDLE`, `hasTarget`, `targetBlock`, `path`,
`pathIndex`, `combatTarget` cleared, `decideTimer` 0 (a settled person perceives at once), `workTimer`
0, `repathCooldown` 0, `interactFreeze` 0, stopped. Animal: `state = WANDER`, `hasTarget` and
`targetEntity` cleared, `decideTimer` and `eatTimer` 0, stopped (a bird hovers). Nothing restores an old
charge, hunt, meal, conversation, path or target; a settlement that went away is found gone; a player
who is now out of reach or imperceptible is not attacked.

**Goals.** A goal is chosen at the start, when the timer (`GOAL_INTERVAL` 0.6 s + a draw of up to
`GOAL_INTERVAL_JITTER` 0.6 s) runs out, when the goal is reached (within `ARRIVE` 0.8), or when progress
is blocked, but never within `REPLAN_COOLDOWN` (0.25 s) of the last: at most four a second. Each choice
takes the same draws whatever the terrain (walker four: spread, distance, interval, stride; bird five:
plus climb), so one body's surroundings never shift the stream for the next body. Escape direction:
straight away from the last contact point while a flame touches the body; out of contact, half that and
half its current heading; with no usable point (closer than 0.05 horizontally) its heading, and a new
panic's heading is the way it faces. The drawn direction is that ± `SPREAD_DEGREES` 70; then turned
50, 100 and 150 degrees to the drawn side first, and straight back: eight directions. A body that is
blocked skips directions within `BLOCKED_EXCLUSION_DEGREES` 30 of its heading. Each direction is
checked along a straight line (`groundReach`: cell to cell with the pathfinder's own footing —
`Pathfinder.standable`/`passable`, now package-private: same level, one up with headroom, down at most
`Pathfinder.MAX_DROP` 3, never a torch or campfire cell, never an unloaded column; animals also stop at a
closed gate). The first direction clear for the whole distance (4–8 blocks) wins, else the one that gets
furthest; if none gets a block, the goal is one block in the best direction and the choice counts as
trapped — the body struggles there. Birds (`flightReach`): 6–10 blocks level, climbing 3–6 but never
above 16 over the ground beneath them (a bird above that glides down to it), sampled every half block at
the top and bottom of the body; the climb is tried first, then level flight.

**Moving.** The heading turns toward the goal at `TURN_RATE_DEGREES` 270 per second and is always a unit
vector, so there is no zero direction to spin on. Walkers use `Steering.moveToward` toward a point two
blocks along the heading (its hop over one block, ladder climb, swimming and fire sidestep as ever;
`VoxelPhysics` keeps them out of walls, bars and fires), at `CREATURE_SPEED_MUL` 1.6 × species speed or
`NPC_SPEED_MUL` 1.35 × archetype speed (`LEGACY_PERSON_SPEED` 3.2 without one), times the goal's stride
(0.85–1). Before each step the columns past the leading faces of the body box (per axis and their
corner, 0.35 ahead) must be loaded and have footing within three blocks below the feet — checked in the
air too, so a body that took a legal drop does not carry on over a second one; otherwise it stops and
replans. People shove a closed gate ahead open (`SettlementManager.openGate`). Birds use
`Steering.flyToward` at a vertical rate that follows the checked line and stop at an unloaded column.
Blocked progress is a step that achieved under `STUCK_PROGRESS` 30 % of its distance (horizontal for
walkers, so jumping at a wall is not progress; three-dimensional for birds) for `STUCK_SECONDS` 0.2.
Physics runs once per tick in the entity tick, as ever; panic never moves a body itself.

**Blind to the player and presentation.** `FirePanic` never reads the player, the camera or anything
rendered; a player's position, perceivability and mode cannot change a choice (a test moves the player
and spins the camera between ticks and compares every position bit for bit). The player has no panic:
§13 holds, tested through the production movement system and the whole game tick with a burning crowd
round a burning player.

**Changes from the proposals above** (reasons in the progress file):

- `Pathfinder` is not used for panic. Goals are 4–8 blocks off and checked with the pathfinder's own
  footing rules along a straight line; A* for up to four random goals a second per body would allocate
  per query and plan detours that read as deliberate. Measured: 0 bytes per fast tick for 75 panicking
  bodies.
- Captives run the same panic; bars and collision keep them in, and rescue stays the player's action.
- `fear` is not raised or cleared by panic; a thornhorn that is still wounded and afraid may charge a
  perceivable player in reach afresh after recovery (its ordinary rule), never an old target.
- A second hook in `SettledNpcAI.update` covers direct callers.
- The ground check is made in the air too, and the flight check at the body's top and bottom with the
  climb following it: the real-settlement probe and the roofed-bird test found the weaker versions let a
  body clear a second drop and a bird graze a roof edge.

## 13. Player control and Creative

- Combustion never touches `PlayerMovementSystem`, `Player.moveSpeedMul`, `canSprint`,
  `sprinting`, `vel`, camera yaw/pitch or input. Burning feedback is damage, `damageFlash`,
  log text and presentation (10).
- Creative: `CombustionSystem` clears and skips the player's state while invulnerable; the
  lethal gate skips the player before any record; no player remains are spawned. Returning to
  Survival starts with a clear state. As built in 06: `Player.restoreCreativeBody` clears the
  fire too, so the switch to Creative forgets it at once, and every command refuses the
  invulnerable player.
- As built in 09 (§14.2): the round trip Survival → Creative → Survival brings no flame back
  until a fresh contact (tested through `Game.switchGameMode`); the player cannot fall asleep
  while alight and a fire wakes a sleeping player. Neither touches movement or the camera.

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

### 14.2 As built in milestone 09 (lifecycle)

**Transient policy.** Stated in the class comments of `BodyCombustion`, `CombustionSystem`,
`BurnResidue` and `BurnResidueSystem`, in `SaveSystem.save` beside the settle calls, and here:

| State | Save | Load, new world | Respawn | To Creative / back | Dormancy, departure | Death |
| --- | --- | --- | --- | --- | --- | --- |
| A body's fire: burning, fuel, heat, soak, owner, scorch, exposure, sample point, reported bottles (`Entity.combustion`) | not written, left running | new bodies, none | cleared (`Game.respawn`) | player's cleared (`Player.restoreCreativeBody`); clear on return until a fresh contact; people and animals burn on | cleared (`EntityManager.depart`) | frozen, never ticked again; copied into the residue |
| Panic (`Npc.panic`, `Creature.panic`) | not written, left running | new bodies, calm | — (the player never panics) | unaffected | cleared (`FirePanic.forget`) | never ticked (dead guards) |
| Remains' flames and scorch (`BurnResidue`) | not written; a save's settle moves them onto the corpse, carcass or settled pieces, still ageing | `BurnResidueSystem.reset`; loaded remains carry none | unaffected | unaffected | none made | made here |
| Health, the player's medical `BURN` | saved (existing) | restored; `BURN` counts down and hurts again once not alight | existing | existing | health, `alive`, sickness, hunger written back to the resident record | — |
| Settled remains, harvest links | saved (05) | restored once | — | — | — | — |

**The death transition and the residue (API for 10).** `BurnResidueSystem.capture(Entity)` is
called at exactly two places, both at the moment the one representation is chosen:
`RagdollSystem.begin` (both `spawn` overloads) and `BodyFragmentSystem.launch` (people, animals and
the player's remains). It returns null for a body neither burning nor scorched; otherwise a
`BurnResidue` copying the scorch, and for a body alight its `intensity()`, its fuel (capped) and its
rain `soak`. Every field is a primitive: it cannot hold the entity or the live `Player` (a test
checks the class reflectively).

| Carrier | Field | Rule |
| --- | --- | --- |
| Falling whole body | `Ragdoll.burn` | set at spawn; anchored to the torso point every solver step |
| Body at rest | `HumanCorpse.burn`, `Carcass.burn` | **moved** from the ragdoll at `RagdollSystem.emit` (the ragdoll's field is nulled; the same object, never a copy); a bird leaves no body, so its residue is let go |
| Body blown apart | `BodyFragment.burn`, `BodyFragment.burnShare` | one object shared by every piece; `burnShare` = the piece's mass / the body's (the shares sum to 1); anchored to the core piece (`definition.parent == -1`) in flight and at rest; the harvest record of a fragmented animal has `burn == null` |

Read-only getters (package `entity` owns every mutator): `scorch()` (constant, kept for the remains'
lifetime), `flameAtDeath()`, `flame()` = `flameAtDeath × (1 − age / flameSeconds)` while
`age < flameSeconds`, else 0, `smoke()` = `flameAtDeath × age / flameSeconds` during the flames, then
falling linearly to 0 over `RESIDUE_SMOKE_SECONDS`, `active()` (flame or smoke showing),
`flameSeconds()` = `min(RESIDUE_FLAME_SECONDS, fuel at death)`, cut to the age at which water or rain
put it out, `age()`, `doused()`, and the anchor `x()/y()/z()` (capture: the body's centre; then the
ragdoll torso point, the core piece's centre, the corpse or carcass position). `flame()` and `smoke()`
are 0 once the system lets the residue go. **10 draws through the carriers** — `ragdolls.live`,
`entities.corpses`, whole `entities.carcasses`, `fragments.live` and `fragments.settled` — each
piece at its `burnShare` of the body's flame, so a body in pieces is never drawn with ten fires.

`BurnResidueSystem` (`Game.burnResidues`, a `SimulationSystem`): `capture`, `update(Game, dt)` from
`Game.advanceWorld` after the fragment step (so paused frames age nothing, and a residue shared by
ten pieces ages once), `trackedCount()`, diagnostics `totalCaptured`, `totalEvicted`, `reset()` from
`WorldBootstrap.resetForNewWorld`. Each update, per tracked residue: released if no carrier holds it
(`BurnResidue.hold`/`letGo`, package-private: a ragdoll or corpse or carcass holds once, each piece
once; corpse and carcass rot, the corpse distance cull, piece eviction, `removePiece`, rot and
distance cull let go); otherwise it ages by `min(dt, RESIDUE_MAX_STEP)`; while its flames last, water
in the anchor cell douses it at once and open precipitation there (`FireSystem.isPrecipitationReaching`,
the living body's predicate) douses it once the soak it inherited plus the rain since reaches
`RAIN_EXTINGUISH_SECONDS`; an unloaded column is skipped. It is released when its smoke ends. A capture
past `MAX_BURN_RESIDUES` (32) ends the oldest one's flames and smoke; its scorch stays on its carriers.
Nothing samples a residue as a flame: it deals no damage and lights nobody (R8, tested).

**First lethal cause.** Within a frame the fast ticks (fire, then AI, physics and death routing) run
before any projectile impact or keg fuse, so a burn death is always processed before a blast in the
same frame. `Entity.hurt` ignores a dead body, so the first lethal hit keeps `lastHitByPlayer`;
`ExplosionSystem.detonate` passes over a dead body before its lethal gate and `recordBlastDeath`
refuses a body the call did not kill; `CombustionSystem` drops a dead body's contact and never
advances it. Hence: fire first → a whole body, no blast record, the fire owner's credit, even for the
player killed by the fire in a fast tick and caught by a keg later in the frame; blast first → the
blast record and the blast owner's credit, one set of pieces, the fire frozen as the blast found it
(and captured from there). The player's body keeps no needs tick while dead (`Player.tickNeeds`
returns for a dead player): the fast ticks left in the frame of a death no longer heal it above 0
or keep an injury counting. `enterDeathIfDue` runs once per death (existing).

**Leaving without dying.** `EntityManager.depart(Game, Npc)` / `depart(Game, Creature)`:
`CombustionSystem.clear` (fire, heat, scorch, exposure, sample point and the reported bottles — the
only source de-duplication memory a body has), `FirePanic.forget` (new, public: clears the intent
without the recovery's plan reset), `forgetTarget` (other people's `combatTarget`, animals'
`targetEntity`), and for a person the conversation: `Game.closeScreens()` when the NPC screen is
theirs, otherwise `activeNpc = null`. It spawns, credits, reports and logs nothing. Callers:
`EntityManager.removeNpc` and `removeNpcs(Game, Predicate)` (new; a reverse index scan that keeps the
order of those who stay; never called from inside the entity tick's own iteration) — used by
`SettlementManager.deactivate` and `rescueCaptive`, all four `CounterattackDirector` removals
(garrison replaced, orphans after a load, dematerialize, members removed) and
`FactionSystem.cleanupExpiredObjective`; the entity tick's administrative `dead` path (health left:
routed fighters, departing traders, fading raiders — never a corpse, `reallyDied`); and the creature
despawn in `EntityManager.slowTick` (an index loop now). Dormancy policy (finite): the dormant
simulation has no fire, so deactivation writes back the health a resident has at that moment and
drops the fire and panic; reactivation spawns a new, calm, unburned body at that health. No fire
is simulated anywhere remote.

**Pause, screens and sleep.** `Game.simulates()` (the frame's gate, unchanged rule, now callable)
and `Game.advanceWorld(dt)` (the frame's whole world step) are the only way anything burns, panics,
falls or ages. Pausing: `PAUSE`, `OPTIONS`, `AUDIO_OPTIONS`, `GAME_MODE`, `WORLD_CONTROLS`, the debug
pause (`simPaused`), and every app state but `PLAYING` (death screen included). Not pausing:
`INVENTORY`, `CRAFTING`, `CRATE`, `NPC`, `MAP`, `CREATIVE_CATALOG` — fires burn on under them.
Sleep fast-forwards only `time.totalMinutes`; fires advance by the frames' fast ticks and residues by
the frames' seconds, so a night's sleep burns nothing ahead (60 sleeping frames: 170 in-game
minutes, one second of fire). `SleepSystem.startSleep` refuses while the player is alight;
`tickSleep` wakes a burning player whatever the red flash (afterburn flashes at most 0.5, which
never woke anyone). A conversation replaced by another screen lets its speaker go
(`HotkeyRouter.toggle`), and a new world clears the speaker (`WorldBootstrap.clearPerWorldState`).

**Reset owners.**

| State | Reset by |
| --- | --- |
| `Entity.combustion` | a new body (new world, load, reactivation); `CombustionSystem.clear` from `Game.respawn`, `EntityManager.depart`; `BodyCombustion.clear` from `Player.restoreCreativeBody` (mode switch, Creative needs tick, Creative respawn) |
| `Npc.panic`, `Creature.panic` | a new body; the end of the recovery (`FirePanic`); `FirePanic.forget` from `depart` |
| `CombustionSystem` tallies | `WorldBootstrap.resetForNewWorld` |
| `BurnResidueSystem` list and tallies | `WorldBootstrap.resetForNewWorld`; per residue: expiry, no carrier left, eviction past 32 |
| `Ragdoll.burn` | moved on at `RagdollSystem.emit`; `RagdollSystem.reset` drops the ragdolls |
| `HumanCorpse.burn`, `Carcass.burn`, `BodyFragment.burn` | die with their carrier, letting go on removal |
| `Game.activeNpc` | `Game.closeScreens`, `WorldBootstrap.clearPerWorldState` (new), `HotkeyRouter.toggle` (new), `EntityManager.depart` (new) |

**Rewards and harvest across transitions** are unchanged in ownership: the first lethal cause's
`lastHitByPlayer`, one carcass or one anchored record per animal, and a save and load restore each
once (tested). A residue carries no reward, yield or arrows.

**Changes from the proposals above:**

- The residue lives on its carriers rather than in a list the presentation walks; the registry
  (≤ 32) only ages and bounds it and knows the carriers' core position by copy.
- Scorch stays on remains for as long as they last (a copied constant); only flames (≤ 4 s) and
  smoke (≤ 3 s) are the bounded, decaying part.
- Water and open rain put a residue's flames out by the living body's rules, the soak carried over.
- A player who burns to death leaves no residue: no player body exists to carry one (only a
  blast leaves the player's remains).
- `Player.tickNeeds` gained its dead guard; departures were centralised in `EntityManager.depart`,
  applying the deactivation policy to every administrative removal, not only settlements.

## 15. Proposed tuning (all values proposed)

| Constant | Value | Why |
| --- | --- | --- |
| Heat gain to ignite: DIRECT_HIT, LIQUID | immediate | a molotov must ignite |
| Heat gain: BLOCK_FIRE | 4.0 /s (0.25 s of contact) | stepping into a burning bush catches |
| Heat gain: CAMPFIRE | 2.0 /s (0.5 s); **as built in 07: 1.0 /s (1.25 s at intensity 0.8)** | standing in the fire catches, brushing past does not; §4.1 says why it halved |
| Heat gain: TORCH | 1.0 /s (1.0 s) | only deliberate contact with the flame head |
| `HEAT_DECAY` | 2.0 /s | brief grazes do not accumulate |
| Source intensity: DIRECT_HIT / LIQUID / BLOCK_FIRE / CAMPFIRE / TORCH | 1.0 / patch intensity (0.5–1) / 1.0 / 0.8 / 0.6 | |
| Afterburn fuel granted | 6 / 6 / 4 / 3 / 2 s | visible burning after leaving |
| `MAX_FUEL` (refresh cap) | 8 s | no endless stacking |
| `FADE_SECONDS`, `MIN_INTENSITY` | 3 s, 0.35 | full flames, then a visible taper |
| `CONTACT_DPS` NPC / creature / player | 10 / 12 / 6 | equals the legacy pool rates, so pool lethality is unchanged |
| `AFTERBURN_DPS` NPC / creature / player | 3.0 / 2.5 / 2.0 | survivable afterburn for sturdy bodies |
| `SHALLOW_WATER_DRAIN` | 3 × | feet in water help, torso immersion ends it |
| `IMMERSION_FRACTION` | 0.6 of body height (added in 06) | §10.1: a person in one cell of water is not immersed, every animal is |
| `RAIN_BODY_EXTINGUISH` | 1.5 s exposed | between the patch (1 s) and block (2 s) values; as built `RAIN_EXTINGUISH_SECONDS` |
| `BURN_INJURY_AFTER` | 1.0 s | section 11; as built `BURN_INJURY_AFTER_SECONDS` |
| `SCORCH_SECONDS` | 10 s at intensity 1 (added in 06) | presentation input only; 10 may retune |
| `AFTERBURN_FLASH` | 0.5 × intensity (added in 06) | least red flash kept during afterburn; contact ticks flash 1 |
| `MAX_BURN_SECONDS` | 600 (added in 06) | keeps an endless campfire contact finite |
| `PANIC_GOAL_INTERVAL` | 0.6 s + seeded 0–0.6 s | irregular, not per frame; as built `PanicConstants.GOAL_INTERVAL` + `GOAL_INTERVAL_JITTER` |
| `PANIC_GOAL_DISTANCE` | 4–8 blocks (birds 6–10 horizontal, climb 3–6) | as built `GOAL_DISTANCE_*`, `FLIGHT_DISTANCE_*`, `CLIMB_*` |
| `PANIC_SPREAD` | ± 70° | crowds do not mirror each other; as built `SPREAD_DEGREES` |
| Panic speed | creature `type.speed × 1.6`; NPC `speed × 1.35` | matches existing flee multipliers; as built `CREATURE_SPEED_MUL`, `NPC_SPEED_MUL`, a person without an archetype walking at `LEGACY_PERSON_SPEED` 3.2 (08) |
| `PANIC_TURN_RATE` | 270 °/s | coherent motion; as built `TURN_RATE_DEGREES` |
| `PANIC_REPLAN_COOLDOWN` | 0.25 s | bounded replanning; as built `REPLAN_COOLDOWN` |
| `PANIC_RECOVERY` | 1.5 s | brief settling after extinction; as built `RECOVERY_SECONDS` |
| `MIN_STRIDE` / `RECOVERY_STRIDE` (added in 08) | each goal at 0.85–1 of the panic speed; 0.55 at the end of the recovery | a stumbling, then slowing, run |
| `AFTER_CONTACT_AWAY_WEIGHT` (added in 08) | 0.5 | out of the flames, half "away from the touch", half the way it runs |
| `STUCK_SECONDS` / `STUCK_PROGRESS` (added in 08) | 0.2 s under 30 % of the intended step | blocked progress replans quickly, not on one bad tick |
| `ARRIVE` (added in 08) | 0.8 blocks | a reached goal is replaced |
| `CANDIDATE_STEP_DEGREES` / `BLOCKED_EXCLUSION_DEGREES` (added in 08) | 50° (eight directions) / 30° | a blocked line turns the goal; a blocked body turns away |
| `FLIGHT_CEILING_ABOVE_GROUND` (added in 08) | 16 blocks | about the top of a bird's ordinary flight (5–13) |
| `MIN_SEPARATE_PIECE` | 0.10 m | section 7 |
| `MAX_ANCHORED_REMAINS` | 60 | section 9 (as built: `BodyFragmentConstants`) |
| `MIN_LAUNCH_MASS` | 1.25 kg | section 9.1; added in 03 |
| `FragmentModels.SHELL_REACH` | 0.03 m | section 7.2; added in 04 (vest and haunch are 0.02) |
| `FragmentModels.CONTACT_SPEED` | 1 m/s | section 7.2; added in 04, well under the 4.5 m/s launch bias |
| Combustion salt / panic salt | `0x4255524e494eL` / `0x50414e494353L` | distinct from every existing salt in the source tree; the combustion salt is unused since 06 (no random stream) |
| `RESIDUE_FLAME_SECONDS` (added in 09) | 4 s, or the fuel left if less | §16's death-residue flame; the flames die down as the body's own would have |
| `RESIDUE_SMOKE_SECONDS` (added in 09) | 3 s | §16's death-residue smoke, after the flames or a douse |
| `MAX_BURN_RESIDUES` (added in 09) | 32 | §16; past it the oldest loses flames and smoke, never scorch |
| `RESIDUE_MAX_STEP` (added in 09) | 0.25 s | a stalled frame cannot end a residue at once |
| `OUT_MEMORY_SECONDS` (added in 10) | 3 s | presentation only: how long a put-out fire is remembered for its taper, steam and wisp (§16.1); nothing in the simulation reads it |
| Presentation constants (added in 10) | §16.1 | anchors, flame sizes, detail distances, per-frame and per-pass caps, taper, steam and wisp times; all proposed, retune with captures |

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
  160 patches and 220 burning cells on the reference host. As built in 06 (no sources yet,
  `CombustionAllocationTest`): an idle crowd of 75 allocates 0 bytes per fast tick; the same crowd
  all burning allocates 1280, all of it `World.getChunk` boxing its map key when consecutive
  bodies change chunk (80 bytes a miss, the lookup every entity's physics already makes), held
  under the ragdoll/fragment steps' 4 KB. The wall-clock target is unmeasured.
  As built in 07 (sources sampled every tick for every body): per body, a bounds rejection
  against each patch (≤ 160) and burning cell (≤ 220, kept in a list beside the map so no
  iterator is made), the exact test and a few block lookups only on a hit, and the torch and
  campfire cells under the swept box (about 4–16 lookups standing or walking; the 2-block
  sweep limit bounds it). `World.getChunk` now resolves any 8 × 8 window of recent chunks
  without boxing, so measured: idle crowd **0** bytes per fast tick, the same crowd all burning
  **0** (was 1280), and every flame at its cap around a moving crowd **96** — the campfire fuel
  map's key, made only for a body touching a fueled campfire's flame (four of the 75 here; a
  boxed default made it 160 until it was removed). Chunks are never loaded or generated by
  sampling (tested at the edge of the loaded area). Keeping out of fires costs people and
  animals one scan of their own cells per physics step and one of the cells ahead per steering
  call. The wall-clock target is still unmeasured.
- Existing caps unchanged: `MAX_LIVE_FRAGMENTS` 120, `MAX_SETTLED_FRAGMENTS` 600,
  `RagdollConstants.MAX_LIVE` 12, `FireSystem.MAX_ACTIVE_FIRES` 220,
  `LiquidFireConstants.MAX_PATCHES` 160 (22 per spill), `MAX_TRACKED_NPC_SPILLS` 64 (removed
  in 07: each body remembers `REPORTED_BOTTLES` 4 bottles in a fixed array instead),
  `ProjectileSystem.MAX_LIVE` 96, `ParticleSystem.MAX` 4000 (splash limit 2800, blood 3600),
  `SettlementManager.MAX_ACTIVE_NPCS` 40, `ExplosionSystem.MAX_ACTIVE_FUSES` 64. With ≤ 12 pieces
  per body, 120 live fragments hold 10 bodies in flight.
- New collections must be capped and covered by `RuntimeBoundsTest`; extend
  `RuntimeBudgetSnapshot` with burning bodies (≤ living bodies) and anchored remains (≤ 60).
  As built: `anchoredRemains` (03); `burningBodies` and `livingBodies` (06, hard limit
  `burningBodies <= livingBodies`, smoke line `burning=N/M`). 06 adds no collection: one fixed
  state object per body, one pending contact per body, four `int` counters.
- Panic: ≤ 1 goal per actor per 0.25 s; path work within existing `Pathfinder` limits.
  As built in 08 (§12.1): no `Pathfinder` query at all. A walker's goal checks at most 8 directions ×
  8 cells (each cell a few block lookups, the same as the pathfinder's neighbour step); a bird's at most
  8 directions × 2 heights × 24 half-block samples × 2 cells. Each tick adds the heading turn and the
  ground check ahead (at most 3 columns × 5 lookups). No collection, no allocation outside opening a gate:
  `FirePanicAllocationTest` measures a crowd at the NPC cap plus 35 animals and birds, all panicking in a
  walled yard of pillars for 5000 ticks (thousands of blocked replans), at **0 bytes per fast tick**. The
  goal bound is asserted by the tests (goals ≤ 1 + elapsed / 0.25 s). Wall-clock cost is unmeasured.
- Eviction and caps never undo a death or skip a living body's combustion; only presentation
  degrades.

Presentation (*proposed*, 10 may retune with captures):

- Full attached emitters for the 24 nearest burning bodies within 48 blocks; reduced cadence to
  96 blocks; none beyond, while gameplay continues.
- Per body per 0.12 s emitter pass: flames `1 + round(4 × surfaceArea × I)`, capped at 6;
  smoke rising as `I` falls; embers at ≤ 0.15 chance. Body fire stops adding at
  `ParticleSystem.SPLASH_LIMIT` and never touches the blood reserve.
- At most 4 simultaneous body-fire audio sources nearest the listener.
- Death residue ≤ 4 s flame + ≤ 3 s smoke, at most 32 residues. As built in 09 (§14.2): exactly
  that, one residue per body whatever its piece count, one small object per burning death and no
  per-frame allocation (a bounded `ArrayList` of 32, scanned backwards); `RuntimeBudgetSnapshot`
  reports `burnResidues` with the hard limit `burnResidues <= MAX_BURN_RESIDUES`. Scorch-only
  residues (a body scorched earlier, not burning at death) are carried but never tracked.
- *Proposed* cost target: ≤ 0.5 ms CPU per frame for 24 burning bodies on the reference host.

### 16.1 As built in milestone 10 (presentation)

**Reading the fire.** Presentation never writes combustion or residue state, reads no simulation
random stream and runs on the main thread. Everything it shows comes from one mapping,
`gfx/BodyFireLook` (`living(Entity)`, `remains(BurnResidue)`, `none()`), which yields: `flame`
(size, heat, light), `coverage` (share of anchors alight), `flare` (swell on catching), `spreading` +
`spread` + touch point (flames climb from the touch), `smoke`, `steam`, `embers`, `scorch`, `glow`
(embers in the char), `light` (self-light), `seed` (flicker). To support it the simulation keeps a few
**presentation-only, read-only** values that nothing in the simulation reads:

| Symbol | Kept by | Meaning |
| --- | --- | --- |
| `BodyCombustion.outSeconds()`, `outIntensity()`, `outDoused()` | `putOut(doused)` at every extinction (water, rain → doused; burnout → not; `CombustionSystem.extinguish` → doused), aged by `ageOut` on the fast tick up to `OUT_MEMORY_SECONDS` (3 s); `clear()` forgets | how and how strongly the last fire went out |
| `BodyCombustion.episodes()`, `flameSeed()` | `ignite` (the seed mixes the touch point and the episode) | a new ignition; a flicker number fixed per fire, deterministic per replay |
| `BodyCombustion.touchX/Y/Z()` | `touchedAt(x, y, z, body)`: the touch point minus the body's feet | where to climb from, carried with a moving body |
| `BurnResidue.seed()` | copied at capture | the remains flicker like the living body did |

Status to look (tested in `BodyFireLookTest`):

| State | Look |
| --- | --- |
| catching | `flare` = 1 − burnSeconds / 0.35 s; flames climb from the touch at 0.25 m + 3 m/s until 3 m |
| burning | `flame` = intensity; `coverage` 0.35 at `MIN_INTENSITY` → 1 at full; `smoke` 1 → 0.25 as intensity rises (it thickens as the fire weakens); `glow` = intensity |
| rain on a burning body | `flame` × (1 − 0.45 × soak fraction), `coverage` × (1 − 0.3 × soak fraction), `steam` = soak fraction |
| burned out | flames taper to 0 over 0.6 s, a smoke wisp over 1.5 s, no steam |
| doused (water, rain) | flames taper over 0.25 s, steam over 1.2 s |
| remains | `flame` = `BurnResidue.flame()`, `smoke` = `smoke()`, steam for 1.2 s after a douse, scorch kept |
| unburned, or a new life (`clear`) | nothing, scorch 0 |

**Where flames stand.** `gfx/model/FlameAnchors.of(family)` builds fixed points on that family's
shared model: the anatomy's own boxes (the `FragmentAnatomy` joint table) share
`max(round(area × 10 /m²), boxes ≥ 0.05 m²)` anchors, between 4 and `MAX_CORE_ANCHORS` 32, one first
for every box ≥ 0.05 m², then by largest area per anchor; worn or grown boxes (vest, pack, ruff,
plates, horns) get one each (two past 0.8 m²), at most `MAX_EXTRA_ANCHORS` 24; boxes under 0.012 m²
and glowing boxes get none. Per anchor: a point on a face (up-facing faces weighted 1.3, sides 1,
underside 0.35), a flame width 0.75 × √(largest face) in [0.09, 0.48] m, a height/width stretch
1.8–2.8 (+0.5 on top faces), a lighting order spread by the golden ratio. As built: bird 6 anchors
(largest flame 0.15 m), hare 8, every other body 32 on its anatomy plus what it wears. `sample(start,
frame, out)` walks the posed part tree exactly as `ModelPart.render` does — pivots, poses, Z·Y·X
rotation, scale, visibility — so a part a person's look hides, or a piece's isolation, has no
anchors; the pieces of a body together hold each anchor once. `gfx/model/BodyPosing` is the single
posing path (living, ragdoll, corpse, carcass, piece; `bodyFrame`), used by the renderer, the flames
and the emitters, so flames sit on the pose that is drawn.

**Flames drawn** (`gfx/BodyFlames`, per frame, from `Renderer`): per alight anchor an outer tongue
(orange cooling to red at its rim, HDR 1.2, alpha 0.8–1) and, within 32 m, a hotter core (0.52 of the
width, HDR 1.5); one soft glow per body or big piece (≥ 0.3 share), as wide as its flames spread
(2 × their farthest reach from their centre + the widest flame, × 0.7–1 with heat, at most 3 m), alpha
0.09 × heat × a darkness factor. Widths grow with the flame (0.45 + 0.55 × flame) and the catch swell
(+45 %). Tongues lean with the air past the body (0.11 per m/s, at most 0.7). Detail by distance:
all anchors with cores ≤ 32 m, half the anchors without cores ≤ 64 m, a quarter ≤ 96 m, none beyond
(the body burns on). The frame's `MAX_INSTANCES` 1,024 are shared evenly: `begin(bodies in range, a
piece counting as its burnShare, particle density, glow strength)` gives each body
`clamp(1024 / bodies, 3, 113)`, a piece `floor(that × burnShare)`; the living are drawn before the
remains. A density setting below 1 thins flames to at most half, since they are how a body shows it
burns. Drawn in `ParticleRenderer`'s third instanced submission with premultiplied blending: a flame
adds its light and covers 0.12 (night) to 0.6 (day) of what is behind it, so it stays orange by day
and glows at night; glows stay additive. `particle.vert` sprite 4 is a base-anchored billboard rising
along world up (tilted 0.35 towards the view's up), flickering in height on its own rhythm; it fades
within 0.3–1.1 m of the camera and into the fog. `particle.frag` shapes the tongue: a rounded base,
a noise-torn edge scrolling upward, licks tearing loose near the tip, a yellow-white core cooling to
the rim. Everything flickers by `ParticleSystem.time`, advanced only in simulated frames: paused
flames stand still.

**Char, glow and self-light** (`entity.vert`/`entity.frag`, set per body by `Renderer.fireMaterial`
and zeroed by `noFire()` after every body and before anything else, so nothing leaks to the next
body, to terrain or to effects): `uScorch` darkens the whole body with soot (× 1 − 0.45 scorch) and
spreads warped char patches in box-local metres (threshold 0.97 − 0.62 scorch), seeded by each part's
colour, so species and kit colours stay between them; `uBurnGlow` lights a thin ember line where
char meets skin; `uFireLight` adds the body's own orange light (flickering). The held item is lit by
the player's fire but never charred.

**Given off** (`BodyFireEffects`, owned by `AmbienceSystem`, every 0.12 s pass, only in simulated
frames): per body per pass `LICKS` 1.4, `EMBERS` 0.3, `SMOKE` 0.9 and `STEAM` 1.4 particles at full
strength, scaled by the look, by the body's anchors over 12 (0.4–2), by range (full ≤ 48 m, half ≤
96 m, none beyond), by the crowd (× 24 / bodies within 48 m past 24) and, for a piece, by its share;
at most `MAX_PER_BODY` 6 per body and `MAX_PER_PASS` 96 per pass, all stopping at
`ParticleSystem.SPLASH_LIMIT`. Licks and embers leave from alight anchors, smoke and steam from any;
each leaves with the body's velocity (licks 0.7, smoke 0.9, steam 0.6 of it) and its
`ParticleSystem` air response (licks 3/s, embers 0.8/s, smoke 1.1/s, steam 1.6/s) settles it into
the rain field's wind. Smoke and steam are the new `KIND_HAZE`: see-through (≤ 0.38), fading in and
swelling 0.55 → 2.15 of their size. The player's own body gives off nothing into the view.

**First person.** `Environment.vigBurn` eases (10/s) towards the player's flame (× 1 + 0.3 flare),
0 while dead; `post_final.frag` draws two offset rows of flame tongues along the bottom edge, higher
at the lower corners (at most about a third of the screen height there), thin flames up the lower
sides and a warm wash at the edges; the crosshair and the middle of the view stay clear. While the
player burns, the red damage vignette and HUD edges show only the flash above the afterburn floor
(`BodyFireLook.shownDamageFlash`); `Player.damageFlash` itself, and sleep's reading of it, are
unchanged. With an item in hand, 2–5 small flames lick round its grip (`BodyFlames.grip`), drawn in
camera space after it; with nothing in hand there is no hand geometry to put them on.

**Sound** (`BodyFireEffects` → `AudioManager.playBodyCrackle/Flare/Sizzle`, buffers from
`engine/BodyFireSounds`, synthesized last from their own seed): the four burning bodies nearest the
listener within 24 m (the player included; a body in pieces is heard once, at its core piece) crackle
1.5–5 times a second (ambience bus, background priority); a catch is heard once as a flare and a
douse once as a sizzle (ordinary priority), at most four such events a pass, remembered per body and
episode in a ring of 16; within 8 m the loudest takes over the fire loop when louder than any block
fire (`AmbienceSystem.updateAmbienceMix`). A new world (`AmbienceSystem.reseed`) forgets them.

**Budgets as built.** Anchors ≤ 56 per body; flames ≤ 1,024 per frame (≤ 113 per body), one extra
draw submission per frame when any are drawn (plus one for grip flames); ≤ 6 particles per body and
≤ 96 per pass, under the splash ceiling; ≤ 4 bodies heard; residues ≤ 32 (09). Measured headlessly:
posing and sampling 60,000 bodies allocates under 4 KB in total, a frame of 40 burning people's
flames under 64 bytes, an emitter pass over 75 burning bodies under 64 bytes (tests). Frame cost:
see the milestone 10 section of the progress record.

**Changes from the proposals above.** The "24 nearest within 48 blocks" rule became even sharing of
a fixed frame cap with distance detail (32/64/96 m) for flames, and range halves (48/96 m) with
crowd thinning past 24 for particles — fair under load without sorting. Flames are drawn every frame
on the posed body rather than emitted on the 0.12 s cadence; only released particles use the
cadence. Per-body flames are 2 × anchors (+ glow) rather than `1 + 4 × area × I`, because anchors
already scale with area.

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
  de-duplication, attribution and Creative immunity must still be asserted. *Done:* the fire
  helpers of `MolotovTest` and `CombatFireIntegrationTest` run the fast ticks of each medium
  tick; the four-patch test asserts one contact at `CONTACT_DPS_*` per fast tick (its bodies now
  each touch the pool's centre, since damage scales with the liquid's strength), the injury
  after a second alight, Creative immunity; the attack test still counts −36 then −72 for two
  bottles on two residents; the crowd test replaced the (NPC, spill) memory cap with "a crowd
  past the NPC cap all catch"; the rain test now also stands someone in the rained-on pool
  through the fast ticks and a control under the roof. `RuntimeBoundsTest` and
  `SimulationSystemContractTest` dropped `trackedNpcSpills()` (the new world asserts bottle ids
  restart instead).
- `FireSystem.damageNear` behaviour relied on by fire/weather tests (07): stacking and
  through-floor damage are intentionally removed. *Done:* `CreativeHazardsTest`,
  `SurvivalCreativeParityTest` and `BlastLethalityTest.fireBombIsNotLethal` drive half a second
  of the combustion fast tick instead of a medium tick of the fire systems.

## 18. Dependency handoff

| Milestone | Consumes | Produces for later milestones |
| --- | --- | --- |
| 02 anatomy (done) | §3.2, §7; `BodySkeleton.of`/`humanoid`, `CreatureModels.of`, `NpcModels.get`, `EntityModel.part`, `ModelPart` pivot/box/split fields, `BodyFragment.Piece`, `FragmentModels` | `BodyFamily`, `FragmentAnatomy`, `FragmentPiece`, `FragmentCut`, `FragmentPose`, `AnatomyModels` (§7.1; exact API in the progress file); tests over `CreatureType.values()` + humanoid. |
| 03 blast deaths (done) | §8, §9; 02 definitions | Shared `Entity` blast record, generalized `ExplosionSystem` gate, `BodyFragmentSystem` spawn for any family, anchored `Carcass`, player-remains spawn at the `Game.frame` death transition (§8.1, §9.1). |
| 04 fragment rendering (done) | 02 definitions, 03 fragments | Definition-driven model selection and isolation in `Renderer.drawFragment`, cut faces per family, bounds from full geometry, suppressed intact carcass, all-species QA scene (§7.2). |
| 05 persistence (done) | §7 save strategy, 03 remains ids | `world.remains` v1 for every family's settled pieces, their poses and the anchored harvest links (with lodged arrows); `world.fragments` v1 unchanged and pinned by a literal fixture (§14.1). |
| 06 combustion state (done) | §5, §6, §10, §11, §15 | `Entity.combustion` (`BodyCombustion`), `CombustionSource`, `CombustionSystem` (`expose`/`ignite`/`extinguish`/`clear`/`isBurning`/`burningBodies`, read-only getters), fast-tick wiring, `CreatureAI` dead guard, medical-BURN gating, reset in `WorldBootstrap`, `FireSystem.isPrecipitationReaching`, `RuntimeBudgetSnapshot.burningBodies` (§10.1). |
| 07 sources (done) | §4, §10; 06 API | `BodySweep` swept contact sampled in `CombustionSystem` for patches (`LiquidFireSystem.exposeContacts`), burning cells, campfires and torches (`FireSystem.exposeContacts`); direct-hit ignition in `ProjectileSystem.onEntityHit`; legacy contact damage removed; block-fire attribution (`FireSystem.ignite(…, byPlayer, origin)`); one attack per person per bottle; people and animals keep out of torches and campfires (`Entity.keepsOutOfFlames`, `VoxelPhysics`, `Steering`, `Pathfinder`); flame placement refused into a burnable body (§4.1). |
| 08 panic (done) | §12; 06 snapshot, 07 ignition | `ai/FirePanic` hooked first in `NpcAI.update`, `SettledNpcAI.update` and `CreatureAI.update`; `PanicIntent` on `Npc.panic`/`Creature.panic` (read-only getters); seeded `EntityManager.nextPanicFloat`; NPC screen closure and a talk gate while panicking; straight-line goal checks on the pathfinder's footing, ground look-ahead, bird flight escape; recovery reset (§12.1). |
| 09 lifecycle (done) | §6, §9, §14 | `BurnResidue` captured at the death transition by `BurnResidueSystem` (`Game.burnResidues`), moved ragdoll → corpse/carcass and shared by a body's pieces (`burnShare`), aged and bounded per frame; first-lethal-cause order; `EntityManager.depart`/`removeNpc`/`removeNpcs` for every departure; `FirePanic.forget`; `Player.tickNeeds` dead guard; `Game.simulates`/`advanceWorld`; sleep refusal and wake; dialog-target resets (§14.2). |
| 10 presentation (done) | §16 presentation budgets, 02/04 geometry, 06/09 snapshots | `BodyFireLook` (one status-to-look mapping), `FlameAnchors` + `BodyPosing` (flames on the drawn pose), `BodyFlames` (per-frame tongues, shared cap), `BodyFireEffects` (licks, embers, smoke, steam, sound), char/glow/self-light in `entity.frag`, first-person fringe and grip flames, body-fire sounds, `BodyFireQaScene` scenes, read-only out memory / flicker / touch offset on `BodyCombustion` (§16.1). |
| 11 validation | the whole contract | Production-path matrix tests, bounds/perf evidence, docs, updated `COMBAT_LETHALITY_AND_MOLOTOV.md` limits. |

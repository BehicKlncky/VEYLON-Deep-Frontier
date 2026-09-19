# Lethal combat and molotov fire — 2026-09-20

Implementation: `382832d`…`63bba07` on `feature/combat-lethality-molotov`, based
on `main` at `570f548` (v0.7.4). Seven milestones: projectile hit zones, body
fragment physics and rendering, blast lethality, fragment persistence, fire in
rain, the molotov simulation and its presentation, then this integration pass.

Host: Windows 11 x64, Java 25.0.4.1 (Temurin), Gradle 9.1.0. Not the reference
machine the repository's wall-clock budgets were recorded on.

## The rules

Every value below is the constant the code reads, not a restatement of intent.

### Who the rules apply to

People — `com.veylon.entity.Npc` — whoever shot them, player or NPC. The player
and creatures keep the damage model they always had, and creatures are never
dismembered.

### Hit zones and torso wounds

Every projectile that deals impact damage is judged by where it went in:
`ProjectileSystem.Kind.BULLET` (musket, flintlock, blunderbuss pellets, relic
carbine, relic rifle) and `Kind.ARROW` (the primitive bow with either arrow).
`Kind.BOMB` and `Kind.FIRE_BOMB` deal no impact damage and have no row; asking
for one throws.

`HitZone` measures the height above the victim's feet at which the projectile's
path crossed into the hit box, by slab intersection, so a steep shot is judged
where it entered rather than at the next sub-step sample up to 0.45 blocks
further on. The thresholds come from the humanoid in `NpcModels.build()` and
`BodySkeleton.buildHumanoid()`, which is drawn at one scale for every
archetype:

| Zone | Entry height above the feet | Effect |
| --- | --- | --- |
| Head | `HEAD_MIN_HEIGHT` 1.47 and above | kills outright, bullet or arrow |
| Torso | `TORSO_MIN_HEIGHT` 0.86 up to 1.47 | wound units, below |
| Legs | below 0.86 | the projectile's own damage, unchanged |

An NPC in `NpcState.SLEEP` is lying down, so every hit on one is a torso hit.

Torso wounds accumulate in integer units on the victim, independent of maximum
health, armour, archetype and any healing in between. `LETHAL_TORSO_WOUNDS` is
6:

| Projectile | Units per torso hit | Torso hits to kill | Damage of a non-lethal torso hit |
| --- | --- | --- | --- |
| Bullet | `BULLET_TORSO_WOUNDS` 3 | 2 | `max(projectile damage, 0.50 × maxHealth)` |
| Arrow | `ARROW_TORSO_WOUNDS` 2 | 3 | `max(projectile damage, 0.33 × maxHealth)` |

Mixed hits add up: a bullet and two arrows kill on the third hit, whoever fired
them. An arrow wound counts for less than a bullet's and costs a smaller share
of maximum health; against a person with little maximum health an iron arrow's
own 16 damage can still exceed a third of it, which is the floor the table
takes the larger of.

Wounds count **once per trigger pull**: every pellet of one blunderbuss shot
carries the same `Projectile.shotId`, so the first pellet into a chest wounds
and the rest deal their ordinary pellet damage.

### Lethal blasts and dismemberment

`ExplosionSystem.explode` takes `lethalToHumans`. A thrown scrap bomb (power
2.6, damage 14) and every powder keg (`KEG_POWER` 3.8, `KEG_DAMAGE` 30,
`KEG_IGNITE` 0.35) pass it, including a keg set off by a blast that is not
itself lethal. Everyone whose body centre lies within
`power × LETHAL_RADIUS_FACTOR` (1.5 — 3.9 blocks for a scrap bomb, 5.7 for a
keg) dies regardless of health, archetype or cover, and is blown apart. Outside
that radius the existing falloff damage over `power × 2.4` is unchanged, as it
is for creatures and the player. A fire bomb is not a bomb for this rule.

A body blown apart becomes ten pieces at the skeletal joints: torso, head
(neck and head), upper arm and forearm, thigh and shin, left and right. Each
piece is an independent rigid body with the ragdoll solver's own gravity, water,
drag, friction and voxel sweep, stepped at the same fixed 1/60 s from the same
per-frame gate, and decays on `RagdollConstants.CORPSE_DECAY` (420 s).

| Launch | Value |
| --- | --- |
| Impulse per unit of blast strength | `IMPULSE_BASE` 9 |
| Falloff | `clamp(1 − d / (strength × 1.5), 0.25, 1)` |
| Upward bias every piece gets | `UPWARD_BIAS` 4.5 m/s |
| Mass | `DENSITY` 260 × box volume — a forearm is ~14× lighter than the torso |
| Spin about the body's mass centre | `SPIN_GAIN` 6 per metre of lever arm |
| Scatter across the blast direction | `TANGENT_JITTER` 0.2 of the launch speed |
| Settling | 12 quiet grounded steps under `SETTLE_ENERGY` 0.25, or `SETTLE_TIMEOUT` 8 s |
| Caps | `MAX_LIVE_FRAGMENTS` 120, `MAX_SETTLED_FRAGMENTS` 600 |

Nine severed joints each throw a blood burst at `JOINT_BURST_POWER` 0.6, and a
piece drips while it moves, through the already-seeded particle system. The
system adds no generator of its own: scatter and spin the blast does not decide
come from a hash of the piece's position.

### The molotov

`ItemType.FIRE_BOMB` is unchanged as an item and keeps its name. It now
shatters on the **first** block or body it touches — the 2.4 s fuse only
catches a bottle that never lands — and produces no fireball, no blast damage
and no broken blocks: glass, burning droplets, the crack of the bottle and the
hiss of the liquid catching.

`LiquidFireSystem` spills the pool as a priority flood from the cell the bottle
broke over, ordered by `distance − DIRECTION_BIAS × dot(throw, offset)` with
`DIRECTION_BIAS` 0.6, so it runs further along the throw:

| Spill | Value |
| --- | --- |
| Cells per bottle | `MAX_PATCHES_PER_SPILL` 22 |
| Cells in the world | `MAX_PATCHES` 160, oldest spill evicted first |
| Reach from the impact cell | `SPILL_RADIUS` 3.2 |
| Fall between neighbouring cells | `MAX_DROP` 2 |
| Ground searched below the impact | `SURFACE_SEARCH_DEPTH` 3 |
| Burn time | `BURN_SECONDS_MIN` 7 + up to `BURN_SECONDS_RANGE` 4 at the centre + up to 1 s jitter |
| Intensity | 1 at the centre, `1 − RIM_INTENSITY_LOSS 0.5 × edge` at the rim |

The liquid never runs uphill and never enters a solid or water cell; tall
grass, bushes and saplings soak it. A patch goes out early if it is built over,
flooded or the ground beneath it burns away.

Each medium tick a patch burns whoever stands in it — `ENTITY_DPS_PLAYER` 6,
`ENTITY_DPS_NPC` 10, `ENTITY_DPS_CREATURE` 12, once per entity per tick however
many patches it straddles, with the burn affliction on the shared
`FireConstants` roll — rolls `IGNITE_CHANCE_PER_TICK` 0.35 × intensity for each
flammable block in and beside its cell, and arms a neighbouring powder keg with
`FireConstants.KEG_FUSE_SECONDS` 1.5 for whoever threw the bottle. Ignition goes
through `FireSystem.ignite`, so pool-lit fires compete for the same
`MAX_ACTIVE_FIRES` 220 as every other block fire and spread from there on their
own. A player's bottle counts as one attack per person burned, per bottle
(`MAX_TRACKED_NPC_SPILLS` 64 remembered pairs).

### Rain

`FireSystem.isRainedOn` is the one predicate: `weather.isPrecip()` (rain, storm
and snow) and `world.skyLight(x, y + 1, z) > RAIN_EXPOSURE_SKYLIGHT` 0.9. A
burning block the rain reaches holds still — its burn timer stops and it rolls
no spread — and goes out after `FireConstants.RAIN_EXTINGUISH_SECONDS` 2
without being consumed: a log stays a log. Wetness dries at the rate it built
up. A pool goes out after `LiquidFireConstants.RAIN_EXTINGUISH_SECONDS` 1 and
neither burns nor ignites while it is soaking. Sheltered fires and pools burn
on, spreading at `RAIN_SPREAD_FACTOR` 0.12 of the dry rate, and a pool under
cover does not light a block the rain is falling on.

Spread itself changed with the rain work: a spread roll now picks uniformly
among the flammable neighbours that are not burning, rather than among all 26
cells, and a roll aimed at the layer above wins `UPWARD_SPREAD_BIAS` 2 times as
often. A fire at the foot of a five-log trunk under a two-deep crown reached the
crown in 0 of 200 seeds before and 200 of 200 after, 5.6 s later on average.

## Design decisions

- **The wound count is on the body, not the weapon.** Two bullets or three
  arrows kill a captive and a leader alike, which is the point: a firearm is
  decisive against people whatever their health pool. Keeping it in units, with
  a damage floor as a share of maximum health, means a wounded person still
  looks wounded on the health bar and still dies to the count.
- **Damage runs through `Entity.hurt`, kills included.** `Npc.killBy` hurts for
  more than the remaining health rather than writing `dead`, so attribution,
  reputation, loot, quest credit and the death pipeline see an ordinary kill.
- **What killed a body decides how it falls.** Only a lethal blast sets
  `dismemberOnDeath`, only on a body it killed, and the first fatal blast's
  record is the one kept. A body already dead when a blast arrives — shot a
  moment earlier, not yet collected by the entity tick — still falls whole.
- **Fragments mirror the ragdoll solver rather than generalising it.** A
  ragdoll is a jointed chain; a piece is one rigid box. Sharing the numbers
  (gravity, water, drag, friction, fixed step, decay) keeps a severed limb
  falling like the body it came from, while the sweep box is refit from the
  piece's current orientation each step so a limb lying down rests on its lowest
  corner instead of hovering or sinking.
- **Mass follows volume.** A forearm leaves a blast about fourteen times faster
  than a torso. Limbs fly several metres while a torso barely leaves the spot —
  physically consistent, and visible in the captures below. An area-weighted
  impulse would throw torsos further; that is a tuning choice, not a fix, and it
  has not been made.
- **The fire bomb became a molotov without becoming a new item.** No enum
  constant, recipe, icon or sound was added; what changed is what happens when
  the bottle lands.
- **One ignition path.** The pool lights blocks through `FireSystem.ignite`
  rather than keeping its own fire list, so one cap covers every flame in the
  world and rain, spread and burn-down keep working on pool-lit fires for free.
- **Pools are not saved, pieces are.** Burning blocks were never saved; pools
  behave like them. Pieces are debris that lasts seven minutes and would be
  conspicuous by its absence, so settled ones persist in their own section.

## Test inventory

886 tests in 121 classes pass; these are the ones this work added or changed.

| Class | Covers |
| --- | --- |
| `combat.ProjectileHitZoneTest` (18) | head and torso lethality for both projectiles, the entry-point classification, blunderbuss pellets counting once, sleepers, NPC-fired shots, the player and creatures being unaffected, and a kind with no table row |
| `combat.BlastLethalityTest` (11) | the lethal radius, cover not saving anyone inside it, falloff damage outside it, ten pieces instead of a ragdoll, kegs and chains, the fire bomb not being lethal, and every death consequence firing exactly once |
| `entity.BodyFragmentPhysicsTest` (15) | the ten pieces and where they start, flight away from the blast, lighter pieces thrown faster, blood at every severed joint, collision, no tunnelling, determinism, push-out, caps, decay and culling |
| `entity.BodyFragmentAllocationTest` (2) | the fragment step allocates nothing per frame, airborne and on the ground, at the live cap |
| `gfx.FragmentGeometryTest` (8) | anchors, transforms, cut faces, the vest clearance, culling radius, rot tint, and allocation-free posing |
| `gfx.FragmentIsolationTest` (5) | a piece draws exactly its own parts, keeps the person's appearance, and `forceSplitDraw` |
| `save.FragmentsSectionTest` (7) | the round trip, a save taken mid-flight, an absent section, corrupt and oversized payloads, the cap, and a piece the reader would refuse |
| `simulation.FireWeatherTest` (9) | rain putting out exposed fires without consuming the block, no spread while rained on, sheltered fires burning on, snow and storm, frozen burn timers, drying off, climbing a tree and a wall, and the fire cap |
| `simulation.MolotovTest` (15) | shattering on a block and on a body, the pool's shape and bounds, water, burning entities and Creative invulnerability, lighting a tree and grass, rain, kegs, reputation per bottle, an in-flight bottle surviving a save, a pool under cover leaving wet grass alone, and the throw message |
| `engine.MolotovPresentationTest` (7) | shatter particles, flame colour, density and caps, no shake or blast, the rag trail, and the sheet's fade and heat |
| `LiquidFireEmitterTest` (3) | the pool's flames, embers, smoke and steam, and the splash ceiling |
| `CombatFireIntegrationTest` (8) | the cross-feature cases: head shots falling whole when a blast follows, a wounded body blown apart exactly once, wounds shared between shooters, a burn death falling whole, a bottle setting off a keg, one fire cap for both sources, rain at a tree, and Creative parity |
| `GameLoopIntegrationTest` | a new world and a load clearing pieces, pools, wounds and blast records |
| `qa.RuntimeBoundsTest` | every cap reached exactly and never passed, and the combined worst case below |
| `qa.SerializedEnumOrderTest` | `NpcArchetype` and `BodyFragment.Piece` ordinals, which the new section writes |
| `simulation.SimulationSystemContractTest` | the liquid-fire system's cadence and cross-world reset |

Changed on purpose: `combat.CombatSystemsTest` expected the fire bomb to
explode and start fires directly, and now expects it to break and spill;
`save.ActiveExplosivePersistenceTest` expected three explosions from a restored
pair of bombs and a keg, and now expects two plus one bottle breaking;
`settlement.SettlementReputationGameplayTest` moved its resident so both stages
of a keg chain still reach a live person.

Worst case, in `RuntimeBoundsTest`: twelve people blown apart by three
scrap-bomb blasts (120 pieces, exactly the live cap), seven bottles burning (154
patches) and 220 block fires, stepped together at 60 fps for twelve seconds.
Every hard limit held on every frame, all 120 pieces came to rest, and the
fragment step allocated **24 bytes per frame** against the 4 KB allowance
`RagdollAllocationTest` uses.

## Native visual evidence

Captured on this host at 1280×720 with `VEYLON_SEED=20260919`. The PNGs are in
the working directory's `screenshots/`, which is git-ignored; the commands
regenerate them exactly.

| Scene | Shots | Files |
| --- | --- | --- |
| `dismember_showcase` | `1,3,8` | `screenshots/dismember_showcase_{1,3,8}s.png` |
| `dismember_wall` | `1,3,8` | `screenshots/dismember_wall_{1,3,8}s.png` |
| `dismember_bomb` | `2.2,4,9` | `screenshots/dismember_bomb_{2,4,9}s.png` |
| `molotov_ground` | `0.7,1.2,3,7,11` | `screenshots/molotov_ground_{0,1,3,7,11}s.png` |
| `molotov_tree` | `1,4,10,20` | `screenshots/molotov_tree_{1,4,10,20}s.png` |
| `molotov_rain` | `2,3.5,5` | `screenshots/molotov_rain_{2,3,5}s.png` |

```powershell
$env:VEYLON_SEED='20260919'; $env:VEYLON_SCENE='dismember_bomb'; $env:VEYLON_SHOT='2.2,4,9'; .\gradlew.bat run
```

The dismemberment scenes were also captured at 1920×1080
(`screenshots/p03_showcase_hd_*.png`, `p03_wall_hd_*.png`) and rerun for
determinism (`p03_wall_determinism_*.png`): the same seed put every piece in
the same place at both resolutions.

`VEYLON_SHOT` names a file by whole seconds, so two shots in one run that round
to the same second overwrite each other; `VEYLON_CAPTURE_TAG` separates them.
What the scenes logged, twice each with the same numbers: the scrap bomb goes
off at 2.4 s and the entity tick at 2.45 s turns five of the six people into 50
pieces with no ragdoll, the last at rest 1.833 s later, while the farmer 5.4
blocks out stays standing; the bottle breaks at 1.00 s over 22 cells, the tree's
trunk lights at 1.50 s and its canopy at 6.50 s, and rain puts the whole pool
out at 4.00 s.

## Deliberate limits

- **Torsos and heads barely move at scrap-bomb strength.** Mass follows volume,
  so the heavy pieces take a small fraction of the impulse; only limbs fly far.
  The captures show it. Changing it means weighting the impulse by area, which
  is a gameplay decision nobody has signed off.
- **Pools and burning blocks are not saved.** A save taken mid-burn loads with
  the fires out. Only settled pieces and a bottle still in the air persist.
- **Zone thresholds assume the unscaled humanoid.** They are heights in metres
  above the feet, read off the one model every archetype uses. A future scaled
  or non-humanoid person would need them derived from the model rather than
  fixed.
- **Wounds live on the `Npc` instance.** A save and load, or a settlement going
  dormant and reactivating, returns a person at their stored health with no
  wounds.
- **A bottle that breaks in the air spills nothing.** The ground search reaches
  three blocks below the impact cell, so a bottle that hits the side of a tree
  canopy high up leaves no pool. Landing on top of the leaves works.
- **A burning log or leaf block barely reads.** The block-fire glow cube is at
  most one block and sits inside the opaque block it belongs to, so a burning
  canopy looks weak next to the liquid's sheets. Pre-existing, unchanged here.
- **A rained-on flame still arms a keg and still burns whoever stands in it**
  until the rain finishes it, and a sheltered block fire may still spread into
  an open cell at the reduced rate, where the rain puts it out again.
- **Only people are dismembered.** Creatures, the player and every non-blast
  death keep whole-body ragdolls, and pieces cannot be harvested or looted.

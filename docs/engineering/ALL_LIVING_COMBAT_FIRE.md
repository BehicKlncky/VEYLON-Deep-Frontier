# All-living dismemberment and body fire — engineering record

Integrated validation of 2026-09-28 (milestone 11 of 12). Branch
`feature/all-living-dismemberment-combustion`, based on `main` at `3704f4d`
(`merge: release v0.8.0`); not yet merged or released. Host: AMD Ryzen 5 5600X,
NVIDIA GeForce RTX 3060 Ti (OpenGL 3.3, driver 610.88), 32 GB, Windows 11 x64,
Temurin 25.0.4.1, Gradle 9.1.0 — not the reference machine the repository's
wall-clock baselines were recorded on.

This is the summary for engineers who maintain or extend the work. Every rule and
number is in the [implementation contract](ALL_LIVING_COMBAT_FIRE_CONTRACT.md)
(cited below as §n); the milestone-by-milestone commands, evidence and handoffs
are in the [progress record](ALL_LIVING_COMBAT_FIRE_PROGRESS.md). It supersedes
parts of the [0.8.0 record](COMBAT_LETHALITY_AND_MOLOTOV.md), which lists what
changed in its follow-up section.

## What the game does now

- **A lethal blast tears every living body apart.** Inside `power × 1.5` of a
  scrap bomb (3.9 blocks) or a powder keg (5.7), measured to the body's centre,
  every person, every animal (a bird in flight included) and a Survival player
  dies whatever its health and cover, and comes apart at its own joints: a person
  into 10 pieces, a deer into 11, a wolf, thornhorn or gloomstalker into 12, a hare
  or a bird into 7. A Creative player is untouched. Other deaths fall whole.
- **Real flames set bodies alight.** Burning liquid, burning blocks, fueled
  campfires, placed torches and a fire bomb breaking on a body give it one fire of
  its own that outlasts the contact, hurts it until it burns out, and is put out by
  water over most of the body or open rain on its head. Light, warmth, lanterns,
  furnaces, glowing blocks and held items are not flames.
- **Burning people and animals panic.** They drop whatever they were doing and
  run in irregular, obstacle-aware bursts away from the flame (birds fly,
  climbing), then settle and decide afresh. The player is never moved, slowed,
  sped up or turned by a fire.
- **One death, one body, paid once.** Credit, quests, trust, reputation, loot,
  bird meat and an animal's harvest happen once per death; a body that dies alight
  hands its flames to the one corpse, carcass or set of pieces it leaves.
- **Seen and heard.** Flames stand on the limbs of the pose drawn (living,
  falling, dead, in pieces), bodies char, give off flame licks, embers, smoke and
  steam, and crackle; the burning player sees flames at the edges of the view.

## Where it lives

| Concern | Owner | Contract |
| --- | --- | --- |
| Pieces of each body | `entity/BodyFamily`, `FragmentAnatomy` (tables), `FragmentPiece`, `FragmentPose`; `gfx/model/AnatomyModels` holds them to the models | §7.1 |
| Lethal gate and blast record | `combat/ExplosionSystem` (`lethalToLiving`), `Entity.recordBlastDeath` / `killBy` | §8.1 |
| Piece physics, caps, harvest link | `entity/BodyFragmentSystem` (one system for every family), `Carcass.remains` ↔ `BodyFragment.harvest` | §9.1 |
| One fire per body | `Entity.combustion` (`BodyCombustion`), written only by `entity/CombustionSystem` (`Game.combustion`) | §10.1 |
| Flame contacts | `entity/BodySweep`; `LiquidFireSystem.exposeContacts`, `FireSystem.exposeContacts`; direct hits in `ProjectileSystem.onEntityHit` | §4.1 |
| Panic | `ai/FirePanic`, `ai/PanicIntent` on `Npc.panic` / `Creature.panic`, stream `EntityManager.nextPanicFloat` | §12.1 |
| Remains' flames | `entity/BurnResidue` on the ragdoll, corpse, carcass or pieces; `entity/BurnResidueSystem` (`Game.burnResidues`) | §14.2 |
| Leaving without dying | `EntityManager.depart` (`removeNpc`, `removeNpcs`, the administrative path, despawn) | §14.2 |
| Presentation | `gfx/BodyFireLook`, `gfx/model/BodyPosing`, `gfx/model/FlameAnchors`, `gfx/BodyFlames`, `BodyFireEffects`, `engine/BodyFireSounds`, shaders | §16.1 |
| Saved remains | `save/RemainsSection` (`world.remains` v1) beside `world.fragments` v1 | §14.1 |

## The fixed update order

A simulated frame (`Game.advanceWorld`, run only while `Game.simulates()`):

1. The scheduler's fast ticks (up to ten), each in this order:
   1. `Player.tickNeeds` — needs and medical afflictions (nothing for a dead player).
   2. `CombustionSystem.fastTick` — for the player, then creatures, then people: skip
      the dead and the Creative player; sweep the body's box from its last sample
      and ask every flame for a contact; keep the strongest; water, then contact
      (ignite or refresh), then rain; damage through `Entity.hurt`; burn down.
   3. `EntityManager.fastTick` — per body: AI (`FirePanic` first), physics, then
      death routing: every consequence once, then one body (pieces for a death
      with a blast record, else a ragdoll); administrative removals depart.
   4. `SettlementManager.fastTick`.
2. At most one medium tick: block and liquid fire spread, burn out, meet the rain
   and arm kegs. They hurt nobody.
3. At most one slow tick: rot of carcasses, corpses and pieces; despawns depart.
4. Particles, then projectiles (a fire bomb breaking on a body ignites it before it
   spills; a scrap bomb's fuse ends in `explode`), ragdolls, pieces, remains'
   flames, keg fuses, noise, and the ambient emitters (`BodyFireEffects`).

Then the player's death transition (`Game.enterDeathIfDue`, which also lays down
a blast victim's remains), and drawing. Hence: a burn death in a frame is
processed before any blast in it, a body already dead is skipped by a blast (so
it gains no record and gives up no credit), and presentation reads a finished
step. Combustion advances only in `FAST_DT` steps, so equal simulated time gives
equal fires however it is split into frames (§6).

## Damage and attribution

- **Blast.** `killBy` hurts for more than the remaining health through the
  ordinary path (armour bypassed for the player); the record is written only if
  that call killed the body, and the first fatal blast's record is kept. Kegs are
  lethal whatever set them off. Outside the radius: the unchanged falloff, cover
  and knockback (§8).
- **Contact.** Each fast tick a body keeps one contact: kind first (direct hit >
  liquid > burning block > campfire > torch), then intensity, then the player's
  over the environment's, then the older source, then the lower point — so the
  order flames report in never matters, and flames never add up (§10.1).
- **Fire damage.** `(contact ? contact rate : afterburn rate) × intensity × dt`
  per fast tick: people 10 / 3, animals 12 / 2.5, the player 6 / 2 per second
  (Creative gated in `Player.hurt`, no armour, no bleed). Liquid and direct hits
  light at once; a burning block after 0.25 s, a campfire after 1.25 s, a torch
  after 1.67 s of contact; grazes decay (§15).
- **Credit.** The fire belongs to the contact that lit or last refreshed it;
  whoever owns it at the killing tick gets the kill, however long after the
  body left the flame. A block fire a bottle started carries that bottle's owner
  and id as it spreads. Blasts, lightning and meteors light environmental fires.
- **Attacks.** The player's flame lighting or taking over a person's fire is one
  attack per bottle (`REPORTED_BOTTLES` 4 remembered per body), never per patch or
  tick; its perception update respects Creative (§4.1).
- **Medical burn.** A Survival player gets `Affliction.BURN` once per fire, after
  a second alight; it neither hurts nor counts down while the flames burn, and a
  poultice treats the injury, not the fire (§11).

## Panic priority

`FirePanic.update` is the first decision of `NpcAI.update` (after the dead guard
and per-tick upkeep): before a conversation holds a person, before the whole
settlement brain (captives, hostile combat, war parties and counterattacks,
friendly defence, search, work, sleep), wandering traders, raiders and camp jobs.
`SettledNpcAI.update` repeats the call for direct callers. `CreatureAI.update`
calls it before any species' choice. A panicking body attacks, shoots, reloads,
trades, works, eats and sleeps nothing; it picks a new goal every 0.6–1.2 s, or
sooner when blocked but never more than four a second, each checked along a
straight line with the pathfinder's footing; it
turns at 270°/s; it stops at drops deeper than three blocks and at the edge of the
loaded world. 1.5 s after the flames go out its plans are reset and its ordinary
AI runs from the world as it is. Panic never reads the player or the camera
(§12.1).

## Transient state and saves

- **Not saved** (§14.2): a body's fire, heat, soak, owner, scorch, reported
  bottles; panic; remains' flames and scorch; pools and burning blocks (as in
  0.8.0). Saving leaves them all running; a load, a new world, respawn and a switch
  to Creative start without them. A body leaving the world without dying takes no
  fire, panic or body with it; a dormant settlement keeps each resident's health.
- **Saved** (§14.1): every settled piece of every body (family, piece, position,
  orientation, decay, look, the pose it died in) and an animal's harvest link and
  lodged arrows in the new optional section `world.remains` v1. `world.fragments`
  v1 is still written byte for byte as 0.8.0 wrote it. This build reads
  `world.remains` when present and `world.fragments` otherwise; 0.8.0 skips
  `world.remains` by its length (reasoned from its reader, not run: no 0.8.0
  binary is exercised by the suite). Pieces in flight are settled before a save.
  Family ordinals and piece ids are append-only and pinned in
  `SerializedEnumOrderTest`.

## Runtime budgets

| Limit | Value | Enforced by |
| --- | --- | --- |
| Pieces in flight / at rest | 120 / 600 (`BodyFragmentConstants`) | `RuntimeBudgetSnapshot` (smoke gate, F3), `RuntimeBoundsTest` |
| Harvest records tied to pieces | 60 (`MAX_ANCHORED_REMAINS`) | snapshot, `RuntimeBoundsTest` |
| Ragdolls | 12 (`RagdollConstants.MAX_LIVE`) | snapshot |
| Remains' flames tracked | 32 (`MAX_BURN_RESIDUES`); past it the oldest loses flames, never scorch | snapshot, `CombustionLifecycleTest` |
| Burning / panicking bodies | ≤ the living bodies (one embedded state each) | snapshot |
| Block fires / liquid cells | 220 / 160 (22 a bottle) | snapshot |
| Flames drawn | 1,024 a frame, shared evenly (≤ 113 a body, a piece by its share) | `BodyFlamesTest`, `RuntimeBoundsTest` |
| Particles given off | ≤ 6 a body, ≤ 96 a pass, under the splash ceiling | `BodyFireEffectsTest` |
| Flame anchors | ≤ 32 on the anatomy + ≤ 24 on worn parts, per family | `FlameAnchorsTest` |
| Bodies heard | 4 nearest; 16 catches or douses remembered | `BodyFireSoundsTest`, `BodyFireEffectsTest` |
| Panic goals | ≤ 1 per 0.25 s per body; no pathfinder query | `FirePanicTest` |

Allocation, from the default suite: the combustion tick 0 bytes per fast tick
with a whole crowd burning (96 with every flame source at its cap: the campfire
fuel map's key), panic 0 bytes over 5,000 ticks of blocked replanning, the piece
step 25 bytes a frame at the live cap with every fire cap full (this run), flames
and emitters under 64 bytes a frame or pass. The whole world step at the full
mixed load below allocated about 10.9 KB a frame on average; this work's own
steps are held by the tests just listed, and the rest of the step (ordinary AI
decisions once bodies recover, fire spread, the other systems) is bounded by no
test. Wall clock is in [Validation](#validation-2026-09-28).

## Extending it

- **A species**: [DEVELOPING "A creature"](../DEVELOPING.md#a-creature) — model,
  skeleton, then a `BodyFamily` constant and its `FragmentAnatomy` table (every
  joint the model and its animation move, parent first, with the builder's pivots
  and boxes; pieces core first; halves shorter than 0.10 m stay with their parent),
  pinned in `SerializedEnumOrderTest`. Flames, fire, panic, saving and drawing
  follow from the table, and the matrix tests pick the species up from
  `CreatureType.values()`.
- **A flame source**: [DEVELOPING "A flame that sets bodies
  alight"](../DEVELOPING.md#a-flame-that-sets-bodies-alight) — a volume that stays
  on its own side of solid faces, an `exposeContacts` adapter called from
  `CombustionSystem.sampleFlames`, a `CombustionSource` kind, a weather rule,
  attribution, and `FlameSourceIgnitionTest` cases.
- **An archetype or a party kind** needs nothing for this work; the matrix stands
  every `NpcArchetype` and `Npc.PartyKind` up automatically.
- The contributor rules are in DEVELOPING, "Living bodies in combat and fire".

## Validation (2026-09-28)

### Coverage

`AllLivingEndToEndTest` (new in milestone 11) builds its matrix from the
registries — every `CreatureType`, every `NpcArchetype` as a resident of a
settlement on the side its kind takes (headhunters, scavengers and captives
hostile, other settlers friendly or neutral in turn), every
`Settlement.Alignment`, every `Npc.PartyKind`, the camp member, wandering trader
and raider, and the Survival player — and drives each through the player's own
commands and the frame's world step:

| Matrix row | Where it is shown |
| --- | --- |
| Every living target | `AllLivingEndToEndTest` (both matrices, 28 bodies each); `AllLivingBlastDeathTest`, `BodyCombustionTest`, `FirePanicTest` per family |
| Blast: real scrap bomb, armed keg, chain; inside, exactly on, outside the radius; cover; repeated; already dead; departed | scrap bomb thrown with the attack command at every body, and a keg lit with the interaction key chaining a second through a burning room (`AllLivingEndToEndTest`); boundary floats, cover, repeat (`AllLivingBlastDeathTest`); fire first then blast (`CombustionLifecycleTest`, the room test) |
| Ignition from every source; false positives | a bottle thrown with the attack command at every body, a bird hit in the air with no pool (`AllLivingEndToEndTest`); pools, blocks, campfires, torches, sweeps between ticks, walls, floors, rain, warmth, lanterns, held torches (`FlameSourceIgnitionTest`) |
| Combustion: afterburn, overlap, refresh cap, burnout, water, rain, shelter, medical injury | `BodyCombustionTest`, `CombustionIntegrationTest`, `FlameSourceIgnitionTest` |
| Panic: every family and species, attacking, sleeping, talking, trapped, blocked, flight, recovery, determinism | every body flees in the fire matrix; the rest in `FirePanicTest` |
| Player control and Creative | `BodyCombustionTest`, `FirePanicTest` (movement identical to an unburned twin); the Creative player on their own bomb and in their own fire (`AllLivingEndToEndTest`) |
| Death and rewards once | every body in both matrices: its death lines at most once, and nothing awarded or charged in the next ten seconds; the hunt request and camp trust by fire and by keg; bird meat, harvest and lodged arrows (`AllLivingBlastDeathTest`, `RemainsPersistenceTest`) |
| Persistence and lifecycle | the burning room saved and loaded (pieces and records once, no fire); `RemainsSectionTest` (v2/v3/0.8.0 fixtures, literal bytes), `RemainsPersistenceTest`, `CombustionLifecycleTest` (mid-flight save, load reset, new world, dormancy, modes, pause, sleep) |
| Stress | every effect at its ceiling at once in a storm, stepped by frames (`AllLivingEndToEndTest`); `RuntimeBoundsTest`; allocation tests |
| Presentation | `FlameAnchorsTest`, `BodyFireLookTest`, `BodyFlamesTest`, `BodyFireEffectsTest`, `BodyFireSoundsTest`, `SpeciesFragmentDrawTest`, native captures below |

The full-load test holds 40 people (every archetype) and 35 animals (every
species) burning and fleeing in a half-roofed yard in a storm, with the block-fire
and liquid caps full, eleven wolves blown apart (132 pieces against the 120 that
may fly) and sixteen hares burning to death (against 12 ragdolls), for ten seconds
of frames. Every hard limit held on every frame; the particle system passed its
splash ceiling (3,440); and the same world stepped with no particles at all ended
with every body, piece, fire and panic draw bit for bit the same. A new world
afterwards kept no piece, ragdoll, fire, residue or remembered sound. Headless,
the world step averaged 0.212 ms and about 10.9 KB a frame over the run.

### Commands and results

| Command | Result |
| --- | --- |
| `gradlew build --rerun-tasks` at `1e65484` (before this milestone) | BUILD SUCCESSFUL, 1462 tests in 142 classes, 0 failures |
| `gradlew test --tests com.veylon.AllLivingEndToEndTest --tests com.veylon.qa.RuntimeBoundsTest --tests com.veylon.CombustionIntegrationTest` | 73 tests, 0 failures |
| Mutation check (14 production faults, one at a time, against `AllLivingEndToEndTest` only) | **14 of 14 caught**: 11 at first; the three survivors were gaps in the new tests (a Creative player never standing in the pool, caps filled but never passed), fixed and then caught |
| `gradlew performanceTest` | 9 of 10 gates pass, plus both rain benchmarks; the durable-save gate fails (5.327 ms against 2.20 ms), the host gap recorded since 0.7.4 |
| `gradlew build --rerun-tasks` (the committed code) | **BUILD SUCCESSFUL, 1523 tests in 143 classes, 0 failures, 0 errors, 0 skipped**; javadoc (doclint) and check executed |

### Performance

`AllLivingFullLoadBenchmarkTest` (tagged `performance`) times the parts at the
full load above, the fastest of seven fresh runs after three discarded, against
the targets the contract set for the reference machine (§16):

| Part | Measured on this host | Target |
| --- | ---: | ---: |
| Combustion tick (contact queries for 75 bodies, 220 block fires, 160 liquid cells) | 0.093 ms | 0.2 ms |
| Entity tick with every body panicking | 0.029 ms | reported |
| Piece step, 120 in flight | 0.029 ms | 1.0 ms |
| One emitter pass (released particles and sound) | 0.147 ms, every 0.12 s | reported |
| One frame's flames (posing, anchors, packing) | 0.072 ms | reported |
| Body-fire presentation a frame (flames + emitter share) | 0.092 ms | 0.5 ms |
| Whole world step of a frame | 0.520 ms | reported |

Natively (vsync off, 1280 × 720): `body_fire_row` 0.68–0.72 ms a frame with 11
bodies alight and 645 flames against 0.67 ms once out; the panic yard 0.75–0.77
ms with 7–8 alight against 0.75 ms; a burning body blown apart, pieces in flight,
0.80–0.84 ms against 0.75 ms at rest. The smoke gate passed at 142 fps average.

### Native captures

After `gradlew installDist`, every registered scene of this work ran with
`VEYLON_SEED=20260919`, its documented shots and its own `VEYLON_CAPTURE_TAG`,
into the isolated data directory `build/qa/all-living-m11` (git-ignored; not
committed): `body_fire_row`, `_row_night`, `_close`, `_out`, `_blast`, `_panic`,
`_rain`, `_ragdoll`, `_bird`, `_player`; `dismember_species`, `_wall`, `_close`
(plus a run for pieces in flight); and 0.8.0's `dismember_bomb` and
`molotov_ground` for regression — 75 images, every run `glErrors=0 khrErrors=0`,
and a 45 s smoke run (`VEYLON_SMOKE=45`) whose runtime line reported the new
counts within their limits. Inspected:

- Bodies catch low and are covered by 3 s (`m11_row_1s`, `_3s`); at night they
  are lit by their own flames (`m11_night_3s`); burnt out they are charred in
  patches with each species' and each kit's colours between (`m11_row_10s`).
- In the rain the open bodies steam and go out while those under the roof burn
  on (`m11_out_4s`, `m11_rain_3s`).
- Burning pieces tumble with blood (`m11_blast_2s`); burning bodies collapse
  with flames on their falling limbs and settle into smoking corpses and
  carcasses, each death logged once (`m11_ragdoll_3s`, `_7s`).
- Every fleeing body is mid-stride with flames on its own limbs; the hare's are
  small (`m11_panic_2s`); three birds burn in flight (`m11_bird_1s`).
- The burning player sees flames along the bottom edge and lower corners and
  round the axe's grip, the middle of the view clear (`m11_player_1s`).
- Every species, a guard and the player's remains come apart at their joints,
  fly and settle against the step and wall with wound faces and no whole carcass
  beneath (`m11_species_0s`, `m11_species_flight_1s`, `m11_species_8s`,
  `m11_species_wall_3s`, `m11_species_close_3s`); 0.8.0's scrap-bomb group and
  molotov pool look as before (`m11_bomb_4s`, `m11_molotov_3s`).

## Limitations and open points

- **Not verified here**: macOS, other GPUs and drivers, render distances other
  than the default, 4096 shadows; the sound by ear (headless tests only); a
  0.8.0 build opening a new save (reasoned, not run).
- **Performance** figures are from a secondary host. `performanceTest`'s durable
  save gate fails on this host with or without this work (recorded since 0.7.4);
  it needs investigating on the reference machine.
- **Tuning is proposed, not signed off** (§15): burn and panic numbers, and the
  0.8.0 question of torsos and heads barely moving at scrap-bomb strength.
- **Presentation limits** (§16.1): char does not follow where flames touched
  first; a burning body lights itself, not the world round it; billboards
  foreshorten from straight above; in very crowded frames the smallest pieces of
  a body may show no flame of their own.
- **Gameplay limits**: one fire per body (a severed limb landing in water does
  not put out the rest); only a lethal blast leaves the player's remains, so a
  player who burns to death leaves no body; loaded remains carry no scorch;
  burning bodies never spread fire.
- **Open finding, not fixed** (pre-existing, outside this work): caged captives in
  generated forts spawn with their heads in the cage roof and cannot move.
- **Balance consequence to note**: a Survival player inside their own scrap
  bomb's (3.9 blocks) or keg's (5.7) lethal radius now dies outright (§8).

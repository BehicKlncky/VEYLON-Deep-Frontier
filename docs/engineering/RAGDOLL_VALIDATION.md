# Articulated ragdoll validation — 2026-09-18

Implementation: `7b6ec92` on `feature/ragdoll-joint-articulation`, based on
`main` at `d9c3b26`. Baseline instrumentation is commit `9c97a1b`; it adds QA
fixtures and a benchmark without changing the old solver. `main` was not modified.

Host: Windows x64, AMD Ryzen 5 5600X, Java 25.0.4.1, Gradle 9.1.0,
NVIDIA GeForce RTX 3060 Ti, NVIDIA 610.88, OpenGL 3.3. This is not the
Intel reference machine used for the repository's older performance budgets.

## Result and design

Humans have ten parent-relative joints; quadrupeds, including hares, have
twelve; birds have six. Visible upper/lower limbs, neck/head joints and
two-link tails replace flat appendages. Hinges enforce one-way bending;
cones leave swing inside their limits free. Wings now use all three pose axes.

Gravity acts on independent endpoints. Lengthwise voxel samples, limb-versus-
torso contact, oriented torso supports and contact friction determine the
landing. There is no rest-direction pull or target roll/pitch. Whole-chain
motion and supported torso contact govern sleep, with the unchanged six-second
hard timeout. Fixed stepping, catch-up clamping, position-hash choices,
population bounds and the paused-game gate remain.

The body save section is version 2. Version 1 joint indices are mapped into
the new tree; unsolved legacy and QA bodies retain the fallback. Death removal,
appearance, arrows, tracks, blood and corpse conversion retain their lifecycle.
No serialized enum order or base save layout changed.

The [authoring note](RAGDOLL_JOINTS.md) explains the skeleton format and all
tuning. Principal values: six constraint passes; shoulder/hip cones
2.40/1.35 rad; neck/head 0.70/0.85; tail/wing 1.40/1.90; elbow 0–2.60;
knee/hock signed one-way 2.50 rad. Terrain sample spacing is at most 0.10 m,
skin 0.004 m, Coulomb friction 0.65 and per-step contact velocity friction 0.26.
Sleep requires 12 quiet steps and squared-speed measure below 0.25, or 30
steps of bounded contact jitter. Every endpoint stays within 0.04 m and root
rotation within 0.06 rad during that window. The only toppling assistance is
a one-time minimum launch angular speed of 1.5 rad/s.

## Headless checks

`gradlew.bat build` passed in 3m 21s: **775 tests, zero failures, zero skipped**,
including packaging and Javadoc. Log: `build/ragdoll-final-build.log`.

Existing suites were extended, not replaced:

| Suite | Tests | Coverage added or retained |
|---|---:|---|
| `RagdollPhysicsTest` | 19 | Hinges, cones and lengths throughout full falls; visible folding; relative shoulder motion and continued swing after contact; limb self-contact; all-species segment/endpoint clearance; finite state; standing deaths; short inset sockets; terrain edits; water; unloaded chunks; timeout; pause and catch-up clamp |
| `DeathRagdollTest` | 17 | Solver handles match reconstructed renderer transforms for every model; bird wings move; all pose axes copy/reset; existing death payload and conversion tests |
| `RagdollAllocationTest` | 2 | Full population airborne and sustained ground-contact paths |
| `CreativeBodyTest` | 18 | Creative and survival deaths retain identical solved joint poses; existing direct/legacy body behavior |
| `BodiesSectionTest` | 6 | Three-axis round trip, handcrafted version 1 migration, malformed section rejection |

The folding assertions require elbows and knees to move more than 0.25 rad.
The free-limb test requires shoulder swing above 0.5 rad and continued motion
after ground contact. A flat, single-segment authored limb cannot meet this
contract. Identical human deaths delivered at 30 and 144 fps produce exactly
equal endpoint arrays, all joint-angle arrays, and bit-identical root angles.

Every point and eleven positions along every segment are checked against solid
voxels throughout the all-species stepped-terrain fall. Settling times there:

| Deer | Wolf | Bird | Hare | Thornhorn | Stalker | Human |
|---:|---:|---:|---:|---:|---:|---:|
| 5.117 s | 1.517 s | 2.617 s | 1.000 s | 4.117 s | 1.750 s | 5.050 s |

All finish before the six-second backstop. The timeout still handles exceptional
cases; these tests are evidence for the exercised falls, not an exhaustive
proof over every possible terrain edit or launch condition.

## Performance

`gradlew.bat performanceTest` was run before and after. Measurements below are
from this same host. The ragdoll profile warms 3,000 fixed steps, then reports
the fastest of five 6,000-step samples with all twelve wolves kept live over
loaded terrain. It includes the fixture's inexpensive reseating work.

| Measurement | Before | After | Gate/result |
|---|---:|---:|---|
| Ragdoll, 12 live bodies | 0.016588 ms/tick | 0.372215 ms/tick | Pass, 1.00 ms |
| Ragdoll airborne allocations | — | 0 bytes/tick | Pass, existing 4 KiB allowance |
| Ragdoll contact allocations | — | 0 bytes/tick | Pass, existing 4 KiB allowance |
| Chunk tick | 0.547 ms | 0.475 ms | Pass, 0.92 ms |
| Entity tick | 0.466 ms | 0.414 ms | Pass, 0.95 ms |
| Settlement tick | 0.016 ms | 0.017 ms | Pass, 0.52 ms |
| Durable save | 6.114 ms | 5.750 ms | Fails before and after, 2.20 ms |

Articulation costs an additional **0.356 ms per full-population tick**. The new
total is about 2.2% of a 16.67 ms frame, or 0.031 ms per body in this fixture.
This is a deliberate increase in physics work, not a claim of unchanged speed.
The airborne benchmark is not a worst-case world-edit recovery benchmark.

**The overall performance command is not green.** Its only failure is the
pre-existing durable-save gate, already failing at 6.069 ms and 6.114 ms before
the physics changes. The save assertion stops that test before its subsequent
load timing. All eight other performance tests, plus both rain performance
tests, pass. No existing baseline or budget was raised. The save gate should be
investigated separately on the documented reference machine; this feature does
not establish a new save regression.

Logs: `build/ragdoll-baseline-profile.log`, `build/ragdoll-final-performance.log`.
Allocation output is in the default suite's `RagdollAllocationTest` XML report.

## Native visual evidence

All captures use `VEYLON_SEED=20260918`, `VEYLON_RESOLUTION=1600x900`, native
OpenGL rendering and the existing QA harness. Both original requested scenes,
`death_ragdoll_showcase` and `death_ragdoll_sequence`, were captured before/after.
Close human, ledge, side-view quadruped and living galleries provide clearer
comparisons. Later fixtures were also copied to an isolated baseline checkout
at `9c97a1b`; only the QA files changed there, preserving the baseline physics
and models. No screenshot was generated or retouched.

Example PowerShell capture, with Java 25 selected through `JAVA_HOME`:

```powershell
$env:VEYLON_SEED = '20260918'
$env:VEYLON_RESOLUTION = '1600x900'
$env:VEYLON_SCENE = 'death_ragdoll_drape'
$env:VEYLON_CAPTURE_TAG = 'after_death_ragdoll_drape'
$env:VEYLON_SHOT = '1,2,3,5,8'
.\gradlew.bat run
```

Capture seconds 1/2/3/5 correspond to physics times 0.25/0.8/2/5 s; second 8
repeats the final snapshot. The living fixtures use seconds 1/3/5 for fixed
idle/walk/attack poses. Capture logs report zero GL and KHR errors.

Representative original PNGs are committed alongside this report:

| Comparison | Before | After | Observation |
|---|---|---|---|
| Human, 0.8 s | [Before](ragdoll-captures/before_death_ragdoll_human_2s.png) | [After](ragdoll-captures/after_death_ragdoll_human_2s.png) | Old body is a straight slab; new knees fold as the torso falls, with the head independently tilted. Later frames show the body landing. |
| Quadruped, 2 s | [Before](ragdoll-captures/before_death_ragdoll_quadruped_3s.png) | [After](ragdoll-captures/after_death_ragdoll_quadruped_3s.png) | Old wolf takes its prescribed side roll; new legs fold under a differently supported torso, with separate head and tail articulation. |
| Ledge, 5 s | [Before](ragdoll-captures/before_death_ragdoll_drape_5s.png) | [After](ragdoll-captures/after_death_ragdoll_drape_5s.png) | Old body lies straight below the shelf; new legs remain above while the upper body and arm hang over the edge. |
| All living models, walk | [Before](ragdoll-captures/before_ragdoll_living_species_3s.png) | [After](ragdoll-captures/after_ragdoll_living_species_3s.png) | Same silhouettes, colours and animation. |

Pixel comparisons cover rectangle x=340..1159, y=170..699, containing the NPC
and all six creature models. Both `ragdoll_living` and `ragdoll_living_species`
have **zero changed pixels** at all three idle/walk/attack snapshots. The model
split draws the original cuboid whenever its child is straight; living Animator
logic is unchanged.

The complete local series remain under `screenshots/`, with prefixes
`before_`/`after_` and scene names. The original showcase baseline uses
`ragdoll_before_showcase_`. Native logs use `build/after_<scene>.log`.

## Deliberate limits

The torso remains one rigid box. There are no pelvis/chest, finger or foot
joints, independent axial twist, soft tissue, accessory collisions, limb-versus-
limb collisions or collisions between separate corpses. Segment contacts use
overlapping small voxel-swept boxes rather than an exact capsule; exceptional
unresolvable penetration translates the complete chain to preserve joint limits.
These are bounded approximations; no physics engine or new dependency was added.

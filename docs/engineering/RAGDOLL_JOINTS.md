# Articulated death bodies

The death pipeline still removes the living entity and applies gameplay
consequences on the killing tick. A bounded transient ragdoll then emits a
carcass or human corpse with its appearance, lodged arrows, blood track and
final pose. Birds leave no corpse.

## Adding a species

Build the visual tree in `CreatureModels`, then describe it in
`BodySkeleton.buildCreature`. The humanoid uses the same format.

- List joints in parent-first order, at most 12. `part` names a real model
  part. `parent = TORSO (-1)` attaches to the rigid torso; otherwise it is an
  earlier joint index.
- Root pivots use model coordinates, including the model's standing height.
  Child pivots use the parent part's unrotated frame. Unmapped intervening
  model parts must remain unrotated in a solved pose.
- Supply a rest vector from pivot to handle. Its magnitude becomes `length`;
  its normalized direction becomes `restX/Y/Z`. All directions work.
  For a chain, the next joint normally sits at the preceding handle.
- Supply the segment's collision radius, sized from its cross-section. The
  solver samples its length at intervals no larger than 0.10 m or 1.8 radii,
  whichever is smaller. Neck/head handles may cover an offset skull.
- Supply `torsoY` and the torso box's half extents. They define the root
  centre, terrain supports and limb-versus-torso proxy.
  The skeleton derives a self-contact exemption for the authored socket
  overlap. This prevents short, inset upper legs from fighting their own torso.
- Choose a cone or hinge. A hinge rotates around the parent's X axis and has
  signed minimum and maximum angles. A cone limits swing from the rest vector;
  movement inside it has no rest-direction spring. Cone frames use the
  shortest rotation, so there is no independent axial twist degree of freedom.

Humans have ten joints: neck/head, upper arm/forearm and thigh/shin on each
side. Quadrupeds have twelve: four upper/lower legs, neck/head and two tail
links. Front knees and rear hocks bend in opposing directions. Hares use
`head_joint` as the neck name so legacy living-animation lookups for an absent
`neck` stay inert. Birds use six joints: head, tail and four wings.

`ModelPart.split` divides an existing box at its midpoint. With the child
straight, it submits the original cuboid with its original dimensions; bent
children draw the two halves. Living animation continues to rotate the old
upper-part names, while new child joints remain at identity. Accessories stay
on their existing parents. No models are cloned per corpse.

## Solver and settling

`RagdollSystem` retains a 1/60 s fixed step, four-step frame catch-up limit,
twelve-body cap and the existing paused-game gate. A double accumulator with
a small float-input tolerance gives the same fixed steps at 30 and 144 fps.
No solver choice reads wall time or a random generator.

Endpoint masses integrate independently against gravity. Six parent-first PBD
passes project lengths and joint limits, transfer reactions through the chain,
resolve limb self-contact and sweep sampled segments through loaded voxels.
The torso has an oriented box with corner, face-centre and centre probes.
Contact corrections act at their lever arms; joint reactions also torque the
torso. A launch impulse breaks the singular balance of perfectly straight legs.
There is no roll or pitch target.

Final projection produces exact chain lengths and joint limits. If a terrain
contact still conflicts at a corner, the final clearance pass translates the
whole chain without changing angles. An enclosing world edit has a bounded
upward search for free space. This is an exceptional recovery path, not a
landing pose. Unloaded columns remain barriers; they are not treated as air.

Velocities come from actual travel. Coulomb friction at contact points resists
sliding and rotation; velocity friction and low restitution dissipate the
remaining contact motion. Iterative penetration repair cannot increase the
incoming maximum-point-speed-plus-angular-speed budget. Only a supported torso
can accumulate quiet steps. A 12 mm
support tolerance bridges the collision skin, avoiding alternating airborne
and grounded flags on tiny limbs. Every endpoint must stay within 4 cm of its
sleep snapshot and root rotation within 0.06 rad. Twelve such steps plus low
maximum point/angular speed freeze the pose; thirty such steps can sleep through
bounded contact jitter. Movement resets the window, so a stationary torso cannot
freeze a swinging limb. The six-second timeout remains a hard backstop. Population
overflow and saves can still freeze a body immediately.

All scratch arrays, vectors and quaternions are allocated at system/body
construction. Body admission and corpse emission allocate; fixed steps do not.
No broadphase, worker thread or third-party physics library is involved.

## Tuning

All global tuning lives in `RagdollConstants`; dimensions live in the skeleton.

| Setting | Value |
|---|---:|
| Shoulder cone | 2.40 rad (137.5°) |
| Hip cone | 1.35 rad (77.3°) |
| Neck / head cones | 0.70 / 0.85 rad (40.1° / 48.7°) |
| Tail / wing cones | 1.40 / 1.90 rad (80.2° / 108.9°) |
| Elbow hinge | 0 to 2.60 rad (149.0°) |
| Human knee / quadruped front knee | -2.50 to 0 rad (-143.2° to 0°) |
| Quadruped rear hock | 0 to 2.50 rad (143.2°) |
| Constraint passes | 6 |
| Root inverse mass / parent reaction | 0.08 / 0.35 |
| Support rotational gain | 4 |
| Contact spacing / skin / support tolerance | 0.10 / 0.004 / 0.012 m |
| Air drag / ground friction per fixed step | 0.012 / 0.26 |
| Coulomb contact friction | 0.65 |
| Restitution | 0.10 |
| Angular damping in air / on torso support | 0.55 / 3.4 per second |
| Minimum one-time toppling angular speed | 1.5 rad/s |
| Point / angular speed caps | 42 m/s / 13 rad/s |
| Quiet squared-speed threshold / steps / timeout | 0.25 / 12 / 6 s |
| Contact-jitter sleep window / travel / rotation | 30 steps / 0.04 m / 0.06 rad |

Gravity remains 26 m/s² in air and 7 m/s² in water, with the existing water
drag and terminal descent. The old 1.45 rad carcass roll exists only as
`FALLBACK_CREATURE_ROLL` for unsolved legacy/QA bodies.

## Pose and persistence

`BodyPose` stores root Y-X-Z orientation and parent-relative X/Y/Z joint
angles plus a bone count. Model parts compose those angles as Rz * Ry * Rx.
The skeleton provides the hierarchy; tests reconstruct model transforms to
check that rendered handles coincide with simulated endpoints.

The optional `world.bodies` save section is version 2. It writes three floats
per joint. Version 1 is still read: old flat joint indices map by name into the
new tree, and new child rotations start at zero. Root position/orientation and
appearance are retained. Saves without that section still use
`BodyPose.solved == false` and the established carcass fallback. Base v3 bytes
and serialized enum order are unchanged.

## Validation fixtures

Use seed `20260918`, resolution `1600x900`, and shot times `1,2,3,5,8`:

- `death_ragdoll_showcase`: four animals and a human.
- `death_ragdoll_sequence`: close quadruped fall.
- `death_ragdoll_quadruped`: the same fall viewed from the side.
- `death_ragdoll_human`: close human fall.
- `death_ragdoll_ledge`: human at a one-block shelf.
- `death_ragdoll_drape`: a fall across the shelf edge, with a contrasting lower floor.
- `ragdoll_living`: fixed idle, walk and attack poses (shots 1, 3, 5).
- `ragdoll_living_species`: the same poses for every species and an NPC.

Capture seconds 1, 2, 3 and 5 correspond to simulation times 0.25, 0.8, 2 and 5 s;
shot 8 repeats the last snapshot. These keep physics paused between snapshots. The living fixture pins
animation time independently of capture timing. The final measurements and
capture observations are recorded in `RAGDOLL_VALIDATION.md`.

Deliberate limits: torso shape is a single box; contacts approximate segments
with overlapping small boxes; self-collision covers limbs against torso, not
limb against limb or body against another body. Fingers, feet, independent
axial twist, soft tissue and accessory collision are not simulated.

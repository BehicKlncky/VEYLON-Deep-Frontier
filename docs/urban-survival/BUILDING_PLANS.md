# Building plan examples — template revision 1

Milestone 05, 2026-10-10. These are fresh outputs of the production headless
`BuildingPlanDiagnostics` command, seed `20260731`, region `(0,0)`, ground floor.
The apartment is the actual reserved refuge parcel (frontage west), the shop the
first commercial parcel (frontage north). World north is at the top. `#` is a
wall, `D` a two-high door, `W` a two-high window over a sill, `C` a container
reservation, `T` a table, and digits show stair tread heights above this floor's
slab. Higher treads appear projected into the ground-floor diagram. Room anchors
are world cells; footprints, partitions, fixtures and ids all come from the plan.

A typical domestic floor contains bedroom, bathroom, kitchen, living, storage
and utility rooms around the two-wide corridor and core. Shallow parcels combine
kitchen/living and storage/utility; the minimal fallback also combines bedroom
and bathroom. Shops have a sales floor, stock/utility rooms and a rear door; their
optional upper floor uses the domestic layout. This catalogue starts with one
flat per storey (decision D-59), not multi-unit blocks. No loot is rolled here.

B1 has slab 36, ground 40, each following floor is four cells higher, and the
roof slab is `40 + 4 * storeys`. The 4 x 6 core has two two-wide flights of two
rises, a two-deep intermediate landing and a top landing that reconnects to the
corridor and next flight. Only the tread surface is solid, preserving clearance
between stacked flights. The same core continues from optional B1 to the roof.

```text
seed=20260731 id=urban:building:0,0:44:0 fingerprint=0xb0f9b6f1052ccff7 attempts=1
ok=true violations=0 cells=8398 transitions=4927 reachable=1420 escapable=1420 diagnostics=[]
apartment-court revision=1 floor=0 slab=40 facing=WEST variant=INTACT
#W###W####W###W##
#.C#..C....C#..C#
#..#........#...#
#..#........#...#
#..#........#...#
#..#........#...#
#..#........#...#
#..#........#...#
#..#........#...#
#..#........#...#
#..#........#...#
#..#........#...#
#..#........#...#
#..#........#...#
#D###D#D#D###D###
D...............D
D...............D
#.1222.D#D###D###
#.1222......#...#
#.4322......#...#
#.4322......#...#
#...........#...#
#...........#...#
#...........#...#
#...........#...#
#...........#...#
#...........#...#
#...........#...#
#...........#...#
#...........#...#
#..........C#..C#
##########W###W##
room=0 [ENTRY] at=65, 41, 96
room=1 [CORRIDOR_STAIRS] at=71, 41, 88
room=2 [CORRIDOR_STAIRS] at=71, 41, 105
room=3 [CORRIDOR_STAIRS] at=65, 41, 104
room=4 [BEDROOM] at=65, 41, 88
room=5 [BATHROOM] at=68, 41, 88
room=6 [KITCHEN] at=73, 41, 88
room=7 [LIVING] at=77, 41, 88
room=8 [STORAGE] at=73, 41, 105
room=9 [UTILITY] at=77, 41, 105

seed=20260731 id=urban:building:0,0:16:0 fingerprint=0x3c648c643014bf19 attempts=1
ok=true violations=0 cells=5040 transitions=3225 reachable=911 escapable=911 diagnostics=[]
shop revision=1 floor=0 slab=40 facing=NORTH variant=INTACT
############DD############
#..........#.............#
#..........#..1144.......#
W..........#..2233.......#
#..........D..2222.......#
#..........#..2222.......#
#C.........#.............#
#..........D..D..........#
#..........#..#..........#
#..........D..D..........#
#..........#..#..........#
#..........#..#..........#
W..........#..#..........W
#..........#..#..........#
#..........#..#..........#
#..........#..#..........#
#C.........#..#.........C#
##################DD######
room=0 [ENTRY] at=143, 41, 7
room=1 [CORRIDOR_STAIRS] at=136, 41, 13
room=2 [CORRIDOR_STAIRS] at=150, 41, 13
room=3 [CORRIDOR_STAIRS] at=150, 41, 7
room=4 [SHOP_FLOOR] at=136, 41, 7
room=6 [STOCKROOM, UTILITY] at=136, 41, 15
room=8 [STOCKROOM, UTILITY] at=150, 41, 15
```

The validator examines the exact immutable voxel proposal. Counters are bounded
cells and directed edges, not wall-clock timing claims. Each clear room cell,
room/landing anchor and interaction target needs both entry and exit reachability;
flights additionally need real swept clearance in both directions. All unlocked
doors are evaluated after opening, while locked doors remain solid. Occupied
variants lock the front and retain an unlatched rear route. Saved touched states
override the planned lock, including explicit unlock; 06 will connect defaults to
stamped fixtures. No key-only-behind-its-lock dependency exists in this family.

An intentionally obstructed departure ceiling on the first example remains
reachable by the other stair lane, but the declared flight's clearance is invalid.
The production validator rejects it and the finite alternate repairs it:

```text
invalid stair fixture: ok=false violations=1 cells=8398 transitions=4925 reachable=1420 escapable=1420 diagnostics=[STAIR: invalid swept clearance or step]
finite repair: attempts=2 ok=true violations=0 cells=8398 transitions=4927 reachable=1420 escapable=1420 diagnostics=[]
```

`BuildingPlannerTest` pins geometry and reservation fingerprints for both families
at seeds `20260731`, `-4242` and `7`. Its 768 nominal-size/family/orientation/variant/
seed cases and 4,112 actual parcels in the frozen six-seed/eight-region matrix are
headless plan evidence. `BuildingPlanTraversalTest` additionally drives production
player movement on staged proposal stairs up and down in Survival and Creative.
Native plan-debug captures are optional for 05 and were not run. Generated,
furnished street-to-basement/roof exploration and native day/night evidence are
milestone 06 gates. No screenshot, manual exploration or integrated furnishing
claim is made by these diagrams.

# Commercial and civic catalogue — template revision 5

Milestone 12, 2026-10-14. Generator 11 stamps building template 5
([D-98–D-101](DECISIONS.md#milestone-12--commercial-and-civic-buildings-2026-10-14-to-2026-10-16)).
The diagrams below are the ground floors of the first building of each family
near the origin for seed `20260731`, printed by the production diagnostics
command (`BuildingPlanDiagnostics.main` with the argument `catalogue`). The
office block, a 27 × 27 five-storey plan downtown, repeats the school's desk
islands on every floor and is left out for length. Its fingerprint is pinned
with the others in `CivicCatalogueTest`.

Symbols are as above, plus `L` for a locked door, `R` for a rack upright and
`=` for security bars. Non-solid furniture (beds, basins, bedrolls, debris)
shows as `.`. Each plan keeps the walk-up skeleton: the two-wide entry hall from
the street door, the cross hall, the stair core beside the entrance and the
rear entrance. Only the program and the overlay differ:

- **Grocer** (damaged): shop floors with shelves along the facade, produce
  tables in between and a frontage display window; stockroom and cold store.
- **Clinic** (picked clean): the front block is the dispensary, a concrete room
  with bars and a counter. Its door is already forced in this variant, so it
  shows as `.`; intact and damaged clinics keep it locked. A treatment room, the
  washroom and utility, and a locked rear staff door (`LL`).
- **School** (damaged): staff office, classroom desk rows and a canteen.
- **Security post** (damaged): the locked metal armoury door (`L`) with lockers
  and a bench inside and bars at its window; squad room, equipment and evidence
  stores; a locked metal sally port at the rear.
- **Warehouse** (picked clean): workshop, then rack halls whose uprights (`R`)
  alternate with shelf bays (`C`) between one-cell aisles.

```text
GROCERY id=urban:building:0,0:16:0 sign="Ashgrove Grocers" fingerprint=0x64c28f93045ef3e3
grocery revision=5 floor=0 slab=40 facing=NORTH variant=DAMAGED
##W#########DD############
#T.........#.............#
#..T.T.T.T.#..1144.......#
W..T.T.T.T.#..2233.......#
#C.T.T.T.T.D.............#
#..T.T.T.T.#.............#
#C.........#.............#
#..........D..D..........#
#..........#..#..........#
#C.........D..D.........C#
W..T.T.T.T.#..#..........W
#C.........#..#.........C#
############..############
#T.........D..D..........#
WC.T.T.T.T.#..#.........CW
#..T.T.T.T.#..#..........#
#C.........#..#.........C#
##################DD######
room=0 [ENTRY] at=143, 41, 7
room=1 [CORRIDOR_STAIRS] at=136, 41, 13
room=2 [CORRIDOR_STAIRS] at=150, 41, 13
room=3 [CORRIDOR_STAIRS] at=150, 41, 7
room=4 [SHOP_FLOOR] at=136, 41, 7
room=6 [SHOP_FLOOR] at=136, 41, 15
room=7 [SHOP_FLOOR] at=136, 41, 19
room=8 [STOCKROOM] at=150, 41, 15
room=9 [STORAGE] at=150, 41, 19

CLINIC id=urban:building:0,0:26:0 sign="Tessaly Clinic & Pharmacy" fingerprint=0x70586f74b59259a8
clinic revision=5 floor=0 slab=40 facing=SOUTH variant=PICKED_CLEAN
##############LL###
#C.....#..#......C#
W......D..D.......W
#......#..#.......#
#......D..D.......#
########..........#
#C.....#..........#
#..T.T............#
=C.T.T.#..2233....#
#..T.T.#..1144....#
#C.....#..........#
########DD#########
room=0 [ENTRY] at=208, 41, 54
room=1 [CORRIDOR_STAIRS] at=203, 41, 48
room=2 [CORRIDOR_STAIRS] at=214, 41, 48
room=3 [CORRIDOR_STAIRS] at=213, 41, 54
room=4 [PHARMACY] at=203, 41, 54
room=6 [CLINIC] at=203, 41, 46
room=8 [BATHROOM, UTILITY] at=214, 41, 46

SCHOOL id=urban:building:0,0:105:0 sign="Varrow Colony School" fingerprint=0x2ebcaeb36c72614f
school revision=5 floor=0 slab=40 facing=NORTH variant=DAMAGED
###########DD###########
#.........#............#
#..T.T.T..#..1144......#
W..T.T.T..#..2233......#
#C.T.T.T..D............#
#..T.T.T..#............#
#C........#............#
#.........D..D.........#
#.........#..#.........#
#T........D..D.........#
#..T.T.T..#..#..T.T.T..#
W..T.T.T..#..#..T.T.T.CW
#..T.T.T..#..#..T.T.T..#
#C........#..#........C#
#####DD#################
room=0 [ENTRY] at=110, 41, 196
room=1 [CORRIDOR_STAIRS] at=104, 41, 202
room=2 [CORRIDOR_STAIRS] at=117, 41, 202
room=3 [CORRIDOR_STAIRS] at=116, 41, 196
room=4 [OFFICE] at=104, 41, 196
room=6 [CLASSROOM] at=104, 41, 204
room=8 [KITCHEN, BATHROOM] at=117, 41, 204

SECURITY id=urban:building:0,0:13:0 sign="Lumen Security Post" fingerprint=0x0eff6acfda0bc3d7
security-post revision=5 floor=0 slab=40 facing=EAST variant=DAMAGED
###W######=###
#C.C...#C.C.C#
#......#.....#
#.TT...#.TTT.#
#......#.....#
#.TT...#.TTT.#
#......#.....#
#......#.....#
####D#D##L####
L............D
L............D
####D#D...21.#
#.........21.#
#.........34.#
#.........34.#
#............#
#............#
#............#
#............#
#C..R........#
###W##########
room=0 [ENTRY] at=121, 41, 30
room=1 [CORRIDOR_STAIRS] at=115, 41, 25
room=2 [CORRIDOR_STAIRS] at=115, 41, 36
room=3 [CORRIDOR_STAIRS] at=121, 41, 36
room=4 [SECURITY] at=121, 41, 25
room=6 [OFFICE] at=113, 41, 25
room=8 [UTILITY, STORAGE] at=113, 41, 36

WAREHOUSE id=urban:building:0,0:49:0 sign="Wrenfield Depot & Workshop" fingerprint=0xc1d22d2d605d6b68
warehouse revision=5 floor=0 slab=40 facing=NORTH variant=PICKED_CLEAN
###############DD###############
#.............#................#
#..T.T.T.T.T..#..1144..........#
W..T.T.T.T.T..#..2233..........#
#C.T.T.T.T.T..D................#
#..T.T.T.T.T..#................#
#C............#................#
#.............D..D.............#
#.............#..#.............#
#.............D..D.............#
W..R.R.R.R.R..#..#..R.R.R.R.R..W
#..C.C.C.R.R..#..#..R.R.C.C.C..#
#C............#..#............C#
#######DD#######################
room=0 [ENTRY] at=175, 41, 65
room=1 [CORRIDOR_STAIRS] at=167, 41, 71
room=2 [CORRIDOR_STAIRS] at=184, 41, 71
room=3 [CORRIDOR_STAIRS] at=183, 41, 65
room=4 [WORKSHOP] at=167, 41, 65
room=6 [WAREHOUSE] at=167, 41, 73
room=8 [WAREHOUSE] at=184, 41, 73

```

`AccessibilityValidator` proves every room cell, landing, stair flight and
reservation of these plans in both directions. A secured room is reached only
through its forced phase: with locks solid it must be unreachable, its corridor
side must be on an ordinary route, and with the door broken every reservation
inside must be reachable and escapable. `CivicCatalogueTest` checks 1,344
family proposals over sizes, frontages, variants, storeys and the minimal
fallback, and every parcel of the frozen six-seed, eight-region matrix (4,112
plans). `BuildingPlanDiagnostics.familyMatrix` and `catalogue` name any failing
template by identity.

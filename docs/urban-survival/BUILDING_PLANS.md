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

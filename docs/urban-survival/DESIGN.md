# Urban Survival — Design Contract

Milestone 01 record, 2026-10-07, audited at `923345f` (game version 0.8.1). Since the pack snapshot `ff42cd8`, that commit changed only agent guidance and Graphify configuration. This is the design contract for the 26-package urban-survival roadmap. Nothing marked **Proposed** exists yet.

Companion records:
- [DECISIONS.md](DECISIONS.md): numbered decisions, compatibility policy, capacity budgets and open owner questions.
- [PROGRESS.md](PROGRESS.md): package ledger, owners, gates and the milestone handoff.
- [VALIDATION.md](VALIDATION.md): verified baseline with source citations, risk register, validation matrix and evidence log.

**Labels.**
- **Existing**: verified in current source. `B-nn` ids point to the citations in [VALIDATION.md §2](VALIDATION.md#2-verified-baseline-at-923345f).
- **Proposed**: designed here and built by the package named in brackets, for example *[05]*.
- *Initial*: a bounded starting quantity to implement and then tune in package 23. It is not a measured result.

## 1. Premise and mandatory owner requirements

The playable world of a new urban world is an abandoned city. The player enters apartments and other buildings, finds what supplies remain, avoids or confronts threats, and returns to a defensible refuge. VEYLON keeps its first-person voxel foundations, current engine and existing ownership model.

The owner's requirements below are **mandatory**. A package may not drop, weaken or postpone one past its gate as a scope shortcut; only the owner can change them, and the change is recorded in DECISIONS.md (§5 change procedure).

| Id | Requirement | Where it is proven |
| --- | --- | --- |
| R1 | The city covers the playable world: a connected urban fabric, not towns scattered in wilderness. Parks, canals and courtyards are part of that fabric. | 03, 13, 26 |
| R2 | The primary loop is: enter buildings, scavenge remaining supplies, avoid or confront threats, return to a defensible refuge. | 11, 23, 26 |
| R3 | Progression does not require chopping trees or mining ore: not for survival needs, the first upgrade or the long-term objective. | 08, 11, 20, 23, 26 |
| R4 | Every generated building has explorable, connected interiors (§5). Decorative sealed shells are a failure. | 05, 06, 12, 14, 26 |
| R5 | Small survivor camps exist inside the city as real encounters. | 16 |
| R6 | Guarded strongholds, the owner's "protected fortress", exist in urban architectural form. | 17 |
| R7 | Civilians hide in secret basements and are found through play. | 14, 16 |
| R8 | Distinct urban districts with distinct creatures, including zombie-like infected and alien-like threats. | 09, 13, 15 |
| R9 | All lore, architecture, factions, creatures, text, visuals and audio are original. The Last of Us and Project Zomboid are atmosphere references only: no names, mechanics signatures, text, art or sound are copied. | every package; 22, 26 |
| R10 | Historical worlds keep their original rules and layouts. The default new world stays legacy until 26 passes. | 02, 25, 26 |

These engineering contracts from the pack apply to every package:
- Main-thread simulation, world access, meshing, rendering and audio, with bounded work.
- Persistence of irreversible facts from the package that introduces them.
- Seeded streams with distinct salts.
- Loaded-column checks before relying on `World.getBlock`.
- Damage routed through the existing player gates, with Survival and Creative twins.
- `Locale.ROOT` for comparisons and identifiers.
- Bounded runtime collections.

## 2. Adopted defaults

These come from the pack and no current owner instruction contradicts them. Each is pinned in DECISIONS.md.

| Default | Decision |
| --- | --- |
| A connected, procedurally generated metropolis, streamed by district-sized planning regions; greenery and water are city fabric | D-01, D-06 |
| A new, explicitly selected urban generator version; historical generators frozen; legacy default until 26 | D-02, D-03, D-04 |
| Urban infected are the first hostile vertical slice; distinct alien organisms come later in quarantine districts | D-20 |
| Six district identities that change layout, interiors, supplies, hazards, encounters and atmosphere | D-07 |
| Long-term loop: secure a refuge, support or deal with survivors, expand into dangerous districts, restore city communications, then continue open-ended survival | D-25 |
| Retain and adapt existing combat, needs, weather, inventory, settlement, procedural art/audio and Creative systems; no engine rewrite | D-26 |
| Out of scope: whole-city simulation, driving, multiplayer, structural collapse, simulated elevators, procedural dialogue AI, citywide electricity solver (vehicles only as static salvage props) | D-27 |

## 3. Player experience

### 3.1 The first fifteen minutes

Route: **city refuge → apartment → supplies → street threat → refuge**. This is the playable-slice route that package 11 must prove on a generated neighbourhood. Times are *initial* targets.

| Time | Beat | What the player does | Systems |
| --- | --- | --- | --- |
| 0:00–1:00 | Wakes in the starter refuge: a secured ground-floor flat off a courtyard; unattended provisional shelter in 08, residents in 16 | Reads contextual room/supply hints; checks the modest kit | Urban spawn, refuge and hints **Implemented [08]**; survivor populations *[16]* |
| 1:00–2:00 | Crosses the street to the apartment block (≤ 40 blocks away) | Sees or hears a distant infected that has not noticed them | Street plan *[03]*; encounter placed outside the sanctuary radius *[09]* |
| 2:00–7:00 | Enters through the front door or a broken side door; uses the stair core; searches the kitchen, bathroom and utility closet on two floors | Each search takes time and makes noise; chooses what to carry | Doors *[04]*; stairs and rooms *[05–06]*; contextual containers *[07]*; WorldNoise (**Existing**, B-66) |
| 7:00–9:00 | Finds a locked flat with a useful item upstairs | Forces the door with the tool found downstairs (loud), or skips it | Lock and forced-entry state *[04]*; noise and perception *[09–10]* |
| 9:00–11:00 | Takes the back stair or alley to the corner shop; shelves are partly picked clean | Searches the stockroom | Shop family *[05–06]*; depletion *[07]* |
| 11:00–13:30 | Rummaging noise draws the infected | Avoids (breaks line of sight, uses the alley, closes a door) or fights | Infected AI *[09]*; indoor and outdoor pursuit *[10]*; damage gates (**Existing**, B-56) |
| 13:30–15:00 | Returns to the refuge | Stores supplies, drinks and eats, treats a wound, crafts a first upgrade from salvage, sleeps or plans the next trip | Refuge storage and bed *[08]*; urban recipes *[08]*; sleep (**Existing**, B-61, adapted *[08]*) |

The first meaningful upgrade is reachable in the starter area from urban sources only. Package 08 implements at least one of these, all *initial*:
- a pry tool (opens locked doors and containers with less time and noise than improvised force);
- a reinforced melee weapon;
- a water filter (turns dirty water clean without charcoal);
- a carry upgrade made from textiles.

### 3.2 From one trip to a multi-day loop

| Phase (in-game days, *initial*) | Player goals | Pressure | Packages |
| --- | --- | --- | --- |
| Day 1 | Learn the refuge routine (store, cook or boil, treat, sleep) and finish the starter loop | Thirst, light, first infected | 08–11 |
| Days 2–4 | Widen to adjacent superblocks and the district edge; trade with the provisional camp; switch from fresh to preserved food as spoilage bites; hear the first relay rumour (C1) | Depleting nearby supplies, noise, night | 07–12, 16 |
| Days 4–8 | Claim and fortify a better refuge; restore water and a small power circuit; earn stronghold access or find an alternative route; help hidden civilians; use basement, transit and rooftop routes into specialist districts for relay components (C2–C3) | Scarcity, district hazards, alien growth, raiders | 13–19 |
| Days 8–12+ | Repair the relay (C4) and make the final broadcast (C5); then keep surviving open-ended | Utility upkeep, renewable-only supplies, threat attraction from noise | 19–23 |

### 3.3 Communications restoration (long-term goal)

This is a design default, not approved story canon (owner question P-01). Package 20 owns the implementation. Each stage references real world objects by stable id. No stage may require an item, key or trust that is available only behind that same stage.

| Stage | Objective | World objects and alternatives |
| --- | --- | --- |
| C1 Rumour | Learn that a working relay exists | Radio log in a camp or hidden-refuge container, or a camp resident's dialogue; it adds a rumour marker, not a verified location |
| C2 Components | Recover three component kinds: transmitter module, power cells, antenna and cable | Each kind has ≥ 2 independent source sites in different districts (for example offices or a police site; warehouses or a vehicle salvage yard; civic or rooftop masts) |
| C3 Access | Gain cooperation or access | Stronghold trust, trade or aid, **or** an infiltration route through service tunnels or rooftops; hidden civilians can supply a route or code |
| C4 Repair | Restore the rooftop relay and a radio workstation at the refuge | The workstation runs on the refuge's local circuit *[19]*; repairs use salvaged parts and tools |
| C5 Broadcast | Make the final communications action | A completion event with the option to continue. Generator and transmitter noise are real WorldNoise events, so threats respond through genuine sensing |

After C5, completion reuses the existing non-terminal victory flow (**Existing**, B-07: Enter continues play) with urban text, unless the owner decides otherwise (P-07). Survival then continues on renewable sources: rain collection, water treatment, gardening if supported, trade with finite restock, and bounded migration. Abandoned containers never refill.

## 4. City structure

### 4.1 Vertical datum (D-05)

The chunk is 16 × 96 × 16, so y runs 0–95 (**Existing**, B-10). No bedrock block exists, and generated y ≤ 1 is stone (B-14). `WaterSystem` spreads water sideways only at y ≤ 30 (B-58), and temperature cools 0.28 °C per block above y 40 (B-59). The urban datum keeps every planned void at y ≥ 33 and puts the street at the start of the altitude-cooling band.

| Band | y | Use |
| --- | --- | --- |
| Solid fill | 0–31 | Concrete, stone or soil fill. y 0–1 is always solid and nothing may be carved there. No caves or ore. |
| Lower service / B2 / transit | slab 32, clear 33–35 | Second basement level, utility corridors, transit platforms and tunnels (under streets) |
| B1 basement | slab 36, clear 37–39 | First basement level, cellars, secret-refuge rooms |
| Street datum | pavement and ground-floor slab at 40; feet at 41 | Streets, sidewalks and flush building entrances (`STREET_Y = 40`) |
| Storeys | slab of storey *n* at 40 + 4*n*; clear 41 + 4*n* … 43 + 4*n* | Pitch 4 = slab + 3 clear cells, matching the existing keep precedent (B-16) and the pathfinder's step headroom (B-65) |
| Roof | slab at 40 + 4*N* for *N* storeys; at most *N* = 12 (roof slab 88) | Roof access, bulkheads and parapets up to y 93; y 94–95 stay empty |

Initial building families stay at ≤ 5 storeys (apartments) and ≤ 8 (offices) until package 24 has measured dense-city cost. Flooded districts place their standing water deliberately *[13]*. Their overlays must be shown to be stable under the existing `WaterSystem` rules.

### 4.2 Planning hierarchy (D-06)

| Level | Size (*initial*) | Identity | Notes |
| --- | --- | --- | --- |
| Planning region | 16 × 16 chunks = 256 × 256 blocks | `(rx, rz)` | One district identity per region; pure function of (seed, revision, rx, rz) |
| Superblock | 64 × 64 blocks, a 4 × 4 grid per region | region + index | Bounded by streets |
| Parcel | 12–32 blocks per side; 2–6 per superblock | superblock + index | Building, courtyard, park or service reservation |
| Building | inside the parcel minus setbacks; may cross chunk borders | parcel + slot | Template family plus rotation plus variant |
| Floor | B2, B1, ground, storeys 1–11 | building + floor index | Uses the datum in §4.1 |
| Room | ≤ 32 per floor | floor + index | Has a function (§5.3) |
| Fixture / container | ≤ 16 per room | room + slot | Doors, windows, containers, utility points, quest reservations |

Streets (*initial*):
- **Avenues** (12 wide: 3 sidewalk, 6 roadway, 3 sidewalk) run along planning-region edges. Each region stamps its half, from a pure function of (seed, edge coordinate), so neighbouring regions agree without generating each other.
- **Local streets** (8 wide: 2 + 4 + 2) run along superblock lines.
- **Alleys** (3 wide) may run inside superblocks.

Residential superblocks keep an inner courtyard of at least 8 × 8. Parks take at most one superblock per region. Canals appear only in flooded districts *[13]*.

**Implemented in package 03** (generator 5, DECISIONS D-39 to D-47): the lattice above with local streets jittered ±5 blocks, so superblocks are 46–66 blocks a side; courtyard housing reached through passages, shop rows along service alleys, parks and plazas; reserved parcels with entrances, stair cores and storey envelopes; the starter refuge and spawn anchor in region (0, 0). Parcels are flat reserved lots: no building exists until packages 05 and 06.

### 4.3 Six district identities (D-07)

Each identity must change decisions, not just colour. Package 03 defines all six district ids and implements reservations for the first two. Package 13 implements full rules. The threat budget is the number of encounter anchors per planning region (*initial*). The active hostile cap is separate (§10).

| District | Architecture and streets | Supply bias | Hazards and exposure | Threat budget | Reason to go |
| --- | --- | --- | --- | --- | --- |
| Reclaimed residential (starter) | 2–5-storey walk-ups and row houses, courtyards, corner shops, narrow streets, overgrowth | Food, water containers, clothing, basic treatment, household tools | Low; dark interiors | 2–6 small groups | Basics, camps, hidden basements |
| Commercial downtown | 4–8-storey offices, shopfronts, plazas, wide avenues with long sightlines | Electronics and radio parts, packaged food in shops, pharmacy goods | High exposure on avenues; long climbs; glass | 6–12 | Relay components (C2), trade goods |
| Industrial yards | Warehouses, workshops, depots, fenced yards, service roads | Tools, repair parts, fuel, batteries, scrap metal, generator parts | Chemical spills, fire and explosive drums, dark large halls, open yards | 4–10 | Utilities *[19]*, repairs, the hostile site *[17]* |
| Flooded neighbourhoods | Low-lying blocks, standing water at street level, canals, raised walkways and bridges | Water-treatment parts; less-looted upper floors | Wetness and cold, slow wading, contaminated water, hidden drops | 2–6 | Upper-floor caches, pump parts |
| Civic and medical precinct | Clinic, school, police or security site, municipal offices, squares with fences and checkpoints | Treatment kits, medicine, secured ammunition and weapons, keys and intel | Guarded areas, secured doors | 4–10 | Medicine, the stronghold *[17]*, relay access (C3) |
| Quarantine and alien growth | Cordoned blocks behind barriers and checkpoints, decontamination posts, growth on facades | Rare medicine and technology, research samples | Contamination, growth nodes, darkness | 6–12 (alien + infected) | Late rare rewards; never required for the first meal, water or treatment |

District climate keeps the existing `Biome`, which has many runtime consumers (B-76; DECISIONS D-08). The district identity is a separate urban layer. Biome ordinals and meanings do not change.

### 4.4 Bounded starter area (D-09)

The spawn planning region `(0, 0)` is always reclaimed residential. Its eight neighbours are residential or commercial, never quarantine or flooded. The starter area is everything within **96 blocks of walking path** of the refuge door. Package 08 guarantees it through pure starter constraints with a finite fallback, never by repeated whole-world generation or debug gifts (*initial* values):
- refuge: bed, storage container, a heat or cooking source that accepts salvaged fuel, and a water container or rain collector;
- ≥ 2 apartment buildings, at least one with ≥ 3 storeys and a basement;
- ≥ 1 corner shop;
- a connected street network plus at least one alley alternative;
- food ≥ 6 servings across ≥ 3 containers in ≥ 2 buildings;
- drinkable water ≥ 3 units plus one renewable method;
- treatment ≥ 2 bandage-class plus ≥ 1 antiseptic-class item;
- ≥ 1 pry-class tool reachable without forced entry;
- materials for ≥ 1 first-upgrade recipe;
- 1 infected encounter of 1–2 bodies, ≥ 24 blocks from the refuge door and not visible from the spawn point *[09]*.

**Implemented in 08 (D-75–D-79):** generator 9 / layout 6 / template 4 reserves
the origin starter superblock on the frozen street lattice: one refuge, one shop
and four three-storey apartments with B1 access. Existing validated furnishing
lanes provide unlocked ground-floor supplies; the building repair limit remains
three, with no whole-city retry. Content 3 adds finite starter reservations to
ordinary searched containers, without altering content 1/2 rolls. Six fixed
seeds prove the apartment/shop route within 96 walking blocks of the courtyard
refuge door, food distribution, treatment, water, sleep and a reinforced spear.
The five-item kit can be spent before this route. The refuge has no NPCs or
invulnerability area; doors, walls, beds, storage and stations remain editable
and destructible. Package 09 adds encounters through generator 10 with the same
geometry and loot, keeping generator 9 encounter-free (D-83–D-85).

The courtyard collector begins with 3 litres and refills through existing rain
simulation; its fire begins with 240 seconds of fuel. Recovered planks fuel fire
and lanterns. Fifteen urban repair/reuse recipes cover containers, cloth
treatment, light, sleep, stations and tool/weapon repair without ore or logs.
Antiseptic is scavenged, not crafted from mandatory wilderness herbs. Residential
starter climate uses MEADOW only in generator 9; full district climates remain 13.

## 5. Building contract

### 5.1 Accessibility rules (D-10)

These are validated by production code *[05]* against real collider and movement semantics, not by abstract graph edges alone.

| Id | Rule |
| --- | --- |
| A1 | Every generated building has ≥ 1 usable entrance from walkable street, alley or courtyard, or a discoverable alternate entry (ground-floor window, fire escape, basement hatch) usable with equipment obtainable before it is needed. |
| A2 | Every generated playable room, container and quest reservation is reachable from an entrance by a route the player collider (0.6 × 1.8, auto-step 1.0) can walk. Where residents or hostiles must reach it, the route must also be followable by `Pathfinder` (B-21, B-65). |
| A3 | Vertical access between all floors uses switchback stairs with landings and ≥ 3 clear cells above each integer departure cell. Frozen templates 1/2 use full-block steps; template 3 uses two half-height treads per directional cell, with matching collision and reversible swept-clearance proof (owner's native-QA refinement, 2026-10-11; D-69–D-70). Ladders are secondary or alternate routes only (fire escapes, hatches). A ladder-only building is invalid. |
| A4 | Every reachable room has a route back to an exit. No playable room may require a one-way drop deeper than 3 blocks (B-65 `MAX_DROP`). |
| A5 | Locked routes need a key or tool obtainable outside the locked area, or an alternate entry. A key is never only behind its own lock. Abandoned property can always be forced with a basic tool, at a noise cost. |
| A6 | Ruin variants may remove rooms or floors. Removed rooms carry no containers or quest reservations, and the remaining playable rooms stay connected. |
| A7 | Windows match floors: window rows sit inside each storey's clear band, never into slabs or solid mass. Facades and entrances match the floor plan. |
| A8 | A structure with walls and a roof is either a building with interiors or explicitly non-enterable small infrastructure (kiosk ≤ 2 × 2, bus shelter, utility box). Package 05 lists that infrastructure set. |
| A9 | Damage, overgrowth, flooding and district overlays never block a validated route. Overlay code re-validates or suppresses the overlay. |
| A10 | An invalid plan is repaired by a deterministic finite fallback (alternate template, then a minimal valid template), never by unbounded retries or by accepting a sealed shell. |
| A11 | Doorways are ≥ 1 wide and 2 high. Main corridors and stair cores are ≥ 2 wide wherever residents must pass each other. |
| A12 | Buildings straddling chunk or region borders are planned whole and stamped as slices. Creation order never changes a wall, fixture or id. |

### 5.2 Room functions and variants (D-11)

Room functions (*initial*): entry/hall, kitchen, living room, bedroom, bathroom, storage closet, utility room, corridor or stair core, shop floor, stockroom, basement storage, basement utility. Packages 12 and 14 add clinic, classroom, office, security, warehouse, platform and service rooms.

Variety comes from constraints, rotation, damage and occupation states (intact, damaged, picked clean, occupied) as well as templates. Every variant keeps the rules in §5.1.

**Implemented in 05:** `BuildingPlan` contains immutable nested `Floor`, `Room`,
`Fixture` and `Stair` records plus the exact proposed voxel volume. Template
revision 1 is selected by generator 6, with one flat per floor and combined
functions on shallow parcels (D-58–D-61). It retains the reserved entrances/core
and validates both entry and exit routes before returning a plan. The twelve
functions above exist; there is no generated building geometry or rolled loot
yet under generator 6. [BUILDING_PLANS.md](BUILDING_PLANS.md) records real floor diagrams and diagnostics.

**Implemented in 06:** generator 7 selects furnished template 2, stamps all parcel
families as whole-plan chunk slices, and preserves the earlier generations.
Apartment/service/shop rooms have modest functional furnishings and contextual
search reservations without rolled contents. Damage/clutter/growth are part of
the validated proposal. Live room/floor, sky/cover, shelter and climate queries
avoid roof-as-ground and apartment-as-cave classifications. Generated physical,
state and day/night appearance evidence is recorded separately from the required
human walk in [PROGRESS.md](PROGRESS.md) and [VALIDATION.md](VALIDATION.md).
The 2026-10-11 native-QA continuation adds generator 8 / template 3 for shaped
stairs, preserving generator 7 and every room/fixture identity. The owner
explicitly confirmed the complete normal-input Survival exploration route passed.

## 6. Occupied sites and the player's refuge (D-21, D-22)

| Site | Role in the loop | Spatial basis | Population (*initial*) | Interaction and consequence | Packages |
| --- | --- | --- | --- | --- | --- |
| Player refuge | Home: storage, bed, crafting, utilities, respawn anchor | Starter ground-floor flat in 08; broader claims in 18 | Player; unattended provisional shelter in 08 | Store, rest and repair in 08; claims/fortification/utilities later; threats arrive through genuine noise and exposure, not scheduled raids | 08, 18, 19 |
| Survivor camp | Trade, aid, rumours, missions | Courtyard, rooftop or reclaimed ground floor | 3–6 | Trade, aid delivery, missions; trust against theft and violence | 16 |
| Hidden basement civilians | Discovery and aid; source of intel and codes | Planned secret basement rooms with a discoverable entrance and clues | 2–5 | Found through clues and real interaction; deliver aid or escort to a known camp; deaths are permanent | 14 (reservations), 16 |
| Guarded stronghold | Protected access, trade hub, holder of a relay component | School courtyard or municipal block with barricades, gates, posts, barracks and clinic | 8–16, ≥ 4 guards | Trusted admission, trade, restricted access or intrusion; theft attributed to the stored owner; guards perceive through real senses | 17 |
| Hostile survivor site | Risk and reward; bounded raids | Fortified warehouse or yard | 4–8 | Avoid, infiltrate or fight; finite supplies; an attainable alternative route | 17 |

Existing settlement machinery is reused rather than replaced (**Existing**, B-34–B-40): stable string ids, activation and dormancy budgets, reputation, theft dedupe, counterattacks with exactly-once outcomes, and captive rescue. Urban sites must not use the legacy terrain planner. Their layout must depend only on (seed, plan), because legacy village layouts read persisted resident counts (B-36). Residents need stable slot ids, not list indexes (B-35).

## 7. Threats (D-20)

**Original direction (names are provisional, P-01).** The city is an abandoned colony city on Veylon.
- **Infected** are former residents altered by quarantine-zone alien biology. They hear first: they investigate real noise, see poorly in darkness and gather indoors by day.
- **Alien organisms** are that biology's own life: territorial growth nodes and an ambush organism that reacts to light and noise near the nodes.

Guardrails, to keep both original:
- no fungal growths on hosts;
- no echolocation "clicker" analogue;
- no certain-death bite premise;
- no reference-game names, factions, text, art or sound.

| Theme | Senses and behaviour | Counters | Placement | Package |
| --- | --- | --- | --- | --- |
| Baseline infected | Hears WorldNoise; occluded line-of-sight sight, short range in darkness; investigates last-known positions; groups ≤ 4 | Quiet movement, darkness, doors and routes, melee or ranged weapons, fire | Street and room anchors outside the sanctuary radius | 09, 10 |
| One infected variant (optional) | Must change player decisions (for example a door-breaker or an ambusher), not just add health | Specific counter | Chosen districts | 15 |
| Alien organism | Reacts to light and noise near growth nodes; clear telegraph | Darkness and stealth, fire on nodes, luring | Quarantine district only | 15 |
| Urban wildlife | Existing species behaviour, adapted | Existing | Parks, roofs, canals | 15 |
| Hostile survivors | Settled-NPC perception and pathing (**Existing**, B-67, B-65) | Avoid, infiltrate, fight | Hostile site and patrols | 17 |
| Environmental | Flooding, contamination, darkness, fire, spills | Gear, route choice | Districts | 13 |

New hostiles must not copy the current creature pattern. Creature perception is 3D distance without line of sight, and creatures never pathfind (B-64). They use occluded sight, WorldNoise hearing, `Pathfinder` with portals, and the existing damage and Creative gates (B-56).

**Implemented in 09 — Veilbound (D-81–D-85).** Alien membrane tissue changes
former colonists into hearing-led, unarmed humanoids. A pale tilted head,
forward torso, braced shoulder and muted clothing carry the silhouette. Health
28, pursuit 1.65 blocks/s; loaded solid voxels occlude sight, whose full-light
range is 18, reduced in darkness and while the player crouches. Hearing follows
the actual WorldNoise radius/location, with no continuous hidden-player tracking.
Idle bodies hold their anchor; they investigate sound for eight seconds or
last sight for ten. The 1.55-block strike winds up for 0.65 s and recovers for
1.8 s (minimum 2.45 s between impacts), rechecking range, walls and mode.
Physical damage 6 uses existing bleeding/treatment. Pain interrupts for 0.35 s;
body fire/panic takes priority. Four procedural positioned cues signal notice,
strike, pain and death. No new infection disease, kill drops or meat/hide.
Shared HUMANOID hit zones, ragdolls, fragments and non-harvestable corpses apply.

Two stable ground-floor slots per selected residential building use street
and room anchors. Admission checks actual loaded support/clearance, sanctuary,
player breathing room, occupied property, overlap and view. Global cap 12;
two spawns, 16 parcels and 16 stored snapshots per slow tick. Dormant bodies
pause without healing; health-zero rows never reactivate. At 32,768 visited
records new encounters stop, with no death eviction or migration. Same-floor
pursuit has two bounded 400-expansion A* searches per fast tick; doors and unsafe
strides stop it. **Portal routing, multi-floor pursuit and sound attenuation
remain 10.** These guards do not protect an exposed refuge from an existing body.

## 8. Scavenging economy: scarcity and depletion (D-12, D-13)

| Id | Rule |
| --- | --- |
| S1 | A container's contents are rolled at most once, from (seed, content revision, container id, loot salt), on first search or consequential hand salvage. Search results, emptying and destruction persist from that moment. An absent row in a valid ledger means untouched; a missing required content-2 section rejects the save. Old content-1 core inventories/edits are adopted without refilling. |
| S2 | The room function selects the table, the district multiplies the bias, and site ownership decides theft. Abandoned property is never theft. |
| S3 | Necessities (food, water, treatment, light, a pry tool) have ≥ 2 independent source types in the starter area and in every district. Specialist goods concentrate in specialist destinations and cost access or encounter risk, never an unobtainable key. |
| S4 | Searching costs time (1.5–4 s per container, *initial*) and emits rummage noise. Forced entry is louder. The world keeps simulating during search and while the container screen is open (**Existing**: the crate screen does not pause, B-45). |
| S5 | Scarcity targets (*initial*, tuned in 23): about 1–2 food servings and 0.5–1 water units per apartment unit; treatment in about half of bathrooms. About 30 % of starter-district buildings are picked clean to half contents or less. |
| S6 | Renewables are bounded: rain collection, water treatment, gardening if supported, trade with finite restock, migration. There are no periodic hidden refills or infinite vendors. |
| S7 | Found food spoils under the existing model (B-47); packaged food spoils slowly. Spoilage cost must not scale with every container in the explored city. |
| S8 | Carrying uses the existing 36-slot inventory and 28 kg capacity plus gear (B-46). Urban containers hold ≤ 12 slots so the existing transfer UI applies (B-45). |
| S9 | Contents always have exactly one owner. Destroying a container by any route (hand, blast, fire, salvage) goes through one removal path. Nothing is silently lost: legacy crates currently orphan contents on blast or fire, and a full inventory destroys drops (B-43, B-44). |

**Implemented in 07 (D-71–D-74).** Nine room/furniture functions select twelve
tables and six district biases, with existing food, water, treatment, clothing,
tools and repair items. Basic supplies have multiple generated room sources;
scarce firearms/ammunition are confined to occupied secured utility/stockroom
storage. Picked-clean sites stay empty. Specialist clinic/warehouse roles work
through current room/district mappings; the actual building catalogue and full
district gameplay remain 12/13. The initial tables do not establish starter
guarantees (08) or final scarcity (23).

Lazy records are bounded to 32,768 including MOVED/DESTROYED tombstones. There is
no tombstone eviction or reroll on replacement. Only `World.crateContents` owns
inventory bytes; content-2 empty records and untouched furniture allocate no
Inventory. Player replacements start empty under fresh negative ids. Search
progress persists across close/load; the open UI target and rummage timer reset.
Normal simulation advances searches, needs and threats. Actual partial transfers,
full destinations, hand salvage, spoilage, blast/fire, owner consequences and
save corruption are exercised through production paths. Survivor taking changes
Free Settlers reputation using stable building ownership, with logical-event
deduplication; abandoned/player storage bypasses legacy camp-proximity theft.
Urban policing remains 16/17. Hand salvage refuses insufficient capacity; blast
and fire intentionally destroy contents. All three retire the original identity
before replacement can create empty player storage.

## 9. Legacy mechanics disposition (D-26)

"Urban-gated" means the urban generator selects a new behaviour while legacy worlds stay byte-identical.

| Disposition | Mechanics |
| --- | --- |
| **Retain** | Combat: melee, bows, firearms, thrown weapons, projectiles, hit zones, explosions. Anatomy, ragdolls, fragments, combustion, burn residue. Needs, afflictions and treatment. Weather, seasons and time. Fire and liquid fire. Inventory and stack codec. Save envelope and atomic publication. Creative abilities, catalog, world controls and mode mark. WorldNoise and perception gates. Settlement record, budgets, dormancy, reputation, theft dedupe, counterattack director. Quest identity and dedupe. Procedural textures, sounds and EFX fallback. Geometric `ShelterSystem`. Column-top precipitation, `exposedToSky` and rooftop rain collection. Quick save and load in the single slot. |
| **Adapt (urban-gated)** | Spawn and starter refuge; starting kit and onboarding text. Crafting: urban recipe set and salvage inputs, with legacy `RECIPES` unchanged. Fuels: campfire and lantern accept salvaged fuel. Containers: fixtures with stable ids, lazy rolls, tombstones and owners. Doors, from the gate pattern. `Pathfinder` portals, a shared budget and door permissions. Urban site planner instead of tiers. Occupation cost without logs. Counterattacks become refuge raids and stronghold retaliation. Captive rescue becomes civilian aid. Quests become urban missions with a bounded journal. Victory flow becomes comms completion. Temperature, audio and lighting get an indoor model in place of heightmap depth. Map plan layer. Urban footstep materials. Respawn at the refuge. Contaminated open water. Sleep safety by threat type and walls. Toxic fog becomes a quarantine hazard. Creature spawns from anchors. |
| **Disable in urban worlds** | Heightmap terrain and mountains. Natural caves, cave identities, fumaroles and Gloomstalker zones. Ore veins. Random trees and plants outside planned greenery. Lakes outside planned water. Surface and underground POIs and cave-entrance shafts. Legacy settlement planner and builder tiers. The crash camp (`setupCamp`), the single-camp faction track, camp upgrades and camp quests. Surface wildlife spawning and surface events (raiders, traders, wolves, meteors) until anchor-based replacements exist. The beacon chain, beacon prompts, music and map marker. The "gather wood" onboarding. |
| **Optional alternative (never required)** | Felling park trees for wood; hunting and carcass harvest of urban wildlife; foraging in overgrowth; gardening; legacy tool tiers and stations when their materials are salvaged; drying racks and tannery. |

## 10. Initial implementation quantities (D-14)

These are bounded starting scope, not a wish list. Packages may change a number only by recording the reason in DECISIONS.md.

| Package | Quantity |
| --- | --- |
| 03 | Region plan cache ≤ 16 regions (LRU); 6 district ids defined; residential and commercial reservations implemented |
| 04 | ≤ 24 new block ids (budget in DECISIONS §3); ≤ 6 container visual forms; doors, windows and stairs from standard blocks plus a few dedicated ids |
| 05 | Implemented revision 1: compact/standard/court apartment layouts adapting about 12 × 16, 16 × 20, 20 × 24 to reserved parcels, up to 5 storeys, optional B1, one flat per floor (D-59). Shop: 1 storey plus optional flat, sales floor, stockroom/utility, back door. 12 room functions; shallow parcels combine functions. |
| 06 | ≤ 6 fixtures per room, of which ≤ 3 containers; 3–6 containers per apartment unit; decorative props are blocks, not entities |
| 07 | Implemented: 9 container functions (kitchen cabinet, fridge, pantry shelf, bathroom cabinet, wardrobe, dresser or desk, utility locker, shop shelf, stockroom crate); 12 contextual tables (D-71 replaces the approximate 14); 6 district bias rows; ≤ 12 slots and ≤ 4 initial occupied slots per container; ≤ 32,768 touched/retired identities |
| 08 | Starter guarantees in §4.4; ≤ 20 new items; ≤ 15 urban recipes |
| 09 | 1 infected type; active hostile cap ≤ 12 globally and ≤ 4 per encounter; sanctuary radius 24 around the refuge; no spawn within 16 blocks or in view; activation and deactivation at 96 and 130 blocks, following the settlement precedent (B-37) |
| 10 | Shared path allowance ≤ 2,000 expansions per fast tick; route cache ≤ 64; portals derived from plans |
| 11 | One generated starter neighbourhood; 5 frozen regression seeds; a capture list |
| 12 | 6 archetypes: grocery or convenience store, clinic or pharmacy, school, office block, police or security site, warehouse or workshop |
| 13 | All 6 districts. For design seeds, residential and commercial within 1 region of spawn and all six within 3 regions (Chebyshev) |
| 14 | ≤ 1 transit station and ≤ 2 tunnel segments per region; basements in ≥ 50 % of residential buildings; ≥ 1 hidden-refuge reservation per 2 residential regions |
| 15 | 1 alien organism with growth nodes; ≤ 1 infected variant; ≤ 3 adapted wildlife species |
| 16 | Camps of 3–6 residents; hidden groups of 2–5; for design seeds, ≥ 1 of each within 2 regions of spawn |
| 17 | 1 friendly stronghold (8–16 residents, ≥ 4 guards) and 1 hostile site (4–8), each within 3 regions of spawn on design seeds |
| 18 | ≤ 3 claimed refuges; ≤ 3 barricade kinds; a salvage table for every urban furniture kind |
| 19 | 1 water utility and 1 power source (generator or battery bank); ≤ 4 consumer kinds (light, purifier or pump, radio, optional fridge); circuit scope of one building |
| 20 | Comms chain C1–C5; 5 missions (clinic supplies, hidden-civilian aid, camp utility, district landmark, stronghold-held component); active journal ≤ 4 |

## 11. Seed matrix and acceptance criteria (D-15)

The candidate design seeds are the legacy fingerprint seeds `20260731`, `-4242` and `7`, plus `0`, `987654321` and `-9000000000000000000`. Package 03 freezes the urban set in [VALIDATION.md §6](VALIDATION.md#6-seed-matrix-registry); package 11 freezes the slice regression set. Every matrix test is deterministic, headless and bounded: ≤ 15 × 15 chunks per seed unless a package records why it needs more.

| Axis | Values |
| --- | --- |
| Region coordinates | `(0,0)`, `(-1,-1)`, `(1,-1)`, `(-1,1)`, `(2,3)`, `(-3,-2)`, and a far pair at about ±4096 regions (overflow and negative rounding) |
| Chunk access orders | Row-major, reverse row-major, column-major, three fixed-seed shuffles, outward spiral from spawn |
| Templates | Every family × 4 rotations × {intact, damaged, picked clean, occupied} × ≥ 2 seeds, including buildings straddling 2 and 4 chunks |

| Criterion | Pass condition | First package |
| --- | --- | --- |
| Determinism | Identical block bytes, height maps, plan ids, fixture ids and container ids across every access order | 03 |
| Seams | Roads, sidewalks, canals and elevations join at every region and chunk border | 03 |
| Accessibility | Zero unreachable playable rooms, containers or quest reservations; zero stair-clearance or overflow violations; bounded validator work | 05 |
| Starter | §4.4 guarantees hold for every starter seed. A failure is a defect, not tuning. | 08 |
| Slice | The headless production route completes the §3.1 loop on the frozen seeds | 11 |
| Districts | §10 package-13 distribution and reachability hold on design seeds | 13 |
| Legacy | Generator 1–3 fingerprints and historical fixtures unchanged | every package |

## 12. Interface map (D-16)

Paths are relative to `src/main/java/com/veylon/`. Proposed objects are modest domain classes, not a framework. Pure plans live under `world` (proposed sub-package `world.urban`), because `world` and `item` are leaf domains and `settlement`, `entity` and `simulation` may depend on them (`docs/ARCHITECTURE.md` §1).

| Concern | Proposed object *[package]* | Existing owner or hook (verified) | Persistence |
| --- | --- | --- | --- |
| Generator identity and dispatch | `World.GEN_URBAN` (next unused, 4); explicit version predicates; `DEFAULT_NEW_WORLD_GENERATOR` and `MAX_SUPPORTED_GENERATOR` *[02]* | `world/World.java` constants and gates; `WorldGenerator.generate`; `SaveSystem.loadPayload` version check; `Game.newWorld` overloads; `WorldBootstrap.newWorld(seed, fresh, version, mode)` (B-02, B-12, B-13, B-26) | Generator version in the v3 base |
| Content revision | `UrbanRevision` *[02]* | Section envelope in `save/V3ExtensionSections.java` (B-28) | `urban.meta` v1 (proposed id) |
| Stable ids | `UrbanIds`: pack, format and validate (§13) *[02]* | Precedents: `Settlement.packId`, `FactionSystem.settlementStorageId` (B-35) | Inside urban sections |
| District, street and parcel plans | `CityPlanner` producing `DistrictPlan`, `StreetNetwork` and `ParcelPlan` (pure, region-keyed, LRU-bounded) *[03]* | Pattern: `SettlementPlanner.plan` purity and symmetry; `World.settlementForRegion` cache (B-16) | Derived |
| Ground, floor and roof queries | `World` urban queries: street level, floor and room at a position, loaded column *[03, 06]* | `World.surfaceHeight` stays legacy and generates chunks as a side effect; there is no loaded-column API (B-18) | Derived |
| Building, floor and room plans | `BuildingPlan`, `FloorPlan`, `RoomPlan`, `FixtureSlot`; a `BuildingTemplates` catalog keyed by revision *[05]* | Precedent: `settlement/SettlementBuilder.Layout` | Derived, frozen per revision |
| Accessibility | `AccessibilityValidator` *[05]* | `ai/Pathfinder.standable` and `passable`; `entity/PlayerConstants` colliders (B-21, B-65) | Diagnostics only |
| Generation slices | `UrbanChunkStamper`: chunk-bucketed layout, `Chunk.set` inside the chunk only, bounded per chunk *[03, 06]* | `WorldGenerator.applySettlementSlice` pattern; `World.getOrCreateChunk` finalisation (B-15, B-16) | Derived |
| Doors, windows, locks, barricades | Appended `BlockType` ids (`world.Fixtures` families) plus the `World.fixtures` side map (`FixtureStates`), keyed by block position (D-48) *[04]* | GATE/GATE_OPEN swap and `World.gateTimers`; `Pathfinder.passable`; `WorldInteractions`; `InteractPromptBuilder` (B-39) | `world.fixtures` (D-49; proposed as `urban.fixtures`) |
| Containers | `UrbanContainers` identity/state/owner ledger plus pure `LootTables` (implemented 07) | `World.crateContents` owns inventory bytes; `CrateTransactionSystem`; `ui/CrateScreen`; `SettlementManager.addReputation` for managed survivor property, legacy theft paths retained | `urban.containers` v1 plus existing core crate map |
| Container removal | One removal path for hand, blast, fire and salvage *[07]* | `PlayerBlockActions.spillContainerContents`; `combat/ExplosionSystem`; `simulation/FireSystem` (B-43) | Tombstones |
| Spawn, refuge and respawn | `UrbanBootstrap`, called from `WorldBootstrap` (skeleton 02, full 08) | `WorldBootstrap.placePlayerOnDryLand`, `setupCamp`; `Game.respawn` (B-03, B-08, B-41) | `urban.refuges` |
| Urban progression | `CraftingSystem.recipesFor(world)` plus appended `ItemType`s *[08]* | `item/CraftingSystem.RECIPES` unchanged (B-49 to B-51) | Existing stack codec |
| Population activation | `UrbanEncounterDirector`: anchors, caps, dormancy *[09]* | `entity/EntityManager.slowTick`; `SettlementManager` activate and deactivate; `qa/RuntimeBudgetSnapshot` (B-37, B-63, B-70) | `urban.population` |
| Hostile body and brain | Infected on the HUMANOID body, hit zones and humanoid model, with a new brain (D-20) *[09]* | `entity/BodyFamily`; `combat/HitZone`; `gfx/model/NpcModels`; `Player.hurtPhysical`; `isPerceivableByAi` (B-56, B-62, B-69) | Ordinals append-only |
| Navigation portals | `PortalGraph` derived from plans plus a shared `PathBudget` *[10]* | `ai/Pathfinder.find`; `ai/Steering`; `SettledNpcAI.moveTo` (B-64, B-65) | Transient |
| Perception | Occluded sight and attenuated WorldNoise *[10]* | `SettledNpcAI` line of sight; `combat/WorldNoise` (B-66, B-67) | Transient |
| Indoor state | `IndoorState` from room plans feeding temperature, audio and light *[06, 22]* | `TemperatureSystem`; `AudioSceneState`; `AmbienceSystem`; `World.skyLight`; `ShelterSystem` (B-57, B-59, B-60, B-20) | Transient |
| Districts | `DistrictRules` *[13]* | `world/Biome` climate kept; `TemperatureSystem`; `WeatherSystem` | Derived; consequential hazards persist |
| Underground and roofs | Basement and transit planners; hidden entrances *[14]* | (new) | `world.fixtures`, `urban.discovery` |
| Sites | Urban site registry reusing `Settlement`, `SettlementManager` and `DormantSettlementSimulation` *[16, 17]* | `settlement/*` (B-34 to B-40) | `urban.sites` or a settlement-codec extension (D-21) |
| Refuge and fortification | `RefugeSystem`: claims, barricades *[18]* | `SleepSystem`; `PlayerBlockActions`; `World.setBlock` (B-19) | `urban.refuges`, `world.fixtures` |
| Utilities | `UtilitySystem` (a `MediumTickSystem`) *[19]* | `WaterSystem`; collectors in `ItemConditionSystem`; `Chunk.lights` (B-20, B-58) | `urban.utilities` |
| Missions and objective | `UrbanMissions` (bounded journal) plus `CommsObjective` *[20]* | `ai/Quest` and `FactionSystem` identity and dedupe; `ui/QuestObjectiveView`; `AppState.VICTORY` (B-07, B-54) | `urban.missions` |
| Discovery and map | `UrbanDiscovery` plus a plan layer in the map *[14, 21]* | `ui/MapScreen`; `Poi.discovered` (B-71) | `urban.discovery` |
| QA | `UrbanQaScenes` in its own class *[03 onward]* | `QaHarness` (1,488 of 1,500 lines); `AutomatedRunDriver` (B-09, B-75) | — |

Wiring rule: `Game.java` is at 990 of its 1,000-line budget (B-09). Urban systems enter through one collaborator (proposed `UrbanSystems`), with at most one delegate line per tick bucket and one reset and reseed call each.

## 13. Data inventory and state owners (D-17)

The table is in dependency order: an item may depend only on items above it. Section ids are proposals; the owning package fixes the final id and version in DECISIONS.md. Every persistent item gets its codec, absence default, reset hook and corruption tests **in the package that introduces it**. Package 25 audits; it is never the first serialization pass.

| # | State | Kind | Owner | Package | Depends on | Persistence; absence default | Reset (new / load / dormancy) | Bound |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Generator version `GEN_URBAN` | Persistent | `World` | 02 | — | v3 base int; values above `MAX_SUPPORTED_GENERATOR` rejected | Fixed per `World` | — |
| 2 | Urban content revision | Persistent | `UrbanRevision` | 02, 07–08 | 1 | `urban.meta`; absent means 1 for older layouts; generators 9–10 require content 3 | New and load | — |
| 3 | Region, district, street and parcel plans | Derived | `CityPlanner` | 03 | 1, 2 | Not saved | Cache per `World` | ≤ 16 regions |
| 4 | Building, floor, room and fixture plans | Derived | `BuildingPlanner` / `BuildingPlan` | 05–06, 08 | 3 | Generator 6 selects template 1 / layout 3; 7 furnished template 2 / layout 4; 8 shaped template 3 / layout 5; 9 starter template 4 / layout 6; 10 same geometry plus encounter catalogue 1 / layout 7. Unchanged `urban.meta` v1 validates the layout; plans not serialized | Cache per `World`, discarded on new/load | ≤ 256 buildings; ≤ 73,984 cells/plan |
| 5 | Generated blocks plus edits | Derived plus deltas | Generator and `World` | 03, 06 | 3, 4 | `changedBlocks` (v3 base, last-write log, B-29) | — | 4,000,000 edits |
| 6 | Fixture state: door or window open, keyed lock or one-sided bar (broken is a recorded block edit); barricade health later | Persistent, touched cells only | `FixtureStates` on `World` | 04, 06 | 4 | `world.fixtures` v1 (D-49); absent follows generated lock defaults, or unlocked for edited/player-placed fixtures; explicit saved unlock overrides the plan | Load replaces; reapplied after structure and recorded edits on chunk generation | ≤ 32,768 cells on write and read |
| 7 | Container identity, state and owner; authoritative contents | Persistent, touched only | `UrbanContainers` metadata; `World.crateContents` inventory | 07–08 | 2, 4 | `urban.containers` v1; required for content ≥ 2; content-1 absence adopts core contents/empties and edited reservations | Load replaces; never re-rolled | ≤ 32,768 rows/core inventories; 12 slots; 20-byte header + 36 bytes/row metadata |
| 8 | Search progress; open target and rummage timer | Progress persistent; target/timer transient | Ledger; `CrateTransactionSystem` | 07 | 7 | Progress in `urban.containers`; load closes UI but retains elapsed work (D-72 revises the initial transient proposal) | Load replaces progress; resets target/timer | One active search |
| 9 | Starter refuge identity and ground-floor respawn anchor; claims later | Persistent | `UrbanWorldState` / `UrbanBootstrap`; `RefugeSystem` later | 08, 18 | 4 | `urban.refuges` v1, required for generators 9–10; absent on older worlds means no claim | New sets starter once; load replaces without replenishing | One starter anchor; ≤ 3 claims in 18 |
| 10 | Encounter identities, durable injuries/positions and permanent deaths | Persistent | `EntityManager.encounters` / `UrbanEncounterDirector` | 09, 15 | 4 | `urban.population` v1/catalogue 1; required for generator 10, absent on earlier worlds means no encounters | New clears; load replaces; dormancy pauses; no migration or replacement generation | 32,768 rows, 47 bytes/row + 12-byte header |
| 11 | Live hostile brain, pain, fire, panic and route intent | Transient | `Npc` / `InfectedAI` | 09 | 10 | Body health/position/yaw remain in frozen core when active, linked by population index; durable torso wounds in population | Re-derived on activation/load; pending strikes forgotten by Creative perception controls | 12 active, two per building; two spawns/slow tick |
| 12 | Portals, route caches, path budget | Derived and transient | `PortalGraph`, `PathBudget` | 10 | 4, 6 | Not saved | Invalidated by edits and doors | ≤ 64 routes |
| 13 | District overlays (flood, contamination, growth) | Derived; consequential changes persistent | `DistrictRules` | 13, 15 | 3 | Within `urban.population` or `urban.sites` as decided | — | Capped |
| 14 | Discovery: buildings, secrets, rumours | Persistent | `UrbanDiscovery` | 14, 16, 21 | 4 | `urban.discovery`; absent means nothing discovered | Load replaces | Capped |
| 15 | Sites: residents, health, deaths, trust, stocks, relocation | Persistent | Urban site registry | 16, 17 | 4 | `urban.sites` or settlement extension; absent means plan defaults | Dormant simulation; stable resident slots | NPC caps (B-37) |
| 16 | Salvage depletion, barricades, upgrades | Persistent | `RefugeSystem`, `FixtureState` | 18 | 4, 6 | `world.fixtures`, `urban.refuges` | Load replaces | Capped |
| 17 | Utilities: repairs, fuel, charge, reservoirs | Persistent | `UtilitySystem` | 19 | 4, 9 | `urban.utilities` | Explicit pause, unloaded and dormant elapsed-time policy; no catch-up refills | Capped |
| 18 | Missions, comms stage, rewards | Persistent | `UrbanMissions` | 20 | 4, 7, 10, 15 | `urban.missions` | Idempotent events; rewards once | Journal ≤ 4 |
| 19 | Map raster caches | Transient | `MapScreen` | 21 | 3, 4, 14 | Not saved | — | Bounded |
| 20 | Presentation: ambience, particles, light terms | Transient | `engine`, `gfx` | 22 | 4 | Not saved | — | Existing caps |
| 21 | Eviction bookkeeping, if eviction is chosen | Decided in 24 | `World` | 24 | All | Decided in 24 | — | — |

Identity scheme (implemented by package 02 in `world/urban/UrbanIds`; the packed form is a save format and frozen):
- **Canonical binary form**: a packed 64-bit id. Bits 63–60 hold a kind code: 1 district, 2 parcel, 3 building, 4 floor, 5 room, 6 fixture, 7 resident, 8 encounter. Code 0 is never valid, and codes 9–15 are reserved and append-only. Bits 59–58 are reserved and must be zero. Then come region x and z (18 bits each, signed, ±131,071 regions, about ±33.5 million blocks, well past float precision), parcel (7 bits, 0–127), building slot (2 bits, 0–3), floor + 2 (4 bits, floors −2 to 12, where floor *n* of an *n*-storey building is its roof), room (5 bits, 0–31) and slot (4 bits, 0–15).
- Every field a kind does not use must be zero, so a building, its first floor and that floor's first room have distinct ids. Doors, windows and containers are all fixtures: a container id is its fixture's id.
- **Resident ids**: the building that the site occupies, plus a resident slot (5 bits, 0–31) and a generation counter for replacements (8 bits, 0–255), stored in the floor, room and slot bits. Never a list index.
- **Canonical text form**, for mission targets and logs, following the existing `storage:<sid>:x:y:z` precedent: `urban:<kind>:<rx>,<rz>` followed by one `:`-separated index per level, for example `urban:fixture:-3,12:7:2:-2:31:15`. A resident ends `:<slot>:<generation>`. The parser accepts only the canonical form, up to 64 characters.
- **Encounter ids** (09) use appended kind code 8, the ground-floor room id plus slot 0 (street) or 1 (indoor). These packed ids have their sign bit set; they live in a separate population ledger and are not player-container ids. Codes 9–15 remain reserved.
- **Player container ids** (07): negative monotonic ids in the separate container ledger, disjoint from packed generated ids. They are not accepted by `UrbanIds`; generated ownership still uses canonical building ids.
- The proposal of milestone 01 used 21-bit regions, a 1-bit building slot and a signed 4-bit floor. A signed 4-bit field cannot hold storey 11, so the floor became a biased field, and a kind tag replaced the spare bits.

Ids never derive from visit, activation or list order. They are validated by range and kind (`UrbanIds.isValid`, `requireKind`). A section that stores an id fails the whole load on an invalid or wrong-kind id.

## 14. The playable slice (gate 11)

The slice is complete when, on a **generated** neighbourhood (not a staged arena), with normal Survival resources and controls:
- the player enters a real furnished multi-floor apartment and a shop;
- finds food, water, treatment and repair or upgrade supplies in rooms whose function matches;
- handles or avoids an infected with fair senses and supported multi-floor pursuit;
- returns to the refuge and obtains a meaningful upgrade without mining ore or felling a tree.

Saving after partial looting, a changed door or wall and a defeated encounter, then reloading over a live world and revisiting in a different chunk order, shows no refill, no resurrection and no stale state. All tested generated rooms are reachable, Survival and Creative twins pass, and the loop is understandable without debug overlays. A missing native route or capture keeps the gate pending and blocks package 12.

## 15. Out of scope

Whole-city simulation, driving (vehicles only as static salvage props), multiplayer, structural collapse, simulated elevators, procedural dialogue AI, a citywide electricity solver, a full food web, a full-building acoustic simulation, and an expanded anatomical-gore goal. Each needs an explicit owner scope change (DECISIONS §5).

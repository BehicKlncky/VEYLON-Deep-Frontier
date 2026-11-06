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

**Implemented in package 21** (generator 19, DECISIONS D-209–D-218): the route above is readable with ordinary UI. The first session logs five short lines (home, barring a door, searching's noise, the map, survivors' work) that stay whole on the HUD's log, and the map's journal repeats them at length until the survivor takes work or hears of the relay. The HUD names the district and the building and floor; a container's line names what it is, whose it is and what searching costs; a lock names the control and the tool, and the city calls its pry tool so. M opens a city map of the streets around the survivor with the buildings they entered named, home, the sites they found, every objective (rumours as rough areas), a journal and a sketch of the storey underfoot with its stairs and ways out. Nothing reaches the map that the survivor has not walked, entered, found or been told; hidden people stay off it until their cellar is found.

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

**Implemented in package 20** (generator 19, DECISIONS D-197–D-208): one relay mast stands on the roof of the tallest unheld office block (else a school, security post or clinic) of the civic region nearest the spawn, a cabinet under a guard-rail mast that nothing can break. C1: anyone of a camp, a stronghold or a hidden refuge, asked about the old radio relay, names its district and a compass direction, never the building; standing beside it finds it too. C2: a transmitter module, power cells and antenna cable each lie in two independent kinds of source spread over the districts (security posts and every stronghold's stores; warehouses and clinics; office blocks and roof maintenance lockers), in the first roll of their container, so no single building, district or person is the only way. C3: a stronghold that counts the survivor a friend, or hidden civilians who trust them, share the relay's codes; without them the call hunts the band four times as long. Infiltration is the stronghold's existing intrusion: its module can be stolen from the restricted stores, which fails that mission and is judged by the theft rules; no separate tunnel or roof route is planned, because no stage needs an access only one site can give. C4: the antenna and the cells fitted at the cabinet with a pry tool, and a radio workstation built at a workbench round the module and put down on the refuge's wiring as a fourth utility device. C5: the radio, switched on and fed in a building the survivor holds, transmits by its generator's running seconds while every three seconds it is a real noise of 32 blocks; when the call has gone out the city answers once, the survivors the player knows trust them more, and the existing ending card shows with urban text. Enter or Escape carries on in the same world. Beside the chain, camps and strongholds give the five missions of §10, each naming a real object, counting only the deed itself, rewarded once, and failing explicitly and for good. Historical beacon worlds are untouched.

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

Initial building families stay at ≤ 5 storeys (apartments) and ≤ 8 (offices) until package 24 has measured dense-city cost. The package-12 office blocks stop at five storeys (D-99). Flooded districts place their standing water deliberately *[13]*. Their overlays must be shown to be stable under the existing `WaterSystem` rules. **Implemented in 13 (D-110):** one block of water at y 41 over flooded roadway and courtyard lawns, two in canal basins (bed y 39), so every water surface is the top of y 41 on a solid bed; `WaterSystem` never spreads water above sea level and only drops it into air, and a generated flooded region is unchanged after a full water-system run.

**Implemented in 14 (D-118–D-127), generator 13.** The bands are used as planned:

- **Transit (B2 band).** Tunnels lie only under boundary avenues, where nothing else is built: track bed on the y 32 slab, feet at 33, ceiling at the y 36 slab. Each region owns one candidate segment and holds at most one station and two segments. A station's platform stands one step above the track, under a vault clear to y 39, with its stair rising to the avenue sidewalk. A flooded district's tunnels hold one block of water at y 33 on a solid bed.
- **B1 band.** Every apartment outside the starter superblock has a first basement. Service passages join two basements at y 37 under a courtyard passage. One apartment per residential region seals a back room behind a loose panel as package 16's hidden refuge, empty until then. Flooded districts hold one block of water over the basement floor (y 37).
- **Roofs.** Parapets carry a guard rail, a concrete bulkhead with a metal door covers the stair, and some roofs carry a fire-escape gap or a connector. Fire escapes are climbed up only, because the controller has no ladder descent (D-123).
- **Discovery.** Stations and cellars enter `urban.discovery` only when observed (D-124); the HUD names the place underfoot.

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

**Implemented in 13 (D-107–D-117), generator 12.** `DistrictMap` gives every region its identity: the spawn region residential, its neighbours residential or commercial (one guaranteed commercial), and an industrial, a flooded, a civic and a quarantine region always within three regions, on every seed. Each district maps to one climate biome, and the HUD names the district. Overlays are a separate raster that keeps two cells from every parcel and leaves avenues dry and clean (D-110). As implemented:

| District | Plan and buildings | Street profile and overlays | Supplies | Climate and visibility | Exposure and hazards | Threat budget | Reason to go |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Reclaimed residential | Layout-2 courtyard housing, park and shop row; walk-ups, corner grocers and clinics, one school per region | Narrow local streets; tall grass and bushes on lawns, grass through the outer sidewalk | Kitchens, bathrooms, wardrobes, grocers (content-3 wardrobe bias) | Meadow; clear | Low; overgrown courtyards give cover | 2–6 anchors (the spawn region is already over budget with its starter slots) | Basics; the refuge |
| Commercial downtown | Layout-2 shop rows, plazas and four housing blocks; offices, grocers, clinics, one police post | Wide avenues and boulevards, plazas, service alleys; planted medians | Packaged food and shop goods (grocer and shop bias), desks | Meadow; clear | Long open sightlines on avenues and plazas | 6–12 | Food stock, offices, the police post's armoury |
| Industrial yards | Depot rows of service lots, two or three workers' blocks, one or two fenced yards; warehouses with workshops, canteens, one security post | Service alleys; fenced yards with four gateways, chemical spills and powder kegs | Tools, scrap, pry tools, iron and torches (utility and warehouse bias) | Dry Scrubland: hot, so thirst rises; light haze (0.85) | Open yards, contaminated ground, explosive kegs | 4–10 | Tools and repair stock, kegs as salvage |
| Flooded neighbourhoods | Low (2–4 storey) courtyard housing around one or two canal basins; walk-ups, grocers, clinics, rarely picked clean | One block of water on every local roadway and courtyard lawn, plank duckboards at crossings, causeways over two-deep canals; avenues stay dry | All tables at −15 % (waterlogged), but few buildings were stripped | Wet Marsh: rain and fog more often; mist (0.7) | Wading soaks the player (wetness 1, cold) and halves walking speed; deep canals | 2–6 | Quiet buildings with intact stock, water to boil |
| Civic and medical precinct | Courtyard and office rows around a fenced square; clinics, municipal offices, two schools and two security posts | A square fenced with sheet metal, entered through four painted checkpoints | Bathroom cabinets and shop shelves roll the clinic table (content-3 civic bias); secured armouries | Meadow; clear | Guarded lobbies, locked dispensaries and armouries | 4–10, clinics and posts first | Medicine, ammunition |
| Quarantine | Cordoned courtyard housing, three clinic rows, usually a fenced square; field clinics and checkpoint posts, mostly damaged or stripped, never held | A sheet-metal cordon along shared avenues, open at a checkpoint wherever a local street crosses; contaminated lawns, plaza and roadway patches | Secured tables +10, others −10 (content-3 bias); many dispensaries and armouries | Rocky Highlands: cold; murk (0.6) | Contaminated ground (sickness), toxic fog that only harms here, the densest infected | 6–12 (Veilbound; alien organisms are 15) | Late rare medicine and ammunition; never needed for the first meal, water or treatment |

**Implemented in 15 (D-130, D-134, D-135), generator 14.** The quarantine grows:
up to two growth sites per region, each a node, a membrane carpet and a
Veilmaw, in courtyards and fenced squares; its budget pays for them, one
building's share for every two sites, rounded up, so its infected and Veilmaws
together never exceed the district maximum of 12. Breachers
take the street doors of one budgeted building in three in the industrial
yards, the civic precinct and the quarantine. Outside the quarantine, parks,
one residential courtyard in three, canal basins and walk-up roofs keep
wildlife. No district's basics depend on any of it.

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
| A5 | Locked routes need a key or tool obtainable outside the locked area, or an alternate entry. A key is never only behind its own lock. Abandoned property can always be forced with a basic tool, at a noise cost. Package 12 refinement (D-100): a secured dispensary or armoury may be reachable only by forcing its own lock; the validator proves the lock is the only way in, stands on an ordinary route and opens entry and exit routes when forced. |
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

**Implemented in 12 (D-98–D-101):** generator 11 / layout 8 adds building
template 5: grocer or convenience store, clinic or pharmacy, school, office
block, security post and warehouse with workshop. Each family runs the walk-up
skeleton under its own room program, then an overlay fits its purpose: shop
floors and stockrooms; a locked dispensary, treatment rooms and a washroom;
classrooms, a staff office and a canteen; work floors, utility and break rooms;
a locked armoury, squad and interview rooms, evidence and equipment stores and
bunks; rack halls with shelf bays and a workbench. Every family has a painted
sign, its own facade and deterministic damaged, picked-clean and partially
occupied variants; schools and offices may have a basement. Each region holds
one school, one security post and one warehouse; the starter superblock and all
walk-ups keep template 4. [BUILDING_PLANS.md](BUILDING_PLANS.md) shows one floor
plan per family.

## 6. Occupied sites and the player's refuge (D-21, D-22)

| Site | Role in the loop | Spatial basis | Population (*initial*) | Interaction and consequence | Packages |
| --- | --- | --- | --- | --- | --- |
| Player refuge | Home: storage, bed, crafting, utilities, respawn anchor | Starter ground-floor flat in 08; broader claims in 18 | Player; unattended provisional shelter in 08 | Store, rest and repair in 08; claims/fortification/utilities later; threats arrive through genuine noise and exposure, not scheduled raids | 08, 18, 19 |
| Survivor camp | Trade, aid, rumours, missions | Courtyard, rooftop or reclaimed ground floor | 3–6 | Trade, aid delivery, missions; trust against theft and violence | 16 |
| Hidden basement civilians | Discovery and aid; source of intel and codes | Planned secret basement rooms with a discoverable entrance and clues | 2–5 | Found through clues and real interaction; deliver aid or escort to a known camp; deaths are permanent | 14 (reservations), 16 |
| Guarded stronghold | Protected access, trade hub, holder of a relay component | School courtyard or municipal block with barricades, gates, posts, barracks and clinic | 8–16, ≥ 4 guards | Trusted admission, trade, restricted access or intrusion; theft attributed to the stored owner; guards perceive through real senses | 17 |
| Hostile survivor site | Risk and reward; bounded raids | Fortified warehouse or yard | 4–8 | Avoid, infiltrate or fight; finite supplies; an attainable alternative route | 17 |

Existing settlement machinery is reused rather than replaced (**Existing**, B-34–B-40): stable string ids, activation and dormancy budgets, reputation, theft dedupe, counterattacks with exactly-once outcomes, and captive rescue. Urban sites must not use the legacy terrain planner. Their layout must depend only on (seed, plan), because legacy village layouts read persisted resident counts (B-36). Residents need stable slot ids, not list indexes (B-35).

**Implemented in package 16** (generator 15, DECISIONS D-141–D-153): each region outside the quarantine plans at most one camp of 3–6 people on the reclaimed ground floor of a held walk-up, entered by its rear door, with spare beds upstairs; each residential region's sealed cellar shelters 2–5 civilians of no faction. Courtyard and rooftop camps are not built, because open ground has no walls or planned cells to keep people in (D-142). The people are registered settlement records with stable slot ids and their own `urban.sites` section; they keep fixed routines on planned cells, eat from their real stash in hourly lockstep whether on screen or not, and judge the player by deeds alone. The aid interactions are supplies into their stores, and, for hidden civilians, a safe move to a known camp's spare beds; trade from finite stores, a camp medic and threats complete the conversation. A refuge is found through M14's traces, its people's muffled sounds and the loose panel itself.

**Implemented in package 17** (generator 16, DECISIONS D-154–D-168): every civic region holds a guarded stronghold in a courtyard block whose six buildings are all held, with a clinic and barracks in its apartments, walled by its own buildings, a metal gate in one passage and a barricade across the other; one raider yard holds an industrial depot row's service alley within three regions of the spawn, with an unheld back way into it. Compound ownership is planned before any building (`CompoundClaims`), so the walls, locks, posts and people are all plan data. Strongholds belong to the urban Wardens and raider yards to the Wreckers, site-local sides that leave every legacy faction untouched. The gatekeeper admits a visitor by day to the yard and the clinic; friends come and go; guards who see a visitor in the barracks warn them, then put them out for the day, and drive out intruders. Theft is judged by what its people saw, or later by missing stores and a guard's recent sighting of the player inside. Alarms send at most three guards; raiders send at most two hunters, once a day; a yard whose raiders are all dead is taken, and a stronghold is grateful for it once. Everything consequential is saved in `urban.sites` and the new `urban.compounds`.

**Implemented in package 18** (generator 17, DECISIONS D-169–D-182): the player may hold up to three refuges, each one storey of one building claimed at a bed with crouch and F and kept as plan ids, so it outlasts every edit inside it. The claiming bed is where a refuge is slept in, and the refuge last chosen by sleeping is home, where the survivor wakes after death. Survivors' rooms cannot be claimed while anyone of theirs is left, a held commercial or civic building's upper floors can be shared, and something hostile on the storey stops a claim; a claim confiscates nothing, and no new encounter appears inside a claimed storey. Furniture, doors, rails, signs, the workbench, basins, beds and the burnt-out cars now left in the local streets come apart for planks, scrap, sheet metal, fibre and spare parts, by time, tool, wear and noise, through one transfer that pays once. Plank and sheet-metal barricades go into broken frames or open cells and door braces reinforce barred doors; a Breacher hammers plank barricades and braces down, every other threat walks round, and nothing the player builds can shut them in. A refuge's safety is read from its real ways in and light: shut tight it rests better and draws nothing; left open it may draw a real infected body that wakes the sleeper, and nothing is ever conjured.

**Implemented in package 19** (generator 18, DECISIONS D-183–D-196): a building's planned basins are taps on the finite stale, untreated water left in its pipes, shared by the building and never refilled; survivors' taps are theirs. A generator rebuilt from salvage, fuelled only from the tanks of cars that did not burn, feeds the work lamps and the water purifier of its own building, the circuit, burning fuel by time and load, wearing, rebuilt with spare parts and heard through the walls by the existing hearing; a lamp gives light exactly while it is fed. The purifier, its filter packed with fibre as its repair, pumps the building's stale water and treats it into clean water on the generator's power, and water only ever moves. Utilities run on the world's own seconds, hold still far away and restore exactly from `urban.utilities`; one rain collector keeps a survivor in water without any power.

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
records new encounters stop, with no death eviction or migration. In 09,
same-floor pursuit used two bounded 400-expansion A* searches per fast tick;
doors and unsafe strides stopped it. These guards do not protect an exposed
refuge from an existing body.

**Implemented in 10 — indoor pursuit (D-86–D-90).** Residents and infected now
share four path queries / 2,800 expansions per fast tick, at most 700 per query.
Derived entries, stair landings and room anchors divide longer routes; loaded
exterior legs advance through modest supported local anchors. Every actual
segment uses live voxel A* and swept collider clearance. Missing, edited,
barred or unloaded routes wait safely instead of direct steering. There is no
guarantee of a citywide detour, forced entry by AI or gap jumping.

The 64-route transient world cache invalidates only intersecting edit envelopes.
Unlocked door swings preserve walking connectivity; lock/support/headroom edits
invalidate it. Searches and cache entries rebuild on load/activation without a
new save format or generator revision. Noise is attenuated through partitions,
slabs, glass and closed doors under shared query/sample bounds. Each event is
consumed once, and hidden movement never changes its position. Veilbound now
search fresh sound for fourteen seconds and last sight for eighteen; they turn
at the remembered place and stop on expiry. Creative clears player-derived
intent. Work/failure/unsupported-entry diagnostics appear in the runtime snapshot.
**Implemented in 13 (D-113, D-114).** Encounter catalogue 3 spends each region's
district threat budget (DESIGN §4.3) on unheld buildings, clinics and security
posts first; the starter superblock keeps catalogue one's slots. Every
district's threat is still the Veilbound: the quarantine differs by its budget,
contaminated ground and the toxic fog, which in generator 12 only harms inside
a quarantine district. Alien organisms and their props remain package 15.

Package 12 (D-106) makes entrance choice lock-aware: a body uses the nearest
entrance whose door is not locked or barred, so a clinic guard follows the player
out of the street door rather than waiting at the locked staff door.

**Implemented in 15 — alien ecology, a variant and city wildlife (D-129–D-140), generator 14.**
The roster is small and complete: one alien organism, one infected variant and
three adapted wildlife species.

| Body | What it does | Tell | Counters | Rewards and risks | Where |
| --- | --- | --- | --- | --- | --- |
| **Veilmaw** (alien) | Lies folded beside its growth node and never pursues. Wakes to touch (1.8 blocks), a player in placed light in plain sight, a noise on its ground, or a placed light on its ground. Lunges once at the point it committed to, recoils exposed and walks home; creeps to a light and smothers a torch. Every move is clamped inside a nine-block territory. | 1.1 s: hood rising, veins brightening, a rising call, one warning line | Crouch (no footsteps), darkness, back off during the telegraph, a torch beside the node (it burns and the Veilmaw withers), a light set down as a lure, blows during the recoil | Remains: two membrane sacs, never meat; a node broken with an axe gives one. The strike is 7 physical damage and sometimes the existing sickness | Up to two growth sites per quarantine region, in courtyards and fenced squares |
| **Breacher** (infected variant) | A blind Veilbound: touch and sound only, with longer hearing. Plans through closed wooden doors and hammers a locked or barred one down in four loud blows; metal doors hold | The hammering, heard by other infected too | Silence (it cannot see a crouched player in plain view), metal doors, distance | Slow, heavy blows (9 damage, 2.4 s apart) through the damage gates; a humanoid corpse | Street doors of one budgeted building in three in industrial, civic and quarantine regions |
| **Skitterwing, Murkhare, Glowdeer** (wildlife) | The species' own behaviour inside a habitat that leashes every move: birds over parks, canals and walk-up roofs; hares on park lawns and one courtyard in three; a deer in a large park | Prey bolts from a perceivable player | — | Optional, finite hunting: hare and deer meat as before, birds leave nothing, and no animal respawns | Outside the quarantine |

The Veilmaw keeps the guardrails above (no host fungus, no echolocation, no
certain-death bite) and departs from the pathfinding rule above deliberately:
it never leaves the open ground of its own courtyard, so it steers in straight
lines under a hard leash, sees by an occluded voxel ray, hears through the
shared occluded query, and stops a lunge at a wall. Its sickness and the
Breacher's blows go through the existing damage and affliction gates, so
Creative is immune and unseen. Bodies are planned encounter residents
(catalogue 5) with separate caps: infected 12, alien organisms 4, wildlife 12.

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

**Implemented in 12 (D-100–D-102).** The commercial and civic families map their
containers onto the unchanged content-3 tables, so no roll changes. Dispensary
medicine sits behind a hand-forcible wooden lock and armoury ammunition behind a
metal lock that needs a pry tool, both loud, and every unheld clinic and security
post keeps a lobby guard (encounter catalogue 2). Held street floors are survivor
property; their upper storeys are not. Grocers, canteens, break rooms, washrooms,
treatment rooms, utility rooms and workshops keep food, water, treatment, light and
pry tools available without forcing anything.

**Implemented in 23 (D-232–D-240), generator 20.** Content revision 4 meets S5's
targets: a stocked flat holds 1.42–1.66 servings and 0.57–0.65 litres on the design
seeds, treatment lies in about half of bathrooms, and a district's bias is a percentage
of each chance, so S3's two kinds of source hold in every district. The starter block
keeps §4.4 from reservations while its refuge holds no scrap, so the first upgrade takes
a trip. Salvage, trade and rewards cannot be turned into more than they are worth, a
trader keeps back what its people live on, and S6's renewables are the rain, boiling,
camps' foraging surplus and nothing else. §16 records the targets, the seed matrix and
the assumptions.

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
| 12 | 6 archetypes: grocery or convenience store, clinic or pharmacy, school, office block, police or security site, warehouse or workshop. **Implemented** (D-98–D-101): 1–2, 1–3, 2–3, 3–5, 1–2 and 1–2 storeys; ≤ 4 containers per room and ≤ 12 per storey; one school and one security post per region |
| 13 | All 6 districts. For design seeds, residential and commercial within 1 region of spawn and all six within 3 regions (Chebyshev) |
| 14 | ≤ 1 transit station and ≤ 2 tunnel segments per region; basements in ≥ 50 % of residential buildings; ≥ 1 hidden-refuge reservation per 2 residential regions. **Implemented** (D-119–D-122): one owned segment per region, so ≤ 1 station and ≤ 2 segments by construction; every apartment outside the starter superblock has a basement; one hidden cellar in every tested residential region (D-122 records the limit of that evidence) |
| 15 | 1 alien organism with growth nodes; ≤ 1 infected variant; ≤ 3 adapted wildlife species. **Implemented** (D-130–D-135): the Veilmaw with ≤ 2 growth sites per quarantine region; the Breacher; Skitterwings, Murkhares and Glowdeer; ≤ 4 active alien organisms and ≤ 12 active wildlife beside the 12 infected |
| 16 | Camps of 3–6 residents; hidden groups of 2–5; for design seeds, ≥ 1 of each within 2 regions of spawn. **Implemented** (D-142): at most one camp per region outside the quarantine and one hidden group per residential region's sealed cellar; every design seed has both within 2 regions; ≤ 5 spare beds per camp; ≤ 1,024 registered sites |
| 17 | 1 friendly stronghold (8–16 residents, ≥ 4 guards) and 1 hostile site (4–8), each within 3 regions of spawn on design seeds. **Implemented** (D-156, D-157): a stronghold in every civic region (8–16 people, 4–5 guards) and a raider yard of 4–8 in the reserved industrial region within 3 regions of the spawn, plus one industrial region in four beyond; every design seed has both within 3 regions; at most 3 guards answer an alarm and at most 2 raiders hunt, once a game day |
| 18 | ≤ 3 claimed refuges; ≤ 3 barricade kinds; a salvage table for every urban furniture kind. **Implemented** (D-170, D-171, D-173, D-174, D-176, D-177): ≤ 3 claims; plank and sheet-metal barricades and the door brace; a salvage entry for every furniture kind, the doors, rails, barricades and each part of a wreck; ≤ 6 burnt-out cars a region; ≤ 2,048 damaged barricade cells and ≤ 256 braces kept |
| 19 | 1 water utility and 1 power source (generator or battery bank); ≤ 4 consumer kinds (light, purifier or pump, radio, optional fridge); circuit scope of one building. **Implemented** (D-184, D-186, D-187, D-190, D-195): the purifier-pump and the generator; two consumer kinds, the work lamp and the purifier, the radio workstation left to 20 and no refrigerator; one building a circuit, one generator to it feeding its four nearest consumers within 16 blocks; ≤ 64 devices; taps finite per building, ≤ 1,024 tapped buildings |
| 20 | Comms chain C1–C5; 5 missions (clinic supplies, hidden-civilian aid, camp utility, district landmark, stronghold-held component); active journal ≤ 4. **Implemented** (D-198, D-199, D-203, D-206): one relay a world; two independent kinds of source for each of three parts; the five missions, each kind given once by a site and each object asked for once in a world; ≤ 4 missions in hand and ≤ 256 records |
| 23 | Tuned supplies, spoilage, carrying, rummaging, durability, salvage, trade and encounter pressure; a balance record with a seed matrix. **Implemented** (D-232–D-240): content revision 4's twelve tables; a trader's reserve of a ration and a skin a mouth; a 1.2 s pain guard; two starter encounter slots (catalogue 6); spoilage, carrying, search times, durability and firearm noise unchanged with reasons (D-239); §16 |

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
| Building, floor and room plans | `BuildingPlan`, `FloorPlan`, `RoomPlan`, `FixtureSlot`; a `BuildingTemplates` catalog keyed by revision *[05]*; commercial and civic families in `CivicTemplates` and `CivicCatalogue` *[12]* | Precedent: `settlement/SettlementBuilder.Layout` | Derived, frozen per revision |
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
| Indoor state | `IndoorState` from room plans feeding temperature, audio and light *[06, 22]* | `TemperatureSystem`; `AudioSceneState`; `AmbienceSystem`; `World.skyLight`; `ShelterSystem` (B-57, B-59, B-60, B-20) | Transient. **22:** `AudioSceneState.urbanZone` and `weatherReach` choose the city zones and the weather's reach; walls stop block light (`UrbanIndoorQueries.lightReaches`); `CitySoundscape`, `UrbanDressing`, `UrbanTraces` and the terrain shader's city rules are presentation, never saved (D-219 to D-230) |
| Districts | `DistrictRules` *[13]* | `world/Biome` climate kept; `TemperatureSystem`; `WeatherSystem` | Derived; consequential hazards persist |
| Underground and roofs | Basement and transit planners; hidden entrances *[14]* | (new) | `world.fixtures`, `urban.discovery` |
| Sites | Urban site registry reusing `Settlement`, `SettlementManager` and `DormantSettlementSimulation` *[16, 17]* | `settlement/*` (B-34 to B-40) | `urban.sites` or a settlement-codec extension (D-21). **16:** `urban.sites` v1 (D-150). **17:** compounds are site records too; `CompoundAccess` and `CompoundResidentAI` own their rules and duty, and `urban.compounds` v1 what they remember (D-167) |
| Refuge and fortification | `RefugeSystem`: claims, barricades *[18]* | `SleepSystem`; `PlayerBlockActions`; `World.setBlock` (B-19) | `urban.refuges`, `world.fixtures`. **18:** `UrbanRefuges` and `RefugeSystem` (claims, home, respawn, the night), `RefugeExposure`, `SalvageTable` and `Dismantling`, `Fortification` and `UrbanFortifications`; `urban.refuges` v2 and `urban.fortifications` v1 (D-176, D-179) |
| Utilities | `UtilitySystem` (a `MediumTickSystem`) *[19]* | `WaterSystem`; collectors in `ItemConditionSystem`; `Chunk.lights` (B-20, B-58) | `urban.utilities`. **19:** `UrbanUtilities` (taps, devices, circuits) on `UrbanWorldState`; `UtilitySystem`, owned by `RefugeSystem`, ticked by one medium-tick line in `Game`; lamp light through `World.emitterOn`; fuel and device salvage in `SalvageTable`; `urban.utilities` v1 (D-194). `WaterSystem` is untouched: taps are a ledger, not voxel water |
| Missions and objective | `UrbanMissions` (bounded journal) plus `CommsObjective` *[20]* | `ai/Quest` and `FactionSystem` identity and dedupe; `ui/QuestObjectiveView`; `AppState.VICTORY` (B-07, B-54) | `urban.missions`. **20:** `UrbanMissions` (the objective's facts and the mission records) on `UrbanWorldState`, with the pure `RelaySites` and `CommsComponents`; `UrbanComms` (rumour, codes, the HUD line and map target) and `UrbanMissionBoard` (offers, evidence, reports, failures, journal) in `settlement`; `CommsObjective` (relay repair, radio, broadcast, the ending) owned by `RefugeSystem` and ticked by its medium tick; hooks in `CrateTransactionSystem`, `UrbanSiteRelations`, `UrbanSiteRelocation`, `UtilitySystem`, `NpcScreen`, `Hud` and `QuestObjectiveView`; `urban.missions` v1 (D-207) |
| Discovery and map | `UrbanDiscovery` plus a plan layer in the map *[14, 21]* | `ui/MapScreen`; `Poi.discovered` (B-71) | `urban.discovery`. **21:** `UrbanVisits` on `UrbanWorldState` (districts walked, buildings entered; a transient memory of rooms entered for the sketch); `ui/CityMap` (what the map and HUD may show), `ui/CityMapView` (the screen) and `ui/FloorSketch` (the storey underfoot) behind `MapScreen` for layout 16; `urban.visits` v1 (D-209–D-213) |
| QA | `UrbanQaScenes` in its own class *[03 onward]* | `QaHarness` (1,488 of 1,500 lines); `AutomatedRunDriver` (B-09, B-75) | — |

Wiring rule: `Game.java` is at 990 of its 1,000-line budget (B-09). Urban systems enter through one collaborator (proposed `UrbanSystems`), with at most one delegate line per tick bucket and one reset and reseed call each.

## 13. Data inventory and state owners (D-17)

The table is in dependency order: an item may depend only on items above it. Section ids are proposals; the owning package fixes the final id and version in DECISIONS.md. Every persistent item gets its codec, absence default, reset hook and corruption tests **in the package that introduces it**. Package 25 audits; it is never the first serialization pass.

| # | State | Kind | Owner | Package | Depends on | Persistence; absence default | Reset (new / load / dormancy) | Bound |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Generator version `GEN_URBAN` | Persistent | `World` | 02 | — | v3 base int; values above `MAX_SUPPORTED_GENERATOR` rejected | Fixed per `World` | — |
| 2 | Urban content revision | Persistent | `UrbanRevision` | 02, 07–08 | 1 | `urban.meta`; absent means 1 for older layouts; generators 9–11 require content 3. **23:** a fresh world records its generator's revision (`UrbanRevision.contentFor`); generator 20 requires content 4 and no other layout accepts it (D-232) | New and load | — |
| 3 | Region, district, street and parcel plans | Derived | `CityPlanner` | 03 | 1, 2 | Not saved | Cache per `World` | ≤ 16 regions |
| 4 | Building, floor, room and fixture plans | Derived | `BuildingPlanner` / `BuildingPlan` | 05–06, 08 | 3 | Generator 6 selects template 1 / layout 3; 7 furnished template 2 / layout 4; 8 shaped template 3 / layout 5; 9 starter template 4 / layout 6; 10 same geometry plus encounter catalogue 1 / layout 7; 11 the commercial and civic catalogue, template 5 / layout 8, walk-ups unchanged. Unchanged `urban.meta` v1 validates the layout; plans not serialized | Cache per `World`, discarded on new/load | ≤ 256 buildings; ≤ 73,984 cells/plan |
| 5 | Generated blocks plus edits | Derived plus deltas | Generator and `World` | 03, 06 | 3, 4 | `changedBlocks` (v3 base, last-write log, B-29) | — | 4,000,000 edits |
| 6 | Fixture state: door or window open, keyed lock or one-sided bar (broken is a recorded block edit); barricade health later | Persistent, touched cells only | `FixtureStates` on `World` | 04, 06 | 4 | `world.fixtures` v1 (D-49); absent follows generated lock defaults, or unlocked for edited/player-placed fixtures; explicit saved unlock overrides the plan | Load replaces; reapplied after structure and recorded edits on chunk generation | ≤ 32,768 cells on write and read |
| 7 | Container identity, state and owner; authoritative contents | Persistent, touched only | `UrbanContainers` metadata; `World.crateContents` inventory | 07–08 | 2, 4 | `urban.containers` v1; required for content ≥ 2; content-1 absence adopts core contents/empties and edited reservations | Load replaces; never re-rolled | ≤ 32,768 rows/core inventories; 12 slots; 20-byte header + 36 bytes/row metadata |
| 8 | Search progress; open target and rummage timer | Progress persistent; target/timer transient | Ledger; `CrateTransactionSystem` | 07 | 7 | Progress in `urban.containers`; load closes UI but retains elapsed work (D-72 revises the initial transient proposal) | Load replaces progress; resets target/timer | One active search |
| 9 | Starter refuge identity and ground-floor respawn anchor; claims later | Persistent | `UrbanWorldState` / `UrbanBootstrap`; `RefugeSystem` later | 08, 18 | 4 | `urban.refuges` v1, required for generators 9–16; absent on older worlds means no claim. **18:** version 2, required for generator 17, adds up to three claims and home (D-179) | New sets starter once; load replaces without replenishing | One starter anchor; ≤ 3 claims in 18 |
| 10 | Encounter identities, durable injuries/positions and permanent deaths | Persistent | `EntityManager.encounters` / `UrbanEncounterDirector` | 09, 15 | 4 | `urban.population` v1 with catalogue 1 (generator 10) or 2 (generator 11, D-102); required for generators 10–11, absent on earlier worlds means no encounters. **15:** codec 2 under catalogue 5 (generator 14) keeps every species: a species id per row and a binding into the NPC or creature list (D-136). **23:** generator 20 freezes catalogue 6 under the same codec 2 (D-238) | New clears; load replaces; dormancy pauses; no migration or replacement generation | 32,768 rows, 47 bytes/row + 12-byte header |
| 11 | Live hostile brain, pain, fire, panic and route intent | Transient | `Npc` / `InfectedAI` | 09 | 10 | Body health/position/yaw remain in frozen core when active, linked by population index; durable torso wounds in population | Re-derived on activation/load; pending strikes forgotten by Creative perception controls | 12 active, two per building; two spawns/slow tick |
| 12 | Portals, route caches, path budget | Derived and transient | `PortalGraph`, `PathBudget` | 10 | 4, 6 | Not saved | Invalidated by edits and doors | ≤ 64 routes |
| 13 | District overlays (flood, contamination, growth) | Derived; consequential changes persistent | `DistrictRules` | 13, 15 | 3 | Within `urban.population` or `urban.sites` as decided. **15:** growth is generated blocks plus recorded edits (a burnt node is an ash edit); a Veilmaw whose node is gone is a tombstone in `urban.population` (D-136) | — | Capped |
| 14 | Discovery: buildings, secrets, rumours | Persistent | `UrbanDiscovery` | 14, 16, 21 | 4 | `urban.discovery`; absent means nothing discovered. **16:** a refuge is found only through this ledger; a camp's discovery is a flag in `urban.sites` (D-144, D-150). **21:** the districts walked and buildings entered are `urban.visits` v1, optional, written for generator 19 only (D-210); the map shows hidden people only through `urban.discovery` | Load replaces | Capped |
| 15 | Sites: residents, health, deaths, trust, stocks, relocation | Persistent | Urban site registry | 16, 17 | 4 | `urban.sites` or settlement extension; absent means plan defaults. **16:** `UrbanSiteDirector` with `UrbanSite` records in the settlement map; `urban.sites` v1, required for generator 15 and failing the load when absent or malformed; stocks are the stash containers in `urban.containers` (D-143, D-150). **17:** compounds are site records too; passes, wanted status, the last sighting inside, missing stores, the hunt cooldown and the reward are `urban.compounds` v1, required for generator 16 (D-167) | Dormant simulation; stable resident slots. **16:** hourly lockstep needs, active or dormant; tombstones for the dead (D-146). **17:** warnings, knocks, responses and hunts in progress are transient: a load or dormancy ends them | NPC caps (B-37); ≤ 1,024 sites |
| 16 | Salvage depletion, barricades, upgrades | Persistent | `RefugeSystem`, `FixtureState` | 18 | 4, 6 | `world.fixtures`, `urban.refuges`. **18:** depletion is recorded block edits and wear is re-derived; damaged barricades and braces are `urban.fortifications` v1, required for generator 17 (D-176) | Load replaces | Capped. **18:** ≤ 2,048 damaged barricade cells and ≤ 256 braces |
| 17 | Utilities: repairs, fuel, charge, reservoirs | Persistent | `UtilitySystem` | 19 | 4, 9 | `urban.utilities`. **19:** v1, required for generator 18: litres drawn per building and every device's switch, condition, fuel and tanks; absent fails the load (D-194) | Explicit pause, unloaded and dormant elapsed-time policy; no catch-up refills. **19:** world seconds only, never paused, a sleep's real seconds, frozen beyond 128 blocks or unloaded, a load exact (D-192) | ≤ 1,024 tapped buildings; ≤ 64 devices |
| 18 | Missions, comms stage, rewards | Persistent | `UrbanMissions` | 20 | 4, 7, 10, 15 | `urban.missions`. **20:** v1, required for generator 19: the objective's facts and every mission record, checked against each other and the plans; absent fails the load (D-207) | Idempotent events; rewards once | Journal ≤ 4 |
| 19 | Map raster caches | Transient | `MapScreen` | 21 | 3, 4, 14 | Not saved. **21:** no raster cache: the city map reads pure plans and loaded chunks while open; the sketch's entered rooms are a transient memory in `UrbanVisits` (D-213) | New and load start empty | ≤ 64 sketched floors |
| 20 | Presentation: ambience, particles, light terms | Transient | `engine`, `gfx` | 22 | 4 | Not saved | — | Existing caps |
| 21 | Eviction bookkeeping, if eviction is chosen | Transient. **24:** none is kept but counters: a dropped chunk is regenerated from the seed, the plans and the persisted deltas (D-241) | `World` (`unloadChunk`, `chunkUnloads`), `ChunkRetention` | 24 | All | Not saved; a load starts with the chunks it regenerates (§17) | New and load start empty | ≤ 2,048 chunks after a pass, beside the kept window and pinned chunks |

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

**Implemented in 11 (D-91–D-97).** The slice is the generator-10 starter superblock as generated. `urban_slice` plays it through the production input seam. It leaves the refuge across the courtyard and forces an interior door of the apartment that hosts an encounter. It searches that apartment's utility locker, kitchen, bathroom and first-floor kitchen. It handles a Veilbound, searches the shop and returns to bar the refuge's street door. It eats, drinks, crafts the reinforced spear, quick-saves and quick-loads over the live world, then revisits in reverse order. The six frozen seeds pass it headless in both modes, and seed `20260731` passes it natively in both modes. Four blockers found while playing it were fixed:

- over-time drains interrupted searches (D-92);
- the camp-illness event chose infected (D-93);
- projectile torso wounds broke saves (D-94);
- F reopened a faced container (D-95).

The tuning findings in D-97 (threat pressure, a self-sufficient refuge, generous supplies, event drains) are assigned to later packages. They do not block the loop. Human playtesting and listening remain deferred until after 22 (D-80).

## 15. Out of scope

Whole-city simulation, driving (vehicles only as static salvage props), multiplayer, structural collapse, simulated elevators, procedural dialogue AI, a citywide electricity solver, a full food web, a full-building acoustic simulation, and an expanded anatomical-gore goal. Each needs an explicit owner scope change (DECISIONS §5).

## 16. The survival economy: inputs, targets and the seed matrix (package 23)

Package 23 (generator 20, content revision 4, encounter catalogue 6; DECISIONS D-232–D-240) tunes the city against a survivor's real needs and proves the loop sustains days of play without a tree or an ore block. Figures marked *measured* come from the tests named beside them on the six design seeds (`20260731`, `-4242`, `7`, `0`, `987654321`, `-9000000000000000000`); they are deterministic and re-run in every build. Nothing here is a playtest observation: those wait for the human pass D-80 schedules.

### 16.1 What a survivor needs, and where the city gives it

A waking game day (900 real seconds) costs 40.5 hunger and 67.5 thirst; a day with a night slept in the refuge costs about 25 and 40 (sleep drains 0.10 and 0.14 a second for the few seconds a night takes). A serving of dried berries gives 14 hunger at full freshness (7 to 12 as found), dried meat 22, a clean waterskin 60 thirst.

| Need | Urban sources (content 4) | Alternatives and renewables | Never needs |
| --- | --- | --- | --- |
| Food | Kitchens, pantries, fridges, grocers and shop shelves; survivors' trade | Camps' foraging surplus above their reserve (D-236); optional hunting (finite) | Berries, herbs or meat from the wild |
| Water | Kitchens, pantries, fridges, grocers, shops; a building's taps (stale, untreated) | Rain collectors; boiling at a fire on planks; the purifier on generator power | Lakes or streams |
| Treatment | Bathrooms (about half), clinics and dispensaries, desks, shops, armouries | Bandages from fibre; splints from sticks and fibre; a friendly camp's medic | Herbs or a herbalist bench |
| Clothing | Wardrobes (rain cloaks, boots) | Indoor shelter and fires | Hides or a tannery |
| Light | Torches (pantries, desks, lockers, warehouses, shops) | Torches from sticks and fibre; a lantern on planks (450 s each); work lamps on power | Charcoal |
| Fuel | Planks from furniture and doors (60 s of fire each) | Fuel cans from the tanks of cars that did not burn (power only) | Logs or coal |
| Repair parts | Scrap (lockers, warehouses, furniture, cars), sheet metal (lockers, metal doors), spare parts (engines, warehouses, workshop benches) | — | Ingots or smelting |
| Weapons | The reinforced spear (scrap, planks, fibre); the pry tool; a flintlock with balls in secured storage | Repairs for two scrap | Ore, a furnace or an anvil |
| Utility components | Spare parts, sheet metal, scrap, fibre filters | — | — |
| Aid and trade goods | Food, water and medicine from any search; salvage as payment | — | — |
| Objective items | Transmitter module (security armouries, stronghold stores), power cells (warehouse tool chests, clinic lockers), antenna cable (office and roof lockers) | Codes from a friend, or the band scan without them | Any one site's goodwill |

The audit (`UrbanEconomyAuditTest`) holds the "never needs" column: no recipe of the city list asks for a log, ore, ingot, coal, charcoal, sulfur, saltpeter, black powder, hide, leather, bone, berry or herb, or a station other than the hand and the workbench; every recipe's inputs are reachable from containers, salvage, survivors' stores, the starter kit and the city's creatures; and the module, cells, cable, fuel, spare parts, sheet metal and pry tool all lie within two regions.

### 16.2 Balance targets and what was measured

| Target | Value | Measured (content 4, six seeds) | Test |
| --- | --- | --- | --- |
| Food in a stocked flat (S5) | 1–2 servings | 1.42–1.66 (content 3: about 2.8) | census |
| Water in a stocked flat (S5) | 0.5–1 litre | 0.57–0.65 (content 3: about 1.7) | census |
| Bathrooms holding treatment (S5) | about half | 52–58 % | census |
| Buildings picked clean (S5) | about a third | 23–28 % | census |
| The starter block's food | days, not weeks: 3–12 waking days, at most 0.6 of content 3's | 4.9–9.7 (content 3: 15–26) | census |
| The starter guarantees (§4.4) | food ≥ 6 servings in ≥ 3 containers in ≥ 2 buildings, water ≥ 3, two bandages and an antiseptic, a pry tool outside the refuge | held on every seed, from reservations alone | census |
| The first upgrade | one trip: never from the refuge alone | the refuge holds no scrap; one neighbour's ground floor funds the spear | census, route |
| Pry tools in the starter block | found, not tripped over: at most half of content 3's | 4–7 (content 3: 22–28) | census |
| Necessities in every district (S3) | two kinds of source each | held in all six districts on every seed | census |
| Specialist goods (S3) | where they belong | spare parts never in flats; pistols only in secured storage; medicine four times richer a cabinet in clinics than bathrooms | census |
| Comms parts without anyone's leave | two unheld sources a part within three regions | modules 14–43, cells 271–411, cable 149–256 buildings | audit |
| Cars that did not burn within one region ring | enough for a generator, a radio, a purifier and the band scan (six) | 22–32 | audit |
| Optional nature | never more than a side dish | hunting the spawn region's wildlife gives at most a sixteenth of its containers' food, felling every park tree at most a ninth of its furniture's fuel | audit |
| Starter encounter pressure (§4.4) | one or two bodies, away from the refuge door | two slots on one apartment, 26–33 blocks from the door (catalogue 5: eight) | encounters |
| Melee | useful, never risk-free | a pry tool takes a strike in a fight with one Veilbound; the spear wins before the first strike | combat |
| Trade | finite, never starving the seller | stops at a ration and a skin a mouth; resumes from foraging | trade |
| Salvage loops | nothing mints value | nine recipe-made pieces never return more than they cost | salvage |

### 16.3 Time and the long game

- **First session (§3.1).** The slice route still completes on generator 20 on every slice seed: the first trip to the quiet apartment (its inner door forced), the shop, the spear at the refuge's bench, the starter threat met from across the block.
- **Days, headless.** The acceptance route (`UrbanEconomyRouteTest`, seed 20260731) completes the objective on day one because its legs are staged poses, then lives four more days: one walk-up searched a day (8 to 15 containers) keeps food above six servings and water above three with the rain collector, health stays at 100 and nothing is felled or mined. Real pacing, how long the objective takes a player and how the days feel, is a playtest question (§16.5).
- **Days, native.** `VEYLON_SCENE=urban_economy` lives two and a half real-time days with nights slept in the refuge (VALIDATION, milestone 23).

### 16.4 Sustainability assumptions

- **Searched containers never refill.** Nothing is reset, respawned or restocked by time; tombstones and depletion persist (S1, S6).
- **Water is renewable.** One rain collector emptied daily gives 2.4–3.0 litres against a waking day's 1.13 (D-195); taps are finite per building but every building has them, and boiling needs only planks.
- **Food is finite but vast, with one bounded renewable.** A region holds hundreds of waking days of food in its containers; camps forage a ration for every three people and a skin for every two every six hours, up to 16 and 12, and a trader sells only above its people's reserve. Gardening is not supported (no planting exists) and hunting never respawns.
- **Spoilage is the pressure on hoarding.** Found food goes stale (a third of its freshness) within one to four waking days, and stale food can poison; carrying is limited to 28 kg plus a pack.
- **Power is a convenience.** Fuel is finite (one can a tank, 300 s idle); nothing a survivor needs to live waits on it.

### 16.5 What tests guarantee and what only a person can judge

Deterministic, re-run in every build: the bands of §16.2, the starter guarantees, reachability and the no-tree, no-ore audit, conservation (no salvage, trade or reward loop pays twice), and the multi-day route. Subjective, pending the human playtest (D-80): whether scarcity feels tense rather than tedious, whether one building a day is the right rhythm, whether the starter block's threat is fair, whether the long band scan is a fair cost for refusing cooperation, and how many real days the objective takes.

### 16.6 Findings left to later packages

- **Two parcels cannot be planned** under building template 7 (generators 13–20): seed 987654321 region (−2, −3) parcel 44 at (−440, −681) and seed −9000000000000000000 region (−3, 0) parcel 116 at (−637, 219). Generating either chunk throws `IllegalStateException` ("no valid building in finite fallback"). Found by this package's three-region audit sweep; owned by packages 24 and 25 (streaming and the regression audit). **24:** left to 25: any fallback changes frozen template-7 output, a compatibility decision for the regression audit (D-247). **25:** resolved: such a building is planned once more without the roof opening that faced its own stair house, which changes nothing that planned before (D-248).
- **The city is generous in total.** The pressure the economy applies is distance, time, noise, risk and spoilage, not a running-out of food; whether that is enough is for the playtest.

## 17. Dense-city performance and the memory model (package 24)

Measured on the second machine (VALIDATION, milestone 24): the economy city, seed 20260731, render distance 6, 1280 × 720, VSync off. Decisions D-241 to D-247.

### 17.1 What a frame and a journey cost

- **Rendering** is about a millisecond a frame on the second machine's RTX 3060 Ti wherever a survivor stands: 960–1,130 FPS in a street, a walk-up's rooms, a transit platform, a stronghold yard, a growth site and a storm with a fire, 60–80 chunks and 140–195 draw calls a frame, 0.4–0.6 million triangles. The world's CPU submission is 0.16–0.24 ms.
- **Meshing** was the streaming frame's largest cost: a city chunk holds 12,600–26,900 vertices and takes 1.7 ms (p99 3.8–4.3 ms) on the CPU, and each was built about 2.6 times while streaming, because a neighbour that arrived later dirtied it again (D-243).
- **Planning** happens on first touch: a region plan in 1.4–4.7 ms and a building in 1.1–2.5 ms, most of it the building's accessibility proof, which is also most of a chunk's first generation (0.1 ms once its buildings are planned).
- **Simulation** in the dense scenes is under 0.1 ms a frame on average. A stronghold's or a street's slow tick cost 8–34 ms every ten seconds while the encounter director recomputed a region's threat budget for each parcel it asked about (D-244); it now costs 0.4 ms at the stronghold and 0.3–5.6 ms in the street, the larger planning a new region's sites; a slow tick that plans new regions' sites and anchors is the spike that remains (§17.5).
- **Audio** updates in 0.004 ms a frame; lights and props are part of meshing (0.0 emitters and 24–79 props a city chunk).
- **Memory** is the chunk: 27 KB of retained heap each in steady state (26,624 bytes of fixed arrays). Before D-241 a journey at render distance 6 kept about 810 chunks and 21 MiB more for every kilometre, and the renderer's two chunk selections and the QA harness walked all of them every frame.
- **The save** grows with durable facts and its time with the places it names (D-246).

### 17.2 Bounded work

| Work | Bound | Owner |
| --- | --- | --- |
| Chunk generation | 2 a frame, out to the render radius + 1 | `ChunkStreaming` |
| Mesh rebuilds | 3 a frame, finished neighbourhoods only, nearest first, inside the render window | `Renderer.selectDirty` |
| Chunk selection and visibility | the render window, (2 r + 1)² lookups, a reused list | `Renderer` |
| Light lists | one scan a generated chunk, one point update an edit | `Chunk` |
| Retained chunks | 2,048 after a pass beside the kept window and pinned chunks; a pass every 256 new chunks | `ChunkRetention` |
| Plan caches | 16 regions each of plans, route catalogues, ecology, sites, vehicles and threat budgets; 256 buildings; 64 transit segments | `UrbanWorldState` |
| Path search | 4 queries and 2,800 expansions a fast tick; 64 cached routes | `PathBudget`, `NavigationCache` |
| Encounter activation | 16 parcels, 16 snapshots and 2 admissions a slow tick; 12 infected, 4 aliens and 12 wildlife active | `UrbanEncounterDirector` |
| Dormant updates | a site's hourly step only while its stash is loaded; utilities still beyond 128 blocks | `UrbanSiteDirector`, `UtilitySystem` |
| Effects | 220 burning cells, liquid fire, particles, noise and hearing at their caps; 2 city sounds a pass; 384 decals a chunk mesh | their systems |

Every count was already bounded before this package except the chunks a journey kept, the per-frame walks over them and the threat budget's recomputation. Planning is bounded by count, not time (§17.5).

### 17.3 The memory model

A generated city is unbounded and streamed: nothing is precomputed citywide and no boundary stops a survivor. Its far chunks are dropped once more than 2,048 are held and regenerated, block for block, on return (D-241), because a city chunk is a pure function of the seed and its plans plus the edits and door states the world replays onto every chunk it generates. Nothing else lives in a chunk: soil moisture and light counters are transient and reset as a load resets them. Whatever only a loaded column runs, or a save reads back by its block, keeps its chunk and the eight around it (D-242), so a dropped chunk is exactly one a load has not brought back yet. GPU meshes go with the chunk and are already freed beyond the render radius + 3. Legacy terrain keeps every chunk: its trees and structures cross chunk borders.

### 17.4 Targets

Taken from the 60 fps frame and asserted in `UrbanCityPerformanceTest`, not calibrated: a city chunk generates and meshes within a frame (16.7 ms) at p95; streaming and the simulation together stay under half a frame (8.3 ms) at p99 at walking, sprint and flight pace; a journey's retained chunks stay within the limit. The calibrated budgets of `docs/PERFORMANCE_BENCHMARKS.md` are unchanged.

### 17.5 What remains open

- **Planning hitches.** A first-touch building plan costs 1.1–2.5 ms and is bounded only by count. Fast travel still meets a 23–30 ms slow tick about every ten seconds where it enters new regions (their sites and anchors) and 6–10 ms generation frames; walking meets a 15 ms slow tick there. A main-thread planner working ahead of the streaming edge within a time budget is the next step (D-247).
- **Save and load time** grow with the places a save names (D-246). **25:** the writers keep their read-back as the guard on the last good save (D-250).
- **The reference machine** has not run the city profile or the calibrated gates for this package.
- **The two unplannable parcels** (§16.6) were planned by package 25 (D-248).

## 18. The integrated save and regression audit (package 25)

Package 25 audited what every package saves, all together, as a durability gate rather than a first serialization pass. Decisions D-248 to D-252. The section inventory, the corruption policy and the compatibility directions are in `docs/ARCHITECTURE.md` ("Save / load"); the evidence is in VALIDATION, milestone 25.

### 18.1 What the audit checked and found

- **The inventory.** Every row of §13 was checked against its codec: its stable id and version, the generators that write it, its bounds on the writing and the reading side, what absence means, whether it is persistent or transient, and its owner and reset. No accidental persistence was found: every saved field is an irreversible fact or a value its package chose to keep (needs clocks, trust, fear, alarm, passes, debts and hunt cooldowns by D-146 and D-167). The transient state stays transient: AI brains and routes, an open container and its timer (search progress is kept, D-72), warnings, knocks and hunts in progress, a pending refuge give-up, noise timers, map sketches and presentation. No consequential fact was found unsaved: every fact of the integrated fixture comes back.
- **Fixed.** The two unplannable parcels (D-248); a core writer that could publish what no load reads (D-249); codec documentation that named only the generator a section was introduced with ("from generator N on" now); the architecture record's stale save section (rewritten).
- **Kept.** The writers' read-back (D-250), and the core's block edits and crates in hash order: a load may change that order, their contents are equal, no reader depends on it, and every extension section is written again byte for byte.

### 18.2 The integrated fixture

One economy city (generator 20, seed 20260731, Survival) carries every kind of irreversible fact a city records, each made through the game's own commands (`IntegratedCityFixture`): in the flat beside the starter refuge a cupboard searched and partly emptied, a search left half done, a container destroyed, a door opened, another barred and braced, a pane shattered, a wall breached, the street door barricaded and battered, the first floor claimed as home, water drawn from its taps and a generator fuelled and running a lamp; out in the city an infected and a Veilmaw killed where they lived; the nearest camp's rumour of the relay and three missions, one done for the hidden civilians found behind their loose panel; and the nearest raider yard's people all dead, the yard taken and the stronghold told of it and grateful once.

`UrbanIntegratedSaveTest` carries it through complete round trips; revisits in either order with three rounds of dormancy (nothing refilled, resurrected, duplicated or paid twice); loads over three populated live worlds (the same city carried on, another seed's city, a legacy world); its chunks regenerated in any order and after they were dropped; a new world of the same or another seed, which inherits nothing; failed loads over the populated world; and its sections in shuffled order. `UrbanSaveCorruptionTest` applies the corruption policy to each of its twelve city sections.

### 18.3 What stays open

- The human checks of D-80, unchanged.
- The writer's 128 MiB check has no test (D-249).
- Save and load time still grow with the places a save names (D-246, D-250), and first-touch planning hitches remain (D-247).
- The reference machine has not run package 24's figures.

## 19. The default urban rollout and final acceptance (package 26)

Package 26 made the abandoned city the ordinary new world and gathered the final evidence. Decisions D-253 to D-258; the evidence is in VALIDATION, milestone 26. Nothing was merged, pushed, tagged or released, and no real save was touched: every native run used its own data directory.

### 19.1 The owner's requirements against the production game

| Id | Requirement | Production behaviour on the default world (generator 20) | Evidence |
| --- | --- | --- | --- |
| R1 | The city covers the playable world | An unbounded streamed city planned region by region; parks, canals, courtyards and squares are its fabric; no wilderness POI, ore, cave or legacy settlement is generated | 03 and 13 plan tests, `UrbanGeneratorDispatchTest`, `WorldGeneratorFingerprintTest`; 24's long journeys |
| R2 | Enter, scavenge, avoid or confront, return | The refuge, contextual containers, infected and alien encounters, the return home and sleep | `UrbanSliceRouteTest` (10–20), `UrbanEconomyRouteTest`, `DefaultCityAcceptanceTest`; the native route |
| R3 | No trees or ore for progression | Survival needs, the first upgrade and the relay come from containers, salvage, people and repairs | `UrbanEconomyRouteTest` (the objective and four more days, no wild block touched), `UrbanMissionRouteTest`, `UrbanStarterProgressionTest` |
| R4 | Every generated building explorable | Every parcel's plan proves entrance, rooms, stairs, containers and exits before stamping; the last two unplannable parcels were planned by 25 | `AccessibilityValidator` sweeps, `UrbanEconomyAuditTest` (no parcel skipped), `RoofOpeningFallbackTest` |
| R5 | Small survivor camps | Held walk-up ground floors with residents, needs, trade, rumours and missions | `UrbanSurvivor*` tests; 16's natives |
| R6 | Guarded strongholds | Gated civic courtyards with guards, rules, theft responses and a raider yard | `UrbanStronghold*` tests; 17's natives |
| R7 | Civilians hidden in basements | Sealed cellars behind loose panels, found by sound and search | `UrbanSurvivor*`, the integrated fixture; 16's natives |
| R8 | Distinct districts and creatures | Six districts (Reclaimed Residential, Commercial Downtown, Industrial Yards, Flooded Quarter, Civic Precinct, Quarantine Zone); the Veilbound, the Veilmaw and the Breacher; Skitterwings, Murkhares and Glowdeer | 13 and 15 tests; their natives |
| R9 | Original content | Working names, generated art and sound; no reference game's names, text, art or sound | Packages 09–22's reviews; P-01 still holds the names as working names |
| R10 | Historical worlds keep their rules; the default switches at 26 | Saved generators load their own world; the wilderness stays on NEW FRONTIER as the labelled legacy world | `HistoricalV3SaveCompatibilityTest`, `HistoricalV020SaveCompatibilityTest`, `UrbanEarlierRevisionSaveTest`, the fingerprints; this package |

No mandatory requirement was found unmet, so the default was switched (D-253). Human judgement of the same requirements (D-80) has still not been given.

### 19.2 The rollout

- **The default.** `World.DEFAULT_NEW_WORLD_GENERATOR` is generator 20 (layout 17, template 7, content 4, encounter catalogue 6). `World.WILDERNESS_NEW_WORLD_GENERATOR` names generator 3, the world every release from 0.3.0 to 0.8.1 created. A save always loads the generator it recorded (D-253).
- **NEW FRONTIER** opens on the city and Survival; `W` or a click shows the wilderness, labelled "(legacy)", with its own card text and note; `S`/`C` choose the mode as before. Every opening returns to the session's world and Survival. A new city's replace notice says "Saving this new city will replace your current save." (P-05); the loading card says "Mapping the abandoned city..." (D-254).
- **The death card** in a city says the survivor wakes at their refuge with what they carried, as the respawn always did (D-256).
- **QA.** `VEYLON_GENERATOR` accepts `wilderness` (and `urban-dev`, the newest layout, today the default itself). A city scene or the save audit refuses to run without an isolated data directory, as `urban-dev` made it before; a wilderness scene keeps the wilderness unless a selector names a world; the smoke plays the session's world (D-255). `VEYLON_FRONTEND=route`/`route-load` plays the default path natively over two launches (D-257).
- **Tests** name the generator they build: the implicit-default conveniences are gone and 91 test classes name generator 3 (D-253).

### 19.3 The final acceptance scenario and where it is shown

| Step of the pack's scenario | Headless, on the default path or its proven twin | Native |
| --- | --- | --- |
| A new Survival world in the city | `DefaultCityAcceptanceTest` (NEW FRONTIER's defaults build generator 20 at the refuge, identical block for block to every route test's city) | the route's NEW FRONTIER, loading and refuge captures |
| A furnished apartment and another district; food, water, medicine, tools | The integrated fixture built on the default path; `UrbanEconomyRouteTest` (clinic, warehouse, office, security post) | the route's cupboard search; 23's economy tour (reused) |
| Infected and an alien district encounter | The fixture's dead infected and Veilmaw | 15's ecology tour, 23's economy tour (reused) |
| A camp, a stronghold, hidden civilians | The fixture: camp missions, hidden aid, the yard taken and the stronghold grateful | `urban_missions` on the default world (this package) |
| A refuge, a utility, a city mission | The fixture's claimed home, taps and generator-run lamp, and its hidden-aid mission | `urban_missions` (this package) |
| Save, load and revisit | The fixture saved, the title reached and loaded fact for fact; `UrbanIntegratedSaveTest`'s revisits on the same world | the route's two launches; 25's save audit of the integrated city (reused) |
| Continued survival without trees or ore | `UrbanEconomyRouteTest`: four days after the city answers | 23's economy tour, 2.5 days (reused) |
| Legacy saves and Creative | Genuine release saves; `DefaultCityAcceptanceTest`'s Creative city; the Creative twins of every tour | the Creative route pair; 25's release-save audits (reused) |
| The default selection | `UrbanGeneratorDispatchTest`, `UrbanDevelopmentSelectorTest`, `ModeSelectionRoutingTest` | the route's `check the default world is the city` |

### 19.4 What stays open

- **Human acceptance (D-80)** was closed on 2026-11-06 by the owner's report of their playtest and their request to release (D-259); the report gave no further detail. Package 26 is complete and ships as v0.9.0.
- **The reference machine** has not run package 24's figures or this package's smokes.
- **The release smoke** approaches a generated fortress only in the wilderness; in the city it exercises save, load, fire and storm, and the city's streaming is measured by `urban_performance` (D-255).
- **P-15** (the inverted day's temperature curve), the first-touch planning hitches (D-247), save time growth (D-246, D-250) and the writer's untested 128 MiB check (D-249) are unchanged.

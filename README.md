# VEYLON: Deep Frontier

A first-person 3D voxel survival simulation for Windows desktop, built with
**Java 25 + LWJGL 3 + OpenGL 3.3 + OpenAL + JOML**. No engine, no asset files —
terrain, creatures, UI, "textures" and **every sound** are procedural programmer art
(audio is synthesized at startup).

You crash-land on Veylon, a living planet fragment: chunk-based voxel terrain with six
biomes, caves, ores and procedural points of interest; dynamic weather, **seasons**,
temperature, cellular water, spreading fire and growing plants; wildlife that leaves
**tracks and blood trails**, smells your scent and hears your noise; a deep body
simulation with **nutrition, spoilage, medical conditions, gear and carry weight**; an
NPC camp with **trust tiers, quests, settlement upgrades and raids**; and a long-term
goal — find blueprints, master the crafting stations and **repair the distress beacon**.

---

## Requirements

- Windows 10/11
- JDK **25** on `PATH` (`java -version` should report 25.x). The Gradle wrapper downloads
  Gradle 9.1 automatically on first run.
- A GPU/driver with OpenGL 3.3. Audio uses OpenAL (bundled native); if no audio device
  exists, the game runs silently.

> **Corporate proxy note:** `gradle.properties` sets
> `javax.net.ssl.trustStoreType=Windows-ROOT` so Java uses the Windows certificate store.
> If the very first wrapper download fails with an SSL error, run once with:
> ```powershell
> $env:GRADLE_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
> ```

## Run (PowerShell)

```powershell
.\gradlew.bat run
```

Saves go to `saves\veylon.sav` (binary format **v2** — saves from the old prototype are
not loadable; start a new world).

## Build & distribute

```powershell
.\gradlew.bat build      # compile + jar
.\gradlew.bat distZip    # build\distributions\veylon-0.1.0.zip
.\gradlew.bat fatJar     # build\libs\veylon-0.1.0-all.jar  -> java -jar to run
.\gradlew.bat jpackage   # build\jpackage\Veylon\Veylon.exe (self-contained app-image)
```

Headless smoke test (auto-exits; exercises save/load v2, equipment, fire, storm):

```powershell
$env:VEYLON_SMOKE = "30"; .\gradlew.bat run
```

---

## Controls

| Key | Action |
| --- | --- |
| `WASD` | Move |
| Mouse | Look |
| `Space` | Jump / swim up |
| `Left Shift` | Sprint (drains stamina, makes noise) |
| `Left Ctrl` | Crouch — stealth (predators detect you at half range) and **read animal tracks** |
| `LMB` (hold) | Mine block / attack (crouched attacks hit 40% harder) |
| `RMB` | Place block, **eat / drink / apply medicine / equip gear**, use station |
| `F` | Interact: talk, harvest bush/herbs/**carcass**, cook/boil/fuel campfire, drying rack, rain collector, **sleep** on bedroll, fill waterskin, repair **beacon** |
| `E` | Inventory + **equipment slots** (click gear slot to equip/unequip) |
| `C` | Crafting — `Q`/`R` cycle station tabs, `W/S` select, `Enter` craft |
| `1-9`, scroll | Hotbar selection |
| `Tab` | Simulation panel |
| `M` | Map (camp, **discovered POIs**, beacon) |
| `P` | Pause simulation |
| `Esc` | Pause menu / close screen / wake up early |
| `F3` | Debug overlay |
| `F5` / `F9` | Save / Load |

## The survival loop

1. **First day:** punch trees, gather fiber and berries, craft a spear and campfire.
   Fill your waterskin (`F` at water) — it's *dirty*; **boil it at a campfire** or you
   risk food poisoning.
2. **Stay healthy:** wolf bites cause **bleeding** (bandage it, or it festers into a
   feverish **infection**); falls cause **sprains** (splint); fire causes **burns**
   (herbal poultice); bad food causes **poisoning** (medicine). Eat both meat *and*
   plants — the protein/vitamin bars gate your health regen and stamina.
3. **Food logistics:** raw meat spoils in minutes. Cook it (lasts longer) or build a
   **drying rack** — dried meat and berries keep for days. Cold biomes slow spoilage.
4. **Hunt properly:** animals drop **carcasses**. Craft a bone knife to skin them for
   meat, **hide** and bone. Carrying raw meat or bleeding makes wolves smell you;
   sprinting and mining make noise. Crouch to stalk — and to read tracks.
5. **Gear up:** tan hide into leather (**tannery**), sew coats, boots, hood, pants and a
   **backpack** (+14 kg). Insulation beats the cold, the rain cloak beats wetness, armor
   blunts attacks. Everything has **durability**. Watch your **carry weight** — overload
   and you can't sprint.
6. **Stations:** Workbench → tools, racks, furnace, tannery, herbalist bench, map table,
   rain collector, bedroll, waterskin. **Furnace** smelts copper/iron (needs coal).
   **Anvil** forges iron tools, weapons and armor. **Herbalist bench** brews antiseptic,
   poultices and medicine. **Campfire** cooks, boils and chars logs to charcoal.
7. **Shelter & sleep:** build a roof and walls — being *Indoors* blocks rain and wind
   chill. Sleep on a **bedroll** at night (or fatigued); sleeping cold, wet and exposed
   risks sickness, and a campfire indoors without ventilation causes **smoke inhalation**.
8. **Explore:** the world generates **crash debris, research pods, ancient ruins,
   predator dens and supply caches**. Discovery marks them on the map. Pods and caches
   hold **blueprint fragments** — decode them at a **map table** to unlock the rain
   cloak, iron armor and the **beacon frame**.
9. **The camp:** trade, gift, and take **camp requests** (food, wood, medicine, ore,
   predator hunts, scouting). Trust tiers: Wary → Neutral → **Friendly** (camp beds,
   medic heals you, trade discounts) → **Allied** (supply gift, beacon calibration).
   The camp grows: palisade and watch post, beds and storage, finally a medical tent.
   Defend it from **wolf raids and scavenger attacks** — or watch its stocks bleed.
10. **Endgame:** forge a **Beacon Frame** (anvil + blueprint), place it, install a
    **signal crystal** (mine the resonant core in ancient ruins), wire it with copper
    ingots, and get the camp's calibration codes at **Allied** trust. Light it up.

## What's simulated

- **Body**: health, hunger, thirst, stamina, body temperature, fatigue, wetness,
  **protein/vitamin nutrition**, and seven medical conditions (bleeding, infection,
  sprain, burns, food poisoning, sickness, smoke inhalation) — each with causes,
  symptoms and treatments.
- **Items**: per-stack **spoilage timers** (temperature-dependent), tool/gear
  **durability**, item weight and **carry capacity**, five equipment slots with
  insulation / rain resistance / armor stats that degrade.
- **World**: 6 biomes, caves, ores, lakes, **5 POI types**, wild herbs, deterministic
  seed; **seasons** (Mild/Wet/Frost/Dry, 6 days each) bias weather, temperature, growth
  and wildlife.
- **Shelter**: roof and wall-enclosure detection — affects wetness, storm wind chill,
  sleep quality and indoor campfire smoke.
- **Wildlife**: Glowdeer, Ashwolves, Skitterwings, **Murkhares, Thornhorns (charge when
  wounded), Gloomstalkers (cave predators)**. Predators hear noise, smell blood/meat,
  follow **blood trails**, scavenge **carcasses**, den at POIs, and fear fire/light.
  Animals leave fading **footprints** readable while crouched.
- **NPC camp**: jobs (guard, hunter, gatherer, medic), needs, sickness, quests,
  **staged settlement upgrades**, predator/scavenger **raids**, trust tiers with real
  perks, and a trader who buys hide for blueprint fragments.
- **Events**: storms with lightning fires, cold snaps, heat waves, drought, berry bloom,
  predator migration, **toxic fog (stay indoors), ashfall (kills growth + darkens sky),
  meteor showers, camp illness, raids** — all touching real systems.
- **Audio**: OpenAL with fully synthesized sound — material footsteps, mining/combat,
  eating/drinking, wolf howls and growls, bird chirps, deer calls, thunder, tool breaks,
  plus crossfading ambience loops (rain, wind, fire crackle, cave drone, night crickets,
  beacon hum).
- **Visuals**: particles (block dust, smoke, embers, rain splashes, 3D snow and ash,
  blood, cold breath), first-person held item with swing/bob, entity walk-bob and
  stalking postures, bird wing flaps, damage/cold/smoke vignettes, beacon light column,
  toxic-fog green cast.

## Architecture

```
com.veylon
  Game / Main                 - loop, input, player actions, tick wiring
  engine/                     - Window, Input, Camera, ShaderProgram, Mesh, Renderer,
                                UiRenderer, AudioManager (OpenAL, synthesized),
                                ParticleSystem
  world/                      - World, Chunk, BlockType, Biome, WorldGenerator (+POIs),
                                ChunkMesher, Raycaster, Poi, RackBatch
  simulation/                 - SimulationScheduler, Time, Weather, Temperature, Water,
                                Fire, Plant, Event, Season, Shelter, ItemCondition
  entity/                     - Entity, Player, Creature, Npc, EntityManager,
                                Affliction, Carcass, Track
  ai/                         - CreatureAI (perception/tracking), NpcAI (jobs/raiders),
                                FactionSystem (quests/upgrades), Quest, Steering
  item/                       - ItemType (+ItemProps), ItemStack, Inventory, Recipe,
                                CraftingSystem, Station, EquipSlot, FoodGroup, ToolKind
  ui/                         - Hud, Inventory/Crafting/Crate/Npc/Map screens,
                                PauseMenu, DebugOverlay, SimulationPanel, EventLog
  save/                       - SaveSystem (binary v2: seed + all deltas)
  util/                       - Noise, Vec3i, FloatList, MathUtil
```

Performance design: rendering/input per frame; movement, needs and entity AI at 20 Hz;
weather/fire/water/temperature/shelter at 2 Hz; plants/events/faction/spawning/spoilage
every 10 s. Chunk meshes rebuild on a per-frame budget; far meshes are released.
Particles are CPU-simulated (cap 4000) and drawn as lit cubes.

## Save format

Binary v2: seed, time, weather, full player state (stats, nutrition, afflictions,
inventory and equipment with durability/freshness, blueprints), changed blocks,
campfire fuel, crates, drying racks, rain collectors, discovered POIs, beacon position
and repair stage, faction (trust, stocks, upgrade stage, active quest), NPCs (jobs,
sickness, raiders), creatures, carcasses, events and the log tail. **v1 prototype saves
are rejected with a console message** — no migration.

## Known limitations

- Lighting is sky-column + point-light falloff baked at mesh time, not flood-fill.
- Simple face-culled meshing; visuals are flat-shaded colored boxes.
- Water is full-cell cellular flow without levels; bounded by sea level by design.
- No dropped-item entities (drops go straight to your inventory).
- One save slot. Creature AI uses steering + jump, not pathfinding.
- Crouching lowers the camera and slows you but doesn't shrink the collision box.
- Sleeping fast-forwards the clock (~3 game hours per real second) rather than
  simulating the whole night at full rate.

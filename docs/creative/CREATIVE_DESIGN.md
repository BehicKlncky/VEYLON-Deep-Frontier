# Stone & Sky - Creative design

Recorded 2026-09-11 from the supplied implementation brief. The research table
is supplied design input; no external source was contacted during this local work.
Implementation follows D1-D8 and R1-R27 below. Planned controls become available
only at their corresponding milestone.

### 3.1 How other games activate and shape Creative play

| Game | Activation | Consequence / marking | Mechanics worth copying |
| --- | --- | --- | --- |
| Minecraft | Chosen at world creation; `/gamemode creative` with cheats; Java F3+F4 switcher | Bedrock: opening a world in Creative permanently disables achievements | Double-tap jump to fly, jump/sneak to rise/descend; invulnerable; no hunger; instant breaking with a 0.3 s repeat; no drops; unlimited items, no durability loss; tabbed searchable inventory; pick block; mobs passive toward the player |
| Satisfactory | "Creative Mode" on New Game, or a checkbox when loading a save, behind a warning | Permanent per save (cannot be disabled, achievements off); saves show a pencil icon | Individual toggles later from the pause menu: no build cost, flight, god mode, give items, disable creatures, progression |
| Subnautica | Chosen when creating a save; later only via console | — | No health/oxygen/food/water, free crafting, invulnerable vehicles |
| Terraria (Journey) | Chosen for character and world at creation | Journey characters only enter Journey worlds | Power menu: god mode, time freeze/set, weather, spawn rate, research → duplication |
| Valheim | World modifier presets at creation (Casual, Hammer / no build cost); `devcommands` console | Some modifiers block achievements while active | No build cost, passive mobs, raids off |
| 7 Days to Die | Console `cm` (creative menu, U) and `dm` (debug: god mode, fly) | Achievements off while active | Creative item menu |
| Astroneer | Enabling Creative on a save | Permanent mark; players complain it happened without a clear warning | Missions and achievements disabled |
| Vintage Story | World creation or `/gamemode creative` with privileges | — | Creative inventory, instant breaking, fly/noclip cycling |

Sources: minecraft.wiki/w/Creative, satisfactory.wiki.gg/wiki/Advanced_Game_Settings,
subnautica.fandom.com/wiki/Game_Modes, terraria.wiki.gg/wiki/Journey_Mode,
valheim.fandom.com/wiki/World_Modifiers, 7daystodie.fandom.com/wiki/Creative_Menu,
astroneer.fandom.com/wiki/Creative_Mode, wiki.vintagestory.at/Creative_mode.

Lessons applied:

1. The **primary** path is choosing the mode when a world is created.
2. Switching an **existing** world is valuable but must be **explicit, confirmed and visibly
   recorded** (Satisfactory, Minecraft Bedrock), never silent (Astroneer backlash).
3. Consoles and debug chords are the usual third path. VEYLON has no console, and an
   accidental hotkey could permanently mark a save, so no gameplay hotkey switches modes.
4. Minecraft's ruleset is the accepted core: invulnerable, no needs, flight, instant breaking
   without drops, unlimited items without wear, searchable catalog, pick block, creatures
   that ignore the player.
5. World controls (Terraria, Satisfactory) are highly valued by builders.

### 3.2 Decisions (implement as specified; record them in `docs/creative/CREATIVE_DESIGN.md`)

- **D1 Activation.** (a) Title → `NEW FRONTIER` opens a mode selection screen (Survival
  default, Creative). (b) Pause → `[G] Game mode` switches an existing world: the first switch
  to Creative requires a confirmation that states the world will be permanently marked as a
  Creative world; later switches in either direction need a simple confirmation; the mark
  never clears. (c) `VEYLON_GAME_MODE=survival|creative` for automated runs only. No console
  and no gameplay hotkey switch.
- **D2 Fixed ruleset.** Creative is one mode with fixed abilities (no per-rule toggles in
  0.7.0), modeled as player abilities so toggles remain possible later.
- **D3 Imperceptible, not lawless.** AI treats a Creative player as imperceptible: nothing
  detects, pursues, flees from, charges, aims at or attacks them. Rules that do not depend on
  perception — reputation, trust, theft and vandalism attribution, bounty, ownership — apply
  unchanged (Minecraft likewise keeps villager reputation in Creative). Consequences that
  require an NPC to perceive the player (alarms, pursuit, witness reactions) do not occur.
  Proximity-driven simulation (chunk streaming, settlement activation, spawning around the
  player, discovery, quests) is unchanged.
- **D4 Use-up versus transform.** In Creative, actions that use an item up for its own effect
  are free (placing, firing, throwing, eating, drinking, applying medicine), and carried items
  neither wear nor spoil. Actions that transform, trade or move items keep Survival rules
  (crafting, cooking, boiling, fueling, drying, smelting, beacon installation, trade, gifts,
  quest delivery, restitution, crate transfers, equipping).
- **D5 Persistence and compatibility.** Mode, permanent mark and flight state live in a new
  extension section; a missing section means Survival and unmarked. v0.6.0 skips unknown
  sections, so it loads a Creative save as Survival (document; cannot be fixed
  retroactively). Saves that contain palette items (v0.6.10) cannot be opened by older builds
  (clean load failure; document).
- **D6 Flight keeps collision.** No noclip or spectator mode.
- **D7 One save slot remains.** A new world of either mode replaces the single slot on its
  next save; the new-frontier screen says so when a save exists.
- **D8 Out of scope for 0.7.0.** Command console, spectator/noclip, multiplayer or permissions,
  achievements, multiple save slots, world-edit tools (fill, copy, paste), per-rule difficulty
  customization, a Survival "peaceful" option, free crafting, dropped item entities, showing a
  save's mode on the title screen before loading.

---

## 4. Product requirements (reference these IDs in tests, commits and the plan)

**Mode and activation**

- **R1** Every world has a game mode, `SURVIVAL` (default) or `CREATIVE`, and a permanent
  `creativeMarked` flag.
- **R2** Title: `NEW FRONTIER` and `N` open a worldless new-frontier screen. Choose Survival or
  Creative with the mouse, arrows/Tab or `S`/`C`; `Enter`/START begins loading; `Esc`/BACK
  returns to the title. When a save file exists, show that saving will replace it.
- **R3** Pause: `[G] Game mode` opens a paused screen that owns every key (Escape, F5, F9, Q, O,
  V never reach the router or gameplay). The first Survival → Creative switch on an unmarked
  world shows the permanent-mark warning. `Enter`/`Y` confirms; `Esc`/`N` cancels and changes
  nothing.
- **R4** Entering Creative: abilities apply immediately; survival stats are restored (health to
  max, hunger/thirst/stamina 100, body temperature normal, fatigue, wetness and smoke 0,
  protein and vitamins 100, afflictions cleared, pending fall damage cleared); AI memory of the
  player is cleared; the world is marked; one log line.
- **R5** Leaving Creative: flight ends and fall distance restarts at the switch height; stats
  stay as they are; perception resumes; the mark stays; one log line.
- **R6** The mode is visible: a Creative HUD badge, mode and mark in the pause header,
  mode/mark/flight in the F3 overlay, and a "Creative world" note on the victory overlay.
- **R7** Terrain, settlements, camp, starter kit and initial wildlife are identical for the same
  seed in both modes.

**Body**

- **R8** A Creative player takes no damage from any source (creatures, humans, projectiles,
  explosions, fire, falls, starvation, dehydration, temperature, afflictions, smoke, toxic fog,
  sleep sickness, food poisoning) and cannot die. No damage feedback: no flash, hurt sound,
  damage log line, knockback or armor wear.
- **R9** No need drains; no affliction can be added; no encumbrance, sprain, fatigue or hunger
  limits on movement or sprinting; sprinting costs no stamina.
- **R10** Observations used by HUD, ambience and music (biome, `envTemp`, `exposedToSky`,
  shelter) keep updating.

**Perception**

- **R11** Creatures and humans never detect, pursue, flee from, charge, ambush, aim at or attack
  a Creative player; player-attributed perception events (noise, scent) are not produced while
  the player is Creative.
- **R12** Non-perception consequences, proximity simulation and friendly interactions are
  unchanged (D3).

**Flight**

- **R13** In Creative only, double-tapping Space within 0.30 s toggles flight. Space rises, Left
  Ctrl descends, Left Shift flies faster, no vertical input hovers. No gravity, stamina,
  footsteps, view bob or noise while flying; collision retained; touching the ground while
  descending lands.
- **R14** A flying player cannot enter unloaded chunk columns (they read as `AIR`); flight
  speeds and an altitude ceiling are chosen from measurements.
- **R15** Flight state persists in Creative saves.

**Catalog**

- **R16** In Creative, `E` opens the Creative catalog: every `ItemType` in exactly one category,
  icon grid with tooltips, scrolling, category tabs, a search field (case-insensitive with
  `Locale.ROOT`; all whitespace-separated terms must match the display name or enum id), and an
  `INVENTORY` tab with the inventory, gear slots and a trash slot.
- **R17** LMB grants a fresh full stack, RMB one item, preferring the selected hotbar slot when it
  is empty, otherwise the first empty slot, otherwise an "Inventory full" notice. Hovering an
  entry and pressing `1`–`9` (search not focused) puts a full stack into that hotbar slot,
  replacing its content. Stacks are created through the normal item paths (full durability and
  freshness, empty magazine).
- **R18** While the catalog is open it owns every key: `E` closes it when search is not focused;
  `Escape` first unfocuses search, then closes; `F5`/`F9` never save or load.

**Building**

- **R19** In Creative, LMB breaks any block with `hardness >= 0` immediately on press and repeats
  every 0.30 s while held. Tool requirements are ignored; there are no drops and no tool wear;
  container and state cleanup (crate contents, rack batch, campfire fuel, collector water,
  lantern, beacon, powder-keg fuse) is unchanged.
- **R20** Creative placement never consumes the held stack; every placement validity rule is
  unchanged.
- **R21** In Creative, the middle mouse button picks the targeted block's item form: select it if
  it is already in the hotbar, else place a full stack in the first empty hotbar slot, else
  replace the selected slot; show a short notice when the block has no item form.

**Unlimited use**

- **R22** In Creative: bows fire without arrows (selected type, default `ARROW`); firearms reload
  without reserve ammunition (normal reload time and magazine use); bombs, food, drinks and
  medicine are not used up; tools, weapons and gear keep their durability; carried stacks do not
  spoil. Transform, trade and transfer actions keep Survival rules (D4).
- **R23** HUD weapon readouts show unlimited ammunition in Creative using ASCII text (do not
  assume the font has `∞`).

**Building palette**

- **R24** Inert building blocks without an item form gain appended, Creative-only item forms
  (catalog, placement, pick block, icons, save round trip). Gameplay-state blocks are excluded,
  each with a recorded reason.

**World controls**

- **R25** In Creative, pause → `[T] World controls`: time presets (Dawn, Noon, Dusk, Midnight;
  forward only), freeze daylight cycle, weather presets with a weather lock, and wildlife
  spawning on/off. Controls only apply in Creative, are cleared when leaving Creative, and
  persist in a new section.

**Quality**

- **R26** Survival parity: every pre-existing test and every v0.6.1 parity test passes unchanged
  at every milestone; performance budgets are unchanged and met.
- **R27** Every new player-visible screen and string fits at 1280×720 and 1920×1080 with the
  default UI scale and is captured natively and inspected.

---


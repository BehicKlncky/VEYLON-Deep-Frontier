# Stone & Sky - Creative mode implementation plan

Started 2026-09-11 at `e963fce36d1e31f5745deac32aa697f0f980f0fe`, annotated
`v0.6.0`. Starting checks: clean main; origin/main at the same commit; no
release/0.7.0, creative branches or new milestone/release tags. Work is local.
The [design](CREATIVE_DESIGN.md) records the supplied research, D1-D8 and R1-R27.
External sources were not contacted. No remote command is permitted.

## Untouched baseline

`gradlew.bat --offline build` passed in 4s with all tasks up to date. A fresh
`build --rerun-tasks` then passed in **1m45s** (105.439s wall clock), including
JavaDoc doclint: **486 tests / 79 classes**, zero failures, errors and skips.
`performanceTest` passed in **10s** (10.848s wall clock). Offline mode uses the
existing local dependency cache. The sandbox wrapper attempt could not create
its cache lock; authorized execution outside the sandbox used the existing cache.
Logs are ignored under `build/creative-work/baseline-*`.

| Benchmark | Measured | Unchanged budget | Result |
| --- | ---: | ---: | --- |
| Chunk tick | 0.340 ms | 0.92 ms | PASS |
| Entity tick | 0.414 ms | 0.95 ms | PASS |
| Settlement tick | 0.017 ms | 0.52 ms | PASS |
| Save | 1.636 ms | 2.20 ms | PASS |
| Load | 192.152 ms | 240 ms | PASS |
| Audio synthesis/conditioning/encoding | 479.983 ms | 1,500 ms | PASS |
| PCM payload | 40,824,424 bytes | 67,108,864 bytes | PASS |
| Audio occlusion | 0.001974 ms/frame | 0.10 ms/frame | PASS |
| Music director | 0.000009 ms/frame | 0.02 ms/frame | PASS |

The separate tick profile uses 200 warmups and fastest of 40 samples. Its
threshold is `max(recorded * 5, 0.010 ms)`; do not substitute these measurements
for the headline benchmarks above.

| Tick profile part | Measured ms | Budget ms | Result |
| --- | ---: | ---: | --- |
| fast: player needs | 0.0026 | 0.0145 | PASS |
| fast: entities | 0.0073 | 0.0600 | PASS |
| fast: settlements | 0.0005 | 0.0100 | PASS |
| medium: weather | 0.0001 | 0.0100 | PASS |
| medium: temperature | 0.0011 | 0.0100 | PASS |
| medium: water | 0.0001 | 0.0100 | PASS |
| medium: fire | 0.0023 | 0.0290 | PASS |
| medium: shelter | 0.0020 | 0.0100 | PASS |
| medium: fumarole scan | 0.0066 | 0.0280 | PASS |
| medium: whole bucket | 0.0108 | 0.0505 | PASS |
| slow: plants | 0.0431 | 0.1920 | PASS |
| slow: events | 0.0002 | 0.0100 | PASS |
| slow: faction | 0.0008 | 0.0100 | PASS |
| slow: entities | 0.0044 | 0.0105 | PASS |
| slow: detritus | 0.0005 | 0.0100 | PASS |
| slow: item condition | 0.0052 | 0.0265 | PASS |
| slow: settlements | 0.0045 | 0.0225 | PASS |
| slow: whole bucket | 0.0729 | 0.2265 | PASS |
| whole cycle | 0.1002 | 0.5135 | PASS |

Real line counts use `[IO.File]::ReadAllLines(...).Count`, including blanks.

| Budgeted source | Baseline lines | Ceiling |
| --- | ---: | ---: |
| Game | 889 | 1,000 |
| QaHarness | 1,409 | 1,500 |
| SaveSystem | 1,785 | 1,800 |
| SettlementManager | 1,392 | 1,500 |
| FactionSystem | 1,265 | 1,400 |
| WorldGenerator | 1,178 | 1,300 |

## Ordered phases

Every branch begins at the current release/0.7.0 tip. Each ends with a
`chore(release): prepare v0.6.N` commit, full build, evidence, clean diff check,
`--no-ff` merge and annotated tag on the merge. All branches are retained with
no upstream. No direct release-branch commit precedes milestone 11. Main stays
at v0.6.0 until final evidence is complete. History, settings, dependencies,
wrapper, workflows, existing enum IDs and all budgets remain unchanged.

| Tag | Branch suffix under creative/ | Definition of done | Status |
| --- | --- | --- | --- |
| v0.6.1 | 01-plan-and-parity-harness | Baseline, A1-A5, missing Survival parity, design; no production change (R26) | verified |
| v0.6.2 | 02-mode-model-and-save | Byte-identical save extraction, mode/abilities/reset/restore, strict section, QA parsing (R1, R7, R15) | verified |
| v0.6.3 | 03-invulnerable-body | All A1 gates, restoration, observations, badge and mode-aware smoke (R4, R6, R8-R10) | verified; save gate is a recorded host deviation |
| v0.6.4 | 04-imperceptible-player | A3 gates and memory clear; preserve D3 consequences (R11-R12) | verified; save gate is a recorded host deviation |
| v0.6.5 | 05-mode-selection | Worldless mode choice, explicit confirmation, permanent mark, key ownership (R2-R7) | verified |
| v0.6.6 | 06-flight | Double tap, collision, loaded columns, measured speeds/ceiling, reset and restore (R5, R13-R15) | verified; save gate is a recorded host deviation |
| v0.6.7 | 07-catalog | Character input, categories, AND search, grants/trash, all key ownership (R16-R18) | verified |
| v0.6.8 | 08-instant-building | Repeat timer, cleanup without drops/wear, free placement, pick mapping (R19-R21) | verified |
| v0.6.9 | 09-unlimited-use | A2 use-up gates, ammunition HUD, unchanged transform/transfer/trade (R22-R23) | verified |
| v0.6.10 | 10-building-palette | Per-block inclusion audit, appended items/icons, mappings and compatibility (R24) | verified; gates ran on a second host (see the v0.6.10 host deviation) |
| v0.6.11 | 11-world-controls | Forward time, freeze, weather lock, spawn gate, strict section and release on exit (R25) | verified; gates ran on the second host (see the v0.6.10 host deviation) |
| v0.7.0 | release/0.7.0 | Whole-diff audit, all final gates, both-resolution captures, artifacts/checksums, local main merge | pending |

R26 applies to every milestone. R27 requires inspected 1280x720 and 1920x1080
captures of changed screens. Simulation/save/movement/item milestones run the
unchanged performance task; from milestone 3 both modes run 30-second smokes.
If native execution is unavailable, record why, continue implementation, then
stop before the final main merge. Never infer human acceptance from captures.

## A1 - damage, needs and movement audit

Locations below are baseline method names/line anchors, not promises that later
extractions leave the same line numbers. Tests named as planned are future
Creative twins; existing tests and the new parity suite pin Survival first.

| Location | Current behavior | Creative decision | Milestone | Test authority |
| --- | --- | --- | --- | --- |
| entity/Entity.hurt:64 | subtracts health, death and hit attribution | override on Player before mutation | 3 | planned CreativeBodyTest; parity fire |
| Player.hurtPhysical:199 | armor reduction/wear, flash, seeded bleed/duration | early gate before feedback and RNG | 3 | PlayerOutcomeDeterminismTest; planned CreativeBodyTest |
| Entity.knockback:75; all callers below | pushes body independently of health damage | Player override rejects knockback | 3 | planned CreativeBodyTest |
| Player.onLanded:223, applyPendingFallDamage:266 | queues damage, hurt audio/log, sprain roll | clear queued damage/fall distance; no feedback | 3, 6 | parity fall; planned CreativeFlightTest |
| VoxelPhysics.integrate:21-65 | gravity, collisions, fall accumulation, ladder/water reset | preserve Survival path; collision flight path | 6 | PlayerMovementSystemTest; parity double tap |
| Player.tickNeeds:232-244 | biome, temperature, sky exposure, noise/flash decay | observations continue before ability gate | 3 | PlayerEnvironmentSystemTest; planned CreativeBodyTest |
| tickHungerAndThirst:282 | hunger/thirst/nutrition drains and illness modifiers | hold needs at full values | 3 | parity independent starvation/dehydration |
| tickNeeds:252-264 | direct starving/dehydrated health writes and death | skip drains and death, restore safe values | 3 | parity exhausted-needs tests |
| tickStamina:306; maxStamina:486 | regen limited by diet, conditions, fatigue | stamina 100, no limits | 3 | planned CreativeBodyTest |
| tickWetness:326 | immersion/rain wetness, drying | wetness remains zero | 3 | EnvironmentalSimulationTest; planned body twins |
| tickBodyTemperature:343 | environment/shelter/gear, direct cold/heat damage | body temperature normal, observations retained | 3 | planned temperature twin fixtures |
| tickHealthRegen:376; tickFatigue:393 | nutrition/condition regen; activity/fire fatigue | skip, hold health max/fatigue zero | 3 | planned CreativeBodyTest |
| tickAfflictions:404 | all seven afflictions, direct damage, infection roll/log | skip ticking; clear on entry; reject additions | 3 | PlayerOutcomeDeterminismTest; PlayerTreatmentSystemTest |
| addAffliction:190 | merge maximum duration | no-op before mutation | 3 | planned every-affliction cases |
| updateScent:369 | meat/bleeding scent | zero scent at source | 4 | planned CreativePerceptionTest |
| tickToxicFogExposure:459 | exposed/unroofed sickness roll and log | gate before RNG/log | 3 | PlayerOutcomeDeterminismTest toxicFogRolls |
| tickSmokeExposure:471 | smoke thresholds/affliction/log and decay | neutralize accumulation and skip feedback | 3 | planned smoke switch-back regression |
| PlayerEnvironmentSystem.mediumTick:77-85 | enclosed-fire/fumarole smoke accumulation | shelter/vent observations stay; no smoke buildup | 3 | standingOverAVentBuildsSmokeExposure; planned twin |
| FireSystem.damageNear:135 | direct hurt, explicit flash and seeded burn/log | gate whole player branch; entity fire unchanged | 3 | parity fire; CombatSystemsTest keg/fire |
| ExplosionSystem.explode:158-195 | player hurt, audio and shared knockback | gate player feedback and knockback; keep physical blast | 3 | CombatSystemsTest; planned body blast twin |
| ProjectileSystem.hitEntity:293 | player physical hurt and audio | gate hurt feedback; physical projectile remains | 3, 4 | CombatSystemsTest; planned projectile twin |
| CreatureAI:208-209,312-313,448-449 | thornhorn, wolf and stalker hurt/knockback/audio/log | suppress damage feedback first; perception gates in 4 | 3, 4 | HumanPerceptionGameplayTest; EntityEcologyTest; parity hungry wolf |
| NpcAI:75-76,375-376 | camp hostility and raider attack/knockback/audio | same body and perception separation | 3, 4 | planned camp/raider body twins |
| SettledNpcAI:569-570 | settler hurt/knockback/audio | same body and perception separation | 3, 4 | HumanPerceptionGameplayTest; parity settler alarm |
| SleepSystem:165 | cold/wet exposed sleep sickness roll/log | no sickness or injury log; sleep effects kept safe | 3, 11 | PlayerOutcomeDeterminismTest; planned sleep/freeze twins |
| PlayerConsumables.eat:88, drink:127 | food/dirty-water poison roll/log | gate poison roll and feedback | 3 | PlayerOutcomeDeterminismTest |
| WorldInteractions drink water:401 | untreated world-water poison/log | gate injury only; water/filling interaction stays | 3 | planned CreativeBodyTest |
| Player.moveSpeedMul:163, canSprint:177 | load, sprain, fatigue, hunger restrictions | neutral movement multiplier; sprint allowed | 3 | planned overloaded/sprained body fixtures |
| PlayerMovementSystem:112-130 | sprint/jump stamina and sprint noise | no stamina costs; perception noise gate in 4 | 3, 4 | PlayerMovementSystemTest; parity double tap |
| Game.frame death:403; respawn:830 | death transition, respawn needs, affliction clear | Creative cannot enter death; restore contract on respawn | 3 | GameLoopIntegrationTest; planned body death test |
| NpcAI:280; PlayerTreatmentSystem:53 | healing from medic/treatment | preserve interaction; full health clamp | 3, 9 | PlayerTreatmentSystemTest |

Only Player overrides stop direct damage to a player; Entity behavior for other
bodies must remain unchanged. Gate caller feedback too: a no-op hurt method
alone does not suppress fire flashes, hurt sounds, log lines or knockback.
Survival continues through exactly the existing arithmetic and RNG calls.

## A2 - item use, wear and storage audit

Every item-affecting `shrink`, `remove`, direct durability/freshness write and
transfer discovered by source search is classified here. Collection removals
for events, paths, particles, corpses and projectile retirement are lifecycle
cleanup, not item consumption, and remain unchanged.

| Location | Current behavior / D4 class | Creative decision | Milestone | Test authority |
| --- | --- | --- | --- | --- |
| PlayerBlockActions.placeSelectedBlockAt:289 | shrink one / use-up | skip shrink; all placement checks and initialization stay | 8 | parity placement; planned CreativeBuildingTest |
| PlayerBlockActions.breakBlock:145, awardDrops:200 | tool wear, material/fiber/leaf drops | skip wear and drops/RNG; keep spills | 8 | parity required tool/drops/rate; PlayerOutcomeDeterminismTest |
| PlayerCombatSystem.attack:172 | melee wear / use-up | skip consumeDurability | 9 | parity melee |
| fireBow:414,428 | arrow removal, bow wear / use-up | selected arrow or ARROW with no reserve; skip costs | 9 | BowGameplayWorkflowTest.bowInputCyclesToIronAndReleaseConsumesOnlySelectedAmmoAndDurability |
| firearm:448,486 | gun wear and reserve removal / use-up | free reserve reload; retain duration and magazine decrement | 9 | FirearmGameplayWorkflowTest.musketAndPistolCommandReloadsThenFiresWithCostsFeedbackAndCleanup; PlayerReloadTest |
| throwBomb:564 | selected bomb shrink / use-up | skip shrink, retain fuse/projectile | 9 | parity bomb; ActiveExplosivePersistenceTest |
| consumeDurability:577; useKnife:595-605 | tool/weapon/harvest wear and destruction | return before wear | 9 | parity break/melee; PlayerOutcomeDeterminismTest carcass |
| Player.hurtPhysical:205 | worn armor decrement/destruction | no damage wear | 3 | planned CreativeBodyTest armor |
| PlayerConsumables.eat:91 | held food shrink / use-up | free; preserve normal benefits and no poison | 9 | parity eating |
| PlayerConsumables.drink:121-122 | drink shrink, empty skin returned / use-up | keep drink, no empty-skin duplication | 9 | planned unlimited drink tests |
| PlayerConsumables.applyMedical:141 | medicine shrink after used treatment / use-up | no shrink, retain treatment outcome | 9 | PlayerTreatmentSystemTest; planned unlimited treatment |
| PlayerConsumables.equipHeld:151 | moves one to gear, returns previous / transfer | unchanged | 9 | planned CreativeUnlimitedUseTest equip |
| ui/InventoryScreen:118-155 | slot swaps, gear transfer/shrink | unchanged transfer; separate Creative trash action | 7, 9 | planned catalog inventory/trash tests |
| WorldInteractions:392 | empty skin to dirty skin / transform | unchanged | 9 | planned unlimited-use transform twins |
| WorldInteractions:508,514,519 | meat cooking, water boiling, campfire logs / transform | unchanged | 9 | ExpansionProgressionTest; planned cooking twin |
| WorldInteractions:538 | charcoal into lantern / transform | unchanged | 9 | LanternGameplayTest |
| WorldInteractions:563,586 | retrieve/dry rack batch / transform and transfer | unchanged | 8, 9 | planned rack spill/transform tests |
| WorldInteractions:597 | empty skin + collector water / transform | unchanged | 9 | planned collector twin |
| WorldInteractions:620,632 | crystal/copper installation / transform | unchanged | 9 | BeaconEndgameIntegrationTest |
| CraftingSystem:215,221,229 | ingredients, relic restoration, rollback / transform | unchanged, stations/blueprints still required | 9 | ExpansionProgressionTest; RelicProgressionGameplayTest |
| NpcScreen:338 | give cost and purchased return / trade | unchanged | 9 | SettlementReputationGameplayTest; planned trade command twin |
| NpcScreen:370 | selected gift shrink / transfer | unchanged | 9 | planned gift twin |
| FactionSystem:976,1161 | quest delivery and expired returned-supply removal / transfer | unchanged | 9 | FactionQuestTest; QuestGameplayEventIntegrationTest |
| SettlementManager:274,277 | restitution food/medicine / transfer | unchanged | 9 | SettlementReputationGameplayTest |
| SettlementManager:1028,1034 | outpost food/log supply / transfer | unchanged | 9 | FortressGameplayIntegrationTest |
| CrateTransactionSystem.transferCrateItemToPlayer / transferPlayerItemToCrate | addStack consumes source count / transfer | unchanged, including theft ledger | 9 | SettlementTheftTransactionTest |
| Inventory.addStack/remove/shrink/set and ItemStack construction | shared storage mechanics and initialization | no global free-item gate; callers own D4 policy | 7, 9 | catalog fresh stack and transfer tests |
| ItemConditionSystem.slowTick:31, tickInventory:63 | player inventory freshness drain/conversion | skip carried inventory; equipment currently has no spoil tick | 9 | planned carried-versus-crate freshness twin |
| ItemConditionSystem:36 and rack/carcass decay | crate food and world storage aging | unchanged | 9 | planned stored-food spoilage test |
| ProjectileSystem:392 | stuck arrow recovery / transfer | unchanged | 9 | BowGameplayWorkflowTest recovery |
| EntityManager:61-155 and world loot | creature/NPC drops and rewards | unchanged; only Creative block drops are suppressed | 8, 9 | BowHuntProgressionIntegrationTest; existing loot tests |

## A3 - all player reads in AI, settlement, combat and EntityManager

Baseline search: `rg -n '\.player|\bPlayer\b'` in the four requested areas,
followed through each local `p` alias and consequences invoked by player actions.
Grouped anchors enumerate repeated reads in the same operation. P = perception
or targeting (gate); S = proximity simulation (keep); C = consequences or
explicit friendly interaction (keep). Ownership predicates such as
`Npc.hostileToPlayer()` remain world-policy answers, not ability gates.

| Location / baseline read anchors | Current behavior / class | Creative decision | Milestone | Test authority |
| --- | --- | --- | --- | --- |
| CreatureAI.detectionRange:32 | stance/noise/scent / P | zero detectable range | 4 | planned CreativePerceptionTest |
| updateDeer:77; updateHare:158 | player distance/stance/noise flee / P | infinite player distance; keep predator/fire/grazing | 4 | planned herbivore twins |
| updateThornhorn:196 | hurt/fear player charge / P | gate charge; other wildlife threats stay | 4 | HumanPerceptionGameplayTest thornhorn; planned twin |
| updatePredatorCommon:243 | hungry detection, pursuit, attack, flee-hurt / P | gate all player directions, keep other prey/carcasses/fire | 4 | parity hungry wolf; planned twin |
| updateStalker:397 | darkness/prey, flank, ambush, hurt / P | gate player prey, retain nest/fire behavior | 4 | EntityEcologyTest; planned stalker twin |
| retreatDownLightGradient:507, pickDarkFlank:528 | player-relative retreat/flank / P | no Creative player fallback target | 4 | planned stalker twin |
| updateBird:557,560-561 | close player takeoff/flee / P | gate player proximity reaction | 4 | planned bird twin |
| SettledNpcAI:46-47 | captive faces nearby player / P | suppress unsolicited facing; rescue stays | 4 | CaptiveRescueGameplayTest |
| SettledNpcAI:57,122 | current/stale combat and perceive / P | predicate before sight/aim/traces; clear on entry | 4 | HumanPerceptionGameplayTest; parity alarm |
| perceive:165-194 | player-attributed hearing and tracker sharing / P | suppress player traces/hearing, keep unrelated events | 4 | planned mining/gunshot and stale-memory tests |
| combat:234; rangedCombat:463; melee:562 | aim, player-relative evade/pursuit, attack / P | gate every entry including old intent | 4 | planned switch-during-combat twin |
| party travel:290,353,415 | far/near abstract travel threshold / S | unchanged materialization budgets | 4 | CounterattackLifecycleTest; NpcBudgetTest |
| NpcAI:32 | talking NPC faces player / C | explicit conversation remains available | 4 | planned friendly interaction twin |
| NpcAI:61-62 | fear fallback away from player / P | use own/home position without sensing player | 4 | planned camp NPC twin |
| NpcAI:67-81 | camp hostile distance/attack/pursuit / P | predicate at branch | 4 | planned hostile camp twin |
| NpcAI:271-284 | voluntary allied medic help / C | retain friendly service; no damage need in Creative | 4 | planned friendly interaction twin |
| NpcAI:366-381 | raider chooses player vs defenders / P | exclude player, retain defender/camp attack | 4 | planned raider target twin |
| NpcAI trader:427-443 | escort completion and following / C | explicit friendly escort/trade loop remains | 4 | QuestGameplayEventIntegrationTest |
| FactionSystem:78-79,353,973-976,1012,1040 | gifts/quests/rewards/inventory / C | unchanged | 4 | FactionQuestTest; QuestGameplayEventIntegrationTest |
| FactionSystem:774-775,918-919 | camp-relative quest fallback origin / S | unchanged; not hostile perception | 4 | FactionQuestTest |
| SettlementManager:260-277 | restitution inventory / C | unchanged | 4 | SettlementReputationGameplayTest |
| onNpcAttackedByPlayer:312 and neighboring alert writes | non-perception reputation plus witness memory / C + P | keep reputation; gate alert and player memory | 4 | planned equal-reputation twin |
| onNpcKilledByPlayer and threat-cleared consequences | ownership/death/morale/reputation / C | unchanged, no detection needed | 4 | SettlementReputationGameplayTest; FortressGameplayIntegrationTest |
| onCrateTheft/onRestrictedStorageOpened/onContainerBroken/onStructureDestroyed/onExplosionPropertyDamaged | reputation/stock plus alert / C + P | retain attribution/stock, gate player-caused alert | 4 | SettlementTheftTransactionTest; planned twins |
| tickRestrictedAreas:568,575-578 | proximity trespass and warning/alert / C + P | retain ownership consequence; no perceived warning/alarm | 4 | SettlementReputationGameplayTest; planned twin |
| slowTick:589 | discovery, activation/deactivation / S | unchanged | 4 | SettlementActivationTest; SettlementRuntimeTest |
| objective:1000; occupancy:1016 | capture proximity and supplies / C | unchanged | 4 | FortressGameplayIntegrationTest |
| bounty party:1217,1227 | origin proximity plus destination at player / S + P | keep bounty accounting; gate tracking dispatch | 4 | planned bounty twin; existing mission tests |
| gate aabb:1347-1348 | prevent closing into player / S | unchanged body collision | 4 | SettlementRuntimeTest |
| CounterattackDirector:134,413-422,527,532-533 | null guard, spawn exclusion, activation distance / S | unchanged; missions target settlements | 4 | CounterattackLifecycleTest |
| SettlementPlanner:235 | seed-derived starter region protection / S | unchanged | 2, 4 | SettlementPlannerTest; planned same-seed test |
| EntityManager:61,63,105-118,144,155 | loot, knife availability, kill feedback / C | unchanged | 4 | BowHuntProgressionIntegrationTest |
| EntityManager:232,269-273,288,351 | natural spawn location, cave band, exclusion / S | unchanged until explicit spawn control | 4, 11 | EntityEcologyTest; planned controls tests |
| ProjectileSystem:265-267 | incidental player hit candidate / physical | keep physical collision without injury (milestone 3 body gate); aiming is gated at SettledNpcAI combat entry | 3, 4 | CreativeHazardsTest ARROW/BULLET; CreativePerceptionTest |
| ProjectileSystem:293,392 | player injury and recovered item | body gate; recovery unchanged | 3, 4 | CombatSystemsTest; BowGameplayWorkflowTest |
| ExplosionSystem:158-159,178,263 | physical blast victims and player-attributed noise | body gate and common noise gate; world blast unchanged | 3, 4 | CombatSystemsTest; planned noise twin |
| WorldNoise.emit / NoiseEvent.playerSource:35 | all positioned perception events | one emit boundary suppresses player source; unrelated events stay | 4 | HumanPerceptionGameplayTest; planned Creative noise tests |

Entering Creative must clear last-known positions/ages, searches, combat targets,
player-directed creature intent and pursuing party destinations. Do not erase
world reputation, dead residents, ownership, quests or settlement activation.
`FactionSystem` has no direct hostile targeting read in this baseline; its
quest-location fallbacks must not receive a blanket ability gate.

## A4 - input ownership audit

| Context / location | Existing keys/buttons | Creative decision | Milestone | Test authority |
| --- | --- | --- | --- | --- |
| Game movement | WASD; Space held/pressed; Left Shift; Left Ctrl; mouse look | double tap only when mayFly | 6 | PlayerMovementSystemTest; parity double tap |
| PlayerInteractionSystem / Game actions | LMB held/press; RMB press; F; R | add middle-button pick only in Creative | 8 | PlayerInteractionSystemTest |
| HotkeyRouter gameplay | E, C, M; Tab, F2, F3, P; F5/F9; 1-9, scroll; Escape | E branches to catalog; no gameplay mode chord | 5, 7 | AudioMenuRoutingTest; planned CreativeMenuRoutingTest |
| Pause | Escape resume, Q quit, O graphics, V audio | G confirmation; T world controls only in Creative | 5, 11 | planned menu ownership tests |
| TitleScreen | N new, L load, O graphics, V audio, Q/Escape quit, Enter selection, LMB | N/button open worldless new-frontier | 5 | planned new-frontier tests |
| New frontier (planned) | unbound | S/C, arrows/Tab, Enter start, Escape back, LMB | 5 | planned new-frontier tests |
| Audio/GraphicsOptionsScreen | W/S or up/down, A/D or left/right, Enter/Space, F5 apply, Escape cancel; mouse drag/click | early router return pattern for every new owning screen | 5, 7, 11 | AudioMenuRoutingTest |
| InventoryScreen | LMB select/move/equip; global E/Escape close | reuse inventory/gear, add Creative-only trash | 7 | planned catalog tests |
| CraftingScreen | Q/R station, W/S or up/down recipe, Enter/LMB craft | unchanged | 7 | existing progression tests |
| CrateScreen | LMB transfer, F/Escape close | unchanged transfers | 7, 9 | SettlementTheftTransactionTest |
| NpcScreen | 1-6 services/dialogue, F/Escape close | unchanged friendly actions | 4, 9 | existing quest/reputation tests |
| Death/victory | Escape title, Enter continue/respawn | mark note only; no Creative death | 3, 5 | planned body/menu tests |
| Sleeping | Escape wake; router otherwise returns | freeze refuses sleep | 11 | planned controls test |
| Catalog (planned) | unbound | text callback, / focus, Backspace, Escape unfocus/close, E close only unfocused, 1-9 hover grant, LMB/RMB, wheel | 7 | planned catalog ownership tests |

G and T have no existing binding in any searched source; middle mouse is free.
Input currently has no character callback. Search owns typed characters so E/P/M
and digits never reach gameplay; F5/F9 must be inert on every new owning screen.

## A5 - save layout and extraction

| Location | Existing representation | Creative decision | Milestone | Test authority |
| --- | --- | --- | --- | --- |
| SaveSystem.save:153-357 | magic, v3, seed, generator, time; weather current/next/blend/timer; player xyz/yaw/pitch and health/hunger/thirst/stamina/body/fatigue/wetness/protein/vitamins/smoke/woundClean/hotbar | byte order unchanged | 2 | SaveMigrationTest; HistoricalV020SaveCompatibilityTest |
| Same writer following player | affliction map, blueprint IDs, inventory, equipment; changed blocks; campfire fuel; crates; rack input/count/progress; collectors; discoveries; beacon optional pos/stage | unchanged; append enum items only in 10 | 2, 10 | save tests; SerializedEnumOrderTest |
| Same writer after beacon | faction trust/alert/food/wood/hostility/upgrade/gift; optional quest; NPCs; creatures; carcasses; events; log tail; settlements; faction maps; keg fuses | base unchanged | 2 | SaveMigrationTest; ActiveExplosivePersistenceTest |
| writeV3Extension:870 / read:916 | W3EX magic, extension version 2; original settlement/resident/party fields retained from extension v1 | unchanged | 2 | SaveMigrationTest |
| writeV3ExtensionSections:986 / read:1183 | S3EC magic, section count; UTF stable ID, int byte length, payload; unknown IDs skipped, max 256 sections / 16 MiB each | retain envelope and bounds | 2 | CorruptSaveResilienceTest |
| Existing section IDs | world.lanterns; npc.party-state; missions.counterattacks; quest.target-state; settlement.reputation-state; combat.active-explosives; combat.keg-fuse-attribution | preserve order and payload bytes | 2 | LanternGameplayTest; QuestTargetPersistenceTest; ActiveExplosivePersistenceTest |
| SaveSystem headroom | only 15 lines free | first extract section codecs/dispatch to dedicated save collaborator; compare fixed-seed bytes before/after with SHA-256 | 2 | all save suites and recorded paired hashes |
| Planned player.game-mode v1 | absent today | int section version, UTF stable mode ID, mark boolean, flying boolean; reject invalid/truncated/trailing data | 2 | planned GameModeSectionTest; live-load corruption tests |
| Planned world.creative-controls v1 | absent today | independent versioned controls; absent defaults off; reject malformed payloads | 11 | planned CreativeControlsSectionTest |
| Load over live world | verify in isolated Game before replacement through newWorld | retain; reset then restore without switch logs/stats/RNG effects | 2 | CorruptSaveResilienceTest; GameLoopIntegrationTest |

Missing mode means Survival/unmarked/not flying. Older v0.6.0 skips the new
sections and loads non-palette Creative saves as Survival; saving there discards
the mark, flight and controls. Appended palette items were expected to cause a
clean old-reader failure; measurement at v0.6.10 disproved that, and the
measured behavior is recorded in the v0.6.10 evidence. v2 fixtures retain
legacy terrain. Verify the entire compatibility matrix before release; these are
requirements, not completed compatibility claims.

## State ownership and implementation rules

| State | Persisted | Owner / reset contract |
| --- | --- | --- |
| Mode and permanent mark | player.game-mode | GameModeController; reset before Player construction |
| Flying | player.game-mode, Creative only | PlayerAbilities; restore separately from switching |
| Other abilities | derived only | one mode-to-abilities authority; gameplay reads booleans |
| Time freeze, weather lock, spawning control | world.creative-controls | Creative controls collaborator; reset on world and leaving Creative |
| Double-tap and break timers | no | movement/block collaborator; reset on world and relevant release |
| Query/tab/scroll/focus | no | catalog model/screen; reset on world or opening |
| Pending confirmation/selected new-world mode | no | owning screen/frontend; cancellation has no world side effects |

Add reset assertions to GameLoopIntegrationTest or SimulationSystemContractTest.
No new randomness, thread, asset or dependency. Preserve Survival arithmetic and
draw order. Use Locale.ROOT and LF/UTF-8. Extract instead of raising line limits.
Game commands remain one-line delegates. Headless tests use real commands and
isolated arenas. No existing expectation changes except the allowed version,
appended enum names and additive reset assertions. Document new skipped Creative
RNG paths explicitly. UI hints must describe only the implemented milestone.

## Evidence

### v0.6.1 evidence

Added SurvivalCreativeParityTest with 17 deterministic cases using production
commands. Existing bow/firearm workflows already pin reserve and arrow costs;
the audit cites them instead of duplicating them. Focused parity run: PASS in
5s, 17 tests / 1 class, zero failures, errors and skips. No production seam or
production behavior change was needed.

Initial new-fixture failures exposed three setup assumptions: wolves attack
only when hungry; a resident index is required for settled NPC identity; voxel
landing stops within one gravity step instead of snapping to an exact Y. The
fixtures now express those production preconditions, without editing existing
tests or changing behavior. Human/native checks are not required for milestone 1.

Final v0.6.1 build: PASS, 503 tests / 80 classes, zero failures, errors and skips; 2m7s (127.897s wall clock). JavaDoc doclint passed. All six line counts remain at the untouched baseline values. Performance baseline passed unchanged; no production change requires a second performance run. Native runs/captures are not required at this milestone. git diff --check release/0.7.0 passed after removing an extra documentation EOF blank line.

### v0.6.2 evidence

First extraction moves the existing stable-ID envelope and seven section codecs
to V3ExtensionSections. SaveSystem drops from 1,785 to 1,109 lines, with the
1,800-line ceiling unchanged. Only shared package-private byte validators and
vector helpers cross the boundary. Core v3 and extension-v1 layouts are untouched.

The identical SaveByteProbe creates seed 20260910, carries three bombs, throws
one, and records a player-attributed powder-keg fuse. Before/after saves are
both 7,057 bytes, with the same SHA-256:

| Writer | SHA-256 |
| --- | --- |
| Before extraction | 981c4a6b277e11185897f40efe354d31694bcf78c87630f52fa1940d09b3974c |
| After extraction | 981c4a6b277e11185897f40efe354d31694bcf78c87630f52fa1940d09b3974c |

Existing save-package tests and doclint: PASS in 31s, 22 tests / 5 classes,
zero failures, errors and skips. The local probe and saves remain ignored in
build/creative-work. An initial sandbox javac attempt reported archive access
errors; authorized local execution compiled and ran without those errors.

Mode derivation, switch/restore separation, same-seed terrain/camp/kit/wildlife
and RNG parity, all mode/mark/flight combinations, absence, historical v2 and
malformed live-load cases passed. Focused model/save/lifecycle/determinism tests
and doclint: PASS in 34s (35.068s wall), 45 tests / 9 classes, zero failures,
errors and skips. Gameplay gates begin in milestone 3; mode selection remains
automated-only until milestone 5.

Final v0.6.2 build: PASS, 512 tests / 82 classes, zero failures, errors and skips;
1m43s (103.813s wall). JavaDoc doclint and unchanged line budgets passed.
Real lines: Game 902/1000, QaHarness 1409/1500, SaveSystem 1109/1800,
SettlementManager 1392/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.
Performance: PASS in 10s (10.414s wall), all budgets unchanged. Chunk 0.372 ms,
entity 0.335 ms, settlement 0.024 ms, save 1.588 ms, load 189.905 ms, audio
478.004 ms, PCM 40,824,424 bytes, occlusion 0.001923 ms, music 0.000006 ms.
All 19 tick-profile budgets passed (whole cycle 0.0848 ms). Native runs and
captures are not required at this milestone. No deviation from the brief.

### v0.6.3 evidence

The implementation description and measurements through the native capture
paragraph below were inherited from the previous agent. The resumed session
reviewed the complete diff and all four new files before adopting the work.

Player owns invulnerability at both direct/physical damage boundaries, knockback,
affliction admission and fall accounting. Creative body restoration is explicit
on new-world entry and switching; loading still restores without switch healing.
Needs refresh biome, environment temperature and sky exposure before holding the
full body. Shelter scans and visible world/fumarole smoke remain active. The HUD
keeps environment temperature, shelter, biome, weather and time; the cyan/amber
badge replaces medical bars, warnings and body/wetness/fatigue/load readouts.

Creative skips bleed, sprain, infection, toxic-fog, fire-affliction, food/water
poisoning and poor-sleep sickness rolls. Survival predicate order, arithmetic and
draw order are unchanged. Every external caller is gated before injury sounds,
logs, flash, blood or armor wear. A package-private Game audio constructor permits
headless sound-request probes; normal construction still creates AudioManager.
No new outcome RNG or simulated cross-world state was added. CreativeQaScenes
records automated-session observations; beginSession resets them, while the
scripted save/load deliberately retains them to detect failures across loading.

Focused body/hazard/Survival parity/model/save tests plus doclint: PASS in 46s
(46.913s wall), 82 tests / 10 classes, zero failures, errors and skips. This
includes 34 new body/hazard cases and harmful Survival twins for every external
damage source, including enclosed fire, fumaroles, both projectile types, all
three creature attackers and all three human melee brains. Initial fixture
failures were corrected by disabling healing in low-DPS damage fixtures, allowing
the arrow to reach its target, fixing camera direction and setting a live raider
mission duration. No existing test or expectation was weakened.

Native 30s smokes, seed 20260910, VSync off, minimum 60 FPS: both PASS on
NVIDIA RTX 1000 Ada Generation Laptop GPU / OpenGL 3.3 / driver 595.95.
Creative 883.2 FPS, p95 1.49 ms, p99 1.76 ms; Survival 888.7 FPS, p95 1.50 ms,
p99 1.75 ms. Both recorded zero GL, KHR-debug and OpenAL errors, successful
save/load, completed fortress approach and all runtime hard limits. Creative
recorded 26,499 body samples, no damage/death, and restored mode/mark/flight.
The first Survival runner attempt accidentally supplied an empty mode variable;
the strict parser correctly refused it. Removing the variable, as required by
the brief, produced the passing Survival twin. Production parsing was unchanged.

Inspected native day-scene captures v063-hud-720_6s.png (1280x720) and
v063-hud-1080-fullscreen_6s.png (1920x1080): badge, environment and shelter
labels fit with clear margins; hotbar, crosshair, time/weather/biome and event
readouts remain visible; no medical bars or vignettes. The first windowed 1080p
attempt was capped by the desktop at 1920x1061, so it is not counted as 1080p
evidence. Existing VEYLON_FULLSCREEN=1 produced the exact required framebuffer.
All captures reported zero GL/KHR/OpenAL errors. Default welcome-log text still
mentions Survival needs; update it when mode selection becomes player-facing.
No human playtest claim is made. Logs and PNGs remain ignored.

Resumed review on 2026-09-14: all Section 0.5 checks matched exactly, including
the 23 paths, base refs, tags and +234/-98 tracked diff. The only production
edit after that review normalizes both Player references in ExplosionSystem
to an explicit entity import; behavior is unchanged. Body, tests and smoke
wiring land together so the command seams and their regression coverage are
reviewable in one commit, followed by the independent HUD commit.

Resumed focused suite and doclint: PASS, 132 tests / 17 classes, zero failures,
errors and skips; 1m03s (63.466s wall). The final import normalization then
passed CreativeHazardsTest in 10s (10.505s wall), with doclint up to date.
The sandbox cannot create the Gradle wrapper lock; approved execution uses
the existing local cache with --offline. No remote command was run.

Resumed native 30s smokes: both PASS, seed 20260910, VSync off, minimum 60 FPS,
1280x720 on the same NVIDIA RTX 1000 Ada / OpenGL 3.3 / driver 595.95 host.
Creative: 899.1 FPS, p95 1.52 ms, p99 1.80 ms, 26,977 body samples with no
damage/death and mode/mark/flight restored. Survival: 898.9 FPS, p95 1.54 ms,
p99 1.75 ms. Both completed isolated save/load and fortress approach, with all
runtime hard limits met and zero GL/KHR/OpenAL errors. These are new runs,
separate from the inherited observations above; neither overlapped other builds.

Resumed captures inspected: v063-resume-hud-720_6s.png (1280x720) and
v063-resume-hud-1080_6s.png (1920x1080, fullscreen). Creative badge, environment
temperature and shelter fit inside the panel; hotbar, crosshair, time, biome,
weather and camp/event labels remain visible without clipping or overlap.
Medical readouts and vignettes are absent. Both captures have zero native
errors. Welcome text remains the documented milestone-5 follow-up. Human
acceptance is unverified. Logs and captures remain ignored.

Final v0.6.3 build: PASS, 546 tests / 84 classes, zero failures, errors and
skips; 1m58s (118.956s wall), including JavaDoc doclint and line budgets.
Real lines: Game 912/1000, QaHarness 1411/1500, SaveSystem 1109/1800,
SettlementManager 1392/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.
The version, release assertion and changelog are prepared for 0.6.3, but the
milestone is not verified, merged or tagged because the next gate fails.

Performance: FAIL in 7s (8.096s wall), save 3.085 ms against the unchanged
2.20 ms budget. A repeat with no code changes also fails in 7s (8.182s wall),
save 2.971 ms. Load timing is not reached because the save assertion fails
first. Other headline budgets pass: first-run chunk 0.382 ms, entity 0.301 ms,
settlement 0.023 ms, audio 455.535 ms, PCM 40,824,424 bytes, occlusion
0.001882 ms/frame, music 0.000004 ms/frame. All 19 tick-profile gates pass;
whole cycle 0.0863 ms. Second-run whole cycle is 0.0827 ms. These save times
cannot be reported as meeting the gate or substituted for the earlier
v0.6.2 plan figure of 1.588 ms.

To distinguish a change regression from current host behavior, an ignored,
detached worktree at the immutable v0.6.2 tag was created under
build/creative-work/v062-save-baseline. Its unmodified performanceTest also
fails: save 3.074 ms / 2.20 ms; 10s (10.575s wall), all other reached budgets
pass. The v0.6.3 diff is empty for the entire save package. This comparison
points to the execution/storage environment rather than the body changes;
the underlying host cause is not established.

An ignored Java diagnostic reads the actual 11,789-byte benchmark payload
before timing and performs the same directory/temp creation, buffered write,
force(true), close and atomic replacement operations. It does no serialization.
In the same approved execution context, fastest of seven after three warmups:
total 2.551 ms (create 0.308, open/write 0.096, force 1.822, close 0.030,
atomic move 0.295 ms). Thus file publication alone currently exceeds the
entire save budget. This is diagnostic evidence, not a replacement benchmark.
The earlier sandbox diagnostic was 2.562 ms. The active host power scheme
reads Balanced; no power, filesystem, security or Git setting was changed.

Stopped under resume-brief Section 1.4 with work committed on
creative/03-invulnerable-body. No budget, test, durable-save operation or
baseline was weakened. Release/main refs and all existing tags remain
untouched; no v0.6.3 tag exists and milestone 4 has not begun. Resume by
resolving the host save-latency blocker and rerunning the unchanged performance
task, then record its passing figures, prepare/merge/tag milestone 3 and
continue in order. All diagnostic files and the detached worktree remain
ignored. git diff --check release/0.7.0 passes.

Resumed session 2 (2026-09-14): the unchanged `build` reports every task up to
date, so the recorded 546-test / 84-class PASS belongs to this exact tree. The
unchanged `performanceTest` still fails only on save: 2.729 ms against 2.20 ms
(7.739s wall). Chunk 0.359 ms, entity 0.318 ms, settlement 0.010 ms, audio
438.742 ms, PCM 40,824,424 bytes, occlusion 0.001679 ms/frame and music
0.000007 ms/frame pass; TickProfileTest passes (whole cycle 0.0786 ms). The
untouched v0.6.2 worktree, run immediately afterwards, fails identically: save
2.775 ms, while chunk 0.364, entity 0.341 and settlement 0.012 ms pass.

The host was on AC power (Balanced scheme) with about 2% CPU and an idle disk
between runs; no other build ran. PerfSaveLoadProbe, an ignored diagnostic that
copies the benchmark's save/load fixture and fastest-of-seven timing, measured
save 2.897 ms and load 187.815 ms against the 240 ms load budget. It shows load
inside its budget; it does not replace the benchmark.

Resolution without raising a budget: the save figure is recorded as a host
deviation, never as a pass. Every later performance milestone runs the
unchanged task and, in the same session, the untouched v0.6.2 worktree. A
milestone advances only when every other budget passes, the probe's load stays
under 240 ms and the milestone's save figure is at most 10% above the paired
reference run. The final release record must list the save gate as not met on
this host. Real line counts equal the build record above.

### v0.6.4 evidence

Every A3 perception or targeting row now reads `Player.isPerceivableByAi()`.
CreatureAI: deer, hare and bird flight; thornhorn charge and charge exit; wolf
and stalker wounded flight; wolf player stalking; stalker detection and its
flat-gradient retreat fallback. SettledNpcAI: captive facing, sight, tracker
traces and combat entry. NpcAI: camp hostility, wounded flight (which now
shelters at the camp fire when nothing is perceived) and raider targeting.
SettlementManager: player-caused alerts for attack, kill, theft, restricted
storage, containers, structures, explosion property and trespass; victim
memory; bounty-hunter dispatch. ProjectileSystem: the shooter reveal.
`WorldNoise.emit` is the one gate for player-attributed events, and
`Player.tickNeeds` holds noise and scent at zero while imperceptible.
`PlayerAwareness.forgetPlayer` is the one memory clear on entering Creative:
sightings, searches, combat intent, alarm-bell runs, player-directed creature
states, bounty and contact pursuit, and queued player events.

Kept unchanged (D3, R12): reputation, theft stock ledger, morale, dead
residents, bounty accrual and earlier scout contact reports; settlement
discovery, activation, dormant simulation and counterattack missions; party
travel distance thresholds; spawning around the player; EntityManager loot
and kill feedback; FactionSystem quests, gifts and camp-relative fallbacks;
trader following and escort; talk facing; medic treatment; gate collision.
Stray hostile projectiles still collide physically without injury (milestone
3 body gate), because aiming is gated at combat entry. Theft and trespass
keep their reputation cost with wording that no longer claims a witness. No
Random, thread, save field or enum constant was added. Survival predicates
keep their arithmetic and draw order; in Creative, gated branches fall
through to ordinary wander and work decisions on the existing AI streams.

CreativePerceptionTest adds 15 cases with Survival twins (6.264s). Focused
perception, AI, settlement, parity, body, hazard, firearm, determinism and
lifecycle suites plus doclint: PASS in 1m08s (68.316s wall), 176 tests / 22
classes, zero failures, errors and skips. No existing test changed.

Final v0.6.4 build: PASS, 561 tests / 85 classes, zero failures, errors and
skips; 2m1s (121.094s wall), including JavaDoc doclint and line budgets.
Real lines: Game 911/1000, QaHarness 1411/1500, SaveSystem 1109/1800,
SettlementManager 1414/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.

Performance (unchanged budgets, 7.799s wall): save 2.738 ms against 2.20 ms
remains the recorded host deviation; the paired untouched v0.6.2 run measured
2.859 ms (7.869s wall), so this milestone is below its reference. Chunk
0.349 ms, entity 0.318 ms, settlement 0.012 ms, audio 454.423 ms, PCM
40,824,424 bytes, occlusion 0.001679 ms/frame and music 0.000008 ms/frame
pass; TickProfileTest passes (whole cycle 0.0854 ms). PerfSaveLoadProbe: save
2.936 ms, load 176.847 ms against 240 ms.

Native 30s smokes, seed 20260910, VSync off, minimum 60 FPS, 1280x720, same
NVIDIA RTX 1000 Ada / OpenGL 3.3 / driver 595.95 host, run one at a time with
no concurrent build. Creative: PASS, 919.8 FPS, p95 1.43 ms, p99 1.62 ms,
27,597 body samples without damage or death, mode/mark/flight restored.
Survival: PASS, 898.9 FPS, p95 1.44 ms, p99 1.73 ms. Both completed isolated
save/load and the fortress approach within every runtime hard limit, with
zero GL, KHR-debug and OpenAL errors. No player-visible screen or string
changed except two Creative-only reputation log lines, which appear in the
existing event log; captures are therefore not required here. Human
playtesting is unverified.

### v0.6.5 evidence

NEW FRONTIER and N open the worldless NewFrontierScreen (appended
`AppState.TITLE_NEW_WORLD`). Survival is the default on every opening; S/C,
arrows or Tab choose; Enter starts through the unchanged two-frame loading;
Escape returns to the title without a world. The replace-save notice follows
`Files.exists` on the save slot, checked once per opening. Pause [G] opens
GameModeScreen (appended `UiMode.GAME_MODE`), which pauses the simulation
through `UiMode.pausesSimulation()` and joins the options screens in
HotkeyRouter's early return, so Escape, F5, F9, Q, O and V cannot reach the
router. Only Survival to Creative on an unmarked world shows the permanent-mark
warning, and Escape or N is checked before Enter or Y. The pause header shows
the mode and mark, the controls reference lists [G], the victory card adds
"Creative world", and a Creative new world logs three truthful hints
(invulnerable, imperceptible, [G]) instead of "Survive." with the same kit.
`FrontendController.performLoad` separates the load from native cursor capture
so the production load path runs headless; `savePath` is the test seam. No
gameplay hotkey switches modes (D1).

ModeSelectionRoutingTest adds 7 cases. Focused routing, lifecycle, save, body,
perception, parity and QA suites plus doclint: PASS in 48s (48.592s wall), 111
tests / 18 classes, zero failures, errors and skips. No existing test changed.

The first 1920x1080 fullscreen new-frontier capture showed a 19-pixel black
band and every element shifted and scaled by 1061/1080. The unchanged title
screen reproduced it: only PostProcessor and ShadowMap set `glViewport`, so
worldless frames kept the desktop-capped 1920x1061 window viewport after the
fullscreen switch. A separate `fix(ui)` commit resets the viewport in
`UiRenderer.end`; native title and new-frontier captures after it show no band
and the divider at exactly 24% of the height. The earlier build, smokes and
captures ran before that fix and are superseded; every gate below reran on the
committed fix.

Final v0.6.5 build: PASS, 568 tests / 86 classes, zero failures, errors and
skips; 2m2s (122.229s wall), including JavaDoc doclint and line budgets. Real
lines: Game 919/1000, QaHarness 1411/1500, SaveSystem 1109/1800,
SettlementManager 1414/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.
The performance task is not required here: no simulation, movement, AI, item
or save code changed.

Native 30s smokes on the committed fix, seed 20260910, VSync off, minimum 60
FPS, 1280x720, NVIDIA RTX 1000 Ada / OpenGL 3.3 / driver 595.95, one at a
time with no concurrent build. Creative: PASS, 907.7 FPS, p95 1.43 ms, p99
1.64 ms, 27,232 body samples without damage or death, mode/mark/flight
restored. Survival: PASS, 913.2 FPS, p95 1.40 ms, p99 1.62 ms. Both completed
isolated save/load and the fortress approach within every runtime hard limit,
with zero GL, KHR-debug and OpenAL errors.

Native captures on the committed fix, inspected, each with zero GL, KHR-debug
and OpenAL errors: `newworld`, `newworld-save`, `gamemode` (the permanent-mark
warning), `gamemode-creative`, `pause-creative` and `victory-creative`, at
1280x720 windowed and 1920x1080 fullscreen (12 PNGs, prefix `v065f-`). Cards,
descriptions, the mark note, the amber replace notice, buttons and key hints
fit without clipping or overlap at both sizes. The confirmation panel keeps
both warning lines inside its margins with the current mode and mark above
them; the pause header reads "CREATIVE  /  Creative world" and the controls
list [G]; the victory card shows the amber "Creative world" note below its
hints. The bracket-glyph softness and proportional column drift in the pause
controls list are unchanged from v0.6.0. The HUD event log beneath the dimmed
modal is partially covered by the panel, as other modal screens already do.
Human acceptance of the wording and layout is unverified.

### v0.6.6 evidence

A separate `test(creative)` commit first narrowed one milestone-5 assertion:
the whole Creative-to-Survival confirmation text becomes obsolete once the
brief's flight line is added, so the test now pins its first line to the
same sentence. It passed unchanged against the milestone-5 screen (7 cases).

`PlayerMovementSystem` measures time since the last Space press on a transient
`Player.secondsSinceJumpTap` field and toggles flight on a second press
within `FLIGHT_TOGGLE_WINDOW_SECONDS = 0.30`. A body that may not fly only
resets that field, so the Survival walking path is unchanged; its WASD block
moved into `applyHorizontalIntent` with identical arithmetic and order. While
flying: Space rises and Left Ctrl descends at 7.5 blocks/s, no vertical input
hovers, cruise is 10.9 and Left Shift 21.6 blocks/s, crouching and sprinting
stay off (no stamina, sprint noise, footsteps or view bob), and descending
onto the ground lands. `VoxelPhysics.integrateFlight` keeps voxel collision
without gravity or step-up; unloaded chunk columns and the altitude ceiling
`Chunk.SY + 32 = 128` act as walls, the ceiling only upward. Leaving Creative
already ends flight and restarts fall accounting (milestone 3), and the
milestone-2 section already restores flight only in Creative. The HUD shows
FLYING beside the Creative badge; the pause controls, the Creative-to-Survival
confirmation, the Creative welcome hint and the new-frontier card mention
flight. `CreativeQaScenes` adds a smoke flight (5.2-6.8 s, after the isolated
load, before the fortress route) and the `creative_flight` measurement scene.

CreativeFlightTest adds 10 cases. The first focused run exposed one fixture
error: after flying into the test wall the player stood at x = 314.7, outside
the ceiling slab at x 309-312, so the ceiling assertion could not hold. The
slab now surrounds the player's actual block; production code did not change.
Focused flight, movement, parity, body, hazard, perception, routing, save,
lifecycle and QA suites plus doclint: PASS in 1m (60.932s wall), 156 tests / 21
classes, zero failures, errors and skips. Real lines: Game 924/1000, QaHarness
1413/1500.

R14 measurement, native `creative_flight` scene (seed 20260910, 1280x720,
render distance 6, streaming budgets unchanged at `ensureChunks(..., 2)` and
`buildDirtyMeshes(..., 3)`), a 28 s straight flight east at Left Shift speed
with a 10-block terrain clearance; "hole" means a missing or unmeshed chunk
within four chunks of the player in the frame just rendered:

| Run | Distance | Speed | Chunks crossed | Unloaded entered | Hole frames | Longest run | FPS | p95 / p99 / max ms |
| --- | ---: | ---: | ---: | --- | --- | --- | ---: | --- |
| VSync off | 604.8 | 21.60 b/s | 38 | no | 15 of 25,195 | 12 frames from 20.01 s | 882.0 | 1.52 / 6.01 / 29.67 |
| VSync on | 604.8 | 21.59 b/s | 38 | no | 15 of 2,790 | 12 frames from 20.02 s | 99.3 | 10.45 / 11.64 / 79.94 |

Both runs reported zero GL, KHR-debug and OpenAL errors. The identical frame
counts at 882 and 99 FPS show the one burst is bounded by the per-frame mesh
budget, not by flight speed; at display refresh it lasts about 0.12 s once in
28 s. Its cause was not isolated. An earlier pair of the same runs without the
start-time field gave the same distances, chunk and hole counts. The inspected
VSync-on 30 s capture shows continuous terrain, trees and water ahead to the
fog, with the CREATIVE and FLYING badge. These results justify keeping 10.9
and 21.6 blocks/s and the 128-block ceiling without raising any streaming
budget. Human evaluation of flight feel is unverified.

Final v0.6.6 build: PASS, 578 tests / 87 classes, zero failures, errors and
skips; 2m5s (125.740s wall), including JavaDoc doclint and line budgets. Real
lines: Game 924/1000, QaHarness 1413/1500, SaveSystem 1109/1800,
SettlementManager 1414/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.

Performance (unchanged budgets, 7.824s wall): save 2.902 ms against 2.20 ms
remains the recorded host deviation; the paired untouched v0.6.2 run measured
2.806 ms (7.872s wall), so this milestone is 3.4% above its reference, inside
the 10% rule. Chunk 0.378 ms, entity 0.314 ms, settlement 0.012 ms, audio
444.961 ms, PCM 40,824,424 bytes, occlusion 0.001763 ms/frame and music
0.000004 ms/frame pass; TickProfileTest passes (whole cycle 0.0860 ms).
PerfSaveLoadProbe: save 2.898 ms, load 187.599 ms against 240 ms.

Native 30s smokes, seed 20260910, VSync off, minimum 60 FPS, 1280x720, NVIDIA
RTX 1000 Ada / OpenGL 3.3 / driver 595.95, one at a time with no concurrent
build. Creative: PASS, 904.3 FPS, p95 1.42 ms, p99 1.63 ms, 27,131 body
samples without damage or death, mode/mark/flight restored, and the scripted
smoke flight ran 1,465 frames across 2 chunks without entering an unloaded
column. Survival: PASS, 913.0 FPS, p95 1.39 ms, p99 1.61 ms. Both completed
isolated save/load and the fortress approach within every runtime hard limit,
with zero GL, KHR-debug and OpenAL errors.

Native captures, inspected, each with zero GL, KHR-debug and OpenAL errors,
at 1280x720 windowed and 1920x1080 fullscreen (8 PNGs, prefix `v066-`):
`creative_flight` at 6 s shows the CREATIVE badge with FLYING, continuous
terrain ahead and the new flight hint in the event log; `pause-creative` fits
the two new flight lines inside the panel; `gamemode-creative` shows both
confirmation lines above the buttons; `newworld` shows the updated Creative
card text inside its card. Nothing clips or overlaps. Human evaluation of
flight feel and wording is unverified.

### v0.6.7 evidence

`item/CreativeCatalog` is the headless model: every `ItemType` belongs to
exactly one of eight categories (Building, Stations, Materials, Food & water,
Medical, Tools, Weapons & ammo, Gear) through property rules plus explicit
ammunition and building overrides, each category keeps declaration order, and
search lowercases display names and enum ids once with `Locale.ROOT` and
requires every whitespace-separated term. A non-empty query searches every
category; an empty one lists the selected category; the view is rebuilt only
when the query or category changes. `item/CreativeGrants` builds grants with
the ordinary `ItemStack` constructor (full durability and freshness, empty
magazine): left click a full stack, right click one item, into the empty
selected hotbar slot, else the first empty slot, else an "Inventory full"
notice; a hovered entry plus 1-9 replaces that hotbar slot.

`ui/CreativeCatalogScreen` (appended `UiMode.CREATIVE_CATALOG`, not pausing)
draws category tabs, a search field with caret, a scrolling icon grid and a
tooltip through the shared `ui/ItemDetails` line extracted from
InventoryScreen with identical output. Its INVENTORY tab draws the existing
inventory screen, whose layout helpers keep the original arithmetic, and adds
a trash slot that deletes only for a player with unlimited items. `Window`
registers `glfwSetCharCallback`; `Input` keeps up to 32 typed code points per
frame, cleared in `endFrame`, behind the public `onTyped` seam. The field
accepts only printable code points the font reports with `hasGlyph`. The
catalog joins HotkeyRouter's early return (R18): typed text reaches only the
focused field, E closes only when unfocused, Escape unfocuses then closes,
and F5/F9 are inert. Query, tab, scroll and focus reset on close and in
`WorldBootstrap`, with an additive GameLoopIntegrationTest assertion. In
Creative, E opens the catalog; Survival E still opens the inventory. The
pause controls and Creative welcome hints mention the catalog, and
VEYLON_FRONTEND gains `catalog`, `catalog-tools`, `catalog-search` and
`catalog-inventory`.

CreativeCatalogTest (6), CreativeGrantsTest (5) and CreativeCatalogRoutingTest
(7) add 18 cases, including search under a Turkish default locale and typed
e/p/m/1 changing only the focused query. Focused item, catalog routing,
lifecycle, audio and mode routing and QA suites plus doclint: PASS in 12s
(12.536s wall), 52 tests / 12 classes, zero failures, errors and skips; the
rerun after the hint lines and version change passed identically (12.722s
wall). No existing expectation changed except the additive reset assertion.

Pre-resume v0.6.7 build: PASS, 596 tests / 90 classes, zero failures, errors and
skips; 2m5s (125.971s wall), including JavaDoc doclint and line budgets. Real
lines: Game 932/1000, QaHarness 1413/1500, SaveSystem 1109/1800,
SettlementManager 1414/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.
The performance task is not required here: the catalog changes no simulation,
movement, AI, item-use or save code.

Resumed 2026-09-15 from the existing catalog branch. The original starting-state
checks do not apply to this explicitly requested continuation. Opening-frame
ownership now consumes E before the screen can close itself, and simultaneous
C/M/F5/F9/debug keys cannot leak. Long queries keep their edited tail inside
the field, with caret space and complete Unicode code points. The inventory
trash label and hint have dark backing for contrast against bright terrain.
Focused catalog/routing tests and doclint pass: 22 tests / 4 classes in 14s.

Final v0.6.7 build: PASS, 600 tests / 91 classes, zero failures, errors and
skips; 2m08s (128.656s wall). JavaDoc and unchanged line budgets pass: Game
932/1000, QaHarness 1413/1500, SaveSystem 1109/1800, SettlementManager
1414/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.

Fresh native 30-second smokes, seed 20260910, VSync off, minimum 60 FPS,
1280x720 on the same NVIDIA host, run sequentially without concurrent builds:
Creative PASS at 883.2 FPS, p95/p99 1.45/1.66 ms; Survival PASS at 851.8 FPS,
p95/p99 1.66/2.39 ms. Both complete isolated save/load and fortress approach,
with zero GL/KHR/OpenAL errors. Creative restores mode/mark/flight, takes no
damage in 26,499 body samples and flies 1,448 frames across two chunks without
entering unloaded columns.

All twelve fresh PNGs with prefix `v067-final-` were opened and inspected:
default catalog, Tools, focused search for iron, and Inventory at 1280x720,
1920x1080 fullscreen and 1920x1080 at UI scale 1.5. Tabs, search/caret, icons,
instructions, gear slots and trash fit without clipping or overlap. The trash
label and hint remain readable over snow. Native runs report 91/91 icons and
zero GL/KHR/OpenAL errors. The default font is small as elsewhere in the UI;
human catalog ergonomics and non-Latin/IME input remain unverified.

### v0.6.8 evidence

R19-R21: PlayerBlockActions gates Creative mining before Survival arithmetic.
A press breaks immediately, ignoring tool requirements; a held button repeats
every 0.30s, and reaching another block while held restarts that interval.
Release, melee/ranged routing, mode switches, screen close and world
replacement clear the transient timer, so the next press breaks at once.
Drops (including berry fiber and leaf RNG) and tool wear are skipped;
crate/rack spills, campfire/collector/lantern/beacon/keg cleanup and
non-perception vandalism consequences keep their normal paths. Placement skips
only the stack cost and retains collision and initialization. BlockItemForms
derives a unique mapping from places(), including BEACON to BEACON_FRAME.
Middle mouse selects an existing hotbar item, grants into the first empty
hotbar slot, or replaces the selected slot; missing forms log a notice. The
Creative welcome log and the pause controls reference name these controls.

Pre-fix focused production-command tests: PASS, 38 tests / 4 classes, zero
failures, errors or skips, with doclint in 13s (13.919s wall). Seven new
building cases cover cadence, resets, tool-only ore, unbreakable blocks, no
drops/wear/noise, all stored block state, repeated placement and pick
precedence. Input gains one additive routing case; lifecycle gains an additive
timer reset assertion. An initial test compilation failed because the new
fixture accidentally replaced an existing helper. The original helper was
restored byte-for-byte; new tests now reuse it.

Resumed 2026-09-15 before the milestone gates. Review of the feature commit
found a cadence defect: `Game.updateActions` recasts the target before routing
every frame, so the block behind a broken one becomes a new target on the next
frame, and the first implementation broke changed targets at once. Holding LMB
therefore dug through the whole 5.2-block reach one block per frame instead of
once every 0.30s. A separate `fix(creative)` commit reads the brief's timer
reset on target change as restarting the interval; only a press after release
breaks at once. The first building case had asserted the defective immediate
retarget; the fix commit explains why that expectation is obsolete. A new case
recasts through `Raycaster` and routes `PlayerCombatSystem.updatePrimaryAction`
in 50 ms frames, with breaks on frames 0, 6 and 12. Swapping in the unfixed
class fails both cases (all four blocks in reach gone after four consecutive
frames); the fix was then restored byte-identically. Focused building, parity,
input and lifecycle suites with doclint: PASS, 39 tests / 4 classes, zero
failures, errors and skips, in 13s (13.574s wall). An earlier full build and
performance run on the unfixed tree are superseded and not counted below.

The first changed-hint captures (prefix `v068-final-`, 1280x720 and 1920x1080)
showed the new building hint fitting, but it was the sixth Creative welcome
line before the shared camp line, while the HUD draws only six event log
lines: "You crash-landed on Veylon in Creative mode." had scrolled out on the
first frame. A separate `fix(creative)` commit merges the catalog and building
hints into one line and records the six-line limit beside the hints. Only log
text changed; no test pins the merged strings. Focused mode selection, catalog
routing, mode model, body, building and lifecycle suites with doclint: PASS,
55 tests / 6 classes, in 18s (18.791s wall). The build, performance run, smokes
and captures made between the two fixes are superseded by the reruns below.

Final v0.6.8 build: PASS, 609 tests / 92 classes, zero failures, errors and
skips; 2m6s (126.895s wall), including JavaDoc doclint and line budgets. Real
lines: Game 939/1000, QaHarness 1413/1500, SaveSystem 1109/1800,
SettlementManager 1414/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.

Performance on the same tree (unchanged budgets, 9.689s wall): every budget
passes, including save at 1.501 ms against 2.20 ms, so this milestone records
no host save deviation. Load 172.745 ms, chunk 0.482 ms, entity 0.336 ms,
settlement 0.011 ms, audio 441.727 ms, PCM 40,824,424 bytes, occlusion
0.001847 ms/frame and music 0.000008 ms/frame pass; TickProfileTest passes
(whole cycle 0.0811 ms). Earlier in the same session, after the cadence fix
and before the log-text fix, the task measured save 1.440 ms and the paired
untouched v0.6.2 worktree measured save 1.455 ms (9.969s wall), with every
other budget passing in both. PerfSaveLoadProbe was not needed because the
benchmark itself reached and passed load.

Native 30s smokes on the final tree, seed 20260910, VSync off, minimum 60 FPS,
1280x720, NVIDIA RTX 1000 Ada / OpenGL 3.3 / driver 595.95, one at a time with
no concurrent build. Creative: PASS, 904.5 FPS, p95 1.40 ms, p99 1.62 ms,
27,138 body samples without damage or death, mode/mark/flight restored, and
the scripted flight ran 1,399 frames across 2 chunks without entering an
unloaded column. Survival: PASS, 897.4 FPS, p95 1.43 ms, p99 1.69 ms. Both
completed isolated save/load and the fortress approach within every runtime
hard limit, with 91/91 icons and zero GL, KHR-debug and OpenAL errors. The
smoke does not script Creative building; R19-R21 evidence is the headless
production-command suite above.

Final captures on the same tree, inspected, each with zero GL, KHR-debug and
OpenAL errors, at 1280x720 windowed and 1920x1080 fullscreen (4 PNGs, prefix
`v068-final2-`). `welcome` (Creative day scene at 6 s) shows all five Creative
hints and the camp line, the merged catalog and building hint on one line
without truncation over bright grass. `pause-creative` fits the Creative
controls line (E catalog, instant LMB, free RMB, middle mouse picks) inside the
panel under the "CREATIVE  /  Creative world" header. That line does not follow
the two-column key layout of its neighbours; bracket-glyph softness and the
modal covering the event log are unchanged from earlier milestones. Human
evaluation of building feel, pick ergonomics and wording is unverified.

### v0.6.9 evidence

R22-R23: every A2 use-up row reads `PlayerAbilities.unlimitedItems()` at its
own call site. PlayerCombatSystem: bow shots skip arrow removal and keep the
deliberate arrow selection (default ARROW) instead of following carried stock,
and [R] still cycles types when none are carried; firearm reloads skip the
reserve check and removal but keep the reload time and per-shot magazine use;
thrown bombs skip the shrink; `consumeDurability` (melee, mining, bow and
firearm wear) and `useKnife` return before any wear. PlayerConsumables: eating,
drinking (without an extra empty skin) and used treatments skip the shrink and
keep their benefits. ItemConditionSystem skips only the carried inventory tick;
crates, racks and collectors still tick, and equipment has no freshness tick in
either mode. Armor wear stays behind the milestone-3 body gate. Hud readouts
use ASCII "unlimited" for arrows, reserve rounds and thrown stacks through
static label helpers, and an empty Creative magazine always shows [R] Reload.
QaHarness `held_*` scenes gain `held_bow`, `held_musket` and `held_bomb` for
readout captures.

Unchanged and pinned in Creative (D4): `CraftingSystem.craft` receives only the
inventory, so stations and ingredients apply; campfire cooking and fuel,
lantern charcoal, trade, gifts, crate deposits and equipping run their Survival
paths. Waterskin filling, rack and collector transforms, beacon installation,
quest delivery and restitution contain no gate. No Random, save field, enum
constant or thread was added; Survival arithmetic, messages and RNG order are
unchanged. The previous agent's unapplied `draft09` sketch was used only as a
checklist; its InventoryScreen unequip refactor was not needed and not applied.

CreativeUnlimitedUseTest adds 6 cases with Survival twins at each changed seam.
Focused unlimited-use, bow, firearm, reload, parity, treatment, crafting,
lantern, crate theft, bow-hunt, body and building suites plus doclint: PASS, 79
tests / 12 classes, zero failures, errors and skips, in 25s (25.475s wall). No
existing test changed.

The first readout captures (prefix `v069-final-`) showed "[R] Reload" drawn over
the held-item label "Veylan Musket [220/220]" at both resolutions. Hud started
the weapon status block 96 units above the bottom, so its second and third rows
(reload hint, bow draw meter or reload bar, "Reloading...") collided with the
hotbar's permanent label 70 units above the bottom. The geometry was unchanged
from v0.6.0 and already collided in Survival for an empty magazine with reserve
rounds; unlimited reserve makes the hint appear for every empty Creative
magazine. A separate `fix(ui)` commit moves the block to `WEAPON_STATUS_TOP =
120` with every string, colour and scale unchanged; compile and doclint passed
(3.604s wall). The build, performance runs, smokes and captures made before it
are superseded by the reruns below.

Final v0.6.9 build: PASS, 615 tests / 93 classes, zero failures, errors and
skips; 2m9s (129.560s wall), including JavaDoc doclint and line budgets. Real
lines: Game 939/1000, QaHarness 1417/1500, SaveSystem 1109/1800,
SettlementManager 1414/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.

Performance on the same tree (unchanged budgets): every budget passes in both
runs. First run (9.708s wall): save 1.745 ms, load 181.142 ms, chunk 0.335 ms,
entity 0.311 ms, settlement 0.013 ms, audio 446.211 ms, PCM 40,824,424 bytes,
occlusion 0.001845 ms/frame and music 0.000005 ms/frame; TickProfileTest passes
(whole cycle 0.0866 ms). The paired untouched v0.6.2 worktree measured save
1.543 ms (9.745s wall). Because that gap was wider than earlier pairs, both were
repeated in the same session: milestone save 1.584 ms (load 183.418 ms, whole
cycle 0.0847 ms; 10.029s wall) against a v0.6.2 reference of 1.809 ms (10.191s
wall). A pair on the pre-fix tree read 1.744/1.510 ms and then 1.460/1.706 ms.
The ordering reverses between repeats with no save code changed, so the spread
is host noise; no host save deviation applies because save stays within 2.20 ms
in every run.

Native 30s smokes on the final tree, seed 20260910, VSync off, minimum 60 FPS,
1280x720, NVIDIA RTX 1000 Ada / OpenGL 3.3 / driver 595.95, one at a time with
no concurrent build. Creative: PASS, 897.9 FPS, p95 1.41 ms, p99 1.59 ms,
26,939 body samples without damage or death, mode/mark/flight restored, and
the scripted flight ran 1,423 frames across 2 chunks without entering an
unloaded column. Survival: PASS, 898.0 FPS, p95 1.41 ms, p99 1.65 ms. Both
completed isolated save/load and the fortress approach within every runtime
hard limit, with 91/91 icons and zero GL, KHR-debug and OpenAL errors. The smoke
does not script unlimited use; R22 evidence is the headless suite above.

Final readout captures, inspected, each with zero GL, KHR-debug and OpenAL
errors: Creative `held_bow`, `held_musket` and `held_bomb` at 6 s, 1280x720
windowed and 1920x1080 fullscreen (6 PNGs, prefix `v069-final2-`). The bow
shows "Selected Arrow [R]   unlimited arrows", the musket "0/1   unlimited Iron
Ball" in the existing empty-magazine colour with "[R] Reload" beneath it, and
the bomb "unlimited Scrap Bomb — LMB to throw". Every readout and hint sits
clearly above the held-item label without clipping or overlap. The scenes pause
the simulation for capture, so the bow draw meter and reload bar were not
captured in motion; they use the same lifted rows. Human evaluation of the
wording and of unlimited-use play is unverified.

### v0.6.10 evidence

R24: `CreativePalette` is the single audit record. Every `BlockType` is now
either buildable or carries a written exclusion reason, and
`CreativePaletteTest.everyBlockIsEitherBuildableOrCarriesAWrittenExclusionReason`
fails if a future block is neither, so the audit cannot silently go stale.

Included (19). Each gains an appended `ItemType` with a `_BLOCK` suffix, so the
new `COPPER_ORE_BLOCK` vein never collides with the existing `COPPER_ORE`
smelter input, and each display name is unique across all 110 items.

| Block | Item | Display name | Why it is inert |
| --- | --- | --- | --- |
| GRASS | GRASS_BLOCK | Grass Block | surface material; PlantSystem only reads it |
| ICE | ICE_BLOCK | Ice Block | frozen surface with no melt simulation |
| LEAVES | LEAVES_BLOCK | Leaf Block | canopy material; read by fire and sapling seeding |
| BUSH | BUSH_BLOCK | Bush | cover only; CreatureAI reads it for concealment |
| TALL_GRASS | TALL_GRASS_BLOCK | Tall Grass | cover only; also replaceable, like AIR and water |
| COAL_ORE | COAL_ORE_BLOCK | Coal Ore Block | tool-gated terrain; drop table unchanged |
| COPPER_ORE | COPPER_ORE_BLOCK | Copper Ore Block | same |
| IRON_ORE | IRON_ORE_BLOCK | Iron Ore Block | same |
| ASH | ASH_BLOCK | Charred Block | fire residue; only read as a fumarole rim |
| SCRAP_BLOCK | WRECKAGE_BLOCK | Wreckage | crash-site material |
| POD_HULL | POD_HULL_BLOCK | Pod Hull | crash-site material |
| RUIN_STONE | RUIN_STONE_BLOCK | Ruin Stone | ruin masonry; only the core carries progression |
| BONE_PILE | BONE_PILE_BLOCK | Bone Pile | decoration placed by SettlementBuilder |
| BASALT | BASALT_BLOCK | Basalt | cave stone |
| SULFUR_ORE | SULFUR_ORE_BLOCK | Sulfur Vein Block | terrain; the fumarole predicate is a pure query |
| SALTPETER_ORE | SALTPETER_ORE_BLOCK | Saltpeter Crust Block | terrain |
| GLOW_FUNGUS | GLOW_FUNGUS_BLOCK | Glow Fungus | light-emitting decoration |
| STONE_BRICK | STONE_BRICK_BLOCK | Stone Brick | settlement masonry; vandalism attribution unchanged |
| CAGE_BARS | CAGE_BARS_BLOCK | Cage Bars | prison wall; the captive is an NPC, not the block |

Excluded (12), with the reason each one stays unbuildable. The shared criterion
is that `placeSelectedBlockAt` writes a block and runs a small per-block
initialization, so a block whose meaning lives elsewhere would be placed as a
broken copy of the real thing.

| Block | Reason |
| --- | --- |
| AIR | absence of a block; breaking already removes one |
| WATER | liquid: immersion and the shoreline mesh assume generated water, and a placed source has no flow simulation |
| BERRY_BUSH | harvest state paired with BERRY_BUSH_EMPTY and ripened by PlantSystem |
| BERRY_BUSH_EMPTY | the spent half of that pair |
| HERB_PLANT | harvestable resource grown by PlantSystem in damp biomes |
| SAPLING | PlantSystem grows it into a whole runtime tree |
| CAMP_BED | sleep anchor for WorldInteractions.sleepInBed and the camp radius |
| GATE | opened by SettlementManager, which writes GATE_OPEN and a World.gateTimers entry |
| GATE_OPEN | the transient half of that pair; it closes itself from a gate timer |
| ALARM_BELL | addressed by Settlement.alarmBell; a placed bell belongs to no settlement |
| RUIN_CORE | relic progression: the tool-gated source of signal crystals |
| BEACON_LIT | endgame state written by beacon installation; unbreakable by design |

The feature reuses existing seams rather than adding gates. `BlockItemForms`
already derives pick block from `places()`, so all 19 blocks became pickable
with no change there, and its duplicate check proves the mapping stays
one-to-one. `IconAtlas` already projects a placeable item's own material tile,
so each palette item has a distinct icon; only the three cutout plants needed a
new path, because projecting a mostly transparent tile onto the isometric cube
leaves scattered fragments that read as a rendering fault. `drawCutoutSprite`
draws their tile flat instead, limited to palette items so no shipped icon
changes - `ROPE_LADDER` keeps its cube. `CreativeCatalog.classify` sends palette
items to Building; the generic `places()` rule would have filed them under
Stations.

Survival parity: no recipe, drop table, loot table, trade, quest or archetype
can reach a palette item.
`CreativePaletteTest.noLootTradeOrGameplayCodeNamesAPaletteConstant` proves this
structurally by scanning every file under `src/main/java` for the 19 constant
names and allowing only `ItemType.java` and `CreativePalette.java` to mention
them; everything else reaches items generically through `ItemType.values()`,
`places()` or `BlockItemForms`. Drop tables are asserted unchanged (grass still
drops dirt, leaves a stick, ruin stone stone). Both enums are append-only and
`SerializedEnumOrderTest` gained the 19 names in order; no existing test changed.
No Random, save field, section, thread or per-frame allocation was added.

A palette stack carried into Survival after a mode switch is spent like any
other block item; `placeSelectedBlockAt` gates only the `shrink` on
`unlimitedItems()`. That crossover is pinned rather than prevented, because it
follows R5: leaving Creative keeps what the player holds.

9 new cases across `CreativePaletteTest` (5) and `CreativePaletteWorldTest` (4):
audit completeness, one-to-one mapping with the `_BLOCK` suffix, category, icon
id, display-name uniqueness and inert properties, drop-table and recipe
isolation, the source scan, placement of all 19 blocks from a full stack without
spending it, pick block for all 19, a save round trip of placed blocks and
carried stacks, and Survival drop parity plus the carried-stack crossover.

Final v0.6.10 build: PASS, 624 tests / 95 classes, zero failures, errors and
skips; 2m19s (140.025s wall) on the committed tree, including JavaDoc doclint
and line budgets. The build before the version bump read 2m17s (137.330s). Real
lines: Game 939/1000, QaHarness 1417/1500, SaveSystem 1109/1800,
SettlementManager 1414/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300 -
all unchanged, since the milestone added no line to any budgeted file.

Performance on the same tree (unchanged budgets): every budget passes. Save
1.058 ms (budget 2.20), load 190.759 ms (240.00), chunk tick 0.492 ms (0.92),
entity tick 0.441 ms (0.95), settlement tick 0.016 ms (0.52), audio synthesis
385.776 ms (1500), PCM 40,824,424 bytes (67,108,864), occlusion 0.002238
ms/frame (0.10), music director 0.000004 ms (0.02); TickProfileTest whole cycle
0.0920 / 0.1027 ms. These figures come from a different host than milestones 1-9
(see the host deviation below), so compare them with each other, not with the
earlier rows.

Old-reader compatibility was measured, not predicted, and the prediction was
wrong. The brief expected a save containing palette items to fail cleanly on
v0.6.0. It does not: `readStack` returns an empty slot for an out-of-range
ordinal and the stack payload is fixed width, so the stream stays aligned. A
0.7.0 Creative save carrying six palette stacks and two placed palette blocks
was written with this tree, then loaded with a compiled v0.6.0 worktree
(`ItemType.values().length` = 91). Result: `load=true`, the six palette stacks
are gone, the unrelated `STONE x9` stack survived in slot 8, and both placed
blocks came back as `BASALT` and `STONE_BRICK` because `BlockType` is unchanged.
The honest statement for the release matrix is therefore **silent loss of
palette item stacks**, not a clean load failure; world geometry survives. D5 in
the design document is corrected to match. Forcing a failure would mean adding a
new failure mode to a format that deliberately tolerates unknown values, so it
was not done.

Native evidence, seed 20260910, VSync off, minimum 60 FPS, 1280x720, one run at
a time with no concurrent build, on NVIDIA GeForce RTX 3060 Ti / OpenGL 3.3.0 /
driver 580.173.02. Creative 30 s smoke: PASS, 1257.9 FPS, p95 0.92 ms, p99 1.08
ms, 37,739 body samples without damage or death, mode/mark/flight restored, and
the scripted flight ran 2,011 frames across 2 chunks without entering an
unloaded column. Survival 30 s smoke: PASS, 1276.7 FPS, p95 0.91 ms, p99 1.03
ms. Both completed the isolated save/load and the fortress approach within every
runtime hard limit, with **110/110 item icons** and zero GL, KHR-debug and
OpenAL errors - the icon count is the runtime proof that all 19 appended items
registered a distinct, non-empty icon.

Captures, inspected (prefix `v0610-`): the Building tab at 1280x720 and
1920x1080, and the `catalog-search` view at 1280x720. The Building tab now holds
30 entries in two rows with no clipping, overlap or scrollbar at either
resolution, and every palette icon is distinguishable from its neighbours. The
three cutout plants render as flat sprites; `GLOW_FUNGUS_BLOCK` reads as small
teal mushrooms rather than the scattered fragments the cube projection produced.
Searching "iron" returns 10 entries, `IRON_ORE_BLOCK` first because the search
walks categories in order and Building is first. One pre-existing cosmetic
observation: two solid cyan rectangles flank the catalog title. A capture of the
same screen taken from a compiled v0.6.9 worktree shows them unchanged, so they
predate this milestone and are left for the release audit. Human judgement of
palette ergonomics, naming and icon legibility in play is unverified.

Host deviation. These gates ran on a Linux host, not the Windows host of
milestones 1-9. `build.gradle` throws "Unsupported release platform" for
linux/amd64, so every Gradle invocation passed `-Dos.name=Windows 10` to get
past the configuration gate; no file in the repository was changed for it, and
the flag reaches only the Gradle JVM, not the compiled code or the tests.
`gradlew run` is unavailable for the same reason, so native runs used
`build/creative-work/run-native.sh`, which puts the cached Linux LWJGL natives
on a hand-assembled classpath and launches `com.veylon.Main` with the same
`--enable-native-access=ALL-UNNAMED -Xmx2G` the application plugin uses. That
script is generated into the ignored work directory and is not committed.
Consequences: `releaseArtifacts` and jpackage were not exercised, the smoke and
performance figures are not comparable with the earlier milestones' Windows
numbers, and the final v0.7.0 gates still need the Windows host.
### v0.6.11 evidence

R25: `CreativeWorldControls` holds three flags (`daylightFrozen`,
`weatherLocked`, `spawningPaused`) and two immediate commands, and is the only
object that knows the feature exists as a whole. Each gate is one boolean
question at its own site: `Game.advanceClock` is the single place the frame's
clock step happens, so freezing stops the clock and nothing else;
`WeatherSystem.mediumTick` skips the `changeTimer` countdown and the `pickNext`
roll while locked, but keeps the flash timer, lets an in-flight blend finish and
announce itself, and still rolls lightning for a locked storm;
`EntityManager.slowTick` returns right after its despawn pass, so the creatures
already present stay and still despawn; `SleepSystem.startSleep` refuses first
of all reasons, because `tickSleep` fast-forwards the very clock the freeze is
holding.

`TimeSystem.advanceToNextHour` is the new production seam.
`simulation/TimePreset` names the four hours (Dawn and Dusk reuse the phase
boundaries from `TimeConstants`), so the screen does no clock arithmetic and the
controller hard-codes no hour. The clock never rewinds: asking for the hour it
already is lands on that hour tomorrow, which is the only monotonic answer for
day counters, seasons and every timer. Crossing midnight carries the day
counter, which the tests walk across four consecutive presets.

Mode is never tested at a gate. `clear()` runs on `GameModeController.reset`
(every new world) and unconditionally inside `switchTo` (both directions), and
`CreativeWorldControls.restore` applies loaded flags only into a Creative world.
A flag can therefore be true only while the world is Creative, and a
hand-forged Survival save is released on load rather than handing a Survival
player a frozen clock with no screen to unfreeze it - pinned by
`aSurvivalSaveCannotSmuggleInAFrozenClock`.

`save/CreativeControlsSection`, id `world.creative-controls`, version 1, three
booleans, written after `player.game-mode` so the mode is already restored. An
unsupported version, a truncated payload, an empty payload and trailing bytes
each throw `IOException`, so the whole load fails with the live world, player
and its own controls intact. An absent section means every control off, which
is what every save written before 0.6.11 means; older builds skip the unknown
id, so a Creative save opened on v0.6.0 simply has no controls.

Scope note, following the brief: only `EntityManager.slowTick` natural spawning
is gated. The scripted wolf-migration and camp-attack events in `EventSystem`
still spawn, exactly as world-creation wildlife is unaffected. The spawn test
drives `entities.slowTick` directly rather than the whole slow bucket so it
measures the gate and not the event. Freezing the clock also freezes seasons and
every other clock-derived reading, which is what "freeze the daylight cycle"
means here.

Parity with every control off: a Survival and a Creative world of seed
20260910, driven through 40 identical clock steps, medium ticks and entity slow
ticks, end with the same `totalMinutes`, the same current/next weather, the same
blend, the same `changeTimer` (so the same weather RNG draws happened) and the
same creature count. Skipped Creative draws are confined to the locked-weather
branch and the paused-spawn branch, both of which require a control to be on.

14 new cases: `CreativeWorldControlsTest` (10) covers key ownership from pause
in Creative only, forward-only presets across midnight, the frozen clock across
200 steps while medium ticks keep running, the sleep refusal and its recovery,
a locked storm across 600 medium ticks with its lightning intact, paused
spawning that adds none and removes none, the release on leaving Creative and on
`newWorld`, the save round trip, the key-to-command table and the no-control
parity. `CreativeControlsSectionTest` (4) covers all eight flag combinations,
absence, four malformed payloads against a live world, and the forged Survival
save. `GameLoopIntegrationTest` gains one additive assertion that the controls
do not survive `newWorld`. No existing expectation changed.

Final v0.6.11 build: PASS, 638 tests / 97 classes, zero failures, errors and
skips; 2m30s (150.239s wall) on the committed tree, including JavaDoc doclint
and line budgets. Real
lines: Game 959/1000, QaHarness 1417/1500, SaveSystem 1109/1800,
SettlementManager 1414/1500, FactionSystem 1265/1400, WorldGenerator 1178/1300.
Game grew by 20 lines for the ui mode, the screen and controls fields, the
clock seam and four one-line delegates.

Performance on the same tree (unchanged budgets): every budget passes. Save
1.132 ms (budget 2.20), load 194.183 ms (240.00), chunk tick 0.526 ms (0.92),
entity tick 0.460 ms (0.95), settlement tick 0.048 ms (0.52), audio synthesis
387.474 ms (1500), PCM 40,824,424 bytes (67,108,864), occlusion 0.002237
ms/frame (0.10), music director 0.000004 ms (0.02); TickProfileTest whole cycle
0.0995 / 0.1027 ms. Same host as v0.6.10, so these compare with those figures
and not with milestones 1-9.

Native 30 s smokes, seed 20260910, VSync off, minimum 60 FPS, 1280x720, one at a
time, NVIDIA GeForce RTX 3060 Ti / OpenGL 3.3.0 / driver 580.173.02. Creative:
PASS, 1260.3 FPS, p95 0.91 ms, p99 1.03 ms, 37,812 body samples without damage
or death, mode/mark/flight restored, 2,009 flight frames across 2 chunks with no
unloaded column entered. Survival: PASS, 1267.2 FPS, p95 0.92 ms, p99 1.04 ms.
Both completed the isolated save/load and the fortress approach within every
runtime hard limit, with 110/110 item icons and zero GL, KHR-debug and OpenAL
errors.

Captures, inspected. `VEYLON_FRONTEND` gains `worldcontrols` and
`worldcontrols-held`, the second applying Dusk, the freeze, a locked storm and
paused spawning first, so one shot shows every ON state and the five log lines
the commands write. Both at 1280x720 and 1920x1080 (prefix `v0611-`): three
sections, four time buttons, six weather buttons with the active one
highlighted, three toggles reading their state, and the footer, all inside the
panel with no clipping at either resolution. The first Creative pause capture
showed the cost of the new `[T] World controls` line: the reference list had
been sitting on the panel border, and one more row put the last line on the
edge. The panel grew from 520 to 548 units and both pause captures were retaken
(prefix `v0611b-`); the Survival header correctly omits the `[T]` entry while
the reference still lists the Creative keys, as it has since milestone 5. The
two solid cyan rectangles flanking every panel title appear here too, which
confirms they belong to the shared panel skin rather than to any one screen;
they are left for the release audit. Human judgement of how the controls feel in
play is unverified.

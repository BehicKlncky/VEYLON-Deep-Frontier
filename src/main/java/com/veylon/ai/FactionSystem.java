package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.util.MathUtil;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.Random;

/**
 * State of the single NPC camp: stocks, trust toward the player, alert level,
 * camp quests and staged settlement upgrades (palisade, comforts, med tent).
 */
public class FactionSystem {

    public Vec3i campPos;
    /** 0..100; below 15 the camp turns hostile. */
    public float trust = 35;
    public float alert = 0;
    public int foodStock = 10;
    public int woodStock = 8;
    public boolean hostile = false;
    /** 0 = bare camp .. 3 = fully built settlement. */
    public int upgradeStage = 0;
    /** While > 0 an NPC works the construction site (cosmetic of a real build). */
    public float buildTimer = 0;
    /** Reward flags so tier perks are granted once. */
    public boolean alliedGiftGiven = false;

    public Quest quest;

    private float foodTimer = 0;
    private float fireTimer = 0;
    private float questCooldown = 60;
    private final Random rng = new Random();

    public void addTrust(Game g, float delta, String reason) {
        trust = MathUtil.clamp(trust + delta, 0, 100);
        if (reason != null && Math.abs(delta) >= 1) {
            g.log(reason + " (trust " + (delta > 0 ? "+" : "") + (int) delta + ")");
        }
        if (trust < 15 && !hostile) {
            hostile = true;
            g.log("The camp has turned HOSTILE toward you!");
        } else if (trust >= 25 && hostile) {
            hostile = false;
            g.log("The camp is no longer hostile.");
        }
        if (delta < 0) {
            alert = Math.min(100, alert + Math.abs(delta) * 2);
        }
        // Allied tier perk: a one-time gift of advanced supplies.
        if (trust >= 75 && !alliedGiftGiven) {
            alliedGiftGiven = true;
            g.player.inventory.add(ItemType.IRON_INGOT, 2);
            g.player.inventory.add(ItemType.MEDICINE, 1);
            g.log("ALLIED with the camp! They share supplies: 2 iron ingots, 1 medicine.");
            g.audio.playQuest();
        }
    }

    public String standing() {
        if (hostile) {
            return "Hostile";
        }
        if (trust >= 75) {
            return "Allied";
        }
        if (trust >= 50) {
            return "Friendly";
        }
        if (trust >= 25) {
            return "Neutral";
        }
        return "Wary";
    }

    /** Camp beds and medic help are open to Friendly+ players. */
    public boolean campPrivileges() {
        return !hostile && trust >= 50;
    }

    // ------------------------------------------------------------------
    // Quests
    // ------------------------------------------------------------------

    /** Offers a quest based on what the camp currently needs. */
    public Quest offerQuest(Game g, String giverName) {
        if (quest != null) {
            return quest;
        }
        if (questCooldown > 0) {
            return null;
        }
        boolean npcSick = false;
        for (Npc n : g.entities.npcs) {
            if (n.sick && !n.isTrader && !n.raider) {
                npcSick = true;
                break;
            }
        }
        if (npcSick || rng.nextFloat() < 0.15f) {
            quest = new Quest(Quest.Type.FETCH, ItemType.MEDICINE, 1, 900,
                    14, ItemType.IRON_INGOT, 1, giverName);
        } else if (foodStock < 8) {
            quest = new Quest(Quest.Type.FETCH, ItemType.COOKED_MEAT, 3, 700,
                    10, ItemType.HIDE, 2, giverName);
        } else if (woodStock < 14) {
            quest = new Quest(Quest.Type.FETCH, ItemType.LOG, 6, 700,
                    8, ItemType.BANDAGE, 2, giverName);
        } else if (rng.nextFloat() < 0.4f) {
            quest = new Quest(Quest.Type.FETCH, ItemType.IRON_ORE, 3, 900,
                    10, ItemType.COOKED_MEAT, 3, giverName);
        } else if (rng.nextFloat() < 0.6f) {
            quest = new Quest(Quest.Type.HUNT_PREDATOR, null, 2, 900,
                    12, ItemType.SPEAR, 1, giverName);
        } else {
            quest = new Quest(Quest.Type.INVESTIGATE, null, 1, 1200,
                    12, ItemType.BLUEPRINT_FRAGMENT, 1, giverName);
        }
        g.audio.playQuest();
        return quest;
    }

    /** Attempts to turn in the active quest; returns a response line. */
    public String turnInQuest(Game g) {
        if (quest == null) {
            return null;
        }
        if (quest.type == Quest.Type.FETCH) {
            if (!g.player.inventory.has(quest.item, quest.required)) {
                return "We still need " + (quest.required - g.player.inventory.count(quest.item))
                        + " more " + quest.item.displayName + ".";
            }
            g.player.inventory.remove(quest.item, quest.required);
            if (quest.item == ItemType.MEDICINE) {
                for (Npc n : g.entities.npcs) {
                    if (n.sick) {
                        n.sick = false;
                        n.mood = Math.min(100, n.mood + 20);
                        g.log(n.name + " takes the medicine and starts to recover.");
                        break;
                    }
                }
            } else if (quest.item.isEdible()) {
                foodStock += quest.required;
            } else if (quest.item == ItemType.LOG) {
                woodStock += quest.required;
            }
        } else if (!quest.complete()) {
            return "Not done yet: " + quest.describe();
        }
        completeQuest(g);
        return "You have our thanks. The camp won't forget this.";
    }

    private void completeQuest(Game g) {
        addTrust(g, quest.trustReward, "Camp request fulfilled");
        if (quest.rewardItem != null) {
            g.player.inventory.add(quest.rewardItem, quest.rewardCount);
            g.log("Reward: " + quest.rewardCount + "x " + quest.rewardItem.displayName);
        }
        g.audio.playQuest();
        quest = null;
        questCooldown = 90 + rng.nextInt(90);
    }

    public void onPredatorKilledNearCamp(Game g) {
        if (quest != null && quest.type == Quest.Type.HUNT_PREDATOR && !quest.complete()) {
            quest.progress++;
            g.log("Camp request: " + quest.describe());
        }
    }

    public void onPoiDiscovered(Game g) {
        if (quest != null && quest.type == Quest.Type.INVESTIGATE && !quest.complete()) {
            quest.progress++;
            g.log("Camp request: " + quest.describe());
        }
    }

    // ------------------------------------------------------------------
    // Slow tick: stocks, fire upkeep, sickness, quests, upgrades
    // ------------------------------------------------------------------

    public void slowTick(Game g, float dt) {
        alert = Math.max(0, alert - 1.5f * dt);
        questCooldown = Math.max(0, questCooldown - dt);
        buildTimer = Math.max(0, buildTimer - dt);
        if (campPos == null) {
            return;
        }

        if (quest != null) {
            quest.timeLeft -= dt;
            if (quest.timeLeft <= 0) {
                g.log("The camp's request expired (" + quest.describe() + ").");
                quest = null;
                questCooldown = 60;
            }
        }

        // NPCs eat from the food stock about once per game hour each.
        foodTimer += dt;
        int campNpcs = 0;
        for (Npc n : g.entities.npcs) {
            if (!n.isTrader && !n.raider && !n.dead) {
                campNpcs++;
            }
        }
        if (foodTimer > 75 && campNpcs > 0) {
            foodTimer = 0;
            if (foodStock > 0) {
                foodStock--;
                for (Npc n : g.entities.npcs) {
                    if (!n.isTrader && !n.raider) {
                        n.hunger = Math.max(0, n.hunger - 25f / campNpcs);
                        n.mood = Math.min(100, n.mood + 2);
                    }
                }
            } else {
                for (Npc n : g.entities.npcs) {
                    if (!n.isTrader && !n.raider) {
                        n.mood = Math.max(0, n.mood - 5);
                    }
                }
            }
        }

        // Sick NPCs slowly worsen; they can die untreated.
        for (Npc n : g.entities.npcs) {
            if (n.sick && !n.dead) {
                n.sickTimer += dt;
                n.mood = Math.max(0, n.mood - 0.5f * dt * 0.1f);
                if (n.sickTimer > 600) {
                    n.health -= 0.4f * dt;
                    if (n.health <= 0) {
                        n.dead = true;
                        n.lastHitByPlayer = false;
                        g.log(n.name + " succumbed to the illness. The camp mourns.");
                        addTrust(g, -5, null);
                    }
                }
            }
        }

        // Keep the camp fire fueled from the wood stock.
        fireTimer += dt;
        if (fireTimer > 60) {
            fireTimer = 0;
            Vec3i firePos = campPos;
            if (g.world.getBlock(firePos.x(), firePos.y(), firePos.z()) == BlockType.CAMPFIRE) {
                float fuel = g.world.campfireFuel.getOrDefault(firePos, 0f);
                if (fuel < 120 && woodStock > 0) {
                    woodStock--;
                    g.world.campfireFuel.put(firePos, fuel + 180);
                }
            } else if (woodStock >= 3) {
                // Rebuild a burned-out camp fire.
                woodStock -= 3;
                g.world.setBlock(firePos.x(), firePos.y(), firePos.z(), BlockType.CAMPFIRE, true);
                g.world.campfireFuel.put(firePos, 300f);
            }
        }

        checkUpgrade(g);
    }

    /** Builds the next settlement stage when trust and wood allow it. */
    private void checkUpgrade(Game g) {
        int cx = campPos.x(), cy = campPos.y(), cz = campPos.z();
        if (upgradeStage == 0 && trust >= 45 && woodStock >= 18) {
            woodStock -= 14;
            buildPalisade(g, cx, cy, cz);
            upgradeStage = 1;
            buildTimer = 60;
            g.log("CAMP UPGRADE: The camp raises a palisade and a watch post!");
            g.audio.playQuest();
        } else if (upgradeStage == 1 && trust >= 60 && woodStock >= 28) {
            woodStock -= 20;
            buildComforts(g, cx, cy, cz);
            upgradeStage = 2;
            buildTimer = 60;
            g.log("CAMP UPGRADE: Beds, storage and a drying rack - the camp grows!");
            g.audio.playQuest();
        } else if (upgradeStage == 2 && trust >= 75 && woodStock >= 34) {
            woodStock -= 24;
            buildMedTent(g, cx, cy, cz);
            upgradeStage = 3;
            buildTimer = 60;
            g.log("CAMP UPGRADE: A medical tent with an herbalist bench is built!");
            g.audio.playQuest();
        }
    }

    private void buildPalisade(Game g, int cx, int cy, int cz) {
        for (int d = -6; d <= 6; d++) {
            // Ring with a gate gap on the south side.
            if (Math.abs(d) <= 1) {
                placeWall(g, cx + d, cz - 6);
            } else {
                placeWall(g, cx + d, cz - 6);
                placeWall(g, cx + d, cz + 6);
            }
            if (Math.abs(d) < 6) {
                placeWall(g, cx - 6, cz + d);
                placeWall(g, cx + 6, cz + d);
            }
        }
        // Watch post: a torch tower at the corner.
        int h = surfaceAt(g, cx + 5, cz + 5);
        for (int i = 1; i <= 3; i++) {
            g.world.setBlock(cx + 5, h + i, cz + 5, BlockType.WALL, true);
        }
        g.world.setBlock(cx + 5, h + 4, cz + 5, BlockType.TORCH, true);
    }

    private void buildComforts(Game g, int cx, int cy, int cz) {
        g.world.setBlock(cx - 3, cy, cz + 1, BlockType.CAMP_BED, true);
        g.world.setBlock(cx - 3, cy, cz - 1, BlockType.CAMP_BED, true);
        g.world.setBlock(cx + 3, cy, cz + 1, BlockType.CRATE, true);
        g.world.crateContents.putIfAbsent(new Vec3i(cx + 3, cy, cz + 1),
                new com.veylon.item.Inventory(12));
        g.world.setBlock(cx + 4, cy, cz - 1, BlockType.DRYING_RACK, true);
        g.world.setBlock(cx, cy, cz + 4, BlockType.CAMPFIRE, true);
        g.world.campfireFuel.putIfAbsent(new Vec3i(cx, cy, cz + 4), 300f);
    }

    private void buildMedTent(Game g, int cx, int cy, int cz) {
        // Small walled tent with herbalist bench and bed.
        for (int dx = -5; dx <= -3; dx++) {
            g.world.setBlock(dx + cx, cy, cz - 5, BlockType.WALL, true);
            g.world.setBlock(dx + cx, cy + 1, cz - 5, BlockType.WALL, true);
            g.world.setBlock(dx + cx, cy + 2, cz - 4, BlockType.PLANK, true);
            g.world.setBlock(dx + cx, cy + 2, cz - 3, BlockType.PLANK, true);
        }
        g.world.setBlock(cx - 5, cy, cz - 4, BlockType.WALL, true);
        g.world.setBlock(cx - 5, cy + 1, cz - 4, BlockType.WALL, true);
        g.world.setBlock(cx - 4, cy, cz - 4, BlockType.HERB_STATION, true);
        g.world.setBlock(cx - 3, cy, cz - 3, BlockType.CAMP_BED, true);
        g.world.setBlock(cx - 5, cy + 1, cz - 3, BlockType.TORCH, true);
    }

    private void placeWall(Game g, int x, int z) {
        int h = surfaceAt(g, x, z);
        g.world.setBlock(x, h + 1, z, BlockType.WALL, true);
        g.world.setBlock(x, h + 2, z, BlockType.WALL, true);
    }

    private int surfaceAt(Game g, int x, int z) {
        return g.world.surfaceHeight(x, z);
    }
}

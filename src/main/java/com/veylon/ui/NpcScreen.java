package com.veylon.ui;

import com.veylon.Game;
import com.veylon.ai.Quest;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;

import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;

/** NPC interaction menu: talk, trade (cheaper when Friendly), gift, quests, attack. */
public class NpcScreen {

    private final Random rng = new Random();
    private String lastResponse = "";

    public void open() {
        lastResponse = "";
    }

    public void update(Game g) {
        Npc n = g.activeNpc;
        if (n == null || n.dead) {
            g.closeScreens();
            return;
        }
        n.interactFreeze = 1.0f;
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();

        boolean friendly = g.faction.trust >= 50;
        // Friendly camps trade at better rates.
        int berryCost = friendly ? 2 : 3;
        int meatCost = friendly ? 1 : 2;

        float pw = 600, ph = 430;
        float x0 = w / 2f - pw / 2f, y0 = h / 2f - ph / 2f;
        ui.rect(x0, y0, pw, ph, 0.07f, 0.07f, 0.1f, 0.94f);
        ui.rectOutline(x0, y0, pw, ph, 2, 0.6f, 0.6f, 0.7f, 0.9f);
        ui.textCentered(w / 2f, y0 + 12, 2f, n.name, 1f, 1f, 0.9f, 1f);

        String sub = n.isTrader
                ? "Wandering trader"
                : n.jobName() + " - " + g.faction.standing() + " (trust " + (int) g.faction.trust + ")";
        ui.textCentered(w / 2f, y0 + 36, 1.3f, sub, 0.8f, 0.85f, 0.9f, 1f);
        ui.textCentered(w / 2f, y0 + 54, 1.2f,
                "Mood " + (int) n.mood + "   Hunger " + (int) n.hunger + "   HP " + (int) n.health
                        + (n.sick ? "   SICK" : ""),
                n.sick ? 0.6f : 0.7f, n.sick ? 0.9f : 0.7f, n.sick ? 0.5f : 0.7f, 1f);

        float oy = y0 + 86;
        option(ui, x0 + 30, oy, "[1] Talk");
        oy += 26;
        if (n.isTrader) {
            option(ui, x0 + 30, oy, "[2] Trade: 1 Iron Ore -> 2 Cooked Meat");
            oy += 26;
            option(ui, x0 + 30, oy, "[3] Trade: 5 Berries -> 2 Coal");
            oy += 26;
            option(ui, x0 + 30, oy, "[4] Trade: 2 Hide -> 1 Blueprint Fragment");
            oy += 26;
        } else {
            option(ui, x0 + 30, oy, "[2] Trade: " + berryCost + " Berries -> 1 Plank"
                    + (friendly ? " (friend price)" : ""));
            oy += 26;
            option(ui, x0 + 30, oy, "[3] Trade: " + meatCost + " Raw Meat -> 3 Berries"
                    + (friendly ? " (friend price)" : ""));
            oy += 26;
            Quest quest = g.faction.quest;
            if (quest == null) {
                option(ui, x0 + 30, oy, "[4] Ask if the camp needs anything");
            } else {
                option(ui, x0 + 30, oy, "[4] Camp request: " + quest.describe()
                        + "  ->  " + quest.rewardText());
            }
            oy += 26;
        }
        ItemStack sel = g.player.selected();
        option(ui, x0 + 30, oy, "[5] Gift held item" + (sel != null
                ? " (" + sel.type.displayName + ")" : " (nothing held)"));
        oy += 26;
        option(ui, x0 + 30, oy, "[6] Attack");
        oy += 36;

        if (!lastResponse.isEmpty()) {
            ui.textShadow(x0 + 30, oy, 1.4f, '"' + lastResponse + '"', 0.95f, 0.9f, 0.7f, 1f);
        }
        ui.textCentered(w / 2f, y0 + ph - 24, 1.2f, "[Esc or F] leave", 0.7f, 0.7f, 0.7f, 1f);

        if (g.input.wasKeyPressed(GLFW_KEY_1)) {
            talk(g, n);
        } else if (g.input.wasKeyPressed(GLFW_KEY_2)) {
            if (n.isTrader) {
                trade(g, ItemType.IRON_ORE, 1, ItemType.COOKED_MEAT, 2);
            } else {
                trade(g, ItemType.BERRY, berryCost, ItemType.PLANK, 1);
            }
        } else if (g.input.wasKeyPressed(GLFW_KEY_3)) {
            if (n.isTrader) {
                trade(g, ItemType.BERRY, 5, ItemType.COAL, 2);
            } else {
                trade(g, ItemType.RAW_MEAT, meatCost, ItemType.BERRY, 3);
            }
        } else if (g.input.wasKeyPressed(GLFW_KEY_4)) {
            if (n.isTrader) {
                trade(g, ItemType.HIDE, 2, ItemType.BLUEPRINT_FRAGMENT, 1);
            } else {
                questAction(g, n);
            }
        } else if (g.input.wasKeyPressed(GLFW_KEY_5)) {
            gift(g, n);
        } else if (g.input.wasKeyPressed(GLFW_KEY_6)) {
            attack(g, n);
        }
    }

    private void option(UiRenderer ui, float x, float y, String s) {
        ui.textShadow(x, y, 1.5f, s, 0.9f, 0.95f, 1f, 1f);
    }

    private void questAction(Game g, Npc n) {
        if (g.faction.hostile) {
            lastResponse = "We want nothing from you.";
            return;
        }
        Quest quest = g.faction.quest;
        if (quest == null) {
            quest = g.faction.offerQuest(g, n.name);
            if (quest == null) {
                lastResponse = "We're managing for now. Check back later.";
            } else {
                lastResponse = "Actually, yes. " + quest.describe() + " - we'll make it worth it: "
                        + quest.rewardText() + ".";
                g.log("Camp request accepted: " + quest.describe());
            }
            return;
        }
        String result = g.faction.turnInQuest(g);
        lastResponse = result != null ? result : "We're managing for now.";
    }

    private void talk(Game g, Npc n) {
        if (n.isTrader) {
            lastResponse = "Fine goods, fair prices. The roads are dangerous, friend.";
            return;
        }
        if (g.faction.hostile) {
            lastResponse = "Leave. Now.";
            return;
        }
        if (n.sick) {
            lastResponse = "*coughs* I'm not well... we need medicine. An herbalist bench can make it.";
            return;
        }
        if (g.faction.trust >= 75) {
            String[] allied = {
                    "Our fires are your fires, friend.",
                    "If you ever raise that beacon, we'll give you the calibration codes.",
                    "The medic will patch you up whenever you need it."
            };
            lastResponse = allied[rng.nextInt(allied.length)];
        } else if (g.faction.trust >= 50) {
            String[] friendlyLines = {
                    "Good to see you. The wolves were close last night.",
                    "The berries to the east are ripening well.",
                    "You are always welcome at our fire. Use the beds if you need."
            };
            lastResponse = friendlyLines[rng.nextInt(friendlyLines.length)];
            if (rng.nextFloat() < 0.25f) {
                g.player.inventory.add(ItemType.BERRY, 2);
                lastResponse = "Take these berries, friend. You look hungry.";
                g.log(n.name + " gave you 2 berries.");
            }
        } else if (g.faction.trust >= 25) {
            lastResponse = "We watch the frontier. Prove yourself and we may trade more.";
        } else {
            lastResponse = "We don't trust strangers. Keep your distance.";
        }
        n.mood = Math.min(100, n.mood + 2);
    }

    private void trade(Game g, ItemType give, int giveN, ItemType get, int getN) {
        if (g.faction.hostile && g.activeNpc != null && !g.activeNpc.isTrader) {
            lastResponse = "No trade with you.";
            return;
        }
        if (g.player.inventory.has(give, giveN)) {
            g.player.inventory.remove(give, giveN);
            g.player.inventory.add(get, getN);
            lastResponse = "A fair trade.";
            g.audio.playClick();
            g.log("Traded " + giveN + " " + give.displayName + " for " + getN + " " + get.displayName);
            if (!g.activeNpc.isTrader) {
                g.faction.addTrust(g, 2, null);
            }
        } else {
            lastResponse = "You don't have the goods. (" + giveN + " " + give.displayName + " needed)";
        }
    }

    private void gift(Game g, Npc n) {
        ItemStack sel = g.player.selected();
        if (sel == null) {
            lastResponse = "You offer... nothing?";
            return;
        }
        ItemType t = sel.type;
        g.player.inventory.shrink(g.player.hotbarSel, 1);
        n.mood = Math.min(100, n.mood + 5);
        if (t == ItemType.MEDICINE && n.sick) {
            n.sick = false;
            g.faction.addTrust(g, 12, "You cured " + n.name + " with medicine!");
            lastResponse = "The fever breaks almost at once... I owe you my life.";
            return;
        }
        if (n.isTrader) {
            lastResponse = "Generous! Safe travels to you.";
            return;
        }
        float gain = t.isEdible() ? 8 : 5;
        if (t.isEdible()) {
            g.faction.foodStock++;
        }
        g.faction.addTrust(g, gain, "You gave " + n.name + " a " + t.displayName);
        lastResponse = "A gift? The camp will remember this.";
    }

    private void attack(Game g, Npc n) {
        ItemStack sel = g.player.selected();
        float dmg = sel != null ? sel.type.damage : 2f;
        n.hurt(dmg, true);
        n.knockback(g.player.pos.x, g.player.pos.z, 3f);
        g.audio.playHit();
        if (!n.isTrader) {
            g.faction.addTrust(g, -30, "You attacked " + n.name + "!");
        }
        g.log("You attacked " + n.name + " (-" + (int) dmg + " HP)");
        g.closeScreens();
    }
}

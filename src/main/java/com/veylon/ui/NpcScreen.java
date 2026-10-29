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
        // A speaker who died or is fleeing their own fire is gone from the conversation.
        if (n == null || n.dead || n.panic.active()) {
            g.closeScreens();
            return;
        }
        n.interactFreeze = 1.0f;
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();

        com.veylon.settlement.UrbanSite site = com.veylon.settlement.UrbanSiteDirector.siteOf(g.world, n);
        if (site != null) {
            updateSite(g, n, site, ui, w, h);
            return;
        }

        com.veylon.settlement.Settlement home =
                n.settled() ? g.world.settlements.get(n.settlementId) : null;
        boolean friendly = home != null
                ? home.friendly() || home.localReputation >= 40
                : g.faction.trust >= 50;
        // Friendly camps trade at better rates.
        int berryCost = friendly ? 2 : 3;
        int meatCost = friendly ? 1 : 2;

        float pw = 600, ph = 430;
        float x0 = w / 2f - pw / 2f, y0 = h / 2f - ph / 2f;
        ui.panel(x0, y0, pw, ph);
        ui.textCentered(w / 2f, y0 + 12, 2f, n.name, 1f, 1f, 0.9f, 1f);

        boolean localQuestProvider = home != null && !home.hostile()
                && (n.isTrader || n.archetype != null && n.archetype.leader);
        String sub;
        if (home != null) {
            sub = n.jobName() + " of the " + home.label()
                    + " (standing " + (int) home.localReputation + ")";
        } else if (n.isTrader) {
            sub = "Wandering trader";
        } else {
            sub = n.jobName() + " - " + g.faction.standing()
                    + " (trust " + (int) g.faction.trust + ")";
        }
        ui.textCentered(w / 2f, y0 + 36, 1.3f, sub, 0.8f, 0.85f, 0.9f, 1f);
        ui.textCentered(w / 2f, y0 + 54, 1.2f,
                "Mood " + (int) n.mood + "   Hunger " + (int) n.hunger + "   HP " + (int) n.health
                        + (n.sick ? "   SICK" : ""),
                n.sick ? 0.6f : 0.7f, n.sick ? 0.9f : 0.7f, n.sick ? 0.5f : 0.7f, 1f);

        float oy = y0 + 86;
        option(ui, x0 + 30, oy, "[1] Talk");
        oy += 26;
        if (n.isTrader) {
            tradeOption(ui, x0 + 30, oy, "[2]", ItemType.IRON_ORE, 1,
                    ItemType.COOKED_MEAT, 2, "");
            oy += 26;
            tradeOption(ui, x0 + 30, oy, "[3]", ItemType.BERRY, 5,
                    ItemType.COAL, 2, "");
            oy += 26;
            if (localQuestProvider) {
                Quest quest = g.faction.quest;
                option(ui, x0 + 30, oy, quest == null
                        ? "[4] Ask what the settlement needs"
                        : "[4] Local request: " + quest.describe() + " -> " + quest.rewardText());
            } else {
                tradeOption(ui, x0 + 30, oy, "[4]", ItemType.HIDE, 2,
                        ItemType.BLUEPRINT_FRAGMENT, 1, "");
            }
            oy += 26;
        } else {
            tradeOption(ui, x0 + 30, oy, "[2]", ItemType.BERRY, berryCost,
                    ItemType.PLANK, 1, friendly ? "friend price" : "");
            oy += 26;
            tradeOption(ui, x0 + 30, oy, "[3]", ItemType.RAW_MEAT, meatCost,
                    ItemType.BERRY, 3, friendly ? "friend price" : "");
            oy += 26;
            if (home != null) {
                if (localQuestProvider) {
                    Quest quest = g.faction.quest;
                    option(ui, x0 + 30, oy, quest == null
                            ? "[4] Ask what the settlement needs"
                            : "[4] Local request: " + quest.describe() + "  ->  " + quest.rewardText());
                } else {
                    option(ui, x0 + 30, oy, "[4] Ask about the " + home.type.displayName.toLowerCase());
                }
            } else {
                Quest quest = g.faction.quest;
                if (quest == null) {
                    option(ui, x0 + 30, oy, "[4] Ask if the camp needs anything");
                } else {
                    option(ui, x0 + 30, oy, "[4] Camp request: " + quest.describe()
                            + "  ->  " + quest.rewardText());
                }
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
                performTrade(g, n, ItemType.IRON_ORE, 1, ItemType.COOKED_MEAT, 2);
            } else {
                performTrade(g, n, ItemType.BERRY, berryCost, ItemType.PLANK, 1);
            }
        } else if (g.input.wasKeyPressed(GLFW_KEY_3)) {
            if (n.isTrader) {
                performTrade(g, n, ItemType.BERRY, 5, ItemType.COAL, 2);
            } else {
                performTrade(g, n, ItemType.RAW_MEAT, meatCost, ItemType.BERRY, 3);
            }
        } else if (g.input.wasKeyPressed(GLFW_KEY_4)) {
            if (localQuestProvider) {
                performSettlementQuestAction(g, n, home);
            } else if (n.isTrader) {
                performTrade(g, n, ItemType.HIDE, 2, ItemType.BLUEPRINT_FRAGMENT, 1);
            } else if (home != null) {
                if (localQuestProvider) {
                    performSettlementQuestAction(g, n, home);
                } else {
                    lastResponse = settlementStatus(home);
                }
            } else {
                performCampQuestAction(g, n);
            }
        } else if (g.input.wasKeyPressed(GLFW_KEY_5)) {
            performGift(g, n);
        } else if (g.input.wasKeyPressed(GLFW_KEY_6)) {
            performAttack(g, n);
        }
    }

    /**
     * A conversation with someone of an urban survivor camp or hidden refuge (milestone 16): no
     * legacy quests or wilderness trades, only what those people deal in.
     */
    private void updateSite(Game g, Npc n, com.veylon.settlement.UrbanSite site, UiRenderer ui, int w, int h) {
        float pw = 600, ph = 420;
        float x0 = w / 2f - pw / 2f, y0 = h / 2f - ph / 2f;
        ui.panel(x0, y0, pw, ph);
        ui.textCentered(w / 2f, y0 + 12, 2f, n.name, 1f, 1f, 0.9f, 1f);
        ui.textCentered(w / 2f, y0 + 36, 1.3f, n.jobName() + " - " + site.label(), 0.8f, 0.85f, 0.9f, 1f);
        ui.textCentered(w / 2f, y0 + 54, 1.2f, com.veylon.settlement.UrbanSiteDialogue.status(g, n),
                0.7f, 0.7f, 0.7f, 1f);
        float oy = y0 + 86;
        for (String line : siteOptions(g, n)) {
            option(ui, x0 + 30, oy, line);
            oy += 26;
        }
        oy += 10;
        if (!lastResponse.isEmpty()) {
            ui.textShadow(x0 + 30, oy, 1.4f, '"' + lastResponse + '"', 0.95f, 0.9f, 0.7f, 1f);
        }
        ui.textCentered(w / 2f, y0 + ph - 24, 1.2f, "[Esc or F] leave", 0.7f, 0.7f, 0.7f, 1f);
        for (int key = 1; key <= 9; key++) {
            if (g.input.wasKeyPressed(GLFW_KEY_0 + key)) {
                performSiteChoice(g, n, key);
                break;
            }
        }
    }

    /**
     * The numbered choices a survivor-site conversation offers, as shown: talk, aid and a gift
     * for anyone; then a camp or stronghold trader's two offers, a medic's dressing, for hidden
     * civilians the word of a safe camp, or a stronghold gatekeeper's leave to come in; then a
     * threat and an attack; to a stronghold, news of a raider yard the player has taken; and, in layout
     * sixteen, the old radio relay ({@link com.veylon.settlement.UrbanComms}).
     */
    public java.util.List<String> siteOptions(Game g, Npc n) {
        com.veylon.settlement.UrbanSite site = com.veylon.settlement.UrbanSiteDirector.siteOf(g.world, n);
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("[1] Talk");
        out.add("[2] Give food and water");
        ItemStack sel = g.player.selected();
        out.add("[3] Give held item" + (sel != null ? " (" + sel.type.displayName + ")" : " (nothing held)"));
        if (site != null) {
            var offers = com.veylon.settlement.UrbanSiteDialogue.offers(g, n);
            var role = site.resident(n.residentIndex).role;
            if (!offers.isEmpty()) {
                out.add("[4] Trade " + offers.get(0).describe());
                out.add("[5] Trade " + offers.get(1).describe());
            } else if ((site.camp() || site.stronghold()) && role == com.veylon.world.urban.SurvivorSites.Role.MEDIC) {
                out.add("[4] Ask to have your wounds dressed");
            } else if (site.refuge()) {
                out.add(com.veylon.settlement.UrbanSiteDialogue.campFor(g, site) == null
                        ? "[4] Tell them of a safe place (you know none)" : "[4] Tell them about the camp you found");
            } else if (site.stronghold() && role == com.veylon.world.urban.SurvivorSites.Role.GATEKEEPER) {
                out.add(com.veylon.settlement.CompoundAccess.hasPass(g, site)
                        ? "[4] Ask to be let through (you have a visitor's pass)" : "[4] Ask to be let in");
            }
        }
        out.add("[6] Demand their supplies");
        out.add("[7] Attack");
        if (site != null && site.stronghold() && com.veylon.settlement.CompoundAccess.unpaidYard(g) != null) {
            out.add("[8] Tell them the raider yard is taken");
        }
        // Layout sixteen: the old relay, its rumour and its codes (urban milestone 20).
        String radio = com.veylon.settlement.UrbanComms.radioOption(g, n);
        if (radio != null) { out.add(radio); }
        return out;
    }

    /**
     * Gameplay command behind a numbered choice in a survivor-site conversation, shared by the
     * keys and the tests. Returns false for a number the conversation does not offer.
     */
    public boolean performSiteChoice(Game g, Npc n, int choice) {
        com.veylon.settlement.UrbanSite site = com.veylon.settlement.UrbanSiteDirector.siteOf(g.world, n);
        if (n == null || n.dead || site == null) { return false; }
        boolean trader = !com.veylon.settlement.UrbanSiteDialogue.offers(g, n).isEmpty();
        var role = site.resident(n.residentIndex).role;
        switch (choice) {
            case 1 -> lastResponse = com.veylon.settlement.UrbanSiteDialogue.talk(g, n);
            case 2 -> lastResponse = com.veylon.settlement.UrbanSiteDialogue.giveSupplies(g, n);
            case 3 -> lastResponse = com.veylon.settlement.UrbanSiteDialogue.giveHeld(g, n);
            case 4 -> {
                if (trader) {
                    lastResponse = com.veylon.settlement.UrbanSiteDialogue.trade(g, n, 0);
                } else if ((site.camp() || site.stronghold()) && role == com.veylon.world.urban.SurvivorSites.Role.MEDIC) {
                    lastResponse = com.veylon.settlement.UrbanSiteDialogue.treat(g, n);
                } else if (site.refuge()) {
                    lastResponse = com.veylon.settlement.UrbanSiteDialogue.guide(g, n);
                } else if (site.stronghold() && role == com.veylon.world.urban.SurvivorSites.Role.GATEKEEPER) {
                    var plan = site.compoundPlan(g.world.urban);
                    if (plan == null) { return false; }
                    lastResponse = com.veylon.settlement.CompoundAccess.admit(g, site, plan, n);
                } else {
                    return false;
                }
            }
            case 5 -> {
                if (!trader) { return false; }
                lastResponse = com.veylon.settlement.UrbanSiteDialogue.trade(g, n, 1);
            }
            case 6 -> lastResponse = com.veylon.settlement.UrbanSiteDialogue.demand(g, n);
            case 7 -> performAttack(g, n);
            case 8 -> {
                if (!site.stronghold() || com.veylon.settlement.CompoundAccess.unpaidYard(g) == null) { return false; }
                lastResponse = com.veylon.settlement.CompoundAccess.reward(g, site);
            }
            case 9 -> {
                if (com.veylon.settlement.UrbanComms.radioOption(g, n) == null) { return false; }
                lastResponse = com.veylon.settlement.UrbanComms.askAboutRadio(g, n);
            }
            default -> { return false; }
        }
        return true;
    }

    private void option(UiRenderer ui, float x, float y, String s) {
        ui.textShadow(x, y, 1.5f, s, 0.9f, 0.95f, 1f, 1f);
    }

    private void tradeOption(UiRenderer ui, float x, float y, String key,
                             ItemType give, int giveCount, ItemType receive, int receiveCount,
                             String suffix) {
        ui.textShadow(x, y + 2, 1.35f, key, 0.9f, 0.95f, 1f, 1f);
        ui.itemIcon(give, x + 38, y - 2, 22);
        ui.textShadow(x + 64, y + 2, 1.2f, giveCount + " " + give.displayName,
                0.88f, 0.9f, 0.92f, 1f);
        ui.textShadow(x + 236, y + 2, 1.25f, "->", 0.55f, 0.9f, 0.92f, 1f);
        ui.itemIcon(receive, x + 263, y - 2, 22);
        ui.textShadow(x + 289, y + 2, 1.2f, receiveCount + " " + receive.displayName,
                0.95f, 0.9f, 0.68f, 1f);
        if (!suffix.isEmpty()) {
            ui.textShadow(x + 445, y + 2, 1.05f, suffix, 0.55f, 0.95f, 0.62f, 1f);
        }
    }

    /**
     * Gameplay command seam shared by the GLFW dialogue key path and tests.
     * It performs the same provider validation/acquisition/turn-in work as the
     * visible NPC menu; callers do not receive a manager-only shortcut.
     */
    public String performQuestAction(Game g, Npc n) {
        if (n == null || n.dead || n.hostileToPlayer()) {
            lastResponse = "There is no safe conversation to have.";
            return lastResponse;
        }
        com.veylon.settlement.Settlement home =
                n.settled() ? g.world.settlements.get(n.settlementId) : null;
        boolean localProvider = home != null && !home.hostile()
                && (n.isTrader || n.archetype != null && n.archetype.leader);
        if (localProvider) {
            return performSettlementQuestAction(g, n, home);
        }
        if (home != null) {
            lastResponse = settlementStatus(home);
            return lastResponse;
        }
        if (n.isTrader) {
            lastResponse = "I trade on the road; local leaders post marked requests.";
            return lastResponse;
        }
        return performCampQuestAction(g, n);
    }

    public String performCampQuestAction(Game g, Npc n) {
        if (g.faction.hostile) {
            lastResponse = "We want nothing from you.";
            return lastResponse;
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
            return lastResponse;
        }
        String result = g.faction.turnInQuest(g);
        lastResponse = result != null ? result : "We're managing for now.";
        return lastResponse;
    }

    public String performSettlementQuestAction(Game g, Npc n,
                                               com.veylon.settlement.Settlement settlement) {
        if (n == null || settlement == null || settlement.hostile()) {
            lastResponse = "This settlement is not offering requests.";
            return lastResponse;
        }
        Quest quest = g.faction.quest;
        if (quest == null) {
            quest = g.faction.offerSettlementQuest(g, n, settlement);
            if (quest == null) {
                lastResponse = "We are managing for now. Ask again later.";
            } else {
                lastResponse = "We could use your help. " + quest.describe()
                        + " - reward: " + quest.rewardText() + ".";
                g.log("Local request accepted: " + quest.describe());
            }
            return lastResponse;
        }
        String result = g.faction.turnInSettlementQuest(g, settlement);
        lastResponse = result != null ? result : "We are managing for now.";
        return lastResponse;
    }

    public String lastResponse() {
        return lastResponse;
    }

    private String settlementStatus(com.veylon.settlement.Settlement s) {
        String food = s.foodStock > 10 ? "Stores are full" : s.foodStock > 4
                ? "We get by" : "Food is short";
        String danger = s.alertLevel > 50 ? "and there's trouble about"
                : "and the walls hold";
        return food + ", " + danger + ". "
                + s.aliveResidents() + " of us live here.";
    }

    private void talk(Game g, Npc n) {
        // Settlement residents speak for their community, not the starter camp.
        if (n.settled()) {
            var s = g.world.settlements.get(n.settlementId);
            if (s != null) {
                if (s.localReputation >= 40 || s.occupied) {
                    lastResponse = "Good to see a friend on the frontier.";
                } else if (s.localReputation <= -10) {
                    lastResponse = "You've caused us trouble. Watch yourself.";
                } else {
                    lastResponse = switch (rng.nextInt(3)) {
                        case 0 -> "Strange lights in the deep caves lately...";
                        case 1 -> "Headhunters prowl these parts. Keep your blade close.";
                        default -> "The land is hard, but we manage.";
                    };
                }
                g.settlementManager.addLocalReputation(g, s, 0.5f, null);
                n.mood = Math.min(100, n.mood + 2);
                return;
            }
        }
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

    /** Gameplay command shared by the visible dialogue choices and integration tests. */
    public boolean performTrade(Game g, Npc n, ItemType give, int giveN,
                                ItemType get, int getN) {
        if (n == null || n.dead || n.hostileToPlayer()) {
            lastResponse = "There is no safe trade to make.";
            return false;
        }
        if (g.faction.hostile && !n.settled() && !n.isTrader) {
            lastResponse = "No trade with you.";
            return false;
        }
        if (g.player.inventory.has(give, giveN)) {
            g.player.inventory.remove(give, giveN);
            g.player.inventory.add(get, getN);
            lastResponse = "A fair trade.";
            g.audio.playClick();
            g.log("Traded " + giveN + " " + give.displayName + " for " + getN + " " + get.displayName);
            if (n.settled()) {
                var s = g.world.settlements.get(n.settlementId);
                if (s != null) {
                    g.settlementManager.onTradeCompleted(g, s, give, giveN);
                }
            } else if (!n.isTrader) {
                g.faction.addTrust(g, 2, null);
            }
            return true;
        } else {
            lastResponse = "You don't have the goods. (" + giveN + " " + give.displayName + " needed)";
            return false;
        }
    }

    /** Gameplay command shared by the visible gift option and integration tests. */
    public boolean performGift(Game g, Npc n) {
        if (n == null || n.dead || n.hostileToPlayer()) {
            lastResponse = "There is no safe exchange to make.";
            return false;
        }
        ItemStack sel = g.player.selected();
        if (sel == null) {
            lastResponse = "You offer... nothing?";
            return false;
        }
        ItemType t = sel.type;
        g.player.inventory.shrink(g.player.hotbarSel, 1);
        n.mood = Math.min(100, n.mood + 5);
        if (t == ItemType.MEDICINE && n.sick) {
            if (n.settled()) {
                g.settlementManager.onResidentHealed(g, n);
            } else {
                n.sick = false;
                g.faction.addTrust(g, 12, "You cured " + n.name + " with medicine!");
            }
            lastResponse = "The fever breaks almost at once... I owe you my life.";
            return true;
        }
        if (n.settled()) {
            var s = g.world.settlements.get(n.settlementId);
            if (s != null) {
                g.settlementManager.onGiftGiven(g, s, t, 1);
                lastResponse = "A gift? The " + s.type.displayName.toLowerCase()
                        + " will remember this.";
                return true;
            }
        }
        if (n.isTrader) {
            lastResponse = "Generous! Safe travels to you.";
            return true;
        }
        float gain = t.isEdible() ? 8 : 5;
        if (t.isEdible()) {
            g.faction.foodStock++;
        }
        g.faction.addTrust(g, gain, "You gave " + n.name + " a " + t.displayName);
        lastResponse = "A gift? The camp will remember this.";
        return true;
    }

    /** Dialogue attack command; the [6] input path uses this exact operation. */
    public boolean performAttack(Game g, Npc n) {
        if (n == null || n.dead) {
            return false;
        }
        ItemStack sel = g.player.selected();
        float dmg = sel != null ? sel.type.damage : 2f;
        n.hurt(dmg, true);
        n.knockback(g.player.pos.x, g.player.pos.z, 3f);
        g.audio.playHit();
        if (n.settled()) {
            g.settlementManager.onNpcAttackedByPlayer(g, n);
        } else if (!n.isTrader && !n.warParty) {
            g.faction.addTrust(g, -30, "You attacked " + n.name + "!");
        }
        g.log("You attacked " + n.name + " (-" + (int) dmg + " HP)");
        g.closeScreens();
        return true;
    }
}

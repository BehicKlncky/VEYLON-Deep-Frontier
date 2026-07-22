package com.veylon.entity;

import com.veylon.item.ItemStack;

/** Applies medical-item effects without depending on rendering, audio or input. */
public final class PlayerTreatmentSystem {

    /** Stable outcomes let the orchestration layer retain the existing messages. */
    public enum Result {
        BANDAGED(true, "You bandage the wound. Bleeding stopped."),
        NO_BLEEDING(false, "No bleeding to bandage."),
        SPLINTED(true, "You splint your leg. You can move normally again."),
        NO_SPRAIN(false, "Nothing needs splinting."),
        INFECTION_CURED(true, "The antiseptic burns away the infection."),
        WOUND_CLEANED(true, "You disinfect the wound - it won't fester now. Still needs a bandage."),
        NO_WOUND(false, "No wound to disinfect."),
        BURN_TREATED(true, "The poultice soothes your burns."),
        INFECTION_REDUCED(true, "The poultice draws out some of the infection."),
        NOTHING_FOR_POULTICE(false, "No burns or infection to treat."),
        ILLNESS_CURED(true, "The medicine works fast. You feel much better."),
        NO_ILLNESS(false, "You aren't sick enough to need medicine."),
        INVALID(false, "Nothing happens.");

        private final boolean used;
        private final String message;

        Result(boolean used, String message) {
            this.used = used;
            this.message = message;
        }

        public boolean used() {
            return used;
        }

        public String message() {
            return message;
        }
    }

    /** Mutates only player medical state; inventory consumption remains caller-owned. */
    public Result apply(Player player, ItemStack held) {
        if (player == null || held == null) {
            return Result.INVALID;
        }
        return switch (held.type) {
            case BANDAGE -> {
                if (!player.has(Affliction.BLEEDING)) {
                    yield Result.NO_BLEEDING;
                }
                player.cure(Affliction.BLEEDING);
                player.woundClean = true;
                player.health = Math.min(player.maxHealth, player.health + 3);
                yield Result.BANDAGED;
            }
            case SPLINT -> {
                if (!player.has(Affliction.SPRAIN)) {
                    yield Result.NO_SPRAIN;
                }
                player.cure(Affliction.SPRAIN);
                yield Result.SPLINTED;
            }
            case ANTISEPTIC -> {
                if (player.has(Affliction.INFECTION)) {
                    player.cure(Affliction.INFECTION);
                    yield Result.INFECTION_CURED;
                }
                if (player.has(Affliction.BLEEDING)) {
                    player.woundClean = true;
                    yield Result.WOUND_CLEANED;
                }
                yield Result.NO_WOUND;
            }
            case HERBAL_POULTICE -> {
                if (player.has(Affliction.BURN)) {
                    player.cure(Affliction.BURN);
                    yield Result.BURN_TREATED;
                }
                if (player.has(Affliction.INFECTION)) {
                    player.afflictions.computeIfPresent(
                            Affliction.INFECTION, (affliction, seconds) -> seconds * 0.4f);
                    yield Result.INFECTION_REDUCED;
                }
                yield Result.NOTHING_FOR_POULTICE;
            }
            case MEDICINE -> {
                if (!player.has(Affliction.FOOD_POISONING)
                        && !player.has(Affliction.SICKNESS)
                        && !player.has(Affliction.INFECTION)) {
                    yield Result.NO_ILLNESS;
                }
                player.cure(Affliction.FOOD_POISONING);
                player.cure(Affliction.SICKNESS);
                player.cure(Affliction.INFECTION);
                yield Result.ILLNESS_CURED;
            }
            default -> Result.INVALID;
        };
    }
}

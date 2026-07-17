package com.veylon.ui;

import com.veylon.Game;
import com.veylon.ai.FactionSystem;
import com.veylon.ai.Quest;
import com.veylon.settlement.Settlement;
import com.veylon.util.Vec3i;
import com.veylon.world.Poi;

/**
 * Read-only projection of a persisted quest target into player-facing navigation.
 *
 * <p>The resolver never guesses a replacement target. It follows the quest's
 * stable settlement/provider/POI identifiers exactly, so a missing or corrupt
 * identity produces no marker instead of pointing at unrelated world state.</p>
 */
public final class QuestObjectiveView {

    public enum Phase {
        TARGET,
        RETURN_TO_PROVIDER
    }

    /** Exact target identity and world position shared by the HUD and map. */
    public record Objective(String questId, String targetId, Vec3i position,
                            Phase phase, boolean discovered) {
    }

    private QuestObjectiveView() {
    }

    public static Objective resolve(Game game) {
        if (game == null || game.world == null || game.faction.quest == null) {
            return null;
        }
        Quest quest = game.faction.quest;
        return switch (quest.status) {
            case ACTIVE -> activeTarget(game, quest);
            case READY_TO_TURN_IN -> turnInTarget(game, quest);
            case COMPLETED, FAILED, EXPIRED, LEGACY_UNBOUND -> null;
        };
    }

    /** Generic navigation text; it deliberately contains no hidden settlement metadata. */
    public static String navigationLabel(Game game) {
        Objective objective = resolve(game);
        if (objective == null) {
            return "";
        }
        double dx = objective.position.x() + 0.5 - game.player.pos.x;
        double dz = objective.position.z() + 0.5 - game.player.pos.z;
        int metres = (int) Math.round(Math.hypot(dx, dz));
        String action = objective.phase == Phase.TARGET
                ? "Marked objective" : "Return to request provider";
        return action + " - " + metres + "m " + compass(dx, dz) + "  [M]";
    }

    private static Objective activeTarget(Game game, Quest quest) {
        if (quest.targetSettlementId != Quest.NO_SETTLEMENT) {
            Settlement settlement = game.world.settlements.get(quest.targetSettlementId);
            if (settlement == null) {
                return null;
            }
            String targetId = FactionSystem.settlementProviderId(settlement.id);
            if (!quest.destinationId.isBlank() && !quest.destinationId.equals(targetId)) {
                return null;
            }
            return new Objective(quest.instanceId, targetId, settlement.center,
                    Phase.TARGET, settlement.discovered);
        }

        if (quest.targetPoiId != null && !quest.targetPoiId.isBlank()) {
            for (Poi poi : game.world.pois) {
                if (quest.targetPoiId.equals(FactionSystem.poiId(poi))) {
                    return new Objective(quest.instanceId, quest.targetPoiId, poi.pos,
                            Phase.TARGET, poi.discovered);
                }
            }
            if (quest.targetPoiId.startsWith("camp-region:") && game.faction.campPos != null) {
                return new Objective(quest.instanceId, quest.targetPoiId, game.faction.campPos,
                        Phase.TARGET, true);
            }
        }
        return null;
    }

    private static Objective turnInTarget(Game game, Quest quest) {
        if ("camp".equals(quest.rewardProviderId)) {
            if (game.faction.campPos == null) {
                return null;
            }
            return new Objective(quest.instanceId, "camp", game.faction.campPos,
                    Phase.RETURN_TO_PROVIDER, true);
        }
        if (quest.giverSettlementId == Quest.NO_SETTLEMENT) {
            return null;
        }
        Settlement provider = game.world.settlements.get(quest.giverSettlementId);
        if (provider == null) {
            return null;
        }
        String providerId = FactionSystem.settlementProviderId(provider.id);
        if (!providerId.equals(quest.rewardProviderId)) {
            return null;
        }
        return new Objective(quest.instanceId, providerId, provider.center,
                Phase.RETURN_TO_PROVIDER, provider.discovered);
    }

    private static String compass(double dx, double dz) {
        double angle = Math.atan2(dx, -dz);
        int sector = Math.floorMod((int) Math.round(angle / (Math.PI / 4.0)), 8);
        return switch (sector) {
            case 0 -> "N";
            case 1 -> "NE";
            case 2 -> "E";
            case 3 -> "SE";
            case 4 -> "S";
            case 5 -> "SW";
            case 6 -> "W";
            default -> "NW";
        };
    }
}

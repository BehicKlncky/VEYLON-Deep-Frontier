package com.veylon.ai;

import com.veylon.item.ItemType;

/** A camp request the player can fulfil for trust and rewards. */
public class Quest {

    public enum Type {
        FETCH("Delivery"),
        HUNT_PREDATOR("Hunt"),
        INVESTIGATE("Scout");

        public final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }
    }

    public Type type;
    /** Item to deliver for FETCH quests. */
    public ItemType item;
    public int required;
    public int progress;
    /** Real seconds before the request expires. */
    public float timeLeft;
    public int trustReward;
    public ItemType rewardItem;
    public int rewardCount;
    public String giverName;

    public Quest(Type type, ItemType item, int required, float timeLeft,
                 int trustReward, ItemType rewardItem, int rewardCount, String giverName) {
        this.type = type;
        this.item = item;
        this.required = required;
        this.timeLeft = timeLeft;
        this.trustReward = trustReward;
        this.rewardItem = rewardItem;
        this.rewardCount = rewardCount;
        this.giverName = giverName;
    }

    public boolean complete() {
        return progress >= required;
    }

    public String describe() {
        String task = switch (type) {
            case FETCH -> "Bring " + required + "x " + item.displayName;
            case HUNT_PREDATOR -> "Kill " + required + " predator(s) near the camp";
            case INVESTIGATE -> "Discover a point of interest";
        };
        return task + "  (" + progress + "/" + required + ")";
    }

    public String rewardText() {
        return "+" + trustReward + " trust"
                + (rewardItem != null ? ", " + rewardCount + "x " + rewardItem.displayName : "");
    }
}

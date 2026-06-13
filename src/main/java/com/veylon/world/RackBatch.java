package com.veylon.world;

import com.veylon.item.ItemType;

/** Items currently drying on a drying rack block. */
public class RackBatch {

    public ItemType input;
    public int count;
    /** Real seconds of drying completed. */
    public float progress;

    public RackBatch(ItemType input, int count) {
        this.input = input;
        this.count = count;
    }

    /** Real seconds needed for this input to finish drying. */
    public float required() {
        return input == ItemType.RAW_MEAT ? 240f : 180f;
    }

    public ItemType output() {
        return input == ItemType.RAW_MEAT ? ItemType.DRIED_MEAT : ItemType.DRIED_BERRY;
    }

    public boolean done() {
        return progress >= required();
    }
}

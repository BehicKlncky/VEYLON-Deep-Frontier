package com.veylon.item;

import com.veylon.world.BlockType;

import java.util.EnumMap;

/** R21: item forms come from placement, never from drops (a beacon drops its frame). */
public final class BlockItemForms {
    private static final EnumMap<BlockType, ItemType> FORMS = new EnumMap<>(BlockType.class);

    static {
        for (ItemType item : ItemType.values()) {
            BlockType block = item.places();
            if (block != null && FORMS.put(block, item) != null) {
                throw new IllegalStateException("Multiple item forms for " + block);
            }
        }
    }

    private BlockItemForms() { }

    public static ItemType of(BlockType block) { return FORMS.get(block); }
}

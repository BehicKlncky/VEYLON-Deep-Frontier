package com.veylon.engine;

/** Four independently synthesized takes, selected only when a voice can actually play. */
final class VariantBank {
    /** Takes per frequent sound; four balances repetition against startup PCM memory. */
    static final int COUNT = 4;
    static final String[] NAMES = {"FootGrass", "FootStone", "FootWood", "FootSnow", "FootWater",
            "HitSoft", "HitStone", "HitWood", "Break", "Place", "Hit", "Swing", "BowRelease",
            "ArrowImpact", "BulletImpact", "Flap", "Musket", "Pistol"};
    private final int[] buffers;
    private int cursor;

    VariantBank(int[] buffers) {
        if (buffers.length < 2) throw new IllegalArgumentException("A bank requires distinct takes");
        this.buffers = buffers.clone();
        for (int i = 0; i < buffers.length; i++) {
            for (int j = 0; j < i; j++) {
                if (buffers[i] == buffers[j]) throw new IllegalArgumentException("Duplicate take");
            }
        }
    }

    static String key(String name, int index) { return index == 0 ? name : name + "Variant" + index; }

    int next() {
        int buffer = buffers[cursor];
        cursor = (cursor + 1) % buffers.length;
        return buffer;
    }
}

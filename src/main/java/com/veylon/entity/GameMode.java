package com.veylon.entity;

/** Stable save IDs decouple mode persistence from enum declaration order. */
public enum GameMode {
    SURVIVAL("survival"),
    CREATIVE("creative");

    public final String id;

    GameMode(String id) {
        this.id = id;
    }

    public static GameMode fromId(String id) {
        for (GameMode mode : values()) {
            if (mode.id.equals(id)) return mode;
        }
        throw new IllegalArgumentException("Unknown game mode: " + id);
    }
}

package com.veylon.item;

/** Body slots the player can equip gear into. */
public enum EquipSlot {
    HEAD("Head"),
    TORSO("Torso"),
    LEGS("Legs"),
    FEET("Feet"),
    BACK("Back");

    public final String displayName;

    EquipSlot(String displayName) {
        this.displayName = displayName;
    }
}

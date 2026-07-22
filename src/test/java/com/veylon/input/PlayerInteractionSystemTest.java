package com.veylon.input;

import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerInteractionSystemTest {

    private final PlayerInteractionSystem system = new PlayerInteractionSystem();
    private final Vector3f direction = new Vector3f(0, 0, -1);

    @Test
    void rangedPrimaryKeepsSecondaryThenInteractOrdering() {
        Recorder recorder = new Recorder();
        var input = new PlayerInteractionSystem.FrameInput().set(
                0.05f, true, true, true, true, false);
        ItemStack held = new ItemStack(ItemType.PRIMITIVE_BOW, 1);
        WeaponDefinition weapon = WeaponRegistry.of(held.type);

        system.update(input, held, weapon, direction, recorder);

        assertEquals("ranged,secondary,interact", recorder.calls.toString());
    }

    @Test
    void nonRangedHeldPrimaryRunsAfterStateCleanup() {
        Recorder recorder = new Recorder();
        var input = new PlayerInteractionSystem.FrameInput().set(
                0.05f, true, true, false, false, false);

        system.update(input, null, null, direction, recorder);

        assertEquals("cooldown,cancel,primary", recorder.calls.toString());
    }

    @Test
    void releasedPrimaryResetsMiningBeforeSecondaryAndInteract() {
        Recorder recorder = new Recorder();
        var input = new PlayerInteractionSystem.FrameInput().set(
                0.05f, false, false, true, true, false);

        system.update(input, null, null, direction, recorder);

        assertEquals("cooldown,cancel,reset,secondary,interact", recorder.calls.toString());
    }

    private static final class Recorder implements PlayerInteractionSystem.Commands {
        private final StringJoiner calls = new StringJoiner();

        @Override
        public void updateRanged(float dt, ItemStack held, WeaponDefinition weapon,
                                 Vector3f direction,
                                 PlayerInteractionSystem.FrameInput input) {
            calls.add("ranged");
        }

        @Override
        public void advanceNonRangedCooldown(float dt) {
            calls.add("cooldown");
        }

        @Override
        public void cancelRangedState() {
            calls.add("cancel");
        }

        @Override
        public void updatePrimary(float dt, Vector3f direction, boolean primaryPressed) {
            calls.add("primary");
        }

        @Override
        public void resetPrimary() {
            calls.add("reset");
        }

        @Override
        public void useSecondary() {
            calls.add("secondary");
        }

        @Override
        public void interact() {
            calls.add("interact");
        }
    }

    private static final class StringJoiner {
        private final StringBuilder value = new StringBuilder();

        void add(String part) {
            if (!value.isEmpty()) {
                value.append(',');
            }
            value.append(part);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}

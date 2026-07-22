package com.veylon.input;

import com.veylon.combat.WeaponDefinition;
import com.veylon.item.ItemStack;
import org.joml.Vector3f;

/**
 * Routes high-level gameplay actions after raw device state and UI mode have
 * been resolved. The command boundary preserves ranged precedence and the
 * existing one-pass ordering without depending on GLFW or the full Game class.
 */
public final class PlayerInteractionSystem {

    /** Reusable frame input buffer. */
    public static final class FrameInput {
        public float dt;
        public boolean primaryHeld;
        public boolean primaryPressed;
        public boolean secondaryPressed;
        public boolean interactPressed;
        public boolean reloadPressed;

        public FrameInput set(float dt, boolean primaryHeld, boolean primaryPressed,
                              boolean secondaryPressed, boolean interactPressed,
                              boolean reloadPressed) {
            this.dt = dt;
            this.primaryHeld = primaryHeld;
            this.primaryPressed = primaryPressed;
            this.secondaryPressed = secondaryPressed;
            this.interactPressed = interactPressed;
            this.reloadPressed = reloadPressed;
            return this;
        }
    }

    /** Narrow execution surface implemented by the game orchestration layer. */
    public interface Commands {
        void updateRanged(float dt, ItemStack held, WeaponDefinition weapon,
                          Vector3f direction, FrameInput input);

        void advanceNonRangedCooldown(float dt);

        void cancelRangedState();

        void updatePrimary(float dt, Vector3f direction, boolean primaryPressed);

        void resetPrimary();

        void useSecondary();

        void interact();
    }

    /** Routes one gameplay frame while preserving ranged action precedence. */
    public void update(FrameInput input, ItemStack held, WeaponDefinition weapon,
                       Vector3f direction, Commands commands) {
        if (weapon != null) {
            commands.updateRanged(input.dt, held, weapon, direction, input);
            if (input.secondaryPressed) {
                commands.useSecondary();
            }
            if (input.interactPressed) {
                commands.interact();
            }
            return;
        }

        commands.advanceNonRangedCooldown(input.dt);
        commands.cancelRangedState();
        if (input.primaryHeld) {
            commands.updatePrimary(input.dt, direction, input.primaryPressed);
        } else {
            commands.resetPrimary();
        }
        if (input.secondaryPressed) {
            commands.useSecondary();
        }
        if (input.interactPressed) {
            commands.interact();
        }
    }
}

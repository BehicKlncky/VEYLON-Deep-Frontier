package com.veylon;

import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.input.PlayerInteractionSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import org.joml.Vector3f;

/**
 * Every way the player deals damage: melee swings, bows, firearms and thrown
 * explosives, plus the durability and reputation consequences of each.
 *
 * <p>The public command methods ({@code updateBowCommand}, {@code
 * updateFirearmCommand}, {@code performPlayerAttack}, ...) are the production
 * paths the native GLFW input loop drives. Gameplay tests call the same methods
 * through {@link Game} rather than synthesizing input events, so there is one
 * implementation of every combat rule.
 *
 * <p>Transient aim state that the HUD draws — {@code bowDraw}, {@code
 * drawingBow}, {@code reloadTimer}, {@code reloadTotal} — deliberately stays on
 * {@code Game}, because that is where the HUD and the save format read it. This
 * class owns the state nothing outside combat needs to see.
 */
final class PlayerCombatSystem {

    /** Melee reach in blocks; also the radius {@link #findAttackTarget} searches. */
    private static final double MELEE_REACH = 3.4;
    private static final double MELEE_REACH_SQ = MELEE_REACH * MELEE_REACH;
    /** Minimum cos(angle) between aim and target for a melee swing to connect. */
    private static final double MELEE_AIM_DOT = 0.80;
    /** Seconds between melee swings. */
    private static final float MELEE_COOLDOWN = 0.45f;
    /** Damage multiplier for striking from a crouch. */
    private static final float AMBUSH_DAMAGE_MULT = 1.4f;
    /** Damage of a bare-handed swing. */
    private static final float UNARMED_DAMAGE = 1.5f;
    private static final float MELEE_KNOCKBACK = 3.2f;
    private static final float MELEE_NOISE_RADIUS = 14f;
    private static final float MELEE_NOISE_STRENGTH = 0.3f;
    private static final float MELEE_NOISE_SELF = 0.25f;
    /** Seconds a struck creature keeps bleeding. */
    private static final float MELEE_BLEED_SECONDS = 18f;

    /** Draw fraction below which releasing the bow cancels instead of firing. */
    private static final float BOW_MIN_RELEASE_DRAW = 0.3f;
    /** Arrow speed scale at zero draw; full draw reaches 1.0. */
    private static final float BOW_MIN_POWER = 0.4f;
    private static final float BOW_POWER_RANGE = 0.6f;
    private static final float BOW_NOISE_STRENGTH = 0.25f;

    /** Cooldown applied after an empty-chamber trigger pull. */
    private static final float DRY_FIRE_COOLDOWN = 0.4f;
    private static final float GUNSHOT_NOISE_STRENGTH = 1f;
    /** Degrees of upward camera kick per unit of weapon recoil. */
    private static final float RECOIL_PITCH_SCALE = 4.5f;
    private static final float RECOIL_SHAKE_SCALE = 0.35f;

    // Swing animation lengths, in seconds.
    private static final float SWING_MELEE = 0.35f;
    private static final float SWING_WHIFF = 0.35f;
    private static final float SWING_BOW = 0.2f;
    private static final float SWING_FIREARM = 0.25f;
    private static final float SWING_THROW = 0.3f;

    /** Durability spent by one melee swing. */
    private static final float MELEE_DURABILITY_COST = 1f;
    /** Durability spent by one skinning pass. */
    private static final float KNIFE_DURABILITY_COST = 1.5f;

    /** Trust granted for slaying a predator within sight of the starter camp. */
    private static final int CAMP_PREDATOR_TRUST = 10;
    /** Blocks from the camp centre within which a predator kill is credited. */
    private static final float CAMP_PREDATOR_RADIUS = 22f;
    private static final int RAIDER_KILL_TRUST = 8;
    /** Trust lost for attacking an unaffiliated, non-hostile NPC. */
    private static final int NPC_ATTACK_TRUST_PENALTY = -30;

    private final Game game;

    /** Seconds until the next ranged shot is permitted. */
    private float rangedCooldown;
    /** Seconds until the next melee swing is permitted. */
    private float attackCooldown;
    /** Preferred arrow type; the native bow input cycles it with R. */
    private ItemType selectedBowAmmo = ItemType.ARROW;
    /** Reloads bind to one inventory slot and the exact stack instance. */
    private int reloadSlot = -1;
    private ItemStack reloadStack;
    private String reloadWeaponId;

    PlayerCombatSystem(Game game) {
        this.game = game;
    }

    /**
     * Clears per-world combat state so a new or loaded world starts clean.
     *
     * <p>Both cooldowns are cleared. Up to {@value #MELEE_COOLDOWN}s of melee
     * cooldown used to survive a world change, which meant the first swing in a
     * new or loaded world could silently do nothing; combat state belongs to the
     * world being left, not the one being entered.
     */
    void reset() {
        rangedCooldown = 0;
        attackCooldown = 0;
        selectedBowAmmo = ItemType.ARROW;
        cancelReload();
    }

    // ------------------------------------------------------------------
    // Melee
    // ------------------------------------------------------------------

    /** Non-ranged primary action: entity attack has precedence over block mining. */
    void updatePrimaryAction(float dt, Vector3f dir, boolean primaryPressed) {
        Entity victim = findAttackTarget(dir);
        if (victim != null) {
            game.miningProgress = 0;
            game.miningTarget = null;
            performPlayerAttack(victim);
        } else if (game.targetHit != null) {
            game.mine(dt);
        } else {
            if (primaryPressed) {
                game.swingTimer = SWING_WHIFF;
                game.audio.playSwing();
            }
            game.miningProgress = 0;
            game.miningTarget = null;
        }
    }

    /** Advances the same melee cooldown used by the native LMB path. */
    void advancePlayerAttackCooldown(float dt) {
        attackCooldown = Math.max(0f, attackCooldown - Math.max(0f, dt));
    }

    /**
     * Gameplay melee command shared by native input and integration tests.
     * Selection/aim remain the caller's job; this command enforces the live
     * reach, cooldown, damage, durability and reputation path.
     *
     * @return true only when the swing actually landed
     */
    boolean performPlayerAttack(Entity victim) {
        if (game.player == null || game.player.dead || victim == null || victim.dead
                || attackCooldown > 0f
                || victim.distSqTo(game.player.pos.x, game.player.pos.y, game.player.pos.z)
                        > MELEE_REACH_SQ) {
            return false;
        }
        attackCooldown = MELEE_COOLDOWN;
        game.swingTimer = SWING_MELEE;
        attack(victim);
        return true;
    }

    private void attack(Entity victim) {
        ItemStack held = game.player.selected();
        float dmg = held != null ? held.type.damage : UNARMED_DAMAGE;
        if (game.player.crouching) {
            dmg *= AMBUSH_DAMAGE_MULT;
        }
        victim.hurt(dmg, true);
        victim.knockback(game.player.pos.x, game.player.pos.z, MELEE_KNOCKBACK);
        game.audio.playHit();
        game.particles.blood(victim.pos.x, victim.pos.y + victim.height * 0.6f, victim.pos.z);
        consumeDurability(held, MELEE_DURABILITY_COST);
        game.player.noise = Math.min(1f, game.player.noise + MELEE_NOISE_SELF);
        game.noise.emit(game, victim.pos.x, victim.pos.y + victim.height * 0.5f, victim.pos.z,
                MELEE_NOISE_RADIUS, MELEE_NOISE_STRENGTH,
                victim instanceof Creature ? "creature-attack" : "melee", true, game.player);
        if (victim instanceof Creature c) {
            c.fear = 1f;
            c.bleedTimer = Math.max(c.bleedTimer, MELEE_BLEED_SECONDS);
        }
        if (victim instanceof Npc n) {
            if (n.settled()) {
                game.settlementManager.onNpcAttackedByPlayer(game, n);
            } else if (!n.isTrader && !n.raider && !n.warParty) {
                game.faction.addTrust(game, NPC_ATTACK_TRUST_PENALTY,
                        "You attacked " + n.name + "!");
            }
        }
    }

    /** Nearest entity inside melee reach that the player is actually looking at. */
    private Entity findAttackTarget(Vector3f dir) {
        Entity best = null;
        double bestD = MELEE_REACH_SQ;
        for (Creature c : game.entities.creatures) {
            double d = candidateDist(c, dir);
            if (d >= 0 && d < bestD) {
                bestD = d;
                best = c;
            }
        }
        for (Npc n : game.entities.npcs) {
            double d = candidateDist(n, dir);
            if (d >= 0 && d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    /** Squared distance to {@code e}, or -1 when out of reach or off-aim. */
    private double candidateDist(Entity e, Vector3f dir) {
        float ex = e.pos.x - game.camera.position.x;
        float ey = (e.pos.y + e.height * 0.5f) - game.camera.position.y;
        float ez = e.pos.z - game.camera.position.z;
        double dist2 = ex * ex + ey * ey + ez * ez;
        if (dist2 > MELEE_REACH_SQ || dist2 < 1e-4) {
            return -1;
        }
        double len = Math.sqrt(dist2);
        double dot = (ex * dir.x + ey * dir.y + ez * dir.z) / len;
        return dot > MELEE_AIM_DOT ? dist2 : -1;
    }

    // ------------------------------------------------------------------
    // Ranged weapons (bow / firearms / thrown)
    // ------------------------------------------------------------------

    /** Routes a held ranged weapon to the command for its category. */
    void updateRangedWeapon(float dt, ItemStack held, WeaponDefinition weapon, Vector3f dir,
                            PlayerInteractionSystem.FrameInput frameInput) {
        game.miningProgress = 0;
        game.miningTarget = null;

        switch (weapon.category) {
            case BOW -> {
                if (frameInput.reloadPressed) {
                    cycleBowAmmo();
                }
                updateBowCommand(dt, frameInput.primaryHeld, frameInput.primaryPressed, dir);
            }
            case FIREARM -> updateFirearmCommand(dt, frameInput.primaryHeld,
                    frameInput.primaryPressed, frameInput.reloadPressed, dir);
            case THROWN -> updateThrownWeaponCommand(dt, frameInput.primaryPressed, dir);
        }
    }

    /** Drops bow draw and any in-flight reload, e.g. when the player switches items. */
    void cancelRangedState() {
        game.drawingBow = false;
        game.bowDraw = 0;
        cancelReload();
    }

    void advanceRangedCooldown(float dt) {
        rangedCooldown = Math.max(0, rangedCooldown - Math.max(0, dt));
    }

    /**
     * Production firearm command shared by native GLFW input and gameplay
     * integration tests. Semi-automatic weapons require the trigger-press edge;
     * automatic weapons deliberately keep firing while the trigger is held.
     * Reload completion remains in {@link #tickReload(float)}, the same method
     * advanced once per native action frame.
     */
    Game.FirearmCommandResult updateFirearmCommand(float dt, boolean triggerHeld,
                                                   boolean triggerPressed,
                                                   boolean reloadPressed,
                                                   Vector3f dir) {
        advanceRangedCooldown(dt);
        ItemStack held = game.player == null ? null : game.player.selected();
        WeaponDefinition weapon = WeaponRegistry.of(held == null ? null : held.type);
        if (game.player == null || game.player.dead || weapon == null
                || weapon.category != WeaponDefinition.Category.FIREARM || dir == null) {
            return Game.FirearmCommandResult.INVALID_WEAPON;
        }

        if (game.reloadTimer > 0) {
            return Game.FirearmCommandResult.RELOADING;
        }

        boolean wantsFire = weapon.automatic ? triggerHeld : triggerPressed;
        Game.FirearmCommandResult result = Game.FirearmCommandResult.NONE;
        if (wantsFire) {
            if (rangedCooldown > 0) {
                return Game.FirearmCommandResult.COOLDOWN;
            }
            if (held.charge > 0) {
                fireFirearm(held, weapon, dir);
                result = Game.FirearmCommandResult.FIRED;
            } else {
                game.audio.playDryFire();
                rangedCooldown = DRY_FIRE_COOLDOWN;
                if (game.player.inventory.count(weapon.ammo) >= weapon.ammoPerShot) {
                    game.log("Not loaded — press [R] to reload.");
                } else {
                    game.log("Out of " + weapon.ammo.displayName + ".");
                }
                result = Game.FirearmCommandResult.DRY_FIRE;
            }
        }

        if (reloadPressed && held.charge < weapon.magazine) {
            boolean started = startReload(held, weapon);
            if (result == Game.FirearmCommandResult.NONE) {
                result = started ? Game.FirearmCommandResult.RELOAD_STARTED
                        : Game.FirearmCommandResult.NO_AMMO;
            }
        }
        return result;
    }

    /**
     * Selected ammunition displayed by the HUD. If only one kind remains, the
     * selection follows that available kind without overriding a deliberate
     * choice while both basic and iron arrows are present.
     */
    ItemType selectedBowAmmo() {
        if (game.player != null && game.player.inventory.count(selectedBowAmmo) <= 0) {
            ItemType fallback = selectedBowAmmo == ItemType.ARROW
                    ? ItemType.IRON_ARROW : ItemType.ARROW;
            if (game.player.inventory.count(fallback) > 0) {
                selectedBowAmmo = fallback;
            }
        }
        return selectedBowAmmo;
    }

    /** Native [R] bow command: deliberately choose basic versus iron arrows. */
    ItemType cycleBowAmmo() {
        if (game.player == null) {
            return selectedBowAmmo;
        }
        boolean basic = game.player.inventory.count(ItemType.ARROW) > 0;
        boolean iron = game.player.inventory.count(ItemType.IRON_ARROW) > 0;
        if (basic && iron) {
            selectedBowAmmo = selectedBowAmmo == ItemType.ARROW
                    ? ItemType.IRON_ARROW : ItemType.ARROW;
        } else if (iron) {
            selectedBowAmmo = ItemType.IRON_ARROW;
        } else if (basic) {
            selectedBowAmmo = ItemType.ARROW;
        } else {
            // Still let the player choose what they intend to craft or recover.
            selectedBowAmmo = selectedBowAmmo == ItemType.ARROW
                    ? ItemType.IRON_ARROW : ItemType.ARROW;
        }
        game.drawingBow = false;
        game.bowDraw = 0;
        game.log("Selected " + selectedBowAmmo.displayName + ".");
        return selectedBowAmmo;
    }

    /** The selected arrow type, or null when none of it remains. */
    private ItemType bowAmmo() {
        ItemType selected = selectedBowAmmo();
        return game.player.inventory.count(selected) > 0 ? selected : null;
    }

    /**
     * Production bow command used directly by the GLFW input path. Holding
     * advances draw, releasing either fires or cancels a short draw, and every
     * call advances the same attack cooldown used during normal play.
     */
    Game.BowCommandResult updateBowCommand(float dt, boolean triggerHeld,
                                           boolean triggerPressed, Vector3f dir) {
        advanceRangedCooldown(dt);
        ItemStack held = game.player == null ? null : game.player.selected();
        WeaponDefinition weapon = WeaponRegistry.of(held == null ? null : held.type);
        if (weapon == null || weapon.category != WeaponDefinition.Category.BOW || dir == null) {
            game.drawingBow = false;
            game.bowDraw = 0;
            return Game.BowCommandResult.INVALID_WEAPON;
        }

        ItemType arrow = bowAmmo();
        if (triggerHeld) {
            if (arrow == null) {
                game.drawingBow = false;
                game.bowDraw = 0;
                if (triggerPressed) {
                    game.log("No arrows. Craft them from sticks, stone and fiber.");
                }
                return Game.BowCommandResult.NO_AMMO;
            }
            if (rangedCooldown > 0) {
                return Game.BowCommandResult.COOLDOWN;
            }
            if (!game.drawingBow) {
                game.drawingBow = true;
                game.bowDraw = 0;
                game.audio.playBowDraw();
            }
            game.bowDraw = Math.min(1f, game.bowDraw + Math.max(0, dt) / weapon.drawTime);
            return Game.BowCommandResult.DRAWING;
        }

        if (!game.drawingBow) {
            return Game.BowCommandResult.NONE;
        }
        if (game.bowDraw >= BOW_MIN_RELEASE_DRAW && arrow != null) {
            fireBow(held, weapon, arrow, dir);
            game.drawingBow = false;
            game.bowDraw = 0;
            return Game.BowCommandResult.FIRED;
        }
        game.drawingBow = false;
        game.bowDraw = 0;
        return Game.BowCommandResult.CANCELLED;
    }

    private void fireBow(ItemStack held, WeaponDefinition weapon, ItemType arrow, Vector3f dir) {
        game.player.inventory.remove(arrow, 1);
        float power = BOW_MIN_POWER + BOW_POWER_RANGE * game.bowDraw;
        Vector3f o = game.camera.position;
        // Under-drawn arrows fly slower, so the shot is retro-scaled after it
        // spawns. Scale exactly what this call put in the air: at the live
        // projectile cap `fire` adds nothing, and searching the list for "an
        // arrow" instead found the newest earlier one and decelerated a shot
        // the player had already released at full draw.
        int spawned = game.projectiles.fire(game, game.player, true, o.x, o.y - 0.08f, o.z,
                dir.x, dir.y, dir.z, weapon, arrow);
        game.projectiles.scaleNewestVelocities(spawned, power);
        game.audio.playBowRelease(o.x, o.y, o.z);
        game.noise.emit(game, o.x, o.y, o.z, weapon.noiseRadius, BOW_NOISE_STRENGTH,
                "bow", true, game.player);
        consumeDurability(held, weapon.durabilityCost);
        rangedCooldown = weapon.attackInterval;
        game.swingTimer = Math.max(game.swingTimer, SWING_BOW);
    }

    private void fireFirearm(ItemStack held, WeaponDefinition weapon, Vector3f dir) {
        held.charge--;
        Vector3f o = game.camera.position;
        game.projectiles.fire(game, game.player, true, o.x, o.y - 0.05f, o.z,
                dir.x, dir.y, dir.z, weapon, weapon.ammo);
        boolean pistol = held.type == ItemType.FLINTLOCK_PISTOL;
        game.audio.playGunshot(pistol, o.x, o.y, o.z);
        game.particles.muzzleFlash(o.x + dir.x * 0.8f, o.y + dir.y * 0.8f - 0.15f,
                o.z + dir.z * 0.8f, dir.x, dir.y, dir.z);
        // Gunshots are enormous noise events: everything hears them.
        game.noise.emit(game, o.x, o.y, o.z, weapon.noiseRadius, GUNSHOT_NOISE_STRENGTH,
                "gunshot", true, game.player);
        game.player.noise = 1f;
        game.camera.pitch -= weapon.recoil * RECOIL_PITCH_SCALE;
        game.renderer.addShake(weapon.recoil * RECOIL_SHAKE_SCALE);
        consumeDurability(held, weapon.durabilityCost);
        rangedCooldown = weapon.attackInterval;
        game.swingTimer = Math.max(game.swingTimer, SWING_FIREARM);
    }

    // ------------------------------------------------------------------
    // Reloading
    // ------------------------------------------------------------------

    private boolean startReload(ItemStack held, WeaponDefinition weapon) {
        if (held == null || weapon == null
                || weapon.category != WeaponDefinition.Category.FIREARM
                || held.charge >= weapon.magazine) {
            return false;
        }
        if (game.player.inventory.count(weapon.ammo) < weapon.ammoPerShot) {
            game.log("No " + weapon.ammo.displayName + " to reload with.");
            game.audio.playDryFire();
            return false;
        }
        game.reloadTotal = weapon.reloadTime;
        game.reloadTimer = game.reloadTotal;
        reloadSlot = game.player.hotbarSel;
        reloadStack = held;
        reloadWeaponId = weapon.id;
        game.audio.playReload();
        return true;
    }

    private void finishReload(ItemStack held, WeaponDefinition weapon) {
        ItemStack current = game.player.selected();
        if (current == null || current != held || WeaponRegistry.of(current.type) != weapon) {
            return; // weapon switched away mid-reload
        }
        int roomRounds = weapon.magazine - held.charge;
        int haveRounds = game.player.inventory.count(weapon.ammo) / weapon.ammoPerShot;
        int loaded = Math.min(roomRounds, haveRounds);
        if (loaded > 0) {
            game.player.inventory.remove(weapon.ammo, loaded * weapon.ammoPerShot);
            held.charge += loaded;
            game.log(held.type.displayName + " loaded ("
                    + held.charge + "/" + weapon.magazine + ").");
        }
    }

    /** Gameplay/test seam: starts a reload using the currently selected firearm. */
    boolean startReloadSelected() {
        ItemStack held = game.player == null ? null : game.player.selected();
        return startReload(held, WeaponRegistry.of(held == null ? null : held.type));
    }

    /**
     * Advances the active reload. Switching slots/stacks cancels it before ammo
     * is consumed; save/load intentionally cancels because this transient state
     * is not serialized. Ammo is transferred exactly once at completion.
     */
    void tickReload(float dt) {
        if (game.reloadTimer <= 0) {
            return;
        }
        ItemStack current = game.player == null ? null : game.player.selected();
        WeaponDefinition currentDef = WeaponRegistry.of(current == null ? null : current.type);
        if (game.player.hotbarSel != reloadSlot || current != reloadStack || currentDef == null
                || !currentDef.id.equals(reloadWeaponId)) {
            cancelReload();
            return;
        }
        game.reloadTimer -= Math.max(0, dt);
        if (game.reloadTimer > 0) {
            return;
        }
        ItemStack completedStack = reloadStack;
        WeaponDefinition completedWeapon = currentDef;
        // Clear first so a second tick can never transfer ammunition again.
        cancelReload();
        finishReload(completedStack, completedWeapon);
    }

    void cancelReload() {
        game.reloadTimer = 0;
        game.reloadTotal = 0;
        reloadSlot = -1;
        reloadStack = null;
        reloadWeaponId = null;
    }

    // ------------------------------------------------------------------
    // Thrown explosives
    // ------------------------------------------------------------------

    /**
     * Advances and, on a press edge, fires the selected thrown weapon. This is
     * the production command used by the native-input path, so save/load and
     * integration coverage can exercise an actual inventory-consuming throw
     * without synthesizing GLFW state.
     *
     * @return true only when a bomb was lit and thrown
     */
    boolean updateThrownWeaponCommand(float dt, boolean attackPressed, Vector3f dir) {
        advanceRangedCooldown(dt);
        if (!attackPressed || rangedCooldown > 0 || dir == null || game.player == null) {
            return false;
        }
        ItemStack held = game.player.selected();
        WeaponDefinition weapon = WeaponRegistry.of(held == null ? null : held.type);
        if (weapon == null || weapon.category != WeaponDefinition.Category.THROWN) {
            return false;
        }
        throwBomb(held, weapon, dir);
        return true;
    }

    private void throwBomb(ItemStack held, WeaponDefinition weapon, Vector3f dir) {
        Vector3f o = game.camera.position;
        game.projectiles.fire(game, game.player, true, o.x, o.y, o.z,
                dir.x, dir.y + 0.18f, dir.z, weapon, null);
        game.player.inventory.shrink(game.player.hotbarSel, 1);
        game.audio.playFuse(o.x, o.y, o.z);
        game.audio.playSwing();
        rangedCooldown = weapon.attackInterval;
        game.swingTimer = Math.max(game.swingTimer, SWING_THROW);
        game.log("Fuse lit — get clear!");
    }

    // ------------------------------------------------------------------
    // Tools and kill consequences
    // ------------------------------------------------------------------

    /** Wears the held item; breaks it when durability runs out. */
    void consumeDurability(ItemStack held, float amount) {
        if (held == null || !held.type.hasDurability()) {
            return;
        }
        held.durability -= amount;
        if (held.durability <= 0) {
            game.log("Your " + held.type.displayName + " broke!");
            game.audio.playToolBreak();
            game.player.inventory.set(game.player.hotbarSel, null);
        }
    }

    boolean playerHasKnife() {
        return game.player.inventory.count(ItemType.BONE_KNIFE) > 0
                || game.player.inventory.count(ItemType.IRON_KNIFE) > 0;
    }

    /** Wears down the first knife in the inventory; used when skinning. */
    void useKnife() {
        for (int i = 0; i < game.player.inventory.size(); i++) {
            ItemStack s = game.player.inventory.get(i);
            if (s != null && (s.type == ItemType.BONE_KNIFE || s.type == ItemType.IRON_KNIFE)) {
                s.durability -= KNIFE_DURABILITY_COST;
                if (s.durability <= 0) {
                    game.log("Your " + s.type.displayName + " broke!");
                    game.audio.playToolBreak();
                    game.player.inventory.set(i, null);
                }
                return;
            }
        }
    }

    /** Called by EntityManager when the player's hit killed a creature. */
    void onCreatureKilled(Creature c) {
        if (!c.type.predator) {
            return;
        }
        // A regional community gets first claim on a threat killed beside its
        // walls; the starter camp is credited only when no settlement is the
        // beneficiary.
        if (game.settlementManager.onNearbyThreatCleared(game, c.pos.x, c.pos.z)) {
            return;
        }
        if (game.world.campPos != null
                && c.distSqTo(game.world.campPos.x(), game.world.campPos.y(),
                        game.world.campPos.z()) < CAMP_PREDATOR_RADIUS * CAMP_PREDATOR_RADIUS) {
            game.faction.addTrust(game, CAMP_PREDATOR_TRUST,
                    "The camp saw you slay a predator");
            game.faction.onPredatorKilledNearCamp(game);
        }
    }

    void onRaiderKilled() {
        game.faction.addTrust(game, RAIDER_KILL_TRUST, "You drove off a scavenger");
    }
}

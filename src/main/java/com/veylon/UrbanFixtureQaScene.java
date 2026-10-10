package com.veylon;

import com.veylon.entity.PlayerMovementSystem;
import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.World;

import java.util.Locale;

/**
 * Urban package 04 QA ({@code VEYLON_SCENE=urban_fixtures} and
 * {@code urban_fixtures_night}): {@link FixtureShowcase} built beside the spawn
 * of a street-network world, shown from both sides of its doors and windows,
 * from the foot and the head of its stairs, before and after the production
 * interactions open, shatter and close them; then a real walk in through the
 * front door and up the stairs, with a pass or fail line. The night variant
 * pins 23:00 with lanterns lit inside, for readability.
 *
 * <p>Day timeline (seconds): facade 0-4; at 4 the front door and a window are
 * opened and the other window shattered; facade again 4-7; at 7 the front door
 * is closed and seen from inside 7-10; windows from inside 10-13; stair foot
 * 13-16; stairwell from the roof 16-19; at 19 the door reopens and the walk
 * runs, reporting at 32. Night timeline: facade 0-4, windows from inside 4-7,
 * stairs 7-10, the door from inside 10-14.
 */
final class UrbanFixtureQaScene {

    private static final float NIGHT_MINUTES = 23 * 60f;
    private static final double WALK_START = 19.0, WALK_REPORT = 32.0;
    private static final float WALK_TOLERANCE = 0.3f;

    private final Game game;
    private final PlayerMovementSystem.Command walk = new PlayerMovementSystem.Command();
    private FixtureShowcase.Layout house;
    private boolean night;
    private int step;
    private double lastSample = -1;

    // Camera hold.
    private boolean holding;
    private float hx, hy, hz, hyaw, hpitch;

    // The walk.
    private Vec3i[] route;
    private int waypoint;
    private int frames, blockedFrames;
    private boolean reported, passed;

    UrbanFixtureQaScene(Game game) {
        this.game = game;
    }

    void stage(String scene) {
        World world = game.world;
        night = scene.endsWith("_night");
        int[] spawn = world.urban.spawnColumn();
        int x0 = spawn[0] + 6, z0 = spawn[1] + 10;
        world.ensureChunks(x0 + FixtureShowcase.WIDTH / 2, z0, 6, 10_000);
        house = FixtureShowcase.build(world, x0, z0, false);
        world.crateContents.computeIfAbsent(house.cabinet(), k -> new Inventory(12)).add(ItemType.BANDAGE, 2);
        lightLantern(house.lantern());
        if (night) {
            Vec3i westLantern = new Vec3i(x0 + 1, house.westRoom().y(), z0 + 1);
            world.setBlock(westLantern.x(), westLantern.y(), westLantern.z(),
                    com.veylon.world.BlockType.LANTERN, false);
            lightLantern(westLantern);
            game.time.totalMinutes = NIGHT_MINUTES;
        }
        System.out.println("[urban-qa] fixtures house at " + x0 + "," + z0 + " frontDoor=" + house.frontDoor()
                + " innerDoor=" + house.innerDoor() + " windows=" + house.window() + "," + house.secondWindow()
                + " stairs=" + house.stairFoot() + "->" + house.stairTop() + (night ? " night" : " day"));
        facade();
    }

    private void lightLantern(Vec3i pos) {
        game.world.addLanternFuel(pos, World.LANTERN_MAX_FUEL);
        game.world.setLanternLit(pos, true);
    }

    /** Per-frame hook from {@link UrbanQaScenes#update}; inert until staged. */
    void update(double elapsed) {
        if (house == null) {
            return;
        }
        if (night) {
            game.time.totalMinutes = NIGHT_MINUTES;
            nightTimeline(elapsed);
        } else {
            dayTimeline(elapsed);
        }
        if (holding) {
            game.player.pos.set(hx, hy, hz);
            game.player.vel.zero();
            game.camera.yaw = hyaw;
            game.camera.pitch = hpitch;
        }
    }

    private void dayTimeline(double t) {
        if (step == 0 && t >= 4) {
            step = 1;
            act("open front door", house.frontDoor());
            act("open window", house.window());
            shatter(house.secondWindow());
            shatter(new Vec3i(house.secondWindow().x(), house.secondWindow().y() + 1, house.secondWindow().z()));
        } else if (step == 1 && t >= 7) {
            step = 2;
            act("close front door", house.frontDoor());
            hold(house.westRoom().x() + 0.5f, house.westRoom().z() + 2.5f, 0f, 8f); // the door from inside
            prompt("inside, facing the front door");
        } else if (step == 2 && t >= 10) {
            step = 3;
            hold(house.eastRoom().x() + 0.5f, house.eastRoom().z() + 2.5f, 0f, 6f); // the windows from inside
        } else if (step == 3 && t >= 13) {
            step = 4;
            hold(house.stairFoot().x() - 0.5f, house.stairFoot().z() + 0.5f, 90f, -18f); // up the flight
        } else if (step == 4 && t >= 16) {
            step = 5;
            Vec3i top = house.stairTop();
            holdAt(top.x() - 0.5f, top.y() + 0.6f, house.stairFoot().z() + 0.5f, 270f, 38f); // down the stairwell
        } else if (step == 5 && t >= WALK_START) {
            step = 6;
            act("reopen front door", house.frontDoor());
            holding = false;
            Vec3i start = house.outsideFront();
            game.player.pos.set(start.x() + 0.5f, start.y(), start.z() - 1.5f);
            game.player.vel.zero();
            route = new Vec3i[]{house.outsideFront(), house.westRoom(),
                    new Vec3i(house.stairFoot().x(), house.stairFoot().y(), house.stairFoot().z()),
                    new Vec3i(house.x0() + 7, house.stairTop().y(), house.stairFoot().z()), house.stairTop()};
            waypoint = 0;
            lastSample = t;
        } else if (step == 6) {
            walkStep(t);
        }
    }

    private void nightTimeline(double t) {
        if (step == 0 && t >= 4) {
            step = 1;
            hold(house.eastRoom().x() + 0.5f, house.eastRoom().z() + 2.5f, 0f, 6f);
        } else if (step == 1 && t >= 7) {
            step = 2;
            hold(house.stairFoot().x() - 0.5f, house.stairFoot().z() + 0.5f, 90f, -18f);
        } else if (step == 2 && t >= 10) {
            step = 3;
            hold(house.westRoom().x() + 0.5f, house.westRoom().z() + 2.5f, 0f, 8f);
            prompt("night, inside, facing the front door");
        }
    }

    private void facade() {
        // Six blocks out on the street, between the door and the windows.
        hold(house.x0() + 7f, house.z0() - 6.5f, 180f, 4f);
    }

    private void hold(float x, float z, float yaw, float pitch) {
        holdAt(x, house.westRoom().y(), z, yaw, pitch);
    }

    private void holdAt(float x, float y, float z, float yaw, float pitch) {
        hx = x;
        hy = y;
        hz = z;
        hyaw = yaw;
        hpitch = pitch;
        holding = true;
    }

    private void act(String what, Vec3i pos) {
        boolean done = game.interactWithBlockAt(pos);
        System.out.println("[urban-qa] fixtures " + what + " -> " + done + " now "
                + game.world.getBlock(pos.x(), pos.y(), pos.z()));
    }

    private void shatter(Vec3i pos) {
        game.player.inventory.set(game.player.hotbarSel, new com.veylon.item.ItemStack(ItemType.STONE_AXE, 1));
        boolean done = game.completePlayerBlockBreak(pos);
        System.out.println("[urban-qa] fixtures shatter " + pos + " -> " + done + " now "
                + game.world.getBlock(pos.x(), pos.y(), pos.z()));
    }

    /** Prints the HUD line the player would see aiming at the front door from here. */
    private void prompt(String where) {
        Vec3i d = house.frontDoor();
        game.targetHit = new com.veylon.world.Raycaster.Hit(d.x(), d.y() + 1, d.z(), 0, 0, 1, 2,
                game.world.getBlock(d.x(), d.y() + 1, d.z()));
        System.out.println("[urban-qa] fixtures prompt (" + where + "): "
                + FixtureActions.prompt(game, d.x(), d.y() + 1, d.z()));
    }

    private void walkStep(double t) {
        if (reported) {
            return;
        }
        if (t >= WALK_REPORT || waypoint >= route.length) {
            reported = true;
            report();
            return;
        }
        float dt = (float) Math.min(0.1, t - lastSample);
        lastSample = t;
        Vec3i target = route[waypoint];
        float dx = target.x() + 0.5f - game.player.pos.x, dz = target.z() + 0.5f - game.player.pos.z;
        if (dx * dx + dz * dz < WALK_TOLERANCE * WALK_TOLERANCE) {
            waypoint++;
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        game.camera.yaw = yaw;
        game.camera.pitch = 6f;
        walk.set(yaw, true, false, false, false, false, false, false, false);
        game.applyMovementCommand(walk, dt);
        frames++;
        blockedFrames += game.player.horizontalCollision ? 1 : 0;
    }

    /** True once the walk has reported. */
    boolean reported() {
        return reported;
    }

    /** True when the walk reached the roof through the door and up the stairs. */
    boolean passed() {
        return passed;
    }

    private void report() {
        Vec3i top = house.stairTop();
        boolean onRoof = Math.abs(game.player.pos.y - top.y()) < 0.1f;
        boolean pass = waypoint >= route.length && onRoof;
        passed = pass;
        System.out.printf(Locale.ROOT, "[urban-qa] fixtures-walk frames=%d waypoints=%d/%d blockedFrames=%d"
                        + " end=%.2f,%.2f,%.2f onRoof=%s pass=%s%n",
                frames, waypoint, route.length, blockedFrames, game.player.pos.x, game.player.pos.y,
                game.player.pos.z, onRoof, pass);
    }
}

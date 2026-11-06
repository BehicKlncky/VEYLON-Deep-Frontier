package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingPlan;

/** Actual generated domestic cupboard, with the production interaction target. */
public record UrbanLootFixture(Game game, BuildingPlan building, BuildingPlan.Fixture fixture) {
    public static UrbanLootFixture create(BuildingPlan.Variant variant) {
        Game game=new Game();
        game.newWorld(20260731,true,World.GEN_URBAN_SHAPED_STAIRS,GameMode.SURVIVAL);
        game.appState=Game.AppState.PLAYING;
        for (var parcel : game.world.urban.regionPlan(0,0).parcels()) {
            BuildingPlan building=game.world.urban.buildingAt(parcel.x0(),parcel.z0());
            if (building.variant()!=variant) { continue; }
            for (var fixture : building.fixtures()) {
                if (fixture.kind()==BuildingPlan.FixtureKind.CONTAINER
                        && BuildingFurnishing.containerUse(building,fixture)==BuildingFurnishing.ContainerUse.KITCHEN_CABINET) {
                    Vec3i p=fixture.position(), target=fixture.target();
                    game.world.getOrCreateChunk(Math.floorDiv(p.x(),16),Math.floorDiv(p.z(),16));
                    game.world.getOrCreateChunk(Math.floorDiv(target.x(),16),Math.floorDiv(target.z(),16));
                    game.player.pos.set(target.x()+.5f,target.y(),target.z()+.5f);
                    game.player.vel.zero(); game.player.onGround=true;
                    return new UrbanLootFixture(game,building,fixture);
                }
            }
        }
        throw new AssertionError("fixture seed has no requested generated kitchen");
    }
    public Vec3i position() { return fixture.position(); }
}

package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.settlement.HumanFaction;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanIds;

/** Native search/transfer QA on real generated cupboards; never stages items or advances search itself. */
final class UrbanLootQaScene {
    private final Game game;
    private BuildingPlan.Fixture kitchen;
    private BuildingPlan.Fixture owned;
    private int phase;
    private double initialClock;
    private float initialHunger;
    private float initialReputation;
    private boolean kitchenNoise;
    private boolean ownedNoise;
    private boolean passed;

    UrbanLootQaScene(Game game) { this.game=game; }

    void stage() {
        if (!UrbanContainers.manages(game.world) || game.gameMode()!=GameMode.SURVIVAL) {
            System.out.println("[urban-qa] loot requires generated urban Survival; inactive"); return;
        }
        kitchen=select(BuildingPlan.Variant.INTACT);
        owned=select(BuildingPlan.Variant.OCCUPIED);
        phase=1; passed=false;
        initialClock=game.time.totalMinutes; initialHunger=game.player.hunger;
        initialReputation=game.settlementManager.reputation(game,HumanFaction.FREE_SETTLERS);
        open(kitchen);
        System.out.println("[urban-qa] loot stage=generated-kitchen id="+UrbanIds.format(kitchen.id())
                +" owned="+UrbanIds.format(owned.id())+" evidence=scripted-production-search-and-transfer; no staged loot");
    }

    private BuildingPlan.Fixture select(BuildingPlan.Variant variant) {
        for (var parcel : game.world.urban.regionPlan(0,0).parcels()) {
            var building=game.world.urban.buildingAt(parcel.x0(),parcel.z0());
            if (building.variant()!=variant) { continue; }
            for (var fixture : building.fixtures()) {
                if (fixture.kind()==BuildingPlan.FixtureKind.CONTAINER
                        && BuildingFurnishing.containerUse(building,fixture)==BuildingFurnishing.ContainerUse.KITCHEN_CABINET) {
                    return fixture;
                }
            }
        }
        throw new IllegalStateException("loot QA seed has no "+variant+" kitchen cupboard");
    }

    private void open(BuildingPlan.Fixture fixture) {
        var target=fixture.target(); var position=fixture.position();
        game.closeScreens();
        game.world.ensureChunks(target.x(),target.z(),2,1000);
        game.player.pos.set(target.x()+.5f,target.y(),target.z()+.5f);
        game.player.vel.zero(); game.player.onGround=true;
        game.camera.yaw=(float) Math.toDegrees(Math.atan2(position.x()-target.x(),target.z()-position.z()));
        game.camera.pitch=12;
        if (!game.interactWithBlockAt(position) || !game.crateSearching()) {
            throw new IllegalStateException("loot QA did not start a real first search");
        }
    }

    void update(double elapsed) {
        if (phase==0 || passed) { return; }
        if (phase==1 && elapsed>=4 && !game.crateSearching()) {
            kitchenNoise=game.noise.countCategory("rummage")>0;
            if (game.transferCrateItemToPlayer(0,1)!=1
                    || game.settlementManager.reputation(game,HumanFaction.FREE_SETTLERS)!=initialReputation) {
                throw new IllegalStateException("abandoned kitchen transfer failed or counted as theft");
            }
            System.out.println("[urban-qa] loot kitchen searched=true taken=1 noise="+kitchenNoise
                    +" simulationMinutes="+(game.time.totalMinutes-initialClock));
            phase=2;
        }
        if (phase==2 && elapsed>=5) { open(owned); phase=3; }
        if (phase==3 && elapsed>=9 && !game.crateSearching()) {
            ownedNoise=game.noise.countCategory("rummage")>0;
            if (game.transferCrateItemToPlayer(0,1)!=1) { throw new IllegalStateException("owned cupboard transfer failed"); }
            System.out.println("[urban-qa] loot owned searched=true taken=1 owner="
                    +UrbanIds.format(game.world.urban.containers.at(owned.position()).ownerId)
                    +" reputation="+game.settlementManager.reputation(game,HumanFaction.FREE_SETTLERS));
            phase=4;
        }
        if (phase==4 && elapsed>=10) {
            passed=kitchenNoise && ownedNoise && game.simulates() && game.time.totalMinutes>initialClock
                    && game.player.hunger<initialHunger
                    && game.settlementManager.reputation(game,HumanFaction.FREE_SETTLERS)<initialReputation;
            System.out.println("[urban-qa] loot pass="+passed+" simulationMinutes="+(game.time.totalMinutes-initialClock)
                    +" hungerUsed="+(initialHunger-game.player.hunger)+" inventories="+game.world.crateContents.size());
            if (!passed) { throw new IllegalStateException("native loot simulation/ownership gate failed"); }
        }
        if (elapsed>=13 && !passed) { throw new IllegalStateException("native loot searches did not complete on simulation ticks"); }
    }

    boolean passed() { return passed; }
}

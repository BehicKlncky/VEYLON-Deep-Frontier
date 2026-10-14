package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.HumanFaction;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanContainers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UrbanLootGameplayTest {
    @TempDir Path directory;

    @Test
    void searchUsesNormalSimulationAndResumesItsSavedProgress() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        assertTrue(g.interactWithBlockAt(f.position()));
        assertTrue(g.crateSearching()); assertNull(g.openCrate);
        assertTrue(g.simulates()); assertEquals(0,g.transferCrateItemToPlayer(0));
        double clock=g.time.totalMinutes; float hunger=g.player.hunger;
        simulate(g,.8f);
        assertTrue(g.crateSearchProgress()>0 && g.crateSearchProgress()<1);
        assertTrue(g.time.totalMinutes>clock); assertTrue(g.player.hunger<hunger);
        assertTrue(g.noise.countCategory("rummage")>0);
        float progress=g.world.urban.containers.at(f.position()).progress;
        g.closeScreens(); simulate(g,1);
        assertEquals(progress,g.world.urban.containers.at(f.position()).progress);
        Path save=directory.resolve("interrupted.sav"); assertTrue(SaveSystem.save(g,save));
        assertTrue(SaveSystem.load(g,save)); assertTrue(g.openCrateAt(f.position()));
        assertEquals(progress,g.world.urban.containers.at(f.position()).progress);
        simulate(g,2); assertFalse(g.crateSearching()); assertNotNull(g.openCrate);
        assertTrue(g.transferCrateItemToPlayer(0)>0);
    }

    @Test
    void partialFullAndEmptyTransfersNeverRefillAfterSaveBreakAndProductionReplacement() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game(); Vec3i p=f.position();
        assertTrue(g.openCrateAt(p)); simulate(g,3);
        g.openCrate.clear(); var source=new ItemStack(ItemType.DRIED_BERRY,5); source.freshness=100;
        g.openCrate.set(0,source);
        fill(g,ItemType.STONE);
        assertEquals(0,g.transferCrateItemToPlayer(0)); assertEquals(5,g.openCrate.get(0).count);
        var nearFull=new ItemStack(ItemType.DRIED_BERRY,ItemType.DRIED_BERRY.maxStack-2); nearFull.freshness=200;
        g.player.inventory.set(0,nearFull);
        assertEquals(2,g.transferCrateItemToPlayer(0)); assertEquals(3,g.openCrate.get(0).count);
        assertEquals(100,g.openCrate.get(0).freshness);
        assertEquals((200f*(ItemType.DRIED_BERRY.maxStack-2)+200)/ItemType.DRIED_BERRY.maxStack,
                g.player.inventory.get(0).freshness,.001f);
        Path save=directory.resolve("partial.sav"); assertTrue(SaveSystem.save(g,save));
        assertTrue(SaveSystem.load(g,save)); assertTrue(g.openCrateAt(p));
        assertFalse(g.crateSearching()); assertEquals(3,g.openCrate.get(0).count);
        g.player.inventory.clear(); assertEquals(3,g.transferCrateItemToPlayer(0));
        assertNull(g.openCrate); assertEquals(UrbanContainers.State.EMPTY,g.world.urban.containers.at(p).state);
        assertTrue(SaveSystem.save(g,save)); assertTrue(SaveSystem.load(g,save));
        assertTrue(g.openCrateAt(p)); assertFalse(g.crateSearching()); assertNull(g.openCrate);
        for (int cycle=0;cycle<2;cycle++) {
            g.closeScreens(); assertTrue(g.completePlayerBlockBreak(p));
            assertEquals(3,g.player.inventory.count(ItemType.DRIED_BERRY));
            int furniture=slot(g,f.fixture().block().drop);
            assertTrue(furniture>=0); g.player.hotbarSel=furniture;
            assertTrue(g.placeSelectedBlockAt(p.x(),p.y(),p.z()));
            assertTrue(g.openCrateAt(p)); assertFalse(g.crateSearching()); assertNull(g.openCrate);
            assertEquals(UrbanContainers.Owner.PLAYER,g.world.urban.containers.at(p).owner);
            assertTrue(g.world.urban.containers.at(p).id<0);
        }
        assertEquals(UrbanContainers.State.MOVED,g.world.urban.containers.byId(f.fixture().id()).state);
        assertTrue(SaveSystem.save(g,save)); assertTrue(SaveSystem.load(g,save));
        assertTrue(g.openCrateAt(p)); assertNull(g.openCrate);
        assertEquals(3,g.player.inventory.count(ItemType.DRIED_BERRY));
    }

    @Test
    void abandonedPropertyIgnoresCampProximityButSurvivorTheftHasOneSavedConsequence() {
        var abandoned=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game a=abandoned.game();
        a.world.campPos=abandoned.position(); float trust=a.faction.trust;
        assertTrue(a.openCrateAt(abandoned.position())); simulate(a,3);
        assertTrue(a.transferCrateItemToPlayer(0)>0);
        assertEquals(trust,a.faction.trust);
        assertEquals(0,a.settlementManager.reputation(a,HumanFaction.FREE_SETTLERS));
        var owned=UrbanLootFixture.create(BuildingPlan.Variant.OCCUPIED); Game g=owned.game();
        assertTrue(g.openCrateAt(owned.position())); simulate(g,3);
        var entry=g.world.urban.containers.at(owned.position());
        assertEquals(owned.building().id(),entry.ownerId);
        ItemType type=g.openCrate.get(0).type; int taken=g.transferCrateItemToPlayer(0); assertTrue(taken>0);
        float reputation=g.settlementManager.reputation(g,HumanFaction.FREE_SETTLERS); assertTrue(reputation<0);
        g.onCrateItemTaken(type,taken,1,1);
        assertEquals(reputation,g.settlementManager.reputation(g,HumanFaction.FREE_SETTLERS),"repeated callback is idempotent");
        Path save=directory.resolve("ownership.sav"); assertTrue(SaveSystem.save(g,save)); assertTrue(SaveSystem.load(g,save));
        assertEquals(reputation,g.settlementManager.reputation(g,HumanFaction.FREE_SETTLERS));
        assertEquals(entry.ownerId,g.world.urban.containers.at(owned.position()).ownerId);
    }

    @Test
    void damageAndDistanceInterruptSearchWhileCreativeDamageGatesRemainEffective() {
        for (GameMode mode : GameMode.values()) {
            var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game(); g.switchGameMode(mode);
            assertTrue(g.openCrateAt(f.position())); simulate(g,.5f);
            float progress=g.world.urban.containers.at(f.position()).progress;
            g.player.hurtPhysical(g,5,true); simulate(g,.1f);
            if (mode==GameMode.SURVIVAL) {
                assertEquals(Game.UiMode.NONE,g.uiMode);
                assertEquals(progress,g.world.urban.containers.at(f.position()).progress);
            } else { assertTrue(g.crateSearching(),"invulnerable Creative is not falsely interrupted"); }
            g.openCrateAt(f.position()); g.player.pos.x+=10; simulate(g,.1f);
            assertEquals(Game.UiMode.NONE,g.uiMode);
        }
    }

    @Test
    void overTimeDrainsDoNotInterruptASearchThatABlowWould() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        // A cold snap, an open wound and an empty stomach all drain health without a blow.
        g.player.bodyTemp=30; g.player.hunger=0;
        g.player.addAffliction(com.veylon.entity.Affliction.BLEEDING,120);
        float health=g.player.health; int harm=g.player.harmEvents;
        assertTrue(g.openCrateAt(f.position()));
        for (int i=0;i<200 && g.crateSearching();i++) { simulate(g,.05f); }
        assertTrue(g.player.health<health,"the drains really ran during the search");
        assertEquals(harm,g.player.harmEvents);
        assertEquals(Game.UiMode.CRATE,g.uiMode,"the search was not interrupted");
        assertTrue(g.world.urban.containers.at(f.position()).state.revealed());
    }

    @Test
    void anUnsearchedHandBreakRollsOnceAndRefusesLossWhenFull() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game(); Vec3i p=f.position();
        fill(g,ItemType.STONE); assertFalse(g.completePlayerBlockBreak(p));
        var entry=g.world.urban.containers.at(p); assertNotNull(entry);
        int contents=g.world.crateContents.get(p).count(ItemType.DRIED_BERRY);
        assertTrue(contents>0); assertSame(f.fixture().block(),g.world.getBlock(p.x(),p.y(),p.z()));
        g.player.inventory.clear(); assertTrue(g.completePlayerBlockBreak(p));
        assertEquals(contents,g.player.inventory.count(ItemType.DRIED_BERRY));
        assertEquals(UrbanContainers.State.MOVED,entry.state); assertFalse(g.world.crateContents.containsKey(p));
    }

    @Test
    void singleItemTransfersPreserveTheRemainderAndDepositsCannotOverfill() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        g.openCrateAt(f.position()); simulate(g,3);
        g.openCrate.clear(); var food=new ItemStack(ItemType.DRIED_BERRY,3); food.freshness=100;
        g.openCrate.set(0,food); g.player.inventory.clear();
        assertEquals(1,g.transferCrateItemToPlayer(0,1)); assertEquals(2,g.openCrate.get(0).count);
        assertEquals(100,g.player.inventory.get(0).freshness); assertEquals(100,g.openCrate.get(0).freshness);
        assertEquals(1,g.transferPlayerItemToCrate(0,1)); assertNull(g.player.inventory.get(0));
        assertEquals(3,g.openCrate.get(0).count);
        for (int i=0;i<g.openCrate.size();i++) { g.openCrate.set(i,new ItemStack(ItemType.STONE,ItemType.STONE.maxStack)); }
        var gun=new ItemStack(ItemType.RELIC_RIFLE,1); gun.durability=22; gun.charge=3; g.player.inventory.set(0,gun);
        assertEquals(0,g.transferPlayerItemToCrate(0,1)); assertSame(gun,g.player.inventory.get(0));
        assertEquals(22,gun.durability); assertEquals(3,gun.charge);
    }

    @Test
    void fireAndBlastDestroyTheOnlyInventoryAndPersistTombstonesInBothModes() {
        for (GameMode mode : GameMode.values()) {
            for (boolean fire : new boolean[]{false,true}) {
                var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game(); g.switchGameMode(mode);
                Vec3i p=f.position(); assertTrue(g.openCrateAt(p)); simulate(g,3);
                assertNotNull(g.openCrate);
                if (fire) { assertTrue(g.fire.ignite(g,p.x(),p.y(),p.z())); g.fire.mediumTick(g,120); }
                else { g.explosions.explode(g,p.x()+.5f,p.y()+.5f,p.z()+.5f,4,0,0,false); }
                assertFalse(g.world.crateContents.containsKey(p));
                assertEquals(0,g.transferCrateItemToPlayer(0)); assertEquals(Game.UiMode.NONE,g.uiMode);
                assertEquals(UrbanContainers.State.DESTROYED,g.world.urban.containers.byId(f.fixture().id()).state);
                Path save=directory.resolve(mode+"-"+fire+".sav"); assertTrue(SaveSystem.save(g,save));
                assertTrue(SaveSystem.load(g,save));
                assertEquals(UrbanContainers.State.DESTROYED,g.world.urban.containers.byId(f.fixture().id()).state);
                assertFalse(g.world.crateContents.containsKey(p));
            }
        }
    }

    @Test
    void playerCausedDestructionUsesTheRecordedOwnerAndNaturalFireIsNotTheft() {
        for (int action=0;action<3;action++) {
            var f=UrbanLootFixture.create(BuildingPlan.Variant.OCCUPIED); Game g=f.game(); Vec3i p=f.position();
            if (action==0) { assertTrue(g.completePlayerBlockBreak(p)); }
            if (action==1) { g.explosions.explode(g,p.x()+.5f,p.y()+.5f,p.z()+.5f,2,0,0,true); }
            if (action==2) { assertTrue(g.fire.ignite(g,p.x(),p.y(),p.z(),true,1)); g.fire.mediumTick(g,120); }
            assertTrue(g.settlementManager.reputation(g,HumanFaction.FREE_SETTLERS)<0,"action "+action);
        }
        var f=UrbanLootFixture.create(BuildingPlan.Variant.OCCUPIED); Game g=f.game(); Vec3i p=f.position();
        assertTrue(g.fire.ignite(g,p.x(),p.y(),p.z())); g.fire.mediumTick(g,120);
        assertEquals(0,g.settlementManager.reputation(g,HumanFaction.FREE_SETTLERS));
    }

    @Test
    void conditionChargeDepositsAndSpoilageKeepMetadataAndDoNotRecreateAnEmptyInventory() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        assertTrue(g.openCrateAt(f.position())); simulate(g,3); g.openCrate.clear();
        var gun=new ItemStack(ItemType.RELIC_RIFLE,1); gun.durability=37; gun.charge=4;
        g.player.inventory.set(0,gun); assertEquals(1,g.transferPlayerItemToCrate(0));
        int stored=slot(g.openCrate,ItemType.RELIC_RIFLE); assertTrue(stored>=0);
        assertEquals(1,g.transferCrateItemToPlayer(stored));
        var carried=g.player.inventory.get(slot(g,ItemType.RELIC_RIFLE));
        assertEquals(37,carried.durability); assertEquals(4,carried.charge);
        assertNull(g.openCrate);
        var food=new ItemStack(ItemType.DRIED_BERRY,1); food.freshness=.1f; g.player.inventory.set(0,food);
        assertEquals(1,g.transferPlayerItemToCrate(0));
        g.itemConditions.slowTick(g,10); simulate(g,.1f);
        assertNull(g.openCrate); assertFalse(g.world.crateContents.containsKey(f.position()));
        assertEquals(UrbanContainers.State.EMPTY,g.world.urban.containers.at(f.position()).state);
        Path save=directory.resolve("spoiled.sav"); assertTrue(SaveSystem.save(g,save)); assertTrue(SaveSystem.load(g,save));
        assertTrue(g.openCrateAt(f.position())); assertFalse(g.crateSearching()); assertNull(g.openCrate);
    }

    static void simulate(Game game,float seconds) {
        for (int i=0;i<Math.round(seconds/.05f);i++) { assertTrue(game.simulates()); game.advanceWorld(.05); }
    }
    private static void fill(Game g,ItemType type) {
        for (int i=0;i<g.player.inventory.size();i++) { g.player.inventory.set(i,new ItemStack(type,type.maxStack)); }
    }
    private static int slot(Game g,ItemType type) { return slot(g.player.inventory,type); }
    private static int slot(com.veylon.item.Inventory inventory,ItemType type) {
        for (int i=0;i<inventory.size();i++) { if (inventory.get(i)!=null && inventory.get(i).type==type) { return i; } }
        return -1;
    }
}

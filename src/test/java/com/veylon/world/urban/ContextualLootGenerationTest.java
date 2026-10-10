package com.veylon.world.urban;

import com.veylon.UrbanLootFixture;
import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ContextualLootGenerationTest {
    @TempDir Path directory;

    @Test
    void generatedNecessitiesHaveSeveralReachableRoomSourcesAndWeaponsStaySecured() {
        for (long seed : new long[]{20260731,7,-4242}) {
            World world=new World(seed,World.GEN_URBAN_SHAPED_STAIRS);
            Map<ItemType,EnumSet<BuildingFurnishing.ContainerUse>> sources=new EnumMap<>(ItemType.class);
            int specialist=0;
            for (var parcel : world.urban.regionPlan(0,0).parcels()) {
                var building=world.urban.buildingAt(parcel.x0(),parcel.z0());
                for (var fixture : building.fixtures()) {
                    if (fixture.kind()!=BuildingPlan.FixtureKind.CONTAINER) { continue; }
                    var use=BuildingFurnishing.containerUse(building,fixture);
                    var table=LootTables.table(building,fixture,world.urban.districtAt(parcel.x0(),parcel.z0()));
                    var inventory=LootTables.roll(seed,2,fixture.id(),table,world.urban.districtAt(parcel.x0(),parcel.z0()),building.variant());
                    if (inventory==null) { continue; }
                    for (int i=0;i<inventory.size();i++) {
                        var stack=inventory.get(i); if (stack==null) { continue; }
                        sources.computeIfAbsent(stack.type,ignored -> EnumSet.noneOf(BuildingFurnishing.ContainerUse.class)).add(use);
                        if (stack.type==ItemType.FLINTLOCK_PISTOL || stack.type==ItemType.MUSKET_BALL || stack.type==ItemType.BLACK_POWDER) {
                            assertEquals(LootTables.Table.SECURED,table); specialist++;
                        }
                    }
                }
            }
            for (ItemType essential : new ItemType[]{ItemType.DRIED_BERRY,ItemType.WATERSKIN_CLEAN,
                    ItemType.BANDAGE,ItemType.TORCH,ItemType.IRON_PICKAXE}) {
                assertTrue(sources.get(essential).size()>=2,"seed="+seed+" essential="+essential+" sources="+sources.get(essential));
            }
            assertTrue(sources.containsKey(ItemType.RAIN_CLOAK)); assertTrue(specialist>0);
            assertEquals(0,world.urban.containers.size()); assertTrue(world.crateContents.isEmpty(),"pure planning never retains inventories");
        }
    }

    @Test
    void boundarySpanningBuildingLootAndIdsDoNotDependOnChunkOrSearchOrder() {
        World forward=new World(20260731,World.GEN_URBAN_SHAPED_STAIRS);
        World reverse=new World(20260731,World.GEN_URBAN_SHAPED_STAIRS);
        forward.urban.resolveContentRevision(2); reverse.urban.resolveContentRevision(2);
        var parcel=forward.urban.regionPlan(0,0).parcels().stream().filter(p -> Math.floorDiv(p.x0(),16)!=Math.floorDiv(p.x1(),16)
                && forward.urban.buildingAt(p.x0(),p.z0()).variant()==BuildingPlan.Variant.INTACT).findFirst().orElseThrow();
        var building=forward.urban.buildingAt(parcel.x0(),parcel.z0());
        var fixtures=new ArrayList<>(building.fixtures().stream().filter(f -> f.kind()==BuildingPlan.FixtureKind.CONTAINER).toList());
        for (int x=Math.floorDiv(parcel.x0(),16);x<=Math.floorDiv(parcel.x1(),16);x++) {
            for (int z=Math.floorDiv(parcel.z0(),16);z<=Math.floorDiv(parcel.z1(),16);z++) { forward.getOrCreateChunk(x,z); }
        }
        for (int x=Math.floorDiv(parcel.x1(),16);x>=Math.floorDiv(parcel.x0(),16);x--) {
            for (int z=Math.floorDiv(parcel.z1(),16);z>=Math.floorDiv(parcel.z0(),16);z--) { reverse.getOrCreateChunk(x,z); }
        }
        for (var fixture : fixtures) { assertEquals(fixture.id(),forward.urban.containers.touch(forward,fixture.position()).id); }
        Collections.reverse(fixtures);
        for (var fixture : fixtures) {
            var entry=reverse.urban.containers.touch(reverse,fixture.position()); assertEquals(fixture.id(),entry.id);
            Inventory a=forward.crateContents.get(fixture.position()), b=reverse.crateContents.get(fixture.position());
            if (a==null) { assertNull(b); continue; }
            assertNotNull(b);
            for (int slot=0;slot<12;slot++) {
                var one=a.get(slot); var two=b.get(slot);
                if (one==null) { assertNull(two); continue; }
                assertEquals(one.type,two.type); assertEquals(one.count,two.count);
                assertEquals(one.durability,two.durability); assertEquals(one.freshness,two.freshness); assertEquals(one.charge,two.charge);
            }
            assertSame(entry,reverse.urban.containers.touch(reverse,fixture.position()));
        }
        assertEquals(fixtures.size(),forward.urban.containers.size());
    }

    @Test
    void aPickedCleanKitchenRecordsItsEmptySearchWithoutAllocatingAnInventory() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.PICKED_CLEAN); var game=f.game();
        assertTrue(game.openCrateAt(f.position()));
        for (int i=0;i<60;i++) { game.fastTick(.05f); }
        assertFalse(game.crateSearching()); assertNull(game.openCrate); assertTrue(game.world.crateContents.isEmpty());
        var id=game.world.urban.containers.at(f.position()).id;
        assertEquals(UrbanContainers.State.EMPTY,game.world.urban.containers.byId(id).state);
        Path save=directory.resolve("picked-clean.sav"); assertTrue(SaveSystem.save(game,save));
        assertTrue(SaveSystem.load(game,save)); assertTrue(game.openCrateAt(f.position()));
        assertFalse(game.crateSearching()); assertNull(game.openCrate);
        assertEquals(id,game.world.urban.containers.at(f.position()).id);
    }
}

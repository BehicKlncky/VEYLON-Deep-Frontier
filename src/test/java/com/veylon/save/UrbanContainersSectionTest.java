package com.veylon.save;

import com.veylon.Game;
import com.veylon.UrbanLootFixture;
import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.LootTables;
import com.veylon.world.urban.UrbanContainers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class UrbanContainersSectionTest {
    @TempDir Path directory;

    @Test
    void rolledContentsAndInterruptedProgressRoundTripOverALiveWorldWithOneOwner() throws Exception {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game original=f.game();
        var state=original.world.urban.containers;
        var entry=state.touch(original.world,f.position());
        entry.state=UrbanContainers.State.SEARCHING; entry.progress=.75f;
        Inventory inventory=original.world.crateContents.get(f.position());
        inventory.get(0).freshness=123;
        Path save=directory.resolve("search.sav"); assertTrue(SaveSystem.save(original,save));
        Game loaded=new Game(); loaded.newWorld(7,true,World.GEN_CAVE_IDENTITIES);
        assertTrue(SaveSystem.load(loaded,save));
        assertEquals(.75f,loaded.world.urban.containers.byId(entry.id).progress);
        assertEquals(UrbanContainers.State.SEARCHING,loaded.world.urban.containers.byId(entry.id).state);
        assertEquals(123,loaded.world.crateContents.get(f.position()).get(0).freshness);
        assertEquals(1,loaded.world.crateContents.size());
        assertArrayEquals(UrbanContainersSection.write(original),UrbanContainersSection.write(loaded));
        loaded.newWorld(8,true,World.GEN_URBAN_SHAPED_STAIRS);
        assertEquals(0,loaded.world.urban.containers.size());
        assertTrue(loaded.world.crateContents.isEmpty());
    }

    @Test
    void anUnsearchedDestructionAndReplacementKeepDifferentDurableIdentities() throws Exception {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game(); Vec3i p=f.position();
        g.world.setBlock(p.x(),p.y(),p.z(),BlockType.AIR,true);
        assertEquals(UrbanContainers.State.DESTROYED,g.world.urban.containers.byId(f.fixture().id()).state);
        assertTrue(g.world.crateContents.isEmpty());
        g.world.setBlock(p.x(),p.y(),p.z(),f.fixture().block(),true);
        var replacement=g.world.urban.containers.touch(g.world,p);
        assertTrue(replacement.id<0); assertEquals(UrbanContainers.Owner.PLAYER,replacement.owner);
        assertEquals(UrbanContainers.State.EMPTY,replacement.state);
        Path save=directory.resolve("replaced.sav"); assertTrue(SaveSystem.save(g,save));
        Game loaded=new Game(); assertTrue(SaveSystem.load(loaded,save));
        assertEquals(replacement.id,loaded.world.urban.containers.touch(loaded.world,p).id);
        assertEquals(UrbanContainers.State.DESTROYED,loaded.world.urban.containers.byId(f.fixture().id()).state);
        assertFalse(loaded.world.crateContents.containsKey(p));
    }

    @Test
    void anAbsentSectionPreservesLegacyEmptyInventoriesAndEditedReservations() throws Exception {
        Path save=directory.resolve("old.sav");
        try (var fixture=getClass().getResourceAsStream("/saves/urban-preloot-m06.sav")) {
            assertNotNull(fixture); Files.copy(fixture,save);
        }
        Game loaded=new Game(); assertTrue(SaveSystem.load(loaded,save));
        var state=loaded.world.urban.containers;
        var empty=state.byId(6917529027642524226L);
        assertEquals(UrbanContainers.State.EMPTY,empty.state);
        assertTrue(loaded.world.crateContents.get(empty.position).isEmpty(),"content-one core depletion marker retained");
        assertSame(empty,state.touch(loaded.world,empty.position),"legacy emptiness never permits a roll");
        var stocked=state.byId(6917529027642524258L);
        assertEquals(3,loaded.world.crateContents.get(stocked.position).count(ItemType.BANDAGE));
        var destroyed=state.byId(6917529027642524290L);
        assertEquals(UrbanContainers.State.DESTROYED,destroyed.state);
        assertEquals(BlockType.AIR,loaded.world.getBlock(79,37,111));
        assertEquals(2,loaded.world.crateContents.size());
        Path resaved=directory.resolve("migrated.sav"); assertTrue(SaveSystem.save(loaded,resaved));
        assertTrue(SaveSystem.load(loaded,resaved));
        assertEquals(UrbanContainers.State.EMPTY,loaded.world.urban.containers.byId(empty.id).state);
    }

    @Test
    void corruptSectionsFailBeforeReplacingTheLiveWorld() throws Exception {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        g.world.urban.containers.touch(g.world,f.position());
        Path save=directory.resolve("good.sav"); assertTrue(SaveSystem.save(g,save));
        byte[] good=Files.readAllBytes(save), section=UrbanContainersSection.write(g);
        byte[][] broken={section.clone(),section.clone(),Arrays.copyOf(section,section.length-1),
                Arrays.copyOf(section,section.length+1),section.clone(),section.clone(),section.clone(),section.clone()};
        ByteBuffer.wrap(broken[0]).putInt(0,99);
        ByteBuffer.wrap(broken[1]).putInt(16,UrbanContainers.MAX_ENTRIES+1);
        ByteBuffer.wrap(broken[4]).putLong(20,0);
        broken[5][50]=(byte) 99;
        ByteBuffer.wrap(broken[6]).putFloat(51,Float.NaN);
        broken[7][55]=0;
        World preserved=g.world;
        for (int i=0;i<broken.length;i++) {
            Path bad=directory.resolve("bad-"+i+".sav");
            Files.write(bad,CreativeSaveSections.replace(good,UrbanContainersSection.ID,broken[i]));
            assertFalse(SaveSystem.load(g,bad),"corruption "+i); assertSame(preserved,g.world);
        }
        assertTrue(SaveSystem.save(g,save));
        byte[] previous=Files.readAllBytes(save);
        g.world.crateContents.get(f.position()).get(0).freshness=Float.NaN;
        assertFalse(SaveSystem.save(g,save)); assertArrayEquals(previous,Files.readAllBytes(save));
    }

    @Test
    void newContentRequiresItsLedgerAndSectionOrderDoesNotChangeResolution() throws Exception {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        g.world.urban.containers.touch(g.world,f.position());
        Path save=directory.resolve("required.sav"); assertTrue(SaveSystem.save(g,save));
        byte[] good=Files.readAllBytes(save);
        Path absent=directory.resolve("absent.sav");
        Files.write(absent,CreativeSaveSections.replace(good,UrbanContainersSection.ID,null));
        assertFalse(SaveSystem.load(g,absent));
        byte[] metadata=UrbanMetaSection.write(g);
        byte[] reordered=CreativeSaveSections.append(CreativeSaveSections.replace(good,UrbanMetaSection.ID,null),
                UrbanMetaSection.ID,metadata);
        Files.write(save,reordered); assertTrue(SaveSystem.load(g,save));
        assertEquals(com.veylon.world.urban.UrbanRevision.CURRENT_CONTENT,g.world.urban.contentRevision());
        assertEquals(1,g.world.urban.containers.size());
    }

    @Test
    void coreUrbanInventoriesRejectOversizedDuplicateAndInvalidStacksBeforePublication() throws Exception {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        g.world.urban.containers.touch(g.world,f.position());
        Path save=directory.resolve("core-good.sav"); assertTrue(SaveSystem.save(g,save));
        byte[] good=Files.readAllBytes(save);
        byte[] marker=ByteBuffer.allocate(16).putInt(f.position().x()).putInt(f.position().y()).putInt(f.position().z()).putInt(12).array();
        int offset=-1;
        for (int i=0;i<=good.length-marker.length;i++) {
            if (Arrays.equals(marker,Arrays.copyOfRange(good,i,i+marker.length))) {
                assertEquals(-1,offset,"unique core inventory marker"); offset=i;
            }
        }
        assertTrue(offset>=4); assertEquals(1,ByteBuffer.wrap(good).getInt(offset-4));
        byte[][] bad={good.clone(),good.clone(),good.clone(),good.clone(),good.clone(),new byte[good.length+256]};
        ByteBuffer.wrap(bad[0]).putInt(offset+12,13);
        ByteBuffer.wrap(bad[1]).putInt(offset-4,UrbanContainers.MAX_ENTRIES+1);
        ByteBuffer.wrap(bad[2]).putInt(offset+16,9999);
        ByteBuffer.wrap(bad[3]).putInt(offset+20,0);
        ByteBuffer.wrap(bad[4]).putFloat(offset+28,Float.NaN);
        System.arraycopy(good,0,bad[5],0,offset+256);
        System.arraycopy(good,offset,bad[5],offset+256,256);
        System.arraycopy(good,offset+256,bad[5],offset+512,good.length-offset-256);
        ByteBuffer.wrap(bad[5]).putInt(offset-4,2);
        World preserved=g.world;
        for (int i=0;i<bad.length;i++) {
            Path corrupt=directory.resolve("core-bad-"+i+".sav"); Files.write(corrupt,bad[i]);
            assertFalse(SaveSystem.load(g,corrupt),"core corruption "+i); assertSame(preserved,g.world);
        }
    }

    @Test
    void ledgerCapacityRejectsMutationBeforeChangingBlocksOrContents() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        var state=g.world.urban.containers;
        for (int i=1;i<=UrbanContainers.MAX_ENTRIES;i++) {
            state.restore(new UrbanContainers.Entry(-i,f.position(),BlockType.CABINET,
                    UrbanContainers.Owner.PLAYER,0,UrbanContainers.State.DESTROYED,0));
        }
        state.restoreNextPlayerId(-UrbanContainers.MAX_ENTRIES-1L);
        g.world.setBlock(f.position().x(),f.position().y(),f.position().z(),BlockType.AIR,true);
        assertSame(f.fixture().block(),g.world.getBlock(f.position().x(),f.position().y(),f.position().z()));
        assertNull(state.touch(g.world,f.position())); assertTrue(g.world.crateContents.isEmpty());
    }

    @Test
    void generationAllocatesNoInventoriesAndContextRollsAreIndependentOfVisitOrder() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        assertEquals(0,g.world.urban.containers.size()); assertTrue(g.world.crateContents.isEmpty());
        var plan=UrbanContainers.planned(g.world,f.position());
        assertEquals(LootTables.Table.KITCHEN,plan.table());
        Inventory first=LootTables.roll(g.world.seed,1,f.fixture().id(),plan.table(),
                g.world.urban.districtAt(f.position().x(),f.position().z()),f.building().variant());
        LootTables.roll(9,1,1,LootTables.Table.WAREHOUSE,com.veylon.world.urban.UrbanDistrict.INDUSTRIAL,BuildingPlan.Variant.INTACT);
        Inventory again=LootTables.roll(g.world.seed,1,f.fixture().id(),plan.table(),
                g.world.urban.districtAt(f.position().x(),f.position().z()),f.building().variant());
        assertEquals(first.count(ItemType.WATERSKIN_CLEAN),again.count(ItemType.WATERSKIN_CLEAN));
        assertEquals(first.get(0).freshness,again.get(0).freshness);
        assertEquals(first.count(ItemType.DRIED_BERRY),again.count(ItemType.DRIED_BERRY));
    }
}

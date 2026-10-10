package com.veylon;

import com.veylon.settlement.HumanFaction;
import com.veylon.world.urban.BuildingPlan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrbanLootQaSceneTest {
    @Test
    void nativeSceneUsesRealSimulationSearchesAndGeneratedOwnership() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT); Game g=f.game();
        var scene=new UrbanLootQaScene(g); scene.stage();
        assertTrue(g.crateSearching()); assertNull(g.openCrate);
        for (int frame=0;frame<=280;frame++) {
            scene.update(frame*.05); g.advanceWorld(.05);
        }
        assertTrue(scene.passed()); assertFalse(g.crateSearching());
        assertTrue(g.settlementManager.reputation(g,HumanFaction.FREE_SETTLERS)<0);
        assertEquals(2,g.world.urban.containers.size(),"only the two real searched fixtures are touched");
    }

    @Test
    void wallClockCameraUpdatesCannotPretendSearchCompletedWithoutSimulation() {
        var f=UrbanLootFixture.create(BuildingPlan.Variant.INTACT);
        var scene=new UrbanLootQaScene(f.game()); scene.stage();
        scene.update(6); assertFalse(scene.passed()); assertTrue(f.game().crateSearching());
        assertThrows(IllegalStateException.class,() -> scene.update(14)); assertFalse(scene.passed());
    }
}

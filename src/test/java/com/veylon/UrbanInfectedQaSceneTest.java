package com.veylon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrbanInfectedQaSceneTest {
    @Test void wallClockCannotClaimCombatOutcomesWithoutSimulation() {
        Game g = UrbanEncounterArena.session(20260731);
        var scene = new UrbanInfectedQaScene(g); scene.stage(false);
        assertThrows(IllegalStateException.class, () -> scene.update(17));
        assertFalse(scene.passed());
        assertFalse(g.entities.encounters.residents().stream().anyMatch(r -> r.dead()));
    }
}

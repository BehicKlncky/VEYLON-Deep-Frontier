package com.veylon;

import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.GameMode;
import com.veylon.world.World;
import org.joml.Matrix3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.9.1 B07 in the scene that showed it: every species, a guard and the player's remains blown
 * apart on an open meadow ({@code dismember_species}). Before the fix about a third of the long
 * pieces there came to rest standing on their ends; now none does.
 */
class SpeciesRestTest {

    @Test
    void onTheOpenMeadowNoLongPieceComesToRestStandingOnItsEnd() {
        Game game = new Game();
        game.newWorld(20_261_126L, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        game.qa.applyBenchmarkScene("dismember_species");
        for (double t = 0; t <= 9.0; t += 0.1) {
            game.qa.updateShowcases(t);
        }
        assertEquals(0, game.fragments.liveCount(), "precondition: everything has come to rest");
        Matrix3f m = new Matrix3f();
        List<String> upright = new ArrayList<>();
        int long_ = 0;
        for (BodyFragment f : game.fragments.settled) {
            f.orientation.get(m);
            float hx = f.halfX, hy = f.halfY, hz = f.halfZ;
            float along, other, up;
            if (hy >= hx && hy >= hz) {
                along = hy; other = Math.max(hx, hz); up = m.m11;
            } else if (hx >= hz) {
                along = hx; other = Math.max(hy, hz); up = m.m01;
            } else {
                along = hz; other = Math.max(hx, hy); up = m.m21;
            }
            if (along < BodyFragmentConstants.END_TOPPLE_RATIO * other) {
                continue;
            }
            long_++;
            if (Math.abs(up) >= BodyFragmentConstants.END_UPRIGHT) {
                upright.add(f.definition + " " + up);
            }
        }
        assertTrue(long_ >= 40, "precondition: the scene throws many long pieces: " + long_);
        assertEquals(List.of(), upright, "long pieces standing on end");
    }
}

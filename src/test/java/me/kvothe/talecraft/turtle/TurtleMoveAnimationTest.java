package me.kvothe.talecraft.turtle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TurtleMoveAnimationTest {

    @Test
    void mapsAllLocalDirections() {
        assertEquals("MovePosX", state(1, 0, 0));
        assertEquals("MoveNegX", state(-1, 0, 0));
        assertEquals("MovePosY", state(0, 1, 0));
        assertEquals("MoveNegY", state(0, -1, 0));
        assertEquals("MovePosZ", state(0, 0, 1));
        assertEquals("MoveNegZ", state(0, 0, -1));
    }

    @Test
    void interpolationIsBoundedAndMonotonic() {
        assertEquals(1.0, TurtleMoveAnimation.remainingFraction(-1));
        assertEquals(0.75, TurtleMoveAnimation.remainingFraction(TurtleMoveAnimation.DURATION_NANOS / 4));
        assertEquals(0.0, TurtleMoveAnimation.remainingFraction(TurtleMoveAnimation.DURATION_NANOS));
        assertEquals(0.0, TurtleMoveAnimation.remainingFraction(TurtleMoveAnimation.DURATION_NANOS * 2));
    }

    @Test
    void mapsTurnStatesAndInterpolatesYaw() {
        assertEquals("TurnLeft", TurtleMoveAnimation.turnStateFor(true));
        assertEquals("TurnRight", TurtleMoveAnimation.turnStateFor(false));
        assertEquals(Math.PI / 2,
                TurtleMoveAnimation.remainingYaw(Math.PI / 2, 0));
        assertEquals(Math.PI / 4,
                TurtleMoveAnimation.remainingYaw(Math.PI / 2,
                        TurtleMoveAnimation.DURATION_NANOS / 2));
        assertEquals(0.0, TurtleMoveAnimation.remainingYaw(Math.PI / 2,
                TurtleMoveAnimation.DURATION_NANOS));
    }

    @Test
    void rejectsZeroMovement() {
        assertThrows(IllegalArgumentException.class,
                () -> TurtleMoveAnimation.stateFor(0, 0, 0));
    }

    private static String state(int x, int y, int z) {
        return TurtleMoveAnimation.stateFor(x, y, z);
    }
}

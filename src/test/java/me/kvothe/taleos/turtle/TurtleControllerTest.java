package me.kvothe.taleos.turtle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurtleControllerTest {

    @Test
    void acceptsBaseAndAnimatedTurtleBlocks() {
        assertTrue(TurtleBlockIds.isTurtleBlockId("TaleOS_Turtle"));
        assertTrue(TurtleBlockIds.isTurtleBlockId(
                "*TaleOS_Turtle_State_Definitions_MovePosZ"));
        assertTrue(TurtleBlockIds.isTurtleBlockId(
                "*TaleOS_Turtle_State_Definitions_MoveNegY"));
    }

    @Test
    void rejectsOtherBlocksAndMissingIds() {
        assertFalse(TurtleBlockIds.isTurtleBlockId("TaleOS_Computer"));
        assertFalse(TurtleBlockIds.isTurtleBlockId("TaleOS_Turtle_Other"));
        assertFalse(TurtleBlockIds.isTurtleBlockId(null));
    }
}

package me.kvothe.talecraft.turtle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurtleControllerTest {

    @Test
    void acceptsBaseAndAnimatedTurtleBlocks() {
        assertTrue(TurtleBlockIds.isTurtleBlockId("TaleCraft_Turtle"));
        assertTrue(TurtleBlockIds.isTurtleBlockId(
                "*TaleCraft_Turtle_State_Definitions_MovePosZ"));
        assertTrue(TurtleBlockIds.isTurtleBlockId(
                "*TaleCraft_Turtle_State_Definitions_MoveNegY"));
    }

    @Test
    void rejectsOtherBlocksAndMissingIds() {
        assertFalse(TurtleBlockIds.isTurtleBlockId("TaleCraft_Computer"));
        assertFalse(TurtleBlockIds.isTurtleBlockId("TaleCraft_Turtle_Other"));
        assertFalse(TurtleBlockIds.isTurtleBlockId(null));
    }
}

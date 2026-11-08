package me.kvothe.taleos.turtle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurtleUpgradeSlotsTest {

    @Test
    void fourInternalSlotsFollowTheExternalUpgrades() {
        assertEquals(22, TurtleController.INVENTORY_CAPACITY);
        assertEquals(18, TurtleController.internalSlot(1));
        assertEquals(21, TurtleController.internalSlot(4));
        assertEquals(-1, TurtleController.internalSlot(0));
        assertEquals(-1, TurtleController.internalSlot(5));
    }

    @Test
    void internalPeripheralNamesResolveOnlyToValidSlots() {
        assertEquals(TurtleController.LEFT_TOOL_SLOT, TurtleController.sideSlot("left"));
        assertEquals(TurtleController.RIGHT_TOOL_SLOT, TurtleController.sideSlot("right"));
        assertEquals(19, TurtleController.sideSlot("internal2"));
        assertEquals(-1, TurtleController.sideSlot("internal0"));
        assertEquals(-1, TurtleController.sideSlot("internal5"));
        assertEquals(-1, TurtleController.sideSlot("internalTwo"));
        assertTrue(TurtleController.isInternalUpgradeName("internal4"));
        assertFalse(TurtleController.isInternalUpgradeName("right"));
        assertFalse(TurtleController.isInternalUpgradeName(null));
    }
}

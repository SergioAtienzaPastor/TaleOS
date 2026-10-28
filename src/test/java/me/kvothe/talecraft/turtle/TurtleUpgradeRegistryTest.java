package me.kvothe.talecraft.turtle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurtleUpgradeRegistryTest {
    @Test
    void registersEveryUpgradeKindAndRejectsOrdinaryItems() {
        TurtleUpgradeRegistry registry = new TurtleUpgradeRegistry();
        registry.register("Tool_Custom", TurtleUpgradeRegistry.Kind.TOOL);
        registry.register("Modem_Custom", TurtleUpgradeRegistry.Kind.MODEM);
        registry.register("Speaker_Custom", TurtleUpgradeRegistry.Kind.SPEAKER);
        registry.register("Crafting_Custom", TurtleUpgradeRegistry.Kind.CRAFTING);

        assertEquals(TurtleUpgradeRegistry.Kind.TOOL, registry.kindOf("Tool_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.MODEM, registry.kindOf("Modem_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.SPEAKER, registry.kindOf("Speaker_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.CRAFTING, registry.kindOf("Crafting_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.TOOL, registry.kindOf("Any_Vanilla_Tool", true));
        assertEquals(TurtleUpgradeRegistry.Kind.TOOL, registry.kindOf("Any_Vanilla_Sword", false, true));
        assertTrue(registry.accepts("Tool_Custom", false));
        assertFalse(registry.accepts("Stone", false));
        assertNull(registry.kindOf(null, false));
    }

    @Test
    void defaultsExposeModemSpeakerAndCraftingIds() {
        TurtleUpgradeRegistry registry = TurtleUpgradeRegistry.defaults();
        assertEquals(TurtleUpgradeRegistry.Kind.MODEM,
                registry.kindOf(TurtleUpgradeRegistry.MODEM_ID, false));
        assertEquals(TurtleUpgradeRegistry.Kind.SPEAKER,
                registry.kindOf(TurtleUpgradeRegistry.SPEAKER_ID, false));
        assertEquals("TaleCraft_Speaker", TurtleUpgradeRegistry.SPEAKER_ID);
        assertEquals(TurtleUpgradeRegistry.Kind.CRAFTING,
                registry.kindOf(TurtleUpgradeRegistry.CRAFTING_ID, false));
    }
}

package me.kvothe.taleos.turtle;

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
        registry.register("Chunk_Custom", TurtleUpgradeRegistry.Kind.CHUNK_LOADER);
        registry.register("Link_Custom", TurtleUpgradeRegistry.Kind.PLAYER_LINK);

        assertEquals(TurtleUpgradeRegistry.Kind.TOOL, registry.kindOf("Tool_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.MODEM, registry.kindOf("Modem_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.SPEAKER, registry.kindOf("Speaker_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.CRAFTING, registry.kindOf("Crafting_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.CHUNK_LOADER, registry.kindOf("Chunk_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.PLAYER_LINK, registry.kindOf("Link_Custom", false));
        assertEquals(TurtleUpgradeRegistry.Kind.TOOL, registry.kindOf("Any_Vanilla_Tool", true));
        assertEquals(TurtleUpgradeRegistry.Kind.TOOL, registry.kindOf("Any_Vanilla_Sword", false, true));
        assertTrue(registry.accepts("Tool_Custom", false));
        assertFalse(registry.accepts("Stone", false));
        assertNull(registry.kindOf(null, false));
    }

    @Test
    void defaultsExposeAllUpgradeIds() {
        TurtleUpgradeRegistry registry = TurtleUpgradeRegistry.defaults();
        assertEquals(TurtleUpgradeRegistry.Kind.MODEM,
                registry.kindOf(TurtleUpgradeRegistry.MODEM_ID, false));
        assertEquals(TurtleUpgradeRegistry.Kind.SPEAKER,
                registry.kindOf(TurtleUpgradeRegistry.SPEAKER_ID, false));
        assertEquals("TaleOS_Speaker", TurtleUpgradeRegistry.SPEAKER_ID);
        assertEquals(TurtleUpgradeRegistry.Kind.CRAFTING,
                registry.kindOf(TurtleUpgradeRegistry.CRAFTING_ID, false));
        assertEquals(TurtleUpgradeRegistry.Kind.CHUNK_LOADER,
                registry.kindOf(TurtleUpgradeRegistry.CHUNK_LOADER_ID, false));
        assertEquals(TurtleUpgradeRegistry.Kind.PLAYER_LINK,
                registry.kindOf(TurtleUpgradeRegistry.PLAYER_LINK_ID, false));
    }

    @Test
    void internalSlotsAcceptModulesButNotToolsOrWeapons() {
        TurtleUpgradeRegistry registry = TurtleUpgradeRegistry.defaults();
        assertTrue(registry.acceptsInternal(TurtleUpgradeRegistry.CHUNK_LOADER_ID, false));
        assertTrue(registry.acceptsInternal(TurtleUpgradeRegistry.CRAFTING_ID, false));
        assertTrue(registry.acceptsInternal(TurtleUpgradeRegistry.MODEM_ID, false));
        assertTrue(registry.acceptsInternal(TurtleUpgradeRegistry.PLAYER_LINK_ID, false));
        assertFalse(registry.acceptsInternal("Tool_Pickaxe_Copper", true));
        assertFalse(registry.acceptsInternal("Weapon_Sword_Copper", false));
        assertFalse(registry.acceptsInternal("Stone", false));
    }
}

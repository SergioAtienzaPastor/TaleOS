package me.kvothe.talecraft.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class NasManagerTest {

    @Test
    void stackedUnitsShareOneComponent() {
        Set<String> units = Set.of("world:10:64:10", "world:10:65:10", "world:10:66:10",
                "world:11:66:10", "world:30:64:10");

        Set<String> connected = NasManager.connectedUnitsWithin(units, "world:10:64:12", 16);

        assertEquals(Set.of("world:10:64:10", "world:10:65:10", "world:10:66:10",
                "world:11:66:10"), connected);
    }

    @Test
    void gapAndRangeAreEnforced() {
        Set<String> units = Set.of("world:0:64:0", "world:2:64:0", "world:20:64:0",
                "other:0:64:0");

        Set<String> connected = NasManager.connectedUnitsWithin(units, "world:0:64:1", 16);

        assertEquals(Set.of("world:0:64:0", "world:2:64:0"), connected);
        assertTrue(NasManager.connectedUnitsWithin(units, "world:19:64:0", 2)
                .contains("world:20:64:0"));
    }
}

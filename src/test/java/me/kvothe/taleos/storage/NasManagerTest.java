package me.kvothe.taleos.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.taleos.os.VirtualFileSystem;

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

    @Test
    void staleNasMountsAreRemovedWithoutTouchingOtherMounts(@TempDir Path dir) throws Exception {
        VirtualFileSystem fs = new VirtualFileSystem(dir.resolve("computer"), 1000);
        fs.mount("nas/19", dir.resolve("19"), 100);
        fs.mount("nas/22", dir.resolve("22"), 100);
        fs.mount("nas/23", dir.resolve("23"), 100);
        fs.mount("usb", dir.resolve("usb"), 100);

        NasManager.removeStaleNasMounts(fs, Set.of("nas/19", "nas/22"));

        assertEquals(List.of("19", "22"), fs.list("nas"));
        assertTrue(fs.isMounted("usb"));
        assertFalse(fs.isMounted("nas/23"));
    }
}

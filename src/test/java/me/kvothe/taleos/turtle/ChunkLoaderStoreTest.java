package me.kvothe.taleos.turtle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.taleos.turtle.ChunkLoaderStore.Location;

class ChunkLoaderStoreTest {

    @TempDir
    Path dir;

    @Test
    void locationsSurviveReloadAndKeepEscapedWorldNames() throws Exception {
        Path file = dir.resolve("chunk_loaders.json");
        ChunkLoaderStore store = new ChunkLoaderStore(file);
        Location first = new Location("creative:test\"\\world", 100, 64, -32);
        Location second = new Location("world", -1, 80, 0);
        store.put(7, first);
        store.put(8, second);
        store.save();

        ChunkLoaderStore loaded = new ChunkLoaderStore(file);
        loaded.load();
        assertEquals(first, loaded.get(7));
        assertEquals(second, loaded.get(8));
        assertEquals(2, loaded.entries().size());

        loaded.remove(7);
        loaded.save();
        ChunkLoaderStore removed = new ChunkLoaderStore(file);
        removed.load();
        assertNull(removed.get(7));
        assertEquals(second, removed.get(8));
    }

    @Test
    void missingFileStartsEmpty() throws Exception {
        ChunkLoaderStore store = new ChunkLoaderStore(dir.resolve("missing.json"));
        store.load();
        assertEquals(0, store.entries().size());
    }

    @Test
    void duplicateComputerIdsAreRejected() throws Exception {
        Path file = dir.resolve("duplicate.json");
        String first = "\"7\":{\"world\":\"world\",\"x\":1,\"y\":2,\"z\":3}";
        Files.writeString(file, "{\"loaders\":{" + first + "," + first + "}}");
        ChunkLoaderStore store = new ChunkLoaderStore(file);
        assertThrows(java.io.IOException.class, store::load);
    }

    @Test
    void blankWorldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Location(" ", 1, 2, 3));
    }
}

package me.kvothe.taleos.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ComputerIdStore: ids must survive a save+load roundtrip (server restart). */
class ComputerIdStoreTest {

    @TempDir
    Path dir;

    @Test
    void idsSurviveReload() throws Exception {
        ComputerIdStore a = new ComputerIdStore(dir.resolve("ids.json"));
        int id = a.idFor("world:10:64:-3");
        int other = a.idFor("creative_test:1:2:3");
        a.save();

        ComputerIdStore b = new ComputerIdStore(dir.resolve("ids.json"));
        b.load();
        assertEquals(id, b.idFor("world:10:64:-3"), "same block key must keep its id after reload");
        assertEquals(other, b.idFor("creative_test:1:2:3"));
        assertEquals(Math.max(id, other) + 1, b.idFor("world:0:0:0"), "new keys get fresh ids");
    }

    @Test
    void movingBlockKeepsIdAndRemovesOldPosition() throws Exception {
        ComputerIdStore store = new ComputerIdStore(dir.resolve("ids.json"));
        int id = store.idFor("world:1:2:3");
        int moved = store.move("world:1:2:3", "world:2:2:3");
        store.save();

        ComputerIdStore loaded = new ComputerIdStore(dir.resolve("ids.json"));
        loaded.load();
        assertEquals(id, moved);
        assertEquals(id, loaded.idFor("world:2:2:3"));
        assertEquals(id + 1, loaded.idFor("world:1:2:3"),
                "the old position is a new block, not the moved computer");
    }

    @Test
    void adoptMovesLegacyKeyWithoutLeavingAnAlias() {
        ComputerIdStore s = new ComputerIdStore(dir.resolve("ids.json"));
        int id = s.idFor("world:1:2:3");
        s.adopt("world:1:2:3", "creative_test:1:2:3");
        assertEquals(id, s.idFor("creative_test:1:2:3"), "legacy id adopted, disk preserved");
        assertEquals(id + 1, s.idFor("world:1:2:3"), "legacy position is free");

        s.adopt("world:9:9:9", "creative_test:9:9:9");
        assertEquals(id + 2, s.idFor("creative_test:9:9:9"));
    }

    @Test
    void unbindFreesThePositionWithoutReusingItsId() throws Exception {
        Path file = dir.resolve("ids.json");
        ComputerIdStore s = new ComputerIdStore(file);
        int id = s.idFor("world:1:2:3");
        assertEquals(id, s.unbind("world:1:2:3"));
        s.save();

        ComputerIdStore loaded = new ComputerIdStore(file);
        loaded.load();
        assertEquals(id + 1, loaded.idFor("world:1:2:3"));
    }

    @Test
    void bindRejectsAnIdThatIsAlreadyActiveElsewhere() {
        ComputerIdStore s = new ComputerIdStore(dir.resolve("ids.json"));
        int id = s.idFor("world:1:2:3");
        assertThrows(IllegalStateException.class, () -> s.bind("world:4:5:6", id));
        assertEquals(id + 1, s.idFor("world:4:5:6"), "failed bind leaves no partial mapping");
        assertThrows(IllegalStateException.class, () -> s.bind("world:4:5:6", id + 2));
        assertEquals(id + 1, s.idFor("world:4:5:6"), "occupied position keeps its id");
    }

    @Test
    void moveRejectsAnOccupiedDestinationWithoutMovingTheSource() {
        ComputerIdStore s = new ComputerIdStore(dir.resolve("ids.json"));
        int source = s.idFor("world:1:2:3");
        int destination = s.idFor("world:4:5:6");
        assertThrows(IllegalStateException.class,
                () -> s.move("world:1:2:3", "world:4:5:6"));
        assertEquals(source, s.idFor("world:1:2:3"));
        assertEquals(destination, s.idFor("world:4:5:6"));
    }

    @Test
    void adoptRejectsConflictingRealMapping() {
        ComputerIdStore s = new ComputerIdStore(dir.resolve("ids.json"));
        int legacy = s.idFor("world:1:2:3");
        int real = s.idFor("creative_test:1:2:3");
        assertThrows(IllegalStateException.class,
                () -> s.adopt("world:1:2:3", "creative_test:1:2:3"));
        assertEquals(legacy, s.idFor("world:1:2:3"));
        assertEquals(real, s.idFor("creative_test:1:2:3"));
    }

    @Test
    void loadRejectsDuplicateIds() throws Exception {
        Path file = dir.resolve("ids.json");
        Files.writeString(file, "{\"next\":2,\"blocks\":{\"world:1:2:3\":0,\"world:4:5:6\":0}}");
        ComputerIdStore s = new ComputerIdStore(file);
        assertThrows(java.io.IOException.class, s::load);
    }

    @Test
    void loadMigratesLegacyWorldAliasesToTheRealWorldKey() throws Exception {
        Path file = dir.resolve("ids.json");
        Files.writeString(file, "{\"next\":9,\"blocks\":{"
                + "\"world:1:2:3\":8,\"creative_test:1:2:3\":8}}");
        ComputerIdStore s = new ComputerIdStore(file);
        s.load();
        assertEquals(8, s.idFor("creative_test:1:2:3"));
        assertEquals(9, s.idFor("world:1:2:3"),
                "a stale legacy alias no longer reserves the real machine id");
    }
}

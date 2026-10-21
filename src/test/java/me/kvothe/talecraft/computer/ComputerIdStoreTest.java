package me.kvothe.talecraft.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void adoptAliasesLegacyKey() {
        ComputerIdStore s = new ComputerIdStore(dir.resolve("ids.json"));
        int id = s.idFor("world:1:2:3");
        s.adopt("world:1:2:3", "creative_test:1:2:3");
        assertEquals(id, s.idFor("creative_test:1:2:3"), "legacy id adopted, disk preserved");

        // No legacy mapping: adopt is a no-op and the real key gets a fresh id.
        s.adopt("world:9:9:9", "creative_test:9:9:9");
        assertEquals(id + 1, s.idFor("creative_test:9:9:9"));

        // An existing real mapping is never overwritten.
        s.adopt("world:1:2:3", "creative_test:1:2:3");
        assertEquals(id, s.idFor("creative_test:1:2:3"));
    }
}

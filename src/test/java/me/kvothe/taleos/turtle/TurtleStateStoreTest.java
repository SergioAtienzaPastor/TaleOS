package me.kvothe.taleos.turtle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TurtleStateStoreTest {

    @TempDir
    Path dir;

    @Test
    void selectedSlotSurvivesReload() throws Exception {
        TurtleStateStore store = new TurtleStateStore(dir.resolve("turtles.json"));
        assertEquals(0, store.getSelectedSlot(7));
        store.setSelectedSlot(7, 15);
        store.save();

        TurtleStateStore loaded = new TurtleStateStore(dir.resolve("turtles.json"));
        loaded.load();
        assertEquals(15, loaded.getSelectedSlot(7));
        assertEquals(0, loaded.getSelectedSlot(8));
    }

    @Test
    void rejectsSlotsOutsideTheSixteenInventorySlots() {
        TurtleStateStore store = new TurtleStateStore(dir.resolve("turtles.json"));
        assertThrows(IllegalArgumentException.class, () -> store.setSelectedSlot(7, -1));
        assertThrows(IllegalArgumentException.class, () -> store.setSelectedSlot(7, 16));
    }
}

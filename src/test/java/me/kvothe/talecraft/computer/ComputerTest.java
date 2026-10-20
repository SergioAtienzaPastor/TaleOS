package me.kvothe.talecraft.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Computer end-to-end headless: rom overlay from the jar (test classpath). */
class ComputerTest {

    @TempDir
    Path dir;

    private Computer bootComputer() throws Exception {
        Computer c = new Computer(42, dir.resolve("c42"), me.kvothe.talecraft.lua.WorldBridge.NONE);
        c.boot();
        return c;
    }

    @Test
    void bootsTaleOsFromJarRom() throws Exception {
        Computer c = bootComputer();
        String out = c.drainOutput();
        assertTrue(out.contains("TaleOS v0.1"), "banner, got: " + out);
        assertEquals("tale> ", c.getPrompt(), "shell prompt");
        assertTrue(c.isAlive());
    }

    @Test
    void helpAndIdRun() throws Exception {
        Computer c = bootComputer();
        c.drainOutput();
        c.queueStdin("id");
        c.tick();
        assertTrue(c.drainOutput().contains("ordenador #42"));
        c.queueStdin("help");
        c.tick();
        assertTrue(c.drainOutput().contains("comandos"));
    }

    @Test
    void clearWipesTheScrollback() throws Exception {
        Computer c = bootComputer();
        assertFalse(c.getScrollback().isEmpty());
        c.queueStdin("clear");
        c.tick();
        // the prompt lives in the page's input row now; clear wipes everything
        assertEquals("", c.getScrollback(),
                "after clear the transcript is empty; got: " + c.getScrollback());
        // new output still works after the wipe
        c.queueStdin("id");
        c.tick();
        assertTrue(c.getScrollback().contains("ordenador #42"));
    }
}

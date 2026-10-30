package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupTest {
    @TempDir
    Path dir;

    @Test
    void runsRootStartupBeforeTheShell() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("valid"), "print('startup ran')");

        machine.tick();

        String output = machine.drainOutput();
        assertTrue(output.contains("TaleOS v0.1"), output);
        assertTrue(output.contains("startup ran"), output);
        assertTrue(machine.isAlive());
        assertEquals("tale> ", machine.getPrompt());
    }

    @Test
    void reportsStartupErrorsAndStillStartsTheShell() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("error"), "error('boom')");

        machine.tick();

        String output = machine.drainOutput();
        assertTrue(output.contains("startup.lua:"), output);
        assertTrue(output.contains("boom"), output);
        assertTrue(machine.isAlive());
        assertEquals("tale> ", machine.getPrompt());
    }

    @Test
    void startupSleepUsesTimerTicks() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("sleep"),
                "os.sleep(0.1); print('startup awake')");
        machine.tick();
        machine.drainOutput();

        machine.tick();
        assertEquals("", machine.drainOutput());
        machine.tick();

        assertTrue(machine.drainOutput().contains("startup awake"));
        assertTrue(machine.isAlive());
        assertEquals("tale> ", machine.getPrompt());
    }

    @Test
    void terminateInterruptsStartupSleepAndShellContinues() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("stop"), "os.sleep(60)");
        machine.tick();
        machine.drainOutput();

        machine.requestTerminate();
        machine.tick();

        assertTrue(machine.drainOutput().contains(LuaMachine.TERMINATED_MESSAGE));
        assertTrue(machine.isAlive());
        assertEquals("tale> ", machine.getPrompt());
    }
}

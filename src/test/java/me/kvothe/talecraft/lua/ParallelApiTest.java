package me.kvothe.talecraft.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParallelApiTest {
    @TempDir
    Path dir;

    @Test
    void waitForAnyHonorsFiltersAndReturnsTheWinningTask() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("any"), """
                local winner = parallel.waitForAny(
                  function() local _, value = os.pullEvent('go'); print('first', value) end,
                  function() os.pullEvent('other'); print('second') end
                )
                print('winner', winner)
                """);
        machine.tick();
        machine.drainOutput();
        machine.queueEvent("ignored");
        machine.tick();
        assertEquals("", machine.drainOutput());

        machine.queueEvent("go", 42);
        machine.tick();

        assertEquals("first\t42\nwinner\t1\n", machine.drainOutput());
        assertTrue(machine.isAlive());
    }

    @Test
    void waitForAllDistributesOneEventToEveryMatchingTask() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("all"), """
                parallel.waitForAll(
                  function() local _, value = os.pullEvent('go'); print('a', value) end,
                  function() local _, value = os.pullEvent('go'); print('b', value) end
                )
                print('all done')
                """);
        machine.tick();
        machine.drainOutput();

        machine.queueEvent("go", "shared");
        machine.tick();

        assertEquals("a\tshared\nb\tshared\nall done\n", machine.drainOutput());
        assertTrue(machine.isAlive());
    }

    @Test
    void parallelCoroutinesCannotEscapeTheInstructionBudget() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("budget"),
                "parallel.waitForAll(function() while true do end end)");

        machine.tick();

        assertEquals(LuaMachine.State.DEAD, machine.getState());
        assertTrue(machine.getError().contains("budget"));
    }

    @Test
    void terminateInterruptsParallelAndReturnsToTheShell() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("terminate"),
                "parallel.waitForAny(function() os.pullEvent() end)");
        machine.tick();
        machine.drainOutput();

        machine.requestTerminate();
        machine.tick();

        String output = machine.drainOutput();
        assertTrue(output.contains("startup.lua: " + LuaMachine.TERMINATED_MESSAGE), output);
        assertTrue(machine.isAlive());
        assertEquals("tale> ", machine.getPrompt());
    }
}

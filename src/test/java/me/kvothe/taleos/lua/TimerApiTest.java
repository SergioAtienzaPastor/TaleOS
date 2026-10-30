package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TimerApiTest {
    @TempDir
    Path dir;

    @Test
    void timersFireOnTheirExactTickWithMonotonicIds() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("timers"), """
                local first = os.startTimer(0.1)
                local second = os.startTimer(0.1)
                print('ids', first, second)
                for i = 1, 2 do local ev, id = os.pullEventRaw(); print(ev, id) end
                """);
        machine.tick();
        assertTrue(machine.drainOutput().contains("ids\t1\t2\n"));

        machine.tick();
        assertEquals("", machine.drainOutput());
        assertTrue(machine.isAlive());
        machine.tick();
        assertEquals("timer\t1\n", machine.drainOutput());
        machine.tick();

        assertEquals("timer\t2\n", machine.drainOutput());
        assertTrue(machine.isAlive());
    }

    @Test
    void cancelsATimerBeforeItFires() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("cancel-before"),
                "local id = os.startTimer(0.05); os.cancelTimer(id); local ev = os.pullEventRaw(); print(ev)");
        machine.tick();
        machine.drainOutput();
        machine.tick();
        machine.tick();
        assertEquals("", machine.drainOutput());

        machine.queueEvent("done");
        machine.tick();

        assertEquals("done\n", machine.drainOutput());
    }

    @Test
    void cancelsATimerAfterItsEventWasQueued() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("cancel-after"), """
                local id = os.startTimer(0.05)
                tale.sleep(2)
                os.cancelTimer(id)
                local ev = os.pullEventRaw()
                print(ev)
                """);
        machine.tick();
        machine.drainOutput();
        machine.tick();
        assertEquals(1, machine.getQueuedEventCount());

        machine.tick();
        machine.queueEvent("done");
        machine.tick();

        assertEquals("done\n", machine.drainOutput());
        assertEquals(0, machine.getQueuedEventCount());
    }
}

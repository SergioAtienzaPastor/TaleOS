package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SleepTest {

    private static final long BUDGET = 200_000;

    @Test
    void sleepParksTheMachineForNTicks() {
        LuaMachine m = new LuaMachine(BUDGET);
        m.load("print('a'); tale.sleep(3); print('b')", "sleepy");
        m.tick();
        assertEquals("a\n", m.drainOutput());
        assertTrue(m.isAlive());

        m.tick();
        m.tick();
        assertEquals("", m.drainOutput(), "sleeping machine prints nothing");
        assertTrue(m.isAlive());

        m.tick(); // third sleep tick ends the timer and resumes
        assertEquals("b\n", m.drainOutput());
        assertEquals(LuaMachine.State.DONE, m.getState());
    }

    @Test
    void eventsStayQueuedWhileSleeping() {
        LuaMachine m = new LuaMachine(BUDGET);
        m.load("tale.sleep(2); local e = tale.pullEvent(); print('ev', e)", "sleepy2");
        m.tick();
        m.queueEvent("key"); // arrives while asleep; must not be lost
        m.tick(); // sleep counts down, event untouched
        m.tick(); // wakes, pullEvent suspends again
        assertEquals("", m.drainOutput(), "nothing printed before the event is delivered");
        m.tick(); // the queued event is delivered to pullEvent
        assertEquals("ev\tkey\n", m.drainOutput());
    }

    @Test
    void sleepZeroMeansOneTick() {
        LuaMachine m = new LuaMachine(BUDGET);
        m.load("tale.sleep(0); print('done')", "sleepy3");
        m.tick();
        assertEquals("", m.drainOutput());
        m.tick();
        assertEquals("done\n", m.drainOutput());
    }

    @Test
    void sleepDoesNotBurnBudget() {
        // Sleeping must park the machine instead of spinning: a loop of sleeps
        // survives many ticks on a small budget.
        LuaMachine m = new LuaMachine(50_000);
        m.load("for i = 1, 3 do tale.sleep(1) end; print('awake')", "spin");
        m.tick(); // runs to the first sleep
        assertTrue(m.isAlive());
        m.tick(); // wakes, second sleep
        m.tick(); // wakes, third sleep
        assertTrue(m.isAlive());
        m.tick(); // wakes, prints, finishes
        assertEquals("awake\n", m.drainOutput());
        assertEquals(LuaMachine.State.DONE, m.getState());
    }
}

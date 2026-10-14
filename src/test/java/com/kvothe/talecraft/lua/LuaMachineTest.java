package com.kvothe.talecraft.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LuaMachineTest {

    private static final long BUDGET = 100_000;

    @Test
    void infiniteLoopDiesByBudgetWithoutHarmingHost() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load("while true do end", "loop");
        machine.tick();
        assertEquals(LuaMachine.State.DEAD, machine.getState());
        assertTrue(machine.getError() != null && machine.getError().contains("budget"),
                "error should mention the budget, was: " + machine.getError());

        // The host thread is fine: another machine runs normally right after.
        LuaMachine survivor = new LuaMachine(BUDGET);
        survivor.load("print('alive')", "ok");
        survivor.tick();
        assertEquals(LuaMachine.State.DONE, survivor.getState());
        assertEquals("alive\n", survivor.drainOutput());
    }

    @Test
    void pcallCannotEscapeTheBudget() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load("print(pcall(function() while true do end end))", "trap");
        machine.tick();
        assertEquals(LuaMachine.State.DEAD, machine.getState(),
                "pcall must not swallow the budget kill; state=" + machine.getState());
    }

    @Test
    void printIsCapturedWithTabSeparator() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load("print('Hello', 'World', 42)", "hello");
        machine.tick();
        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("Hello\tWorld\t42\n", machine.drainOutput());
    }

    @Test
    void sandboxHidesDangerousLibraries() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load(
                "print(type(io), type(luajava), type(debug), type(os and os.exit), type(package))",
                "probe1");
        machine.tick();
        assertEquals("nil\tnil\tnil\tnil\tnil\n", machine.drainOutput());

        LuaMachine safe = new LuaMachine(BUDGET);
        safe.load("print(type(os.clock), type(string.rep), type(coroutine.create))", "probe2");
        safe.tick();
        assertEquals("function\tfunction\tfunction\n", safe.drainOutput());
    }

    @Test
    void pullEventSuspendsAndNextTickDelivers() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load("local e = tale.pullEvent(); print('got', e)", "events");
        machine.tick();
        assertTrue(machine.isAlive(), "machine should be suspended waiting for an event");
        assertEquals("", machine.drainOutput());

        machine.queueEvent("key");
        machine.tick();
        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("got\tkey\n", machine.drainOutput());
    }

    @Test
    void compileErrorKillsMachineAtLoad() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load("this is not lua", "broken");
        assertEquals(LuaMachine.State.DEAD, machine.getState());
        assertFalse(machine.isAlive());
    }
}

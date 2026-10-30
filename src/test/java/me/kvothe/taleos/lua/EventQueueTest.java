package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

class EventQueueTest {
    private static final long BUDGET = 100_000;

    @Test
    void preservesTypedArgumentsIncludingNilAndTables() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load("local ev, number, bool, missing, tbl, tail = tale.pullEvent()\n"
                + "print(ev, number, bool, missing == nil, tbl.answer, tail)", "typed-events");
        machine.tick();
        LuaTable table = new LuaTable();
        table.set("answer", 42);

        assertTrue(machine.queueEvent("typed", 7, true, null, table, "tail"));
        machine.tick();

        assertEquals("typed\t7\ttrue\ttrue\t42\ttail\n", machine.drainOutput());
    }

    @Test
    void deliversEventsInFifoOrder() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load("for i = 1, 3 do local ev, value = tale.pullEvent(); print(ev, value) end",
                "fifo-events");
        machine.tick();
        machine.queueEvent("first", 1);
        machine.queueEvent("second", 2);
        machine.queueEvent("third", 3);

        machine.tick();
        machine.tick();
        machine.tick();

        assertEquals("first\t1\nsecond\t2\nthird\t3\n", machine.drainOutput());
    }

    @Test
    void rejectsNewEventsWhenTheQueueIsFull() {
        LuaMachine machine = new LuaMachine(BUDGET);
        for (int i = 0; i < LuaMachine.MAX_EVENT_QUEUE; i++) {
            assertTrue(machine.queueEvent("event", i));
        }

        assertFalse(machine.queueEvent("overflow"));
        assertEquals(LuaMachine.MAX_EVENT_QUEUE, machine.getQueuedEventCount());
    }

    @Test
    void luaQueueEventKeepsTypesAndTrailingNil() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load("os.queueEvent('custom', 3, false, { value = 'ok' }, nil)\n"
                + "local ev, number, bool, tbl, missing = tale.pullEvent()\n"
                + "print(ev, number, bool, tbl.value, missing == nil, select('#', ev, number, bool, tbl, missing))",
                "lua-queue-event");
        machine.tick();
        machine.tick();

        assertEquals("custom\t3\tfalse\tok\ttrue\t5\n", machine.drainOutput());
    }
}

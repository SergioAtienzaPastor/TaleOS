package me.kvothe.talecraft.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WorldApiTest {

    private static final long BUDGET = 200_000;

    /** Computer at (10, 64, 5); only block (10, 63, 5) exists ("Stone"). */
    private static final class FakeBridge implements WorldBridge {
        String chatLog;

        @Override
        public String getBlockId(int x, int y, int z) {
            return x == 10 && y == 63 && z == 5 ? "Stone" : null;
        }

        @Override
        public double getTime() {
            return 1234.5;
        }

        @Override
        public void chat(String message) {
            chatLog = message;
        }

        @Override
        public int getOriginX() {
            return 10;
        }

        @Override
        public int getOriginY() {
            return 64;
        }

        @Override
        public int getOriginZ() {
            return 5;
        }
    }

    private LuaMachine machineWithWorld() {
        LuaMachine m = new LuaMachine(BUDGET);
        m.setWorldBridge(new FakeBridge());
        return m;
    }

    @Test
    void getBlockAndDetectUseRelativeCoords() {
        LuaMachine m = machineWithWorld();
        m.load("print(tale.getBlock(0,-1,0), tale.detect(), tale.detect(0,5,0), tale.getBlock(1,0,0))", "w1");
        m.tick();
        assertEquals(LuaMachine.State.DONE, m.getState());
        assertEquals("Stone\ttrue\tfalse\tnil\n", m.drainOutput());
    }

    @Test
    void chatAndTimeReachTheBridge() {
        FakeBridge bridge = new FakeBridge();
        LuaMachine m = new LuaMachine(BUDGET);
        m.setWorldBridge(bridge);
        m.load("tale.chat('hola mundo'); print(tale.time())", "w2");
        m.tick();
        assertEquals("hola mundo", bridge.chatLog);
        assertEquals("1234.5\n", m.drainOutput());
    }

    @Test
    void worldCallLoopsStillDieByBudget() {
        LuaMachine m = machineWithWorld();
        m.load("while true do tale.getBlock(0,-1,0) end", "w3");
        m.tick();
        assertEquals(LuaMachine.State.DEAD, m.getState(),
                "a loop of world calls must die by budget like any other loop");
    }

    @Test
    void worldCallsOverTheTickCapReturnNil() {
        LuaMachine m = machineWithWorld();
        m.load("local s = 0; for i = 1, 40 do if tale.getBlock(0,-1,0) then s = s + 1 end end; print(s)", "w4");
        m.tick();
        assertEquals(LuaMachine.State.DONE, m.getState());
        assertEquals("32\n", m.drainOutput(), "32 world calls per tick execute, the rest get nil");
    }
}

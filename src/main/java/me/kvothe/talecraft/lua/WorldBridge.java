package me.kvothe.talecraft.lua;

/**
 * Bridge between a LuaMachine and the game world. Implementations must be
 * called from the server tick thread only — LuaMachine guarantees this by
 * executing world calls inside {@code tick()} (the Lua code runs on the
 * machine's coroutine thread and merely requests the call via a yield;
 * the tick thread performs it and resumes with the result, the CC:T
 * "main thread callback" pattern).
 */
public interface WorldBridge {

    /** Block id at the absolute position, or null for air/unloaded chunks. */
    String getBlockId(int x, int y, int z);

    /** Epoch seconds (real time; world daytime is a later milestone). */
    double getTime();

    /** Broadcasts a chat message to the world the computer is in. */
    void chat(String message);

    /** Origin (the computer's position) for relative-coordinate reads. */
    default int getOriginX() {
        return 0;
    }

    default int getOriginY() {
        return 0;
    }

    default int getOriginZ() {
        return 0;
    }

    /** Drawable canvas of the Monitor block at the computer position plus the
     * given offset; (0,0,0) means "search the six face neighbours". Null when
     * there is no monitor there. */
    default me.kvothe.talecraft.computer.MonitorCanvas monitorAt(int dx, int dy, int dz) {
        return null;
    }

    default TurtleBridge turtle() {
        return null;
    }

    default me.kvothe.talecraft.peripheral.PeripheralProvider peripherals() {
        return me.kvothe.talecraft.peripheral.PeripheralProvider.NONE;
    }

    /** No-world bridge: reads return nil, chat is dropped. */
    WorldBridge NONE = new WorldBridge() {
        @Override
        public String getBlockId(int x, int y, int z) {
            return null;
        }

        @Override
        public double getTime() {
            return System.currentTimeMillis() / 1000.0;
        }

        @Override
        public void chat(String message) {
            // nowhere to send
        }
    };
}

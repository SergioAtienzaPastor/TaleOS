package me.kvothe.taleos.peripheral.modem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import com.hypixel.hytale.protocol.BlockPosition;

import me.kvothe.taleos.computer.Computer;
import me.kvothe.taleos.lua.WorldBridge;

class ModemPeripheralTest {
    @TempDir
    Path dir;

    @Test
    void turtleModemFollowsPositionAndEquipmentWhileKeepingChannels() throws Exception {
        Computer computer = new Computer(41, dir.resolve("computer"), WorldBridge.NONE);
        computer.boot();
        AtomicReference<BlockPosition> position = new AtomicReference<>(new BlockPosition(0, 64, 0));
        AtomicBoolean equipped = new AtomicBoolean(true);
        WirelessNetwork network = new WirelessNetwork(20, 32);
        ModemPeripheral modem = new ModemPeripheral(computer, "world", position::get, "left",
                equipped::get, network);
        CountingNode receiver = new CountingNode(42, "world", new BlockPosition(5, 64, 0));
        network.attach(receiver);
        network.open(receiver, 7);

        modem.call("open", LuaValue.valueOf(7));
        assertTrue(modem.call("isOpen", LuaValue.valueOf(7)).arg1().toboolean());
        modem.call("transmit", LuaValue.varargsOf(new LuaValue[] {
                LuaValue.valueOf(7), LuaValue.valueOf(8), LuaValue.valueOf("hola") }));
        assertEquals(1, receiver.messages);
        assertEquals(5, receiver.distance, 0.001);

        position.set(new BlockPosition(3, 64, 0));
        modem.call("transmit", LuaValue.varargsOf(new LuaValue[] {
                LuaValue.valueOf(7), LuaValue.valueOf(8), LuaValue.valueOf("movido") }));
        assertEquals(2, receiver.messages);
        assertEquals(2, receiver.distance, 0.001);

        equipped.set(false);
        assertThrows(LuaError.class, () -> modem.call("transmit", LuaValue.varargsOf(new LuaValue[] {
                LuaValue.valueOf(7), LuaValue.valueOf(8), LuaValue.TRUE })));
        equipped.set(true);
        assertTrue(modem.call("isOpen", LuaValue.valueOf(7)).arg1().toboolean());
        modem.call("transmit", LuaValue.varargsOf(new LuaValue[] {
                LuaValue.valueOf(7), LuaValue.valueOf(8), LuaValue.TRUE }));
        assertEquals(3, receiver.messages);
        computer.close();
    }

    private static final class CountingNode implements WirelessNetwork.Node {
        private final int owner;
        private final String world;
        private final BlockPosition position;
        private int messages;
        private double distance;

        private CountingNode(int owner, String world, BlockPosition position) {
            this.owner = owner;
            this.world = world;
            this.position = position;
        }

        @Override public int ownerId() { return owner; }
        @Override public String worldName() { return world; }
        @Override public String side() { return "right"; }
        @Override public int x() { return position.x; }
        @Override public int y() { return position.y; }
        @Override public int z() { return position.z; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isLoaded() { return true; }

        @Override
        public boolean deliver(WirelessNetwork.ModemMessage message) {
            messages++;
            distance = message.distance();
            return true;
        }
    }
}

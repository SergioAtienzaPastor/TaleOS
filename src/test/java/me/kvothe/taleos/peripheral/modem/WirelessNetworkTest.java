package me.kvothe.taleos.peripheral.modem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

class WirelessNetworkTest {
    private static final class FakeNode implements WirelessNetwork.Node {
        private final int owner;
        private final String world;
        private final String side;
        private final int x;
        private final int y;
        private final int z;
        private boolean alive = true;
        private boolean loaded = true;
        private int queueCapacity = Integer.MAX_VALUE;
        private final List<WirelessNetwork.ModemMessage> messages = new ArrayList<>();

        private FakeNode(int owner, String world, String side, int x, int y, int z) {
            this.owner = owner;
            this.world = world;
            this.side = side;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int ownerId() {
            return owner;
        }

        @Override
        public String worldName() {
            return world;
        }

        @Override
        public String side() {
            return side;
        }

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public int z() {
            return z;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public boolean isLoaded() {
            return loaded;
        }

        @Override
        public boolean deliver(WirelessNetwork.ModemMessage message) {
            if (messages.size() >= queueCapacity) {
                return false;
            }
            messages.add(message);
            return true;
        }
    }

    private static FakeNode node(int owner, String world, int x) {
        return new FakeNode(owner, world, "left", x, 0, 0);
    }

    @Test
    void channelsValidateOpenCloseAndThe128ChannelLimit() {
        WirelessNetwork network = new WirelessNetwork();
        FakeNode modem = node(1, "overworld", 0);
        network.attach(modem);
        network.open(modem, 0);
        network.open(modem, 65_535);
        assertTrue(network.isOpen(modem, 0));
        assertTrue(network.isOpen(modem, 65_535));
        network.close(modem, 0);
        assertFalse(network.isOpen(modem, 0));
        network.closeAll(modem);
        assertFalse(network.isOpen(modem, 65_535));
        for (int channel = 0; channel < 128; channel++) {
            network.open(modem, channel);
        }
        assertEquals(128, network.openChannelCount(modem));
        assertThrows(LuaError.class, () -> network.open(modem, 128));
        assertThrows(LuaError.class, () -> network.open(modem, -1));
        assertThrows(LuaError.class, () -> network.open(modem, 65_536));
    }

    @Test
    void deliversOnlyInRangeAndNeverToTheSameComputer() {
        WirelessNetwork network = new WirelessNetwork();
        FakeNode sender = node(1, "overworld", 0);
        FakeNode receiver = node(2, "overworld", 10);
        FakeNode ownSecondModem = node(1, "overworld", 0);
        FakeNode outside = node(3, "overworld", 65);
        for (FakeNode modem : List.of(sender, receiver, ownSecondModem, outside)) {
            network.attach(modem);
            network.open(modem, 7);
        }

        assertEquals(1, network.transmit(sender, 7, 99, LuaValue.valueOf("hola")));
        assertEquals(1, receiver.messages.size());
        assertTrue(ownSecondModem.messages.isEmpty());
        assertTrue(outside.messages.isEmpty());
        WirelessNetwork.ModemMessage message = receiver.messages.get(0);
        assertEquals("left", message.side());
        assertEquals(7, message.channel());
        assertEquals(99, message.replyChannel());
        assertEquals("hola", message.payload().tojstring());
        assertEquals(10, message.distance());
    }

    @Test
    void requiresSameWorldLiveComputerAndLoadedModem() {
        WirelessNetwork network = new WirelessNetwork();
        FakeNode sender = node(1, "one", 0);
        FakeNode otherWorld = node(2, "two", 0);
        FakeNode unloaded = node(3, "one", 0);
        FakeNode dead = node(4, "one", 0);
        unloaded.loaded = false;
        dead.alive = false;
        for (FakeNode modem : List.of(sender, otherWorld, unloaded, dead)) {
            network.attach(modem);
            network.open(modem, 1);
        }

        assertEquals(0, network.transmit(sender, 1, 1, LuaValue.TRUE));
        unloaded.loaded = true;
        assertEquals(1, network.transmit(sender, 1, 1, LuaValue.TRUE));
        sender.loaded = false;
        assertThrows(LuaError.class, () -> network.transmit(sender, 1, 1, LuaValue.TRUE));
    }

    @Test
    void copiesNestedPayloadsIndependentlyAndRejectsCyclesAndFunctions() {
        WirelessNetwork network = new WirelessNetwork();
        FakeNode sender = node(1, "overworld", 0);
        FakeNode first = node(2, "overworld", 0);
        FakeNode second = node(3, "overworld", 0);
        for (FakeNode modem : List.of(sender, first, second)) {
            network.attach(modem);
            network.open(modem, 4);
        }
        LuaTable nested = new LuaTable();
        nested.set("answer", 42);
        LuaTable payload = new LuaTable();
        payload.set("nested", nested);
        network.transmit(sender, 4, 4, payload);
        nested.set("answer", 99);

        LuaTable firstPayload = first.messages.get(0).payload().checktable();
        LuaTable secondPayload = second.messages.get(0).payload().checktable();
        assertNotSame(firstPayload, secondPayload);
        assertNotSame(firstPayload.get("nested"), secondPayload.get("nested"));
        assertEquals(42, firstPayload.get("nested").get("answer").checkint());
        firstPayload.get("nested").set("answer", 7);
        assertEquals(42, secondPayload.get("nested").get("answer").checkint());

        LuaTable cycle = new LuaTable();
        cycle.set("self", cycle);
        assertThrows(LuaError.class, () -> network.transmit(sender, 4, 4, cycle));
        assertThrows(LuaError.class, () -> network.transmit(sender, 4, 4,
                new org.luaj.vm2.lib.ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        return LuaValue.NIL;
                    }
                }));
    }

    @Test
    void enforcesMessageRateQueueCapacityAndPayloadDepth() {
        WirelessNetwork network = new WirelessNetwork(64, 2);
        FakeNode sender = node(1, "overworld", 0);
        FakeNode receiver = node(2, "overworld", 0);
        receiver.queueCapacity = 0;
        network.attach(sender);
        network.attach(receiver);
        network.open(receiver, 8);
        assertEquals(0, network.transmit(sender, 8, 8, LuaValue.TRUE));
        network.transmit(sender, 8, 8, LuaValue.TRUE);
        assertThrows(LuaError.class, () -> network.transmit(sender, 8, 8, LuaValue.TRUE));
        network.beginTick("overworld");
        assertEquals(0, network.transmit(sender, 8, 8, LuaValue.TRUE));

        LuaTable deep = new LuaTable();
        LuaTable cursor = deep;
        for (int i = 0; i < 20; i++) {
            LuaTable next = new LuaTable();
            cursor.set("next", next);
            cursor = next;
        }
        assertThrows(LuaError.class, () -> network.transmit(sender, 8, 8, deep));
    }

    @Test
    void channelsSurviveReassociationButNotOwnerShutdown() {
        WirelessNetwork network = new WirelessNetwork();
        FakeNode original = node(9, "overworld", 0);
        network.attach(original);
        network.open(original, 12);
        FakeNode moved = new FakeNode(9, "overworld", "left", 5, 0, 0);
        network.attach(moved);
        assertTrue(network.isOpen(moved, 12));
        network.removeOwner(9);
        assertFalse(network.isOpen(moved, 12));
        assertThrows(LuaError.class, () -> network.transmit(moved, 12, 12, LuaValue.TRUE));
    }
}

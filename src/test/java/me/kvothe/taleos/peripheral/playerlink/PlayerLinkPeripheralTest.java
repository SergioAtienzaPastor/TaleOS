package me.kvothe.taleos.peripheral.playerlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import me.kvothe.taleos.peripheral.playerlink.PlayerLinkItemMetadata.Configuration;

class PlayerLinkPeripheralTest {
    private static final class FakeBackend implements PlayerLinkPeripheral.Backend {
        private Configuration configuration = new Configuration(UUID.randomUUID(), "PlayerOne",
                java.util.Map.of(), null, false);
        private String unavailable;
        private PlayerLinkTransfer.Result transfer = PlayerLinkTransfer.Result.success(3);
        private PlayerLinkPeripheral.SlotResult slot =
                PlayerLinkPeripheral.SlotResult.success("Stone", 4, 64);
        private int source;
        private int destination;
        private int count;

        @Override
        public Configuration configuration() {
            return configuration;
        }

        @Override
        public boolean saveConfiguration(Configuration configuration) {
            this.configuration = configuration;
            return true;
        }

        @Override
        public String unavailableReason() {
            return unavailable;
        }

        @Override
        public PlayerLinkTransfer.Result transfer(int turtleSlot, int playerSlot, int count) {
            source = turtleSlot;
            destination = playerSlot;
            this.count = count;
            return transfer;
        }

        @Override
        public PlayerLinkPeripheral.SlotResult playerSlot(int playerSlot) {
            destination = playerSlot;
            return slot;
        }
    }

    private final FakeBackend backend = new FakeBackend();
    private final PlayerLinkPeripheral peripheral = new PlayerLinkPeripheral(backend);

    @Test
    void ownerIsReadableButCannotBeChangedFromLua() {
        assertEquals("PlayerOne", call("getOwner").arg1().tojstring());
        assertFalse(peripheral.getMethods().contains("setOwner"));
        assertFalse(peripheral.getMethods().contains("bind"));
        assertFalse(peripheral.getMethods().contains("unbind"));
        UUID original = backend.configuration.ownerId();
        assertTrue(call("setOwner", LuaValue.valueOf("AnotherPlayer")).arg1().isnil());
        assertEquals(original, backend.configuration.ownerId());
    }

    @Test
    void routesDefaultSlotAndAutomaticModeArePersistedByTheBackend() {
        assertTrue(call("setRoute", LuaValue.valueOf(1), LuaValue.valueOf(9)).arg1().toboolean());
        assertTrue(call("setRoute", LuaValue.valueOf(2), LuaValue.valueOf(10)).arg1().toboolean());
        assertTrue(call("setDefaultSlot", LuaValue.valueOf(11)).arg1().toboolean());
        assertTrue(call("setAutoTransfer", LuaValue.TRUE).arg1().toboolean());
        assertEquals(9, backend.configuration.routes().get(1));
        assertEquals(11, backend.configuration.defaultSlot());
        assertTrue(backend.configuration.autoTransfer());

        LuaTable routes = call("getRoutes").arg1().checktable();
        assertEquals(9, routes.get(1).toint());
        assertEquals(10, routes.get(2).toint());
        assertTrue(call("clearRoute", LuaValue.valueOf(1)).arg1().toboolean());
        assertFalse(backend.configuration.routes().containsKey(1));
        assertTrue(call("setDefaultSlot", LuaValue.NIL).arg1().toboolean());
        assertNull(backend.configuration.defaultSlot());
    }

    @Test
    void invalidSettingsAndNumericStringsAreRejectedWithoutMutation() {
        Configuration before = backend.configuration;
        assertFalse(call("setRoute", LuaValue.valueOf(0), LuaValue.valueOf(9)).arg1().toboolean());
        assertFalse(call("setRoute", LuaValue.valueOf("1"), LuaValue.valueOf(9)).arg1().toboolean());
        assertFalse(call("setDefaultSlot", LuaValue.valueOf(65)).arg1().toboolean());
        assertFalse(call("setAutoTransfer", LuaValue.valueOf("yes")).arg1().toboolean());
        assertEquals(before, backend.configuration);
    }

    @Test
    void unlinkedItemCannotBeConfigured() {
        backend.configuration = Configuration.unlinked();
        assertTrue(call("getOwner").arg1().isnil());
        assertFalse(call("setAutoTransfer", LuaValue.TRUE).arg1().toboolean());
        assertFalse(call("setRoute", LuaValue.valueOf(1), LuaValue.valueOf(9)).arg1().toboolean());
    }

    @Test
    void manualTransferReturnsTheConfirmedQuantity() {
        Varargs result = call("transfer", LuaValue.valueOf(2), LuaValue.valueOf(9),
                LuaValue.valueOf(3));
        assertTrue(result.arg1().toboolean());
        assertEquals(3, result.arg(2).toint());
        assertEquals(2, backend.source);
        assertEquals(9, backend.destination);
        assertEquals(3, backend.count);

        backend.transfer = PlayerLinkTransfer.Result.failure("Destination is full");
        Varargs failed = call("transfer", LuaValue.valueOf(2), LuaValue.valueOf(9));
        assertFalse(failed.arg1().toboolean());
        assertEquals("Destination is full", failed.arg(2).tojstring());
    }

    @Test
    void availabilityAndInspectionReportBackendReasons() {
        assertTrue(call("isAvailable").arg1().toboolean());
        LuaTable detail = call("getPlayerSlot", LuaValue.valueOf(9)).arg1().checktable();
        assertEquals("Stone", detail.get("name").tojstring());
        assertEquals(4, detail.get("count").toint());

        backend.unavailable = "The linked player is offline";
        Varargs unavailable = call("isAvailable");
        assertFalse(unavailable.arg1().toboolean());
        assertEquals("The linked player is offline", unavailable.arg(2).tojstring());
        backend.slot = PlayerLinkPeripheral.SlotResult.failure("The linked player is offline");
        assertTrue(call("getPlayerSlot", LuaValue.valueOf(9)).arg1().isnil());
    }

    private Varargs call(String method, LuaValue... args) {
        return peripheral.call(method, LuaValue.varargsOf(args));
    }
}

package me.kvothe.taleos.peripheral.usb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

class UsbDrivePeripheralTest {
    private static final class Backend implements UsbDrivePeripheral.Backend {
        private Integer id = 12;
        private String label = "Datos";
        private String mount = "usb";

        @Override
        public Integer penDriveId() {
            return id;
        }

        @Override
        public String penDriveLabel() {
            return label;
        }

        @Override
        public void setPenDriveLabel(String value) {
            label = value == null || value.isBlank() ? "PenDrive #" + id : value;
        }

        @Override
        public String mountPath() {
            return id == null ? null : mount;
        }

        @Override
        public boolean eject() {
            boolean present = id != null;
            id = null;
            mount = null;
            return present;
        }
    }

    @Test
    void exposesRequiredMethodsAndCurrentMetadata() {
        Backend backend = new Backend();
        UsbDrivePeripheral peripheral = new UsbDrivePeripheral(backend);

        assertEquals("usb_drive", peripheral.getType());
        assertEquals(6, peripheral.getMethods().size());
        assertTrue(peripheral.call("isPenDrivePresent", LuaValue.NIL).arg1().toboolean());
        assertEquals(12, peripheral.call("getPenDriveID", LuaValue.NIL).arg1().toint());
        assertEquals("Datos", peripheral.call("getPenDriveLabel", LuaValue.NIL).arg1().tojstring());
        assertEquals("usb", peripheral.call("getMountPath", LuaValue.NIL).arg1().tojstring());
        peripheral.call("setPenDriveLabel", LuaValue.valueOf("Nuevo"));
        assertEquals("Nuevo", backend.label);
    }

    @Test
    void stalePeripheralFailsSafelyAfterEject() {
        Backend backend = new Backend();
        UsbDrivePeripheral peripheral = new UsbDrivePeripheral(backend);

        assertTrue(peripheral.call("eject", LuaValue.NIL).arg1().toboolean());
        assertFalse(peripheral.call("isPenDrivePresent", LuaValue.NIL).arg1().toboolean());
        assertTrue(peripheral.call("getPenDriveID", LuaValue.NIL).arg1().isnil());
        assertThrows(LuaError.class, () -> peripheral.call("getPenDriveLabel", LuaValue.NIL));
        assertThrows(LuaError.class, () -> peripheral.call("getMountPath", LuaValue.NIL));
        assertFalse(peripheral.call("eject", LuaValue.NIL).arg1().toboolean());
    }
}

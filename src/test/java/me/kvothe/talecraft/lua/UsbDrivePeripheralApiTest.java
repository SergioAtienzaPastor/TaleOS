package me.kvothe.talecraft.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.talecraft.peripheral.PeripheralProvider;
import me.kvothe.talecraft.peripheral.usb.UsbDrivePeripheral;

class UsbDrivePeripheralApiTest {
    @TempDir
    Path dir;

    private static final class Backend implements UsbDrivePeripheral.Backend {
        private Integer id = 17;
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
            label = value == null ? "PenDrive #" + id : value;
        }

        @Override
        public String mountPath() {
            return mount;
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
    void luaWrapCallsEveryUsbDriveMethod() throws Exception {
        Backend backend = new Backend();
        UsbDrivePeripheral drive = new UsbDrivePeripheral(backend);
        PeripheralProvider provider = new PeripheralProvider() {
            @Override
            public Map<String, me.kvothe.talecraft.peripheral.Peripheral> snapshot() {
                return Map.of("left", drive);
            }

            @Override
            public me.kvothe.talecraft.peripheral.Peripheral find(String name) {
                return name.equals("left") ? drive : null;
            }
        };
        LuaMachine machine = RomTestSupport.boot(dir.resolve("computer"), """
                local drive = peripheral.wrap('left')
                print(peripheral.getType('left'))
                print(table.concat(peripheral.getMethods('left'), ','))
                print(drive.isPenDrivePresent(), drive.getPenDriveID(), drive.getPenDriveLabel())
                print(drive.setPenDriveLabel('Respaldo'), drive.getPenDriveLabel(), drive.getMountPath())
                print(drive.eject(), drive.isPenDrivePresent(), drive.getPenDriveID())
                print(drive.eject())
                """);
        machine.setWorldBridge(new WorldBridge() {
            @Override
            public String getBlockId(int x, int y, int z) {
                return null;
            }

            @Override
            public double getTime() {
                return 0;
            }

            @Override
            public void chat(String message) {
            }

            @Override
            public PeripheralProvider peripherals() {
                return provider;
            }
        });

        machine.tick();

        String output = machine.drainOutput();
        assertTrue(output.contains("usb_drive\n"), output);
        assertTrue(output.contains("isPenDrivePresent,getPenDriveID,getPenDriveLabel,"
                + "setPenDriveLabel,getMountPath,eject\n"), output);
        assertTrue(output.contains("true\t17\tDatos\n"), output);
        assertTrue(output.contains("true\tRespaldo\tusb\n"), output);
        assertTrue(output.contains("true\tfalse\tnil\n"), output);
        assertTrue(output.endsWith("false\n"), output);
        assertEquals(null, backend.id);
    }
}

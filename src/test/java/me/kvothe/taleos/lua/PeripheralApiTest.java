package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import me.kvothe.taleos.computer.MonitorCanvas;
import me.kvothe.taleos.peripheral.Peripheral;
import me.kvothe.taleos.peripheral.PeripheralProvider;
import me.kvothe.taleos.peripheral.monitor.MonitorPeripheral;

class PeripheralApiTest {
    @TempDir
    Path dir;

    private static final class FakeProvider implements PeripheralProvider {
        private final Map<String, Peripheral> peripherals = new LinkedHashMap<>();

        @Override
        public Map<String, Peripheral> snapshot() {
            return new LinkedHashMap<>(peripherals);
        }

        @Override
        public Peripheral find(String name) {
            return peripherals.get(name);
        }
    }

    private static final class FakePeripheral implements Peripheral {
        @Override
        public String getType() {
            return "fake";
        }

        @Override
        public List<String> getMethods() {
            return List.of("echo", "sum");
        }

        @Override
        public Varargs call(String method, Varargs args) {
            return switch (method) {
                case "echo" -> LuaValue.valueOf(args.arg1().checkjstring());
                case "sum" -> LuaValue.valueOf(args.arg1().checkint() + args.arg(2).checkint());
                default -> LuaValue.NIL;
            };
        }
    }

    private static WorldBridge bridge(PeripheralProvider provider) {
        return new WorldBridge() {
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
        };
    }

    @Test
    void discoversWrapsFindsAndCallsWithoutJavaObjects() throws Exception {
        FakeProvider provider = new FakeProvider();
        provider.peripherals.put("left", new FakePeripheral());
        LuaMachine machine = RomTestSupport.boot(dir.resolve("api"), """
                local names = peripheral.getNames()
                print(#names, names[1], peripheral.getType('left'))
                print(table.concat(peripheral.getMethods('left'), ','))
                local wrapped = peripheral.wrap('left')
                print(type(wrapped), wrapped.echo('hola'), wrapped.sum(2, 3))
                print(peripheral.call('left', 'echo', 'direct'))
                local found, missing = peripheral.find('fake')
                print(found == wrapped, missing == nil, peripheral.wrap('right') == nil)
                print(type(wrapped.echo), tostring(wrapped):find('userdata') == nil)
                """);
        machine.setWorldBridge(bridge(provider));

        machine.tick();

        String output = machine.drainOutput();
        assertTrue(output.contains("1\tleft\tfake\n"), output);
        assertTrue(output.contains("echo,sum\n"), output);
        assertTrue(output.contains("table\thola\t5\n"), output);
        assertTrue(output.contains("direct\n"), output);
        assertTrue(output.contains("true\ttrue\ttrue\n"), output);
        assertTrue(output.contains("function\ttrue\n"), output);
    }

    @Test
    void detachQueuesEventAndInvalidatesExistingHandle() throws Exception {
        FakeProvider provider = new FakeProvider();
        provider.peripherals.put("left", new FakePeripheral());
        LuaMachine machine = RomTestSupport.boot(dir.resolve("detach"), """
                local wrapped = peripheral.wrap('left')
                print('ready', wrapped.echo('ok'))
                local event, name = os.pullEvent()
                print(event, name, peripheral.getType(name))
                local ok = pcall(function() wrapped.echo('stale') end)
                print('stale call', ok)
                """);
        machine.setWorldBridge(bridge(provider));
        machine.tick();
        assertTrue(machine.drainOutput().contains("ready\tok"));

        provider.peripherals.clear();
        machine.tick();

        String output = machine.drainOutput();
        assertTrue(output.contains("peripheral_detach\tleft\tnil"), output);
        assertTrue(output.contains("stale call\tfalse"), output);
    }

    @Test
    void attachQueuesEventAndNameCanBeWrappedAgain() throws Exception {
        FakeProvider provider = new FakeProvider();
        LuaMachine machine = RomTestSupport.boot(dir.resolve("attach"), """
                local event, name = os.pullEvent()
                print(event, name, peripheral.getType(name), peripheral.wrap(name).echo('new'))
                """);
        machine.setWorldBridge(bridge(provider));
        machine.tick();
        machine.drainOutput();

        provider.peripherals.put("back", new FakePeripheral());
        machine.tick();

        assertTrue(machine.drainOutput().contains("peripheral\tback\tfake\tnew"));
    }

    @Test
    void taleMonitorKeepsLegacyNeighbourPriority() throws Exception {
        FakeProvider provider = new FakeProvider();
        MonitorCanvas left = new MonitorCanvas();
        MonitorCanvas right = new MonitorCanvas();
        left.setPixel(0, 0, MonitorCanvas.colorIndex("blue"));
        right.setPixel(0, 0, MonitorCanvas.colorIndex("red"));
        provider.peripherals.put("left", new MonitorPeripheral(left));
        provider.peripherals.put("right", new MonitorPeripheral(right));
        LuaMachine machine = RomTestSupport.boot(dir.resolve("monitor-priority"),
                "print(tale.monitor().getPixel(1, 1))");
        machine.setWorldBridge(bridge(provider));

        machine.tick();

        assertTrue(machine.drainOutput().contains("red\n"));
    }

    @Test
    void monitorWrapSharesLegacyAndRedirectContract() throws Exception {
        FakeProvider provider = new FakeProvider();
        MonitorCanvas canvas = new MonitorCanvas();
        provider.peripherals.put("left", new MonitorPeripheral(canvas));
        LuaMachine machine = RomTestSupport.boot(dir.resolve("monitor"), """
                local monitor = peripheral.wrap('left')
                assert(monitor == tale.monitor())
                local w, h = monitor.getSize()
                local tw, th = monitor.getTerminalSize()
                print(w, h, tw, th)
                monitor.setPixel(40, 24, 'red')
                print(monitor.getPixel(40, 24))
                local previous = term.redirect(monitor)
                term.write('Z')
                term.redirect(previous)
                print(monitor.__getCell(1, 1))
                """);
        machine.setWorldBridge(bridge(provider));

        machine.tick();

        String output = machine.drainOutput();
        assertTrue(output.contains("40\t24\t10\t4"), output);
        assertTrue(output.contains("red\n"), output);
        assertTrue(output.contains("Z\t"), output);
        assertEquals(0, canvas.getPixel(39, 23));
        assertNotNull(provider.find("left"));
    }
}

package me.kvothe.talecraft.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import me.kvothe.talecraft.lua.LuaModemTestSupport.Modem;
import me.kvothe.talecraft.lua.LuaModemTestSupport.Provider;

class GpsApiTest {
    @TempDir
    Path dir;

    @Test
    void trilateratesExact3dReferencesAndRejectsCoplanarGeometry() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("math"), """
                local target = { x = 3, y = -2, z = 5 }
                local positions = {
                  { x = 0, y = 0, z = 0 }, { x = 10, y = 0, z = 0 },
                  { x = 0, y = 10, z = 0 }, { x = 0, y = 0, z = 10 },
                }
                local references = {}
                for i, p in ipairs(positions) do
                  references[i] = { x = p.x, y = p.y, z = p.z,
                    distance = math.sqrt((p.x-target.x)^2 + (p.y-target.y)^2 + (p.z-target.z)^2) }
                end
                local x, y, z = gps.trilaterate(references)
                print('position', math.abs(x-target.x)<1e-9, math.abs(y-target.y)<1e-9,
                  math.abs(z-target.z)<1e-9)
                references[4] = { x = 5, y = 5, z = 0, distance = 1 }
                print('coplanar', gps.trilaterate(references) == nil)
                """);
        machine.tick();

        String output = machine.drainOutput();
        assertTrue(output.contains("position\ttrue\ttrue\ttrue"), output);
        assertTrue(output.contains("coplanar\ttrue"), output);
    }

    @Test
    void locateUsesModemDistancesAndReturnsNilOnTimeout() throws Exception {
        Provider provider = new Provider();
        Modem modem = new Modem();
        provider.values.put("left", modem);
        LuaMachine machine = RomTestSupport.boot(dir.resolve("locate"), """
                local x, y, z = gps.locate(0.2)
                print('located', x, y, z)
                local missing, reason = gps.locate(0.05)
                print('missing', missing == nil, type(reason))
                """);
        machine.setComputerId(12);
        machine.setWorldBridge(LuaModemTestSupport.bridge(provider));
        machine.tick();
        assertEquals(1, modem.transmissions.size());
        double request = modem.transmissions.get(0).payload().get("request").checkdouble();
        double[][] positions = { { 0, 0, 0 }, { 10, 0, 0 }, { 0, 10, 0 }, { 0, 0, 10 } };
        for (double[] position : positions) {
            LuaTable response = new LuaTable();
            response.set("gps", "response");
            response.set("request", request);
            response.set("x", position[0]);
            response.set("y", position[1]);
            response.set("z", position[2]);
            double distance = Math.sqrt(Math.pow(position[0] - 3, 2)
                    + Math.pow(position[1] + 2, 2) + Math.pow(position[2] - 5, 2));
            machine.queueEvent("modem_message", "left", 65_534, 65_534, response, distance);
            machine.tick();
        }
        for (int i = 0; i < 3; i++) {
            machine.tick();
        }

        String output = machine.drainOutput();
        assertTrue(output.contains("located\t3.0\t-2\t5"), output);
        assertTrue(output.contains("missing\ttrue\tstring"), output);
        assertEquals(2, modem.transmissions.size());
        assertFalse(modem.channels.contains(65_534));
    }
}

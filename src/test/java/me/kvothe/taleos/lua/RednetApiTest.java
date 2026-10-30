package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import me.kvothe.taleos.lua.LuaModemTestSupport.Modem;
import me.kvothe.taleos.lua.LuaModemTestSupport.Provider;

class RednetApiTest {
    @TempDir
    Path dir;

    @Test
    void sendReceiveFiltersProtocolAndHonorsTimeout() throws Exception {
        Provider provider = new Provider();
        Modem modem = new Modem();
        provider.values.put("left", modem);
        LuaMachine machine = RomTestSupport.boot(dir.resolve("protocol"), """
                assert(rednet.open('left'))
                print('sent', rednet.send(7, { value = 'hola' }, 'chat'))
                local sender, message, protocol = rednet.receive('chat', 0.2)
                print(sender, message.value, protocol)
                print('timeout', rednet.receive('chat', 0.05) == nil)
                """);
        machine.setComputerId(5);
        machine.setWorldBridge(LuaModemTestSupport.bridge(provider));
        machine.tick();
        assertEquals(1, modem.transmissions.size());
        assertEquals(7, modem.transmissions.get(0).channel());
        assertEquals(5, modem.transmissions.get(0).replyChannel());

        machine.queueEvent("modem_message", "left", 7, 42,
                LuaModemTestSupport.rednetMessage(LuaValue.valueOf("ignored"), "other"), 1.0);
        machine.tick();
        LuaTable message = new LuaTable();
        message.set("value", "hola");
        machine.queueEvent("modem_message", "left", 7, 42,
                LuaModemTestSupport.rednetMessage(message, "chat"), 1.0);
        machine.tick();
        for (int i = 0; i < 3; i++) {
            machine.tick();
        }

        String output = machine.drainOutput();
        assertTrue(output.contains("sent\ttrue"), output);
        assertTrue(output.contains("42\thola\tchat"), output);
        assertTrue(output.contains("timeout\ttrue"), output);
    }

    @Test
    void dnsLookupRunsThroughTheParallelDaemon() throws Exception {
        Provider provider = new Provider();
        Modem modem = new Modem();
        provider.values.put("left", modem);
        LuaMachine machine = RomTestSupport.boot(dir.resolve("dns"), """
                assert(rednet.open('left'))
                rednet.host('warp', 'base')
                parallel.waitForAny(rednet.run, function()
                  print('lookup', rednet.lookup('warp', 'base', 0.2))
                end)
                """);
        machine.setComputerId(5);
        machine.setWorldBridge(LuaModemTestSupport.bridge(provider));
        machine.tick();
        assertEquals(65_535, modem.transmissions.get(0).channel());
        LuaValue lookup = modem.transmissions.get(0).payload();

        machine.queueEvent("modem_message", "left", 65_535, 9, lookup, 2.0);
        machine.tick();
        assertEquals(2, modem.transmissions.size());
        assertEquals(9, modem.transmissions.get(1).channel());
        LuaValue response = modem.transmissions.get(1).payload();
        machine.queueEvent("modem_message", "left", 5, 9, response, 2.0);
        machine.tick();

        assertTrue(machine.drainOutput().contains("lookup\t9"), "DNS daemon did not answer");
        assertTrue(machine.isAlive());
        assertEquals("tale> ", machine.getPrompt());
    }

    @Test
    void dnsLookupWithoutHostnameCollectsEveryHost() throws Exception {
        Provider provider = new Provider();
        Modem modem = new Modem();
        provider.values.put("left", modem);
        LuaMachine machine = RomTestSupport.boot(dir.resolve("dns-all"), """
                assert(rednet.open('left'))
                rednet.host('warp', 'base')
                parallel.waitForAny(rednet.run, function()
                  local found = rednet.lookup('warp', nil, 0.2)
                  print('all', #found, found[1])
                end)
                """);
        machine.setComputerId(5);
        machine.setWorldBridge(LuaModemTestSupport.bridge(provider));
        machine.tick();
        LuaValue lookup = modem.transmissions.get(0).payload();
        machine.queueEvent("modem_message", "left", 65_535, 9, lookup, 2.0);
        machine.tick();
        LuaValue response = modem.transmissions.get(1).payload();
        machine.queueEvent("modem_message", "left", 5, 9, response, 2.0);
        machine.tick();
        for (int i = 0; i < 6; i++) {
            machine.tick();
        }

        String output = machine.drainOutput();
        assertTrue(output.contains("all\t1\t9"), output);
    }

    @Test
    void shellProgramUsesRednetWhileDnsDaemonStaysActive() throws Exception {
        Provider provider = new Provider();
        provider.values.put("left", new Modem());
        Path root = dir.resolve("shell");
        Files.createDirectories(root);
        Files.writeString(root.resolve("ping.lua"), """
                assert(rednet.open('left'))
                local sender, message = rednet.receive('ping', 0.2)
                print('program', sender, message)
                """);
        LuaMachine machine = RomTestSupport.boot(root, null);
        machine.setComputerId(5);
        machine.setWorldBridge(LuaModemTestSupport.bridge(provider));
        machine.tick();
        machine.drainOutput();
        machine.queueEvent("stdin", "ping");
        machine.tick();
        machine.queueEvent("modem_message", "left", 5, 33,
                LuaModemTestSupport.rednetMessage(LuaValue.valueOf("pong"), "ping"), 1.0);
        machine.tick();

        assertTrue(machine.drainOutput().contains("program\t33\tpong"));
        assertTrue(machine.isAlive());
        assertEquals("tale> ", machine.getPrompt());
    }
}

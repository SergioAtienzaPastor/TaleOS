package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.taleos.peripheral.Peripheral;
import me.kvothe.taleos.peripheral.PeripheralProvider;
import me.kvothe.taleos.peripheral.playerlink.PlayerLinkItemMetadata.Configuration;
import me.kvothe.taleos.peripheral.playerlink.PlayerLinkPeripheral;
import me.kvothe.taleos.peripheral.playerlink.PlayerLinkTransfer;

class PlayerLinkApiTest {
    @TempDir
    Path dir;

    @Test
    void playerLinkWorksThroughGenericLuaPeripheralApi() throws Exception {
        PlayerLinkPeripheral link = new PlayerLinkPeripheral(new PlayerLinkPeripheral.Backend() {
            private Configuration configuration = new Configuration(UUID.randomUUID(),
                    "PlayerOne", Map.of(), null, false);

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
                return null;
            }

            @Override
            public PlayerLinkTransfer.Result transfer(int turtleSlot, int playerSlot, int count) {
                return PlayerLinkTransfer.Result.success(Math.min(3, count));
            }

            @Override
            public PlayerLinkPeripheral.SlotResult playerSlot(int playerSlot) {
                return PlayerLinkPeripheral.SlotResult.success("Stone", 4, 64);
            }
        });
        PeripheralProvider provider = new PeripheralProvider() {
            @Override
            public Map<String, Peripheral> snapshot() {
                return Map.of("internal1", link);
            }

            @Override
            public Peripheral find(String name) {
                return "internal1".equals(name) ? link : null;
            }
        };
        LuaMachine machine = RomTestSupport.boot(dir.resolve("player-link"), """
                local link = peripheral.wrap('internal1')
                print(peripheral.getType('internal1'))
                print(link.getOwner())
                print(link.setRoute(1, 9))
                print(link.setAutoTransfer(true))
                print(link.transfer(1, 9, 3))
                print(link.getPlayerSlot(9).count)
                print(link.setRoute('1', 9))
                print(link.setOwner)
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
        assertTrue(output.contains("player_link\n"), output);
        assertTrue(output.contains("PlayerOne\n"), output);
        assertTrue(output.contains("true\t3\n"), output);
        assertTrue(output.contains("4\n"), output);
        assertTrue(output.contains("false\tUsage: setRoute"), output);
        assertTrue(output.contains("nil\n"), output);
    }
}

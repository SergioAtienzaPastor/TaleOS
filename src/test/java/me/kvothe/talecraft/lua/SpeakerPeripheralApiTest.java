package me.kvothe.talecraft.lua;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.talecraft.peripheral.Peripheral;
import me.kvothe.talecraft.peripheral.PeripheralProvider;
import me.kvothe.talecraft.peripheral.speaker.SpeakerPeripheral;
import me.kvothe.talecraft.peripheral.speaker.SpeakerRateLimiter;

class SpeakerPeripheralApiTest {
    @TempDir
    Path dir;

    @Test
    void speakerWorksThroughGenericPeripheralApiWithoutAudio() throws Exception {
        SpeakerPeripheral speaker = new SpeakerPeripheral(new SpeakerRateLimiter(),
                new SpeakerPeripheral.SoundBackend() {
                    @Override
                    public String resolve(String name) {
                        return "TaleCraft_Boot_Beep".equals(name) ? name : null;
                    }

                    @Override
                    public void play(String soundEvent, float volume, float pitch) {
                    }
                });
        PeripheralProvider provider = new PeripheralProvider() {
            @Override
            public Map<String, Peripheral> snapshot() {
                return Map.of("left", speaker);
            }

            @Override
            public Peripheral find(String name) {
                return "left".equals(name) ? speaker : null;
            }
        };
        LuaMachine machine = RomTestSupport.boot(dir.resolve("speaker"), """
                print(peripheral.getType('left'))
                print(table.concat(peripheral.getMethods('left'), ','))
                local speaker = peripheral.wrap('left')
                print(speaker.playSound('TaleCraft_Boot_Beep', 1, 1.5))
                print(speaker.playSound('TaleCraft_Boot_Beep'))
                print(speaker.playSound('missing'))
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
        assertTrue(output.contains("speaker\n"), output);
        assertTrue(output.contains("playSound\n"), output);
        assertTrue(output.contains("true\n"), output);
        assertTrue(output.contains("false\tlímite de un sonido por equipo y tick\n"), output);
        assertTrue(output.contains("false\tSoundEvent no registrado: missing\n"), output);
    }
}

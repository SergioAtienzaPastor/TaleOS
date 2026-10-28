package me.kvothe.talecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModemConfigTest {
    @TempDir
    Path dir;

    @Test
    void readsBoundedRangeAndMessageLimit() throws Exception {
        Path config = dir.resolve("config.json");
        Files.writeString(config, """
                {"modem":{"range":24,"max_messages_per_tick":7}}
                """);

        assertEquals(new ModemConfig.Values(24, 7), ModemConfig.load(config));
    }

    @Test
    void invalidOrMissingValuesFallBackToSafeDefaults() throws Exception {
        Path config = dir.resolve("config.json");
        Files.writeString(config, """
                {"modem":{"range":99999,"max_messages_per_tick":7}}
                """);
        assertEquals(ModemConfig.defaults(), ModemConfig.load(config));
        Files.writeString(config, "{}");
        assertEquals(ModemConfig.defaults(), ModemConfig.load(config));
    }
}

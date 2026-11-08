package me.kvothe.taleos.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerLinkConfigTest {
    @TempDir
    Path directory;

    @Test
    void readsEnabledDistanceAndTransferBudget() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, """
                {"player_link":{"enabled":false,"max_distance":32,"transfers_per_tick":4}}
                """);
        assertEquals(new PlayerLinkConfig.Values(false, 32, 4), PlayerLinkConfig.load(file));
    }

    @Test
    void missingOrInvalidConfigurationUsesSafeDefaults() throws Exception {
        Path file = directory.resolve("config.json");
        assertEquals(PlayerLinkConfig.defaults(), PlayerLinkConfig.load(file));
        Files.writeString(file, """
                {"player_link":{"enabled":true,"max_distance":0,"transfers_per_tick":999}}
                """);
        assertEquals(PlayerLinkConfig.defaults(), PlayerLinkConfig.load(file));
    }
}

package me.kvothe.talecraft.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import me.kvothe.talecraft.peripheral.modem.WirelessNetwork;

public final class ModemConfig {
    public record Values(int range, int maxMessagesPerTick) {
    }

    private ModemConfig() {
    }

    public static Values defaults() {
        return new Values(WirelessNetwork.DEFAULT_RANGE, WirelessNetwork.DEFAULT_MAX_MESSAGES_PER_TICK);
    }

    public static Values load(Path file) {
        try {
            Object parsed = new HttpConfig.Parser(Files.readString(file)).parse();
            if (!(parsed instanceof Map<?, ?> root) || !(root.get("modem") instanceof Map<?, ?> modem)) {
                return defaults();
            }
            Object range = modem.get("range");
            Object messages = modem.get("max_messages_per_tick");
            if (!(range instanceof Long radius) || !(messages instanceof Long limit)
                    || radius < 1 || radius > 256 || limit < 1 || limit > 256) {
                throw new IllegalArgumentException("modem range must be 1..256 and message limit 1..256");
            }
            return new Values(radius.intValue(), limit.intValue());
        } catch (IOException | RuntimeException exception) {
            System.err.println("[TaleCraft] invalid modem config; using safe defaults: "
                    + exception.getMessage());
            return defaults();
        }
    }
}

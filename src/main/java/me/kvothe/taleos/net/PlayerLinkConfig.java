package me.kvothe.taleos.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PlayerLinkConfig {
    public record Values(boolean enabled, int maxDistance, int transfersPerTick) {
    }

    private PlayerLinkConfig() {
    }

    public static Values defaults() {
        return new Values(true, 64, 8);
    }

    public static Values load(Path file) {
        try {
            Object parsed = new HttpConfig.Parser(Files.readString(file)).parse();
            if (!(parsed instanceof Map<?, ?> root)
                    || !(root.get("player_link") instanceof Map<?, ?> playerLink)) {
                return defaults();
            }
            Object enabled = playerLink.get("enabled");
            Object distance = playerLink.get("max_distance");
            Object transfers = playerLink.get("transfers_per_tick");
            if (!(enabled instanceof Boolean active) || !(distance instanceof Long radius)
                    || !(transfers instanceof Long limit)
                    || radius < 1 || radius > 256 || limit < 1 || limit > 64) {
                throw new IllegalArgumentException(
                        "player_link requires enabled, max_distance 1..256 and transfers_per_tick 1..64");
            }
            return new Values(active, radius.intValue(), limit.intValue());
        } catch (IOException | RuntimeException exception) {
            System.err.println("[TaleOS] invalid Player Link config; using safe defaults: "
                    + exception.getMessage());
            return defaults();
        }
    }
}

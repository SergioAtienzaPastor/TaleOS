package me.kvothe.talecraft.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import me.kvothe.talecraft.I18n;

public final class LanguageConfig {
    private LanguageConfig() {
    }

    public static String load(Path file) {
        try {
            Object parsed = new HttpConfig.Parser(Files.readString(file)).parse();
            if (parsed instanceof Map<?, ?> root && root.get("language") instanceof String value
                    && (value.equalsIgnoreCase("en") || value.equalsIgnoreCase("es"))) {
                return value.toLowerCase(java.util.Locale.ROOT);
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("[TaleCraft] Invalid language config; using English: "
                    + exception.getMessage());
        }
        return I18n.DEFAULT_LANGUAGE;
    }
}

package me.kvothe.talecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguageConfigTest {
    @TempDir
    Path dir;

    @Test
    void missingLanguageUsesEnglish() throws Exception {
        Path config = dir.resolve("config.json");
        assertEquals("en", LanguageConfig.load(config));
        Files.writeString(config, "{}");
        assertEquals("en", LanguageConfig.load(config));
    }

    @Test
    void readsBothSupportedLanguages() throws Exception {
        Path config = dir.resolve("config.json");
        Files.writeString(config, "{\"language\":\"es\"}");
        assertEquals("es", LanguageConfig.load(config));
        Files.writeString(config, "{\"language\":\"en\"}");
        assertEquals("en", LanguageConfig.load(config));
    }

    @Test
    void invalidLanguageUsesEnglishAndDefaultConfigDeclaresIt() throws Exception {
        Path config = dir.resolve("config.json");
        Files.writeString(config, "{\"language\":\"fr\"}");
        assertEquals("en", LanguageConfig.load(config));
        assertTrue(HttpConfig.DEFAULT_JSON.contains("\"language\": \"en\""));
    }
}

package me.kvothe.talecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class I18nTest {
    @AfterEach
    void restoreEnglish() {
        I18n.setLanguage(I18n.DEFAULT_LANGUAGE);
    }

    @Test
    void englishIsTheDefaultAndTranslatesLegacyMessages() {
        I18n.setLanguage("en");
        assertEquals("en", I18n.language());
        assertEquals("Stop", I18n.t("Stop"));
        assertEquals("inventory unavailable", I18n.t("inventario no disponible"));
        assertEquals("Program terminated", I18n.t("Program terminated"));
    }

    @Test
    void spanishCoversInterfacesLuaAndApiErrors() {
        I18n.setLanguage("es");
        assertEquals("Detener", I18n.t("Stop"));
        assertEquals("Completar", I18n.t("Complete"));
        assertEquals("ordenador #", I18n.t("Computer #"));
        assertEquals("Programa detenido", I18n.t("Program terminated"));
        assertEquals("no hay ningún módem inalámbrico conectado",
                I18n.t("no wireless modem attached"));
        assertEquals("inventario no disponible", I18n.t("inventario no disponible"));
    }

    @Test
    void userTextAndInvalidLanguageFallBackSafely() {
        I18n.setLanguage("es");
        assertEquals("Mensaje personalizado", I18n.t("Mensaje personalizado"));
        I18n.setLanguage("invalid");
        assertEquals("en", I18n.language());
        assertEquals("Custom message", I18n.t("Custom message"));
    }
}

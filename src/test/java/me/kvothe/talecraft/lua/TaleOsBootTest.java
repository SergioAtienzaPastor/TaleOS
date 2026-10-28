package me.kvothe.talecraft.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.talecraft.os.VirtualFileSystem;

/** Boots the real TaleOS rom from src/main/resources/lua/rom headless. */
class TaleOsBootTest {

    private static final long BUDGET = 200_000;
    private static final Path ROM_DIR = Path.of("src/main/resources/lua/rom");

    @TempDir
    Path dir;

    private Map<String, String> loadRomOverlay() throws Exception {
        Map<String, String> overlay = new HashMap<>();
        try (var walk = Files.walk(ROM_DIR)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                overlay.put("rom/" + ROM_DIR.relativize(p).toString().replace('\\', '/'),
                        Files.readString(p));
            }
        }
        return overlay;
    }

    private LuaMachine boot(Path computerRoot, Map<String, String> overlay) throws Exception {
        VirtualFileSystem vfs = new VirtualFileSystem(computerRoot, 1_000_000);
        vfs.mountRom(overlay);
        LuaMachine m = new LuaMachine(BUDGET, vfs);
        m.setComputerId(7);
        m.load(overlay.get("rom/bios.lua"), "taleos:bios");
        return m;
    }

    @Test
    void bootsToPromptAndAnswersCommands() throws Exception {
        LuaMachine m = boot(dir.resolve("c7"), loadRomOverlay());
        m.tick();
        String boot = m.drainOutput();
        assertTrue(boot.contains("TaleOS v0.1"), "boot banner, got: " + boot);
        assertEquals("tale> ", m.getPrompt(), "shell prompt");
        assertTrue(m.isAlive(), "shell waits suspended for stdin");

        m.queueEvent("stdin", "id");
        m.tick();
        assertTrue(m.drainOutput().contains("Computer #7"));
        assertTrue(m.isAlive());

        m.queueEvent("stdin", "ls rom");
        m.tick();
        String ls = m.drainOutput();
        assertTrue(ls.contains("shell.lua") && ls.contains("programs/"), "ls rom: " + ls);

        m.queueEvent("stdin", "echo hola taleos");
        m.tick();
        assertTrue(m.drainOutput().contains("hola taleos"));

        m.queueEvent("stdin", "noexiste");
        m.tick();
        assertTrue(m.drainOutput().contains("Unknown command:"));
    }

    @Test
    void cdChangesWorkingDirectoryForCommandsAndPrograms() throws Exception {
        Path root = dir.resolve("cwd");
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/nota.txt"), "contenido relativo");
        Files.writeString(root.resolve("docs/hola.lua"), "print('PROGRAMA_RELATIVO_OK')");
        LuaMachine m = boot(root, loadRomOverlay());
        m.tick();
        m.drainOutput();

        m.queueEvent("stdin", "cd docs");
        m.tick();
        assertEquals("tale:/docs> ", m.getPrompt());
        m.queueEvent("stdin", "ls");
        m.tick();
        String listing = m.drainOutput();
        assertTrue(listing.contains("nota.txt") && listing.contains("hola.lua"), listing);
        m.queueEvent("stdin", "cat nota.txt");
        m.tick();
        assertTrue(m.drainOutput().contains("contenido relativo"));
        m.queueEvent("stdin", "hola");
        m.tick();
        assertTrue(m.drainOutput().contains("PROGRAMA_RELATIVO_OK"));
        m.queueEvent("stdin", "cd ..");
        m.tick();
        assertEquals("tale> ", m.getPrompt());
        m.queueEvent("stdin", "cat docs/nota.txt");
        m.tick();
        assertTrue(m.drainOutput().contains("contenido relativo"));
    }

    @Test
    void fileWrittenViaLuaReplPersistsAcrossRestart() throws Exception {
        Map<String, String> overlay = loadRomOverlay();
        LuaMachine m = boot(dir.resolve("c9"), overlay);
        m.tick();
        m.drainOutput();

        m.queueEvent("stdin", "lua");
        m.tick();
        m.drainOutput();
        m.queueEvent("stdin", "fs.write('notas.txt', 'persisto')");
        m.tick();
        m.queueEvent("stdin", "exit");
        m.tick();
        assertTrue(m.isAlive(), "back at shell after leaving the repl");

        // "Server restart": a brand-new machine over the same computer root.
        LuaMachine restarted = boot(dir.resolve("c9"), overlay);
        restarted.tick();
        restarted.queueEvent("stdin", "cat notas.txt");
        restarted.tick();
        assertTrue(restarted.drainOutput().contains("persisto"),
                "file must survive a machine restart");
    }

    @Test
    void rebootHaltsTheMachineCleanly() throws Exception {
        LuaMachine m = boot(dir.resolve("c10"), loadRomOverlay());
        m.tick();
        m.drainOutput();
        m.queueEvent("stdin", "reboot");
        m.tick();
        assertEquals(LuaMachine.State.DONE, m.getState());
        assertTrue(m.isRebootRequested());
    }

    @Test
    void romIsReadOnlyFromShell() throws Exception {
        LuaMachine m = boot(dir.resolve("c11"), loadRomOverlay());
        m.tick();
        m.drainOutput();
        m.queueEvent("stdin", "rm rom/shell.lua");
        m.tick();
        String out = m.drainOutput();
        assertTrue(out.contains("read-only"), "rom must be read-only, got: " + out);
        m.queueEvent("stdin", "ls rom");
        m.tick();
        assertTrue(m.drainOutput().contains("shell.lua"), "rom/shell.lua must still be there");
    }

    @Test
    void spanishLocalizesShellWithoutChangingUserOutput() throws Exception {
        String previous = me.kvothe.talecraft.I18n.language();
        try {
            me.kvothe.talecraft.I18n.setLanguage("es");
            LuaMachine m = boot(dir.resolve("spanish"), loadRomOverlay());
            m.tick();
            m.drainOutput();
            m.queueEvent("stdin", "id");
            m.tick();
            assertTrue(m.drainOutput().contains("ordenador #7"));
            m.queueEvent("stdin", "noexiste");
            m.tick();
            assertTrue(m.drainOutput().contains("comando desconocido:"));
            m.queueEvent("stdin", "echo Mensaje personalizado");
            m.tick();
            assertTrue(m.drainOutput().contains("Mensaje personalizado"));
        } finally {
            me.kvothe.talecraft.I18n.setLanguage(previous);
        }
    }
}

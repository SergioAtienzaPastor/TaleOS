package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.taleos.computer.MonitorCanvas;
import me.kvothe.taleos.computer.TerminalSurface;

class TerminalApiTest {

    @TempDir
    Path dir;

    private static String row(TerminalSurface surface, int y) {
        StringBuilder row = new StringBuilder(surface.getWidth());
        for (int x = 0; x < surface.getWidth(); x++) {
            row.append(surface.getChar(x, y));
        }
        return row.toString();
    }

    @Test
    void nativeTermSupportsSpatialOperationsAndColors() {
        LuaMachine machine = new LuaMachine(100_000);
        machine.load("term.clear()\n"
                + "term.setCursorPos(2, 2)\n"
                + "term.setTextColor(colors and colors.red or 256)\n"
                + "term.setBackgroundColor(1)\n"
                + "term.blit('ab', '8f', '01')\n"
                + "term.setCursorBlink(true)\n"
                + "print(term.getCursorPos())\n"
                + "print(term.getTextColor(), term.getBackgroundColor(), term.isColor())\n", "term");
        machine.tick();

        TerminalSurface surface = machine.getNativeSurface();
        assertEquals('a', surface.getChar(1, 1));
        assertEquals(8, surface.getCellTextColor(1, 1));
        assertEquals(0, surface.getCellBackgroundColor(1, 1));
        assertEquals(15, surface.getCellTextColor(2, 1));
        assertEquals(1, surface.getCellBackgroundColor(2, 1));
        assertTrue(surface.getCursorBlink());
        assertEquals("\f4\t2\n256\t1\ttrue\n", machine.drainOutput());
    }

    @Test
    void printFollowsRedirectAndNativeTranscriptOnlyContainsNativeOutput() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("redirect"),
                "term.clear()\n"
                        + "local native = term.native()\n"
                        + "local win = window.create(native, 2, 2, 8, 2)\n"
                        + "local previous = term.redirect(win)\n"
                        + "assert(previous == native)\n"
                        + "assert(term.current() == win)\n"
                        + "print('remote')\n"
                        + "term.redirect(previous)\n"
                        + "print('native')\n");
        machine.tick();

        String output = machine.drainOutput();
        assertTrue(output.endsWith("\fnative\n"), output);
        assertFalse(output.contains("remote"));
        TerminalSurface surface = machine.getNativeSurface();
        assertEquals("remote", row(surface, 1).substring(1, 7));
        assertTrue(row(surface, 0).startsWith("native"), surface.toVisualText());
    }

    @Test
    void windowClipsWritesWithoutMovingParentCursor() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("window"),
                "term.clear()\n"
                        + "term.setCursorPos(7, 8)\n"
                        + "local win = window.create(term.current(), 2, 2, 3, 2)\n"
                        + "win.write('abcd')\n"
                        + "assert(term.getCursorPos() == 7)\n"
                        + "local child = window.create(win, 2, 2, 4, 2)\n"
                        + "child.write('XY')\n"
                        + "win.setVisible(false)\n"
                        + "win.write('hidden')\n");
        machine.tick();

        TerminalSurface surface = machine.getNativeSurface();
        assertEquals(6, surface.getCursorX());
        assertEquals(7, surface.getCursorY());
        assertEquals("abc", row(surface, 1).substring(1, 4));
        assertEquals("dXY", row(surface, 2).substring(1, 4));
        assertTrue(machine.drainOutput().endsWith("\f"));
    }

    @Test
    void windowClearLineScrollAndRepositionStayInsideItsRegion() throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("window-scroll"),
                "term.clear()\n"
                        + "local win = window.create(term.native(), 2, 2, 3, 3)\n"
                        + "win.write('a\\nb\\nc')\n"
                        + "win.scroll(1)\n"
                        + "win.reposition(1, 1, 2, 1)\n"
                        + "win.clearLine()\n");
        machine.tick();

        TerminalSurface surface = machine.getNativeSurface();
        assertEquals("b", row(surface, 1).substring(1, 2));
        assertEquals("c", row(surface, 2).substring(1, 2));
        assertEquals("", row(surface, 0).trim());
    }

    @Test
    void monitorRedirectUsesCharacterSizeAndPreservesLegacyPixelApi() {
        MonitorCanvas canvas = new MonitorCanvas();
        LuaMachine machine = new LuaMachine(100_000);
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
            public MonitorCanvas monitorAt(int dx, int dy, int dz) {
                return canvas;
            }
        });
        machine.load("local mon = tale.monitor()\n"
                + "assert(tale.monitor() == mon)\n"
                + "mon.write(1, 1, 'A', 'lime')\n"
                + "assert(mon.getPixel(1, 1) == 'black')\n"
                + "assert(mon.getPixel(2, 1) == 'lime')\n"
                + "local pw, ph = mon.getSize()\n"
                + "assert(pw == 40 and ph == 24)\n"
                + "local old = term.redirect(mon)\n"
                + "print('Hi')\n"
                + "local w, h = term.getSize()\n"
                + "assert(w == 10 and h == 4)\n"
                + "term.redirect(old)\n"
                + "print('native')\n", "monitor-terminal");
        machine.tick();

        assertEquals("native\n", machine.drainOutput());
        assertTrue(machine.getNativeSurface().toVisualText().startsWith("native\n"));
        assertTrue(canvas.getRevision() > 1);
        assertFalse(machine.getNativeSurface().toVisualText().startsWith("Hi"));
    }
}

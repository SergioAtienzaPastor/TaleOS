package me.kvothe.talecraft.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerminalSurfaceTest {

    @Test
    void writesAndWrapsInVisualOrder() {
        TerminalSurface surface = new TerminalSurface(4, 3);
        surface.write("abcdef");
        assertEquals("abcd\nef\n\n", surface.toVisualText());
        assertEquals(2, surface.getCursorX());
        assertEquals(1, surface.getCursorY());
    }

    @Test
    void newlineAndFinalColumnScrollWithoutDroppingText() {
        TerminalSurface surface = new TerminalSurface(3, 2);
        surface.write("abc");
        assertEquals("abc\n\n", surface.toVisualText());
        assertEquals(0, surface.getCursorX());
        assertEquals(1, surface.getCursorY());
        surface.write("defg");
        assertEquals("def\ng\n", surface.toVisualText());
    }

    @Test
    void carriageReturnAndTabHaveTerminalSemantics() {
        TerminalSurface surface = new TerminalSurface(10, 2);
        surface.write("ab\rc\tZ");
        assertEquals("c   Z\n\n", surface.toVisualText());
    }

    @Test
    void clearUsesCurrentColorsAndHomesCursor() {
        TerminalSurface surface = new TerminalSurface(3, 2);
        surface.setTextColor(4);
        surface.setBackgroundColor(2);
        surface.write("x");
        surface.clear();
        assertEquals(0, surface.getCursorX());
        assertEquals(0, surface.getCursorY());
        assertEquals(' ', surface.getChar(2, 1));
        assertEquals(4, surface.getCellTextColor(2, 1));
        assertEquals(2, surface.getCellBackgroundColor(2, 1));
    }

    @Test
    void clearLineOnlyChangesCursorLine() {
        TerminalSurface surface = new TerminalSurface(4, 2);
        surface.write("one\ntwo");
        surface.setCursorPos(2, 1);
        surface.clearLine();
        assertEquals("one\n\n", surface.toVisualText());
        assertEquals('o', surface.getChar(0, 0));
        assertEquals(' ', surface.getChar(0, 1));
        assertEquals(0, surface.getCursorX());
        assertEquals(1, surface.getCursorY());
    }

    @Test
    void positiveAndNegativeScrollFillExposedRows() {
        TerminalSurface surface = new TerminalSurface(2, 3);
        surface.write("a\nb\nc");
        surface.scroll(1);
        assertEquals("b\nc\n\n", surface.toVisualText());
        surface.scroll(-1);
        assertEquals("\nb\nc\n", surface.toVisualText());
        surface.scroll(3);
        assertEquals("\n\n\n", surface.toVisualText());
    }

    @Test
    void blitAppliesIndependentColorsPerCell() {
        TerminalSurface surface = new TerminalSurface(4, 1);
        surface.blit("ab", "0f", "f0");
        assertEquals(0, surface.getCellTextColor(0, 0));
        assertEquals(15, surface.getCellBackgroundColor(0, 0));
        assertEquals(15, surface.getCellTextColor(1, 0));
        assertEquals(0, surface.getCellBackgroundColor(1, 0));
        assertEquals(2, surface.getCursorX());
    }

    @Test
    void blitRejectsMismatchedLengthsAndBadDigits() {
        TerminalSurface surface = new TerminalSurface(4, 1);
        assertThrows(IllegalArgumentException.class, () -> surface.blit("ab", "0", "00"));
        assertThrows(IllegalArgumentException.class, () -> surface.blit("a", "x", "0"));
    }

    @Test
    void directCellsClipOutsideAndRejectControlCharacters() {
        TerminalSurface surface = new TerminalSurface(2, 1);
        surface.setCell(-1, 0, 'x', 1, 0);
        surface.setCell(0, 0, '\n', 1, 0);
        assertEquals(' ', surface.getChar(0, 0));
        assertEquals(' ', surface.getChar(99, 99));
    }

    @Test
    void cursorBlinkAndColorsAreObservable() {
        TerminalSurface surface = new TerminalSurface(2, 1);
        assertFalse(surface.getCursorBlink());
        surface.setCursorBlink(true);
        surface.setCursorPos(1, 0);
        surface.setTextColor(7);
        surface.setBackgroundColor(8);
        assertTrue(surface.getCursorBlink());
        assertEquals(1, surface.getCursorX());
        assertEquals(7, surface.getTextColor());
        assertEquals(8, surface.getBackgroundColor());
        assertThrows(IllegalArgumentException.class, () -> surface.setCursorPos(2, 0));
        assertThrows(IllegalArgumentException.class, () -> surface.setTextColor(16));
    }

    @Test
    void revisionsAreMonotonicAndNeverConsumed() {
        TerminalSurface surface = new TerminalSurface(2, 1);
        long firstObserver = surface.getRevision();
        long secondObserver = firstObserver;
        surface.write("x");
        assertTrue(surface.getRevision() > firstObserver);
        firstObserver = surface.getRevision();
        secondObserver = surface.getRevision();
        surface.setCursorBlink(true);
        assertTrue(surface.getRevision() > firstObserver);
        assertTrue(surface.getRevision() > secondObserver);
    }

    @Test
    void dimensionsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new TerminalSurface(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new TerminalSurface(2, -1));
    }
}

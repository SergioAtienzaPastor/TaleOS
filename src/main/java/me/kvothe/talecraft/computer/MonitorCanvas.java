package me.kvothe.talecraft.computer;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Drawable surface of a Monitor block: a 40x24 pixel grid over a fixed
 * 16-color palette, plus a built-in 3x5 microfont so programs can paint text
 * without per-pixel work. Pure in-memory state: Lua API calls mutate it from
 * the machine (tick) thread and open canvas pages push the dirty rows to
 * clients once per tick.
 */
public final class MonitorCanvas {

    public static final int WIDTH = 40;
    public static final int HEIGHT = 24;

    /** Palette names in index order (pixel values are indexes into this). */
    public static final String[] PALETTE = {
            "black", "navy", "blue", "cyan", "green", "lime", "yellow", "orange",
            "red", "pink", "magenta", "purple", "brown", "gray", "silver", "white",
    };

    /** Hex colors matching PALETTE (as used by the UI documents). */
    public static final String[] PALETTE_HEX = {
            "#14161c", "#2a3a6a", "#3a5fce", "#3cb5b5", "#3f9e4d", "#7cc050",
            "#e0c040", "#d8832c", "#cc4040", "#e08fb0", "#b84fb8", "#7a4fc0",
            "#7a5a38", "#707880", "#b8c0c8", "#e8e8e8",
    };

    private static final Map<String, Integer> COLOR_INDEX = new HashMap<>();

    static {
        for (int i = 0; i < PALETTE.length; i++) {
            COLOR_INDEX.put(PALETTE[i], i);
        }
    }

    /** Color name to index, or -1 when unknown. */
    public static int colorIndex(String name) {
        Integer i = COLOR_INDEX.get(name == null ? "" : name.toLowerCase());
        return i == null ? -1 : i;
    }

    // --- 3x5 microfont (rows top to bottom, '#' = pixel on) ---

    private static final String[][] FONT = new String[128][];

    private static void glyph(char c, String... rows) {
        FONT[c] = rows;
    }

    static {
        glyph('A', " # ", "# #", "###", "# #", "# #");
        glyph('B', "## ", "# #", "## ", "# #", "## ");
        glyph('C', " ##", "#  ", "#  ", "#  ", " ##");
        glyph('D', "## ", "# #", "# #", "# #", "## ");
        glyph('E', "###", "#  ", "## ", "#  ", "###");
        glyph('F', "###", "#  ", "## ", "#  ", "#  ");
        glyph('G', " ##", "#  ", "# #", "# #", " ##");
        glyph('H', "# #", "# #", "###", "# #", "# #");
        glyph('I', "###", " # ", " # ", " # ", "###");
        glyph('J', "  #", "  #", "  #", "# #", " # ");
        glyph('K', "# #", "# #", "## ", "# #", "# #");
        glyph('L', "#  ", "#  ", "#  ", "#  ", "###");
        glyph('M', "# #", "###", "###", "# #", "# #");
        glyph('N', "# #", "###", "###", "# #", "# #");
        glyph('O', " # ", "# #", "# #", "# #", " # ");
        glyph('P', "## ", "# #", "## ", "#  ", "#  ");
        glyph('Q', " # ", "# #", "# #", " # ", "  #");
        glyph('R', "## ", "# #", "## ", "# #", "# #");
        glyph('S', " ##", "#  ", " # ", "  #", "## ");
        glyph('T', "###", " # ", " # ", " # ", " # ");
        glyph('U', "# #", "# #", "# #", "# #", "###");
        glyph('V', "# #", "# #", "# #", "# #", " # ");
        glyph('W', "# #", "# #", "###", "###", "# #");
        glyph('X', "# #", "# #", " # ", "# #", "# #");
        glyph('Y', "# #", "# #", " # ", " # ", " # ");
        glyph('Z', "###", "  #", " # ", "#  ", "###");
        glyph('0', "###", "# #", "# #", "# #", "###");
        glyph('1', " # ", "## ", " # ", " # ", "###");
        glyph('2', "## ", "  #", " # ", "#  ", "###");
        glyph('3', "## ", "  #", " # ", "  #", "## ");
        glyph('4', "# #", "# #", "###", "  #", "  #");
        glyph('5', "###", "#  ", "## ", "  #", "## ");
        glyph('6', " ##", "#  ", "###", "# #", " # ");
        glyph('7', "###", "  #", " # ", " # ", " # ");
        glyph('8', " # ", "# #", " # ", "# #", " # ");
        glyph('9', " # ", "# #", "###", "  #", "## ");
        glyph(':', "   ", " # ", "   ", " # ", "   ");
        glyph('.', "   ", "   ", "   ", "   ", " # ");
        glyph('-', "   ", "   ", "###", "   ", "   ");
        glyph('_', "   ", "   ", "   ", "   ", "###");
        glyph('!', " # ", " # ", " # ", "   ", " # ");
        glyph('?', "## ", "  #", " # ", "   ", " # ");
        glyph('+', "   ", " # ", "###", " # ", "   ");
        glyph('=', "   ", "###", "   ", "###", "   ");
        glyph('/', "  #", "  #", " # ", "#  ", "#  ");
        glyph('>', "#  ", " # ", "  #", " # ", "#  ");
        glyph('<', "  #", " # ", "#  ", " # ", "  #");
        glyph('\'', " # ", " # ", "   ", "   ", "   ");
        glyph('"', "# #", "# #", "   ", "   ", "   ");
        glyph(',', "   ", "   ", "   ", " # ", "#  ");
        glyph(';', "   ", " # ", "   ", " # ", "#  ");
        glyph('%', "# #", "  #", " # ", "#  ", "# #");
        glyph('#', "# #", "###", "# #", "###", "# #");
        glyph('*', "   ", "# #", " # ", "# #", "   ");
        glyph('(', "  #", " # ", " # ", " # ", "  #");
        glyph(')', "#  ", " # ", " # ", " # ", "#  ");
        glyph(' ', "   ", "   ", "   ", "   ", "   ");
    }

    private final byte[] pixels = new byte[WIDTH * HEIGHT];
    private final BitSet dirtyRows = new BitSet(HEIGHT);

    public MonitorCanvas() {
        clear(0);
    }

    public void clear(int color) {
        java.util.Arrays.fill(pixels, (byte) color);
        dirtyRows.set(0, HEIGHT);
    }

    /** 0-based; out-of-range writes are ignored (scripts can't crash on it). */
    public void setPixel(int x, int y, int color) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            return;
        }
        pixels[y * WIDTH + x] = (byte) color;
        dirtyRows.set(y);
    }

    public int getPixel(int x, int y) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            return 0;
        }
        return pixels[y * WIDTH + x];
    }

    public void fill(int x, int y, int w, int h, int color) {
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                setPixel(col, row, color);
            }
        }
    }

    /** Paints text with the 3x5 font at pixel position (x, y); each character
     * advances 4 pixels. Lowercase renders as uppercase. */
    public void writeText(int x, int y, String text, int color) {
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            String[] rows = c < 128 ? FONT[c] : null;
            if (rows != null) {
                for (int row = 0; row < 5; row++) {
                    for (int col = 0; col < 3; col++) {
                        if (rows[row].charAt(col) == '#') {
                            setPixel(cursor + col, y + row, color);
                        }
                    }
                }
            }
            cursor += 4;
        }
    }

    /** Rows changed since the last call; the returned set is a copy. */
    public BitSet consumeDirtyRows() {
        BitSet copy = (BitSet) dirtyRows.clone();
        dirtyRows.clear();
        return copy;
    }

    /** Marks every row dirty (new canvas page opening mid-session). */
    public void markAllDirty() {
        dirtyRows.set(0, HEIGHT);
    }
}

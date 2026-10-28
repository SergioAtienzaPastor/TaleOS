package me.kvothe.talecraft.computer;

import java.util.Arrays;

/** Pure Java character terminal with independent foreground and background colors per cell. */
public final class TerminalSurface implements TerminalTarget {

    public static final int BLACK = 0;
    public static final int WHITE = 15;

    private final int width;
    private final int height;
    private final char[] characters;
    private final byte[] foreground;
    private final byte[] background;
    private int cursorX;
    private int cursorY;
    private boolean cursorBlink;
    private int textColor = WHITE;
    private int backgroundColor = BLACK;
    private long revision;

    public TerminalSurface(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("terminal dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        int size = Math.multiplyExact(width, height);
        characters = new char[size];
        foreground = new byte[size];
        background = new byte[size];
        clear();
        revision = 1;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public synchronized void write(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            writeChar(text.charAt(i), textColor, backgroundColor);
        }
    }

    private void writeChar(char character, int fg, int bg) {
        switch (character) {
            case '\n' -> newLine();
            case '\r' -> setCursorInternal(0, cursorY);
            case '\t' -> {
                int spaces = 4 - cursorX % 4;
                for (int i = 0; i < spaces; i++) {
                    writeChar(' ', fg, bg);
                }
            }
            default -> {
                if (!Character.isISOControl(character)) {
                    setCellInternal(cursorX, cursorY, character, fg, bg);
                    advance();
                }
            }
        }
    }

    private void advance() {
        cursorX++;
        if (cursorX >= width) {
            cursorX = 0;
            newLine();
        }
    }

    private void newLine() {
        cursorX = 0;
        cursorY++;
        if (cursorY >= height) {
            cursorY = height - 1;
            scrollInternal(1);
        } else {
            revision++;
        }
    }

    @Override
    public synchronized void blit(String text, String fg, String bg) {
        if (text == null || fg == null || bg == null) {
            throw new IllegalArgumentException("blit strings must not be null");
        }
        if (text.length() != fg.length() || text.length() != bg.length()) {
            throw new IllegalArgumentException("blit text and color strings must have equal lengths");
        }
        for (int i = 0; i < text.length(); i++) {
            writeChar(text.charAt(i), parseBlitColor(fg.charAt(i)), parseBlitColor(bg.charAt(i)));
        }
    }

    public static int parseBlitColor(char digit) {
        int index = Character.digit(digit, 16);
        if (index < 0) {
            throw new IllegalArgumentException("invalid blit color: " + digit);
        }
        return index;
    }

    @Override
    public synchronized void clear() {
        Arrays.fill(characters, ' ');
        Arrays.fill(foreground, (byte) textColor);
        Arrays.fill(background, (byte) backgroundColor);
        cursorX = 0;
        cursorY = 0;
        revision++;
    }

    @Override
    public synchronized void clearLine() {
        for (int x = 0; x < width; x++) {
            setCellInternal(x, cursorY, ' ', textColor, backgroundColor);
        }
        cursorX = 0;
        revision++;
    }

    @Override
    public synchronized void scroll(int lines) {
        if (lines != 0) {
            scrollInternal(lines);
        }
    }

    private void scrollInternal(int lines) {
        if (lines >= height || lines <= -height) {
            Arrays.fill(characters, ' ');
            Arrays.fill(foreground, (byte) textColor);
            Arrays.fill(background, (byte) backgroundColor);
        } else if (lines > 0) {
            int moved = width * (height - lines);
            System.arraycopy(characters, width * lines, characters, 0, moved);
            System.arraycopy(foreground, width * lines, foreground, 0, moved);
            System.arraycopy(background, width * lines, background, 0, moved);
            fillRows(height - lines, height);
        } else {
            int amount = -lines;
            int moved = width * (height - amount);
            System.arraycopy(characters, 0, characters, width * amount, moved);
            System.arraycopy(foreground, 0, foreground, width * amount, moved);
            System.arraycopy(background, 0, background, width * amount, moved);
            fillRows(0, amount);
        }
        revision++;
    }

    private void fillRows(int first, int end) {
        for (int y = first; y < end; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                characters[index] = ' ';
                foreground[index] = (byte) textColor;
                background[index] = (byte) backgroundColor;
            }
        }
    }

    @Override
    public synchronized int getCursorX() {
        return cursorX;
    }

    @Override
    public synchronized int getCursorY() {
        return cursorY;
    }

    @Override
    public synchronized void setCursorPos(int x, int y) {
        checkPosition(x, y);
        setCursorInternal(x, y);
    }

    private void setCursorInternal(int x, int y) {
        if (cursorX != x || cursorY != y) {
            cursorX = x;
            cursorY = y;
            revision++;
        }
    }

    @Override
    public synchronized boolean getCursorBlink() {
        return cursorBlink;
    }

    @Override
    public synchronized void setCursorBlink(boolean blink) {
        if (cursorBlink != blink) {
            cursorBlink = blink;
            revision++;
        }
    }

    @Override
    public synchronized int getTextColor() {
        return textColor;
    }

    @Override
    public synchronized void setTextColor(int color) {
        checkColor(color);
        if (textColor != color) {
            textColor = color;
            revision++;
        }
    }

    @Override
    public synchronized int getBackgroundColor() {
        return backgroundColor;
    }

    @Override
    public synchronized void setBackgroundColor(int color) {
        checkColor(color);
        if (backgroundColor != color) {
            backgroundColor = color;
            revision++;
        }
    }

    @Override
    public synchronized char getChar(int x, int y) {
        return inside(x, y) ? characters[y * width + x] : ' ';
    }

    @Override
    public synchronized int getCellTextColor(int x, int y) {
        return inside(x, y) ? foreground[y * width + x] : backgroundColor;
    }

    @Override
    public synchronized int getCellBackgroundColor(int x, int y) {
        return inside(x, y) ? background[y * width + x] : backgroundColor;
    }

    @Override
    public synchronized void setCell(int x, int y, char character, int fg, int bg) {
        checkColor(fg);
        checkColor(bg);
        if (inside(x, y) && !Character.isISOControl(character)) {
            setCellInternal(x, y, character, fg, bg);
        }
    }

    private void setCellInternal(int x, int y, char character, int fg, int bg) {
        int index = y * width + x;
        if (characters[index] != character || foreground[index] != (byte) fg
                || background[index] != (byte) bg) {
            characters[index] = character;
            foreground[index] = (byte) fg;
            background[index] = (byte) bg;
            revision++;
        }
    }

    /** Full visual projection, top row first, including a final newline. */
    public synchronized String toVisualText() {
        StringBuilder text = new StringBuilder((width + 1) * height);
        for (int y = 0; y < height; y++) {
            int end = width;
            while (end > 0 && characters[y * width + end - 1] == ' ') {
                end--;
            }
            text.append(characters, y * width, end).append('\n');
        }
        return text.toString();
    }

    public synchronized long getRevision() {
        return revision;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private void checkPosition(int x, int y) {
        if (!inside(x, y)) {
            throw new IllegalArgumentException("cursor outside " + width + "x" + height
                    + " terminal: " + x + "," + y);
        }
    }

    private static void checkColor(int color) {
        if (color < 0 || color >= MonitorCanvas.PALETTE.length) {
            throw new IllegalArgumentException("terminal color outside palette: " + color);
        }
    }
}

package me.kvothe.taleos.computer;

/** Character-terminal adapter over a monitor canvas. */
public final class MonitorTerminal implements TerminalTarget {

    public static final int CELL_WIDTH = 4;
    public static final int CELL_HEIGHT = 6;

    private final MonitorCanvas canvas;
    private TerminalSurface surface;

    public MonitorTerminal(MonitorCanvas canvas) {
        this.canvas = canvas;
        this.surface = new TerminalSurface(canvas.getWidth() / CELL_WIDTH,
                canvas.getHeight() / CELL_HEIGHT);
    }

    public TerminalSurface getSurface() {
        return surface();
    }

    private TerminalSurface surface() {
        int width = canvas.getWidth() / CELL_WIDTH;
        int height = canvas.getHeight() / CELL_HEIGHT;
        if (surface.getWidth() != width || surface.getHeight() != height) {
            surface = new TerminalSurface(width, height);
        }
        return surface;
    }

    @Override
    public int getWidth() {
        return surface().getWidth();
    }

    @Override
    public int getHeight() {
        return surface().getHeight();
    }

    @Override
    public void write(String text) {
        surface().write(text);
        redraw();
    }

    @Override
    public void blit(String text, String foreground, String background) {
        surface().blit(text, foreground, background);
        redraw();
    }

    @Override
    public void clear() {
        surface().clear();
        redraw();
    }

    @Override
    public void clearLine() {
        surface().clearLine();
        redraw();
    }

    @Override
    public void scroll(int lines) {
        surface().scroll(lines);
        redraw();
    }

    @Override
    public int getCursorX() {
        return surface().getCursorX();
    }

    @Override
    public int getCursorY() {
        return surface().getCursorY();
    }

    @Override
    public void setCursorPos(int x, int y) {
        surface().setCursorPos(x, y);
        redraw();
    }

    @Override
    public boolean getCursorBlink() {
        return surface().getCursorBlink();
    }

    @Override
    public void setCursorBlink(boolean blink) {
        surface().setCursorBlink(blink);
        redraw();
    }

    @Override
    public int getTextColor() {
        return surface().getTextColor();
    }

    @Override
    public void setTextColor(int color) {
        surface().setTextColor(color);
        if (surface().getCursorBlink()) {
            redraw();
        }
    }

    @Override
    public int getBackgroundColor() {
        return surface().getBackgroundColor();
    }

    @Override
    public void setBackgroundColor(int color) {
        surface().setBackgroundColor(color);
    }

    @Override
    public char getChar(int x, int y) {
        return surface().getChar(x, y);
    }

    @Override
    public int getCellTextColor(int x, int y) {
        return surface().getCellTextColor(x, y);
    }

    @Override
    public int getCellBackgroundColor(int x, int y) {
        return surface().getCellBackgroundColor(x, y);
    }

    @Override
    public void setCell(int x, int y, char character, int foreground, int background) {
        surface().setCell(x, y, character, foreground, background);
        redraw();
    }

    private void redraw() {
        for (int y = 0; y < getHeight(); y++) {
            for (int x = 0; x < getWidth(); x++) {
                int px = x * CELL_WIDTH;
                int py = y * CELL_HEIGHT;
                canvas.fill(px, py, CELL_WIDTH, CELL_HEIGHT, surface().getCellBackgroundColor(x, y));
                char character = surface().getChar(x, y);
                if (character != ' ') {
                    canvas.writeText(px, py, String.valueOf(character), surface().getCellTextColor(x, y));
                }
            }
        }
        if (surface().getCursorBlink()) {
            canvas.fill(surface().getCursorX() * CELL_WIDTH,
                    surface().getCursorY() * CELL_HEIGHT + CELL_HEIGHT - 1,
                    CELL_WIDTH - 1, 1, surface().getTextColor());
        }
    }
}

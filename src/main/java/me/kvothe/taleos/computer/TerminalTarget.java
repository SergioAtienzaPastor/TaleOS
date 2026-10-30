package me.kvothe.taleos.computer;

/** Shared destination contract for native terminals, monitors and redirected windows. */
public interface TerminalTarget {

    int getWidth();

    int getHeight();

    void write(String text);

    void blit(String text, String foreground, String background);

    void clear();

    void clearLine();

    void scroll(int lines);

    int getCursorX();

    int getCursorY();

    void setCursorPos(int x, int y);

    boolean getCursorBlink();

    void setCursorBlink(boolean blink);

    int getTextColor();

    void setTextColor(int color);

    int getBackgroundColor();

    void setBackgroundColor(int color);

    char getChar(int x, int y);

    int getCellTextColor(int x, int y);

    int getCellBackgroundColor(int x, int y);

    void setCell(int x, int y, char character, int foreground, int background);

    default boolean isColor() {
        return true;
    }
}

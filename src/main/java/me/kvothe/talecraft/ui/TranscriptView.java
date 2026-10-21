package me.kvothe.talecraft.ui;

import java.util.ArrayList;
import java.util.List;

import me.kvothe.talecraft.computer.Computer;

/**
 * Renders the computer transcript for the terminal page. The client's text
 * field jumps back to its top whenever the value is set (the UI protocol has
 * no scroll command), so the transcript is rendered with the newest block
 * first: the latest command and its output are always visible at the top,
 * right under the input row, and scrolling down reveals older history. Blocks
 * start at each command echo (see {@link Computer#ECHO_MARK}) and each block
 * keeps its internal top-to-bottom order.
 */
public final class TranscriptView {

    private TranscriptView() {
    }

    /** Transcript with its blocks in reverse arrival order (newest first). */
    public static String newestFirst(String scrollback) {
        List<String> blocks = new ArrayList<>();
        int pos = 0;
        while (pos <= scrollback.length()) {
            int next = scrollback.indexOf(Computer.ECHO_MARK, pos);
            blocks.add(next < 0 ? scrollback.substring(pos) : scrollback.substring(pos, next));
            if (next < 0) {
                break;
            }
            pos = next + 1;
        }
        StringBuilder sb = new StringBuilder(scrollback.length());
        for (int i = blocks.size() - 1; i >= 0; i--) {
            sb.append(blocks.get(i));
        }
        return sb.toString();
    }
}

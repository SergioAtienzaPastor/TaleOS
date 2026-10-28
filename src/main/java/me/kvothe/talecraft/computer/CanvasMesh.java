package me.kvothe.talecraft.computer;

import java.util.ArrayList;
import java.util.List;

/**
 * Greedy mesher for a {@link MonitorCanvas}: collapses same-color pixels into
 * few axis-aligned rectangles (horizontal runs first, then vertical merges of
 * runs with identical span and color). Color index 0 (black) is the screen
 * background and produces no rectangles — the monitor model face is already
 * dark, so only lit pixels become world geometry.
 */
public final class CanvasMesh {

    /** Inclusive pixel rectangle of one color: x0..x1, y0..y1. */
    public record Rect(int x0, int y0, int x1, int y1, int color) {
        public int width() {
            return x1 - x0 + 1;
        }

        public int height() {
            return y1 - y0 + 1;
        }
    }

    private CanvasMesh() {
    }

    public static List<Rect> mesh(MonitorCanvas canvas) {
        return mesh(canvas, 0, 0, canvas.getWidth(), canvas.getHeight());
    }

    /** Meshes only a global-pixel region; returned rectangles keep global coordinates. */
    public static List<Rect> mesh(MonitorCanvas canvas, int startX, int startY,
            int width, int height) {
        int endX = Math.min(canvas.getWidth(), startX + width);
        int endY = Math.min(canvas.getHeight(), startY + height);
        startX = Math.max(0, startX);
        startY = Math.max(0, startY);
        List<Rect> closed = new ArrayList<>();
        List<Rect> open = new ArrayList<>();
        for (int y = startY; y < endY; y++) {
            List<Rect> rowRuns = new ArrayList<>();
            int x = startX;
            while (x < endX) {
                int color = canvas.getPixel(x, y);
                if (color == 0) {
                    x++;
                    continue;
                }
                int x0 = x;
                while (x < endX && canvas.getPixel(x, y) == color) {
                    x++;
                }
                rowRuns.add(new Rect(x0, y, x - 1, y, color));
            }
            boolean[] merged = new boolean[rowRuns.size()];
            List<Rect> nextOpen = new ArrayList<>();
            for (Rect prev : open) {
                boolean grown = false;
                for (int i = 0; i < rowRuns.size(); i++) {
                    Rect run = rowRuns.get(i);
                    if (!merged[i] && run.x0() == prev.x0() && run.x1() == prev.x1()
                            && run.color() == prev.color()) {
                        nextOpen.add(new Rect(prev.x0(), prev.y0(), prev.x1(), y, prev.color()));
                        merged[i] = true;
                        grown = true;
                        break;
                    }
                }
                if (!grown) {
                    closed.add(prev);
                }
            }
            for (int i = 0; i < rowRuns.size(); i++) {
                if (!merged[i]) {
                    nextOpen.add(rowRuns.get(i));
                }
            }
            open = nextOpen;
        }
        closed.addAll(open);
        return closed;
    }
}

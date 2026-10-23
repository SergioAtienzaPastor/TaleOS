package me.kvothe.talecraft.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** CanvasMesh: greedy merging of same-color pixels into rectangles. */
class CanvasMeshTest {

    @Test
    void blankCanvasMeshesToNothing() {
        assertTrue(CanvasMesh.mesh(new MonitorCanvas()).isEmpty());
    }

    @Test
    void singlePixelIsOneUnitRect() {
        MonitorCanvas c = new MonitorCanvas();
        c.setPixel(3, 4, 7);
        List<CanvasMesh.Rect> mesh = CanvasMesh.mesh(c);
        assertEquals(1, mesh.size());
        CanvasMesh.Rect r = mesh.get(0);
        assertEquals(3, r.x0());
        assertEquals(4, r.y0());
        assertEquals(3, r.x1());
        assertEquals(4, r.y1());
        assertEquals(7, r.color());
    }

    @Test
    void horizontalRunMergesIntoOneRect() {
        MonitorCanvas c = new MonitorCanvas();
        c.fill(2, 3, 5, 1, 9);
        List<CanvasMesh.Rect> mesh = CanvasMesh.mesh(c);
        assertEquals(1, mesh.size());
        assertEquals(new CanvasMesh.Rect(2, 3, 6, 3, 9), mesh.get(0));
    }

    @Test
    void solidBlockMergesVertically() {
        MonitorCanvas c = new MonitorCanvas();
        c.fill(2, 3, 4, 2, 5);
        List<CanvasMesh.Rect> mesh = CanvasMesh.mesh(c);
        assertEquals(1, mesh.size(), "2 rows of identical runs merge");
        assertEquals(new CanvasMesh.Rect(2, 3, 5, 4, 5), mesh.get(0));
    }

    @Test
    void differentColorsStaySeparate() {
        MonitorCanvas c = new MonitorCanvas();
        c.fill(0, 0, 2, 1, 1);
        c.fill(2, 0, 2, 1, 2);
        List<CanvasMesh.Rect> mesh = CanvasMesh.mesh(c);
        assertEquals(2, mesh.size());
    }

    @Test
    void interruptedRunDoesNotMergeVertically() {
        MonitorCanvas c = new MonitorCanvas();
        c.fill(0, 0, 3, 1, 4); // row 0: xxx
        c.setPixel(1, 1, 4);   // row 1: .x.
        List<CanvasMesh.Rect> mesh = CanvasMesh.mesh(c);
        assertEquals(2, mesh.size(), "narrower second row closes the first run");
    }

    @Test
    void clockLikeTextStaysWellUnderTheRectCap() {
        MonitorCanvas c = new MonitorCanvas();
        c.writeText(1, 3, "12:34:56", 15);
        List<CanvasMesh.Rect> mesh = CanvasMesh.mesh(c);
        assertTrue(mesh.size() < 200, "meshed to " + mesh.size() + " rects");
        assertTrue(mesh.size() > 20, "digits actually produce geometry");
    }
}

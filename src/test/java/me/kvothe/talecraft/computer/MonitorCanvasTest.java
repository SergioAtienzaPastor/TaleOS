package me.kvothe.talecraft.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.kvothe.talecraft.lua.LuaMachine;

/** MonitorCanvas: pixels, dirty-row tracking, font, and the Lua API surface. */
class MonitorCanvasTest {

    @Test
    void setAndGetPixel() {
        MonitorCanvas c = new MonitorCanvas();
        c.setPixel(3, 4, MonitorCanvas.colorIndex("lime"));
        assertEquals(MonitorCanvas.colorIndex("lime"), c.getPixel(3, 4));
        assertEquals(0, c.getPixel(0, 0), "untouched pixel stays black");
    }

    @Test
    void outOfRangeWritesAreIgnored() {
        MonitorCanvas c = new MonitorCanvas();
        c.setPixel(-1, 0, 5);
        c.setPixel(0, 999, 5);
        assertEquals(0, c.getPixel(0, 0));
    }

    @Test
    void dirtyRowsAreTrackedAndConsumed() {
        MonitorCanvas c = new MonitorCanvas();
        c.consumeDirtyRows(); // initial clear
        c.setPixel(0, 5, 1);
        c.setPixel(39, 5, 2);
        c.setPixel(10, 7, 3);
        assertTrue(c.consumeDirtyRows().cardinality() == 2, "rows 5 and 7 only");
        assertTrue(c.consumeDirtyRows().isEmpty(), "nothing dirty after consume");
    }

    @Test
    void fillPaintsRectangles() {
        MonitorCanvas c = new MonitorCanvas();
        c.fill(2, 3, 4, 2, MonitorCanvas.colorIndex("red"));
        assertEquals(MonitorCanvas.colorIndex("red"), c.getPixel(2, 3));
        assertEquals(MonitorCanvas.colorIndex("red"), c.getPixel(5, 4));
        assertEquals(0, c.getPixel(6, 3), "outside the rectangle");
    }

    @Test
    void writeTextUsesTheMicrofont() {
        MonitorCanvas c = new MonitorCanvas();
        c.writeText(0, 0, "1", MonitorCanvas.colorIndex("white"));
        // glyph '1': " # " / "## " / " # " / " # " / "###"
        assertEquals(MonitorCanvas.colorIndex("white"), c.getPixel(1, 0));
        assertEquals(0, c.getPixel(0, 0));
        assertEquals(MonitorCanvas.colorIndex("white"), c.getPixel(0, 4));
    }

    @Test
    void monitorWithoutBridgeIsNil() {
        LuaMachine m = new LuaMachine(100_000);
        m.load("print(tale.monitor())", "test");
        m.tick();
        assertEquals("nil\n", m.drainOutput());
    }

    @Test
    void monitorApiPaintsThroughLua() {
        MonitorCanvas canvas = new MonitorCanvas();
        LuaMachine m = new LuaMachine(100_000);
        m.setWorldBridge(new me.kvothe.talecraft.lua.WorldBridge() {
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
        m.load("local mon = tale.monitor()\n"
                + "mon.setPixel(1, 1, 'lime')\n"
                + "mon.write(2, 2, 'A', 'red')\n"
                + "print(mon.getPixel(1, 1))\n"
                + "print(mon.getSize())\n", "test");
        m.tick();
        assertEquals("lime\n" + MonitorCanvas.WIDTH + "\t" + MonitorCanvas.HEIGHT + "\n",
                m.drainOutput());
        assertEquals(MonitorCanvas.colorIndex("lime"), canvas.getPixel(0, 0));
        assertEquals(MonitorCanvas.colorIndex("red"), canvas.getPixel(2, 1)); // 'A' first row: " # "
    }
}

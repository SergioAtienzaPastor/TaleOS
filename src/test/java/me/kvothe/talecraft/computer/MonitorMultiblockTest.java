package me.kvothe.talecraft.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import me.kvothe.talecraft.computer.MonitorMultiblock.Block;
import me.kvothe.talecraft.computer.MonitorMultiblock.Facing;
import me.kvothe.talecraft.computer.MonitorMultiblock.Pos;
import me.kvothe.talecraft.computer.MonitorMultiblock.Structure;

class MonitorMultiblockTest {

    @Test
    void singleBlockKeepsLegacyDimensions() {
        List<Structure> structures = MonitorMultiblock.rebuild(List.of(block(2, 70, -3)));

        assertEquals(1, structures.size());
        Structure structure = structures.get(0);
        assertEquals(new Pos(2, 70, -3), structure.origin());
        assertEquals(1, structure.width());
        assertEquals(1, structure.height());
        assertEquals(40, structure.pixelWidth());
        assertEquals(24, structure.pixelHeight());
    }

    @Test
    void horizontalAndVerticalLinesFormSingleRectangles() {
        Structure horizontal = MonitorMultiblock.rebuild(List.of(
                block(4, 64, 8), block(2, 64, 8), block(3, 64, 8))).get(0);
        Structure vertical = MonitorMultiblock.rebuild(List.of(
                block(2, 64, 8), block(2, 65, 8), block(2, 66, 8))).get(0);

        assertEquals(new Pos(2, 64, 8), horizontal.origin());
        assertEquals(3, horizontal.width());
        assertEquals(1, horizontal.height());
        assertEquals(new Pos(2, 66, 8), vertical.origin());
        assertEquals(1, vertical.width());
        assertEquals(3, vertical.height());
    }

    @Test
    void twoByTwoUsesTopLeftOriginAndUniqueBlockCoordinates() {
        Structure structure = MonitorMultiblock.rebuild(List.of(
                block(3, 65, 2), block(2, 65, 2),
                block(2, 64, 2), block(3, 64, 2))).get(0);
        Pos bottomRight = new Pos(3, 64, 2);

        assertEquals(2, structure.width());
        assertEquals(2, structure.height());
        assertEquals(80, structure.pixelWidth());
        assertEquals(48, structure.pixelHeight());
        assertEquals(1, structure.blockX(bottomRight));
        assertEquals(1, structure.blockY(bottomRight));
        assertEquals(40, structure.pixelX(bottomRight));
        assertEquals(24, structure.pixelY(bottomRight));
        assertEquals(new MonitorDisplay.LocalRect(0, 0, 1, 1),
                MonitorDisplay.localRect(structure, bottomRight,
                        new CanvasMesh.Rect(40, 24, 79, 47, 4)));
        assertEquals(4, new HashSet<>(structure.blocks()).size());
    }

    @Test
    void eastFacingUsesItsOwnHorizontalAxis() {
        Structure structure = MonitorMultiblock.rebuild(List.of(
                new Block(new Pos(4, 60, 8), Facing.EAST),
                new Block(new Pos(4, 60, 7), Facing.EAST))).get(0);

        assertEquals(new Pos(4, 60, 8), structure.origin());
        assertEquals(2, structure.width());
        assertEquals(40, structure.pixelX(new Pos(4, 60, 7)));
    }

    @Test
    void incompatibleOrientationsNeverMerge() {
        List<Structure> structures = MonitorMultiblock.rebuild(List.of(
                new Block(new Pos(0, 64, 0), Facing.SOUTH),
                new Block(new Pos(1, 64, 0), Facing.EAST)));

        assertEquals(2, structures.size());
        assertEquals(Set.of(Facing.SOUTH, Facing.EAST),
                Set.of(structures.get(0).facing(), structures.get(1).facing()));
    }

    @Test
    void holeProducesOnlyCompleteRectanglesAndKeepsEveryBlock() {
        List<Block> blocks = new ArrayList<>();
        for (int y = 64; y <= 66; y++) {
            for (int x = 0; x < 3; x++) {
                if (x != 1 || y != 65) {
                    blocks.add(block(x, y, 0));
                }
            }
        }

        List<Structure> structures = MonitorMultiblock.rebuild(blocks);
        Set<Pos> covered = new HashSet<>();
        for (Structure structure : structures) {
            assertEquals(structure.width() * structure.height(), structure.blocks().size());
            for (Pos pos : structure.blocks()) {
                assertTrue(covered.add(pos), "block covered twice");
                assertFalse(pos.equals(new Pos(1, 65, 0)));
            }
        }
        assertEquals(8, covered.size());
        assertTrue(structures.size() > 1);
    }

    @Test
    void removingIntermediateBlockSplitsLine() {
        List<Structure> structures = MonitorMultiblock.rebuild(List.of(
                block(0, 64, 0), block(2, 64, 0)));

        assertEquals(2, structures.size());
        assertEquals(1, structures.get(0).width());
        assertEquals(1, structures.get(1).width());
    }

    @Test
    void growingLineChangesDimensionsAndRetainsDeterministicOrigin() {
        Structure before = MonitorMultiblock.rebuild(List.of(block(5, 64, 1))).get(0);
        Structure after = MonitorMultiblock.rebuild(List.of(
                block(5, 64, 1), block(6, 64, 1))).get(0);

        assertEquals(before.origin(), after.origin());
        assertEquals(1, before.width());
        assertEquals(2, after.width());
        assertEquals(80, after.pixelWidth());
    }

    @Test
    void oversizedRectangleIsPartitionedAtSafeLimit() {
        List<Block> blocks = new ArrayList<>();
        for (int x = 0; x < MonitorMultiblock.MAX_WIDTH + 1; x++) {
            blocks.add(block(x, 64, 0));
        }

        List<Structure> structures = MonitorMultiblock.rebuild(blocks);
        assertEquals(2, structures.size());
        assertEquals(MonitorMultiblock.MAX_WIDTH, structures.get(0).width());
        assertEquals(1, structures.get(1).width());
    }

    @Test
    void croppedMesherDoesNotRepeatWholeCanvasOnEachBlock() {
        Structure structure = MonitorMultiblock.rebuild(List.of(
                block(0, 64, 0), block(1, 64, 0))).get(0);
        MonitorCanvas canvas = new MonitorCanvas(structure.pixelWidth(), structure.pixelHeight());
        canvas.fill(0, 0, canvas.getWidth(), canvas.getHeight(), 4);
        Pos left = new Pos(0, 64, 0);
        Pos right = new Pos(1, 64, 0);

        List<CanvasMesh.Rect> leftMesh = CanvasMesh.mesh(canvas, structure.pixelX(left),
                structure.pixelY(left), 40, 24);
        List<CanvasMesh.Rect> rightMesh = CanvasMesh.mesh(canvas, structure.pixelX(right),
                structure.pixelY(right), 40, 24);

        assertEquals(List.of(new CanvasMesh.Rect(0, 0, 39, 23, 4)), leftMesh);
        assertEquals(List.of(new CanvasMesh.Rect(40, 0, 79, 23, 4)), rightMesh);
        assertEquals(new MonitorDisplay.LocalRect(0, 0, 1, 1),
                MonitorDisplay.localRect(structure, left, leftMesh.get(0)));
        assertEquals(new MonitorDisplay.LocalRect(0, 0, 1, 1),
                MonitorDisplay.localRect(structure, right, rightMesh.get(0)));
    }

    @Test
    void resizeAndRebindKeepContentRevisionAndObserversOnSharedCanvas() {
        MonitorCanvas canvas = new MonitorCanvas();
        MonitorTerminal terminal = new MonitorTerminal(canvas);
        canvas.setPixel(3, 4, 7);
        long pageRevision = canvas.getRevision();
        long displayRevision = canvas.getRevision();
        byte[] resizedPixels = new byte[80 * 24];
        System.arraycopy(canvas.snapshot().pixels(), 0, resizedPixels, 0, 40);
        for (int y = 1; y < 24; y++) {
            System.arraycopy(canvas.snapshot().pixels(), y * 40, resizedPixels, y * 80, 40);
        }

        canvas.restore(new MonitorCanvas.Snapshot(80, 24, resizedPixels));
        me.kvothe.talecraft.peripheral.monitor.MonitorPeripheral peripheral =
                new me.kvothe.talecraft.peripheral.monitor.MonitorPeripheral(new MonitorCanvas());
        peripheral.rebind(canvas);

        assertSame(canvas, peripheral.getCanvas());
        assertEquals(20, terminal.getWidth());
        assertEquals(4, terminal.getHeight());
        assertEquals(80, peripheral.getCanvas().getWidth());
        assertEquals(7, canvas.getPixel(3, 4));
        assertTrue(canvas.getRevision() > pageRevision);
        assertTrue(canvas.getRevision() > displayRevision);
        assertEquals(canvas.getRevision(), canvas.getRevision());
        assertEquals(20, peripheral.call("getTerminalSize", org.luaj.vm2.LuaValue.NIL).arg(1).toint());
        assertEquals(4, peripheral.call("getTerminalSize", org.luaj.vm2.LuaValue.NIL).arg(2).toint());
    }

    private static Block block(int x, int y, int z) {
        return new Block(new Pos(x, y, z), Facing.SOUTH);
    }
}

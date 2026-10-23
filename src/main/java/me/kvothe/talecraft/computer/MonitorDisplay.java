package me.kvothe.talecraft.computer;

import java.util.List;

import org.joml.Matrix4d;
import org.joml.Vector3f;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;

/**
 * In-world rendering of a {@link MonitorCanvas}: the canvas is meshed into
 * colored rectangles ({@link CanvasMesh}) and pushed to clients as solid
 * {@code DisplayDebug} shapes via {@link DebugUtils} — thin slabs floating a
 * few millimetres in front of the monitor's screen face, oriented by the
 * block's rotation. No entities are spawned: the vanilla client renders these
 * packets natively, in the real 16-color palette, for every player in the
 * world.
 *
 * Shapes carry a client-side TTL and are re-sent on a heartbeat while the
 * canvas is non-blank; when the canvas clears or the block disappears,
 * sending simply stops and the last frame fades out on its own (no global
 * ClearDebugShapes, which would also wipe other mods' shapes). Chunk reads go
 * through an in-memory {@link SectionReader}: an unloaded chunk means "skip
 * this tick", never teardown.
 */
public final class MonitorDisplay {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BLOCK_RECHECK_TICKS = 40;

    /** Seconds a pushed frame stays on screen client-side; the heartbeat
     * re-sends well before that so the picture never blinks. */
    private static final float FRAME_TTL_SECONDS = 4.0f;
    private static final int HEARTBEAT_TICKS = 40;

    /** Rect slab depth, and how far the slab plane sits from the block centre
     * (0.5 = block face; the extra millimetres avoid z-fighting). */
    private static final double SLAB_DEPTH = 0.006;
    private static final double FACE_OFFSET = 0.507;

    /** Safety cap on shapes sent per frame. */
    private static final int MAX_RECTS = 512;

    // --- Calibration constants (see TESTING.md) ---------------------------
    /** True when the model's screen face is local +Z at yaw 0. */
    private static final boolean FRONT_IS_POSITIVE_Z = false;
    /** Sign applied to the block yaw before rotateY. */
    private static final double YAW_SIGN = 1.0;
    /** Flip when text comes out mirrored. */
    private static final boolean MIRROR_U = false;
    /** Debug shapes are assumed unit cubes centered on their matrix origin. */
    private static final boolean UNIT_CUBE_CENTERED = true;

    private static final Vector3f[] PALETTE_RGB = buildPalette();

    private final World world;
    private final int x, y, z;
    private final MonitorCanvas canvas;

    private boolean testMode;
    private int ticksUntilCheck = 0;
    private int ticksUntilHeartbeat = 0;
    private int lastRenderedHash = -1;
    private boolean loggedYaw = false;

    public MonitorDisplay(World world, int x, int y, int z, MonitorCanvas canvas) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.canvas = canvas;
    }

    /** Test hook (console /talecraft ... __displaytest): skip the block check
     * and render the pattern on all four horizontal faces at once. */
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    /** One tick; returns false when the monitor block is gone for good. */
    public boolean tick() {
        if (!testMode && --ticksUntilCheck <= 0) {
            ticksUntilCheck = BLOCK_RECHECK_TICKS;
            Boolean stillMonitor = readBlockIsMonitor();
            if (stillMonitor == null) {
                return true; // chunk not in memory: neither render nor teardown
            }
            if (!stillMonitor) {
                LOGGER.atInfo().log("monitor display at " + x + "," + y + "," + z
                        + ": block gone, stopping");
                return false;
            }
        }
        List<CanvasMesh.Rect> mesh = CanvasMesh.mesh(canvas);
        int hash = canvas.contentHash();
        if (mesh.isEmpty()) {
            lastRenderedHash = hash;
            return true; // nothing lit: last pushed frame fades out by itself
        }
        if (hash != lastRenderedHash || --ticksUntilHeartbeat <= 0) {
            if (render(mesh)) {
                lastRenderedHash = hash;
                ticksUntilHeartbeat = HEARTBEAT_TICKS;
            }
        }
        return true;
    }

    /** Stops rendering and blanks the canvas; shapes already sent expire
     * within FRAME_TTL_SECONDS. */
    public void stop() {
        canvas.clear(0);
        lastRenderedHash = -1;
        ticksUntilHeartbeat = 0;
    }

    private boolean render(List<CanvasMesh.Rect> mesh) {
        double[] yaws;
        if (testMode) {
            yaws = new double[] { 0, Math.PI / 2, Math.PI, -Math.PI / 2 };
        } else {
            Double yaw = readBlockYaw();
            if (yaw == null) {
                return false; // chunk not in memory: retry next tick
            }
            yaws = new double[] { yaw };
        }
        int sent = 0;
        for (double yaw : yaws) {
            for (CanvasMesh.Rect rect : mesh) {
                if (sent >= MAX_RECTS) {
                    LOGGER.atWarning().log("monitor at " + x + "," + y + "," + z
                            + ": frame exceeds " + MAX_RECTS + " rects, truncated");
                    return true;
                }
                DebugUtils.add(world, DebugShape.Cube, rectMatrix(rect, yaw),
                        PALETTE_RGB[rect.color() & 15], 1.0f, FRAME_TTL_SECONDS,
                        DebugUtils.FLAG_NO_WIREFRAME);
                sent++;
            }
        }
        return true;
    }

    /** Matrix placing one thin slab for the rect on the face rotated by yaw:
     * translate to block centre, rotate the face frame, translate to the
     * rect's centre in face-local coords (u right, v up, w out of screen),
     * then scale the unit cube down to the slab size. */
    private Matrix4d rectMatrix(CanvasMesh.Rect r, double yaw) {
        double sx = r.width() / (double) MonitorCanvas.WIDTH;
        double sy = r.height() / (double) MonitorCanvas.HEIGHT;
        double u = (r.x0() + r.x1() + 1) / (2.0 * MonitorCanvas.WIDTH) - 0.5;
        double v = 0.5 - (r.y0() + r.y1() + 1) / (2.0 * MonitorCanvas.HEIGHT);
        double w = FACE_OFFSET;
        if (!FRONT_IS_POSITIVE_Z) {
            w = -w;
        }
        if (MIRROR_U) {
            u = -u;
        }
        Matrix4d m = new Matrix4d()
                .translate(x + 0.5, y + 0.5, z + 0.5)
                .rotateY(YAW_SIGN * yaw);
        if (UNIT_CUBE_CENTERED) {
            m.translate(u, v, w);
        } else {
            m.translate(u - sx / 2, v - sy / 2, w - SLAB_DEPTH / 2);
        }
        return m.scale(sx, sy, SLAB_DEPTH);
    }

    /** Null when the chunk is not in memory; otherwise whether the block is a monitor. */
    private Boolean readBlockIsMonitor() {
        try {
            SectionReader reader = new SectionReader(world.getChunkStore());
            if (!reader.hasStorageInMemory(x, y, z)) {
                return null;
            }
            BlockType type = BlockType.getAssetMap().getAsset(reader.getBlock(x, y, z));
            return type != null && "TaleCraft_Monitor".equals(type.getId());
        } catch (IllegalStateException wrongThread) {
            return null;
        }
    }

    /** Null when the rotation cannot be read right now (chunk not in memory
     * or unexpected index). */
    private Double readBlockYaw() {
        try {
            SectionReader reader = new SectionReader(world.getChunkStore());
            if (!reader.hasStorageInMemory(x, y, z)) {
                return null;
            }
            int rotationIndex = reader.getRotationIndex(x, y, z);
            double yaw = RotationTuple.get(rotationIndex).yaw().getRadians();
            if (!loggedYaw) {
                loggedYaw = true;
                LOGGER.atInfo().log("monitor at " + x + "," + y + "," + z
                        + ": rotationIndex=" + rotationIndex + " yawDeg="
                        + Math.round(Math.toDegrees(yaw)));
            }
            return yaw;
        } catch (Exception e) {
            return null;
        }
    }

    private static Vector3f[] buildPalette() {
        Vector3f[] palette = new Vector3f[MonitorCanvas.PALETTE_HEX.length];
        for (int i = 0; i < palette.length; i++) {
            int rgb = Integer.parseInt(MonitorCanvas.PALETTE_HEX[i].substring(1), 16);
            palette[i] = new Vector3f(((rgb >> 16) & 0xff) / 255f,
                    ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f);
        }
        return palette;
    }
}

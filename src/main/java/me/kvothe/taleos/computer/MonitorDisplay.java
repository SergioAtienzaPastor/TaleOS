package me.kvothe.taleos.computer;

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

/** Renders one shared canvas, cropped over the front face of each structure block. */
public final class MonitorDisplay {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BLOCK_RECHECK_TICKS = 40;
    private static final float FRAME_TTL_SECONDS =
            DisplayFramePacer.FRAME_TTL_TICKS / 20.0f - 0.01f;
    private static final double SLAB_DEPTH = 0.006;
    private static final double FACE_OFFSET = 0.507;
    private static final int MAX_RECTS_PER_BLOCK = 512;
    private static final boolean FRONT_IS_POSITIVE_Z = true;
    private static final double YAW_SIGN = 1.0;
    private static final boolean MIRROR_U = false;
    private static final boolean UNIT_CUBE_CENTERED = true;
    private static final Vector3f[] PALETTE_RGB = buildPalette();

    private final World world;
    private final MonitorMultiblock.Structure structure;
    private final MonitorCanvas canvas;
    private final DisplayFramePacer frames = new DisplayFramePacer();
    private final boolean legacyRotation;

    private boolean testMode;
    private int ticksUntilCheck;

    public MonitorDisplay(World world, int x, int y, int z, MonitorCanvas canvas) {
        this(world, new MonitorMultiblock.Structure(new MonitorMultiblock.Pos(x, y, z), 1, 1,
                MonitorMultiblock.Facing.SOUTH,
                List.of(new MonitorMultiblock.Pos(x, y, z))), canvas, true);
    }

    public MonitorDisplay(World world, MonitorMultiblock.Structure structure, MonitorCanvas canvas) {
        this(world, structure, canvas, false);
    }

    private MonitorDisplay(World world, MonitorMultiblock.Structure structure, MonitorCanvas canvas,
            boolean legacyRotation) {
        this.world = world;
        this.structure = structure;
        this.canvas = canvas;
        this.legacyRotation = legacyRotation;
    }

    public MonitorMultiblock.Structure getStructure() {
        return structure;
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    /** Unloaded chunks are skipped and never cause teardown. */
    public boolean tick() {
        if (!testMode && --ticksUntilCheck <= 0) {
            ticksUntilCheck = BLOCK_RECHECK_TICKS;
            Boolean matches = loadedBlocksMatch();
            if (Boolean.FALSE.equals(matches)) {
                return false;
            }
        }
        long revision = canvas.getRevision();
        boolean blank = true;
        for (MonitorMultiblock.Pos pos : structure.blocks()) {
            if (loaded(pos) && !CanvasMesh.mesh(canvas, structure.pixelX(pos), structure.pixelY(pos),
                    MonitorCanvas.WIDTH, MonitorCanvas.HEIGHT).isEmpty()) {
                blank = false;
                break;
            }
        }
        if (frames.needsFrame(revision, blank)) {
            boolean rendered = false;
            for (MonitorMultiblock.Pos pos : structure.blocks()) {
                rendered |= renderBlock(pos);
            }
            if (rendered || blank) {
                frames.onRendered(revision);
            }
        }
        return true;
    }

    /** Already-sent shapes expire within their short TTL. */
    public void stop() {
        frames.reset();
    }

    private boolean renderBlock(MonitorMultiblock.Pos pos) {
        double[] yaws;
        if (testMode) {
            yaws = new double[] { 0, Math.PI / 2, Math.PI, -Math.PI / 2 };
        } else {
            Double yaw = legacyRotation ? readBlockYaw(pos) : readMatchingYaw(pos);
            if (yaw == null) {
                return false;
            }
            yaws = new double[] { yaw };
        }
        List<CanvasMesh.Rect> mesh = CanvasMesh.mesh(canvas, structure.pixelX(pos),
                structure.pixelY(pos), MonitorCanvas.WIDTH, MonitorCanvas.HEIGHT);
        int sent = 0;
        for (double yaw : yaws) {
            for (CanvasMesh.Rect rect : mesh) {
                if (sent >= MAX_RECTS_PER_BLOCK) {
                    LOGGER.atWarning().log("monitor block frame exceeds " + MAX_RECTS_PER_BLOCK
                            + " rects, truncated");
                    return true;
                }
                DebugUtils.add(world, DebugShape.Cube, rectMatrix(pos, rect, yaw),
                        PALETTE_RGB[rect.color() & 15], 1.0f, FRAME_TTL_SECONDS,
                        DebugUtils.FLAG_NO_WIREFRAME);
                sent++;
            }
        }
        return true;
    }

    record LocalRect(double u, double v, double width, double height) {
    }

    static LocalRect localRect(MonitorMultiblock.Structure structure, MonitorMultiblock.Pos pos,
            CanvasMesh.Rect r) {
        double width = r.width() / (double) MonitorCanvas.WIDTH;
        double height = r.height() / (double) MonitorCanvas.HEIGHT;
        double u = (r.x0() + r.x1() + 1) / (2.0 * MonitorCanvas.WIDTH)
                - structure.blockX(pos) - 0.5;
        double v = 0.5 - (r.y0() + r.y1() + 1) / (2.0 * MonitorCanvas.HEIGHT)
                + structure.blockY(pos);
        return new LocalRect(u, v, width, height);
    }

    private Matrix4d rectMatrix(MonitorMultiblock.Pos pos, CanvasMesh.Rect r, double yaw) {
        LocalRect local = localRect(structure, pos, r);
        double sx = local.width();
        double sy = local.height();
        double u = local.u();
        double v = local.v();
        double w = FRONT_IS_POSITIVE_Z ? FACE_OFFSET : -FACE_OFFSET;
        if (MIRROR_U) {
            u = -u;
        }
        Matrix4d matrix = new Matrix4d()
                .translate(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5)
                .rotateY(YAW_SIGN * yaw);
        if (UNIT_CUBE_CENTERED) {
            matrix.translate(u, v, w);
        } else {
            matrix.translate(u - sx / 2, v - sy / 2, w - SLAB_DEPTH / 2);
        }
        return matrix.scale(sx, sy, SLAB_DEPTH);
    }

    /** Null means at least one loaded block is valid; only a loaded mismatch is false. */
    private Boolean loadedBlocksMatch() {
        for (MonitorMultiblock.Pos pos : structure.blocks()) {
            BlockState state = read(pos);
            if (state != null && (!state.monitor() || state.facing() != structure.facing())) {
                return false;
            }
        }
        return true;
    }

    private boolean loaded(MonitorMultiblock.Pos pos) {
        try {
            return new SectionReader(world.getChunkStore()).hasStorageInMemory(pos.x(), pos.y(), pos.z());
        } catch (IllegalStateException wrongThread) {
            return false;
        }
    }

    private Double readMatchingYaw(MonitorMultiblock.Pos pos) {
        BlockState state = read(pos);
        if (state == null || !state.monitor() || state.facing() != structure.facing()) {
            return null;
        }
        return state.yaw();
    }

    private Double readBlockYaw(MonitorMultiblock.Pos pos) {
        BlockState state = read(pos);
        return state == null || !state.monitor() ? null : state.yaw();
    }

    private BlockState read(MonitorMultiblock.Pos pos) {
        try {
            SectionReader reader = new SectionReader(world.getChunkStore());
            if (!reader.hasStorageInMemory(pos.x(), pos.y(), pos.z())) {
                return null;
            }
            BlockType type = BlockType.getAssetMap().getAsset(reader.getBlock(pos.x(), pos.y(), pos.z()));
            boolean monitor = type != null && "TaleOS_Monitor".equals(type.getId());
            double yaw = RotationTuple.get(reader.getRotationIndex(pos.x(), pos.y(), pos.z()))
                    .yaw().getRadians();
            return new BlockState(monitor, MonitorMultiblock.Facing.fromYaw(yaw), yaw);
        } catch (Exception e) {
            return null;
        }
    }

    private record BlockState(boolean monitor, MonitorMultiblock.Facing facing, double yaw) {
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

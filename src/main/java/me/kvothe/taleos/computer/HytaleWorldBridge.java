package me.kvothe.taleos.computer;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import me.kvothe.taleos.lua.WorldBridge;

/**
 * WorldBridge backed by a real Hytale world. Block reads go through a
 * {@link SectionReader} over in-memory sections only, so scripts never
 * trigger chunk loads from the tick thread.
 */
public final class HytaleWorldBridge implements WorldBridge {

    private final World world;
    private final java.util.function.Supplier<BlockPosition> origin;
    private final int computerId;
    private final ComputerManager manager;
    private final me.kvothe.taleos.turtle.TurtleController turtle;
    private final HytalePeripheralProvider peripheralProvider;

    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    public HytaleWorldBridge(World world, BlockPosition computerPos, int computerId,
            ComputerManager manager) {
        this(world, computerPos, computerId, manager, null);
    }

    public HytaleWorldBridge(World world, BlockPosition computerPos, int computerId,
            ComputerManager manager, me.kvothe.taleos.turtle.TurtleController turtle) {
        this.world = world;
        this.origin = () -> computerPos;
        this.computerId = computerId;
        this.manager = manager;
        this.turtle = turtle;
        this.peripheralProvider = new HytalePeripheralProvider(world, this.origin, manager, turtle);
    }

    public HytaleWorldBridge(World world, java.util.function.Supplier<BlockPosition> computerPos,
            int computerId, ComputerManager manager, java.util.function.Supplier<Computer> computer) {
        this.world = world;
        this.origin = computerPos;
        this.computerId = computerId;
        this.manager = manager;
        this.turtle = null;
        this.peripheralProvider = new HytalePeripheralProvider(world, computerPos, manager, computer);
    }

    @Override
    public me.kvothe.taleos.lua.TurtleBridge turtle() {
        return turtle;
    }

    @Override
    public HytalePeripheralProvider peripherals() {
        return peripheralProvider;
    }

    private BlockPosition origin() {
        BlockPosition position = origin.get();
        return position != null ? position : new BlockPosition(0, 0, 0);
    }

    public int getOriginX() {
        return origin().x;
    }

    public int getOriginY() {
        return origin().y;
    }

    public int getOriginZ() {
        return origin().z;
    }

    @Override
    public String getBlockId(int x, int y, int z) {
        try {
            SectionReader reader = new SectionReader(world.getChunkStore());
            if (!reader.hasStorageInMemory(x, y, z)) {
                return null;
            }
            BlockType type = BlockType.getAssetMap().getAsset(reader.getBlock(x, y, z));
            if (type == null || "Empty".equals(type.getId())) {
                return null; // air reads as the "Empty" asset; scripts expect null for air
            }
            return type.getId();
        } catch (IllegalStateException wrongThread) {
            // World stores assert thread affinity; a stray call must never
            // crash the world thread — report "unloaded" instead.
            LOGGER.atWarning().log("block read off the world thread at " + x + "," + y + "," + z);
            return null;
        }
    }

    @Override
    public double getTime() {
        return System.currentTimeMillis() / 1000.0;
    }

    @Override
    public void chat(String message) {
        world.sendMessage(Message.raw("[computer #" + computerId + "] " + message));
    }

    @Override
    public MonitorCanvas monitorAt(int dx, int dy, int dz) {
        if (manager == null) {
            return null;
        }
        BlockPosition origin = origin();
        if (dx == 0 && dy == 0 && dz == 0) {
            // No offset: search the six face neighbours for a monitor block.
            int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
                    { 0, 0, -1 } };
            for (int[] d : dirs) {
                MonitorCanvas canvas = canvasIfMonitor(origin.x + d[0], origin.y + d[1], origin.z + d[2]);
                if (canvas != null) {
                    return canvas;
                }
            }
            return null;
        }
        return canvasIfMonitor(origin.x + dx, origin.y + dy, origin.z + dz);
    }

    private MonitorCanvas canvasIfMonitor(int x, int y, int z) {
        if (!"TaleOS_Monitor".equals(getBlockId(x, y, z))) {
            return null;
        }
        return manager.canvasAt(world, new BlockPosition(x, y, z));
    }

    /** Console-side diagnostics for the block-read chain. Forces an async chunk
     * load so the probe works with zero players online. Logs to the server log. */
    public static void debugProbe(World world, int x, int y, int z) {
        StringBuilder sb = new StringBuilder("[TaleOS probe] ");
        try {
            ChunkStore cs = world.getChunkStore();
            sb.append("world=").append(world.getName()).append(" cubic=").append(cs.supportsCubicSections());
            long idx = ChunkUtil.indexChunkFromBlock(x, z);
            var ref0 = cs.getChunkReference(idx);
            sb.append(" chunkRef(before)=").append(ref0 == null ? "null" : ref0.isValid() ? "valid" : "invalid");
            if (ref0 == null || !ref0.isValid()) {
                try {
                    var loaded = cs.getChunkReferenceAsync(idx).get(15, java.util.concurrent.TimeUnit.SECONDS);
                    sb.append(" asyncLoad=").append(loaded != null && loaded.isValid());
                } catch (Exception e) {
                    sb.append(" loadError=").append(e.getClass().getSimpleName());
                }
            }
            var ref1 = cs.getChunkReference(idx);
            sb.append(" chunkRef(after)=").append(ref1 == null ? "null" : ref1.isValid() ? "valid" : "invalid");
            var sY = cs.getChunkSectionReferenceAtBlock(x, y, z);
            sb.append(" sectionRef(y=").append(y).append(")=").append(sY == null ? "null" : sY.isValid() ? "valid" : "invalid");
            var s0 = cs.getChunkSectionReferenceAtBlock(x, 0, z);
            sb.append(" sectionRef(y=0)=").append(s0 == null ? "null" : s0.isValid() ? "valid" : "invalid");
            sb.append(" sectionsInColumn=").append(
                    cs.getLoadedSectionReferences(ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(z)).size());
        } catch (Throwable t) {
            sb.append(" ERROR=").append(t);
        }
        LOGGER.atInfo().log(sb.toString());
    }
}

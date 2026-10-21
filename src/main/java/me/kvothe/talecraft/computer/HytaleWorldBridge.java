package me.kvothe.talecraft.computer;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import me.kvothe.talecraft.lua.WorldBridge;

/**
 * WorldBridge backed by a real Hytale world. Block reads go through a
 * {@link SectionReader} over in-memory sections only, so scripts never
 * trigger chunk loads from the tick thread.
 */
public final class HytaleWorldBridge implements WorldBridge {

    private final World world;
    private final int originX, originY, originZ;
    private final int computerId;

    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    public HytaleWorldBridge(World world, BlockPosition computerPos, int computerId) {
        this.world = world;
        this.originX = computerPos.x;
        this.originY = computerPos.y;
        this.originZ = computerPos.z;
        this.computerId = computerId;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
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

    /** Console-side diagnostics for the block-read chain. Forces an async chunk
     * load so the probe works with zero players online. Logs to the server log. */
    public static void debugProbe(World world, int x, int y, int z) {
        StringBuilder sb = new StringBuilder("[TaleCraft probe] ");
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

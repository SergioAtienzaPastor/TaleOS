package me.kvothe.talecraft.computer;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;

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
            return type == null ? null : type.getId();
        } catch (IllegalStateException wrongThread) {
            // World stores assert thread affinity; a stray call must never
            // crash the world thread — report "unloaded" instead.
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
}

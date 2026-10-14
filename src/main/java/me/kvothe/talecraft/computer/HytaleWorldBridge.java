package me.kvothe.talecraft.computer;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import me.kvothe.talecraft.lua.WorldBridge;

/**
 * WorldBridge backed by a real Hytale world. Block reads go through
 * {@code getChunkIfLoaded} so scripts never trigger chunk loads from the
 * tick thread.
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
        int cx = ChunkUtil.chunkCoordinate(x);
        int cz = ChunkUtil.chunkCoordinate(z);
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexColumn(cx, cz));
        if (chunk == null) {
            return null;
        }
        BlockType type = chunk.getBlockType(x, y, z);
        return type == null ? null : type.getId();
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

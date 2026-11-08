package me.kvothe.taleos.turtle;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import me.kvothe.taleos.computer.ComputerManager;
import me.kvothe.taleos.turtle.ChunkLoaderStore.Location;

public final class TurtleChunkLoaderManager {
    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    private final ComputerManager computers;
    private final ChunkLoaderStore store;
    private final Set<String> heldChunks = new HashSet<>();
    private final Set<String> pendingChunks = new HashSet<>();
    private final Set<Integer> pendingRestore = new HashSet<>();

    public TurtleChunkLoaderManager(ComputerManager computers, java.nio.file.Path file) {
        this.computers = computers;
        this.store = new ChunkLoaderStore(file);
    }

    public synchronized void load() throws IOException {
        store.load();
        pendingRestore.clear();
        pendingRestore.addAll(store.entries().keySet());
    }

    public synchronized void tickWorld(World world) {
        for (Map.Entry<Integer, Location> entry : store.entries().entrySet()) {
            Location location = entry.getValue();
            if (!location.world().equals(world.getName())) {
                continue;
            }
            String chunk = chunkKey(location);
            ensureChunk(world, chunk);
            if (pendingRestore.contains(entry.getKey()) && heldChunks.contains(chunk)) {
                restoreTurtle(world, entry.getKey(), location);
            }
        }
        for (TurtleController turtle : computers.turtlesInWorld(world)) {
            sync(turtle);
        }
    }

    public synchronized void sync(TurtleController turtle) {
        int id = turtle.getComputerId();
        Location previous = store.get(id);
        if (turtle.getInventory() == null) {
            if (previous != null && previous.world().equals(turtle.getWorld().getName())) {
                ensureChunk(turtle.getWorld(), chunkKey(previous));
            }
            return;
        }
        if (!turtle.hasUpgradeAnywhere(TurtleUpgradeRegistry.Kind.CHUNK_LOADER)) {
            forget(id);
            return;
        }
        BlockPosition position = turtle.getPosition();
        Location current = new Location(turtle.getWorld().getName(), position.x, position.y, position.z);
        if (!current.equals(previous)) {
            store.put(id, current);
            save();
            if (previous != null) {
                releaseIfUnused(previous);
            }
        }
        ensureChunk(turtle.getWorld(), chunkKey(current));
    }

    public synchronized void forget(int computerId) {
        Location removed = store.remove(computerId);
        pendingRestore.remove(computerId);
        if (removed != null) {
            save();
            releaseIfUnused(removed);
        }
    }

    public synchronized int activeCount() {
        return store.entries().size();
    }

    public synchronized boolean isChunkHeld(World world, BlockPosition position) {
        String chunk = chunkKey(world.getName(), position.x, position.z);
        if (!heldChunks.contains(chunk)) {
            return false;
        }
        Ref<ChunkStore> reference = world.getChunkStore().getChunkReference(
                ChunkUtil.indexChunkFromBlock(position.x, position.z));
        WorldChunk worldChunk = worldChunk(world, reference);
        return worldChunk != null && worldChunk.shouldKeepLoaded();
    }

    public synchronized void shutdown() {
        for (String chunk : Set.copyOf(heldChunks)) {
            World world = computers.worldByName(worldName(chunk));
            if (world != null) {
                long index = Long.parseLong(chunk.substring(chunk.lastIndexOf(':') + 1));
                releaseChunk(world, index, chunk);
            }
        }
        heldChunks.clear();
        pendingChunks.clear();
        pendingRestore.clear();
    }

    private void restoreTurtle(World world, int id, Location location) {
        pendingRestore.remove(id);
        SectionReader reader = new SectionReader(world.getChunkStore());
        if (!reader.hasStorageInMemory(location.x(), location.y(), location.z())) {
            pendingRestore.add(id);
            return;
        }
        BlockType type = BlockType.getAssetMap().getAsset(
                reader.getBlock(location.x(), location.y(), location.z()));
        if (type == null || !TurtleBlockIds.isTurtleBlockId(type.getId())) {
            forget(id);
            return;
        }
        try {
            TurtleController turtle = computers.turtleControllerAt(world,
                    new BlockPosition(location.x(), location.y(), location.z()));
            sync(turtle);
        } catch (RuntimeException exception) {
            LOGGER.atWarning().log("cannot restore Turtle chunk loader #" + id + ": "
                    + exception.getMessage());
        }
    }

    private void ensureChunk(World world, String chunk) {
        if (heldChunks.contains(chunk) || pendingChunks.contains(chunk)) {
            return;
        }
        long index = chunkIndex(chunk);
        Ref<ChunkStore> reference = world.getChunkStore().getChunkReference(index);
        if (worldChunk(world, reference) != null) {
            holdChunk(world, reference, chunk);
            return;
        }
        pendingChunks.add(chunk);
        try {
            world.getChunkStore().getChunkReferenceAsync(index).thenAccept(loaded ->
                    world.execute(() -> completeChunkLoad(world, loaded, chunk)))
                    .exceptionally(failure -> {
                        world.execute(() -> failChunkLoad(world, chunk, failure));
                        return null;
                    });
        } catch (RuntimeException exception) {
            failChunkLoad(world, chunk, exception);
        }
    }

    private synchronized void completeChunkLoad(World world, Ref<ChunkStore> reference, String chunk) {
        pendingChunks.remove(chunk);
        if (usersOfChunk(chunk) == 0 || heldChunks.contains(chunk)) {
            return;
        }
        if (worldChunk(world, reference) != null) {
            holdChunk(world, reference, chunk);
        }
    }

    private synchronized void failChunkLoad(World world, String chunk, Throwable failure) {
        pendingChunks.remove(chunk);
        if (usersOfChunk(chunk) > 0) {
            LOGGER.atWarning().log("cannot load Turtle chunk " + chunk + ": " + failure.getMessage());
        }
    }

    private void holdChunk(World world, Ref<ChunkStore> reference, String chunk) {
        WorldChunk worldChunk = worldChunk(world, reference);
        if (worldChunk == null || heldChunks.contains(chunk) || usersOfChunk(chunk) == 0) {
            return;
        }
        worldChunk.addKeepLoaded();
        heldChunks.add(chunk);
    }

    private void releaseIfUnused(Location location) {
        String chunk = chunkKey(location);
        if (usersOfChunk(chunk) > 0 || !heldChunks.contains(chunk)) {
            return;
        }
        World world = computers.worldByName(location.world());
        if (world != null) {
            releaseChunk(world, chunkIndex(chunk), chunk);
        } else {
            heldChunks.remove(chunk);
        }
    }

    private void releaseChunk(World world, long index, String chunk) {
        Ref<ChunkStore> reference = world.getChunkStore().getChunkReference(index);
        WorldChunk worldChunk = worldChunk(world, reference);
        if (worldChunk != null) {
            worldChunk.removeKeepLoaded();
        }
        heldChunks.remove(chunk);
    }

    private int usersOfChunk(String chunk) {
        int users = 0;
        for (Location location : store.entries().values()) {
            if (chunkKey(location).equals(chunk)) {
                users++;
            }
        }
        return users;
    }

    private void save() {
        try {
            store.save();
        } catch (IOException exception) {
            LOGGER.atWarning().log("cannot persist Turtle chunk loaders: " + exception.getMessage());
        }
    }

    private static WorldChunk worldChunk(World world, Ref<ChunkStore> reference) {
        return reference == null || !reference.isValid() ? null
                : world.getChunkStore().getStore().getComponent(reference, WorldChunk.getComponentType());
    }

    private static String chunkKey(Location location) {
        return chunkKey(location.world(), location.x(), location.z());
    }

    private static String chunkKey(String world, int x, int z) {
        return world + ":" + ChunkUtil.indexChunkFromBlock(x, z);
    }

    private static long chunkIndex(String chunk) {
        return Long.parseLong(chunk.substring(chunk.lastIndexOf(':') + 1));
    }

    private static String worldName(String chunk) {
        return chunk.substring(0, chunk.lastIndexOf(':'));
    }
}

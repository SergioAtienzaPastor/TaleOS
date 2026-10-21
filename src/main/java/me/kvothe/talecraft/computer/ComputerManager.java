package me.kvothe.talecraft.computer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.lua.WorldBridge;
import me.kvothe.talecraft.ui.TerminalPage;

/**
 * Registry of all live computers, keyed {@code "world:x:y:z"}, plus the id
 * store and the open terminal pages that need per-tick refreshes. All data
 * lives under {@code mods/TaleCraft/} relative to the server working dir.
 */
public final class ComputerManager {

    private final Path dataDir;
    private final ComputerIdStore idStore;
    private final Map<String, Computer> computers = new ConcurrentHashMap<>();
    private final Map<UUID, TerminalPage> openPages = new ConcurrentHashMap<>();
    private volatile long lastTickNanos;

    public ComputerManager(Path dataDir) {
        this.dataDir = dataDir;
        this.idStore = new ComputerIdStore(dataDir.resolve("ids.json"));
    }

    public void load() {
        try {
            idStore.load();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + idStore, e);
        }
    }

    /** Boots (or returns) the computer at the given block. A null world means
     * "no world context" (headless self-test): the machine gets WorldBridge.NONE. */
    public Computer bootAt(World world, BlockPosition pos) {
        String worldName = world != null ? world.getName() : "world";
        String key = key(worldName, pos.x, pos.y, pos.z);
        if (world != null) {
            // Computers booted while the world was unresolvable live under the
            // legacy literal "world" key: adopt them so ids/disks stay stable.
            String legacyKey = key("world", pos.x, pos.y, pos.z);
            if (!legacyKey.equals(key)) {
                idStore.adopt(legacyKey, key);
                Computer migrated = computers.remove(legacyKey);
                if (migrated != null) {
                    computers.putIfAbsent(key, migrated);
                }
            }
        }
        return computers.compute(key, (k, existing) -> {
            if (existing != null && existing.isAlive()) {
                if (world != null) {
                    // Idempotent upgrade: a machine booted headless gets the
                    // real world bridge as soon as one is available.
                    existing.getMachine().setWorldBridge(
                            new HytaleWorldBridge(world, pos, existing.getId()));
                }
                return existing;
            }
            int id = idStore.idFor(k);
            if (world == null) {
                System.err.println("[TaleCraft] WARN: booting computer at " + k
                        + " without world context: tale.* world calls will no-op");
            }
            WorldBridge bridge = world == null
                    ? WorldBridge.NONE
                    : new HytaleWorldBridge(world, pos, id);
            try {
                Computer c = new Computer(id, dataDir.resolve("computers").resolve(String.valueOf(id)), bridge);
                c.boot();
                return c;
            } catch (IOException e) {
                throw new IllegalStateException("cannot boot computer at " + k, e);
            }
        });
    }

    /** Ticks every live machine once and refreshes open terminal pages.
     * The ECS tick system runs once per world store; the time-window guard
     * makes the pass idempotent so machines tick exactly once per server tick. */
    public void tickAll() {
        long now = System.nanoTime();
        if (now - lastTickNanos < 40_000_000L) {
            return;
        }
        lastTickNanos = now;
        computers.values().removeIf(c -> {
            if (c.isHalted()) {
                // reboot is handled by the next interaction; drop the corpse.
                return true;
            }
            c.tick();
            return false;
        });
        for (TerminalPage page : openPages.values()) {
            page.refreshFromComputer();
        }
    }

    public void registerPage(UUID player, TerminalPage page) {
        openPages.put(player, page);
    }

    public boolean isRunningAt(World world, BlockPosition pos) {
        Computer c = computers.get(key(world.getName(), pos.x, pos.y, pos.z));
        return c != null && c.isAlive();
    }

    /** First alive computer in the 6-neighbourhood of the position, or null. */
    public Computer findAdjacentRunning(World world, BlockPosition pos) {
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] d : dirs) {
            Computer c = computers.get(key(world.getName(), pos.x + d[0], pos.y + d[1], pos.z + d[2]));
            if (c != null && c.isAlive()) {
                return c;
            }
        }
        return null;
    }

    public void unregisterPage(UUID player) {
        openPages.remove(player);
    }

    /** Persists ids; machine state is volatile by design (see Computer). */
    public void shutdown() {
        try {
            idStore.save();
        } catch (IOException e) {
            System.err.println("[TaleCraft] failed to save ids.json: " + e.getMessage());
        }
        computers.clear();
        openPages.clear();
    }

    public String key(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    /** Resolves the World for an entity-store accessor (page callbacks don't
     * get it directly). Null when it cannot be resolved (headless testing). */
    public static World worldOf(ComponentAccessor<EntityStore> accessor) {
        try {
            if (accessor instanceof Store<?> store
                    && store.getExternalData() instanceof EntityStore entityStore) {
                return entityStore.getWorld();
            }
        } catch (Exception ignored) {
            // best effort
        }
        return null;
    }

    /** Resolves the World a player is in. Interaction accessors are often not
     * the Store itself, so this is the reliable path from page suppliers. */
    public static World worldOf(PlayerRef playerRef) {
        try {
            UUID worldUuid = playerRef.getWorldUuid();
            if (worldUuid != null) {
                World w = Universe.get().getWorld(worldUuid);
                if (w != null) {
                    return w;
                }
            }
        } catch (Exception ignored) {
            // best effort
        }
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()
                    && ref.getStore().getExternalData() instanceof EntityStore entityStore) {
                return entityStore.getWorld();
            }
        } catch (Exception ignored) {
            // best effort
        }
        return null;
    }
}

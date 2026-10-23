package me.kvothe.talecraft.computer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import me.kvothe.talecraft.lua.WorldBridge;
import me.kvothe.talecraft.ui.EditorPage;
import me.kvothe.talecraft.ui.MonitorCanvasPage;
import me.kvothe.talecraft.ui.TerminalPage;

/**
 * Registry of all live computers, keyed {@code "world:x:y:z"}, plus the id
 * store and the open terminal pages that need per-tick refreshes. All data
 * lives under {@code mods/TaleCraft/} relative to the server working dir.
 */
public final class ComputerManager {

    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    private final Path dataDir;
    private final ComputerIdStore idStore;
    private final Map<String, Computer> computers = new ConcurrentHashMap<>();
    private final Map<UUID, TerminalPage> openPages = new ConcurrentHashMap<>();
    private final Map<String, MonitorCanvas> canvases = new ConcurrentHashMap<>();
    private final Map<UUID, MonitorCanvasPage> openMonitorPages = new ConcurrentHashMap<>();
    private final Map<String, MonitorDisplay> displays = new ConcurrentHashMap<>();
    /** Worlds with a pending display-entity cleanup (set from any thread,
     * executed on the world's tick thread). */
    private final java.util.Set<String> pendingDisplayCleanup =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private int worldTickCounter;

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
        Computer computer = computers.compute(key, (k, existing) -> {
            if (existing != null && existing.isAlive()) {
                if (world != null) {
                    // Idempotent upgrade: a machine booted headless gets the
                    // real world bridge as soon as one is available.
                    existing.getMachine().setWorldBridge(
                            new HytaleWorldBridge(world, pos, existing.getId(), this));
                }
                return existing;
            }
            int id = idStore.idFor(k);
            LOGGER.atInfo().log("boot computer #" + id + " at " + k);
            if (world == null) {
                LOGGER.atWarning().log("booting computer at " + k
                        + " without world context: tale.* world calls will no-op");
            }
            WorldBridge bridge = world == null
                    ? WorldBridge.NONE
                    : new HytaleWorldBridge(world, pos, id, this);
            try {
                Computer c = new Computer(id, dataDir.resolve("computers").resolve(String.valueOf(id)), bridge);
                c.boot();
                return c;
            } catch (IOException e) {
                throw new IllegalStateException("cannot boot computer at " + k, e);
            }
        });
        computer.setEditorOpener(this::openEditor);
        return computer;
    }

    /** Opens the editor page for a computer file, for the player who typed the
     * edit command (or any player viewing that computer's terminal). Called on
     * the world tick thread from Computer.tick. */
    public void openEditor(Computer computer, String path, UUID requestedBy) {
        PlayerRef playerRef = requestedBy != null ? Universe.get().getPlayer(requestedBy) : null;
        if (playerRef == null) {
            for (Map.Entry<UUID, TerminalPage> e : openPages.entrySet()) {
                if (e.getValue().getComputer() == computer) {
                    playerRef = Universe.get().getPlayer(e.getKey());
                    if (playerRef != null) {
                        break;
                    }
                }
            }
        }
        if (playerRef == null) {
            computer.systemMessage("edit: no hay ningún jugador con el terminal abierto");
            return;
        }
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                computer.systemMessage("edit: jugador no disponible");
                return;
            }
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                computer.systemMessage("edit: jugador no disponible");
                return;
            }
            EditorPage page = new EditorPage(playerRef, this, computer, path);
            // The editor replaces the terminal page; stop refreshing the old one.
            unregisterPage(playerRef.getUuid());
            player.getPageManager().openCustomPage(ref, store, page);
        } catch (Exception e) {
            LOGGER.atWarning().log("cannot open editor: " + e.getMessage());
            computer.systemMessage("edit: no se pudo abrir el editor");
        }
    }

    /** Ticks the computers of ONE world (called from that world's tick thread)
     * and refreshes the open terminal pages. World stores assert thread
     * affinity, so a machine must never be ticked from another world's thread. */
    public void tickWorld(World world) {
        String prefix = world.getName() + ":";
        if (pendingDisplayCleanup.remove(world.getName())) {
            cleanDisplayEntities(world);
        }
        // Slow background sweep: the mannequins left by the spawn bisect only
        // become visible to the store when their chunk loads (a player walking
        // by), so a one-shot cleanup misses them. Touches loaded entities only.
        if (++worldTickCounter % 400 == 0) {
            cleanDisplayEntities(world);
        }
        computers.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) {
                return false;
            }
            Computer c = entry.getValue();
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
        for (MonitorCanvasPage page : openMonitorPages.values()) {
            page.refreshFromCanvas();
        }
        // In-world monitor readouts (DisplayDebug shapes); a display whose
        // monitor block vanished removes itself from the map.
        displays.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) {
                return false;
            }
            return !entry.getValue().tick();
        });
    }

    /** Drawable canvas of the monitor block at the position (created on first
     * use; volatile by design, like the computers' transcript). Also registers
     * the in-world display renderer for the block. */
    public MonitorCanvas canvasAt(World world, BlockPosition pos) {
        String key = key(world.getName(), pos.x, pos.y, pos.z);
        MonitorCanvas canvas = canvases.computeIfAbsent(key, k -> new MonitorCanvas());
        displays.computeIfAbsent(key,
                k -> new MonitorDisplay(world, pos.x, pos.y, pos.z, canvas));
        return canvas;
    }

    /** Console test hook: paints a canned pattern on the display at the
     * position, rendered on all four horizontal faces at once (calibration:
     * the red corner marker lands on a different physical corner per face).
     * Does not require a real monitor block. */
    public void displayTest(World world, BlockPosition pos) {
        String key = key(world.getName(), pos.x, pos.y, pos.z);
        MonitorCanvas canvas = canvases.computeIfAbsent(key, k -> new MonitorCanvas());
        canvas.clear(0);
        canvas.fill(0, 0, 2, 2, 8); // red 2x2 top-left corner marker
        canvas.fill(0, 0, MonitorCanvas.WIDTH, 1, 15);
        canvas.writeText(3, 3, "TALECRAFT", 4);
        canvas.writeText(3, 10, "12:34:56", 10);
        canvas.fill(0, MonitorCanvas.HEIGHT - 1, MonitorCanvas.WIDTH, 1, 3);
        MonitorDisplay display = displays.computeIfAbsent(key,
                k -> new MonitorDisplay(world, pos.x, pos.y, pos.z, canvas));
        display.setTestMode(true);
    }

    /** Console test hook: stops the test display/canvas at the position. */
    public void displayClear(World world, BlockPosition pos) {
        String key = key(world.getName(), pos.x, pos.y, pos.z);
        MonitorDisplay display = displays.remove(key);
        if (display != null) {
            display.stop();
        }
        canvases.remove(key);
    }

    /** Schedules a cleanup of leftover display NPCs (runs on the world's tick
     * thread; entity stores assert thread affinity). */
    public void requestDisplayCleanup(World world) {
        pendingDisplayCleanup.add(world.getName());
    }

    /** Removes leftover display NPCs from the pre-DisplayDebug renderer era:
     * the vanilla Empty_Role mannequins from the spawn bisect and the
     * TaleCraft_MonitorDisplay nameplate carriers. Both roles are orphans
     * forever (nothing spawns them anymore), so periodic sweeps and the
     * manual console cleanup remove both. Only loaded entities are touched —
     * the mannequins become visible to the store when their chunk loads (a
     * player walking by), so the periodic sweep repeats every 400 ticks.
     * Returns the count. */
    private int cleanDisplayEntities(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        int[] removed = { 0 };
        try {
            store.forEachChunk(Query.and(NPCEntity.getComponentType()), (chunk, cmd) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                    if (npc == null) {
                        continue;
                    }
                    String role = npc.getRoleName();
                    if ("Empty_Role".equals(role) || "TaleCraft_MonitorDisplay".equals(role)) {
                        cmd.removeEntity(chunk.getReferenceTo(i), RemoveReason.REMOVE);
                        removed[0]++;
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.atWarning().log("display cleanup failed: " + e.getMessage());
        }
        if (removed[0] > 0) {
            LOGGER.atInfo().log("removed " + removed[0] + " leftover display entities in "
                    + world.getName());
        }
        return removed[0];
    }

    public void registerMonitorPage(UUID player, MonitorCanvasPage page) {
        openMonitorPages.put(player, page);
    }

    public void unregisterMonitorPage(UUID player) {
        openMonitorPages.remove(player);
    }

    public void registerPage(UUID player, TerminalPage page) {
        openPages.put(player, page);
    }

    public boolean isRunningAt(World world, BlockPosition pos) {
        Computer c = computers.get(key(world.getName(), pos.x, pos.y, pos.z));
        return c != null && c.isAlive();
    }

    /** First alive computer near the position: the 6 face neighbours first,
     * then the rest of a ±2 cube (monitors read a computer "close by", so a
     * small gap in the user's setup still works). */
    public Computer findAdjacentRunning(World world, BlockPosition pos) {
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] d : dirs) {
            Computer c = computers.get(key(world.getName(), pos.x + d[0], pos.y + d[1], pos.z + d[2]));
            if (c != null && c.isAlive()) {
                return c;
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 1) {
                        continue; // already checked above
                    }
                    Computer c = computers.get(key(world.getName(), pos.x + dx, pos.y + dy, pos.z + dz));
                    if (c != null && c.isAlive()) {
                        return c;
                    }
                }
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
        canvases.clear();
        openMonitorPages.clear();
        for (MonitorDisplay display : displays.values()) {
            display.stop();
        }
        displays.clear();
    }

    public String key(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    /** True only when this page is the one the player's client is currently
     * showing. Sending UI commands to a page that is not current makes the
     * client disconnect ("Selected element not found"), so every per-tick
     * refresh must check this first. */
    public static boolean isCurrentPage(PlayerRef playerRef,
            com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage page) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return false;
            }
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            return player != null && player.getPageManager().getCustomPage() == page;
        } catch (Exception e) {
            return false;
        }
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

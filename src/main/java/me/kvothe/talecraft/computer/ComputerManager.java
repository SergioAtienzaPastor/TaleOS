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
import me.kvothe.talecraft.turtle.TurtleController;
import me.kvothe.talecraft.turtle.TurtleStateStore;
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
    private final TurtleStateStore turtleStateStore;
    private final me.kvothe.talecraft.storage.UsbDriveManager usbDriveManager;
    private final me.kvothe.talecraft.storage.NasManager nasManager;
    private final me.kvothe.talecraft.pocket.PocketManager pocketManager;
    private me.kvothe.talecraft.net.HttpService httpService;
    private me.kvothe.talecraft.peripheral.modem.WirelessNetwork wirelessNetwork =
            new me.kvothe.talecraft.peripheral.modem.WirelessNetwork();
    private final Map<String, Computer> computers = new ConcurrentHashMap<>();
    private final Map<String, me.kvothe.talecraft.peripheral.modem.ModemPeripheral> modemPeripherals =
            new ConcurrentHashMap<>();
    private final Map<String, TurtleController> turtles = new ConcurrentHashMap<>();
    private final Map<Integer, me.kvothe.talecraft.peripheral.speaker.SpeakerRateLimiter> speakerRateLimiters =
            new ConcurrentHashMap<>();
    private final Map<UUID, TerminalPage> openPages = new ConcurrentHashMap<>();
    private final Map<String, MonitorCanvas> canvases = new ConcurrentHashMap<>();
    private final Map<String, me.kvothe.talecraft.peripheral.monitor.MonitorPeripheral> monitorPeripherals =
            new ConcurrentHashMap<>();
    private final Map<UUID, MonitorCanvasPage> openMonitorPages = new ConcurrentHashMap<>();
    private final Map<String, MonitorDisplay> displays = new ConcurrentHashMap<>();
    private final Map<String, MonitorMultiblock.Structure> monitorStructures = new ConcurrentHashMap<>();
    private final Map<String, MonitorMultiblock.Structure> monitorBlockStructures =
            new ConcurrentHashMap<>();
    private final Map<String, java.util.Set<MonitorMultiblock.Pos>> pendingMonitorChanges =
            new ConcurrentHashMap<>();
    /** Worlds with a pending display-entity cleanup (set from any thread,
     * executed on the world's tick thread). */
    private final java.util.Set<String> pendingDisplayCleanup =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private int worldTickCounter;

    public ComputerManager(Path dataDir) {
        this.dataDir = dataDir;
        this.idStore = new ComputerIdStore(dataDir.resolve("ids.json"));
        this.turtleStateStore = new TurtleStateStore(dataDir.resolve("turtles.json"));
        this.usbDriveManager = new me.kvothe.talecraft.storage.UsbDriveManager(this, dataDir, idStore);
        this.nasManager = new me.kvothe.talecraft.storage.NasManager(this, usbDriveManager.hub());
        this.pocketManager = new me.kvothe.talecraft.pocket.PocketManager(this, dataDir, idStore);
    }

    public me.kvothe.talecraft.storage.UsbDriveManager usbDrives() {
        return usbDriveManager;
    }

    public me.kvothe.talecraft.storage.NasManager nas() {
        return nasManager;
    }

    public me.kvothe.talecraft.pocket.PocketManager pockets() {
        return pocketManager;
    }

    public void prepareComputer(Computer computer) {
        computer.getMachine().setHttpBridge(httpService);
        computer.setEditorOpener(this::openEditor);
    }

    public void load() {
        me.kvothe.talecraft.I18n.setLanguage(
                me.kvothe.talecraft.net.LanguageConfig.load(dataDir.resolve("config.json")));
        httpService = new me.kvothe.talecraft.net.HttpService(dataDir.resolve("config.json"));
        me.kvothe.talecraft.net.ModemConfig.Values modemConfig =
                me.kvothe.talecraft.net.ModemConfig.load(dataDir.resolve("config.json"));
        wirelessNetwork = new me.kvothe.talecraft.peripheral.modem.WirelessNetwork(
                modemConfig.range(), modemConfig.maxMessagesPerTick());
        try {
            idStore.load();
            turtleStateStore.load();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read TaleCraft state", e);
        }
    }

    /** Boots (or returns) the computer at the given block. A null world means
     * "no world context" (headless self-test): the machine gets WorldBridge.NONE. */
    public synchronized Computer bootAt(World world, BlockPosition pos) {
        return boot(world, pos, false);
    }

    public synchronized Computer bootTurtleAt(World world, BlockPosition pos) {
        if (world == null) {
            throw new IllegalArgumentException("a turtle needs a world");
        }
        return boot(world, pos, true);
    }

    private Computer boot(World world, BlockPosition pos, boolean turtle) {
        String worldName = world != null ? world.getName() : "world";
        String key = key(worldName, pos.x, pos.y, pos.z);
        if (world != null) {
            String legacyKey = key("world", pos.x, pos.y, pos.z);
            if (!legacyKey.equals(key) && idStore.idAt(legacyKey) != null) {
                Computer migrated = computers.get(legacyKey);
                Computer destination = computers.get(key);
                if (destination != null && destination != migrated) {
                    throw new IllegalStateException("cannot adopt legacy computer at " + key
                            + ": another instance is active");
                }
                idStore.adopt(legacyKey, key);
                if (migrated != null) {
                    computers.remove(legacyKey, migrated);
                    computers.put(key, migrated);
                }
                TurtleController migratedTurtle = turtles.remove(legacyKey);
                if (migratedTurtle != null) {
                    turtles.put(key, migratedTurtle);
                }
            }
        }

        Computer existing = computers.get(key);
        if (existing != null && existing.isAlive()) {
            assertUniqueInstance(key, existing);
            Integer bound = idStore.idAt(key);
            if (bound == null) {
                idStore.bind(key, existing.getId());
            } else if (bound != existing.getId()) {
                throw new IllegalStateException("position " + key + " is bound to computer #"
                        + bound + " instead of #" + existing.getId());
            }
            TurtleController controller = turtle ? turtles.computeIfAbsent(key,
                    ignored -> new TurtleController(world, pos, existing.getId(), this, turtleStateStore))
                    : turtles.get(key);
            if (world != null) {
                existing.getMachine().setWorldBridge(controller == null
                        ? new HytaleWorldBridge(world, pos, existing.getId(), this)
                        : new HytaleWorldBridge(world, pos, existing.getId(), this, controller));
                usbDriveManager.mountComputer(world, pos, existing);
                nasManager.mountComputer(world, pos, existing);
            }
            existing.setEditorOpener(this::openEditor);
            return existing;
        }
        if (existing != null) {
            unloadInstance(key, existing);
        }

        int id = idStore.idFor(key);
        assertIdAvailable(id, key);
        TurtleController controller = turtle
                ? new TurtleController(world, pos, id, this, turtleStateStore)
                : null;
        WorldBridge bridge = world == null
                ? WorldBridge.NONE
                : controller == null
                        ? new HytaleWorldBridge(world, pos, id, this)
                        : new HytaleWorldBridge(world, pos, id, this, controller);
        LOGGER.atInfo().log("boot " + (turtle ? "turtle" : "computer") + " #" + id + " at " + key);
        if (world == null) {
            LOGGER.atWarning().log("booting computer at " + key
                    + " without world context: tale.* world calls will no-op");
        }
        final Computer computer;
        try {
            computer = new Computer(id,
                    dataDir.resolve("computers").resolve(String.valueOf(id)), bridge);
        } catch (IOException e) {
            throw new IllegalStateException("cannot boot computer at " + key, e);
        }
        computer.getMachine().setHttpBridge(httpService);
        computers.put(key, computer);
        try {
            computer.boot();
            if (world != null) {
                usbDriveManager.mountComputer(world, pos, computer);
                nasManager.mountComputer(world, pos, computer);
            }
        } catch (RuntimeException | Error failure) {
            unloadInstance(key, computer);
            throw failure;
        }
        if (controller != null) {
            turtles.put(key, controller);
        }
        computer.setEditorOpener(this::openEditor);
        return computer;
    }

    public synchronized TurtleController turtleControllerAt(World world, BlockPosition pos) {
        bootTurtleAt(world, pos);
        return turtles.get(key(world.getName(), pos.x, pos.y, pos.z));
    }

    /** Reserves the id carried by an item before vanilla places the block. */
    public synchronized int bindAt(World world, BlockPosition pos, Integer carriedId) {
        String key = key(world.getName(), pos.x, pos.y, pos.z);
        Integer previous = idStore.idAt(key);
        int id = carriedId != null ? carriedId : idStore.idFor(key);
        try {
            assertIdAvailable(id, key);
            idStore.bind(key, id);
            idStore.save();
            return id;
        } catch (IOException e) {
            rollbackBind(key, previous);
            throw new IllegalStateException("cannot persist computer placement at " + key, e);
        } catch (RuntimeException e) {
            rollbackBind(key, previous);
            throw e;
        }
    }

    private void rollbackBind(String key, Integer previous) {
        idStore.unbind(key);
        if (previous != null) {
            idStore.bind(key, previous);
        }
    }

    /** Removes a broken block's binding and unloads its live instance. */
    public synchronized int unbindAt(World world, BlockPosition pos) {
        String key = key(world.getName(), pos.x, pos.y, pos.z);
        Computer computer = computers.get(key);
        Integer bound = idStore.idAt(key);
        if (computer != null && bound != null && bound != computer.getId()) {
            throw new IllegalStateException("position " + key + " is bound to computer #"
                    + bound + " instead of #" + computer.getId());
        }
        int id = computer != null ? computer.getId()
                : bound != null ? bound : idStore.idFor(key);
        assertIdAvailable(id, key);
        idStore.bind(key, id);
        idStore.unbind(key);
        if (computer != null) {
            unloadInstance(key, computer);
        } else {
            turtles.remove(key);
        }
        saveState();
        return id;
    }

    public synchronized void moveComputer(World world, BlockPosition previous, BlockPosition destination,
            TurtleController controller) {
        String oldKey = key(world.getName(), previous.x, previous.y, previous.z);
        String newKey = key(world.getName(), destination.x, destination.y, destination.z);
        Computer computer = computers.get(oldKey);
        if (computer == null) {
            throw new IllegalStateException("running turtle not found at " + oldKey);
        }
        Computer atDestination = computers.get(newKey);
        if (atDestination != null && atDestination != computer) {
            throw new IllegalStateException("cannot move computer to occupied position " + newKey);
        }
        TurtleController turtleAtDestination = turtles.get(newKey);
        if (turtleAtDestination != null && turtleAtDestination != controller) {
            throw new IllegalStateException("cannot move turtle to occupied position " + newKey);
        }
        assertIdAvailable(computer.getId(), newKey, computer);
        idStore.move(oldKey, newKey);
        computers.remove(oldKey, computer);
        computers.put(newKey, computer);
        turtles.remove(oldKey);
        turtles.put(newKey, controller);
        usbDriveManager.unmountComputer(oldKey);
        nasManager.unmountComputer(oldKey);
        usbDriveManager.mountComputer(world, destination, computer);
        nasManager.mountComputer(world, destination, computer);
        computer.getMachine().setWorldBridge(
                new HytaleWorldBridge(world, destination, computer.getId(), this, controller));
        saveState();
    }

    private void assertIdAvailable(int id, String key) {
        assertIdAvailable(id, key, null);
    }

    private void assertIdAvailable(int id, String key, Computer allowed) {
        for (Map.Entry<String, Computer> entry : computers.entrySet()) {
            if (!entry.getKey().equals(key) && entry.getValue() != allowed
                    && entry.getValue().getId() == id) {
                throw new IllegalStateException("computer #" + id + " is already active at "
                        + entry.getKey());
            }
        }
    }

    private void assertUniqueInstance(String key, Computer computer) {
        assertIdAvailable(computer.getId(), key);
        for (Map.Entry<String, Computer> entry : computers.entrySet()) {
            if (!entry.getKey().equals(key) && entry.getValue() == computer) {
                throw new IllegalStateException("computer instance #" + computer.getId()
                        + " is registered at both " + key + " and " + entry.getKey());
            }
        }
    }

    private void unloadInstance(String key, Computer computer) {
        usbDriveManager.unmountComputer(key);
        nasManager.unmountComputer(key);
        computers.remove(key, computer);
        turtles.remove(key);
        releaseComputer(computer);
        computer.close();
    }

    public void releaseComputer(Computer computer) {
        wirelessNetwork.removeOwner(computer.getId());
        modemPeripherals.entrySet().removeIf(entry -> entry.getValue().ownerId() == computer.getId());
        speakerRateLimiters.remove(computer.getId());
        closePagesFor(computer);
    }

    public void beginSpeakerTick(int computerId) {
        var limiter = speakerRateLimiters.get(computerId);
        if (limiter != null) {
            limiter.beginTick();
        }
    }

    private synchronized void unloadHalted(String key, Computer computer) {
        if (computers.get(key) == computer && computer.isHalted()) {
            unloadInstance(key, computer);
        }
    }

    private synchronized void rebootHalted(String key, Computer computer, World world) {
        if (computers.get(key) != computer || !computer.isHalted() || !computer.wantsReboot()) {
            return;
        }
        TurtleController controller = turtles.get(key);
        BlockPosition position = controller != null ? controller.getPosition() : positionOf(key);
        World rebootWorld = controller != null ? controller.getWorld() : world;
        unloadInstance(key, computer);
        boot(rebootWorld, position, controller != null);
    }

    private static BlockPosition positionOf(String key) {
        int zSeparator = key.lastIndexOf(':');
        int ySeparator = key.lastIndexOf(':', zSeparator - 1);
        int xSeparator = key.lastIndexOf(':', ySeparator - 1);
        return new BlockPosition(
                Integer.parseInt(key.substring(xSeparator + 1, ySeparator)),
                Integer.parseInt(key.substring(ySeparator + 1, zSeparator)),
                Integer.parseInt(key.substring(zSeparator + 1)));
    }

    private void saveState() {
        try {
            idStore.save();
            turtleStateStore.save();
        } catch (IOException e) {
            LOGGER.atWarning().log("cannot persist computer state: " + e.getMessage());
        }
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
        usbDriveManager.tickWorld(world);
        nasManager.tickWorld(world);
        pocketManager.tickWorld(world);
        for (Map.Entry<String, Computer> entry : computers.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                var limiter = speakerRateLimiters.get(entry.getValue().getId());
                if (limiter != null) {
                    limiter.beginTick();
                }
            }
        }
        if (worldTickCounter % 20 == 0) {
            for (Map.Entry<String, Computer> entry : computers.entrySet()) {
                if (entry.getKey().startsWith(prefix) && entry.getValue().isAlive()) {
                    try {
                        usbDriveManager.mountComputer(world, positionOf(entry.getKey()), entry.getValue());
                    } catch (RuntimeException exception) {
                        LOGGER.atWarning().log("cannot rescan USB drives near " + entry.getKey()
                                + ": " + exception.getMessage());
                    }
                    try {
                        nasManager.mountComputer(world, positionOf(entry.getKey()), entry.getValue());
                    } catch (RuntimeException exception) {
                        LOGGER.atWarning().log("cannot rescan NAS units near " + entry.getKey()
                                + ": " + exception.getMessage());
                    }
                }
            }
        }
        wirelessNetwork.beginTick(world.getName());
        if (pendingDisplayCleanup.remove(world.getName())) {
            cleanDisplayEntities(world);
        }
        if (++worldTickCounter % 400 == 0) {
            cleanDisplayEntities(world);
        }
        if (worldTickCounter % 10 == 0) {
            for (Map.Entry<String, MonitorMultiblock.Structure> entry : monitorBlockStructures.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    requestMonitorRebuild(world, toBlockPosition(entry.getValue().origin()));
                }
            }
        }
        java.util.Set<MonitorMultiblock.Pos> changed = pendingMonitorChanges.remove(world.getName());
        if (changed != null && !changed.isEmpty()) {
            rebuildMonitors(world, changed);
        }
        for (Map.Entry<String, Computer> entry : computers.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            Computer computer = entry.getValue();
            if (computer.isHalted()) {
                if (computer.wantsReboot()) {
                    rebootHalted(entry.getKey(), computer, world);
                } else {
                    unloadHalted(entry.getKey(), computer);
                }
            } else {
                computer.tick();
            }
        }
        for (java.util.Map.Entry<String, TurtleController> entry : turtles.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().tick();
            }
        }
        for (TerminalPage page : openPages.values()) {
            page.refreshFromComputer();
        }
        for (MonitorCanvasPage page : openMonitorPages.values()) {
            page.refreshFromCanvas();
        }
        java.util.Set<MonitorDisplay> ticked = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        for (Map.Entry<String, MonitorDisplay> entry : displays.entrySet()) {
            if (!entry.getKey().startsWith(prefix) || !ticked.add(entry.getValue())) {
                continue;
            }
            MonitorDisplay display = entry.getValue();
            if (!display.tick()) {
                for (MonitorMultiblock.Pos pos : display.getStructure().blocks()) {
                    requestMonitorRebuild(world, toBlockPosition(pos));
                }
            }
        }
    }

    public void requestMonitorRebuild(World world, BlockPosition pos) {
        pendingMonitorChanges.computeIfAbsent(world.getName(),
                ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(new MonitorMultiblock.Pos(pos.x, pos.y, pos.z));
    }

    /** Returns the shared structure canvas, forming it on first access. */
    public MonitorCanvas canvasAt(World world, BlockPosition pos) {
        String key = key(world.getName(), pos.x, pos.y, pos.z);
        if (!monitorBlockStructures.containsKey(key)) {
            rebuildMonitors(world, java.util.Set.of(new MonitorMultiblock.Pos(pos.x, pos.y, pos.z)));
        }
        return canvases.computeIfAbsent(key, ignored -> new MonitorCanvas());
    }

    public me.kvothe.talecraft.peripheral.monitor.MonitorPeripheral monitorPeripheralAt(
            World world, BlockPosition pos) {
        String key = key(world.getName(), pos.x, pos.y, pos.z);
        MonitorCanvas canvas = canvasAt(world, pos);
        return monitorPeripherals.computeIfAbsent(key,
                ignored -> new me.kvothe.talecraft.peripheral.monitor.MonitorPeripheral(canvas));
    }

    private synchronized void rebuildMonitors(World world,
            java.util.Set<MonitorMultiblock.Pos> seeds) {
        java.util.Set<MonitorMultiblock.Structure> affected = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<MonitorMultiblock.Pos> queue = new java.util.ArrayDeque<>();
        java.util.Set<MonitorMultiblock.Pos> queued = new java.util.HashSet<>();
        for (MonitorMultiblock.Pos seed : seeds) {
            enqueueMonitorSeed(world, seed, affected, queue, queued);
            MonitorMultiblock.Structure old = monitorBlockStructures.get(key(world, seed));
            if (old != null) {
                affected.add(old);
                for (MonitorMultiblock.Pos pos : old.blocks()) {
                    enqueueMonitorSeed(world, pos, affected, queue, queued);
                }
            }
        }
        Map<MonitorMultiblock.Pos, MonitorMultiblock.Block> loaded = new java.util.HashMap<>();
        while (!queue.isEmpty()) {
            MonitorMultiblock.Pos pos = queue.removeFirst();
            MonitorState state = readMonitor(world, pos);
            MonitorMultiblock.Structure old = monitorBlockStructures.get(key(world, pos));
            if (state == null) {
                if (old != null && old.contains(pos)) {
                    loaded.put(pos, new MonitorMultiblock.Block(pos, old.facing()));
                }
                continue;
            }
            if (!state.monitor()) {
                continue;
            }
            MonitorMultiblock.Block block = new MonitorMultiblock.Block(pos, state.facing());
            loaded.put(pos, block);
            if (old != null) {
                affected.add(old);
                for (MonitorMultiblock.Pos oldPos : old.blocks()) {
                    if (queued.add(oldPos)) {
                        queue.addLast(oldPos);
                    }
                }
            }
            for (MonitorMultiblock.Pos next : planarNeighbours(state.facing(), pos)) {
                if (queued.add(next)) {
                    queue.addLast(next);
                }
            }
        }

        Map<MonitorMultiblock.Structure, MonitorCanvas> oldCanvases = new java.util.IdentityHashMap<>();
        Map<MonitorMultiblock.Structure, MonitorCanvas.Snapshot> snapshots = new java.util.IdentityHashMap<>();
        Map<String, me.kvothe.talecraft.peripheral.monitor.MonitorPeripheral> oldPeripherals =
                new java.util.HashMap<>();
        java.util.Set<MonitorDisplay> stopped = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        for (MonitorMultiblock.Structure old : affected) {
            MonitorCanvas canvas = canvases.get(key(world, old.origin()));
            if (canvas != null) {
                oldCanvases.put(old, canvas);
                snapshots.put(old, canvas.snapshot());
            }
            monitorStructures.remove(key(world, old.origin()), old);
            for (MonitorMultiblock.Pos pos : old.blocks()) {
                String blockKey = key(world, pos);
                monitorBlockStructures.remove(blockKey, old);
                canvases.remove(blockKey);
                me.kvothe.talecraft.peripheral.monitor.MonitorPeripheral peripheral =
                        monitorPeripherals.remove(blockKey);
                if (peripheral != null) {
                    oldPeripherals.put(blockKey, peripheral);
                }
                MonitorDisplay display = displays.remove(blockKey);
                if (display != null && stopped.add(display)) {
                    display.stop();
                }
            }
        }

        java.util.Set<MonitorCanvas> claimed = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        Map<MonitorCanvas, MonitorCanvas> pageReplacements = new java.util.IdentityHashMap<>();
        java.util.Set<String> resizeEvents = new java.util.HashSet<>();
        for (MonitorMultiblock.Structure structure : MonitorMultiblock.rebuild(loaded.values())) {
            MonitorMultiblock.Structure source = sourceFor(structure, affected, claimed,
                    oldCanvases);
            MonitorCanvas canvas = source == null ? null : oldCanvases.get(source);
            if (canvas == null) {
                canvas = new MonitorCanvas(structure.pixelWidth(), structure.pixelHeight());
            } else {
                claimed.add(canvas);
                pageReplacements.putIfAbsent(canvas, canvas);
            }
            canvas.restore(mergedSnapshot(world, structure, affected, snapshots));
            for (MonitorMultiblock.Structure old : affected) {
                MonitorCanvas oldCanvas = oldCanvases.get(old);
                if (oldCanvas != null && old.facing() == structure.facing()
                        && old.blocks().stream().anyMatch(structure::contains)) {
                    pageReplacements.putIfAbsent(oldCanvas, canvas);
                }
            }
            MonitorDisplay display = new MonitorDisplay(world, structure, canvas);
            monitorStructures.put(key(world, structure.origin()), structure);
            for (MonitorMultiblock.Pos pos : structure.blocks()) {
                String blockKey = key(world, pos);
                monitorBlockStructures.put(blockKey, structure);
                canvases.put(blockKey, canvas);
                displays.put(blockKey, display);
                me.kvothe.talecraft.peripheral.monitor.MonitorPeripheral peripheral =
                        oldPeripherals.get(blockKey);
                if (peripheral != null) {
                    peripheral.rebind(canvas);
                    monitorPeripherals.put(blockKey, peripheral);
                }
                MonitorMultiblock.Structure previous = findContaining(pos, affected);
                if (previous != null && (previous.width() != structure.width()
                        || previous.height() != structure.height())) {
                    queueResizeEvents(world, pos, resizeEvents);
                }
            }
        }
        for (MonitorCanvasPage page : openMonitorPages.values()) {
            MonitorCanvas replacement = pageReplacements.get(page.getCanvas());
            if (replacement != null && replacement != page.getCanvas()) {
                page.rebindCanvas(replacement);
            }
        }
    }

    private void enqueueMonitorSeed(World world, MonitorMultiblock.Pos pos,
            java.util.Set<MonitorMultiblock.Structure> affected,
            java.util.ArrayDeque<MonitorMultiblock.Pos> queue,
            java.util.Set<MonitorMultiblock.Pos> queued) {
        for (MonitorMultiblock.Pos next : withNeighbours(pos)) {
            if (queued.add(next)) {
                queue.addLast(next);
            }
            MonitorMultiblock.Structure old = monitorBlockStructures.get(key(world, next));
            if (old != null) {
                affected.add(old);
            }
        }
    }

    private MonitorCanvas.Snapshot mergedSnapshot(World world, MonitorMultiblock.Structure structure,
            java.util.Set<MonitorMultiblock.Structure> affected,
            Map<MonitorMultiblock.Structure, MonitorCanvas.Snapshot> snapshots) {
        byte[] pixels = new byte[structure.pixelWidth() * structure.pixelHeight()];
        for (MonitorMultiblock.Pos pos : structure.blocks()) {
            MonitorMultiblock.Structure old = findContaining(pos, affected);
            MonitorCanvas.Snapshot snapshot = old == null ? null : snapshots.get(old);
            if (snapshot == null) {
                continue;
            }
            int oldX = old.pixelX(pos);
            int oldY = old.pixelY(pos);
            int newX = structure.pixelX(pos);
            int newY = structure.pixelY(pos);
            for (int y = 0; y < MonitorCanvas.HEIGHT; y++) {
                System.arraycopy(snapshot.pixels(), (oldY + y) * snapshot.width() + oldX,
                        pixels, (newY + y) * structure.pixelWidth() + newX, MonitorCanvas.WIDTH);
            }
        }
        return new MonitorCanvas.Snapshot(structure.pixelWidth(), structure.pixelHeight(), pixels);
    }

    private static MonitorMultiblock.Structure sourceFor(MonitorMultiblock.Structure structure,
            java.util.Set<MonitorMultiblock.Structure> affected, java.util.Set<MonitorCanvas> claimed,
            Map<MonitorMultiblock.Structure, MonitorCanvas> oldCanvases) {
        return affected.stream()
                .filter(old -> oldCanvases.get(old) != null && !claimed.contains(oldCanvases.get(old)))
                .filter(old -> old.facing() == structure.facing())
                .filter(old -> old.origin().equals(structure.origin())
                        || structure.blocks().stream().anyMatch(old::contains))
                .min(java.util.Comparator.comparing(MonitorMultiblock.Structure::origin))
                .orElse(null);
    }

    private static MonitorMultiblock.Structure findContaining(MonitorMultiblock.Pos pos,
            java.util.Set<MonitorMultiblock.Structure> structures) {
        return structures.stream().filter(structure -> structure.contains(pos)).findFirst().orElse(null);
    }

    private void queueResizeEvents(World world, MonitorMultiblock.Pos monitor,
            java.util.Set<String> sent) {
        String[] sides = { "left", "right", "top", "bottom", "front", "back" };
        int[][] offsets = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 },
                { 0, 0, -1 }, { 0, 0, 1 } };
        for (int i = 0; i < offsets.length; i++) {
            int[] offset = offsets[i];
            Computer computer = computers.get(key(world.getName(), monitor.x() + offset[0],
                    monitor.y() + offset[1], monitor.z() + offset[2]));
            if (computer != null && computer.isAlive()
                    && sent.add(computer.getId() + ":" + sides[i])) {
                computer.getMachine().queueEvent("monitor_resize", sides[i]);
            }
        }
    }

    private MonitorState readMonitor(World world, MonitorMultiblock.Pos pos) {
        try {
            com.hypixel.hytale.server.core.universe.world.accessor.SectionReader reader =
                    new com.hypixel.hytale.server.core.universe.world.accessor.SectionReader(
                            world.getChunkStore());
            if (!reader.hasStorageInMemory(pos.x(), pos.y(), pos.z())) {
                return null;
            }
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType type =
                    com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
                            .getAssetMap().getAsset(reader.getBlock(pos.x(), pos.y(), pos.z()));
            double yaw = com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple
                    .get(reader.getRotationIndex(pos.x(), pos.y(), pos.z())).yaw().getRadians();
            return new MonitorState(type != null && "TaleCraft_Monitor".equals(type.getId()),
                    MonitorMultiblock.Facing.fromYaw(yaw));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record MonitorState(boolean monitor, MonitorMultiblock.Facing facing) {
    }

    private static java.util.List<MonitorMultiblock.Pos> planarNeighbours(
            MonitorMultiblock.Facing facing, MonitorMultiblock.Pos pos) {
        return java.util.List.of(facing.right(pos, 1), facing.right(pos, -1),
                pos.offset(0, 1, 0), pos.offset(0, -1, 0));
    }

    private static java.util.List<MonitorMultiblock.Pos> withNeighbours(MonitorMultiblock.Pos pos) {
        return java.util.List.of(pos, pos.offset(1, 0, 0), pos.offset(-1, 0, 0),
                pos.offset(0, 1, 0), pos.offset(0, -1, 0), pos.offset(0, 0, 1),
                pos.offset(0, 0, -1));
    }

    private String key(World world, MonitorMultiblock.Pos pos) {
        return key(world.getName(), pos.x(), pos.y(), pos.z());
    }

    private static BlockPosition toBlockPosition(MonitorMultiblock.Pos pos) {
        return new BlockPosition(pos.x(), pos.y(), pos.z());
    }

    public Computer computerAt(World world, BlockPosition pos) {
        return computers.get(key(world.getName(), pos.x, pos.y, pos.z));
    }

    public Map<String, Computer> computersInWorld(World world) {
        String prefix = world.getName() + ":";
        Map<String, Computer> result = new java.util.LinkedHashMap<>();
        computers.forEach((location, computer) -> {
            if (location.startsWith(prefix)) {
                result.put(location, computer);
            }
        });
        return result;
    }

    public String keyOf(Computer computer) {
        for (Map.Entry<String, Computer> entry : computers.entrySet()) {
            if (entry.getValue() == computer) {
                return entry.getKey();
            }
        }
        return null;
    }

    public me.kvothe.talecraft.peripheral.modem.ModemPeripheral modemPeripheralAt(
            World world, BlockPosition pos, Computer computer, String side) {
        String key = key(world.getName(), pos.x, pos.y, pos.z) + ":" + computer.getId() + ":" + side;
        return modemPeripherals.computeIfAbsent(key, ignored ->
                new me.kvothe.talecraft.peripheral.modem.ModemPeripheral(
                        computer, world, pos, side, wirelessNetwork));
    }

    public me.kvothe.talecraft.peripheral.modem.ModemPeripheral turtleModemPeripheral(
            TurtleController turtle, Computer computer, String side) {
        String key = "turtle:" + computer.getId() + ":" + side;
        return modemPeripherals.computeIfAbsent(key, ignored ->
                new me.kvothe.talecraft.peripheral.modem.ModemPeripheral(
                        computer, turtle, side, wirelessNetwork));
    }

    public me.kvothe.talecraft.peripheral.speaker.SpeakerPeripheral speakerPeripheralAt(
            World world, BlockPosition pos, Computer computer) {
        return new me.kvothe.talecraft.peripheral.speaker.SpeakerPeripheral(
                speakerRateLimiters.computeIfAbsent(computer.getId(),
                        ignored -> new me.kvothe.talecraft.peripheral.speaker.SpeakerRateLimiter()),
                new me.kvothe.talecraft.peripheral.speaker.HytaleSpeakerBackend(world, () -> pos));
    }

    public me.kvothe.talecraft.peripheral.speaker.SpeakerPeripheral turtleSpeakerPeripheral(
            TurtleController turtle, Computer computer) {
        return new me.kvothe.talecraft.peripheral.speaker.SpeakerPeripheral(
                speakerRateLimiters.computeIfAbsent(computer.getId(),
                        ignored -> new me.kvothe.talecraft.peripheral.speaker.SpeakerRateLimiter()),
                new me.kvothe.talecraft.peripheral.speaker.HytaleSpeakerBackend(
                        turtle.getWorld(), turtle::getPosition));
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

    /** Console test hook: stops and clears the display/canvas at the position. */
    public void displayClear(World world, BlockPosition pos) {
        String key = key(world.getName(), pos.x, pos.y, pos.z);
        MonitorMultiblock.Structure structure = monitorBlockStructures.remove(key);
        if (structure == null) {
            MonitorCanvas canvas = canvases.remove(key);
            if (canvas != null) {
                canvas.clear(0);
            }
            MonitorDisplay display = displays.remove(key);
            if (display != null) {
                display.stop();
            }
            monitorPeripherals.remove(key);
            return;
        }
        MonitorCanvas canvas = canvases.get(key);
        if (canvas != null) {
            canvas.clear(0);
        }
        monitorStructures.remove(key(world, structure.origin()), structure);
        java.util.Set<MonitorDisplay> stopped = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        for (MonitorMultiblock.Pos block : structure.blocks()) {
            String blockKey = key(world, block);
            monitorBlockStructures.remove(blockKey, structure);
            canvases.remove(blockKey);
            monitorPeripherals.remove(blockKey);
            MonitorDisplay display = displays.remove(blockKey);
            if (display != null && stopped.add(display)) {
                display.stop();
            }
        }
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

    public void closePagesFor(Computer computer) {
        openPages.entrySet().removeIf(entry -> entry.getValue().getComputer() == computer);
    }

    /** Persists ids; machine state is volatile by design (see Computer). */
    public synchronized void shutdown() {
        saveState();
        pocketManager.shutdown();
        for (Computer computer : computers.values()) {
            computer.close();
        }
        nasManager.shutdown();
        usbDriveManager.shutdown();
        computers.clear();
        turtles.clear();
        wirelessNetwork.clear();
        modemPeripherals.clear();
        openPages.clear();
        canvases.clear();
        monitorPeripherals.clear();
        openMonitorPages.clear();
        for (MonitorDisplay display : displays.values()) {
            display.stop();
        }
        displays.clear();
        monitorStructures.clear();
        monitorBlockStructures.clear();
        pendingMonitorChanges.clear();
        if (httpService != null) {
            httpService.close();
        }
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

package me.kvothe.taleos.storage;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;

import me.kvothe.taleos.computer.Computer;
import me.kvothe.taleos.computer.ComputerManager;
import me.kvothe.taleos.os.VirtualFileSystem;

public final class NasManager {
    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
    public static final String BLOCK_ID = "TaleOS_NAS";
    public static final int SLOTS = 8;
    public static final int RANGE = 16;
    private static final int[][] NEIGHBOURS = {
            { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 },
            { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 },
    };

    private final ComputerManager computers;
    private final PenDriveHub hub;
    private final Set<String> units = new HashSet<>();
    private final Map<String, Set<String>> attachments = new HashMap<>();

    public NasManager(ComputerManager computers, PenDriveHub hub) {
        this.computers = computers;
        this.hub = hub;
    }

    public ItemContainer containerAt(World world, BlockPosition position) {
        return UsbDriveManager.containerAt(world, position, BLOCK_ID, SLOTS);
    }

    public synchronized void registerUnit(World world, BlockPosition position) {
        if (containerAt(world, position) == null) {
            return;
        }
        units.add(location(world, position));
        syncUnit(world, position);
    }

    public synchronized void unitBroken(World world, BlockPosition position) {
        String location = location(world, position);
        units.remove(location);
        for (int slot = 0; slot < SLOTS; slot++) {
            hub.remove(slotLocation(location, slot));
        }
    }

    public synchronized void mountComputer(World world, BlockPosition position, Computer computer) {
        discoverAround(world, position);
        reconcileComputer(world, computers.keyOf(computer), position, computer);
    }

    public synchronized void mountPocket(World world, BlockPosition position, Computer computer) {
        discoverAround(world, position);
        reconcileComputer(world, pocketKey(computer.getId()), position, computer);
    }

    public static String pocketKey(int computerId) {
        return "pocket:" + computerId;
    }

    public synchronized void tickWorld(World world) {
        String prefix = world.getName() + ":";
        for (String location : List.copyOf(units)) {
            if (location.startsWith(prefix)) {
                syncUnit(world, positionOf(location));
            }
        }
        for (Map.Entry<String, Computer> entry : computers.computersInWorld(world).entrySet()) {
            if (entry.getValue().isAlive()) {
                try {
                    reconcileComputer(world, entry.getKey(), positionOf(entry.getKey()), entry.getValue());
                } catch (RuntimeException exception) {
                    LOGGER.atWarning().log("cannot reconcile NAS mounts for " + entry.getKey()
                            + ": " + exception.getMessage());
                }
            }
        }
    }

    private void discoverAround(World world, BlockPosition origin) {
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dy = -RANGE; dy <= RANGE; dy++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
                        continue;
                    }
                    BlockPosition position = new BlockPosition(origin.x + dx, origin.y + dy,
                            origin.z + dz);
                    if (containerAt(world, position) != null) {
                        units.add(location(world, position));
                    }
                }
            }
        }
    }

    private void reconcileComputer(World world, String computerKey, BlockPosition origin,
            Computer computer) {
        if (computerKey == null) {
            return;
        }
        Set<String> component = connectedUnitsWithin(units, world.getName(), origin, RANGE);
        Set<String> desired = new HashSet<>();
        Set<String> desiredMountPaths = new HashSet<>();
        for (String unit : component) {
            syncUnit(world, positionOf(unit));
            for (int slot = 0; slot < SLOTS; slot++) {
                String location = slotLocation(unit, slot);
                PenDriveHub.Drive drive = hub.atLocation(location);
                if (drive != null) {
                    desired.add(location);
                    desiredMountPaths.add("nas/" + drive.id());
                }
            }
        }
        removeStaleNasMounts(computer.getFileSystem(), desiredMountPaths);
        Set<String> current = attachments.computeIfAbsent(computerKey, ignored -> new HashSet<>());
        for (String location : new ArrayList<>(current)) {
            PenDriveHub.Drive drive = hub.atLocation(location);
            if (!desired.contains(location) || drive == null
                    || !hub.attachmentActive(location, computerKey, "nas/" + drive.id())) {
                hub.detachComputer(location, computerKey);
                current.remove(location);
            }
        }
        for (String location : desired) {
            if (current.contains(location)) {
                continue;
            }
            PenDriveHub.Drive drive = hub.atLocation(location);
            try {
                hub.attachComputer(location, computerKey, access(computer), "nas",
                        "nas/" + drive.id());
                current.add(location);
            } catch (IOException | VirtualFileSystem.FsException exception) {
                throw new IllegalStateException("cannot attach NAS PenDrive at " + location
                        + " to " + computerKey, exception);
            }
        }
        if (current.isEmpty()) {
            attachments.remove(computerKey);
        }
    }

    static void removeStaleNasMounts(VirtualFileSystem fs, Set<String> desiredMountPaths) {
        for (String mounted : fs.getMountPaths()) {
            if (mounted.startsWith("nas/") && !desiredMountPaths.contains(mounted)) {
                try {
                    fs.unmount(mounted);
                } catch (VirtualFileSystem.FsException exception) {
                    throw new IllegalStateException("cannot remove stale NAS mount " + mounted,
                            exception);
                }
            }
        }
    }

    private void syncUnit(World world, BlockPosition position) {
        String location = location(world, position);
        ItemContainer container = containerAt(world, position);
        if (container == null) {
            unitBroken(world, position);
            return;
        }
        units.add(location);
        for (int slot = 0; slot < SLOTS; slot++) {
            String slotLocation = slotLocation(location, slot);
            ItemStack stack = container.getItemStack((short) slot);
            if (!PenDriveItemMetadata.isPenDrive(stack)) {
                hub.remove(slotLocation);
                continue;
            }
            try {
                Integer id = PenDriveItemMetadata.id(stack);
                PenDriveHub.Drive drive = hub.insert(slotLocation, id,
                        PenDriveItemMetadata.label(stack));
                if (id == null && !container.replaceItemStackInSlot((short) slot, stack,
                        PenDriveItemMetadata.withId(stack, drive.id())).succeeded()) {
                    throw new IllegalStateException("cannot persist PenDrive id at " + slotLocation);
                }
            } catch (IOException | VirtualFileSystem.FsException | RuntimeException exception) {
                LOGGER.atWarning().log("NAS PenDrive rejected at " + slotLocation + ": "
                        + exception.getMessage());
            }
        }
    }

    public synchronized void assertCanInsert(World world, BlockPosition position, int slot,
            ItemStack stack) {
        if (slot < 0 || slot >= SLOTS || !PenDriveItemMetadata.isPenDrive(stack)) {
            throw new IllegalArgumentException(me.kvothe.taleos.I18n.t(
                    "The NAS only accepts PenDrives in its 8 bays"));
        }
        Integer id = PenDriveItemMetadata.id(stack);
        PenDriveHub.Drive claimed = id == null ? null : hub.byId(id);
        String location = slotLocation(location(world, position), slot);
        if (claimed != null && hub.atLocation(location) != claimed) {
            throw new IllegalStateException("PenDrive #" + id + me.kvothe.taleos.I18n.t(
                    " is already inserted in another reader"));
        }
    }

    public synchronized PenDriveHub.Drive insertAdmin(World world, BlockPosition position, int slot,
            String label) {
        ItemContainer container = containerAt(world, position);
        if (container == null || slot < 0 || slot >= SLOTS) {
            throw new IllegalStateException(me.kvothe.taleos.I18n.t("NAS or bay unavailable"));
        }
        ItemStack existing = container.getItemStack((short) slot);
        if (existing != null && !existing.isEmpty()) {
            throw new IllegalStateException(me.kvothe.taleos.I18n.t("The bay is not empty"));
        }
        ItemStack inserted = PenDriveItemMetadata.unassigned();
        if (!container.replaceItemStackInSlot((short) slot, existing, inserted).succeeded()) {
            throw new IllegalStateException(me.kvothe.taleos.I18n.t("Could not insert the PenDrive"));
        }
        syncUnit(world, position);
        String location = slotLocation(location(world, position), slot);
        PenDriveHub.Drive drive = hub.atLocation(location);
        if (label != null && !label.isBlank() && drive != null) {
            ItemStack stack = container.getItemStack((short) slot);
            ItemStack relabelled = PenDriveItemMetadata.withLabel(stack, label);
            if (!container.replaceItemStackInSlot((short) slot, stack, relabelled).succeeded()) {
                throw new IllegalStateException("cannot persist PenDrive label at " + location);
            }
            hub.setLabel(location, label);
        }
        return drive;
    }

    public synchronized List<String> driveLocations() {
        return hub.locations().stream().map(location -> location + "=" + hub.atLocation(location).id())
                .toList();
    }

    public synchronized void unmountComputer(String computerKey) {
        Set<String> current = attachments.remove(computerKey);
        if (current != null) {
            for (String location : current) {
                hub.detachComputer(location, computerKey);
            }
        }
        hub.detachComputer(computerKey);
    }

    public synchronized void shutdown() {
        for (String computerKey : List.copyOf(attachments.keySet())) {
            unmountComputer(computerKey);
        }
        units.clear();
    }

    private static PenDriveHub.ComputerAccess access(Computer computer) {
        return new PenDriveHub.ComputerAccess() {
            @Override
            public VirtualFileSystem fileSystem() {
                return computer.getFileSystem();
            }

            @Override
            public boolean alive() {
                return computer.isAlive();
            }

            @Override
            public void usbEvent(String event, String side) {
                computer.getMachine().queueEvent(event, side);
            }
        };
    }

    static Set<String> connectedUnitsWithin(Set<String> unitLocations, String computerKey,
            int range) {
        return connectedUnitsWithin(unitLocations, worldOf(computerKey), positionOf(computerKey), range);
    }

    private static Set<String> connectedUnitsWithin(Set<String> unitLocations, String world,
            BlockPosition origin, int range) {
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> component = new HashSet<>();
        long rangeSquared = (long) range * range;
        for (String location : unitLocations) {
            if (!location.startsWith(world + ":")) {
                continue;
            }
            BlockPosition position = positionOf(location);
            long dx = position.x - origin.x;
            long dy = position.y - origin.y;
            long dz = position.z - origin.z;
            if (dx * dx + dy * dy + dz * dz <= rangeSquared) {
                pending.add(location);
                component.add(location);
            }
        }
        while (!pending.isEmpty()) {
            BlockPosition position = positionOf(pending.removeFirst());
            for (int[] offset : NEIGHBOURS) {
                String neighbour = location(world, new BlockPosition(position.x + offset[0],
                        position.y + offset[1], position.z + offset[2]));
                if (unitLocations.contains(neighbour) && component.add(neighbour)) {
                    pending.addLast(neighbour);
                }
            }
        }
        return component;
    }

    private static String slotLocation(String location, int slot) {
        return location + "#" + slot;
    }

    private static String location(World world, BlockPosition position) {
        return location(world.getName(), position);
    }

    private static String location(String world, BlockPosition position) {
        return world + ":" + position.x + ":" + position.y + ":" + position.z;
    }

    private static String worldOf(String key) {
        return key.substring(0, key.indexOf(':'));
    }

    private static BlockPosition positionOf(String key) {
        int z = key.lastIndexOf(':');
        int y = key.lastIndexOf(':', z - 1);
        int x = key.lastIndexOf(':', y - 1);
        return new BlockPosition(Integer.parseInt(key.substring(x + 1, y)),
                Integer.parseInt(key.substring(y + 1, z)), Integer.parseInt(key.substring(z + 1)));
    }
}

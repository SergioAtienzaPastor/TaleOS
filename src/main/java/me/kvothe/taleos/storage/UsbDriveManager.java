package me.kvothe.taleos.storage;

import java.io.IOException;
import java.util.List;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.taleos.computer.Computer;
import me.kvothe.taleos.computer.ComputerIdStore;
import me.kvothe.taleos.computer.ComputerManager;
import me.kvothe.taleos.computer.HytalePeripheralProvider;
import me.kvothe.taleos.os.VirtualFileSystem;
import me.kvothe.taleos.peripheral.usb.UsbDrivePeripheral;

public final class UsbDriveManager {
    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
    public static final String BLOCK_ID = "TaleOS_USB_Drive";
    private static final String[][] NEIGHBOURS = {
            { "left", "-1", "0", "0" }, { "right", "1", "0", "0" },
            { "top", "0", "1", "0" }, { "bottom", "0", "-1", "0" },
            { "front", "0", "0", "1" }, { "back", "0", "0", "-1" },
    };

    private final ComputerManager computers;
    private final PenDriveHub hub;

    public UsbDriveManager(ComputerManager computers, java.nio.file.Path dataDir, ComputerIdStore ids) {
        this.computers = computers;
        this.hub = new PenDriveHub(dataDir.resolve("pendrives"), ids::allocateGlobalId,
                ids::reserveGlobalId);
    }

    public PenDriveHub hub() {
        return hub;
    }

    public ItemContainer containerAt(World world, BlockPosition position) {
        return containerAt(world, position, BLOCK_ID, 1);
    }

    public static ItemContainer containerAt(World world, BlockPosition position,
            String expectedBlockId, int capacity) {
        try {
            SectionReader reader = new SectionReader(world.getChunkStore());
            if (!reader.hasStorageInMemory(position.x, position.y, position.z)) {
                return null;
            }
            BlockType type = BlockType.getAssetMap().getAsset(
                    reader.getBlock(position.x, position.y, position.z));
            if (type == null || !expectedBlockId.equals(type.getId())) {
                return null;
            }
            var componentType = com.hypixel.hytale.server.core.modules.block.components
                    .ItemContainerBlock.getComponentType();
            var component = com.hypixel.hytale.server.core.modules.block.BlockModule.getComponent(
                    componentType, world, position.x, position.y, position.z);
            if (component == null) {
                var chunks = world.getChunkStore();
                var section = chunks.getChunkSectionReferenceAtBlock(position.x, position.y, position.z);
                var components = chunks.getStore().getComponent(section,
                        com.hypixel.hytale.server.core.universe.world.chunk.section
                                .BlockComponentSection.getComponentType());
                var blockRef = com.hypixel.hytale.server.core.modules.block.BlockEntity
                        .ensureBlockEntity(chunks.getStore(), section, components,
                                position.x, position.y, position.z, type, 0);
                var template = type.getBlockEntity().getComponent(componentType);
                if (blockRef != null && blockRef.isValid()) {
                    component = chunks.getStore().getComponent(blockRef, componentType);
                    if (component == null) {
                        component = new com.hypixel.hytale.server.core.modules.block.components
                                .ItemContainerBlock(template);
                        chunks.getStore().putComponent(blockRef, componentType, component);
                    }
                } else {
                    int index = com.hypixel.hytale.math.util.ChunkUtil.indexBlock(
                            position.x, position.y, position.z);
                    var holder = components.getBlockHolder(index);
                    if (holder != null) {
                        component = holder.getComponent(componentType);
                        if (component == null) {
                            component = new com.hypixel.hytale.server.core.modules.block.components
                                    .ItemContainerBlock(template);
                            holder.putComponent(componentType, component);
                        }
                    }
                }
            }
            ItemContainer container = component == null ? null : component.getItemContainer();
            if (container == null) {
                LOGGER.atWarning().log("PenDrive container diagnostic at " + position + ": blockEntity="
                        + type.getBlockEntity() + ", component=" + component);
            }
            if (container != null && container.getCapacity() == capacity) {
                for (int slot = 0; slot < capacity; slot++) {
                    container.setSlotFilter(FilterActionType.ADD, (short) slot,
                            (action, target, targetSlot, stack) -> PenDriveItemMetadata.isPenDrive(stack));
                }
            }
            return container;
        } catch (RuntimeException exception) {
            LOGGER.atWarning().log("cannot read PenDrive container at " + position + ": "
                    + exception);
            return null;
        }
    }

    public synchronized PenDriveHub.Drive sync(World world, BlockPosition position) {
        String location = computers.key(world.getName(), position.x, position.y, position.z);
        ItemContainer container = containerAt(world, position);
        if (container == null) {
            hub.remove(location);
            return null;
        }
        ItemStack stack = container.getItemStack((short) 0);
        if (!PenDriveItemMetadata.isPenDrive(stack)) {
            hub.remove(location);
            return null;
        }
        try {
            Integer id = PenDriveItemMetadata.id(stack);
            PenDriveHub.Drive drive = hub.insert(location, id, PenDriveItemMetadata.label(stack));
            if (id == null && !container.replaceItemStackInSlot((short) 0, stack,
                    PenDriveItemMetadata.withId(stack, drive.id())).succeeded()) {
                throw new IllegalStateException("cannot persist PenDrive id at " + location);
            }
            mountAdjacent(world, position);
            return drive;
        } catch (IOException | VirtualFileSystem.FsException exception) {
            hub.remove(location);
            throw new IllegalStateException("cannot mount PenDrive at " + location, exception);
        } catch (RuntimeException exception) {
            hub.remove(location);
            throw exception;
        }
    }

    public synchronized void tickWorld(World world) {
        String prefix = world.getName() + ":";
        for (String location : hub.locations()) {
            if (location.startsWith(prefix) && location.indexOf('#') < 0) {
                BlockPosition position = positionOf(location);
                try {
                    sync(world, position);
                } catch (RuntimeException exception) {
                    LOGGER.atWarning().log("PenDrive rejected at " + location + ": "
                            + exception.getMessage());
                }
            }
        }
    }

    public synchronized void mountComputer(World world, BlockPosition position, Computer computer) {
        String computerKey = computers.key(world.getName(), position.x, position.y, position.z);
        for (String[] neighbour : NEIGHBOURS) {
            BlockPosition drivePosition = offset(position, neighbour);
            if (sync(world, drivePosition) != null) {
                String location = computers.key(world.getName(), drivePosition.x, drivePosition.y,
                        drivePosition.z);
                attach(location, computerKey, computer, neighbour[0]);
            }
        }
    }

    private void mountAdjacent(World world, BlockPosition drivePosition) {
        String location = computers.key(world.getName(), drivePosition.x, drivePosition.y,
                drivePosition.z);
        for (String[] neighbour : NEIGHBOURS) {
            BlockPosition computerPosition = offset(drivePosition, neighbour);
            Computer computer = computers.computerAt(world, computerPosition);
            if (computer != null && computer.isAlive()) {
                String computerKey = computers.key(world.getName(), computerPosition.x,
                        computerPosition.y, computerPosition.z);
                attach(location, computerKey, computer, oppositeSide(neighbour[0]));
            }
        }
    }

    static String oppositeSide(String side) {
        return switch (side) {
            case "left" -> "right";
            case "right" -> "left";
            case "top" -> "bottom";
            case "bottom" -> "top";
            case "front" -> "back";
            case "back" -> "front";
            default -> throw new IllegalArgumentException("unknown side: " + side);
        };
    }

    private void attach(String location, String computerKey, Computer computer, String side) {
        try {
            hub.attachComputer(location, computerKey, access(computer), side);
        } catch (IOException | VirtualFileSystem.FsException exception) {
            throw new IllegalStateException("cannot attach PenDrive to computer at " + computerKey,
                    exception);
        }
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

    public synchronized void unmountComputer(String computerKey) {
        hub.detachComputer(computerKey);
    }

    public synchronized void driveBroken(World world, BlockPosition position) {
        hub.remove(computers.key(world.getName(), position.x, position.y, position.z));
    }

    public synchronized UsbDrivePeripheral peripheralAt(World world, BlockPosition position,
            Computer computer, String side) {
        sync(world, position);
        String location = computers.key(world.getName(), position.x, position.y, position.z);
        String computerKey = computer == null ? null : computers.keyOf(computer);
        return new UsbDrivePeripheral(new UsbDrivePeripheral.Backend() {
            @Override
            public Integer penDriveId() {
                PenDriveHub.Drive drive = hub.atLocation(location);
                return drive == null ? null : drive.id();
            }

            @Override
            public String penDriveLabel() {
                PenDriveHub.Drive drive = hub.atLocation(location);
                return drive == null ? null : drive.label();
            }

            @Override
            public void setPenDriveLabel(String label) {
                setLabel(world, position, label);
            }

            @Override
            public String mountPath() {
                return computerKey == null ? null : hub.mountPath(location, computerKey);
            }

            @Override
            public boolean eject() {
                return UsbDriveManager.this.eject(world, position);
            }
        });
    }

    public synchronized void setLabel(World world, BlockPosition position, String label) {
        ItemContainer container = containerAt(world, position);
        ItemStack stack = container == null ? null : container.getItemStack((short) 0);
        Integer id = PenDriveItemMetadata.id(stack);
        if (id == null) {
            throw new IllegalStateException("No PenDrive present");
        }
        String location = computers.key(world.getName(), position.x, position.y, position.z);
        ItemStack relabelled = PenDriveItemMetadata.withLabel(stack, label);
        if (!container.replaceItemStackInSlot((short) 0, stack, relabelled).succeeded()) {
            throw new IllegalStateException("cannot persist PenDrive label at " + location);
        }
        hub.setLabel(location, label);
    }

    public synchronized boolean eject(World world, BlockPosition position) {
        ItemContainer container = containerAt(world, position);
        ItemStack stack = container == null ? null : container.getItemStack((short) 0);
        if (!PenDriveItemMetadata.isPenDrive(stack)) {
            return false;
        }
        var removal = container.removeItemStackFromSlot((short) 0);
        if (!removal.succeeded()) {
            return false;
        }
        ItemStack removed = removal.getOutput();
        if (!PenDriveItemMetadata.isPenDrive(removed)) {
            restore(container, removed);
            return false;
        }
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(store, List.of(removed),
                    new org.joml.Vector3d(position.x + 0.5, position.y + 0.5, position.z + 0.5),
                    Rotation3f.IDENTITY);
            if (holders == null || holders.length != 1) {
                restore(container, removed);
                return false;
            }
            Ref<EntityStore>[] refs = store.addEntities(holders,
                    com.hypixel.hytale.component.AddReason.SPAWN);
            if (refs == null || refs.length != 1 || refs[0] == null || !refs[0].isValid()) {
                restore(container, removed);
                return false;
            }
        } catch (RuntimeException exception) {
            restore(container, removed);
            LOGGER.atWarning().log("cannot eject PenDrive at " + position + ": "
                    + exception.getMessage());
            return false;
        }
        driveBroken(world, position);
        return true;
    }

    private static void restore(ItemContainer container, ItemStack stack) {
        if (stack != null && !container.replaceItemStackInSlot((short) 0, null, stack).succeeded()) {
            LOGGER.atSevere().log("cannot restore PenDrive after a failed inventory operation");
        }
    }

    public synchronized void assertCanInsert(World world, BlockPosition position, ItemStack stack) {
        if (!PenDriveItemMetadata.isPenDrive(stack)) {
            throw new IllegalArgumentException(me.kvothe.taleos.I18n.t("The USB drive only accepts PenDrives"));
        }
        Integer id = PenDriveItemMetadata.id(stack);
        PenDriveHub.Drive claimed = id == null ? null : hub.byId(id);
        String location = computers.key(world.getName(), position.x, position.y, position.z);
        if (claimed != null && hub.atLocation(location) != claimed) {
            throw new IllegalStateException("PenDrive #" + id + me.kvothe.taleos.I18n.t(
                    " is already inserted in another reader"));
        }
    }

    public synchronized PenDriveHub.Drive insertAdmin(World world, BlockPosition position,
            String label) {
        ItemContainer container = containerAt(world, position);
        if (container == null) {
            throw new IllegalStateException(me.kvothe.taleos.I18n.t("USB drive unavailable"));
        }
        ItemStack existing = container.getItemStack((short) 0);
        if (existing != null && !existing.isEmpty()) {
            throw new IllegalStateException(me.kvothe.taleos.I18n.t("The drive slot is not empty"));
        }
        ItemStack inserted = PenDriveItemMetadata.unassigned();
        if (!container.replaceItemStackInSlot((short) 0, existing, inserted).succeeded()) {
            throw new IllegalStateException(me.kvothe.taleos.I18n.t("Could not insert the PenDrive"));
        }
        try {
            PenDriveHub.Drive drive = sync(world, position);
            if (label != null && !label.isBlank()) {
                setLabel(world, position, label);
            }
            return drive;
        } catch (RuntimeException exception) {
            container.removeItemStackFromSlot((short) 0);
            driveBroken(world, position);
            throw exception;
        }
    }

    public synchronized void shutdown() {
        hub.clear();
    }

    private static BlockPosition offset(BlockPosition origin, String[] neighbour) {
        return new BlockPosition(origin.x + Integer.parseInt(neighbour[1]),
                origin.y + Integer.parseInt(neighbour[2]), origin.z + Integer.parseInt(neighbour[3]));
    }

    private static BlockPosition positionOf(String key) {
        int z = key.lastIndexOf(':');
        int y = key.lastIndexOf(':', z - 1);
        int x = key.lastIndexOf(':', y - 1);
        return new BlockPosition(Integer.parseInt(key.substring(x + 1, y)),
                Integer.parseInt(key.substring(y + 1, z)), Integer.parseInt(key.substring(z + 1)));
    }
}

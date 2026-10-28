package me.kvothe.talecraft.computer;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;

import me.kvothe.talecraft.peripheral.Peripheral;
import me.kvothe.talecraft.peripheral.PeripheralProvider;
import me.kvothe.talecraft.peripheral.inventory.HytaleInventoryAdapter;
import me.kvothe.talecraft.peripheral.inventory.InventoryPeripheral;

public final class HytalePeripheralProvider implements PeripheralProvider {
    private static final Map<String, BlockPosition> SIDES = new LinkedHashMap<>();

    static {
        SIDES.put("left", new BlockPosition(-1, 0, 0));
        SIDES.put("right", new BlockPosition(1, 0, 0));
        SIDES.put("top", new BlockPosition(0, 1, 0));
        SIDES.put("bottom", new BlockPosition(0, -1, 0));
        SIDES.put("front", new BlockPosition(0, 0, 1));
        SIDES.put("back", new BlockPosition(0, 0, -1));
    }

    private final World world;
    private final java.util.function.Supplier<BlockPosition> origin;
    private final ComputerManager manager;
    private final me.kvothe.talecraft.turtle.TurtleController turtle;
    private final java.util.function.Supplier<Computer> computer;

    public HytalePeripheralProvider(World world, BlockPosition origin, ComputerManager manager) {
        this(world, origin, manager, null);
    }

    public HytalePeripheralProvider(World world, BlockPosition origin, ComputerManager manager,
            me.kvothe.talecraft.turtle.TurtleController turtle) {
        this(world, () -> origin, manager, turtle);
    }

    public HytalePeripheralProvider(World world, java.util.function.Supplier<BlockPosition> origin,
            ComputerManager manager, me.kvothe.talecraft.turtle.TurtleController turtle) {
        this.world = world;
        this.origin = origin;
        this.manager = manager;
        this.turtle = turtle;
        this.computer = null;
    }

    public HytalePeripheralProvider(World world, java.util.function.Supplier<BlockPosition> origin,
            ComputerManager manager, java.util.function.Supplier<Computer> computer) {
        this.world = world;
        this.origin = origin;
        this.manager = manager;
        this.turtle = null;
        this.computer = computer;
    }

    private BlockPosition origin() {
        return origin.get();
    }

    private Computer ownerComputer() {
        return computer != null ? computer.get() : manager.computerAt(world, origin());
    }

    @Override
    public Map<String, Peripheral> snapshot() {
        Map<String, Peripheral> result = new LinkedHashMap<>();
        BlockPosition origin = origin();
        SIDES.forEach((side, offset) -> {
            Peripheral peripheral = turtle == null ? null : turtle.peripheral(side);
            if (peripheral == null) {
                peripheral = at(origin.x + offset.x, origin.y + offset.y, origin.z + offset.z, side);
            }
            if (peripheral != null) {
                result.put(side, peripheral);
            }
        });
        return result;
    }

    @Override
    public Peripheral find(String name) {
        BlockPosition origin = origin();
        BlockPosition offset = SIDES.get(name);
        if (offset != null) {
            Peripheral equipped = turtle == null ? null : turtle.peripheral(name);
            return equipped != null ? equipped
                    : at(origin.x + offset.x, origin.y + offset.y, origin.z + offset.z, name);
        }
        if (name != null && name.startsWith("@")) {
            String[] coordinates = name.substring(1).split(",", -1);
            if (coordinates.length == 3) {
                try {
                    return at(origin.x + Integer.parseInt(coordinates[0]),
                            origin.y + Integer.parseInt(coordinates[1]),
                            origin.z + Integer.parseInt(coordinates[2]), name);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Peripheral at(int x, int y, int z, String side) {
        SectionReader reader = new SectionReader(world.getChunkStore());
        if (!reader.hasStorageInMemory(x, y, z)) {
            return null;
        }
        var type = com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap()
                .getAsset(reader.getBlock(x, y, z));
        if (type == null || "Empty".equals(type.getId())) {
            return null;
        }
        BlockPosition position = new BlockPosition(x, y, z);
        if ("TaleCraft_Monitor".equals(type.getId())) {
            return manager.monitorPeripheralAt(world, position);
        }
        if ("TaleCraft_Modem".equals(type.getId())) {
            Computer computer = ownerComputer();
            return computer == null ? null : manager.modemPeripheralAt(world, position, computer, side);
        }
        if (me.kvothe.talecraft.turtle.TurtleUpgradeRegistry.SPEAKER_ID.equals(type.getId())) {
            Computer computer = ownerComputer();
            return computer == null ? null : manager.speakerPeripheralAt(world, position, computer);
        }
        if (me.kvothe.talecraft.storage.UsbDriveManager.BLOCK_ID.equals(type.getId())) {
            return manager.usbDrives().peripheralAt(world, position, ownerComputer(), side);
        }
        ItemContainer container = containerAt(world, x, y, z);
        return container == null ? null : new InventoryPeripheral(new HytaleInventoryAdapter(container));
    }

    public static ItemContainer containerAt(World world, BlockPosition position) {
        return containerAt(world, position.x, position.y, position.z);
    }

    private static ItemContainer containerAt(World world, int x, int y, int z) {
        try {
            ItemContainerBlock component = BlockModule.getComponent(
                    ItemContainerBlock.getComponentType(), world, x, y, z);
            return component == null ? null : component.getItemContainer();
        } catch (RuntimeException e) {
            return null;
        }
    }
}

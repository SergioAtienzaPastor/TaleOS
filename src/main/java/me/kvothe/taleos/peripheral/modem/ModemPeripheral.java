package me.kvothe.taleos.peripheral.modem;

import java.util.List;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;

import me.kvothe.taleos.computer.Computer;
import me.kvothe.taleos.peripheral.Peripheral;
import me.kvothe.taleos.turtle.TurtleUpgradeRegistry;

public final class ModemPeripheral implements Peripheral, WirelessNetwork.Node {
    private static final List<String> METHODS = List.of(
            "open", "close", "closeAll", "isOpen", "transmit", "isWireless");

    private final Computer computer;
    private final World world;
    private final BlockPosition position;
    private final me.kvothe.taleos.turtle.TurtleController turtle;
    private final java.util.function.Supplier<BlockPosition> dynamicPosition;
    private final java.util.function.BooleanSupplier loadedOverride;
    private final String worldNameOverride;
    private final String side;
    private final WirelessNetwork network;

    public ModemPeripheral(Computer computer, World world, BlockPosition position, String side,
            WirelessNetwork network) {
        this(computer, world, position, null, side, network);
    }

    public ModemPeripheral(Computer computer, me.kvothe.taleos.turtle.TurtleController turtle,
            String side, WirelessNetwork network) {
        this(computer, turtle.getWorld(), null, turtle, side, network);
    }

    private ModemPeripheral(Computer computer, World world, BlockPosition position,
            me.kvothe.taleos.turtle.TurtleController turtle, String side, WirelessNetwork network) {
        this.computer = computer;
        this.world = world;
        this.position = position;
        this.turtle = turtle;
        this.dynamicPosition = null;
        this.loadedOverride = null;
        this.worldNameOverride = null;
        this.side = side;
        this.network = network;
        network.attach(this);
    }

    ModemPeripheral(Computer computer, String worldName,
            java.util.function.Supplier<BlockPosition> position, String side,
            java.util.function.BooleanSupplier loaded, WirelessNetwork network) {
        this.computer = computer;
        this.world = null;
        this.position = null;
        this.turtle = null;
        this.dynamicPosition = position;
        this.loadedOverride = loaded;
        this.worldNameOverride = worldName;
        this.side = side;
        this.network = network;
        network.attach(this);
    }

    @Override
    public String getType() {
        return "modem";
    }

    @Override
    public List<String> getMethods() {
        return METHODS;
    }

    @Override
    public Varargs call(String method, Varargs args) {
        return switch (method) {
            case "open" -> {
                network.open(this, args.arg1().checkint());
                yield LuaValue.NIL;
            }
            case "close" -> {
                network.close(this, args.arg1().checkint());
                yield LuaValue.NIL;
            }
            case "closeAll" -> {
                network.closeAll(this);
                yield LuaValue.NIL;
            }
            case "isOpen" -> LuaValue.valueOf(network.isOpen(this, args.arg1().checkint()));
            case "transmit" -> {
                network.transmit(this, args.arg(1).checkint(), args.arg(2).checkint(), args.arg(3));
                yield LuaValue.NIL;
            }
            case "isWireless" -> LuaValue.TRUE;
            default -> LuaValue.NIL;
        };
    }

    @Override
    public int ownerId() {
        return computer.getId();
    }

    @Override
    public String worldName() {
        return worldNameOverride != null ? worldNameOverride : world.getName();
    }

    @Override
    public String side() {
        return side;
    }

    @Override
    public int x() {
        return currentPosition().x;
    }

    @Override
    public int y() {
        return currentPosition().y;
    }

    @Override
    public int z() {
        return currentPosition().z;
    }

    private BlockPosition currentPosition() {
        if (dynamicPosition != null) {
            return dynamicPosition.get();
        }
        return turtle == null ? position : turtle.getPosition();
    }

    @Override
    public boolean isAlive() {
        return computer.isAlive();
    }

    @Override
    public boolean isLoaded() {
        try {
            if (loadedOverride != null) {
                return loadedOverride.getAsBoolean();
            }
            BlockPosition current = currentPosition();
            SectionReader reader = new SectionReader(world.getChunkStore());
            if (!reader.hasStorageInMemory(current.x, current.y, current.z)) {
                return false;
            }
            if (turtle != null) {
                return turtle.hasUpgrade(side, TurtleUpgradeRegistry.Kind.MODEM);
            }
            BlockType type = BlockType.getAssetMap().getAsset(
                    reader.getBlock(current.x, current.y, current.z));
            return type != null && TurtleUpgradeRegistry.MODEM_ID.equals(type.getId());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean deliver(WirelessNetwork.ModemMessage message) {
        return computer.getMachine().queueEvent("modem_message", message.side(), message.channel(),
                message.replyChannel(), message.payload(), message.distance());
    }
}

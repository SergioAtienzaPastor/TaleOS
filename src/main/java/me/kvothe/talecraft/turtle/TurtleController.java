package me.kvothe.talecraft.turtle;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockRotationUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.computer.ComputerItemMetadata;
import me.kvothe.talecraft.computer.ComputerItemStorage;
import me.kvothe.talecraft.computer.ComputerManager;
import me.kvothe.talecraft.computer.HytalePeripheralProvider;
import me.kvothe.talecraft.lua.TurtleBridge;
import me.kvothe.talecraft.peripheral.inventory.HytaleInventoryAdapter;
import me.kvothe.talecraft.peripheral.inventory.InventoryTransfers;

public final class TurtleController implements TurtleBridge {

    public static final int INVENTORY_SLOTS = 16;
    public static final int LEFT_TOOL_SLOT = 16;
    public static final int RIGHT_TOOL_SLOT = 17;
    public static final int TOOL_SLOT = RIGHT_TOOL_SLOT;
    public static final String BLOCK_ID = "TaleCraft_Turtle";

    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    private final World world;
    private final int computerId;
    private final ComputerManager manager;
    private final TurtleStateStore stateStore;
    private final TurtleToolDisplay toolDisplay;
    private BlockPosition position;
    private double moveOffsetX;
    private double moveOffsetY;
    private double moveOffsetZ;
    private long moveStartedNanos;
    private int moveTicks;
    private double turnOffsetYaw;
    private long turnStartedNanos;
    private int turnTicks;

    public TurtleController(World world, BlockPosition position, int computerId,
            ComputerManager manager, TurtleStateStore stateStore) {
        this.world = world;
        this.position = position;
        this.computerId = computerId;
        this.manager = manager;
        this.stateStore = stateStore;
        this.toolDisplay = new TurtleToolDisplay(this);
    }

    public BlockPosition getPosition() {
        return position;
    }

    public Vector3d getVisualPosition() {
        double remaining = moveTicks == 0 ? 0
                : TurtleMoveAnimation.remainingFraction(System.nanoTime() - moveStartedNanos);
        return new Vector3d(position.x + 0.5 + moveOffsetX * remaining,
                position.y + moveOffsetY * remaining, position.z + 0.5 + moveOffsetZ * remaining);
    }

    public double getVisualYaw(double logicalYaw) {
        double remaining = turnTicks == 0 ? 0
                : TurtleMoveAnimation.remainingYaw(turnOffsetYaw, System.nanoTime() - turnStartedNanos);
        return logicalYaw + remaining;
    }

    public World getWorld() {
        return world;
    }

    boolean isMoving() {
        return moveTicks > 0 || turnTicks > 0;
    }

    public void tick() {
        if (moveTicks > 0 && --moveTicks == 0) {
            clearMoveAnimation();
        }
        if (turnTicks > 0 && --turnTicks == 0) {
            clearMoveAnimation();
        }
        toolDisplay.tick();
    }

    private void startMoveAnimation(BlockPosition previous, RotationTuple rotation) {
        Vector3i delta = new Vector3i(position.x - previous.x, position.y - previous.y,
                position.z - previous.z);
        moveOffsetX = -delta.x;
        moveOffsetY = -delta.y;
        moveOffsetZ = -delta.z;
        moveStartedNanos = System.nanoTime();
        moveTicks = TurtleMoveAnimation.TICKS;
        Vector3i localDelta = new Vector3i(delta);
        rotation.undoRotationTo(localDelta);
        ChunkStore chunks = world.getChunkStore();
        BlockOperations.setBlockInteractionState(chunks,
                chunks.getChunkSectionReferenceAtBlock(position.x, position.y, position.z),
                position.x, position.y, position.z, turtleType(),
                TurtleMoveAnimation.stateFor(localDelta.x, localDelta.y, localDelta.z), true);
    }

    private void startTurnAnimation(boolean left) {
        turnOffsetYaw = left ? Math.PI / 2 : -Math.PI / 2;
        turnStartedNanos = System.nanoTime();
        turnTicks = TurtleMoveAnimation.TICKS;
        ChunkStore chunks = world.getChunkStore();
        BlockOperations.setBlockInteractionState(chunks,
                chunks.getChunkSectionReferenceAtBlock(position.x, position.y, position.z),
                position.x, position.y, position.z, turtleType(),
                TurtleMoveAnimation.turnStateFor(left), true);
    }

    private void clearMoveAnimation() {
        moveTicks = 0;
        moveOffsetX = 0;
        moveOffsetY = 0;
        moveOffsetZ = 0;
        turnTicks = 0;
        turnOffsetYaw = 0;
        BlockType current = blockType(position);
        BlockType base = turtleType();
        if (current == null || BLOCK_ID.equals(current.getId()) || !isTurtleBlock(current)
                || base == null) {
            return;
        }
        ChunkStore chunks = world.getChunkStore();
        SectionReader reader = new SectionReader(chunks);
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(
                position.x, position.y, position.z);
        BlockComponentSection components = chunks.getStore().getComponent(
                section, BlockComponentSection.getComponentType());
        int rotation = reader.getRotationIndex(position.x, position.y, position.z);
        Holder<ChunkStore> holder = BlockEntity.takeBlockEntity(chunks.getStore(), components,
                position.x, position.y, position.z);
        int settings = SetBlockSettings.NO_UPDATE_STATE | SetBlockSettings.NO_DROP_ITEMS
                | SetBlockSettings.NO_FIRE_ON_BREAK | SetBlockSettings.NO_SEND_PARTICLES
                | SetBlockSettings.NO_SEND_AUDIO;
        BlockOperations.setBlock(chunks, section, position.x, position.y, position.z,
                BlockType.getAssetMap().getIndex(BLOCK_ID), base, rotation, 0, settings);
        if (holder != null) {
            BlockEntity.setBlockEntity(chunks.getStore(), section, components,
                    position.x, position.y, position.z, base, rotation, holder);
        }
    }

    public int getSelectedSlot() {
        return stateStore.getSelectedSlot(computerId);
    }

    public SimpleItemContainer getInventory() {
        try {
            ChunkStore chunks = world.getChunkStore();
            SectionReader reader = new SectionReader(chunks);
            if (!reader.hasStorageInMemory(position.x, position.y, position.z)) {
                return null;
            }
            BlockType type = turtleType();
            if (type == null) {
                return null;
            }
            Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(
                    position.x, position.y, position.z);
            BlockComponentSection components = chunks.getStore().getComponent(
                    section, BlockComponentSection.getComponentType());
            int index = com.hypixel.hytale.math.util.ChunkUtil.indexBlock(
                    position.x, position.y, position.z);
            ItemContainerBlock containerBlock = null;
            Ref<ChunkStore> block = components.getBlockReference(index);
            if (block != null && block.isValid()) {
                containerBlock = chunks.getStore().getComponent(block, ItemContainerBlock.getComponentType());
            } else {
                Holder<ChunkStore> holder = components.getBlockHolder(index);
                if (holder != null) {
                    containerBlock = holder.getComponent(ItemContainerBlock.getComponentType());
                } else {
                    block = BlockEntity.ensureBlockEntity(chunks.getStore(), section, components,
                            position.x, position.y, position.z, type, 0);
                    if (block != null) {
                        containerBlock = chunks.getStore().getComponent(
                                block, ItemContainerBlock.getComponentType());
                    }
                }
            }
            if (containerBlock == null) {
                return null;
            }
            SimpleItemContainer inventory = containerBlock.getItemContainer();
            if (inventory.getCapacity() < RIGHT_TOOL_SLOT + 1) {
                SimpleItemContainer expanded = new SimpleItemContainer((short) (RIGHT_TOOL_SLOT + 1));
                for (short slot = 0; slot < inventory.getCapacity(); slot++) {
                    expanded.setItemStackForSlot(slot, inventory.getItemStack(slot));
                }
                containerBlock.setItemContainer(expanded);
                inventory = expanded;
            }
            for (int slot : new int[] { LEFT_TOOL_SLOT, RIGHT_TOOL_SLOT }) {
                inventory.setSlotFilter(FilterActionType.ADD, (short) slot,
                        (action, container, target, stack) -> stack == null || stack.isEmpty()
                                || TurtleUpgradeRegistry.defaults().accepts(stack));
            }
            return inventory;
        } catch (Exception e) {
            LOGGER.atWarning().log("turtle inventory unavailable at " + position + ": " + e);
            return null;
        }
    }

    public ItemStack getTool(int slot) {
        ItemContainer inventory = getInventory();
        return inventory == null ? null : inventory.getItemStack((short) slot);
    }

    public TurtleUpgradeRegistry.Kind upgradeKind(String side) {
        int slot = sideSlot(side);
        return slot < 0 ? null : TurtleUpgradeRegistry.defaults().kindOf(getTool(slot));
    }

    public boolean hasUpgrade(String side, TurtleUpgradeRegistry.Kind kind) {
        return upgradeKind(side) == kind;
    }

    public me.kvothe.talecraft.peripheral.Peripheral peripheral(String side) {
        TurtleUpgradeRegistry.Kind kind = upgradeKind(side);
        if (kind != TurtleUpgradeRegistry.Kind.MODEM && kind != TurtleUpgradeRegistry.Kind.SPEAKER) {
            return null;
        }
        var computer = manager.computerAt(world, position);
        if (computer == null) {
            return null;
        }
        return kind == TurtleUpgradeRegistry.Kind.MODEM
                ? manager.turtleModemPeripheral(this, computer, side)
                : manager.turtleSpeakerPeripheral(this, computer);
    }

    private static int sideSlot(String side) {
        return "left".equals(side) ? LEFT_TOOL_SLOT : "right".equals(side) ? RIGHT_TOOL_SLOT : -1;
    }

    public String getFacing() {
        SectionReader reader = new SectionReader(world.getChunkStore());
        Vector3i forward = new Vector3i(0, 0, 1);
        RotationTuple.get(reader.getRotationIndex(position.x, position.y, position.z))
                .applyRotationTo(forward);
        return forward.x > 0 ? "east" : forward.x < 0 ? "west"
                : forward.z > 0 ? "south" : "north";
    }

    @Override
    public Varargs call(String method, Varargs args) {
        try {
            return switch (method) {
                case "forward" -> result(move(TurtleDirection.FORWARD));
                case "back" -> result(move(TurtleDirection.BACK));
                case "up" -> result(move(TurtleDirection.UP));
                case "down" -> result(move(TurtleDirection.DOWN));
                case "turnLeft" -> result(turn(true));
                case "turnRight" -> result(turn(false));
                case "detect" -> LuaValue.valueOf(blockAt(TurtleDirection.FORWARD) != null);
                case "detectUp" -> LuaValue.valueOf(blockAt(TurtleDirection.UP) != null);
                case "detectDown" -> LuaValue.valueOf(blockAt(TurtleDirection.DOWN) != null);
                case "inspect" -> inspect(TurtleDirection.FORWARD);
                case "inspectUp" -> inspect(TurtleDirection.UP);
                case "inspectDown" -> inspect(TurtleDirection.DOWN);
                case "select" -> select(args.arg1().optint(-1));
                case "getSelectedSlot" -> LuaValue.valueOf(getSelectedSlot() + 1);
                case "getItemCount" -> LuaValue.valueOf(itemCount(optionalSlot(args)));
                case "getItemSpace" -> LuaValue.valueOf(itemSpace(optionalSlot(args)));
                case "getItemDetail" -> itemDetail(optionalSlot(args));
                case "transferTo" -> transfer(args.arg1().optint(-1), args.arg(2).optint(Integer.MAX_VALUE));
                case "compare" -> LuaValue.valueOf(compare(TurtleDirection.FORWARD));
                case "compareUp" -> LuaValue.valueOf(compare(TurtleDirection.UP));
                case "compareDown" -> LuaValue.valueOf(compare(TurtleDirection.DOWN));
                case "compareTo" -> compareTo(args.arg1().optint(-1));
                case "equip", "equipRight" -> equip(RIGHT_TOOL_SLOT);
                case "equipLeft" -> equip(LEFT_TOOL_SLOT);
                case "unequip" -> unequip(RIGHT_TOOL_SLOT);
                case "getEquippedLeft" -> equippedDetail(LEFT_TOOL_SLOT);
                case "getEquippedRight" -> equippedDetail(RIGHT_TOOL_SLOT);
                case "getFuelLevel", "getFuelLimit" -> LuaValue.valueOf("unlimited");
                case "refuel" -> failure("esta Turtle no necesita combustible");
                case "dig" -> result(dig(TurtleDirection.FORWARD, args.arg1()));
                case "digUp" -> result(dig(TurtleDirection.UP, args.arg1()));
                case "digDown" -> result(dig(TurtleDirection.DOWN, args.arg1()));
                case "attack" -> result(attack(TurtleDirection.FORWARD, args.arg1()));
                case "attackUp" -> result(attack(TurtleDirection.UP, args.arg1()));
                case "attackDown" -> result(attack(TurtleDirection.DOWN, args.arg1()));
                case "craft" -> result(craft(args.arg1().isnil() ? 64 : args.arg1().optint(-1)));
                case "place" -> result(place(TurtleDirection.FORWARD));
                case "placeUp" -> result(place(TurtleDirection.UP));
                case "placeDown" -> result(place(TurtleDirection.DOWN));
                case "suck" -> result(suck(TurtleDirection.FORWARD, args.arg1().optint(Integer.MAX_VALUE)));
                case "suckUp" -> result(suck(TurtleDirection.UP, args.arg1().optint(Integer.MAX_VALUE)));
                case "suckDown" -> result(suck(TurtleDirection.DOWN, args.arg1().optint(Integer.MAX_VALUE)));
                case "drop" -> result(drop(TurtleDirection.FORWARD, args.arg1().optint(Integer.MAX_VALUE)));
                case "dropUp" -> result(drop(TurtleDirection.UP, args.arg1().optint(Integer.MAX_VALUE)));
                case "dropDown" -> result(drop(TurtleDirection.DOWN, args.arg1().optint(Integer.MAX_VALUE)));
                default -> LuaValue.NIL;
            };
        } catch (Exception e) {
            LOGGER.atWarning().log("turtle." + method + " failed at " + position + ": " + e);
            return failure("error interno: " + e.getClass().getSimpleName());
        }
    }

    private Varargs select(int luaSlot) {
        if (luaSlot < 1 || luaSlot > INVENTORY_SLOTS) {
            return failure("slot fuera de rango (1-16)");
        }
        stateStore.setSelectedSlot(computerId, luaSlot - 1);
        try {
            stateStore.save();
        } catch (java.io.IOException e) {
            LOGGER.atWarning().log("cannot save turtle selection: " + e.getMessage());
        }
        return LuaValue.TRUE;
    }

    private int optionalSlot(Varargs args) {
        return args.isnil(1) ? getSelectedSlot() : args.arg1().optint(-1) - 1;
    }

    private int itemCount(int slot) {
        ItemStack stack = stack(slot);
        return stack == null ? 0 : stack.getQuantity();
    }

    private int itemSpace(int slot) {
        if (slot < 0 || slot >= INVENTORY_SLOTS) {
            return 0;
        }
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return 0;
        }
        ItemStack stack = inventory.getItemStack((short) slot);
        if (stack == null || stack.isEmpty()) {
            return 64;
        }
        Item item = stack.getItem();
        int max = item == null ? 64 : item.getMaxStack();
        return Math.max(0, max - stack.getQuantity());
    }

    private Varargs itemDetail(int slot) {
        ItemStack stack = stack(slot);
        if (stack == null) {
            return LuaValue.NIL;
        }
        LuaTable detail = new LuaTable();
        detail.set("name", stack.getItemId());
        detail.set("count", stack.getQuantity());
        Item item = stack.getItem();
        detail.set("maxCount", item == null ? 64 : item.getMaxStack());
        if (stack.getMaxDurability() > 0) {
            detail.set("durability", stack.getDurability());
            detail.set("maxDurability", stack.getMaxDurability());
        }
        return detail;
    }

    private Varargs transfer(int luaTarget, int count) {
        if (luaTarget < 1 || luaTarget > INVENTORY_SLOTS || count < 0) {
            return failure("slot o cantidad no válidos");
        }
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return failure("inventario no disponible");
        }
        int source = getSelectedSlot();
        if (source == luaTarget - 1) {
            return LuaValue.TRUE;
        }
        ItemStack sourceStack = inventory.getItemStack((short) source);
        if (sourceStack == null || sourceStack.isEmpty()) {
            return failure("slot vacío");
        }
        int amount = Math.min(count, sourceStack.getQuantity());
        return result(inventory.moveItemStackFromSlotToSlot((short) source, amount,
                inventory, (short) (luaTarget - 1)).succeeded()
                ? TurtleResult.OK : TurtleResult.fail("no cabe en el slot destino"));
    }

    private Varargs equip(int toolSlot) {
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return failure("inventario no disponible");
        }
        int selected = getSelectedSlot();
        ItemStack stack = inventory.getItemStack((short) selected);
        if (stack != null && !stack.isEmpty() && !TurtleUpgradeRegistry.defaults().accepts(stack)) {
            return failure("el objeto seleccionado no es una mejora válida");
        }
        ItemStack tool = inventory.getItemStack((short) toolSlot);
        inventory.setItemStackForSlot((short) selected, tool);
        inventory.setItemStackForSlot((short) toolSlot, stack);
        return LuaValue.TRUE;
    }

    private Varargs unequip(int toolSlot) {
        ItemStack selected = stack(getSelectedSlot());
        if (selected != null) {
            return failure("selecciona un slot vacío");
        }
        return equip(toolSlot);
    }

    private Varargs equippedDetail(int toolSlot) {
        ItemStack tool = getTool(toolSlot);
        if (tool == null || tool.isEmpty()) {
            return LuaValue.NIL;
        }
        LuaTable detail = new LuaTable();
        detail.set("name", tool.getItemId());
        detail.set("count", tool.getQuantity());
        if (tool.getMaxDurability() > 0) {
            detail.set("durability", tool.getDurability());
            detail.set("maxDurability", tool.getMaxDurability());
        }
        return detail;
    }

    private boolean compare(TurtleDirection direction) {
        BlockType target = blockAt(direction);
        ItemStack selected = stack(getSelectedSlot());
        return target != null && selected != null && target.getId().equals(selected.getBlockKey());
    }

    private Varargs compareTo(int luaSlot) {
        if (luaSlot < 1 || luaSlot > INVENTORY_SLOTS) {
            return failure("slot fuera de rango (1-16)");
        }
        ItemStack first = stack(getSelectedSlot());
        ItemStack second = stack(luaSlot - 1);
        if (first == null || second == null) {
            return LuaValue.valueOf(first == second);
        }
        return LuaValue.valueOf(first.getItemId().equals(second.getItemId()));
    }

    private ItemStack stack(int slot) {
        if (slot < 0 || slot >= INVENTORY_SLOTS) {
            return null;
        }
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return null;
        }
        ItemStack stack = inventory.getItemStack((short) slot);
        return stack == null || stack.isEmpty() ? null : stack;
    }

    private TurtleResult move(TurtleDirection direction) {
        if (isMoving()) {
            clearMoveAnimation();
        }
        BlockPosition destination = target(direction);
        ChunkStore chunks = world.getChunkStore();
        SectionReader reader = new SectionReader(chunks);
        if (!reader.hasStorageInMemory(position.x, position.y, position.z)
                || !reader.hasStorageInMemory(destination.x, destination.y, destination.z)) {
            return TurtleResult.fail("chunk no cargado");
        }
        BlockType turtle = turtleType();
        if (turtle == null || !isTurtleBlock(blockType(position))) {
            return TurtleResult.fail("la turtle ya no está en su posición");
        }
        if (blockType(destination) != null) {
            return TurtleResult.fail("destino ocupado");
        }
        if (getInventory() == null) {
            return TurtleResult.fail("inventario no disponible");
        }

        Ref<ChunkStore> sourceSection = chunks.getChunkSectionReferenceAtBlock(position.x, position.y, position.z);
        Ref<ChunkStore> destinationSection = chunks.getChunkSectionReferenceAtBlock(
                destination.x, destination.y, destination.z);
        BlockComponentSection sourceComponents = chunks.getStore().getComponent(
                sourceSection, BlockComponentSection.getComponentType());
        BlockComponentSection destinationComponents = chunks.getStore().getComponent(
                destinationSection, BlockComponentSection.getComponentType());
        int rotation = reader.getRotationIndex(position.x, position.y, position.z);
        int filler = reader.getFiller(position.x, position.y, position.z);
        int turtleId = BlockType.getAssetMap().getIndex(BLOCK_ID);
        Holder<ChunkStore> holder = BlockEntity.takeBlockEntity(chunks.getStore(), sourceComponents,
                position.x, position.y, position.z);
        if (holder == null) {
            return TurtleResult.fail("estado del bloque no disponible");
        }
        int settings = SetBlockSettings.NO_UPDATE_STATE | SetBlockSettings.NO_DROP_ITEMS
                | SetBlockSettings.NO_FIRE_ON_BREAK | SetBlockSettings.NO_SEND_PARTICLES
                | SetBlockSettings.NO_SEND_AUDIO;
        if (!BlockOperations.setBlock(chunks, destinationSection, destination.x, destination.y,
                destination.z, turtleId, turtle, rotation, 0, settings)) {
            BlockEntity.setBlockEntity(chunks.getStore(), sourceSection, sourceComponents,
                    position.x, position.y, position.z, turtle, rotation, holder);
            return TurtleResult.fail("no se pudo ocupar el destino");
        }
        if (!BlockOperations.setBlock(chunks, sourceSection, position.x, position.y, position.z,
                BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, settings)) {
            BlockOperations.setBlock(chunks, destinationSection, destination.x, destination.y,
                    destination.z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, settings);
            BlockEntity.setBlockEntity(chunks.getStore(), sourceSection, sourceComponents,
                    position.x, position.y, position.z, turtle, rotation, holder);
            return TurtleResult.fail("no se pudo liberar el origen");
        }
        BlockEntity.setBlockEntity(chunks.getStore(), destinationSection, destinationComponents,
                destination.x, destination.y, destination.z, turtle, rotation, holder);
        BlockPosition previous = position;
        position = destination;
        manager.moveComputer(world, previous, destination, this);
        startMoveAnimation(previous, RotationTuple.get(rotation));
        return TurtleResult.OK;
    }

    private TurtleResult turn(boolean left) {
        if (isMoving()) {
            clearMoveAnimation();
        }
        ChunkStore chunks = world.getChunkStore();
        SectionReader reader = new SectionReader(chunks);
        if (!reader.hasStorageInMemory(position.x, position.y, position.z)) {
            return TurtleResult.fail("chunk no cargado");
        }
        BlockType type = turtleType();
        if (type == null) {
            return TurtleResult.fail("bloque Turtle no registrado");
        }
        int rotationIndex = reader.getRotationIndex(position.x, position.y, position.z);
        RotationTuple rotated = BlockRotationUtil.getRotated(RotationTuple.get(rotationIndex), Axis.Y,
                left ? Rotation.TwoSeventy : Rotation.Ninety, type.getVariantRotation());
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(position.x, position.y, position.z);
        boolean changed = BlockOperations.setBlock(chunks, section, position.x, position.y, position.z,
                BlockType.getAssetMap().getIndex(BLOCK_ID), type, rotated.index(),
                reader.getFiller(position.x, position.y, position.z), SetBlockSettings.NO_UPDATE_STATE);
        if (!changed) {
            return TurtleResult.fail("no se pudo girar");
        }
        startTurnAnimation(left);
        return TurtleResult.OK;
    }

    private TurtleResult dig(TurtleDirection direction, LuaValue sideArg) {
        BlockPosition target = target(direction);
        BlockType type = blockType(target);
        if (type == null) {
            return TurtleResult.fail("no hay ningún bloque");
        }
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return TurtleResult.fail("inventario no disponible");
        }
        String side = sideArg.isnil() ? null : sideArg.checkjstring().toLowerCase(java.util.Locale.ROOT);
        if (side != null && !side.equals("left") && !side.equals("right")) {
            return TurtleResult.fail("el lado debe ser left o right");
        }
        TurtleResult firstFailure = null;
        for (int slot : new int[] { LEFT_TOOL_SLOT, RIGHT_TOOL_SLOT }) {
            if (side != null && !side.equals(slot == LEFT_TOOL_SLOT ? "left" : "right")) {
                continue;
            }
            ItemStack toolStack = inventory.getItemStack((short) slot);
            if (toolStack == null || toolStack.isEmpty() || toolStack.getItem() == null
                    || toolStack.getItem().getTool() == null) {
                continue;
            }
            BlockGathering gathering = type.getGathering();
            BlockBreakingDropType breaking = gathering == null ? null : gathering.getBreaking();
            if (breaking != null && breaking.getGatherType() != null && !breaking.getGatherType().isBlank()
                    && !canHarvest(toolStack.getItem().getTool(), breaking)) {
                if (firstFailure == null) {
                    firstFailure = TurtleResult.fail("herramienta no válida o de calidad insuficiente");
                }
                continue;
            }
            boolean machine = ComputerItemMetadata.isMachine(type.getId());
            boolean usbDrive = me.kvothe.talecraft.storage.UsbDriveManager.BLOCK_ID.equals(type.getId());
            var usbContainer = usbDrive ? manager.usbDrives().containerAt(world, target) : null;
            ItemStack penDrive = usbContainer == null ? null : usbContainer.getItemStack((short) 0);
            List<ItemStack> drops;
            Integer machineId = null;
            if (machine) {
                if (!inventory.canAddItemStack(new ItemStack(type.getId(), 1))) {
                    return TurtleResult.fail("inventario lleno");
                }
                BsonDocument holder = ComputerItemStorage.copyHolder(world, target);
                machineId = manager.unbindAt(world, target);
                drops = List.of(ComputerItemMetadata.createDrop(type.getId(), machineId, holder));
            } else {
                drops = new java.util.ArrayList<>(breaking == null
                        ? BlockHarvestUtils.getDrops(type, 1, null, null)
                        : BlockHarvestUtils.getDrops(type, breaking.getQuantity(),
                                breaking.getItemId(), breaking.getDropListId()));
                if (me.kvothe.talecraft.storage.PenDriveItemMetadata.isPenDrive(penDrive)) {
                    drops.add(penDrive);
                }
                if (!inventory.canAddItemStacks(drops)) {
                    return TurtleResult.fail("inventario lleno");
                }
            }
            ChunkStore chunks = world.getChunkStore();
            Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(target.x, target.y, target.z);
            if (!BlockOperations.setBlock(chunks, section, target.x, target.y, target.z,
                    BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0,
                    SetBlockSettings.NO_DROP_ITEMS | SetBlockSettings.NO_FIRE_ON_BREAK)) {
                if (machineId != null) {
                    manager.bindAt(world, target, machineId);
                }
                return TurtleResult.fail("no se pudo romper el bloque");
            }
            if (usbDrive) {
                manager.usbDrives().driveBroken(world, target);
            }
            inventory.addItemStacks(drops);
            toolDisplay.swing(slot);
            return TurtleResult.OK;
        }
        return firstFailure != null ? firstFailure : TurtleResult.fail("no hay ninguna herramienta equipada");
    }

    private static boolean canHarvest(ItemTool tool, BlockBreakingDropType breaking) {
        if (tool == null) {
            return false;
        }
        for (ItemToolSpec spec : tool.getSpecs()) {
            if (breaking.getGatherType().equals(spec.getGatherType())
                    && spec.getQuality() >= breaking.getQuality()) {
                return true;
            }
        }
        return false;
    }

    private TurtleResult attack(TurtleDirection direction, LuaValue sideArg) {
        String side = sideArg.isnil() ? null : sideArg.checkjstring().toLowerCase(java.util.Locale.ROOT);
        if (side != null && !side.equals("left") && !side.equals("right")) {
            return TurtleResult.fail("el lado debe ser left o right");
        }
        BlockPosition target = target(direction);
        if (blockType(target) != null) {
            return TurtleResult.fail("hay un bloque sólido delante");
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        SpatialResource<Ref<EntityStore>, EntityStore> entities = store.getResource(
                EntityModule.get().getEntitySpatialResourceType());
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        Vector3d center = new Vector3d(target.x + 0.5, target.y + 0.5, target.z + 0.5);
        entities.getSpatialStructure().collect(center, 1.75, nearby);
        List<TurtleCombat.AttackTarget<Ref<EntityStore>>> candidates = new ArrayList<>();
        for (Ref<EntityStore> ref : nearby) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            boolean damageable = stats != null && stats.get(DefaultEntityStatTypes.getHealth()) != null;
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            String role = npc == null ? null : npc.getRoleName();
            boolean display = store.getComponent(ref,
                    com.hypixel.hytale.server.core.entity.entities.BlockEntity.getComponentType()) != null
                    || "Empty_Role".equals(role) || "TaleCraft_MonitorDisplay".equals(role);
            Vector3d entityPosition = transform.getPosition();
            candidates.add(new TurtleCombat.AttackTarget<>(ref, entityPosition.x, entityPosition.y,
                    entityPosition.z, store.getComponent(ref, ItemComponent.getComponentType()) != null,
                    display, damageable));
        }
        Ref<EntityStore> victim = TurtleCombat.selectTarget(candidates, target);
        if (victim == null) {
            return TurtleResult.fail("no hay ninguna entidad dañable");
        }
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return TurtleResult.fail("inventario no disponible");
        }
        int weaponSlot = side == null ? -1 : sideSlot(side);
        ItemStack weapon = null;
        if (side == null) {
            for (int slot : new int[] { LEFT_TOOL_SLOT, RIGHT_TOOL_SLOT }) {
                ItemStack candidate = inventory.getItemStack((short) slot);
                if (TurtleUpgradeRegistry.defaults().kindOf(candidate) == TurtleUpgradeRegistry.Kind.TOOL) {
                    weaponSlot = slot;
                    weapon = candidate;
                    break;
                }
            }
        } else {
            weapon = inventory.getItemStack((short) weaponSlot);
        }
        Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL,
                TurtleCombat.basicDamage(weapon));
        DamageSystems.executeDamage(victim, store, damage);
        if (damage.isCancelled()) {
            return TurtleResult.fail("la entidad no puede recibir daño");
        }
        if (weaponSlot >= 0 && weapon != null && !weapon.isEmpty() && weapon.getItem() != null) {
            toolDisplay.swing(weaponSlot);
        }
        return TurtleResult.OK;
    }

    private TurtleResult craft(int limit) {
        if (limit < 1 || limit > 64) {
            return TurtleResult.fail("el límite debe estar entre 1 y 64");
        }
        if (!hasUpgrade("left", TurtleUpgradeRegistry.Kind.CRAFTING)
                && !hasUpgrade("right", TurtleUpgradeRegistry.Kind.CRAFTING)) {
            return TurtleResult.fail("se necesita una mejora de crafting");
        }
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return TurtleResult.fail("inventario no disponible");
        }
        List<ItemStack> slots = new ArrayList<>(INVENTORY_SLOTS);
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getItemStack((short) slot);
            slots.add(stack == null || stack.isEmpty() ? null : stack);
        }
        List<CraftingRecipe> recipes = new ArrayList<>(
                CraftingRecipe.getAssetMap().getAssetMap().values());
        recipes.sort(java.util.Comparator.comparing(CraftingRecipe::getId));
        boolean outputBlocked = false;
        TurtleCrafting.StackOps<MaterialQuantity, ItemStack> ops = craftingStackOps();
        for (CraftingRecipe recipe : recipes) {
            if (!isTurtleRecipe(recipe)) {
                continue;
            }
            TurtleCrafting.Recipe<MaterialQuantity, ItemStack> inputsOutputs =
                    new TurtleCrafting.Recipe<>(CraftingManager.getInputMaterials(recipe),
                            CraftingManager.getOutputItemStacks(recipe));
            if (inputsOutputs.inputs().isEmpty() || inputsOutputs.outputs().isEmpty()) {
                continue;
            }
            TurtleCrafting.Plan<ItemStack> plan = TurtleCrafting.plan(slots, inputsOutputs, limit, ops);
            outputBlocked |= plan.outputBlocked();
            if (plan.crafts() == 0) {
                continue;
            }
            for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
                inventory.setItemStackForSlot((short) slot, plan.slots().get(slot));
            }
            return TurtleResult.OK;
        }
        return TurtleResult.fail(outputBlocked ? "no cabe la salida en el inventario"
                : "no hay una receta de crafting con los materiales disponibles");
    }

    private static boolean isTurtleRecipe(CraftingRecipe recipe) {
        var requirements = recipe.getBenchRequirement();
        if (requirements == null) {
            return false;
        }
        for (var requirement : requirements) {
            if (requirement.type == BenchType.Crafting
                    && requirement.requiredTierLevel <= 0
                    && (requirement.requiredAugmentTags == null
                            || requirement.requiredAugmentTags.length == 0)) {
                return true;
            }
        }
        return false;
    }

    private static TurtleCrafting.StackOps<MaterialQuantity, ItemStack> craftingStackOps() {
        return new TurtleCrafting.StackOps<>() {
            @Override
            public int quantity(ItemStack stack) {
                return stack == null || stack.isEmpty() ? 0 : stack.getQuantity();
            }

            @Override
            public int required(MaterialQuantity material) {
                return material.getQuantity();
            }

            @Override
            public int maxStack(ItemStack stack) {
                Item item = stack.getItem();
                return item == null ? 64 : item.getMaxStack();
            }

            @Override
            public boolean matches(MaterialQuantity material, ItemStack stack) {
                return CraftingManager.matches(material, stack);
            }

            @Override
            public boolean sameType(ItemStack first, ItemStack second) {
                return ItemStack.isStackableWith(first, second);
            }

            @Override
            public ItemStack withQuantity(ItemStack stack, int quantity) {
                return quantity == 0 ? null : stack.withQuantity(quantity);
            }

            @Override
            public MaterialQuantity withRequired(MaterialQuantity material, int quantity) {
                return material.clone(quantity);
            }
        };
    }

    private TurtleResult place(TurtleDirection direction) {
        BlockPosition target = target(direction);
        if (blockType(target) != null) {
            return TurtleResult.fail("destino ocupado");
        }
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return TurtleResult.fail("inventario no disponible");
        }
        int selected = getSelectedSlot();
        ItemStack stack = inventory.getItemStack((short) selected);
        if (stack == null || stack.isEmpty()) {
            return TurtleResult.fail("slot vacío");
        }
        String blockKey = stack.getBlockKey();
        BlockType type = blockKey == null ? null : BlockType.getAssetMap().getAsset(blockKey);
        if (type == null) {
            return TurtleResult.fail("el objeto seleccionado no es un bloque");
        }
        ChunkStore chunks = world.getChunkStore();
        SectionReader reader = new SectionReader(chunks);
        if (!reader.hasStorageInMemory(target.x, target.y, target.z)) {
            return TurtleResult.fail("chunk no cargado");
        }
        boolean machine = ComputerItemMetadata.isMachine(stack.getItemId());
        if (machine) {
            try {
                manager.bindAt(world, target, ComputerItemMetadata.computerId(stack));
            } catch (RuntimeException e) {
                return TurtleResult.fail(e.getMessage());
            }
        }
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(target.x, target.y, target.z);
        if (!BlockOperations.setBlock(chunks, section, target.x, target.y, target.z,
                BlockType.getAssetMap().getIndex(type.getId()), type, 0, 0, SetBlockSettings.NONE)) {
            if (machine) {
                manager.unbindAt(world, target);
            }
            return TurtleResult.fail("no se pudo colocar el bloque");
        }
        try {
            if (machine) {
                ComputerItemStorage.restoreHolder(world, target, type, 0, stack);
            }
        } catch (RuntimeException e) {
            BlockOperations.setBlock(chunks, section, target.x, target.y, target.z,
                    BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SetBlockSettings.NO_DROP_ITEMS);
            manager.unbindAt(world, target);
            return TurtleResult.fail(me.kvothe.talecraft.I18n.t("Could not restore machine: ")
                    + e.getMessage());
        }
        inventory.removeItemStackFromSlot((short) selected, 1);
        return TurtleResult.OK;
    }

    private TurtleResult suck(TurtleDirection direction, int limit) {
        if (limit <= 0) {
            return TurtleResult.fail("cantidad no válida");
        }
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return TurtleResult.fail("inventario no disponible");
        }
        BlockPosition target = target(direction);
        ItemContainer container = HytalePeripheralProvider.containerAt(world, target);
        if (container != null) {
            int moved = InventoryTransfers.pull(new HytaleInventoryAdapter(container),
                    new HytaleInventoryAdapter(inventory), limit);
            return moved > 0 ? TurtleResult.OK
                    : TurtleResult.fail("no hay objetos que recoger o el inventario está lleno");
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        SpatialResource<Ref<EntityStore>, EntityStore> items = store.getResource(
                EntityModule.get().getItemSpatialResourceType());
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        Vector3d center = new Vector3d(target.x + 0.5, target.y + 0.5, target.z + 0.5);
        items.getSpatialStructure().collect(center, 2.5, nearby);
        boolean pickedAny = false;
        List<ItemStack> overflow = new ArrayList<>();
        for (Ref<EntityStore> ref : nearby) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            ItemComponent item = store.getComponent(ref, ItemComponent.getComponentType());
            if (item == null || !item.canPickUp()) {
                continue;
            }
            ItemStack picked = ItemComponent.addToItemContainer(store, ref, inventory);
            if (picked == null || picked.isEmpty()) {
                continue;
            }
            pickedAny = true;
            int excess = picked.getQuantity() - limit;
            if (excess > 0) {
                ItemStack remainder = picked.withQuantity(excess);
                inventory.removeItemStack(remainder);
                overflow.add(remainder);
                limit = 0;
            } else {
                limit -= picked.getQuantity();
            }
            if (limit <= 0) {
                break;
            }
        }
        if (!overflow.isEmpty()) {
            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(store, overflow,
                    center, Rotation3f.IDENTITY);
            store.addEntities(holders, AddReason.SPAWN);
        }
        return pickedAny ? TurtleResult.OK : TurtleResult.fail("no hay objetos que recoger");
    }

    private TurtleResult drop(TurtleDirection direction, int count) {
        if (count <= 0) {
            return TurtleResult.fail("cantidad no válida");
        }
        ItemContainer inventory = getInventory();
        if (inventory == null) {
            return TurtleResult.fail("inventario no disponible");
        }
        int selected = getSelectedSlot();
        ItemStack stack = inventory.getItemStack((short) selected);
        if (stack == null || stack.isEmpty()) {
            return TurtleResult.fail("slot vacío");
        }
        int amount = Math.min(count, stack.getQuantity());
        BlockPosition target = target(direction);
        ItemContainer container = HytalePeripheralProvider.containerAt(world, target);
        if (container != null) {
            int moved = InventoryTransfers.push(new HytaleInventoryAdapter(inventory), selected,
                    new HytaleInventoryAdapter(container), amount);
            return moved > 0 ? TurtleResult.OK
                    : TurtleResult.fail("el contenedor está lleno o no acepta el objeto");
        }
        ItemStack dropped = inventory.removeItemStackFromSlot((short) selected, amount).getOutput();
        if (dropped == null || dropped.isEmpty()) {
            return TurtleResult.fail("no se pudo retirar el objeto");
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(store, List.of(dropped),
                new Vector3d(target.x + 0.5, target.y + 0.5, target.z + 0.5), Rotation3f.IDENTITY);
        if (holders.length != 1) {
            inventory.addItemStack(dropped);
            return TurtleResult.fail("no se pudo crear el drop");
        }
        Ref<EntityStore>[] refs = store.addEntities(holders, AddReason.SPAWN);
        if (refs == null || refs.length != 1 || refs[0] == null || !refs[0].isValid()) {
            inventory.addItemStack(dropped);
            return TurtleResult.fail("el mundo no aceptó el drop");
        }
        return TurtleResult.OK;
    }

    private Varargs inspect(TurtleDirection direction) {
        BlockType type = blockAt(direction);
        if (type == null) {
            return failure("no hay ningún bloque");
        }
        LuaTable detail = new LuaTable();
        detail.set("name", type.getId());
        return LuaValue.varargsOf(new LuaValue[] { LuaValue.TRUE, detail });
    }

    private BlockType blockAt(TurtleDirection direction) {
        return blockType(target(direction));
    }

    private BlockPosition target(TurtleDirection direction) {
        if (direction == TurtleDirection.UP) {
            return new BlockPosition(position.x, position.y + 1, position.z);
        }
        if (direction == TurtleDirection.DOWN) {
            return new BlockPosition(position.x, position.y - 1, position.z);
        }
        SectionReader reader = new SectionReader(world.getChunkStore());
        int rotation = reader.getRotationIndex(position.x, position.y, position.z);
        Vector3i forward = new Vector3i(0, 0, 1);
        RotationTuple.get(rotation).applyRotationTo(forward);
        int sign = direction == TurtleDirection.BACK ? -1 : 1;
        return new BlockPosition(position.x + forward.x * sign, position.y,
                position.z + forward.z * sign);
    }

    private BlockType blockType(BlockPosition pos) {
        SectionReader reader = new SectionReader(world.getChunkStore());
        if (!reader.hasStorageInMemory(pos.x, pos.y, pos.z)) {
            return null;
        }
        int id = reader.getBlock(pos.x, pos.y, pos.z);
        return id == BlockType.EMPTY_ID ? null : BlockType.getAssetMap().getAsset(id);
    }

    private static BlockType turtleType() {
        return BlockType.getAssetMap().getAsset(BLOCK_ID);
    }

    private static boolean isTurtleBlock(BlockType type) {
        return type != null && TurtleBlockIds.isTurtleBlockId(type.getId());
    }

    private static Varargs result(TurtleResult result) {
        return result.success() ? LuaValue.TRUE : failure(result.error());
    }

    private static Varargs failure(String message) {
        return LuaValue.varargsOf(new LuaValue[] { LuaValue.FALSE, LuaValue.valueOf(message) });
    }
}

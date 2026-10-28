package me.kvothe.talecraft;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

import me.kvothe.talecraft.computer.Computer;
import me.kvothe.talecraft.computer.ComputerManager;
import me.kvothe.talecraft.computer.HytaleWorldBridge;

/**
 * {@code /talecraft <world> <x> <y> <z> <línea...>} boots the computer at that
 * block if needed and queues one shell line. Usable from the server console,
 * which makes the whole shell scriptable for automated testing and admin work.
 */
public class TaleCraftCommand extends CommandBase {

    private final ComputerManager manager;
    private final RequiredArg<World> worldArg;
    private final RequiredArg<Integer> xArg;
    private final RequiredArg<Integer> yArg;
    private final RequiredArg<Integer> zArg;
    private final RequiredArg<String> lineArg;

    public TaleCraftCommand(String pluginName, String pluginVersion, ComputerManager manager) {
        super("talecraft", pluginName + " v" + pluginVersion
                + ": ejecuta una línea de shell en un Computer (admin/test)");
        this.manager = manager;
        this.worldArg = withRequiredArg("world", "mundo del bloque", ArgTypes.WORLD);
        this.xArg = withRequiredArg("x", "coordenada x del bloque", ArgTypes.INTEGER);
        this.yArg = withRequiredArg("y", "coordenada y del bloque", ArgTypes.INTEGER);
        this.zArg = withRequiredArg("z", "coordenada z del bloque", ArgTypes.INTEGER);
        this.lineArg = withRequiredArg("linea", "línea de shell a ejecutar", ArgTypes.GREEDY_STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        World world = ctx.get(worldArg);
        int x = ctx.get(xArg);
        int y = ctx.get(yArg);
        int z = ctx.get(zArg);
        String line = ctx.get(lineArg);
        if ("__probe".equals(line == null ? "" : line.trim())) {
            HytaleWorldBridge.debugProbe(world, x, y, z);
            ctx.sendMessage(Message.raw("[TaleCraft] probe en el log del servidor"));
            return;
        }
        world.execute(() -> executeOnWorld(ctx, world, x, y, z, line));
    }

    private void executeOnWorld(CommandContext ctx, World world, int x, int y, int z, String line) {
        if (line == null || line.isBlank()) {
            ctx.sendMessage(Message.raw("[TaleCraft] línea vacía"));
            return;
        }
        String action = line.trim();
        if ("__displaytest".equals(action)) {
            manager.displayTest(world, new BlockPosition(x, y, z));
            ctx.sendMessage(Message.raw("[TaleCraft] patrón de prueba (4 caras) en "
                    + x + "," + y + "," + z));
            return;
        }
        if ("__displayclear".equals(action)) {
            manager.displayClear(world, new BlockPosition(x, y, z));
            ctx.sendMessage(Message.raw("[TaleCraft] display limpiado en " + x + "," + y + "," + z));
            return;
        }
        if ("__blockid".equals(action)) {
            {
                String id = new HytaleWorldBridge(world, new BlockPosition(x, y, z), -1, manager)
                        .getBlockId(x, y, z);
                ctx.sendMessage(Message.raw("[TaleCraft] bloque en " + x + "," + y + "," + z + " = " + id));
            }
            return;
        }
        if ("__cleanmobs".equals(action)) {
            manager.requestDisplayCleanup(world);
            ctx.sendMessage(Message.raw("[TaleCraft] limpieza de displays/maniquís programada en "
                    + world.getName()));
            return;
        }
        if ("__stop".equals(action)) {
            BlockPosition pos = new BlockPosition(x, y, z);
            if (!manager.isRunningAt(world, pos)) {
                ctx.sendMessage(Message.raw("[TaleCraft] no hay ningún ordenador encendido ahí"));
                return;
            }
            manager.bootAt(world, pos).requestTerminate();
            ctx.sendMessage(Message.raw("[TaleCraft] terminate enviado"));
            return;
        }
        if ("__modemplace".equals(action)) {
            placeAdminBlock(ctx, world, x, y, z, "TaleCraft_Modem", "módem");
            return;
        }
        if ("__speakerplace".equals(action)) {
            placeAdminBlock(ctx, world, x, y, z,
                    me.kvothe.talecraft.turtle.TurtleUpgradeRegistry.SPEAKER_ID, "altavoz");
            return;
        }
        if ("__monitorplace".equals(action)) {
            placeAdminBlock(ctx, world, x, y, z, "TaleCraft_Monitor", "monitor");
            return;
        }
        if ("__usbplace".equals(action)) {
            placeAdminBlock(ctx, world, x, y, z,
                    me.kvothe.talecraft.storage.UsbDriveManager.BLOCK_ID, "lector USB");
            return;
        }
        if (action.equals("__usbinsert") || action.startsWith("__usbinsert ")) {
            String label = action.length() > "__usbinsert".length()
                    ? action.substring("__usbinsert".length()).trim() : null;
            try {
                var drive = manager.usbDrives().insertAdmin(world, new BlockPosition(x, y, z), label);
                ctx.sendMessage(Message.raw("[TaleCraft] PenDrive #" + drive.id()
                        + " insertado: " + drive.label()));
            } catch (RuntimeException exception) {
                ctx.sendMessage(Message.raw("[TaleCraft] no se pudo insertar: " + exception.getMessage()));
            }
            return;
        }
        if ("__nasplace".equals(action)) {
            placeAdminBlock(ctx, world, x, y, z,
                    me.kvothe.talecraft.storage.NasManager.BLOCK_ID, "NAS");
            return;
        }
        if (action.startsWith("__nasinsert ")) {
            String[] parts = action.split(" ", 3);
            if (parts.length < 2) {
                ctx.sendMessage(Message.raw("[TaleCraft] uso: __nasinsert <slot 0-7> [etiqueta]"));
                return;
            }
            try {
                var drive = manager.nas().insertAdmin(world, new BlockPosition(x, y, z),
                        Integer.parseInt(parts[1]), parts.length == 3 ? parts[2] : null);
                ctx.sendMessage(Message.raw("[TaleCraft] NAS PenDrive #" + drive.id()
                        + " insertado: " + drive.label()));
            } catch (RuntimeException exception) {
                ctx.sendMessage(Message.raw("[TaleCraft] no se pudo insertar en NAS: "
                        + exception.getMessage()));
            }
            return;
        }
        if ("__turtleplace".equals(action)) {
            placeAdminBlock(ctx, world, x, y, z, "TaleCraft_Turtle", "turtle");
            return;
        }
        if (action.startsWith("__turtlegive ")) {
            String[] parts = action.split(" ");
            if (parts.length != 3) {
                ctx.sendMessage(Message.raw("[TaleCraft] uso: __turtlegive <item> <cantidad>"));
                return;
            }
            Computer turtle = manager.bootTurtleAt(world, new BlockPosition(x, y, z));
            {
                var controller = manager.turtleControllerAt(world, new BlockPosition(x, y, z));
                var inventory = controller.getInventory();
                if (inventory == null) {
                    ctx.sendMessage(Message.raw("[TaleCraft] inventario no disponible"));
                    return;
                }
                var stack = new com.hypixel.hytale.server.core.inventory.ItemStack(
                        parts[1], Integer.parseInt(parts[2]));
                boolean added = inventory.addItemStack(stack).succeeded();
                ctx.sendMessage(Message.raw("[TaleCraft] turtle #" + turtle.getId()
                        + " recibido=" + added));
            }
            return;
        }
        if ("__turtleboot".equals(action)) {
            Computer turtle = manager.bootTurtleAt(world, new BlockPosition(x, y, z));
            ctx.sendMessage(Message.raw("[TaleCraft] turtle #" + turtle.getId() + " lista"));
            return;
        }
        if (action.startsWith("__turtle ")) {
            Computer turtle = manager.bootTurtleAt(world, new BlockPosition(x, y, z));
            String turtleLine = action.substring("__turtle ".length());
            turtle.queueStdin(turtleLine);
            ctx.sendMessage(Message.raw("[TaleCraft] turtle #" + turtle.getId()
                    + " <- '" + turtleLine + "'"));
            return;
        }
        Computer computer = manager.bootAt(world, new BlockPosition(x, y, z));
        computer.queueStdin(line);
        ctx.sendMessage(Message.raw("[TaleCraft] computer #" + computer.getId() + " <- '" + line + "'"));
    }

    private void placeAdminBlock(CommandContext ctx, World world, int x, int y, int z,
            String blockId, String label) {
        ChunkStore chunks = world.getChunkStore();
        SectionReader reader = new SectionReader(chunks);
        if (!reader.hasStorageInMemory(x, y, z)) {
            ctx.sendMessage(Message.raw("[TaleCraft] chunk no cargado"));
            return;
        }
        if (reader.getBlock(x, y, z) != BlockType.EMPTY_ID) {
            ctx.sendMessage(Message.raw("[TaleCraft] destino ocupado"));
            return;
        }
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        boolean placed = BlockOperations.setBlock(chunks,
                chunks.getChunkSectionReferenceAtBlock(x, y, z), x, y, z,
                BlockType.getAssetMap().getIndex(type.getId()), type, 0, 0, SetBlockSettings.NONE);
        if (placed && com.hypixel.hytale.server.core.modules.block.BlockEntity.declaresComponent(type,
                com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock
                        .getComponentType())) {
            var section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
            var components = chunks.getStore().getComponent(section,
                    com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
                            .getComponentType());
            com.hypixel.hytale.server.core.modules.block.BlockEntity.ensureBlockEntity(
                    chunks.getStore(), section, components, x, y, z, type, 0);
        }
        if (placed && "TaleCraft_Monitor".equals(blockId)) {
            manager.requestMonitorRebuild(world, new BlockPosition(x, y, z));
        }
        if (placed && me.kvothe.talecraft.storage.NasManager.BLOCK_ID.equals(blockId)) {
            manager.nas().registerUnit(world, new BlockPosition(x, y, z));
        }
        ctx.sendMessage(Message.raw("[TaleCraft] " + label + " colocado=" + placed));
    }
}

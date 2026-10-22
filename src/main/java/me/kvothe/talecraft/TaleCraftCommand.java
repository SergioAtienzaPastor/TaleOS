package me.kvothe.talecraft;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.universe.world.World;

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
        if (line == null || line.isBlank()) {
            ctx.sendMessage(Message.raw("[TaleCraft] línea vacía"));
            return;
        }
        if ("__probe".equals(line.trim())) {
            HytaleWorldBridge.debugProbe(world, x, y, z);
            ctx.sendMessage(Message.raw("[TaleCraft] probe en el log del servidor"));
            return;
        }
        String action = line.trim();
        if ("__displaytest".equals(action)) {
            manager.displayTest(world, new BlockPosition(x, y, z), null);
            ctx.sendMessage(Message.raw("[TaleCraft] patrón de prueba en " + x + "," + y + "," + z));
            return;
        }
        if ("__displayraw".equals(action)) {
            manager.displayTest(world, new BlockPosition(x, y, z),
                    "linea1 [#ff0000]ROJO\nlinea2 &aVERDE&r normal\nlinea3 <#00ffff>cyan</#00ffff>");
            ctx.sendMessage(Message.raw("[TaleCraft] raw nameplate test en " + x + "," + y + "," + z));
            return;
        }
        if ("__displayclear".equals(action)) {
            manager.displayClear(world, new BlockPosition(x, y, z));
            ctx.sendMessage(Message.raw("[TaleCraft] display limpiado en " + x + "," + y + "," + z));
            return;
        }
        if ("__blockid".equals(action)) {
            String id = new HytaleWorldBridge(world, new BlockPosition(x, y, z), -1, manager)
                    .getBlockId(x, y, z);
            ctx.sendMessage(Message.raw("[TaleCraft] bloque en " + x + "," + y + "," + z + " = " + id));
            return;
        }
        if ("__cleanmobs".equals(action)) {
            manager.requestDisplayCleanup(world);
            ctx.sendMessage(Message.raw("[TaleCraft] limpieza de displays/maniquís programada en "
                    + world.getName()));
            return;
        }
        Computer computer = manager.bootAt(world, new BlockPosition(x, y, z));
        computer.queueStdin(line);
        ctx.sendMessage(Message.raw("[TaleCraft] computer #" + computer.getId() + " <- '" + line + "'"));
    }
}

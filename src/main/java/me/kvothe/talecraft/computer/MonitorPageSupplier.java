package me.kvothe.talecraft.computer;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.ui.MonitorCanvasPage;

/**
 * Monitor peripheral: using the block opens its drawable canvas page. Programs
 * paint on it from Lua via tale.monitor(). The canvas exists independently of
 * any computer (a monitor with no program yet shows a blank screen).
 */
public final class MonitorPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    private final ComputerManager manager;

    public MonitorPageSupplier(ComputerManager manager) {
        this.manager = manager;
    }

    @Override
    public CustomUIPage tryCreate(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
            PlayerRef playerRef, InteractionContext ctx) {
        BlockPosition pos = ctx.getTargetBlock();
        World world = ComputerManager.worldOf(accessor);
        if (world == null) {
            world = ComputerManager.worldOf(playerRef);
        }
        if (pos == null || world == null) {
            return null;
        }
        MonitorCanvas canvas = manager.canvasAt(world, pos);
        MonitorCanvasPage page = new MonitorCanvasPage(playerRef, manager, canvas);
        manager.registerMonitorPage(playerRef.getUuid(), page);
        return page;
    }
}

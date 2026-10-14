package me.kvothe.talecraft.computer;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.ui.TerminalPage;

/**
 * Monitor peripheral: using it opens a read-only view of the terminal of an
 * adjacent computer (any of the 6 touching faces). This is the v0.1 of
 * "output redirected to a monitor"; direct peripheral APIs come later.
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
        if (pos == null || world == null) {
            return null;
        }
        Computer computer = manager.findAdjacentRunning(world, pos);
        if (computer == null) {
            playerRef.sendMessage(Message.raw("[TaleCraft] No running Computer adjacent to this monitor."));
            return null;
        }
        TerminalPage page = new TerminalPage(playerRef, manager, computer, true);
        manager.registerPage(playerRef.getUuid(), page);
        return page;
    }
}

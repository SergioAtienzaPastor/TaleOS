package me.kvothe.talecraft.computer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.ui.TerminalPage;

/**
 * Opens the terminal page when a player uses a Computer block. Registered as a
 * named supplier so the block's interaction asset can reference it with
 * {@code {"Type": "OpenCustomUI", "Page": {"Id": "TaleCraftComputer"}}}.
 */
public final class TerminalPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    private final ComputerManager manager;

    public TerminalPageSupplier(ComputerManager manager) {
        this.manager = manager;
    }

    @Override
    public CustomUIPage tryCreate(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
            PlayerRef playerRef, InteractionContext ctx) {
        BlockPosition pos = ctx.getTargetBlock();
        if (pos == null) {
            return null;
        }
        Computer computer = manager.bootAt(ComputerManager.worldOf(accessor), pos);
        TerminalPage page = new TerminalPage(playerRef, manager, computer);
        manager.registerPage(playerRef.getUuid(), page);
        return page;
    }
}

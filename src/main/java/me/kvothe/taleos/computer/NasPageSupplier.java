package me.kvothe.taleos.computer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.taleos.ui.NasPage;

public final class NasPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {
    private final ComputerManager manager;

    public NasPageSupplier(ComputerManager manager) {
        this.manager = manager;
    }

    @Override
    public CustomUIPage tryCreate(@Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull PlayerRef playerRef,
            @Nonnull InteractionContext context) {
        BlockPosition position = context.getTargetBlock();
        World world = ComputerManager.worldOf(accessor);
        if (position == null || world == null) {
            world = ComputerManager.worldOf(playerRef);
        }
        if (position == null || world == null || manager.nas().containerAt(world, position) == null) {
            return null;
        }
        manager.nas().registerUnit(world, position);
        return new NasPage(playerRef, manager, world, position);
    }
}

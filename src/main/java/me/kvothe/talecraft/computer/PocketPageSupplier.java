package me.kvothe.talecraft.computer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.pocket.PocketComputerItemMetadata;
import me.kvothe.talecraft.pocket.PocketHolder;
import me.kvothe.talecraft.ui.TerminalPage;

public final class PocketPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {
    private final ComputerManager manager;

    public PocketPageSupplier(ComputerManager manager) {
        this.manager = manager;
    }

    @Override
    public CustomUIPage tryCreate(@Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull PlayerRef playerRef,
            @Nonnull InteractionContext context) {
        ItemStack held = context.getHeldItem();
        ItemContainer container = context.getHeldItemContainer();
        short slot = context.getHeldItemSlot();
        World world = ComputerManager.worldOf(accessor);
        if (world == null) {
            world = ComputerManager.worldOf(playerRef);
        }
        if (!PocketComputerItemMetadata.isPocketComputer(held) || container == null || world == null) {
            return null;
        }
        try {
            ItemStack assigned = manager.pockets().assignIdentity(held);
            if (assigned != held && !container.replaceItemStackInSlot(slot, held, assigned).succeeded()) {
                return null;
            }
            PocketComputerItemMetadata.Identity identity = PocketComputerItemMetadata.identity(assigned);
            if (identity == null) {
                return null;
            }
            PocketHolder holder = PocketHolder.hytale(playerRef, container, slot, identity);
            Computer computer = manager.pockets().boot(holder);
            TerminalPage page = new TerminalPage(playerRef, manager, computer);
            manager.registerPage(playerRef.getUuid(), page);
            return page;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            playerRef.sendMessage(Message.raw("[TaleCraft] " + exception.getMessage()));
            return null;
        }
    }
}

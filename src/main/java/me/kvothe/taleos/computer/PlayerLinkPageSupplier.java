package me.kvothe.taleos.computer;

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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.taleos.peripheral.playerlink.PlayerLinkItemMetadata;
import me.kvothe.taleos.ui.PlayerLinkPage;

public final class PlayerLinkPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {
    @Override
    public CustomUIPage tryCreate(@Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull PlayerRef playerRef,
            @Nonnull InteractionContext context) {
        ItemStack held = context.getHeldItem();
        ItemContainer container = context.getHeldItemContainer();
        short slot = context.getHeldItemSlot();
        if (!PlayerLinkItemMetadata.isPlayerLink(held) || container == null) {
            return null;
        }
        try {
            PlayerLinkItemMetadata.Configuration configuration =
                    PlayerLinkItemMetadata.configuration(held);
            if (configuration.ownerId() == null) {
                ItemStack bound = PlayerLinkItemMetadata.bind(held, playerRef.getUuid(),
                        playerRef.getUsername());
                if (!container.replaceItemStackInSlot(slot, held, bound).succeeded()) {
                    playerRef.sendMessage(Message.raw(me.kvothe.taleos.I18n.t(
                            "Could not link the Player Link because its slot changed")));
                    return null;
                }
                context.setHeldItem(bound);
                playerRef.sendMessage(Message.raw(me.kvothe.taleos.I18n.t(
                        "Player Link linked to ") + playerRef.getUsername()));
            }
            return new PlayerLinkPage(playerRef, container, slot);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            playerRef.sendMessage(Message.raw("[TaleOS] " + exception.getMessage()));
            return null;
        }
    }
}

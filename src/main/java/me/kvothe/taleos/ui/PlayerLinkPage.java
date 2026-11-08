package me.kvothe.taleos.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.taleos.peripheral.playerlink.PlayerLinkItemMetadata;
import me.kvothe.taleos.peripheral.playerlink.PlayerLinkItemMetadata.Configuration;

public final class PlayerLinkPage extends InteractiveCustomUIPage<TerminalPage.TerminalEventData> {
    private final PlayerRef playerRef;
    private final ItemContainer container;
    private final short slot;

    public PlayerLinkPage(@Nonnull PlayerRef playerRef, ItemContainer container, short slot) {
        super(playerRef, CustomPageLifetime.CanDismiss, TerminalPage.TerminalEventData.CODEC);
        this.playerRef = playerRef;
        this.container = container;
        this.slot = slot;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("TaleOS/PlayerLink.ui");
        cmd.set("#Title.Text", me.kvothe.taleos.I18n.t("Player Link"));
        cmd.set("#Explanation.Text", me.kvothe.taleos.I18n.t(
                "An equipped Turtle can send items to the linked player's inventory."));
        cmd.set("#AccessHint.Text", me.kvothe.taleos.I18n.t(
                "This does not restrict who can use the Turtle."));
        cmd.set("#Unlink.Text", me.kvothe.taleos.I18n.t("Unlink"));
        cmd.set("#Close.Text", me.kvothe.taleos.I18n.t("Close"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#Unlink",
                new EventData().append("Action", "unlink"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#Close",
                new EventData().append("Action", "close"), false);
        refresh(cmd);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull TerminalPage.TerminalEventData data) {
        if ("close".equals(data.action)) {
            close();
            return;
        }
        if (!"unlink".equals(data.action)) {
            return;
        }
        ItemStack current = container.getItemStack(slot);
        Configuration configuration = PlayerLinkItemMetadata.configuration(current);
        if (configuration == null || configuration.ownerId() == null || !canUnlink(configuration)) {
            playerRef.sendMessage(Message.raw(me.kvothe.taleos.I18n.t(
                    "Only the linked player or an administrator can unlink this item")));
            return;
        }
        ItemStack unlinked = PlayerLinkItemMetadata.unbind(current);
        if (!container.replaceItemStackInSlot(slot, current, unlinked).succeeded()) {
            playerRef.sendMessage(Message.raw(me.kvothe.taleos.I18n.t(
                    "Could not unlink the Player Link because its slot changed")));
            return;
        }
        playerRef.sendMessage(Message.raw(me.kvothe.taleos.I18n.t("Player Link unlinked")));
        UICommandBuilder cmd = new UICommandBuilder();
        refresh(cmd);
        sendUpdate(cmd, false);
    }

    private void refresh(UICommandBuilder cmd) {
        Configuration configuration = PlayerLinkItemMetadata.configuration(container.getItemStack(slot));
        if (configuration == null) {
            cmd.set("#Status.Text", me.kvothe.taleos.I18n.t(
                    "The Player Link is no longer in this slot."));
            cmd.set("#Unlink.Visible", false);
        } else if (configuration.ownerId() == null) {
            cmd.set("#Status.Text", me.kvothe.taleos.I18n.t(
                    "Not linked. Hold the item and press F to link it to you."));
            cmd.set("#Unlink.Visible", false);
        } else {
            cmd.set("#Status.Text", me.kvothe.taleos.I18n.t("Linked to: ")
                    + configuration.ownerName());
            cmd.set("#Unlink.Visible", canUnlink(configuration));
        }
        cmd.set("#Usage.Text", me.kvothe.taleos.I18n.t(
                "Equip it in an external or internal Turtle upgrade slot."));
    }

    private boolean canUnlink(Configuration configuration) {
        return playerRef.getUuid().equals(configuration.ownerId())
                || playerRef.hasPermission("taleos.playerlink.admin");
    }
}

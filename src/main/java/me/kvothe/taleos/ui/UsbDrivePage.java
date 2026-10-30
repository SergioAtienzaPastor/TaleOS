package me.kvothe.taleos.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.taleos.computer.ComputerManager;
import me.kvothe.taleos.storage.PenDriveItemMetadata;
import me.kvothe.taleos.storage.PenDriveHub;

public final class UsbDrivePage extends InteractiveCustomUIPage<TerminalPage.TerminalEventData> {
    private final PlayerRef playerRef;
    private final ComputerManager manager;
    private final World world;
    private final BlockPosition position;
    private String draggedKind;
    private int draggedSlot = -1;
    private String hoveredKind;
    private int hoveredSlot = -1;

    public UsbDrivePage(@Nonnull PlayerRef playerRef, ComputerManager manager, World world,
            BlockPosition position) {
        super(playerRef, CustomPageLifetime.CanDismiss, TerminalPage.TerminalEventData.CODEC);
        this.playerRef = playerRef;
        this.manager = manager;
        this.world = world;
        this.position = position;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("TaleOS/USB_Drive.ui");
        cmd.set("#Title.Text", me.kvothe.taleos.I18n.t("USB Drive"));
        cmd.set("#PlayerInventoryLabel.Text", me.kvothe.taleos.I18n.t("YOUR INVENTORY"));
        bindGrid(evt, "#DriveGrid", "drive");
        bindGrid(evt, "#HotbarGrid", "hotbar");
        bindGrid(evt, "#BackpackGrid", "backpack");
        refresh(ref, store, cmd);
    }

    private static void bindGrid(UIEventBuilder evt, String selector, String kind) {
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, selector,
                new EventData().append("Action", "slots:" + kind), false);
        evt.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, selector,
                new EventData().append("Action", "hover:" + kind), false);
        evt.addEventBinding(CustomUIEventBindingType.SlotMouseDragCompleted, selector,
                new EventData().append("Action", "drag:" + kind), false);
        evt.addEventBinding(CustomUIEventBindingType.Dropped, selector,
                new EventData().append("Action", "drop:" + kind), false);
        evt.addEventBinding(CustomUIEventBindingType.SlotClickReleaseWhileDragging, selector,
                new EventData().append("Action", "drop:" + kind), false);
        evt.addEventBinding(CustomUIEventBindingType.DragCancelled, selector,
                new EventData().append("Action", "cancel:" + kind), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull TerminalPage.TerminalEventData data) {
        if (data.action == null) {
            return;
        }
        String[] parts = data.action.split(":");
        if (parts.length != 2) {
            return;
        }
        String operation = parts[0];
        String kind = parts[1];
        if (operation.equals("cancel")) {
            clearDrag();
        } else {
            int slot = data.toSlotIndex >= 0 ? data.toSlotIndex : data.slotIndex;
            if (operation.equals("drag")) {
                slot = data.fromSlotIndex >= 0 ? data.fromSlotIndex : data.slotIndex;
            }
            if (slot < 0) {
                return;
            }
            switch (operation) {
                case "hover" -> {
                    hoveredKind = kind;
                    hoveredSlot = slot;
                    return;
                }
                case "slots" -> {
                    if (draggedKind != null) {
                        dropDragged(store, kind, slot);
                    } else {
                        ItemContainer source = container(store, kind);
                        ItemStack stack = source == null || slot >= source.getCapacity() ? null
                                : source.getItemStack((short) slot);
                        if (stack != null && !stack.isEmpty()) {
                            draggedKind = kind;
                            draggedSlot = slot;
                        }
                    }
                }
                case "drag" -> {
                    draggedKind = kind;
                    draggedSlot = slot;
                    if (hoveredKind != null && (!hoveredKind.equals(kind) || hoveredSlot != slot)) {
                        dropDragged(store, hoveredKind, hoveredSlot);
                    } else if (data.toSlotIndex >= 0) {
                        dropDragged(store, kind, data.toSlotIndex);
                    } else {
                        return;
                    }
                }
                case "drop" -> dropDragged(store, kind, slot);
                default -> { }
            }
        }
        UICommandBuilder cmd = new UICommandBuilder();
        refresh(ref, store, cmd);
        sendUpdate(cmd, false);
    }

    private void dropDragged(Store<EntityStore> store, String targetKind, int targetSlot) {
        if (draggedKind == null) {
            return;
        }
        String sourceKind = draggedKind;
        int sourceSlot = draggedSlot;
        clearDrag();
        if (sourceKind.equals(targetKind) || (!sourceKind.equals("drive") && !targetKind.equals("drive"))) {
            return;
        }
        ItemContainer source = container(store, sourceKind);
        ItemContainer target = container(store, targetKind);
        if (source == null || target == null || sourceSlot < 0 || targetSlot < 0
                || sourceSlot >= source.getCapacity() || targetSlot >= target.getCapacity()) {
            return;
        }
        ItemStack stack = source.getItemStack((short) sourceSlot);
        ItemStack targetStack = target.getItemStack((short) targetSlot);
        if (stack == null || stack.isEmpty() || (targetStack != null && !targetStack.isEmpty())) {
            return;
        }
        try {
            if (targetKind.equals("drive")) {
                manager.usbDrives().assertCanInsert(world, position, stack);
            }
            if (!target.canAddItemStackToSlot((short) targetSlot, stack, false, false)
                    || !source.moveItemStackFromSlotToSlot((short) sourceSlot, 1, target,
                            (short) targetSlot).succeeded()) {
                return;
            }
            try {
                manager.usbDrives().sync(world, position);
            } catch (RuntimeException exception) {
                target.moveItemStackFromSlotToSlot((short) targetSlot, 1, source, (short) sourceSlot);
                try {
                    manager.usbDrives().sync(world, position);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        } catch (RuntimeException exception) {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#Status.Text", exception.getMessage());
            sendUpdate(cmd, false);
        }
    }

    private ItemContainer container(Store<EntityStore> store, String kind) {
        if (kind.equals("drive")) {
            return manager.usbDrives().containerAt(world, position);
        }
        Player player = store.getComponent(playerRef.getReference(), Player.getComponentType());
        if (player == null) {
            return null;
        }
        return kind.equals("hotbar") ? player.getInventory().getHotbar()
                : player.getInventory().getBackpack();
    }

    private void clearDrag() {
        draggedKind = null;
        draggedSlot = -1;
    }

    private void refresh(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder cmd) {
        ItemContainer drive = manager.usbDrives().containerAt(world, position);
        setGrid(cmd, "#DriveGrid", drive);
        Player player = store.getComponent(ref, Player.getComponentType());
        setGrid(cmd, "#HotbarGrid", player.getInventory().getHotbar());
        setGrid(cmd, "#BackpackGrid", player.getInventory().getBackpack());
        ItemStack stack = drive == null ? null : drive.getItemStack((short) 0);
        Integer id = PenDriveItemMetadata.id(stack);
        cmd.set("#Status.Text", id == null ? me.kvothe.taleos.I18n.t("Insert a PenDrive")
                : PenDriveItemMetadata.label(stack) + " · " + PenDriveHub.PENDRIVE_QUOTA_BYTES / 1000
                        + " kB · ID " + id);
        cmd.set("#DragHint.Text", me.kvothe.taleos.I18n.t(draggedKind == null
                ? "Drag a PenDrive into the reader or remove it to your inventory"
                : "Drop the PenDrive in the destination slot"));
    }

    private static void setGrid(UICommandBuilder cmd, String selector, ItemContainer container) {
        int count = container == null ? 1 : container.getCapacity();
        ItemGridSlot[] slots = new ItemGridSlot[count];
        for (int index = 0; index < count; index++) {
            ItemStack stack = container.getItemStack((short) index);
            slots[index] = stack == null || stack.isEmpty() ? new ItemGridSlot()
                    : new ItemGridSlot(UiItemStacks.forDisplay(stack));
            slots[index].setActivatable(true);
        }
        cmd.set(selector + ".Slots", slots);
    }
}

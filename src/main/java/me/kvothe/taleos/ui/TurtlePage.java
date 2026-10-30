package me.kvothe.taleos.ui;

import javax.annotation.Nonnull;

import org.luaj.vm2.LuaValue;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.taleos.computer.Computer;
import me.kvothe.taleos.computer.ComputerManager;
import me.kvothe.taleos.turtle.TurtleController;

public final class TurtlePage extends TerminalPage {

    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    private final PlayerRef playerRef;
    private final TurtleController turtle;
    private String inventorySignature = "";
    private int refreshTicks;
    private String draggedKind;
    private int draggedSlot = -1;
    private String hoveredKind;
    private int hoveredSlot = -1;

    public TurtlePage(@Nonnull PlayerRef playerRef, ComputerManager manager, Computer computer,
            TurtleController turtle) {
        super(playerRef, manager, computer);
        this.playerRef = playerRef;
        this.turtle = turtle;
    }

    @Override
    protected String layout() {
        return "TaleOS/Turtle.ui";
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        super.build(ref, cmd, evt, store);
        cmd.set("#Title.Text", "Turtle #" + getComputer().getId());
        cmd.set("#Stop.Text", me.kvothe.taleos.I18n.t("Stop program"));
        cmd.set("#LeftUpgradeLabel.Text", me.kvothe.taleos.I18n.t("Left upgrade"));
        cmd.set("#RightUpgradeLabel.Text", me.kvothe.taleos.I18n.t("Right upgrade"));
        cmd.set("#PlayerInventoryLabel.Text", me.kvothe.taleos.I18n.t("YOUR INVENTORY"));
        bindGrid(evt, "#TurtleGrid", "turtle");
        bindGrid(evt, "#LeftToolGrid", "leftTool");
        bindGrid(evt, "#RightToolGrid", "rightTool");
        bindGrid(evt, "#HotbarGrid", "hotbar");
        bindGrid(evt, "#BackpackGrid", "backpack");
        refreshInventory(ref, store, cmd);
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
            @Nonnull TerminalEventData data) {
        if (data.action == null || !(data.action.startsWith("slots:")
                || data.action.startsWith("drag:") || data.action.startsWith("drop:")
                || data.action.startsWith("hover:") || data.action.startsWith("cancel:"))) {
            super.handleDataEvent(ref, store, data);
            return;
        }
        String[] parts = data.action.split(":");
        String operation = parts[0];
        String kind = parts[1];
        if (operation.equals("cancel")) {
            clearDrag();
            updateDragHint();
            return;
        }
        int slot = data.toSlotIndex >= 0 ? data.toSlotIndex : data.slotIndex;
        if (operation.equals("drag")) {
            slot = data.fromSlotIndex >= 0 ? data.fromSlotIndex : data.slotIndex;
        }
        if (slot < 0) {
            LOGGER.atInfo().log("Turtle UI " + data.action + " without slot; slotIndex="
                    + data.slotIndex + ", from=" + data.fromSlotIndex + ", to=" + data.toSlotIndex
                    + ", itemStackId=" + data.itemStackId);
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
                    UICommandBuilder cmd = new UICommandBuilder();
                    refreshInventory(ref, store, cmd);
                    sendUpdate(cmd, false);
                } else if (kind.equals("turtle")) {
                    turtle.call("select", LuaValue.valueOf(slot + 1));
                    UICommandBuilder cmd = new UICommandBuilder();
                    cmd.set("#ToolHint.Text", me.kvothe.taleos.I18n.t("Active slot: ")
                            + (turtle.getSelectedSlot() + 1));
                    sendUpdate(cmd, false);
                } else if (isToolSlot(kind) && turtle.getInventory().getItemStack(
                        (short) containerSlot(kind, slot)) != null) {
                    draggedKind = kind;
                    draggedSlot = slot;
                    updateDragHint();
                }
                return;
            }
            case "drag" -> {
                draggedKind = kind;
                draggedSlot = slot;
                if (hoveredKind != null
                        && (!hoveredKind.equals(kind) || hoveredSlot != slot)) {
                    dropDragged(store, hoveredKind, hoveredSlot);
                } else if (data.toSlotIndex >= 0) {
                    dropDragged(store, kind, data.toSlotIndex);
                } else {
                    updateDragHint();
                    return;
                }
            }
            case "drop" -> dropDragged(store, kind, slot);
            default -> { }
        }
        UICommandBuilder cmd = new UICommandBuilder();
        refreshInventory(ref, store, cmd);
        sendUpdate(cmd, false);
    }

    private void updateDragHint() {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#DragHint.Text", me.kvothe.taleos.I18n.t(draggedKind == null
                ? "Drag items between grids to move them"
                : "Drop the item in the destination slot"));
        sendUpdate(cmd, false);
    }

    private void clearDrag() {
        draggedKind = null;
        draggedSlot = -1;
    }

    private void dropDragged(Store<EntityStore> store, String targetKind, int targetSlot) {
        if (draggedKind == null) {
            return;
        }
        String sourceKind = draggedKind;
        int sourceSlot = draggedSlot;
        clearDrag();
        ItemContainer source = container(store, sourceKind);
        ItemContainer target = container(store, targetKind);
        if (source == null || target == null || sourceSlot < 0 || targetSlot < 0) {
            return;
        }
        sourceSlot = containerSlot(sourceKind, sourceSlot);
        targetSlot = containerSlot(targetKind, targetSlot);
        if (sourceSlot >= source.getCapacity() || targetSlot >= target.getCapacity()) {
            return;
        }
        if (source == target && sourceSlot == targetSlot) {
            return;
        }
        ItemStack stack = source.getItemStack((short) sourceSlot);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack targetStack = target.getItemStack((short) targetSlot);
        boolean targetTool = isToolSlot(targetKind);
        boolean sourceTool = isToolSlot(sourceKind);
        if (targetTool || sourceTool || (targetStack != null && !targetStack.isEmpty()
                && !targetStack.getItemId().equals(stack.getItemId()))) {
            if (accepts(targetKind, stack) && accepts(sourceKind, targetStack)) {
                source.setItemStackForSlot((short) sourceSlot, targetStack);
                target.setItemStackForSlot((short) targetSlot, stack);
            } else {
                computerMessage("Ese objeto no es una mejora válida para este slot");
            }
        } else if (target.canAddItemStackToSlot((short) targetSlot, stack, false, false)) {
            source.moveItemStackFromSlotToSlot((short) sourceSlot, stack.getQuantity(),
                    target, (short) targetSlot);
        } else {
            computerMessage("Ese objeto no cabe en este slot");
        }
    }

    private static boolean isToolSlot(String kind) {
        return kind.equals("leftTool") || kind.equals("rightTool");
    }

    private static int containerSlot(String kind, int gridSlot) {
        if (kind.equals("leftTool")) {
            return TurtleController.LEFT_TOOL_SLOT;
        }
        if (kind.equals("rightTool")) {
            return TurtleController.RIGHT_TOOL_SLOT;
        }
        return gridSlot;
    }

    private static boolean accepts(String kind, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isToolSlot(kind)) {
            return true;
        }
        return me.kvothe.taleos.turtle.TurtleUpgradeRegistry.defaults().accepts(stack);
    }

    private ItemContainer container(Store<EntityStore> store, String kind) {
        if (kind.equals("turtle") || kind.equals("leftTool") || kind.equals("rightTool")) {
            return turtle.getInventory();
        }
        Player player = store.getComponent(playerRef.getReference(), Player.getComponentType());
        return kind.equals("hotbar") ? player.getInventory().getHotbar()
                : player.getInventory().getBackpack();
    }

    private void computerMessage(String message) {
        getComputer().systemMessage(message == null || message.isBlank() ? "Acción no válida" : message);
    }

    @Override
    public void refreshFromComputer() {
        super.refreshFromComputer();
        if (++refreshTicks % 5 != 0 || !ComputerManager.isCurrentPage(playerRef, this)) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        String signature = inventorySignature(ref.getStore());
        if (!signature.equals(inventorySignature)) {
            UICommandBuilder cmd = new UICommandBuilder();
            refreshInventory(ref, ref.getStore(), cmd);
            sendUpdate(cmd, false);
        }
    }

    private void refreshInventory(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder cmd) {
        inventorySignature = inventorySignature(store);
        ItemContainer inventory = turtle.getInventory();
        setGrid(cmd, "#TurtleGrid", inventory, 0, TurtleController.INVENTORY_SLOTS);
        setGrid(cmd, "#LeftToolGrid", inventory, TurtleController.LEFT_TOOL_SLOT, 1);
        setGrid(cmd, "#RightToolGrid", inventory, TurtleController.RIGHT_TOOL_SLOT, 1);
        Player player = store.getComponent(ref, Player.getComponentType());
        setGrid(cmd, "#HotbarGrid", player.getInventory().getHotbar(), 0,
                player.getInventory().getHotbar().getCapacity());
        setGrid(cmd, "#BackpackGrid", player.getInventory().getBackpack(), 0,
                player.getInventory().getBackpack().getCapacity());
        cmd.set("#Facing.Text", me.kvothe.taleos.I18n.t("Facing: ") + turtle.getFacing());
        cmd.set("#ToolHint.Text", me.kvothe.taleos.I18n.t("Active slot: ")
                + (turtle.getSelectedSlot() + 1) + me.kvothe.taleos.I18n.t(
                        " · drag tools and upgrades to equip or remove them"));
        cmd.set("#DragHint.Text", me.kvothe.taleos.I18n.t(draggedKind == null
                ? "Drag items between grids to move them"
                : "Drop the item in the destination slot"));
    }

    private static void setGrid(UICommandBuilder cmd, String selector,
            ItemContainer container, int firstSlot, int count) {
        ItemGridSlot[] slots = new ItemGridSlot[count];
        for (int index = 0; index < count; index++) {
            ItemStack stack = container == null ? null : container.getItemStack((short) (firstSlot + index));
            slots[index] = stack == null || stack.isEmpty() ? new ItemGridSlot()
                    : new ItemGridSlot(UiItemStacks.forDisplay(stack));
            slots[index].setActivatable(true);
        }
        cmd.set(selector + ".Slots", slots);
    }

    private String inventorySignature(Store<EntityStore> store) {
        StringBuilder signature = new StringBuilder();
        appendSignature(signature, turtle.getInventory());
        Player player = store.getComponent(playerRef.getReference(), Player.getComponentType());
        appendSignature(signature, player.getInventory().getHotbar());
        appendSignature(signature, player.getInventory().getBackpack());
        return signature.append(turtle.getSelectedSlot()).append(turtle.getFacing())
                .append(draggedKind).append(draggedSlot).toString();
    }

    private static void appendSignature(StringBuilder signature, ItemContainer container) {
        if (container == null) {
            signature.append("null;");
            return;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty()) {
                signature.append(slot).append(stack.getItemId()).append(stack.getQuantity())
                        .append(stack.getDurability()).append(';');
            }
        }
        signature.append('|');
    }
}

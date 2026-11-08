package me.kvothe.taleos.peripheral.playerlink;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

public final class PlayerLinkTransfer {
    public record Result(boolean success, int moved, String reason) {
        public static Result success(int moved) {
            return new Result(true, moved, null);
        }

        public static Result failure(String reason) {
            return new Result(false, 0, reason);
        }
    }

    private PlayerLinkTransfer() {
    }

    public static Result move(ItemContainer source, int sourceSlot,
            ItemContainer destination, int destinationSlot, int count) {
        if (source == null || destination == null || sourceSlot < 0 || destinationSlot < 0
                || sourceSlot >= source.getCapacity() || destinationSlot >= destination.getCapacity()
                || count <= 0) {
            return Result.failure("Invalid inventory slot or transfer count");
        }
        ItemStack before = source.getItemStack((short) sourceSlot);
        if (before == null || before.isEmpty()) {
            return Result.failure("The Turtle source slot is empty");
        }
        ItemStack destinationBefore = destination.getItemStack((short) destinationSlot);
        if (destinationBefore != null && !destinationBefore.isEmpty()
                && !ItemStack.isStackableWith(destinationBefore, before)) {
            return Result.failure("The destination slot contains another item");
        }
        if (!destination.canAddItemStackToSlot((short) destinationSlot, before, false, false)) {
            return Result.failure("The destination slot is full or cannot accept this item");
        }
        int previous = before.getQuantity();
        source.moveItemStackFromSlotToSlot((short) sourceSlot, Math.min(previous, count),
                destination, (short) destinationSlot);
        ItemStack after = source.getItemStack((short) sourceSlot);
        int remaining = after == null || after.isEmpty() ? 0 : after.getQuantity();
        int moved = previous - remaining;
        return moved > 0 ? Result.success(moved)
                : Result.failure("The destination slot is full or contains another item");
    }
}

package me.kvothe.taleos.peripheral.inventory;

public final class InventoryTransfers {
    private InventoryTransfers() {
    }

    public static int pull(InventoryAdapter source, InventoryAdapter destination, int limit) {
        if (limit <= 0) {
            return 0;
        }
        int moved = 0;
        for (int slot = 0; slot < source.size() && moved < limit; slot++) {
            ItemStackSnapshot stack = source.getStack(slot);
            if (stack == null || stack.count() <= 0) {
                continue;
            }
            moved += source.moveTo(slot, Math.min(limit - moved, stack.count()), destination);
        }
        return moved;
    }

    public static int push(InventoryAdapter source, int sourceSlot,
            InventoryAdapter destination, int limit) {
        if (limit <= 0 || sourceSlot < 0 || sourceSlot >= source.size()) {
            return 0;
        }
        ItemStackSnapshot stack = source.getStack(sourceSlot);
        if (stack == null || stack.count() <= 0) {
            return 0;
        }
        return source.moveTo(sourceSlot, Math.min(limit, stack.count()), destination);
    }
}

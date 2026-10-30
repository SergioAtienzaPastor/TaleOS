package me.kvothe.taleos.peripheral.inventory;

public interface InventoryAdapter {
    int size();

    ItemStackSnapshot getStack(int slot);

    int getItemLimit(int slot);

    default int moveTo(int sourceSlot, int amount, InventoryAdapter destination) {
        return 0;
    }
}

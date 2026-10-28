package me.kvothe.talecraft.peripheral.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

class InventoryPeripheralTest {
    private static final class FakeInventory implements InventoryAdapter {
        private final ItemStackSnapshot[] stacks;
        private String acceptedItem;

        private FakeInventory(int size) {
            stacks = new ItemStackSnapshot[size];
        }

        private FakeInventory put(int slot, String name, int count, int maxCount,
                Map<String, Object> metadata) {
            stacks[slot] = new ItemStackSnapshot(name, count, maxCount,
                    null, null, metadata, null);
            return this;
        }

        @Override
        public int size() {
            return stacks.length;
        }

        @Override
        public ItemStackSnapshot getStack(int slot) {
            return stacks[slot];
        }

        @Override
        public int getItemLimit(int slot) {
            return stacks[slot] == null ? 64 : stacks[slot].maxCount();
        }

        @Override
        public int moveTo(int sourceSlot, int amount, InventoryAdapter destination) {
            if (!(destination instanceof FakeInventory target)) {
                return 0;
            }
            ItemStackSnapshot source = stacks[sourceSlot];
            if (source == null || target.acceptedItem != null && !target.acceptedItem.equals(source.name())) {
                return 0;
            }
            int remaining = Math.min(amount, source.count());
            int moved = 0;
            for (int slot = 0; slot < target.stacks.length && remaining > 0; slot++) {
                ItemStackSnapshot existing = target.stacks[slot];
                if (existing != null && (!existing.name().equals(source.name())
                        || !Objects.equals(existing.metadata(), source.metadata()))) {
                    continue;
                }
                int capacity = existing == null ? source.maxCount()
                        : existing.maxCount() - existing.count();
                int added = Math.min(capacity, remaining);
                if (added <= 0) {
                    continue;
                }
                target.stacks[slot] = existing == null ? withCount(source, added)
                        : withCount(existing, existing.count() + added);
                remaining -= added;
                moved += added;
            }
            if (moved > 0) {
                stacks[sourceSlot] = source.count() == moved ? null
                        : withCount(source, source.count() - moved);
            }
            return moved;
        }

        private static ItemStackSnapshot withCount(ItemStackSnapshot stack, int count) {
            return new ItemStackSnapshot(stack.name(), count, stack.maxCount(), stack.durability(),
                    stack.maxDurability(), stack.metadata(), stack.nativeStack());
        }
    }

    @Test
    void exposesSafeListDetailsAndLimits() {
        FakeInventory inventory = new FakeInventory(2).put(0, "stone", 3, 64,
                Map.of("label", "north", "nested", Map.of("enabled", true)));
        InventoryPeripheral peripheral = new InventoryPeripheral(inventory);

        assertEquals(2, peripheral.call("size", LuaValue.NIL).arg1().toint());
        LuaTable list = peripheral.call("list", LuaValue.NIL).arg1().checktable();
        assertEquals("stone", list.get(1).get("name").tojstring());
        assertEquals(3, list.get(1).get("count").toint());
        assertTrue(list.get(1).get("metadata").isnil());
        LuaTable detail = peripheral.call("getItemDetail", LuaValue.valueOf(1)).arg1().checktable();
        assertEquals("north", detail.get("metadata").get("label").tojstring());
        assertTrue(detail.get("metadata").get("nested").get("enabled").toboolean());
        assertEquals(64, peripheral.call("getItemLimit", LuaValue.valueOf(2)).arg1().toint());
        assertTrue(peripheral.call("getItemDetail", LuaValue.valueOf(2)).arg1().isnil());
        assertFalse(detail.isuserdata());
    }

    @Test
    void pullMovesPartialQuantitiesAcrossSlotsAndPreservesMetadata() {
        Map<String, Object> metadata = Map.of("quality", 2);
        FakeInventory chest = new FakeInventory(2)
                .put(0, "stone", 10, 64, metadata)
                .put(1, "stone", 20, 64, metadata);
        FakeInventory turtle = new FakeInventory(2).put(0, "stone", 60, 64, metadata);

        assertEquals(12, InventoryTransfers.pull(chest, turtle, 12));
        assertNull(chest.getStack(0));
        assertEquals(18, chest.getStack(1).count());
        assertEquals(64, turtle.getStack(0).count());
        assertEquals(8, turtle.getStack(1).count());
        assertEquals(metadata, turtle.getStack(1).metadata());
    }

    @Test
    void transfersRespectFullAndFilteredDestinations() {
        FakeInventory chest = new FakeInventory(1).put(0, "stone", 10, 64, Map.of());
        FakeInventory full = new FakeInventory(1).put(0, "dirt", 64, 64, Map.of());
        assertEquals(0, InventoryTransfers.pull(chest, full, 5));
        assertEquals(10, chest.getStack(0).count());

        FakeInventory filtered = new FakeInventory(1);
        filtered.acceptedItem = "dirt";
        assertEquals(0, InventoryTransfers.pull(chest, filtered, 5));
        assertEquals(10, chest.getStack(0).count());
        assertTrue(Arrays.stream(filtered.stacks).allMatch(Objects::isNull));
    }

    @Test
    void pushMovesOnlyRequestedAmount() {
        FakeInventory turtle = new FakeInventory(2).put(1, "stone", 20, 64, Map.of("id", "x"));
        FakeInventory chest = new FakeInventory(1);

        assertEquals(5, InventoryTransfers.push(turtle, 1, chest, 5));
        assertEquals(15, turtle.getStack(1).count());
        assertEquals(5, chest.getStack(0).count());
        assertEquals("x", chest.getStack(0).metadata().get("id"));
    }
}

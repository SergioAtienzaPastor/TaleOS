package me.kvothe.talecraft.peripheral.inventory;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonNumber;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

public final class HytaleInventoryAdapter implements InventoryAdapter {
    private final ItemContainer container;

    public HytaleInventoryAdapter(ItemContainer container) {
        this.container = container;
    }

    public ItemContainer container() {
        return container;
    }

    @Override
    public int size() {
        return container.getCapacity();
    }

    @Override
    public ItemStackSnapshot getStack(int slot) {
        ItemStack stack = container.getItemStack((short) slot);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        Double durability = stack.getMaxDurability() > 0 ? stack.getDurability() : null;
        Double maxDurability = stack.getMaxDurability() > 0 ? stack.getMaxDurability() : null;
        return new ItemStackSnapshot(stack.getItemId(), stack.getQuantity(),
                item == null ? 64 : item.getMaxStack(), durability, maxDurability,
                metadata(stack.getMetadata()), stack);
    }

    @Override
    public int getItemLimit(int slot) {
        ItemStackSnapshot stack = getStack(slot);
        return stack == null ? 64 : stack.maxCount();
    }

    @Override
    public int moveTo(int sourceSlot, int amount, InventoryAdapter destination) {
        if (!(destination instanceof HytaleInventoryAdapter hytaleDestination) || amount <= 0) {
            return 0;
        }
        ItemStack before = container.getItemStack((short) sourceSlot);
        if (before == null || before.isEmpty()) {
            return 0;
        }
        int previous = before.getQuantity();
        container.moveItemStackFromSlot((short) sourceSlot,
                Math.min(amount, previous), hytaleDestination.container);
        ItemStack after = container.getItemStack((short) sourceSlot);
        int remaining = after == null || after.isEmpty() ? 0 : after.getQuantity();
        return Math.max(0, previous - remaining);
    }

    private static Map<String, Object> metadata(BsonDocument document) {
        if (document == null || document.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        document.forEach((key, value) -> {
            Object converted = bson(value);
            if (converted != null) {
                result.put(key, converted);
            }
        });
        return result;
    }

    private static Object bson(BsonValue value) {
        if (value instanceof BsonString string) {
            return string.getValue();
        }
        if (value instanceof BsonBoolean bool) {
            return bool.getValue();
        }
        if (value instanceof BsonNumber number) {
            return number.doubleValue();
        }
        if (value instanceof BsonDateTime date) {
            return date.getValue();
        }
        if (value instanceof BsonObjectId id) {
            return id.getValue().toHexString();
        }
        if (value instanceof BsonBinary binary) {
            return HexFormat.of().formatHex(binary.getData());
        }
        if (value instanceof BsonDocument document) {
            Map<String, Object> result = new LinkedHashMap<>();
            document.forEach((key, entry) -> {
                Object converted = bson(entry);
                if (converted != null) {
                    result.put(key, converted);
                }
            });
            return result;
        }
        if (value instanceof BsonArray array) {
            java.util.List<Object> result = new java.util.ArrayList<>();
            for (BsonValue entry : array) {
                Object converted = bson(entry);
                if (converted != null) {
                    result.add(converted);
                }
            }
            return result;
        }
        return null;
    }
}

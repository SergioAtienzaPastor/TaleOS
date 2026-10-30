package me.kvothe.taleos.pocket;

import java.util.UUID;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;

public final class PocketComputerItemMetadata {
    public static final String ITEM_ID = "TaleOS_PocketComputer";
    public static final String ID_KEY = "TaleOSPocketComputerId";
    public static final String UUID_KEY = "TaleOSPocketComputerUuid";

    private PocketComputerItemMetadata() {
    }

    public record Identity(int id, UUID uuid) {
        public Identity {
            if (id < 0) {
                throw new IllegalArgumentException("negative Pocket Computer id: " + id);
            }
            if (uuid == null) {
                throw new IllegalArgumentException("Pocket Computer UUID is required");
            }
        }
    }

    public static boolean isPocketComputer(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ITEM_ID.equals(stack.getItemId())
                && stack.getQuantity() == 1;
    }

    public static ItemStack unassigned() {
        return new ItemStack(ITEM_ID, 1);
    }

    public static ItemStack create(int id, UUID uuid) {
        return withIdentity(unassigned(), id, uuid);
    }

    public static ItemStack withIdentity(ItemStack stack, int id, UUID uuid) {
        Identity identity = new Identity(id, uuid);
        ItemDisplayMetadata display = new ItemDisplayMetadata(
                Message.raw("Pocket Computer #" + id), null);
        return stack.withMetadata(ID_KEY, Codec.INTEGER, id)
                .withMetadata(UUID_KEY, new BsonString(uuid.toString()))
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, display);
    }

    public static Integer id(ItemStack stack) {
        BsonValue value = value(stack, ID_KEY);
        if (value == null) {
            return null;
        }
        if (!value.isInt32()) {
            throw new IllegalArgumentException(ID_KEY + " must be an integer");
        }
        int id = value.asInt32().getValue();
        if (id < 0) {
            throw new IllegalArgumentException("negative Pocket Computer id: " + id);
        }
        return id;
    }

    public static UUID uuid(ItemStack stack) {
        BsonValue value = value(stack, UUID_KEY);
        if (value == null) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException(UUID_KEY + " must be a string");
        }
        try {
            return UUID.fromString(value.asString().getValue());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(UUID_KEY + " must be a UUID", exception);
        }
    }

    public static Identity identity(ItemStack stack) {
        if (!isPocketComputer(stack)) {
            return null;
        }
        Integer id = id(stack);
        UUID uuid = uuid(stack);
        return id == null || uuid == null ? null : new Identity(id, uuid);
    }

    public static boolean matches(ItemStack stack, Identity identity) {
        Identity actual = identity(stack);
        return actual != null && actual.equals(identity);
    }

    private static BsonValue value(ItemStack stack, String key) {
        BsonDocument metadata = stack == null ? null : stack.getMetadata();
        return metadata == null ? null : metadata.get(key);
    }
}

package me.kvothe.taleos.storage;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;

public final class PenDriveItemMetadata {
    public static final String ITEM_ID = "TaleOS_PenDrive";
    public static final String ID_KEY = "TaleOSPenDriveId";
    public static final String LABEL_KEY = "TaleOSPenDriveLabel";
    public static final int MAX_LABEL_LENGTH = 64;

    private PenDriveItemMetadata() {
    }

    public static boolean isPenDrive(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ITEM_ID.equals(stack.getItemId())
                && stack.getQuantity() == 1;
    }

    public static ItemStack create(int id, String label) {
        if (id < 0) {
            throw new IllegalArgumentException("negative PenDrive id: " + id);
        }
        String effective = normalizeLabel(label, id);
        ItemDisplayMetadata display = new ItemDisplayMetadata(Message.raw(effective), null);
        return new ItemStack(ITEM_ID, 1)
                .withMetadata(ID_KEY, Codec.INTEGER, id)
                .withMetadata(LABEL_KEY, new BsonString(effective))
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, display);
    }

    public static ItemStack unassigned() {
        return new ItemStack(ITEM_ID, 1);
    }

    public static Integer id(ItemStack stack) {
        if (!isPenDrive(stack)) {
            return null;
        }
        BsonDocument metadata = stack.getMetadata();
        BsonValue value = metadata == null ? null : metadata.get(ID_KEY);
        if (value == null) {
            return null;
        }
        if (!value.isInt32()) {
            throw new IllegalArgumentException(ID_KEY + " must be an integer");
        }
        int id = value.asInt32().getValue();
        if (id < 0) {
            throw new IllegalArgumentException("negative PenDrive id: " + id);
        }
        return id;
    }

    public static String label(ItemStack stack) {
        Integer id = id(stack);
        BsonDocument metadata = stack == null ? null : stack.getMetadata();
        BsonValue value = metadata == null ? null : metadata.get(LABEL_KEY);
        if (value == null) {
            return id == null ? "PenDrive" : defaultLabel(id);
        }
        if (!value.isString()) {
            throw new IllegalArgumentException(LABEL_KEY + " must be a string");
        }
        return normalizeLabel(value.asString().getValue(), id == null ? -1 : id);
    }

    public static ItemStack withId(ItemStack stack, int id) {
        if (!isPenDrive(stack)) {
            throw new IllegalArgumentException("not a PenDrive");
        }
        return create(id, label(stack));
    }

    public static ItemStack withLabel(ItemStack stack, String label) {
        Integer id = id(stack);
        if (id == null) {
            throw new IllegalArgumentException("PenDrive has no id");
        }
        return create(id, label);
    }

    public static String normalizeLabel(String label, int id) {
        String effective = label == null ? "" : label.trim();
        if (effective.isEmpty()) {
            return id >= 0 ? defaultLabel(id) : "PenDrive";
        }
        if (effective.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("PenDrive label is too long (max "
                    + MAX_LABEL_LENGTH + " characters)");
        }
        return effective;
    }

    public static String defaultLabel(int id) {
        return "PenDrive #" + id;
    }
}

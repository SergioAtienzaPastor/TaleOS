package me.kvothe.talecraft.computer;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;

public final class ComputerItemMetadata {

    public static final String COMPUTER_ITEM_ID = "TaleCraft_Computer";
    public static final String TURTLE_ITEM_ID = "TaleCraft_Turtle";
    public static final String COMPUTER_ID_KEY = "TaleCraftComputerId";
    public static final String BLOCK_HOLDER_KEY = ItemStack.Metadata.BLOCK_HOLDER;

    private ComputerItemMetadata() {
    }

    public static boolean isMachine(String itemId) {
        return COMPUTER_ITEM_ID.equals(itemId) || TURTLE_ITEM_ID.equals(itemId);
    }

    public static boolean isTurtle(String itemId) {
        return TURTLE_ITEM_ID.equals(itemId);
    }

    public static ItemStack createDrop(String itemId, int id, BsonDocument blockHolder) {
        if (!isMachine(itemId)) {
            throw new IllegalArgumentException("not a TaleCraft machine: " + itemId);
        }
        if (id < 0) {
            throw new IllegalArgumentException("negative computer id: " + id);
        }
        String label = (isTurtle(itemId) ? "Turtle #" : "Computer #") + id;
        ItemDisplayMetadata display = new ItemDisplayMetadata(Message.raw(label), null);
        ItemStack stack = new ItemStack(itemId, 1)
                .withMetadata(COMPUTER_ID_KEY, Codec.INTEGER, id)
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, display);
        return blockHolder == null ? stack : stack.withMetadata(BLOCK_HOLDER_KEY, blockHolder);
    }

    public static Integer computerId(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null) {
            return null;
        }
        BsonValue value = metadata.get(COMPUTER_ID_KEY);
        if (value == null) {
            return null;
        }
        if (!value.isInt32()) {
            throw new IllegalArgumentException(COMPUTER_ID_KEY + " must be an integer");
        }
        int id = value.asInt32().getValue();
        if (id < 0) {
            throw new IllegalArgumentException("negative computer id: " + id);
        }
        return id;
    }
}

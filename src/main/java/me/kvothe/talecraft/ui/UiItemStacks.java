package me.kvothe.talecraft.ui;

import org.bson.BsonDocument;

import com.hypixel.hytale.server.core.inventory.ItemStack;

public final class UiItemStacks {

    private UiItemStacks() {
    }

    public static ItemStack forDisplay(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        if (stack.getItemId().startsWith("TaleCraft_")) {
            return stack.withMetadata((BsonDocument) null);
        }
        if (stack.getMetadata() == null) {
            return stack;
        }
        BsonDocument metadata = stack.getMetadata().clone();
        metadata.remove(ItemStack.Metadata.BLOCK_HOLDER);
        return stack.withMetadata(metadata);
    }
}

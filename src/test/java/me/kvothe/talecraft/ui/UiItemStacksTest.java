package me.kvothe.talecraft.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.UUID;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import me.kvothe.talecraft.computer.ComputerItemMetadata;
import me.kvothe.talecraft.pocket.PocketComputerItemMetadata;
import me.kvothe.talecraft.storage.PenDriveItemMetadata;

class UiItemStacksTest {
    private static com.hypixel.hytale.assetstore.AssetStore<String,
            com.hypixel.hytale.server.core.asset.type.item.config.Item,
            com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,
                    com.hypixel.hytale.server.core.asset.type.item.config.Item>> previousStore;

    private static final class TestAssetStore extends com.hypixel.hytale.assetstore.AssetStore<String,
            com.hypixel.hytale.server.core.asset.type.item.config.Item,
            com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,
                    com.hypixel.hytale.server.core.asset.type.item.config.Item>> {
        private TestAssetStore(com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,
                com.hypixel.hytale.server.core.asset.type.item.config.Item> map) {
            super(new Builder(String.class,
                    com.hypixel.hytale.server.core.asset.type.item.config.Item.class, map) {
                @Override
                public com.hypixel.hytale.assetstore.AssetStore build() {
                    return null;
                }
            }.setCodec(com.hypixel.hytale.server.core.asset.type.item.config.Item.CODEC)
                    .setKeyFunction(item -> ((com.hypixel.hytale.server.core.asset.type.item.config.Item) item)
                            .getId()));
        }

        @Override
        protected com.hypixel.hytale.event.IEventBus getEventBus() {
            return null;
        }

        @Override
        public void addFileMonitor(String pack, java.nio.file.Path path) {
        }

        @Override
        public void removeFileMonitor(java.nio.file.Path path) {
        }

        @Override
        protected void handleRemoveOrUpdate(java.util.Set<String> removed,
                java.util.Map<String, com.hypixel.hytale.server.core.asset.type.item.config.Item> changed,
                com.hypixel.hytale.assetstore.AssetUpdateQuery query) {
        }
    }

    @org.junit.jupiter.api.BeforeAll
    static void installItemRegistry() throws Exception {
        var field = com.hypixel.hytale.server.core.asset.type.item.config.Item.class
                .getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousStore = (com.hypixel.hytale.assetstore.AssetStore) field.get(null);
        var map = new com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,
                com.hypixel.hytale.server.core.asset.type.item.config.Item>(java.util.Map.of(
                        PenDriveItemMetadata.ITEM_ID,
                        new com.hypixel.hytale.server.core.asset.type.item.config.Item(
                                PenDriveItemMetadata.ITEM_ID),
                        ComputerItemMetadata.TURTLE_ITEM_ID,
                        new com.hypixel.hytale.server.core.asset.type.item.config.Item(
                                ComputerItemMetadata.TURTLE_ITEM_ID)));
        field.set(null, new TestAssetStore(map));
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreItemRegistry() throws Exception {
        var field = com.hypixel.hytale.server.core.asset.type.item.config.Item.class
                .getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousStore);
    }

    @Test
    void penDriveDisplayKeepsOnlySafeNameMetadata() {
        ItemStack original = PenDriveItemMetadata.create(19, "Respaldo")
                .withMetadata(ComputerItemMetadata.COMPUTER_ID_KEY, new org.bson.BsonInt32(8))
                .withMetadata(ComputerItemMetadata.BLOCK_HOLDER_KEY,
                        new BsonDocument("Inventory", new BsonDocument()))
                .withMetadata(PocketComputerItemMetadata.UUID_KEY,
                        new org.bson.BsonString(UUID.randomUUID().toString()))
                .withMetadata("VanillaClientValue", new org.bson.BsonInt32(42));

        ItemStack display = UiItemStacks.forDisplay(original);

        assertNotSame(original, display);
        assertFalse(display.getMetadata().containsKey(PenDriveItemMetadata.ID_KEY));
        assertFalse(display.getMetadata().containsKey(PenDriveItemMetadata.LABEL_KEY));
        assertFalse(display.getMetadata().containsKey(ComputerItemMetadata.COMPUTER_ID_KEY));
        assertFalse(display.getMetadata().containsKey(ComputerItemMetadata.BLOCK_HOLDER_KEY));
        assertFalse(display.getMetadata().containsKey(PocketComputerItemMetadata.UUID_KEY));
        assertFalse(display.getMetadata().containsKey("VanillaClientValue"));
        assertEquals(19, PenDriveItemMetadata.id(original));
        assertEquals("Respaldo", PenDriveItemMetadata.label(original));
        assertEquals(8, original.getMetadata().getInt32(ComputerItemMetadata.COMPUTER_ID_KEY)
                .getValue());
        assertEquals(42, original.getMetadata().getInt32("VanillaClientValue").getValue());
    }

    @Test
    void displayCopyKeepsStackProperties() {
        ItemStack original = ComputerItemMetadata.createDrop(
                ComputerItemMetadata.TURTLE_ITEM_ID, 12, null)
                .withMaxDurability(40).withDurability(17);

        ItemStack display = UiItemStacks.forDisplay(original);

        assertEquals(original.getItemId(), display.getItemId());
        assertEquals(original.getQuantity(), display.getQuantity());
        assertEquals(17, display.getDurability());
        assertEquals(40, display.getMaxDurability());
        assertEquals(null, display.getMetadata());
        assertEquals(12, ComputerItemMetadata.computerId(original));
    }
}

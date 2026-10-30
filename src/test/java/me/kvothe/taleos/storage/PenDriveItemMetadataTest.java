package me.kvothe.taleos.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

class PenDriveItemMetadataTest {
    private static AssetStore<String, Item, DefaultAssetMap<String, Item>> previousStore;

    private static final class TestAssetStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private TestAssetStore(DefaultAssetMap<String, Item> map) {
            super(new AssetStore.Builder(String.class, Item.class, map) {
                @Override
                public AssetStore build() {
                    return null;
                }
            }.setCodec(Item.CODEC).setKeyFunction(item -> ((Item) item).getId()));
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
                java.util.Map<String, Item> changed,
                com.hypixel.hytale.assetstore.AssetUpdateQuery query) {
        }
    }

    @BeforeAll
    static void installItemRegistry() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousStore = (AssetStore<String, Item, DefaultAssetMap<String, Item>>) field.get(null);
        var map = new DefaultAssetMap<String, Item>(java.util.Map.of(
                PenDriveItemMetadata.ITEM_ID, new Item(PenDriveItemMetadata.ITEM_ID)));
        var store = new TestAssetStore(map);
        field.set(null, store);
    }

    @AfterAll
    static void restoreItemRegistry() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousStore);
    }

    @Test
    void idAndLabelLiveInBsonMetadata() {
        ItemStack stack = PenDriveItemMetadata.create(19, "Respaldo");

        assertTrue(PenDriveItemMetadata.isPenDrive(stack));
        assertEquals(19, PenDriveItemMetadata.id(stack));
        assertEquals("Respaldo", PenDriveItemMetadata.label(stack));
        assertEquals(19, stack.getMetadata().getInt32(PenDriveItemMetadata.ID_KEY).getValue());
        assertEquals("Respaldo",
                stack.getMetadata().getString(PenDriveItemMetadata.LABEL_KEY).getValue());
        assertEquals("Respaldo", stack.getDisplayName().getRawText());
    }

    @Test
    void unassignedAndRelabelledStacksKeepIdentityRules() {
        ItemStack blank = PenDriveItemMetadata.unassigned();
        assertNull(PenDriveItemMetadata.id(blank));
        ItemStack assigned = PenDriveItemMetadata.withId(blank, 4);
        ItemStack relabelled = PenDriveItemMetadata.withLabel(assigned, "  Azul  ");

        assertEquals(4, PenDriveItemMetadata.id(relabelled));
        assertEquals("Azul", PenDriveItemMetadata.label(relabelled));
        assertEquals("PenDrive #4", PenDriveItemMetadata.label(
                PenDriveItemMetadata.withLabel(relabelled, null)));
        assertThrows(IllegalArgumentException.class,
                () -> PenDriveItemMetadata.withLabel(relabelled, "x".repeat(65)));
    }

    @Test
    void idAndLabelSurviveItemStackBsonRoundTrip() {
        ItemStack original = PenDriveItemMetadata.create(23, "Copia persistente");

        org.bson.BsonValue encoded = ItemStack.CODEC.encode(original,
                com.hypixel.hytale.codec.EmptyExtraInfo.EMPTY);
        ItemStack decoded = ItemStack.CODEC.decode(encoded,
                com.hypixel.hytale.codec.EmptyExtraInfo.EMPTY);

        assertEquals(23, PenDriveItemMetadata.id(decoded));
        assertEquals("Copia persistente", PenDriveItemMetadata.label(decoded));
        assertEquals("Copia persistente", decoded.getDisplayName().getRawText());
    }

    @Test
    void nativeSlotTransactionsPreserveMetadataWhenInsertingAndRemoving() {
        var inventory = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer(
                (short) 1);
        var reader = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer(
                (short) 1);
        ItemStack blank = PenDriveItemMetadata.unassigned();
        assertTrue(inventory.setItemStackForSlot((short) 0, blank).succeeded());

        assertTrue(inventory.moveItemStackFromSlotToSlot((short) 0, 1, reader, (short) 0)
                .succeeded());
        ItemStack assigned = PenDriveItemMetadata.withId(blank, 31);
        assertTrue(reader.replaceItemStackInSlot((short) 0, blank, assigned).succeeded());
        ItemStack relabelled = PenDriveItemMetadata.withLabel(assigned, "Portátil");
        assertTrue(reader.replaceItemStackInSlot((short) 0, assigned, relabelled).succeeded());
        assertTrue(reader.moveItemStackFromSlotToSlot((short) 0, 1, inventory, (short) 0)
                .succeeded());

        ItemStack removed = inventory.getItemStack((short) 0);
        assertEquals(31, PenDriveItemMetadata.id(removed));
        assertEquals("Portátil", PenDriveItemMetadata.label(removed));
        assertTrue(reader.isEmpty());
    }
}

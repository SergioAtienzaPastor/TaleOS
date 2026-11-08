package me.kvothe.taleos.peripheral.playerlink;

import java.util.Map;

import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

final class PlayerLinkItemRegistry {
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

    private PlayerLinkItemRegistry() {
    }

    static void install() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousStore = (AssetStore<String, Item, DefaultAssetMap<String, Item>>) field.get(null);
        var map = new DefaultAssetMap<String, Item>(Map.of(
                PlayerLinkItemMetadata.ITEM_ID, item(PlayerLinkItemMetadata.ITEM_ID, 1),
                "TaleOS_Modem", item("TaleOS_Modem", 25),
                "TaleOS_Chunk_Loader_Upgrade", item("TaleOS_Chunk_Loader_Upgrade", 1)));
        field.set(null, new TestAssetStore(map));
    }

    private static Item item(String id, int maxStack) throws Exception {
        Item item = new Item(id);
        var field = Item.class.getDeclaredField("maxStack");
        field.setAccessible(true);
        field.setInt(item, maxStack);
        return item;
    }

    static void restore() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousStore);
    }
}

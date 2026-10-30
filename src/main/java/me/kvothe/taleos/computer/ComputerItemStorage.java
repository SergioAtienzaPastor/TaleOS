package me.kvothe.taleos.computer;

import org.bson.BsonDocument;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.codec.EmptyExtraInfo;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class ComputerItemStorage {

    private ComputerItemStorage() {
    }

    public static BsonDocument copyHolder(World world, BlockPosition position) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(
                position.x, position.y, position.z);
        if (section == null || !section.isValid()) {
            return null;
        }
        BlockComponentSection components = chunks.getStore().getComponent(section,
                BlockComponentSection.getComponentType());
        if (components == null) {
            return null;
        }
        Holder<ChunkStore> holder = components.copyBlockHolder(
                ChunkUtil.indexBlock(position.x, position.y, position.z));
        return holder == null ? null
                : ChunkStore.REGISTRY.getEntityCodec().encode(holder, EmptyExtraInfo.EMPTY);
    }

    public static boolean restoreHolder(World world, BlockPosition position, BlockType type,
            int rotation, ItemStack stack) {
        BsonDocument encoded = stack.getMetadata() == null ? null
                : stack.getMetadata().getDocument(ItemStack.Metadata.BLOCK_HOLDER, null);
        if (encoded == null) {
            return false;
        }
        Holder<ChunkStore> holder = ChunkStore.REGISTRY.deserialize(encoded);
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(
                position.x, position.y, position.z);
        BlockComponentSection components = chunks.getStore().getComponent(section,
                BlockComponentSection.getComponentType());
        BlockEntity.setBlockEntity(chunks.getStore(), section, components,
                position.x, position.y, position.z, type, rotation, holder);
        return true;
    }

}

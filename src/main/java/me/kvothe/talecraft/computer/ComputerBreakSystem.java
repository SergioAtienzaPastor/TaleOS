package me.kvothe.talecraft.computer;

import java.util.List;

import org.bson.BsonDocument;
import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.codec.EmptyExtraInfo;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ComputerBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    private final ComputerManager manager;

    public ComputerBreakSystem(ComputerManager manager) {
        super(BreakBlockEvent.class);
        this.manager = manager;
    }

    @Override
    public void handle(int index, com.hypixel.hytale.component.ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, BreakBlockEvent event) {
        BlockType blockType = event.getBlockType();
        if (event.isCancelled() || blockType == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i target = event.getTargetBlock();
        if ("TaleCraft_Monitor".equals(blockType.getId())) {
            manager.requestMonitorRebuild(world,
                    new BlockPosition(target.x, target.y, target.z));
            return;
        }
        if (me.kvothe.talecraft.storage.UsbDriveManager.BLOCK_ID.equals(blockType.getId())) {
            manager.usbDrives().driveBroken(world,
                    new BlockPosition(target.x, target.y, target.z));
            return;
        }
        if (me.kvothe.talecraft.storage.NasManager.BLOCK_ID.equals(blockType.getId())) {
            manager.nas().unitBroken(world,
                    new BlockPosition(target.x, target.y, target.z));
            return;
        }
        if (!ComputerItemMetadata.isMachine(blockType.getId())) {
            return;
        }
        try {
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> chunkAccessor = chunkStore.getStore();
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(
                    target.x, target.y, target.z);
            if (sectionRef == null || !sectionRef.isValid()) {
                event.setCancelled(true);
                return;
            }
            BsonDocument holderData = copyHolder(chunkAccessor, sectionRef, target);
            int id = manager.unbindAt(world,
                    new BlockPosition(target.x, target.y, target.z));
            ItemStack drop = ComputerItemMetadata.createDrop(blockType.getId(), id, holderData);
            event.setCancelled(true);
            BlockHarvestUtils.naturallyRemoveBlock(target, blockType, 0, 0,
                    null, null, 0, sectionRef, commandBuffer, chunkAccessor);
            Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(commandBuffer,
                    List.of(drop), new Vector3d(target.x + 0.5, target.y + 0.5, target.z + 0.5),
                    Rotation3f.IDENTITY);
            commandBuffer.addEntities(drops, AddReason.SPAWN);
        } catch (RuntimeException e) {
            event.setCancelled(true);
            LOGGER.atSevere().log("cannot break computer safely at " + world.getName() + ":"
                    + target.x + ":" + target.y + ":" + target.z + ": " + e);
        }
    }

    private static BsonDocument copyHolder(Store<ChunkStore> accessor, Ref<ChunkStore> sectionRef,
            Vector3i target) {
        BlockComponentSection components = accessor.getComponent(sectionRef,
                BlockComponentSection.getComponentType());
        if (components == null) {
            return null;
        }
        Holder<ChunkStore> holder = components.copyBlockHolder(
                ChunkUtil.indexBlock(target.x, target.y, target.z));
        return holder == null ? null
                : ChunkStore.REGISTRY.getEntityCodec().encode(holder, EmptyExtraInfo.EMPTY);
    }

    @Override
    public com.hypixel.hytale.component.query.Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}

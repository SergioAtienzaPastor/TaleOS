package me.kvothe.taleos.computer;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ComputerPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    private final ComputerManager manager;

    public ComputerPlaceSystem(ComputerManager manager) {
        super(PlaceBlockEvent.class);
        this.manager = manager;
    }

    @Override
    public void handle(int index, com.hypixel.hytale.component.ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, PlaceBlockEvent event) {
        ItemStack stack = event.getItemInHand();
        if (event.isCancelled() || stack == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        org.joml.Vector3i target = event.getTargetBlock();
        BlockPosition pos = new BlockPosition(target.x, target.y, target.z);
        if ("TaleOS_Monitor".equals(stack.getItemId())) {
            manager.requestMonitorRebuild(world, pos);
            return;
        }
        if (me.kvothe.taleos.storage.UsbDriveManager.BLOCK_ID.equals(stack.getItemId())) {
            manager.usbDrives().sync(world, pos);
            return;
        }
        if (me.kvothe.taleos.storage.NasManager.BLOCK_ID.equals(stack.getItemId())) {
            manager.nas().registerUnit(world, pos);
            return;
        }
        if (!ComputerItemMetadata.isMachine(stack.getItemId())) {
            return;
        }
        try {
            manager.bindAt(world, pos, ComputerItemMetadata.computerId(stack));
        } catch (RuntimeException e) {
            event.setCancelled(true);
            LOGGER.atWarning().log("rejected computer placement at " + world.getName() + ":"
                    + target.x + ":" + target.y + ":" + target.z + ": " + e.getMessage());
        }
    }

    @Override
    public com.hypixel.hytale.component.query.Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}

package me.kvothe.taleos.computer;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Drives each world its own computers once per tick and refreshes open
 * terminal pages. Registered on the EntityStore registry, so it runs once per
 * world store on <b>that</b> world's thread — computers must only tick (and
 * touch the world) from their own world thread.
 */
public class ComputerTickSystem extends TickingSystem<EntityStore> {

    private final ComputerManager manager;

    public ComputerTickSystem(ComputerManager manager) {
        this.manager = manager;
    }

    @Override
    public void tick(float deltaTime, int step, Store<EntityStore> store) {
        World world = null;
        if (store.getExternalData() instanceof EntityStore entityStore) {
            world = entityStore.getWorld();
        }
        if (world != null) {
            manager.tickWorld(world);
        }
    }
}

package me.kvothe.talecraft.computer;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Drives every live computer's LuaMachine once per server tick and refreshes
 * open terminal pages. Registered as a global ticking system.
 */
public class ComputerTickSystem extends TickingSystem<EntityStore> {

    private final ComputerManager manager;

    public ComputerTickSystem(ComputerManager manager) {
        this.manager = manager;
    }

    @Override
    public void tick(float deltaTime, int step, Store<EntityStore> store) {
        manager.tickAll();
    }
}

package me.kvothe.taleos.computer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.taleos.turtle.TurtleController;
import me.kvothe.taleos.ui.TurtlePage;

public final class TurtlePageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    private final ComputerManager manager;

    public TurtlePageSupplier(ComputerManager manager) {
        this.manager = manager;
    }

    @Override
    public CustomUIPage tryCreate(@Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull PlayerRef playerRef,
            @Nonnull InteractionContext context) {
        BlockPosition pos = context.getTargetBlock();
        World world = ComputerManager.worldOf(accessor);
        if (pos == null || world == null) {
            world = ComputerManager.worldOf(playerRef);
        }
        if (pos == null || world == null) {
            return null;
        }
        boolean freshBoot = !manager.isRunningAt(world, pos);
        Computer computer = manager.bootTurtleAt(world, pos);
        TurtleController controller = manager.turtleControllerAt(world, pos);
        SimpleItemContainer inventory = controller.getInventory();
        if (inventory == null) {
            computer.systemMessage("inventario no disponible; acércate a la Turtle");
            return null;
        }
        TurtlePage page = new TurtlePage(playerRef, manager, computer, controller);
        manager.registerPage(playerRef.getUuid(), page);
        if (freshBoot) {
            TerminalPageSupplier.playBootBeep(pos, accessor);
        }
        return page;
    }
}

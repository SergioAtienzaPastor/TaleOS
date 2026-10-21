package me.kvothe.talecraft.computer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.ui.TerminalPage;

/**
 * Opens the terminal page when a player uses a Computer block. Registered as a
 * named supplier so the block's interaction asset can reference it with
 * {@code {"Type": "OpenCustomUI", "Page": {"Id": "TaleCraftComputer"}}}.
 */
public final class TerminalPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    private final ComputerManager manager;

    public TerminalPageSupplier(ComputerManager manager) {
        this.manager = manager;
    }

    @Override
    public CustomUIPage tryCreate(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
            PlayerRef playerRef, InteractionContext ctx) {
        BlockPosition pos = ctx.getTargetBlock();
        if (pos == null) {
            return null;
        }
        World world = ComputerManager.worldOf(accessor);
        if (world == null) {
            world = ComputerManager.worldOf(playerRef);
        }
        boolean freshBoot = world != null && !manager.isRunningAt(world, pos);
        Computer computer = manager.bootAt(world, pos);
        if (freshBoot) {
            playBootBeep(pos, accessor);
        }
        TerminalPage page = new TerminalPage(playerRef, manager, computer);
        manager.registerPage(playerRef.getUuid(), page);
        return page;
    }

    /** Boot beep at the block. */
    private static void playBootBeep(BlockPosition pos, ComponentAccessor<EntityStore> accessor) {
        int index = soundIndex("TaleCraft_Boot_Beep");
        if (index < 0) {
            index = soundIndex("SFX/TaleCraft_Boot_Beep");
        }
        if (index < 0) {
            System.err.println("[TaleCraft] WARN: sound event TaleCraft_Boot_Beep not registered; skipping beep");
            return;
        }
        try {
            SoundUtil.playSoundEvent3d(index, SoundCategory.SFX, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                    accessor);
        } catch (Exception e) {
            System.err.println("[TaleCraft] WARN: could not play boot beep: " + e.getMessage());
        }
    }

    private static int soundIndex(String name) {
        try {
            return SoundEvent.getAssetStore().getAssetMap().getIndex(name);
        } catch (Exception e) {
            return -1;
        }
    }
}

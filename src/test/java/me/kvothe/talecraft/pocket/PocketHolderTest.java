package me.kvothe.talecraft.pocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;

class PocketHolderTest {
    @BeforeAll
    static void installItemRegistry() throws Exception {
        PocketItemRegistry.install();
    }

    @AfterAll
    static void restoreItemRegistry() throws Exception {
        PocketItemRegistry.restore();
    }

    private static final class FakePlayer implements PocketHolder.PlayerAccess {
        private final UUID id = UUID.randomUUID();
        private boolean online = true;
        private Object world = new Object();
        private BlockPosition position = new BlockPosition(3, 64, -8);

        @Override
        public UUID playerId() {
            return id;
        }

        @Override
        public boolean online() {
            return online;
        }

        @Override
        public Object world() {
            return world;
        }

        @Override
        public BlockPosition position() {
            return position;
        }
    }

    private static PocketHolder holder(FakePlayer player, SimpleItemContainer container, short slot,
            PocketComputerItemMetadata.Identity identity) {
        return new PocketHolder(player, PocketHolder.containerAccess(container), slot, identity);
    }

    @Test
    void holderRequiresExactStackSlotWorldAndOnlinePlayer() {
        FakePlayer player = new FakePlayer();
        Object world = player.world;
        SimpleItemContainer container = new SimpleItemContainer((short) 2);
        PocketComputerItemMetadata.Identity identity = new PocketComputerItemMetadata.Identity(4,
                UUID.randomUUID());
        container.setItemStackForSlot((short) 0, PocketComputerItemMetadata.create(4, identity.uuid()));
        PocketHolder holder = holder(player, container, (short) 0, identity);

        assertTrue(holder.validIn(world));
        assertFalse(holder.validIn(new Object()));

        container.moveItemStackFromSlotToSlot((short) 0, 1, container, (short) 1);
        assertFalse(holder.validIn(world), "moving the stack invalidates its original slot");
        assertFalse(holder(player, container, (short) 0, identity).validIn(world));
        assertTrue(holder(player, container, (short) 1, identity).validIn(world));

        container.removeItemStackFromSlot((short) 1);
        assertFalse(holder(player, container, (short) 1, identity).validIn(world), "drop/removal");
        container.setItemStackForSlot((short) 1, PocketComputerItemMetadata.create(4, identity.uuid()));
        player.online = false;
        assertFalse(holder(player, container, (short) 1, identity).validIn(world), "logout");
        player.online = true;
        player.world = new Object();
        assertFalse(holder(player, container, (short) 1, identity).validIn(world), "world change");
    }

    @Test
    void holderRejectsDuplicateMetadataAtItsSlot() {
        FakePlayer player = new FakePlayer();
        SimpleItemContainer container = new SimpleItemContainer((short) 1);
        PocketComputerItemMetadata.Identity identity = new PocketComputerItemMetadata.Identity(9,
                UUID.randomUUID());
        container.setItemStackForSlot((short) 0,
                PocketComputerItemMetadata.create(9, UUID.randomUUID()));
        assertFalse(holder(player, container, (short) 0, identity).validIn(player.world));
    }
}

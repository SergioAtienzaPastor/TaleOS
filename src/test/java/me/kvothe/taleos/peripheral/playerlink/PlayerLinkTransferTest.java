package me.kvothe.taleos.peripheral.playerlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;

class PlayerLinkTransferTest {
    private static final String ITEM = "TaleOS_Modem";

    @org.junit.jupiter.api.BeforeAll
    static void installItemRegistry() throws Exception {
        PlayerLinkItemRegistry.install();
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreItemRegistry() throws Exception {
        PlayerLinkItemRegistry.restore();
    }

    @Test
    void movesOnlyTheRequestedAmountWithoutChangingTheTotal() {
        SimpleItemContainer turtle = new SimpleItemContainer((short) 16);
        SimpleItemContainer player = new SimpleItemContainer((short) 9);
        turtle.setItemStackForSlot((short) 0, new ItemStack(ITEM, 10)
                .withMetadata("testMarker", new BsonString("preserved")));

        PlayerLinkTransfer.Result result = PlayerLinkTransfer.move(turtle, 0, player, 8, 4);

        assertTrue(result.success());
        assertEquals(4, result.moved());
        assertEquals(6, quantity(turtle, 0));
        assertEquals(4, quantity(player, 8));
        assertEquals(10, quantity(turtle, 0) + quantity(player, 8));
        assertEquals("preserved", player.getItemStack((short) 8).getMetadata()
                .getString("testMarker").getValue());
    }

    @Test
    void partialStackLeavesTheRemainderInTheTurtle() {
        SimpleItemContainer turtle = new SimpleItemContainer((short) 16);
        SimpleItemContainer player = new SimpleItemContainer((short) 9);
        turtle.setItemStackForSlot((short) 1, new ItemStack(ITEM, 5));
        player.setItemStackForSlot((short) 0, new ItemStack(ITEM, 23));

        PlayerLinkTransfer.Result result = PlayerLinkTransfer.move(turtle, 1, player, 0, 5);

        assertTrue(result.success());
        assertEquals(2, result.moved());
        assertEquals(3, quantity(turtle, 1));
        assertEquals(25, quantity(player, 0));
        assertEquals(28, quantity(turtle, 1) + quantity(player, 0));
    }

    @Test
    void occupiedOrFullDestinationDoesNotLoseOrDuplicateItems() {
        SimpleItemContainer turtle = new SimpleItemContainer((short) 16);
        SimpleItemContainer player = new SimpleItemContainer((short) 9);
        turtle.setItemStackForSlot((short) 0, new ItemStack(ITEM, 7));
        player.setItemStackForSlot((short) 0, new ItemStack("TaleOS_Chunk_Loader_Upgrade", 1));

        PlayerLinkTransfer.Result incompatible = PlayerLinkTransfer.move(turtle, 0, player, 0, 7);
        assertFalse(incompatible.success());
        assertEquals(7, quantity(turtle, 0));
        assertEquals("TaleOS_Chunk_Loader_Upgrade", player.getItemStack((short) 0).getItemId());

        player.setItemStackForSlot((short) 0, new ItemStack(ITEM, 25));
        PlayerLinkTransfer.Result full = PlayerLinkTransfer.move(turtle, 0, player, 0, 7);
        assertFalse(full.success());
        assertEquals(7, quantity(turtle, 0));
        assertEquals(25, quantity(player, 0));
    }

    @Test
    void emptySourceAndInvalidCountsAreSafeNoOps() {
        SimpleItemContainer turtle = new SimpleItemContainer((short) 16);
        SimpleItemContainer player = new SimpleItemContainer((short) 9);
        assertFalse(PlayerLinkTransfer.move(turtle, 0, player, 0, 1).success());
        assertFalse(PlayerLinkTransfer.move(turtle, -1, player, 0, 1).success());
        assertFalse(PlayerLinkTransfer.move(turtle, 0, player, 0, 0).success());
        assertEquals(0, quantity(player, 0));
    }

    private static int quantity(SimpleItemContainer container, int slot) {
        ItemStack stack = container.getItemStack((short) slot);
        return stack == null || stack.isEmpty() ? 0 : stack.getQuantity();
    }
}

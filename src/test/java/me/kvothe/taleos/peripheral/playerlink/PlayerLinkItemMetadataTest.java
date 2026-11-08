package me.kvothe.taleos.peripheral.playerlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import me.kvothe.taleos.peripheral.playerlink.PlayerLinkItemMetadata.Configuration;

class PlayerLinkItemMetadataTest {
    @org.junit.jupiter.api.BeforeAll
    static void installItemRegistry() throws Exception {
        PlayerLinkItemRegistry.install();
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreItemRegistry() throws Exception {
        PlayerLinkItemRegistry.restore();
    }

    @Test
    void bindingAndSettingsSurviveItemMetadataCopies() {
        UUID owner = UUID.randomUUID();
        ItemStack bound = PlayerLinkItemMetadata.bind(
                new ItemStack(PlayerLinkItemMetadata.ITEM_ID, 1), owner, "PlayerOne");
        Configuration configured = PlayerLinkItemMetadata.configuration(bound)
                .withRoute(1, 9).withRoute(4, 12).withDefaultSlot(10).withAutoTransfer(true);
        ItemStack saved = PlayerLinkItemMetadata.configure(bound, configured);
        ItemStack restored = new ItemStack(PlayerLinkItemMetadata.ITEM_ID, 1,
                saved.getMetadata().clone());

        Configuration actual = PlayerLinkItemMetadata.configuration(restored);
        assertEquals(owner, actual.ownerId());
        assertEquals("PlayerOne", actual.ownerName());
        assertEquals(Map.of(1, 9, 4, 12), actual.routes());
        assertEquals(10, actual.defaultSlot());
        assertTrue(actual.autoTransfer());
    }

    @Test
    void unlinkingClearsIdentityRoutesAndAutomaticTransfer() {
        ItemStack bound = PlayerLinkItemMetadata.bind(
                new ItemStack(PlayerLinkItemMetadata.ITEM_ID, 1), UUID.randomUUID(), "PlayerOne");
        ItemStack configured = PlayerLinkItemMetadata.configure(bound,
                PlayerLinkItemMetadata.configuration(bound).withRoute(2, 9)
                        .withDefaultSlot(8).withAutoTransfer(true));

        Configuration cleared = PlayerLinkItemMetadata.configuration(
                PlayerLinkItemMetadata.unbind(configured));
        assertNull(cleared.ownerId());
        assertNull(cleared.ownerName());
        assertTrue(cleared.routes().isEmpty());
        assertNull(cleared.defaultSlot());
        assertFalse(cleared.autoTransfer());
    }

    @Test
    void linkedItemCannotBeBoundAgain() {
        ItemStack bound = PlayerLinkItemMetadata.bind(
                new ItemStack(PlayerLinkItemMetadata.ITEM_ID, 1), UUID.randomUUID(), "PlayerOne");
        assertThrows(IllegalArgumentException.class, () -> PlayerLinkItemMetadata.bind(
                bound, UUID.randomUUID(), "AnotherPlayer"));
    }

    @Test
    void invalidRoutesAndUnlinkedSettingsAreRejected() {
        UUID owner = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new Configuration(owner, "PlayerOne", Map.of(0, 9), null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Configuration(owner, "PlayerOne", Map.of(1, 65), null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Configuration(null, null, Map.of(), 9, false));
    }

    @Test
    void malformedOwnerMetadataFailsClosed() {
        BsonDocument config = new BsonDocument("owner", new BsonString("not-a-uuid"));
        ItemStack corrupted = new ItemStack(PlayerLinkItemMetadata.ITEM_ID, 1)
                .withMetadata("TaleOSPlayerLink", config);
        assertThrows(IllegalArgumentException.class,
                () -> PlayerLinkItemMetadata.configuration(corrupted));
    }
}

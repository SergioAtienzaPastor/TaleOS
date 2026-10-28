package me.kvothe.talecraft.pocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.codec.EmptyExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;

class PocketComputerItemMetadataTest {
    @BeforeAll
    static void installItemRegistry() throws Exception {
        PocketItemRegistry.install();
    }

    @AfterAll
    static void restoreItemRegistry() throws Exception {
        PocketItemRegistry.restore();
    }

    @Test
    void identitySurvivesNativeBsonRoundTrip() {
        UUID uuid = UUID.randomUUID();
        ItemStack original = PocketComputerItemMetadata.create(27, uuid);

        ItemStack decoded = ItemStack.CODEC.decode(ItemStack.CODEC.encode(original,
                EmptyExtraInfo.EMPTY), EmptyExtraInfo.EMPTY);

        assertEquals(new PocketComputerItemMetadata.Identity(27, uuid),
                PocketComputerItemMetadata.identity(decoded));
        assertEquals("Pocket Computer #27", decoded.getDisplayName().getRawText());
    }

    @Test
    void blankStackHasNoIdentityUntilAssigned() {
        ItemStack blank = PocketComputerItemMetadata.unassigned();
        assertTrue(PocketComputerItemMetadata.isPocketComputer(blank));
        assertNull(PocketComputerItemMetadata.identity(blank));
        assertNull(PocketComputerItemMetadata.id(blank));
        assertNull(PocketComputerItemMetadata.uuid(blank));
    }

    @Test
    void matchingChecksBothDiskAndInstanceIdentity() {
        UUID uuid = UUID.randomUUID();
        ItemStack stack = PocketComputerItemMetadata.create(8, uuid);
        assertTrue(PocketComputerItemMetadata.matches(stack,
                new PocketComputerItemMetadata.Identity(8, uuid)));
        assertThrows(IllegalArgumentException.class, () -> PocketComputerItemMetadata.matches(stack,
                new PocketComputerItemMetadata.Identity(8, null)));
        assertNotEquals(PocketComputerItemMetadata.identity(stack),
                new PocketComputerItemMetadata.Identity(8, UUID.randomUUID()));
    }

    @Test
    void malformedIdentityIsRejected() {
        ItemStack badId = PocketComputerItemMetadata.unassigned()
                .withMetadata(PocketComputerItemMetadata.ID_KEY, new org.bson.BsonString("seven"));
        ItemStack badUuid = PocketComputerItemMetadata.unassigned()
                .withMetadata(PocketComputerItemMetadata.UUID_KEY, new org.bson.BsonString("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> PocketComputerItemMetadata.id(badId));
        assertThrows(IllegalArgumentException.class, () -> PocketComputerItemMetadata.uuid(badUuid));
    }
}

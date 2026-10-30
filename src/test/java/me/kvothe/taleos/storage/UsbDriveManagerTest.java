package me.kvothe.taleos.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UsbDriveManagerTest {
    @Test
    void insertionEventsUseTheSideSeenFromTheComputer() {
        assertEquals("right", UsbDriveManager.oppositeSide("left"));
        assertEquals("left", UsbDriveManager.oppositeSide("right"));
        assertEquals("bottom", UsbDriveManager.oppositeSide("top"));
        assertEquals("top", UsbDriveManager.oppositeSide("bottom"));
        assertEquals("back", UsbDriveManager.oppositeSide("front"));
        assertEquals("front", UsbDriveManager.oppositeSide("back"));
    }

    @Test
    void exposesRequiredAssetIds() {
        assertEquals("TaleOS_PenDrive", PenDriveItemMetadata.ITEM_ID);
        assertEquals("TaleOS_USB_Drive", UsbDriveManager.BLOCK_ID);
    }
}

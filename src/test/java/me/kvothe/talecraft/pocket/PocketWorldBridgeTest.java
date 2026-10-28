package me.kvothe.talecraft.pocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hypixel.hytale.protocol.BlockPosition;

import me.kvothe.talecraft.computer.ComputerManager;
import me.kvothe.talecraft.computer.HytaleWorldBridge;

class PocketWorldBridgeTest {
    @TempDir
    Path dir;

    @Test
    void mobileWorldBridgeReadsItsOriginFromTheHolderEveryTime() {
        AtomicReference<BlockPosition> position = new AtomicReference<>(new BlockPosition(2, 65, -4));
        HytaleWorldBridge bridge = new HytaleWorldBridge(null, position::get, 12,
                new ComputerManager(dir), () -> null);

        assertEquals(2, bridge.getOriginX());
        assertEquals(65, bridge.getOriginY());
        assertEquals(-4, bridge.getOriginZ());

        position.set(new BlockPosition(-19, 70, 31));
        assertEquals(-19, bridge.getOriginX());
        assertEquals(70, bridge.getOriginY());
        assertEquals(31, bridge.getOriginZ());
    }
}

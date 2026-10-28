package me.kvothe.talecraft.pocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;

import me.kvothe.talecraft.computer.Computer;
import me.kvothe.talecraft.computer.ComputerIdStore;
import me.kvothe.talecraft.computer.ComputerManager;
import me.kvothe.talecraft.lua.WorldBridge;

class PocketManagerTest {
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
        private BlockPosition position = new BlockPosition(0, 64, 0);

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

    private record Fixture(FakePlayer player, SimpleItemContainer container,
            PocketComputerItemMetadata.Identity identity) {
    }

    @TempDir
    Path dir;

    private ComputerManager computers;
    private ComputerIdStore ids;
    private PocketManager pockets;
    private int created;

    @BeforeEach
    void setUp() {
        computers = new ComputerManager(dir);
        ids = new ComputerIdStore(dir.resolve("ids.json"));
        pockets = new PocketManager(computers, dir, ids, (holder, id, root) -> {
            created++;
            return new Computer(id, root, WorldBridge.NONE);
        });
    }

    @AfterEach
    void tearDown() {
        pockets.shutdown();
    }

    private Fixture fixture(int id, UUID uuid) {
        FakePlayer player = new FakePlayer();
        SimpleItemContainer container = new SimpleItemContainer((short) 2);
        container.setItemStackForSlot((short) 0, PocketComputerItemMetadata.create(id, uuid));
        return new Fixture(player, container, new PocketComputerItemMetadata.Identity(id, uuid));
    }

    private static PocketHolder holder(Fixture fixture, short slot) {
        return new PocketHolder(fixture.player, PocketHolder.containerAccess(fixture.container), slot,
                fixture.identity);
    }

    @Test
    void globalIdsAreUniqueAcrossItemsAndReload() throws Exception {
        ItemStack first = pockets.assignIdentity(PocketComputerItemMetadata.unassigned());
        ItemStack second = pockets.assignIdentity(PocketComputerItemMetadata.unassigned());
        int firstId = PocketComputerItemMetadata.id(first);
        int secondId = PocketComputerItemMetadata.id(second);
        assertNotEquals(firstId, secondId);
        assertNotEquals(PocketComputerItemMetadata.uuid(first), PocketComputerItemMetadata.uuid(second));

        ComputerIdStore loaded = new ComputerIdStore(dir.resolve("ids.json"));
        loaded.load();
        PocketManager reloaded = new PocketManager(computers, dir, loaded,
                (holder, id, root) -> new Computer(id, root, WorldBridge.NONE));
        ItemStack third = reloaded.assignIdentity(PocketComputerItemMetadata.unassigned());
        assertTrue(PocketComputerItemMetadata.id(third) > Math.max(firstId, secondId));
        assertSame(first, reloaded.assignIdentity(first), "an existing identity is not reassigned");
    }

    @Test
    void openingTwiceAtSameLocationReturnsOneMachine() {
        Fixture fixture = fixture(1, UUID.randomUUID());
        Computer first = pockets.boot(holder(fixture, (short) 0));
        Computer second = pockets.boot(holder(fixture, (short) 0));

        assertSame(first, second);
        assertEquals(1, created);
        assertEquals(1, pockets.activeCount());
    }

    @Test
    void duplicateStacksCannotShareDiskOrInstance() {
        Fixture original = fixture(7, UUID.randomUUID());
        pockets.boot(holder(original, (short) 0));
        Fixture sameIdentity = fixture(7, original.identity.uuid());
        Fixture sameDisk = fixture(7, UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> pockets.boot(holder(sameIdentity, (short) 0)));
        assertThrows(IllegalStateException.class, () -> pockets.boot(holder(sameDisk, (short) 0)));
        assertEquals(1, created);
        assertEquals(1, pockets.activeCount());
    }

    @Test
    void changingSlotSuspendsBeforeReopeningWithoutDuplicate() {
        Fixture fixture = fixture(2, UUID.randomUUID());
        Computer original = pockets.boot(holder(fixture, (short) 0));
        fixture.container.moveItemStackFromSlotToSlot((short) 0, 1, fixture.container, (short) 1);

        pockets.tickWorld(fixture.player.world);
        assertFalse(original.isAlive());
        assertEquals(0, pockets.activeCount());

        Computer reopened = pockets.boot(holder(fixture, (short) 1));
        assertNotSame(original, reopened);
        assertEquals(2, created);
        assertEquals(1, pockets.activeCount());
        pockets.tickWorld(fixture.player.world);
        assertEquals(1, pockets.activeCount());
    }

    @Test
    void dropLogoutAndWorldChangeSuspendTheirSessions() {
        Fixture dropped = fixture(3, UUID.randomUUID());
        Computer droppedComputer = pockets.boot(holder(dropped, (short) 0));
        dropped.container.removeItemStackFromSlot((short) 0);
        pockets.tickWorld(dropped.player.world);
        assertFalse(droppedComputer.isAlive());

        Fixture loggedOut = fixture(4, UUID.randomUUID());
        Computer loggedOutComputer = pockets.boot(holder(loggedOut, (short) 0));
        loggedOut.player.online = false;
        pockets.tickWorld(loggedOut.player.world);
        assertFalse(loggedOutComputer.isAlive());

        Fixture transferred = fixture(5, UUID.randomUUID());
        Object oldWorld = transferred.player.world;
        Computer transferredComputer = pockets.boot(holder(transferred, (short) 0));
        transferred.player.world = new Object();
        pockets.tickWorld(oldWorld);
        assertFalse(transferredComputer.isAlive());
        assertEquals(0, pockets.activeCount());
    }

    @Test
    void disconnectClosesImmediately() {
        Fixture fixture = fixture(6, UUID.randomUUID());
        Computer computer = pockets.boot(holder(fixture, (short) 0));
        pockets.disconnect(fixture.player.id);
        assertFalse(computer.isAlive());
        assertEquals(0, pockets.activeCount());
    }

    @Test
    void closingPocketReleasesItsNasMounts() throws Exception {
        Fixture fixture = fixture(12, UUID.randomUUID());
        Computer computer = pockets.boot(holder(fixture, (short) 0));
        var hub = computers.usbDrives().hub();
        hub.insert("world:1:64:1#0", 22, "Compartido");
        String pocketKey = me.kvothe.talecraft.storage.NasManager.pocketKey(12);
        hub.attachComputer("world:1:64:1#0", pocketKey,
                new me.kvothe.talecraft.storage.PenDriveHub.ComputerAccess() {
                    @Override
                    public me.kvothe.talecraft.os.VirtualFileSystem fileSystem() {
                        return computer.getFileSystem();
                    }

                    @Override
                    public boolean alive() {
                        return computer.isAlive();
                    }

                    @Override
                    public void usbEvent(String event, String side) {
                    }
                }, "nas", "nas/22");
        computer.getFileSystem().write("nas/22/LEEME_NAS.txt", "compartido");

        pockets.disconnect(fixture.player.id);

        assertFalse(computer.getFileSystem().isMounted("nas/22"));
        assertThrows(me.kvothe.talecraft.os.VirtualFileSystem.FsException.class,
                () -> computer.getFileSystem().read("nas/22/LEEME_NAS.txt"));
    }

    @Test
    void pocketCdAcceptsMountedNasDriveId() throws Exception {
        Fixture fixture = fixture(12, UUID.randomUUID());
        Computer computer = pockets.boot(holder(fixture, (short) 0));
        var hub = computers.usbDrives().hub();
        hub.insert("world:1:64:1#0", 22, "Compartido");
        String pocketKey = me.kvothe.talecraft.storage.NasManager.pocketKey(12);
        hub.attachComputer("world:1:64:1#0", pocketKey,
                new me.kvothe.talecraft.storage.PenDriveHub.ComputerAccess() {
                    @Override
                    public me.kvothe.talecraft.os.VirtualFileSystem fileSystem() {
                        return computer.getFileSystem();
                    }

                    @Override
                    public boolean alive() {
                        return computer.isAlive();
                    }

                    @Override
                    public void usbEvent(String event, String side) {
                    }
                }, "nas", "nas/22");
        computer.getFileSystem().write("nas/22/prueba.txt", "contenido");
        computer.drainOutput();

        computer.queueStdin("cd 22");
        computer.tick();
        assertEquals("tale:/nas/22> ", computer.getPrompt());
        computer.queueStdin("ls");
        computer.tick();
        assertTrue(computer.drainOutput().contains("prueba.txt"));
    }

    @Test
    void ticksOnlyInTheHoldersWorld() {
        Fixture fixture = fixture(10, UUID.randomUUID());
        Computer computer = pockets.boot(holder(fixture, (short) 0));
        computer.drainOutput();
        computer.queueStdin("id");

        pockets.tickWorld(new Object());
        assertFalse(computer.drainOutput().contains("Computer #10"));
        pockets.tickWorld(fixture.player.world);
        assertTrue(computer.drainOutput().contains("Computer #10"));
    }

    @Test
    void filesPersistAcrossSuspensionAndUseTheGlobalComputerDirectory() throws Exception {
        Fixture fixture = fixture(11, UUID.randomUUID());
        Computer first = pockets.boot(holder(fixture, (short) 0));
        first.getFileSystem().write("notes.txt", "persistente");
        first.drainOutput();
        fixture.container.removeItemStackFromSlot((short) 0);
        pockets.tickWorld(fixture.player.world);

        fixture.container.setItemStackForSlot((short) 0,
                PocketComputerItemMetadata.create(11, fixture.identity.uuid()));
        Computer second = pockets.boot(holder(fixture, (short) 0));

        assertNotSame(first, second);
        assertEquals("persistente", second.getFileSystem().read("notes.txt"));
        assertTrue(dir.resolve("computers/11/notes.txt").toFile().isFile());
    }
}

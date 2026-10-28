package me.kvothe.talecraft.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.talecraft.computer.ComputerIdStore;
import me.kvothe.talecraft.os.VirtualFileSystem;

class PenDriveHubTest {
    @TempDir
    Path dir;

    private static final class TestComputer implements PenDriveHub.ComputerAccess {
        private final VirtualFileSystem fs;
        private final List<String> events = new ArrayList<>();
        private boolean alive = true;

        private TestComputer(Path root) throws Exception {
            fs = new VirtualFileSystem(root, 1_000_000);
        }

        @Override
        public VirtualFileSystem fileSystem() {
            return fs;
        }

        @Override
        public boolean alive() {
            return alive;
        }

        @Override
        public void usbEvent(String event, String side) {
            events.add(event + ":" + side);
        }
    }

    @Test
    void insertionMountsRealPersistentStorageAndEjectionFailsClosed() throws Exception {
        ComputerIdStore ids = new ComputerIdStore(dir.resolve("ids.json"));
        PenDriveHub hub = new PenDriveHub(dir.resolve("pendrives"), ids::allocateGlobalId);
        TestComputer computer = new TestComputer(dir.resolve("computer"));
        PenDriveHub.Drive drive = hub.insert("world:1:64:1", null, "Datos");
        hub.attachComputer("world:1:64:1", "world:0:64:1", computer, "left");

        computer.fs.write("usb/docs/a.txt", "persistente");
        assertEquals("usb", hub.mountPath("world:1:64:1", "world:0:64:1"));
        assertEquals("persistente", drive.root().resolve("docs/a.txt").toFile().exists()
                ? computer.fs.read("usb/docs/a.txt") : null);
        assertEquals(List.of("usb:left"), computer.events);

        hub.remove("world:1:64:1");
        assertThrows(VirtualFileSystem.FsException.class,
                () -> computer.fs.write("usb/docs/a.txt", "no"));
        assertEquals(List.of("usb:left", "usb_eject:left"), computer.events);

        PenDriveHub reopened = new PenDriveHub(dir.resolve("pendrives"), ids::allocateGlobalId);
        reopened.insert("world:2:64:1", drive.id(), "Datos");
        reopened.attachComputer("world:2:64:1", "world:0:64:1", computer, "left");
        assertEquals("persistente", computer.fs.read("usb/docs/a.txt"));
    }

    @Test
    void duplicateIdCannotClaimTwoReaders() throws Exception {
        PenDriveHub hub = new PenDriveHub(dir.resolve("pendrives"), () -> 10);
        hub.insert("one", 7, "Uno");
        assertThrows(IllegalStateException.class, () -> hub.insert("two", 7, "Copia"));
        assertEquals(1, hub.activeDriveCount());
        assertEquals(7, hub.atLocation("one").id());

        hub.insert("two", 8, "Otro");
        TestComputer computer = new TestComputer(dir.resolve("computer"));
        hub.attachComputer("one", "computer", computer, "left");
        assertThrows(IllegalStateException.class, () -> hub.insert("one", 8, "Copia"));
        assertEquals(1, hub.activeDriveCount());
        assertEquals(null, hub.atLocation("one"));
        assertEquals(8, hub.byId(8).id());
        assertEquals(List.of("usb:left", "usb_eject:left"), computer.events);
    }

    @Test
    void changingDriveEjectsOldAndSecondMountGetsUsb2() throws Exception {
        PenDriveHub hub = new PenDriveHub(dir.resolve("pendrives"), () -> 20);
        TestComputer computer = new TestComputer(dir.resolve("computer"));
        hub.insert("one", 1, "Uno");
        hub.attachComputer("one", "computer", computer, "left");
        hub.insert("two", 2, "Dos");
        hub.attachComputer("two", "computer", computer, "right");

        assertEquals("usb2", hub.mountPath("two", "computer"));
        hub.insert("one", 3, "Tres");
        assertEquals(null, hub.mountPath("one", "computer"));
        hub.attachComputer("one", "computer", computer, "left");
        assertEquals("usb", hub.mountPath("one", "computer"));
        assertEquals(List.of("usb:left", "usb:right", "usb_eject:left", "usb:left"), computer.events);
    }

    @Test
    void globalIdsDoNotCollideWithComputerIdsAndCounterPersists() throws Exception {
        ComputerIdStore ids = new ComputerIdStore(dir.resolve("ids.json"));
        PenDriveHub hub = new PenDriveHub(dir.resolve("pendrives"), ids::allocateGlobalId,
                ids::reserveGlobalId);
        int penId = hub.insert("reader", null, null).id();
        int computerId = ids.idFor("world:1:2:3");
        assertNotEquals(penId, computerId);
        assertThrows(IllegalStateException.class,
                () -> hub.insert("forged", computerId, "ID manipulado"));
        ids.save();

        ComputerIdStore loaded = new ComputerIdStore(dir.resolve("ids.json"));
        loaded.load();
        PenDriveHub reopened = new PenDriveHub(dir.resolve("pendrives"), loaded::allocateGlobalId,
                loaded::reserveGlobalId);
        int nextPen = reopened.insert("other", null, null).id();
        assertTrue(nextPen > Math.max(penId, computerId));
        reopened.insert("foreign", 50, "Externo");
        assertTrue(loaded.idFor("world:9:9:9") > 50);
    }

    @Test
    void repeatedSyncDoesNotDuplicateMountsOrEventsAndLabelPersists() throws Exception {
        PenDriveHub hub = new PenDriveHub(dir.resolve("pendrives"), () -> 30);
        TestComputer computer = new TestComputer(dir.resolve("computer"));
        PenDriveHub.Drive original = hub.insert("reader", 9, "Inicial");
        hub.attachComputer("reader", "computer", computer, "back");
        hub.attachComputer("reader", "computer", computer, "back");
        computer.fs.write("usb/datos.txt", "contenido");

        hub.setLabel("reader", "  Etiqueta guardada  ");
        assertEquals(original, hub.insert("reader", 9, "Etiqueta guardada"));
        assertEquals("Etiqueta guardada", hub.atLocation("reader").label());
        assertEquals(List.of("usb:back"), computer.events);
        assertEquals(List.of("usb"), computer.fs.getMountPaths());

        hub.remove("reader");
        PenDriveHub.Drive reopened = hub.insert("other", 9, "Etiqueta guardada");
        assertEquals(original.root(), reopened.root());
        assertEquals("Etiqueta guardada", reopened.label());
        hub.attachComputer("other", "computer", computer, "left");
        assertEquals("contenido", computer.fs.read("usb/datos.txt"));
    }

    @Test
    void detachingOneComputerLeavesOtherAdjacentMountsAlive() throws Exception {
        PenDriveHub hub = new PenDriveHub(dir.resolve("pendrives"), () -> 40);
        TestComputer first = new TestComputer(dir.resolve("first"));
        TestComputer second = new TestComputer(dir.resolve("second"));
        hub.insert("reader", 5, "Compartido");
        hub.attachComputer("reader", "first", first, "left");
        hub.attachComputer("reader", "second", second, "right");

        hub.detachComputer("first");

        assertEquals(null, hub.mountPath("reader", "first"));
        assertEquals("usb", hub.mountPath("reader", "second"));
        assertEquals(List.of("usb:left", "usb_eject:left"), first.events);
        assertEquals(List.of("usb:right"), second.events);
        second.fs.write("usb/a.txt", "a");
        hub.remove("reader");
        assertThrows(VirtualFileSystem.FsException.class, () -> second.fs.read("usb/a.txt"));
        assertEquals(List.of("usb:right", "usb_eject:right"), second.events);
    }

    @Test
    void staleNasAttachmentIsRepairedOnNextAttach() throws Exception {
        PenDriveHub hub = new PenDriveHub(dir.resolve("pendrives"), () -> 60);
        TestComputer turtle = new TestComputer(dir.resolve("turtle"));
        hub.insert("world:1:64:1#0", 22, "Compartido");
        hub.attachComputer("world:1:64:1#0", "turtle", turtle, "nas", "nas/22");
        turtle.fs.write("nas/22/programa.lua", "print('NAS_OK')");
        turtle.fs.unmount("nas/22");

        assertTrue(!hub.attachmentActive("world:1:64:1#0", "turtle", "nas/22"));
        hub.attachComputer("world:1:64:1#0", "turtle", turtle, "nas", "nas/22");

        assertTrue(hub.attachmentActive("world:1:64:1#0", "turtle", "nas/22"));
        assertEquals("print('NAS_OK')", turtle.fs.read("nas/22/programa.lua"));
    }

    @Test
    void explicitNasMountSharesFilesAndDetachesPerComputer() throws Exception {
        PenDriveHub hub = new PenDriveHub(dir.resolve("pendrives"), () -> 50);
        TestComputer first = new TestComputer(dir.resolve("nas-first"));
        TestComputer second = new TestComputer(dir.resolve("nas-second"));
        hub.insert("world:1:64:1#3", 19, "Compartido");
        hub.attachComputer("world:1:64:1#3", "first", first, "nas", "nas/19");
        hub.attachComputer("world:1:64:1#3", "second", second, "nas", "nas/19");

        first.fs.write("nas/19/programa.lua", "print('NAS_OK')");

        assertEquals("print('NAS_OK')", second.fs.read("nas/19/programa.lua"));
        assertEquals("nas/19", hub.mountPath("world:1:64:1#3", "second"));
        assertEquals(List.of("nas"), first.fs.list(""));
        assertEquals(List.of("19"), first.fs.list("nas"));
        hub.detachComputer("world:1:64:1#3", "first");
        assertThrows(VirtualFileSystem.FsException.class,
                () -> first.fs.read("nas/19/programa.lua"));
        assertEquals("print('NAS_OK')", second.fs.read("nas/19/programa.lua"));
        hub.remove("world:1:64:1#3");
        assertThrows(VirtualFileSystem.FsException.class,
                () -> second.fs.read("nas/19/programa.lua"));
    }
}

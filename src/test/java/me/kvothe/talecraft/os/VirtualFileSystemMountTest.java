package me.kvothe.talecraft.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VirtualFileSystemMountTest {
    @TempDir
    Path dir;

    @Test
    void nestedMountsUseLongestPrefixAndIndependentQuotas() throws Exception {
        VirtualFileSystem fs = new VirtualFileSystem(dir.resolve("computer"), 100);
        fs.mount("usb", dir.resolve("pendrives/7"), 10);
        fs.mount("usb/inner", dir.resolve("pendrives/8"), 5);

        assertEquals(List.of("usb"), fs.list(""));
        assertEquals(List.of("inner"), fs.list("usb"));
        fs.write("root.txt", "r".repeat(80));
        fs.write("usb/file.txt", "12345678");
        fs.write("usb/inner/file.txt", "12345");
        assertEquals("12345678", Files.readString(dir.resolve("pendrives/7/file.txt")));
        assertEquals("12345", Files.readString(dir.resolve("pendrives/8/file.txt")));
        assertThrows(VirtualFileSystem.FsException.class, () -> fs.write("usb/big.txt", "123"));
        assertThrows(VirtualFileSystem.FsException.class,
                () -> fs.write("usb/inner/big.txt", "x"));
        assertEquals(10, fs.getQuotaBytes("usb"));
        assertEquals(5, fs.getQuotaBytes("usb/inner"));
        assertEquals(20, fs.getFreeBytes());
        assertEquals(2, fs.getFreeBytes("usb"));
        assertEquals(0, fs.getFreeBytes("usb/inner"));
        assertEquals("12345", fs.read("usb/inner/../inner/file.txt"));
        assertThrows(VirtualFileSystem.FsException.class,
                () -> fs.write("usb/../../../escape", "x"));
    }

    @Test
    void unmountFailsClosedAndUnmountsNestedMounts() throws Exception {
        VirtualFileSystem fs = new VirtualFileSystem(dir.resolve("computer"), 100);
        fs.mount("usb", dir.resolve("pendrives/7"), 10);
        fs.mount("usb/inner", dir.resolve("pendrives/8"), 10);
        fs.write("usb/file.txt", "safe");

        fs.unmount("usb");

        assertFalse(fs.isMounted("usb"));
        assertFalse(fs.isMounted("usb/inner"));
        assertThrows(VirtualFileSystem.FsException.class, () -> fs.read("usb/file.txt"));
        assertThrows(VirtualFileSystem.FsException.class, () -> fs.write("usb/file.txt", "wrong"));
        assertFalse(Files.exists(dir.resolve("computer/usb/file.txt")));
        fs.mount("usb", dir.resolve("pendrives/7"), 10);
        assertEquals("safe", fs.read("usb/file.txt"));
    }

    @Test
    void reservedNasNamespaceHidesLocalGhostsButAllowsRealMounts() throws Exception {
        Path localGhost = dir.resolve("computer/nas/22/compartido.txt");
        Files.createDirectories(localGhost.getParent());
        Files.writeString(localGhost, "copia local");
        VirtualFileSystem fs = new VirtualFileSystem(dir.resolve("computer"), 1000);
        fs.unmount("nas");

        assertThrows(VirtualFileSystem.FsException.class,
                () -> fs.read("nas/22/compartido.txt"));
        assertThrows(VirtualFileSystem.FsException.class,
                () -> fs.write("nas/22/compartido.txt", "otra copia"));
        assertFalse(fs.list("").contains("nas"));
        assertEquals(List.of(), fs.list("nas"));

        fs.mount("nas/22", dir.resolve("pendrives/22"), 1000);
        fs.write("nas/22/compartido.txt", "PenDrive real");
        assertEquals(List.of("nas"), fs.list(""));
        assertEquals(List.of("22"), fs.list("nas"));
        assertEquals(List.of("compartido.txt"), fs.list("nas/22"));
        assertEquals("PenDrive real", fs.read("nas/22/compartido.txt"));

        fs.unmount("nas/22");
        assertThrows(VirtualFileSystem.FsException.class,
                () -> fs.read("nas/22/compartido.txt"));
        assertFalse(fs.list("").contains("nas"));
    }

    @Test
    void diskReadOnlyMountStillReadsAndLists() throws Exception {
        Path root = dir.resolve("readonly");
        Files.createDirectories(root);
        Files.writeString(root.resolve("info.txt"), "hola");
        VirtualFileSystem fs = new VirtualFileSystem(dir.resolve("computer"), 100);
        fs.mount("external", root, 50, true);

        assertEquals("hola", fs.read("external/info.txt"));
        assertEquals(List.of("info.txt"), fs.list("external"));
        assertThrows(VirtualFileSystem.FsException.class,
                () -> fs.write("external/no.txt", "x"));
        assertThrows(VirtualFileSystem.FsException.class, () -> fs.delete("external/info.txt"));
    }

    @Test
    void mountResolveCannotLeaveItsOwnHostRoot() throws Exception {
        VirtualFileSystem fs = new VirtualFileSystem(dir.resolve("computer"), 100);
        fs.mount("usb", dir.resolve("pendrives/7"), 10);
        assertEquals(dir.resolve("pendrives/7/docs/a.txt").toAbsolutePath(),
                fs.resolve("usb/docs/a.txt"));
        assertEquals(dir.resolve("computer/docs/a.txt").toAbsolutePath(),
                fs.resolve("usb/../docs/a.txt"));
    }

    @Test
    void nestedMountCreatesVirtualParentDirectories() throws Exception {
        VirtualFileSystem fs = new VirtualFileSystem(dir.resolve("computer"), 100);
        fs.mount("virtual/deep", dir.resolve("pendrives/9"), 10);
        fs.write("virtual/deep/a.txt", "a");

        assertTrue(fs.exists("virtual"));
        assertTrue(fs.isDir("virtual"));
        assertEquals(List.of("deep"), fs.list("virtual"));
        assertEquals(List.of("a.txt"), fs.list("virtual/deep"));
        assertEquals("a", fs.read("virtual/deep/a.txt"));
    }
}

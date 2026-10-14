package me.kvothe.talecraft.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VirtualFileSystemTest {

    @TempDir
    Path dir;

    private VirtualFileSystem newFs(long quota) throws Exception {
        return new VirtualFileSystem(dir.resolve("computer"), quota);
    }

    @Test
    void writeReadRoundtripPersistsOnDisk() throws Exception {
        VirtualFileSystem fs = newFs(1_000_000);
        fs.write("docs/hello.txt", "hola mundo");
        assertEquals("hola mundo", fs.read("docs/hello.txt"));
        // Real file exists under the root: persistence is just a directory.
        assertTrue(Files.isRegularFile(dir.resolve("computer/docs/hello.txt")));
        // A fresh instance over the same root sees the file (server restart case).
        VirtualFileSystem reopened = newFs(1_000_000);
        assertEquals("hola mundo", reopened.read("docs/hello.txt"));
    }

    @Test
    void jailRejectsEscapesAndAbsolutes() throws Exception {
        VirtualFileSystem fs = newFs(1_000_000);
        for (String bad : new String[] { "../evil.txt", "a/../../evil.txt", "C:/win/evil.txt" }) {
            assertThrows(VirtualFileSystem.FsException.class, () -> fs.write(bad, "x"),
                    "should reject: " + bad);
        }
        // "a/../b.txt" stays inside and is fine.
        fs.write("a/../b.txt", "ok");
        assertTrue(fs.exists("b.txt"));
        // Leading "/" means the jail root (CC-style), never the host filesystem.
        fs.write("/etc/passwd", "x");
        assertTrue(fs.exists("etc/passwd"));
        assertTrue(Files.isRegularFile(dir.resolve("computer/etc/passwd")));
    }

    @Test
    void quotaIsEnforced() throws Exception {
        VirtualFileSystem fs = newFs(100);
        fs.write("a.txt", "x".repeat(80));
        assertThrows(VirtualFileSystem.FsException.class, () -> fs.write("b.txt", "x".repeat(30)));
        // Overwriting the same file recomputes against its current size.
        fs.write("a.txt", "y".repeat(90));
        assertEquals(90, fs.getSize("a.txt"));
        assertEquals(10, fs.getFreeBytes());
    }

    @Test
    void mkdirListDelete() throws Exception {
        VirtualFileSystem fs = newFs(1_000_000);
        fs.mkdir("data/programs");
        assertTrue(fs.isDir("data"));
        fs.write("data/programs/a.lua", "print(1)");
        fs.write("data/b.lua", "print(2)");
        assertEquals(List.of("b.lua", "programs"), fs.list("data"));
        fs.delete("data");
        assertFalse(fs.exists("data/b.lua"));
        assertThrows(VirtualFileSystem.FsException.class, () -> fs.delete(""));
    }

    @Test
    void romOverlayIsReadOnlyAndShadowsDisk() throws Exception {
        VirtualFileSystem fs = newFs(1_000_000);
        fs.mountRom(Map.of("rom/shell.lua", "-- shell", "rom/programs/id.lua", "-- id"));
        assertTrue(fs.exists("rom/shell.lua"));
        assertTrue(fs.isDir("rom"));
        assertTrue(fs.isDir("rom/programs"));
        assertEquals(List.of("programs", "shell.lua"), fs.list("rom"));
        assertEquals("-- shell", fs.read("rom/shell.lua"));
        assertThrows(VirtualFileSystem.FsException.class, () -> fs.write("rom/x.lua", "x"));
        assertThrows(VirtualFileSystem.FsException.class, () -> fs.mkdir("rom/sub"));
        assertThrows(VirtualFileSystem.FsException.class, () -> fs.delete("rom/shell.lua"));
    }

    @Test
    void readMissingReturnsNullAndAppendWorks() throws Exception {
        VirtualFileSystem fs = newFs(1_000_000);
        assertNull(fs.read("nope.txt"));
        fs.write("log.txt", "a");
        fs.append("log.txt", "b");
        assertEquals("ab", fs.read("log.txt"));
    }
}

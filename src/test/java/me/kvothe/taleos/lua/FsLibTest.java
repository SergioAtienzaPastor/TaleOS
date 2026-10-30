package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.taleos.os.VirtualFileSystem;

class FsLibTest {

    private static final long BUDGET = 100_000;

    @TempDir
    Path dir;

    private LuaMachine newMachine(long quota) throws Exception {
        return new LuaMachine(BUDGET, new VirtualFileSystem(dir.resolve("c0"), quota));
    }

    @Test
    void luaRoundtripThroughFsApi() throws Exception {
        LuaMachine m = newMachine(1_000_000);
        m.load("fs.write('notes/a.txt', 'contenido')\n"
                + "print(fs.exists('notes/a.txt'), fs.isDir('notes'), fs.read('notes/a.txt'))\n"
                + "print(fs.getSize('notes/a.txt') > 0, fs.getCapacity() > 0)", "fsdemo");
        m.tick();
        assertEquals(LuaMachine.State.DONE, m.getState());
        assertEquals("true\ttrue\tcontenido\ntrue\ttrue\n", m.drainOutput());
    }

    @Test
    void jailEscapeFromLuaIsAnError() throws Exception {
        LuaMachine m = newMachine(1_000_000);
        m.load("local ok, err = pcall(fs.write, '../escape.txt', 'x')\n"
                + "print(ok, err and err:match('escapes') ~= nil)", "jailbreak");
        m.tick();
        assertEquals("false\ttrue\n", m.drainOutput());
        assertTrue(!java.nio.file.Files.exists(dir.resolve("escape.txt")),
                "no file may be written outside the jail");
    }

    @Test
    void quotaOverflowFromLuaIsCatchable() throws Exception {
        LuaMachine m = newMachine(64);
        m.load("local ok, err = pcall(fs.write, 'big.txt', string.rep('x', 200))\n"
                + "print(ok, err and err:match('quota') ~= nil)", "quota");
        m.tick();
        assertEquals("false\ttrue\n", m.drainOutput());
    }
}

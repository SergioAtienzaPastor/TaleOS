package me.kvothe.taleos.ui.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.taleos.os.VirtualFileSystem;

class ShellCompleterTest {

    @TempDir
    Path dir;

    private VirtualFileSystem fileSystem;
    private ShellCompleter completer;

    @BeforeEach
    void setUp() throws Exception {
        fileSystem = new VirtualFileSystem(dir.resolve("computer"), 1_000_000);
        fileSystem.mountRom(Map.of(
                "rom/shell.lua", "-- shell",
                "rom/programs/id.lua", "-- id",
                "rom/programs/ls.lua", "-- ls",
                "rom/programs/cat.lua", "-- cat"));
        fileSystem.write("demo.lua", "print('demo')");
        fileSystem.write("scripts/tools/nested.lua", "print('nested')");
        fileSystem.write("notes.txt", "not a program");
        fileSystem.mkdir("fake.lua");
        fileSystem.write("docs/notes/a.txt", "a");
        fileSystem.write("docs/notes/b.txt", "b");
        completer = new ShellCompleter(fileSystem);
    }

    @Test
    void commandsComeFromRomAndVirtualFileSystem() {
        List<String> candidates = completer.complete("", 0).candidates();

        assertTrue(candidates.containsAll(List.of("id", "ls", "cat", "demo", "scripts/tools/nested")));
        assertFalse(candidates.contains("shell"));
        assertFalse(candidates.contains("notes.txt"));
        assertFalse(candidates.contains("fake"));
    }

    @Test
    void pathsKeepDirectoryPrefixAndMarkDirectories() {
        Completion completion = completer.complete("cat docs/n", 9);

        assertEquals(List.of("docs/notes/"), completion.candidates());
        for (String command : List.of("edit", "cat", "ls", "mkdir", "rm")) {
            String line = command + " docs/notes/";
            assertEquals(List.of("docs/notes/a.txt", "docs/notes/b.txt"),
                    completer.complete(line, line.length()).candidates(), command);
        }
    }

    @Test
    void commandArgumentsAreCompletedOnlyInTheirFirstArgument() {
        assertEquals(List.of("forward"), completer.complete("go f", 4).candidates());
        assertEquals(List.of("left", "right"), completer.complete("turn ", 5).candidates());
        assertEquals(List.of("left", "right"), completer.complete("equip ", 6).candidates());
        assertEquals(List.of("left"), completer.complete("unequip l", 9).candidates());
        assertTrue(completer.complete("go forward ", 11).candidates().isEmpty());
    }

    @Test
    void pathTraversalFailsClosed() {
        assertTrue(completer.complete("cat ../../", 10).candidates().isEmpty());
        assertTrue(completer.complete("cat C:/", 7).candidates().isEmpty());
    }

    @Test
    void mountedPenDrivePathsAreCompletedAndDisappearAfterEject() throws Exception {
        fileSystem.mount("usb", dir.resolve("pendrives/1"), 100);
        fileSystem.mount("usb2", dir.resolve("pendrives/2"), 100);
        fileSystem.write("usb/docs/readme.txt", "one");
        fileSystem.write("usb2/docs/readme.txt", "two");

        assertEquals(List.of("usb/docs/"), completer.complete("cat usb/d", 9).candidates());
        assertEquals(List.of("usb2/docs/"), completer.complete("cat usb2/d", 10).candidates());

        fileSystem.unmount("usb");
        assertTrue(completer.complete("cat usb/", 8).candidates().isEmpty());
        assertEquals(List.of("usb2/docs/readme.txt"),
                completer.complete("cat usb2/docs/r", 14).candidates());
    }

    @Test
    void completionInMiddlePreservesFollowingArguments() {
        CompletionSession session = new CompletionSession(completer);
        session.reset("cat  tail");
        session.valueChanged("ca  tail");

        assertTrue(session.complete());
        assertEquals("cat  tail", session.getValue());
        assertEquals(3, session.getCaret());
    }
}

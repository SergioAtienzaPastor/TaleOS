package me.kvothe.talecraft.ui.completion;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import me.kvothe.talecraft.os.VirtualFileSystem;

public final class ShellCompleter implements Completer {

    private static final Set<String> PATH_COMMANDS = Set.of("edit", "cat", "ls", "mkdir", "rm");
    private static final List<String> GO_DIRECTIONS = List.of(
            "forward", "back", "up", "down", "left", "right");
    private static final List<String> SIDES = List.of("left", "right");

    private final VirtualFileSystem fileSystem;

    public ShellCompleter(VirtualFileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public Completion complete(String text, int caret) {
        if (caret < 0 || caret > text.length()) {
            return null;
        }
        int start = caret;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        String token = text.substring(start, caret);
        String before = text.substring(0, start);
        if (before.isBlank()) {
            return new Completion(start, caret, matching(programNames(), token));
        }
        String[] words = before.trim().split("\\s+");
        String command = words[0];
        int argument = words.length;
        List<String> candidates = List.of();
        if (PATH_COMMANDS.contains(command)) {
            candidates = pathCandidates(token);
        } else if (argument == 1) {
            if (command.equals("go")) {
                candidates = matching(GO_DIRECTIONS, token);
            } else if (command.equals("turn") || command.equals("equip")
                    || command.equals("unequip")) {
                candidates = matching(SIDES, token);
            }
        }
        return new Completion(start, caret, candidates);
    }

    private List<String> programNames() {
        TreeSet<String> names = new TreeSet<>();
        addPrograms(names, "rom/programs", "rom/programs/");
        addPrograms(names, "", "");
        return List.copyOf(names);
    }

    private void addPrograms(Set<String> names, String directory, String stripPrefix) {
        for (String name : listDirectory(directory)) {
            String path = directory.isEmpty() ? name : directory + "/" + name;
            try {
                if (fileSystem.isDir(path)) {
                    if (!path.equals("rom")) {
                        addPrograms(names, path, stripPrefix);
                    }
                } else if (name.endsWith(".lua")) {
                    names.add(path.substring(stripPrefix.length(), path.length() - 4));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private List<String> pathCandidates(String token) {
        int slash = token.lastIndexOf('/');
        String directory = slash < 0 ? "" : token.substring(0, slash + 1);
        String fragment = slash < 0 ? token : token.substring(slash + 1);
        TreeSet<String> matches = new TreeSet<>();
        for (String name : listDirectory(directory)) {
            if (!name.startsWith(fragment)) {
                continue;
            }
            String path = directory + name;
            try {
                matches.add(path + (fileSystem.isDir(path) ? "/" : ""));
            } catch (Exception ignored) {
            }
        }
        return List.copyOf(matches);
    }

    private List<String> listDirectory(String directory) {
        try {
            boolean rom = directory.equals("rom") || directory.startsWith("rom/");
            if (!rom && Files.isSymbolicLink(fileSystem.resolve(directory))) {
                return List.of();
            }
            return fileSystem.list(directory);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<String> matching(List<String> values, String fragment) {
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(fragment)) {
                matches.add(value);
            }
        }
        return matches;
    }
}

package com.kvothe.talecraft.os;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Per-computer virtual filesystem. Everything lives under a single root
 * directory (wired to {@code <world>/talecraft/computer/<id>/} at runtime) and
 * no path may ever escape it: absolute paths, drive letters and ".." segments
 * that would leave the root are rejected. A byte quota bounds total usage.
 *
 * <p>Additionally a read-only {@code rom/} mount can be overlaid (the TaleOS
 * files shipped in the plugin jar): reads under {@code rom/} are served from
 * the overlay and writes there are rejected.
 *
 * <p>Text files are UTF-8. This class is deliberately free of LuaJ types so it
 * can be unit-tested headless; the Lua binding lives in
 * {@code com.kvothe.talecraft.lua.FsLib}.
 */
public final class VirtualFileSystem {

    /** Raised when a path is invalid or would escape the jail. */
    public static final class FsException extends Exception {
        public FsException(String message) {
            super(message);
        }
    }

    private final Path root;
    private final long quotaBytes;
    private volatile Map<String, String> rom = Map.of();

    public VirtualFileSystem(Path root, long quotaBytes) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.quotaBytes = quotaBytes;
        Files.createDirectories(this.root);
    }

    public Path getRoot() {
        return root;
    }

    public long getQuotaBytes() {
        return quotaBytes;
    }

    /** Mounts the read-only rom overlay: keys are relative paths like
     * "rom/shell.lua", values the file contents. */
    public void mountRom(Map<String, String> files) {
        this.rom = Map.copyOf(files);
    }

    /** Normalizes a Lua-facing path to a relative key, rejecting jail escapes. */
    private String normalizeRel(String path) throws FsException {
        if (path == null) {
            throw new FsException("path is nil");
        }
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.contains(":")) {
            throw new FsException("drive/absolute paths are not allowed: " + path);
        }
        List<String> parts = new ArrayList<>();
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) {
                continue;
            }
            if (seg.equals("..")) {
                if (parts.isEmpty()) {
                    throw new FsException("path escapes the computer filesystem: " + path);
                }
                parts.remove(parts.size() - 1);
            } else {
                parts.add(seg);
            }
        }
        return String.join("/", parts);
    }

    private static boolean isRomPath(String rel) {
        return rel.equals("rom") || rel.startsWith("rom/");
    }

    /** Resolves a Lua-facing path against the root, rejecting jail escapes. */
    public Path resolve(String path) throws FsException {
        return root.resolve(normalizeRel(path)).normalize();
    }

    public boolean exists(String path) throws FsException {
        String rel = normalizeRel(path);
        if (isRomPath(rel)) {
            return rom.containsKey(rel) || isRomDir(rel);
        }
        return Files.exists(root.resolve(rel));
    }

    public boolean isDir(String path) throws FsException {
        String rel = normalizeRel(path);
        if (isRomPath(rel)) {
            return !rom.containsKey(rel) && isRomDir(rel);
        }
        return Files.isDirectory(root.resolve(rel));
    }

    /** Names (not paths) directly inside the directory, sorted. */
    public List<String> list(String path) throws FsException, IOException {
        String rel = normalizeRel(path);
        if (isRomPath(rel)) {
            String prefix = rel.isEmpty() ? "" : rel + "/";
            TreeSet<String> names = new TreeSet<>();
            for (String key : rom.keySet()) {
                if (key.startsWith(prefix)) {
                    String rest = key.substring(prefix.length());
                    int slash = rest.indexOf('/');
                    names.add(slash < 0 ? rest : rest.substring(0, slash));
                }
            }
            if (names.isEmpty()) {
                throw new FsException("not a directory: " + path);
            }
            return List.copyOf(names);
        }
        Path dir = root.resolve(rel);
        if (!Files.isDirectory(dir)) {
            throw new FsException("not a directory: " + path);
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                names.add(child.getFileName().toString());
            }
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    /** Reads the whole file, or returns null when it does not exist. */
    public String read(String path) throws FsException, IOException {
        String rel = normalizeRel(path);
        if (isRomPath(rel)) {
            return rom.get(rel);
        }
        Path file = root.resolve(rel);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** Creates or fully overwrites a file. Parent dirs are created. */
    public void write(String path, String data) throws FsException, IOException {
        writeBytes(path, data.getBytes(StandardCharsets.UTF_8), false);
    }

    public void append(String path, String data) throws FsException, IOException {
        writeBytes(path, data.getBytes(StandardCharsets.UTF_8), true);
    }

    public void mkdir(String path) throws FsException, IOException {
        String rel = normalizeRel(path);
        rejectRomWrite(rel);
        Files.createDirectories(root.resolve(rel));
    }

    /** Deletes a file or directory (recursively). Missing paths are ignored. */
    public void delete(String path) throws FsException, IOException {
        String rel = normalizeRel(path);
        rejectRomWrite(rel);
        Path target = root.resolve(rel);
        if (target.equals(root)) {
            throw new FsException("refusing to delete the filesystem root");
        }
        if (!Files.exists(target)) {
            return;
        }
        try (var walk = Files.walk(target)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    /** Size of a file, or of a directory's subtree. 0 when missing. */
    public long getSize(String path) throws FsException, IOException {
        String rel = normalizeRel(path);
        if (isRomPath(rel)) {
            String content = rom.get(rel);
            return content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length;
        }
        Path target = root.resolve(rel);
        if (!Files.exists(target)) {
            return 0;
        }
        if (Files.isRegularFile(target)) {
            return Files.size(target);
        }
        try (var walk = Files.walk(target)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).sum();
        }
    }

    public long getUsedBytes() throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).sum();
        }
    }

    public long getFreeBytes() throws IOException {
        return Math.max(0, quotaBytes - getUsedBytes());
    }

    private boolean isRomDir(String rel) {
        String prefix = rel + "/";
        for (String key : rom.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void rejectRomWrite(String rel) throws FsException {
        if (isRomPath(rel)) {
            throw new FsException("rom is read-only: " + rel);
        }
    }

    private void writeBytes(String path, byte[] data, boolean append) throws FsException, IOException {
        String rel = normalizeRel(path);
        rejectRomWrite(rel);
        Path file = root.resolve(rel);
        if (file.equals(root)) {
            throw new FsException("cannot write to the filesystem root");
        }
        if (Files.isDirectory(file)) {
            throw new FsException("path is a directory: " + path);
        }
        long existing = Files.isRegularFile(file) && !append ? Files.size(file) : 0;
        long projected = getUsedBytes() - existing + data.length;
        if (projected > quotaBytes) {
            throw new FsException("disk quota exceeded (" + quotaBytes + " bytes)");
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        if (append) {
            Files.write(file, data, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } else {
            Files.write(file, data);
        }
    }
}

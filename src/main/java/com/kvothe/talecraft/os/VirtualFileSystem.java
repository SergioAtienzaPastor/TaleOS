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

/**
 * Per-computer virtual filesystem. Everything lives under a single root
 * directory (wired to {@code <world>/talecraft/computer/<id>/} at runtime) and
 * no path may ever escape it: absolute paths, drive letters and ".." segments
 * that would leave the root are rejected. A byte quota bounds total usage.
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

    /** Resolves a Lua-facing path against the root, rejecting jail escapes. */
    public Path resolve(String path) throws FsException {
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
        Path resolved = root.resolve(p).normalize();
        if (!resolved.equals(root) && !resolved.startsWith(root)) {
            throw new FsException("path escapes the computer filesystem: " + path);
        }
        return resolved;
    }

    public boolean exists(String path) throws FsException {
        return Files.exists(resolve(path));
    }

    public boolean isDir(String path) throws FsException {
        return Files.isDirectory(resolve(path));
    }

    /** Names (not paths) directly inside the directory, sorted. */
    public List<String> list(String path) throws FsException, IOException {
        Path dir = resolve(path);
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
        Path file = resolve(path);
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
        Files.createDirectories(resolve(path));
    }

    /** Deletes a file or directory (recursively). Missing paths are ignored. */
    public void delete(String path) throws FsException, IOException {
        Path target = resolve(path);
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
        Path target = resolve(path);
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

    private void writeBytes(String path, byte[] data, boolean append) throws FsException, IOException {
        Path file = resolve(path);
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

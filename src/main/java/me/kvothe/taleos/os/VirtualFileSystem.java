package me.kvothe.taleos.os;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Jailed, UTF-8 filesystem composed of independent mounts. The root and every
 * disk mount have their own host directory and byte quota; memory mounts are
 * read-only. The longest matching mount prefix wins, so nested mounts never
 * expose the host directory shadowed by another mount.
 */
public final class VirtualFileSystem {

    public static final class FsException extends Exception {
        public FsException(String message) {
            super(message);
        }
    }

    private static final class Mount {
        private final Path root;
        private final long quotaBytes;
        private final Map<String, String> memory;
        private final boolean readOnly;

        private Mount(Path root, long quotaBytes, Map<String, String> memory, boolean readOnly) {
            this.root = root;
            this.quotaBytes = quotaBytes;
            this.memory = memory;
            this.readOnly = readOnly;
        }

        private boolean inMemory() {
            return memory != null;
        }

        private boolean readOnly() {
            return readOnly;
        }
    }

    private record Resolved(String mountPath, Mount mount, String relative) {
    }

    private final Path root;
    private final long quotaBytes;
    private final Map<String, Mount> mounts = new java.util.TreeMap<>();
    private final Set<String> detached = new HashSet<>();

    public VirtualFileSystem(Path root, long quotaBytes) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.quotaBytes = quotaBytes;
        Files.createDirectories(this.root);
        mounts.put("", new Mount(this.root, quotaBytes, null, false));
    }

    public Path getRoot() {
        return root;
    }

    public long getQuotaBytes() {
        return quotaBytes;
    }

    public synchronized long getQuotaBytes(String path) throws FsException {
        return resolveMount(normalizeRel(path)).mount.quotaBytes;
    }

    /** Mounts the read-only rom overlay. Keys may include the rom/ prefix. */
    public synchronized void mountRom(Map<String, String> files) {
        java.util.HashMap<String, String> relative = new java.util.HashMap<>();
        files.forEach((name, content) -> {
            String key = name.replace('\\', '/');
            key = key.equals("rom") ? "" : key.startsWith("rom/") ? key.substring(4) : key;
            if (!key.isEmpty()) {
                relative.put(key, content);
            }
        });
        mounts.put("rom", new Mount(null, 0, Map.copyOf(relative), true));
        detached.remove("rom");
    }

    /** Mounts a writable disk with an independent quota. */
    public synchronized void mount(String path, Path mountRoot, long mountQuotaBytes)
            throws FsException, IOException {
        mount(path, mountRoot, mountQuotaBytes, false);
    }

    public synchronized void mount(String path, Path mountRoot, long mountQuotaBytes,
            boolean readOnly) throws FsException, IOException {
        String rel = normalizeRel(path);
        if (rel.isEmpty()) {
            throw new FsException("cannot replace the filesystem root");
        }
        if (mounts.containsKey(rel)) {
            throw new FsException("mount is already attached: " + rel);
        }
        Path normalized = mountRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        mounts.put(rel, new Mount(normalized, mountQuotaBytes, null, readOnly));
        detached.remove(rel);
    }

    /** Detaches this mount and any nested mounts. Their paths fail closed. */
    public synchronized void unmount(String path) throws FsException {
        String rel = normalizeRel(path);
        if (rel.isEmpty()) {
            throw new FsException("cannot unmount the filesystem root");
        }
        boolean found = false;
        for (String mounted : new ArrayList<>(mounts.keySet())) {
            if (mounted.equals(rel) || mounted.startsWith(rel + "/")) {
                mounts.remove(mounted);
                detached.add(mounted);
                found = true;
            }
        }
        if (!found) {
            detached.add(rel);
        }
    }

    public synchronized boolean isMounted(String path) throws FsException {
        return mounts.containsKey(normalizeRel(path));
    }

    public synchronized List<String> getMountPaths() {
        return mounts.keySet().stream().filter(path -> !path.isEmpty()).toList();
    }

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

    private Resolved resolveMount(String rel) throws FsException {
        String match = null;
        for (String mounted : mounts.keySet()) {
            if ((mounted.isEmpty() || rel.equals(mounted) || rel.startsWith(mounted + "/"))
                    && (match == null || mounted.length() > match.length())) {
                match = mounted;
            }
        }
        if (match == null) {
            throw new FsException("mount is not attached: " + rel);
        }
        Mount mount = mounts.get(match);
        if (match.isEmpty()) {
            for (String missing : detached) {
                if ((rel.equals(missing) || rel.startsWith(missing + "/")) && !hasNestedMount(rel)) {
                    throw new FsException("mount is not attached: " + missing);
                }
            }
        }
        String relative = match.isEmpty() ? rel : rel.substring(Math.min(rel.length(), match.length() + 1));
        return new Resolved(match, mount, relative);
    }

    /** Resolves a Lua path inside its owning mount, never on the host root. */
    public Path resolve(String path) throws FsException {
        Resolved resolved = resolveMount(normalizeRel(path));
        Path mountRoot = resolved.mount.root == null ? root : resolved.mount.root;
        Path host = mountRoot.resolve(resolved.relative).normalize();
        return checkedHost(mountRoot, host);
    }

    private Path checkedHost(Path mountRoot, Path host) throws FsException {
        if (!host.startsWith(mountRoot)) {
            throw new FsException("path escapes its mount");
        }
        try {
            Path realRoot = mountRoot.toRealPath();
            Path current = host;
            while (current != null && !Files.exists(current)) {
                current = current.getParent();
            }
            if (current != null && !current.toRealPath().startsWith(realRoot)) {
                throw new FsException("path escapes its mount through a symbolic link");
            }
        } catch (IOException e) {
            throw new FsException("cannot verify mount jail: " + e.getMessage());
        }
        return host;
    }

    public boolean exists(String path) throws FsException {
        String rel = normalizeRel(path);
        Resolved resolved = resolveMount(rel);
        if (hasNestedMount(rel)) {
            return true;
        }
        if (resolved.mount.inMemory()) {
            return resolved.relative.isEmpty() || resolved.mount.memory.containsKey(resolved.relative)
                    || isMemoryDir(resolved.mount, resolved.relative);
        }
        return Files.exists(host(resolved));
    }

    public boolean isDir(String path) throws FsException {
        String rel = normalizeRel(path);
        Resolved resolved = resolveMount(rel);
        if (hasNestedMount(rel)) {
            return true;
        }
        if (resolved.mount.inMemory()) {
            return resolved.relative.isEmpty() || (!resolved.mount.memory.containsKey(resolved.relative)
                    && isMemoryDir(resolved.mount, resolved.relative));
        }
        return Files.isDirectory(host(resolved));
    }

    private boolean hasNestedMount(String rel) {
        String prefix = rel.isEmpty() ? "" : rel + "/";
        for (String mounted : mounts.keySet()) {
            if (!mounted.isEmpty() && (rel.isEmpty() || mounted.startsWith(prefix))) {
                return true;
            }
        }
        return false;
    }

    /** Names directly inside a directory, including child mount points, sorted. */
    public List<String> list(String path) throws FsException, IOException {
        String rel = normalizeRel(path);
        Resolved resolved;
        try {
            resolved = resolveMount(rel);
        } catch (FsException exception) {
            if (rel.equals("nas") && detached.contains("nas")) {
                return List.of();
            }
            throw exception;
        }
        TreeSet<String> names = new TreeSet<>();
        for (String mounted : mounts.keySet()) {
            if (mounted.isEmpty()) {
                continue;
            }
            String remainder = null;
            if (rel.isEmpty()) {
                remainder = mounted;
            } else if (mounted.startsWith(rel + "/")) {
                remainder = mounted.substring(rel.length() + 1);
            }
            if (remainder != null && !remainder.isEmpty()) {
                int slash = remainder.indexOf('/');
                names.add(slash < 0 ? remainder : remainder.substring(0, slash));
            }
        }
        if (resolved.mount.inMemory()) {
            String prefix = resolved.relative.isEmpty() ? "" : resolved.relative + "/";
            for (String key : resolved.mount.memory.keySet()) {
                if (key.startsWith(prefix)) {
                    String rest = key.substring(prefix.length());
                    int slash = rest.indexOf('/');
                    if (!rest.isEmpty()) {
                        names.add(slash < 0 ? rest : rest.substring(0, slash));
                    }
                }
            }
            if (names.isEmpty() && !resolved.relative.isEmpty()
                    && !isMemoryDir(resolved.mount, resolved.relative)) {
                throw new FsException("not a directory: " + path);
            }
            return List.copyOf(names);
        }
        Path dir = host(resolved);
        if (!Files.isDirectory(dir)) {
            if (!names.isEmpty()) {
                return List.copyOf(names);
            }
            throw new FsException("not a directory: " + path);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                String childRel = rel.isEmpty() ? name : rel + "/" + name;
                boolean hidden = false;
                for (String missing : detached) {
                    if (resolved.mountPath().isEmpty()
                            && (childRel.equals(missing) || childRel.startsWith(missing + "/"))
                            && !hasNestedMount(childRel)) {
                        hidden = true;
                        break;
                    }
                }
                if (!hidden) {
                    names.add(name);
                }
            }
        }
        return List.copyOf(names);
    }

    public String read(String path) throws FsException, IOException {
        Resolved resolved = resolveMount(normalizeRel(path));
        if (resolved.mount.inMemory()) {
            return resolved.mount.memory.get(resolved.relative);
        }
        Path file = host(resolved);
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
    }

    public void write(String path, String data) throws FsException, IOException {
        writeBytes(path, data.getBytes(StandardCharsets.UTF_8), false);
    }

    public void append(String path, String data) throws FsException, IOException {
        writeBytes(path, data.getBytes(StandardCharsets.UTF_8), true);
    }

    public void mkdir(String path) throws FsException, IOException {
        Resolved resolved = writable(normalizeRel(path));
        if (resolved.relative.isEmpty()) {
            return;
        }
        Files.createDirectories(host(resolved));
    }

    public void delete(String path) throws FsException, IOException {
        String rel = normalizeRel(path);
        Resolved resolved = writable(rel);
        if (resolved.relative.isEmpty()) {
            throw new FsException("refusing to delete a mount root: " + rel);
        }
        for (String mounted : mounts.keySet()) {
            if (mounted.startsWith(rel + "/")) {
                throw new FsException("directory contains a mounted filesystem: " + rel);
            }
        }
        Path target = host(resolved);
        if (!Files.exists(target)) {
            return;
        }
        try (var walk = Files.walk(target)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    public long getSize(String path) throws FsException, IOException {
        Resolved resolved = resolveMount(normalizeRel(path));
        if (resolved.mount.inMemory()) {
            String content = resolved.mount.memory.get(resolved.relative);
            if (content != null) {
                return content.getBytes(StandardCharsets.UTF_8).length;
            }
            if (resolved.relative.isEmpty() || isMemoryDir(resolved.mount, resolved.relative)) {
                return memoryUsed(resolved.mount);
            }
            return 0;
        }
        Path target = host(resolved);
        return diskSize(target);
    }

    public long getUsedBytes() throws IOException {
        return diskSize(root);
    }

    public long getUsedBytes(String path) throws FsException, IOException {
        Resolved resolved = resolveMount(normalizeRel(path));
        return resolved.mount.inMemory() ? memoryUsed(resolved.mount) : diskSize(resolved.mount.root);
    }

    public long getFreeBytes() throws IOException {
        return Math.max(0, quotaBytes - getUsedBytes());
    }

    public long getFreeBytes(String path) throws FsException, IOException {
        Resolved resolved = resolveMount(normalizeRel(path));
        return resolved.mount.inMemory() ? 0
                : Math.max(0, resolved.mount.quotaBytes - diskSize(resolved.mount.root));
    }

    private static long diskSize(Path target) throws IOException {
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

    private static long memoryUsed(Mount mount) {
        return mount.memory.values().stream()
                .mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length).sum();
    }

    private static boolean isMemoryDir(Mount mount, String rel) {
        String prefix = rel + "/";
        return mount.memory.keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    private Resolved writable(String rel) throws FsException {
        Resolved resolved = resolveMount(rel);
        if (resolved.mount.readOnly()) {
            throw new FsException((resolved.mountPath.isEmpty() ? rel : resolved.mountPath)
                    + " is read-only: " + rel);
        }
        return resolved;
    }

    private Path host(Resolved resolved) throws FsException {
        return checkedHost(resolved.mount.root, resolved.mount.root.resolve(resolved.relative).normalize());
    }

    private void writeBytes(String path, byte[] data, boolean append) throws FsException, IOException {
        Resolved resolved = writable(normalizeRel(path));
        if (resolved.relative.isEmpty()) {
            throw new FsException("cannot write to a mount root: " + path);
        }
        Path file = host(resolved);
        if (Files.isDirectory(file)) {
            throw new FsException("path is a directory: " + path);
        }
        long existing = Files.isRegularFile(file) && !append ? Files.size(file) : 0;
        long projected = diskSize(resolved.mount.root) - existing + data.length;
        if (projected > resolved.mount.quotaBytes) {
            throw new FsException("disk quota exceeded (" + resolved.mount.quotaBytes + " bytes)");
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

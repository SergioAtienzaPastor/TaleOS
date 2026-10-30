package me.kvothe.taleos.computer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent store of computer ids. Lives in {@code mods/TaleOS/ids.json}
 * relative to the server working directory. Writes are atomic (tmp + rename),
 * following the CC:Tweaked lesson on crash-safe id allocation.
 *
 * <p>JSON is hand-rolled on purpose: the file only carries a counter and a
 * {@code "world:x:y:z" -> id} map with simple string keys.
 */
public final class ComputerIdStore {

    private final Path file;
    private int nextId = 0;
    private final Map<String, Integer> byBlock = new ConcurrentHashMap<>();
    private final Map<Integer, String> byId = new ConcurrentHashMap<>();

    public ComputerIdStore(Path file) {
        this.file = file;
    }

    /** Gets the id for a block position, allocating a fresh one if unknown. */
    public synchronized int idFor(String blockKey) {
        Integer existing = byBlock.get(blockKey);
        if (existing != null) {
            return existing;
        }
        int id = nextId++;
        byBlock.put(blockKey, id);
        byId.put(id, blockKey);
        return id;
    }

    public synchronized Integer idAt(String blockKey) {
        return byBlock.get(blockKey);
    }

    /** Allocates and saves a global id shared with PenDrive metadata. */
    public synchronized int allocateGlobalId() {
        int id = nextId++;
        try {
            save();
            return id;
        } catch (IOException exception) {
            nextId--;
            throw new IllegalStateException("cannot persist global id allocation", exception);
        }
    }

    /** Reserves an id carried by an existing PenDrive, including foreign worlds. */
    public synchronized void reserveGlobalId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("negative global id: " + id);
        }
        if (byId.containsKey(id)) {
            throw new IllegalStateException("global id #" + id + " already belongs to a computer");
        }
        int previous = nextId;
        nextId = Math.max(nextId, id + 1);
        try {
            save();
        } catch (IOException exception) {
            nextId = previous;
            throw new IllegalStateException("cannot persist global id reservation", exception);
        }
    }

    /** Binds an item-carried id to its new position without ever overwriting a
     * different machine or allowing two positions to share the same id. */
    public synchronized int bind(String blockKey, int id) {
        if (id < 0) {
            throw new IllegalArgumentException("negative computer id: " + id);
        }
        Integer atBlock = byBlock.get(blockKey);
        if (atBlock != null) {
            if (atBlock == id) {
                return id;
            }
            throw new IllegalStateException("position " + blockKey
                    + " already belongs to computer #" + atBlock);
        }
        String atId = byId.get(id);
        if (atId != null) {
            throw new IllegalStateException("computer #" + id + " is already active at " + atId);
        }
        byBlock.put(blockKey, id);
        byId.put(id, blockKey);
        nextId = Math.max(nextId, id + 1);
        return id;
    }

    /** If realKey has no id yet but legacyKey does, moves that binding to the
     * real key (used to adopt computers created while the world was
     * unresolvable and the key fell back to the literal "world" prefix). */
    public synchronized void adopt(String legacyKey, String realKey) {
        if (legacyKey.equals(realKey)) {
            return;
        }
        Integer legacy = byBlock.get(legacyKey);
        if (legacy == null) {
            return;
        }
        Integer real = byBlock.get(realKey);
        if (real != null && !real.equals(legacy)) {
            throw new IllegalStateException("cannot adopt computer #" + legacy + " at " + realKey
                    + ": position already belongs to computer #" + real);
        }
        byBlock.remove(legacyKey);
        if (real == null) {
            byBlock.put(realKey, legacy);
        }
        byId.put(legacy, realKey);
    }

    public synchronized int move(String oldKey, String newKey) {
        Integer id = byBlock.get(oldKey);
        if (id == null) {
            throw new IllegalStateException("computer id not found for " + oldKey);
        }
        if (oldKey.equals(newKey)) {
            return id;
        }
        Integer destination = byBlock.get(newKey);
        if (destination != null) {
            throw new IllegalStateException("cannot move computer #" + id + " to " + newKey
                    + ": position already belongs to computer #" + destination);
        }
        byBlock.remove(oldKey);
        byBlock.put(newKey, id);
        byId.put(id, newKey);
        return id;
    }

    public synchronized Integer unbind(String blockKey) {
        Integer id = byBlock.remove(blockKey);
        if (id != null) {
            byId.remove(id, blockKey);
        }
        return id;
    }

    public synchronized void load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        int loadedNextId = extractInt(json, "next", 0);
        Map<String, Integer> loadedBlocks = new ConcurrentHashMap<>();
        Map<Integer, String> loadedIds = new ConcurrentHashMap<>();
        for (String entry : extractObjectEntries(json, "blocks")) {
            int sep = entry.lastIndexOf("\":");
            if (sep < 0) {
                continue;
            }
            String key = unquote(entry.substring(1, sep));
            int id = extractInt(entry.substring(sep + 2), "", -1);
            if (id < 0) {
                continue;
            }
            Integer previous = loadedBlocks.putIfAbsent(key, id);
            String duplicate = loadedIds.putIfAbsent(id, key);
            if (duplicate != null && key.startsWith("world:") && !duplicate.startsWith("world:")) {
                loadedBlocks.remove(key, id);
                loadedIds.put(id, duplicate);
                duplicate = null;
            } else if (duplicate != null && !key.startsWith("world:") && duplicate.startsWith("world:")) {
                loadedBlocks.remove(duplicate);
                loadedIds.put(id, key);
                duplicate = null;
            }
            if (previous != null || duplicate != null) {
                throw new IOException(previous != null
                        ? "position " + key + " has multiple computer ids"
                        : "computer #" + id + " is mapped to both " + duplicate + " and " + key);
            }
            loadedNextId = Math.max(loadedNextId, id + 1);
        }
        byBlock.clear();
        byId.clear();
        byBlock.putAll(loadedBlocks);
        byId.putAll(loadedIds);
        nextId = loadedNextId;
    }

    public synchronized void save() throws IOException {
        StringBuilder sb = new StringBuilder("{\"next\":").append(nextId).append(",\"blocks\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : byBlock.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(quote(e.getKey())).append("\":").append(e.getValue());
        }
        sb.append("}}");
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".new");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static int extractInt(String json, String key, int fallback) {
        String needle = key.isEmpty() ? "" : "\"" + key + "\":";
        int i = needle.isEmpty() ? 0 : json.indexOf(needle);
        if (i < 0) {
            return fallback;
        }
        i += needle.length();
        StringBuilder num = new StringBuilder();
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) {
            num.append(json.charAt(i++));
        }
        try {
            return Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Entries of the form "key":value inside the named top-level object. */
    private static java.util.List<String> extractObjectEntries(String json, String name) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String needle = "\"" + name + "\":{";
        int start = json.indexOf(needle);
        if (start < 0) {
            return out;
        }
        int i = start + needle.length();
        int depth = 1;
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        for (; i < json.length() && depth > 0; i++) {
            char c = json.charAt(i);
            if (inString) {
                cur.append(c);
                if (c == '"' && json.charAt(i - 1) != '\\') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
                cur.append(c);
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (--depth > 0) {
                    cur.append(c);
                }
            } else if (c == ',' && depth == 1) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    private static String quote(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unquote(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}

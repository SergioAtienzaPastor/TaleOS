package me.kvothe.talecraft.computer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent store of computer ids. Lives in {@code mods/TaleCraft/ids.json}
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

    public ComputerIdStore(Path file) {
        this.file = file;
    }

    /** Gets the id for a block position, allocating a fresh one if unknown. */
    public synchronized int idFor(String blockKey) {
        return byBlock.computeIfAbsent(blockKey, k -> nextId++);
    }

    public synchronized void load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        nextId = extractInt(json, "next", 0);
        byBlock.clear();
        for (String entry : extractObjectEntries(json, "blocks")) {
            int sep = entry.lastIndexOf("\":");
            if (sep < 0) {
                continue;
            }
            String key = unquote(entry.substring(1, sep));
            int id = extractInt(entry.substring(sep + 1), "", -1);
            if (id >= 0) {
                byBlock.put(key, id);
                nextId = Math.max(nextId, id + 1);
            }
        }
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

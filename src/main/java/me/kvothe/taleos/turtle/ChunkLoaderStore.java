package me.kvothe.taleos.turtle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChunkLoaderStore {
    public record Location(String world, int x, int y, int z) {
        public Location {
            if (world == null || world.isBlank()) {
                throw new IllegalArgumentException("chunk loader world cannot be empty");
            }
        }
    }

    private static final Pattern ENTRY = Pattern.compile(
            "\\\"(-?\\d+)\\\":\\{\\\"world\\\":\\\"((?:\\\\.|[^\\\"])*)\\\",\\\"x\\\":(-?\\d+),\\\"y\\\":(-?\\d+),\\\"z\\\":(-?\\d+)\\}");

    private final Path file;
    private final Map<Integer, Location> locations = new LinkedHashMap<>();

    public ChunkLoaderStore(Path file) {
        this.file = file;
    }

    public synchronized Location get(int computerId) {
        return locations.get(computerId);
    }

    public synchronized Map<Integer, Location> entries() {
        return Map.copyOf(locations);
    }

    public synchronized void put(int computerId, Location location) {
        locations.put(computerId, location);
    }

    public synchronized Location remove(int computerId) {
        return locations.remove(computerId);
    }

    public synchronized void load() throws IOException {
        locations.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        Matcher matcher = ENTRY.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            Location location = new Location(unquote(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)),
                    Integer.parseInt(matcher.group(5)));
            if (id < 0 || locations.putIfAbsent(id, location) != null) {
                throw new IOException("invalid or duplicate Turtle chunk loader id: " + id);
            }
        }
    }

    public synchronized void save() throws IOException {
        StringBuilder json = new StringBuilder("{\"loaders\":{");
        boolean first = true;
        for (Map.Entry<Integer, Location> entry : locations.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            Location location = entry.getValue();
            json.append('"').append(entry.getKey()).append("\":{\"world\":\"")
                    .append(quote(location.world())).append("\",\"x\":").append(location.x())
                    .append(",\"y\":").append(location.y()).append(",\"z\":")
                    .append(location.z()).append('}');
        }
        json.append("}}");
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".new");
        Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String quote(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unquote(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                result.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                result.append(c);
            }
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }
}

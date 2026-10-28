package me.kvothe.talecraft.turtle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TurtleStateStore {

    private static final Pattern SLOT = Pattern.compile("\\\"(\\d+)\\\"\\s*:\\s*(\\d+)");

    private final Path file;
    private final Map<Integer, Integer> selectedByComputer = new ConcurrentHashMap<>();

    public TurtleStateStore(Path file) {
        this.file = file;
    }

    public int getSelectedSlot(int computerId) {
        return selectedByComputer.getOrDefault(computerId, 0);
    }

    public void setSelectedSlot(int computerId, int slot) {
        if (slot < 0 || slot >= TurtleController.INVENTORY_SLOTS) {
            throw new IllegalArgumentException("slot outside turtle inventory: " + slot);
        }
        selectedByComputer.put(computerId, slot);
    }

    public void load() throws IOException {
        selectedByComputer.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        Matcher matcher = SLOT.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            int slot = Integer.parseInt(matcher.group(2));
            if (slot >= 0 && slot < TurtleController.INVENTORY_SLOTS) {
                selectedByComputer.put(id, slot);
            }
        }
    }

    public synchronized void save() throws IOException {
        StringBuilder json = new StringBuilder("{\"slots\":{");
        boolean first = true;
        for (Map.Entry<Integer, Integer> entry : selectedByComputer.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        json.append("}}");
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".new");
        Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}

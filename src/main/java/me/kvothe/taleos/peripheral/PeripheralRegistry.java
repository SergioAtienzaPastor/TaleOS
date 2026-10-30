package me.kvothe.taleos.peripheral;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PeripheralRegistry {
    public record Change(boolean attached, String name) {
    }

    private PeripheralProvider provider;
    private final Map<String, PeripheralHandle> handles = new LinkedHashMap<>();
    private Map<String, String> attached = Map.of();
    private boolean initialized;
    private boolean invalidated;

    public PeripheralRegistry(PeripheralProvider provider) {
        this.provider = provider == null ? PeripheralProvider.NONE : provider;
    }

    public List<Change> refresh() {
        Map<String, Peripheral> snapshot = provider.snapshot();
        Map<String, String> next = new LinkedHashMap<>();
        snapshot.forEach((name, peripheral) -> next.put(name, peripheral.getType()));
        if (!initialized) {
            initialized = true;
            invalidated = false;
            attached = next;
            return List.of();
        }
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<String, String> entry : attached.entrySet()) {
            String name = entry.getKey();
            if (invalidated || !entry.getValue().equals(next.get(name))) {
                changes.add(new Change(false, name));
            }
        }
        for (Map.Entry<String, String> entry : next.entrySet()) {
            String name = entry.getKey();
            if (invalidated || !entry.getValue().equals(attached.get(name))) {
                changes.add(new Change(true, name));
            }
        }
        attached = next;
        invalidated = false;
        return changes;
    }

    public void invalidate() {
        invalidated = true;
    }

    public void setProvider(PeripheralProvider provider) {
        this.provider = provider == null ? PeripheralProvider.NONE : provider;
    }

    public PeripheralHandle handle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return handles.computeIfAbsent(name, key -> new PeripheralHandle(this, key));
    }

    Peripheral resolve(String name) {
        return provider.find(name);
    }
}

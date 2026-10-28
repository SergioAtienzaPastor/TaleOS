package me.kvothe.talecraft.peripheral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

class PeripheralRegistryTest {
    private static final class Provider implements PeripheralProvider {
        private final Map<String, Peripheral> values = new LinkedHashMap<>();

        @Override
        public Map<String, Peripheral> snapshot() {
            return new LinkedHashMap<>(values);
        }

        @Override
        public Peripheral find(String name) {
            return values.get(name);
        }
    }

    private static Peripheral peripheral(String type) {
        return new Peripheral() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public List<String> getMethods() {
                return List.of("ping");
            }

            @Override
            public Varargs call(String method, Varargs args) {
                return LuaValue.TRUE;
            }
        };
    }

    @Test
    void handlesResolveOnEveryCallAndSurviveReattachment() {
        Provider provider = new Provider();
        provider.values.put("front", peripheral("one"));
        PeripheralRegistry registry = new PeripheralRegistry(provider);
        registry.refresh();
        PeripheralHandle handle = registry.handle("front");

        assertSame(handle, registry.handle("front"));
        assertEquals("one", handle.getType());
        provider.values.put("front", peripheral("two"));
        assertEquals("two", handle.getType());
        provider.values.clear();
        assertFalse(handle.isPresent());
        provider.values.put("front", peripheral("three"));
        assertTrue(handle.isPresent());
        assertEquals("three", handle.getType());
    }

    @Test
    void replacementAndInvalidationProduceDetachAttachPairs() {
        Provider provider = new Provider();
        provider.values.put("front", peripheral("one"));
        PeripheralRegistry registry = new PeripheralRegistry(provider);
        assertTrue(registry.refresh().isEmpty());

        provider.values.put("front", peripheral("two"));
        List<PeripheralRegistry.Change> changes = registry.refresh();
        assertEquals(2, changes.size());
        assertFalse(changes.get(0).attached());
        assertTrue(changes.get(1).attached());

        registry.invalidate();
        assertEquals(2, registry.refresh().size());
        assertTrue(registry.refresh().isEmpty());
    }
}

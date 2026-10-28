package me.kvothe.talecraft.peripheral;

import java.util.Map;

public interface PeripheralProvider {
    Map<String, Peripheral> snapshot();

    Peripheral find(String name);

    PeripheralProvider NONE = new PeripheralProvider() {
        @Override
        public Map<String, Peripheral> snapshot() {
            return Map.of();
        }

        @Override
        public Peripheral find(String name) {
            return null;
        }
    };
}

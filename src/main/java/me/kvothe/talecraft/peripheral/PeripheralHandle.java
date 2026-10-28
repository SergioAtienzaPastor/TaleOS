package me.kvothe.talecraft.peripheral;

import java.util.List;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.Varargs;

public final class PeripheralHandle {
    private final PeripheralRegistry registry;
    private final String name;

    PeripheralHandle(PeripheralRegistry registry, String name) {
        this.registry = registry;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isPresent() {
        return resolve() != null;
    }

    public String getType() {
        Peripheral peripheral = resolve();
        return peripheral == null ? null : peripheral.getType();
    }

    public List<String> getMethods() {
        Peripheral peripheral = resolve();
        return peripheral == null ? null : peripheral.getMethods();
    }

    public Varargs call(String method, Varargs args) {
        Peripheral peripheral = resolve();
        if (peripheral == null) {
            throw new LuaError("Peripheral " + name + " is detached");
        }
        if (!peripheral.supportsMethod(method)) {
            throw new LuaError("No such method " + method + " on " + peripheral.getType());
        }
        try {
            return peripheral.call(method, args);
        } catch (LuaError error) {
            throw new LuaError(me.kvothe.talecraft.I18n.t(error.getMessage()));
        }
    }

    public Peripheral resolve() {
        return registry.resolve(name);
    }
}

package me.kvothe.taleos.peripheral;

import java.util.List;

import org.luaj.vm2.Varargs;

public interface Peripheral {
    String getType();

    List<String> getMethods();

    default boolean supportsMethod(String method) {
        return getMethods().contains(method);
    }

    Varargs call(String method, Varargs args);
}

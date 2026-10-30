package me.kvothe.taleos.peripheral.modem;

import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

final class ModemPayload {
    static final int MAX_DEPTH = 16;
    static final int MAX_NODES = 1024;
    static final int MAX_STRING_BYTES = 32_768;

    private ModemPayload() {
    }

    static Object toWire(LuaValue value) {
        Budget budget = new Budget();
        return write(value, 0, new IdentityHashMap<>(), budget);
    }

    static LuaValue fromWire(Object value) {
        if (value == LuaValue.NIL) {
            return LuaValue.NIL;
        }
        if (value instanceof String string) {
            return LuaValue.valueOf(string);
        }
        if (value instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }
        if (value instanceof Double number) {
            return LuaValue.valueOf(number);
        }
        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                table.set(fromWire(entry.getKey()), fromWire(entry.getValue()));
            }
            return table;
        }
        throw new IllegalArgumentException("invalid wireless payload");
    }

    private static Object write(LuaValue value, int depth, IdentityHashMap<LuaTable, Boolean> active,
            Budget budget) {
        if (++budget.nodes > MAX_NODES) {
            throw new LuaError("modem payload has too many values");
        }
        if (value == null || value.isnil()) {
            return LuaValue.NIL;
        }
        if (value.isboolean()) {
            return value.toboolean();
        }
        if (value.isnumber()) {
            double number = value.todouble();
            if (!Double.isFinite(number)) {
                throw new LuaError("modem payload numbers must be finite");
            }
            return number;
        }
        if (value.isstring()) {
            String string = value.tojstring();
            budget.stringBytes += string.getBytes(StandardCharsets.UTF_8).length;
            if (budget.stringBytes > MAX_STRING_BYTES) {
                throw new LuaError("modem payload strings are too large");
            }
            return string;
        }
        if (!value.istable()) {
            throw new LuaError("unsupported modem payload type: " + value.typename());
        }
        if (depth >= MAX_DEPTH) {
            throw new LuaError("modem payload is too deep");
        }
        LuaTable source = value.checktable();
        if (active.put(source, Boolean.TRUE) != null) {
            throw new LuaError("modem payload contains a cycle");
        }
        try {
            Map<Object, Object> copy = new LinkedHashMap<>();
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = source.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                Object copiedKey = write(key, depth + 1, active, budget);
                Object copiedValue = write(next.arg(2), depth + 1, active, budget);
                copy.put(copiedKey, copiedValue);
            }
            return copy;
        } finally {
            active.remove(source);
        }
    }

    private static final class Budget {
        private int nodes;
        private int stringBytes;
    }
}

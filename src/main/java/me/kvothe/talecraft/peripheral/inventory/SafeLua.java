package me.kvothe.talecraft.peripheral.inventory;

import java.util.Map;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class SafeLua {
    private SafeLua() {
    }

    public static LuaTable table(Map<String, Object> values) {
        LuaTable table = new LuaTable();
        values.forEach((key, value) -> {
            LuaValue converted = value(value);
            if (key != null && !converted.isnil()) {
                table.set(key, converted);
            }
        });
        return table;
    }

    public static LuaValue value(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof String string) {
            return LuaValue.valueOf(string);
        }
        if (value instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }
        if (value instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            map.forEach((key, entry) -> {
                if (key instanceof String string) {
                    LuaValue converted = value(entry);
                    if (!converted.isnil()) {
                        table.set(string, converted);
                    }
                }
            });
            return table;
        }
        if (value instanceof Iterable<?> iterable) {
            LuaTable table = new LuaTable();
            int index = 1;
            for (Object entry : iterable) {
                LuaValue converted = value(entry);
                if (!converted.isnil()) {
                    table.set(index++, converted);
                }
            }
            return table;
        }
        return LuaValue.NIL;
    }
}

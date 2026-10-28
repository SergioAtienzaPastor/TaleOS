package me.kvothe.talecraft.peripheral.inventory;

import java.util.Map;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public record ItemStackSnapshot(String name, int count, int maxCount, Double durability,
        Double maxDurability, Map<String, Object> metadata, Object nativeStack) {

    public LuaTable toLua(boolean detailed) {
        LuaTable table = new LuaTable();
        table.set("name", LuaValue.valueOf(name));
        table.set("count", LuaValue.valueOf(count));
        if (detailed) {
            table.set("maxCount", LuaValue.valueOf(maxCount));
            if (durability != null) {
                table.set("durability", LuaValue.valueOf(durability));
            }
            if (maxDurability != null) {
                table.set("maxDurability", LuaValue.valueOf(maxDurability));
            }
            if (metadata != null && !metadata.isEmpty()) {
                table.set("metadata", SafeLua.table(metadata));
            }
        }
        return table;
    }
}

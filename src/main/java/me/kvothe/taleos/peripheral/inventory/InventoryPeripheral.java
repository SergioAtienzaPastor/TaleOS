package me.kvothe.taleos.peripheral.inventory;

import java.util.List;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import me.kvothe.taleos.peripheral.Peripheral;

public final class InventoryPeripheral implements Peripheral {
    public static final String TYPE = "inventory";
    private static final List<String> METHODS = List.of(
            "size", "list", "getItemDetail", "getItemLimit");

    private final InventoryAdapter inventory;

    public InventoryPeripheral(InventoryAdapter inventory) {
        this.inventory = inventory;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public List<String> getMethods() {
        return METHODS;
    }

    @Override
    public Varargs call(String method, Varargs args) {
        return switch (method) {
            case "size" -> LuaValue.valueOf(inventory.size());
            case "list" -> list();
            case "getItemDetail" -> detail(slot(args));
            case "getItemLimit" -> LuaValue.valueOf(inventory.getItemLimit(slot(args)));
            default -> throw new LuaError("Unknown inventory method " + method);
        };
    }

    private int slot(Varargs args) {
        int slot = args.arg1().checkint() - 1;
        if (slot < 0 || slot >= inventory.size()) {
            throw new LuaError("Slot out of range (1-" + inventory.size() + ")");
        }
        return slot;
    }

    private LuaTable list() {
        LuaTable result = new LuaTable();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStackSnapshot stack = inventory.getStack(slot);
            if (stack != null && stack.count() > 0) {
                result.set(slot + 1, stack.toLua(false));
            }
        }
        return result;
    }

    private Varargs detail(int slot) {
        ItemStackSnapshot stack = inventory.getStack(slot);
        return stack == null || stack.count() <= 0 ? LuaValue.NIL : stack.toLua(true);
    }
}

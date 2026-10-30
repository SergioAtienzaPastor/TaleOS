package me.kvothe.taleos.turtle;

import java.util.HashMap;
import java.util.Map;

import com.hypixel.hytale.server.core.inventory.ItemStack;

public final class TurtleUpgradeRegistry {
    public enum Kind {
        TOOL,
        MODEM,
        SPEAKER,
        CRAFTING
    }

    public static final String MODEM_ID = "TaleOS_Modem";
    public static final String SPEAKER_ID = "TaleOS_Speaker";
    public static final String CRAFTING_ID = "TaleOS_Crafting_Upgrade";

    private static final TurtleUpgradeRegistry DEFAULT = createDefault();

    private final Map<String, Kind> upgrades = new HashMap<>();

    public static TurtleUpgradeRegistry createDefault() {
        TurtleUpgradeRegistry registry = new TurtleUpgradeRegistry();
        registry.register(MODEM_ID, Kind.MODEM);
        registry.register(SPEAKER_ID, Kind.SPEAKER);
        registry.register(CRAFTING_ID, Kind.CRAFTING);
        return registry;
    }

    public static TurtleUpgradeRegistry defaults() {
        return DEFAULT;
    }

    public void register(String itemId, Kind kind) {
        if (itemId == null || itemId.isBlank() || kind == null) {
            throw new IllegalArgumentException("an upgrade needs an item id and a kind");
        }
        upgrades.put(itemId, kind);
    }

    public Kind kindOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        var item = stack.getItem();
        return kindOf(stack.getItemId(), item != null && item.getTool() != null,
                item != null && item.getWeapon() != null);
    }

    public Kind kindOf(String itemId, boolean tool) {
        return kindOf(itemId, tool, false);
    }

    public Kind kindOf(String itemId, boolean tool, boolean weapon) {
        Kind registered = upgrades.get(itemId);
        return registered != null ? registered : tool || weapon ? Kind.TOOL : null;
    }

    public boolean accepts(ItemStack stack) {
        return kindOf(stack) != null;
    }

    public boolean accepts(String itemId, boolean tool) {
        return kindOf(itemId, tool) != null;
    }
}

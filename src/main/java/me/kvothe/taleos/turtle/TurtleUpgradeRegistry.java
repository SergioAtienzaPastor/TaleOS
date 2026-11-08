package me.kvothe.taleos.turtle;

import java.util.HashMap;
import java.util.Map;

import com.hypixel.hytale.server.core.inventory.ItemStack;

public final class TurtleUpgradeRegistry {
    public enum Kind {
        TOOL,
        MODEM,
        SPEAKER,
        CRAFTING,
        CHUNK_LOADER,
        PLAYER_LINK
    }

    public static final String MODEM_ID = "TaleOS_Modem";
    public static final String SPEAKER_ID = "TaleOS_Speaker";
    public static final String CRAFTING_ID = "TaleOS_Crafting_Upgrade";
    public static final String CHUNK_LOADER_ID = "TaleOS_Chunk_Loader_Upgrade";
    public static final String PLAYER_LINK_ID = "TaleOS_Player_Link";

    private static final TurtleUpgradeRegistry DEFAULT = createDefault();

    private final Map<String, Kind> upgrades = new HashMap<>();

    public static TurtleUpgradeRegistry createDefault() {
        TurtleUpgradeRegistry registry = new TurtleUpgradeRegistry();
        registry.register(MODEM_ID, Kind.MODEM);
        registry.register(SPEAKER_ID, Kind.SPEAKER);
        registry.register(CRAFTING_ID, Kind.CRAFTING);
        registry.register(CHUNK_LOADER_ID, Kind.CHUNK_LOADER);
        registry.register(PLAYER_LINK_ID, Kind.PLAYER_LINK);
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

    public boolean acceptsInternal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        Kind kind = kindOf(stack);
        return kind != null && kind != Kind.TOOL;
    }

    public boolean accepts(String itemId, boolean tool) {
        return kindOf(itemId, tool) != null;
    }

    public boolean acceptsInternal(String itemId, boolean tool) {
        Kind kind = kindOf(itemId, tool);
        return kind != null && kind != Kind.TOOL;
    }
}

package me.kvothe.taleos.peripheral.playerlink;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;

public final class PlayerLinkItemMetadata {
    public static final String ITEM_ID = "TaleOS_Player_Link";
    public static final int MAX_PLAYER_SLOT = 64;
    public static final int TURTLE_SLOTS = 16;

    private static final String CONFIG_KEY = "TaleOSPlayerLink";
    private static final String OWNER_KEY = "owner";
    private static final String OWNER_NAME_KEY = "ownerName";
    private static final String ROUTES_KEY = "routes";
    private static final String DEFAULT_SLOT_KEY = "defaultSlot";
    private static final String AUTO_KEY = "autoTransfer";

    private PlayerLinkItemMetadata() {
    }

    public record Configuration(UUID ownerId, String ownerName, Map<Integer, Integer> routes,
            Integer defaultSlot, boolean autoTransfer) {
        public Configuration {
            if (ownerName != null && ownerName.isBlank()) {
                throw new IllegalArgumentException("Player Link owner name cannot be blank");
            }
            routes = routes == null ? Map.of()
                    : Collections.unmodifiableMap(new TreeMap<>(routes));
            routes.forEach((turtleSlot, playerSlot) -> {
                validateTurtleSlot(turtleSlot);
                validatePlayerSlot(playerSlot);
            });
            if (defaultSlot != null) {
                validatePlayerSlot(defaultSlot);
            }
            if (ownerId == null && (!routes.isEmpty() || defaultSlot != null || autoTransfer)) {
                throw new IllegalArgumentException("An unlinked Player Link cannot have transfer settings");
            }
        }

        public static Configuration unlinked() {
            return new Configuration(null, null, Map.of(), null, false);
        }

        public Configuration withRoute(int turtleSlot, int playerSlot) {
            validateTurtleSlot(turtleSlot);
            validatePlayerSlot(playerSlot);
            Map<Integer, Integer> updated = new TreeMap<>(routes);
            updated.put(turtleSlot, playerSlot);
            return new Configuration(ownerId, ownerName, updated, defaultSlot, autoTransfer);
        }

        public Configuration withoutRoute(int turtleSlot) {
            validateTurtleSlot(turtleSlot);
            Map<Integer, Integer> updated = new TreeMap<>(routes);
            updated.remove(turtleSlot);
            return new Configuration(ownerId, ownerName, updated, defaultSlot, autoTransfer);
        }

        public Configuration withDefaultSlot(Integer playerSlot) {
            return new Configuration(ownerId, ownerName, routes, playerSlot, autoTransfer);
        }

        public Configuration withAutoTransfer(boolean enabled) {
            return new Configuration(ownerId, ownerName, routes, defaultSlot, enabled);
        }
    }

    public static boolean isPlayerLink(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ITEM_ID.equals(stack.getItemId())
                && stack.getQuantity() == 1;
    }

    public static Configuration configuration(ItemStack stack) {
        if (!isPlayerLink(stack)) {
            return null;
        }
        BsonDocument metadata = stack.getMetadata();
        BsonValue value = metadata == null ? null : metadata.get(CONFIG_KEY);
        if (value == null) {
            return Configuration.unlinked();
        }
        if (!value.isDocument()) {
            throw new IllegalArgumentException(CONFIG_KEY + " must be a document");
        }
        BsonDocument document = value.asDocument();
        if (!document.containsKey(OWNER_KEY)) {
            return Configuration.unlinked();
        }
        UUID owner = uuid(document.get(OWNER_KEY));
        String ownerName = document.get(OWNER_NAME_KEY) instanceof BsonString name
                ? name.getValue() : owner.toString();
        Map<Integer, Integer> routes = new TreeMap<>();
        if (document.get(ROUTES_KEY) instanceof BsonDocument routeDocument) {
            for (Map.Entry<String, BsonValue> entry : routeDocument.entrySet()) {
                if (!(entry.getValue() instanceof BsonInt32 playerSlot)) {
                    throw new IllegalArgumentException("Player Link route destinations must be integers");
                }
                try {
                    routes.put(Integer.parseInt(entry.getKey()), playerSlot.getValue());
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Player Link route sources must be integers", exception);
                }
            }
        } else if (document.containsKey(ROUTES_KEY)) {
            throw new IllegalArgumentException("Player Link routes must be a document");
        }
        int defaultSlot = document.get(DEFAULT_SLOT_KEY) instanceof BsonInt32 slot
                ? slot.getValue() : -1;
        boolean automatic = document.get(AUTO_KEY) instanceof BsonBoolean enabled
                && enabled.getValue();
        return new Configuration(owner, ownerName, routes,
                defaultSlot < 0 ? null : defaultSlot, automatic);
    }

    public static ItemStack bind(ItemStack stack, UUID owner, String ownerName) {
        if (!isPlayerLink(stack) || owner == null || ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("Player Link binding requires the item and a player");
        }
        Configuration current = configuration(stack);
        if (current.ownerId() != null) {
            throw new IllegalArgumentException("Player Link is already linked");
        }
        Configuration bound = new Configuration(owner, ownerName, current.routes(),
                current.defaultSlot(), current.autoTransfer());
        return write(stack, bound);
    }

    public static ItemStack configure(ItemStack stack, Configuration configuration) {
        if (!isPlayerLink(stack) || configuration == null || configuration.ownerId() == null) {
            throw new IllegalArgumentException("Player Link must be linked before configuring it");
        }
        return write(stack, configuration);
    }

    public static ItemStack unbind(ItemStack stack) {
        if (!isPlayerLink(stack)) {
            throw new IllegalArgumentException("Not a Player Link");
        }
        BsonDocument metadata = stack.getMetadata() == null ? new BsonDocument()
                : stack.getMetadata().clone();
        metadata.remove(CONFIG_KEY);
        return stack.withMetadata(metadata).withMetadata(ItemDisplayMetadata.KEYED_CODEC,
                new ItemDisplayMetadata(Message.raw("Player Link"), null));
    }

    private static ItemStack write(ItemStack stack, Configuration configuration) {
        String ownerName = configuration.ownerName() == null
                ? configuration.ownerId().toString() : configuration.ownerName();
        BsonDocument document = new BsonDocument()
                .append(OWNER_KEY, new BsonString(configuration.ownerId().toString()))
                .append(OWNER_NAME_KEY, new BsonString(ownerName))
                .append(DEFAULT_SLOT_KEY, new BsonInt32(
                        configuration.defaultSlot() == null ? -1 : configuration.defaultSlot()))
                .append(AUTO_KEY, new BsonBoolean(configuration.autoTransfer()));
        BsonDocument routes = new BsonDocument();
        configuration.routes().forEach((turtleSlot, playerSlot) ->
                routes.put(Integer.toString(turtleSlot), new BsonInt32(playerSlot)));
        document.put(ROUTES_KEY, routes);
        return stack.withMetadata(CONFIG_KEY, document)
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, new ItemDisplayMetadata(
                        Message.raw("Player Link — " + ownerName), null));
    }

    private static UUID uuid(BsonValue value) {
        if (!(value instanceof BsonString string)) {
            throw new IllegalArgumentException("Player Link owner must be a UUID string");
        }
        try {
            return UUID.fromString(string.getValue());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Player Link owner must be a valid UUID", exception);
        }
    }

    private static void validateTurtleSlot(int slot) {
        if (slot < 1 || slot > TURTLE_SLOTS) {
            throw new IllegalArgumentException("Turtle slot must be between 1 and 16");
        }
    }

    private static void validatePlayerSlot(int slot) {
        if (slot < 1 || slot > MAX_PLAYER_SLOT) {
            throw new IllegalArgumentException("Player slot must be between 1 and 64");
        }
    }
}

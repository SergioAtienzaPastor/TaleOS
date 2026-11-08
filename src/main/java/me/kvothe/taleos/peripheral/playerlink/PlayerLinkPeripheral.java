package me.kvothe.taleos.peripheral.playerlink;

import java.util.List;

import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import me.kvothe.taleos.peripheral.Peripheral;
import me.kvothe.taleos.peripheral.playerlink.PlayerLinkItemMetadata.Configuration;

public final class PlayerLinkPeripheral implements Peripheral {
    public interface Backend {
        Configuration configuration();

        boolean saveConfiguration(Configuration configuration);

        String unavailableReason();

        PlayerLinkTransfer.Result transfer(int turtleSlot, int playerSlot, int count);

        SlotResult playerSlot(int playerSlot);
    }

    public record SlotResult(String name, int count, int maxCount, String reason) {
        public static SlotResult success(String name, int count, int maxCount) {
            return new SlotResult(name, count, maxCount, null);
        }

        public static SlotResult failure(String reason) {
            return new SlotResult(null, 0, 0, reason);
        }
    }

    private static final List<String> METHODS = List.of("getOwner", "isAvailable", "setRoute",
            "clearRoute", "getRoutes", "setDefaultSlot", "setAutoTransfer", "transfer",
            "getPlayerSlot");

    private final Backend backend;

    public PlayerLinkPeripheral(Backend backend) {
        this.backend = backend;
    }

    @Override
    public String getType() {
        return "player_link";
    }

    @Override
    public List<String> getMethods() {
        return METHODS;
    }

    @Override
    public Varargs call(String method, Varargs args) {
        try {
            return switch (method) {
                case "getOwner" -> owner();
                case "isAvailable" -> available();
                case "setRoute" -> setRoute(args.arg1(), args.arg(2));
                case "clearRoute" -> clearRoute(args.arg1());
                case "getRoutes" -> routes();
                case "setDefaultSlot" -> setDefaultSlot(args.arg1());
                case "setAutoTransfer" -> setAutoTransfer(args.arg1());
                case "transfer" -> transfer(args.arg1(), args.arg(2), args.arg(3));
                case "getPlayerSlot" -> playerSlot(args.arg1());
                default -> LuaValue.NIL;
            };
        } catch (RuntimeException exception) {
            return failure("Player Link operation failed: " + exception.getMessage());
        }
    }

    private Varargs owner() {
        Configuration configuration = backend.configuration();
        return configuration == null || configuration.ownerId() == null ? LuaValue.NIL
                : LuaValue.valueOf(configuration.ownerName());
    }

    private Varargs available() {
        String reason = backend.unavailableReason();
        return reason == null ? LuaValue.TRUE : failure(reason);
    }

    private Varargs setRoute(LuaValue turtleSlot, LuaValue playerSlot) {
        int source = integer(turtleSlot);
        int destination = integer(playerSlot);
        if (!validTurtleSlot(source) || !validPlayerSlot(destination)) {
            return failure("Usage: setRoute(turtleSlot 1-16, playerSlot 1-64)");
        }
        Configuration configuration = linkedConfiguration();
        return configuration == null ? failure("Hold the Player Link and press F to link it first")
                : save(configuration.withRoute(source, destination));
    }

    private Varargs clearRoute(LuaValue turtleSlot) {
        int source = integer(turtleSlot);
        if (!validTurtleSlot(source)) {
            return failure("Turtle slot must be between 1 and 16");
        }
        Configuration configuration = linkedConfiguration();
        return configuration == null ? failure("Hold the Player Link and press F to link it first")
                : save(configuration.withoutRoute(source));
    }

    private Varargs routes() {
        Configuration configuration = backend.configuration();
        if (configuration == null) {
            return LuaValue.NIL;
        }
        LuaTable result = new LuaTable();
        configuration.routes().forEach((turtleSlot, playerSlot) ->
                result.set(turtleSlot.intValue(), LuaValue.valueOf(playerSlot.intValue())));
        return result;
    }

    private Varargs setDefaultSlot(LuaValue playerSlot) {
        Integer destination = playerSlot.isnil() ? null : integer(playerSlot);
        if (destination != null && !validPlayerSlot(destination)) {
            return failure("Player slot must be between 1 and 64, or nil to clear");
        }
        Configuration configuration = linkedConfiguration();
        return configuration == null ? failure("Hold the Player Link and press F to link it first")
                : save(configuration.withDefaultSlot(destination));
    }

    private Varargs setAutoTransfer(LuaValue enabled) {
        if (!enabled.isboolean()) {
            return failure("Automatic transfer must be true or false");
        }
        Configuration configuration = linkedConfiguration();
        return configuration == null ? failure("Hold the Player Link and press F to link it first")
                : save(configuration.withAutoTransfer(enabled.toboolean()));
    }

    private Varargs transfer(LuaValue turtleSlot, LuaValue playerSlot, LuaValue count) {
        int source = integer(turtleSlot);
        int destination = integer(playerSlot);
        int amount = count.isnil() ? Integer.MAX_VALUE : integer(count);
        if (!validTurtleSlot(source) || !validPlayerSlot(destination) || amount <= 0) {
            return failure("Usage: transfer(turtleSlot 1-16, playerSlot 1-64[, count > 0])");
        }
        PlayerLinkTransfer.Result result = backend.transfer(source, destination, amount);
        return result.success() ? LuaValue.varargsOf(LuaValue.TRUE, LuaValue.valueOf(result.moved()))
                : failure(result.reason());
    }

    private Varargs playerSlot(LuaValue playerSlot) {
        int slot = integer(playerSlot);
        if (!validPlayerSlot(slot)) {
            return LuaValue.varargsOf(LuaValue.NIL,
                    LuaValue.valueOf(me.kvothe.taleos.I18n.t("Player slot must be between 1 and 64")));
        }
        SlotResult result = backend.playerSlot(slot);
        if (result.reason() != null) {
            return LuaValue.varargsOf(LuaValue.NIL,
                    LuaValue.valueOf(me.kvothe.taleos.I18n.t(result.reason())));
        }
        LuaTable detail = new LuaTable();
        if (result.name() != null) {
            detail.set("name", result.name());
        }
        detail.set("count", result.count());
        detail.set("maxCount", result.maxCount());
        return detail;
    }

    private Configuration linkedConfiguration() {
        Configuration configuration = backend.configuration();
        return configuration == null || configuration.ownerId() == null ? null : configuration;
    }

    private Varargs save(Configuration configuration) {
        return backend.saveConfiguration(configuration) ? LuaValue.TRUE
                : failure("Could not save the Player Link configuration");
    }

    private static int integer(LuaValue value) {
        if (!(value instanceof LuaNumber)) {
            return -1;
        }
        double number = value.todouble();
        return Double.isFinite(number) && number == Math.rint(number)
                && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE
                ? value.toint() : -1;
    }

    private static boolean validTurtleSlot(int slot) {
        return slot >= 1 && slot <= PlayerLinkItemMetadata.TURTLE_SLOTS;
    }

    private static boolean validPlayerSlot(int slot) {
        return slot >= 1 && slot <= PlayerLinkItemMetadata.MAX_PLAYER_SLOT;
    }

    private static Varargs failure(String reason) {
        return LuaValue.varargsOf(LuaValue.FALSE,
                LuaValue.valueOf(me.kvothe.taleos.I18n.t(reason)));
    }
}

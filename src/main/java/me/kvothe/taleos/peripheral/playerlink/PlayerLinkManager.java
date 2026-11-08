package me.kvothe.taleos.peripheral.playerlink;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.taleos.computer.Computer;
import me.kvothe.taleos.computer.ComputerManager;
import me.kvothe.taleos.net.PlayerLinkConfig;
import me.kvothe.taleos.peripheral.playerlink.PlayerLinkItemMetadata.Configuration;
import me.kvothe.taleos.turtle.TurtleController;

public final class PlayerLinkManager {
    private static final long EVENT_INTERVAL_NANOS = 1_000_000_000L;

    private record LinkKey(String world, int computerId, int upgradeSlot) {
    }

    private record Resolution(Player player, String reason) {
    }

    private record SlotTarget(ItemContainer container, short slot) {
    }

    private final ComputerManager computers;
    private final Map<LinkKey, Integer> transferBudget = new HashMap<>();
    private final Map<String, Long> eventTimes = new HashMap<>();
    private PlayerLinkConfig.Values values = PlayerLinkConfig.defaults();

    public PlayerLinkManager(ComputerManager computers) {
        this.computers = computers;
    }

    public void load(Path file) {
        values = PlayerLinkConfig.load(file);
    }

    public PlayerLinkPeripheral peripheral(TurtleController turtle, int upgradeSlot, String side) {
        return new PlayerLinkPeripheral(new PlayerLinkPeripheral.Backend() {
            @Override
            public Configuration configuration() {
                return configurationAt(turtle, upgradeSlot);
            }

            @Override
            public boolean saveConfiguration(Configuration configuration) {
                return PlayerLinkManager.this.saveConfiguration(turtle, upgradeSlot, configuration);
            }

            @Override
            public String unavailableReason() {
                return resolve(turtle, upgradeSlot).reason();
            }

            @Override
            public PlayerLinkTransfer.Result transfer(int turtleSlot, int playerSlot, int count) {
                return PlayerLinkManager.this.transfer(turtle, upgradeSlot,
                        turtleSlot, playerSlot, count);
            }

            @Override
            public PlayerLinkPeripheral.SlotResult playerSlot(int playerSlot) {
                return inspect(turtle, upgradeSlot, playerSlot);
            }
        });
    }

    public synchronized void beginTick(World world) {
        transferBudget.keySet().removeIf(key -> key.world().equals(world.getName()));
        long now = System.nanoTime();
        eventTimes.entrySet().removeIf(entry -> now - entry.getValue() > 300_000_000_000L);
    }

    public synchronized void tick(TurtleController turtle) {
        for (int upgradeSlot = TurtleController.LEFT_TOOL_SLOT;
                upgradeSlot < TurtleController.INVENTORY_CAPACITY; upgradeSlot++) {
            Configuration configuration = configurationAt(turtle, upgradeSlot);
            if (configuration == null || !configuration.autoTransfer()) {
                continue;
            }
            Resolution resolution = resolve(turtle, upgradeSlot);
            if (resolution.reason() != null) {
                emitUnavailable(turtle, upgradeSlot, resolution.reason());
                continue;
            }
            for (int source = 0; source < TurtleController.INVENTORY_SLOTS; source++) {
                Integer destination = configuration.routes().get(source + 1);
                if (destination == null) {
                    destination = configuration.defaultSlot();
                }
                if (destination == null || !remaining(turtle, upgradeSlot)) {
                    continue;
                }
                ItemContainer inventory = turtle.getInventory();
                if (inventory == null) {
                    emitUnavailable(turtle, upgradeSlot, "Turtle inventory is unavailable");
                    break;
                }
                ItemStack stack = inventory.getItemStack((short) source);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                transfer(turtle, upgradeSlot, source + 1, destination, Integer.MAX_VALUE);
            }
        }
    }

    public synchronized void shutdown() {
        transferBudget.clear();
        eventTimes.clear();
    }

    private Configuration configurationAt(TurtleController turtle, int upgradeSlot) {
        ItemStack stack = turtle.getTool(upgradeSlot);
        return PlayerLinkItemMetadata.isPlayerLink(stack)
                ? PlayerLinkItemMetadata.configuration(stack) : null;
    }

    private boolean saveConfiguration(TurtleController turtle, int upgradeSlot,
            Configuration configuration) {
        ItemContainer inventory = turtle.getInventory();
        ItemStack current = turtle.getTool(upgradeSlot);
        if (inventory == null || !PlayerLinkItemMetadata.isPlayerLink(current)) {
            return false;
        }
        ItemStack updated = PlayerLinkItemMetadata.configure(current, configuration);
        return inventory.setItemStackForSlot((short) upgradeSlot, updated).succeeded();
    }

    private Resolution resolve(TurtleController turtle, int upgradeSlot) {
        if (!values.enabled()) {
            return new Resolution(null, "Player Link is disabled by the server");
        }
        Configuration configuration = configurationAt(turtle, upgradeSlot);
        if (configuration == null) {
            return new Resolution(null, "Player Link is detached");
        }
        UUID ownerId = configuration.ownerId();
        if (ownerId == null) {
            return new Resolution(null, "Hold the Player Link and press F to link it first");
        }
        PlayerRef playerRef = Universe.get().getPlayer(ownerId);
        if (!online(playerRef)) {
            return new Resolution(null, "The linked player is offline");
        }
        World playerWorld = ComputerManager.worldOf(playerRef);
        if (playerWorld == null || playerWorld != turtle.getWorld()) {
            return new Resolution(null, "The linked player is in another world");
        }
        Transform transform = playerRef.getTransform();
        if (transform == null || transform.getPosition() == null) {
            return new Resolution(null, "The linked player position is unavailable");
        }
        double dx = transform.getPosition().x - (turtle.getPosition().x + 0.5);
        double dy = transform.getPosition().y - (turtle.getPosition().y + 0.5);
        double dz = transform.getPosition().z - (turtle.getPosition().z + 0.5);
        if (dx * dx + dy * dy + dz * dz > (double) values.maxDistance() * values.maxDistance()) {
            return new Resolution(null, "The linked player is too far away");
        }
        Ref<EntityStore> reference = playerRef.getReference();
        Player player = reference.getStore().getComponent(reference, Player.getComponentType());
        return player == null || player.getInventory() == null
                ? new Resolution(null, "The linked player inventory is unavailable")
                : new Resolution(player, null);
    }

    private synchronized PlayerLinkTransfer.Result transfer(TurtleController turtle, int upgradeSlot,
            int turtleSlot, int playerSlot, int count) {
        Resolution resolution = resolve(turtle, upgradeSlot);
        if (resolution.reason() != null) {
            emitUnavailable(turtle, upgradeSlot, resolution.reason());
            return PlayerLinkTransfer.Result.failure(resolution.reason());
        }
        SlotTarget target = target(resolution.player(), playerSlot);
        if (target == null) {
            return PlayerLinkTransfer.Result.failure("Player slot is outside the hotbar and backpack");
        }
        if (!acquire(turtle, upgradeSlot)) {
            return PlayerLinkTransfer.Result.failure("Player Link transfer limit reached for this tick");
        }
        ItemContainer inventory = turtle.getInventory();
        if (inventory == null) {
            return PlayerLinkTransfer.Result.failure("Turtle inventory is unavailable");
        }
        PlayerLinkTransfer.Result result = PlayerLinkTransfer.move(inventory, turtleSlot - 1,
                target.container(), target.slot(), count);
        if (!result.success()) {
            emitFull(turtle, upgradeSlot, turtleSlot, playerSlot);
        }
        return result;
    }

    private PlayerLinkPeripheral.SlotResult inspect(TurtleController turtle, int upgradeSlot,
            int playerSlot) {
        Resolution resolution = resolve(turtle, upgradeSlot);
        if (resolution.reason() != null) {
            return PlayerLinkPeripheral.SlotResult.failure(resolution.reason());
        }
        SlotTarget target = target(resolution.player(), playerSlot);
        if (target == null) {
            return PlayerLinkPeripheral.SlotResult.failure(
                    "Player slot is outside the hotbar and backpack");
        }
        ItemStack stack = target.container().getItemStack(target.slot());
        if (stack == null || stack.isEmpty()) {
            return PlayerLinkPeripheral.SlotResult.success(null, 0, 64);
        }
        var item = stack.getItem();
        return PlayerLinkPeripheral.SlotResult.success(stack.getItemId(), stack.getQuantity(),
                item == null ? 64 : item.getMaxStack());
    }

    private static SlotTarget target(Player player, int oneBasedSlot) {
        ItemContainer hotbar = player.getInventory().getHotbar();
        int hotbarSize = hotbar.getCapacity();
        if (oneBasedSlot <= hotbarSize) {
            return new SlotTarget(hotbar, (short) (oneBasedSlot - 1));
        }
        ItemContainer backpack = player.getInventory().getBackpack();
        int backpackSlot = oneBasedSlot - hotbarSize - 1;
        return backpackSlot >= 0 && backpackSlot < backpack.getCapacity()
                ? new SlotTarget(backpack, (short) backpackSlot) : null;
    }

    private boolean acquire(TurtleController turtle, int upgradeSlot) {
        LinkKey key = key(turtle, upgradeSlot);
        int used = transferBudget.getOrDefault(key, 0);
        if (used >= values.transfersPerTick()) {
            return false;
        }
        transferBudget.put(key, used + 1);
        return true;
    }

    private boolean remaining(TurtleController turtle, int upgradeSlot) {
        return transferBudget.getOrDefault(key(turtle, upgradeSlot), 0) < values.transfersPerTick();
    }

    private void emitFull(TurtleController turtle, int upgradeSlot, int turtleSlot, int playerSlot) {
        Computer computer = computers.computerAt(turtle.getWorld(), turtle.getPosition());
        if (computer == null || !eventAllowed(turtle, upgradeSlot, "full:" + turtleSlot)) {
            return;
        }
        computer.getMachine().queueEvent("player_link_full", sideName(upgradeSlot),
                turtleSlot, playerSlot);
    }

    private void emitUnavailable(TurtleController turtle, int upgradeSlot, String reason) {
        Computer computer = computers.computerAt(turtle.getWorld(), turtle.getPosition());
        if (computer == null || !eventAllowed(turtle, upgradeSlot, "unavailable")) {
            return;
        }
        computer.getMachine().queueEvent("player_link_unavailable", sideName(upgradeSlot),
                me.kvothe.taleos.I18n.t(reason));
    }

    private boolean eventAllowed(TurtleController turtle, int upgradeSlot, String type) {
        String key = turtle.getWorld().getName() + ":" + turtle.getComputerId() + ":"
                + upgradeSlot + ":" + type;
        long now = System.nanoTime();
        Long previous = eventTimes.get(key);
        if (previous != null && now - previous < EVENT_INTERVAL_NANOS) {
            return false;
        }
        eventTimes.put(key, now);
        return true;
    }

    private static LinkKey key(TurtleController turtle, int upgradeSlot) {
        return new LinkKey(turtle.getWorld().getName(), turtle.getComputerId(), upgradeSlot);
    }

    private static String sideName(int upgradeSlot) {
        if (upgradeSlot == TurtleController.LEFT_TOOL_SLOT) {
            return "left";
        }
        if (upgradeSlot == TurtleController.RIGHT_TOOL_SLOT) {
            return "right";
        }
        return "internal" + (upgradeSlot - TurtleController.FIRST_INTERNAL_SLOT + 1);
    }

    private static boolean online(PlayerRef playerRef) {
        try {
            Ref<EntityStore> reference = playerRef == null ? null : playerRef.getReference();
            return playerRef != null && playerRef.isValid() && reference != null && reference.isValid()
                    && Universe.get().getPlayer(playerRef.getUuid()) == playerRef;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}

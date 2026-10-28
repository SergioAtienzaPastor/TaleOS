package me.kvothe.talecraft.pocket;

import java.util.UUID;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.computer.ComputerManager;

public final class PocketHolder {
    public interface PlayerAccess {
        UUID playerId();

        boolean online();

        Object world();

        BlockPosition position();
    }

    public interface ContainerAccess {
        ItemStack stack(short slot);

        default Object identity() {
            return this;
        }
    }

    private final PlayerAccess player;
    private final ContainerAccess container;
    private final short slot;
    private final PocketComputerItemMetadata.Identity identity;

    public PocketHolder(PlayerAccess player, ContainerAccess container, short slot,
            PocketComputerItemMetadata.Identity identity) {
        if (player == null || container == null || identity == null) {
            throw new IllegalArgumentException("Pocket holder requires player, container and identity");
        }
        this.player = player;
        this.container = container;
        this.slot = slot;
        this.identity = identity;
    }

    public static PocketHolder hytale(PlayerRef player, ItemContainer container, short slot,
            PocketComputerItemMetadata.Identity identity) {
        return new PocketHolder(playerAccess(player), containerAccess(container), slot, identity);
    }

    public static PlayerAccess playerAccess(PlayerRef player) {
        return new PlayerAccess() {
            @Override
            public UUID playerId() {
                return player.getUuid();
            }

            @Override
            public boolean online() {
                try {
                    Ref<EntityStore> ref = player.getReference();
                    return player.isValid() && ref != null && ref.isValid()
                            && Universe.get().getPlayer(player.getUuid()) == player;
                } catch (RuntimeException exception) {
                    return false;
                }
            }

            @Override
            public Object world() {
                return ComputerManager.worldOf(player);
            }

            @Override
            public BlockPosition position() {
                try {
                    Transform transform = player.getTransform();
                    if (transform == null || transform.getPosition() == null) {
                        return null;
                    }
                    return new BlockPosition(
                            (int) Math.floor(transform.getPosition().x),
                            (int) Math.floor(transform.getPosition().y),
                            (int) Math.floor(transform.getPosition().z));
                } catch (RuntimeException exception) {
                    return null;
                }
            }
        };
    }

    public static ContainerAccess containerAccess(ItemContainer container) {
        return new ContainerAccess() {
            @Override
            public ItemStack stack(short slot) {
                return container.getItemStack(slot);
            }

            @Override
            public Object identity() {
                return container;
            }
        };
    }

    public boolean validIn(Object expectedWorld) {
        if (expectedWorld == null || !player.online() || player.world() != expectedWorld
                || player.position() == null) {
            return false;
        }
        try {
            return PocketComputerItemMetadata.matches(container.stack(slot), identity);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean sameLocation(PocketHolder other) {
        return other != null && slot == other.slot && container.identity() == other.container.identity()
                && player.playerId().equals(other.player.playerId());
    }

    public UUID playerId() {
        return player.playerId();
    }

    public Object world() {
        return player.world();
    }

    public BlockPosition position() {
        return player.position();
    }

    public short slot() {
        return slot;
    }

    public PocketComputerItemMetadata.Identity identity() {
        return identity;
    }
}

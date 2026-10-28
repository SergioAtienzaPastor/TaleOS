package me.kvothe.talecraft.pocket;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;

import me.kvothe.talecraft.computer.Computer;
import me.kvothe.talecraft.computer.ComputerIdStore;
import me.kvothe.talecraft.computer.ComputerManager;
import me.kvothe.talecraft.computer.HytaleWorldBridge;

public final class PocketManager {
    @FunctionalInterface
    public interface MachineFactory {
        Computer create(PocketHolder holder, int id, Path root) throws IOException;
    }

    private static final class Session {
        private final PocketHolder holder;
        private final Object world;
        private Computer computer;
        private int nasTicks;

        private Session(PocketHolder holder, Computer computer) {
            this.holder = holder;
            this.world = holder.world();
            this.computer = computer;
        }
    }

    private final ComputerManager manager;
    private final Path dataDir;
    private final ComputerIdStore idStore;
    private final MachineFactory machineFactory;
    private final Map<UUID, Session> byUuid = new ConcurrentHashMap<>();
    private final Map<Integer, Session> byId = new ConcurrentHashMap<>();

    public PocketManager(ComputerManager manager, Path dataDir, ComputerIdStore idStore) {
        this(manager, dataDir, idStore, (holder, id, root) -> new Computer(id, root,
                new HytaleWorldBridge((World) holder.world(), holder::position, id, manager,
                        () -> manager.pockets().computer(id))));
    }

    public PocketManager(ComputerManager manager, Path dataDir, ComputerIdStore idStore,
            MachineFactory machineFactory) {
        this.manager = manager;
        this.dataDir = dataDir;
        this.idStore = idStore;
        this.machineFactory = machineFactory;
    }

    public synchronized ItemStack assignIdentity(ItemStack stack) {
        if (!PocketComputerItemMetadata.isPocketComputer(stack)) {
            throw new IllegalArgumentException("not a single Pocket Computer stack");
        }
        Integer carriedId = PocketComputerItemMetadata.id(stack);
        UUID uuid = PocketComputerItemMetadata.uuid(stack);
        int id = carriedId != null ? carriedId : idStore.allocateGlobalId();
        if (carriedId != null) {
            idStore.reserveGlobalId(id);
        }
        UUID effectiveUuid = uuid != null ? uuid : UUID.randomUUID();
        if (carriedId != null && uuid != null) {
            return stack;
        }
        return PocketComputerItemMetadata.withIdentity(stack, id, effectiveUuid);
    }

    public synchronized Computer boot(PocketHolder holder) {
        Object world = holder.world();
        if (!holder.validIn(world)) {
            throw new IllegalStateException("Pocket Computer is no longer held by its owner");
        }
        PocketComputerItemMetadata.Identity identity = holder.identity();
        Session existing = byUuid.get(identity.uuid());
        Session withSameId = byId.get(identity.id());
        if (existing != null || withSameId != null) {
            if (existing != null && existing == withSameId
                    && existing.holder.sameLocation(holder) && existing.world == world
                    && existing.computer.isAlive()) {
                return existing.computer;
            }
            throw new IllegalStateException("Pocket Computer #" + identity.id()
                    + " is already active from another stack");
        }

        Computer computer = create(holder);
        Session session = new Session(holder, computer);
        byUuid.put(identity.uuid(), session);
        byId.put(identity.id(), session);
        try {
            computer.boot();
            if (world instanceof World hytaleWorld) {
                manager.nas().mountPocket(hytaleWorld, holder.position(), computer);
            }
            return computer;
        } catch (RuntimeException | Error failure) {
            remove(session);
            manager.nas().unmountComputer(me.kvothe.talecraft.storage.NasManager.pocketKey(identity.id()));
            computer.close();
            throw failure;
        }
    }

    private Computer create(PocketHolder holder) {
        int id = holder.identity().id();
        try {
            Computer computer = machineFactory.create(holder, id,
                    dataDir.resolve("computers").resolve(String.valueOf(id)));
            manager.prepareComputer(computer);
            return computer;
        } catch (IOException exception) {
            throw new IllegalStateException("cannot boot Pocket Computer #" + id, exception);
        }
    }

    public synchronized void tickWorld(Object world) {
        for (Session session : List.copyOf(byUuid.values())) {
            if (session.world != world || byUuid.get(session.holder.identity().uuid()) != session) {
                continue;
            }
            if (!session.holder.validIn(world)) {
                close(session);
                continue;
            }
            Computer computer = session.computer;
            if (computer.isHalted()) {
                if (computer.wantsReboot()) {
                    reboot(session);
                } else {
                    close(session);
                }
            } else {
                if (world instanceof World hytaleWorld && ++session.nasTicks % 20 == 0) {
                    manager.nas().mountPocket(hytaleWorld, session.holder.position(), computer);
                }
                manager.beginSpeakerTick(computer.getId());
                computer.tick();
            }
        }
    }

    private void reboot(Session session) {
        close(session);
        if (session.holder.validIn(session.world)) {
            boot(session.holder);
        }
    }

    public synchronized void disconnect(UUID playerId) {
        for (Session session : List.copyOf(byUuid.values())) {
            if (session.holder.playerId().equals(playerId)) {
                close(session);
            }
        }
    }

    private void close(Session session) {
        remove(session);
        manager.nas().unmountComputer(me.kvothe.talecraft.storage.NasManager.pocketKey(
                session.holder.identity().id()));
        session.computer.close();
        manager.releaseComputer(session.computer);
    }

    private void remove(Session session) {
        byUuid.remove(session.holder.identity().uuid(), session);
        byId.remove(session.holder.identity().id(), session);
    }

    public Computer computer(UUID uuid) {
        Session session = byUuid.get(uuid);
        return session == null ? null : session.computer;
    }

    public Computer computer(int id) {
        Session session = byId.get(id);
        return session == null ? null : session.computer;
    }

    public int activeCount() {
        return byUuid.size();
    }

    public synchronized void shutdown() {
        for (Session session : List.copyOf(byUuid.values())) {
            close(session);
        }
        byUuid.clear();
        byId.clear();
    }
}

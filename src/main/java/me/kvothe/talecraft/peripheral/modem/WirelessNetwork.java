package me.kvothe.talecraft.peripheral.modem;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

public final class WirelessNetwork {
    public static final int MIN_CHANNEL = 0;
    public static final int MAX_CHANNEL = 65_535;
    public static final int MAX_OPEN_CHANNELS = 128;
    public static final int DEFAULT_RANGE = 64;
    public static final int DEFAULT_MAX_MESSAGES_PER_TICK = 32;

    public record ModemMessage(String side, int channel, int replyChannel, LuaValue payload,
            double distance) {
    }

    public interface Node {
        int ownerId();

        String worldName();

        String side();

        int x();

        int y();

        int z();

        boolean isAlive();

        boolean isLoaded();

        boolean deliver(ModemMessage message);
    }

    private record ChannelKey(int ownerId, String side) {
    }

    private final int range;
    private final int maxMessagesPerTick;
    private final Set<Node> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<ChannelKey, Set<Integer>> channels = new HashMap<>();
    private final Map<String, Integer> messagesThisTick = new HashMap<>();

    public WirelessNetwork() {
        this(DEFAULT_RANGE, DEFAULT_MAX_MESSAGES_PER_TICK);
    }

    public WirelessNetwork(int range, int maxMessagesPerTick) {
        if (range < 1 || range > 256) {
            throw new IllegalArgumentException("wireless range must be between 1 and 256");
        }
        if (maxMessagesPerTick < 1 || maxMessagesPerTick > 256) {
            throw new IllegalArgumentException("messages per tick must be between 1 and 256");
        }
        this.range = range;
        this.maxMessagesPerTick = maxMessagesPerTick;
    }

    public synchronized void attach(Node node) {
        nodes.add(node);
        channels.computeIfAbsent(key(node), ignored -> new HashSet<>());
    }

    public synchronized void open(Node node, int channel) {
        checkChannel(channel);
        attach(node);
        Set<Integer> open = channels.get(key(node));
        if (open.size() >= MAX_OPEN_CHANNELS && !open.contains(channel)) {
            throw new LuaError("a modem cannot open more than " + MAX_OPEN_CHANNELS + " channels");
        }
        open.add(channel);
    }

    public synchronized void close(Node node, int channel) {
        checkChannel(channel);
        Set<Integer> open = channels.get(key(node));
        if (open != null) {
            open.remove(channel);
        }
    }

    public synchronized void closeAll(Node node) {
        Set<Integer> open = channels.get(key(node));
        if (open != null) {
            open.clear();
        }
    }

    public synchronized boolean isOpen(Node node, int channel) {
        checkChannel(channel);
        Set<Integer> open = channels.get(key(node));
        return open != null && open.contains(channel);
    }

    public synchronized int transmit(Node sender, int channel, int replyChannel, LuaValue payload) {
        checkChannel(channel);
        checkChannel(replyChannel);
        if (!nodes.contains(sender) || !sender.isAlive() || !sender.isLoaded()) {
            throw new LuaError("wireless modem is detached");
        }
        Object wirePayload = ModemPayload.toWire(payload);
        int sent = messagesThisTick.getOrDefault(sender.worldName(), 0);
        if (sent >= maxMessagesPerTick) {
            throw new LuaError("wireless message limit reached for this tick");
        }
        messagesThisTick.put(sender.worldName(), sent + 1);
        int delivered = 0;
        for (Node receiver : nodes) {
            if (receiver.ownerId() == sender.ownerId()
                    || !receiver.worldName().equals(sender.worldName())
                    || !isOpen(receiver, channel)
                    || !receiver.isAlive() || !receiver.isLoaded()) {
                continue;
            }
            double distance = distance(sender, receiver);
            if (distance > range) {
                continue;
            }
            LuaValue receiverCopy = ModemPayload.fromWire(wirePayload);
            if (receiver.deliver(new ModemMessage(receiver.side(), channel, replyChannel,
                    receiverCopy, distance))) {
                delivered++;
            }
        }
        return delivered;
    }

    public synchronized void beginTick(String worldName) {
        messagesThisTick.remove(worldName);
    }

    public synchronized void removeOwner(int ownerId) {
        nodes.removeIf(node -> node.ownerId() == ownerId);
        channels.keySet().removeIf(key -> key.ownerId() == ownerId);
        messagesThisTick.clear();
    }

    public synchronized void clear() {
        nodes.clear();
        channels.clear();
        messagesThisTick.clear();
    }

    synchronized int openChannelCount(Node node) {
        Set<Integer> open = channels.get(key(node));
        return open == null ? 0 : open.size();
    }

    private static ChannelKey key(Node node) {
        return new ChannelKey(node.ownerId(), node.side());
    }

    private static double distance(Node first, Node second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void checkChannel(int channel) {
        if (channel < MIN_CHANNEL || channel > MAX_CHANNEL) {
            throw new LuaError("channel must be an integer between 0 and 65535");
        }
    }
}

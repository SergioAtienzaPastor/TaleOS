package me.kvothe.taleos.lua;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import me.kvothe.taleos.peripheral.Peripheral;
import me.kvothe.taleos.peripheral.PeripheralProvider;

final class LuaModemTestSupport {
    private LuaModemTestSupport() {
    }

    static final class Provider implements PeripheralProvider {
        final Map<String, Peripheral> values = new LinkedHashMap<>();

        @Override
        public Map<String, Peripheral> snapshot() {
            return new LinkedHashMap<>(values);
        }

        @Override
        public Peripheral find(String name) {
            return values.get(name);
        }
    }

    record Transmission(int channel, int replyChannel, LuaValue payload) {
    }

    static final class Modem implements Peripheral {
        final Set<Integer> channels = new HashSet<>();
        final List<Transmission> transmissions = new ArrayList<>();

        @Override
        public String getType() {
            return "modem";
        }

        @Override
        public List<String> getMethods() {
            return List.of("open", "close", "closeAll", "isOpen", "transmit", "isWireless");
        }

        @Override
        public Varargs call(String method, Varargs args) {
            return switch (method) {
                case "open" -> {
                    channels.add(args.arg1().checkint());
                    yield LuaValue.NIL;
                }
                case "close" -> {
                    channels.remove(args.arg1().checkint());
                    yield LuaValue.NIL;
                }
                case "closeAll" -> {
                    channels.clear();
                    yield LuaValue.NIL;
                }
                case "isOpen" -> LuaValue.valueOf(channels.contains(args.arg1().checkint()));
                case "transmit" -> {
                    transmissions.add(new Transmission(args.arg(1).checkint(), args.arg(2).checkint(),
                            args.arg(3)));
                    yield LuaValue.NIL;
                }
                case "isWireless" -> LuaValue.TRUE;
                default -> LuaValue.NIL;
            };
        }
    }

    static WorldBridge bridge(Provider provider) {
        return new WorldBridge() {
            @Override
            public String getBlockId(int x, int y, int z) {
                return null;
            }

            @Override
            public double getTime() {
                return 0;
            }

            @Override
            public void chat(String message) {
            }

            @Override
            public PeripheralProvider peripherals() {
                return provider;
            }
        };
    }

    static LuaValue rednetMessage(LuaValue message, String protocol) {
        org.luaj.vm2.LuaTable envelope = new org.luaj.vm2.LuaTable();
        envelope.set("taleos_rednet", LuaValue.TRUE);
        envelope.set("message", message);
        if (protocol == null) {
            envelope.set("protocol", LuaValue.NIL);
        } else {
            envelope.set("protocol", protocol);
        }
        return envelope;
    }
}

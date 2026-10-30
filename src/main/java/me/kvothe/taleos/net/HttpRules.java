package me.kvothe.taleos.net;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

final class HttpRules {
    private HttpRules() {
    }

    record Rule(Matcher matcher, boolean allow, long maxDownload) {
        boolean matches(String host, List<InetAddress> addresses) {
            return matcher.matches(host.toLowerCase(Locale.ROOT), addresses);
        }
    }

    interface Matcher {
        boolean matches(String host, List<InetAddress> addresses);
    }

    static Rule parse(String pattern, String action, long maxDownload) {
        if (pattern == null || pattern.isBlank() || action == null) {
            throw new IllegalArgumentException("HTTP rule requires a host and action");
        }
        boolean allow = switch (action.toLowerCase(Locale.ROOT)) {
            case "allow" -> true;
            case "deny" -> false;
            default -> throw new IllegalArgumentException("Invalid HTTP rule action: " + action);
        };
        if (maxDownload < 0) {
            throw new IllegalArgumentException("HTTP rule max_download cannot be negative");
        }
        return new Rule(parseMatcher(pattern.trim()), allow, maxDownload);
    }

    static Rule denyAll() {
        return new Rule((host, addresses) -> true, false, 0);
    }

    static boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int value = ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16)
                    | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
            return in(value, 0x00000000, 8) || in(value, 0x0a000000, 8)
                    || in(value, 0x64400000, 10) || in(value, 0x7f000000, 8)
                    || in(value, 0xa9fe0000, 16) || in(value, 0xac100000, 12)
                    || in(value, 0xc0000000, 24) || in(value, 0xc0000200, 24)
                    || in(value, 0xc0586300, 24) || in(value, 0xc0a80000, 16)
                    || in(value, 0xc6120000, 15) || in(value, 0xc6336400, 24)
                    || in(value, 0xcb007100, 24) || in(value, 0xe0000000, 4)
                    || in(value, 0xf0000000, 4);
        }
        if (address instanceof Inet6Address) {
            return prefix(bytes, 0, 96) || prefixHex(bytes, "0064ff9b", 96)
                    || prefixHex(bytes, "0064ff9b0001", 48) || prefixHex(bytes, "0100", 64)
                    || prefixHex(bytes, "2001", 23) || prefixHex(bytes, "20010db8", 32)
                    || prefixHex(bytes, "fc", 7) || prefixHex(bytes, "fe80", 10)
                    || prefixHex(bytes, "fec0", 10) || prefixHex(bytes, "ff", 8);
        }
        return true;
    }

    private static Matcher parseMatcher(String pattern) {
        if (pattern.equals("*")) {
            return (host, addresses) -> true;
        }
        if (pattern.equalsIgnoreCase("$private")) {
            return (host, addresses) -> {
                for (InetAddress address : addresses) {
                    if (isPrivate(address)) {
                        return true;
                    }
                }
                return false;
            };
        }
        if (pattern.indexOf('/') >= 0) {
            return cidr(pattern);
        }
        byte[] literal = parseIp(pattern);
        if (literal != null) {
            return (host, addresses) -> {
                for (InetAddress address : addresses) {
                    if (java.util.Arrays.equals(literal, address.getAddress())) {
                        return true;
                    }
                }
                return false;
            };
        }
        if (pattern.startsWith("*.")) {
            String suffix = domain(pattern.substring(2));
            return (host, addresses) -> host.endsWith("." + suffix);
        }
        String domain = domain(pattern);
        return (host, addresses) -> host.equals(domain);
    }

    private static String domain(String value) {
        try {
            String ascii = IDN.toASCII(value).toLowerCase(Locale.ROOT);
            if (ascii.isEmpty() || ascii.length() > 253 || ascii.startsWith(".") || ascii.endsWith(".")
                    || ascii.contains("..")) {
                throw new IllegalArgumentException("Invalid HTTP rule host: " + value);
            }
            for (String label : ascii.split("\\.", -1)) {
                if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                    throw new IllegalArgumentException("Invalid HTTP rule host: " + value);
                }
                for (int i = 0; i < label.length(); i++) {
                    char c = label.charAt(i);
                    if (!Character.isLetterOrDigit(c) && c != '-') {
                        throw new IllegalArgumentException("Invalid HTTP rule host: " + value);
                    }
                }
            }
            return ascii;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid HTTP rule host: " + value, exception);
        }
    }

    private static Matcher cidr(String value) {
        int slash = value.lastIndexOf('/');
        if (slash <= 0 || slash != value.indexOf('/')) {
            throw new IllegalArgumentException("Invalid HTTP CIDR rule: " + value);
        }
        byte[] network = parseIp(value.substring(0, slash));
        if (network == null) {
            throw new IllegalArgumentException("Invalid HTTP CIDR rule: " + value);
        }
        int prefix;
        try {
            prefix = Integer.parseInt(value.substring(slash + 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid HTTP CIDR rule: " + value, exception);
        }
        if (prefix < 0 || prefix > network.length * 8) {
            throw new IllegalArgumentException("Invalid HTTP CIDR rule: " + value);
        }
        return (host, addresses) -> {
            for (InetAddress address : addresses) {
                byte[] candidate = address.getAddress();
                if (candidate.length == network.length && prefixMatches(network, candidate, prefix)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static byte[] parseIp(String value) {
        if (value.indexOf(':') >= 0) {
            if (!value.matches("[0-9A-Fa-f:.]+")) {
                return null;
            }
            try {
                InetAddress address = InetAddress.getByName(value);
                return address instanceof Inet6Address ? address.getAddress() : null;
            } catch (UnknownHostException exception) {
                return null;
            }
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] result = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || !parts[i].matches("[0-9]{1,3}")) {
                return null;
            }
            try {
                int octet = Integer.parseInt(parts[i]);
                if (octet > 255) {
                    return null;
                }
                result[i] = (byte) octet;
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return result;
    }

    private static boolean prefixMatches(byte[] network, byte[] candidate, int prefix) {
        int whole = prefix / 8;
        for (int i = 0; i < whole; i++) {
            if (network[i] != candidate[i]) {
                return false;
            }
        }
        int remaining = prefix % 8;
        if (remaining == 0) {
            return true;
        }
        int mask = 0xff << (8 - remaining);
        return (network[whole] & mask) == (candidate[whole] & mask);
    }

    private static boolean in(int value, int network, int prefix) {
        int mask = prefix == 0 ? 0 : (int) (0xffffffffL << (32 - prefix));
        return (value & mask) == network;
    }

    private static boolean prefix(byte[] bytes, int value, int prefix) {
        byte[] network = new byte[bytes.length];
        if (value != 0) {
            network[network.length - 1] = (byte) value;
        }
        return prefixMatches(network, bytes, prefix);
    }

    private static boolean prefixHex(byte[] bytes, String hex, int prefix) {
        byte[] network = new byte[bytes.length];
        for (int i = 0; i < hex.length() / 2; i++) {
            network[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return prefixMatches(network, bytes, prefix);
    }
}

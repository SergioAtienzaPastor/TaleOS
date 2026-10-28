package me.kvothe.talecraft.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HttpConfig {
    static final String DEFAULT_JSON = """
        {
          "http": {
            "enabled": true,
            "max_requests": 16,
            "timeout_seconds": 30,
            "max_redirects": 16,
            "bandwidth": {
              "global_download": 33554432
            },
            "rules": [
              { "host": "$private", "action": "deny" },
              { "host": "*", "action": "allow", "max_download": 16777216 }
            ]
          },
          "modem": {
            "range": 64,
            "max_messages_per_tick": 32
          }
        }
        """;

    record Values(boolean enabled, int maxRequests, int timeoutSeconds, int maxRedirects,
                  long globalDownload, List<HttpRules.Rule> rules) {
    }

    private HttpConfig() {
    }

    static Values load(Path file) {
        if (Files.notExists(file)) {
            try {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, DEFAULT_JSON);
            } catch (IOException | RuntimeException exception) {
                System.err.println("[TaleCraft] " +"HTTP disabled: cannot create " + file + ": " + exception.getMessage());
                return disabled();
            }
        }
        try {
            Object root = new Parser(Files.readString(file)).parse();
            return read(root);
        } catch (IOException | RuntimeException exception) {
            System.err.println("[TaleCraft] " +"HTTP disabled: invalid config " + file + ": " + exception.getMessage());
            return disabled();
        }
    }

    static Values defaults() {
        try {
            return read(new Parser(DEFAULT_JSON).parse());
        } catch (RuntimeException exception) {
            return disabled();
        }
    }

    private static Values read(Object root) {
        Map<String, Object> http = object(root, "root");
        Map<String, Object> values = object(required(http, "http"), "http");
        boolean enabled = bool(required(values, "enabled"), "enabled");
        int maxRequests = integer(required(values, "max_requests"), "max_requests", 0, Integer.MAX_VALUE);
        int timeout = integer(required(values, "timeout_seconds"), "timeout_seconds", 0, 60);
        int redirects = integer(required(values, "max_redirects"), "max_redirects", 0, 1000);
        Map<String, Object> bandwidth = object(required(values, "bandwidth"), "bandwidth");
        long global = longValue(required(bandwidth, "global_download"), "global_download", 0, Long.MAX_VALUE);
        List<Object> rawRules = array(required(values, "rules"), "rules");
        List<HttpRules.Rule> rules = new ArrayList<>();
        try {
            for (Object raw : rawRules) {
                Map<String, Object> rule = object(raw, "rule");
                String host = string(required(rule, "host"), "rule host");
                String action = string(required(rule, "action"), "rule action");
                Object rawLimit = rule.get("max_download");
                long limit = rawLimit == null ? 0 : longValue(rawLimit, "max_download", 0, Long.MAX_VALUE);
                rules.add(HttpRules.parse(host, action, limit));
            }
        } catch (RuntimeException invalidRule) {
            System.err.println("[TaleCraft] " +"Invalid HTTP rule; all addresses denied: " + invalidRule.getMessage());
            return new Values(enabled, maxRequests, timeout, redirects, global, List.of(HttpRules.denyAll()));
        }
        return new Values(enabled, maxRequests, timeout, redirects, global, List.copyOf(rules));
    }

    private static Values disabled() {
        return new Values(false, 16, 30, 16, 33554432,
                List.of(HttpRules.parse("$private", "deny", 0), HttpRules.parse("*", "allow", 16777216)));
    }

    private static Object required(Map<String, Object> object, String key) {
        if (!object.containsKey(key)) {
            throw new IllegalArgumentException("Missing HTTP config value: " + key);
        }
        return object.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String name) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        return (List<Object>) value;
    }

    private static String string(Object value, String name) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return (String) value;
    }

    private static boolean bool(Object value, String name) {
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return (Boolean) value;
    }

    private static int integer(Object value, String name, int min, int max) {
        long parsed = longValue(value, name, min, max);
        return (int) parsed;
    }

    private static long longValue(Object value, String name, long min, long max) {
        if (!(value instanceof Long parsed) || parsed < min || parsed > max) {
            throw new IllegalArgumentException(name + " must be an integer between " + min + " and " + max);
        }
        return parsed;
    }

    static final class Parser {
        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
        }

        Object parse() {
            skipSpace();
            Object value = value();
            skipSpace();
            if (position != text.length()) {
                throw error("Trailing JSON content");
            }
            return value;
        }

        private Object value() {
            skipSpace();
            if (position >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            return switch (text.charAt(position)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            position++;
            Map<String, Object> result = new LinkedHashMap<>();
            skipSpace();
            if (take('}')) {
                return result;
            }
            while (true) {
                skipSpace();
                if (position >= text.length() || text.charAt(position) != '"') {
                    throw error("Expected object key");
                }
                String key = string();
                skipSpace();
                expect(':');
                if (result.put(key, value()) != null) {
                    throw error("Duplicate object key: " + key);
                }
                skipSpace();
                if (take('}')) {
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> array() {
            position++;
            List<Object> result = new ArrayList<>();
            skipSpace();
            if (take(']')) {
                return result;
            }
            while (true) {
                result.add(value());
                skipSpace();
                if (take(']')) {
                    return result;
                }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') {
                    return result.toString();
                }
                if (c == '\\') {
                    if (position >= text.length()) {
                        throw error("Unfinished string escape");
                    }
                    char escaped = text.charAt(position++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(unicode());
                        default -> throw error("Invalid string escape");
                    }
                } else {
                    if (c < 0x20) {
                        throw error("Control character in string");
                    }
                    result.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private char unicode() {
            if (position + 4 > text.length()) {
                throw error("Invalid unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(position++), 16);
                if (digit < 0) {
                    throw error("Invalid unicode escape");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Object number() {
            int start = position;
            if (take('-') && position >= text.length()) {
                throw error("Invalid number");
            }
            if (take('0')) {
                if (position < text.length() && Character.isDigit(text.charAt(position))) {
                    throw error("Leading zero in number");
                }
            } else {
                digits();
            }
            if (take('.')) {
                digits();
                throw error("HTTP config numbers must be integers");
            }
            if (position < text.length() && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
                throw error("HTTP config numbers must be integers");
            }
            try {
                return Long.valueOf(text.substring(start, position));
            } catch (NumberFormatException exception) {
                throw error("Invalid number");
            }
        }

        private void digits() {
            int start = position;
            while (position < text.length() && Character.isDigit(text.charAt(position))) {
                position++;
            }
            if (start == position) {
                throw error("Expected digit");
            }
        }

        private Object literal(String literal, Object value) {
            if (!text.startsWith(literal, position)) {
                throw error("Invalid JSON value");
            }
            position += literal.length();
            return value;
        }

        private void skipSpace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private boolean take(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at JSON offset " + position);
        }
    }
}

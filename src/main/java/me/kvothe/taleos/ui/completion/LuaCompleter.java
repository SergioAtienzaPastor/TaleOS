package me.kvothe.taleos.ui.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class LuaCompleter implements Completer {

    private static final Set<String> KEYWORDS = Set.of(
            "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
            "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
            "true", "until", "while");

    private static final Set<String> SAFE_GLOBALS = Set.of(
            "_G", "_VERSION", "assert", "error", "getmetatable", "ipairs", "load", "next",
            "pairs", "pcall", "print", "rawequal", "rawget", "rawlen", "rawset", "select",
            "setmetatable", "tonumber", "tostring", "type", "xpcall", "fs", "tale", "os",
            "term", "window", "colors", "colours", "string", "math", "table", "coroutine", "http",
            "parallel", "peripheral", "rednet", "gps");

    private static final Map<String, Set<String>> MEMBERS = Map.ofEntries(
            Map.entry("http", Set.of("get")),
            Map.entry("peripheral", Set.of("getNames", "getType", "getMethods", "call", "wrap", "find")),
            Map.entry("rednet", Set.of("open", "close", "closeAll", "isOpen", "send", "broadcast",
                    "receive", "host", "unhost", "lookup", "run", "CHANNEL_BROADCAST")),
            Map.entry("gps", Set.of("locate", "host", "trilaterate", "CHANNEL")),
            Map.entry("fs", Set.of("exists", "isDir", "list", "read", "write", "append", "mkdir",
                    "delete", "getSize", "getCapacity", "getFreeSpace")),
            Map.entry("tale", Set.of("pullEvent", "getComputerID", "setPrompt", "reboot", "shutdown",
                    "getBlock", "detect", "chat", "time", "sleep", "monitor")),
            Map.entry("os", Set.of("clock", "time", "date", "openEditor", "pullEvent", "pullEventRaw",
                    "queueEvent", "startTimer", "cancelTimer", "getComputerID", "reboot", "shutdown", "sleep")),
            Map.entry("parallel", Set.of("waitForAny", "waitForAll")),
            Map.entry("window", Set.of("create")),
            Map.entry("term", Set.of("write", "blit", "clear", "clearLine", "scroll", "getSize",
                    "setCursorPos", "getCursorPos", "setCursorBlink", "getCursorBlink",
                    "setTextColor", "getTextColor", "setBackgroundColor", "getBackgroundColor",
                    "isColor", "native", "current", "redirect")),
            Map.entry("string", Set.of("byte", "char", "dump", "find", "format", "gmatch", "gsub",
                    "len", "lower", "match", "rep", "reverse", "sub", "upper")),
            Map.entry("math", Set.of("abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "cosh",
                    "deg", "exp", "floor", "fmod", "frexp", "huge", "ldexp", "log", "max", "min",
                    "modf", "pi", "pow", "rad", "random", "randomseed", "sin", "sinh", "sqrt", "tan",
                    "tanh")),
            Map.entry("table", Set.of("concat", "insert", "maxn", "pack", "remove", "sort", "unpack")),
            Map.entry("coroutine", Set.of("create", "resume", "running", "status", "wrap", "yield")),
            Map.entry("turtle", Set.of("forward", "back", "up", "down", "turnLeft", "turnRight",
                    "detect", "detectUp", "detectDown", "inspect", "inspectUp", "inspectDown", "select",
                    "getSelectedSlot", "getItemCount", "getItemSpace", "getItemDetail", "transferTo",
                    "compare", "compareUp", "compareDown", "compareTo", "equip", "unequip", "equipLeft",
                    "equipRight", "getEquippedLeft", "getEquippedRight", "getFuelLevel", "getFuelLimit",
                    "refuel", "dig", "digUp", "digDown", "attack", "attackUp", "attackDown",
                    "craft", "place", "placeUp", "placeDown", "suck",
                    "suckUp", "suckDown", "drop", "dropUp", "dropDown")));

    private final boolean turtleAvailable;

    public LuaCompleter(boolean turtleAvailable) {
        this.turtleAvailable = turtleAvailable;
    }

    @Override
    public Completion complete(String text, int caret) {
        if (caret < 0 || caret > text.length() || !isCode(text, caret)) {
            return null;
        }
        int start = caret;
        while (start > 0 && isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        String fragment = text.substring(start, caret);
        if (start > 0 && text.charAt(start - 1) == '.') {
            int rootEnd = start - 1;
            int rootStart = rootEnd;
            while (rootStart > 0 && isIdentifierPart(text.charAt(rootStart - 1))) {
                rootStart--;
            }
            if (rootStart == rootEnd) {
                return null;
            }
            String root = text.substring(rootStart, rootEnd);
            if (root.equals("turtle") && !turtleAvailable) {
                return null;
            }
            Set<String> members = MEMBERS.get(root);
            return members == null ? null : new Completion(start, caret, matching(members, fragment));
        }
        TreeSet<String> values = new TreeSet<>(KEYWORDS);
        values.addAll(SAFE_GLOBALS);
        if (turtleAvailable) {
            values.add("turtle");
        }
        return new Completion(start, caret, matching(values, fragment));
    }

    private static List<String> matching(Set<String> values, String fragment) {
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(fragment)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private static boolean isIdentifierPart(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }

    private static boolean isCode(String text, int caret) {
        int index = 0;
        while (index < caret) {
            char c = text.charAt(index);
            if (c == '-' && index + 1 < caret && text.charAt(index + 1) == '-') {
                int content = longBracketContentStart(text, index + 2);
                if (content >= 0) {
                    int equals = content - index - 4;
                    int close = text.indexOf("]" + "=".repeat(equals) + "]", content);
                    if (close < 0 || close + equals + 2 > caret) {
                        return false;
                    }
                    index = close + equals + 2;
                } else {
                    int newline = text.indexOf('\n', index + 2);
                    if (newline < 0 || newline >= caret) {
                        return false;
                    }
                    index = newline + 1;
                }
            } else if (c == '\'' || c == '"') {
                index = skipQuoted(text, index, caret);
                if (index < 0) {
                    return false;
                }
            } else if (c == '[') {
                int content = longBracketContentStart(text, index);
                if (content >= 0) {
                    int equals = content - index - 2;
                    int close = text.indexOf("]" + "=".repeat(equals) + "]", content);
                    if (close < 0 || close + equals + 2 > caret) {
                        return false;
                    }
                    index = close + equals + 2;
                } else {
                    index++;
                }
            } else {
                index++;
            }
        }
        return true;
    }

    private static int skipQuoted(String text, int start, int caret) {
        char quote = text.charAt(start);
        int index = start + 1;
        while (index < caret) {
            char c = text.charAt(index++);
            if (c == '\\') {
                if (index < caret) {
                    index++;
                }
            } else if (c == quote) {
                return index;
            } else if (c == '\n' || c == '\r') {
                return index;
            }
        }
        return -1;
    }

    private static int longBracketContentStart(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '[') {
            return -1;
        }
        int index = start + 1;
        while (index < text.length() && text.charAt(index) == '=') {
            index++;
        }
        return index < text.length() && text.charAt(index) == '[' ? index + 1 : -1;
    }
}

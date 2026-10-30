package me.kvothe.taleos.ui.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LuaCompleterTest {

    private final LuaCompleter completer = new LuaCompleter(false);

    @Test
    void completesKeywordsAndSafeGlobals() {
        assertEquals(List.of("return"), completer.complete("ret", 3).candidates());
        List<String> globals = completer.complete("", 0).candidates();
        assertTrue(globals.containsAll(List.of("print", "pcall", "fs", "tale", "term", "parallel")));
        assertFalse(globals.contains("io"));
        assertFalse(globals.contains("debug"));
        assertFalse(globals.contains("require"));
        assertFalse(globals.contains("turtle"));
    }

    @Test
    void completesKnownApiMembersWithoutEvaluatingExpressions() {
        assertTrue(completer.complete("fs.get", 6).candidates().contains("getSize"));
        assertTrue(completer.complete("os.", 3).candidates().containsAll(
                List.of("startTimer", "cancelTimer", "queueEvent", "pullEventRaw")));
        assertTrue(completer.complete("parallel.", 9).candidates()
                .containsAll(List.of("waitForAny", "waitForAll")));
        assertTrue(completer.complete("peripheral.", 11).candidates()
                .containsAll(List.of("getNames", "getType", "getMethods", "call", "wrap", "find")));
        assertTrue(completer.complete("rednet.", 7).candidates()
                .containsAll(List.of("send", "broadcast", "receive", "host", "lookup")));
        assertTrue(completer.complete("gps.", 4).candidates().containsAll(List.of("locate", "host")));
        for (String root : List.of("tale", "os", "term", "string", "math", "table", "coroutine")) {
            String text = root + ".";
            assertFalse(completer.complete(text, text.length()).candidates().isEmpty(), root);
        }
        assertNull(completer.complete("unknown.", 8));
        assertNull(completer.complete("getTable().", 11));
    }

    @Test
    void turtleMembersFollowComputerCapability() {
        assertNull(completer.complete("turtle.", 7));
        LuaCompleter turtleCompleter = new LuaCompleter(true);
        assertTrue(turtleCompleter.complete("turtle.turn", 11).candidates()
                .containsAll(List.of("turnLeft", "turnRight")));
        assertTrue(turtleCompleter.complete("turt", 4).candidates().contains("turtle"));
        assertTrue(turtleCompleter.complete("turtle.a", 8).candidates()
                .containsAll(List.of("attack", "attackUp", "attackDown")));
        assertEquals(List.of("craft"), turtleCompleter.complete("turtle.cra", 10).candidates());
    }

    @Test
    void doesNotCompleteInsideStringsOrComments() {
        for (String text : List.of("print('fs.li", "print(\"fs.li", "-- fs.li",
                "--[[ fs.li", "local s = [=[fs.li")) {
            assertNull(completer.complete(text, text.length()), text);
        }
        String afterString = "local s = 'done'\nfs.li";
        assertEquals(List.of("list"), completer.complete(afterString, afterString.length()).candidates());
    }

    @Test
    void luaCompletionInMiddlePreservesSuffix() {
        CompletionSession session = new CompletionSession(completer);
        session.reset("local x = fs.li .. 'tail'");
        session.valueChanged("local x = fs.lis .. 'tail'");

        assertTrue(session.complete());
        assertEquals("local x = fs.list .. 'tail'", session.getValue());
        assertEquals(17, session.getCaret());
    }

    @Test
    void completesTerminalRedirectAndWindowApis() {
        assertTrue(completer.complete("term.", 5).candidates().containsAll(
                List.of("blit", "clearLine", "scroll", "getCursorPos", "setCursorBlink",
                        "setTextColor", "setBackgroundColor", "isColor", "native", "current", "redirect")));
        assertEquals(List.of("create"), completer.complete("window.", 7).candidates());
        assertTrue(completer.complete("colo", 4).candidates().containsAll(List.of("colors", "colours")));
    }
}

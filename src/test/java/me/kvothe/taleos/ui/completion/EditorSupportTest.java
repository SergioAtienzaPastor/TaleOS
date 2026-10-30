package me.kvothe.taleos.ui.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EditorSupportTest {

    @Test
    void runCommandKeepsRelativeDirectories() {
        assertEquals("scripts/tools/demo", EditorSupport.runCommandFor("scripts/tools/demo.lua"));
        assertEquals("./scripts/demo", EditorSupport.runCommandFor("./scripts/demo.lua"));
        assertEquals("demo", EditorSupport.runCommandFor("demo.lua"));
        assertEquals("scripts/demo.txt", EditorSupport.runCommandFor("scripts/demo.txt"));
    }
}

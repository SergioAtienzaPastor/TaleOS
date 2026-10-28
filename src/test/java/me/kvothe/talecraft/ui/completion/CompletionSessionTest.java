package me.kvothe.talecraft.ui.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CompletionSessionTest {

    @Test
    void valueChangeInfersCaretFromMiddleInsertion() {
        CompletionSession session = new CompletionSession((text, caret) ->
                new Completion(caret - 2, caret, List.of("rom/")));
        session.reset("ls docs");
        session.valueChanged("ls rodocs");

        assertEquals(5, session.getCaret());
        assertTrue(session.complete());
        assertEquals("ls rom/docs", session.getValue());
        assertEquals(7, session.getCaret());
    }

    @Test
    void commonPrefixIsAppliedBeforeCyclingCandidates() {
        CompletionSession session = new CompletionSession((text, caret) ->
                new Completion(0, caret, List.of("print", "printf", "println")));
        session.reset("pri");

        assertTrue(session.complete());
        assertEquals("print", session.getValue());
        assertEquals("print", session.getCommonPrefix());
        assertEquals(List.of("print", "printf", "println"), session.getCandidates());
        assertTrue(session.complete());
        assertEquals("printf", session.getValue());
        assertTrue(session.complete());
        assertEquals("println", session.getValue());
        assertTrue(session.complete());
        assertEquals("print", session.getValue());
    }

    @Test
    void editingInvalidatesCandidateSnapshot() {
        CompletionSession session = new CompletionSession((text, caret) ->
                new Completion(0, caret, List.of("print", "println").stream()
                        .filter(candidate -> candidate.startsWith(text)).toList()));
        session.reset("pri");
        assertTrue(session.complete());
        assertFalse(session.getCandidates().isEmpty());

        session.valueChanged("println!");

        assertTrue(session.getCandidates().isEmpty());
        assertEquals("", session.getCommonPrefix());
        assertFalse(session.complete());
    }

    @Test
    void deletionInMiddleLeavesSuffixUntouched() {
        CompletionSession session = new CompletionSession((text, caret) ->
                new Completion(caret - 2, caret, List.of("cat")));
        session.reset("cat notes");
        session.valueChanged("ca notes");

        assertEquals(2, session.getCaret());
        assertTrue(session.complete());
        assertEquals("cat notes", session.getValue());
        assertEquals(3, session.getCaret());
    }
}

package me.kvothe.taleos.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Block-reversal rendering of the transcript (newest block first). */
class TranscriptViewTest {

    @Test
    void emptyTranscriptRendersEmpty() {
        assertEquals("", TranscriptView.newestFirst(""));
    }

    @Test
    void transcriptWithoutEchoesKeepsItsOrder() {
        assertEquals("boot\nlisto\n", TranscriptView.newestFirst("boot\nlisto\n"));
    }

    @Test
    void blocksRenderNewestFirstKeepingInternalOrder() {
        String transcript = "boot banner\n"
                + "\u0001tale> help\nline1\nline2\n"
                + "\u0001tale> echo hola\nhola\n";
        assertEquals("tale> echo hola\nhola\ntale> help\nline1\nline2\nboot banner\n",
                TranscriptView.newestFirst(transcript));
    }
}

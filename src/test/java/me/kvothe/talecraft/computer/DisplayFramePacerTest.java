package me.kvothe.talecraft.computer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisplayFramePacerTest {

    @Test
    void changedFrameWaitsForPreviousFrameToExpire() {
        DisplayFramePacer pacer = new DisplayFramePacer();
        assertTrue(pacer.needsFrame(33, false));
        pacer.onRendered(33);

        assertFalse(pacer.needsFrame(34, false));
        assertFalse(pacer.needsFrame(34, false));
        assertFalse(pacer.needsFrame(34, false));
        assertTrue(pacer.needsFrame(34, false));
    }

    @Test
    void unchangedFrameIsRefreshedBeforeItExpires() {
        DisplayFramePacer pacer = new DisplayFramePacer();
        assertTrue(pacer.needsFrame(33, false));
        pacer.onRendered(33);

        assertFalse(pacer.needsFrame(33, false));
        assertFalse(pacer.needsFrame(33, false));
        assertTrue(pacer.needsFrame(33, false));
    }

    @Test
    void blankCanvasIsNotRefreshedAndNextFrameStillWaits() {
        DisplayFramePacer pacer = new DisplayFramePacer();
        assertTrue(pacer.needsFrame(33, false));
        pacer.onRendered(33);

        assertFalse(pacer.needsFrame(0, true));
        assertFalse(pacer.needsFrame(34, false));
        assertFalse(pacer.needsFrame(34, false));
        assertTrue(pacer.needsFrame(34, false));
    }

    @Test
    void failedRenderIsRetriedWithoutMarkingFrameAsSent() {
        DisplayFramePacer pacer = new DisplayFramePacer();
        assertTrue(pacer.needsFrame(33, false));
        assertTrue(pacer.needsFrame(33, false));
        pacer.onRendered(33);
        assertFalse(pacer.needsFrame(33, false));
    }
}

package me.kvothe.taleos.computer;

/** Prevents a changed canvas from being sent while its previous frame is alive. */
final class DisplayFramePacer {

    static final int FRAME_TTL_TICKS = 4;
    private static final int HEARTBEAT_TICKS = 3;

    private int ticksUntilExpiry;
    private int ticksUntilHeartbeat;
    private long lastRenderedRevision = Long.MIN_VALUE;

    boolean needsFrame(long revision, boolean empty) {
        if (ticksUntilExpiry > 0) {
            ticksUntilExpiry--;
        }
        if (ticksUntilHeartbeat > 0) {
            ticksUntilHeartbeat--;
        }
        if (empty) {
            lastRenderedRevision = revision;
            ticksUntilHeartbeat = 0;
            return false;
        }
        boolean changed = revision != lastRenderedRevision;
        if (changed && ticksUntilExpiry > 0) {
            ticksUntilHeartbeat = 0;
            return false;
        }
        return changed || ticksUntilHeartbeat == 0;
    }

    void onRendered(long revision) {
        lastRenderedRevision = revision;
        ticksUntilExpiry = FRAME_TTL_TICKS;
        ticksUntilHeartbeat = HEARTBEAT_TICKS;
    }

    void reset() {
        ticksUntilExpiry = 0;
        ticksUntilHeartbeat = 0;
        lastRenderedRevision = Long.MIN_VALUE;
    }
}

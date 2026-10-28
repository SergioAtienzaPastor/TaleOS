package me.kvothe.talecraft.peripheral.speaker;

public final class SpeakerRateLimiter {
    public static final int MAX_SOUNDS_PER_TICK = 1;

    private int remaining = MAX_SOUNDS_PER_TICK;

    public void beginTick() {
        remaining = MAX_SOUNDS_PER_TICK;
    }

    public boolean tryAcquire() {
        if (remaining == 0) {
            return false;
        }
        remaining--;
        return true;
    }
}

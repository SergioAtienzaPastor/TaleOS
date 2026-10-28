package me.kvothe.talecraft.net;

final class DownloadLimiter {
    private final long bytesPerSecond;
    private double available;
    private long lastRefill;

    DownloadLimiter(long bytesPerSecond) {
        this.bytesPerSecond = bytesPerSecond;
        this.available = bytesPerSecond;
        this.lastRefill = System.nanoTime();
    }

    void acquire(int bytes) throws InterruptedException {
        if (bytesPerSecond == 0) {
            return;
        }
        synchronized (this) {
            while (true) {
                refill();
                if (available >= bytes) {
                    available -= bytes;
                    return;
                }
                double missing = bytes - available;
                long waitMillis = Math.max(1L, (long) Math.ceil(missing * 1000.0 / bytesPerSecond));
                wait(waitMillis);
            }
        }
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefill) / 1_000_000_000.0;
        available = Math.min(bytesPerSecond, available + elapsedSeconds * bytesPerSecond);
        lastRefill = now;
    }
}

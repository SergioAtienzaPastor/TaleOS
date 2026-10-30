package me.kvothe.taleos.turtle;

final class TurtleMoveAnimation {

    static final int TICKS = 7;
    static final long DURATION_NANOS = 350_000_000L;

    private TurtleMoveAnimation() {
    }

    static String stateFor(int x, int y, int z) {
        if (x != 0) {
            return x > 0 ? "MovePosX" : "MoveNegX";
        }
        if (y != 0) {
            return y > 0 ? "MovePosY" : "MoveNegY";
        }
        if (z != 0) {
            return z > 0 ? "MovePosZ" : "MoveNegZ";
        }
        throw new IllegalArgumentException("Movement cannot be zero");
    }

    static String turnStateFor(boolean left) {
        return left ? "TurnLeft" : "TurnRight";
    }

    static double remainingFraction(long elapsedNanos) {
        return 1.0 - Math.max(0.0, Math.min(1.0, elapsedNanos / (double) DURATION_NANOS));
    }

    static double remainingYaw(double initialYaw, long elapsedNanos) {
        return initialYaw * remainingFraction(elapsedNanos);
    }
}

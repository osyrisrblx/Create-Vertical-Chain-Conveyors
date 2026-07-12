package com.osyris.verticalchainconveyors;

/**
 * Small tick-driven state machine for retrying connection stats whose remote
 * block state was unavailable. Kept independent of a Level so the cross-chunk
 * retry policy can be tested without launching Minecraft.
 */
public final class VCCDeferredStatsRefresh {
    private final int retryInterval;
    private boolean pending;
    private boolean attemptedThisTick;
    private int retryDelay;

    public VCCDeferredStatsRefresh(int retryInterval) {
        if (retryInterval < 0)
            throw new IllegalArgumentException("retryInterval must be non-negative");
        this.retryInterval = retryInterval;
    }

    public void arm() {
        pending = true;
        attemptedThisTick = false;
        retryDelay = 0;
    }

    public boolean beginTick() {
        attemptedThisTick = false;
        if (!pending)
            return false;
        if (retryDelay > 0) {
            retryDelay--;
            return false;
        }
        attemptedThisTick = true;
        return true;
    }

    public void completeAttempt(boolean unresolvedTarget) {
        pending = unresolvedTarget;
        attemptedThisTick = false;
        retryDelay = unresolvedTarget ? retryInterval : 0;
    }

    public boolean attemptedThisTick() {
        return attemptedThisTick;
    }

    public boolean pending() {
        return pending;
    }

    public int retryDelay() {
        return retryDelay;
    }
}

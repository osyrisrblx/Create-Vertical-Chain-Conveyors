package com.osyris.verticalchainconveyors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VCCDeferredStatsRefreshTest {

    @Test
    void armRequestsAnImmediateAttempt() {
        VCCDeferredStatsRefresh refresh = new VCCDeferredStatsRefresh(20);

        refresh.arm();

        assertTrue(refresh.pending());
        assertTrue(refresh.beginTick());
        assertTrue(refresh.attemptedThisTick());
    }

    @Test
    void unresolvedTargetRetriesAtTheConfiguredInterval() {
        VCCDeferredStatsRefresh refresh = new VCCDeferredStatsRefresh(3);
        refresh.arm();
        assertTrue(refresh.beginTick());

        refresh.completeAttempt(true);

        assertEquals(3, refresh.retryDelay());
        assertFalse(refresh.beginTick());
        assertFalse(refresh.beginTick());
        assertFalse(refresh.beginTick());
        assertTrue(refresh.beginTick());
    }

    @Test
    void retriesDoNotExpireWhileTargetRemainsUnavailable() {
        VCCDeferredStatsRefresh refresh = new VCCDeferredStatsRefresh(2);
        refresh.arm();

        for (int attempt = 0; attempt < 100; attempt++) {
            while (!refresh.beginTick()) {
                assertTrue(refresh.pending());
            }
            refresh.completeAttempt(true);
        }

        assertTrue(refresh.pending());
    }

    @Test
    void successfulResolutionStopsFutureAttempts() {
        VCCDeferredStatsRefresh refresh = new VCCDeferredStatsRefresh(20);
        refresh.arm();
        assertTrue(refresh.beginTick());

        refresh.completeAttempt(false);

        assertFalse(refresh.pending());
        assertEquals(0, refresh.retryDelay());
        for (int tick = 0; tick < 100; tick++)
            assertFalse(refresh.beginTick());
    }

    @Test
    void rearmingClearsAnExistingDelay() {
        VCCDeferredStatsRefresh refresh = new VCCDeferredStatsRefresh(20);
        refresh.arm();
        refresh.beginTick();
        refresh.completeAttempt(true);

        refresh.arm();

        assertEquals(0, refresh.retryDelay());
        assertTrue(refresh.beginTick());
    }

    @Test
    void negativeRetryIntervalsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new VCCDeferredStatsRefresh(-1));
    }
}

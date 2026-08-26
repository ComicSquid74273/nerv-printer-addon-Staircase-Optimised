package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RasterRestockTransferPolicyTest {
    @Test
    void choosesFullestMatchingShulkerAndKeepsStableTieOrder() {
        assertEquals(
            1,
            RasterRestockTransferPolicy.fullestShulkerIndex(
                java.util.List.of(640, 1728, 1728, 1200)
            )
        );
        assertEquals(
            -1,
            RasterRestockTransferPolicy.fullestShulkerIndex(
                java.util.List.of(0, 0, 0)
            )
        );
    }

    @Test
    void prefersAFullStackThatFitsOverAnEarlyPartialStack() {
        assertEquals(
            1,
            RasterRestockTransferPolicy.bestSourceStackIndex(
                java.util.List.of(21, 64, 64),
                64
            )
        );
    }

    @Test
    void plansOnlyTheLookaheadMaterialBatch() {
        assertEquals(
            510,
            RasterRestockTransferPolicy.plannedAmount(
                9892, 510, 64, 2048, 1728
            )
        );
    }

    @Test
    void keepsOneUsefulStackForSparseLookaheadDemand() {
        assertEquals(
            64,
            RasterRestockTransferPolicy.plannedAmount(
                1000, 3, 64, 2048, 1728
            )
        );
    }

    @Test
    void capsBatchAtInventoryAndSourceAvailability() {
        assertEquals(
            90,
            RasterRestockTransferPolicy.plannedAmount(
                1000, 512, 64, 90, 1728
            )
        );
        assertEquals(
            37,
            RasterRestockTransferPolicy.plannedAmount(
                1000, 512, 64, 2048, 37
            )
        );
    }

    @Test
    void producesNoBatchWithoutDemandCapacityOrSource() {
        assertEquals(0, RasterRestockTransferPolicy.plannedAmount(0, 0, 64, 64, 64));
        assertEquals(0, RasterRestockTransferPolicy.plannedAmount(64, 64, 64, 0, 64));
        assertEquals(0, RasterRestockTransferPolicy.plannedAmount(64, 64, 64, 64, 0));
    }

    @Test
    void acceptsExactAndOvershootingStackConfirmation() {
        assertFalse(RasterRestockTransferPolicy.targetReached(509, 510));
        assertTrue(RasterRestockTransferPolicy.targetReached(510, 510));
        assertTrue(RasterRestockTransferPolicy.targetReached(512, 510));
    }

    @Test
    void reconcilesProgressAndBoundsNoProgressRetries() {
        assertEquals(
            RasterRestockTransferPolicy.Confirmation.PROGRESS,
            RasterRestockTransferPolicy.confirmAfterReopen(64, 128, 3, 3)
        );
        assertEquals(
            RasterRestockTransferPolicy.Confirmation.RETRY,
            RasterRestockTransferPolicy.confirmAfterReopen(64, 64, 2, 3)
        );
        assertEquals(
            RasterRestockTransferPolicy.Confirmation.FAIL,
            RasterRestockTransferPolicy.confirmAfterReopen(64, 64, 3, 3)
        );
    }

    @Test
    void completesReturnWhenReopenedSourceContainsTheShulker() {
        assertEquals(
            RasterRestockTransferPolicy.ReturnConfirmation.COMPLETE,
            RasterRestockTransferPolicy.confirmReturnAfterReopen(
                0, 1, 0, 3
            )
        );
    }

    @Test
    void retriesReturnWhileThePlayerStillOwnsTheShulker() {
        assertEquals(
            RasterRestockTransferPolicy.ReturnConfirmation.RETRY,
            RasterRestockTransferPolicy.confirmReturnAfterReopen(
                1, 0, 2, 3
            )
        );
        assertEquals(
            RasterRestockTransferPolicy.ReturnConfirmation.FAIL,
            RasterRestockTransferPolicy.confirmReturnAfterReopen(
                1, 0, 3, 3
            )
        );
    }

    @Test
    void failsClosedWhenTheReturnedShulkerIsAuthoritativelyMissing() {
        assertEquals(
            RasterRestockTransferPolicy.ReturnConfirmation.FAIL,
            RasterRestockTransferPolicy.confirmReturnAfterReopen(
                0, 0, 0, 3
            )
        );
    }
}

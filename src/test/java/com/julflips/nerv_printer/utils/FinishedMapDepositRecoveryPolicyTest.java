package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static com.julflips.nerv_printer.utils.FinishedMapDepositRecoveryPolicy.Decision.COMPLETE;
import static com.julflips.nerv_printer.utils.FinishedMapDepositRecoveryPolicy.Decision.FAIL;
import static com.julflips.nerv_printer.utils.FinishedMapDepositRecoveryPolicy.Decision.RETRY_DEPOSIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FinishedMapDepositRecoveryPolicyTest {
    @Test
    void retriesWhenDisconnectPrecededTheActualTransfer() {
        assertEquals(
            RETRY_DEPOSIT,
            FinishedMapDepositRecoveryPolicy.decide(
                MapHandoffStage.DEPOSIT_REQUESTED,
                true,
                false,
                false,
                false
            )
        );
    }

    @Test
    void acceptsTransportBeyondConnectedInputOnlyAfterRequest() {
        assertEquals(
            COMPLETE,
            FinishedMapDepositRecoveryPolicy.decide(
                MapHandoffStage.DEPOSIT_REQUESTED,
                false,
                false,
                false,
                false
            )
        );
        assertEquals(
            FAIL,
            FinishedMapDepositRecoveryPolicy.decide(
                MapHandoffStage.LOCKED_MAP_CONFIRMED,
                false,
                false,
                false,
                false
            )
        );
    }

    @Test
    void directDestinationEvidenceCompletesEitherLockedStage() {
        assertEquals(
            COMPLETE,
            FinishedMapDepositRecoveryPolicy.decide(
                MapHandoffStage.LOCKED_MAP_CONFIRMED,
                false,
                true,
                false,
                false
            )
        );
    }

    @Test
    void playerCopyWinsOverDestinationDuplicate() {
        assertEquals(
            RETRY_DEPOSIT,
            FinishedMapDepositRecoveryPolicy.decide(
                MapHandoffStage.DEPOSIT_REQUESTED,
                true,
                true,
                false,
                false
            )
        );
    }

    @Test
    void ambiguityOrExtraSuppliesFailsClosed() {
        assertEquals(
            FAIL,
            FinishedMapDepositRecoveryPolicy.decide(
                MapHandoffStage.DEPOSIT_REQUESTED,
                false,
                false,
                true,
                false
            )
        );
        assertEquals(
            FAIL,
            FinishedMapDepositRecoveryPolicy.decide(
                MapHandoffStage.DEPOSIT_REQUESTED,
                false,
                false,
                false,
                true
            )
        );
    }
}

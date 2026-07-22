package com.julflips.nerv_printer.utils;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedUTraversalMovementTest {
    @Test
    void reversesThroughTheSameVerifiedSupportRouteForPlacementBacktrack() {
        List<BlockPos> supports = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(0, 64, 1),
            new BlockPos(0, 64, 2),
            new BlockPos(1, 64, 2)
        );

        OrderedUTraversalMovement.Progress progress =
            OrderedUTraversalMovement.resolve(
                supports,
                2,
                -1,
                0.5,
                1.5,
                ignored -> true
            );

        assertEquals(1, progress.currentIndex());
        assertEquals(
            OrderedUTraversalMovement.MovementStatus.READY,
            progress.movement().status()
        );
        assertEquals(supports.get(0), progress.movement().requiredSupport());
        assertEquals(
            0,
            OrderedUTraversalMovement.steeringGoalIndex(
                supports,
                1,
                -1
            )
        );
    }

    @Test
    void printingAndTeardownResolveTheSameSupportProgress() {
        List<BlockPos> route = List.of(
            new BlockPos(0, 10, -2),
            new BlockPos(0, 10, -1),
            new BlockPos(0, 11, 0),
            new BlockPos(0, 10, 1)
        );
        Set<BlockPos> ready = Set.copyOf(route);

        var progress = OrderedUTraversalMovement.resolve(
            route,
            1,
            0.5,
            0.5,
            ready::contains
        );

        assertEquals(2, progress.currentIndex());
        assertEquals(
            OrderedUTraversalMovement.MovementStatus.READY,
            progress.movement().status()
        );
        assertEquals(route.get(3), progress.movement().requiredSupport());
    }

    @Test
    void reconcilesOneServerCorrectedSupportWithoutLeavingTheRoute() {
        List<BlockPos> route = List.of(
            new BlockPos(0, 10, 0),
            new BlockPos(0, 10, 1),
            new BlockPos(0, 10, 2),
            new BlockPos(0, 10, 3)
        );

        var progress = OrderedUTraversalMovement.resolve(
            route,
            2,
            0.5,
            1.5,
            ignored -> true
        );

        assertEquals(1, progress.currentIndex());
        assertTrue(progress.reconciledPreviousSupport());
        assertEquals(
            OrderedUTraversalMovement.MovementStatus.READY,
            progress.movement().status()
        );
        assertEquals(route.get(2), progress.movement().requiredSupport());
    }

    @Test
    void completedRouteDoesNotAuthorizeForwardMovementPastItsEnd() {
        var decision = OrderedUTraversalMovement.decideMovement(
            List.of("first", "last"),
            1,
            ignored -> true
        );

        assertEquals(
            OrderedUTraversalMovement.MovementStatus.COMPLETE,
            decision.status()
        );
        assertFalse(decision.mayMove());
    }

    @Test
    void finalCellCompletesWithoutASeparateGroundedHandoff() {
        assertEquals(
            OrderedUTraversalMovement.EndpointProgress.APPROACHING,
            OrderedUTraversalMovement.endpointProgress(false)
        );
        assertEquals(
            OrderedUTraversalMovement.EndpointProgress.REACHED,
            OrderedUTraversalMovement.endpointProgress(true)
        );
    }

    @Test
    void recoveryReversalIsDetectedWithoutTreatingCornersAsPivots() {
        List<BlockPos> recovery = List.of(
            new BlockPos(0, 10, 0),
            new BlockPos(0, 11, 1),
            new BlockPos(0, 10, 2),
            new BlockPos(0, 11, 1),
            new BlockPos(0, 10, 0)
        );
        List<BlockPos> corner = List.of(
            new BlockPos(0, 10, 0),
            new BlockPos(0, 10, 1),
            new BlockPos(1, 10, 1)
        );

        assertTrue(
            OrderedUTraversalMovement.isRouteReversal(recovery, 2)
        );
        assertTrue(
            OrderedUTraversalMovement.hasRouteReversalWithin(
                recovery,
                0,
                1,
                2
            )
        );
        assertFalse(
            OrderedUTraversalMovement.hasRouteReversalWithin(
                recovery,
                0,
                1,
                1
            )
        );
        assertFalse(
            OrderedUTraversalMovement.isRouteReversal(corner, 1)
        );
    }

    @Test
    void teardownTurnaroundUsesOneHorizontalBrakeTick() {
        assertEquals(
            OrderedUTraversalMovement.TurnaroundProgress
                .NOT_A_TURNAROUND,
            OrderedUTraversalMovement.turnaroundProgress(
                false,
                false
            )
        );
        assertEquals(
            OrderedUTraversalMovement.TurnaroundProgress
                .BRAKE_AND_MARK_SETTLED,
            OrderedUTraversalMovement.turnaroundProgress(
                true,
                false
            )
        );
        assertEquals(
            OrderedUTraversalMovement.TurnaroundProgress.READY,
            OrderedUTraversalMovement.turnaroundProgress(
                true,
                true
            )
        );
    }

    @Test
    void recoveryMayOwnTheSameExteriorSupportForEntryAndExit() {
        BlockPos exterior = new BlockPos(0, 10, -1);
        List<BlockPos> recovery = List.of(
            exterior,
            new BlockPos(0, 10, 0),
            new BlockPos(0, 10, 1),
            new BlockPos(0, 10, 0),
            exterior
        );

        assertTrue(
            OrderedUTraversalMovement.ownsStructuralEndpoint(
                recovery,
                exterior
            )
        );
        assertFalse(
            OrderedUTraversalMovement.ownsStructuralEndpoint(
                recovery,
                new BlockPos(0, 10, 1)
            )
        );
    }

    @Test
    void steeringUsesStraightSegmentsInsteadOfPerBlockCenters() {
        List<BlockPos> route = List.of(
            new BlockPos(0, 10, -1),
            new BlockPos(0, 11, 0),
            new BlockPos(0, 10, 1),
            new BlockPos(1, 10, 1),
            new BlockPos(2, 11, 1),
            new BlockPos(2, 11, 0)
        );

        assertEquals(
            2,
            OrderedUTraversalMovement.steeringGoalIndex(route, 0)
        );
        assertEquals(
            4,
            OrderedUTraversalMovement.steeringGoalIndex(route, 2)
        );
        assertEquals(
            5,
            OrderedUTraversalMovement.steeringGoalIndex(route, 4)
        );
    }

    @Test
    void approachSupportIsDerivedFromEitherEntryDirection() {
        assertEquals(
            new BlockPos(4, 20, -2),
            OrderedUTraversalMovement.entryApproachSupport(
                new BlockPos(4, 20, -1),
                new BlockPos(4, 21, 0)
            )
        );
        assertEquals(
            new BlockPos(9, 20, -2),
            OrderedUTraversalMovement.entryApproachSupport(
                new BlockPos(9, 20, -1),
                new BlockPos(9, 19, 0)
            )
        );
        assertEquals(
            new BlockPos(9, 30, 10),
            OrderedUTraversalMovement.entryApproachSupport(
                new BlockPos(10, 30, 10),
                new BlockPos(11, 30, 10)
            )
        );
        assertEquals(
            new BlockPos(11, 30, 10),
            OrderedUTraversalMovement.entryApproachSupport(
                new BlockPos(10, 30, 10),
                new BlockPos(9, 30, 10)
            )
        );
    }

    @Test
    void departureSupportIsDerivedFromEitherExitDirection() {
        assertEquals(
            new BlockPos(4, 20, -2),
            OrderedUTraversalMovement.exitDepartureSupport(
                new BlockPos(4, 20, -1),
                new BlockPos(4, 21, 0)
            )
        );
        assertEquals(
            new BlockPos(9, 20, 12),
            OrderedUTraversalMovement.exitDepartureSupport(
                new BlockPos(9, 20, 11),
                new BlockPos(9, 19, 10)
            )
        );
        assertEquals(
            new BlockPos(9, 30, 10),
            OrderedUTraversalMovement.exitDepartureSupport(
                new BlockPos(10, 30, 10),
                new BlockPos(11, 30, 10)
            )
        );
        assertEquals(
            new BlockPos(11, 30, 10),
            OrderedUTraversalMovement.exitDepartureSupport(
                new BlockPos(10, 30, 10),
                new BlockPos(9, 30, 10)
            )
        );
    }
}

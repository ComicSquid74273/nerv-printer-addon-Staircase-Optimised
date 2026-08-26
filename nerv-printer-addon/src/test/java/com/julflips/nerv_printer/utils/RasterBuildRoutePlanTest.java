package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterBuildRoutePlanTest {

    @Test
    void checkpointResumeRejectsAStaleRouteCursorBehindTheFrontier() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                java.util.List.of(
                    slice(0, 1, 0, 10, 10),
                    slice(1, -1, 1, 10, 10),
                    slice(2, 1, 2, 10, 10)
                ),
                6.0
            ),
            0,
            2,
            0,
            1,
            3.5
        );
        String unfinished = "2:2:1";
        int deadline = route.deadline(unfinished);
        assertEquals(
            route.replayIndex(deadline, 3),
            route.resumeIndexFor(unfinished, 0, 3)
        );
    }

    @Test
    void checkpointResumeRetainsAnInWindowPhysicalCursor() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                java.util.List.of(
                    slice(0, 1, 0, 10, 10),
                    slice(1, -1, 1, 10, 10),
                    slice(2, 1, 2, 10, 10)
                ),
                6.0
            ),
            0,
            2,
            0,
            1,
            3.5
        );
        String unfinished = "2:2:1";
        int deadline = route.deadline(unfinished);
        int replay = route.replayIndex(deadline, 3);
        int retained = Math.min(deadline, replay + 1);
        assertEquals(
            retained,
            route.resumeIndexFor(unfinished, retained, 3)
        );
    }
    @Test
    void compilesOnlyAdjacentSideLaneSegmentsWithEveryTargetDeadline() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                List.of(
                    slice(0, 1, 0, 1, 50, 97),
                    slice(0, 1, 1, 2, 51, 98)
                ),
                6.0
            ),
            0, 2, 0, 1, 3.5
        );

        assertEquals(6, route.deadlineByTarget().size());
        assertEquals(6, new HashSet<>(route.deadlineByTarget().keySet()).size());
        assertTrue(route.points().stream().anyMatch(point ->
            point.phase() == RasterBuildRoutePlan.Phase.BAND_CONNECTOR));
        assertTrue(route.points().stream()
            .filter(RasterBuildRoutePlan.Point::constructionPoint)
            .allMatch(point -> point.eyeY() >= 1.5 && point.eyeY() <= 98.5));

        for (int index = 1; index < route.points().size(); index++) {
            var previous = route.points().get(index - 1);
            var current = route.points().get(index);
            int changedAxes = 0;
            if (Math.abs(previous.x() - current.x()) > 1.0e-6) changedAxes++;
            if (Math.abs(previous.eyeY() - current.eyeY()) > 1.0e-6) changedAxes++;
            if (Math.abs(previous.z() - current.z()) > 1.0e-6) changedAxes++;
            assertTrue(changedAxes <= 2, "No route segment may move on all three axes.");
            if (changedAxes == 2
                && Math.abs(previous.eyeY() - current.eyeY()) < 1.0e-6) {
                assertEquals(
                    Math.abs(previous.x() - current.x()),
                    Math.abs(previous.z() - current.z()),
                    1.0e-6,
                    "Horizontal diagonals must be exactly 45 degrees."
                );
            }
            double distance = Math.sqrt(
                Math.pow(previous.x() - current.x(), 2.0)
                    + Math.pow(previous.eyeY() - current.eyeY(), 2.0)
                    + Math.pow(previous.z() - current.z(), 2.0)
            );
            assertTrue(distance > 1.0e-6, "Compiled route points must be distinct.");
            assertTrue(
                distance <= RasterBuildRoutePlan.MAXIMUM_TRANSIT_SEGMENT
                    + 1.0e-6,
                "Every retained route segment must be bounded."
            );
        }
    }

    @Test
    void alternatingBandChangesShiftXAtTheCompletedEndpoint() {
        ArrayList<RasterThreeLanePathPlanner.Slice<String>> slices =
            new ArrayList<>();
        slices.add(slice(0, 1, 0, 10, 10, 10));
        slices.add(slice(0, 1, 1, 10, 10, 10));
        slices.add(slice(1, -1, 1, 10, 10, 10));
        slices.add(slice(1, -1, 0, 10, 10, 10));

        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(slices, 6.0),
            0, 5, 0, 1, 3.5
        );

        assertTrue(route.points().stream().anyMatch(point ->
            point.phase() == RasterBuildRoutePlan.Phase.BAND_CONNECTOR
                && (point.z() < 0 || point.z() > 2)));
        assertFalse(route.points().stream()
            .filter(point -> point.phase()
                == RasterBuildRoutePlan.Phase.BAND_CONNECTOR)
            .toList().isEmpty());
        assertEquals(1, route.lateralDirection());
        for (int index = 1; index < route.points().size(); index++) {
            var previous = route.points().get(index - 1);
            var current = route.points().get(index);
            if (Math.abs(previous.x() - current.x()) <= 1.0e-6) continue;
            if (previous.band() == current.band()) continue;
            assertTrue(
                previous.z() > 2.0 && current.z() > 2.0,
                "This southbound row must shift X beyond its completed south endpoint."
            );
        }
    }

    @Test
    void sameRowHeightChangesAreContinuousForwardRamps() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                List.of(
                    singleTargetSlice(0, 1, 2, 10, 0),
                    singleTargetSlice(0, 1, 2, 6, 1),
                    singleTargetSlice(0, 1, 2, 14, 2)
                ),
                5.90
            ),
            0, 4, 0, 2, 3.5
        );
        assertTrue(route.points().stream().anyMatch(point ->
            point.phase() == RasterBuildRoutePlan.Phase.HEIGHT_CONNECTOR));
        for (int index = 1; index < route.points().size(); index++) {
            var previous = route.points().get(index - 1);
            var current = route.points().get(index);
            if (previous.band() != current.band()
                || Math.abs(previous.eyeY() - current.eyeY()) <= 1.0e-6) {
                continue;
            }
            assertNotEquals(
                previous.z(),
                current.z(),
                "An in-row height change must make forward progress instead of stopping vertically."
            );
        }
    }

    @Test
    void bandHeightAndXChangesOccurOnlyBeyondCompletedEndpoint() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                List.of(
                    singleTargetSlice(0, 1, 0, 10, 0),
                    singleTargetSlice(0, 1, 0, 10, 1),
                    singleTargetSlice(1, -1, 1, 30, 1),
                    singleTargetSlice(1, -1, 1, 30, 0)
                ),
                5.90
            ),
            0, 1, 0, 1, 3.5
        );
        for (int index = 1; index < route.points().size(); index++) {
            var previous = route.points().get(index - 1);
            var current = route.points().get(index);
            if (previous.band() == current.band()) continue;
            boolean changesX = Math.abs(previous.x() - current.x()) > 1.0e-6;
            boolean changesY = Math.abs(previous.eyeY() - current.eyeY()) > 1.0e-6;
            if (!changesX && !changesY) continue;
            assertTrue(previous.z() > 2.0 && current.z() > 2.0,
                "Band X/Y changes must remain beyond the completed south endpoint.");
        }
    }

    @Test
    void forwardEgressNeverReversesThroughCompletedRow() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                List.of(
                    singleTargetSlice(0, 1, 0, 10, 0),
                    singleTargetSlice(0, 1, 0, 10, 1),
                    singleTargetSlice(1, -1, 1, 10, 1),
                    singleTargetSlice(1, -1, 1, 10, 0)
                ),
                5.90
            ),
            0, 1, 0, 1, 3.5
        );
        int retained = route.deadline("0:0:0");
        var egress = route.forwardEgressAlongRoute(retained);
        assertFalse(egress.isEmpty());
        assertTrue(egress.getLast().z() > 2.0);
        double previousZ = route.points().get(retained).z();
        for (var point : egress) {
            assertTrue(point.z() + 1.0e-6 >= previousZ);
            previousZ = point.z();
        }
    }

    @Test
    void negativeXTraversalKeepsEveryXShiftBeyondTheCompletedNorthEndpoint() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                List.of(
                    slice(0, 1, 2, 10, 10),
                    slice(1, 1, 1, 10, 10),
                    slice(2, 1, 0, 10, 10)
                ),
                5.90
            ),
            0, 2, 0, 1, 3.5
        );
        assertEquals(-1, route.lateralDirection());
        for (int index = 1; index < route.points().size(); index++) {
            var previous = route.points().get(index - 1);
            var current = route.points().get(index);
            if (Math.abs(previous.x() - current.x()) <= 1.0e-6
                || previous.band() == current.band()) {
                continue;
            }
            assertTrue(previous.z() < 0.0 && current.z() < 0.0);
        }
    }

    @Test
    void egressAndEntryUseTheGlobalSideLaneTransitAltitude() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                List.of(
                    slice(0, 1, 0, 1, 97, 97),
                    slice(0, 1, 1, 1, 97, 97)
                ),
                6.0
            ),
            0, 2, 0, 1, 3.5
        );
        int retained = route.deadline("0:0:0");
        var egress = route.egress(retained);
        var entry = route.entry(retained);

        assertTrue(egress.stream().allMatch(point ->
            point.eyeY() == route.minimumEyeY()));
        assertEquals(route.exteriorAnchor(retained).z(), egress.getLast().z());
        assertEquals(route.points().get(retained), entry.getLast());
        assertEquals(0, route.replayIndexFor("0:0:0", 3));

        int later = route.points().size() - 1;
        var retainedEgress = route.egressAlongRoute(later);
        var retainedAccess = route.previousExteriorAccess(later);
        assertEquals(retainedAccess, retainedEgress.getLast());
        assertEquals(route.points().get(later - 1), retainedEgress.getFirst());
        var retainedEntry = route.entryAlongRoute(later);
        assertEquals(
            retainedEgress.reversed().stream().toList(),
            retainedEntry.subList(0, retainedEntry.size() - 1)
        );
        assertEquals(route.points().get(later), retainedEntry.getLast());
    }

    @Test
    void retainedEntryUsesItsPairedAccessEvenWhenTheNearestSideDiffers() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                List.of(
                    slice(0, 1, 0, 1, 97, 97),
                    slice(0, 1, 1, 1, 97, 97),
                    slice(1, 1, 0, 1, 1, 1),
                    slice(1, 1, 1, 1, 1, 1)
                ),
                6.0
            ),
            0, 2, 0, 1, 3.5
        );
        int retained = java.util.stream.IntStream.range(
            0,
            route.points().size()
        ).filter(index -> !route.exteriorAnchor(index).equals(
            route.previousExteriorAccess(index)
        )).findFirst().orElseThrow();

        var pairedAccess = route.previousExteriorAccess(retained);
        assertNotEquals(route.exteriorAnchor(retained), pairedAccess);
        assertEquals(pairedAccess, route.entryAlongRoute(retained).getFirst());
    }

    @Test
    void repairExitCanUseTheNearestExteriorInsteadOfReversingTheLine() {
        var route = RasterBuildRoutePlan.create(
            RasterThreeLanePathPlanner.create(
                List.of(
                    slice(0, 1, 0, 1, 97, 97),
                    slice(0, 1, 1, 1, 97, 97),
                    slice(1, 1, 0, 1, 1, 1),
                    slice(1, 1, 1, 1, 1, 1)
                ),
                6.0
            ),
            0, 2, 0, 1, 3.5
        );
        int retained = java.util.stream.IntStream.range(
            0,
            route.points().size()
        ).filter(index -> {
            var point = route.points().get(index);
            return Math.abs(route.exteriorAnchor(index).z() - point.z())
                < Math.abs(route.previousExteriorAccess(index).z() - point.z());
        }).findFirst().orElseThrow();
        var point = route.points().get(retained);

        assertTrue(
            Math.abs(route.exteriorAnchor(retained).z() - point.z())
                < Math.abs(route.previousExteriorAccess(retained).z() - point.z())
        );
    }

    @Test
    void primaryBuildLegsAlternateNorthSouthAndDeadlinesRemainMonotonic() {
        ArrayList<RasterThreeLanePathPlanner.Slice<String>> slices =
            new ArrayList<>();
        for (int band = 0; band < 2; band++) {
            for (int offset = 0; offset < 6; offset++) {
                int z = (band & 1) == 0 ? -1 + offset : 4 - offset;
                slices.add(northSouthSlice(
                    band,
                    z,
                    (band & 1) == 0 ? 1 : -1
                ));
            }
        }
        var strips = RasterThreeLanePathPlanner.create(slices, 6.0);
        var route = RasterBuildRoutePlan.create(
            strips,
            0, 5, -1, 4, 3.5
        );

        for (int band = 0; band < 2; band++) {
            int expectedBand = band;
            var primary = route.points().stream()
                .filter(point -> point.constructionPoint()
                    && point.band() == expectedBand
                    && point.pass() == 0)
                .toList();
            assertEquals(6, primary.size());
            List<Double> expected = (band & 1) == 0
                ? List.of(-0.5, 0.5, 1.5, 2.5, 3.5, 4.5)
                : List.of(4.5, 3.5, 2.5, 1.5, 0.5, -0.5);
            assertEquals(expected,
                primary.stream().map(RasterBuildRoutePlan.Point::z).toList());
            int expectedDirection = (band & 1) == 0 ? 1 : -1;
            assertTrue(primary.stream().allMatch(point ->
                point.direction() == expectedDirection));
        }

        int previousDeadline = -1;
        for (var assignment : strips.assignments()) {
            int deadline = route.deadline(assignment.target().payload());
            assertTrue(deadline >= previousDeadline);
            previousDeadline = deadline;
        }
        int retained = route.points().size() - 1;
        assertTrue(route.replayIndex(retained, 3) >= retained - 3);
    }

    @Test
    void compilesCompleteCliffHeavyMapBeforeRuntime() {
        ArrayList<RasterThreeLanePathPlanner.Slice<String>> slices =
            new ArrayList<>();
        int targets = 0;
        for (int band = 0; band < 43; band++) {
            for (int z = -1; z < 128; z++) {
                ArrayList<RasterThreeLanePathPlanner.Target<String>> cells =
                    new ArrayList<>();
                for (int lane = 0; lane < 3; lane++) {
                    int x = band * 3 + lane;
                    if (x >= 128) continue;
                    int y = lane == 0 ? 1 : lane == 1 ? 50 : 97;
                    cells.add(new RasterThreeLanePathPlanner.Target<>(
                        x,
                        y,
                        z,
                        x + ":" + z
                    ));
                    targets++;
                }
                slices.add(new RasterThreeLanePathPlanner.Slice<>(
                    band,
                    1,
                    cells
                ));
            }
        }
        var strips = RasterThreeLanePathPlanner.create(slices, 6.0);
        var route = RasterBuildRoutePlan.create(
            strips,
            0, 127, -1, 127, 3.5
        );

        assertEquals(128 * 129, targets);
        assertEquals(targets, route.deadlineByTarget().size());
        assertTrue(route.points().size() < 40_000);
        assertTrue(route.points().stream()
            .filter(RasterBuildRoutePlan.Point::constructionPoint)
            .allMatch(point -> point.direction() == 1
                || point.pass() == 1));
    }

    private static RasterThreeLanePathPlanner.Slice<String> slice(
        int band,
        int direction,
        int x,
        int... heights
    ) {
        ArrayList<RasterThreeLanePathPlanner.Target<String>> targets =
            new ArrayList<>();
        for (int lane = 0; lane < heights.length; lane++) {
            targets.add(new RasterThreeLanePathPlanner.Target<>(
                x,
                heights[lane],
                lane,
                band + ":" + x + ":" + lane
            ));
        }
        return new RasterThreeLanePathPlanner.Slice<>(
            band,
            direction,
            targets
        );
    }

    private static RasterThreeLanePathPlanner.Slice<String>
        northSouthSlice(int band, int z, int direction) {
        ArrayList<RasterThreeLanePathPlanner.Target<String>> targets =
            new ArrayList<>();
        for (int lane = 0; lane < 3; lane++) {
            int x = band * 3 + lane;
            targets.add(new RasterThreeLanePathPlanner.Target<>(
                x,
                10,
                z,
                band + ":" + x + ":" + z
            ));
        }
        return new RasterThreeLanePathPlanner.Slice<>(
            band,
            direction,
            targets
        );
    }

    private static RasterThreeLanePathPlanner.Slice<String>
        singleTargetSlice(int band, int direction, int x, int y, int z) {
        return new RasterThreeLanePathPlanner.Slice<>(
            band,
            direction,
            List.of(new RasterThreeLanePathPlanner.Target<>(
                x,
                y,
                z,
                band + ":" + x + ":" + z
            ))
        );
    }
}

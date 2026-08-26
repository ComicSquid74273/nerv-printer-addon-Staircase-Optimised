package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterExteriorIngressPlanTest {
    private static final RasterExteriorIngressPlan.Bounds MAP =
        new RasterExteriorIngressPlan.Bounds(-64.0, 64.0, -320.0, -191.0);

    @Test
    void flatExteriorTravelKeepsYUntilTheFinalEndpointHandoff() {
        var start = new RasterExteriorIngressPlan.Point(
            52.0, -31.7, -324.5
        );
        var destination = new RasterExteriorIngressPlan.Point(
            -66.5, -35.0, -197.5
        );
        var route = RasterExteriorIngressPlan.flatWestExterior(
            start, destination, MAP, 3.5
        );
        assertEquals(
            new RasterExteriorIngressPlan.Point(-67.5, -31.7, -324.5),
            route.waypoints().get(0)
        );
        assertEquals(
            new RasterExteriorIngressPlan.Point(-67.5, -31.7, -197.5),
            route.waypoints().get(1)
        );
        assertEquals(
            new RasterExteriorIngressPlan.Point(-66.5, -31.7, -197.5),
            route.waypoints().get(2)
        );
        assertEquals(destination, route.waypoints().get(3));
    }

    @Test
    void directAerialLogisticsRisesThenCruisesThenDescends() {
        var start = new RasterExteriorIngressPlan.Point(
            10.0, -32.7, -325.0
        );
        var landing = new RasterExteriorIngressPlan.Point(
            -66.5, -35.0, -194.5
        );

        var route = RasterExteriorIngressPlan.directAerial(
            start, landing, -10.0
        );

        assertEquals(3, route.size());
        assertEquals(
            new RasterExteriorIngressPlan.Point(10.0, -10.0, -325.0),
            route.get(0)
        );
        assertEquals(
            new RasterExteriorIngressPlan.Point(-66.5, -10.0, -194.5),
            route.get(1)
        );
        assertEquals(landing, route.get(2));
    }

    @Test
    void deepExteriorRecoveryRisesToMapClearCruiseBeforeCrossing() {
        assertEquals(
            -14.0,
            RasterExteriorIngressPlan.mapClearCruiseY(-37.0, -14.0)
        );
        assertEquals(
            -10.0,
            RasterExteriorIngressPlan.mapClearCruiseY(-10.0, -14.0)
        );
    }

    @Test
    void obstructedRiseCanMoveFartherOutsideBeforeAscending() {
        var start = new RasterExteriorIngressPlan.Point(-41.5, -37.0, -189.9);
        var candidates = RasterExteriorIngressPlan.verticalStagingCandidates(
            start,
            MAP,
            3.5,
            2.0
        );

        assertEquals(-185.5, candidates.getFirst().z());
        assertEquals(start.x(), candidates.getFirst().x());
        assertTrue(candidates.stream().allMatch(point ->
            point.x() < MAP.minimumX()
                || point.x() > MAP.maximumX()
                || point.z() < MAP.minimumZ()
                || point.z() > MAP.maximumZ()
        ));
    }

    @Test
    void risesBeforeMovingOutsideAndDescendsOnlyAtAccess() {
        var start = new RasterExteriorIngressPlan.Point(-66.5, -35.4, -189.1);
        var access = new RasterExteriorIngressPlan.Point(-63.5, -37.0, -323.5);

        var route = RasterExteriorIngressPlan.candidates(
            start, access, MAP, -15.0, 3.5
        ).getFirst().waypoints();

        assertEquals(start.x(), route.get(0).x());
        assertEquals(start.z(), route.get(0).z());
        assertEquals(-15.0, route.get(0).y());
        assertEquals(-15.0, route.get(1).y());
        assertEquals(-15.0, route.get(route.size() - 2).y());
        assertEquals(access, route.getLast());
    }

    @Test
    void deterministicSouthwestRouteRisesThenMovesNorthThenEast() {
        var start = new RasterExteriorIngressPlan.Point(-66.5, -35.4, -197.2);
        var access = new RasterExteriorIngressPlan.Point(-53.5, -37.0, -324.5);

        var route = RasterExteriorIngressPlan.northThenEast(
            start, access, -31.0
        ).waypoints();

        assertEquals(4, route.size());
        assertEquals(new RasterExteriorIngressPlan.Point(-66.5, -31.0, -197.2), route.get(0));
        assertEquals(new RasterExteriorIngressPlan.Point(-66.5, -31.0, -324.5), route.get(1));
        assertEquals(new RasterExteriorIngressPlan.Point(-53.5, -31.0, -324.5), route.get(2));
        assertEquals(access, route.get(3));
    }

    @Test
    void descendsToTheFirstSafeUnderMapHeightBeforeEnteringHorizontally() {
        var exterior = new RasterExteriorIngressPlan.Point(
            -41.5, -35.7, -324.5
        );
        var interior = new RasterExteriorIngressPlan.Point(
            -41.5, -37.5, -320.5
        );

        var handoff = RasterExteriorIngressPlan
            .descendBeforeHorizontalEntry(exterior, interior);

        assertEquals(2, handoff.size());
        assertEquals(
            new RasterExteriorIngressPlan.Point(-41.5, -37.5, -324.5),
            handoff.getFirst()
        );
        assertEquals(interior, handoff.getLast());
    }

    @Test
    void movesAtTheLowerSafeHeightBeforeAscending() {
        var start = new RasterExteriorIngressPlan.Point(
            -41.5, -37.5375, -311.5
        );
        var higher = new RasterExteriorIngressPlan.Point(
            -41.5, -36.5375, -310.5
        );

        var handoff = RasterExteriorIngressPlan.safeVerticalDogleg(
            start,
            higher
        );

        assertEquals(2, handoff.size());
        assertEquals(
            new RasterExteriorIngressPlan.Point(-41.5, -37.5375, -310.5),
            handoff.getFirst()
        );
        assertEquals(higher, handoff.getLast());
    }

    @Test
    void northSouthTravelRemainsOutsideTheMapFootprint() {
        var start = new RasterExteriorIngressPlan.Point(-66.5, -35.4, -189.1);
        var access = new RasterExteriorIngressPlan.Point(-63.5, -37.0, -323.5);

        var candidate = RasterExteriorIngressPlan.candidates(
            start, access, MAP, -15.0, 3.5
        ).getFirst();
        double outsideX = candidate.waypoints().get(1).x();

        assertEquals(RasterExteriorIngressPlan.Side.WEST, candidate.side());
        assertTrue(outsideX < MAP.minimumX());
        assertEquals(outsideX, candidate.waypoints().get(2).x());
        assertEquals(access.z(), candidate.waypoints().get(2).z());
    }

    @Test
    void choosesTheShorterExteriorSideWithoutCrossingTheMap() {
        var eastStart = new RasterExteriorIngressPlan.Point(68.0, -30.0, -190.0);
        var access = new RasterExteriorIngressPlan.Point(60.5, -37.0, -323.5);

        var routes = RasterExteriorIngressPlan.candidates(
            eastStart, access, MAP, -15.0, 3.5
        );

        assertEquals(RasterExteriorIngressPlan.Side.EAST, routes.getFirst().side());
        assertEquals(RasterExteriorIngressPlan.Side.WEST, routes.getLast().side());
    }
}

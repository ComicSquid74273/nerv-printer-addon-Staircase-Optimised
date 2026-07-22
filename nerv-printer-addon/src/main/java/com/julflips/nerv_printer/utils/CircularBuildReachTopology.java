package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Translation-invariant, per-block reach schedule for circular printing.
 *
 * <p>The expensive geometry pass is compiled once for an NBT/reach profile.
 * Runtime inventory planning only filters this immutable schedule against the
 * authoritative missing blocks. A target does not need its entire owning U to
 * be reachable: every individually reachable foreign block is retained.</p>
 */
public final class CircularBuildReachTopology {
    public static final int SCHEMA_VERSION = 1;
    public static final int ALGORITHM_VERSION = 1;

    private CircularBuildReachTopology() {
    }

    public record Route(
        int routeIndex,
        List<BlockReachWindow.Cell> orderedSupports,
        List<BlockReachWindow.Cell> orderedTargets
    ) {
        public Route {
            if (routeIndex < 0) {
                throw new IllegalArgumentException(
                    "A build route index cannot be negative."
                );
            }
            orderedSupports = requireCells(
                orderedSupports,
                "orderedSupports"
            );
            orderedTargets = requireCells(
                orderedTargets,
                "orderedTargets"
            );
            if (orderedSupports.isEmpty() || orderedTargets.isEmpty()) {
                throw new IllegalArgumentException(
                    "A build reach route needs supports and targets."
                );
            }
        }
    }

    public record TargetReach(
        int targetRouteIndex,
        int targetIndex,
        BlockReachWindow.Cell target,
        List<Integer> reachableSupportIndices,
        List<Integer> guaranteedSupportIndices
    ) {
        public TargetReach {
            if (targetRouteIndex < 0 || targetIndex < 0) {
                throw new IllegalArgumentException(
                    "A build target reference cannot be negative."
                );
            }
            Objects.requireNonNull(target, "target");
            reachableSupportIndices = requireIncreasingIndices(
                reachableSupportIndices,
                false,
                "reachable support"
            );
            guaranteedSupportIndices = requireIncreasingIndices(
                guaranteedSupportIndices,
                true,
                "guaranteed support"
            );
            if (!reachableSupportIndices.containsAll(
                guaranteedSupportIndices
            )) {
                throw new IllegalArgumentException(
                    "Guaranteed supports must also be center-reachable."
                );
            }
        }

        public BlockReachWindow.Window reachWindow() {
            return window(reachableSupportIndices).orElseThrow();
        }

        public Optional<BlockReachWindow.Window> guaranteedWindow() {
            return window(guaranteedSupportIndices);
        }

        /** The last support that is safe as a moving placement deadline. */
        public int deadlineSupportIndex() {
            return guaranteedSupportIndices.isEmpty()
                ? reachableSupportIndices.getLast()
                : guaranteedSupportIndices.getLast();
        }
    }

    public record RoutePlan(
        int routeIndex,
        List<BlockReachWindow.Cell> orderedSupports,
        List<TargetReach> reachableForeignTargets
    ) {
        public RoutePlan {
            if (routeIndex < 0) {
                throw new IllegalArgumentException(
                    "A build route plan has an invalid route index."
                );
            }
            orderedSupports = requireCells(
                orderedSupports,
                "orderedSupports"
            );
            if (orderedSupports.isEmpty()) {
                throw new IllegalArgumentException(
                    "A build route plan needs supports."
                );
            }
            reachableForeignTargets = List.copyOf(
                Objects.requireNonNull(
                    reachableForeignTargets,
                    "reachableForeignTargets"
                )
            );
            for (TargetReach reach : reachableForeignTargets) {
                Objects.requireNonNull(reach, "target reach");
                validateSupportIndices(
                    reach.reachableSupportIndices(),
                    orderedSupports.size()
                );
                validateSupportIndices(
                    reach.guaranteedSupportIndices(),
                    orderedSupports.size()
                );
                if (reach.targetRouteIndex() == routeIndex) {
                    throw new IllegalArgumentException(
                        "A foreign-target plan cannot own its host route."
                    );
                }
            }
        }

        public Optional<TargetReach> target(
            int targetRouteIndex,
            int targetIndex
        ) {
            return reachableForeignTargets.stream()
                .filter(reach ->
                    reach.targetRouteIndex() == targetRouteIndex
                        && reach.targetIndex() == targetIndex)
                .findFirst();
        }

        public Optional<TargetReach> target(
            BlockReachWindow.Cell target
        ) {
            Objects.requireNonNull(target, "target");
            return reachableForeignTargets.stream()
                .filter(reach -> reach.target().equals(target))
                .findFirst();
        }
    }

    public record Snapshot(
        int schemaVersion,
        int algorithmVersion,
        String compactPlanSha256,
        double standingEyeHeight,
        double maximumReach,
        List<Integer> targetCounts,
        List<RoutePlan> routePlans
    ) {
        public Snapshot {
            if (schemaVersion != SCHEMA_VERSION
                || algorithmVersion != ALGORITHM_VERSION) {
                throw new IllegalArgumentException(
                    "Unsupported circular build reach topology version."
                );
            }
            if (!FileFingerprint.isSha256(compactPlanSha256)) {
                throw new IllegalArgumentException(
                    "The build topology has an invalid compact-plan hash."
                );
            }
            requireReachProfile(standingEyeHeight, maximumReach);
            targetCounts = List.copyOf(
                Objects.requireNonNull(targetCounts, "targetCounts")
            );
            routePlans = List.copyOf(
                Objects.requireNonNull(routePlans, "routePlans")
            );
            if (targetCounts.isEmpty()
                || routePlans.size() != targetCounts.size()) {
                throw new IllegalArgumentException(
                    "Build topology routes and target counts disagree."
                );
            }
            for (int routeIndex = 0;
                 routeIndex < routePlans.size();
                 routeIndex++) {
                Integer targetCount = targetCounts.get(routeIndex);
                RoutePlan routePlan = routePlans.get(routeIndex);
                if (targetCount == null
                    || targetCount <= 0
                    || routePlan.routeIndex() != routeIndex) {
                    throw new IllegalArgumentException(
                        "Build topology routes must be contiguous."
                    );
                }
                for (TargetReach reach
                    : routePlan.reachableForeignTargets()) {
                    if (reach.targetRouteIndex() >= targetCounts.size()
                        || reach.targetIndex() >= targetCounts.get(
                            reach.targetRouteIndex()
                        )) {
                        throw new IllegalArgumentException(
                            "A persisted build target exceeds its owning route."
                        );
                    }
                }
            }
        }

        public RoutePlan routePlan(int routeIndex) {
            if (routeIndex < 0 || routeIndex >= routePlans.size()) {
                throw new IllegalArgumentException(
                    "The build route index is outside the topology."
                );
            }
            return routePlans.get(routeIndex);
        }
    }

    public static Snapshot compile(
        String compactPlanSha256,
        List<Route> orderedRoutes,
        double standingEyeHeight,
        double maximumReach
    ) {
        if (!FileFingerprint.isSha256(compactPlanSha256)) {
            throw new IllegalArgumentException(
                "The compact-plan fingerprint must be SHA-256."
            );
        }
        requireReachProfile(standingEyeHeight, maximumReach);
        Objects.requireNonNull(orderedRoutes, "orderedRoutes");
        if (orderedRoutes.isEmpty()) {
            throw new IllegalArgumentException(
                "A build topology requires routes."
            );
        }

        ArrayList<Route> routes = new ArrayList<>(orderedRoutes.size());
        for (int routeIndex = 0;
             routeIndex < orderedRoutes.size();
             routeIndex++) {
            Route route = Objects.requireNonNull(
                orderedRoutes.get(routeIndex),
                "route"
            );
            if (route.routeIndex() != routeIndex) {
                throw new IllegalArgumentException(
                    "Build topology routes must be contiguous and ordered."
                );
            }
            routes.add(route);
        }

        ArrayList<Integer> targetCounts = new ArrayList<>(routes.size());
        routes.forEach(route ->
            targetCounts.add(route.orderedTargets().size())
        );
        ArrayList<RoutePlan> routePlans = new ArrayList<>(routes.size());
        for (Route host : routes) {
            int hostMinimumX = host.orderedSupports().stream()
                .mapToInt(BlockReachWindow.Cell::x)
                .min().orElseThrow();
            int hostMaximumX = host.orderedSupports().stream()
                .mapToInt(BlockReachWindow.Cell::x)
                .max().orElseThrow();
            ArrayList<TargetReach> reachable = new ArrayList<>();
            for (Route owner : routes) {
                if (owner.routeIndex() == host.routeIndex()) continue;
                int ownerMinimumX = owner.orderedTargets().stream()
                    .mapToInt(BlockReachWindow.Cell::x)
                    .min().orElseThrow();
                int ownerMaximumX = owner.orderedTargets().stream()
                    .mapToInt(BlockReachWindow.Cell::x)
                    .max().orElseThrow();
                if (intervalDistance(
                    hostMinimumX,
                    hostMaximumX,
                    ownerMinimumX,
                    ownerMaximumX
                ) > maximumReach) {
                    continue;
                }

                for (int targetIndex = 0;
                     targetIndex < owner.orderedTargets().size();
                     targetIndex++) {
                    BlockReachWindow.Cell target =
                        owner.orderedTargets().get(targetIndex);
                    Optional<BlockReachWindow.Window> opportunity =
                        BlockReachWindow.find(
                            target,
                            host.orderedSupports(),
                            standingEyeHeight,
                            maximumReach
                        );
                    if (opportunity.isEmpty()) continue;
                    List<Integer> guaranteed =
                        BlockReachWindow
                            .findGuaranteedFromSupportCell(
                                target,
                                host.orderedSupports(),
                                standingEyeHeight,
                                maximumReach
                            )
                            .map(BlockReachWindow.Window::reachableSupportIndices)
                            .orElse(List.of());
                    reachable.add(
                        new TargetReach(
                            owner.routeIndex(),
                            targetIndex,
                            target,
                            opportunity.orElseThrow()
                                .reachableSupportIndices(),
                            guaranteed
                        )
                    );
                }
            }
            reachable.sort(
                Comparator
                    .comparingInt(TargetReach::deadlineSupportIndex)
                    .thenComparingInt(reach ->
                        reach.reachableSupportIndices().getFirst())
                    .thenComparingInt(TargetReach::targetRouteIndex)
                    .thenComparingInt(TargetReach::targetIndex)
            );
            routePlans.add(
                new RoutePlan(
                    host.routeIndex(),
                    host.orderedSupports(),
                    reachable
                )
            );
        }

        return new Snapshot(
            SCHEMA_VERSION,
            ALGORITHM_VERSION,
            compactPlanSha256,
            standingEyeHeight,
            maximumReach,
            targetCounts,
            routePlans
        );
    }

    private static List<BlockReachWindow.Cell> requireCells(
        List<BlockReachWindow.Cell> cells,
        String label
    ) {
        cells = List.copyOf(Objects.requireNonNull(cells, label));
        for (BlockReachWindow.Cell cell : cells) {
            Objects.requireNonNull(cell, label + " entry");
        }
        return cells;
    }

    private static List<Integer> requireIncreasingIndices(
        List<Integer> indices,
        boolean allowEmpty,
        String label
    ) {
        indices = List.copyOf(Objects.requireNonNull(indices, label));
        if (!allowEmpty && indices.isEmpty()) {
            throw new IllegalArgumentException(label + " list is empty.");
        }
        int previous = -1;
        for (Integer index : indices) {
            if (index == null || index <= previous) {
                throw new IllegalArgumentException(
                    label + " indices must be nonnegative and increasing."
                );
            }
            previous = index;
        }
        return indices;
    }

    private static void validateSupportIndices(
        List<Integer> indices,
        int supportCount
    ) {
        if (!indices.isEmpty() && indices.getLast() >= supportCount) {
            throw new IllegalArgumentException(
                "A build reach index exceeds its host route."
            );
        }
    }

    private static Optional<BlockReachWindow.Window> window(
        List<Integer> indices
    ) {
        if (indices.isEmpty()) return Optional.empty();
        return Optional.of(
            new BlockReachWindow.Window(
                indices.getFirst(),
                indices.getLast(),
                indices
            )
        );
    }

    private static double intervalDistance(
        int firstMinimum,
        int firstMaximum,
        int secondMinimum,
        int secondMaximum
    ) {
        if (firstMaximum < secondMinimum) {
            return secondMinimum - firstMaximum;
        }
        if (secondMaximum < firstMinimum) {
            return firstMinimum - secondMaximum;
        }
        return 0.0;
    }

    private static void requireReachProfile(
        double standingEyeHeight,
        double maximumReach
    ) {
        if (!Double.isFinite(standingEyeHeight)
            || standingEyeHeight <= 0.0
            || !Double.isFinite(maximumReach)
            || maximumReach <= 0.0) {
            throw new IllegalArgumentException(
                "The build reach profile must be finite and positive."
            );
        }
    }
}

package com.julflips.nerv_printer.utils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Plans a grounded local return to the retained circular-build cursor.
 *
 * <p>The destination is an ordered support behind the retained cursor. This
 * prevents a physically adjacent opposite U leg from stealing the cursor and
 * deliberately replays a short, already-confirmed section after a server
 * correction or movement overshoot.</p>
 */
public final class CircularBuildRouteRejoinPlan {
    private CircularBuildRouteRejoinPlan() {
    }

    public record Plan(
        int routeSupportIndex,
        List<GroundedSupportPathPlanner.Cell> path
    ) {
        public Plan {
            if (routeSupportIndex < 0) {
                throw new IllegalArgumentException(
                    "A route rejoin requires a support index."
                );
            }
            path = List.copyOf(path);
            if (path.isEmpty()) {
                throw new IllegalArgumentException(
                    "A route rejoin requires a grounded path."
                );
            }
        }
    }

    /**
     * Horizontal search bounds derived from the generated route phase and
     * the authoritative support under the player.
     */
    public record Domain(
        int minimumX,
        int maximumX,
        int minimumZ,
        int maximumZ
    ) {
        public Domain {
            if (maximumX < minimumX || maximumZ < minimumZ) {
                throw new IllegalArgumentException(
                    "A route-rejoin domain cannot be inverted."
                );
            }
        }

        public boolean contains(GroundedSupportPathPlanner.Cell candidate) {
            Objects.requireNonNull(candidate, "candidate");
            return candidate.x() >= minimumX
                && candidate.x() <= maximumX
                && candidate.z() >= minimumZ
                && candidate.z() <= maximumZ;
        }
    }

    public static Domain routeDomain(
        GroundedSupportPathPlanner.Cell start,
        List<GroundedSupportPathPlanner.Cell> eligibleRouteSupports
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(
            eligibleRouteSupports,
            "eligibleRouteSupports"
        );
        if (eligibleRouteSupports.isEmpty()) {
            throw new IllegalArgumentException(
                "A route-rejoin domain requires eligible supports."
            );
        }

        int minimumX = start.x();
        int maximumX = start.x();
        int minimumZ = start.z();
        int maximumZ = start.z();
        for (GroundedSupportPathPlanner.Cell support
            : eligibleRouteSupports) {
            Objects.requireNonNull(support, "eligible route support");
            minimumX = Math.min(minimumX, support.x());
            maximumX = Math.max(maximumX, support.x());
            minimumZ = Math.min(minimumZ, support.z());
            maximumZ = Math.max(maximumZ, support.z());
        }
        return new Domain(minimumX, maximumX, minimumZ, maximumZ);
    }

    public static Optional<Plan> find(
        GroundedSupportPathPlanner.Cell start,
        List<GroundedSupportPathPlanner.Cell> orderedRouteSupports,
        int retainedSupportIndex,
        int firstEligibleSupportIndex,
        int lastEligibleSupportIndex,
        int movementDirection,
        int replaySupportCount,
        Predicate<GroundedSupportPathPlanner.Cell> insideDomain,
        Predicate<GroundedSupportPathPlanner.Cell> walkable,
        int nodeCap
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(
            orderedRouteSupports,
            "orderedRouteSupports"
        );
        Objects.requireNonNull(insideDomain, "insideDomain");
        Objects.requireNonNull(walkable, "walkable");
        if (orderedRouteSupports.isEmpty()
            || retainedSupportIndex < 0
            || retainedSupportIndex >= orderedRouteSupports.size()
            || firstEligibleSupportIndex < 0
            || lastEligibleSupportIndex < firstEligibleSupportIndex
            || lastEligibleSupportIndex >= orderedRouteSupports.size()
            || replaySupportCount < 0
            || movementDirection != -1 && movementDirection != 1) {
            throw new IllegalArgumentException(
                "Invalid circular route-rejoin bounds."
            );
        }

        int replaySupportIndex = Math.max(
            firstEligibleSupportIndex,
            Math.min(
                lastEligibleSupportIndex,
                retainedSupportIndex
                    - movementDirection * replaySupportCount
            )
        );
        GroundedSupportPathPlanner.Cell replaySupport =
            Objects.requireNonNull(
                orderedRouteSupports.get(replaySupportIndex),
                "replay route support"
            );

        Optional<GroundedSupportPathPlanner.Plan> grounded =
            GroundedSupportPathPlanner.findPath(
                start,
                Set.of(replaySupport),
                insideDomain,
                walkable,
                nodeCap
            );
        if (grounded.isEmpty()) return Optional.empty();

        GroundedSupportPathPlanner.Plan path = grounded.orElseThrow();
        if (!path.endpoint().equals(replaySupport)) {
            throw new IllegalStateException(
                "The grounded rejoin ended outside its route goals."
            );
        }
        return Optional.of(new Plan(replaySupportIndex, path.path()));
    }
}

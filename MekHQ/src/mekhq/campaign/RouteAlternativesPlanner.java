/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MekHQ was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package mekhq.campaign;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

import mekhq.campaign.universe.PlanetarySystem;

/** Computes transient, deterministic route comparisons without changing campaign state. */
public final class RouteAlternativesPlanner {
    private static final int MAXIMUM_SYSTEMS_PER_COURSE = 10001;
    private static final double SCORE_TOLERANCE = 1.0e-9;
    private static final String PATH_SEPARATOR = "\u0000";

    private RouteAlternativesPlanner() {
    }

    /** The operational reason a course is offered. */
    public enum CourseKind {
        FASTEST,
        FEWEST_JUMPS,
        COMMAND_CIRCUIT
    }

    /** Command-circuit coverage assumed while comparing a course. */
    public enum CircuitCoverage {
        NONE,
        WHOLE
    }

    /** Whether every leg in a generated course passed the supplied access policy. */
    public enum AccessStatus {
        CLEAR,
        BLOCKED
    }

    /** Supplies the route graph and campaign-specific filtering without owning mutable campaign options. */
    public interface RoutePolicy {
        Collection<PlanetarySystem> getNeighbors(PlanetarySystem system);

        boolean isSystemAllowed(PlanetarySystem system);

        boolean canTraverse(PlanetarySystem origin, PlanetarySystem destination);

        default RoutePolicy forSegment(PlanetarySystem origin, PlanetarySystem destination) {
            return this;
        }
    }

    /** One immutable route comparison. */
    public record Course(CourseKind kind, List<PlanetarySystem> systems, double oneGTotalDays,
                         CircuitCoverage circuitCoverage, AccessStatus accessStatus) {
        public Course {
            Objects.requireNonNull(kind);
            systems = List.copyOf(systems);
            Objects.requireNonNull(circuitCoverage);
            Objects.requireNonNull(accessStatus);
        }

        public int jumps() {
            return Math.max(0, systems.size() - 1);
        }

        public JumpPath toJumpPath() {
            JumpPath path = new JumpPath();
            path.addSystems(systems);
            return path;
        }
    }

    static boolean isValidRequest(PlanetarySystem origin, List<PlanetarySystem> requestedStops) {
        if ((origin == null) || (requestedStops == null) || requestedStops.isEmpty()) {
            return false;
        }
        return requestedStops.stream().noneMatch(Objects::isNull);
    }

    /**
     * Plans unique courses through every requested stop in order.
     *
     * @param origin                    first system in every candidate course
     * @param requestedStops             explicit stops, including the final destination
     * @param date                       date used for recharge and elapsed-time comparisons
     * @param currentCircuitAvailability current whole-route operational circuit availability
     * @param policy                     immutable graph and access policy for this calculation
     *
     * @return fastest, fewest-jump, and useful circuit courses in stable presentation order
     */
    public static List<Course> plan(PlanetarySystem origin, List<PlanetarySystem> requestedStops, LocalDate date,
          boolean currentCircuitAvailability, RoutePolicy policy) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(requestedStops);
        Objects.requireNonNull(date);
        Objects.requireNonNull(policy);
        if (requestedStops.isEmpty()) {
            return List.of();
        }

        CircuitCoverage currentCoverage = currentCircuitAvailability ? CircuitCoverage.WHOLE : CircuitCoverage.NONE;
        LinkedHashMap<String, Course> uniqueCourses = new LinkedHashMap<>();
        addCourse(uniqueCourses, createCourse(CourseKind.FASTEST, Objective.FASTEST, currentCoverage, origin,
              requestedStops, date, policy));
        addCourse(uniqueCourses, createCourse(CourseKind.FEWEST_JUMPS, Objective.FEWEST_JUMPS, currentCoverage,
              origin, requestedStops, date, policy));

        if (!currentCircuitAvailability) {
            Course circuitCourse = createCourse(CourseKind.COMMAND_CIRCUIT, Objective.FASTEST,
                  CircuitCoverage.WHOLE, origin, requestedStops, date, policy);
            Course fastestCourse = uniqueCourses.values().stream()
                                         .filter(course -> course.kind() == CourseKind.FASTEST)
                                         .findFirst()
                                         .orElse(null);
            if (isUsefulCircuitCourse(circuitCourse, fastestCourse, date)) {
                addCourse(uniqueCourses, circuitCourse);
            }
        }
        return List.copyOf(uniqueCourses.values());
    }

    private static Course createCourse(CourseKind kind, Objective objective, CircuitCoverage circuitCoverage,
          PlanetarySystem origin, List<PlanetarySystem> requestedStops, LocalDate date, RoutePolicy policy) {
        List<PlanetarySystem> systems = new ArrayList<>();
        PlanetarySystem segmentOrigin = origin;
        boolean useCommandCircuit = circuitCoverage == CircuitCoverage.WHOLE;
        for (PlanetarySystem requestedStop : requestedStops) {
            if (requestedStop == null) {
                return null;
            }
            RoutePolicy segmentPolicy = policy.forSegment(segmentOrigin, requestedStop);
            List<PlanetarySystem> segment = findSegment(segmentOrigin, requestedStop, date, useCommandCircuit,
                objective, segmentPolicy);
            if (segment.isEmpty()) {
                return null;
            }
            systems.addAll(segment.subList(systems.isEmpty() ? 0 : 1, segment.size()));
            segmentOrigin = requestedStop;
        }

        JumpPath path = new JumpPath();
        path.addSystems(systems);
        return new Course(kind, systems, path.getTotalTime(date, 0.0, useCommandCircuit), circuitCoverage,
              AccessStatus.CLEAR);
    }

    static JumpPath planFastestSegment(PlanetarySystem origin, PlanetarySystem destination, LocalDate date,
          boolean useCommandCircuit, RoutePolicy policy) {
        JumpPath path = new JumpPath();
        if (origin == null) {
            return path;
        }
        if ((destination == null) || sameSystem(origin, destination)) {
            path.addSystem(origin);
            return path;
        }

        List<PlanetarySystem> systems = findSegment(origin, destination, date, useCommandCircuit,
              Objective.FASTEST, policy.forSegment(origin, destination));
        path.addSystems(systems);
        return path;
    }

    private static List<PlanetarySystem> findSegment(PlanetarySystem origin, PlanetarySystem destination,
          LocalDate date, boolean useCommandCircuit, Objective objective, RoutePolicy policy) {
        if (sameSystem(origin, destination)) {
            return List.of(origin);
        }
        if (!policy.isSystemAllowed(destination)) {
            return List.of();
        }

        Comparator<SearchNode> nodeOrder = Comparator.comparingDouble(SearchNode::score)
                                                   .thenComparing(SearchNode::pathKey);
        PriorityQueue<SearchNode> frontier = new PriorityQueue<>(nodeOrder);
        Map<String, SearchNode> bestNodes = new HashMap<>();
        SearchNode start = new SearchNode(origin, 0.0, systemKey(origin), List.of(origin));
        frontier.add(start);
        bestNodes.put(systemKey(origin), start);

        while (!frontier.isEmpty()) {
            SearchNode current = frontier.remove();
            if (current != bestNodes.get(systemKey(current.system()))) {
                continue;
            }
            if (sameSystem(current.system(), destination)) {
                return current.systems();
            }
            if (current.systems().size() >= MAXIMUM_SYSTEMS_PER_COURSE) {
                continue;
            }

            List<PlanetarySystem> neighbors = new ArrayList<>(policy.getNeighbors(current.system()));
            neighbors.sort(Comparator.comparing(RouteAlternativesPlanner::systemKey));
            for (PlanetarySystem neighbor : neighbors) {
                if ((neighbor == null) || containsSystem(current.systems(), neighbor)
                      || !policy.isSystemAllowed(neighbor)
                      || !policy.canTraverse(current.system(), neighbor)) {
                    continue;
                }

                double edgeScore = objective == Objective.FEWEST_JUMPS ? 1.0
                                         : getDepartureRecharge(current.system(), origin, date, useCommandCircuit);
                double candidateScore = current.score() + edgeScore;
                String candidatePathKey = current.pathKey() + PATH_SEPARATOR + systemKey(neighbor);
                SearchNode previous = bestNodes.get(systemKey(neighbor));
                if (!isBetter(candidateScore, candidatePathKey, previous)) {
                    continue;
                }

                List<PlanetarySystem> candidateSystems = new ArrayList<>(current.systems());
                candidateSystems.add(neighbor);
                SearchNode candidate = new SearchNode(neighbor, candidateScore, candidatePathKey,
                      List.copyOf(candidateSystems));
                bestNodes.put(systemKey(neighbor), candidate);
                frontier.add(candidate);
            }
        }
        return List.of();
    }

    private static double getDepartureRecharge(PlanetarySystem system, PlanetarySystem segmentOrigin,
          LocalDate date, boolean useCommandCircuit) {
        return sameSystem(system, segmentOrigin) ? 0.0 : system.getRechargeTime(date, useCommandCircuit);
    }

    private static boolean isBetter(double candidateScore, String candidatePathKey, SearchNode previous) {
        if (previous == null) {
            return true;
        }
        if (candidateScore < previous.score() - SCORE_TOLERANCE) {
            return true;
        }
        return (Math.abs(candidateScore - previous.score()) <= SCORE_TOLERANCE)
                     && (candidatePathKey.compareTo(previous.pathKey()) < 0);
    }

    private static boolean isUsefulCircuitCourse(Course circuitCourse, Course fastestCourse, LocalDate date) {
        if ((circuitCourse == null) || (fastestCourse == null)
              || pathKey(circuitCourse.systems()).equals(pathKey(fastestCourse.systems()))) {
            return false;
        }
        double fastestWithCircuit = fastestCourse.toJumpPath().getTotalTime(date, 0.0, true);
        return circuitCourse.oneGTotalDays() < fastestWithCircuit - SCORE_TOLERANCE;
    }

    private static void addCourse(Map<String, Course> courses, Course course) {
        if (course != null) {
            courses.putIfAbsent(pathKey(course.systems()), course);
        }
    }

    private static boolean containsSystem(List<PlanetarySystem> systems, PlanetarySystem candidate) {
        return systems.stream().anyMatch(system -> sameSystem(system, candidate));
    }

    private static boolean sameSystem(PlanetarySystem first, PlanetarySystem second) {
        return Objects.equals(systemKey(first), systemKey(second));
    }

    private static String pathKey(List<PlanetarySystem> systems) {
        return String.join(PATH_SEPARATOR, systems.stream().map(RouteAlternativesPlanner::systemKey).toList());
    }

    private static String systemKey(PlanetarySystem system) {
        return Objects.requireNonNull(system.getId());
    }

    private enum Objective {
        FASTEST,
        FEWEST_JUMPS
    }

    private record SearchNode(PlanetarySystem system, double score, String pathKey,
                              List<PlanetarySystem> systems) {
    }
}

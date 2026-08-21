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

import static java.lang.Math.sqrt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mekhq.campaign.RouteAlternativesPlanner.Course;
import mekhq.campaign.RouteAlternativesPlanner.CourseKind;
import mekhq.campaign.RouteAlternativesPlanner.RoutePolicy;
import mekhq.campaign.universe.PlanetarySystem;
import org.junit.jupiter.api.Test;

class RouteAlternativesPlannerTest {
    private static final LocalDate TEST_DATE = LocalDate.of(3025, 1, 1);

    @Test
    void immutableRequestedStopsPassValidationAndReachPlanning() {
        PlanetarySystem origin = system("ORIGIN", 0.0);
        PlanetarySystem destination = system("DESTINATION", 0.0);
        List<PlanetarySystem> requestedStops = List.of(destination);

        assertTrue(RouteAlternativesPlanner.isValidRequest(origin, requestedStops));
        List<Course> courses = RouteAlternativesPlanner.plan(origin, requestedStops, TEST_DATE, false,
              policy(Map.of(origin, List.of(destination))));

        assertEquals(List.of(origin, destination), courses.getFirst().systems());
    }

    @Test
    void routeRequestValidationRejectsNullInputsAndElements() {
        PlanetarySystem origin = system("ORIGIN", 0.0);
        List<PlanetarySystem> requestedStops = new ArrayList<>();
        requestedStops.add(null);

        assertFalse(RouteAlternativesPlanner.isValidRequest(null, List.of(origin)));
        assertFalse(RouteAlternativesPlanner.isValidRequest(origin, null));
        assertFalse(RouteAlternativesPlanner.isValidRequest(origin, List.of()));
        assertFalse(RouteAlternativesPlanner.isValidRequest(origin, requestedStops));
    }

    @Test
    void fastestAndFewestJumpsCanProduceDistinctCourses() {
        PlanetarySystem origin = system("ORIGIN", 0.0);
        PlanetarySystem shortRecharge = system("SHORT_RECHARGE", 100.0);
        PlanetarySystem quickOne = system("QUICK_ONE", 1.0);
        PlanetarySystem quickTwo = system("QUICK_TWO", 1.0);
        PlanetarySystem destination = system("DESTINATION", 0.0);
        RoutePolicy policy = policy(Map.of(
              origin, List.of(shortRecharge, quickOne),
              shortRecharge, List.of(destination),
              quickOne, List.of(quickTwo),
              quickTwo, List.of(destination)));

        List<Course> courses = RouteAlternativesPlanner.plan(origin, List.of(destination), TEST_DATE, false, policy);

        assertEquals(2, courses.size());
        assertEquals(CourseKind.FASTEST, courses.get(0).kind());
        assertEquals(List.of(origin, quickOne, quickTwo, destination), courses.get(0).systems());
        assertEquals(CourseKind.FEWEST_JUMPS, courses.get(1).kind());
        assertEquals(List.of(origin, shortRecharge, destination), courses.get(1).systems());
    }

        @Test
        void identicalCoursesAreDeduplicated() {
          PlanetarySystem origin = system("ORIGIN", 0.0);
          PlanetarySystem destination = system("DESTINATION", 0.0);

          List<Course> courses = RouteAlternativesPlanner.plan(origin, List.of(destination), TEST_DATE, false,
              policy(Map.of(origin, List.of(destination))));

          assertEquals(1, courses.size());
          assertEquals(CourseKind.FASTEST, courses.getFirst().kind());
          assertEquals(List.of(origin, destination), courses.getFirst().systems());
        }

        @Test
        void equalScoresUseStableSystemIdPathTieBreaking() {
          PlanetarySystem origin = system("ORIGIN", 0.0);
          PlanetarySystem laterId = system("B_ROUTE", 5.0);
          PlanetarySystem earlierId = system("A_ROUTE", 5.0);
          PlanetarySystem destination = system("DESTINATION", 0.0);
          RoutePolicy policy = policy(Map.of(
              origin, List.of(laterId, earlierId),
              laterId, List.of(destination),
              earlierId, List.of(destination)));

          for (int iteration = 0; iteration < 5; iteration++) {
            List<Course> courses = RouteAlternativesPlanner.plan(origin, List.of(destination), TEST_DATE, false,
                policy);
            assertEquals(List.of(origin, earlierId, destination), courses.getFirst().systems());
          }
        }

        @Test
        void requestedStopsAreConcatenatedWithoutDuplicateSegmentOrigins() {
          PlanetarySystem origin = system("ORIGIN", 0.0);
          PlanetarySystem firstIntermediate = system("FIRST_INTERMEDIATE", 1.0);
          PlanetarySystem requestedStop = system("REQUESTED_STOP", 1.0);
          PlanetarySystem secondIntermediate = system("SECOND_INTERMEDIATE", 1.0);
          PlanetarySystem destination = system("DESTINATION", 0.0);
          RoutePolicy policy = policy(Map.of(
              origin, List.of(firstIntermediate),
              firstIntermediate, List.of(requestedStop),
              requestedStop, List.of(secondIntermediate),
              secondIntermediate, List.of(destination)));

          List<Course> courses = RouteAlternativesPlanner.plan(origin, List.of(requestedStop, destination), TEST_DATE,
              false, policy);

          assertEquals(List.of(origin, firstIntermediate, requestedStop, secondIntermediate, destination),
              courses.getFirst().systems());
        }

        @Test
        void disallowedAndInaccessibleSystemsAreFiltered() {
          PlanetarySystem origin = system("ORIGIN", 0.0);
          PlanetarySystem empty = system("EMPTY", 0.0);
          PlanetarySystem inaccessible = system("INACCESSIBLE", 0.0);
          PlanetarySystem clear = system("CLEAR", 0.0);
          PlanetarySystem destination = system("DESTINATION", 0.0);
          Map<PlanetarySystem, List<PlanetarySystem>> neighbors = Map.of(
              origin, List.of(empty, inaccessible, clear),
              empty, List.of(destination),
              inaccessible, List.of(destination),
              clear, List.of(destination));
          RoutePolicy policy = policy(neighbors, Set.of(empty), Set.of(new Leg(origin, inaccessible)));

          List<Course> courses = RouteAlternativesPlanner.plan(origin, List.of(destination), TEST_DATE, false, policy);

          assertEquals(1, courses.size());
          assertEquals(List.of(origin, clear, destination), courses.getFirst().systems());
          assertEquals(RouteAlternativesPlanner.AccessStatus.CLEAR, courses.getFirst().accessStatus());
        }

        @Test
        void unavailableRequestedSegmentProducesNoCourses() {
          PlanetarySystem origin = system("ORIGIN", 0.0);
          PlanetarySystem blocked = system("BLOCKED", 0.0);
          PlanetarySystem destination = system("DESTINATION", 0.0);
          RoutePolicy policy = policy(Map.of(origin, List.of(blocked), blocked, List.of(destination)),
              Set.of(blocked), Set.of());

          assertTrue(RouteAlternativesPlanner.plan(origin, List.of(destination), TEST_DATE, false, policy).isEmpty());
        }

        @Test
        void wholeCircuitCourseIsOfferedOnlyWhenItImprovesTheRoute() {
          PlanetarySystem origin = system("ORIGIN", 0.0);
          PlanetarySystem normalRoute = system("NORMAL_ROUTE", 5.0, 5.0);
          PlanetarySystem circuitOne = system("CIRCUIT_ONE", 100.0, 1.0);
          PlanetarySystem circuitTwo = system("CIRCUIT_TWO", 100.0, 1.0);
          PlanetarySystem destination = system("DESTINATION", 0.0);
          RoutePolicy policy = policy(Map.of(
              origin, List.of(normalRoute, circuitOne),
              normalRoute, List.of(destination),
              circuitOne, List.of(circuitTwo),
              circuitTwo, List.of(destination)));

          List<Course> courses = RouteAlternativesPlanner.plan(origin, List.of(destination), TEST_DATE, false, policy);

          assertEquals(2, courses.size());
          assertEquals(List.of(origin, normalRoute, destination), courses.getFirst().systems());
          assertEquals(CourseKind.COMMAND_CIRCUIT, courses.getLast().kind());
          assertEquals(List.of(origin, circuitOne, circuitTwo, destination), courses.getLast().systems());
          assertEquals(RouteAlternativesPlanner.CircuitCoverage.WHOLE, courses.getLast().circuitCoverage());
        }

    private static PlanetarySystem system(String id, double rechargeHours) {
        return system(id, rechargeHours, rechargeHours);
    }

    private static PlanetarySystem system(String id, double rechargeHours, double circuitRechargeHours) {
        PlanetarySystem system = mock(PlanetarySystem.class);
        when(system.getId()).thenReturn(id);
        when(system.getRechargeTime(any(LocalDate.class), anyBoolean()))
              .thenAnswer(invocation -> (boolean) invocation.getArgument(1)
                                       ? circuitRechargeHours
                                       : rechargeHours);
        when(system.getTimeToJumpPoint(anyDouble()))
              .thenAnswer(invocation -> 1.0 / sqrt(invocation.getArgument(0)));
        return system;
    }

    private static RoutePolicy policy(Map<PlanetarySystem, List<PlanetarySystem>> neighbors) {
        return policy(neighbors, Set.of(), Set.of());
    }

    private static RoutePolicy policy(Map<PlanetarySystem, List<PlanetarySystem>> neighbors,
          Set<PlanetarySystem> disallowedSystems, Set<Leg> inaccessibleLegs) {
        return new RoutePolicy() {
            @Override
            public Collection<PlanetarySystem> getNeighbors(PlanetarySystem system) {
                return neighbors.getOrDefault(system, List.of());
            }

            @Override
            public boolean isSystemAllowed(PlanetarySystem system) {
                return !disallowedSystems.contains(system);
            }

            @Override
            public boolean canTraverse(PlanetarySystem origin, PlanetarySystem destination) {
                return !inaccessibleLegs.contains(new Leg(origin, destination));
            }
        };
    }

    private record Leg(PlanetarySystem origin, PlanetarySystem destination) {
    }
}

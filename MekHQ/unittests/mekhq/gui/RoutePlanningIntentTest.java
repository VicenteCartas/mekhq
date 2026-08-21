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
package mekhq.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import mekhq.campaign.JumpPath;
import mekhq.campaign.universe.PlanetarySystem;
import org.junit.jupiter.api.Test;

class RoutePlanningIntentTest {
    @Test
    void automaticIntermediateIsNotARemovableRequestedStop() {
        PlanetarySystem origin = system("Origin");
        PlanetarySystem intermediate = system("Intermediate");
        PlanetarySystem destination = system("Destination");
        RoutePlanningIntent intent = new RoutePlanningIntent(origin);

        assertTrue(intent.plot(origin, destination,
              planner(Map.of(new Leg(origin, destination), List.of(origin, intermediate, destination)))));

        assertIterableEquals(List.of(destination), intent.getRequestedStops());
        assertFalse(intent.isRequestedStop(intermediate));
        assertTrue(intent.canTrimAt(intermediate));
        assertFalse(intent.canTrimAt(origin));
        assertFalse(intent.removeRequestedStop(intermediate, planner(Map.of())));
        assertIterableEquals(List.of(origin, intermediate, destination), intent.getJumpPath().getSystems());
    }

    @Test
    void appendConcatenatesSegmentsWithoutDuplicateOrigins() {
        PlanetarySystem origin = system("Origin");
        PlanetarySystem firstIntermediate = system("First Intermediate");
        PlanetarySystem firstStop = system("First Stop");
        PlanetarySystem secondIntermediate = system("Second Intermediate");
        PlanetarySystem destination = system("Destination");
        RoutePlanningIntent intent = new RoutePlanningIntent(origin);
        RoutePlanningIntent.SegmentPlanner planner = planner(Map.of(
              new Leg(origin, firstStop), List.of(origin, firstIntermediate, firstStop),
              new Leg(firstStop, destination), List.of(firstStop, secondIntermediate, destination)));

        assertTrue(intent.plot(origin, firstStop, planner));
        assertTrue(intent.append(destination, planner));

        assertIterableEquals(List.of(firstStop, destination), intent.getRequestedStops());
        assertIterableEquals(List.of(origin, firstIntermediate, firstStop, secondIntermediate, destination),
              intent.getJumpPath().getSystems());
    }

    @Test
    void removingRequestedStopRederivesNeighboringSegments() {
        PlanetarySystem origin = system("Origin");
        PlanetarySystem firstStop = system("First Stop");
        PlanetarySystem destination = system("Destination");
        PlanetarySystem replacementIntermediate = system("Replacement Intermediate");
        RoutePlanningIntent intent = new RoutePlanningIntent(origin);
        RoutePlanningIntent.SegmentPlanner planner = planner(Map.of(
              new Leg(origin, firstStop), List.of(origin, firstStop),
              new Leg(firstStop, destination), List.of(firstStop, destination),
              new Leg(origin, destination), List.of(origin, replacementIntermediate, destination)));

        assertTrue(intent.plot(origin, firstStop, planner));
        assertTrue(intent.append(destination, planner));
        assertTrue(intent.removeRequestedStop(firstStop, planner));

        assertIterableEquals(List.of(destination), intent.getRequestedStops());
        assertIterableEquals(List.of(origin, replacementIntermediate, destination),
              intent.getJumpPath().getSystems());
    }

    @Test
    void trimmingAtIntermediateRetainsOnlyEarlierRequestedStops() {
        PlanetarySystem origin = system("Origin");
        PlanetarySystem firstStop = system("First Stop");
        PlanetarySystem trimTarget = system("Trim Target");
        PlanetarySystem destination = system("Destination");
        RoutePlanningIntent intent = new RoutePlanningIntent(origin);
        RoutePlanningIntent.SegmentPlanner planner = planner(Map.of(
              new Leg(origin, firstStop), List.of(origin, firstStop),
              new Leg(firstStop, destination), List.of(firstStop, trimTarget, destination),
              new Leg(firstStop, trimTarget), List.of(firstStop, trimTarget)));

        assertTrue(intent.plot(origin, firstStop, planner));
        assertTrue(intent.append(destination, planner));
        assertTrue(intent.trimAt(trimTarget, planner));

        assertIterableEquals(List.of(firstStop, trimTarget), intent.getRequestedStops());
        assertIterableEquals(List.of(origin, firstStop, trimTarget), intent.getJumpPath().getSystems());
    }

    @Test
    void invalidSegmentLeavesExistingIntentUnchanged() {
        PlanetarySystem origin = system("Origin");
        PlanetarySystem firstStop = system("First Stop");
        PlanetarySystem unreachable = system("Unreachable");
        RoutePlanningIntent intent = new RoutePlanningIntent(origin);
        RoutePlanningIntent.SegmentPlanner initialPlanner = planner(
              Map.of(new Leg(origin, firstStop), List.of(origin, firstStop)));

        assertTrue(intent.plot(origin, firstStop, initialPlanner));
        assertFalse(intent.append(unreachable, initialPlanner));

        assertSame(origin, intent.getOrigin());
        assertIterableEquals(List.of(firstStop), intent.getRequestedStops());
        assertIterableEquals(List.of(origin, firstStop), intent.getJumpPath().getSystems());
    }

    @Test
    void mapTabIntentInitializationIsIdempotent() {
        PlanetarySystem origin = system("Origin");
        RoutePlanningIntent initializedIntent = MapTab.initializeRoutePlanningIntent(null, origin);

        assertSame(origin, initializedIntent.getOrigin());
        assertSame(initializedIntent,
              MapTab.initializeRoutePlanningIntent(initializedIntent, system("Replacement Origin")));
        assertSame(origin, initializedIntent.getOrigin());
    }

    private static PlanetarySystem system(String name) {
        return new PlanetarySystem(name);
    }

    private static RoutePlanningIntent.SegmentPlanner planner(Map<Leg, List<PlanetarySystem>> segments) {
        return (origin, destination) -> {
            JumpPath path = new JumpPath();
            List<PlanetarySystem> systems = segments.get(new Leg(origin, destination));
            if (systems != null) {
                path.addSystems(systems);
            }
            return path;
        };
    }

    private record Leg(PlanetarySystem origin, PlanetarySystem destination) {
    }
}
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.annotation.Nullable;
import mekhq.campaign.JumpPath;
import mekhq.campaign.RouteAlternativesPlanner.PlanningResult;
import mekhq.campaign.RouteAlternativesPlanner.PlanningStatus;
import mekhq.campaign.universe.PlanetarySystem;

final class RoutePlanningIntent {
    @FunctionalInterface
    interface SegmentPlanner {
        PlanningResult calculate(PlanetarySystem origin, PlanetarySystem destination);
    }

    enum ChangeResult {
        CHANGED,
        NO_CHANGE,
        ACCESS_DENIED,
        NO_ROUTE;

        boolean changed() {
            return this == CHANGED;
        }
    }

    private @Nullable PlanetarySystem origin;
    private List<PlanetarySystem> requestedStops = List.of();
    private JumpPath jumpPath = new JumpPath();

    RoutePlanningIntent(@Nullable PlanetarySystem origin) {
        this.origin = origin;
    }

    @Nullable PlanetarySystem getOrigin() {
        return origin;
    }

    List<PlanetarySystem> getRequestedStops() {
        return requestedStops;
    }

    JumpPath getJumpPath() {
        return copyOf(jumpPath);
    }

    boolean adopt(@Nullable JumpPath proposedPath) {
        if ((origin == null) || (proposedPath == null) || requestedStops.isEmpty() || proposedPath.isEmpty()) {
            return false;
        }

        List<PlanetarySystem> proposedSystems = proposedPath.getSystems();
        if (!Objects.equals(origin, proposedPath.getFirstSystem())
              || !Objects.equals(requestedStops.getLast(), proposedPath.getLastSystem())) {
            return false;
        }

        int searchFrom = 0;
        for (PlanetarySystem requestedStop : requestedStops) {
            int stopIndex = indexOf(proposedSystems, requestedStop, searchFrom);
            if (stopIndex < 0) {
                return false;
            }
            searchFrom = stopIndex + 1;
        }

        JumpPath adoptedPath = copyOf(proposedPath);
        adoptedPath.setTargetPlanet(jumpPath.getTargetPlanet());
        jumpPath = adoptedPath;
        return true;
    }

    ChangeResult plot(@Nullable PlanetarySystem proposedOrigin, @Nullable PlanetarySystem destination,
          SegmentPlanner planner) {
        if ((proposedOrigin == null) || (destination == null)) {
            return ChangeResult.NO_CHANGE;
        }
        return replace(proposedOrigin, List.of(destination), planner);
    }

    ChangeResult append(@Nullable PlanetarySystem destination, SegmentPlanner planner) {
        if ((origin == null) || (destination == null)) {
            return ChangeResult.NO_CHANGE;
        }
        List<PlanetarySystem> proposedStops = new ArrayList<>(requestedStops);
        proposedStops.add(destination);
        return replace(origin, proposedStops, planner);
    }

    ChangeResult removeRequestedStop(@Nullable PlanetarySystem stop, SegmentPlanner planner) {
        if ((origin == null) || (stop == null) || !requestedStops.contains(stop)) {
            return ChangeResult.NO_CHANGE;
        }
        List<PlanetarySystem> proposedStops = new ArrayList<>(requestedStops);
        proposedStops.remove(stop);
        if (proposedStops.isEmpty()) {
            requestedStops = List.of();
            jumpPath = new JumpPath();
            return ChangeResult.CHANGED;
        }
        return replace(origin, proposedStops, planner);
    }

    ChangeResult trimAt(@Nullable PlanetarySystem destination, SegmentPlanner planner) {
        if ((origin == null) || (destination == null)) {
            return ChangeResult.NO_CHANGE;
        }
        int trimIndex = jumpPath.getSystems().indexOf(destination);
        if (trimIndex <= 0) {
            return ChangeResult.NO_CHANGE;
        }

        List<PlanetarySystem> proposedStops = new ArrayList<>();
        int searchFrom = 0;
        for (PlanetarySystem requestedStop : requestedStops) {
            int stopIndex = indexOf(jumpPath.getSystems(), requestedStop, searchFrom);
            if ((stopIndex < 0) || (stopIndex >= trimIndex)) {
                break;
            }
            proposedStops.add(requestedStop);
            searchFrom = stopIndex + 1;
        }
        proposedStops.add(destination);
        return replace(origin, proposedStops, planner);
    }

    boolean isRequestedStop(@Nullable PlanetarySystem system) {
        return (system != null) && requestedStops.contains(system);
    }

    boolean canTrimAt(@Nullable PlanetarySystem system) {
        return (system != null) && (jumpPath.getSystems().indexOf(system) > 0);
    }

    void clear(@Nullable PlanetarySystem defaultOrigin) {
        origin = defaultOrigin;
        requestedStops = List.of();
        jumpPath = new JumpPath();
    }

    private ChangeResult replace(PlanetarySystem proposedOrigin, List<PlanetarySystem> proposedStops,
          SegmentPlanner planner) {
        PlanningResult proposedPath = derive(proposedOrigin, proposedStops, planner);
        if (!proposedPath.routeFound()) {
            return changeResultFor(proposedPath.status());
        }
        origin = proposedOrigin;
        requestedStops = List.copyOf(proposedStops);
        jumpPath = proposedPath.path();
        return ChangeResult.CHANGED;
    }

    private static PlanningResult derive(PlanetarySystem origin, List<PlanetarySystem> requestedStops,
          SegmentPlanner planner) {
        JumpPath derivedPath = new JumpPath();
        PlanetarySystem segmentOrigin = origin;
        for (PlanetarySystem requestedStop : requestedStops) {
            PlanningResult segmentResult = planner.calculate(segmentOrigin, requestedStop);
            if (segmentResult == null) {
                return PlanningResult.failed(PlanningStatus.NO_ROUTE);
            }
            if (!segmentResult.routeFound()) {
                return segmentResult;
            }
            JumpPath segment = segmentResult.path();
            if (segment.isEmpty()
                  || !Objects.equals(segmentOrigin, segment.getFirstSystem())
                  || !Objects.equals(requestedStop, segment.getLastSystem())) {
                return PlanningResult.failed(PlanningStatus.NO_ROUTE);
            }
            List<PlanetarySystem> segmentSystems = segment.getSystems();
            derivedPath.addSystems(segmentSystems.subList(derivedPath.isEmpty() ? 0 : 1, segmentSystems.size()));
            derivedPath.setTargetPlanet(segment.getTargetPlanet());
            segmentOrigin = requestedStop;
        }
        return PlanningResult.found(derivedPath);
    }

    private static ChangeResult changeResultFor(PlanningStatus status) {
        return switch (status) {
            case ACCESS_DENIED -> ChangeResult.ACCESS_DENIED;
            case NO_ROUTE -> ChangeResult.NO_ROUTE;
            case ROUTE_FOUND -> ChangeResult.NO_CHANGE;
        };
    }

    private static int indexOf(List<PlanetarySystem> systems, PlanetarySystem target, int fromIndex) {
        for (int index = fromIndex; index < systems.size(); index++) {
            if (Objects.equals(systems.get(index), target)) {
                return index;
            }
        }
        return -1;
    }

    private static JumpPath copyOf(JumpPath source) {
        JumpPath copy = new JumpPath();
        copy.addSystems(source.getSystems());
        copy.setTargetPlanet(source.getTargetPlanet());
        return copy;
    }
}

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
import java.util.Optional;

import jakarta.annotation.Nullable;
import mekhq.campaign.JumpPath;
import mekhq.campaign.universe.PlanetarySystem;

final class RoutePlanningIntent {
    @FunctionalInterface
    interface SegmentPlanner {
        JumpPath calculate(PlanetarySystem origin, PlanetarySystem destination);
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

    boolean plot(@Nullable PlanetarySystem proposedOrigin, @Nullable PlanetarySystem destination,
          SegmentPlanner planner) {
        if ((proposedOrigin == null) || (destination == null)) {
            return false;
        }
        return replace(proposedOrigin, List.of(destination), planner);
    }

    boolean append(@Nullable PlanetarySystem destination, SegmentPlanner planner) {
        if ((origin == null) || (destination == null)) {
            return false;
        }
        List<PlanetarySystem> proposedStops = new ArrayList<>(requestedStops);
        proposedStops.add(destination);
        return replace(origin, proposedStops, planner);
    }

    boolean removeRequestedStop(@Nullable PlanetarySystem stop, SegmentPlanner planner) {
        if ((origin == null) || (stop == null) || !requestedStops.contains(stop)) {
            return false;
        }
        List<PlanetarySystem> proposedStops = new ArrayList<>(requestedStops);
        proposedStops.remove(stop);
        return replace(origin, proposedStops, planner);
    }

    boolean trimAt(@Nullable PlanetarySystem destination, SegmentPlanner planner) {
        if ((origin == null) || (destination == null)) {
            return false;
        }
        int trimIndex = jumpPath.getSystems().indexOf(destination);
        if (trimIndex <= 0) {
            return false;
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

    private boolean replace(PlanetarySystem proposedOrigin, List<PlanetarySystem> proposedStops,
          SegmentPlanner planner) {
        Optional<JumpPath> proposedPath = derive(proposedOrigin, proposedStops, planner);
        if (proposedPath.isEmpty()) {
            return false;
        }
        origin = proposedOrigin;
        requestedStops = List.copyOf(proposedStops);
        jumpPath = proposedPath.get();
        return true;
    }

    private static Optional<JumpPath> derive(PlanetarySystem origin, List<PlanetarySystem> requestedStops,
          SegmentPlanner planner) {
        JumpPath derivedPath = new JumpPath();
        PlanetarySystem segmentOrigin = origin;
        for (PlanetarySystem requestedStop : requestedStops) {
            JumpPath segment = planner.calculate(segmentOrigin, requestedStop);
            if ((segment == null) || segment.isEmpty()
                  || !Objects.equals(segmentOrigin, segment.getFirstSystem())
                  || !Objects.equals(requestedStop, segment.getLastSystem())) {
                return Optional.empty();
            }
            List<PlanetarySystem> segmentSystems = segment.getSystems();
            derivedPath.addSystems(segmentSystems.subList(derivedPath.isEmpty() ? 0 : 1, segmentSystems.size()));
            segmentOrigin = requestedStop;
        }
        return Optional.of(derivedPath);
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
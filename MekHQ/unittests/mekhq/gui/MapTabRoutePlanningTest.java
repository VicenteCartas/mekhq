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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekhq.campaign.JumpPath;
import mekhq.campaign.universe.PlanetarySystem;
import org.junit.jupiter.api.Test;

class MapTabRoutePlanningTest {
    @Test
    void whatIfRouteIgnoresFleetProgressAndCannotBeginTransit() {
        PlanetarySystem fleetSystem = new PlanetarySystem("Fleet System");
        PlanetarySystem whatIfOrigin = new PlanetarySystem("What-If Origin");
        JumpPath proposedPath = path(whatIfOrigin, new PlanetarySystem("Destination"));

        assertTrue(MapTab.isWhatIfRoute(proposedPath, fleetSystem));
        assertEquals(0.0, MapTab.getProposedRouteTransitProgress(proposedPath, fleetSystem, 4.5));
        assertFalse(MapTab.canBeginTransit(proposedPath, fleetSystem, null));
    }

    @Test
    void actualOriginUsesFleetProgressAndRestoresTransitEligibility() {
        PlanetarySystem fleetSystem = new PlanetarySystem("Fleet System");
        JumpPath proposedPath = path(fleetSystem, new PlanetarySystem("Destination"));

        assertFalse(MapTab.isWhatIfRoute(proposedPath, fleetSystem));
        assertEquals(4.5, MapTab.getProposedRouteTransitProgress(proposedPath, fleetSystem, 4.5));
        assertTrue(MapTab.canBeginTransit(proposedPath, fleetSystem, null));
    }

    @Test
    void activeTransitPreventsStartingAProposedRoute() {
        PlanetarySystem fleetSystem = new PlanetarySystem("Fleet System");
        JumpPath proposedPath = path(fleetSystem, new PlanetarySystem("Proposed Destination"));
        JumpPath activePath = path(fleetSystem, new PlanetarySystem("Active Destination"));

        assertFalse(MapTab.canBeginTransit(proposedPath, fleetSystem, activePath));
    }

    private static JumpPath path(PlanetarySystem... systems) {
        JumpPath path = new JumpPath();
        for (PlanetarySystem system : systems) {
            path.addSystem(system);
        }
        return path;
    }
}
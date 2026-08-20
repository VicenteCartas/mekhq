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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InterstellarMapPanelMarkerLayoutTest {
    private static final double CENTER_X = 137.25;
    private static final double CENTER_Y = 82.75;
    private static final double DELTA = 0.000_001;

    @ParameterizedTest
    @ValueSource(doubles = { 3.0, 7.5, 12.0 })
    void transientMarkerStateDoesNotMoveStableGeometry(double markerSize) {
        InterstellarMapPanel.SystemMarkerLayout baseline = createLayout(markerSize,
              InterstellarMapPanel.RouteMarkerState.NONE, false, false);

        for (InterstellarMapPanel.RouteMarkerState routeState : InterstellarMapPanel.RouteMarkerState.values()) {
            for (boolean selected : new boolean[] { false, true }) {
                for (boolean hovered : new boolean[] { false, true }) {
                    assertStableGeometry(baseline, createLayout(markerSize, routeState, selected, hovered));
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = { 3.0, 7.5, 12.0 })
    void routeRadiiRemainDistinctAndOutsideSelection(double markerSize) {
        InterstellarMapPanel.SystemMarkerLayout none = createLayout(markerSize,
              InterstellarMapPanel.RouteMarkerState.NONE, false, false);
        InterstellarMapPanel.SystemMarkerLayout planned = createLayout(markerSize,
              InterstellarMapPanel.RouteMarkerState.PLANNED, false, false);
        InterstellarMapPanel.SystemMarkerLayout active = createLayout(markerSize,
              InterstellarMapPanel.RouteMarkerState.ACTIVE, false, false);

        assertEquals(0.0, none.navigationRadius(), DELTA);
        assertTrue(planned.navigationRadius() > planned.selectedRadius());
        assertTrue(active.navigationRadius() > planned.navigationRadius());
          assertTrue(active.navigationRadius() - active.hoveredRadius() >= 2.5,
              "active route ring must clear hover brackets");
    }

    private static InterstellarMapPanel.SystemMarkerLayout createLayout(double markerSize,
          InterstellarMapPanel.RouteMarkerState routeState, boolean selected, boolean hovered) {
        return InterstellarMapPanel.SystemMarkerLayout.create(CENTER_X, CENTER_Y, markerSize,
              routeState, selected, hovered);
    }

    private static void assertStableGeometry(InterstellarMapPanel.SystemMarkerLayout expected,
          InterstellarMapPanel.SystemMarkerLayout actual) {
        assertEquals(expected.ownershipRadius(), actual.ownershipRadius(), DELTA, "ownership radius");
        assertEquals(expected.selectedRadius(), actual.selectedRadius(), DELTA, "selected radius");
        assertEquals(expected.hoveredRadius(), actual.hoveredRadius(), DELTA, "hovered radius");
        assertEquals(expected.externalOrbitRadius(), actual.externalOrbitRadius(), DELTA, "external orbit");
        assertEquals(expected.labelX(), actual.labelX(), DELTA, "label X");
        assertEquals(expected.overrideRadius(), actual.overrideRadius(), DELTA, "override radius");
        assertPointEquals(expected.operationAnchor(), actual.operationAnchor(), "operation anchor");
        assertPointEquals(expected.serviceAnchor(), actual.serviceAnchor(), "service anchor");
        assertPointEquals(expected.routeBadgeAnchor(18.0), actual.routeBadgeAnchor(18.0), "route badge anchor");
        assertPointEquals(expected.routeBadgeAnchor(28.0), actual.routeBadgeAnchor(28.0), "large route badge anchor");
        assertPointEquals(expected.shipAnchor(), actual.shipAnchor(), "ship anchor");
        for (int capitalIndex = 0; capitalIndex < 3; capitalIndex++) {
            assertPointEquals(expected.capitalAnchor(capitalIndex, 3), actual.capitalAnchor(capitalIndex, 3),
                  "capital anchor " + capitalIndex);
        }
    }

    private static void assertPointEquals(Point2D.Double expected, Point2D.Double actual, String message) {
        assertEquals(expected.x, actual.x, DELTA, message + " X");
        assertEquals(expected.y, actual.y, DELTA, message + " Y");
    }
}

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
package mekhq.gui.gdx.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

class StarMapViewTest {
    private static final float HIDDEN = Float.NaN;

    @Test
    void theCursorPicksTheNearestSystemWithinReach() {
        float[] screen = { 100, 100, 110, 104, 300, 300 };
        assertEquals(1, StarMapView.nearest(screen, 108, 103, StarMapView.PICK_RADIUS));
        assertEquals(0, StarMapView.nearest(screen, 98, 99, StarMapView.PICK_RADIUS));
    }

    @Test
    void nothingIsPickedOutOfReachOrBehindTheCamera() {
        float[] screen = { 100, 100, HIDDEN, HIDDEN };
        assertEquals(-1, StarMapView.nearest(screen, 140, 100, StarMapView.PICK_RADIUS));
        assertEquals(-1, StarMapView.nearest(new float[] { HIDDEN, HIDDEN }, 0, 0, StarMapView.PICK_RADIUS));
        assertEquals(-1, StarMapView.nearest(new float[0], 0, 0, StarMapView.PICK_RADIUS));
    }

    @Test
    void corewardLiesAwayFromTheCamera() {
        Vector3 out = StarMapView.world(12.5, 40, new Vector3());
        assertEquals(new Vector3(12.5f, 0, -40), out);
    }
}

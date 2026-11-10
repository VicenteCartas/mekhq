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
package mekhq.campaign.universe.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InfluenceMapTest {
    @Test
    void aLoneSystemHoldsSpaceRoundItToWhereItsInfluenceFadesBelowTheThreshold() {
        InfluenceMap land = map(new float[] { 0 }, new float[] { 0 }, new int[] { 3 }, new boolean[] { false });

        // (1 - r²)² = 0.2 at r = 0.7435 of the reach
        float held = (float) Math.sqrt(1 - Math.sqrt(InfluenceMap.THRESHOLD)) * InfluenceMap.REACH;
        assertEquals(3, land.owner(land.column(0), land.row(0)));
        assertEquals(held, land.distance(land.column(0), land.row(0)), 2.5f, "from the centre to the edge");
        assertEquals(0, land.owner(land.column(held + 10), land.row(0)));
        assertEquals(10, land.distance(land.column(held + 10), land.row(0)), 2.5f, "outside, to the edge");
        assertEquals(3, land.owner(land.column(held - 10), land.row(0)));
        assertEquals(10, land.distance(land.column(held - 10), land.row(0)), 2.5f, "inside, to the edge");
    }

    @Test
    void twoEqualRealmsMeetHalfwayBetweenTheirSystems() {
        InfluenceMap land = map(new float[] { 0, 40 }, new float[] { 0, 0 }, new int[] { 1, 2 },
              new boolean[] { false, false });

        assertEquals(1, land.owner(land.column(10), land.row(0)));
        assertEquals(2, land.owner(land.column(30), land.row(0)));
        assertEquals(10, land.distance(land.column(10), land.row(0)), 2.5f);
        assertEquals(10, land.distance(land.column(30), land.row(0)), 2.5f);
    }

    @Test
    void moreSystemsOutweighFewer() {
        InfluenceMap land = map(new float[] { -5, 0, 5, 40 }, new float[] { 0, 5, 0, 0 }, new int[] { 1, 1, 1, 2 },
              new boolean[] { false, false, false, false });

        assertEquals(1, land.owner(land.column(22), land.row(0)), "the larger realm reaches past halfway");
    }

    @Test
    void disputedGroundNamesWhoElseClaimsIt() {
        InfluenceMap land = map(new float[] { 0, 0 }, new float[] { 0, 0 }, new int[] { 1, 2 },
              new boolean[] { true, true });

        int column = land.column(0);
        int row = land.row(0);
        int owner = land.owner(column, row);
        assertTrue(owner == 1 || owner == 2);
        assertEquals(3 - owner, land.partner(column, row));
        assertEquals(1, land.dispute(column, row), 0.05f);
        assertEquals(0, land.dispute(land.column(InfluenceMap.DISPUTE_REACH + 5), row));
    }

    @Test
    void noClaimsHoldNothing() {
        InfluenceMap land = map(new float[0], new float[0], new int[0], new boolean[0]);

        assertEquals(0, land.owner(land.column(0), land.row(0)));
        assertEquals(InfluenceMap.FAR / InfluenceMap.DISTANCE_SCALE, land.distance(land.column(0), land.row(0)));
    }

    private static InfluenceMap map(float[] x, float[] y, int[] realm, boolean[] disputed) {
        return InfluenceMap.of(x.length, x, y, realm, disputed, -100, -100, 100, 100);
    }
}

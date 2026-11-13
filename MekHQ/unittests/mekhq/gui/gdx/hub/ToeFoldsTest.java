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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import org.junit.jupiter.api.Test;

class ToeFoldsTest {
    private static final float EPSILON = 1e-4f;
    private static final float COMPANY = ToeFolds.STEP * ToeFolds.UNITS_FOLD;
    private static final float BATTALION = ToeFolds.STEP * COMPANY;

    @Test
    void eachFormationFoldsAStepAfterTheLastOfWhatsUnderIt() {
        ToeSnapshot snapshot = ToeLayoutTest.regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        Group battalion = snapshot.root().children().get(0);
        Group company = battalion.children().get(0);
        Group lance = company.children().get(0);
        Group replacements = snapshot.root().children().get(2);

        assertEquals(ToeFolds.UNITS_FOLD, folds.threshold(lance.id()), EPSILON);
        assertEquals(COMPANY, folds.threshold(company.id()), EPSILON);
        assertEquals(ToeFolds.UNITS_FOLD, folds.threshold(ToeFolds.attached(company.id())), EPSILON);
        assertEquals(BATTALION, folds.threshold(battalion.id()), EPSILON);
        assertEquals(ToeFolds.UNITS_FOLD, folds.threshold(replacements.id()), EPSILON);
        assertEquals(ToeFolds.UNITS_FOLD, folds.threshold(ToeFolds.UNASSIGNED), EPSILON);
        assertEquals(0, folds.threshold(snapshot.root().id()), "the force itself never folds");
    }

    @Test
    void theZoomMustGoWellPastAFoldToFoldOrOpen() {
        ToeSnapshot snapshot = ToeLayoutTest.regiment();
        int lance = snapshot.root().children().get(0).children().get(0).children().get(0).id();
        ToeFolds folds = new ToeFolds(snapshot);
        folds.reset(40);

        assertFalse(folds.update(ToeFolds.UNITS_FOLD - 1), "just under the fold");
        assertFalse(folds.folded(lance));
        assertTrue(folds.update(ToeFolds.UNITS_FOLD / ToeFolds.HYSTERESIS - 0.1f));
        assertTrue(folds.folded(lance));
        assertFalse(folds.update(ToeFolds.UNITS_FOLD + 1), "just over the fold");
        assertTrue(folds.folded(lance));
        assertTrue(folds.update(ToeFolds.UNITS_FOLD * ToeFolds.HYSTERESIS + 0.1f));
        assertFalse(folds.folded(lance));
    }

    @Test
    void stepsRunFromClosestInToFarthestOut() {
        ToeFolds folds = new ToeFolds(ToeLayoutTest.regiment());

        assertArrayEquals(new float[] { ToeFolds.UNITS_FOLD, COMPANY, BATTALION }, folds.steps(), EPSILON);
        assertEquals(BATTALION, folds.lowest(), EPSILON);
    }
}

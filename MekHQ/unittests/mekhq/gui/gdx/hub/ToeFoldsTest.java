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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import org.junit.jupiter.api.Test;

class ToeFoldsTest {
    @Test
    void eachStepInOpensTheNextTierOfTheTree() {
        ToeSnapshot snapshot = ToeLayoutTest.regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        Group battalion = snapshot.root().children().get(0);
        Group company = battalion.children().get(0);
        Group lance = company.children().get(0);

        assertEquals(5, folds.steps(), "battalions, companies, lances, then the units, then close in");
        assertEquals(0, folds.step());
        assertTrue(folds.folded(battalion.id()));
        assertFalse(folds.folded(snapshot.root().id()), "the force itself never folds");

        folds.step(1);
        assertFalse(folds.folded(battalion.id()));
        assertTrue(folds.folded(company.id()));

        folds.step(2);
        assertFalse(folds.folded(company.id()));
        assertTrue(folds.folded(lance.id()));
        assertTrue(folds.folded(ToeFolds.attached(company.id())));

        folds.step(folds.unitsStep());
        assertTrue(folds.units());
        assertFalse(folds.folded(lance.id()));
        assertFalse(folds.folded(ToeFolds.attached(company.id())));
        assertFalse(folds.folded(ToeFolds.UNASSIGNED));
    }

    @Test
    void whatHoldsOnlyUnitsStaysACardUntilTheUnitsShow() {
        ToeSnapshot snapshot = ToeLayoutTest.regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        Group replacements = snapshot.root().children().get(2);
        folds.step(folds.unitsStep() - 1);

        assertTrue(folds.folded(replacements.id()), "a formation with none under it, a tier up");
        assertTrue(folds.folded(ToeFolds.UNASSIGNED));
        assertEquals(folds.unitsStep(), folds.opening(replacements.id()));
        assertEquals(folds.unitsStep(), folds.opening(ToeFolds.UNASSIGNED));
    }

    @Test
    void aCardOpensAtTheStepOfItsTier() {
        ToeSnapshot snapshot = ToeLayoutTest.regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        Group battalion = snapshot.root().children().get(0);

        assertEquals(1, folds.opening(battalion.id()));
        assertEquals(2, folds.opening(battalion.children().get(0).id()));
        assertEquals(0, folds.opening(12345), "off the table, it stays where it is");
    }

    @Test
    void theStepsStopAtEitherEndAndSnapTheScale() {
        ToeFolds folds = new ToeFolds(ToeLayoutTest.regiment());

        assertFalse(folds.step(-1));
        assertEquals(0, folds.step());
        assertTrue(folds.step(99));
        assertEquals(folds.steps() - 1, folds.step());
        assertEquals(ToeFolds.CLOSE_PIXELS, folds.cellPixels());
        assertEquals(ToeFolds.UNITS_PIXELS, folds.cellPixels(folds.unitsStep()));
        assertEquals(ToeFolds.CARDS_PIXELS, folds.cellPixels(0));
    }
}

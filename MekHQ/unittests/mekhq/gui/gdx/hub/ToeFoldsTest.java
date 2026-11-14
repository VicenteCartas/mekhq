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
    }

    @Test
    void aCardOpensByHandWhereItStandsAndItsSiblingsStayFolded() {
        ToeSnapshot snapshot = ToeLayoutTest.regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        Group first = snapshot.root().children().get(0);
        Group second = snapshot.root().children().get(1);

        assertTrue(folds.toggle(first.id()));
        assertEquals(0, folds.step(), "opening a card leaves the step where it is");
        assertFalse(folds.folded(first.id()));
        assertTrue(folds.folded(second.id()));
        assertTrue(folds.folded(first.children().get(0).id()), "what's under it opens a tier at a time");

        assertTrue(folds.toggle(first.id()));
        assertTrue(folds.folded(first.id()), "a second click folds it back");
        assertFalse(folds.toggle(snapshot.root().id()), "the force itself never folds");
        assertFalse(folds.toggle(12345));
    }

    @Test
    void steppingOutForgetsWhatWasOpenedByHandAndSteppingInKeepsIt() {
        ToeSnapshot snapshot = ToeLayoutTest.regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        Group battalion = snapshot.root().children().get(0);
        Group company = battalion.children().get(0);
        Group other = battalion.children().get(1);
        folds.step(1);
        folds.toggle(company.id());
        folds.toggle(other.id());
        folds.toggle(other.id());
        folds.toggle(battalion.id());

        assertTrue(folds.folded(battalion.id()), "an open formation folds by hand too");
        folds.step(2);
        assertFalse(folds.folded(battalion.id()), "stepping in drops what was folded by hand");
        assertFalse(folds.folded(company.id()));
        folds.toggle(company.children().get(0).id());
        folds.step(1);
        assertTrue(folds.folded(company.id()), "stepping out forgets what was opened by hand");
        assertTrue(folds.folded(battalion.children().get(1).id()));

        folds.toggle(company.id());
        folds.forget();
        assertTrue(folds.folded(company.id()));
    }

    @Test
    void whatWasOpenedByHandCarriesOverToTheForceAsItComesBack() {
        ToeSnapshot snapshot = ToeLayoutTest.regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        Group battalion = snapshot.root().children().get(0);
        folds.toggle(battalion.id());

        ToeFolds next = new ToeFolds(snapshot);
        next.carry(folds);
        assertFalse(next.folded(battalion.id()));
        assertTrue(next.folded(snapshot.root().children().get(1).id()));
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

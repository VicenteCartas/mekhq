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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import mekhq.campaign.force.FormationSlot;
import mekhq.campaign.force.FormationTemplate;
import mekhq.gui.gdx.hub.ToeSlots.Fill;
import mekhq.gui.gdx.hub.ToeSlots.Fit;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import mekhq.gui.gdx.hub.ToeSnapshot.Kind;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;
import org.junit.jupiter.api.Test;

class ToeSlotsTest {
    private static final FormationSlot LIGHT = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.LIGHT);
    private static final FormationSlot MEDIUM = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.MEDIUM);
    private static final FormationSlot HEAVY = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.HEAVY);

    @Test
    void aFormationShowsItsVacantSlotsAndTheUnitsInNone() {
        Card medium = card(MEDIUM);
        Card heavy = card(HEAVY);
        Group lance = lance(List.of(medium, heavy), List.of(HEAVY, LIGHT));

        Fill fill = ToeSlots.of(lance);

        assertEquals(List.of(LIGHT), fill.vacant());
        assertEquals(Set.of(medium.id()), fill.misfits());
    }

    @Test
    void aFormationAuthorizingNothingHasNeither() {
        Fill fill = ToeSlots.of(lance(List.of(card(MEDIUM)), List.of()));

        assertTrue(fill.vacant().isEmpty());
        assertTrue(fill.misfits().isEmpty());
    }

    @Test
    void theTallyCountsTheFormationsUnderIt() {
        Group under = lance(List.of(card(LIGHT)), List.of(LIGHT, LIGHT));
        Group over = lance(List.of(card(MEDIUM), card(MEDIUM)), List.of(MEDIUM));
        Group company = new Group(9, "Company", null, List.of(under, over), List.of(card(HEAVY)), false,
              List.of(LIGHT));

        assertArrayEquals(new int[] { 2, 2 }, ToeSlots.tally(company));
        assertArrayEquals(new int[] { 1, 1 }, ToeSlots.own(company));
    }

    @Test
    void carryingAUnitToAFormationSaysWhetherItFillsAVacantSlot() {
        Card light = card(LIGHT);
        Group lance = lance(List.of(card(HEAVY)), List.of(HEAVY, LIGHT));

        assertEquals(Fit.FITS, ToeSlots.fit(lance, List.of(light)));
        assertEquals(Fit.MISFITS, ToeSlots.fit(lance, List.of(card(MEDIUM))));
        assertEquals(Fit.MISFITS, ToeSlots.fit(lance, List.of(light, card(LIGHT))));
        assertEquals(Fit.OPEN, ToeSlots.fit(lance(List.of(), List.of()), List.of(light)));
        assertEquals(Fit.OPEN, ToeSlots.fit(null, List.of(light)));
    }

    @Test
    void movingAUnitWithinItsFormationStillFits() {
        Card heavy = card(HEAVY);
        Group lance = lance(List.of(heavy), List.of(HEAVY, LIGHT));

        assertEquals(Fit.FITS, ToeSlots.fit(lance, List.of(heavy)));
    }

    @Test
    void aTemplateKeepsTheSlotsSetAndAddsOpenOnesOfTheUsualKind() {
        FormationSlot anyMek = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.ANY);
        FormationSlot anyTank = new FormationSlot(FormationSlot.Kind.VEHICLE, FormationSlot.Weight.ANY);
        FormationSlot tank = new FormationSlot(FormationSlot.Kind.VEHICLE, FormationSlot.Weight.HEAVY);
        List<FormationSlot> lance = List.of(LIGHT, MEDIUM, HEAVY, HEAVY);

        assertEquals(List.of(LIGHT, MEDIUM, HEAVY, HEAVY, anyMek),
              ToeSlots.template(FormationTemplate.STAR, lance, List.of()));
        assertEquals(List.of(LIGHT, MEDIUM, HEAVY, HEAVY),
              ToeSlots.template(FormationTemplate.LANCE, List.of(LIGHT, MEDIUM, HEAVY, HEAVY, anyMek), List.of()));
        assertEquals(List.of(anyTank, anyTank, anyTank, anyTank, anyTank, anyTank),
              ToeSlots.template(FormationTemplate.LEVEL_II, List.of(), List.of(card(tank), card(tank), card(LIGHT))));
    }

    private static Group lance(List<Card> units, List<FormationSlot> slots) {
        return new Group(1, "Lance", null, List.of(), units, false, slots);
    }

    private static Card card(FormationSlot fit) {
        return new Card(UUID.randomUUID(), "Unit", "", Kind.MEK, 50, null, null, Condition.SOUND, Work.NONE,
              Crew.FULL, false, true, false, false, 0, fit);
    }
}

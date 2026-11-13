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
package mekhq.campaign.force;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import mekhq.MHQConstants;
import mekhq.campaign.Campaign;
import mekhq.campaign.force.FormationSlot.Kind;
import mekhq.campaign.force.FormationSlot.Weight;
import mekhq.utilities.MHQXMLUtility;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class FormationSlotTest {
    private static final FormationSlot ANY_MEK = new FormationSlot(Kind.MEK, Weight.ANY);
    private static final FormationSlot LIGHT_MEK = new FormationSlot(Kind.MEK, Weight.LIGHT);
    private static final FormationSlot MEDIUM_MEK = new FormationSlot(Kind.MEK, Weight.MEDIUM);
    private static final FormationSlot ANY_LIGHT = new FormationSlot(Kind.ANY, Weight.LIGHT);

    @Test
    void aSlotAdmitsWhatItNamesAndAnythingWhereItNamesNothing() {
        assertTrue(ANY_MEK.admits(LIGHT_MEK));
        assertTrue(ANY_LIGHT.admits(new FormationSlot(Kind.VEHICLE, Weight.LIGHT)));
        assertFalse(LIGHT_MEK.admits(MEDIUM_MEK));
        assertFalse(LIGHT_MEK.admits(new FormationSlot(Kind.VEHICLE, Weight.LIGHT)));
        // A unit no slot can name, a DropShip's weight say, only fits a slot that leaves it open
        assertFalse(LIGHT_MEK.admits(ANY_MEK));
        assertTrue(FormationSlot.OPEN.admits(FormationSlot.OPEN));
    }

    @Test
    void assignMovesAUnitAlongToSeatAnotherThatFitsOnlyWhereItSat() {
        List<FormationSlot> slots = List.of(ANY_MEK, ANY_LIGHT);

        int[] seats = FormationSlot.assign(slots, List.of(LIGHT_MEK, MEDIUM_MEK));

        assertArrayEquals(new int[] { 1, 0 }, seats);
    }

    @Test
    void assignLeavesThoseOverStrengthOrFittingNothingUnseated() {
        assertArrayEquals(new int[] { 0, -1 }, FormationSlot.assign(List.of(ANY_MEK), List.of(LIGHT_MEK,
              MEDIUM_MEK)));
        assertArrayEquals(new int[] { -1 }, FormationSlot.assign(List.of(LIGHT_MEK), List.of(MEDIUM_MEK)));
    }

    @Test
    void aSlotReadsBackFromItsCode() {
        for (Kind kind : Kind.values()) {
            for (Weight weight : Weight.values()) {
                FormationSlot slot = new FormationSlot(kind, weight);
                assertEquals(slot, FormationSlot.parse(slot.code()));
            }
        }
        assertEquals(LIGHT_MEK, FormationSlot.parse(" mek:light "));
        assertNull(FormationSlot.parse("MEK"));
        assertNull(FormationSlot.parse("MEK:LIGHT:EXTRA"));
        assertNull(FormationSlot.parse("WALKER:LIGHT"));
    }

    @Test
    void aFormationsSlotsAreSavedAndLoaded() throws Exception {
        Formation formation = new Formation("Recon Lance");
        formation.setSlots(List.of(LIGHT_MEK, LIGHT_MEK, ANY_LIGHT, FormationSlot.OPEN));
        StringWriter written = new StringWriter();
        try (PrintWriter writer = new PrintWriter(written)) {
            formation.writeToXML(writer, 0);
        }
        Element element = MHQXMLUtility.newSafeDocumentBuilder()
                                .parse(new ByteArrayInputStream(written.toString().getBytes(StandardCharsets.UTF_8)))
                                .getDocumentElement();
        Campaign campaign = mock(Campaign.class);
        when(campaign.getPlayerForce()).thenReturn(mock(PlayerForce.class));

        Formation loaded = Formation.generateInstanceFromXML(element, campaign, MHQConstants.VERSION);

        assertNotNull(loaded);
        assertEquals(formation.getSlots(), loaded.getSlots());
    }

    @Test
    void aFormationAuthorizingNothingSavesNoSlots() {
        StringWriter written = new StringWriter();
        try (PrintWriter writer = new PrintWriter(written)) {
            new Formation("Lance").writeToXML(writer, 0);
        }

        assertFalse(written.toString().contains("<slots>"));
    }
}

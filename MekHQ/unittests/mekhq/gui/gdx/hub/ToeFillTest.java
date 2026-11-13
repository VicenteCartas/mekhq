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

import java.util.List;
import java.util.UUID;

import mekhq.campaign.force.FormationSlot;
import mekhq.gui.gdx.hub.ToeFill.Candidate;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import mekhq.gui.gdx.hub.ToeSnapshot.Kind;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;
import org.junit.jupiter.api.Test;

class ToeFillTest {
    private static final FormationSlot LIGHT = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.LIGHT);
    private static final FormationSlot HEAVY = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.HEAVY);

    @Test
    void offersWhatFitsTheUnassignedAndSoundFirstAndThoseLeavingAGapLast() {
        Card held = card("Held", HEAVY, 0, false);
        Card slotted = card("Slotted", LIGHT, 0, false);
        Card loose = card("Loose", LIGHT, 0, false);
        Card free = card("Free", LIGHT, 0, false);
        Card away = card("Away", LIGHT, 0, true);
        Card heavy = card("Heavy", HEAVY, 0, false);
        Card dented = new Card(UUID.randomUUID(), "Dented", "", Kind.MEK, 50, null, null, Condition.LIGHT, Work.NONE,
              Crew.FULL, false, true, false, false, 0, LIGHT);
        Group target = group(1, List.of(held), List.of(HEAVY, LIGHT));
        Group strict = group(2, List.of(slotted), List.of(LIGHT));
        Group open = group(3, List.of(loose), List.of());
        Group root = new Group(0, "Force", null, List.of(target, strict, open), List.of(), false, List.of());
        ToeSnapshot shown = new ToeSnapshot("Force", root, List.of(dented, free, away, heavy), List.of());

        List<Candidate> candidates = ToeFill.candidates(shown, target, LIGHT);

        assertEquals(List.of("Free", "Dented", "Loose", "Slotted"), candidates.stream().map(c -> c.card().name()).toList());
        assertEquals(List.of(false, false, false, true), candidates.stream().map(Candidate::leavesVacancy).toList());
        assertEquals(strict, candidates.get(3).from());
    }

    @Test
    void aFormationIsWhereMostOfItsUnitsAreOrElseWhereItsParentIs() {
        Group empty = group(1, List.of(), List.of(LIGHT));
        Group company = new Group(2, "Company", null, List.of(empty),
              List.of(card("A", HEAVY, 1, false), card("B", HEAVY, 1, false), card("C", HEAVY, 0, false)), false,
              List.of());
        Card here = card("Here", LIGHT, 1, false);
        Card there = card("There", LIGHT, 0, false);
        ToeSnapshot shown = new ToeSnapshot("Force", company, List.of(there, here), List.of("Kestrel"));

        assertEquals(1, ToeFill.place(shown, empty));
        List<Candidate> candidates = ToeFill.candidates(shown, empty, LIGHT);
        assertEquals(List.of("Here", "There"), candidates.stream().map(c -> c.card().name()).toList());
        assertEquals(List.of(false, true), candidates.stream().map(Candidate::elsewhere).toList());
    }

    private static Group group(int id, List<Card> units, List<FormationSlot> slots) {
        return new Group(id, "Lance " + id, null, List.of(), units, false, slots);
    }

    private static Card card(String name, FormationSlot fit, int place, boolean deployed) {
        return new Card(UUID.randomUUID(), name, "", Kind.MEK, 50, null, null, Condition.SOUND, Work.NONE,
              Crew.FULL, false, true, deployed, false, place, fit);
    }
}

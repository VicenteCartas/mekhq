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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import megamek.common.units.Entity;
import mekhq.campaign.force.FormationSlot;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import mekhq.gui.gdx.hub.ToeSnapshot.Kind;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;
import mekhq.gui.gdx.hub.ToeTable.Place;
import org.junit.jupiter.api.Test;

class ToeSnapshotTest {
    private static final FormationSlot MEDIUM_MEK = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.MEDIUM);
    @Test
    void conditionPutsSalvageAndInoperableAboveDamage() {
        assertEquals(Condition.SALVAGE, Condition.of(Entity.DMG_NONE, true, false));
        assertEquals(Condition.INOPERABLE, Condition.of(Entity.DMG_LIGHT, false, true));
        assertEquals(Condition.SOUND, Condition.of(Entity.DMG_NONE, true, true));
        assertEquals(Condition.HEAVY, Condition.of(Entity.DMG_HEAVY, true, true));
        assertEquals(Condition.CRIPPLED, Condition.of(Entity.DMG_CRIPPLED, true, true));
        assertTrue(Condition.CRIPPLED.fights());
        assertFalse(Condition.INOPERABLE.fights());
    }

    @Test
    void workShowsTheLongestLastingFirst() {
        assertEquals(Work.MOTHBALLING, Work.of(false, true, true, true));
        assertEquals(Work.ACTIVATING, Work.of(true, true, false, false));
        assertEquals(Work.MOTHBALLED, Work.of(true, false, true, true));
        assertEquals(Work.REFIT, Work.of(false, false, true, true));
        assertEquals(Work.REPAIR, Work.of(false, false, false, true));
        assertEquals(Work.NONE, Work.of(false, false, false, false));
    }

    @Test
    void aUnitIsReadyOnlyWholeEnoughCrewedHomeAndIdle() {
        assertTrue(card(Condition.HEAVY, Work.NONE, Crew.PARTIAL, false, false, 0).ready());
        assertFalse(card(Condition.INOPERABLE, Work.NONE, Crew.FULL, false, false, 0).ready());
        assertFalse(card(Condition.SOUND, Work.REPAIR, Crew.FULL, false, false, 0).ready());
        assertFalse(card(Condition.SOUND, Work.NONE, Crew.NONE, false, false, 0).ready());
        assertFalse(card(Condition.SOUND, Work.NONE, Crew.FULL, true, false, 0).ready());
        assertFalse(card(Condition.SOUND, Work.NONE, Crew.FULL, false, true, 0).ready());
    }

    @Test
    void aForceInOnePlaceHasNoPlacesToNarrowTo() {
        ToeSnapshot snapshot = snapshot(List.of(), sound(0), sound(0));

        List<Place> places = ToeTable.places(snapshot);

        assertEquals(1, places.size());
        assertNull(places.get(0).units());
    }

    @Test
    void aSpreadForceNarrowsToEachPlaceItIsAt() {
        Card home = sound(0);
        Card atFirst = sound(1);
        Card atSecond = sound(2);
        Card moving = card(Condition.SOUND, Work.NONE, Crew.FULL, false, true, 0);
        ToeSnapshot snapshot = snapshot(List.of("Kestrel Station", "Corvin Yards"), home, atFirst, atSecond, moving);

        List<Place> places = ToeTable.places(snapshot);

        assertEquals(5, places.size());
        assertNull(places.get(0).units());
        assertEquals("Kestrel Station", places.get(2).name());
        assertEquals("Corvin Yards", places.get(3).name());
        assertEquals(List.of(home, moving), picked(places.get(1), snapshot));
        assertEquals(List.of(atFirst), picked(places.get(2), snapshot));
        assertEquals(List.of(atSecond), picked(places.get(3), snapshot));
        assertEquals(List.of(moving), picked(places.get(4), snapshot));
    }

    @Test
    void aForceUnderWayAloneStillNarrowsToItsTransit() {
        Card moving = card(Condition.SOUND, Work.NONE, Crew.FULL, false, true, 0);
        ToeSnapshot snapshot = snapshot(List.of(), sound(0), moving);

        List<Place> places = ToeTable.places(snapshot);

        assertEquals(3, places.size());
        assertEquals(List.of(moving), picked(places.get(2), snapshot));
    }

    @Test
    void theMainForceTakesInWhatTravelsOnABasesBooks() {
        Card home = sound(0);
        Card toBase = card(Condition.SOUND, Work.NONE, Crew.FULL, false, true, 1);
        ToeSnapshot snapshot = snapshot(List.of("Kestrel Station"), home, toBase);

        List<Place> places = ToeTable.places(snapshot);

        assertEquals(List.of(home, toBase), picked(places.get(1), snapshot));
        assertEquals(List.of(toBase), picked(places.get(2), snapshot));
    }

    private static List<Card> picked(Place place, ToeSnapshot snapshot) {
        return snapshot.allUnits().stream().filter(place.units()).toList();
    }

    @Test
    void aFormationWhoseEchelonIsNotKnownStandsOneUpFromTheBiggestUnderIt() {
        Group team = new Group(1, "Team", null, List.of(), List.of(), false, List.of(), 0);
        Group lance = new Group(2, "Lance", null, List.of(), List.of(), false, List.of());
        Group company = new Group(3, "Company", null, List.of(team, lance), List.of(), false, List.of());
        Group battalion = new Group(4, "Battalion", null, List.of(company), List.of(), false, List.of(), 3);

        assertEquals(0, team.reckonedEchelon());
        assertEquals(1, lance.reckonedEchelon(), "one with none under it, a lance");
        assertEquals(2, company.reckonedEchelon());
        assertEquals(3, battalion.reckonedEchelon(), "the echelon it's given wins");
        assertEquals(4, new Group(5, "Regiment", null, List.of(battalion), List.of(), false, List.of())
              .reckonedEchelon());
    }

    @Test
    void aDeployedUnitCountsAsAwayRatherThanFitToFight() {
        Card deployed = card(Condition.SOUND, Work.NONE, Crew.FULL, true, false, 0);

        assertEquals(ToeSnapshot.Issue.DEPLOYED, ToeSnapshot.Issue.first(deployed));
        assertEquals(2, ToeSnapshot.Issue.tally(List.of(deployed, deployed, sound(0)))[ToeSnapshot.Issue.DEPLOYED
              .ordinal()]);
        assertNull(ToeSnapshot.Issue.first(sound(0)));
    }

    private static ToeSnapshot snapshot(List<String> bases, Card... units) {
        Group lance = new Group(2, "Lance", null, List.of(), List.of(units), false, List.of());
        Group root = new Group(1, "Force", null, List.of(lance), List.of(), false, List.of());
        return new ToeSnapshot("Force", root, List.of(), bases);
    }

    private static Card sound(int place) {
        return card(Condition.SOUND, Work.NONE, Crew.FULL, false, false, place);
    }

    private static Card card(Condition condition, Work work, Crew crew, boolean deployed, boolean transit,
          int place) {
        return new Card(UUID.randomUUID(), "Unit", "", Kind.MEK, 50, null, null, condition, work, crew, false, true,
              deployed, transit, place, MEDIUM_MEK);
    }
}

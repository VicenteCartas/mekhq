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
package mekhq.gui.model.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import megamek.common.universe.FactionTag;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.events.LocationChangedEvent;
import mekhq.campaign.events.NewDayEvent;
import mekhq.campaign.events.PlanetarySystemsChangedEvent;
import mekhq.campaign.events.persons.PersonChangedEvent;
import mekhq.campaign.force.Detachment;
import mekhq.campaign.force.PlayerForce;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.enums.HPGRating;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StarMapTest {
    private static final LocalDate TODAY = LocalDate.of(3025, 6, 1);

    private final Campaign campaign = mock(Campaign.class);
    private final AbstractLocation location = mock(AbstractLocation.class);
    private final ArrayList<PlanetarySystem> systems = new ArrayList<>();
    private final Faction lyrans = faction("LA", "Lyran Commonwealth", 0x2060C0, false);
    private final Faction kurita = faction("DC", "Draconis Combine", 0xC02020, false);
    private final Faction abandoned = faction("ABN", "Abandoned", 0x404040, true);
    private PlanetarySystem tharkad;
    private PlanetarySystem skye;

    @BeforeEach
    void setUp() {
        PlayerForce force = mock(PlayerForce.class);
        Detachment detachment = mock(Detachment.class);
        when(campaign.getLocalDate()).thenReturn(TODAY);
        when(campaign.getPlayerForce()).thenReturn(force);
        when(force.getForceDetachment()).thenReturn(detachment);
        when(detachment.getCurrentLocation()).thenReturn(location);
        when(campaign.getSystems()).thenReturn(systems);
        tharkad = system("tharkad", "Tharkad", -100.0, 300.0, Set.of(lyrans));
        skye = system("skye", "Skye", -80.0, 200.0, Set.of(kurita, lyrans));
        when(location.getCurrentSystem()).thenReturn(tharkad);
    }

    @Test
    void itListsEverySystemWithAPlaceInItsOwnersColours() {
        system("lost", "Lost", null, 5.0, Set.of(lyrans));
        StarMap map = StarMap.of(campaign);

        assertEquals(TODAY, map.date());
        assertEquals(2, map.stars().size(), "a system without a position has no place on the map");
        StarMap.Star first = map.stars().get(0);
        assertEquals(new StarMap.Star("tharkad", "Tharkad", -100, 300, 0x2060C0, "Lyran Commonwealth",
              "Lyran Commonwealth", true), first);
        StarMap.Star shared = map.stars().get(1);
        assertEquals("Draconis Combine / Lyran Commonwealth", shared.owner(), "owners in name order");
        assertEquals(0xC02020, shared.rgb(), "the first owner's colour");
        assertEquals("Draconis Combine", shared.faction(), "the owner whose colour it wears");
        assertEquals("tharkad", map.current());
        assertTrue(map.route().isEmpty());
    }

    @Test
    void abandonedAndUnclaimedSystemsAreUnsettled() {
        system("ruin", "Ruin", 0.0, 0.0, Set.of(abandoned));
        system("void", "Void", 1.0, 1.0, Set.of());
        StarMap map = StarMap.of(campaign);

        StarMap.Star ruin = map.stars().get(2);
        assertFalse(ruin.settled());
        assertEquals("Abandoned", ruin.owner());
        StarMap.Star empty = map.stars().get(3);
        assertFalse(empty.settled());
        assertEquals("", empty.owner());
        assertEquals(StarMap.UNCLAIMED, empty.rgb());
        assertEquals("", empty.faction());
    }

    @Test
    void aSettledOwnerLendsItsColourBeforeTheAbandoned() {
        system("held", "Held", 0.0, 0.0, Set.of(abandoned, kurita));
        StarMap.Star held = StarMap.of(campaign).stars().get(2);
        assertTrue(held.settled());
        assertEquals(0xC02020, held.rgb());
        assertEquals("Draconis Combine / Abandoned", held.owner());
    }

    @Test
    void travellingTheRouteRunsFromTheForceToItsDestination() {
        JumpPath path = new JumpPath();
        path.addSystem(tharkad);
        path.addSystem(skye);
        when(location.getJumpPath()).thenReturn(path);
        assertEquals(List.of("tharkad", "skye"), StarMap.of(campaign).route());
    }

    @Test
    void nowhereTheMapHasNoCurrentSystem() {
        when(campaign.getPlayerForce().getForceDetachment().getCurrentLocation()).thenReturn(null);
        StarMap map = StarMap.of(campaign);
        assertNull(map.current());
        assertTrue(map.route().isEmpty());
    }

    @Test
    void theDetailOfAnotherSystemCountsTheJumpsThere() {
        JumpPath path = new JumpPath();
        path.addSystem(tharkad);
        path.addSystem(skye);
        when(campaign.calculateJumpPath(tharkad, skye)).thenReturn(path);
        when(tharkad.getDistanceTo(skye)).thenReturn(102.0);
        when(skye.getPopulation(TODAY)).thenReturn(8_000_000_000L);
        when(skye.getHPG(TODAY)).thenReturn(HPGRating.A);
        when(skye.getRechargeTime(TODAY, false)).thenReturn(141.0);

        StarMap.Detail detail = StarMap.detail(campaign, "skye");

        assertEquals("Skye", detail.name());
        assertEquals("Draconis Combine / Lyran Commonwealth", detail.owner());
        assertEquals(8_000_000_000L, detail.population());
        assertEquals(HPGRating.A.toString(), detail.hpg());
        assertEquals(141.0, detail.recharge());
        assertEquals(102.0, detail.distance());
        assertEquals(1, detail.jumps());
        assertFalse(detail.here());
    }

    @Test
    void theDetailOfTheForcesSystemNeedsNoRoute() {
        StarMap.Detail detail = StarMap.detail(campaign, "tharkad");
        assertTrue(detail.here());
        assertEquals(0, detail.jumps());
        verify(campaign, never()).calculateJumpPath(any(), any());
    }

    @Test
    void noRouteAndNoSystemAreToldApart() {
        when(campaign.calculateJumpPath(tharkad, skye)).thenReturn(null);
        assertEquals(-1, StarMap.detail(campaign, "skye").jumps());
        assertNull(StarMap.detail(campaign, "nowhere"));
    }

    @Test
    void theMapChangesWithTheDayTheForceAndTheSystems() {
        ViewModel<StarMap> model = StarMap.model();
        assertTrue(model.isAffectedBy(new NewDayEvent(campaign)));
        assertTrue(model.isAffectedBy(mock(LocationChangedEvent.class)));
        assertTrue(model.isAffectedBy(new PlanetarySystemsChangedEvent(campaign)));
        assertFalse(model.isAffectedBy(mock(PersonChangedEvent.class)));
    }

    private PlanetarySystem system(String id, String name, Double x, Double y, Set<Faction> owners) {
        PlanetarySystem system = mock(PlanetarySystem.class);
        when(system.getId()).thenReturn(id);
        when(system.getPrintableName(TODAY)).thenReturn(name);
        when(system.getX()).thenReturn(x);
        when(system.getY()).thenReturn(y);
        when(system.getFactionSet(TODAY)).thenReturn(owners);
        when(campaign.getSystemById(id)).thenReturn(system);
        systems.add(system);
        return system;
    }

    private static Faction faction(String shortName, String name, int rgb, boolean isAbandoned) {
        Faction faction = mock(Faction.class);
        when(faction.getShortName()).thenReturn(shortName);
        when(faction.getFullName(TODAY.getYear())).thenReturn(name);
        when(faction.getColor()).thenReturn(new Color(rgb));
        when(faction.is(FactionTag.ABANDONED)).thenReturn(isAbandoned);
        return faction;
    }
}

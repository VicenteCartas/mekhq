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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import megamek.common.universe.FactionTag;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Factions;
import mekhq.campaign.universe.PlanetarySystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BordersTest {
    private static final LocalDate DATE = LocalDate.of(2765, 3, 1);
    private final Faction lyrans = faction("LA", "Lyran Commonwealth", 0x2060C0);
    private final Faction kurita = faction("DC", "Draconis Combine", 0xC02020);
    private final Faction independent = faction("IND", "Independent", 0x808080);
    private final List<PlanetarySystem> all = new ArrayList<>();
    private final Borders.Realms realms = new Borders.Realms();

    @BeforeEach
    void setUp() {
        Factions factions = mock(Factions.class);
        when(factions.getFaction("IND")).thenReturn(independent);
        Factions.setInstance(factions);
    }

    @AfterEach
    void tearDown() {
        Factions.setInstance(null);
    }

    @Test
    void eachHeldSystemIsAClaimByItsRealm() {
        all.add(system("tharkad", 10.0, -20.0, Set.of(lyrans)));
        all.add(system("luthien", 150.0, 30.0, Set.of(kurita)));

        Borders borders = Borders.of(all, realms, DATE);

        assertEquals(DATE, borders.date());
        assertEquals(2, borders.stars().size());
        assertEquals(2, borders.claims());
        assertEquals(10, borders.claimX(0));
        assertEquals(-20, borders.claimY(0));
        assertEquals(new Borders.Realm("LA", 0x2060C0), borders.realms().get(borders.claimRealm(0)));
        assertEquals("DC", borders.realms().get(borders.claimRealm(1)).shortName());
        assertFalse(borders.disputed(0));
        assertEquals(10, borders.minX());
        assertEquals(-20, borders.minY());
        assertEquals(150, borders.maxX());
        assertEquals(30, borders.maxY());
    }

    @Test
    void sharedSystemsAreDisputedAndIndependentsYieldToRealms() {
        all.add(system("hesperus", 0.0, 0.0, Set.of(lyrans, kurita, independent)));

        Borders borders = Borders.of(all, realms, DATE);

        assertEquals(2, borders.claims());
        assertTrue(borders.disputed(0));
        assertTrue(borders.disputed(1));
        for (int i = 0; i < borders.claims(); i++) {
            assertFalse("IND".equals(borders.realms().get(borders.claimRealm(i)).shortName()));
        }
    }

    @Test
    void independentsAloneStillClaim() {
        all.add(system("galatea", 0.0, 0.0, Set.of(independent)));

        Borders borders = Borders.of(all, realms, DATE);

        assertEquals(1, borders.claims());
        assertEquals("IND", borders.realms().get(borders.claimRealm(0)).shortName());
    }

    @Test
    void abandonedAndUnheldSystemsClaimNothingButStillBoundTheMap() {
        Faction abandoned = faction("ABN", "Abandoned", 0x000000);
        when(abandoned.is(FactionTag.ABANDONED)).thenReturn(true);
        all.add(system("ruin", -50.0, 0.0, Set.of(abandoned)));
        all.add(system("void", 50.0, 0.0, Set.of()));

        Borders borders = Borders.of(all, realms, DATE);

        assertEquals(0, borders.claims());
        assertEquals(-50, borders.minX());
        assertEquals(50, borders.maxX());
    }

    @Test
    void realmsKeepTheirNumbersFromDateToDate() {
        PlanetarySystem tharkad = system("tharkad", 0.0, 0.0, Set.of(lyrans));
        PlanetarySystem luthien = system("luthien", 100.0, 0.0, Set.of(kurita));
        LocalDate later = DATE.plusYears(300);
        when(tharkad.getFactionSet(later)).thenReturn(Set.of(kurita));
        all.add(tharkad);
        all.add(luthien);

        Borders before = Borders.of(all, realms, DATE);
        Borders after = Borders.of(all, realms, later);

        assertEquals(before.claimRealm(1), after.claimRealm(0), "the Combine's number");
        assertEquals("DC", after.realms().get(after.claimRealm(0)).shortName());
        assertEquals("Draconis Combine", after.stars().stream()
              .filter(star -> star.id().equals("tharkad")).findFirst().orElseThrow().owner());
        assertEquals(2, after.realms().size());
    }

    @Test
    void noSystemsMeanNoClaims() {
        Borders borders = Borders.of(List.of(), realms, DATE);

        assertEquals(0, borders.claims());
        assertTrue(borders.stars().isEmpty());
        assertTrue(borders.realms().isEmpty());
    }

    private static PlanetarySystem system(String id, Double x, Double y, Set<Faction> owners) {
        PlanetarySystem system = mock(PlanetarySystem.class);
        when(system.getId()).thenReturn(id);
        when(system.getPrintableName(any())).thenReturn(id);
        when(system.getX()).thenReturn(x);
        when(system.getY()).thenReturn(y);
        when(system.getFactionSet(any())).thenReturn(owners);
        return system;
    }

    private static Faction faction(String shortName, String name, int rgb) {
        Faction faction = mock(Faction.class);
        when(faction.getShortName()).thenReturn(shortName);
        when(faction.getFullName(anyInt())).thenReturn(name);
        when(faction.getColor()).thenReturn(new Color(rgb));
        when(faction.is(FactionTag.ABANDONED)).thenReturn(false);
        return faction;
    }
}

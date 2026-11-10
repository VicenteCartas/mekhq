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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import mekhq.gui.enums.MHQTabType;
import org.junit.jupiter.api.Test;

class PaletteSearchTest {
    private static final PaletteEntry HANGAR = screen("Hangar", MHQTabType.HANGAR);
    private static final PaletteEntry FINANCES = screen("Finances", MHQTabType.FINANCES);
    private static final PaletteEntry CLASSIC = new PaletteEntry(PaletteEntry.Kind.ACTION, "Classic View",
          "Leave the command post", new PaletteEntry.Classic());
    private static final PaletteEntry SAVE = new PaletteEntry(PaletteEntry.Kind.MENU, "Save Campaign", "File",
          new PaletteEntry.MenuItem(List.of(0, 2), "Save Campaign"));
    private static final PaletteEntry HANSEN = person("Erik Hansen", "MekWarrior");
    private static final PaletteEntry JOSE = person("José Hannover", "Technician");
    private static final PaletteEntry ATLAS = unit("Atlas AS7-D", "BattleMek");

    private static final List<PaletteEntry> ALL = List.of(HANGAR, FINANCES, CLASSIC, SAVE, HANSEN, JOSE, ATLAS);

    @Test
    void blankQueryListsScreensAndActionsInOrder() {
        assertEquals(List.of(HANGAR, FINANCES, CLASSIC), PaletteSearch.find(ALL, "  ", 10));
        assertEquals(List.of(HANGAR, FINANCES), PaletteSearch.find(ALL, "", 2));
    }

    @Test
    void labelPrefixRanksAheadOfKindOrder() {
        // "han" starts Hansen's surname (word start) and Hangar's label (prefix)
        assertEquals(List.of(HANGAR, HANSEN, JOSE), PaletteSearch.find(ALL, "han", 10));
    }

    @Test
    void equalScoresListScreensBeforePeople() {
        PaletteEntry personnelScreen = screen("Personnel", MHQTabType.PERSONNEL);
        PaletteEntry percy = person("Percy", "Pilot");
        assertEquals(List.of(personnelScreen, percy),
              PaletteSearch.find(List.of(percy, personnelScreen), "per", 10));
    }

    @Test
    void accentsAndCaseAreIgnored() {
        assertEquals(List.of(JOSE), PaletteSearch.find(ALL, "JOSE", 10));
        assertEquals(List.of(JOSE), PaletteSearch.find(ALL, "josé", 10));
    }

    @Test
    void everyWordMustMatch() {
        assertEquals(List.of(HANSEN), PaletteSearch.find(ALL, "erik han", 10));
        assertTrue(PaletteSearch.find(ALL, "erik atlas", 10).isEmpty());
    }

    @Test
    void detailMatchesRankLast() {
        // "tech" is inside no label, but starts José's role
        assertEquals(List.of(JOSE), PaletteSearch.find(ALL, "tech", 10));
        assertEquals(3, PaletteSearch.score(SAVE, PaletteSearch.words("file")));
        assertEquals(0, PaletteSearch.score(SAVE, PaletteSearch.words("save")));
        assertEquals(1, PaletteSearch.score(SAVE, PaletteSearch.words("camp")));
        assertEquals(2, PaletteSearch.score(SAVE, PaletteSearch.words("ave")));
    }

    @Test
    void limitKeepsTheBest() {
        assertEquals(List.of(HANGAR), PaletteSearch.find(ALL, "han", 1));
    }

    private static PaletteEntry screen(String label, MHQTabType tab) {
        return new PaletteEntry(PaletteEntry.Kind.SCREEN, label, "Area", new PaletteEntry.Screen(tab));
    }

    private static PaletteEntry person(String name, String role) {
        return new PaletteEntry(PaletteEntry.Kind.PERSON, name, role, new PaletteEntry.PersonTarget(UUID.randomUUID()));
    }

    private static PaletteEntry unit(String name, String type) {
        return new PaletteEntry(PaletteEntry.Kind.UNIT, name, type, new PaletteEntry.UnitTarget(UUID.randomUUID()));
    }
}

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
package mekhq.campaign.universe.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mekhq.campaign.universe.Faction;
import org.junit.jupiter.api.Test;

class TerritoryGridTest {
    private final Faction lyrans = faction("LA");
    private final Faction kurita = faction("DC");

    @Test
    void oddColumnsSitHalfACellOffAwayFromTheOrigin() {
        assertEquals(0, TerritoryGrid.centerY(0, 0), 1e-9);
        assertEquals(15, TerritoryGrid.centerY(1, 0), 1e-9);
        assertEquals(-15, TerritoryGrid.centerY(-1, 0), 1e-9, "Java's remainder keeps the sign");
        assertEquals(2 * TerritoryGrid.SPACING_X, TerritoryGrid.centerX(2), 1e-9);
    }

    @Test
    void neighboursAreSixCellsOneCellAwayAndNeighbourBack() {
        for (int column = -3; column <= 3; column++) {
            for (int row = -2; row <= 2; row++) {
                TerritoryHex hex = new TerritoryHex(column, row);
                List<TerritoryHex> around = TerritoryGrid.neighbors(hex);
                assertEquals(6, around.size());
                for (TerritoryHex neighbour : around) {
                    double dx = TerritoryGrid.centerX(neighbour.column()) - TerritoryGrid.centerX(column);
                    double dy = TerritoryGrid.centerY(neighbour.column(), neighbour.row())
                          - TerritoryGrid.centerY(column, row);
                    assertEquals(TerritoryGrid.HEX_SIZE, Math.hypot(dx, dy), 1e-6, hex + " to " + neighbour);
                    assertTrue(TerritoryGrid.neighbors(neighbour).contains(hex), neighbour + " back to " + hex);
                }
            }
        }
    }

    @Test
    void cellsOrderByColumnThenRow() {
        assertTrue(TerritoryGrid.isBefore(new TerritoryHex(0, 5), new TerritoryHex(1, 0)));
        assertTrue(TerritoryGrid.isBefore(new TerritoryHex(1, 0), new TerritoryHex(1, 1)));
        assertFalse(TerritoryGrid.isBefore(new TerritoryHex(1, 1), new TerritoryHex(1, 1)));
    }

    @Test
    void regionsJoinNeighboursHeldByTheSameRealms() {
        Map<TerritoryHex, TerritoryCell> cells = block(5, 5, List.of());
        put(cells, 2, 2, List.of(lyrans));
        put(cells, 2, 3, List.of(lyrans));
        put(cells, 0, 0, List.of(kurita));

        List<TerritoryGrid.Region> regions = TerritoryGrid.regions(cells);

        TerritoryGrid.Region lyran = region(regions, List.of(lyrans));
        assertEquals(2, lyran.cells().size());
        assertEquals(TerritorySemantic.SOVEREIGN, lyran.semantic());
        assertEquals(TerritorySemantic.SOVEREIGN, region(regions, List.of(kurita)).semantic(),
              "touching the edge");
        assertEquals(TerritorySemantic.UNCLAIMED_EXTERIOR, region(regions, List.of()).semantic());
        assertEquals(25, regions.stream().mapToInt(region -> region.cells().size()).sum());
    }

    @Test
    void groundNoOneHoldsShutInByARealmIsAPocketAndARealmsInsideAnothersAnEnclave() {
        Map<TerritoryHex, TerritoryCell> cells = block(7, 7, List.of(lyrans));
        put(cells, 2, 2, List.of());
        put(cells, 4, 4, List.of(kurita));
        put(cells, 4, 1, List.of(lyrans, kurita));

        List<TerritoryGrid.Region> regions = TerritoryGrid.regions(cells);

        assertEquals(TerritorySemantic.UNCLAIMED_POCKET, region(regions, List.of()).semantic());
        assertEquals(TerritorySemantic.ENCLAVE, region(regions, List.of(kurita)).semantic());
        assertEquals(TerritorySemantic.DISPUTED, region(regions, List.of(lyrans, kurita)).semantic());
        assertEquals(TerritorySemantic.SOVEREIGN, region(regions, List.of(lyrans)).semantic());
    }

    private static Map<TerritoryHex, TerritoryCell> block(int columns, int rows, List<Faction> owners) {
        Map<TerritoryHex, TerritoryCell> cells = new HashMap<>();
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < rows; row++) {
                put(cells, column, row, owners);
            }
        }
        return cells;
    }

    private static void put(Map<TerritoryHex, TerritoryCell> cells, int column, int row, List<Faction> owners) {
        TerritoryHex hex = new TerritoryHex(column, row);
        cells.put(hex, new TerritoryCell(hex, TerritoryGrid.centerX(column), TerritoryGrid.centerY(column, row),
              owners));
    }

    private static TerritoryGrid.Region region(List<TerritoryGrid.Region> regions, List<Faction> owners) {
        return regions.stream().filter(region -> region.factions().equals(owners)).findFirst().orElseThrow();
    }

    private static Faction faction(String shortName) {
        Faction faction = mock(Faction.class);
        when(faction.getShortName()).thenReturn(shortName);
        return faction;
    }
}

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
package mekhq.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.Star;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;
import org.junit.jupiter.api.Test;

class StartStarChartTest {
    private static final String SYSTEM = """
          id: Somewhere
          xcood: -12.5
          ycood: 40.25
          planet:
            - name: Somewhere
              event:
                - date: '2300-01-01'
                  faction:
                    source: noncanon
                    value:
                      - TH
                - date: '2786-01-01'
                  population:
                    value: 1000
                - date: '2800-01-01'
                  faction:
                    value:
                      - FWL
                      - CC
                - date: '3030-01-01'
                  faction:
                    value: [ LA ]
          """;

    @Test
    void ownerIsTheLatestClaimAtTheDate() throws IOException {
        StartStarChart.SystemLines system = StartStarChart.system(new BufferedReader(new StringReader(SYSTEM)),
              "3025-01-01");

        assertNotNull(system);
        assertEquals("Somewhere", system.id());
        assertEquals(-12.5, system.x());
        assertEquals(40.25, system.y());
        assertEquals("FWL", system.owner());
    }

    @Test
    void inlineClaimsCount() throws IOException {
        StartStarChart.SystemLines system = StartStarChart.system(new BufferedReader(new StringReader(SYSTEM)),
              "3050-01-01");

        assertNotNull(system);
        assertEquals("LA", system.owner());
    }

    @Test
    void noClaimBeforeTheDateLeavesItUnheld() throws IOException {
        StartStarChart.SystemLines system = StartStarChart.system(new BufferedReader(new StringReader(SYSTEM)),
              "2100-01-01");

        assertNotNull(system);
        assertNull(system.owner());
    }

    @Test
    void readsTheShippedInnerSphere() {
        Map<String, Color> colours = StartStarChart.colours(new File("data/universe/factions"));
        assertEquals(new Color(255, 221, 0), colours.get("FS"));

        StarChart chart = StartStarChart.read(new File("data/universe/planetary_systems/canon_systems.zip"),
              new File("data/universe/factions"), "3025-01-01", "Galatea");

        assertNotNull(chart);
        assertTrue(chart.stars().size() > 2000, () -> chart.stars().size() + " systems");
        assertEquals(-53.775, chart.focusX(), 0.001);
        assertEquals(34.077, chart.focusY(), 0.001);
        Star terra = chart.stars().stream().filter(star -> star.x() == 0 && star.y() == 0).findFirst().orElseThrow();
        assertEquals(colours.get("CS"), terra.color());
    }
}

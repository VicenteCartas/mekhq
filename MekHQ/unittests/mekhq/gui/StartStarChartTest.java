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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.xml.stream.XMLStreamException;

import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.Star;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void unsourcedClaimsCount() throws IOException {
        String system = """
              id: Elsewhere
              xcood: 1
              ycood: 2
              planet:
                - name: Elsewhere
                  event:
                    - date: '2800-01-01'
                      faction:
                        - FS
                        - CC
                    - date: '3000-01-01'
                      faction:
                        - ABN
              """;

        StartStarChart.SystemLines early = StartStarChart.system(new BufferedReader(new StringReader(system)),
              "2900-01-01");
        StartStarChart.SystemLines late = StartStarChart.system(new BufferedReader(new StringReader(system)),
              "3025-01-01");

        assertNotNull(early);
        assertEquals("FS", early.owner());
        assertNotNull(late);
        assertEquals("ABN", late.owner());
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

    @Test
    void anUnknownFocusFallsBackToTheNextOne() {
        StarChart chart = StartStarChart.read(new File("data/universe/planetary_systems/canon_systems.zip"),
              new File("data/universe/factions"), "3025-01-01", "Nowhere At All", "Galatea");

        assertNotNull(chart);
        assertEquals(-53.775, chart.focusX(), 0.001);
        assertEquals(34.077, chart.focusY(), 0.001);
    }

    @Test
    void aSaveTellsItsDateAndItsForcesSystem() throws XMLStreamException {
        String save = """
              <?xml version="1.0" encoding="UTF-8"?>
              <campaign version="0.51.00">
                  <info>
                      <id>x</id>
                      <calendar>3025-04-08</calendar>
                  </info>
                  <contracts><contract><calendar>3030-01-01</calendar></contract></contracts>
                  <locations>
                      <location>
                          <currentSystemId>Canopus IV</currentSystemId>
                      </location>
                      <location>
                          <currentSystemId>Terra</currentSystemId>
                      </location>
                  </locations>
              </campaign>
              """;

        StartStarChart.Whereabouts whereabouts = StartStarChart.whereabouts(
              new ByteArrayInputStream(save.getBytes(StandardCharsets.UTF_8)));

        assertEquals("3025-04-08", whereabouts.date());
        assertEquals("Canopus IV", whereabouts.system());
    }

    @ParameterizedTest
    @ValueSource(strings = { "currentPlanetId", "currentPlanetName", "CURRENTSYSTEMID" })
    void anOlderSaveTellsItsSystemByItsOldTag(String tag) throws XMLStreamException {
        String save = ("<campaign><info><calendar>3010-02-03</calendar></info>"
              + "<location><%s>Tharkad</%s></location></campaign>").formatted(tag, tag);

        StartStarChart.Whereabouts whereabouts = StartStarChart.whereabouts(
              new ByteArrayInputStream(save.getBytes(StandardCharsets.UTF_8)));

        assertEquals("3010-02-03", whereabouts.date());
        assertEquals("Tharkad", whereabouts.system());
    }

    @Test
    void aSaveWithoutThemTellsNothing() throws XMLStreamException {
        String save = "<campaign><info><calendar>someday</calendar></info></campaign>";

        StartStarChart.Whereabouts whereabouts = StartStarChart.whereabouts(
              new ByteArrayInputStream(save.getBytes(StandardCharsets.UTF_8)));

        assertNull(whereabouts.date());
        assertNull(whereabouts.system());
    }
}

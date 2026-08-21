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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import mekhq.campaign.mission.Mission;
import mekhq.campaign.mission.Scenario;
import org.junit.jupiter.api.Test;

class InterstellarMapPanelOperationMarkerTest {
    private static final double DELTA = 0.000_001;

    @Test
    void semanticZoomKeepsOnlyUrgentOperationsVisibleAtAtlasZoom() {
        InterstellarMapPanel.SemanticZoomProfile atlas =
              InterstellarMapPanel.SemanticZoomProfile.create(0.9, 3.0);
        InterstellarMapPanel.SemanticZoomProfile navigation =
              InterstellarMapPanel.SemanticZoomProfile.create(2.5, 3.0);
        InterstellarMapPanel.StrategicMarker missionOnly = new InterstellarMapPanel.StrategicMarker(1, 0);
        InterstellarMapPanel.StrategicMarker urgent = new InterstellarMapPanel.StrategicMarker(1, 1);

        assertEquals(0.0, atlas.missionOperationAlpha(), DELTA);
        assertTrue(atlas.urgentOperationAlpha() > 0.0);
        assertTrue(navigation.missionOperationAlpha() > 0.0);
        assertTrue(navigation.urgentOperationAlpha() >= navigation.missionOperationAlpha());
        assertEquals(0.0, InterstellarMapPanel.visibleOperationAlpha(1.0, atlas, missionOnly), DELTA);
        assertEquals(atlas.urgentOperationAlpha(),
              InterstellarMapPanel.visibleOperationAlpha(1.0, atlas, urgent), DELTA);
        assertEquals(0.0, InterstellarMapPanel.visibleOperationAlpha(0.0, atlas, urgent), DELTA,
              "the operations layer toggle must still suppress urgent markers");
        assertEquals(atlas, InterstellarMapPanel.SemanticZoomProfile.create(0.9, 3.0));
    }

    @Test
    void urgentMarkerIsDeterministicAndHasMateriallyStrongerFilledArea() {
        BufferedImage missionOnly = render(new InterstellarMapPanel.StrategicMarker(1, 0));
        BufferedImage urgent = render(new InterstellarMapPanel.StrategicMarker(1, 1));

        int missionPixels = nonTransparentPixelCount(missionOnly);
        int urgentPixels = nonTransparentPixelCount(urgent);
        assertTrue(missionPixels > 0);
        assertTrue(urgentPixels > 0);
        assertFalse(Arrays.equals(pixels(missionOnly), pixels(urgent)));
        assertTrue(urgentPixels >= missionPixels * 1.2,
              "the filled urgent flag should occupy materially more pixels than the outline flag");
        assertArrayEquals(pixels(urgent), pixels(render(new InterstellarMapPanel.StrategicMarker(1, 1))));
    }

    @Test
    void aggregationCountsMissionsOnceAndNeverInflatesTheBadgeWithScenarios() {
        Mission firstMission = missionAt("system-a");
        Mission secondMission = missionAt("system-a");
        Scenario firstScenario = scenarioFor(101);
        Scenario secondScenario = scenarioFor(101);
        Scenario thirdScenario = scenarioFor(102);

        Map<String, InterstellarMapPanel.StrategicMarker> markers =
              InterstellarMapPanel.buildStrategicMarkers(
                    List.of(firstMission, secondMission),
                    List.of(firstScenario, secondScenario, thirdScenario),
                    missionId -> missionId == 101 ? firstMission : secondMission);

        assertEquals(1, markers.size(), "systems receive one flag regardless of operation count");
        assertEquals(new InterstellarMapPanel.StrategicMarker(2, 3), markers.get("system-a"));
    }

    @Test
    void scenarioOnlyFallbackIsUrgentWithoutAZeroCountBadge() {
        Mission resolvedMission = missionAt("system-a");
        Scenario scenario = scenarioFor(101);

        InterstellarMapPanel.StrategicMarker marker = InterstellarMapPanel.buildStrategicMarkers(
              List.of(), List.of(scenario), missionId -> resolvedMission).get("system-a");

        assertEquals(new InterstellarMapPanel.StrategicMarker(0, 1), marker);
        assertTrue(marker.hasActiveScenario());
        assertArrayEquals(pixels(render(new InterstellarMapPanel.StrategicMarker(1, 1))),
              pixels(render(marker)), "zero and one mission should both omit a count badge");
        assertFalse(Arrays.equals(pixels(render(marker)),
              pixels(render(new InterstellarMapPanel.StrategicMarker(2, 1)))));
    }

    private static Mission missionAt(String systemId) {
        Mission mission = mock(Mission.class, RETURNS_DEEP_STUBS);
        when(mission.getSystem().getId()).thenReturn(systemId);
        return mission;
    }

    private static Scenario scenarioFor(int missionId) {
        Scenario scenario = mock(Scenario.class);
        when(scenario.getMissionId()).thenReturn(missionId);
        return scenario;
    }

    private static BufferedImage render(InterstellarMapPanel.StrategicMarker marker) {
        BufferedImage canvas = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        InterstellarMapPanel.SystemMarkerLayout layout = InterstellarMapPanel.SystemMarkerLayout.create(
              70, 70, 12, InterstellarMapPanel.RouteMarkerState.NONE, false, false);
        InterstellarMapPanel.drawOperationMarker(graphics, layout, marker);
        graphics.dispose();
        return canvas;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static int nonTransparentPixelCount(BufferedImage image) {
        return (int) Arrays.stream(pixels(image)).filter(pixel -> (pixel >>> 24) > 0).count();
    }
}

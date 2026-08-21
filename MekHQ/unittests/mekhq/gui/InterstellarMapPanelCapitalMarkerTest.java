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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import mekhq.campaign.universe.Faction;
import org.junit.jupiter.api.Test;

class InterstellarMapPanelCapitalMarkerTest {
    private static final Color TEST_FACTION_COLOR = new Color(220, 90, 70);

    @Test
    void factionPowerClassificationDoesNotChangeVisibilityOrSymbolSelection() {
        Faction major = faction(TEST_FACTION_COLOR, true, false);
        Faction clan = faction(TEST_FACTION_COLOR, false, true);
        Faction other = faction(TEST_FACTION_COLOR, false, false);

        BufferedImage majorMarker = renderCapital(major, 3.0);
        BufferedImage clanMarker = renderCapital(clan, 3.0);
        BufferedImage otherMarker = renderCapital(other, 3.0);

        assertTrue(nonTransparentPixelCount(majorMarker) > 0);
        assertTrue(Arrays.equals(pixels(majorMarker), pixels(clanMarker)));
        assertTrue(Arrays.equals(pixels(majorMarker), pixels(otherMarker)));
        for (Faction faction : new Faction[] { major, clan, other }) {
            verify(faction, never()).isMajorOrSuperPower();
            verify(faction, never()).isClan();
        }
    }

    @Test
    void unifiedMarkerPaintsVisiblyInDifferentFactionColors() {
        BufferedImage redMarker = renderCapital(faction(TEST_FACTION_COLOR, false, false), 7.5);
        Color blue = new Color(70, 145, 225);
        BufferedImage blueMarker = renderCapital(faction(blue, false, false), 7.5);

        assertTrue(exactColorPixelCount(redMarker, TEST_FACTION_COLOR) > 0);
        assertTrue(exactColorPixelCount(blueMarker, blue) > 0);
        assertFalse(Arrays.equals(pixels(redMarker), pixels(blueMarker)));
        assertTrue(darkPixelCount(redMarker) > 0, "the crown needs a dark contrast backing");
    }

    private static Faction faction(Color color, boolean major, boolean clan) {
        Faction faction = mock(Faction.class);
        when(faction.getColor()).thenReturn(color);
        when(faction.isMajorOrSuperPower()).thenReturn(major);
        when(faction.isClan()).thenReturn(clan);
        return faction;
    }

    private static BufferedImage renderCapital(Faction faction, double markerSize) {
        BufferedImage canvas = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        InterstellarMapPanel.drawFactionCapitalMarker(graphics, new Point2D.Double(24, 24), markerSize,
              faction);
        graphics.dispose();
        return canvas;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static int exactColorPixelCount(BufferedImage image, Color color) {
        return (int) Arrays.stream(pixels(image)).filter(pixel -> pixel == color.getRGB()).count();
    }

    private static int darkPixelCount(BufferedImage image) {
        return (int) Arrays.stream(pixels(image))
              .filter(pixel -> ((pixel >>> 24) > 0)
                    && (((pixel >>> 16) & 0xff) < 40)
                    && (((pixel >>> 8) & 0xff) < 40)
                    && ((pixel & 0xff) < 40))
              .count();
    }

    private static int nonTransparentPixelCount(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 0) {
                    count++;
                }
            }
        }
        return count;
    }
}

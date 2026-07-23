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
package mekhq.gui.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

class MekHQVisualThemeTest {
    @Test
    void everySemanticRoleResolvesAColor() {
        for (MekHQVisualTheme.ColorRole role : MekHQVisualTheme.ColorRole.values()) {
            assertNotNull(MekHQVisualTheme.color(role), role.name());
        }
    }

    @Test
    void spacingTokensIncreaseInVisualHierarchy() {
        assertTrue(MekHQVisualTheme.hairline() > 0);
        assertTrue(MekHQVisualTheme.thinGap() >= MekHQVisualTheme.hairline());
        assertTrue(MekHQVisualTheme.controlGap() >= MekHQVisualTheme.thinGap());
        assertTrue(MekHQVisualTheme.sectionGap() >= MekHQVisualTheme.controlGap());
        assertTrue(MekHQVisualTheme.regionGap() >= MekHQVisualTheme.sectionGap());
    }

    @Test
    void technicalFontIsMonospacedAndBold() {
        JLabel label = new JLabel();
        Font font = MekHQVisualTheme.technicalFont(label, 0.0f);

        assertEquals(Font.MONOSPACED, font.getFamily());
        assertTrue(font.isBold());
        assertEquals(label.getFont().getSize(), font.getSize());
    }

    @Test
    void alphaAndMixInputsAreClamped() {
        Color source = new Color(10, 20, 30);

        assertEquals(255, MekHQVisualTheme.withAlpha(source, 300).getAlpha());
        assertEquals(0, MekHQVisualTheme.withAlpha(source, -1).getAlpha());
        assertEquals(Color.BLACK, MekHQVisualTheme.mix(Color.BLACK, Color.WHITE, -1.0f));
        assertEquals(Color.WHITE, MekHQVisualTheme.mix(Color.BLACK, Color.WHITE, 2.0f));
    }

    @Test
    void signalAndSurfaceAdaptToLightAndDarkBackgrounds() {
        Color originalBackground = UIManager.getColor("Panel.background");
        try {
            UIManager.put("Panel.background", new Color(24, 27, 30));
            Color darkSignal = MekHQVisualTheme.color(MekHQVisualTheme.ColorRole.SIGNAL);
            Color darkSurface = MekHQVisualTheme.color(MekHQVisualTheme.ColorRole.SURFACE);
            assertTrue(MekHQVisualTheme.isDarkTheme());

            UIManager.put("Panel.background", new Color(242, 244, 246));
            Color lightSignal = MekHQVisualTheme.color(MekHQVisualTheme.ColorRole.SIGNAL);
            Color lightSurface = MekHQVisualTheme.color(MekHQVisualTheme.ColorRole.SURFACE);
            assertFalse(MekHQVisualTheme.isDarkTheme());

            assertNotEquals(darkSignal, lightSignal);
            assertNotEquals(darkSurface, lightSurface);
        } finally {
            if (originalBackground == null) {
                UIManager.getDefaults().remove("Panel.background");
            } else {
                UIManager.put("Panel.background", originalBackground);
            }
        }
    }
}

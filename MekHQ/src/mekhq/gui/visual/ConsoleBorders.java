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

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import mekhq.gui.visual.MekHQVisualTheme.ColorRole;

/** Shared technical borders for command-interface regions without imposing a layout. */
public final class ConsoleBorders {
    private ConsoleBorders() {
    }

    public static TitledBorder createTitledBorder(JComponent component, String title) {
        return createTitledBorder(component, title, MekHQVisualTheme.color(ColorRole.SIGNAL));
    }

    public static TitledBorder createTitledBorder(JComponent component, String title, Color accent) {
        Color lineColor = MekHQVisualTheme.mix(MekHQVisualTheme.color(ColorRole.BORDER), accent, 0.25f);
        return BorderFactory.createTitledBorder(
              BorderFactory.createLineBorder(lineColor, MekHQVisualTheme.hairline()),
              title,
              TitledBorder.LEADING,
              TitledBorder.TOP,
              MekHQVisualTheme.technicalFont(component, 0.0f),
              accent);
    }

    public static Border createGroupSeparator() {
        Color lineColor = MekHQVisualTheme.mix(MekHQVisualTheme.color(ColorRole.BORDER),
              MekHQVisualTheme.color(ColorRole.SIGNAL), 0.20f);
        return BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, MekHQVisualTheme.hairline(), 0, 0, lineColor),
              BorderFactory.createEmptyBorder(0, MekHQVisualTheme.controlGap(), 0, 0));
    }

    public static Border createRegionBorder() {
        Color lineColor = MekHQVisualTheme.mix(MekHQVisualTheme.color(ColorRole.BORDER),
              MekHQVisualTheme.color(ColorRole.SIGNAL), 0.20f);
        return BorderFactory.createLineBorder(lineColor, MekHQVisualTheme.hairline());
    }
}

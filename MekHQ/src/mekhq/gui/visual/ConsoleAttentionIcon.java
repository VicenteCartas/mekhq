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

import static megamek.client.ui.util.UIUtil.scaleForGUI;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

import mekhq.gui.visual.MekHQVisualTheme.ColorRole;

/** Fixed-size tab marker that paints only while its associated content needs attention. */
public final class ConsoleAttentionIcon implements Icon {
    private boolean active;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int xPosition, int yPosition) {
        if (!active) {
            return;
        }

        int markerSize = scaleForGUI(6);
        int markerOffset = Math.max(0, (getIconWidth() - markerSize) / 2);
        graphics.setColor(MekHQVisualTheme.color(ColorRole.INFORMATION));
        graphics.fillOval(xPosition + markerOffset, yPosition + markerOffset, markerSize, markerSize);
    }

    @Override
    public int getIconWidth() {
        return scaleForGUI(10);
    }

    @Override
    public int getIconHeight() {
        return scaleForGUI(10);
    }
}

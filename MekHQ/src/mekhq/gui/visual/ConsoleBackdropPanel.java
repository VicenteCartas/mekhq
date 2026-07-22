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

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

import mekhq.gui.visual.MekHQVisualTheme.ColorRole;

/** Theme-aware application canvas with an optional nonanimated command grid. */
public class ConsoleBackdropPanel extends JPanel {
    private boolean gridVisible = true;

    public ConsoleBackdropPanel() {
        super(new BorderLayout());
        setOpaque(true);
    }

    public boolean isGridVisible() {
        return gridVisible;
    }

    public void setGridVisible(boolean gridVisible) {
        this.gridVisible = gridVisible;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        setBackground(MekHQVisualTheme.color(ColorRole.CANVAS));
        super.paintComponent(graphics);
        if (!gridVisible) {
            return;
        }

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.setColor(MekHQVisualTheme.withAlpha(MekHQVisualTheme.color(ColorRole.SIGNAL),
              MekHQVisualTheme.isDarkTheme() ? 14 : 9));
        int gridSize = MekHQVisualTheme.gridSize();
        for (int xPosition = 0; xPosition < getWidth(); xPosition += gridSize) {
            graphics2D.drawLine(xPosition, 0, xPosition, getHeight());
        }
        for (int yPosition = 0; yPosition < getHeight(); yPosition += gridSize) {
            graphics2D.drawLine(0, yPosition, getWidth(), yPosition);
        }
        graphics2D.dispose();
    }
}

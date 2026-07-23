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
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import jakarta.annotation.Nullable;
import mekhq.gui.visual.MekHQVisualTheme.ColorRole;

/** Compact technical section heading with a centered rule and optional state/trailing control. */
public final class ConsoleHeaderPanel extends JPanel {
    private final JLabel titleLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final JPanel trailingPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, MekHQVisualTheme.controlGap(), 0));

    public ConsoleHeaderPanel(String title) {
        super(new BorderLayout(MekHQVisualTheme.controlGap(), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, MekHQVisualTheme.thinGap(), 0));

        titleLabel.setFont(MekHQVisualTheme.technicalFont(titleLabel, 0.0f));
        titleLabel.setForeground(MekHQVisualTheme.color(ColorRole.SIGNAL));
        titleLabel.setText(title);

        statusLabel.setFont(MekHQVisualTheme.technicalFont(statusLabel, 0.0f));
        statusLabel.setVisible(false);
        trailingPanel.setOpaque(false);
        trailingPanel.add(statusLabel);

        add(titleLabel, BorderLayout.WEST);
        add(new CenteredRule(), BorderLayout.CENTER);
        add(trailingPanel, BorderLayout.EAST);
        getAccessibleContext().setAccessibleName(title);
    }

    public void setTitleText(String title) {
        titleLabel.setText(title);
        getAccessibleContext().setAccessibleName(title);
    }

    public String getTitleText() {
        return titleLabel.getText();
    }

    public void setStatus(@Nullable String status, ConsoleState state) {
        boolean visible = status != null && !status.isBlank();
        statusLabel.setText(visible ? status : "");
        statusLabel.setForeground(state.color());
        statusLabel.setVisible(visible);
        updateAccessibleName();
    }

    public String getStatusText() {
        return statusLabel.getText();
    }

    public void setTrailingComponent(@Nullable JComponent component) {
        while (trailingPanel.getComponentCount() > 1) {
            trailingPanel.remove(1);
        }
        if (component != null) {
            trailingPanel.add(component);
        }
        revalidate();
        repaint();
    }

    private void updateAccessibleName() {
        String status = statusLabel.isVisible() ? ", " + statusLabel.getText() : "";
        getAccessibleContext().setAccessibleName(titleLabel.getText() + status);
    }

    private static final class CenteredRule extends JComponent {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            Color border = MekHQVisualTheme.color(ColorRole.BORDER);
            Color signal = MekHQVisualTheme.color(ColorRole.SIGNAL);
            graphics2D.setColor(MekHQVisualTheme.mix(border, signal, 0.35f));
            int centerY = (getHeight() - 1) / 2;
            graphics2D.drawLine(0, centerY, getWidth(), centerY);
            graphics2D.dispose();
        }
    }
}

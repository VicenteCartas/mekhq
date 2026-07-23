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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import mekhq.gui.visual.MekHQVisualTheme.ColorRole;

/** Compact exact-value telemetry with an additional semantic state indicator. */
public final class TelemetryPanel extends JPanel {
    private final JLabel label = new JLabel();
    private final JLabel value = new JLabel();
    private final JLabel detail = new JLabel();

    private ConsoleState state = ConsoleState.NEUTRAL;

    public TelemetryPanel(String labelText, String valueText, ConsoleState state) {
        super(new BorderLayout(0, MekHQVisualTheme.thinGap()));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(MekHQVisualTheme.thinGap(),
              MekHQVisualTheme.controlGap(),
              MekHQVisualTheme.thinGap(),
              MekHQVisualTheme.controlGap()));

        label.setText(labelText);
        label.setFont(MekHQVisualTheme.technicalFont(label, 0.0f));
        label.setForeground(MekHQVisualTheme.color(ColorRole.MUTED));

        value.setText(valueText);
        value.setFont(value.getFont().deriveFont(Font.BOLD));

        detail.setForeground(MekHQVisualTheme.color(ColorRole.MUTED));
        detail.setVisible(false);

        JPanel textPanel = new JPanel(new BorderLayout(0, MekHQVisualTheme.thinGap()));
        textPanel.setOpaque(false);
        textPanel.add(value, BorderLayout.NORTH);
        textPanel.add(detail, BorderLayout.CENTER);

        add(label, BorderLayout.NORTH);
        add(textPanel, BorderLayout.CENTER);
        setState(state);
        updateAccessibleName();
    }

    public void setValueText(String valueText) {
        value.setText(valueText);
        updateAccessibleName();
    }

    public String getValueText() {
        return value.getText();
    }

    public void setDetailText(String detailText) {
        detail.setText(detailText);
        detail.setVisible(detailText != null && !detailText.isBlank());
        updateAccessibleName();
    }

    public void setState(ConsoleState state) {
        this.state = state;
        value.setForeground(state == ConsoleState.NEUTRAL
                                  ? MekHQVisualTheme.color(ColorRole.FOREGROUND)
                                  : state.color());
        updateAccessibleName();
        repaint();
    }

    public ConsoleState getState() {
        return state;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.setColor(state == ConsoleState.NEUTRAL
                                  ? MekHQVisualTheme.color(ColorRole.BORDER)
                                  : state.color());
        graphics2D.fillRect(0, 0, Math.max(MekHQVisualTheme.hairline(), 2), getHeight());
        graphics2D.dispose();
    }

    private void updateAccessibleName() {
        String detailText = detail.isVisible() ? ", " + detail.getText() : "";
        getAccessibleContext().setAccessibleName(label.getText() + ": " + value.getText() + detailText +
                                                       ", " + state.name().toLowerCase());
    }
}

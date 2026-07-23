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

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;

import mekhq.gui.visual.MekHQVisualTheme.ColorRole;

/** Applies command-interface styling to standard Swing/FlatLaf components without replacing their classes. */
public final class ConsoleComponentStyler {
    private static final String FLATLAF_STYLE_PROPERTY = "FlatLaf.style";
    private static final String BUTTON_TYPE_PROPERTY = "JButton.buttonType";
    private static final String TABBED_PANE_TYPE_PROPERTY = "JTabbedPane.tabType";
    private static final String TABBED_PANE_SEPARATORS_PROPERTY = "JTabbedPane.showTabSeparators";
    private static final String TABBED_PANE_CONTENT_SEPARATOR_PROPERTY = "JTabbedPane.showContentSeparator";
    private static final String TABBED_PANE_ALIGNMENT_PROPERTY = "JTabbedPane.tabAreaAlignment";

    private ConsoleComponentStyler() {
    }

    public enum ButtonRole {
        STANDARD,
        PRIMARY,
        DANGER,
        TOOL
    }

    public static void styleButton(JButton button, ButtonRole role) {
        styleAbstractButton(button, role);
    }

    public static void styleToggle(JToggleButton button, ButtonRole role) {
        styleAbstractButton(button, role);
    }

    public static void styleInput(JComponent component) {
        Color signal = MekHQVisualTheme.color(ColorRole.SIGNAL);
        component.putClientProperty(FLATLAF_STYLE_PROPERTY, Map.of(
              "focusColor", MekHQVisualTheme.withAlpha(signal, 100),
              "focusedBorderColor", signal));
    }

    public static void styleTable(JTable table) {
        table.setShowGrid(false);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setRowHeight(Math.max(table.getRowHeight(), scaleForGUI(24)));
    }

    public static void styleTabbedPane(JTabbedPane tabbedPane) {
        tabbedPane.setOpaque(true);
        tabbedPane.setBackground(MekHQVisualTheme.color(ColorRole.SURFACE));
          tabbedPane.putClientProperty(FLATLAF_STYLE_PROPERTY, Map.of(
              "underlineColor", MekHQVisualTheme.color(ColorRole.SIGNAL)));
        tabbedPane.putClientProperty(TABBED_PANE_TYPE_PROPERTY, "underlined");
          tabbedPane.putClientProperty(TABBED_PANE_SEPARATORS_PROPERTY, Boolean.FALSE);
        tabbedPane.putClientProperty(TABBED_PANE_CONTENT_SEPARATOR_PROPERTY, Boolean.TRUE);
        tabbedPane.putClientProperty(TABBED_PANE_ALIGNMENT_PROPERTY, "leading");
    }

    public static void styleSplitPane(JSplitPane splitPane) {
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(scaleForGUI(6));
    }

    public static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createLineBorder(MekHQVisualTheme.color(ColorRole.BORDER),
              MekHQVisualTheme.hairline()));
        scrollPane.getViewport().setBackground(MekHQVisualTheme.color(ColorRole.SURFACE));
    }

    private static void styleAbstractButton(AbstractButton button, ButtonRole role) {
        button.setFocusable(true);
        button.setFocusPainted(true);

        Color signal = MekHQVisualTheme.color(ColorRole.SIGNAL);
        Color stateColor = role == ButtonRole.DANGER ? MekHQVisualTheme.color(ColorRole.CRITICAL) : signal;
        Map<String, Object> style = new LinkedHashMap<>();
        style.put("arc", 4);
        style.put("focusColor", MekHQVisualTheme.withAlpha(stateColor, 100));
        style.put("focusedBorderColor", stateColor);
        style.put("hoverBorderColor", stateColor);
        style.put("pressedBorderColor", stateColor);
        style.put("hoverBackground", MekHQVisualTheme.mix(MekHQVisualTheme.color(ColorRole.SURFACE),
              stateColor, MekHQVisualTheme.isDarkTheme() ? 0.10f : 0.06f));
        style.put("pressedBackground", MekHQVisualTheme.mix(MekHQVisualTheme.color(ColorRole.SURFACE),
              stateColor, MekHQVisualTheme.isDarkTheme() ? 0.18f : 0.12f));

        if (role == ButtonRole.PRIMARY) {
            style.put("borderColor", signal);
            style.put("background", MekHQVisualTheme.mix(MekHQVisualTheme.color(ColorRole.SURFACE), signal,
                  MekHQVisualTheme.isDarkTheme() ? 0.18f : 0.10f));
        } else if (role == ButtonRole.DANGER) {
            style.put("borderColor", stateColor);
        } else if (role == ButtonRole.TOOL) {
            button.putClientProperty(BUTTON_TYPE_PROPERTY, "toolBarButton");
        }

        button.putClientProperty(FLATLAF_STYLE_PROPERTY, style);
    }
}

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class ConsoleVisualComponentsTest {
    private static final String FLATLAF_STYLE_PROPERTY = "FlatLaf.style";

    @Test
    void backdropPaintsGridAndCanDisableIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConsoleBackdropPanel panel = new ConsoleBackdropPanel();
            panel.setSize(80, 80);

            BufferedImage gridImage = render(panel, 80, 80);
            panel.setGridVisible(false);
            BufferedImage plainImage = render(panel, 80, 80);

            assertNotEquals(gridImage.getRGB(0, 0), plainImage.getRGB(0, 0));
            assertFalse(panel.isGridVisible());
        });
    }

    @Test
    void headerExposesTitleStatusAndAccessibleName() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConsoleHeaderPanel header = new ConsoleHeaderPanel("DISPATCHES");
            header.setStatus("3 NEW", ConsoleState.INFORMATION);

            assertEquals("DISPATCHES", header.getTitleText());
            assertEquals("3 NEW", header.getStatusText());
            assertTrue(header.getAccessibleContext().getAccessibleName().contains("3 NEW"));
        });
    }

    @Test
    void angularSectionLeavesCutCornerTransparent() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConsoleSectionPanel panel = new ConsoleSectionPanel("ASSESSMENT", ConsoleSectionPanel.Style.ANGULAR);
            panel.setBorder(javax.swing.BorderFactory.createEmptyBorder());
            panel.setSize(100, 60);

            BufferedImage image = render(panel, 100, 60);
            assertEquals(0, new Color(image.getRGB(99, 0), true).getAlpha());
            assertTrue(new Color(image.getRGB(50, 30), true).getAlpha() > 0);
        });
    }

    @Test
    void telemetryPreservesExactValueAndAccessibleState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TelemetryPanel telemetry = new TelemetryPanel("FUNDS", "155,880,397 C-Bills", ConsoleState.NOMINAL);
            telemetry.setDetailText("No overdue payments");

            assertEquals("155,880,397 C-Bills", telemetry.getValueText());
            assertEquals(ConsoleState.NOMINAL, telemetry.getState());
            assertTrue(telemetry.getAccessibleContext().getAccessibleName().contains("nominal"));
        });
    }

    @Test
    void bordersPreserveTitlesAndUseLightweightGroupSeparation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel();
            javax.swing.border.TitledBorder titledBorder = ConsoleBorders.createTitledBorder(
                  panel, "MARKETPLACE", ConsoleState.INFORMATION.color());

            assertEquals("MARKETPLACE", titledBorder.getTitle());
            assertEquals(ConsoleState.INFORMATION.color(), titledBorder.getTitleColor());
            assertTrue(titledBorder.getTitleFont().isBold());

            Insets separatorInsets = ConsoleBorders.createGroupSeparator().getBorderInsets(panel);
            assertTrue(separatorInsets.left >= MekHQVisualTheme.controlGap());
            assertEquals(0, separatorInsets.top);
            assertEquals(0, separatorInsets.bottom);
        });
    }

    @Test
    void attentionIconReservesSpaceAndPaintsOnlyWhenActive() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConsoleAttentionIcon icon = new ConsoleAttentionIcon();
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            BufferedImage inactiveImage = render(icon);

            icon.setActive(true);
            BufferedImage activeImage = render(icon);

            assertEquals(width, icon.getIconWidth());
            assertEquals(height, icon.getIconHeight());
            assertFalse(new Color(inactiveImage.getRGB(width / 2, height / 2), true).getAlpha() > 0);
            assertTrue(new Color(activeImage.getRGB(width / 2, height / 2), true).getAlpha() > 0);
            assertTrue(icon.isActive());
        });
    }

    @Test
    void stylerKeepsStandardSwingTypesAndSetsFlatLafProperties() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = new JButton("Advance Day");
            ConsoleComponentStyler.styleButton(button, ConsoleComponentStyler.ButtonRole.PRIMARY);

            assertEquals(JButton.class, button.getClass());
            assertTrue(button.isFocusable());
            assertNotNull(button.getClientProperty(FLATLAF_STYLE_PROPERTY));
            assertTrue(button.getClientProperty(FLATLAF_STYLE_PROPERTY) instanceof Map<?, ?>);

            JTable table = new JTable(2, 2);
            ConsoleComponentStyler.styleTable(table);
            assertFalse(table.getShowHorizontalLines());
            assertFalse(table.getShowVerticalLines());
            assertTrue(table.getFillsViewportHeight());

            JTabbedPane tabs = new JTabbedPane();
            ConsoleComponentStyler.styleTabbedPane(tabs);
            assertTrue(tabs.isOpaque());
            assertEquals(MekHQVisualTheme.color(MekHQVisualTheme.ColorRole.SURFACE), tabs.getBackground());
            assertEquals("underlined", tabs.getClientProperty("JTabbedPane.tabType"));

            JSplitPane splitPane = new JSplitPane();
            ConsoleComponentStyler.styleSplitPane(splitPane);
            assertTrue(splitPane.isContinuousLayout());
        });
    }

    private static BufferedImage render(JPanel panel, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = image.createGraphics();
        panel.paint(graphics2D);
        graphics2D.dispose();
        return image;
    }

    private static BufferedImage render(javax.swing.Icon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = image.createGraphics();
        icon.paintIcon(null, graphics2D, 0, 0);
        graphics2D.dispose();
        return image;
    }
}

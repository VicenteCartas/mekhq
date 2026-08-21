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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import mekhq.campaign.Campaign;
import org.junit.jupiter.api.Test;

class InterstellarMapPanelLegendTest {
    private static final List<String> GROUPS = List.of("NAVIGATION", "RANGE RINGS", "SYSTEM STATUS", "MAP DATA");
    private static final List<Integer> GROUP_ENTRY_COUNTS = List.of(6, 4, 7, 7);
    private static final List<String> TITLES = List.of(
          "Selected system", "Hovered system", "Current fleet", "Planned route", "Active route",
                    "Waypoint number", "Contract-search radius", "Planetary-acquisition radius", "Jump radius",
                "50 ly HPG range", "System contact", "Faction ownership", "National capital", "Great Hiring Hall",
        "Operation flag", "Restricted system", "GM-edited system", "Analytical / service value",
        "Faction emblem", "HPG network & traffic", "Sovereign border", "Disputed territory",
        "Unclaimed pocket", "Enclave");
    private static final List<String> DESCRIPTIONS = List.of(
        "Cyan corner brackets identify the selected system.",
        "Pale corner brackets identify the system under the pointer.",
        "An amber JumpShip marks the fleet; an amber ring is its low-zoom beacon.",
        "A cyan dashed path and thin rings show the proposed jump route.",
        "Amber paths and rings show the current trip; pale pulses show travel flow.",
        "Numbered badges give each route stop's order.",
        "A configurable-color thick dashed ring centered on the selected system bounds contract searches; campaign and MekHQ options control visibility.",
        "A configurable-color thick dashed ring centered on the selected system bounds planetary acquisition; campaign, MekHQ, and zoom options control visibility.",
        "A configurable-color thick dashed ring centered on the selected system shows one-jump reach; MekHQ and zoom options control visibility.",
        "A dark-green dotted ring centered on the selected system marks 50 ly; HPG layer visibility controls when it appears.",
        "Star color shows spectral class; at low zoom, contact color reflects faction or map data, with grey for empty or unowned systems.",
        "Colored arcs identify each faction present; a neutral dashed collar marks a shared system.",
        "A faction-color crown with a dark outline marks every dated national capital at atlas zoom.",
        "A pale triangle marks a Great Hiring Hall.",
        "An outline flag marks an active mission; a filled flag marks an active scenario; the count is active missions.",
        "A black X marks a system barred by outlaw or restricted-entry standing rules.",
        "A cyan outline ring marks a non-canon system override.",
        "Bracket color reflects the active map layer's value or service status.",
        "A faint emblem watermark identifies territory; its tint identifies the faction.",
        "Rings show rating: A cyan, B blue, C orange, D red; links and pulses show A/B traffic.",
        "Translucent faction fill and a solid edge mark territory inferred from dated ownership.",
        "Multiple faction colors, diagonal hatching, and a dashed border mark shared control.",
        "A dark enclosed void with a dotted boundary marks locally unclaimed space.",
        "A closed double border marks one faction's territory enclosed by another.");

    @Test
    void legendTabsAreCompleteStableAndRenderableOffscreen() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tabbedPane = InterstellarMapPanel.createMapLegendTabbedPane();
            assertEquals(GROUPS.size(), tabbedPane.getTabCount());
            assertEquals(GROUPS, GROUPS.stream()
                  .map(group -> tabbedPane.getTitleAt(GROUPS.indexOf(group)))
                  .toList());

            List<Component> components = descendantsOf(tabbedPane);
            List<String> labels = components.stream()
                  .filter(JLabel.class::isInstance)
                  .map(JLabel.class::cast)
                  .map(JLabel::getText)
                  .toList();
            assertEquals(TITLES, labels);
            assertEquals(DESCRIPTIONS, components.stream()
                .filter(JTextArea.class::isInstance)
                .map(JTextArea.class::cast)
                .map(JTextArea::getText)
                .toList());

            List<JComponent> swatches = components.stream()
                  .filter(JComponent.class::isInstance)
                  .map(JComponent.class::cast)
                .filter(component -> Boolean.TRUE.equals(component.getClientProperty("mapLegendSwatch")))
                  .toList();
            assertEquals(TITLES.size(), swatches.size());
            assertEquals(TITLES, swatches.stream()
                .map(swatch -> (String) swatch.getClientProperty("mapLegendTitle"))
                .toList());
            for (JComponent swatch : swatches) {
                assertEquals(swatch.getPreferredSize(), swatch.getMinimumSize());
                assertEquals(swatch.getPreferredSize(), swatch.getMaximumSize());
                renderOffscreen(swatch);
            }

            JButton legendButton = InterstellarMapPanel.createMapLegendButton();
            assertEquals(legendButton.getPreferredSize(), legendButton.getMinimumSize());
            assertEquals(legendButton.getPreferredSize(), legendButton.getMaximumSize());
            assertEquals("Show map symbol legend",
                  legendButton.getAccessibleContext().getAccessibleName());
            assertTrue(legendButton.isFocusable());
            renderOffscreen(legendButton);

            Dimension commonViewportSize = null;
            int entryOffset = 0;
            for (int tabIndex = 0; tabIndex < tabbedPane.getTabCount(); tabIndex++) {
                JScrollPane scrollPane = (JScrollPane) tabbedPane.getComponentAt(tabIndex);
                assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                      scrollPane.getHorizontalScrollBarPolicy());
                assertTrue(scrollPane.isFocusable());
                if (commonViewportSize == null) {
                    commonViewportSize = scrollPane.getViewport().getPreferredSize();
                } else {
                    assertEquals(commonViewportSize, scrollPane.getViewport().getPreferredSize());
                }

                int entryCount = GROUP_ENTRY_COUNTS.get(tabIndex);
                List<Component> tabComponents = descendantsOf(scrollPane);
                assertEquals(TITLES.subList(entryOffset, entryOffset + entryCount), tabComponents.stream()
                      .filter(JLabel.class::isInstance)
                      .map(JLabel.class::cast)
                      .map(JLabel::getText)
                      .toList());
                assertEquals(DESCRIPTIONS.subList(entryOffset, entryOffset + entryCount), tabComponents.stream()
                      .filter(JTextArea.class::isInstance)
                      .map(JTextArea.class::cast)
                      .map(JTextArea::getText)
                      .toList());
                entryOffset += entryCount;

                BufferedImage renderedTab = renderOffscreen(scrollPane);
                assertFalse(scrollPane.getVerticalScrollBar().isVisible());
                assertFalse(scrollPane.getHorizontalScrollBar().isVisible());
                assertTrue(scrollPane.getViewport().getView().getHeight()
                      <= scrollPane.getViewport().getExtentSize().height);
                    assertTrue(scrollPane.getViewport().getView().getWidth()
                        <= scrollPane.getViewport().getExtentSize().width);
                assertTrue(hasColorVariation(renderedTab));
            }
            assertEquals(TITLES.size(), entryOffset);
        });
    }

    @Test
    void legendSwatchesRemainFixedAndCenteredForWrappedRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tabbedPane = InterstellarMapPanel.createMapLegendTabbedPane();
            for (int tabIndex = 0; tabIndex < tabbedPane.getTabCount(); tabIndex++) {
                renderOffscreen((JScrollPane) tabbedPane.getComponentAt(tabIndex));
            }

            List<Component> components = descendantsOf(tabbedPane);
            List<JTextArea> meanings = components.stream()
                  .filter(JTextArea.class::isInstance)
                  .map(JTextArea.class::cast)
                  .toList();
            assertTrue(meanings.stream().anyMatch(meaning -> meaning.getPreferredSize().height
                  > meaning.getFontMetrics(meaning.getFont()).getHeight()));

            List<JComponent> swatches = components.stream()
                  .filter(JComponent.class::isInstance)
                  .map(JComponent.class::cast)
                  .filter(component -> Boolean.TRUE.equals(component.getClientProperty("mapLegendSwatch")))
                  .toList();
            for (JComponent swatch : swatches) {
                String title = (String) swatch.getClientProperty("mapLegendTitle");
                JComponent swatchCell = componentWithProperty(components, "mapLegendSwatchCell", title);
                JComponent textCell = componentWithProperty(components, "mapLegendTextCell", title);
                JComponent row = componentWithProperty(components, "mapLegendRow", title);

                assertEquals(swatch.getPreferredSize(), swatch.getSize());
                Point swatchCenterInCell = SwingUtilities.convertPoint(swatch,
                      swatch.getWidth() / 2, swatch.getHeight() / 2, swatchCell);
                assertEquals(swatchCell.getHeight() / 2.0, swatchCenterInCell.y, 1.0);
                Point swatchCenterInRow = SwingUtilities.convertPoint(swatch,
                      swatch.getWidth() / 2, swatch.getHeight() / 2, row);
                Point textCenterInRow = SwingUtilities.convertPoint(textCell,
                      textCell.getWidth() / 2, textCell.getHeight() / 2, row);
                assertEquals(textCenterInRow.y, swatchCenterInRow.y, 1.0);
                assertEquals(row.getHeight() / 2.0, swatchCenterInRow.y, 1.5);
            }
        });
    }

    @Test
    void layerControlButtonsShareVerticalCenterWhenExpanded() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Campaign campaign = mock(Campaign.class);
            when(campaign.getSystems()).thenReturn(new ArrayList<>());
            InterstellarMapPanel mapPanel = new InterstellarMapPanel(campaign, mock(CampaignGUI.class));
            Dimension mapSize = new Dimension(1200, 800);
            mapPanel.setSize(mapSize);

            BufferedImage image = new BufferedImage(mapSize.width, mapSize.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            mapPanel.paintComponent(graphics);
            graphics.dispose();
            layoutRecursively(mapPanel);

            JButton legendButton = buttonWithAccessibleName(mapPanel, "Show map symbol legend");
            JButton optionButton = buttonWithAccessibleName(mapPanel, "Hide map layer controls");
            Point legendCenter = SwingUtilities.convertPoint(legendButton,
                  legendButton.getWidth() / 2, legendButton.getHeight() / 2, mapPanel);
            Point optionCenter = SwingUtilities.convertPoint(optionButton,
                  optionButton.getWidth() / 2, optionButton.getHeight() / 2, mapPanel);
            assertEquals(legendCenter.y, optionCenter.y);
        });
    }

    private static JButton buttonWithAccessibleName(Component root, String accessibleName) {
        return descendantsOf(root).stream()
              .filter(JButton.class::isInstance)
              .map(JButton.class::cast)
              .filter(button -> accessibleName.equals(button.getAccessibleContext().getAccessibleName()))
              .findFirst()
              .orElseThrow();
    }

    private static JComponent componentWithProperty(List<Component> components, String property, String title) {
        return components.stream()
              .filter(JComponent.class::isInstance)
              .map(JComponent.class::cast)
              .filter(component -> Boolean.TRUE.equals(component.getClientProperty(property)))
              .filter(component -> title.equals(component.getClientProperty("mapLegendTitle")))
              .findFirst()
              .orElseThrow();
    }

    private static BufferedImage renderOffscreen(JComponent component) {
        Dimension renderSize = component.getPreferredSize();
        component.setSize(renderSize);
        layoutRecursively(component);
        BufferedImage image = new BufferedImage(renderSize.width, renderSize.height,
                  BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        component.printAll(graphics);
        graphics.dispose();
        return image;
    }

    private static void layoutRecursively(Component component) {
        if (component instanceof java.awt.Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }

    private static boolean hasColorVariation(BufferedImage image) {
        int firstPixel = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != firstPixel) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Component> descendantsOf(Component root) {
        List<Component> components = new ArrayList<>();
        components.add(root);
        if (root instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                components.addAll(descendantsOf(child));
            }
        }
        return components;
    }
}

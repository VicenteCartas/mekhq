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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;

import mekhq.gui.visual.MekHQVisualTheme.ColorRole;

/** General-purpose command section with angular, subtle, or divider-only presentation. */
public final class ConsoleSectionPanel extends JPanel {
    public enum Style {
        ANGULAR,
        SUBTLE,
        DIVIDER_ONLY
    }

    private final Style style;
    private final JPanel contentPanel = new JPanel(new BorderLayout());

    public ConsoleSectionPanel(String title, Style style) {
        super(new BorderLayout(0, MekHQVisualTheme.controlGap()));
        this.style = style;
        setOpaque(false);
        contentPanel.setOpaque(false);

        if (title != null && !title.isBlank()) {
            add(new ConsoleHeaderPanel(title), BorderLayout.NORTH);
        }
        add(contentPanel, BorderLayout.CENTER);
        setBorder(createBorder(style));
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }

    public void setContent(Component content) {
        contentPanel.removeAll();
        contentPanel.add(content, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (style == Style.DIVIDER_ONLY) {
            super.paintComponent(graphics);
            return;
        }

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setColor(MekHQVisualTheme.color(ColorRole.SURFACE));
        if (style == Style.ANGULAR) {
            graphics2D.fill(createAngularFrame(0, 0, getWidth(), getHeight()));
        } else {
            graphics2D.fillRect(0, 0, getWidth(), getHeight());
        }
        graphics2D.dispose();
    }

    @Override
    protected void paintChildren(Graphics graphics) {
        if (style != Style.ANGULAR) {
            super.paintChildren(graphics);
            return;
        }

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.clip(createAngularFrame(0, 0, getWidth(), getHeight()));
        super.paintChildren(graphics2D);
        graphics2D.dispose();
    }

    private static Border createBorder(Style style) {
        Border padding = BorderFactory.createEmptyBorder(MekHQVisualTheme.sectionInset(),
              MekHQVisualTheme.sectionInset(),
              MekHQVisualTheme.sectionInset(),
              MekHQVisualTheme.sectionInset());
        return switch (style) {
            case ANGULAR -> BorderFactory.createCompoundBorder(new AngularBorder(), padding);
            case SUBTLE -> BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(
                  MekHQVisualTheme.color(ColorRole.BORDER), MekHQVisualTheme.hairline()), padding);
            case DIVIDER_ONLY -> BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(
                  MekHQVisualTheme.hairline(), 0, 0, 0, MekHQVisualTheme.color(ColorRole.BORDER)), padding);
        };
    }

    private static Path2D createAngularFrame(float left, float top, float right, float bottom) {
        int cornerCut = MekHQVisualTheme.cornerCut();
        Path2D frame = new Path2D.Float();
        frame.moveTo(left, top);
        frame.lineTo(right - cornerCut, top);
        frame.lineTo(right, top + cornerCut);
        frame.lineTo(right, bottom);
        frame.lineTo(left + cornerCut, bottom);
        frame.lineTo(left, bottom - cornerCut);
        frame.closePath();
        return frame;
    }

    private static final class AngularBorder extends AbstractBorder {
        @Override
        public Insets getBorderInsets(Component component) {
            int inset = MekHQVisualTheme.hairline();
            return new Insets(inset, inset, inset, inset);
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int xPosition, int yPosition, int width,
              int height) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setStroke(new BasicStroke(MekHQVisualTheme.hairline()));
            graphics2D.setColor(MekHQVisualTheme.mix(MekHQVisualTheme.color(ColorRole.BORDER),
                  MekHQVisualTheme.color(ColorRole.SIGNAL), 0.35f));

            float left = xPosition + 0.5f;
            float top = yPosition + 0.5f;
            float right = xPosition + width - 1.5f;
            float bottom = yPosition + height - 1.5f;
            graphics2D.draw(createAngularFrame(left, top, right, bottom));
            graphics2D.dispose();
        }
    }
}

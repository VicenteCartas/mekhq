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
import java.awt.Component;
import java.awt.Font;
import javax.swing.UIManager;

/** Theme-aware semantic colors, typography, and spacing for MekHQ command interfaces. */
public final class MekHQVisualTheme {
    private static final Color DARK_SIGNAL = new Color(86, 208, 197);
    private static final Color LIGHT_SIGNAL = new Color(23, 112, 112);
    private static final Color DARK_INFORMATION = new Color(92, 166, 214);
    private static final Color LIGHT_INFORMATION = new Color(32, 103, 157);
    private static final Color DARK_NOMINAL = new Color(104, 190, 116);
    private static final Color LIGHT_NOMINAL = new Color(42, 125, 62);
    private static final Color DARK_CAUTION = new Color(235, 177, 76);
    private static final Color LIGHT_CAUTION = new Color(150, 92, 16);
    private static final Color DARK_CRITICAL = new Color(224, 102, 102);
    private static final Color LIGHT_CRITICAL = new Color(174, 45, 45);

    private MekHQVisualTheme() {
    }

    /** Semantic color roles shared by the shell, workstations, and immersive dialogs. */
    public enum ColorRole {
        CANVAS,
        SURFACE,
        BORDER,
        FOREGROUND,
        MUTED,
        SIGNAL,
        INTERACTIVE,
        INFORMATION,
        NOMINAL,
        CAUTION,
        CRITICAL
    }

    /** Resolves a semantic color against the active look and feel. */
    public static Color color(ColorRole role) {
        return switch (role) {
            case CANVAS -> firstColor("Panel.background", Color.DARK_GRAY);
            case FOREGROUND -> firstColor("Label.foreground", Color.WHITE);
            case BORDER -> firstColor("Component.borderColor",
                  firstColor("Separator.foreground", mix(color(ColorRole.CANVAS), color(ColorRole.FOREGROUND), 0.25f)));
            case MUTED -> firstColor("Label.disabledForeground",
                  mix(color(ColorRole.CANVAS), color(ColorRole.FOREGROUND), 0.52f));
            case SURFACE -> mix(color(ColorRole.CANVAS), color(ColorRole.FOREGROUND), isDarkTheme() ? 0.06f : 0.025f);
            case SIGNAL -> isDarkTheme() ? DARK_SIGNAL : LIGHT_SIGNAL;
            case INTERACTIVE -> firstColor("Component.linkColor",
                  mix(color(ColorRole.FOREGROUND), color(ColorRole.SIGNAL), isDarkTheme() ? 0.62f : 0.72f));
            case INFORMATION -> firstColor("Actions.Blue", isDarkTheme() ? DARK_INFORMATION : LIGHT_INFORMATION);
            case NOMINAL -> firstColor("Actions.Green", isDarkTheme() ? DARK_NOMINAL : LIGHT_NOMINAL);
            case CAUTION -> firstColor("Actions.Yellow", isDarkTheme() ? DARK_CAUTION : LIGHT_CAUTION);
            case CRITICAL -> firstColor("Actions.Red", isDarkTheme() ? DARK_CRITICAL : LIGHT_CRITICAL);
        };
    }

    /** Returns whether the active panel background should be treated as dark. */
    public static boolean isDarkTheme() {
        Color background = firstColor("Panel.background", Color.DARK_GRAY);
        double luminance = 0.2126 * background.getRed() +
                                 0.7152 * background.getGreen() +
                                 0.0722 * background.getBlue();
        return luminance < 128;
    }

    /** Creates a compact monospaced technical font based on the component's active font size. */
    public static Font technicalFont(Component component, float sizeAdjustment) {
        Font baseFont = component.getFont();
        float size = Math.max(1.0f, baseFont.getSize2D() + sizeAdjustment);
        return new Font(Font.MONOSPACED, Font.BOLD, Math.max(1, Math.round(size)));
    }

    public static int hairline() {
        return Math.max(1, scaleForGUI(1));
    }

    public static int thinGap() {
        return scaleForGUI(4);
    }

    public static int controlGap() {
        return scaleForGUI(8);
    }

    public static int sectionGap() {
        return scaleForGUI(12);
    }

    public static int regionGap() {
        return scaleForGUI(16);
    }

    public static int sectionInset() {
        return scaleForGUI(10);
    }

    public static int gridSize() {
        return scaleForGUI(28);
    }

    public static int cornerCut() {
        return scaleForGUI(11);
    }

    /** Mixes two opaque colors using the supplied weight for the second color. */
    public static Color mix(Color firstColor, Color secondColor, float secondColorWeight) {
        float clampedWeight = Math.max(0.0f, Math.min(1.0f, secondColorWeight));
        float firstColorWeight = 1.0f - clampedWeight;
        int red = Math.round(firstColor.getRed() * firstColorWeight + secondColor.getRed() * clampedWeight);
        int green = Math.round(firstColor.getGreen() * firstColorWeight + secondColor.getGreen() * clampedWeight);
        int blue = Math.round(firstColor.getBlue() * firstColorWeight + secondColor.getBlue() * clampedWeight);
        return new Color(red, green, blue);
    }

    /** Returns the supplied color with a clamped alpha value. */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static Color firstColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }
}

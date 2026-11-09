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

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.Star;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;
import megamek.logging.MMLogger;

/**
 * The Inner Sphere as the start screen's star map shows it: every canon system where it lies, in its owner's colour at a
 * date. A quick read of the shipped files rather than MekHQ's full universe, which loads only with a campaign.
 */
final class StartStarChart {
    private static final MMLogger LOGGER = MMLogger.create(StartStarChart.class);
    /** Systems that no one holds, or that are lost, show this dim grey rather than black. */
    private static final Color UNHELD = new Color(110, 110, 110);

    private StartStarChart() {
    }

    /**
     * The canon systems in {@code systemsZip} in their owners' colours from the faction files in {@code factions} as of
     * {@code date} (yyyy-mm-dd), focused on the system {@code focus}; null when the files cannot be read.
     */
    static @Nullable StarChart read(File systemsZip, File factions, String date, String focus) {
        Map<String, Color> colours = colours(factions);
        List<Star> stars = new ArrayList<>();
        double focusX = 0;
        double focusY = 0;
        try (ZipFile zip = new ZipFile(systemsZip)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".yml")) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry),
                      StandardCharsets.UTF_8))) {
                    SystemLines system = system(reader, date);
                    if (system == null) {
                        continue;
                    }
                    Color colour = system.owner() == null ? UNHELD : colours.getOrDefault(system.owner(), UNHELD);
                    stars.add(new Star(system.x(), system.y(), colour));
                    if (system.id().equals(focus)) {
                        focusX = system.x();
                        focusY = system.y();
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("The start screen could not read the star systems: {}", e.getMessage());
            return null;
        }
        return stars.isEmpty() ? null : new StarChart(stars, focusX, focusY);
    }

    /** A system's id, place and owner at a date, from its file's lines. */
    record SystemLines(String id, double x, double y, @Nullable String owner) { }

    /** Reads one system's file: its id, coordinates and the latest owner at or before {@code date}. */
    static @Nullable SystemLines system(BufferedReader reader, String date) throws IOException {
        String id = null;
        double x = Double.NaN;
        double y = Double.NaN;
        String owner = null;
        String ownedSince = "";
        String eventDate = "";
        boolean inFaction = false;
        boolean inValue = false;
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            String key = null;
            if (line.startsWith("id:")) {
                id = value(trimmed);
            } else if (line.startsWith("xcood:")) {
                x = number(value(trimmed));
            } else if (line.startsWith("ycood:")) {
                y = number(value(trimmed));
            } else if (trimmed.startsWith("- date:")) {
                eventDate = value(trimmed.substring(2)).replace("'", "");
                inFaction = false;
                inValue = false;
            } else if (trimmed.equals("faction:")) {
                inFaction = true;
            } else if (inFaction && trimmed.startsWith("value:")) {
                String inline = value(trimmed).replace("[", "").replace("]", "").trim();
                inValue = inline.isEmpty();
                inFaction = inValue;
                key = inline.isEmpty() ? null : inline.split(",")[0].trim();
            } else if (inValue) {
                key = trimmed.startsWith("- ") ? trimmed.substring(2).trim() : null;
                inFaction = false;
                inValue = false;
            }
            // The latest owner at the date wins; a disputed system goes to the first claimant
            if (key != null && !key.isEmpty() && eventDate.compareTo(date) <= 0
                  && eventDate.compareTo(ownedSince) >= 0) {
                owner = key;
                ownedSince = eventDate;
            }
        }
        return id == null || Double.isNaN(x) || Double.isNaN(y) ? null : new SystemLines(id, x, y, owner);
    }

    /** Each faction's colour by its key, from the faction files' {@code color} blocks. */
    static Map<String, Color> colours(File factions) {
        Map<String, Color> colours = new HashMap<>();
        File[] files = factions.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return colours;
        }
        for (File file : files) {
            try {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                String key = null;
                int[] rgb = { -1, -1, -1 };
                boolean inColour = false;
                for (String line : lines) {
                    if (line.startsWith("key:")) {
                        key = value(line);
                    } else if (line.startsWith("color:")) {
                        inColour = true;
                    } else if (inColour && line.startsWith(" ")) {
                        String trimmed = line.trim();
                        int channel = trimmed.startsWith("red:") ? 0 : trimmed.startsWith("green:") ? 1
                              : trimmed.startsWith("blue:") ? 2 : -1;
                        if (channel >= 0) {
                            rgb[channel] = (int) Math.round(number(value(trimmed)));
                        }
                    } else {
                        inColour = false;
                    }
                }
                if (key != null && rgb[0] >= 0 && rgb[1] >= 0 && rgb[2] >= 0) {
                    boolean dark = rgb[0] + rgb[1] + rgb[2] < 60;
                    colours.put(key, dark ? UNHELD : new Color(clamp(rgb[0]), clamp(rgb[1]), clamp(rgb[2])));
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("Skipped faction file {} for the start screen's star map", file);
            }
        }
        return colours;
    }

    private static String value(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(colon + 1).trim();
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.replace("'", ""));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }
}

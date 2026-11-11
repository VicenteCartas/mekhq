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
package mekhq.gui.gdx.hub;

import java.util.HashMap;
import java.util.Map;

import megamek.common.Hex;
import megamek.common.board.Board;
import megamek.common.board.Coords;
import megamek.common.planetaryConditions.PlanetaryConditions;
import mekhq.gui.gdx.hub.WarTableSnapshot.Relief;
import mekhq.gui.gdx.hub.WarTableSnapshot.Sector;
import mekhq.gui.gdx.hub.WarTableSnapshot.Site;

/**
 * A sector as a game board, one board hex for each of its hexes, for the game's renderer to draw the war table's
 * ground with: its biome as the hex's theme and cover, its relief as levels, its seas as water, its roads joined up,
 * its cities and facilities as buildings. Hexes nobody has scouted are drawn as they are; the table veils them.
 */
final class WarTableBoard {
    /** How deep a sea is, in levels. */
    private static final int SEA_DEPTH = 2;

    private WarTableBoard() {
    }

    /** The board of {@code sector}; any thread. */
    static Board of(Sector sector) {
        int width = Math.max(1, sector.width());
        int height = Math.max(1, sector.height());
        Map<Long, Site> sites = new HashMap<>();
        for (Site site : sector.sites()) {
            sites.put(key(site.x(), site.y()), site);
        }
        Hex[] data = new Hex[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y * width + x] = new Hex(1, "", "", new Coords(x, y));
            }
        }
        for (WarTableSnapshot.Hex hex : sector.hexes()) {
            if (hex.x() < 0 || hex.y() < 0 || hex.x() >= width || hex.y() >= height) {
                continue;
            }
            Coords coords = new Coords(hex.x(), hex.y());
            Site site = sites.get(key(hex.x(), hex.y()));
            data[hex.y() * width + hex.x()] = new Hex(level(hex), terrain(hex, site), theme(hex.terrain()), coords);
        }
        Board board = new Board();
        board.newData(width, height, data, null);
        return board;
    }

    /** The weather the board is drawn under: the sector's temperature, in daylight. */
    static PlanetaryConditions conditions(Sector sector) {
        PlanetaryConditions conditions = new PlanetaryConditions();
        conditions.setTemperature(sector.temperature());
        return conditions;
    }

    /**
     * What the board drawn of {@code sector} is made from, so a sector that changed only in what's been scouted or
     * where the forces are keeps its board.
     */
    static String signature(Sector sector) {
        StringBuilder out = new StringBuilder().append(sector.width()).append('x').append(sector.height());
        for (WarTableSnapshot.Hex hex : sector.hexes()) {
            out.append(';').append(hex.x()).append(',').append(hex.y()).append(hex.terrain()).append(hex.relief())
                  .append(hex.road() ? 'r' : '-').append(hex.city() ? 'c' : '-');
        }
        for (Site site : sector.sites()) {
            out.append('|').append(site.x()).append(',').append(site.y()).append(site.type()).append(site.tier());
        }
        return out.toString();
    }

    /**
     * The level of {@code hex}'s ground, the surface of its water for a sea: lowland and plains at one, hills two or
     * three, mountains four or five, varied by where the hex lies so a range doesn't read as a plateau.
     */
    static int level(WarTableSnapshot.Hex hex) {
        float vary = HoloTableView.hash(hex.x() * 1.31, hex.y() * 2.17);
        return switch (hex.relief()) {
            case WATER -> 0;
            case LOW, FLAT -> 1;
            case RAISED -> vary < 0.5f ? 2 : 3;
            case HIGH -> vary < 0.4f ? 4 : 5;
        };
    }

    /**
     * The level of what stands highest on {@code hex}, for its marks to stand on: a city's rooftops, else its ground.
     * Plants don't count; a force stands among the trees.
     */
    static int height(WarTableSnapshot.Hex hex, boolean site) {
        return level(hex) + (site ? 0 : storeys(hex));
    }

    /** How many levels high the buildings of {@code hex} stand, 0 for none: a city's two to four. */
    private static int storeys(WarTableSnapshot.Hex hex) {
        if (water(hex) || !hex.city() && !hex.terrain().endsWith("Urban") && !"Factory".equals(hex.terrain())) {
            return 0;
        }
        return 2 + (int) (HoloTableView.hash(hex.x() * 3.7, hex.y() * 1.9) * 3);
    }

    /** Whether {@code hex} is open water, drawn as the board's water. */
    static boolean water(WarTableSnapshot.Hex hex) {
        return hex.relief() == Relief.WATER || hex.terrain().endsWith("Sea");
    }

    /** The board's theme for StratCon's terrain {@code terrain}: the ground's geology and the plants it grows. */
    static String theme(String terrain) {
        return switch (terrain) {
            case "ColdForest", "ColdHills", "ColdMountain", "SnowField", "ArcticDesert", "Glacier", "FrozenSea",
                 "ColdUrban" -> "snow";
            case "Desert", "HotHillsDry", "HotMountainsDry", "Badlands", "Savannah", "HotUrban" -> "desert";
            case "Jungle", "HotForest", "HotHillsWet", "HotMountainsWet" -> "tropical";
            case "Mountain" -> "rock";
            case "Tundra", "Steppe" -> "dirt";
            case "GrayLunar" -> "lunar";
            case "Mars" -> "mars";
            case "Volcano" -> "volcano";
            default -> "";
        };
    }

    /** The board terrain of {@code hex}, with {@code site} standing on it if not null. */
    static String terrain(WarTableSnapshot.Hex hex, Site site) {
        if (water(hex)) {
            return "FrozenSea".equals(hex.terrain()) ? "water:1;ice:1" : "water:" + SEA_DEPTH;
        }
        float vary = HoloTableView.hash(hex.x() * 3.7, hex.y() * 1.9);
        if (site != null) {
            // The facility's own models stand on it (see WarTableView)
            return "pavement:1" + (hex.road() ? ";road:1" : "");
        }
        int storeys = storeys(hex);
        if (storeys > 0) {
            String industry = "Factory".equals(hex.terrain()) ? "heavy_industrial:1;" : "";
            return industry + "pavement:1;building:2;bldg_cf:40;bldg_elev:" + storeys + (hex.road() ? ";road:1" : "");
        }
        String cover = hex.road() ? "" : cover(hex.terrain(), vary);
        if (hex.road()) {
            cover = "road:1";
        } else if (cover.isEmpty() && hex.terrain().endsWith("Facility")) {
            cover = "pavement:1";
        }
        return cover;
    }

    /** What grows or lies on a hex of {@code terrain} with no road through it; {@code vary} is 0 to 1 by hex. */
    private static String cover(String terrain, float vary) {
        return switch (terrain) {
            case "Forest", "ColdForest" -> "woods:" + (vary < 0.55f ? 1 : 2) + ";foliage_elev:2";
            case "Jungle", "HotForest" -> "jungle:" + (vary < 0.5f ? 1 : 2) + ";foliage_elev:2";
            case "Savannah" -> vary < 0.3f ? "woods:1;foliage_elev:2" : "";
            case "Plains" -> vary < 0.12f ? "woods:1;foliage_elev:2" : "";
            case "Farmland" -> "planted_fields:1";
            case "Swamp" -> "swamp:1";
            case "Hills", "HotHillsWet" -> vary < 0.3f ? "rough:1" : vary < 0.55f ? "woods:1;foliage_elev:2" : "";
            case "HotHillsDry", "ColdHills" -> vary < 0.45f ? "rough:1" : "";
            case "Mountain", "ColdMountain", "HotMountainsDry", "HotMountainsWet", "Volcano" ->
                  vary < 0.6f ? "rough:1" : "";
            case "Badlands" -> "rough:1";
            case "Desert" -> vary < 0.4f ? "sand:1" : "";
            case "Tundra" -> "tundra:1";
            case "SnowField", "ArcticDesert" -> "snow:1";
            case "Glacier" -> "ice:1;snow:1";
            default -> "";
        };
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFF_FFFFL);
    }
}

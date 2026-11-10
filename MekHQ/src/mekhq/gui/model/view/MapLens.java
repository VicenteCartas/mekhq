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

package mekhq.gui.model.view;

import static mekhq.campaign.personnel.medical.advancedMedicalAlternate.CanonicalDiseaseType.getAllActiveBioweapons;
import static mekhq.campaign.personnel.medical.advancedMedicalAlternate.CanonicalDiseaseType.getAllActiveDiseases;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import megamek.logging.MMLogger;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.PlanetarySystem.PlanetaryRating;
import mekhq.campaign.universe.SocioIndustrialData;
import mekhq.campaign.universe.enums.HPGRating;

/**
 * A way of looking at the star map's systems, as the classic map's layers do: each lens rates a system on a date from
 * 1, the least, to its {@link #levels()}, or 0 when it doesn't rate there (no one lives there, or it has none of what
 * the lens looks for). The colours are the classic map's, from the Viridis palettes. Any thread.
 */
public enum MapLens {
    TECHNOLOGY("technology", 0x333333, 0x440154, 0x3B528B, 0x21908C, 0x5DC863, 0xFDE725),
    INDUSTRY("industry", 0x2A0A3A, 0x51127C, 0xB63679, 0xFB8861, 0xFCFDBF),
    RAW_MATERIALS("rawMaterials", 0x0D0887, 0x7E03A8, 0xCC4678, 0xF89441, 0xF0F921),
    OUTPUT("output", 0x2A0A3A, 0x560F6E, 0xBB3754, 0xF98C0A, 0xFCFFA4),
    AGRICULTURE("agriculture", 0x00204D, 0x424D6B, 0x7C7B78, 0xBCAF6F, 0xFFEA46),
    POPULATION("population", 0x440154, 0x482878, 0x3E4A89, 0x31688E, 0x26828E, 0x1F9E89, 0x35B779, 0x6DCD59,
          0xB4DE2C, 0xFDE725),
    HPG("hpg", 0x545454, 0xA8A8A8, 0xDE4968, 0xFCFDBF),
    RECHARGE("recharge", 0x808080, 0xE16462, 0xF0F921),
    ACADEMIES("academies", 0x26828E, 0x1F9E89, 0x35B779, 0x6DCD59, 0xB4DE2C, 0xFDE725),
    HIRING_HALLS("hiringHalls", 0xBB3754, 0xF98C0A, 0xFDE725, 0x5DC863),
    DISEASES("diseases", 0xFDE725, 0xF98C0A, 0xBB3754, 0x7E03A8);

    private static final MMLogger LOGGER = MMLogger.create(MapLens.class);
    /** The bits each lens's level takes in {@link #pack}. */
    private static final int BITS = 4;
    /** The least people, by level, from the classic map's deciles of 2750. */
    private static final long[] PEOPLE = { 1, 1_000_000L, 25_000_000L, 100_000_000L, 200_000_000L, 300_000_000L,
          500_000_000L, 1_000_000_000L, 1_500_000_000L, 3_000_000_000L };

    private final String key;
    private final int[] palette;

    MapLens(String key, int... palette) {
        this.key = key;
        this.palette = palette;
    }

    /** Its name in the resources, under {@code HqHub.map.lens.}. */
    public String key() {
        return key;
    }

    /** The highest level it rates a system. */
    public int levels() {
        return palette.length;
    }

    /** The colour of {@code level}, 1 to {@link #levels()}, as 0xRRGGBB. */
    public int rgb(int level) {
        return palette[Math.clamp(level, 1, palette.length) - 1];
    }

    /** {@code level} of {@code packed}, every lens's levels as {@link #pack} put them. */
    public int level(long packed) {
        return (int) ((packed >>> (ordinal() * BITS)) & ((1 << BITS) - 1));
    }

    /** Every lens's level for {@code system} on {@code date}, packed into one number. */
    public static long pack(PlanetarySystem system, LocalDate date) {
        long packed = 0;
        long people = system.getPopulation(date);
        if (people <= 0) {
            return 0;
        }
        for (MapLens lens : values()) {
            int level;
            try {
                level = lens.rate(system, date, people);
            } catch (RuntimeException ex) {
                LOGGER.debug(ex, "No {} for {}", lens, system.getId());
                level = 0;
            }
            packed |= (long) Math.clamp(level, 0, lens.levels()) << (lens.ordinal() * BITS);
        }
        return packed;
    }

    private int rate(PlanetarySystem system, LocalDate date, long people) {
        SocioIndustrialData socio = this.ordinal() <= AGRICULTURE.ordinal() ? system.getSocioIndustrial(date) : null;
        return switch (this) {
            case TECHNOLOGY -> socio == null || socio.tech == null ? 0 : switch (socio.tech) {
                case REGRESSED -> 1;
                case F -> 2;
                case D -> 3;
                case C -> 4;
                case B -> 5;
                case A, ADVANCED -> 6;
            };
            case INDUSTRY -> socio == null ? 0 : rating(socio.industry);
            case RAW_MATERIALS -> socio == null ? 0 : rating(socio.rawMaterials);
            case OUTPUT -> socio == null ? 0 : rating(socio.output);
            case AGRICULTURE -> socio == null ? 0 : rating(socio.agriculture);
            case POPULATION -> {
                int level = 0;
                while (level < PEOPLE.length && people >= PEOPLE[level]) {
                    level++;
                }
                yield level;
            }
            case HPG -> {
                HPGRating hpg = system.getHPG(date);
                yield hpg == null ? 0 : switch (hpg) {
                    case D -> 1;
                    case C -> 2;
                    case B -> 3;
                    case A -> 4;
                    default -> 0;
                };
            }
            case RECHARGE -> Math.clamp(system.getNumberRechargeStations(date) + 1, 0, 3);
            case ACADEMIES -> Math.min(6, system.getFilteredAcademies(date, true).size());
            case HIRING_HALLS -> switch (system.getHiringHallLevel(date)) {
                case QUESTIONABLE -> 1;
                case MINOR -> 2;
                case STANDARD -> 3;
                case GREAT -> 4;
                default -> 0;
            };
            case DISEASES -> {
                HashSet<Object> diseases = new HashSet<>(getAllActiveDiseases(system.getId(), date, true));
                diseases.addAll(getAllActiveBioweapons(system.getId(), date, true));
                yield Math.min(4, diseases.size());
            }
        };
    }

    private static int rating(PlanetaryRating rating) {
        return rating == null ? 0 : switch (rating) {
            case F -> 1;
            case D -> 2;
            case C -> 3;
            case B -> 4;
            case A -> 5;
        };
    }

    /** The lenses in the order the map offers them. */
    public static List<MapLens> offered() {
        return List.of(values());
    }
}

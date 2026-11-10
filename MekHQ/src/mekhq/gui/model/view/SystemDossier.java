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

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import jakarta.annotation.Nullable;
import megamek.SuiteOptions;
import mekhq.campaign.Campaign;
import mekhq.campaign.personnel.InjuryType;
import mekhq.campaign.personnel.education.Academy;
import mekhq.campaign.universe.LandMass;
import mekhq.campaign.universe.Planet;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.Satellite;
import mekhq.campaign.universe.SocioIndustrialData;
import mekhq.campaign.universe.SourceableValue;
import mekhq.campaign.universe.enums.CapitalType;
import mekhq.campaign.universe.enums.PlanetaryType;

/**
 * A system's full record as the planet view shows it: the system, then each of its worlds, profile, environment and
 * society, ready to show. Read on the Swing thread, on the campaign's date; every value is already written out, and
 * every fact is named by a key the screen turns into its label.
 *
 * @param id        the system's id
 * @param name      its name today
 * @param system    the facts of the whole system: star, recharge, stations
 * @param worlds    its worlds, innermost first
 * @param primary   the index in {@code worlds} of its primary world, or -1 when it has none
 * @param academies the names of the academies open there
 * @param diseases  the diseases and bioweapons active there
 */
public record SystemDossier(String id, String name, List<Fact> system, List<World> worlds, int primary,
      List<String> academies, List<String> diseases) {

    private static final Pattern MARKUP = Pattern.compile("(?s)<[^>]*>|[*_#`]+|\\[([^]]*)]\\([^)]*\\)");
    private static final Pattern BLANKS = Pattern.compile("\\n{3,}");

    public SystemDossier {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        system = List.copyOf(system);
        worlds = List.copyOf(worlds);
        academies = List.copyOf(academies);
        diseases = List.copyOf(diseases);
    }

    /**
     * One labelled value.
     *
     * @param key   the last part of its label's key
     * @param value the value, written out
     */
    public record Fact(String key, String value) {
        public Fact {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
        }
    }

    /**
     * One world of the system.
     *
     * @param name        its name today
     * @param kind        its type: terrestrial, gas giant, asteroid belt...
     * @param position    its place in the system, or blank when unknown
     * @param profile     who holds it, how it is run, its size, orbit, day and moons
     * @param environment its gravity, air, heat, water and life
     * @param society     its people, HPG, hiring hall and landmasses
     * @param industry    its five socio-industrial ratings, each with its meaning, technology first; empty when unrated
     * @param description its description, plain text, or blank
     */
    public record World(String name, String kind, String position, List<Fact> profile, List<Fact> environment,
          List<Fact> society, List<String> industry, String description) {
        public World {
            Objects.requireNonNull(name);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(position);
            Objects.requireNonNull(description);
            profile = List.copyOf(profile);
            environment = List.copyOf(environment);
            society = List.copyOf(society);
            industry = List.copyOf(industry);
        }
    }

    /** Swing thread: the record of the system {@code id}, or null when the campaign has no such system. */
    public static @Nullable SystemDossier read(Campaign campaign, String id) {
        PlanetarySystem system = campaign.getSystemById(id);
        if (system == null) {
            return null;
        }
        LocalDate date = campaign.getLocalDate();
        List<Fact> facts = new ArrayList<>();
        SourceableValue<?> star = system.getSourcedStar();
        if (star != null && star.getValue() != null) {
            facts.add(new Fact("star", String.valueOf(star.getValue())));
        }
        facts.add(new Fact("recharge", system.getRechargeTimeText(date, campaign.isUseCommandCircuit())));
        facts.add(new Fact("stations", system.getRechargeStationsText(date)));
        Planet primaryWorld = system.getPrimaryPlanet();
        if (primaryWorld != null) {
            facts.add(new Fact("jumpPoint", String.format(Locale.getDefault(), "%.2f",
                  primaryWorld.getTimeToJumpPoint(1))));
        }
        List<World> worlds = new ArrayList<>();
        int primary = -1;
        for (Planet planet : system.getPlanets()) {
            if (planet == primaryWorld) {
                primary = worlds.size();
            }
            worlds.add(world(planet, date));
        }
        List<String> academies = new ArrayList<>();
        for (Academy academy : system.getFilteredAcademies(campaign)) {
            academies.add(academy.getName());
        }
        Set<String> diseases = new TreeSet<>();
        for (Collection<InjuryType> active : List.of(getAllActiveBioweapons(id, date, true),
              getAllActiveDiseases(id, date, true))) {
            for (InjuryType disease : active) {
                diseases.add(disease.getSimpleName());
            }
        }
        return new SystemDossier(system.getId(), system.getPrintableName(date), facts, worlds, primary, academies,
              List.copyOf(diseases));
    }

    private static World world(Planet planet, LocalDate date) {
        List<Fact> profile = new ArrayList<>();
        profile.add(new Fact("owner", planet.getFactionDesc(date)));
        List<String> administration = planet.getAdministration(date);
        if (!administration.isEmpty()) {
            profile.add(new Fact("administration", String.join("  \u203a  ", administration)));
        }
        CapitalType capital = planet.getCapitalType(date);
        if (capital != null && !capital.isNone()) {
            profile.add(new Fact("capital", capital.getLabel()));
        }
        if (planet.getPlanetType() != PlanetaryType.ASTEROID_BELT && planet.getSourcedDiameter() != null) {
            profile.add(new Fact("diameter", String.format(Locale.getDefault(), "%,.0f km", planet.getDiameter())));
        }
        if (planet.getOrbitRadius() != null) {
            profile.add(new Fact("orbit", String.format(Locale.getDefault(), "%.3f AU", planet.getOrbitRadius())));
        }
        profile.add(new Fact("jumpPoint", String.format(Locale.getDefault(), "%.2f",
              planet.getTimeToJumpPoint(1))));
        add(profile, "year", planet.getSourcedYearLength(), "%s");
        add(profile, "day", planet.getSourcedDayLength(date), "%s");
        List<String> moons = new ArrayList<>();
        if (planet.getSatellites() != null) {
            for (Satellite satellite : planet.getSatellites()) {
                SourceableValue<String> moon = satellite.getSourcedName();
                if (moon != null && moon.getValue() != null) {
                    moons.add(moon.getValue() + " (" + satellite.getSize() + ")");
                }
            }
        }
        if (planet.getSmallMoons() > 0) {
            moons.add(planet.getSmallMoons() + " small");
        }
        if (planet.hasRing()) {
            moons.add("ring");
        }
        if (!moons.isEmpty()) {
            profile.add(new Fact("satellites", String.join(", ", moons)));
        }

        List<Fact> environment = new ArrayList<>();
        add(environment, "gravity", planet.getSourcedGravity(), "%sg");
        add(environment, "atmosphere", planet.getSourcedAtmosphere(date), "%s");
        add(environment, "pressure", planet.getSourcedPressure(date), "%s");
        add(environment, "composition", planet.getSourcedComposition(date), "%s");
        add(environment, "temperature", planet.getSourcedTemperature(date), "%s\u00b0C");
        add(environment, "water", planet.getSourcedPercentWater(date), "%s%%");
        add(environment, "life", planet.getSourcedLifeForm(date), "%s");

        List<Fact> society = new ArrayList<>();
        if (planet.getSourcedPopulation(date) != null) {
            society.add(new Fact("population", NumberFormat.getIntegerInstance(SuiteOptions.getNumberFormatLocale())
                  .format(planet.getPopulation(date))));
        }
        add(society, "hpg", planet.getSourcedHPG(date), "%s");
        society.add(new Fact("hiringHall", planet.getHiringHallLevel(date).getLabel()));
        if (planet.getLandMasses() != null) {
            List<String> lands = new ArrayList<>();
            for (LandMass land : planet.getLandMasses()) {
                String name = land.getSourcedName() == null ? "" : String.valueOf(land.getSourcedName().getValue());
                String seat = land.getSourcedCapital() == null ? "" : String.valueOf(
                      land.getSourcedCapital().getValue());
                lands.add(seat.isBlank() ? name : name.isBlank() ? seat : name + " (" + seat + ")");
            }
            if (!lands.isEmpty()) {
                society.add(new Fact("landmasses", plain(String.join(", ", lands))));
            }
        }
        List<String> industry = new ArrayList<>();
        if (planet.getSourcedSocioIndustrial(date) != null) {
            SocioIndustrialData data = planet.getSocioIndustrial(date);
            society.add(new Fact("usilr", data.toString()));
            for (String line : data.getHTMLDescription().split("<br>")) {
                String text = plain(line).strip();
                if (!text.isEmpty()) {
                    industry.add(text);
                }
            }
        }
        String position = planet.getSystemPosition() == null ? "" : planet.getDisplayableSystemPosition();
        return new World(planet.getPrintableName(date), String.valueOf(planet.getPlanetType()), position, profile,
              environment, society, industry, plain(planet.getDescription()));
    }

    private static void add(List<Fact> facts, String key, @Nullable SourceableValue<?> value, String format) {
        if (value != null && value.getValue() != null) {
            facts.add(new Fact(key, plain(String.format(Locale.getDefault(), format, value.getValue()))));
        }
    }

    /** {@code text} without its HTML tags and Markdown marks: link text kept, three or more line breaks made two. */
    static String plain(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String stripped = MARKUP.matcher(text).replaceAll(match -> match.group(1) == null ? "" : match.group(1)
              .replace("\\", "\\\\").replace("$", "\\$"));
        stripped = stripped.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        return BLANKS.matcher(stripped.replace("\r", "")).replaceAll("\n\n").strip();
    }
}

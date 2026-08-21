/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
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
package mekhq.gui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import mekhq.campaign.JumpPath;
import mekhq.campaign.JumpPathItinerary.CircuitMode;
import mekhq.campaign.JumpPathSchedule.Mode;
import mekhq.campaign.JumpPathSchedule.Result;
import mekhq.campaign.NavigationRouteAnalysis.Finding;
import mekhq.campaign.NavigationRouteAnalysis.FindingKind;
import mekhq.campaign.NavigationRouteAnalysis.LegAssessment;
import mekhq.campaign.NavigationRouteAnalysis.LegFacts;
import mekhq.campaign.NavigationRouteAnalysis.Severity;
import mekhq.campaign.RouteAlternativesPlanner.AccessStatus;
import mekhq.campaign.RouteAlternativesPlanner.CircuitCoverage;
import mekhq.campaign.RouteAlternativesPlanner.Course;
import mekhq.campaign.RouteAlternativesPlanner.CourseKind;
import mekhq.campaign.universe.PlanetarySystem;
import org.junit.jupiter.api.Test;

class JumpPathViewPanelTest {
    private static final LocalDate START_DATE = LocalDate.of(3025, 1, 1);
    private static final ResourceBundle RESOURCES = ResourceBundle.getBundle(
          "mekhq.resources.JumpPathViewPanel", Locale.US);

    @Test
    void timelineMomentUsesLocalizedCampaignDateAndRoundedElapsedHours() {
        assertEquals("Jan 3, 3025 | D+2d 6h",
              JumpPathViewPanel.formatTimelineMoment(START_DATE, 2.25, Locale.US, RESOURCES));
    }

    @Test
    void timelineMomentCarriesNegativeOffsetsAcrossCampaignDateBoundary() {
        assertEquals("Dec 31, 3024 | D-2h",
              JumpPathViewPanel.formatTimelineMoment(START_DATE, -2.0 / 24.0, Locale.US, RESOURCES));
    }

        @Test
        void scheduleMomentUsesLocalizedAbsoluteDateAndTime() {
          assertEquals("Jan 3, 3025, 2:30\u202fPM",
          JumpPathViewPanel.formatScheduleMoment(START_DATE.atTime(14, 30).plusDays(2), Locale.US));
        }

        @Test
        void scheduleStatusDistinguishesFeasibleForecastAndMissedDeadline() {
        LocalDateTime earliest = START_DATE.atStartOfDay();
        Result forecast = new Result(Mode.DEPART_AT, earliest, earliest, earliest,
          earliest.plusDays(4), 0, true, List.of(), List.of());
        Result missed = new Result(Mode.ARRIVE_BY, earliest.plusDays(4), earliest,
          earliest.minusHours(5), earliest.plusDays(4), 0, false, List.of(), List.of());

        assertEquals("FORECAST READY", JumpPathViewPanel.scheduleStatusText(forecast, Locale.US, RESOURCES));
        assertEquals("DEADLINE MISSED BY 5h",
          JumpPathViewPanel.scheduleStatusText(missed, Locale.US, RESOURCES));
        }

        @Test
        void courseSelectionComparesStableSystemIds() {
          PlanetarySystem origin = new PlanetarySystem("Origin");
          PlanetarySystem destination = new PlanetarySystem("Destination");
          JumpPath path = pathOf(origin, destination);
          Course sameCourse = new Course(CourseKind.FASTEST, List.of(origin, destination), 10.0,
              CircuitCoverage.NONE, AccessStatus.CLEAR);
          Course differentCourse = new Course(CourseKind.FEWEST_JUMPS,
              List.of(origin, new PlanetarySystem("Elsewhere"), destination), 11.0,
              CircuitCoverage.NONE, AccessStatus.CLEAR);

          assertTrue(JumpPathViewPanel.isCourseSelected(path, sameCourse));
          assertFalse(JumpPathViewPanel.isCourseSelected(path, differentCourse));
        }

        @Test
        void selectedCircuitCourseInitializesWholeCoverageWithoutChangingCampaignDefault() {
          PlanetarySystem origin = new PlanetarySystem("Origin");
          PlanetarySystem intermediate = new PlanetarySystem("Intermediate");
          PlanetarySystem destination = new PlanetarySystem("Destination");
          JumpPath path = pathOf(origin, intermediate, destination);
          Course circuitCourse = new Course(CourseKind.COMMAND_CIRCUIT, path.getSystems(), 8.0,
              CircuitCoverage.WHOLE, AccessStatus.CLEAR);

          assertEquals(CircuitMode.WHOLE,
              JumpPathViewPanel.initialCircuitPlan(path, List.of(circuitCourse), false).mode());
          assertEquals(CircuitMode.NONE,
              JumpPathViewPanel.initialCircuitPlan(path, List.of(), false).mode());
        }

          @Test
          void legFactsUseOrderedGroundedConstraintAndRechargeData() {
            PlanetarySystem origin = new PlanetarySystem("Origin");
            PlanetarySystem destination = new PlanetarySystem("Destination");
            LegAssessment assessment = new LegAssessment(origin, destination,
              new LegFacts(30.5, 2, false, false, 72.0, 1, 168.0, true),
              List.of(new Finding(FindingKind.ACCESS_DENIED, Severity.BLOCKED)), Severity.BLOCKED);

            assertEquals("30.5 LY | 2 STD JUMPS | ACCESS BLOCKED | RECHARGE 72h | "
                  + "SOLAR 168h / 1 STATION(S) | CIRCUIT",
              JumpPathViewPanel.formatLegFacts(assessment, Locale.US, RESOURCES));
          }

        private static JumpPath pathOf(PlanetarySystem... systems) {
          JumpPath path = new JumpPath();
          path.addSystems(List.of(systems));
          return path;
        }
}

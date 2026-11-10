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

import static java.lang.Math.ceil;
import static mekhq.MHQConstants.CONFIRMATION_BEGIN_TRANSIT;
import static mekhq.campaign.enums.DailyReportType.GENERAL;
import static mekhq.campaign.market.personnelMarket.enums.PersonnelMarketStyle.MEKHQ;
import static mekhq.campaign.mission.contract.utilities.ContractAutomation.outOfContractMothballAutomation;
import static mekhq.campaign.personnel.skills.SkillType.EXP_REGULAR;
import static mekhq.campaign.randomEvents.prisoners.RecoverMIAPersonnel.abandonMissingPersonnel;

import java.util.Objects;

import jakarta.annotation.Nullable;
import mekhq.MekHQ;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.NavigationRouteAnalysis.PathAssessment;
import mekhq.campaign.NavigationRouteAnalysis.Severity;
import mekhq.campaign.finances.Money;
import mekhq.campaign.market.personnelMarket.markets.NewPersonnelMarket;
import mekhq.campaign.mission.utilities.TransportCostCalculations;
import mekhq.campaign.unit.Unit;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.utilities.JumpBlockers;
import mekhq.gui.baseComponents.immersiveDialogs.ImmersiveDialogConfirmation;

/**
 * Sets the force out on a course. The Navigation tab's Begin Transit and the command post's star map both start a
 * journey here, so either way the player confirms it, JumpShips unable to jump hold it up, the journey is paid for
 * and the force leaves its world. Swing thread.
 */
public final class BeginTransit {
    private BeginTransit() {
    }

    /**
     * Whether the force at {@code campaignCurrentSystem} could set out on {@code proposedPath} now: it starts there,
     * goes somewhere, the force isn't already following {@code activePath}, and {@code routeAssessment} doesn't block
     * it.
     */
    static boolean allowed(@Nullable JumpPath proposedPath, @Nullable PlanetarySystem campaignCurrentSystem,
          @Nullable JumpPath activePath, @Nullable PathAssessment routeAssessment) {
        // A route without jumps is only a journey when it heads for a specific world in the current system
        return (proposedPath != null) && !proposedPath.isEmpty()
              && ((proposedPath.getJumps() > 0) || (proposedPath.getTargetPlanet() != null))
              && Objects.equals(proposedPath.getFirstSystem(), campaignCurrentSystem)
              && ((activePath == null) || activePath.isEmpty()) && (routeAssessment != null)
              && (routeAssessment.severity() != Severity.BLOCKED);
    }

    /**
     * Sets the force out on {@code jumpPath}, assessed as {@code routeAssessment}, once the player confirms it.
     *
     * @return whether the force set out; not when it can't, or the player held back
     */
    public static boolean begin(Campaign campaign, JumpPath jumpPath, @Nullable PathAssessment routeAssessment) {
        AbstractLocation currentLocation = campaign.getPlayerForce().getForceDetachment().getCurrentLocation();
        if (currentLocation == null
              || !allowed(jumpPath, campaign.getCurrentSystem(), currentLocation.getJumpPath(), routeAssessment)) {
            return false;
        }

        // An in-system course never jumps, so JumpShips unable to jump don't hold it up
        if ((jumpPath.getJumps() > 0) && !JumpBlockers.areAllUnitsJumpCapable(campaign)) {
            return false;
        }

        if (!MekHQ.getMHQOptions().getNagDialogIgnore(CONFIRMATION_BEGIN_TRANSIT)) {
            ImmersiveDialogConfirmation dialog = new ImmersiveDialogConfirmation(campaign,
                  CONFIRMATION_BEGIN_TRANSIT);
            if (!dialog.wasConfirmed()) {
                return false;
            }
        }

        // Mothballing
        outOfContractMothballAutomation(campaign);

        // Everything else
        int duration = (int) ceil(jumpPath.getTotalTime(campaign.getLocalDate(), currentLocation.getTransitTime(),
              campaign.isUseCommandCircuit(), campaign.getJumpDriveProfile()));

        TransportCostCalculations transportCostCalculations = campaign.getTransportCostCalculation(EXP_REGULAR);
        Money journeyCost = transportCostCalculations.calculateCostForEntireJourney(duration, jumpPath);

        String jumpReport = TransportCostCalculations.performJumpTransaction(campaign.getPlayerForce().getFinances(),
              jumpPath, campaign.getLocalDate(), journeyCost, campaign.getCurrentSystem());

        if (!jumpReport.isBlank()) {
            campaign.addReport(GENERAL, jumpReport);
        }

        currentLocation.setJumpPath(jumpPath);

        campaign.getUnits().forEach(unit -> unit.setSite(Unit.SITE_FACILITY_BASIC));

        abandonMissingPersonnel(campaign);

        NewPersonnelMarket personnelMarket = campaign.getPlayerForce().getHumanResources().getNewPersonnelMarket();
        if (personnelMarket.getAssociatedPersonnelMarketStyle() == MEKHQ) {
            personnelMarket.clearCurrentApplicants();
        }
        return true;
    }
}

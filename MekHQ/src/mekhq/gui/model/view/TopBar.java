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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import jakarta.annotation.Nullable;
import mekhq.MekHQ;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.events.LocationChangedEvent;
import mekhq.campaign.events.NewDayEvent;
import mekhq.campaign.events.TransitStatusChangedEvent;
import mekhq.campaign.events.loans.LoanEvent;
import mekhq.campaign.events.missions.MissionEvent;
import mekhq.campaign.events.scenarios.ScenarioEvent;
import mekhq.campaign.events.transactions.TransactionEvent;
import mekhq.campaign.finances.Finances;
import mekhq.campaign.finances.Loan;
import mekhq.campaign.finances.Money;
import mekhq.campaign.universe.PlanetarySystem;

/**
 * The campaign shell's top bar: today, the force's funds, where it is and what needs the player's attention.
 *
 * @param date        today
 * @param dateText    today in the player's date format
 * @param funds       the force's balance
 * @param fundsText   the balance with its currency symbol
 * @param system      the system the force is in, or empty when it has none
 * @param travel      whether the force is on a world, at the jump point or flying between them
 * @param destination the system the force is travelling to, or null when it isn't travelling
 * @param alerts      what needs the player's attention, most urgent first
 */
public record TopBar(LocalDate date, String dateText, Money funds, String fundsText, String system, Travel travel,
      @Nullable String destination, List<Alert> alerts) {

    /** Where the force is in its system. */
    public enum Travel {
        ON_WORLD,
        AT_JUMP_POINT,
        IN_TRANSIT
    }

    /** Something that needs the player's attention, in the order the top bar shows them. */
    public enum Alert {
        /** A scenario is due today or earlier; the day can't advance until it's played. */
        SCENARIO_DUE,
        /** A loan payment is overdue. */
        LOAN_OVERDUE,
        /** The balance is below zero. */
        IN_DEBT
    }

    public TopBar {
        Objects.requireNonNull(date);
        Objects.requireNonNull(dateText);
        Objects.requireNonNull(funds);
        Objects.requireNonNull(fundsText);
        Objects.requireNonNull(system);
        Objects.requireNonNull(travel);
        alerts = List.copyOf(alerts);
    }

    /** The top bar, with today in the player's date format. */
    public static ViewModel<TopBar> model() {
        return model(date -> MekHQ.getMHQOptions().getDisplayFormattedDate(date));
    }

    /** The top bar, with today formatted by {@code dateFormat}. */
    public static ViewModel<TopBar> model(Function<LocalDate, String> dateFormat) {
        Objects.requireNonNull(dateFormat);
        return ViewModel.of("TopBar", campaign -> of(campaign, dateFormat),
              List.of(NewDayEvent.class, TransactionEvent.class, LoanEvent.class, LocationChangedEvent.class,
                    TransitStatusChangedEvent.class, MissionEvent.class, ScenarioEvent.class));
    }

    /** Swing thread: the top bar of {@code campaign}. */
    static TopBar of(Campaign campaign, Function<LocalDate, String> dateFormat) {
        LocalDate date = campaign.getLocalDate();
        Finances finances = campaign.getPlayerForce().getFinances();
        Money funds = finances.getBalance();

        AbstractLocation location = campaign.getPlayerForce().getForceDetachment().getCurrentLocation();
        PlanetarySystem current = (location == null) ? null : location.getCurrentSystem();
        String system = (current == null) ? "" : current.getPrintableName(date);
        Travel travel = Travel.ON_WORLD;
        if (location != null) {
            if (location.isInTransit()) {
                travel = Travel.IN_TRANSIT;
            } else if (location.isAtJumpPoint()) {
                travel = Travel.AT_JUMP_POINT;
            }
        }
        JumpPath path = (location == null) ? null : location.getJumpPath();
        PlanetarySystem last = ((path == null) || path.isEmpty()) ? null : path.getLastSystem();
        String destination = (last == null) ? null : last.getPrintableName(date);

        List<Alert> alerts = new ArrayList<>();
        if (campaign.checkScenariosDue()) {
            alerts.add(Alert.SCENARIO_DUE);
        }
        for (Loan loan : finances.getLoans()) {
            if (loan.isOverdue()) {
                alerts.add(Alert.LOAN_OVERDUE);
                break;
            }
        }
        if (funds.isNegative()) {
            alerts.add(Alert.IN_DEBT);
        }
        return new TopBar(date, dateFormat.apply(date), funds, funds.toAmountAndSymbolString(), system, travel,
              destination, alerts);
    }
}

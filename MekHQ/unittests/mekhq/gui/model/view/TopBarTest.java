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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.events.LocationChangedEvent;
import mekhq.campaign.events.NewDayEvent;
import mekhq.campaign.events.loans.LoanPaidEvent;
import mekhq.campaign.events.persons.PersonChangedEvent;
import mekhq.campaign.events.scenarios.ScenarioResolvedEvent;
import mekhq.campaign.events.transactions.TransactionDebitEvent;
import mekhq.campaign.finances.Finances;
import mekhq.campaign.finances.Loan;
import mekhq.campaign.finances.Money;
import mekhq.campaign.force.Detachment;
import mekhq.campaign.force.PlayerForce;
import mekhq.campaign.universe.PlanetarySystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TopBarTest {
    private static final Function<LocalDate, String> DATE_FORMAT = date -> "day " + date;
    private static final LocalDate TODAY = LocalDate.of(3025, 6, 1);

    private final Campaign campaign = mock(Campaign.class);
    private final Finances finances = mock(Finances.class);
    private final AbstractLocation location = mock(AbstractLocation.class);
    private final List<Loan> loans = new ArrayList<>();

    @BeforeEach
    void setUp() {
        PlayerForce force = mock(PlayerForce.class);
        Detachment detachment = mock(Detachment.class);
        when(campaign.getLocalDate()).thenReturn(TODAY);
        when(campaign.getPlayerForce()).thenReturn(force);
        when(force.getFinances()).thenReturn(finances);
        when(force.getForceDetachment()).thenReturn(detachment);
        when(detachment.getCurrentLocation()).thenReturn(location);
        when(finances.getBalance()).thenReturn(Money.of(1_250_000));
        when(finances.getLoans()).thenReturn(loans);
        PlanetarySystem galatea = system("Galatea");
        when(location.getCurrentSystem()).thenReturn(galatea);
    }

    @Test
    void onAWorldItShowsTodayTheFundsAndTheSystem() {
        TopBar topBar = TopBar.of(campaign, DATE_FORMAT);
        assertEquals(TODAY, topBar.date());
        assertEquals("day 3025-06-01", topBar.dateText());
        assertEquals(Money.of(1_250_000), topBar.funds());
        assertEquals(Money.of(1_250_000).toAmountAndSymbolString(), topBar.fundsText());
        assertEquals("Galatea", topBar.system());
        assertEquals(TopBar.Travel.ON_WORLD, topBar.travel());
        assertNull(topBar.destination());
        assertTrue(topBar.alerts().isEmpty());
    }

    @Test
    void travellingItShowsWhereTheForceIsAndWhereItIsGoing() {
        JumpPath path = new JumpPath();
        path.addSystem(system("Galatea"));
        path.addSystem(system("Tharkad"));
        when(location.getJumpPath()).thenReturn(path);
        when(location.isInTransit()).thenReturn(true);
        TopBar inTransit = TopBar.of(campaign, DATE_FORMAT);
        assertEquals(TopBar.Travel.IN_TRANSIT, inTransit.travel());
        assertEquals("Tharkad", inTransit.destination());

        when(location.isInTransit()).thenReturn(false);
        when(location.isAtJumpPoint()).thenReturn(true);
        assertEquals(TopBar.Travel.AT_JUMP_POINT, TopBar.of(campaign, DATE_FORMAT).travel());

        when(location.getJumpPath()).thenReturn(new JumpPath());
        assertNull(TopBar.of(campaign, DATE_FORMAT).destination(), "an empty path goes nowhere");
    }

    @Test
    void aForceWithNoLocationShowsNoSystem() {
        when(campaign.getPlayerForce().getForceDetachment().getCurrentLocation()).thenReturn(null);
        TopBar topBar = TopBar.of(campaign, DATE_FORMAT);
        assertEquals("", topBar.system());
        assertEquals(TopBar.Travel.ON_WORLD, topBar.travel());
    }

    @Test
    void theAlertsComeMostUrgentFirst() {
        Loan paid = mock(Loan.class);
        Loan overdue = mock(Loan.class);
        when(overdue.isOverdue()).thenReturn(true);
        loans.addAll(List.of(paid, overdue, overdue));
        when(finances.getBalance()).thenReturn(Money.of(-10));
        when(campaign.checkScenariosDue()).thenReturn(true);
        assertEquals(List.of(TopBar.Alert.SCENARIO_DUE, TopBar.Alert.LOAN_OVERDUE, TopBar.Alert.IN_DEBT),
              TopBar.of(campaign, DATE_FORMAT).alerts());
    }

    @Test
    void itRebuildsForTheEventsThatChangeItOnly() {
        ViewModel<TopBar> model = TopBar.model(DATE_FORMAT);
        assertTrue(model.isAffectedBy(new NewDayEvent(campaign)));
        assertTrue(model.isAffectedBy(mock(TransactionDebitEvent.class)));
        assertTrue(model.isAffectedBy(mock(LoanPaidEvent.class)));
        assertTrue(model.isAffectedBy(mock(LocationChangedEvent.class)));
        assertTrue(model.isAffectedBy(mock(ScenarioResolvedEvent.class)));
        assertFalse(model.isAffectedBy(mock(PersonChangedEvent.class)));
    }

    @Test
    void aNewDayHandsTheScreenTheNewTopBarAndAQuietDayHandsItNothing() {
        try (SessionHarness harness = new SessionHarness(campaign)) {
            SessionHarness.Screen<TopBar> screen = harness.watch(TopBar.model(DATE_FORMAT));
            harness.settle();
            assertEquals(TODAY, screen.latest().date());

            harness.fire(mock(PersonChangedEvent.class));
            harness.fire(mock(TransactionDebitEvent.class));
            harness.settle();
            assertEquals(1, screen.snapshots().size(), "an equal top bar isn't handed over again");

            when(campaign.getLocalDate()).thenReturn(TODAY.plusDays(1));
            harness.fire(new NewDayEvent(campaign));
            harness.settle();
            assertEquals(2, screen.snapshots().size());
            assertEquals("day 3025-06-02", screen.latest().dateText());
        }
    }

    private static PlanetarySystem system(String name) {
        PlanetarySystem system = mock(PlanetarySystem.class);
        when(system.getPrintableName(any())).thenReturn(name);
        return system;
    }
}

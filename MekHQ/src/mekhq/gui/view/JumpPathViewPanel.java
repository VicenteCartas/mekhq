/*
 * Copyright (C) 2009-2026 The MegaMek Team. All Rights Reserved.
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
package mekhq.gui.view;

import static java.lang.Math.ceil;
import static java.text.MessageFormat.format;
import static mekhq.campaign.personnel.skills.SkillType.EXP_REGULAR;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import megamek.client.ui.util.UIUtil;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.finances.Money;
import mekhq.campaign.mission.TransportCostCalculations;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.factionStanding.FactionStandingUtilities;
import mekhq.gui.baseComponents.JScrollablePanel;

/**
 * A custom panel that gets filled in with goodies from a JumpPath record
 *
 * @author Jay Lawson (jaylawson39 at yahoo.com)
 */
public class JumpPathViewPanel extends JScrollablePanel {
    private static final Color DOSSIER_BACKGROUND = new Color(7, 16, 27);
    private static final Color DOSSIER_TEXT = new Color(218, 231, 235);
    private static final Color DOSSIER_MUTED_TEXT = new Color(132, 153, 161);
    private static final Color DOSSIER_ACCENT = new Color(65, 210, 224);
    private static final Color DOSSIER_ACTIVE = new Color(235, 166, 66);
    private static final Color DOSSIER_DIVIDER = new Color(35, 66, 82);
    private static final int HORIZONTAL_PADDING = UIUtil.scaleForGUI(14);

    private final JumpPath path;
    private final Campaign campaign;
    private final ResourceBundle resourceMap;

    public JumpPathViewPanel(JumpPath p, Campaign c) {
        super();
        this.path = p;
        this.campaign = c;
        resourceMap = ResourceBundle.getBundle("mekhq.resources.JumpPathViewPanel",
              MekHQ.getMHQOptions().getLocale());
        initComponents();
    }

    private void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(DOSSIER_BACKGROUND);
        setOpaque(true);

        add(createHeader());
        add(createSummary());
        add(createItinerary());
    }

    private JPanel createHeader() {
        JPanel header = createBandPanel();
        header.setLayout(new GridBagLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, HORIZONTAL_PADDING, 10, HORIZONTAL_PADDING));

        boolean activeRoute = isActiveRoute();
        Color routeColor = activeRoute ? DOSSIER_ACTIVE : DOSSIER_ACCENT;

        JLabel eyebrow = new JLabel(resourceMap.getString("dossier.eyebrow.text"));
        eyebrow.setForeground(routeColor);
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, eyebrow.getFont().getSize2D() * 0.85f));
        GridBagConstraints constraints = createFullWidthConstraints(0);
        constraints.insets = new Insets(0, 0, 3, 0);
        header.add(eyebrow, constraints);

        JLabel routeStatus = new JLabel(resourceMap.getString(activeRoute
              ? "dossier.activeRoute.text"
              : "dossier.plannedRoute.text"));
        routeStatus.setForeground(DOSSIER_TEXT);
        routeStatus.setFont(routeStatus.getFont().deriveFont(Font.BOLD, routeStatus.getFont().getSize2D() * 1.3f));
        constraints = createFullWidthConstraints(1);
        constraints.insets = new Insets(0, 0, 4, 0);
        header.add(routeStatus, constraints);

        LocalDate currentDate = campaign.getLocalDate();
        String startName = getSystemName(path.getFirstSystem(), currentDate);
        String endName = getSystemName(path.getLastSystem(), currentDate);
        JLabel endpoints = new JLabel(format(resourceMap.getString("dossier.route.format"), startName, endName));
        endpoints.setForeground(DOSSIER_MUTED_TEXT);
        constraints = createFullWidthConstraints(2);
        header.add(endpoints, constraints);
        return header;
    }

    private JPanel createSummary() {
        JPanel summary = createSection("section.summary.text");
        LocalDate currentDate = campaign.getLocalDate();
        double currentTransit = campaign.getPlayerForce().getForceDetachment().getCurrentLocation().getTransitTime();
        boolean useCommandCircuit = isUseCommandCircuit();

        int metricIndex = 0;
        addMetric(summary, metricIndex++, "metric.jumps.text", Integer.toString(path.getJumps()));
        addMetric(summary, metricIndex++, "metric.startTransit.text", formatDays(path.getStartTime(currentTransit)));
        addMetric(summary, metricIndex++, "metric.endTransit.text", formatDays(path.getEndTime()));
        addMetric(summary, metricIndex++, "metric.recharge.text",
              formatDays(path.getTotalRechargeTime(currentDate, useCommandCircuit)));
        addMetric(summary, metricIndex++, "metric.totalTime.text",
              formatDays(path.getTotalTime(currentDate, currentTransit, useCommandCircuit)));

        if (campaign.getCampaignOptions().isPayForTransport()) {
            TransportCostCalculations calculations = campaign.getTransportCostCalculation(EXP_REGULAR);
            int duration = (int) ceil(path.getTotalTime(currentDate, currentTransit, useCommandCircuit));
            Money journeyCost = calculations.calculateJumpCostForEntireJourney(duration, path.getJumps());
            addMetric(summary, metricIndex, "metric.cost.text", journeyCost.toAmountAndSymbolString());
        }
        return summary;
    }

    private JPanel createItinerary() {
        JPanel itinerary = createSection("section.itinerary.text");
        LocalDate currentDate = campaign.getLocalDate();
        boolean useCommandCircuit = isUseCommandCircuit();
        Color routeColor = isActiveRoute() ? DOSSIER_ACTIVE : DOSSIER_ACCENT;

        int waypointIndex = 0;
        for (PlanetarySystem system : path.getSystems()) {
            JPanel waypoint = createBandPanel();
            waypoint.setLayout(new GridBagLayout());
            if (waypointIndex > 0) {
                waypoint.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DOSSIER_DIVIDER));
            }

            JLabel sequence = new JLabel(String.format("%02d", waypointIndex + 1));
            sequence.setForeground(routeColor);
            sequence.setFont(sequence.getFont().deriveFont(Font.BOLD));
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.gridheight = 2;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.insets = new Insets(8, 0, 8, 12);
            waypoint.add(sequence, constraints);

            JLabel systemName = new JLabel(system.getPrintableName(currentDate));
            systemName.setForeground(DOSSIER_TEXT);
            systemName.setFont(systemName.getFont().deriveFont(Font.BOLD));
            constraints = new GridBagConstraints();
            constraints.gridx = 1;
            constraints.gridy = 0;
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.insets = new Insets(7, 0, 1, 0);
            waypoint.add(systemName, constraints);

            JLabel rechargeTime = new JLabel(format(resourceMap.getString("waypoint.recharge.format"),
                  system.getRechargeTimeText(currentDate, useCommandCircuit)));
            rechargeTime.setForeground(DOSSIER_MUTED_TEXT);
            rechargeTime.setFont(rechargeTime.getFont()
                                       .deriveFont(Font.PLAIN, rechargeTime.getFont().getSize2D() * 0.9f));
            constraints = new GridBagConstraints();
            constraints.gridx = 1;
            constraints.gridy = 1;
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.insets = new Insets(0, 0, 7, 0);
            waypoint.add(rechargeTime, constraints);

            constraints = createFullWidthConstraints(waypointIndex + 1);
            itinerary.add(waypoint, constraints);
            waypointIndex++;
        }
        return itinerary;
    }

    private JPanel createSection(String headingKey) {
        JPanel section = createBandPanel();
        section.setLayout(new GridBagLayout());
        section.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(1, 0, 0, 0, DOSSIER_DIVIDER),
              BorderFactory.createEmptyBorder(9, HORIZONTAL_PADDING, 10, HORIZONTAL_PADDING)));

        JLabel heading = new JLabel(resourceMap.getString(headingKey));
        heading.setForeground(DOSSIER_ACCENT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() * 0.85f));
        GridBagConstraints constraints = createFullWidthConstraints(0);
        constraints.insets = new Insets(0, 0, 5, 0);
        section.add(heading, constraints);
        return section;
    }

    private void addMetric(JPanel summary, int metricIndex, String labelKey, String value) {
        JPanel metric = createBandPanel();
        metric.setLayout(new BoxLayout(metric, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(resourceMap.getString(labelKey));
        label.setForeground(DOSSIER_MUTED_TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() * 0.8f));
        label.setAlignmentX(LEFT_ALIGNMENT);
        metric.add(label);

        JLabel metricValue = new JLabel(value);
        metricValue.setForeground(DOSSIER_TEXT);
        metricValue.setFont(metricValue.getFont().deriveFont(Font.BOLD));
        metricValue.setAlignmentX(LEFT_ALIGNMENT);
        metric.add(metricValue);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = metricIndex % 2;
        constraints.gridy = 1 + (metricIndex / 2);
        constraints.weightx = 0.5;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(3, 0, 5, (metricIndex % 2 == 0) ? HORIZONTAL_PADDING : 0);
        summary.add(metric, constraints);
    }

    private JPanel createBandPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(DOSSIER_BACKGROUND);
        panel.setOpaque(true);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }

    private GridBagConstraints createFullWidthConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        return constraints;
    }

    private boolean isActiveRoute() {
        JumpPath activePath = campaign.getPlayerForce().getForceDetachment().getCurrentLocation().getJumpPath();
        return activePath == path;
    }

    private boolean isUseCommandCircuit() {
        return FactionStandingUtilities.isUseCommandCircuit(campaign.getPlayerForce()
                                                                  .isOverridingCommandCircuitRequirements(),
              campaign.isGM(), campaign.getCampaignOptions().isUseFactionStandingCommandCircuitSafe(),
              campaign.getPlayerForce().getFactionStandings(), campaign.getFutureAtBContracts());
    }

    private String formatDays(double days) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(MekHQ.getMHQOptions().getLocale());
        numberFormat.setMaximumFractionDigits(2);
        return format(resourceMap.getString("metric.days.format"), numberFormat.format(days));
    }

    private String getSystemName(PlanetarySystem system, LocalDate currentDate) {
        return (system == null) ? resourceMap.getString("dossier.unknownSystem.text")
                     : system.getPrintableName(currentDate);
    }
}

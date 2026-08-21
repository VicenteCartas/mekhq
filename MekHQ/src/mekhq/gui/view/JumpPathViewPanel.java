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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import megamek.client.ui.util.UIUtil;
import mekhq.MekHQ;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.JumpPathItinerary;
import mekhq.campaign.JumpPathSchedule;
import mekhq.campaign.NavigationRouteAnalysis;
import mekhq.campaign.NavigationRouteAnalysis.LegAssessment;
import mekhq.campaign.NavigationRouteAnalysis.PathAssessment;
import mekhq.campaign.NavigationRouteAnalysis.Severity;
import mekhq.campaign.JumpPathItinerary.CircuitMode;
import mekhq.campaign.JumpPathItinerary.CircuitPlan;
import mekhq.campaign.JumpPathItinerary.Plan;
import mekhq.campaign.JumpPathItinerary.RequiredAcceleration;
import mekhq.campaign.JumpPathItinerary.TimelineEntry;
import mekhq.campaign.JumpPathSchedule.Dwell;
import mekhq.campaign.JumpPathSchedule.Mode;
import mekhq.campaign.JumpPathSchedule.Result;
import mekhq.campaign.RouteAlternativesPlanner.AccessStatus;
import mekhq.campaign.RouteAlternativesPlanner.CircuitCoverage;
import mekhq.campaign.RouteAlternativesPlanner.Course;
import mekhq.campaign.campaignOptions.CampaignOption;
import mekhq.campaign.finances.Money;
import mekhq.campaign.mission.utilities.TransportCostCalculations;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.factionStanding.FactionStandingUtilities;
import mekhq.gui.baseComponents.FramedCommandButton;
import mekhq.gui.baseComponents.FramedCommandButtonStyle.ButtonColors;
import mekhq.gui.baseComponents.FramedCommandButtonStyle.ButtonStateColors;
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
    private static final Color DOSSIER_CONTROL_BACKGROUND = new Color(12, 29, 42);
        private static final Color DOSSIER_CONTROL_ACTIVE = new Color(18, 45, 56);
        private static final ButtonColors COURSE_BUTTON_COLORS = new ButtonColors(
            new ButtonStateColors(DOSSIER_CONTROL_BACKGROUND, DOSSIER_TEXT, DOSSIER_DIVIDER),
            new ButtonStateColors(DOSSIER_CONTROL_ACTIVE, DOSSIER_ACCENT, DOSSIER_ACCENT),
            new ButtonStateColors(DOSSIER_DIVIDER, DOSSIER_TEXT, DOSSIER_ACCENT),
            new ButtonStateColors(DOSSIER_CONTROL_BACKGROUND.darker(), DOSSIER_MUTED_TEXT, DOSSIER_DIVIDER.darker()));
    private static final int HORIZONTAL_PADDING = UIUtil.scaleForGUI(14);
        private static final int COURSE_BUTTON_WIDTH = UIUtil.scaleForGUI(112);
        private static final int COURSE_BUTTON_HEIGHT = UIUtil.scaleForGUI(34);
    private static final double MINIMUM_ACCELERATION_G = 0.1;
    private static final double MAXIMUM_ACCELERATION_G = 10.0;
    private static final double ACCELERATION_STEP_G = 0.1;
    private static final double MINIMUM_DESIRED_DAYS = 0.1;
    private static final double MAXIMUM_DESIRED_DAYS = 100000.0;
    private static final int MAXIMUM_DWELL_HOURS = 24000;

    private final JumpPath path;
    private final Campaign campaign;
    private final Locale locale;
    private final ResourceBundle resourceMap;
    private final List<Course> routeCourses;
    private final List<PlanetarySystem> requestedStops;
    private final List<Integer> dwellHoursByRequestedStop;
    private final Consumer<Course> courseSelectionHandler;
    private Plan itineraryPlan;
    private Result schedule;
    private Mode scheduleMode;
    private LocalDateTime earliestFeasibleDeparture;
    private LocalDateTime departureAnchor;
    private LocalDateTime arrivalDeadline;
    private CircuitPlan circuitPlan;
    private PathAssessment navigationAssessment;
    private CircuitPlan customCircuitPlan = CircuitPlan.custom(Set.of());
    private JLabel startingTransitValue;
    private JLabel endingTransitValue;
    private JLabel rechargeValue;
    private JLabel totalTimeValue;
    private JLabel requiredAccelerationValue;
    private JLabel scheduleAnchorLabel;
    private JLabel scheduleAnchorValue;
    private JLabel scheduleDepartureValue;
    private JLabel scheduleArrivalValue;
    private JLabel scheduleDwellValue;
    private JLabel scheduleStatusValue;
    private JPanel itinerarySection;
    private JPanel circuitDepartures;
    private JSpinner accelerationSpinner;
    private JSpinner desiredDurationSpinner;
    private JSpinner scheduleAnchorSpinner;
    private boolean adjustingScheduleAnchor;

    public JumpPathViewPanel(JumpPath p, Campaign c) {
        this(p, c, List.of(), List.of(), course -> { });
    }

    public JumpPathViewPanel(JumpPath p, Campaign c, List<Course> routeCourses,
          Consumer<Course> courseSelectionHandler) {
        this(p, c, routeCourses, List.of(), courseSelectionHandler);
    }

    public JumpPathViewPanel(JumpPath p, Campaign c, List<Course> routeCourses,
          List<PlanetarySystem> requestedStops, Consumer<Course> courseSelectionHandler) {
        super();
        this.path = p;
        this.campaign = c;
        this.routeCourses = List.copyOf(routeCourses);
        this.requestedStops = List.copyOf(requestedStops);
        dwellHoursByRequestedStop = new ArrayList<>(requestedStops.size());
        requestedStops.forEach(stop -> dwellHoursByRequestedStop.add(0));
        this.courseSelectionHandler = courseSelectionHandler;
        locale = MekHQ.getMHQOptions().getLocale();
        resourceMap = ResourceBundle.getBundle("mekhq.resources.JumpPathViewPanel", locale);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(DOSSIER_BACKGROUND);
        setOpaque(true);

        circuitPlan = initialCircuitPlan(path, routeCourses, isUseCommandCircuit());
        itineraryPlan = calculatePlan(JumpPathItinerary.DEFAULT_ACCELERATION_G);
        earliestFeasibleDeparture = campaign.getLocalDate().atStartOfDay();
        departureAnchor = earliestFeasibleDeparture;
        scheduleMode = Mode.DEPART_AT;
        schedule = calculateSchedule();
        arrivalDeadline = schedule.arrival();
        navigationAssessment = calculateNavigationAssessment();
        add(createHeader());
        add(createSummary());
        if (!isActiveRoute()) {
            if (routeCourses.size() > 1) {
                add(createCourseComparison());
            }
            add(createCircuitPlanner());
        }
        add(createAccelerationPlanner());
        add(createSchedulePlanner());
        itinerarySection = createItinerary();
        add(itinerarySection);
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

        int metricIndex = 0;
        addMetric(summary, metricIndex++, "metric.jumps.text", Integer.toString(path.getJumps()));
        startingTransitValue = addMetric(summary, metricIndex++, "metric.startTransit.text",
              formatDays(itineraryPlan.startingTransitDays()));
        endingTransitValue = addMetric(summary, metricIndex++, "metric.endTransit.text",
              formatDays(itineraryPlan.endingTransitDays()));
          rechargeValue = addMetric(summary, metricIndex++, "metric.recharge.text",
              formatDays(itineraryPlan.rechargeDays()));
        totalTimeValue = addMetric(summary, metricIndex++, "metric.totalTime.text",
              formatDays(itineraryPlan.totalDays()));

        if (campaign.getCampaignOptions().get(CampaignOption.PAY_FOR_TRANSPORT)) {
            TransportCostCalculations calculations = campaign.getTransportCostCalculation(EXP_REGULAR);
            int duration = (int) ceil(itineraryPlan.totalDays());
            Money journeyCost = calculations.calculateJumpCostForEntireJourney(duration, path.getJumps());
            addMetric(summary, metricIndex, "metric.cost.text", journeyCost.toAmountAndSymbolString());
        }
        return summary;
    }

    private JPanel createCourseComparison() {
        JPanel comparison = createSection("section.courses.text");
        int row = 1;
        for (Course course : routeCourses) {
            boolean selected = isCourseSelected(path, course);
            JPanel courseRow = createBandPanel();
            courseRow.setLayout(new GridBagLayout());
            if (row > 1) {
                courseRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DOSSIER_DIVIDER));
            }

            JLabel name = new JLabel(resourceMap.getString(switch (course.kind()) {
                case FASTEST -> "course.fastest.text";
                case FEWEST_JUMPS -> "course.fewestJumps.text";
                case COMMAND_CIRCUIT -> "course.commandCircuit.text";
            }));
            name.setForeground(selected ? DOSSIER_ACCENT : DOSSIER_TEXT);
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.WEST;
            constraints.insets = new Insets(7, 0, 1, 8);
            courseRow.add(name, constraints);

            JLabel facts = new JLabel(format(resourceMap.getString("course.facts.format"), course.jumps(),
                  formatNumber(course.oneGTotalDays(), 2),
                  resourceMap.getString(course.circuitCoverage() == CircuitCoverage.WHOLE
                                              ? "course.circuit.whole.text"
                                              : "course.circuit.none.text"),
                  resourceMap.getString(course.accessStatus() == AccessStatus.CLEAR
                                              ? "course.access.clear.text"
                                              : "course.access.blocked.text")));
            facts.setForeground(course.accessStatus() == AccessStatus.CLEAR ? DOSSIER_MUTED_TEXT : DOSSIER_ACTIVE);
            facts.setFont(facts.getFont().deriveFont(Font.PLAIN, facts.getFont().getSize2D() * 0.78f));
            constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 1;
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.WEST;
            constraints.insets = new Insets(1, 0, 7, 8);
            courseRow.add(facts, constraints);

            FramedCommandButton useCourse = new FramedCommandButton(resourceMap.getString(selected
                  ? "course.selected.text"
                  : "course.use.text"), COURSE_BUTTON_COLORS);
            Dimension buttonSize = new Dimension(COURSE_BUTTON_WIDTH, COURSE_BUTTON_HEIGHT);
            useCourse.setPreferredSize(buttonSize);
            useCourse.setMinimumSize(buttonSize);
            useCourse.setMaximumSize(buttonSize);
            useCourse.setEnabled(!selected);
            useCourse.setToolTipText(resourceMap.getString(selected
                  ? "course.selected.tooltip"
                  : "course.use.tooltip"));
            useCourse.getAccessibleContext().setAccessibleDescription(useCourse.getToolTipText());
            useCourse.addActionListener(event -> courseSelectionHandler.accept(course));
            constraints = new GridBagConstraints();
            constraints.gridx = 1;
            constraints.gridy = 0;
            constraints.gridheight = 2;
            constraints.anchor = GridBagConstraints.EAST;
            constraints.insets = new Insets(5, 0, 5, 0);
            courseRow.add(useCourse, constraints);

            constraints = createFullWidthConstraints(row++);
            comparison.add(courseRow, constraints);
        }
        return comparison;
    }

    private JPanel createCircuitPlanner() {
        JPanel planner = createSection("section.circuit.text");
        JComboBox<String> modeSelector = new JComboBox<>(new String[] {
              resourceMap.getString("circuit.none.text"),
              resourceMap.getString("circuit.whole.text"),
              resourceMap.getString("circuit.custom.text")
        });
        modeSelector.setSelectedIndex(circuitPlan.mode().ordinal());
        modeSelector.setBackground(DOSSIER_CONTROL_BACKGROUND);
        modeSelector.setForeground(DOSSIER_TEXT);
        modeSelector.setToolTipText(resourceMap.getString("circuit.mode.tooltip"));
        modeSelector.getAccessibleContext().setAccessibleName(resourceMap.getString("circuit.mode.text"));
        modeSelector.getAccessibleContext().setAccessibleDescription(modeSelector.getToolTipText());
        modeSelector.setPreferredSize(new Dimension(UIUtil.scaleForGUI(142), modeSelector.getPreferredSize().height));

        JPanel input = createBandPanel();
        input.setLayout(new GridBagLayout());
        JLabel label = createPlannerLabel("circuit.mode.text");
        label.setLabelFor(modeSelector);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        input.add(label, constraints);
        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.EAST;
        input.add(modeSelector, constraints);
        constraints = createFullWidthConstraints(1);
        constraints.insets = new Insets(3, 0, 4, 0);
        planner.add(input, constraints);

        circuitDepartures = createBandPanel();
        circuitDepartures.setLayout(new GridBagLayout());
        constraints = createFullWidthConstraints(2);
        planner.add(circuitDepartures, constraints);
        populateCircuitDepartures();

        modeSelector.addActionListener(event -> {
            CircuitMode selectedMode = CircuitMode.values()[modeSelector.getSelectedIndex()];
            circuitPlan = switch (selectedMode) {
                case NONE -> CircuitPlan.none();
                case WHOLE -> CircuitPlan.whole();
                case CUSTOM -> customCircuitPlan;
            };
            populateCircuitDepartures();
            refreshCircuitPlan();
        });
        return planner;
    }

    private void populateCircuitDepartures() {
        circuitDepartures.removeAll();
        if (circuitPlan.mode() != CircuitMode.CUSTOM) {
            circuitDepartures.revalidate();
            circuitDepartures.repaint();
            return;
        }

        List<PlanetarySystem> systems = path.getSystems();
        if (systems.size() <= 2) {
            JLabel none = new JLabel(resourceMap.getString("circuit.noDepartures.text"));
            none.setForeground(DOSSIER_MUTED_TEXT);
            none.setFont(none.getFont().deriveFont(Font.PLAIN, none.getFont().getSize2D() * 0.78f));
            circuitDepartures.add(none, createFullWidthConstraints(0));
        } else {
            for (int index = 1; index < systems.size() - 1; index++) {
                PlanetarySystem system = systems.get(index);
                JCheckBox covered = new JCheckBox(system.getPrintableName(campaign.getLocalDate()),
                        circuitPlan.usesCircuitAt(index));
                covered.setBackground(DOSSIER_BACKGROUND);
                covered.setForeground(DOSSIER_TEXT);
                covered.setOpaque(true);
                covered.setToolTipText(resourceMap.getString("circuit.departure.tooltip"));
                covered.getAccessibleContext().setAccessibleDescription(covered.getToolTipText());
                int departureIndex = index;
                covered.addActionListener(event -> {
                    customCircuitPlan = customCircuitPlan.withCoverage(departureIndex, covered.isSelected());
                    circuitPlan = customCircuitPlan;
                    refreshCircuitPlan();
                });
                GridBagConstraints constraints = createFullWidthConstraints(index - 1);
                constraints.insets = new Insets(1, 0, 1, 0);
                circuitDepartures.add(covered, constraints);
            }
        }
        circuitDepartures.revalidate();
        circuitDepartures.repaint();
    }

    private void refreshCircuitPlan() {
        double accelerationG = ((Number) accelerationSpinner.getValue()).doubleValue();
        itineraryPlan = calculatePlan(accelerationG);
        navigationAssessment = calculateNavigationAssessment();
        refreshPlanPresentation();
        updateRequiredAcceleration(((Number) desiredDurationSpinner.getValue()).doubleValue());
    }

    private JPanel createAccelerationPlanner() {
        JPanel planner = createSection("section.acceleration.text");
        accelerationSpinner = new JSpinner(new SpinnerNumberModel(
              JumpPathItinerary.DEFAULT_ACCELERATION_G, MINIMUM_ACCELERATION_G, MAXIMUM_ACCELERATION_G,
              ACCELERATION_STEP_G));
        configureSpinner(accelerationSpinner, "0.0", "planning.acceleration.text", "planning.acceleration.tooltip");
        addPlannerInput(planner, 1, "planning.acceleration.text", "planning.acceleration.tooltip",
              accelerationSpinner);

        double initialDesiredDays = Math.max(MINIMUM_DESIRED_DAYS,
              Math.min(MAXIMUM_DESIRED_DAYS, itineraryPlan.totalDays()));
        desiredDurationSpinner = new JSpinner(new SpinnerNumberModel(initialDesiredDays,
              MINIMUM_DESIRED_DAYS, MAXIMUM_DESIRED_DAYS, 1.0));
        configureSpinner(desiredDurationSpinner, "0.0", "planning.desiredTime.text",
              "planning.desiredTime.tooltip");
        addPlannerInput(planner, 2, "planning.desiredTime.text", "planning.desiredTime.tooltip",
              desiredDurationSpinner);

        JPanel result = createBandPanel();
        result.setLayout(new GridBagLayout());
        JLabel resultLabel = createPlannerLabel("planning.requiredAcceleration.text");
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        result.add(resultLabel, constraints);

        requiredAccelerationValue = new JLabel();
        requiredAccelerationValue.setFont(requiredAccelerationValue.getFont().deriveFont(Font.BOLD));
        requiredAccelerationValue.setHorizontalAlignment(SwingConstants.TRAILING);
        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.EAST;
        result.add(requiredAccelerationValue, constraints);
        constraints = createFullWidthConstraints(3);
        constraints.insets = new Insets(4, 0, 2, 0);
        planner.add(result, constraints);

        accelerationSpinner.addChangeListener(event -> {
            itineraryPlan = calculatePlan(((Number) accelerationSpinner.getValue()).doubleValue());
            refreshPlanPresentation();
        });
        desiredDurationSpinner.addChangeListener(event -> updateRequiredAcceleration(
              ((Number) desiredDurationSpinner.getValue()).doubleValue()));
        updateRequiredAcceleration(((Number) desiredDurationSpinner.getValue()).doubleValue());
        return planner;
    }

    private JPanel createSchedulePlanner() {
        JPanel planner = createSection("section.schedule.text");
        JComboBox<String> modeSelector = new JComboBox<>(new String[] {
              resourceMap.getString("schedule.departAt.text"),
              resourceMap.getString("schedule.arriveBy.text")
        });
        modeSelector.setSelectedIndex(scheduleMode.ordinal());
        modeSelector.setBackground(DOSSIER_CONTROL_BACKGROUND);
        modeSelector.setForeground(DOSSIER_TEXT);
        modeSelector.setToolTipText(resourceMap.getString("schedule.mode.tooltip"));
        modeSelector.getAccessibleContext().setAccessibleName(resourceMap.getString("schedule.mode.text"));
        modeSelector.getAccessibleContext().setAccessibleDescription(modeSelector.getToolTipText());
        modeSelector.setPreferredSize(new Dimension(UIUtil.scaleForGUI(142), modeSelector.getPreferredSize().height));
        addPlannerControl(planner, 1, resourceMap.getString("schedule.mode.text"),
              resourceMap.getString("schedule.mode.tooltip"), modeSelector);

        scheduleAnchorSpinner = new JSpinner(new SpinnerDateModel(toDate(departureAnchor), null, null,
              Calendar.HOUR_OF_DAY));
        configureScheduleAnchorSpinner();
        addPlannerControl(planner, 2, resourceMap.getString("schedule.anchor.text"),
              resourceMap.getString("schedule.anchor.tooltip"), scheduleAnchorSpinner);

        int row = 3;
        for (Dwell dwell : schedule.dwells()) {
            String label = format(resourceMap.getString("schedule.dwell.label.format"),
                  dwell.requestedStopIndex() + 1, dwell.system().getPrintableName(campaign.getLocalDate()));
            String tooltip = resourceMap.getString("schedule.dwell.tooltip");
            JSpinner dwellSpinner = new JSpinner(new SpinnerNumberModel(
                dwellHoursByRequestedStop.get(dwell.requestedStopIndex()).intValue(),
                0, MAXIMUM_DWELL_HOURS, 1));
            configureNumberSpinner(dwellSpinner, "0", label, tooltip);
            addPlannerControl(planner, row++, label, tooltip, dwellSpinner);
            dwellSpinner.addChangeListener(event -> {
                dwellHoursByRequestedStop.set(dwell.requestedStopIndex(),
                      ((Number) dwellSpinner.getValue()).intValue());
                refreshPlanPresentation();
            });
        }
        if (schedule.dwells().isEmpty()) {
            JLabel none = new JLabel(resourceMap.getString("schedule.noDwells.text"));
            none.setForeground(DOSSIER_MUTED_TEXT);
            none.setFont(none.getFont().deriveFont(Font.PLAIN, none.getFont().getSize2D() * 0.78f));
            GridBagConstraints constraints = createFullWidthConstraints(row++);
            constraints.insets = new Insets(3, 0, 4, 0);
            planner.add(none, constraints);
        }

        scheduleAnchorValue = new JLabel();
        scheduleDepartureValue = new JLabel();
        scheduleArrivalValue = new JLabel();
        scheduleDwellValue = new JLabel();
        scheduleStatusValue = new JLabel();
        scheduleAnchorLabel = addScheduleResult(planner, row++, "schedule.selectedAnchor.text", scheduleAnchorValue);
        addScheduleResult(planner, row++, "schedule.departure.text", scheduleDepartureValue);
        addScheduleResult(planner, row++, "schedule.arrival.text", scheduleArrivalValue);
        addScheduleResult(planner, row++, "schedule.totalDwell.text", scheduleDwellValue);
        addScheduleResult(planner, row, "schedule.status.text", scheduleStatusValue);
        refreshSchedulePresentation();

        modeSelector.addActionListener(event -> {
            Mode selectedMode = Mode.values()[modeSelector.getSelectedIndex()];
            if (selectedMode == scheduleMode) {
                return;
            }
            if (selectedMode == Mode.ARRIVE_BY) {
                arrivalDeadline = schedule.arrival();
            } else {
                departureAnchor = schedule.departure();
            }
            scheduleMode = selectedMode;
            updateScheduleAnchorControl();
            refreshPlanPresentation();
        });
        scheduleAnchorSpinner.addChangeListener(event -> {
            if (adjustingScheduleAnchor) {
                return;
            }
            LocalDateTime selectedAnchor = fromDate((Date) scheduleAnchorSpinner.getValue());
            if (scheduleMode == Mode.DEPART_AT) {
                departureAnchor = selectedAnchor;
            } else {
                arrivalDeadline = selectedAnchor;
            }
            refreshPlanPresentation();
        });
        return planner;
    }

    private JLabel addScheduleResult(JPanel planner, int row, String labelKey, JLabel value) {
        JPanel result = createBandPanel();
        result.setLayout(new GridBagLayout());
        JLabel label = createPlannerLabel(labelKey);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0.42;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        result.add(label, constraints);

        value.setForeground(DOSSIER_TEXT);
        value.setFont(value.getFont().deriveFont(Font.BOLD, value.getFont().getSize2D() * 0.82f));
        value.setHorizontalAlignment(SwingConstants.TRAILING);
        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 0.58;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.EAST;
        result.add(value, constraints);

        constraints = createFullWidthConstraints(row);
        constraints.insets = new Insets(2, 0, 2, 0);
        planner.add(result, constraints);
        return label;
    }

    private void configureScheduleAnchorSpinner() {
        JSpinner.DateEditor editor = new JSpinner.DateEditor(scheduleAnchorSpinner,
              resourceMap.getString("schedule.datePattern.text"));
        editor.getFormat().setTimeZone(TimeZone.getTimeZone("UTC"));
        scheduleAnchorSpinner.setEditor(editor);
        scheduleAnchorSpinner.setBorder(BorderFactory.createLineBorder(DOSSIER_DIVIDER));
        scheduleAnchorSpinner.setPreferredSize(new Dimension(UIUtil.scaleForGUI(176),
              scheduleAnchorSpinner.getPreferredSize().height));
        String label = resourceMap.getString("schedule.anchor.text");
        String tooltip = resourceMap.getString("schedule.anchor.tooltip");
        scheduleAnchorSpinner.setToolTipText(tooltip);
        scheduleAnchorSpinner.getAccessibleContext().setAccessibleName(label);
        scheduleAnchorSpinner.getAccessibleContext().setAccessibleDescription(tooltip);
        JFormattedTextField textField = editor.getTextField();
        textField.setBackground(DOSSIER_CONTROL_BACKGROUND);
        textField.setForeground(DOSSIER_TEXT);
        textField.setCaretColor(DOSSIER_ACCENT);
        textField.setHorizontalAlignment(SwingConstants.TRAILING);
        textField.setToolTipText(tooltip);
    }

    private void configureSpinner(JSpinner spinner, String pattern, String labelKey, String tooltipKey) {
        configureNumberSpinner(spinner, pattern, resourceMap.getString(labelKey), resourceMap.getString(tooltipKey));
    }

    private void configureNumberSpinner(JSpinner spinner, String pattern, String label, String tooltip) {
        spinner.setEditor(new JSpinner.NumberEditor(spinner, pattern));
        spinner.setBorder(BorderFactory.createLineBorder(DOSSIER_DIVIDER));
        spinner.setPreferredSize(new Dimension(UIUtil.scaleForGUI(88), spinner.getPreferredSize().height));
        spinner.setToolTipText(tooltip);
        spinner.getAccessibleContext().setAccessibleName(label);
        spinner.getAccessibleContext().setAccessibleDescription(tooltip);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            JFormattedTextField textField = editor.getTextField();
            textField.setBackground(DOSSIER_CONTROL_BACKGROUND);
            textField.setForeground(DOSSIER_TEXT);
            textField.setCaretColor(DOSSIER_ACCENT);
            textField.setHorizontalAlignment(SwingConstants.TRAILING);
            textField.setToolTipText(tooltip);
        }
    }

    private void addPlannerInput(JPanel planner, int row, String labelKey, String tooltipKey, JSpinner spinner) {
        addPlannerControl(planner, row, resourceMap.getString(labelKey), resourceMap.getString(tooltipKey), spinner);
    }

    private void addPlannerControl(JPanel planner, int row, String labelText, String tooltip, JComponent control) {
        JPanel input = createBandPanel();
        input.setLayout(new GridBagLayout());
        JLabel label = new JLabel(labelText);
        label.setForeground(DOSSIER_MUTED_TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() * 0.8f));
        label.setLabelFor(control);
        label.setToolTipText(tooltip);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        input.add(label, constraints);

        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.EAST;
        input.add(control, constraints);

        constraints = createFullWidthConstraints(row);
        constraints.insets = new Insets(3, 0, 3, 0);
        planner.add(input, constraints);
    }

    private JLabel createPlannerLabel(String labelKey) {
        JLabel label = new JLabel(resourceMap.getString(labelKey));
        label.setForeground(DOSSIER_MUTED_TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() * 0.8f));
        return label;
    }

    private void updateRequiredAcceleration(double desiredTotalDays) {
        AbstractLocation currentLocation = getCurrentLocation();
        RequiredAcceleration result = JumpPathItinerary.solveRequiredAcceleration(path, campaign.getLocalDate(),
              desiredTotalDays, getFleetSystem(currentLocation), getCurrentTransit(currentLocation),
              circuitPlan);
        String resultText;
        if (result.isPossible()) {
            resultText = format(resourceMap.getString("planning.requiredAcceleration.format"),
                  formatNumber(result.accelerationG().orElseThrow(), 2));
            requiredAccelerationValue.setForeground(DOSSIER_ACCENT);
        } else {
            resultText = resourceMap.getString("planning.requiredAcceleration.impossible.text");
            requiredAccelerationValue.setForeground(DOSSIER_ACTIVE);
        }
        requiredAccelerationValue.setText(resultText);
        requiredAccelerationValue.getAccessibleContext().setAccessibleName(format(
              resourceMap.getString("planning.requiredAcceleration.accessible.format"), resultText));
    }

    private void refreshPlanPresentation() {
        schedule = calculateSchedule();
        startingTransitValue.setText(formatDays(itineraryPlan.startingTransitDays()));
        endingTransitValue.setText(formatDays(itineraryPlan.endingTransitDays()));
        rechargeValue.setText(formatDays(itineraryPlan.rechargeDays()));
        totalTimeValue.setText(formatDays(itineraryPlan.totalDays() + (schedule.totalDwellHours() / 24.0)));
        refreshSchedulePresentation();
        populateItinerary(itinerarySection);
        itinerarySection.revalidate();
        itinerarySection.repaint();
    }

    private JPanel createItinerary() {
        JPanel itinerary = createSection("section.itinerary.text");
        populateItinerary(itinerary);
        return itinerary;
    }

    private void populateItinerary(JPanel itinerary) {
        while (itinerary.getComponentCount() > 1) {
            itinerary.remove(1);
        }

        LocalDate currentDate = schedule.departure().toLocalDate();
        Color routeColor = isActiveRoute() ? DOSSIER_ACTIVE : DOSSIER_ACCENT;

        for (JumpPathSchedule.Entry scheduledEntry : schedule.entries()) {
            TimelineEntry entry = scheduledEntry.itineraryEntry();
            JPanel waypoint = createBandPanel();
            waypoint.setLayout(new GridBagLayout());
            if (entry.sequence() > 1) {
                waypoint.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DOSSIER_DIVIDER));
            }

            JLabel sequence = new JLabel(String.format("%02d", entry.sequence()));
            sequence.setForeground(routeColor);
            sequence.setFont(sequence.getFont().deriveFont(Font.BOLD));
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.gridheight = 2;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.insets = new Insets(8, 0, 8, 12);
            waypoint.add(sequence, constraints);

            String systemNameText = entry.system().getPrintableName(currentDate);
            if (entry.destination() && (path.getTargetPlanet() != null)) {
                systemNameText = format(resourceMap.getString("timeline.targetPlanet.format"), systemNameText,
                      path.getTargetPlanet().getPrintableName(currentDate));
            }
            JLabel systemName = new JLabel(systemNameText);
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

            int eventRow = 1;
            if (!entry.origin()) {
                int legIndex = entry.sequence() - 2;
                if (legIndex < navigationAssessment.legs().size()) {
                    LegAssessment legAssessment = navigationAssessment.legs().get(legIndex);
                    JLabel legFacts = new JLabel(formatLegFacts(legAssessment, locale, resourceMap));
                    legFacts.setForeground(switch (legAssessment.severity()) {
                        case BLOCKED -> new Color(234, 86, 86);
                        case CAUTION -> DOSSIER_ACTIVE;
                        default -> DOSSIER_MUTED_TEXT;
                    });
                    legFacts.setFont(legFacts.getFont().deriveFont(Font.PLAIN,
                          legFacts.getFont().getSize2D() * 0.74f));
                    legFacts.getAccessibleContext().setAccessibleName(legFacts.getText());
                    constraints = new GridBagConstraints();
                    constraints.gridx = 1;
                    constraints.gridy = eventRow++;
                    constraints.gridwidth = 2;
                    constraints.weightx = 1.0;
                    constraints.fill = GridBagConstraints.HORIZONTAL;
                    constraints.anchor = GridBagConstraints.WEST;
                    constraints.insets = new Insets(0, 0, 3, 0);
                    waypoint.add(legFacts, constraints);
                }
            }
            if (entry.origin()) {
                addTimelineEvent(waypoint, eventRow++, resourceMap.getString("timeline.originStart.text"),
                    formatScheduleMoment(scheduledEntry.arrival(), locale));
                addTimelineEvent(waypoint, eventRow++,
                    format(resourceMap.getString("timeline.jumpDeparture.format"),
                        formatDuration(itineraryPlan.startingTransitDays())),
                    formatScheduleMoment(scheduledEntry.departure(), locale));
            } else {
                addTimelineEvent(waypoint, eventRow++, resourceMap.getString("timeline.jumpArrival.text"),
                    formatScheduleMoment(scheduledEntry.arrival(), locale));
            }
            if (!entry.origin() && !entry.destination()) {
                if (scheduledEntry.dwellHours() > 0) {
                    addTimelineEvent(waypoint, eventRow++,
                        format(resourceMap.getString("timeline.rechargeComplete.format"),
                            formatHours(entry.rechargeHours(), locale, resourceMap)),
                        formatScheduleMoment(scheduledEntry.readyForDeparture(), locale));
                    addTimelineEvent(waypoint, eventRow++,
                        format(resourceMap.getString("timeline.dwellDeparture.format"),
                            formatHours(scheduledEntry.dwellHours(), locale, resourceMap)),
                        formatScheduleMoment(scheduledEntry.departure(), locale));
                } else {
                    addTimelineEvent(waypoint, eventRow++,
                        format(resourceMap.getString("timeline.rechargeDeparture.format"),
                            formatHours(entry.rechargeHours(), locale, resourceMap)),
                        formatScheduleMoment(scheduledEntry.departure(), locale));
                }
            }
            if (entry.destination()) {
                addTimelineEvent(waypoint, eventRow,
                    format(resourceMap.getString("timeline.endpointArrival.format"),
                        formatDuration(entry.endpointTransitDays())),
                    formatScheduleMoment(scheduledEntry.endpointArrival(), locale));
            }

            constraints = createFullWidthConstraints(entry.sequence());
            itinerary.add(waypoint, constraints);
        }
        }

        static String formatLegFacts(LegAssessment assessment, Locale locale, ResourceBundle resources) {
          NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
          numberFormat.setMaximumFractionDigits(2);
          numberFormat.setMinimumFractionDigits(0);
          String status = navigationStatusText(assessment, resources);
          String recharge = Double.isFinite(assessment.facts().rechargeHours())
              ? format(resources.getString("timeline.rechargeHours.format"),
                  numberFormat.format(assessment.facts().rechargeHours()))
              : resources.getString("timeline.rechargeImpossible.text");
          String solar = Double.isFinite(assessment.facts().solarRechargeHours())
              ? format(resources.getString("timeline.solarRecharge.format"),
                  numberFormat.format(assessment.facts().solarRechargeHours()))
              : resources.getString("timeline.solarRechargeImpossible.text");
          String stations = assessment.facts().rechargeStationCount() == 0
              ? resources.getString("timeline.noRechargeStation.text")
              : format(resources.getString("timeline.rechargeStations.format"),
                  assessment.facts().rechargeStationCount());
          String rechargeSources = format(resources.getString("timeline.rechargeSources.format"), solar, stations);
          String circuit = assessment.facts().commandCircuitAssumed()
              ? resources.getString("timeline.circuitAssumed.text")
              : "";
          return format(resources.getString("timeline.legFacts.format"),
              numberFormat.format(assessment.facts().distanceLy()),
              assessment.facts().minimumStandardJumps(), status, recharge, rechargeSources, circuit);
        }

        private static String navigationStatusText(LegAssessment assessment, ResourceBundle resources) {
          NavigationRouteAnalysis.FindingKind primaryFinding = assessment.findings().stream()
              .filter(finding -> finding.severity() == Severity.BLOCKED)
              .map(NavigationRouteAnalysis.Finding::kind)
              .findFirst()
              .orElseGet(() -> assessment.findings().stream()
                  .filter(finding -> finding.severity() == Severity.CAUTION)
                  .map(NavigationRouteAnalysis.Finding::kind)
                  .findFirst()
                  .orElse(null));
          if (primaryFinding == null) {
            return resources.getString("timeline.status.clear.text");
          }
          return resources.getString(switch (primaryFinding) {
            case OUT_OF_STANDARD_JUMP_RANGE -> "timeline.status.rangeBlocked.text";
            case ACCESS_DENIED -> "timeline.status.accessBlocked.text";
            case ABANDONED_DESTINATION_AVOIDED -> "timeline.status.emptyBlocked.text";
            case ABANDONED_DESTINATION_ALLOWED -> "timeline.status.emptyCaution.text";
            case RECHARGE_IMPOSSIBLE -> "timeline.status.rechargeBlocked.text";
            default -> "timeline.status.blocked.text";
          });
        }

        private PathAssessment calculateNavigationAssessment() {
          PathAssessment assessment = campaign.assessNavigationPath(path.getSystems(), circuitPlan::usesCircuitAt);
          return assessment == null ? new PathAssessment(List.of(), Severity.CLEAR) : assessment;
        }

        private void addTimelineEvent(JPanel waypoint, int row, String eventText, String momentText) {
          JLabel event = new JLabel(eventText);
          event.setForeground(DOSSIER_MUTED_TEXT);
          event.setFont(event.getFont().deriveFont(Font.BOLD, event.getFont().getSize2D() * 0.78f));
          GridBagConstraints constraints = new GridBagConstraints();
          constraints.gridx = 1;
          constraints.gridy = row;
          constraints.weightx = 0.42;
          constraints.fill = GridBagConstraints.HORIZONTAL;
          constraints.anchor = GridBagConstraints.WEST;
          constraints.insets = new Insets(1, 0, 2, 8);
          waypoint.add(event, constraints);

          JLabel moment = new JLabel(momentText);
          moment.setForeground(DOSSIER_TEXT);
          moment.setFont(moment.getFont().deriveFont(Font.PLAIN, moment.getFont().getSize2D() * 0.78f));
          moment.setHorizontalAlignment(SwingConstants.TRAILING);
          constraints = new GridBagConstraints();
          constraints.gridx = 2;
          constraints.gridy = row;
          constraints.weightx = 0.58;
          constraints.fill = GridBagConstraints.HORIZONTAL;
          constraints.anchor = GridBagConstraints.EAST;
          constraints.insets = new Insets(1, 0, 2, 0);
          waypoint.add(moment, constraints);
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

    private JLabel addMetric(JPanel summary, int metricIndex, String labelKey, String value) {
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
        return metricValue;
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
              campaign.getPlayerForce().getFactionStandings(), campaign.getFutureContracts());
    }

    private Plan calculatePlan(double accelerationG) {
        AbstractLocation currentLocation = getCurrentLocation();
        return JumpPathItinerary.calculate(path, campaign.getLocalDate(), accelerationG,
              getFleetSystem(currentLocation), getCurrentTransit(currentLocation), circuitPlan);
    }

    private Result calculateSchedule() {
        LocalDateTime anchor = scheduleMode == Mode.DEPART_AT ? departureAnchor : arrivalDeadline;
        return JumpPathSchedule.calculate(itineraryPlan, requestedStops, dwellHoursByRequestedStop,
              scheduleMode, anchor, earliestFeasibleDeparture);
    }

    private void refreshSchedulePresentation() {
        if (scheduleAnchorValue == null) {
            return;
        }
        scheduleAnchorLabel.setText(resourceMap.getString(schedule.mode() == Mode.DEPART_AT
              ? "schedule.departureAnchor.text"
              : "schedule.arrivalDeadline.text"));
        scheduleAnchorValue.setText(formatScheduleMoment(schedule.anchor(), locale));
        scheduleDepartureValue.setText(formatScheduleMoment(schedule.departure(), locale));
        scheduleArrivalValue.setText(formatScheduleMoment(schedule.arrival(), locale));
        scheduleDwellValue.setText(formatHours(schedule.totalDwellHours(), locale, resourceMap));
        scheduleStatusValue.setText(scheduleStatusText(schedule, locale, resourceMap));
        scheduleStatusValue.setForeground(schedule.feasible() ? DOSSIER_ACCENT : DOSSIER_ACTIVE);
        scheduleStatusValue.getAccessibleContext().setAccessibleName(scheduleStatusValue.getText());
    }

    private void updateScheduleAnchorControl() {
        adjustingScheduleAnchor = true;
        LocalDateTime anchor = scheduleMode == Mode.DEPART_AT ? departureAnchor : arrivalDeadline;
        scheduleAnchorSpinner.setValue(toDate(anchor));
        adjustingScheduleAnchor = false;
    }

    private static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime fromDate(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC).withSecond(0).withNano(0);
    }

    static boolean isCourseSelected(JumpPath path, Course course) {
        List<PlanetarySystem> pathSystems = path.getSystems();
        List<PlanetarySystem> courseSystems = course.systems();
        if (pathSystems.size() != courseSystems.size()) {
            return false;
        }
        for (int index = 0; index < pathSystems.size(); index++) {
            if (!pathSystems.get(index).getId().equals(courseSystems.get(index).getId())) {
                return false;
            }
        }
        return true;
    }

    static CircuitPlan initialCircuitPlan(JumpPath path, List<Course> courses, boolean campaignUsesCircuit) {
        for (Course course : courses) {
            if (isCourseSelected(path, course) && (course.circuitCoverage() == CircuitCoverage.WHOLE)) {
                return CircuitPlan.whole();
            }
        }
        return campaignUsesCircuit ? CircuitPlan.whole() : CircuitPlan.none();
    }

    private AbstractLocation getCurrentLocation() {
        return campaign.getPlayerForce().getForceDetachment().getCurrentLocation();
    }

    private PlanetarySystem getFleetSystem(AbstractLocation currentLocation) {
        return (currentLocation == null) ? null : currentLocation.getCurrentSystem();
    }

    private double getCurrentTransit(AbstractLocation currentLocation) {
        return (currentLocation == null) ? 0.0 : currentLocation.getTransitTime();
    }

    private String formatDays(double days) {
        return format(resourceMap.getString("metric.days.format"), formatNumber(days, 2));
    }

    private String formatDuration(double days) {
        long roundedHours = Math.round(days * 24.0);
        String duration = formatHours(Math.abs(roundedHours), locale, resourceMap);
        return (roundedHours < 0)
              ? format(resourceMap.getString("timeline.negativeDuration.format"), duration)
              : duration;
    }

    private String formatNumber(double value, int maximumFractionDigits) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMaximumFractionDigits(maximumFractionDigits);
        return numberFormat.format(value);
    }

    static String formatTimelineMoment(LocalDate startDate, double elapsedDays, Locale locale,
          ResourceBundle resources) {
        long roundedHours = Math.round(elapsedDays * 24.0);
        long elapsedWholeDays = Math.floorDiv(roundedHours, 24);
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            .withLocale(locale)
                            .format(startDate.plusDays(elapsedWholeDays));
        String sign = (roundedHours < 0) ? "-" : "+";
        return format(resources.getString("timeline.moment.format"), date, sign,
              formatHours(Math.abs(roundedHours), locale, resources));
    }

    static String formatScheduleMoment(LocalDateTime moment, Locale locale) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                     .withLocale(locale)
                     .format(moment);
    }

    static String scheduleStatusText(Result schedule, Locale locale, ResourceBundle resources) {
        if (schedule.feasible()) {
            return resources.getString(schedule.mode() == Mode.DEPART_AT
                  ? "schedule.status.forecast.text"
                  : "schedule.status.feasible.text");
        }
        long missedHours = Math.max(1,
              (Duration.between(schedule.departure(), schedule.earliestFeasibleDeparture()).toMinutes() + 59) / 60);
        return format(resources.getString(schedule.mode() == Mode.DEPART_AT
                    ? "schedule.status.departureUnavailable.format"
                    : "schedule.status.missed.format"),
              formatHours(missedHours, locale, resources));
    }

    private static String formatHours(long totalHours, Locale locale, ResourceBundle resources) {
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(locale);
        long days = totalHours / 24;
        long hours = totalHours % 24;
        if ((days > 0) && (hours > 0)) {
            return format(resources.getString("timeline.daysHours.format"), numberFormat.format(days),
                  numberFormat.format(hours));
        } else if (days > 0) {
            return format(resources.getString("timeline.days.format"), numberFormat.format(days));
        }
        return format(resources.getString("timeline.hours.format"), numberFormat.format(hours));
    }

    private String getSystemName(PlanetarySystem system, LocalDate currentDate) {
        return (system == null) ? resourceMap.getString("dossier.unknownSystem.text")
                     : system.getPrintableName(currentDate);
    }
}

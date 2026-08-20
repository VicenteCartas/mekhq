/*
 * Copyright (C) 2017-2026 The MegaMek Team. All Rights Reserved.
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
import static megamek.client.ui.WrapLayout.wordWrap;
import static mekhq.MHQConstants.CONFIRMATION_BEGIN_TRANSIT;
import static mekhq.campaign.enums.DailyReportType.GENERAL;
import static mekhq.campaign.market.contractMarket.ContractAutomation.outOfContractMothballAutomation;
import static mekhq.campaign.market.personnelMarket.enums.PersonnelMarketStyle.MEKHQ;
import static mekhq.campaign.personnel.skills.SkillType.EXP_REGULAR;
import static mekhq.campaign.randomEvents.prisoners.RecoverMIAPersonnel.abandonMissingPersonnel;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Objects;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import megamek.client.ui.util.UIUtil;
import megamek.common.event.Subscribe;
import megamek.common.ui.FastJScrollPane;
import mekhq.MekHQ;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.events.NewDayEvent;
import mekhq.campaign.events.OptionsChangedEvent;
import mekhq.campaign.finances.Money;
import mekhq.campaign.market.personnelMarket.markets.NewPersonnelMarket;
import mekhq.campaign.mission.TransportCostCalculations;
import mekhq.campaign.unit.Unit;
import mekhq.campaign.universe.Planet;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.utilities.JumpBlockers;
import mekhq.gui.baseComponents.immersiveDialogs.ImmersiveDialogConfirmation;
import mekhq.gui.baseComponents.roundedComponents.RoundedJButton;
import mekhq.gui.dialog.JumpCostsSummary;
import mekhq.gui.enums.MHQTabType;
import mekhq.gui.panels.TutorialHyperlinkPanel;
import mekhq.gui.utilities.JSuggestField;
import mekhq.gui.view.JumpPathViewPanel;
import mekhq.gui.view.PlanetViewPanel;

/**
 * Displays interstellar map and contains transit controls.
 */
// FIXME: this class should not inherit from CampaignGuiTab because it is managed by NavigationTab now
public final class MapTab extends CampaignGuiTab implements ActionListener {
    private static final int PADDING = UIUtil.scaleForGUI(10);
    private static final Color ROUTE_STRIP_BACKGROUND = new Color(7, 16, 27);
    private static final Color ROUTE_STRIP_BORDER = new Color(35, 66, 82);
    private static final Color ROUTE_TEXT_COLOR = new Color(218, 231, 235);
    private static final Color ROUTE_MUTED_COLOR = new Color(132, 153, 161);
    private static final Color PLANNED_ROUTE_COLOR = new Color(65, 210, 224);
    private static final Color ACTIVE_ROUTE_COLOR = new Color(235, 166, 66);
    private static final Color HUD_CONTROL_BACKGROUND = new Color(15, 30, 43);
    private static final int HUD_MINIMUM_HEIGHT = UIUtil.scaleForGUI(38);
    private static final int ROUTE_TRANSITION_FRAME_DELAY_MS = 16;
    private static final long ROUTE_TRANSITION_DURATION_NS = 280_000_000L;
    private static final float ROUTE_TRANSITION_MIDPOINT = 0.5f;
    private static final float ROUTE_TRANSITION_MIN_ALPHA = 0.15f;
    private static final float ROUTE_REVEAL_SWEEP_END = 0.75f;
    private static final float ROUTE_REVEAL_MAX_ALPHA = 0.75f;

    private JViewport mapView;
    private JPanel panMapView;
    private InterstellarMapPanel panMap;
    private PlanetarySystemMapPanel panSystem;
    private JScrollPane scrollPlanetView;
    private ResourceBundle resourceMap;
    private RoundedJButton btnCalculateJumpPath;
    private RoundedJButton btnBeginTransit;
    private JLabel lblRouteStatus;
    private JLabel lblRouteDestination;
    private JLabel lblRouteJumps;
    private JLabel lblRouteDuration;
    private JLabel lblRouteNextJump;
    private JLabel lblRouteCost;
    private RouteStripPanel routeStrip;
    private Timer routeTransitionTimer;
    private RouteStripSnapshot presentedRouteSnapshot;
    private RouteStripSnapshot targetRouteSnapshot;
    private long routeTransitionStartTime;
    private float routeTransitionStartAlpha = 1.0f;
    private boolean routeTransitionMidpointApplied;
    private boolean routeRevealTransition;
    private DossierIdentity presentedDossierIdentity;
    JSuggestField suggestPlanet;

    //region Constructors
    public MapTab(CampaignGUI gui, String tabName) {
        super(gui, tabName);
    }
    //endregion Constructors

    @Override
    public MHQTabType tabType() {
        return null;
    }

    @Override
    public void removeNotify() {
        finishRouteTransition();
        super.removeNotify();
    }

    /*
     * (non-Javadoc)
     *
     * @see mekhq.gui.CampaignGuiTab#initTab()
     */
    @Override
    public void initTab() {
        resourceMap = ResourceBundle.getBundle("mekhq.resources.CampaignGUI",
              MekHQ.getMHQOptions().getLocale());

        panMapView = new JPanel(new BorderLayout());
        panMapView.add(createNavigationHud(), BorderLayout.PAGE_START);

        //the actual map
        panMap = new InterstellarMapPanel(getCampaign(), getCampaignGui());
        // let's go ahead and zoom in on the current location
        panMap.setSelectedSystem(getCampaign().getPlayerForce()
                                       .getForceDetachment()
                                       .getCurrentLocation()
                                       .getCurrentSystem());
        panMapView.add(panMap, BorderLayout.CENTER);

        JPanel panMapFooter = new JPanel(new BorderLayout());
        panMapFooter.add(createRouteStrip(), BorderLayout.NORTH);
        JPanel pnlTutorial = new TutorialHyperlinkPanel("mapTab.keyText");
        panMapFooter.add(pnlTutorial, BorderLayout.SOUTH);
        panMapView.add(panMapFooter, BorderLayout.SOUTH);

        mapView = new JViewport();
        mapView.setMinimumSize(new Dimension(600, 600));
        mapView.setView(panMapView);

        scrollPlanetView = new FastJScrollPane();
        scrollPlanetView.setBackground(ROUTE_STRIP_BACKGROUND);
        scrollPlanetView.getViewport().setBackground(ROUTE_STRIP_BACKGROUND);
        scrollPlanetView.setBorder(null);
        scrollPlanetView.setMinimumSize(new Dimension(400, 600));
        scrollPlanetView.setPreferredSize(new Dimension(400, 600));
        scrollPlanetView.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPlanetView.setViewportView(null);
        scrollPlanetView.setBorder(null);
        JSplitPane splitMap = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapView, scrollPlanetView);
        splitMap.setOneTouchExpandable(true);
        splitMap.setResizeWeight(1.0);
        splitMap.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, ev -> refreshPlanetView());

        panMap.setCampaign(getCampaign());
        panMap.addActionListener(this);

        panSystem = new PlanetarySystemMapPanel(getCampaign(), getCampaignGui());
        panSystem.addActionListener(this);

        setLayout(new BorderLayout());
        add(splitMap, BorderLayout.CENTER);
        updateRouteStrip();
    }

    private JPanel createNavigationHud() {
        JPanel navigationHud = new JPanel(new GridBagLayout());
        navigationHud.setBackground(ROUTE_STRIP_BACKGROUND);
        navigationHud.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, 0, 1, 0, ROUTE_STRIP_BORDER),
              BorderFactory.createEmptyBorder(4, PADDING, 4, PADDING)));

        JLabel searchLabel = new JLabel(resourceMap.getString("mapHud.systemSearch.text"));
        searchLabel.setForeground(ROUTE_MUTED_COLOR);
        searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD,
              searchLabel.getFont().getSize2D() * 0.8f));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, PADDING);
        navigationHud.add(searchLabel, constraints);

        suggestPlanet = new JSuggestField(getFrame(), getCampaign().getSystemNames());
        suggestPlanet.setToolTipText(resourceMap.getString("mapHud.systemSearch.toolTipText"));
        suggestPlanet.setBackground(HUD_CONTROL_BACKGROUND);
        suggestPlanet.setForeground(ROUTE_TEXT_COLOR);
        suggestPlanet.setCaretColor(PLANNED_ROUTE_COLOR);
        suggestPlanet.setSelectionColor(ROUTE_STRIP_BORDER);
        suggestPlanet.setSelectedTextColor(ROUTE_TEXT_COLOR);
        suggestPlanet.setBorder(BorderFactory.createLineBorder(ROUTE_STRIP_BORDER));
        suggestPlanet.addActionListener(ev -> {
            PlanetarySystem system = getCampaign().getSystemByName(suggestPlanet.getText());
            if (system != null) {
                panMap.setSelectedSystem(system);
                panSystem.updatePlanetarySystem(system);
                refreshPlanetView();
            }
        });
        suggestPlanet.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                suggestPlanet.setBorder(BorderFactory.createLineBorder(PLANNED_ROUTE_COLOR));
            }

            @Override
            public void focusLost(FocusEvent e) {
                suggestPlanet.setBorder(BorderFactory.createLineBorder(ROUTE_STRIP_BORDER));
            }
        });
        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 0, PADDING);
        navigationHud.add(suggestPlanet, constraints);

        JButton centerOnFleet = createHudButton("mapHud.centerOnFleet.text",
              "mapHud.centerOnFleet.toolTipText");
        centerOnFleet.addActionListener(event -> panMap.centerOnCurrentSystem());
        constraints = new GridBagConstraints();
        constraints.gridx = 2;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(0, 0, 0, PADDING);
        navigationHud.add(centerOnFleet, constraints);

        JButton routingOptions = createHudButton("mapHud.routingOptions.text",
              "mapHud.routingOptions.toolTipText");
        JPopupMenu routingMenu = createRoutingMenu();
        routingOptions.addActionListener(event -> routingMenu.show(routingOptions, 0, routingOptions.getHeight()));
        constraints = new GridBagConstraints();
        constraints.gridx = 3;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.EAST;
        navigationHud.add(routingOptions, constraints);

        Dimension preferredSize = navigationHud.getPreferredSize();
        preferredSize.height = Math.max(preferredSize.height, HUD_MINIMUM_HEIGHT);
        navigationHud.setPreferredSize(preferredSize);
        return navigationHud;
    }

    private JPopupMenu createRoutingMenu() {
        JPopupMenu routingMenu = new JPopupMenu();
        routingMenu.setBorder(BorderFactory.createLineBorder(ROUTE_STRIP_BORDER));

        JCheckBoxMenuItem avoidAbandonedSystems = new JCheckBoxMenuItem(
              resourceMap.getString("chkAvoidAbandonedSystems.text"));
        avoidAbandonedSystems.setToolTipText(wordWrap(
              resourceMap.getString("chkAvoidAbandonedSystems.toolTipText")));
        avoidAbandonedSystems.setSelected(getCampaign().getPlayerForce().isAvoidingEmptySystems());
        avoidAbandonedSystems.addActionListener(event -> getCampaign().getPlayerForce()
              .setIsAvoidingEmptySystems(avoidAbandonedSystems.isSelected()));
        routingMenu.add(avoidAbandonedSystems);

        JCheckBoxMenuItem useCommandCircuits = new JCheckBoxMenuItem(
              resourceMap.getString("chkUseCommandCircuits.text"));
        useCommandCircuits.setToolTipText(wordWrap(resourceMap.getString("chkUseCommandCircuits.toolTipText")));
        useCommandCircuits.setSelected(getCampaign().getPlayerForce().isOverridingCommandCircuitRequirements());
        useCommandCircuits.addActionListener(event -> {
            getCampaign().getPlayerForce()
                  .setIsOverridingCommandCircuitRequirements(useCommandCircuits.isSelected());
            updateRouteStrip();
        });
        routingMenu.add(useCommandCircuits);
        return routingMenu;
    }

    private JButton createHudButton(String textKey, String toolTipKey) {
        JButton button = new JButton(resourceMap.getString(textKey));
        button.setToolTipText(resourceMap.getString(toolTipKey));
        button.setForeground(ROUTE_TEXT_COLOR);
        button.setBackground(HUD_CONTROL_BACKGROUND);
        button.setFocusPainted(false);
          button.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(ROUTE_STRIP_BORDER),
              BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        return button;
    }

    private JPanel createRouteStrip() {
        routeStrip = new RouteStripPanel();
        routeStrip.setBackground(ROUTE_STRIP_BACKGROUND);
        routeStrip.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(1, 0, 0, 0, ROUTE_STRIP_BORDER),
              BorderFactory.createEmptyBorder(4, PADDING, 4, PADDING)));

        lblRouteStatus = createRouteLabel(Font.BOLD);
        lblRouteDestination = createRouteLabel(Font.PLAIN);
        lblRouteJumps = createRouteLabel(Font.PLAIN);
        lblRouteDuration = createRouteLabel(Font.PLAIN);
        lblRouteNextJump = createRouteLabel(Font.PLAIN);
        lblRouteCost = createRouteLabel(Font.PLAIN);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 2, PADDING);
        routeStrip.add(lblRouteStatus, constraints);

        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 2, PADDING);
        routeStrip.add(lblRouteDestination, constraints);

        constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, PADDING);
        routeStrip.add(lblRouteJumps, constraints);

        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, PADDING);
        routeStrip.add(lblRouteDuration, constraints);

        constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, PADDING);
        routeStrip.add(lblRouteNextJump, constraints);

        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, PADDING);
        routeStrip.add(lblRouteCost, constraints);

        JButton btnJumpFees = createHudButton("btnJumpFees.text", "btnJumpFees.toolTipText");
        btnJumpFees.addActionListener(evt -> showJumpFeeSummary());
        constraints = new GridBagConstraints();
        constraints.gridx = 2;
        constraints.gridy = 0;
        constraints.gridheight = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(0, 0, 0, PADDING);
        routeStrip.add(btnJumpFees, constraints);

        btnCalculateJumpPath = new RoundedJButton(resourceMap.getString("btnCalculateJumpPath.text"));
        btnCalculateJumpPath.setToolTipText(resourceMap.getString("btnCalculateJumpPath.toolTipText"));
        btnCalculateJumpPath.addActionListener(ev -> calculateJumpPath());
        constraints = new GridBagConstraints();
        constraints.gridx = 3;
        constraints.gridy = 0;
        constraints.gridheight = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(0, 0, 0, PADDING);
        routeStrip.add(btnCalculateJumpPath, constraints);

        btnBeginTransit = new RoundedJButton(resourceMap.getString("btnBeginTransit.text"));
        btnBeginTransit.setToolTipText(resourceMap.getString("btnBeginTransit.toolTipText"));
        btnBeginTransit.addActionListener(ev -> beginTransit());
        constraints = new GridBagConstraints();
        constraints.gridx = 4;
        constraints.gridy = 0;
        constraints.gridheight = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.EAST;
        routeStrip.add(btnBeginTransit, constraints);

        routeTransitionTimer = new Timer(ROUTE_TRANSITION_FRAME_DELAY_MS, event -> updateRouteTransition());
        routeTransitionTimer.setCoalesce(true);
        return routeStrip;
    }

    private void showJumpFeeSummary() {
        TransportCostCalculations transportCostCalculations =
              getCampaign().getTransportCostCalculation(EXP_REGULAR);
        transportCostCalculations.calculateJumpCostForEachDay();
        new JumpCostsSummary(getCampaignGui().getFrame(), transportCostCalculations);
    }

    private JLabel createRouteLabel(int fontStyle) {
        JLabel label = new JLabel();
        label.setForeground(ROUTE_TEXT_COLOR);
        label.setFont(label.getFont().deriveFont(fontStyle));
        return label;
    }

    private void updateRouteStrip() {
        if ((panMap == null) || (lblRouteStatus == null)) {
            return;
        }

        transitionToRouteSnapshot(createRouteStripSnapshot());
    }

    private RouteStripSnapshot createRouteStripSnapshot() {
        AbstractLocation currentLocation = getCampaign().getPlayerForce()
                                                    .getForceDetachment()
                                                    .getCurrentLocation();
        JumpPath proposedPath = panMap.getJumpPath();
        JumpPath activePath = (currentLocation == null) ? null : currentLocation.getJumpPath();
        boolean hasProposedPath = (proposedPath != null) && !proposedPath.isEmpty();
        boolean hasActivePath = (activePath != null) && !activePath.isEmpty();
        JumpPath displayedPath = hasProposedPath ? proposedPath : (hasActivePath ? activePath : null);
        String unavailable = resourceMap.getString("routeStrip.unavailable.text");
        boolean isCostVisible = getCampaign().getCampaignOptions().isPayForTransport();
        boolean isPlotCourseVisible = !hasProposedPath;
        boolean isPlotCourseEnabled = isPlotCourseVisible && (panMap.getSelectedSystem() != null);

        if (displayedPath == null) {
            return new RouteStripSnapshot(RouteStripState.NO_ROUTE,
                  resourceMap.getString("routeStrip.noRoute.text"),
                  ROUTE_MUTED_COLOR,
                  resourceMap.getString("routeStrip.destination.text") + "  " + unavailable,
                  resourceMap.getString("routeStrip.jumps.text") + "  " + unavailable,
                  resourceMap.getString("routeStrip.duration.text") + "  " + unavailable,
                  resourceMap.getString("routeStrip.nextJump.text") + "  " + unavailable,
                  resourceMap.getString("routeStrip.estimatedCost.text") + "  " + unavailable,
                  ROUTE_MUTED_COLOR,
                  isCostVisible,
                  isPlotCourseVisible,
                  isPlotCourseEnabled,
                  hasProposedPath);
        }

        boolean isPlannedRoute = hasProposedPath;
        String statusText = resourceMap.getString(isPlannedRoute
              ? "routeStrip.plannedRoute.text"
              : "routeStrip.inTransit.text");
        PlanetarySystem destination = displayedPath.getLastSystem();
        String destinationName = (destination == null)
              ? unavailable
              : destination.getPrintableName(getCampaign().getLocalDate());
        String destinationText = resourceMap.getString("routeStrip.destination.text") + "  " + destinationName;
        String jumpsText = resourceMap.getString(isPlannedRoute
              ? "routeStrip.jumps.text"
              : "routeStrip.jumpsRemaining.text") + "  " + displayedPath.getJumps();

          double currentTransit = (currentLocation == null) ? 0.0 : currentLocation.getTransitTime();
          int duration = (int) ceil(displayedPath.getTotalTime(getCampaign().getLocalDate(), currentTransit,
              getCampaign().isUseCommandCircuit()));
        String durationText = resourceMap.getString("routeStrip.duration.text") + "  " + duration + ' '
              + resourceMap.getString("routeStrip.days.text");
          String nextJumpText = createNextJumpText(displayedPath, currentLocation, unavailable);

        String costText = resourceMap.getString("routeStrip.estimatedCost.text") + "  " + unavailable;
        if (isCostVisible) {
            TransportCostCalculations calculations = getCampaign().getTransportCostCalculation(EXP_REGULAR);
            Money journeyCost = calculations.calculateJumpCostForEntireJourney(duration, displayedPath.getJumps());
            costText = resourceMap.getString("routeStrip.estimatedCost.text") + "  "
                + journeyCost.toAmountAndSymbolString();
        }

        return new RouteStripSnapshot(isPlannedRoute ? RouteStripState.PLANNED_ROUTE : RouteStripState.IN_TRANSIT,
              statusText,
              isPlannedRoute ? PLANNED_ROUTE_COLOR : ACTIVE_ROUTE_COLOR,
              destinationText,
              jumpsText,
              durationText,
              nextJumpText,
              costText,
              ROUTE_TEXT_COLOR,
              isCostVisible,
              isPlotCourseVisible,
              isPlotCourseEnabled,
              hasProposedPath);
    }

    private String createNextJumpText(JumpPath displayedPath, AbstractLocation currentLocation, String unavailable) {
        String label = resourceMap.getString("routeStrip.nextJump.text") + "  ";
        if ((displayedPath == null) || displayedPath.isEmpty() || (displayedPath.getJumps() <= 0)
              || (currentLocation == null) || (currentLocation.getCurrentSystem() == null)
              || (displayedPath.getFirstSystem() == null)) {
            return label + unavailable;
        }

        double remainingTransitDays = Math.max(0.0,
              displayedPath.getStartTime(currentLocation.getTransitTime()));
        PlanetarySystem currentSystem = currentLocation.getCurrentSystem();
        double remainingRechargeHours = Math.max(0.0,
              currentSystem.getRechargeTime(getCampaign().getLocalDate(), getCampaign().isUseCommandCircuit())
                    - currentLocation.getRechargeTime());
        double remainingDays = Math.max(remainingTransitDays, remainingRechargeHours / 24.0);
        if (remainingDays <= 0.0) {
            return label + resourceMap.getString("routeStrip.nextJump.ready.text");
        }

        long totalHours = (long) Math.ceil(remainingDays * 24.0);
        long days = totalHours / 24;
        long hours = totalHours % 24;
        String timeText;
        if ((days > 0) && (hours > 0)) {
            timeText = days + resourceMap.getString("routeStrip.nextJump.daySuffix.text") + ' '
                  + hours + resourceMap.getString("routeStrip.nextJump.hourSuffix.text");
        } else if (days > 0) {
            timeText = days + resourceMap.getString("routeStrip.nextJump.daySuffix.text");
        } else {
            timeText = hours + resourceMap.getString("routeStrip.nextJump.hourSuffix.text");
        }
        return label + timeText;
    }

    private void transitionToRouteSnapshot(RouteStripSnapshot nextSnapshot) {
        if (nextSnapshot.equals(targetRouteSnapshot)) {
            return;
        }

        if ((presentedRouteSnapshot == null) || !routeStrip.isDisplayable()) {
            showRouteSnapshotImmediately(nextSnapshot);
            return;
        }

        if (nextSnapshot.equals(presentedRouteSnapshot)) {
            showRouteSnapshotImmediately(nextSnapshot);
            return;
        }

        routeTransitionTimer.stop();
        targetRouteSnapshot = nextSnapshot;
        routeTransitionStartTime = System.nanoTime();
        routeTransitionStartAlpha = routeStrip.getChildAlpha();
        routeTransitionMidpointApplied = false;
        routeRevealTransition = (nextSnapshot.state() == RouteStripState.PLANNED_ROUTE)
              && (presentedRouteSnapshot.state() != RouteStripState.PLANNED_ROUTE);
        btnCalculateJumpPath.setEnabled(false);
        btnBeginTransit.setEnabled(false);
        routeStrip.setRevealProgress(0.0f);
        routeTransitionTimer.restart();
    }

    private void updateRouteTransition() {
        float progress = Math.min(1.0f,
              (float) (System.nanoTime() - routeTransitionStartTime) / ROUTE_TRANSITION_DURATION_NS);

        if (!routeTransitionMidpointApplied && (progress >= ROUTE_TRANSITION_MIDPOINT)) {
            applyRouteSnapshot(targetRouteSnapshot, false);
            routeTransitionMidpointApplied = true;
        }

        float alpha;
        if (progress < ROUTE_TRANSITION_MIDPOINT) {
            float phaseProgress = easeRouteTransition(progress / ROUTE_TRANSITION_MIDPOINT);
            alpha = routeTransitionStartAlpha
                  + ((ROUTE_TRANSITION_MIN_ALPHA - routeTransitionStartAlpha) * phaseProgress);
        } else {
            float phaseProgress = easeRouteTransition((progress - ROUTE_TRANSITION_MIDPOINT)
                  / ROUTE_TRANSITION_MIDPOINT);
            alpha = ROUTE_TRANSITION_MIN_ALPHA + ((1.0f - ROUTE_TRANSITION_MIN_ALPHA) * phaseProgress);
        }
        routeStrip.setChildAlpha(alpha);
        routeStrip.setRevealProgress(routeRevealTransition ? progress : 0.0f);

        if (progress >= 1.0f) {
            finishRouteTransition();
        }
    }

    private static float easeRouteTransition(float progress) {
        return progress * progress * (3.0f - (2.0f * progress));
    }

    private void showRouteSnapshotImmediately(RouteStripSnapshot snapshot) {
        if (routeTransitionTimer != null) {
            routeTransitionTimer.stop();
        }
        targetRouteSnapshot = snapshot;
        routeTransitionMidpointApplied = true;
        routeRevealTransition = false;
        routeStrip.setChildAlpha(1.0f);
        routeStrip.setRevealProgress(0.0f);
        applyRouteSnapshot(snapshot, true);
    }

    private void finishRouteTransition() {
        if (routeTransitionTimer == null) {
            return;
        }
        routeTransitionTimer.stop();
        routeStrip.setChildAlpha(1.0f);
        routeStrip.setRevealProgress(0.0f);
        if (targetRouteSnapshot != null) {
            applyRouteSnapshot(targetRouteSnapshot, true);
        }
        routeTransitionMidpointApplied = true;
        routeRevealTransition = false;
    }

    private void applyRouteSnapshot(RouteStripSnapshot snapshot, boolean enableActions) {
        lblRouteStatus.setText(snapshot.statusText());
        lblRouteStatus.setForeground(snapshot.statusColor());
        lblRouteDestination.setText(snapshot.destinationText());
        lblRouteJumps.setText(snapshot.jumpsText());
        lblRouteDuration.setText(snapshot.durationText());
        lblRouteNextJump.setText(snapshot.nextJumpText());
        lblRouteCost.setText(snapshot.costText());
        setRouteMetricColor(snapshot.metricColor());
        lblRouteCost.setVisible(snapshot.costVisible());
        btnCalculateJumpPath.setVisible(snapshot.plotCourseVisible());
        btnCalculateJumpPath.setEnabled(enableActions && snapshot.plotCourseEnabled());
        btnBeginTransit.setEnabled(enableActions && snapshot.beginTransitEnabled());
        presentedRouteSnapshot = snapshot;
        routeStrip.revalidate();
        routeStrip.repaint();
    }

    private void setRouteMetricColor(Color color) {
        lblRouteDestination.setForeground(color);
        lblRouteJumps.setForeground(color);
        lblRouteDuration.setForeground(color);
        lblRouteNextJump.setForeground(color);
        lblRouteCost.setForeground(color);
    }

    /*
     * (non-Javadoc)
     *
     * @see mekhq.gui.CampaignGuiTab#refreshAll()
     */
    @Override
    public void refreshAll() {
        refreshSystemView();
    }

    private void calculateJumpPath() {
        if (null != panMap.getSelectedSystem()) {
            panMap.setJumpPath(getCampaign().calculateJumpPath(getCampaign().getCurrentSystem(),
                  panMap.getSelectedSystem(), false, false));
            refreshPlanetView();
        }
    }

    private void beginTransit() {
        if (panMap.getJumpPath().isEmpty()) {
            return;
        }

        if (!JumpBlockers.areAllUnitsJumpCapable(getCampaign())) {
            return;
        }

        if (!MekHQ.getMHQOptions().getNagDialogIgnore(CONFIRMATION_BEGIN_TRANSIT)) {
            ImmersiveDialogConfirmation dialog = new ImmersiveDialogConfirmation(getCampaign(),
                  CONFIRMATION_BEGIN_TRANSIT);
            if (!dialog.wasConfirmed()) {
                return;
            }
        }

        // Mothballing
        outOfContractMothballAutomation(getCampaign());

        // Everything else
        JumpPath jumpPath = panMap.getJumpPath();

        boolean isUseCommandCircuits = getCampaign().isUseCommandCircuit();
        int duration = (int) ceil(jumpPath.getTotalTime(getCampaign().getLocalDate(),
              getCampaign().getPlayerForce().getForceDetachment().getCurrentLocation().getTransitTime(),
              isUseCommandCircuits));

        TransportCostCalculations transportCostCalculations = getCampaign().getTransportCostCalculation(EXP_REGULAR);
        Money journeyCost = transportCostCalculations.calculateJumpCostForEntireJourney(duration, jumpPath.getJumps());

        String jumpReport = TransportCostCalculations.performJumpTransaction(getCampaign().getPlayerForce()
                                                                                   .getFinances(), jumpPath,
              getCampaign().getLocalDate(), journeyCost, getCampaign().getCurrentSystem());

        if (!jumpReport.isBlank()) {
            getCampaign().addReport(GENERAL, jumpReport);
        }

        getCampaign().getPlayerForce().getForceDetachment().getCurrentLocation().setJumpPath(panMap.getJumpPath());
        refreshPlanetView();

        panMap.setJumpPath(new JumpPath());
        panMap.repaint();
        updateRouteStrip();

        getCampaign().getUnits().forEach(unit -> unit.setSite(Unit.SITE_FACILITY_BASIC));

        abandonMissingPersonnel(getCampaign());

        NewPersonnelMarket personnelMarket = getCampaign().getPlayerForce().getHumanResources().getNewPersonnelMarket();
        if (personnelMarket.getAssociatedPersonnelMarketStyle() == MEKHQ) {
            personnelMarket.clearCurrentApplicants();
        }
    }

    private void refreshSystemView() {
        updateRouteStrip();
        JumpPath path = panMap.getJumpPath();
        if (null != path && !path.isEmpty()) {
            scrollPlanetView.setViewportView(new JumpPathViewPanel(path, getCampaign()));
            SwingUtilities.invokeLater(() -> scrollPlanetView.getVerticalScrollBar().setValue(0));
            return;
        }
        PlanetarySystem system = panMap.getSelectedSystem();
        if (null != system) {
            showDossier(system, 0);
        }
    }

    private void refreshPlanetView() {
        updateRouteStrip();
        JumpPath path = panMap.getJumpPath();
        if (null != path && !path.isEmpty()) {
            scrollPlanetView.setViewportView(new JumpPathViewPanel(path, getCampaign()));
            SwingUtilities.invokeLater(() -> scrollPlanetView.getVerticalScrollBar().setValue(0));
            return;
        }
        int pos = panSystem.getSelectedPlanetPosition();
        PlanetarySystem system = panMap.getSelectedSystem();
        if (null != system) {
            showDossier(system, pos);
        }
    }

    private void showDossier(PlanetarySystem system, int planetPosition) {
        DossierIdentity dossierIdentity = new DossierIdentity(system.getId(), planetPosition);
        boolean animateReveal = presentedDossierIdentity != null &&
                                      !presentedDossierIdentity.equals(dossierIdentity);
        scrollPlanetView.setViewportView(new PlanetViewPanel(system, getCampaign(), planetPosition, animateReveal));
        presentedDossierIdentity = dossierIdentity;
        SwingUtilities.invokeLater(() -> scrollPlanetView.getVerticalScrollBar().setValue(0));
    }

    /**
     * Switch to the planetary system view, highlighting a specific {@link Planet}
     *
     * @param p The {@link Planet} to select.
     */
    public void switchPlanetaryMap(Planet p) {
        PlanetarySystem s = p.getParentSystem();
        panMap.setSelectedSystem(s);
        panSystem.updatePlanetarySystem(p);
        mapView.setView(panSystem);
        refreshPlanetView();
    }

    /**
     * Switches to the planetary system view, highlighting a specific {@link PlanetarySystem}.
     *
     * @param s The {@link PlanetarySystem} to select.
     */
    public void switchPlanetaryMap(PlanetarySystem s) {
        panMap.setSelectedSystem(s);
        panSystem.updatePlanetarySystem(s);
        mapView.setView(panSystem);
        refreshPlanetView();
    }

    /**
     * Switches to the interstellar map view, highlighting a specific {@link PlanetarySystem}.
     *
     * @param s The {@link PlanetarySystem} to select.
     */
    public void switchSystemsMap(PlanetarySystem s) {
        panMap.setSelectedSystem(s);
        panSystem.updatePlanetarySystem(s);
        switchSystemsMap();
    }

    public void switchSystemsMap() {
        mapView.setView(panMapView);
        refreshSystemView();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (Objects.equals(e.getSource(), panMap)) {
            refreshSystemView();
        } else if (Objects.equals(e.getSource(), panSystem)) {
            refreshPlanetView();
        }
    }

    @Subscribe
    public void handle(NewDayEvent ev) {
        panMap.requestStaticCartographyPreparation();
        panMap.repaint();
        updateRouteStrip();
        suggestPlanet.setSuggestData(getCampaign().getSystemNames());
    }

    @Subscribe
    public void handle(OptionsChangedEvent ev) {
        panMap.repaint();
        updateRouteStrip();
    }

    private enum RouteStripState {
        NO_ROUTE,
        PLANNED_ROUTE,
        IN_TRANSIT
    }

        private record RouteStripSnapshot(RouteStripState state, String statusText, Color statusColor,
            String destinationText, String jumpsText, String durationText, String nextJumpText, String costText,
            Color metricColor, boolean costVisible, boolean plotCourseVisible, boolean plotCourseEnabled,
            boolean beginTransitEnabled) {
    }

        private record DossierIdentity(String systemId, int planetPosition) {
        }

    private static final class RouteStripPanel extends JPanel {
        private float childAlpha = 1.0f;
        private float revealProgress;

        private RouteStripPanel() {
            super(new GridBagLayout());
        }

        private float getChildAlpha() {
            return childAlpha;
        }

        private void setChildAlpha(float childAlpha) {
            this.childAlpha = Math.max(0.0f, Math.min(1.0f, childAlpha));
            repaint();
        }

        private void setRevealProgress(float revealProgress) {
            this.revealProgress = Math.max(0.0f, Math.min(1.0f, revealProgress));
            repaint();
        }

        @Override
        protected void paintChildren(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            Composite originalComposite = graphics2D.getComposite();
            try {
                graphics2D.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, childAlpha));
                super.paintChildren(graphics2D);
            } finally {
                graphics2D.setComposite(originalComposite);
                graphics2D.dispose();
            }
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            super.paintBorder(graphics);
            if (revealProgress <= 0.0f) {
                return;
            }

            float sweepProgress = Math.min(1.0f, revealProgress / ROUTE_REVEAL_SWEEP_END);
            float revealAlpha = revealProgress < ROUTE_REVEAL_SWEEP_END
                  ? ROUTE_REVEAL_MAX_ALPHA
                  : ROUTE_REVEAL_MAX_ALPHA * (1.0f - revealProgress) / (1.0f - ROUTE_REVEAL_SWEEP_END);
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            Composite originalComposite = graphics2D.getComposite();
            try {
                graphics2D.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, revealAlpha));
                graphics2D.setColor(PLANNED_ROUTE_COLOR);
                graphics2D.drawLine(0, 0, Math.round((getWidth() - 1) * sweepProgress), 0);
            } finally {
                graphics2D.setComposite(originalComposite);
                graphics2D.dispose();
            }
        }
    }
}

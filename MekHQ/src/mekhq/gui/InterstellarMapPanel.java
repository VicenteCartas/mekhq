/*
 * Copyright (C) 2011-2026 The MegaMek Team. All Rights Reserved.
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

import static java.lang.Math.min;
import static mekhq.campaign.enums.DailyReportType.GENERAL;
import static mekhq.campaign.personnel.medical.advancedMedicalAlternate.CanonicalDiseaseType.getAllActiveBioweapons;
import static mekhq.campaign.personnel.medical.advancedMedicalAlternate.CanonicalDiseaseType.getAllActiveDiseases;

import java.awt.*;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.vecmath.Vector2d;

import megamek.client.ui.util.UIUtil;
import megamek.codeUtilities.ObjectUtility;
import megamek.common.annotations.Nullable;
import megamek.common.universe.FactionTag;
import megamek.logging.MMLogger;
import mekhq.MHQConstants;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.Mission;
import mekhq.campaign.mission.Scenario;
import mekhq.campaign.personnel.InjuryType;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Factions;
import mekhq.campaign.universe.HPGLink;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.SocioIndustrialData;
import mekhq.campaign.universe.StarType;
import mekhq.campaign.universe.Systems;
import mekhq.campaign.universe.enums.HPGRating;
import mekhq.campaign.universe.enums.HiringHallLevel;
import mekhq.campaign.universe.factionHints.FactionHints;
import mekhq.campaign.universe.factionStanding.FactionStandingUtilities;
import mekhq.campaign.universe.factionStanding.FactionStandings;
import mekhq.gui.dialog.PlanetarySystemEditorDialog;

/**
 * This is not functional yet. Just testing things out. A lot of this code is borrowed from InterstellarMap.java in
 * MekWars
 *
 * @author Jay Lawson (jaylawson39 at yahoo.com)
 */
public class InterstellarMapPanel extends JPanel {
    private static final MMLogger LOGGER = MMLogger.create(InterstellarMapPanel.class);

    interface RoutePlanningHandler {
        void plotRoute(PlanetarySystem destination);

        void appendWaypoint(PlanetarySystem destination);

        void trimRouteAt(PlanetarySystem destination);

        void removeWaypoint(PlanetarySystem waypoint);

        void clearPlannedRoute();

        boolean hasPlannedRoute();

        boolean canTrimRouteAt(PlanetarySystem system);

        boolean isRequestedWaypoint(PlanetarySystem system);
    }

    private static final RoutePlanningHandler NO_ROUTE_PLANNING_HANDLER = new RoutePlanningHandler() {
        @Override
        public void plotRoute(PlanetarySystem destination) {
        }

        @Override
        public void appendWaypoint(PlanetarySystem destination) {
        }

        @Override
        public void trimRouteAt(PlanetarySystem destination) {
        }

        @Override
        public void removeWaypoint(PlanetarySystem waypoint) {
        }

        @Override
        public void clearPlannedRoute() {
        }

        @Override
        public boolean hasPlannedRoute() {
            return false;
        }

        @Override
        public boolean canTrimRouteAt(PlanetarySystem system) {
            return false;
        }

        @Override
        public boolean isRequestedWaypoint(PlanetarySystem system) {
            return false;
        }
    };

    private static final String CURRENT_LOCATION_ICON_PATH =
          "data/images/universe/default_jumpship_fleet.png";
        private static final int CURRENT_LOCATION_ICON_SIZE = 34;
    private static final Color MAP_BACKGROUND_TOP = new Color(7, 16, 27);
    private static final Color MAP_BACKGROUND_BOTTOM = new Color(3, 8, 15);
    private static final Color MAP_GRID_MINOR = new Color(35, 66, 82, 45);
    private static final Color MAP_GRID_MAJOR = new Color(50, 91, 108, 75);
    private static final Color PLANNED_ROUTE_COLOR = new Color(65, 210, 224);
    private static final Color ACTIVE_ROUTE_COLOR = new Color(235, 166, 66);
    private static final Color ACTIVE_ROUTE_FLOW_COLOR = new Color(255, 226, 154);
    private static final Color CURRENT_LOCATION_COLOR = new Color(255, 190, 82);
    private static final Color SELECTED_SYSTEM_COLOR = new Color(120, 225, 235);
    private static final Color HOVERED_SYSTEM_COLOR = new Color(190, 215, 222);
    private static final Color SYSTEM_LABEL_COLOR = new Color(218, 231, 235);
    private static final Color OPERATION_MARKER_COLOR = new Color(239, 170, 54);
    private static final Color URGENT_OPERATION_COLOR = new Color(255, 220, 122);
    private static final Color HPG_A_TRAFFIC_COLOR = new Color(145, 240, 255);
    private static final Color HPG_B_TRAFFIC_COLOR = new Color(105, 175, 255);
    private static final int LAYER_ANIMATION_DELAY_MS = 16;
    private static final long LAYER_ANIMATION_DURATION_NS = 260_000_000L;
    private static final long MAP_MODE_ANIMATION_DURATION_NS = 300_000_000L;
    private static final int OPTION_PANEL_COLLAPSED_SIZE = 30;
    private static final int SELECTION_ANIMATION_DELAY_MS = 16;
    private static final long SELECTION_ANIMATION_DURATION_NS = 260_000_000L;
    private static final int PROPOSED_ROUTE_ANIMATION_DELAY_MS = 16;
    private static final long PROPOSED_ROUTE_BASE_LEG_DURATION_NS = 400_000_000L;
    private static final long PROPOSED_ROUTE_MIN_LEG_DURATION_NS = 90_000_000L;
    private static final int AMBIENT_ANIMATION_DELAY_MS = 80;
    private static final int TRAVEL_ANIMATION_DELAY_MS = 16;
    private static final long ROUTE_ACTIVATION_DURATION_NS = 550_000_000L;
    private static final long SYSTEM_HOP_DURATION_NS = 520_000_000L;
    private static final double SYSTEM_HOP_DEPARTURE_END_PROGRESS = 0.34;
    private static final double SYSTEM_HOP_ARRIVAL_START_PROGRESS = 0.64;
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000.0;
    private static final double FULL_CIRCLE_RADIANS = Math.PI * 2.0;
    private static final Color LAYER_CONTROL_BACKGROUND = new Color(5, 13, 23, 230);
    private static final Color LAYER_CONTROL_BORDER = new Color(65, 210, 224, 105);
    private static final Color LAYER_CONTROL_TEXT = new Color(198, 214, 220);
    private static final Color LAYER_CONTROL_BUTTON_HOVER = new Color(18, 45, 56, 245);
    private static final Color LAYER_CONTROL_BUTTON_PRESSED = new Color(33, 73, 82, 255);
    private static final Color LAYER_CONTROL_BUTTON_ICON = new Color(125, 230, 238);
    private static final Color LAYER_CONTROL_BUTTON_FOCUS = new Color(190, 245, 250, 210);
    private static final String LAYER_CONTROL_HEADING = "MAP LAYER";
    private static final String OVERLAY_CONTROL_HEADING = "OVERLAYS";
    private static final int MAP_LEGEND_CONTENT_WIDTH = 560;
    private static final int MAP_LEGEND_DIALOG_MARGIN = 16;
    private static final int MAP_LEGEND_BUTTON_SIZE = 24;
    private static final int MAP_LEGEND_SWATCH_WIDTH = 64;
    private static final int MAP_LEGEND_SWATCH_HEIGHT = 38;
    private static final Color MAP_LEGEND_BACKGROUND = new Color(5, 13, 23);
    private static final Color MAP_LEGEND_MUTED_TEXT = new Color(158, 179, 187);
    private static final Color MAP_LEGEND_DIVIDER = new Color(65, 210, 224, 45);
    private static final Color NAVIGATION_INSTRUMENT_SHADOW = new Color(3, 8, 15, 215);
    private static final int NAVIGATION_INSTRUMENT_MARGIN = 14;
    private static final int NAVIGATION_INSTRUMENT_WIDTH = 280;
    private static final int NAVIGATION_INSTRUMENT_HEIGHT = 118;
    private static final int NAVIGATION_COMPASS_CENTER_X = 104;
    private static final int NAVIGATION_COMPASS_CENTER_Y = 36;
    private static final int NAVIGATION_COMPASS_RADIUS = 15;
    private static final int NAVIGATION_SCALE_BAR_INSET = 4;
    private static final int NAVIGATION_SCALE_BAR_WIDTH = 100;
    private static final int NAVIGATION_SCALE_BAR_Y = 96;
    private static final MathContext NAVIGATION_DISTANCE_FORMAT = new MathContext(3, RoundingMode.HALF_UP);
        private static final Stroke CONFIGURABLE_RANGE_RING_STROKE = new BasicStroke(3.0f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 3 }, 0);
        private static final Stroke HPG_RANGE_RING_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 2, 5 }, 0);
        private static final Color HPG_RANGE_RING_COLOR = new Color(0, 100, 50);
    private static final int GRID_TARGET_SPACING = 96;
        private static final long MAX_CACHED_RENDER_LAYER_PIXELS = 16_777_216L;
    private static final double TERRITORY_HEX_SIZE = 30.0;
    private static final double TERRITORY_HEX_SPACING_X = TERRITORY_HEX_SIZE * Math.sqrt(3) / 2.0;
    private static final double TERRITORY_HEX_RADIUS = TERRITORY_HEX_SIZE / Math.sqrt(3);
    private static final int TERRITORY_HEX_MARGIN = 2;
        private static final Stroke TERRITORY_CONTOUR_SOFTENING_STROKE = new BasicStroke(5.0f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Color TERRITORY_BORDER_DARK = new Color(2, 6, 10, 215);
    private static final Color TERRITORY_NEUTRAL_EDGE = new Color(198, 211, 214, 185);
    private static final Color TERRITORY_POCKET_FILL = new Color(1, 5, 9, 105);
    private static final double FACTION_LOGO_OPACITY = 0.34;
    private static final int FACTION_LOGO_MIN_SIZE = 36;
    private static final int FACTION_LOGO_COMPACT_MIN_SIZE = 24;
    private static final int FACTION_LOGO_MAX_SIZE = 100;
    private static final int FACTION_LOGO_SIZE_STEP = 4;
    private static final int FACTION_LOGO_COLLISION_PADDING = 8;
    private static final BufferedImage CURRENT_LOCATION_ICON = loadCurrentLocationIcon();

    private enum MapMode {
        FACTION,
        TECHNOLOGY,
        INDUSTRY,
        RAW_MATERIALS,
        OUTPUT,
        AGRICULTURE,
        POPULATION,
        HPG,
        RECHARGE_STATIONS,
        ACADEMIES,
        HIRING_HALLS,
        DISEASE_OUTBREAKS
    }

        private enum MapLegendSymbol {
          SYSTEM_CONTACT,
          FACTION_OWNERSHIP,
          ANALYTICAL_VALUE,
          FACTION_EMBLEM,
          SELECTED_SYSTEM,
          HOVERED_SYSTEM,
          CURRENT_FLEET,
          PLANNED_ROUTE,
          ACTIVE_ROUTE,
          WAYPOINT_BADGE,
          CONTRACT_SEARCH_RADIUS,
          PLANETARY_ACQUISITION_RADIUS,
          JUMP_RADIUS,
          HPG_RANGE,
          FACTION_CAPITAL,
          GREAT_HIRING_HALL,
          OPERATION,
          RESTRICTED_SYSTEM,
          GM_EDITED_SYSTEM,
          HPG_NETWORK,
          SOVEREIGN_TERRITORY,
          DISPUTED_TERRITORY,
          UNCLAIMED_POCKET,
          ENCLAVE
        }

        private record MapLegendEntry(MapLegendSymbol symbol, String title, String meaning) {
        }

        private record MapLegendSection(String heading, List<MapLegendEntry> entries) {
        }

        private static final List<MapLegendSection> MAP_LEGEND_SECTIONS = List.of(
            new MapLegendSection("NAVIGATION", List.of(
                new MapLegendEntry(MapLegendSymbol.SELECTED_SYSTEM, "Selected system",
                    "Cyan corner brackets identify the selected system."),
                new MapLegendEntry(MapLegendSymbol.HOVERED_SYSTEM, "Hovered system",
                    "Pale corner brackets identify the system under the pointer."),
                new MapLegendEntry(MapLegendSymbol.CURRENT_FLEET, "Current fleet",
                    "An amber JumpShip marks the fleet; an amber ring is its low-zoom beacon."),
                new MapLegendEntry(MapLegendSymbol.PLANNED_ROUTE, "Planned route",
                    "A cyan dashed path and thin rings show the proposed jump route."),
                new MapLegendEntry(MapLegendSymbol.ACTIVE_ROUTE, "Active route",
                    "Amber paths and rings show the current trip; pale pulses show travel flow."),
                new MapLegendEntry(MapLegendSymbol.WAYPOINT_BADGE, "Waypoint number",
                    "Numbered badges give each route stop's order."))),
            new MapLegendSection("RANGE RINGS", List.of(
                new MapLegendEntry(MapLegendSymbol.CONTRACT_SEARCH_RADIUS, "Contract-search radius",
                    "A configurable-color thick dashed ring centered on the selected system bounds contract searches; campaign and MekHQ options control visibility."),
                new MapLegendEntry(MapLegendSymbol.PLANETARY_ACQUISITION_RADIUS, "Planetary-acquisition radius",
                    "A configurable-color thick dashed ring centered on the selected system bounds planetary acquisition; campaign, MekHQ, and zoom options control visibility."),
                new MapLegendEntry(MapLegendSymbol.JUMP_RADIUS, "Jump radius",
                    "A configurable-color thick dashed ring centered on the selected system shows one-jump reach; MekHQ and zoom options control visibility."),
                new MapLegendEntry(MapLegendSymbol.HPG_RANGE, "50 ly HPG range",
                    "A dark-green dotted ring centered on the selected system marks 50 ly; HPG layer visibility controls when it appears."))),
            new MapLegendSection("SYSTEM STATUS", List.of(
                new MapLegendEntry(MapLegendSymbol.SYSTEM_CONTACT, "System contact",
                    "Star color shows spectral class; at low zoom, contact color reflects faction or map data, with grey for empty or unowned systems."),
                new MapLegendEntry(MapLegendSymbol.FACTION_OWNERSHIP, "Faction ownership",
                    "Colored arcs identify each faction present; a neutral dashed collar marks a shared system."),
                new MapLegendEntry(MapLegendSymbol.FACTION_CAPITAL, "National capital",
                    "A faction-color crown with a dark outline marks every dated national capital at atlas zoom."),
                new MapLegendEntry(MapLegendSymbol.GREAT_HIRING_HALL, "Great Hiring Hall",
                    "A pale triangle marks a Great Hiring Hall."),
                new MapLegendEntry(MapLegendSymbol.OPERATION, "Operation flag",
                    "An outline flag marks an active mission; a filled flag marks an active scenario; the count is active missions."),
                new MapLegendEntry(MapLegendSymbol.RESTRICTED_SYSTEM, "Restricted system",
                    "A black X marks a system barred by outlaw or restricted-entry standing rules."),
                new MapLegendEntry(MapLegendSymbol.GM_EDITED_SYSTEM, "GM-edited system",
                    "A cyan outline ring marks a non-canon system override."))),
            new MapLegendSection("MAP DATA", List.of(
                new MapLegendEntry(MapLegendSymbol.ANALYTICAL_VALUE, "Analytical / service value",
                    "Bracket color reflects the active map layer's value or service status."),
                new MapLegendEntry(MapLegendSymbol.FACTION_EMBLEM, "Faction emblem",
                    "A faint emblem watermark identifies territory; its tint identifies the faction."),
                new MapLegendEntry(MapLegendSymbol.HPG_NETWORK, "HPG network & traffic",
                    "Rings show rating: A cyan, B blue, C orange, D red; links and pulses show A/B traffic."),
                new MapLegendEntry(MapLegendSymbol.SOVEREIGN_TERRITORY, "Sovereign border",
                    "Translucent faction fill and a solid edge mark territory inferred from dated ownership."),
                new MapLegendEntry(MapLegendSymbol.DISPUTED_TERRITORY, "Disputed territory",
                    "Multiple faction colors, diagonal hatching, and a dashed border mark shared control."),
                new MapLegendEntry(MapLegendSymbol.UNCLAIMED_POCKET, "Unclaimed pocket",
                    "A dark enclosed void with a dotted boundary marks locally unclaimed space."),
                new MapLegendEntry(MapLegendSymbol.ENCLAVE, "Enclave",
                    "A closed double border marks one faction's territory enclosed by another."))));

        private static final class MapLegendSwatch extends JComponent {
          private final MapLegendSymbol symbol;

          private MapLegendSwatch(MapLegendEntry entry) {
            symbol = entry.symbol();
            Dimension swatchSize = UIUtil.scaleForGUI(MAP_LEGEND_SWATCH_WIDTH, MAP_LEGEND_SWATCH_HEIGHT);
            setPreferredSize(swatchSize);
            setMinimumSize(swatchSize);
            setMaximumSize(swatchSize);
            setFocusable(false);
            putClientProperty("mapLegendSwatch", Boolean.TRUE);
            putClientProperty("mapLegendTitle", entry.title());
          }

          @Override
          protected void paintComponent(Graphics graphics) {
            Graphics2D swatchGraphics = (Graphics2D) graphics.create();
            try {
                swatchGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                swatchGraphics.scale(getWidth() / (double) MAP_LEGEND_SWATCH_WIDTH,
                    getHeight() / (double) MAP_LEGEND_SWATCH_HEIGHT);
                swatchGraphics.setPaint(new GradientPaint(0, 0, MAP_BACKGROUND_TOP,
                    0, MAP_LEGEND_SWATCH_HEIGHT, MAP_BACKGROUND_BOTTOM));
                swatchGraphics.fillRect(0, 0, MAP_LEGEND_SWATCH_WIDTH, MAP_LEGEND_SWATCH_HEIGHT);
                paintMapLegendSymbol(swatchGraphics, symbol);
                swatchGraphics.setPaint(LAYER_CONTROL_BORDER);
                swatchGraphics.setStroke(new BasicStroke(0.8f));
                swatchGraphics.drawRect(0, 0, MAP_LEGEND_SWATCH_WIDTH - 1, MAP_LEGEND_SWATCH_HEIGHT - 1);
            } finally {
                swatchGraphics.dispose();
            }
          }
        }

    enum RouteMarkerState {
        NONE,
        PLANNED,
        ACTIVE
    }

    record TerritoryHex(int column, int row) {
    }

    enum TerritorySemantic {
        SOVEREIGN,
        DISPUTED,
        UNCLAIMED_EXTERIOR,
        UNCLAIMED_POCKET,
        ENCLAVE
    }

    record TerritoryCell(TerritoryHex hex, double centerX, double centerY, List<Faction> factions) {
    }

        record TerritoryComponent(Faction faction, TerritoryHex anchorHex, double anchorX, double anchorY,
            int cellCount, int interiorDepth, double minMapX, double maxMapX, double minMapY, double maxMapY) {
        }

        record TerritoryContour(List<Faction> factions, TerritorySemantic semantic, Shape shape, Paint paint,
            int cellCount, double minMapX, double maxMapX, double minMapY, double maxMapY) {
    }

    record TerritoryAtlas(LocalDate date, int minColumn, int maxColumn, int minRow, int maxRow,
            Map<TerritoryHex, TerritoryCell> cells, List<TerritoryContour> contours,
            List<TerritoryComponent> components) {
    }

    record RenderViewKey(int width, int height, long centerXBits, long centerYBits, long scaleBits) {
        static RenderViewKey create(int width, int height, double centerX, double centerY, double scale) {
            return new RenderViewKey(width, height, Double.doubleToLongBits(centerX),
                  Double.doubleToLongBits(centerY), Double.doubleToLongBits(scale));
        }
    }

    record MapCenter(double x, double y) {
    }

    record TerritoryRenderKey(RenderViewKey viewKey, LocalDate date, long dataRevision) {
    }

    record FactionLogoRenderKey(TerritoryRenderKey territoryKey, int majorMinimumSize,
          int compactMinimumSize, int maximumSize, int collisionPadding, int shadowOffset) {
    }

    record TerritoryDataKey(LocalDate date, long dataRevision) {
    }

        record RenderCacheDiagnostics(int backgroundRenderCount, int territoryRenderCount,
            int factionLogoRenderCount, int territoryPreparationCount, boolean backgroundImageRetained,
            boolean territoryImageRetained, boolean factionLogoImageRetained) {
        }

    static final class RenderLayerCache<K> {
        private K key;
        private BufferedImage image;
        private int renderCount;

        BufferedImage getOrRender(K requestedKey, int width, int height, Consumer<Graphics2D> renderer) {
            boolean hasMatchingDimensions = (image != null) && (image.getWidth() == width)
                  && (image.getHeight() == height);
            if (hasMatchingDimensions && requestedKey.equals(key)) {
                return image;
            }

            BufferedImage renderedImage;
            if (hasMatchingDimensions) {
                renderedImage = image;
            } else {
                clear();
                renderedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            }
            Graphics2D graphics = renderedImage.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Clear);
                graphics.fillRect(0, 0, width, height);
                graphics.setComposite(AlphaComposite.SrcOver);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                renderer.accept(graphics);
            } catch (RuntimeException exception) {
                renderedImage.flush();
                key = null;
                image = null;
                throw exception;
            } finally {
                graphics.dispose();
            }
            key = requestedKey;
            image = renderedImage;
            renderCount++;
            return renderedImage;
        }

        void clear() {
            if (image != null) {
                image.flush();
            }
            key = null;
            image = null;
        }

        int getRenderCount() {
            return renderCount;
        }

        boolean hasImage() {
            return image != null;
        }
    }

    static final class PreparedRenderData<K, V> {
        private K key;
        private V value;
        private int preparationCount;

        V prepare(K requestedKey, Supplier<V> preparer) {
            if ((value != null) && requestedKey.equals(key)) {
                return value;
            }
            V preparedValue = preparer.get();
            key = requestedKey;
            value = preparedValue;
            preparationCount++;
            return preparedValue;
        }

        @Nullable V get(K requestedKey) {
            return ((value != null) && requestedKey.equals(key)) ? value : null;
        }

        void clear() {
            key = null;
            value = null;
        }

        int getPreparationCount() {
            return preparationCount;
        }

        @Nullable K getKey() {
            return key;
        }
    }

    static void drawRenderLayer(Graphics2D graphics, BufferedImage image, double alpha) {
        paintLayerWithAlpha(graphics, alpha, layerGraphics -> layerGraphics.drawImage(image, 0, 0, null));
    }

        private record FactionLogoKey(int gameYear, String factionCode) {
        }

        private record ScaledFactionLogoKey(FactionLogoKey logoKey, int width, int height) {
        }

        private record FactionLogoImage(BufferedImage tinted, BufferedImage shadow) {
        }

        private record FactionLogoCandidate(TerritoryComponent component, int priority, double projectedArea,
            Rectangle2D.Double bounds, FactionLogoImage image) {
        }

        static final class StaticCartographyPreparationQueue<K> {
            private final BooleanSupplier isShowing;
            private final Consumer<Runnable> eventLoopScheduler;
            private final Consumer<K> preparer;
            private K latestRequest;
            private boolean dispatchPending;

            StaticCartographyPreparationQueue(BooleanSupplier isShowing, Consumer<Runnable> eventLoopScheduler,
                  Consumer<K> preparer) {
                this.isShowing = isShowing;
                this.eventLoopScheduler = eventLoopScheduler;
                this.preparer = preparer;
            }

            void request(K requestedKey) {
                if (!isShowing.getAsBoolean()) {
                    return;
                }
                latestRequest = requestedKey;
                if (dispatchPending) {
                    return;
                }
                dispatchPending = true;
                eventLoopScheduler.accept(this::dispatch);
            }

            void cancel() {
                latestRequest = null;
            }

            private void dispatch() {
                dispatchPending = false;
                K requestedKey = latestRequest;
                latestRequest = null;
                if ((requestedKey != null) && isShowing.getAsBoolean()) {
                    preparer.accept(requestedKey);
                }
            }
        }

                record SemanticZoomProfile(double territoryAlpha, double factionLogoAlpha,
            double strategicContactAlpha,
          double spectralAlpha, double detailedOverlayAlpha, double ordinaryLabelAlpha,
          double routeLabelAlpha, double routeBadgeAlpha, double hpgNetworkAlpha,
                    double hpgTrafficAlpha, double capitalAlpha, double missionOperationAlpha,
                    double urgentOperationAlpha,
          double currentLocationAlpha, double serviceAlpha, double stellarDetailAlpha) {
        static SemanticZoomProfile create(double scale, double semanticReference) {
            double boundedReference = Math.clamp(semanticReference, 2.4, 3.6);
            double atlasEnd = Math.clamp(boundedReference / 3.0, 0.8, 1.0);
            double detailStart = Math.clamp(boundedReference * 0.8, 2.4, 3.0);
            double fullDetail = Math.clamp(boundedReference * (4.0 / 3.0), 3.6, 4.2);
            double navigationAlpha = fadeBetween(scale, atlasEnd, detailStart);
            double detailAlpha = fadeBetween(scale, detailStart, fullDetail);
            double territoryAlpha = interpolate(0.6, 1.0, navigationAlpha)
                * interpolate(1.0, 0.45, detailAlpha);
            double spectralAlpha = fadeBetween(scale, 0.8, Math.clamp(boundedReference, 2.8, 3.2));
            double ordinaryLabelAlpha = fadeBetween(scale,
                Math.clamp(boundedReference * 0.45, 1.2, 1.6),
                Math.clamp(boundedReference, 2.8, 3.2));

            return new SemanticZoomProfile(
                territoryAlpha,
                1.0 - navigationAlpha,
                  1.0 - spectralAlpha,
                  spectralAlpha,
                  navigationAlpha,
                  ordinaryLabelAlpha,
                  navigationAlpha,
                  navigationAlpha,
                  interpolate(0.65, 1.0, navigationAlpha),
                  navigationAlpha,
                  interpolate(0.75, 1.0, navigationAlpha),
                  navigationAlpha,
                  interpolate(0.68, 1.0, navigationAlpha),
                  navigationAlpha,
                  detailAlpha,
                  detailAlpha);
        }
    }

    record TerritoryVisualProfile(double secondaryDetailAlpha, double sharedSystemAlpha) {
        static TerritoryVisualProfile create(double scale) {
            return new TerritoryVisualProfile(fadeBetween(scale, 1.8, 3.2),
                  fadeBetween(scale, 2.2, 3.6));
        }
    }

    record SystemMarkerLayout(double centerX, double centerY, double size,
          RouteMarkerState routeState, boolean selected, boolean hovered, double ownershipRadius,
          double navigationRadius, double selectedRadius, double hoveredRadius, double externalOrbitRadius) {
        static SystemMarkerLayout create(double centerX, double centerY, double size,
              RouteMarkerState routeState, boolean selected, boolean hovered) {
            double ownershipRadius = size + 2.8;
            double selectedRadius = Math.ceil(Math.max(size * 1.5 + 2.0, ownershipRadius + 3.0));
            double hoveredRadius = Math.ceil(Math.max(size * 1.5 + 6.0, selectedRadius + 4.0));
            double plannedNavigationRadius = Math.max(Math.max(size * 1.55, ownershipRadius + 5.0),
                  selectedRadius + 1.8);
            double activeNavigationRadius = Math.max(Math.max(size * 1.8, plannedNavigationRadius + 2.4),
                hoveredRadius + 2.5);
            double navigationRadius = switch (routeState) {
                case NONE -> 0.0;
                case PLANNED -> plannedNavigationRadius;
                case ACTIVE -> activeNavigationRadius;
            };
            double externalOrbitRadius = Math.max(activeNavigationRadius, hoveredRadius * Math.sqrt(2.0));
            return new SystemMarkerLayout(centerX, centerY, size, routeState, selected, hovered,
                  ownershipRadius, navigationRadius, selectedRadius, hoveredRadius, externalOrbitRadius);
        }

        Point2D.Double operationAnchor() {
            double radialOffset = externalOrbitRadius + Math.max(14.0, size * 1.25)
                  + externalOrbitGap(4.0, 5.0);
            return diagonalAnchor(-1.0, -1.0, radialOffset);
        }

        double overrideRadius() {
            return ownershipRadius + 2.5;
        }

        Point2D.Double capitalAnchor(int markerIndex, int markerCount) {
            Rectangle2D.Double markerBounds = nationalCapitalMarkerBounds(new Point2D.Double(), size);
            double markerSpacing = markerBounds.width + 2.0;
            double horizontalOffset = (markerIndex - ((markerCount - 1) / 2.0)) * markerSpacing;
            double rightmostX = centerX + (((markerCount - 1) / 2.0) * markerSpacing);
            double shipLeft = shipAnchor().x - (CURRENT_LOCATION_ICON_SIZE / 2.0);
            double bandShift = Math.min(0.0,
                shipLeft - 2.0 - (rightmostX + (markerBounds.width / 2.0)));
            return new Point2D.Double(centerX + horizontalOffset + bandShift,
                centerY - hoveredRadius - (markerBounds.height / 2.0) - 4.0);
        }

        Point2D.Double serviceAnchor() {
            double markerClearanceRadius = Math.max(6.0, size * 0.7);
            double radialOffset = externalOrbitRadius + markerClearanceRadius
                  + externalOrbitGap(3.0, 4.0);
            return diagonalAnchor(-1.0, 1.0, radialOffset);
        }

        Point2D.Double routeBadgeAnchor(double badgeDiameter) {
            double radialOffset = externalOrbitRadius + (badgeDiameter / 2.0)
                  + externalOrbitGap(3.0, 4.0);
            return diagonalAnchor(1.0, 1.0, radialOffset);
        }

        Point2D.Double shipAnchor() {
            double iconClearance = CURRENT_LOCATION_ICON_SIZE / 2.0;
            double radialOffset = externalOrbitRadius + iconClearance + externalOrbitGap(3.0, 5.0);
            Point2D.Double anchor = diagonalAnchor(1.0, -1.0, radialOffset);
            if (CURRENT_LOCATION_ICON == null) {
                return anchor;
            }
            double halfIconSize = CURRENT_LOCATION_ICON_SIZE / 2.0;
            return new Point2D.Double(Math.round(anchor.x - halfIconSize) + halfIconSize,
                  Math.round(anchor.y - halfIconSize) + halfIconSize);
        }

        double labelX() {
            return centerX + Math.max(size * 1.8, hoveredRadius + 3.0);
        }

        private double externalOrbitGap(double compactGap, double expandedGap) {
            double expansion = Math.clamp((size - 3.0) / 8.0, 0.0, 1.0);
            return interpolate(compactGap, expandedGap, expansion);
        }

        private Point2D.Double diagonalAnchor(double horizontalDirection, double verticalDirection,
              double radialOffset) {
            double componentOffset = radialOffset / Math.sqrt(2.0);
            return new Point2D.Double(centerX + (horizontalDirection * componentOffset),
                  centerY + (verticalDirection * componentOffset));
        }
    }

        private SystemMarkerLayout createSystemMarkerLayout(PlanetarySystem system, double size,
                    RouteMarkerState routeState) {
                return createSystemMarkerLayout(system, size, routeState, null);
        }

        private SystemMarkerLayout createSystemMarkerLayout(PlanetarySystem system, double size,
                    RouteMarkerState routeState, @Nullable PlanetarySystem hoveredSystem) {
                boolean selected = isSameSystem(system, selectedSystem);
                boolean hovered = isSelectionAnimationTarget(system)
                            || (!selected && isSameSystem(system, hoveredSystem));
                return SystemMarkerLayout.create(map2scrX(system.getX()), map2scrY(system.getY()), size,
                            routeState, selected, hovered);
        }

    private static final Vector2d[] BASE_HEX_COORDS = {
          new Vector2d(1.0, 0.0),
          new Vector2d(Math.cos(Math.PI / 3.0), Math.sin(Math.PI / 3.0)),
          new Vector2d(Math.cos(2.0 * Math.PI / 3.0), Math.sin(2.0 * Math.PI / 3.0)),
          new Vector2d(-1.0, 0.0),
          new Vector2d(Math.cos(4.0 * Math.PI / 3.0), Math.sin(4.0 * Math.PI / 3.0)),
          new Vector2d(Math.cos(5.0 * Math.PI / 3.0), Math.sin(5.0 * Math.PI / 3.0))
    };

    private final JLayeredPane pane;
    private final JPanel mapPanel;
    private final JPanel optionControl;
    private final JViewport optionView;
    private final JPanel optionPanel;
    private final JButton optionButton;
    private final JButton mapLegendButton;
    private final ResourceBundle resourceMap;

    // Map view options
    private final JRadioButton optFactions;
    private final JRadioButton optTech;
    private final JRadioButton optIndustry;
    private final JRadioButton optRawMaterials;
    private final JRadioButton optOutput;
    private final JRadioButton optAgriculture;
    private final JRadioButton optPopulation;
    private final JRadioButton optHPG;
    private final JRadioButton optRecharge;
    private final JRadioButton optAcademies;
    private final JRadioButton optHiringHalls;
    private final JRadioButton optDiseases;

    private final JCheckBox optEmptySystems;
    private final JCheckBox optHPGNetwork;
    private final JCheckBox optTerritory;
    private final JCheckBox optOperations;

    private final Timer layerAnimationTimer;
    private final Timer selectionAnimationTimer;
    private final Timer proposedRouteAnimationTimer;
    private final Timer ambientAnimationTimer;
    private final Timer travelAnimationTimer;
    private final long ambientAnimationEpochNanos;
    private boolean optionPanelHidden;
    private JDialog mapLegendDialog;
    private boolean optionPanelAnimating;
    private long optionPanelAnimationStartTime;
    private long optionPanelAnimationDuration;
    private double optionPanelAnimationStartExpansion = 1.0;
    private double optionPanelAnimationTargetExpansion = 1.0;
    private double optionPanelExpansion = 1.0;
    private boolean territoryLayerAnimating;
    private long territoryLayerAnimationStartTime;
    private long territoryLayerAnimationDuration;
    private double territoryLayerAnimationStartAlpha = 1.0;
    private double territoryLayerAnimationTargetAlpha = 1.0;
    private double territoryLayerAlpha = 1.0;
    private boolean hpgNetworkLayerAnimating;
    private long hpgNetworkLayerAnimationStartTime;
    private long hpgNetworkLayerAnimationDuration;
    private double hpgNetworkLayerAnimationStartAlpha;
    private double hpgNetworkLayerAnimationTargetAlpha;
    private double hpgNetworkLayerAlpha;
    private boolean operationsLayerAnimating;
    private long operationsLayerAnimationStartTime;
    private long operationsLayerAnimationDuration;
    private double operationsLayerAnimationStartAlpha = 1.0;
    private double operationsLayerAnimationTargetAlpha = 1.0;
    private double operationsLayerAlpha = 1.0;
    private boolean mapModeAnimating;
    private long mapModeAnimationStartTime;
    private long mapModeAnimationDuration;
    private double mapModeAnimationStartProgress = 1.0;
    private double mapModeAnimationProgress = 1.0;
    private MapMode previousMapMode = MapMode.FACTION;
    private MapMode targetMapMode = MapMode.FACTION;

    private ArrayList<PlanetarySystem> systems;

    private JumpPath jumpPath;
    private Campaign campaign;
    private final InnerStellarMapConfig conf = new InnerStellarMapConfig();
    private final CampaignGUI hqView;
    private PlanetarySystem selectedSystem = null;
    private String selectionAnimationSystemId = null;
    private long selectionAnimationStartTime;
    private double selectionAnimationProgress = 1.0;
    private long proposedRouteAnimationStartTime;
    private long proposedRouteAnimationDuration;
    private double proposedRouteAnimationProgress = 1.0;
    private boolean travelVisualStateInitialized;
    private String observedCurrentSystemId;
    private List<String> observedActiveRouteSystemIds = List.of();
    private boolean observedActiveRouteExists;
    private List<String> cachedProposedRouteSystemIds = List.of();
    private List<String> activatingRouteSystemIds = List.of();
    private long routeActivationStartTime;
    private double routeActivationProgress = 1.0;
    private String systemHopOriginId;
    private String systemHopDestinationId;
    private long systemHopStartTime;
    private double systemHopProgress = 1.0;
    private Point lastMousePos = null;
    private int mouseMod = 0;
    private RoutePlanningHandler routePlanningHandler = NO_ROUTE_PLANNING_HANDLER;

    private transient double minX;
    private transient double minY;
    private transient double maxX;
    private transient double maxY;
    private transient LocalDate now;
        private final PreparedRenderData<TerritoryDataKey, TerritoryAtlas> preparedTerritoryAtlas =
            new PreparedRenderData<>();
        private final RenderLayerCache<RenderViewKey> backgroundRenderCache = new RenderLayerCache<>();
        private final RenderLayerCache<TerritoryRenderKey> territoryRenderCache = new RenderLayerCache<>();
        private final RenderLayerCache<FactionLogoRenderKey> factionLogoRenderCache = new RenderLayerCache<>();
        private final StaticCartographyPreparationQueue<TerritoryDataKey> territoryPreparationQueue =
            new StaticCartographyPreparationQueue<>(this::isShowing, SwingUtilities::invokeLater,
                  this::prepareRequestedStaticCartography);
        private long cartographyDataRevision;
    private final Map<FactionLogoKey, FactionLogoImage> factionLogoImages = new HashMap<>();
    private final Map<ScaledFactionLogoKey, FactionLogoImage> scaledFactionLogoImages = new HashMap<>();
    private final Set<FactionLogoKey> missingFactionLogoImages = new HashSet<>();

    public InterstellarMapPanel(Campaign campaign, CampaignGUI view) {
        this.campaign = campaign;
        resourceMap = ResourceBundle.getBundle("mekhq.resources.CampaignGUI",
              MekHQ.getMHQOptions().getLocale());
        systems = this.campaign.getSystems();
        hqView = view;
        jumpPath = new JumpPath();
        optionPanelHidden = false;
                ambientAnimationEpochNanos = System.nanoTime();
        layerAnimationTimer = new Timer(LAYER_ANIMATION_DELAY_MS, e -> updateLayerAnimations());
        layerAnimationTimer.setCoalesce(true);
        selectionAnimationTimer = new Timer(SELECTION_ANIMATION_DELAY_MS, e -> updateSelectionAnimation());
        selectionAnimationTimer.setCoalesce(true);
                proposedRouteAnimationTimer = new Timer(PROPOSED_ROUTE_ANIMATION_DELAY_MS,
              e -> updateProposedRouteAnimation());
                proposedRouteAnimationTimer.setCoalesce(true);
                ambientAnimationTimer = new Timer(AMBIENT_ANIMATION_DELAY_MS, e -> updateAmbientAnimation());
                ambientAnimationTimer.setCoalesce(true);
            travelAnimationTimer = new Timer(TRAVEL_ANIMATION_DELAY_MS, e -> updateTravelAnimations());
            travelAnimationTimer.setCoalesce(true);

        setBorder(BorderFactory.createLineBorder(Color.black));

        addKeyListener(new KeyAdapter() {
            /** Handle the key pressed event from the text field. */
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                boolean moved = false;
                if (keyCode == KeyEvent.VK_LEFT) {
                    conf.centerY -= 1.0;
                    moved = true;
                }
                if (keyCode == KeyEvent.VK_RIGHT) {
                    conf.centerY += 1.0;
                    moved = true;
                }
                if (keyCode == KeyEvent.VK_DOWN) {
                    conf.centerX += 1.0;
                    moved = true;
                }
                if (keyCode == KeyEvent.VK_UP) {
                    conf.centerX -= 1.0;
                    moved = true;
                }
                if (moved) {
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                lastMousePos = new Point(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                lastMousePos = null;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
                mouseMod = 0;
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
                mouseMod = e.getButton();
            }

            public void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    final Point popupAnchor = new Point(e.getPoint());
                    final PlanetarySystem popupSystem = findSystemAt(popupAnchor);
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem item = new JMenuItem("Zoom In");
                    item.addActionListener(ae -> zoom(1.5, popupAnchor));
                    popup.add(item);
                    item = new JMenuItem("Zoom Out");
                    item.addActionListener(ae -> zoom(0.5, popupAnchor));
                    popup.add(item);
                    JMenu centerM = new JMenu("Center Map");
                    item = new JMenuItem("On Selected Planet");
                    item.setEnabled(selectedSystem != null);
                    if (selectedSystem != null) {
                        // only add if there is a planet to center on
                        item.addActionListener(ae -> center(selectedSystem));
                    }
                    centerM.add(item);
                    item = new JMenuItem("On Current Location");
                    item.setEnabled(InterstellarMapPanel.this.campaign.getCurrentSystem() != null);
                    if (InterstellarMapPanel.this.campaign.getCurrentSystem() != null) {
                        // only add if there is a planet to center on
                        item.addActionListener(evt -> {
                            selectSystem(InterstellarMapPanel.this.campaign.getCurrentSystem(), true);
                            center(InterstellarMapPanel.this.campaign.getCurrentSystem());
                        });
                    }
                    centerM.add(item);
                    item = new JMenuItem("On Terra");
                    item.addActionListener(evt -> {
                        conf.centerX = 0.0;
                        conf.centerY = 0.0;
                        repaint();
                    });
                    centerM.add(item);
                    popup.add(centerM);
                      popup.addSeparator();
                      popup.add(createRoutePlanningMenuItem("map.route.plotHere", popupSystem != null,
                          () -> routePlanningHandler.plotRoute(popupSystem)));
                      popup.add(createRoutePlanningMenuItem("map.route.appendWaypoint", popupSystem != null,
                          () -> routePlanningHandler.appendWaypoint(popupSystem)));
                      popup.add(createRoutePlanningMenuItem("map.route.trimHere",
                          (popupSystem != null) && routePlanningHandler.canTrimRouteAt(popupSystem),
                          () -> routePlanningHandler.trimRouteAt(popupSystem)));
                      popup.add(createRoutePlanningMenuItem("map.route.removeWaypoint",
                          (popupSystem != null) && routePlanningHandler.isRequestedWaypoint(popupSystem),
                          () -> routePlanningHandler.removeWaypoint(popupSystem)));
                      popup.add(createRoutePlanningMenuItem("map.route.clear",
                          routePlanningHandler.hasPlannedRoute(), routePlanningHandler::clearPlannedRoute));
                      popup.addSeparator();
                    item = new JMenuItem("Cancel Current Trip");
                    item.setEnabled(null != InterstellarMapPanel.this.campaign.getPlayerForce()
                                                  .getForceDetachment()
                                                  .getCurrentLocation()
                                                  .getJumpPath());
                    item.addActionListener(evt -> {
                        InterstellarMapPanel.this.campaign.getPlayerForce()
                              .getForceDetachment()
                              .getCurrentLocation()
                              .setJumpPath(null);
                        repaint();
                    });
                    popup.add(item);
                    item = new JMenuItem("Save Map (64 Mpx at current zoom level) ...");
                    item.setEnabled(true);
                    item.addActionListener(evt -> {
                        final int imgSize = 8192;
                        BufferedImage img = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_RGB);
                        Graphics g = img.createGraphics();
                        int originalWidth = getWidth();
                        int originalHeight = getHeight();
                        double originalX = conf.centerX;
                        double originalY = conf.centerY;
                        try {
                            Optional<File> file = FileDialogs.saveStarMap(hqView.getFrame());
                            if (file.isPresent()) {
                                prepareStaticCartography();
                                clearRenderLayerCaches();
                                mapPanel.setSize(imgSize, imgSize);
                                conf.centerX += (imgSize - originalWidth) / conf.scale / 2.0;
                                conf.centerY += (imgSize - originalHeight) / conf.scale / 2.0;
                                mapPanel.print(g);
                                ImageIO.write(img, "png", file.get());
                            }
                        } catch (Exception ex) {
                            LOGGER.error("", ex);
                        }
                        conf.centerX = originalX;
                        conf.centerY = originalY;
                        clearRenderLayerCaches();
                        g.dispose();
                        mapPanel.repaint();
                    });
                    popup.add(item);
                    JMenu menuGM = new JMenu("GM Mode");
                    item = new JMenuItem("Move to selected planet");
                    item.setEnabled((selectedSystem != null) && InterstellarMapPanel.this.campaign.isGM());
                    if (selectedSystem != null) {
                        // only add if there is a planet to center on
                        item.addActionListener(evt -> {
                            InterstellarMapPanel.this.campaign.getPlayerForce()
                                  .getDetachmentLocationManager()
                                  .moveToPlanetarySystem(InterstellarMapPanel.this.campaign, selectedSystem);
                                routePlanningHandler.clearPlannedRoute();
                            center(selectedSystem);
                        });
                    }
                    menuGM.add(item);
                    /*
                     * TODO: re-enable this later
                     * item = new JMenuItem("Edit planetary events");
                     * item.setEnabled(selectedSystem != null && campaign.isGM());
                     * if (selectedSystem != null) {
                     * item.setText("Edit planetary events for " +
                     * selectedSystem.getPrintableName(Utilities.getDateTimeDay(campaign.getCalendar
                     * ())));
                     * item.addActionListener(new ActionListener() {
                     *
                     * @Override
                     * public void actionPerformed(ActionEvent attackingEntity) {
                     * openPlanetEventEditor(selectedSystem);
                     * }
                     * });
                     * }
                     * menuGM.add(item);
                     */

                    item = new JMenuItem("Edit System (GM)...");
                    item.setEnabled((selectedSystem != null) && InterstellarMapPanel.this.campaign.isGM());
                    if (selectedSystem != null) {
                        final PlanetarySystem editTarget = selectedSystem;
                        item.addActionListener(evt -> openPlanetarySystemEditor(editTarget));
                    }
                    menuGM.add(item);

                    item = new JMenuItem("Recharge Jumpdrive");
                    item.setEnabled(InterstellarMapPanel.this.campaign.getPlayerForce()
                                          .getForceDetachment()
                                          .getCurrentLocation()
                                          .isRecharging(InterstellarMapPanel.this.campaign) &&
                                          InterstellarMapPanel.this.campaign.isGM());
                    item.addActionListener(evt -> {
                        InterstellarMapPanel.this.campaign.getPlayerForce().getForceDetachment().getCurrentLocation()
                              .chargeFully(InterstellarMapPanel.this.campaign);
                        InterstellarMapPanel.this.campaign.addReport(GENERAL, "GM: Jumpship drives fully charged");
                    });
                    menuGM.add(item);

                    popup.add(menuGM);
                    popup.show(e.getComponent(), e.getX() + 10, e.getY() + 10);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (e.getClickCount() >= 2) {
                        // center and zoom
                        changeSelectedSystem(nearestNeighbour(scr2mapX(e.getX()), scr2mapY(e.getY())));
                        if (conf.scale < 4.0) {
                            conf.scale = 4.0;
                        }
                        center(selectedSystem);
                        // bring up a planetary system map
                        hqView.getNavigationTab().getMapTab().switchPlanetaryMap(selectedSystem);
                    } else {
                        PlanetarySystem target = nearestNeighbour(scr2mapX(e.getX()), scr2mapY(e.getY()));
                        if (null == target) {
                            return;
                        }
                        if (e.isAltDown()) {
                            routePlanningHandler.plotRoute(target);
                            return;
                        } else if (e.isShiftDown()) {
                            routePlanningHandler.appendWaypoint(target);
                            return;
                        }
                        changeSelectedSystem(target);
                        repaint();
                    }
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (mouseMod != MouseEvent.BUTTON1) {
                    return;
                }
                if (lastMousePos != null) {
                    conf.centerX -= (lastMousePos.x - e.getX()) / conf.scale;
                    conf.centerY -= (lastMousePos.y - e.getY()) / conf.scale;
                    lastMousePos.x = e.getX();
                    lastMousePos.y = e.getY();
                }
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (lastMousePos == null) {
                    lastMousePos = new Point(e.getX(), e.getY());
                } else {
                    lastMousePos.x = e.getX();
                    lastMousePos.y = e.getY();
                }
                repaint();
            }
        });

        addMouseWheelListener(new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoom(Math.pow(1.175, -1 * e.getWheelRotation()), e.getPoint());
            }
        });

        pane = new JLayeredPane();
        mapPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                refreshTravelVisualState();
                Graphics2D g2 = (Graphics2D) g;
                double ambientElapsedSeconds = (System.nanoTime() - ambientAnimationEpochNanos)
                      / NANOSECONDS_PER_SECOND;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    RenderViewKey renderViewKey = RenderViewKey.create(getWidth(), getHeight(), conf.centerX,
                        conf.centerY, conf.scale);
                    paintStaticCartographyBackground(g2, renderViewKey);
                double size = 1 + 5 * Math.log(conf.scale);
                size = Math.clamp(size, conf.minDotSize, conf.maxDotSize);

                final Stroke thick = new BasicStroke(2.0f);
                final Stroke thin = new BasicStroke(1.2f);
                final Stroke dashed = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
                      new float[] { 3 }, 0);
                final Stroke dotted = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
                      new float[] { 2, 5 }, 0);

                minX = scr2mapX(-size * 2.0);
                minY = scr2mapY(getHeight() + size * 2.0);
                maxX = scr2mapX(getWidth() + size * 2.0);
                maxY = scr2mapY(-size * 2.0);
                now = InterstellarMapPanel.this.campaign.getLocalDate();
                    TerritoryAtlas atlas = getPreparedTerritoryAtlas(now);
                    TerritoryRenderKey territoryRenderKey = atlas == null ? null
                        : new TerritoryRenderKey(renderViewKey, now, cartographyDataRevision);
                PlanetarySystem hoveredSystem = findHoveredSystem(size);
                double semanticZoomReference = getSemanticZoomReference(conf.showPlanetNamesThreshold);
                    SemanticZoomProfile semanticZoom = SemanticZoomProfile.create(conf.scale, semanticZoomReference);
                    TerritoryVisualProfile territoryVisuals = TerritoryVisualProfile.create(conf.scale);
                    double visibleHpgNetworkAlpha = hpgNetworkLayerAlpha * semanticZoom.hpgNetworkAlpha();

                Arc2D.Double arc = new Arc2D.Double();

                // Draw auras around a selected planet
                if (selectedSystem != null) {
                    final double x = map2scrX(selectedSystem.getX());
                    final double y = map2scrY(selectedSystem.getY());
                    // Contract Search Radius Aura
                    if (!InterstellarMapPanel.this.campaign.getCampaignOptions().getContractMarketMethod().isNone()
                              && MekHQ.getMHQOptions().getInterstellarMapShowContractSearchRadius()) {
                        final double z = map2scrX(selectedSystem.getX()
                                                        +
                                                        InterstellarMapPanel.this.campaign.getCampaignOptions()
                                                              .getContractSearchRadius());
                        final double contractSearchRadius = z - x;
                        g2.setPaint(MekHQ.getMHQOptions().getInterstellarMapContractSearchRadiusColour());
                        g2.setStroke(CONFIGURABLE_RANGE_RING_STROKE);
                        arc.setArcByCenter(x, y, contractSearchRadius, 0, 360, Arc2D.OPEN);
                        g2.draw(arc);
                    }

                    // Acquisition Search Radius Aura
                    if (InterstellarMapPanel.this.campaign.getCampaignOptions().isUsePlanetaryAcquisition()
                              && MekHQ.getMHQOptions().getInterstellarMapShowPlanetaryAcquisitionRadius()
                              && (conf.scale > MekHQ.getMHQOptions()
                                                     .getInterstellarMapShowPlanetaryAcquisitionRadiusMinimumZoom())) {
                        final double z = map2scrX(selectedSystem.getX()
                                                        + (MHQConstants.MAX_JUMP_RADIUS
                                                                 *
                                                                 InterstellarMapPanel.this.campaign.getCampaignOptions()
                                                                       .getMaxJumpsPlanetaryAcquisition()));
                        final double acquisitionRadius = z - x;
                        g2.setPaint(MekHQ.getMHQOptions().getInterstellarMapPlanetaryAcquisitionRadiusColour());
                        g2.setStroke(CONFIGURABLE_RANGE_RING_STROKE);
                        arc.setArcByCenter(x, y, acquisitionRadius, 0, 360, Arc2D.OPEN);
                        g2.draw(arc);
                    }

                    // Jump Radius Aura
                    if (MekHQ.getMHQOptions().getInterstellarMapShowJumpRadius()
                              && (conf.scale > MekHQ.getMHQOptions().getInterstellarMapShowJumpRadiusMinimumZoom())) {
                        final double z = map2scrX(selectedSystem.getX() + MHQConstants.MAX_JUMP_RADIUS);
                        final double jumpRadius = z - x;
                        g2.setPaint(MekHQ.getMHQOptions().getInterstellarMapJumpRadiusColour());
                        g2.setStroke(CONFIGURABLE_RANGE_RING_STROKE);
                        arc.setArcByCenter(x, y, jumpRadius, 0, 360, Arc2D.OPEN);
                        g2.draw(arc);
                    }

                    if (visibleHpgNetworkAlpha > 0.0) {
                        paintLayerWithAlpha(g2, visibleHpgNetworkAlpha, hpgGraphics -> {
                            final double z = map2scrX(selectedSystem.getX() + 50);
                            final double jumpRadius = z - x;
                            hpgGraphics.setPaint(HPG_RANGE_RING_COLOR);
                            hpgGraphics.setStroke(HPG_RANGE_RING_STROKE);
                            arc.setArcByCenter(x, y, jumpRadius, 0, 360, Arc2D.OPEN);
                            hpgGraphics.draw(arc);
                        });
                    }
                }

                double visibleTerritoryAlpha = territoryLayerAlpha * semanticZoom.territoryAlpha();
                if ((atlas != null) && (territoryRenderKey != null) && (visibleTerritoryAlpha > 0.0)) {
                    paintStaticTerritoryLayer(g2, atlas, territoryRenderKey, visibleTerritoryAlpha);
                }
                double visibleFactionLogoAlpha = semanticZoom.factionLogoAlpha() * getFactionMapModeAlpha();
                if ((atlas != null) && (territoryRenderKey != null) && (visibleFactionLogoAlpha > 0.0)) {
                    FactionLogoRenderKey factionLogoRenderKey = createFactionLogoRenderKey(territoryRenderKey);
                    paintStaticFactionLogoLayer(g2, atlas, factionLogoRenderKey,
                          visibleFactionLogoAlpha * FACTION_LOGO_OPACITY);
                }

                JumpPath activeJumpPath = getActiveJumpPath();
                boolean showRouteActivation = isRouteActivationAnimating();
                List<PlanetarySystem> activeRouteSystems = showRouteActivation
                      ? resolveRouteSystems(activatingRouteSystemIds)
                      : getPathSystems(activeJumpPath);
                if (showRouteActivation && (activeRouteSystems.size() != activatingRouteSystemIds.size())) {
                    showRouteActivation = false;
                    activeRouteSystems = getPathSystems(activeJumpPath);
                }
                int revealedProposedRouteSystemCount = showRouteActivation
                      ? 0
                      : getRevealedProposedRouteSystemCount();
                if (!showRouteActivation) {
                    drawProposedRoute(g2, arc, size, revealedProposedRouteSystemCount);
                }

                        if (visibleHpgNetworkAlpha > 0.0) {
                          double hpgSystemSize = size;
                          paintLayerWithAlpha(g2, visibleHpgNetworkAlpha,
                              hpgGraphics -> InterstellarMapPanel.this.drawHpgNetworkLayer(hpgGraphics, arc, hpgSystemSize,
                                                                    thick, thin, dashed, dotted, ambientElapsedSeconds,
                                                                    semanticZoom.hpgTrafficAlpha()));
                }

                if (!activeRouteSystems.isEmpty()) {
                    drawActiveRoute(g2, arc, activeRouteSystems, size,
                          showRouteActivation ? routeActivationProgress : 1.0, ambientElapsedSeconds);
                }

                                Set<PlanetarySystem> activeRouteWaypoints = new HashSet<>(activeRouteSystems);
                                Set<PlanetarySystem> routeWaypoints = new HashSet<>();
                                for (int systemIndex = 0;
                                            systemIndex < revealedProposedRouteSystemCount;
                                            systemIndex++) {
                                        routeWaypoints.add(jumpPath.get(systemIndex));
                                }
                routeWaypoints.addAll(activeRouteSystems);

                PlanetarySystem currentSystem = InterstellarMapPanel.this.campaign.getCurrentSystem();
                Set<String> priorityLabelSystemIds = new HashSet<>();
                if (selectedSystem != null) {
                    priorityLabelSystemIds.add(selectedSystem.getId());
                }
                if (currentSystem != null) {
                    priorityLabelSystemIds.add(currentSystem.getId());
                }
                if (!activeRouteSystems.isEmpty()) {
                    priorityLabelSystemIds.add(activeRouteSystems.getLast().getId());
                }
                if (!showRouteActivation && !jumpPath.isEmpty()
                      && (revealedProposedRouteSystemCount >= jumpPath.size())) {
                                        priorityLabelSystemIds.add(jumpPath.get(jumpPath.size() - 1).getId());
                }

                Map<Faction, String> capitals = new HashMap<>();
                for (Faction faction : Factions.getInstance().getFactions()) {
                    capitals.put(faction, faction.getStartingPlanet(InterstellarMapPanel.this.campaign.getLocalDate()));
                }
                Map<String, StrategicMarker> strategicMarkers = buildStrategicMarkers();

                boolean isUseFactionStandingOutlawing =
                      campaign.getCampaignOptions().isUseFactionStandingOutlawedSafe();
                Faction campaignFaction = campaign.getFaction();
                FactionStandings factionStandings = campaign.getPlayerForce().getFactionStandings();
                LocalDate today = campaign.getLocalDate();
                List<AtBContract> activeAtBContracts = campaign.getActiveAtBContracts();

                FactionHints factionHints = FactionHints.getInstance();

                Graphics2D previousMapModeGraphics = null;
                Graphics2D targetMapModeGraphics = g2;
                if (mapModeAnimating) {
                    previousMapModeGraphics = createLayerGraphicsWithAlpha(g2, 1.0 - mapModeAnimationProgress);
                    targetMapModeGraphics = createLayerGraphicsWithAlpha(g2, mapModeAnimationProgress);
                }

                for (PlanetarySystem system : systems) {
                    if (isSystemVisible(system, false)) {
                        double x = map2scrX(system.getX());
                        double y = map2scrY(system.getY());
                        boolean systemEmpty = isSystemEmpty(system);
                        boolean routeWaypoint = routeWaypoints.contains(system);
                        RouteMarkerState routeMarkerState = activeRouteWaypoints.contains(system)
                            ? RouteMarkerState.ACTIVE
                            : (routeWaypoint ? RouteMarkerState.PLANNED : RouteMarkerState.NONE);
                        SystemMarkerLayout markerLayout = createSystemMarkerLayout(system, size, routeMarkerState,
                            hoveredSystem);
                        boolean showRouteContact = systemEmpty && routeWaypoint && !optEmptySystems.isSelected();
                        boolean showIntrinsicStar = !systemEmpty || optEmptySystems.isSelected()
                            || isSameSystem(system, currentSystem)
                            || isSameSystem(system, selectedSystem)
                              || routeWaypoint;
                        if (showRouteContact) {
                            drawNavigationContact(g2, arc, x, y, size);
                        } else if (showIntrinsicStar && (semanticZoom.spectralAlpha() > 0.0)) {
                            paintLayerWithAlpha(g2, semanticZoom.spectralAlpha(),
                                starGraphics -> drawIntrinsicStar(starGraphics, arc, system, x, y,
                                    markerLayout.size(), ambientElapsedSeconds));
                        }

                        if (previousMapModeGraphics != null) {
                            drawMapModeOverlay(previousMapModeGraphics, arc, system, markerLayout, systemEmpty,
                                  showIntrinsicStar, showRouteContact, capitals, previousMapMode,
                                semanticZoom.strategicContactAlpha(), semanticZoom.detailedOverlayAlpha(),
                                                                territoryVisuals.sharedSystemAlpha(), semanticZoom.capitalAlpha(),
                                                                semanticZoom.serviceAlpha());
                        }
                        if (targetMapModeGraphics != null) {
                            drawMapModeOverlay(targetMapModeGraphics, arc, system, markerLayout, systemEmpty,
                                  showIntrinsicStar, showRouteContact, capitals, targetMapMode,
                                semanticZoom.strategicContactAlpha(), semanticZoom.detailedOverlayAlpha(),
                                                                territoryVisuals.sharedSystemAlpha(), semanticZoom.capitalAlpha(),
                                                                semanticZoom.serviceAlpha());
                        }

                        StrategicMarker strategicMarker = strategicMarkers.get(system.getId());
                        double markerOperationsAlpha = visibleOperationAlpha(
                              operationsLayerAlpha, semanticZoom, strategicMarker);
                        if (markerOperationsAlpha > 0.0) {
                            paintLayerWithAlpha(g2, markerOperationsAlpha,
                                markerGraphics -> drawOperationMarker(markerGraphics, markerLayout, strategicMarker));
                        }

                        // Outlaw status image
                        if (isUseFactionStandingOutlawing) {
                            boolean isOutlawedInSystem = !FactionStandingUtilities.canEnterTargetSystem(campaignFaction,
                                  factionStandings, null, system, today, activeAtBContracts, factionHints);
                            if (isOutlawedInSystem) {
                                int half = (int) (size * 0.8);
                                g2.setPaint(Color.BLACK);
                                Stroke oldStroke = g2.getStroke();
                                g2.setStroke(new BasicStroke(4));
                                g2.drawLine((int) (x - half), (int) (y - half), (int) (x + half), (int) (y + half));
                                g2.drawLine((int) (x - half), (int) (y + half), (int) (x + half), (int) (y - half));
                                g2.setStroke(oldStroke);
                            }
                        }

                        // GM-edited system marker: thin cyan outline ring so the player can spot non-canon edits.
                        if (campaign.hasPlanetarySystemOverride(system.getId())) {
                            Stroke oldStroke = g2.getStroke();
                            g2.setPaint(Color.CYAN);
                            g2.setStroke(new BasicStroke(2.0f));
                            arc.setArcByCenter(x, y, markerLayout.overrideRadius(), 0, 360, Arc2D.OPEN);
                            g2.draw(arc);
                            g2.setStroke(oldStroke);
                        }

                        boolean isCurrentSystem = isSameSystem(system, currentSystem);
                        if (isCurrentSystem && (semanticZoom.strategicContactAlpha() > 0.0)) {
                            paintLayerWithAlpha(g2, semanticZoom.strategicContactAlpha(),
                                  markerGraphics -> drawStrategicCurrentLocationMarker(markerGraphics, markerLayout));
                        }
                        boolean isSelectionAnimating = isSelectionAnimationTarget(system);
                        if ((hoveredSystem != null) && hoveredSystem.equals(system)
                            && !isSameSystem(system, selectedSystem)) {
                            drawHoveredSystemMarker(g2, markerLayout);
                        }
                        if ((selectedSystem != null) && selectedSystem.equals(system)) {
                            if (isSelectionAnimating) {
                                drawSelectionAnimationMarker(g2, markerLayout, selectionAnimationProgress);
                            } else {
                                drawSelectedSystemMarker(g2, markerLayout, system.getId(), ambientElapsedSeconds);
                            }
                        }
                        if ((semanticZoom.currentLocationAlpha() > 0.0) && !isSystemHopAnimating()
                            && isCurrentSystem) {
                            paintLayerWithAlpha(g2, semanticZoom.currentLocationAlpha(),
                                markerGraphics -> drawCurrentLocationMarker(markerGraphics, markerLayout,
                                    ambientElapsedSeconds));
                        }
                    }
                }

                if ((semanticZoom.currentLocationAlpha() > 0.0) && isSystemHopAnimating()) {
                      double hopMarkerSize = size;
                    paintLayerWithAlpha(g2, semanticZoom.currentLocationAlpha(),
                          markerGraphics -> drawSystemHopMarker(markerGraphics, hopMarkerSize, hoveredSystem,
                              ambientElapsedSeconds));
                }

                if (previousMapModeGraphics != null) {
                    previousMapModeGraphics.dispose();
                }
                if (targetMapModeGraphics != g2) {
                    targetMapModeGraphics.dispose();
                }

                if (!activeRouteSystems.isEmpty()) {
                    drawActiveRouteWaypointBadges(g2, activeRouteSystems, size,
                          showRouteActivation ? routeActivationProgress : 1.0, hoveredSystem,
                          semanticZoom.routeBadgeAlpha());
                }
                if (!showRouteActivation) {
                    drawRouteWaypointBadges(g2, jumpPath, size, PLANNED_ROUTE_COLOR,
                          revealedProposedRouteSystemCount, hoveredSystem, semanticZoom.routeBadgeAlpha());
                }

                // cycle through planets again and assign names - to make sure names go on
                // outside
                for (PlanetarySystem system : systems) {
                    boolean routeWaypoint = routeWaypoints.contains(system);
                    if (isSystemVisible(system, !optEmptySystems.isSelected())
                              || (routeWaypoint && isSystemVisible(system, false))
                              || (system.equals(selectedSystem) && isSystemVisible(system, false))) {
                        double x = map2scrX(system.getX());
                        double y = map2scrY(system.getY());
                        boolean isPriorityLabel = priorityLabelSystemIds.contains(system.getId())
                            || isSameSystem(system, hoveredSystem);
                        double baseLabelAlpha = isPriorityLabel
                            ? 1.0
                            : (routeWaypoint
                                ? semanticZoom.routeLabelAlpha()
                                : semanticZoom.ordinaryLabelAlpha());
                        if (baseLabelAlpha > 0.0) {
                            String planetName = system.getPrintableName(InterstellarMapPanel.this.campaign.getLocalDate());
                            String stellarDetail = system.getStar() == null ? null : "  [" + system.getStar() + "]";
                            RouteMarkerState routeMarkerState = activeRouteWaypoints.contains(system)
                                  ? RouteMarkerState.ACTIVE
                                  : (routeWaypoint ? RouteMarkerState.PLANNED : RouteMarkerState.NONE);
                                                        SystemMarkerLayout markerLayout = createSystemMarkerLayout(system, size,
                                                                    routeMarkerState, hoveredSystem);
                            final float xPos = (float) markerLayout.labelX();
                            final float yPos = (float) y;
                            drawSystemLabel(g2, planetName, stellarDetail, xPos, yPos,
                                  isPriorityLabel ? Color.WHITE : SYSTEM_LABEL_COLOR, baseLabelAlpha,
                                  semanticZoom.stellarDetailAlpha() * baseLabelAlpha);
                        }
                    }
                }

                NavigationInstrumentLayout instrumentLayout = createNavigationInstrumentLayout(
                      getWidth(), getHeight(), conf.scale);
                drawNavigationInstrument(g2, instrumentLayout);
            }
        };
        pane.add(mapPanel, Integer.valueOf(1));

        optionPanel = new JPanel();
        optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.Y_AXIS));
                optionPanel.setOpaque(false);
                optionPanel.setBorder(BorderFactory.createEmptyBorder(UIUtil.scaleForGUI(7), UIUtil.scaleForGUI(8),
                            UIUtil.scaleForGUI(6), UIUtil.scaleForGUI(8)));

                optionButton = getOptionButton();
                mapLegendButton = createMapLegendButton();
                mapLegendButton.addActionListener(event -> showMapLegendDialog());

        Icon checkboxIcon = new ImageIcon("data/images/misc/checkbox_unselected.png");
        Icon checkboxSelectedIcon = new ImageIcon("data/images/misc/checkbox_selected.png");

                JPanel optionHeader = new JPanel();
                optionHeader.setLayout(new BoxLayout(optionHeader, BoxLayout.X_AXIS));
                optionHeader.setOpaque(false);
                optionHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
                optionHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, optionButton.getPreferredSize().height));
                optionHeader.add(createLabel(LAYER_CONTROL_HEADING));
                optionHeader.add(Box.createHorizontalGlue());
                optionHeader.add(mapLegendButton);
                optionHeader.add(Box.createRigidArea(new Dimension(UIUtil.scaleForGUI(4), 0)));
                optionHeader.add(Box.createRigidArea(optionButton.getPreferredSize()));
                optionPanel.add(optionHeader);

                optFactions = createOptionRadioButton("Faction", checkboxIcon, checkboxSelectedIcon, MapMode.FACTION);
        optionPanel.add(optFactions);
          optTech = createOptionRadioButton("Technology", checkboxIcon, checkboxSelectedIcon, MapMode.TECHNOLOGY);
        optionPanel.add(optTech);
          optIndustry = createOptionRadioButton("Industry", checkboxIcon, checkboxSelectedIcon, MapMode.INDUSTRY);
        optionPanel.add(optIndustry);
          optRawMaterials = createOptionRadioButton("Raw Materials", checkboxIcon, checkboxSelectedIcon,
              MapMode.RAW_MATERIALS);
        optionPanel.add(optRawMaterials);
          optOutput = createOptionRadioButton("Output", checkboxIcon, checkboxSelectedIcon, MapMode.OUTPUT);
        optionPanel.add(optOutput);
          optAgriculture = createOptionRadioButton("Agriculture", checkboxIcon, checkboxSelectedIcon,
              MapMode.AGRICULTURE);
        optionPanel.add(optAgriculture);
          optPopulation = createOptionRadioButton("Population", checkboxIcon, checkboxSelectedIcon,
              MapMode.POPULATION);
        optionPanel.add(optPopulation);
          optHPG = createOptionRadioButton("HPG", checkboxIcon, checkboxSelectedIcon, MapMode.HPG);
        optionPanel.add(optHPG);
          optRecharge = createOptionRadioButton("Recharge Stations", checkboxIcon, checkboxSelectedIcon,
              MapMode.RECHARGE_STATIONS);
        optionPanel.add(optRecharge);
          optAcademies = createOptionRadioButton("Academies", checkboxIcon, checkboxSelectedIcon, MapMode.ACADEMIES);
        optionPanel.add(optAcademies);
          optHiringHalls = createOptionRadioButton("Hiring Halls", checkboxIcon, checkboxSelectedIcon,
              MapMode.HIRING_HALLS);
        optionPanel.add(optHiringHalls);
          optDiseases = createOptionRadioButton("Disease Outbreaks", checkboxIcon, checkboxSelectedIcon,
              MapMode.DISEASE_OUTBREAKS);
        optionPanel.add(optDiseases);

        ButtonGroup colorChoice = new ButtonGroup();
        colorChoice.add(optFactions);
        colorChoice.add(optTech);
        colorChoice.add(optIndustry);
        colorChoice.add(optRawMaterials);
        colorChoice.add(optOutput);
        colorChoice.add(optAgriculture);
        colorChoice.add(optPopulation);
        colorChoice.add(optHPG);
        colorChoice.add(optRecharge);
        colorChoice.add(optAcademies);
        colorChoice.add(optHiringHalls);
        colorChoice.add(optDiseases);
        // factions by default
        optFactions.setSelected(true);

        optionPanel.add(Box.createRigidArea(new Dimension(0, 7)));
        optionPanel.add(createOptionDivider());
        optionPanel.add(Box.createRigidArea(new Dimension(0, 7)));
        optionPanel.add(createLabel(OVERLAY_CONTROL_HEADING));
        optEmptySystems = createOptionCheckBox("Empty systems", checkboxIcon, checkboxSelectedIcon);
        optEmptySystems.setSelected(false);
        optEmptySystems.addActionListener(e -> repaint());
        optionPanel.add(optEmptySystems);
        optTerritory = createOptionCheckBox("Territory", checkboxIcon, checkboxSelectedIcon);
        optTerritory.setSelected(true);
        optTerritory.addActionListener(e -> startTerritoryLayerAnimation());
        optionPanel.add(optTerritory);
        optHPGNetwork = createOptionCheckBox("HPG Network", checkboxIcon, checkboxSelectedIcon);
        optHPGNetwork.addActionListener(e -> startHpgNetworkLayerAnimation());
        optionPanel.add(optHPGNetwork);
        optOperations = createOptionCheckBox("Operations", checkboxIcon, checkboxSelectedIcon);
        optOperations.setSelected(true);
        optOperations.addActionListener(e -> startOperationsLayerAnimation());
        optionPanel.add(optOperations);

        optionView = new JViewport();
        optionView.setOpaque(false);
          optionView.setView(optionPanel);

          optionControl = new JPanel(null);
          optionControl.setOpaque(true);
          optionControl.setBackground(LAYER_CONTROL_BACKGROUND);
          optionControl.setBorder(BorderFactory.createLineBorder(LAYER_CONTROL_BORDER,
              Math.max(1, UIUtil.scaleForGUI(1))));
          optionControl.add(optionView);
          optionControl.add(optionButton);
          optionControl.setComponentZOrder(optionButton, 0);

          pane.add(optionControl, Integer.valueOf(10));

        add(pane);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                updateAmbientAnimationTimer();
                requestStaticCartographyPreparation();
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        updateAmbientAnimationTimer();
        requestStaticCartographyPreparation();
    }

    @Override
    public void removeNotify() {
        ambientAnimationTimer.stop();
        travelAnimationTimer.stop();
        disposeMapLegendDialog();
        territoryPreparationQueue.cancel();
        clearRenderLayerCaches();
        super.removeNotify();
    }

    private void updateAmbientAnimationTimer() {
        if (isShowing()) {
            settleTravelVisualState();
            if (!ambientAnimationTimer.isRunning()) {
                ambientAnimationTimer.start();
            }
        } else {
            ambientAnimationTimer.stop();
            travelAnimationTimer.stop();
        }
    }

    private void updateAmbientAnimation() {
        if (!isShowing()) {
            ambientAnimationTimer.stop();
            travelAnimationTimer.stop();
            return;
        }
        refreshTravelVisualState();
        mapPanel.repaint();
    }

    private void settleTravelVisualState() {
        travelAnimationTimer.stop();
        observedCurrentSystemId = getCurrentSystemId();
        observedActiveRouteSystemIds = getPathSystemIds(getActiveJumpPath());
        observedActiveRouteExists = !observedActiveRouteSystemIds.isEmpty();
        cachedProposedRouteSystemIds = List.of();
        activatingRouteSystemIds = List.of();
        routeActivationProgress = 1.0;
        systemHopOriginId = null;
        systemHopDestinationId = null;
        systemHopProgress = 1.0;
        travelVisualStateInitialized = true;
    }

    private void refreshTravelVisualState() {
        if (campaign == null) {
            return;
        }
        if (!travelVisualStateInitialized) {
            settleTravelVisualState();
            return;
        }

        String currentSystemId = getCurrentSystemId();
        List<String> activeRouteSystemIds = getPathSystemIds(getActiveJumpPath());
        boolean activeRouteExists = !activeRouteSystemIds.isEmpty();
        if (isRouteActivationAnimating() && !activatingRouteSystemIds.equals(activeRouteSystemIds)) {
            finishRouteActivation();
        }
        if (activeRouteExists && !observedActiveRouteExists) {
            if ((activeRouteSystemIds.size() > 1) && activeRouteSystemIds.equals(cachedProposedRouteSystemIds)) {
                startRouteActivation(activeRouteSystemIds);
            }
            cachedProposedRouteSystemIds = List.of();
        }
        if (!java.util.Objects.equals(currentSystemId, observedCurrentSystemId)
              && (activeRouteExists || observedActiveRouteExists)) {
            startSystemHop(observedCurrentSystemId, currentSystemId);
        }
        observedCurrentSystemId = currentSystemId;
        observedActiveRouteSystemIds = activeRouteSystemIds;
        observedActiveRouteExists = activeRouteExists;
    }

    private void startRouteActivation(List<String> routeSystemIds) {
        activatingRouteSystemIds = List.copyOf(routeSystemIds);
        routeActivationStartTime = System.nanoTime();
        routeActivationProgress = 0.0;
        startTravelAnimationTimerIfVisible();
    }

    private void finishRouteActivation() {
        activatingRouteSystemIds = List.of();
        routeActivationProgress = 1.0;
    }

    private void startSystemHop(@Nullable String originId, @Nullable String destinationId) {
        if ((originId == null) || (destinationId == null) || originId.equals(destinationId)
              || (campaign.getSystemById(originId) == null) || (campaign.getSystemById(destinationId) == null)) {
            return;
        }
        systemHopOriginId = originId;
        systemHopDestinationId = destinationId;
        systemHopStartTime = System.nanoTime();
        systemHopProgress = 0.0;
        startTravelAnimationTimerIfVisible();
    }

    private void startTravelAnimationTimerIfVisible() {
        if (isShowing() && !travelAnimationTimer.isRunning()) {
            travelAnimationTimer.start();
        }
    }

    private void updateTravelAnimations() {
        if (!isShowing()) {
            travelAnimationTimer.stop();
            return;
        }

        long nowNanos = System.nanoTime();
        if (isRouteActivationAnimating()) {
            routeActivationProgress = Math.min(1.0,
                  (double) (nowNanos - routeActivationStartTime) / ROUTE_ACTIVATION_DURATION_NS);
            if (routeActivationProgress >= 1.0) {
                finishRouteActivation();
            }
        }
        if (isSystemHopAnimating()) {
            systemHopProgress = Math.min(1.0,
                  (double) (nowNanos - systemHopStartTime) / SYSTEM_HOP_DURATION_NS);
            if (systemHopProgress >= 1.0) {
                systemHopOriginId = null;
                systemHopDestinationId = null;
            }
        }
        if (!isRouteActivationAnimating() && !isSystemHopAnimating()) {
            travelAnimationTimer.stop();
        }
        mapPanel.repaint();
    }

    private boolean isRouteActivationAnimating() {
        return (routeActivationProgress < 1.0) && (activatingRouteSystemIds.size() > 1);
    }

    private boolean isSystemHopAnimating() {
        return (systemHopProgress < 1.0) && (systemHopOriginId != null) && (systemHopDestinationId != null);
    }

    private @Nullable JumpPath getActiveJumpPath() {
        if (campaign == null) {
            return null;
        }
        return campaign.getPlayerForce().getForceDetachment().getCurrentLocation().getJumpPath();
    }

    private @Nullable String getCurrentSystemId() {
        PlanetarySystem currentSystem = campaign == null ? null : campaign.getCurrentSystem();
        return currentSystem == null ? null : currentSystem.getId();
    }

    static JTabbedPane createMapLegendTabbedPane() {
        int contentWidth = UIUtil.scaleForGUI(MAP_LEGEND_CONTENT_WIDTH);
        List<JPanel> sections = MAP_LEGEND_SECTIONS.stream()
              .map(section -> createMapLegendSection(section, contentWidth))
              .toList();
        int viewportHeight = sections.stream()
              .mapToInt(section -> section.getPreferredSize().height)
              .max()
              .orElse(1);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setOpaque(true);
        tabbedPane.setBackground(MAP_LEGEND_BACKGROUND);
        tabbedPane.setForeground(LAYER_CONTROL_TEXT);
        tabbedPane.setFocusable(true);
        tabbedPane.getAccessibleContext().setAccessibleName("Map symbol legend");
        tabbedPane.getAccessibleContext().setAccessibleDescription(
              "Legend for symbols shown on the interstellar map");
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            MapLegendSection section = MAP_LEGEND_SECTIONS.get(sectionIndex);
            JScrollPane scrollPane = createMapLegendSectionScrollPane(sections.get(sectionIndex), contentWidth,
                  viewportHeight);
            tabbedPane.addTab(section.heading(), scrollPane);
            tabbedPane.setBackgroundAt(sectionIndex, MAP_LEGEND_BACKGROUND);
            tabbedPane.setForegroundAt(sectionIndex, LAYER_CONTROL_TEXT);
        }
        return tabbedPane;
    }

    private static JScrollPane createMapLegendSectionScrollPane(JPanel section, int viewportWidth,
          int viewportHeight) {
        JScrollPane scrollPane = new JScrollPane(section, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(true);
        scrollPane.setBackground(MAP_LEGEND_BACKGROUND);
        scrollPane.setFocusable(true);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(MAP_LEGEND_BACKGROUND);
        Dimension viewportSize = new Dimension(viewportWidth, viewportHeight);
        scrollPane.getViewport().setPreferredSize(viewportSize);
        scrollPane.setPreferredSize(viewportSize);
        scrollPane.getVerticalScrollBar().setUnitIncrement(UIUtil.scaleForGUI(16));
        scrollPane.getAccessibleContext().setAccessibleName(section.getAccessibleContext().getAccessibleName());
        scrollPane.getAccessibleContext().setAccessibleDescription(
              "Map symbol legend section " + section.getAccessibleContext().getAccessibleName());
        return scrollPane;
    }

    private static JPanel createMapLegendSection(MapLegendSection section, int sectionWidth) {
        int horizontalPadding = UIUtil.scaleForGUI(8);
        int rowWidth = sectionWidth - (horizontalPadding * 2);
        JPanel sectionPanel = new JPanel();
        sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.Y_AXIS));
        sectionPanel.setOpaque(true);
        sectionPanel.setBackground(MAP_LEGEND_BACKGROUND);
        sectionPanel.setBorder(BorderFactory.createEmptyBorder(UIUtil.scaleForGUI(8), horizontalPadding,
              UIUtil.scaleForGUI(8), horizontalPadding));
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionPanel.getAccessibleContext().setAccessibleName(section.heading());
        sectionPanel.putClientProperty("mapLegendSection", section.heading());
        for (MapLegendEntry entry : section.entries()) {
            sectionPanel.add(createMapLegendRow(entry, rowWidth));
        }

        Dimension preferredSize = sectionPanel.getPreferredSize();
        sectionPanel.setPreferredSize(new Dimension(sectionWidth, preferredSize.height));
        sectionPanel.setMaximumSize(new Dimension(sectionWidth, preferredSize.height));
        return sectionPanel;
    }

    private static JPanel createMapLegendRow(MapLegendEntry entry, int rowWidth) {
        int verticalPadding = UIUtil.scaleForGUI(6);
        int swatchGap = UIUtil.scaleForGUI(10);
        int swatchWidth = UIUtil.scaleForGUI(MAP_LEGEND_SWATCH_WIDTH);
        int textWidth = rowWidth - swatchWidth - swatchGap;
        MapLegendSwatch swatch = new MapLegendSwatch(entry);

        JLabel title = new JLabel(entry.title());
        title.setForeground(LAYER_CONTROL_TEXT);
        Font baseFont = ObjectUtility.nonNull(UIManager.getFont("Label.font"), title.getFont());
        title.setFont(baseFont.deriveFont(Font.BOLD, baseFont.getSize2D()));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea meaning = new JTextArea(entry.meaning());
        meaning.setEditable(false);
        meaning.setFocusable(false);
        meaning.setOpaque(false);
        meaning.setForeground(MAP_LEGEND_MUTED_TEXT);
        meaning.setFont(baseFont.deriveFont(Math.max(10.0f, baseFont.getSize2D() - 1.0f)));
        meaning.setLineWrap(true);
        meaning.setWrapStyleWord(true);
        meaning.setBorder(null);
        meaning.setAlignmentX(Component.LEFT_ALIGNMENT);
        meaning.setSize(textWidth, Short.MAX_VALUE);
        int meaningHeight = meaning.getPreferredSize().height;
        Dimension meaningSize = new Dimension(textWidth, meaningHeight);
        meaning.setPreferredSize(meaningSize);
        meaning.setMinimumSize(meaningSize);
        meaning.setMaximumSize(meaningSize);
        meaning.putClientProperty("mapLegendTitle", entry.title());

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.add(title);
        textPanel.add(Box.createRigidArea(new Dimension(0, UIUtil.scaleForGUI(2))));
        textPanel.add(meaning);
        int textHeight = title.getPreferredSize().height + UIUtil.scaleForGUI(2) + meaningHeight;
        Dimension textSize = new Dimension(textWidth, textHeight);
        textPanel.setPreferredSize(textSize);
                textPanel.setMinimumSize(textSize);
        textPanel.setMaximumSize(textSize);
                textPanel.putClientProperty("mapLegendTextCell", Boolean.TRUE);
                textPanel.putClientProperty("mapLegendTitle", entry.title());

                JPanel swatchCell = new JPanel(new GridBagLayout());
                swatchCell.setOpaque(false);
                swatchCell.setPreferredSize(swatch.getPreferredSize());
                swatchCell.setMinimumSize(swatch.getPreferredSize());
                swatchCell.add(swatch);
                swatchCell.putClientProperty("mapLegendSwatchCell", Boolean.TRUE);
                swatchCell.putClientProperty("mapLegendTitle", entry.title());

                JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
                GridBagConstraints swatchConstraints = new GridBagConstraints();
                swatchConstraints.gridx = 0;
                swatchConstraints.gridy = 0;
                swatchConstraints.weighty = 1.0;
                swatchConstraints.fill = GridBagConstraints.VERTICAL;
                swatchConstraints.anchor = GridBagConstraints.CENTER;
                swatchConstraints.insets = new Insets(0, 0, 0, swatchGap);
                row.add(swatchCell, swatchConstraints);

                GridBagConstraints textConstraints = new GridBagConstraints();
                textConstraints.gridx = 1;
                textConstraints.gridy = 0;
                textConstraints.weightx = 1.0;
                textConstraints.weighty = 1.0;
                textConstraints.fill = GridBagConstraints.HORIZONTAL;
                textConstraints.anchor = GridBagConstraints.CENTER;
                row.add(textPanel, textConstraints);
        row.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, 0, 1, 0, MAP_LEGEND_DIVIDER),
              BorderFactory.createEmptyBorder(verticalPadding, 0, verticalPadding, 0)));
        int rowHeight = Math.max(swatch.getPreferredSize().height, textHeight) + (verticalPadding * 2) + 1;
        Dimension rowSize = new Dimension(rowWidth, rowHeight);
        row.setPreferredSize(rowSize);
        row.setMinimumSize(rowSize);
        row.setMaximumSize(rowSize);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.putClientProperty("mapLegendRow", Boolean.TRUE);
        row.putClientProperty("mapLegendTitle", entry.title());
        row.getAccessibleContext().setAccessibleName(entry.title());
        row.getAccessibleContext().setAccessibleDescription(entry.meaning());
        return row;
    }

    private static void paintMapLegendSymbol(Graphics2D graphics, MapLegendSymbol symbol) {
        switch (symbol) {
            case SYSTEM_CONTACT -> paintLegendSystemContact(graphics);
            case FACTION_OWNERSHIP -> paintLegendFactionOwnership(graphics);
            case ANALYTICAL_VALUE -> paintLegendAnalyticalValue(graphics);
            case FACTION_EMBLEM -> paintLegendFactionEmblem(graphics);
            case SELECTED_SYSTEM -> paintLegendSelectedSystem(graphics);
            case HOVERED_SYSTEM -> paintLegendHoveredSystem(graphics);
            case CURRENT_FLEET -> paintLegendCurrentFleet(graphics);
            case PLANNED_ROUTE -> paintLegendPlannedRoute(graphics);
            case ACTIVE_ROUTE -> paintLegendActiveRoute(graphics);
            case WAYPOINT_BADGE -> paintLegendWaypointBadge(graphics);
            case CONTRACT_SEARCH_RADIUS -> paintLegendRangeRing(graphics,
                MekHQ.getMHQOptions().getInterstellarMapContractSearchRadiusColour(),
                CONFIGURABLE_RANGE_RING_STROKE);
            case PLANETARY_ACQUISITION_RADIUS -> paintLegendRangeRing(graphics,
                MekHQ.getMHQOptions().getInterstellarMapPlanetaryAcquisitionRadiusColour(),
                CONFIGURABLE_RANGE_RING_STROKE);
            case JUMP_RADIUS -> paintLegendRangeRing(graphics,
                MekHQ.getMHQOptions().getInterstellarMapJumpRadiusColour(), CONFIGURABLE_RANGE_RING_STROKE);
            case HPG_RANGE -> paintLegendRangeRing(graphics, HPG_RANGE_RING_COLOR, HPG_RANGE_RING_STROKE);
            case FACTION_CAPITAL -> drawNationalCapitalMarker(graphics, new Point2D.Double(32, 19), 10,
                new Color(232, 112, 84));
            case GREAT_HIRING_HALL -> drawGreatHiringHallMarker(graphics, new Point2D.Double(32, 19), 10);
            case OPERATION -> paintLegendOperation(graphics);
            case RESTRICTED_SYSTEM -> paintLegendRestrictedSystem(graphics);
            case GM_EDITED_SYSTEM -> paintLegendGmEditedSystem(graphics);
            case HPG_NETWORK -> paintLegendHpgNetwork(graphics);
            case SOVEREIGN_TERRITORY -> paintLegendTerritorySemantic(graphics,
                TerritorySemantic.SOVEREIGN);
            case DISPUTED_TERRITORY -> paintLegendTerritorySemantic(graphics,
                TerritorySemantic.DISPUTED);
            case UNCLAIMED_POCKET -> paintLegendTerritorySemantic(graphics,
                TerritorySemantic.UNCLAIMED_POCKET);
            case ENCLAVE -> paintLegendTerritorySemantic(graphics, TerritorySemantic.ENCLAVE);
        }
    }

    private static void paintLegendRangeRing(Graphics2D graphics, Color color, Stroke stroke) {
        graphics.setPaint(color);
        graphics.setStroke(stroke);
        graphics.draw(new Ellipse2D.Double(15, 2, 34, 34));
        graphics.setPaint(SELECTED_SYSTEM_COLOR);
        graphics.fill(new Ellipse2D.Double(30, 17, 4, 4));
    }

    private static void paintLegendSystemContact(Graphics2D graphics) {
        Color spectralColor = new Color(255, 190, 120);
        graphics.setPaint(new RadialGradientPaint(new Point2D.Double(19, 19), 9.0f,
              new float[] { 0.0f, 0.25f, 0.62f, 1.0f },
              new Color[] { brighten(spectralColor), spectralColor, withAlpha(spectralColor, 80),
                            withAlpha(spectralColor, 0) }));
        graphics.fill(new Ellipse2D.Double(10, 10, 18, 18));

        Arc2D.Double contact = new Arc2D.Double();
        contact.setArcByCenter(46, 19, 7.0, 90, 180, Arc2D.PIE);
        graphics.setPaint(new Color(88, 170, 230));
        graphics.fill(contact);
        contact.setArcByCenter(46, 19, 7.0, 270, 180, Arc2D.PIE);
        graphics.setPaint(new Color(232, 112, 84));
        graphics.fill(contact);
        contact.setArcByCenter(46, 19, 7.8, 0, 360, Arc2D.OPEN);
        graphics.setPaint(withAlpha(Color.WHITE, 100));
        graphics.setStroke(new BasicStroke(0.8f));
        graphics.draw(contact);
    }

    private static void paintLegendFactionOwnership(Graphics2D graphics) {
        Arc2D.Double arc = new Arc2D.Double();
        drawNavigationContact(graphics, arc, 32, 19, 7);
        Color[] factionColors = { new Color(232, 112, 84), new Color(88, 170, 230),
                                  new Color(114, 196, 126) };
        graphics.setStroke(new BasicStroke(2.5f));
        for (int index = 0; index < factionColors.length; index++) {
            arc.setArcByCenter(32, 19, 12, 72 + (index * 120), 72, Arc2D.OPEN);
            graphics.setPaint(factionColors[index]);
            graphics.draw(arc);
        }
        SystemMarkerLayout layout = SystemMarkerLayout.create(32, 19, 9.2,
              RouteMarkerState.NONE, false, false);
        drawSharedSystemCue(graphics, arc, layout);
    }

    private static void paintLegendAnalyticalValue(Graphics2D graphics) {
        Arc2D.Double arc = new Arc2D.Double();
        drawNavigationContact(graphics, arc, 32, 19, 7);
        SystemMarkerLayout layout = SystemMarkerLayout.create(32, 19, 7,
              RouteMarkerState.NONE, false, false);
        drawAnalyticalOverlay(graphics, arc, layout, new Color(114, 224, 152), 1.0);
    }

    private static void paintLegendFactionEmblem(Graphics2D graphics) {
        GeneralPath emblem = new GeneralPath();
        emblem.moveTo(32, 5);
        emblem.lineTo(48, 12);
        emblem.lineTo(44, 29);
        emblem.lineTo(32, 35);
        emblem.lineTo(20, 29);
        emblem.lineTo(16, 12);
        emblem.closePath();
        graphics.setPaint(withAlpha(Color.BLACK, 150));
        graphics.translate(2, 2);
        graphics.fill(emblem);
        graphics.translate(-2, -2);
        graphics.setPaint(withAlpha(new Color(88, 170, 230), 105));
        graphics.fill(emblem);
        graphics.setPaint(withAlpha(new Color(205, 232, 240), 120));
        graphics.setStroke(new BasicStroke(1.2f));
        graphics.draw(emblem);
        graphics.draw(new Line2D.Double(23, 18, 41, 18));
        graphics.draw(new Line2D.Double(32, 9, 32, 30));
    }

    private static void paintLegendSelectedSystem(Graphics2D graphics) {
        Arc2D.Double arc = new Arc2D.Double();
        drawNavigationContact(graphics, arc, 32, 19, 7);
        SystemMarkerLayout layout = SystemMarkerLayout.create(32, 19, 7,
              RouteMarkerState.NONE, true, false);
        drawSelectedSystemMarker(graphics, layout, "legend", 0.0);
    }

    private static void paintLegendHoveredSystem(Graphics2D graphics) {
        Arc2D.Double arc = new Arc2D.Double();
        drawNavigationContact(graphics, arc, 32, 19, 7);
        SystemMarkerLayout layout = SystemMarkerLayout.create(32, 19, 7,
              RouteMarkerState.NONE, false, true);
        drawHoveredSystemMarker(graphics, layout);
    }

    private static void paintLegendCurrentFleet(Graphics2D graphics) {
        drawJumpShipIcon(graphics, 18, 19, 0.0, 0.0, false);
        SystemMarkerLayout beaconLayout = SystemMarkerLayout.create(49, 19, 4,
              RouteMarkerState.NONE, false, false);
        drawStrategicCurrentLocationMarker(graphics, beaconLayout);
    }

    private static void paintLegendPlannedRoute(Graphics2D graphics) {
        graphics.setPaint(PLANNED_ROUTE_COLOR);
        graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
              new float[] { 7, 5 }, 0));
        graphics.draw(new Line2D.Double(5, 19, 59, 19));
        Arc2D.Double arc = new Arc2D.Double();
        SystemMarkerLayout layout = SystemMarkerLayout.create(32, 19, 5,
              RouteMarkerState.PLANNED, false, false);
        drawRouteWaypoint(graphics, arc, layout, PLANNED_ROUTE_COLOR);
    }

    private static void paintLegendActiveRoute(Graphics2D graphics) {
        graphics.setPaint(ACTIVE_ROUTE_COLOR);
        graphics.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Line2D.Double(5, 19, 59, 19));
        Arc2D.Double arc = new Arc2D.Double();
        SystemMarkerLayout layout = SystemMarkerLayout.create(32, 19, 5,
              RouteMarkerState.ACTIVE, false, false);
        drawRouteWaypoint(graphics, arc, layout, ACTIVE_ROUTE_COLOR);
        graphics.setPaint(withAlpha(ACTIVE_ROUTE_COLOR, 75));
        graphics.fill(new Ellipse2D.Double(44, 15, 8, 8));
        graphics.setPaint(ACTIVE_ROUTE_FLOW_COLOR);
        graphics.fill(new Ellipse2D.Double(46.3, 17.3, 3.4, 3.4));
    }

    private static void paintLegendWaypointBadge(Graphics2D graphics) {
        graphics.setPaint(PLANNED_ROUTE_COLOR);
        graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
              new float[] { 7, 5 }, 0));
        graphics.draw(new Line2D.Double(5, 8, 59, 8));
        SystemMarkerLayout layout = SystemMarkerLayout.create(16, 7, 3,
              RouteMarkerState.PLANNED, false, false);
        drawRouteWaypointBadge(graphics, layout, PLANNED_ROUTE_COLOR, 2);
    }

    private static void paintLegendOperation(Graphics2D graphics) {
          SystemMarkerLayout missionLayout = SystemMarkerLayout.create(42, 46, 4,
              RouteMarkerState.NONE, false, false);
          SystemMarkerLayout urgentLayout = SystemMarkerLayout.create(75, 46, 4,
              RouteMarkerState.NONE, false, false);
          drawOperationMarker(graphics, missionLayout, new StrategicMarker(1, 0));
          drawOperationMarker(graphics, urgentLayout, new StrategicMarker(3, 1));
    }

    private static void paintLegendRestrictedSystem(Graphics2D graphics) {
        Arc2D.Double arc = new Arc2D.Double();
        drawNavigationContact(graphics, arc, 32, 19, 9);
        graphics.setPaint(Color.BLACK);
        graphics.setStroke(new BasicStroke(4.0f));
        graphics.draw(new Line2D.Double(25, 12, 39, 26));
        graphics.draw(new Line2D.Double(25, 26, 39, 12));
    }

    private static void paintLegendGmEditedSystem(Graphics2D graphics) {
        Arc2D.Double arc = new Arc2D.Double();
        drawNavigationContact(graphics, arc, 32, 19, 7);
        graphics.setPaint(Color.CYAN);
        graphics.setStroke(new BasicStroke(2.0f));
        arc.setArcByCenter(32, 19, 12, 0, 360, Arc2D.OPEN);
        graphics.draw(arc);
    }

    private static void paintLegendHpgNetwork(Graphics2D graphics) {
        graphics.setPaint(Color.CYAN);
        graphics.setStroke(new BasicStroke(2.8f));
        graphics.draw(new Line2D.Double(5, 12, 59, 12));
        graphics.draw(new Ellipse2D.Double(8, 7, 10, 10));
        graphics.setPaint(Color.BLUE);
        graphics.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
              new float[] { 6, 4 }, 0));
        graphics.draw(new Line2D.Double(5, 27, 59, 27));
        graphics.draw(new Ellipse2D.Double(46, 22, 10, 10));
        graphics.setPaint(HPG_A_TRAFFIC_COLOR);
        graphics.fill(new Ellipse2D.Double(38, 9.5, 5, 5));
        graphics.setPaint(HPG_B_TRAFFIC_COLOR);
        graphics.fill(new Ellipse2D.Double(24, 25, 4, 4));
    }

    private static void paintLegendTerritorySemantic(Graphics2D graphics, TerritorySemantic semantic) {
        Shape territory = createLegendHex(32, 19, 14);
        Color factionColor = new Color(88, 170, 230);
        if (semantic == TerritorySemantic.UNCLAIMED_POCKET) {
            graphics.setPaint(TERRITORY_POCKET_FILL);
        } else if (semantic == TerritorySemantic.DISPUTED) {
            graphics.setPaint(new GradientPaint(18, 19, new Color(232, 112, 84, 110),
                  46, 19, new Color(88, 170, 230, 110), true));
        } else {
            graphics.setPaint(withAlpha(factionColor, 100));
        }
        graphics.fill(territory);

        switch (semantic) {
            case SOVEREIGN -> drawSovereignBoundary(graphics, territory, factionColor);
            case DISPUTED -> {
                paintLegendDisputedHatch(graphics, territory);
                graphics.setPaint(TERRITORY_BORDER_DARK);
                graphics.setStroke(new BasicStroke(3.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.draw(territory);
                graphics.setPaint(TERRITORY_NEUTRAL_EDGE);
                graphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT,
                      BasicStroke.JOIN_ROUND, 0, new float[] { 7.0f, 4.0f }, 0));
                graphics.draw(territory);
            }
            case UNCLAIMED_POCKET -> drawUnclaimedPocketBoundary(graphics, territory, 1.0);
            case ENCLAVE -> drawEnclaveBoundary(graphics, territory, factionColor, 1.0);
            case UNCLAIMED_EXTERIOR -> {
            }
        }
    }

    private static void paintLegendDisputedHatch(Graphics2D graphics, Shape territory) {
        Graphics2D hatchGraphics = (Graphics2D) graphics.create();
        try {
            hatchGraphics.clip(territory);
            hatchGraphics.setStroke(new BasicStroke(1.2f));
            Color[] colors = { new Color(232, 112, 84), new Color(88, 170, 230) };
            int colorIndex = 0;
            for (int hatchX = 8; hatchX < 56; hatchX += 8) {
                hatchGraphics.setPaint(colors[colorIndex++ % colors.length]);
                hatchGraphics.draw(new Line2D.Double(hatchX, 34, hatchX + 30, 4));
            }
        } finally {
            hatchGraphics.dispose();
        }
    }

    private static GeneralPath createLegendHex(double centerX, double centerY, double radius) {
        GeneralPath hex = new GeneralPath();
        for (int vertex = 0; vertex < 6; vertex++) {
            double angle = Math.toRadians(60 * vertex);
            double x = centerX + (Math.cos(angle) * radius);
            double y = centerY + (Math.sin(angle) * radius);
            if (vertex == 0) {
                hex.moveTo(x, y);
            } else {
                hex.lineTo(x, y);
            }
        }
        hex.closePath();
        return hex;
    }

    private static List<String> getPathSystemIds(@Nullable JumpPath path) {
        if ((path == null) || path.isEmpty()) {
            return List.of();
        }
        List<String> systemIds = new ArrayList<>(path.size());
        for (PlanetarySystem system : path.getSystems()) {
            systemIds.add(system.getId());
        }
        return List.copyOf(systemIds);
    }

    private static List<PlanetarySystem> getPathSystems(@Nullable JumpPath path) {
        return ((path == null) || path.isEmpty()) ? List.of() : List.copyOf(path.getSystems());
    }

    private List<PlanetarySystem> resolveRouteSystems(List<String> systemIds) {
        List<PlanetarySystem> routeSystems = new ArrayList<>(systemIds.size());
        for (String systemId : systemIds) {
            PlanetarySystem system = campaign.getSystemById(systemId);
            if (system == null) {
                return List.of();
            }
            routeSystems.add(system);
        }
        return List.copyOf(routeSystems);
    }

    static JButton createMapLegendButton() {
        JButton legendButton = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                paintMapLegendButton(graphics, this);
            }
        };
        Dimension buttonSize = UIUtil.scaleForGUI(MAP_LEGEND_BUTTON_SIZE, MAP_LEGEND_BUTTON_SIZE);
        legendButton.setPreferredSize(buttonSize);
        legendButton.setMinimumSize(buttonSize);
        legendButton.setMaximumSize(buttonSize);
        legendButton.setMargin(new Insets(0, 0, 0, 0));
        legendButton.setBorder(null);
        legendButton.setBorderPainted(false);
        legendButton.setContentAreaFilled(false);
        legendButton.setFocusPainted(false);
        legendButton.setOpaque(false);
        legendButton.setRolloverEnabled(true);
        legendButton.setFocusable(true);
        legendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String accessibleText = "Show map symbol legend";
        legendButton.setToolTipText(accessibleText);
        legendButton.getAccessibleContext().setAccessibleName(accessibleText);
        legendButton.getAccessibleContext().setAccessibleDescription(
              "Open a legend describing symbols on the interstellar map");
        return legendButton;
    }

    private static void paintMapLegendButton(Graphics graphics, AbstractButton legendButton) {
        Graphics2D buttonGraphics = (Graphics2D) graphics.create();
        try {
            buttonGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel model = legendButton.getModel();
            Color background = model.isPressed() ? LAYER_CONTROL_BUTTON_PRESSED
                  : model.isRollover() ? LAYER_CONTROL_BUTTON_HOVER : LAYER_CONTROL_BACKGROUND;
            buttonGraphics.setPaint(background);
            buttonGraphics.fillRect(0, 0, legendButton.getWidth(), legendButton.getHeight());

            double centerX = legendButton.getWidth() / 2.0;
            double centerY = legendButton.getHeight() / 2.0;
            double radius = Math.max(5.0, Math.min(legendButton.getWidth(), legendButton.getHeight()) * 0.3);
            buttonGraphics.setPaint(legendButton.isEnabled()
                  ? LAYER_CONTROL_BUTTON_ICON : LAYER_CONTROL_TEXT.darker());
            buttonGraphics.setStroke(new BasicStroke(Math.max(1.2f, UIUtil.scaleForGUI(1))));
            buttonGraphics.draw(new Ellipse2D.Double(centerX - radius, centerY - radius,
                  radius * 2.0, radius * 2.0));
            double glyphWidth = Math.max(1.4, UIUtil.scaleForGUI(1));
            buttonGraphics.setStroke(new BasicStroke((float) glyphWidth, BasicStroke.CAP_ROUND,
                  BasicStroke.JOIN_ROUND));
            buttonGraphics.draw(new Line2D.Double(centerX, centerY - 0.5,
                  centerX, centerY + (radius * 0.52)));
            buttonGraphics.fill(new Ellipse2D.Double(centerX - (glyphWidth / 2.0),
                  centerY - (radius * 0.58), glyphWidth, glyphWidth));

            if (legendButton.isFocusOwner()) {
                int focusInset = Math.max(2, UIUtil.scaleForGUI(2));
                buttonGraphics.setPaint(LAYER_CONTROL_BUTTON_FOCUS);
                buttonGraphics.setStroke(new BasicStroke(Math.max(1, UIUtil.scaleForGUI(1))));
                buttonGraphics.drawRect(focusInset, focusInset,
                      legendButton.getWidth() - (focusInset * 2) - 1,
                      legendButton.getHeight() - (focusInset * 2) - 1);
            }
        } finally {
            buttonGraphics.dispose();
        }
    }

    private void showMapLegendDialog() {
        if ((mapLegendDialog != null) && mapLegendDialog.isDisplayable()) {
            if (!mapLegendDialog.isVisible()) {
                mapLegendDialog.setVisible(true);
            }
            mapLegendDialog.toFront();
            mapLegendDialog.requestFocus();
            return;
        }
        mapLegendDialog = null;

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Map Symbols", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTabbedPane tabbedPane = createMapLegendTabbedPane();
        JPanel dialogContent = new JPanel(new BorderLayout());
        dialogContent.setOpaque(true);
        dialogContent.setBackground(MAP_LEGEND_BACKGROUND);
        int contentPadding = UIUtil.scaleForGUI(8);
        dialogContent.setBorder(BorderFactory.createEmptyBorder(contentPadding, contentPadding,
              contentPadding, contentPadding));
        dialogContent.add(tabbedPane, BorderLayout.CENTER);
        dialog.setContentPane(dialogContent);
        installMapLegendDialogCloseBinding(dialog);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (mapLegendDialog == dialog) {
                    mapLegendDialog = null;
                }
            }
        });
        mapLegendDialog = dialog;

        dialog.pack();
        Rectangle availableBounds = getMapLegendDialogBounds(owner);
        Dimension packedSize = dialog.getSize();
        dialog.setSize(Math.min(packedSize.width, availableBounds.width),
              Math.min(packedSize.height, availableBounds.height));
        Component relativeTo = mapPanel.isShowing() ? mapPanel : owner;
        dialog.setLocationRelativeTo(relativeTo);
        constrainToBounds(dialog, availableBounds);
        dialog.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            if (dialog.isVisible()) {
                tabbedPane.requestFocusInWindow();
            }
        });
    }

    private static void installMapLegendDialogCloseBinding(JDialog dialog) {
        String actionName = "closeMapLegendDialog";
        JRootPane rootPane = dialog.getRootPane();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
              .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), actionName);
        rootPane.getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dialog.dispose();
            }
        });
    }

    private Rectangle getMapLegendDialogBounds(@Nullable Window owner) {
        Rectangle bounds = getUsableScreenBounds(mapLegendButton);
        if ((owner != null) && owner.isShowing()) {
            Rectangle ownerBounds = bounds.intersection(owner.getBounds());
            if (!ownerBounds.isEmpty()) {
                bounds = ownerBounds;
            }
        }
        int margin = UIUtil.scaleForGUI(MAP_LEGEND_DIALOG_MARGIN);
        if ((bounds.width > (margin * 2)) && (bounds.height > (margin * 2))) {
            bounds.grow(-margin, -margin);
        }
        return bounds;
    }

    private static void constrainToBounds(Window window, Rectangle bounds) {
        int maximumX = bounds.x + bounds.width - window.getWidth();
        int maximumY = bounds.y + bounds.height - window.getHeight();
        int x = Math.max(bounds.x, Math.min(window.getX(), maximumX));
        int y = Math.max(bounds.y, Math.min(window.getY(), maximumY));
        window.setLocation(x, y);
    }

    private void disposeMapLegendDialog() {
        JDialog dialog = mapLegendDialog;
        mapLegendDialog = null;
        if (dialog != null) {
            dialog.dispose();
        }
    }

    private static Rectangle getUsableScreenBounds(Component component) {
        GraphicsConfiguration configuration = component.getGraphicsConfiguration();
        Rectangle usableScreen = new Rectangle(configuration.getBounds());
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        usableScreen.x += screenInsets.left;
        usableScreen.y += screenInsets.top;
        usableScreen.width -= screenInsets.left + screenInsets.right;
        usableScreen.height -= screenInsets.top + screenInsets.bottom;
        return usableScreen;
    }

    private JButton getOptionButton() {
        JButton optionButton = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                paintOptionButton(graphics, this);
            }
        };
        Dimension buttonSize = UIUtil.scaleForGUI(OPTION_PANEL_COLLAPSED_SIZE, OPTION_PANEL_COLLAPSED_SIZE);
        optionButton.setPreferredSize(buttonSize);
        optionButton.setMinimumSize(buttonSize);
        optionButton.setMaximumSize(buttonSize);
        optionButton.setMargin(new Insets(0, 0, 0, 0));
        optionButton.setBorder(null);
        optionButton.setBorderPainted(false);
        optionButton.setContentAreaFilled(false);
        optionButton.setFocusPainted(false);
        optionButton.setOpaque(false);
        optionButton.setRolloverEnabled(true);
        optionButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        updateOptionButtonAccessibility(optionButton);
        optionButton.addActionListener(e -> {
            optionPanelHidden = !optionPanelHidden;
            updateOptionButtonAccessibility(optionButton);
            startOptionPanelAnimation();
        });
        return optionButton;
    }

    private static void paintOptionButton(Graphics graphics, AbstractButton optionButton) {
        Graphics2D buttonGraphics = (Graphics2D) graphics.create();
        try {
            buttonGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel model = optionButton.getModel();
            Color background = model.isPressed() ? LAYER_CONTROL_BUTTON_PRESSED
                  : model.isRollover() ? LAYER_CONTROL_BUTTON_HOVER : LAYER_CONTROL_BACKGROUND;
            buttonGraphics.setColor(background);
            buttonGraphics.fillRect(0, 0, optionButton.getWidth(), optionButton.getHeight());

            int barHeight = Math.max(2, UIUtil.scaleForGUI(2));
            int barGap = Math.max(2, UIUtil.scaleForGUI(2));
            int barWidth = Math.min(UIUtil.scaleForGUI(14), optionButton.getWidth() - UIUtil.scaleForGUI(8));
            int totalHeight = (barHeight * 3) + (barGap * 2);
            int barX = (optionButton.getWidth() - barWidth) / 2;
            int barY = (optionButton.getHeight() - totalHeight) / 2;
            buttonGraphics.setColor(optionButton.isEnabled() ? LAYER_CONTROL_BUTTON_ICON : LAYER_CONTROL_TEXT.darker());
            for (int barIndex = 0; barIndex < 3; barIndex++) {
                int inset = (barIndex == 1) ? 0 : UIUtil.scaleForGUI(2);
                buttonGraphics.fillRoundRect(barX + inset, barY + (barIndex * (barHeight + barGap)),
                      barWidth - (inset * 2), barHeight, barHeight, barHeight);
            }

            if (optionButton.isFocusOwner()) {
                int focusInset = Math.max(2, UIUtil.scaleForGUI(2));
                buttonGraphics.setColor(LAYER_CONTROL_BUTTON_FOCUS);
                buttonGraphics.setStroke(new BasicStroke(Math.max(1, UIUtil.scaleForGUI(1))));
                buttonGraphics.drawRect(focusInset, focusInset,
                      optionButton.getWidth() - (focusInset * 2) - 1,
                      optionButton.getHeight() - (focusInset * 2) - 1);
            }
        } finally {
            buttonGraphics.dispose();
        }
    }

    private void updateOptionButtonAccessibility(JButton optionButton) {
        String actionText = optionPanelHidden ? "Show map layer controls" : "Hide map layer controls";
        optionButton.setToolTipText(actionText);
        optionButton.getAccessibleContext().setAccessibleName(actionText);
        optionButton.getAccessibleContext().setAccessibleDescription(actionText);
    }

    private void startOptionPanelAnimation() {
        long currentTime = System.nanoTime();
        advanceLayerAnimations(currentTime);
        optionPanelAnimationStartExpansion = optionPanelExpansion;
        optionPanelAnimationTargetExpansion = optionPanelHidden ? 0.0 : 1.0;
        double remainingDistance = Math.abs(optionPanelAnimationTargetExpansion - optionPanelExpansion);
        optionPanelAnimationDuration = getScaledLayerAnimationDuration(remainingDistance);
        optionPanelAnimationStartTime = currentTime;
        optionPanelAnimating = remainingDistance > 0.0;
        startLayerAnimationTimerIfNeeded();
        updateOptionViewBounds(getWidth(), getHeight());
        repaint();
    }

    private void startTerritoryLayerAnimation() {
        long currentTime = System.nanoTime();
        advanceLayerAnimations(currentTime);
        territoryLayerAnimationStartAlpha = territoryLayerAlpha;
        territoryLayerAnimationTargetAlpha = optTerritory.isSelected() ? 1.0 : 0.0;
        double remainingDistance = Math.abs(territoryLayerAnimationTargetAlpha - territoryLayerAlpha);
        territoryLayerAnimationDuration = getScaledLayerAnimationDuration(remainingDistance);
        territoryLayerAnimationStartTime = currentTime;
        territoryLayerAnimating = remainingDistance > 0.0;
        startLayerAnimationTimerIfNeeded();
        repaint();
    }

    private void startHpgNetworkLayerAnimation() {
        long currentTime = System.nanoTime();
        advanceLayerAnimations(currentTime);
        hpgNetworkLayerAnimationStartAlpha = hpgNetworkLayerAlpha;
        hpgNetworkLayerAnimationTargetAlpha = optHPGNetwork.isSelected() ? 1.0 : 0.0;
        double remainingDistance = Math.abs(hpgNetworkLayerAnimationTargetAlpha - hpgNetworkLayerAlpha);
        hpgNetworkLayerAnimationDuration = getScaledLayerAnimationDuration(remainingDistance);
        hpgNetworkLayerAnimationStartTime = currentTime;
        hpgNetworkLayerAnimating = remainingDistance > 0.0;
        startLayerAnimationTimerIfNeeded();
        repaint();
    }

    private void startOperationsLayerAnimation() {
        long currentTime = System.nanoTime();
        advanceLayerAnimations(currentTime);
        operationsLayerAnimationStartAlpha = operationsLayerAlpha;
        operationsLayerAnimationTargetAlpha = optOperations.isSelected() ? 1.0 : 0.0;
        double remainingDistance = Math.abs(operationsLayerAnimationTargetAlpha - operationsLayerAlpha);
        operationsLayerAnimationDuration = getScaledLayerAnimationDuration(remainingDistance);
        operationsLayerAnimationStartTime = currentTime;
        operationsLayerAnimating = remainingDistance > 0.0;
        startLayerAnimationTimerIfNeeded();
        repaint();
    }

    private void startMapModeAnimation(MapMode selectedMode) {
        long currentTime = System.nanoTime();
        advanceLayerAnimations(currentTime);
        if (selectedMode == targetMapMode) {
            return;
        }

        double startProgress = 0.0;
        if (mapModeAnimating && (selectedMode == previousMapMode)) {
            MapMode outgoingMode = targetMapMode;
            startProgress = 1.0 - mapModeAnimationProgress;
            previousMapMode = outgoingMode;
        } else {
            previousMapMode = targetMapMode;
        }
        targetMapMode = selectedMode;
        mapModeAnimationStartProgress = startProgress;
        mapModeAnimationProgress = startProgress;
        mapModeAnimationDuration = Math.max(1L,
              Math.round(MAP_MODE_ANIMATION_DURATION_NS * (1.0 - startProgress)));
        mapModeAnimationStartTime = currentTime;
        mapModeAnimating = previousMapMode != targetMapMode;
        startLayerAnimationTimerIfNeeded();
        repaint();
    }

    private void startLayerAnimationTimerIfNeeded() {
        if (hasActiveLayerAnimation() && !layerAnimationTimer.isRunning()) {
            layerAnimationTimer.start();
        }
    }

    private static long getScaledLayerAnimationDuration(double remainingDistance) {
        return Math.max(1L, Math.round(LAYER_ANIMATION_DURATION_NS * remainingDistance));
    }

    private void updateLayerAnimations() {
        boolean changed = advanceLayerAnimations(System.nanoTime());
        if (!hasActiveLayerAnimation()) {
            layerAnimationTimer.stop();
        }
        if (changed) {
            updateOptionViewBounds(getWidth(), getHeight());
            repaint();
        }
    }

    private boolean advanceLayerAnimations(long currentTime) {
        boolean changed = false;
        if (optionPanelAnimating) {
            double elapsedProgress = getAnimationProgress(currentTime, optionPanelAnimationStartTime,
                  optionPanelAnimationDuration);
            optionPanelExpansion = interpolate(optionPanelAnimationStartExpansion,
                  optionPanelAnimationTargetExpansion, easeInOutCubic(elapsedProgress));
            if (elapsedProgress >= 1.0) {
                optionPanelExpansion = optionPanelAnimationTargetExpansion;
                optionPanelAnimating = false;
            }
            changed = true;
        }
        if (territoryLayerAnimating) {
            double elapsedProgress = getAnimationProgress(currentTime, territoryLayerAnimationStartTime,
                  territoryLayerAnimationDuration);
            territoryLayerAlpha = interpolate(territoryLayerAnimationStartAlpha,
                  territoryLayerAnimationTargetAlpha, easeInOutCubic(elapsedProgress));
            if (elapsedProgress >= 1.0) {
                territoryLayerAlpha = territoryLayerAnimationTargetAlpha;
                territoryLayerAnimating = false;
            }
            changed = true;
        }
        if (hpgNetworkLayerAnimating) {
            double elapsedProgress = getAnimationProgress(currentTime, hpgNetworkLayerAnimationStartTime,
                  hpgNetworkLayerAnimationDuration);
            hpgNetworkLayerAlpha = interpolate(hpgNetworkLayerAnimationStartAlpha,
                  hpgNetworkLayerAnimationTargetAlpha, easeInOutCubic(elapsedProgress));
            if (elapsedProgress >= 1.0) {
                hpgNetworkLayerAlpha = hpgNetworkLayerAnimationTargetAlpha;
                hpgNetworkLayerAnimating = false;
            }
            changed = true;
        }
        if (operationsLayerAnimating) {
            double elapsedProgress = getAnimationProgress(currentTime, operationsLayerAnimationStartTime,
                  operationsLayerAnimationDuration);
            operationsLayerAlpha = interpolate(operationsLayerAnimationStartAlpha,
                  operationsLayerAnimationTargetAlpha, easeInOutCubic(elapsedProgress));
            if (elapsedProgress >= 1.0) {
                operationsLayerAlpha = operationsLayerAnimationTargetAlpha;
                operationsLayerAnimating = false;
            }
            changed = true;
        }
        if (mapModeAnimating) {
            double elapsedProgress = getAnimationProgress(currentTime, mapModeAnimationStartTime,
                  mapModeAnimationDuration);
            mapModeAnimationProgress = interpolate(mapModeAnimationStartProgress, 1.0,
                  easeInOutCubic(elapsedProgress));
            if (elapsedProgress >= 1.0) {
                mapModeAnimationProgress = 1.0;
                previousMapMode = targetMapMode;
                mapModeAnimating = false;
            }
            changed = true;
        }
        return changed;
    }

    private boolean hasActiveLayerAnimation() {
        return optionPanelAnimating || territoryLayerAnimating || hpgNetworkLayerAnimating
              || operationsLayerAnimating || mapModeAnimating;
    }

    private static double getAnimationProgress(long currentTime, long startTime, long duration) {
        return Math.min(1.0, (double) (currentTime - startTime) / duration);
    }

    private static void paintLayerWithAlpha(Graphics2D graphics, double alpha,
          Consumer<Graphics2D> layerPainter) {
        Graphics2D layerGraphics = createLayerGraphicsWithAlpha(graphics, alpha);
        try {
            layerPainter.accept(layerGraphics);
        } finally {
            layerGraphics.dispose();
        }
    }

    private static Graphics2D createLayerGraphicsWithAlpha(Graphics2D graphics, double alpha) {
        Graphics2D layerGraphics = (Graphics2D) graphics.create();
        layerGraphics.setComposite(deriveCompositeWithAlpha(layerGraphics.getComposite(), alpha));
        return layerGraphics;
    }

    private static Composite deriveCompositeWithAlpha(Composite composite, double alpha) {
        float clampedAlpha = (float) Math.clamp(alpha, 0.0, 1.0);
        if (composite instanceof AlphaComposite alphaComposite) {
            return alphaComposite.derive(alphaComposite.getAlpha() * clampedAlpha);
        }
        return AlphaComposite.SrcOver.derive(clampedAlpha);
    }

    private void updateOptionViewBounds(int containerWidth, int containerHeight) {
        if ((containerWidth <= 0) || (containerHeight <= 0)) {
            return;
        }

        Dimension preferredSize = optionPanel.getPreferredSize();
          Dimension buttonSize = optionButton.getPreferredSize();
          Insets controlInsets = optionControl.getInsets();
          int collapsedWidth = buttonSize.width + controlInsets.left + controlInsets.right;
          int collapsedHeight = buttonSize.height + controlInsets.top + controlInsets.bottom;
          int expandedWidth = Math.max(collapsedWidth,
              preferredSize.width + controlInsets.left + controlInsets.right);
          int expandedHeight = Math.max(collapsedHeight,
              preferredSize.height + controlInsets.top + controlInsets.bottom);
          int controlMargin = UIUtil.scaleForGUI(10);
          int availableWidth = Math.max(1, containerWidth - (controlMargin * 2));
          int availableHeight = Math.max(1, containerHeight - (controlMargin * 2));
        expandedWidth = Math.min(expandedWidth, availableWidth);
        expandedHeight = Math.min(expandedHeight, availableHeight);
          collapsedWidth = Math.min(collapsedWidth, expandedWidth);
          collapsedHeight = Math.min(collapsedHeight, expandedHeight);
        int viewportWidth = (int) Math.round(interpolate(collapsedWidth, expandedWidth, optionPanelExpansion));
        int viewportHeight = (int) Math.round(interpolate(collapsedHeight, expandedHeight, optionPanelExpansion));

          optionControl.setBounds(Math.max(0, containerWidth - controlMargin - viewportWidth),
              Math.max(0, containerHeight - controlMargin - viewportHeight), viewportWidth, viewportHeight);
          int contentWidth = Math.max(0, viewportWidth - controlInsets.left - controlInsets.right);
          int contentHeight = Math.max(0, viewportHeight - controlInsets.top - controlInsets.bottom);
          int expandedContentWidth = Math.max(0, expandedWidth - controlInsets.left - controlInsets.right);
          int expandedContentHeight = Math.max(0, expandedHeight - controlInsets.top - controlInsets.bottom);
          optionView.setViewSize(new Dimension(expandedContentWidth, expandedContentHeight));
          optionView.setBounds(controlInsets.left, controlInsets.top, contentWidth, contentHeight);
          optionView.setViewPosition(new Point(0, 0));
          int buttonWidth = Math.min(buttonSize.width, contentWidth);
          int buttonHeight = Math.min(buttonSize.height, contentHeight);
          int buttonOffsetY = Math.min(optionPanel.getInsets().top,
              Math.max(0, contentHeight - buttonHeight));
          optionButton.setBounds(controlInsets.left + Math.max(0, contentWidth - buttonWidth),
              controlInsets.top + buttonOffsetY, buttonWidth, buttonHeight);
          optionControl.revalidate();
        optionView.revalidate();
    }

    public void setCampaign(Campaign c) {
        this.campaign = c;
        refreshSystemsFromCampaign();
        settleTravelVisualState();
        repaint();
    }

    private void refreshSystemsFromCampaign() {
        String selectedSystemId = selectedSystem == null ? null : selectedSystem.getId();
        this.systems = campaign.getSystems();
        cartographyDataRevision++;
        territoryPreparationQueue.cancel();
        preparedTerritoryAtlas.clear();
        clearRenderLayerCaches();
        clearFactionLogoImages();
        requestStaticCartographyPreparation();
        if (selectedSystemId != null) {
            selectSystem(campaign.getSystemById(selectedSystemId), false);
        }
    }

    public void setJumpPath(@Nullable JumpPath path) {
        setProposedJumpPath(path);
    }

    private void setProposedJumpPath(@Nullable JumpPath path) {
        List<String> previousRouteSystemIds = getPathSystemIds(jumpPath);
        jumpPath = path == null ? new JumpPath() : path;
        List<String> proposedRouteSystemIds = getPathSystemIds(jumpPath);
        if (!proposedRouteSystemIds.isEmpty()) {
            cachedProposedRouteSystemIds = proposedRouteSystemIds;
        } else if (!previousRouteSystemIds.equals(getPathSystemIds(getActiveJumpPath()))) {
            cachedProposedRouteSystemIds = List.of();
        }
        if (jumpPath.size() > 1) {
            startProposedRouteAnimation();
        } else {
            stopProposedRouteAnimation();
        }
        refreshTravelVisualState();
        if (proposedRouteSystemIds.isEmpty()) {
            cachedProposedRouteSystemIds = List.of();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        pane.setBounds(0, 0, width, height);
        mapPanel.setBounds(0, 0, width, height);
        updateOptionViewBounds(width, height);

        super.paintComponent(g);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setOpaque(false);
        label.setForeground(PLANNED_ROUTE_COLOR);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() * 0.85f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 3, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return (label);
    }

    private JPanel createOptionDivider() {
        JPanel divider = new JPanel();
        divider.setOpaque(true);
        divider.setBackground(LAYER_CONTROL_BORDER);
        divider.setPreferredSize(new Dimension(150, 1));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        return divider;
    }

    private JCheckBox createOptionCheckBox(String text, Icon checkboxIcon, Icon checkboxSelectedIcon) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setOpaque(false);
        checkBox.setForeground(LAYER_CONTROL_TEXT);
        checkBox.setFocusable(false);
        checkBox.setFont(checkBox.getFont().deriveFont(Font.PLAIN));
        checkBox.setPreferredSize(new Dimension(150, 20));
        checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkBox.setIcon(checkboxIcon);
        checkBox.setSelectedIcon(checkboxSelectedIcon);
        checkBox.setSelected(false);
        return checkBox;
    }

    private JRadioButton createOptionRadioButton(String text, Icon checkboxIcon, Icon checkboxSelectedIcon,
          MapMode mapMode) {
        JRadioButton radioButton = new JRadioButton(text);
        radioButton.setOpaque(false);
        radioButton.setForeground(LAYER_CONTROL_TEXT);
        radioButton.setFocusable(false);
        radioButton.setFont(radioButton.getFont().deriveFont(Font.PLAIN));
        radioButton.setPreferredSize(new Dimension(150, 20));
        radioButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        radioButton.setIcon(checkboxIcon);
        radioButton.setSelectedIcon(checkboxSelectedIcon);
        radioButton.setSelected(false);
        radioButton.addActionListener(e -> {
            if (radioButton.isSelected()) {
                startMapModeAnimation(mapMode);
            }
        });
        return radioButton;
    }

    private static void setupHexPath(@Nullable GeneralPath path, double centerX, double centerY, double radius) {
        if (null == path) {
            return;
        }
        radius *= Math.sqrt(4.0 / 3.0);
        path.reset();
        path.moveTo(centerX + radius * BASE_HEX_COORDS[0].x, centerY + radius * BASE_HEX_COORDS[0].y);
        for (int i = 1; i < 6; ++i) {
            path.lineTo(centerX + radius * BASE_HEX_COORDS[i].x, centerY + radius * BASE_HEX_COORDS[i].y);
        }
        path.closePath();
    }

    private void paintStaticCartographyBackground(Graphics2D graphics, RenderViewKey renderViewKey) {
        if (!canCacheRenderLayer(renderViewKey.width(), renderViewKey.height())) {
            backgroundRenderCache.clear();
            drawMapBackground(graphics, renderViewKey.width(), renderViewKey.height());
            return;
        }
        BufferedImage background = backgroundRenderCache.getOrRender(renderViewKey,
              renderViewKey.width(), renderViewKey.height(),
              layerGraphics -> drawMapBackground(layerGraphics, renderViewKey.width(), renderViewKey.height()));
        graphics.drawImage(background, 0, 0, null);
    }

    private void paintStaticTerritoryLayer(Graphics2D graphics, TerritoryAtlas atlas,
          TerritoryRenderKey renderKey, double alpha) {
        if (!canCacheRenderLayer(renderKey.viewKey().width(), renderKey.viewKey().height())) {
            territoryRenderCache.clear();
            paintLayerWithAlpha(graphics, alpha, layerGraphics -> drawTerritoryLayer(layerGraphics, atlas));
            return;
        }
        BufferedImage territory = territoryRenderCache.getOrRender(renderKey,
              renderKey.viewKey().width(), renderKey.viewKey().height(),
              layerGraphics -> drawTerritoryLayer(layerGraphics, atlas));
        drawRenderLayer(graphics, territory, alpha);
    }

    private void paintStaticFactionLogoLayer(Graphics2D graphics, TerritoryAtlas atlas,
          FactionLogoRenderKey renderKey, double alpha) {
        RenderViewKey viewKey = renderKey.territoryKey().viewKey();
        if (!canCacheRenderLayer(viewKey.width(), viewKey.height())) {
            factionLogoRenderCache.clear();
            paintLayerWithAlpha(graphics, alpha,
                  layerGraphics -> drawFactionLogoLayer(layerGraphics, atlas, renderKey));
            return;
        }
        BufferedImage factionLogos = factionLogoRenderCache.getOrRender(renderKey,
              viewKey.width(), viewKey.height(),
              layerGraphics -> drawFactionLogoLayer(layerGraphics, atlas, renderKey));
        drawRenderLayer(graphics, factionLogos, alpha);
    }

    static boolean canCacheRenderLayer(int width, int height) {
        return (width > 0) && (height > 0) && ((long) width * height <= MAX_CACHED_RENDER_LAYER_PIXELS);
    }

    private FactionLogoRenderKey createFactionLogoRenderKey(TerritoryRenderKey territoryRenderKey) {
        return new FactionLogoRenderKey(territoryRenderKey,
              Math.max(1, UIUtil.scaleForGUI(FACTION_LOGO_MIN_SIZE)),
              Math.max(1, UIUtil.scaleForGUI(FACTION_LOGO_COMPACT_MIN_SIZE)),
              Math.max(1, UIUtil.scaleForGUI(FACTION_LOGO_MAX_SIZE)),
              Math.max(1, UIUtil.scaleForGUI(FACTION_LOGO_COLLISION_PADDING)),
              Math.max(1, UIUtil.scaleForGUI(2)));
    }

    private void drawTerritoryLayer(Graphics2D graphics, TerritoryAtlas atlas) {
        double visibleMinX = Math.min(scr2mapX(0.0), scr2mapX(mapPanel.getWidth()));
        double visibleMaxX = Math.max(scr2mapX(0.0), scr2mapX(mapPanel.getWidth()));
        double visibleMinY = Math.min(scr2mapY(0.0), scr2mapY(mapPanel.getHeight()));
        double visibleMaxY = Math.max(scr2mapY(0.0), scr2mapY(mapPanel.getHeight()));
        AffineTransform mapToScreen = getMap2ScrTransform();
        TerritoryVisualProfile visualProfile = TerritoryVisualProfile.create(conf.scale);
        for (TerritoryContour contour : atlas.contours()) {
            if ((contour.maxMapX() < visibleMinX) || (contour.minMapX() > visibleMaxX)
                  || (contour.maxMapY() < visibleMinY) || (contour.minMapY() > visibleMaxY)) {
                continue;
            }
            paintTerritoryContour(graphics, contour, mapToScreen, visualProfile);
        }
    }

    static void paintTerritoryContour(Graphics2D graphics, TerritoryContour contour,
          AffineTransform mapToScreen, TerritoryVisualProfile visualProfile) {
        if (contour.semantic() == TerritorySemantic.UNCLAIMED_EXTERIOR) {
            return;
        }

        Graphics2D territoryGraphics = (Graphics2D) graphics.create();
        try {
            territoryGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                  RenderingHints.VALUE_ANTIALIAS_ON);
            Shape screenShape = mapToScreen.createTransformedShape(contour.shape());
            if (contour.semantic() == TerritorySemantic.UNCLAIMED_POCKET) {
                territoryGraphics.setPaint(TERRITORY_POCKET_FILL);
            } else {
                territoryGraphics.setPaint(contour.paint());
            }
            territoryGraphics.fill(screenShape);

            switch (contour.semantic()) {
                case SOVEREIGN -> drawSovereignBoundary(territoryGraphics, screenShape,
                      contour.factions().getFirst().getColor());
                case DISPUTED -> drawDisputedTerritory(territoryGraphics, screenShape, contour.factions(),
                      visualProfile.secondaryDetailAlpha());
                case UNCLAIMED_POCKET -> drawUnclaimedPocketBoundary(territoryGraphics, screenShape,
                      visualProfile.secondaryDetailAlpha());
                case ENCLAVE -> drawEnclaveBoundary(territoryGraphics, screenShape,
                      contour.factions().getFirst().getColor(), visualProfile.secondaryDetailAlpha());
                case UNCLAIMED_EXTERIOR -> {
                }
            }
        } finally {
            territoryGraphics.dispose();
        }
    }

    private static void drawSovereignBoundary(Graphics2D graphics, Shape shape, Color factionColor) {
        graphics.setPaint(TERRITORY_BORDER_DARK);
        graphics.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(shape);
        graphics.setPaint(withAlpha(factionColor, 205));
        graphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(shape);
    }

    private static void drawDisputedTerritory(Graphics2D graphics, Shape shape, List<Faction> factions,
          double secondaryDetailAlpha) {
        if (secondaryDetailAlpha > 0.0) {
            paintDisputedHatch(graphics, shape, factions, secondaryDetailAlpha);
        }
        graphics.setPaint(TERRITORY_BORDER_DARK);
        graphics.setStroke(new BasicStroke(3.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(shape);
        graphics.setPaint(TERRITORY_NEUTRAL_EDGE);
        graphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0,
              new float[] { 7.0f, 4.0f }, 0));
        graphics.draw(shape);
    }

    private static void paintDisputedHatch(Graphics2D graphics, Shape shape, List<Faction> factions,
          double detailAlpha) {
        Graphics2D hatchGraphics = (Graphics2D) graphics.create();
        try {
            Rectangle2D hatchBounds = shape.getBounds2D();
            Rectangle clipBounds = graphics.getClipBounds();
            if (clipBounds != null) {
                hatchBounds = hatchBounds.createIntersection(clipBounds);
            }
            if (hatchBounds.isEmpty()) {
                return;
            }
            hatchGraphics.clip(shape);
            hatchGraphics.setComposite(deriveCompositeWithAlpha(hatchGraphics.getComposite(), detailAlpha));
            hatchGraphics.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            double diagonalSpan = hatchBounds.getHeight();
            int hatchIndex = 0;
            for (double hatchX = hatchBounds.getMinX() - diagonalSpan;
                  hatchX <= hatchBounds.getMaxX(); hatchX += 8.0) {
                Color factionColor = factions.get(hatchIndex % factions.size()).getColor();
                hatchGraphics.setPaint(withAlpha(factionColor, 220));
                hatchGraphics.draw(new Line2D.Double(hatchX, hatchBounds.getMaxY(),
                      hatchX + diagonalSpan, hatchBounds.getMinY()));
                hatchIndex++;
            }
        } finally {
            hatchGraphics.dispose();
        }
    }

    private static void drawUnclaimedPocketBoundary(Graphics2D graphics, Shape shape, double detailAlpha) {
        if (detailAlpha <= 0.0) {
            return;
        }
        graphics.setComposite(deriveCompositeWithAlpha(graphics.getComposite(), detailAlpha));
        graphics.setPaint(TERRITORY_NEUTRAL_EDGE);
        graphics.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
              new float[] { 1.0f, 5.0f }, 0));
        graphics.draw(shape);
    }

    private static void drawEnclaveBoundary(Graphics2D graphics, Shape shape, Color factionColor,
          double detailAlpha) {
        drawSovereignBoundary(graphics, shape, factionColor);
        if (detailAlpha <= 0.0) {
            return;
        }
        graphics.setComposite(deriveCompositeWithAlpha(graphics.getComposite(), detailAlpha));
        graphics.setPaint(withAlpha(factionColor, 180));
        graphics.setStroke(new BasicStroke(5.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(shape);
        graphics.setPaint(TERRITORY_BORDER_DARK);
        graphics.setStroke(new BasicStroke(2.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(shape);
        graphics.setPaint(withAlpha(factionColor, 225));
        graphics.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(shape);
    }

    private void prepareStaticCartography() {
        if (campaign != null) {
            prepareStaticCartography(campaign.getLocalDate());
        }
    }

    private void prepareStaticCartography(LocalDate date) {
        TerritoryDataKey requestedKey = new TerritoryDataKey(date, cartographyDataRevision);
        if (preparedTerritoryAtlas.get(requestedKey) != null) {
            return;
        }

        TerritoryDataKey previousKey = preparedTerritoryAtlas.getKey();
        if ((previousKey != null) && (previousKey.date().getYear() != date.getYear())) {
            clearFactionLogoImages();
        }
        preparedTerritoryAtlas.prepare(requestedKey, () -> buildTerritoryAtlas(date));
        territoryRenderCache.clear();
        factionLogoRenderCache.clear();
    }

    private @Nullable TerritoryAtlas getPreparedTerritoryAtlas(LocalDate date) {
        TerritoryDataKey requestedKey = new TerritoryDataKey(date, cartographyDataRevision);
        TerritoryAtlas atlas = preparedTerritoryAtlas.get(requestedKey);
        if (atlas == null) {
            scheduleTerritoryPreparation(requestedKey);
        }
        return atlas;
    }

    private void scheduleTerritoryPreparation(TerritoryDataKey requestedKey) {
        territoryPreparationQueue.request(requestedKey);
    }

    void requestStaticCartographyPreparation() {
        if (campaign != null) {
            scheduleTerritoryPreparation(new TerritoryDataKey(campaign.getLocalDate(), cartographyDataRevision));
        }
    }

    private void prepareRequestedStaticCartography(TerritoryDataKey requestedKey) {
        if ((campaign == null) || (requestedKey.dataRevision() != cartographyDataRevision)
              || !requestedKey.date().equals(campaign.getLocalDate())) {
            return;
        }
        prepareStaticCartography(requestedKey.date());
        mapPanel.repaint();
    }

    void clearRenderLayerCaches() {
        backgroundRenderCache.clear();
        territoryRenderCache.clear();
        factionLogoRenderCache.clear();
    }

    RenderCacheDiagnostics getRenderCacheDiagnostics() {
        return new RenderCacheDiagnostics(backgroundRenderCache.getRenderCount(),
              territoryRenderCache.getRenderCount(), factionLogoRenderCache.getRenderCount(),
              preparedTerritoryAtlas.getPreparationCount(), backgroundRenderCache.hasImage(),
              territoryRenderCache.hasImage(), factionLogoRenderCache.hasImage());
    }

    private TerritoryAtlas buildTerritoryAtlas(LocalDate date) {
        if (systems.isEmpty()) {
            return new TerritoryAtlas(date, 0, -1, 0, -1, Map.of(), List.of(), List.of());
        }

        double systemMinX = systems.stream().mapToDouble(PlanetarySystem::getX).min().orElse(0.0);
        double systemMaxX = systems.stream().mapToDouble(PlanetarySystem::getX).max().orElse(0.0);
        double systemMinY = systems.stream().mapToDouble(PlanetarySystem::getY).min().orElse(0.0);
        double systemMaxY = systems.stream().mapToDouble(PlanetarySystem::getY).max().orElse(0.0);
        int minColumn = (int) Math.floor(systemMinX / TERRITORY_HEX_SPACING_X) - TERRITORY_HEX_MARGIN;
        int maxColumn = (int) Math.ceil(systemMaxX / TERRITORY_HEX_SPACING_X) + TERRITORY_HEX_MARGIN;
        int minRow = (int) Math.floor(systemMinY / TERRITORY_HEX_SIZE) - TERRITORY_HEX_MARGIN;
        int maxRow = (int) Math.ceil(systemMaxY / TERRITORY_HEX_SIZE) + TERRITORY_HEX_MARGIN;
        Faction independentFaction = Factions.getInstance().getFaction("IND");
        Map<TerritoryHex, TerritoryCell> cells = new HashMap<>();

        for (int column = minColumn; column <= maxColumn; column++) {
            for (int row = minRow; row <= maxRow; row++) {
                TerritoryHex hex = new TerritoryHex(column, row);
                double centerX = column * TERRITORY_HEX_SPACING_X;
                double centerY = row * TERRITORY_HEX_SIZE
                      + (column % 2) * TERRITORY_HEX_SIZE / 2.0;
                List<Faction> factions = classifyTerritoryHex(centerX, centerY, date, independentFaction);
                cells.put(hex, new TerritoryCell(hex, centerX, centerY, factions));
            }
        }
        List<TerritoryContour> contours = buildTerritoryContours(cells);
        List<TerritoryComponent> components = buildTerritoryComponents(cells);
        return new TerritoryAtlas(date, minColumn, maxColumn, minRow, maxRow, cells, contours, components);
    }

    static List<TerritoryContour> buildTerritoryContours(Map<TerritoryHex, TerritoryCell> cells) {
        List<TerritoryCell> orderedCells = cells.values().stream()
              .sorted(Comparator.comparingInt((TerritoryCell cell) -> cell.hex().column())
                    .thenComparingInt(cell -> cell.hex().row()))
              .toList();
        Set<TerritoryHex> visited = new HashSet<>();
        List<TerritoryContour> contours = new ArrayList<>();

        for (TerritoryCell seed : orderedCells) {
            if (!visited.add(seed.hex())) {
                continue;
            }
            List<TerritoryCell> regionCells = new ArrayList<>();
            List<TerritoryHex> pending = new ArrayList<>();
            pending.add(seed.hex());
            for (int pendingIndex = 0; pendingIndex < pending.size(); pendingIndex++) {
                TerritoryHex hex = pending.get(pendingIndex);
                TerritoryCell cell = cells.get(hex);
                regionCells.add(cell);
                for (TerritoryHex neighborHex : getTerritoryNeighbors(hex)) {
                    TerritoryCell neighbor = cells.get(neighborHex);
                    if ((neighbor != null) && seed.factions().equals(neighbor.factions())
                          && visited.add(neighborHex)) {
                        pending.add(neighborHex);
                    }
                }
            }

            GeneralPath mergedCells = new GeneralPath();
            GeneralPath cellPath = new GeneralPath();
            for (TerritoryCell cell : regionCells) {
                setupHexPath(cellPath, cell.centerX(), cell.centerY(), TERRITORY_HEX_SIZE / 2.0);
                mergedCells.append(cellPath, false);
            }
            Area contourArea = new Area(mergedCells);
            contourArea.add(new Area(TERRITORY_CONTOUR_SOFTENING_STROKE.createStrokedShape(contourArea)));
            Rectangle2D bounds = contourArea.getBounds2D();
            TerritorySemantic semantic = classifyTerritorySemantic(seed.factions(), regionCells, cells);
            contours.add(new TerritoryContour(List.copyOf(seed.factions()), semantic, contourArea,
                  createTerritoryPaint(seed.factions()), regionCells.size(), bounds.getMinX(), bounds.getMaxX(),
                  bounds.getMinY(), bounds.getMaxY()));
        }
        return List.copyOf(contours);
    }

    private static TerritorySemantic classifyTerritorySemantic(List<Faction> factions,
          List<TerritoryCell> regionCells, Map<TerritoryHex, TerritoryCell> cells) {
        if (factions.size() > 1) {
            return TerritorySemantic.DISPUTED;
        }

        Set<TerritoryHex> regionHexes = new HashSet<>();
        for (TerritoryCell cell : regionCells) {
            regionHexes.add(cell.hex());
        }
        boolean touchesAtlasEdge = regionCells.stream()
              .flatMap(cell -> getTerritoryNeighbors(cell.hex()).stream())
              .anyMatch(neighborHex -> !cells.containsKey(neighborHex));
        if (factions.isEmpty()) {
            return touchesAtlasEdge ? TerritorySemantic.UNCLAIMED_EXTERIOR
                  : TerritorySemantic.UNCLAIMED_POCKET;
        }
        if (touchesAtlasEdge) {
            return TerritorySemantic.SOVEREIGN;
        }

        Faction owner = factions.getFirst();
        Faction surroundingOwner = null;
        for (TerritoryCell cell : regionCells) {
            for (TerritoryHex neighborHex : getTerritoryNeighbors(cell.hex())) {
                if (regionHexes.contains(neighborHex)) {
                    continue;
                }
                TerritoryCell neighbor = cells.get(neighborHex);
                if ((neighbor == null) || (neighbor.factions().size() != 1)
                      || owner.equals(neighbor.factions().getFirst())) {
                    return TerritorySemantic.SOVEREIGN;
                }
                if (surroundingOwner == null) {
                    surroundingOwner = neighbor.factions().getFirst();
                } else if (!surroundingOwner.equals(neighbor.factions().getFirst())) {
                    return TerritorySemantic.SOVEREIGN;
                }
            }
        }
        return surroundingOwner == null ? TerritorySemantic.SOVEREIGN : TerritorySemantic.ENCLAVE;
    }

    private static Paint createTerritoryPaint(List<Faction> factions) {
        if (factions.isEmpty()) {
            return new Color(0.0f, 0.0f, 0.0f, 0.25f);
        }
        if (factions.size() == 1) {
            Color factionColor = factions.getFirst().getColor();
            float[] colorComponents = factionColor.getComponents(null);
            return new Color(colorComponents[0], colorComponents[1], colorComponents[2], 0.25f);
        }

        int factionCount = factions.size();
        float[] paintFractions = new float[factionCount * 2];
        Color[] paintColors = new Color[factionCount * 2];
        for (int factionIndex = 0; factionIndex < factionCount; factionIndex++) {
            paintFractions[factionIndex * 2] = factionIndex * (1.0f / factionCount) + 0.001f;
            paintFractions[factionIndex * 2 + 1] = (factionIndex + 1) * (1.0f / factionCount);
            Color factionColor = factions.get(factionIndex).getColor();
            float[] colorComponents = factionColor.getComponents(null);
            Color translucentColor = new Color(colorComponents[0], colorComponents[1], colorComponents[2], 0.25f);
            paintColors[factionIndex * 2] = translucentColor;
            paintColors[factionIndex * 2 + 1] = translucentColor;
        }
        paintFractions[0] = 0.0f;
        return new LinearGradientPaint(new Point2D.Double(0.0, 0.0), new Point2D.Double(6.0, 6.0),
              paintFractions, paintColors, CycleMethod.REPEAT);
    }

    private List<Faction> classifyTerritoryHex(double centerX, double centerY, LocalDate date,
          Faction independentFaction) {
        GeneralPath path = new GeneralPath();
        setupHexPath(path, centerX, centerY, TERRITORY_HEX_SIZE / 2.0);
        List<PlanetarySystem> nearbySystems = Systems.getInstance().getNearbySystems(centerX, centerY,
              (int) Math.round(TERRITORY_HEX_SIZE * 1.3));
        Set<Faction> hexFactions = new HashSet<>();

        for (PlanetarySystem system : nearbySystems) {
            if (!isSystemEmpty(system, date) && path.contains(system.getX(), system.getY())) {
                Set<Faction> factions = new HashSet<>(system.getFactionSet(date));
                factions.remove(independentFaction);
                hexFactions.addAll(factions);
            }
        }
        if (hexFactions.isEmpty()) {
            for (PlanetarySystem system : nearbySystems) {
                if (!isSystemEmpty(system, date)) {
                    hexFactions.addAll(new HashSet<>(system.getFactionSet(date)));
                }
            }
        }
        if (hexFactions.size() > 1) {
            hexFactions.remove(independentFaction);
        }

        return hexFactions.stream()
              .sorted(Comparator.comparing(Faction::getShortName))
              .toList();
    }

    private List<TerritoryComponent> buildTerritoryComponents(Map<TerritoryHex, TerritoryCell> cells) {
        List<TerritoryCell> singleOwnerCells = cells.values().stream()
              .filter(cell -> cell.factions().size() == 1)
              .sorted(Comparator.comparingInt((TerritoryCell cell) -> cell.hex().column())
                    .thenComparingInt(cell -> cell.hex().row()))
              .toList();
        Set<TerritoryHex> visited = new HashSet<>();
        List<TerritoryComponent> components = new ArrayList<>();

        for (TerritoryCell seed : singleOwnerCells) {
            if (!visited.add(seed.hex())) {
                continue;
            }
            Faction faction = seed.factions().getFirst();
            List<TerritoryCell> componentCells = new ArrayList<>();
            List<TerritoryHex> pending = new ArrayList<>();
            pending.add(seed.hex());
            for (int pendingIndex = 0; pendingIndex < pending.size(); pendingIndex++) {
                TerritoryHex hex = pending.get(pendingIndex);
                TerritoryCell cell = cells.get(hex);
                componentCells.add(cell);
                for (TerritoryHex neighborHex : getTerritoryNeighbors(hex)) {
                    TerritoryCell neighbor = cells.get(neighborHex);
                    if ((neighbor != null) && (neighbor.factions().size() == 1)
                          && faction.equals(neighbor.factions().getFirst()) && visited.add(neighborHex)) {
                        pending.add(neighborHex);
                    }
                }
            }
            components.add(createTerritoryComponent(faction, componentCells));
        }
        return List.copyOf(components);
    }

    private TerritoryComponent createTerritoryComponent(Faction faction, List<TerritoryCell> cells) {
        Set<TerritoryHex> componentHexes = new HashSet<>();
        double centroidX = 0.0;
        double centroidY = 0.0;
        double minMapX = Double.POSITIVE_INFINITY;
        double maxMapX = Double.NEGATIVE_INFINITY;
        double minMapY = Double.POSITIVE_INFINITY;
        double maxMapY = Double.NEGATIVE_INFINITY;
        for (TerritoryCell cell : cells) {
            componentHexes.add(cell.hex());
            centroidX += cell.centerX();
            centroidY += cell.centerY();
            minMapX = Math.min(minMapX, cell.centerX() - TERRITORY_HEX_RADIUS);
            maxMapX = Math.max(maxMapX, cell.centerX() + TERRITORY_HEX_RADIUS);
            minMapY = Math.min(minMapY, cell.centerY() - TERRITORY_HEX_SIZE / 2.0);
            maxMapY = Math.max(maxMapY, cell.centerY() + TERRITORY_HEX_SIZE / 2.0);
        }
        centroidX /= cells.size();
        centroidY /= cells.size();

        Map<TerritoryHex, Integer> boundaryDistance = new HashMap<>();
        List<TerritoryHex> pending = new ArrayList<>();
        for (TerritoryCell cell : cells) {
            boolean boundary = getTerritoryNeighbors(cell.hex()).stream()
                  .anyMatch(neighbor -> !componentHexes.contains(neighbor));
            if (boundary) {
                boundaryDistance.put(cell.hex(), 0);
                pending.add(cell.hex());
            }
        }
        for (int pendingIndex = 0; pendingIndex < pending.size(); pendingIndex++) {
            TerritoryHex hex = pending.get(pendingIndex);
            int nextDistance = boundaryDistance.get(hex) + 1;
            for (TerritoryHex neighbor : getTerritoryNeighbors(hex)) {
                if (componentHexes.contains(neighbor) && !boundaryDistance.containsKey(neighbor)) {
                    boundaryDistance.put(neighbor, nextDistance);
                    pending.add(neighbor);
                }
            }
        }

        TerritoryCell anchor = null;
        int anchorDepth = -1;
        double anchorCentroidDistance = Double.POSITIVE_INFINITY;
        for (TerritoryCell cell : cells) {
            int depth = boundaryDistance.getOrDefault(cell.hex(), 0);
            double centroidDistance = Point2D.distanceSq(cell.centerX(), cell.centerY(), centroidX, centroidY);
            if ((depth > anchorDepth)
                  || ((depth == anchorDepth) && (centroidDistance < anchorCentroidDistance))
                  || ((depth == anchorDepth) && (centroidDistance == anchorCentroidDistance)
                        && isHexBefore(cell.hex(), anchor.hex()))) {
                anchor = cell;
                anchorDepth = depth;
                anchorCentroidDistance = centroidDistance;
            }
        }
        return new TerritoryComponent(faction, anchor.hex(), anchor.centerX(), anchor.centerY(), cells.size(),
              anchorDepth, minMapX, maxMapX, minMapY, maxMapY);
    }

    private static boolean isHexBefore(TerritoryHex first, TerritoryHex second) {
        return (first.column() < second.column())
              || ((first.column() == second.column()) && (first.row() < second.row()));
    }

    private static List<TerritoryHex> getTerritoryNeighbors(TerritoryHex hex) {
        int column = hex.column();
        int row = hex.row();
        int leftColumn = column - 1;
        int rightColumn = column + 1;
        double currentOffset = (column % 2) / 2.0;
        double leftOffset = (leftColumn % 2) / 2.0;
        double rightOffset = (rightColumn % 2) / 2.0;
        int leftLowerRow = row + (int) Math.round(currentOffset - leftOffset - 0.5);
        int leftUpperRow = row + (int) Math.round(currentOffset - leftOffset + 0.5);
        int rightLowerRow = row + (int) Math.round(currentOffset - rightOffset - 0.5);
        int rightUpperRow = row + (int) Math.round(currentOffset - rightOffset + 0.5);
        return List.of(new TerritoryHex(leftColumn, leftLowerRow), new TerritoryHex(leftColumn, leftUpperRow),
              new TerritoryHex(column, row - 1), new TerritoryHex(column, row + 1),
              new TerritoryHex(rightColumn, rightLowerRow), new TerritoryHex(rightColumn, rightUpperRow));
    }

    private double getFactionMapModeAlpha() {
        if (!mapModeAnimating) {
            return targetMapMode == MapMode.FACTION ? 1.0 : 0.0;
        }
        double previousAlpha = previousMapMode == MapMode.FACTION ? 1.0 - mapModeAnimationProgress : 0.0;
        double targetAlpha = targetMapMode == MapMode.FACTION ? mapModeAnimationProgress : 0.0;
        return previousAlpha + targetAlpha;
    }

    private void drawFactionLogoLayer(Graphics2D graphics, TerritoryAtlas atlas,
          FactionLogoRenderKey renderKey) {
        int majorMinimumLogoSize = renderKey.majorMinimumSize();
        int compactMinimumLogoSize = renderKey.compactMinimumSize();
        int maximumLogoSize = Math.max(majorMinimumLogoSize, renderKey.maximumSize());
        int collisionPadding = renderKey.collisionPadding();
        double projectedCellArea = TERRITORY_HEX_SPACING_X * TERRITORY_HEX_SIZE
              * conf.scale * conf.scale;
        List<FactionLogoCandidate> candidates = new ArrayList<>();

        for (TerritoryComponent component : atlas.components()) {
            int priority = getFactionLogoPriority(component.faction());
            if (priority < 0) {
                continue;
            }
            double anchorX = map2scrX(component.anchorX());
            double anchorY = map2scrY(component.anchorY());
            if ((anchorX < 0.0) || (anchorX > mapPanel.getWidth())
                  || (anchorY < 0.0) || (anchorY > mapPanel.getHeight())) {
                continue;
            }

            double projectedArea = component.cellCount() * projectedCellArea;
            int minimumLogoSize = priority == 0 ? majorMinimumLogoSize : compactMinimumLogoSize;
            double minimumAreaFactor = switch (priority) {
                case 0 -> 4.0;
                case 1 -> 2.25;
                default -> 3.0;
            };
            double minimumExtentFactor = switch (priority) {
                case 0 -> 1.25;
                case 1 -> 0.9;
                default -> 1.0;
            };
            double minimumProjectedArea = minimumLogoSize * minimumLogoSize * minimumAreaFactor;
            double projectedWidth = (component.maxMapX() - component.minMapX()) * conf.scale;
            double projectedHeight = (component.maxMapY() - component.minMapY()) * conf.scale;
            if ((projectedArea < minimumProjectedArea) || (projectedWidth < minimumLogoSize * minimumExtentFactor)
                || (projectedHeight < minimumLogoSize * minimumExtentFactor)) {
                continue;
            }

            double desiredSize = Math.sqrt(projectedArea) * (priority == 0 ? 0.5 : 0.65);
            double containmentFactor = priority == 0 ? 0.68 : 0.85;
            double containedSize = Math.min(projectedWidth * containmentFactor,
                projectedHeight * containmentFactor);
            int logoSize = quantizeLogoSize(Math.min(Math.min(desiredSize, containedSize), maximumLogoSize),
                  minimumLogoSize, maximumLogoSize);
            if (logoSize < minimumLogoSize) {
                continue;
            }

            FactionLogoKey logoKey = new FactionLogoKey(renderKey.territoryKey().date().getYear(),
                component.faction().getShortName());
            FactionLogoImage sourceImage = getFactionLogoImage(component.faction(), logoKey);
            if (sourceImage == null) {
                continue;
            }
            int sourceWidth = sourceImage.tinted().getWidth();
            int sourceHeight = sourceImage.tinted().getHeight();
            int targetWidth = sourceWidth >= sourceHeight
                  ? logoSize
                  : Math.max(1, (int) Math.round(logoSize * (double) sourceWidth / sourceHeight));
            int targetHeight = sourceHeight >= sourceWidth
                  ? logoSize
                  : Math.max(1, (int) Math.round(logoSize * (double) sourceHeight / sourceWidth));
            FactionLogoImage scaledImage = getScaledFactionLogoImage(logoKey, sourceImage, targetWidth, targetHeight);
            Rectangle2D.Double bounds = new Rectangle2D.Double(anchorX - targetWidth / 2.0,
                  anchorY - targetHeight / 2.0, targetWidth, targetHeight);
            candidates.add(new FactionLogoCandidate(component, priority, projectedArea, bounds, scaledImage));
        }

        candidates.sort(Comparator.comparingInt(FactionLogoCandidate::priority)
              .thenComparing(Comparator.comparingDouble(FactionLogoCandidate::projectedArea).reversed())
              .thenComparing(candidate -> candidate.component().faction().getShortName())
              .thenComparingInt(candidate -> candidate.component().anchorHex().column())
              .thenComparingInt(candidate -> candidate.component().anchorHex().row()));
        List<Rectangle2D.Double> acceptedBounds = new ArrayList<>();
        int shadowOffset = renderKey.shadowOffset();
        for (FactionLogoCandidate candidate : candidates) {
            Rectangle2D.Double paddedBounds = new Rectangle2D.Double(
                  candidate.bounds().x - collisionPadding, candidate.bounds().y - collisionPadding,
                  candidate.bounds().width + collisionPadding * 2.0,
                  candidate.bounds().height + collisionPadding * 2.0);
            if (acceptedBounds.stream().anyMatch(accepted -> accepted.intersects(paddedBounds))) {
                continue;
            }
            acceptedBounds.add(paddedBounds);
            int imageX = (int) Math.round(candidate.bounds().x);
            int imageY = (int) Math.round(candidate.bounds().y);
            graphics.drawImage(candidate.image().shadow(), imageX + shadowOffset, imageY + shadowOffset, null);
            graphics.drawImage(candidate.image().tinted(), imageX, imageY, null);
        }
    }

    private static int getFactionLogoPriority(Faction faction) {
        if (faction.isIndependent() || faction.is(FactionTag.ABANDONED)) {
            return -1;
        }
        if (faction.isMajorOrSuperPower() || faction.isClan()) {
            return 0;
        }
        if (faction.isMinorPower() || faction.isPeriphery() || faction.isDeepPeriphery()) {
            return 1;
        }
        return faction.isPirate() ? 2 : -1;
    }

    private static int quantizeLogoSize(double size, int minimumSize, int maximumSize) {
        if (size < minimumSize) {
            return 0;
        }
        int quantizedSize = (int) Math.round(size / FACTION_LOGO_SIZE_STEP) * FACTION_LOGO_SIZE_STEP;
        return Math.clamp(quantizedSize, minimumSize, maximumSize);
    }

    private @Nullable FactionLogoImage getFactionLogoImage(Faction faction, FactionLogoKey logoKey) {
        FactionLogoImage cachedImage = factionLogoImages.get(logoKey);
        if ((cachedImage != null) || missingFactionLogoImages.contains(logoKey)) {
            return cachedImage;
        }

        try {
            ImageIcon icon = Factions.getFactionLogo(logoKey.gameYear(), logoKey.factionCode());
            Image sourceImage = icon.getImage();
            int sourceWidth = icon.getIconWidth();
            int sourceHeight = icon.getIconHeight();
            if ((sourceImage == null) || (sourceWidth <= 0) || (sourceHeight <= 0)) {
                missingFactionLogoImages.add(logoKey);
                return null;
            }

            BufferedImage source = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sourceGraphics = source.createGraphics();
            sourceGraphics.drawImage(sourceImage, 0, 0, null);
            sourceGraphics.dispose();
            Color factionColor = faction.getColor();
            int tintedRed = (factionColor.getRed() + 510) / 3;
            int tintedGreen = (factionColor.getGreen() + 510) / 3;
            int tintedBlue = (factionColor.getBlue() + 510) / 3;
            BufferedImage tinted = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
            BufferedImage shadow = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
            for (int imageY = 0; imageY < sourceHeight; imageY++) {
                for (int imageX = 0; imageX < sourceWidth; imageX++) {
                    int alpha = source.getRGB(imageX, imageY) >>> 24;
                    if (alpha == 0) {
                        continue;
                    }
                    tinted.setRGB(imageX, imageY,
                          (alpha << 24) | (tintedRed << 16) | (tintedGreen << 8) | tintedBlue);
                    int shadowAlpha = (int) Math.round(alpha * 0.72);
                    shadow.setRGB(imageX, imageY, shadowAlpha << 24);
                }
            }
            FactionLogoImage image = new FactionLogoImage(tinted, shadow);
            factionLogoImages.put(logoKey, image);
            return image;
        } catch (RuntimeException exception) {
            missingFactionLogoImages.add(logoKey);
            return null;
        }
    }

    private FactionLogoImage getScaledFactionLogoImage(FactionLogoKey logoKey, FactionLogoImage sourceImage,
          int width, int height) {
        ScaledFactionLogoKey scaledKey = new ScaledFactionLogoKey(logoKey, width, height);
        return scaledFactionLogoImages.computeIfAbsent(scaledKey, ignored -> new FactionLogoImage(
              scaleFactionLogoImage(sourceImage.tinted(), width, height),
              scaleFactionLogoImage(sourceImage.shadow(), width, height)));
    }

    private static BufferedImage scaleFactionLogoImage(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    private void clearFactionLogoImages() {
        factionLogoImages.clear();
        scaledFactionLogoImages.clear();
        missingFactionLogoImages.clear();
        factionLogoRenderCache.clear();
    }

        private void drawHpgNetworkLayer(Graphics2D graphics, Arc2D.Double arc, double size, Stroke thick,
            Stroke thin, Stroke dashed, Stroke dotted, double ambientElapsedSeconds, double trafficAlpha) {
        Collection<HPGLink> hpgNetwork = Systems.getInstance().getHPGNetwork(now);

        for (PlanetarySystem system : systems) {
            if (isSystemVisible(system, true)) {
                double x = map2scrX(system.getX());
                double y = map2scrY(system.getY());
                HPGRating hpgRating = ObjectUtility.nonNull(system.getHPG(now), HPGRating.X);
                if (hpgRating == HPGRating.A) {
                    graphics.setPaint(Color.CYAN);
                    arc.setArcByCenter(x, y, size * 1.6, 0, 360, Arc2D.OPEN);
                    graphics.setStroke(thick);
                    graphics.draw(arc);
                }
                if (hpgRating == HPGRating.A || hpgRating == HPGRating.B) {
                    graphics.setPaint(Color.BLUE);
                    arc.setArcByCenter(x, y, size * 1.3, 0, 360, Arc2D.OPEN);
                    graphics.setStroke(thin);
                    graphics.draw(arc);
                }
                if (hpgRating == HPGRating.C) {
                    graphics.setPaint(Color.ORANGE);
                    arc.setArcByCenter(x, y, size * 1.3, 0, 360, Arc2D.OPEN);
                    graphics.setStroke(dashed);
                    graphics.draw(arc);
                }
                if (hpgRating == HPGRating.D) {
                    graphics.setPaint(Color.RED);
                    arc.setArcByCenter(x, y, size * 1.3, 0, 360, Arc2D.OPEN);
                    graphics.setStroke(dotted);
                    graphics.draw(arc);
                }
            }
        }
        for (HPGLink link : hpgNetwork) {
            PlanetarySystem primary = link.primary();
            PlanetarySystem secondary = link.secondary();
            if (isSystemVisible(primary, false) || isSystemVisible(secondary, false)) {
                if (link.rating() == HPGRating.A) {
                    graphics.setPaint(Color.CYAN);
                    graphics.setStroke(thick);
                    graphics.draw(new Line2D.Double(map2scrX(primary.getX()), map2scrY(primary.getY()),
                          map2scrX(secondary.getX()), map2scrY(secondary.getY())));
                }
                if (link.rating() == HPGRating.B) {
                    graphics.setPaint(Color.BLUE);
                    graphics.setStroke(dashed);
                    graphics.draw(new Line2D.Double(map2scrX(primary.getX()), map2scrY(primary.getY()),
                          map2scrX(secondary.getX()), map2scrY(secondary.getY())));
                }
            }
        }
        if (trafficAlpha > 0.0) {
            paintLayerWithAlpha(graphics, trafficAlpha,
                  trafficGraphics -> drawHpgTrafficPulses(trafficGraphics, hpgNetwork, ambientElapsedSeconds));
        }
    }

    private void drawHpgTrafficPulses(Graphics2D graphics, Collection<HPGLink> hpgNetwork,
          double ambientElapsedSeconds) {
        for (HPGLink link : hpgNetwork) {
            if ((link.rating() != HPGRating.A) && (link.rating() != HPGRating.B)) {
                continue;
            }
            PlanetarySystem primary = link.primary();
            PlanetarySystem secondary = link.secondary();
            if (!isSystemVisible(primary, false) && !isSystemVisible(secondary, false)) {
                continue;
            }

            boolean primaryFirst = primary.getId().compareTo(secondary.getId()) <= 0;
            PlanetarySystem start = primaryFirst ? primary : secondary;
            PlanetarySystem end = primaryFirst ? secondary : primary;
            int linkHash = (31 * getStableHash(start.getId())) + getStableHash(end.getId());
            double cycleDurationSeconds = interpolate(5.0, 8.0,
                  getStableUnit(linkHash, 0x165667b1));
            double cyclePhase = fractionalPart((ambientElapsedSeconds / cycleDurationSeconds)
                  + getStableUnit(linkHash, 0x27d4eb2f));
            double dutyCycle = 0.3;
            if (cyclePhase >= dutyCycle) {
                continue;
            }

            double packetProgress = cyclePhase / dutyCycle;
            double alphaEnvelope = Math.sin(Math.PI * packetProgress);
            int packetAlpha = (int) Math.round((link.rating() == HPGRating.A ? 160.0 : 125.0) * alphaEnvelope);
            if (packetAlpha < 8) {
                continue;
            }
            double packetX = interpolate(map2scrX(start.getX()), map2scrX(end.getX()), packetProgress);
            double packetY = interpolate(map2scrY(start.getY()), map2scrY(end.getY()), packetProgress);
            double packetRadius = link.rating() == HPGRating.A ? 2.1 : 1.7;
            Color packetColor = link.rating() == HPGRating.A ? HPG_A_TRAFFIC_COLOR : HPG_B_TRAFFIC_COLOR;
            graphics.setPaint(withAlpha(packetColor, packetAlpha));
            graphics.fill(new Ellipse2D.Double(packetX - packetRadius, packetY - packetRadius,
                  packetRadius * 2.0, packetRadius * 2.0));
        }
    }

    private PlanetarySystem findHoveredSystem(double systemSize) {
        if ((lastMousePos == null) || (mouseMod == MouseEvent.BUTTON1)) {
            return null;
        }

        PlanetarySystem nearestSystem = null;
        double nearestDistance = Math.max(10.0, systemSize * 2.5);
        for (PlanetarySystem system : systems) {
            if (!isSystemVisible(system, !optEmptySystems.isSelected())) {
                continue;
            }
            double distance = Point2D.distance(lastMousePos.x, lastMousePos.y,
                  map2scrX(system.getX()), map2scrY(system.getY()));
            if (distance <= nearestDistance) {
                nearestSystem = system;
                nearestDistance = distance;
            }
        }
        return nearestSystem;
    }

    private PlanetarySystem findSystemAt(Point point) {
        double systemSize = Math.clamp(1 + (5 * Math.log(conf.scale)), conf.minDotSize, conf.maxDotSize);
        PlanetarySystem nearestSystem = null;
        double nearestDistance = Math.max(10.0, systemSize * 2.5);
        for (PlanetarySystem system : systems) {
            if (!isSystemVisible(system, !optEmptySystems.isSelected())) {
                continue;
            }
            double distance = Point2D.distance(point.x, point.y,
                  map2scrX(system.getX()), map2scrY(system.getY()));
            if (distance <= nearestDistance) {
                nearestSystem = system;
                nearestDistance = distance;
            }
        }
        return nearestSystem;
    }

    private JMenuItem createRoutePlanningMenuItem(String resourceKey, boolean enabled, Runnable action) {
        JMenuItem item = new JMenuItem(resourceMap.getString(resourceKey + ".text"));
        item.setToolTipText(resourceMap.getString(resourceKey + ".toolTipText"));
        item.getAccessibleContext().setAccessibleName(item.getText());
        item.getAccessibleContext().setAccessibleDescription(item.getToolTipText());
        item.setEnabled(enabled);
        item.addActionListener(event -> action.run());
        return item;
    }

        private static void drawIntrinsicStar(Graphics2D graphics, Arc2D.Double arc, PlanetarySystem system,
                    double x, double y, double size, double ambientElapsedSeconds) {
        Color spectralColor = getSpectralColor(system.getStar());
                double luminosityScale = getLuminosityClassVisualScale(system.getStar());
                double shimmer = getAmbientWave(ambientElapsedSeconds, getStableHash(system.getId()), 0x51f15e,
                            3.5, 6.5);
                double auraAlphaScale = 1.0 + (shimmer * 0.045);
          double auraRadius = Math.max(2.0, size * 1.65) * luminosityScale;
          graphics.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) auraRadius,
              new float[] { 0.0f, 0.16f, 0.38f, 0.68f, 1.0f },
              new Color[] {
                                    withAlpha(brighten(spectralColor), scaleAlpha(245, auraAlphaScale)),
                                    withAlpha(spectralColor, scaleAlpha(225, auraAlphaScale)),
                                    withAlpha(spectralColor, scaleAlpha(130, auraAlphaScale)),
                                    withAlpha(spectralColor, scaleAlpha(42, auraAlphaScale)),
                  withAlpha(spectralColor, 0)
              }, CycleMethod.NO_CYCLE));
          arc.setArcByCenter(x, y, auraRadius, 0, 360, Arc2D.OPEN);
          graphics.fill(arc);

          double coreRadius = Math.max(0.65, size * 0.3) * luminosityScale;
          arc.setArcByCenter(x, y, coreRadius, 0, 360, Arc2D.OPEN);
                graphics.setPaint(withAlpha(brighten(spectralColor), scaleAlpha(250, 1.0 + (shimmer * 0.035))));
        graphics.fill(arc);
    }

        private static void drawNavigationContact(Graphics2D graphics, Arc2D.Double arc,
          double x, double y, double size) {
        Color contactColor = new Color(165, 184, 190);
        arc.setArcByCenter(x, y, Math.max(1.5, size * 0.9), 0, 360, Arc2D.OPEN);
        graphics.setPaint(withAlpha(contactColor, 55));
        graphics.fill(arc);
        arc.setArcByCenter(x, y, Math.max(0.8, size * 0.46), 0, 360, Arc2D.OPEN);
        graphics.setPaint(contactColor);
        graphics.fill(arc);
        arc.setArcByCenter(x, y, Math.max(0.35, size * 0.16), 0, 360, Arc2D.OPEN);
        graphics.setPaint(brighten(contactColor));
        graphics.fill(arc);
    }

    private void drawStrategicContact(Graphics2D graphics, Arc2D.Double arc, PlanetarySystem system,
          SystemMarkerLayout layout, boolean systemEmpty, MapMode mapMode) {
        List<Color> contactColors = new ArrayList<>();
        if ((mapMode == MapMode.FACTION) && !systemEmpty) {
            Set<Faction> factions = system.getFactionSet(now);
            if (factions != null) {
                List<Faction> orderedFactions = new ArrayList<>(factions);
                orderedFactions.sort(Comparator.comparing(Faction::getShortName));
                for (Faction faction : orderedFactions) {
                    contactColors.add(faction.getColor());
                }
            }
        } else if (!systemEmpty) {
            contactColors.add(getSystemColor(system, mapMode));
        }
        if (contactColors.isEmpty()) {
            contactColors.add(new Color(105, 120, 128));
        }

        Stroke oldStroke = graphics.getStroke();
        Paint oldPaint = graphics.getPaint();
        try {
            double contactRadius = Math.max(1.8, Math.min(3.2, layout.size() * 0.62));
            double outlineRadius = contactRadius + 0.8;
            arc.setArcByCenter(layout.centerX(), layout.centerY(), outlineRadius, 0, 360, Arc2D.PIE);
            graphics.setPaint(withAlpha(MAP_BACKGROUND_BOTTOM, 225));
            graphics.fill(arc);

            double segmentExtent = 360.0 / contactColors.size();
            for (int colorIndex = 0; colorIndex < contactColors.size(); colorIndex++) {
                arc.setArcByCenter(layout.centerX(), layout.centerY(), contactRadius,
                      90.0 - ((colorIndex + 1) * segmentExtent), segmentExtent, Arc2D.PIE);
                graphics.setPaint(contactColors.get(colorIndex));
                graphics.fill(arc);
            }

            arc.setArcByCenter(layout.centerX(), layout.centerY(), outlineRadius, 0, 360, Arc2D.OPEN);
            graphics.setPaint(withAlpha(Color.WHITE, 90));
            graphics.setStroke(new BasicStroke(0.7f));
            graphics.draw(arc);
        } finally {
            graphics.setStroke(oldStroke);
            graphics.setPaint(oldPaint);
        }
    }

    private Map<String, StrategicMarker> buildStrategicMarkers() {
        return buildStrategicMarkers(campaign.getActiveMissions(false), campaign.getActiveScenarios(),
              campaign::getMission);
    }

    static Map<String, StrategicMarker> buildStrategicMarkers(List<Mission> activeMissions,
          List<Scenario> activeScenarios, java.util.function.IntFunction<Mission> missionLookup) {
        Map<String, StrategicMarker> strategicMarkers = new HashMap<>();
        for (Mission mission : activeMissions) {
            PlanetarySystem system = mission.getSystem();
            if ((system == null) || (system.getId() == null)) {
                continue;
            }
            strategicMarkers.compute(system.getId(), (systemId, marker) -> marker == null
                  ? new StrategicMarker(1, 0)
                  : new StrategicMarker(marker.activeMissionCount() + 1, marker.activeScenarioCount()));
        }

        for (Scenario scenario : activeScenarios) {
            Mission mission = missionLookup.apply(scenario.getMissionId());
            if (mission == null) {
                continue;
            }
            PlanetarySystem system = mission.getSystem();
            if ((system == null) || (system.getId() == null)) {
                continue;
            }
            strategicMarkers.compute(system.getId(), (systemId, marker) -> marker == null
                  ? new StrategicMarker(0, 1)
                  : new StrategicMarker(marker.activeMissionCount(), marker.activeScenarioCount() + 1));
        }
        return Map.copyOf(strategicMarkers);
    }

    static double visibleOperationAlpha(double layerAlpha, SemanticZoomProfile semanticZoom,
          StrategicMarker marker) {
        if (marker == null) {
            return 0.0;
        }
        double semanticAlpha = marker.hasActiveScenario()
              ? semanticZoom.urgentOperationAlpha()
              : semanticZoom.missionOperationAlpha();
        return layerAlpha * semanticAlpha;
    }

    static void drawOperationMarker(Graphics2D graphics, SystemMarkerLayout layout,
          StrategicMarker marker) {
        Stroke oldStroke = graphics.getStroke();
        Paint oldPaint = graphics.getPaint();
        Font oldFont = graphics.getFont();
        try {
            boolean urgent = marker.hasActiveScenario();
            double hierarchyScale = urgent ? 1.2 : 1.0;
            double size = layout.size();
            double flagHeight = Math.max(6.0, size * 0.9) * hierarchyScale;
            double flagWidth = Math.max(8.0, size * 1.15) * hierarchyScale;
            Point2D.Double anchor = layout.operationAnchor();
            double mastX = anchor.x - (flagWidth / 2.0);
            double mastTopY = anchor.y - (flagHeight / 2.0);
            double mastBottomY = anchor.y + (flagHeight / 2.0);

            GeneralPath pennant = new GeneralPath();
            pennant.moveTo(mastX, mastTopY);
            pennant.lineTo(mastX + flagWidth, mastTopY + (flagHeight * 0.5));
            pennant.lineTo(mastX, mastTopY + flagHeight);
            pennant.closePath();
            Line2D.Double mast = new Line2D.Double(mastX, mastTopY, mastX, mastBottomY + 1.5);

            if (urgent) {
                graphics.setPaint(URGENT_OPERATION_COLOR);
                graphics.fill(pennant);
            }
            graphics.setPaint(withAlpha(Color.BLACK, urgent ? 235 : 190));
            graphics.setStroke(new BasicStroke(urgent ? 4.6f : 3.8f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(mast);
            graphics.draw(pennant);
            graphics.setPaint(urgent ? URGENT_OPERATION_COLOR : withAlpha(OPERATION_MARKER_COLOR, 210));
            graphics.setStroke(new BasicStroke(urgent ? 2.5f : 1.8f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(mast);
            graphics.draw(pennant);

            if (marker.activeMissionCount() > 1) {
                String countText = Integer.toString(marker.activeMissionCount());
                Font countFont = oldFont.deriveFont(Font.BOLD,
                      Math.max(9.0f, Math.min(11.0f, oldFont.getSize2D())));
                graphics.setFont(countFont);
                double badgeHeight = Math.max(10.0, graphics.getFontMetrics().getAscent() + 2.0);
                double badgeWidth = Math.max(badgeHeight,
                    graphics.getFontMetrics().stringWidth(countText) + 6.0);
                double badgeX = mastX - badgeWidth - 2.0;
                double badgeY = anchor.y - (badgeHeight / 2.0);
                java.awt.geom.RoundRectangle2D.Double badge = new java.awt.geom.RoundRectangle2D.Double(
                    badgeX, badgeY, badgeWidth, badgeHeight, badgeHeight, badgeHeight);
                graphics.setPaint(withAlpha(Color.BLACK, 225));
                graphics.setStroke(new BasicStroke(3.0f));
                graphics.draw(badge);
                graphics.setPaint(urgent ? URGENT_OPERATION_COLOR : OPERATION_MARKER_COLOR);
                graphics.fill(badge);
                graphics.setPaint(Color.BLACK);
                float countX = (float) (badgeX
                    + ((badgeWidth - graphics.getFontMetrics().stringWidth(countText)) / 2.0));
                float countY = (float) (badgeY
                    + ((badgeHeight - graphics.getFontMetrics().getHeight()) / 2.0)
                    + graphics.getFontMetrics().getAscent());
                graphics.drawString(countText, countX, countY);
            }
        } finally {
            graphics.setFont(oldFont);
            graphics.setStroke(oldStroke);
            graphics.setPaint(oldPaint);
        }
    }

    record StrategicMarker(int activeMissionCount, int activeScenarioCount) {
        boolean hasActiveScenario() {
            return activeScenarioCount > 0;
        }
    }

    private void drawFactionOverlay(Graphics2D graphics, Arc2D.Double arc, PlanetarySystem system,
          SystemMarkerLayout layout, boolean systemEmpty, Map<Faction, String> capitals,
            double ownershipAlpha, double sharedSystemAlpha, double capitalAlpha, double serviceAlpha) {
        Set<Faction> factions = system.getFactionSet(now);
        if ((factions == null) || factions.isEmpty() || systemEmpty) {
            if (optEmptySystems.isSelected()) {
                drawAnalyticalOverlay(graphics, arc, layout, Color.DARK_GRAY, ownershipAlpha);
            }
            return;
        }

        Stroke oldStroke = graphics.getStroke();
        Paint oldPaint = graphics.getPaint();
        Composite oldComposite = graphics.getComposite();
        try {
            List<Faction> orderedFactions = new ArrayList<>(factions);
            orderedFactions.sort(Comparator.comparing(Faction::getShortName));
            double arcSpacing = 360.0 / orderedFactions.size();
            double arcExtent = Math.min(72.0, 240.0 / orderedFactions.size());
            boolean hasGreatHiringHall = system.getHiringHallLevel(now) == HiringHallLevel.GREAT;
            int capitalCount = (int) orderedFactions.stream()
                  .filter(faction -> system.getId().equals(capitals.get(faction)))
                  .count();
            Composite ownershipComposite = deriveCompositeWithAlpha(oldComposite, ownershipAlpha);
            Composite capitalComposite = deriveCompositeWithAlpha(oldComposite, capitalAlpha);
            Composite serviceComposite = deriveCompositeWithAlpha(oldComposite, serviceAlpha);
            int index = 0;
            int capitalIndex = 0;
            for (Faction faction : orderedFactions) {
                double arcCenter = 90.0 - (index * arcSpacing);
                double arcStart = arcCenter - (arcExtent / 2.0);
                if (ownershipAlpha > 0.0) {
                    graphics.setComposite(ownershipComposite);
                    graphics.setPaint(faction.getColor());
                    graphics.setStroke(new BasicStroke(2.5f));
                    arc.setArcByCenter(layout.centerX(), layout.centerY(), layout.ownershipRadius(),
                          arcStart, arcExtent, Arc2D.OPEN);
                    graphics.draw(arc);
                }

                if ((capitalAlpha > 0.0) && system.getId().equals(capitals.get(faction))) {
                    graphics.setComposite(capitalComposite);
                    drawFactionCapitalMarker(graphics, layout.capitalAnchor(capitalIndex, capitalCount),
                          layout.size(), faction);
                    capitalIndex++;
                }
                index++;
            }

            if ((sharedSystemAlpha > 0.0) && hasSharedSystemOwnership(factions)) {
                graphics.setComposite(deriveCompositeWithAlpha(oldComposite, sharedSystemAlpha));
                drawSharedSystemCue(graphics, arc, layout);
            }

            if (hasGreatHiringHall && (serviceAlpha > 0.0)) {
                graphics.setComposite(serviceComposite);
                drawGreatHiringHallMarker(graphics, layout.serviceAnchor(), layout.size());
            }
        } finally {
            graphics.setComposite(oldComposite);
            graphics.setStroke(oldStroke);
            graphics.setPaint(oldPaint);
        }
    }

    static boolean hasSharedSystemOwnership(PlanetarySystem system, LocalDate date) {
        return hasSharedSystemOwnership(system.getFactionSet(date));
    }

    private static boolean hasSharedSystemOwnership(@Nullable Set<Faction> factions) {
        return (factions != null) && (factions.size() > 1);
    }

    static void drawSharedSystemCue(Graphics2D graphics, Arc2D.Double arc, SystemMarkerLayout layout) {
        double collarRadius = layout.ownershipRadius() + 1.6;
        arc.setArcByCenter(layout.centerX(), layout.centerY(), collarRadius, 0, 360, Arc2D.OPEN);
        graphics.setPaint(withAlpha(Color.BLACK, 215));
        graphics.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(arc);
        graphics.setPaint(withAlpha(new Color(225, 232, 230), 210));
        graphics.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0,
              new float[] { 3.0f, 2.5f }, 0));
        graphics.draw(arc);
    }

    static void drawFactionCapitalMarker(Graphics2D graphics, Point2D.Double anchor, double size,
          Faction faction) {
        drawNationalCapitalMarker(graphics, anchor, size, faction.getColor());
    }

    static Rectangle2D.Double nationalCapitalMarkerBounds(Point2D.Double anchor, double markerSize) {
        double strokePadding = nationalCapitalOutlineWidth(markerSize) / 2.0;
        double halfWidth = nationalCapitalHalfWidth(markerSize) + strokePadding;
        double halfHeight = nationalCapitalHalfHeight(markerSize) + strokePadding;
        return new Rectangle2D.Double(anchor.x - halfWidth, anchor.y - halfHeight,
              halfWidth * 2.0, halfHeight * 2.0);
    }

    private static double nationalCapitalHalfWidth(double markerSize) {
        return Math.max(3.6, markerSize * 0.5);
    }

    private static double nationalCapitalHalfHeight(double markerSize) {
        return Math.max(5.5, markerSize * 0.62);
    }

    private static float nationalCapitalOutlineWidth(double markerSize) {
        return (float) Math.clamp(markerSize * 0.42, 4.0, 5.0);
    }

    static void drawNationalCapitalMarker(Graphics2D graphics, Point2D.Double anchor, double size,
          Color factionColor) {
        double crownHalfWidth = nationalCapitalHalfWidth(size);
        double crownHalfHeight = nationalCapitalHalfHeight(size);
        double crownBaseY = anchor.y + (crownHalfHeight * 0.15);

        GeneralPath crown = new GeneralPath();
        crown.moveTo(anchor.x, anchor.y + crownHalfHeight);
        crown.lineTo(anchor.x, crownBaseY);
        crown.moveTo(anchor.x - crownHalfWidth, crownBaseY);
        crown.lineTo(anchor.x - crownHalfWidth, anchor.y - (crownHalfHeight * 0.45));
        crown.lineTo(anchor.x, anchor.y - crownHalfHeight);
        crown.lineTo(anchor.x + crownHalfWidth, anchor.y - (crownHalfHeight * 0.45));
        crown.lineTo(anchor.x + crownHalfWidth, crownBaseY);

        graphics.setPaint(withAlpha(Color.BLACK, 205));
        graphics.setStroke(new BasicStroke(nationalCapitalOutlineWidth(size), BasicStroke.CAP_ROUND,
              BasicStroke.JOIN_ROUND));
        graphics.draw(crown);
        graphics.setPaint(factionColor);
        graphics.setStroke(new BasicStroke((float) Math.clamp(size * 0.24, 2.3, 3.0),
              BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(crown);
    }

    private static void drawGreatHiringHallMarker(Graphics2D graphics, Point2D.Double anchor, double size) {
        double halfWidth = Math.max(2.4, size * 0.48);
        double markerHeight = Math.max(3.8, size * 0.72);
        double markerTop = anchor.y - (markerHeight / 2.0);
        GeneralPath serviceMarker = new GeneralPath();
        serviceMarker.moveTo(anchor.x - halfWidth, markerTop);
        serviceMarker.lineTo(anchor.x + halfWidth, markerTop);
        serviceMarker.lineTo(anchor.x, markerTop + markerHeight);
        serviceMarker.closePath();

        graphics.setPaint(withAlpha(Color.BLACK, 220));
        graphics.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(serviceMarker);
        graphics.setPaint(new Color(202, 215, 224));
        graphics.fill(serviceMarker);
        graphics.setPaint(new Color(238, 244, 247));
        graphics.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(serviceMarker);
    }

        private void drawMapModeOverlay(Graphics2D graphics, Arc2D.Double arc, PlanetarySystem system,
                    SystemMarkerLayout layout, boolean systemEmpty, boolean showIntrinsicStar,
          boolean showRouteContact, Map<Faction, String> capitals, MapMode mapMode,
                    double strategicContactAlpha, double ownershipAlpha, double sharedSystemAlpha,
                    double capitalAlpha, double serviceAlpha) {
                if ((strategicContactAlpha > 0.0) && showIntrinsicStar && !showRouteContact) {
                        paintLayerWithAlpha(graphics, strategicContactAlpha,
                                    contactGraphics -> drawStrategicContact(contactGraphics, arc, system, layout, systemEmpty, mapMode));
                }
        if (mapMode == MapMode.FACTION) {
                        drawFactionOverlay(graphics, arc, system, layout, systemEmpty, capitals,
                ownershipAlpha, sharedSystemAlpha, capitalAlpha, serviceAlpha);
        } else if (showIntrinsicStar && !showRouteContact) {
            double analyticalAlpha = isServiceMapMode(mapMode) ? serviceAlpha : ownershipAlpha;
                        drawAnalyticalOverlay(graphics, arc, layout, getSystemColor(system, mapMode), analyticalAlpha);
        }
    }

    private static boolean isServiceMapMode(MapMode mapMode) {
        return switch (mapMode) {
            case RECHARGE_STATIONS, ACADEMIES, HIRING_HALLS, DISEASE_OUTBREAKS -> true;
            default -> false;
        };
    }

        private static void drawAnalyticalOverlay(Graphics2D graphics, Arc2D.Double arc,
            SystemMarkerLayout layout, Color color, double alpha) {
        if (alpha <= 0.0) {
            return;
        }
        Stroke oldStroke = graphics.getStroke();
        Paint oldPaint = graphics.getPaint();
        Composite oldComposite = graphics.getComposite();
        try {
            graphics.setComposite(deriveCompositeWithAlpha(oldComposite, alpha));
            graphics.setPaint(color);
            graphics.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            arc.setArcByCenter(layout.centerX(), layout.centerY(), layout.ownershipRadius(), 24, 48, Arc2D.OPEN);
            graphics.draw(arc);
            arc.setArcByCenter(layout.centerX(), layout.centerY(), layout.ownershipRadius(), 204, 48, Arc2D.OPEN);
            graphics.draw(arc);
        } finally {
            graphics.setComposite(oldComposite);
            graphics.setStroke(oldStroke);
            graphics.setPaint(oldPaint);
        }
    }

        private static void drawRouteWaypoint(Graphics2D graphics, Arc2D.Double arc, SystemMarkerLayout layout,
                    Color color) {
        Stroke oldStroke = graphics.getStroke();
                graphics.setStroke(new BasicStroke(layout.routeState() == RouteMarkerState.ACTIVE ? 2.5f : 1.8f));
        graphics.setPaint(color);
                arc.setArcByCenter(layout.centerX(), layout.centerY(), layout.navigationRadius(), 0, 360, Arc2D.OPEN);
        graphics.draw(arc);
        graphics.setStroke(oldStroke);
    }

    private void drawActiveRoute(Graphics2D graphics, Arc2D.Double arc, List<PlanetarySystem> routeSystems,
          double size, double activationProgress, double ambientElapsedSeconds) {
        int legCount = routeSystems.size() - 1;
        double clampedProgress = Math.clamp(activationProgress, 0.0, 1.0);
        double routePosition = easeInOutCubic(clampedProgress) * Math.max(0, legCount);
        Stroke oldStroke = graphics.getStroke();
        Paint oldPaint = graphics.getPaint();

        for (int legIndex = 0; legIndex < legCount; legIndex++) {
            PlanetarySystem start = routeSystems.get(legIndex);
            PlanetarySystem end = routeSystems.get(legIndex + 1);
            double startX = map2scrX(start.getX());
            double startY = map2scrY(start.getY());
            double endX = map2scrX(end.getX());
            double endY = map2scrY(end.getY());
            double completedLegProgress = Math.clamp(routePosition - legIndex, 0.0, 1.0);
            double boundaryX = interpolate(startX, endX, completedLegProgress);
            double boundaryY = interpolate(startY, endY, completedLegProgress);

            if (completedLegProgress < 1.0) {
                graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                      new float[] { 8, 6 }, 0));
                graphics.setPaint(PLANNED_ROUTE_COLOR);
                graphics.draw(new Line2D.Double(boundaryX, boundaryY, endX, endY));
            }
            if (completedLegProgress > 0.0) {
                graphics.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setPaint(ACTIVE_ROUTE_COLOR);
                graphics.draw(new Line2D.Double(startX, startY, boundaryX, boundaryY));
            }
        }

        if ((clampedProgress > 0.0) && (clampedProgress < 1.0) && (legCount > 0)) {
            drawRouteActivationBoundary(graphics, routeSystems, routePosition);
        }
        if (clampedProgress >= 1.0) {
            drawActiveRouteFlow(graphics, routeSystems, ambientElapsedSeconds);
        }
        for (int waypointIndex = 0; waypointIndex < routeSystems.size(); waypointIndex++) {
            PlanetarySystem waypoint = routeSystems.get(waypointIndex);
            double waypointActivation = getRouteWaypointActivation(routePosition, waypointIndex, clampedProgress);
            RouteMarkerState routeMarkerState = waypointActivation >= 0.5
                ? RouteMarkerState.ACTIVE
                : RouteMarkerState.PLANNED;
            SystemMarkerLayout layout = createSystemMarkerLayout(waypoint, size, routeMarkerState);
            drawRouteWaypoint(graphics, arc, layout,
                interpolateColor(PLANNED_ROUTE_COLOR, ACTIVE_ROUTE_COLOR, waypointActivation));
        }

        graphics.setStroke(oldStroke);
        graphics.setPaint(oldPaint);
    }

    private void drawRouteActivationBoundary(Graphics2D graphics, List<PlanetarySystem> routeSystems,
          double routePosition) {
        int legIndex = Math.min(routeSystems.size() - 2, (int) Math.floor(routePosition));
        double legProgress = routePosition - legIndex;
        PlanetarySystem start = routeSystems.get(legIndex);
        PlanetarySystem end = routeSystems.get(legIndex + 1);
        double boundaryX = interpolate(map2scrX(start.getX()), map2scrX(end.getX()), legProgress);
        double boundaryY = interpolate(map2scrY(start.getY()), map2scrY(end.getY()), legProgress);
        graphics.setPaint(withAlpha(ACTIVE_ROUTE_COLOR, 80));
        graphics.fill(new Ellipse2D.Double(boundaryX - 4.0, boundaryY - 4.0, 8.0, 8.0));
        graphics.setPaint(ACTIVE_ROUTE_FLOW_COLOR);
        graphics.fill(new Ellipse2D.Double(boundaryX - 1.7, boundaryY - 1.7, 3.4, 3.4));
    }

    private static double getRouteWaypointActivation(double routePosition, int waypointIndex,
          double activationProgress) {
        if (activationProgress >= 1.0) {
            return 1.0;
        }
        double transitionLength = 0.18;
        if (waypointIndex == 0) {
            return Math.clamp(routePosition / transitionLength, 0.0, 1.0);
        }
        return Math.clamp((routePosition - waypointIndex + transitionLength) / transitionLength, 0.0, 1.0);
    }

    private void drawActiveRouteFlow(Graphics2D graphics, List<PlanetarySystem> routeSystems,
          double ambientElapsedSeconds) {
        int legCount = routeSystems.size() - 1;
        if (legCount < 1) {
            return;
        }

        int routeHash = 1;
        for (PlanetarySystem system : routeSystems) {
            routeHash = (31 * routeHash) + getStableHash(system.getId());
        }
        double routePeriodSeconds = Math.clamp(3.2 + ((legCount - 1) * 0.45), 3.2, 5.5);
        double routeProgress = fractionalPart((ambientElapsedSeconds / routePeriodSeconds)
              + getStableUnit(routeHash, 0x7f4a7c15));
        double routePosition = routeProgress * legCount;
        int legIndex = Math.min(legCount - 1, (int) Math.floor(routePosition));
        double legProgress = routePosition - legIndex;
        PlanetarySystem start = routeSystems.get(legIndex);
        PlanetarySystem end = routeSystems.get(legIndex + 1);
        double packetX = interpolate(map2scrX(start.getX()), map2scrX(end.getX()), legProgress);
        double packetY = interpolate(map2scrY(start.getY()), map2scrY(end.getY()), legProgress);

        Paint oldPaint = graphics.getPaint();
        graphics.setPaint(withAlpha(ACTIVE_ROUTE_COLOR, 75));
        graphics.fill(new Ellipse2D.Double(packetX - 3.2, packetY - 3.2, 6.4, 6.4));
        graphics.setPaint(withAlpha(ACTIVE_ROUTE_FLOW_COLOR, 230));
        graphics.fill(new Ellipse2D.Double(packetX - 1.55, packetY - 1.55, 3.1, 3.1));
        graphics.setPaint(oldPaint);
    }

    private void drawActiveRouteWaypointBadges(Graphics2D graphics, List<PlanetarySystem> routeSystems,
            double size, double activationProgress, @Nullable PlanetarySystem hoveredSystem, double alpha) {
        if (alpha <= 0.0) {
            return;
        }
        int legCount = routeSystems.size() - 1;
        double clampedProgress = Math.clamp(activationProgress, 0.0, 1.0);
        double routePosition = easeInOutCubic(clampedProgress) * Math.max(0, legCount);
        paintLayerWithAlpha(graphics, alpha, badgeGraphics -> {
            for (int waypointIndex = 1; waypointIndex < routeSystems.size(); waypointIndex++) {
                PlanetarySystem waypoint = routeSystems.get(waypointIndex);
                double waypointActivation = getRouteWaypointActivation(routePosition, waypointIndex, clampedProgress);
                RouteMarkerState routeMarkerState = waypointActivation >= 0.5
                    ? RouteMarkerState.ACTIVE
                    : RouteMarkerState.PLANNED;
                SystemMarkerLayout layout = createSystemMarkerLayout(waypoint, size, routeMarkerState, hoveredSystem);
                drawRouteWaypointBadge(badgeGraphics, layout,
                    interpolateColor(PLANNED_ROUTE_COLOR, ACTIVE_ROUTE_COLOR, waypointActivation), waypointIndex);
            }
        });
    }

        private void drawProposedRoute(Graphics2D graphics, Arc2D.Double arc, double size,
                    int revealedSystemCount) {
                if (jumpPath.isEmpty()) {
            return;
                }

                Stroke oldStroke = graphics.getStroke();
                graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                            new float[] { 8, 6 }, 0));
                PlanetarySystem origin = jumpPath.get(0);
                      SystemMarkerLayout originLayout = createSystemMarkerLayout(origin, size,
                          RouteMarkerState.PLANNED);
                    drawRouteWaypoint(graphics, arc, originLayout, PLANNED_ROUTE_COLOR);

                for (int systemIndex = 1; systemIndex < revealedSystemCount; systemIndex++) {
            PlanetarySystem systemA = jumpPath.get(systemIndex - 1);
            PlanetarySystem systemB = jumpPath.get(systemIndex);
            graphics.setPaint(PLANNED_ROUTE_COLOR);
            graphics.draw(new Line2D.Double(map2scrX(systemA.getX()), map2scrY(systemA.getY()),
                                    map2scrX(systemB.getX()), map2scrY(systemB.getY())));
            SystemMarkerLayout layout = createSystemMarkerLayout(systemB, size, RouteMarkerState.PLANNED);
            drawRouteWaypoint(graphics, arc, layout, PLANNED_ROUTE_COLOR);
                }

                if (proposedRouteAnimationTimer.isRunning() && (revealedSystemCount < jumpPath.size())) {
            PlanetarySystem systemA = jumpPath.get(revealedSystemCount - 1);
            PlanetarySystem systemB = jumpPath.get(revealedSystemCount);
            double legProgress = getCurrentProposedRouteLegProgress();
            double startX = map2scrX(systemA.getX());
            double startY = map2scrY(systemA.getY());
            double endX = interpolate(startX, map2scrX(systemB.getX()), legProgress);
            double endY = interpolate(startY, map2scrY(systemB.getY()), legProgress);
            graphics.setPaint(PLANNED_ROUTE_COLOR);
            graphics.draw(new Line2D.Double(startX, startY, endX, endY));
        }
                graphics.setStroke(oldStroke);
        }

        private void drawRouteWaypointBadges(Graphics2D graphics, JumpPath path, double size, Color color,
                  int visibleSystemCount, @Nullable PlanetarySystem hoveredSystem, double alpha) {
                if (alpha <= 0.0) {
            return;
        }
                int waypointLimit = Math.min(path.size(), visibleSystemCount);
                paintLayerWithAlpha(graphics, alpha, badgeGraphics -> {
                    int waypointNumber = 1;
                    for (int waypointIndex = 1; waypointIndex < waypointLimit; waypointIndex++) {
                        PlanetarySystem waypoint = path.get(waypointIndex);
                        if (!routePlanningHandler.isRequestedWaypoint(waypoint)) {
                            continue;
                        }
                        SystemMarkerLayout layout = createSystemMarkerLayout(waypoint, size, RouteMarkerState.PLANNED,
                              hoveredSystem);
                        drawRouteWaypointBadge(badgeGraphics, layout, color, waypointNumber++);
                    }
                });
    }

        private static void drawRouteWaypointBadge(Graphics2D graphics, SystemMarkerLayout layout, Color color,
            int waypointNumber) {
        Font oldFont = graphics.getFont();
        Paint oldPaint = graphics.getPaint();
        Font badgeFont = oldFont.deriveFont(Font.BOLD, Math.max(9.0f, Math.min(11.0f, oldFont.getSize2D())));
        graphics.setFont(badgeFont);

        String badgeText = Integer.toString(waypointNumber);
        FontMetrics metrics = graphics.getFontMetrics();
          Rectangle2D textBounds = metrics.getStringBounds(badgeText, graphics);
          int badgeDiameter = Math.max(16,
              Math.max(metrics.getHeight() + 2, (int) Math.ceil(textBounds.getWidth()) + 8));
                Point2D.Double badgeAnchor = layout.routeBadgeAnchor(badgeDiameter);
                double badgeCenterX = badgeAnchor.x;
                double badgeCenterY = badgeAnchor.y;
        Ellipse2D.Double badge = new Ellipse2D.Double(badgeCenterX - (badgeDiameter / 2.0),
              badgeCenterY - (badgeDiameter / 2.0), badgeDiameter, badgeDiameter);

        graphics.setPaint(withAlpha(MAP_BACKGROUND_BOTTOM, 235));
        graphics.fill(badge);
        graphics.setPaint(color);
    float textX = (float) (badgeCenterX - textBounds.getCenterX());
        float textY = (float) (badgeCenterY + ((metrics.getAscent() - metrics.getDescent()) / 2.0));
        graphics.drawString(badgeText, textX, textY);

        graphics.setFont(oldFont);
        graphics.setPaint(oldPaint);
    }

    private static void drawSystemLabel(Graphics2D graphics, String baseText, @Nullable String suffix,
          float x, float y, Color color, double baseAlpha, double suffixAlpha) {
        drawOutlinedTextWithAlpha(graphics, baseText, x, y, color, baseAlpha);
        if ((suffix == null) || (suffixAlpha <= 0.0)) {
            return;
        }
        float suffixX = x + graphics.getFontMetrics().stringWidth(baseText);
        drawOutlinedTextWithAlpha(graphics, suffix, suffixX, y, color, suffixAlpha);
    }

    private static void drawOutlinedTextWithAlpha(Graphics2D graphics, String text, float x, float y,
          Color color, double alpha) {
        if (alpha <= 0.0) {
            return;
        }
        paintLayerWithAlpha(graphics, alpha, labelGraphics -> {
            labelGraphics.setPaint(Color.BLACK);
            labelGraphics.drawString(text, x - 1f, y - 1f);
            labelGraphics.drawString(text, x + 1f, y - 1f);
            labelGraphics.drawString(text, x + 1f, y + 1f);
            labelGraphics.drawString(text, x - 1f, y + 1f);
            labelGraphics.setPaint(color);
            labelGraphics.drawString(text, x, y);
        });
    }

    private static @Nullable BufferedImage loadCurrentLocationIcon() {
        try {
            BufferedImage source = ImageIO.read(new File(CURRENT_LOCATION_ICON_PATH));
            if (source == null) {
                LOGGER.error("Unable to read current-location JumpShip image: {}", CURRENT_LOCATION_ICON_PATH);
                return null;
            }

            BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(),
                  BufferedImage.TYPE_INT_ARGB);
            for (int imageY = 0; imageY < source.getHeight(); imageY++) {
                for (int imageX = 0; imageX < source.getWidth(); imageX++) {
                    int sourcePixel = source.getRGB(imageX, imageY);
                    int alpha = sourcePixel >>> 24;
                    if (alpha == 0) {
                        continue;
                    }
                    int red = (sourcePixel >>> 16) & 0xff;
                    int green = (sourcePixel >>> 8) & 0xff;
                    int blue = sourcePixel & 0xff;
                    int luminance = ((red * 54) + (green * 183) + (blue * 19)) >>> 8;
                    double shade = 0.35 + (luminance / 255.0 * 0.65);
                    int tintedRed = (int) Math.round(CURRENT_LOCATION_COLOR.getRed() * shade);
                    int tintedGreen = (int) Math.round(CURRENT_LOCATION_COLOR.getGreen() * shade);
                    int tintedBlue = (int) Math.round(CURRENT_LOCATION_COLOR.getBlue() * shade);
                    tinted.setRGB(imageX, imageY,
                          (alpha << 24) | (tintedRed << 16) | (tintedGreen << 8) | tintedBlue);
                }
            }

            BufferedImage scaled = new BufferedImage(CURRENT_LOCATION_ICON_SIZE, CURRENT_LOCATION_ICON_SIZE,
                  BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.translate(0, CURRENT_LOCATION_ICON_SIZE);
            graphics.rotate(-Math.PI / 2.0);
            graphics.drawImage(tinted, 0, 0, CURRENT_LOCATION_ICON_SIZE, CURRENT_LOCATION_ICON_SIZE, null);
            graphics.dispose();
            return scaled;
        } catch (IOException exception) {
            LOGGER.error("Unable to load current-location JumpShip image", exception);
            return null;
        }
    }

    private static void drawCurrentLocationMarker(Graphics2D graphics, SystemMarkerLayout layout,
          double ambientElapsedSeconds) {
        Point2D.Double shipAnchor = layout.shipAnchor();
        drawJumpShipIcon(graphics, shipAnchor.x, shipAnchor.y, 0.0, ambientElapsedSeconds, false);
    }

    private static void drawStrategicCurrentLocationMarker(Graphics2D graphics, SystemMarkerLayout layout) {
        Stroke oldStroke = graphics.getStroke();
        Paint oldPaint = graphics.getPaint();
        try {
            double radius = Math.max(3.8, layout.ownershipRadius() + 1.0);
            Ellipse2D.Double beacon = new Ellipse2D.Double(layout.centerX() - radius, layout.centerY() - radius,
                  radius * 2.0, radius * 2.0);
            graphics.setPaint(withAlpha(CURRENT_LOCATION_COLOR, 70));
            graphics.setStroke(new BasicStroke(4.0f));
            graphics.draw(beacon);
            graphics.setPaint(CURRENT_LOCATION_COLOR);
            graphics.setStroke(new BasicStroke(1.4f));
            graphics.draw(beacon);
        } finally {
            graphics.setStroke(oldStroke);
            graphics.setPaint(oldPaint);
        }
    }

        private void drawSystemHopMarker(Graphics2D graphics, double size, @Nullable PlanetarySystem hoveredSystem,
            double ambientElapsedSeconds) {
        if ((systemHopOriginId == null) || (systemHopDestinationId == null)) {
            return;
        }
        PlanetarySystem origin = campaign.getSystemById(systemHopOriginId);
        PlanetarySystem destination = campaign.getSystemById(systemHopDestinationId);
        if ((origin == null) || (destination == null)) {
            return;
        }

          double progress = Math.clamp(systemHopProgress, 0.0, 1.0);
          if (progress < SYSTEM_HOP_DEPARTURE_END_PROGRESS) {
            double phaseProgress = progress / SYSTEM_HOP_DEPARTURE_END_PROGRESS;
                        SystemMarkerLayout originLayout = createSystemMarkerLayout(origin, size, RouteMarkerState.ACTIVE,
                                    hoveredSystem);
            drawSystemHopEndpoint(graphics, originLayout, ambientElapsedSeconds,
                phaseProgress, true);
          } else if (progress >= SYSTEM_HOP_ARRIVAL_START_PROGRESS) {
            double phaseProgress = (progress - SYSTEM_HOP_ARRIVAL_START_PROGRESS)
                / (1.0 - SYSTEM_HOP_ARRIVAL_START_PROGRESS);
            SystemMarkerLayout destinationLayout = createSystemMarkerLayout(destination, size,
                  RouteMarkerState.ACTIVE, hoveredSystem);
            drawSystemHopEndpoint(graphics, destinationLayout, ambientElapsedSeconds,
                phaseProgress, false);
          }
    }

        private static void drawSystemHopEndpoint(Graphics2D graphics, SystemMarkerLayout layout,
            double ambientElapsedSeconds, double phaseProgress, boolean departing) {
          double easedProgress = easeInOutCubic(phaseProgress);
          double shipAlpha = departing ? 1.0 - easedProgress : easedProgress;
          Point2D.Double shipAnchor = layout.shipAnchor();
          double shimmerAlpha = Math.sin(Math.PI * phaseProgress);

          drawSystemHopShimmer(graphics, shipAnchor.x, shipAnchor.y, shimmerAlpha);
          paintLayerWithAlpha(graphics, shipAlpha,
              shipGraphics -> drawJumpShipIcon(shipGraphics, shipAnchor.x, shipAnchor.y, 0.0,
                  ambientElapsedSeconds, false));
        }

        private static void drawSystemHopShimmer(Graphics2D graphics, double centerX, double centerY, double alpha) {
          if (alpha <= 0.0) {
            return;
          }
        Paint oldPaint = graphics.getPaint();
          Stroke oldStroke = graphics.getStroke();
          double halfWidth = CURRENT_LOCATION_ICON_SIZE * 0.48;
          double spread = interpolate(2.0, 7.0, alpha);
          graphics.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          graphics.setPaint(withAlpha(PLANNED_ROUTE_COLOR, scaleAlpha(150, alpha)));
          graphics.draw(new Line2D.Double(centerX - halfWidth - spread, centerY - 5.0,
              centerX + halfWidth - spread, centerY - 5.0));
          graphics.setPaint(withAlpha(ACTIVE_ROUTE_FLOW_COLOR, scaleAlpha(175, alpha)));
          graphics.draw(new Line2D.Double(centerX - halfWidth + spread, centerY,
              centerX + halfWidth + spread, centerY));
          graphics.setPaint(withAlpha(PLANNED_ROUTE_COLOR, scaleAlpha(120, alpha)));
          graphics.draw(new Line2D.Double(centerX - halfWidth - spread, centerY + 5.0,
              centerX + halfWidth - spread, centerY + 5.0));
          graphics.setStroke(oldStroke);
        graphics.setPaint(oldPaint);
    }

    private static void drawJumpShipIcon(Graphics2D graphics, double centerX, double centerY, double rotation,
          double ambientElapsedSeconds, boolean moving) {
        Graphics2D shipGraphics = (Graphics2D) graphics.create();
        try {
            shipGraphics.translate(centerX, centerY);
            shipGraphics.rotate(rotation);

            if (CURRENT_LOCATION_ICON != null) {
                int iconOffset = -CURRENT_LOCATION_ICON_SIZE / 2;
                shipGraphics.drawImage(CURRENT_LOCATION_ICON, iconOffset, iconOffset, null);
            } else {
                drawJumpShipFallback(shipGraphics);
            }
            drawJumpShipNavigationLights(shipGraphics, ambientElapsedSeconds, moving);
        } finally {
            shipGraphics.dispose();
        }
    }

    private static void drawJumpShipFallback(Graphics2D graphics) {
        GeneralPath locationChevron = new GeneralPath();
        locationChevron.moveTo(-5.0, -2.0);
        locationChevron.lineTo(0.0, 3.0);
        locationChevron.lineTo(5.0, -2.0);
        graphics.setPaint(withAlpha(MAP_BACKGROUND_BOTTOM, 230));
        graphics.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(locationChevron);
        graphics.setPaint(CURRENT_LOCATION_COLOR);
        graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(locationChevron);
    }

    private static void drawJumpShipNavigationLights(Graphics2D graphics, double ambientElapsedSeconds,
          boolean moving) {
        double cyanPeriod = moving ? 1.35 : 2.8;
        double amberPeriod = moving ? 1.85 : 3.7;
        drawJumpShipNavigationLight(graphics, -3.5, -4.0, PLANNED_ROUTE_COLOR,
              getNavigationLightAlpha(ambientElapsedSeconds, cyanPeriod, 0.08));
        drawJumpShipNavigationLight(graphics, 7.0, 3.5, ACTIVE_ROUTE_FLOW_COLOR,
              getNavigationLightAlpha(ambientElapsedSeconds, amberPeriod, 0.61));
    }

    private static int getNavigationLightAlpha(double ambientElapsedSeconds, double periodSeconds,
          double phaseOffset) {
        double phase = fractionalPart((ambientElapsedSeconds / periodSeconds) + phaseOffset);
        double pulse = phase < 0.08
              ? phase / 0.08
              : (phase < 0.20 ? 1.0 - ((phase - 0.08) / 0.12) : 0.0);
        return (int) Math.round(interpolate(35.0, 235.0, pulse));
    }

    private static void drawJumpShipNavigationLight(Graphics2D graphics, double x, double y, Color color,
          int alpha) {
        graphics.setPaint(withAlpha(color, Math.max(18, alpha / 4)));
        graphics.fill(new Ellipse2D.Double(x - 1.8, y - 1.8, 3.6, 3.6));
        graphics.setPaint(withAlpha(color, alpha));
        graphics.fill(new Ellipse2D.Double(x - 0.8, y - 0.8, 1.6, 1.6));
    }

        private static void drawSelectedSystemMarker(Graphics2D graphics, SystemMarkerLayout layout,
                    String systemId, double ambientElapsedSeconds) {
        Stroke oldStroke = graphics.getStroke();
                double radius = layout.selectedRadius();
        double bracketLength = Math.min(6.0, radius * 0.55);
                GeneralPath brackets = createCornerBrackets(layout.centerX(), layout.centerY(), radius, bracketLength);

        double breath = getAmbientWave(ambientElapsedSeconds, getStableHash(systemId), 0x3c6ef372, 3.4, 4.0);
        graphics.setPaint(withAlpha(SELECTED_SYSTEM_COLOR, scaleAlpha(70, 1.0 + (breath * 0.09))));
        graphics.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.draw(brackets);
        graphics.setPaint(SELECTED_SYSTEM_COLOR);
        graphics.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.draw(brackets);
        graphics.setStroke(oldStroke);
    }

        private static void drawSelectionAnimationMarker(Graphics2D graphics, SystemMarkerLayout layout,
                    double progress) {
        double easedProgress = easeOutCubic(progress);
                double hoveredRadius = layout.hoveredRadius();
                double selectedRadius = layout.selectedRadius();
        double radius = interpolate(hoveredRadius, selectedRadius, easedProgress);
        double hoveredBracketLength = Math.min(5.0, hoveredRadius * 0.45);
        double selectedBracketLength = Math.min(6.0, selectedRadius * 0.55);
        double bracketLength = interpolate(hoveredBracketLength, selectedBracketLength, easedProgress);
        GeneralPath brackets = createCornerBrackets(layout.centerX(), layout.centerY(), radius, bracketLength);

        Stroke oldStroke = graphics.getStroke();
        int glowAlpha = (int) Math.round(interpolate(0.0, 70.0, easedProgress));
        float glowWidth = (float) interpolate(1.1, 5.0, easedProgress);
        graphics.setPaint(withAlpha(SELECTED_SYSTEM_COLOR, glowAlpha));
        graphics.setStroke(new BasicStroke(glowWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.draw(brackets);

        Color hoveredColor = withAlpha(HOVERED_SYSTEM_COLOR, 190);
        graphics.setPaint(interpolateColor(hoveredColor, SELECTED_SYSTEM_COLOR, easedProgress));
        float foregroundWidth = (float) interpolate(1.1, 2.3, easedProgress);
        graphics.setStroke(new BasicStroke(foregroundWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(brackets);
        graphics.setStroke(oldStroke);
    }

    private static void drawHoveredSystemMarker(Graphics2D graphics, SystemMarkerLayout layout) {
        Stroke oldStroke = graphics.getStroke();
        graphics.setPaint(withAlpha(HOVERED_SYSTEM_COLOR, 190));
        graphics.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double radius = layout.hoveredRadius();
        double bracketLength = Math.min(5.0, radius * 0.45);
        graphics.draw(createCornerBrackets(layout.centerX(), layout.centerY(), radius, bracketLength));
        graphics.setStroke(oldStroke);
    }

    private static double easeOutCubic(double progress) {
        double clampedProgress = Math.clamp(progress, 0.0, 1.0);
        return 1.0 - Math.pow(1.0 - clampedProgress, 3.0);
    }

    private static double easeInOutCubic(double progress) {
        double clampedProgress = Math.clamp(progress, 0.0, 1.0);
        if (clampedProgress < 0.5) {
            return 4.0 * clampedProgress * clampedProgress * clampedProgress;
        }
        return 1.0 - Math.pow(-2.0 * clampedProgress + 2.0, 3.0) / 2.0;
    }

    private static double interpolate(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private static double getSemanticZoomReference(double configuredNameThreshold) {
        return configuredNameThreshold > 0.0 ? configuredNameThreshold : 3.0;
    }

    private static double fadeBetween(double scale, double start, double end) {
        if (end <= start) {
            return scale >= end ? 1.0 : 0.0;
        }
        double progress = Math.clamp((scale - start) / (end - start), 0.0, 1.0);
        return progress * progress * (3.0 - (2.0 * progress));
    }

    private static Color interpolateColor(Color start, Color end, double progress) {
        double clampedProgress = Math.clamp(progress, 0.0, 1.0);
        int red = (int) Math.round(interpolate(start.getRed(), end.getRed(), clampedProgress));
        int green = (int) Math.round(interpolate(start.getGreen(), end.getGreen(), clampedProgress));
        int blue = (int) Math.round(interpolate(start.getBlue(), end.getBlue(), clampedProgress));
        int alpha = (int) Math.round(interpolate(start.getAlpha(), end.getAlpha(), clampedProgress));
        return new Color(red, green, blue, alpha);
    }

    private static GeneralPath createCornerBrackets(double x, double y, double radius, double bracketLength) {
        GeneralPath brackets = new GeneralPath();
        brackets.moveTo(x - radius + bracketLength, y - radius);
        brackets.lineTo(x - radius, y - radius);
        brackets.lineTo(x - radius, y - radius + bracketLength);
        brackets.moveTo(x + radius - bracketLength, y - radius);
        brackets.lineTo(x + radius, y - radius);
        brackets.lineTo(x + radius, y - radius + bracketLength);
        brackets.moveTo(x + radius, y + radius - bracketLength);
        brackets.lineTo(x + radius, y + radius);
        brackets.lineTo(x + radius - bracketLength, y + radius);
        brackets.moveTo(x - radius + bracketLength, y + radius);
        brackets.lineTo(x - radius, y + radius);
        brackets.lineTo(x - radius, y + radius - bracketLength);
        return brackets;
    }

    @SuppressWarnings("removal")
    private static Color getSpectralColor(@Nullable StarType star) {
        if (star == null) {
            return new Color(210, 220, 225);
        }
        return switch (star.getSpectralClass()) {
            case StarType.SPECTRAL_O -> new Color(145, 170, 255);
            case StarType.SPECTRAL_B -> new Color(170, 195, 255);
            case StarType.SPECTRAL_A -> new Color(210, 222, 255);
            case StarType.SPECTRAL_F -> new Color(242, 245, 255);
            case StarType.SPECTRAL_G -> new Color(255, 235, 175);
            case StarType.SPECTRAL_K -> new Color(255, 190, 120);
            case StarType.SPECTRAL_M -> new Color(255, 132, 92);
            case StarType.SPECTRAL_L -> new Color(205, 105, 72);
            case StarType.SPECTRAL_T -> new Color(135, 165, 190);
            case StarType.SPECTRAL_Y -> new Color(150, 145, 125);
            case StarType.SPECTRAL_D -> new Color(225, 235, 255);
            default -> new Color(205, 215, 220);
        };
    }

    @SuppressWarnings("removal")
    private static double getLuminosityClassVisualScale(@Nullable StarType star) {
        if (star == null) {
            return 1.0;
        }
        String luminosity = star.getLuminosity();
        if (luminosity == null) {
            return 1.0;
        }
        return switch (luminosity) {
            case StarType.LUM_0 -> 1.42;
            case StarType.LUM_IA -> 1.4;
            case StarType.LUM_IAB, StarType.LUM_I -> 1.37;
            case StarType.LUM_IB -> 1.35;
            case StarType.LUM_II_EVOLVED, StarType.LUM_II -> 1.25;
            case StarType.LUM_III_EVOLVED, StarType.LUM_III -> 1.16;
            case StarType.LUM_IV_EVOLVED, StarType.LUM_IV -> 1.08;
            case StarType.LUM_V_EVOLVED, StarType.LUM_V -> 1.0;
            case StarType.LUM_VI -> 0.86;
            case StarType.LUM_VI_PLUS, StarType.LUM_VII -> 0.75;
            default -> 1.0;
        };
    }

    private static double getAmbientWave(double elapsedSeconds, int stableHash, int salt,
          double minimumPeriodSeconds, double maximumPeriodSeconds) {
        double periodSeconds = interpolate(minimumPeriodSeconds, maximumPeriodSeconds,
              getStableUnit(stableHash, salt));
        double phaseOffset = getStableUnit(stableHash, salt ^ 0x9e3779b9) * FULL_CIRCLE_RADIANS;
        return Math.sin((elapsedSeconds / periodSeconds * FULL_CIRCLE_RADIANS) + phaseOffset);
    }

    private static double getStableUnit(int stableHash, int salt) {
        int mixedHash = stableHash ^ salt;
        mixedHash ^= mixedHash >>> 16;
        mixedHash *= 0x7feb352d;
        mixedHash ^= mixedHash >>> 15;
        mixedHash *= 0x846ca68b;
        mixedHash ^= mixedHash >>> 16;
        return Integer.toUnsignedLong(mixedHash) / 4_294_967_296.0;
    }

    private static int getStableHash(@Nullable String stableId) {
        return stableId == null ? 0 : stableId.hashCode();
    }

    private static double fractionalPart(double value) {
        return value - Math.floor(value);
    }

    private static int scaleAlpha(int baseAlpha, double scale) {
        return Math.clamp((int) Math.round(baseAlpha * scale), 0, 255);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color brighten(Color color) {
        return new Color((color.getRed() + 255) / 2, (color.getGreen() + 255) / 2,
              (color.getBlue() + 255) / 2);
    }

    private void drawMapBackground(Graphics2D graphics, int width, int height) {
        graphics.setPaint(new GradientPaint(0, 0, MAP_BACKGROUND_TOP, 0, height, MAP_BACKGROUND_BOTTOM));
        graphics.fillRect(0, 0, width, height);

        double spacing = getGridSpacing(conf.scale);
        double left = scr2mapX(0);
        double right = scr2mapX(width);
        double bottom = scr2mapY(height);
        double top = scr2mapY(0);
        long firstColumn = (long) Math.ceil(left / spacing);
        long lastColumn = (long) Math.floor(right / spacing);
        long firstRow = (long) Math.ceil(bottom / spacing);
        long lastRow = (long) Math.floor(top / spacing);

        Stroke oldStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(1.0f));
        for (long column = firstColumn; column <= lastColumn; column++) {
            graphics.setPaint((Math.floorMod(column, 5) == 0) ? MAP_GRID_MAJOR : MAP_GRID_MINOR);
            double x = map2scrX(column * spacing);
            graphics.draw(new Line2D.Double(x, 0, x, height));
        }
        for (long row = firstRow; row <= lastRow; row++) {
            graphics.setPaint((Math.floorMod(row, 5) == 0) ? MAP_GRID_MAJOR : MAP_GRID_MINOR);
            double y = map2scrY(row * spacing);
            graphics.draw(new Line2D.Double(0, y, width, y));
        }
        graphics.setStroke(oldStroke);
    }

    private static double getGridSpacing(double scale) {
        double targetSpacing = GRID_TARGET_SPACING / Math.max(scale, 0.0001);
        double magnitude = Math.pow(10, Math.floor(Math.log10(targetSpacing)));
        double normalizedSpacing = targetSpacing / magnitude;
        if (normalizedSpacing <= 2) {
            return 2 * magnitude;
        } else if (normalizedSpacing <= 5) {
            return 5 * magnitude;
        }
        return 10 * magnitude;
    }

    static NavigationInstrumentLayout createNavigationInstrumentLayout(
          int viewportWidth, int viewportHeight, double mapScale) {
        int margin = Math.max(1, UIUtil.scaleForGUI(NAVIGATION_INSTRUMENT_MARGIN));
        int instrumentWidth = Math.max(1, UIUtil.scaleForGUI(NAVIGATION_INSTRUMENT_WIDTH));
        int instrumentHeight = Math.max(1, UIUtil.scaleForGUI(NAVIGATION_INSTRUMENT_HEIGHT));
        if (!Double.isFinite(mapScale) || (mapScale <= 0.0)
              || (viewportWidth < instrumentWidth + (2 * margin))
              || (viewportHeight < instrumentHeight + (2 * margin))) {
            return NavigationInstrumentLayout.hidden(MHQConstants.MAX_JUMP_RADIUS);
        }

        double x = margin;
        double y = viewportHeight - margin - instrumentHeight;
        double barStartX = x + UIUtil.scaleForGUI(NAVIGATION_SCALE_BAR_INSET);
        double barPixelWidth = Math.max(1, UIUtil.scaleForGUI(NAVIGATION_SCALE_BAR_WIDTH));
        double distanceLy = barPixelWidth / mapScale;

        return new NavigationInstrumentLayout(true, x, y, instrumentWidth, instrumentHeight,
              x + UIUtil.scaleForGUI(NAVIGATION_COMPASS_CENTER_X),
              y + UIUtil.scaleForGUI(NAVIGATION_COMPASS_CENTER_Y),
              Math.max(1, UIUtil.scaleForGUI(NAVIGATION_COMPASS_RADIUS)),
              barStartX, barStartX + barPixelWidth,
              y + UIUtil.scaleForGUI(NAVIGATION_SCALE_BAR_Y), distanceLy,
              MHQConstants.MAX_JUMP_RADIUS);
    }

    static void drawNavigationInstrument(Graphics2D graphics, NavigationInstrumentLayout layout) {
        if (!layout.visible()) {
            return;
        }

        Graphics2D instrumentGraphics = (Graphics2D) graphics.create();
        try {
            instrumentGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                  RenderingHints.VALUE_ANTIALIAS_ON);
            instrumentGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                  RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawNavigationCompass(instrumentGraphics, layout);
            drawNavigationScale(instrumentGraphics, layout);
        } finally {
            instrumentGraphics.dispose();
        }
    }

    private static void drawNavigationCompass(Graphics2D graphics, NavigationInstrumentLayout layout) {
        double centerX = layout.compassCenterX();
        double centerY = layout.compassCenterY();
        double radius = layout.compassRadius();
        float lineWidth = Math.max(1.0f, UIUtil.scaleForGUI(1));
        double shadowOffset = Math.max(1, UIUtil.scaleForGUI(1));

        graphics.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.setPaint(NAVIGATION_INSTRUMENT_SHADOW);
        graphics.draw(new Line2D.Double(centerX - radius + shadowOffset, centerY + shadowOffset,
              centerX + radius + shadowOffset, centerY + shadowOffset));
        graphics.draw(new Line2D.Double(centerX + shadowOffset, centerY - radius + shadowOffset,
              centerX + shadowOffset, centerY + radius + shadowOffset));
        graphics.setPaint(LAYER_CONTROL_BUTTON_ICON);
        graphics.draw(new Line2D.Double(centerX - radius, centerY, centerX + radius, centerY));
        graphics.draw(new Line2D.Double(centerX, centerY - radius, centerX, centerY + radius));

        double pointerSize = Math.max(3, UIUtil.scaleForGUI(4));
        GeneralPath pointers = new GeneralPath();
        pointers.moveTo(centerX, centerY - radius);
        pointers.lineTo(centerX - pointerSize, centerY - radius + (pointerSize * 1.6));
        pointers.lineTo(centerX + pointerSize, centerY - radius + (pointerSize * 1.6));
        pointers.closePath();
        pointers.moveTo(centerX, centerY + radius);
        pointers.lineTo(centerX - pointerSize, centerY + radius - (pointerSize * 1.6));
        pointers.lineTo(centerX + pointerSize, centerY + radius - (pointerSize * 1.6));
        pointers.closePath();
        pointers.moveTo(centerX - radius, centerY);
        pointers.lineTo(centerX - radius + (pointerSize * 1.6), centerY - pointerSize);
        pointers.lineTo(centerX - radius + (pointerSize * 1.6), centerY + pointerSize);
        pointers.closePath();
        pointers.moveTo(centerX + radius, centerY);
        pointers.lineTo(centerX + radius - (pointerSize * 1.6), centerY - pointerSize);
        pointers.lineTo(centerX + radius - (pointerSize * 1.6), centerY + pointerSize);
        pointers.closePath();
        graphics.fill(pointers);

        double centerMarkerRadius = Math.max(1.5, UIUtil.scaleForGUI(2));
        graphics.setPaint(LAYER_CONTROL_TEXT);
        graphics.fill(new Ellipse2D.Double(centerX - centerMarkerRadius, centerY - centerMarkerRadius,
              centerMarkerRadius * 2.0, centerMarkerRadius * 2.0));

        Font baseFont = ObjectUtility.nonNull(UIManager.getFont("Label.font"), graphics.getFont());
        float fontSize = Math.max(UIUtil.scaleForGUI(9), baseFont.getSize2D() * 0.78f);
        graphics.setFont(baseFont.deriveFont(Font.BOLD, fontSize));
        FontMetrics metrics = graphics.getFontMetrics();
        double labelGap = Math.max(4, UIUtil.scaleForGUI(7));
        double horizontalBaseline = centerY + ((metrics.getAscent() - metrics.getDescent()) / 2.0);
        drawCenteredNavigationText(graphics, layout.corewardLabel(), centerX,
              centerY - radius - labelGap, LAYER_CONTROL_BUTTON_ICON);
        drawCenteredNavigationText(graphics, layout.rimwardLabel(), centerX,
              centerY + radius + labelGap + metrics.getAscent(), LAYER_CONTROL_TEXT);
        drawRightAlignedNavigationText(graphics, layout.antiSpinwardLabel(),
              centerX - radius - labelGap, horizontalBaseline, LAYER_CONTROL_TEXT);
        drawNavigationText(graphics, layout.spinwardLabel(), centerX + radius + labelGap,
              horizontalBaseline, LAYER_CONTROL_TEXT);
    }

    private static void drawNavigationScale(Graphics2D graphics, NavigationInstrumentLayout layout) {
        Font baseFont = ObjectUtility.nonNull(UIManager.getFont("Label.font"), graphics.getFont());
        float headingFontSize = Math.max(UIUtil.scaleForGUI(9), baseFont.getSize2D() * 0.78f);
        graphics.setFont(baseFont.deriveFont(Font.BOLD, headingFontSize));
        double headingBaseline = layout.scaleBarY() - Math.max(7, UIUtil.scaleForGUI(9));
        drawNavigationText(graphics, "DISTANCE", layout.scaleBarStartX(), headingBaseline,
              MAP_LEGEND_MUTED_TEXT);
        drawRightAlignedNavigationText(graphics, layout.jumpReferenceLabel(),
              layout.x() + layout.width(), headingBaseline, LAYER_CONTROL_TEXT);

        float lineWidth = Math.max(1.0f, UIUtil.scaleForGUI(1));
        double tickRadius = Math.max(3, UIUtil.scaleForGUI(4));
        graphics.setStroke(new BasicStroke(lineWidth + Math.max(1.0f, UIUtil.scaleForGUI(2)),
              BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        graphics.setPaint(NAVIGATION_INSTRUMENT_SHADOW);
        graphics.draw(new Line2D.Double(layout.scaleBarStartX(), layout.scaleBarY(),
              layout.scaleBarEndX(), layout.scaleBarY()));
        graphics.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        graphics.setPaint(LAYER_CONTROL_BUTTON_ICON);
        graphics.draw(new Line2D.Double(layout.scaleBarStartX(), layout.scaleBarY(),
              layout.scaleBarEndX(), layout.scaleBarY()));
        graphics.draw(new Line2D.Double(layout.scaleBarStartX(), layout.scaleBarY() - tickRadius,
              layout.scaleBarStartX(), layout.scaleBarY() + tickRadius));
        graphics.draw(new Line2D.Double(layout.scaleBarEndX(), layout.scaleBarY() - tickRadius,
              layout.scaleBarEndX(), layout.scaleBarY() + tickRadius));

        graphics.setFont(baseFont.deriveFont(Font.PLAIN, headingFontSize));
        FontMetrics metrics = graphics.getFontMetrics();
        double valueBaseline = layout.scaleBarY() + metrics.getAscent() + Math.max(2, UIUtil.scaleForGUI(3));
        drawCenteredNavigationText(graphics, "0", layout.scaleBarStartX(), valueBaseline,
              MAP_LEGEND_MUTED_TEXT);
        drawCenteredNavigationText(graphics, layout.distanceLabel(), layout.scaleBarEndX(), valueBaseline,
              LAYER_CONTROL_TEXT);
    }

    private static void drawCenteredNavigationText(
          Graphics2D graphics, String text, double centerX, double baseline, Color color) {
        double x = centerX - (graphics.getFontMetrics().stringWidth(text) / 2.0);
        drawNavigationText(graphics, text, x, baseline, color);
    }

    private static void drawRightAlignedNavigationText(
          Graphics2D graphics, String text, double rightX, double baseline, Color color) {
        double x = rightX - graphics.getFontMetrics().stringWidth(text);
        drawNavigationText(graphics, text, x, baseline, color);
    }

    private static void drawNavigationText(
          Graphics2D graphics, String text, double x, double baseline, Color color) {
        double shadowOffset = Math.max(1, UIUtil.scaleForGUI(1));
        graphics.setPaint(NAVIGATION_INSTRUMENT_SHADOW);
        graphics.drawString(text, (float) (x + shadowOffset), (float) (baseline + shadowOffset));
        graphics.setPaint(color);
        graphics.drawString(text, (float) x, (float) baseline);
    }

    record NavigationInstrumentLayout(
          boolean visible,
          double x,
          double y,
          double width,
          double height,
          double compassCenterX,
          double compassCenterY,
          double compassRadius,
          double scaleBarStartX,
          double scaleBarEndX,
          double scaleBarY,
          double distanceLy,
          int maximumJumpLy) {

        static NavigationInstrumentLayout hidden(int maximumJumpLy) {
            return new NavigationInstrumentLayout(false, 0.0, 0.0, 0.0, 0.0,
                  0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, maximumJumpLy);
        }

        Rectangle2D.Double bounds() {
            return new Rectangle2D.Double(x, y, width, height);
        }

        double scaleBarPixelWidth() {
            return scaleBarEndX - scaleBarStartX;
        }

        String corewardLabel() {
            return "COREWARD";
        }

        String rimwardLabel() {
            return "RIMWARD";
        }

        String antiSpinwardLabel() {
            return "ANTI-SPINWARD";
        }

        String spinwardLabel() {
            return "SPINWARD";
        }

        String distanceLabel() {
            BigDecimal exactDistance = BigDecimal.valueOf(distanceLy);
            BigDecimal displayedDistance = exactDistance.round(NAVIGATION_DISTANCE_FORMAT).stripTrailingZeros();
            String approximationMarker = displayedDistance.compareTo(exactDistance) == 0 ? "" : "~";
            return approximationMarker + displayedDistance.toPlainString() + " LY";
        }

        String jumpReferenceLabel() {
            return "MAX JUMP " + maximumJumpLy + " LY";
        }
    }

    /**
     * Computes the map-coordinate from the screen coordinate system
     */
    private double scr2mapX(double x) {
        return (x - getWidth() / 2.0) / conf.scale - conf.centerX;
    }

    private double map2scrX(double x) {
        return getWidth() / 2.0 + (x + conf.centerX) * conf.scale;
    }

    private double scr2mapY(double y) {
        return (getHeight() / 2.0 - y) / conf.scale + conf.centerY;
    }

    private double map2scrY(double y) {
        return getHeight() / 2.0 - (y - conf.centerY) * conf.scale;
    }

    private AffineTransform getMap2ScrTransform() {
        AffineTransform transform = new AffineTransform();
        transform.translate(getWidth() / 2.0, getHeight() / 2.0);
        transform.scale(conf.scale, -conf.scale);
        transform.translate(conf.centerX, -conf.centerY);
        return transform;
    }

    MapCenter getMapCenter() {
        return new MapCenter(-conf.centerX, conf.centerY);
    }

    void restoreMapCenter(MapCenter center) {
        conf.centerX = -center.x();
        conf.centerY = center.y();
        repaint();
    }

    public void setSelectedSystem(PlanetarySystem p) {
        selectSystem(p, true);
        if (conf.scale < 4.0) {
            conf.scale = 4.0;
        }
        center(selectedSystem);
        repaint();
    }

    /**
     * Selects and centers the campaign's current system without changing the proposed route.
     */
    public void centerOnCurrentSystem() {
        PlanetarySystem currentSystem = campaign == null ? null : campaign.getCurrentSystem();
        if (currentSystem == null) {
            return;
        }

        selectSystem(currentSystem, true);
        center(currentSystem);
        notifyListeners();
    }

    /**
     * Calculate the nearest neighbor for the given point If anyone has a better algorithm than this, please, feel free
     * to exchange my brute force thing... A good idea would be a voronoi diagram and the sweep algorithm from Steven
     * Fortune.
     */
    private PlanetarySystem nearestNeighbour(double x, double y) {
        double minDiff = Double.MAX_VALUE;
        double diff;
        PlanetarySystem minPlanet = null;
        for (PlanetarySystem p : systems) {
            diff = Math.sqrt(Math.pow(x - p.getX(), 2) + Math.pow(y - p.getY(), 2));
            if (diff < minDiff) {
                minDiff = diff;
                minPlanet = p;
            }
        }
        return minPlanet;
    }

    private boolean isSystemEmpty(PlanetarySystem system) {
        return isSystemEmpty(system, now);
    }

    private static boolean isSystemEmpty(PlanetarySystem system, LocalDate date) {
        Set<Faction> factions = system.getFactionSet(date);
        if ((null == factions) || factions.isEmpty()) {
            return true;
        }

        return factions.stream()
                     .allMatch(faction -> faction.is(FactionTag.ABANDONED));
    }

    private boolean isSystemVisible(PlanetarySystem system, boolean hideEmpty) {
        if (null == system) {
            return false;
        }
        // The current planet and the selected one are always visible
        if (system.equals(campaign.getCurrentSystem()) || system.equals(selectedSystem)) {
            return true;
        }
        // viewport check
        double x = system.getX();
        double y = system.getY();
        if ((x < minX) || (x > maxX) || (y < minY) || (y > maxY)) {
            return false;
        }
        if (hideEmpty) {
            // Filter out "empty" systems
            return !isSystemEmpty(system);
        }
        return true;
    }

    /**
     * Activate and Center
     */
    private void center(PlanetarySystem p) {
        if (p == null) {
            return;
        }
        conf.centerX = -p.getX();
        conf.centerY = p.getY();
        repaint();
    }

    private void zoom(double percent, Point pos) {
        double newScale = conf.scale * percent;
        if (!Double.isFinite(percent) || (percent <= 0) || !Double.isFinite(newScale) || (newScale <= 0)) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        int anchorX = width / 2;
        int anchorY = height / 2;
        if ((pos != null) && (pos.x >= 0) && (pos.x < width) && (pos.y >= 0) && (pos.y < height)) {
            anchorX = pos.x;
            anchorY = pos.y;
        }

        double anchorMapX = scr2mapX(anchorX);
        double anchorMapY = scr2mapY(anchorY);
        conf.scale = newScale;
        conf.centerX = (anchorX - width / 2.0) / newScale - anchorMapX;
        conf.centerY = anchorMapY - (height / 2.0 - anchorY) / newScale;
        repaint();
    }

    public PlanetarySystem getSelectedSystem() {
        return selectedSystem;
    }

    public JumpPath getJumpPath() {
        return jumpPath;
    }

    void setRoutePlanningHandler(RoutePlanningHandler routePlanningHandler) {
        this.routePlanningHandler = Objects.requireNonNull(routePlanningHandler);
    }

    void selectRouteTarget(PlanetarySystem target) {
        selectSystem(target, true);
        repaint();
    }

    public void changeSelectedSystem(PlanetarySystem p) {
        selectSystem(p, true);
        notifyListeners();
    }

    private void startProposedRouteAnimation() {
          int legCount = Math.max(1, jumpPath.size() - 1);
          long adaptiveLegDuration = Math.max(PROPOSED_ROUTE_MIN_LEG_DURATION_NS,
              Math.round(PROPOSED_ROUTE_BASE_LEG_DURATION_NS / Math.sqrt(legCount)));
          proposedRouteAnimationDuration = legCount * adaptiveLegDuration;
        proposedRouteAnimationStartTime = System.nanoTime();
        proposedRouteAnimationProgress = 0.0;
        proposedRouteAnimationTimer.restart();
    }

    private void updateProposedRouteAnimation() {
        if (jumpPath.size() < 2) {
            stopProposedRouteAnimation();
            return;
        }

        long elapsedTime = System.nanoTime() - proposedRouteAnimationStartTime;
        proposedRouteAnimationProgress = Math.min(1.0,
              (double) elapsedTime / proposedRouteAnimationDuration);
        if (proposedRouteAnimationProgress >= 1.0) {
            stopProposedRouteAnimation();
        }
        repaint();
    }

    private void stopProposedRouteAnimation() {
        proposedRouteAnimationTimer.stop();
        proposedRouteAnimationProgress = 1.0;
    }

    private int getRevealedProposedRouteSystemCount() {
        if (jumpPath.isEmpty()) {
            return 0;
        }
        if (!proposedRouteAnimationTimer.isRunning()) {
            return jumpPath.size();
        }
        int legCount = jumpPath.size() - 1;
        int completedLegCount = Math.min(legCount,
              (int) Math.floor(proposedRouteAnimationProgress * legCount));
        return completedLegCount + 1;
    }

    private double getCurrentProposedRouteLegProgress() {
        int legCount = jumpPath.size() - 1;
        double routePosition = proposedRouteAnimationProgress * legCount;
        return easeOutCubic(routePosition - Math.floor(routePosition));
    }

    private void selectSystem(PlanetarySystem system, boolean animate) {
        boolean selectionChanged = !isSameSystem(selectedSystem, system);
        selectedSystem = system;
        if (!selectionChanged) {
            return;
        }

        if (animate && (system != null) && isDisplayable()) {
            selectionAnimationSystemId = system.getId();
            selectionAnimationStartTime = System.nanoTime();
            selectionAnimationProgress = 0.0;
            selectionAnimationTimer.restart();
            repaint();
        } else {
            stopSelectionAnimation();
        }
    }

    private void updateSelectionAnimation() {
        if (!isSelectionAnimationTarget(selectedSystem)) {
            stopSelectionAnimation();
            return;
        }

        long elapsedTime = System.nanoTime() - selectionAnimationStartTime;
        selectionAnimationProgress = Math.min(1.0, (double) elapsedTime / SELECTION_ANIMATION_DURATION_NS);
        if (selectionAnimationProgress >= 1.0) {
            stopSelectionAnimation();
        }
        repaint();
    }

    private void stopSelectionAnimation() {
        selectionAnimationTimer.stop();
        selectionAnimationSystemId = null;
        selectionAnimationProgress = 1.0;
    }

    private boolean isSelectionAnimationTarget(PlanetarySystem system) {
        return selectionAnimationTimer.isRunning() && (selectionAnimationSystemId != null) && (system != null)
              && selectionAnimationSystemId.equals(system.getId());
    }

    private static boolean isSameSystem(PlanetarySystem first, PlanetarySystem second) {
        if (first == second) {
            return true;
        }
        return (first != null) && (second != null) && first.getId().equals(second.getId());
    }

    /**
     * Return a planet color based on what the user has selected from the radio button options
     *
     * @param system PlanetarySystem object
     *
     * @return a Color
     */
    public Color getSystemColor(PlanetarySystem system) {
        return getSystemColor(system, getSelectedMapMode());
    }

    private Color getSystemColor(PlanetarySystem system, MapMode mapMode) {
        // color shading is from the Viridis color palettes
        long pop = system.getPopulation(campaign.getLocalDate());

        // if no population, then just return black no matter what we asked for
        if (pop == 0L) {
            return Color.BLACK;
        }

        SocioIndustrialData socio = system.getSocioIndustrial(campaign.getLocalDate());

        if (null != socio && (mapMode == MapMode.TECHNOLOGY)) {
            return switch (socio.tech) {
                case REGRESSED -> new Color(51, 51, 51);
                case F -> new Color(68, 1, 84);
                case D -> new Color(59, 82, 139);
                case C -> new Color(33, 144, 140);
                case B -> new Color(93, 200, 99);
                case A, ADVANCED -> new Color(253, 231, 37);
            };
        }
        if (null != socio && (mapMode == MapMode.INDUSTRY)) {
            return switch (socio.industry) {
                case F -> new Color(0, 0, 4);
                case D -> new Color(81, 18, 124);
                case C -> new Color(182, 54, 121);
                case B -> new Color(251, 136, 97);
                case A -> new Color(252, 253, 191);
            };
        }
        if (null != socio && (mapMode == MapMode.RAW_MATERIALS)) {
            return switch (socio.rawMaterials) {
                case F -> new Color(13, 8, 135);
                case D -> new Color(126, 3, 168);
                case C -> new Color(204, 70, 120);
                case B -> new Color(248, 148, 65);
                case A -> new Color(240, 249, 33);
            };
        }
        if (null != socio && (mapMode == MapMode.OUTPUT)) {
            return switch (socio.output) {
                case F -> new Color(0, 0, 4);
                case D -> new Color(86, 15, 110);
                case C -> new Color(187, 55, 84);
                case B -> new Color(249, 140, 10);
                case A -> new Color(252, 255, 164);
            };
        }
        if (null != socio && (mapMode == MapMode.AGRICULTURE)) {
            return switch (socio.agriculture) {
                case F -> new Color(0, 32, 77);
                case D -> new Color(66, 77, 107);
                case C -> new Color(124, 123, 120);
                case B -> new Color(188, 175, 111);
                case A -> new Color(255, 234, 70);
            };
        }

        if (mapMode == MapMode.POPULATION) {
            // numbers based roughly on deciles of population distribution in 2750
            if (pop >= 3000000000L) {
                return new Color(253, 231, 37);
            } else if (pop >= 1500000000L) {
                return new Color(180, 222, 44);
            } else if (pop >= 1000000000L) {
                return new Color(109, 205, 89);
            } else if (pop >= 500000000L) {
                return new Color(53, 183, 121);
            } else if (pop >= 300000000L) {
                return new Color(31, 158, 137);
            } else if (pop >= 200000000L) {
                return new Color(38, 130, 142);
            } else if (pop >= 100000000L) {
                return new Color(49, 104, 142);
            } else if (pop >= 25000000L) {
                return new Color(62, 74, 137);
            } else if (pop >= 1000000L) {
                return new Color(72, 40, 120);
            } else if (pop > 0L) {
                return new Color(68, 1, 84);
            } else {
                return Color.GRAY;
            }
        }

        if (mapMode == MapMode.HPG) {
            HPGRating hpg = system.getHPG(campaign.getLocalDate());
            if (null == hpg) {
                return Color.BLACK;
            }
            // use two shades of gray for C and D as this is pony express
            return switch (hpg) {
                case D -> new Color(84, 84, 84);
                case C -> new Color(168, 168, 168);
                case B -> new Color(222, 73, 104);
                case A -> new Color(252, 253, 191);
                default -> Color.BLACK;
            };
        }

        if (mapMode == MapMode.RECHARGE_STATIONS) {
            // use two shades of gray for C and D as this is pony express
            return switch (system.getNumberRechargeStations(campaign.getLocalDate())) {
                case 2 -> new Color(240, 249, 33);
                case 1 -> new Color(225, 100, 98);
                case 0 -> new Color(128, 128, 128);
                default -> Color.BLACK;
            };
        }

        if (mapMode == MapMode.ACADEMIES) {
            int academyCount = Math.clamp(system.getFilteredAcademies(campaign).size(), 0, 6);

            return switch (academyCount) {
                case 6 -> new Color(253, 231, 37);
                case 5 -> new Color(180, 222, 44);
                case 4 -> new Color(109, 205, 89);
                case 3 -> new Color(53, 183, 121);
                case 2 -> new Color(31, 158, 137);
                case 1 -> new Color(38, 130, 142);
                default -> Color.BLACK;
            };
        }

        if (mapMode == MapMode.HIRING_HALLS) {
            return switch (system.getHiringHallLevel(campaign.getLocalDate())) {
                case QUESTIONABLE -> new Color(187, 55, 84);
                case MINOR -> new Color(249, 140, 10);
                case STANDARD -> new Color(253, 231, 37);
                case GREAT -> new Color(93, 200, 99);
                default -> Color.BLACK;
            };
        }

        if (mapMode == MapMode.DISEASE_OUTBREAKS) {
            Set<InjuryType> diseases = getAllActiveDiseases(system.getId(), campaign.getLocalDate(), true);
            diseases.addAll(getAllActiveBioweapons(system.getId(), campaign.getLocalDate(), true));

            int diseaseCount = min(4, diseases.size());
            return switch (diseaseCount) {
                case 1 -> new Color(253, 231, 37);
                case 2 -> new Color(249, 140, 10);
                case 3 -> new Color(187, 55, 84);
                case 4 -> new Color(126, 3, 168);
                default -> Color.BLACK;
            };
        }

        return Color.GRAY;
    }

    private MapMode getSelectedMapMode() {
        if (optTech.isSelected()) {
            return MapMode.TECHNOLOGY;
        }
        if (optIndustry.isSelected()) {
            return MapMode.INDUSTRY;
        }
        if (optRawMaterials.isSelected()) {
            return MapMode.RAW_MATERIALS;
        }
        if (optOutput.isSelected()) {
            return MapMode.OUTPUT;
        }
        if (optAgriculture.isSelected()) {
            return MapMode.AGRICULTURE;
        }
        if (optPopulation.isSelected()) {
            return MapMode.POPULATION;
        }
        if (optHPG.isSelected()) {
            return MapMode.HPG;
        }
        if (optRecharge.isSelected()) {
            return MapMode.RECHARGE_STATIONS;
        }
        if (optAcademies.isSelected()) {
            return MapMode.ACADEMIES;
        }
        if (optHiringHalls.isSelected()) {
            return MapMode.HIRING_HALLS;
        }
        if (optDiseases.isSelected()) {
            return MapMode.DISEASE_OUTBREAKS;
        }
        return MapMode.FACTION;
    }

    /*
     * TODO: re-enable later
     * private void openPlanetEventEditor(Planet p) {
     * NewPlanetaryEventDialog editor = new NewPlanetaryEventDialog(null, campaign,
     * selectedSystem);
     * editor.setVisible(true);
     * List<Planet.PlanetaryEvent> result = editor.getChangedEvents();
     * if ((null != result) && !result.isEmpty()) {
     * Planets.getInstance().updatePlanetaryEvents(p.getId(), result, true);
     * Planets.getInstance().recalcHPGNetwork();
     * repaint();
     * notifyListeners();
     * }
     * }
     */

    /**
     * Opens the GM-only planetary system editor pre-selected on the given system. Refreshes and repaints the map after
     * the dialog closes so saved planetary overrides are drawn from the campaign overlay.
     */
    private void openPlanetarySystemEditor(PlanetarySystem system) {
        if ((system == null) || !campaign.isGM()) {
            return;
        }
        PlanetarySystemEditorDialog dialog = new PlanetarySystemEditorDialog(hqView.getFrame(), campaign);
        dialog.selectSystemById(system.getId());
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                refreshSystemsFromCampaign();
                repaint();
            }
        });
        dialog.setVisible(true);
    }

    private final transient List<ActionListener> listeners = new ArrayList<>();

    public void addActionListener(ActionListener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    @Deprecated(since = "0.51.0", forRemoval = true)
    public void removeActionListener(ActionListener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_FIRST, "refresh");
        listeners.forEach(l -> l.actionPerformed(ev));
    }

    public boolean isFactionsSelected() {
        return optFactions.isSelected();
    }
}

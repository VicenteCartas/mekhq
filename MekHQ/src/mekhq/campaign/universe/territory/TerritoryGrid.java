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
package mekhq.campaign.universe.territory;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.Nullable;
import megamek.common.universe.FactionTag;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Factions;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.Systems;

/**
 * Who holds the space between the stars on a date: a hex grid laid over every system, each cell held by the realms
 * whose settled systems lie in it (or, when none do, near it). The interstellar maps draw their borders from it. It
 * reads only the systems and their factions, so it can be built on any thread.
 *
 * @param date     the date it was read on
 * @param minColumn its westernmost column
 * @param maxColumn its easternmost column; less than {@code minColumn} when the grid is empty
 * @param minRow    its lowest row
 * @param maxRow    its highest row; less than {@code minRow} when the grid is empty
 * @param cells     every cell, by hex
 */
public record TerritoryGrid(LocalDate date, int minColumn, int maxColumn, int minRow, int maxRow,
      Map<TerritoryHex, TerritoryCell> cells) {
    /** The light years between the flat sides of a cell. */
    public static final double HEX_SIZE = 30.0;
    /** The light years between the centres of neighbouring columns. */
    public static final double SPACING_X = HEX_SIZE * Math.sqrt(3) / 2.0;
    /** The light years from a cell's centre to its corners. */
    public static final double HEX_RADIUS = HEX_SIZE / Math.sqrt(3);
    /** The empty cells kept around the outermost systems. */
    public static final int MARGIN = 2;
    /** How far around a cell's centre a system may lend it its owner when no settled system lies inside. */
    private static final int NEARBY_RADIUS = (int) Math.round(HEX_SIZE * 1.3);
    private static final double[] CORNER_X = new double[6];
    private static final double[] CORNER_Y = new double[6];

    static {
        for (int corner = 0; corner < 6; corner++) {
            CORNER_X[corner] = Math.cos(corner * Math.PI / 3.0);
            CORNER_Y[corner] = Math.sin(corner * Math.PI / 3.0);
        }
    }

    /**
     * A run of neighbouring cells held by the same realms.
     *
     * @param factions their holders, in short name order; empty when no one holds them
     * @param semantic what the run means on the map
     * @param cells    its cells, in column then row order of their discovery
     */
    public record Region(List<Faction> factions, TerritorySemantic semantic, List<TerritoryCell> cells) {
        public Region {
            factions = List.copyOf(factions);
            cells = List.copyOf(cells);
        }
    }

    public TerritoryGrid {
        cells = Map.copyOf(cells);
    }

    /** Whether the grid has no cells. */
    public boolean isEmpty() {
        return maxColumn < minColumn || maxRow < minRow;
    }

    /**
     * Reads the grid on {@code date} over the bounds of {@code systems}, asking {@code registry} which systems lie
     * near each cell.
     */
    public static TerritoryGrid build(Collection<PlanetarySystem> systems, Systems registry, LocalDate date) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (PlanetarySystem system : systems) {
            if (system == null || system.getX() == null || system.getY() == null) {
                continue;
            }
            minX = Math.min(minX, system.getX());
            maxX = Math.max(maxX, system.getX());
            minY = Math.min(minY, system.getY());
            maxY = Math.max(maxY, system.getY());
        }
        if (minX > maxX) {
            return new TerritoryGrid(date, 0, -1, 0, -1, Map.of());
        }
        int minColumn = (int) Math.floor(minX / SPACING_X) - MARGIN;
        int maxColumn = (int) Math.ceil(maxX / SPACING_X) + MARGIN;
        int minRow = (int) Math.floor(minY / HEX_SIZE) - MARGIN;
        int maxRow = (int) Math.ceil(maxY / HEX_SIZE) + MARGIN;
        Faction independent = Factions.getInstance().getFaction("IND");
        Map<TerritoryHex, TerritoryCell> cells = new HashMap<>();
        Path2D.Double outline = new Path2D.Double();
        Known known = new Known(date);
        for (int column = minColumn; column <= maxColumn; column++) {
            for (int row = minRow; row <= maxRow; row++) {
                TerritoryHex hex = new TerritoryHex(column, row);
                double centerX = centerX(column);
                double centerY = centerY(column, row);
                TerritoryClassification classification = classifyHex(registry, outline, centerX, centerY, known,
                      independent);
                AdministrativeKey administration = classifyAdministrativeKey(classification.factions(),
                      classification.evidenceSystems(), known, centerX, centerY);
                cells.put(hex, new TerritoryCell(hex, centerX, centerY, classification.factions(), administration));
            }
        }
        return new TerritoryGrid(date, minColumn, maxColumn, minRow, maxRow, cells);
    }

    /** The light years spinward of Terra of the centres of the cells in {@code column}. */
    public static double centerX(int column) {
        return column * SPACING_X;
    }

    /** The light years coreward of Terra of the centre of the cell at {@code column}, {@code row}. */
    public static double centerY(int column, int row) {
        // Java's remainder keeps the sign, so negative odd columns sit half a cell the other way.
        return row * HEX_SIZE + (column % 2) * HEX_SIZE / 2.0;
    }

    /** Traces the outline of the cell centred at {@code centerX}, {@code centerY} into {@code path}. */
    public static void outline(Path2D path, double centerX, double centerY) {
        path.reset();
        path.moveTo(centerX + HEX_RADIUS * CORNER_X[0], centerY + HEX_RADIUS * CORNER_Y[0]);
        for (int corner = 1; corner < 6; corner++) {
            path.lineTo(centerX + HEX_RADIUS * CORNER_X[corner], centerY + HEX_RADIUS * CORNER_Y[corner]);
        }
        path.closePath();
    }

    /** The six cells that share a side with {@code hex}. */
    public static List<TerritoryHex> neighbors(TerritoryHex hex) {
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

    /** Whether {@code first} comes before {@code second} in column then row order. */
    public static boolean isBefore(TerritoryHex first, TerritoryHex second) {
        return (first.column() < second.column())
              || ((first.column() == second.column()) && (first.row() < second.row()));
    }

    /** Whether no one but the abandoned holds {@code system} on {@code date}. */
    public static boolean isSystemEmpty(PlanetarySystem system, LocalDate date) {
        return isEmpty(system.getFactionSet(date));
    }

    private static boolean isEmpty(@Nullable Set<Faction> factions) {
        if ((null == factions) || factions.isEmpty()) {
            return true;
        }
        for (Faction faction : factions) {
            if (!faction.is(FactionTag.ABANDONED)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Who holds the cell centred at {@code centerX}, {@code centerY} on {@code date}: the realms of the settled systems
     * inside it, independents only when no realm is there, or when none lie inside the realms of those nearby.
     */
    public static TerritoryClassification classifyHex(Systems registry, double centerX, double centerY,
          LocalDate date, @Nullable Faction independent) {
        return classifyHex(registry, new Path2D.Double(), centerX, centerY, new Known(date), independent);
    }

    private static TerritoryClassification classifyHex(Systems registry, Path2D outline, double centerX,
          double centerY, Known known, @Nullable Faction independent) {
        outline(outline, centerX, centerY);
        List<PlanetarySystem> nearbySystems = registry.getNearbySystems(centerX, centerY, NEARBY_RADIUS);
        Set<Faction> hexFactions = new HashSet<>();
        List<PlanetarySystem> evidenceSystems = new ArrayList<>();
        for (PlanetarySystem system : nearbySystems) {
            Set<Faction> owners = known.owners(system);
            if (!isEmpty(owners) && outline.contains(system.getX(), system.getY())) {
                evidenceSystems.add(system);
                for (Faction faction : owners) {
                    if (!faction.equals(independent)) {
                        hexFactions.add(faction);
                    }
                }
            }
        }
        if (hexFactions.isEmpty()) {
            evidenceSystems.clear();
            for (PlanetarySystem system : nearbySystems) {
                Set<Faction> owners = known.owners(system);
                if (!isEmpty(owners)) {
                    evidenceSystems.add(system);
                    hexFactions.addAll(owners);
                }
            }
        }
        if (hexFactions.size() > 1) {
            hexFactions.remove(independent);
        }
        List<Faction> factions = new ArrayList<>(hexFactions);
        factions.sort(Comparator.comparing(Faction::getShortName));
        return new TerritoryClassification(factions, evidenceSystems);
    }

    /**
     * The administrative division of a cell held by one realm alone: that of its nearest evidence system the realm
     * alone holds and whose administration is fully named; null when there is none.
     */
    public static @Nullable AdministrativeKey classifyAdministrativeKey(List<Faction> cellFactions,
          List<PlanetarySystem> evidenceSystems, LocalDate date, double centerX, double centerY) {
        return classifyAdministrativeKey(cellFactions, evidenceSystems, new Known(date), centerX, centerY);
    }

    private static @Nullable AdministrativeKey classifyAdministrativeKey(List<Faction> cellFactions,
          List<PlanetarySystem> evidenceSystems, Known known, double centerX, double centerY) {
        if ((cellFactions.size() != 1) || evidenceSystems.isEmpty()) {
            return null;
        }
        Faction owner = cellFactions.getFirst();
        PlanetarySystem nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (PlanetarySystem system : evidenceSystems) {
            Set<Faction> factions = known.owners(system);
            if ((factions == null) || (factions.size() != 1) || !factions.contains(owner)
                  || !isNamed(known.administration(system))) {
                continue;
            }
            double distance = Point2D.distanceSq(centerX, centerY, system.getX(), system.getY());
            if ((nearest == null) || (distance < nearestDistance)
                  || ((distance == nearestDistance) && (system.getId().compareTo(nearest.getId()) < 0))) {
                nearest = system;
                nearestDistance = distance;
            }
        }
        return nearest == null ? null : new AdministrativeKey(owner, known.administration(nearest));
    }

    private static boolean isNamed(List<String> path) {
        if (path.isEmpty()) {
            return false;
        }
        for (String component : path) {
            if ((component == null) || component.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** The grid's regions. */
    public List<Region> regions() {
        return regions(cells);
    }

    /**
     * The runs of neighbouring {@code cells} held by the same realms, seeded in column then row order, each with what it
     * means on the map.
     */
    public static List<Region> regions(Map<TerritoryHex, TerritoryCell> cells) {
        List<TerritoryCell> orderedCells = new ArrayList<>(cells.values());
        orderedCells.sort(Comparator.comparingInt((TerritoryCell cell) -> cell.hex().column())
              .thenComparingInt(cell -> cell.hex().row()));
        Set<TerritoryHex> visited = new HashSet<>();
        List<Region> regions = new ArrayList<>();
        for (TerritoryCell seed : orderedCells) {
            if (!visited.add(seed.hex())) {
                continue;
            }
            List<TerritoryCell> regionCells = new ArrayList<>();
            List<TerritoryHex> pending = new ArrayList<>();
            pending.add(seed.hex());
            for (int pendingIndex = 0; pendingIndex < pending.size(); pendingIndex++) {
                TerritoryHex hex = pending.get(pendingIndex);
                regionCells.add(cells.get(hex));
                for (TerritoryHex neighborHex : neighbors(hex)) {
                    TerritoryCell neighbor = cells.get(neighborHex);
                    if ((neighbor != null) && seed.factions().equals(neighbor.factions())
                          && visited.add(neighborHex)) {
                        pending.add(neighborHex);
                    }
                }
            }
            regions.add(new Region(seed.factions(), classifySemantic(seed.factions(), regionCells, cells),
                  regionCells));
        }
        return List.copyOf(regions);
    }

    /**
     * What a region held by {@code factions} means: disputed when more than one realm holds it; unclaimed, open to the
     * edge or shut in, when none does; an enclave when one realm's ground lies wholly inside another single realm's.
     */
    public static TerritorySemantic classifySemantic(List<Faction> factions, List<TerritoryCell> regionCells,
          Map<TerritoryHex, TerritoryCell> cells) {
        if (factions.size() > 1) {
            return TerritorySemantic.DISPUTED;
        }
        Set<TerritoryHex> regionHexes = new HashSet<>();
        for (TerritoryCell cell : regionCells) {
            regionHexes.add(cell.hex());
        }
        boolean touchesEdge = false;
        for (TerritoryCell cell : regionCells) {
            for (TerritoryHex neighborHex : neighbors(cell.hex())) {
                if (!cells.containsKey(neighborHex)) {
                    touchesEdge = true;
                    break;
                }
            }
            if (touchesEdge) {
                break;
            }
        }
        if (factions.isEmpty()) {
            return touchesEdge ? TerritorySemantic.UNCLAIMED_EXTERIOR : TerritorySemantic.UNCLAIMED_POCKET;
        }
        if (touchesEdge) {
            return TerritorySemantic.SOVEREIGN;
        }
        Faction owner = factions.getFirst();
        Faction surroundingOwner = null;
        for (TerritoryCell cell : regionCells) {
            for (TerritoryHex neighborHex : neighbors(cell.hex())) {
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

    /**
     * The owners and administration of the systems on one date, each read once: a cell asks them of every system near
     * it, and its neighbours ask the same systems again.
     */
    private static final class Known {
        private final LocalDate date;
        private final Map<PlanetarySystem, Set<Faction>> owners = new IdentityHashMap<>();
        private final Map<PlanetarySystem, List<String>> administrations = new IdentityHashMap<>();

        Known(LocalDate date) {
            this.date = date;
        }

        @Nullable Set<Faction> owners(PlanetarySystem system) {
            if (owners.containsKey(system)) {
                return owners.get(system);
            }
            Set<Faction> read = system.getFactionSet(date);
            owners.put(system, read);
            return read;
        }

        List<String> administration(PlanetarySystem system) {
            return administrations.computeIfAbsent(system, read -> read.getAdministration(date));
        }
    }
}

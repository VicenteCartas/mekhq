/*
 * Copyright (c) 2019 The MegaMek Team. All rights reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ.  If not, see <http://www.gnu.org/licenses/>.
 */

package mekhq.gui.control;

import megamek.client.ui.Messages;
import megamek.client.ui.swing.AdvancedSearchDialog;
import megamek.client.ui.swing.MechTileset;
import megamek.client.ui.swing.MechViewPanel;
import megamek.common.*;
import megamek.common.loaders.EntityLoadingException;
import megamek.common.util.EncodeControl;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.finances.Money;
import mekhq.campaign.unit.Unit;
import mekhq.campaign.unit.UnitTechProgression;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class UnitSearchControl extends JPanel {
    private static MechTileset unitTileset;
    private static ResourceBundle resourceMap;

    private List<PropertyChangeListener> listeners;

    private final Campaign campaign;
    private final List<Map.Entry<MechSummary, Unit>> units;
    private Integer unitType;
    private Integer unitWeightClass;
    private final AdvancedSearchDialog advancedSearchDialog;

    private Map.Entry<MechSummary, Unit> selectedUnit;

    private TableRowSorter<UnitTableModel> sorter;
    private UnitTableModel unitModel;
    private MechSearchFilter searchFilter;

    private JLabel lblUnitType;
    private JComboBox<String> comboUnitType;
    private JLabel lblUnitWeight;
    private JComboBox<String> comboUnitWeight;
    private JLabel lblFilter;
    private JLabel lblImage;
    private JPanel panelFilterBtns;
    private JPanel panelLeft;
    private JScrollPane scrTableUnits;
    private MechViewPanel panelMekView;
    private JTable tableUnits;
    private JTextField txtFilter;
    private JSplitPane splitMain;
    private JButton btnAdvSearch;
    private JButton btnResetSearch;
    private JPanel panelSearchBtns;

    public UnitSearchControl(
            Frame parent,
            Campaign campaign,
            List<Unit> possibleUnits,
            Integer unitType,
            Integer unitWeightClass) {
        this.listeners = new ArrayList<>();
        this.campaign = campaign;
        this.unitType = unitType;
        this.unitWeightClass = unitWeightClass;

        this.advancedSearchDialog = new AdvancedSearchDialog(parent, campaign.getCalendar().get(GregorianCalendar.YEAR));
        resourceMap = ResourceBundle.getBundle("mekhq.resources.UnitSearchControl", new EncodeControl()); //$NON-NLS-1$

        if (possibleUnits != null) {
            this.units = new ArrayList<>(possibleUnits.size());

            List<MechSummary> summaries = possibleUnits.stream().map(Unit::toSummary).collect(Collectors.toList());
            for(int i = 0; i < possibleUnits.size(); i ++) {
                this.units.add(new AbstractMap.SimpleImmutableEntry<>(summaries.get(i), possibleUnits.get(i)));
            }
        } else {
            MechSummary[] summaries = MechSummaryCache.getInstance().getAllMechs();
            this.units = Arrays
                    .stream(summaries)
                    .map(s -> new AbstractMap.SimpleImmutableEntry<MechSummary, Unit>(s, null))
                    .collect(Collectors.toList());
        }

        initComponents();
        setUserPreferences();
        setUnitsModel();
    }

    public Optional<Unit> getSelectedUnit() {
        if(this.selectedUnit == null) {
            return Optional.empty();
        }
        return Optional.of(this.selectedUnit.getValue());
    }

    public Optional<Entity> getSelectedEntity() {
        final String METHOD_NAME = "getSelectedEntity";
        if(this.selectedUnit == null) {
            return Optional.empty();
        }

        try
        {
            return Optional.of(
                    new MechFileParser(
                            this.selectedUnit.getKey().getSourceFile(),
                            this.selectedUnit.getKey().getEntryName())
                            .getEntity());
        }
        catch (EntityLoadingException ele) {
            MekHQ.getLogger().error(getClass(), METHOD_NAME, ele);
            return Optional.empty();
        }
    }

    public void addListSelectionListener(PropertyChangeListener x) {
        this.listeners.add(x);
    }

    public void removeListSelectionListener(PropertyChangeListener x) {
        this.listeners.remove(x);
    }

    private void initComponents() {
        GridBagConstraints gridBagConstraints;
        this.setLayout(new BorderLayout());

        createFilterPanel();
        createUnitTypeArea();
        createUnitWeightArea();
        createFilterArea();
        createImageArea();
        createAdvancedSearchArea();
        createUnitsTableArea();
        finalizeLayout();
    }

    private void createFilterPanel() {
        this.panelFilterBtns = new JPanel();
        this.panelFilterBtns.setName("panelFilterBtns");
        this.panelFilterBtns.setLayout(new GridBagLayout());
    }

    private void createUnitTypeArea() {
        this.lblUnitType = new JLabel();
        this.lblUnitType.setName("lblUnitType");
        this.lblUnitType.setText(resourceMap.getString("lblUnitType.text"));

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        this.panelFilterBtns.add(this.lblUnitType, gridBagConstraints);

        this.comboUnitType = new JComboBox<>();
        this.comboUnitType.setName("comboUnitType");
        this.comboUnitType.setPreferredSize(new Dimension(200, 27));
        this.comboUnitType.addActionListener(x -> filterUnits());

        DefaultComboBoxModel<String> unitTypeModel = new DefaultComboBoxModel<>();
        if (this.unitType != null) {
            String unitTypeName = UnitType.getTypeDisplayableName(this.unitType);
            unitTypeModel.addElement(unitTypeName);
            unitTypeModel.setSelectedItem(unitTypeName);
            this.comboUnitType.setEnabled(false);
        } else {
            for (int i = 0; i < UnitType.SIZE; i++) {
                unitTypeModel.addElement(UnitType.getTypeDisplayableName(i));
            }
            unitTypeModel.setSelectedItem(UnitType.getTypeDisplayableName(UnitType.MEK));
        }

        this.comboUnitType.setModel(unitTypeModel);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        this.panelFilterBtns.add(this.comboUnitType, gridBagConstraints);
    }

    private void createUnitWeightArea() {
        this.lblUnitWeight = new JLabel();
        this.lblUnitWeight.setName("lblUnitWeight"); // NOI18N
        this.lblUnitWeight.setText(resourceMap.getString("lblWeight.text")); // NOI18N

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        this.panelFilterBtns.add(this.lblUnitWeight, gridBagConstraints);

        this.comboUnitWeight = new JComboBox<>();
        this.comboUnitWeight.setName("comboUnitWeight");
        this.comboUnitWeight.setPreferredSize(new Dimension(200, 27));
        this.comboUnitWeight.addActionListener(x -> filterUnits());

        DefaultComboBoxModel<String> unitWeightModel = new DefaultComboBoxModel<>();
        if (this.unitWeightClass != null) {
            String weightClassName = EntityWeightClass.getClassName(this.unitWeightClass);
            unitWeightModel.addElement(weightClassName);
            unitWeightModel.setSelectedItem(weightClassName);
            this.comboUnitWeight.setEnabled(false);
        } else {
            for (int i = 0; i < EntityWeightClass.SIZE; i++) {
                unitWeightModel.addElement(EntityWeightClass.getClassName(i));
            }
            unitWeightModel.addElement("All");
            unitWeightModel.setSelectedItem(EntityWeightClass.getClassName(EntityWeightClass.WEIGHT_LIGHT));
        }

        this.comboUnitWeight.setModel(unitWeightModel);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        this.panelFilterBtns.add(this.comboUnitWeight, gridBagConstraints);
    }

    private void createFilterArea() {
        this.lblFilter = new JLabel();
        this.lblFilter.setName("lblFilter");
        this.lblFilter.setText(resourceMap.getString("lblFilter.text"));

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        this.panelFilterBtns.add(this.lblFilter, gridBagConstraints);

        this.txtFilter = new JTextField();
        this.txtFilter.setName("txtFilter");
        this.txtFilter.setText(resourceMap.getString("txtFilter.text"));
        this.txtFilter.setPreferredSize(new Dimension(200, 28));
        this.txtFilter.getDocument().addDocumentListener(
                new DocumentListener() {
                    public void changedUpdate(DocumentEvent e) {
                        filterUnits();
                    }
                    public void insertUpdate(DocumentEvent e) {
                        filterUnits();
                    }
                    public void removeUpdate(DocumentEvent e) {
                        filterUnits();
                    }
                });

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        this.panelFilterBtns.add(this.txtFilter, gridBagConstraints);
    }

    private void createImageArea() {
        this.lblImage = new JLabel();
        this.lblImage.setName("lblImage");
        this.lblImage.setHorizontalAlignment(SwingConstants.CENTER);
        this.lblImage.setText(resourceMap.getString("lblImage.text"));

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 3;
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        this.panelFilterBtns.add(this.lblImage, gridBagConstraints);
    }

    private void createAdvancedSearchArea() {
        this.btnAdvSearch = new JButton();
        this.btnAdvSearch.setText(megamek.client.ui.Messages.getString("MechSelectorDialog.AdvSearch")); //$NON-NLS-1$
        this.btnAdvSearch.addActionListener(evt -> {
            searchFilter = this.advancedSearchDialog.showDialog();
            this.btnResetSearch.setEnabled(searchFilter != null);
            filterUnits();
        });

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        this.panelSearchBtns.add(this.btnAdvSearch, gridBagConstraints);

        this.btnResetSearch = new JButton();
        this.btnResetSearch.setText(Messages.getString("MechSelectorDialog.Reset")); //$NON-NLS-1$
        this.btnResetSearch.setEnabled(false);
        this.btnResetSearch.addActionListener(evt -> {
            this.advancedSearchDialog.clearValues();
            searchFilter = null;
            this.btnResetSearch.setEnabled(false);
            filterUnits();
        });

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        this.panelSearchBtns.add(this.btnResetSearch, gridBagConstraints);

        this.panelSearchBtns = new JPanel();
        this.panelSearchBtns.setName("panelSearchBtns");
        this.panelSearchBtns.setLayout(new GridBagLayout());

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 0);
        this.panelFilterBtns.add(this.panelSearchBtns, gridBagConstraints);
    }

    private void createUnitsTableArea() {
        this.unitModel = new UnitTableModel();

        this.sorter = new TableRowSorter<>(this.unitModel);
        this.sorter.setComparator(UnitTableModel.COL_COST, new UnitSearchControl.MoneySorter());

        this.tableUnits = new JTable();
        tableUnits.setName("tableUnits"); // NOI18N
        tableUnits.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableUnits.setModel(this.unitModel);
        tableUnits.setRowSorter(this.sorter);
        tableUnits.getSelectionModel().addListSelectionListener(this::unitSelectedChanged);

        TableColumn column;
        for (int i = 0; i < UnitTableModel.N_COL; i++) {
            column = tableUnits.getColumnModel().getColumn(i);
            if (i == UnitTableModel.COL_CHASSIS) {
                column.setPreferredWidth(125);
            } else if(i == UnitTableModel.COL_MODEL ||
                    i == UnitTableModel.COL_COST) {
                column.setPreferredWidth(75);
            } else if(i == UnitTableModel.COL_WEIGHT ||
                    i == UnitTableModel.COL_BV) {
                column.setPreferredWidth(50);
            } else {
                column.setPreferredWidth(25);
            }
            column.setCellRenderer(unitModel.getRenderer());
        }

        this.scrTableUnits = new JScrollPane();
        this.scrTableUnits.setName("scrTableUnits"); // NOI18N
        this.scrTableUnits.setPreferredSize(new Dimension(500, 400));
        this.scrTableUnits.setViewportView(tableUnits);
    }

    private void finalizeLayout() {
        this.panelLeft = new JPanel();
        this.panelLeft.setLayout(new BorderLayout());
        this.panelLeft.add(this.panelFilterBtns, BorderLayout.PAGE_START);
        this.panelLeft.add(this.scrTableUnits, BorderLayout.CENTER);

        this.panelMekView = new MechViewPanel();

        this.splitMain = new JSplitPane();
        this.splitMain = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.panelLeft, this.panelMekView);
        this.splitMain.setOneTouchExpandable(true);
        this.splitMain.setResizeWeight(0.0);
        this.add(this.splitMain, BorderLayout.CENTER);
    }

    private void setUserPreferences() {
    }

    private void setUnitsModel() {
        final String METHOD_NAME = "setUnitsModel";
        if (this.units != null) {
            this.unitModel.setData(this.units);
        } else {
            MekHQ.getLogger().error(
                    getClass(),
                    METHOD_NAME,
                    "No units to filter. This is not expected, please report a bug.");
        }
        filterUnits();
    }

    private void filterUnits() {
        RowFilter<UnitTableModel, Integer> unitTypeFilter;
        final int nClass = this.comboUnitWeight.getSelectedIndex();
        final int nUnit = this.comboUnitType.getSelectedIndex();
        final int year = this.campaign.getCalendar().get(GregorianCalendar.YEAR);

        //If current expression doesn't parse, don't update.
        try {
            unitTypeFilter = new RowFilter<UnitTableModel,Integer>() {
                @Override
                public boolean include(Entry<? extends UnitTableModel, ? extends Integer> entry) {
                    UnitTableModel unitModel = entry.getModel();
                    Map.Entry<MechSummary, Unit> unit = unitModel.getUnit(entry.getIdentifier());
                    ITechnology tech = UnitTechProgression.getProgression(unit.getKey(), campaign.getTechFaction(), true);

                    if (/*year limits*/
                            (!campaign.getCampaignOptions().limitByYear() || unit.getKey().getYear() <= year) &&
                            /*Clan/IS limits*/
                            (campaign.getCampaignOptions().allowClanPurchases() || !TechConstants.isClan(unit.getKey().getType())) &&
                            (campaign.getCampaignOptions().allowISPurchases() || TechConstants.isClan(unit.getKey().getType())) &&
                            /* Canon */
                            (unit.getKey().isCanon() || !campaign.getCampaignOptions().allowCanonOnly()) &&
                            /* Weight */
                            (unit.getKey().getWeightClass() == nClass || nClass == EntityWeightClass.SIZE) &&
                            /* Technology Level */
                            (tech != null) && campaign.isLegal(tech) &&
                            /*Unit type*/
                            (nUnit == UnitType.SIZE || unit.getKey().getUnitType().equals(UnitType.getTypeName(nUnit))) &&
                            (searchFilter == null || MechSearchFilter.isMatch(unit.getKey(), searchFilter))) {
                        if(txtFilter.getText().length() > 0) {
                            String text = txtFilter.getText();
                            return unit.getKey().getName().toLowerCase().contains(text.toLowerCase());
                        }
                        return true;
                    }
                    return false;
                }
            };
        } catch (java.util.regex.PatternSyntaxException e) {
            return;
        }
        sorter.setRowFilter(unitTypeFilter);
    }

    private void unitSelectedChanged(ListSelectionEvent evt) {
        final String METHOD_NAME = "unitSelectedChanged(ListSelectionEvent)"; //$NON-NLS-1$

        Map.Entry<MechSummary, Unit> oldValue;
        oldValue = this.selectedUnit;

        int selectedRowIndex = this.tableUnits.getSelectedRow();
        if(selectedRowIndex < 0) {
            this.selectedUnit = null;
        } else {
            int selectedModelIndex = this.tableUnits.convertRowIndexToModel(selectedRowIndex);
            this.selectedUnit = this.units.get(selectedModelIndex);
        }

        refreshUnitView();
        PropertyChangeEvent pce = new PropertyChangeEvent(
                this,
                "selectedUnit",
                oldValue,
                this.selectedUnit);
        for (PropertyChangeListener listener : this.listeners) {
            listener.propertyChange(pce);
        }
    }

    void refreshUnitView() {
        final String METHOD_NAME = "refreshUnitView()";

        if (this.selectedUnit == null) {
            panelMekView.reset();
            lblImage.setIcon(null);
            return;
        }

        Optional<Entity> selectedEntity = getSelectedEntity();
        if (!selectedEntity.isPresent()) {
            panelMekView.reset();
            lblImage.setIcon(null);
            return;
        }

        panelMekView.setMech(selectedEntity.get(), true);

        if (unitTileset == null) {
            unitTileset = new MechTileset(Configuration.unitImagesDir());
            try {
                unitTileset.loadFromFile("mechset.txt");
            } catch (IOException ex) {
                MekHQ.getLogger().error(getClass(), METHOD_NAME, ex);
                return;
            }
        }

        Image unitImage = unitTileset.imageFor(selectedEntity.get(), lblImage, -1);
        if(unitImage != null) {
            lblImage.setIcon(new ImageIcon(unitImage));
        }
    }

    /**
     * A table model for displaying units
     */
    public class UnitTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 8472587304279640434L;
        private final static int COL_MODEL = 0;
        private final static int COL_CHASSIS = 1;
        private final static int COL_WEIGHT = 2;
        private final static int COL_BV = 3;
        private final static int COL_YEAR = 4;
        private final static int COL_COST = 5;
        private final static int N_COL = 6;

        private List<Map.Entry<MechSummary, Unit>> data = new ArrayList<>();

        public UnitTableModel() {
        }

        public void setData(List<Map.Entry<MechSummary, Unit>> units) {
            this.data = units;
            fireTableDataChanged();
        }

        public int getRowCount() {
            return data.size();
        }

        public int getColumnCount() {
            return N_COL;
        }

        public int getAlignment(int col) {
            switch(col) {
                case COL_MODEL:
                case COL_CHASSIS:
                    return SwingConstants.LEFT;
                default:
                    return SwingConstants.RIGHT;
            }
        }

        @Override
        public String getColumnName(int column) {
            switch(column) {
                case COL_MODEL:
                    return "Model";
                case COL_CHASSIS:
                    return "Chassis";
                case COL_WEIGHT:
                    return "Weight";
                case COL_BV:
                    return "BV";
                case COL_YEAR:
                    return "Year";
                case COL_COST:
                    return "Price";
                default:
                    return "?";
            }
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }

        public Map.Entry<MechSummary, Unit> getUnit(int index) {
            return data.get(index);
        }

        public Object getValueAt(int row, int col) {
            Map.Entry<MechSummary, Unit> entry = data.get(row);
            if(col == COL_MODEL) {
                return entry.getKey().getModel();
            }
            if(col == COL_CHASSIS) {
                return entry.getKey().getChassis();
            }
            if(col == COL_WEIGHT) {
                return entry.getKey().getTons();
            }
            if(col == COL_BV) {
                return entry.getKey().getBV();
            }
            if(col == COL_YEAR) {
                return entry.getKey().getYear();
            }
            if(col == COL_COST) {
                if (entry.getValue() != null) {
                    return entry.getValue().getSellValue().toAmountAndSymbolString();
                } else {
                    return getPurchasePrice(entry.getKey()).toAmountAndSymbolString();
                }
            }

            return "?";
        }

        private Money getPurchasePrice(MechSummary ms) {
            Money cost = Money.of(ms.getCost());
            if(ms.getUnitType().equals(UnitType.getTypeName(UnitType.INFANTRY))
                    || ms.getUnitType().equals(UnitType.getTypeName(UnitType.BATTLE_ARMOR))) {
                cost = Money.of(ms.getAlternateCost());
            }

            if(TechConstants.isClan(ms.getType())) {
                cost = cost.multipliedBy(campaign.getCampaignOptions().getClanPriceModifier());
            }

            return cost;
        }

        public UnitSearchControl.UnitTableModel.Renderer getRenderer() {
            return new UnitSearchControl.UnitTableModel.Renderer();
        }

        public class Renderer extends DefaultTableCellRenderer {
            public Component getTableCellRendererComponent(
                    JTable table,
                    Object value,
                    boolean isSelected,
                    boolean hasFocus,
                    int row,
                    int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setOpaque(true);
                int actualCol = table.convertColumnIndexToModel(column);
                setHorizontalAlignment(getAlignment(actualCol));

                return this;
            }
        }
    }

    /**
     * A sorter for money values.
     */
    public class MoneySorter implements Comparator<String> {
        @Override
        public int compare(String s0, String s1) {
            final String METHOD_NAME = "compare";

            long l0 = 0;
            try {
                l0 = DecimalFormat.getInstance().parse(s0.substring(0, s0.indexOf(" ")).replace(",", "")).longValue();
            } catch (ParseException e) {
                MekHQ.getLogger().error(getClass(), METHOD_NAME, e);
            }

            long l1 = 0;
            try {
                l1 = DecimalFormat.getInstance().parse(s1.substring(0, s1.indexOf(" ")).replace(",", "")).longValue();
            } catch (ParseException e) {
                MekHQ.getLogger().error(getClass(), METHOD_NAME, e);
            }
            return ((Comparable<Long>)l0).compareTo(l1);
        }
    }
}

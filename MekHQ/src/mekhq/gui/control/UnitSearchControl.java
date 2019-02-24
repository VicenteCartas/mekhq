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
import megamek.common.logging.LogLevel;
import megamek.common.util.EncodeControl;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.finances.Money;
import mekhq.campaign.unit.Unit;
import mekhq.campaign.unit.UnitOrder;
import mekhq.campaign.unit.UnitTechProgression;
import mekhq.gui.dialog.UnitSelectorDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class UnitSearchControl extends JPanel {
    private static MechTileset unitTileset;
    private static ResourceBundle resourceMap;

    private final Campaign campaign;
    private final Optional<List<Unit>> units;
    private final MechSummary[] unitsSummary;
    private final Optional<UnitType> unitType;
    private final Optional<Integer> unitWeightClass;

    private Unit selectedUnit;
    private Entity selectedEntity;

    private final AdvancedSearchDialog advancedSearchDialog;

    private TableRowSorter<UnitSearchControl.MechTableModel> sorter;
    private UnitSearchControl.MechTableModel unitModel;
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
            Optional<List<Unit>> units,
            Optional<UnitType> unitType,
            Optional<Integer> unitWeightClass) {
        this.campaign = campaign;
        this.units = units;
        this.unitType = unitType;
        this.unitWeightClass = unitWeightClass;

        resourceMap = ResourceBundle.getBundle("mekhq.resources.UnitSearchControl", new EncodeControl()); //$NON-NLS-1$


        initComponents();
        setUserPreferences();






        unitModel = new UnitSearchControl.MechTableModel();

        if (units.isPresent()) {
            // Create summary from units
            this.unitsSummary = null;
        } else {
            this.unitsSummary = MechSummaryCache.getInstance().getAllMechs();
        }

        setMechs(allMechs);

        this.advancedSearchDialog = new AdvancedSearchDialog(parent, campaign.getCalendar().get(GregorianCalendar.YEAR));


    }

    private void initComponents() {
        GridBagConstraints gridBagConstraints;
        this.setLayout(new BorderLayout());

        panelFilterBtns.setName("panelFilterBtns");
        panelFilterBtns.setLayout(new GridBagLayout());


        createUnitTypeArea();
        createUnitWeightArea();
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
        this.comboUnitType.setMinimumSize(new Dimension(200, 27));
        this.comboUnitType.setPreferredSize(new Dimension(200, 27));
        this.comboUnitType.addActionListener(evt -> comboUnitTypeActionPerformed(evt));

        DefaultComboBoxModel<String> unitTypeModel = new DefaultComboBoxModel<>();
        if (this.unitType.isPresent()) {
            String unitTypeName = this.unitType.get().getTypeDisplayableName();
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
        this.comboUnitWeight.setMinimumSize(new Dimension(200, 27));
        this.comboUnitWeight.setPreferredSize(new Dimension(200, 27));
        this.comboUnitWeight.addActionListener(evt -> comboWeightActionPerformed(evt));

        DefaultComboBoxModel<String> unitWeightModel = new DefaultComboBoxModel<>();
        if (this.unitWeightClass.isPresent()) {
            String weightClassName = EntityWeightClass.getClassName(this.unitWeightClass.get());
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

    private void setUserPreferences() {
    }







    private void initComponents() {






        txtFilter.setText(resourceMap.getString("txtFilter.text")); // NOI18N
        txtFilter.setMinimumSize(new java.awt.Dimension(200, 28));
        txtFilter.setName("txtFilter"); // NOI18N
        txtFilter.setPreferredSize(new java.awt.Dimension(200, 28));
        txtFilter.getDocument().addDocumentListener(
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
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panelFilterBtns.add(txtFilter, gridBagConstraints);

        lblFilter.setText(resourceMap.getString("lblFilter.text")); // NOI18N
        lblFilter.setName("lblFilter"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panelFilterBtns.add(lblFilter, gridBagConstraints);

        lblImage.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblImage.setText(resourceMap.getString("lblImage.text")); // NOI18N
        lblImage.setName("lblImage"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        panelFilterBtns.add(lblImage, gridBagConstraints);

        panelSearchBtns.setLayout(new GridBagLayout());

        btnAdvSearch.setText(megamek.client.ui.Messages.getString("MechSelectorDialog.AdvSearch")); //$NON-NLS-1$
        btnAdvSearch.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchFilter = asd.showDialog();
                btnResetSearch.setEnabled(searchFilter!=null);
                filterUnits();
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        panelSearchBtns.add(btnAdvSearch, gridBagConstraints);

        btnResetSearch.setText(Messages.getString("MechSelectorDialog.Reset")); //$NON-NLS-1$
        btnResetSearch.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                asd.clearValues();
                searchFilter=null;
                btnResetSearch.setEnabled(false);
                filterUnits();
            }
        });
        btnResetSearch.setEnabled(false);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        panelSearchBtns.add(btnResetSearch, gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 0);
        panelFilterBtns.add(panelSearchBtns, gridBagConstraints);


        scrTableUnits.setMinimumSize(new java.awt.Dimension(500, 400));
        scrTableUnits.setName("scrTableUnits"); // NOI18N
        scrTableUnits.setPreferredSize(new java.awt.Dimension(500, 400));

        tableUnits.setFont(Font.decode(resourceMap.getString("tableUnits.font"))); // NOI18N
        tableUnits.setModel(unitModel);
        tableUnits.setName("tableUnits"); // NOI18N
        tableUnits.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        sorter = new TableRowSorter<>(unitModel);
        sorter.setComparator(UnitSelectorDialog.MechTableModel.COL_COST, new UnitSelectorDialog.FormattedNumberSorter());
        tableUnits.setRowSorter(sorter);
        tableUnits.getSelectionModel().addListSelectionListener(evt -> unitChanged(evt));
        TableColumn column = null;
        for (int i = 0; i < UnitSelectorDialog.MechTableModel.N_COL; i++) {
            column = tableUnits.getColumnModel().getColumn(i);
            if (i == UnitSelectorDialog.MechTableModel.COL_CHASSIS) {
                column.setPreferredWidth(125);
            }
            else if(i == UnitSelectorDialog.MechTableModel.COL_MODEL
                    || i == UnitSelectorDialog.MechTableModel.COL_COST) {
                column.setPreferredWidth(75);
            }
            else if(i == UnitSelectorDialog.MechTableModel.COL_WEIGHT
                    || i == UnitSelectorDialog.MechTableModel.COL_BV) {
                column.setPreferredWidth(50);
            }
            else {
                column.setPreferredWidth(25);
            }
            column.setCellRenderer(unitModel.getRenderer());

        }
        scrTableUnits.setViewportView(tableUnits);

        panelLeft.setLayout(new BorderLayout());
        panelLeft.add(panelFilterBtns, BorderLayout.PAGE_START);
        panelLeft.add(scrTableUnits, BorderLayout.CENTER);

        splitMain = new JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT,panelLeft, panelMekView);
        splitMain.setOneTouchExpandable(true);
        splitMain.setResizeWeight(0.0);
        getContentPane().add(splitMain, BorderLayout.CENTER);

                pack();
    }

    private void comboWeightActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboWeightActionPerformed
        filterUnits();
    }

    public Entity getEntity() {
        if(null == selectedUnit) {
            return null;
        }
        return selectedUnit.getEntity();
    }


    private void filterUnits() {
        RowFilter<UnitSelectorDialog.MechTableModel, Integer> unitTypeFilter = null;
        final int nClass = comboUnitWeight.getSelectedIndex();
        final int nUnit = comboUnitType.getSelectedIndex();
        final int year = campaign.getCalendar().get(GregorianCalendar.YEAR);
        //If current expression doesn't parse, don't update.
        try {
            unitTypeFilter = new RowFilter<UnitSelectorDialog.MechTableModel,Integer>() {
                @Override
                public boolean include(Entry<? extends UnitSelectorDialog.MechTableModel, ? extends Integer> entry) {
                    UnitSelectorDialog.MechTableModel mechModel = entry.getModel();
                    MechSummary mech = mechModel.getMechSummary(entry.getIdentifier());
                    ITechnology tech = UnitTechProgression.getProgression(mech, campaign.getTechFaction(), true);
                    if (
                        /*year limits*/
                            (!campaign.getCampaignOptions().limitByYear() || mech.getYear() <= year) &&
                                    /*Clan/IS limits*/
                                    (campaign.getCampaignOptions().allowClanPurchases() || !TechConstants.isClan(mech.getType())) &&
                                    (campaign.getCampaignOptions().allowISPurchases() || TechConstants.isClan(mech.getType())) &&
                                    /* Canon */
                                    (mech.isCanon() || !campaign.getCampaignOptions().allowCanonOnly()) &&
                                    /* Weight */
                                    (mech.getWeightClass() == nClass || nClass == EntityWeightClass.SIZE) &&
                                    /* Technology Level */
                                    (null != tech) && campaign.isLegal(tech) &&
                                    /*Unit type*/
                                    (nUnit == UnitType.SIZE || mech.getUnitType().equals(UnitType.getTypeName(nUnit))) &&
                                    (searchFilter==null || MechSearchFilter.isMatch(mech, searchFilter))) {
                        if(txtFilter.getText().length() > 0) {
                            String text = txtFilter.getText();
                            return mech.getName().toLowerCase().contains(text.toLowerCase());
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

    private void unitChanged(javax.swing.event.ListSelectionEvent evt) {
        final String METHOD_NAME = "unitChanged(ListSelectionEvent)"; //$NON-NLS-1$

        int view = tableUnits.getSelectedRow();
        if(view < 0) {
            //selection got filtered away
            selectedUnit = null;
            refreshUnitView();
            return;
        }
        int selected = tableUnits.convertRowIndexToModel(view);
        // else
        MechSummary ms = mechs[selected];
        try {
            // For some unknown reason the base path gets screwed up after you
            // print so this sets the source file to the full path.
            Entity entity = new MechFileParser(ms.getSourceFile(), ms.getEntryName()).getEntity();
            selectedUnit = new UnitOrder(entity, campaign);
            btnBuy.setEnabled(true);
            btnBuy.setText("Buy (TN: " + campaign.getTargetForAcquisition(selectedUnit, campaign.getLogisticsPerson(), false).getValueAsString() + "+)");
            btnBuy.setToolTipText(campaign.getTargetForAcquisition(selectedUnit, campaign.getLogisticsPerson(), false).getDesc());
            refreshUnitView();
        } catch (EntityLoadingException ex) {
            selectedUnit = null;
            btnBuy.setEnabled(false);
            btnBuy.setText("Buy (TN: --)");
            btnBuy.setToolTipText(null);
            MekHQ.getLogger().log(getClass(), METHOD_NAME, LogLevel.ERROR,
                    "Unable to load mech: " + ms.getSourceFile() + ": " //$NON-NLS-1$
                            + ms.getEntryName() + ": " + ex.getMessage()); //$NON-NLS-1$
            MekHQ.getLogger().error(getClass(), METHOD_NAME, ex);
            refreshUnitView();
        }
    }

    void refreshUnitView() {
        final String METHOD_NAME = "refreshUnitView()"; //$NON-NLS-1$

        boolean populateTextFields = true;

        // null entity, so load a default unit.
        if (selectedUnit == null) {
            panelMekView.reset();
            lblImage.setIcon(null);
            return;
        }
        MechView mechView = null;
        try {
            mechView = new MechView(selectedUnit.getEntity(), false, true);
        } catch (Exception e) {
            e.printStackTrace();
            // error unit didn't load right. this is bad news.
            populateTextFields = false;
        }
        if (populateTextFields && (mechView != null)) {
            panelMekView.setMech(selectedUnit.getEntity(), true);
        } else {
            panelMekView.reset();
        }

        if (mt == null) {
            mt = new MechTileset(Configuration.unitImagesDir());
            try {
                mt.loadFromFile("mechset.txt");
            } catch (IOException ex) {
                MekHQ.getLogger().error(getClass(), METHOD_NAME, ex);
                //TODO: do something here
                return;
            }
        }// end if(null tileset)
        Image unitImage = mt.imageFor(selectedUnit.getEntity(), lblImage, -1);
        if(null != unitImage) {
            lblImage.setIcon(new ImageIcon(unitImage));
        }
    }
    /*
     public Entity getSelectedEntity() {
        return selectedUnit;

    }
     */
    public void setMechs (MechSummary [] m) {
        this.mechs = m;

        // break out if there are no units to filter
        if (this.mechs == null) {
            System.err.println("No units to filter!");
        } else {
            unitModel.setData(mechs);
        }
        filterUnits();
    }

    public void changeBuyBtnToSelectBtn () {
        for (ActionListener actionListener : btnBuy.getActionListeners()) {
            btnBuy.removeActionListener(actionListener);
        }

        ResourceBundle resourceMap = ResourceBundle.getBundle("UnitSelectorDialog", new EncodeControl()); //$NON-NLS-1$
        btnBuy.setText(resourceMap.getString("btnBuy.textSelect")); // NOI18N

        btnBuy.addActionListener(evt -> btnBuySelectActionPerformed(evt));
    }

    public JComboBox<String> getComboUnitType() {
        return comboUnitType;
    }

    public JComboBox<String> getComboUnitWeight() {
        return comboUnitWeight;
    }


    /**
     * A table model for displaying work items
     */
    public class MechTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 8472587304279640434L;
        private final static int COL_MODEL = 0;
        private final static int COL_CHASSIS = 1;
        private final static int COL_WEIGHT = 2;
        private final static int COL_BV = 3;
        private final static int COL_YEAR = 4;
        private final static int COL_COST = 5;
        private final static int N_COL = 6;

        private MechSummary[] data = new MechSummary[0];

        public MechTableModel() {
            //this.columnNames = new String[] {"Model", "Chassis"};
            //this.data = new MechSummary[0];
        }

        public int getRowCount() {
            return data.length;
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
        public Class<? extends Object> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }

        public MechSummary getMechSummary(int i) {
            return data[i];
        }

        //fill table with values
        public void setData(MechSummary[] ms) {
            data = ms;
            fireTableDataChanged();
        }

        public Object getValueAt(int row, int col) {
            MechSummary ms = data[row];
            if(col == COL_MODEL) {
                return ms.getModel();
            }
            if(col == COL_CHASSIS) {
                return ms.getChassis();
            }
            if(col == COL_WEIGHT) {
                return ms.getTons();
            }
            if(col == COL_BV) {
                return ms.getBV();
            }
            if(col == COL_YEAR) {
                return ms.getYear();
            }
            if(col == COL_COST) {
                return getPurchasePrice(ms).toAmountAndSymbolString();
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

        public UnitSelectorDialog.MechTableModel.Renderer getRenderer() {
            return new UnitSelectorDialog.MechTableModel.Renderer();
        }

        public class Renderer extends DefaultTableCellRenderer {

            private static final long serialVersionUID = 9054581142945717303L;

            public Component getTableCellRendererComponent(JTable table,
                                                           Object value, boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected,
                        hasFocus, row, column);
                setOpaque(true);
                int actualCol = table.convertColumnIndexToModel(column);
                setHorizontalAlignment(getAlignment(actualCol));

                return this;
            }
        }
    }

    /**
     * A comparator for numbers that have been formatted with DecimalFormat
     * @author Jay Lawson
     *
     */
    public class FormattedNumberSorter implements Comparator<String> {

        @Override
        public int compare(String s0, String s1) {
            //lets find the weight class integer for each name
            long l0 = 0;
            try {
                l0 = DecimalFormat.getInstance().parse(s0.replace(",", "")).longValue();
            } catch (java.text.ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            long l1 = 0;
            try {
                l1 = DecimalFormat.getInstance().parse(s1.replace(",", "")).longValue();
            } catch (java.text.ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return ((Comparable<Long>)l0).compareTo(l1);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        asd.clearValues();
        searchFilter=null;
        filterUnits();
        super.setVisible(visible);
    }
}

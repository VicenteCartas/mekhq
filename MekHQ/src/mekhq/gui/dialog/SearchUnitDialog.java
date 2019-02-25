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

package mekhq.gui.dialog;

import megamek.common.util.EncodeControl;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.unit.Unit;
import mekhq.gui.control.UnitSearchControl;
import mekhq.gui.preferences.JWindowPreference;
import mekhq.preferences.PreferencesNode;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Optional;
import java.util.ResourceBundle;

public class SearchUnitDialog extends JDialog implements PropertyChangeListener {
    private UnitSearchControl unitSearchControl;
    private JButton btnOk;

    public SearchUnitDialog(Frame parent, boolean modal, Campaign campaign, java.util.List<Unit> possibleUnits, Integer unitType, Integer unitWeightClass) {
        super(parent, modal);

        initComponents(parent, campaign, possibleUnits, unitType, unitWeightClass);
        this.setLocationRelativeTo(parent);
        setUserPreferences();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (this.unitSearchControl.getSelectedEntity().isPresent()) {
            this.btnOk.setEnabled(true);
        } else {
            this.btnOk.setEnabled(false);
        }
    }

    public Optional<Unit> getUnit() {
        return this.unitSearchControl.getSelectedUnit();
    }

    private void initComponents(Frame parent, Campaign campaign, java.util.List<Unit> possibleUnits, Integer unitType, Integer unitWeightClass) {
        ResourceBundle resourceMap = ResourceBundle.getBundle("mekhq.resources.SearchUnitDialog", new EncodeControl());

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        this.unitSearchControl = new UnitSearchControl(
                parent,
                campaign,
                possibleUnits,
                unitType,
                unitWeightClass);
        this.unitSearchControl.addListSelectionListener(this);
        JPanel pnlBtn = new JPanel();

        JButton btnOk = new JButton();
        btnOk.setText(resourceMap.getString("btnOkay.text"));
        btnOk.setName("btnOk");
        btnOk.addActionListener(this::closeDialog);
        pnlBtn.add(btnOk);

        JButton btnCancel = new JButton();
        btnCancel.setText(resourceMap.getString("btnCancel.text"));
        btnCancel.setName("btnCancel");
        btnCancel.addActionListener(this::closeDialog);
        pnlBtn.add(btnCancel);

        getContentPane().add(this.unitSearchControl, BorderLayout.CENTER);
        getContentPane().add(pnlBtn, BorderLayout.PAGE_END);
        pack();
    }

    private void setUserPreferences() {
        PreferencesNode preferences = MekHQ.getPreferences().forClass(SearchUnitDialog.class);

        this.setName("dialog");
        preferences.manage(new JWindowPreference(this));
    }

    private void closeDialog(java.awt.event.ActionEvent evt) {
        this.setVisible(false);
    }
}

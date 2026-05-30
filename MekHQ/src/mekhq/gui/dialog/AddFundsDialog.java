/*
 * Copyright (C) 2013-2025 The MegaMek Team. All Rights Reserved.
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
package mekhq.gui.dialog;

import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.util.ResourceBundle;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import megamek.client.ui.preferences.JWindowPreference;
import megamek.client.ui.preferences.PreferencesNode;
import megamek.logging.MMLogger;
import mekhq.MekHQ;
import mekhq.campaign.finances.Money;
import mekhq.campaign.finances.enums.TransactionType;
import mekhq.gui.baseComponents.AbstractMHQJavaFXDialog;

/**
 * Dialog used to add (or remove) funds from the campaign treasury.
 *
 * <p>This dialog was migrated from AWT/Swing to JavaFX as a reference example of an incremental UI migration. The
 * window itself remains a Swing {@link javax.swing.JDialog} (so existing call sites and modal behaviour are
 * unchanged), but its contents are built entirely with JavaFX controls hosted inside a {@link JFXPanel}. The
 * Swing/JavaFX interop plumbing lives in {@link AbstractMHQJavaFXDialog}.</p>
 *
 * <p>The public API ({@link #getClosedType()}, {@link #getTransactionType()}, {@link #getFundsQuantityField()} and
 * {@link #getFundsDescription()}) is intentionally preserved so that {@code FinancesTab} and any other callers do not
 * need to change.</p>
 *
 * @author natit
 */
public class AddFundsDialog extends AbstractMHQJavaFXDialog {
    private static final MMLogger LOGGER = MMLogger.create(AddFundsDialog.class);

    private final transient ResourceBundle resourceMap = ResourceBundle.getBundle("mekhq.resources.AddFundsDialog",
          MekHQ.getMHQOptions().getLocale());

    // JavaFX controls (only touched on the JavaFX Application Thread)
    private TextField fundsQuantityField;
    private ComboBox<TransactionType> categoryCombo;
    private TextField descriptionField;

    // Results cached on confirm so the Swing getters can read them safely from the EDT after the dialog closes.
    private volatile int closedType = JOptionPane.CLOSED_OPTION;
    private volatile Money fundsQuantity = Money.zero();
    private volatile String fundsDescription = "";
    private volatile TransactionType transactionType = TransactionType.MISCELLANEOUS;

    public AddFundsDialog(final JFrame frame, final boolean modal) {
        super(frame, modal, ResourceBundle.getBundle("mekhq.resources.AddFundsDialog",
              MekHQ.getMHQOptions().getLocale()).getString("Form.title"));
        setName("Form");
        initialize();
        setUserPreferences();
    }

    /**
     * Builds the JavaFX content for this dialog.
     *
     * @return the JavaFX {@link Scene} hosting the input controls
     */
    @Override
    protected Scene buildScene() {
        fundsQuantityField = new TextField(resourceMap.getString("fundsQuantityField.text"));
        fundsQuantityField.setTooltip(new Tooltip(resourceMap.getString("fundsQuantityField.toolTipText")));
        fundsQuantityField.setPrefColumnCount(10);

        categoryCombo = new ComboBox<>(FXCollections.observableArrayList(TransactionType.values()));
        categoryCombo.getSelectionModel().select(TransactionType.MISCELLANEOUS);
        categoryCombo.setTooltip(new Tooltip("The category the transaction falls into."));

        descriptionField = new TextField("Rich Uncle");
        descriptionField.setTooltip(new Tooltip("Description of the transaction."));
        descriptionField.setPrefColumnCount(20);
        // Mirror the old Swing behaviour: select everything when the field gains focus.
        descriptionField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                Platform.runLater(descriptionField::selectAll);
            }
        });
        // Pressing Enter in the description field confirms, matching the previous dialog.
        descriptionField.setOnAction(event -> confirm());

        Button btnAddFunds = new Button(resourceMap.getString("btnAddFunds.text"));
        btnAddFunds.setMaxWidth(Double.MAX_VALUE);
        btnAddFunds.setOnAction(event -> confirm());

        HBox fields = new HBox(2, fundsQuantityField, categoryCombo, descriptionField);
        fields.setPadding(new Insets(2));

        BorderPane root = new BorderPane();
        root.setTop(fields);
        root.setBottom(btnAddFunds);

        return new Scene(root);
    }

    /**
     * Caches the entered values and closes the dialog. Invoked on the JavaFX Application Thread.
     */
    private void confirm() {
        fundsQuantity = parseMoney(fundsQuantityField.getText());
        fundsDescription = descriptionField.getText();
        transactionType = categoryCombo.getSelectionModel().getSelectedItem();
        closedType = JOptionPane.OK_OPTION;
        closeDialog();
    }

    /**
     * Parses a user supplied amount into {@link Money}, mirroring the lenient behaviour of the old
     * {@code JMoneyTextField} (defaults to zero on any parse failure).
     *
     * @param text the raw text entered by the user
     *
     * @return the parsed {@link Money} amount, or {@link Money#zero()} if it cannot be parsed
     */
    private static Money parseMoney(String text) {
        try {
            return Money.of(NumberFormat.getInstance().parse(text).doubleValue());
        } catch (Exception ignored) {
            return Money.zero();
        }
    }

    /**
     * These need to be migrated to the Suite Constants / Suite Options Setup
     */
    private void setUserPreferences() {
        try {
            PreferencesNode preferences = MekHQ.getMHQPreferences().forClass(AddFundsDialog.class);
            this.setName("dialog");
            preferences.manage(new JWindowPreference(this));
        } catch (Exception ex) {
            LOGGER.error("Failed to set user preferences", ex);
        }
    }

    public Money getFundsQuantityField() {
        return fundsQuantity;
    }

    public String getFundsDescription() {
        return fundsDescription;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public int getClosedType() {
        return closedType;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            AddFundsDialog dialog = new AddFundsDialog(new JFrame(), true);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
            dialog.setVisible(true);
        });
    }
}

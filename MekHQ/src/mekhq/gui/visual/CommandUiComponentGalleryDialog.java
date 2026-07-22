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
package mekhq.gui.visual;

import static mekhq.utilities.MHQInternationalization.getTextAt;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import megamek.common.ui.FastJScrollPane;

/** Branch-only gallery for reviewing the shared command UI visual primitives before live shell integration. */
public final class CommandUiComponentGalleryDialog extends JDialog {
    private static final String RESOURCE_BUNDLE = "mekhq.resources.CommandUiComponentGallery";

    public CommandUiComponentGalleryDialog(Frame owner) {
        super(owner, getText("dialog.title"), false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        add(createGallery(), BorderLayout.CENTER);
        pack();

        Rectangle usableBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(getWidth(), Math.min(usableBounds.width, 1100)),
              Math.min(getHeight(), Math.min(usableBounds.height, 820)));
        setLocationRelativeTo(owner);
    }

    private ConsoleBackdropPanel createGallery() {
        ConsoleBackdropPanel gallery = new ConsoleBackdropPanel();
        gallery.setBorder(BorderFactory.createEmptyBorder(MekHQVisualTheme.sectionGap(),
              MekHQVisualTheme.sectionGap(),
              MekHQVisualTheme.sectionGap(),
              MekHQVisualTheme.sectionGap()));

        ConsoleHeaderPanel header = new ConsoleHeaderPanel(getText("header.title"));
        header.setStatus(getText("header.status"), ConsoleState.INFORMATION);
        header.setTrailingComponent(new JLabel(getText("header.note")));
        gallery.add(header, BorderLayout.NORTH);

        JPanel sectionStack = new JPanel();
        sectionStack.setLayout(new BoxLayout(sectionStack, BoxLayout.Y_AXIS));
        sectionStack.setOpaque(false);
        sectionStack.add(createTelemetrySection());
        sectionStack.add(Box.createVerticalStrut(MekHQVisualTheme.sectionGap()));
        sectionStack.add(createControlsSection());
        sectionStack.add(Box.createVerticalStrut(MekHQVisualTheme.sectionGap()));
        sectionStack.add(createOperationsSection());
        sectionStack.add(Box.createVerticalStrut(MekHQVisualTheme.sectionGap()));
        sectionStack.add(createDataSection());

        JScrollPane scrollPane = new FastJScrollPane(sectionStack);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(MekHQVisualTheme.sectionGap(), 0, 0, 0));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        gallery.add(scrollPane, BorderLayout.CENTER);
        return gallery;
    }

    private ConsoleSectionPanel createTelemetrySection() {
        ConsoleSectionPanel section = new ConsoleSectionPanel(getText("telemetry.title"),
              ConsoleSectionPanel.Style.ANGULAR);
        JPanel telemetryGrid = new JPanel(new GridLayout(2, 3,
              MekHQVisualTheme.controlGap(), MekHQVisualTheme.controlGap()));
        telemetryGrid.setOpaque(false);
        telemetryGrid.add(createTelemetry("telemetry.funds", "telemetry.funds.value", "telemetry.funds.detail",
              ConsoleState.NEUTRAL));
        telemetryGrid.add(createTelemetry("telemetry.readiness", "telemetry.readiness.value",
              "telemetry.readiness.detail", ConsoleState.NOMINAL));
        telemetryGrid.add(createTelemetry("telemetry.dispatches", "telemetry.dispatches.value",
              "telemetry.dispatches.detail", ConsoleState.INFORMATION));
        telemetryGrid.add(createTelemetry("telemetry.deadline", "telemetry.deadline.value",
              "telemetry.deadline.detail", ConsoleState.CAUTION));
        telemetryGrid.add(createTelemetry("telemetry.blocker", "telemetry.blocker.value",
              "telemetry.blocker.detail", ConsoleState.CRITICAL));
        telemetryGrid.add(createTelemetry("telemetry.scope", "telemetry.scope.value",
              "telemetry.scope.detail", ConsoleState.MUTED));
        section.setContent(telemetryGrid);
        return section;
    }

    private TelemetryPanel createTelemetry(String labelKey, String valueKey, String detailKey, ConsoleState state) {
        TelemetryPanel telemetry = new TelemetryPanel(getText(labelKey), getText(valueKey), state);
        telemetry.setDetailText(getText(detailKey));
        return telemetry;
    }

    private ConsoleSectionPanel createControlsSection() {
        ConsoleSectionPanel section = new ConsoleSectionPanel(getText("controls.title"),
              ConsoleSectionPanel.Style.SUBTLE);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING,
              MekHQVisualTheme.controlGap(), MekHQVisualTheme.controlGap()));
        controls.setOpaque(false);

        JButton primary = new JButton(getText("controls.primary"));
        ConsoleComponentStyler.styleButton(primary, ConsoleComponentStyler.ButtonRole.PRIMARY);
        controls.add(primary);

        JButton standard = new JButton(getText("controls.standard"));
        ConsoleComponentStyler.styleButton(standard, ConsoleComponentStyler.ButtonRole.STANDARD);
        controls.add(standard);

        JButton danger = new JButton(getText("controls.danger"));
        ConsoleComponentStyler.styleButton(danger, ConsoleComponentStyler.ButtonRole.DANGER);
        controls.add(danger);

        JToggleButton toggle = new JToggleButton(getText("controls.toggle"), true);
        ConsoleComponentStyler.styleToggle(toggle, ConsoleComponentStyler.ButtonRole.STANDARD);
        controls.add(toggle);

        JComboBox<String> scope = new JComboBox<>(new String[] {
              getText("controls.scope.all"),
              getText("controls.scope.main"),
              getText("controls.scope.base") });
        ConsoleComponentStyler.styleInput(scope);
        controls.add(scope);

        JSpinner quantity = new JSpinner(new SpinnerNumberModel(3, 0, 20, 1));
        ConsoleComponentStyler.styleInput(quantity);
        controls.add(quantity);

        JTextField search = new JTextField(getText("controls.search"), 15);
        ConsoleComponentStyler.styleInput(search);
        controls.add(search);

        section.setContent(controls);
        return section;
    }

    private ConsoleSectionPanel createOperationsSection() {
        ConsoleSectionPanel section = new ConsoleSectionPanel(getText("operations.title"),
              ConsoleSectionPanel.Style.DIVIDER_ONLY);

        JTextArea dispatch = new JTextArea(getText("operations.dispatch"));
        dispatch.setEditable(false);
        dispatch.setLineWrap(true);
        dispatch.setWrapStyleWord(true);
        dispatch.setBorder(BorderFactory.createEmptyBorder(MekHQVisualTheme.controlGap(),
              MekHQVisualTheme.controlGap(),
              MekHQVisualTheme.controlGap(),
              MekHQVisualTheme.controlGap()));

        JList<String> priorities = new JList<>(new String[] {
              getText("operations.priority.one"),
              getText("operations.priority.two"),
              getText("operations.priority.three") });
        priorities.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        priorities.setSelectedIndex(0);

        JScrollPane dispatchScroll = new FastJScrollPane(dispatch);
        JScrollPane prioritiesScroll = new FastJScrollPane(priorities);
        ConsoleComponentStyler.styleScrollPane(dispatchScroll);
        ConsoleComponentStyler.styleScrollPane(prioritiesScroll);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dispatchScroll, prioritiesScroll);
        splitPane.setResizeWeight(0.65);
        splitPane.setPreferredSize(new Dimension(800, 150));
        ConsoleComponentStyler.styleSplitPane(splitPane);
        section.setContent(splitPane);
        return section;
    }

    private ConsoleSectionPanel createDataSection() {
        ConsoleSectionPanel section = new ConsoleSectionPanel(getText("data.title"),
              ConsoleSectionPanel.Style.ANGULAR);

        JTable table = new JTable(new DefaultTableModel(new Object[][] {
              { getText("data.row.engine"), "Part", "2", "3 days", "High" },
              { getText("data.row.armor"), "Supply", "120", "1 day", "Normal" },
              { getText("data.row.actuator"), "Part", "1", "Paused", "Low" }
        }, new Object[] {
              getText("data.column.name"),
              getText("data.column.type"),
              getText("data.column.quantity"),
              getText("data.column.next"),
              getText("data.column.priority") }));
        ConsoleComponentStyler.styleTable(table);
        JScrollPane tableScroll = new FastJScrollPane(table);
        ConsoleComponentStyler.styleScrollPane(tableScroll);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(getText("data.tab.queue"), tableScroll);
        tabs.addTab(getText("data.tab.empty"), createEmptyState());
        ConsoleComponentStyler.styleTabbedPane(tabs);
        section.setContent(tabs);
        return section;
    }

    private JPanel createEmptyState() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel label = new JLabel(getText("data.empty"), JLabel.CENTER);
        label.setForeground(MekHQVisualTheme.color(MekHQVisualTheme.ColorRole.MUTED));
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private static String getText(String key) {
        return getTextAt(RESOURCE_BUNDLE, key);
    }
}

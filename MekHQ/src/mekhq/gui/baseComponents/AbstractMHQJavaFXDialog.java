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
package mekhq.gui.baseComponents;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.concurrent.CountDownLatch;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import megamek.logging.MMLogger;

/**
 * Base class for MekHQ dialogs whose content is built with JavaFX but which are hosted inside a Swing {@link JDialog}.
 *
 * <p>This is the shared plumbing for the incremental AWT/Swing &rarr; JavaFX migration. The surrounding application
 * remains Swing; each migrated dialog embeds a JavaFX {@link Scene} inside a {@link JFXPanel} (the officially supported
 * Swing/JavaFX interop bridge). Centralising the bridge here keeps the per-dialog code focused on the actual UI and,
 * critically, prevents every subclass from having to re-discover the interop pitfalls (implicit-exit shutdown, cross
 * thread access, blocking the EDT while the scene is built).</p>
 *
 * <h2>Threading contract</h2>
 * <ul>
 *     <li>{@link #buildScene()} is always invoked on the <em>JavaFX Application Thread</em>. Build and touch JavaFX
 *     nodes only there.</li>
 *     <li>To close the dialog from a JavaFX event handler, call {@link #closeDialog()} (or {@code setVisible(false)}
 *     via {@link javax.swing.SwingUtilities#invokeLater}); never hide the window directly from the FX thread.</li>
 *     <li>Read user input back via {@code volatile} fields that the FX event handlers populate, so the Swing/EDT
 *     getters observe a consistent value after the dialog closes.</li>
 * </ul>
 *
 * <p><strong>Why {@link Platform#setImplicitExit(boolean)} is forced to {@code false}:</strong> JavaFX defaults to
 * shutting the toolkit down when the last JavaFX window/embedded panel goes away. In a Swing-hosted application that
 * would tear down the FX runtime as soon as the first such dialog closes, and the next open would deadlock the EDT
 * (the {@code Platform.runLater} that builds the scene would never run). This class disables implicit exit once, for
 * the lifetime of the JVM.</p>
 */
public abstract class AbstractMHQJavaFXDialog extends JDialog {
    private static final MMLogger LOGGER = MMLogger.create(AbstractMHQJavaFXDialog.class);

    /** Fallback preferred size used for {@code pack()} before the JavaFX scene has reported its own size. */
    private static final Dimension DEFAULT_PREFERRED_SIZE = new Dimension(420, 70);

    private final JFXPanel jfxPanel = new JFXPanel();

    /**
     * @param frame the owning Swing frame
     * @param modal whether the dialog should be modal
     * @param title the window title
     */
    protected AbstractMHQJavaFXDialog(final JFrame frame, final boolean modal, final String title) {
        super(frame, modal);
        initComponents(title);
    }

    /**
     * Builds the JavaFX content for this dialog. Implementations must construct and return the {@link Scene} that will
     * be displayed; this method is invoked on the JavaFX Application Thread.
     *
     * @return the JavaFX {@link Scene} to host inside this dialog
     */
    protected abstract Scene buildScene();

    /**
     * @return the preferred size hint used for {@code pack()} before the JavaFX scene reports its own dimensions.
     *         Subclasses may override to better fit their content.
     */
    protected Dimension getPreferredDialogSize() {
        return DEFAULT_PREFERRED_SIZE;
    }

    private void initComponents(final String title) {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(title);
        getContentPane().add(jfxPanel, BorderLayout.CENTER);

        // Build the JavaFX scene graph on the JavaFX Application Thread, then size the window once it is ready.
        final CountDownLatch sceneReady = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // See class Javadoc: embedded JavaFX-in-Swing must opt out of implicit exit, otherwise the FX runtime
                // shuts down when the first such dialog closes and the next open deadlocks the EDT.
                Platform.setImplicitExit(false);
                jfxPanel.setScene(buildScene());
            } finally {
                sceneReady.countDown();
            }
        });

        try {
            sceneReady.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while building JavaFX scene", ex);
        }

        // The scene's preferred size is not known on the EDT yet, so give the panel a sensible size for pack().
        jfxPanel.setPreferredSize(getPreferredDialogSize());
        setLocationRelativeTo(getParent());
        pack();
    }

    /**
     * Closes (hides) the dialog. Safe to call from the JavaFX Application Thread; the actual window hide is dispatched
     * back to the Swing Event Dispatch Thread.
     */
    protected void closeDialog() {
        javax.swing.SwingUtilities.invokeLater(() -> setVisible(false));
    }
}

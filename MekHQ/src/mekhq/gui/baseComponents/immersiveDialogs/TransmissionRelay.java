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
package mekhq.gui.baseComponents.immersiveDialogs;

import static mekhq.utilities.MHQInternationalization.getText;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;

import jakarta.annotation.Nullable;
import megamek.logging.MMLogger;
import mekhq.gui.model.view.Transmission;

/**
 * Shows an immersive dialog as a {@link Transmission} on the screen hosting the campaign, in place of its window, and
 * carries the answer back to the dialog as if the player had used the window. The dialog's controls stay the real
 * ones: what the player sets on the screen is set on them, their listeners run, and the screen shows the result.
 *
 * <p>Swing thread only, except for the {@link Transmission.Channel} methods, which any thread may call.</p>
 */
final class TransmissionRelay implements Transmission.Channel {
    private static final MMLogger LOGGER = MMLogger.create(TransmissionRelay.class);

    /** What the dialog shows, as its constructor was given it. */
    record Content(String message, List<ImmersiveDialogCore.ButtonLabelTooltipPair> buttons, @Nullable String note,
          int width, boolean vertical, @Nullable JPanel supplemental, @Nullable ImageIcon image,
          TransmissionSignalQuality signal) { }

    private final ImmersiveDialogCore dialog;
    private final Content content;
    private final List<Component> controls;

    private @Nullable Transmission.Handle handle;
    private @Nullable SecondaryLoop loop;
    private boolean pending;
    private boolean fellBack;

    private TransmissionRelay(ImmersiveDialogCore dialog, Content content, List<Component> controls) {
        this.dialog = dialog;
        this.content = content;
        this.controls = controls;
    }

    /** A relay for {@code dialog}, or null when it can't be shown as a transmission and keeps its window. */
    static @Nullable TransmissionRelay of(ImmersiveDialogCore dialog, Content content) {
        List<Component> controls = new ArrayList<>();
        if (content.supplemental() != null && !collect(content.supplemental(), controls)) {
            return null;
        }
        return new TransmissionRelay(dialog, content, controls);
    }

    /**
     * Shows the dialog on the screen hosting the campaign, and for a modal dialog waits for the answer.
     *
     * @return false if no screen takes it now, so the dialog should show its window
     */
    boolean show() {
        if (handle != null || pending) {
            return true;
        }
        if (Transmission.presenter() == null) {
            return false;
        }
        fellBack = false;
        if (!dialog.isModal()) {
            // A dialog may still hide itself and come back modal before the caller lets go of the Swing thread
            pending = true;
            SwingUtilities.invokeLater(() -> {
                if (pending) {
                    pending = false;
                    if (!present()) {
                        dialog.showWindow();
                    }
                }
            });
            return true;
        }
        if (!present()) {
            return false;
        }
        SecondaryLoop wait = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        loop = wait;
        wait.enter();
        if (fellBack) {
            fellBack = false;
            dialog.showWindow();
        }
        return true;
    }

    /** Takes the transmission down without an answer, as the dialog was hidden or closed. */
    void withdraw() {
        pending = false;
        Transmission.Handle shown = handle;
        handle = null;
        if (shown != null) {
            shown.withdraw();
        }
        release();
    }

    private boolean present() {
        Transmission.Presenter presenter = Transmission.presenter();
        if (presenter == null) {
            return false;
        }
        Transmission transmission;
        try {
            transmission = transmission();
        } catch (RuntimeException exception) {
            LOGGER.warn("Can't show {} as a transmission: {}", dialog.getClass().getSimpleName(), exception);
            return false;
        }
        if (transmission == null) {
            return false;
        }
        handle = presenter.present(transmission, this);
        return handle != null;
    }

    private void release() {
        SecondaryLoop wait = loop;
        loop = null;
        if (wait != null) {
            wait.exit();
        }
    }

    @Override
    public void control(int index, int value) {
        SwingUtilities.invokeLater(() -> {
            if (handle == null || index < 0 || index >= controls.size()) {
                return;
            }
            Component control = controls.get(index);
            if (control instanceof JSpinner spinner) {
                spinner.setValue(value);
            } else if (control instanceof JComboBox<?> combo) {
                if (value >= -1 && value < combo.getItemCount()) {
                    combo.setSelectedIndex(value);
                }
            } else if (control instanceof AbstractButton check && check.isSelected() != (value != 0)) {
                check.doClick(0);
            }
            Transmission.Handle shown = handle;
            if (shown != null) {
                shown.update(read(controls));
            }
        });
    }

    @Override
    public void link(String href) {
        SwingUtilities.invokeLater(() -> {
            if (handle != null) {
                dialog.hyperlinkEventListenerActions(new HyperlinkEvent(dialog,
                      HyperlinkEvent.EventType.ACTIVATED, null, href));
            }
        });
    }

    @Override
    public void answer(int choice, boolean responded) {
        SwingUtilities.invokeLater(() -> {
            if (handle == null) {
                return;
            }
            handle = null;
            if (responded) {
                dialog.respond(choice);
            }
            // Closing without a response leaves the default choice, as closing the window does
            dialog.dispose();
            release();
        });
    }

    @Override
    public void fallBack() {
        SwingUtilities.invokeLater(() -> {
            if (handle == null) {
                return;
            }
            handle = null;
            if (loop != null) {
                fellBack = true;
                release();
            } else {
                dialog.showWindow();
            }
        });
    }

    /** The snapshot of the dialog, or null when a part of it can't be shown as a transmission. */
    private @Nullable Transmission transmission() {
        TransmissionSignalQuality quality = content.signal();
        Transmission.Signal signal = new Transmission.Signal(quality.ordinal(), quality.tintOpacity,
              quality.scanlineGap, quality.scanlineOpacity, quality.noiseMarks, quality.noiseOpacity,
              quality.glitchInterval, quality.glitchFrameMinimum, quality.glitchFrameMaximum, quality.glitchOffset,
              quality.glitchHeightMinimum, quality.glitchHeightMaximum, quality.glitchLineOpacity);
        Transmission.Texts texts = new Transmission.Texts(getText("ImmersiveDialog.header.title"),
              getText(quality.statusResourceKey), getText("ImmersiveDialog.message.title"),
              getText("ImmersiveDialog.response.title"), getText("ImmersiveDialog.information.title"));

        Transmission.Speaker left = null;
        Transmission.Speaker right = null;
        if (dialog.leftSpeaker() != null) {
            left = dialog.transmissionSpeaker(dialog.leftSpeaker(), dialog.campaign(), true);
            if (left == null) {
                return null;
            }
        }
        if (dialog.rightSpeaker() != null) {
            right = dialog.transmissionSpeaker(dialog.rightSpeaker(), dialog.campaign(), false);
            if (right == null) {
                return null;
            }
        }

        List<Transmission.Response> responses = new ArrayList<>();
        for (ImmersiveDialogCore.ButtonLabelTooltipPair button : content.buttons()) {
            responses.add(new Transmission.Response(button.btnLabel(), button.btnTooltip()));
        }
        return new Transmission(dialog.getTitle() == null ? "" : dialog.getTitle(), texts, signal, left, right,
              content.message(), picture(content.image()), responses, content.vertical(), content.width(),
              content.note(), read(controls), dialog.isModal());
    }

    /** {@code icon}'s pixels, or null when there is no icon or it has no size. */
    static @Nullable Transmission.Picture picture(@Nullable ImageIcon icon) {
        if (icon == null) {
            return null;
        }
        Image image = icon.getImage();
        int width = icon.getIconWidth();
        int height = icon.getIconHeight();
        if (image == null || width <= 0 || height <= 0) {
            return null;
        }
        BufferedImage pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = pixels.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return new Transmission.Picture(width, height, pixels.getRGB(0, 0, width, height, null, 0, width));
    }

    /**
     * Adds the controls in {@code container} to {@code controls} in reading order.
     *
     * @return false if it holds something a transmission can't show
     */
    static boolean collect(Container container, List<Component> controls) {
        for (Component component : container.getComponents()) {
            if (!component.isVisible() || component instanceof Box.Filler || component instanceof JSeparator) {
                continue;
            }
            if (component instanceof JLabel label) {
                if (label.getIcon() != null && blank(label.getText())) {
                    LOGGER.info("Showing a dialog as a window: it has a picture among its controls");
                    return false;
                }
                if (!blank(label.getText())) {
                    controls.add(label);
                }
            } else if (component instanceof JSpinner spinner) {
                if (!(spinner.getModel() instanceof SpinnerNumberModel model) || !(model.getValue() instanceof Integer)) {
                    LOGGER.info("Showing a dialog as a window: it has a spinner of other than whole numbers");
                    return false;
                }
                controls.add(spinner);
            } else if (component instanceof JComboBox<?> || component instanceof JCheckBox) {
                controls.add(component);
            } else if (component instanceof JPanel || component.getClass() == Box.class) {
                if (!collect((Container) component, controls)) {
                    return false;
                }
            } else {
                LOGGER.info("Showing a dialog as a window: it has a {} among its controls",
                      component.getClass().getSimpleName());
                return false;
            }
        }
        return true;
    }

    /** {@code controls} as they now read. */
    static List<Transmission.Control> read(List<Component> controls) {
        List<Transmission.Control> read = new ArrayList<>(controls.size());
        for (Component control : controls) {
            if (control instanceof JLabel label) {
                read.add(new Transmission.Text(label.getText() == null ? "" : label.getText()));
            } else if (control instanceof JSpinner spinner) {
                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                read.add(new Transmission.Stepper((Integer) model.getValue(), whole(model.getMinimum(),
                      Integer.MIN_VALUE), whole(model.getMaximum(), Integer.MAX_VALUE),
                      whole(model.getStepSize(), 1)));
            } else if (control instanceof JComboBox<?> combo) {
                read.add(new Transmission.Select(items(combo), combo.getSelectedIndex()));
            } else if (control instanceof JCheckBox check) {
                read.add(new Transmission.Check(check.getText() == null ? "" : check.getText(), check.isSelected()));
            }
        }
        return read;
    }

    /** The items of {@code combo} as its renderer writes them. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<String> items(JComboBox<?> combo) {
        ListCellRenderer renderer = combo.getRenderer();
        JList list = new JList<>();
        List<String> items = new ArrayList<>(combo.getItemCount());
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object item = combo.getItemAt(i);
            String text = null;
            if (renderer != null) {
                Component cell = renderer.getListCellRendererComponent(list, item, i, false, false);
                if (cell instanceof JLabel label) {
                    text = label.getText();
                }
            }
            items.add(text != null ? text : String.valueOf(item));
        }
        return items;
    }

    private static int whole(@Nullable Comparable<?> value, int otherwise) {
        return value instanceof Number number ? number.intValue() : otherwise;
    }

    private static int whole(@Nullable Number value, int otherwise) {
        return value != null ? value.intValue() : otherwise;
    }

    private static boolean blank(@Nullable String text) {
        return text == null || text.replaceAll("<[^>]*>", "").isBlank();
    }
}

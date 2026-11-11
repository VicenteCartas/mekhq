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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import mekhq.gui.model.view.Transmission;
import org.junit.jupiter.api.Test;

class TransmissionRelayTest {
    @Test
    void collectsControlsInReadingOrder() {
        JPanel panel = new JPanel();
        panel.add(new JLabel("<html>Rent <b>2</b> bays</html>"));
        panel.add(new JSpinner(new SpinnerNumberModel(2, 0, 10, 2)));
        panel.add(Box.createRigidArea(new Dimension(5, 5)));
        panel.add(new JSeparator());
        JPanel nested = new JPanel();
        JComboBox<String> combo = new JComboBox<>(new String[] { "a", "b" });
        combo.setSelectedIndex(1);
        nested.add(combo);
        JCheckBox check = new JCheckBox("Repeat", true);
        nested.add(check);
        panel.add(nested);
        JLabel hidden = new JLabel("hidden");
        hidden.setVisible(false);
        panel.add(hidden);
        panel.add(new JLabel(" "));

        List<Component> controls = new ArrayList<>();
        assertTrue(TransmissionRelay.collect(panel, controls));
        assertEquals(List.of(new Transmission.Text("<html>Rent <b>2</b> bays</html>"),
              new Transmission.Stepper(2, 0, 10, 2), new Transmission.Select(List.of("a", "b"), 1),
              new Transmission.Check("Repeat", true)), TransmissionRelay.read(controls));
    }

    @Test
    void readsComboItemsAsTheirRendererWritesThem() {
        JComboBox<Integer> combo = new JComboBox<>(new Integer[] { 1, 2 });
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                  boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setText("Option " + value);
                return this;
            }
        });

        assertEquals(List.of(new Transmission.Select(List.of("Option 1", "Option 2"), 0)),
              TransmissionRelay.read(List.of(combo)));
    }

    @Test
    void readsAnUnboundedSpinnerAsTheWholeRange() {
        SpinnerNumberModel model = new SpinnerNumberModel(Integer.valueOf(5), null, null, Integer.valueOf(1));

        assertEquals(List.of(new Transmission.Stepper(5, Integer.MIN_VALUE, Integer.MAX_VALUE, 1)),
              TransmissionRelay.read(List.of(new JSpinner(model))));
    }

    @Test
    void refusesWhatATransmissionCannotShow() {
        JPanel picture = new JPanel();
        picture.add(new JLabel(new ImageIcon(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB))));
        JPanel field = new JPanel();
        field.add(new JTextField("text"));
        JPanel fractions = new JPanel();
        fractions.add(new JSpinner(new SpinnerNumberModel(0.5, 0.0, 1.0, 0.1)));

        assertFalse(TransmissionRelay.collect(picture, new ArrayList<>()));
        assertFalse(TransmissionRelay.collect(field, new ArrayList<>()));
        assertFalse(TransmissionRelay.collect(fractions, new ArrayList<>()));
    }

    @Test
    void keepsAPicturesPixels() {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF102030);
        image.setRGB(1, 0, 0x80405060);

        Transmission.Picture picture = TransmissionRelay.picture(new ImageIcon(image));

        assertNotNull(picture);
        assertEquals(2, picture.width());
        assertEquals(1, picture.height());
        assertArrayEquals(new int[] { 0xFF102030, 0x80405060 }, picture.argb());
        assertNull(TransmissionRelay.picture(null));
    }

    @Test
    void transmissionPartsRefuseWhatTheyCannotShow() {
        assertThrows(IllegalArgumentException.class, () -> new Transmission.Picture(2, 2, new int[3]));
        assertThrows(IllegalArgumentException.class, () -> new Transmission.Response(null, null));
        Transmission.Texts texts = new Transmission.Texts("h", "s", "m", "r", "i");
        Transmission.Signal signal = new Transmission.Signal(0, 0, 5, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0);
        Transmission transmission = new Transmission("", texts, signal, null, null, null, null,
              List.of(new Transmission.Response("OK", null)), false, 400, null, List.of(), true);
        assertEquals("", transmission.message());
    }
}

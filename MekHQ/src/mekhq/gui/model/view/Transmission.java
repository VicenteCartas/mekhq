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
package mekhq.gui.model.view;

import java.util.List;
import java.util.Objects;

import jakarta.annotation.Nullable;

/**
 * An immersive dialog as a screen hosting the campaign shows it in place of its window: who speaks, what they say, the
 * responses, the out-of-character note and the dialog's extra controls, with every text in the dialog's own HTML and
 * every picture as plain pixels. Built on the Swing thread; read on the GPU thread, so it refers to nothing mutable.
 *
 * <p>While a {@link Presenter} is {@linkplain #register registered}, immersive dialogs offer themselves to it instead
 * of showing their windows. The presenter answers through the dialog's {@link Channel}; a modal dialog waits for the
 * answer on the Swing thread, still dispatching events, so neither thread waits for the other.</p>
 *
 * @param title     the dialog's window title
 * @param texts     the section headers and the signal status, from the dialog's bundle
 * @param signal    how rough the speakers' feeds are
 * @param left      the speaker at the left, or null
 * @param right     the speaker at the right, or null
 * @param message   the in-character message
 * @param image     a picture above the message, or null
 * @param responses the responses, in the order of their choice numbers
 * @param vertical  whether responses stack as rows showing their details, rather than sit side by side
 * @param width     the message column's width as the dialog's window sets it, in unscaled pixels
 * @param note      the out-of-character note, or null
 * @param controls  the dialog's extra controls and their labels, in order
 * @param modal     whether the dialog's caller waits for the answer
 */
public record Transmission(String title, Texts texts, Signal signal, @Nullable Speaker left, @Nullable Speaker right,
      String message, @Nullable Picture image, List<Response> responses, boolean vertical, int width,
      @Nullable String note, List<Control> controls, boolean modal) {

    public Transmission {
        Objects.requireNonNull(title);
        Objects.requireNonNull(texts);
        Objects.requireNonNull(signal);
        message = message == null ? "" : message;
        responses = List.copyOf(responses);
        controls = List.copyOf(controls);
    }

    /** The fixed texts: header, signal status and the message, response and information section titles. */
    public record Texts(String header, String status, String message, String response, String information) { }

    /**
     * A feed's roughness, as the dialog's signal quality sets it: {@code level} 0 (clear) to 2 (degraded), then the
     * look's numbers in unscaled pixels and 1/8 s timer steps.
     */
    public record Signal(int level, float tintOpacity, int scanlineGap, float scanlineOpacity, int noiseMarks,
          float noiseOpacity, int glitchInterval, int glitchFramesMin, int glitchFramesMax, int glitchOffset,
          int glitchHeightMin, int glitchHeightMax, float glitchLineOpacity) { }

    /**
     * A speaker: the channel they speak on, their description in HTML and their picture, shown through the comm feed
     * when {@code feed} is set and plain otherwise (a news network's logo).
     */
    public record Speaker(String source, String description, @Nullable Picture picture, boolean feed) { }

    /** A picture as 0xAARRGGBB pixels, row by row from the top. */
    public record Picture(int width, int height, int[] argb) {
        public Picture {
            if (width <= 0 || height <= 0 || argb.length != width * height) {
                throw new IllegalArgumentException("A " + width + "x" + height + " picture needs " + width * height
                      + " pixels, not " + argb.length);
            }
        }
    }

    /** A response: its label and its detail, either of which may be null but not both. */
    public record Response(@Nullable String label, @Nullable String detail) {
        public Response {
            if (label == null && detail == null) {
                throw new IllegalArgumentException("A response needs a label or a detail");
            }
        }
    }

    /** One of the dialog's extra controls, or a label among them. */
    public sealed interface Control permits Text, Stepper, Select, Check { }

    /** A label, in HTML. */
    public record Text(String html) implements Control { }

    /** A whole number from {@code min} to {@code max} in steps of {@code step}. */
    public record Stepper(int value, int min, int max, int step) implements Control { }

    /** One of {@code items}, {@code index} chosen (-1 for none). */
    public record Select(List<String> items, int index) implements Control {
        public Select {
            items = List.copyOf(items);
        }
    }

    /** A checkbox. */
    public record Check(String text, boolean ticked) implements Control { }

    /**
     * How a presenter answers a dialog. Any thread may call it; the dialog acts on the Swing thread, in order.
     */
    public interface Channel {
        /**
         * Sets control {@code index} as the player changed it: a stepper's value, a select's index, or a checkbox's
         * state as 1 or 0. The dialog's own listeners run, and the presenter gets the controls back as they now read.
         */
        void control(int index, int value);

        /** Follows a link the player clicked in the message or the note. */
        void link(String href);

        /**
         * Answers the dialog: {@code responded} when the player chose response {@code choice}, false when they
         * closed the transmission, which leaves the dialog's default choice.
         */
        void answer(int choice, boolean responded);

        /** Shows the dialog as its own window after all, as when the presenter goes away before an answer. */
        void fallBack();
    }

    /** A screen that can show transmissions. */
    public interface Presenter {
        /**
         * Swing thread: shows {@code transmission}, answering through {@code channel}; null if it cannot show it now,
         * and the dialog shows its own window.
         */
        @Nullable Handle present(Transmission transmission, Channel channel);
    }

    /** A transmission being shown. Any thread may call it. */
    public interface Handle {
        /** Shows the controls as they now read, after their dialog changed them. */
        void update(List<Control> controls);

        /** Takes the transmission down without an answer, as when its dialog was closed or hidden. */
        void withdraw();
    }

    private static volatile @Nullable Presenter presenter;

    /** Shows immersive dialogs on {@code screen} from now on, in place of the one before. */
    public static void register(Presenter screen) {
        presenter = Objects.requireNonNull(screen);
    }

    /** Stops showing immersive dialogs on {@code screen}, if it still is the one showing them. */
    public static void unregister(Presenter screen) {
        if (presenter == screen) {
            presenter = null;
        }
    }

    /** The screen showing immersive dialogs, or null when they show their own windows. */
    public static @Nullable Presenter presenter() {
        return presenter;
    }
}

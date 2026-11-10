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
package mekhq.gui.gdx.hub;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.GUIPreferences;
import megamek.client.ui.clientGUI.boardview.gpu.GpuHost;
import megamek.client.ui.design.DesignTokens;
import megamek.client.ui.gdx.UiBackdropBlur;
import megamek.client.ui.gdx.UiChoice;
import megamek.client.ui.gdx.UiKit;
import megamek.client.ui.gdx.UiResponseButton;
import megamek.client.ui.gdx.UiRichText;
import megamek.client.ui.gdx.UiSignalFeed;
import megamek.client.ui.gdx.UiStepper;
import megamek.client.ui.gdx.UiTheme;
import mekhq.gui.model.view.Transmission;

/**
 * Immersive dialogs as the command post shows them: a {@link Transmission} coming in over the hub, which dims and blurs
 * behind it. The channel opens as a line of light that widens into the panel, the speakers' feeds boot up, the message
 * types in (a click or Enter shows it all) and the responses light up one after another; the chosen one sweeps full of
 * light as the answer goes out, and the channel closes. Number keys take a response, Enter the focused one (the first
 * to begin with), Tab and the arrows move the focus, and Esc closes the channel without a response, as closing the
 * dialog's window does. The dialog's extra controls show as the kit's, set the dialog's own as they change, and show
 * what the dialog then says. Transmissions offered while one shows wait their turn. Without motion everything shows at
 * once. GPU thread, unless a method says otherwise.
 */
final class TransmissionView implements Disposable {
    /** A speaker's column and the widest and tallest their picture shows, in stage units. */
    private static final float SPEAKER_WIDTH = 200;
    private static final float SPEAKER_HEIGHT = 280;
    /** The narrowest the message column shows, and the room kept clear around the panel, in stage units. */
    private static final float NARROWEST = 240;
    private static final float MARGIN = 40;
    /** The most of the screen's height the message, the note and a picture over the message take. */
    private static final float MESSAGE_SHARE = .4f;
    private static final float NOTE_SHARE = .18f;
    private static final float IMAGE_SHARE = .25f;
    /** Seconds the backdrop takes to dim, and the channel waits, takes to open and to close. */
    private static final float DIM_SECONDS = .35f;
    private static final float OPEN_DELAY = .15f;
    private static final float OPEN_SECONDS = .28f;
    private static final float CLOSE_SECONDS = .18f;
    /** When the message starts typing, its rate in characters a second and the longest it takes, in seconds. */
    private static final float TYPE_DELAY = .45f;
    private static final float TYPE_RATE = 90;
    private static final float TYPE_LONGEST = 3.5f;
    /** A rate so slow the message waits blank, from nothing, until it starts typing. */
    private static final float WAITING = 1e-6f;
    /** When the first response lights up, how long after it each next one does and how long each takes. */
    private static final float RESPONSE_DELAY = .5f;
    private static final float RESPONSE_STAGGER = .06f;
    private static final float RESPONSE_SECONDS = .18f;
    private static final Color DIM = new Color(.01f, .03f, .04f, .6f);
    private static final Color FEED_TINT = Color.valueOf("56D0C5");
    private static final Color FEED_LINE = Color.valueOf("EBB14C");
    /** A stepper whose step isn't 1 lists its values, when there are no more than this many. */
    private static final int LISTED_VALUES = 200;
    /** The responses with number keys. */
    private static final int KEYED = 9;

    private final Deque<Entry> queue = new ArrayDeque<>();
    private final Table root = new Table();
    private final List<UiResponseButton> buttons = new ArrayList<>();
    private final List<Disposable> owned = new ArrayList<>();
    private UiTheme theme;
    private UiKit ui;
    private Stage stage;
    private UiBackdropBlur blur;
    private TextureRegion white;
    private float scale = 1;
    private boolean motion = true;
    private boolean disposed;
    /** How far the backdrop is dimmed, 0 to 1. */
    private float dim;

    /** The transmission showing, and whether its channel is closing. */
    private @Nullable Entry current;
    private boolean closing;
    private @Nullable Table panel;
    private @Nullable UiRichText message;
    /** The focused response's detail, under responses side by side; null when they stack or have none. */
    private @Nullable UiRichText detail;
    private final Table controlTable = new Table();
    private Actor[] controlActors = new Actor[0];
    private List<Transmission.Control> shownControls = List.of();
    private float columnWidth;
    private int focus;
    /** Whether the view is setting its controls to the dialog's, so their changes don't go back to it. */
    private boolean syncing;

    /** Sets the view up on the hub's look, at {@code scale} pixels a stage unit. */
    void create(UiTheme theme, UiKit ui, float scale) {
        this.theme = theme;
        this.ui = ui;
        this.scale = scale;
        motion = !GUIPreferences.getInstance().getReduceMotion();
        white = new TextureRegion(theme.skin.get("white", Texture.class));
        stage = new Stage(new ScreenViewport());
        root.setFillParent(true);
        stage.addActor(root);
        blur = new UiBackdropBlur();
    }

    /**
     * Any thread: shows {@code transmission} once those before it have gone, answering through {@code channel}.
     *
     * @return the handle the dialog updates and withdraws it by
     */
    Transmission.Handle offer(Transmission transmission, Transmission.Channel channel) {
        Entry entry = new Entry(transmission, channel);
        GpuHost.post(() -> {
            if (disposed) {
                channel.fallBack();
            } else {
                queue.add(entry);
            }
        });
        return entry;
    }

    /** Whether a transmission shows or waits, or the backdrop still clears, so the view takes the input. */
    boolean active() {
        return current != null || !queue.isEmpty() || dim > 0;
    }

    void resize(int width, int height) {
        if (stage == null || width <= 0 || height <= 0) {
            return;
        }
        ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1 / scale);
        stage.getViewport().update(width, height, true);
        if (current != null && !closing) {
            build(false);
        }
    }

    /** Draws the view over the hub, which has drawn its frame. */
    void render(float delta) {
        if (stage == null) {
            return;
        }
        if (current == null && !queue.isEmpty()) {
            current = queue.poll();
            closing = false;
            focus = 0;
            build(motion);
        }
        float target = (current != null && !closing) || !queue.isEmpty() ? 1 : 0;
        dim = motion ? approach(dim, target, delta / DIM_SECONDS) : target;
        if (dim <= 0 && current == null) {
            return;
        }
        blur.apply(Interpolation.smooth.apply(dim), DIM);
        stage.getViewport().apply(true);
        if (panel != null) {
            panel.setOrigin(Align.center);
        }
        stage.act(delta);
        stage.draw();
    }

    /** The view's input, ahead of the hub's: while it is active it takes everything. */
    InputProcessor input() {
        return new InputProcessor() {
            private boolean open() {
                return current != null && !closing;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (!active()) {
                    return false;
                }
                if (open() && !stage.keyDown(keycode)) {
                    key(keycode);
                }
                return true;
            }

            @Override
            public boolean keyUp(int keycode) {
                if (!active()) {
                    return false;
                }
                stage.keyUp(keycode);
                return true;
            }

            @Override
            public boolean keyTyped(char character) {
                if (!active()) {
                    return false;
                }
                if (open()) {
                    stage.keyTyped(character);
                }
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (!active()) {
                    return false;
                }
                if (open()) {
                    stage.touchDown(screenX, screenY, pointer, button);
                }
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (!active()) {
                    return false;
                }
                stage.touchUp(screenX, screenY, pointer, button);
                return true;
            }

            @Override
            public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
                if (!active()) {
                    return false;
                }
                stage.touchCancelled(screenX, screenY, pointer, button);
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (!active()) {
                    return false;
                }
                stage.touchDragged(screenX, screenY, pointer);
                return true;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                if (!active()) {
                    return false;
                }
                stage.mouseMoved(screenX, screenY);
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (!active()) {
                    return false;
                }
                stage.scrolled(amountX, amountY);
                return true;
            }
        };
    }

    private void key(int keycode) {
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        switch (keycode) {
            case Input.Keys.ESCAPE -> dismiss();
            case Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> {
                if (message != null && message.revealing()) {
                    message.finish();
                } else {
                    choose(focus);
                }
            }
            case Input.Keys.TAB -> move(shift ? -1 : 1);
            case Input.Keys.DOWN, Input.Keys.RIGHT -> move(1);
            case Input.Keys.UP, Input.Keys.LEFT -> move(-1);
            default -> {
                int number = number(keycode);
                if (number > 0) {
                    choose(number - 1);
                }
            }
        }
    }

    /** The number 1 to 9 on {@code keycode}, on the top row or the keypad, or 0. */
    private static int number(int keycode) {
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            return keycode - Input.Keys.NUM_1 + 1;
        }
        if (keycode >= Input.Keys.NUMPAD_1 && keycode <= Input.Keys.NUMPAD_9) {
            return keycode - Input.Keys.NUMPAD_1 + 1;
        }
        return 0;
    }

    private void move(int step) {
        if (!buttons.isEmpty()) {
            focus((focus + step + buttons.size()) % buttons.size());
        }
    }

    private void focus(int index) {
        focus = index;
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).focused(i == index);
        }
        if (detail != null && current != null) {
            Transmission.Response response = current.transmission.responses().get(index);
            detail.setText(response.label() == null || response.detail() == null ? "" : response.detail());
        }
    }

    /** Sends response {@code index} and closes the channel once its button has swept full of light. */
    private void choose(int index) {
        Entry shown = current;
        if (shown == null || closing || index < 0 || index >= buttons.size()) {
            return;
        }
        closing = true;
        if (message != null) {
            message.finish();
        }
        focus(index);
        UiResponseButton button = buttons.get(index);
        button.confirm();
        shown.channel.answer(index, true);
        close(motion ? button.confirmSeconds() : 0);
    }

    /** Closes the channel without a response. */
    private void dismiss() {
        Entry shown = current;
        if (shown == null || closing) {
            return;
        }
        closing = true;
        shown.channel.answer(0, false);
        close(0);
    }

    private void close(float delay) {
        Table shown = panel;
        if (!motion || shown == null) {
            closed();
            return;
        }
        shown.addAction(Actions.sequence(Actions.delay(delay),
              Actions.parallel(Actions.scaleTo(1, .02f, CLOSE_SECONDS, Interpolation.pow2In),
                    Actions.fadeOut(CLOSE_SECONDS)),
              Actions.run(this::closed)));
    }

    private void closed() {
        clear();
        current = null;
        closing = false;
    }

    /** The dialog was hidden or closed: takes {@code entry} down without an answer. */
    private void withdraw(Entry entry) {
        if (queue.remove(entry)) {
            return;
        }
        if (entry == current && !closing) {
            closing = true;
            close(0);
        }
    }

    /** The dialog shows {@code controls} now, after one of them changed. */
    private void update(Entry entry, List<Transmission.Control> controls) {
        entry.controls = List.copyOf(controls);
        if (entry != current || closing) {
            return;
        }
        if (!sameShape(shownControls, entry.controls)) {
            fillControls(entry.controls);
            return;
        }
        syncing = true;
        try {
            for (int i = 0; i < entry.controls.size(); i++) {
                sync(controlActors[i], entry.controls.get(i));
            }
        } finally {
            syncing = false;
        }
        shownControls = entry.controls;
    }

    private void sync(Actor actor, Transmission.Control control) {
        switch (control) {
            case Transmission.Text text -> ((UiRichText) actor).setText(text.html());
            case Transmission.Stepper stepper -> ((UiStepper) actor).value(listed(stepper)
                  ? (stepper.value() - stepper.min()) / stepper.step() : stepper.value());
            case Transmission.Select select -> {
                if (actor instanceof UiStepper selector) {
                    selector.value(Math.max(0, select.index()));
                }
            }
            case Transmission.Check check -> {
                UiChoice choice = (UiChoice) actor;
                choice.setChecked(check.ticked());
                choice.label().setText(check.text());
            }
        }
    }

    /** Whether {@code next} shows in the actors made for {@code shown}. */
    private static boolean sameShape(List<Transmission.Control> shown, List<Transmission.Control> next) {
        if (shown.size() != next.size()) {
            return false;
        }
        for (int i = 0; i < shown.size(); i++) {
            Transmission.Control before = shown.get(i);
            Transmission.Control after = next.get(i);
            boolean same = switch (before) {
                case Transmission.Text text -> after instanceof Transmission.Text;
                case Transmission.Check check -> after instanceof Transmission.Check;
                case Transmission.Select select -> after instanceof Transmission.Select other
                      && select.items().equals(other.items());
                case Transmission.Stepper stepper -> after instanceof Transmission.Stepper other
                      && stepper.min() == other.min() && stepper.max() == other.max() && stepper.step() == other.step();
            };
            if (!same) {
                return false;
            }
        }
        return true;
    }

    /** Lays the current transmission out, opening its channel when {@code animate} is set. */
    private void build(boolean animate) {
        Entry entry = Objects.requireNonNull(current);
        Transmission transmission = entry.transmission;
        clear();
        float stageHeight = stage.getHeight();
        float gap = space("lg");
        int speakers = (transmission.left() != null ? 1 : 0) + (transmission.right() != null ? 1 : 0);
        float sides = speakers * (SPEAKER_WIDTH + gap);
        columnWidth = Math.max(NARROWEST, Math.min(transmission.width(),
              stage.getWidth() - 2 * MARGIN - 4 * gap - sides));

        Table shown = ui.panel(true);
        shown.setTransform(true);
        shown.add(header(transmission)).growX().row();
        shown.add(ui.solid(faded(UiTheme.MINT, .35f))).height(1).growX().padTop(space("sm")).row();
        Table body = new Table();
        if (transmission.left() != null) {
            body.add(speaker(transmission.left(), transmission.signal())).top().width(SPEAKER_WIDTH).padRight(gap);
        }
        body.add(column(transmission, stageHeight)).top().width(columnWidth);
        if (transmission.right() != null) {
            body.add(speaker(transmission.right(), transmission.signal())).top().width(SPEAKER_WIDTH).padLeft(gap);
        }
        shown.add(body).padTop(space("md")).row();
        if (transmission.note() != null && !transmission.note().isBlank()) {
            shown.add(note(transmission, columnWidth + sides, stageHeight)).growX().padTop(space("md"));
        }
        root.add(shown).maxHeight(stageHeight - 2 * MARGIN);
        panel = shown;
        focus(Math.min(focus, Math.max(0, buttons.size() - 1)));

        if (!animate) {
            return;
        }
        shown.setScale(1, .02f);
        shown.getColor().a = 0;
        shown.addAction(Actions.sequence(Actions.delay(OPEN_DELAY),
              Actions.parallel(Actions.scaleTo(1, 1, OPEN_SECONDS, Interpolation.pow3Out),
                    Actions.fadeIn(OPEN_SECONDS * .6f))));
        UiRichText typed = Objects.requireNonNull(message);
        float rate = Math.max(TYPE_RATE, typed.document().length() / TYPE_LONGEST);
        typed.typeIn(WAITING);
        typed.addAction(Actions.delay(TYPE_DELAY, Actions.run(() -> typed.typeIn(rate))));
        for (int i = 0; i < buttons.size(); i++) {
            UiResponseButton button = buttons.get(i);
            button.getColor().a = 0;
            button.addAction(Actions.delay(RESPONSE_DELAY + i * RESPONSE_STAGGER, Actions.fadeIn(RESPONSE_SECONDS)));
        }
    }

    /** The header: the incoming transmission, its signal and the dialog's title. */
    private Table header(Transmission transmission) {
        int level = Math.max(0, Math.min(2, transmission.signal().level()));
        Color tone = level == 0 ? UiTheme.MINT : level == 1 ? UiTheme.AMBER : UiTheme.CORAL;
        Table header = new Table();
        Image live = ui.solid(UiTheme.MINT);
        if (motion) {
            live.addAction(Actions.forever(Actions.sequence(Actions.alpha(.2f, .6f, Interpolation.sine),
                  Actions.alpha(1, .6f, Interpolation.sine))));
        }
        header.add(live).size(6).padRight(space("sm"));
        header.add(ui.label(transmission.texts().header(), UiTheme.CAPS_FONT, 13, UiTheme.MINT)).left();
        header.add(bars(level, tone)).bottom().padLeft(space("md")).padBottom(3);
        header.add(ui.label(transmission.texts().status(), UiTheme.CAPS_FONT, 11, tone)).padLeft(space("sm"));
        header.add().growX();
        header.add(ui.label(transmission.title(), "hud-title", 16, UiTheme.TEXT)).right().padLeft(space("lg"));
        return header;
    }

    /** Three bars, fewer lit the rougher the signal; the last one lit drops out now and then on the roughest. */
    private Table bars(int level, Color tone) {
        Table bars = new Table();
        int lit = 3 - level;
        for (int i = 0; i < 3; i++) {
            Image bar = ui.solid(i < lit ? tone : faded(UiTheme.MUTED, .3f));
            bars.add(bar).size(3, 5 + i * 3).bottom().padLeft(i == 0 ? 0 : 2);
            if (motion && level == 2 && i == lit - 1) {
                bar.addAction(Actions.forever(Actions.sequence(Actions.alpha(.15f, .08f), Actions.delay(.4f),
                      Actions.alpha(1, .08f), Actions.delay(1.1f))));
            }
        }
        return bars;
    }

    /** A speaker's column: the channel they speak on, their picture and who they are. */
    private Table speaker(Transmission.Speaker speaker, Transmission.Signal signal) {
        Table column = new Table();
        column.top();
        column.add(ui.label(speaker.source(), UiTheme.CAPS_FONT, 11, UiTheme.MINT)).left().row();
        Transmission.Picture picture = speaker.picture();
        if (picture != null) {
            float height = Math.min(SPEAKER_HEIGHT, SPEAKER_WIDTH * picture.height() / picture.width());
            float width = height * picture.width() / picture.height();
            TextureRegion region = region(picture);
            Actor shown;
            if (speaker.feed()) {
                UiSignalFeed feed = new UiSignalFeed(region, white, width, height, FEED_TINT, FEED_LINE,
                      signal(signal), motion);
                owned.add(feed);
                shown = feed;
            } else {
                shown = new Image(region);
            }
            column.add(shown).size(width, height).padTop(space("xs")).row();
        }
        UiRichText about = rich(speaker.description(), small()).wrapWidth(SPEAKER_WIDTH);
        column.add(about).width(SPEAKER_WIDTH).padTop(space("sm"));
        return column;
    }

    /** The middle column: a picture, the message, the dialog's controls and the responses. */
    private Table column(Transmission transmission, float stageHeight) {
        Table column = new Table();
        column.top();
        Transmission.Picture picture = transmission.image();
        if (picture != null) {
            float width = Math.min(columnWidth, picture.width());
            float height = width * picture.height() / picture.width();
            if (height > stageHeight * IMAGE_SHARE) {
                height = stageHeight * IMAGE_SHARE;
                width = height * picture.width() / picture.height();
            }
            column.add(new Image(region(picture))).size(width, height).padBottom(space("md")).row();
        }
        column.add(section(transmission.texts().message(), UiTheme.MINT)).growX().row();
        UiRichText text = rich(transmission.message(), theme.richText(14, UiTheme.TEXT, UiTheme.MINT))
              .wrapWidth(columnWidth);
        message = text;
        ScrollPane scroll = ui.scrollList(text);
        column.add(scroll).width(columnWidth).maxHeight(stageHeight * MESSAGE_SHARE).padTop(space("xs")).row();
        controlTable.clearChildren();
        fillControls(current == null ? List.of() : current.controls);
        column.add(controlTable).growX().row();
        if (!transmission.responses().isEmpty()) {
            column.add(section(transmission.texts().response(), UiTheme.MINT)).growX().padTop(space("md")).row();
            column.add(responses(transmission)).growX().padTop(space("xs")).row();
            boolean details = transmission.responses().stream()
                  .anyMatch(response -> response.label() != null && response.detail() != null);
            if (!transmission.vertical() && details) {
                detail = rich("", small()).wrapWidth(columnWidth);
                column.add(detail).growX().padTop(space("xs"));
            }
        }
        return column;
    }

    /** The responses: stacked rows showing their details, or side by side, wrapping onto more rows. */
    private Table responses(Transmission transmission) {
        Table list = new Table();
        float gap = space("sm");
        Table row = null;
        float rowWidth = 0;
        for (int i = 0; i < transmission.responses().size(); i++) {
            UiResponseButton button = response(transmission, i);
            buttons.add(button);
            if (transmission.vertical()) {
                list.add(button).growX().padBottom(gap).row();
                continue;
            }
            float width = button.getPrefWidth();
            if (row == null || rowWidth > 0 && rowWidth + gap + width > columnWidth) {
                row = new Table();
                list.add(row).growX().padBottom(gap).row();
                rowWidth = 0;
            }
            row.add(button).growX().padLeft(rowWidth > 0 ? gap : 0);
            rowWidth += (rowWidth > 0 ? gap : 0) + width;
        }
        return list;
    }

    private UiResponseButton response(Transmission transmission, int index) {
        Transmission.Response response = transmission.responses().get(index);
        float pad = space("sm");
        Label key = index < KEYED ? ui.label(String.valueOf(index + 1), UiTheme.CAPS_FONT, 12, UiTheme.MINT) : null;
        // The key column, the padding and the button's frame
        float wrap = columnWidth - 4 * pad - 24;
        Table content = new Table();
        String label = response.label() != null ? response.label() : response.detail();
        UiRichText text = rich(label, theme.richText(14, UiTheme.TEXT, UiTheme.MINT)).skipOnClick(false);
        if (transmission.vertical() || text.getPrefWidth() > wrap) {
            text.wrapWidth(wrap);
        }
        content.add(text).left().growX().row();
        if (transmission.vertical() && response.label() != null && response.detail() != null) {
            UiRichText about = rich(response.detail(), small()).skipOnClick(false).wrapWidth(wrap);
            content.add(about).left().growX().padTop(space("xxs"));
        }
        UiResponseButton button = new UiResponseButton(content, key, white, UiTheme.MINT, pad, motion);
        UiKit.onChange(button, () -> choose(index));
        button.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, @Nullable Actor fromActor) {
                if (pointer == -1 && !closing) {
                    focus(index);
                }
            }
        });
        return button;
    }

    /** The dialog's extra controls, each label beside the control after it. */
    private void fillControls(List<Transmission.Control> controls) {
        controlTable.clearChildren();
        controlActors = new Actor[controls.size()];
        shownControls = controls;
        if (controls.isEmpty()) {
            return;
        }
        controlTable.padTop(space("sm"));
        for (int i = 0; i < controls.size(); i++) {
            Transmission.Control control = controls.get(i);
            boolean labels = control instanceof Transmission.Text && i + 1 < controls.size()
                  && !(controls.get(i + 1) instanceof Transmission.Text);
            controlActors[i] = control(i, control, labels ? columnWidth * .55f : columnWidth);
            if (labels) {
                controlActors[i + 1] = control(i + 1, controls.get(i + 1), columnWidth);
                Table row = new Table();
                row.add(controlActors[i]).left().growX();
                row.add(controlActors[i + 1]).right().padLeft(space("md"));
                controlTable.add(row).growX().padTop(space("xs")).row();
                i++;
            } else {
                controlTable.add(controlActors[i]).left().padTop(space("xs")).row();
            }
        }
    }

    private Actor control(int index, Transmission.Control control, float wrap) {
        return switch (control) {
            case Transmission.Text text -> rich(text.html(), small()).wrapWidth(wrap);
            case Transmission.Stepper stepper -> stepper(index, stepper);
            case Transmission.Select select -> {
                if (select.items().isEmpty()) {
                    yield new Actor();
                }
                UiStepper selector = ui.selector(select.items(), Math.max(0, select.index()));
                UiKit.onChange(selector, () -> send(index, selector.value()));
                yield selector;
            }
            case Transmission.Check check -> {
                UiChoice choice = ui.check(check.text(), check.ticked());
                choice.setProgrammaticChangeEvents(false);
                UiKit.onChange(choice, () -> send(index, choice.isChecked() ? 1 : 0));
                yield choice;
            }
        };
    }

    /** A number stepper; one stepping other than by 1 lists its values, unless there are too many. */
    private UiStepper stepper(int index, Transmission.Stepper stepper) {
        if (listed(stepper)) {
            List<String> values = new ArrayList<>();
            for (int value = stepper.min(); value <= stepper.max(); value += stepper.step()) {
                values.add(String.valueOf(value));
            }
            int chosen = UiStepper.clamp((stepper.value() - stepper.min()) / stepper.step(), 0, values.size() - 1);
            UiStepper selector = ui.selector(values, chosen);
            UiKit.onChange(selector, () -> send(index, stepper.min() + selector.value() * stepper.step()));
            return selector;
        }
        UiStepper plain = ui.stepper(stepper.value(), stepper.min(), stepper.max());
        UiKit.onChange(plain, () -> send(index, plain.value()));
        return plain;
    }

    private static boolean listed(Transmission.Stepper stepper) {
        return stepper.step() > 1 && stepper.max() >= stepper.min()
              && (stepper.max() - stepper.min()) / stepper.step() < LISTED_VALUES;
    }

    private void send(int index, int value) {
        Entry shown = current;
        if (!syncing && shown != null && !closing) {
            shown.channel.control(index, value);
        }
    }

    /** The out-of-character note, on an amber band along the panel's foot. */
    private Table note(Transmission transmission, float width, float stageHeight) {
        Table band = new Table();
        band.setBackground(ui.skin.newDrawable("white", faded(UiTheme.AMBER, .08f)));
        float pad = space("md");
        band.add(ui.solid(UiTheme.AMBER)).width(2).growY().padRight(pad);
        Table inside = new Table();
        inside.add(section(transmission.texts().information(), UiTheme.AMBER)).growX().row();
        UiRichText text = rich(Objects.requireNonNull(transmission.note()),
              theme.richText(13, UiTheme.TEXT, UiTheme.AMBER)).wrapWidth(width - 2 * pad - 2);
        inside.add(ui.scrollList(text)).growX().maxHeight(stageHeight * NOTE_SHARE).padTop(space("xs"));
        band.add(inside).growX().pad(space("sm"), 0, space("sm"), pad);
        return band;
    }

    /** A section's title in {@code color} and a faint rule after it. */
    private Table section(String title, Color color) {
        Table row = new Table();
        row.add(ui.label(title, UiTheme.CAPS_FONT, 11, color)).left();
        row.add(ui.solid(faded(color, .3f))).height(1).growX().padLeft(space("sm"));
        return row;
    }

    private UiRichText.Style small() {
        return theme.richText(12.5f, UiTheme.tokenColor("text.secondary"), UiTheme.MINT);
    }

    /** Rich text whose links the dialog follows. */
    private UiRichText rich(String html, UiRichText.Style style) {
        UiRichText text = new UiRichText(html, style);
        Entry shown = current;
        text.onLink(href -> {
            if (shown != null && shown == current && !closing) {
                shown.channel.link(href);
            }
        });
        return text;
    }

    /** A texture of {@code picture}, disposed with the transmission. */
    private TextureRegion region(Transmission.Picture picture) {
        Pixmap pixmap = new Pixmap(picture.width(), picture.height(), Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        int[] argb = picture.argb();
        for (int y = 0; y < picture.height(); y++) {
            for (int x = 0; x < picture.width(); x++) {
                int pixel = argb[y * picture.width() + x];
                pixmap.drawPixel(x, y, (pixel << 8) | (pixel >>> 24));
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        owned.add(texture);
        return new TextureRegion(texture);
    }

    /** The kit's feed roughness for the dialog's: its static marks over a share of the feed, its glitches a chance. */
    static UiSignalFeed.Signal signal(Transmission.Signal signal) {
        int stepsMin = Math.max(1, signal.glitchFramesMin());
        return new UiSignalFeed.Signal(signal.tintOpacity(), Math.max(2, signal.scanlineGap()),
              signal.scanlineOpacity(), Math.min(1, signal.noiseMarks() / 400f), signal.noiseOpacity(),
              signal.glitchInterval() <= 0 ? 0 : 1f / signal.glitchInterval(), stepsMin,
              Math.max(stepsMin, signal.glitchFramesMax()), signal.glitchOffset(), signal.glitchHeightMin(),
              Math.max(signal.glitchHeightMin(), signal.glitchHeightMax()), signal.glitchLineOpacity());
    }

    /** Takes the transmission's actors down and frees its textures and feeds. */
    private void clear() {
        root.clearChildren();
        controlTable.clearChildren();
        controlTable.padTop(0);
        controlActors = new Actor[0];
        shownControls = List.of();
        buttons.clear();
        panel = null;
        message = null;
        detail = null;
        for (Disposable resource : owned) {
            resource.dispose();
        }
        owned.clear();
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static Color faded(Color color, float alpha) {
        return new Color(color.r, color.g, color.b, alpha);
    }

    private static float space(String token) {
        return DesignTokens.shipped().space().get(token);
    }

    /** Ends every transmission showing or waiting, so their dialogs show their windows instead. */
    @Override
    public void dispose() {
        disposed = true;
        Entry shown = current;
        if (shown != null && !closing) {
            shown.channel.fallBack();
        }
        for (Entry waiting : queue) {
            waiting.channel.fallBack();
        }
        queue.clear();
        current = null;
        if (stage != null) {
            clear();
            stage.dispose();
        }
        if (blur != null) {
            blur.dispose();
        }
    }

    /** A transmission offered to the view, and the handle its dialog keeps. */
    private final class Entry implements Transmission.Handle {
        private final Transmission transmission;
        private final Transmission.Channel channel;
        /** GPU thread: the controls as the dialog last showed them. */
        private List<Transmission.Control> controls;

        private Entry(Transmission transmission, Transmission.Channel channel) {
            this.transmission = transmission;
            this.channel = channel;
            controls = transmission.controls();
        }

        @Override
        public void update(List<Transmission.Control> next) {
            GpuHost.post(() -> TransmissionView.this.update(this, next));
        }

        @Override
        public void withdraw() {
            GpuHost.post(() -> TransmissionView.this.withdraw(this));
        }
    }
}

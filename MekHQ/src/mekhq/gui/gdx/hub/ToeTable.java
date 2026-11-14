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

import static mekhq.utilities.MHQInternationalization.getFormattedText;
import static mekhq.utilities.MHQInternationalization.getText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.GUIPreferences;
import megamek.client.ui.design.DesignTokens;
import megamek.client.ui.gdx.UiButton;
import megamek.client.ui.gdx.UiKit;
import megamek.client.ui.gdx.UiStepper;
import megamek.client.ui.gdx.UiTheme;
import mekhq.campaign.force.FormationSlot;
import mekhq.campaign.force.FormationSlot.Kind;
import mekhq.campaign.force.FormationSlot.Weight;
import mekhq.gui.gdx.hub.ToeLayout.Block;
import mekhq.gui.gdx.hub.ToeLayout.Slot;
import mekhq.gui.gdx.hub.ToeLayout.Vacancy;
import mekhq.gui.gdx.hub.ToeMoves.Load;
import mekhq.gui.gdx.hub.ToeMoves.Move;
import mekhq.gui.gdx.hub.ToeMoves.Verdict;
import mekhq.gui.gdx.hub.ToeSlots.Fit;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Issue;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;

/**
 * The TO&amp;E: the force's formations raised on the command post's display ({@link ToeView}), with its strength
 * over it, tabs to light only what's at one of the places the force is spread over, pins naming its formations and
 * units, and a card for the unit pointed at.
 *
 * <p>GL thread throughout. The screen showing it hands it its input ({@link #input}) and draws it in place of its own
 * view while it's {@link #shown}.</p>
 */
final class ToeTable implements Disposable {
    private static final Color LABEL_PLATE = new Color(0.035f, 0.047f, 0.06f, 0.9f);
    private static final Color LABEL_INK = new Color(0.93f, 0.95f, 0.97f, 1);
    private static final Color LABEL_FORMATION = new Color(0.45f, 0.72f, 0.95f, 1);
    private static final Color ALL_READY = new Color(0.45f, 0.9f, 0.55f, 1);
    private static final Color ISSUE_BAD = new Color(1.0f, 0.42f, 0.36f, 1);
    private static final Color ISSUE_WARN = new Color(1.0f, 0.78f, 0.36f, 1);
    private static final Color ISSUE_CREW = new Color(1.0f, 0.55f, 0.45f, 1);
    private static final Color ISSUE_TIRED = new Color(1.0f, 0.92f, 0.65f, 1);
    private static final Color ISSUE_LAID_UP = new Color(0.68f, 0.72f, 0.78f, 1);
    private static final Color ISSUE_AWAY = new Color(0.75f, 0.92f, 1.0f, 1);
    private static final Color SLOT_VACANT = new Color(0.66f, 0.74f, 0.84f, 1);
    private static final Color SLOT_MISFIT = new Color(0.9f, 0.74f, 1.0f, 1);
    private static final Color SCRIM = new Color(0, 0, 0, 0.45f);
    /** How many pixels a cell must take on screen before vacant slots show the kind of unit they call for. */
    private static final float GHOST_ICONS_FROM = 56;
    /** The most slots a formation may be authorized. */
    private static final int MOST_SLOTS = 24;
    private static final float EDITOR_WIDTH = 460;
    private static final float PICKER_WIDTH = 620;
    private static final float PICKER_HEIGHT = 340;
    /** How many troubles a folded formation's card names before it only counts the rest. */
    private static final int BADGES = 3;
    /** How many pixels a cell must take on screen before units' pins show. */
    private static final float UNIT_PINS_FROM = 110;
    /** The fewest pixels a formation's name is cut to before its label hides instead. */
    private static final float NAME_LEAST = 40;
    private static final String PIN_NAME = "name";
    private static final int CHIPS_A_ROW = 5;

    /** Keeps a click on a panel from reaching the table under it. */
    private static final InputListener SWALLOW = new InputListener() {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            return true;
        }
    };

    /** Which labels the table shows: formations' and units'; formations' only; none but the unit pointed at. */
    enum Labels {
        ALL, FORMATIONS, NONE;

        Labels next() {
            return values()[(ordinal() + 1) % values().length];
        }

        String text() {
            return getText("Toe.labels." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** A place the table can be narrowed to: its tab's name and which units are there. */
    record Place(String name, @Nullable Predicate<Card> units) {
    }

    /**
     * A pin over a formation's head ({@code block}) or a unit's standee ({@code slot}); a folded formation's also
     * has its {@code name} cell, to narrow it to the card, the name's own width and the rest of the pin's, and its
     * row of {@code badges}, with a {@code compact} one for when that doesn't fit.
     */
    private record Pin(Actor actor, @Nullable Block block, @Nullable Slot slot, @Nullable Cell<Label> name,
          float nameWide, float rest, @Nullable Actor badges, @Nullable Actor compact) {
        Pin(Actor actor, @Nullable Block block, @Nullable Slot slot) {
            this(actor, block, slot, null, 0, 0, null, null);
        }
    }

    /** A vacant slot's mark of the kind of unit it calls for, over its ring. */
    private record Ghost(Actor icon, Vacancy vacancy) {
    }

    /** What the picker fills: {@code slot}, vacant in {@code group}, and the units that fit it. */
    record Filling(ToeSnapshot.Group group, FormationSlot slot, List<ToeFill.Candidate> candidates) {
    }

    private final ToeView view = new ToeView(this::picked, this::openCard, this::fill, this::edit,
          new ToeView.Drags() {
        @Override
        public boolean lift(Load load) {
            return lifted(load);
        }

        @Override
        public void drop(Load load, int into) {
            dropped(load, into);
        }
    });
    private final Runnable leave;
    private final Consumer<List<Move>> reorganize;
    private final BiConsumer<Integer, List<FormationSlot>> authorize;
    /** The moves that put back the last drop, none once undone. */
    private List<Move> undo = List.of();
    private final Group pins = new Group();
    private final List<Pin> pinned = new ArrayList<>();
    private final List<Ghost> ghosts = new ArrayList<>();
    private final List<Rectangle> placedLabels = new ArrayList<>();
    private final Rectangle labelProbe = new Rectangle();
    private final Table root = new Table();
    private final Vector3 at = new Vector3();
    private UiKit ui;
    private float scale = 1;
    private Stage stage;
    private Table header;
    private Label force;
    private Table strength;
    private Table tabs;
    private @Nullable UiKit.Segmented segments;
    private UiButton labelsButton;
    private Label hint;
    private Label empty;
    /** What a drag carries, by the pointer, and where it would go or why it can't. */
    private Table plate;
    private Label plateName;
    private Label plateWhere;
    private Label plateWhy;
    private Label plateFit;
    private String plateSays = "";
    private Table notice;
    private Label noticeText;
    private Table hoverCard;
    private Label hoverName;
    private Label hoverCrew;
    private Label hoverDetail;
    /** The slot editor's dimmed layer over the table, and the formation it edits and the slots it has so far. */
    private Table editorLayer;
    private @Nullable ToeSnapshot.Group editing;
    private final List<FormationSlot> draft = new ArrayList<>();
    /** The vacant slot the picker fills, on the same layer, and the place it's narrowed to. */
    private @Nullable Filling filling;
    private int fillPlace;
    private Labels labels = Labels.ALL;
    private List<Place> places = List.of();
    private int placeIndex;
    private @Nullable ToeSnapshot snapshot;
    private @Nullable ToeLayout layout;
    private @Nullable ToeFolds folds;
    private @Nullable InputProcessor input;
    private boolean shown;
    private boolean motion = true;
    private int pointerX;
    private int pointerY;

    /**
     * {@code leave} steps back off the table to the command post; {@code reorganize} makes the moves a drag drops,
     * and {@code authorize} gives a formation the slots its editor sets, the force coming back changed through
     * {@link #refresh}.
     */
    ToeTable(Runnable leave, Consumer<List<Move>> reorganize, BiConsumer<Integer, List<FormationSlot>> authorize) {
        this.leave = leave;
        this.reorganize = reorganize;
        this.authorize = authorize;
    }

    /** Sets the table up on the hub's look, at {@code scale} pixels a stage unit. */
    void create(UiKit ui, float scale) {
        this.ui = ui;
        this.scale = scale;
        view.create();
        stage = new Stage(new ScreenViewport());
        pins.setTouchable(Touchable.disabled);
        stage.addActor(pins);
        root.setFillParent(true);
        root.top().pad(space("xl"));
        stage.addActor(root);
        header = header();
        header.setTouchable(Touchable.enabled);
        header.addListener(SWALLOW);
        tabs = new Table();
        Table top = new Table();
        top.add(header).top().left().expandX();
        UiButton home = ui.button("hud-utility", "locate", getText("Toe.home"), null);
        UiKit.onChange(home, () -> frameShown(false, false));
        top.add(home).top().right().padRight(space("sm"));
        labelsButton = ui.button("hud-utility", "tag", labels.text(), null);
        UiKit.onChange(labelsButton, this::cycleLabels);
        top.add(labelsButton).top().right().padRight(space("sm"));
        UiButton back = ui.button("hud-utility", "chevron-left", getText("Toe.back"), null);
        UiKit.onChange(back, leave::run);
        top.add(back).top().right();
        root.add(top).growX().row();
        root.add(tabs).left().padTop(space("sm")).row();
        root.add().grow();
        empty = ui.label(getText("Toe.none"), "hud-body", 16, UiTheme.tokenColor("text.secondary"));
        empty.setVisible(false);
        Table emptyLayer = new Table();
        emptyLayer.setFillParent(true);
        emptyLayer.setTouchable(Touchable.disabled);
        emptyLayer.add(empty);
        stage.addActor(emptyLayer);
        Table hintLayer = new Table();
        hintLayer.setFillParent(true);
        hintLayer.setTouchable(Touchable.disabled);
        hintLayer.bottom().pad(space("md"));
        hint = ui.label(getText("Toe.hint"), "hud-body", 12, UiTheme.tokenColor("text.secondary"));
        hintLayer.add(hint);
        stage.addActor(hintLayer);
        stage.addActor(hoverCard());
        stage.addActor(noticeLayer());
        stage.addActor(plate());
        editorLayer = new Table();
        editorLayer.setFillParent(true);
        editorLayer.setBackground(ui.skin.newDrawable("white", SCRIM));
        editorLayer.setTouchable(Touchable.enabled);
        editorLayer.addListener(SWALLOW);
        editorLayer.setVisible(false);
        stage.addActor(editorLayer);
    }

    private Table noticeLayer() {
        Table layer = new Table();
        layer.setFillParent(true);
        layer.setTouchable(Touchable.disabled);
        layer.bottom().padBottom(space("xl") * 2);
        notice = ui.panel(true);
        noticeText = ui.label("", "hud-body", 14, UiTheme.tokenColor("text.primary"));
        notice.add(noticeText);
        notice.setVisible(false);
        layer.add(notice);
        return layer;
    }

    private Table plate() {
        plate = ui.panel(true);
        plate.setTouchable(Touchable.disabled);
        plate.defaults().left();
        plateName = ui.label("", "hud-body", 14, UiTheme.tokenColor("text.primary"));
        plateWhere = ui.label("", "hud-body", 13, LABEL_FORMATION);
        plateWhy = ui.label("", "hud-body", 13, UiTheme.tokenColor("status.warning"));
        plateFit = ui.label("", "hud-body", 13, SLOT_MISFIT);
        plate.setVisible(false);
        return plate;
    }

    /** Shows {@code text} a moment over the table, then fades it. */
    void notice(String text) {
        if (notice == null) {
            return;
        }
        noticeText.setText(text);
        notice.pack();
        notice.setVisible(true);
        notice.clearActions();
        notice.getColor().a = 0;
        notice.addAction(Actions.sequence(Actions.fadeIn(motion ? 0.15f : 0), Actions.delay(2.6f),
              Actions.fadeOut(motion ? 0.4f : 0), Actions.visible(false)));
    }

    /** Whether a drag may pick {@code load} up: not if it's out on a deployment, which the table says. */
    private boolean lifted(Load load) {
        if (ToeMoves.locked(load)) {
            notice(getFormattedText("Toe.drag.locked", name(load)));
            return false;
        }
        return true;
    }

    /** Makes the moves dropping {@code load} into {@code into} does, which a Ctrl+Z puts back. */
    private void dropped(Load load, int into) {
        ToeMoves rules = view.rules();
        if (rules == null) {
            return;
        }
        List<Move> moves = rules.moves(load, into);
        if (moves.isEmpty()) {
            return;
        }
        undo = rules.undoing(moves);
        reorganize.accept(moves);
        notice(getFormattedText("Toe.moved", name(load), targetName(rules, into)));
    }

    /** Puts back the last drop. */
    private void undo() {
        if (undo.isEmpty()) {
            return;
        }
        reorganize.accept(undo);
        undo = List.of();
        notice(getText("Toe.undone"));
    }

    /** What {@code load} is called: its formation's name, its unit's, or how many units it is. */
    private static String name(Load load) {
        if (load.formation() != null) {
            return load.formation().name();
        }
        return load.units().size() == 1 ? load.units().get(0).name()
              : getFormattedText("Toe.drag.units", load.units().size());
    }

    private static String targetName(ToeMoves rules, int into) {
        ToeSnapshot.Group group = rules.group(into);
        return into == ToeMoves.UNASSIGNED || group == null ? getText("Toe.unassigned") : group.name();
    }

    /** Keeps the carried load's plate by the pointer, saying where it would go or why it can't. */
    private void placePlate() {
        Load load = view.carried();
        ToeMoves rules = view.rules();
        if (load == null || rules == null) {
            plate.setVisible(false);
            plateSays = "";
            return;
        }
        Block target = view.dropTarget();
        Verdict verdict = view.dropVerdict();
        String where = "";
        String why = "";
        Fit fit = Fit.OPEN;
        if (target != null && verdict != null) {
            int into = ToeMoves.target(target);
            if (verdict == Verdict.MOVE && load.formation() == null) {
                fit = ToeSlots.fit(rules.group(into), load.units());
            }
            switch (verdict) {
                case MOVE -> where = getFormattedText("Toe.drag.into", targetName(rules, into));
                case TARGET_LOCKED -> why = getFormattedText("Toe.drag.targetLocked", targetName(rules, into));
                case INTO_ITSELF -> why = getText("Toe.drag.intoItself");
                case NOT_UNASSIGNED -> why = getText("Toe.drag.notUnassigned");
                default -> {
                }
            }
        }
        String name = name(load);
        String says = name + '\n' + where + '\n' + why + '\n' + fit;
        if (!says.equals(plateSays)) {
            plateSays = says;
            plate.clearChildren();
            plateName.setText(name);
            plate.add(plateName).row();
            if (!where.isEmpty()) {
                plateWhere.setText(where);
                plate.add(plateWhere).padTop(space("xxs")).row();
            }
            if (!why.isEmpty()) {
                plateWhy.setText(why);
                plate.add(plateWhy).padTop(space("xxs")).row();
            }
            if (fit != Fit.OPEN) {
                plateFit.setText(getText(fit == Fit.FITS ? "Toe.slot.fills" : "Toe.slot.misfits"));
                plateFit.setColor(fit == Fit.FITS ? ALL_READY : SLOT_MISFIT);
                plate.add(plateFit).padTop(space("xxs")).row();
            }
            plate.pack();
        }
        Vector2 pointer = stage.screenToStageCoordinates(new Vector2(view.pointerX(), view.pointerY()));
        float x = pointer.x + space("lg");
        if (x + plate.getWidth() > stage.getWidth()) {
            x = pointer.x - space("lg") - plate.getWidth();
        }
        plate.setPosition(x, Math.max(0, pointer.y - space("lg") - plate.getHeight()));
        plate.setVisible(true);
        plate.toFront();
    }

    private Table header() {
        Table panel = ui.panel(true);
        panel.defaults().left();
        panel.add(ui.caption(getText("Toe.overline"), true)).row();
        force = ui.roleTypeLabel("title", "", "text.primary");
        panel.add(force).padTop(space("xxs")).row();
        strength = new Table();
        panel.add(strength).padTop(space("sm")).row();
        return panel;
    }

    private Table hoverCard() {
        hoverCard = ui.panel(true);
        hoverCard.setTouchable(Touchable.disabled);
        hoverCard.defaults().left();
        hoverName = ui.roleTypeLabel("name", "", "text.primary");
        hoverCrew = ui.label("", "hud-body", 13, UiTheme.tokenColor("text.primary"));
        hoverDetail = ui.label("", "hud-body", 13, UiTheme.tokenColor("text.secondary"));
        hoverCard.add(hoverName).row();
        hoverCard.add(hoverCrew).padTop(space("xxs")).row();
        hoverCard.add(hoverDetail).padTop(space("xxs")).row();
        hoverCard.setVisible(false);
        return hoverCard;
    }

    /** Whether the table is up, so the screen draws it and gives it its input. */
    boolean shown() {
        return shown;
    }

    /** Shows {@code shown}, the table rising with the whole force in view. */
    void open(ToeSnapshot shown) {
        this.shown = true;
        motion = !GUIPreferences.getInstance().getReduceMotion();
        view.reduceMotion(!motion);
        header.getColor().a = 0;
        header.clearActions();
        header.addAction(Actions.delay(motion ? 0.4f : 0, Actions.fadeIn(motion ? 0.4f : 0)));
        placeIndex = 0;
        show(shown, true);
    }

    /** Shows {@code shown}, the force as it is now, in place: the camera, the place looked at and the pick stay. */
    void refresh(ToeSnapshot shown) {
        if (this.shown) {
            show(shown, false);
        }
    }

    /** Puts the table away. */
    void close() {
        shown = false;
        view.cancelDrag();
        hoverCard.setVisible(false);
        closeEditor();
    }

    private void show(ToeSnapshot shown, boolean opening) {
        String previous = placeIndex < places.size() ? places.get(placeIndex).name() : null;
        snapshot = shown;
        force.setText(shown.force());
        folds = new ToeFolds(shown);
        places = places(shown);
        placeIndex = 0;
        for (int i = 0; i < places.size() && !opening; i++) {
            if (places.get(i).name().equals(previous)) {
                placeIndex = i;
            }
        }
        tabs.clearChildren();
        segments = null;
        if (places.size() > 1) {
            segments = ui.segmented("hud-tab", false, places.stream().map(Place::name).toArray(String[]::new));
            for (int i = 0; i < segments.buttons.size(); i++) {
                int picked = i;
                UiKit.onChange(segments.buttons.get(i), () -> {
                    if (picked != placeIndex) {
                        narrow(picked);
                    }
                });
            }
            segments.select(placeIndex);
            tabs.add(segments);
        }
        view.filter(places.get(placeIndex).units());
        farthest();
        if (opening) {
            frameShown(true, true);
        } else {
            folds.reset(view.wantCellPixels());
            relayout(false);
        }
        strength(shown);
        empty.setVisible(shown.allUnits().isEmpty() && shown.root().children().isEmpty());
    }

    /** Lays the force out folded as {@link #folds} has it now, and shows it, the change morphing unless it opens. */
    private void relayout(boolean opening) {
        if (snapshot == null || folds == null) {
            return;
        }
        layout = layoutOf(folds);
        view.show(snapshot, layout, opening);
        pinAll(layout);
    }

    private ToeLayout layoutOf(ToeFolds folded) {
        return ToeLayout.of(Objects.requireNonNull(snapshot), getText("Toe.unassigned"), folded);
    }

    /** Lets the camera back off far enough to take everything in folded as far as it goes, and a little more. */
    private void farthest() {
        if (folds == null) {
            return;
        }
        folds.reset(0);
        float[] most = layoutOf(folds).bounds();
        view.farthest(Math.max(12, Math.max(1.4f * view.fitDistance(most),
              view.distanceFor(0.8f * folds.lowest()))));
    }

    /**
     * Frames what's lit, folded as little as lets it all in: from nothing folded to everything, the first way of
     * folding it whose framing is close enough in for it to be folded so; jumps there {@code now}, or glides.
     */
    private void frameShown(boolean now, boolean opening) {
        if (snapshot == null || folds == null) {
            return;
        }
        Predicate<Card> lit = places.isEmpty() ? null : places.get(placeIndex).units();
        float[] steps = folds.steps();
        float upper = Float.MAX_VALUE;
        float lower = 0;
        float cell = 0;
        float[] framed = null;
        for (int i = 0; i <= steps.length; i++) {
            lower = i < steps.length ? steps[i] : 0;
            folds.reset(lower);
            ToeLayout folded = layoutOf(folds);
            float[] bounds = lit == null ? null : folded.bounds(lit);
            bounds = bounds == null ? folded.bounds() : bounds;
            float fit = view.fitCellPixels(bounds);
            if (fit >= lower) {
                // Kept folded just past the next fold, short of where the hysteresis opens it
                cell = Math.max(lower, Math.min(fit, upper * 1.04f));
                framed = bounds;
                break;
            }
            upper = lower;
        }
        folds.reset(lower);
        relayout(opening);
        view.frame(Objects.requireNonNull(framed), cell, now);
    }

    /** Opens the folded formation {@code card} one level, gliding in on it as it opens. */
    private void openCard(Block card) {
        if (folds == null || layout == null) {
            return;
        }
        float cell = folds.threshold(card.key()) * 1.15f;
        folds.reset(cell);
        relayout(false);
        Block opened = layout.holder(card.key());
        float[] at = opened == null ? null : layout.reach(opened.key());
        view.frame(at != null ? at
              : new float[] { card.x(), card.z(), card.x() + card.width(), card.z() + card.depth() }, cell, false);
    }

    /** The places to narrow the table to: all of it, then, if the force is spread out, each place it's at. */
    static List<Place> places(ToeSnapshot shown) {
        List<Place> places = new ArrayList<>();
        places.add(new Place(getText("Toe.filter.all"), null));
        List<Card> all = shown.allUnits();
        boolean transit = all.stream().anyMatch(Card::transit);
        if (shown.bases().isEmpty() && !transit) {
            return places;
        }
        // The main force takes in everything on its way somewhere, whichever base it's on the books of
        places.add(new Place(getText("Toe.filter.main"), card -> card.place() == ToeSnapshot.MAIN_FORCE
              || card.transit()));
        for (int i = 0; i < shown.bases().size(); i++) {
            int place = i + 1;
            places.add(new Place(shown.bases().get(i), card -> card.place() == place));
        }
        if (transit) {
            places.add(new Place(getText("Toe.filter.transit"), Card::transit));
        }
        return places;
    }

    /** Lights only what's at place {@code index}, its strength over the table, and glides onto it. */
    void narrow(int index) {
        if (index < 0 || index >= places.size()) {
            return;
        }
        placeIndex = index;
        if (segments != null) {
            segments.select(index);
        }
        view.filter(places.get(index).units());
        if (snapshot != null) {
            frameShown(false, false);
            strength(snapshot);
        }
    }

    /** What's at the place looked at: how many, how many ready, and what keeps the rest out of the field. */
    private void strength(ToeSnapshot shown) {
        strength.clearChildren();
        int units = 0;
        int ready = 0;
        int damaged = 0;
        int wrecked = 0;
        int repair = 0;
        int laidUp = 0;
        int deployed = 0;
        int transit = 0;
        int noCrew = 0;
        int fatigued = 0;
        for (Card card : shown.allUnits()) {
            if (!view.lit(card)) {
                continue;
            }
            units++;
            ready += card.ready() ? 1 : 0;
            Condition condition = card.condition();
            wrecked += condition.fights() ? 0 : 1;
            damaged += condition.fights() && condition != Condition.SOUND ? 1 : 0;
            repair += card.work() == Work.REPAIR || card.work() == Work.REFIT ? 1 : 0;
            laidUp += card.work() == Work.MOTHBALLED || card.work() == Work.MOTHBALLING
                  || card.work() == Work.ACTIVATING ? 1 : 0;
            deployed += card.deployed() ? 1 : 0;
            transit += card.transit() ? 1 : 0;
            noCrew += card.crew() == Crew.NONE ? 1 : 0;
            fatigued += card.fatigued() ? 1 : 0;
        }
        chip("Toe.count.units", units, UiKit.Tone.NORMAL, true);
        chip("Toe.count.ready", ready, UiKit.Tone.OK, true);
        chip("Toe.count.damaged", damaged, UiKit.Tone.WARN, false);
        chip("Toe.count.wrecked", wrecked, UiKit.Tone.BAD, false);
        chip("Toe.count.repair", repair, UiKit.Tone.WARN, false);
        chip("Toe.count.laidUp", laidUp, UiKit.Tone.NORMAL, false);
        chip("Toe.count.deployed", deployed, UiKit.Tone.NORMAL, false);
        chip("Toe.count.transit", transit, UiKit.Tone.NORMAL, false);
        chip("Toe.count.noCrew", noCrew, UiKit.Tone.BAD, false);
        chip("Toe.count.fatigued", fatigued, UiKit.Tone.WARN, false);
    }

    private void chip(String key, int count, UiKit.Tone tone, boolean always) {
        if (count > 0 || always) {
            if (strength.getCells().size == CHIPS_A_ROW) {
                strength.row().padTop(space("xs"));
            }
            strength.add(ui.chip(getFormattedText(key, count), tone, true)).left().padRight(space("xs"));
        }
    }

    private void pinAll(ToeLayout layout) {
        // Pins carry their fade over a fold, so what stays on the table doesn't blink
        Map<Integer, Float> blockFade = new HashMap<>();
        Map<UUID, Float> slotFade = new HashMap<>();
        for (Pin pin : pinned) {
            if (pin.block() != null) {
                blockFade.put(pin.block().key(), pin.actor().getColor().a);
            } else if (pin.slot() != null) {
                slotFade.put(pin.slot().card().id(), pin.actor().getColor().a);
            }
        }
        pins.clearChildren();
        pinned.clear();
        ghosts.clear();
        for (Vacancy vacancy : layout.vacancies()) {
            if (vacancy.slot().kind() != Kind.ANY) {
                Actor icon = ui.icon(kindIcon(vacancy.slot().kind()), 24, SLOT_VACANT);
                icon.setVisible(false);
                icon.getColor().a = 0;
                pins.addActor(icon);
                ghosts.add(new Ghost(icon, vacancy));
            }
        }
        for (Block block : layout.blocks()) {
            List<Card> lit = block.units().stream().filter(view::lit).toList();
            int ready = 0;
            for (Card card : lit) {
                ready += card.ready() ? 1 : 0;
            }
            String count = lit.isEmpty() ? null : ready + "/" + lit.size();
            Color countTone = srgb(ToeView.readiness(lit.isEmpty() ? 1 : ready / (float) lit.size()));
            Pin pin;
            if (block.card()) {
                Table plate = plate(LABEL_FORMATION);
                Label name = ui.label(UiTheme.upper(block.name()), "hud-button", 12, LABEL_INK);
                name.setEllipsis(true);
                float nameWide = name.getPrefWidth();
                Cell<Label> nameCell = plate.add(name).padLeft(7).padRight(8).padTop(3).padBottom(3).left();
                plate.pack();
                pins.addActor(plate);
                int[] slots = slotTally(block);
                Actor badges = badges(lit, count, countTone, BADGES, slots);
                Actor compact = badges(lit, count, countTone, 1, null);
                pins.addActor(badges);
                pins.addActor(compact);
                pin = new Pin(plate, block, null, nameCell, nameWide, plate.getWidth() - nameWide, badges, compact);
            } else {
                Table chip = pin(block.name(), LABEL_FORMATION, count, countTone);
                Label name = chip.findActor(PIN_NAME);
                float nameWide = name.getPrefWidth();
                pin = new Pin(chip, block, null, chip.getCell(name), nameWide, chip.getWidth() - nameWide, null, null);
            }
            pin.actor().getColor().a = blockFade.getOrDefault(block.key(), 0f);
            if (pin.badges() != null && pin.compact() != null) {
                pin.badges().getColor().a = pin.actor().getColor().a;
                pin.compact().getColor().a = pin.actor().getColor().a;
            }
            pinned.add(pin);
        }
        for (Slot slot : layout.slots()) {
            Pin pin = new Pin(pin(slot.card().name(), srgb(ToeView.state(slot.card())), null, null), null, slot);
            pin.actor().getColor().a = slotFade.getOrDefault(slot.card().id(), 0f);
            pinned.add(pin);
        }
    }

    /**
     * How many slots are vacant and how many units fit none in the folded formation {@code card}: everything under it,
     * or for a formation's own units folded apart, theirs and their formation's slots.
     */
    private int[] slotTally(Block card) {
        if (card.group() != null) {
            return ToeSlots.tally(card.group());
        }
        ToeMoves rules = view.rules();
        ToeSnapshot.Group group = rules == null ? null : rules.group(ToeMoves.target(card));
        return group == null ? new int[2] : ToeSlots.own(group);
    }

    /**
     * A folded formation's state in a row: how many of its units are ready ({@code ready}, in {@code readyTone}),
     * then what's keeping the rest out of the field, the worst first: each trouble's mark and how many have it, past
     * {@code most} how many more troubles there are; a check if they're all fit to fight; then, if {@code slots} are
     * given, how many slots are vacant and how many units fit none.
     */
    private Actor badges(List<Card> units, @Nullable String ready, Color readyTone, int most, @Nullable int[] slots) {
        Table row = new Table();
        if (ready != null) {
            row.add(ui.label(ready, "hud-button", 12, readyTone)).padRight(9);
        }
        int[] counts = Issue.tally(units);
        int shown = 0;
        int more = 0;
        for (Issue issue : Issue.values()) {
            int count = counts[issue.ordinal()];
            if (count == 0) {
                continue;
            }
            if (shown == most) {
                more++;
                continue;
            }
            Color tone = issueTone(issue);
            row.add(ui.icon(issueIcon(issue), 13, tone)).size(13);
            row.add(ui.label(Integer.toString(count), "hud-button", 12, tone)).padLeft(2).padRight(7);
            shown++;
        }
        if (more > 0) {
            row.add(ui.label("+" + more, "hud-button", 12, LABEL_INK));
        }
        if (shown == 0 && !units.isEmpty()) {
            row.add(ui.icon("check-circle", 13, ALL_READY)).size(13).padRight(7);
        }
        if (slots != null && slots[0] > 0) {
            row.add(ui.icon("radio-unchecked", 13, SLOT_VACANT)).size(13);
            row.add(ui.label(Integer.toString(slots[0]), "hud-button", 12, SLOT_VACANT)).padLeft(2).padRight(7);
        }
        if (slots != null && slots[1] > 0) {
            row.add(ui.icon("rules", 13, SLOT_MISFIT)).size(13);
            row.add(ui.label(Integer.toString(slots[1]), "hud-button", 12, SLOT_MISFIT)).padLeft(2);
        }
        row.pack();
        row.setVisible(false);
        return row;
    }

    private static String issueIcon(Issue issue) {
        return switch (issue) {
            case OUT_OF_ACTION -> "dangerous";
            case DAMAGED -> "warn";
            case REPAIR -> "build";
            case NO_CREW -> "person-off";
            case FATIGUED -> "bedtime";
            case MOTHBALLED -> "inventory";
            case TRANSIT -> "rocket";
        };
    }

    private static String kindIcon(Kind kind) {
        return switch (kind) {
            case ANY, MEK, PROTOMEK -> "unit";
            case VEHICLE -> "car";
            case VTOL, AEROSPACE -> "flight";
            case NAVAL -> "anchor";
            case INFANTRY -> "walk";
            case BATTLE_ARMOR -> "shield-person";
            case LARGE_CRAFT -> "rocket";
        };
    }

    private static Color issueTone(Issue issue) {
        return switch (issue) {
            case OUT_OF_ACTION -> ISSUE_BAD;
            case DAMAGED, REPAIR -> ISSUE_WARN;
            case NO_CREW -> ISSUE_CREW;
            case FATIGUED -> ISSUE_TIRED;
            case MOTHBALLED -> ISSUE_LAID_UP;
            case TRANSIT -> ISSUE_AWAY;
        };
    }

    /** An opaque plate so a pin reads over the table, its edge bar in {@code tone}. */
    private Table plate(Color tone) {
        Table chip = new Table();
        chip.setBackground(ui.skin.newDrawable("white", LABEL_PLATE));
        chip.add(new Image(ui.skin.newDrawable("white", tone))).width(3).growY();
        chip.setVisible(false);
        return chip;
    }

    /**
     * A pin: an opaque plate so it reads over the table, its edge bar in {@code tone}, its text in upper case, and
     * after it {@code count}, if any, in {@code countTone}.
     */
    private Table pin(String text, Color tone, @Nullable String count, @Nullable Color countTone) {
        Table chip = plate(tone);
        Label name = ui.label(UiTheme.upper(text), "hud-button", 12, LABEL_INK);
        name.setEllipsis(true);
        name.setName(PIN_NAME);
        chip.add(name).padLeft(7).padRight(8).padTop(3).padBottom(3).left();
        if (count != null && countTone != null) {
            chip.add(ui.label(count, "hud-button", 12, countTone)).padRight(8).padTop(3).padBottom(3);
        }
        chip.pack();
        pins.addActor(chip);
        return chip;
    }

    private static Color srgb(float[] linear) {
        return new Color((float) Math.pow(linear[0], 1 / 2.2), (float) Math.pow(linear[1], 1 / 2.2),
              (float) Math.pow(linear[2], 1 / 2.2), 1);
    }

    /** Opens the slot editor on the formation of {@code block}, its folded card or its own units folded apart. */
    private void edit(Block block) {
        ToeMoves rules = view.rules();
        ToeSnapshot.Group group = rules == null ? null : rules.group(ToeMoves.target(block));
        if (group == null || ui == null) {
            return;
        }
        view.cancelDrag();
        editing = group;
        draft.clear();
        draft.addAll(group.slots());
        buildEditor();
        editorLayer.setVisible(true);
        editorLayer.toFront();
    }

    /** Whether the slot editor is up, and on which formation; for the demo. */
    @Nullable
    ToeSnapshot.Group editing() {
        return editing;
    }

    /** Whether the slot editor or the picker is up over the table, which then takes no input of its own. */
    private boolean modal() {
        return editing != null || filling != null;
    }

    /** Closes the slot editor or the picker, whichever is up. */
    private void closeEditor() {
        editing = null;
        filling = null;
        draft.clear();
        if (editorLayer != null) {
            editorLayer.setVisible(false);
            editorLayer.clearChildren();
        }
    }

    /**
     * The slot editor: a row for each slot the formation is authorized, its kind and weight class each a selector, and
     * a button that strikes it; under them, the usual strengths to shape it to, then adding one, taking the units it
     * has now as its slots, and clearing them; then applying it.
     */
    private void buildEditor() {
        ToeSnapshot.Group group = Objects.requireNonNull(editing);
        editorLayer.clearChildren();
        Table dialog = ui.dialog(getFormattedText("Toe.slot.title", group.name()), this::closeEditor, true);
        dialog.defaults().left();
        Label lead = ui.label(getText("Toe.slot.lead"), "hud-body", 13, UiTheme.tokenColor("text.secondary"));
        lead.setWrap(true);
        dialog.add(lead).width(EDITOR_WIDTH).row();
        Table rows = new Table();
        rows.defaults().left().padTop(space("xs"));
        if (draft.isEmpty()) {
            rows.add(ui.label(getText("Toe.slot.none"), "hud-body", 13, UiTheme.tokenColor("text.muted")));
        }
        List<String> kinds = Arrays.stream(Kind.values()).map(ToeTable::kindName).toList();
        List<String> weights = Arrays.stream(Weight.values()).map(ToeTable::weightName).toList();
        for (int i = 0; i < draft.size(); i++) {
            int index = i;
            FormationSlot slot = draft.get(i);
            rows.add(ui.icon(kindIcon(slot.kind()), 16, SLOT_VACANT)).size(16).padRight(space("sm"));
            UiStepper kind = ui.selector(kinds, slot.kind().ordinal());
            UiKit.onChange(kind, () -> {
                draft.set(index, new FormationSlot(Kind.values()[kind.value()], draft.get(index).weight()));
                buildEditorLater();
            });
            rows.add(kind).padRight(space("md"));
            UiStepper weight = ui.selector(weights, slot.weight().ordinal());
            UiKit.onChange(weight, () -> draft.set(index,
                  new FormationSlot(draft.get(index).kind(), Weight.values()[weight.value()])));
            rows.add(weight).padRight(space("md"));
            UiButton strike = ui.button("button-icon" + UiTheme.COMPACT, "delete", null, null);
            UiKit.onChange(strike, () -> {
                draft.remove(index);
                buildEditorLater();
            });
            rows.add(strike).row();
        }
        dialog.add(rows).padTop(space("md")).row();
        Table shapes = new Table();
        shapes.add(ui.label(getText("Toe.slot.template"), "hud-body", 13, UiTheme.tokenColor("text.muted")))
              .padRight(space("md"));
        for (ToeSlots.Template template : ToeSlots.Template.values()) {
            UiButton shape = ui.button("button-secondary" + UiTheme.COMPACT, null,
                  getFormattedText("Toe.slot.template." + template.name(), template.size), null);
            UiKit.onChange(shape, () -> shape(template));
            shapes.add(shape).padRight(space("sm"));
        }
        dialog.add(shapes).padTop(space("lg")).row();
        Table tools = new Table();
        UiButton add = ui.button("button-secondary" + UiTheme.COMPACT, "add", getText("Toe.slot.add"), null);
        add.setDisabled(draft.size() >= MOST_SLOTS);
        UiKit.onChange(add, () -> {
            if (draft.size() < MOST_SLOTS) {
                draft.add(draft.isEmpty() ? new FormationSlot(Kind.MEK, Weight.ANY) : draft.get(draft.size() - 1));
                buildEditorLater();
            }
        });
        tools.add(add).padRight(space("sm"));
        UiButton match = ui.button("button-secondary" + UiTheme.COMPACT, "check-circle", getText("Toe.slot.match"),
              null);
        UiKit.onChange(match, () -> {
            draft.clear();
            group.units().stream().limit(MOST_SLOTS).forEach(card -> draft.add(card.fit()));
            buildEditorLater();
        });
        tools.add(match).padRight(space("sm"));
        UiButton clear = ui.button("button-quiet" + UiTheme.COMPACT, "delete", getText("Toe.slot.clear"), null);
        UiKit.onChange(clear, () -> {
            draft.clear();
            buildEditorLater();
        });
        tools.add(clear);
        dialog.add(tools).padTop(space("sm")).row();
        Table foot = ui.footer(dialog, true);
        foot.right();
        UiButton cancel = ui.button("button-quiet", null, getText("Toe.slot.cancel"), null);
        UiKit.onChange(cancel, this::closeEditor);
        foot.add(cancel).padRight(space("sm"));
        UiButton apply = ui.button("button-primary", null, getText("Toe.slot.apply"), null);
        UiKit.onChange(apply, this::applyEditor);
        foot.add(apply);
        editorLayer.add(dialog);
    }

    /** Brings the slots being edited to {@code template}'s strength, as its button in the editor does. */
    void shape(ToeSlots.Template template) {
        ToeSnapshot.Group group = editing;
        if (group == null) {
            return;
        }
        List<FormationSlot> shaped = ToeSlots.template(template, draft, group.units());
        draft.clear();
        draft.addAll(shaped);
        buildEditorLater();
    }

    /** Builds the editor again once the event that changed it is done with its buttons. */
    private void buildEditorLater() {
        Gdx.app.postRunnable(() -> {
            if (editing != null) {
                buildEditor();
            }
        });
    }

    private void applyEditor() {
        ToeSnapshot.Group group = editing;
        if (group == null) {
            return;
        }
        authorize.accept(group.id(), List.copyOf(draft));
        notice(getFormattedText(draft.isEmpty() ? "Toe.slot.cleared" : "Toe.slot.applied", group.name(),
              draft.size()));
        closeEditor();
    }

    /**
     * Opens the picker on {@code vacancy}: the units that fit it, narrowed to the place the table is narrowed to, or
     * else to where its formation is if any there fit.
     */
    private void fill(Vacancy vacancy) {
        ToeMoves rules = view.rules();
        ToeSnapshot.Group group = rules == null ? null : rules.group(ToeMoves.target(vacancy.block()));
        if (group == null || snapshot == null || ui == null) {
            return;
        }
        if (group.deployed()) {
            notice(getFormattedText("Toe.fill.locked", group.name()));
            return;
        }
        view.cancelDrag();
        filling = new Filling(group, vacancy.slot(), ToeFill.candidates(snapshot, group, vacancy.slot()));
        fillPlace = placeIndex;
        if (fillPlace == 0 && places.size() > 1) {
            // Places run all, the main force, then each base, so a unit's place is one short of its tab
            int home = ToeFill.place(snapshot, group) + 1;
            if (home < places.size() && !fillable(home).isEmpty()) {
                fillPlace = home;
            }
        }
        buildPicker();
        editorLayer.setVisible(true);
        editorLayer.toFront();
    }

    /** What the picker is filling, if it's up; for the demo. */
    @Nullable
    Filling filling() {
        return filling;
    }

    /** The units the picker offers at the place of tab {@code index}. */
    private List<ToeFill.Candidate> fillable(int index) {
        Filling at = Objects.requireNonNull(filling);
        Predicate<Card> units = index < places.size() ? places.get(index).units() : null;
        return units == null ? at.candidates()
              : at.candidates().stream().filter(candidate -> units.test(candidate.card())).toList();
    }

    /**
     * The picker: a tab for each place, counting the units there that fit the slot, over a list of them, each saying
     * what it is, what shape it's in, where it's taken from and what taking it costs; a click on one moves it in.
     */
    private void buildPicker() {
        Filling at = Objects.requireNonNull(filling);
        editorLayer.clearChildren();
        Table dialog = ui.dialog(getFormattedText("Toe.fill.title", slotName(at.slot())), this::closeEditor, true);
        dialog.defaults().left();
        Label lead = ui.label(getFormattedText("Toe.fill.lead", at.group().name()), "hud-body", 13,
              UiTheme.tokenColor("text.secondary"));
        lead.setWrap(true);
        dialog.add(lead).width(PICKER_WIDTH).row();
        if (places.size() > 1) {
            String[] names = new String[places.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = getFormattedText("Toe.fill.place", places.get(i).name(), fillable(i).size());
            }
            UiKit.Segmented choices = ui.segmented("hud-tab", false, names);
            for (int i = 0; i < choices.buttons.size(); i++) {
                int picked = i;
                UiKit.onChange(choices.buttons.get(i), () -> {
                    if (picked != fillPlace) {
                        fillPlace = picked;
                        buildPickerLater();
                    }
                });
            }
            choices.select(fillPlace);
            dialog.add(choices).padTop(space("md")).row();
        }
        Table list = new Table();
        list.top();
        List<ToeFill.Candidate> offered = fillable(fillPlace);
        if (offered.isEmpty()) {
            ui.empty(list, fillPlace == 0 ? getText("Toe.fill.none")
                  : getFormattedText("Toe.fill.noneHere", places.get(fillPlace).name()), true);
        }
        for (ToeFill.Candidate candidate : offered) {
            list.add(candidateRow(candidate)).growX().row();
        }
        dialog.add(ui.scrollList(list, true)).width(PICKER_WIDTH).height(PICKER_HEIGHT).padTop(space("sm")).row();
        Table foot = ui.footer(dialog, true);
        foot.right();
        UiButton cancel = ui.button("button-quiet", null, getText("Toe.fill.cancel"), null);
        UiKit.onChange(cancel, this::closeEditor);
        foot.add(cancel);
        editorLayer.add(dialog);
    }

    private void buildPickerLater() {
        Gdx.app.postRunnable(() -> {
            if (filling != null) {
                buildPicker();
            }
        });
    }

    /**
     * A unit the picker offers: its kind, name and crew; its state and where it is; the formation it'd leave and what
     * that costs; and, at the end, whether it's ready or what's keeping it back, in that trouble's tone.
     */
    private Button candidateRow(ToeFill.Candidate candidate) {
        Card card = candidate.card();
        ToeSnapshot shown = Objects.requireNonNull(snapshot);
        Button row = new Button(ui.skin.get(UiTheme.TOKEN_ROW, TextButton.TextButtonStyle.class));
        row.left();
        row.add(ui.icon(kindIcon(card.fit().kind()), 18, SLOT_VACANT)).size(18).top().padTop(2)
              .padRight(space("md"));
        Table about = new Table();
        about.top().left();
        about.defaults().left();
        Table title = new Table();
        title.add(ui.label(card.name(), "hud-body", 14, UiTheme.tokenColor("text.primary")));
        title.add(ui.label(crewText(card), "hud-body", 12, UiTheme.tokenColor("text.secondary")))
              .padLeft(space("sm"));
        about.add(title).row();
        about.add(ui.label(detail(card, shown), "hud-body", 12, UiTheme.tokenColor("text.secondary"))).row();
        Table from = new Table();
        from.add(ui.label(candidate.from() == null ? getText("Toe.fill.free")
              : getFormattedText("Toe.fill.from", candidate.from().name()), "hud-body", 12,
              UiTheme.tokenColor("text.muted")));
        List<String> costs = new ArrayList<>();
        if (candidate.leavesVacancy()) {
            costs.add(getFormattedText("Toe.fill.leaves", Objects.requireNonNull(candidate.from()).name()));
        }
        if (candidate.elsewhere()) {
            costs.add(getFormattedText("Toe.fill.elsewhere", placeName(card, shown)));
        }
        if (!costs.isEmpty()) {
            from.add(ui.label(String.join("  \u00b7  ", costs), "hud-body", 12, ISSUE_WARN)).padLeft(space("sm"));
        }
        about.add(from).row();
        row.add(about).growX().left();
        Issue trouble = Issue.first(card);
        Table status = new Table();
        if (trouble == null) {
            status.add(ui.icon("check", 14, ALL_READY)).size(14).padRight(space("xs"));
            status.add(ui.label(getText("Toe.fill.ready"), "hud-body", 12, ALL_READY));
        } else {
            Color tone = issueTone(trouble);
            status.add(ui.icon(issueIcon(trouble), 14, tone)).size(14).padRight(space("xs"));
            status.add(ui.label(getText("Toe.fill.status." + trouble.name()), "hud-body", 12, tone));
        }
        row.add(status).right().top().padLeft(space("md"));
        UiKit.onChange(row, () -> fillWith(candidate));
        return row;
    }

    /** Moves {@code candidate} into the slot the picker's filling, as a drop there would, which a Ctrl+Z puts back. */
    void fillWith(ToeFill.Candidate candidate) {
        Filling at = filling;
        if (at == null) {
            return;
        }
        closeEditor();
        dropped(Load.of(List.of(candidate.card())), at.group().id());
    }

    private static String placeName(Card card, ToeSnapshot shown) {
        return card.place() == ToeSnapshot.MAIN_FORCE ? getText("Toe.filter.main")
              : shown.bases().get(card.place() - 1);
    }

    /** Who crews {@code card}, in words. */
    private static String crewText(Card card) {
        return switch (card.crew()) {
            case NONE -> getText("Toe.crew.none");
            case NOT_NEEDED -> getText("Toe.crew.notNeeded");
            case PARTIAL -> getFormattedText("Toe.crew.partial", card.crewName());
            case FULL -> card.crewName();
        };
    }

    /** What {@code slot} calls for in words: "Light Mek", "Any Vehicle", "Any unit". */
    static String slotName(FormationSlot slot) {
        if (slot.weight() == Weight.ANY) {
            return slot.kind() == Kind.ANY ? getText("Toe.slot.anyUnit")
                  : getFormattedText("Toe.slot.anyOf", kindName(slot.kind()));
        }
        return getFormattedText("Toe.slot.named", weightName(slot.weight()),
              slot.kind() == Kind.ANY ? getText("Toe.slot.unit") : kindName(slot.kind()));
    }

    private static String kindName(Kind kind) {
        return getText("Toe.slot.kind." + kind.name());
    }

    private static String weightName(Weight weight) {
        return getText("Toe.slot.weight." + weight.name());
    }

    private void picked(@Nullable Slot slot) {
        // The pick shows in the hologram; the actions on it come with the selection bar
    }

    InputProcessor input() {
        if (input == null) {
            input = newInput();
        }
        return input;
    }

    private InputProcessor newInput() {
        InputProcessor keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (!shown) {
                    return false;
                }
                if (modal()) {
                    if (keycode == Input.Keys.ESCAPE) {
                        closeEditor();
                        return true;
                    }
                    return false;
                }
                switch (keycode) {
                    case Input.Keys.ESCAPE -> {
                        if (!view.cancelDrag()) {
                            leave.run();
                        }
                    }
                    case Input.Keys.Z -> {
                        if (!Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                              && !Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
                            return false;
                        }
                        undo();
                    }
                    case Input.Keys.L -> cycleLabels();
                    case Input.Keys.HOME -> frameShown(false, false);
                    case Input.Keys.EQUALS, Input.Keys.PLUS -> view.zoom(-3);
                    case Input.Keys.MINUS -> view.zoom(3);
                    case Input.Keys.LEFT_BRACKET -> narrow((placeIndex + places.size() - 1) % places.size());
                    case Input.Keys.RIGHT_BRACKET -> narrow((placeIndex + 1) % places.size());
                    default -> {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                pointerX = screenX;
                pointerY = screenY;
                return false;
            }
        };
        InputProcessor controls = new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                return shown && stage.touchDown(screenX, screenY, pointer, button);
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                return shown && stage.touchDragged(screenX, screenY, pointer);
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                return shown && stage.touchUp(screenX, screenY, pointer, button);
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                return shown && stage.mouseMoved(screenX, screenY);
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                return shown && stage.scrolled(amountX, amountY);
            }
        };
        InputProcessor table = new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                return shown && !modal() && view.input().touchDown(screenX, screenY, pointer, button);
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                return shown && !modal() && view.input().touchDragged(screenX, screenY, pointer);
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                return shown && !modal() && view.input().touchUp(screenX, screenY, pointer, button);
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                return shown && !modal() && view.input().mouseMoved(screenX, screenY);
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                return shown && !modal() && view.input().scrolled(amountX, amountY);
            }
        };
        return new InputMultiplexer(keys, controls, table);
    }

    void resize(int width, int height) {
        if (stage == null || width <= 0 || height <= 0) {
            return;
        }
        ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1 / scale);
        stage.getViewport().update(width, height, true);
    }

    /** Draws the table and its controls over it. */
    void render(float delta) {
        if (!shown || stage == null) {
            return;
        }
        if (folds != null && folds.update(view.wantCellPixels())) {
            relayout(false);
        }
        view.render();
        placePins();
        placeHoverCard();
        placePlate();
        stage.getViewport().apply(true);
        stage.act(delta);
        stage.draw();
    }

    /** Shows the next of {@link Labels}' sets of labels. */
    private void cycleLabels() {
        labels = labels.next();
        if (labelsButton != null) {
            labelsButton.setText(labels.text());
        }
    }

    private void placePins() {
        float cell = view.cellPixels() / scale;
        float fade = Gdx.graphics.getDeltaTime() * 4;
        Slot hovered = view.hovered();
        Slot selected = view.selected();
        int taken = 0;
        for (Pin pin : pinned) {
            Actor actor = pin.actor();
            if (pin.block() != null && pin.block().card()) {
                placeCard(pin, view.posed(pin.block()), cell, fade);
                continue;
            }
            boolean show;
            float anchor;
            boolean above = false;
            if (pin.block() != null) {
                Block block = view.posed(pin.block());
                // Zoomed out, a head is too short for its label, which would cover the units under it
                boolean cramped = cell * block.head() < actor.getHeight() * 0.6f;
                float left = ToeView.iconInset(block) + block.head() * 0.9f;
                float nameRoom = Math.min(pin.nameWide(),
                      (block.width() - left - ToeView.iconInset(block)) * cell - pin.rest());
                show = labels != Labels.NONE && nameRoom >= NAME_LEAST
                      && (block.units().isEmpty() || blockLit(block)) && (!cramped || block.level() == 0);
                if (show) {
                    fit(pin, nameRoom);
                }
                at.set(block.x() + left, view.top(block.level()),
                      cramped ? block.z() : block.z() + block.head() / 2);
                anchor = 0;
                above = cramped;
            } else {
                Slot slot = view.posed(pin.slot());
                boolean pointed = pin.slot() == hovered || pin.slot() == selected;
                show = pointed || labels == Labels.ALL && cell >= UNIT_PINS_FROM && view.lit(slot.card());
                at.set(slot.x(), view.top(slot.block().level()), slot.z() + ToeLayout.CELL * 0.42f);
                anchor = 0.5f;
            }
            if (!show) {
                actor.setVisible(false);
                actor.getColor().a = 0;
                continue;
            }
            view.project(at);
            if (at.z > 1) {
                actor.setVisible(false);
                continue;
            }
            actor.setVisible(true);
            actor.getColor().a = Math.min(1, actor.getColor().a + fade);
            float x = at.x / scale - actor.getWidth() * anchor;
            float y = at.y / scale - (above ? -4 : anchor == 0 ? actor.getHeight() / 2 : actor.getHeight());
            if (pin.block() != null && crowded(x, y, actor.getWidth(), actor.getHeight(), taken)) {
                actor.setVisible(false);
                actor.getColor().a = 0;
                continue;
            }
            if (pin.block() != null) {
                taken++;
            }
            actor.setPosition(x, y, Align.bottomLeft);
        }
        placeGhosts(cell, fade);
    }

    /** Puts each vacant slot's mark of the kind it calls for over its ring, while cells are big enough for it. */
    private void placeGhosts(float cell, float fade) {
        boolean show = cell >= GHOST_ICONS_FROM;
        float size = Math.min(28, cell * 0.3f);
        for (Ghost ghost : ghosts) {
            Block block = view.posed(ghost.vacancy().block());
            at.set(block.x() + ghost.vacancy().dx(), view.top(block.level()),
                  block.z() + ghost.vacancy().dz() - ToeLayout.CELL * 0.08f);
            ghost.icon().setSize(size, size);
            place(ghost.icon(), show, size / 2, size / 2, fade);
            ghost.icon().getColor().a = Math.min(ghost.icon().getColor().a, 0.75f);
        }
    }

    /**
     * Whether a formation label at {@code x, y} of {@code width, height} would cover one of the first {@code count}
     * placed, which win as outer formations come first; records it as the next one when it wouldn't.
     */
    private boolean crowded(float x, float y, float width, float height, int count) {
        Rectangle label = labelProbe.set(x, y, width, height);
        for (int k = 0; k < count; k++) {
            if (placedLabels.get(k).overlaps(label)) {
                return true;
            }
        }
        if (count == placedLabels.size()) {
            placedLabels.add(new Rectangle(label));
        } else {
            placedLabels.get(count).set(label);
        }
        return false;
    }

    private boolean blockLit(Block block) {
        for (Card card : block.units()) {
            if (view.lit(card)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Places a folded formation's pins on its card: its name, cut short to fit, on the card's head, its badges in
     * the body under the readiness strip; each only while the card is big enough on screen for it.
     */
    private void placeCard(Pin pin, Block card, float cell, float fade) {
        Table plate = (Table) pin.actor();
        Actor badges = Objects.requireNonNull(pin.badges());
        Actor compact = Objects.requireNonNull(pin.compact());
        boolean labelled = labels != Labels.NONE && (card.units().isEmpty() || blockLit(card));
        float inset = ToeView.iconInset(card);
        float left = inset + (card.group() != null ? card.head() * 0.9f : 0);
        float nameRoom = Math.min(pin.nameWide(), (card.width() - left - inset) * cell - pin.rest());
        boolean named = labelled && !card.attached() && nameRoom >= 24
              && card.head() * cell >= plate.getHeight() * 0.6f;
        if (named) {
            fit(pin, nameRoom);
        }
        at.set(card.x() + left, view.top(card.level()), card.z() + card.head() / 2);
        place(plate, named, 0, plate.getHeight() / 2, fade);
        float body = (card.width() - inset * 2) * cell;
        boolean deep = card.depth() * 0.47f * cell >= badges.getHeight();
        boolean full = labelled && deep && badges.getWidth() <= body;
        boolean brief = labelled && deep && !full && compact.getWidth() <= body;
        at.set(card.x() + inset, view.top(card.level()), card.z() + card.depth() * 0.765f);
        place(badges, full, 0, badges.getHeight() / 2, fade);
        at.set(card.x() + inset, view.top(card.level()), card.z() + card.depth() * 0.765f);
        place(compact, brief, 0, compact.getHeight() / 2, fade);
    }

    /** Cuts the name on {@code pin}'s plate to {@code room} pixels across, or lets it out to its whole length. */
    private static void fit(Pin pin, float room) {
        Table plate = (Table) pin.actor();
        if (Math.abs(plate.getWidth() - pin.rest() - room) > 0.5f) {
            Objects.requireNonNull(pin.name()).width(room);
            plate.invalidate();
            plate.pack();
        }
    }

    /**
     * Shows {@code actor}, fading in, with the point {@code at} on the table {@code dx}, {@code dy} in from its
     * bottom left; or hides it.
     */
    private void place(Actor actor, boolean show, float dx, float dy, float fade) {
        if (show) {
            view.project(at);
            show = at.z <= 1;
        }
        if (!show) {
            actor.setVisible(false);
            actor.getColor().a = 0;
            return;
        }
        actor.setVisible(true);
        actor.getColor().a = Math.min(1, actor.getColor().a + fade);
        actor.setPosition(at.x / scale - dx, at.y / scale - dy, Align.bottomLeft);
    }

    private void placeHoverCard() {
        Slot slot = view.hovered();
        Block card = view.hoveredCard();
        Vacancy vacancy = view.hoveredVacancy();
        if (snapshot == null || modal() || slot == null && card == null && vacancy == null) {
            hoverCard.setVisible(false);
            return;
        }
        if (vacancy != null) {
            hoverName.setText(getFormattedText("Toe.slot.vacant", slotName(vacancy.slot())));
            hoverCrew.setText(vacancy.block().name());
            hoverDetail.setText(getText("Toe.slot.vacantHint"));
        } else if (slot == null) {
            List<Card> lit = card.units().stream().filter(view::lit).toList();
            hoverName.setText(card.name());
            hoverCrew.setText(getFormattedText("Toe.ready", lit.stream().filter(Card::ready).count(), lit.size()));
            int[] slots = slotTally(card);
            String troubles = troubles(lit);
            if (slots[0] > 0) {
                troubles += "  \u00b7  " + getFormattedText("Toe.slot.vacantCount", slots[0]);
            }
            if (slots[1] > 0) {
                troubles += "  \u00b7  " + getFormattedText("Toe.slot.misfitCount", slots[1]);
            }
            hoverDetail.setText(troubles);
        } else {
            Card unit = slot.card();
            hoverName.setText(unit.name());
            hoverCrew.setText(crewText(unit));
            String detail = detail(unit, snapshot);
            hoverDetail.setText(view.misfit(unit) ? detail + "  \u00b7  " + getText("Toe.slot.misfit") : detail);
        }
        hoverCard.pack();
        float x = pointerX / scale + 18;
        float y = (Gdx.graphics.getHeight() - pointerY) / scale - 18 - hoverCard.getHeight();
        hoverCard.setPosition(Math.min(x, stage.getWidth() - hoverCard.getWidth()), Math.max(0, y));
        hoverCard.setVisible(true);
    }

    /** What's keeping {@code units} out of the field, in words, the worst first. */
    static String troubles(List<Card> units) {
        int[] counts = Issue.tally(units);
        List<String> parts = new ArrayList<>();
        for (Issue issue : Issue.values()) {
            if (counts[issue.ordinal()] > 0) {
                parts.add(getFormattedText("Toe.issue." + issue.name(), counts[issue.ordinal()]));
            }
        }
        return parts.isEmpty() ? getText("Toe.issue.none") : String.join("  \u00b7  ", parts);
    }

    /** A unit's state in a line: its tonnage, condition, the work on it, and where it is. */
    static String detail(Card card, ToeSnapshot shown) {
        List<String> parts = new ArrayList<>();
        parts.add(getFormattedText("Toe.tons", Math.round(card.tons())));
        parts.add(getText("Toe.condition." + card.condition().name()));
        if (card.work() != Work.NONE) {
            parts.add(getText("Toe.work." + card.work().name()));
        }
        if (card.fatigued()) {
            parts.add(getText("Toe.crew.fatigued"));
        }
        if (card.deployed()) {
            parts.add(getText("Toe.deployed"));
        }
        if (card.transit()) {
            parts.add(getText("Toe.transit"));
        }
        parts.add(placeName(card, shown));
        return String.join("  \u00b7  ", parts);
    }

    /** Where {@code slot}'s unit stands on screen, in input coordinates, for the demo to point at. */
    Vector2 onScreen(Slot slot) {
        at.set(slot.x(), view.top(slot.block().level()) + 0.3f, slot.z());
        view.project(at);
        return new Vector2(at.x, Gdx.graphics.getHeight() - at.y);
    }

    /** Where the middle of {@code block} is on screen, in input coordinates, for the demo to point at. */
    Vector2 onScreen(Block block) {
        Block posed = view.posed(block);
        at.set(posed.centreX(), view.top(posed.level()), posed.centreZ());
        view.project(at);
        return new Vector2(at.x, Gdx.graphics.getHeight() - at.y);
    }

    /** Where the middle of {@code vacancy} is on screen, in input coordinates, for the demo to point at. */
    Vector2 onScreen(Vacancy vacancy) {
        Block posed = view.posed(vacancy.block());
        at.set(posed.x() + vacancy.dx(), view.top(posed.level()), posed.z() + vacancy.dz());
        view.project(at);
        return new Vector2(at.x, Gdx.graphics.getHeight() - at.y);
    }

    /** The slots on the table nobody fills. */
    List<Vacancy> vacancies() {
        return layout == null ? List.of() : layout.vacancies();
    }

    /** The folded formations on the table. */
    List<Block> cards() {
        return layout == null ? List.of() : layout.blocks().stream().filter(Block::card).toList();
    }

    /** Every block on the table, folded or not. */
    List<Block> blocks() {
        return layout == null ? List.of() : layout.blocks();
    }

    /** Where {@code card} stands on the table; null if it's not on it. */
    @Nullable
    Slot slotOf(Card card) {
        if (layout == null) {
            return null;
        }
        for (Slot slot : layout.slots()) {
            if (slot.card() != null && slot.card().id().equals(card.id())) {
                return slot;
            }
        }
        return null;
    }

    ToeView view() {
        return view;
    }

    @Override
    public void dispose() {
        view.dispose();
        if (stage != null) {
            stage.dispose();
        }
    }

    private static float space(String token) {
        return DesignTokens.shipped().space().get(token);
    }
}

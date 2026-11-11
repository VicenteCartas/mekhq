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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
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
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.GUIPreferences;
import megamek.client.ui.clientGUI.boardview.gpu.GpuBoardInspection;
import megamek.client.ui.design.DesignTokens;
import megamek.client.ui.gdx.UiButton;
import megamek.client.ui.gdx.UiCountBadge;
import megamek.client.ui.gdx.UiKit;
import megamek.client.ui.gdx.UiTheme;
import megamek.common.loaders.MapSettings;
import megamek.common.planetaryConditions.PlanetaryConditions;
import megamek.server.ServerBoardHelper;
import mekhq.gui.gdx.hub.WarTableSnapshot.Beacon;
import mekhq.gui.gdx.hub.WarTableSnapshot.Hex;
import mekhq.gui.gdx.hub.WarTableSnapshot.Kind;
import mekhq.gui.gdx.hub.WarTableSnapshot.Mark;
import mekhq.gui.gdx.hub.WarTableSnapshot.Reserve;
import mekhq.gui.gdx.hub.WarTableSnapshot.Sector;
import mekhq.gui.gdx.hub.WarTableSnapshot.Site;
import mekhq.gui.gdx.hub.WarTableSnapshot.Token;

/**
 * The war room: an operation's sectors raised on the sand table ({@link WarTableView}), with the operation's standing
 * over it, a tab per sector, pins naming what stands on the table, and a card for the hex picked. A scenario's card
 * leads into the battle: the camera dives onto its hex and the board it would be fought on comes up in the game's own
 * renderer ({@link GpuBoardInspection}), its briefing beside it. Forces are moved by hand: one dragged from the reserve
 * drawer onto an open hex is deployed there, and one dragged off the table onto the drawer is recalled, each once
 * confirmed. The drawer folds to a tab at the table's edge; open, it sorts the reserve by what the forces field and by
 * the TO&amp;E forces they belong to, and finds one by name;
 * the campaign carries the {@link Orders} out and the table is {@link #refresh refreshed} with the outcome.
 *
 * <p>GL thread throughout. The screen showing it hands it its input ({@link #input}) and draws it in place of its own
 * view while it's {@link #shown}.</p>
 */
final class WarTable implements Disposable {
    /** How long the camera takes to dive onto a scenario's hex, or to rise back off it, in seconds. */
    private static final float DIVE_SECONDS = 0.85f;
    private static final float CARD_WIDTH = 340;
    private static final float BRIEFING_WIDTH = 360;
    private static final float TILE_ART = 44;
    private static final float TILE_WIDTH = 80;
    private static final float DRAG_ART = 56;
    private static final int DRAWER_ROW = 3;
    private static final float DRAWER_WIDTH = 296;
    /** How long the reserve's drawer takes to slide open or shut, in seconds. */
    private static final float DRAWER_SECONDS = 0.3f;
    private static final float CLICK_SLOP = 6;
    /** Map labels: a near-opaque plate, bright ink, and the edge bar of a force (a friendly blue). */
    private static final Color LABEL_PLATE = new Color(0.035f, 0.047f, 0.06f, 0.9f);
    private static final Color LABEL_INK = new Color(0.93f, 0.95f, 0.97f, 1);
    private static final Color LABEL_FRIEND = new Color(0.45f, 0.72f, 0.95f, 1);

    /** What's ordered of the campaign's forces on the table, carried out on the campaign's own thread. */
    interface Orders {
        /** Deploys force {@code force} onto hex ({@code x}, {@code y}) of sector {@code sector} of {@code contract}. */
        void deploy(UUID contract, int sector, int x, int y, int force);

        /** Recalls force {@code force} off sector {@code sector} of {@code contract}. */
        void recall(UUID contract, int sector, int force);
    }

    private final Runnable leave;
    private final Orders orders;
    private final WarTableView view = new WarTableView(this::picked);
    private UiKit ui;
    private float scale = 1;
    private boolean motion = true;
    private Stage stage;
    private final Table root = new Table();
    private final Group pins = new Group();
    private final List<Pin> pinned = new ArrayList<>();
    private Table header;
    private Label operation;
    private Table standing;
    private Table tabs;
    private @Nullable UiKit.Segmented segments;
    private Table card;
    private Table hoverCard;
    private Label hoverName;
    private Label hoverDetail;
    private int pointerX;
    private int pointerY;
    private Table briefing;
    private GpuBoardInspection inspection;
    private Table inspectionFrame;
    private List<WarTableSnapshot> operations = List.of();
    private int operationIndex;
    private UiButton nextOperation;
    private Label empty;
    private @Nullable WarTableSnapshot snapshot;
    private int sectorIndex;
    private boolean shown;
    /** How far the camera has dived onto the scenario being looked into, 0 to 1, and which way it's going. */
    private float dive;
    private boolean diving;
    private @Nullable Beacon inspected;
    private Table drawer;
    private Button drawerTab;
    private UiCountBadge drawerCount;
    private UiCountBadge tabCount;
    private Label drawerCaption;
    private Table drawerKinds;
    private UiKit.SearchField drawerSearch;
    private Table drawerList;
    private final List<Texture> drawerArt = new ArrayList<>();
    private Label hint;
    /** The reserve as the drawer holds it: the sector's, but for the forces already ordered onto the table. */
    private List<Reserve> reserve = List.of();
    private @Nullable Kind drawerKind;
    private boolean drawerOpen;
    /** Whether the drawer's slid out of the way of a force dragged from it. */
    private boolean drawerAway;
    /** Whether the drawer was open before a force carried off the table opened it, to put it back as it was. */
    private boolean drawerWasOpen;
    /** How far the drawer's slid open, 0 to 1. */
    private float drawerShown;
    private final Map<Integer, Actor> tiles = new HashMap<>();
    private Image dragIcon;
    private Table askLayer;
    private Table ask;
    private @Nullable Runnable confirmed;
    private @Nullable Runnable cancelled;
    private Table notice;
    private Label noticeText;
    private VerticalGroup contacts;
    /** Each sector as the table last showed it, by contract and sector, so what's changed since plays out. */
    private final Map<String, Sector> seen = new HashMap<>();
    /** The force carried off the table towards the drawer, null for none, and where the press that took it was. */
    private @Nullable Token carriedToken;
    private int carriedFromX;
    private int carriedFromY;
    /** The hex whose card shows, -1 for none, to show again when the table's refreshed. */
    private int pickedX = -1;
    private int pickedY = -1;
    /** The forces ordered onto the table, kept out of the drawer until the table shows the order carried out. */
    private final Set<Integer> ordered = new HashSet<>();

    /** A table whose back button, or Escape, runs {@code leave}, handing what's done to its forces to {@code orders}. */
    WarTable(Runnable leave, Orders orders) {
        this.leave = leave;
        this.orders = orders;
    }

    /** Keeps a click on a panel from reaching the table under it. */
    private static final InputListener SWALLOW = new InputListener() {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            return true;
        }
    };

    /** A label pinned over a hex of the table, kept hidden until {@code from} (in {@link TimeUtils#millis}). */
    private record Pin(Actor actor, int x, int y, float lift, long from) {
    }

    /** Sets the table up on the hub's look, at {@code scale} pixels a stage unit. */
    void create(UiTheme theme, UiKit ui, float scale) {
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
        tabs = new Table();
        card = ui.panel(true);
        card.setTouchable(Touchable.enabled);
        card.addListener(SWALLOW);
        header.setTouchable(Touchable.enabled);
        header.addListener(SWALLOW);
        card.setVisible(false);
        Table top = new Table();
        top.add(header).top().left();
        top.add(tabs).expandX().top().padLeft(space("xl"));
        UiButton plot = ui.button("hud-utility", "map", getText("WarTable.plot"), null);
        UiKit.onChange(plot, () -> view.plot(!view.plotted()));
        top.add(plot).top().right().padRight(space("sm"));
        UiButton back = ui.button("hud-utility", "chevron-left", getText("WarTable.back"), null);
        UiKit.onChange(back, this::escape);
        top.add(back).top().right();
        root.add(top).growX().row();
        Table middle = new Table();
        middle.add().grow();
        middle.add(card).width(CARD_WIDTH).top().right().padTop(space("lg"));
        root.add(middle).grow().row();
        empty = ui.label(getText("WarTable.none"), "hud-body", 16, UiTheme.tokenColor("text.secondary"));
        empty.setVisible(false);
        Table emptyLayer = new Table();
        emptyLayer.setFillParent(true);
        emptyLayer.setTouchable(Touchable.disabled);
        emptyLayer.add(empty);
        stage.addActor(emptyLayer);
        stage.addActor(hintLayer());
        drawer();
        dragIcon = new Image();
        dragIcon.setScaling(Scaling.fit);
        dragIcon.setSize(DRAG_ART, DRAG_ART);
        dragIcon.setTouchable(Touchable.disabled);
        dragIcon.setVisible(false);
        stage.addActor(dragIcon);
        stage.addActor(noticeLayer());
        stage.addActor(contactLayer());
        stage.addActor(askLayer());
        stage.addActor(hoverCard());
        stage.addActor(inspectionLayer(theme));
    }

    private Table hintLayer() {
        Table layer = new Table();
        layer.setFillParent(true);
        layer.setTouchable(Touchable.disabled);
        layer.bottom().pad(space("md"));
        hint = ui.label(getText("WarTable.hint"), "hud-body", 12, UiTheme.tokenColor("text.secondary"));
        layer.add(hint);
        return layer;
    }

    /**
     * The reserve: the forces that may be put on the table, in a drawer at its left edge to drag them onto it from, and
     * to drag one off onto. Shut, it's a tab with their count; {@link #placeDrawer} slides it.
     */
    private void drawer() {
        drawer = ui.panel(true);
        drawer.top().left();
        drawer.setTouchable(Touchable.enabled);
        drawer.addListener(SWALLOW);
        drawer.setVisible(false);
        drawer.defaults().growX();
        Table head = new Table();
        head.add(ui.caption(getText("WarTable.reserve"), true)).left();
        drawerCount = ui.countBadge(0);
        head.add(drawerCount).padLeft(space("xs"));
        head.add().expandX();
        UiButton close = ui.button("hud-icon", "chevron-left", null, null);
        UiKit.onChange(close, () -> openDrawer(false));
        head.add(close);
        drawer.add(head).row();
        drawerCaption = ui.label(getText("WarTable.reserve.drag"), "hud-body", 12,
              UiTheme.tokenColor("text.secondary"));
        drawerCaption.setWrap(true);
        drawer.add(drawerCaption).padTop(space("xxs")).row();
        drawerSearch = ui.search(getText("WarTable.reserve.search"), true);
        UiKit.onChange(drawerSearch.field, this::fillDrawer);
        drawer.add(drawerSearch).padTop(space("sm")).row();
        drawerKinds = new Table();
        drawer.add(drawerKinds).left().fill(false, false).padTop(space("sm")).row();
        drawerList = new Table();
        drawerList.top().left();
        drawer.add(ui.scrollList(drawerList, true, false)).grow().padTop(space("sm"));
        stage.addActor(drawer);

        drawerTab = ui.rail(getText("WarTable.reserve"), "group");
        drawerTab.setProgrammaticChangeEvents(false);
        tabCount = ui.countBadge(0);
        drawerTab.row();
        drawerTab.add(tabCount).padTop(space("sm"));
        drawerTab.setVisible(false);
        UiKit.onChange(drawerTab, () -> openDrawer(drawerTab.isChecked()));
        stage.addActor(drawerTab);
    }

    /** Slides the reserve's drawer open, or shut to its tab. */
    void openDrawer(boolean open) {
        drawerOpen = open;
        drawerTab.setChecked(open);
        if (!open && stage.getKeyboardFocus() == drawerSearch.field) {
            stage.setKeyboardFocus(null);
        }
    }

    /** Slides the drawer towards where it's to be, open or shut, and stands its tab at its edge. */
    private void placeDrawer(float delta) {
        boolean shown = snapshot != null && inspected == null;
        boolean open = shown && drawerOpen && !drawerAway;
        float step = motion ? delta / DRAWER_SECONDS : 1;
        drawerShown = MathUtils.clamp(drawerShown + (open ? step : -step), 0, 1);
        float eased = open ? Interpolation.pow3Out.apply(drawerShown) : Interpolation.pow2In.apply(drawerShown);
        float edge = space("xl");
        float top = header.localToStageCoordinates(new Vector2()).y - space("lg");
        float bottom = hint.localToStageCoordinates(new Vector2(0, hint.getHeight())).y + space("sm");
        drawer.setBounds(MathUtils.lerp(-DRAWER_WIDTH - space("lg"), edge, eased), bottom, DRAWER_WIDTH,
              Math.max(0, top - bottom));
        drawer.setVisible(shown && drawerShown > 0);
        drawerTab.setVisible(shown);
        drawerTab.setSize(drawerTab.getPrefWidth(), drawerTab.getPrefHeight());
        float tabX = drawerShown > 0 ? drawer.getX() + DRAWER_WIDTH + space("xs") : edge;
        drawerTab.setPosition(Math.max(edge, tabX), top - drawerTab.getHeight());
    }

    private Table noticeLayer() {
        Table layer = new Table();
        layer.setFillParent(true);
        layer.setTouchable(Touchable.disabled);
        layer.top().padTop(space("xl") * 4);
        notice = ui.panel(true);
        noticeText = ui.label("", "hud-body", 14, UiTheme.tokenColor("status.warning"));
        notice.add(noticeText);
        notice.setVisible(false);
        layer.add(notice);
        return layer;
    }

    /** Where new contacts are called out as they strike the table, one under another. */
    private Table contactLayer() {
        Table layer = new Table();
        layer.setFillParent(true);
        layer.setTouchable(Touchable.disabled);
        layer.top().padTop(space("xl") * 4);
        contacts = new VerticalGroup();
        contacts.space(space("xs"));
        layer.add(contacts);
        return layer;
    }

    /** Calls out each of {@code arrived}, the scenarios new to the table, as its beacon strikes. */
    private void announce(List<WarTableView.Contact> arrived) {
        for (WarTableView.Contact contact : arrived) {
            Beacon beacon = contact.beacon();
            boolean now = beacon.daysLeft() == 0;
            Table banner = ui.panel(true);
            banner.setTransform(true);
            banner.add(ui.caption(getText(now ? "WarTable.contact.engaged" : "WarTable.contact"), true)).row();
            banner.add(ui.roleTypeLabel("title", beacon.name(), now ? "status.danger" : "status.warning")).row();
            String when = beacon.daysLeft() < 0 ? getText("WarTable.contact.open")
                  : now ? getText("WarTable.today") : getFormattedText("WarTable.inDays", beacon.daysLeft());
            banner.add(ui.label(when, "hud-body", 13, UiTheme.tokenColor("text.secondary")));
            banner.pack();
            banner.setOrigin(Align.center);
            banner.getColor().a = 0;
            float delay = Math.max(0, contact.delay()) + 0.15f;
            contacts.addAction(Actions.delay(delay, Actions.run(() -> {
                contacts.addActor(banner);
                banner.setScale(1.25f, 0.4f);
                banner.addAction(Actions.sequence(
                      Actions.parallel(Actions.fadeIn(0.18f), Actions.scaleTo(1, 1, 0.32f, Interpolation.swingOut)),
                      Actions.delay(3.2f),
                      Actions.parallel(Actions.fadeOut(0.5f), Actions.moveBy(0, 12, 0.5f, Interpolation.pow2In)),
                      Actions.removeActor()));
            })));
        }
    }

    private String seenKey(int sector) {
        return snapshot == null ? "" : snapshot.contract() + ":" + sector;
    }

    /** Where an order is asked to be confirmed before the campaign's told, the force held over the table meanwhile. */
    private Table askLayer() {
        askLayer = new Table();
        askLayer.setFillParent(true);
        askLayer.setTouchable(Touchable.childrenOnly);
        askLayer.bottom();
        ask = ui.panel(true);
        ask.setTouchable(Touchable.enabled);
        ask.addListener(SWALLOW);
        ask.setTransform(true);
        askLayer.add(ask).width(CARD_WIDTH + 40);
        askLayer.setVisible(false);
        return askLayer;
    }

    private Table header() {
        Table panel = ui.panel(true);
        panel.defaults().left();
        panel.add(ui.caption(getText("WarTable.overline"), true)).row();
        operation = ui.roleTypeLabel("title", "", "text.primary");
        nextOperation = ui.button("hud-icon", "chevron-right", null, null);
        UiKit.onChange(nextOperation, () -> showOperation((operationIndex + 1) % operations.size()));
        Table title = new Table();
        title.add(operation).left();
        title.add(nextOperation).padLeft(space("sm"));
        panel.add(title).padTop(space("xxs")).row();
        standing = new Table();
        panel.add(standing).padTop(space("sm")).row();
        return panel;
    }

    private Table hoverCard() {
        hoverCard = ui.panel(true);
        hoverCard.setTouchable(Touchable.disabled);
        hoverCard.defaults().left();
        hoverName = ui.roleTypeLabel("name", "", "text.primary");
        hoverDetail = ui.label("", "hud-body", 13, UiTheme.tokenColor("text.secondary"));
        hoverCard.add(hoverName).row();
        hoverCard.add(hoverDetail).padTop(space("xxs")).row();
        hoverCard.setVisible(false);
        return hoverCard;
    }

    /** The board a scenario would be fought on, filling the screen beside its briefing. */
    private Table inspectionLayer(UiTheme theme) {
        inspectionFrame = new Table();
        inspectionFrame.setFillParent(true);
        inspectionFrame.pad(space("xl"));
        briefing = ui.panel(true);
        briefing.top().left();
        briefing.setTouchable(Touchable.enabled);
        briefing.addListener(SWALLOW);
        inspection = new GpuBoardInspection(theme, ui, getText("WarTable.inspection.back"), this::rise);
        inspectionFrame.add(briefing).width(BRIEFING_WIDTH).top().fillY().padRight(space("lg"));
        inspectionFrame.add(inspection.actor()).grow();
        inspectionFrame.setVisible(false);
        return inspectionFrame;
    }

    /** Whether the table is up, so the screen draws it and gives it its input. */
    boolean shown() {
        return shown;
    }

    /** Shows the first of {@code shown}, its first sector rising on the table; an empty table when there's none. */
    void open(List<WarTableSnapshot> shown) {
        operations = List.copyOf(shown);
        this.shown = true;
        motion = !GUIPreferences.getInstance().getReduceMotion();
        nextOperation.setVisible(operations.size() > 1);
        header.getColor().a = 0;
        header.clearActions();
        header.addAction(Actions.delay(motion ? 0.6f : 0, Actions.fadeIn(motion ? 0.4f : 0)));
        if (operations.isEmpty()) {
            answer(false);
            letGo();
            reserve = List.of();
            snapshot = null;
            operation.setText(getText("WarTable.noOperation"));
            standing.clearChildren();
            tabs.clearChildren();
            segments = null;
            pins.clearChildren();
            pinned.clear();
            card.setVisible(false);
            inspectionFrame.setVisible(false);
            dive = 0;
            diving = false;
            inspected = null;
            view.clear();
            empty.setVisible(true);
            return;
        }
        empty.setVisible(false);
        showOperation(0);
    }

    private void showOperation(int index) {
        operationIndex = index;
        WarTableSnapshot shown = operations.get(index);
        snapshot = shown;
        operation.setText(shown.operation());
        standing(shown);
        tabs.clearChildren();
        segments = null;
        if (shown.sectors().size() > 1) {
            String[] names = shown.sectors().stream().map(Sector::name).toArray(String[]::new);
            segments = ui.segmented("hud-tab", false, names);
            for (int i = 0; i < segments.buttons.size(); i++) {
                int picked = i;
                UiKit.onChange(segments.buttons.get(i), () -> {
                    if (picked != sectorIndex) {
                        showSector(picked);
                    }
                });
            }
            segments.select(0);
            tabs.add(segments);
        }
        dive = 0;
        diving = false;
        inspected = null;
        inspectionFrame.setVisible(false);
        showSector(0);
    }

    private void standing(WarTableSnapshot shown) {
        standing.clearChildren();
        standing.add(ui.chip(getFormattedText("WarTable.victoryPoints", shown.victoryPoints()),
              shown.victoryPoints() < 0 ? UiKit.Tone.BAD : UiKit.Tone.OK, true)).padRight(space("sm"));
        standing.add(ui.chip(getFormattedText("WarTable.supportPoints", shown.supportPoints()), UiKit.Tone.NORMAL,
              true));
    }

    /**
     * Shows {@code shown}, the operations as they are now that an order's been carried out, in place: the operation,
     * sector and hex looked at stay, and forces deployed arc down onto the table.
     */
    void refresh(List<WarTableSnapshot> shown) {
        ordered.clear();
        UUID contract = snapshot == null ? null : snapshot.contract();
        operations = List.copyOf(shown);
        nextOperation.setVisible(operations.size() > 1);
        int index = -1;
        for (int i = 0; i < operations.size() && contract != null; i++) {
            if (operations.get(i).contract().equals(contract)) {
                index = i;
            }
        }
        if (!this.shown) {
            return;
        }
        if (index < 0) {
            open(shown);
            return;
        }
        operationIndex = index;
        snapshot = operations.get(index);
        standing(snapshot);
        if (sectorIndex >= snapshot.sectors().size()) {
            showOperation(index);
            return;
        }
        Sector sector = snapshot.sectors().get(sectorIndex);
        List<WarTableView.Contact> arrived = view.update(sector);
        seen.put(seenKey(sectorIndex), sector);
        pinAll(sector, arrived);
        announce(arrived);
        fillReserve(sector);
        Hex hex = pickedX < 0 ? null : sector.hex(pickedX, pickedY);
        if (hex != null && card.isVisible() && inspected == null) {
            fillCard(hex);
        }
    }

    /** Puts the table away, the board looked into with it. */
    void close() {
        answer(false);
        letGo();
        shown = false;
        inspection.close();
        inspectionFrame.setVisible(false);
        card.setVisible(false);
        hoverCard.setVisible(false);
    }

    /** Where {@code force}'s standee shows on the map, in input coordinates; null if it isn't on it. */
    @Nullable Vector2 standeeOnScreen(int force) {
        return view.screenOf(force);
    }

    /** Which of the operation's sectors is on the table. */
    int sectorShown() {
        return sectorIndex;
    }

    /** Whether the sector shown has its ground drawn (or the map's given up waiting for it). */
    boolean groundReady() {
        return view.ready();
    }

    void showSector(int index) {
        if (snapshot == null || snapshot.sectors().isEmpty()) {
            return;
        }
        sectorIndex = index;
        if (segments != null) {
            segments.select(index);
        }
        answer(false);
        letGo();
        Sector sector = snapshot.sectors().get(index);
        List<WarTableView.Contact> arrived = view.show(sector, seen.get(seenKey(index)));
        seen.put(seenKey(index), sector);
        contacts.clearActions();
        contacts.clearChildren();
        if (!motion) {
            view.settle();
            arrived = arrived.stream().map(contact -> new WarTableView.Contact(contact.beacon(), 0)).toList();
        }
        card.setVisible(false);
        pickedX = -1;
        pinAll(sector, arrived);
        announce(arrived);
        fillReserve(sector);
    }

    private void pinAll(Sector sector, List<WarTableView.Contact> arrived) {
        pins.clearChildren();
        pinned.clear();
        long now = TimeUtils.millis();
        for (Beacon beacon : sector.beacons()) {
            long from = now;
            for (WarTableView.Contact contact : arrived) {
                if (contact.beacon().equals(beacon)) {
                    from = now + (long) ((contact.delay() + 0.4f) * 1000);
                }
            }
            String when = beacon.daysLeft() < 0 ? ""
                  : beacon.daysLeft() == 0 ? getText("WarTable.today")
                  : getFormattedText("WarTable.inDays", beacon.daysLeft());
            pin(beacon.name(), when, beacon.daysLeft() == 0 ? UiKit.Tone.BAD : UiKit.Tone.WARN, beacon.x(),
                  beacon.y(), 2.2f, from);
        }
        for (Site site : sector.sites()) {
            pin(site.name(), "", site.allied() ? UiKit.Tone.OK : UiKit.Tone.BAD, site.x(), site.y(), 1.4f, now);
        }
        for (Token token : sector.tokens()) {
            // Clear of the standee
            pin(token.name(), "", UiKit.Tone.NORMAL, token.x(), token.y(), 2f, now);
        }
    }

    /**
     * Pins a map label over hex ({@code x}, {@code y}): an opaque plate so it reads over any ground, its edge bar in
     * {@code tone}'s colour, the name in upper case and {@code detail}, if any, after it in the tone's colour.
     */
    private void pin(String text, String detail, UiKit.Tone tone, int x, int y, float lift, long from) {
        Table chip = new Table();
        chip.setBackground(ui.skin.newDrawable("white", LABEL_PLATE));
        chip.add(new Image(ui.skin.newDrawable("white", tone == UiKit.Tone.NORMAL ? LABEL_FRIEND : tone.color)))
              .width(3).growY();
        chip.add(ui.label(UiTheme.upper(text), "hud-button", 12, LABEL_INK)).padLeft(7).padRight(detail.isEmpty() ? 8 : 0)
              .padTop(3).padBottom(3);
        if (!detail.isEmpty()) {
            chip.add(ui.label(UiTheme.upper(detail), "hud-button", 12, tone.color)).padLeft(8).padRight(8);
        }
        chip.pack();
        chip.setVisible(false);
        pins.addActor(chip);
        pinned.add(new Pin(chip, x, y, lift, from));
    }

    /**
     * Fills the drawer with {@code sector}'s reserve, but for the forces already ordered onto the table: a tab for each
     * kind of force it holds, the one looked at kept while it still holds any.
     */
    private void fillReserve(Sector sector) {
        reserve = sector.reserve().stream().filter(force -> !ordered.contains(force.force())).toList();
        drawerCount.count(reserve.size());
        tabCount.count(reserve.size());
        Map<Kind, Integer> kinds = new EnumMap<>(Kind.class);
        for (Reserve force : reserve) {
            kinds.merge(force.kind(), 1, Integer::sum);
        }
        List<Kind> held = new ArrayList<>(kinds.keySet());
        if (drawerKind == null || !kinds.containsKey(drawerKind)) {
            drawerKind = held.isEmpty() ? null : held.get(0);
        }
        drawerKinds.clearChildren();
        if (held.size() > 1) {
            String[] labels = held.stream()
                  .map(kind -> getFormattedText("WarTable.reserve." + kind.name().toLowerCase(Locale.ROOT),
                        kinds.get(kind)))
                  .toArray(String[]::new);
            UiKit.Segmented segmented = ui.segmented("hud-tab", false, labels);
            for (int i = 0; i < held.size(); i++) {
                Kind kind = held.get(i);
                int index = i;
                UiKit.onChange(segmented.buttons.get(i), () -> {
                    drawerKind = kind;
                    segmented.select(index);
                    fillDrawer();
                });
            }
            segmented.select(held.indexOf(drawerKind));
            drawerKinds.add(segmented);
        }
        if (carriedToken == null) {
            caption(false);
        }
        fillDrawer();
    }

    /**
     * Fills the drawer's list: with a search, every force whose name or TO&amp;E force holds it, else the forces of
     * the kind looked at; under a heading for each TO&amp;E force, in the order they first come.
     */
    private void fillDrawer() {
        drawerList.clearChildren();
        tiles.clear();
        drawerArt.forEach(Texture::dispose);
        drawerArt.clear();
        String query = drawerSearch.field.getText().trim().toLowerCase(Locale.ROOT);
        drawerKinds.setVisible(query.isEmpty());
        Map<String, List<Reserve>> groups = new LinkedHashMap<>();
        for (Reserve force : reserve) {
            boolean shown = query.isEmpty() ? force.kind() == drawerKind
                  : (force.name() + "\n" + force.group()).toLowerCase(Locale.ROOT).contains(query);
            if (shown) {
                groups.computeIfAbsent(force.group(), group -> new ArrayList<>()).add(force);
            }
        }
        if (groups.isEmpty()) {
            Label none = ui.label(getText(reserve.isEmpty() ? "WarTable.reserveEmpty" : "WarTable.reserve.noMatch"),
                  "hud-body", 13, UiTheme.tokenColor("text.secondary"));
            none.setWrap(true);
            drawerList.add(none).width(DRAWER_ROW * (TILE_WIDTH + 2 * space("xxs"))).padTop(space("sm"));
            return;
        }
        for (Map.Entry<String, List<Reserve>> group : groups.entrySet()) {
            Table heading = new Table();
            Label name = ui.label(group.getKey().isBlank() ? getText("WarTable.reserve.unassigned") : group.getKey(),
                  "hud-body", 12, UiTheme.tokenColor("text.secondary"));
            name.setEllipsis(true);
            heading.add(name).growX().minWidth(0).left();
            heading.add(ui.label(String.valueOf(group.getValue().size()), "hud-body", 12,
                  UiTheme.tokenColor("text.secondary"))).padLeft(space("xs"));
            boolean first = drawerList.getCells().isEmpty();
            drawerList.add(heading).colspan(DRAWER_ROW).growX().padTop(first ? 0 : space("md")).row();
            drawerList.add(ui.solid("border.subtle")).colspan(DRAWER_ROW).growX().height(1)
                  .padBottom(space("xxs")).row();
            int count = 0;
            for (Reserve force : group.getValue()) {
                if (count > 0 && count % DRAWER_ROW == 0) {
                    drawerList.row();
                }
                Actor tile = tile(force);
                tiles.put(force.force(), tile);
                drawerList.add(tile).width(TILE_WIDTH).pad(space("xxs")).top();
                count++;
            }
            drawerList.row();
        }
    }

    /** Says over the drawer what it's for: forces to drag out, or, while one's carried off the table, to drop it in. */
    private void caption(boolean recalling) {
        drawerCaption.setText(getText(recalling ? "WarTable.recallHere" : "WarTable.reserve.drag"));
        drawerCaption.setColor(UiTheme.tokenColor(recalling ? "status.warning" : "text.secondary"));
    }

    /** Where force {@code force}'s tile is in the open drawer, in screen pixels, or null if it isn't there. */
    @Nullable Vector2 tileOnScreen(int force) {
        Actor tile = tiles.get(force);
        return tile == null || !drawer.isVisible() ? null
              : stage.stageToScreenCoordinates(tile.localToStageCoordinates(new Vector2(tile.getWidth() / 2,
                    tile.getHeight() / 2)));
    }

    /** The forces the drawer holds, in its order. */
    List<Reserve> reserveShown() {
        return reserve;
    }

    /** {@code reserve}'s tile: its icon over its name, rising under the pointer, to be dragged onto the table. */
    private Actor tile(Reserve reserve) {
        Table tile = new Table();
        tile.setTouchable(Touchable.enabled);
        tile.setTransform(true);
        TextureRegionDrawable art = null;
        if (reserve.icon() != null) {
            Texture texture = WarTableAssets.texture(reserve.icon());
            drawerArt.add(texture);
            art = new TextureRegionDrawable(texture);
            Image image = new Image(art);
            image.setScaling(Scaling.fit);
            tile.add(image).size(TILE_WIDTH - space("xs"), TILE_ART).row();
        } else {
            tile.add(ui.solid("border.subtle")).size(TILE_ART).row();
        }
        Label name = ui.label(reserve.name(), "hud-body", 11, UiTheme.tokenColor("text.primary"));
        name.setWrap(true);
        name.setAlignment(Align.center);
        tile.add(name).width(TILE_WIDTH).padTop(space("xxs"));
        TextureRegionDrawable dragged = art;
        tile.addListener(new InputListener() {
            private boolean carrying;

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, @Nullable Actor fromActor) {
                if (pointer == -1 && !carrying) {
                    tile.setOrigin(Align.bottom);
                    tile.clearActions();
                    tile.addAction(Actions.scaleTo(1.06f, 1.06f, 0.12f, Interpolation.pow2Out));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, @Nullable Actor toActor) {
                if (pointer == -1 && !carrying) {
                    tile.clearActions();
                    tile.addAction(Actions.scaleTo(1, 1, 0.12f, Interpolation.pow2Out));
                }
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.LEFT || busy() || !view.settled()) {
                    return false;
                }
                carrying = true;
                drawerAway = true;
                stage.setKeyboardFocus(null);
                view.carry(reserve.icon(), -1);
                dragIcon.setDrawable(dragged);
                tile.getColor().a = 0.35f;
                Vector2 at = screen(event);
                follow((int) at.x, (int) at.y);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                Vector2 at = screen(event);
                follow((int) at.x, (int) at.y);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                carrying = false;
                drawerAway = false;
                tile.getColor().a = 1;
                tile.clearActions();
                tile.setScale(1);
                dragIcon.setVisible(false);
                Vector2 at = screen(event);
                int screenX = (int) at.x;
                int screenY = (int) at.y;
                Hex hex = overDrawer(screenX, screenY) ? null : view.point(screenX, screenY);
                if (hex != null && hex.open()) {
                    askDeploy(reserve, hex);
                } else {
                    view.drop();
                    if (hex != null) {
                        notice(getText("WarTable.deploy.closed"));
                    }
                }
            }
        });
        return tile;
    }

    /** A force dragged from the drawer is at ({@code screenX}, {@code screenY}): over the table its standee is. */
    private void follow(int screenX, int screenY) {
        Hex hex = null;
        if (overDrawer(screenX, screenY)) {
            view.unpoint();
        } else {
            hex = view.point(screenX, screenY);
        }
        boolean onTable = hex != null && view.carrying();
        dragIcon.setVisible(!onTable && dragIcon.getDrawable() != null);
        Vector2 at = stage.screenToStageCoordinates(new Vector2(screenX, screenY));
        dragIcon.setPosition(at.x - DRAG_ART / 2, at.y - DRAG_ART / 2);
    }

    private Vector2 screen(InputEvent event) {
        return stage.stageToScreenCoordinates(new Vector2(event.getStageX(), event.getStageY()));
    }

    /** Whether ({@code screenX}, {@code screenY}) is over the drawer, or its tab. */
    private boolean overDrawer(int screenX, int screenY) {
        Actor hit = hit(screenX, screenY);
        return hit != null && (hit.isDescendantOf(drawer) || hit.isDescendantOf(drawerTab));
    }

    /** Whether ({@code screenX}, {@code screenY}) is over one of the table's panels, rather than the table. */
    private boolean overPanel(int screenX, int screenY) {
        Actor hit = hit(screenX, screenY);
        return hit != null && !hit.isDescendantOf(pins);
    }

    private @Nullable Actor hit(int screenX, int screenY) {
        Vector2 at = stage.screenToStageCoordinates(new Vector2(screenX, screenY));
        return stage.hit(at.x, at.y, true);
    }

    private void askDeploy(Reserve reserve, Hex hex) {
        WarTableSnapshot ordering = snapshot;
        int sectorOrdered = sectorIndex;
        if (ordering == null) {
            view.drop();
            return;
        }
        view.holdOver(hex);
        List<String> lines = new ArrayList<>();
        lines.add(getFormattedText("WarTable.hex", hex.x() + 1, hex.y() + 1) + "  \u00b7  " + hex.terrain());
        lines.add(getText(hex.scouted() ? "WarTable.scouted" : "WarTable.unscouted"));
        lines.add(getText("WarTable.deploy.warning"));
        ask(getText("WarTable.deploy.title"), reserve.name(), lines, getText("WarTable.deploy.confirm"), () -> {
            view.drop();
            view.land(reserve.force());
            ordered.add(reserve.force());
            Sector sector = sector();
            if (sector != null) {
                fillReserve(sector);
            }
            orders.deploy(ordering.contract(), sectorOrdered, hex.x(), hex.y(), reserve.force());
        }, view::drop);
    }

    private void askRecall(Token token) {
        WarTableSnapshot ordering = snapshot;
        Sector sector = sector();
        int sectorOrdered = sectorIndex;
        if (ordering == null || sector == null) {
            view.drop();
            return;
        }
        view.holdOver(sector.hex(token.x(), token.y()));
        List<String> lines = new ArrayList<>();
        lines.add(getFormattedText("WarTable.recall.cost", WarTableSnapshot.RECALL_COST, ordering.supportPoints()));
        ask(getText("WarTable.recall.title"), token.name(), lines, getText("WarTable.recall.confirm"), () -> {
            view.drop();
            view.lift(token.force(), () -> orders.recall(ordering.contract(), sectorOrdered, token.force()));
        }, view::drop);
    }

    /** Asks for {@code subject} to be {@code title}d, {@code lines} telling what that means; runs the answer's. */
    private void ask(String title, String subject, List<String> lines, String yes, Runnable confirm,
          Runnable cancel) {
        hoverCard.setVisible(false);
        ask.clearChildren();
        ask.defaults().left().growX();
        ask.add(ui.caption(title, true)).row();
        Label name = ui.roleTypeLabel("title", subject, "text.primary");
        name.setWrap(true);
        ask.add(name).padTop(space("xxs")).row();
        for (String line : lines) {
            Label text = ui.label(line, "hud-body", 13, UiTheme.tokenColor("text.secondary"));
            text.setWrap(true);
            ask.add(text).padTop(space("xxs")).row();
        }
        Table buttons = new Table();
        UiButton no = ui.button("button-secondary", null, getText("WarTable.cancel"), null);
        UiKit.onChange(no, () -> answer(false));
        UiButton go = ui.button("button-primary", null, yes, null);
        UiKit.onChange(go, () -> answer(true));
        buttons.add(no).padRight(space("sm"));
        buttons.add(go);
        ask.add(buttons).right().fill(false, false).padTop(space("md")).row();
        confirmed = confirm;
        cancelled = cancel;
        askLayer.padBottom(space("xl") * 2);
        askLayer.invalidate();
        askLayer.setVisible(true);
        if (motion) {
            ask.pack();
            ask.setOrigin(Align.center);
            ask.getColor().a = 0;
            ask.setScale(0.92f);
            ask.clearActions();
            ask.addAction(Actions.parallel(Actions.fadeIn(0.16f),
                  Actions.scaleTo(1, 1, 0.22f, Interpolation.swingOut)));
        }
    }

    /** Answers what's asked, if anything is: runs its confirmation, or its cancellation. */
    private void answer(boolean yes) {
        Runnable then = yes ? confirmed : cancelled;
        confirmed = null;
        cancelled = null;
        if (askLayer != null) {
            askLayer.setVisible(false);
        }
        if (then != null) {
            then.run();
        }
    }

    private boolean asking() {
        return confirmed != null || cancelled != null;
    }

    /** Whether a field has the keyboard, so keys are typed rather than acted on. */
    private boolean typing() {
        return stage.getKeyboardFocus() instanceof TextField;
    }

    private boolean busy() {
        return asking() || inspected != null || carriedToken != null;
    }

    /** Puts down a force carried off the table, if one is. */
    private void letGo() {
        if (carriedToken != null) {
            carriedToken = null;
            caption(false);
            openDrawer(drawerWasOpen);
            view.drop();
        }
    }

    /** Says {@code text} over the table for a moment. */
    private void notice(String text) {
        noticeText.setText(text);
        notice.pack();
        notice.setVisible(true);
        notice.clearActions();
        notice.getColor().a = 0;
        notice.addAction(Actions.sequence(Actions.fadeIn(0.15f), Actions.delay(2.2f), Actions.fadeOut(0.4f),
              Actions.visible(false)));
    }

    private @Nullable Token tokenOf(int force) {
        Sector sector = sector();
        if (sector != null) {
            for (Token token : sector.tokens()) {
                if (token.force() == force) {
                    return token;
                }
            }
        }
        return null;
    }

    /** A click on the table picked {@code hex}: its card shows what's on it. */
    private void picked(Hex hex) {
        if (sector() == null) {
            return;
        }
        fillCard(hex);
        if (motion) {
            card.getColor().a = 0;
            card.clearActions();
            card.addAction(Actions.fadeIn(0.18f));
        }
    }

    private void fillCard(Hex hex) {
        Sector sector = sector();
        if (sector == null) {
            return;
        }
        pickedX = hex.x();
        pickedY = hex.y();
        card.clearChildren();
        card.defaults().left().growX();
        card.add(ui.caption(getFormattedText("WarTable.hex", hex.x() + 1, hex.y() + 1), true)).row();
        Label terrain = ui.roleTypeLabel("title", hex.terrain(), "text.primary");
        terrain.setWrap(true);
        card.add(terrain).padTop(space("xxs")).row();
        List<String> traits = new ArrayList<>();
        traits.add(getText(hex.scouted() ? "WarTable.scouted" : "WarTable.unscouted"));
        if (hex.city()) {
            traits.add(getText("WarTable.city"));
        }
        if (hex.road()) {
            traits.add(getText("WarTable.road"));
        }
        card.add(ui.label(String.join("  \u00b7  ", traits), "hud-body", 13,
              UiTheme.tokenColor(hex.scouted() ? "text.secondary" : "status.warning"))).padTop(space("xxs")).row();
        for (Beacon beacon : sector.beacons()) {
            if (beacon.x() == hex.x() && beacon.y() == hex.y()) {
                beaconEntry(beacon);
            }
        }
        for (Site site : sector.sites()) {
            if (site.x() == hex.x() && site.y() == hex.y()) {
                entry(site.name(), getText(site.allied() ? "WarTable.allied" : "WarTable.hostile")
                            + (site.objective() ? "  \u00b7  " + getText("WarTable.objective") : "")
                            + (site.cutOff() ? "  \u00b7  " + getText("WarTable.cutOff") : ""),
                      site.detail(), site.allied() ? "status.success" : "status.danger");
            }
        }
        for (Token token : sector.tokens()) {
            if (token.x() == hex.x() && token.y() == hex.y()) {
                tokenEntry(token);
            }
        }
        for (Mark mark : sector.marks()) {
            if (mark.x() == hex.x() && mark.y() == hex.y()) {
                entry(mark.name(), getText("WarTable.pointOfInterest"), mark.description(), "text.primary");
            }
        }
        card.setVisible(true);
    }

    /** A force's entry on a hex's card, with what recalling it would cost, or why it can't be. */
    private void tokenEntry(Token token) {
        entry(token.name(), getText("WarTable.force"), "", "text.primary");
        if (token.hold().isBlank()) {
            UiButton recall = ui.button("button-secondary", null,
                  getFormattedText("WarTable.recall", WarTableSnapshot.RECALL_COST), null);
            UiKit.onChange(recall, () -> {
                if (!busy()) {
                    askRecall(token);
                }
            });
            card.add(recall).padTop(space("sm")).left().fill(false, false).row();
        } else {
            Label held = ui.label(token.hold(), "hud-body", 13, UiTheme.tokenColor("status.warning"));
            held.setWrap(true);
            card.add(held).padTop(space("xxs")).row();
        }
    }

    private void entry(String name, String kind, String detail, String colour) {
        card.add(ui.solid("border.subtle")).height(1).padTop(space("md")).row();
        Label title = ui.roleTypeLabel("name", name, "text.primary");
        title.setWrap(true);
        card.add(title).padTop(space("sm")).row();
        card.add(ui.label(kind, "hud-body", 13, UiTheme.tokenColor(colour))).padTop(space("xxs")).row();
        if (!detail.isBlank()) {
            Label text = ui.label(detail, "hud-body", 13, UiTheme.tokenColor("text.secondary"));
            text.setWrap(true);
            card.add(text).padTop(space("xxs")).row();
        }
    }

    private void beaconEntry(Beacon beacon) {
        String when = beacon.daysLeft() < 0 ? getText("WarTable.undated")
              : beacon.daysLeft() == 0 ? getText("WarTable.today")
              : getFormattedText("WarTable.inDays", beacon.daysLeft());
        entry(beacon.name(), getText("WarTable.scenario") + "  \u00b7  " + when
              + (beacon.objective() ? "  \u00b7  " + getText("WarTable.objective") : ""), beacon.briefing(),
              beacon.daysLeft() == 0 ? "status.danger" : "status.warning");
        if (beacon.map() != null) {
            UiButton go = ui.button("button-primary", null, getText("WarTable.intoBattle"), null);
            UiKit.onChange(go, () -> diveInto(beacon));
            card.add(go).padTop(space("md")).left().row();
        }
    }

    /** Picks {@code beacon}'s hex on the shown sector, as a click would, and dives onto it. */
    void inspect(Beacon beacon) {
        Sector sector = sector();
        Hex hex = sector == null ? null : sector.hex(beacon.x(), beacon.y());
        if (hex == null) {
            return;
        }
        view.select(hex);
        picked(hex);
        diveInto(beacon);
    }

    /** Dives onto {@code beacon}'s hex while its board is made, then shows the board beside its briefing. */
    private void diveInto(Beacon beacon) {
        MapSettings settings = beacon.map();
        Sector sector = sector();
        if (settings == null || sector == null) {
            return;
        }
        inspected = beacon;
        diving = true;
        card.setVisible(false);
        hoverCard.setVisible(false);
        briefing.clearChildren();
        briefing.defaults().left().growX();
        briefing.add(ui.caption(sector.name(), true)).row();
        Label name = ui.roleTypeLabel("title", beacon.name(), "text.primary");
        name.setWrap(true);
        briefing.add(name).padTop(space("xxs")).row();
        Label text = ui.label(beacon.briefing().isBlank() ? getText("WarTable.noBriefing") : beacon.briefing(),
              "hud-body", 14, UiTheme.tokenColor("text.secondary"));
        text.setWrap(true);
        briefing.add(text).padTop(space("md")).row();
        briefing.add(ui.label(getFormattedText("WarTable.board", settings.getBoardWidth(), settings.getBoardHeight()),
              "hud-body", 13, UiTheme.tokenColor("text.primary"))).padTop(space("lg")).row();
        PlanetaryConditions conditions = new PlanetaryConditions();
        conditions.setTemperature(sector.temperature());
        MapSettings copy = MapSettings.getInstance(settings);
        inspection.inspect(beacon.name(), () -> ServerBoardHelper.getPossibleGameBoard(copy, false), conditions);
        if (!motion) {
            dive = 1;
        }
    }

    /** Back from the board: it goes, and the camera rises back off the hex. */
    private void rise() {
        diving = false;
        inspection.close();
        inspectionFrame.setVisible(false);
        if (!motion) {
            dive = 0;
        }
    }

    private void escape() {
        if (asking()) {
            answer(false);
        } else if (carriedToken != null) {
            letGo();
        } else if (inspected != null) {
            rise();
        } else if (!drawerSearch.field.getText().isEmpty() || typing()) {
            drawerSearch.field.setText("");
            stage.setKeyboardFocus(null);
        } else if (drawerOpen) {
            openDrawer(false);
        } else {
            leave.run();
        }
    }

    private @Nullable Sector sector() {
        return snapshot == null || snapshot.sectors().isEmpty() ? null : snapshot.sectors().get(sectorIndex);
    }

    /** The table's input: Escape, then its controls, then the camera's while no board is up. */
    InputProcessor input() {
        InputProcessor keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (!shown) {
                    return false;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    escape();
                    return true;
                }
                if (asking() && (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER)) {
                    answer(true);
                    return true;
                }
                if (keycode == Input.Keys.SPACE && !typing()) {
                    view.settle();
                    return true;
                }
                if (keycode == Input.Keys.P && !typing()) {
                    view.plot(!view.plotted());
                    return true;
                }
                return false;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                pointerX = screenX;
                pointerY = screenY;
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                pointerX = screenX;
                pointerY = screenY;
                return false;
            }
        };
        InputProcessor camera = new InputAdapter() {
            private boolean active() {
                return shown && inspected == null;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (!active()) {
                    return false;
                }
                if (asking()) {
                    return true;
                }
                stage.setKeyboardFocus(null);
                if (button == Input.Buttons.LEFT && carriedToken == null) {
                    // A force on the table is picked up off it to drag onto the drawer, unless it may not leave
                    Token token = tokenOf(view.figureAt(screenX, screenY));
                    if (token != null && token.hold().isBlank()) {
                        carriedToken = token;
                        carriedFromX = screenX;
                        carriedFromY = screenY;
                        view.carry(token.icon(), token.force());
                        view.point(screenX, screenY);
                        caption(true);
                        drawerWasOpen = drawerOpen;
                        openDrawer(true);
                        return true;
                    }
                    if (token != null) {
                        notice(token.hold());
                    }
                }
                return view.input().touchDown(screenX, screenY, pointer, button);
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (!active()) {
                    return false;
                }
                if (carriedToken != null) {
                    if (overDrawer(screenX, screenY)) {
                        view.unpoint();
                    } else {
                        view.point(screenX, screenY);
                    }
                    return true;
                }
                return !asking() && view.input().touchDragged(screenX, screenY, pointer);
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (!active()) {
                    return false;
                }
                Token token = carriedToken;
                if (token != null) {
                    carriedToken = null;
                    caption(false);
                    Sector sector = sector();
                    Hex from = sector == null ? null : sector.hex(token.x(), token.y());
                    boolean recalled = overDrawer(screenX, screenY);
                    openDrawer(drawerWasOpen);
                    if (recalled) {
                        askRecall(token);
                    } else {
                        view.drop();
                        if (from != null && Math.abs(screenX - carriedFromX) + Math.abs(screenY - carriedFromY)
                              <= CLICK_SLOP) {
                            // Only clicked: picks the force's hex, as a click on the table would
                            view.select(from);
                            picked(from);
                        }
                    }
                    return true;
                }
                return !asking() && view.input().touchUp(screenX, screenY, pointer, button);
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                if (active() && overPanel(screenX, screenY)) {
                    view.unpoint();
                    return false;
                }
                return active() && view.input().mouseMoved(screenX, screenY);
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                return active() && view.input().scrolled(amountX, amountY);
            }
        };
        InputProcessor controls = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return shown && stage.keyDown(keycode);
            }

            @Override
            public boolean keyUp(int keycode) {
                return shown && stage.keyUp(keycode);
            }

            @Override
            public boolean keyTyped(char character) {
                return shown && stage.keyTyped(character);
            }

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
        return new InputMultiplexer(keys, controls, camera);
    }

    void resize(int width, int height) {
        if (stage == null || width <= 0 || height <= 0) {
            return;
        }
        view.resize(width, height);
        ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1 / scale);
        stage.getViewport().update(width, height, true);
    }

    /** Draws the room and the table's controls over it. */
    void render(float delta) {
        if (!shown || stage == null) {
            return;
        }
        float step = motion ? delta / DIVE_SECONDS : 1;
        dive = MathUtils.clamp(dive + (diving ? step : -step), 0, 1);
        if (!diving && dive == 0) {
            inspected = null;
        }
        boolean boardUp = diving && dive >= 1;
        if (boardUp && !inspectionFrame.isVisible()) {
            inspectionFrame.setVisible(true);
            if (motion) {
                inspectionFrame.getColor().a = 0;
                inspectionFrame.clearActions();
                inspectionFrame.addAction(Actions.fadeIn(0.25f, Interpolation.smooth));
            }
        }
        view.render(Interpolation.smooth.apply(dive));
        root.setVisible(inspected == null);
        placeDrawer(delta);
        placePins();
        placeHoverCard();
        stage.getViewport().apply(true);
        stage.act(delta);
        stage.draw();
    }

    private void placePins() {
        boolean settled = view.settled() && inspected == null;
        Vector3 at = new Vector3();
        // Pins sharing a hex stack upward from the lowest one instead of covering each other
        Map<Long, Float> stacked = new HashMap<>();
        long now = TimeUtils.millis();
        for (Pin pin : pinned) {
            Actor actor = pin.actor();
            if (!settled || now < pin.from()) {
                actor.setVisible(false);
                actor.getColor().a = 0;
                continue;
            }
            view.top(pin.x(), pin.y(), at);
            at.y += 0.03f * pin.lift();
            view.project(at);
            if (at.z > 1) {
                actor.setVisible(false);
                continue;
            }
            actor.setVisible(true);
            actor.getColor().a = Math.min(1, actor.getColor().a + Gdx.graphics.getDeltaTime() * 3);
            long hex = ((long) pin.x() << 32) | (pin.y() & 0xFFFFFFFFL);
            float y = Math.max(at.y / scale, stacked.getOrDefault(hex, Float.NEGATIVE_INFINITY));
            stacked.put(hex, y + actor.getHeight() + 2);
            actor.setPosition(at.x / scale - actor.getWidth() / 2, y, Align.bottomLeft);
        }
    }

    private void placeHoverCard() {
        Hex hex = inspected == null && !asking() ? view.hovered() : null;
        Sector sector = sector();
        if (hex == null || sector == null) {
            hoverCard.setVisible(false);
            return;
        }
        hoverName.setText(hex.terrain());
        String detail = getText(hex.scouted() ? "WarTable.scouted" : "WarTable.unscouted");
        for (Beacon beacon : sector.beacons()) {
            if (beacon.x() == hex.x() && beacon.y() == hex.y()) {
                detail = beacon.name();
            }
        }
        for (Site site : sector.sites()) {
            if (site.x() == hex.x() && site.y() == hex.y()) {
                detail = site.name();
            }
        }
        hoverDetail.setText(detail);
        hoverCard.pack();
        float x = pointerX / scale + 18;
        float y = (Gdx.graphics.getHeight() - pointerY) / scale - 18 - hoverCard.getHeight();
        hoverCard.setPosition(Math.min(x, stage.getWidth() - hoverCard.getWidth()), Math.max(0, y));
        hoverCard.setVisible(true);
    }

    @Override
    public void dispose() {
        drawerArt.forEach(Texture::dispose);
        drawerArt.clear();
        if (inspection != null) {
            inspection.dispose();
        }
        view.dispose();
        if (stage != null) {
            stage.dispose();
        }
    }

    private static float space(String token) {
        return DesignTokens.shipped().space().get(token);
    }

}

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

import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;
import megamek.client.ui.design.DesignTokens;
import megamek.client.ui.gdx.UiButton;
import megamek.client.ui.gdx.UiKit;
import megamek.client.ui.gdx.UiMenuList;
import megamek.client.ui.gdx.UiModal;
import megamek.client.ui.gdx.UiPopover;
import megamek.client.ui.gdx.UiTheme;
import megamek.common.Configuration;
import mekhq.campaign.universe.eras.Eras;
import mekhq.gui.enums.MHQTabType;
import mekhq.gui.model.view.StarMap;
import mekhq.gui.model.view.TopBar;

/**
 * The campaign's command post: the {@link HoloTableView holotable} with the star map around the force, and over it the
 * campaign's name, today, where the force is, its funds and what needs the player's attention; a rail of the
 * campaign's areas (keys 1 to 7), a command palette (Ctrl+K) over its screens, people, units and menu items, and the
 * way back to the classic campaign window. The rail's Map (key 2), or a click on the table's map, dives the camera down
 * onto the map's disc and fades the room into the full {@link StarMapView star map}; the hub's own chrome and rail
 * slide away (their keys still work), and the map's header (the way back,
 * where the force is, today and a search for a system), the factions in view, a compass and the chosen system's detail
 * sliding in around it; leaving flies the map back and climbs out again, and a click or Esc skips either way. Hovering
 * a system names its owners and clicking it opens its detail. Under the map a {@link TimeScrubber timeline} takes it
 * through the inner sphere's history, its borders and owners as they were in any year ([ and ], Shift for a decade),
 * and back to the campaign's own (T); it only ever changes what the map shows. GPU thread, unless a method says
 * otherwise.
 */
final class HubScreen extends ApplicationAdapter {
    /** The most results the palette lists. */
    private static final int RESULTS = 12;
    private static final float PALETTE_WIDTH = 560;
    private static final float DETAIL_WIDTH = 320;
    private static final float SEARCH_WIDTH = 300;
    /** The most systems the map's search lists, and factions its legend. */
    private static final int FOUND = 8;
    private static final int LEGEND = 6;
    /** How often the legend recounts the factions on screen, in seconds. */
    private static final float LEGEND_EVERY = 0.3f;
    /** How long the map takes to open, in seconds; the dive ends, and the fade into the map starts, part way. */
    private static final float OPEN_SECONDS = 1.4f;
    private static final float DIVE_END = 0.65f;
    private static final float FADE_START = 0.5f;
    /** How far the chrome slides in from the edges, in stage units. */
    private static final float CHROME_SLIDE = 40;
    /** How far right of and below the cursor the hover card sits, in stage units. */
    private static final float HOVER_OFFSET = 18;
    /** The years the timeline spans, unless the campaign is past the last. */
    static final int FIRST_YEAR = 2150;
    static final int LAST_YEAR = 3152;
    private static final float TIMELINE_WIDTH = 640;
    private static final float TIMELINE_MIN_WIDTH = 300;
    /** The widest the scale bar runs, in stage units. */
    private static final float SCALE_WIDTH = 110;
    private static final int[] SCALE_STEPS = { 1, 2, 5, 10, 20, 50, 100, 200, 500, 1000 };

    /** What the screen asks of the hub; any thread. */
    interface Hub {
        /** Goes where {@code target} leads. */
        void go(PaletteEntry.Target target);

        /** Gathers the palette's entries, which come back through {@link #palette(List)}. */
        void palette();

        /** The star map opened or closed; while open, the campaign's map comes through {@link #map(StarMap)}. */
        void mapShown(boolean shown);

        /** Reads the detail of the system {@code id}, which comes back through {@link #detail(StarMap.Detail)}. */
        void system(String id);

        /**
         * Sets the force out on the course to the system {@code id} once the player confirms it, then reads its
         * detail again.
         */
        void travel(String id);

        /** The screen's listener is being disposed. */
        void disposed();
    }

    private final HoloTableView holo;
    private final StarMapView map;
    private final BorderHistory history;
    private Table timeLayer;
    private Table timePanel;
    private TimeScrubber scrubber;
    private Label timeYear;
    private Label timeEra;
    private UiButton todayButton;
    /** The year the timeline's readout and era show, so they are only set as it changes. */
    private int readoutYear = Integer.MIN_VALUE;
    /** The date read ahead of the timeline last, so it is asked for once. */
    private @Nullable LocalDate readAhead;
    /** The campaign's date, once its map came; the timeline's years are on its day. */
    private @Nullable LocalDate today;
    /** The campaign's date as the top bar writes it. */
    private String todayText = "";
    private StarLabels labels;
    /** Whether the map is open or opening, rather than closed or closing. */
    private boolean mapOpen;
    /** How far the map has opened, 0 to 1: the camera dives to the table's disc, then the room fades into the map. */
    private float progress;
    /** Closing, the map's camera flies back to its opening view before the dive reverses. */
    private boolean returning;
    /** How far the map's chrome has slid in, 0 to 1. */
    private float chromeShown;
    private boolean handCursor;
    private Table hubLayout;
    private Table railLayer;
    private Table header;
    private Table headerPanel;
    private Table legendLayer;
    private Table compassLayer;
    private Label mapWhere;
    private Label mapDate;
    private Label mapVoyage;
    private UiKit.SearchField search;
    private Table searchPanel;
    private UiMenuList searchResults;
    private Table legend;
    private List<StarMapView.Legend> legendShown = List.of();
    private float legendClock;
    private Container<UiKit.Icon> compass;
    private Image scaleBar;
    private Cell<Image> scaleCell;
    private Label scaleText;
    /** The light years the scale bar shows; 0 before it is first placed. */
    private int scaleShown;
    private Table detailLayer;
    private Table detail;
    private Table hoverCard;
    private Label hoverName;
    private Label hoverOwner;
    /** The system whose detail the panel waits for or shows. */
    private @Nullable String detailId;
    private final String campaignName;
    private final float scale;
    private final Hub hub;
    private UiTheme theme;
    private UiKit ui;
    private Stage stage;
    private Label date;
    private Label where;
    private Label funds;
    private Table alerts;
    private final Map<HubArea, UiButton> rail = new EnumMap<>(HubArea.class);
    private UiPopover flyout;
    private UiMenuList flyoutList;
    private @Nullable HubArea flyoutArea;
    private UiModal paletteLayer;
    private UiKit.SearchField paletteField;
    private UiMenuList results;
    /** The palette's entries for this opening; null while the hub gathers them. */
    private @Nullable List<PaletteEntry> entries;
    private Set<MHQTabType> screens = EnumSet.allOf(MHQTabType.class);
    /** GPU thread: the last top bar the session handed over, shown at the next frame. */
    private @Nullable TopBar pending;
    /** Whether a modal Swing dialog over the hub holds the input. */
    private volatile boolean blocked;

    /**
     * The hub of the campaign {@code campaignName}: the table from {@code glb} with {@code chart} over it, at the GUI
     * scale {@code scale}, its map's borders read from {@code history}.
     */
    HubScreen(Path glb, @Nullable StarChart chart, String campaignName, float scale, Hub hub,
          BorderHistory history) {
        holo = new HoloTableView(glb, chart, () -> showMap(true));
        map = new StarMapView(new StarMapView.Listener() {
            @Override
            public void hovered(@Nullable StarMap.Star star) {
                hover(star);
            }

            @Override
            public void chosen(@Nullable StarMap.Star star) {
                chooseSystem(star);
            }
        });
        if (chart != null) {
            map.startAt(chart.focusX(), chart.focusY());
        }
        this.history = history;
        map.history(history);
        this.campaignName = campaignName;
        this.scale = scale;
        this.hub = hub;
    }

    /** The campaign's star map changed. */
    void map(StarMap changed) {
        map.show(changed);
        LocalDate before = today;
        today = changed.date();
        if (scrubber != null) {
            int year = today.getYear();
            boolean onToday = before == null || scrubber.year() == before.getYear();
            scrubber.range(FIRST_YEAR, Math.max(LAST_YEAR, year), year,
                  TimeScrubber.bands(Eras.getInstance().getEras(), FIRST_YEAR, Math.max(LAST_YEAR, year)));
            if (onToday) {
                scrubber.set(year, before != null);
            }
            era();
        }
        if (mapVoyage != null) {
            StarMap.Voyage voyage = changed.voyage();
            mapVoyage.setText(voyage == null ? "" : voyage(voyage));
            mapVoyage.setVisible(voyage != null);
        }
        // The course to the chosen system changes as the days pass and the force sets out
        if (mapOpen && detailId != null) {
            hub.system(detailId);
        }
    }

    /** The detail of a system the player chose. */
    void detail(StarMap.Detail read) {
        StarMap.Star chosen = map.chosen();
        if (mapOpen && chosen != null && chosen.id().equals(read.id()) && read.id().equals(detailId)) {
            StarMap.Course course = read.course();
            map.preview(course == null || course.state() == StarMap.Departure.UNDERWAY ? null : course.route());
            fillDetail(chosen, read);
        }
    }

    private static String voyage(StarMap.Voyage voyage) {
        return getFormattedText("HqHub.map.voyage", voyage.destination(),
              getFormattedText("HqHub.map.jumpsLeft", voyage.jumps()),
              getFormattedText("HqHub.map.daysLeft", voyage.days()),
              getText("HqHub.map.stage." + voyage.stage().name()));
    }

    /** The campaign's top bar changed. */
    void show(TopBar bar) {
        pending = bar;
    }

    /** The screens the campaign has changed. */
    void screens(Set<MHQTabType> shown) {
        screens = Set.copyOf(shown);
        if (flyout != null && flyout.isVisible()) {
            flyout.cancel();
        }
    }

    /** The palette's entries, gathered as it opened. */
    void palette(List<PaletteEntry> gathered) {
        if (paletteLayer != null && paletteLayer.isVisible()) {
            entries = gathered;
            refilter();
        }
    }

    /** Any thread: whether a modal Swing dialog holds the input. */
    void blocked(boolean blocked) {
        this.blocked = blocked;
    }

    @Override
    public void create() {
        holo.create();
        map.create();
        theme = new UiTheme(Configuration.fontsDir());
        ui = new UiKit(theme.skin);
        labels = new StarLabels(theme);
        stage = new Stage(new ScreenViewport());
        stage.addActor(layout());
        stage.addActor(legendLayer());
        stage.addActor(timeLayer());
        stage.addActor(compassLayer());
        stage.addActor(detailLayer());
        stage.addActor(header());
        stage.addActor(searchPanel());
        stage.addActor(hoverCard());
        stage.addActor(rail());
        flyout = new UiPopover(ui, () -> flyoutArea = null, UiTheme.TOKEN);
        flyoutList = new UiMenuList(ui, UiTheme.TOKEN_MENU_ROW);
        stage.addActor(flyout);
        stage.addActor(paletteLayer());
        InputAdapter keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return HubScreen.this.keyDown(keycode);
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(blocker(), stage, keys, view()));
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /** The shown view's own controls: the map's, or the table's camera; while the map opens or closes, a skip. */
    private InputProcessor view() {
        InputProcessor skipper = new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                skip();
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                return true;
            }
        };
        return new InputProcessor() {
            private InputProcessor shown() {
                if (moving()) {
                    return skipper;
                }
                return mapOpen ? map.input() : holo.input();
            }

            @Override
            public boolean keyDown(int keycode) {
                return shown().keyDown(keycode);
            }

            @Override
            public boolean keyUp(int keycode) {
                return shown().keyUp(keycode);
            }

            @Override
            public boolean keyTyped(char character) {
                return shown().keyTyped(character);
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                // A click on the map leaves the search
                if (stage.getKeyboardFocus() == search.field) {
                    stage.setKeyboardFocus(null);
                }
                return shown().touchDown(screenX, screenY, pointer, button);
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                return shown().touchUp(screenX, screenY, pointer, button);
            }

            @Override
            public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
                return shown().touchCancelled(screenX, screenY, pointer, button);
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                return shown().touchDragged(screenX, screenY, pointer);
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                return shown().mouseMoved(screenX, screenY);
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                return shown().scrolled(amountX, amountY);
            }
        };
    }

    /** Swallows every input while a modal Swing dialog is open, as the dialog's Swing owner would. */
    private InputAdapter blocker() {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return blocked;
            }

            @Override
            public boolean keyUp(int keycode) {
                return blocked;
            }

            @Override
            public boolean keyTyped(char character) {
                return blocked;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                return blocked;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                return blocked;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                return blocked;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                return blocked;
            }
        };
    }

    /** The keys the stage left: Esc, the palette's Ctrl+K and the areas' 1 to 7. */
    private boolean keyDown(int keycode) {
        if (paletteLayer.isVisible() || UiModal.active(stage) != null
              || stage.getKeyboardFocus() instanceof TextField) {
            return false;
        }
        if (keycode == Input.Keys.ESCAPE) {
            if (moving()) {
                skip();
            } else if (flyout.isVisible()) {
                flyout.back();
            } else if (mapOpen && map.chosen() != null) {
                map.clearChoice();
            } else if (mapOpen) {
                showMap(false);
            } else {
                hub.go(new PaletteEntry.Classic());
            }
            return true;
        }
        if (keycode == Input.Keys.K && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
              || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))) {
            openPalette();
            return true;
        }
        if (mapOpen && !moving() && timeKey(keycode)) {
            return true;
        }
        for (HubArea area : HubArea.values()) {
            if (keycode == Input.Keys.NUM_0 + area.hotkey() || keycode == Input.Keys.NUMPAD_0 + area.hotkey()) {
                choose(area);
                return true;
            }
        }
        return false;
    }

    private Table layout() {
        float edge = space("xxl");
        Table root = new Table();
        hubLayout = root;
        root.setFillParent(true);
        root.pad(edge);

        Table name = new Table();
        name.add(ui.caption(getText("HqHub.overline"), true)).left().row();
        name.add(ui.roleTypeLabel("headline", campaignName, "text.primary")).left();

        Table status = ui.panel(true);
        status.defaults().right();
        date = ui.roleTypeLabel("title", "", "text.primary");
        where = ui.label("", "hud-body", 14, UiTheme.tokenColor("text.secondary"));
        funds = ui.roleTypeLabel("name", "", "accent.primary");
        Table fundsRow = new Table();
        fundsRow.add(ui.caption(getText("HqHub.funds"), true)).padRight(space("md"));
        fundsRow.add(funds);
        alerts = new Table();
        status.add(date).row();
        status.add(where).padTop(space("xs")).row();
        status.add(fundsRow).padTop(space("md")).row();
        status.add(alerts).padTop(space("sm"));

        root.add(name).expandX().left().top();
        root.add(status).right().top();
        root.row();
        root.add().colspan(2).expand();
        root.row();

        UiButton back = ui.button("button-secondary", null, getText("HqHub.classic"), null);
        UiKit.onChange(back, () -> hub.go(new PaletteEntry.Classic()));
        Table leave = new Table();
        leave.add(back);
        leave.add(ui.caption(getText("HqHub.escape"), true)).padLeft(space("md"));
        root.add(leave).left().bottom();
        Label controls = ui.caption(getText("HqHub.controls"), true);
        controls.setAlignment(Align.right);
        root.add(controls).right().bottom();
        return root;
    }

    /**
     * The map's header across the top: the way back to the command post, where the force is and today, and the
     * search for a system.
     */
    private Table header() {
        header = new Table();
        header.setFillParent(true);
        header.top().pad(space("xxl"));
        Table panel = ui.panel(true);
        headerPanel = panel;
        UiButton back = ui.button("hud-utility", "chevron-left", getText("HqHub.map.back"),
              getText("HqHub.map.backKey"));
        UiKit.onChange(back, () -> showMap(false));
        panel.add(back).padRight(space("lg"));
        Table title = new Table();
        title.defaults().left();
        title.add(ui.caption(getText("HqHub.map.overline"), true)).row();
        mapWhere = ui.roleTypeLabel("headline", "", "text.primary");
        title.add(mapWhere).row();
        mapDate = ui.label("", "hud-body", 14, UiTheme.tokenColor("text.secondary"));
        title.add(mapDate).row();
        mapVoyage = ui.label("", "hud-body", 14, UiTheme.tokenColor("text.primary"));
        mapVoyage.setVisible(false);
        title.add(mapVoyage);
        panel.add(title).left();
        Label controls = ui.caption(getText("HqHub.map.controls"), true);
        controls.setAlignment(Align.right);
        controls.setWrap(true);
        panel.add(controls).growX().right().padLeft(space("xl")).minWidth(0);
        search = ui.search(getText("HqHub.map.search"), true);
        UiKit.onChange(search.field, this::refind);
        panel.add(search).width(SEARCH_WIDTH).padLeft(space("lg"));
        header.add(panel).growX();
        // Ahead of the field: the results' keys, and Esc to clear the search and then leave it
        header.addCaptureListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (stage.getKeyboardFocus() != search.field) {
                    return false;
                }
                switch (keycode) {
                    case Input.Keys.ESCAPE -> {
                        if (search.field.getText().isEmpty()) {
                            stage.setKeyboardFocus(null);
                        } else {
                            search.field.setText("");
                            refind();
                        }
                    }
                    case Input.Keys.UP, Input.Keys.DOWN, Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER ->
                          searchResults.keyDown(keycode);
                    default -> {
                        return false;
                    }
                }
                event.stop();
                return true;
            }
        });
        return header;
    }

    /** The systems matching the search, under its field. */
    private Table searchPanel() {
        searchPanel = ui.panel(true);
        searchResults = new UiMenuList(ui, UiTheme.TOKEN_MENU_ROW);
        searchPanel.add(searchResults).growX();
        searchPanel.setVisible(false);
        return searchPanel;
    }

    /** Lists the systems that best match the search, the first highlighted for Enter. */
    private void refind() {
        searchResults.clearChildren();
        String query = search.field.getText();
        if (query.isBlank()) {
            searchPanel.setVisible(false);
            return;
        }
        List<StarMap.Star> found = StarMapView.search(map.stars(), query, FOUND);
        if (found.isEmpty()) {
            ui.empty(searchResults, getText("HqHub.map.noMatch"), true);
        }
        UiButton first = null;
        for (StarMap.Star star : found) {
            UiButton row = searchResults.item(star.name(), owner(star.owner()), null, false, true, () -> find(star));
            first = first == null ? row : first;
        }
        if (first != null) {
            searchResults.highlight(first);
        }
        searchPanel.setVisible(true);
        searchPanel.pack();
    }

    /** Flies to the system the search found and chooses it, leaving the search. */
    private void find(StarMap.Star star) {
        search.field.setText("");
        refind();
        stage.setKeyboardFocus(null);
        map.focus(star);
    }

    /** The factions holding the systems in view, at the bottom left. */
    private Table legendLayer() {
        legendLayer = new Table();
        legendLayer.setFillParent(true);
        legendLayer.bottom().left().pad(space("xxl"));
        legend = ui.panel(true);
        legend.setTouchable(Touchable.disabled);
        legendLayer.add(legend).minWidth(220);
        return legendLayer;
    }

    /** Recounts the legend's factions; rebuilds it only when they changed. */
    private void refreshLegend() {
        List<StarMapView.Legend> counted = map.legend(LEGEND);
        if (counted.equals(legendShown)) {
            return;
        }
        legendShown = counted;
        legend.clearChildren();
        legend.defaults().left();
        legend.add(ui.caption(getText("HqHub.map.legend"), true)).colspan(3).padBottom(space("xs")).row();
        for (StarMapView.Legend faction : counted) {
            float swatch = space("sm");
            legend.add(ui.solid(colour(faction.rgb()))).size(swatch).padRight(space("sm"));
            legend.add(ui.label(faction.name(), "hud-body", 13, lifted(faction.rgb()))).expandX().padTop(space("xxs"));
            legend.add(ui.label(String.valueOf(faction.systems()), "hud-body", 13,
                  UiTheme.tokenColor("text.secondary"))).right().padLeft(space("md")).padTop(space("xxs")).row();
        }
        legend.setVisible(!counted.isEmpty());
    }

    /**
     * The timeline across the bottom: the year the map shows, large, and its era; the way back to the campaign's own
     * year; and the timeline itself.
     */
    private Table timeLayer() {
        timeLayer = new Table();
        timeLayer.setFillParent(true);
        timeLayer.bottom().pad(space("xxl"));
        timeLayer.setTouchable(Touchable.childrenOnly);
        timePanel = ui.panel(true);
        timePanel.setTouchable(Touchable.enabled);
        // The panel takes its own clicks, so they don't choose the systems behind it
        timePanel.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        });
        Table readout = new Table();
        readout.defaults().left();
        readout.add(ui.caption(getText("HqHub.map.timeline"), true)).row();
        timeYear = ui.roleTypeLabel("headline", "", "accent.primary");
        readout.add(timeYear);
        timeEra = ui.label("", "hud-body", 14, UiTheme.tokenColor("text.secondary"));
        timeEra.setEllipsis(true);
        todayButton = ui.button("hud-utility", null, getText("HqHub.map.today"), getText("HqHub.map.todayKey"));
        UiKit.onChange(todayButton, this::backToToday);
        Table top = new Table();
        top.add(readout).left().bottom();
        top.add(timeEra).growX().left().bottom().padLeft(space("lg")).padBottom(space("xxs")).minWidth(0);
        top.add(todayButton).right().bottom().padLeft(space("lg"));
        scrubber = new TimeScrubber(ui, year -> era());
        timePanel.add(top).growX().row();
        timePanel.add(scrubber).growX().padTop(space("sm"));
        timeLayer.add(timePanel).width(TIMELINE_WIDTH);
        return timeLayer;
    }

    /** The timeline's keys: [ and ] a year back and on, with Shift a decade, and T back to the campaign's year. */
    private boolean timeKey(int keycode) {
        if (scrubber == null || today == null) {
            return false;
        }
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
              || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        int step = shift ? 10 : 1;
        switch (keycode) {
            case Input.Keys.LEFT_BRACKET -> scrubber.move(scrubber.year() - step);
            case Input.Keys.RIGHT_BRACKET -> scrubber.move(scrubber.year() + step);
            case Input.Keys.T -> backToToday();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Takes the timeline, and with it the map, back to the campaign's year. */
    private void backToToday() {
        if (today != null && scrubber.year() != today.getYear()) {
            scrubber.move(today.getYear());
        }
    }

    /** Shows the map on the timeline's year, on the campaign's day of it; the campaign's own date in its own year. */
    private void era() {
        if (today == null) {
            return;
        }
        boolean history = scrubber.year() != today.getYear();
        map.era(history ? today.withYear(scrubber.year()) : null);
        todayButton.setDisabled(!history);
        writeMapDate();
    }

    /** The map's date in its header: the campaign's, or the year in history the map shows and the campaign's. */
    private void writeMapDate() {
        if (mapDate == null) {
            return;
        }
        boolean history = today != null && scrubber != null && scrubber.year() != today.getYear();
        mapDate.setText(history ? getFormattedText("HqHub.map.history", scrubber.year(), todayText) : todayText);
        mapDate.setColor(UiTheme.tokenColor(history ? "accent.primary" : "text.secondary"));
    }

    /**
     * Keeps the timeline's readout on the year its handle glides through, pulses the handle while the map charts the
     * year's borders, and once it has them reads the years either side ahead.
     */
    private void followTimeline() {
        int year = Math.round(scrubber.shown());
        if (year != readoutYear) {
            readoutYear = year;
            timeYear.setText(String.valueOf(year));
            timeEra.setText(scrubber.era());
        }
        LocalDate shown = map.shownDate();
        boolean charted = shown != null && shown.getYear() == scrubber.year();
        scrubber.charting(!charted);
        if (charted && today != null && !shown.equals(readAhead)) {
            readAhead = shown;
            for (int step : new int[] { 1, -1 }) {
                int near = shown.getYear() + step;
                if (near >= FIRST_YEAR && near != today.getYear()) {
                    history.prefetch(today.withYear(near));
                }
            }
        }
    }

    /** The compass, its needle at coreward, and the map's controls, at the bottom right. */
    private Table compassLayer() {
        compassLayer = new Table();
        compassLayer.setFillParent(true);
        compassLayer.bottom().right().pad(space("xxl"));
        compassLayer.setTouchable(Touchable.disabled);
        Table panel = ui.panel(true);
        compass = new Container<>(ui.icon("arrow-up", 28, UiTheme.tokenColor("accent.primary")));
        compass.setTransform(true);
        compass.size(28);
        Table rose = new Table();
        rose.add(compass).size(28).row();
        rose.add(ui.caption(getText("HqHub.map.coreward"), true)).padTop(space("xxs"));
        panel.add(rose).padRight(space("lg"));
        Table scale = new Table();
        scaleBar = ui.solid(UiTheme.tokenColor("accent.primary"));
        scaleText = ui.label("", "hud-body", 13, UiTheme.tokenColor("text.secondary"));
        scale.add(scaleText).right().row();
        scaleCell = scale.add(scaleBar).height(2).width(SCALE_WIDTH).right().padTop(space("xxs"));
        panel.add(scale).width(SCALE_WIDTH).bottom();
        compassLayer.add(panel);
        return compassLayer;
    }

    /** Sizes the scale bar to the roundest distance that fits it at the middle of the screen. */
    private void placeScale() {
        float pixels = map.pixelsPerLightYear() * stage.getWidth() / Math.max(1, Gdx.graphics.getWidth());
        if (pixels <= 0) {
            return;
        }
        int wanted = 1;
        for (int step : SCALE_STEPS) {
            if (step * pixels <= SCALE_WIDTH) {
                wanted = step;
            }
        }
        float width = Math.max(4, wanted * pixels);
        if (wanted != scaleShown || Math.abs(scaleCell.getPrefWidth() - width) > 0.5f) {
            scaleShown = wanted;
            scaleText.setText(getFormattedText("HqHub.map.lightYears", wanted));
            scaleCell.width(width);
            scaleCell.getTable().invalidateHierarchy();
        }
    }

    /** The chosen system's detail, at the right edge below the header. */
    private Table detailLayer() {
        detailLayer = new Table();
        detailLayer.setFillParent(true);
        detailLayer.top().right().padRight(space("xxl")).padTop(space("xxl") * 4);
        detail = ui.panel(true);
        detail.setTouchable(Touchable.enabled);
        // The panel takes its own clicks, so they don't choose the systems behind it
        detail.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        });
        detailLayer.add(detail).width(DETAIL_WIDTH);
        detailLayer.setVisible(false);
        return detailLayer;
    }

    /** The name and owners of the system under the cursor, beside it. */
    private Table hoverCard() {
        hoverCard = ui.panel(true);
        hoverCard.setTouchable(Touchable.disabled);
        hoverName = ui.roleTypeLabel("name", "", "text.primary");
        hoverOwner = ui.label("", "hud-body", 13, UiTheme.tokenColor("text.secondary"));
        hoverCard.defaults().left();
        hoverCard.add(hoverName).row();
        hoverCard.add(hoverOwner).padTop(space("xxs"));
        hoverCard.setVisible(false);
        return hoverCard;
    }

    /**
     * Starts the dive from the table into the star map, or back out of it; the camera first flies the map back to the
     * view it opened with.
     */
    private void showMap(boolean open) {
        if (open == mapOpen) {
            return;
        }
        mapOpen = open;
        if (open) {
            if (flyout.isVisible()) {
                flyout.cancel();
            }
            if (progress == 0) {
                map.snapToStart();
            }
            if (today != null) {
                scrubber.set(today.getYear(), false);
                era();
            }
            returning = false;
        } else {
            map.clearChoice();
            hover(null);
            search.field.setText("");
            refind();
            if (stage.getKeyboardFocus() == search.field) {
                stage.setKeyboardFocus(null);
            }
            returning = progress >= 1;
            if (returning) {
                map.returnToStart();
            }
        }
        hub.mapShown(open);
    }

    /** Whether the map is opening or closing. */
    private boolean moving() {
        return mapOpen ? progress < 1 : progress > 0;
    }

    /** Ends the opening or closing at once. */
    private void skip() {
        if (mapOpen) {
            progress = map.loaded() ? 1 : Math.max(progress, FADE_START);
        } else {
            map.snapToStart();
            returning = false;
            progress = 0;
        }
    }

    /** Moves the opening or closing {@code delta} seconds on, and the chrome with it. */
    private void advance(float delta) {
        if (mapOpen) {
            // The fade into the map waits for the map to load
            float limit = map.loaded() ? 1 : FADE_START;
            progress = Math.max(progress, Math.min(limit, progress + delta / OPEN_SECONDS));
        } else if (returning) {
            returning = !map.settled() || chromeShown > 0;
        } else {
            progress = Math.max(0, progress - delta / OPEN_SECONDS);
        }
        float wanted = mapOpen && progress >= 1 ? 1 : 0;
        chromeShown = wanted > chromeShown ? Math.min(wanted, chromeShown + delta * 4)
              : Math.max(wanted, chromeShown - delta * 6);
    }

    /** Slides the chrome in from the edges as far as it has come, and fades the hub's own out as the camera dives. */
    private void placeChrome() {
        float in = ease(chromeShown);
        float slide = (1 - in) * CHROME_SLIDE;
        header.setPosition(0, slide);
        legendLayer.setPosition(-slide, 0);
        compassLayer.setPosition(slide, 0);
        detailLayer.setPosition(slide, 0);
        timeLayer.setPosition(0, -slide);
        for (Table layer : new Table[] { header, legendLayer, compassLayer, detailLayer, timeLayer }) {
            layer.getColor().a = in;
        }
        boolean shown = chromeShown > 0;
        header.setVisible(shown);
        legendLayer.setVisible(shown);
        compassLayer.setVisible(shown);
        timeLayer.setVisible(shown);
        header.setTouchable(chromeShown >= 1 ? Touchable.childrenOnly : Touchable.disabled);
        timeLayer.setTouchable(chromeShown >= 1 ? Touchable.childrenOnly : Touchable.disabled);
        // The timeline keeps clear of the legend and the compass either side of it
        float beside = Math.max(legend.getWidth(), compassLayer.getChildren().first().getWidth()) + space("xxl") * 2;
        float timeWidth = Math.max(TIMELINE_MIN_WIDTH, Math.min(TIMELINE_WIDTH,
              stage.getWidth() - beside * 2 - space("lg") * 2));
        Cell<?> timeCell = timeLayer.getCell(timePanel);
        if (timeCell.getPrefWidth() != timeWidth) {
            timeCell.width(timeWidth);
            timeLayer.invalidate();
        }
        searchPanel.setVisible(shown && searchPanel.isVisible() && !search.field.getText().isBlank());
        if (searchPanel.isVisible()) {
            Vector2 corner = search.localToStageCoordinates(new Vector2(0, -space("xs")));
            searchPanel.setWidth(Math.max(search.getWidth(), searchPanel.getPrefWidth()));
            searchPanel.setHeight(searchPanel.getPrefHeight());
            searchPanel.setPosition(Math.round(corner.x), Math.round(corner.y - searchPanel.getHeight()));
        }
        compass.setOrigin(compass.getWidth() / 2, compass.getHeight() / 2);
        compass.setRotation(-map.yaw());
        placeScale();
        float below = headerPanel.getHeight() + space("xxl") + space("lg");
        if (detailLayer.getPadTop() != below) {
            detailLayer.padTop(below);
        }

        float hubFade = 1 - Math.min(1, progress / (DIVE_END * 0.4f));
        hubLayout.getColor().a = hubFade;
        hubLayout.setVisible(hubFade > 0);
        // The rail gives the map the whole screen: it slides out left; its keys still work
        float railIn = ease(hubFade);
        railLayer.setPosition(-(1 - railIn) * CHROME_SLIDE * 2, 0);
        railLayer.getColor().a = railIn;
        railLayer.setVisible(railIn > 0);
        railLayer.setTouchable(railIn >= 1 ? Touchable.childrenOnly : Touchable.disabled);
    }

    private static float ease(float t) {
        float clamped = Math.max(0, Math.min(1, t));
        return clamped * clamped * (3 - 2 * clamped);
    }

    private void hover(@Nullable StarMap.Star star) {
        if (star == null || !mapOpen) {
            hoverCard.setVisible(false);
            return;
        }
        hoverName.setText(ui.cased("name", star.name()));
        hoverOwner.setText(owner(star.owner()));
        hoverOwner.setColor(lifted(star.rgb()));
        hoverCard.pack();
        hoverCard.setVisible(true);
    }

    /** Opens the chosen system's detail and asks the hub for the rest of it; none closes the panel. */
    private void chooseSystem(@Nullable StarMap.Star star) {
        map.preview(null);
        if (star == null) {
            detailId = null;
            detailLayer.setVisible(false);
            return;
        }
        detailId = star.id();
        fillDetail(star, null);
        detailLayer.setVisible(true);
        hub.system(star.id());
    }

    /** Fills the detail panel for {@code star}; {@code read} null while the hub reads it. */
    private void fillDetail(StarMap.Star star, @Nullable StarMap.Detail read) {
        detail.clearChildren();
        detail.defaults().left().growX();
        detail.add(ui.caption(getText("HqHub.map.system"), true)).row();
        Label name = ui.roleTypeLabel("headline", star.name(), "text.primary");
        name.setWrap(true);
        detail.add(name).padTop(space("xs")).row();
        Label owner = ui.label(owner(star.owner()), "hud-body", 14, lifted(star.rgb()));
        owner.setWrap(true);
        detail.add(owner).padTop(space("xxs")).row();
        Table facts = new Table();
        facts.defaults().padTop(space("sm"));
        if (read == null) {
            ui.empty(facts, getText("HqHub.map.reading"), true);
        } else {
            fact(facts, "HqHub.map.population", read.population() > 0 ? population(read.population())
                  : getText("HqHub.map.uninhabited"));
            fact(facts, "HqHub.map.hpg", read.hpg());
            fact(facts, "HqHub.map.star", read.star().isBlank() ? getText("HqHub.map.unknown") : read.star());
            fact(facts, "HqHub.map.recharge", getFormattedText("HqHub.map.hours", Math.round(read.recharge())));
            if (read.here()) {
                fact(facts, "HqHub.map.jumps", getText("HqHub.map.here"));
            } else {
                fact(facts, "HqHub.map.distance", getFormattedText("HqHub.map.lightYears",
                      String.format(Locale.getDefault(), "%.1f", read.distance())));
                fact(facts, "HqHub.map.jumps", read.jumps() < 0 ? getText("HqHub.map.noRoute")
                      : getFormattedText("HqHub.map.jumpCount", read.jumps()));
            }
            StarMap.Course course = read.course();
            if (course != null) {
                fact(facts, "HqHub.map.transit", getFormattedText("HqHub.map.days", course.days()));
                if (course.recharge() > 0) {
                    fact(facts, "HqHub.map.charging", getFormattedText("HqHub.map.hours",
                          Math.round(course.recharge())));
                }
                if (!course.cost().isBlank()) {
                    fact(facts, "HqHub.map.cost", course.cost());
                }
            }
        }
        detail.add(facts).padTop(space("sm")).row();
        Table actions = new Table();
        actions.left();
        StarMap.Course course = read == null ? null : read.course();
        if (course != null) {
            UiButton go = ui.button("button-primary", null, getText("HqHub.map.setCourse"), null);
            go.setDisabled(course.state() != StarMap.Departure.READY);
            UiKit.onChange(go, () -> {
                if (!go.isDisabled()) {
                    go.setDisabled(true);
                    hub.travel(read.id());
                }
            });
            actions.add(go).padRight(space("sm"));
        }
        UiButton plot = ui.button("button-secondary", null, getText("HqHub.map.navigation"), null);
        UiKit.onChange(plot, () -> hub.go(new PaletteEntry.Screen(MHQTabType.NAVIGATION)));
        actions.add(plot);
        detail.add(actions).padTop(space("md")).row();
        if (course != null && course.state() != StarMap.Departure.READY) {
            Label why = ui.caption(getText("HqHub.map.departure." + course.state().name()), true);
            why.setWrap(true);
            detail.add(why).padTop(space("xs")).row();
        }
        detail.add(ui.caption(getText("HqHub.map.close"), true)).padTop(space("sm"));
    }

    private void fact(Table facts, String key, String value) {
        facts.add(ui.caption(getText(key), true)).left().expandX();
        facts.add(ui.label(value, "hud-body", 14, UiTheme.tokenColor("text.primary"))).right();
        facts.row();
    }

    private static String owner(String owner) {
        return owner.isBlank() ? getText("HqHub.map.unclaimed") : owner;
    }

    /** Keeps the hover card beside the cursor, flipped inwards near the window's edges. */
    private void placeHoverCard() {
        if (!hoverCard.isVisible()) {
            return;
        }
        Vector2 cursor = stage.screenToStageCoordinates(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        float x = cursor.x + HOVER_OFFSET;
        float y = cursor.y - HOVER_OFFSET - hoverCard.getHeight();
        if (x + hoverCard.getWidth() > stage.getWidth()) {
            x = cursor.x - HOVER_OFFSET - hoverCard.getWidth();
        }
        if (y < 0) {
            y = cursor.y + HOVER_OFFSET;
        }
        hoverCard.setPosition(Math.round(x), Math.round(y));
    }

    private static String population(long people) {
        NumberFormat format = NumberFormat.getCompactNumberInstance(Locale.getDefault(), NumberFormat.Style.SHORT);
        format.setMaximumFractionDigits(1);
        return format.format(people);
    }

    /** An owner's colour lifted towards white, so a dark one still reads on the panels. */
    private static Color lifted(int rgb) {
        return colour(rgb).lerp(Color.WHITE, 0.45f);
    }

    private static Color colour(int rgb) {
        return new Color(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1);
    }

    /** The left rail: the palette's search, then the campaign's areas with their hotkeys. */
    private Table rail() {
        Table column = new Table();
        railLayer = column;
        column.setFillParent(true);
        column.left().padLeft(space("xxl"));
        Table panel = ui.panel(true);
        panel.defaults().growX().pad(space("xxs"));
        UiButton search = ui.button("hud-utility", "search", getText("HqHub.palette.open"),
              getText("HqHub.palette.shortcut"));
        UiKit.onChange(search, this::openPalette);
        panel.add(search).padBottom(space("sm")).row();
        for (HubArea area : HubArea.values()) {
            UiButton button = ui.button("hud-utility", area.icon, getText("HqHub.area." + area.name()),
                  String.valueOf(area.hotkey()));
            UiKit.onChange(button, () -> choose(area));
            rail.put(area, button);
            panel.add(button).row();
        }
        column.add(panel);
        return column;
    }

    /** Opens an area's only screen, or its flyout of screens; choosing the open flyout's area again closes it. */
    private void choose(HubArea area) {
        if (area == HubArea.MAP) {
            if (flyout.isVisible()) {
                flyout.cancel();
            }
            showMap(!mapOpen);
            return;
        }
        List<MHQTabType> tabs = area.tabs(screens);
        boolean wasOpen = flyout.isVisible() && flyoutArea == area;
        if (flyout.isVisible()) {
            flyout.cancel();
        }
        if (tabs.size() == 1) {
            hub.go(new PaletteEntry.Screen(tabs.get(0)));
        } else if (!tabs.isEmpty() && !wasOpen) {
            flyoutList.clearChildren();
            UiButton first = null;
            for (MHQTabType tab : tabs) {
                UiButton row = flyoutList.item(tab.toString(), null, null, false, true, () -> {
                    flyout.cancel();
                    hub.go(new PaletteEntry.Screen(tab));
                });
                first = first == null ? row : first;
            }
            UiButton button = rail.get(area);
            Vector2 corner = button.localToStageCoordinates(new Vector2(button.getWidth() + space("md"),
                  button.getHeight()));
            flyout.header(getText("HqHub.area." + area.name()), null).content(flyoutList).openedFrom(button);
            flyout.showAt(corner.x, corner.y);
            flyoutArea = area;
            flyoutList.highlight(first);
        }
    }

    /** The palette: a search field over its best matches, near the top of a dimmed window. */
    private UiModal paletteLayer() {
        paletteLayer = new UiModal(ui, this::closePalette);
        paletteLayer.setFillParent(true);
        paletteLayer.top().padTop(space("xxl") * 3);
        paletteField = ui.search(getText("HqHub.palette.placeholder"), true);
        results = new UiMenuList(ui, UiTheme.TOKEN_MENU_ROW);
        UiKit.onChange(paletteField.field, this::refilter);
        Table panel = ui.panel(true);
        panel.add(paletteField).growX().row();
        panel.add(results).growX().padTop(space("sm"));
        paletteLayer.add(panel).width(PALETTE_WIDTH);
        // Ahead of the field: the list's keys and Esc, which the field would otherwise take
        paletteLayer.addCaptureListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                switch (keycode) {
                    case Input.Keys.ESCAPE -> closePalette();
                    case Input.Keys.UP, Input.Keys.DOWN, Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER ->
                          results.keyDown(keycode);
                    default -> {
                        return false;
                    }
                }
                event.stop();
                return true;
            }
        });
        return paletteLayer;
    }

    private void openPalette() {
        if (flyout.isVisible()) {
            flyout.cancel();
        }
        entries = null;
        paletteField.field.setText("");
        refilter();
        paletteLayer.open(true);
        stage.setKeyboardFocus(paletteField.field);
        paletteLayer.focus();
        hub.palette();
    }

    private void closePalette() {
        paletteLayer.open(false);
        entries = null;
    }

    /** Lists the entries that best match the field, the first one highlighted for Enter. */
    private void refilter() {
        results.clearChildren();
        List<PaletteEntry> gathered = entries;
        if (gathered == null) {
            ui.empty(results, getText("HqHub.palette.loading"), true);
            return;
        }
        List<PaletteEntry> found = PaletteSearch.find(gathered, paletteField.field.getText(), RESULTS);
        if (found.isEmpty()) {
            ui.empty(results, getText("HqHub.palette.none"), true);
            return;
        }
        UiButton first = null;
        for (PaletteEntry entry : found) {
            String kind = getText("HqHub.palette.kind." + entry.kind().name());
            String detail = entry.detail().isBlank() ? kind : getFormattedText("HqHub.palette.detail", kind,
                  entry.detail());
            UiButton row = results.item(entry.label(), detail, null, false, true, () -> {
                closePalette();
                hub.go(entry.target());
            });
            if (first == null) {
                first = row;
            }
        }
        results.highlight(first);
    }

    private void apply(TopBar bar) {
        date.setText(ui.cased("title", bar.dateText()));
        where.setText(whereabouts(bar));
        mapWhere.setText(ui.cased("headline", whereabouts(bar)));
        todayText = bar.dateText();
        writeMapDate();
        funds.setText(ui.cased("name", bar.fundsText()));
        alerts.clearChildren();
        for (TopBar.Alert alert : bar.alerts()) {
            UiKit.Tone tone = alert == TopBar.Alert.SCENARIO_DUE ? UiKit.Tone.WARN : UiKit.Tone.BAD;
            alerts.add(ui.chip(getText("HqHub.alert." + alert.name()), tone, true)).padLeft(space("sm"));
        }
    }

    private static String whereabouts(TopBar bar) {
        return switch (bar.travel()) {
            case IN_TRANSIT -> bar.destination() == null ? getText("HqHub.inTransit")
                  : getFormattedText("HqHub.inTransitTo", bar.destination());
            case AT_JUMP_POINT -> getFormattedText("HqHub.atJumpPoint", bar.system());
            case ON_WORLD -> bar.system();
        };
    }

    @Override
    public void resize(int width, int height) {
        holo.resize(width, height);
        map.resize(width, height);
        if (stage != null && width > 0 && height > 0) {
            ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1 / scale);
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        if (pending != null) {
            apply(pending);
            pending = null;
        }
        advance(delta);
        if (progress >= 1) {
            map.render();
        } else {
            float fade = ease((progress - FADE_START) / (1 - FADE_START));
            holo.render(ease(progress / DIVE_END), fade);
            if (fade > 0) {
                map.render(fade, false);
            }
        }
        boolean hand = !mapOpen && progress == 0 && holo.hoveringMap();
        if (hand != handCursor) {
            handCursor = hand;
            Gdx.graphics.setSystemCursor(hand ? Cursor.SystemCursor.Hand : Cursor.SystemCursor.Arrow);
        }
        stage.getViewport().apply(true);
        if (progress >= 1) {
            labels.draw(map, stage);
            placeHoverCard();
            followTimeline();
            legendClock -= delta;
            if (legendClock <= 0) {
                legendClock = LEGEND_EVERY;
                refreshLegend();
            }
        }
        placeChrome();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void dispose() {
        hub.disposed();
        holo.dispose();
        map.dispose();
        if (labels != null) {
            labels.dispose();
        }
        if (stage != null) {
            stage.dispose();
        }
        if (theme != null) {
            theme.dispose();
        }
    }

    private static float space(String token) {
        return DesignTokens.shipped().space().get(token);
    }
}

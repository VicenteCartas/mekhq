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
import java.util.ArrayList;
import java.util.Arrays;
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
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
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
import mekhq.MHQConstants;
import mekhq.campaign.universe.eras.Eras;
import mekhq.gui.enums.MHQTabType;
import mekhq.gui.model.view.Borders;
import mekhq.gui.model.view.MapLens;
import mekhq.gui.model.view.StarMap;
import mekhq.gui.model.view.SystemDossier;
import mekhq.gui.model.view.TopBar;
import mekhq.gui.model.view.Transmission;

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
    private static final float DOSSIER_WIDTH = 460;
    /** The socio-industrial ratings, in the order a world's record lists them. */
    private static final List<String> INDUSTRY = List.of("tech", "industry", "materials", "output", "agriculture");
    private static final float SEARCH_WIDTH = 300;
    /** Seconds the cancel-trip button waits for its confirming second press. */
    private static final float CANCEL_WINDOW = 3;
    /** The most systems the map's search lists, and factions its legend. */
    private static final int FOUND = 8;
    private static final int LEGEND = 6;
    /** How often the legend recounts the factions on screen, in seconds. */
    private static final float LEGEND_EVERY = 0.3f;
    /** The colours of the borders lens's strip: a few of the great houses'. */
    private static final int[] BORDERS_STRIP = { 0xD5B53A, 0x3E8BD8, 0x2DA05A, 0xC23B3B, 0x8C55C8 };
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

        /** Abandons the course the force is following, leaving it where it is. */
        void cancelTrip();

        /** Reads the full record of the system {@code id}, for {@link HubScreen#dossier}. */
        void dossier(String id);

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
    /** The course across the header: the voyage underway, or the one plotted to the chosen system. */
    private Table routeStrip;
    private @Nullable StarMap.Voyage voyage;
    private List<String> voyageRoute = List.of();
    /** The chosen system's detail while it has a course to plot, not one underway. */
    private @Nullable StarMap.Detail plotted;
    /** Seconds left to confirm cancelling the trip; none when not asked. */
    private float cancelArmed;
    private Table helpPanel;
    private UiKit.SearchField search;
    private Table searchPanel;
    private UiMenuList searchResults;
    private Table legend;
    /** The lenses to look at the systems through, and the overlays, above the legend. */
    private Table lensPanel;
    private boolean lensesOpen;
    private @Nullable MapLens legendLens;
    private int[] legendLevels = new int[0];
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
    /** The chosen system's full record, beside its card, while asked for. */
    private Table dossierPanel;
    private boolean dossierOpen;
    private @Nullable SystemDossier dossierShown;
    private int dossierWorld;
    private UiButton dossierButton;
    private Table hoverCard;
    private Label hoverStats;
    private Table hoverPerks;
    private Table rulerCard;
    private UiPopover mapMenu;
    private UiMenuList mapMenuList;
    private Label rulerReading;
    private Label rulerJumps;
    /** Whether the hover card shows a realm rather than a system. */
    private boolean realmShown;
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
    /** The campaign's immersive dialogs, over everything else. */
    private final TransmissionView transmissions = new TransmissionView();
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

            @Override
            public void realm(@Nullable Borders.Holding held, @Nullable Borders.Holding rival) {
                hoverRealm(held, rival);
            }

            @Override
            public void context(@Nullable StarMap.Star star, double x, double y, int screenX, int screenY) {
                openMapMenu(star, x, y, screenX, screenY);
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
        if (routeStrip != null) {
            voyage = changed.voyage();
            voyageRoute = changed.route();
            writeRoute();
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
            boolean planned = course != null && course.state() != StarMap.Departure.UNDERWAY;
            map.preview(planned ? course.route() : null);
            plotted = planned ? read : null;
            writeRoute();
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

    /**
     * Any thread: shows an immersive dialog as a transmission over the hub, once those before it have gone.
     *
     * @return the handle the dialog updates and withdraws it by
     */
    Transmission.Handle transmit(Transmission transmission, Transmission.Channel channel) {
        return transmissions.offer(transmission, channel);
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
        stage.addActor(rulerCard());
        stage.addActor(hoverCard());
        stage.addActor(rail());
        flyout = new UiPopover(ui, () -> flyoutArea = null, UiTheme.TOKEN);
        flyoutList = new UiMenuList(ui, UiTheme.TOKEN_MENU_ROW);
        stage.addActor(flyout);
        mapMenu = new UiPopover(ui, () -> { }, UiTheme.TOKEN);
        mapMenuList = new UiMenuList(ui, UiTheme.TOKEN_MENU_ROW);
        stage.addActor(mapMenu);
        stage.addActor(paletteLayer());
        transmissions.create(theme, ui, scale);
        InputAdapter keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return HubScreen.this.keyDown(keycode);
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(blocker(), transmissions.input(), stage, keys, view()));
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
                if (mapOpen && !moving() && overChrome(screenX, screenY)) {
                    // Over the chrome the map points at nothing
                    map.point(-1, -1);
                    return false;
                }
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
            } else if (mapMenu.isVisible()) {
                mapMenu.cancel();
            } else if (mapOpen && map.clearRuler()) {
                rulerCard.setVisible(false);
            } else if (mapOpen && dossierOpen) {
                showDossier(false);
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
        if (mapOpen && keycode == Input.Keys.L) {
            cycleLens(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
            return true;
        }
        if (mapOpen && keycode == Input.Keys.H) {
            map.network(!map.network());
            rebuildLenses();
            return true;
        }
        if (mapOpen && keycode == Input.Keys.R) {
            map.admin(!map.admin());
            rebuildLenses();
            return true;
        }
        if (mapOpen && keycode == Input.Keys.E) {
            map.empty(!map.empty());
            rebuildLenses();
            return true;
        }
        if (mapOpen && keycode == Input.Keys.J) {
            map.reach(!map.reach());
            rebuildLenses();
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
        panel.add(title).left();
        routeStrip = new Table();
        panel.add(routeStrip).growX().padLeft(space("xl")).minWidth(0);
        UiButton help = ui.button("hud-icon", "help", null, null);
        UiKit.onChange(help, () -> helpPanel.setVisible(!helpPanel.isVisible()));
        panel.add(help).padLeft(space("lg"));
        search = ui.search(getText("HqHub.map.search"), true);
        UiKit.onChange(search.field, this::refind);
        panel.add(search).width(SEARCH_WIDTH).padLeft(space("lg"));
        header.add(panel).growX().row();
        helpPanel = ui.panel(true);
        helpPanel.setTouchable(Touchable.disabled);
        helpPanel.defaults().left();
        helpPanel.add(ui.caption(getText("HqHub.map.help"), true)).padBottom(space("xs")).row();
        for (String control : getText("HqHub.map.controls").split("\u00b7")) {
            helpPanel.add(ui.label(control.trim(), "hud-body", 13, UiTheme.tokenColor("text.primary")))
                  .padTop(space("xxs")).row();
        }
        // The key to the icons on the system cards
        helpPanel.add(ui.caption(getText("HqHub.map.key"), true)).padTop(space("md")).padBottom(space("xs")).row();
        String[][] icons = { { "group", "hall" }, { "book", "academies" }, { "star", "capital" },
              { "factory", "industry" }, { "power", "recharge" }, { "warn", "health" } };
        for (String[] icon : icons) {
            Color colour = UiTheme.tokenColor(icon[0].equals("star") ? "accent.primary"
                  : icon[0].equals("warn") ? "status.warning" : "text.primary");
            Table row = new Table();
            row.add(ui.icon(icon[0], 16, colour)).size(16).padRight(space("sm"));
            row.add(ui.label(getText("HqHub.map.key." + icon[1]), "hud-body", 13,
                  UiTheme.tokenColor("text.primary")));
            helpPanel.add(row).padTop(space("xxs")).row();
        }
        helpPanel.setVisible(false);
        header.add(helpPanel).right().padTop(space("sm")).padRight(SEARCH_WIDTH + space("lg"));
        writeRoute();
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
        lensPanel = ui.panel(true);
        lensPanel.setTouchable(Touchable.enabled);
        // The panel takes its own clicks, so they don't choose the systems behind it
        lensPanel.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        });
        rebuildLenses();
        legend = ui.panel(true);
        legend.setTouchable(Touchable.disabled);
        legendLayer.add(lensPanel).minWidth(220).fillX().row();
        legendLayer.add(legend).minWidth(220).fillX().padTop(space("md"));
        return legendLayer;
    }

    /**
     * Lays out the lens panel: the lens the map looks through, or all of them to choose from when it is open; and the
     * overlays to show over any lens.
     */
    private void rebuildLenses() {
        lensPanel.clearChildren();
        lensPanel.defaults().left().growX();
        Table head = new Table();
        head.add(ui.caption(getText("HqHub.map.lenses"), true)).expandX().left();
        head.add(ui.label(getText("HqHub.map.lensKey"), "hud-body", 12, UiTheme.tokenColor("text.secondary")));
        lensPanel.add(head).padBottom(space("xs")).row();
        MapLens active = map.lens();
        if (lensesOpen || active == null) {
            lensPanel.add(lensRow(null, active == null)).row();
        }
        for (MapLens lens : MapLens.offered()) {
            if (lensesOpen || lens == active) {
                lensPanel.add(lensRow(lens, lens == active)).row();
            }
        }
        lensPanel.add(ui.solid(UiTheme.tokenColor("text.secondary"))).height(1).padTop(space("xs"))
              .padBottom(space("xs")).getActor().getColor().a = 0.2f;
        lensPanel.row();
        lensPanel.add(choiceRow(null, getText("HqHub.map.layer.hpg"), getText("HqHub.map.layer.hpgKey"),
              map.network(), () -> {
                  map.network(!map.network());
                  rebuildLenses();
              })).row();
        lensPanel.add(choiceRow(null, getText("HqHub.map.layer.reach"), getText("HqHub.map.layer.reachKey"),
              map.reach(), () -> {
                  map.reach(!map.reach());
                  rebuildLenses();
              })).row();
        lensPanel.add(choiceRow(null, getText("HqHub.map.layer.admin"), getText("HqHub.map.layer.adminKey"),
              map.admin(), () -> {
                  map.admin(!map.admin());
                  rebuildLenses();
              })).row();
        lensPanel.add(choiceRow(null, getText("HqHub.map.layer.empty"), getText("HqHub.map.layer.emptyKey"),
              map.empty(), () -> {
                  map.empty(!map.empty());
                  rebuildLenses();
              })).row();
    }

    /** A lens's row, its palette strip before its name; choosing it when the panel is shut opens the panel. */
    private Table lensRow(@Nullable MapLens lens, boolean active) {
        Table strip = new Table();
        float wide = space("xl") + space("sm");
        float high = space("xs");
        if (lens == null) {
            for (int rgb : BORDERS_STRIP) {
                strip.add(ui.solid(colour(rgb))).size(wide / BORDERS_STRIP.length, high);
            }
        } else {
            for (int level = 1; level <= lens.levels(); level++) {
                strip.add(ui.solid(colour(lens.rgb(level)))).size(wide / lens.levels(), high);
            }
        }
        String name = getText(lens == null ? "HqHub.map.lens.borders" : "HqHub.map.lens." + lens.key());
        return choiceRow(strip, name, lensesOpen || !active ? "" : "\u25BE", active, () -> {
            if (lensesOpen) {
                map.lens(lens);
                lensesOpen = false;
            } else {
                lensesOpen = true;
            }
            rebuildLenses();
        });
    }

    /** A row of the lens panel: lit when it is {@code on}, brighter under the pointer, running {@code action}. */
    private Table choiceRow(@Nullable Table strip, String name, String key, boolean on, Runnable action) {
        Color accent = UiTheme.tokenColor("accent.primary");
        Drawable rest = ui.skin.newDrawable("white", on ? new Color(accent.r, accent.g, accent.b, 0.16f)
              : new Color(1, 1, 1, 0));
        Drawable over = ui.skin.newDrawable("white", on ? new Color(accent.r, accent.g, accent.b, 0.26f)
              : new Color(1, 1, 1, 0.07f));
        Table row = new Table();
        row.setBackground(rest);
        row.setTouchable(Touchable.enabled);
        row.pad(space("xxs"), space("sm"), space("xxs"), space("sm"));
        row.add(ui.solid(on ? accent : new Color(0, 0, 0, 0))).size(2, space("md")).padRight(space("sm"));
        if (strip != null) {
            row.add(strip).padRight(space("sm"));
        } else {
            row.add(ui.solid(on ? accent : UiTheme.tokenColor("text.secondary"))).size(space("xs"))
                  .padRight(space("sm"));
        }
        row.add(ui.label(name, "hud-body", 13, UiTheme.tokenColor(on ? "text.primary" : "text.secondary")))
              .expandX().left();
        row.add(ui.label(key, "hud-body", 12, UiTheme.tokenColor("text.secondary"))).padLeft(space("md"));
        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer,
                  Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                row.setBackground(over);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer,
                  Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                row.setBackground(rest);
            }
        });
        return row;
    }

    /** Looks through the next lens, or the one before; past the last, back at the owners. */
    private void cycleLens(boolean back) {
        List<MapLens> offered = MapLens.offered();
        MapLens active = map.lens();
        int at = active == null ? -1 : offered.indexOf(active);
        int next = Math.floorMod(at + 1 + (back ? -1 : 1), offered.size() + 1) - 1;
        map.lens(next < 0 ? null : offered.get(next));
        rebuildLenses();
    }

    /** Recounts the legend's factions; rebuilds it only when they changed. */
    private void refreshLegend() {
        MapLens lens = map.lens();
        if (lens != null) {
            refreshLensLegend(lens);
            return;
        }
        List<StarMapView.Legend> counted = map.legend(LEGEND);
        if (counted.equals(legendShown) && legendLens == null) {
            return;
        }
        legendLens = null;
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

    /** The legend under {@code lens}: its levels, highest first, and how many systems in view stand at each. */
    private void refreshLensLegend(MapLens lens) {
        int[] counts = map.lensCounts();
        if (lens == legendLens && Arrays.equals(counts, legendLevels)) {
            return;
        }
        legendLens = lens;
        legendLevels = counts;
        legendShown = List.of();
        String[] names = getText("HqHub.map.lens." + lens.key() + ".levels").split(",");
        legend.clearChildren();
        legend.defaults().left();
        legend.add(ui.caption(getText("HqHub.map.lens." + lens.key()), true)).colspan(3).padBottom(space("xs")).row();
        for (int level = lens.levels(); level >= 1; level--) {
            int rgb = lens.rgb(level);
            String name = level - 1 < names.length ? names[level - 1].trim() : String.valueOf(level);
            int count = level < counts.length ? counts[level] : 0;
            legend.add(ui.solid(colour(rgb))).size(space("sm")).padRight(space("sm"));
            legend.add(ui.label(name, "hud-body", 13, lifted(rgb))).expandX().padTop(space("xxs"));
            legend.add(ui.label(String.valueOf(count), "hud-body", 13, UiTheme.tokenColor("text.secondary")))
                  .right().padLeft(space("md")).padTop(space("xxs")).row();
        }
        Label columns = ui.label(getText("HqHub.map.lens.columns"), "hud-body", 12, UiTheme.tokenColor("text.secondary"));
        columns.setWrap(true);
        legend.add(columns).colspan(3).growX().width(200).padTop(space("sm")).row();
        legend.setVisible(true);
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
        mapDate.setText(history ? getFormattedText("HqHub.map.history", String.valueOf(scrubber.year()), todayText) : todayText);
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
        // The panels take their own clicks, so they don't choose the systems behind them
        InputListener keep = new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        };
        dossierPanel = ui.panel(true);
        dossierPanel.setTouchable(Touchable.enabled);
        dossierPanel.addListener(keep);
        dossierPanel.setVisible(false);
        detailLayer.add(dossierPanel).width(DOSSIER_WIDTH).top().padRight(space("lg"));
        detail = ui.panel(true);
        detail.setTouchable(Touchable.enabled);
        detail.addListener(keep);
        detailLayer.add(detail).width(DETAIL_WIDTH).top();
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
        hoverCard.add(hoverOwner).padTop(space("xxs")).row();
        hoverStats = ui.label("", "hud-body", 13, UiTheme.tokenColor("text.primary"));
        hoverStats.setVisible(false);
        hoverCard.add(hoverStats).padTop(space("xxs")).height(0).row();
        hoverPerks = new Table();
        hoverCard.add(hoverPerks);
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
        if (cancelArmed > 0) {
            cancelArmed -= delta;
            if (cancelArmed <= 0) {
                writeRoute();
            }
        }
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
        legendLayer.setTouchable(chromeShown >= 1 ? Touchable.childrenOnly : Touchable.disabled);
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

    /**
     * Whether the window's ({@code screenX}, {@code screenY}) lies over the map's chrome: anything the stage would
     * take, and the panels that only show things.
     */
    private boolean overChrome(int screenX, int screenY) {
        Vector2 at = stage.screenToStageCoordinates(new Vector2(screenX, screenY));
        if (stage.hit(at.x, at.y, true) != null) {
            return true;
        }
        for (Actor panel : new Actor[] { legend, compassLayer.getChildren().first() }) {
            if (panel.isVisible() && panel.getStage() != null && panel.getParent().isVisible()) {
                Vector2 local = panel.stageToLocalCoordinates(new Vector2(at));
                if (local.x >= 0 && local.y >= 0 && local.x < panel.getWidth() && local.y < panel.getHeight()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void hover(@Nullable StarMap.Star star) {
        if (star == null || !mapOpen || overChrome(Gdx.input.getX(), Gdx.input.getY())) {
            hoverCard.setVisible(false);
            return;
        }
        hoverName.setText(ui.cased("name", star.name()));
        hoverName.setColor(UiTheme.tokenColor("text.primary"));
        realmShown = false;
        hoverStats.setVisible(false);
        hoverCard.getCell(hoverStats).height(0);
        hoverOwner.setText(owner(star.owner()));
        hoverOwner.setColor(lifted(star.rgb()));
        hoverPerks.clearChildren();
        for (Perk perk : perks(star)) {
            UiKit.Icon icon = ui.icon(perk.icon(), 16, perk.colour());
            hoverPerks.add(icon).size(16).padRight(space("xs")).padTop(space("xs"));
        }
        hoverCard.pack();
        hoverCard.setVisible(true);
    }

    /** Shows what the realm under the cursor holds, in the hover card; none hides it. */
    private void hoverRealm(@Nullable Borders.Holding held, @Nullable Borders.Holding rival) {
        if (held == null || !mapOpen || overChrome(Gdx.input.getX(), Gdx.input.getY())) {
            if (realmShown) {
                realmShown = false;
                hoverStats.setVisible(false);
                hoverCard.setVisible(false);
            }
            return;
        }
        realmShown = true;
        hoverPerks.clearChildren();
        hoverName.setText(ui.cased("name", held.name()));
        hoverName.setColor(lifted(held.rgb()));
        hoverOwner.setText(getFormattedText("HqHub.map.realm.systems", held.systems()));
        hoverOwner.setColor(UiTheme.tokenColor("text.secondary"));
        StringBuilder more = new StringBuilder();
        if (rival != null) {
            hoverName.setText(ui.cased("name", getText("HqHub.map.contested")));
            hoverName.setColor(UiTheme.tokenColor("status.warning"));
            hoverOwner.setText(getFormattedText("HqHub.map.contested.between", held.name(), rival.name()));
            hoverOwner.setColor(UiTheme.tokenColor("text.primary"));
            more.append(getFormattedText("HqHub.map.contested.count", held.name(), held.systems())).append('\n')
                  .append(getFormattedText("HqHub.map.contested.count", rival.name(), rival.systems()));
            hoverStats.setText(more);
            hoverStats.setVisible(true);
            hoverCard.getCell(hoverStats).height(hoverStats.getPrefHeight());
            hoverCard.pack();
            hoverCard.setVisible(true);
            return;
        }
        if (held.people() > 0) {
            more.append(getFormattedText("HqHub.map.realm.people", population(held.people())));
        }
        if (!held.capital().isBlank()) {
            if (!more.isEmpty()) {
                more.append('\n');
            }
            more.append(getFormattedText("HqHub.map.realm.capital", held.capital()));
        }
        hoverStats.setText(more);
        hoverStats.setVisible(!more.isEmpty());
        hoverCard.getCell(hoverStats).height(more.isEmpty() ? 0 : hoverStats.getPrefHeight());
        hoverCard.pack();
        hoverCard.setVisible(true);
    }

    /** Opens the chosen system's detail and asks the hub for the rest of it; none closes the panel. */
    private void chooseSystem(@Nullable StarMap.Star star) {
        map.preview(null);
        plotted = null;
        writeRoute();
        if (star == null) {
            detailId = null;
            detailLayer.setVisible(false);
            showDossier(false);
            return;
        }
        detailId = star.id();
        fillDetail(star, null);
        detailLayer.setVisible(true);
        hub.system(star.id());
        if (dossierOpen) {
            showDossier(true);
        }
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
        List<Perk> perks = perks(star);
        if (!perks.isEmpty()) {
            Table chips = new Table();
            chips.left();
            for (int i = 0; i < perks.size(); i++) {
                Perk perk = perks.get(i);
                Table chip = new Table();
                chip.add(ui.icon(perk.icon(), 16, perk.colour())).size(16).padRight(space("xs"));
                chip.add(ui.label(perk.text(), "hud-body", 13, perk.colour()));
                chips.add(chip).left().padRight(space("md")).padTop(space("xs"));
                if (i % 2 == 1) {
                    chips.row();
                }
            }
            detail.add(chips).padTop(space("xs")).row();
        }
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
        dossierButton = ui.button("button-secondary", "info",
              getText(dossierOpen ? "HqHub.map.dossier.hide" : "HqHub.map.dossier.show"), null);
        UiKit.onChange(dossierButton, () -> showDossier(!dossierOpen));
        actions.add(dossierButton).padRight(space("sm"));
        UiButton plot = iconButton("tune", "HqHub.map.navigation");
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

    /** Something about a system that matters to the force: its icon, its words and its colour. */
    private record Perk(String icon, String text, Color colour) {}

    /** What {@code star} offers the force, or warns it of: hiring hall, academies, capital, industry, stations, health. */
    private static List<Perk> perks(StarMap.Star star) {
        List<Perk> perks = new ArrayList<>();
        Color good = UiTheme.tokenColor("text.primary");
        int hall = MapLens.HIRING_HALLS.level(star.lenses());
        if (hall > 0) {
            perks.add(new Perk("group", getText("HqHub.map.perk.hall." + hall), good));
        }
        int academies = MapLens.ACADEMIES.level(star.lenses());
        if (academies > 0) {
            perks.add(new Perk("book", getFormattedText("HqHub.map.perk.academies", academies), good));
        }
        if (star.capital() > 0) {
            perks.add(new Perk("star", getText("HqHub.map.perk.capital." + Math.min(3, star.capital())),
                  UiTheme.tokenColor("accent.primary")));
        }
        int industry = MapLens.INDUSTRY.level(star.lenses());
        if (industry >= 4) {
            perks.add(new Perk("factory", getText("HqHub.map.perk.industry." + industry), good));
        }
        if (MapLens.RECHARGE.level(star.lenses()) >= 2) {
            perks.add(new Perk("power", getText("HqHub.map.perk.recharge"), good));
        }
        if (MapLens.DISEASES.level(star.lenses()) > 0) {
            perks.add(new Perk("warn", getText("HqHub.map.perk.health"), UiTheme.tokenColor("status.warning")));
        }
        return perks;
    }

    /** A bare icon button with a tooltip read from {@code tipKey}. */
    private UiButton iconButton(String icon, String tipKey) {
        UiButton button = ui.button("hud-icon", icon, null, null);
        ui.tip(button).getActor().setText(getText(tipKey));
        return button;
    }

    /** Opens the chosen system's full record beside its card and asks the hub for it, or closes it. */
    private void showDossier(boolean open) {
        dossierOpen = open && detailId != null;
        if (dossierButton != null) {
            dossierButton.setText(getText(dossierOpen ? "HqHub.map.dossier.hide" : "HqHub.map.dossier.show"));
        }
        if (!dossierOpen) {
            dossierShown = null;
            dossierPanel.setVisible(false);
            return;
        }
        dossierShown = null;
        fillDossier();
        dossierPanel.setVisible(true);
        dossierPanel.getColor().a = 0;
        dossierPanel.clearActions();
        dossierPanel.addAction(Actions.fadeIn(0.18f));
        hub.dossier(detailId);
    }

    /** The full record of a system the player asked about. */
    void dossier(SystemDossier read) {
        if (dossierOpen && read.id().equals(detailId)) {
            dossierShown = read;
            dossierWorld = Math.max(0, read.primary());
            fillDossier();
        }
    }

    /** Fills the record panel: the system, then the world picked from its tabs, section by section. */
    private void fillDossier() {
        dossierPanel.clearChildren();
        dossierPanel.defaults().left().growX();
        dossierPanel.add(ui.caption(getText("HqHub.map.dossier.title"), true)).row();
        SystemDossier read = dossierShown;
        if (read == null) {
            Table waiting = new Table();
            ui.empty(waiting, getText("HqHub.map.reading"), true);
            dossierPanel.add(waiting).padTop(space("sm"));
            return;
        }
        Table body = new Table();
        body.top().left();
        body.defaults().left().growX();
        dossierSection(body, "system", read.system());
        if (!read.academies().isEmpty()) {
            dossierText(body, "academies", String.join(", ", read.academies()), "text.primary");
        }
        if (!read.diseases().isEmpty()) {
            dossierText(body, "diseases", String.join(", ", read.diseases()), "status.warning");
        }
        if (!read.worlds().isEmpty()) {
            int index = Math.min(dossierWorld, read.worlds().size() - 1);
            if (read.worlds().size() > 1) {
                String[] names = read.worlds().stream().map(SystemDossier.World::name).toArray(String[]::new);
                UiKit.Segmented tabs = ui.segmented("hud-tab", false, names);
                for (int i = 0; i < tabs.buttons.size(); i++) {
                    int picked = i;
                    UiKit.onChange(tabs.buttons.get(i), () -> {
                        dossierWorld = picked;
                        fillDossier();
                    });
                }
                tabs.select(index);
                tabs.left();
                body.add(ui.scrollStrip(tabs)).padTop(space("lg")).row();
            }
            SystemDossier.World world = read.worlds().get(index);
            Label name = ui.roleTypeLabel("title", world.name(), "text.primary");
            name.setWrap(true);
            body.add(name).padTop(space("md")).row();
            String kind = world.position().isBlank() ? world.kind()
                  : getFormattedText("HqHub.map.dossier.where", world.kind(), world.position());
            body.add(ui.label(kind, "hud-body", 13, UiTheme.tokenColor("text.secondary"))).padTop(space("xxs")).row();
            dossierSection(body, "profile", world.profile());
            dossierSection(body, "environment", world.environment());
            dossierSection(body, "society", world.society());
            if (world.industry().size() == INDUSTRY.size()) {
                body.add(ui.caption(getText("HqHub.map.dossier.section.industry"), true)).padTop(space("lg")).row();
                body.add(ui.solid(UiTheme.tokenColor("border.subtle"))).height(1).padTop(space("xxs")).row();
                for (int i = 0; i < INDUSTRY.size(); i++) {
                    dossierText(body, "industry." + INDUSTRY.get(i), world.industry().get(i), "text.primary");
                }
            }
            if (!world.description().isBlank()) {
                body.add(ui.caption(getText("HqHub.map.dossier.section.about"), true)).padTop(space("lg")).row();
                body.add(ui.solid(UiTheme.tokenColor("border.subtle"))).height(1).padTop(space("xxs")).row();
                Label about = ui.label(world.description(), "hud-body", 13, UiTheme.tokenColor("text.primary"));
                about.setWrap(true);
                body.add(about).padTop(space("sm")).row();
            }
        }
        Label system = ui.roleTypeLabel("headline", read.name(), "text.primary");
        system.setWrap(true);
        dossierPanel.add(system).padTop(space("xs")).row();
        ScrollPane scroll = ui.scrollList(body, true, false);
        dossierPanel.add(scroll).padTop(space("sm")).maxHeight(Math.max(240, stage.getHeight() - space("xxl") * 8));
    }

    /** A titled run of facts in the record panel, label beside value; nothing when there are none. */
    private void dossierSection(Table body, String section, List<SystemDossier.Fact> facts) {
        if (facts.isEmpty()) {
            return;
        }
        body.add(ui.caption(getText("HqHub.map.dossier.section." + section), true)).padTop(space("lg")).row();
        body.add(ui.solid(UiTheme.tokenColor("border.subtle"))).height(1).padTop(space("xxs")).row();
        Table rows = new Table();
        rows.defaults().top().left().padTop(space("xs"));
        for (SystemDossier.Fact fact : facts) {
            rows.add(ui.label(getText("HqHub.map.dossier." + fact.key()), "hud-body", 13,
                  UiTheme.tokenColor("text.secondary"))).width(150).padRight(space("md"));
            Label value = ui.label(fact.value(), "hud-body", 13, UiTheme.tokenColor("text.primary"));
            value.setWrap(true);
            rows.add(value).growX().minWidth(0).row();
        }
        body.add(rows).padTop(space("xxs")).row();
    }

    /** A labelled paragraph in the record panel. */
    private void dossierText(Table body, String key, String text, String colour) {
        Table row = new Table();
        row.defaults().top().left();
        row.add(ui.label(getText("HqHub.map.dossier." + key), "hud-body", 13, UiTheme.tokenColor("text.secondary")))
              .width(150).padRight(space("md"));
        Label value = ui.label(text, "hud-body", 13, UiTheme.tokenColor(colour));
        value.setWrap(true);
        row.add(value).growX().minWidth(0);
        body.add(row).padTop(space("xs")).row();
    }

    /**
     * Writes the course across the header: the voyage underway, with the way to see it whole and to call it off;
     * else the course plotted to the chosen system, to set out on or clear; else how to plot one.
     */
    private void writeRoute() {
        routeStrip.clearChildren();
        routeStrip.left();
        Table about = new Table();
        about.defaults().left();
        Table actions = new Table();
        StarMap.Voyage underway = voyage;
        StarMap.Detail chosen = plotted;
        if (underway != null) {
            about.add(ui.caption(getText("HqHub.map.route.underway"), true)).row();
            Label line = ui.label(voyage(underway), "hud-body", 14, UiTheme.tokenColor("text.primary"));
            line.setEllipsis(true);
            about.add(line).growX().minWidth(0).padTop(space("xxs")).row();
            Table leg = new Table();
            float done = (float) Math.clamp(underway.leg(), 0, 1);
            leg.add(ui.solid(UiTheme.tokenColor("accent.primary"))).height(2).width(160 * done);
            leg.add(ui.solid(UiTheme.tokenColor("border.subtle"))).height(2).width(160 * (1 - done));
            about.add(leg).left().padTop(space("xs"));
            List<String> whole = voyageRoute;
            boolean drawn = map.routeShown();
            UiButton eye = iconButton(drawn ? "show" : "hide", drawn ? "HqHub.map.route.hide" : "HqHub.map.route.reveal");
            UiKit.onChange(eye, () -> {
                map.showRoute(!map.routeShown());
                writeRoute();
            });
            actions.add(eye).padRight(space("xs"));
            UiButton show = iconButton("locate", "HqHub.map.route.show");
            UiKit.onChange(show, () -> map.frame(whole));
            actions.add(show).padRight(space("xs"));
            boolean armed = cancelArmed > 0;
            UiButton cancel = armed
                  ? ui.button("button-primary", "cancel", getText("HqHub.map.route.confirm"), null)
                  : iconButton("cancel", "HqHub.map.route.cancel");
            UiKit.onChange(cancel, () -> {
                if (cancelArmed > 0) {
                    cancelArmed = 0;
                    hub.cancelTrip();
                } else {
                    cancelArmed = CANCEL_WINDOW;
                }
                writeRoute();
            });
            actions.add(cancel);
        } else if (chosen != null && chosen.course() != null) {
            StarMap.Course course = chosen.course();
            about.add(ui.caption(getText("HqHub.map.route.plotted"), true)).row();
            String text = getFormattedText("HqHub.map.route.course", chosen.name(),
                  getFormattedText("HqHub.map.jumpCount", course.jumps()),
                  getFormattedText("HqHub.map.days", course.days()));
            if (!course.cost().isBlank()) {
                text += "  \u00b7  " + course.cost();
            }
            Label line = ui.label(text, "hud-body", 14, UiTheme.tokenColor("text.primary"));
            line.setEllipsis(true);
            about.add(line).growX().minWidth(0).padTop(space("xxs"));
            UiButton go = ui.button("button-primary", null, getText("HqHub.map.setCourse"), null);
            go.setDisabled(course.state() != StarMap.Departure.READY);
            UiKit.onChange(go, () -> {
                if (!go.isDisabled()) {
                    go.setDisabled(true);
                    hub.travel(chosen.id());
                }
            });
            actions.add(go).padRight(space("sm"));
            List<String> whole = course.route();
            UiButton show = iconButton("locate", "HqHub.map.route.show");
            UiKit.onChange(show, () -> map.frame(whole));
            actions.add(show).padRight(space("xs"));
            UiButton clear = iconButton("close", "HqHub.map.route.clear");
            UiKit.onChange(clear, () -> {
                map.clearChoice();
                chooseSystem(null);
            });
            actions.add(clear);
        } else {
            about.add(ui.caption(getText("HqHub.map.route.none"), true)).row();
            about.add(ui.label(getText("HqHub.map.route.hint"), "hud-body", 14, UiTheme.tokenColor("text.muted")))
                  .padTop(space("xxs"));
        }
        routeStrip.add(about).growX().minWidth(0);
        routeStrip.add(actions).right().padLeft(space("lg"));
    }

    private void fact(Table facts, String key, String value) {
        facts.add(ui.caption(getText(key), true)).left().expandX();
        facts.add(ui.label(value, "hud-body", 14, UiTheme.tokenColor("text.primary"))).right();
        facts.row();
    }

    private static String owner(String owner) {
        return owner.isBlank() ? getText("HqHub.map.unclaimed") : owner;
    }

    /**
     * Opens the map's menu where the player right-clicked: what can be done with {@code star}, or with the open space
     * at ({@code x}, {@code y}) light years, and with the view.
     */
    private void openMapMenu(@Nullable StarMap.Star star, double x, double y, int screenX, int screenY) {
        if (!mapOpen || moving()) {
            return;
        }
        mapMenuList.clearChildren();
        StarMap.Star force = map.current();
        if (star != null) {
            menuItem("HqHub.map.menu.details", null, () -> map.focus(star));
            if (force != null && !force.id().equals(star.id())) {
                menuItem("HqHub.map.menu.measure", null, () -> map.measureFromForce(star));
            }
        }
        menuItem("HqHub.map.menu.centre", null, () -> map.flyTo(x, y));
        if (force != null) {
            menuItem("HqHub.map.menu.force", getText("HqHub.map.menu.forceKey"), map::home);
        }
        StarMap.Star terra = map.system("Terra");
        if (terra != null) {
            menuItem("HqHub.map.menu.terra", null, () -> map.flyTo(terra.x(), terra.y()));
        }
        mapMenuList.separator();
        menuItem("HqHub.map.menu.zoomIn", null, () -> map.zoomBy(0.5f));
        menuItem("HqHub.map.menu.zoomOut", null, () -> map.zoomBy(2));
        if (map.ruled() || (today != null && scrubber != null && scrubber.year() != today.getYear())) {
            mapMenuList.separator();
        }
        if (map.ruled()) {
            menuItem("HqHub.map.menu.clearRuler", getText("HqHub.map.menu.clearRulerKey"), () -> {
                map.clearRuler();
                rulerCard.setVisible(false);
            });
        }
        if (today != null && scrubber != null && scrubber.year() != today.getYear()) {
            menuItem("HqHub.map.menu.today", getText("HqHub.map.menu.todayKey"), this::backToToday);
        }
        String title = star != null ? star.name() : getText("HqHub.map.menu.space");
        String where = getFormattedText("HqHub.map.menu.coordinates",
              String.format(Locale.getDefault(), "%.1f", x), String.format(Locale.getDefault(), "%.1f", y));
        String detail = star != null ? owner(star.owner()) + "  \u00b7  " + where : where;
        Vector2 corner = stage.screenToStageCoordinates(new Vector2(screenX, screenY));
        mapMenu.header(title, detail).content(mapMenuList);
        mapMenu.showAt(corner.x, corner.y);
        hoverCard.setVisible(false);
    }

    private void menuItem(String key, @Nullable String hint, Runnable action) {
        mapMenuList.item(getText(key), hint, null, false, true, () -> {
            mapMenu.cancel();
            action.run();
        });
    }

    /** The ruler's reading, written at its middle. */
    private Table rulerCard() {
        rulerCard = ui.panel(true);
        rulerCard.setTouchable(Touchable.disabled);
        rulerReading = ui.label("", "hud-body", 14, UiTheme.tokenColor("text.primary"));
        rulerJumps = ui.caption("", true);
        rulerCard.add(rulerReading).row();
        rulerCard.add(rulerJumps).padTop(space("xxs"));
        rulerCard.setVisible(false);
        return rulerCard;
    }

    /** Writes the ruler's reading at its middle, or hides it when no ruler is laid. */
    private void placeRuler() {
        float[] reading = mapOpen ? map.ruler() : null;
        if (reading == null) {
            rulerCard.setVisible(false);
            return;
        }
        String span = String.format(Locale.getDefault(), "%.1f", reading[2]);
        int jumps = (int) Math.ceil(reading[2] / MHQConstants.MAX_JUMP_RADIUS - 1e-4);
        rulerReading.setText(getFormattedText("HqHub.map.lightYears", span));
        rulerJumps.setText(getFormattedText("HqHub.map.ruler.jumps", jumps));
        rulerCard.pack();
        Vector2 at = stage.screenToStageCoordinates(new Vector2(reading[0], reading[1]));
        rulerCard.setPosition(Math.round(at.x - rulerCard.getWidth() / 2), Math.round(at.y + HOVER_OFFSET));
        rulerCard.setVisible(true);
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
        transmissions.resize(width, height);
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
            placeRuler();
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
        transmissions.render(delta);
    }

    @Override
    public void dispose() {
        transmissions.dispose();
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

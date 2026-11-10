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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
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
import mekhq.gui.enums.MHQTabType;
import mekhq.gui.model.view.TopBar;

/**
 * The campaign's command post: the {@link HoloTableView holotable} with the star map around the force, and over it the
 * campaign's name, today, where the force is, its funds and what needs the player's attention; a rail of the
 * campaign's areas (keys 1 to 7), a command palette (Ctrl+K) over its screens, people, units and menu items, and the
 * way back to the classic campaign window. GPU thread, unless a method says otherwise.
 */
final class HubScreen extends ApplicationAdapter {
    /** The most results the palette lists. */
    private static final int RESULTS = 12;
    private static final float PALETTE_WIDTH = 560;

    /** What the screen asks of the hub; any thread. */
    interface Hub {
        /** Goes where {@code target} leads. */
        void go(PaletteEntry.Target target);

        /** Gathers the palette's entries, which come back through {@link #palette(List)}. */
        void palette();

        /** The screen's listener is being disposed. */
        void disposed();
    }

    private final HoloTableView holo;
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
     * scale {@code scale}.
     */
    HubScreen(Path glb, @Nullable StarChart chart, String campaignName, float scale, Hub hub) {
        holo = new HoloTableView(glb, chart);
        this.campaignName = campaignName;
        this.scale = scale;
        this.hub = hub;
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
        theme = new UiTheme(Configuration.fontsDir());
        ui = new UiKit(theme.skin);
        stage = new Stage(new ScreenViewport());
        stage.addActor(layout());
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
        Gdx.input.setInputProcessor(new InputMultiplexer(blocker(), stage, keys, holo.input()));
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
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
        if (paletteLayer.isVisible() || UiModal.active(stage) != null) {
            return false;
        }
        if (keycode == Input.Keys.ESCAPE) {
            if (flyout.isVisible()) {
                flyout.back();
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

    /** The left rail: the palette's search, then the campaign's areas with their hotkeys. */
    private Table rail() {
        Table column = new Table();
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
        if (stage != null && width > 0 && height > 0) {
            ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1 / scale);
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void render() {
        if (pending != null) {
            apply(pending);
            pending = null;
        }
        holo.render();
        stage.getViewport().apply(true);
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 0.1f));
        stage.draw();
    }

    @Override
    public void dispose() {
        hub.disposed();
        holo.dispose();
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

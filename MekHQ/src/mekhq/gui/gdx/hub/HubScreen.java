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

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
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
import megamek.client.ui.gdx.UiTheme;
import megamek.common.Configuration;
import mekhq.gui.model.view.TopBar;

/**
 * The campaign's command post: the {@link HoloTableView holotable} with the star map around the force, and over it the
 * campaign's name, today, where the force is, its funds and what needs the player's attention, and the way back to the
 * classic campaign window.
 */
final class HubScreen extends ApplicationAdapter {
    private final HoloTableView holo;
    private final String campaignName;
    private final float scale;
    private final Runnable classic;
    private UiTheme theme;
    private UiKit ui;
    private Stage stage;
    private Label date;
    private Label where;
    private Label funds;
    private Table alerts;
    /** GPU thread: the last top bar the session handed over, shown at the next frame. */
    private @Nullable TopBar pending;

    /**
     * The hub of the campaign {@code campaignName}: the table from {@code glb} with {@code chart} over it, at the GUI
     * scale {@code scale}; {@code classic} leaves it for the campaign window, on the GPU thread.
     */
    HubScreen(Path glb, @Nullable StarChart chart, String campaignName, float scale, Runnable classic) {
        holo = new HoloTableView(glb, chart);
        this.campaignName = campaignName;
        this.scale = scale;
        this.classic = classic;
    }

    /** GPU thread: the campaign's top bar changed. */
    void show(TopBar bar) {
        pending = bar;
    }

    @Override
    public void create() {
        holo.create();
        theme = new UiTheme(Configuration.fontsDir());
        ui = new UiKit(theme.skin);
        stage = new Stage(new ScreenViewport());
        stage.addActor(layout());
        InputAdapter keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    classic.run();
                    return true;
                }
                return false;
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, keys, holo.input()));
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
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
        UiKit.onChange(back, classic);
        Table leave = new Table();
        leave.add(back);
        leave.add(ui.caption(getText("HqHub.escape"), true)).padLeft(space("md"));
        root.add(leave).left().bottom();
        Label controls = ui.caption(getText("HqHub.controls"), true);
        controls.setAlignment(Align.right);
        root.add(controls).right().bottom();
        return root;
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

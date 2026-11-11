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

import java.nio.IntBuffer;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import megamek.client.ui.clientGUI.GUIPreferences;
import megamek.client.ui.gdx.UiTheme;

/**
 * How heavy the hub's frames are, in its bottom left corner: the frames a second, the slowest frame, and the time a
 * frame's drawing takes on the CPU and, through timer queries, on the GPU, averaged over half a second. The frame rate
 * stops at the screen's refresh rate (or the window's cap), so the CPU and GPU times are what show the headroom. It
 * follows MegaMek's Show FPS setting, which F3 toggles.
 */
final class FrameMeter {
    /** Seconds the figures average over. */
    private static final float WINDOW = .5f;
    /** Timer queries in flight: the GPU answers a few frames late. */
    private static final int QUERIES = 4;
    /** {@code GL_TIME_ELAPSED}, which libGDX's GL30 doesn't name. */
    private static final int TIME_ELAPSED = 0x88BF;
    private static final float MARGIN = 8;

    private boolean shown;
    private Stage stage;
    private Label label;
    private float scale = 1;

    private long frameStart;
    private int frames;
    private float elapsed;
    private float worst;
    private long cpuNanos;
    private long gpuNanos;
    private int gpuFrames;

    private final int[] queries = new int[QUERIES];
    private final boolean[] pending = new boolean[QUERIES];
    private final IntBuffer result = BufferUtils.newIntBuffer(1);
    private int slot;
    private boolean timing;

    /** Sets the meter up on the hub's look, at {@code scale} pixels a stage unit. */
    void create(UiTheme theme, float scale) {
        this.scale = scale;
        shown = GUIPreferences.getInstance().getShowFPS();
        stage = new Stage(new ScreenViewport());
        label = new Label("", theme.skin, "hud-small");
        label.setColor(UiTheme.TEXT);
        Table box = new Table();
        box.setBackground(theme.skin.newDrawable("white", new Color(0, 0, 0, .62f)));
        box.pad(3, 8, 3, 8);
        box.add(label);
        box.setTouchable(Touchable.disabled);
        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.disabled);
        root.add(box).expand().bottom().left().pad(MARGIN);
        stage.addActor(root);
        if (Gdx.gl30 != null) {
            for (int i = 0; i < QUERIES; i++) {
                result.clear();
                Gdx.gl30.glGenQueries(1, result);
                queries[i] = result.get(0);
            }
        }
    }

    /** F3, before anything else takes the keys, so it works over a transmission too. */
    InputProcessor input() {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode != Input.Keys.F3) {
                    return false;
                }
                shown = !shown;
                GUIPreferences.getInstance().setValue(GUIPreferences.ADVANCED_SHOW_FPS, shown);
                reset();
                return true;
            }
        };
    }

    /** At the start of the hub's frame. */
    void begin() {
        if (!shown) {
            return;
        }
        frameStart = System.nanoTime();
        timing = false;
        GL30 gl = Gdx.gl30;
        if (gl == null || queries[slot] == 0) {
            return;
        }
        if (pending[slot]) {
            result.clear();
            gl.glGetQueryObjectuiv(queries[slot], GL30.GL_QUERY_RESULT_AVAILABLE, result);
            if (result.get(0) == 0) {
                return;
            }
            result.clear();
            gl.glGetQueryObjectuiv(queries[slot], GL30.GL_QUERY_RESULT, result);
            gpuNanos += Integer.toUnsignedLong(result.get(0));
            gpuFrames++;
            pending[slot] = false;
        }
        gl.glBeginQuery(TIME_ELAPSED, queries[slot]);
        timing = true;
    }

    /** At the end of the hub's frame, {@code delta} seconds after the last: counts it and draws the meter over it. */
    void end(float delta) {
        if (!shown) {
            return;
        }
        if (timing) {
            Gdx.gl30.glEndQuery(TIME_ELAPSED);
            timing = false;
            pending[slot] = true;
            slot = (slot + 1) % QUERIES;
        }
        cpuNanos += System.nanoTime() - frameStart;
        frames++;
        elapsed += delta;
        worst = Math.max(worst, delta);
        if (elapsed >= WINDOW) {
            label.setText(summary(frames, elapsed, worst, cpuNanos, gpuNanos, gpuFrames));
            frames = 0;
            elapsed = 0;
            worst = 0;
            cpuNanos = 0;
            gpuNanos = 0;
            gpuFrames = 0;
        }
        stage.getViewport().apply(true);
        stage.act(delta);
        stage.draw();
    }

    /**
     * The meter's text for {@code frames} frames over {@code seconds}, the slowest {@code worst} seconds long, taking
     * {@code cpuNanos} on the CPU and, over the {@code gpuFrames} the GPU has timed (none leaves the GPU out),
     * {@code gpuNanos} on the GPU.
     */
    static String summary(int frames, float seconds, float worst, long cpuNanos, long gpuNanos, int gpuFrames) {
        long rate = Math.round(frames / Math.max(seconds, 1e-3f));
        String slowest = milliseconds(worst * 1e9);
        String cpu = milliseconds(cpuNanos / (double) Math.max(frames, 1));
        if (gpuFrames == 0) {
            return getFormattedText("HqHub.frameMeter.noGpu", rate, slowest, cpu);
        }
        return getFormattedText("HqHub.frameMeter", rate, slowest, cpu, milliseconds(gpuNanos / (double) gpuFrames));
    }

    private static String milliseconds(double nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
    }

    void resize(int width, int height) {
        if (stage == null || width <= 0 || height <= 0) {
            return;
        }
        ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1 / scale);
        stage.getViewport().update(width, height, true);
    }

    void dispose() {
        if (stage != null) {
            stage.dispose();
        }
        if (Gdx.gl30 != null && queries[0] != 0) {
            IntBuffer names = BufferUtils.newIntBuffer(QUERIES);
            names.put(queries).flip();
            Gdx.gl30.glDeleteQueries(QUERIES, names);
        }
    }

    private void reset() {
        frames = 0;
        elapsed = 0;
        worst = 0;
        cpuNanos = 0;
        gpuNanos = 0;
        gpuFrames = 0;
        label.setText("");
    }
}

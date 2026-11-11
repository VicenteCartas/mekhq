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

import java.util.Random;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Align;
import jakarta.annotation.Nullable;

/**
 * The light of a transmission's channel, drawn over its panel and setting the panel's scale as an old tube screen
 * powers on and off. Powering on, a dot of light stretches into a line that unfolds into the panel, bright at its
 * edges, and the panel's face flashes as it settles. Powering off, the panel squeezes into a line, the line into a dot,
 * and the dot fades out after the panel is gone. A cut from one transmission to the next skips both: the new panel
 * shows at once, torn by static under a sweeping line. GPU thread.
 */
final class TransmissionTube extends Actor {
    /** Seconds the dot shows, stretches into a line, the line unfolds and the panel's face settles. */
    static final float DOT = .07f;
    static final float LINE = .12f;
    static final float UNFOLD = .2f;
    static final float SETTLE = .2f;
    /** Seconds the panel squeezes into a line, the line into a dot, and the dot fades. */
    static final float SQUASH = .12f;
    static final float COLLAPSE = .08f;
    static final float AFTERGLOW = .22f;
    /** Seconds a cut tears the new panel. */
    static final float CUT = .2f;
    /** The scale of a panel squeezed into a line. */
    private static final float THIN = .02f;
    private static final int RINGS = 5;
    private static final int TEARS = 6;

    private enum Phase { IDLE, ON, OFF, CUT }

    private final TextureRegion white;
    private final Color glow;
    private final Color core;
    private final Random random = new Random();
    private final Vector2 point = new Vector2();
    private Phase phase = Phase.IDLE;
    private @Nullable Actor target;
    private @Nullable Runnable done;
    private float wait;
    private float time;
    /** Where the light was last, in stage units, so the dot can fade after the panel is gone. */
    private float centerX;
    private float centerY;

    /** A tube glowing {@code glow}; {@code white} is a solid white region. */
    TransmissionTube(TextureRegion white, Color glow) {
        this.white = white;
        this.glow = new Color(glow);
        core = new Color(glow).lerp(Color.WHITE, .6f);
        setTouchable(Touchable.disabled);
    }

    /** Seconds from the start of a power-on, after its delay, until the panel is full size. */
    static float openSeconds() {
        return DOT + LINE + UNFOLD;
    }

    /** Powers {@code panel} on after {@code delay} seconds; it hides until then. */
    void powerOn(Actor panel, float delay) {
        start(Phase.ON, panel, delay, null);
        panel.setScale(THIN, THIN);
        panel.getColor().a = 0;
    }

    /** Powers {@code panel} off after {@code delay} seconds, then runs {@code then} as it goes. */
    void powerOff(Actor panel, float delay, Runnable then) {
        start(Phase.OFF, panel, delay, then);
    }

    /** Cuts to {@code panel}, the next transmission's. */
    void cut(Actor panel) {
        start(Phase.CUT, panel, 0, null);
        panel.setScale(1, 1);
    }

    /** Whether a panel is still powering on or being cut to. */
    boolean opening() {
        return phase == Phase.ON || phase == Phase.CUT;
    }

    /** Shows the opening panel at once, as when the player skips the opening. */
    void finish() {
        if (opening() && target != null) {
            target.setScale(1, 1);
            target.getColor().a = 1;
        }
        if (opening()) {
            stop();
        }
    }

    /** Stops the light at once, leaving the panel as it is. */
    void stop() {
        phase = Phase.IDLE;
        target = null;
        done = null;
    }

    private void start(Phase next, Actor panel, float delay, @Nullable Runnable then) {
        phase = next;
        target = panel;
        done = then;
        wait = delay;
        time = 0;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (phase == Phase.IDLE) {
            return;
        }
        if (wait > 0) {
            wait -= delta;
            return;
        }
        time += delta;
        Actor panel = target;
        if (panel != null) {
            panel.setOrigin(Align.center);
        }
        switch (phase) {
            case ON -> on(panel);
            case OFF -> off(panel);
            case CUT -> {
                if (panel != null) {
                    panel.getColor().a = time < CUT * .35f ? .35f + .5f * random.nextFloat() : 1;
                }
                if (time >= CUT) {
                    stop();
                }
            }
            default -> { }
        }
    }

    private void on(@Nullable Actor panel) {
        if (panel == null) {
            stop();
            return;
        }
        if (time < DOT) {
            panel.setScale(THIN, THIN);
            panel.getColor().a = 0;
        } else if (time < DOT + LINE) {
            panel.setScale(MathUtils.lerp(THIN, 1, Interpolation.pow3Out.apply((time - DOT) / LINE)), THIN);
            panel.getColor().a = 1;
        } else if (time < openSeconds()) {
            panel.setScale(1, MathUtils.lerp(THIN, 1, Interpolation.pow3Out.apply((time - DOT - LINE) / UNFOLD)));
            panel.getColor().a = 1;
        } else {
            panel.setScale(1, 1);
            panel.getColor().a = 1;
            if (time >= openSeconds() + SETTLE) {
                stop();
            }
        }
    }

    private void off(@Nullable Actor panel) {
        if (time < SQUASH) {
            if (panel != null) {
                panel.setScale(1, MathUtils.lerp(1, THIN, Interpolation.pow2In.apply(time / SQUASH)));
            }
        } else if (time < SQUASH + COLLAPSE) {
            if (panel != null) {
                panel.setScale(MathUtils.lerp(1, THIN, Interpolation.pow2In.apply((time - SQUASH) / COLLAPSE)),
                      THIN);
            }
        } else if (done != null) {
            if (panel != null) {
                panel.getColor().a = 0;
            }
            target = null;
            Runnable then = done;
            done = null;
            then.run();
        } else if (time >= SQUASH + COLLAPSE + AFTERGLOW) {
            stop();
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (phase == Phase.IDLE || wait > 0) {
            return;
        }
        Actor panel = target;
        float width = 0;
        float height = 0;
        float scaleX = 1;
        float scaleY = 1;
        if (panel != null && panel.getParent() != null) {
            width = panel.getWidth();
            height = panel.getHeight();
            scaleX = panel.getScaleX();
            scaleY = panel.getScaleY();
            panel.getParent().localToStageCoordinates(point.set(panel.getX() + width / 2,
                  panel.getY() + height / 2));
            centerX = point.x;
            centerY = point.y;
        }
        int source = batch.getBlendSrcFunc();
        int destination = batch.getBlendDstFunc();
        Color before = new Color(batch.getColor());
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        switch (phase) {
            case ON -> drawOn(batch, width, height, scaleX, scaleY);
            case OFF -> drawOff(batch, width, height, scaleX, scaleY);
            case CUT -> drawCut(batch, width, height);
            default -> { }
        }
        batch.setBlendFunction(source, destination);
        batch.setColor(before);
    }

    private void drawOn(Batch batch, float width, float height, float scaleX, float scaleY) {
        if (time < DOT) {
            float lit = time / DOT;
            light(batch, 4 + 8 * lit, 4 + 8 * lit, 0, lit, 3, 3);
        } else if (time < DOT + LINE) {
            float stretch = (time - DOT) / LINE;
            light(batch, width * scaleX, 2, 0, 1, 2, 4);
            light(batch, 10, 10, 0, 1 - stretch * .5f, 6, 6);
        } else {
            float unfold = Math.min(1, (time - DOT - LINE) / UNFOLD);
            float fade = 1 - unfold;
            float settled = time > openSeconds() ? Math.min(1, (time - openSeconds()) / SETTLE) : 0;
            float half = height * scaleY / 2;
            light(batch, width, 2, 0, fade * .8f, 2, 4);
            float rims = (.4f + .6f * fade) * (1 - settled);
            light(batch, width, 2, half, rims, 2, 3);
            light(batch, width, 2, -half, rims, 2, 3);
            face(batch, width, 2 * half, (.08f + .1f * fade) * (1 - settled));
        }
    }

    private void drawOff(Batch batch, float width, float height, float scaleX, float scaleY) {
        if (time < SQUASH) {
            float squeeze = time / SQUASH;
            float half = height * scaleY / 2;
            face(batch, width, 2 * half, .2f * squeeze);
            light(batch, width, 2, half, .3f + .7f * squeeze, 2, 3);
            light(batch, width, 2, -half, .3f + .7f * squeeze, 2, 3);
        } else if (time < SQUASH + COLLAPSE) {
            light(batch, width * scaleX, 2, 0, 1, 2, 4);
            light(batch, 10, 10, 0, (time - SQUASH) / COLLAPSE, 6, 6);
        } else {
            float faded = Math.min(1, (time - SQUASH - COLLAPSE) / AFTERGLOW);
            float size = 2 + 8 * (1 - faded);
            light(batch, size, size, 0, (1 - faded) * (1 - faded), 3, 3);
        }
    }

    private void drawCut(Batch batch, float width, float height) {
        float fade = 1 - Math.min(1, time / CUT);
        float left = centerX - width / 2;
        float bottom = centerY - height / 2;
        batch.setColor(core.r, core.g, core.b, .3f * fade);
        for (int i = 0; i < TEARS; i++) {
            float tear = 1 + 5 * random.nextFloat();
            batch.draw(white, left, bottom + random.nextFloat() * (height - tear), width, tear);
        }
        light(batch, width, 2, height / 2 - (1 - fade) * height, .7f * fade, 2, 4);
    }

    /**
     * A bar of light {@code width} by {@code height} centered {@code offset} above the light's center, at
     * {@code alpha}, haloed by rings spreading {@code spreadX} and {@code spreadY} units each.
     */
    private void light(Batch batch, float width, float height, float offset, float alpha, float spreadX,
          float spreadY) {
        if (alpha <= 0 || width <= 0) {
            return;
        }
        float y = centerY + offset;
        for (int ring = RINGS; ring >= 1; ring--) {
            float w = width + 2 * ring * spreadX;
            float h = height + 2 * ring * spreadY;
            batch.setColor(glow.r, glow.g, glow.b, alpha * .12f * (RINGS + 1 - ring) / RINGS);
            batch.draw(white, centerX - w / 2, y - h / 2, w, h);
        }
        batch.setColor(core.r, core.g, core.b, alpha);
        batch.draw(white, centerX - width / 2, y - height / 2, width, height);
    }

    /** A wash of light over the panel's face, {@code width} by {@code height} at {@code alpha}. */
    private void face(Batch batch, float width, float height, float alpha) {
        if (alpha <= 0) {
            return;
        }
        batch.setColor(core.r, core.g, core.b, alpha);
        batch.draw(white, centerX - width / 2, centerY - height / 2, width, height);
    }
}

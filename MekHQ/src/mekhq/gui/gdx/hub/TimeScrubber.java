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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.utils.Align;
import jakarta.annotation.Nullable;
import megamek.client.ui.design.DesignTokens;
import megamek.client.ui.gdx.UiKit;
import megamek.client.ui.gdx.UiTheme;
import mekhq.campaign.universe.eras.Era;

/**
 * The star map's timeline: the years of the inner sphere's history in bands for its eras, the campaign's year marked,
 * and a glowing handle on the year the map shows, which the player drags, clicks or scrolls. The handle glides to the
 * year rather than jumping, and pulses while the map charts that year's borders. GPU thread.
 */
final class TimeScrubber extends WidgetGroup {
    /** One era's years on the timeline, from {@code from} through {@code to}. */
    record Band(int from, int to, String name) {}

    private static final float TRACK = 12;
    private static final float HANDLE = 24;
    private static final float WIDTH = 560;
    /** How quickly the handle glides to the year, per second. */
    private static final float GLIDE = 14;

    private final Texture white;
    private final IntConsumer scrubbed;
    private final Color accent = UiTheme.tokenColor("accent.primary");
    private final Color text = UiTheme.tokenColor("text.primary");
    private final float gap = DesignTokens.shipped().space().get("xs");
    private final List<Band> bands = new ArrayList<>();
    private final List<Label> bandLabels = new ArrayList<>();
    private final List<Label> centuryLabels = new ArrayList<>();
    private final List<Integer> centuries = new ArrayList<>();
    private final UiKit kit;
    private int first = 2150;
    private int last = 3152;
    private int today = 3025;
    private int year = 3025;
    /** The year the handle is at as it glides. */
    private float shown = 3025;
    private boolean dragging;
    private boolean hovering;
    private boolean charting;
    private float clock;
    private float trackY;

    /** A timeline telling {@code scrubbed} each year the player moves it to. */
    TimeScrubber(UiKit kit, IntConsumer scrubbed) {
        this.kit = kit;
        this.scrubbed = scrubbed;
        white = kit.skin.get("white", Texture.class);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                dragging = true;
                move(yearAt(x));
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                move(yearAt(x));
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                dragging = false;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                move(year + (amountY > 0 ? -1 : 1));
                return true;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, @Nullable Actor fromActor) {
                if (pointer == -1) {
                    hovering = true;
                    getStage().setScrollFocus(TimeScrubber.this);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, @Nullable Actor toActor) {
                if (pointer == -1 && (toActor == null || !toActor.isDescendantOf(TimeScrubber.this))) {
                    hovering = false;
                    if (getStage() != null && getStage().getScrollFocus() == TimeScrubber.this) {
                        getStage().setScrollFocus(null);
                    }
                }
            }
        });
    }

    /**
     * The eras in {@code eras}, keyed by the day each ends as {@link mekhq.campaign.universe.eras.Eras} keeps them, as
     * bands over the years {@code first} through {@code last}; eras wholly outside them are left out.
     */
    static List<Band> bands(Map<LocalDate, Era> eras, int first, int last) {
        List<Band> found = new ArrayList<>();
        int from = Integer.MIN_VALUE;
        for (Map.Entry<LocalDate, Era> era : eras.entrySet()) {
            int to = era.getKey().getYear();
            int start = Math.max(first, from);
            int end = Math.min(last, to);
            if (start <= end) {
                found.add(new Band(start, end, era.getValue().toString()));
            }
            from = to + 1;
        }
        return found;
    }

    /** Spans the years {@code from} through {@code to} in {@code eras}' bands, the campaign's year {@code now}. */
    void range(int from, int to, int now, List<Band> eras) {
        boolean changed = from != first || to != last || !eras.equals(bands);
        first = from;
        last = Math.max(from + 1, to);
        today = MathUtils.clamp(now, first, last);
        if (!changed) {
            return;
        }
        bands.clear();
        bands.addAll(eras);
        for (Label label : bandLabels) {
            label.remove();
        }
        bandLabels.clear();
        for (Band band : bands) {
            Label label = kit.typeLabel(band.name(), "caption", "text.muted");
            label.setAlignment(Align.center);
            label.setEllipsis(true);
            bandLabels.add(label);
            addActor(label);
        }
        for (Label label : centuryLabels) {
            label.remove();
        }
        centuryLabels.clear();
        centuries.clear();
        for (int century = (first + 99) / 100 * 100; century <= last; century += 100) {
            Label label = kit.typeLabel(String.valueOf(century), "caption", "text.muted");
            label.setAlignment(Align.center);
            centuries.add(century);
            centuryLabels.add(label);
            addActor(label);
        }
        invalidateHierarchy();
    }

    /** The year the timeline is on. */
    int year() {
        return year;
    }

    /** The campaign's year. */
    int today() {
        return today;
    }

    /** The year the handle has glided to so far. */
    float shown() {
        return shown;
    }

    /** The era of the year the handle is at; empty when it is in none. */
    String era() {
        int at = Math.round(shown);
        for (Band band : bands) {
            if (at >= band.from() && at <= band.to()) {
                return band.name();
            }
        }
        return "";
    }

    /** Puts the timeline on {@code to}, without telling anyone; {@code glide} false jumps the handle there. */
    void set(int to, boolean glide) {
        year = MathUtils.clamp(to, first, last);
        if (!glide) {
            shown = year;
        }
    }

    /** Moves the timeline to {@code to} as the player did, telling whoever listens if it changed. */
    void move(int to) {
        int clamped = MathUtils.clamp(to, first, last);
        if (clamped != year) {
            year = clamped;
            scrubbed.accept(year);
        }
    }

    /** Whether the map is still charting the borders of the year the timeline is on. */
    void charting(boolean charting) {
        this.charting = charting;
    }

    private int yearAt(float x) {
        float width = getWidth();
        if (width <= 0) {
            return year;
        }
        return Math.round(first + MathUtils.clamp(x / width, 0, 1) * (last - first));
    }

    private float xOf(float at) {
        return (at - first) / (last - first) * getWidth();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        clock += delta;
        float rate = dragging ? GLIDE * 2 : GLIDE;
        shown += (year - shown) * Math.min(1, delta * rate);
        if (Math.abs(year - shown) < 0.01f) {
            shown = year;
        }
    }

    @Override
    public float getPrefWidth() {
        return WIDTH;
    }

    @Override
    public float getPrefHeight() {
        float names = bandLabels.isEmpty() ? 0 : bandLabels.get(0).getPrefHeight();
        float years = centuryLabels.isEmpty() ? 0 : centuryLabels.get(0).getPrefHeight();
        return names + gap + HANDLE + gap + years;
    }

    @Override
    public void layout() {
        float years = centuryLabels.isEmpty() ? 0 : centuryLabels.get(0).getPrefHeight();
        trackY = years + gap + (HANDLE - TRACK) / 2;
        float top = years + gap + HANDLE + gap;
        for (int i = 0; i < bands.size(); i++) {
            Band band = bands.get(i);
            Label label = bandLabels.get(i);
            float left = xOf(band.from());
            float right = xOf(band.to() + 1);
            label.setBounds(left, top, Math.max(0, right - left), label.getPrefHeight());
            label.setVisible(right - left >= label.getPrefWidth() * 0.6f);
        }
        for (int i = 0; i < centuries.size(); i++) {
            Label label = centuryLabels.get(i);
            float width = label.getPrefWidth();
            float x = MathUtils.clamp(xOf(centuries.get(i)) - width / 2, 0, getWidth() - width);
            label.setBounds(x, 0, width, years);
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        Color before = batch.getColor().cpy();
        float alpha = parentAlpha * getColor().a;
        float x0 = getX();
        float y0 = getY() + trackY;
        float width = getWidth();
        int at = Math.round(shown);
        // The eras: alternating bands, the one the handle is in lit
        for (int i = 0; i < bands.size(); i++) {
            Band band = bands.get(i);
            float left = xOf(band.from());
            float right = xOf(band.to() + 1);
            boolean lit = at >= band.from() && at <= band.to();
            if (lit) {
                tint(batch, accent, (hovering || dragging ? 0.26f : 0.18f) * alpha);
            } else {
                tint(batch, text, (i % 2 == 0 ? 0.05f : 0.09f) * alpha);
            }
            batch.draw(white, x0 + left, y0, right - left, TRACK);
            if (i > 0) {
                tint(batch, text, 0.28f * alpha);
                batch.draw(white, x0 + left, y0 - 3, 1, TRACK + 6);
            }
            bandLabels.get(i).getColor().a = lit ? 1 : 0.7f;
        }
        // History run so far, a thin bright line along the track's foot
        tint(batch, accent, 0.55f * alpha);
        batch.draw(white, x0, y0, xOf(shown), 2);
        // Half-century ticks under the track
        for (int tick = (first + 49) / 50 * 50; tick <= last; tick += 50) {
            tint(batch, text, (tick % 100 == 0 ? 0.45f : 0.22f) * alpha);
            batch.draw(white, x0 + xOf(tick), y0 - (tick % 100 == 0 ? 6 : 4), 1, tick % 100 == 0 ? 6 : 4);
        }
        // The campaign's year: a bright notch through the track
        float now = x0 + xOf(today + 0.5f);
        tint(batch, text, 0.9f * alpha);
        batch.draw(white, now - 1, y0 - 4, 2, TRACK + 8);
        batch.draw(white, now - 3, y0 + TRACK + 4, 6, 2);
        // The handle: a glowing bar, pulsing while its year is charted
        float hx = x0 + xOf(shown + 0.5f);
        float pulse = charting ? 0.55f + 0.45f * MathUtils.sin(clock * 9) : 1;
        float lift = hovering || dragging ? 1.35f : 1;
        for (int ring = 4; ring >= 1; ring--) {
            float spread = ring * 4 * lift;
            tint(batch, accent, 0.07f * pulse * alpha);
            batch.draw(white, hx - 1.5f - spread, getY() + trackY + TRACK / 2 - HANDLE / 2 - ring,
                  3 + spread * 2, HANDLE + ring * 2);
        }
        tint(batch, accent, Math.min(1, 0.85f * pulse + 0.15f) * alpha);
        batch.draw(white, hx - 1.5f, getY() + trackY + TRACK / 2 - HANDLE / 2, 3, HANDLE);
        tint(batch, Color.WHITE, 0.9f * pulse * alpha);
        batch.draw(white, hx - 0.5f, getY() + trackY + TRACK / 2 - HANDLE / 2 + 3, 1, HANDLE - 6);
        batch.setColor(before);
        super.draw(batch, parentAlpha);
    }

    private static void tint(Batch batch, Color colour, float alpha) {
        batch.setColor(colour.r, colour.g, colour.b, alpha);
    }
}

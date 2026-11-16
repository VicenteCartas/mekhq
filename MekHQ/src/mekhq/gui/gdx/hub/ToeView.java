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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import jakarta.annotation.Nullable;
import mekhq.gui.gdx.hub.ToeLayout.Block;
import mekhq.gui.gdx.hub.ToeLayout.Link;
import mekhq.gui.gdx.hub.ToeLayout.Slot;
import mekhq.gui.gdx.hub.ToeLayout.Vacancy;
import mekhq.gui.gdx.hub.ToeMoves.Load;
import mekhq.gui.gdx.hub.ToeMoves.Verdict;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;

/**
 * The TO&amp;E table, seen from straight above: the formations laid out as an org chart ({@link ToeLayout}), each a
 * node joined to the one over it by a line of command, each unit a hexagonal counter with its art laid on it. What's
 * going on with a unit
 * reads off its counter without a word: the ring round it green when it's ready, amber when it's damaged, red when it's
 * out of action; a wreck's art knocked askew, one under repair ringed by a turning dashed ring, one laid up drained of
 * colour under a hatched cover, one nobody crews a hollow hologram of itself, one deployed an empty counter pinging
 * outward, one on its way somewhere circled by running chevrons. Far out, the counters give way to tiles in their
 * state's colour, so a regiment reads at a glance as a mosaic of its readiness.
 *
 * <p>Dragging a unit, a formation by its head or a folded formation's card carries it to another formation, the
 * formations it may go to breathing and the one under the pointer lit; dragging anywhere else, or with the right or
 * middle button, pans the table. The wheel zooms towards the pointer, and a click picks a unit ({@link #input}).</p>
 */
final class ToeView implements Disposable {
    /** What the table asks of its owner as a drag picks something up and puts it down. */
    interface Drags {
        /** {@code load} is being picked up; false leaves it where it is. */
        boolean lift(Load load);

        /** {@code load} was put down to go into formation {@code into}, or out of every one, as it may. */
        void drop(Load load, int into);
    }

    static final float FIELD_OF_VIEW = 30;
    private static final float CLICK_SLOP = 6;
    /** How near the screen's edge, as a share of it, a drag scrolls the table, and how fast, pixels a second. */
    private static final float EDGE_BAND = 0.06f;
    private static final float EDGE_SPEED = 700;
    /** How tall a slab stands, and how much higher each level down the tree. */
    private static final float SLAB = 0.06f;
    private static final float TERRACE = 0.05f;
    /** A unit's counter: its hexagon's corner radius and how thick it is, in cells. */
    private static final float BASE_RADIUS = 0.46f;
    private static final float BASE_TALL = 0.03f;
    /** Half across the hologram's quad about a counter: the counter's edge falls at 0.8 of it. */
    private static final float MARK_HALF = BASE_RADIUS / 0.8f;
    /** The box a unit's art is fitted into on its counter, in cells. */
    private static final float ART_WIDE = 0.84f;
    private static final float ART_DEEP = 0.73f;
    /** How many units' art is sent to the graphics card a frame, so a regiment's doesn't stall the opening. */
    private static final int UPLOADS_PER_FRAME = 24;
    /** Between how many pixels a cell takes on screen the tiles give way to the counters. */
    private static final float NEAR_FROM = 22;
    private static final float NEAR_TO = 32;
    /** How long a formation takes to fold into its card or open out of it, in seconds. */
    private static final float MORPH_SECONDS = 0.3f;
    /** How far out to either side what's framed may stand on screen, as the screen's half-width. */
    private static final float SIDE_EDGE = 0.92f;
    /** How high what's framed may stand on screen, under the header, from -1 at the bottom to 1 at the top. */
    private static final float TOP_EDGE = 0.56f;
    /** How low what's framed may stand on screen, above the hint and the minimap. */
    private static final float BOTTOM_EDGE = -0.86f;
    /** How long the table takes to rise when it opens, and the camera to glide somewhere, in seconds. */
    private static final float RISE_SECONDS = 0.8f;
    private static final float GLIDE_RATE = 6;
    /** How bright what the filter leaves out shows. */
    private static final float DIMMED = 0.18f;
    /** Half across a block's edge line at the least, in pixels; its shader draws a pixel and a half of it. */
    private static final float LINE_HALF_PIXELS = 3;
    private static final int HOLO_STRIDE = 11;

    // What each piece of the hologram is, and its styles (ToeShaders.HOLO_FRAGMENT)
    private static final int LINE = 0;
    private static final int RING = 1;
    private static final int PING = 3;
    private static final int CHEVRONS = 4;
    private static final int TILE = 5;
    private static final int COVER = 6;
    private static final int PIP = 7;
    private static final int STRIP = 8;
    private static final int FILL = 9;
    private static final int VACANCY = 10;
    private static final int PIP_PLACE = 0;
    private static final int PIP_TIRED = 1;
    private static final int PIP_MISFIT = 2;
    private static final int RING_STEADY = 0;
    private static final int RING_PULSING = 1;
    private static final int RING_WORK = 2;
    private static final int RING_PICKED = 3;
    private static final int RING_POINTED = 4;
    private static final int RING_EMPTY = 5;
    private static final int TILE_FILLED = 0;
    private static final int TILE_HOLLOW = 1;
    private static final int TILE_HATCHED = 2;
    private static final int TILE_DASHED = 3;

    /** The places' colours, linear: the main force, then the bases in turn. */
    private static final float[][] PLACES = {
          { 0.22f, 0.55f, 1.0f }, { 0.62f, 0.42f, 1.0f }, { 1.0f, 0.4f, 0.72f }, { 0.85f, 0.88f, 0.95f },
          { 0.2f, 0.85f, 0.85f } };
    private static final float[] READY = { 0.25f, 0.9f, 0.42f };
    private static final float[] DAMAGED = { 1.0f, 0.7f, 0.15f };
    private static final float[] HEAVY = { 1.0f, 0.4f, 0.1f };
    private static final float[] WRECKED = { 1.0f, 0.12f, 0.08f };
    private static final float[] LAID_UP = { 0.5f, 0.56f, 0.62f };
    private static final float[] REPAIRING = { 1.0f, 0.7f, 0.22f };
    private static final float[] REFITTING = { 0.3f, 0.52f, 1.0f };
    private static final float[] AWAY = { 0.7f, 0.92f, 1.0f };
    private static final float[] PICKED = { 0.95f, 0.97f, 1.0f };
    private static final float[] SPARE = { 0.6f, 0.64f, 0.7f };
    private static final float[] EDGE = { 0.3f, 0.7f, 1.0f };
    private static final float[] TIRED = { 1.0f, 0.9f, 0.55f };
    /** A unit in none of its formation's slots, and a slot the carried load would fill. */
    private static final float[] MISFIT = { 0.78f, 0.5f, 1.0f };
    /** The order a card's readiness strip runs in: ready first, then what keeps the rest out, worst last. */
    private static final float[][] STRIP_ORDER = { READY, AWAY, DAMAGED, HEAVY, REPAIRING, REFITTING, SPARE, LAID_UP,
                                                   WRECKED };
    /** The most units a card's readiness strip parts one from the next for. */
    private static final int STRIP_PARTED_MOST = 12;
    /** A node's panel and the strip at its head, as the screen shows them, 0-255: a step up from the dark table. */
    private static final float[] PLATE_SRGB = { 20, 25, 33 };
    private static final float[] HEAD_SRGB = { 30, 38, 50 };

    /** Something leaving the table as a formation folds: where it showed, and where it goes as it sinks away. */
    private record Leaving(@Nullable Block block, @Nullable Slot slot, float[] from, float[] to) {
    }

    private final Consumer<Slot> picked;
    private final Consumer<Block> pickedCard;
    private final Consumer<Vacancy> fill;
    private final Consumer<Block> edit;
    private final Drags drags;
    /** The units in none of their formation's slots. */
    private final Set<UUID> misfits = new HashSet<>();
    /** How far each block of the layout stands up this frame, for what's laid on it. */
    private final Map<Block, Float> rises = new IdentityHashMap<>();
    /** The rules a drag keeps to, over the force as it was last shown. */
    private @Nullable ToeMoves rules;
    /** What the press under way would pick up if it drags; what's being carried, and its units, to show it gone. */
    private @Nullable Load grab;
    private @Nullable Load carried;
    private final Set<UUID> carriedUnits = new HashSet<>();
    /** The block the carried load is over, what dropping it there would do, and where on the table the pointer is. */
    private @Nullable Block over;
    private @Nullable Verdict overVerdict;
    private final Vector3 carriedAt = new Vector3();
    private boolean carriedOnTable;
    /** Whether the press under way is spent: it lifted nothing it was meant to, so it doesn't pan either. */
    private boolean spent;
    private int pressButton;
    private final Matrix4 model = new Matrix4();
    private final FloatArray holo = new FloatArray();
    /** Units' art on the graphics card by its key, kept across refreshes, and art waiting to go there. */
    private final Map<String, Texture> arts = new HashMap<>();
    private final Map<String, BufferedImage> waiting = new LinkedHashMap<>();
    private final Map<Integer, Texture> icons = new HashMap<>();
    private ShaderProgram fieldShader;
    private ShaderProgram overlayShader;
    private ShaderProgram propShader;
    private ShaderProgram holoShader;
    private PerspectiveCamera camera;
    private PerspectiveCamera probe;
    private Mesh screen;
    private Mesh box;
    private Mesh prism;
    private Mesh plane;
    private @Nullable Mesh effects;
    private @Nullable ToeLayout layout;
    private @Nullable Predicate<Card> shown;
    private @Nullable Slot hovered;
    private @Nullable Slot selected;
    private @Nullable Block hoveredCard;
    /** The unit whose run of a card's readiness strip is pointed at; null if none is. */
    private @Nullable Card hoveredUnit;
    private @Nullable Vacancy hoveredVacancy;
    /**
     * Where each block and unit shows this frame, mid-way through a fold if one's under way: the layout's own first,
     * then what's leaving; how far each stands up, 0 sunk into the table to 1, and how large each unit's counter is.
     */
    private final List<Block> posedBlocks = new ArrayList<>();
    private final FloatArray blockRise = new FloatArray();
    private final List<Slot> posedSlots = new ArrayList<>();
    private final FloatArray slotRise = new FloatArray();
    private final FloatArray slotScale = new FloatArray();
    private final Map<Block, Block> blockPoses = new IdentityHashMap<>();
    private final Map<Slot, Slot> slotPoses = new IdentityHashMap<>();
    private int settledBlocks;
    private int settledSlots;
    /**
     * The fold under way: how far, 0 to 1; where each block ({@code x, z, width, depth, rise}) and unit ({@code x, z,
     * scale, rise}) of the layout comes from; what's leaving; and how far the camera's moved to keep the point it was
     * on in place, which it eases into as everything else does.
     */
    private float morph = 1;
    private final Map<Block, float[]> blockFrom = new IdentityHashMap<>();
    private final Map<Slot, float[]> slotFrom = new IdentityHashMap<>();
    private final List<Leaving> leaving = new ArrayList<>();
    private final Vector3 slide = new Vector3();
    private final Vector3 eye = new Vector3();
    /** Whether the last zoom was the wheel's, toward the pointer, rather than the middle of the screen. */
    private boolean pointerZoom;
    /** The wheel's scrolling short of a whole notch, and who's told of each step it's turned in or out. */
    private float wheel;
    private @Nullable IntConsumer zoomed;
    /** How far the last fold moved the camera to keep the point under the pointer on what it was on. */
    private final Vector3 anchored = new Vector3();
    /** Whether the force is at more than one place, so each counter shows where it is. */
    private boolean spread;
    private float time;
    private float clock;
    private float distance = 10;
    private float wantDistance = 10;
    private final Vector3 focus = new Vector3();
    private final Vector3 wantFocus = new Vector3();
    private int pressX;
    private int pressY;
    private int lastX;
    private int lastY;
    private int pointerX;
    private int pointerY;
    private boolean dragged;
    private boolean still;

    private final InputProcessor input = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (carried != null) {
                return true;
            }
            pressX = screenX;
            pressY = screenY;
            lastX = screenX;
            lastY = screenY;
            pointerX = screenX;
            pointerY = screenY;
            pressButton = button;
            dragged = false;
            spent = false;
            grab = button == Input.Buttons.LEFT ? grabAt(screenX, screenY) : null;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            pointerX = screenX;
            pointerY = screenY;
            if (Math.abs(screenX - pressX) + Math.abs(screenY - pressY) > CLICK_SLOP && !dragged) {
                dragged = true;
                if (grab != null) {
                    Load lifted = grab;
                    grab = null;
                    if (drags.lift(lifted)) {
                        carry(lifted);
                    } else {
                        spent = true;
                    }
                }
            }
            if (carried != null) {
                aimDrop();
            } else if (dragged && !spent) {
                // The table follows the pointer: straight down, a pixel is the same stretch of it everywhere
                float cell = cellPixels();
                wantFocus.add(-(screenX - lastX) / cell, 0, -(screenY - lastY) / cell);
                keepOnTable(wantFocus);
                focus.set(wantFocus);
            }
            lastX = screenX;
            lastY = screenY;
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            grab = null;
            if (carried != null) {
                if (button == pressButton) {
                    pointerX = screenX;
                    pointerY = screenY;
                    aimDrop();
                    Load load = carried;
                    Block target = over;
                    boolean moves = overVerdict != null && overVerdict.moves();
                    putDown();
                    if (moves) {
                        drags.drop(load, ToeMoves.target(target));
                    }
                }
                return true;
            }
            if (!dragged && button == Input.Buttons.RIGHT) {
                Block block = seen(screenX, screenY, new Vector3());
                if (block != null && (block.group() != null || block.attached())) {
                    edit.accept(block);
                }
                return true;
            }
            if (!dragged && button == Input.Buttons.LEFT) {
                Block card = namedAt(screenX, screenY);
                if (card != null) {
                    pointerX = screenX;
                    pointerY = screenY;
                    pointerZoom = true;
                    pickedCard.accept(card);
                    return true;
                }
                selected = pick(screenX, screenY);
                Vacancy vacancy = selected == null ? vacancyAt(screenX, screenY) : null;
                if (vacancy != null) {
                    fill.accept(vacancy);
                    return true;
                }
                picked.accept(selected);
            }
            return true;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            pointerX = screenX;
            pointerY = screenY;
            hovered = pick(screenX, screenY);
            hoveredCard = hovered == null ? namedAt(screenX, screenY) : null;
            hoveredUnit = hoveredCard == null || !hoveredCard.card() ? null
                  : stripUnitAt(hoveredCard, screenX, screenY);
            hoveredVacancy = hovered == null && hoveredCard == null ? vacancyAt(screenX, screenY) : null;
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            // A wheel's notch is a step; a touchpad's little scrolls add up to one
            wheel += amountY;
            int notches = (int) wheel;
            if (notches != 0) {
                wheel -= notches;
                pointerZoom = true;
                if (zoomed != null) {
                    zoomed.accept(notches);
                }
            }
            return true;
        }
    };

    /**
     * A click hands the unit under it to {@code picked}, or null for none, a folded formation or the name strip of an
     * open one to {@code card} and a vacant slot to {@code fill}; a right click a formation's block or card to
     * {@code edit}; {@code drags} is told what a drag picks up and where it's put down.
     */
    ToeView(Consumer<Slot> picked, Consumer<Block> card, Consumer<Vacancy> fill, Consumer<Block> edit, Drags drags) {
        this.picked = picked;
        this.pickedCard = card;
        this.fill = fill;
        this.edit = edit;
        this.drags = drags;
    }

    /**
     * What a press at the pointer would pick up: a folded formation's card, a unit, or a formation by the strip at
     * its head; null for none.
     */
    private @Nullable Load grabAt(int screenX, int screenY) {
        Vector3 hit = new Vector3();
        Block block = seen(screenX, screenY, hit);
        if (block == null || layout == null) {
            return null;
        }
        if (block.card()) {
            return ToeMoves.load(block);
        }
        Slot slot = layout.slotAt(hit.x, hit.z);
        if (slot != null && slot.block() == block) {
            return Load.of(List.of(slot.card()));
        }
        return block.group() != null && hit.z <= block.z() + block.head() ? ToeMoves.load(block) : null;
    }

    private void carry(Load load) {
        carried = load;
        carriedUnits.clear();
        List<Card> units = load.formation() != null ? load.formation().allUnits() : load.units();
        for (Card card : units) {
            carriedUnits.add(card.id());
        }
        hovered = null;
        hoveredCard = null;
        hoveredUnit = null;
        hoveredVacancy = null;
        aimDrop();
    }

    private void putDown() {
        carried = null;
        carriedUnits.clear();
        over = null;
        overVerdict = null;
    }

    /** Where the carried load would go, under the pointer as the table now stands. */
    private void aimDrop() {
        if (carried == null) {
            return;
        }
        over = seen(pointerX, pointerY, carriedAt);
        overVerdict = over == null || rules == null ? null : rules.judge(carried, ToeMoves.target(over));
        carriedOnTable = onTable(pointerX, pointerY, over == null ? 0 : top(over.level()), carriedAt) != null;
    }

    /** Drops what's being carried where it was picked up; false if nothing was. */
    boolean cancelDrag() {
        if (carried == null) {
            return false;
        }
        putDown();
        spent = true;
        return true;
    }

    /** What's being carried; null for nothing. */
    @Nullable Load carried() {
        return carried;
    }

    /** The block the carried load is over; null for none. */
    @Nullable Block dropTarget() {
        return over;
    }

    /** What dropping the carried load where it is would do; null if it's over nothing. */
    @Nullable Verdict dropVerdict() {
        return overVerdict;
    }

    /** The rules over the force as it was last shown; null before it's shown. */
    @Nullable ToeMoves rules() {
        return rules;
    }

    /** Where the pointer is, in window coordinates, y down. */
    int pointerX() {
        return pointerX;
    }

    int pointerY() {
        return pointerY;
    }

    /** Scrolls the table while a drag is held near the screen's edge, so it can carry anything anywhere. */
    private void edgeScroll(float delta) {
        if (carried == null) {
            return;
        }
        float dx = edge(pointerX, Gdx.graphics.getWidth());
        float dz = edge(pointerY, Gdx.graphics.getHeight());
        if (dx != 0 || dz != 0) {
            float step = EDGE_SPEED * delta / cellPixels();
            wantFocus.add(dx * step, 0, dz * step);
            keepOnTable(wantFocus);
            focus.set(wantFocus);
        }
        aimDrop();
    }

    /** How far into the band along one of the screen's edges {@code at} is, -1 to 1; 0 outside it. */
    private static float edge(int at, int size) {
        float band = Math.max(8, size * EDGE_BAND);
        if (at < band) {
            return -(1 - Math.max(0, at) / band);
        }
        if (at > size - band) {
            return Math.min(1, (at - (size - band)) / band);
        }
        return 0;
    }

    void create() {
        fieldShader = HoloTableView.compile(WarTableShaders.SCREEN_VERTEX, WarTableShaders.FIELD_FRAGMENT);
        overlayShader = HoloTableView.compile(WarTableShaders.SCREEN_VERTEX, WarTableShaders.OVERLAY_FRAGMENT);
        propShader = HoloTableView.compile(ToeShaders.PROP_VERTEX, ToeShaders.PROP_FRAGMENT);
        holoShader = HoloTableView.compile(ToeShaders.HOLO_VERTEX, ToeShaders.HOLO_FRAGMENT);
        camera = new PerspectiveCamera(FIELD_OF_VIEW, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        probe = new PerspectiveCamera(FIELD_OF_VIEW, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        screen = new Mesh(true, 4, 6, VertexAttribute.Position());
        screen.setVertices(new float[] { -1, -1, 0, 1, -1, 0, 1, 1, 0, -1, 1, 0 });
        screen.setIndices(new short[] { 0, 1, 2, 2, 3, 0 });
        box = box();
        prism = prism();
        plane = plane();
    }

    InputProcessor input() {
        return input;
    }

    /**
     * Tells {@code steps} of each turn of the wheel, in whole steps, out positive, toward the pointer: the zoom snaps
     * from step to step ({@link ToeFolds}), so the table picks the step and {@link #zoomTo}s it.
     */
    void onZoom(IntConsumer steps) {
        zoomed = steps;
    }

    /**
     * Glides to where a cell takes {@code cellPixels} on screen: about the point under the pointer if the wheel turned,
     * where the last fold took it, or the middle of the screen.
     */
    void zoomTo(float cellPixels) {
        float before = wantDistance;
        wantDistance = distanceFor(cellPixels);
        Vector3 under = pointerZoom ? onTable(pointerX, pointerY, 0, new Vector3()) : null;
        if (under != null) {
            under.add(anchored);
            wantFocus.sub(under).scl(wantDistance / before).add(under);
            keepOnTable(wantFocus);
        }
        anchored.setZero();
        if (still) {
            distance = wantDistance;
            focus.set(wantFocus);
        }
    }

    /** Zooms toward the middle of the screen from here on, for the keyboard. */
    void centreZoom() {
        pointerZoom = false;
    }

    /**
     * Shows {@code snapshot} laid out as {@code layout}; {@code opening}, the table rises, otherwise what was on it
     * glides to where it now stands, formations folding into cards and opening out of them, the camera keeping on
     * the point it was on. The caller frames the table ({@link #frame}).
     */
    void show(ToeSnapshot snapshot, ToeLayout next, boolean opening) {
        ToeLayout old = layout;
        if (!opening && old != null) {
            morph(old, next);
            if (still) {
                clearMorph();
                slide.setZero();
            }
        } else {
            clearMorph();
        }
        this.layout = next;
        rules = new ToeMoves(snapshot);
        misfits.clear();
        collectMisfits(snapshot.root());
        hoveredVacancy = null;
        spread = !snapshot.bases().isEmpty();
        icons.values().forEach(Texture::dispose);
        icons.clear();
        iconTextures(snapshot.root());
        Set<String> wanted = new HashSet<>();
        for (Block block : next.blocks()) {
            for (Card card : block.units()) {
                if (card.art() != null && card.image() != null) {
                    wanted.add(card.art());
                    if (!arts.containsKey(card.art())) {
                        waiting.put(card.art(), card.image());
                    }
                }
            }
        }
        for (Iterator<Map.Entry<String, Texture>> entries = arts.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<String, Texture> entry = entries.next();
            if (!wanted.contains(entry.getKey())) {
                entry.getValue().dispose();
                entries.remove();
            }
        }
        waiting.keySet().retainAll(wanted);
        hovered = rebind(hovered);
        selected = rebind(selected);
        hoveredCard = null;
        hoveredUnit = null;
        if (opening) {
            clock = still ? RISE_SECONDS : 0;
        }
        pose();
    }

    private void clearMorph() {
        morph = 1;
        blockFrom.clear();
        slotFrom.clear();
        leaving.clear();
    }

    /**
     * Sets the fold from {@code old} to {@code next} under way: each of {@code next}'s blocks and units from where it
     * shows now or, new, from where it shows within what it opens out of; each of {@code old}'s gone off the table into
     * what it's folded into.
     */
    private void morph(ToeLayout old, ToeLayout next) {
        Map<Integer, float[]> blocksWere = new HashMap<>();
        Map<UUID, float[]> slotsWere = new HashMap<>();
        List<Block> oldBlocks = settledBlocks > 0 ? posedBlocks.subList(0, settledBlocks) : old.blocks();
        List<Slot> oldSlots = settledSlots > 0 ? posedSlots.subList(0, settledSlots) : old.slots();
        for (Block block : oldBlocks) {
            blocksWere.put(block.key(), rect(block));
        }
        for (int i = 0; i < oldSlots.size(); i++) {
            Slot slot = oldSlots.get(i);
            float scale = settledSlots > 0 ? slotScale.get(i) : 1;
            slotsWere.put(slot.card().id(), new float[] { slot.x(), slot.z(), scale, 1 });
        }
        // What opens out of a node or folds into one maps between the extents of everything under it, before and after
        Map<Integer, float[]> reachWere = new HashMap<>();
        for (Block block : old.blocks()) {
            reachWere.put(block.key(), span(Objects.requireNonNull(old.reach(block.key()))));
        }
        anchor(old.blocks(), reachWere, next);
        clearMorph();
        for (Block block : next.blocks()) {
            float[] from = blocksWere.get(block.key());
            if (from == null) {
                float[][] frame = within(old.holder(block.key()), next, reachWere);
                from = frame == null ? new float[] { block.centreX(), block.centreZ(), 0, 0 }
                      : mapRect(rect(block), frame[0], frame[1]);
                from = new float[] { from[0], from[1], from[2], from[3], 0 };
            } else {
                from = new float[] { from[0], from[1], from[2], from[3], 1 };
            }
            blockFrom.put(block, from);
        }
        Set<UUID> staying = new HashSet<>();
        for (Slot slot : next.slots()) {
            staying.add(slot.card().id());
            float[] from = slotsWere.get(slot.card().id());
            if (from == null) {
                float[][] frame = within(old.holder(slot.card().id()), next, reachWere);
                if (frame == null) {
                    from = new float[] { slot.x(), slot.z(), 0, 0 };
                } else {
                    from = mapPoint(slot.x(), slot.z(), frame[0], frame[1]);
                    from = new float[] { from[0], from[1], frame[1][2] / frame[0][2], 0 };
                }
            }
            slotFrom.put(slot, from);
        }
        for (Block block : oldBlocks) {
            if (next.block(block.key()) == null) {
                Block holder = next.holder(block.key());
                float[] was = holder == null ? null : reachWere.get(holder.key());
                float[] to = was == null ? new float[] { block.centreX(), block.centreZ(), 0, 0 }
                      : mapRect(rect(block), was, reach(next, holder));
                leaving.add(new Leaving(block, null, rect(block), to));
            }
        }
        for (int i = 0; i < oldSlots.size(); i++) {
            Slot slot = oldSlots.get(i);
            if (staying.contains(slot.card().id())) {
                continue;
            }
            float scale = settledSlots > 0 ? slotScale.get(i) : 1;
            Block holder = next.holder(slot.card().id());
            float[] was = holder == null ? null : reachWere.get(holder.key());
            float[] to;
            if (was == null) {
                to = new float[] { slot.x(), slot.z(), 0 };
            } else {
                float[] now = reach(next, holder);
                float[] at = mapPoint(slot.x(), slot.z(), was, now);
                to = new float[] { at[0], at[1], scale * now[2] / Math.max(was[2], 1e-4f) };
            }
            leaving.add(new Leaving(null, slot, new float[] { slot.x(), slot.z(), scale }, to));
        }
        morph = 0;
    }

    /**
     * Where something new comes from: within what it was folded into on the table before ({@code holder}), as it
     * stands within that now; the extent of that and what's under it now, then before; null if it wasn't on the
     * table.
     */
    private static @Nullable float[][] within(@Nullable Block holder, ToeLayout next, Map<Integer, float[]> were) {
        if (holder == null) {
            return null;
        }
        Block now = next.holder(holder.key());
        float[] was = were.get(holder.key());
        return now == null || was == null ? null : new float[][] { reach(next, now), was };
    }

    /** The extent of {@code block} and everything under it in {@code layout}, as a rect. */
    private static float[] reach(ToeLayout layout, Block block) {
        float[] reach = layout.reach(block.key());
        return reach == null ? rect(block) : span(reach);
    }

    /** {@code bounds}, min x, min z, max x, max z, as a rect: its corner, then its size. */
    private static float[] span(float[] bounds) {
        return new float[] { bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1] };
    }

    /**
     * Keeps the camera on what it was looking at: the point under the pointer for the wheel, the middle of the
     * screen otherwise. Where the formation at or nearest it goes, the camera goes, easing there as the table does, so
     * what was by the pointer stays by it.
     */
    private void anchor(List<Block> oldBlocks, Map<Integer, float[]> were, ToeLayout next) {
        float lag = 1 - ease();
        eye.set(focus).mulAdd(slide, -lag);
        Vector3 point = pointerZoom ? onTable(pointerX, pointerY, 0, new Vector3()) : new Vector3(eye);
        Vector3 moved = new Vector3();
        if (point != null) {
            Block inner = null;
            float nearest = Float.MAX_VALUE;
            for (Block block : oldBlocks) {
                float dx = Math.max(0, Math.max(block.x() - point.x, point.x - block.x() - block.width()));
                float dz = Math.max(0, Math.max(block.z() - point.z, point.z - block.z() - block.depth()));
                float off = dx * dx + dz * dz;
                if (off < nearest - 1e-6f || off <= nearest + 1e-6f && inner != null && block.level() > inner.level()) {
                    nearest = off;
                    inner = block;
                }
            }
            float[] from = null;
            float[] to = null;
            if (inner != null) {
                Block now = next.block(inner.key());
                if (now != null) {
                    from = rect(inner);
                    to = inner.card() && !now.card() ? reach(next, now) : rect(now);
                } else {
                    now = next.holder(inner.key());
                    from = now == null ? null : were.get(now.key());
                    to = now == null ? null : rect(now);
                }
            }
            if (from != null && to != null) {
                float x = MathUtils.clamp(point.x, from[0], from[0] + from[2]);
                float z = MathUtils.clamp(point.z, from[1], from[1] + from[3]);
                float[] at = mapPoint(x, z, from, to);
                moved.set(at[0] - x, 0, at[1] - z);
            }
        }
        slide.scl(lag).add(moved);
        focus.add(moved);
        wantFocus.add(moved);
        anchored.set(moved);
    }

    private static float[] rect(Block block) {
        return new float[] { block.x(), block.z(), block.width(), block.depth() };
    }

    /** {@code rect} as it stands within {@code from}, carried over to stand the same way within {@code to}. */
    private static float[] mapRect(float[] rect, float[] from, float[] to) {
        float sx = to[2] / Math.max(from[2], 1e-4f);
        float sz = to[3] / Math.max(from[3], 1e-4f);
        return new float[] { to[0] + (rect[0] - from[0]) * sx, to[1] + (rect[1] - from[1]) * sz, rect[2] * sx,
                             rect[3] * sz };
    }

    private static float[] mapPoint(float x, float z, float[] from, float[] to) {
        return new float[] { to[0] + (x - from[0]) * to[2] / Math.max(from[2], 1e-4f),
                             to[1] + (z - from[1]) * to[3] / Math.max(from[3], 1e-4f) };
    }

    /** How far the fold under way has got, eased, 0 to 1. */
    private float ease() {
        return morph >= 1 ? 1 : Interpolation.pow2Out.apply(MathUtils.clamp(morph, 0, 1));
    }

    /** Places this frame's blocks and units, mid-way through the fold if one's under way. */
    private void pose() {
        posedBlocks.clear();
        blockRise.clear();
        posedSlots.clear();
        slotRise.clear();
        slotScale.clear();
        blockPoses.clear();
        slotPoses.clear();
        rises.clear();
        if (layout == null) {
            settledBlocks = 0;
            settledSlots = 0;
            return;
        }
        float e = ease();
        for (Block block : layout.blocks()) {
            float[] from = e < 1 ? blockFrom.get(block) : null;
            Block posed = from == null ? block : block.moved(MathUtils.lerp(from[0], block.x(), e),
                  MathUtils.lerp(from[1], block.z(), e), MathUtils.lerp(from[2], block.width(), e),
                  MathUtils.lerp(from[3], block.depth(), e));
            posedBlocks.add(posed);
            blockRise.add(from == null ? 1 : MathUtils.lerp(from[4], 1, e));
            blockPoses.put(block, posed);
            rises.put(block, blockRise.peek());
        }
        settledBlocks = posedBlocks.size();
        for (Slot slot : layout.slots()) {
            float[] from = e < 1 ? slotFrom.get(slot) : null;
            Block block = blockPoses.getOrDefault(slot.block(), slot.block());
            Slot posed = from == null && block == slot.block() ? slot : new Slot(slot.card(),
                  from == null ? slot.x() : MathUtils.lerp(from[0], slot.x(), e),
                  from == null ? slot.z() : MathUtils.lerp(from[1], slot.z(), e), block);
            posedSlots.add(posed);
            slotScale.add(from == null ? 1 : MathUtils.lerp(from[2], 1, e));
            slotRise.add(from == null ? 1 : MathUtils.lerp(from[3], 1, e));
            slotPoses.put(slot, posed);
        }
        settledSlots = posedSlots.size();
        if (e >= 1) {
            clearMorph();
            return;
        }
        for (Leaving gone : leaving) {
            float[] from = gone.from();
            float[] to = gone.to();
            if (gone.block() != null) {
                posedBlocks.add(gone.block().moved(MathUtils.lerp(from[0], to[0], e), MathUtils.lerp(from[1], to[1], e),
                      MathUtils.lerp(from[2], to[2], e), MathUtils.lerp(from[3], to[3], e)));
                blockRise.add(1 - e);
            } else if (gone.slot() != null) {
                posedSlots.add(new Slot(gone.slot().card(), MathUtils.lerp(from[0], to[0], e),
                      MathUtils.lerp(from[1], to[1], e), gone.slot().block()));
                slotScale.add(MathUtils.lerp(from[2], to[2], e));
                slotRise.add(1 - e);
            }
        }
    }

    private void collectMisfits(Group group) {
        misfits.addAll(ToeSlots.of(group).misfits());
        for (Group child : group.children()) {
            collectMisfits(child);
        }
    }

    /** Whether {@code card} is in none of its formation's slots. */
    boolean misfit(Card card) {
        return misfits.contains(card.id());
    }

    /** Where {@code block} shows this frame. */
    Block posed(Block block) {
        return blockPoses.getOrDefault(block, block);
    }

    /** Where {@code slot} shows this frame. */
    Slot posed(Slot slot) {
        return slotPoses.getOrDefault(slot, slot);
    }

    /** Whether a fold is under way. */
    boolean morphing() {
        return morph < 1;
    }

    /** Lights only the units {@code filter} picks, the rest dimmed; null lights them all. */
    void filter(@Nullable Predicate<Card> filter) {
        shown = filter;
    }

    /**
     * Glides, or jumps {@code now}, onto the middle of {@code bounds} from where a cell takes {@code cellPixels} on
     * screen.
     */
    void frame(float[] bounds, float cellPixels, boolean now) {
        pointerZoom = false;
        anchored.setZero();
        wantFocus.set((bounds[0] + bounds[2]) / 2, 0, (bounds[1] + bounds[3]) / 2);
        wantDistance = distanceFor(cellPixels);
        if (now || still) {
            distance = wantDistance;
            focus.set(wantFocus);
            slide.setZero();
        }
    }

    /**
     * Glides the least way that brings all of {@code bounds} on screen under the header, the camera as far off as it's
     * going; as much of it as fits from its top left if it's too big.
     */
    void reveal(float[] bounds) {
        Vector3 corner = new Vector3();
        Vector3 along = new Vector3();
        Vector3 down = new Vector3();
        Vector3 middle = new Vector3();
        // The blocks stand a little off the table, so a few goes bring them on screen
        for (int pass = 0; pass < 4; pass++) {
            aim(probe, wantFocus, wantDistance);
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            float bottom = Float.MAX_VALUE;
            float top = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                corner.set(bounds[(k & 1) == 0 ? 0 : 2], 0.3f, bounds[(k & 2) == 0 ? 1 : 3]).prj(probe.combined);
                left = Math.min(left, corner.x);
                right = Math.max(right, corner.x);
                bottom = Math.min(bottom, corner.y);
                top = Math.max(top, corner.y);
            }
            float sx = right - left > 2 * SIDE_EDGE || left < -SIDE_EDGE ? -SIDE_EDGE - left
                  : right > SIDE_EDGE ? SIDE_EDGE - right : 0;
            float sy = top - bottom > TOP_EDGE - BOTTOM_EDGE || top > TOP_EDGE ? TOP_EDGE - top
                  : bottom < BOTTOM_EDGE ? BOTTOM_EDGE - bottom : 0;
            if (Math.abs(sx) < 1e-3f && Math.abs(sy) < 1e-3f) {
                break;
            }
            middle.set(wantFocus.x, 0.3f, wantFocus.z).prj(probe.combined);
            along.set(wantFocus.x + 1, 0.3f, wantFocus.z).prj(probe.combined).sub(middle);
            down.set(wantFocus.x, 0.3f, wantFocus.z + 1).prj(probe.combined).sub(middle);
            float det = along.x * down.y - down.x * along.y;
            if (Math.abs(det) < 1e-6f) {
                break;
            }
            // What's on screen moving by (sx, sy) is the camera moving the other way
            wantFocus.x -= (sx * down.y - down.x * sy) / det;
            wantFocus.z -= (along.x * sy - sx * along.y) / det;
        }
        keepOnTable(wantFocus);
        if (still) {
            focus.set(wantFocus);
        }
    }

    /** Glides, or jumps if motion is reduced, to look at the point {@code x, z} on the table, as near as it is. */
    void lookAt(float x, float z) {
        pointerZoom = false;
        anchored.setZero();
        wantFocus.set(x, 0, z);
        keepOnTable(wantFocus);
        if (still) {
            focus.set(wantFocus);
        }
    }

    /** The point on the table the camera's going to look at, settled. */
    Vector3 looking() {
        return new Vector3(wantFocus);
    }

    /** What of the table is on screen now, as {@link ToeLayout#bounds}; null before the table's first drawn. */
    @Nullable
    float[] seen() {
        Vector3 low = onTable(0, Gdx.graphics.getHeight(), 0, new Vector3());
        Vector3 high = onTable(Gdx.graphics.getWidth(), 0, 0, new Vector3());
        return low == null || high == null ? null
              : new float[] { Math.min(low.x, high.x), Math.min(low.z, high.z), Math.max(low.x, high.x),
                              Math.max(low.z, high.z) };
    }

    /** How many pixels a cell takes on screen with all of {@code bounds} framed under the header, as {@link #frame}. */
    float fitCellPixels(float[] bounds) {
        return cellPixelsAt(fitDistance(bounds, new Vector3()));
    }

    /** How far off the camera stands for a cell to take {@code cellPixels} on screen. */
    float distanceFor(float cellPixels) {
        return Gdx.graphics.getHeight() / (2 * cellPixels * MathUtils.tanDeg(FIELD_OF_VIEW / 2));
    }

    private float cellPixelsAt(float far) {
        return Gdx.graphics.getHeight() / (2 * far * MathUtils.tanDeg(FIELD_OF_VIEW / 2));
    }

    /** Without motion, the table opens and the camera moves at once. */
    void reduceMotion(boolean reduce) {
        still = reduce;
    }

    @Nullable Slot hovered() {
        return hovered;
    }

    @Nullable Slot selected() {
        return selected;
    }

    /** The folded formation pointed at, or the open one whose head is; null if none is. */
    @Nullable Block hoveredCard() {
        return hoveredCard;
    }

    /** The unit whose run of the pointed-at card's readiness strip the pointer is on; null if it's on none. */
    @Nullable Card hoveredUnit() {
        return hoveredUnit;
    }

    /** The vacant slot pointed at; null if none is. */
    @Nullable Vacancy hoveredVacancy() {
        return hoveredVacancy;
    }

    void select(@Nullable Slot slot) {
        selected = slot;
    }

    /** Whether {@code card} is lit by the filter. */
    boolean lit(Card card) {
        return shown == null || shown.test(card);
    }

    /** How many pixels a cell takes on screen where the camera looks. */
    float cellPixels() {
        return Gdx.graphics.getHeight() / (2 * distance * MathUtils.tanDeg(FIELD_OF_VIEW / 2));
    }

    /** How far the counters have taken over from the tiles, 0 to 1. */
    float near() {
        return MathUtils.clamp((cellPixels() - NEAR_FROM) / (NEAR_TO - NEAR_FROM), 0, 1);
    }

    /** {@code world} projected to window coordinates, y up; z over 1 when it's behind the camera. */
    Vector3 project(Vector3 world) {
        return camera.project(world);
    }

    /** How high the top of a slab {@code level} deep in the tree stands, risen as far as the table is. */
    float top(int level) {
        return (SLAB + level * TERRACE) * risen(level);
    }

    void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        time += delta;
        clock += delta;
        if (morph < 1) {
            morph += delta / MORPH_SECONDS;
        }
        upload();
        edgeScroll(delta);
        float glide = still ? 1 : Math.min(1, delta * GLIDE_RATE);
        distance = MathUtils.lerp(distance, wantDistance, glide);
        focus.lerp(wantFocus, glide);
        float lag = 1 - ease();
        pose();
        aim(camera, eye.set(focus).mulAdd(slide, -lag), distance);

        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Gdx.gl.glViewport(0, 0, width, height);
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        fieldShader.bind();
        set(fieldShader, "u_inverse", camera.invProjectionView);
        set(fieldShader, "u_grid", 0, 0, ToeLayout.CELL * 0.6f);
        set(fieldShader, "u_floor", -0.002f);
        screen.render(fieldShader, GL20.GL_TRIANGLES);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        float near = near();
        if (layout != null) {
            drawSlabs();
            drawCounters();
            drawArt(near);
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        Gdx.gl.glDepthMask(false);
        if (layout != null) {
            drawHologram(near);
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        overlayShader.bind();
        set(overlayShader, "u_screen", width, height);
        screen.render(overlayShader, GL20.GL_TRIANGLES);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Points {@code into} straight down at {@code at} from {@code far} above, the table's far side up the screen. */
    private static void aim(PerspectiveCamera into, Vector3 at, float far) {
        into.viewportWidth = Gdx.graphics.getWidth();
        into.viewportHeight = Gdx.graphics.getHeight();
        into.position.set(at.x, at.y + far, at.z);
        into.direction.set(0, -1, 0);
        into.up.set(0, 0, -1);
        into.near = Math.max(0.05f, far * 0.05f);
        into.far = far * 2 + 10;
        into.update();
    }

    /** How far off the camera takes {@code bounds} in, looking at their middle, which goes in {@code aimAt}. */
    private float fitDistance(float[] bounds, Vector3 aimAt) {
        aimAt.set((bounds[0] + bounds[2]) / 2, 0, (bounds[1] + bounds[3]) / 2);
        float low = 0.5f;
        float high = 2000;
        Vector3 corner = new Vector3();
        for (int step = 0; step < 28; step++) {
            float middle = (low + high) / 2;
            aim(probe, aimAt, middle);
            boolean fits = true;
            for (int k = 0; k < 4 && fits; k++) {
                corner.set(bounds[(k & 1) == 0 ? 0 : 2], 0.3f, bounds[(k & 2) == 0 ? 1 : 3]);
                corner.prj(probe.combined);
                fits = Math.abs(corner.x) <= SIDE_EDGE && corner.y >= BOTTOM_EDGE && corner.y <= TOP_EDGE;
            }
            if (fits) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return high;
    }

    private void keepOnTable(Vector3 at) {
        if (layout != null) {
            float[] bounds = layout.bounds();
            at.x = MathUtils.clamp(at.x, bounds[0], bounds[2]);
            at.z = MathUtils.clamp(at.z, bounds[1], bounds[3]);
        }
        at.y = 0;
    }

    /** Where the pointer's ray meets the plane at height {@code y}; null if it doesn't. */
    private @Nullable Vector3 onTable(int screenX, int screenY, float y, Vector3 into) {
        if (camera == null) {
            return null;
        }
        Ray ray = camera.getPickRay(screenX, screenY);
        if (ray.direction.y > -1e-4f) {
            return null;
        }
        float t = (y - ray.origin.y) / ray.direction.y;
        return into.set(ray.direction).scl(t).add(ray.origin);
    }

    /** The unit under the pointer: on the block under it, at that block's height. */
    private @Nullable Slot pick(int screenX, int screenY) {
        Vector3 hit = new Vector3();
        Block block = seen(screenX, screenY, hit);
        if (block == null || block.card()) {
            return null;
        }
        Slot slot = layout.slotAt(hit.x, hit.z);
        return slot != null && slot.block() == block ? slot : null;
    }

    /** The vacant slot under the pointer, on the block under it; null for none. */
    private @Nullable Vacancy vacancyAt(int screenX, int screenY) {
        Vector3 hit = new Vector3();
        Block block = seen(screenX, screenY, hit);
        if (block == null || block.card()) {
            return null;
        }
        for (Vacancy vacancy : layout.vacancies()) {
            if (vacancy.block() == block && Math.abs(hit.x - block.x() - vacancy.dx()) <= ToeLayout.CELL / 2
                  && Math.abs(hit.z - block.z() - vacancy.dz()) <= ToeLayout.CELL / 2) {
                return vacancy;
            }
        }
        return null;
    }

    /** The folded formation under the pointer, or the formation whose head strip it's on; null for neither. */
    private @Nullable Block namedAt(int screenX, int screenY) {
        Vector3 hit = new Vector3();
        Block block = seen(screenX, screenY, hit);
        if (block == null || block.card()) {
            return block;
        }
        return hit.z <= block.z() + block.head() ? block : null;
    }

    /** The block the pointer sees, where on it going in {@code hit}; null for none. */
    private @Nullable Block seen(int screenX, int screenY, Vector3 hit) {
        if (layout == null) {
            return null;
        }
        // From the highest terrace down: the first block the ray meets at its own height is the one it sees
        for (int level = 6; level >= 0; level--) {
            if (onTable(screenX, screenY, top(level), hit) == null) {
                return null;
            }
            Block block = layout.blockAt(hit.x, hit.z);
            if (block != null && block.level() == level) {
                return block;
            }
        }
        return null;
    }

    private @Nullable Slot rebind(@Nullable Slot slot) {
        if (slot == null || layout == null) {
            return null;
        }
        for (Slot now : layout.slots()) {
            if (now.card().id().equals(slot.card().id())) {
                return now;
            }
        }
        return null;
    }

    private float risen(int level) {
        float t = MathUtils.clamp((clock - level * 0.08f) / RISE_SECONDS, 0, 1);
        return Interpolation.pow3Out.apply(t);
    }

    /** Sends a few of the waiting units' art to the graphics card. */
    private void upload() {
        int sent = 0;
        for (Iterator<Map.Entry<String, BufferedImage>> entries = waiting.entrySet().iterator();
              entries.hasNext() && sent < UPLOADS_PER_FRAME; sent++) {
            Map.Entry<String, BufferedImage> entry = entries.next();
            entries.remove();
            arts.put(entry.getKey(), WarTableAssets.texture(entry.getValue()));
        }
    }

    private void iconTextures(Group group) {
        if (group.icon() != null) {
            icons.put(group.id(), WarTableAssets.texture(group.icon()));
        }
        for (Group child : group.children()) {
            iconTextures(child);
        }
    }

    // --- The slabs ---

    private void drawSlabs() {
        propShader.bind();
        lights(propShader);
        set(propShader, "u_textured", 0f);
        // Flat, as the screen's own colours: lit and tone mapped, a panel this dark sinks into the table
        set(propShader, "u_flat", 1f);
        set(propShader, "u_shine", 0.05f);
        set(propShader, "u_saturation", 1f);
        set(propShader, "u_ghost", 0f);
        set(propShader, "u_emissive", 0, 0, 0);
        for (int i = 0; i < posedBlocks.size(); i++) {
            Block block = posedBlocks.get(i);
            set(propShader, "u_dim", blockLit(block) ? 1 : 0.45f);
            // A box stands out from the table as a panel of its own, a card a shade lighter than a formation laid
            // open, so it reads as a thing in itself
            float lift = Math.min(block.level(), 4) * 1.5f + (block.card() ? 3 : 0);
            float top = top(block.level()) * blockRise.get(i);
            drawPanel(block.x(), 0, block.z(), block.width(), top, block.depth(), PLATE_SRGB, lift);
            // The head strip, a shade lighter, carries the formation's name and icon
            float inset = Math.min(0.04f, block.depth() * 0.04f);
            drawPanel(block.x() + inset, top, block.z() + inset, block.width() - inset * 2, 0.006f,
                  block.head() - inset * 2, HEAD_SRGB, lift);
        }
        set(propShader, "u_flat", 0f);
        set(propShader, "u_textured", 1f);
        set(propShader, "u_alpha", 1f);
        set(propShader, "u_base", 0);
        for (int i = 0; i < posedBlocks.size(); i++) {
            Block block = posedBlocks.get(i);
            Texture icon = block.group() == null ? null : icons.get(block.group().id());
            if (icon == null || blockRise.get(i) < 0.5f) {
                continue;
            }
            float size = block.head() * 0.78f;
            float top = top(block.level()) * blockRise.get(i) + 0.008f;
            set(propShader, "u_dim", blockLit(block) ? 1 : 0.45f);
            set(propShader, "u_baseFactor", 1, 1, 1, 1);
            set(propShader, "u_emissive", 0.05f, 0.06f, 0.07f);
            model.setToTranslation(block.x() + iconInset(block) + size / 2, top, block.z() + block.head() / 2)
                  .scale(size, 1, size);
            set(propShader, "u_model", model);
            icon.bind(0);
            plane.render(propShader, GL20.GL_TRIANGLES);
        }
    }

    /** How far in from its left edge a block's icon stands, on its head strip. */
    static float iconInset(Block block) {
        return block.head() * 0.25f;
    }

    private boolean blockLit(Block block) {
        if (shown == null) {
            return true;
        }
        for (Card card : block.units()) {
            if (shown.test(card)) {
                return true;
            }
        }
        return false;
    }

    /** A box in {@code srgb}, the screen's 0-255 colour, {@code lift} lighter, drawn flat. */
    private void drawPanel(float x, float y, float z, float width, float tall, float depth, float[] srgb, float lift) {
        drawBox(x, y, z, width, tall, depth, linear(srgb[0] + lift), linear(srgb[1] + lift),
              linear(srgb[2] + lift * 1.3f));
    }

    private static float linear(float srgb) {
        return (float) Math.pow(srgb / 255, 2.2);
    }

    private void drawBox(float x, float y, float z, float width, float tall, float depth, float r, float g, float b) {
        model.setToTranslation(x, y, z).scale(width, Math.max(tall, 1e-4f), depth);
        set(propShader, "u_model", model);
        set(propShader, "u_baseFactor", r, g, b, 1);
        box.render(propShader, GL20.GL_TRIANGLES);
    }

    // --- The units ---

    /** Each unit's counter, a dark hexagon on its block, growing in as the table rises. */
    private void drawCounters() {
        float grow = grown();
        if (grow <= 0) {
            return;
        }
        set(propShader, "u_textured", 0f);
        set(propShader, "u_shine", 0.04f);
        set(propShader, "u_emissive", 0, 0, 0);
        set(propShader, "u_baseFactor", 0.05f, 0.058f, 0.068f, 1);
        for (int i = 0; i < posedSlots.size(); i++) {
            Slot slot = posedSlots.get(i);
            float size = BASE_RADIUS * grow * slotScale.get(i);
            if (size <= 1e-4f) {
                continue;
            }
            set(propShader, "u_dim", lit(slot.card()) ? 1 : 0.5f);
            model.setToTranslation(slot.x(), top(slot.block().level()) * slotRise.get(i), slot.z())
                  .scale(size, BASE_TALL, size);
            set(propShader, "u_model", model);
            prism.render(propShader, GL20.GL_TRIANGLES);
        }
    }

    /** Each unit's art laid on its counter, as it's painted, close in. */
    private void drawArt(float near) {
        float grow = grown();
        if (near <= 0 || grow <= 0) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDepthMask(false);
        set(propShader, "u_textured", 1f);
        set(propShader, "u_flat", 1f);
        set(propShader, "u_base", 0);
        set(propShader, "u_time", time);
        set(propShader, "u_emissive", 0, 0, 0);
        for (int i = 0; i < posedSlots.size(); i++) {
            Slot slot = posedSlots.get(i);
            Card card = slot.card();
            Texture art = card.art() == null ? null : arts.get(card.art());
            float rise = slotRise.get(i);
            if (art != null && rise > 0.05f) {
                drawArt(art, card, slot.x(), top(slot.block().level()) * rise + BASE_TALL + 0.002f, slot.z(),
                      grow * slotScale.get(i), near * rise * ghost(card));
            }
        }
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        set(propShader, "u_flat", 0f);
    }

    private void drawArt(Texture art, Card card, float x, float y, float z, float grow, float near) {
        float aspect = art.getWidth() / (float) art.getHeight();
        float wide = ART_WIDE;
        float deep = ART_WIDE / aspect;
        if (deep > ART_DEEP) {
            deep = ART_DEEP;
            wide = ART_DEEP * aspect;
        }
        model.setToTranslation(x, y, z);
        // A wreck lies knocked askew on its counter; one fit only for salvage, overturned
        if (card.condition() == Condition.INOPERABLE) {
            model.rotate(Vector3.Y, -28);
        } else if (card.condition() == Condition.SALVAGE) {
            model.rotate(Vector3.Y, 160);
        }
        model.scale(wide * grow, 1, deep * grow);
        boolean laidUp = laidUp(card);
        boolean wreck = !card.condition().fights();
        float dim = lit(card) ? 1 : DIMMED;
        if (laidUp) {
            dim *= 0.75f;
        }
        set(propShader, "u_model", model);
        set(propShader, "u_alpha", near * (card.deployed() ? 0.22f : 1));
        set(propShader, "u_saturation", laidUp || card.deployed() ? 0.06f : wreck ? 0.5f : 1f);
        set(propShader, "u_ghost", card.crew() == Crew.NONE ? 1f : card.crew() == Crew.PARTIAL ? 0.35f : 0f);
        set(propShader, "u_dim", dim);
        set(propShader, "u_baseFactor", 1, wreck ? 0.55f : 1, wreck ? 0.5f : 1, 1);
        art.bind(0);
        plane.render(propShader, GL20.GL_TRIANGLES);
    }

    /** How far the counters have grown in as the table rises, 0 to 1. */
    private float grown() {
        return Interpolation.pow2Out.apply(MathUtils.clamp((clock - 0.25f) / RISE_SECONDS, 0, 1));
    }

    private static boolean laidUp(Card card) {
        return card.work() == Work.MOTHBALLED || card.work() == Work.MOTHBALLING || card.work() == Work.ACTIVATING;
    }

    // --- The hologram ---

    private void drawHologram(float near) {
        holo.clear();
        float open = risen(0);
        float pixel = 1 / cellPixels();
        links(open, pixel);
        for (int i = 0; i < posedBlocks.size(); i++) {
            Block block = posedBlocks.get(i);
            float rise = blockRise.get(i);
            outline(block, open * rise, pixel, rise);
            if (block.card()) {
                strip(block, top(block.level()) * rise + 0.003f, open * rise);
            }
            if (held(block)) {
                float y = top(block.level()) * rise + 0.0025f;
                float x1 = block.x() + block.width();
                float z1 = block.z() + block.depth();
                quad(holo, block.x(), y, block.z(), -1, 1, x1, y, block.z(), 1, 1, x1, y, z1, 1, -1, block.x(), y, z1,
                      -1, -1, EDGE, open * rise, FILL, 0);
            }
        }
        float grow = grown();
        for (int i = 0; i < posedSlots.size(); i++) {
            Slot slot = posedSlots.get(i);
            unit(slot, near, open * grow * slotRise.get(i) * ghost(slot.card()), slotScale.get(i), slotRise.get(i));
        }
        if (carried != null && carriedOnTable) {
            flat(holo, carriedAt.x, carriedAt.y + 0.01f, carriedAt.z, MARK_HALF, PICKED, open, RING, RING_PICKED);
        }
        for (int i = 0; i < settledBlocks; i++) {
            Block block = posedBlocks.get(i);
            if (ToeLayout.empty(block)) {
                float[] at = ToeLayout.emptySlot(block);
                flat(holo, at[0], top(block.level()) * blockRise.get(i) + 0.003f, at[1], MARK_HALF, SPARE,
                      0.5f * open * blockRise.get(i), RING, RING_EMPTY);
            }
        }
        for (Vacancy vacancy : layout.vacancies()) {
            vacancy(vacancy, near, open * grow);
        }
        if (holo.size == 0) {
            return;
        }
        int count = holo.size / HOLO_STRIDE;
        if (effects == null || effects.getMaxVertices() < count) {
            if (effects != null) {
                effects.dispose();
            }
            effects = new Mesh(false, Math.max(count, 4096), 0, new VertexAttributes(VertexAttribute.Position(),
                  VertexAttribute.TexCoords(0), VertexAttribute.ColorUnpacked(),
                  new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_kind")));
        }
        effects.setVertices(holo.items, 0, holo.size);
        holoShader.bind();
        holoShader.setUniformMatrix("u_projView", camera.combined);
        set(holoShader, "u_time", time);
        effects.render(holoShader, GL20.GL_TRIANGLES, 0, count);
    }

    /**
     * A slot nobody fills: close in, a dashed ring with a bar for each step of the weight class it calls for; far out,
     * a faint dashed tile. One the carried units would fill, where they may go, lights up.
     */
    private void vacancy(Vacancy vacancy, float near, float open) {
        Block block = posed(vacancy.block());
        float rise = rises.getOrDefault(vacancy.block(), 1f);
        float x = block.x() + vacancy.dx();
        float z = block.z() + vacancy.dz();
        float y = top(block.level()) * rise + 0.004f;
        float strength = open * rise;
        float[] colour = SPARE;
        float bright = 0.55f;
        if (wanted(vacancy)) {
            colour = held(vacancy.block()) ? PICKED : MISFIT;
            bright = held(vacancy.block()) ? 1.3f : 0.8f + 0.25f * MathUtils.sin(time * 4);
        }
        flat(holo, x, y, z, MARK_HALF, colour, bright * strength * near, VACANCY, vacancy.slot().weight().ordinal());
        flat(holo, x, y, z, MARK_HALF, colour, 0.25f * bright * strength * (1 - near), TILE, TILE_DASHED);
    }

    /** Whether one of the units being carried would fill {@code vacancy}, and may be put where it is. */
    private boolean wanted(Vacancy vacancy) {
        if (carried == null || carried.formation() != null) {
            return false;
        }
        Verdict verdict = verdict(vacancy.block());
        if (verdict == null || !verdict.moves()) {
            return false;
        }
        for (Card card : carried.units()) {
            if (vacancy.slot().admits(card.fit())) {
                return true;
            }
        }
        return false;
    }

    /** What dropping the carried load on {@code block} would do; null while nothing's carried. */
    private @Nullable Verdict verdict(Block block) {
        return carried == null || rules == null ? null : rules.judge(carried, ToeMoves.target(block));
    }

    /** Whether {@code block} is where the carried load would go if dropped now. */
    private boolean held(Block block) {
        return over != null && over.key() == block.key() && overVerdict != null && overVerdict.moves();
    }

    /** How much of a unit shows: a ghost of itself while it's being carried away. */
    private float ghost(Card card) {
        return carriedUnits.contains(card.id()) ? 0.3f : 1;
    }

    /**
     * The lines of command down from each formation to those under it, on the table between the nodes, a pixel and a
     * half across whatever the zoom; they settle in where they now run as a fold finishes.
     */
    private void links(float open, float pixel) {
        float settle = morph >= 1 ? 1 : MathUtils.clamp((ease() - 0.6f) / 0.4f, 0, 1);
        float strength = 0.42f * open * settle;
        if (strength <= 0.001f) {
            return;
        }
        float half = Math.max(0.02f, LINE_HALF_PIXELS * pixel);
        for (Link link : layout.links()) {
            float length = (float) Math.hypot(link.x1() - link.x0(), link.z1() - link.z0());
            if (length < 1e-4f) {
                continue;
            }
            // Run on half a core's width past each end, so where two meet they join in a clean corner
            float ex = (link.x1() - link.x0()) / length * half * 0.25f;
            float ez = (link.z1() - link.z0()) / length * half * 0.25f;
            line(holo, link.x0() - ex, link.z0() - ez, link.x1() + ex, link.z1() + ez, 0.003f, half, EDGE,
                  strength * (link.level() == 0 ? 1.2f : 1), 0);
        }
    }

    /** A node's edge round its top, never thinner than a pixel and a half; running dashes while it's deployed. */
    private void outline(Block block, float open, float pixel, float rise) {
        boolean lit = blockLit(block);
        boolean pointed = hoveredCard != null && block.card() && block.key() == hoveredCard.key();
        float y = top(block.level()) * rise + 0.002f;
        float strength = (block.level() == 0 ? 0.5f : block.card() ? 0.42f : 0.34f) * (lit ? 1 : 0.35f) * open
              * (pointed ? 2.2f : 1);
        boolean deployed = block.group() != null && block.group().deployed();
        // A deployed formation's edge runs in dashes in the colour its units are away in
        float[] colour = pointed ? PICKED : block.group() == null ? SPARE : deployed ? AWAY : EDGE;
        Verdict verdict = verdict(block);
        if (verdict != null && verdict.moves()) {
            // Where the load may go breathes; where it would go now is lit
            colour = held(block) ? PICKED : EDGE;
            strength = open * (held(block) ? 1.3f : 0.5f + 0.12f * MathUtils.sin(time * 4));
        } else if (verdict != null && verdict.refused()) {
            colour = SPARE;
            strength *= 0.3f;
        }
        float speed = deployed ? 0.6f : 0;
        float half = Math.max(0.02f, LINE_HALF_PIXELS * pixel);
        // The cores meet at the corners: across and down run on half a core's width past them, no further
        float meet = half * 0.25f;
        float x0 = block.x();
        float z0 = block.z();
        float x1 = block.x() + block.width();
        float z1 = block.z() + block.depth();
        line(holo, x0 - meet, z0, x1 + meet, z0, y, half, colour, strength, speed);
        line(holo, x1, z0 + meet, x1, z1 - meet, y, half, colour, strength, speed);
        line(holo, x1 + meet, z1, x0 - meet, z1, y, half, colour, strength, speed);
        line(holo, x0, z1 - meet, x0, z0 + meet, y, half, colour, strength, speed);
    }

    /**
     * A folded formation's readiness strip across its card under its head: a run for each unit in its state's colour,
     * the ready first and the worst off last, so how much of the strip is green reads as how ready it is. Up to
     * {@link #STRIP_PARTED_MOST} units each run is parted from the next; past that the strip runs on unbroken, where
     * the partings would only blur. The unit pointed at on it is lit.
     */
    private void strip(Block card, float y, float strength) {
        List<Card> units = stripUnits(card);
        int count = units.size();
        if (count == 0 || strength <= 0.001f) {
            return;
        }
        boolean parted = count <= STRIP_PARTED_MOST;
        float[] span = stripSpan(card);
        float x0 = span[0];
        float x1 = span[1];
        float z0 = span[2];
        float z1 = span[3];
        int first = 0;
        for (int k = 1; k <= count; k++) {
            Card start = units.get(first);
            if (k < count && state(units.get(k)) == state(start) && lit(units.get(k)) == lit(start)) {
                continue;
            }
            float xa = x0 + (x1 - x0) * first / count;
            float xb = x0 + (x1 - x0) * k / count;
            float ua = parted ? first : 0.5f;
            float ub = parted ? k : 0.5f;
            quad(holo, xa, y, z0, ua, 1, xb, y, z0, ub, 1, xb, y, z1, ub, -1, xa, y, z1, ua, -1, state(start),
                  strength * (lit(start) ? 1 : 0.2f), STRIP, 0);
            first = k;
        }
        int pointed = hoveredUnit == null || hoveredCard == null || hoveredCard.key() != card.key() ? -1
              : units.indexOf(hoveredUnit);
        if (pointed >= 0) {
            float xa = x0 + (x1 - x0) * pointed / count;
            float xb = x0 + (x1 - x0) * (pointed + 1) / count;
            float grow = (z1 - z0) * 0.35f;
            quad(holo, xa, y + 0.001f, z0 - grow, 0.5f, 1, xb, y + 0.001f, z0 - grow, 0.5f, 1, xb, y + 0.001f,
                  z1 + grow, 0.5f, -1, xa, y + 0.001f, z1 + grow, 0.5f, -1, state(hoveredUnit), strength * 0.45f, STRIP,
                  0);
        }
    }

    /** A card's units in the order its readiness strip runs. */
    private List<Card> stripUnits(Block card) {
        List<Card> units = new ArrayList<>(card.units());
        units.sort(Comparator.comparingInt(ToeView::stripRank).thenComparing(unit -> !lit(unit)));
        return units;
    }

    /** Where a card's readiness strip runs on it: its left and right, and its far and near edge. */
    private static float[] stripSpan(Block card) {
        float inset = ToeView.iconInset(card);
        return new float[] { card.x() + inset, card.x() + card.width() - inset,
                             card.z() + card.depth() * ToeLayout.CARD_STRIP_FROM,
                             card.z() + card.depth() * ToeLayout.CARD_STRIP_TO };
    }

    /** The unit whose run of {@code card}'s readiness strip is under the pointer; null if it's not on the strip. */
    private @Nullable Card stripUnitAt(Block card, int screenX, int screenY) {
        Vector3 hit = onTable(screenX, screenY, top(card.level()), new Vector3());
        List<Card> units = stripUnits(card);
        if (hit == null || units.isEmpty()) {
            return null;
        }
        float[] span = stripSpan(card);
        // A little more than the strip itself, so a thin strip far out can still be pointed at
        float slack = card.depth() * 0.08f;
        if (hit.x < span[0] || hit.x >= span[1] || hit.z < span[2] - slack || hit.z > span[3] + slack) {
            return null;
        }
        int index = (int) ((hit.x - span[0]) / (span[1] - span[0]) * units.size());
        return units.get(MathUtils.clamp(index, 0, units.size() - 1));
    }

    private static int stripRank(Card card) {
        float[] tone = state(card);
        for (int i = 0; i < STRIP_ORDER.length; i++) {
            if (STRIP_ORDER[i] == tone) {
                return i;
            }
        }
        return STRIP_ORDER.length;
    }

    /** What's going on with a unit: its tile far out, and close in the marks about its counter. */
    private void unit(Slot slot, float near, float open, float scale, float rise) {
        Card card = slot.card();
        float x = slot.x();
        float z = slot.z();
        float y = top(slot.block().level()) * rise + BASE_TALL + 0.004f;
        float mark = MARK_HALF * scale;
        float[] tone = state(card);
        int tile = card.deployed() ? TILE_DASHED : laidUp(card) ? TILE_HATCHED
              : card.crew() == Crew.NONE ? TILE_HOLLOW : TILE_FILLED;
        if (!lit(card)) {
            flat(holo, x, y, z, mark, tone, 0.12f * open * (1 - near), TILE, tile);
            return;
        }
        flat(holo, x, y, z, mark, tone, 0.85f * open * (1 - near), TILE, tile);
        if (spread) {
            // Where it is, a pip at the counter's upper left corner
            flat(holo, x - BASE_RADIUS * 0.5f * scale, y + 0.001f, z - BASE_RADIUS * 0.866f * scale, 0.07f * scale,
                  place(card.place()), 0.9f * open * near, PIP, PIP_PLACE);
        }
        if (card.fatigued()) {
            // A tired crew, a pale crescent at its lower right
            flat(holo, x + BASE_RADIUS * 0.5f * scale, y + 0.001f, z + BASE_RADIUS * 0.866f * scale, 0.085f * scale,
                  TIRED, 0.9f * open * near, PIP, PIP_TIRED);
        }
        if (misfits.contains(card.id())) {
            // In none of its formation's slots, a hollow square at its upper right
            flat(holo, x + BASE_RADIUS * 0.5f * scale, y + 0.001f, z - BASE_RADIUS * 0.866f * scale, 0.085f * scale,
                  MISFIT, 0.95f * open * near, PIP, PIP_MISFIT);
        }
        if (same(slot, selected)) {
            flat(holo, x, y + 0.001f, z, mark, PICKED, open, RING, RING_PICKED);
        } else if (same(slot, hovered)) {
            flat(holo, x, y + 0.001f, z, mark, PICKED, 0.6f * open, RING, RING_POINTED);
        }
        float strength = near * open;
        if (strength <= 0) {
            return;
        }
        Condition condition = card.condition();
        int ring = !condition.fights() || condition == Condition.CRIPPLED ? RING_PULSING
              : card.work() == Work.REPAIR || card.work() == Work.REFIT ? RING_WORK : RING_STEADY;
        // A ready unit's ring stays quiet, so the ones that aren't stand out
        flat(holo, x, y, z, mark, tone, (tone == READY ? 0.45f : 1) * strength, RING, ring);
        if (laidUp(card)) {
            flat(holo, x, y + 0.001f, z, mark, LAID_UP, strength, COVER, card.work() == Work.MOTHBALLED ? 0 : 1);
        }
        if (card.deployed()) {
            flat(holo, x, y + 0.001f, z, mark, AWAY, strength, PING, 0);
        }
        if (card.transit()) {
            flat(holo, x, y + 0.001f, z, mark, place(card.place()), 0.9f * strength, CHEVRONS, 0);
        }
    }

    private static boolean same(Slot slot, @Nullable Slot other) {
        return other != null && other.card().id().equals(slot.card().id());
    }

    /** The colour a unit shows in: what most keeps it out of the field, or ready's. */
    static float[] state(Card card) {
        if (card.deployed()) {
            return AWAY;
        }
        if (!card.condition().fights()) {
            return WRECKED;
        }
        return switch (card.work()) {
            case MOTHBALLED, MOTHBALLING, ACTIVATING -> LAID_UP;
            case REFIT -> REFITTING;
            case REPAIR -> REPAIRING;
            default -> card.condition().ordinal() >= Condition.HEAVY.ordinal() ? HEAVY
                  : card.condition() != Condition.SOUND ? DAMAGED : card.crew() == Crew.NONE ? SPARE : READY;
        };
    }

    /** The colour of a formation whose {@code share} of units is ready. */
    static float[] readiness(float share) {
        return share >= 0.75f ? READY : share >= 0.5f ? DAMAGED : WRECKED;
    }

    /** The colour of place {@code place}: the main force's, or a base's. */
    static float[] place(int place) {
        return PLACES[place == ToeSnapshot.MAIN_FORCE ? 0 : 1 + (place - 1) % (PLACES.length - 1)];
    }

    private static void line(FloatArray out, float x0, float z0, float x1, float z1, float y, float half,
          float[] colour, float strength, float speed) {
        float length = (float) Math.hypot(x1 - x0, z1 - z0);
        float px = -(z1 - z0) / length * half;
        float pz = (x1 - x0) / length * half;
        quad(out, x0 - px, y, z0 - pz, 0, 0, x1 - px, y, z1 - pz, length, 0, x1 + px, y, z1 + pz, length, 1,
              x0 + px, y, z0 + pz, 0, 1, colour, strength, LINE, speed);
    }

    /**
     * A square flat on the table at ({@code x}, {@code y}, {@code z}), {@code half} across each way, its uv from -1 to
     * 1 and up the screen as v grows.
     */
    private static void flat(FloatArray out, float x, float y, float z, float half, float[] colour, float strength,
          int kind, float parameter) {
        if (strength <= 0.001f) {
            return;
        }
        quad(out, x - half, y, z - half, -1, 1, x + half, y, z - half, 1, 1, x + half, y, z + half, 1, -1,
              x - half, y, z + half, -1, -1, colour, strength, kind, parameter);
    }

    private static void quad(FloatArray out, float x0, float y0, float z0, float u0, float v0, float x1, float y1,
          float z1, float u1, float v1, float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3,
          float u3, float v3, float[] colour, float strength, int kind, float parameter) {
        out.addAll(x0, y0, z0, u0, v0, colour[0], colour[1], colour[2], strength, kind, parameter);
        out.addAll(x1, y1, z1, u1, v1, colour[0], colour[1], colour[2], strength, kind, parameter);
        out.addAll(x2, y2, z2, u2, v2, colour[0], colour[1], colour[2], strength, kind, parameter);
        out.addAll(x0, y0, z0, u0, v0, colour[0], colour[1], colour[2], strength, kind, parameter);
        out.addAll(x2, y2, z2, u2, v2, colour[0], colour[1], colour[2], strength, kind, parameter);
        out.addAll(x3, y3, z3, u3, v3, colour[0], colour[1], colour[2], strength, kind, parameter);
    }

    // --- Meshes and shaders ---

    /** A key light from above and a little to the left, and a cool rim; fixed, so nothing shifts as the view moves. */
    private void lights(ShaderProgram shader) {
        set(shader, "u_projView", camera.combined);
        set(shader, "u_camera", camera.position.x, camera.position.y, camera.position.z);
        set(shader, "u_keyDir", 0.27f, -0.92f, -0.28f);
        set(shader, "u_rimDir", -0.4f, -0.3f, 0.87f);
    }

    /** A box from (0, 0, 0) to (1, 1, 1): its top and four sides. */
    private static Mesh box() {
        float[][] faces = {
              // normal, then the four corners
              { 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1 },
              { 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1 },
              { 0, 0, -1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0 },
              { 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1 },
              { -1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0 } };
        float[] vertices = new float[faces.length * 4 * 8];
        short[] indices = new short[faces.length * 6];
        int v = 0;
        int i = 0;
        for (int f = 0; f < faces.length; f++) {
            float[] face = faces[f];
            for (int k = 0; k < 4; k++) {
                v = vertex(vertices, v, face[3 + k * 3], face[4 + k * 3], face[5 + k * 3], face[0], face[1], face[2],
                      0, 0);
            }
            short first = (short) (f * 4);
            indices[i++] = first;
            indices[i++] = (short) (first + 1);
            indices[i++] = (short) (first + 2);
            indices[i++] = first;
            indices[i++] = (short) (first + 2);
            indices[i++] = (short) (first + 3);
        }
        return mesh(vertices, indices);
    }

    /** A flat-topped hexagonal prism, its corners a unit from its axis, from y 0 to 1; its top and sides. */
    private static Mesh prism() {
        float[] vertices = new float[(1 + 6 + 24) * 8];
        short[] indices = new short[6 * 3 + 6 * 6];
        int v = vertex(vertices, 0, 0, 1, 0, 0, 1, 0, 0, 0);
        int i = 0;
        for (int k = 0; k < 6; k++) {
            v = vertex(vertices, v, MathUtils.cosDeg(k * 60), 1, MathUtils.sinDeg(k * 60), 0, 1, 0, 0, 0);
        }
        for (int k = 0; k < 6; k++) {
            indices[i++] = 0;
            indices[i++] = (short) (1 + (k + 1) % 6);
            indices[i++] = (short) (1 + k);
        }
        for (int k = 0; k < 6; k++) {
            float nx = MathUtils.cosDeg(k * 60 + 30);
            float nz = MathUtils.sinDeg(k * 60 + 30);
            float ax = MathUtils.cosDeg(k * 60);
            float az = MathUtils.sinDeg(k * 60);
            float bx = MathUtils.cosDeg(k * 60 + 60);
            float bz = MathUtils.sinDeg(k * 60 + 60);
            short first = (short) (v / 8);
            v = vertex(vertices, v, ax, 0, az, nx, 0, nz, 0, 0);
            v = vertex(vertices, v, bx, 0, bz, nx, 0, nz, 0, 0);
            v = vertex(vertices, v, bx, 1, bz, nx, 0, nz, 0, 0);
            v = vertex(vertices, v, ax, 1, az, nx, 0, nz, 0, 0);
            indices[i++] = first;
            indices[i++] = (short) (first + 2);
            indices[i++] = (short) (first + 1);
            indices[i++] = first;
            indices[i++] = (short) (first + 3);
            indices[i++] = (short) (first + 2);
        }
        return mesh(vertices, indices);
    }

    /** A square lying flat, a unit across about its middle, facing up; its picture's top up the screen. */
    private static Mesh plane() {
        float[] vertices = new float[4 * 8];
        int v = vertex(vertices, 0, -0.5f, 0, -0.5f, 0, 1, 0, 0, 0);
        v = vertex(vertices, v, 0.5f, 0, -0.5f, 0, 1, 0, 1, 0);
        v = vertex(vertices, v, 0.5f, 0, 0.5f, 0, 1, 0, 1, 1);
        vertex(vertices, v, -0.5f, 0, 0.5f, 0, 1, 0, 0, 1);
        return mesh(vertices, new short[] { 0, 2, 1, 0, 3, 2 });
    }

    private static Mesh mesh(float[] vertices, short[] indices) {
        Mesh mesh = new Mesh(true, vertices.length / 8, indices.length, new VertexAttributes(
              VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    private static int vertex(float[] into, int at, float x, float y, float z, float nx, float ny, float nz, float u,
          float v) {
        into[at] = x;
        into[at + 1] = y;
        into[at + 2] = z;
        into[at + 3] = nx;
        into[at + 4] = ny;
        into[at + 5] = nz;
        into[at + 6] = u;
        into[at + 7] = v;
        return at + 8;
    }

    /** Sets {@code shader}'s uniform {@code name} if it reads it: its compiler drops those it doesn't. */
    private static void set(ShaderProgram shader, String name, float... values) {
        if (!shader.hasUniform(name)) {
            return;
        }
        switch (values.length) {
            case 1 -> shader.setUniformf(name, values[0]);
            case 2 -> shader.setUniformf(name, values[0], values[1]);
            case 3 -> shader.setUniformf(name, values[0], values[1], values[2]);
            default -> shader.setUniformf(name, values[0], values[1], values[2], values[3]);
        }
    }

    private static void set(ShaderProgram shader, String name, int value) {
        if (shader.hasUniform(name)) {
            shader.setUniformi(name, value);
        }
    }

    private static void set(ShaderProgram shader, String name, Matrix4 value) {
        if (shader.hasUniform(name)) {
            shader.setUniformMatrix(name, value);
        }
    }

    @Override
    public void dispose() {
        arts.values().forEach(Texture::dispose);
        arts.clear();
        icons.values().forEach(Texture::dispose);
        icons.clear();
        for (Disposable disposable : new Disposable[] { fieldShader, overlayShader, propShader, holoShader, screen, box,
                                                        prism, plane, effects }) {
            if (disposable != null) {
                disposable.dispose();
            }
        }
    }
}

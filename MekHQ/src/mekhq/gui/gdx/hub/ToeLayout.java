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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import jakarta.annotation.Nullable;
import mekhq.campaign.force.FormationSlot;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;

/**
 * Where everything stands on the TO&amp;E table: each formation a block, its own units in rows on a plate at its head
 * and its sub-formations packed under them, so the shape of the force reads from the table as the tree reads from an
 * org chart. Blocks pack row by row, as many to a row as brings their parent nearest a wide screen's proportions; the
 * units in no formation get a block of their own beside the force.
 *
 * <p>A formation folded ({@link ToeFolds}) is a card in place of its block, sized to stand a set number of pixels
 * across where it folds, so cards are born the same size on screen whatever they hold.</p>
 *
 * <p>The table's plane is x across and z towards the viewer, a unit's slot {@link #CELL} square. Plain Java, so the
 * packing can be tested without GL.</p>
 */
final class ToeLayout {
    /** A unit's slot, across. */
    static final float CELL = 1;
    /** The margin inside a block, and the strip at its head that carries its name and icon. */
    static final float PAD = 0.3f;
    static final float HEAD = 0.55f;
    /** The space between blocks packed side by side or one under another. */
    static final float GAP = 0.35f;
    /** The most units a formation's plate holds in a row before starting another. */
    static final int ROW = 6;
    /** The proportions, width over depth, that blocks are packed towards. */
    static final float ASPECT = 16 / 9f;
    /**
     * A lance's card's size on screen where it folds, in pixels, how much more each tier of formations folded into a
     * card adds to it, and how much of its depth its head takes.
     */
    static final float CARD_WIDE_PIXELS = 168;
    static final float CARD_DEEP_PIXELS = 80;
    static final float CARD_TIER_WIDE = 0.35f;
    static final float CARD_TIER_DEEP = 0.15f;
    static final float CARD_HEAD = 0.3f;

    /**
     * A formation's block: its key ({@link ToeFolds}: its group's id, or {@link ToeFolds#UNASSIGNED} or an
     * {@link ToeFolds#attached} card's), its corner nearest the table's far left ({@code x}, {@code z}) and its size,
     * how deep in the tree it is (0 for the force), the units in it and under it, and whether it's folded into a card.
     * The unassigned units' block and an attached card have no group.
     */
    record Block(int key, @Nullable Group group, String name, float x, float z, float width, float depth, int level,
          List<Card> units, boolean card) {
        boolean contains(float px, float pz) {
            return px >= x && px <= x + width && pz >= z && pz <= z + depth;
        }

        float centreX() {
            return x + width / 2;
        }

        float centreZ() {
            return z + depth / 2;
        }

        /** Whether it's a formation's own units folded apart, in its block and under its name. */
        boolean attached() {
            return card && group == null && key != ToeFolds.UNASSIGNED;
        }

        /** How deep the strip at its head is. */
        float head() {
            return card ? depth * CARD_HEAD : HEAD;
        }

        /** The same block at ({@code toX}, {@code toZ}), {@code toWidth} by {@code toDepth}. */
        Block moved(float toX, float toZ, float toWidth, float toDepth) {
            return new Block(key, group, name, toX, toZ, toWidth, toDepth, level, units, card);
        }
    }

    /** A unit's place: the middle of its slot, on the plate of {@code block}. */
    record Slot(Card card, float x, float z, Block block) {
    }

    /**
     * A slot the formation of {@code block} is authorized that nobody fills: the middle of where it shows on the
     * plate, after the units, as {@code dx}, {@code dz} from the block's corner, so it keeps its place on the block as
     * the block moves.
     */
    record Vacancy(FormationSlot slot, float dx, float dz, Block block) {
    }

    private final List<Block> blocks = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private final List<Vacancy> vacancies = new ArrayList<>();
    private final Map<Integer, Block> holders = new HashMap<>();
    private final Map<UUID, Block> cardHolders = new HashMap<>();
    private float minX = Float.MAX_VALUE;
    private float minZ = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE;
    private float maxZ = -Float.MAX_VALUE;

    private ToeLayout() {
    }

    /** Lays {@code snapshot} out with nothing folded, the force's block and the unassigned units' about the origin. */
    static ToeLayout of(ToeSnapshot snapshot, String unassignedName) {
        return of(snapshot, unassignedName, null);
    }

    /**
     * Lays {@code snapshot} out, what {@code folds} has folded as cards, the force's block and the unassigned units'
     * packed together about the origin. A formation's own units folded apart take its name.
     */
    static ToeLayout of(ToeSnapshot snapshot, String unassignedName, @Nullable ToeFolds folds) {
        Folding folding = new Folding(folds);
        List<Measure> top = new ArrayList<>();
        top.add(measure(snapshot.root(), 0, folding));
        List<Card> unassigned = snapshot.unassigned();
        if (!unassigned.isEmpty()) {
            top.add(folding.folded(ToeFolds.UNASSIGNED)
                  ? folding.card(ToeFolds.UNASSIGNED, null, unassignedName, 0, unassigned)
                  : measureUnits(ToeFolds.UNASSIGNED, null, unassignedName, unassigned, List.of(), unassigned, 0,
                  unassignedRow(unassigned.size()), List.of()));
        }
        Packing packing = pack(top);
        ToeLayout layout = new ToeLayout();
        float x = -packing.width / 2;
        float z = -packing.depth / 2;
        for (int i = 0; i < top.size(); i++) {
            layout.place(top.get(i), x + packing.at[i * 2], z + packing.at[i * 2 + 1]);
        }
        return layout;
    }

    /** As many unassigned units to a row as keeps a long list of them from trailing off in a line. */
    static int unassignedRow(int count) {
        return Math.min(count, Math.max(ROW, (int) Math.ceil(Math.sqrt(count * 2.0))));
    }

    List<Block> blocks() {
        return blocks;
    }

    List<Slot> slots() {
        return slots;
    }

    /** The slots nobody fills, on the formations' plates as they're laid out. */
    List<Vacancy> vacancies() {
        return vacancies;
    }

    /** The block keyed {@code key} if it's on the table, a card or not; null if it's folded into another. */
    @Nullable Block block(int key) {
        Block holder = holders.get(key);
        return holder != null && holder.key() == key ? holder : null;
    }

    /** The block showing what's keyed {@code key}: its own, or the card of what it's folded into; null for none. */
    @Nullable Block holder(int key) {
        return holders.get(key);
    }

    /** The block unit {@code card} shows on, or the card it's folded into; null if it's not on the table. */
    @Nullable Block holder(UUID card) {
        return cardHolders.get(card);
    }

    /** The table's extent: min x, min z, max x, max z. */
    float[] bounds() {
        return new float[] { minX, minZ, maxX, maxZ };
    }

    /**
     * The extent of the units {@code shown} picks, as {@link #bounds}: their slots, and the cards any of them are
     * folded into; null if it picks none.
     */
    @Nullable
    float[] bounds(Predicate<Card> shown) {
        float[] box = { Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
        boolean any = false;
        for (Slot slot : slots) {
            if (shown.test(slot.card())) {
                any = true;
                grow(box, slot.x() - CELL / 2, slot.z() - CELL / 2, slot.x() + CELL / 2, slot.z() + CELL / 2);
            }
        }
        for (Block block : blocks) {
            if (block.card() && block.units().stream().anyMatch(shown)) {
                any = true;
                grow(box, block.x(), block.z(), block.x() + block.width(), block.z() + block.depth());
            }
        }
        return any ? box : null;
    }

    private static void grow(float[] box, float x0, float z0, float x1, float z1) {
        box[0] = Math.min(box[0], x0);
        box[1] = Math.min(box[1], z0);
        box[2] = Math.max(box[2], x1);
        box[3] = Math.max(box[3], z1);
    }

    /** The slot under ({@code x}, {@code z}) on the table, null for none. */
    @Nullable Slot slotAt(float x, float z) {
        for (Slot slot : slots) {
            if (Math.abs(x - slot.x()) <= CELL / 2 && Math.abs(z - slot.z()) <= CELL / 2) {
                return slot;
            }
        }
        return null;
    }

    /** The innermost block under ({@code x}, {@code z}), null for none. */
    @Nullable Block blockAt(float x, float z) {
        Block found = null;
        for (Block block : blocks) {
            if (block.contains(x, z) && (found == null || block.level() >= found.level())) {
                found = block;
            }
        }
        return found;
    }

    /**
     * A block measured before it's placed: its size, and where its units and children go within it. A card's
     * {@code contents} is the formation folded into it, if it's one, its {@code all} every unit it holds.
     */
    private record Measure(int key, @Nullable Group group, String name, int level, List<Card> units,
          List<FormationSlot> vacant, List<Card> all, int columns, List<Measure> children, Packing packing, float width, float depth, boolean card,
          @Nullable Group contents) {
    }

    /** Blocks packed together: their bounds and each one's corner within them, x and z by turns. */
    private record Packing(float width, float depth, float[] at) {
    }

    /** What's folded. */
    private record Folding(@Nullable ToeFolds folds) {
        boolean folded(int key) {
            return folds != null && folds.folded(key);
        }

        Measure card(int key, @Nullable Group group, String name, int level, List<Card> units) {
            float threshold = folds == null ? ToeFolds.UNITS_FOLD : Math.max(1, folds.threshold(key));
            int tiers = tiers(group) - 1;
            return new Measure(key, group, name, level, List.of(), List.of(), units, 0, List.of(),
                  new Packing(0, 0, new float[0]),
                  CARD_WIDE_PIXELS * (1 + CARD_TIER_WIDE * tiers) / threshold,
                  CARD_DEEP_PIXELS * (1 + CARD_TIER_DEEP * tiers) / threshold, true, group);
        }

        /** How many tiers of formations fold into {@code group}'s card: 1 for a lance, 2 for a company of lances. */
        private static int tiers(@Nullable Group group) {
            int below = 0;
            if (group != null) {
                for (Group child : group.children()) {
                    below = Math.max(below, tiers(child));
                }
            }
            return 1 + below;
        }
    }

    private static Measure measure(Group group, int level, Folding folding) {
        if (level > 0 && folding.folded(group.id())) {
            return folding.card(group.id(), group, group.name(), level, group.allUnits());
        }
        List<Measure> children = new ArrayList<>();
        List<Card> units = group.units();
        List<FormationSlot> vacant = ToeSlots.of(group).vacant();
        int attached = ToeFolds.attached(group.id());
        if (!units.isEmpty() && !group.children().isEmpty() && folding.folded(attached)) {
            children.add(folding.card(attached, null, group.name(), level + 1, units));
            units = List.of();
            vacant = List.of();
        }
        for (Group child : group.children()) {
            children.add(measure(child, level + 1, folding));
        }
        return measureUnits(group.id(), group, group.name(), units, vacant, group.allUnits(), level,
              Math.min(units.size() + vacant.size(), ROW), children);
    }

    private static Measure measureUnits(int key, @Nullable Group group, String name, List<Card> units,
          List<FormationSlot> vacant, List<Card> all, int level, int row, List<Measure> children) {
        Packing packing = pack(children);
        int cells = units.size() + vacant.size();
        // A formation with nothing in it still shows a slot, to put units in
        int columns = cells == 0 ? (children.isEmpty() ? 1 : 0) : row;
        int rows = cells == 0 ? columns : (cells + columns - 1) / columns;
        float plateWidth = columns * CELL;
        float plateDepth = rows * CELL;
        float inner = Math.max(Math.max(plateWidth, packing.width), 2 * CELL);
        float depth = HEAD + plateDepth + (plateDepth > 0 && !children.isEmpty() ? GAP : 0) + packing.depth;
        return new Measure(key, group, name, level, units, vacant, all, columns, children, packing, inner + 2 * PAD,
              depth + PAD, false, null);
    }

    /**
     * Packs {@code blocks} in rows, in their order: as many to a row as brings the whole nearest {@link #ASPECT},
     * wasting least space between them.
     */
    private static Packing pack(List<Measure> blocks) {
        int count = blocks.size();
        if (count == 0) {
            return new Packing(0, 0, new float[0]);
        }
        Packing best = null;
        double bestScore = Double.MAX_VALUE;
        for (int columns = 1; columns <= count; columns++) {
            Packing packing = rows(blocks, columns);
            double area = 0;
            for (Measure block : blocks) {
                area += block.width * block.depth;
            }
            double waste = 1 - area / (packing.width * packing.depth);
            double score = Math.abs(Math.log(packing.width / packing.depth / ASPECT)) + waste;
            if (score < bestScore - 1e-6) {
                bestScore = score;
                best = packing;
            }
        }
        return best;
    }

    private static Packing rows(List<Measure> blocks, int columns) {
        float[] at = new float[blocks.size() * 2];
        float width = 0;
        float z = 0;
        for (int first = 0; first < blocks.size(); first += columns) {
            float x = 0;
            float rowDepth = 0;
            for (int i = first; i < Math.min(blocks.size(), first + columns); i++) {
                Measure block = blocks.get(i);
                at[i * 2] = x;
                at[i * 2 + 1] = z;
                x += block.width + GAP;
                rowDepth = Math.max(rowDepth, block.depth);
            }
            width = Math.max(width, x - GAP);
            z += rowDepth + GAP;
        }
        return new Packing(width, z - GAP, at);
    }

    private void place(Measure measure, float x, float z) {
        Block block = new Block(measure.key, measure.group, measure.name, x, z, measure.width, measure.depth,
              measure.level, measure.all, measure.card);
        blocks.add(block);
        holders.put(measure.key, block);
        minX = Math.min(minX, x);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x + measure.width);
        maxZ = Math.max(maxZ, z + measure.depth);
        if (measure.card) {
            if (measure.contents != null) {
                hold(measure.contents, block);
            }
            for (Card card : measure.all) {
                cardHolders.put(card.id(), block);
            }
            return;
        }
        if (measure.group != null && !measure.units.isEmpty() && !measure.children.isEmpty()) {
            holders.put(ToeFolds.attached(measure.group.id()), block);
        }
        float plateX = x + PAD;
        float plateZ = z + HEAD;
        int columns = Math.max(1, measure.columns);
        for (int i = 0; i < measure.units.size(); i++) {
            Card card = measure.units.get(i);
            slots.add(new Slot(card, plateX + (i % columns + 0.5f) * CELL, plateZ + (i / columns + 0.5f) * CELL,
                  block));
            cardHolders.put(card.id(), block);
        }
        for (int j = 0; j < measure.vacant.size(); j++) {
            int i = measure.units.size() + j;
            vacancies.add(new Vacancy(measure.vacant.get(j), PAD + (i % columns + 0.5f) * CELL,
                  HEAD + (i / columns + 0.5f) * CELL, block));
        }
        int cells = measure.units.size() + measure.vacant.size();
        int rows = cells == 0 ? measure.columns : (cells + columns - 1) / columns;
        float childrenZ = plateZ + rows * CELL + (rows > 0 && !measure.children.isEmpty() ? GAP : 0);
        for (int i = 0; i < measure.children.size(); i++) {
            place(measure.children.get(i), plateX + measure.packing.at[i * 2],
                  childrenZ + measure.packing.at[i * 2 + 1]);
        }
    }

    /** Everything folded into {@code card} under {@code group}, held by it. */
    private void hold(Group group, Block card) {
        holders.putIfAbsent(group.id(), card);
        holders.putIfAbsent(ToeFolds.attached(group.id()), card);
        for (Group child : group.children()) {
            hold(child, card);
        }
    }

    /**
     * Whether {@code block} is an empty formation's, showing the one slot to put units in; one authorized slots shows
     * those instead.
     */
    static boolean empty(Block block) {
        return !block.card() && block.units().isEmpty() && (block.group() == null
              || block.group().children().isEmpty() && block.group().slots().isEmpty());
    }

    /** The middle of an empty formation's slot, x then z. */
    static float[] emptySlot(Block block) {
        return new float[] { block.x() + PAD + CELL / 2, block.z() + HEAD + CELL / 2 };
    }
}

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
 * Where everything stands on the TO&amp;E table, laid out as an org chart: each formation a node, its name and icon
 * at its head and its own units in rows under that, with lines of command running down from it to the formations under
 * it. The upper tiers spread out side by side under their commands, a line dropping to a bar across them and down to
 * each; the lower tiers are listed one under another, indented, a line down their left to each. Every formation on a
 * tier lays out the ones under it the same way, so the chart never turns from listing back to spreading. As many tiers
 * spread as still fit a wide screen, or take little more room than listing them, so a company spreads its lances where
 * a regiment spreads its battalions and lists their companies' lances. The units in no formation get a node of their
 * own beside the force.
 *
 * <p>A formation folded ({@link ToeFolds}) is a card in place of its node and everything under it, sized to stand a
 * set number of pixels across where it folds, so cards are born the same size on screen whatever they hold. A
 * formation's own units folded apart are a card on its node, under its name.</p>
 *
 * <p>The table's plane is x across and z towards the viewer, a unit's slot {@link #CELL} square. Plain Java, so the
 * layout can be tested without GL.</p>
 */
final class ToeLayout {
    /** A unit's slot, across. */
    static final float CELL = 1;
    /** The margin inside a node, and the strip at its head that carries its name and icon where no scale's given. */
    static final float PAD = 0.2f;
    static final float HEAD = 0.55f;
    /** How far below a formation the ones it spreads out stand, the bar across them halfway down. */
    static final float DROP = 1.4f;
    /** The space between formations spread side by side. */
    static final float SPREAD_GAP = 1;
    /** How far in formations listed under another stand, the line down to them running halfway in. */
    static final float INDENT = 1;
    /** The space between formations listed one under another. */
    static final float STACK_GAP = 0.45f;
    /** The space between the force and the units in no formation. */
    static final float APART = 2.5f;
    /** The most units a formation's plate holds in a row before starting another. */
    static final int ROW = 6;
    /** The proportions, width over depth, the chart is laid out towards. */
    static final float ASPECT = 16 / 9f;
    /** How much more room than the smallest layout one spread further may take and still be chosen. */
    private static final double SPREAD_ROOM = 1.25;
    /** How wide, in the interface's pixels, a layout may stand and still be spread further: a wide screen's chart. */
    private static final double FIT_PIXELS = 1400;
    /**
     * The sizes on screen, in the interface's pixels, of what carries a name, the same at every zoom step: the strip
     * at a node's or a card's head; a card, the same for every one, so a box's size never says anything its name or
     * count doesn't, a long name cut short; and a node, unless its units take more.
     */
    static final float HEAD_PIXELS = 30;
    static final float CARD_WIDE_PIXELS = 220;
    static final float CARD_DEEP_PIXELS = 60;
    static final float CARD_HEAD = HEAD_PIXELS / CARD_DEEP_PIXELS;
    /** Where down a card its readiness strip runs from and to, its badges in the rest under it. */
    static final float CARD_STRIP_FROM = (HEAD_PIXELS + 3) / CARD_DEEP_PIXELS;
    static final float CARD_STRIP_TO = (HEAD_PIXELS + 11) / CARD_DEEP_PIXELS;
    static final float NODE_WIDE_PIXELS = 240;

    /**
     * A formation's node: its key ({@link ToeFolds}: its group's id, or {@link ToeFolds#UNASSIGNED} or an
     * {@link ToeFolds#attached} card's), its corner nearest the table's far left ({@code x}, {@code z}) and its size,
     * how deep in the tree it is (0 for the force), the units in it and under it, whether it's folded into a card, and
     * how deep the strip at its head that carries its name is. The unassigned units' node and an attached card have
     * no group.
     */
    record Block(int key, @Nullable Group group, String name, float x, float z, float width, float depth, int level,
          List<Card> units, boolean card, float head) {
        /** A node with a head of the usual depth for it. */
        Block(int key, @Nullable Group group, String name, float x, float z, float width, float depth, int level,
              List<Card> units, boolean card) {
            this(key, group, name, x, z, width, depth, level, units, card, card ? depth * CARD_HEAD : HEAD);
        }

        boolean contains(float px, float pz) {
            return px >= x && px <= x + width && pz >= z && pz <= z + depth;
        }

        float centreX() {
            return x + width / 2;
        }

        float centreZ() {
            return z + depth / 2;
        }

        /** Whether it's a formation's own units folded apart, on its node and under its name. */
        boolean attached() {
            return card && group == null && key != ToeFolds.UNASSIGNED;
        }

        /** The same node at ({@code toX}, {@code toZ}), {@code toWidth} by {@code toDepth}. */
        Block moved(float toX, float toZ, float toWidth, float toDepth) {
            return new Block(key, group, name, toX, toZ, toWidth, toDepth, level, units, card,
                  card ? toDepth * CARD_HEAD : head);
        }
    }

    /** A unit's place: the middle of its slot, on the plate of {@code block}. */
    record Slot(Card card, float x, float z, Block block) {
    }

    /**
     * A slot the formation of {@code block} is authorized that nobody fills: the middle of where it shows on the
     * plate, after the units, as {@code dx}, {@code dz} from the node's corner, so it keeps its place on the node as
     * the node moves.
     */
    record Vacancy(FormationSlot slot, float dx, float dz, Block block) {
    }

    /**
     * A straight run of a line of command, from ({@code x0}, {@code z0}) to ({@code x1}, {@code z1}), down from a
     * formation {@code level} deep in the tree to those under it.
     */
    record Link(float x0, float z0, float x1, float z1, int level) {
    }

    private final List<Block> blocks = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private final List<Vacancy> vacancies = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private final Map<Integer, Block> holders = new HashMap<>();
    private final Map<UUID, Block> cardHolders = new HashMap<>();
    private final Map<Integer, float[]> reaches = new HashMap<>();
    private float minX = Float.MAX_VALUE;
    private float minZ = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE;
    private float maxZ = -Float.MAX_VALUE;

    private ToeLayout() {
    }

    /** Lays {@code snapshot} out with nothing folded, the force and the unassigned units about the origin. */
    static ToeLayout of(ToeSnapshot snapshot, String unassignedName) {
        return of(snapshot, unassignedName, null);
    }

    /**
     * Lays {@code snapshot} out, what {@code folds} has folded as cards, the force and the unassigned units side by
     * side about the origin. A formation's own units folded apart take its name.
     */
    static ToeLayout of(ToeSnapshot snapshot, String unassignedName, @Nullable ToeFolds folds) {
        Folding folding = new Folding(folds);
        List<Card> unassigned = snapshot.unassigned();
        Node spare = null;
        if (!unassigned.isEmpty()) {
            spare = folding.folded(ToeFolds.UNASSIGNED)
                  ? folding.card(ToeFolds.UNASSIGNED, null, unassignedName, 0, unassigned)
                  : plate(ToeFolds.UNASSIGNED, null, unassignedName, unassigned, List.of(), unassigned, 0,
                  unassignedRow(unassigned.size()), true, null, folding);
        }
        Map<Integer, Node> nodes = new HashMap<>();
        // How big each way of laying it out stands on a wide screen, the most tiers spread last
        List<Tree> trees = new ArrayList<>();
        List<Double> sizes = new ArrayList<>();
        double smallest = Double.MAX_VALUE;
        for (int spread = 0; spread <= height(snapshot.root()); spread++) {
            Tree tree = tree(snapshot.root(), 0, folding, spread, nodes);
            double size = Math.max(width(tree, spare), depth(tree, spare) * ASPECT);
            trees.add(tree);
            sizes.add(size);
            smallest = Math.min(smallest, size);
        }
        // The most spread that still fits a wide screen, or takes little more room than the least, which reads more
        // plainly, but never one so much bigger that the whole force can't be seen at once
        double fits = FIT_PIXELS / folding.cell();
        Tree best = null;
        for (int i = 0; i < trees.size(); i++) {
            if (sizes.get(i) <= fits || sizes.get(i) <= smallest * SPREAD_ROOM + 1e-6) {
                best = trees.get(i);
            }
        }
        ToeLayout layout = new ToeLayout();
        float x = -width(best, spare) / 2;
        float z = -depth(best, spare) / 2;
        layout.place(best, x, z);
        if (spare != null) {
            layout.place(new Tree(spare, List.of()), x + best.width + APART, z);
        }
        return layout;
    }

    private static float width(Tree tree, @Nullable Node spare) {
        return tree.width + (spare == null ? 0 : APART + spare.width);
    }

    private static float depth(Tree tree, @Nullable Node spare) {
        return Math.max(tree.depth, spare == null ? 0 : spare.depth);
    }

    /** How many tiers of formations stand under {@code group}. */
    private static int height(Group group) {
        int height = 0;
        for (Group child : group.children()) {
            height = Math.max(height, 1 + height(child));
        }
        return height;
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

    /** The lines of command between the nodes. */
    List<Link> links() {
        return links;
    }

    /** The node keyed {@code key} if it's on the table, a card or not; null if it's folded into another. */
    @Nullable Block block(int key) {
        Block holder = holders.get(key);
        return holder != null && holder.key() == key ? holder : null;
    }

    /** The node showing what's keyed {@code key}: its own, or the card of what it's folded into; null for none. */
    @Nullable Block holder(int key) {
        return holders.get(key);
    }

    /** The node unit {@code card} shows on, or the card it's folded into; null if it's not on the table. */
    @Nullable Block holder(UUID card) {
        return cardHolders.get(card);
    }

    /** The table's extent: min x, min z, max x, max z. */
    float[] bounds() {
        return new float[] { minX, minZ, maxX, maxZ };
    }

    /**
     * The extent of the node keyed {@code key} and everything laid out under it, as {@link #bounds}; null if it's not
     * on the table.
     */
    @Nullable
    float[] reach(int key) {
        float[] reach = reaches.get(key);
        if (reach != null) {
            return reach.clone();
        }
        Block block = block(key);
        return block == null ? null
              : new float[] { block.x(), block.z(), block.x() + block.width(), block.z() + block.depth() };
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

    /** The node under ({@code x}, {@code z}), an attached card over the node it's on; null for none. */
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
     * A node measured before it's placed: its size and its units' rows. A card's {@code contents} is the formation
     * folded into it, if it's one, its {@code all} every unit it holds; {@code inner} is the card a formation's own
     * units are folded apart into, on its node.
     */
    private record Node(int key, @Nullable Group group, String name, int level, List<Card> units,
          List<FormationSlot> vacant, List<Card> all, int columns, float width, float depth, boolean card,
          @Nullable Group contents, @Nullable Node inner, float head) {
    }

    /**
     * A node and everything under it laid out about it, measured from the corner of its extent: where each of the
     * formations under it stands, where its node stands across, and the lines between them.
     */
    private static final class Tree {
        private final Node node;
        private final List<Tree> children;
        private final float[] at;
        private final List<float[]> links = new ArrayList<>();
        private float nodeX;
        private float width;
        private float depth;

        Tree(Node node, List<Tree> children) {
            this.node = node;
            this.children = children;
            at = new float[children.size() * 2];
            width = node.width;
            depth = node.depth;
        }

        /** Where the line down to it comes in, across: the middle of its node's top. */
        float attach() {
            return nodeX + node.width / 2;
        }
    }

    /** What's folded, and how many of the interface's pixels a cell takes, which sizes what carries a name. */
    private record Folding(@Nullable ToeFolds folds) {
        boolean folded(int key) {
            return folds != null && folds.folded(key);
        }

        float cell() {
            return folds == null ? ToeFolds.UNITS_PIXELS : folds.cellPixels();
        }

        Node card(int key, @Nullable Group group, String name, int level, List<Card> units) {
            float cell = cell();
            float depth = CARD_DEEP_PIXELS / cell;
            return new Node(key, group, name, level, List.of(), List.of(), units, 0, CARD_WIDE_PIXELS / cell, depth,
                  true, group, null, HEAD_PIXELS / cell);
        }
    }

    /**
     * {@code group} and everything under it, the formations on the tiers above {@code spread} spreading those under
     * them side by side and the rest listing them.
     */
    private static Tree tree(Group group, int level, Folding folding, int spread, Map<Integer, Node> nodes) {
        if (level > 0 && folding.folded(group.id())) {
            return new Tree(nodes.computeIfAbsent(group.id(),
                  id -> folding.card(id, group, group.name(), level, group.allUnits())), List.of());
        }
        Node node = nodes.computeIfAbsent(group.id(), id -> node(group, level, folding));
        List<Tree> children = new ArrayList<>();
        for (Group child : group.children()) {
            children.add(tree(child, level + 1, folding, spread, nodes));
        }
        if (children.isEmpty()) {
            return new Tree(node, children);
        }
        // A tier lays out what's under it one way throughout, so going down the chart never turns from listing back
        // to spreading, and formations side by side are always of the same tier
        return level < spread ? spread(node, children) : stack(node, children);
    }

    private static Node node(Group group, int level, Folding folding) {
        List<Card> units = group.units();
        List<FormationSlot> vacant = ToeSlots.of(group).vacant();
        Node inner = null;
        int attached = ToeFolds.attached(group.id());
        if (!units.isEmpty() && !group.children().isEmpty() && folding.folded(attached)) {
            inner = folding.card(attached, null, group.name(), level + 1, units);
            units = List.of();
            vacant = List.of();
        }
        return plate(group.id(), group, group.name(), units, vacant, group.allUnits(), level,
              Math.min(units.size() + vacant.size(), ROW), group.children().isEmpty(), inner, folding);
    }

    /**
     * A formation's node: the strip at its head with its name, and under it a plate with its units, or the card
     * they're folded into; a node with neither is its head alone.
     */
    private static Node plate(int key, @Nullable Group group, String name, List<Card> units,
          List<FormationSlot> vacant, List<Card> all, int level, int row, boolean leaf, @Nullable Node inner,
          Folding folding) {
        int cells = units.size() + vacant.size();
        // A formation with nothing in it and none under it still shows a slot, to put units in
        int columns = cells == 0 ? (leaf && inner == null ? 1 : 0) : row;
        int rows = cells == 0 ? columns : (cells + columns - 1) / columns;
        float plateWidth = inner == null ? columns * CELL : inner.width;
        float plateDepth = inner == null ? rows * CELL : inner.depth;
        float cell = folding.cell();
        float head = HEAD_PIXELS / cell;
        float width = Math.max(plateWidth + 2 * PAD, NODE_WIDE_PIXELS / cell);
        float depth = plateDepth > 0 ? head + plateDepth + PAD : head;
        return new Node(key, group, name, level, units, vacant, all, columns, width, depth, false, null, inner, head);
    }

    /** {@code node} with {@code children} listed one under another below it, indented, a line down to each. */
    private static Tree stack(Node node, List<Tree> children) {
        Tree tree = new Tree(node, children);
        float[] size = list(tree, 0, children.size(), 0, node.depth + STACK_GAP, node.depth);
        tree.width = Math.max(node.width, size[0]);
        tree.depth = node.depth + STACK_GAP + size[1];
        return tree;
    }

    /**
     * {@code node} with {@code children} side by side below it, a line dropping from it to a bar across them and down
     * to each; {@code node} stands over the middle of them.
     */
    private static Tree spread(Node node, List<Tree> children) {
        Tree tree = new Tree(node, children);
        float bar = node.depth + DROP / 2;
        float top = node.depth + DROP;
        float x = 0;
        float below = 0;
        float first = 0;
        float last = 0;
        for (int i = 0; i < children.size(); i++) {
            Tree child = children.get(i);
            tree.at[i * 2] = x;
            tree.at[i * 2 + 1] = top;
            float attach = x + child.attach();
            tree.links.add(new float[] { attach, bar, attach, top });
            below = Math.max(below, child.depth);
            if (i == 0) {
                first = attach;
            }
            last = attach;
            x += child.width + SPREAD_GAP;
        }
        // Shifted right if the node over the middle of them would stand out past the first of them
        float shift = Math.max(0, node.width / 2 - (first + last) / 2);
        for (int k = 0; k < children.size(); k++) {
            tree.at[k * 2] += shift;
        }
        for (float[] link : tree.links) {
            link[0] += shift;
            link[2] += shift;
        }
        first += shift;
        last += shift;
        float centre = (first + last) / 2;
        tree.nodeX = centre - node.width / 2;
        tree.links.add(new float[] { centre, node.depth, centre, bar });
        if (last > first) {
            tree.links.add(new float[] { first, bar, last, bar });
        }
        tree.width = Math.max(x - SPREAD_GAP + shift, tree.nodeX + node.width);
        tree.depth = top + below;
        return tree;
    }

    /**
     * Lists {@code tree}'s children {@code from} up to {@code to} one under another, indented from {@code x}, the
     * first at {@code z}, a line running down from {@code railTop} with a tick across to each; how wide and deep the
     * list stands from {@code x} and {@code z}.
     */
    private static float[] list(Tree tree, int from, int to, float x, float z, float railTop) {
        float rail = x + INDENT / 2;
        float at = z;
        float wide = 0;
        float lastTick = railTop;
        for (int k = from; k < to; k++) {
            Tree child = tree.children.get(k);
            tree.at[k * 2] = x + INDENT;
            tree.at[k * 2 + 1] = at;
            float tick = at + child.node.head() / 2;
            tree.links.add(new float[] { rail, tick, x + INDENT + child.nodeX, tick });
            lastTick = tick;
            wide = Math.max(wide, INDENT + child.width);
            at += child.depth + STACK_GAP;
        }
        tree.links.add(new float[] { rail, railTop, rail, lastTick });
        return new float[] { wide, at - STACK_GAP - z };
    }

    private void place(Tree tree, float x, float z) {
        Block block = place(tree.node, x + tree.nodeX, z);
        for (float[] link : tree.links) {
            links.add(new Link(x + link[0], z + link[1], x + link[2], z + link[3], tree.node.level));
        }
        for (int i = 0; i < tree.children.size(); i++) {
            place(tree.children.get(i), x + tree.at[i * 2], z + tree.at[i * 2 + 1]);
        }
        reaches.put(block.key(), new float[] { x, z, x + tree.width, z + tree.depth });
    }

    private Block place(Node node, float x, float z) {
        Block block = new Block(node.key, node.group, node.name, x, z, node.width, node.depth, node.level, node.all,
              node.card, node.head);
        blocks.add(block);
        holders.put(node.key, block);
        minX = Math.min(minX, x);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x + node.width);
        maxZ = Math.max(maxZ, z + node.depth);
        if (node.card) {
            if (node.contents != null) {
                hold(node.contents, block);
            }
            for (Card card : node.all) {
                cardHolders.put(card.id(), block);
            }
            return block;
        }
        float plateX = x + PAD;
        float plateZ = z + node.head;
        if (node.inner != null) {
            place(node.inner, plateX, plateZ);
        } else if (node.group != null && !node.units.isEmpty() && !node.group.children().isEmpty()) {
            holders.put(ToeFolds.attached(node.group.id()), block);
        }
        int columns = Math.max(1, node.columns);
        for (int i = 0; i < node.units.size(); i++) {
            Card card = node.units.get(i);
            slots.add(new Slot(card, plateX + (i % columns + 0.5f) * CELL, plateZ + (i / columns + 0.5f) * CELL,
                  block));
            cardHolders.put(card.id(), block);
        }
        for (int j = 0; j < node.vacant.size(); j++) {
            int i = node.units.size() + j;
            vacancies.add(new Vacancy(node.vacant.get(j), PAD + (i % columns + 0.5f) * CELL,
                  node.head + (i / columns + 0.5f) * CELL, block));
        }
        return block;
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
        return new float[] { block.x() + PAD + CELL / 2, block.z() + block.head() + CELL / 2 };
    }
}

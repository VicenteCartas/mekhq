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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The command palette's matching. Every word of the query has to match the entry's label or detail: the start of the
 * label matches best, then the start of a word in it, then anywhere in it, then the start of a word in the detail.
 * Case and accents don't matter. Equally good matches list by kind (screens first), then shorter labels first.
 */
final class PaletteSearch {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern WORD_BREAK = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int NO_MATCH = -1;

    private PaletteSearch() {
    }

    /**
     * At most {@code limit} of {@code entries} that match {@code query}, best first; with a blank query, the screens
     * and actions in their given order.
     */
    static List<PaletteEntry> find(List<PaletteEntry> entries, String query, int limit) {
        String[] words = words(query);
        if (words.length == 0) {
            return entries.stream()
                  .filter(entry -> entry.kind() == PaletteEntry.Kind.SCREEN || entry.kind() == PaletteEntry.Kind.ACTION)
                  .limit(limit)
                  .toList();
        }
        record Scored(PaletteEntry entry, int score) {
        }
        List<Scored> matches = new ArrayList<>();
        for (PaletteEntry entry : entries) {
            int score = score(entry, words);
            if (score != NO_MATCH) {
                matches.add(new Scored(entry, score));
            }
        }
        matches.sort(Comparator.comparingInt(Scored::score)
              .thenComparing(scored -> scored.entry().kind())
              .thenComparingInt(scored -> scored.entry().label().length())
              .thenComparing(scored -> scored.entry().label()));
        return matches.stream().limit(limit).map(Scored::entry).toList();
    }

    /** How well {@code entry} matches every one of {@code words}, lower is better, or {@link #NO_MATCH}. */
    static int score(PaletteEntry entry, String[] words) {
        String label = fold(entry.label());
        String detail = fold(entry.detail());
        int total = 0;
        for (String word : words) {
            int score;
            if (label.startsWith(word)) {
                score = 0;
            } else if (startsAWord(label, word)) {
                score = 1;
            } else if (label.contains(word)) {
                score = 2;
            } else if (startsAWord(detail, word) || detail.startsWith(word)) {
                score = 3;
            } else {
                return NO_MATCH;
            }
            total += score;
        }
        return total;
    }

    private static boolean startsAWord(String text, String word) {
        for (String part : WORD_BREAK.split(text)) {
            if (part.startsWith(word)) {
                return true;
            }
        }
        return false;
    }

    /** The words of {@code query}, folded as labels are. */
    static String[] words(String query) {
        String folded = fold(query).trim();
        return folded.isEmpty() ? new String[0] : SPACES.split(folded);
    }

    /** {@code text} in lower case without accents. */
    static String fold(String text) {
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }
}

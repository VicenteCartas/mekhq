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
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import jakarta.annotation.Nullable;
import megamek.logging.MMLogger;
import mekhq.gui.model.view.Borders;

/**
 * The inner sphere's borders on the dates the star map shows, read one at a time on a thread of their own and kept
 * for the last few dates, so scrubbing through history never holds up a frame. The newest date asked for is read
 * next; dates read ahead wait behind it. Any thread.
 */
final class BorderHistory {
    private static final MMLogger LOGGER = MMLogger.create(BorderHistory.class);
    /** How many dates are kept; each holds its territory, some megabytes. */
    static final int CAPACITY = 10;
    /** How many dates may wait to be read ahead. */
    private static final int AHEAD = 4;

    private final Function<LocalDate, Borders> reader;
    private final Object lock = new Object();
    /** The dates read, the least recently shown first. */
    private final Map<LocalDate, Borders> read = new LinkedHashMap<>(CAPACITY * 2, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LocalDate, Borders> eldest) {
            return size() > CAPACITY;
        }
    };
    /** The dates to read ahead, the most wanted last. */
    private final ArrayDeque<LocalDate> ahead = new ArrayDeque<>();
    /** The dates that couldn't be read, not tried again until the systems change. */
    private final Set<LocalDate> failed = new HashSet<>();
    private @Nullable LocalDate wanted;
    private @Nullable LocalDate reading;
    /** Counts the times the systems changed, so a date read from the old ones is dropped. */
    private int generation;
    private boolean closed;
    private @Nullable Thread worker;

    /** Reads the borders on a date with {@code reader}, which may be called on the history's own thread. */
    BorderHistory(Function<LocalDate, Borders> reader) {
        this.reader = reader;
    }

    /** The borders on {@code date} if they were read; else asks for them, before anything else, and returns null. */
    @Nullable Borders get(LocalDate date) {
        synchronized (lock) {
            Borders known = read.get(date);
            if (known == null && !date.equals(reading) && !failed.contains(date)) {
                wanted = date;
                wake();
            }
            return known;
        }
    }

    /** Reads the borders on {@code date} ahead of being asked, behind any date that is. */
    void prefetch(LocalDate date) {
        synchronized (lock) {
            if (read.containsKey(date) || date.equals(reading) || date.equals(wanted) || failed.contains(date)) {
                return;
            }
            ahead.remove(date);
            ahead.addLast(date);
            while (ahead.size() > AHEAD) {
                ahead.removeFirst();
            }
            wake();
        }
    }

    /** The systems changed: forgets every date read. */
    void invalidate() {
        synchronized (lock) {
            generation++;
            read.clear();
            failed.clear();
            ahead.clear();
        }
    }

    /** Stops reading; the thread ends once any date it is reading is done. */
    void close() {
        synchronized (lock) {
            closed = true;
            wanted = null;
            ahead.clear();
            lock.notifyAll();
        }
    }

    private void wake() {
        if (closed) {
            return;
        }
        if (worker == null) {
            Thread thread = new Thread(this::run, "MekHQ star map history");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            worker = thread;
            thread.start();
        }
        lock.notifyAll();
    }

    private void run() {
        while (true) {
            LocalDate date;
            int readFor;
            synchronized (lock) {
                while (!closed && wanted == null && ahead.isEmpty()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (closed) {
                    return;
                }
                if (wanted != null) {
                    date = wanted;
                    wanted = null;
                } else {
                    date = ahead.removeLast();
                }
                if (read.containsKey(date)) {
                    continue;
                }
                reading = date;
                readFor = generation;
            }
            Borders borders = null;
            long started = System.nanoTime();
            try {
                borders = reader.apply(date);
            } catch (RuntimeException failure) {
                LOGGER.warn("Couldn't read the borders on {}: {}", date, failure.getMessage());
            }
            LOGGER.debug("Read the borders on {} in {} ms", date, (System.nanoTime() - started) / 1_000_000);
            synchronized (lock) {
                reading = null;
                if (readFor == generation) {
                    if (borders == null) {
                        failed.add(date);
                    } else {
                        read.put(date, borders);
                    }
                }
            }
        }
    }
}

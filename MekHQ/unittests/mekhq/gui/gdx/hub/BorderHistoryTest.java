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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import jakarta.annotation.Nullable;
import mekhq.gui.model.view.Borders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BorderHistoryTest {
    private static final LocalDate FIRST = LocalDate.of(2765, 1, 1);
    private static final LocalDate SECOND = LocalDate.of(2800, 1, 1);
    private static final LocalDate THIRD = LocalDate.of(3025, 1, 1);
    private final List<LocalDate> asked = new CopyOnWriteArrayList<>();
    private @Nullable BorderHistory history;

    @AfterEach
    void tearDown() {
        if (history != null) {
            history.close();
        }
    }

    @Test
    void asksWithoutWaitingThenHasTheDateOnceRead() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        Borders read = mock(Borders.class);
        history = new BorderHistory(reader(date -> read, release));

        assertNull(history.get(FIRST), "never waits for the read");
        release.countDown();

        assertSame(read, await(FIRST));
        assertEquals(List.of(FIRST), asked);
    }

    @Test
    void theNewestDateAskedForIsReadNextAndThoseSkippedNever() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        history = new BorderHistory(reader(date -> mock(Borders.class), release));
        history.get(FIRST);
        awaitAsked(1);
        history.get(SECOND);
        history.get(THIRD);
        release.countDown();

        assertNotNull(await(THIRD));
        assertEquals(List.of(FIRST, THIRD), asked);
        assertNotNull(history.get(FIRST), "the first read is kept");
    }

    @Test
    void datesReadAheadWaitBehindTheOneWanted() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        history = new BorderHistory(reader(date -> mock(Borders.class), release));
        history.get(FIRST);
        awaitAsked(1);
        history.prefetch(SECOND);
        history.get(THIRD);
        release.countDown();
        awaitAsked(3);

        assertEquals(List.of(FIRST, THIRD, SECOND), asked);
        assertNotNull(await(SECOND));
    }

    @Test
    void aDateThatFailsIsNotTriedAgainUntilTheSystemsChange() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(0);
        history = new BorderHistory(reader(date -> {
            throw new IllegalStateException("no systems");
        }, release));
        history.get(FIRST);
        awaitAsked(1);
        Thread.sleep(50);
        for (int i = 0; i < 5; i++) {
            assertNull(history.get(FIRST));
        }
        Thread.sleep(50);
        assertEquals(1, asked.size());

        history.invalidate();
        history.get(FIRST);
        awaitAsked(2);
    }

    @Test
    void bordersReadFromSystemsSinceChangedAreDropped() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        history = new BorderHistory(reader(date -> mock(Borders.class), release));
        history.get(FIRST);
        awaitAsked(1);
        history.invalidate();
        release.countDown();
        Thread.sleep(50);

        assertNull(history.get(FIRST), "read again");
        assertNotNull(await(FIRST));
        assertEquals(2, asked.size());
    }

    private Function<LocalDate, Borders> reader(Function<LocalDate, Borders> read, CountDownLatch release) {
        return date -> {
            asked.add(date);
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return read.apply(date);
        };
    }

    private Borders await(LocalDate date) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < until) {
            Borders read = history.get(date);
            if (read != null) {
                return read;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("never read " + date);
    }

    private void awaitAsked(int count) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (asked.size() < count) {
            if (System.nanoTime() > until) {
                throw new AssertionError("asked " + asked);
            }
            Thread.sleep(5);
        }
    }
}

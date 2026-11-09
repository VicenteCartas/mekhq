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
package mekhq.gui.model.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import megamek.common.event.MMEvent;
import mekhq.campaign.Campaign;
import org.junit.jupiter.api.Test;

class CampaignSessionTest {
    private static class Changed extends MMEvent {}

    private static class ChangedAgain extends Changed {}

    private static class Unrelated extends MMEvent {}

    /** A view model of a counter the test changes, as a campaign's domain code would change the campaign. */
    private static final class Counter {
        private final AtomicInteger value = new AtomicInteger();
        private final AtomicInteger builds = new AtomicInteger();
        private final ViewModel<Integer> model = ViewModel.of("Counter", campaign -> {
            builds.incrementAndGet();
            return value.get();
        }, List.of(Changed.class));
    }

    private final Campaign campaign = mock(Campaign.class);

    @Test
    void theFirstSnapshotIsBuiltOnTheSwingThreadAndHandedOverOnTheGpuThread() {
        Counter counter = new Counter();
        try (SessionHarness harness = new SessionHarness(campaign)) {
            SessionHarness.Screen<Integer> screen = harness.watch(counter.model);
            assertEquals(0, counter.builds.get(), "watching builds nothing on the caller's thread");
            assertEquals(1, harness.runSwing());
            assertEquals(1, counter.builds.get());
            assertTrue(screen.snapshots().isEmpty(), "the screen gets it on its own thread");
            assertEquals(1, harness.runGpu());
            assertEquals(List.of(0), screen.snapshots());
        }
    }

    @Test
    void aBurstOfEventsRebuildsOnceAfterTheBurst() {
        Counter counter = new Counter();
        try (SessionHarness harness = new SessionHarness(campaign)) {
            SessionHarness.Screen<Integer> screen = harness.watch(counter.model);
            harness.settle();
            for (int i = 0; i < 5; i++) {
                counter.value.incrementAndGet();
                harness.fire(new Changed(), new ChangedAgain());
            }
            assertEquals(1, harness.swingQueued(), "one rebuild is queued for the whole burst");
            harness.settle();
            assertEquals(2, counter.builds.get());
            assertEquals(List.of(0, 5), screen.snapshots());
        }
    }

    @Test
    void anEventTheViewModelDoesntCareAboutRebuildsNothing() {
        Counter counter = new Counter();
        try (SessionHarness harness = new SessionHarness(campaign)) {
            harness.watch(counter.model);
            harness.settle();
            harness.fire(new Unrelated());
            assertEquals(0, harness.swingQueued());
            assertEquals(1, counter.builds.get());
        }
    }

    @Test
    void anUnchangedSnapshotIsntHandedOverAgain() {
        Counter counter = new Counter();
        try (SessionHarness harness = new SessionHarness(campaign)) {
            SessionHarness.Screen<Integer> screen = harness.watch(counter.model);
            harness.settle();
            harness.fire(new Changed());
            harness.settle();
            assertEquals(2, counter.builds.get());
            assertEquals(List.of(0), screen.snapshots());
        }
    }

    @Test
    void onlyTheDirtyViewModelsRebuild() {
        Counter first = new Counter();
        AtomicInteger otherBuilds = new AtomicInteger();
        ViewModel<String> other = ViewModel.of("Other", campaign -> "built " + otherBuilds.incrementAndGet(),
              List.of(Unrelated.class));
        try (SessionHarness harness = new SessionHarness(campaign)) {
            harness.watch(first.model);
            SessionHarness.Screen<String> otherScreen = harness.watch(other);
            harness.settle();
            harness.fire(new Changed());
            harness.settle();
            assertEquals(2, first.builds.get());
            assertEquals(List.of("built 1"), otherScreen.snapshots());
        }
    }

    @Test
    void aClosedWatchGetsNothingMoreEvenWhatWasAlreadyOnItsWay() {
        Counter counter = new Counter();
        try (SessionHarness harness = new SessionHarness(campaign)) {
            SessionHarness.Screen<Integer> screen = harness.watch(counter.model);
            harness.settle();
            counter.value.set(1);
            harness.fire(new Changed());
            harness.runSwing();
            assertEquals(1, harness.gpuQueued());
            screen.close();
            harness.settle();
            counter.value.set(2);
            harness.fire(new Changed());
            harness.settle();
            assertEquals(List.of(0), screen.snapshots());
        }
    }

    @Test
    void aClosedSessionStopsHearingEventsAndRunsNoCommands() {
        Counter counter = new Counter();
        AtomicInteger commands = new AtomicInteger();
        SessionHarness harness = new SessionHarness(campaign);
        SessionHarness.Screen<Integer> screen = harness.watch(counter.model);
        harness.settle();
        harness.session().dispatch(campaign -> commands.incrementAndGet());
        harness.close();
        assertTrue(harness.session().isClosed());
        harness.fire(new Changed());
        harness.settle();
        assertEquals(0, commands.get(), "a command queued before the session closed doesn't run");
        assertEquals(1, counter.builds.get());
        SessionHarness.Screen<Integer> late = harness.watch(counter.model);
        harness.settle();
        assertTrue(late.snapshots().isEmpty(), "watching a closed session hands nothing over");
        assertEquals(List.of(0), screen.snapshots());
    }

    @Test
    void aCommandRunsOnTheSwingThreadWithTheCampaignAndItsEventsRebuildTheScreens() {
        Counter counter = new Counter();
        try (SessionHarness harness = new SessionHarness(campaign)) {
            SessionHarness.Screen<Integer> screen = harness.watch(counter.model);
            harness.settle();
            harness.session().dispatch(target -> {
                assertSame(campaign, target);
                counter.value.set(7);
                harness.fire(new Changed());
            });
            assertEquals(0, counter.value.get(), "dispatching runs nothing on the caller's thread");
            harness.settle();
            assertEquals(List.of(0, 7), screen.snapshots());
        }
    }

    @Test
    void aFailingBuildKeepsTheLastSnapshotAndAFailingCommandStopsNothing() {
        AtomicInteger value = new AtomicInteger();
        ViewModel<Integer> fragile = ViewModel.of("Fragile", campaign -> {
            if (value.get() < 0) {
                throw new IllegalStateException("broken on purpose");
            }
            return value.get();
        }, List.of(Changed.class));
        try (SessionHarness harness = new SessionHarness(campaign)) {
            SessionHarness.Screen<Integer> screen = harness.watch(fragile);
            harness.settle();
            harness.session().dispatch(campaign -> {
                throw new IllegalStateException("broken on purpose");
            });
            harness.session().dispatch(campaign -> value.set(-1));
            harness.fire(new Changed());
            harness.settle();
            assertEquals(List.of(0), screen.snapshots());
            harness.session().dispatch(campaign -> value.set(3));
            harness.fire(new Changed());
            harness.settle();
            assertEquals(List.of(0, 3), screen.snapshots());
            assertFalse(harness.session().isClosed());
        }
    }
}

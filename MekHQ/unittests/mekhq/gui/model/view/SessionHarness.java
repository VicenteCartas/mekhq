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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import megamek.common.event.EventBus;
import megamek.common.event.MMEvent;
import mekhq.campaign.Campaign;

/**
 * Drives a {@link CampaignSession} in a plain unit test: its own event bus, and the Swing and GPU threads as queues the
 * test runs by hand, so a test sees exactly what each thread would do and in what order. Screen tests watch their view
 * models through {@link #watch} and read what the screen was handed.
 *
 * <pre>{@code
 * try (SessionHarness harness = new SessionHarness(campaign)) {
 *     SessionHarness.Screen<TopBar> topBar = harness.watch(TopBar.model(Object::toString));
 *     harness.settle();
 *     harness.fire(new NewDayEvent(campaign));
 *     harness.settle();
 *     assertEquals(2, topBar.snapshots().size());
 * }
 * }</pre>
 */
public final class SessionHarness implements AutoCloseable {
    private final EventBus bus = new EventBus();
    private final Deque<Runnable> swing = new ArrayDeque<>();
    private final Deque<Runnable> gpu = new ArrayDeque<>();
    private final CampaignSession session;

    public SessionHarness(Campaign campaign) {
        session = new CampaignSession(campaign, CampaignSession.EventSource.of(bus), swing::add, gpu::add);
    }

    /** What a watching screen was handed on the GPU thread. */
    public static final class Screen<T> {
        private final List<T> snapshots = new ArrayList<>();
        private CampaignSession.Watch watch;

        /** Every snapshot handed over, oldest first. */
        public List<T> snapshots() {
            return Collections.unmodifiableList(snapshots);
        }

        /** The last snapshot handed over. */
        public T latest() {
            if (snapshots.isEmpty()) {
                throw new IllegalStateException("The screen hasn't been handed a snapshot");
            }
            return snapshots.get(snapshots.size() - 1);
        }

        /** Stops watching, like a screen that closes. */
        public void close() {
            watch.close();
        }
    }

    public CampaignSession session() {
        return session;
    }

    /** Watches {@code model} like a screen would. */
    public <T> Screen<T> watch(ViewModel<T> model) {
        Screen<T> screen = new Screen<>();
        screen.watch = session.watch(model, screen.snapshots::add);
        return screen;
    }

    /** Fires {@code events} on the session's bus, as the campaign's domain code would. */
    public void fire(MMEvent... events) {
        for (MMEvent event : events) {
            bus.trigger(event);
        }
    }

    /** Runs the Swing thread's queued work, including work it queues itself; returns how many tasks ran. */
    public int runSwing() {
        return run(swing);
    }

    /** Runs the GPU thread's queued work; returns how many tasks ran. */
    public int runGpu() {
        return run(gpu);
    }

    /** Runs both threads until neither has work left, as a screen's handler may dispatch a command. */
    public void settle() {
        int ran;
        do {
            ran = runSwing() + runGpu();
        } while (ran > 0);
    }

    /** How many tasks wait on the Swing thread. */
    public int swingQueued() {
        return swing.size();
    }

    /** How many tasks wait on the GPU thread. */
    public int gpuQueued() {
        return gpu.size();
    }

    @Override
    public void close() {
        session.close();
    }

    private static int run(Deque<Runnable> queue) {
        int ran = 0;
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
            ran++;
        }
        return ran;
    }
}

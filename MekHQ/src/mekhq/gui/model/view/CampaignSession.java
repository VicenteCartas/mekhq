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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

import megamek.client.ui.clientGUI.boardview.gpu.GpuHost;
import megamek.common.event.EventBus;
import megamek.common.event.MMEvent;
import megamek.common.event.Subscribe;
import megamek.logging.MMLogger;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;

/**
 * The bridge between a {@link Campaign} and the screens of the native window (docs/libgdx-ui-port-plan.md,
 * Architecture).
 *
 * <ul>
 *   <li>The campaign stays on the Swing thread. Only this session's Swing-thread work touches it: building snapshots
 *   and carrying out commands.</li>
 *   <li>A screen {@link #watch watches} a {@link ViewModel}. The session builds its snapshot on the Swing thread and
 *   hands it to the screen on the GPU thread. An event the view model cares about marks the snapshot dirty, and a
 *   burst of events, such as a new day, rebuilds it once, after the burst. A snapshot equal to the last one isn't
 *   handed over again.</li>
 *   <li>A screen changes the campaign only by {@link #dispatch dispatching} a {@link CampaignCommand}. The domain code
 *   it calls fires events, and the events rebuild the snapshots.</li>
 * </ul>
 *
 * <p>Any thread may watch, dispatch or close. The event bus calls {@link #handle} on whatever thread fires the
 * event.</p>
 */
public final class CampaignSession implements AutoCloseable {
    private static final MMLogger LOGGER = MMLogger.create(CampaignSession.class);

    /** Where a session hears the campaign's events: MekHQ's event bus, or a test's own. */
    public interface EventSource {
        void register(Object handler);

        void unregister(Object handler);

        /** MekHQ's event bus. */
        static EventSource mekhq() {
            return new EventSource() {
                @Override
                public void register(Object handler) {
                    MekHQ.registerHandler(handler);
                }

                @Override
                public void unregister(Object handler) {
                    MekHQ.unregisterHandler(handler);
                }
            };
        }

        /** {@code bus}, such as a test's own. */
        static EventSource of(EventBus bus) {
            Objects.requireNonNull(bus);
            return new EventSource() {
                @Override
                public void register(Object handler) {
                    bus.register(handler);
                }

                @Override
                public void unregister(Object handler) {
                    bus.unregister(handler);
                }
            };
        }
    }

    /** A screen's watch of one view model; closing it stops the snapshots. */
    public interface Watch extends AutoCloseable {
        @Override
        void close();
    }

    private final Campaign campaign;
    private final EventSource events;
    private final Executor swing;
    private final Executor gpu;
    private final List<Watched<?>> watches = new CopyOnWriteArrayList<>();
    private final AtomicBoolean flushQueued = new AtomicBoolean();
    /** Serializes adding watches with closing, so no watch is added to a closed session. */
    private final Object lifecycle = new Object();
    private volatile boolean closed;

    /** A session on MekHQ's event bus that hands snapshots to the native window's GPU thread. */
    public CampaignSession(Campaign campaign) {
        this(campaign, EventSource.mekhq(), SwingUtilities::invokeLater, GpuHost::post);
    }

    /**
     * A session that hears {@code events}, works on the campaign through {@code swing} and hands snapshots over
     * through {@code gpu}. Tests pass queues they run by hand.
     */
    public CampaignSession(Campaign campaign, EventSource events, Executor swing, Executor gpu) {
        this.campaign = Objects.requireNonNull(campaign);
        this.events = Objects.requireNonNull(events);
        this.swing = Objects.requireNonNull(swing);
        this.gpu = Objects.requireNonNull(gpu);
        events.register(this);
    }

    /**
     * Any thread: hands {@code screen} the snapshot of {@code model} on the GPU thread, now and whenever it changes,
     * until the watch or the session is closed.
     */
    public <T> Watch watch(ViewModel<T> model, Consumer<? super T> screen) {
        Watched<T> watched = new Watched<>(Objects.requireNonNull(model), Objects.requireNonNull(screen));
        synchronized (lifecycle) {
            if (closed) {
                watched.closed = true;
                return watched;
            }
            watches.add(watched);
        }
        watched.dirty.set(true);
        queueFlush();
        return watched;
    }

    /** Any thread: carries out {@code command} on the Swing thread, unless the session closes first. */
    public void dispatch(CampaignCommand command) {
        Objects.requireNonNull(command);
        swing.execute(() -> {
            if (closed) {
                return;
            }
            try {
                command.execute(campaign);
            } catch (RuntimeException exception) {
                LOGGER.error(exception, "Campaign command {} failed", command);
            }
        });
    }

    /** Any thread: stops every watch and stops hearing the campaign's events. */
    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            for (Watched<?> watched : watches) {
                watched.closed = true;
            }
            watches.clear();
        }
        events.unregister(this);
    }

    /** Whether the session is closed. */
    public boolean isClosed() {
        return closed;
    }

    /** How many watches the session holds; for tests. */
    int watchCount() {
        return watches.size();
    }

    /** The event bus: marks the snapshots {@code event} may change as dirty. Public only so the bus can call it. */
    @Subscribe
    public void handle(MMEvent event) {
        if (closed) {
            return;
        }
        boolean dirtied = false;
        for (Watched<?> watched : watches) {
            if (watched.model.isAffectedBy(event)) {
                watched.dirty.set(true);
                dirtied = true;
            }
        }
        if (dirtied) {
            queueFlush();
        }
    }

    private void queueFlush() {
        if (flushQueued.compareAndSet(false, true)) {
            swing.execute(this::flush);
        }
    }

    /** Swing thread: rebuilds the dirty snapshots and hands over the ones that changed. */
    private void flush() {
        flushQueued.set(false);
        if (closed) {
            return;
        }
        for (Watched<?> watched : watches) {
            if (watched.dirty.getAndSet(false)) {
                watched.rebuild();
            }
        }
    }

    private final class Watched<T> implements Watch {
        private final ViewModel<T> model;
        private final Consumer<? super T> screen;
        private final AtomicBoolean dirty = new AtomicBoolean();
        /** Swing thread: the snapshot last handed over, once {@link #built}. */
        private T last;
        private boolean built;
        private volatile boolean closed;

        Watched(ViewModel<T> model, Consumer<? super T> screen) {
            this.model = model;
            this.screen = screen;
        }

        /** Swing thread. */
        void rebuild() {
            if (closed) {
                return;
            }
            T snapshot;
            try {
                snapshot = model.build(campaign);
            } catch (RuntimeException exception) {
                LOGGER.error(exception, "View model {} failed to build; the screen keeps its last snapshot", model);
                return;
            }
            if (built && Objects.equals(snapshot, last)) {
                return;
            }
            built = true;
            last = snapshot;
            gpu.execute(() -> {
                if (!closed) {
                    screen.accept(snapshot);
                }
            });
        }

        @Override
        public void close() {
            closed = true;
            watches.remove(this);
        }
    }
}

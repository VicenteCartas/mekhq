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
import java.util.function.Function;

import megamek.common.event.MMEvent;
import mekhq.campaign.Campaign;

/**
 * What a screen of the native window shows, as an immutable snapshot built from the campaign, and the events that can
 * change it. A {@link CampaignSession} builds it on the Swing thread and rebuilds it only after one of those events.
 *
 * <p>A snapshot never refers back to the campaign or to anything mutable in it, because the screen reads it on the GPU
 * thread. It should implement {@code equals}, as records do, so an unchanged snapshot isn't handed over again.</p>
 *
 * @param <T> the snapshot
 */
public interface ViewModel<T> {
    /** Swing thread: the snapshot of {@code campaign}. */
    T build(Campaign campaign);

    /** Any thread: whether {@code event} may change the snapshot. */
    boolean isAffectedBy(MMEvent event);

    /** A view model built by {@code builder} and changed by events of the {@code triggers} classes or their subclasses. */
    static <T> ViewModel<T> of(String name, Function<Campaign, T> builder, List<Class<? extends MMEvent>> triggers) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(builder);
        List<Class<? extends MMEvent>> classes = List.copyOf(triggers);
        return new ViewModel<>() {
            @Override
            public T build(Campaign campaign) {
                return builder.apply(campaign);
            }

            @Override
            public boolean isAffectedBy(MMEvent event) {
                for (Class<? extends MMEvent> trigger : classes) {
                    if (trigger.isInstance(event)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}

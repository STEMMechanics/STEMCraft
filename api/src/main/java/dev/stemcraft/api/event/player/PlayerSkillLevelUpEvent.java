/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.api.event.player;

import dev.stemcraft.api.event.BaseEvent;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired after a player's profession skill crosses one or more level thresholds.
 *
 * <p>The event contains the complete transition rather than one event per
 * threshold, so large XP awards remain a single externally consumable event.</p>
 */
@Getter
public final class PlayerSkillLevelUpEvent extends BaseEvent {
    private final UUID playerUuid;
    private final String playerName;
    private final String skillKey;
    private final String skillTitle;
    private final int previousLevel;
    private final int newLevel;
    private final double previousXp;
    private final double newXp;

    public PlayerSkillLevelUpEvent(
        @NotNull UUID playerUuid,
        @NotNull String playerName,
        @NotNull String skillKey,
        @NotNull String skillTitle,
        int previousLevel,
        int newLevel,
        double previousXp,
        double newXp
    ) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.skillKey = skillKey;
        this.skillTitle = skillTitle;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
        this.previousXp = previousXp;
        this.newXp = newXp;
    }
}

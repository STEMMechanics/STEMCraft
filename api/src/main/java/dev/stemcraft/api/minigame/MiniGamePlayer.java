/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
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

package dev.stemcraft.api.minigame;

import dev.stemcraft.api.capability.HasMeta;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a player participating in a mini-game.
 */
public interface MiniGamePlayer extends HasMeta<MiniGamePlayer> {

    /**
     * Gets the underlying Bukkit Player object.
     *
     * @return The Player object.
     */
    @NotNull Player getPlayer();

    /**
     * Gets the mini-game arena the player is currently in.
     *
     * @return The MiniGameArena instance.
     */
    MiniGameArena arena();

    /**
     * Gets the team name of the player.
     *
     * @return The team name.
     */
    String getTeam();

    /**
     * Sets the team name of the player.
     *
     * @param team The team name to set.
     */
    void setTeam(String team);

    /**
     * Gets the score of the player.
     *
     * @return The player's score.
     */
    int getScore();

    /**
     * Sets the score of the player.
     *
     * @param score The score to set.
     */
    void setScore(int score);

    /**
     * Adds to the player's score.
     *
     * @param delta The amount to add (optional, defaults to 1).
     */
    void addScore(int delta);
    default void addScore() { addScore(1); }

    /**
     * Subtracts from the player's score.
     *
     * @param delta The amount to subtract (optional, defaults to 1).
     */
    void subScore(int delta);
    default void subScore() { subScore(1); }

    /**
     * Gets the number of kills the player has.
     *
     * @return The number of kills.
     */
    int getKills();

    /**
     * Sets the number of kills the player has.
     *
     * @param kills The number of kills to set.
     */
    void setKills(int kills);

    /**
     * Increments the player's kill count by one.
     */
    void addKill();

    /**
     * Gets the number of deaths the player has.
     *
     * @return The number of deaths.
     */
    int getDeaths();

    /**
     * Sets the number of deaths the player has.
     *
     * @param deaths The number of deaths to set.
     */
    void setDeaths(int deaths);

    /**
     * Increments the player's death count by one.
     */
    void addDeath();
}
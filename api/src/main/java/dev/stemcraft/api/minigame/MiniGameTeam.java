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
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Represents a team in a mini-game.
 */
public interface MiniGameTeam extends HasMeta<MiniGameTeam> {

    /**
     * Gets the name of the team.
     *
     * @return The team name.
     */
    String getName();

    /**
     * Gets the score of the team.
     *
     * @return The team's score.
     */
    int getScore();

    /**
     * Sets the score of the team.
     *
     * @param score The score to set.
     */
    void setScore(int score);

    /**
     * Adds to the team's score.
     *
     * @param delta The amount to add (optional, defaults to 1).
     */
    void addScore(int delta);
    default void addScore() { addScore(1); }

    /**
     * Subtracts from the team's score.
     *
     * @param delta The amount to subtract (optional, defaults to 1).
     */
    void subScore(int delta);
    default void subScore() { subScore(1); }

    /**
     * Gets the spawn location of the team.
     *
     * @return The spawn location.
     */
    Location getSpawn();

    /**
     * Sets the spawn location of the team.
     *
     * @param location The location to set as spawn.
     */
    void setSpawn(Location location);

    /**
     * Teleports all team members to the team's spawn location.
     */
    void teleportAllToSpawn();

    /**
     * Adds a player to the team.
     *
     * @param player The player to add.
     */
    void addPlayer(Player player);
    void addPlayer(MiniGamePlayer player);

    /**
     * Gets a list of all players in the team.
     *
     * @return A list of players in the team.
     */
    List<Player> getPlayers();
}
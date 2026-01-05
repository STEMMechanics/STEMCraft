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

public interface MiniGamePlayer extends HasMeta {

    Player getPlayer();

    String getTeam();
    void setTeam(String team);

    int getScore();
    void setScore(int score);
    void addScore(int delta);
    default void addScore() { addScore(1); }
    void subScore(int delta);
    default void subScore() { subScore(1); }

    int getKills();
    void setKills(int kills);
    void addKill();

    int getDeaths();
    void setDeaths(int deaths);
    void addDeath();
}

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

package dev.stemcraft.service.minigame;

import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.capability.HasMetaImpl;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

class MiniGameTeamImpl extends HasMetaImpl<MiniGameTeam> implements MiniGameTeam {
    private final String id;
    @Getter
    private final String name;
    @Getter
    @Setter
    private int score;
    @Getter
    @Setter
    private Location spawn;
    private final List<Player> players = new ArrayList<>();

    MiniGameTeamImpl(String id, String name, Location spawn) {
        this.id = id;
        this.name = id;
        this.spawn = spawn;
        set("displayName", name);
    }

    String getId() {
        return id;
    }

    @Override
    public void addScore(int delta) {
        this.score += delta;
    }

    @Override
    public void subScore(int delta) {
        this.score -= delta;
    }

    @Override
    public void teleportAllToSpawn() {
        if (spawn == null) return;
        for (Player player : new ArrayList<>(players)) {
            player.teleport(spawn);
        }
    }

    @Override
    public void addPlayer(Player player) {
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    @Override
    public void addPlayer(MiniGamePlayer player) {
        if (player == null) return;
        addPlayer(player.getPlayer());
    }

    @Override
    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    void removePlayer(Player player) {
        players.remove(player);
    }
}

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

package dev.stemcraft.feature.portal;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only distortion, with explicit cleanup because fake effects do not expire client-side. */
final class PortalTravelEffects {
    private final Map<UUID, Player> viewers = new HashMap<>();

    void start(Player player) {
        if (player.hasPotionEffect(PotionEffectType.NAUSEA)) return;
        player.sendPotionEffectChange(player, new PotionEffect(PotionEffectType.NAUSEA, 200, 0, true, false, false));
        viewers.put(player.getUniqueId(), player);
    }

    void stop(Player player) {
        if (viewers.remove(player.getUniqueId()) == null || !player.isOnline()) return;
        PotionEffect real = player.getPotionEffect(PotionEffectType.NAUSEA);
        if (real == null) player.sendPotionEffectChangeRemove(player, PotionEffectType.NAUSEA);
        else player.sendPotionEffectChange(player, real);
    }

    void stopAll() {
        for (Player player : java.util.List.copyOf(viewers.values())) stop(player);
    }
}

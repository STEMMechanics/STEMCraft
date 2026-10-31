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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.service.playerprofile.PlayerProfileSection;
import dev.stemcraft.api.service.playerprofile.PlayerProfileSectionDefinition;
import dev.stemcraft.api.service.playerprofile.PlayerProfileSectionProvider;
import dev.stemcraft.api.service.playerprofile.PlayerProfileService;
import dev.stemcraft.api.service.playerprofile.PlayerProfileSnapshot;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Core aggregate for externally consumable player profile sections. */
public final class PlayerProfileServiceImpl extends BaseService implements PlayerProfileService {
    private final ConcurrentMap<String, PlayerProfileSectionProvider> providers = new ConcurrentHashMap<>();

    public PlayerProfileServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        providers.clear();
    }

    @Override
    public void onDisable() {
        providers.clear();
    }

    @Override
    public void register(@NotNull PlayerProfileSectionProvider provider) {
        String id = normalizeId(provider.id());
        if (id == null) {
            throw new IllegalArgumentException("Invalid player profile section id: " + provider.id());
        }
        providers.put(id, provider);
    }

    @Override
    public void unregister(@NotNull String id) {
        String normalized = normalizeId(id);
        if (normalized != null) {
            providers.remove(normalized);
        }
    }

    @Override
    public @NotNull List<PlayerProfileSectionDefinition> definitions() {
        return providers.values().stream()
            .sorted(Comparator.comparing(provider -> normalizeId(provider.id())))
            .map(provider -> new PlayerProfileSectionDefinition(
                normalizeId(provider.id()),
                provider.title(),
                provider.isAvailable()
            ))
            .toList();
    }

    @Override
    public @Nullable PlayerProfileSnapshot snapshot(
        @NotNull UUID playerUuid,
        @NotNull Set<String> requestedSections,
        @Nullable String period
    ) {
        PlayerService.ResolvedPlayer identity = api.players() == null
            ? null
            : api.players().resolveIdentityByUuid(playerUuid);
        if (identity == null) {
            return null;
        }

        Set<String> requested = requestedSections.stream()
            .map(PlayerProfileServiceImpl::normalizeId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<PlayerProfileSection> sections = new ArrayList<>();
        providers.values().stream()
            .sorted(Comparator.comparing(provider -> normalizeId(provider.id())))
            .forEach(provider -> {
                String id = normalizeId(provider.id());
                if (!requested.isEmpty() && !requested.contains(id)) {
                    return;
                }

                boolean available = provider.isAvailable();
                Map<String, Object> data = available
                    ? provider.snapshot(playerUuid, period)
                    : Map.of();
                sections.add(new PlayerProfileSection(id, provider.title(), available, data));
            });

        return new PlayerProfileSnapshot(
            identity.uuid(),
            identity.name(),
            identity.platform(),
            Bukkit.getPlayer(playerUuid) != null,
            Instant.now(),
            sections
        );
    }

    private static @Nullable String normalizeId(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            return null;
        }
        return normalized;
    }
}

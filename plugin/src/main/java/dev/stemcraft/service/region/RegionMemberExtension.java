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

package dev.stemcraft.service.region;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.model.RegionMemberData;
import dev.stemcraft.api.model.SCManagedRegion;
import dev.stemcraft.api.service.region.RegionDataExtension;
import dev.stemcraft.api.service.region.RegionService;
import dev.stemcraft.api.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Self-contained managed-region extension for explicit region membership.
 */
public class RegionMemberExtension implements RegionDataExtension<RegionMemberData> {
    public static final String KEY = RegionMemberData.KEY;

    private RegionService regionService;

    /**
     * Creates a new region member extension.
     */
    public RegionMemberExtension() { }

    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull RegionService service) {
        this.regionService = service;
    }

    @Override
    public @NotNull String key() {
        return KEY;
    }

    @Override
    public @NotNull Class<RegionMemberData> type() {
        return RegionMemberData.class;
    }

    @Override
    public @NotNull String description() {
        return "Explicit player and group membership for managed regions.";
    }

    @Override
    public @NotNull String commandKey() {
        return RegionMemberData.COMMAND_KEY;
    }

    @Override
    public @NotNull List<String[]> setTabCompletions() {
        return List.of(
            new String[]{"add", "{player}"},
            new String[]{"add", "g:"},
            new String[]{"remove", "{player}"},
            new String[]{"remove", "g:"}
        );
    }

    @Override
    public @NotNull List<String[]> getTabCompletions() {
        return List.<String[]>of(new String[]{});
    }

    @Override
    public @NotNull List<String[]> clearTabCompletions() {
        return List.of(
            new String[]{},
            new String[]{"{player}"},
            new String[]{"g:"}
        );
    }

    @Override
    public void onSet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        if (scope != null) {
            ctx.returnError("Region members do not support scoped values.");
        }
        onSet(ctx, region);
    }

    @Override
    public void onSet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region) {
        ctx.checkArgsSizeAtLeast(2, "/region set <id> member <add|remove> <player|g:group>");

        String action = ctx.getArgLower(0);
        MemberTarget target = parseTarget(ctx, 1);
        RegionMemberData settings = getOrCreate(region);

        switch (action) {
            case "add" -> addTarget(settings, target);
            case "remove" -> removeTarget(settings, target);
            default -> ctx.returnError("Unknown member action '" + action + "'.");
        }

        persist(region, settings);
        ctx.returnSuccess("Region members updated for '" + region.getId() + "'.");
    }

    @Override
    public void onGet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        if (scope != null) {
            ctx.returnError("Region members do not support scoped values.");
        }
        onGet(ctx, region);
    }

    @Override
    public void onGet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region) {
        RegionMemberData settings = get(region);
        if (settings == null || settings.isEmpty()) {
            ctx.returnInfo("Region '" + region.getId() + "' has no explicit members.");
        }

        ctx.info("Region members for '" + region.getId() + "':");
        ctx.info(" - players: " + formatPlayers(settings));
        ctx.info(" - groups: " + formatGroups(settings));
    }

    @Override
    public void onClear(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        if (scope != null) {
            ctx.returnError("Region members do not support scoped values.");
        }
        onClear(ctx, region);
    }

    @Override
    public void onClear(@NotNull CommandContext ctx, @NotNull SCManagedRegion region) {
        RegionMemberData settings = get(region);
        if (settings == null || settings.isEmpty()) {
            ctx.returnInfo("Region '" + region.getId() + "' has no explicit members.");
        }

        if (ctx.numArgs() == 0) {
            region.removeData(key());
            regionService.saveManagedRegion(region);
            ctx.returnSuccess("Region members cleared for '" + region.getId() + "'.");
        }

        MemberTarget target = parseTarget(ctx, 0);
        removeTarget(settings, target);
        persist(region, settings);
        ctx.returnSuccess("Region members updated for '" + region.getId() + "'.");
    }

    @Override
    public @NotNull List<String> describe(@NotNull SCManagedRegion region) {
        RegionMemberData settings = get(region);
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }

        return List.of(
            "players: " + formatPlayers(settings),
            "groups: " + formatGroups(settings)
        );
    }

    /**
     * Returns whether the player matches the region member list either directly or through a
     * configured group entry.
     *
     * @param region The managed region to test.
     * @param player The player to test.
     * @return True if the player is a region member.
     */
    public boolean isMember(@NotNull SCManagedRegion region, @NotNull Player player) {
        RegionMemberData settings = get(region);
        if (settings == null || settings.isEmpty()) {
            return false;
        }

        if (settings.hasPlayer(player.getUniqueId())) {
            return true;
        }

        for (String group : settings.groups()) {
            if (PermissionUtil.isInGroup(player.getUniqueId(), group)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the member payload for the given region, creating it when missing.
     *
     * @param region The managed region to read or update.
     * @return The member payload.
     */
    public @NotNull RegionMemberData getOrCreate(@NotNull SCManagedRegion region) {
        RegionMemberData settings = get(region);
        if (settings != null) {
            return settings;
        }

        RegionMemberData created = new RegionMemberData();
        set(region, created);
        return created;
    }

    private void addTarget(@NotNull RegionMemberData settings, @NotNull MemberTarget target) {
        if (target.group() != null) {
            settings.addGroup(target.group());
            return;
        }
        settings.addPlayer(Objects.requireNonNull(target.playerId(), "playerId"));
    }

    private void removeTarget(@NotNull RegionMemberData settings, @NotNull MemberTarget target) {
        if (target.group() != null) {
            settings.removeGroup(target.group());
            return;
        }
        settings.removePlayer(Objects.requireNonNull(target.playerId(), "playerId"));
    }

    private void persist(@NotNull SCManagedRegion region, @NotNull RegionMemberData settings) {
        if (settings.isEmpty()) {
            region.removeData(key());
        } else {
            set(region, settings);
        }
        regionService.saveManagedRegion(region);
    }

    private @NotNull String formatPlayers(@NotNull RegionMemberData settings) {
        if (settings.playerIds().isEmpty()) {
            return "(none)";
        }

        List<String> names = new ArrayList<>();
        for (String playerId : settings.playerIds()) {
            try {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerId));
                names.add(offlinePlayer.getName() == null ? playerId : offlinePlayer.getName());
            } catch (IllegalArgumentException ignored) {
                names.add(playerId);
            }
        }

        return String.join(", ", names);
    }

    private @NotNull String formatGroups(@NotNull RegionMemberData settings) {
        return settings.groups().isEmpty() ? "(none)" : String.join(", ", settings.groups());
    }

    private @NotNull MemberTarget parseTarget(@NotNull CommandContext ctx, int index) {
        String raw = ctx.getArg(index);
        if (raw == null || raw.isBlank()) {
            ctx.returnError("Missing member target.");
        }

        if (raw.startsWith("g:")) {
            String group = raw.substring(2).trim().toLowerCase(Locale.ROOT);
            if (group.isBlank()) {
                ctx.returnError("Group targets must use the form g:<group>.");
            }
            return new MemberTarget(null, group);
        }

        OfflinePlayer offlinePlayer = ctx.getArgAsOfflinePlayer(index);
        if (offlinePlayer == null) {
            ctx.returnError("Unknown player '" + raw + "'.");
        }

        return new MemberTarget(offlinePlayer.getUniqueId(), null);
    }

    private record MemberTarget(@Nullable UUID playerId, @Nullable String group) { }
}

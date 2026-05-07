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
import dev.stemcraft.api.model.RegionTitleData;
import dev.stemcraft.api.service.region.RegionDataExtension;
import dev.stemcraft.api.service.region.RegionService;
import dev.stemcraft.api.model.SCManagedRegion;
import dev.stemcraft.api.util.TextUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * Self-contained managed-region extension for region titles.
 */
public class RegionTitleExtension implements RegionDataExtension<RegionTitleData> {
    public static final String KEY = RegionTitleData.KEY;
    private static final Duration FADE_IN = Duration.ofMillis(500);
    private static final Duration STAY = Duration.ofSeconds(3);
    private static final Duration FADE_OUT = Duration.ofMillis(1000);

    private RegionService regionService;

    /**
     * Creates a new region title extension.
     */
    public RegionTitleExtension() { }

    /**
     * Registers this extension's runtime listeners when the region service enables it.
     *
     * @param api The STEMCraft API instance.
     * @param service The region service instance.
     */
    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull RegionService service) {
        this.regionService = service;
        api.events().register(PlayerMoveEvent.class, this::handleMove);
    }

    /**
     * Returns the namespaced extension key used to store title settings.
     *
     * @return The extension key.
     */
    @Override
    public @NotNull String key() {
        return KEY;
    }

    /**
     * Returns the payload type used by the title extension.
     *
     * @return The settings payload type.
     */
    @Override
    public @NotNull Class<RegionTitleData> type() {
        return RegionTitleData.class;
    }

    /**
     * Returns a short description of the extension.
     *
     * @return The extension description.
     */
    @Override
    public @NotNull String description() {
        return "Enter and exit title configuration for managed regions.";
    }

    /**
     * Returns the `/region` subcommand label for this extension.
     *
     * @return The title subcommand label.
     */
    @Override
    public @NotNull String commandKey() {
        return RegionTitleData.COMMAND_KEY;
    }

    @Override
    public @NotNull List<String[]> setTabCompletions() {
        return List.of(
            new String[]{"enter"},
            new String[]{"enter-subtitle"},
            new String[]{"exit"},
            new String[]{"exit-subtitle"}
        );
    }

    @Override
    public @NotNull List<String[]> getTabCompletions() {
        return List.of(
            new String[]{"enter"},
            new String[]{"enter-subtitle"},
            new String[]{"exit"},
            new String[]{"exit-subtitle"}
        );
    }

    @Override
    public @NotNull List<String[]> clearTabCompletions() {
        return List.of(
            new String[]{},
            new String[]{"enter"},
            new String[]{"enter-subtitle"},
            new String[]{"exit"},
            new String[]{"exit-subtitle"}
        );
    }

    @Override
    public @NotNull List<String> describe(@NotNull SCManagedRegion region) {
        if (getAll(region).isEmpty()) {
            return List.of();
        }

        List<String> out = new java.util.ArrayList<>();
        getAll(region).forEach((scope, settings) -> {
            out.add(scope + ".enter: " + valueOrUnset(settings.getEnterTitle()));
            out.add(scope + ".enter-subtitle: " + valueOrUnset(settings.getEnterSubtitle()));
            out.add(scope + ".exit: " + valueOrUnset(settings.getExitTitle()));
            out.add(scope + ".exit-subtitle: " + valueOrUnset(settings.getExitSubtitle()));
        });
        return out;
    }

    /**
     * Handles `/region set <id> title ...` command execution.
     *
     * @param ctx The command context positioned at the title arguments.
     * @param region The managed region being edited.
     */
    @Override
    public void onSet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        ctx.checkArgsSizeAtLeast(2, "/region set <id> title <enter|enter-subtitle|exit|exit-subtitle> <text> [g:<scope>]");

        String action = ctx.getArg(0).toLowerCase();
        RegionTitleData settings = getOrCreate(region, scope);

        switch (action) {
            case "enter" -> {
                settings.setEnterTitle(joinValue(ctx));
                set(region, scope, settings);
                regionService.saveManagedRegion(region);
                ctx.returnSuccess("Region enter title updated for '" + region.getId() + "'.");
            }
            case "enter-subtitle" -> {
                settings.setEnterSubtitle(joinValue(ctx));
                set(region, scope, settings);
                regionService.saveManagedRegion(region);
                ctx.returnSuccess("Region enter subtitle updated for '" + region.getId() + "'.");
            }
            case "exit" -> {
                settings.setExitTitle(joinValue(ctx));
                set(region, scope, settings);
                regionService.saveManagedRegion(region);
                ctx.returnSuccess("Region exit title updated for '" + region.getId() + "'.");
            }
            case "exit-subtitle" -> {
                settings.setExitSubtitle(joinValue(ctx));
                set(region, scope, settings);
                regionService.saveManagedRegion(region);
                ctx.returnSuccess("Region exit subtitle updated for '" + region.getId() + "'.");
            }
            default -> ctx.returnError("Unknown region title key '" + action + "'.");
        }
    }

    /**
     * Handles `/region get <id> title ...` command execution.
     *
     * @param ctx The command context positioned at the title arguments.
     * @param region The managed region being read.
     */
    @Override
    public void onGet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        RegionTitleData settings = get(region, scope);
        if (ctx.numArgs() == 0) {
            if (settings == null) {
                if (scope != null) {
                    ctx.returnInfo("Region '" + region.getId() + "' has no title settings for scope '" + scope + "'.");
                }
                if (getAll(region).isEmpty()) {
                    ctx.returnInfo("Region '" + region.getId() + "' has no title settings.");
                }
                ctx.info("Region title settings for '" + region.getId() + "':");
                getAll(region).forEach((entryScope, value) -> {
                    ctx.info(" - " + entryScope + ".enter: " + valueOrUnset(value.getEnterTitle()));
                    ctx.info(" - " + entryScope + ".enter-subtitle: " + valueOrUnset(value.getEnterSubtitle()));
                    ctx.info(" - " + entryScope + ".exit: " + valueOrUnset(value.getExitTitle()));
                    ctx.info(" - " + entryScope + ".exit-subtitle: " + valueOrUnset(value.getExitSubtitle()));
                });
                return;
            }

            ctx.info("Region title settings for '" + region.getId() + "'" + (scope == null ? "" : " [" + scope + "]") + ":");
            ctx.info(" - enter: " + valueOrUnset(settings.getEnterTitle()));
            ctx.info(" - enter-subtitle: " + valueOrUnset(settings.getEnterSubtitle()));
            ctx.info(" - exit: " + valueOrUnset(settings.getExitTitle()));
            ctx.info(" - exit-subtitle: " + valueOrUnset(settings.getExitSubtitle()));
            return;
        }

        if (settings == null) {
            ctx.returnInfo("Region '" + region.getId() + "' has no title settings" + (scope == null ? "" : " for scope '" + scope + "'") + ".");
        }

        String action = ctx.getArg(0).toLowerCase();
        switch (action) {
            case "enter" -> ctx.returnInfo("Region enter title for '" + region.getId() + "': " + valueOrUnset(settings.getEnterTitle()));
            case "enter-subtitle" -> ctx.returnInfo("Region enter subtitle for '" + region.getId() + "': " + valueOrUnset(settings.getEnterSubtitle()));
            case "exit" -> ctx.returnInfo("Region exit title for '" + region.getId() + "': " + valueOrUnset(settings.getExitTitle()));
            case "exit-subtitle" -> ctx.returnInfo("Region exit subtitle for '" + region.getId() + "': " + valueOrUnset(settings.getExitSubtitle()));
            default -> ctx.returnError("Unknown region title key '" + action + "'.");
        }
    }

    /**
     * Handles `/region clear <id> title ...` command execution.
     *
     * @param ctx The command context positioned at the title arguments.
     * @param region The managed region being edited.
     */
    @Override
    public void onClear(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        if (ctx.numArgs() == 0) {
            remove(region, scope);
            regionService.saveManagedRegion(region);
            ctx.returnSuccess("Region title settings cleared for '" + region.getId() + "'.");
        }

        RegionTitleData settings = get(region, scope);
        if (settings == null) {
            ctx.returnInfo("Region '" + region.getId() + "' has no title settings.");
        }

        String action = ctx.getArg(0).toLowerCase();
        switch (action) {
            case "enter" -> {
                settings.setEnterTitle("");
                set(region, scope, settings);
                regionService.saveManagedRegion(region);
                ctx.returnSuccess("Region enter title cleared for '" + region.getId() + "'.");
            }
            case "enter-subtitle" -> {
                settings.setEnterSubtitle("");
                set(region, scope, settings);
                regionService.saveManagedRegion(region);
                ctx.returnSuccess("Region enter subtitle cleared for '" + region.getId() + "'.");
            }
            case "exit" -> {
                settings.setExitTitle("");
                set(region, scope, settings);
                regionService.saveManagedRegion(region);
                ctx.returnSuccess("Region exit title cleared for '" + region.getId() + "'.");
            }
            case "exit-subtitle" -> {
                settings.setExitSubtitle("");
                set(region, scope, settings);
                regionService.saveManagedRegion(region);
                ctx.returnSuccess("Region exit subtitle cleared for '" + region.getId() + "'.");
            }
            default -> ctx.returnError("Unknown region title key '" + action + "'.");
        }
    }

    /**
     * Returns the title settings payload for the given region, creating a new payload when absent.
     *
     * @param region The managed region to read or update.
     * @return The title settings payload.
     */
    public @NotNull RegionTitleData getOrCreate(@NotNull SCManagedRegion region, @Nullable String scope) {
        RegionTitleData settings = get(region, scope);
        if (settings != null) {
            return settings;
        }

        RegionTitleData created = new RegionTitleData();
        set(region, scope, created);
        return created;
    }

    /**
     * Handles player movement and displays titles for managed-region transitions.
     *
     * @param event The player movement event.
     */
    private void handleMove(@NotNull PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to.getWorld() == null) {
            return;
        }

        Player player = event.getPlayer();
        SCManagedRegion fromRegion = resolveTitledRegion(player, from);
        SCManagedRegion toRegion = resolveTitledRegion(player, to);
        if (sameRegion(fromRegion, toRegion)) {
            return;
        }

        if (toRegion != null) {
            showEnterTitle(player, toRegion);
            return;
        }

        if (fromRegion != null) {
            showExitTitle(player, fromRegion);
        }
    }

    /**
     * Resolves the highest-priority managed region with non-empty title settings at the given location.
     *
     * @param location The location to resolve.
     * @return The highest-priority titled region, or null if none match.
     */
    private @Nullable SCManagedRegion resolveTitledRegion(@NotNull Player player, @Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        for (SCManagedRegion region : regionService.getManagedRegionsAt(location)) {
            RegionTitleData settings = get(region, player);
            if (settings == null) {
                continue;
            }

            if (hasVisibleText(settings.getEnterTitle(), settings.getEnterSubtitle())
                || hasVisibleText(settings.getExitTitle(), settings.getExitSubtitle())) {
                return region;
            }
        }

        return null;
    }

    /**
     * Displays the configured enter title for the given region.
     *
     * @param player The player entering the region.
     * @param region The region being entered.
     */
    private void showEnterTitle(@NotNull Player player, @NotNull SCManagedRegion region) {
        RegionTitleData settings = get(region, player);
        if (settings == null || !hasVisibleText(settings.getEnterTitle(), settings.getEnterSubtitle())) {
            return;
        }

        player.showTitle(Title.title(
            TextUtil.colourise(settings.getEnterTitle()),
            TextUtil.colourise(settings.getEnterSubtitle()),
            Title.Times.times(FADE_IN, STAY, FADE_OUT)
        ));
    }

    /**
     * Displays the configured exit title for the given region.
     *
     * @param player The player leaving the region.
     * @param region The region being exited.
     */
    private void showExitTitle(@NotNull Player player, @NotNull SCManagedRegion region) {
        RegionTitleData settings = get(region, player);
        if (settings == null || !hasVisibleText(settings.getExitTitle(), settings.getExitSubtitle())) {
            return;
        }

        player.showTitle(Title.title(
            TextUtil.colourise(settings.getExitTitle()),
            TextUtil.colourise(settings.getExitSubtitle()),
            Title.Times.times(FADE_IN, STAY, FADE_OUT)
        ));
    }

    /**
     * Joins command arguments into a single text value.
     *
     * @param ctx The command context.
     * @return The joined text.
     */
    private @NotNull String joinValue(@NotNull CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "Missing text value.");
        return String.join(" ", ctx.args().subList(1, ctx.args().size()));
    }

    private @NotNull String valueOrUnset(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "(unset)";
        }
        return value;
    }

    /**
     * Checks whether the given region references represent the same managed region.
     *
     * @param left The first region reference.
     * @param right The second region reference.
     * @return True if both references resolve to the same region, false otherwise.
     */
    private boolean sameRegion(@Nullable SCManagedRegion left, @Nullable SCManagedRegion right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.getId().equals(right.getId());
    }

    /**
     * Checks whether either title line contains visible text.
     *
     * @param title The title text.
     * @param subtitle The subtitle text.
     * @return True if either line contains visible text, false otherwise.
     */
    private boolean hasVisibleText(@Nullable String title, @Nullable String subtitle) {
        return (title != null && !title.isBlank()) || (subtitle != null && !subtitle.isBlank());
    }
}

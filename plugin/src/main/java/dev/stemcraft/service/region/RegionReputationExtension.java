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
import dev.stemcraft.api.model.RegionReputationData;
import dev.stemcraft.api.model.SCManagedRegion;
import dev.stemcraft.api.service.region.RegionDataExtension;
import dev.stemcraft.api.service.region.RegionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Self-contained managed-region extension for reputation configuration.
 */
public class RegionReputationExtension implements RegionDataExtension<RegionReputationData> {
    public static final String KEY = RegionReputationData.KEY;
    private RegionService regionService;

    /**
     * Creates a new region reputation extension.
     */
    public RegionReputationExtension() { }

    /**
     * Stores the region service when this extension is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The region service instance.
     */
    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull RegionService service) {
        this.regionService = service;
    }

    /**
     * Returns the namespaced extension key used to store reputation settings.
     *
     * @return The extension key.
     */
    @Override
    public @NotNull String key() {
        return KEY;
    }

    /**
     * Returns the payload type used by the reputation extension.
     *
     * @return The settings payload type.
     */
    @Override
    public @NotNull Class<RegionReputationData> type() {
        return RegionReputationData.class;
    }

    /**
     * Returns a short description of the extension.
     *
     * @return The extension description.
     */
    @Override
    public @NotNull String description() {
        return "Reputation thresholds and storage hints for managed regions.";
    }

    /**
     * Returns the command label used by this extension.
     *
     * @return The extension command label.
     */
    @Override
    public @NotNull String commandKey() {
        return RegionReputationData.COMMAND_KEY;
    }

    /**
     * Returns tab completions for `/region set <id> reputation ...`.
     *
     * @return The completion suffixes.
     */
    @Override
    public @NotNull List<String[]> setTabCompletions() {
        return List.of(
            new String[]{"key"},
            new String[]{"default"},
            new String[]{"hostile-threshold"},
            new String[]{"minimum"},
            new String[]{"maximum"}
        );
    }

    /**
     * Returns tab completions for `/region get <id> reputation ...`.
     *
     * @return The completion suffixes.
     */
    @Override
    public @NotNull List<String[]> getTabCompletions() {
        return List.of(
            new String[]{},
            new String[]{"key"},
            new String[]{"default"},
            new String[]{"hostile-threshold"},
            new String[]{"minimum"},
            new String[]{"maximum"}
        );
    }

    /**
     * Returns tab completions for `/region clear <id> reputation ...`.
     *
     * @return The completion suffixes.
     */
    @Override
    public @NotNull List<String[]> clearTabCompletions() {
        return List.of(
            new String[]{},
            new String[]{"key"},
            new String[]{"default"},
            new String[]{"hostile-threshold"},
            new String[]{"minimum"},
            new String[]{"maximum"}
        );
    }

    @Override
    public @NotNull List<String> describe(@NotNull SCManagedRegion region) {
        if (getAll(region).isEmpty()) {
            return List.of();
        }

        List<String> out = new java.util.ArrayList<>();
        getAll(region).forEach((scope, settings) -> {
            out.add(scope + ".key: " + valueOrUnset(settings.getReputationKey()));
            out.add(scope + ".default: " + settings.getDefaultValue());
            out.add(scope + ".hostile-threshold: " + settings.getHostileThreshold());
            out.add(scope + ".minimum: " + settings.getMinimumValue());
            out.add(scope + ".maximum: " + settings.getMaximumValue());
        });
        return out;
    }

    /**
     * Handles `/region set <id> reputation ...`.
     *
     * @param ctx The command context positioned at the reputation arguments.
     * @param region The managed region being edited.
     */
    @Override
    public void onSet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        ctx.checkArgsSizeAtLeast(2, "/region set <id> reputation <key|default|hostile-threshold|minimum|maximum> <value> [g:<scope>]");

        String settingKey = ctx.getArg(0).toLowerCase(Locale.ROOT);
        RegionReputationData settings = getOrCreate(region, scope);

        switch (settingKey) {
            case "key" -> settings.setReputationKey(joinValue(ctx));
            case "default" -> settings.setDefaultValue(parseDouble(ctx.getArg(1), "default", ctx));
            case "hostile-threshold" -> settings.setHostileThreshold(parseDouble(ctx.getArg(1), "hostile-threshold", ctx));
            case "minimum" -> settings.setMinimumValue(parseDouble(ctx.getArg(1), "minimum", ctx));
            case "maximum" -> settings.setMaximumValue(parseDouble(ctx.getArg(1), "maximum", ctx));
            default -> ctx.returnError("Unknown reputation setting '" + settingKey + "'.");
        }

        set(region, scope, settings);
        regionService.saveManagedRegion(region);
        ctx.returnSuccess("Region reputation setting '" + settingKey + "' updated for '" + region.getId() + "'.");
    }

    /**
     * Handles `/region get <id> reputation ...`.
     *
     * @param ctx The command context positioned at the reputation arguments.
     * @param region The managed region being read.
     */
    @Override
    public void onGet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        RegionReputationData settings = get(region, scope);
        if (settings == null) {
            if (scope != null) {
                ctx.returnInfo("Region '" + region.getId() + "' has no reputation settings for scope '" + scope + "'.");
            }
            if (getAll(region).isEmpty()) {
                ctx.returnInfo("Region '" + region.getId() + "' has no reputation settings.");
            }
            return;
        }

        if (ctx.numArgs() == 0) {
            if (scope == null && getAll(region).size() > 1) {
                ctx.info("Region reputation settings for '" + region.getId() + "':");
                getAll(region).forEach((entryScope, value) -> {
                    ctx.info(" - " + entryScope + ".key: " + valueOrUnset(value.getReputationKey()));
                    ctx.info(" - " + entryScope + ".default: " + value.getDefaultValue());
                    ctx.info(" - " + entryScope + ".hostile-threshold: " + value.getHostileThreshold());
                    ctx.info(" - " + entryScope + ".minimum: " + value.getMinimumValue());
                    ctx.info(" - " + entryScope + ".maximum: " + value.getMaximumValue());
                });
                return;
            }

            ctx.info("Region reputation settings for '" + region.getId() + "'" + (scope == null ? "" : " [" + scope + "]") + ":");
            ctx.info(" - key: " + valueOrUnset(settings.getReputationKey()));
            ctx.info(" - default: " + settings.getDefaultValue());
            ctx.info(" - hostile-threshold: " + settings.getHostileThreshold());
            ctx.info(" - minimum: " + settings.getMinimumValue());
            ctx.info(" - maximum: " + settings.getMaximumValue());
            return;
        }

        String settingKey = ctx.getArg(0).toLowerCase(Locale.ROOT);
        switch (settingKey) {
            case "key" -> ctx.returnInfo("Region reputation key for '" + region.getId() + "': " + valueOrUnset(settings.getReputationKey()));
            case "default" -> ctx.returnInfo("Region reputation default for '" + region.getId() + "': " + settings.getDefaultValue());
            case "hostile-threshold" -> ctx.returnInfo("Region hostile threshold for '" + region.getId() + "': " + settings.getHostileThreshold());
            case "minimum" -> ctx.returnInfo("Region reputation minimum for '" + region.getId() + "': " + settings.getMinimumValue());
            case "maximum" -> ctx.returnInfo("Region reputation maximum for '" + region.getId() + "': " + settings.getMaximumValue());
            default -> ctx.returnError("Unknown reputation setting '" + settingKey + "'.");
        }
    }

    /**
     * Handles `/region clear <id> reputation ...`.
     *
     * @param ctx The command context positioned at the reputation arguments.
     * @param region The managed region being edited.
     */
    @Override
    public void onClear(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        if (ctx.numArgs() == 0) {
            remove(region, scope);
            regionService.saveManagedRegion(region);
            ctx.returnSuccess("Region reputation settings cleared for '" + region.getId() + "'.");
        }

        RegionReputationData settings = get(region, scope);
        if (settings == null) {
            ctx.returnInfo("Region '" + region.getId() + "' has no reputation settings.");
        }

        String settingKey = ctx.getArg(0).toLowerCase(Locale.ROOT);
        switch (settingKey) {
            case "key" -> settings.setReputationKey("");
            case "default" -> settings.setDefaultValue(0.0d);
            case "hostile-threshold" -> settings.setHostileThreshold(-100.0d);
            case "minimum" -> settings.setMinimumValue(-1000.0d);
            case "maximum" -> settings.setMaximumValue(1000.0d);
            default -> ctx.returnError("Unknown reputation setting '" + settingKey + "'.");
        }

        set(region, scope, settings);
        regionService.saveManagedRegion(region);
        ctx.returnSuccess("Region reputation setting '" + settingKey + "' cleared for '" + region.getId() + "'.");
    }

    /**
     * Returns the reputation payload for the given region, creating it when missing.
     *
     * @param region The managed region to read or update.
     * @return The reputation payload.
     */
    public @NotNull RegionReputationData getOrCreate(@NotNull SCManagedRegion region, @Nullable String scope) {
        RegionReputationData settings = get(region, scope);
        if (settings != null) {
            return settings;
        }

        RegionReputationData created = new RegionReputationData();
        set(region, scope, created);
        return created;
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

    /**
     * Parses a floating point command value.
     *
     * @param raw The raw value.
     * @param label The logical field label.
     * @return The parsed number.
     */
    private double parseDouble(@NotNull String raw, @NotNull String label, @NotNull CommandContext ctx) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            ctx.returnError("Invalid " + label + " value '" + raw + "'.");
            return 0.0d;
        }
    }

    /**
     * Returns a printable string for an optional text value.
     *
     * @param value The candidate value.
     * @return The printable representation.
     */
    private @NotNull String valueOrUnset(@Nullable String value) {
        return value == null || value.isBlank() ? "(unset)" : value;
    }
}

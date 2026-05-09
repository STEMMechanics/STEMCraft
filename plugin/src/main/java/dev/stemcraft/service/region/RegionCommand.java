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

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandBuilder;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.model.RegionScopedData;
import dev.stemcraft.api.model.SCManagedRegion;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.region.RegionExtension;
import dev.stemcraft.service.RegionServiceImpl;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Command surface for managed regions owned by the region service.
 */
public class RegionCommand {
    private static final String MANAGED_REGION_ID_PATTERN = "[a-z0-9_./-]+";
    private final STEMCraft plugin;
    private final STEMCraftAPI api;
    private final RegionServiceImpl regionService;
    private Command command;

    /**
     * Creates a new region command helper.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     * @param regionService The region service implementation.
     */
    public RegionCommand(@NotNull STEMCraft plugin,
                         @NotNull STEMCraftAPI api,
                         @NotNull RegionServiceImpl regionService) {
        this.plugin = plugin;
        this.api = api;
        this.regionService = regionService;
    }

    /**
     * Registers the `/region` command and supporting tab completions.
     */
    public void onEnable() {
        api.tabComplete().register("managed-regions", (player, args) -> getManagedRegionCompletions(player));
        api.tabComplete().register("managed-region-extensions", (sender, args) -> regionService.getCommandExtensionKeys().stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList());
        api.tabComplete().register("managed-region-scopes", (sender, args) -> List.of(
            RegionScopedData.ALL,
            RegionScopedData.MEMBERS,
            RegionScopedData.NONMEMBERS
        ));

        CommandBuilder builder = api.commands().create("region")
            .description("Manage persistent STEMCraft regions.")
            .permission("stemcraft.command.region")
            .usage("/region <list [world|all]|create <id>|delete <id>|info <id>|select <id>|set <id> <extension>|get <id> <extension>|clear <id> <extension>>")
            .tabCompletion("list")
            .tabCompletion("list", "all")
            .tabCompletion("list", "{world}")
            .tabCompletion("create", "")
            .tabCompletion("delete", "{managed-regions}")
            .tabCompletion("info", "{managed-regions}")
            .tabCompletion("select", "{managed-regions}")
            .tabCompletion("set", "{managed-regions}", "{managed-region-extensions}")
            .tabCompletion("get", "{managed-regions}", "{managed-region-extensions}")
            .tabCompletion("clear", "{managed-regions}", "{managed-region-extensions}")
            .executor(this::onCommand);

        command = builder.register(plugin);
    }

    /**
     * Returns the registered `/region` command instance.
     *
     * @return The registered region command.
     */
    public @NotNull Command getCommand() {
        return command;
    }

    /**
     * Cleans up the region command helper.
     */
    @SuppressWarnings("EmptyMethod")
    public void onDisable() {
        // Nothing to clean up.
    }

    /**
     * Dispatches `/region` subcommands.
     *
     * @param unused The STEMCraft API instance.
     * @param unusedCommand The command metadata.
     * @param ctx The command context.
     */
    public void onCommand(@NotNull STEMCraftAPI unused, @NotNull Command unusedCommand, @NotNull CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(1, "/region <list|create|delete|info|select|set|get|clear>");

        String subCommand = ctx.getArg(0).toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "list" -> handleList(ctx);
            case "create" -> handleCreate(ctx);
            case "delete" -> handleDelete(ctx);
            case "info" -> handleInfo(ctx);
            case "select" -> handleSelect(ctx);
            case "set" -> handleExtensionAction(subCommand, ctx);
            case "get" -> handleExtensionAction(subCommand, ctx);
            case "clear" -> handleExtensionAction(subCommand, ctx);
            default -> ctx.returnError("Unknown region subcommand '" + subCommand + "'.");
        }
    }

    /**
     * Dispatches a registered extension action.
     *
     * @param action The root action label.
     * @param ctx The command context.
     */
    private void handleExtensionAction(@NotNull String action, @NotNull CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3, "/region " + action + " <id> <extension-id> ...");

        SCManagedRegion region = requireManagedRegion(ctx, ctx.getArg(1));
        String extensionId = ctx.getArg(2).toLowerCase(Locale.ROOT);
        RegionExtension<?> extension = regionService.getCommandExtension(extensionId);
        if (extension == null) {
            ctx.returnError("Unknown region extension '" + extensionId + "'.");
        }

        String rawScope = ctx.getOption("g");
        String scope = rawScope == null ? null : normalizeScope(ctx, rawScope);
        ctx.dropArgs(3);
        switch (action) {
            case "set" -> extension.onSet(ctx, region, scope);
            case "get" -> extension.onGet(ctx, region, scope);
            case "clear" -> extension.onClear(ctx, region, scope);
            default -> ctx.returnError("Unknown region action '" + action + "'.");
        }
    }

    /**
     * Lists all managed regions known to the region service.
     *
     * @param ctx The command context.
     */
    private void handleList(@NotNull CommandContext ctx) {
        if (ctx.numArgs() >= 2 && isAllWorldsToken(ctx.getArg(1))) {
            List<SCManagedRegion> regions = regionService.getAllManagedRegions().stream()
                .sorted(Comparator
                    .comparing(SCManagedRegion::getWorldName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(SCManagedRegion::getId, String.CASE_INSENSITIVE_ORDER))
                .toList();
            if (regions.isEmpty()) {
                ctx.returnInfo("No managed regions are defined.");
            }

            ctx.info("Managed regions across all worlds:");
            for (SCManagedRegion region : regions) {
                ctx.info(" - " + regionService.getManagedRegionReference(region) + " priority=" + region.getPriority());
            }
            return;
        }

        String worldName = resolveListWorld(ctx);
        List<SCManagedRegion> regions = regionService.getManagedRegions(worldName).stream()
            .sorted(Comparator.comparing(SCManagedRegion::getId, String.CASE_INSENSITIVE_ORDER))
            .toList();

        if (regions.isEmpty()) {
            ctx.returnInfo("No managed regions are defined for world '" + worldName + "'.");
        }

        ctx.info("Managed regions in '" + worldName + "':");
        for (SCManagedRegion region : regions) {
            ctx.info(" - " + region.getId() + " priority=" + region.getPriority());
        }
    }

    /**
     * Creates or replaces a managed region from the player's WorldEdit selection.
     *
     * @param ctx The command context.
     */
    private void handleCreate(@NotNull CommandContext ctx) {
        Player player = requirePlayer(ctx);
        ctx.checkArgsSizeAtLeast(2, "/region create <id> [priority]");

        SCRegion selection = api.selections().getWorldEditSelection(player);
        if (selection == null) {
            ctx.returnError("No WorldEdit selection found. Make a selection first.");
        }
        SCRegion selectedRegion = Objects.requireNonNull(selection, "selection");

        World world = selectedRegion.getWorld();
        if (world == null) {
            ctx.returnError("The current WorldEdit selection is not bound to a loaded world.");
        }
        World selectedWorld = Objects.requireNonNull(world, "world");

        ManagedRegionRef regionRef = parseRegionRef(ctx, ctx.getArg(1), selectedWorld.getName());
        if (!regionRef.worldName().equals(selectedWorld.getName())) {
            ctx.returnError("Region world '" + regionRef.worldName() + "' does not match the WorldEdit selection world '" + selectedWorld.getName() + "'.");
        }

        int priority = ctx.numArgs() >= 3 ? parsePriority(ctx.getArg(2), ctx) : 0;
        SCManagedRegion region = new SCManagedRegion(regionRef.id(), selectedWorld.getName(), selectedRegion.copy(), priority);
        regionService.saveManagedRegion(region);
        ctx.returnSuccess("Managed region '" + regionRef.reference() + "' saved from your current WorldEdit selection.");
    }

    /**
     * Removes a managed region definition.
     *
     * @param ctx The command context.
     */
    private void handleDelete(@NotNull CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "/region delete <id>");
        ManagedRegionRef regionRef = parseRegionRef(ctx, ctx.getArg(1), resolveDefaultWorldName(ctx));
        if (!regionService.removeManagedRegion(regionRef.worldName(), regionRef.id())) {
            ctx.returnError("Managed region '" + regionRef.reference() + "' does not exist.");
        }
        ctx.returnSuccess("Managed region '" + regionRef.reference() + "' deleted.");
    }

    /**
     * Shows a summary of one managed region definition.
     *
     * @param ctx The command context.
     */
    private void handleInfo(@NotNull CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "/region info <id>");
        SCManagedRegion region = requireManagedRegion(ctx, ctx.getArg(1));

        ctx.info("Managed region: " + regionService.getManagedRegionReference(region));
        ctx.info(" - World: " + region.getWorldName());
        ctx.info(" - Priority: " + region.getPriority());
        ctx.info(" - Shape: " + formatShape(region));
        if (region.data().isEmpty()) {
            ctx.info(" - Extensions: (none)");
            return;
        }

        ctx.info(" - Extensions:");
        regionService.getExtensions().stream()
            .sorted(Comparator.comparing(this::extensionLabel, String.CASE_INSENSITIVE_ORDER))
            .forEach(extension -> {
                List<String> lines = extension.describe(region);
                if (lines.isEmpty()) {
                    return;
                }

                String label = extensionLabel(extension);
                ctx.info("   - " + label + ":");
                lines.forEach(line -> ctx.info("      - " + line));
            });
    }

    /**
     * Copies a managed region shape into the caller's WorldEdit selection.
     *
     * @param ctx The command context.
     */
    private void handleSelect(@NotNull CommandContext ctx) {
        Player player = requirePlayer(ctx);
        ctx.checkArgsSizeAtLeast(2, "/region select <id>");
        SCManagedRegion region = requireManagedRegion(ctx, ctx.getArg(1));

        if (region.getRegion() == null) {
            ctx.returnError("Managed region '" + region.getId() + "' is a whole-world region and does not have a local shape to select.");
        }

        api.selections().setWorldEditSelection(player, region.getRegion().copy());
        ctx.returnSuccess("WorldEdit selection updated from managed region '" + regionService.getManagedRegionReference(region) + "'.");
    }

    /**
     * Retrieves a managed region from the region service or stops command execution with an error.
     *
     * @param ctx The command context.
     * @param regionId The managed region identifier.
     * @return The managed region.
     */
    private @NotNull SCManagedRegion requireManagedRegion(@NotNull CommandContext ctx, @NotNull String regionId) {
        ManagedRegionRef regionRef = parseRegionRef(ctx, regionId, resolveDefaultWorldName(ctx));
        SCManagedRegion region = regionService.getManagedRegion(regionRef.worldName(), regionRef.id());
        if (region == null) {
            ctx.returnError("Managed region '" + regionRef.reference() + "' does not exist.");
        }
        return Objects.requireNonNull(region, "region");
    }

    /**
     * Retrieves the command sender as a player or stops command execution with an error.
     *
     * @param ctx The command context.
     * @return The player sender.
     */
    private @NotNull Player requirePlayer(@NotNull CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) {
            ctx.returnError("A player is required when using this command.");
        }
        return Objects.requireNonNull(player, "player");
    }

    /**
     * Parses a region priority value from text or stops command execution with an error.
     *
     * @param raw The raw priority text.
     * @param ctx The command context.
     * @return The parsed priority.
     */
    private int parsePriority(@NotNull String raw, @NotNull CommandContext ctx) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            ctx.returnError("Invalid region priority '" + raw + "'.");
            return 0;
        }
    }

    /**
     * Normalizes a user-supplied region identifier to a namespaced region id.
     *
     * @param raw The raw region identifier.
     * @return The normalized namespaced identifier.
     */
    private @NotNull String normalizeRegionId(@NotNull CommandContext ctx, @NotNull String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches(MANAGED_REGION_ID_PATTERN)) {
            ctx.returnError("Invalid managed region ID '" + raw + "'.");
        }
        return normalized;
    }

    /**
     * Formats a managed region shape for human-readable command output.
     *
     * @param region The managed region to describe.
     * @return The human-readable shape summary.
     */
    private @NotNull String formatShape(@NotNull SCManagedRegion region) {
        if (region.getRegion() == null) {
            return "WORLD";
        }
        return region.getRegion().toString();
    }

    private @NotNull List<String> getManagedRegionCompletions(@Nullable Player player) {
        String currentWorld = player == null ? null : player.getWorld().getName();
        Set<String> values = new LinkedHashSet<>();

        for (SCManagedRegion region : regionService.getAllManagedRegions()) {
            if (region.getWorldName().equals(currentWorld)) {
                values.add(region.getId());
            }
            values.add(regionService.getManagedRegionReference(region));
        }

        return values.stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private @NotNull String resolveListWorld(@NotNull CommandContext ctx) {
        if (ctx.numArgs() >= 2) {
            World world = ctx.getArgAsWorld(1);
            if (world == null) {
                ctx.returnError("World '" + ctx.getArg(1) + "' does not exist.");
            }
            return Objects.requireNonNull(world, "world").getName();
        }
        return resolveDefaultWorldName(ctx);
    }

    private boolean isAllWorldsToken(@Nullable String value) {
        return value != null && (value.equalsIgnoreCase("all") || value.equals("*") || value.equalsIgnoreCase("server"));
    }

    private @NotNull String extensionLabel(@NotNull RegionExtension<?> extension) {
        String commandKey = extension.commandKey();
        return commandKey == null ? extension.key() : commandKey;
    }

    private @NotNull String normalizeScope(@NotNull CommandContext ctx, @NotNull String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(RegionScopedData.ALL)
            || normalized.equals(RegionScopedData.MEMBERS)
            || normalized.equals(RegionScopedData.NONMEMBERS)) {
            return normalized;
        }

        ctx.returnError("Invalid region scope '" + raw + "'.");
        return normalized;
    }

    private @NotNull String resolveDefaultWorldName(@NotNull CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) {
            ctx.returnError("A world is required here. Use <world:id> from console.");
        }
        return Objects.requireNonNull(player, "player").getWorld().getName();
    }

    private @NotNull ManagedRegionRef parseRegionRef(@NotNull CommandContext ctx,
                                                     @NotNull String raw,
                                                     @Nullable String defaultWorldName) {
        String trimmed = raw.trim();
        int delimiter = trimmed.indexOf(':');
        if (delimiter > 0 && delimiter < trimmed.length() - 1) {
            String worldName = trimmed.substring(0, delimiter);
            String regionId = normalizeRegionId(ctx, trimmed.substring(delimiter + 1));
            return new ManagedRegionRef(worldName, regionId);
        }

        if (defaultWorldName == null || defaultWorldName.isBlank()) {
            ctx.returnError("A world is required here. Use <world:id>.");
        }

        return new ManagedRegionRef(Objects.requireNonNull(defaultWorldName, "defaultWorldName"), normalizeRegionId(ctx, trimmed));
    }

    private record ManagedRegionRef(String worldName, String id) {
        private @NotNull String reference() {
            return worldName + ":" + id;
        }
    }
}

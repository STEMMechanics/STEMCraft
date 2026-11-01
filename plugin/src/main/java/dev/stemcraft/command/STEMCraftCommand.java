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

package dev.stemcraft.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.feature.PlayerWelcomeCommand;
import dev.stemcraft.api.service.save.SaveReport;
import dev.stemcraft.service.firstjoin.FirstJoinCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import dev.stemcraft.api.service.loot.LootService;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class STEMCraftCommand extends BaseCommand {
    private static final String PERMISSION = "stemcraft.command.stemcraft";
    private final FirstJoinCommand firstJoinCommand;
    private final PlayerWelcomeCommand playerWelcomeCommand;

    public STEMCraftCommand(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
        this.firstJoinCommand = new FirstJoinCommand(plugin.firstJoin());
        this.playerWelcomeCommand = new PlayerWelcomeCommand(plugin, api);
    }

    @Override
    public void onLoad() {
        setLabel("stemcraft");
        setDescription("STEMCRAFT_COMMAND_DESCRIPTION");
        setUsage("STEMCRAFT_COMMAND_USAGE");
        setPermission(PERMISSION);
        addAliases("sc");
        addTabCompletion("status");
        addTabCompletion("version");
        addTabCompletion("reload");
        addTabCompletion("save");
        addTabCompletion("pack");
        addTabCompletion("loot");
        addTabCompletion("pack", "list");
        addTabCompletion("pack", "info", "{workshop-pack}");
        addTabCompletion("pack", "create", "{workshop-pack}");
        addTabCompletion("pack", "enable", "{workshop-pack}");
        addTabCompletion("pack", "disable", "{workshop-pack}");
        addTabCompletion("pack", "delete", "{workshop-pack}");
        addTabCompletion("pack", "validate", "{workshop-pack}");
        addTabCompletion("pack", "reload", "{workshop-pack}");
        addTabCompletion("loot", "simulate", "{loot-table}", "{number}");
        api.tabComplete().register("loot-table", (player, args, current) -> api.loot().tableIds());
        addTabCompletion("reload", "locale");
        addTabCompletion("reload", "locales");
        firstJoinCommand.addTabCompletions(this);
        playerWelcomeCommand.addTabCompletions(this);
        register(plugin);
    }

    @Override
    public void onExecute(Command cmd, CommandContext ctx) {
        String action = Objects.requireNonNullElse(ctx.getArgLower(0), "status");

        if (firstJoinCommand.handle(cmd, ctx)) {
            return;
        }
        if (playerWelcomeCommand.handle(cmd, ctx)) {
            return;
        }

        switch (action) {
            case "status", "info" -> sendStatus(ctx);
            case "version" -> sendVersion(ctx);
            case "reload" -> handleReload(ctx);
            case "save" -> handleSave(ctx);
            case "pack" -> handlePack(ctx);
            case "loot" -> handleLoot(ctx);
            default -> ctx.returnUsage();
        }
    }

    private void handlePack(CommandContext ctx) {
        require(ctx, "stemcraft.command.stemcraft.pack");
        String action = Objects.requireNonNullElse(ctx.getArgLower(1), "list");
        var service = plugin.contentPacks();
        if (service == null) {
            ctx.returnError("Content packs are unavailable.");
            return;
        }
        switch (action) {
            case "list" -> {
                if (service.packs().isEmpty()) ctx.returnInfo("No content packs are installed.");
                for (var pack : service.packs()) {
                    ctx.info(pack.id() + "  " + (pack.enabled() ? "enabled" : "disabled")
                        + (pack.valid() ? "" : "  INVALID: " + String.join(" | ", pack.validationErrors())));
                }
            }
            case "info" -> {
                String id = ctx.getArg(2);
                var pack = id == null ? null : service.find(id);
                if (pack == null) {
                    ctx.returnError("Pack not found: " + id);
                    return;
                }
                ctx.returnInfo(pack.id() + " (" + (pack.enabled() ? "enabled" : "disabled") + ")\n"
                    + "Path: " + pack.path() + "\nNamespace: " + pack.namespace() + "\nDescription: " + pack.description()
                    + "\nDependencies: " + String.join(", ", pack.dependencies())
                    + "\nResource-pack rebuild: " + (pack.resourcePackRebuildRequired() ? "required" : "not required"));
                if (!pack.validationErrors().isEmpty()) ctx.warn("Validation: " + String.join(" | ", pack.validationErrors()));
            }
            case "create" -> {
                String id = ctx.getArg(2);
                if (id == null || !service.create(id, ctx.getArg(3))) ctx.returnError("Could not create pack: " + id);
                ctx.returnSuccess("Created pack " + id + ". Add YAML to its configs directory, then enable it.");
            }
            case "enable", "disable", "delete", "validate", "reload" -> handlePackAction(ctx, service, action, ctx.getArg(2));
            default -> ctx.returnUsage();
        }
    }

    private void handlePackAction(CommandContext ctx, dev.stemcraft.service.ContentPackServiceImpl service,
                                  String action, String id) {
        if (id == null) ctx.returnError("A pack ID is required.");
        switch (action) {
            case "validate" -> {
                var result = service.validate(id);
                if (!result.valid()) ctx.returnError("Pack is invalid: " + String.join(" | ", result.errors()));
                ctx.returnSuccess("Pack " + id + " is valid.");
            }
            case "enable" -> {
                if (!service.enable(id)) ctx.returnError("Could not enable pack " + id + ".");
                ctx.returnSuccess("Enabled pack " + id + ".");
            }
            case "disable" -> {
                if (!service.disable(id)) ctx.returnError("Could not disable pack " + id + ".");
                ctx.returnSuccess("Disabled pack " + id + ".");
            }
            case "delete" -> {
                if (!service.delete(id)) ctx.returnError("Could not move pack " + id + " to recoverable trash.");
                ctx.returnSuccess("Moved pack " + id + " to recoverable trash.");
            }
            case "reload" -> {
                if (!service.reload(id)) ctx.returnError("Pack reload failed; previous runtime state was retained where possible.");
                ctx.returnSuccess("Reloaded pack " + id + ".");
            }
            default -> ctx.returnUsage();
        }
    }

    private void handleLoot(CommandContext ctx) {
        require(ctx, "stemcraft.command.stemcraft.loot");
        if (!"simulate".equals(ctx.getArgLower(1)) || ctx.getArg(2) == null)
            ctx.returnError("Usage: /stemcraft loot simulate <table> <rolls> [world] [mob] [tier] [biome] [spawn-reason]");
        int rolls = 1;
        try { rolls = Math.clamp(Integer.parseInt(Objects.requireNonNullElse(ctx.getArg(3), "1")), 1, 100_000); }
        catch (NumberFormatException exception) { ctx.returnError("Rolls must be a whole number."); }
        EntityType mob = null;
        if (ctx.getArg(5) != null) {
            try { mob = EntityType.valueOf(ctx.getArg(5).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { ctx.returnError("Unknown mob type: " + ctx.getArg(5)); }
        }
        int tier = 0;
        if (ctx.getArg(6) != null) {
            try { tier = Math.max(0, Integer.parseInt(ctx.getArg(6))); }
            catch (NumberFormatException exception) { ctx.returnError("Tier must be a whole number."); }
        }
        boolean playerKill = "true".equalsIgnoreCase(ctx.getArg(9));
        LootService.LootContext context = new LootService.LootContext(ctx.getArg(4), mob, ctx.getArg(7),
            ctx.getArg(8), playerKill, tier, Map.of("player-kill", playerKill));
        var drops = api.loot().simulate(ctx.getArg(2), rolls, context, new java.util.Random(0L));
        if (drops.isEmpty()) ctx.returnInfo("No drops in " + rolls + " simulated roll(s).");
        else drops.forEach(drop -> ctx.info(drop.item() + " x" + drop.amount()));
    }

    private void require(CommandContext ctx, String permission) {
        if (!ctx.hasPermission(permission)) ctx.returnError("Missing permission: " + permission);
    }

    private void handleSave(CommandContext ctx) {
        if (!ctx.getSender().hasPermission("stemcraft.command.stemcraft.save")) {
            ctx.returnError("STEMCRAFT_COMMAND_SAVE_NO_PERMISSION");
        }
        SaveReport report = api.saves().saveAll();
        if (report.successful()) {
            ctx.returnSuccess("STEMCRAFT_COMMAND_SAVE_SUCCESS", "count", report.succeeded());
        }
        ctx.warn("STEMCRAFT_COMMAND_SAVE_FAILURE", "succeeded", report.succeeded(),
            "attempted", report.attempted(), "count", report.failures().size());
        report.failures().forEach((id, reason) ->
            ctx.warn("STEMCRAFT_COMMAND_SAVE_FAILURE_ENTRY", "id", id, "reason", reason));
    }

    private void sendVersion(CommandContext ctx) {
        ctx.returnInfo("STEMCRAFT_COMMAND_VERSION", "version", plugin.getPluginMeta().getVersion());
    }

    private void sendStatus(CommandContext ctx) {
        ctx.info("STEMCRAFT_COMMAND_STATUS_HEADER", "version", plugin.getPluginMeta().getVersion());
        ctx.info("STEMCRAFT_COMMAND_STATUS_MAINTENANCE", "state", plugin.isMaintenanceMode() ? "on" : "off");
        ctx.info("STEMCRAFT_COMMAND_STATUS_LOCALE", "locale", api.locales().getDefaultLocale());
        ctx.info("STEMCRAFT_COMMAND_STATUS_FEATURES",
            "count", plugin.loadedFeatureIds().size(),
            "features", joinOrPlaceholder(plugin.loadedFeatureIds()));
        ctx.info("STEMCRAFT_COMMAND_STATUS_MINIGAMES",
            "count", plugin.loadedMiniGameIds().size(),
            "minigames", joinOrPlaceholder(plugin.loadedMiniGameIds()));

        Map<String, String> failures = plugin.loadFailures();
        if (failures.isEmpty()) {
            ctx.success("STEMCRAFT_COMMAND_STATUS_LOAD_OK");
            return;
        }

        ctx.warn("STEMCRAFT_COMMAND_STATUS_LOAD_FAILURES", "count", failures.size());
        for (Map.Entry<String, String> entry : failures.entrySet()) {
            ctx.warn("STEMCRAFT_COMMAND_STATUS_LOAD_FAILURE_ENTRY",
                "id", entry.getKey(),
                "reason", entry.getValue() == null ? "unknown error" : entry.getValue());
        }
    }

    private void handleReload(CommandContext ctx) {
        String scope = ctx.getArgLower(1);
        boolean localesOnly = false;
        if (scope != null) {
            switch (scope.toLowerCase(Locale.ROOT)) {
                case "locale", "locales" -> localesOnly = true;
                default -> ctx.returnError("STEMCRAFT_COMMAND_RELOAD_SCOPE_INVALID", "scope", scope);
            }
        }

        STEMCraft.ReloadSummary summary = plugin.reloadStemCraft(localesOnly);
        if (localesOnly) {
            if (!summary.localesReloaded()) {
                ctx.returnError("STEMCRAFT_COMMAND_RELOAD_LOCALES_FAILED");
            }
            ctx.returnSuccess("STEMCRAFT_COMMAND_RELOAD_LOCALES_SUCCESS", "locale", api.locales().getDefaultLocale());
        }

        if (!summary.configReloaded()) {
            ctx.warn("STEMCRAFT_COMMAND_RELOAD_CONFIG_FAILED");
        }
        ctx.success("STEMCRAFT_COMMAND_RELOAD_SUCCESS", "features", summary.reloadedFeatures());
        ctx.warn("STEMCRAFT_COMMAND_RELOAD_RESTART_NOTICE");
    }

    private String joinOrPlaceholder(Iterable<String> values) {
        String joined = String.join(", ", values);
        return joined.isBlank() ? "<none>" : joined;
    }
}

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
import dev.stemcraft.service.firstjoin.FirstJoinCommand;

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
            default -> ctx.returnUsage();
        }
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

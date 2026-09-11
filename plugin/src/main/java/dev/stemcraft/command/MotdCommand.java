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
import dev.stemcraft.api.service.motd.MotdService;

import java.util.Locale;
import java.util.Objects;

public class MotdCommand extends BaseCommand {
    private static final String PERMISSION = "stemcraft.command.motd";

    public MotdCommand(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onLoad() {
        setLabel("motd");
        setDescription("MOTD_COMMAND_DESCRIPTION");
        setUsage("MOTD_COMMAND_USAGE");
        setPermission(PERMISSION);
        addTabCompletion("show");
        addTabCompletion("reload");
        addTabCompletion("set");
        addTabCompletion("set", "title");
        addTabCompletion("set", "title", "-c");
        addTabCompletion("set", "text");
        addTabCompletion("set", "text", "-c");
        register(plugin);
    }

    @Override
    public void onExecute(Command cmd, CommandContext ctx) {
        String action = Objects.requireNonNullElse(ctx.getArgLower(0), "show");
        switch (action) {
            case "show", "status" -> showMotd(ctx);
            case "reload" -> {
                plugin.motd().onReload();
                ctx.returnSuccess("MOTD_COMMAND_RELOAD_SUCCESS");
            }
            case "set" -> setMotd(ctx);
            default -> ctx.returnUsage();
        }
    }

    private void showMotd(CommandContext ctx) {
        MotdService.ResolvedMotd configured = plugin.motd().defaultMotd();
        MotdService.ResolvedMotd active = plugin.motd().current();

        ctx.info("MOTD_COMMAND_HEADER");
        ctx.info("MOTD_COMMAND_DEFAULT_TITLE", "value", configured.motdTitle(), "centered", plugin.motd().isDefaultTitleCentered() ? "yes" : "no");
        ctx.info("MOTD_COMMAND_DEFAULT_TEXT", "value", configured.motdText(), "centered", plugin.motd().isDefaultTextCentered() ? "yes" : "no");
        ctx.info("MOTD_COMMAND_ACTIVE_TITLE", "value", active.motdTitle());
        ctx.info("MOTD_COMMAND_ACTIVE_TEXT", "value", active.motdText());
        ctx.info("MOTD_COMMAND_ACTIVE_PRIORITY", "value", active.priority().name().toLowerCase(Locale.ROOT));
    }

    private void setMotd(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3, "MOTD_COMMAND_USAGE");
        String field = Objects.requireNonNullElse(ctx.getArgLower(1), "");
        String value = ctx.getArgsAsString(2).trim();
        boolean centered = ctx.hasFlag("c", false);

        if (value.isEmpty()) {
            ctx.returnError("MOTD_COMMAND_SET_EMPTY");
        }

        switch (field) {
            case "title" -> {
                plugin.motd().updateDefaultTitle(value, centered);
                ctx.returnSuccess("MOTD_COMMAND_SET_TITLE_SUCCESS", "value", value, "centered", centered ? "yes" : "no");
            }
            case "text" -> {
                plugin.motd().updateDefaultText(value, centered);
                ctx.returnSuccess("MOTD_COMMAND_SET_TEXT_SUCCESS", "value", value, "centered", centered ? "yes" : "no");
            }
            default -> ctx.returnError("MOTD_COMMAND_FIELD_INVALID", "field", field);
        }
    }
}

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

package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Registers configurable player-run command aliases from config.yml.
 */
public class CustomCommands extends BaseFeature {
    private static final Pattern COMMAND_LABEL_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]*$");

    private final Map<String, Command> registeredCommands = new LinkedHashMap<>();

    public CustomCommands(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        reloadCommands();
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadCommands();
    }

    @Override
    public void onDisable() {
        unregisterCommands();
    }

    private void reloadCommands() {
        unregisterCommands();

        ConfigSection config = getConfigSection();
        Set<String> seenLabels = new LinkedHashSet<>();
        boolean changed = false;

        for (String id : config.getKeys(false)) {
            if ("enabled".equalsIgnoreCase(id)) {
                continue;
            }

            ConfigSection entry = config.getSection(id, false);
            if (entry == null) {
                continue;
            }

            String label = normalizeLabel(entry.getString("command", id));
            if (!COMMAND_LABEL_PATTERN.matcher(label).matches()) {
                api.messages().warn("CUSTOM_COMMAND_INVALID_LABEL", "id", id, "label", label);
                continue;
            }

            if (!seenLabels.add(label)) {
                api.messages().warn("CUSTOM_COMMAND_DUPLICATE_LABEL", "id", id, "label", label);
                continue;
            }

            List<String> runCommands = getRunCommands(entry);
            if (runCommands.isEmpty()) {
                api.messages().warn("CUSTOM_COMMAND_MISSING_RUN", "id", id, "label", label);
                continue;
            }

            String permission = entry.getString("permission", "stemcraft.command." + label).trim();
            registeredCommands.put(label, api.commands().create(label)
                    .description("CUSTOM_COMMAND_DESCRIPTION")
                    .usage("/" + label)
                    .permission(permission)
                    .executor((unused, cmd, ctx) -> {
                        ctx.checkNotConsole("COMMAND_PLAYER_ONLY");

                        Player player = ctx.asPlayer();
                        for (String configuredCommand : runCommands) {
                            boolean dispatched = Bukkit.dispatchCommand(player, configuredCommand);
                            if (!dispatched) {
                                ctx.returnError("CUSTOM_COMMAND_RUN_FAILED", "command", configuredCommand);
                            }
                        }
                    })
                    .register(STEMCraft.getPlugin()));
            changed = true;
        }

        if (changed) {
            syncCommands();
        }
    }

    private void unregisterCommands() {
        if (registeredCommands.isEmpty()) {
            return;
        }

        registeredCommands.values().forEach(Command::unregister);
        registeredCommands.clear();
        syncCommands();
    }

    private List<String> getRunCommands(ConfigSection entry) {
        List<String> commands = new ArrayList<>();

        Object raw = entry.get("run");
        if (raw instanceof String single) {
            String command = normalizeRunCommand(single);
            if (!command.isBlank()) {
                commands.add(command);
            }
            return commands;
        }

        for (String configuredCommand : entry.getStringList("run")) {
            String command = normalizeRunCommand(configuredCommand);
            if (!command.isBlank()) {
                commands.add(command);
            }
        }

        return commands;
    }

    private String normalizeLabel(String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private String normalizeRunCommand(String command) {
        String normalized = command == null ? "" : command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private void syncCommands() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("syncCommands");
            method.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException ignored) {
            // Not available on all server implementations.
        }
    }
}

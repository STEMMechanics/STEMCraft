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
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import dev.stemcraft.service.command.CommandImpl;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final String EDITOR_LABEL = "customcommands";
    private static final String EDITOR_PERMISSION = "stemcraft.command.customcommands";
    private static final Set<String> RESERVED_LABELS = Set.of(EDITOR_LABEL, "customcommand");

    private final Map<String, Command> registeredCommands = new LinkedHashMap<>();
    private final Map<String, org.bukkit.command.Command> displacedCommands = new LinkedHashMap<>();
    private final Map<String, CustomCommandEntry> activeEntries = new LinkedHashMap<>();
    private Command editorCommand;

    public CustomCommands(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        registerTabCompletions();
        registerEditorCommand();
        reloadCommands();
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadCommands();
    }

    @Override
    public void onDisable() {
        unregisterCommands(false);
        unregisterEditorCommand();
    }

    private void reloadCommands() {
        boolean hadRegisteredCommands = !registeredCommands.isEmpty();
        unregisterCommands(false);
        activeEntries.clear();

        ConfigSection config = getConfigSection();
        Set<String> seenLabels = new LinkedHashSet<>();
        boolean changed = hadRegisteredCommands;

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

            if (RESERVED_LABELS.contains(label)) {
                api.messages().warn("CUSTOM_COMMAND_RESERVED_LABEL", "id", id, "label", label);
                continue;
            }

            if (!seenLabels.add(label)) {
                api.messages().warn("CUSTOM_COMMAND_DUPLICATE_LABEL", "id", id, "label", label);
                continue;
            }

            CustomCommandEntry customCommandEntry = readEntry(id, entry);
            activeEntries.put(customCommandEntry.id(), customCommandEntry);
            registerRuntimeCommand(customCommandEntry);
            changed = true;
        }

        if (changed) {
            syncCommands();
        }
    }

    private void registerEditorCommand() {
        if (editorCommand != null) {
            return;
        }

        editorCommand = api.commands().create(EDITOR_LABEL)
                .description("CUSTOM_COMMAND_EDITOR_DESCRIPTION")
                .usage("CUSTOM_COMMAND_EDITOR_USAGE")
                .permission(EDITOR_PERMISSION)
                .tabCompletion("list")
                .tabCompletion("info", "{custom-command-id}")
                .tabCompletion("create")
                .tabCompletion("create", "")
                .tabCompletion("create", "", "")
                .tabCompletion("delete", "{custom-command-id}")
                .tabCompletion("setlabel", "{custom-command-id}")
                .tabCompletion("setlabel", "{custom-command-id}", "")
                .tabCompletion("setpermission", "{custom-command-id}")
                .tabCompletion("setpermission", "{custom-command-id}", "")
                .tabCompletion("clearpermission", "{custom-command-id}")
                .tabCompletion("addcommand", "{custom-command-id}")
                .tabCompletion("addcommand", "{custom-command-id}", "")
                .tabCompletion("setcommand", "{custom-command-id}", "{custom-command-command-index:$1}")
                .tabCompletion("setcommand", "{custom-command-id}", "{custom-command-command-index:$1}", "")
                .tabCompletion("removecommand", "{custom-command-id}", "{custom-command-command-index:$1}")
                .executor((unused, cmd, ctx) -> handleEditorCommand(ctx))
                .register(STEMCraft.getPlugin());
    }

    private void registerTabCompletions() {
        api.tabComplete().register("custom-command-id", (player, args) ->
                listEntries().stream()
                        .map(CustomCommandEntry::id)
                        .toList()
        );

        api.tabComplete().register("custom-command-command-index", (player, args) -> {
            if (args.length == 0 || args[0] == null || args[0].isBlank()) {
                return List.of();
            }

            CustomCommandEntry entry = findEntry(normalizeConfigId(args[0]));
            if (entry == null || entry.runCommands().isEmpty()) {
                return List.of();
            }

            List<String> indexes = new ArrayList<>();
            for (int i = 1; i <= entry.runCommands().size(); i++) {
                indexes.add(String.valueOf(i));
            }

            return indexes;
        });
    }

    private void unregisterEditorCommand() {
        if (editorCommand == null) {
            return;
        }

        editorCommand.unregister();
        editorCommand = null;
    }

    private void handleEditorCommand(dev.stemcraft.api.command.CommandContext ctx) {
        String subCommand = ctx.getArgLower(0);
        if (subCommand == null || subCommand.equals("list")) {
            showEditorList(ctx, subCommand == null ? 1 : ctx.getArgAsInt(1, 1));
            return;
        }

        switch (subCommand) {
            case "info" -> {
                ctx.checkArgsSizeAtLeast(2, "CUSTOM_COMMAND_EDITOR_USAGE");
                showEditorInfo(ctx, normalizeConfigId(ctx.getArg(1)), ctx.getArgAsInt(2, 1));
            }
            case "create" -> createEditorEntry(ctx);
            case "delete" -> deleteEditorEntry(ctx);
            case "setlabel" -> setEditorLabel(ctx);
            case "setpermission" -> setEditorPermission(ctx);
            case "clearpermission" -> clearEditorPermission(ctx);
            case "addcommand" -> addEditorCommand(ctx);
            case "setcommand" -> setEditorCommand(ctx);
            case "removecommand" -> removeEditorCommand(ctx);
            default -> ctx.returnUsage();
        }
    }

    private void showEditorList(dev.stemcraft.api.command.CommandContext ctx, int page) {
        List<CustomCommandEntry> entries = listEntries();
        int lineCount = Math.max(1, entries.size());

        ChatMenuUtil.render(
                ctx.getSender(),
                buildListTitle(ctx.isPlayer()),
                EDITOR_LABEL + " list",
                page,
                lineCount,
                (start, count, isPlayer) -> {
                    List<Component> lines = new ArrayList<>();
                    int end = Math.min(start + count, lineCount);

                    for (int i = start; i < end; i++) {
                        if (entries.isEmpty()) {
                            lines.add(Component.text("No custom commands are configured.", NamedTextColor.GRAY));
                            continue;
                        }

                        CustomCommandEntry entry = entries.get(i);
                        Component line = Component.text(entry.id(), NamedTextColor.YELLOW)
                                .append(Component.text(" - ", NamedTextColor.GRAY))
                                .append(Component.text(" " + entry.runCommands().size() + pluralize(entry.runCommands().size(), " cmd", " cmds"), NamedTextColor.AQUA));

                        if (isPlayer) {
                            line = line.append(Component.text(" "))
                                    .append(actionButton("[Info]", NamedTextColor.GOLD,
                                            ClickEvent.runCommand("/" + EDITOR_LABEL + " info " + entry.id()),
                                            "Show command details"))
                                    .append(Component.text(" "))
                                    .append(actionButton("[Run]", NamedTextColor.GREEN,
                                            ClickEvent.runCommand("/" + entry.label()),
                                            "Run /" + entry.label()))
                                    .append(Component.text(" "))
                                    .append(actionButton("[Del]", NamedTextColor.RED,
                                            ClickEvent.runCommand("/" + EDITOR_LABEL + " delete " + entry.id()),
                                            "Delete this custom command"));
                        }

                        lines.add(line);
                    }

                    return lines;
                },
                "No custom commands are configured."
        );
    }

    private Component buildListTitle(boolean isPlayer) {
        Component title = Component.text("Custom Commands", NamedTextColor.AQUA);

        if (isPlayer) {
            title = title.append(Component.text(" "))
                    .append(actionButton("[Create]", NamedTextColor.GREEN,
                            ClickEvent.suggestCommand("/" + EDITOR_LABEL + " create id"),
                            "Create a new custom command"));
        }

        return title;
    }

    private void showEditorInfo(dev.stemcraft.api.command.CommandContext ctx, String id, int page) {
        CustomCommandEntry entry = requireEntry(ctx, id);

        ctx.getSender().sendMessage(buildInfoIdLine(entry, ctx.isPlayer()));
        ctx.getSender().sendMessage(buildInfoCommandLine(entry, ctx.isPlayer()));
        ctx.getSender().sendMessage(buildInfoPermissionLine(entry, ctx.isPlayer()));
        ctx.getSender().sendMessage(buildInfoCommandsHeaderLine(entry, ctx.isPlayer()));

        if (entry.runCommands().isEmpty()) {
            ctx.getSender().sendMessage(Component.text("No run commands are configured.", NamedTextColor.RED));
            return;
        }

        for (int i = 0; i < entry.runCommands().size(); i++) {
            ctx.getSender().sendMessage(buildInfoConfiguredCommandLine(entry, i, ctx.isPlayer()));
        }
    }

    private Component buildInfoIdLine(CustomCommandEntry entry, boolean isPlayer) {
        return Component.text("ID: ", NamedTextColor.GRAY)
                .append(Component.text(entry.id(), NamedTextColor.YELLOW));
    }

    private Component buildInfoCommandLine(CustomCommandEntry entry, boolean isPlayer) {
        Component line = Component.text("Command: ", NamedTextColor.GRAY)
                .append(Component.text("/" + entry.label(), NamedTextColor.YELLOW));

        if (isPlayer) {
            line = line.append(Component.text(" "))
                    .append(actionButton("[Edit]", NamedTextColor.BLUE,
                            ClickEvent.suggestCommand("/" + EDITOR_LABEL + " setlabel " + entry.id() + " " + entry.label()),
                            "Change the command label"))
                    .append(Component.text(" "))
                    .append(actionButton("[Run]", NamedTextColor.GREEN,
                            ClickEvent.runCommand("/" + entry.label()),
                            "Run /" + entry.label()));
        }

        return line;
    }

    private Component buildInfoPermissionLine(CustomCommandEntry entry, boolean isPlayer) {
        Component line = Component.text("Permission: ", NamedTextColor.GRAY)
                .append(formatPermissionComponent(entry));

        if (isPlayer) {
            line = line.append(Component.text(" "))
                    .append(actionButton("[Edit]", NamedTextColor.BLUE,
                            ClickEvent.suggestCommand("/" + EDITOR_LABEL + " setpermission " + entry.id() + " " + entry.permission()),
                            "Change the permission node"))
                    .append(Component.text(" "))
                    .append(actionButton("[Del]", NamedTextColor.RED,
                            ClickEvent.runCommand("/" + EDITOR_LABEL + " clearpermission " + entry.id()),
                            "Clear the permission requirement"));
        }

        return line;
    }

    private Component buildInfoCommandsHeaderLine(CustomCommandEntry entry, boolean isPlayer) {
        Component line = Component.text("Commands:", NamedTextColor.GRAY);

        if (isPlayer) {
            line = line.append(Component.text(" "))
                    .append(actionButton("[Add]", NamedTextColor.GREEN,
                            ClickEvent.suggestCommand("/" + EDITOR_LABEL + " addcommand " + entry.id() + " "),
                            "Add a configured command"));
        }

        return line;
    }

    private Component buildInfoConfiguredCommandLine(CustomCommandEntry entry, int zeroBasedIndex, boolean isPlayer) {
        int oneBasedIndex = zeroBasedIndex + 1;
        String configuredCommand = entry.runCommands().get(zeroBasedIndex);

        Component line = Component.text("#" + oneBasedIndex + " ", NamedTextColor.DARK_GRAY)
                .append(Component.text(configuredCommand, NamedTextColor.GOLD));

        if (isPlayer) {
            line = line.append(Component.text(" "))
                    .append(actionButton("[Edit]", NamedTextColor.BLUE,
                            ClickEvent.suggestCommand("/" + EDITOR_LABEL + " setcommand " + entry.id() + " " + oneBasedIndex + " " + configuredCommand),
                            "Replace this configured command"))
                    .append(Component.text(" "))
                    .append(actionButton("[Del]", NamedTextColor.RED,
                            ClickEvent.runCommand("/" + EDITOR_LABEL + " removecommand " + entry.id() + " " + oneBasedIndex),
                            "Remove this configured command"));
        }

        return line;
    }

    private void createEditorEntry(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.rawArgs().size() < 2) {
            ctx.returnUsage();
        }

        String id = normalizeConfigId(ctx.rawArgs().get(1));
        validateConfigId(ctx, id, true);

        String label = ctx.rawArgs().size() >= 3 ? normalizeLabel(ctx.rawArgs().get(2)) : id;
        validateCommandLabel(ctx, id, label);

        List<String> runCommands = new ArrayList<>();
        if (ctx.rawArgs().size() >= 4) {
            String runCommand = normalizeRunCommand(joinRawArgs(ctx, 3));
            if (runCommand.isBlank()) {
                ctx.returnError("Custom command run text cannot be empty.");
            }
            runCommands.add(runCommand);
        }

        saveEntry(new CustomCommandEntry(id, label, "", true, runCommands));
        ctx.returnSuccess("Created custom command '/" + label + "'.");
    }

    private void deleteEditorEntry(dev.stemcraft.api.command.CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "CUSTOM_COMMAND_EDITOR_USAGE");
        String id = normalizeConfigId(ctx.getArg(1));
        CustomCommandEntry entry = requireEntry(ctx, id);

        deleteEntry(id);
        ctx.returnSuccess("Deleted custom command '/" + entry.label() + "'.");
    }

    private void setEditorLabel(dev.stemcraft.api.command.CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3, "CUSTOM_COMMAND_EDITOR_USAGE");
        String id = normalizeConfigId(ctx.getArg(1));
        CustomCommandEntry entry = requireEntry(ctx, id);
        String label = normalizeLabel(ctx.rawArgs().get(2));

        validateCommandLabel(ctx, id, label);

        String permission = entry.explicitPermission() ? entry.permission() : defaultPermission(label);
        saveEntry(new CustomCommandEntry(entry.id(), label, permission, entry.explicitPermission(), entry.runCommands()));
        ctx.returnSuccess("Updated custom command '" + entry.id() + "' to '/" + label + "'.");
    }

    private void setEditorPermission(dev.stemcraft.api.command.CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3, "CUSTOM_COMMAND_EDITOR_USAGE");
        String id = normalizeConfigId(ctx.getArg(1));
        CustomCommandEntry entry = requireEntry(ctx, id);
        String permission = ctx.rawArgs().get(2).trim();

        boolean explicitPermission = true;
        if (permission.equalsIgnoreCase("default") || permission.equals("-")) {
            permission = defaultPermission(entry.label());
            explicitPermission = false;
        } else if (permission.equalsIgnoreCase("public")) {
            permission = "";
        }

        if (permission.isBlank()) {
            if (!explicitPermission) {
                ctx.returnError("Permission cannot be blank.");
            }
        }

        saveEntry(new CustomCommandEntry(entry.id(), entry.label(), permission, explicitPermission, entry.runCommands()));
        ctx.returnSuccess("Updated permission for '/" + entry.label() + "' to " + describePermission(entry.label(), permission, explicitPermission) + ".");
    }

    private void clearEditorPermission(dev.stemcraft.api.command.CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "CUSTOM_COMMAND_EDITOR_USAGE");
        String id = normalizeConfigId(ctx.getArg(1));
        CustomCommandEntry entry = requireEntry(ctx, id);

        saveEntry(new CustomCommandEntry(entry.id(), entry.label(), "", true, entry.runCommands()));
        ctx.returnSuccess("Cleared the permission requirement for '/" + entry.label() + "'.");
    }

    private void addEditorCommand(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.rawArgs().size() < 3) {
            ctx.returnUsage();
        }

        String id = normalizeConfigId(ctx.rawArgs().get(1));
        CustomCommandEntry entry = requireEntry(ctx, id);
        String runCommand = normalizeRunCommand(joinRawArgs(ctx, 2));
        if (runCommand.isBlank()) {
            ctx.returnError("Custom command run text cannot be empty.");
        }

        List<String> runCommands = new ArrayList<>(entry.runCommands());
        runCommands.add(runCommand);
        saveEntry(new CustomCommandEntry(entry.id(), entry.label(), entry.permission(), entry.explicitPermission(), runCommands));
        ctx.returnSuccess("Added run command #" + runCommands.size() + " to '/" + entry.label() + "'.");
    }

    private void setEditorCommand(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.rawArgs().size() < 4) {
            ctx.returnUsage();
        }

        String id = normalizeConfigId(ctx.rawArgs().get(1));
        CustomCommandEntry entry = requireEntry(ctx, id);
        int index = ctx.getArgAsInt(2, -1);
        List<String> runCommands = new ArrayList<>(entry.runCommands());
        int zeroBasedIndex = validateRunIndex(ctx, index, runCommands.size());

        String runCommand = normalizeRunCommand(joinRawArgs(ctx, 3));
        if (runCommand.isBlank()) {
            ctx.returnError("Custom command run text cannot be empty.");
        }

        runCommands.set(zeroBasedIndex, runCommand);
        saveEntry(new CustomCommandEntry(entry.id(), entry.label(), entry.permission(), entry.explicitPermission(), runCommands));
        ctx.returnSuccess("Updated run command #" + index + " for '/" + entry.label() + "'.");
    }

    private void removeEditorCommand(dev.stemcraft.api.command.CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3, "CUSTOM_COMMAND_EDITOR_USAGE");
        String id = normalizeConfigId(ctx.getArg(1));
        CustomCommandEntry entry = requireEntry(ctx, id);
        List<String> runCommands = new ArrayList<>(entry.runCommands());
        int index = ctx.getArgAsInt(2, -1);
        int zeroBasedIndex = validateRunIndex(ctx, index, runCommands.size());

        if (runCommands.size() == 1) {
            ctx.returnError("Cannot remove the last run command. Delete the custom command instead.");
        }

        runCommands.remove(zeroBasedIndex);
        saveEntry(new CustomCommandEntry(entry.id(), entry.label(), entry.permission(), entry.explicitPermission(), runCommands));
        ctx.returnSuccess("Removed run command #" + index + " from '/" + entry.label() + "'.");
    }

    private void unregisterCommands(boolean syncAfter) {
        if (registeredCommands.isEmpty()) {
            return;
        }

        new ArrayList<>(registeredCommands.keySet()).forEach(this::unregisterRuntimeCommand);
        registeredCommands.clear();
        if (syncAfter) {
            syncCommands();
        }
    }

    private static List<String> getRunCommands(ConfigSection entry) {
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

    static CustomCommandEntry readEntry(String id, ConfigSection entry) {
        String label = normalizeLabel(entry.getString("command", id));
        boolean explicitPermission = entry.contains("permission");
        String configuredPermission = entry.getString("permission", "");
        String permission = explicitPermission ? configuredPermission : defaultPermission(label);

        return new CustomCommandEntry(id, label, permission, explicitPermission, List.copyOf(getRunCommands(entry)));
    }

    static void writeEntry(ConfigSection root, CustomCommandEntry entry) {
        ConfigSection section = root.createSection(entry.id(), true);
        section.set("command", entry.label());
        if (entry.explicitPermission()) {
            section.set("permission", entry.permission());
        }
        section.set("run", new ArrayList<>(entry.runCommands()));
    }

    static String normalizeConfigId(String id) {
        return normalizeLabel(id);
    }

    static String normalizeLabel(String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    static String normalizeRunCommand(String command) {
        String normalized = command == null ? "" : command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    static String defaultPermission(String label) {
        return "stemcraft.command." + label;
    }

    private void saveEntry(CustomCommandEntry entry) {
        CustomCommandEntry previousEntry = activeEntries.get(entry.id());
        writeEntry(getConfigSection(), entry);
        getConfigSection().save();
        activeEntries.put(entry.id(), entry);

        boolean syncRequired = false;
        if (previousEntry == null) {
            registerRuntimeCommand(entry);
            syncRequired = true;
        } else if (!previousEntry.label().equals(entry.label())) {
            unregisterRuntimeCommand(previousEntry.label());
            registerRuntimeCommand(entry);
            syncRequired = true;
        } else {
            Command command = registeredCommands.get(entry.label());
            if (command != null && !previousEntry.permission().equals(entry.permission())) {
                command.setPermission(entry.permission());
                syncRequired = true;
            }
        }

        if (syncRequired) {
            syncCommands();
        }
    }

    private void deleteEntry(String id) {
        CustomCommandEntry previousEntry = activeEntries.remove(id);
        getConfigSection().remove(id);
        getConfigSection().save();
        if (previousEntry != null) {
            unregisterRuntimeCommand(previousEntry.label());
            syncCommands();
        }
    }

    private List<CustomCommandEntry> listEntries() {
        List<CustomCommandEntry> entries = new ArrayList<>(activeEntries.values());
        entries.sort(Comparator.comparing(CustomCommandEntry::id));
        return entries;
    }

    private @Nullable CustomCommandEntry findEntry(String id) {
        return activeEntries.get(id);
    }

    private CustomCommandEntry requireEntry(dev.stemcraft.api.command.CommandContext ctx, String id) {
        CustomCommandEntry entry = findEntry(id);
        if (entry == null) {
            ctx.returnError("Custom command '" + id + "' does not exist.");
        }
        return entry;
    }

    private void validateConfigId(dev.stemcraft.api.command.CommandContext ctx, String id, boolean requireMissing) {
        if (!COMMAND_LABEL_PATTERN.matcher(id).matches()) {
            ctx.returnError("Custom command id '" + id + "' is invalid.");
        }

        if ("enabled".equalsIgnoreCase(id)) {
            ctx.returnError("Custom command id 'enabled' is reserved.");
        }

        if (requireMissing && findEntry(id) != null) {
            ctx.returnError("Custom command '" + id + "' already exists.");
        }
    }

    private void validateCommandLabel(dev.stemcraft.api.command.CommandContext ctx, String id, String label) {
        if (!COMMAND_LABEL_PATTERN.matcher(label).matches()) {
            ctx.returnError("Command label '" + label + "' is invalid.");
        }

        if (RESERVED_LABELS.contains(label)) {
            ctx.returnError("Command label '/" + label + "' is reserved.");
        }

        for (CustomCommandEntry existingEntry : listEntries()) {
            if (!existingEntry.id().equalsIgnoreCase(id) && existingEntry.label().equalsIgnoreCase(label)) {
                ctx.returnError("Command label '/" + label + "' is already used by '" + existingEntry.id() + "'.");
            }
        }
    }

    private int validateRunIndex(dev.stemcraft.api.command.CommandContext ctx, int index, int size) {
        if (index < 1 || index > size) {
            ctx.returnError("Run command index must be between 1 and " + size + ".");
        }

        return index - 1;
    }

    private String joinRawArgs(dev.stemcraft.api.command.CommandContext ctx, int start) {
        if (start < 0 || start >= ctx.rawArgs().size()) {
            return "";
        }

        return String.join(" ", ctx.rawArgs().subList(start, ctx.rawArgs().size()));
    }

    private Component actionButton(String text, NamedTextColor color, ClickEvent clickEvent, String hoverText) {
        return Component.text(text, color)
                .clickEvent(clickEvent)
                .hoverEvent(HoverEvent.showText(Component.text(hoverText)));
    }

    private Component formatPermissionComponent(CustomCommandEntry entry) {
        if (!entry.explicitPermission()) {
            return Component.text(entry.permission(), NamedTextColor.AQUA)
                    .append(Component.text(" default", NamedTextColor.DARK_GRAY));
        }

        if (entry.permission().isBlank()) {
            return Component.text("<public>", NamedTextColor.GREEN);
        }

        return Component.text(entry.permission(), NamedTextColor.AQUA);
    }

    private String describePermission(String label, String permission, boolean explicitPermission) {
        if (!explicitPermission) {
            return "'" + defaultPermission(label) + "' (default)";
        }

        if (permission.isBlank()) {
            return "<public>";
        }

        return "'" + permission + "'";
    }

    private String pluralize(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }

    private void registerRuntimeCommand(CustomCommandEntry entry) {
        String label = entry.label();
        Command command = api.commands().create(label)
                .description("CUSTOM_COMMAND_DESCRIPTION")
                .usage("/" + label)
                .permission(entry.permission())
                .executor((unused, cmd, ctx) -> {
                    ctx.checkNotConsole("COMMAND_PLAYER_ONLY");

                    Player player = ctx.asPlayer();
                    CustomCommandEntry activeEntry = activeEntryForLabel(label);
                    if (activeEntry == null) {
                        ctx.returnError("CUSTOM_COMMAND_RUN_FAILED", "command", label);
                    }

                    if (activeEntry.runCommands().isEmpty()) {
                        ctx.returnInfo("CUSTOM_COMMAND_NO_COMMANDS_SET");
                    }

                    for (String configuredCommand : activeEntry.runCommands()) {
                        if (!dispatchConfiguredCommand(player, configuredCommand)) {
                            ctx.returnError("CUSTOM_COMMAND_RUN_FAILED", "command", configuredCommand);
                        }
                    }
                })
                .register(STEMCraft.getPlugin());
        registeredCommands.put(label, command);
        claimCommandLabel(label, command);
    }

    static ParsedRunCommand parseRunCommand(Player player, String configuredCommand) {
        String trimmed = configuredCommand == null ? "" : configuredCommand.trim();
        if (trimmed.isBlank()) {
            return new ParsedRunCommand(CommandDispatchMode.PLAYER, "");
        }

        String resolved = PlaceholderUtil.apply(
                trimmed,
                "player", player.getName(),
                "uuid", player.getUniqueId().toString()
        );

        if (resolved.regionMatches(true, 0, "server:", 0, "server:".length())) {
            return new ParsedRunCommand(CommandDispatchMode.SERVER, resolved.substring("server:".length()).trim());
        }

        if (resolved.regionMatches(true, 0, "player:", 0, "player:".length())) {
            return new ParsedRunCommand(CommandDispatchMode.PLAYER, resolved.substring("player:".length()).trim());
        }

        return new ParsedRunCommand(CommandDispatchMode.PLAYER, resolved);
    }

    private void unregisterRuntimeCommand(String label) {
        Command command = registeredCommands.remove(label);
        if (command != null) {
            command.unregister();
        }
        restoreCommandLabel(label);
    }

    private @Nullable CustomCommandEntry activeEntryForLabel(String label) {
        for (CustomCommandEntry entry : activeEntries.values()) {
            if (entry.label().equals(label)) {
                return entry;
            }
        }
        return null;
    }

    private boolean dispatchConfiguredCommand(Player player, String rawCommand) {
        if (rawCommand == null) {
            return false;
        }

        ParsedRunCommand parsedCommand = parseRunCommand(player, applyCommandPlaceholders(player, rawCommand.trim()));
        if (parsedCommand.command().isBlank()) {
            return false;
        }

        if (parsedCommand.mode() == CommandDispatchMode.SERVER) {
            ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
            return Bukkit.dispatchCommand(console, parsedCommand.command());
        }

        return Bukkit.dispatchCommand(player, parsedCommand.command());
    }

    private String applyCommandPlaceholders(Player player, String command) {
        String rendered = PlaceholderUtil.apply(
            command,
            "player", player.getName(),
            "uuid", player.getUniqueId().toString()
        );
        String placeholderRendered = api.placeholders().apply(player, rendered);
        return placeholderRendered == null ? "" : placeholderRendered.trim();
    }

    private void syncCommands() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("syncCommands");
            method.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException ignored) {
            // Not available on all server implementations.
        }
    }

    private void claimCommandLabel(String label, Command command) {
        org.bukkit.command.Command bukkitCommand = resolveBukkitCommand(command);
        if (bukkitCommand == null) {
            return;
        }

        Map<String, org.bukkit.command.Command> knownCommands = getKnownCommands();
        if (knownCommands == null) {
            return;
        }

        applyCommandOverride(knownCommands, displacedCommands, label, bukkitCommand);
    }

    private void restoreCommandLabel(String label) {
        Map<String, org.bukkit.command.Command> knownCommands = getKnownCommands();
        if (knownCommands == null) {
            return;
        }

        restoreCommandOverride(knownCommands, displacedCommands, label);
    }

    private @Nullable org.bukkit.command.Command resolveBukkitCommand(Command command) {
        if (command instanceof CommandImpl impl) {
            return impl.getRegisteredBukkitCommand();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private @Nullable Map<String, org.bukkit.command.Command> getKnownCommands() {
        CommandMap commandMap = getCommandMap();
        Class<?> type = commandMap.getClass();

        while (type != null) {
            try {
                Field field = type.getDeclaredField("knownCommands");
                field.setAccessible(true);
                Object value = field.get(commandMap);
                if (value instanceof Map<?, ?> knownCommands) {
                    return (Map<String, org.bukkit.command.Command>) knownCommands;
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }

        return null;
    }

    private CommandMap getCommandMap() {
        try {
            return Bukkit.getCommandMap();
        } catch (NoSuchMethodError ignored) {
        }

        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot get CommandMap", ex);
        }
    }

    static void applyCommandOverride(
            Map<String, org.bukkit.command.Command> knownCommands,
            Map<String, org.bukkit.command.Command> displacedCommands,
            String label,
            org.bukkit.command.Command overrideCommand
    ) {
        String key = normalizeLabel(label);
        org.bukkit.command.Command current = knownCommands.get(key);
        if (current == overrideCommand) {
            return;
        }

        if (current != null) {
            displacedCommands.putIfAbsent(key, current);
        }
        knownCommands.put(key, overrideCommand);
    }

    static void restoreCommandOverride(
            Map<String, org.bukkit.command.Command> knownCommands,
            Map<String, org.bukkit.command.Command> displacedCommands,
            String label
    ) {
        String key = normalizeLabel(label);
        org.bukkit.command.Command displaced = displacedCommands.remove(key);
        if (displaced != null) {
            knownCommands.put(key, displaced);
        }
    }

    static record CustomCommandEntry(String id, String label, String permission, boolean explicitPermission,
                                     List<String> runCommands) {
        CustomCommandEntry {
            runCommands = List.copyOf(runCommands);
        }
    }

    enum CommandDispatchMode {
        PLAYER,
        SERVER
    }

    static record ParsedRunCommand(CommandDispatchMode mode, String command) {
    }
}

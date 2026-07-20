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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
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
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.api.GeyserApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reusable configurable menus rendered as Java books or Bedrock SimpleForms.
 */
public class InteractiveMenus extends BaseFeature {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_.-]*$");
    private static final int BOOK_LINES_PER_PAGE = 12;
    private static final String EDIT_PERMISSION = "stemcraft.imenu.edit";

    private final Map<String, MenuDefinition> menus = new LinkedHashMap<>();
    private Command command;

    public InteractiveMenus(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        reloadMenus();
        registerCommand();
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadMenus();
    }

    @Override
    public void onDisable() {
        if (command != null) {
            command.unregister();
            command = null;
        }
    }

    private void registerCommand() {
        api.tabComplete().register("interactive-menus", (player, args) -> new ArrayList<>(menus.keySet()));
        api.tabComplete().register("interactive-menu-items", (player, args) -> {
            if (args.length == 0) {
                return List.of();
            }
            MenuDefinition menu = menus.get(normalizeId(args[0]));
            if (menu == null) {
                return List.of();
            }
            return new ArrayList<>(menu.items().keySet());
        });

        command = api.commands().create("imenu")
            .description("Interactive menus")
            .usage("/imenu <open|select|list|info|create|delete|set|item>")
            .tabCompletion("open", "{interactive-menus}", "{player}")
            .tabCompletion("open", "{interactive-menus}", "{player}", "-test")
            .tabCompletion("select", "{interactive-menus}", "{interactive-menu-items:$1}")
            .tabCompletion("select", "{interactive-menus}", "{interactive-menu-items:$1}", "-test")
            .tabCompletion("list")
            .tabCompletion("info", "{interactive-menus}")
            .tabCompletion("create")
            .tabCompletion("delete", "{interactive-menus}")
            .tabCompletion("set", "{interactive-menus}", "title")
            .tabCompletion("set", "{interactive-menus}", "body")
            .tabCompletion("set", "{interactive-menus}", "book-title")
            .tabCompletion("set", "{interactive-menus}", "book-author")
            .tabCompletion("item", "add", "{interactive-menus}")
            .tabCompletion("item", "remove", "{interactive-menus}", "{interactive-menu-items:$2}")
            .tabCompletion("item", "set", "{interactive-menus}", "{interactive-menu-items:$2}", "title")
            .tabCompletion("item", "set", "{interactive-menus}", "{interactive-menu-items:$2}", "description")
            .tabCompletion("item", "command", "add", "{interactive-menus}", "{interactive-menu-items:$3}")
            .tabCompletion("item", "command", "clear", "{interactive-menus}", "{interactive-menu-items:$3}")
            .executor(this::onCommand)
            .register(STEMCraft.getPlugin());
    }

    private void onCommand(STEMCraftAPI unused, Command cmd, CommandContext ctx) {
        if (ctx.numArgs() == 0) {
            ctx.returnInfo("Usage: /imenu <open|select|list|info|create|delete|set|item>");
        }

        switch (ctx.getArgLower(0)) {
            case "open" -> commandOpen(ctx);
            case "select" -> commandSelect(ctx);
            case "list" -> commandList(ctx);
            case "info" -> commandInfo(ctx);
            case "create" -> commandCreate(ctx);
            case "delete" -> commandDelete(ctx);
            case "set" -> commandSet(ctx);
            case "item" -> commandItem(ctx);
            default -> ctx.returnError("Unknown imenu subcommand: " + ctx.getArg(0));
        }
    }

    private void commandOpen(CommandContext ctx) {
        ctx.checkNotConsole("COMMAND_PLAYER_ONLY");
        ctx.checkArgsSizeAtLeast(2, "Usage: /imenu open <menu> [player] [-test]");

        String menuId = normalizeId(ctx.getArg(1));
        MenuDefinition menu = requireMenu(ctx, menuId);
        Player target = ctx.getPlayer(2, ctx.asPlayer());
        if (target == null) {
            ctx.returnError("PLAYER_NOT_FOUND", "player", ctx.getArg(2));
            return;
        }

        showMenu(target, menu, ctx.hasFlag("test"));
        if (!target.equals(ctx.asPlayer())) {
            ctx.success("Opened menu '" + menu.id() + "' for " + target.getName() + ".");
        }
    }

    private void commandSelect(CommandContext ctx) {
        ctx.checkNotConsole("COMMAND_PLAYER_ONLY");
        ctx.checkArgsSizeAtLeast(3, "Usage: /imenu select <menu> <item> [-test]");

        MenuDefinition menu = requireMenu(ctx, normalizeId(ctx.getArg(1)));
        MenuItem item = requireItem(ctx, menu, normalizeId(ctx.getArg(2)));
        selectItem(ctx.asPlayer(), menu, item, ctx.hasFlag("test"));
    }

    private void commandList(CommandContext ctx) {
        if (menus.isEmpty()) {
            ctx.returnInfo("No interactive menus are configured.");
        }

        ctx.info("Interactive menus:");
        menus.values().forEach(menu -> ctx.info(" - " + menu.id() + " (" + menu.items().size() + " item(s))"));
    }

    private void commandInfo(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "Usage: /imenu info <menu>");
        MenuDefinition menu = requireMenu(ctx, normalizeId(ctx.getArg(1)));

        ctx.info("Menu '" + menu.id() + "':");
        ctx.info(" - Title: " + TextUtil.stripColour(menu.title()));
        ctx.info(" - Body: " + TextUtil.stripColour(menu.body()));
        ctx.info(" - Items: " + menu.items().size());
        for (MenuItem item : menu.items().values()) {
            ctx.info("   - " + item.id() + ": " + TextUtil.stripColour(item.title()));
            for (String command : item.commands()) {
                ctx.info("     * " + command);
            }
        }
    }

    private void commandCreate(CommandContext ctx) {
        requireEditPermission(ctx);
        ctx.checkArgsSizeAtLeast(2, "Usage: /imenu create <menu> [title]");

        String menuId = normalizeId(ctx.getArg(1));
        validateId(ctx, menuId, "menu");
        if (menus.containsKey(menuId)) {
            ctx.returnError("Interactive menu already exists: " + menuId);
        }

        String title = ctx.numArgs() > 2 ? ctx.getArgsAsString(3) : menuId;
        ConfigSection menu = getConfigSection().getSection("menus").createSection(menuId);
        menu.set("title", title);
        menu.set("body", "");
        menu.set("book-title", TextUtil.stripColour(title));
        menu.set("book-author", "STEMCraft");
        menu.createSection("items");
        menu.save();
        reloadMenus();
        ctx.returnSuccess("Created interactive menu '" + menuId + "'.");
    }

    private void commandDelete(CommandContext ctx) {
        requireEditPermission(ctx);
        ctx.checkArgsSizeAtLeast(2, "Usage: /imenu delete <menu>");

        String menuId = normalizeId(ctx.getArg(1));
        requireMenu(ctx, menuId);
        getConfigSection().set("menus." + menuId, null);
        getConfigSection().save();
        reloadMenus();
        ctx.returnSuccess("Deleted interactive menu '" + menuId + "'.");
    }

    private void commandSet(CommandContext ctx) {
        requireEditPermission(ctx);
        ctx.checkArgsSizeAtLeast(4, "Usage: /imenu set <menu> <title|body|book-title|book-author> <value>");

        String menuId = normalizeId(ctx.getArg(1));
        requireMenu(ctx, menuId);
        String field = normalizeField(ctx.getArg(2));
        if (!List.of("title", "body", "book-title", "book-author").contains(field)) {
            ctx.returnError("Unknown menu field: " + ctx.getArg(2));
        }

        getConfigSection().set("menus." + menuId + "." + field, ctx.getArgsAsString(4));
        getConfigSection().save();
        reloadMenus();
        ctx.returnSuccess("Updated " + field + " for menu '" + menuId + "'.");
    }

    private void commandItem(CommandContext ctx) {
        requireEditPermission(ctx);
        ctx.checkArgsSizeAtLeast(2, "Usage: /imenu item <add|remove|set|command>");

        switch (ctx.getArgLower(1)) {
            case "add" -> commandItemAdd(ctx);
            case "remove" -> commandItemRemove(ctx);
            case "set" -> commandItemSet(ctx);
            case "command" -> commandItemCommand(ctx);
            default -> ctx.returnError("Unknown item subcommand: " + ctx.getArg(1));
        }
    }

    private void commandItemAdd(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(5, "Usage: /imenu item add <menu> <item> <title>");

        String menuId = normalizeId(ctx.getArg(2));
        MenuDefinition menu = requireMenu(ctx, menuId);
        String itemId = normalizeId(ctx.getArg(3));
        validateId(ctx, itemId, "item");
        if (menu.items().containsKey(itemId)) {
            ctx.returnError("Menu item already exists: " + itemId);
        }

        ConfigSection item = getConfigSection().getSection("menus." + menuId + ".items").createSection(itemId);
        item.set("title", ctx.getArgsAsString(5));
        item.set("description", "");
        item.set("commands", List.of());
        item.save();
        reloadMenus();
        ctx.returnSuccess("Added item '" + itemId + "' to menu '" + menuId + "'.");
    }

    private void commandItemRemove(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(4, "Usage: /imenu item remove <menu> <item>");

        String menuId = normalizeId(ctx.getArg(2));
        MenuDefinition menu = requireMenu(ctx, menuId);
        String itemId = normalizeId(ctx.getArg(3));
        requireItem(ctx, menu, itemId);

        getConfigSection().set("menus." + menuId + ".items." + itemId, null);
        getConfigSection().save();
        reloadMenus();
        ctx.returnSuccess("Removed item '" + itemId + "' from menu '" + menuId + "'.");
    }

    private void commandItemSet(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(6, "Usage: /imenu item set <menu> <item> <title|description> <value>");

        String menuId = normalizeId(ctx.getArg(2));
        MenuDefinition menu = requireMenu(ctx, menuId);
        String itemId = normalizeId(ctx.getArg(3));
        requireItem(ctx, menu, itemId);
        String field = normalizeField(ctx.getArg(4));
        if (!List.of("title", "description").contains(field)) {
            ctx.returnError("Unknown item field: " + ctx.getArg(4));
        }

        getConfigSection().set("menus." + menuId + ".items." + itemId + "." + field, ctx.getArgsAsString(6));
        getConfigSection().save();
        reloadMenus();
        ctx.returnSuccess("Updated " + field + " for item '" + itemId + "'.");
    }

    private void commandItemCommand(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(5, "Usage: /imenu item command <add|clear> <menu> <item> [command]");

        switch (ctx.getArgLower(2)) {
            case "add" -> {
                ctx.checkArgsSizeAtLeast(6, "Usage: /imenu item command add <menu> <item> <command>");
                addItemCommand(ctx);
            }
            case "clear" -> clearItemCommands(ctx);
            default -> ctx.returnError("Unknown item command action: " + ctx.getArg(2));
        }
    }

    private void addItemCommand(CommandContext ctx) {
        String menuId = normalizeId(ctx.getArg(3));
        MenuDefinition menu = requireMenu(ctx, menuId);
        String itemId = normalizeId(ctx.getArg(4));
        MenuItem item = requireItem(ctx, menu, itemId);

        String commandLine = normalizeCommand(String.join(" ", ctx.rawArgs().subList(5, ctx.rawArgs().size())));
        if (commandLine.isBlank()) {
            ctx.returnError("Command cannot be empty.");
        }

        List<String> commands = new ArrayList<>(item.commands());
        commands.add(commandLine);
        getConfigSection().set("menus." + menuId + ".items." + itemId + ".commands", commands);
        getConfigSection().save();
        reloadMenus();
        ctx.returnSuccess("Added command to item '" + itemId + "'.");
    }

    private void clearItemCommands(CommandContext ctx) {
        String menuId = normalizeId(ctx.getArg(3));
        MenuDefinition menu = requireMenu(ctx, menuId);
        String itemId = normalizeId(ctx.getArg(4));
        requireItem(ctx, menu, itemId);

        getConfigSection().set("menus." + menuId + ".items." + itemId + ".commands", List.of());
        getConfigSection().save();
        reloadMenus();
        ctx.returnSuccess("Cleared commands for item '" + itemId + "'.");
    }

    private void reloadMenus() {
        menus.clear();

        ConfigSection menusSection = getConfigSection().getSection("menus", false);
        if (menusSection == null) {
            return;
        }

        for (String menuKey : menusSection.getKeys(false)) {
            String menuId = normalizeId(menuKey);
            if (!ID_PATTERN.matcher(menuId).matches()) {
                api.messages().warn("Interactive menu '" + menuKey + "' has an invalid id.");
                continue;
            }

            ConfigSection menuSection = menusSection.getSection(menuKey, false);
            if (menuSection == null) {
                continue;
            }

            Map<String, MenuItem> items = loadItems(menuId, menuSection);
            menus.put(menuId, new MenuDefinition(
                menuId,
                menuSection.getString("title", menuId),
                menuSection.getString("body", ""),
                menuSection.getString("book-title", TextUtil.stripColour(menuSection.getString("title", menuId))),
                menuSection.getString("book-author", "STEMCraft"),
                items
            ));
        }
    }

    private @NotNull Map<String, MenuItem> loadItems(@NotNull String menuId, @NotNull ConfigSection menuSection) {
        Map<String, MenuItem> items = new LinkedHashMap<>();
        ConfigSection itemsSection = menuSection.getSection("items", false);
        if (itemsSection == null) {
            return items;
        }

        for (String itemKey : itemsSection.getKeys(false)) {
            String itemId = normalizeId(itemKey);
            if (!ID_PATTERN.matcher(itemId).matches()) {
                api.messages().warn("Interactive menu item '" + menuId + "." + itemKey + "' has an invalid id.");
                continue;
            }

            ConfigSection itemSection = itemsSection.getSection(itemKey, false);
            if (itemSection == null) {
                continue;
            }

            items.put(itemId, new MenuItem(
                itemId,
                itemSection.getString("title", itemId),
                itemSection.getString("description", ""),
                normalizeCommands(itemSection)
            ));
        }
        return items;
    }

    private @NotNull List<String> normalizeCommands(@NotNull ConfigSection section) {
        List<String> commands = new ArrayList<>();
        Object rawCommands = section.get("commands");
        if (rawCommands instanceof String command) {
            addNormalizedCommand(commands, command);
        } else {
            section.getStringList("commands").forEach(command -> addNormalizedCommand(commands, command));
        }

        if (commands.isEmpty() && section.get("command") instanceof String command) {
            addNormalizedCommand(commands, command);
        }

        return List.copyOf(commands);
    }

    private void addNormalizedCommand(@NotNull List<String> commands, @Nullable String command) {
        String normalized = normalizeCommand(command);
        if (!normalized.isBlank()) {
            commands.add(normalized);
        }
    }

    private void showMenu(Player player, MenuDefinition menu, boolean testMode) {
        if (menu.items().isEmpty()) {
            api.messages().warn(player, "Interactive menu '" + menu.id() + "' has no items.");
            return;
        }

        if (PlayerUtil.isBedrock(player) && sendBedrockForm(player, menu, testMode)) {
            return;
        }

        if (PlayerUtil.isBedrock(player)) {
            showChatFallback(player, menu, testMode);
            return;
        }

        player.openBook(buildBook(menu, testMode));
    }

    private boolean sendBedrockForm(Player player, MenuDefinition menu, boolean testMode) {
        try {
            SimpleForm.Builder form = SimpleForm.builder()
                .title(TextUtil.stripColour(menu.title()))
                .content(TextUtil.stripColour(menu.body()));

            List<MenuItem> items = new ArrayList<>(menu.items().values());
            for (MenuItem item : items) {
                String button = TextUtil.stripColour(item.title());
                String description = TextUtil.stripColour(item.description());
                if (!description.isBlank()) {
                    button += "\n" + description;
                }
                form.button(button);
            }

            form.validResultHandler(response -> {
                int selected = response.clickedButtonId();
                if (selected >= 0 && selected < items.size()) {
                    Bukkit.getScheduler().runTask(STEMCraft.getPlugin(), () ->
                        selectItem(player, menu, items.get(selected), testMode)
                    );
                }
            });

            return GeyserApi.api().sendForm(player.getUniqueId(), form);
        } catch (LinkageError | RuntimeException exception) {
            return false;
        }
    }

    private void showChatFallback(Player player, MenuDefinition menu, boolean testMode) {
        player.sendMessage(TextUtil.colourise(menu.title()));
        if (!menu.body().isBlank()) {
            player.sendMessage(TextUtil.colourise(menu.body()));
        }

        for (MenuItem item : menu.items().values()) {
            String command = "/imenu select " + menu.id() + " " + item.id() + (testMode ? " -test" : "");
            Component line = Component.text(command, NamedTextColor.AQUA)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(TextUtil.colourise(item.title()));
            player.sendMessage(line);
        }
    }

    private ItemStack buildBook(MenuDefinition menu, boolean testMode) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(TextUtil.colouriseToSection(menu.bookTitle()));
        meta.setAuthor(TextUtil.colouriseToSection(menu.bookAuthor()));

        List<Component> pages = new ArrayList<>();
        Component page = TextUtil.colourise(menu.title());
        int lines = 1;
        if (!menu.body().isBlank()) {
            page = page.append(Component.newline()).append(TextUtil.colourise(menu.body()));
            lines += 1;
        }
        page = page.append(Component.newline()).append(Component.newline());
        lines += 2;

        for (MenuItem item : menu.items().values()) {
            if (lines >= BOOK_LINES_PER_PAGE) {
                pages.add(page);
                page = Component.empty();
                lines = 0;
            }

            String selectCommand = "/imenu select " + menu.id() + " " + item.id() + (testMode ? " -test" : "");
            page = page.append(Component.text("> ", NamedTextColor.DARK_GRAY))
                .append(TextUtil.colourise(item.title())
                    .clickEvent(ClickEvent.runCommand(selectCommand))
                    .hoverEvent(HoverEvent.showText(Component.text(selectCommand))))
                .append(Component.newline());
            lines++;

            if (!item.description().isBlank()) {
                page = page.append(TextUtil.colourise("<gray>" + item.description() + "</gray>"))
                    .append(Component.newline());
                lines++;
            }
        }

        pages.add(page);
        meta.addPages(pages.toArray(Component[]::new));
        if (!book.setItemMeta(meta)) {
            throw new IllegalStateException("Failed to apply interactive menu book metadata");
        }
        return book;
    }

    private void selectItem(Player player, MenuDefinition menu, MenuItem item, boolean testMode) {
        if (testMode) {
            player.sendMessage(TextUtil.colourise("<gold>Test selection:</gold> " + menu.id() + "." + item.id()));
            if (item.commands().isEmpty()) {
                player.sendMessage(TextUtil.colourise("<gray>No commands configured.</gray>"));
                return;
            }

            for (String command : item.commands()) {
                player.sendMessage(TextUtil.colourise("<gray>Would run:</gray> " + describeCommand(player, menu, item, command)));
            }
            return;
        }

        if (item.commands().isEmpty()) {
            api.messages().warn(player, "No commands are configured for this menu item.");
            return;
        }

        for (String command : item.commands()) {
            dispatchConfiguredCommand(player, menu, item, command);
        }
    }

    private void dispatchConfiguredCommand(Player player, MenuDefinition menu, MenuItem item, String rawCommand) {
        String command = applyPlaceholders(player, menu, item, rawCommand);
        if (command.regionMatches(true, 0, "player:", 0, "player:".length())) {
            String playerCommand = command.substring("player:".length()).trim();
            if (!playerCommand.isBlank()) {
                Bukkit.dispatchCommand(player, playerCommand);
            }
            return;
        }

        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        Bukkit.dispatchCommand(console, command);
    }

    private String describeCommand(Player player, MenuDefinition menu, MenuItem item, String rawCommand) {
        String command = applyPlaceholders(player, menu, item, rawCommand);
        if (command.regionMatches(true, 0, "player:", 0, "player:".length())) {
            return "player:" + command.substring("player:".length()).trim();
        }
        return "console:" + command;
    }

    private String applyPlaceholders(Player player, MenuDefinition menu, MenuItem item, String input) {
        return PlaceholderUtil.apply(
            input,
            "player", player.getName(),
            "uuid", player.getUniqueId().toString(),
            "menu", menu.id(),
            "item", item.id(),
            "item_title", TextUtil.stripColour(item.title()),
            "item-title", TextUtil.stripColour(item.title())
        );
    }

    private void requireEditPermission(CommandContext ctx) {
        if (!ctx.hasPermission(EDIT_PERMISSION)) {
            ctx.returnError("COMMAND_NO_PERMISSION");
        }
    }

    private MenuDefinition requireMenu(CommandContext ctx, String menuId) {
        MenuDefinition menu = menus.get(menuId);
        if (menu == null) {
            ctx.returnError("Unknown interactive menu: " + menuId);
        }
        return menu;
    }

    private MenuItem requireItem(CommandContext ctx, MenuDefinition menu, String itemId) {
        MenuItem item = menu.items().get(itemId);
        if (item == null) {
            ctx.returnError("Unknown menu item: " + itemId);
        }
        return item;
    }

    private void validateId(CommandContext ctx, String id, String label) {
        if (!ID_PATTERN.matcher(id).matches()) {
            ctx.returnError("Invalid " + label + " id: " + id);
        }
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeField(String field) {
        return field == null ? "" : field.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String normalizeCommand(String command) {
        String normalized = command == null ? "" : command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private record MenuDefinition(
        String id,
        String title,
        String body,
        String bookTitle,
        String bookAuthor,
        Map<String, MenuItem> items
    ) {}

    private record MenuItem(
        String id,
        String title,
        String description,
        List<String> commands
    ) {}
}

/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.item.CustomItemDefinition;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Child-friendly live item workshop command. */
public final class ItemEditCommand extends BaseCommand {
    private static final String EDIT_PERMISSION = "stemcraft.itemedit.edit";
    private static final String TEST_PERMISSION = "stemcraft.itemedit.test";
    private static final String PUBLISH_PERMISSION = "stemcraft.itemedit.publish";

    public ItemEditCommand(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onLoad() {
        setLabel("itemedit");
        addAliases("iedit");
        setDescription("Edit a live STEMCraft item draft");
        setUsage("/itemedit <list|info|hand|item-id|create|set|test|undo|redo|save|cancel>");
        setAccess(ItemEditCommand::canAccess);
        addTabCompletion("hand");
        addTabCompletion("list");
        addTabCompletion("list", "{number}");
        addTabCompletion("info");
        addTabCompletion("info", "{workshop-item}");
        addTabCompletion("create");
        addTabCompletion("set");
        addTabCompletion("set", "potion-effects", "{workshop-effect}");
        addTabCompletion("set", "ability-add", "{workshop-trigger}", "{workshop-effect}");
        addTabCompletion("set", "ability-edit", "{workshop-trigger}", "{number}", "{workshop-effect}");
        addTabCompletion("test");
        addTabCompletion("undo");
        addTabCompletion("redo");
        addTabCompletion("save");
        addTabCompletion("cancel");
        addTabCompletion("{workshop-item}");
        addTabCompletion("create", "{workshop-item}", "{material}", "{workshop-pack}");
        addTabCompletion("set", "{workshop-field}");
        addTabCompletion("test", "{player}", "{number}");
        api.tabComplete().register("workshop-item", (player, args, current) -> {
            List<String> ids = new ArrayList<>();
            api.items().customItemDefinitions().forEach(definition -> ids.add(definition.id()));
            return ids;
        });
        api.tabComplete().register("workshop-pack", (player, args, current) -> plugin.contentPacks() == null
            ? List.of() : plugin.contentPacks().packs().stream().filter(info -> info.enabled())
                .map(info -> info.id()).toList());
        api.tabComplete().register("workshop-field", (player, args, current) -> List.of("name", "lore", "material",
            "color", "durability", "texture", "attack-damage", "attack-speed", "armor", "toughness",
            "enchantments", "potion-effects", "ability", "ability-add", "ability-edit", "ability-remove"));
        api.tabComplete().register("material", (player, args, current) -> java.util.Arrays.stream(Material.values())
            .filter(Material::isItem).map(material -> material.name().toLowerCase(Locale.ROOT)).toList());
        api.tabComplete().register("workshop-effect", (player, args, current) -> Registry.MOB_EFFECT.stream()
            .map(effect -> effect.getKey().getKey()).sorted().toList());
        api.tabComplete().register("workshop-particle", (player, args, current) -> java.util.Arrays.stream(Particle.values())
            .map(particle -> particle.name().toLowerCase(Locale.ROOT)).sorted().toList());
        api.tabComplete().register("workshop-slot", (player, args, current) -> java.util.Arrays.stream(EquipmentSlot.values())
            .map(slot -> slot.name().toLowerCase(Locale.ROOT)).toList());
        api.tabComplete().register("workshop-trigger", (player, args, current) -> List.of(
            "while-equipped", "on-hit", "on-hurt", "on-projectile-hit", "on-consume", "on-walk"));
        register(plugin);
    }

    @Override
    public void onExecute(Command command, CommandContext ctx) {
        Player player = ctx.asPlayer();
        if (player == null) ctx.returnError("This command is for players.");
        String action = Objects.requireNonNullElse(ctx.getArgLower(0), "hand");
        switch (action) {
            case "hand" -> {
                String heldId = api.items().getCustomItemId(player.getInventory().getItemInMainHand());
                if (heldId == null) ctx.returnError("Hold a registered STEMCraft item first.");
                open(ctx, player, heldId);
            }
            case "list" -> list(ctx);
            case "info" -> info(ctx);
            case "create" -> create(ctx, player);
            case "set" -> set(ctx, player);
            case "test" -> test(ctx, player);
            case "undo" -> result(ctx, api.itemEditor().undo(player), "Undo applied.", "Nothing to undo.");
            case "redo" -> result(ctx, api.itemEditor().redo(player), "Redo applied.", "Nothing to redo.");
            case "save", "publish" -> {
                require(ctx, PUBLISH_PERMISSION);
                if (!api.itemEditor().save(player)) {
                    if (api.itemEditor().selected(player) == null) ctx.returnError("No live draft is open.");
                    return;
                }
                ctx.returnSuccess("Item published and reloaded.");
            }
            case "cancel" -> result(ctx, api.itemEditor().cancel(player), "Draft cancelled.", "No live draft is open.");
            default -> open(ctx, player, action);
        }
    }

    private void list(CommandContext ctx) {
        List<CustomItemDefinition> definitions = definitions();
        int page = ChatMenuUtil.getPageFromArgs(ctx.args(), 1, 1);
        boolean canEdit = ctx.hasPermission(EDIT_PERMISSION);
        ChatMenuUtil.render(ctx.getSender(), Component.text("Custom items", NamedTextColor.AQUA),
            "itemedit list", page, definitions.size(), (start, count, interactive) -> {
                List<Component> lines = new ArrayList<>();
                int end = Math.min(start + count, definitions.size());
                for (int index = start; index < end; index++) {
                    CustomItemDefinition definition = definitions.get(index);
                    Component line = Component.text((index + 1) + ". ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(api.items().getItemName(definition.template()), NamedTextColor.WHITE))
                        .append(Component.text(" (" + definition.id() + ")", NamedTextColor.GRAY));
                    if (interactive && canEdit) {
                        line = line.append(Component.text(" [Edit]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/itemedit " + definition.id()))
                            .hoverEvent(HoverEvent.showText(Component.text("Open a live draft"))));
                    }
                    lines.add(line);
                }
                return lines;
            }, "No custom items are available.");
    }

    private void info(CommandContext ctx) {
        String requestedId = ctx.getArg(1);
        if (requestedId == null || requestedId.isBlank()) {
            ctx.returnError("Usage: /itemedit info <item-id>");
            return;
        }
        CustomItemDefinition definition = definitions().stream()
            .filter(candidate -> candidate.id().equalsIgnoreCase(requestedId))
            .findFirst().orElse(null);
        if (definition == null) {
            ctx.returnError("Custom item not found: " + requestedId);
            return;
        }

        String itemName = api.items().getItemName(definition.template());
        ctx.info("Custom item: " + itemName);
        ctx.info(" - ID: " + definition.id());
        ctx.info(" - Material: " + readable(definition.template().getType().name()));
        ctx.info(" - Placement: " + readable(definition.placementMode().name()));
        if (definition.managedObjectType() != null)
            ctx.info(" - Managed object: " + definition.managedObjectType());
        if (definition.clients() != null && definition.clients().java() != null)
            ctx.info(" - Java model: " + definition.clients().java().itemModelId());
        if (definition.clients() != null && definition.clients().bedrock() != null)
            ctx.info(" - Bedrock identifier: " + definition.clients().bedrock().identifier());
        ctx.returnInfo("Use /itemedit " + definition.id() + " to open a live draft.");
    }

    private List<CustomItemDefinition> definitions() {
        return api.items().customItemDefinitions().stream()
            .sorted(Comparator.comparing(CustomItemDefinition::id, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static String readable(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private void create(CommandContext ctx, Player player) {
        require(ctx, EDIT_PERMISSION);
        if (ctx.numArgs() < 3) ctx.returnError("Usage: /itemedit create <item-id> <material> [pack]");
        String pack = ctx.getArg(3);
        if (pack == null || pack.isBlank()) pack = firstEnabledPack();
        if (pack == null || !api.itemEditor().create(player, ctx.getArg(1), ctx.getArg(2), pack))
            ctx.returnError("Could not create the item draft. Check the item ID, material, and pack.");
        ctx.returnSuccess("Live draft created for " + ctx.getArg(1) + ".");
    }

    private void set(CommandContext ctx, Player player) {
        require(ctx, EDIT_PERMISSION);
        if (ctx.numArgs() < 3) ctx.returnError("Usage: /itemedit set <field> <value>");
        String value = String.join(" ", ctx.args().subList(2, ctx.args().size()));
        if (!api.itemEditor().set(player, ctx.getArg(1), value)) {
            if (api.itemEditor().selected(player) == null) ctx.returnError("No live draft is open.");
            return;
        }
        ctx.returnSuccess("Updated the live draft preview.");
    }

    private void test(CommandContext ctx, Player player) {
        require(ctx, TEST_PERMISSION);
        Player target = player;
        int amount = 1;
        if (ctx.getArg(1) != null) {
            target = Bukkit.getPlayerExact(ctx.getArg(1));
            if (target == null) {
                ctx.returnError("Player is not online: " + ctx.getArg(1));
                return;
            }
        }
        if (ctx.getArg(2) != null) {
            try { amount = Integer.parseInt(ctx.getArg(2)); }
            catch (NumberFormatException exception) {
                ctx.returnError("Amount must be a whole number.");
                return;
            }
        }
        if (!api.itemEditor().test(player, target, amount)) ctx.returnError("No live draft is open.");
        ctx.returnSuccess("Preview item sent to " + target.getName() + ".");
    }

    private void open(CommandContext ctx, Player player, String itemId) {
        if (itemId == null || itemId.isBlank() || !api.itemEditor().open(player, itemId))
            ctx.returnError("No editable custom item was found.");
        ctx.returnSuccess("Live draft opened. Use /itemedit set or the dialog; /itemedit save publishes it.");
    }

    private void result(CommandContext ctx, boolean success, String yes, String no) {
        if (success) ctx.returnSuccess(yes); else ctx.returnError(no);
    }

    private void require(CommandContext ctx, String permission) {
        if (!ctx.hasPermission(permission)) ctx.returnError("COMMAND_NO_PERMISSION");
    }

    private static boolean canAccess(CommandSender sender, List<String> args) {
        if (!(sender instanceof Player)) return true;
        String action = args == null || args.isEmpty() ? "hand" : args.getFirst().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "test" -> sender.hasPermission(TEST_PERMISSION);
            case "save", "publish" -> sender.hasPermission(PUBLISH_PERMISSION);
            case "hand", "list", "info", "create", "set", "undo", "redo", "cancel" ->
                sender.hasPermission(EDIT_PERMISSION);
            default -> sender.hasPermission(EDIT_PERMISSION);
        };
    }

    private String firstEnabledPack() {
        if (plugin.contentPacks() == null) return null;
        return plugin.contentPacks().packs().stream().filter(info -> info.enabled()).map(info -> info.id()).findFirst().orElse(null);
    }
}

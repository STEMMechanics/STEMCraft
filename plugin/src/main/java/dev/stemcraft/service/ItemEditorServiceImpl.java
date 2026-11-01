/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.dialog.DialogResponse;
import dev.stemcraft.api.service.editor.EditorWebContext;
import dev.stemcraft.api.service.editor.EditorWebModule;
import dev.stemcraft.api.service.editor.EditorWebResponse;
import dev.stemcraft.api.service.itemeditor.ItemEditorService;
import dev.stemcraft.api.service.item.CustomItemDefinition;
import dev.stemcraft.api.service.web.WebServiceRequest;
import dev.stemcraft.api.util.TextUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-player draft editor with a live preview and atomic YAML publication step. */
public final class ItemEditorServiceImpl extends BaseService implements ItemEditorService {
    private static final int MAX_AMOUNT = 99 * 36;
    private static final long DRAFT_TIMEOUT_MILLIS = 30L * 60L * 1_000L;
    private static final Gson EDITOR_GSON = new GsonBuilder().serializeNulls().create();
    private final Map<UUID, Draft> drafts = new HashMap<>();

    public ItemEditorServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "item-editor");
    }

    @Override
    public void onEnable() {
        api.tasks().repeating("workshop:item-editor:cleanup", 1_200L, 1_200L, this::expireDrafts);
        if (api.editorWeb() != null) api.editorWeb().registerModule(new EditorWebModule() {
            @Override public @NotNull String id() { return "items"; }
            @Override public @NotNull String title() { return "Items"; }
            @Override public @NotNull String icon() { return "I"; }
            @Override public @NotNull String description() { return "Edit custom item definitions."; }
            @Override public @NotNull String readPermission() { return "stemcraft.itemedit.edit"; }
            @Override public @NotNull String writePermission() { return "stemcraft.itemedit.publish"; }
            @Override public @NotNull EditorWebResponse handle(@NotNull EditorWebContext context, @NotNull String path,
                                                                 @NotNull WebServiceRequest request) {
                return handleEditorRequest(context, path, request);
            }
        });
        api.events().register(org.bukkit.event.player.PlayerQuitEvent.class,
            event -> {
                Draft draft = drafts.remove(event.getPlayer().getUniqueId());
                if (draft != null) api.itemAbilities().unregisterPreview(draft.token);
            });
    }

    @Override
    public void onDisable() {
        api.tasks().cancel("workshop:item-editor:cleanup");
        drafts.values().forEach(draft -> api.itemAbilities().unregisterPreview(draft.token));
        drafts.clear();
    }

    @Override
    public boolean open(@NotNull Player player, @NotNull String itemId) {
        String id = normalizeItemId(itemId);
        Located located = locate(id);
        if (located == null) return false;
        Draft previous = drafts.remove(player.getUniqueId());
        if (previous != null) api.itemAbilities().unregisterPreview(previous.token);
        Draft draft = new Draft(id, located.configKey(), located.packId(), located.file(), deepCopy(located.values()),
            lastModified(located.file()), UUID.randomUUID().toString());
        drafts.put(player.getUniqueId(), draft);
        syncPreviewAbilities(draft);
        preview(player, draft);
        showIndicator(player, draft);
        openDialog(player, draft);
        return true;
    }

    @Override
    public boolean create(@NotNull Player player, @NotNull String itemId, @NotNull String material,
                          @NotNull String packId) {
        String id = normalizeItemId(itemId);
        if (locate(id) != null) return false;
        Material parsed = Material.matchMaterial(material.trim().toUpperCase(Locale.ROOT));
        if (parsed == null || !parsed.isItem()) return false;
        var pack = plugin.contentPacks() == null ? null : plugin.contentPacks().find(packId);
        if (pack == null || !pack.enabled()) return false;
        Draft previous = drafts.remove(player.getUniqueId());
        if (previous != null) api.itemAbilities().unregisterPreview(previous.token);
        File file = new File(new File(pack.path()), "configs/workshop-items.yml");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("material", parsed.name());
        Draft draft = new Draft(id, id, pack.id(), file, values, lastModified(file), UUID.randomUUID().toString());
        drafts.put(player.getUniqueId(), draft);
        syncPreviewAbilities(draft);
        preview(player, draft);
        showIndicator(player, draft);
        openDialog(player, draft);
        return true;
    }

    @Override
    public boolean set(@NotNull Player player, @NotNull String field, @NotNull String value) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null) return false;
        touch(draft);
        Map<String, Object> before = deepCopy(draft.values);
        try {
            mutate(draft.values, field, value, draft.packId);
        } catch (IllegalArgumentException exception) {
            return reportError(player, exception.getMessage());
        }
        draft.undo.push(before);
        draft.redo.clear();
        syncPreviewAbilities(draft);
        preview(player, draft);
        showIndicator(player, draft);
        return true;
    }

    @Override
    public boolean undo(@NotNull Player player) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null || draft.undo.isEmpty()) return false;
        touch(draft);
        draft.redo.push(deepCopy(draft.values));
        draft.values = draft.undo.pop();
        syncPreviewAbilities(draft);
        preview(player, draft);
        showIndicator(player, draft);
        return true;
    }

    @Override
    public boolean redo(@NotNull Player player) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null || draft.redo.isEmpty()) return false;
        touch(draft);
        draft.undo.push(deepCopy(draft.values));
        draft.values = draft.redo.pop();
        syncPreviewAbilities(draft);
        preview(player, draft);
        showIndicator(player, draft);
        return true;
    }

    @Override
    public boolean cancel(@NotNull Player player) {
        Draft draft = drafts.remove(player.getUniqueId());
        if (draft == null) return false;
        api.itemAbilities().unregisterPreview(draft.token);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (hasPreviewToken(held, draft.token)) {
            if (draft.originalHeld != null) {
                ItemStack restored = draft.originalHeld.clone();
                restored.setAmount(Math.clamp(held.getAmount(), 1, restored.getMaxStackSize()));
                player.getInventory().setItemInMainHand(restored);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        }
        api.itemAbilities().refresh(player);
        player.sendActionBar(Component.text("Live draft cancelled: " + draft.itemId));
        return true;
    }

    @Override
    public boolean save(@NotNull Player player) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null) return false;
        touch(draft);
        long currentModified = lastModified(draft.file);
        if (currentModified != draft.baseModified) {
            return reportError(player, "the definition changed elsewhere; reload it before saving.");
        }
        File parent = draft.file.getParentFile();
        if (!parent.exists() && !parent.mkdirs())
            return reportError(player, "could not create the item file directory.");
        ConfigFile config = api.config().load(draft.file);
        if (config == null) return reportError(player, "could not load the item file.");
        byte[] previousFile = null;
        boolean fileExisted = draft.file.isFile();
        try {
            if (fileExisted) previousFile = Files.readAllBytes(draft.file.toPath());
        } catch (IOException exception) {
            return reportError(player, "could not read the item file before saving.");
        }
        ConfigSection items = config.getSection("custom-items");
        items.set(draft.configKey, deepCopy(draft.values));
        config.save();
        boolean reloaded = "__root__".equals(draft.packId)
            ? plugin.items() != null && plugin.items().reloadRootItems()
            : plugin.contentPacks() != null && plugin.contentPacks().reload(draft.packId);
        if (!reloaded) {
            try {
                if (fileExisted && previousFile != null) Files.write(draft.file.toPath(), previousFile);
                else Files.deleteIfExists(draft.file.toPath());
                config.reload();
            } catch (IOException restoreFailure) {
                plugin.getLogger().warning("[item-editor] Could not restore failed draft file '"
                    + draft.file + "': " + restoreFailure.getMessage());
            }
            return reportError(player, "validation failed; runtime definition was not changed.");
        }
        draft.baseModified = lastModified(draft.file);
        draft.undo.clear();
        draft.redo.clear();
        syncPreviewAbilities(draft);
        preview(player, draft);
        player.sendActionBar(Component.text("Published " + draft.itemId + " (live draft retained)"));
        return true;
    }

    private boolean reportError(Player player, String message) {
        api.messages().error(player, "Item edit: " + message);
        return false;
    }

    @Override
    public boolean test(@NotNull Player editor, @Nullable Player target, int amount) {
        Draft draft = drafts.get(editor.getUniqueId());
        if (draft == null) return false;
        touch(draft);
        Player recipient = target == null ? editor : target;
        int remaining = Math.clamp(amount, 1, MAX_AMOUNT);
        while (remaining > 0) {
            ItemStack stack = buildPreview(editor, draft, Math.min(remaining, 64), null);
            if (stack == null) return false;
            recipient.getInventory().addItem(stack).values()
                .forEach(item -> recipient.getWorld().dropItemNaturally(recipient.getLocation(), item));
            remaining -= stack.getAmount();
        }
        return true;
    }

    @Override
    public @Nullable String selected(@NotNull Player player) {
        Draft draft = drafts.get(player.getUniqueId());
        return draft == null ? null : draft.itemId;
    }

    private EditorWebResponse handleEditorRequest(EditorWebContext context, String path, WebServiceRequest request) {
        try {
            if ("/data".equals(path) && "GET".equalsIgnoreCase(request.method())) {
                List<Map<String, Object>> items = new ArrayList<>();
                api.items().customItemDefinitions().stream()
                    .sorted(java.util.Comparator.comparing(CustomItemDefinition::id, String.CASE_INSENSITIVE_ORDER))
                    .forEach(definition -> {
                        Located located = locate(normalizeItemId(definition.id()));
                        if (located == null) return;
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("id", definition.id());
                        value.put("displayName", api.items().getItemName(definition.template()));
                        value.put("material", definition.template().getType().name());
                        value.put("pack", located.packId());
                        value.put("file", located.file().getName());
                        value.put("revision", String.valueOf(lastModified(located.file())));
                        value.put("values", deepCopy(located.values()));
                        items.add(value);
                    });
                List<Map<String, Object>> packs = plugin.contentPacks() == null ? List.of() : plugin.contentPacks().packs().stream()
                    .map(pack -> Map.<String, Object>of("id", pack.id(), "enabled", pack.enabled()))
                    .toList();
                List<String> materials = java.util.Arrays.stream(Material.values())
                    .filter(material -> material.isItem() && !material.isAir())
                    .map(Material::name).sorted().toList();
                return json(200, Map.of("kind", "items", "items", items, "packs", packs, "materials", materials));
            }
            if ("/create".equals(path) && "POST".equalsIgnoreCase(request.method())) {
                if (!context.writable()) return textResponse(403, "This link is read-only.");
                if (plugin.contentPacks() == null) return textResponse(503, "Content packs are unavailable.");
                Map<?, ?> body = EDITOR_GSON.fromJson(request.bodyAsString(), Map.class);
                String pack = requiredText(body.get("pack"), "Choose a content pack.");
                String file = body.get("file") == null ? "configs/items.yml" : String.valueOf(body.get("file")).trim();
                if (file.isBlank()) file = "configs/items.yml";
                if (!file.contains("/")) file = "configs/" + file;
                String id = normalizeItemId(requiredText(body.get("id"), "Enter an item ID."));
                String material = requiredText(body.get("material"), "Choose an item material").toUpperCase(Locale.ROOT);
                Material parsed = Material.matchMaterial(material);
                if (parsed == null || !parsed.isItem()) return textResponse(400, "Choose a valid item material.");
                ContentPackServiceImpl.DocumentSnapshot snapshot = plugin.contentPacks().editorDocument(pack, file);
                if (snapshot == null) {
                    snapshot = plugin.contentPacks().createEditorDocument(pack, file, "custom-items: {}\n");
                    if (snapshot == null) return textResponse(400, "The item file could not be created in that pack.");
                }
                if (plugin.contentPacks().editorValue(pack, file, "custom-items." + id) != null)
                    return textResponse(409, "That item ID already exists in this file.");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("material", material);
                String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
                if (!name.isBlank()) item.put("name", name);
                ContentPackServiceImpl.DocumentSaveResult result = plugin.contentPacks().saveEditorValue(
                    pack, file, "custom-items." + id, item, snapshot.revision());
                return json(result.success() ? 200 : 409, result);
            }
            if ("/save".equals(path) && "POST".equalsIgnoreCase(request.method())) {
                if (!context.writable()) return textResponse(403, "This link is read-only.");
                Map<?, ?> body = EDITOR_GSON.fromJson(request.bodyAsString(), Map.class);
                String id = body.get("id") == null ? "" : String.valueOf(body.get("id"));
                String revision = body.get("revision") == null ? "" : String.valueOf(body.get("revision"));
                Map<String, Object> fields = body.get("fields") instanceof Map<?, ?> raw ? stringMap(raw) : Map.of();
                WebItemSaveResult result = saveWebItem(id, revision, fields);
                return json(result.success() ? 200 : 409, result);
            }
            return textResponse(405, "Method not allowed.");
        } catch (IllegalArgumentException exception) {
            return textResponse(400, exception.getMessage() == null ? "Invalid item details." : exception.getMessage());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[item-editor] Web editor request failed: " + exception.getMessage());
            return textResponse(500, "The item editor could not complete that action.");
        }
    }

    private WebItemSaveResult saveWebItem(String itemId, String expectedRevision, Map<String, Object> fields) {
        String id = normalizeItemId(itemId);
        Located located = locate(id);
        if (located == null) return new WebItemSaveResult(false, "Item not found.", null);
        String currentRevision = String.valueOf(lastModified(located.file()));
        if (expectedRevision != null && !expectedRevision.isBlank() && !expectedRevision.equals(currentRevision))
            return new WebItemSaveResult(false, "This item changed elsewhere. Reload it before saving.", currentRevision);
        Map<String, Object> values = deepCopy(located.values());
        for (Map.Entry<String, Object> field : fields.entrySet()) applyWebField(values, field.getKey(), field.getValue(), located.packId());
        File parent = located.file().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            return new WebItemSaveResult(false, "Could not create the item file directory.", currentRevision);
        ConfigFile config = api.config().load(located.file());
        if (config == null) return new WebItemSaveResult(false, "Could not load the item file.", currentRevision);
        byte[] previous;
        boolean existed = located.file().isFile();
        try {
            previous = existed ? Files.readAllBytes(located.file().toPath()) : null;
        } catch (IOException exception) {
            return new WebItemSaveResult(false, "Could not read the item file before saving.", currentRevision);
        }
        config.getSection("custom-items").set(located.configKey(), values);
        config.save();
        boolean reloaded = "__root__".equals(located.packId())
            ? plugin.items() != null && plugin.items().reloadRootItems()
            : plugin.contentPacks() != null && plugin.contentPacks().reload(located.packId());
        if (!reloaded) {
            try {
                if (existed && previous != null) Files.write(located.file().toPath(), previous);
                else Files.deleteIfExists(located.file().toPath());
                config.reload();
                if ("__root__".equals(located.packId())) plugin.items().reloadRootItems();
                else if (plugin.contentPacks() != null) plugin.contentPacks().reload(located.packId());
            } catch (IOException restoreFailure) {
                plugin.getLogger().warning("[item-editor] Could not restore failed web save '" + located.file()
                    + "': " + restoreFailure.getMessage());
            }
            return new WebItemSaveResult(false, "Validation failed; the previous working item was restored.",
                String.valueOf(lastModified(located.file())));
        }
        return new WebItemSaveResult(true, "Saved " + id + " and reloaded it.", String.valueOf(lastModified(located.file())));
    }

    private void applyWebField(Map<String, Object> values, String field, Object rawValue, String packId) {
        String key = field == null ? "" : field.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        String value = rawValue == null ? "" : String.valueOf(rawValue);
        if ("abilities".equals(key) && rawValue instanceof Map<?, ?> map) {
            values.put("abilities", stringMap(map));
            return;
        }
        if (value.isBlank() && Set.of("color", "texture", "durability", "enchantments", "potion-effects").contains(key)) {
            if ("durability".equals(key)) values.remove("max-damage");
            else if ("potion-effects".equals(key)) values.remove("potion");
            else values.remove(key);
            return;
        }
        mutate(values, key, value, packId);
    }

    private static EditorWebResponse json(int status, Object value) {
        return new EditorWebResponse(status, "application/json; charset=utf-8", EDITOR_GSON.toJson(value));
    }

    private static EditorWebResponse textResponse(int status, String value) {
        return new EditorWebResponse(status, "text/plain; charset=utf-8", value == null ? "" : value);
    }

    private void openDialog(Player player, Draft draft) {
        List<String> textures = plugin.contentPacks() == null ? List.of()
            : plugin.contentPacks().texturePaths(draft.packId);
        String texturePicker = textures.isEmpty() ? "No PNG textures in this pack yet."
            : "Available PNG textures: " + String.join(", ", textures.stream().limit(40).toList())
                + (textures.size() > 40 ? " …" : "");
        var dialog = api.dialogs().create("item-editor:" + draft.itemId)
            .title(Component.text("Live item draft: " + draft.itemId))
            .body(Component.text("Changes apply to this preview immediately. Save publishes the YAML definition.\n"
                + texturePicker))
            .textInput("name", Component.text("On-screen item name"), text(draft.values.get("name")), 128)
            .multilineTextInput("lore", Component.text("Lore (one line per entry)"),
                String.join("\n", stringList(draft.values.get("lore"))), 1000, 4)
            .textInput("color", Component.text("Colour (#RRGGBB, if supported)"),
                text(draft.values.getOrDefault("color", "")), 16)
            .textInput("texture", Component.text("Texture path (PNG)"),
                text(draft.values.getOrDefault("texture", "")), 160)
            .textInput("attack-damage", Component.text("Attack damage"), text(nested(draft.values, "stats.attack-damage")), 16)
            .textInput("attack-speed", Component.text("Attack speed"), text(nested(draft.values, "stats.attack-speed")), 16)
            .textInput("armor", Component.text("Armour"), text(nested(draft.values, "stats.armor")), 16)
            .textInput("toughness", Component.text("Armour toughness"), text(nested(draft.values, "stats.toughness")), 16)
            .textInput("enchantments", Component.text("Enchantments (id=level,...)"), enchantments(draft.values), 500)
            .textInput("potion-effects", Component.text("Potion effects (type=seconds=level,...)"), potionEffects(draft.values), 500)
            .textInput("ability-add", Component.text("Add ability (trigger:action:value[:seconds[:level]])"), "", 240)
            .textInput("ability-edit", Component.text("Edit ability (trigger:index:field:value)"), "", 240)
            .textInput("ability-remove", Component.text("Remove ability/action (trigger[:index[:action]])"), "", 120)
            .submit(Component.text("Apply to live draft"), response -> applyDialog(player, response))
            .cancel(Component.text("Keep live draft open"), () -> showIndicator(player, draft));
        dialog.open(player);
    }

    private void applyDialog(Player player, DialogResponse response) {
        for (String field : List.of("name", "lore", "color", "texture", "attack-damage", "attack-speed",
            "armor", "toughness", "enchantments", "potion-effects", "ability-add", "ability-edit",
            "ability-remove")) {
            String value = response.text(field);
            if (!value.isBlank()) set(player, field, value);
        }
    }

    private void mutate(Map<String, Object> values, String field, String raw, String packId) {
        String key = field.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        switch (key) {
            case "name" -> values.put("name", raw);
            case "lore" -> values.put("lore", splitLines(raw));
            case "material" -> {
                Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
                if (material == null || !material.isItem()) throw new IllegalArgumentException("unknown item material");
                values.put("material", material.name());
            }
            case "color", "colour" -> {
                String value = raw.trim().replace("#", "");
                if (!value.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("colour must be #RRGGBB");
                values.put("color", value.toUpperCase(Locale.ROOT));
            }
            case "texture" -> {
                String value = raw.trim();
                if (value.isBlank()) { values.remove("texture"); break; }
                String path = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
                path = path.replace('\\', '/');
                if (path.startsWith("textures/")) path = path.substring("textures/".length());
                String requestedPath = path;
                String selected = plugin.contentPacks() == null ? null : plugin.contentPacks().texturePaths(packId)
                    .stream()
                    .filter(candidate -> candidate.equalsIgnoreCase(requestedPath)
                        || candidate.replaceFirst("(?i)\\.png$", "").equalsIgnoreCase(
                            requestedPath.replaceFirst("(?i)\\.png$", "")))
                    .findFirst().orElse(null);
                if (selected == null)
                    throw new IllegalArgumentException("texture PNG was not found in pack " + packId);
                values.put("texture", selected.replaceFirst("(?i)\\.png$", ""));
                if (plugin.contentPacks() != null) plugin.contentPacks().markResourcePackChange(packId);
            }
            case "durability", "max-damage" -> values.put("max-damage", positiveInt(raw, "durability"));
            case "attack-damage", "attack-speed", "armor", "toughness" -> setNested(values, "stats." + key, positiveDouble(raw, key));
            case "enchantments" -> values.put("enchantments", parseEnchantments(raw));
            case "potion-effects", "effects" -> setPotionEffects(values, raw);
            case "ability", "ability-add" -> addSimpleAbility(values, raw);
            case "ability-edit" -> editAbility(values, raw);
            case "ability-remove" -> removeAbility(values, raw);
            default -> throw new IllegalArgumentException("unsupported field: " + field);
        }
    }

    private void setPotionEffects(Map<String, Object> values, String raw) {
        List<Map<String, Object>> effects = new ArrayList<>();
        for (String part : raw.split(",")) {
            String[] pieces = part.trim().split("=");
            if (pieces.length < 2) throw new IllegalArgumentException("effects use type=seconds=level");
            String type = pieces[0].trim().toLowerCase(Locale.ROOT);
            if (Registry.MOB_EFFECT.get(NamespacedKey.minecraft(type)) == null)
                throw new IllegalArgumentException("unknown potion effect: " + type);
            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("type", type);
            effect.put("duration-seconds", positiveInt(pieces[1], "duration"));
            effect.put("level", pieces.length > 2 ? positiveInt(pieces[2], "level") : 1);
            effects.add(effect);
        }
        Map<String, Object> potion = map(values.get("potion"));
        potion.put("effects", effects);
        values.put("potion", potion);
    }

    private void addSimpleAbility(Map<String, Object> values, String raw) {
        String[] pieces = raw.split(":");
        if (pieces.length < 3) throw new IllegalArgumentException("ability uses trigger:action:effect[:seconds[:level]]");
        String trigger = pieces[0].trim().toLowerCase(Locale.ROOT).replace('_', '-');
        String action = pieces[1].trim().toLowerCase(Locale.ROOT).replace('_', '-');
        Map<String, Object> actionMap = new LinkedHashMap<>();
        actionMap.put("type", action);
        switch (action) {
            case "effect" -> {
                actionMap.put("effect", pieces[2].trim());
                if (pieces.length > 3) actionMap.put("duration-seconds", positiveInt(pieces[3], "duration"));
                if (pieces.length > 4) actionMap.put("level", positiveInt(pieces[4], "level"));
            }
            case "particles" -> {
                actionMap.put("particle", pieces[2].trim());
                if (pieces.length > 3) actionMap.put("count", positiveInt(pieces[3], "particle count"));
            }
            case "plant-flower" -> actionMap.put("flower", pieces[2].trim());
            case "sound" -> actionMap.put("sound", pieces[2].trim());
            case "title" -> {
                actionMap.put("title", pieces[2].trim());
                if (pieces.length > 3) actionMap.put("subtitle", pieces[3].trim());
            }
            default -> throw new IllegalArgumentException("unsupported ability action: " + action);
        }
        Map<String, Object> ability = new LinkedHashMap<>();
        ability.put("actions", List.of(actionMap));
        Map<String, Object> abilities = map(values.get("abilities"));
        List<Object> entries = new ArrayList<>();
        Object existing = abilities.get(trigger);
        if (existing instanceof List<?> list) entries.addAll(list);
        else if (existing instanceof Map<?, ?> map) entries.add(stringMap(map));
        entries.add(ability);
        abilities.put(trigger, entries);
        values.put("abilities", abilities);
    }

    private void removeAbility(Map<String, Object> values, String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (value.isBlank()) throw new IllegalArgumentException("ability trigger is required");
        Map<String, Object> abilities = map(values.get("abilities"));
        String[] parts = value.split(":", 3);
        String trigger = parts[0];
        Object existing = abilities.get(trigger);
        if (existing == null) return;
        if (parts.length == 1) {
            abilities.remove(trigger);
        } else if (existing instanceof List<?> list) {
            int index;
            try { index = Integer.parseInt(parts[1]); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("ability index must be a number"); }
            List<Object> remaining = new ArrayList<>(list);
            if (index < 0 || index >= remaining.size()) throw new IllegalArgumentException("ability index is out of range");
            if (parts.length == 2) {
                remaining.remove(index);
            } else {
                if (!(remaining.get(index) instanceof Map<?, ?> rawAbility))
                    throw new IllegalArgumentException("ability entry must be a mapping");
                Map<String, Object> ability = stringMap(rawAbility);
                List<Object> actions = new ArrayList<>();
                Object rawActions = ability.get("actions");
                if (rawActions instanceof List<?> listActions) actions.addAll(listActions);
                int actionIndex;
                try { actionIndex = Integer.parseInt(parts[2]); }
                catch (NumberFormatException exception) { throw new IllegalArgumentException("action index must be a number"); }
                if (actionIndex < 0 || actionIndex >= actions.size())
                    throw new IllegalArgumentException("action index is out of range");
                actions.remove(actionIndex);
                if (actions.isEmpty()) remaining.remove(index); else {
                    ability.put("actions", actions);
                    remaining.set(index, ability);
                }
            }
            if (remaining.isEmpty()) abilities.remove(trigger); else abilities.put(trigger, remaining);
        } else {
            abilities.remove(trigger);
        }
        values.put("abilities", abilities);
    }

    private void editAbility(Map<String, Object> values, String raw) {
        String[] pieces = raw.trim().split(":", 5);
        if (pieces.length < 4) throw new IllegalArgumentException(
            "ability edit uses trigger:index:field:value or trigger:index:action:field:value");
        String trigger = pieces[0].trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int abilityIndex = parseIndex(pieces[1], "ability");
        Map<String, Object> abilities = map(values.get("abilities"));
        Object rawEntries = abilities.get(trigger);
        if (!(rawEntries instanceof List<?> list) || abilityIndex < 0 || abilityIndex >= list.size())
            throw new IllegalArgumentException("ability index is out of range");
        if (!(list.get(abilityIndex) instanceof Map<?, ?> rawAbility))
            throw new IllegalArgumentException("ability entry must be a mapping");
        Map<String, Object> ability = stringMap(rawAbility);
        int actionIndex = pieces.length == 4 ? 0 : parseIndex(pieces[2], "action");
        String field = pieces.length == 4 ? pieces[2].trim().toLowerCase(Locale.ROOT).replace('_', '-') : pieces[3]
            .trim().toLowerCase(Locale.ROOT).replace('_', '-');
        String newValue = pieces.length == 4 ? pieces[3].trim() : pieces[4].trim();
        if (Set.of("chance", "cooldown-seconds", "cooldown", "spacing", "slot", "target").contains(field)) {
            switch (field) {
                case "chance" -> {
                    double chance = positiveDouble(newValue, "ability chance");
                    if (chance > 1D) throw new IllegalArgumentException("ability chance must be between 0 and 1");
                    ability.put(field, chance);
                }
                case "cooldown", "cooldown-seconds", "spacing" -> ability.put(field,
                    positiveDouble(newValue, field));
                default -> ability.put(field, newValue.toLowerCase(Locale.ROOT));
            }
        } else {
            Object rawActions = ability.get("actions");
            if (!(rawActions instanceof List<?> listActions) || actionIndex < 0 || actionIndex >= listActions.size())
                throw new IllegalArgumentException("action index is out of range");
            if (!(listActions.get(actionIndex) instanceof Map<?, ?> rawAction))
                throw new IllegalArgumentException("action entry must be a mapping");
            Map<String, Object> actions = new LinkedHashMap<>(stringMap(rawAction));
            if (field.equals("type")) {
                String type = newValue.toLowerCase(Locale.ROOT).replace('_', '-');
                if (!Set.of("effect", "particles", "title", "sound", "plant-flower").contains(type))
                    throw new IllegalArgumentException("unsupported ability action: " + newValue);
                actions.put(field, type);
            } else if (Set.of("duration-seconds", "level", "count", "fade-in", "stay", "fade-out").contains(field)) {
                actions.put(field, positiveInt(newValue, field));
            } else {
                actions.put(field, newValue);
            }
            List<Object> updatedActions = new ArrayList<>(listActions);
            updatedActions.set(actionIndex, actions);
            ability.put("actions", updatedActions);
        }
        List<Object> updated = new ArrayList<>(list);
        updated.set(abilityIndex, ability);
        abilities.put(trigger, updated);
        values.put("abilities", abilities);
    }

    private static int parseIndex(String value, String label) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(label + " index must be a number"); }
    }

    private void preview(Player player, Draft draft) {
        int amount = 1;
        ItemStack held = player.getInventory().getItemInMainHand();
        String heldId = api.items().getCustomItemId(held);
        boolean ownsHeld = hasPreviewToken(held, draft.token)
            || heldId != null && draft.itemId.equalsIgnoreCase(normalizeItemId(heldId));
        if (ownsHeld || held == null || held.getType().isAir()) {
            if (ownsHeld && draft.originalHeld == null) draft.originalHeld = held.clone();
            if (ownsHeld) amount = held.getAmount();
            ItemStack updated = buildPreview(player, draft, amount, ownsHeld ? held : null);
            if (updated != null) player.getInventory().setItemInMainHand(updated);
        }
        replacePreviewCopies(draft, player);
        api.itemAbilities().refresh(player);
    }

    private void syncPreviewAbilities(Draft draft) {
        api.itemAbilities().registerPreview(draft.token, draft.itemId, deepCopy(draft.values));
    }

    private ItemStack buildPreview(Player player, Draft draft, int amount, @Nullable ItemStack existing) {
        Material material = Material.matchMaterial(text(draft.values.getOrDefault("material", "PAPER")).toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) return null;
        ItemStack stack = existing == null || existing.getType() != material ? new ItemStack(material, amount) : existing.clone();
        stack.setAmount(Math.clamp(amount, 1, stack.getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        String name = text(draft.values.get("name"));
        meta.displayName(name.isBlank() ? null : TextUtil.colourise(name)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<String> lore = stringList(draft.values.get("lore"));
        meta.lore(lore.isEmpty() ? null : lore.stream().map(TextUtil::colourise).toList());
        if (draft.values.containsKey("max-damage") && meta instanceof Damageable damageable) {
            int max = positiveInt(text(draft.values.get("max-damage")), "durability");
            damageable.setMaxDamage(max);
            damageable.setDamage(Math.min(damageable.getDamage(), max));
        }
        applyColour(meta, draft.values.get("color"));
        applyStats(meta, draft.values, draft.itemId);
        applyEnchantments(meta, draft.values.get("enchantments"));
        applyPotion(meta, draft.values.get("potion"));
        stack.setItemMeta(meta);
        api.items().addAttrib(stack, "custom-item-id", draft.itemId);
        api.items().addAttrib(stack, "workshop-preview-token", draft.token);
        applyPreviewVisual(stack, draft);
        return stack;
    }

    @SuppressWarnings("UnstableApiUsage")
    private void applyPreviewVisual(ItemStack stack, Draft draft) {
        CustomItemDefinition definition = api.items().customItemDefinition(draft.itemId);
        if (definition == null || definition.clients() == null || definition.clients().java() == null) return;
        var java = definition.clients().java();
        NamespacedKey itemModel = NamespacedKey.fromString(java.itemModelId());
        ItemMeta meta = stack.getItemMeta();
        if (itemModel == null || meta == null) return;
        meta.setItemModel(itemModel);
        CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
        modelData.setFloats(List.of((float) java.customModelData()));
        meta.setCustomModelDataComponent(modelData);
        stack.setItemMeta(meta);
    }

    private void replacePreviewCopies(Draft draft, Player editor) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (!hasPreviewToken(item, draft.token)) continue;
                ItemStack updated = buildPreview(editor, draft, item.getAmount(), item);
                if (updated != null) player.getInventory().setItem(slot, updated);
            }
            ItemStack[] armor = player.getInventory().getArmorContents();
            for (int slot = 0; slot < armor.length; slot++) {
                if (!hasPreviewToken(armor[slot], draft.token)) continue;
                ItemStack updated = buildPreview(editor, draft, armor[slot].getAmount(), armor[slot]);
                if (updated != null) armor[slot] = updated;
            }
            player.getInventory().setArmorContents(armor);
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (hasPreviewToken(offHand, draft.token)) {
                ItemStack updated = buildPreview(editor, draft, offHand.getAmount(), offHand);
                if (updated != null) player.getInventory().setItemInOffHand(updated);
            }
            api.itemAbilities().refresh(player);
        }
    }

    private boolean hasPreviewToken(ItemStack item, String token) {
        return item != null && token.equals(api.items().getAttrib(item, "workshop-preview-token", String.class, ""));
    }

    private Located locate(String itemId) {
        ConfigFile root = api.config().load("config.yml");
        if (root != null) {
            Map<String, Object> items = root.getMap("custom-items", true);
            for (Map.Entry<String, Object> entry : items.entrySet()) {
                if (!normalizeItemId(entry.getKey()).equals(itemId)) continue;
                if (entry.getValue() instanceof Map<?, ?> map) {
                    return new Located("__root__", entry.getKey(), new File(plugin.getDataFolder(), "config.yml"),
                        stringMap(map));
                }
            }
        }
        if (plugin.contentPacks() == null) return null;
        for (String packId : plugin.contentPacks().packs().stream()
            .filter(info -> info.enabled())
            .map(info -> info.id()).toList()) {
            for (ContentPackDocument document : plugin.contentPacks().documents(packId)) {
                Map<String, Object> items = document.config().getMap("custom-items", true);
                for (Map.Entry<String, Object> entry : items.entrySet()) {
                    if (!normalizeItemId(entry.getKey()).equals(itemId)) continue;
                    if (entry.getValue() instanceof Map<?, ?> map) {
                        return new Located(packId, entry.getKey(), document.file(), stringMap(map));
                    }
                }
            }
        }
        return null;
    }

    private void showIndicator(Player player, Draft draft) {
        player.sendActionBar(Component.text("Live draft: " + draft.itemId + "  •  /itemedit save to publish"));
    }

    private void expireDrafts() {
        long cutoff = System.currentTimeMillis() - DRAFT_TIMEOUT_MILLIS;
        for (var iterator = drafts.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Draft draft = entry.getValue();
            if (draft.lastActivity >= cutoff) continue;
            api.itemAbilities().unregisterPreview(draft.token);
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.sendActionBar(Component.text("Live draft expired: " + draft.itemId));
            iterator.remove();
        }
    }

    private static void touch(Draft draft) {
        draft.lastActivity = System.currentTimeMillis();
    }

    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? stringMap(raw) : new LinkedHashMap<>();
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) if (entry.getKey() != null)
            result.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
        return result;
    }

    private static Map<String, Object> deepCopy(Map<String, Object> value) {
        return stringMap(value);
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) return stringMap(map);
        if (value instanceof List<?> list) return list.stream().map(ItemEditorServiceImpl::deepCopyValue).toList();
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void setNested(Map<String, Object> values, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = values;
        for (int index = 0; index < parts.length - 1; index++) {
            Object existing = current.get(parts[index]);
            Map<String, Object> next = existing instanceof Map<?, ?> map ? stringMap(map) : new LinkedHashMap<>();
            current.put(parts[index], next);
            current = next;
        }
        current.put(parts[parts.length - 1], value);
    }

    private static Object nested(Map<String, Object> values, String path) {
        Object current = values;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return "";
            current = map.get(part);
        }
        return current == null ? "" : current;
    }

    private static List<String> splitLines(String value) {
        return List.of(value.replace('|', '\n').split("\\R", -1)).stream().filter(line -> !line.isBlank()).toList();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return value == null ? List.of() : List.of(String.valueOf(value));
        return list.stream().map(String::valueOf).toList();
    }

    private static Map<String, Integer> parseEnchantments(String raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String entry : raw.split(",")) {
            String[] parts = entry.trim().split("=");
            if (parts.length != 2 || RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT))) == null)
                throw new IllegalArgumentException("enchantments use id=level");
            result.put(parts[0].trim().toLowerCase(Locale.ROOT), positiveInt(parts[1], "enchantment level"));
        }
        return result;
    }

    private static String enchantments(Map<String, Object> values) {
        Map<String, Object> map = map(values.get("enchantments"));
        return map.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static String potionEffects(Map<String, Object> values) {
        Object raw = map(values.get("potion")).get("effects");
        if (!(raw instanceof List<?> list)) return "";
        return list.stream().filter(value -> value instanceof Map<?, ?>).map(value -> {
            Map<String, Object> effect = stringMap((Map<?, ?>) value);
            return text(effect.get("type")) + "=" + text(effect.getOrDefault("duration-seconds", 1))
                + "=" + text(effect.getOrDefault("level", 1));
        }).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static void applyColour(ItemMeta meta, Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return;
        Color color = Color.fromRGB(Integer.parseInt(String.valueOf(raw).replace("#", ""), 16));
        if (meta instanceof LeatherArmorMeta leather) leather.setColor(color);
        if (meta instanceof PotionMeta potion) potion.setColor(color);
    }

    private void applyStats(ItemMeta meta, Map<String, Object> values, String id) {
        Map<String, Object> stats = map(values.get("stats"));
        Material material = Material.matchMaterial(text(values.getOrDefault("material", "PAPER")).toUpperCase(Locale.ROOT));
        applyStat(meta, Attribute.ATTACK_DAMAGE, stats, "attack-damage", id, material, EquipmentSlotGroup.MAINHAND);
        applyStat(meta, Attribute.ATTACK_SPEED, stats, "attack-speed", id, material, EquipmentSlotGroup.MAINHAND);
        applyStat(meta, Attribute.ARMOR, stats, "armor", id, material,
            ItemServiceImpl.equipmentGroup(material));
        applyStat(meta, Attribute.ARMOR_TOUGHNESS, stats, "toughness", id, material,
            ItemServiceImpl.equipmentGroup(material));
    }

    private void applyStat(ItemMeta meta, Attribute attribute, Map<String, Object> stats, String key, String id,
                           Material material, EquipmentSlotGroup slot) {
        if (!stats.containsKey(key)) return;
        NamespacedKey modifierKey = new NamespacedKey(plugin, "workshop_" + normalizeItemId(id) + "_" + key);
        var existing = meta.getAttributeModifiers(attribute);
        if (existing != null) existing.stream().filter(modifier -> modifierKey.equals(modifier.getKey()))
            .forEach(modifier -> meta.removeAttributeModifier(attribute, modifier));
        double total = number(stats.get(key), 0D);
        meta.addAttributeModifier(attribute, new AttributeModifier(modifierKey,
            ItemServiceImpl.configuredModifierValue(material, attribute, total),
            AttributeModifier.Operation.ADD_NUMBER, slot));
    }

    private static void applyEnchantments(ItemMeta meta, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            NamespacedKey key = NamespacedKey.minecraft(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT));
            var enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
            if (enchantment != null) meta.addEnchant(enchantment, Math.max(1, integer(entry.getValue(), 1)), true);
        }
    }

    private void applyPotion(ItemMeta meta, Object raw) {
        if (raw instanceof String reference && plugin.items() != null) {
            plugin.items().applyPotionDefinition(meta, reference);
            return;
        }
        if (!(meta instanceof PotionMeta potion) || !(raw instanceof Map<?, ?> potionMap)) return;
        Object effects = potionMap.get("effects");
        if (!(effects instanceof List<?> list)) return;
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> map)) continue;
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(text(map.get("type")).toLowerCase(Locale.ROOT)));
            if (type == null) continue;
            int seconds = Math.max(1, integer(map.get("duration-seconds"), 1));
            int level = Math.max(1, integer(map.get("level"), 1));
            potion.addCustomEffect(new PotionEffect(type, seconds * 20, level - 1), true);
        }
    }

    private static String normalizeItemId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("item ID must not be blank");
        String value = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String requiredText(Object value, String message) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) throw new IllegalArgumentException(message);
        return text;
    }

    private static int positiveInt(String raw, String label) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a positive whole number");
        }
    }

    private static double positiveDouble(String raw, String label) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value < 0D) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a non-negative number");
        }
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }

    private static long lastModified(File file) {
        try {
            if (!file.isFile()) return -1L;
            long modified = Files.getLastModifiedTime(file.toPath()).toMillis();
            long size = Files.size(file.toPath());
            return 31L * (31L * modified + size) + Arrays.hashCode(Files.readAllBytes(file.toPath()));
        }
        catch (IOException exception) { return -2L; }
    }

    private record Located(String packId, String configKey, File file, Map<String, Object> values) { }

    private record WebItemSaveResult(boolean success, String message, String revision) { }

    private static final class Draft {
        private final String itemId;
        private final String configKey;
        private final String packId;
        private final File file;
        private Map<String, Object> values;
        private final Deque<Map<String, Object>> undo = new ArrayDeque<>();
        private final Deque<Map<String, Object>> redo = new ArrayDeque<>();
        private long baseModified;
        private final String token;
        private ItemStack originalHeld;
        private long lastActivity = System.currentTimeMillis();

        private Draft(String itemId, String configKey, String packId, File file, Map<String, Object> values,
                      long baseModified, String token) {
            this.itemId = itemId;
            this.configKey = configKey;
            this.packId = packId;
            this.file = file;
            this.values = values;
            this.baseModified = baseModified;
            this.token = token;
        }
    }
}

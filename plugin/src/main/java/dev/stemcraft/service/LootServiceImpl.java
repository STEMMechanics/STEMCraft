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
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.condition.ConditionContext;
import dev.stemcraft.api.service.editor.EditorWebContext;
import dev.stemcraft.api.service.editor.EditorWebModule;
import dev.stemcraft.api.service.editor.EditorWebResponse;
import dev.stemcraft.api.service.loot.LootService;
import dev.stemcraft.api.service.web.WebServiceRequest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockState;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * General loot evaluation service.  The first consumer is MOB_DEATH; the
 * evaluator deliberately has no dependency on a particular mob or item.
 *
 * <p>Chance and quantity are evaluated once per entry per event.  A removal
 * quantity is then consumed across matching stacks in list order.  A failed
 * REPLACE roll never removes its source stack.</p>
 */
public final class LootServiceImpl extends BaseService implements LootService {
    private static final String MOB_DEATH = "mob-death";
    private static final String BLOCK_BREAK = "block-break";
    private static final String SPAWN_REASON = "workshop_spawn_reason";
    private static final String SPAWN_BIOME = "workshop_spawn_biome";
    private static final Gson EDITOR_GSON = new GsonBuilder().serializeNulls().create();

    private final Map<String, LootTable> tables = new LinkedHashMap<>();
    private final Map<String, Set<String>> ownedTables = new HashMap<>();
    private NamespacedKey spawnReasonKey;
    private NamespacedKey spawnBiomeKey;

    public LootServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "loot");
    }

    @Override
    public void onEnable() {
        spawnReasonKey = new NamespacedKey(plugin, SPAWN_REASON);
        spawnBiomeKey = new NamespacedKey(plugin, SPAWN_BIOME);
        if (plugin.contentPacks() != null) {
            plugin.contentPacks().registerHandler("loot", this::reloadContentPack, this::unloadContentPack,
                this::validateContentPack);
        }
        if (api.editorWeb() != null) api.editorWeb().registerModule(new EditorWebModule() {
            @Override public @NotNull String id() { return "loot"; }
            @Override public @NotNull String title() { return "Loot"; }
            @Override public @NotNull String icon() { return "L"; }
            @Override public @NotNull String description() { return "Define drops for mobs and block breaks."; }
            @Override public @NotNull String readPermission() { return "stemcraft.command.stemcraft.loot"; }
            @Override public @NotNull String writePermission() { return "stemcraft.command.stemcraft.loot"; }
            @Override public @NotNull EditorWebResponse handle(@NotNull EditorWebContext context, @NotNull String path,
                                                                 @NotNull WebServiceRequest request) {
                return handleEditorRequest(context, path, request);
            }
        });
        api.events().register(CreatureSpawnEvent.class, this::recordSpawnContext, EventPriority.MONITOR, false);
        // Apply before feature/minigame listeners so their existing clear/restrict
        // rules can remove workshop drops in the same way they remove vanilla drops.
        api.events().register(EntityDeathEvent.class, this::handleDeath, EventPriority.LOWEST, true);
        api.events().register(BlockDropItemEvent.class, this::handleBlockDrops, EventPriority.HIGHEST, true);
        reloadAll();
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadAll();
        if (plugin.contentPacks() != null) plugin.contentPacks().activateEnabledPacks();
    }

    @Override
    public void onDisable() {
        tables.clear();
        ownedTables.clear();
    }

    /** Reconcile one pack without disturbing tables owned by other packs. */
    public boolean reloadContentPack(@NotNull String packId) {
        unloadContentPack(packId);
        if (plugin.contentPacks() == null) return true;
        var info = plugin.contentPacks().find(packId);
        if (info == null || !info.enabled()) return true;

        List<LootTable> loaded = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (ContentPackDocument document : plugin.contentPacks().documents(packId)) {
            loaded.addAll(parseDocument(document, errors));
        }
        if (!errors.isEmpty()) {
            plugin.getLogger().warning("[loot] Refusing to activate '" + packId + "': " + String.join(" | ", errors));
            return false;
        }
        Set<String> ids = ConcurrentHashMap.newKeySet();
        for (LootTable table : loaded) {
            if (!ids.add(table.id()) || tables.containsKey(table.id())) {
                plugin.getLogger().warning("[loot] Duplicate table ID '" + table.id() + "' in pack '" + packId + "'.");
                return false;
            }
        }
        for (LootTable table : loaded) tables.put(table.id(), table);
        ownedTables.put(normalize(packId), ids);
        return true;
    }

    public void unloadContentPack(@NotNull String packId) {
        Set<String> ids = ownedTables.remove(normalize(packId));
        if (ids != null) ids.forEach(tables::remove);
    }

    /** Validate table syntax without mutating the active table registry. */
    public List<String> validateContentPack(@NotNull String packId) {
        if (plugin.contentPacks() == null) return List.of();
        List<String> errors = new ArrayList<>();
        for (ContentPackDocument document : plugin.contentPacks().documents(packId)) {
            parseDocument(document, errors);
        }
        return List.copyOf(errors);
    }

    public void reloadAll() {
        tables.clear();
        ownedTables.clear();
    }

    @Override
    public @NotNull List<String> tableIds() {
        return tables.keySet().stream().sorted().toList();
    }

    @Override
    public @NotNull List<LootDrop> simulate(@NotNull String tableId, int rolls, @NotNull LootContext context,
                                            @NotNull Random random) {
        LootTable table = tables.get(normalize(tableId));
        if (table == null || rolls <= 0) return List.of();
        int boundedRolls = Math.min(100_000, rolls);
        Map<String, Integer> totals = new HashMap<>();
        for (int roll = 0; roll < boundedRolls; roll++) {
            List<ItemStack> drops = initialDrops(context);
            evaluate(table, context, random, drops);
            for (ItemStack drop : drops) {
                if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
                totals.merge(canonical(drop), drop.getAmount(), Integer::sum);
            }
        }
        return totals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new LootDrop(entry.getKey(), entry.getValue()))
            .toList();
    }

    private EditorWebResponse handleEditorRequest(EditorWebContext context, String path, WebServiceRequest request) {
        ContentPackServiceImpl packs = plugin.contentPacks();
        try {
            if ("/data".equals(path) && "GET".equalsIgnoreCase(request.method())) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("kind", "loot");
                data.put("tables", tableIds());
                List<Map<String, Object>> documents = new ArrayList<>();
                List<Map<String, Object>> definitions = new ArrayList<>();
                List<Map<String, Object>> packOptions = new ArrayList<>();
                if (packs != null) {
                    for (var info : packs.packs()) {
                        packOptions.add(Map.of("id", info.id(), "enabled", info.enabled(), "valid", info.valid()));
                        for (ContentPackDocument document : packs.documents(info.id())) {
                            String file = document.packDirectory().toPath().relativize(document.file().toPath()).toString()
                                .replace(java.io.File.separatorChar, '/');
                            Map<String, Object> rawTables = document.config().getMap("loot.tables", true);
                            if (!rawTables.isEmpty()) {
                                documents.add(Map.of("pack", info.id(), "file", file,
                                    "label", info.id() + " / " + file));
                            }
                            for (Map.Entry<String, Object> table : rawTables.entrySet()) {
                                if (!(table.getValue() instanceof Map<?, ?> rawTable)) continue;
                                Map<String, Object> definition = new LinkedHashMap<>();
                                definition.put("id", table.getKey());
                                definition.put("pack", info.id());
                                definition.put("file", file);
                                definition.put("revision", packs.editorDocument(info.id(), file).revision());
                                definition.put("table", copyConfigValue(rawTable));
                                definitions.add(definition);
                            }
                        }
                    }
                }
                data.put("documents", documents);
                data.put("definitions", definitions);
                data.put("packs", packOptions);
                data.put("items", api.items().customItemDefinitions().stream()
                    .map(definition -> definition.id()).sorted(String.CASE_INSENSITIVE_ORDER).toList());
                data.put("blocks", java.util.Arrays.stream(Material.values())
                    .filter(material -> material.isBlock() && !material.isAir())
                    .map(Material::name).sorted().toList());
                return json(200, data);
            }
            if ("/create".equals(path) && "POST".equalsIgnoreCase(request.method())) {
                if (!context.writable()) return textResponse(403, "This link is read-only.");
                if (packs == null) return textResponse(503, "Content packs are unavailable.");
                Map<?, ?> body = EDITOR_GSON.fromJson(request.bodyAsString(), Map.class);
                String pack = requiredText(body.get("pack"), "Choose a content pack.");
                String file = text(body.get("file"));
                if (file == null || file.isBlank()) file = "configs/loot.yml";
                if (!file.contains("/")) file = "configs/" + file;
                String tableId = normalize(requiredText(body.get("table"), "Enter a table ID."));
                if (!tableId.matches("[a-z0-9][a-z0-9-]*")) return textResponse(400, "Table IDs must use lowercase letters, numbers, and hyphens.");
                String target = normalize(requiredText(body.get("target"), "Choose what causes the drop."));
                if (!Set.of(MOB_DEATH, BLOCK_BREAK).contains(target)) return textResponse(400, "Choose mob-death or block-break.");
                String item = requiredText(body.get("item"), "Enter an item or material to drop.");
                double chance = number(body.get("chance"), 1D);
                int minimum = integer(body.get("min"), 1);
                int maximum = integer(body.get("max"), minimum);
                if (!Double.isFinite(chance) || chance < 0D || chance > 1D || minimum < 1 || maximum < minimum)
                    return textResponse(400, "Use a chance from 0 to 1 and a valid amount range.");

                ContentPackServiceImpl.DocumentSnapshot snapshot = packs.editorDocument(pack, file);
                if (snapshot == null) {
                    snapshot = packs.createEditorDocument(pack, file, "loot:\n  tables: {}\n");
                    if (snapshot == null) return textResponse(400, "The loot file could not be created in that pack.");
                }
                Object existing = packs.editorValue(pack, file, "loot.tables." + tableId);
                Map<String, Object> table = existing instanceof Map<?, ?> raw
                    ? copyMap(raw) : new LinkedHashMap<>();
                String existingTarget = normalize(text(table.get("target")));
                if (!existingTarget.isBlank() && !existingTarget.equals(target))
                    return textResponse(409, "That table already belongs to a different trigger.");
                table.put("target", target.toUpperCase(Locale.ROOT).replace('-', '_'));
                List<Object> rules = new ArrayList<>();
                if (table.get("rules") instanceof Collection<?> rawRules)
                    rawRules.forEach(value -> rules.add(copyConfigValue(value)));
                int priority = rules.stream().mapToInt(value -> value instanceof Map<?, ?> rule
                    ? integer(rule.get("priority"), 0) : 0).max().orElse(0) + 10;
                Map<String, Object> conditions = new LinkedHashMap<>();
                if (target.equals(BLOCK_BREAK)) {
                    List<String> blocks = splitValues(text(body.get("block")));
                    if (blocks.isEmpty()) return textResponse(400, "Enter at least one block type.");
                    conditions.put("block", blocks.size() == 1 ? blocks.getFirst() : blocks);
                } else {
                    String entity = text(body.get("entity"));
                    if (entity != null && !entity.isBlank()) conditions.put("entity-type", entity.toUpperCase(Locale.ROOT));
                }
                String world = text(body.get("world"));
                if (world != null && !world.isBlank()) conditions.put("world", world);
                String biome = text(body.get("biome"));
                if (biome != null && !biome.isBlank()) conditions.put("biome", biome);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("item", item);
                entry.put("chance", chance);
                entry.put("min", minimum);
                entry.put("max", maximum);
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("priority", priority);
                rule.put("operation", "APPEND");
                rule.put("conditions", conditions);
                rule.put("entries", List.of(entry));
                rules.add(rule);
                table.put("rules", rules);
                ContentPackServiceImpl.DocumentSaveResult result = packs.saveEditorValue(
                    pack, file, "loot.tables." + tableId, table, snapshot.revision());
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("success", result.success());
                response.put("message", result.message());
                response.put("revision", result.revision());
                response.put("table", tableId);
                return json(result.success() ? 200 : 409, response);
            }
            if ("/document".equals(path) && packs != null) {
                String pack = request.queryParam("pack");
                String file = request.queryParam("file");
                if ("GET".equalsIgnoreCase(request.method())) {
                    ContentPackServiceImpl.DocumentSnapshot snapshot = packs.editorDocument(pack, file);
                    if (snapshot == null) return textResponse(404, "Document not found.");
                    return json(200, snapshot);
                }
                if ("PUT".equalsIgnoreCase(request.method())) {
                    if (!context.writable()) return textResponse(403, "This link is read-only.");
                    Map<?, ?> body = EDITOR_GSON.fromJson(request.bodyAsString(), Map.class);
                    String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
                    String revision = body.get("revision") == null ? "" : String.valueOf(body.get("revision"));
                    ContentPackServiceImpl.DocumentSaveResult result = packs.saveEditorDocument(pack, file, content, revision);
                    return json(result.success() ? 200 : 409, result);
                }
            }
            if ("/simulate".equals(path) && "POST".equalsIgnoreCase(request.method())) {
                Map<?, ?> body = EDITOR_GSON.fromJson(request.bodyAsString(), Map.class);
                String table = body.get("table") == null ? "" : String.valueOf(body.get("table"));
                int rolls = Math.clamp(integer(body.get("rolls"), 10), 1, 100_000);
                EntityType entity = null;
                String entityName = body.get("entity") == null ? "" : String.valueOf(body.get("entity"));
                if (!entityName.isBlank()) {
                    try { entity = EntityType.valueOf(entityName.toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException ignored) { return textResponse(400, "Choose a valid creature type."); }
                }
                boolean playerKill = Boolean.parseBoolean(String.valueOf(body.get("playerKill")));
                String block = text(body.get("block"));
                LootContext simulation = new LootContext(
                    text(body.get("world")), entity, text(body.get("biome")), text(body.get("spawnReason")),
                    playerKill, Math.max(0, integer(body.get("tier"), 0)), Map.of(
                        "player-kill", playerKill,
                        "block", block == null ? "" : block.toUpperCase(Locale.ROOT)
                    ));
                List<Map<String, Object>> drops = simulate(table, rolls, simulation, new Random(0L)).stream()
                    .map(drop -> Map.<String, Object>of("item", drop.item(), "amount", drop.amount())).toList();
                return json(200, Map.of("table", table, "rolls", rolls, "drops", drops));
            }
            return textResponse(405, "Method not allowed.");
        } catch (RuntimeException exception) {
            return textResponse(400, exception.getMessage() == null
                ? "The loot editor could not complete that action." : exception.getMessage());
        }
    }

    private static EditorWebResponse json(int status, Object value) {
        return new EditorWebResponse(status, "application/json; charset=utf-8", EDITOR_GSON.toJson(value));
    }

    private static EditorWebResponse textResponse(int status, String value) {
        return new EditorWebResponse(status, "text/plain; charset=utf-8", value == null ? "" : value);
    }

    private void recordSpawnContext(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(spawnReasonKey, PersistentDataType.STRING, event.getSpawnReason().name());
        String biome = biome(entity.getLocation());
        if (!biome.isBlank()) data.set(spawnBiomeKey, PersistentDataType.STRING, biome);
    }

    private void handleDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || entity.hasMetadata("NPC")) return;
        NamespacedKey questNpcKey = new NamespacedKey(plugin, "quest-npc-profile");
        if (entity.getPersistentDataContainer().has(questNpcKey, PersistentDataType.STRING)) return;
        Player killer = entity.getKiller();
        Location location = entity.getLocation();
        String spawnReason = entity.getPersistentDataContainer().get(spawnReasonKey, PersistentDataType.STRING);
        String spawnedBiome = entity.getPersistentDataContainer().get(spawnBiomeKey, PersistentDataType.STRING);
        int distanceTier = api.distanceDifficulty().storedTier(entity);
        LootContext context = new LootContext(
            location.getWorld() == null ? null : location.getWorld().getName(),
            entity.getType(),
            biome(location),
            spawnReason,
            killer != null,
            distanceTier,
            Map.of(
                "player-kill", killer != null,
                "killer", killer == null ? "" : killer.getName(),
                "mob-spawn-biome", spawnedBiome == null ? "" : spawnedBiome,
                "killer-biome", killer == null ? "" : biome(killer.getLocation())
            )
        );
        for (LootTable table : tables.values()) {
            if (!MOB_DEATH.equals(table.target())) continue;
            evaluate(table, context, new Random(), event.getDrops());
        }
    }

    private void handleBlockDrops(BlockDropItemEvent event) {
        if (event.isCancelled()) return;
        BlockState state = event.getBlockState();
        String blockType = state.getType().name();
        String blockKey = state.getType().getKey().asString();
        Location location = state.getLocation();
        Player player = event.getPlayer();
        LootContext context = new LootContext(
            location.getWorld() == null ? null : location.getWorld().getName(),
            null,
            biome(location),
            null,
            false,
            api.distanceDifficulty().storedTier(player),
            Map.of(
                "player-kill", false,
                "block", blockType,
                "block-key", blockKey,
                "breaker", player.getName()
            )
        );
        for (LootTable table : tables.values()) {
            if (!BLOCK_BREAK.equals(table.target())) continue;
            List<ItemStack> extraDrops = new ArrayList<>();
            evaluate(table, context, new Random(), extraDrops);
            if (location.getWorld() == null) continue;
            for (ItemStack drop : extraDrops) {
                if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
                Item item = location.getWorld().dropItemNaturally(location.clone().add(0.5, 0.5, 0.5), drop);
                event.getItems().add(item);
            }
        }
    }

    private void evaluate(LootTable table, LootContext context, Random random, List<ItemStack> drops) {
        Map<String, Object> conditionValues = new LinkedHashMap<>(context.values());
        if (context.world() != null) conditionValues.putIfAbsent("world", context.world());
        for (LootRule rule : table.rules()) {
            String selectedBiome = switch (rule.biomeSource()) {
                case "mob-spawn" -> String.valueOf(context.values().getOrDefault("mob-spawn-biome", ""));
                case "killer" -> killerBiome(context);
                default -> context.biome();
            };
            ConditionContext conditions = new ConditionContext(
                null, null, null, null, selectedBiome,
                context.entityType() == null ? "" : context.entityType().name(),
                context.spawnReason(), context.distanceTier(), conditionValues);
            if (!api.conditions().matches(rule.conditions(), conditions)) continue;
            switch (rule.operation()) {
                case APPEND -> append(rule.entries(), random, drops);
                case REMOVE -> remove(rule, random, drops);
                case REPLACE -> replace(rule, random, drops, false);
                case REPLACE_ALL -> replace(rule, random, drops, true);
            }
        }
    }

    private static String killerBiome(LootContext context) {
        Object value = context.values().get("killer-biome");
        return value == null ? "" : String.valueOf(value);
    }

    private void append(List<LootEntry> entries, Random random, List<ItemStack> drops) {
        for (LootEntry entry : entries) {
            if (!roll(entry.chance(), random)) continue;
            ItemStack item = create(entry.item(), entry.quantity(random));
            if (item != null) drops.add(item);
        }
    }

    private void remove(LootRule rule, Random random, List<ItemStack> drops) {
        if (rule.match() == null || !roll(rule.chance(), random)) return;
        int remaining = rule.quantity(random);
        if (remaining <= 0) return;
        for (int index = 0; index < drops.size() && remaining > 0; index++) {
            ItemStack stack = drops.get(index);
            if (!sameItem(stack, rule.match())) continue;
            int removed = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - removed);
            remaining -= removed;
            if (stack.getAmount() <= 0) {
                drops.remove(index--);
            }
        }
    }

    private void replace(LootRule rule, Random random, List<ItemStack> drops, boolean all) {
        if (!roll(rule.chance(), random)) return;
        List<ItemStack> replacements = new ArrayList<>();
        for (LootEntry entry : rule.entries()) {
            if (!roll(entry.chance(), random)) continue;
            ItemStack item = create(entry.item(), entry.quantity(random));
            if (item != null) replacements.add(item);
        }
        // A failed replacement must leave the original drops untouched.
        if (replacements.isEmpty()) return;
        if (all) {
            drops.clear();
        } else if (rule.match() != null) {
            int remaining = rule.quantity(random);
            for (int index = 0; index < drops.size() && remaining > 0; index++) {
                ItemStack stack = drops.get(index);
                if (!sameItem(stack, rule.match())) continue;
                int removed = Math.min(remaining, stack.getAmount());
                stack.setAmount(stack.getAmount() - removed);
                remaining -= removed;
                if (stack.getAmount() <= 0) drops.remove(index--);
            }
        }
        drops.addAll(replacements);
    }

    private List<ItemStack> initialDrops(LootContext context) {
        Object configured = context.values().get("original-drops");
        if (!(configured instanceof Collection<?> values)) return new ArrayList<>();
        List<ItemStack> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof ItemStack stack) result.add(stack.clone());
            else if (value != null) {
                ItemStack item = create(String.valueOf(value), 1);
                if (item != null) result.add(item);
            }
        }
        return result;
    }

    private ItemStack create(String reference, int quantity) {
        if (reference == null || reference.isBlank() || quantity <= 0) return null;
        ItemStack custom = api.items().createCustomItem(reference, quantity);
        if (custom != null) return custom;
        String materialName = reference.contains(":") ? reference.substring(reference.indexOf(':') + 1) : reference;
        Material material = Material.matchMaterial(materialName.trim().toUpperCase(Locale.ROOT));
        return material == null ? null : new ItemStack(material, quantity);
    }

    private boolean sameItem(ItemStack stack, String reference) {
        if (stack == null || stack.getType().isAir()) return false;
        String expected = canonical(reference);
        return expected != null && expected.equalsIgnoreCase(canonical(stack));
    }

    private String canonical(String reference) {
        if (reference == null) return null;
        ItemStack custom = api.items().createCustomItem(reference, 1);
        if (custom != null) return canonical(custom);
        String value = reference.trim().toLowerCase(Locale.ROOT);
        if (!value.contains(":")) value = "minecraft:" + value;
        return value;
    }

    private String canonical(ItemStack stack) {
        String custom = api.items().getCustomItemId(stack);
        if (custom != null && !custom.isBlank()) return normalize(custom);
        return stack.getType().getKey().asString().toLowerCase(Locale.ROOT);
    }

    static boolean roll(double chance, Random random) {
        return chance >= 1D || chance > 0D && random.nextDouble() < chance;
    }

    static int inclusiveQuantity(Random random, int min, int max) {
        if (max < min) throw new IllegalArgumentException("max must be at least min");
        return min == max ? min : min + random.nextInt(max - min + 1);
    }

    private static String biome(Location location) {
        if (location == null || location.getWorld() == null) return "";
        Biome biome = location.getBlock().getBiome();
        return biome == null ? "" : biome.getKey().asString();
    }

    private List<LootTable> parseDocument(ContentPackDocument document, List<String> errors) {
        ConfigSection section = document.config();
        Map<String, Object> rawTables = section.getMap("loot.tables", false);
        if (rawTables.isEmpty()) return List.of();
        List<LootTable> result = new ArrayList<>();
        for (Map.Entry<String, Object> tableEntry : rawTables.entrySet()) {
            if (!(tableEntry.getValue() instanceof Map<?, ?> rawTable)) {
                errors.add(source(document, "loot.tables." + tableEntry.getKey()) + " must be a mapping.");
                continue;
            }
            String id = normalize(tableEntry.getKey());
            String target = normalize(value(rawTable, "target", MOB_DEATH));
            List<LootRule> rules = parseRules(document, id, rawTable.get("rules"), errors);
            result.add(new LootTable(id, target, rules));
        }
        return result;
    }

    private List<LootRule> parseRules(ContentPackDocument document, String tableId, Object raw, List<String> errors) {
        if (!(raw instanceof Collection<?> values)) return List.of();
        List<LootRule> result = new ArrayList<>();
        int order = 0;
        for (Object value : values) {
            String path = source(document, "loot.tables." + tableId + ".rules[" + order + "]");
            if (!(value instanceof Map<?, ?> map)) {
                errors.add(path + " must be a mapping.");
                order++;
                continue;
            }
            String operationName = normalize(value(map, "operation", "APPEND"));
            Operation operation;
            try {
                operation = Operation.valueOf(operationName.replace('-', '_'));
            } catch (IllegalArgumentException exception) {
                errors.add(path + ".operation must be APPEND, REMOVE, REPLACE, or REPLACE_ALL.");
                order++;
                continue;
            }
            Map<String, Object> conditions = stringMap(map.get("conditions"));
            String biomeSource = normalize(value(map, "biome-source", "mob-death"));
            double chance = number(map.get("chance"), 1D);
            if (chance < 0D || chance > 1D) errors.add(path + ".chance must be between 0 and 1.");
            String match = text(map.get("match"));
            List<LootEntry> entries = parseEntries(map, path, errors, chance);
            int min = integer(map.get("min"), integer(map.get("quantity"), 1));
            int max = integer(map.get("max"), integer(map.get("quantity"), min));
            if (max < min || min < 0) errors.add(path + " quantity range is invalid.");
            result.add(new LootRule(integer(map.get("priority"), 0), order++, conditions, biomeSource,
                operation, match, min, max, entries, chance));
        }
        result.sort(Comparator.comparingInt(LootRule::priority).thenComparingInt(LootRule::order));
        return List.copyOf(result);
    }

    private List<LootEntry> parseEntries(Map<?, ?> rule, String path, List<String> errors, double defaultChance) {
        Object rawEntries = rule.get("entries");
        Collection<?> values = rawEntries instanceof Collection<?> collection ? collection : List.of(rule);
        List<LootEntry> result = new ArrayList<>();
        for (Object raw : values) {
            if (!(raw instanceof Map<?, ?> entry)) {
                errors.add(path + ".entries must contain mappings.");
                continue;
            }
            String item = text(entry.get("item"));
            if (item == null || item.isBlank()) {
                errors.add(path + ".entries.item is required.");
                continue;
            }
            double chance = number(entry.get("chance"), defaultChance);
            int min = integer(entry.get("min"), integer(entry.get("amount"), 1));
            int max = integer(entry.get("max"), integer(entry.get("amount"), min));
            if (chance < 0D || chance > 1D || min < 0 || max < min) {
                errors.add(path + ".entries for '" + item + "' has invalid chance or quantity.");
                continue;
            }
            result.add(new LootEntry(item, chance, min, max));
        }
        return List.copyOf(result);
    }

    private String source(ContentPackDocument document, String path) {
        return document.packId() + "/" + document.file().getName() + ":" + path;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String requiredText(Object value, String message) {
        String text = text(value);
        if (text == null || text.isBlank()) throw new IllegalArgumentException(message);
        return text;
    }

    private static List<String> splitValues(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isBlank())
            .map(entry -> entry.contains(":") ? entry.substring(entry.indexOf(':') + 1) : entry)
            .map(entry -> entry.toUpperCase(Locale.ROOT))
            .distinct()
            .toList();
    }

    private static String value(Map<?, ?> map, String key, String fallback) {
        String value = text(map.get(key));
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) {
            try { return Double.parseDouble(String.valueOf(value).trim()); }
            catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try { return Integer.parseInt(String.valueOf(value).trim()); }
            catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    private static Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> copyMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), copyConfigValue(entry.getValue()));
        }
        return result;
    }

    private static Object copyConfigValue(Object value) {
        if (value instanceof Map<?, ?> map) return copyMap(map);
        if (value instanceof Collection<?> collection) return collection.stream().map(LootServiceImpl::copyConfigValue).toList();
        return value;
    }

    private enum Operation { APPEND, REMOVE, REPLACE, REPLACE_ALL }

    private record LootTable(String id, String target, List<LootRule> rules) { }

    private record LootRule(int priority, int order, Map<String, Object> conditions, String biomeSource,
                            Operation operation, String match, int min, int max, List<LootEntry> entries,
                            double chance) {
        private int quantity(Random random) {
            return inclusiveQuantity(random, min, max);
        }
    }

    private record LootEntry(String item, double chance, int min, int max) {
        private int quantity(Random random) {
            return inclusiveQuantity(random, min, max);
        }
    }
}

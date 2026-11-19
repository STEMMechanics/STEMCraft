/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.service;

import com.google.gson.Gson;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.editor.EditorWebContext;
import dev.stemcraft.api.service.editor.EditorWebModule;
import dev.stemcraft.api.service.editor.EditorWebResponse;
import dev.stemcraft.api.service.editor.WebView;
import dev.stemcraft.api.service.editor.WebViewBadge;
import dev.stemcraft.api.service.editor.WebViewButton;
import dev.stemcraft.api.service.editor.WebViewComponent;
import dev.stemcraft.api.service.editor.WebViewDialog;
import dev.stemcraft.api.service.editor.WebViewEmpty;
import dev.stemcraft.api.service.editor.WebViewField;
import dev.stemcraft.api.service.editor.WebViewForm;
import dev.stemcraft.api.service.editor.WebViewItem;
import dev.stemcraft.api.service.editor.WebViewLink;
import dev.stemcraft.api.service.editor.WebViewList;
import dev.stemcraft.api.service.editor.WebViewOption;
import dev.stemcraft.api.service.editor.WebViewPanel;
import dev.stemcraft.api.service.editor.WebViewSubview;
import dev.stemcraft.api.service.editor.WebViewTabs;
import dev.stemcraft.api.service.web.WebServiceRequest;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Typed, kid-friendly editor for crops and items found in the world. */
public final class AgricultureEditorModule implements EditorWebModule {
    private static final Gson GSON = new Gson();
    private static final String PERMISSION = "stemcraft.command.stemcraft.pack";
    private static final Set<String> KINDS = Set.of("crops", "foraging");

    private final STEMCraftAPI api;
    private final ContentPackServiceImpl packs;
    private final Runnable reloadDefinitions;

    public AgricultureEditorModule(@NotNull STEMCraftAPI api,
                                   @NotNull ContentPackServiceImpl packs,
                                   @NotNull Runnable reloadDefinitions) {
        this.api = api;
        this.packs = packs;
        this.reloadDefinitions = reloadDefinitions;
    }

    @Override public @NotNull String id() { return "agriculture"; }
    @Override public @NotNull String title() { return "Farming & Gathering"; }
    @Override public @NotNull String icon() { return "tree"; }
    @Override public @NotNull String description() { return "Set up crops players can grow and items they can find by breaking blocks."; }
    @Override public @NotNull String readPermission() { return PERMISSION; }
    @Override public @NotNull String writePermission() { return PERMISSION; }

    @Override
    public @NotNull WebView view(@NotNull EditorWebContext context, @NotNull WebServiceRequest request) {
        String pack = clean(request.queryParam("pack"));
        String file = clean(request.queryParam("file"));
        String kind = kind(request.queryParam("kind"));
        String id = clean(request.queryParam("id"));
        if (pack.isBlank() || file.isBlank()) return documentList(context, kind);
        ContentPackServiceImpl.EditorDataSnapshot snapshot = packs.editorData(pack, file);
        if (snapshot == null) return new WebView("agriculture", "Farming & Gathering", "", context.writable(), List.of(
            new WebViewSubview("Pack files", WebViewButton.moduleNavigate("back", "Back to pack", "packs", Map.of("pack", pack))),
            new WebViewEmpty("This pack file could not be opened.")));
        if (!id.isBlank()) return detail(context, snapshot, kind, id);

        Map<String, Object> records = records(snapshot.data(), kind);
        List<WebViewItem> items = records.entrySet().stream().map(entry -> {
            Map<String, Object> value = asMap(entry.getValue());
            String detail = kind.equals("crops")
                ? "Seed: " + value(value.get("seed"), "Not set") + " · Soil: " + value(value.get("soil"), "Not set")
                : "Item: " + value(value.get("item"), "Not set") + " · Blocks: " + stringList(value.get("blocks")).size();
            return new WebViewItem(entry.getKey(), pretty(entry.getKey()), detail,
                List.of(new WebViewBadge(kind.equals("crops") ? "Crop" : "Found item", "default")),
                WebViewButton.navigate("open-" + entry.getKey(), "Open",
                    Map.of("pack", pack, "file", file, "kind", kind, "id", entry.getKey())));
        }).toList();
        List<WebViewTabs.Tab> tabs = List.of(
            new WebViewTabs.Tab("crops", "Crops", WebViewButton.navigate("show-crops", "Crops",
                Map.of("pack", pack, "file", file, "kind", "crops")), kind.equals("crops")),
            new WebViewTabs.Tab("foraging", "Found items", WebViewButton.navigate("show-foraging", "Found items",
                Map.of("pack", pack, "file", file, "kind", "foraging")), kind.equals("foraging"))
        );
        List<WebViewComponent> components = List.of(new WebViewTabs(tabs),
            new WebViewPanel(kind.equals("crops") ? "Crops players can grow" : "Items players can find",
                kind.equals("crops")
                    ? "Set the seed, soil, growing stages, and what players get when they harvest."
                    : "Choose which blocks can drop each item when players break them.",
                1, List.of(new WebViewList(kind.equals("crops") ? "Crops" : "Items found in the world", items,
                    true, "Search by name", kind.equals("crops") ? "No crops in this file yet." : "No found items in this file yet."))));
        WebViewButton create = createAction(snapshot.pack(), snapshot.file(), kind, snapshot.revision());
        return new WebView("agriculture", "Farming & Gathering", "", context.writable(), components)
            .withTopbarActions(context.writable() ? List.of(create) : List.of())
            .withSidebarItems(List.of());
    }

    private WebView documentList(EditorWebContext context, String kind) {
        List<WebViewItem> documents = new ArrayList<>();
        for (var pack : packs.packs()) for (ContentPackDocument document : packs.documents(pack.id())) {
            String file = document.packDirectory().toPath().relativize(document.file().toPath()).toString()
                .replace(java.io.File.separatorChar, '/');
            ContentPackServiceImpl.EditorDataSnapshot snapshot = packs.editorData(pack.id(), file);
            if (snapshot == null || records(snapshot.data(), "crops").isEmpty() && records(snapshot.data(), "foraging").isEmpty()) continue;
            documents.add(new WebViewItem(pack.id() + "/" + file, pack.id(), file,
                List.of(new WebViewBadge(records(snapshot.data(), "crops").size() + " crops", "default"),
                    new WebViewBadge(records(snapshot.data(), "foraging").size() + " found items", "default")),
                WebViewButton.navigate("open-" + pack.id() + "-" + file.hashCode(), "Open", Map.of(
                    "pack", pack.id(), "file", file, "kind", kind))));
        }
        return new WebView("agriculture", "Farming & Gathering", "", context.writable(), List.of(
            new WebViewPanel("Choose a pack file", "Open a pack's crop and world item settings to edit them.", 1,
                List.of(new WebViewList("Pack files", documents, true, "Search packs", "No crop or world item settings found.")))));
    }

    private WebView detail(EditorWebContext context, ContentPackServiceImpl.EditorDataSnapshot snapshot,
                           String kind, String id) {
        Map<String, Object> values = records(snapshot.data(), kind).get(id) instanceof Map<?, ?> raw
            ? stringMap(raw) : null;
        Map<String, String> back = Map.of("pack", snapshot.pack(), "file", snapshot.file(), "kind", kind);
        if (values == null) return new WebView("agriculture", "Farming & Gathering", "", context.writable(), List.of(
            new WebViewSubview(pretty(id), WebViewButton.navigate("back", "Back to list", back)),
            new WebViewEmpty("This crop or world item no longer exists.")));

        List<WebViewField> fields = kind.equals("crops") ? cropFields(values) : forageFields(values);
        WebViewButton save = new WebViewButton("request", "save", "Save changes", "primary", "save", "POST",
            Map.of("pack", snapshot.pack(), "file", snapshot.file(), "kind", kind, "id", id,
                "revision", snapshot.revision()), Map.of(), null, Map.of());
        WebViewButton delete = new WebViewButton("request", "delete", "Remove", "danger", "delete", "POST",
            Map.of("pack", snapshot.pack(), "file", snapshot.file(), "kind", kind, "id", id,
                "revision", snapshot.revision()), Map.of(), null, Map.of("query", back),
            "Remove '" + pretty(id) + "' from this pack?");
        WebViewForm form = new WebViewForm("agriculture-entry", fields, List.of(), List.of(save), 2, "fields", false, "");
        String recordTitle = pretty(id);
        String panelTitle = kind.equals("crops") ? "Crop details" : "World item details";
        String description = kind.equals("crops")
            ? "Choose the Minecraft blocks and custom items used by this crop."
            : "Choose which blocks can drop this item and which tool players need.";
        return new WebView("agriculture", recordTitle, "", context.writable(), List.of(
            new WebViewSubview(recordTitle, WebViewButton.navigate("back", "Back to list", back)),
            new WebViewPanel(panelTitle, description + " " + snapshot.pack() + " / " + snapshot.file(), 1,
                List.of(form), context.writable() ? List.of(delete) : List.of(), "")));
    }

    private WebViewButton createAction(String pack, String file, String kind, String revision) {
        Map<String, Object> defaults = kind.equals("crops") ? Map.of(
            "soil", "FARMLAND", "water-above", false, "light-min", 9,
            "stages", List.of("WHEAT"), "seconds-per-stage", 180,
            "mature-min", 2, "mature-max", 4, "seed-min", 1, "seed-max", 2, "fortune", true
        ) : Map.of("blocks", List.of(), "biomes", List.of(), "chance-percent", 8,
            "min", 1, "max", 1, "fortune", false, "natural-only", true, "tools", List.of());
        List<WebViewField> fields = new ArrayList<>();
        fields.add(WebViewField.text("id", kind.equals("crops") ? "Crop ID" : "Found item ID",
            kind.equals("crops") ? "For example: rice" : "For example: rice-shoots", true, "[a-z0-9][a-z0-9_-]*")
            .withHelp("A short name used to identify this entry in the pack."));
        fields.addAll(kind.equals("crops") ? cropFields(defaults) : forageFields(defaults));
        String label = kind.equals("crops") ? "New crop" : "New found item";
        return WebViewButton.dialog("create-" + kind, label, "primary", "create",
            Map.of("pack", pack, "file", file, "kind", kind, "revision", revision),
            new WebViewDialog(kind.equals("crops") ? "Create a crop" : "Create a found item",
                "Create", fields, 2));
    }

    private List<WebViewField> cropFields(Map<String, Object> values) {
        return List.of(
            itemField("seed", "Seed item players plant", values.get("seed"), true)
                .withHelp("Choose the custom item players hold to plant this crop."),
            materialField("soil", "Block the crop grows on", values.getOrDefault("soil", "FARMLAND"), true)
                .withHelp("Choose the Minecraft block players must plant this crop on."),
            WebViewField.checkbox("water-above", "Needs water above the soil", bool(values.get("water-above"), false))
                .withHelp("Turn this on when the crop grows in water, such as rice."),
            WebViewField.number("light-min", "Minimum light level", value(values.get("light-min"), "9"), "0 to 15", true, "0", "15", "1")
                .withHelp("Minecraft light levels go from 0 (dark) to 15 (bright)."),
            new WebViewField("lines", "Growth stages (one block per line)", "",
                String.join("\n", stringList(values.getOrDefault("stages", List.of("WHEAT")))), "", false,
                null, List.of(), Map.of(), true)
                .withHelp("Enter the Minecraft block for each stage, from newly planted to fully grown. Example: WHEAT on one line."),
            WebViewField.number("seconds-per-stage", "Seconds for each growth stage", value(values.get("seconds-per-stage"), "180"), "For example: 180", true, "1", null, "1")
                .withHelp("A four-stage crop set to 180 takes about 12 minutes to fully grow."),
            itemField("mature-drop", "Item dropped when fully grown", values.get("mature-drop"), true)
                .withHelp("Choose the custom item players receive when they harvest a grown crop."),
            WebViewField.number("mature-min", "Smallest harvest amount", value(values.get("mature-min"), "2"), "", true, "1", null, "1"),
            WebViewField.number("mature-max", "Largest harvest amount", value(values.get("mature-max"), "4"), "", true, "1", null, "1"),
            itemField("seed-drop", "Seed item returned at harvest", values.get("seed-drop"), true)
                .withHelp("Choose the custom item players get back after harvesting."),
            WebViewField.number("seed-min", "Smallest number of seeds returned", value(values.get("seed-min"), "1"), "", true, "1", null, "1"),
            WebViewField.number("seed-max", "Largest number of seeds returned", value(values.get("seed-max"), "2"), "", true, "1", null, "1"),
            WebViewField.checkbox("fortune", "Fortune increases the drops", bool(values.get("fortune"), true))
        );
    }

    private List<WebViewField> forageFields(Map<String, Object> values) {
        List<WebViewOption> blocks = materialOptions(true);
        List<WebViewOption> biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).keyStream().sorted()
            .map(key -> new WebViewOption(key.toString(), pretty(key.getKey()))).toList();
        List<WebViewOption> tools = materialOptions(false);
        return List.of(
            new WebViewField("multiselect", "Blocks that can give this item", "",
                String.join("|", stringList(values.get("blocks"))), "", false, null, blocks, Map.of(), true)
                .withHelp("Search and tick the Minecraft blocks. For example: seagrass or tall seagrass."),
            new WebViewField("multiselect", "Biomes where it can drop", "",
                String.join("|", stringList(values.get("biomes"))), "", false, null, biomes, Map.of(), true)
                .withHelp("Leave all unchecked to allow the item to drop in every biome."),
            WebViewField.number("chance-percent", "Chance to find it (%)", value(percent(values.get("chance")), "8"), "0 to 100", true, "0", "100", "1")
                .withHelp("For example, 8 means an 8% chance each time the block is broken."),
            itemField("item", "Custom item players find", values.get("item"), true)
                .withHelp("Choose the custom item players receive. Use … to open its item editor."),
            WebViewField.number("min", "Smallest amount", value(values.get("min"), "1"), "", true, "1", null, "1"),
            WebViewField.number("max", "Largest amount", value(values.get("max"), "1"), "", true, "1", null, "1"),
            WebViewField.checkbox("natural-only", "Only blocks generated by Minecraft", bool(values.get("natural-only"), true))
                .withHelp("Turn this off if player-placed blocks should also drop the item."),
            new WebViewField("multiselect", "Tools that can collect it", "",
                String.join("|", stringList(values.get("tools"))), "", false, null, tools, Map.of(), true)
                .withHelp("Leave all unchecked to allow any tool."),
            WebViewField.checkbox("fortune", "Fortune increases the drops", bool(values.get("fortune"), false))
        );
    }

    private WebViewField itemField(String name, String label, Object value, boolean required) {
        Map<String, String> namespaces = new LinkedHashMap<>();
        for (var pack : packs.packs()) for (ContentPackDocument document : packs.documents(pack.id())) {
            var items = document.config().getSection("custom-items", false);
            if (items != null) for (String id : items.getKeys(false)) namespaces.put(id.toLowerCase(Locale.ROOT), document.namespace());
        }
        List<WebViewOption> options = api.items().customItemDefinitions().stream().map(definition -> {
            String namespace = namespaces.getOrDefault(definition.id().toLowerCase(Locale.ROOT), "stemcraft");
            String reference = namespace + ":" + definition.id();
            return new WebViewOption(reference, api.items().getItemName(definition.template()) + " (" + reference + ")");
        }).toList();
        return new WebViewField("suggestion", name, label, value(value, ""), "Search custom items", required,
            null, options, Map.of(), false)
            .withLink(new WebViewLink("items", "item", "Open custom item", Map.of(), "*:"));
    }

    private WebViewField materialField(String name, String label, Object value, boolean blocks) {
        return new WebViewField("suggestion", name, label, value(value, ""), "Search Minecraft blocks", true,
            null, materialOptions(blocks), Map.of(), false);
    }

    private static List<WebViewOption> materialOptions(boolean blocksOnly) {
        return java.util.Arrays.stream(Material.values()).filter(material -> !material.isAir())
            .filter(material -> blocksOnly ? material.isBlock() : material.isItem())
            .map(Material::name).sorted().map(value -> new WebViewOption(value, pretty(value))).toList();
    }

    private static Map<String, Object> records(Map<String, Object> data, String kind) {
        Map<String, Object> agriculture = asMap(data.get("agriculture"));
        return asMap(agriculture.get(kind));
    }

    @Override
    public @NotNull EditorWebResponse handle(@NotNull EditorWebContext context, @NotNull String path,
                                              @NotNull WebServiceRequest request) {
        if (!"POST".equalsIgnoreCase(request.method())) return text(405, "Method not allowed.");
        if (!context.writable()) return text(403, "This link is read-only.");
        try {
            Map<?, ?> body = GSON.fromJson(request.bodyAsString(), Map.class);
            if (body == null) return text(400, "The editor sent no crop details.");
            String pack = required(body.get("pack"), "Choose a content pack.");
            String file = required(body.get("file"), "Choose a pack file.");
            String kind = kind(required(body.get("kind"), "Choose crops or found items."));
            String id = required(body.get("id"), "Enter a name.").toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9][a-z0-9_-]*")) throw new IllegalArgumentException("Use lowercase letters, numbers, hyphens, or underscores for the ID.");
            String revision = clean(body.get("revision"));
            ContentPackServiceImpl.EditorDataSnapshot snapshot = packs.editorData(pack, file);
            if (snapshot == null) return text(404, "Pack file not found.");
            String recordPath = "agriculture." + kind + "." + id;
            if ("/delete".equals(path)) {
                if (!records(snapshot.data(), kind).containsKey(id)) return text(404, "That entry no longer exists.");
                ContentPackServiceImpl.DocumentSaveResult result = packs.saveEditorValue(pack, file, recordPath, null, revision);
                if (!result.success()) return json(409, result);
                reloadDefinitions.run();
                return json(200, Map.of("message", "Removed " + pretty(id) + "."));
            }
            Map<String, Object> fields = body.get("fields") instanceof Map<?, ?> raw ? stringMap(raw) : stringMap(body);
            fields.remove("id");
            Map<String, Object> record = kind.equals("crops") ? parseCrop(fields) : parseForage(fields);
            if ("/create".equals(path) && records(snapshot.data(), kind).containsKey(id))
                throw new IllegalArgumentException("An entry with that ID already exists.");
            if (!"/create".equals(path) && !"/save".equals(path)) return text(404, "Unknown editor action.");
            ContentPackServiceImpl.DocumentSaveResult result = packs.saveEditorValue(pack, file, recordPath,
                record, revision);
            if (!result.success()) return json(409, result);
            reloadDefinitions.run();
            return json(200, Map.of("message", ("/create".equals(path) ? "Created " : "Saved ") + pretty(id) + "."));
        } catch (IllegalArgumentException exception) {
            return text(400, exception.getMessage());
        } catch (RuntimeException exception) {
            return text(500, "Could not save these crop or world item settings.");
        }
    }

    private Map<String, Object> parseCrop(Map<String, Object> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("seed", customItem(fields.get("seed"), "Choose a custom seed item."));
        result.put("soil", validMaterial(fields.get("soil"), true, "Choose a Minecraft soil block."));
        result.put("water-above", bool(fields.get("water-above"), false));
        result.put("light-min", integer(fields.get("light-min"), 9, 0, 15, "Minimum light level"));
        List<String> stages = stringList(fields.get("stages")).stream()
            .map(stage -> validMaterial(stage, true, "Every growth stage must be a Minecraft block."))
            .collect(Collectors.toCollection(ArrayList::new));
        if (stages.isEmpty()) throw new IllegalArgumentException("Add at least one growth stage, with one Minecraft block on each line.");
        result.put("stages", stages);
        result.put("seconds-per-stage", integer(fields.get("seconds-per-stage"), 180, 1, Integer.MAX_VALUE, "Growth time"));
        result.put("mature-drop", customItem(fields.get("mature-drop"), "Choose the custom item dropped by a grown crop."));
        result.put("mature-min", integer(fields.get("mature-min"), 1, 1, Integer.MAX_VALUE, "Smallest harvest amount"));
        result.put("mature-max", integer(fields.get("mature-max"), 1, 1, Integer.MAX_VALUE, "Largest harvest amount"));
        if ((int) result.get("mature-max") < (int) result.get("mature-min"))
            throw new IllegalArgumentException("Largest harvest amount must be at least the smallest amount.");
        result.put("seed-drop", customItem(fields.get("seed-drop"), "Choose the custom seed item returned at harvest."));
        result.put("seed-min", integer(fields.get("seed-min"), 1, 1, Integer.MAX_VALUE, "Smallest seed amount"));
        result.put("seed-max", integer(fields.get("seed-max"), 1, 1, Integer.MAX_VALUE, "Largest seed amount"));
        if ((int) result.get("seed-max") < (int) result.get("seed-min"))
            throw new IllegalArgumentException("Largest seed amount must be at least the smallest amount.");
        result.put("fortune", bool(fields.get("fortune"), false));
        return result;
    }

    private Map<String, Object> parseForage(Map<String, Object> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> blocks = stringList(fields.get("blocks")).stream()
            .map(block -> validMaterial(block, true, "Choose Minecraft blocks."))
            .distinct().toList();
        if (blocks.isEmpty()) throw new IllegalArgumentException("Choose at least one block where players can find this item.");
        result.put("blocks", blocks);
        List<String> biomes = stringList(fields.get("biomes")).stream().map(String::toLowerCase).distinct().toList();
        for (String biome : biomes) {
            NamespacedKey key = NamespacedKey.fromString(biome.contains(":") ? biome : "minecraft:" + biome);
            if (key == null || RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key) == null)
                throw new IllegalArgumentException("Unknown Minecraft biome: " + biome);
        }
        result.put("biomes", biomes);
        double chance = decimal(fields.get("chance-percent"), 8D, "Drop chance") / 100D;
        if (!Double.isFinite(chance) || chance < 0D || chance > 1D) throw new IllegalArgumentException("Drop chance must be between 0 and 100 percent.");
        result.put("chance", chance);
        result.put("item", customItem(fields.get("item"), "Choose the custom item players find."));
        int min = integer(fields.get("min"), 1, 1, Integer.MAX_VALUE, "Smallest amount");
        int max = integer(fields.get("max"), 1, 1, Integer.MAX_VALUE, "Largest amount");
        if (max < min) throw new IllegalArgumentException("Largest amount must be at least the smallest amount.");
        result.put("min", min);
        result.put("max", max);
        result.put("natural-only", bool(fields.get("natural-only"), true));
        List<String> tools = stringList(fields.get("tools")).stream()
            .map(tool -> validMaterial(tool, false, "Choose Minecraft tools."))
            .distinct().toList();
        result.put("tools", tools);
        result.put("fortune", bool(fields.get("fortune"), false));
        return result;
    }

    private String customItem(Object raw, String error) {
        String id = required(raw, error);
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        boolean found = api.items().customItemDefinitions().stream().anyMatch(definition -> definition.id().equalsIgnoreCase(path));
        if (!found) throw new IllegalArgumentException("Choose a custom Minecraft item from the suggestion list.");
        return id;
    }

    private static String validMaterial(Object raw, boolean block, String error) {
        String value = required(raw, error).toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(value);
        if (material == null || material.isAir() || (block ? !material.isBlock() : !material.isItem()))
            throw new IllegalArgumentException(error);
        return material.name();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Collection<?> collection) return collection.stream().map(String::valueOf)
            .map(String::trim).filter(item -> !item.isBlank()).toList();
        if (value == null) return List.of();
        return java.util.Arrays.stream(String.valueOf(value).split("[|,\\r\\n]"))
            .map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? stringMap(map) : Map.of();
    }

    private static Map<String, Object> stringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String required(Object raw, String message) {
        String value = clean(raw);
        if (value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    private static String clean(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String value(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private static boolean bool(Object value, boolean fallback) { return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }
    private static int integer(Object raw, int fallback, int min, int max, String label) {
        try {
            double number = raw instanceof Number value ? value.doubleValue() : Double.parseDouble(String.valueOf(raw));
            if (!Double.isFinite(number) || number != Math.rint(number) || number < min || number > max) throw new NumberFormatException();
            return (int) number;
        } catch (RuntimeException exception) {
            if (raw == null) return fallback;
            throw new IllegalArgumentException(label + " must be a whole number between " + min + " and " + max + ".");
        }
    }
    private static double decimal(Object raw, double fallback, String label) {
        try {
            if (raw == null) return fallback;
            double value = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(raw));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) { throw new IllegalArgumentException(label + " must be a number."); }
    }
    private static String percent(Object value) {
        if (value instanceof Number number) return String.valueOf(number.doubleValue() * 100D);
        try { return String.valueOf(Double.parseDouble(String.valueOf(value)) * 100D); }
        catch (RuntimeException ignored) { return null; }
    }
    private static String kind(String raw) {
        String value = raw == null ? "crops" : raw.trim().toLowerCase(Locale.ROOT);
        return KINDS.contains(value) ? value : "crops";
    }
    private static String pretty(String value) {
        String words = value == null ? "" : value.replace('_', ' ').replace('-', ' ');
        return java.util.Arrays.stream(words.split("\\s+")).filter(word -> !word.isBlank())
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(" "));
    }
    private static EditorWebResponse json(int status, Object value) {
        return new EditorWebResponse(status, "application/json; charset=utf-8", GSON.toJson(value));
    }
    private static EditorWebResponse text(int status, String value) {
        return new EditorWebResponse(status, "text/plain; charset=utf-8", value == null ? "" : value);
    }
}

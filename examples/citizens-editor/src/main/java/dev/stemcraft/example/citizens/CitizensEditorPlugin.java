package dev.stemcraft.example.citizens;

import com.google.gson.Gson;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.editor.EditorWebContext;
import dev.stemcraft.api.service.editor.EditorWebModule;
import dev.stemcraft.api.service.editor.EditorWebResponse;
import dev.stemcraft.api.service.editor.WebView;
import dev.stemcraft.api.service.editor.WebViewBadge;
import dev.stemcraft.api.service.editor.WebViewButton;
import dev.stemcraft.api.service.editor.WebViewComponent;
import dev.stemcraft.api.service.editor.WebViewEmpty;
import dev.stemcraft.api.service.editor.WebViewField;
import dev.stemcraft.api.service.editor.WebViewFields;
import dev.stemcraft.api.service.editor.WebViewForm;
import dev.stemcraft.api.service.editor.WebViewItem;
import dev.stemcraft.api.service.editor.WebViewList;
import dev.stemcraft.api.service.editor.WebViewOption;
import dev.stemcraft.api.service.editor.WebViewPanel;
import dev.stemcraft.api.service.editor.WebViewSubview;
import dev.stemcraft.api.service.editor.WebViewTree;
import dev.stemcraft.api.service.web.WebServiceRequest;
import net.citizensnpcs.Citizens;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.api.util.MemoryDataKey;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.logging.Level;

/** Example STEMCraft WebView module for browsing and editing Citizens NPCs. */
public final class CitizensEditorPlugin extends JavaPlugin {
    private static final Gson GSON = new Gson();
    private EditorWebModule module;

    @Override
    public void onEnable() {
        STEMCraftAPI api = STEMCraftAPI.api();
        if (api == null) {
            getLogger().severe("STEMCraft API is unavailable; disabling the Citizens editor example.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        module = new CitizensNpcModule();
        api.editorWeb().registerModule(module);
    }

    @Override
    public void onDisable() {
        STEMCraftAPI api = STEMCraftAPI.api();
        if (api != null && module != null) api.editorWeb().unregisterModule(module);
    }

    private final class CitizensNpcModule implements EditorWebModule {
        @Override public String id() { return "citizens"; }
        @Override public String title() { return "Citizens"; }
        @Override public String icon() { return "users"; }
        @Override public String description() { return "Browse and edit saved Citizens NPCs."; }
        @Override public String readPermission() { return "citizens.admin"; }
        @Override public String writePermission() { return "citizens.admin"; }

        @Override
        public WebView view(EditorWebContext context, WebServiceRequest request) {
            try {
                String selectedId = request.queryParam("npc");
                if (selectedId != null && !selectedId.isBlank()) {
                    String selectedTrait = request.queryParam("trait");
                    if (selectedTrait != null && !selectedTrait.isBlank())
                        return traitDetail(context, selectedId, selectedTrait);
                    return npcDetail(context, selectedId);
                }
                return npcList(context);
            } catch (RuntimeException exception) {
                return new WebView("citizens", "Citizens", "", false,
                    List.of(new WebViewEmpty("Could not read Citizens NPCs: " + message(exception))));
            }
        }

        @Override
        public EditorWebResponse handle(EditorWebContext context, String path, WebServiceRequest request) {
            if (!"/npc".equals(path) && !"/trait".equals(path)) return text(404, "Not found.");
            if (!"PUT".equalsIgnoreCase(request.method())) return text(405, "Method not allowed.");
            if (!context.writable()) return text(403, "Citizens admin permission is required to save.");
            if (request.body().length > 128_000) return text(413, "Citizens editor payload is too large.");

            try {
                Map<?, ?> payload = GSON.fromJson(request.bodyAsString(), Map.class);
                if (payload == null) return text(400, "Missing Citizens editor data.");
                return "/trait".equals(path) ? saveTrait(payload) : saveNpc(payload);
            } catch (IllegalArgumentException exception) {
                return text(400, message(exception));
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "[citizens-editor] Could not save Citizens editor data.", exception);
                return text(500, "Citizens could not save these changes: " + message(exception));
            }
        }
    }

    private WebView npcList(EditorWebContext context) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        Map<String, List<NPC>> groups = new TreeMap<>();
        for (NPC npc : registry.sorted()) {
            if (!metadataBoolean(npc, NPC.Metadata.SHOULD_SAVE, true)) continue;
            String type = npc.getOrAddTrait(MobType.class).getType().name();
            groups.computeIfAbsent(type, ignored -> new ArrayList<>()).add(npc);
        }

        List<WebViewTree.Node> roots = groups.entrySet().stream().map(entry -> {
            List<WebViewTree.Node> npcs = entry.getValue().stream()
                .sorted(Comparator.comparingInt(NPC::getId))
                .map(this::npcTreeNode).toList();
            String type = entry.getKey();
            return new WebViewTree.Node("type-" + type, pretty(type), npcs.size() + " NPCs",
                List.of(new WebViewBadge(String.valueOf(npcs.size()), "default")), npcs,
                WebViewButton.display("type-" + type, pretty(type), "default"), true);
        }).toList();

        int count = groups.values().stream().mapToInt(List::size).sum();
        WebViewTree tree = new WebViewTree("NPCs from Citizens storage", roots, true,
            "Citizens has no saved NPCs.");
        return new WebView("citizens", "Citizens NPCs", "", context.writable(), List.of(
            new WebViewPanel("NPCs", 1, List.of(tree), List.of(), count + " saved NPC" + (count == 1 ? "" : "s"))));
    }

    private WebViewTree.Node npcTreeNode(NPC npc) {
        EntityType type = npc.getOrAddTrait(MobType.class).getType();
        Location location = npc.getStoredLocation();
        String place = location == null || location.getWorld() == null ? "No stored location" :
            location.getWorld().getName() + " · " + format(location.getX()) + ", " +
                format(location.getY()) + ", " + format(location.getZ());
        List<WebViewBadge> badges = new ArrayList<>();
        badges.add(new WebViewBadge(npc.isSpawned() ? "Spawned" : "Despawned", npc.isSpawned() ? "good" : "default"));
        if (metadataBoolean(npc, NPC.Metadata.DEFAULT_PROTECTED, true)) {
            badges.add(new WebViewBadge("Protected", "default"));
        }
        return new WebViewTree.Node("npc-" + npc.getId(), npc.getName(),
            "ID " + npc.getId() + " · " + pretty(type.name()) + " · " + place, badges, List.of(),
            WebViewButton.navigate("open-npc-" + npc.getId(), "Open", Map.of("npc", String.valueOf(npc.getId()))), false);
    }

    private WebView npcDetail(EditorWebContext context, String selectedId) {
        int id;
        try {
            id = Integer.parseInt(selectedId);
        } catch (NumberFormatException exception) {
            return missingNpcView(context, "Invalid NPC ID.");
        }
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc == null) return missingNpcView(context, "That NPC is not in the Citizens registry.");

        EntityType type = npc.getOrAddTrait(MobType.class).getType();
        Map<String, Object> state = npcState(npc);
        List<WebViewField> identityFields = List.of(
            WebViewField.text("name", "NPC name", "Name shown in game", true, null),
            new WebViewField("select", "entityType", "Entity type", type.name(), "", true, null,
                entityTypeOptions())
        );
        List<WebViewField> behaviorFields = List.of(
            WebViewField.checkbox("protected", "Protected from player and mob interaction",
                metadataBoolean(npc, NPC.Metadata.DEFAULT_PROTECTED, true)),
            WebViewField.checkbox("minecraftAi", "Use Minecraft AI",
                metadataBoolean(npc, NPC.Metadata.USE_MINECRAFT_AI, false)),
            WebViewField.checkbox("flyable", "Allow flying movement",
                metadataBoolean(npc, NPC.Metadata.FLYABLE, false)),
            WebViewField.checkbox("nameplateVisible", "Show the nameplate",
                metadataBoolean(npc, NPC.Metadata.NAMEPLATE_VISIBLE, true)),
            WebViewField.checkbox("nameHologram", "Always use a name hologram",
                metadataBoolean(npc, NPC.Metadata.ALWAYS_USE_NAME_HOLOGRAM, false)),
            WebViewField.checkbox("collidable", "Collidable",
                metadataBoolean(npc, NPC.Metadata.COLLIDABLE,
                    !metadataBoolean(npc, NPC.Metadata.DEFAULT_PROTECTED, true)))
        );
        WebViewButton save = new WebViewButton("request", "save", "Save NPC", "primary", "npc", "PUT",
            Map.of(), Map.of(), null, Map.of());
        WebViewForm form = new WebViewForm("citizens-npc-editor", List.of(), List.of(
            new WebViewPanel("Identity", 1, List.of(new WebViewFields(identityFields, 2))),
            new WebViewPanel("Behavior", 1, List.of(new WebViewFields(behaviorFields, 2)))
        ), context.writable() ? List.of(save) : List.of(), 1, "", true, "");

        List<WebViewItem> traits = new ArrayList<>();
        for (Trait trait : npc.getTraits()) {
            traits.add(new WebViewItem(trait.getName(), pretty(trait.getName()),
                trait.getClass().getSimpleName(), List.of(),
                WebViewButton.navigate("edit-trait-" + trait.getName(), "Edit", Map.of(
                    "npc", String.valueOf(npc.getId()), "trait", trait.getName()))));
        }
        traits.sort(Comparator.comparing(WebViewItem::title, String.CASE_INSENSITIVE_ORDER));

        String location = locationDescription(npc.getStoredLocation());
        return new WebView("citizens", npc.getName(), "", context.writable(), List.of(
            new WebViewSubview(npc.getName(), WebViewButton.navigate("back-to-npcs", "All NPCs", Map.of())),
            new WebViewPanel("NPC settings", 1, List.of(form), List.of(), location),
            new WebViewPanel("Saved traits", 1, List.of(new WebViewList("Traits", traits, true,
                "Search traits", "No traits are registered on this NPC.")), List.of(),
                traits.size() + " trait" + (traits.size() == 1 ? "" : "s"))
        ), state);
    }

    private WebView traitDetail(EditorWebContext context, String selectedId, String traitName) {
        int id;
        try {
            id = Integer.parseInt(selectedId);
        } catch (NumberFormatException exception) {
            return missingNpcView(context, "Invalid NPC ID.");
        }
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc == null) return missingNpcView(context, "That NPC is not in the Citizens registry.");
        Trait trait = findTrait(npc, traitName);
        if (trait == null) return missingNpcView(context, "That trait is no longer registered on this NPC.");

        Map<String, Object> values = traitData(trait);
        List<WebViewField> fields = new ArrayList<>();
        collectTraitFields(values, "fields", fields, 0);
        Map<String, Object> formValues = copyTraitMap(values);
        for (WebViewField field : fields) {
            if ("json".equals(field.type())) {
                String path = field.name().substring("fields.".length());
                if (traitPath(formValues, path) == null) setTraitPath(formValues, path, "null");
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("npcId", npc.getId());
        state.put("traitName", trait.getName());
        state.put("revision", npcRevision(npc));
        state.put("fields", formValues);

        WebViewButton save = new WebViewButton("request", "save-trait", "Save trait", "primary",
            "trait", "PUT", Map.of(), Map.of(), null, Map.of());
        List<WebViewComponent> components = new ArrayList<>();
        components.add(new WebViewSubview(pretty(trait.getName()), WebViewButton.navigate("back-to-npc", "Back to NPC", Map.of(
            "npc", String.valueOf(npc.getId())))));
        if (fields.isEmpty()) {
            components.add(new WebViewPanel("Trait settings", trait.getClass().getSimpleName(), 1, List.of(
                new WebViewEmpty("This trait has no saved settings that can be edited."))));
        } else {
            WebViewForm form = new WebViewForm("citizens-trait-editor", fields, List.of(),
                context.writable() ? List.of(save) : List.of(), 2, "", true, "");
            components.add(new WebViewPanel(pretty(trait.getName()),
                "Change the saved settings for this NPC trait. The values below are stored by Citizens.", 1,
                List.of(form)));
        }
        return new WebView("citizens", npc.getName() + " · " + pretty(trait.getName()), "",
            context.writable(), components, state);
    }

    private EditorWebResponse saveNpc(Map<?, ?> payload) {
        int id = integer(payload.get("npcId"), "NPC ID");
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc == null) return text(404, "That NPC no longer exists in Citizens storage.");
        String expectedRevision = string(payload.get("revision"), "NPC revision");
        if (!npcRevision(npc).equals(expectedRevision)) {
            return text(409, "This NPC changed after the page opened. Reload it before saving your edits.");
        }

        String name = string(payload.get("name"), "NPC name").trim();
        if (name.isEmpty()) return text(400, "NPC name cannot be blank.");
        if (name.length() > 256) return text(400, "NPC name cannot exceed 256 characters.");
        EntityType type;
        try {
            type = EntityType.valueOf(string(payload.get("entityType"), "Entity type"));
        } catch (IllegalArgumentException exception) {
            return text(400, "Select a valid entity type.");
        }
        if (!type.isAlive()) return text(400, "Citizens NPCs must use a living entity type.");

        boolean protectedNpc = bool(payload.get("protected"), "Protected");
        boolean minecraftAi = bool(payload.get("minecraftAi"), "Minecraft AI");
        boolean flyable = bool(payload.get("flyable"), "Flyable");
        boolean nameplateVisible = bool(payload.get("nameplateVisible"), "Nameplate visibility");
        boolean nameHologram = bool(payload.get("nameHologram"), "Name hologram");
        boolean collidable = bool(payload.get("collidable"), "Collidable");

        try {
            if (!npc.getName().equals(name)) npc.setName(name);
            MobType mobType = npc.getOrAddTrait(MobType.class);
            if (mobType.getType() != type) {
                npc.setBukkitEntityType(type);
                mobType.setType(type);
            }
            npc.setProtected(protectedNpc);
            npc.setUseMinecraftAI(minecraftAi);
            npc.setFlyable(flyable);
            npc.setAlwaysUseNameHologram(nameHologram);
            npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, nameplateVisible);
            npc.data().setPersistent(NPC.Metadata.COLLIDABLE, collidable);
            citizens().storeNPCs();
        } catch (RuntimeException exception) {
            getLogger().warning("[citizens-editor] Failed saving NPC " + id + ": " + message(exception));
            return text(500, "Citizens could not apply or save these NPC settings: " + message(exception));
        }
        return json(200, Map.of("message", "NPC settings saved to Citizens storage."));
    }

    private EditorWebResponse saveTrait(Map<?, ?> payload) {
        int id = integer(payload.get("npcId"), "NPC ID");
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc == null) return text(404, "That NPC no longer exists in Citizens storage.");
        String traitName = string(payload.get("traitName"), "Trait name");
        Trait trait = findTrait(npc, traitName);
        if (trait == null) return text(404, "That trait is no longer registered on this NPC.");
        String expectedRevision = string(payload.get("revision"), "NPC revision");
        if (!npcRevision(npc).equals(expectedRevision)) {
            return text(409, "This NPC changed after the page opened. Reload it before saving your edits.");
        }
        if (!(payload.get("fields") instanceof Map<?, ?> submitted))
            return text(400, "No trait settings were submitted.");

        Map<String, Object> previous = traitData(trait);
        Map<String, Object> updated = copyTraitMap(previous);
        for (Map.Entry<?, ?> entry : submitted.entrySet()) {
            if (entry.getKey() == null) continue;
            String path = String.valueOf(entry.getKey());
            if (!containsTraitPath(updated, path))
                return text(400, "The trait field '" + prettyPath(path) + "' is no longer available. Reload the NPC.");
            Object current = traitPath(updated, path);
            setTraitPath(updated, path, normalizeTraitValue(entry.getValue(), current, path));
        }

        try {
            trait.load(memoryDataKey(updated));
            citizens().storeNPCs();
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "[citizens-editor] Could not apply trait '" + traitName + "' to NPC " + id + ".", exception);
            try {
                trait.load(memoryDataKey(previous));
                citizens().storeNPCs();
            } catch (Exception rollbackException) {
                getLogger().log(Level.SEVERE, "[citizens-editor] Could not restore trait '" + traitName + "' on NPC " + id + ".",
                    rollbackException);
            }
            return text(500, "Citizens could not apply those trait settings: " + message(exception));
        }
        return json(200, Map.of("message", "Saved " + pretty(traitName) + " settings for " + npc.getName() + "."));
    }

    private static Trait findTrait(NPC npc, String name) {
        for (Trait trait : npc.getTraits()) if (trait.getName().equals(name)) return trait;
        return null;
    }

    private static Map<String, Object> traitData(Trait trait) {
        MemoryDataKey data = new MemoryDataKey();
        trait.save(data);
        Object canonical = canonicalTraitValue(data.getValuesDeep());
        return copyTraitMap(canonical instanceof Map<?, ?> map ? map : Map.of());
    }

    private static MemoryDataKey memoryDataKey(Map<String, Object> values) {
        MemoryDataKey data = new MemoryDataKey();
        values.forEach((key, value) -> data.setRaw(key, copyTraitValue(value)));
        return data;
    }

    private static Object canonicalTraitValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> {
                if (key != null) sorted.put(String.valueOf(key), canonicalTraitValue(child));
            });
            return sorted;
        }
        if (value instanceof java.util.Collection<?> collection)
            return collection.stream().map(CitizensEditorPlugin::canonicalTraitValue).toList();
        return value;
    }

    private static Map<String, Object> copyTraitMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((key, child) -> {
            if (key != null) result.put(String.valueOf(key), copyTraitValue(child));
        });
        return result;
    }

    private static Object copyTraitValue(Object value) {
        if (value instanceof Map<?, ?> map) return copyTraitMap(map);
        if (value instanceof java.util.Collection<?> collection)
            return collection.stream().map(CitizensEditorPlugin::copyTraitValue).toList();
        return value;
    }

    private static void collectTraitFields(Map<String, Object> values, String prefix,
                                           List<WebViewField> fields, int depth) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String path = prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map && !map.isEmpty() && depth < 8) {
                collectTraitFields(copyTraitMap(map), path, fields, depth + 1);
            } else {
                fields.add(traitField(path, value));
            }
        }
    }

    private static WebViewField traitField(String path, Object value) {
        String label = prettyPath(path.substring("fields.".length()));
        if (value instanceof Boolean checked) {
            return WebViewField.checkbox(path, label, checked)
                .withHelp("This setting belongs to the " + prettyPath(path.substring("fields.".length()).split("\\.")[0]) + " trait.");
        }
        if (value instanceof Number number) {
            String step = number.doubleValue() == Math.rint(number.doubleValue()) ? "1" : "0.1";
            return WebViewField.number(path, label, String.valueOf(value), "", false, null, null, step)
                .withHelp("This number is saved by the Citizens trait.");
        }
        if (value instanceof String text) {
            WebViewField field = text.contains("\n")
                ? WebViewField.textarea(path, label, text, true)
                : WebViewField.text(path, label, "", false, null);
            if (!text.contains("\n")) field = new WebViewField(field.type(), field.name(), field.label(), text,
                field.placeholder(), field.required(), field.pattern(), field.options(), field.attributes(), field.wide(),
                field.help(), field.link());
            return field.withHelp("This text is saved by the Citizens trait.");
        }
        if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
            return new WebViewField("lines", path, label, GSON.toJson(list), "", false, null, List.of(), Map.of(), true)
                .withHelp("Enter one value per line. Citizens saves these as a list.");
        }
        if (value == null)
            return new WebViewField("json", path, label, "null", "", false, null, List.of(), Map.of(), true)
                .withHelp("This saved value is empty. Keep null unless this trait needs another JSON value.");
        return WebViewField.json(path, label, value, true)
            .withHelp("Advanced trait data. Keep the value as valid JSON.");
    }

    private static String prettyPath(String path) {
        return Arrays.stream(path.replaceAll("([a-z])([A-Z])", "$1 $2").split("[._\\-\\s]+"))
            .filter(word -> !word.isBlank())
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
            .collect(Collectors.joining(" "));
    }

    private static boolean containsTraitPath(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (int index = 0; index < parts.length; index++) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(parts[index])) return false;
            current = map.get(parts[index]);
        }
        return true;
    }

    private static Object traitPath(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) current = ((Map<?, ?>) current).get(part);
        return current;
    }

    private static void setTraitPath(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> parent = root;
        for (int index = 0; index < parts.length - 1; index++) {
            Object child = parent.get(parts[index]);
            if (!(child instanceof Map<?, ?> map)) throw new IllegalArgumentException("Trait setting path is invalid.");
            parent = copyTraitMap(map);
            root.put(parts[index], parent);
        }
        parent.put(parts[parts.length - 1], value);
    }

    private static Object normalizeTraitValue(Object submitted, Object current, String path) {
        if (current instanceof Boolean) {
            if (submitted instanceof Boolean) return submitted;
            throw new IllegalArgumentException("" + prettyPath(path) + " must be checked or unchecked.");
        }
        if (current instanceof Number number) {
            if (!(submitted instanceof Number next) || !Double.isFinite(next.doubleValue()))
                throw new IllegalArgumentException(prettyPath(path) + " must be a valid number.");
            if (number.doubleValue() == Math.rint(number.doubleValue())) return next.longValue();
            return next.doubleValue();
        }
        if (current instanceof String) {
            if (submitted instanceof String) return submitted;
            throw new IllegalArgumentException(prettyPath(path) + " must be text.");
        }
        if (current instanceof List<?> oldList) {
            if (!(submitted instanceof List<?> nextList))
                throw new IllegalArgumentException(prettyPath(path) + " must be a list.");
            if (oldList.isEmpty()) return nextList.stream().map(CitizensEditorPlugin::copyTraitValue).toList();
            Object sample = oldList.getFirst();
            return nextList.stream().map(value -> normalizeTraitValue(value, sample, path)).toList();
        }
        if (current instanceof Map<?, ?>) {
            if (!(submitted instanceof Map<?, ?> next))
                throw new IllegalArgumentException(prettyPath(path) + " must be an object.");
            return copyTraitMap(next);
        }
        return copyTraitValue(submitted);
    }

    private WebView missingNpcView(EditorWebContext context, String message) {
        return new WebView("citizens", "Citizens NPCs", "", context.writable(), List.of(
            new WebViewSubview("NPC not found", WebViewButton.navigate("back-to-npcs", "All NPCs", Map.of())),
            new WebViewEmpty(message)
        ));
    }

    private static List<WebViewOption> entityTypeOptions() {
        return Arrays.stream(EntityType.values()).filter(EntityType::isAlive)
            .sorted(Comparator.comparing(EntityType::name))
            .map(type -> new WebViewOption(type.name(), pretty(type.name())))
            .toList();
    }

    private static Map<String, Object> npcState(NPC npc) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("npcId", npc.getId());
        state.put("revision", npcRevision(npc));
        state.put("name", npc.getName());
        state.put("entityType", npc.getOrAddTrait(MobType.class).getType().name());
        state.put("protected", metadataBoolean(npc, NPC.Metadata.DEFAULT_PROTECTED, true));
        state.put("minecraftAi", metadataBoolean(npc, NPC.Metadata.USE_MINECRAFT_AI, false));
        state.put("flyable", metadataBoolean(npc, NPC.Metadata.FLYABLE, false));
        state.put("nameplateVisible", metadataBoolean(npc, NPC.Metadata.NAMEPLATE_VISIBLE, true));
        state.put("nameHologram", metadataBoolean(npc, NPC.Metadata.ALWAYS_USE_NAME_HOLOGRAM, false));
        state.put("collidable", metadataBoolean(npc, NPC.Metadata.COLLIDABLE,
            !metadataBoolean(npc, NPC.Metadata.DEFAULT_PROTECTED, true)));
        return state;
    }

    private static String npcRevision(NPC npc) {
        Map<String, Object> values = new LinkedHashMap<>(npcStateWithoutRevision(npc));
        Map<String, Object> traits = new TreeMap<>();
        for (Trait trait : npc.getTraits()) traits.put(trait.getName(), traitData(trait));
        values.put("traits", traits);
        values.put("spawned", npc.isSpawned());
        values.put("location", locationDescription(npc.getStoredLocation()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(GSON.toJson(values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static Map<String, Object> npcStateWithoutRevision(NPC npc) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", npc.getId());
        values.put("name", npc.getName());
        values.put("entityType", npc.getOrAddTrait(MobType.class).getType().name());
        values.put("protected", metadataBoolean(npc, NPC.Metadata.DEFAULT_PROTECTED, true));
        values.put("minecraftAi", metadataBoolean(npc, NPC.Metadata.USE_MINECRAFT_AI, false));
        values.put("flyable", metadataBoolean(npc, NPC.Metadata.FLYABLE, false));
        values.put("nameplateVisible", metadataBoolean(npc, NPC.Metadata.NAMEPLATE_VISIBLE, true));
        values.put("nameHologram", metadataBoolean(npc, NPC.Metadata.ALWAYS_USE_NAME_HOLOGRAM, false));
        values.put("collidable", metadataBoolean(npc, NPC.Metadata.COLLIDABLE,
            !metadataBoolean(npc, NPC.Metadata.DEFAULT_PROTECTED, true)));
        return values;
    }

    private static boolean metadataBoolean(NPC npc, NPC.Metadata metadata, boolean fallback) {
        Object value = npc.data().get(metadata.getKey());
        if (value instanceof Boolean result) return result;
        if (value instanceof String text) {
            return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "true", "yes", "on", "1" -> true;
                case "false", "no", "off", "0" -> false;
                default -> fallback;
            };
        }
        if (value instanceof Number number) return number.intValue() != 0;
        return fallback;
    }

    private Citizens citizens() {
        if (CitizensAPI.getPlugin() instanceof Citizens plugin && plugin.isEnabled()) return plugin;
        throw new IllegalStateException("Citizens is not available.");
    }

    private static String locationDescription(Location location) {
        if (location == null) return "No stored location";
        String world = location.getWorld() == null ? "Unknown world" : location.getWorld().getName();
        return world + " · " + format(location.getX()) + ", " + format(location.getY()) + ", " +
            format(location.getZ());
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String pretty(String value) {
        String[] words = value.toLowerCase(java.util.Locale.ROOT).split("[_\\s]+");
        return Arrays.stream(words).filter(word -> !word.isBlank())
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
            .collect(Collectors.joining(" "));
    }

    private static int integer(Object value, String label) {
        if (value instanceof Number number) return number.intValue();
        throw new IllegalArgumentException(label + " is missing or invalid.");
    }

    private static String string(Object value, String label) {
        if (value instanceof String text) return text;
        throw new IllegalArgumentException(label + " is missing or invalid.");
    }

    private static boolean bool(Object value, String label) {
        if (value instanceof Boolean result) return result;
        throw new IllegalArgumentException(label + " is missing or invalid.");
    }

    private static EditorWebResponse text(int status, String body) {
        return new EditorWebResponse(status, "text/plain; charset=utf-8", body);
    }

    private static EditorWebResponse json(int status, Object value) {
        return new EditorWebResponse(status, "application/json; charset=utf-8", GSON.toJson(value));
    }

    private static String message(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}

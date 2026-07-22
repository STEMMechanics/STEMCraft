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

package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.event.server.MaintenanceModeChangedEvent;
import dev.stemcraft.api.factory.ChunkGeneratorFactory;
import dev.stemcraft.api.util.PermissionUtil;
import dev.stemcraft.command.BaseCommand;
import dev.stemcraft.config.migration.HyphenatedConfigSchemaMigration;
import dev.stemcraft.config.migration.LegacyServiceConfigMigration;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.service.*;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.chunkgen.FlatGenerator;
import dev.stemcraft.chunkgen.WaterGenerator;
import dev.stemcraft.chunkgen.VoidGenerator;
import dev.stemcraft.feature.BaseFeature;
import dev.stemcraft.service.command.CommandServiceImpl;
import dev.stemcraft.service.message.MessageServiceImpl;
import dev.stemcraft.service.minigame.MiniGameServiceImpl;
import dev.stemcraft.service.resourcepack.ResourcePackServiceImpl;
import dev.stemcraft.service.tabcompletion.TabCompleteServiceImpl;
import dev.stemcraft.service.firstjoin.FirstJoinService;
import dev.stemcraft.service.world.WorldServiceImpl;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("SameParameterValue")
@Getter
@Accessors(fluent = true)
public final class STEMCraft extends JavaPlugin {
    private static final Pattern VERSION_COMPONENT_PATTERN = Pattern.compile("\\d+");
    private static STEMCraftAPI api;
    private File cacheDir;

    private ChatServiceImpl chat;
    private CommandServiceImpl commands;
    private ConfigServiceImpl config;
    private AuditServiceImpl audit;
    private DatabaseServiceImpl database;
    private EventServiceImpl events;
    private HologramServiceImpl holograms;
    private ItemServiceImpl items;
    private LocaleServiceImpl locales;
    private MessageServiceImpl messages;
    private MiniGameServiceImpl minigames;
    private MotdServiceImpl motd;
    private PlaceholderServiceImpl placeholders;
    private PlayerServiceImpl players;
    private PlayerStatsServiceImpl playerStats;
    private ProfanityFilterServiceImpl profanityFilter;
    private PunishmentServiceImpl punishments;
    private RecipeServiceImpl recipes;
    private RegionServiceImpl regions;
    private ResourcePackServiceImpl resourcePack;
    private SelectionServiceImpl selections;
    private TabCompleteServiceImpl tabComplete;
    private TaskServiceImpl tasks;
    private FirstJoinService firstJoin;
    private WebServiceImpl web;
    private WorldServiceImpl worlds;

    @Getter(AccessLevel.NONE)
    private ConfigFile configFile;
    private final List<BaseFeature> loadedFeatures = new ArrayList<>();
    private final List<String> loadedCommandIds = new ArrayList<>();
    private final List<String> loadedMiniGameIds = new ArrayList<>();
    private final Map<String, String> loadFailures = new LinkedHashMap<>();

    private volatile boolean isMaintenanceMode = false;
    private boolean postWorldStartupComplete = false;

    @Getter
    private volatile boolean debugging = false;
    private static final String WHITE_LIST_MESSAGE = "This server is invite-only.";

    /** {@inheritDoc} */
    @Override
    public void onEnable() {

        // Check we are running Paper
        if(!isPaper()) {
            getLogger().severe("STEMCraft requires Paper to run.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        initializeApi(this);
        InstanceHolder.set(api, this);

        // Load pre-early services
        config = new ConfigServiceImpl(this, api);
        tasks = new TaskServiceImpl(this, api);
        tasks.onEnable();
        config.onEnable();

        // Load configuration
        configFile = api.config().load("config.yml");
        if(configFile == null) {
            error("STEMCRAFT_ERROR_LOAD_CONFIG");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }

        // Temporary migration for legacy service config paths. Remove after old configs are retired.
        new LegacyServiceConfigMigration(this, configFile).apply();
        // Temporary migration for known snake_case schema keys. Remove after configs have been rewritten in the field.
        new HyphenatedConfigSchemaMigration(this, configFile).apply();

        // Load early services
        messages = new MessageServiceImpl(this, api);
        locales = new LocaleServiceImpl(this, api);

        messages.onEnable();
        locales.onEnable();

        // Check dependencies
        Plugin we = getServer().getPluginManager().getPlugin("WorldEdit");
        if(we == null) {
            error("DEPENDENCY_WORLDEDIT_REQUIRED");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        debugging = configFile.getBoolean("debug", false);

        // Load managers
        audit = new AuditServiceImpl(this, api);
        chat = new ChatServiceImpl(this, api);
        commands = new CommandServiceImpl(this, api);
        database = new DatabaseServiceImpl(this, api);
        events = new EventServiceImpl(this, api);
        holograms = new HologramServiceImpl(this, api);
        items = new ItemServiceImpl(this, api);
        minigames = new MiniGameServiceImpl(this, api);
        motd = new MotdServiceImpl(this, api);
        placeholders = new PlaceholderServiceImpl(this, api);
        players = new PlayerServiceImpl(this, api);
        playerStats = new PlayerStatsServiceImpl(this, api);
        profanityFilter = new ProfanityFilterServiceImpl(this, api);
        punishments = new PunishmentServiceImpl(this, api);
        recipes = new RecipeServiceImpl(this, api);
        regions = new RegionServiceImpl(this, api);
        resourcePack = new ResourcePackServiceImpl(this, api);
        selections = new SelectionServiceImpl(this, api);
        tabComplete = new TabCompleteServiceImpl(this, api);
        web = new WebServiceImpl(this, api);
        firstJoin = new FirstJoinService(this, api);
        worlds = new WorldServiceImpl(this, api);

        database.onEnable();
        firstJoin.onEnable();
        audit.onEnable();
        chat.onEnable();
        commands.onEnable();
        events.onEnable();
        holograms.onEnable();
        items.onEnable();
        minigames.onEnable();
        motd.onEnable();
        placeholders.onEnable();
        players.onEnable();
        playerStats.onEnable();
        profanityFilter.onEnable();
        punishments.onEnable();
        recipes.onEnable();
        regions.onEnable();
        resourcePack.onEnable();
        selections.onEnable();
        tabComplete.onEnable();
        web.onEnable();
        worlds.onEnable();

        registerBuiltInWorldGenerators();

        info("STEMCRAFT_ENABLED");

        isMaintenanceMode = configFile.getBoolean("maintenance", false);

        api.commands().create("maintenance")
            .tabCompletion("on")
            .tabCompletion("off")
            .description("MAINTENANCE_DESCRIPTION")
            .usage("MAINTENANCE_USAGE")
            .permission("stemcraft.command.maintenance")
            .executor((unused, cmd, ctx) -> {
                if (ctx.args().isEmpty()) {
                    ctx.returnInfo("MAINTENANCE_STATUS", "state", isMaintenanceMode ? "on" : "off");
                }

                String state = ctx.args().getFirst().toLowerCase();
                if ("on".equals(state)) {
                    isMaintenanceMode = true;
                } else if ("off".equals(state)) {
                    isMaintenanceMode = false;
                } else {
                    ctx.returnError("MAINTENANCE_INVALID_OPTION");
                }

                configFile.set("maintenance", isMaintenanceMode);
                configFile.save();

                Bukkit.getPluginManager().callEvent(new MaintenanceModeChangedEvent(isMaintenanceMode));

                ctx.returnInfo("MAINTENANCE_STATUS", "state", isMaintenanceMode ? "on" : "off");
            })
            .register(this);

        //noinspection UnstableApiUsage
        api.events().register(PlayerConnectionValidateLoginEvent.class, event -> {
            if (isMaintenanceMode) {
                UUID uuid = getUuid(event);

                if (uuid == null) {
                    // may be null in the Paper API
                    // can’t reliably identify them here (offline mode / early stage / etc.)
                    return;
                }

                if(PermissionUtil.hasPermission(uuid, "stemcraft.maintenance.bypass")) {
                    return;
                }

                //noinspection UnstableApiUsage
                event.kickMessage(Component.text("Server is under maintenance. Please try again later."));
            }
        });

        // Also kick on join for safety as PlayerConnectionValidateLoginEvent may be bypassed in some cases
        api.events().register(PlayerJoinEvent.class, event -> {
            if(isMaintenanceMode) {
                Player player = event.getPlayer();
                if(!player.hasPermission("stemcraft.maintenance.bypass")) {
                    player.kick(Component.text("Server is under maintenance. Please try again later."), PlayerKickEvent.Cause.PLUGIN);
                } else {
                    info("The server is in maintenance mode");
                }
            }
        });

        api.events().register(ServerLoadEvent.class, event -> completePostWorldStartup());
    }

    @SuppressWarnings("UnstableApiUsage")
    private static @Nullable UUID getUuid(PlayerConnectionValidateLoginEvent event) {
        var conn = event.getConnection();
        UUID uuid = null;

        //noinspection UnstableApiUsage
        if (conn instanceof PlayerLoginConnection loginConn) {
            //noinspection UnstableApiUsage
            var profile = loginConn.getAuthenticatedProfile();
            if (profile == null) //noinspection UnstableApiUsage
                profile = loginConn.getUnsafeProfile();
            if (profile != null) uuid = profile.getId();
        }
        return uuid;
    }

    /** {@inheritDoc} */
    @Override
    public void onDisable() {
        disableService(minigames);
        disableService(worlds);
        disableService(web);
        disableService(tabComplete);
        disableService(resourcePack);
        disableService(selections);
        disableService(regions);
        disableService(recipes);
        disableService(punishments);
        disableService(profanityFilter);
        disableService(playerStats);
        disableService(placeholders);
        disableService(players);
        disableService(motd);
        disableService(items);
        disableService(holograms);
        disableService(events);
        disableService(firstJoin);
        disableService(audit);
        disableService(database);
        disableService(commands);
        disableService(chat);

        disableService(tasks);
        disableService(locales);
        disableService(messages);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, String id) {
        BuiltInGeneratorSpec spec = parseBuiltInGeneratorSpec(id);
        if (spec.key().isEmpty()) {
            return null;
        }

        return createBuiltInGenerator(spec.key(), spec.options());
    }

    private void registerBuiltInWorldGenerators() {
        worlds.generator().register("void", options -> createBuiltInGenerator("void", options));
        worlds.generator().register("flat", options -> createBuiltInGenerator("flat", options));
        worlds.generator().register("water", new ChunkGeneratorFactory() {
            @Override
            public ChunkGenerator create(String options) {
                return createBuiltInGenerator("water", options);
            }

            @Override
            public @NonNull List<String> tabCompleteOptions(@NonNull String options) {
                return WaterGenerator.tabCompleteOptions(options);
            }
        });
        worlds.generator().register("normal", options -> createBuiltInGenerator("normal", options));
        worlds.generator().register("default", options -> createBuiltInGenerator("default", options));
    }

    private void completePostWorldStartup() {
        if (postWorldStartupComplete) {
            return;
        }
        postWorldStartupComplete = true;

        worlds.completeStartupLoad();
        loadFeatures();
        loadCommands();
        loadMinigames();
    }

    private void disableService(@Nullable BaseService service) {
        if (service != null) {
            service.onDisable();
        }
    }

    private @Nullable ChunkGenerator createBuiltInGenerator(String key, @Nullable String options) {
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        String generatorOptions = options == null ? "" : options.trim();

        return switch (normalizedKey) {
            case "void" -> new VoidGenerator();
            case "flat" -> FlatGenerator.fromOptions(generatorOptions);
            case "water" -> WaterGenerator.fromOptions(generatorOptions);
            default -> null;
        };
    }

    private BuiltInGeneratorSpec parseBuiltInGeneratorSpec(@Nullable String id) {
        if (id == null) {
            return new BuiltInGeneratorSpec("", "");
        }

        String raw = id.trim();
        if (raw.isEmpty()) {
            return new BuiltInGeneratorSpec("", "");
        }

        int separator = raw.indexOf(':');
        if (separator < 0) {
            return new BuiltInGeneratorSpec(raw, "");
        }

        String key = raw.substring(0, separator).trim();
        String options = raw.substring(separator + 1).trim();
        return new BuiltInGeneratorSpec(key, options);
    }

    private record BuiltInGeneratorSpec(String key, String options) {}

    /**
     * Get the main STEMCraft plugin instance.
     *
     * @return The STEMCraft plugin instance.
     */
    public static STEMCraft getPlugin() {
        return JavaPlugin.getPlugin(STEMCraft.class);
    }

    /**
     * Check if the server is running Paper.
     *
     * @return true if the server is running Paper, false otherwise.
     */
    private boolean isPaper() {
        try {
            Class.forName("io.papermc.paper.ServerBuildInfo");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Load STEMCraft Features within dev.stemcraft.features.
     */
    private void loadFeatures() {
        loadedFeatures.clear();
        iterateClasses(
                "dev/stemcraft/feature",
                BaseFeature.class,
                this::loadFeature,
                new Class<?>[]{STEMCraftAPI.class},
                api);
    }

    /**
     * Load a specific STEMCraft Feature if enabled.
     */
    private void loadFeature(BaseFeature feature) {
        if(!feature.isEnabled()) {
            debug("STEMCRAFT_FEATURE_DISABLED", "name", feature.id());
            return;
        }

        feature.onEnable();
        loadedFeatures.add(feature);
        debug("STEMCRAFT_FEATURE_LOADED", "name", feature.id());
    }

    /**
     * Load STEMCraft Commands within dev.stemcraft.command.
     */
    private void loadCommands() {
        loadedCommandIds.clear();
        iterateClasses(
                "dev/stemcraft/command",
                BaseCommand.class,
                this::loadCommand,
                new Class<?>[]{STEMCraft.class, STEMCraftAPI.class},
                this, api);
    }

    private void loadCommand(BaseCommand command) {
        command.onLoad();
        loadedCommandIds.add(command.getClass().getSimpleName());
    }

    /**
     * Load STEMCraft Minigames within dev.stemcraft.minigame.
     */
    private void loadMinigames() {
        loadedMiniGameIds.clear();
        iterateClasses(
                "dev/stemcraft/minigame",
                BaseMiniGame.class,
                this::loadMiniGame,
                new Class<?>[]{STEMCraftAPI.class},
                api);
    }

    private void loadMiniGame(BaseMiniGame miniGame) {
        miniGame.onLoad();
        loadedMiniGameIds.add(miniGame.getClass().getSimpleName());
    }

    /**
     * Get the plugin version.
     */
    public static String getVersion() {
        return getPlugin().getPluginMeta().getVersion();
    }

    /**
     * Get the current Minecraft version as major, minor, and patch components.
     *
     * @return a three-element array containing the parsed Minecraft version.
     */
    public static int[] getMinecraftVersion() {
        return parseMinecraftVersion(Bukkit.getMinecraftVersion());
    }

    static int[] parseMinecraftVersion(@Nullable String version) {
        int[] components = new int[] {0, 0, 0};
        if (version == null || version.isBlank()) {
            return components;
        }

        String normalizedVersion = version;
        int buildSeparator = normalizedVersion.indexOf(".build.");
        if (buildSeparator >= 0) {
            normalizedVersion = normalizedVersion.substring(0, buildSeparator);
        }

        Matcher matcher = VERSION_COMPONENT_PATTERN.matcher(normalizedVersion);
        int index = 0;
        while (matcher.find() && index < components.length) {
            components[index++] = Integer.parseInt(matcher.group());
        }
        return components;
    }

    /**
     * Iterate through classes in the JAR file under the specified path,
     * filtering by the given type and executing a callback for each instance.
     *
     * @param path The path within the JAR to scan.
     * @param typeFilter The class type to filter by.
     * @param callback The callback to execute for each instance.
     * @param constructorTypes The constructor argument types. (e.g. new Class<?>[]{STEMCraftAPI.class, WorldService.class}).
     * @param constructorArgs The constructor arguments. (e.g. this.api, this.worlds).
     * @param <T> The type of the class to filter by.
     */
    private <T> void iterateClasses(
            String path,
            Class<T> typeFilter,
            Consumer<T> callback,
            @Nullable Class<?>[] constructorTypes,
            Object... constructorArgs
    ) {
        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith(path) || !name.endsWith(".class")) continue;

                String className = name
                        .substring(0, name.length() - ".class".length())
                        .replace('/', '.');

                try {
                    Class<?> rawClass = Class.forName(className, true, getClassLoader());

                    if (!typeFilter.isAssignableFrom(rawClass) || rawClass == typeFilter) continue;
                    if (Modifier.isAbstract(rawClass.getModifiers())) continue;

                    @SuppressWarnings("unchecked")
                    Class<? extends T> castClass = (Class<? extends T>) rawClass;

                    if(constructorTypes == null) {
                        constructorTypes = new Class<?>[0];
                    }

                    Constructor<? extends T> constructor =
                            castClass.getDeclaredConstructor(constructorTypes);

                    constructor.setAccessible(true);
                    T instance = constructor.newInstance(constructorArgs);

                    callback.accept(instance);

                } catch (ReflectiveOperationException | RuntimeException ex) {
                    recordLoadFailure(className, ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    error("STEMCRAFT_ERROR_LOAD_CLASS", ex,
                            "class", className,
                            "error", ex.getMessage());
                }
            }
        } catch (IOException ex) {
            error("STEMCRAFT_ERROR_SCAN_CLASS", ex, "error", ex.getMessage());
        }
    }

    private void recordLoadFailure(String id, String reason) {
        loadFailures.put(id, reason == null ? "unknown error" : reason);
    }

    public ReloadSummary reloadStemCraft(boolean localesOnly) {
        boolean configReloaded = true;
        if (!localesOnly && configFile != null) {
            configReloaded = configFile.reload();
            debugging = configFile.getBoolean("debug", false);
        }

        messages = new MessageServiceImpl(this, api);
        messages.onEnable();

        if (resourcePack != null) {
            resourcePack.onReload();
        }

        if (locales == null) {
            locales = new LocaleServiceImpl(this, api);
            locales.onEnable();
        } else {
            locales.reload();
        }

        int reloadedFeatures = 0;
        if (!localesOnly) {
            if (motd != null) {
                motd.onReload();
            }
            if (firstJoin != null) {
                firstJoin.onReload();
            }
            if (selections != null) {
                selections.onReload();
            }
            if (placeholders != null) {
                placeholders.onReload();
            }
            for (BaseFeature feature : loadedFeatures) {
                try {
                    feature.onReload();
                    reloadedFeatures++;
                } catch (RuntimeException ex) {
                    recordLoadFailure("reload:" + feature.id(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    error("STEMCRAFT_ERROR_LOAD_CLASS", ex,
                        "class", feature.getClass().getName(),
                        "error", ex.getMessage());
                }
            }
        }

        return new ReloadSummary(configReloaded, true, reloadedFeatures);
    }

    public List<String> loadedFeatureIds() {
        return loadedFeatures.stream().map(BaseFeature::id).sorted().toList();
    }

    public List<String> loadedCommandIds() {
        return Collections.unmodifiableList(loadedCommandIds);
    }

    public List<String> loadedMiniGameIds() {
        return Collections.unmodifiableList(loadedMiniGameIds);
    }

    public Map<String, String> loadFailures() {
        return Collections.unmodifiableMap(loadFailures);
    }

    /**
     * Export a directory bundled within the JAR to the plugin's data folder.
     *
     * @param jarPath The root path within the JAR to export.
     * @param exportPath The subdirectory within the data folder to export to. Can be null to use the same as jarPath.
     */
    public void exportBundledDirectory(String jarPath, @Nullable String exportPath) {
        try (JarFile jf = new JarFile(getFile())) {
            String prefix = jarPath.endsWith("/") ? jarPath : jarPath + "/";
            String subDir = (exportPath == null || exportPath.isBlank()) ? jarPath : exportPath;

            File targetRoot = new File(getDataFolder(), subDir);
            if (!targetRoot.exists() && !targetRoot.mkdirs()) {
                getLogger().warning("Failed to create data folder: " + targetRoot);
                return;
            }

            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith(prefix) || entry.isDirectory()) {
                    continue;
                }

                // name is like "data-packs/minecraft-icons/config.yml"
                String relative = name.substring(prefix.length()); // "minecraft-icons/config.yml"
                File out = new File(targetRoot, relative);

                if (!out.getParentFile().exists() && !out.getParentFile().mkdirs()) {
                    getLogger().warning("Failed to create parent dir for " + out);
                    continue;
                }

                if (!out.exists()) {
                    try (InputStream in = getResource(name)) {
                        if (in == null) {
                            getLogger().warning("Bundled resource not found: " + name);
                            continue;
                        }

                        try (OutputStream outStream = Files.newOutputStream(out.toPath())) {
                            in.transferTo(outStream);
                        }
                    } catch (IOException ioEx) {
                        getLogger().warning("Failed to export bundled resource " + name + " -> " + out + ": " + ioEx.getMessage());
                    }
                }
            }
        } catch (IOException ex) {
            error("Failed to export bundled directory {dir}: {error}", ex, "dir", jarPath, "error", ex.getMessage());
        }
    }

    public void exportBundledDirectory(String jarRoot) {
        exportBundledDirectory(jarRoot, jarRoot);
    }

    // Messaging shortcuts
    private void debug(String message, Object... placeholders) {
        if (this.messages != null) {
            this.messages.debug(message, placeholders);
        } else {
            getLogger().fine(String.valueOf(message));
        }
    }

    private void log(String message, Object... placeholders) {
        if (this.messages != null) {
            this.messages.log(message, placeholders);
        } else {
            getLogger().info(String.valueOf(message));
        }
    }

    private void info(String message, Object... placeholders) {
        if (this.messages != null) {
            this.messages.info(message, placeholders);
        } else {
            getLogger().info(String.valueOf(message));
        }
    }

    private void warn(String message, Object... placeholders) {
        if (this.messages != null) {
            this.messages.warn(message, placeholders);
        } else {
            getLogger().warning(String.valueOf(message));
        }
    }

    private void error(String message, Object... placeholders) {
        if (this.messages != null) {
            this.messages.error(message, placeholders);
        } else {
            getLogger().severe(String.valueOf(message));
        }
    }

    private void error(String message, Throwable ex, Object... placeholders) {
        if (this.messages != null) {
            this.messages.error(message, ex, placeholders);
        } else {
            getLogger().severe(message + (ex != null ? ": " + ex.getMessage() : ""));
        }
    }

    private void success(String message, Object... placeholders) {
        if (this.messages != null) {
            this.messages.success(message, placeholders);
        } else {
            getLogger().info(String.valueOf(message));
        }
    }

    public record ReloadSummary(boolean configReloaded, boolean localesReloaded, int reloadedFeatures) { }

    private static void initializeApi(STEMCraft plugin) {
        api = new STEMCraftAPIImpl(plugin);
    }
}

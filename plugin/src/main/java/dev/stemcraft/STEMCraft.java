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
import dev.stemcraft.api.util.PermissionUtil;
import dev.stemcraft.command.BaseCommand;
import dev.stemcraft.service.*;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.chunkgen.FlatGenerator;
import dev.stemcraft.chunkgen.VoidGenerator;
import dev.stemcraft.feature.BaseFeature;
import dev.stemcraft.service.command.CommandServiceImpl;
import dev.stemcraft.service.message.MessageServiceImpl;
//import dev.stemcraft.service.minigame.MiniGameServiceImpl;
import dev.stemcraft.service.resourcepack.ResourcePackServiceImpl;
import dev.stemcraft.service.tabcompletion.TabCompleteServiceImpl;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@SuppressWarnings("SameParameterValue")
@Getter
@Accessors(fluent = true)
public final class STEMCraft extends JavaPlugin {
    private static STEMCraft instance;
    private static STEMCraftAPI api;
    private File cacheDir;

    private ChatServiceImpl chat;
    private CommandServiceImpl commands;
    private ConfigServiceImpl config;
    private DatabaseServiceImpl database;
    private EventServiceImpl events;
    private GatekeeperServiceImpl gatekeeper;
    private HologramServiceImpl holograms;
    private ItemServiceImpl items;
    private LocaleServiceImpl locales;
    private MessageServiceImpl messages;
//    private MiniGameServiceImpl minigames;
    private MotdServiceImpl motd;
    private PlayerServiceImpl players;
    private PunishmentServiceImpl punishments;
    private RecipeServiceImpl recipes;
    private RegionServiceImpl regions;
    private ResourcePackServiceImpl resourcePack;
    private TabCompleteServiceImpl tabComplete;
    private TaskServiceImpl tasks;
    private WebServiceImpl web;
    private WorldServiceImpl worlds;

    @Getter(AccessLevel.NONE)
    private ConfigFile configFile;

    private boolean isMaintenanceMode = false;

    @Getter
    private boolean debugging = false;
    private final String whiteListMessage = "This server is invite-only.";

    /** {@inheritDoc} */
    @Override
    public void onEnable() {
        instance = this;
        api = new STEMCraftAPIImpl(this);
        InstanceHolder.set(api, this);

        // Load pre-early services
        config = new ConfigServiceImpl(this, api);
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

        // Load early services
        messages = new MessageServiceImpl(this, api);
        locales = new LocaleServiceImpl(this, api);
        tasks = new TaskServiceImpl(this, api);

        messages.onEnable();
        locales.onEnable();
        tasks.onEnable();

        // Check dependencies
        Plugin we = getServer().getPluginManager().getPlugin("WorldEdit");
        if(we == null || !we.isEnabled()) {
            error("DEPENDENCY_WORLDEDIT_REQUIRED");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        debugging = configFile.getBoolean("debug", false);

        // Load managers
        chat = new ChatServiceImpl(this, api);
        commands = new CommandServiceImpl(this, api);
        database = new DatabaseServiceImpl(this, api);
        events = new EventServiceImpl(this, api);
        gatekeeper = new GatekeeperServiceImpl(this, api);
        holograms = new HologramServiceImpl(this, api);
        items = new ItemServiceImpl(this, api);
//        minigames = new MiniGameServiceImpl(this, api);
        motd = new MotdServiceImpl(this, api);
        players = new PlayerServiceImpl(this, api);
        punishments = new PunishmentServiceImpl(this, api);
        recipes = new RecipeServiceImpl(this, api);
        regions = new RegionServiceImpl(this, api);
        resourcePack = new ResourcePackServiceImpl(this, api);
        tabComplete = new TabCompleteServiceImpl(this, api);
        web = new WebServiceImpl(this, api);
        worlds = new WorldServiceImpl(this, api);

        chat.onEnable();
        commands.onEnable();
        database.onEnable();
        events.onEnable();
        gatekeeper.onEnable();
        holograms.onEnable();
        items.onEnable();
//        minigames.onEnable();
        motd.onEnable();
        players.onEnable();
        punishments.onEnable();
        recipes.onEnable();
        regions.onEnable();
        resourcePack.onEnable();
        tabComplete.onEnable();
        web.onEnable();
        worlds.onEnable();


        info("STEMCRAFT_ENABLED");

        // Register world generators
        worlds.generator().register("void", (options) -> new VoidGenerator());
        worlds.generator().register("flat",   FlatGenerator::fromOptions);
        worlds.generator().register("normal", cfg -> null);

        loadFeatures();
        loadCommands();
        loadMinigames();

        isMaintenanceMode = configFile.getBoolean("maintenance", false);

        api.commands().create("maintenance")
            .tabCompletion("on")
            .tabCompletion("off")
            .description("MAINTENANCE_DESCRIPTION")
            .usage("MAINTENANCE_USAGE")
            .permission("stemcraft.command.maintenance")
            .executor((unused, cmd, ctx) -> {
                if (ctx.args().isEmpty()) {
                    ctx.returnInfo("MAINTENANCE_STATUS", isMaintenanceMode ? "on" : "off");
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

                ctx.returnInfo("MAINTENANCE_STATUS", isMaintenanceMode ? "on" : "off");
            })
            .register(this);

        //noinspection UnstableApiUsage
        api.events().register(PlayerConnectionValidateLoginEvent.class, event -> {
            if (isMaintenanceMode) {
                UUID uuid = getUuid(event);

                if (uuid == null) {
                    // may be null (https://jd.papermc.io/paper/1.21.11/org/bukkit/profile/PlayerProfile.html)
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
        worlds.onDisable();
        web.onDisable();
        tabComplete.onDisable();
        resourcePack.onDisable();
        regions.onDisable();
        recipes.onDisable();
        punishments.onDisable();
        players.onDisable();
        motd.onDisable();
//        minigames.onDisable();
        items.onDisable();
        holograms.onDisable();
        gatekeeper.onDisable();
        events.onDisable();
        database.onDisable();
        commands.onDisable();
        chat.onDisable();

        tasks.onDisable();
        locales.onDisable();
        messages.onDisable();
    }

    public static STEMCraft getPlugin() {
        return instance;
    }

    /**
     * Load STEMCraft Features within dev.stemcraft.features.
     */
    private void loadFeatures() {
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
        if(feature.getConfigSection().getBoolean("enabled", true)) {
            debug("STEMCRAFT_FEATURE_DISABLED", "name", feature.id());
            return;
        }

        feature.onEnable();
        debug("STEMCRAFT_FEATURE_LOADED", "name", feature.id());
    }

    /**
     * Load STEMCraft Commands within dev.stemcraft.command.
     */
    private void loadCommands() {
        iterateClasses(
                "dev/stemcraft/command",
                BaseCommand.class,
                BaseCommand::onLoad,
                new Class<?>[]{STEMCraft.class, STEMCraftAPI.class},
                this, api);
    }

    /**
     * Load STEMCraft Minigames within dev.stemcraft.minigame.
     */
    private void loadMinigames() {
//        iterateClasses(
//                "dev/stemcraft/minigame",
//                BaseMinigame.class,
//                BaseMinigame::onLoad,
//                new Class<?>[]{STEMCraftAPI.class},
//                api);
    }

    /**
     * Get the plugin version.
     */
    public static String getVersion() {
        return instance.getPluginMeta().getVersion();
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

                } catch (ReflectiveOperationException ex) {
                    error("STEMCRAFT_ERROR_LOAD_CLASS", ex,
                            "class", className,
                            "error", ex.getMessage());
                }
            }
        } catch (IOException ex) {
            error("STEMCRAFT_ERROR_SCAN_CLASS", ex, "error", ex.getMessage());
        }
    }

    private <T> void iterateClasses(String path, Class<T> typeFilter, Consumer<T> callback) { iterateClasses(path, typeFilter, callback, null); }

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

                // name is like "resource-pack/minecraft-icons/config.yml"
                String relative = name.substring(prefix.length()); // "minecraft-icons/config.yml"
                File out = new File(targetRoot, relative);

                if (!out.getParentFile().exists() && !out.getParentFile().mkdirs()) {
                    getLogger().warning("Failed to create parent dir for " + out);
                    continue;
                }

                if (!out.exists()) {
                    saveResource(name, false); // never overwrite
                }
            }
        } catch (Exception ex) {
            error("Failed to export bundled directory {dir}: {error}", ex, "dir", jarPath, "error", ex.getMessage());
        }
    }

    public void exportBundledDirectory(String jarRoot) {
        exportBundledDirectory(jarRoot, jarRoot);
    }

    // Messaging shortcuts
    private void debug(String message, Object... placeholders) { this.messages.debug(message, placeholders); }
    private void log(String message, Object... placeholders) { this.messages.log(message, placeholders); }
    private void info(String message, Object... placeholders) { this.messages.info(message, placeholders); }
    private void warn(String message, Object... placeholders) { this.messages.warn(message, placeholders); }
    private void error(String message, Object... placeholders) { this.messages.error(message, placeholders); }
    private void error(String message, Throwable ex, Object... placeholders) { this.messages.error(message, ex, placeholders); }
    private void success(String message, Object... placeholders) { this.messages.success(message, placeholders); }
}

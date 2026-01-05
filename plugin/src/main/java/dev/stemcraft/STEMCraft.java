package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.event.server.MaintenanceModeChangedEvent;
import dev.stemcraft.api.service.hologram.HologramService;
import dev.stemcraft.api.service.locale.LocaleService;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.api.service.motd.MotdService;
import dev.stemcraft.api.service.player.PlayerLogService;
import dev.stemcraft.api.service.punishment.PunishmentService;
import dev.stemcraft.api.service.web.WebService;
import dev.stemcraft.api.service.tabcomplete.TabCompleteService;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.command.STEMCraftCommandImpl;
import dev.stemcraft.service.*;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.chunkgen.FlatGenerator;
import dev.stemcraft.chunkgen.VoidGenerator;
import dev.stemcraft.features.BaseFeature;
import dev.stemcraft.service.command.CommandServiceImpl;
import dev.stemcraft.service.minigame.MiniGameServiceImpl;
import dev.stemcraft.service.PlayerServiceImpl;
import dev.stemcraft.service.tabcompletion.TabCompleteServiceImpl;
import dev.stemcraft.service.world.WorldServiceImpl;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Getter
@Accessors(fluent = true)
public final class STEMCraft extends JavaPlugin {
    private static STEMCraft instance;
    private static STEMCraftAPI api;
    private File cacheDir;

    private CommandServiceImpl commands;
    private ConfigServiceImpl config;
    private EventServiceImpl events;

    private LocaleService locale;
    private MessageService messengerService;
    private PlayerLogService playerLogService;
    private WorldService worldService;
    private MotdService motd;
    private WebService webService;
    private TabCompleteService tabCompleteService;
    private dev.stemcraft.api.service.task.TaskService taskService;
    private PunishmentService punishmentService;
    private HologramService hologramService;
    private ItemServiceImpl items;
    private ResourcePackManager resourcePackService;
    private RegionServiceImpl regions;
    private ChatManager chatService;
    private GateKeeperServiceImpl gateKeeperService;
    private MiniGameServiceImpl miniGameService;

//    private YamlConfiguration config;
    @Getter(AccessLevel.NONE)
    private ConfigFile configFile;

    private boolean isMaintenanceMode = false;

    private boolean debugging = false;
    private String whiteListMessage = "This server is invite-only.";

    @Override
    public void onEnable() {
        instance = this;
        api = new STEMCraftAPIImpl(this);
        InstanceHolder.set(api, this);

        config = new ConfigServiceImpl();

        // Load configuration
        configFile = api.config().load("config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }


        // Load early managers
        messengerService = new MessengerManager(this);
        locale = new LocaleServiceImpl(this);
        taskService = new TaskServiceImpl(this);

        messengerService.onEnable();
        locale.onEnable();
        taskService.onEnable();

        // Check dependencies
        Plugin we = getServer().getPluginManager().getPlugin("WorldEdit");
        if(we == null || !we.isEnabled()) {
            error("DEPENDENCY_WORLDEDIT_REQUIRED");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Setup API

        debugging = configFile.getBoolean("debug", false);

        loadUtilities();


        // Load managers
        commands = new CommandServiceImpl(this, api);
        events = new EventServiceImpl(this, api);

        playerLogService = new PlayerServiceImpl(this);
        worldService = new WorldServiceImpl(this);
        motd = new MotdServiceImpl(this, api);
        webService = new WebServiceImpl(this);
        tabCompleteService = new TabCompleteServiceImpl(this);
        punishmentService = new PunishmentManager(this);
        hologramService = new HologramServiceImpl(this);
        items = new ItemServiceImpl(this, api);
        resourcePackService = new ResourcePackManager(this);
        regions = new RegionServiceImpl(this, api);
        chatService = new ChatManager(this);
        gateKeeperService = new GateKeeperServiceImpl(this);
        miniGameService = new MiniGameServiceImpl(this);

        commands.onEnable();
        events.onEnable();

        playerLogService.onEnable();
        worldService.onEnable();
        motd.onEnable();
        webService.onEnable();
        tabCompleteService.onEnable();
        punishmentService.onEnable();
        hologramService.onEnable();
        items.onEnable();
        resourcePackService.onEnable();
        regions.onEnable();
        chatService.onEnable();
        gateKeeperService.onEnable();
        miniGameService.onEnable();

        info("STEMCRAFT_ENABLED");

        worldService.registerGenerator("void", (options) -> new VoidGenerator());
        worldService.registerGenerator("flat",   FlatGenerator::fromOptions);       // e.g., "grass_block;dirt:3;bedrock"
        worldService.registerGenerator("normal", cfg -> null);               // null => vanilla normal

        loadFeatures();
        loadCommands();
        loadminigames();

        whiteListMessage = configFile.getString("whitelist_message", whiteListMessage);
        registerEvent(AsyncPlayerPreLoginEvent.class, event -> {
            if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                        MiniMessage.miniMessage().deserialize(whiteListMessage)
                );
            }
        });

        registerEvent(PlayerLoginEvent.class, event -> {
            if (event.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST) {
                event.disallow(
                        PlayerLoginEvent.Result.KICK_WHITELIST,
                        MiniMessage.miniMessage().deserialize(whiteListMessage)
                );
            }
        });

        isMaintenanceMode = configFile.getBoolean("maintenance", false);

        registerCommand("maintenance")
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

        registerEvent(PlayerLoginEvent.class, event -> {
            if (isMaintenanceMode) {
                boolean isAdmin = event.getPlayer().hasPermission("stemcraft.maintenance.bypass");

                if (!isAdmin) {
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Server is under maintenance. Please try again later.");
                }
            }
        });
    }

    @Override
    public void onDisable() {

        miniGameService.onDisable();
        gateKeeperService.onDisable();
        regions.onDisable();
        items.onDisable();
        hologramService.onDisable();
        webService.onDisable();
        motd.onDisable();
        worldService.onDisable();
        playerLogService.onDisable();

        events.onDisable();
        commands.onDisable();

        taskService.onDisable();
        locale.onDisable();
        messengerService.onDisable();
    }

    public static STEMCraft getPlugin() {
        return instance;
    }

    /**
     * Load STEMCraft Features within dev.stemcraft.features
     */
    private void loadFeatures() {
        iterateClasses("dev/stemcraft/features/", BaseFeature.class, instance -> {
            String featureConfigBase = instance.getConfigBase();

            if (!configFile.getBoolean(featureConfigBase + ".enabled", true)) {
                debug("STEMCRAFT_FEATURE_DISABLED", "name", instance.getId());
                return;
            }

            instance.onEnable(api);
            debug("STEMCRAFT_FEATURE_LOADED", "name", instance.getId());
        });
    }

    /**
     * Load STEMCraft Utilities within dev.stemcraft.api.utils
     */
    private void loadUtilities() {
        iterateClasses("dev/stemcraft/api/util", STEMCraftUtil.class, STEMCraftUtil::onLoad);
    }

    private void loadCommands() {
        iterateClasses("dev/stemcraft/command", STEMCraftCommandImpl.class, instance -> {
            instance.onLoad(STEMCraft.instance);
        });
    }

    private void loadminigames() {
        iterateClasses("dev/stemcraft/minigames", STEMCraftMiniGame.class, instance -> {
            instance.onLoad(STEMCraft.instance);
        });
    }

    /**
     * Get the plugin version.
     */
    public static String getVersion() {
        return instance.getDescription().getVersion();
    }

    private <T> void iterateClasses(String path,
                                    Class<T> typeFilter,
                                    Consumer<T> callback) {
        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith(path) || !name.endsWith(".class")) {
                    continue;
                }

                String className = name
                        .substring(0, name.length() - ".class".length())
                        .replace('/', '.');

                try {
                    Class<?> rawClass = Class.forName(className, true, getClassLoader());

                    if (!typeFilter.isAssignableFrom(rawClass) || rawClass == typeFilter) {
                        continue;
                    }
                    if (Modifier.isAbstract(rawClass.getModifiers())) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Class<? extends T> castClass = (Class<? extends T>) rawClass;

                    T instance = castClass.getDeclaredConstructor().newInstance();

                    callback.accept(instance);

                } catch (ReflectiveOperationException ex) {
                    error("STEMCRAFT_ERROR_LOAD_CLASS", ex, "class", className, "error", ex.getMessage());
                }
            }
        } catch (IOException ex) {
            error("STEMCRAFT_ERROR_SCAN_CLASS", ex, "error", ex.getMessage());
        }
    }

    public void debug(String message, Object... placeholders) {
        if(debugging) {
            messengerService.log(message, placeholders);
        }
    }

    public void log(String message, Object... placeholders) {
        messengerService.log(message, placeholders);
    }

    public void info(String message, Object... placeholders) {
        messengerService.info(message, placeholders);
    }

    public void warn(String message, Object... placeholders) {
        messengerService.warn(message, placeholders);
    }

    public void error(String message, Object... placeholders) {
        messengerService.error(message, placeholders);
    }

    public void error(String message, Throwable ex, Object... placeholders) {
        messengerService.error(message, ex, placeholders);
    }

    public void success(String message, Object... placeholders) {
        messengerService.success(message, placeholders);
    }

    public void exportBundledDirectory(String jarRoot, String dataSubDir) {
        try (JarFile jf = new JarFile(getFile())) {
            String prefix = jarRoot.endsWith("/") ? jarRoot : jarRoot + "/";
            File targetRoot = new File(getDataFolder(), dataSubDir);

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
            error("Failed to export bundled directory {dir}: {error}", ex, "dir", jarRoot, "error", ex.getMessage());
        }
    }

    public void exportBundledDirectory(String jarRoot) {
        exportBundledDirectory(jarRoot, jarRoot);
    }

    public File getCacheDir() {
        if (this.cacheDir == null) {
            this.cacheDir = new File(this.getDataFolder(), "cache");
            if (!this.cacheDir.exists()) {
                this.cacheDir.mkdirs();
            }
        }
        return this.cacheDir;
    }

    public FileConfiguration getCacheConfig(String fileName) {
        try {
            File dir = this.getCacheDir();
            File backFile = new File(dir, fileName);
            if (!backFile.exists()) {
                if(!backFile.createNewFile()) {
                    throw new IOException("Failed to create cache file: " + backFile.getAbsolutePath());
                }
            }

            return YamlConfiguration.loadConfiguration(backFile);
        } catch (IOException ex) {
            error(ex.getMessage());
        }

        return new YamlConfiguration();
    }

    public void saveCacheConfig(String fileName, FileConfiguration config) {
        try {
            File dir = this.getCacheDir();
            File backFile = new File(dir, fileName);
            config.save(backFile);
        } catch (IOException ex) {
            error(ex.getMessage());
        }
    }
}

package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.events.STEMCraftEventHandler;
import dev.stemcraft.api.services.hologram.HologramService;
import dev.stemcraft.api.services.task.TaskService;
import dev.stemcraft.api.services.punishment.PunishmentService;
import dev.stemcraft.api.services.web.WebService;
import dev.stemcraft.api.services.tabcomplete.TabCompleteService;
import dev.stemcraft.api.utils.STEMCraftUtil;
import dev.stemcraft.commands.STEMCraftCommandImpl;
import dev.stemcraft.managers.*;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.services.*;
import dev.stemcraft.chunkgen.FlatGenerator;
import dev.stemcraft.chunkgen.VoidGenerator;
import dev.stemcraft.features.STEMCraftFeature;
//import dev.stemcraft.minigames.STEMCraftMiniGame;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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

    private LocaleService localeService;
    private MessengerService messengerService;
    private PlayerLogService playerLogService;
    private WorldService worldService;
    private MOTDService motdService;
    private WebService webService;
    private TabCompleteService tabCompleteService;
    private TaskService taskService;
    private PunishmentService punishmentService;
    private HologramService hologramService;
    private ItemService itemService;
    private ResourcePackManager resourcePackService;
    private RegionManager regionService;
    private ChatManager chatService;
    private GateKeeperManager gateKeeperService;

    private YamlConfiguration config;
    @Getter(AccessLevel.NONE)
    private File configFile;

    private boolean inMaintenanceMode = false;

    private boolean debugging = false;
    private String whiteListMessage = "This server is invite-only.";

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        configFile = new File(instance.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Load early managers
        messengerService = new MessengerManager(this);
        localeService = new LocaleManager(this);
        taskService = new TaskManager(this);

        messengerService.onEnable();
        localeService.onEnable();
        taskService.onEnable();

        // Check dependencies
        Plugin we = getServer().getPluginManager().getPlugin("WorldEdit");
        if(we == null || !we.isEnabled()) {
            error("DEPENDENCY_WORLDEDIT_REQUIRED");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Setup API
        api = new STEMCraftApiManager(this);
        InstanceHolder.set(api, this);

        debugging = config.getBoolean("debug", false);

        loadUtilities();


        // Load managers
        playerLogService = new PlayerLogManager(this);
        worldService = new WorldManager(this);
        motdService = new MOTDManager(this);
        webService = new WebManager(this);
        tabCompleteService = new TabCompleteManager(this);
        punishmentService = new PunishmentManager(this);
        hologramService = new HologramManager(this);
        itemService = new ItemManager(this);
        resourcePackService = new ResourcePackManager(this);
        regionService = new RegionManager(this);
        chatService = new ChatManager(this);
        gateKeeperService = new GateKeeperManager(this);

        playerLogService.onEnable();
        worldService.onEnable();
        motdService.onEnable();
        webService.onEnable();
        tabCompleteService.onEnable();
        punishmentService.onEnable();
        hologramService.onEnable();
        itemService.onEnable();
        resourcePackService.onEnable();
        regionService.onEnable();
        chatService.onEnable();
        gateKeeperService.onEnable();

        info("STEMCRAFT_ENABLED");

        worldService.registerGenerator("void", (options) -> new VoidGenerator());
        worldService.registerGenerator("flat",   FlatGenerator::fromOptions);       // e.g., "grass_block;dirt:3;bedrock"
        worldService.registerGenerator("normal", cfg -> null);               // null => vanilla normal

        loadFeatures();
        loadCommands();
        loadMiniGames();

        whiteListMessage = config.getString("whitelist_message", whiteListMessage);
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

        inMaintenanceMode = config().getBoolean("maintenance", false);

        registerCommand("maintenance")
            .addTabCompletion("on")
            .addTabCompletion("off")
            .setDescription("MAINTENANCE_DESCRIPTION")
            .setUsage("MAINTENANCE_USAGE")
            .setPermission("stemcraft.command.maintenance")
            .setExecutor((unused, cmd, ctx) -> {
                if (ctx.args().isEmpty()) {
                    ctx.returnInfo("MAINTENANCE_STATUS", inMaintenanceMode ? "on" : "off");
                }

                String state = ctx.args().getFirst().toLowerCase();
                if ("on".equals(state)) {
                    inMaintenanceMode = true;
                } else if ("off".equals(state)) {
                    inMaintenanceMode = false;
                } else {
                    ctx.returnError("MAINTENANCE_INVALID_OPTION");
                }

                config().set("maintenance", inMaintenanceMode);
                saveConfig();
                ctx.returnInfo("MAINTENANCE_STATUS", inMaintenanceMode ? "on" : "off");
            })
            .register(this);

        registerEvent(PlayerLoginEvent.class, event -> {
            if (inMaintenanceMode) {
                boolean isAdmin = event.getPlayer().hasPermission("stemcraft.maintenance.bypass");

                if (!isAdmin) {
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Server is under maintenance. Please try again later.");
                }
            }
        });
    }

    @Override
    public void onDisable() {

        gateKeeperService.onDisable();
        regionService.onDisable();
        itemService.onDisable();
        hologramService.onDisable();
        webService.onDisable();
        motdService.onDisable();
        worldService.onDisable();
        playerLogService.onDisable();

        taskService.onDisable();
        localeService.onDisable();
        messengerService.onDisable();
    }

    public static STEMCraft getInstance() {
        return instance;
    }

    public void configSave() {
        try {
            instance.config().save(configFile);
        } catch(Exception ex) {
            error("STEMCRAFT_ERROR_SAVING_CONFIG", ex);
        }
    }

    /**
     * Load STEMCraft Features within dev.stemcraft.features
     */
    private void loadFeatures() {
        iterateClasses("dev/stemcraft/features/", STEMCraftFeature.class, instance -> {
            String featureConfigBase = instance.getConfigBase();

            if (!config.getBoolean(featureConfigBase + ".enabled", true)) {
                debug("STEMCRAFT_FEATURE_DISABLED", "name", instance.getName());
                return;
            }

            instance.onEnable(api);
            debug("STEMCRAFT_FEATURE_LOADED", "name", instance.getName());
        });
    }

    /**
     * Load STEMCraft Utilities within dev.stemcraft.api.utils
     */
    private void loadUtilities() {
        iterateClasses("dev/stemcraft/api/utils", STEMCraftUtil.class, STEMCraftUtil::onLoad);
    }

    private void loadCommands() {
        iterateClasses("dev/stemcraft/commands", STEMCraftCommandImpl.class, instance -> {
            instance.onLoad(STEMCraft.instance);
        });
    }

    private void loadMiniGames() {
//        iterateClasses("dev/stemcraft/minigames", STEMCraftMiniGame.class, instance -> {
//            instance.onLoad(STEMCraft.instance);
//        });
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

    public <T extends Event> Listener registerEvent(Class<T> event, STEMCraftEventHandler<T> callback, EventPriority priority, boolean ignoreCancelled) {
        Listener listener = new Listener() {};

        instance.getServer().getPluginManager().registerEvent(event, listener, priority, (ignored, rawEvent) -> {
            if(event.isInstance(rawEvent)) {
                T castedEvent = event.cast(rawEvent);
                if (ignoreCancelled && rawEvent instanceof Cancellable c && c.isCancelled()) {
                    return;
                }

                callback.handle(castedEvent);
            }
        }, instance, ignoreCancelled);

        return listener;
    }

    public <T extends Event> void registerEvent(Class<T> event, STEMCraftEventHandler<T> callback) {
        registerEvent(event, callback, EventPriority.NORMAL, false);
    }

    public STEMCraftCommand registerCommand(String label) {
        return api.registerCommand(label);
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

    public boolean isInMaintenanceMode() {
        return inMaintenanceMode;
    }

    public FileConfiguration getConfig(String fileName) {
        File file = new File(this.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    return null;
                }
            } catch (IOException e) {
                this.error("Could not create config file: " + fileName, e);
                return null;
            }
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    public void saveConfig(String fileName, FileConfiguration config) {
        File file = new File(this.getDataFolder(), fileName);
        try {
            config.save(file);
        } catch (IOException e) {
            this.error("Could not save config file: " + fileName, e);
        }
    }
}

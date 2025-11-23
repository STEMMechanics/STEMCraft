package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.events.STEMCraftEventHandler;
import dev.stemcraft.api.services.persistenttimer.PersistentTimerService;
import dev.stemcraft.api.services.web.WebService;
import dev.stemcraft.api.tabcomplete.TabCompleteService;
import dev.stemcraft.api.utils.STEMCraftUtil;
import dev.stemcraft.commands.STEMCraftCommandImpl;
import dev.stemcraft.managers.*;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.services.*;
import dev.stemcraft.chunkgen.FlatGenerator;
import dev.stemcraft.chunkgen.VoidGenerator;
import dev.stemcraft.features.STEMCraftFeature;
import dev.stemcraft.tabcomplete.TabCompleteManager;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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

    private LocaleService localeService;
    private MessengerService messengerService;
    private PlayerLogService playerLogService;
    private WorldService worldService;
    private MOTDService motdService;
    private WebService webService;
    private TabCompleteService tabCompleteService;
    private PersistentTimerService persistentTimerService;

    private YamlConfiguration config;
    @Getter(AccessLevel.NONE)
    private File configFile;

    private boolean debugging = false;


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
        persistentTimerService = new PersistentTimerManager(this);

        messengerService.onEnable();
        localeService.onEnable();
        persistentTimerService.onEnable();

        // Check dependencies
        Plugin we = getServer().getPluginManager().getPlugin("WorldEdit");
        if(we == null || !we.isEnabled()) {
            error("WorldEdit plugin not found or not enabled! STEMCraft requires WorldEdit to function.");
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

        playerLogService.onEnable();
        worldService.onEnable();
        motdService.onEnable();
        webService.onEnable();

        info("STEMCraft enabled");

        worldService.registerGenerator("void", (options) -> new VoidGenerator());
        worldService.registerGenerator("flat",   FlatGenerator::fromOptions);       // e.g., "grass_block;dirt:3;bedrock"
        worldService.registerGenerator("normal", cfg -> null);               // null => vanilla normal

        loadFeatures();
        loadCommands();
    }

    @Override
    public void onDisable() {
        webService.onDisable();
        motdService.onDisable();
        worldService.onDisable();
        playerLogService.onDisable();

        persistentTimerService.onDisable();
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
            error("Could not save the config file to disk", ex);
        }
    }

    /**
     * Load STEMCraft Features within dev.stemcraft.features
     */
    private void loadFeatures() {
        iterateClasses("dev/stemcraft/features/", STEMCraftFeature.class, instance -> {
            String featureConfigBase = instance.getConfigBase();

            if (!config.getBoolean(featureConfigBase + ".enabled", true)) {
                info("Feature {name} disabled in config", "name", instance.getName());
                return;
            }

            instance.onEnable(api);
            info("Feature {name} loaded", "name", instance.getName());
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
                    error("Failed to load class " + className + ": " + ex.getMessage(), ex);
                }
            }
        } catch (IOException ex) {
            error("Failed to scan classes in plugin jar: " + ex.getMessage(), ex);
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

    public <T extends Event> Listener registerEvent(Class<T> event, STEMCraftEventHandler<T> callback) { return registerEvent(event, callback, EventPriority.NORMAL, false); }

    public STEMCraftCommand registerCommand(String label) {
        return api.registerCommand(label);
    }

    public void debug(String message, String... placeholders) {
        if(debugging) {
            messengerService.log(message, placeholders);
        }
    }

    public void log(String message, String... placeholders) {
        messengerService.log(message, placeholders);
    }

    public void info(String message, String... placeholders) {
        messengerService.info(message, placeholders);
    }

    public void warn(String message, String... placeholders) {
        messengerService.warn(message, placeholders);
    }

    public void error(String message, String... placeholders) {
        messengerService.error(message, placeholders);
    }

    public void error(String message, Throwable ex, String... placeholders) {
        messengerService.error(message, ex, placeholders);
    }

    public void success(String message, String... placeholders) {
        messengerService.success(message, placeholders);
    }
}

package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.events.STEMCraftEventHandler;
import dev.stemcraft.api.services.web.WebService;
import dev.stemcraft.api.utils.STEMCraftUtil;
import dev.stemcraft.commands.STEMCraftCommandImpl;
import dev.stemcraft.managers.*;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.services.*;
import dev.stemcraft.chunkgen.FlatGenerator;
import dev.stemcraft.chunkgen.VoidGenerator;
import dev.stemcraft.features.STEMCraftFeature;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.List;
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

    private YamlConfiguration config;

    private boolean debugging = false;


    @Override
    public void onEnable() {
        instance = this;

        // Check dependencies
        Plugin we = getServer().getPluginManager().getPlugin("WorldEdit");
        if(we == null || !we.isEnabled()) {
            error("WorldEdit plugin not found or not enabled! STEMCraft requires WorldEdit to function.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Load configuration
        File configFile = new File(instance.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Setup API
        api = new STEMCraftApiManager(this);
        InstanceHolder.set(api, this);

        debugging = config.getBoolean("debug", false);

        loadUtilities();


        // Load managers
        messengerService = new MessengerManager(this);
        localeService = new LocaleManager(this);
        playerLogService = new PlayerLogManager(this);
        worldService = new WorldManager(this);
        motdService = new MOTDManager(this);
        webService = new WebManager(this);

        messengerService.onEnable();
        localeService.onEnable();
        playerLogService.onEnable();
        worldService.onEnable();
        motdService.onEnable();
        webService.onEnable();

        info("STEMCraft enabled");

        worldService.registerGenerator("void", (options) -> new VoidGenerator());
        worldService.registerGenerator("flat",   FlatGenerator::fromOptions);       // e.g., "grass_block;dirt:3;bedrock"
        worldService.registerGenerator("normal", cfg -> null);               // null => vanilla normal

        loadFeatures();
        registerCommands();
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }

    private static class STEMCraftCommand extends Command implements PluginIdentifiableCommand {
        private final Plugin plugin;
        private final CommandExecutor executor;
        @Setter
        private TabCompleter completer;

        public STEMCraftCommand(String name, Plugin plugin, CommandExecutor executor) {
            super(name);
            this.plugin = plugin;
            this.executor = executor;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!testPermission(sender)) {
                return true;
            }
            return executor.onCommand(sender, this, label, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (completer != null) {
                return completer.onTabComplete(sender, this, alias, args);
            }
            return super.tabComplete(sender, alias, args);
        }

        @Override
        public @NotNull Plugin getPlugin() {
            return plugin;
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
            onLoad();
        });
    }







    private void registerCommands() {
        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (!entry.getName().startsWith("dev/stemcraft/commands/")
                        || !entry.getName().endsWith(".class")) {
                    continue;
                }

                String className = entry.getName()
                        .replace('/', '.')
                        .substring(0, entry.getName().length() - ".class".length());

                try {
                    Class<?> clazz = Class.forName(className);

                    if (!dev.stemcraft.api.commands.STEMCraftCommand.class.isAssignableFrom(clazz)) continue;
                    if (Modifier.isAbstract(clazz.getModifiers())) continue;

                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    Method onRegisterMethod = clazz.getMethod("onRegister", STEMCraft.class);
                    onRegisterMethod.invoke(instance, this);

                } catch (ReflectiveOperationException ex) {
                    error("Failed to load command " + className);
                }
            }
        } catch (IOException ex) {
            error("Failed to scan commands in plugin jar");
        }
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

                    if (!typeFilter.isAssignableFrom(rawClass)) {
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

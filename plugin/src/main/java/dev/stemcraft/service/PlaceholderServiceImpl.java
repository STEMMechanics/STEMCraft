package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.placeholder.PlaceholderService;
import dev.stemcraft.service.placeholderapi.PlaceholderApiSupport;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class PlaceholderServiceImpl extends BaseService implements PlaceholderService {
    private static final String PLACEHOLDER_API_PLUGIN = "PlaceholderAPI";
    private static final String BRIDGE_CLASS = "dev.stemcraft.service.placeholderapi.PlaceholderApiBridge";

    private PlaceholderApiSupport bridge;

    public PlaceholderServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        api.events().register(PluginEnableEvent.class, event -> {
            if (PLACEHOLDER_API_PLUGIN.equals(event.getPlugin().getName())) {
                enableBridgeIfAvailable();
            }
        });

        api.events().register(PluginDisableEvent.class, event -> {
            if (PLACEHOLDER_API_PLUGIN.equals(event.getPlugin().getName())) {
                disableBridge();
            }
        });

        enableBridgeIfAvailable();
    }

    @Override
    public void onDisable() {
        disableBridge();
    }

    @Override
    public void onReload() {
        super.onReload();
        enableBridgeIfAvailable();
    }

    @Override
    public boolean isAvailable() {
        enableBridgeIfAvailable();
        return bridge != null;
    }

    @Override
    public @Nullable String apply(@Nullable OfflinePlayer player, @Nullable String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String rendered = api.messages().tokens().apply(text);
        if (bridge != null || isPlaceholderApiPluginEnabled()) {
            enableBridgeIfAvailable();
        }

        if (bridge != null) {
            rendered = bridge.apply(player, rendered);
        }

        return rendered;
    }

    @Override
    public @NotNull List<String> apply(@Nullable OfflinePlayer player, @NotNull List<String> lines) {
        List<String> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(apply(player, line));
        }
        return rendered;
    }

    private void enableBridgeIfAvailable() {
        if (bridge != null || !isPlaceholderApiPluginEnabled()) {
            return;
        }

        try {
            Class<?> clazz = Class.forName(BRIDGE_CLASS, true, getClass().getClassLoader());
            Constructor<?> constructor = clazz.getDeclaredConstructor(STEMCraftAPI.class);
            Object instance = constructor.newInstance(api);
            if (!(instance instanceof PlaceholderApiSupport support)) {
                plugin.getLogger().warning("[placeholders] Failed to initialize PlaceholderAPI bridge: invalid bridge type");
                return;
            }

            support.enable();
            bridge = support;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("[placeholders] Failed to initialize PlaceholderAPI bridge: " + ex.getMessage());
            bridge = null;
        }
    }

    private void disableBridge() {
        if (bridge == null) {
            return;
        }

        bridge.disable();
        bridge = null;
    }

    private boolean isPlaceholderApiPluginEnabled() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLACEHOLDER_API_PLUGIN);
        return plugin != null && plugin.isEnabled();
    }
}

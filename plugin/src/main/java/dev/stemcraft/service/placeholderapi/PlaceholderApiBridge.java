package dev.stemcraft.service.placeholderapi;

import dev.stemcraft.api.STEMCraftAPI;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderApiBridge implements PlaceholderApiSupport {
    private final PlaceholderApiService expansion;

    public PlaceholderApiBridge(STEMCraftAPI api) {
        this.expansion = new PlaceholderApiService(api);
    }

    @Override
    public void enable() {
        if (!expansion.isRegistered()) {
            expansion.register();
        }
    }

    @Override
    public void disable() {
        if (expansion.isRegistered()) {
            expansion.unregister();
        }
    }

    @Override
    public String apply(@Nullable OfflinePlayer player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}

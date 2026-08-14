package dev.stemcraft.api.service.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Applies bracket properties supplied when a custom item is created. */
@FunctionalInterface
public interface CustomItemPropertyHandler {
    /**
     * Applies properties such as {@code animal=chicken} to a newly-created item.
     *
     * @param item newly-created custom item
     * @param properties immutable property map
     * @throws IllegalArgumentException when a property or value is invalid
     */
    void apply(@NotNull ItemStack item, @NotNull Map<String, String> properties);
}

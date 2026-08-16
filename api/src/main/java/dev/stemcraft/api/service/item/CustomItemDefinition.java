package dev.stemcraft.api.service.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Map;

public record CustomItemDefinition(
    @NotNull String id,
    @NotNull ItemStack template,
    @NotNull CustomItemPlacementMode placementMode,
    @Nullable String managedObjectType,
    @Nullable CustomItemClientDefinition clients,
    @NotNull Map<String, CustomItemClientDefinition> visualStates
) {
    public CustomItemDefinition(@NotNull String id, @NotNull ItemStack template,
                                @NotNull CustomItemPlacementMode placementMode,
                                @Nullable String managedObjectType,
                                @Nullable CustomItemClientDefinition clients) {
        this(id, template, placementMode, managedObjectType, clients, Map.of());
    }

    public CustomItemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(placementMode, "placementMode");
        visualStates = visualStates == null ? Map.of() : Map.copyOf(visualStates);
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        template = template.clone();
        if (placementMode != CustomItemPlacementMode.MANAGED) {
            managedObjectType = null;
        } else if (managedObjectType == null || managedObjectType.isBlank()) {
            throw new IllegalArgumentException("managedObjectType must be set for MANAGED placement");
        }
    }
}

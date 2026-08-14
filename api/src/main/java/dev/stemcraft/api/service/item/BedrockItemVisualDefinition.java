package dev.stemcraft.api.service.item;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record BedrockItemVisualDefinition(
    @NotNull String identifier,
    @NotNull String icon,
    @NotNull String texturePath,
    @NotNull String displayName
) {
    public BedrockItemVisualDefinition {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(texturePath, "texturePath");
        Objects.requireNonNull(displayName, "displayName");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        if (icon.isBlank()) {
            throw new IllegalArgumentException("icon must not be blank");
        }
        if (texturePath.isBlank()) {
            throw new IllegalArgumentException("texturePath must not be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
    }
}

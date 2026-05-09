package dev.stemcraft.api.service.resourcepack.generator;

import dev.stemcraft.api.config.ConfigSectionView;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Convenience base class for generators that want a stored id and config
 * reference.
 */
public abstract class AbstractResourcePackGenerator implements ResourcePackGenerator {
    private final String id;
    protected ConfigSectionView config;

    protected AbstractResourcePackGenerator(@NotNull String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public final @NotNull String id() {
        return id;
    }

    @Override
    public void onLoad(@NotNull ConfigSectionView config) {
        this.config = config;
    }

    protected final @NotNull ConfigSectionView config() {
        return Objects.requireNonNull(config, "config");
    }
}

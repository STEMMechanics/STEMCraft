/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.api.service.config;

import dev.stemcraft.api.config.ConfigFile;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.util.function.Consumer;

/**
 * Service for managing configuration files.
 */
public interface ConfigService {

    /**
     * Retrieves a configuration file by name.
     *
     * @param name The name of the configuration file.
     * @param createIfNotExist Whether to create the file if it does not exist.
     * @return The configuration file object.
     */
    @Nullable ConfigFile load(@NotNull String name, boolean createIfNotExist);
    default @Nullable ConfigFile load(@NotNull String name) { return load(name, true); }

    @Nullable ConfigFile load(@NotNull File parent, @NotNull String name, boolean createIfNotExist);
    default @Nullable ConfigFile load(@NotNull File parent, @NotNull String name) { return load(parent, name, true); }

    @Nullable ConfigFile load(@NotNull File file, boolean createIfNotExist);
    default @Nullable ConfigFile load(@NotNull File file) { return load(file, true); }

    /**
     * Opts a loaded file into external file monitoring. Monitoring is shared by
     * the service and is not implemented by individual consumers.
     */
    default void watch(@NotNull String name) { }

    default void watch(@NotNull File file) { watch(file.getPath()); }

    default void watch(@NotNull ConfigFile file) { watch(file.getName()); }

    /** Registers for effective changes in every currently and subsequently loaded file. */
    default ConfigSubscription onAnyChange(@NotNull Consumer<ConfigChangeEvent> listener) {
        return ConfigSubscription.noop();
    }

    /** Registers a listener for all effective changes to a loaded file. */
    default ConfigSubscription onChange(@NotNull String name,
                                        @NotNull Consumer<ConfigChangeEvent> listener) {
        return ConfigSubscription.noop();
    }

    /** Registers a listener for changes affecting one path in a loaded file. */
    default ConfigSubscription onChange(@NotNull String name,
                                        @NotNull String path,
                                        @NotNull Consumer<ConfigChangeEvent> listener) {
        return onChange(name, event -> {
            if (event.affects(path)) listener.accept(event);
        });
    }

    default ConfigSubscription onChange(@NotNull File file,
                                        @NotNull Consumer<ConfigChangeEvent> listener) {
        return onChange(file.getPath(), listener);
    }

    default ConfigSubscription onChange(@NotNull ConfigFile file,
                                        @NotNull Consumer<ConfigChangeEvent> listener) {
        return onChange(file.getName(), listener);
    }

    default ConfigSubscription onChange(@NotNull File file,
                                        @NotNull String path,
                                        @NotNull Consumer<ConfigChangeEvent> listener) {
        return onChange(file.getPath(), path, listener);
    }

    default ConfigSubscription onChange(@NotNull ConfigFile file,
                                        @NotNull String path,
                                        @NotNull Consumer<ConfigChangeEvent> listener) {
        return onChange(file.getName(), path, listener);
    }
}

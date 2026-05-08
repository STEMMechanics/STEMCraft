package dev.stemcraft.api.service.resourcepack;

import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service for managing the resource pack.
 */
public interface ResourcePackService {

    /**
     * Retrieves the resource pack configuration.
     *
     * @return The configuration section view for the resource pack.
     */
    @NotNull ConfigSectionView getConfig();

    /**
     * Retrieves the resource pack file.
     *
     * @return The resource pack file.
     */
    @Nullable File getResourcePack();

    /**
     * Retrieves the resource pack hash as a hexadecimal string.
     *
     * @return The resource pack hash.
     */
    @Nullable String getResourcePackHash();

    /**
     * Retrieves the resource pack host.
     *
     * @return The resource pack host.
     */
    @NotNull ResourcePackHost host();

    /**
     * Registers a resource pack generator.
     *
     * @param generator The resource pack generator to register.
     */
    void registerGenerator(@NotNull ResourcePackGenerator generator);

    /**
     * Checks whether a compatible resource pack generator has been loaded.
     *
     * @param generatorId The generator id to check.
     * @return true if the generator is loaded, false otherwise.
     */
    boolean hasGenerator(@NotNull String generatorId);

    /**
     * Checks whether a compatible resource pack generator has been loaded.
     *
     * @param generatorType The generator type to check.
     * @return true if the generator is loaded, false otherwise.
     */
    boolean hasGenerator(@NotNull Class<? extends ResourcePackGenerator> generatorType);

    /**
     * Unregisters a resource pack generator by id.
     *
     * @param generatorId The generator id to remove.
     */
    default void unregisterGenerator(@NotNull String generatorId) {
    }

    /**
     * Sends the resource pack to the specified player.
     *
     * @param audience The player to send the resource pack to.
     */
    void sendPack(@NotNull Audience audience);

    /**
     * Sends the resource pack to all online players.
     */
    void sendPackToAll();

    /**
     * Generates the resource pack, providing status updates via the callback.
     *
     * @param statusCallback A consumer to receive status updates during generation.
     */
    void generatePack(@Nullable Consumer<@NotNull String> statusCallback);

    /**
     * Returns the legacy service-level pack-format compatibility window as a
     * two-element array containing {@code [minSupportedFormat, maxSupportedFormat]}.
     *
     * <p>This does not describe the full multi-target build plan. It reflects
     * the currently resolved compatibility range for the running server version,
     * retained for callers that still consume the older array-based API.</p>
     *
     * @return The min and max supported pack formats for the current service state.
     */
    int[] supportedVersion();

    /**
     * Gets the supported format range for the base build target generated for
     * the running server version.
     *
     * <p>Additional future overlay targets may be present in {@link #buildPlan()}.</p>
     *
     * @return The supported format range for the base pack target.
     */
    @NotNull PackFormatRange supportedRange();

    /**
     * Gets the planned build targets for the current pack build. The first
     * entry is always the base pack target, followed by any future overlay
     * targets.
     *
     * @return The planned build targets.
     */
    @NotNull List<ResourcePackBuildTarget> buildPlan();
}

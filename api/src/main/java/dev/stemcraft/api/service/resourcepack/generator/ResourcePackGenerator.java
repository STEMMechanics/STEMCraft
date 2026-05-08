package dev.stemcraft.api.service.resourcepack.generator;

import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildTarget;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Public extension point for resource-pack generation.
 *
 * <p>External plugins should usually implement this interface directly.
 * {@link AbstractResourcePackGenerator} is available as a convenience helper
 * for generators that want a stored id and config reference.</p>
 */
public interface ResourcePackGenerator {

    /**
     * Returns the unique generator id.
     *
     * @return The unique generator id.
     */
    @NotNull String id();

    /**
     * Called after dependency checks and before the generator is activated.
     *
     * @param config The generator configuration section from disk.
     */
    default void onLoad(@NotNull ConfigSectionView config) {
    }

    /**
     * Called when the generator is unloaded or unregistered.
     */
    default void onUnload() {
    }

    /**
     * Generates resource-pack output for one supported build target.
     *
     * @param context The build context for the target.
     * @throws IOException If a file operation fails.
     */
    void generate(@NotNull ResourcePackBuildContext context) throws IOException;

    /**
     * Returns generator ids that must already be active before this generator
     * can be enabled.
     *
     * @return Required generator ids.
     */
    default @NotNull List<String> requiredGenerators() {
        return List.of();
    }

    /**
     * Returns the supported pack-format range for this generator.
     *
     * @return The supported pack-format range.
     */
    default @NotNull PackFormatRange supportedFormats() {
        return PackFormatRange.all();
    }

    /**
     * Returns whether the generator supports the given build target.
     *
     * @param target The build target to test.
     * @return {@code true} if the target is supported.
     */
    default boolean supports(@NotNull ResourcePackBuildTarget target) {
        return supportedFormats().contains(target.packFormat());
    }
}

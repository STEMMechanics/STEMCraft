package dev.stemcraft.api.service.resourcepack;

import dev.stemcraft.api.config.ConfigSection;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Output abstraction for one resource-pack build target.
 */
public interface ResourcePackWriter {

    /**
     * Returns the filesystem root for this target's output.
     *
     * @return The target output root.
     */
    @NotNull Path root();

    /**
     * Returns the supported pack-format range represented by this output
     * segment.
     *
     * @return The supported pack-format range.
     */
    @NotNull PackFormatRange supportedRange();

    /**
     * Returns whether this writer targets an overlay directory.
     *
     * @return {@code true} for overlay targets.
     */
    boolean overlay();

    /**
     * Returns the overlay directory name for overlay targets.
     *
     * @return The overlay directory name, or {@code null} for the base pack.
     */
    @Nullable String overlayDirectory();

    /**
     * Returns the mutable manifest for the current build.
     *
     * @return The mutable build manifest.
     */
    @NotNull ConfigSection manifest();

    /**
     * Resolves a relative path within this target's output root.
     *
     * @param relativePath The relative output path.
     * @return The resolved path.
     */
    @NotNull Path resolve(@NotNull String relativePath);

    /**
     * Writes text to a relative output path, creating parent directories.
     *
     * @param relativePath The relative output path.
     * @param content The text content to write.
     * @throws IOException If writing fails.
     */
    void writeString(@NotNull String relativePath, @NotNull String content) throws IOException;

    /**
     * Copies a file into a relative output path.
     *
     * @param source The source file.
     * @param relativePath The relative output path.
     * @throws IOException If copying fails.
     */
    void copyFile(@NotNull Path source, @NotNull String relativePath) throws IOException;

    /**
     * Copies a directory into a relative output path.
     *
     * @param sourceDir The source directory.
     * @param relativePath The relative output path.
     * @throws IOException If copying fails.
     */
    void copyDirectory(@NotNull Path sourceDir, @NotNull String relativePath) throws IOException;
}

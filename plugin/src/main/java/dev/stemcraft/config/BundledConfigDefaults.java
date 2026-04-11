package dev.stemcraft.config;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.config.ConfigFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Restores missing config sections from the bundled config.yml defaults.
 */
public final class BundledConfigDefaults {
    private static final String CONFIG_RESOURCE = "config.yml";

    private BundledConfigDefaults() {
    }

    /**
     * Restores the first matching missing section from the bundled config.
     *
     * @param plugin The plugin owning the bundled config.
     * @param configFile The live config file to restore into.
     * @param candidatePaths Candidate section paths in priority order.
     * @return The restored path, or null if no bundled default section matched.
     */
    public static String restoreMissingSection(STEMCraft plugin, ConfigFile configFile, List<String> candidatePaths) {
        if (plugin == null || configFile == null || candidatePaths == null || candidatePaths.isEmpty()) {
            return null;
        }

        YamlConfiguration defaults = loadDefaults(plugin);
        if (defaults == null) {
            return null;
        }

        for (String candidatePath : candidatePaths) {
            if (candidatePath == null || candidatePath.isBlank()) {
                continue;
            }

            String sourcePath = resolveSectionPath(defaults, candidatePath);
            if (sourcePath == null) {
                continue;
            }

            if (!copySection(configFile, defaults, sourcePath)) {
                continue;
            }

            configFile.save();
            plugin.getLogger().info("Restored missing config section '" + sourcePath + "' from bundled defaults.");
            return sourcePath;
        }

        return null;
    }

    private static YamlConfiguration loadDefaults(STEMCraft plugin) {
        try (InputStream stream = plugin.getResource(CONFIG_RESOURCE)) {
            if (stream == null) {
                plugin.getLogger().warning("Bundled config.yml resource is missing; cannot restore defaults.");
                return null;
            }

            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load bundled config.yml defaults: " + exception.getMessage());
            return null;
        }
    }

    private static boolean copySection(ConfigFile configFile, YamlConfiguration defaults, String sourcePath) {
        ConfigurationSection source = defaults.getConfigurationSection(sourcePath);
        if (source == null) {
            return false;
        }

        if (configFile.contains(sourcePath) && !configFile.isSection(sourcePath)) {
            configFile.remove(sourcePath);
        }

        if (!configFile.isSection(sourcePath)) {
            configFile.createSection(sourcePath);
        }

        for (Map.Entry<String, Object> entry : source.getValues(true).entrySet()) {
            String relativePath = entry.getKey();
            if (source.isConfigurationSection(relativePath)) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                value = new ArrayList<>(list);
            }

            configFile.set(sourcePath + "." + relativePath, value);
        }

        return true;
    }

    private static String resolveSectionPath(ConfigurationSection root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }

        String[] parts = path.split("\\.");
        ConfigurationSection current = root;
        StringBuilder resolved = new StringBuilder(path.length());

        for (int i = 0; i < parts.length; i++) {
            String chosen = resolveSegment(current, parts[i]);
            if (chosen == null) {
                return null;
            }

            if (i > 0) {
                resolved.append('.');
            }
            resolved.append(chosen);

            current = current.getConfigurationSection(chosen);
            if (current == null && i < parts.length - 1) {
                return null;
            }
        }

        String resolvedPath = resolved.toString();
        return root.isConfigurationSection(resolvedPath) ? resolvedPath : null;
    }

    private static String resolveSegment(ConfigurationSection current, String segment) {
        if (current == null || segment == null || segment.isBlank()) {
            return null;
        }

        if (segmentExists(current, segment)) {
            return segment;
        }

        String hyphen = segment.replace('_', '-');
        if (!hyphen.equals(segment) && segmentExists(current, hyphen)) {
            return hyphen;
        }

        String snake = segment.replace('-', '_');
        if (!snake.equals(segment) && segmentExists(current, snake)) {
            return snake;
        }

        return null;
    }

    private static boolean segmentExists(ConfigurationSection current, String segment) {
        return current.isConfigurationSection(segment) || current.contains(segment);
    }
}

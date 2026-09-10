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

package dev.stemcraft.service.world;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.factory.ChunkGeneratorFactory;
import dev.stemcraft.api.service.world.WorldGeneration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.server.PluginDisableEvent;
import dev.stemcraft.api.service.world.generation.GeneratedWorld;
import dev.stemcraft.api.service.world.generation.GeneratorDefinition;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing world chunk generators.
 */
public class WorldGenerationImpl implements WorldGeneration {
    private final STEMCraftAPI api;
    private final Map<String, ChunkGeneratorFactory> registry = new ConcurrentHashMap<>();

    private record Registration(Plugin owner, GeneratorDefinition definition, ChunkGeneratorFactory factory) {}
    private record GeneratorVersion(NamespacedKey key, int version) {}
    private final Map<GeneratorVersion, Registration> versions = new ConcurrentHashMap<>();
    private final Map<NamespacedKey, Registration> definitions = new ConcurrentHashMap<>();
    private final Map<ChunkGenerator, GeneratorDefinition> instances = Collections.synchronizedMap(new WeakHashMap<>());

    @Override public Collection<GeneratorDefinition> getGenerators() {
        return definitions.values().stream().map(Registration::definition)
                .sorted(Comparator.comparing(d -> d.key().toString())).toList();
    }
    @Override public Optional<GeneratorDefinition> getGenerator(NamespacedKey key) {
        Registration entry = definitions.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry.definition());
    }
    @Override public Optional<GeneratorDefinition> getGenerator(NamespacedKey key, int version) {
        Registration entry = versions.get(new GeneratorVersion(key, version));
        return entry == null ? Optional.empty() : Optional.of(entry.definition());
    }
    public Optional<GeneratorDefinition> definition(String key, int version) {
        NamespacedKey id = namespacedKey(key);
        return id == null ? Optional.empty() : getGenerator(id, version);
    }
    public ChunkGenerator get(String key, String options, int version) {
        NamespacedKey id = namespacedKey(key);
        Registration entry = id == null ? null : versions.get(new GeneratorVersion(id, version));
        if (entry == null) throw new IllegalArgumentException("Required generator " + key + " version " + version + " is unavailable");
        return create(entry, options);
    }
    private ChunkGenerator create(Registration registration, String options) {
        ChunkGenerator generator = Objects.requireNonNull(registration.factory().create(options), "Generator factory returned null");
        instances.put(generator, registration.definition());
        return generator;
    }
    public Optional<GeneratorDefinition> definition(String key) {
        NamespacedKey id = namespacedKey(key);
        return id == null ? Optional.empty() : getGenerator(id);
    }
    @Override public Optional<GeneratedWorld> getGeneratedWorld(World world) {
        ChunkGenerator generator = world.getGenerator();
        GeneratorDefinition definition = generator instanceof dev.stemcraft.chunkgen.StemChunkGenerator stem
                ? stem.definition() : instances.get(generator);
        return definition == null ? Optional.empty()
                : Optional.of(new GeneratedWorld(world.getUID(), definition, world.getSeed()));
    }
    @Override public synchronized void registerGenerator(Plugin owner, GeneratorDefinition definition, ChunkGeneratorFactory factory) {
        requireServerThread();
        Objects.requireNonNull(factory);
        if (!owner.isEnabled()) throw new IllegalArgumentException("Generator owner must be enabled");
        if (!definition.key().getNamespace().equals(owner.getName().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Generator namespace must match owning plugin");
        String key = definition.key().toString();
        if (registry.containsKey(key) || (definition.key().getNamespace().equals("stemcraft")
                && registry.containsKey(definition.key().getKey())))
            throw new IllegalArgumentException("Generator key already registered: " + key);
        Registration existing = definitions.get(definition.key());
        if (existing != null && existing.owner() != owner) throw new IllegalArgumentException("Generator belongs to another plugin");
        Registration registration = new Registration(owner, definition, factory);
        if (versions.putIfAbsent(new GeneratorVersion(definition.key(), definition.version()), registration) != null)
            throw new IllegalArgumentException("Generator key/version already registered: " + key);
        if (existing == null || existing.definition().version() < definition.version()) definitions.put(definition.key(), registration);
    }
    @Override public boolean unregisterGenerator(Plugin owner, NamespacedKey key) {
        requireServerThread();
        Registration registration = definitions.get(key);
        if (registration == null) return false;
        if (registration.owner() != owner) throw new IllegalArgumentException("Generator belongs to another plugin");
        versions.entrySet().removeIf(entry -> entry.getKey().key().equals(key));
        return definitions.remove(key, registration);
    }
    void unregisterOwner(Plugin owner) {
        versions.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
        definitions.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
    }
    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Generator registration requires the server thread");
    }
    private static NamespacedKey namespacedKey(String key) {
        String normalized = normalizeKey(key);
        return NamespacedKey.fromString(normalized.contains(":") ? normalized : "stemcraft:" + normalized);
    }

    /**
     * Creates a new WorldGeneration service.
     *
     * @param api The STEMCraft API instance.
     */
    public WorldGenerationImpl(STEMCraftAPI api) {
        this.api = api;
    }

    /**
     * Called when the service is enabled.
     */
    public void onEnable() {
        api.events().register(PluginDisableEvent.class, event -> unregisterOwner(event.getPlugin()));
        api.tabComplete().register("world-generators", (player, args) -> list());
        api.tabComplete().register("world-generator-options", (player, args) -> {
            if (args.length == 0 || args[0].isBlank()) {
                return List.of();
            }

            String options = args.length > 1 ? args[1] : "";
            return tabCompleteOptions(args[0], options);
        });
    }

    /**
     * Called when the service is disabled.
     */
    @SuppressWarnings("EmptyMethod")
    public void onDisable() {
        versions.clear();
        definitions.clear();
        registry.clear();
        instances.clear();
    }

    /**
     * Returns a sorted list of registered STEMCraft generator keys and enabled
     * Bukkit plugins that can provide a default world generator.
     *
     * @return A list of available chunk generator keys.
     */
    public @NotNull List<String> list() {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        out.addAll(registry.keySet());
        for (NamespacedKey key : definitions.keySet()) out.add(key.toString());

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            listExternalGenerator(plugin).ifPresent(out::add);
        }

        return new ArrayList<>(out);
    }

    /**
     * Registers a new chunk generator factory with the given key.
     *
     * @param key The unique key for the chunk generator.
     * @param factory The factory to create chunk generator instances.
     */
    public synchronized void register(@NotNull String key, @NotNull ChunkGeneratorFactory factory) {
        String k = normalizeKey(key);
        if (definition(k).isPresent()) throw new IllegalArgumentException("Versioned generator already registered: " + k);
        registry.put(k, factory);
    }

    /**
     * Checks if a chunk generator with the given key is registered.
     *
     * @param key The chunk generator key.
     * @return True if the generator is registered, false otherwise.
     */
    public boolean isRegistered(@NotNull String key) {
        String k = normalizeKey(key);
        return registry.containsKey(k) || definition(k).isPresent();
    }

    @Override
    public boolean isAvailable(@NotNull String key, @NotNull String worldName) {
        String generatorKey = key.trim();
        if (generatorKey.isEmpty()) {
            return false;
        }
        if (isRegistered(generatorKey)) {
            return true;
        }
        return resolveExternal(worldName, generatorKey, "").isPresent();
    }

    @Override
    public @NotNull List<String> tabCompleteOptions(@NotNull String key, @NotNull String cfg) {
        String k = normalizeKey(key);
        ChunkGeneratorFactory f = registry.get(k);
        if (f == null) {
            return List.of();
        }

        return f.tabCompleteOptions(cfg);
    }

    /**
     * Creates a new chunk generator instance for the given key and configuration.
     *
     * @param key The chunk generator key.
     * @param cfg The configuration string for the generator.
     * @return A new ChunkGenerator instance.
     * @throws IllegalArgumentException if the key is unknown.
     */
    public @NotNull ChunkGenerator get(@NotNull String key, @NotNull String cfg) {
        String k = normalizeKey(key);
        NamespacedKey id = namespacedKey(k);
        Registration registration = id == null ? null : definitions.get(id);
        if (registration != null) {
            return create(registration, cfg);
        }
        ChunkGeneratorFactory f = registry.get(k);
        if (f == null) throw new IllegalArgumentException("Unknown generator key: " + key);
        return f.create(cfg);
    }

    /**
     * Resolves a Bukkit plugin generator.
     *
     * @param worldName The world name the generator is for.
     * @param generatorKey Plugin name or plugin:id generator key.
     * @param generatorOptions Generator id when {@code generatorKey} is a plugin name.
     * @return The resolved generator, if one is available.
     */
    public @NotNull Optional<ChunkGenerator> resolveExternal(
        @NotNull String worldName,
        @NotNull String generatorKey,
        @NotNull String generatorOptions
    ) {
        ExternalGeneratorSpec spec = parseExternalGeneratorSpec(generatorKey, generatorOptions);
        Plugin plugin = Bukkit.getPluginManager().getPlugin(spec.pluginName());
        if (plugin == null || !plugin.isEnabled()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(plugin.getDefaultWorldGenerator(worldName, spec.id()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private @NotNull Optional<String> listExternalGenerator(@NotNull Plugin plugin) {
        if (!plugin.isEnabled()) {
            return Optional.empty();
        }

        String pluginName = plugin.getName();
        if (pluginName.isBlank()) {
            return Optional.empty();
        }

        try {
            if (plugin.getDefaultWorldGenerator("__stemcraft_generator_probe__", null) == null) {
                return Optional.empty();
            }
        } catch (RuntimeException exception) {
            return Optional.empty();
        }

        return Optional.of(pluginName);
    }

    private static @NotNull ExternalGeneratorSpec parseExternalGeneratorSpec(
        @NotNull String generatorKey,
        @NotNull String generatorOptions
    ) {
        String key = generatorKey.trim();
        String options = generatorOptions.trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Generator name cannot be empty.");
        }
        if (key.contains(":") && !options.isEmpty()) {
            throw new IllegalArgumentException(
                "Generator '" + key + "' already includes an id; remove generator options or use plugin:id only."
            );
        }

        int separator = key.indexOf(':');
        if (separator < 0) {
            return new ExternalGeneratorSpec(key, options.isEmpty() ? null : options);
        }

        String pluginName = key.substring(0, separator).trim();
        String id = key.substring(separator + 1).trim();
        if (pluginName.isEmpty()) {
            throw new IllegalArgumentException("Generator plugin name cannot be empty.");
        }
        return new ExternalGeneratorSpec(pluginName, id.isEmpty() ? null : id);
    }

    /**
     * Normalizes a chunk generator key.
     *
     * @param key The chunk generator key.
     * @return The normalized key.
     * @throws IllegalArgumentException if the key is null or empty.
     */
    private static @NotNull String normalizeKey(@NotNull String key) {
        String k = key.trim();
        if (k.isEmpty()) throw new IllegalArgumentException("key cannot be empty");
        return k.toLowerCase(Locale.ROOT);
    }

    private record ExternalGeneratorSpec(@NotNull String pluginName, String id) {}
}

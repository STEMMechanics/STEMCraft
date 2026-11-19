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

package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.condition.ConditionContext;
import dev.stemcraft.api.service.condition.ConditionProvider;
import dev.stemcraft.api.service.distance.DistanceDifficultyService;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/**
 * Feature that adjusts mob difficulty based on distance from world spawn.
 */
public class DistanceDifficulty extends BaseFeature implements DistanceDifficultyService {
    private static final int DEFAULT_RANGE = 1_000;
    private static final double DEFAULT_MULTIPLIER = 0.25;

    private NamespacedKey key;
    private NamespacedKey tierKey;
    private int range;
    private double multiplier;
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Map<String, Set<String>> ownedProfiles = new HashMap<>();
    private final Map<String, String> selectedWorldProfiles = new LinkedHashMap<>();

    /**
     * Constructor for DistanceDifficulty feature.
     *
     * @param api The STEMCraft API instance.
     */
    public DistanceDifficulty(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Registers event listeners to adjust mob difficulty based on distance from world spawn.
     */
    @Override
    public void onEnable() {
        key = new NamespacedKey(STEMCraft.getPlugin(), "distance_multiplier");
        tierKey = new NamespacedKey(STEMCraft.getPlugin(), "distance_tier");
        if (STEMCraft.getPlugin().contentPacks() != null) {
            STEMCraft.getPlugin().contentPacks().registerHandler(
                "distance-difficulty", this::reloadContentPack, this::unloadContentPack);
        }
        reloadConfigState();
        api.conditions().register(new DistanceTierConditionProvider());

        api.events().register(CreatureSpawnEvent.class, event -> {
            LivingEntity entity = event.getEntity();
            if (entity instanceof Player) return;

            Profile profile = profile(entity.getLocation());
            int tier = tier(entity.getLocation());
            double multiplier = resolveMultiplier(tier * profile.range(), profile);
            entity.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);
            if (!(entity instanceof Monster)) return;
            if (multiplier == 1.0) return;

            entity.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, multiplier);
        });

        api.events().register(EntityDamageByEntityEvent.class, event -> {
            Entity rawDamager = event.getDamager();
            Entity victim = event.getEntity();
            if (!(victim instanceof LivingEntity livingVictim)) return;

            Entity trueDamager = unwrapDamager(rawDamager);

            boolean mobHitsPlayer = isMobDamagingPlayer(trueDamager, livingVictim);
            boolean playerHitsMob = isPlayerDamagingMob(trueDamager, livingVictim);

            if (!mobHitsPlayer && !playerHitsMob) return;

            if (mobHitsPlayer) {
                Player player = (Player) livingVictim;
                if (player.getGameMode() != GameMode.SURVIVAL) return;

                double entityMultiplier = getMultiplierFromEntity(trueDamager);
                if (entityMultiplier != 1.0) {
                    event.setDamage(event.getDamage() * entityMultiplier);
                }
            } else {
                Player player = (Player) trueDamager;
                if (player.getGameMode() != GameMode.SURVIVAL) return;

                double entityMultiplier = getMultiplierFromEntity(livingVictim);
                if (entityMultiplier != 1.0) {
                    event.setDamage(event.getDamage() / entityMultiplier);
                }
            }
        });
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadConfigState();
    }

    @Override
    public void onDisable() {
        api.conditions().unregister("distance-tier");
        profiles.clear();
        ownedProfiles.clear();
    }

    @Override
    public boolean isAvailable() {
        return key != null && tierKey != null;
    }

    @Override
    public int tier(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) return 0;
        int dx = Math.abs(location.getBlockX() - location.getWorld().getSpawnLocation().getBlockX());
        int dz = Math.abs(location.getBlockZ() - location.getWorld().getSpawnLocation().getBlockZ());
        return tierForDistance(Math.max(dx, dz), profile(location).range());
    }

    @Override
    public int storedTier(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return 0;
        Integer stored = living.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        if (stored != null) return Math.max(0, stored);
        // Compatibility for mobs spawned before distance_tier was introduced.
        Double storedMultiplier = living.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        if (storedMultiplier != null) {
            Profile profile = profile(living.getLocation());
            if (profile.multiplier() > 0D) {
                return Math.max(0, (int) Math.round((storedMultiplier - 1D) / profile.multiplier()));
            }
        }
        return tier(living.getLocation());
    }

    @Override
    public double multiplier(Entity entity) {
        return getMultiplierFromEntity(entity);
    }

    @Override
    public boolean reloadContentPack(@org.jetbrains.annotations.NotNull String packId) {
        unloadContentPack(packId);
        var pack = STEMCraft.getPlugin().contentPacks() == null
            ? null : STEMCraft.getPlugin().contentPacks().find(packId);
        if (pack == null || !pack.enabled()) return true;

        Set<String> owned = new java.util.LinkedHashSet<>();
        List<String> errors = new ArrayList<>();
        for (File file : profileFiles(new File(pack.path()))) {
            ConfigFile config = api.config().load(file);
            if (config == null) continue;
            config.reload();
            Map<String, Object> values = config.getMap("difficulty-profiles", false);
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                    errors.add(pack.id() + "/" + file.getName() + ".difficulty-profiles."
                        + entry.getKey() + " must be a mapping.");
                    continue;
                }
                Map<String, Object> profile = stringMap(raw);
                double configuredRange = number(profile.get("range"), range);
                double configuredMultiplier = number(profile.get("multiplier"), multiplier);
                if (configuredRange <= 0D || configuredMultiplier < 0D) {
                    errors.add(pack.id() + "/" + file.getName() + ".difficulty-profiles."
                        + entry.getKey() + " has an invalid range or multiplier.");
                    continue;
                }
                String id = profileId(pack.id(), entry.getKey());
                if (profiles.containsKey(id)) {
                    errors.add(pack.id() + "/" + file.getName() + ".difficulty-profiles."
                        + entry.getKey() + " duplicates profile " + id + ".");
                    continue;
                }
                profiles.put(id, new Profile(Math.max(1, (int) Math.round(configuredRange)), configuredMultiplier));
                owned.add(id);
            }
        }
        if (!errors.isEmpty()) {
            owned.forEach(profiles::remove);
            STEMCraft.getPlugin().getLogger().warning("[distance-difficulty] Refusing profiles for '"
                + pack.id() + "': " + String.join(" | ", errors));
            return false;
        }
        ownedProfiles.put(pack.id().toLowerCase(Locale.ROOT), Set.copyOf(owned));
        return true;
    }

    @Override
    public void unloadContentPack(@org.jetbrains.annotations.NotNull String packId) {
        Set<String> owned = ownedProfiles.remove(packId.toLowerCase(Locale.ROOT));
        if (owned != null) owned.forEach(profiles::remove);
    }

    @Override
    public boolean selectWorldProfile(@org.jetbrains.annotations.NotNull String world,
                                      @org.jetbrains.annotations.NotNull String profileId) {
        String normalizedWorld = world.trim();
        String normalizedProfile = profileId(profileId);
        if (normalizedWorld.isBlank() || !profiles.containsKey(normalizedProfile)) return false;
        selectedWorldProfiles.put(normalizedWorld, normalizedProfile);
        ConfigSection config = getConfigSection();
        config.set("world-profiles." + normalizedWorld, normalizedProfile);
        getRootConfigSection().save();
        return true;
    }

    @Override
    public @org.jetbrains.annotations.NotNull Map<String, String> worldProfiles() {
        return Map.copyOf(selectedWorldProfiles);
    }

    private void reloadConfigState() {
        range = Math.max(1, getConfigSection().getInt("range", DEFAULT_RANGE));
        multiplier = getConfigSection().getDouble("multiplier", DEFAULT_MULTIPLIER);
        selectedWorldProfiles.clear();
        for (Map.Entry<String, Object> entry : getConfigSection().getMap("world-profiles", false).entrySet()) {
            if (entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                selectedWorldProfiles.put(entry.getKey(), profileId(String.valueOf(entry.getValue())));
            }
        }
    }

    /**
     * Calculates the difficulty multiplier based on distance.
     *
     * @param dist Distance from world spawn.
     * @return Calculated multiplier.
     */
    private double resolveMultiplier(int dist) { return resolveMultiplier(dist, new Profile(range, multiplier)); }

    private double resolveMultiplier(int dist, Profile profile) {
        int bands = tierForDistance(dist, profile.range());
        return 1.0 + (bands * profile.multiplier());
    }

    static int tierForDistance(int distance, int range) {
        return Math.max(0, distance) / Math.max(1, range);
    }

    private Profile profile(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) return new Profile(range, multiplier);
        String selected = selectedWorldProfiles.get(location.getWorld().getName());
        return selected == null ? new Profile(range, multiplier)
            : profiles.getOrDefault(selected, new Profile(range, multiplier));
    }

    private static String profileId(String packId, String profileId) {
        return packId(packId) + ":" + profileId(profileId);
    }

    private static String profileId(String profileId) {
        return profileId == null ? "" : profileId.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String packId(String packId) {
        return packId == null ? "" : packId.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static List<File> profileFiles(File packDirectory) {
        List<File> files = new ArrayList<>();
        File root = new File(packDirectory, "config.yml");
        if (root.isFile()) files.add(root);
        File configs = new File(packDirectory, "configs");
        File[] children = configs.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (children != null) {
            java.util.Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            files.addAll(List.of(children));
        }
        return files;
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) values.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return values;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /**
     * Unwraps the true damager entity from projectiles.
     *
     * @param damager The raw damager entity.
     * @return The true damager entity.
     */
    private Entity unwrapDamager(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity) {
                return shooterEntity;
            }
        }
        return damager;
    }

    /**
     * Checks if a mob is damaging a player.
     *
     * @param damager The entity causing damage.
     * @param victim The entity receiving damage.
     * @return True if a mob is damaging a player, false otherwise.
     */
    private boolean isMobDamagingPlayer(Entity damager, LivingEntity victim) {
        if (!(victim instanceof Player)) return false;
        if (damager instanceof Player) return false;
        return damager instanceof Monster;
    }

    /**
     * Checks if a player is damaging a mob.
     *
     * @param damager The entity causing damage.
     * @param victim The entity receiving damage.
     * @return True if a player is damaging a mob, false otherwise.
     */
    private boolean isPlayerDamagingMob(Entity damager, LivingEntity victim) {
        if (!(damager instanceof Player)) return false;
        if (victim instanceof Player) return false;
        return victim instanceof Monster;
    }

    /**
     * Retrieves the difficulty multiplier from an entity's persistent data.
     *
     * @param entity The entity to check.
     * @return The difficulty multiplier, or 1.0 if not set.
     */
    private double getMultiplierFromEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return 1.0;
        PersistentDataContainer pdc = living.getPersistentDataContainer();
        Double val = pdc.get(key, PersistentDataType.DOUBLE);
        return val != null ? val : 1.0;
    }

    private final class DistanceTierConditionProvider implements ConditionProvider {
        @Override
        public @org.jetbrains.annotations.NotNull String id() {
            return "distance-tier";
        }

        @Override
        public @org.jetbrains.annotations.NotNull java.util.List<String> validate(
                @org.jetbrains.annotations.NotNull Object value, @org.jetbrains.annotations.NotNull String path) {
            if (value instanceof Number || value instanceof java.util.Map<?, ?>) return java.util.List.of();
            return java.util.List.of(path + " must be a tier number or {min, max} range.");
        }

        @Override
        public boolean matches(@org.jetbrains.annotations.NotNull Object value,
                               @org.jetbrains.annotations.NotNull ConditionContext context) {
            return dev.stemcraft.service.ConditionServiceImpl.matchesValue(context.distanceTier(), value);
        }
    }

    private record Profile(int range, double multiplier) { }
}

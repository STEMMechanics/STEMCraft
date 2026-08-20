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

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.WorldUtil;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;

/**
 * Feature that manages separate inventories for players based on their game mode
 * and the world group they are in. World groups are determined by stripping
 * "_nether" and "_the_end" suffixes from world names.
 */
@SuppressWarnings("unused")
public class GameModeInventories extends BaseFeature {
    private static final String AUTOSAVE_TASK = "feature:gamemode-inventories-autosave";
    private static final long AUTOSAVE_TICKS = 1200L;
    private final Map<UUID, PlayerProfiles> profiles = new HashMap<>();
    private final Map<UUID, ActiveProfile> activeProfiles = new HashMap<>();

    /**
     * Constructor for GameModeInventories feature.
     */
    public GameModeInventories(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Initializes the feature, loading stored inventories and registering event handlers.
     */
    @Override
    public void onEnable() {
        ensureStorage();
        loadAll();

        api.events().register(PlayerJoinEvent.class, this::onJoin);
        api.events().register(PlayerQuitEvent.class, this::onQuit);
        api.events().register(PlayerChangedWorldEvent.class, this::onWorldChange);
        api.events().register(PlayerGameModeChangeEvent.class, this::onGameModeChange);
        api.tasks().repeating(AUTOSAVE_TASK, AUTOSAVE_TICKS, AUTOSAVE_TICKS, this::saveOnlineProfiles);
    }

    /**
     * Cleans up the feature, saving all inventories to storage.
     */
    @Override
    public void onDisable() {
        api.tasks().cancel(AUTOSAVE_TASK);
        saveOnlineProfiles();
    }

    @Override
    public void onSave() {
        saveAll();
    }

    /**
     * Handles player join events, applying or creating the appropriate profile.
     *
     * @param event The player join event.
     */
    private void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isInMinigame(player)) {
            return;
        }
        String baseName = WorldUtil.baseName(player.getWorld());
        GameMode gm = player.getGameMode();
        applyOrCreateProfile(player, baseName, gm);
        activeProfiles.put(player.getUniqueId(), new ActiveProfile(baseName, gm));
    }

    /**
     * Handles player quit events, saving the current profile.
     *
     * @param event The player quit event.
     */
    private void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isInMinigame(player)) {
            return;
        }
        saveActiveProfile(player);
        activeProfiles.remove(player.getUniqueId());
    }

    /**
     * Handles world change events, saving the current profile and applying or creating the new one.
     *
     * @param event The player changed world event.
     */
    private void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isInMinigame(player)) {
            return;
        }
        World to = player.getWorld();
        GameMode gm = player.getGameMode();
        String toBase = WorldUtil.baseName(to);
        switchProfile(player, toBase, gm);
    }

    /**
     * Handles game mode change events, saving the current profile and applying or creating the new one.
     *
     * @param event The player game mode change event.
     */
    private void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        if (isInMinigame(player)) {
            return;
        }
        GameMode newGm = event.getNewGameMode();
        String baseName = WorldUtil.baseName(player.getWorld());
        switchProfile(player, baseName, newGm);
    }

    /**
     * Constructs a unique profile key based on world group and game mode.
     *
     * @param group The world group.
     * @param gm The game mode.
     * @return The constructed profile key.
     */
    private String profileKey(String group, GameMode gm) {
        return group + ":" + gm.name();
    }

    /**
     * Retrieves the player profiles for a given UUID, creating a new entry if none exists.
     *
     * @param uuid The player's UUID.
     * @return The PlayerProfiles object for the given UUID.
     */
    private PlayerProfiles getPlayerProfiles(UUID uuid) {
        return profiles.computeIfAbsent(uuid, u -> new PlayerProfiles());
    }

    /**
     * Saves the current profile of a player.
     *
     * @param player The player whose profile is to be saved.
     * @param group The world group.
     * @param gm The game mode.
     */
    private void saveCurrentProfile(Player player, String group, GameMode gm) {
        PlayerProfiles p = getPlayerProfiles(player.getUniqueId());
        String key = profileKey(group, gm);
        PlayerState state = PlayerState.fromPlayer(player);
        p.states.put(key, state);
        persistProfile(player.getUniqueId(), key, state);
    }

    /**
     * Saves the profile that was actually applied to the player. This must not be inferred from
     * the player's current world and game mode because world settings can fire a nested game-mode
     * change while a world-change event is still being dispatched.
     */
    private void saveActiveProfile(Player player) {
        ActiveProfile active = activeProfiles.get(player.getUniqueId());
        if (active != null) saveCurrentProfile(player, active.group(), active.gameMode());
    }

    private void switchProfile(Player player, String group, GameMode gameMode) {
        ActiveProfile target = new ActiveProfile(group, gameMode);
        ActiveProfile active = activeProfiles.get(player.getUniqueId());
        if (target.equals(active)) return;
        if (active != null) saveCurrentProfile(player, active.group(), active.gameMode());
        applyOrCreateProfile(player, group, gameMode);
        activeProfiles.put(player.getUniqueId(), target);
    }

    /**
     * Applies an existing profile to a player or creates a new one if none exists.
     *
     * @param player The player to whom the profile is to be applied.
     * @param group The world group.
     * @param gm The game mode.
     */
    private void applyOrCreateProfile(Player player, String group, GameMode gm) {
        PlayerProfiles p = getPlayerProfiles(player.getUniqueId());
        String key = profileKey(group, gm);

        PlayerState state = p.states.get(key);
        if (state == null) {
            state = PlayerState.create(player);
            p.states.put(key, state);
        }

        state.applyTo(player);
    }

    private boolean isInMinigame(Player player) {
        return api.minigames().list().stream().anyMatch(miniGame -> miniGame.findPlayer(player) != null);
    }

    /**
     * Loads all player profiles from storage.
     */
    private void loadAll() {
        profiles.clear();
        api.database().queryEach(
            "SELECT player_uuid, profile_key, state_yaml FROM gamemode_inventories",
            null,
            rs -> {
                UUID uuid;
                try {
                    uuid = UUID.fromString(rs.getString("player_uuid"));
                } catch (IllegalArgumentException ignored) {
                    return;
                }

                String profileKey = rs.getString("profile_key");
                String stateYaml = rs.getString("state_yaml");
                PlayerState state = PlayerState.fromYaml(stateYaml);
                if (state == null) {
                    return;
                }

                PlayerProfiles p = profiles.computeIfAbsent(uuid, u -> new PlayerProfiles());
                p.states.put(profileKey, state);
            }
        );
    }

    /**
     * Saves all player profiles to storage.
     */
    private void saveOnlineProfiles() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!isInMinigame(player)) saveActiveProfile(player);
        }
    }

    private void persistProfile(UUID uuid, String key, PlayerState state) {
        String yaml = state.toYaml();
        api.database().update(
            "INSERT OR REPLACE INTO gamemode_inventories (player_uuid, profile_key, state_yaml, updated_at) VALUES (?, ?, ?, ?)",
            ps -> {
                ps.setString(1, uuid.toString());
                ps.setString(2, key);
                ps.setString(3, yaml);
                ps.setLong(4, System.currentTimeMillis());
            }
        );
    }

    private void ensureStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS gamemode_inventories (" +
            "player_uuid TEXT NOT NULL," +
            "profile_key TEXT NOT NULL," +
            "state_yaml TEXT NOT NULL," +
            "updated_at INTEGER NOT NULL," +
            "PRIMARY KEY (player_uuid, profile_key)" +
            ");"
        );
        api.database().execute("CREATE INDEX IF NOT EXISTS gamemode_inventories_player_uuid ON gamemode_inventories(player_uuid);");
    }

    private static class PlayerProfiles {
        final Map<String, PlayerState> states = new HashMap<>();
    }

    private record ActiveProfile(String group, GameMode gameMode) { }

    private static void clearPotionEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private static class PlayerState {
        double health;
        int foodLevel;
        float saturation;
        float exhaustion;
        int level;
        float exp;
        int totalExp;
        ItemStack[] contents;
        ItemStack[] armor;
        ItemStack[] ender;
        List<PotionEffect> effects;

        static PlayerState create(Player player) {
            PlayerState state = new PlayerState();
            state.health = PlayerUtil.getMaxHealth(player);
            state.foodLevel = 20;
            state.saturation = 5.0f;
            state.exhaustion = 0.0f;
            state.level = 0;
            state.exp = 0.0f;
            state.totalExp = 0;
            state.contents = new ItemStack[player.getInventory().getSize()];
            state.armor = new ItemStack[PlayerUtil.getArmorLength(player)];
            state.ender = new ItemStack[player.getEnderChest().getSize()];
            state.effects = new ArrayList<>();
            return state;
        }

        static PlayerState fromPlayer(Player player) {
            PlayerState s = new PlayerState();
            s.health = player.getHealth();
            s.foodLevel = player.getFoodLevel();
            s.saturation = player.getSaturation();
            s.exhaustion = player.getExhaustion();
            s.level = player.getLevel();
            s.exp = player.getExp();
            s.totalExp = player.getTotalExperience();
            s.contents = player.getInventory().getContents();
            s.armor = player.getInventory().getArmorContents();
            s.ender = player.getEnderChest().getContents();
            s.effects = new ArrayList<>(player.getActivePotionEffects());
            return s;
        }

        void applyTo(Player player) {
            double max = PlayerUtil.getMaxHealth(player);
            player.setHealth(Math.min(health, max));
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
            player.setLevel(level);
            player.setExp(exp);
            player.setTotalExperience(totalExp);

            clearPotionEffects(player);
            for (PotionEffect effect : effects) {
                player.addPotionEffect(effect);
            }

            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armor);
            player.getEnderChest().setContents(ender);
        }

        static PlayerState fromConfigSection(ConfigurationSection s) {
            PlayerState state = new PlayerState();
            state.health = s.getDouble("health", 20.0);
            state.foodLevel = s.getInt("food", 20);
            state.saturation = (float) s.getDouble("saturation", 5.0F);
            state.exhaustion = (float) s.getDouble("exhaustion", 0.0F);
            state.level = s.getInt("level", 0);
            state.exp = (float) s.getDouble("exp", 0.0F);
            state.totalExp = s.getInt("totalExp", 0);

            List<?> contentsList = s.getList("contents", List.of());
            List<ItemStack> contents = new ArrayList<>();

            for (Object o : contentsList) {
                if (o instanceof ItemStack item) {
                    contents.add(item);
                }
            }

            state.contents = contents.toArray(new ItemStack[0]);

            List<?> armorList = s.getList("armor", List.of());
            List<ItemStack> armor = new ArrayList<>();
            for (Object o : armorList) {
                if (o instanceof ItemStack item) {
                    armor.add(item);
                }
            }
            state.armor = armor.toArray(new ItemStack[0]);

            List<?> enderList = s.getList("ender", List.of());
            List<ItemStack> ender = new ArrayList<>();
            for (Object o : enderList) {
                if (o instanceof ItemStack item) {
                    ender.add(item);
                }
            }
            state.ender = ender.toArray(new ItemStack[0]);

            List<?> effectsList = s.getList("effects", List.of());
            List<PotionEffect> effects = new ArrayList<>();
            for (Object o : effectsList) {
                if (o instanceof PotionEffect effect) {
                    effects.add(effect);
                }
            }
            state.effects = effects;

            return state;
        }

        void toConfigSection(ConfigurationSection s) {
            s.set("health", health);
            s.set("food", foodLevel);
            s.set("saturation", saturation);
            s.set("exhaustion", exhaustion);
            s.set("level", level);
            s.set("exp", exp);
            s.set("totalExp", totalExp);
            s.set("contents", contents);
            s.set("armor", armor);
            s.set("ender", ender);
            s.set("effects", effects);
        }

        static PlayerState fromYaml(String yaml) {
            if (yaml == null || yaml.isBlank()) {
                return null;
            }

            YamlConfiguration cfg = new YamlConfiguration();
            try {
                cfg.loadFromString(yaml);
            } catch (Exception ignored) {
                return null;
            }

            ConfigurationSection section = cfg.getConfigurationSection("state");
            if (section == null) {
                return null;
            }
            return fromConfigSection(section);
        }

        String toYaml() {
            YamlConfiguration cfg = new YamlConfiguration();
            ConfigurationSection section = cfg.createSection("state");
            toConfigSection(section);
            return cfg.saveToString();
        }
    }
}

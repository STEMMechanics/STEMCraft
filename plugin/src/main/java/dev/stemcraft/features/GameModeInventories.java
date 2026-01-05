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

package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.WorldUtil;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Feature that manages separate inventories for players based on their game mode
 * and the world group they are in. World groups are determined by stripping
 * "_nether" and "_the_end" suffixes from world names.
 */
public class GameModeInventories extends BaseFeature {

    private ConfigSection data;
    private final Map<UUID, PlayerProfiles> profiles = new HashMap<>();

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

        this.data = api.config().load("inventories.yml");
        loadAll();

        api.events().register(PlayerJoinEvent.class, this::onJoin);
        api.events().register(PlayerQuitEvent.class, this::onQuit);
        api.events().register(PlayerChangedWorldEvent.class, this::onWorldChange);
        api.events().register(PlayerGameModeChangeEvent.class, this::onGameModeChange);
    }

    /**
     * Cleans up the feature, saving all inventories to storage.
     */
    @Override
    public void onDisable() {
        saveAll();
    }

    /**
     * Handles player join events, applying or creating the appropriate profile.
     */
    private void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String baseName = WorldUtil.baseName(player.getWorld());
        GameMode gm = player.getGameMode();
        applyOrCreateProfile(player, baseName, gm);
    }

    /**
     * Handles player quit events, saving the current profile.
     */
    private void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String baseName = WorldUtil.baseName(player.getWorld());
        GameMode gm = player.getGameMode();
        saveCurrentProfile(player, baseName, gm);
        saveAll();
    }

    /**
     * Handles world change events, saving the current profile and applying or creating the new one.
     */
    private void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom();
        World to = player.getWorld();
        GameMode gm = player.getGameMode();

        String fromBase = WorldUtil.baseName(from);
        String toBase = WorldUtil.baseName(to);

        if (fromBase.equals(toBase)) {
            return;
        }

        saveCurrentProfile(player, fromBase, gm);
        applyOrCreateProfile(player, toBase, gm);
    }

    /**
     * Handles game mode change events, saving the current profile and applying or creating the new one.
     */
    private void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        GameMode oldGm = player.getGameMode();
        GameMode newGm = event.getNewGameMode();
        String baseName = WorldUtil.baseName(player.getWorld());

        saveCurrentProfile(player, baseName, oldGm);
        applyOrCreateProfile(player, baseName, newGm);
    }

    private String profileKey(String group, GameMode gm) {
        return group + ":" + gm.name();
    }

    private PlayerProfiles getPlayerProfiles(UUID uuid) {
        return profiles.computeIfAbsent(uuid, u -> new PlayerProfiles());
    }

    private void saveCurrentProfile(Player player, String group, GameMode gm) {
        PlayerProfiles p = getPlayerProfiles(player.getUniqueId());
        String key = profileKey(group, gm);
        p.states.put(key, PlayerState.fromPlayer(player));
    }

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

    private void loadAll() {
        profiles.clear();
        ConfigSection playersSec = this.data.getSection("players");

        for (String uuidStr : playersSec.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            PlayerProfiles p = new PlayerProfiles();
            profiles.put(uuid, p);

            for (String key : playersSec.getSection(uuidStr).getKeys(false)) {
                ConfigSection s = playersSec.getSection(uuidStr).getSection(key);
                if (s == null) continue;

                PlayerState state = PlayerState.fromConfigSection(s);
                p.states.put(key, state);
            }
        }
    }

    private void saveAll() {
        ConfigSection playersSec = data.getSection("players");

        for (Map.Entry<UUID, PlayerProfiles> entry : profiles.entrySet()) {
            String uuidStr = entry.getKey().toString();
            PlayerProfiles p = entry.getValue();

            ConfigSection profSec = playersSec.createSection(uuidStr);
            for (Map.Entry<String, PlayerState> profileEntry : p.states.entrySet()) {
                String key = profileEntry.getKey();
                PlayerState state = profileEntry.getValue();
                ConfigSection s = profSec.createSection(key);
                state.toConfigSection(s);
            }
        }

        data.save();
    }

    private static class PlayerProfiles {
        final Map<String, PlayerState> states = new HashMap<>();
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
            state.armor = new ItemStack[player.getInventory().getArmorContents().length];
            state.ender = new ItemStack[player.getEnderChest().getSize()];
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

            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armor);
            player.getEnderChest().setContents(ender);
        }

        static PlayerState fromConfigSection(ConfigSection s) {
            PlayerState state = new PlayerState();
            state.health = s.getDouble("health", 20.0);
            state.foodLevel = s.getInt("food", 20);
            state.saturation = s.getFloat("saturation", 5.0F);
            state.exhaustion = s.getFloat("exhaustion", 0.0F);
            state.level = s.getInt("level", 0);
            state.exp = s.getFloat("exp", 0.0F);
            state.totalExp = s.getInt("totalExp", 0);

            List<?> contentsList = s.getList("contents");
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

            return state;
        }

        void toConfigSection(ConfigSection s) {
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
        }
    }
}
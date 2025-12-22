package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.utils.SCPlayer;
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

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GameModeInventories implements STEMCraftFeature {

    private File storageFile;
    private YamlConfiguration storage;

    private final Map<UUID, PlayerProfiles> profiles = new HashMap<>();

    @Override
    public void onEnable(STEMCraftAPI api) {
        this.storageFile = new File(api.dataFolder(), "inventories.yml");
        this.storage = new YamlConfiguration();
        loadAll();

        api.registerEvent(PlayerJoinEvent.class, this::onJoin);
        api.registerEvent(PlayerQuitEvent.class, this::onQuit);
        api.registerEvent(PlayerChangedWorldEvent.class, this::onWorldChange);
        api.registerEvent(PlayerGameModeChangeEvent.class, this::onGameModeChange);
    }

    @Override
    public void onDisable() {
        saveAll();
    }

    private void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String group = worldGroup(player.getWorld());
        GameMode gm = player.getGameMode();
        applyOrCreateProfile(player, group, gm);
    }

    private void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String group = worldGroup(player.getWorld());
        GameMode gm = player.getGameMode();
        saveCurrentProfile(player, group, gm);
        saveAll();
    }

    private void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom();
        World to = player.getWorld();
        GameMode gm = player.getGameMode();

        String fromGroup = worldGroup(from);
        String toGroup = worldGroup(to);

        if (fromGroup.equals(toGroup)) {
            return;
        }

        saveCurrentProfile(player, fromGroup, gm);
        applyOrCreateProfile(player, toGroup, gm);
    }

    private void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        GameMode oldGm = player.getGameMode();
        GameMode newGm = event.getNewGameMode();
        String group = worldGroup(player.getWorld());

        saveCurrentProfile(player, group, oldGm);
        applyOrCreateProfile(player, group, newGm);
    }

    private String worldGroup(World world) {
        String name = world.getName();
        if (name.endsWith("_nether")) {
            return name.substring(0, name.length() - "_nether".length());
        }
        if (name.endsWith("_the_end")) {
            return name.substring(0, name.length() - "_the_end".length());
        }
        return name;
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
        if (!storageFile.exists()) {
            return;
        }

        try {
            storage.load(storageFile);
        } catch (Exception e) {
            STEMCraft.getInstance().warn("GAMEMODE_INVENTORIES_LOAD_FAILED", "error", e.getMessage());
            return;
        }

        ConfigurationSection playersSec = storage.getConfigurationSection("players");
        if (playersSec == null) {
            return;
        }

        for (String uuidStr : playersSec.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            PlayerProfiles p = new PlayerProfiles();
            profiles.put(uuid, p);

            ConfigurationSection profSec = playersSec.getConfigurationSection(uuidStr + ".profiles");
            if (profSec == null) {
                continue;
            }

            for (String key : profSec.getKeys(false)) {
                ConfigurationSection s = profSec.getConfigurationSection(key);
                if (s == null) continue;

                PlayerState state = PlayerState.fromConfigSection(s);
                p.states.put(key, state);
            }
        }
    }

    private void saveAll() {
        storage = new YamlConfiguration();
        ConfigurationSection playersSec = storage.createSection("players");

        for (Map.Entry<UUID, PlayerProfiles> entry : profiles.entrySet()) {
            String uuidStr = entry.getKey().toString();
            PlayerProfiles p = entry.getValue();

            ConfigurationSection profSec = playersSec.createSection(uuidStr + ".profiles");
            for (Map.Entry<String, PlayerState> profileEntry : p.states.entrySet()) {
                String key = profileEntry.getKey();
                PlayerState state = profileEntry.getValue();
                ConfigurationSection s = profSec.createSection(key);
                state.toConfigSection(s);
            }
        }

        try {
            storage.save(storageFile);
        } catch (IOException e) {
            STEMCraft.getInstance().warn("GAMEMODE_INVENTORIES_LOAD_FAILED", "error", e.getMessage());
        }
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
            state.health = SCPlayer.maxHealth(player);
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
            double max = SCPlayer.maxHealth(player);
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

        static PlayerState fromConfigSection(ConfigurationSection s) {
            PlayerState state = new PlayerState();
            state.health = s.getDouble("health", 20.0);
            state.foodLevel = s.getInt("food", 20);
            state.saturation = (float) s.getDouble("saturation", 5.0);
            state.exhaustion = (float) s.getDouble("exhaustion", 0.0);
            state.level = s.getInt("level", 0);
            state.exp = (float) s.getDouble("exp", 0.0);
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
        }
    }
}
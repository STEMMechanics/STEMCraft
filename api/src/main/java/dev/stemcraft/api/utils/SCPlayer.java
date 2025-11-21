package dev.stemcraft.api.utils;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.geysermc.geyser.api.GeyserApi;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SCPlayer extends STEMCraftUtil {
    private static Boolean isGeyserInstalled = null;
    private static GeyserApi geyserApi = null;
    private static Map<String, String> nameCache = new HashMap<>();
    private static File configFile;
    private static YamlConfiguration config;

    @Override
    public void onLoad() {
        configFile = new File(STEMCraftAPI.api().dataFolder(), "players.yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                error("Could not create the players.yml configuration file", e);
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        ConfigurationSection cacheSection = config.getConfigurationSection("players.cache");
        if (cacheSection != null) {
            for (String key : cacheSection.getKeys(false)) {
                nameCache.put(key, cacheSection.getString(key));
            }
        }
    }

    /**
     * Test if a player is a BedRock player
     *
     * @param player The player to test.
     * @return If the player is a geyser
     */
    public static boolean isBedrock(Player player) {
        if(isGeyserInstalled == null) {
            if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
                isGeyserInstalled = true;
                geyserApi = GeyserApi.api();
            }

            return false;
        }

        if(!isGeyserInstalled) {
            return false;
        }

        return geyserApi.isBedrockPlayer(player.getUniqueId());
    }

    /**
     * Create a players head item stack based on a player.
     *
     * @param player The player to base the head on.
     * @return The item stack containing the players head.
     */
    public static ItemStack getHead(Player player) {
        if(player == null) {
            return null;
        }

        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        skullMeta.setOwningPlayer(player);
        playerHead.setItemMeta(skullMeta);

        return playerHead;
    }

    /**
     * Safely teleport the player to a location
     * @param player The player to teleport
     * @param location The location to teleport the player
     */
    public static CompletableFuture<Void> teleport(Player player, Location location) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLater(InstanceHolder.plugin(), () -> {
            player.teleport(location);
            future.complete(null); // Mark the task as complete
        }, 1L);
        return future;
    }

    /**
     * Safely teleport the player to a location
     * @param player The player to teleport
     * @param location The location to teleport the player
     * @param callback Callback once the teleport is complete
     */
    public static void teleport(Player player, Location location, Runnable callback) {
        Bukkit.getScheduler().runTaskLater(InstanceHolder.plugin(), () -> {
            player.teleport(location);
            if(callback != null) {
                callback.run();
            }
        }, 1L);
    }

    public static void updateCacheName(String id, String name) {
        nameCache.put(id, name);
        config.set("players.cache." + id, name);

        try {
            config.save(configFile);
        } catch (IOException e) {
            error("Failed to save the players configuration file", e);
        }
    }

    public static String name(UUID id) {
        return name(String.valueOf(id));
    }

    public static String name(String id) {
        // 1. Check the cache
        if (nameCache.containsKey(id)) {
            return nameCache.get(id);
        }

        // 2. Check online players
        Player player = Bukkit.getPlayer(UUID.fromString(id));
        if (player != null) {
            String name = player.getName();
            updateCacheName(id, name); // Cache the name
            return name;
        }

        // 3. Check offline players asynchronously
        CompletableFuture<String> futureName = CompletableFuture.supplyAsync(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(id));
            return offlinePlayer.getName();
        });

        try {
            String name = futureName.get(5, TimeUnit.SECONDS); // Wait up to 5 seconds
            if (name != null) {
                updateCacheName(id, name); // Cache the name
            }
            return name;
        } catch (Exception e) {
            error("Lookup player name for UUID " + id + " failed", e);
            return null; // Return null if lookup fails
        }
    }
}

package dev.stemcraft.api.utils;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import org.bukkit.attribute.Attribute;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.geysermc.geyser.api.GeyserApi;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SCPlayer extends STEMCraftUtil {
    private static Boolean isGeyserInstalled = null;
    private static GeyserApi geyserApi = null;
    private static final Map<String, String> nameCache = new HashMap<>();
    private static File configFile;
    private static YamlConfiguration config;
    private static List<Player> hiddenPlayers = new ArrayList<>();

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

        STEMCraftAPI.api().registerEvent(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            if (hiddenPlayers.contains(player)) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.getUniqueId().equals(player.getUniqueId())) continue;

                    other.hidePlayer(InstanceHolder.plugin(), player);
                    player.hidePlayer(InstanceHolder.plugin(), other);
                }
            }
        });
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
     *
     * @param player   The player to teleport
     * @param location The location to teleport the player
     */
    public static void teleport(Player player, Location location) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLater(InstanceHolder.plugin(), () -> {
            player.teleport(location);
            future.complete(null); // Mark the task as complete
        }, 1L);
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

    public static String name(UUID id, String def) {
        return name(String.valueOf(id), def);
    }

    public static String name(String id) {
        return name(id, null);
    }

    public static String name(String id, String def) {
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
            return def;
        }
    }

    public static double maxHealth(Player player) {
        return Optional.ofNullable(
                        player.getAttribute(Attribute.MAX_HEALTH)
                )
                .map(AttributeInstance::getBaseValue)
                .orElse(20.0);
    }

    /**
     * Convert a duration string (1d) to seconds or epoch expiry time.
     *
     * @param player The player to give the item.
     * @param item The item to give.
     * @param dropOnFail Drop the item if the player inventory is full.
     * @param showMessage Show a message if giving the item failed and item was not dropped.
     * @return The result or null if error.
     */
    public static Boolean givePlayerItem(Player player, ItemStack item, Boolean dropOnFail, Boolean showMessage) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            if (dropOnFail) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                return true;
            } else if (showMessage) {
                error(player, "INV_NO_ROOM");
            }

            return false;
        }

        return true;
    }

    public static Boolean givePlayerItem(Player player, ItemStack item) {
        return givePlayerItem(player, item, false, true);
    }

    public static boolean isWhitelisted(Player player) {
        return STEMCraftAPI.api().gateKeeper().isWhitelisted(player);
    }

    public static void whitelist(Player player, boolean whitelist) {
        STEMCraftAPI.api().gateKeeper().whitelist(player, whitelist);
    }

    public static boolean isBlacklisted(Player player) {
        return STEMCraftAPI.api().gateKeeper().isWhitelisted(player);
    }

    public static void blacklist(Player player, boolean blacklist) {
        STEMCraftAPI.api().gateKeeper().whitelist(player, blacklist);
    }

    public static void hide(Player player) {
        if (hiddenPlayers.contains(player)) {
            return;
        }

        hiddenPlayers.add(player);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.hidePlayer(InstanceHolder.plugin(), player);
            player.hidePlayer(InstanceHolder.plugin(), other);
        }
    }

    public static void show(Player player) {
        if(!hiddenPlayers.contains(player)) {
            return;
        }

        hiddenPlayers.remove(player);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.showPlayer(InstanceHolder.plugin(), player);
            player.showPlayer(InstanceHolder.plugin(), other);
        }
    }
}

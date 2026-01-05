package dev.stemcraft.api.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.geysermc.geyser.api.GeyserApi;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class PlayerUtil {
    private static Boolean isGeyserInstalled = null;
    private static GeyserApi geyserApi = null;
    private static final Map<UUID, String> NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Get the player's maximum health.
     *
     * @param player The player to get the max health of.
     * @return The player's maximum health.
     */
    public static double getMaxHealth(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        return (inst != null) ? inst.getValue() : 20.0;
    }

    /**
     * Sets the player's health to their maximum health.
     */
    public static void setMaxHealth(Player player) {
        player.setHealth(getMaxHealth(player));
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

    /**
     * Convert a duration string (1d) to seconds or epoch expiry time.
     *
     * @param player The player to give the item.
     * @param item The item to give.
     * @param dropOnFail Drop the item if the player inventory is full.
     * @param showMessage Show a message if giving the item failed and item was not dropped.
     * @return The result or null if error.
     */
    public static Boolean give(Player player, ItemStack item, Boolean dropOnFail, Boolean showMessage) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            if (dropOnFail) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                return true;
            } else if (showMessage) {
                STEMCraftAPI.api().error(player, "INV_NO_ROOM");
            }

            return false;
        }

        return true;
    }

    public static Boolean give(Player player, ItemStack item) {
        return give(player, item, false, true);
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
     * Get the player's name from their UUID. If the player is not found
     * locally, an async lookup from Mojang is performed if a callback
     * is provided.
     */
    public static String name(UUID uuid, Consumer<String> onResolved) {
        // 1. Online player
        Player player = Bukkit.getPlayer(uuid);
        if(player != null) {
            String name = player.getName();

            if(onResolved != null) {
                onResolved.accept(name);
            }

            return name;
        }

        // 2. Offline player
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.hasPlayedBefore()) {
            String name = offline.getName();

            if(onResolved != null) {
                onResolved.accept(name);
            }

            return name;
        }

        // 3. Internal cache
        String cached = NAME_CACHE.get(uuid);
        if (cached != null) {
            if(onResolved != null) {
                onResolved.accept(cached);
            }

            return cached;
        }

        // 4. Async lookup if requested
        if (onResolved != null) {
            lookupNameFromMojang(uuid).thenAccept(name -> {
                if (name != null) {
                    NAME_CACHE.put(uuid, name);
                    onResolved.accept(name);
                }
            });
        }

        return null;
    }

    public static String name(UUID uuid) {
        return name(uuid, null);
    }

    /**
     * Async lookup of player name from Mojang API
     */
    private static CompletableFuture<String> lookupNameFromMojang(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String id = uuid.toString().replace("-", "");
                URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) return null;

                try (InputStream in = conn.getInputStream();
                     InputStreamReader reader = new InputStreamReader(in)) {

                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    return json.has("name") ? json.get("name").getAsString() : null;
                }
            } catch (Exception e) {
                return null;
            }
        });
    }
}

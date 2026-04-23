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

package dev.stemcraft.api.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.api.STEMCraftAPI;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Utility class for player-related operations.
 */
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
     *
     * @param player The player to set the health of.
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
        if (!playerHead.setItemMeta(skullMeta)) {
            throw new IllegalStateException("Unable to apply skull metadata");
        }

        return playerHead;
    }

    /**
     * Safely teleport the player to a location.
     *
     * @param player The player to teleport.
     * @param location The location to teleport the player.
     */
    public static void teleport(Player player, Location location) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        STEMCraftAPI.api().tasks().nextTick(() -> {
            player.teleport(location);
            future.complete(null); // Mark the task as complete
        });
    }

    /**
     * Safely teleport the player to a location.
     * @param player The player to teleport.
     * @param location The location to teleport the player.
     * @param callback Callback once the teleport is complete.
     */
    public static void teleport(Player player, Location location, Runnable callback) {
        STEMCraftAPI.api().tasks().nextTick(() -> {
            player.teleport(location);
            if(callback != null) {
                callback.run();
            }
        });
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
                STEMCraftAPI.api().messages().error(player, "INV_NO_ROOM");
            }

            return false;
        }

        return true;
    }

    public static Boolean give(Player player, ItemStack item) {
        return give(player, item, false, true);
    }

    /**
     * Test if a player is a BedRock player.
     *
     * @param player The player to test.
     * @return If the player is a geyser.
     */
    public static boolean isBedrock(Player player) {
        if (player == null) {
            return false;
        }

        if (!ensureGeyserInitialized()) {
            return false;
        }

        return geyserApi.isBedrockPlayer(player.getUniqueId());
    }

    /**
     * Test if a UUID belongs to a Bedrock player.
     *
     * @param uuid The player UUID to test.
     * @return True if the UUID belongs to a Bedrock player.
     */
    public static boolean isBedrock(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        if (!ensureGeyserInitialized()) {
            return false;
        }

        return geyserApi.isBedrockPlayer(uuid);
    }

    /**
     * Check whether the given player is effectively whitelisted according to
     * the active server whitelist authority.
     *
     * @param player The player to test.
     * @return True if the player is whitelisted.
     */
    public static boolean isWhitelisted(Player player) {
        if (player == null) {
            return false;
        }

        return STEMCraftAPI.api().players().isWhitelisted(player);
    }

    /**
     * Check whether the given identity is effectively whitelisted according to
     * the active server whitelist authority.
     *
     * @param uuid The player UUID.
     * @param username The player username.
     * @param platform The player platform, such as java or bedrock.
     * @return True if the identity is whitelisted.
     */
    public static boolean isWhitelisted(@Nullable UUID uuid, @Nullable String username, @Nullable String platform) {
        return STEMCraftAPI.api().players().isWhitelisted(uuid, username, platform);
    }

    /**
     * Check the vanilla Bukkit whitelist, respecting whether whitelist
     * enforcement is enabled at all on the server.
     *
     * @param uuid The player UUID.
     * @param username The player username.
     * @return True if vanilla whitelist rules would allow the player.
     */
    public static boolean isWhitelistedVanilla(@Nullable UUID uuid, @Nullable String username) {
        if (!Bukkit.hasWhitelist()) {
            return true;
        }

        if (uuid != null) {
            return Bukkit.getOfflinePlayer(uuid).isWhitelisted();
        }

        if (username != null && !username.isBlank()) {
            return Bukkit.getOfflinePlayer(username).isWhitelisted();
        }

        return false;
    }

    /**
     * Get the player's name from their UUID. If the player is not found
     * locally, an async lookup from Mojang is performed if a callback
     * is provided.
     *
     * @param uuid The player's UUID.
     * @param onResolved Callback when the name is resolved (can be null).
     * @return The player's name if found locally, otherwise null.
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

    public static @Nullable ItemStack @NotNull [] getArmor(Player player) {
        return player.getInventory().getArmorContents();
    }

    public static int getArmorLength(@NotNull Player player) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        return armorContents.length;
    }

    public static void clearArmor(@NotNull Player player) {
        player.getInventory().setArmorContents(new ItemStack[4]);
    }

    /**
     * Async lookup of player name from Mojang API.
     *
     * @param uuid The player's UUID.
     * @return A CompletableFuture with the player's name or null if not found.
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
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {

                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    return json.has("name") ? json.get("name").getAsString() : null;
                }
            } catch (java.io.IOException | IllegalStateException e) {
                return null;
            }
        });
    }

    private static boolean ensureGeyserInitialized() {
        if (isGeyserInstalled != null) {
            return isGeyserInstalled;
        }

        synchronized (PlayerUtil.class) {
            if (isGeyserInstalled == null) {
                if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
                    geyserApi = GeyserApi.api();
                    isGeyserInstalled = true;
                } else {
                    isGeyserInstalled = false;
                }
            }
            return isGeyserInstalled;
        }
    }
}

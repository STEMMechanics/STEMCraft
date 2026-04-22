package dev.stemcraft.minigame.skyblock;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SkyBlockPlayerState {
    private final Location location;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean flying;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final int level;
    private final float exp;
    private final int fireTicks;
    private final float fallDistance;
    private final ItemStack[] storageContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;

    private SkyBlockPlayerState(
        @Nullable Location location,
        @NotNull GameMode gameMode,
        boolean allowFlight,
        boolean flying,
        double health,
        int foodLevel,
        float saturation,
        float exhaustion,
        int level,
        float exp,
        int fireTicks,
        float fallDistance,
        ItemStack[] storageContents,
        ItemStack[] armorContents,
        @Nullable ItemStack offHand
    ) {
        this.location = location == null ? null : location.clone();
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.level = level;
        this.exp = exp;
        this.fireTicks = fireTicks;
        this.fallDistance = fallDistance;
        this.storageContents = cloneItems(storageContents);
        this.armorContents = cloneItems(armorContents);
        this.offHand = offHand == null ? null : offHand.clone();
    }

    static @NotNull SkyBlockPlayerState capture(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();
        return new SkyBlockPlayerState(
            player.getLocation(),
            player.getGameMode(),
            player.getAllowFlight(),
            player.isFlying(),
            player.getHealth(),
            player.getFoodLevel(),
            player.getSaturation(),
            player.getExhaustion(),
            player.getLevel(),
            player.getExp(),
            player.getFireTicks(),
            player.getFallDistance(),
            inventory.getStorageContents(),
            inventory.getArmorContents(),
            inventory.getItemInOffHand()
        );
    }

    static @Nullable SkyBlockPlayerState load(@NotNull ConfigSection stateSection, @Nullable World fallbackWorld) {
        if (!stateSection.contains("gamemode")) {
            return null;
        }

        String worldName = stateSection.getString("location-world", fallbackWorld == null ? "" : fallbackWorld.getName());
        World world = worldName.isBlank() ? fallbackWorld : Bukkit.getWorld(worldName);
        Location location = loadLocation(stateSection, world);
        if (location == null && fallbackWorld != null) {
            location = fallbackWorld.getSpawnLocation();
        }

        GameMode gameMode = parseGameMode(stateSection.getString("gamemode", GameMode.SURVIVAL.name()));
        ItemStack[] storage = toItems(stateSection.getList("storage"));
        ItemStack[] armor = toItems(stateSection.getList("armor"));
        ItemStack offHand = stateSection.get("offhand") instanceof ItemStack item ? item.clone() : null;

        return new SkyBlockPlayerState(
            location,
            gameMode,
            stateSection.getBoolean("allow-flight", false),
            stateSection.getBoolean("flying", false),
            stateSection.getDouble("health", 20.0d),
            stateSection.getInt("food-level", 20),
            stateSection.getFloat("saturation", 20.0f),
            stateSection.getFloat("exhaustion", 0.0f),
            stateSection.getInt("level", 0),
            stateSection.getFloat("exp", 0.0f),
            stateSection.getInt("fire-ticks", 0),
            stateSection.getFloat("fall-distance", 0.0f),
            storage,
            armor,
            offHand
        );
    }

    void save(@NotNull ConfigSection stateSection) {
        if (location != null && location.getWorld() != null) {
            stateSection.set("location", LocationUtil.serialize(location, false, true));
            stateSection.set("location-world", location.getWorld().getName());
        } else {
            stateSection.set("location", null);
            stateSection.set("location-world", null);
        }
        stateSection.set("gamemode", gameMode.name());
        stateSection.set("allow-flight", allowFlight);
        stateSection.set("flying", flying);
        stateSection.set("health", health);
        stateSection.set("food-level", foodLevel);
        stateSection.set("saturation", saturation);
        stateSection.set("exhaustion", exhaustion);
        stateSection.set("level", level);
        stateSection.set("exp", exp);
        stateSection.set("fire-ticks", fireTicks);
        stateSection.set("fall-distance", fallDistance);
        stateSection.set("storage", toList(storageContents));
        stateSection.set("armor", toList(armorContents));
        stateSection.set("offhand", offHand == null ? null : offHand.clone());
    }

    void apply(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setStorageContents(cloneItems(storageContents));
        inventory.setArmorContents(cloneItems(armorContents));
        inventory.setItemInOffHand(offHand == null ? null : offHand.clone());

        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying);
        player.setFireTicks(fireTicks);
        player.setFallDistance(fallDistance);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setLevel(level);
        player.setExp(exp);
        player.setHealth(Math.clamp(health, 1.0d, PlayerUtil.getMaxHealth(player)));
        if (location != null) {
            player.teleport(location);
        }
    }

    public @NotNull SkyBlockPlayerState copy() {
        return new SkyBlockPlayerState(
                this.location,
                this.gameMode,
                this.allowFlight,
                this.flying,
                this.health,
                this.foodLevel,
                this.saturation,
                this.exhaustion,
                this.level,
                this.exp,
                this.fireTicks,
                this.fallDistance,
                this.storageContents,
                this.armorContents,
                this.offHand
        );
    }

    private static @Nullable Location loadLocation(@NotNull ConfigSection section, @Nullable World defaultWorld) {
        String serialized = section.getString("location");
        if (serialized.isBlank()) {
            return null;
        }
        return defaultWorld == null ? LocationUtil.deserialize(serialized) : LocationUtil.deserialize(serialized, defaultWorld);
    }

    private static @NotNull GameMode parseGameMode(@NotNull String raw) {
        try {
            return GameMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return GameMode.SURVIVAL;
        }
    }

    private static @NotNull ItemStack[] cloneItems(ItemStack @Nullable [] source) {
        if (source == null) {
            return new ItemStack[0];
        }

        ItemStack[] clone = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            clone[i] = source[i] == null ? null : source[i].clone();
        }
        return clone;
    }

    private static @NotNull ItemStack[] toItems(@Nullable List<?> rawItems) {
        if (rawItems == null) {
            return new ItemStack[0];
        }

        List<ItemStack> items = new ArrayList<>();
        for (Object raw : rawItems) {
            items.add(raw instanceof ItemStack item ? item.clone() : null);
        }
        return items.toArray(new ItemStack[0]);
    }

    private static @NotNull List<ItemStack> toList(ItemStack[] items) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : items) {
            list.add(item == null ? null : item.clone());
        }
        return list;
    }
}

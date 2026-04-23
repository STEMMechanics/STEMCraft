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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.audit.AuditService;
import dev.stemcraft.api.util.ByteFormat;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.StringUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Implementation of the AuditService for logging player actions.
 */
public class AuditServiceImpl extends BaseService implements AuditService {
    private File logDirectory;
    private int maxDays = 28;
    private int tpsThreshold = 15;
    private long memoryThreshold = 5 * 1024 * 1024; // 50 MB
    private List<Pattern> trackedPlacePatterns = new ArrayList<>();
    private List<Pattern> trackedBreakPatterns = new ArrayList<>();
    private static final UUID SERVER_UUID = new UUID(0L, 0L);

    private final Map<UUID, Deque<PlayerLogEntry>> buffers = new ConcurrentHashMap<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Constructor.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public AuditServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "player_logs");
    }

    @Override
    protected List<String> getConfigPathCandidates() {
        return List.of("player_logs", "player-logs", "auditing");
    }

    /**
     * Enable the service.
     */
    @Override
    public void onEnable() {
        if(!getConfigSection().getBoolean("enabled", true)) {
            return;
        }

        logDirectory = new File(plugin.getDataFolder(), "audit-logs");
        if (!logDirectory.exists() && !logDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create audit logs directory");
            return;
        }

        maxDays = getConfigSection().getInt("max_days", 28);
        tpsThreshold = getConfigSection().getInt("tps_threshold", 15);
        memoryThreshold = ByteFormat.toBytes(getConfigSection().getString("memory_threshold", "5MB"));
        trackedPlacePatterns = loadPatterns("blocks.place");
        trackedBreakPatterns = loadPatterns("blocks.break");



        // periodic performance check every 2 minutes
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::checkPerformance,
                20L * 60 * 2,
                20L * 60 * 2
        );

        // periodic flush every 2 minutes
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::flushAll,
                20L * 60 * 2,
                20L * 60 * 2
        );

        api.events().register(AsyncChatEvent.class, event -> log(event.getPlayer(), "CHAT: " + event.message()));

        api.events().register(PlayerCommandPreprocessEvent.class, event -> log(event.getPlayer(), "COMMAND: " + event.getMessage()));

        api.events().register(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "JOIN - UUID: " + player.getUniqueId());
        });

        api.events().register(PlayerQuitEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "LEAVE");
            flush(player);
        });

        api.events().register(PlayerKickEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "KICK: " + event.reason());
            flush(player);
        });

        api.events().register(SignChangeEvent.class, event -> {
            Player player = event.getPlayer();
            List<Component> lines = event.lines();
            log(player, "SIGN: " + StringUtil.joinPlainText(lines, " | "));
        });

        api.events().register(PlayerTeleportEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "TELEPORT: " + event.getFrom().getBlockX() + "," + event.getFrom().getBlockY() + "," + event.getFrom().getBlockZ()
                    + " -> " + event.getTo().getBlockX() + "," + event.getTo().getBlockY() + "," + event.getTo().getBlockZ());
        });

        api.events().register(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            log(player, "DEATH: " + event.deathMessage());
        });

        api.events().register(PlayerLevelChangeEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "LEVEL: " + event.getOldLevel() + " -> " + event.getNewLevel());
        });

        api.events().register(BlockPlaceEvent.class, event -> {
            Material type = event.getBlockPlaced().getType();

            if (!matches(trackedPlacePatterns, type)) return;

            log(event.getPlayer(),
                    "BLOCK PLACE: " + type + " at " + event.getBlockPlaced().getLocation());
        });

        api.events().register(BlockBreakEvent.class, event -> {
            Material type = event.getBlock().getType();

            if (!matches(trackedBreakPatterns, type)) return;

            log(event.getPlayer(),
                    "BLOCK BREAK: " + type + " at " + event.getBlock().getLocation());
        });

        api.events().register(PlayerBucketFillEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "BUCKET FILL: " + event.getBucket());
        });

        api.events().register(PlayerBucketEmptyEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "BUCKET EMPTY: " + event.getBucket());
        });

        api.events().register(EntityPickupItemEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                log(player, "ITEM PICKUP: " + event.getItem().getItemStack());
            }
        });

        api.events().register(PlayerDropItemEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "ITEM DROP: " + event.getItemDrop().getItemStack());
        });

        api.events().register(EnchantItemEvent.class, event -> {
            Player player = event.getEnchanter();
            log(player, "ENCHANT: " + event.getItem());
        });

        api.events().register(PlayerEditBookEvent.class, event -> {
            Player player = event.getPlayer();
            event.getNewBookMeta();
            log(player, "BOOK EDIT: " + event.getPreviousBookMeta().getTitle() + " -> " +
                    event.getNewBookMeta().getTitle());
        });

        api.events().register(FurnaceExtractEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "FURNACE: EXTRACT " + event.getItemType() + " x" + event.getItemAmount());
        });

        api.events().register(PlayerGameModeChangeEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "GAMEMODE: " + player.getGameMode() + " -> " + event.getNewGameMode());
        });

        api.events().register(CraftItemEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                log(player, "CRAFT: " + event.getRecipe().getResult());
            }
        });

        api.events().register(EntityDeathEvent.class, event -> {
            if (event.getEntity().getKiller() != null) {
                Player killer = event.getEntity().getKiller();
                log(killer, "ENTITY DEATH: " + event.getEntity().getType());
            }
        });

        api.events().register(EntityExplodeEvent.class, event -> {
            if (event.getEntityType().toString().contains("TNT")) {
                if (event.getEntity() instanceof Player player) {
                    log(player, "PRIMED TNT at " + event.getLocation());
                }
            }
        });

        api.events().register(InventoryOpenEvent.class, event -> {
            if (event.getPlayer() instanceof Player player &&
                    event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.CHEST) {
                log(player, "CHEST OPEN: " + event.getInventory().getLocation());
            }
        });

        api.events().register(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            File file = new File(logDirectory, player.getName() + ".log");
            if (!file.exists()) {
                log(player, "REGISTER");
            }
        });

        api.events().register(ServerCommandEvent.class, event -> {
            if(event.getSender() instanceof BlockCommandSender) {
                log(null, "COMMAND BLOCK: " + event.getCommand());
            } else {
                log(null, "CONSOLE COMMAND: " + event.getCommand());
            }
        });

        api.events().register(ServerCommandEvent.class, event -> log(null, "RCON COMMAND: " + event.getCommand()));

        api.events().register(PortalCreateEvent.class, event -> {
            if(event.getBlocks().isEmpty()) {
                log(null, "PORTAL CREATE at " + event.getReason() + " in " + event.getWorld().getName());
                return;
            }

            Location loc = event.getBlocks().getFirst().getLocation();
            log(null, "PORTAL CREATE at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() +
                    " in " + event.getWorld().getName() + " due to " + event.getReason());
        });
    }

    /**
     * Disable the service.
     */
    @Override
    public void onDisable() {
        flushAll();
    }

    /**
     * Load regex patterns from config.
     *
     * @param path Config path.
     * @return List of compiled patterns.
     */
    private List<Pattern> loadPatterns(String path) {
        List<Pattern> list = new ArrayList<>();

        for (String raw : getConfigSection().getStringList(path)) {
            try {
                list.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                plugin.getLogger().warning("[PlayerLog] Invalid regex in " + path + ": " + raw);
            }
        }

        return list;
    }

    /**
     * Check if material matches any pattern in the list.
     *
     * @param list List of patterns.
     * @param material Material to check.
     * @return True if matches, false otherwise.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean matches(Collection<Pattern> list, Material material) {
        if (list.isEmpty()) return true; // empty = log everything

        String name = material.name();
        for (Pattern p : list) {
            if (p.matcher(name).matches()) return true;
        }
        return false;
    }

    /**
     * Log action.
     *
     * @param player The player (null for server).
     * @param action The action description.
     * @param placeholders Optional placeholders.
     */
    @Override
    public void log(@Nullable Player player, @NonNull String action, String... placeholders) {
        UUID id;
        String name;

        if (player == null) {
            id = SERVER_UUID;
            name = "_SERVER_";
        } else {
            id = player.getUniqueId();
            name = player.getName();
        }

        Deque<PlayerLogEntry> deque = buffers.computeIfAbsent(
                id,
                x -> new ArrayDeque<>()
        );

        deque.addFirst(new PlayerLogEntry(Instant.now(), PlaceholderUtil.apply(action, placeholders), name));
    }

    /**
     * Flush all buffers to disk.
     */
    private void flushAll() {
        buffers.forEach((uuid, deque) -> {
            String name = deque.peekFirst() != null ? deque.peekFirst().playerName() : null;
            if (name != null) {
                flush(uuid, name);
            }
        });
    }

    /**
     * Flush specific player buffer to disk.
     *
     * @param player The player.
     */
    private void flush(Player player) {
        if (player == null) return;
        flush(player.getUniqueId(), player.getName());
    }

    /**
     * Flush specific player buffer to disk.
     *
     * @param uuid The player UUID.
     * @param playerName The player name.
     */
    private void flush(UUID uuid, String playerName) {
        Deque<PlayerLogEntry> deque = buffers.get(uuid);
        Instant cutoff = Instant.now().minus(maxDays, ChronoUnit.DAYS);

        File file = new File(logDirectory, playerName + ".log");
        List<PlayerLogEntry> merged = new ArrayList<>();

        // 1) Load existing entries from disk (if any)
        if (file.exists()) {
            try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Expect "yyyy-MM-dd HH:mm:ss <action>"
                    if (line.length() < 20) continue; // too short to contain timestamp
                    String tsPart = line.substring(0, 19);
                    String action = line.length() > 20 ? line.substring(20) : "";

                    try {
                        LocalDateTime ldt = LocalDateTime.parse(tsPart, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        Instant ts = ldt.atZone(ZoneId.systemDefault()).toInstant();

                        if (!ts.isBefore(cutoff)) {
                            merged.add(new PlayerLogEntry(ts, action, playerName));
                        }
                    } catch (DateTimeParseException ignored) {
                        // bad line, skip
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to read existing log for " + playerName + ": " + e.getMessage());
            }
        }

        // 2) Add in-memory entries (also pruning by maxDays)
        if (deque != null && !deque.isEmpty()) {
            for (PlayerLogEntry entry : deque) {
                if (!entry.timestamp().isBefore(cutoff)) {
                    merged.add(entry);
                }
            }
            deque.clear(); // important to avoid re-logging the same entries
        }

        // 3) Nothing left? Optionally delete file
//        if (merged.isEmpty()) {
//            if (file.exists()) {
//                // you can delete or leave an empty file; your call
//                // file.delete();
//            }
//            return;
//        }

        // 4) Sort newest first
        merged.sort(Comparator.comparing(PlayerLogEntry::timestamp).reversed());

        // 5) Rewrite file with merged entries
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (PlayerLogEntry entry : merged) {
                String line = formatter.format(entry.timestamp()) + " " + entry.action();
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write player log for " + playerName + ": " + e.getMessage());
        }
    }

    /**
     * Get current server TPS.
     *
     * @return TPS or -1.0 if unavailable.
     */
    private double getCurrentTPS() {
        try {
            double[] tps = Bukkit.getServer().getTPS();
            return tps.length > 0 ? tps[0] : -1.0;
        } catch (NoSuchMethodError e) {
            return -1.0;
        }
    }

    /**
     * Check server performance and log warnings if thresholds are breached.
     */
    private void checkPerformance() {
        double tps = getCurrentTPS();
        Runtime rt = Runtime.getRuntime();
        long freeMemory = rt.freeMemory();

        if (tps >= 0 && tps < tpsThreshold) {
            log(null, "TPS WARNING: " + tps);
        }

        if (freeMemory < memoryThreshold) {
            log(null, "MEMORY WARNING: " + freeMemory + " bytes free");
        }
    }

    /**
     * Record representing a player log entry.
     */
    private record PlayerLogEntry(Instant timestamp, String action, String playerName) { }
}

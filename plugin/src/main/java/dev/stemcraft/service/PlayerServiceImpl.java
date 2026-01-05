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
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.util.ByteFormat;
import dev.stemcraft.api.util.DirectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class PlayerServiceImpl extends BaseService implements PlayerService {
    private final List<Player> hiddenPlayers = new ArrayList<>();

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

    public PlayerServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        if(!plugin.getConfig().getBoolean("player-log.enabled", true)) {
            return;
        }

        logDirectory = new File(plugin.getDataFolder(), "player_logs");
        if (!logDirectory.exists() && !logDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create player logs directory");
            return;
        }

        maxDays = plugin.getConfig().getInt("player_log.max-days", 28);
        tpsThreshold = plugin.getConfig().getInt("player_log.tps_threshold", 15);
        memoryThreshold = ByteFormat.toBytes(plugin.getConfig().getString("player-log.memory_threshold", "5MB"));
        trackedPlacePatterns = loadPatterns("player_log.blocks.place");
        trackedBreakPatterns = loadPatterns("player_log.blocks.break");



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

        api.events().register(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            if (hiddenPlayers.contains(player)) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.getUniqueId().equals(player.getUniqueId())) continue;

                    other.hidePlayer(plugin, player);
                    player.hidePlayer(plugin, other);
                }
            }
        });
    }

    @Override
    public void onDisable() {
        flushAll();
    }

    @Override
    public void log(Player player, String action, String... placeholders) {

    }

    @Override
    public void hide(Player player) {
        if(player == null) return;
        if(hiddenPlayers.contains(player)) return;

        hiddenPlayers.add(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.hidePlayer(plugin, player);
        }
    }

    @Override
    public void show(Player player) {
        if(player == null) return;
        if(!hiddenPlayers.contains(player)) return;

        hiddenPlayers.remove(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.showPlayer(plugin, player);
        }
    }


    private List<Pattern> loadPatterns(String path) {
        List<Pattern> list = new ArrayList<>();

        for (String raw : plugin.getConfig().getStringList(path)) {
            try {
                list.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                plugin.getLogger().warning("[PlayerLog] Invalid regex in " + path + ": " + raw);
            }
        }

        return list;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean matches(List<Pattern> list, Material material) {
        if (list.isEmpty()) return true; // empty = log everything

        String name = material.name();
        for (Pattern p : list) {
            if (p.matcher(name).matches()) return true;
        }
        return false;
    }

    @Override
    public void log(Player player, String action, String... placeholders) {
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

        deque.addFirst(new PlayerLogEntry(Instant.now(), DirectionUtil.placeholders(action, placeholders), name));
    }

    private void flushAll() {
        buffers.forEach((uuid, deque) -> {
            String name = deque.peekFirst() != null ? deque.peekFirst().playerName() : null;
            if (name != null) {
                flush(uuid, name);
            }
        });
    }

    private void flush(Player player) {
        if (player == null) return;
        flush(player.getUniqueId(), player.getName());
    }

    private void flush(UUID uuid, String playerName) {
        Deque<PlayerLogEntry> deque = buffers.get(uuid);
        Instant cutoff = Instant.now().minus(maxDays, ChronoUnit.DAYS);

        File file = new File(logDirectory, playerName + ".log");
        List<PlayerLogEntry> merged = new ArrayList<>();

        // 1) Load existing entries from disk (if any)
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
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
        if (merged.isEmpty()) {
            if (file.exists()) {
                // you can delete or leave an empty file; your call
                // file.delete();
            }
            return;
        }

        // 4) Sort newest first
        merged.sort(Comparator.comparing(PlayerLogEntry::timestamp).reversed());

        // 5) Rewrite file with merged entries
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            for (PlayerLogEntry entry : merged) {
                String line = formatter.format(entry.timestamp()) + " " + entry.action();
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write player log for " + playerName + ": " + e.getMessage());
        }
    }

    // region Event handlers

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        log(event.getPlayer(), "CHAT: " + event.getMessage());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        log(event.getPlayer(), "COMMAND: " + event.getMessage());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        log(player, "JOIN - UUID: " + player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        log(player, "LEAVE");
        flush(player);
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        log(player, "KICK: " + event.getReason());
        flush(player);
    }

    @EventHandler
    public void onSign(SignChangeEvent event) {
        Player player = event.getPlayer();
        String[] lines = event.getLines();
        log(player, "SIGN: " + String.join(" | ", lines));
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        log(player, "TELEPORT: " + event.getFrom().getBlockX() + "," + event.getFrom().getBlockY() + "," + event.getFrom().getBlockZ()
                + " -> " + event.getTo().getBlockX() + "," + event.getTo().getBlockY() + "," + event.getTo().getBlockZ());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        log(player, "DEATH: " + event.getDeathMessage());
    }

    @EventHandler
    public void onLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        log(player, "LEVEL: " + event.getOldLevel() + " -> " + event.getNewLevel());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();

        if (!matches(trackedPlacePatterns, type)) return;

        log(event.getPlayer(),
                "BLOCK PLACE: " + type + " at " + event.getBlockPlaced().getLocation());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();

        if (!matches(trackedBreakPatterns, type)) return;

        log(event.getPlayer(),
                "BLOCK BREAK: " + type + " at " + event.getBlock().getLocation());
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        log(player, "BUCKET FILL: " + event.getBucket());
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        log(player, "BUCKET EMPTY: " + event.getBucket());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            log(player, "ITEM PICKUP: " + event.getItem().getItemStack());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        log(player, "ITEM DROP: " + event.getItemDrop().getItemStack());
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        log(player, "ENCHANT: " + event.getItem());
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        event.getNewBookMeta();
        log(player, "BOOK EDIT: " + event.getPreviousBookMeta().getTitle() + " -> " +
                event.getNewBookMeta().getTitle());
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        log(player, "FURNACE: EXTRACT " + event.getItemType() + " x" + event.getItemAmount());
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        log(player, "GAMEMODE: " + player.getGameMode() + " -> " + event.getNewGameMode());
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            log(player, "CRAFT: " + event.getRecipe().getResult());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();
            log(killer, "ENTITY DEATH: " + event.getEntity().getType());
        }
    }

    @EventHandler
    public void onTNTPrime(EntityExplodeEvent event) {
        if (event.getEntityType().toString().contains("TNT")) {
            if (event.getEntity() instanceof Player player) {
                log(player, "PRIMED TNT at " + event.getLocation());
            }
        }
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player &&
                event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.CHEST) {
            log(player, "CHEST OPEN: " + event.getInventory().getLocation());
        }
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        File file = new File(logDirectory, player.getName() + ".log");
        if (!file.exists()) {
            log(player, "REGISTER");
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        if(event.getSender() instanceof BlockCommandSender) {
            log(null, "COMMAND BLOCK: " + event.getCommand());
        } else {
            log(null, "CONSOLE COMMAND: " + event.getCommand());
        }
    }

    @EventHandler
    public void onRconCommand(ServerCommandEvent event) {
        log(null, "RCON COMMAND: " + event.getCommand());
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        if(event.getBlocks().isEmpty()) {
            log(null, "PORTAL CREATE at " + event.getReason() + " in " + event.getWorld().getName());
            return;
        }

        Location loc = event.getBlocks().getFirst().getLocation();
        log(null, "PORTAL CREATE at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() +
                " in " + event.getWorld().getName() + " due to " + event.getReason());
    }

    private double getCurrentTPS() {
        try {
            double[] tps = Bukkit.getServer().getTPS();
            return tps.length > 0 ? tps[0] : -1.0;
        } catch (NoSuchMethodError e) {
            return -1.0;
        }
    }

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

    private record PlayerLogEntry(Instant timestamp, String action, String playerName) { }
}

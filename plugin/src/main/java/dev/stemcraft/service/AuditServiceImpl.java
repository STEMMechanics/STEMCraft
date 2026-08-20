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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.audit.AuditService;
import dev.stemcraft.api.util.ByteFormat;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.StringUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final List<String> COMMUNICATION_CATEGORIES = List.of("chat", "sign", "book", "command");
    private File logDirectory;
    private int maxDays = 28;
    private int tpsThreshold = 15;
    private long memoryThreshold = 5 * 1024 * 1024; // 50 MB
    private int queryDefaultLimit = 20;
    private int queryMaxLimit = 100;
    private boolean active;
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
            active = false;
            return;
        }

        logDirectory = new File(plugin.getDataFolder(), "audit-logs");
        if (!logDirectory.exists() && !logDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create audit logs directory");
            active = false;
            return;
        }
        active = true;

        maxDays = getConfigSection().getInt("max_days", 28);
        tpsThreshold = getConfigSection().getInt("tps_threshold", 15);
        memoryThreshold = ByteFormat.toBytes(getConfigSection().getString("memory_threshold", "5MB"));
        queryDefaultLimit = getConfigSection().getInt("query_default_limit", 20);
        queryMaxLimit = getConfigSection().getInt("query_max_limit", 100);
        trackedPlacePatterns = loadPatterns("blocks.place");
        trackedBreakPatterns = loadPatterns("blocks.break");
        ensureStructuredStorage();
        registerCommands();

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

        api.events().register(AsyncChatEvent.class, event -> {
            if (plugin.firstJoin() != null && plugin.firstJoin().hasActiveSession(event.getPlayer().getUniqueId())) {
                return;
            }
            log(event.getPlayer(), "CHAT: " + PLAIN.serialize(event.message()));
        });

        api.events().register(PlayerCommandPreprocessEvent.class, event -> {
            if (plugin.firstJoin() != null && plugin.firstJoin().hasActiveSession(event.getPlayer().getUniqueId())) {
                return;
            }
            log(event.getPlayer(), "COMMAND: " + event.getMessage());
            recordEvent("command", "player", event.getPlayer(), event.getMessage(), event.getPlayer().getLocation(), Map.of());
        });

        api.events().register(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "JOIN - UUID: " + player.getUniqueId());
            recordEvent("join", "player", player, player.getUniqueId().toString(), player.getLocation(), Map.of());
        });

        api.events().register(PlayerQuitEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "LEAVE");
            recordEvent("leave", "player", player, null, player.getLocation(), Map.of());
            flush(player);
        });

        api.events().register(PlayerKickEvent.class, event -> {
            Player player = event.getPlayer();
            log(player, "KICK: " + event.reason());
            recordEvent("kick", event.getCause().name().toLowerCase(Locale.ROOT), player, PLAIN.serialize(event.reason()), player.getLocation(), Map.of(
                "cause", event.getCause().name()
            ));
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
        active = false;
        flushAll();
    }

    @Override
    public void onSave() {
        flushAll();
    }

    public void recordCommunication(String category,
                                    @Nullable Player player,
                                    @Nullable String content,
                                    @Nullable String effectiveContent,
                                    @Nullable Location location,
                                    @Nullable Map<String, Object> details) {
        if (!active) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (details != null && !details.isEmpty()) {
            payload.putAll(details);
        }
        if (effectiveContent != null && !Objects.equals(effectiveContent, content)) {
            payload.put("effective_content", effectiveContent);
        }
        recordEvent(category, "player", player, content, location, payload);
    }

    void recordEvent(String category,
                     String subtype,
                     @Nullable Player player,
                     @Nullable String content,
                     @Nullable Location location,
                     @Nullable Map<String, Object> details) {
        if (!active) {
            return;
        }
        UUID actorUuid = player != null ? player.getUniqueId() : SERVER_UUID;
        String actorName = player != null ? player.getName() : "_SERVER_";
        recordEvent(category, subtype, actorUuid, actorName, content, location, details);
    }

    private void recordEvent(String category,
                             String subtype,
                             @Nullable UUID actorUuid,
                             @Nullable String actorName,
                             @Nullable String content,
                             @Nullable Location location,
                             @Nullable Map<String, Object> details) {
        String world = location != null && location.getWorld() != null ? location.getWorld().getName() : null;
        Double x = location != null ? location.getX() : null;
        Double y = location != null ? location.getY() : null;
        Double z = location != null ? location.getZ() : null;
        String detailsJson = details == null || details.isEmpty() ? null : GSON.toJson(details);

        api.database().update(
            "INSERT INTO audit_events (occurred_at, category, subtype, actor_uuid, actor_name, content, world, x, y, z, details_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setString(2, normalizeAuditText(category));
                ps.setString(3, normalizeAuditText(subtype));
                ps.setString(4, actorUuid != null ? actorUuid.toString() : null);
                ps.setString(5, actorName == null || actorName.isBlank() ? "_UNKNOWN_" : actorName);
                ps.setString(6, content);
                ps.setString(7, world);
                if (x == null) {
                    ps.setNull(8, java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(8, x);
                }
                if (y == null) {
                    ps.setNull(9, java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(9, y);
                }
                if (z == null) {
                    ps.setNull(10, java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(10, z);
                }
                ps.setString(11, detailsJson);
            }
        );
    }

    private void ensureStructuredStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS audit_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "occurred_at INTEGER NOT NULL," +
                "category TEXT NOT NULL," +
                "subtype TEXT NOT NULL," +
                "actor_uuid TEXT," +
                "actor_name TEXT NOT NULL," +
                "content TEXT," +
                "world TEXT," +
                "x REAL," +
                "y REAL," +
                "z REAL," +
                "details_json TEXT" +
            ");"
        );
        api.database().execute("CREATE INDEX IF NOT EXISTS audit_events_occurred_at_idx ON audit_events(occurred_at);");
        api.database().execute("CREATE INDEX IF NOT EXISTS audit_events_actor_name_idx ON audit_events(actor_name);");
        api.database().execute("CREATE INDEX IF NOT EXISTS audit_events_category_idx ON audit_events(category);");
    }

    private void registerCommands() {
        api.commands().create("audit")
            .description("Review audit events.")
            .usage("/audit <list|show|context> [options]")
            .permission("stemcraft.command.audit")
            .tabCompletion("list")
            .tabCompletion("show", "{int}")
            .tabCompletion("context", "{int}")
            .executor((unused, cmd, ctx) -> {
                if (ctx.args().isEmpty()) {
                    handleAuditList(ctx);
                    return;
                }

                String subcommand = ctx.getArgLower(0);
                if (subcommand == null) {
                    handleAuditList(ctx);
                    return;
                }

                switch (subcommand) {
                    case "list" -> {
                        ctx.dropArg();
                        handleAuditList(ctx);
                    }
                    case "show" -> {
                        ctx.dropArg();
                        handleAuditShow(ctx);
                    }
                    case "context" -> {
                        ctx.dropArg();
                        handleAuditContext(ctx);
                    }
                    default -> handleAuditList(ctx);
                }
            })
            .register(plugin);
    }

    private void handleAuditList(dev.stemcraft.api.command.CommandContext ctx) {
        AuditIdentity playerFilter = resolveAuditIdentity(ctx.getOption("player"));
        String categoryOption = normalizeAuditText(ctx.getOption("type", "all"));
        Set<String> categories = parseAuditCategories(categoryOption);
        Instant since = parseTimeFilter(ctx.getOption("since"), Instant.now().minus(1, ChronoUnit.DAYS));
        Instant until = parseTimeFilter(ctx.getOption("until"), null);
        String contains = trimToNull(ctx.getOption("contains"));
        int page = parsePositiveInt(ctx.getOption("page"), 1);
        int limit = clamp(parsePositiveInt(ctx.getOption("limit"), queryDefaultLimit), 1, queryMaxLimit);

        List<AuditEventRecord> events = queryAuditEvents(
            playerFilter,
            categories,
            since,
            until,
            contains,
            limit,
            (page - 1) * limit,
            false
        );

        if (events.isEmpty()) {
            ctx.returnInfo("No audit events matched the requested filters.");
            return;
        }

        ctx.info("Audit results:");
        for (AuditEventRecord event : events) {
            ctx.info(formatAuditSummary(event));
        }
    }

    private void handleAuditShow(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /audit show <id>");
            return;
        }

        long id = parseLongOrFail(ctx, ctx.getArg(0), "Usage: /audit show <id>");
        AuditEventRecord event = getAuditEvent(id);
        if (event == null) {
            ctx.returnError("Audit event #" + id + " was not found.");
            return;
        }

        ctx.info("Audit #" + event.id() + " " + formatInstant(event.occurredAt()) + " " + event.category() + "/" + event.subtype());
        ctx.info("Actor: " + event.actorName() + formatUuidSuffix(event.actorUuid()));
        if (event.world() != null) {
            ctx.info("Location: " + event.world() + " " + formatCoordinates(event));
        }
        if (event.content() != null && !event.content().isBlank()) {
            ctx.info("Content: " + event.content());
        }
        if (event.detailsJson() != null && !event.detailsJson().isBlank()) {
            ctx.info("Details: " + event.detailsJson());
        }
    }

    private void handleAuditContext(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /audit context <id> [window]");
            return;
        }

        long id = parseLongOrFail(ctx, ctx.getArg(0), "Usage: /audit context <id> [window]");
        AuditEventRecord center = getAuditEvent(id);
        if (center == null) {
            ctx.returnError("Audit event #" + id + " was not found.");
            return;
        }

        long windowSeconds = parseWindowSeconds(ctx.getArg(1, "5m"), 300L);
        Instant since = center.occurredAt().minusSeconds(windowSeconds);
        Instant until = center.occurredAt().plusSeconds(windowSeconds);

        List<AuditEventRecord> events = queryAuditEvents(
            null,
            new LinkedHashSet<>(COMMUNICATION_CATEGORIES),
            since,
            until,
            null,
            queryMaxLimit,
            0,
            true
        );

        if (events.isEmpty()) {
            ctx.returnInfo("No audit context entries were found.");
            return;
        }

        ctx.info("Audit context around #" + center.id() + " (" + formatInstant(center.occurredAt()) + "):");
        for (AuditEventRecord event : events) {
            String prefix = event.id() == center.id() ? "* " : "  ";
            ctx.info(prefix + formatAuditSummary(event));
        }
    }

    private @Nullable AuditEventRecord getAuditEvent(long id) {
        return api.database().querySingleMapped(
            "SELECT id, occurred_at, category, subtype, actor_uuid, actor_name, content, world, x, y, z, details_json " +
                "FROM audit_events WHERE id = ?",
            ps -> ps.setLong(1, id),
            rs -> mapAuditEvent(rs)
        );
    }

    private List<AuditEventRecord> queryAuditEvents(@Nullable AuditIdentity identity,
                                                    @Nullable Set<String> categories,
                                                    @Nullable Instant since,
                                                    @Nullable Instant until,
                                                    @Nullable String contains,
                                                    int limit,
                                                    int offset,
                                                    boolean ascending) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, occurred_at, category, subtype, actor_uuid, actor_name, content, world, x, y, z, details_json " +
                "FROM audit_events WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (identity != null) {
            sql.append(" AND (");
            boolean appended = false;
            if (identity.uuid() != null) {
                sql.append("actor_uuid = ?");
                params.add(identity.uuid().toString());
                appended = true;
            }
            if (identity.name() != null) {
                if (appended) {
                    sql.append(" OR ");
                }
                sql.append("LOWER(actor_name) = ?");
                params.add(identity.name().toLowerCase(Locale.ROOT));
            }
            sql.append(')');
        }

        if (categories != null && !categories.isEmpty()) {
            sql.append(" AND category IN (");
            boolean first = true;
            for (String category : categories) {
                if (!first) {
                    sql.append(", ");
                }
                sql.append('?');
                params.add(category);
                first = false;
            }
            sql.append(')');
        }

        if (since != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(since.toEpochMilli());
        }
        if (until != null) {
            sql.append(" AND occurred_at <= ?");
            params.add(until.toEpochMilli());
        }
        if (contains != null) {
            sql.append(" AND LOWER(COALESCE(content, '')) LIKE ?");
            params.add("%" + contains.toLowerCase(Locale.ROOT) + "%");
        }

        sql.append(" ORDER BY occurred_at ").append(ascending ? "ASC" : "DESC").append(", id ").append(ascending ? "ASC" : "DESC");
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<AuditEventRecord> results = new ArrayList<>();
        api.database().queryEach(sql.toString(), ps -> bindParameters(ps, params), rs -> results.add(mapAuditEvent(rs)));
        return results;
    }

    List<AuditEventRecord> findCommunicationContext(@NotNull Instant center, long windowSeconds, @Nullable UUID playerUuid) {
        AuditIdentity identity = playerUuid == null ? null : new AuditIdentity(playerUuid, null);
        return queryAuditEvents(
            identity,
            new LinkedHashSet<>(COMMUNICATION_CATEGORIES),
            center.minusSeconds(windowSeconds),
            center.plusSeconds(windowSeconds),
            null,
            queryMaxLimit,
            0,
            true
        );
    }

    private AuditEventRecord mapAuditEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        Double x = rs.getDouble("x");
        if (rs.wasNull()) {
            x = null;
        }
        Double y = rs.getDouble("y");
        if (rs.wasNull()) {
            y = null;
        }
        Double z = rs.getDouble("z");
        if (rs.wasNull()) {
            z = null;
        }

        return new AuditEventRecord(
            rs.getLong("id"),
            Instant.ofEpochMilli(rs.getLong("occurred_at")),
            rs.getString("category"),
            rs.getString("subtype"),
            parseUuid(rs.getString("actor_uuid")),
            rs.getString("actor_name"),
            rs.getString("content"),
            rs.getString("world"),
            x,
            y,
            z,
            rs.getString("details_json")
        );
    }

    private void bindParameters(java.sql.PreparedStatement ps, List<Object> params) throws java.sql.SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            int index = i + 1;
            if (value == null) {
                ps.setObject(index, null);
            } else if (value instanceof String stringValue) {
                ps.setString(index, stringValue);
            } else if (value instanceof Long longValue) {
                ps.setLong(index, longValue);
            } else if (value instanceof Integer intValue) {
                ps.setInt(index, intValue);
            } else {
                ps.setObject(index, value);
            }
        }
    }

    private @Nullable AuditIdentity resolveAuditIdentity(@Nullable String rawPlayer) {
        String name = trimToNull(rawPlayer);
        if (name == null) {
            return null;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        UUID uuid = offlinePlayer != null ? offlinePlayer.getUniqueId() : null;
        return new AuditIdentity(uuid, name);
    }

    private Set<String> parseAuditCategories(@Nullable String raw) {
        String category = normalizeAuditText(raw);
        if (category == null || category.isBlank() || "all".equals(category)) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(List.of(category));
    }

    private @Nullable Instant parseTimeFilter(@Nullable String raw, @Nullable Instant defaultValue) {
        String value = trimToNull(raw);
        if (value == null) {
            return defaultValue;
        }

        try {
            long seconds = dev.stemcraft.api.util.TimeUtil.parseDuration(value);
            return Instant.now().minusSeconds(seconds);
        } catch (IllegalArgumentException ignored) {
            // fall through
        }

        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            // fall through
        }

        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through
        }

        try {
            String normalized = value.contains(" ") ? value.replace(' ', 'T') : value;
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through
        }

        try {
            return LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
            return defaultValue;
        }
    }

    private long parseWindowSeconds(@Nullable String raw, long defaultValue) {
        String value = trimToNull(raw);
        if (value == null) {
            return defaultValue;
        }

        try {
            long seconds = dev.stemcraft.api.util.TimeUtil.parseDuration(value);
            return seconds > 0L ? seconds : defaultValue;
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }

    private long parseLongOrFail(dev.stemcraft.api.command.CommandContext ctx, @Nullable String raw, String usage) {
        if (raw == null) {
            ctx.returnError(usage);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            ctx.returnError(usage);
            return -1L;
        }
    }

    private int parsePositiveInt(@Nullable String raw, int defaultValue) {
        String value = trimToNull(raw);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private @Nullable UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private @Nullable String normalizeAuditText(@Nullable String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String formatAuditSummary(AuditEventRecord event) {
        String content = event.content() == null ? "" : " " + clip(event.content().replace('\n', ' '), 96);
        return "#" + event.id() + " " + formatInstant(event.occurredAt()) + " " + event.category() + "/" + event.subtype() +
            " " + event.actorName() + content;
    }

    private String formatInstant(Instant instant) {
        return formatter.format(instant);
    }

    private String formatCoordinates(AuditEventRecord event) {
        if (event.x() == null || event.y() == null || event.z() == null) {
            return "(unknown)";
        }
        return Math.round(event.x()) + ", " + Math.round(event.y()) + ", " + Math.round(event.z());
    }

    private String formatUuidSuffix(@Nullable UUID uuid) {
        return uuid == null ? "" : " [" + uuid + "]";
    }

    private String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private record AuditIdentity(@Nullable UUID uuid, @Nullable String name) {}

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

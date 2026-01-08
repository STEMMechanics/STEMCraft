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

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.punishment.PunishmentAlertCallback;
import dev.stemcraft.api.service.punishment.PunishmentRecord;
import dev.stemcraft.api.service.punishment.PunishmentService;
import dev.stemcraft.api.service.web.WebService;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TimeUtil;
import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of the PunishmentService for managing player punishments.
 */
public class PunishmentServiceImpl extends BaseService implements PunishmentService {

    private ConfigSection config;

    private final List<PunishmentRecord> punishments = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<String, PunishmentAlertCallback> alerts = new HashMap<>();

    private static final UUID SERVER_UUID = new UUID(0L, 0L);

    /**
     * Constructor for PunishmentServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api    The STEMCraft API instance.
     */
    public PunishmentServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Called when the service is being enabled.
     */
    @Override
    public void onEnable() {
        config = api.config().load("punishments.yml");

        loadFromConfig();
        registerWebEndpoint();

        registerAlert("warn", (type, player, record) -> {
            api.messages().info(player, "WARN_PLAYER", "reason", record.reason(), "actor", record.actorName());
            return true;
        });

        // Register a persistent type for temporary punishments
        api.tasks().registerPersistentCallback("punishment", (type, id, data) -> {
            PunishmentRecord record = getPunishmentById(id);
            if(record == null) {
                return;
            }

            Player player = Bukkit.getPlayer(record.targetUuid());
            if(record.type().equalsIgnoreCase("ban")) {
                api.messages().broadcast("BAN_EXPIRED_BROADCAST", player, "player", record.targetName());
            } else if(record.type().equalsIgnoreCase("mute")) {
                api.messages().broadcast("MUTE_EXPIRED_BROADCAST", "player", record.targetName());
            }
        });

        api.events().register(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();

            // wait 3 seconds (60 ticks) for client/world to finish loading
            api.tasks().runLater(60, () -> punishments.forEach(record -> {
                if(!record.alerted() && player.getUniqueId().equals(record.targetUuid())) {
                    alert(record);
                }
            }));
        });

        // /kick <player> [duration] [reason...]
        api.commands().create("kick")
                .description("PUNISHMENT_DESCRIPTION")
                .usage("PUNISHMENT_USAGE")
                .permission("stemcraft.command.kick")
                .tabCompletion("{player}")
                .executor((ignored, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    Player target = ctx.getArgAsPlayer(1);
                    if (target == null) {
                        cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                        return;
                    }

                    String reason = ctx.getArgsAsString(2, "Kicked by " + ctx.getSenderName());
                    Player actor = ctx.getSenderAsPlayer();
                    this.record(target.getUniqueId(), actor, null, "kick", true, reason);

                    Player targetPlayer = target.getPlayer();
                    String targetName = target.getName();
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        targetPlayer.kick(Component.text("You have been kicked: " + reason));
                    }

                    cmd.broadcast("COMMAND_KICK_BROADCAST", "player", targetName, "reason", reason);
                })
                .register(STEMCraft.getPlugin());

        // /warn <player> [duration] [reason...]
        api.commands().create("warn")
                .permission("stemcraft.command.warn")
                .tabCompletion("{player}", "{reason}")
                .executor((ignored, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    OfflinePlayer target = ctx.getArgAsOfflinePlayer(1);
                    if (target == null) {
                        cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                        return;
                    }

                    String reason = ctx.getArgsAsString(2, "Warned by " + ctx.getSenderName());
                    Player actor = ctx.getSenderAsPlayer();
                    this.record(target.getUniqueId(), actor, null, "warn", false, reason);

                    String targetName = target.getName();
                    cmd.broadcast("COMMAND_WARNING_BROADCAST", "player", targetName, "reason", reason);
                })
                .register(STEMCraft.getPlugin());

        // /ban <player> [duration] [reason...]
        api.commands().create("ban")
                .permission("stemcraft.command.ban")
                .tabCompletion("{player}", "unban", "{reason}")
                .tabCompletion("{player}", "{duration}", "{reason}")
                .tabCompletion("{player}", "perm", "{reason}")
                .tabCompletion("{player}", "{reason}")
                .executor((ignored, cmd, ctx) -> {
                    boolean pardon = false;

                    if (ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    OfflinePlayer target = ctx.getArgAsOfflinePlayer(1);
                    if(target == null) {
                        cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                        return;
                    }

                    int reasonIndex = 3;

                    Duration duration = ctx.getArgAsDuration(2);
                    if(duration == null) {
                        if(!ctx.getArg(2).equalsIgnoreCase("perm") && !ctx.getArg(2).equalsIgnoreCase("unban")) {
                            reasonIndex = 2;
                        }

                        if(ctx.getArg(2).equalsIgnoreCase("unban")) {
                            duration = Duration.ofSeconds(-1);
                            pardon = true;
                        }
                    }

                    String reason = ctx.getArgsAsString(reasonIndex, pardon ? "Cancelled" : "Banned");
                    Player actor = ctx.getSenderAsPlayer();

                    UUID targetUuid = target.getUniqueId();
                    this.record(targetUuid, actor, duration, "ban", true, reason);

                    Date expires = null;
                    if (duration != null) {
                        expires = Date.from(Instant.now().plus(duration));
                    }

                    PlayerProfile profile = Bukkit.createProfile(
                            target.getUniqueId(),
                            target.getName()
                    );

                    if(pardon) {
                        BanList<PlayerProfile> banList = Bukkit.getBanList(BanListType.PROFILE);

                        if (!banList.isBanned(profile)) {
                            cmd.error(ctx.getSender(), "PLAYER_NOT_BANNED", "player", target.getName());
                            return;
                        }

                        banList.pardon(profile);
                        api.messages().broadcast("BAN_EXPIRED_BROADCAST", target.getPlayer(), "reason", reason, "actor", ctx.getSenderName());
                    } else {
                        Bukkit.getBanList(BanListType.PROFILE)
                                .addBan(profile, reason, expires, actor != null ? actor.getName() : "<server>");

                        if (target.isOnline()) {
                            Player online = target.getPlayer();
                            if (online != null) {
                                online.kick(Component.text("You have been banned: " + reason));
                            }
                        }

                        String durationString = duration == null ? "permanently" : "for " + TimeUtil.formatDuration(duration.toSeconds());
                        api.messages().broadcast("BAN_PLAYER_BROADCAST", target.getPlayer(), "reason", reason, "actor", ctx.getSenderName(), "duration", durationString);
                    }
                })
                .register(STEMCraft.getPlugin());
    }

    /**
     * Register an alert callback for a specific punishment type.
     *
     * @param type     The punishment type to register the alert for.
     * @param callback The callback to invoke when a punishment of the specified type is recorded.
     */
    @Override
    public void registerAlert(String type, PunishmentAlertCallback callback) {
        alerts.put(type, callback);

        punishments.forEach(record -> {
            if(!record.alerted() && record.type().equalsIgnoreCase(type)) {
                alert(record);
            }
        });
    }

    /**
     * Record a new punishment for a player.
     *
     * @param playerUuid The UUID of the player being punished.
     * @param actor      The player performing the punishment (null for server).
     * @param duration   The duration of the punishment (null for permanent).
     * @param type       The type of punishment (e.g., "ban", "mute", "warn").
     * @param alerted    Whether the player has been alerted about the punishment.
     * @param reason     The reason for the punishment.
     */
    @Override
    public synchronized void record(UUID playerUuid, Player actor, Duration duration, String type, boolean alerted, String reason) {
        String playerName = PlayerUtil.name(playerUuid);
        if(playerName == null) {
            // ERROR PLAYER NOT FOUND!
            return;
        }

        long id = nextId.getAndIncrement();
        Instant now = Instant.now();
        Long durationSeconds = duration != null ? duration.getSeconds() : null;

        PunishmentRecord record = new PunishmentRecord(
                id,
                playerUuid,
                playerName,
                actor != null ? actor.getUniqueId() : SERVER_UUID,
                actor != null ? actor.getName() : "<server>",
                type,
                alerted,
                reason != null ? reason : "",
                now,
                durationSeconds
        );

        punishments.add(record);
        writeRecordToConfig(record);
        config.save();

        if(!alerted) {
            alert(record);
        }
    }

    /**
     * List punishment records with optional filtering and pagination.
     *
     * @param player   Optional player UUID to filter by (null for no filter).
     * @param type     Optional punishment type to filter by (null for no filter).
     * @param page     Page number (1-based).
     * @param pageSize Number of records per page.
     * @return List of punishment records matching the criteria.
     */
    @Override
    public synchronized List<PunishmentRecord> list(UUID player, String type, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize <= 0) pageSize = 25;

        Stream<PunishmentRecord> stream = getPunishmentRecordStream(player, type);

        // Sort newest → oldest
        List<PunishmentRecord> ordered = stream
                .sorted(Comparator.comparing(PunishmentRecord::createdAt).reversed())
                .toList();

        // Pagination
        int from = (page - 1) * pageSize;
        if (from >= ordered.size()) {
            return Collections.emptyList();
        }

        int to = Math.min(from + pageSize, ordered.size());
        return ordered.subList(from, to);
    }

    private @NonNull Stream<PunishmentRecord> getPunishmentRecordStream(UUID player, String type) {
        Stream<PunishmentRecord> stream = punishments.stream();

        // Optional player filter
        if (player != null) {
            stream = stream.filter(p -> player.equals(p.targetUuid()));
        }

        // Optional type filter (string)
        if (type != null && !type.isBlank()) {
            String match = type.trim().toLowerCase(Locale.ROOT);
            stream = stream.filter(p ->
                    p.type() != null &&
                            p.type().toLowerCase(Locale.ROOT).equals(match)
            );
        }
        return stream;
    }

    /**
     * Load punishments from the configuration file.
     */
    private void loadFromConfig() {
        punishments.clear();

        ConfigSection section = config.getSection("punishments");
        if (section == null) {
            return;
        }

        long maxId = 0;

        for (String key : section.getKeys(false)) {
            try {
                long id = Long.parseLong(key);
                ConfigSection pSec = section.getSection(key);
                if (pSec == null) continue;

                String targetUuidStr = pSec.getString("target.uuid");
                UUID targetUuid = targetUuidStr != null && !targetUuidStr.isEmpty()
                        ? UUID.fromString(targetUuidStr) : null;
                String targetName = pSec.getString("target.name");

                String actorUuidStr = pSec.getString("actor.uuid");
                UUID actorUuid = actorUuidStr != null && !actorUuidStr.isEmpty()
                        ? UUID.fromString(actorUuidStr) : null;
                String actorName = pSec.getString("actor.name");

                String typeStr = pSec.getString("type");
                String type = typeStr != null
                        ? typeStr
                        : "warning";

                boolean alerted = pSec.getBoolean("alerted", false);
                String reason = pSec.getString("reason", "");

                long createdAtMillis = pSec.getLong("createdAt");
                Instant createdAt = Instant.ofEpochMilli(createdAtMillis);

                Long durationSeconds = null;
                if (pSec.contains("durationSeconds")) {
                    durationSeconds = pSec.getLong("durationSeconds");
                }

                PunishmentRecord record = new PunishmentRecord(
                        id,
                        targetUuid,
                        targetName,
                        actorUuid,
                        actorName,
                        type,
                        alerted,
                        reason,
                        createdAt,
                        durationSeconds
                );

                punishments.add(record);
                if (id > maxId) {
                    maxId = id;
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("[PunishmentManager] Failed to load punishment " + key + ": " + ex.getMessage());
            }
        }

        nextId.set(maxId + 1);
    }

    /**
     * Write a punishment record to the configuration file.
     *
     * @param record The punishment record to write.
     */
    private void writeRecordToConfig(PunishmentRecord record) {
        String path = "punishments." + record.id();

        config.set(path + ".target.uuid",
                record.targetUuid() != null ? record.targetUuid().toString() : null);
        config.set(path + ".target.name", record.targetName());
        config.set(path + ".actor.uuid",
                record.actorUuid() != null ? record.actorUuid().toString() : null);
        config.set(path + ".actor.name", record.actorName());
        config.set(path + ".type", record.type());
        config.set(path + ".alerted", record.alerted());
        config.set(path + ".reason", record.reason());
        config.set(path + ".createdAt", record.createdAt().toEpochMilli());
        if (!record.permanent()) {
            config.set(path + ".durationSeconds", record.durationSeconds());
        } else {
            config.set(path + ".durationSeconds", null);
        }
    }

    /**
     * Register a web endpoint for viewing punishments.
     */
    private void registerWebEndpoint() {
        api.web().registerEndpointHandler("/punish", (method, uri, queryParams) -> {
            // Parse URI and query string
            java.net.URI parsed = java.net.URI.create(uri);
            String path = parsed.getPath();           // /punish, /punish/type/ban, /punish/player/nomadjimbob

            int page = 1;
            int pageSize = 20;

            String pageParam = queryParams.get("page");
            if (pageParam != null) {
                try {
                    page = Integer.parseInt(pageParam);
                    if (page < 1) {
                        page = 1;
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // Snapshot and sort punishments newest first
            java.util.List<PunishmentRecord> all;
            synchronized (this) {
                all = new java.util.ArrayList<>(punishments);
            }
            all.sort(java.util.Comparator.comparing(PunishmentRecord::createdAt).reversed());

            // Collect distinct punishment types for toolbar
            java.util.Set<String> typeSet = all.stream()
                    .map(PunishmentRecord::type)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

            // Routing: /punish, /punish/type/<type>, /punish/player[/<name-or-uuid>] or /punish/player?q=...
            String[] parts = path.split("/"); // ["", "punish", ...]
            java.util.List<PunishmentRecord> filtered = all;
            String title;
            String basePath;
            boolean filteredMode = false;

            if (parts.length == 2) {
                // /punish -> all punishments
                title = "All punishments";
                basePath = "/punish";
            } else if (parts.length >= 3 && "type".equalsIgnoreCase(parts[2])) {
                if (parts.length < 4 || parts[3].isEmpty()) {
                    return java.util.Map.of(
                            "code", 404,
                            "body", "<html><body><h1>404 Not Found</h1><p>Missing punishment type.</p></body></html>"
                    );
                }

                String typeParam = parts[3];
                String match = typeParam.trim().toLowerCase(Locale.ROOT);

                filtered = all.stream()
                        .filter(p -> p.type() != null && p.type().trim().toLowerCase(Locale.ROOT).equals(match))
                        .toList();

                title = "Punishments - type " + typeParam;
                basePath = "/punish/type/" + typeParam;
                filteredMode = true;
            } else if (parts.length >= 3 && "player".equalsIgnoreCase(parts[2])) {
                // /punish/player/<name-or-uuid> or /punish/player?q=...
                String playerSpec;
                if (parts.length >= 4 && !parts[3].isEmpty()) {
                    playerSpec = parts[3];
                } else {
                    playerSpec = queryParams.get("q");
                }

                if (playerSpec == null || playerSpec.isBlank()) {
                    return java.util.Map.of(
                            "responseCode", 404,
                            "body", "<html><body><h1>404 Not Found</h1><p>Missing player name or UUID.</p></body></html>"
                    );
                }

                String rawSpec = playerSpec.trim();

                // Try UUID first
                UUID playerUuid = null;
                try {
                    playerUuid = UUID.fromString(rawSpec);
                } catch (IllegalArgumentException ignored) {
                }

                if (playerUuid != null) {
                    UUID finalPlayerUuid = playerUuid;
                    filtered = all.stream()
                            .filter(p -> finalPlayerUuid.equals(p.targetUuid()))
                            .toList();
                } else {
                    String lowerName = rawSpec.toLowerCase(Locale.ROOT);
                    filtered = all.stream()
                            .filter(p -> p.targetName() != null && p.targetName().toLowerCase(Locale.ROOT).equals(lowerName))
                            .toList();
                }

                title = "Punishments - player " + WebService.escapeHtml(rawSpec);
                basePath = "/punish/player/" + URLEncoder.encode(rawSpec, StandardCharsets.UTF_8);
                filteredMode = true;
            } else {
                return java.util.Map.of(
                        "responseCode", 404,
                        "body", "<html><body><h1>404 Not Found</h1><p>Unknown punish route: "
                                + WebService.escapeHtml(path) + "</p></body></html>"
                );
            }

            int total = filtered.size();
            int totalPages = (int) Math.ceil(total / (double) pageSize);
            if (totalPages == 0) {
                totalPages = 1;
            }
            if (page > totalPages) {
                page = totalPages;
            }

            int from = (page - 1) * pageSize;
            int to = Math.min(from + pageSize, total);
            java.util.List<PunishmentRecord> pageData =
                    total == 0 || from >= total ? java.util.List.of() : filtered.subList(from, to);

            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><title>")
                    .append(WebService.escapeHtml(title))
                    .append("</title><style>")
                    .append("body{font-family:Arial,Helvetica,sans-serif;font-size:13px;margin:10px;}")
                    .append("table{border-collapse:collapse;width:100%;}")
                    .append("th,td{border:1px solid #ccc;padding:4px;text-align:left;}")
                    .append("th{background:#eee;}")
                    .append("small{color:#777;}")
                    .append("a{margin:0 4px;text-decoration:none;}")
                    .append(".toolbar{margin-bottom:10px;padding:6px 8px;background:#f5f5f5;border:1px solid #ddd;}")
                    .append(".toolbar form{display:inline-block;margin-right:16px;}")
                    .append("</style></head><body>");

            // toolbar with player search, type list, and clear link when filtered
            sb.append("<div class=\"toolbar\">");
            sb.append("<form method=\"GET\" action=\"/punish/player\" accept-charset=\"UTF-8\">");
            sb.append("Player: <input type=\"text\" name=\"q\" size=\"16\" /> ");
            sb.append("<button type=\"submit\">Search</button>");
            sb.append("</form>");

            sb.append("<span>Types: <a href=\"/punish\">All</a>");
            for (String t : typeSet) {
                String link = "/punish/type/" + URLEncoder.encode(t, StandardCharsets.UTF_8);
                sb.append(" | <a href=\"").append(link).append("\">")
                        .append(WebService.escapeHtml(t))
                        .append("</a>");
            }
            sb.append("</span>");

            if (filteredMode) {
                sb.append("<span style=\"margin-left:16px;\"><a href=\"/punish\">Clear</a></span>");
            }

            sb.append("</div>");

            sb.append("<h1>").append(WebService.escapeHtml(title)).append("</h1>");
            sb.append("<p>Total ").append(total).append(", page ").append(page)
                    .append(" of ").append(totalPages).append("</p>");

            sb.append("<table>");
            sb.append("<tr>")
                    .append("<th>ID</th>")
                    .append("<th>Date</th>")
                    .append("<th>Target</th>")
                    .append("<th>Actor</th>")
                    .append("<th>Type</th>")
                    .append("<th>Duration</th>")
                    .append("<th>Reason</th>")
                    .append("</tr>");

            for (PunishmentRecord p : pageData) {
                sb.append("<tr>");

                sb.append("<td>").append(p.id()).append("</td>");

                sb.append("<td>").append(TimeUtil.toFriendlyTime(p.createdAt())).append("</td>");

                String playerLabel = p.targetName() != null ? p.targetName() : "Unknown";
                String playerSpec = p.targetUuid() != null ? p.targetUuid().toString() : playerLabel;
                String playerHref = "/punish/player/" + URLEncoder.encode(playerSpec, StandardCharsets.UTF_8);

                sb.append("<td>")
                        .append("<a href=\"")
                        .append(playerHref)
                        .append("\">")
                        .append(WebService.escapeHtml(playerLabel))
                        .append("</a>");

                if (p.targetUuid() != null) {
                    sb.append(" <small>(").append(p.targetUuid()).append(")</small>");
                }

                sb.append("</td>");

                if(p.actorUuid().equals(SERVER_UUID)) {
                    sb.append("<td>")
                            .append(WebService.escapeHtml("SERVER"))
                            .append("</td>");
                } else {
                    sb.append("<td>")
                            .append(WebService.escapeHtml(p.actorName())).append(" <small>(").append(p.actorUuid()).append(")</small>")
                            .append("</td>");
                }

                sb.append("<td>").append(p.type()).append("</td>");

                if (p.permanent()) {
                    sb.append("<td>PERMANENT</td>");
                } else if(p.cancelled()) {
                    sb.append("<td>CANCELLED</td>");
                } else {
                    sb.append("<td>").append(TimeUtil.formatDuration(p.durationSeconds())).append("</td>");
                }

                sb.append("<td>").append(WebService.escapeHtml(p.reason())).append("</td>");

                sb.append("</tr>");
            }
            sb.append("</table>");

            // pager
            sb.append("<p>");
            if (page > 1) {
                sb.append("<a href=\"").append(basePath).append("?page=").append(page - 1)
                        .append("\">&laquo; Prev</a>");
            }
            if (page < totalPages) {
                sb.append("<a href=\"").append(basePath).append("?page=").append(page + 1)
                        .append("\">Next &raquo;</a>");
            }
            sb.append("</p>");

            sb.append("</body></html>");

            // Return string so WebManager treats it as HTTP 200
            return sb.toString();
        });
    }

    /**
     * Get a punishment record by its ID.
     *
     * @param id The ID of the punishment record.
     * @return The PunishmentRecord if found, null otherwise.
     */
    private PunishmentRecord getPunishmentById(long id) {
        for (PunishmentRecord record : punishments) {
            if (record.id() == id) {
                return record;
            }
        }
        return null;
    }

    /**
     * Get a punishment record by its ID (string version).
     *
     * @param id The ID of the punishment record as a string.
     * @return The PunishmentRecord if found, null otherwise.
     */
    private PunishmentRecord getPunishmentById(String id) {
        try {
            long parsed = Long.parseLong(id);
            return getPunishmentById(parsed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Alert the player about their punishment if they are online.
     *
     * @param record The punishment record to alert about.
     */
    private void alert(PunishmentRecord record) {
        Player player = record.getPlayerIfOnline();
        if(player != null) {
            boolean result = alerts.get(record.type()).run(record.type(), player, record);
            if(result) {
                record.setAlerted();
                writeRecordToConfig(record);
                config.save();
            }
        }
    }
}
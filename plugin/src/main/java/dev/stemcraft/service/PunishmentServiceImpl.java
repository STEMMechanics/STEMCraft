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
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.service.profanity.ProfanityFilterResult;
import dev.stemcraft.api.service.profanity.ProfanitySeverity;
import dev.stemcraft.api.service.punishment.PunishmentAlertCallback;
import dev.stemcraft.api.service.punishment.PunishmentRecord;
import dev.stemcraft.api.service.punishment.PunishmentService;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TimeUtil;
import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jspecify.annotations.NonNull;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.io.File;

/**
 * Implementation of the PunishmentService for managing player punishments.
 */
public class PunishmentServiceImpl extends BaseService implements PunishmentService {
    private final List<PunishmentRecord> punishments = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<String, PunishmentAlertCallback> alerts = new HashMap<>();
    private final List<Consumer<PunishmentRecord>> observers = new CopyOnWriteArrayList<>();

    private static final UUID SERVER_UUID = new UUID(0L, 0L);
    private boolean profanityFilterEnabled;
    private String profanityFilterAction;
    private ProfanitySeverity profanityFilterMinimumSeverity;
    private String profanityFilterBlockedMessage;

    /**
     * Constructor for PunishmentServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public PunishmentServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "punishments");
    }

    /**
     * Called when the service is being enabled.
     */
    @Override
    public void onEnable() {
        reloadSettings();
        ensureStorage();
        loadFromDatabase();

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

        api.events().register(AsyncPlayerPreLoginEvent.class, event -> {
            PunishmentRecord activeBan = findActiveBan(event.getUniqueId());
            if (activeBan != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, formatBanMessage(activeBan));
            }
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

                    Player target = ctx.getPlayer(0);
                    if (target == null) {
                        cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                        return;
                    }

                    String reason = moderatedReasonOrReturn(ctx.getArgsAsString(2, "Kicked by " + ctx.getSenderName()), cmd, ctx.getSender());
                    if (reason == null) {
                        return;
                    }
                    Player actor = ctx.asPlayer();
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

                    OfflinePlayer target = ctx.getArgAsOfflinePlayer(0);
                    if (target == null) {
                        cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                        return;
                    }

                    String reason = moderatedReasonOrReturn(ctx.getArgsAsString(2, "Warned by " + ctx.getSenderName()), cmd, ctx.getSender());
                    if (reason == null) {
                        return;
                    }
                    Player actor = ctx.asPlayer();
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

                    OfflinePlayer target = ctx.getArgAsOfflinePlayer(0);
                    if(target == null) {
                        cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                        return;
                    }

                    int reasonIndex = 3;
                    String durationArg = ctx.getArg(1, "");

                    Duration duration = ctx.getArgAsDuration(1);
                    if(duration == null) {
                        if(!durationArg.equalsIgnoreCase("perm") && !durationArg.equalsIgnoreCase("unban")) {
                            reasonIndex = 2;
                        }

                        if(durationArg.equalsIgnoreCase("unban")) {
                            duration = Duration.ofSeconds(-1);
                            pardon = true;
                        }
                    }

                    String reason = moderatedReasonOrReturn(ctx.getArgsAsString(reasonIndex, pardon ? "Cancelled" : "Banned"), cmd, ctx.getSender());
                    if (reason == null) {
                        return;
                    }
                    Player actor = ctx.asPlayer();

                    UUID targetUuid = target.getUniqueId();
                    PlayerProfile profile = Bukkit.createProfile(
                            target.getUniqueId(),
                            target.getName()
                    );

                    if(pardon) {
                        pardonBan(cmd, ctx.getSender(), target, actor, reason, ctx.getSenderName());
                    } else {
                        this.record(targetUuid, actor, duration, "ban", true, reason);
                        Date expires = null;
                        if (duration != null) {
                            expires = Date.from(Instant.now().plus(duration));
                        }
                        Bukkit.getBanList(BanListType.PROFILE)
                                .addBan(profile, reason, expires, actor != null ? actor.getName() : "<server>");

                        if (target.isOnline()) {
                            Player online = target.getPlayer();
                            if (online != null) {
                                online.kick(formatBanMessage(findActiveBan(targetUuid)));
                            }
                        }

                        String durationString = duration == null ? "permanently" : "for " + TimeUtil.formatDuration(duration.toSeconds());
                        api.messages().broadcast("BAN_PLAYER_BROADCAST", target.getPlayer(), "player", Objects.requireNonNull(target.getName()), "reason", reason, "actor", ctx.getSenderName(), "duration", durationString);
                    }
                })
                .register(STEMCraft.getPlugin());

        api.commands().create("unban")
                .permission("stemcraft.command.unban")
                .usage("/unban <player> [reason]")
                .tabCompletion("{player}", "{reason}")
                .executor((ignored, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    OfflinePlayer target = ctx.getArgAsOfflinePlayer(0);
                    if (target == null) {
                        cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                        return;
                    }

                    String reason = moderatedReasonOrReturn(ctx.getArgsAsString(1, "Cancelled"), cmd, ctx.getSender());
                    if (reason == null) {
                        return;
                    }
                    pardonBan(cmd, ctx.getSender(), target, ctx.asPlayer(), reason, ctx.getSenderName());
                })
                .register(STEMCraft.getPlugin());
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
    }

    private void reloadSettings() {
        profanityFilterEnabled = getConfigSection().getBoolean("profanity_filter.enabled", true);
        profanityFilterAction = getConfigSection().getString("profanity_filter.action", "sanitize").trim().toLowerCase(Locale.ROOT);
        profanityFilterMinimumSeverity = ProfanitySeverity.fromString(
            getConfigSection().getString("profanity_filter.minimum_severity", "moderate"),
            ProfanitySeverity.MODERATE
        );
        profanityFilterBlockedMessage = getConfigSection().getString(
            "profanity_filter.blocked_message",
            "Punishment reasons must not include profane language."
        );
    }

    /**
     * Register an alert callback for a specific punishment type.
     *
     * @param type The punishment type to register the alert for.
     * @param callback The callback to invoke when a punishment of the specified type is recorded.
     */
    @Override
    public void registerAlert(@NotNull String type, @NotNull PunishmentAlertCallback callback) {
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
     * @param actor The player performing the punishment (null for server).
     * @param duration The duration of the punishment (null for permanent).
     * @param type The type of punishment (e.g., "ban", "mute", "warn").
     * @param alerted Whether the player has been alerted about the punishment.
     * @param reason The reason for the punishment.
     */
    @Override
    public synchronized void record(@NotNull UUID playerUuid, @Nullable Player actor, @Nullable Duration duration, @NotNull String type, boolean alerted, @NotNull String reason) {
        record(playerUuid, actor, duration, type, alerted, reason, true);
    }

    private synchronized void record(@NotNull UUID playerUuid, @Nullable Player actor, @Nullable Duration duration, @NotNull String type, boolean alerted, @NotNull String reason, boolean notifyObservers) {
        String effectiveReason = sanitizeReason(reason);
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
                effectiveReason,
                now,
                durationSeconds
        );

        punishments.add(record);
        writeRecordToDatabase(record);
        if (notifyObservers) {
            observers.forEach(observer -> observer.accept(record));
        }

        if(!alerted) {
            alert(record);
        }
    }

    void registerObserver(@NotNull Consumer<PunishmentRecord> observer) {
        observers.add(observer);
    }

    /**
     * List punishment records with optional filtering and pagination.
     *
     * @param player Optional player UUID to filter by (null for no filter).
     * @param type Optional punishment type to filter by (null for no filter).
     * @param page Page number (1-based).
     * @param pageSize Number of records per page.
     * @return List of punishment records matching the criteria.
     */
    @Override
    public synchronized @NotNull List<PunishmentRecord> list(@Nullable UUID player, @Nullable String type, int page, int pageSize) {
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
     * Load punishments from the database.
     */
    private void loadFromDatabase() {
        punishments.clear();
        long[] maxId = {0L};

        api.database().queryEach(
            "SELECT id, target_uuid, target_name, actor_uuid, actor_name, type, alerted, reason, created_at, duration_seconds " +
                "FROM punishments ORDER BY id ASC",
            null,
            rs -> {
                try {
                    long id = rs.getLong("id");
                    String targetUuidStr = rs.getString("target_uuid");
                    String actorUuidStr = rs.getString("actor_uuid");

                    UUID targetUuid = targetUuidStr == null || targetUuidStr.isBlank() ? null : UUID.fromString(targetUuidStr);
                    UUID actorUuid = actorUuidStr == null || actorUuidStr.isBlank() ? null : UUID.fromString(actorUuidStr);
                    long durationRaw = rs.getLong("duration_seconds");
                    Long durationSeconds = rs.wasNull() ? null : durationRaw;

                    PunishmentRecord record = new PunishmentRecord(
                        id,
                        targetUuid,
                        rs.getString("target_name"),
                        actorUuid,
                        rs.getString("actor_name"),
                        rs.getString("type"),
                        rs.getInt("alerted") == 1,
                        rs.getString("reason"),
                        Instant.ofEpochMilli(rs.getLong("created_at")),
                        durationSeconds
                    );

                    punishments.add(record);
                    if (id > maxId[0]) {
                        maxId[0] = id;
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("[PunishmentManager] Failed to load punishment row: " + ex.getMessage());
                }
            }
        );

        nextId.set(maxId[0] + 1);
    }

    /**
     * Write a punishment record to the database.
     *
     * @param record The punishment record to write.
     */
    private void writeRecordToDatabase(PunishmentRecord record) {
        api.database().update(
            "INSERT INTO punishments (id, target_uuid, target_name, actor_uuid, actor_name, type, alerted, reason, created_at, duration_seconds) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "target_uuid = excluded.target_uuid, " +
                "target_name = excluded.target_name, " +
                "actor_uuid = excluded.actor_uuid, " +
                "actor_name = excluded.actor_name, " +
                "type = excluded.type, " +
                "alerted = excluded.alerted, " +
                "reason = excluded.reason, " +
                "created_at = excluded.created_at, " +
                "duration_seconds = excluded.duration_seconds",
            ps -> {
                ps.setLong(1, record.id());
                ps.setString(2, record.targetUuid() != null ? record.targetUuid().toString() : null);
                ps.setString(3, record.targetName());
                ps.setString(4, record.actorUuid() != null ? record.actorUuid().toString() : null);
                ps.setString(5, record.actorName());
                ps.setString(6, record.type());
                ps.setInt(7, record.alerted() ? 1 : 0);
                ps.setString(8, record.reason());
                ps.setLong(9, record.createdAt().toEpochMilli());
                if (record.permanent()) {
                    ps.setNull(10, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(10, record.durationSeconds());
                }
            }
        );
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
        Player player = Bukkit.getPlayer(record.targetUuid());
        if(player != null) {
            boolean result = alerts.get(record.type()).run(record.type(), player, record);
            if(result) {
                record.setAlerted();
                writeRecordToDatabase(record);
            }
        }
    }

    private void ensureStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS punishments (" +
            "id INTEGER PRIMARY KEY," +
            "target_uuid TEXT," +
            "target_name TEXT," +
            "actor_uuid TEXT," +
            "actor_name TEXT," +
            "type TEXT NOT NULL," +
            "alerted INTEGER NOT NULL DEFAULT 0," +
            "reason TEXT," +
            "created_at INTEGER NOT NULL," +
            "duration_seconds INTEGER" +
            ");"
        );
        api.database().execute("CREATE INDEX IF NOT EXISTS punishments_target_uuid ON punishments(target_uuid);");
        api.database().execute("CREATE INDEX IF NOT EXISTS punishments_type ON punishments(type);");

        if (api.database().migrationVersion("punishments-state") >= 1) {
            return;
        }

        File legacyFile = new File(plugin.getDataFolder(), "punishments.yml");
        ConfigFile legacy = legacyFile.exists() ? api.config().load(legacyFile, false) : null;
        if (legacy != null && legacy.isSection("punishments")) {
            var section = legacy.getSection("punishments", false);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        long id = Long.parseLong(key);
                        var pSec = section.getSection(key, false);
                        if (pSec == null) continue;

                        String targetUuidStr = pSec.getString("target.uuid");
                        String actorUuidStr = pSec.getString("actor.uuid");
                        Long durationSeconds = pSec.contains("durationSeconds") ? pSec.getLong("durationSeconds") : null;

                        PunishmentRecord record = new PunishmentRecord(
                            id,
                                targetUuidStr.isBlank() ? null : UUID.fromString(targetUuidStr),
                            pSec.getString("target.name"),
                                actorUuidStr.isBlank() ? null : UUID.fromString(actorUuidStr),
                            pSec.getString("actor.name"),
                            pSec.getString("type"),
                            pSec.getBoolean("alerted", false),
                            pSec.getString("reason", ""),
                            Instant.ofEpochMilli(pSec.getLong("createdAt")),
                            durationSeconds
                        );
                        writeRecordToDatabase(record);
                    } catch (Exception ignored) {
                        // ignored
                    }
                }
            }
        }

        api.database().setMigrationVersion("punishments-state", 1);
    }

    private void pardonBan(dev.stemcraft.api.command.Command cmd, CommandSender sender, OfflinePlayer target, @Nullable Player actor, String reason, String actorName) {
        UUID targetUuid = target.getUniqueId();
        PlayerProfile profile = Bukkit.createProfile(targetUuid, target.getName());
        BanList<PlayerProfile> banList = Bukkit.getBanList(BanListType.PROFILE);

        if (findActiveBan(targetUuid) == null && !banList.isBanned(profile)) {
            cmd.error(sender, "PLAYER_NOT_BANNED", "player", target.getName());
            return;
        }

        this.record(targetUuid, actor, Duration.ofSeconds(-1), "ban", true, reason);
        banList.pardon(profile);
        api.messages().broadcast("BAN_EXPIRED_BROADCAST", target.getPlayer(), "player", Objects.requireNonNull(target.getName()), "reason", reason, "actor", actorName);
    }

    boolean pardonActiveBan(@NotNull UUID targetUuid, @Nullable Player actor, @NotNull String reason, @NotNull String actorName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        PlayerProfile profile = Bukkit.createProfile(targetUuid, target.getName());
        BanList<PlayerProfile> banList = Bukkit.getBanList(BanListType.PROFILE);

        if (findActiveBan(targetUuid) == null && !banList.isBanned(profile)) {
            return false;
        }

        this.record(targetUuid, actor, Duration.ofSeconds(-1), "ban", true, reason);
        banList.pardon(profile);
        api.messages().broadcast("BAN_EXPIRED_BROADCAST", target.getPlayer(), "player", Objects.requireNonNullElse(target.getName(), targetUuid.toString()), "reason", reason, "actor", actorName);
        return true;
    }

    private @Nullable String moderatedReasonOrReturn(@NotNull String reason, dev.stemcraft.api.command.Command cmd, CommandSender sender) {
        if (!profanityFilterEnabled || api.profanityFilter() == null || !api.profanityFilter().isEnabled()) {
            return reason;
        }

        ProfanityFilterResult result = api.profanityFilter().check(reason, profanityFilterMinimumSeverity);
        if (!result.offensive()) {
            return reason;
        }

        if ("reject".equals(profanityFilterAction)) {
            cmd.error(sender, profanityFilterBlockedMessage);
            return null;
        }

        return result.cleanedText();
    }

    private @NotNull String sanitizeReason(@NotNull String reason) {
        if (!profanityFilterEnabled || api.profanityFilter() == null || !api.profanityFilter().isEnabled()) {
            return reason;
        }

        ProfanityFilterResult result = api.profanityFilter().check(reason, profanityFilterMinimumSeverity);
        return result.offensive() ? result.cleanedText() : reason;
    }

    synchronized PunishmentRecord findActiveBan(UUID playerUuid) {
        PunishmentRecord latest = null;
        for (PunishmentRecord record : punishments) {
            if (!playerUuid.equals(record.targetUuid())) {
                continue;
            }
            if (!"ban".equalsIgnoreCase(record.type())) {
                continue;
            }
            if (latest == null || record.createdAt().isAfter(latest.createdAt())) {
                latest = record;
            }
        }

        if (latest == null || latest.cancelled()) {
            return null;
        }
        if (latest.permanent()) {
            return latest;
        }

        Instant expiresAt = latest.expiresAt();
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            return null;
        }

        return latest;
    }

    synchronized @Nullable PunishmentRecord findLatestPriorPunishment(@NotNull UUID playerUuid,
                                                                      @NotNull String type,
                                                                      @NotNull Instant before) {
        PunishmentRecord latest = null;
        for (PunishmentRecord record : punishments) {
            if (!playerUuid.equals(record.targetUuid())) {
                continue;
            }
            if (!type.equalsIgnoreCase(record.type())) {
                continue;
            }
            if (!record.createdAt().isBefore(before) || record.cancelled()) {
                continue;
            }
            if (latest == null || record.createdAt().isAfter(latest.createdAt())) {
                latest = record;
            }
        }
        return latest;
    }

    Component formatBanMessage(PunishmentRecord record) {
        if (record == null) {
            return Component.text("You have been banned.");
        }

        Component message = Component.text("You are banned from this server.");

        if (record.reason() != null && !record.reason().isBlank()) {
            message = message.append(Component.text("\nReason: " + record.reason()));
        }

        if (!record.permanent() && record.durationSeconds() != null && record.durationSeconds() > 0L) {
            Instant expiresAt = record.expiresAt();
            if (expiresAt != null) {
                long remainingSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
                if (remainingSeconds > 0L) {
                    message = message.append(
                            Component.text("\nRemaining: " + TimeUtil.formatDuration(remainingSeconds))
                    );
                }
            }
        }

        return message;
    }
}

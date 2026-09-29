package dev.stemcraft.service.firstjoin;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.playerreset.*;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.service.BaseService;
import dev.stemcraft.api.service.stembot.StemBotSession;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FirstJoinService extends BaseService {
    private static final String TABLE_NAME = "first_join_players";
    private final Map<UUID, FirstJoinSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID,StemBotSession> guides = new ConcurrentHashMap<>();
    private final Random random;
    private ScheduledTask timeoutTask;

    private boolean enabled;
    private int timeoutSeconds;
    private int maximumAttempts;
    private int minimumNumber;
    private int maximumNumber;
    private double movementTolerance;
    private String bypassPermission;
    private String stemBotAction;
    private NamespacedKey verifiedKey;

    public FirstJoinService(STEMCraft plugin, STEMCraftAPI api) {
        this(plugin, api, new Random());
    }

    FirstJoinService(STEMCraft plugin, STEMCraftAPI api, Random random) {
        super(plugin, api, "first-join");
        this.random = random;
    }

    @Override
    public void onEnable() {
        reloadSettings();
        ensureStorage();
        new FirstJoinListener(plugin, api, this).register();
        timeoutTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> expireTimedOutSessions(), 20L, 20L);
        api.playerResets().register(new PlayerResetHandler() {
            public @NotNull String id() { return "first-join-state"; }
            public @NotNull Set<PlayerResetScope> scopes() { return Set.of(PlayerResetScope.COMPLETE); }
            public int priority() { return 400; }
            public @NotNull PlayerResetPreview preview(@NotNull PlayerResetContext context) {
                return new PlayerResetPreview("First-join verification", isVerified(context.playerUuid()) ? 1 : 0);
            }
            public void reset(@NotNull PlayerResetContext context) { removeSession(context.playerUuid()); }
        });
    }

    @Override
    public void onDisable() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        for(UUID id:Set.copyOf(sessions.keySet())) removeSession(id);
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
        if(!enabled) for(UUID id:Set.copyOf(sessions.keySet())) removeSession(id);
    }

    private void reloadSettings() {
        enabled = getConfigSection().getBoolean("enabled", true);
        timeoutSeconds = getConfigSection().getInt("timeout-seconds", 60);
        maximumAttempts = getConfigSection().getInt("maximum-attempts", 3);
        minimumNumber = getConfigSection().getInt("minimum-number", 1);
        maximumNumber = getConfigSection().getInt("maximum-number", 20);
        movementTolerance = getConfigSection().getDouble("movement-tolerance", 0.5d);
        String verifiedKeyId = getConfigSection().getString("verified-key", "human_verified");
        bypassPermission = getConfigSection().getString("bypass-permission", "stemcraft.firstjoin.bypass");
        stemBotAction = getConfigSection().getString("stembot-action", "").trim();
        verifiedKey = new NamespacedKey(plugin, verifiedKeyId.toLowerCase());
    }

    private void ensureStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "player_uuid TEXT PRIMARY KEY," +
                "verified_at INTEGER NOT NULL," +
                "last_name TEXT NOT NULL" +
            ");"
        );
    }

    public boolean hasActiveSession(@NotNull UUID playerId) {
        return enabled && sessions.containsKey(playerId);
    }

    public boolean isVerified(@NotNull UUID playerId) {
        return api.database().querySingleMapped(
            "SELECT verified_at FROM " + TABLE_NAME + " WHERE player_uuid = ?",
            ps -> ps.setString(1, playerId.toString()),
            rs -> rs.getLong(1)
        ) != null;
    }

    public void handleJoin(@NotNull Player player) {
        if (!enabled) {
            return;
        }
        if (bypassesFirstJoinCheck(player)) {
            removeSession(player.getUniqueId());
            return;
        }

        if (hasPersistedFirstJoinStatus(player.getUniqueId())) {
            applyVerifiedMarker(player);
            removeSession(player.getUniqueId());
            return;
        }

        FirstJoinSession session = createSession(player);
        sessions.put(player.getUniqueId(), session);
        resumeWelcome(player);
    }

    /** Reattach the guide after a world transition, retaining the question and deadline. */
    public void resumeWelcome(@NotNull Player player) {
        FirstJoinSession session=sessions.get(player.getUniqueId());
        if(!hasActiveSession(player.getUniqueId())||session==null) return;
        StemBotSession previous=guides.remove(player.getUniqueId());
        if(previous!=null) previous.close();
        var bot=api.stemBot();
        if(bot!=null) bot.open(player).ifPresent(guide->{
            guides.put(player.getUniqueId(),guide);
            guide.follow(true);
            guide.listen(input -> processChatResponse(player,input));
        });
        sendFirstJoinPrompt(player,session);
    }

    public boolean guideHandlesChat(UUID playerId) {
        StemBotSession guide=guides.get(playerId);
        return guide!=null&&guide.listening();
    }

    public void processChatResponse(@NotNull Player player, @NotNull String input) {
        FirstJoinSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (session.isExpired(System.currentTimeMillis())) {
            timeoutPlayer(player);
            return;
        }

        FirstJoinEvaluationResult result = evaluateResponse(session, input, System.currentTimeMillis());
        switch (result.outcome()) {
            case success -> completeFirstJoinCheck(player);
            case incorrect -> {
                sendVerificationMessage(player, "FIRST_JOIN_INCORRECT", "question", session.prompt());
                sendVerificationMessage(player, "FIRST_JOIN_ATTEMPTS_LEFT", "attempts", session.attemptsRemaining());
            }
            case failure -> failPlayer(player);
            case expired -> timeoutPlayer(player);
        }
    }

    public void handleMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        FirstJoinSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        StemBotSession guide=guides.get(player.getUniqueId());
        if(guide!=null&&guide.active()) {
            // If the guide disappears, freeze at the latest location rather than the join point.
            session.updateInitialLocation(event.getTo());
            return;
        }

        Location to = event.getTo();

        Location initial = session.initialLocation();
        if (!Objects.equals(initial.getWorld(), to.getWorld())) {
            event.setTo(withDirection(initial, to));
            return;
        }

        double dx = to.getX() - initial.getX();
        double dy = to.getY() - initial.getY();
        double dz = to.getZ() - initial.getZ();
        double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
        if (distanceSquared <= (movementTolerance * movementTolerance)) {
            return;
        }

        event.setTo(withDirection(initial, to));
    }

    public void cancelIfActive(@NotNull Player player, @NotNull Cancellable event) {
        if (!hasActiveSession(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    public void handleCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        Player player=event.getPlayer();
        FirstJoinSession session=sessions.get(player.getUniqueId());
        if(!hasActiveSession(player.getUniqueId())||session==null) return;
        event.setCancelled(true);
        sendVerificationMessage(player,"FIRST_JOIN_COMMAND_BLOCKED");
        sendVerificationMessage(player,"FIRST_JOIN_REQUIRED","question",session.prompt());
    }

    private void sendVerificationMessage(Player player,String key,Object... placeholders) {
        StemBotSession guide=guides.get(player.getUniqueId());
        if(guide!=null&&guide.speak(api.messages().text(player,key,placeholders))) return;
        api.messages().info(player,key,placeholders);
    }

    public void handleTeleport(@NotNull Player player, @Nullable Location to) {
        if (to == null) {
            return;
        }

        FirstJoinSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        session.updateInitialLocation(to);
    }

    public @Nullable Player resolvePlayerDamager(@NotNull EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof org.bukkit.projectiles.ProjectileSource projectileSource) {
            if (projectileSource instanceof Player player) {
                return player;
            }
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    public void removeSession(@NotNull UUID playerId) {
        sessions.remove(playerId);
        StemBotSession guide=guides.remove(playerId);
        if(guide!=null) guide.close();
    }

    public void handleAdminStatus(@NotNull CommandContext ctx) {
        OfflinePlayer player = resolveOfflinePlayer(ctx);
        if (player == null) {
            ctx.returnError("Player was not found.");
            return;
        }

        boolean verified = hasPersistedFirstJoinStatus(player.getUniqueId());
        boolean active = hasActiveSession(player.getUniqueId());
        String bypass = bypassStatus(player);
        ctx.returnInfo("First-join status for " + displayName(player) + ": verified=" + verified + ", active-session=" + active + ", bypass=" + bypass);
    }

    public void handleAdminReset(@NotNull CommandContext ctx) {
        OfflinePlayer player = resolveOfflinePlayer(ctx);
        if (player == null) {
            ctx.returnError("Player was not found.");
            return;
        }

        resetFirstJoinStatus(player, ctx.getSenderName());
        ctx.returnSuccess("Reset first-join status for " + displayName(player) + ".");
    }

    private @Nullable OfflinePlayer resolveOfflinePlayer(@NotNull CommandContext ctx) {
        if (ctx.args().size() <= 2) {
            ctx.returnError("Usage: /stemcraft firstjoin <status|reset> <player>");
            return null;
        }
        OfflinePlayer player = ctx.getArgAsOfflinePlayer(2);
        if (player == null) {
            return null;
        }
        if (!player.isOnline() && !player.hasPlayedBefore()) {
            return null;
        }
        return player;
    }

    public void resetFirstJoinStatus(@NotNull OfflinePlayer player, @NotNull String actorName) {
        api.database().update("DELETE FROM " + TABLE_NAME + " WHERE player_uuid = ?", ps -> ps.setString(1, player.getUniqueId().toString()));
        if (player.getPlayer() != null) {
            player.getPlayer().getPersistentDataContainer().remove(verifiedKey);
        }
        removeSession(player.getUniqueId());
        plugin.getLogger().info("First-join status reset for " + displayName(player) + " [" + player.getUniqueId() + "] by " + actorName);
    }

    FirstJoinQuestion generateQuestion() {
        int min = Math.max(1, minimumNumber);
        int max = Math.max(min, maximumNumber);

        boolean useAddition = random.nextBoolean() || max == 1;
        if (useAddition) {
            int left = nextBetween(min, max);
            int right = nextBetween(min, max);
            return new FirstJoinQuestion(left + " + " + right + " = ?", left + right);
        }

        int left = nextBetween(Math.max(2, min), max);
        int rightUpper = Math.clamp(left - 1, min, max);
        int right = nextBetween(min, rightUpper);
        if (left <= right) {
            left = Math.min(max, right + 1);
        }
        return new FirstJoinQuestion(left + " - " + right + " = ?", left - right);
    }

    FirstJoinEvaluationResult evaluateResponse(@NotNull FirstJoinSession session, @NotNull String input, long nowMillis) {
        if (session.isExpired(nowMillis)) {
            return new FirstJoinEvaluationResult(FirstJoinOutcome.expired);
        }

        Integer parsed = parseInt(input);
        if (parsed != null && parsed == session.expectedAnswer()) {
            return new FirstJoinEvaluationResult(FirstJoinOutcome.success);
        }

        session.decrementAttempts();
        if (session.attemptsRemaining() <= 0) {
            return new FirstJoinEvaluationResult(FirstJoinOutcome.failure);
        }

        session.setQuestion(generateQuestion());
        return new FirstJoinEvaluationResult(FirstJoinOutcome.incorrect);
    }

    private void completeFirstJoinCheck(@NotNull Player player) {
        markVerified(player.getUniqueId(), player.getName(), player);
        sendVerificationMessage(player, "FIRST_JOIN_SUCCESS");
        sessions.remove(player.getUniqueId());
        StemBotSession guide=guides.remove(player.getUniqueId());
        var bot=api.stemBot();
        if(stemBotAction==null||stemBotAction.isBlank()) {
            if(guide!=null) guide.close();
        } else if(bot!=null&&bot.hasAction(stemBotAction)) {
            if(guide!=null) {
                if(!guide.startAction(stemBotAction)) guide.close();
            } else bot.startAction(player,stemBotAction);
        } else {
            if(guide!=null) guide.close();
            plugin.getLogger().warning("First-join STEMBot action is unavailable: " + stemBotAction);
        }
        plugin.getLogger().info("First-join check passed for " + player.getName() + " [" + player.getUniqueId() + "]");
    }

    private void failPlayer(@NotNull Player player) {
        removeSession(player.getUniqueId());
        plugin.getLogger().warning("First-join check failed for " + player.getName() + " [" + player.getUniqueId() + "]");
        player.kick(Component.text(api.messages().text(player, "FIRST_JOIN_FAILED")));
    }

    private void timeoutPlayer(@NotNull Player player) {
        removeSession(player.getUniqueId());
        plugin.getLogger().warning("First-join check timed out for " + player.getName() + " [" + player.getUniqueId() + "]");
        player.kick(Component.text(api.messages().text(player, "FIRST_JOIN_TIMEOUT")));
    }

    private void sendFirstJoinPrompt(@NotNull Player player, @NotNull FirstJoinSession session) {
        sendVerificationMessage(player, "FIRST_JOIN_WELCOME");
        sendVerificationMessage(player, "FIRST_JOIN_REQUIRED", "question", session.prompt());
        sendVerificationMessage(player, "FIRST_JOIN_PRIVATE");
    }

    private FirstJoinSession createSession(@NotNull Player player) {
        FirstJoinQuestion question = generateQuestion();
        return new FirstJoinSession(
            player.getUniqueId(),
            question.answer(),
            maximumAttempts,
            System.currentTimeMillis() + (Math.max(1, timeoutSeconds) * 1000L),
            player.getLocation(),
            question.prompt()
        );
    }

    private boolean bypassesFirstJoinCheck(@NotNull Player player) {
        return player.isOp()
            || (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission));
    }

    private @NotNull String bypassStatus(@NotNull OfflinePlayer player) {
        if (player.isOp()) {
            return "op";
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer != null && bypassPermission != null && !bypassPermission.isBlank() && onlinePlayer.hasPermission(bypassPermission)) {
            return bypassPermission;
        }

        return "none";
    }

    private boolean hasPersistedFirstJoinStatus(@NotNull UUID playerId) {
        return isVerified(playerId);
    }

    private void markVerified(@NotNull UUID playerId, @NotNull String playerName, @Nullable Player onlinePlayer) {
        api.database().update(
            "INSERT INTO " + TABLE_NAME + " (player_uuid, verified_at, last_name) VALUES (?, ?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET verified_at = excluded.verified_at, last_name = excluded.last_name",
            ps -> {
                ps.setString(1, playerId.toString());
                ps.setLong(2, Instant.now().toEpochMilli());
                ps.setString(3, playerName);
            }
        );
        if (onlinePlayer != null) {
            applyVerifiedMarker(onlinePlayer);
        }
    }

    private void applyVerifiedMarker(@NotNull Player player) {
        player.getPersistentDataContainer().set(verifiedKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void expireTimedOutSessions() {
        long now = System.currentTimeMillis();
        for (FirstJoinSession session : sessions.values()) {
            if (!session.isExpired(now)) {
                continue;
            }

            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                removeSession(session.playerId());
                continue;
            }

            player.getScheduler().run(plugin, task -> timeoutPlayer(player), () -> removeSession(session.playerId()));
        }
    }

    private int nextBetween(int min, int max) {
        if (max <= min) {
            return min;
        }
        return random.nextInt((max - min) + 1) + min;
    }

    private @Nullable Integer parseInt(@NotNull String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private @NotNull Location withDirection(@NotNull Location base, @NotNull Location directionSource) {
        Location reset = base.clone();
        reset.setYaw(directionSource.getYaw());
        reset.setPitch(directionSource.getPitch());
        return reset;
    }

    private @NotNull String displayName(@NotNull OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString() : name;
    }

    record FirstJoinEvaluationResult(FirstJoinOutcome outcome) {
    }

    enum FirstJoinOutcome {
        success,
        incorrect,
        failure,
        expired
    }
}

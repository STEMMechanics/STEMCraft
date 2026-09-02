package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.player.PlayerService.ResolvedPlayer;
import dev.stemcraft.api.service.playerreset.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Ordered coordinator for destructive player resets. */
public final class PlayerResetServiceImpl extends BaseService implements PlayerResetService {
    private static final String PERMISSION = "stemcraft.player-reset.admin";
    private static final Duration PLAN_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_OFFLINE_WAIT_TICKS = 200;
    private final Map<String, PlayerResetHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, PlayerResetPlan> plans = new ConcurrentHashMap<>();
    private final Set<UUID> resetting = ConcurrentHashMap.newKeySet();

    public PlayerResetServiceImpl(STEMCraft plugin, STEMCraftAPI api) { super(plugin, api, "player-reset"); }

    @Override public void onEnable() {
        registerSqlHandlers();
        registerVanillaHandler();
        registerCommand();
        api.events().register(AsyncPlayerPreLoginEvent.class, event -> {
            if (resetting.contains(event.getUniqueId())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Your player data reset is still running. Please try again shortly."));
            }
        });
    }

    @Override public void register(@NotNull PlayerResetHandler handler) {
        PlayerResetHandler previous = handlers.putIfAbsent(handler.id(), handler);
        if (previous != null) throw new IllegalArgumentException("Player reset handler already registered: " + handler.id());
    }

    @Override public void unregister(@NotNull String handlerId) { handlers.remove(handlerId); }

    @Override public @NotNull List<PlayerResetHandler> handlers() {
        return handlers.values().stream().sorted(Comparator.comparingInt(PlayerResetHandler::priority).thenComparing(PlayerResetHandler::id)).toList();
    }

    @Override public @NotNull PlayerResetPlan plan(@NotNull UUID uuid, @NotNull String playerName,
                                                    @NotNull PlayerResetScope scope, @NotNull String actorName) {
        prunePlans();
        PlayerResetContext context = new PlayerResetContext(uuid, playerName, scope, actorName);
        List<PlayerResetPlan.Entry> entries = handlers().stream().filter(handler -> handler.scopes().contains(scope))
            .map(handler -> new PlayerResetPlan.Entry(handler.id(), handler.preview(context))).toList();
        PlayerResetPlan plan = new PlayerResetPlan(uuid, playerName, scope, actorName,
            Instant.now().plus(PLAN_LIFETIME), entries);
        plans.put(actorName.toLowerCase(Locale.ROOT), plan);
        return plan;
    }

    @Override public @Nullable PlayerResetPlan getPlan(@NotNull String actorName) {
        prunePlans(); return plans.get(actorName.toLowerCase(Locale.ROOT));
    }

    @Override public void confirm(@NotNull String actorName) {
        PlayerResetPlan plan = plans.remove(actorName.toLowerCase(Locale.ROOT));
        if (plan == null || plan.expiresAt().isBefore(Instant.now())) throw new IllegalArgumentException("Reset plan is missing or expired.");
        Player player = Bukkit.getPlayer(plan.playerUuid());
        resetting.add(plan.playerUuid());
        if (player != null) {
            player.kick(Component.text("Your player data is being reset by an administrator. You may reconnect when the reset is complete."));
        }
        waitUntilOffline(plan, 0);
    }

    private void waitUntilOffline(PlayerResetPlan plan, int waitedTicks) {
        if (Bukkit.getPlayer(plan.playerUuid()) == null) {
            api.tasks().runLater(2L, () -> execute(plan));
            return;
        }
        if (waitedTicks >= MAX_OFFLINE_WAIT_TICKS) {
            notifyActor(plan.actorName(), "Player reset aborted: " + plan.playerName() + " did not disconnect.");
            resetting.remove(plan.playerUuid());
            return;
        }
        api.tasks().runLater(2L, () -> waitUntilOffline(plan, waitedTicks + 2));
    }

    private void execute(PlayerResetPlan plan) {
        PlayerResetContext context = new PlayerResetContext(plan.playerUuid(), plan.playerName(), plan.scope(), plan.actorName());
        List<String> completed = new ArrayList<>();
        String failure = null;
        String error = null;
        for (PlayerResetHandler handler : handlers()) {
            if (!handler.scopes().contains(plan.scope())) continue;
            try {
                handler.reset(context);
                completed.add(handler.id());
            } catch (Exception exception) {
                failure = handler.id(); error = exception.getMessage();
                plugin.getLogger().severe("Player reset failed in " + failure + " for " + plan.playerUuid() + ": " + error);
                break;
            }
        }
        boolean success = failure == null;
        api.audit().log(null, "player-reset", "target", plan.playerName(), "uuid", plan.playerUuid().toString(),
            "scope", plan.scope().name(), "actor", plan.actorName(), "result", success ? "success" : "failed:" + failure);
        notifyActor(plan.actorName(), success
            ? "Player reset completed for " + plan.playerName() + " (" + plan.scope().name().toLowerCase(Locale.ROOT) + ")."
            : "Player reset partially completed for " + plan.playerName() + "; failed at " + failure + ": " + error);
        resetting.remove(plan.playerUuid());
    }

    private void registerCommand() {
        api.commands().create("playerreset").description("Preview and execute a complete player-data reset.")
            .usage("/playerreset <player> [progression|gameplay|complete] | /playerreset confirm")
            .permission(PERMISSION)
            .tabCompletion("{player}")
            .tabCompletion("{player}", "progression")
            .tabCompletion("{player}", "gameplay")
            .tabCompletion("{player}", "complete")
            .tabCompletion("confirm")
            .executor((unused, command, ctx) -> command(ctx)).register(plugin);
    }

    private void command(CommandContext ctx) {
        if (ctx.args().isEmpty()) ctx.returnUsage();
        if ("confirm".equals(ctx.getArgLower(0))) {
            if (ctx.args().size() != 1) ctx.returnUsage();
            PlayerResetPlan plan = getPlan(ctx.getSenderName());
            if (plan == null) { ctx.returnError("Reset plan is missing or expired."); return; }
            confirm(ctx.getSenderName());
            ctx.returnSuccess("Reset accepted. The player will be kicked before data is removed.");
            return;
        }
        if (ctx.args().size() > 2) ctx.returnUsage();
        ResolvedPlayer player = api.players().resolveIdentity(ctx.getArg(0));
        if (player == null) { ctx.returnError("Player not found."); return; }
        PlayerResetScope scope = PlayerResetScope.COMPLETE;
        if (ctx.args().size() == 2) {
            try { scope = PlayerResetScope.valueOf(ctx.getArg(1).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { ctx.returnError("Area must be progression, gameplay, or complete."); return; }
        }
        PlayerResetPlan plan = plan(player.uuid(), player.name(), scope, ctx.getSenderName());
        sendResetWarning(ctx, "This command will reset the following for " + player.name() + ":");
        for (PlayerResetPlan.Entry entry : plan.entries()) {
            sendResetWarning(ctx, " - " + entry.preview().description() + " (" + entry.preview().records() + ")");
        }
        sendResetWarning(ctx, "This cannot be undone. To confirm within 10 minutes, run /playerreset confirm");
    }

    private void sendResetWarning(@NotNull CommandContext ctx, @NotNull String message) {
        ctx.getSender().sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private void registerSqlHandlers() {
        register(sqlHandler("progression-storage", PlayerResetScope.PROGRESSION, 200,
            List.of(
                spec("quest_progress", "player_uuid"), spec("quest_npc_daily_roll", "player_uuid"),
                spec("quest_completed", "player_uuid"), spec("quest_attempt_revision", "player_uuid"),
                spec("quest_npc_death_day", "player_uuid"), spec("quest_attempt_timing", "player_uuid"),
                spec("quest_failure", "player_uuid"), spec("quest_tracking", "player_uuid"),
                spec("quest_tracking_preferences", "player_uuid")
            )));
        register(sqlHandler("gameplay-storage", PlayerResetScope.GAMEPLAY, 210,
            List.of(spec("gamemode_inventories", "player_uuid"), spec("mailbox_notifications", "player_uuid"),
                spec("mailbox_player_inventories", "player_uuid"), spec("mailbox_mail_queue", "recipient_uuid"),
                spec("player_last_locations", "uuid"), spec("player_world_last_locations", "uuid"),
                spec("active_graves", "owner_uuid"), spec("notice_board_posts", "author_uuid"))));
        register(sqlHandler("complete-storage", PlayerResetScope.COMPLETE, 220,
            List.of(spec("first_join_players", "player_uuid"), spec("player_welcome_state", "player_uuid"),
                spec("random_first_spawn_seen", "uuid"), spec("random_first_spawn_spawns", "uuid"))));
    }

    private PlayerResetHandler sqlHandler(String id, PlayerResetScope minimum, int priority, List<SqlSpec> specs) {
        Set<PlayerResetScope> scopes = Arrays.stream(PlayerResetScope.values()).filter(scope -> scope.includes(minimum)).collect(java.util.stream.Collectors.toSet());
        return new PlayerResetHandler() {
            public @NotNull String id() { return id; }
            public @NotNull Set<PlayerResetScope> scopes() { return scopes; }
            public int priority() { return priority; }
            public @NotNull PlayerResetPreview preview(@NotNull PlayerResetContext context) {
                int count = specs.stream().mapToInt(spec -> count(spec, context.playerUuid())).sum();
                return new PlayerResetPreview(id.replace('-', ' '), count);
            }
            public void reset(@NotNull PlayerResetContext context) {
                for (SqlSpec spec : specs) delete(spec, context.playerUuid());
            }
        };
    }

    private int count(SqlSpec spec, UUID uuid) {
        if (!tableExists(spec.table())) return 0;
        return api.database().querySingleMapped("SELECT COUNT(*) FROM " + spec.table() + " WHERE " + spec.column() + "=?",
            ps -> ps.setString(1, uuid.toString()), rs -> rs.getInt(1), 0);
    }

    private void delete(SqlSpec spec, UUID uuid) {
        if (!tableExists(spec.table())) return;
        api.database().update("DELETE FROM " + spec.table() + " WHERE " + spec.column() + "=?", ps -> ps.setString(1, uuid.toString()));
    }

    private boolean tableExists(String table) {
        Integer found = api.database().querySingleMapped("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
            ps -> ps.setString(1, table), rs -> rs.getInt(1));
        return found != null && found > 0;
    }

    private void registerVanillaHandler() {
        register(new PlayerResetHandler() {
            public @NotNull String id() { return "vanilla-player-data"; }
            public @NotNull Set<PlayerResetScope> scopes() { return Set.of(PlayerResetScope.GAMEPLAY, PlayerResetScope.COMPLETE); }
            public int priority() { return 900; }
            public @NotNull PlayerResetPreview preview(@NotNull PlayerResetContext context) {
                return new PlayerResetPreview("Vanilla inventory, XP, statistics, advancements, recipes and spawn data", vanillaFiles(context.playerUuid()).size());
            }
            public void reset(@NotNull PlayerResetContext context) throws IOException {
                for (Path file : vanillaFiles(context.playerUuid())) Files.deleteIfExists(file);
            }
        });
    }

    private Set<Path> vanillaFiles(UUID uuid) {
        Set<Path> files = new LinkedHashSet<>();
        for (World world : Bukkit.getWorlds()) {
            Path folder = world.getWorldFolder().toPath();
            files.add(folder.resolve("playerdata").resolve(uuid + ".dat"));
            files.add(folder.resolve("playerdata").resolve(uuid + ".dat_old"));
            files.add(folder.resolve("stats").resolve(uuid + ".json"));
            files.add(folder.resolve("advancements").resolve(uuid + ".json"));
        }
        return files.stream().filter(Files::exists).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void notifyActor(String actorName, String message) {
        Player actor = Bukkit.getPlayerExact(actorName);
        if (actor != null) actor.sendMessage(Component.text(message)); else plugin.getLogger().info(message);
    }

    private void prunePlans() { plans.values().removeIf(plan -> plan.expiresAt().isBefore(Instant.now())); }
    private static SqlSpec spec(String table, String column) { return new SqlSpec(table, column); }
    private record SqlSpec(String table, String column) {}
}

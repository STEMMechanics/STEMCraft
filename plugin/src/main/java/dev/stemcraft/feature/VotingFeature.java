package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.mailbox.MailSendRequest;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import dev.stemcraft.api.util.TimeUtil;
import dev.stemcraft.service.EntitlementService;
import dev.stemcraft.service.PlayerLoginMessageServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** Reusable physical voting groups with personalized client-side levers. */
public final class VotingFeature extends BaseFeature {
    private static final String ADMIN_PERMISSION = "stemcraft.command.vote.admin";
    private static final String PLAYER_ACTIVITY_TABLE = "voting_player_activity";
    private static final String LIFECYCLE_TASK = "feature:voting-lifecycle";
    private final Map<String, VoteGroup> groups = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<String, VoteOption>> options = new HashMap<>();
    private final Map<String, LinkedHashMap<String, VoteTower>> towers = new HashMap<>();
    private final Map<String, Map<UUID, Set<String>>> votes = new HashMap<>();
    private final Map<String, Map<Integer, VoteReminder>> reminders = new HashMap<>();
    private final Map<String, List<VoteReward>> rewards = new HashMap<>();
    private final Map<UUID, PendingPlacement> placements = new HashMap<>();
    private final Map<UUID, String> removals = new HashMap<>();
    private final Map<UUID, String> lastChunks = new HashMap<>();
    private List<TowerElement> towerElements = List.of();

    public VotingFeature(STEMCraftAPI api) { super(api); }

    @Override public void onEnable() {
        loadTowerSchema(); ensureStorage(); loadAll(); registerCommand();
        api.events().register(PlayerInteractEvent.class, this::onInteract, EventPriority.HIGHEST, false);
        api.events().register(BlockBreakEvent.class, this::onBreak, EventPriority.HIGHEST, true);
        api.events().register(PlayerJoinEvent.class, event -> api.tasks().runLater(10L, () -> {
            refreshLevers(event.getPlayer());
            announceLoginVotes(event.getPlayer());
        }));
        api.events().register(PlayerQuitEvent.class, event -> recordPlayerActivity(event.getPlayer().getUniqueId(), Instant.now().getEpochSecond()));
        api.events().register(PlayerChangedWorldEvent.class, event -> api.tasks().runLater(1L, () -> refreshLevers(event.getPlayer())));
        api.events().register(PlayerMoveEvent.class, this::onMove, EventPriority.MONITOR, true);
        api.tasks().repeating("feature:voting-displays", 100L, this::refreshAllDisplays);
        api.tasks().repeating("feature:voting-plot-sync", 200L, this::refreshPlotSources);
        api.tasks().repeating(LIFECYCLE_TASK, 100L, this::processVotingLifecycle);
        refreshPlotSources();
        refreshAllDisplays();
        processVotingLifecycle();
    }

    @Override public void onDisable() {
        long now = Instant.now().getEpochSecond();
        for (Player player : Bukkit.getOnlinePlayers()) recordPlayerActivity(player.getUniqueId(), now);
        api.tasks().cancel("feature:voting-displays"); api.tasks().cancel("feature:voting-plot-sync");
        api.tasks().cancel(LIFECYCLE_TASK);
        placements.clear(); removals.clear(); lastChunks.clear();
    }

    @Override public void onReload() {
        super.onReload();
        loadTowerSchema();
        loadAll();
        refreshAllDisplays();
        processVotingLifecycle();
    }

    private void ensureStorage() {
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_groups (
              id TEXT PRIMARY KEY, name TEXT NOT NULL, votes_per_player INTEGER NOT NULL DEFAULT 1,
              starts_at INTEGER NOT NULL DEFAULT 0, ends_at INTEGER NOT NULL DEFAULT 9223372036854775807,
              paused INTEGER NOT NULL DEFAULT 0, source_type TEXT NOT NULL DEFAULT 'STATIC', source_value TEXT
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_options (
              group_id TEXT NOT NULL, option_id TEXT NOT NULL, display_name TEXT NOT NULL, owner_uuid TEXT,
              PRIMARY KEY(group_id, option_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_towers (
              group_id TEXT NOT NULL, option_id TEXT NOT NULL, world_name TEXT NOT NULL,
              x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, facing TEXT NOT NULL,
              PRIMARY KEY(group_id, option_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_votes (
              group_id TEXT NOT NULL, voter_uuid TEXT NOT NULL, option_id TEXT NOT NULL, created_at INTEGER NOT NULL,
              PRIMARY KEY(group_id, voter_uuid, option_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_player_activity (
              player_uuid TEXT PRIMARY KEY, last_seen_at INTEGER NOT NULL
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_reminders (
              group_id TEXT NOT NULL, days_remaining INTEGER NOT NULL, message TEXT NOT NULL,
              PRIMARY KEY(group_id, days_remaining)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_reminder_events (
              group_id TEXT NOT NULL, days_remaining INTEGER NOT NULL, sent_at INTEGER NOT NULL,
              PRIMARY KEY(group_id, days_remaining)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_rewards (
              group_id TEXT NOT NULL, reward_id INTEGER NOT NULL, action TEXT NOT NULL,
              value TEXT NOT NULL, duration_seconds INTEGER NOT NULL DEFAULT -1,
              PRIMARY KEY(group_id, reward_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_group_closures (
              group_id TEXT PRIMARY KEY, closed_at INTEGER NOT NULL, processed_at INTEGER
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_winners (
              group_id TEXT NOT NULL, option_id TEXT NOT NULL, option_name TEXT NOT NULL,
              player_uuid TEXT, vote_count INTEGER NOT NULL,
              PRIMARY KEY(group_id, option_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS voting_reward_deliveries (
              group_id TEXT NOT NULL, reward_id INTEGER NOT NULL, player_uuid TEXT NOT NULL,
              delivered_at INTEGER NOT NULL,
              PRIMARY KEY(group_id, reward_id, player_uuid)
            );
            """);
    }

    private void loadAll() {
        groups.clear(); options.clear(); towers.clear(); votes.clear(); reminders.clear(); rewards.clear();
        api.database().queryEach("SELECT * FROM voting_groups", null, rs -> {
            VoteGroup group = new VoteGroup(rs.getString("id"), rs.getString("name"), rs.getInt("votes_per_player"),
                rs.getLong("starts_at"), rs.getLong("ends_at"), rs.getInt("paused") != 0,
                rs.getString("source_type"), rs.getString("source_value"));
            groups.put(group.id, group);
        });
        api.database().queryEach("SELECT * FROM voting_options ORDER BY option_id", null, rs ->
            options.computeIfAbsent(rs.getString("group_id"), key -> new LinkedHashMap<>()).put(rs.getString("option_id"),
                new VoteOption(rs.getString("option_id"), rs.getString("display_name"), parseUuid(rs.getString("owner_uuid")))));
        api.database().queryEach("SELECT * FROM voting_towers ORDER BY option_id", null, rs ->
            towers.computeIfAbsent(rs.getString("group_id"), key -> new LinkedHashMap<>()).put(rs.getString("option_id"),
                new VoteTower(rs.getString("group_id"), rs.getString("option_id"), rs.getString("world_name"),
                    rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), parseFace(rs.getString("facing")))));
        api.database().queryEach("SELECT group_id,voter_uuid,option_id FROM voting_votes", null, rs ->
            votes.computeIfAbsent(rs.getString("group_id"), key -> new HashMap<>())
                .computeIfAbsent(UUID.fromString(rs.getString("voter_uuid")), key -> new HashSet<>()).add(rs.getString("option_id")));
        api.database().queryEach("SELECT group_id,days_remaining,message FROM voting_reminders ORDER BY days_remaining", null, rs ->
            reminders.computeIfAbsent(rs.getString("group_id"), key -> new TreeMap<>()).put(
                rs.getInt("days_remaining"), new VoteReminder(rs.getInt("days_remaining"), rs.getString("message"))));
        api.database().queryEach("SELECT group_id,reward_id,action,value,duration_seconds FROM voting_rewards ORDER BY reward_id", null, rs ->
            rewards.computeIfAbsent(rs.getString("group_id"), key -> new ArrayList<>()).add(new VoteReward(
                rs.getInt("reward_id"), rs.getString("action"), rs.getString("value"), rs.getLong("duration_seconds"))));
    }

    private void registerCommand() {
        api.tabComplete().register("vote-group", (player, args, currentArgument) -> new ArrayList<>(groups.keySet()));
        api.tabComplete().register("vote-option", (player, args, currentArgument) -> {
            if (args.length == 0) return List.of();
            LinkedHashMap<String, VoteOption> groupOptions = options.get(args[0].toLowerCase(Locale.ROOT));
            if (groupOptions == null) return List.of();
            List<String> result = new ArrayList<>(groupOptions.keySet()); result.add("next"); return result;
        });
        api.tabComplete().register("vote-date", (player, args, currentArgument) -> dateCompletions(currentArgument));
        api.commands().create("vote").description("View and administer physical voting groups.")
            .usage("/vote <help|status|tally|admin> ...")
            .tabCompletion("help").tabCompletion("status").tabCompletion("tally", "{vote-group}").tabCompletion("admin", "create")
            .tabCompletion("admin", "list")
            .tabCompletion("admin", "help")
            .tabCompletion("admin", "tally", "{vote-group}")
            .tabCompletion("admin", "view", "{vote-group}")
            .tabCompletion("admin", "title", "{vote-group}", "", "")
            .tabCompletion("admin", "option", "add", "{vote-group}", "", "")
            .tabCompletion("admin", "option", "remove", "{vote-group}", "{vote-option:$3}")
            .tabCompletion("admin", "option", "list", "{vote-group}")
            .tabCompletion("admin", "source", "plots", "{vote-group}", "{world}")
            .tabCompletion("admin", "schedule", "{vote-group}", "{vote-date}", "{vote-date}")
            .tabCompletion("admin", "reminder", "{vote-group}", "set", "", "")
            .tabCompletion("admin", "reminder", "{vote-group}", "clear", "")
            .tabCompletion("admin", "reminder", "{vote-group}", "list")
            .tabCompletion("admin", "reward", "{vote-group}", "add", "grant-badge", "", "")
            .tabCompletion("admin", "reward", "{vote-group}", "add", "revoke-badge", "")
            .tabCompletion("admin", "reward", "{vote-group}", "add", "mail", "", "")
            .tabCompletion("admin", "reward", "{vote-group}", "list")
            .tabCompletion("admin", "reward", "{vote-group}", "clear")
            .tabCompletion("admin", "tower", "place", "{vote-group}", "{vote-option:$3}")
            .tabCompletion("admin", "tower", "remove", "{vote-group}", "{vote-option:$3}")
            .tabCompletion("admin", "tower", "remove", "{vote-group}", "all")
            .tabCompletion("admin", "remove", "{vote-group}")
            .tabCompletion("admin", "pause", "{vote-group}")
            .tabCompletion("admin", "resume", "{vote-group}")
            .tabCompletion("admin", "reset", "{vote-group}")
            .tabCompletion("admin", "reset-player", "{vote-group}", "{player}")
            .executor((unused, command, ctx) -> execute(ctx)).register(STEMCraft.getPlugin());
    }

    private void execute(CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            if (ctx.isConsole()) showCommandHelp(ctx);
            else showStatus(ctx);
            return;
        }
        if (ctx.getArgLower(0).equals("help")) { showCommandHelp(ctx); return; }
        if (ctx.getArgLower(0).equals("status")) { showStatus(ctx); return; }
        if (ctx.getArgLower(0).equals("tally")) {
            if (!ctx.hasPermission(ADMIN_PERMISSION)) { ctx.returnError("You cannot review voting tallies."); return; }
            showTally(ctx);
            return;
        }
        if (!ctx.getArgLower(0).equals("admin")) { ctx.returnError("Use /vote status or /vote admin."); return; }
        if (!ctx.hasPermission(ADMIN_PERMISSION)) { ctx.returnError("You cannot administer voting groups."); return; }
        if (ctx.args().size() < 2 || ctx.getArg(1).matches("\\d+")) { showAdminMenu(ctx); return; }
        switch (ctx.getArgLower(1)) {
            case "help" -> showCommandHelp(ctx);
            case "create" -> createGroup(ctx);
            case "option" -> optionCommand(ctx);
            case "source" -> sourceCommand(ctx);
            case "schedule" -> schedule(ctx);
            case "reminder" -> reminderCommand(ctx);
            case "reward" -> rewardCommand(ctx);
            case "tower" -> towerCommand(ctx);
            case "pause" -> setPaused(ctx, true);
            case "resume" -> setPaused(ctx, false);
            case "reset" -> resetGroup(ctx);
            case "reset-player" -> resetPlayer(ctx);
            case "remove" -> removeGroup(ctx);
            case "list" -> listGroups(ctx);
            case "view" -> showGroupMenu(ctx);
            case "tally" -> showTally(ctx, 2);
            case "title" -> setTitle(ctx);
            default -> adminHelp(ctx);
        }
    }

    private void adminHelp(CommandContext ctx) { ctx.returnInfo("Use /vote admin <create|title|remove|option|source|schedule|reminder|reward|tower|pause|resume|reset|reset-player|list|tally>."); }

    private void showCommandHelp(CommandContext ctx) {
        ctx.info("Voting commands:");
        ctx.info("  /vote status [page] — Show voting groups and your vote allowance.");
        ctx.info("  /vote tally [group] — Review option vote totals (admin).");
        ctx.info("  /vote admin create <id> [votes-per-player] [name]");
        ctx.info("  /vote admin schedule <group> <start> <finish> — Dates use YYYY-MM-DD; timestamps are also accepted.");
        ctx.info("  /vote admin option <add|remove|list> ...");
        ctx.info("  /vote admin source plots <group> <world>");
        ctx.info("  /vote admin reminder <group> <set|clear|list> ...");
        ctx.info("  /vote admin reward <group> <add|remove|clear|list> ...");
        ctx.info("  /vote admin view <group> | list | title <group> <title>");
        ctx.info("  /vote admin pause|resume|reset|reset-player|remove <group>");
        ctx.info("  /vote admin tower place/remove ... — Requires an in-game player.");
        ctx.info("Use /vote help for this guide; players can run /vote with no arguments to view status.");
    }

    private void showTally(CommandContext ctx) {
        showTally(ctx, 1, "vote tally");
    }

    private void showTally(CommandContext ctx, int groupIndex) {
        showTally(ctx, groupIndex, "vote admin tally");
    }

    private void showTally(CommandContext ctx, int groupIndex, String paginationCommand) {
        String requestedId = ctx.args().size() > groupIndex ? ctx.getArgLower(groupIndex) : null;
        VoteGroup requested = requestedId == null ? null : groups.get(requestedId);
        if (requestedId != null && requested == null && !requestedId.matches("\\d+")) {
            ctx.returnError("Unknown voting group.");
            return;
        }

        List<VoteGroup> selected;
        if (requested != null) {
            selected = List.of(requested);
        } else {
            selected = new ArrayList<>(groups.values());
        }
        if (selected.isEmpty()) { ctx.returnInfo("No voting groups exist."); return; }

        if (ctx.isConsole()) {
            renderTallyText(ctx, selected);
            return;
        }

        if (requested != null) {
            renderTallyGroup(ctx, requested, ChatMenuUtil.getPageFromArgs(ctx.args(), groupIndex + 1, 1), paginationCommand);
            return;
        }

        int page = boundedTallyPage(ChatMenuUtil.getPageFromArgs(ctx.args(), groupIndex, 1), selected.size());
        ChatMenuUtil.render(ctx.getSender(), "Voting tallies", paginationCommand, page, selected.size(), (start, count, interactive) -> {
            List<Component> lines = new ArrayList<>();
            for (VoteGroup group : selected.subList(start, start + count)) {
                int resultVotes = resultTotalVotes(group.id);
                Component line = Component.text("● ", stateColour(group)).append(Component.text(group.name, NamedTextColor.WHITE))
                    .append(Component.text(" (" + group.id + ") — " + resultVotes + " " + voteWord(resultVotes), NamedTextColor.GRAY));
                if (interactive) line = line.clickEvent(ClickEvent.runCommand("/" + paginationCommand + " " + group.id))
                    .hoverEvent(HoverEvent.showText(Component.text("View this voting tally")));
                lines.add(line);
            }
            return lines;
        }, "No voting groups exist.");
    }

    private void renderTallyText(CommandContext ctx, List<VoteGroup> selected) {
        for (VoteGroup group : selected) {
            ctx.info("Tally for " + group.name + " (" + group.id + ") — " + state(group) + ":");
            List<VoteTallyRow> rows = tallyRows(group.id);
            if (rows.isEmpty()) {
                ctx.info("  No claimed vote options configured.");
                continue;
            }
            for (int index = 0; index < rows.size(); index++) {
                VoteTallyRow row = rows.get(index);
                String owner = row.owner() == null ? "" : " — " + Optional.ofNullable(Bukkit.getOfflinePlayer(row.owner()).getName()).orElse(row.owner().toString());
                ctx.info("  " + (index + 1) + ". " + row.optionName() + " [" + row.optionId() + "]: "
                    + row.votes() + " " + voteWord(row.votes()) + owner);
            }
            int resultVotes = resultTotalVotes(group.id);
            ctx.info("  Total: " + resultVotes + " " + voteWord(resultVotes));
        }
    }

    private void renderTallyGroup(CommandContext ctx, VoteGroup group, int requestedPage, String paginationCommand) {
        List<VoteTallyRow> rows = tallyRows(group.id);
        if (rows.isEmpty()) {
            ctx.returnInfo("No claimed vote options configured for " + group.name + ".");
            return;
        }
        int page = boundedTallyPage(requestedPage, rows.size());
        ChatMenuUtil.render(ctx.getSender(), "Tally: " + group.name + " (" + group.id + ") — " + state(group),
            paginationCommand + " " + group.id, page, rows.size(), (start, count, interactive) -> {
                List<Component> lines = new ArrayList<>();
                for (int index = start; index < start + count; index++) {
                    VoteTallyRow row = rows.get(index);
                    String owner = row.owner() == null ? "" : " — "
                        + Optional.ofNullable(Bukkit.getOfflinePlayer(row.owner()).getName()).orElse(row.owner().toString());
                    lines.add(Component.text((index + 1) + ". " + row.optionName(), NamedTextColor.WHITE)
                        .append(Component.text(" [" + row.optionId() + "] — " + row.votes() + " " + voteWord(row.votes()) + owner,
                            NamedTextColor.GRAY)));
                }
                return lines;
            }, "No claimed vote options configured.");
        int resultVotes = resultTotalVotes(group.id);
        ctx.getSender().sendMessage(Component.text("Total: " + resultVotes + " " + voteWord(resultVotes), NamedTextColor.GRAY));
        ctx.getSender().sendMessage(button("[← All groups]", "/" + paginationCommand, "Select another voting group"));
    }

    private int boundedTallyPage(int page, int count) {
        int maxPage = Math.max(1, (count + 7) / 8);
        return Math.max(1, Math.min(page, maxPage));
    }

    private List<VoteTallyRow> tallyRows(String groupId) {
        return options.getOrDefault(groupId, new LinkedHashMap<>()).values().stream()
            .filter(option -> includedInResults(groupId, option))
            .map(option -> new VoteTallyRow(option.id, option.name, option.owner, optionVotes(groupId, option.id)))
            .sorted(java.util.Comparator.comparingInt(VoteTallyRow::votes).reversed()
                .thenComparing(VoteTallyRow::optionName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(VoteTallyRow::optionId))
            .toList();
    }

    private void createGroup(CommandContext ctx) {
        if (ctx.args().size() < 3) { ctx.returnError("Usage: /vote admin create <id> [votes-per-player] [name]"); return; }
        String id = cleanId(ctx.getArgLower(2));
        if (id.isEmpty() || groups.containsKey(id)) { ctx.returnError("That voting group already exists or has an invalid ID."); return; }
        int allowance = 1; int nameFrom = 3;
        if (ctx.args().size() > 3 && ctx.getArg(3).matches("\\d+")) { allowance = Math.max(1, Integer.parseInt(ctx.getArg(3))); nameFrom = 4; }
        String name = ctx.args().size() > nameFrom ? String.join(" ", ctx.args().subList(nameFrom, ctx.args().size())) : id;
        VoteGroup group = new VoteGroup(id, name, allowance, 0, Long.MAX_VALUE, false, "STATIC", null);
        groups.put(id, group); saveGroup(group); ctx.returnSuccess("Created voting group " + id + " with " + allowance + " vote(s) per player.");
    }

    private void setTitle(CommandContext ctx) {
        if (ctx.args().size() < 4) {
            ctx.returnError("Usage: /vote admin title <group> <title>");
            return;
        }
        VoteGroup group = requireGroup(ctx, 2);
        if (group == null) return;
        String title = String.join(" ", ctx.args().subList(3, ctx.args().size())).trim();
        if (title.isEmpty()) {
            ctx.returnError("The voting group title cannot be blank.");
            return;
        }
        group.name = title;
        saveGroup(group);
        ctx.returnSuccess("Voting group title updated to " + title + ".");
    }

    private void optionCommand(CommandContext ctx) {
        if (ctx.args().size() >= 4 && ctx.getArgLower(2).equals("list")) {
            VoteGroup group = requireGroup(ctx, 3); if (group == null) return;
            for (VoteOption option : options.getOrDefault(group.id, new LinkedHashMap<>()).values())
                ctx.info(option.id + " — " + option.name);
            return;
        }
        if (ctx.args().size() >= 5 && ctx.getArgLower(2).equals("remove")) {
            VoteGroup group = requireGroup(ctx, 3); if (group == null) return;
            String optionId = resolveOption(group.id, ctx.getArg(4));
            if (optionId == null) { ctx.returnError("Unknown vote option."); return; }
            removeOption(group.id, optionId);
            ctx.returnSuccess("Voting tower and option slot removed."); return;
        }
        if (ctx.args().size() < 6 || !ctx.getArgLower(2).equals("add")) {
            ctx.returnError("Usage: /vote admin option <add <group> <option-id> <name>|remove <group> <option>|list <group>>"); return;
        }
        VoteGroup group = requireGroup(ctx, 3); if (group == null) return;
        String id = cleanId(ctx.getArgLower(4)); String name = String.join(" ", ctx.args().subList(5, ctx.args().size()));
        VoteOption option = new VoteOption(id, name, null);
        options.computeIfAbsent(group.id, key -> new LinkedHashMap<>()).put(id, option); saveOption(group.id, option);
        ctx.returnSuccess("Added " + name + " to " + group.name + ".");
    }

    private void sourceCommand(CommandContext ctx) {
        if (ctx.args().size() < 5 || !ctx.getArgLower(2).equals("plots")) {
            ctx.returnError("Usage: /vote admin source plots <group> <plot-world>"); return;
        }
        VoteGroup group = requireGroup(ctx, 3); if (group == null) return;
        group.sourceType = "PLOTSQUARED_PLAYERS"; group.sourceValue = ctx.getArg(4); saveGroup(group);
        int count = refreshPlotCandidates(group);
        if (count < 0) ctx.returnError("PlotSquared is not available or that plot world could not be loaded.");
        else ctx.returnSuccess("Loaded " + count + " plot creator(s) from " + group.sourceValue + ".");
    }

    private void schedule(CommandContext ctx) {
        if (ctx.args().size() < 5) { ctx.returnError("Usage: /vote admin schedule <group> <start> <finish>"); return; }
        VoteGroup group = requireGroup(ctx, 2); if (group == null) return;
        try { group.startsAt = parseTime(ctx.getArg(3)); group.endsAt = parseTime(ctx.getArg(4)); }
        catch (IllegalArgumentException ex) { ctx.returnError(ex.getMessage()); return; }
        if (group.endsAt <= group.startsAt) { ctx.returnError("The finish must be after the start."); return; }
        clearLifecycleState(group.id);
        saveGroup(group); refreshGroup(group.id); ctx.returnSuccess("Voting schedule updated for " + group.name + ".");
    }

    private void reminderCommand(CommandContext ctx) {
        if (ctx.args().size() < 4) {
            ctx.returnError("Usage: /vote admin reminder <group> <set <days> <message>|clear <days>|list>");
            return;
        }
        VoteGroup group = requireGroup(ctx, 2); if (group == null) return;
        String action = ctx.getArgLower(3);
        if (action.equals("list")) {
            Map<Integer, VoteReminder> groupReminders = reminders.getOrDefault(group.id, Map.of());
            if (groupReminders.isEmpty()) { ctx.info("No reminders configured for " + group.name + "."); return; }
            groupReminders.values().stream().sorted(java.util.Comparator.comparingInt(VoteReminder::daysRemaining))
                .forEach(reminder -> ctx.info(reminder.daysRemaining() + " day(s): " + reminder.message()));
            return;
        }
        if (action.equals("clear")) {
            if (ctx.args().size() < 5) { ctx.returnError("Usage: /vote admin reminder <group> clear <days>"); return; }
            int days = parseNonNegativeInt(ctx.getArg(4), "Reminder days must be zero or greater.", ctx);
            if (days < 0) return;
            api.database().update("DELETE FROM voting_reminders WHERE group_id=? AND days_remaining=?", ps -> {
                ps.setString(1, group.id); ps.setInt(2, days);
            });
            api.database().update("DELETE FROM voting_reminder_events WHERE group_id=? AND days_remaining=?", ps -> {
                ps.setString(1, group.id); ps.setInt(2, days);
            });
            reminders.getOrDefault(group.id, new HashMap<>()).remove(days);
            ctx.returnSuccess("Removed the " + days + "-day voting reminder for " + group.name + ".");
            return;
        }
        if (!action.equals("set") || ctx.args().size() < 6) {
            ctx.returnError("Usage: /vote admin reminder <group> set <days> <message>");
            return;
        }
        int days = parseNonNegativeInt(ctx.getArg(4), "Reminder days must be zero or greater.", ctx);
        if (days < 0) return;
        String message = String.join(" ", ctx.args().subList(5, ctx.args().size())).trim();
        if (message.isBlank()) { ctx.returnError("The reminder message cannot be blank."); return; }
        VoteReminder reminder = new VoteReminder(days, message);
        api.database().update("INSERT OR REPLACE INTO voting_reminders(group_id,days_remaining,message) VALUES(?,?,?)", ps -> {
            ps.setString(1, group.id); ps.setInt(2, days); ps.setString(3, message);
        });
        reminders.computeIfAbsent(group.id, key -> new TreeMap<>()).put(days, reminder);
        api.database().update("DELETE FROM voting_reminder_events WHERE group_id=? AND days_remaining=?", ps -> {
            ps.setString(1, group.id); ps.setInt(2, days);
        });
        ctx.returnSuccess("Saved the " + days + "-day voting reminder for " + group.name + ".");
    }

    private void rewardCommand(CommandContext ctx) {
        if (ctx.args().size() < 4) {
            ctx.returnError("Usage: /vote admin reward <group> <add|remove|clear|list> ...");
            return;
        }
        VoteGroup group = requireGroup(ctx, 2); if (group == null) return;
        String action = ctx.getArgLower(3);
        List<VoteReward> groupRewards = rewards.computeIfAbsent(group.id, key -> new ArrayList<>());
        if (action.equals("list")) {
            if (groupRewards.isEmpty()) { ctx.info("No winner rewards configured for " + group.name + "."); return; }
            groupRewards.stream().sorted(java.util.Comparator.comparingInt(VoteReward::id))
                .forEach(reward -> ctx.info("#" + reward.id() + " " + describeReward(reward)));
            return;
        }
        if (action.equals("clear")) {
            api.database().update("DELETE FROM voting_rewards WHERE group_id=?", ps -> ps.setString(1, group.id));
            groupRewards.clear();
            ctx.returnSuccess("Cleared winner rewards for " + group.name + ".");
            return;
        }
        if (action.equals("remove")) {
            if (ctx.args().size() < 5) { ctx.returnError("Usage: /vote admin reward <group> remove <reward-id>"); return; }
            int id = parseNonNegativeInt(ctx.getArg(4), "Reward ID must be a number.", ctx);
            if (id < 0) return;
            api.database().update("DELETE FROM voting_rewards WHERE group_id=? AND reward_id=?", ps -> {
                ps.setString(1, group.id); ps.setInt(2, id);
            });
            groupRewards.removeIf(reward -> reward.id() == id);
            ctx.returnSuccess("Removed winner reward #" + id + " from " + group.name + ".");
            return;
        }
        if (!action.equals("add") || ctx.args().size() < 6) {
            ctx.returnError("Usage: /vote admin reward <group> add <grant-badge <badge> [duration]|revoke-badge <badge>|mail <message>>");
            return;
        }
        String rewardType = ctx.getArgLower(4);
        String value;
        long duration = -1L;
        switch (rewardType) {
            case "revoke-badge" -> {
                if (ctx.args().size() < 6) { ctx.returnError("Usage: /vote admin reward <group> add revoke-badge <badge>"); return; }
                value = cleanBadgeId(ctx.getArg(5));
                if (value.isBlank()) { ctx.returnError("Invalid badge ID."); return; }
            }
            case "grant-badge" -> {
                if (ctx.args().size() < 6) { ctx.returnError("Usage: /vote admin reward <group> add grant-badge <badge> [duration]"); return; }
                value = cleanBadgeId(ctx.getArg(5));
                if (value.isBlank()) { ctx.returnError("Invalid badge ID."); return; }
                if (ctx.args().size() >= 7) {
                    try { duration = TimeUtil.parseDuration(ctx.getArg(6), true); }
                    catch (IllegalArgumentException ex) { ctx.returnError("Use a duration such as 30d, 12h, or permanent."); return; }
                    if (duration == 0L) { ctx.returnError("Badge duration must be greater than zero or permanent."); return; }
                }
            }
            case "mail" -> {
                value = String.join(" ", ctx.args().subList(5, ctx.args().size())).trim();
                if (value.isBlank()) { ctx.returnError("The winner mail message cannot be blank."); return; }
            }
            default -> { ctx.returnError("Reward actions are grant-badge, revoke-badge, and mail."); return; }
        }
        int id = groupRewards.stream().mapToInt(VoteReward::id).max().orElse(0) + 1;
        String normalizedAction = switch (rewardType) {
            case "grant-badge" -> "GRANT_BADGE_WINNER";
            case "revoke-badge" -> "REVOKE_BADGE_ALL";
            default -> "MAIL_WINNER";
        };
        long savedDuration = duration;
        VoteReward reward = new VoteReward(id, normalizedAction, value, savedDuration);
        api.database().update("INSERT INTO voting_rewards(group_id,reward_id,action,value,duration_seconds) VALUES(?,?,?,?,?)", ps -> {
            ps.setString(1, group.id); ps.setInt(2, id); ps.setString(3, normalizedAction);
            ps.setString(4, value); ps.setLong(5, savedDuration);
        });
        groupRewards.add(reward);
        ctx.returnSuccess("Added winner reward #" + id + " to " + group.name + ": " + describeReward(reward));
    }

    private int parseNonNegativeInt(String value, String error, CommandContext ctx) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            ctx.returnError(error);
            return -1;
        }
    }

    private String cleanBadgeId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9][a-z0-9_.-]*") ? normalized : "";
    }

    private String describeReward(VoteReward reward) {
        return switch (reward.action()) {
            case "GRANT_BADGE_WINNER" -> "grant badge " + reward.value()
                + (reward.durationSeconds() < 0 ? " permanently" : " for " + TimeUtil.formatDuration(reward.durationSeconds()));
            case "REVOKE_BADGE_ALL" -> "revoke badge " + reward.value() + " from all players";
            case "MAIL_WINNER" -> "mail winners: " + reward.value();
            default -> reward.action() + " " + reward.value();
        };
    }

    private void towerCommand(CommandContext ctx) {
        if (ctx.args().size() < 4 || !Set.of("place", "remove").contains(ctx.getArgLower(2))) {
            ctx.returnError("Usage: /vote admin tower <place <group> <option>|remove <group> [option]>"); return;
        }
        ctx.checkNotConsole(); VoteGroup group = requireGroup(ctx, 3); if (group == null) return;
        if (ctx.getArgLower(2).equals("remove")) {
            if (ctx.args().size() == 4) {
                placements.remove(ctx.asPlayer().getUniqueId()); removals.put(ctx.asPlayer().getUniqueId(), group.id);
                ctx.returnSuccess("Right-click any block in the tower to remove it. Sneak-right-click to cancel."); return;
            }
            if (ctx.getArg(4).equalsIgnoreCase("all")) {
                int removed = removeAllTowers(group.id);
                ctx.returnSuccess("Removed " + removed + " voting tower(s). Option slots remain available for placement."); return;
            }
            String optionId = resolvePlacedTowerOption(group.id, ctx.getArg(4));
            if (optionId == null || !removeTower(group.id, optionId)) { ctx.returnError("That option does not have a placed tower."); return; }
            ctx.returnSuccess("Voting tower removed. To also delete its option slot, use /vote admin option remove " + group.id + " " + optionId + "."); return;
        }
        if (ctx.args().size() < 5) { ctx.returnError("Usage: /vote admin tower place <group> <option>"); return; }
        String optionId = resolveOption(group.id, ctx.getArg(4));
        if (optionId == null && ctx.getArg(4).equalsIgnoreCase("next") && isPlotSource(group)) {
            VoteOption slot = createPlotSlot(group.id);
            options.computeIfAbsent(group.id, key -> new LinkedHashMap<>()).put(slot.id, slot); saveOption(group.id, slot);
            optionId = slot.id;
        }
        if (optionId == null) { ctx.returnError("Unknown vote option. Use its ID, name, or 'next'."); return; }
        boolean continuous = ctx.getArg(4).equalsIgnoreCase("next");
        removals.remove(ctx.asPlayer().getUniqueId());
        placements.put(ctx.asPlayer().getUniqueId(), new PendingPlacement(group.id, optionId, continuous));
        ctx.returnSuccess("Right-click the block where the tower lectern belongs."
            + (continuous ? " Placement will continue until you sneak-right-click." : " Sneak-right-click to cancel."));
    }

    private void setPaused(CommandContext ctx, boolean paused) {
        VoteGroup group = requireGroup(ctx, 2); if (group == null) return;
        group.paused = paused; saveGroup(group); refreshGroup(group.id);
        ctx.returnSuccess(group.name + (paused ? " paused." : " resumed."));
    }

    private void resetGroup(CommandContext ctx) {
        VoteGroup group = requireGroup(ctx, 2); if (group == null) return;
        api.database().update("DELETE FROM voting_votes WHERE group_id=?", ps -> ps.setString(1, group.id));
        votes.remove(group.id); refreshGroup(group.id); ctx.returnSuccess("All votes reset for " + group.name + ".");
    }

    private void resetPlayer(CommandContext ctx) {
        if (ctx.args().size() < 4) { ctx.returnError("Usage: /vote admin reset-player <group> <player>"); return; }
        VoteGroup group = requireGroup(ctx, 2); if (group == null) return;
        OfflinePlayer player = Bukkit.getOfflinePlayer(ctx.getArg(3)); UUID uuid = player.getUniqueId();
        api.database().update("DELETE FROM voting_votes WHERE group_id=? AND voter_uuid=?", ps -> { ps.setString(1, group.id); ps.setString(2, uuid.toString()); });
        votes.getOrDefault(group.id, Map.of()).remove(uuid); refreshGroup(group.id);
        Player online = Bukkit.getPlayer(uuid); if (online != null) refreshLevers(online);
        ctx.returnSuccess("Reset votes cast by " + ctx.getArg(3) + ".");
    }

    private void removeGroup(CommandContext ctx) {
        VoteGroup group = requireGroup(ctx, 2); if (group == null) return;
        int removedTowers = removeAllTowers(group.id);
        api.database().update("DELETE FROM voting_votes WHERE group_id=?", ps -> ps.setString(1, group.id));
        api.database().update("DELETE FROM voting_options WHERE group_id=?", ps -> ps.setString(1, group.id));
        api.database().update("DELETE FROM voting_groups WHERE id=?", ps -> ps.setString(1, group.id));
        api.database().update("DELETE FROM voting_reminders WHERE group_id=?", ps -> ps.setString(1, group.id));
        clearLifecycleState(group.id);
        api.database().update("DELETE FROM voting_rewards WHERE group_id=?", ps -> ps.setString(1, group.id));
        groups.remove(group.id); options.remove(group.id); votes.remove(group.id); towers.remove(group.id);
        reminders.remove(group.id); rewards.remove(group.id);
        placements.entrySet().removeIf(entry -> entry.getValue().groupId.equals(group.id));
        removals.entrySet().removeIf(entry -> entry.getValue().equals(group.id));
        ctx.returnSuccess("Removed voting group " + group.id + " and " + removedTowers + " physical tower(s).");
    }

    private void listGroups(CommandContext ctx) {
        showAdminMenu(ctx);
    }

    private void showStatus(CommandContext ctx) {
        if (groups.isEmpty()) { ctx.returnInfo("No voting groups exist."); return; }
        UUID uuid = ctx.getSender() instanceof Player player ? player.getUniqueId() : null;
        List<VoteGroup> values = new ArrayList<>(groups.values());
        int page = ChatMenuUtil.getPageFromArgs(ctx.args(), 1, 1);
        ChatMenuUtil.render(ctx.getSender(), "Voting", "vote status", page, values.size(), (start, count, interactive) -> {
            List<Component> lines = new ArrayList<>();
            for (VoteGroup group : values.subList(start, start + count)) {
                int used = uuid == null ? 0 : votes.getOrDefault(group.id, Map.of()).getOrDefault(uuid, Set.of()).size();
                lines.add(Component.text("● ", stateColour(group)).append(Component.text(group.name, NamedTextColor.WHITE))
                    .append(Component.text(" — " + state(group) + " — " + used + "/" + group.votesPerPlayer + " used", NamedTextColor.GRAY)));
            }
            return lines;
        }, "No voting groups exist.");
    }

    private void showAdminMenu(CommandContext ctx) {
        List<VoteGroup> values = new ArrayList<>(groups.values());
        int page = ChatMenuUtil.getPageFromArgs(ctx.args(), 1, 1);
        ChatMenuUtil.render(ctx.getSender(), "Voting administration", "vote admin", page, values.size(), (start, count, interactive) -> {
            List<Component> lines = new ArrayList<>();
            for (VoteGroup group : values.subList(start, start + count)) {
                Component line = Component.text("● ", stateColour(group)).append(Component.text(group.name, NamedTextColor.WHITE))
                    .append(Component.text(" (" + group.id + ")", NamedTextColor.DARK_GRAY))
                    .append(Component.text(" — " + totalVotes(group.id) + " votes", NamedTextColor.GRAY));
                if (interactive) line = line.clickEvent(ClickEvent.runCommand("/vote admin view " + group.id))
                    .hoverEvent(HoverEvent.showText(Component.text("Manage this voting group")));
                lines.add(line);
            }
            return lines;
        }, "No voting groups exist.");
        ctx.getSender().sendMessage(suggestButton("[＋ Create]", "/vote admin create new-vote 1 New Vote", "Create a voting group"));
    }

    private void showGroupMenu(CommandContext ctx) {
        VoteGroup group = requireGroup(ctx, 2); if (group == null) return;
        List<VoteOption> values = new ArrayList<>(options.getOrDefault(group.id, new LinkedHashMap<>()).values());
        int page = values.isEmpty() ? 1 : ChatMenuUtil.getPageFromArgs(ctx.args(), 3, 1);
        int renderedCount = Math.max(1, values.size());
        ChatMenuUtil.render(ctx.getSender(), group.name + " (" + group.id + ") — " + state(group), "vote admin view " + group.id,
            page, renderedCount, (start, count, interactive) -> {
                List<Component> lines = new ArrayList<>();
                if (values.isEmpty()) {
                    lines.add(Component.text("No options configured. Add a static option or load PlotSquared creators.", NamedTextColor.GRAY));
                    return lines;
                }
                for (VoteOption option : values.subList(start, start + count)) {
                    boolean placed = towers.getOrDefault(group.id, new LinkedHashMap<>()).containsKey(option.id);
                    Component line = Component.text(option.name, NamedTextColor.WHITE)
                        .append(Component.text(" — " + optionVotes(group.id, option.id) + " votes", NamedTextColor.GRAY))
                        .append(Component.space()).append(placed
                            ? Component.text("[Placed]", NamedTextColor.GREEN)
                            : button("[Place]", "/vote admin tower place " + group.id + " " + option.id, "Place this option's tower"))
                        .append(Component.space()).append(suggestButton("[Remove]", "/vote admin option remove " + group.id + " " + option.id,
                            "Remove this option slot, its tower, and its votes"));
                    lines.add(line);
                }
                return lines;
            }, "This group has no options.");
        int placed = towers.getOrDefault(group.id, new LinkedHashMap<>()).size();
        ctx.getSender().sendMessage(Component.text("Allowance: ", NamedTextColor.GRAY)
            .append(Component.text(group.votesPerPlayer + " per player", NamedTextColor.WHITE))
            .append(Component.text(" • Schedule: ", NamedTextColor.GRAY))
            .append(Component.text(formatTime(group.startsAt) + " → " + formatTime(group.endsAt), NamedTextColor.WHITE)));
        ctx.getSender().sendMessage(Component.text("Source: ", NamedTextColor.GRAY)
            .append(Component.text(group.sourceType + (group.sourceValue == null ? "" : " (" + group.sourceValue + ")"), NamedTextColor.WHITE))
            .append(Component.text(" • Options: ", NamedTextColor.GRAY)).append(Component.text(values.size(), NamedTextColor.WHITE))
            .append(Component.text(" • Towers: ", NamedTextColor.GRAY)).append(Component.text(placed + "/" + values.size(), NamedTextColor.WHITE))
            .append(Component.text(" • Votes: ", NamedTextColor.GRAY)).append(Component.text(totalVotes(group.id), NamedTextColor.WHITE)));
        ctx.getSender().sendMessage(Component.text("Reminders: ", NamedTextColor.GRAY)
            .append(Component.text(reminders.getOrDefault(group.id, Map.of()).size(), NamedTextColor.WHITE))
            .append(Component.text(" • Winner rewards: ", NamedTextColor.GRAY))
            .append(Component.text(rewards.getOrDefault(group.id, List.of()).size(), NamedTextColor.WHITE)));
        ctx.getSender().sendMessage(Component.text("Static option syntax: ", NamedTextColor.DARK_GRAY)
            .append(Component.text("<option-id> <display name>", NamedTextColor.GRAY)));
        String toggleCommand = "/vote admin " + (group.paused ? "resume " : "pause ") + group.id;
        ctx.getSender().sendMessage(button(group.paused ? "[Resume]" : "[Pause]", toggleCommand, group.paused ? "Resume voting" : "Pause voting")
            .append(Component.space()).append(suggestButton("[Schedule]", "/vote admin schedule " + group.id + " now ", "Set start and finish"))
            .append(Component.space()).append(suggestButton("[＋ Static option]", "/vote admin option add " + group.id + " ", "Enter <option-id> <display name>; for example fishing Fishing Expansion"))
            .append(Component.space()).append(suggestButton("[Plot source]", "/vote admin source plots " + group.id + " ", "Load plot creators")));
        ctx.getSender().sendMessage(button("[Place next]", "/vote admin tower place " + group.id + " next", "Place the next unplaced tower")
            .append(Component.space()).append(suggestButton("[Remove towers]", "/vote admin tower remove " + group.id + " all", "Remove every physical tower but keep option slots"))
            .append(Component.space()).append(suggestButton("[Reset votes]", "/vote admin reset " + group.id, "Review before sending"))
            .append(Component.space()).append(suggestButton("[Remove group]", "/vote admin remove " + group.id, "Remove this group, all options, votes, and physical towers")));
    }

    private void announceLoginVotes(Player player) {
        UUID playerId = player.getUniqueId();
        Long previousLastSeenAt = loadPlayerActivity(playerId);
        recordPlayerActivity(playerId, Instant.now().getEpochSecond());
        if (!getConfigSection().getBoolean("login-announcement.enabled", true)) return;

        List<String> summaries = new ArrayList<>();
        long now = Instant.now().getEpochSecond();
        for (VoteGroup group : groups.values()) {
            if (!"open".equals(state(group))) continue;
            GroupLoginSummary summary = loginSummary(group, playerId, previousLastSeenAt);
            String message = formatLoginVoteSummary(group.name, summary.receivedVotes(), summary.totalVotes(), summary.bestRank());
            if (!message.isEmpty()) summaries.add(message);
            VoteReminder reminder = loginReminder(group, now);
            if (reminder != null) summaries.add(formatReminderMessage(reminder.message(), group, now));
        }
        if (summaries.isEmpty()) return;

        PlayerLoginMessageServiceImpl loginMessages = STEMCraft.getPlugin().playerLoginMessages();
        loginMessages.queue(player, PlayerLoginMessageServiceImpl.Priority.OTHER, "voting", recipient -> {
            for (String summary : summaries) recipient.sendMessage(Component.text(summary, NamedTextColor.GRAY));
        });
    }

    private VoteReminder loginReminder(VoteGroup group, long now) {
        if (group.endsAt == Long.MAX_VALUE || group.endsAt <= now) return null;
        Map<Integer, VoteReminder> configured = reminders.getOrDefault(group.id, Map.of());
        if (configured.isEmpty()) return null;
        int days = daysRemaining(group.endsAt, now);
        return configured.values().stream()
            .filter(reminder -> reminder.daysRemaining() >= days)
            .min(java.util.Comparator.comparingInt(VoteReminder::daysRemaining))
            .orElse(null);
    }

    static int daysRemaining(long endsAt, long now) {
        if (endsAt == Long.MAX_VALUE) return Integer.MAX_VALUE;
        long seconds = Math.max(0L, endsAt - now);
        return (int) Math.min(Integer.MAX_VALUE, (seconds + 86_399L) / 86_400L);
    }

    static String formatReminderMessage(String template, String groupName, int days, String endTime, int totalVotes) {
        return template
            .replace("{group}", groupName)
            .replace("{days}", Integer.toString(days))
            .replace("{days-remaining}", Integer.toString(days))
            .replace("{end}", endTime)
            .replace("{votes}", Integer.toString(totalVotes));
    }

    private String formatReminderMessage(String template, VoteGroup group, long now) {
        return formatReminderMessage(template, group.name, daysRemaining(group.endsAt, now), formatTime(group.endsAt), totalVotes(group.id));
    }

    private void processVotingLifecycle() {
        long now = Instant.now().getEpochSecond();
        for (VoteGroup group : new ArrayList<>(groups.values())) {
            if ("open".equals(state(group))) processScheduledReminders(group, now);
            if ("finished".equals(state(group)) && group.endsAt != Long.MAX_VALUE) closeGroup(group, now);
        }
    }

    private void processScheduledReminders(VoteGroup group, long now) {
        if (group.endsAt == Long.MAX_VALUE) return;
        int actualDays = daysRemaining(group.endsAt, now);
        for (VoteReminder reminder : reminders.getOrDefault(group.id, Map.of()).values()) {
            if (actualDays > reminder.daysRemaining() || !claimReminder(group.id, reminder.daysRemaining(), now)) continue;
            String message = formatReminderMessage(reminder.message(), group, now);
            if (getConfigSection().getBoolean("announcements.enabled", true)) {
                Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(Component.text(message, NamedTextColor.YELLOW)));
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("group_id", group.id);
            data.put("group", group.name);
            data.put("configured_days_remaining", reminder.daysRemaining());
            data.put("days_remaining", actualDays);
            data.put("ends_at", group.endsAt);
            data.put("message", message);
            api.web().publishEvent("stemcraft.voting.reminder", null, null, data);
        }
    }

    private boolean claimReminder(String groupId, int days, long now) {
        return api.database().update(
            "INSERT OR IGNORE INTO voting_reminder_events(group_id,days_remaining,sent_at) VALUES(?,?,?)", ps -> {
                ps.setString(1, groupId); ps.setInt(2, days); ps.setLong(3, now);
            }) > 0;
    }

    private void closeGroup(VoteGroup group, long now) {
        ClosureStatus closure = loadClosure(group.id);
        List<ClosedWinner> winners;
        if (closure == null) {
            winners = calculateWinners(group.id);
            api.database().update("INSERT OR IGNORE INTO voting_group_closures(group_id,closed_at,processed_at) VALUES(?,?,NULL)", ps -> {
                ps.setString(1, group.id); ps.setLong(2, now);
            });
            for (ClosedWinner winner : winners) api.database().update(
                "INSERT OR IGNORE INTO voting_winners(group_id,option_id,option_name,player_uuid,vote_count) VALUES(?,?,?,?,?)", ps -> {
                    ps.setString(1, group.id); ps.setString(2, winner.optionId()); ps.setString(3, winner.optionName());
                    ps.setString(4, winner.playerUuid() == null ? null : winner.playerUuid().toString()); ps.setInt(5, winner.votes());
                });
            publishCloseEvent(group, winners, now);
            closure = loadClosure(group.id);
        } else {
            winners = loadClosedWinners(group.id);
        }
        if (closure == null || closure.processedAt() != null) return;
        if (!applyRewards(group, winners)) return;
        api.database().update("UPDATE voting_group_closures SET processed_at=? WHERE group_id=? AND processed_at IS NULL", ps -> {
            ps.setLong(1, now); ps.setString(2, group.id);
        });
    }

    private List<ClosedWinner> calculateWinners(String groupId) {
        int maximum = maximumVotes(groupId);
        if (maximum <= 0) return List.of();
        return options.getOrDefault(groupId, new LinkedHashMap<>()).values().stream()
            .filter(option -> includedInResults(groupId, option))
            .filter(option -> optionVotes(groupId, option.id()) == maximum)
            .map(option -> new ClosedWinner(option.id(), option.name(), option.owner, maximum))
            .toList();
    }

    private void publishCloseEvent(VoteGroup group, List<ClosedWinner> winners, long now) {
        List<Map<String, Object>> winnerData = winners.stream().map(winner -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("option_id", winner.optionId());
            data.put("option", winner.optionName());
            data.put("player_uuid", winner.playerUuid() == null ? null : winner.playerUuid().toString());
            data.put("votes", winner.votes());
            return data;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("group_id", group.id);
        data.put("group", group.name);
        data.put("closed_at", now);
        data.put("ends_at", group.endsAt);
        data.put("total_votes", resultTotalVotes(group.id));
        data.put("winners", winnerData);
        api.web().publishEvent("stemcraft.voting.closed", null, null, data);
    }

    private ClosureStatus loadClosure(String groupId) {
        return api.database().querySingleMapped("SELECT closed_at,processed_at FROM voting_group_closures WHERE group_id=?",
            ps -> ps.setString(1, groupId), rs -> {
                long closedAt = rs.getLong(1);
                long processedAt = rs.getLong(2);
                return new ClosureStatus(closedAt, rs.wasNull() ? null : processedAt);
            });
    }

    private List<ClosedWinner> loadClosedWinners(String groupId) {
        List<ClosedWinner> result = new ArrayList<>();
        api.database().queryEach("SELECT option_id,option_name,player_uuid,vote_count FROM voting_winners WHERE group_id=? ORDER BY option_id",
            ps -> ps.setString(1, groupId), rs -> result.add(new ClosedWinner(
                rs.getString(1), rs.getString(2), parseUuid(rs.getString(3)), rs.getInt(4))));
        return result;
    }

    private boolean applyRewards(VoteGroup group, List<ClosedWinner> winners) {
        EntitlementService entitlements = STEMCraft.getPlugin().entitlements();
        for (VoteReward reward : rewards.getOrDefault(group.id, List.of())) {
            switch (reward.action()) {
                case "REVOKE_BADGE_ALL" -> {
                    if (entitlements == null) return false;
                    for (UUID playerUuid : entitlements.knownPlayerIds()) {
                        if (deliveryExists(group.id, reward.id(), playerUuid)) continue;
                        if (!entitlements.revokeBadge(playerUuid, reward.value())) {
                            STEMCraft.getPlugin().getLogger().warning("Could not revoke voting reward badge '"
                                + reward.value() + "' while closing " + group.id + ".");
                            continue;
                        }
                        recordDelivery(group.id, reward.id(), playerUuid);
                    }
                }
                case "GRANT_BADGE_WINNER" -> {
                    if (entitlements == null) return false;
                    for (ClosedWinner winner : winners) {
                        UUID playerUuid = winner.playerUuid();
                        if (playerUuid == null || deliveryExists(group.id, reward.id(), playerUuid)) continue;
                        boolean granted = reward.durationSeconds() < 0
                            ? entitlements.grantBadge(playerUuid, reward.value())
                            : entitlements.grantBadge(playerUuid, reward.value(), reward.durationSeconds());
                        if (!granted) {
                            STEMCraft.getPlugin().getLogger().warning("Could not grant voting reward badge '"
                                + reward.value() + "' to " + playerUuid + " while closing " + group.id + ".");
                            continue;
                        }
                        recordDelivery(group.id, reward.id(), playerUuid);
                    }
                }
                case "MAIL_WINNER" -> {
                    if (api.mailboxes() == null) return false;
                    for (ClosedWinner winner : winners) {
                        UUID playerUuid = winner.playerUuid();
                        if (playerUuid == null || deliveryExists(group.id, reward.id(), playerUuid)) continue;
                        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
                        String playerName = Objects.requireNonNullElse(player.getName(), playerUuid.toString());
                        String message = formatWinnerMessage(reward.value(), group, winner, playerName);
                        if (!api.mailboxes().send(new MailSendRequest("STEMCraft", playerUuid, message, List.of())).queued()) {
                            STEMCraft.getPlugin().getLogger().warning("Could not send voting reward mail to "
                                + playerName + " while closing " + group.id + ".");
                            continue;
                        }
                        recordDelivery(group.id, reward.id(), playerUuid);
                    }
                }
                default -> STEMCraft.getPlugin().getLogger().warning("Unknown voting reward action '"
                    + reward.action() + "' for " + group.id + ".");
            }
        }
        return true;
    }

    private String formatWinnerMessage(String template, VoteGroup group, ClosedWinner winner, String playerName) {
        return template
            .replace("{group}", group.name)
            .replace("{group-id}", group.id)
            .replace("{player}", playerName)
            .replace("{option}", winner.optionName())
            .replace("{votes}", Integer.toString(winner.votes()));
    }

    private boolean deliveryExists(String groupId, int rewardId, UUID playerUuid) {
        Integer count = api.database().querySingleMapped(
            "SELECT COUNT(*) FROM voting_reward_deliveries WHERE group_id=? AND reward_id=? AND player_uuid=?", ps -> {
                ps.setString(1, groupId); ps.setInt(2, rewardId); ps.setString(3, playerUuid.toString());
            }, rs -> rs.getInt(1));
        return count != null && count > 0;
    }

    private void recordDelivery(String groupId, int rewardId, UUID playerUuid) {
        api.database().update(
            "INSERT OR IGNORE INTO voting_reward_deliveries(group_id,reward_id,player_uuid,delivered_at) VALUES(?,?,?,?)", ps -> {
                ps.setString(1, groupId); ps.setInt(2, rewardId); ps.setString(3, playerUuid.toString());
                ps.setLong(4, System.currentTimeMillis());
            });
    }

    private void clearLifecycleState(String groupId) {
        api.database().update("DELETE FROM voting_reminder_events WHERE group_id=?", ps -> ps.setString(1, groupId));
        api.database().update("DELETE FROM voting_group_closures WHERE group_id=?", ps -> ps.setString(1, groupId));
        api.database().update("DELETE FROM voting_winners WHERE group_id=?", ps -> ps.setString(1, groupId));
        api.database().update("DELETE FROM voting_reward_deliveries WHERE group_id=?", ps -> ps.setString(1, groupId));
    }

    private Long loadPlayerActivity(UUID playerId) {
        return api.database().querySingleMapped(
            "SELECT last_seen_at FROM " + PLAYER_ACTIVITY_TABLE + " WHERE player_uuid=?",
            ps -> ps.setString(1, playerId.toString()),
            rs -> rs.getLong(1)
        );
    }

    private void recordPlayerActivity(UUID playerId, long lastSeenAt) {
        api.database().update(
            "INSERT INTO " + PLAYER_ACTIVITY_TABLE + " (player_uuid,last_seen_at) VALUES (?,?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET last_seen_at=excluded.last_seen_at",
            ps -> {
                ps.setString(1, playerId.toString());
                ps.setLong(2, lastSeenAt);
            }
        );
    }

    private GroupLoginSummary loginSummary(VoteGroup group, UUID playerId, Long previousLastSeenAt) {
        int receivedVotes = previousLastSeenAt == null ? 0 : receivedVotes(group.id, playerId, previousLastSeenAt);
        int totalVotes = 0;
        Integer bestRank = null;
        LinkedHashMap<String, VoteOption> groupOptions = options.getOrDefault(group.id, new LinkedHashMap<>());
        for (VoteOption option : groupOptions.values()) {
            if (!playerId.equals(option.owner)) continue;
            int count = optionVotes(group.id, option.id);
            totalVotes += count;
            if (count <= 0) continue;
            int rank = rank(group.id, option.id, count);
            if (rank <= 3 && (bestRank == null || rank < bestRank)) bestRank = rank;
        }
        return new GroupLoginSummary(receivedVotes, totalVotes, bestRank);
    }

    private int receivedVotes(String groupId, UUID playerId, long previousLastSeenAt) {
        return api.database().querySingleMapped(
            "SELECT COUNT(*) FROM voting_votes vv " +
                "JOIN voting_options vo ON vo.group_id=vv.group_id AND vo.option_id=vv.option_id " +
                "WHERE vv.group_id=? AND vo.owner_uuid=? AND vv.created_at>?",
            ps -> {
                ps.setString(1, groupId);
                ps.setString(2, playerId.toString());
                ps.setLong(3, previousLastSeenAt);
            },
            rs -> rs.getInt(1),
            0
        );
    }

    private int rank(String groupId, String optionId, int optionVoteCount) {
        int higherCount = 0;
        for (VoteOption option : options.getOrDefault(groupId, new LinkedHashMap<>()).values()) {
            if (optionVotes(groupId, option.id) > optionVoteCount) higherCount++;
        }
        return higherCount + 1;
    }

    static String formatLoginVoteSummary(String groupTitle, int receivedVotes, int totalVotes, Integer rank) {
        if (receivedVotes <= 0 && rank == null) return "";
        if (receivedVotes > 0) {
            String message = groupTitle + ": +" + receivedVotes + " " + voteWord(receivedVotes) + ", " + totalVotes + " total";
            return rank == null ? message : message + " — " + ordinal(rank);
        }
        return groupTitle + ": " + ordinal(rank) + " — " + totalVotes + " " + voteWord(totalVotes);
    }

    private static String voteWord(int count) {
        return count == 1 ? "vote" : "votes";
    }

    static String ordinal(int value) {
        int lastTwo = Math.abs(value) % 100;
        String suffix = lastTwo >= 11 && lastTwo <= 13 ? "th" : switch (Math.abs(value) % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return value + suffix;
    }

    private NamedTextColor stateColour(VoteGroup group) { return switch (state(group)) { case "open" -> NamedTextColor.GREEN; case "paused" -> NamedTextColor.YELLOW; default -> NamedTextColor.GRAY; }; }
    private String formatTime(long epochSecond) { return epochSecond == Long.MAX_VALUE ? "none" : Instant.ofEpochSecond(epochSecond)
        .atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T', ' '); }
    private Component button(String text, String command, String hover) { return Component.text(text, NamedTextColor.GOLD)
        .clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(Component.text(hover))); }
    private Component suggestButton(String text, String command, String hover) { return Component.text(text, NamedTextColor.GOLD)
        .clickEvent(ClickEvent.suggestCommand(command)).hoverEvent(HoverEvent.showText(Component.text(hover))); }

    private void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer(); String removalGroup = removals.get(player.getUniqueId());
        if (removalGroup != null) {
            event.setCancelled(true);
            if (player.isSneaking()) { removals.remove(player.getUniqueId()); api.messages().send(player, "/info/Tower removal cancelled."); return; }
            VoteTower selected = towerPartAt(event.getClickedBlock().getLocation(), removalGroup);
            if (selected == null) { api.messages().send(player, "/warn/That block is not part of a tower in this voting group."); return; }
            removeTower(selected.groupId, selected.optionId); removals.remove(player.getUniqueId());
            api.messages().send(player, "/success/Voting tower removed. Use /vote admin option remove " + selected.groupId + " " + selected.optionId + " to also delete its option slot."); return;
        }
        PendingPlacement pending = placements.get(player.getUniqueId());
        if (pending != null) {
            event.setCancelled(true);
            if (player.isSneaking()) { placements.remove(player.getUniqueId()); api.messages().send(player, "/info/Tower placement cancelled."); return; }
            Block clicked = event.getClickedBlock(); Block anchor = clicked.getType() == Material.LECTERN ? clicked : clicked.getRelative(BlockFace.UP);
            BlockFace facing = facing(player).getOppositeFace();
            VoteTower tower = new VoteTower(pending.groupId, pending.optionId, anchor.getWorld().getName(), anchor.getX(), anchor.getY(), anchor.getZ(), facing);
            VoteTower previous = towers.getOrDefault(pending.groupId, new LinkedHashMap<>()).get(pending.optionId);
            if (previous != null) clearExistingTower(previous);
            towers.computeIfAbsent(pending.groupId, key -> new LinkedHashMap<>()).put(pending.optionId, tower);
            saveTower(tower); buildTower(tower); refreshGroup(tower.groupId);
            if (pending.continuous) {
                String next = resolveOption(pending.groupId, "next");
                VoteGroup group = groups.get(pending.groupId);
                if (next == null && group != null && isPlotSource(group)) {
                    VoteOption slot = createPlotSlot(group.id);
                    options.computeIfAbsent(group.id, key -> new LinkedHashMap<>()).put(slot.id, slot); saveOption(group.id, slot);
                    next = slot.id;
                }
                if (next != null) {
                    placements.put(player.getUniqueId(), new PendingPlacement(pending.groupId, next, true));
                    api.messages().send(player, "/success/Voting tower placed. Right-click again to place the next tower, or sneak-right-click to finish.");
                    return;
                }
            }
            placements.remove(player.getUniqueId());
            api.messages().send(player, "/success/Voting tower placed. All configured towers have been placed."); return;
        }
        VoteTower tower = towerAt(event.getClickedBlock().getLocation()); if (tower == null) return;
        event.setCancelled(true); forceLeverOff(event.getClickedBlock()); castOrRetract(player, tower);
        // Apply the player's private lever state after the cancelled vanilla interaction has finished.
        api.tasks().runLater(1L, () -> refreshLevers(player));
    }

    private void castOrRetract(Player player, VoteTower tower) {
        VoteGroup group = groups.get(tower.groupId); VoteOption option = options.getOrDefault(tower.groupId, new LinkedHashMap<>()).get(tower.optionId);
        if (group == null || option == null) return;
        if (isPlotSource(group) && option.owner == null) { api.messages().send(player, "/warn/This voting tower has not been claimed yet."); return; }
        String permission = getConfigSection().getString("permission", "").trim();
        if (!permission.isEmpty() && !player.hasPermission(permission)) { api.messages().send(player, "/error/You cannot vote."); return; }
        if (!state(group).equals("open")) { api.messages().send(player, "/warn/Voting is " + state(group) + "."); return; }
        if (player.getUniqueId().equals(option.owner)) { api.messages().send(player, "/warn/You cannot vote for your own entry."); return; }
        Set<String> selected = votes.computeIfAbsent(group.id, key -> new HashMap<>()).computeIfAbsent(player.getUniqueId(), key -> new HashSet<>());
        if (selected.remove(option.id)) {
            api.database().update("DELETE FROM voting_votes WHERE group_id=? AND voter_uuid=? AND option_id=?", ps -> {
                ps.setString(1, group.id); ps.setString(2, player.getUniqueId().toString()); ps.setString(3, option.id); });
            api.messages().send(player, "/success/Vote removed from " + option.name + ".");
        } else {
            if (selected.size() >= group.votesPerPlayer) { api.messages().send(player, "/warn/You have used all your votes. Turn off one of your votes first."); refreshLever(player, tower); return; }
            selected.add(option.id);
            api.database().update("INSERT OR REPLACE INTO voting_votes(group_id,voter_uuid,option_id,created_at) VALUES(?,?,?,?)", ps -> {
                ps.setString(1, group.id); ps.setString(2, player.getUniqueId().toString()); ps.setString(3, option.id); ps.setLong(4, Instant.now().getEpochSecond()); });
            api.messages().send(player, "/success/Voted for " + option.name + ".");
        }
        refreshGroup(group.id); refreshLevers(player);
    }

    private void onBreak(BlockBreakEvent event) {
        if (towerAt(event.getBlock().getLocation()) != null || isTowerPart(event.getBlock().getLocation())) {
            if (!event.getPlayer().hasPermission(ADMIN_PERMISSION)) event.setCancelled(true);
        }
    }

    private void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        String chunk = event.getTo().getWorld().getName() + ":" + event.getTo().getChunk().getX() + ":" + event.getTo().getChunk().getZ();
        if (!chunk.equals(lastChunks.put(event.getPlayer().getUniqueId(), chunk))) refreshLevers(event.getPlayer());
    }

    private void buildTower(VoteTower tower) {
        if (Bukkit.getWorld(tower.world) == null) return;
        for (TowerElement element : towerTemplate()) for (Location location : elementLocations(tower, element)) {
            Block block = location.getBlock(); String role = placeholderRole(element.block);
            if (role == null) {
                Material decoration = Material.matchMaterial(element.block);
                if (decoration != null && (element.replaceAlways || element.placeIfNonSolid && !block.getType().isSolid()
                    || !element.placeIfNonSolid && block.getType().isAir())) {
                    block.setType(decoration, false);
                    setFacing(block, tower.facing);
                }
                continue;
            }
            block.setType(roleMaterial(role), false); setFacing(block, tower.facing);
            if (role.equals("lever") && block.getBlockData() instanceof Switch data) {
                data.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);
                data.setFacing(tower.facing); data.setPowered(false); block.setBlockData(data, false);
            }
        }
    }

    private void refreshAllDisplays() { for (String group : groups.keySet()) refreshGroup(group); Bukkit.getOnlinePlayers().forEach(this::refreshLevers); }

    private void refreshGroup(String groupId) {
        int total = totalVotes(groupId), maximum = maximumVotes(groupId);
        for (VoteTower tower : towers.getOrDefault(groupId, new LinkedHashMap<>()).values()) {
            VoteOption option = options.getOrDefault(groupId, new LinkedHashMap<>()).get(tower.optionId); if (option == null) continue;
            VoteGroup group = groups.get(groupId);
            boolean vacant = group != null && isPlotSource(group) && option.owner == null;
            int count = optionVotes(groupId, option.id);
            Block signBlock = signBlock(tower);
            if (signBlock.getState() instanceof Sign sign) {
                var side = sign.getSide(org.bukkit.block.sign.Side.FRONT); side.line(0, Component.text(vacant ? "" : option.name));
                side.line(1, Component.text(vacant ? "" : count + (count == 1 ? " vote" : " votes")));
                side.line(2, Component.text(vacant ? "" : total == 0 ? "0%" : Math.round(count * 100D / total) + "%"));
                side.line(3, Component.empty());
                side.setGlowingText(true); sign.setWaxed(true); sign.update(true, false);
            }
            List<Location> lamps = lampLocations(tower);
            int lit = lampCount(count, maximum, lamps.size());
            for (int i = 0; i < lamps.size(); i++) {
                Block block = lamps.get(i).getBlock(); if (!(block.getBlockData() instanceof Lightable data)) continue;
                data.setLit(!vacant && i < lit); block.setBlockData(data, false);
            }
            Block lever = leverBlock(tower);
            if (vacant) lever.setType(Material.AIR, false);
            else {
                if (lever.getType() != roleMaterial("lever")) lever.setType(roleMaterial("lever"), false);
                setFacing(lever, tower.facing);
                if (lever.getBlockData() instanceof Switch data) {
                    data.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);
                    data.setFacing(tower.facing); data.setPowered(false); lever.setBlockData(data, false);
                }
            }
        }
    }

    static int lampCount(int votes, int maximum, int lamps) {
        if (votes <= 0 || lamps <= 0) return 0;
        if (maximum <= lamps) return Math.min(votes, lamps);
        return Math.min(lamps, Math.max(1, (int) Math.ceil(votes * (double) lamps / maximum)));
    }

    private void refreshLevers(Player player) { for (var map : towers.values()) for (VoteTower tower : map.values()) refreshLever(player, tower); }
    private void refreshLever(Player player, VoteTower tower) {
        if (!player.getWorld().getName().equals(tower.world)) return;
        Block block = leverBlock(tower); if (!(block.getBlockData() instanceof Powerable powerable)) return;
        BlockData view = block.getBlockData().clone(); ((Powerable) view).setPowered(votes.getOrDefault(tower.groupId, Map.of())
            .getOrDefault(player.getUniqueId(), Set.of()).contains(tower.optionId));
        player.sendBlockChange(block.getLocation(), view);
    }

    private void forceLeverOff(Block block) { if (block.getBlockData() instanceof Powerable data && data.isPowered()) { data.setPowered(false); block.setBlockData(data, false); } }
    private void clearExistingTower(VoteTower tower) {
        if (local(tower, -2, 2, -1).getBlock().getType() == roleMaterial("lamp")) {
            clearLegacyTower(tower);
            return;
        }
        if (local(tower, 0, 1, 0).getBlock().getType() == roleMaterial("sign")
            || local(tower, 0, 2, -1).getBlock().getType() == roleMaterial("lamp")) {
            clearSparseTower(tower);
            return;
        }
        for (TowerElement element : towerTemplate()) if (!element.placeIfNonSolid) for (Location location : elementLocations(tower, element))
            clearIfTowerMaterial(location.getBlock());
    }

    private boolean removeTower(String groupId, String optionId) {
        VoteTower tower = towers.getOrDefault(groupId, new LinkedHashMap<>()).remove(optionId);
        if (tower == null) return false;
        clearExistingTower(tower);
        api.database().update("DELETE FROM voting_towers WHERE group_id=? AND option_id=?", ps -> { ps.setString(1, groupId); ps.setString(2, optionId); });
        refreshGroup(groupId); return true;
    }

    private int removeAllTowers(String groupId) {
        List<String> optionIds = new ArrayList<>(towers.getOrDefault(groupId, new LinkedHashMap<>()).keySet());
        optionIds.forEach(optionId -> removeTower(groupId, optionId));
        return optionIds.size();
    }

    private void removeOption(String groupId, String optionId) {
        removeTower(groupId, optionId);
        clearOptionVotes(groupId, optionId);
        LinkedHashMap<String, VoteOption> groupOptions = options.get(groupId);
        if (groupOptions != null) groupOptions.remove(optionId);
        api.database().update("DELETE FROM voting_options WHERE group_id=? AND option_id=?", ps -> {
            ps.setString(1, groupId); ps.setString(2, optionId);
        });
        refreshGroup(groupId);
    }
    private void clearLegacyTower(VoteTower tower) {
        clearIfTowerMaterial(local(tower, 0, 0, 0).getBlock());
        clearIfTowerMaterial(local(tower, 0, 0, 1).getBlock());
        clearIfTowerMaterial(local(tower, 0, 1, -1).getBlock());
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 3; y++) clearIfTowerMaterial(local(tower, x, y, -2).getBlock());
        for (int x = -2; x <= 2; x++) for (int y = 2; y <= 3; y++) clearIfTowerMaterial(local(tower, x, y, -1).getBlock());
    }
    private void clearSparseTower(VoteTower tower) {
        clearIfTowerMaterial(local(tower, 0, 0, 0).getBlock());
        clearIfTowerMaterial(local(tower, 0, 0, 1).getBlock());
        clearIfTowerMaterial(local(tower, 0, 1, 0).getBlock());
        for (int y = 2; y <= 11; y++) clearIfTowerMaterial(local(tower, 0, y, -1).getBlock());
    }
    private void clearIfTowerMaterial(Block block) {
        Set<Material> materials = new HashSet<>(Set.of(roleMaterial("lectern"), roleMaterial("lever"), roleMaterial("sign"), roleMaterial("lamp"),
            Material.POLISHED_DEEPSLATE));
        for (TowerElement element : towerTemplate()) { Material literal = Material.matchMaterial(element.block); if (literal != null) materials.add(literal); }
        if (materials.contains(block.getType())) block.setType(Material.AIR, false);
    }
    private int optionVotes(String group, String option) { return (int) votes.getOrDefault(group, Map.of()).values().stream().filter(set -> set.contains(option)).count(); }
    private int totalVotes(String group) { return votes.getOrDefault(group, Map.of()).values().stream().mapToInt(Set::size).sum(); }
    private int resultTotalVotes(String groupId) {
        return options.getOrDefault(groupId, new LinkedHashMap<>()).values().stream()
            .filter(option -> includedInResults(groupId, option))
            .mapToInt(option -> optionVotes(groupId, option.id))
            .sum();
    }
    private int maximumVotes(String group) {
        return options.getOrDefault(group, new LinkedHashMap<>()).values().stream()
            .filter(option -> includedInResults(group, option))
            .mapToInt(option -> optionVotes(group, option.id))
            .max().orElse(0);
    }
    private boolean includedInResults(String groupId, VoteOption option) {
        VoteGroup group = groups.get(groupId);
        return group == null || !isPlotSource(group) || option.owner != null;
    }

    private String state(VoteGroup group) {
        long now = Instant.now().getEpochSecond();
        return now >= group.endsAt ? "finished" : group.paused ? "paused" : now < group.startsAt ? "scheduled" : "open";
    }
    private String resolveOption(String groupId, String value) {
        LinkedHashMap<String, VoteOption> groupOptions = options.getOrDefault(groupId, new LinkedHashMap<>());
        if (value.equalsIgnoreCase("next")) return groupOptions.keySet().stream()
            .filter(id -> !towers.getOrDefault(groupId, new LinkedHashMap<>()).containsKey(id)).findFirst().orElse(null);
        VoteOption exact = groupOptions.get(value.toLowerCase(Locale.ROOT)); if (exact != null) return exact.id;
        return groupOptions.values().stream().filter(option -> option.name.equalsIgnoreCase(value)).map(option -> option.id).findFirst().orElse(null);
    }
    private String resolvePlacedTowerOption(String groupId, String value) {
        LinkedHashMap<String, VoteTower> groupTowers = towers.getOrDefault(groupId, new LinkedHashMap<>());
        String exactId = groupTowers.keySet().stream().filter(id -> id.equalsIgnoreCase(value)).findFirst().orElse(null);
        if (exactId != null) return exactId;
        String optionId = resolveOption(groupId, value);
        return optionId != null && groupTowers.containsKey(optionId) ? optionId : null;
    }
    private VoteGroup requireGroup(CommandContext ctx, int index) { if (ctx.args().size() <= index) { ctx.returnError("Missing voting group."); return null; } VoteGroup group = groups.get(ctx.getArgLower(index)); if (group == null) ctx.returnError("Unknown voting group."); return group; }
    private String cleanId(String value) { return Pattern.compile("[^a-z0-9_-]").matcher(value.toLowerCase(Locale.ROOT)).replaceAll(""); }
    private static UUID parseUuid(String value) { try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; } }
    private static BlockFace parseFace(String value) { try { return horizontal(BlockFace.valueOf(value)); } catch (Exception ignored) { return BlockFace.SOUTH; } }
    private static BlockFace horizontal(BlockFace face) { return switch (face) { case NORTH, EAST, SOUTH, WEST -> face; default -> BlockFace.SOUTH; }; }
    private static BlockFace facing(Player player) { float yaw = Math.floorMod(Math.round(player.getLocation().getYaw() / 90F), 4); return switch ((int) yaw) { case 0 -> BlockFace.SOUTH; case 1 -> BlockFace.WEST; case 2 -> BlockFace.NORTH; default -> BlockFace.EAST; }; }

    static List<String> dateCompletions(String currentArgument) {
        String partial = Objects.requireNonNullElse(currentArgument, "").trim();
        LocalDate today = LocalDate.now();
        if (partial.isEmpty()) return List.of(today.format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (!partial.matches("\\d{1,4}(?:-\\d{0,2}(?:-\\d{0,2})?)?")) return List.of();

        if (!partial.contains("-")) {
            if (partial.length() == 4) return List.of(partial + "-");
            int firstYear = today.getYear();
            return java.util.stream.IntStream.rangeClosed(firstYear, firstYear + 10)
                .mapToObj(year -> String.format(Locale.ROOT, "%04d-", year))
                .filter(value -> value.startsWith(partial))
                .toList();
        }

        String[] parts = partial.split("-", -1);
        int year;
        try { year = Integer.parseInt(parts[0]); }
        catch (NumberFormatException ignored) { return List.of(); }

        if (parts.length == 2) {
            String monthPrefix = parts[1];
            if (monthPrefix.length() == 2) {
                try {
                    int month = Integer.parseInt(monthPrefix);
                    if (month < 1 || month > 12) return List.of();
                    return List.of(partial + "-");
                } catch (NumberFormatException ignored) { return List.of(); }
            }
            return java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(month -> String.format(Locale.ROOT, "%04d-%02d", year, month))
                .filter(value -> value.startsWith(partial))
                .toList();
        }

        if (parts.length != 3) return List.of();
        int month;
        try { month = Integer.parseInt(parts[1]); }
        catch (NumberFormatException ignored) { return List.of(); }
        if (month < 1 || month > 12) return List.of();
        int days = YearMonth.of(year, month).lengthOfMonth();
        String dayPrefix = parts[2];
        if (dayPrefix.length() == 2) {
            try {
                int day = Integer.parseInt(dayPrefix);
                if (day < 1 || day > days) return List.of();
                return List.of();
            } catch (NumberFormatException ignored) { return List.of(); }
        }
        return java.util.stream.IntStream.rangeClosed(1, days)
            .mapToObj(day -> String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day))
            .filter(value -> value.startsWith(partial))
            .toList();
    }

    private long parseTime(String value) {
        if (value.equalsIgnoreCase("now")) return Instant.now().getEpochSecond();
        if (value.equalsIgnoreCase("none")) return Long.MAX_VALUE;
        try { return Instant.parse(value).getEpochSecond(); } catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toEpochSecond(); }
        catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(value.replace('_', 'T')).atZone(ZoneId.systemDefault()).toEpochSecond(); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("Use ISO time such as 2026-09-01, 2026-09-01T18:00, or 'now'."); }
    }

    private void saveGroup(VoteGroup group) { api.database().update("INSERT OR REPLACE INTO voting_groups(id,name,votes_per_player,starts_at,ends_at,paused,source_type,source_value) VALUES(?,?,?,?,?,?,?,?)", ps -> { ps.setString(1, group.id); ps.setString(2, group.name); ps.setInt(3, group.votesPerPlayer); ps.setLong(4, group.startsAt); ps.setLong(5, group.endsAt); ps.setInt(6, group.paused ? 1 : 0); ps.setString(7, group.sourceType); ps.setString(8, group.sourceValue); }); }
    private void saveOption(String group, VoteOption option) { api.database().update("INSERT OR REPLACE INTO voting_options(group_id,option_id,display_name,owner_uuid) VALUES(?,?,?,?)", ps -> { ps.setString(1, group); ps.setString(2, option.id); ps.setString(3, option.name); ps.setString(4, option.owner == null ? null : option.owner.toString()); }); }
    private void saveTower(VoteTower tower) { api.database().update("INSERT OR REPLACE INTO voting_towers(group_id,option_id,world_name,x,y,z,facing) VALUES(?,?,?,?,?,?,?)", ps -> { ps.setString(1, tower.groupId); ps.setString(2, tower.optionId); ps.setString(3, tower.world); ps.setInt(4, tower.x); ps.setInt(5, tower.y); ps.setInt(6, tower.z); ps.setString(7, tower.facing.name()); }); }

    private void refreshPlotSources() {
        for (VoteGroup group : groups.values()) if (isPlotSource(group)) refreshPlotCandidates(group);
    }

    private boolean isPlotSource(VoteGroup group) { return "PLOTSQUARED_PLAYERS".equals(group.sourceType); }

    private int refreshPlotCandidates(VoteGroup group) {
        if (Bukkit.getPluginManager().getPlugin("PlotSquared") == null) return -1;
        try {
            Object api = Class.forName("com.plotsquared.core.PlotAPI").getConstructor().newInstance();
            Collection<?> areas = (Collection<?>) api.getClass().getMethod("getPlotAreas", String.class).invoke(api, group.sourceValue);
            LinkedHashMap<UUID, String> owners = new LinkedHashMap<>();
            for (Object area : areas) {
                Collection<?> plots = (Collection<?>) area.getClass().getMethod("getPlots").invoke(area);
                for (Object plot : plots) {
                    Collection<?> plotOwners = (Collection<?>) plot.getClass().getMethod("getOwners").invoke(plot);
                    for (Object owner : plotOwners) if (owner instanceof UUID uuid) owners.putIfAbsent(uuid, Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse(uuid.toString().substring(0, 8)));
                }
            }
            LinkedHashMap<String, VoteOption> slots = options.computeIfAbsent(group.id, key -> new LinkedHashMap<>());
            Set<UUID> assigned = new HashSet<>();
            for (Map.Entry<String, VoteOption> entry : new ArrayList<>(slots.entrySet())) {
                VoteOption current = entry.getValue();
                String currentName = current.owner == null ? null : owners.get(current.owner);
                if (currentName != null && assigned.add(current.owner)) {
                    if (!current.name.equals(currentName)) { VoteOption updated = new VoteOption(current.id, currentName, current.owner); slots.put(current.id, updated); saveOption(group.id, updated); }
                } else if (current.owner != null) {
                    VoteOption vacant = new VoteOption(current.id, "Unclaimed plot", null);
                    slots.put(current.id, vacant); saveOption(group.id, vacant); clearOptionVotes(group.id, current.id);
                }
            }
            for (Map.Entry<UUID, String> owner : owners.entrySet()) {
                if (assigned.contains(owner.getKey())) continue;
                VoteOption vacant = slots.values().stream().filter(option -> option.owner == null).findFirst().orElse(null);
                if (vacant == null) { vacant = createPlotSlot(group.id); slots.put(vacant.id, vacant); }
                VoteOption claimed = new VoteOption(vacant.id, owner.getValue(), owner.getKey());
                slots.put(claimed.id, claimed); saveOption(group.id, claimed); assigned.add(owner.getKey());
            }
            return owners.size();
        } catch (ReflectiveOperationException | LinkageError ex) { return -1; }
    }

    private VoteOption createPlotSlot(String groupId) {
        Map<String, VoteOption> existing = options.getOrDefault(groupId, new LinkedHashMap<>());
        int number = 1; String id;
        do { id = String.format(Locale.ROOT, "plot-slot-%03d", number++); } while (existing.containsKey(id));
        return new VoteOption(id, "Unclaimed plot", null);
    }

    private void clearOptionVotes(String groupId, String optionId) {
        api.database().update("DELETE FROM voting_votes WHERE group_id=? AND option_id=?", ps -> { ps.setString(1, groupId); ps.setString(2, optionId); });
        Map<UUID, Set<String>> groupVotes = votes.get(groupId);
        if (groupVotes == null) return;
        groupVotes.values().forEach(selected -> selected.remove(optionId));
        groupVotes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private Material material(String path, Material fallback) { Material value = Material.matchMaterial(getConfigSection().getString(path, fallback.name())); return value == null ? fallback : value; }
    private Material roleMaterial(String role) { return switch (role) {
        case "lever" -> material("tower.materials.lever", Material.LEVER);
        case "sign" -> material("tower.materials.sign", Material.OAK_WALL_SIGN);
        case "lamp" -> material("tower.materials.lamp", Material.REDSTONE_LAMP);
        default -> Material.LECTERN;
    }; }
    private String placeholderRole(String value) {
        if (value == null || !value.startsWith("{") || !value.endsWith("}")) return null;
        String role = value.substring(1, value.length() - 1).toLowerCase(Locale.ROOT);
        return Set.of("lever", "sign", "lamp").contains(role) ? role : null;
    }
    private void loadTowerSchema() {
        try {
            towerElements = compileTowerSchema(getConfigSection().getSection("tower.schema", false));
        } catch (IllegalArgumentException ex) {
            STEMCraft.getPlugin().getLogger().severe("Invalid voting tower schema: " + ex.getMessage() + ". Using the built-in tower.");
            towerElements = defaultTowerSchema();
        }
    }
    private List<TowerElement> compileTowerSchema(ConfigSection schema) {
        if (schema == null) throw new IllegalArgumentException("tower.schema is missing");
        Map<Character, Material> blocks = new HashMap<>();
        for (Map.Entry<String, Object> entry : schema.getMap("blocks", false).entrySet()) {
            if (entry.getKey().length() != 1) throw new IllegalArgumentException("block keys must be one character: " + entry.getKey());
            char key = entry.getKey().charAt(0);
            if (Character.isDigit(key) || key == 'G' || key == 'V' || key == '.' || key == ' ')
                throw new IllegalArgumentException("reserved character cannot be configured: " + key);
            Material material = Material.matchMaterial(String.valueOf(entry.getValue()));
            if (material == null || !material.isBlock()) throw new IllegalArgumentException("unknown block for " + key + ": " + entry.getValue());
            blocks.put(key, material);
        }
        Set<Character> conditional = new HashSet<>();
        List<String> conditionalValues = schema.getStringList("place-if-not-solid");
        if (conditionalValues.isEmpty()) conditionalValues = schema.getStringList("only-if-nonblock");
        for (String value : conditionalValues) {
            if (value.length() != 1 || !blocks.containsKey(value.charAt(0)))
                throw new IllegalArgumentException("place-if-not-solid entries must name a configured character: " + value);
            conditional.add(value.charAt(0));
        }
        List<Integer> point = schema.getIntegerList("click-point");
        if (point.size() != 2) {
            String value = schema.getString("click-point", "");
            try { point = Arrays.stream(value.split(",")).map(String::trim).map(Integer::parseInt).toList(); }
            catch (RuntimeException ignored) { point = List.of(); }
        }
        if (point.size() != 2 || point.get(0) < 0 || point.get(1) < 0)
            throw new IllegalArgumentException("click-point must be [column, row-from-bottom]");
        List<String> layout = schema.getStringList("layout");
        if (layout.isEmpty() || point.get(1) >= layout.size()) throw new IllegalArgumentException("layout is empty or click-point is outside it");
        int clickRow = layout.size() - 1 - point.get(1), clickColumn = point.get(0);
        if (clickColumn >= layout.get(clickRow).length() || ". ".indexOf(layout.get(clickRow).charAt(clickColumn)) >= 0)
            throw new IllegalArgumentException("click-point must select a placed block");

        List<TowerElement> result = new ArrayList<>();
        Set<Integer> lamps = new HashSet<>(); int levers = 0, signs = 0;
        for (int row = 0; row < layout.size(); row++) for (int column = 0; column < layout.get(row).length(); column++) {
            char symbol = layout.get(row).charAt(column);
            if (symbol == '.') continue;
            int[] at = {0, layout.size() - 1 - row - point.get(1), clickColumn - column};
            if (symbol == ' ') result.add(new TowerElement(Material.AIR.name(), at, 0, true, false));
            else if (symbol == 'V') { result.add(new TowerElement("{lever}", at, 0, true, false)); levers++; }
            else if (symbol == 'G') { result.add(new TowerElement("{sign}", at, 0, true, false)); signs++; }
            else if (Character.isDigit(symbol)) {
                int order = Character.digit(symbol, 10);
                if (!lamps.add(order)) throw new IllegalArgumentException("lamp " + symbol + " appears more than once");
                result.add(new TowerElement("{lamp}", at, order, true, false));
            } else {
                Material material = blocks.get(symbol);
                if (material == null) throw new IllegalArgumentException("layout uses undefined character: " + symbol);
                boolean placeIfNonSolid = conditional.contains(symbol);
                result.add(new TowerElement(material.name(), at, 0, !placeIfNonSolid, placeIfNonSolid));
            }
        }
        if (levers != 1 || signs != 1) throw new IllegalArgumentException("layout must contain exactly one V and one G");
        if (lamps.isEmpty() || lamps.size() > 10 || !lamps.equals(new HashSet<>(java.util.stream.IntStream.range(0, lamps.size()).boxed().toList())))
            throw new IllegalArgumentException("lamps must be a contiguous sequence starting at 0");
        return List.copyOf(result);
    }
    private List<TowerElement> defaultTowerSchema() {
        List<TowerElement> result = new ArrayList<>();
        result.add(new TowerElement("{lever}", new int[]{0,0,1}, 0, true, false));
        result.add(new TowerElement(Material.LECTERN.name(), new int[]{0,0,0}, 0, true, false));
        result.add(new TowerElement(Material.STONE.name(), new int[]{0,0,-2}, 0, false, true));
        result.add(new TowerElement("{sign}", new int[]{0,1,-1}, 0, true, false));
        result.add(new TowerElement(Material.STONE.name(), new int[]{0,1,-2}, 0, false, true));
        for (int lamp = 0; lamp < 10; lamp++) result.add(new TowerElement("{lamp}", new int[]{0,lamp + 2,-2}, lamp, true, false));
        return List.copyOf(result);
    }
    private List<TowerElement> towerTemplate() { return towerElements; }
    private List<Location> elementLocations(VoteTower tower, TowerElement element) {
        List<Location> result = new ArrayList<>();
        result.add(local(tower, element.at[0], element.at[1], element.at[2]));
        return result;
    }
    private List<Location> roleLocations(VoteTower tower, String role) {
        List<Location> result = new ArrayList<>();
        towerTemplate().stream().filter(element -> role.equals(placeholderRole(element.block)))
            .sorted(java.util.Comparator.comparingInt(TowerElement::order)).forEach(element -> result.addAll(elementLocations(tower, element)));
        if (!result.isEmpty()) return result;
        TowerElement fallback = switch (role) {
            case "lever" -> new TowerElement("{lever}", new int[]{0,0,1}, 0, true, false);
            case "sign" -> new TowerElement("{sign}", new int[]{0,1,0}, 0, true, false);
            default -> new TowerElement("{lamp}", new int[]{0,2,-1}, 0, true, false);
        };
        return elementLocations(tower, fallback);
    }
    private Location local(VoteTower tower, int right, int up, int forward) { World world = Bukkit.getWorld(tower.world); BlockFace r = switch (tower.facing) { case NORTH -> BlockFace.EAST; case EAST -> BlockFace.SOUTH; case SOUTH -> BlockFace.WEST; default -> BlockFace.NORTH; }; return new Location(world, tower.x + r.getModX()*right + tower.facing.getModX()*forward, tower.y + up, tower.z + r.getModZ()*right + tower.facing.getModZ()*forward); }
    private Block leverBlock(VoteTower tower) { return roleLocations(tower, "lever").getFirst().getBlock(); }
    private Block signBlock(VoteTower tower) { return roleLocations(tower, "sign").getFirst().getBlock(); }
    private List<Location> lampLocations(VoteTower tower) { return roleLocations(tower, "lamp"); }
    private void setFacing(Block block, BlockFace face) { if (block.getBlockData() instanceof Directional data && data.getFaces().contains(face)) { data.setFacing(face); block.setBlockData(data, false); } }
    private VoteTower towerAt(Location location) { for (var map:towers.values()) for(VoteTower tower:map.values()) if(same(leverBlock(tower).getLocation(),location)) return tower; return null; }
    private VoteTower towerPartAt(Location location, String groupId) {
        for (VoteTower tower : towers.getOrDefault(groupId, new LinkedHashMap<>()).values())
            for (TowerElement element : towerTemplate())
                if (elementLocations(tower, element).stream().anyMatch(part -> same(part, location))) return tower;
        return null;
    }
    private boolean isTowerPart(Location location) { for(var map:towers.values())for(VoteTower tower:map.values())for(TowerElement element:towerTemplate())if(elementLocations(tower,element).stream().anyMatch(l->same(l,location)))return true;return false; }
    private boolean same(Location a, Location b) { return a.getWorld()!=null&&b.getWorld()!=null&&a.getWorld().equals(b.getWorld())&&a.getBlockX()==b.getBlockX()&&a.getBlockY()==b.getBlockY()&&a.getBlockZ()==b.getBlockZ(); }

    private static final class VoteGroup { final String id; String name; final int votesPerPlayer; long startsAt,endsAt; boolean paused; String sourceType,sourceValue; VoteGroup(String id,String name,int votes,long start,long end,boolean paused,String source,String value){this.id=id;this.name=name;this.votesPerPlayer=votes;this.startsAt=start;this.endsAt=end;this.paused=paused;this.sourceType=source;this.sourceValue=value;} }
    private record GroupLoginSummary(int receivedVotes, int totalVotes, Integer bestRank) {}
    private record VoteTallyRow(String optionId, String optionName, UUID owner, int votes) {}
    private record VoteReminder(int daysRemaining, String message) {}
    private record VoteReward(int id, String action, String value, long durationSeconds) {}
    private record ClosureStatus(long closedAt, Long processedAt) {}
    private record ClosedWinner(String optionId, String optionName, UUID playerUuid, int votes) {}
    private record VoteOption(String id,String name,UUID owner) {}
    private record VoteTower(String groupId,String optionId,String world,int x,int y,int z,BlockFace facing) {}
    private record PendingPlacement(String groupId,String optionId,boolean continuous) {}
    private record TowerElement(String block, int[] at, int order, boolean replaceAlways, boolean placeIfNonSolid) {}
}

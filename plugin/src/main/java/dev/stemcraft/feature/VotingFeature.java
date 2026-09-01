package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
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
import java.util.*;
import java.util.regex.Pattern;

/** Reusable physical voting groups with personalized client-side levers. */
public final class VotingFeature extends BaseFeature {
    private static final String ADMIN_PERMISSION = "stemcraft.command.vote.admin";
    private final Map<String, VoteGroup> groups = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<String, VoteOption>> options = new HashMap<>();
    private final Map<String, LinkedHashMap<String, VoteTower>> towers = new HashMap<>();
    private final Map<String, Map<UUID, Set<String>>> votes = new HashMap<>();
    private final Map<UUID, PendingPlacement> placements = new HashMap<>();
    private final Map<UUID, String> lastChunks = new HashMap<>();
    private List<TowerElement> towerElements = List.of();

    public VotingFeature(STEMCraftAPI api) { super(api); }

    @Override public void onEnable() {
        loadTowerSchema(); ensureStorage(); loadAll(); registerCommand();
        api.events().register(PlayerInteractEvent.class, this::onInteract, EventPriority.HIGHEST, false);
        api.events().register(BlockBreakEvent.class, this::onBreak, EventPriority.HIGHEST, true);
        api.events().register(PlayerJoinEvent.class, event -> api.tasks().runLater(10L, () -> refreshLevers(event.getPlayer())));
        api.events().register(PlayerChangedWorldEvent.class, event -> api.tasks().runLater(1L, () -> refreshLevers(event.getPlayer())));
        api.events().register(PlayerMoveEvent.class, this::onMove, EventPriority.MONITOR, true);
        api.tasks().repeating("feature:voting-displays", 100L, this::refreshAllDisplays);
        refreshAllDisplays();
    }

    @Override public void onDisable() { api.tasks().cancel("feature:voting-displays"); placements.clear(); lastChunks.clear(); }

    @Override public void onReload() { super.onReload(); loadTowerSchema(); refreshAllDisplays(); }

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
    }

    private void loadAll() {
        groups.clear(); options.clear(); towers.clear(); votes.clear();
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
    }

    private void registerCommand() {
        api.tabComplete().register("vote-group", (player, args) -> new ArrayList<>(groups.keySet()));
        api.tabComplete().register("vote-option", (player, args) -> {
            if (args.length == 0) return List.of();
            LinkedHashMap<String, VoteOption> groupOptions = options.get(args[0].toLowerCase(Locale.ROOT));
            if (groupOptions == null) return List.of();
            List<String> result = new ArrayList<>(groupOptions.keySet()); result.add("next"); return result;
        });
        api.commands().create("vote").description("Physical voting groups.")
            .usage("/vote [status] | /vote admin ...")
            .tabCompletion("status").tabCompletion("admin", "create")
            .tabCompletion("admin", "list")
            .tabCompletion("admin", "view", "{vote-group}")
            .tabCompletion("admin", "option", "add", "{vote-group}", "", "")
            .tabCompletion("admin", "option", "list", "{vote-group}")
            .tabCompletion("admin", "source", "plots", "{vote-group}", "{world}")
            .tabCompletion("admin", "schedule", "{vote-group}", "now", "")
            .tabCompletion("admin", "tower", "place", "{vote-group}", "{vote-option:$3}")
            .tabCompletion("admin", "pause", "{vote-group}")
            .tabCompletion("admin", "resume", "{vote-group}")
            .tabCompletion("admin", "reset", "{vote-group}")
            .tabCompletion("admin", "reset-player", "{vote-group}", "{player}")
            .executor((unused, command, ctx) -> execute(ctx)).register(STEMCraft.getPlugin());
    }

    private void execute(CommandContext ctx) {
        if (ctx.args().isEmpty() || ctx.getArgLower(0).equals("status")) { showStatus(ctx); return; }
        if (!ctx.getArgLower(0).equals("admin")) { ctx.returnError("Use /vote status or /vote admin."); return; }
        if (!ctx.hasPermission(ADMIN_PERMISSION)) { ctx.returnError("You cannot administer voting groups."); return; }
        if (ctx.args().size() < 2 || ctx.getArg(1).matches("\\d+")) { showAdminMenu(ctx); return; }
        switch (ctx.getArgLower(1)) {
            case "create" -> createGroup(ctx);
            case "option" -> optionCommand(ctx);
            case "source" -> sourceCommand(ctx);
            case "schedule" -> schedule(ctx);
            case "tower" -> towerCommand(ctx);
            case "pause" -> setPaused(ctx, true);
            case "resume" -> setPaused(ctx, false);
            case "reset" -> resetGroup(ctx);
            case "reset-player" -> resetPlayer(ctx);
            case "list" -> listGroups(ctx);
            case "view" -> showGroupMenu(ctx);
            default -> adminHelp(ctx);
        }
    }

    private void adminHelp(CommandContext ctx) { ctx.returnInfo("Use /vote admin <create|option|source|schedule|tower|pause|resume|reset|reset-player|list>."); }

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

    private void optionCommand(CommandContext ctx) {
        if (ctx.args().size() >= 4 && ctx.getArgLower(2).equals("list")) {
            VoteGroup group = requireGroup(ctx, 3); if (group == null) return;
            for (VoteOption option : options.getOrDefault(group.id, new LinkedHashMap<>()).values())
                ctx.info(option.id + " — " + option.name);
            return;
        }
        if (ctx.args().size() < 6 || !ctx.getArgLower(2).equals("add")) {
            ctx.returnError("Usage: /vote admin option <add <group> <option-id> <name>|list <group>>"); return;
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
        saveGroup(group); refreshGroup(group.id); ctx.returnSuccess("Voting schedule updated for " + group.name + ".");
    }

    private void towerCommand(CommandContext ctx) {
        if (ctx.args().size() < 5 || !ctx.getArgLower(2).equals("place")) {
            ctx.returnError("Usage: /vote admin tower place <group> <option>"); return;
        }
        ctx.checkNotConsole(); VoteGroup group = requireGroup(ctx, 3); if (group == null) return;
        String optionId = resolveOption(group.id, ctx.getArg(4));
        if (optionId == null) { ctx.returnError("Unknown vote option. Use its ID, name, or 'next'."); return; }
        placements.put(ctx.asPlayer().getUniqueId(), new PendingPlacement(group.id, optionId));
        ctx.returnSuccess("Right-click the block where the tower lectern belongs. Sneak-right-click to cancel.");
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
                            : button("[Place]", "/vote admin tower place " + group.id + " " + option.id, "Place this option's tower"));
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
        ctx.getSender().sendMessage(Component.text("Static option syntax: ", NamedTextColor.DARK_GRAY)
            .append(Component.text("<option-id> <display name>", NamedTextColor.GRAY)));
        String toggleCommand = "/vote admin " + (group.paused ? "resume " : "pause ") + group.id;
        ctx.getSender().sendMessage(button(group.paused ? "[Resume]" : "[Pause]", toggleCommand, group.paused ? "Resume voting" : "Pause voting")
            .append(Component.space()).append(suggestButton("[Schedule]", "/vote admin schedule " + group.id + " now ", "Set start and finish"))
            .append(Component.space()).append(suggestButton("[＋ Static option]", "/vote admin option add " + group.id + " ", "Enter <option-id> <display name>; for example fishing Fishing Expansion"))
            .append(Component.space()).append(suggestButton("[Plot source]", "/vote admin source plots " + group.id + " ", "Load plot creators")));
        ctx.getSender().sendMessage(button("[Place next]", "/vote admin tower place " + group.id + " next", "Place the next unplaced tower")
            .append(Component.space()).append(suggestButton("[Reset votes]", "/vote admin reset " + group.id, "Review before sending")));
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
        Player player = event.getPlayer(); PendingPlacement pending = placements.get(player.getUniqueId());
        if (pending != null) {
            event.setCancelled(true);
            if (player.isSneaking()) { placements.remove(player.getUniqueId()); api.messages().send(player, "/info/Tower placement cancelled."); return; }
            Block clicked = event.getClickedBlock(); Block anchor = clicked.getType() == Material.LECTERN ? clicked : clicked.getRelative(BlockFace.UP);
            BlockFace facing = facing(player).getOppositeFace();
            VoteTower tower = new VoteTower(pending.groupId, pending.optionId, anchor.getWorld().getName(), anchor.getX(), anchor.getY(), anchor.getZ(), facing);
            VoteTower previous = towers.getOrDefault(pending.groupId, new LinkedHashMap<>()).get(pending.optionId);
            if (previous != null) clearExistingTower(previous);
            towers.computeIfAbsent(pending.groupId, key -> new LinkedHashMap<>()).put(pending.optionId, tower);
            saveTower(tower); placements.remove(player.getUniqueId()); buildTower(tower); refreshGroup(tower.groupId);
            api.messages().send(player, "/success/Voting tower placed."); return;
        }
        VoteTower tower = towerAt(event.getClickedBlock().getLocation()); if (tower == null) return;
        event.setCancelled(true); forceLeverOff(event.getClickedBlock()); castOrRetract(player, tower);
        // Apply the player's private lever state after the cancelled vanilla interaction has finished.
        api.tasks().runLater(1L, () -> refreshLevers(player));
    }

    private void castOrRetract(Player player, VoteTower tower) {
        VoteGroup group = groups.get(tower.groupId); VoteOption option = options.getOrDefault(tower.groupId, new LinkedHashMap<>()).get(tower.optionId);
        if (group == null || option == null) return;
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
                    || !element.placeIfNonSolid && block.getType().isAir())) block.setType(decoration, false);
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
            int count = optionVotes(groupId, option.id);
            Block signBlock = signBlock(tower);
            if (signBlock.getState() instanceof Sign sign) {
                var side = sign.getSide(org.bukkit.block.sign.Side.FRONT); side.line(0, Component.text(option.name));
                side.line(1, Component.text(count + (count == 1 ? " vote" : " votes")));
                side.line(2, Component.text(total == 0 ? "0%" : Math.round(count * 100D / total) + "%"));
                side.setGlowingText(true); sign.setWaxed(true); sign.update(true, false);
            }
            List<Location> lamps = lampLocations(tower);
            int lit = lampCount(count, maximum, lamps.size());
            for (int i = 0; i < lamps.size(); i++) {
                Block block = lamps.get(i).getBlock(); if (!(block.getBlockData() instanceof Lightable data)) continue;
                data.setLit(i < lit); block.setBlockData(data, false);
            }
            forceLeverOff(leverBlock(tower));
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
    private int maximumVotes(String group) { return options.getOrDefault(group, new LinkedHashMap<>()).keySet().stream().mapToInt(id -> optionVotes(group, id)).max().orElse(0); }

    private String state(VoteGroup group) { long now = Instant.now().getEpochSecond(); return group.paused ? "paused" : now < group.startsAt ? "scheduled" : now >= group.endsAt ? "finished" : "open"; }
    private String resolveOption(String groupId, String value) {
        LinkedHashMap<String, VoteOption> groupOptions = options.getOrDefault(groupId, new LinkedHashMap<>());
        if (value.equalsIgnoreCase("next")) return groupOptions.keySet().stream()
            .filter(id -> !towers.getOrDefault(groupId, new LinkedHashMap<>()).containsKey(id)).findFirst().orElse(null);
        VoteOption exact = groupOptions.get(value.toLowerCase(Locale.ROOT)); if (exact != null) return exact.id;
        return groupOptions.values().stream().filter(option -> option.name.equalsIgnoreCase(value)).map(option -> option.id).findFirst().orElse(null);
    }
    private VoteGroup requireGroup(CommandContext ctx, int index) { if (ctx.args().size() <= index) { ctx.returnError("Missing voting group."); return null; } VoteGroup group = groups.get(ctx.getArgLower(index)); if (group == null) ctx.returnError("Unknown voting group."); return group; }
    private String cleanId(String value) { return Pattern.compile("[^a-z0-9_-]").matcher(value.toLowerCase(Locale.ROOT)).replaceAll(""); }
    private static UUID parseUuid(String value) { try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; } }
    private static BlockFace parseFace(String value) { try { return horizontal(BlockFace.valueOf(value)); } catch (Exception ignored) { return BlockFace.SOUTH; } }
    private static BlockFace horizontal(BlockFace face) { return switch (face) { case NORTH, EAST, SOUTH, WEST -> face; default -> BlockFace.SOUTH; }; }
    private static BlockFace facing(Player player) { float yaw = Math.floorMod(Math.round(player.getLocation().getYaw() / 90F), 4); return switch ((int) yaw) { case 0 -> BlockFace.SOUTH; case 1 -> BlockFace.WEST; case 2 -> BlockFace.NORTH; default -> BlockFace.EAST; }; }

    private long parseTime(String value) { if (value.equalsIgnoreCase("now")) return Instant.now().getEpochSecond(); if (value.equalsIgnoreCase("none")) return Long.MAX_VALUE; try { return Instant.parse(value).getEpochSecond(); } catch (DateTimeParseException ignored) { } try { return LocalDateTime.parse(value.replace('_', 'T')).atZone(ZoneId.systemDefault()).toEpochSecond(); } catch (DateTimeParseException ex) { throw new IllegalArgumentException("Use ISO time such as 2026-09-01T18:00 or 'now'."); } }

    private void saveGroup(VoteGroup group) { api.database().update("INSERT OR REPLACE INTO voting_groups(id,name,votes_per_player,starts_at,ends_at,paused,source_type,source_value) VALUES(?,?,?,?,?,?,?,?)", ps -> { ps.setString(1, group.id); ps.setString(2, group.name); ps.setInt(3, group.votesPerPlayer); ps.setLong(4, group.startsAt); ps.setLong(5, group.endsAt); ps.setInt(6, group.paused ? 1 : 0); ps.setString(7, group.sourceType); ps.setString(8, group.sourceValue); }); }
    private void saveOption(String group, VoteOption option) { api.database().update("INSERT OR REPLACE INTO voting_options(group_id,option_id,display_name,owner_uuid) VALUES(?,?,?,?)", ps -> { ps.setString(1, group); ps.setString(2, option.id); ps.setString(3, option.name); ps.setString(4, option.owner == null ? null : option.owner.toString()); }); }
    private void saveTower(VoteTower tower) { api.database().update("INSERT OR REPLACE INTO voting_towers(group_id,option_id,world_name,x,y,z,facing) VALUES(?,?,?,?,?,?,?)", ps -> { ps.setString(1, tower.groupId); ps.setString(2, tower.optionId); ps.setString(3, tower.world); ps.setInt(4, tower.x); ps.setInt(5, tower.y); ps.setInt(6, tower.z); ps.setString(7, tower.facing.name()); }); }

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
            for (Map.Entry<UUID, String> owner : owners.entrySet()) { VoteOption option = new VoteOption(owner.getKey().toString(), owner.getValue(), owner.getKey()); options.computeIfAbsent(group.id, key -> new LinkedHashMap<>()).put(option.id, option); saveOption(group.id, option); }
            return owners.size();
        } catch (ReflectiveOperationException | LinkageError ex) { return -1; }
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
    private boolean isTowerPart(Location location) { for(var map:towers.values())for(VoteTower tower:map.values())for(TowerElement element:towerTemplate())if(elementLocations(tower,element).stream().anyMatch(l->same(l,location)))return true;return false; }
    private boolean same(Location a, Location b) { return a.getWorld()!=null&&b.getWorld()!=null&&a.getWorld().equals(b.getWorld())&&a.getBlockX()==b.getBlockX()&&a.getBlockY()==b.getBlockY()&&a.getBlockZ()==b.getBlockZ(); }

    private static final class VoteGroup { final String id,name; final int votesPerPlayer; long startsAt,endsAt; boolean paused; String sourceType,sourceValue; VoteGroup(String id,String name,int votes,long start,long end,boolean paused,String source,String value){this.id=id;this.name=name;this.votesPerPlayer=votes;this.startsAt=start;this.endsAt=end;this.paused=paused;this.sourceType=source;this.sourceValue=value;} }
    private record VoteOption(String id,String name,UUID owner) {}
    private record VoteTower(String groupId,String optionId,String world,int x,int y,int z,BlockFace facing) {}
    private record PendingPlacement(String groupId,String optionId) {}
    private record TowerElement(String block, int[] at, int order, boolean replaceAlways, boolean placeIfNonSolid) {}
}

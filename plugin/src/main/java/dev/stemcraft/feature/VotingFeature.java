package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import net.kyori.adventure.text.Component;
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

    public VotingFeature(STEMCraftAPI api) { super(api); }

    @Override public void onEnable() {
        ensureStorage(); loadAll(); registerCommand();
        api.events().register(PlayerInteractEvent.class, this::onInteract, EventPriority.HIGHEST, false);
        api.events().register(BlockBreakEvent.class, this::onBreak, EventPriority.HIGHEST, true);
        api.events().register(PlayerJoinEvent.class, event -> api.tasks().runLater(10L, () -> refreshLevers(event.getPlayer())));
        api.events().register(PlayerChangedWorldEvent.class, event -> api.tasks().runLater(1L, () -> refreshLevers(event.getPlayer())));
        api.events().register(PlayerMoveEvent.class, this::onMove, EventPriority.MONITOR, true);
        api.tasks().repeating("feature:voting-displays", 100L, this::refreshAllDisplays);
        refreshAllDisplays();
    }

    @Override public void onDisable() { api.tasks().cancel("feature:voting-displays"); placements.clear(); lastChunks.clear(); }

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
        api.commands().create("vote").description("Physical voting groups.")
            .usage("/vote [status] | /vote admin ...")
            .tabCompletion("status").tabCompletion("admin")
            .executor((unused, command, ctx) -> execute(ctx)).register(STEMCraft.getPlugin());
    }

    private void execute(CommandContext ctx) {
        if (ctx.args().isEmpty() || ctx.getArgLower(0).equals("status")) { showStatus(ctx); return; }
        if (!ctx.getArgLower(0).equals("admin")) { ctx.returnError("Use /vote status or /vote admin."); return; }
        if (!ctx.hasPermission(ADMIN_PERMISSION)) { ctx.returnError("You cannot administer voting groups."); return; }
        if (ctx.args().size() < 2) { adminHelp(ctx); return; }
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
        if (groups.isEmpty()) { ctx.returnInfo("No voting groups exist."); return; }
        for (VoteGroup group : groups.values()) ctx.info(group.id + " — " + group.name + " — " + state(group));
    }

    private void showStatus(CommandContext ctx) {
        if (groups.isEmpty()) { ctx.returnInfo("No voting groups exist."); return; }
        UUID uuid = ctx.getSender() instanceof Player player ? player.getUniqueId() : null;
        for (VoteGroup group : groups.values()) {
            int used = uuid == null ? 0 : votes.getOrDefault(group.id, Map.of()).getOrDefault(uuid, Set.of()).size();
            ctx.info(group.name + ": " + state(group) + (uuid == null ? "" : " — " + used + "/" + group.votesPerPlayer + " votes used"));
        }
    }

    private void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer(); PendingPlacement pending = placements.get(player.getUniqueId());
        if (pending != null) {
            event.setCancelled(true);
            if (player.isSneaking()) { placements.remove(player.getUniqueId()); api.messages().send(player, "/info/Tower placement cancelled."); return; }
            Block clicked = event.getClickedBlock(); Block anchor = clicked.getType() == Material.LECTERN ? clicked : clicked.getRelative(BlockFace.UP);
            BlockFace facing = clicked.getBlockData() instanceof Directional directional ? horizontal(directional.getFacing()) : facing(player);
            VoteTower tower = new VoteTower(pending.groupId, pending.optionId, anchor.getWorld().getName(), anchor.getX(), anchor.getY(), anchor.getZ(), facing);
            towers.computeIfAbsent(pending.groupId, key -> new LinkedHashMap<>()).put(pending.optionId, tower);
            saveTower(tower); placements.remove(player.getUniqueId()); buildTower(tower); refreshGroup(tower.groupId);
            api.messages().send(player, "/success/Voting tower placed."); return;
        }
        VoteTower tower = towerAt(event.getClickedBlock().getLocation()); if (tower == null) return;
        event.setCancelled(true); forceLeverOff(event.getClickedBlock()); castOrRetract(player, tower);
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
            api.messages().send(player, "/success/Vote retracted from " + option.name + ".");
        } else {
            if (selected.size() >= group.votesPerPlayer) { api.messages().send(player, "/warn/You have used all " + group.votesPerPlayer + " votes. Retract one first."); refreshLever(player, tower); return; }
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
        World world = Bukkit.getWorld(tower.world); if (world == null) return;
        Material wall = material("tower.wall", Material.POLISHED_DEEPSLATE), lectern = material("tower.lectern", Material.LECTERN);
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 3; y++) local(tower, x, y, getConfigSection().getInt("tower.wall-depth", -2)).getBlock().setType(wall, false);
        Block anchor = world.getBlockAt(tower.x, tower.y, tower.z); anchor.setType(lectern, false); setFacing(anchor, tower.facing);
        Block lever = leverBlock(tower); lever.setType(material("tower.lever", Material.LEVER), false);
        if (lever.getBlockData() instanceof Switch data) { data.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR); data.setFacing(tower.facing); data.setPowered(false); lever.setBlockData(data, false); }
        Block sign = signBlock(tower); sign.setType(material("tower.sign", Material.OAK_WALL_SIGN), false); setFacing(sign, tower.facing);
        for (Location location : lampLocations(tower)) location.getBlock().setType(material("tower.lamp", Material.REDSTONE_LAMP), false);
    }

    private void refreshAllDisplays() { for (String group : groups.keySet()) refreshGroup(group); Bukkit.getOnlinePlayers().forEach(this::refreshLevers); }

    private void refreshGroup(String groupId) {
        int total = totalVotes(groupId), maximum = maximumVotes(groupId);
        for (VoteTower tower : towers.getOrDefault(groupId, new LinkedHashMap<>()).values()) {
            VoteOption option = options.getOrDefault(groupId, new LinkedHashMap<>()).get(tower.optionId); if (option == null) continue;
            int count = optionVotes(groupId, option.id), lit = lampCount(count, maximum, 10);
            Block signBlock = signBlock(tower);
            if (signBlock.getState() instanceof Sign sign) {
                var side = sign.getSide(org.bukkit.block.sign.Side.FRONT); side.line(0, Component.text(option.name));
                side.line(1, Component.text(count + (count == 1 ? " vote" : " votes")));
                side.line(2, Component.text(total == 0 ? "0%" : Math.round(count * 100D / total) + "%"));
                side.setGlowingText(true); sign.setWaxed(true); sign.update(true, false);
            }
            List<Location> lamps = lampLocations(tower);
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
    private int[] offset(String path, int x, int y, int z) { List<Integer> list = getConfigSection().getIntegerList(path); return list.size() == 3 ? new int[]{list.get(0), list.get(1), list.get(2)} : new int[]{x,y,z}; }
    private Location local(VoteTower tower, int right, int up, int forward) { World world = Bukkit.getWorld(tower.world); BlockFace r = switch (tower.facing) { case NORTH -> BlockFace.EAST; case EAST -> BlockFace.SOUTH; case SOUTH -> BlockFace.WEST; default -> BlockFace.NORTH; }; return new Location(world, tower.x + r.getModX()*right + tower.facing.getModX()*forward, tower.y + up, tower.z + r.getModZ()*right + tower.facing.getModZ()*forward); }
    private Block leverBlock(VoteTower tower) { int[] o = offset("tower.lever-offset",0,0,1); return local(tower,o[0],o[1],o[2]).getBlock(); }
    private Block signBlock(VoteTower tower) { int[] o = offset("tower.sign-offset",0,1,-1); return local(tower,o[0],o[1],o[2]).getBlock(); }
    private List<Location> lampLocations(VoteTower tower) { int[] o=offset("tower.lamp-origin",-2,2,-1); int columns=Math.max(1,getConfigSection().getInt("tower.lamp-columns",5)), rows=Math.max(1,getConfigSection().getInt("tower.lamp-rows",2)); List<Location> result=new ArrayList<>(); for(int y=0;y<rows;y++)for(int x=0;x<columns;x++)result.add(local(tower,o[0]+x,o[1]+y,o[2])); return result; }
    private void setFacing(Block block, BlockFace face) { if (block.getBlockData() instanceof Directional data && data.getFaces().contains(face)) { data.setFacing(face); block.setBlockData(data, false); } }
    private VoteTower towerAt(Location location) { for (var map:towers.values()) for(VoteTower tower:map.values()) if(same(leverBlock(tower).getLocation(),location)) return tower; return null; }
    private boolean isTowerPart(Location location) { for(var map:towers.values())for(VoteTower tower:map.values()){ if(same(signBlock(tower).getLocation(),location)||same(local(tower,0,0,0),location)||lampLocations(tower).stream().anyMatch(l->same(l,location)))return true;}return false; }
    private boolean same(Location a, Location b) { return a.getWorld()!=null&&b.getWorld()!=null&&a.getWorld().equals(b.getWorld())&&a.getBlockX()==b.getBlockX()&&a.getBlockY()==b.getBlockY()&&a.getBlockZ()==b.getBlockZ(); }

    private static final class VoteGroup { final String id,name; final int votesPerPlayer; long startsAt,endsAt; boolean paused; String sourceType,sourceValue; VoteGroup(String id,String name,int votes,long start,long end,boolean paused,String source,String value){this.id=id;this.name=name;this.votesPerPlayer=votes;this.startsAt=start;this.endsAt=end;this.paused=paused;this.sourceType=source;this.sourceValue=value;} }
    private record VoteOption(String id,String name,UUID owner) {}
    private record VoteTower(String groupId,String optionId,String world,int x,int y,int z,BlockFace facing) {}
    private record PendingPlacement(String groupId,String optionId) {}
}

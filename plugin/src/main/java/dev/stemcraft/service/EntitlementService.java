package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.player.PlayerService.ResolvedPlayer;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.service.playerreset.*;
import dev.stemcraft.api.util.PermissionUtil;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Central source of truth for calculated/manual entitlements and visible badges. */
public final class EntitlementService extends BaseService {
    private static final String ADMIN_PERMISSION = "stemcraft.entitlements.admin";
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9_.-]*$");
    private static final Pattern BADGE_PLACEHOLDER = Pattern.compile("\\{badge(?:-([1-9][0-9]*))?}");
    private static final Pattern DURATION = Pattern.compile("^([1-9][0-9]*)([dhw])$", Pattern.CASE_INSENSITIVE);
    private static final long HOURLY_TICKS = 20L * 60L * 60L;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, EntitlementDefinition> entitlements = new LinkedHashMap<>();
    private final Map<String, BadgeDefinition> badges = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> applied = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> directBadges = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> manualEntitlements = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> revokedEntitlements = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> revokedBadges = new ConcurrentHashMap<>();
    private String badgePrefix = " ";
    private String badgeSeparator = "";
    private String badgeSuffix = "";

    public EntitlementService(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "entitlements");
    }

    @Override
    public void onEnable() {
        createTables();
        loadConfig();
        loadApplied();
        registerCommands();
        api.events().register(PlayerJoinEvent.class, event -> recalculate(event.getPlayer().getUniqueId()));
        api.tasks().repeating("entitlements:rolling-recalculation", HOURLY_TICKS, HOURLY_TICKS,
            () -> knownPlayers().forEach(this::recalculate));
        Bukkit.getOnlinePlayers().forEach(player -> recalculate(player.getUniqueId()));
        api.playerResets().register(new PlayerResetHandler() {
            public @NotNull String id() { return "entitlements-and-badges"; }
            public @NotNull Set<PlayerResetScope> scopes() { return Set.of(PlayerResetScope.PROGRESSION, PlayerResetScope.GAMEPLAY, PlayerResetScope.COMPLETE); }
            public int priority() { return 110; }
            public @NotNull PlayerResetPreview preview(@NotNull PlayerResetContext context) {
                int count = applied.getOrDefault(context.playerUuid(), Set.of()).size() + directBadges.getOrDefault(context.playerUuid(), Set.of()).size();
                return new PlayerResetPreview("Entitlements, badges and reset suppressions", count);
            }
            public void reset(@NotNull PlayerResetContext context) {
                resetAll(context.playerUuid());
            }
        });
    }

    @Override
    public void onReload() {
        super.onReload();
        loadConfig();
        knownPlayers().forEach(this::recalculate);
    }

    @Override
    public void onDisable() {
        api.tasks().cancel("entitlements:rolling-recalculation");
    }

    /** Called by fact-producing services after a player's stats change. */
    public void onFactsChanged(@NotNull UUID uuid) {
        recalculate(uuid);
    }

    /** Records the common played/won facts shared by all completed minigames. */
    public void recordMinigameResult(@NotNull String minigame, @NotNull Collection<Player> participants,
                                     @NotNull Collection<UUID> winners) {
        String normalized = normalize(minigame);
        String playedKey = "minigame_" + normalized + "_games_played";
        String winsKey = "minigame_" + normalized + "_wins";
        if (api.playerStats().getDefinition(playedKey) == null) api.playerStats().register(new PlayerStatDefinition(
            playedKey, normalized + " Games Played", "Completed " + normalized + " games.", normalized, "minigame", normalized));
        if (api.playerStats().getDefinition(winsKey) == null) api.playerStats().register(new PlayerStatDefinition(
            winsKey, normalized + " Games Won", "Won " + normalized + " games.", normalized, "minigame", normalized));
        Set<UUID> seen = new HashSet<>();
        for (Player player : participants) {
            if (!seen.add(player.getUniqueId())) continue;
            api.playerStats().increment(player.getUniqueId(), player.getName(), playedKey, 1);
            if (winners.contains(player.getUniqueId())) api.playerStats().increment(player.getUniqueId(), player.getName(), winsKey, 1);
        }
    }

    public void recalculate(@NotNull UUID uuid) {
        Set<String> current = applied.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
        for (EntitlementDefinition definition : entitlements.values()) {
            if (definition.manual()) continue;
            if (revokedEntitlements.getOrDefault(uuid, Set.of()).contains(definition.id())) continue;
            if (current.contains(definition.id()) && !definition.rolling()) {
                syncLuckPerms(uuid, definition, true);
                continue;
            }
            boolean qualified = qualifies(uuid, definition);
            Set<String> badgesBefore = ownedBadgeIds(uuid);
            Set<String> permissionsBefore = ownedPermissions(uuid);
            if (qualified && current.add(definition.id())) {
                persistEntitlement(uuid, definition.id(), "calculated");
                syncLuckPerms(uuid, definition, true);
                notifyEntitlementChange(uuid, definition, true, badgesBefore, permissionsBefore);
            } else if (!qualified && definition.rolling()
                && !manualEntitlements.getOrDefault(uuid, Set.of()).contains(definition.id())
                && current.remove(definition.id())) {
                deleteEntitlement(uuid, definition.id());
                syncLuckPerms(uuid, definition, false);
                notifyEntitlementChange(uuid, definition, false, badgesBefore, permissionsBefore);
            } else if (current.contains(definition.id())) {
                syncLuckPerms(uuid, definition, true);
            }
        }
    }

    public @NotNull List<BadgeDefinition> appliedBadges(@NotNull UUID uuid) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(directBadges.getOrDefault(uuid, Set.of()));
        for (String entitlementId : applied.getOrDefault(uuid, Set.of())) {
            EntitlementDefinition definition = entitlements.get(entitlementId);
            if (definition != null) ids.addAll(definition.badges());
        }
        for (BadgeDefinition badge : badges.values()) {
            if (hasBadgePermission(uuid, badge)) ids.add(badge.id());
        }
        Set<String> suppressed = revokedBadges.getOrDefault(uuid, Set.of());
        return ids.stream().filter(id -> !suppressed.contains(id)).map(badges::get).filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(BadgeDefinition::priority).reversed().thenComparing(BadgeDefinition::id))
            .toList();
    }

    public @NotNull String renderBadges(@NotNull UUID uuid, int limit) {
        List<BadgeDefinition> values = appliedBadges(uuid);
        if (limit > 0 && values.size() > limit) values = values.subList(0, limit);
        if (values.isEmpty()) return "";
        return badgePrefix + String.join(badgeSeparator, values.stream().map(this::renderBadgeDisplay).toList()) + badgeSuffix;
    }

    public @NotNull String applyBadgePlaceholders(@NotNull UUID uuid, @Nullable String input) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        Matcher matcher = BADGE_PLACEHOLDER.matcher(input);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            int limit = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(renderBadges(uuid, limit)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    public boolean grantEntitlement(@NotNull UUID uuid, @NotNull String id, @NotNull String source) {
        EntitlementDefinition definition = entitlements.get(normalize(id));
        if (definition == null) return false;
        revokedEntitlements.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).remove(definition.id());
        api.database().update("DELETE FROM player_entitlement_revocations WHERE player_uuid=? AND entitlement_id=?", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, definition.id());
        });
        Set<String> badgesBefore = ownedBadgeIds(uuid);
        Set<String> permissionsBefore = ownedPermissions(uuid);
        boolean added = applied.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(definition.id());
        if (added) {
            persistEntitlement(uuid, definition.id(), source);
        }
        if ("manual".equalsIgnoreCase(source)) {
            manualEntitlements.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(definition.id());
            persistEntitlement(uuid, definition.id(), "manual");
        }
        syncLuckPerms(uuid, definition, true);
        if (added) notifyEntitlementChange(uuid, definition, true, badgesBefore, permissionsBefore);
        return true;
    }

    public boolean revokeEntitlement(@NotNull UUID uuid, @NotNull String id) {
        EntitlementDefinition definition = entitlements.get(normalize(id));
        if (definition == null) return false;
        Set<String> badgesBefore = ownedBadgeIds(uuid);
        Set<String> permissionsBefore = ownedPermissions(uuid);
        boolean removed = applied.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).remove(definition.id());
        manualEntitlements.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).remove(definition.id());
        revokedEntitlements.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(definition.id());
        api.database().update("INSERT OR REPLACE INTO player_entitlement_revocations(player_uuid,entitlement_id,revoked_at) VALUES(?,?,?)", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, definition.id()); ps.setLong(3, System.currentTimeMillis());
        });
        deleteEntitlement(uuid, definition.id());
        syncLuckPerms(uuid, definition, false);
        if (removed) notifyEntitlementChange(uuid, definition, false, badgesBefore, permissionsBefore);
        return true;
    }

    public boolean grantBadge(@NotNull UUID uuid, @NotNull String id) {
        BadgeDefinition badge = badges.get(normalize(id));
        if (badge == null) return false;
        revokedBadges.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).remove(badge.id());
        api.database().update("DELETE FROM player_badge_revocations WHERE player_uuid=? AND badge_id=?", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, badge.id());
        });
        Set<String> before = ownedBadgeIds(uuid);
        boolean added = directBadges.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(badge.id());
        api.database().update("INSERT OR REPLACE INTO player_badges(player_uuid,badge_id,granted_at) VALUES(?,?,?)", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, badge.id()); ps.setLong(3, System.currentTimeMillis());
        });
        syncBadgePermission(uuid, badge, true);
        if (added) notifyBadgeChanges(uuid, before);
        return true;
    }

    public boolean revokeBadge(@NotNull UUID uuid, @NotNull String id) {
        BadgeDefinition badge = badges.get(normalize(id));
        if (badge == null) return false;
        Set<String> before = ownedBadgeIds(uuid);
        boolean removed = directBadges.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).remove(badge.id());
        revokedBadges.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(badge.id());
        api.database().update("INSERT OR REPLACE INTO player_badge_revocations(player_uuid,badge_id,revoked_at) VALUES(?,?,?)", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, badge.id()); ps.setLong(3, System.currentTimeMillis());
        });
        api.database().update("DELETE FROM player_badges WHERE player_uuid=? AND badge_id=?", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, badge.id());
        });
        if (appliedBadges(uuid).stream().noneMatch(value -> value.id().equals(badge.id()))) syncBadgePermission(uuid, badge, false);
        if (removed || before.contains(badge.id())) notifyBadgeChanges(uuid, before);
        return true;
    }

    private Set<String> ownedBadgeIds(UUID uuid) {
        LinkedHashSet<String> result = new LinkedHashSet<>(directBadges.getOrDefault(uuid, Set.of()));
        for (String entitlementId : applied.getOrDefault(uuid, Set.of())) {
            EntitlementDefinition definition = entitlements.get(entitlementId);
            if (definition != null) result.addAll(definition.badges());
        }
        result.removeAll(revokedBadges.getOrDefault(uuid, Set.of()));
        return result;
    }

    private Set<String> ownedPermissions(UUID uuid) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String entitlementId : applied.getOrDefault(uuid, Set.of())) {
            EntitlementDefinition definition = entitlements.get(entitlementId);
            if (definition != null) result.addAll(definition.permissions());
        }
        return result;
    }

    private void notifyEntitlementChange(UUID uuid, EntitlementDefinition definition, boolean gained,
                                         Set<String> badgesBefore, Set<String> permissionsBefore) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !getConfigSection().getBoolean("notifications.enabled", true)) return;
        Set<String> permissionsAfter = ownedPermissions(uuid);
        boolean permissionsChanged = gained
            ? permissionsAfter.stream().anyMatch(permission -> !permissionsBefore.contains(permission))
            : permissionsBefore.stream().anyMatch(permission -> !permissionsAfter.contains(permission));
        if (permissionsChanged) {
            String path = gained ? "notifications.ability-gained" : "notifications.ability-lost";
            api.messages().send(player, getConfigSection().getString(path,
                gained ? "/success/New ability: <yellow>{name}</yellow> — {description}"
                    : "/warn/Ability lost: <yellow>{name}</yellow> — {description}"),
                "name", definition.name(), "description", definition.description());
        }
        notifyBadgeChanges(uuid, badgesBefore);
    }

    private void notifyBadgeChanges(UUID uuid, Set<String> before) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !getConfigSection().getBoolean("notifications.enabled", true)) return;
        Set<String> after = ownedBadgeIds(uuid);
        for (String id : after) if (!before.contains(id)) notifyBadge(player, id, true);
        for (String id : before) if (!after.contains(id)) notifyBadge(player, id, false);
    }

    private void notifyBadge(Player player, String id, boolean gained) {
        BadgeDefinition badge = badges.get(id);
        if (badge == null) return;
        String path = gained ? "notifications.badge-gained" : "notifications.badge-lost";
        api.messages().send(player, getConfigSection().getString(path,
            gained ? "/success/Badge earned: {badge} {description}" : "/warn/Badge lost: {badge} {description}"),
            "badge", badge.display(), "description", badge.description());
    }

    private static String friendlyName(String id) {
        String[] words = id.replace('.', '-').replace('_', '-').split("-");
        StringJoiner result = new StringJoiner(" ");
        for (String word : words) if (!word.isBlank())
            result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        return result.toString();
    }

    private void resetAll(UUID uuid) {
        for (String id : new HashSet<>(applied.getOrDefault(uuid, Set.of()))) {
            EntitlementDefinition definition = entitlements.get(id);
            if (definition != null) syncLuckPerms(uuid, definition, false);
        }
        for (String id : new HashSet<>(directBadges.getOrDefault(uuid, Set.of()))) {
            BadgeDefinition badge = badges.get(id);
            if (badge != null) syncBadgePermission(uuid, badge, false);
        }
        applied.remove(uuid); directBadges.remove(uuid); manualEntitlements.remove(uuid);
        revokedEntitlements.remove(uuid); revokedBadges.remove(uuid);
        for (String table : List.of("player_entitlements", "player_badges", "player_entitlement_revocations", "player_badge_revocations")) {
            api.database().update("DELETE FROM " + table + " WHERE player_uuid=?", ps -> ps.setString(1, uuid.toString()));
        }
    }

    private boolean qualifies(UUID uuid, EntitlementDefinition definition) {
        for (StatCondition condition : definition.stats()) {
            if (api.playerStats().getDefinition(condition.key()) == null) return false;
            if (api.playerStats().total(uuid, condition.key(), condition.duration()) < condition.atLeast()) return false;
        }
        if (definition.questsCompleted() != null) {
            Integer count = api.database().querySingleMapped(
                "SELECT COUNT(*) FROM quest_completed WHERE player_uuid=?", ps -> ps.setString(1, uuid.toString()), rs -> rs.getInt(1));
            if ((count == null ? 0 : count) < definition.questsCompleted()) return false;
        }
        for (String quest : definition.quests()) {
            Integer found = api.database().querySingleMapped(
                "SELECT COUNT(*) FROM quest_completed WHERE player_uuid=? AND quest_id=?", ps -> {
                    ps.setString(1, uuid.toString()); ps.setString(2, quest);
                }, rs -> rs.getInt(1));
            if (found == null || found == 0) return false;
        }
        for (String permission : definition.requiresPermissions()) {
            if (!PermissionUtil.hasPermission(uuid, permission)) return false;
        }
        return definition.condition() == null || matches(uuid, definition.condition());
    }

    private boolean matches(UUID uuid, Condition condition) {
        if (condition instanceof ConditionGroup group) return group.all()
            ? group.conditions().stream().allMatch(child -> matches(uuid, child))
            : group.conditions().stream().anyMatch(child -> matches(uuid, child));
        if (condition instanceof NotCondition not) return !matches(uuid, not.condition());
        if (condition instanceof PermissionCondition permission)
            return PermissionUtil.hasPermission(uuid, permission.permission());
        if (condition instanceof StatCondition stat)
            return api.playerStats().getDefinition(stat.key()) != null
                && api.playerStats().total(uuid, stat.key(), stat.duration()) >= stat.atLeast();
        if (condition instanceof QuestCondition quest) {
            Integer found = api.database().querySingleMapped(
                "SELECT COUNT(*) FROM quest_completed WHERE player_uuid=? AND quest_id=?", ps -> {
                    ps.setString(1, uuid.toString()); ps.setString(2, quest.quest());
                }, rs -> rs.getInt(1));
            return found != null && found > 0;
        }
        if (condition instanceof QuestCountCondition count) {
            Integer found = api.database().querySingleMapped(
                "SELECT COUNT(*) FROM quest_completed WHERE player_uuid=?", ps -> ps.setString(1, uuid.toString()), rs -> rs.getInt(1));
            return (found == null ? 0 : found) >= count.atLeast();
        }
        return false;
    }

    private boolean hasBadgePermission(UUID uuid, BadgeDefinition badge) {
        if (badge.permission().isBlank()) return false;
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? online.hasPermission(badge.permission()) : PermissionUtil.hasPermission(uuid, badge.permission());
    }

    private void createTables() {
        api.database().execute("CREATE TABLE IF NOT EXISTS player_entitlements (player_uuid TEXT NOT NULL, entitlement_id TEXT NOT NULL, source TEXT NOT NULL, granted_at INTEGER NOT NULL, PRIMARY KEY(player_uuid,entitlement_id))");
        api.database().execute("CREATE TABLE IF NOT EXISTS player_badges (player_uuid TEXT NOT NULL, badge_id TEXT NOT NULL, granted_at INTEGER NOT NULL, PRIMARY KEY(player_uuid,badge_id))");
        api.database().execute("CREATE TABLE IF NOT EXISTS player_entitlement_revocations (player_uuid TEXT NOT NULL, entitlement_id TEXT NOT NULL, revoked_at INTEGER NOT NULL, PRIMARY KEY(player_uuid,entitlement_id))");
        api.database().execute("CREATE TABLE IF NOT EXISTS player_badge_revocations (player_uuid TEXT NOT NULL, badge_id TEXT NOT NULL, revoked_at INTEGER NOT NULL, PRIMARY KEY(player_uuid,badge_id))");
    }

    private void loadApplied() {
        applied.clear(); directBadges.clear(); manualEntitlements.clear(); revokedEntitlements.clear(); revokedBadges.clear();
        api.database().queryEach("SELECT player_uuid,entitlement_id,source FROM player_entitlements", null, rs -> {
            UUID uuid = UUID.fromString(rs.getString(1));
            applied.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(rs.getString(2));
            if ("manual".equalsIgnoreCase(rs.getString(3))) manualEntitlements.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(rs.getString(2));
        });
        api.database().queryEach("SELECT player_uuid,badge_id FROM player_badges", null, rs ->
            directBadges.computeIfAbsent(UUID.fromString(rs.getString(1)), ignored -> ConcurrentHashMap.newKeySet()).add(rs.getString(2)));
        api.database().queryEach("SELECT player_uuid,entitlement_id FROM player_entitlement_revocations", null, rs ->
            revokedEntitlements.computeIfAbsent(UUID.fromString(rs.getString(1)), ignored -> ConcurrentHashMap.newKeySet()).add(rs.getString(2)));
        api.database().queryEach("SELECT player_uuid,badge_id FROM player_badge_revocations", null, rs ->
            revokedBadges.computeIfAbsent(UUID.fromString(rs.getString(1)), ignored -> ConcurrentHashMap.newKeySet()).add(rs.getString(2)));
    }

    private Set<UUID> knownPlayers() {
        Set<UUID> uuids = new LinkedHashSet<>(applied.keySet());
        uuids.addAll(directBadges.keySet());
        uuids.addAll(revokedEntitlements.keySet());
        uuids.addAll(revokedBadges.keySet());
        Bukkit.getOnlinePlayers().forEach(player -> uuids.add(player.getUniqueId()));
        api.database().queryEach("SELECT DISTINCT player_uuid FROM player_stats_state", null, rs -> uuids.add(UUID.fromString(rs.getString(1))));
        return uuids;
    }

    private void persistEntitlement(UUID uuid, String id, String source) {
        api.database().update("INSERT OR REPLACE INTO player_entitlements(player_uuid,entitlement_id,source,granted_at) VALUES(?,?,?,?)", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, id); ps.setString(3, source); ps.setLong(4, System.currentTimeMillis());
        });
    }

    private void deleteEntitlement(UUID uuid, String id) {
        api.database().update("DELETE FROM player_entitlements WHERE player_uuid=? AND entitlement_id=?", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, id);
        });
    }

    private void loadConfig() {
        entitlements.clear(); badges.clear();
        ConfigSection root = getConfigSection();
        badgePrefix = root.getString("badge-display.prefix", " ");
        badgeSeparator = root.getString("badge-display.separator", "");
        badgeSuffix = root.getString("badge-display.suffix", "");
        ConfigSectionView badgeRoot = root.getSection("badges");
        if (badgeRoot != null) for (String id : badgeRoot.getKeys(false)) {
            ConfigSectionView section = badgeRoot.getSection(id);
            if (section == null || !valid(id)) continue;
            String display = migrateDefaultBadgeDisplay(id, section.getString("display", "<gray>:star:</gray>"));
            badges.put(id, new BadgeDefinition(id, display,
                section.getString("description", id), section.getString("permission", "stemcraft.badge." + id),
                section.getInt("priority", 0)));
        }
        ConfigSectionView entitlementRoot = root.getSection("definitions");
        if (entitlementRoot != null) for (String id : entitlementRoot.getKeys(false)) {
            ConfigSectionView section = entitlementRoot.getSection(id);
            if (section == null || !valid(id)) continue;
            List<StatCondition> stats = readStats(section);
            List<String> quests = new ArrayList<>(section.getStringList("when.quests"));
            String quest = section.getString("when.quest-completed", "");
            if (!quest.isBlank()) quests.add(quest);
            Integer questCount = section.contains("when.quests-completed.at-least") ? section.getInt("when.quests-completed.at-least") : null;
            List<String> permissions = section.getStringList("grants.permissions");
            List<String> badgeIds = section.getStringList("grants.badges");
            List<String> requiredPermissions = section.getStringList("when.permissions");
            Condition condition = readConditionTree(section);
            boolean manual = !section.isSection("when") && !section.contains("when");
            entitlements.put(id, new EntitlementDefinition(id,
                section.getString("name", friendlyName(id)), section.getString("description", friendlyName(id)),
                stats, quests, questCount, requiredPermissions,
                permissions, badgeIds, condition, manual,
                stats.stream().anyMatch(value -> value.duration() != null) || isRolling(condition)));
        }
    }

    private Condition readConditionTree(ConfigSectionView section) {
        List<Condition> roots = new ArrayList<>();
        addConditionGroup(roots, section.get("when.all"), true);
        addConditionGroup(roots, section.get("when.any"), false);
        Object not = section.get("when.not");
        if (not != null) {
            Condition child = readCondition(not);
            if (child != null) roots.add(new NotCondition(child));
        }
        return roots.isEmpty() ? null : roots.size() == 1 ? roots.getFirst() : new ConditionGroup(true, roots);
    }

    private void addConditionGroup(List<Condition> roots, Object raw, boolean all) {
        if (!(raw instanceof List<?> values)) return;
        List<Condition> children = values.stream().map(this::readCondition).filter(Objects::nonNull).toList();
        if (!children.isEmpty()) roots.add(new ConditionGroup(all, children));
    }

    private @Nullable Condition readCondition(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        if (map.get("all") instanceof List<?> all) {
            List<Condition> children = all.stream().map(this::readCondition).filter(Objects::nonNull).toList();
            return children.isEmpty() ? null : new ConditionGroup(true, children);
        }
        if (map.get("any") instanceof List<?> any) {
            List<Condition> children = any.stream().map(this::readCondition).filter(Objects::nonNull).toList();
            return children.isEmpty() ? null : new ConditionGroup(false, children);
        }
        if (map.containsKey("not")) {
            Condition child = readCondition(map.get("not"));
            return child == null ? null : new NotCondition(child);
        }
        if (map.get("stat") instanceof Map<?, ?> stat) {
            String key = Objects.toString(stat.get("key"), "");
            return key.isBlank() ? null : new StatCondition(key, number(stat.get("at-least")),
                normalizeDuration(Objects.toString(stat.get("duration"), "")));
        }
        if (map.containsKey("permission")) {
            String permission = Objects.toString(map.get("permission"), "").trim();
            return permission.isEmpty() ? null : new PermissionCondition(permission);
        }
        if (map.containsKey("quest-completed")) {
            String quest = Objects.toString(map.get("quest-completed"), "").trim();
            return quest.isEmpty() ? null : new QuestCondition(quest);
        }
        if (map.get("quests-completed") instanceof Map<?, ?> count)
            return new QuestCountCondition((int) number(count.get("at-least")));
        return null;
    }

    private boolean isRolling(@Nullable Condition condition) {
        if (condition instanceof StatCondition stat) return stat.duration() != null;
        if (condition instanceof ConditionGroup group) return group.conditions().stream().anyMatch(this::isRolling);
        return condition instanceof NotCondition not && isRolling(not.condition());
    }

    private List<StatCondition> readStats(ConfigSectionView section) {
        List<StatCondition> result = new ArrayList<>();
        ConfigSectionView singular = section.getSection("when.stat");
        if (singular != null) addStat(result, singular);
        Object raw = section.get("when.stats");
        if (raw instanceof List<?> list) for (Object value : list) {
            if (value instanceof Map<?, ?> map) {
                String key = Objects.toString(map.get("key"), "");
                double atLeast = number(map.get("at-least"));
                String duration = normalizeDuration(Objects.toString(map.get("duration"), ""));
                if (!key.isBlank()) result.add(new StatCondition(key, atLeast, duration));
            }
        }
        return List.copyOf(result);
    }

    private void addStat(List<StatCondition> result, ConfigSectionView section) {
        String key = section.getString("key", "");
        if (!key.isBlank()) result.add(new StatCondition(key, section.getDouble("at-least", 0), normalizeDuration(section.getString("duration", ""))));
    }

    private void registerCommands() {
        api.commands().create("badges").description("View applied player badges.").usage("/badges [player]")
            .tabCompletion("{player}").executor((unused, command, ctx) -> showBadges(ctx)).register(plugin);
        api.commands().create("entitlements").description("Manage entitlements and badges.")
            .usage("/entitlements <menu|edit|condition|list|reload|recalculate|grant|revoke|badge|create|delete|set>")
            .permission(ADMIN_PERMISSION).tabCompletion("menu").tabCompletion("edit").tabCompletion("condition")
            .tabCompletion("list").tabCompletion("reload").tabCompletion("recalculate", "{player}")
            .tabCompletion("grant", "{player}").tabCompletion("revoke", "{player}")
            .tabCompletion("badge", "grant", "{player}").tabCompletion("badge", "revoke", "{player}")
            .tabCompletion("create").tabCompletion("delete").tabCompletion("set")
            .executor((unused, command, ctx) -> manage(ctx)).register(plugin);
    }

    private void showBadges(CommandContext ctx) {
        ResolvedPlayer target;
        if (ctx.args().isEmpty()) {
            if (!ctx.isPlayer()) ctx.returnError("Console must specify a player.");
            target = ResolvedPlayer.of(ctx.asPlayer());
        } else {
            target = api.players().resolveIdentity(ctx.getArg(0));
            if (target == null) {
                ctx.returnError("Player not found.");
                return;
            }
        }
        List<BadgeDefinition> visible = appliedBadges(target.uuid());
        ctx.getSender().sendMessage(Component.text("Badges for " + target.name(), NamedTextColor.GOLD));
        if (visible.isEmpty()) {
            ctx.getSender().sendMessage(Component.text("  No badges applied.", NamedTextColor.GRAY));
            return;
        }
        for (BadgeDefinition badge : visible) {
            ctx.getSender().sendMessage(Component.text("  ").append(miniMessage.deserialize(renderBadgeDisplay(badge)))
                .append(Component.text(" " + badge.description(), NamedTextColor.GRAY)));
        }
    }

    private String renderBadgeDisplay(BadgeDefinition badge) {
        return api.messages().tokens().apply(badge.display());
    }

    static String migrateDefaultBadgeDisplay(String id, String display) {
        String legacy = switch (id) {
            case "explorer" -> "<green>✦</green>";
            case "quest-master" -> "<light_purple>✪</light_purple>";
            case "veteran" -> "<gold>★</gold>";
            case "survival-veteran" -> "<dark_green>♜</dark_green>";
            case "bedwars-player" -> "<red>⚔</red>";
            case "bedwars-champion" -> "<gold>♛</gold>";
            case "bridge-hot-streak" -> "<aqua>⚡</aqua>";
            case "builder" -> "<yellow>⌂</yellow>";
            case "event-winner" -> "<gradient:#ffd700:#ff8c00>🏆</gradient>";
            default -> null;
        };
        if (!display.equals(legacy)) return display;
        return switch (id) {
            case "explorer" -> ":compass:";
            case "quest-master" -> ":question_yellow:";
            case "veteran" -> ":clock:";
            case "survival-veteran" -> ":survival:";
            case "bedwars-player" -> ":swords:";
            case "bedwars-champion" -> ":crown:";
            case "bridge-hot-streak" -> ":star:";
            case "builder" -> ":crafting_table:";
            case "event-winner" -> ":trophy:";
            default -> display;
        };
    }

    private void manage(CommandContext ctx) {
        if (ctx.args().isEmpty()) { showEntitlementMenu(ctx); return; }
        String action = ctx.getArgLower(0);
        if ("menu".equals(action)) { showEntitlementMenu(ctx); return; }
        if ("edit".equals(action)) { showEntitlementEditor(ctx); return; }
        if ("condition".equals(action)) { editCondition(ctx); return; }
        if ("list".equals(action)) {
            ctx.info("Entitlements: " + String.join(", ", entitlements.keySet()));
            ctx.info("Badges: " + String.join(", ", badges.keySet()));
            return;
        }
        if ("reload".equals(action)) { onReload(); ctx.returnSuccess("Entitlements reloaded and recalculated."); }
        if ("recalculate".equals(action)) {
            ResolvedPlayer player = requirePlayer(ctx, 1); recalculate(player.uuid()); ctx.returnSuccess("Recalculated " + player.name() + ".");
        }
        if ("grant".equals(action) || "revoke".equals(action)) {
            ResolvedPlayer player = requirePlayer(ctx, 1); requireArgs(ctx, 3); String id = ctx.getArgLower(2);
            boolean ok = "grant".equals(action) ? grantEntitlement(player.uuid(), id, "manual") : revokeEntitlement(player.uuid(), id);
            if (!ok) ctx.returnError("Unknown entitlement '" + id + "'.");
            ctx.returnSuccess(("grant".equals(action) ? "Granted " : "Revoked ") + id + " for " + player.name() + ".");
        }
        if ("badge".equals(action)) { manageBadge(ctx); return; }
        if ("create".equals(action) || "delete".equals(action) || "set".equals(action)) { editEntitlement(ctx, action); return; }
        ctx.returnUsage();
    }

    private void showEntitlementMenu(CommandContext ctx) {
        List<EntitlementDefinition> values = new ArrayList<>(entitlements.values());
        int page = ChatMenuUtil.getPageFromArgs(ctx.args(), 1, 1);
        ChatMenuUtil.render(ctx.getSender(), "Ability administration", "entitlements menu", page, values.size(),
            (start, count, interactive) -> values.subList(start, start + count).stream().map(definition -> {
                Component line = Component.text(definition.manual() ? "○ " : "● ",
                        definition.manual() ? NamedTextColor.GRAY : NamedTextColor.GREEN)
                    .append(Component.text(definition.name(), NamedTextColor.WHITE))
                    .append(Component.text(" (" + definition.id() + ")", NamedTextColor.DARK_GRAY));
                return interactive ? line.clickEvent(ClickEvent.runCommand("/entitlements edit " + definition.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("Open ability editor"))) : line;
            }).toList(), "No abilities are configured.");
        ctx.getSender().sendMessage(suggestButton("[＋ Create]", "/entitlements create new-ability",
            "Click, replace the ID, then send").append(Component.space())
            .append(button("[↻ Reload]", "/entitlements reload", "Reload and recalculate")));
    }

    private void showEntitlementEditor(CommandContext ctx) {
        requireArgs(ctx, 2);
        EntitlementDefinition definition = entitlements.get(ctx.getArgLower(1));
        if (definition == null) { ctx.returnError("Unknown entitlement '" + ctx.getArg(1) + "'."); return; }
        String base = "/entitlements set " + definition.id() + " ";
        ctx.getSender().sendMessage(Component.text("--- Ability: " + definition.name() + " ---", NamedTextColor.AQUA));
        ctx.getSender().sendMessage(editField("Name", definition.name(), base + "name "));
        ctx.getSender().sendMessage(editField("Description", definition.description(), base + "description "));
        ctx.getSender().sendMessage(editField("Granted permissions", String.join(", ", definition.permissions()), base + "permissions "));
        ctx.getSender().sendMessage(editField("Granted badges", String.join(", ", definition.badges()), base + "badges "));
        ctx.getSender().sendMessage(Component.text("Conditions", NamedTextColor.YELLOW));
        Condition root = combinedCondition(definition);
        if (root == null) ctx.getSender().sendMessage(Component.text("  Manual grant only", NamedTextColor.GRAY));
        else renderCondition(ctx, definition.id(), root, "root", 0);
        if (!(root instanceof ConditionGroup)) ctx.getSender().sendMessage(conditionAddButtons(definition.id(), "root"));
        ctx.getSender().sendMessage(button("[← All abilities]", "/entitlements menu", "Back to ability list")
            .append(Component.space()).append(button("[Make manual]", "/entitlements set " + definition.id() + " manual true", "Remove all conditions"))
            .append(Component.space()).append(suggestButton("[Delete]", "/entitlements delete " + definition.id(), "Delete this ability")));
    }

    private void renderCondition(CommandContext ctx, String id, Condition condition, String path, int depth) {
        String indent = "  ".repeat(depth);
        Component controls = path.equals("root") ? Component.empty()
            : Component.space().append(button("[×]", "/entitlements condition " + id + " remove " + path, "Remove condition"));
        if (condition instanceof ConditionGroup group) {
            String operator = group.all() ? "ALL (AND)" : "ANY (OR)";
            ctx.getSender().sendMessage(Component.text(indent + operator, NamedTextColor.GOLD)
                .append(Component.space()).append(button("[toggle]", "/entitlements condition " + id + " operator " + path
                    + " " + (group.all() ? "any" : "all"), "Change AND/OR")).append(controls));
            for (int i = 0; i < group.conditions().size(); i++)
                renderCondition(ctx, id, group.conditions().get(i), childPath(path, i), depth + 1);
            ctx.getSender().sendMessage(Component.text(indent).append(conditionAddButtons(id, path)));
            return;
        }
        String label = conditionLabel(condition);
        Component line = Component.text(indent + "• " + label, NamedTextColor.WHITE).append(controls);
        if (!(condition instanceof NotCondition)) line = line.append(Component.space())
            .append(button("[AND]", "/entitlements condition " + id + " wrap " + path + " all", "Wrap in an AND group"))
            .append(Component.space()).append(button("[OR]", "/entitlements condition " + id + " wrap " + path + " any", "Wrap in an OR group"))
            .append(Component.space()).append(button("[NOT]", "/entitlements condition " + id + " wrap " + path + " not", "Negate condition"));
        ctx.getSender().sendMessage(line);
        if (condition instanceof NotCondition not) renderCondition(ctx, id, not.condition(), childPath(path, 0), depth + 1);
    }

    private Component conditionAddButtons(String id, String path) {
        String base = "/entitlements condition " + id + " add " + path + " ";
        return suggestButton("[＋ stat]", base + "stat skill_mining_xp 400", "Add a skill/stat threshold")
            .append(Component.space()).append(suggestButton("[＋ permission]", base + "permission stemcraft.permission", "Require a permission"))
            .append(Component.space()).append(suggestButton("[＋ NOT permission]", base + "not-permission stemcraft.permission", "Require a missing permission"))
            .append(Component.space()).append(suggestButton("[＋ quest]", base + "quest quest-id", "Require a completed quest"));
    }

    private String conditionLabel(Condition condition) {
        if (condition instanceof StatCondition stat) return stat.key() + " ≥ " + formatThreshold(stat.atLeast())
            + (stat.duration() == null ? "" : " in " + stat.duration());
        if (condition instanceof PermissionCondition permission) return "has permission " + permission.permission();
        if (condition instanceof QuestCondition quest) return "completed quest " + quest.quest();
        if (condition instanceof QuestCountCondition count) return "completed at least " + count.atLeast() + " quests";
        if (condition instanceof NotCondition) return "NOT";
        return "condition";
    }

    private void editCondition(CommandContext ctx) {
        requireArgs(ctx, 4);
        String id = ctx.getArgLower(1);
        EntitlementDefinition definition = entitlements.get(id);
        if (definition == null) { ctx.returnError("Unknown entitlement '" + id + "'."); return; }
        String action = ctx.getArgLower(2);
        String path = ctx.getArgLower(3);
        Condition condition = combinedCondition(definition);
        try {
            switch (action) {
                case "add" -> {
                    requireArgs(ctx, 5);
                    Condition added = conditionFromCommand(ctx, 4);
                    condition = addAt(condition, conditionPath(path), added);
                }
                case "remove" -> condition = replaceAt(condition, conditionPath(path), ignored -> null);
                case "operator" -> {
                    requireArgs(ctx, 5);
                    boolean all = switch (ctx.getArgLower(4)) {
                        case "all", "and" -> true;
                        case "any", "or" -> false;
                        default -> throw new IllegalArgumentException("Operator must be all/and or any/or.");
                    };
                    condition = replaceAt(condition, conditionPath(path), current -> {
                        if (!(current instanceof ConditionGroup group)) throw new IllegalArgumentException("That condition is not a group.");
                        return new ConditionGroup(all, group.conditions());
                    });
                }
                case "wrap" -> {
                    requireArgs(ctx, 5);
                    String wrapper = ctx.getArgLower(4);
                    condition = replaceAt(condition, conditionPath(path), current -> switch (wrapper) {
                        case "all", "and" -> new ConditionGroup(true, List.of(current));
                        case "any", "or" -> new ConditionGroup(false, List.of(current));
                        case "not" -> current instanceof NotCondition not ? not.condition() : new NotCondition(current);
                        default -> throw new IllegalArgumentException("Wrapper must be all, any, or not.");
                    });
                }
                default -> throw new IllegalArgumentException("Use add, remove, operator, or wrap.");
            }
        } catch (IllegalArgumentException exception) {
            ctx.returnError(exception.getMessage());
            return;
        }
        ConfigSection root = getConfigSection();
        String configPath = "definitions." + id + ".when";
        root.remove(configPath);
        if (condition != null) {
            Condition stored = condition instanceof ConditionGroup ? condition : new ConditionGroup(true, List.of(condition));
            root.set(configPath, conditionMap(stored));
        }
        root.save();
        onReload();
        ctx.success("Ability conditions updated.");
        showEntitlementEditor(ctx);
    }

    private Condition conditionFromCommand(CommandContext ctx, int index) {
        String type = ctx.getArgLower(index);
        return switch (type) {
            case "stat" -> {
                requireArgs(ctx, index + 3);
                double threshold;
                try { threshold = Double.parseDouble(ctx.getArg(index + 2)); }
                catch (NumberFormatException exception) { throw new IllegalArgumentException("Stat threshold must be a number."); }
                String duration = normalizeDuration(ctx.getArg(index + 3, ""));
                yield new StatCondition(ctx.getArg(index + 1), threshold, duration);
            }
            case "permission" -> {
                requireArgs(ctx, index + 2);
                yield new PermissionCondition(ctx.getArg(index + 1));
            }
            case "not-permission" -> {
                requireArgs(ctx, index + 2);
                yield new NotCondition(new PermissionCondition(ctx.getArg(index + 1)));
            }
            case "quest" -> {
                requireArgs(ctx, index + 2);
                yield new QuestCondition(ctx.getArg(index + 1));
            }
            case "quests" -> {
                requireArgs(ctx, index + 2);
                try { yield new QuestCountCondition(Integer.parseInt(ctx.getArg(index + 1))); }
                catch (NumberFormatException exception) { throw new IllegalArgumentException("Quest count must be a whole number."); }
            }
            default -> throw new IllegalArgumentException("Condition type must be stat, permission, not-permission, quest, or quests.");
        };
    }

    private Condition combinedCondition(EntitlementDefinition definition) {
        List<Condition> values = new ArrayList<>();
        values.addAll(definition.stats());
        definition.quests().forEach(quest -> values.add(new QuestCondition(quest)));
        if (definition.questsCompleted() != null) values.add(new QuestCountCondition(definition.questsCompleted()));
        definition.requiresPermissions().forEach(permission -> values.add(new PermissionCondition(permission)));
        if (definition.condition() != null) values.add(definition.condition());
        return values.isEmpty() ? null : values.size() == 1 ? values.getFirst() : new ConditionGroup(true, List.copyOf(values));
    }

    private static List<Integer> conditionPath(String raw) {
        if (raw == null || raw.equals("root")) return List.of();
        try { return Arrays.stream(raw.split("\\.")).map(Integer::parseInt).toList(); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid condition path."); }
    }

    private static String childPath(String parent, int child) {
        return parent.equals("root") ? String.valueOf(child) : parent + "." + child;
    }

    private Condition addAt(@Nullable Condition root, List<Integer> path, Condition added) {
        if (root == null) return added;
        return replaceAt(root, path, current -> current instanceof ConditionGroup group
            ? new ConditionGroup(group.all(), append(group.conditions(), added))
            : new ConditionGroup(true, List.of(current, added)));
    }

    private static List<Condition> append(List<Condition> values, Condition added) {
        List<Condition> result = new ArrayList<>(values);
        result.add(added);
        return List.copyOf(result);
    }

    private @Nullable Condition replaceAt(@Nullable Condition root, List<Integer> path,
                                           java.util.function.Function<Condition, @Nullable Condition> change) {
        if (root == null) throw new IllegalArgumentException("There are no conditions at that path.");
        if (path.isEmpty()) return change.apply(root);
        int index = path.getFirst();
        List<Integer> remaining = path.subList(1, path.size());
        if (root instanceof ConditionGroup group) {
            if (index < 0 || index >= group.conditions().size()) throw new IllegalArgumentException("Condition path does not exist.");
            List<Condition> children = new ArrayList<>(group.conditions());
            Condition replacement = replaceAt(children.get(index), remaining, change);
            if (replacement == null) children.remove(index); else children.set(index, replacement);
            return children.isEmpty() ? null : new ConditionGroup(group.all(), List.copyOf(children));
        }
        if (root instanceof NotCondition not && index == 0) {
            Condition replacement = replaceAt(not.condition(), remaining, change);
            return replacement == null ? null : new NotCondition(replacement);
        }
        throw new IllegalArgumentException("Condition path does not exist.");
    }

    private Map<String, Object> conditionMap(Condition condition) {
        if (condition instanceof ConditionGroup group) return Map.of(group.all() ? "all" : "any",
            group.conditions().stream().map(this::conditionMap).toList());
        if (condition instanceof NotCondition not) return Map.of("not", conditionMap(not.condition()));
        if (condition instanceof PermissionCondition permission) return Map.of("permission", permission.permission());
        if (condition instanceof QuestCondition quest) return Map.of("quest-completed", quest.quest());
        if (condition instanceof QuestCountCondition count) return Map.of("quests-completed", Map.of("at-least", count.atLeast()));
        if (condition instanceof StatCondition stat) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("key", stat.key()); value.put("at-least", stat.atLeast());
            if (stat.duration() != null) value.put("duration", stat.duration());
            return Map.of("stat", value);
        }
        throw new IllegalArgumentException("Unsupported condition.");
    }

    private Component editField(String label, String value, String command) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value.isBlank() ? "(none)" : value, NamedTextColor.WHITE))
            .append(Component.space()).append(suggestButton("[edit]", command, "Click, edit the command, then send"));
    }

    private Component button(String text, String command, String hover) {
        return Component.text(text, NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private Component suggestButton(String text, String command, String hover) {
        return Component.text(text, NamedTextColor.GOLD).clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private static String formatThreshold(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private void manageBadge(CommandContext ctx) {
        requireArgs(ctx, 3); String sub = ctx.getArgLower(1);
        if ("grant".equals(sub) || "revoke".equals(sub)) {
            requireArgs(ctx, 4); ResolvedPlayer player = requirePlayer(ctx, 2); String id = ctx.getArgLower(3);
            boolean ok = "grant".equals(sub) ? grantBadge(player.uuid(), id) : revokeBadge(player.uuid(), id);
            if (!ok) ctx.returnError("Unknown badge '" + id + "'.");
            ctx.returnSuccess(("grant".equals(sub) ? "Granted " : "Revoked ") + id + " for " + player.name() + ".");
        }
        String id = ctx.getArgLower(2); if (!valid(id)) ctx.returnError("Invalid badge id.");
        ConfigSection root = getConfigSection();
        if ("create".equals(sub)) {
            if (badges.containsKey(id)) ctx.returnError("Badge already exists.");
            root.set("badges." + id + ".display", "<gray>:star:</gray>"); root.set("badges." + id + ".description", id);
            root.set("badges." + id + ".permission", "stemcraft.badge." + id); root.set("badges." + id + ".priority", 0);
        } else if ("delete".equals(sub)) {
            BadgeDefinition existing = badges.get(id);
            if (existing != null) knownPlayers().forEach(uuid -> syncBadgePermission(uuid, existing, false));
            root.remove("badges." + id);
        } else if ("set".equals(sub)) {
            requireArgs(ctx, 5); String field = ctx.getArgLower(3); String value = join(ctx.rawArgs(), 4);
            if ("-".equals(value)) root.remove("badges." + id + "." + field);
            else root.set("badges." + id + "." + field, "priority".equals(field) ? Integer.parseInt(value) : value);
        }
        else ctx.returnUsage();
        root.save(); onReload(); ctx.returnSuccess("Badge configuration updated.");
    }

    private void editEntitlement(CommandContext ctx, String action) {
        requireArgs(ctx, 2); String id = ctx.getArgLower(1); if (!valid(id)) ctx.returnError("Invalid entitlement id.");
        ConfigSection root = getConfigSection();
        if ("create".equals(action)) {
            if (entitlements.containsKey(id)) ctx.returnError("Entitlement already exists.");
            root.set("definitions." + id + ".grants.badges", List.of());
        } else if ("delete".equals(action)) {
            EntitlementDefinition existing = entitlements.get(id);
            if (existing != null) knownPlayers().forEach(uuid -> {
                syncLuckPerms(uuid, existing, false);
                deleteEntitlement(uuid, id);
                Set<String> values = applied.get(uuid);
                if (values != null) values.remove(id);
            });
            root.remove("definitions." + id);
        }
        else {
            requireArgs(ctx, 4); String field = ctx.getArgLower(2); String value = join(ctx.rawArgs(), 3);
            if ("manual".equals(field)) {
                if (!Boolean.parseBoolean(value)) ctx.returnError("Use 'manual true' to remove calculated conditions.");
                root.remove("definitions." + id + ".when");
                root.save(); onReload(); ctx.returnSuccess("Entitlement configuration updated.");
            }
            String path = switch (field) {
                case "stat" -> "when.stat.key"; case "at-least" -> "when.stat.at-least"; case "duration" -> "when.stat.duration";
                case "quest" -> "when.quest-completed"; case "quests-at-least" -> "when.quests-completed.at-least";
                case "badges" -> "grants.badges"; case "permissions" -> "grants.permissions"; default -> field;
            };
            if ("-".equals(value)) {
                root.remove("definitions." + id + "." + path);
                root.save(); onReload(); ctx.returnSuccess("Entitlement configuration updated.");
            }
            Object stored = switch (field) {
                case "at-least" -> Double.parseDouble(value); case "quests-at-least" -> Integer.parseInt(value);
                case "badges", "permissions" -> "none".equalsIgnoreCase(value) ? List.of()
                    : Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
                default -> value;
            };
            root.set("definitions." + id + "." + path, stored);
        }
        root.save(); onReload(); ctx.returnSuccess("Entitlement configuration updated.");
    }

    private ResolvedPlayer requirePlayer(CommandContext ctx, int index) {
        requireArgs(ctx, index + 1); ResolvedPlayer value = api.players().resolveIdentity(ctx.getArg(index));
        if (value == null) ctx.returnError("Player not found."); return value;
    }

    private static void requireArgs(CommandContext ctx, int count) { if (ctx.args().size() < count) ctx.returnUsage(); }
    private static String join(List<String> args, int start) { return String.join(" ", args.subList(start, args.size())); }
    private static boolean valid(String id) { return id != null && ID.matcher(id).matches(); }
    private static String normalize(String id) { return id == null ? "" : id.trim().toLowerCase(Locale.ROOT); }
    private static double number(Object value) { return value instanceof Number n ? n.doubleValue() : 0.0; }

    private static @Nullable String normalizeDuration(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = DURATION.matcher(raw.trim());
        if (!matcher.matches()) return null;
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "h" -> Math.max(1, (amount + 23) / 24) + "d";
            case "w" -> amount * 7 + "d";
            default -> amount + "d";
        };
    }

    private void syncLuckPerms(UUID uuid, EntitlementDefinition definition, boolean add) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return;
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            String groupName = "stemcraft-entitlement-" + definition.id().replace('.', '-').replace('_', '-');
            luckPerms.getGroupManager().createAndLoadGroup(groupName).thenAccept(group -> {
                for (String permission : definition.permissions()) group.data().add(Node.builder(permission).build());
                for (String badgeId : definition.badges()) {
                    BadgeDefinition badge = badges.get(badgeId);
                    if (badge != null && !badge.permission().isBlank()) group.data().add(Node.builder(badge.permission()).build());
                }
                luckPerms.getGroupManager().saveGroup(group);
                luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
                    InheritanceNode node = InheritanceNode.builder(group).build();
                    if (add) user.data().add(node); else user.data().remove(node);
                    luckPerms.getUserManager().saveUser(user);
                });
            });
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning("Could not synchronize LuckPerms entitlement '" + definition.id() + "': " + exception.getMessage());
        }
    }

    private void syncBadgePermission(UUID uuid, BadgeDefinition badge, boolean add) {
        if (badge.permission().isBlank()) return;
        syncLuckPerms(uuid, new EntitlementDefinition("badge-" + badge.id(), badge.description(), badge.description(),
            List.of(), List.of(), null, List.of(), List.of(), List.of(badge.id()), null, true, false), add);
    }

    public record BadgeDefinition(String id, String display, String description, String permission, int priority) {}
    private interface Condition {}
    private record ConditionGroup(boolean all, List<Condition> conditions) implements Condition {}
    private record NotCondition(Condition condition) implements Condition {}
    private record PermissionCondition(String permission) implements Condition {}
    private record QuestCondition(String quest) implements Condition {}
    private record QuestCountCondition(int atLeast) implements Condition {}
    private record StatCondition(String key, double atLeast, @Nullable String duration) implements Condition {}
    private record EntitlementDefinition(String id, String name, String description,
        List<StatCondition> stats, List<String> quests,
        @Nullable Integer questsCompleted, List<String> requiresPermissions, List<String> permissions,
        List<String> badges, @Nullable Condition condition, boolean manual, boolean rolling) {}
}

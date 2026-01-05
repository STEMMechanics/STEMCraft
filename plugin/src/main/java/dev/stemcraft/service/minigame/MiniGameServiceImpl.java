package dev.stemcraft.service.minigame;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.capability.HasMessagesImpl;
import dev.stemcraft.api.event.minigame.ArenaCountdownZeroEvent;
import dev.stemcraft.api.event.minigame.ArenaPlayerJoinEvent;
import dev.stemcraft.api.event.minigame.ArenaPlayerLeaveEvent;
import dev.stemcraft.api.event.minigame.ArenaStatusChangedEvent;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameHudProvider;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.service.minigame.MiniGameService;
import dev.stemcraft.api.minigame.util.TeamNames;
import dev.stemcraft.api.util.SCPlayer;
import dev.stemcraft.api.util.TimeUtil;
import dev.stemcraft.capability.HasMetaImpl;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiniGameServiceImpl implements MiniGameService {

    static class MiniGamePlayerImpl extends HasMetaImpl implements MiniGamePlayer {
        @Getter
        private final Player player;
        @Getter
        @Setter
        private String team;
        @Getter
        @Setter
        private int score;
        private BossBar bossbar;
        private Scoreboard scoreboard;
        private Objective objective;

        public MiniGamePlayerImpl(Player player) {
            this.player = player;
        }

        public void addScore(int delta) {
            this.score += delta;
        }

        public void subScore(int delta) {
            this.score -= delta;
        }

        public void updateBossBar(String title, BarColor color, BarStyle style, double progress) {
            if (bossbar == null) {
                bossbar = Bukkit.createBossBar(title, color, style);
                bossbar.addPlayer(player);
            }

            bossbar.setTitle(title);
            bossbar.setColor(color);
            bossbar.setStyle(style);
            bossbar.setProgress(progress);
        }

        public void removeBossBar() {
            if (bossbar != null) {
                bossbar.removePlayer(player);
                bossbar = null;
            }
        }

        public void updateScoreboard(String title, List<String> lines) {
            if (scoreboard == null) {
                ScoreboardManager manager = Bukkit.getScoreboardManager();
                scoreboard = manager.getNewScoreboard();
                objective = scoreboard.registerNewObjective("minigame", "dummy", title);
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                player.setScoreboard(scoreboard);
            }

            objective.setDisplayName(title);

            // Clear existing scores
            for (String entry : new HashSet<>(scoreboard.getEntries())) {
                scoreboard.resetScores(entry);
            }

            // Set new scores
            int score = lines.size();
            for (String line : lines) {
                objective.getScore(line).setScore(score--);
            }
        }

        public void removeScoreboard() {
            if (scoreboard != null) {
                ScoreboardManager manager = Bukkit.getScoreboardManager();
                player.setScoreboard(manager.getMainScoreboard());
                scoreboard = null;
                objective = null;
            }
        }
    }

    static class MiniGameTeamImpl extends HasMetaImpl {
        @Getter
        private final String name;
        @Getter
        @Setter
        private int score;

        public MiniGameTeamImpl(String name) {
            this.name = name;
            this.score = 0;
        }

        public void addScore(int delta) {
            this.score += delta;
        }

        public void subScore(int delta) {
            this.score -= delta;
        }
    }

    @Getter
    class MiniGameArenaImpl extends HasMetaImpl implements MiniGameArena {
        private final String namespace;
        private final String id;
        private final String name;
        private final World world;
        private Location lobbySpawn;
        private int countdown;
        @Getter(AccessLevel.NONE)
        private int countdownMax;
        private String status;
        private final Map<String, MiniGameTeamImpl> teams = new HashMap<>();
        private final Map<Player, MiniGamePlayerImpl> players = new HashMap<>();

        public MiniGameArenaImpl(String namespace, String id, String name, World world, List<String> teams) {
            this.namespace = namespace;
            this.id = id;
            this.name = name;
            this.world = world;
            this.countdown = 0;
            this.countdownMax = 0;
            this.status = "waiting";
            this.lobbySpawn = world.getSpawnLocation();

            for (String teamName : teams) {
                this.teams.put(teamName, new MiniGameTeamImpl(teamName));
            }
        }

        @Getter(AccessLevel.NONE)
        private boolean firingStatusEvent = false;
        @Getter(AccessLevel.NONE)
        private String pendingStatus = null;

        @Override
        public void setStatus(String newStatus, int countdown) {
            if (newStatus == null) return;

            // During dispatch: queue only (last wins)
            if (firingStatusEvent) {
                pendingStatus = newStatus;
                return;
            }

            if (java.util.Objects.equals(this.status, newStatus)) return;

            String old = this.status;
            this.status = newStatus;

            firingStatusEvent = true;
            try {
                Bukkit.getPluginManager().callEvent(new ArenaStatusChangedEvent(this));
            } finally {
                firingStatusEvent = false;
            }

            // Apply queued status after listeners finish (fires a second event, not nested)
            if (pendingStatus != null && !java.util.Objects.equals(this.status, pendingStatus)) {
                String next = pendingStatus;
                pendingStatus = null;
                setStatus(next);
            } else {
                pendingStatus = null;
            }
        }

        @Override
        public void setCountdown(int seconds) {
            this.countdown = seconds;
            this.countdownMax = Math.max(0, seconds);
        }

        @Override
        public int numPlayers() {
            return players.size();
        }

        @Override
        public List<Player> getPlayers() {
            return new ArrayList<>(players.keySet());
        }

        @Override
        public boolean hasPlayer(Player player) {
            return players.containsKey(player);
        }

        @Override
        public void addPlayer(Player player) {
            if(players.containsKey(player)) {
                return;
            }

            setupPlayer(player);
            players.put(player, getRandomTeam());

            Bukkit.getPluginManager().callEvent(new ArenaPlayerJoinEvent(player, this));
        }

        @Override
        public void removePlayer(Player player) {
            if(!players.containsKey(player)) {
                return;
            }

            Bukkit.getPluginManager().callEvent(new ArenaPlayerLeaveEvent(player, this));

            players.remove(player);
            cleanupPlayer(player);
        }

        @Override
        public void broadcast(String message, List<Player> exclude, Object... placeholders) {
            List<Player> excluded =
                    (exclude == null) ? new ArrayList<>() : new ArrayList<>(exclude);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!players.containsKey(player) && !excluded.contains(player)) {
                    excluded.add(player);
                }
            }

            super.broadcast(message, excluded, placeholders);
        }

        @Override
        public List<String> getTeams() {
            return new ArrayList<>(teams);
        }

        @Override
        public String getRandomTeam() {
            if (teams.isEmpty()) return "";

            String selectedTeam = null;
            int minCount = Integer.MAX_VALUE;

            for (String team : teams) {
                int count = getTeamPlayers(team).size();
                if (count < minCount) {
                    minCount = count;
                    selectedTeam = team;
                }
            }

            return selectedTeam;
        }

        @Override
        public void setRandomTeam(Player player) {
            setPlayerTeam(player, getRandomTeam());
        }

        @Override
        public List<Player> getTeamPlayers(String team) {
            List<Player> teamPlayers = new ArrayList<>();
            for (Map.Entry<Player, String> entry : players.entrySet()) {
                if (entry.getValue().equals(team)) {
                    teamPlayers.add(entry.getKey());
                }
            }
            return teamPlayers;
        }

        @Override
        public String getPlayerTeam(Player player) {
            return players.getOrDefault(player, null);
        }

        @Override
        public void setPlayerTeam(Player player, String team) {
            if (teams.isEmpty()) return;

            if (players.containsKey(player)  && teams.contains(team)) {
                players.put(player, team);
            }
        }

        @Override
        public void teleport(Player player, Location location) {
            SCPlayer.teleport(player, location);
        }

        @Override
        public void teleportAll(Location location) {
            players.keySet().forEach(player -> SCPlayer.teleport(player, location));
        }

        @Override
        public void teleportTeam(String team, Location location) {
            players.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(team))
                    .forEach(entry -> SCPlayer.teleport(entry.getKey(), location));
        }
    }

    static class MiniGameHudImpl<T> {
        private final List<String> bossBarLines;
        private final List<String> scoreboardLines;
        private final Class<T> dataType;
        private final MiniGameHudProvider<T> provider;

        public MiniGameHudImpl(String namespace, String status, List<String> bossBarLines, List<String> scoreboardLines, Class<T> dataType, MiniGameHudProvider<T> provider) {
            this.bossBarLines = bossBarLines;
            this.scoreboardLines = scoreboardLines;
            this.dataType = dataType;
            this.provider = provider;
        }
    }

    STEMCraft plugin;
    Map<String, List<MiniGameArenaImpl>> arenas = new HashMap<>();
    Map<String, MiniGameHudImpl<?>> huds = new HashMap<>();

    public MiniGameServiceImpl(STEMCraft plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        plugin.tabCompleteService().register("minigame-teams", (sender, args) -> {
            return TeamNames.predefined().stream().toList();
        });

        plugin.taskService().repeating("minigame-countdown", 20, 20, () -> {
            for (MiniGameArenaImpl<?> arena : arenas.values()) {
                if (arena.getCountdown() > 0) {
                    arena.countdown -= 1; // tick down without resetting countdownMax
                    if (arena.countdown == 0) {
                        Bukkit.getPluginManager().callEvent(new ArenaCountdownZeroEvent(arena));
                    }
                }
            }
        });



        plugin.taskService().repeating("minigame-hud", 20, 20, () -> {
            hudTick++;

            for (MiniGameArenaImpl<?> arena : arenas.values()) {
                String hudKey = arenaKey(arena);
                if (hudKey == null) continue;

                MiniGameHudImpl<?> hud = huds.get(hudKey);
                if (hud == null) continue;

                for (Player player : arena.getPlayers()) {
                    if (player == null || !player.isOnline()) continue;

                    Object data = arena.getData();
                    if (!hud.dataType.isInstance(data)) continue;

                    Map<String, String> placeholders = new HashMap<>();

                    // built-ins
                    placeholders.put("arena-name", arena.getName());
                    placeholders.put("arena-countdown", TimeUtil.formatLongDuration(arena.getCountdown()));

                    // provider placeholders
                    Map<String, String> extra = callHudProvider(hud, arena, player);
                    if (extra != null) placeholders.putAll(extra);

                    // bossbar
                    updateBossBar(player, hud, placeholders);

                    // scoreboard
                    updateScoreboard(player, hud, placeholders);
                }
            }
        });
    }

    public void onDisable() {
        // Cleanup boss bars and scoreboards
        for (UUID playerId : new HashSet<>(playerBars.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                cleanupPlayer(player);
            }
        }
    }

    private String arenaKey(MiniGameArenaImpl<?> arena) {
        // arenas are stored as "namespace:id" in the arenas map key; infer namespace by reverse lookup
        for (Map.Entry<String, MiniGameArenaImpl<?>> e : arenas.entrySet()) {
            if (e.getValue() == arena) {
                String ns = e.getKey().split(":", 2)[0];
                return ns + ":" + arena.getStatus();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, String> callHudProvider(MiniGameHudImpl<?> hudRaw,
                                                    MiniGameArenaImpl<?> arenaRaw,
                                                    Player player) {
        MiniGameHudImpl<T> hud = (MiniGameHudImpl<T>) hudRaw;

        // Safe because you already checked hud.dataType.isInstance(arenaRaw.data())
        MiniGameArena<T> arena = (MiniGameArena<T>) arenaRaw;

        return hud.provider.provide(arena, player);
    }

    @Override
    public <T> MiniGameArena<T> addArena(String namespace, String id, String name, World world, List<String> teams, T data) {
        MiniGameArenaImpl<T> arena = new MiniGameArenaImpl<>(namespace, id, name, world, teams, data);
        arenas.put(namespace + ":" + id, arena);
        return arena;
    }

    @Override
    public boolean hasArena(String namespace, String id) {
        return arenas.containsKey(namespace + ":" + id);
    }

    @Override
    public void removeArena(String namespace, String id) {
        arenas.remove(namespace + ":" + id);
    }

    @Override
    public List<String> getArenas(String namespace) {
        List<String> arenaIds = new ArrayList<>();
        String prefix = namespace + ":";
        for (String key : arenas.keySet()) {
            if (key.startsWith(prefix)) {
                String arenaId = key.substring(prefix.length());
                arenaIds.add(arenaId);
            }
        }
        return arenaIds;
    }

    @Override
    public <T> void registerHud(String namespace, String status, List<String> bossBarLines, List<String> scoreboardLines, Class<T> dataType, MiniGameHudProvider<T> provider) {
        huds.put(namespace + ":" + status, new MiniGameHudImpl<>(namespace, status, bossBarLines, scoreboardLines, dataType, provider));
    }

    private final Map<UUID, BossBar> playerBars = new HashMap<>();
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private final Map<UUID, Objective> playerObjectives = new HashMap<>();

    private long hudTick = 0L;

    @Override
    public String getPlayerArenaNamespace(Player player) {
        for (Map.Entry<String, MiniGameArenaImpl<?>> e : arenas.entrySet()) {
            MiniGameArenaImpl<?> arena = e.getValue();
            if (arena.hasPlayer(player)) {
                String ns = e.getKey().split(":", 2)[0];
                return ns;
            }
        }
        return null;
    }

    @Override
    public MiniGameArena getPlayerArena(String namespace, Player player) {
        if(arenas.containsKey(namespace)) {
            for (MiniGameArenaImpl arena : arenas.get(namespace)) {
                if (arena.hasPlayer(player)) {
                    return arena;
                }
            }
        }

        return null;
    }



    @Override
    public void addPlayer(Player p, String namespace, String arenaId) {
        MiniGameArenaImpl<?> arena = arenas.get(namespace + ":" + arenaId);
        if (arena == null) {
            throw new IllegalArgumentException("Arena " + arenaId + " in namespace " + namespace + " does not exist.");
        }

        addPlayer(p, arena);
    }

    @Override
    public void addPlayer(Player player, MiniGameArena<?> arena) {
        arena.addPlayer(player);
    }

    public void removePlayer(Player p) {
        arenas.forEach((key, arena) -> {
            if(arena.hasPlayer(p)) {
                arena.removePlayer(p);
            }
        });
    }

    private void updateBossBar(Player player, MiniGameHudImpl<?> hud, Map<String, String> placeholders) {
        BossBar bar = playerBars.get(player.getUniqueId());
        if (bar == null) return;

        // Rotate title every 7 seconds if multiple lines exist
        List<String> lines = hud.bossBarLines;
        String raw;
        if (lines == null || lines.isEmpty()) {
            raw = "";
        } else if (lines.size() == 1) {
            raw = lines.get(0);
        } else {
            long idx = (hudTick / 7L) % lines.size();
            raw = lines.get((int) idx);
        }

        String title = applyPlaceholders(raw, placeholders);
        bar.setTitle(title);
        bar.setVisible(true);

        // Progress: default uses arena countdown, counting UP (0 -> 1)
        // Provider can override by setting placeholders: bossbar_val and bossbar_max
        double progress = 1.0;
        try {
            String v = placeholders.get("bossbar_val");
            String m = placeholders.get("bossbar_max");
            if (v != null && m != null) {
                double val = Double.parseDouble(v);
                double max = Double.parseDouble(m);
                if (max > 0) progress = val / max;
            }
        } catch (NumberFormatException ignored) {
            // fall back to arena countdown progress below
            progress = Double.NaN;
        }

        if (Double.isNaN(progress)) {
            // leave for arena-based calculation below
            progress = 1.0;
        }

        // If provider did not override, compute from arena countdown.
        // We expect placeholders to include arena-countdown but we need raw numbers, so derive from arena if possible.
        // Use arena countdownMax/countdown if the arena is the player's current arena.
        MiniGameArenaImpl<?> arena = null;
        // Find arena by scanning current arenas for membership
        for (MiniGameArenaImpl<?> a : arenas.values()) {
            if (a.getPlayers().contains(player)) {
                arena = a;
                break;
            }
        }
        if (arena != null && (placeholders.get("bossbar_val") == null || placeholders.get("bossbar_max") == null)) {
            int max = Math.max(0, arena.countdownMax);
            int cur = Math.max(0, arena.countdown);
            if (max <= 0) {
                progress = 1.0;
            } else {
                // countdown counts DOWN, but progress should count UP
                progress = (double) (max - cur) / (double) max;
            }
        }

        if (progress < 0.0) progress = 0.0;
        if (progress > 1.0) progress = 1.0;
        bar.setProgress(progress);
    }

    private void updateScoreboard(Player player, MiniGameHudImpl<?> hud, Map<String, String> placeholders) {
        Scoreboard sb = playerBoards.get(player.getUniqueId());
        Objective obj = playerObjectives.get(player.getUniqueId());
        if (sb == null || obj == null) return;

        List<String> rendered = renderScoreboardLines(hud.scoreboardLines, placeholders);

        // sidebar practical limit
        if (rendered.size() > 15) rendered = rendered.subList(0, 15);

        // clear previous entries
        for (String entry : new HashSet<>(sb.getEntries())) sb.resetScores(entry);

        // scoreboard ordering uses scores, numbers cannot truly be hidden in vanilla
        int score = rendered.size();
        Set<String> used = new HashSet<>();
        for (String line : rendered) {
            String entry = uniqueEntry(trimEntry(line), used);
            used.add(entry);
            obj.getScore(entry).setScore(score--);
        }

        if (player.getScoreboard() != sb) player.setScoreboard(sb);
    }

    private static final Pattern WHOLE_PLACEHOLDER =
            Pattern.compile("^\\{([a-zA-Z0-9\\-_.]+)}$");

    private static List<String> renderScoreboardLines(List<String> template, Map<String, String> values) {
        List<String> out = new ArrayList<>();
        for (String line : template) {
            if (line == null) continue;

            String trimmed = line.trim();
            Matcher m = WHOLE_PLACEHOLDER.matcher(trimmed);

            // if the entire line is just {key} and key missing -> drop line
            if (m.matches()) {
                String key = m.group(1);
                String v = values.get(key);
                if (v == null || v.isEmpty()) continue;
                out.add(colorize(v));
                continue;
            }

            out.add(applyPlaceholders(line, values));
        }
        return out;
    }

    private static String applyPlaceholders(String s, Map<String, String> map) {
        if (s == null || s.isEmpty() || map == null || map.isEmpty()) return colorize(s);
        String out = s;
        for (var e : map.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return colorize(out);
    }

    private static String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static String trimEntry(String s) {
        if (s == null) return "";
        // scoreboard entry limit is tight; keep it safe
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static String uniqueEntry(String base, Set<String> used) {
        if (!used.contains(base)) return base;

        // add invisible-ish uniqueness using color codes
        for (ChatColor c : ChatColor.values()) {
            String candidate = trimEntry(base + c);
            if (!used.contains(candidate)) return candidate;
        }

        return UUID.randomUUID().toString().substring(0, 16);
    }

    private void setupPlayer(Player player) {
        // BossBar
        BossBar bar = playerBars.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        bar.setVisible(true);

        // Scoreboard
        Scoreboard sb = playerBoards.computeIfAbsent(player.getUniqueId(), id -> {
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            return mgr.getNewScoreboard();
        });

        Objective obj = sb.getObjective("minigame");
        if (obj == null) {
            obj = sb.registerNewObjective("minigame", "dummy", "");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        playerObjectives.put(player.getUniqueId(), obj);

        player.setScoreboard(sb);
    }

    private void cleanupPlayer(Player player) {
        UUID id = player.getUniqueId();

        BossBar bar = playerBars.remove(id);
        if (bar != null) {
            bar.removePlayer(player);
            if (bar.getPlayers().isEmpty()) bar.setVisible(false);
        }

        playerObjectives.remove(id);
        playerBoards.remove(id);

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        player.setScoreboard(mgr.getMainScoreboard());
    }
}

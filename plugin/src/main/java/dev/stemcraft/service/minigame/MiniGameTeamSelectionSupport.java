package dev.stemcraft.service.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.minigame.MiniGameTeamSelectionInput;
import dev.stemcraft.api.minigame.MiniGameTeamSelectionPolicy;
import dev.stemcraft.api.minigame.util.TeamNames;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class MiniGameTeamSelectionSupport {
    private static final int FLOOR_SCAN_DEPTH = 5;
    private static final int HOTBAR_TEAM_SLOT_START = 0;
    private static final int HOTBAR_AUTO_SLOT = 8;
    private static final int HOTBAR_MAX_TEAM_SLOTS = 8;
    private static final String FLOOR_SELECTOR_CONFIG_PATH = "minigames.team-selection.floor";
    private static final String LOBBY_TEAM_ORDER_KEY = "teamSelectionLobbyOrder";
    public static final String CONFIG_LOBBY_REGION_PATH = "lobby-region";
    public static final String CONFIG_INPUT_PATH = "team-selection.input";
    public static final String CONFIG_INPUTS_PATH = "team-selection.inputs";
    public static final String LEGACY_CONFIG_FLOOR_ENABLED_PATH = "team-floor-selection.enabled";
    public static final String TEAM_SELECTION_PREFERENCE_KEY = "teamSelectionPreference";

    private final STEMCraftAPI api;
    private MiniGameServiceImpl service;
    private Map<String, Set<Material>> floorSelectorMaterials = Map.of();

    MiniGameTeamSelectionSupport(@NotNull STEMCraftAPI api) {
        this.api = api;
        reloadConfig();
        registerListeners();
    }

    void attachService(@NotNull MiniGameServiceImpl service) {
        this.service = service;
    }

    void reloadConfig() {
        floorSelectorMaterials = loadFloorSelectorMaterials();
    }

    public void registerPlaceholders(@NotNull MiniGame minigame) {
        minigame.registerArenaPlaceholder("auto-selected-count", (arena, team, player) -> arena == null ? "0" : String.valueOf(autoSelectedCount(arena)));
        minigame.registerArenaPlaceholder("team-selection-auto-count", (arena, team, player) -> arena == null ? "0" : String.valueOf(autoSelectedCount(arena)));
        minigame.registerArenaPlaceholder("min-teams", (arena, team, player) -> arena == null ? "0" : String.valueOf(minTeams(arena)));
        minigame.registerArenaPlaceholder("active-teams", (arena, team, player) -> arena == null ? "0" : String.valueOf(activeTeams(arena)));
        minigame.registerArenaPlaceholder("max-teams", (arena, team, player) -> arena == null ? "0" : String.valueOf(maxTeams(arena)));
        minigame.registerPlayerPlaceholder("selected-team", (arena, team, player) -> renderSelectedTeam(arena, player));
        minigame.registerPlayerPlaceholder("team-selection", (arena, team, player) -> renderSelectedTeam(arena, player));
        for (int i = 0; i < HOTBAR_MAX_TEAM_SLOTS; i++) {
            final int index = i;
            minigame.registerArenaPlaceholder("lobby-team-line-" + (i + 1), (arena, team, player) -> renderLobbyTeamLine(arena, player, index));
            minigame.registerArenaPlaceholder("team-selection-line-" + (i + 1), (arena, team, player) -> renderLobbyTeamLine(arena, player, index));
        }
    }

    public static @NotNull MiniGameArena applyArenaDefaults(@NotNull MiniGameArena arena) {
        return arena
            .setLobbyRegion(null)
            .setTeamSelectionInput(null);
    }

    public static @Nullable String serializeInput(@Nullable MiniGameTeamSelectionInput input) {
        return input == null ? null : input.configToken();
    }

    public static @Nullable MiniGameTeamSelectionInput parseInput(@Nullable String token) {
        return MiniGameTeamSelectionInput.fromToken(token);
    }

    public boolean usesTeamSelection(@NotNull MiniGameArena arena) {
        MiniGameTeamSelectionPolicy policy = policy(arena);
        return policy != null && !selectableTeams(arena).isEmpty();
    }

    public void validateArenaSetup(@NotNull MiniGameArena arena, @NotNull ArenaValidationResult result) {
        MiniGameTeamSelectionPolicy policy = policy(arena);
        if (policy == null) {
            return;
        }

        List<MiniGameTeam> teams = selectableTeams(arena);
        if (teams.isEmpty()) {
            return;
        }

        MiniGameTeamSelectionInput enabledInput = arena.getTeamSelectionInput();
        Set<MiniGameTeamSelectionInput> supportedInputs = policy.supportedInputs(arena);
        if (enabledInput != null && !supportedInputs.contains(enabledInput)) {
            result.addError("Team selection input '" + enabledInput.configToken() + "' is not supported by this minigame.", MiniGameArena.TEAM_SELECTION_INPUT_META_KEY);
        }

        if (enabledInput == MiniGameTeamSelectionInput.FLOOR) {
            validateFloorSelection(arena, policy, teams, result);
        }

        if (enabledInput == MiniGameTeamSelectionInput.HOTBAR && teams.size() > HOTBAR_MAX_TEAM_SLOTS) {
            result.addError("Hotbar team selection supports at most " + HOTBAR_MAX_TEAM_SLOTS + " teams.", MiniGameArena.TEAM_SELECTION_INPUT_META_KEY);
        }
    }

    public void onPlayerJoinedArena(@NotNull MiniGameArena arena, @NotNull Player player) {
        if (!usesTeamSelection(arena)) {
            return;
        }

        updateFloorSelection(arena, player, player.getLocation(), true);
        reevaluateArena(arena);
        syncLobbySelectorInventory(arena, player);
    }

    public void onPlayerLeftArena(@NotNull MiniGameArena arena) {
        if (!usesTeamSelection(arena)) {
            return;
        }

        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING || arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            reevaluateArena(arena);
        }
    }

    public void prepareArenaStart(@NotNull MiniGameArena arena) {
        if (!usesTeamSelection(arena)) {
            return;
        }

        applyAssignments(arena);
        clearLobbySelectors(arena);
    }

    public void onArenaStatusChanged(@NotNull MiniGameArena arena,
                                     @Nullable MiniGameArena.ArenaStatus oldStatus,
                                     @NotNull MiniGameArena.ArenaStatus newStatus) {
        if (!usesTeamSelection(arena)) {
            return;
        }

        boolean lobbyPhase = newStatus == MiniGameArena.ArenaStatus.WAITING || newStatus == MiniGameArena.ArenaStatus.STARTING;
        if (lobbyPhase) {
            reevaluateArena(arena);
            syncLobbySelectorInventories(arena);
            refreshArenaHud(arena);
            return;
        }

        if (oldStatus == MiniGameArena.ArenaStatus.WAITING || oldStatus == MiniGameArena.ArenaStatus.STARTING) {
            clearLobbySelectors(arena);
        }
        refreshArenaHud(arena);
    }

    public void tickArena(@NotNull MiniGameArena arena) {
        if (!usesTeamSelection(arena)) {
            return;
        }

        MiniGameArena.ArenaStatus status = arena.getStatus();
        if (status != MiniGameArena.ArenaStatus.WAITING && status != MiniGameArena.ArenaStatus.STARTING) {
            return;
        }

        boolean changed = false;
        if (inputEnabled(arena, MiniGameTeamSelectionInput.FLOOR)) {
            for (Player player : arena.getPlayers()) {
                changed |= updateFloorSelection(arena, player, player.getLocation(), false);
            }
        }

        if (changed || status == MiniGameArena.ArenaStatus.STARTING) {
            reevaluateArena(arena);
        }
    }

    public int autoSelectedCount(@NotNull MiniGameArena arena) {
        int count = 0;
        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer == null) {
                continue;
            }

            String selection = preference(mgPlayer);
            if (!isExplicitSelection(selection)) {
                count++;
            }
        }
        return count;
    }

    public int minTeams(@NotNull MiniGameArena arena) {
        if (arena.getTeams().isEmpty()) {
            return 0;
        }
        return Math.max(1, requiredActiveTeams(arena));
    }

    public int activeTeams(@NotNull MiniGameArena arena) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING || arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            Map<Player, String> assignments = buildAssignments(arena, preferences(arena));
            return (int) assignments.values().stream().distinct().count();
        }

        return (int) arena.getTeams().stream()
            .filter(team -> !arena.getTeamPlayers(team.getName()).isEmpty())
            .count();
    }

    public int maxTeams(@NotNull MiniGameArena arena) {
        return arena.getTeams().size();
    }

    public @NotNull String renderSelectedTeam(@Nullable MiniGameArena arena, @Nullable MiniGamePlayer player) {
        if (player == null) {
            return "<gray>Auto</gray>";
        }

        String selection = preference(player);
        if (!isExplicitSelection(selection)) {
            return "<gray>Auto</gray>";
        }

        MiniGameTeam team = arena == null ? null : arena.getTeam(selection);
        return team == null
            ? "<gray>" + beautifyTeamName(selection) + "</gray>"
            : renderTeamLabel(arena, team);
    }

    public @NotNull String renderLobbyTeamLine(@Nullable MiniGameArena arena, @Nullable MiniGamePlayer viewer, int index) {
        if (arena == null) {
            return "";
        }

        List<MiniGameTeam> teams = visibleLobbyTeams(arena);
        if (index >= teams.size()) {
            return "";
        }

        MiniGameTeam team = teams.get(index);
        int players = arena.getTeamPlayers(team.getName()).size();
        int capacity = Math.max(1, teamCapacity(arena, team));
        boolean viewerTeam = viewer != null
            && team.getName().equalsIgnoreCase(currentLobbyViewerTeam(arena, viewer));
        MiniGameTeamSelectionPolicy policy = policy(arena);
        return policy == null
            ? MiniGameTeamSelectionPolicy.defaultTeamLabel(team) + " <gray>(" + players + "/" + capacity + ")</gray>"
            : policy.renderLobbyTeamLine(arena, team, players, capacity, viewerTeam);
    }

    private void registerListeners() {
        api.events().register(PlayerMoveEvent.class, event -> {
            MiniGameServiceImpl service = this.service;
            if (service == null) {
                return;
            }
            Location to = event.getTo();
            if (to == null || samePosition(event.getFrom(), to)) {
                return;
            }

            MiniGameArenaImpl arena = service.findParticipantArena(event.getPlayer());
            if (arena == null || !inputEnabled(arena, MiniGameTeamSelectionInput.FLOOR)) {
                return;
            }

            MiniGameArena.ArenaStatus status = arena.getStatus();
            if (status != MiniGameArena.ArenaStatus.WAITING && status != MiniGameArena.ArenaStatus.STARTING) {
                return;
            }

            boolean changed = updateFloorSelection(arena, event.getPlayer(), to, false);
            if (changed || status == MiniGameArena.ArenaStatus.STARTING) {
                reevaluateArena(arena);
            }
        }, EventPriority.MONITOR, false);

        api.events().register(PlayerInteractEvent.class, event -> {
            MiniGameServiceImpl service = this.service;
            if (service == null) {
                return;
            }
            if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_AIR
                && event.getAction() != Action.LEFT_CLICK_BLOCK) {
                return;
            }

            Player player = event.getPlayer();
            MiniGameArenaImpl arena = service.findParticipantArena(player);
            if (arena == null || !inputEnabled(arena, MiniGameTeamSelectionInput.HOTBAR)) {
                return;
            }

            MiniGameArena.ArenaStatus status = arena.getStatus();
            if (status != MiniGameArena.ArenaStatus.WAITING && status != MiniGameArena.ArenaStatus.STARTING) {
                return;
            }

            String selection = selectionForHotbarSlot(arena, player.getInventory().getHeldItemSlot());
            if (selection == null) {
                return;
            }

            setPreference(arena, player, selection);
            syncLobbySelectorInventory(arena, player);
            reevaluateArena(arena);
            event.setCancelled(true);
        }, EventPriority.NORMAL, false);

        api.events().register(InventoryClickEvent.class, event -> {
            MiniGameServiceImpl service = this.service;
            if (service == null) {
                return;
            }
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            MiniGameArenaImpl arena = service.findParticipantArena(player);
            if (arena == null || !inputEnabled(arena, MiniGameTeamSelectionInput.HOTBAR)) {
                return;
            }

            MiniGameArena.ArenaStatus status = arena.getStatus();
            if (status != MiniGameArena.ArenaStatus.WAITING && status != MiniGameArena.ArenaStatus.STARTING) {
                return;
            }

            if (event.getClickedInventory() == player.getInventory()) {
                event.setCancelled(true);
            }
        }, EventPriority.NORMAL, false);
    }

    private void validateFloorSelection(@NotNull MiniGameArena arena,
                                        @NotNull MiniGameTeamSelectionPolicy policy,
                                        @NotNull List<MiniGameTeam> teams,
                                        @NotNull ArenaValidationResult result) {
        SCRegion lobbyRegion = arena.getLobbyRegion();
        if (lobbyRegion == null) {
            result.addError("Lobby region is required when floor team selection is enabled.", MiniGameArena.LOBBY_REGION_META_KEY);
            return;
        }

        if (lobbyRegion.getWorld() == null || !arena.world().equals(lobbyRegion.getWorld())) {
            result.addError("Lobby region must be in the arena world.", MiniGameArena.LOBBY_REGION_META_KEY);
            return;
        }

        if (arena.getRegion() != null && !arena.getRegion().contains(lobbyRegion)) {
            result.addError("Lobby region must be inside the arena region.", MiniGameArena.LOBBY_REGION_META_KEY);
        }

        Map<String, Boolean> coverage = new LinkedHashMap<>();
        for (MiniGameTeam team : teams) {
            coverage.put(normalizeTeamId(team.getName()), false);
        }

        forEachRegionBlock(lobbyRegion, block -> {
            Material material = block.getType();
            for (MiniGameTeam team : teams) {
                String teamId = normalizeTeamId(team.getName());
                if (!coverage.getOrDefault(teamId, false)
                    && selectorMaterials(arena, team).contains(material)) {
                    coverage.put(teamId, true);
                }
            }
        });

        for (MiniGameTeam team : teams) {
            if (!coverage.getOrDefault(normalizeTeamId(team.getName()), false)) {
                result.addError("Lobby region is missing selector blocks for team '" + team.getName() + "'.", MiniGameArena.LOBBY_REGION_META_KEY);
            }
        }
    }

    private void reevaluateArena(@NotNull MiniGameArena arena) {
        if (!usesTeamSelection(arena)) {
            return;
        }

        applyAssignments(arena);
        syncLobbySelectorInventories(arena);

        boolean startReady = canStart(arena);
        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && startReady) {
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, startCountdownSeconds(arena));
            refreshArenaHud(arena);
            return;
        }

        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING && !startReady) {
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            arena.setCountdown(0);
            int requiredTeams = Math.max(1, requiredActiveTeams(arena));
            if (requiredTeams > 1) {
                arena.broadcast("<yellow>Need players across at least " + requiredTeams + " teams before the game can start.</yellow>");
            }
            refreshArenaHud(arena);
            return;
        }

        refreshArenaHud(arena);
    }

    private boolean canStart(@NotNull MiniGameArena arena) {
        if (arena.numPlayers() < arena.getMinPlayers()) {
            return false;
        }

        Map<Player, String> preferences = preferences(arena);
        if (requiredActiveTeams(arena) > 1 && allPlayersSelectedSameExplicitTeam(preferences, arena.numPlayers())) {
            return false;
        }

        Map<Player, String> assignments = buildAssignments(arena, preferences);
        if (assignments.size() < arena.numPlayers()) {
            return false;
        }

        return new LinkedHashSet<>(assignments.values()).size() >= Math.max(1, requiredActiveTeams(arena));
    }

    private void applyAssignments(@NotNull MiniGameArena arena) {
        Map<Player, String> assignments = buildAssignments(arena, preferences(arena));
        for (Map.Entry<Player, String> entry : assignments.entrySet()) {
            Player player = entry.getKey();
            String teamId = entry.getValue();
            MiniGameTeam current = arena.getPlayerTeam(player);
            if (current == null || !current.getName().equalsIgnoreCase(teamId)) {
                arena.setPlayerTeam(player, teamId);
            }
        }
    }

    private @NotNull Map<Player, String> buildAssignments(@NotNull MiniGameArena arena,
                                                           @NotNull Map<Player, String> preferences) {
        List<MiniGameTeam> teams = assignableTeams(arena, preferences(arena));
        Map<String, Integer> occupancy = new LinkedHashMap<>();
        Map<String, Integer> capacity = new LinkedHashMap<>();
        for (MiniGameTeam team : teams) {
            occupancy.put(team.getName(), 0);
            capacity.put(team.getName(), Math.max(1, teamCapacity(arena, team)));
        }

        Map<Player, String> assignments = new LinkedHashMap<>();
        for (Player player : arena.getPlayers()) {
            String preferredTeam = preferences.getOrDefault(player, TeamNames.TEAM_AUTO);
            if (!isExplicitSelection(preferredTeam)) {
                continue;
            }

            Integer current = occupancy.get(preferredTeam);
            Integer max = capacity.get(preferredTeam);
            if (current != null && max != null && current < max) {
                assignments.put(player, preferredTeam);
                occupancy.put(preferredTeam, current + 1);
            }
        }

        for (Player player : arena.getPlayers()) {
            if (assignments.containsKey(player)) {
                continue;
            }

            String preferredTeam = preferences.getOrDefault(player, TeamNames.TEAM_AUTO);
            if (isExplicitSelection(preferredTeam)) {
                continue;
            }

            String teamId = selectLeastFilledTeam(occupancy, capacity);
            if (teamId == null && !teams.isEmpty()) {
                teamId = teams.getFirst().getName();
            }
            if (teamId != null) {
                assignments.put(player, teamId);
                occupancy.computeIfPresent(teamId, (ignored, count) -> count + 1);
            }
        }

        return assignments;
    }

    private boolean updateFloorSelection(@NotNull MiniGameArena arena,
                                         @NotNull Player player,
                                         @NotNull Location location,
                                         boolean force) {
        if (!inputEnabled(arena, MiniGameTeamSelectionInput.FLOOR)) {
            return false;
        }

        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer == null) {
            return false;
        }

        String previous = preference(mgPlayer);
        String resolved = resolveFloorSelection(arena, location, previous);
        boolean changed = !Objects.equals(previous, resolved);
        if (!force && !changed) {
            return false;
        }
        if (changed) {
            setPreference(arena, player, resolved);
        } else {
            mgPlayer.set(TEAM_SELECTION_PREFERENCE_KEY, resolved);
        }
        return changed;
    }

    private @NotNull String resolveFloorSelection(@NotNull MiniGameArena arena,
                                                  @NotNull Location location,
                                                  @Nullable String previous) {
        if (location.getWorld() == null) {
            return previous == null ? TeamNames.TEAM_AUTO : previous;
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = location.getBlockY() - 1;
        boolean sawAir = false;
        SCRegion lobbyRegion = arena.getLobbyRegion();
        for (int depth = 0; depth < FLOOR_SCAN_DEPTH; depth++) {
            org.bukkit.block.Block block = location.getWorld().getBlockAt(x, startY - depth, z);
            Material material = block.getType();
            if (material.isAir()) {
                sawAir = true;
                continue;
            }

            if (lobbyRegion != null && !lobbyRegion.contains(block.getLocation())) {
                return TeamNames.TEAM_AUTO;
            }

            String teamId = teamForSelectorMaterial(arena, material);
            return teamId == null ? TeamNames.TEAM_AUTO : teamId;
        }

        if (sawAir && previous != null && !previous.isBlank()) {
            return previous;
        }
        return TeamNames.TEAM_AUTO;
    }

    private void syncLobbySelectorInventories(@NotNull MiniGameArena arena) {
        for (Player player : arena.getPlayers()) {
            syncLobbySelectorInventory(arena, player);
        }
    }

    private void syncLobbySelectorInventory(@NotNull MiniGameArena arena, @NotNull Player player) {
        if (!inputEnabled(arena, MiniGameTeamSelectionInput.HOTBAR)
            || (arena.getStatus() != MiniGameArena.ArenaStatus.WAITING && arena.getStatus() != MiniGameArena.ArenaStatus.STARTING)) {
            return;
        }

        List<MiniGameTeam> teams = selectableTeams(arena);
        String selection = preference(arena.getPlayer(player));
        for (int i = 0; i < HOTBAR_MAX_TEAM_SLOTS; i++) {
            player.getInventory().setItem(i, null);
        }

        for (int i = 0; i < teams.size() && i < HOTBAR_MAX_TEAM_SLOTS; i++) {
            MiniGameTeam team = teams.get(i);
            boolean selected = team.getName().equalsIgnoreCase(selection);
            player.getInventory().setItem(HOTBAR_TEAM_SLOT_START + i, createSelectorItem(team, selected));
        }
        player.getInventory().setItem(HOTBAR_AUTO_SLOT, createAutoSelectorItem(!isExplicitSelection(selection)));
        player.updateInventory();
    }

    private void clearLobbySelectors(@NotNull MiniGameArena arena) {
        if (!inputEnabled(arena, MiniGameTeamSelectionInput.HOTBAR)) {
            return;
        }

        for (Player player : arena.getPlayers()) {
            for (int slot = HOTBAR_TEAM_SLOT_START; slot <= HOTBAR_AUTO_SLOT; slot++) {
                player.getInventory().setItem(slot, null);
            }
            player.updateInventory();
        }
    }

    private @Nullable String selectionForHotbarSlot(@NotNull MiniGameArena arena, int slot) {
        if (slot == HOTBAR_AUTO_SLOT) {
            return TeamNames.TEAM_AUTO;
        }

        int teamIndex = slot - HOTBAR_TEAM_SLOT_START;
        if (teamIndex < 0) {
            return null;
        }

        List<MiniGameTeam> teams = selectableTeams(arena);
        return teamIndex >= teams.size() ? null : teams.get(teamIndex).getName();
    }

    private @Nullable String teamForSelectorMaterial(@NotNull MiniGameArena arena, @NotNull Material material) {
        MiniGameTeamSelectionPolicy policy = policy(arena);
        if (policy == null) {
            return null;
        }

        for (MiniGameTeam team : selectableTeams(arena)) {
            if (selectorMaterials(arena, team).contains(material)) {
                return team.getName();
            }
        }
        return null;
    }

    private @NotNull Map<Player, String> preferences(@NotNull MiniGameArena arena) {
        Map<Player, String> preferences = new LinkedHashMap<>();
        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer != null) {
                preferences.put(player, preference(mgPlayer));
            }
        }
        return preferences;
    }

    private void setPreference(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull String teamId) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer == null) {
            return;
        }
        mgPlayer.set(TEAM_SELECTION_PREFERENCE_KEY, normalizeSelection(teamId));
        player.sendActionBar(TextUtil.colourise("<gray>Team selection:</gray> " + renderSelectedTeam(arena, mgPlayer)));
    }

    private void refreshArenaHud(@NotNull MiniGameArena arena) {
        MiniGameServiceImpl service = requireService();
        MiniGameImpl minigame = service.getMiniGameImpl(arena.namespace());
        if (minigame == null) {
            return;
        }

        MiniGameHUD hud = minigame.getArenaActiveHUD(arena);
        for (Player player : arena.getOccupants()) {
            MiniGamePlayerImpl miniGamePlayer = ((MiniGameArenaImpl) arena).occupantProfile(player);
            if (miniGamePlayer == null) {
                continue;
            }

            if (hud == null) {
                miniGamePlayer.hudDispose();
                continue;
            }

            miniGamePlayer.hudUpdate(
                hud.bossbar(miniGamePlayer),
                hud.bossbarColor(miniGamePlayer),
                hud.scoreboard(miniGamePlayer)
            );
        }
    }

    private boolean allPlayersSelectedSameExplicitTeam(@NotNull Map<Player, String> preferences, int expectedPlayers) {
        if (preferences.size() != expectedPlayers || preferences.isEmpty()) {
            return false;
        }

        Set<String> teams = new LinkedHashSet<>();
        for (String selection : preferences.values()) {
            if (!isExplicitSelection(selection)) {
                return false;
            }
            teams.add(normalizeSelection(selection));
        }
        return teams.size() == 1;
    }

    private @Nullable String selectLeastFilledTeam(@NotNull Map<String, Integer> occupancy,
                                                   @NotNull Map<String, Integer> capacity) {
        return occupancy.entrySet().stream()
            .filter(entry -> entry.getValue() < capacity.getOrDefault(entry.getKey(), 0))
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private boolean inputEnabled(@NotNull MiniGameArena arena, @NotNull MiniGameTeamSelectionInput input) {
        return arena.getTeamSelectionInput() == input;
    }

    private int startCountdownSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("startCountdownSeconds", Integer.class, 30));
    }

    private int teamCapacity(@NotNull MiniGameArena arena, @NotNull MiniGameTeam team) {
        MiniGameTeamSelectionPolicy policy = policy(arena);
        return policy == null ? Math.max(1, arena.getMaxPlayers()) : Math.max(1, policy.teamCapacity(arena, team));
    }

    private int requiredActiveTeams(@NotNull MiniGameArena arena) {
        MiniGameTeamSelectionPolicy policy = policy(arena);
        return policy == null ? 1 : Math.max(1, policy.requiredActiveTeams(arena));
    }

    private @NotNull List<MiniGameTeam> assignableTeams(@NotNull MiniGameArena arena, @NotNull Map<Player, String> preferences) {
        MiniGameTeamSelectionPolicy policy = policy(arena);
        if (policy == null) {
            return List.of();
        }
        return new ArrayList<>(policy.assignableTeams(arena, preferences));
    }

    private @NotNull List<MiniGameTeam> selectableTeams(@NotNull MiniGameArena arena) {
        MiniGameTeamSelectionPolicy policy = policy(arena);
        if (policy == null) {
            return List.of();
        }
        return new ArrayList<>(policy.selectableTeams(arena));
    }

    private @NotNull List<MiniGameTeam> visibleLobbyTeams(@NotNull MiniGameArena arena) {
        List<MiniGameTeam> teams = assignableTeams(arena, preferences(arena));
        if (teams.isEmpty()) {
            arena.remove(LOBBY_TEAM_ORDER_KEY);
            return List.of();
        }

        List<String> currentOrder = teams.stream()
            .map(MiniGameTeam::getName)
            .map(this::normalizeTeamId)
            .toList();
        List<String> previousVisible = new ArrayList<>(arena.getList(LOBBY_TEAM_ORDER_KEY, String.class, List.of()));

        Map<String, MiniGameTeam> teamsById = new LinkedHashMap<>();
        for (MiniGameTeam team : teams) {
            teamsById.put(normalizeTeamId(team.getName()), team);
        }

        Set<String> occupied = new LinkedHashSet<>();
        for (MiniGameTeam team : teams) {
            if (!arena.getTeamPlayers(team.getName()).isEmpty()) {
                occupied.add(normalizeTeamId(team.getName()));
            }
        }

        int requiredVisible = Math.min(teams.size(), Math.max(requiredActiveTeams(arena), occupied.size()));
        List<String> visibleOrder = new ArrayList<>();
        for (String teamId : previousVisible) {
            String normalized = normalizeTeamId(teamId);
            if (currentOrder.contains(normalized) && !visibleOrder.contains(normalized)) {
                visibleOrder.add(normalized);
            }
        }

        for (String teamId : currentOrder) {
            if (occupied.contains(teamId) && !visibleOrder.contains(teamId)) {
                visibleOrder.add(teamId);
            }
        }

        for (String teamId : currentOrder) {
            if (!visibleOrder.contains(teamId)) {
                visibleOrder.add(teamId);
            }
            if (visibleOrder.size() >= requiredVisible) {
                break;
            }
        }

        if (visibleOrder.size() > requiredVisible) {
            visibleOrder = new ArrayList<>(visibleOrder.subList(0, requiredVisible));
        }
        arena.set(LOBBY_TEAM_ORDER_KEY, visibleOrder);

        List<MiniGameTeam> visible = new ArrayList<>();
        for (String teamId : visibleOrder) {
            MiniGameTeam team = teamsById.get(teamId);
            if (team != null) {
                visible.add(team);
            }
        }
        return visible;
    }

    private @Nullable MiniGameTeamSelectionPolicy policy(@NotNull MiniGameArena arena) {
        MiniGameServiceImpl service = requireService();
        MiniGameImpl minigame = service.getMiniGameImpl(arena.namespace());
        return minigame == null ? null : minigame.getTeamSelectionPolicy();
    }

    private @NotNull MiniGameServiceImpl requireService() {
        if (service == null) {
            throw new IllegalStateException("MiniGameTeamSelectionSupport service not attached");
        }
        return service;
    }

    private @Nullable String currentLobbyViewerTeam(@NotNull MiniGameArena arena, @NotNull MiniGamePlayer viewer) {
        String selected = preference(viewer);
        if (isExplicitSelection(selected)) {
            return selected;
        }

        Player player = viewer.getPlayer();
        MiniGameTeam assignedTeam = arena.getPlayerTeam(player);
        return assignedTeam == null ? null : assignedTeam.getName();
    }

    private @NotNull Set<Material> selectorMaterials(@NotNull MiniGameArena arena, @NotNull MiniGameTeam team) {
        String teamId = normalizeTeamId(team.getName());
        Set<Material> configured = floorSelectorMaterials.get(teamId);
        if (configured != null) {
            return configured;
        }

        MiniGameTeamSelectionPolicy policy = policy(arena);
        return policy == null ? Set.of() : policy.selectorMaterials(arena, team);
    }

    private @NotNull String preference(@Nullable MiniGamePlayer player) {
        if (player == null) {
            return TeamNames.TEAM_AUTO;
        }

        return normalizeSelection(player.get(TEAM_SELECTION_PREFERENCE_KEY, String.class, TeamNames.TEAM_AUTO));
    }

    private @NotNull String normalizeSelection(@Nullable String selection) {
        return selection == null || selection.isBlank() ? TeamNames.TEAM_AUTO : selection.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isExplicitSelection(@Nullable String selection) {
        return selection != null && !selection.isBlank() && !TeamNames.TEAM_AUTO.equalsIgnoreCase(selection);
    }

    private @NotNull String renderTeamLabel(@NotNull MiniGameArena arena, @NotNull MiniGameTeam team) {
        MiniGameTeamSelectionPolicy policy = policy(arena);
        return policy == null ? MiniGameTeamSelectionPolicy.defaultTeamLabel(team) : policy.renderTeamLabel(arena, team);
    }

    private @NotNull ItemStack createSelectorItem(@NotNull MiniGameTeam team, boolean selected) {
        ItemStack item = new ItemStack(TeamNames.getMaterial(team.getName()));
        ItemMeta meta = item.getItemMeta();
        String prefix = selected ? "Selected: " : "Select: ";
        meta.setDisplayName(prefix + beautifyTeamName(team.get("displayName", String.class, team.getName())));
        item.setItemMeta(meta);
        return item;
    }

    private @NotNull ItemStack createAutoSelectorItem(boolean selected) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(selected ? "Selected: Auto" : "Select: Auto");
        item.setItemMeta(meta);
        return item;
    }

    private void forEachRegionBlock(@NotNull SCRegion region, @NotNull java.util.function.Consumer<org.bukkit.block.Block> consumer) {
        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();
        if (min.getWorld() == null) {
            return;
        }

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    org.bukkit.block.Block block = min.getWorld().getBlockAt(x, y, z);
                    if (region.contains(block.getLocation())) {
                        consumer.accept(block);
                    }
                }
            }
        }
    }

    private boolean samePosition(@NotNull Location from, @NotNull Location to) {
        return from.getWorld() != null
            && from.getWorld().equals(to.getWorld())
            && Double.compare(from.getX(), to.getX()) == 0
            && Double.compare(from.getY(), to.getY()) == 0
            && Double.compare(from.getZ(), to.getZ()) == 0;
    }

    private @NotNull String normalizeTeamId(@Nullable String teamId) {
        return teamId == null ? "" : teamId.trim().toLowerCase(Locale.ROOT);
    }

    private @NotNull String beautifyTeamName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "Unknown";
        }

        String[] parts = name.replace('_', ' ').split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private @NotNull Map<String, Set<Material>> loadFloorSelectorMaterials() {
        dev.stemcraft.api.config.ConfigFile root = api.config().load("config.yml");
        if (root == null) {
            return defaultFloorSelectorMaterials();
        }

        dev.stemcraft.api.config.ConfigSection floorSection = root.getSection(FLOOR_SELECTOR_CONFIG_PATH);
        boolean changed = false;
        Map<String, Set<Material>> materialsByTeam = new LinkedHashMap<>();

        for (String teamId : TeamNames.predefined().stream()
            .filter(team -> !TeamNames.TEAM_AUTO.equals(team))
            .sorted()
            .toList()) {
            List<String> defaultValues = defaultFloorSelectorMaterialsForTeam(teamId).stream()
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .toList();
            if (!floorSection.contains(teamId)) {
                floorSection.set(teamId, defaultValues);
                changed = true;
            }

            Set<Material> parsed = parseFloorSelectorMaterials(teamId, floorSection.getStringList(teamId));
            materialsByTeam.put(teamId, parsed.isEmpty() ? defaultFloorSelectorMaterialsForTeam(teamId) : parsed);
        }

        if (changed) {
            root.save();
        }
        return materialsByTeam;
    }

    private @NotNull Set<Material> parseFloorSelectorMaterials(@NotNull String teamId, @NotNull List<String> rawValues) {
        Set<Material> parsed = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }

            Material material = Material.matchMaterial(rawValue.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                api.messages().warn("Ignoring invalid team-selection floor material '" + rawValue + "' for team '" + teamId + "'.");
                continue;
            }
            parsed.add(material);
        }
        return parsed;
    }

    private @NotNull Map<String, Set<Material>> defaultFloorSelectorMaterials() {
        return TeamNames.predefined().stream()
            .filter(team -> !TeamNames.TEAM_AUTO.equals(team))
            .sorted()
            .collect(Collectors.toMap(
                team -> team,
                this::defaultFloorSelectorMaterialsForTeam,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private @NotNull Set<Material> defaultFloorSelectorMaterialsForTeam(@NotNull String teamId) {
        return switch (normalizeTeamId(teamId)) {
            case TeamNames.TEAM_BLACK -> Set.of(Material.BLACK_WOOL, Material.BLACK_CARPET, Material.BLACK_CONCRETE, Material.BLACK_CONCRETE_POWDER, Material.BLACK_TERRACOTTA);
            case TeamNames.TEAM_BLUE -> Set.of(Material.BLUE_WOOL, Material.BLUE_CARPET, Material.BLUE_CONCRETE, Material.BLUE_CONCRETE_POWDER, Material.BLUE_TERRACOTTA);
            case TeamNames.TEAM_BROWN -> Set.of(Material.BROWN_WOOL, Material.BROWN_CARPET, Material.BROWN_CONCRETE, Material.BROWN_CONCRETE_POWDER, Material.BROWN_TERRACOTTA);
            case TeamNames.TEAM_CYAN -> Set.of(Material.CYAN_WOOL, Material.CYAN_CARPET, Material.CYAN_CONCRETE, Material.CYAN_CONCRETE_POWDER, Material.CYAN_TERRACOTTA);
            case TeamNames.TEAM_GRAY -> Set.of(Material.GRAY_WOOL, Material.GRAY_CARPET, Material.GRAY_CONCRETE, Material.GRAY_CONCRETE_POWDER, Material.GRAY_TERRACOTTA);
            case TeamNames.TEAM_GREEN -> Set.of(Material.GREEN_WOOL, Material.GREEN_CARPET, Material.GREEN_CONCRETE, Material.GREEN_CONCRETE_POWDER, Material.GREEN_TERRACOTTA);
            case TeamNames.TEAM_LIGHT_BLUE -> Set.of(Material.LIGHT_BLUE_WOOL, Material.LIGHT_BLUE_CARPET, Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_CONCRETE_POWDER, Material.LIGHT_BLUE_TERRACOTTA);
            case TeamNames.TEAM_LIGHT_GRAY -> Set.of(Material.LIGHT_GRAY_WOOL, Material.LIGHT_GRAY_CARPET, Material.LIGHT_GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE_POWDER, Material.LIGHT_GRAY_TERRACOTTA);
            case TeamNames.TEAM_LIME -> Set.of(Material.LIME_WOOL, Material.LIME_CARPET, Material.LIME_CONCRETE, Material.LIME_CONCRETE_POWDER, Material.LIME_TERRACOTTA);
            case TeamNames.TEAM_MAGENTA -> Set.of(Material.MAGENTA_WOOL, Material.MAGENTA_CARPET, Material.MAGENTA_CONCRETE, Material.MAGENTA_CONCRETE_POWDER, Material.MAGENTA_TERRACOTTA);
            case TeamNames.TEAM_ORANGE -> Set.of(Material.ORANGE_WOOL, Material.ORANGE_CARPET, Material.ORANGE_CONCRETE, Material.ORANGE_CONCRETE_POWDER, Material.ORANGE_TERRACOTTA);
            case TeamNames.TEAM_PINK -> Set.of(Material.PINK_WOOL, Material.PINK_CARPET, Material.PINK_CONCRETE, Material.PINK_CONCRETE_POWDER, Material.PINK_TERRACOTTA);
            case TeamNames.TEAM_PURPLE -> Set.of(Material.PURPLE_WOOL, Material.PURPLE_CARPET, Material.PURPLE_CONCRETE, Material.PURPLE_CONCRETE_POWDER, Material.PURPLE_TERRACOTTA);
            case TeamNames.TEAM_RED -> Set.of(Material.RED_WOOL, Material.RED_CARPET, Material.RED_CONCRETE, Material.RED_CONCRETE_POWDER, Material.RED_TERRACOTTA);
            case TeamNames.TEAM_YELLOW -> Set.of(Material.YELLOW_WOOL, Material.YELLOW_CARPET, Material.YELLOW_CONCRETE, Material.YELLOW_CONCRETE_POWDER, Material.YELLOW_TERRACOTTA);
            default -> Set.of(Material.WHITE_WOOL, Material.WHITE_CARPET, Material.WHITE_CONCRETE, Material.WHITE_CONCRETE_POWDER, Material.WHITE_TERRACOTTA);
        };
    }
}

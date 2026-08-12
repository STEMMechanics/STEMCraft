package dev.stemcraft.service.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.api.util.TextUtil;
import dev.stemcraft.capability.HasMetaImpl;
import lombok.Getter;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class MiniGameArenaImpl extends HasMetaImpl<MiniGameArena> implements MiniGameArena {
    private static final double SUPPLY_DROP_SPAWN_ABOVE_SURFACE_OFFSET = 0.15d;
    private static final double SUPPLY_DROP_CRATE_START_Y = 190.0d;
    private static final double SUPPLY_DROP_CRATE_DESCENT_PER_TICK = 0.24d;
    private static final double SUPPLY_DROP_PARACHUTE_HEIGHT = 4.0d;
    private static final BlockFace[] SUPPLY_DROP_SUPPORT_FACES = new BlockFace[] {
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST,
        BlockFace.NORTH_EAST,
        BlockFace.NORTH_WEST,
        BlockFace.SOUTH_EAST,
        BlockFace.SOUTH_WEST
    };
    private static final Color[] DEFAULT_CELEBRATION_COLORS = new Color[] {
        Color.RED,
        Color.ORANGE,
        Color.YELLOW,
        Color.AQUA,
        Color.LIME,
        Color.FUCHSIA
    };
    private static final SupplyDropParachutePart[] SUPPLY_DROP_PARACHUTE_PARTS = new SupplyDropParachutePart[] {
        new SupplyDropParachutePart(-1, 0, -1, parachuteStripe(-1)),
        new SupplyDropParachutePart(0, 0, -1, parachuteStripe(-1)),
        new SupplyDropParachutePart(1, 0, -1, parachuteStripe(-1)),
        new SupplyDropParachutePart(-1, 0, 0, parachuteStripe(0)),
        new SupplyDropParachutePart(0, 0, 0, parachuteStripe(0)),
        new SupplyDropParachutePart(1, 0, 0, parachuteStripe(0)),
        new SupplyDropParachutePart(-1, 0, 1, parachuteStripe(1)),
        new SupplyDropParachutePart(0, 0, 1, parachuteStripe(1)),
        new SupplyDropParachutePart(1, 0, 1, parachuteStripe(1)),
        new SupplyDropParachutePart(-2, -1, -1, parachuteStripe(-1)),
        new SupplyDropParachutePart(-2, -1, 0, parachuteStripe(0)),
        new SupplyDropParachutePart(-2, -1, 1, parachuteStripe(1)),
        new SupplyDropParachutePart(-1, -1, -2, parachuteStripe(-2)),
        new SupplyDropParachutePart(-1, -1, 2, parachuteStripe(2)),
        new SupplyDropParachutePart(0, -1, -2, parachuteStripe(-2)),
        new SupplyDropParachutePart(0, -1, 2, parachuteStripe(2)),
        new SupplyDropParachutePart(1, -1, -2, parachuteStripe(-2)),
        new SupplyDropParachutePart(1, -1, 2, parachuteStripe(2)),
        new SupplyDropParachutePart(2, -1, -1, parachuteStripe(-1)),
        new SupplyDropParachutePart(2, -1, 0, parachuteStripe(0)),
        new SupplyDropParachutePart(2, -1, 1, parachuteStripe(1)),
        new SupplyDropParachutePart(-3, -2, -1, parachuteStripe(-1)),
        new SupplyDropParachutePart(-3, -2, 0, parachuteStripe(0)),
        new SupplyDropParachutePart(-3, -2, 1, parachuteStripe(1)),
        new SupplyDropParachutePart(-2, -2, -2, parachuteStripe(-2)),
        new SupplyDropParachutePart(-2, -2, 2, parachuteStripe(2)),
        new SupplyDropParachutePart(-1, -2, -3, parachuteStripe(-3)),
        new SupplyDropParachutePart(-1, -2, 3, parachuteStripe(3)),
        new SupplyDropParachutePart(0, -2, -3, parachuteStripe(-3)),
        new SupplyDropParachutePart(0, -2, 3, parachuteStripe(3)),
        new SupplyDropParachutePart(1, -2, -3, parachuteStripe(-3)),
        new SupplyDropParachutePart(1, -2, 3, parachuteStripe(3)),
        new SupplyDropParachutePart(2, -2, -2, parachuteStripe(-2)),
        new SupplyDropParachutePart(2, -2, 2, parachuteStripe(2)),
        new SupplyDropParachutePart(3, -2, -1, parachuteStripe(-1)),
        new SupplyDropParachutePart(3, -2, 0, parachuteStripe(0)),
        new SupplyDropParachutePart(3, -2, 1, parachuteStripe(1))
    };
    private static final long SUPPLY_DROP_SIGNAL_PERIOD_TICKS = 40L;

    private final MiniGameServiceImpl service;
    private final STEMCraftAPI api;
    private final String namespace;
    private final String id;
    @Getter
    private String name;
    private final World world;
    private boolean validated;
    @Getter
    private SCRegion region;
    @Getter
    private ArenaStatus status;
    @Getter
    private Location lobbySpawn;
    @Getter
    private Location spectatorSpawn;
    @Getter
    private int countdown;
    @Getter
    private int countdownMax;
    @Getter
    private int minPlayers;
    @Getter
    private int maxPlayers;

    private final Map<String, MiniGameTeamImpl> teams = new LinkedHashMap<>();
    private final Map<Player, MiniGamePlayerImpl> players = new HashMap<>();
    private final Map<Player, MiniGamePlayerImpl> spectatorProfiles = new HashMap<>();
    private final Set<Player> spectators = new HashSet<>();
    private final Set<Material> unlimitedAmmo = EnumSet.noneOf(Material.class);
    private final Set<Material> unlimitedPlacements = EnumSet.noneOf(Material.class);
    private final Set<String> activeCelebrationKeys = new HashSet<>();
    private final Map<UUID, SupplyDropMarker> supplyDropMarkers = new HashMap<>();
    private final Map<UUID, SupplyDropCrate> supplyDropCrates = new HashMap<>();
    private final Map<UUID, Long> protectionUntil = new HashMap<>();
    private final Map<String, Map<Material, Integer>> kits = new HashMap<>();

    @Getter
    private ArenaValidationResult lastValidation = ArenaValidationResult.success();

    /**
     * Constructor
     *
     * @param api       STEMCraft API
     * @param namespace The arena namespace
     * @param id        The arena ID
     * @param world     The world the arena is in
     */
    MiniGameArenaImpl(@NotNull MiniGameServiceImpl service, @NotNull STEMCraftAPI api, @NotNull String namespace, @NotNull String id, @NotNull World world) {
        this.service = service;
        this.api = api;
        this.namespace = namespace;
        this.id = id;
        this.name = StringUtil.beautify(id);
        this.world = world;
        this.lobbySpawn = world.getSpawnLocation();
        this.spectatorSpawn = this.lobbySpawn;
        this.region = null;
        this.status = ArenaStatus.SETUP;
    }

    /**
     * Get the arena namespace.
     *
     * @return The arena namespace.
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Get the arena id.
     *
     * @return The arena id.
     */
    public String id() {
        return id;
    }

    /**
     * Get the world the arena is in.
     *
     * @return The world.
     */
    public World world() {
        return world;
    }

    @Override
    public boolean isJoinable() {
        if (get("allowRunningJoin", Boolean.class, false) && status == ArenaStatus.RUNNING) {
            return true;
        }
        return MiniGameArena.super.isJoinable();
    }

    public SCRegion region() {
        return region;
    }

    public MiniGameArena setRegion(SCRegion region) {
        this.region = region;
        return this;
    }

    public MiniGameArena setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set the arena status.
     *
     * @param newStatus The new status to set.
     * @param countdown The countdown time in seconds, or -1 to leave unchanged. Setting to 0 will not trigger countdown end.
     */
    @Override
    public MiniGameArena setStatus(ArenaStatus newStatus, int countdown) {
        if (newStatus == null) return this;
        if (Objects.equals(this.status, newStatus)) return this;

        if(newStatus != ArenaStatus.DISABLED && this.status == ArenaStatus.SETUP && !validated) {
            ArenaValidationResult result = validate();
            if (result.hasErrors()) {
                api.messages().warn("Arena '{}' failed validation and cannot change status from SETUP.", id);
                return this;
            }
        }

        ArenaStatus oldStatus = this.status;
        if ((oldStatus == ArenaStatus.SETUP || oldStatus == ArenaStatus.DISABLED)
            && newStatus != ArenaStatus.DISABLED && validated) {
            MiniGameArenaHandler handler = service.getHandler(this.namespace());
            handler.onArenaLoad(this);
        } else if (oldStatus != null && oldStatus != ArenaStatus.DISABLED && newStatus == ArenaStatus.DISABLED) {
            MiniGameArenaHandler handler = service.getHandler(this.namespace());
            handler.onArenaUnload(this);
        }

        if ((oldStatus == ArenaStatus.ENDING || oldStatus == ArenaStatus.COOLDOWN)
            && newStatus != ArenaStatus.ENDING
            && newStatus != ArenaStatus.COOLDOWN) {
            stopWinnerCelebration();
        }

        this.status = newStatus;
        if (countdown >= 0) {
            this.countdownMax = countdown;
            setCountdown(countdown);
        } else if (newStatus == ArenaStatus.WAITING || newStatus == ArenaStatus.IDLE || newStatus == ArenaStatus.SETUP
            || newStatus == ArenaStatus.DISABLED || newStatus == ArenaStatus.SHUTDOWN || newStatus == ArenaStatus.RESETTING) {
            this.countdownMax = 0;
        }

        if (newStatus == ArenaStatus.RUNNING) {
            service.teamSelectionSupport().prepareArenaStart(this);
        }

        MiniGameArenaHandler handler = service.getHandler(this.namespace());
        handler.onArenaStatusChanged(this, oldStatus, newStatus);
        service.teamSelectionSupport().onArenaStatusChanged(this, oldStatus, newStatus);
        return this;
    }

    /**
     * Validate the arena region.
     *
     * @return The arena validation result.
     */
    @Override
    public ArenaValidationResult validate() {
        ArenaValidationResult result = ArenaValidationResult.success();
        MiniGameArenaHandler handler = service.getHandler(namespace);

        if(this.namespace.isEmpty()) {
            result.addError("Arena namespace is not set.", "namespace");
        }

        if(this.id.isEmpty()) {
            result.addError("Arena ID is not set.", "id");
        }

        if(this.name == null || this.name.isEmpty()) {
            this.name = StringUtil.beautify(id);
        }

        if(this.world == null || !Bukkit.getWorlds().contains(this.world)) {
            result.addError("Arena world is not set or is invalid.", "world");
        }

        handler.validate(this, result);
        service.teamSelectionSupport().validateArenaSetup(this, result);

        this.validated = !result.hasErrors();
        this.lastValidation = result;
        return result;
    }

    /**
     * Set the lobby spawn location.
     *
     * @param location The lobby spawn location.
     */
    @Override
    public MiniGameArena setLobbySpawn(Location location) {
        this.lobbySpawn = location;
        return this;
    }

    @Override
    public MiniGameArena setSpectatorSpawn(Location location) {
        this.spectatorSpawn = location;
        return this;
    }

    /**
     * Set the countdown time.
     *
     * @param seconds The countdown time in seconds.
     */
    @Override
    public MiniGameArena setCountdown(int seconds) {
        this.countdown = Math.max(0, seconds);
        if (this.countdown == 0
            && status != ArenaStatus.STARTING
            && status != ArenaStatus.PREPARATION
            && status != ArenaStatus.RUNNING
            && status != ArenaStatus.COOLDOWN
            && status != ArenaStatus.ENDING) {
            this.countdownMax = 0;
        }
        return this;
    }

    /**
     * Subtract one second from the countdown.
     *
     * @return The updated countdown time in seconds.
     */
    @Override
    public int decrementCountdown() {
        if (countdown > 0) countdown -= 1;
        return countdown;
    }

    /**
     * Set the minimum number of players required to start the game.
     * If the specified minimum is less than 1, it will be adjusted to 1.
     * If the current maximum is less than the new minimum, the maximum will be adjusted to equal the new minimum.
     *
     * @param minPlayers The minimum number of players.
     */
    @Override
    public MiniGameArena setMinPlayers(int minPlayers) {
        if(minPlayers < 1) minPlayers = 1;
        this.minPlayers = minPlayers;
        if(this.maxPlayers < minPlayers) {
            this.maxPlayers = minPlayers;
        }

        return this;
    }

    /**
     * Set the maximum number of players allowed in the game.
     * If the specified maximum is less than the minimum, it will be adjusted to equal the minimum.
     * If there are current players exceeding the new maximum, no players will be removed; this setting only affects future joins.
     *
     * @param maxPlayers The maximum number of players.
     */
    @Override
    public MiniGameArena setMaxPlayers(int maxPlayers) {
        if(maxPlayers < 1) maxPlayers = 1;
        this.maxPlayers = Math.max(minPlayers, maxPlayers);

        return this;
    }

    /**
     * Get the number of players currently in the arena.
     *
     * @return The number of players.
     */
    @Override
    public int numPlayers() {
        return players.size();
    }

    /**
     * Get a list of players currently in the arena.
     *
     * @return The list of players.
     */
    @Override
    public List<Player> getPlayers() {
        return new ArrayList<>(players.keySet());
    }

    /**
     * Check if a player is currently in the arena.
     *
     * @param player The player to check.
     * @return True if the player is in the arena, false otherwise.
     */
    @Override
    public boolean hasPlayer(Player player) {
        return players.containsKey(player);
    }

    /**
     * Add a player to the arena.
     *
     * @param player The player to add.
     */
    @Override
    public void addPlayer(Player player) {
        if (players.containsKey(player)) return;
        boolean joiningFromOutside = !spectators.contains(player);

        MiniGameArenaImpl existingArena = service.findPlayerArena(player);
        if (existingArena != null && existingArena != this) {
            api.messages().warn(player, "You are already in another arena.");
            return;
        }

        if(!isJoinable()) {
            api.messages().send(player, "minigame.arena.join.inactive", null,
                    "arena", name);
            return;
        }

        if (players.size() >= maxPlayers) {
            api.messages().send(player, "minigame.arena.join.full", null, "arena", name);
            return;
        }

        MiniGamePlayerImpl mgPlayer = null;
        if (spectators.contains(player)) {
            mgPlayer = detachSpectatorProfile(player, false, false);
        }

        service.storePreviousPlayerState(player);
        service.prepareActivePlayer(player);
        if (mgPlayer == null) {
            mgPlayer = new MiniGamePlayerImpl(service, player);
        }
        players.put(player, mgPlayer);
        service.registerPlayerArena(player, this, false);

        if (mgPlayer.getTeam() != null) {
            MiniGameTeamImpl preservedTeam = teams.get(mgPlayer.getTeam());
            if (preservedTeam != null) {
                preservedTeam.addPlayer(player);
            } else {
                mgPlayer.setTeam(null);
            }
        }

        if (!teams.isEmpty()
            && mgPlayer.getTeam() == null
            && !service.teamSelectionSupport().usesTeamSelection(this)
            && get("autoAssignTeams", Boolean.class, true)) {
            setRandomTeam(player);
        }

        MiniGameArenaHandler handler = service.getHandler(namespace);
        Location location = handler.onPlayerJoinArena(this, player);
        if (location == null) {
            location = (getStatus() == ArenaStatus.PREPARATION || getStatus() == ArenaStatus.RUNNING)
                ? getPlayerSpawnOrLobby(player)
                : lobbySpawn;
        }

        PlayerUtil.teleport(player, location);
        if (joiningFromOutside) {
            service.applyArenaJoinActions(this, player, false);
        }
        service.teamSelectionSupport().onPlayerJoinedArena(this, player);
    }

    /**
     * Remove a player from the arena.
     *
     * @param player The player to remove.
     */
    @Override
    public void removePlayer(Player player) {
        removeActivePlayer(player, true, false);
    }

    void removePlayer(Player player, boolean restoreLocation) {
        removeActivePlayer(player, restoreLocation, false);
    }

    @Override
    public int numSpectators() {
        return spectators.size();
    }

    @Override
    public List<Player> getSpectators() {
        return new ArrayList<>(spectators);
    }

    @Override
    public boolean hasSpectator(Player player) {
        return spectators.contains(player);
    }

    @Override
    public void addSpectator(Player player) {
        if (spectators.contains(player)) return;
        boolean joiningFromOutside = !players.containsKey(player);

        MiniGameArenaImpl existingArena = service.findPlayerArena(player);
        if (existingArena != null && existingArena != this) {
            api.messages().warn(player, "You are already in another arena.");
            return;
        }

        MiniGamePlayerImpl spectatorProfile = null;
        if (players.containsKey(player)) {
            spectatorProfile = detachActiveProfile(player, false, false);
        }

        service.storePreviousPlayerState(player);
        service.prepareSpectatorPlayer(player);
        if (spectatorProfile == null) {
            spectatorProfile = new MiniGamePlayerImpl(service, player);
        }
        spectators.add(player);
        spectatorProfiles.put(player, spectatorProfile);
        service.registerPlayerArena(player, this, true);

        MiniGameArenaHandler handler = service.getHandler(namespace);
        Location location = handler.onPlayerJoinSpectator(this, player);
        if (location == null) {
            location = spectatorSpawn != null ? spectatorSpawn : lobbySpawn;
        }

        PlayerUtil.teleport(player, location);
        if (joiningFromOutside) {
            service.applyArenaJoinActions(this, player, true);
        }
    }

    @Override
    public void removeSpectator(Player player) {
        removeSpectatorInternal(player, true, false);
    }

    void removeSpectator(Player player, boolean restoreLocation) {
        removeSpectatorInternal(player, restoreLocation, false);
    }

    void removeOccupant(Player player, boolean restoreLocation) {
        if (players.containsKey(player)) {
            removeActivePlayer(player, restoreLocation, false);
        } else if (spectators.contains(player)) {
            removeSpectatorInternal(player, restoreLocation, false);
        }
    }

    /**
     * Get a list of teams in the arena.
     *
     * @return The list of teams.
     */
    @Override
    public List<MiniGameTeam> getTeams() {
        return new ArrayList<>(teams.values());
    }

    /**
     * Add a team to the arena.
     *
     * @param id    The team ID.
     * @param name  The team name.
     * @param spawn The team spawn location.
     * @return The created team.
     */
    @Override
    public MiniGameTeam addTeam(String id, String name, Location spawn) {
        MiniGameTeamImpl team = new MiniGameTeamImpl(id, name, spawn);
        teams.put(id, team);
        return team;
    }

    /**
     * Remove a team from the arena.
     *
     * @param id The team ID.
     */
    @Override
    public void removeTeam(String id) {
        teams.remove(id);
    }

    /**
     * Get a team by its ID.
     *
     * @param id The team ID.
     * @return The team, or null if not found.
     */
    @Override
    public MiniGameTeam getTeam(String id) {
        return teams.get(id);
    }

    /**
     * Get a random team with the least number of players.
     *
     * @return The team ID.
     */
    @Override
    public String getRandomTeam() {
        if (teams.isEmpty()) return "";

        String selected = null;
        int minCount = Integer.MAX_VALUE;

        for (MiniGameTeamImpl team : teams.values()) {
            int count = team.getPlayers().size();
            if (count < minCount) {
                minCount = count;
                selected = team.getId();
            }
        }

        return selected == null ? teams.keySet().iterator().next() : selected;
    }


    /******* NOT USED??? ********/












    @Override
    public void setRandomTeam(Player player) {
        setPlayerTeam(player, getRandomTeam());
    }

    @Override
    public List<Player> getTeamPlayers(String team) {
        MiniGameTeamImpl t = teams.get(team);
        return t == null ? List.of() : t.getPlayers();
    }

    @Override
    public MiniGameTeam getPlayerTeam(Player player) {
        MiniGamePlayerImpl mgPlayer = players.get(player);
        if (mgPlayer == null) return null;
        return teams.get(mgPlayer.getTeam());
    }

    @Override
    public MiniGameTeam getPlayerTeam(MiniGamePlayer player) {
        if (player == null) return null;
        return teams.get(player.getTeam());
    }

    @Override
    public void setPlayerTeam(Player player, String team) {
        if (team == null || team.isEmpty() || !teams.containsKey(team)) return;

        MiniGamePlayerImpl mgPlayer = players.get(player);
        if (mgPlayer == null) return;

        if (mgPlayer.getTeam() != null) {
            MiniGameTeamImpl prev = teams.get(mgPlayer.getTeam());
            if (prev != null) prev.removePlayer(player);
        }

        MiniGameTeamImpl next = teams.get(team);
        if (next != null) {
            next.addPlayer(player);
            mgPlayer.setTeam(team);
        }
    }

    @Override
    public void teleport(Player player, Location location) {
        if (player != null && location != null) player.teleport(location);
    }

    @Override
    public void teleportAll(Location location) {
        if (location == null) return;
        for (Player player : getPlayers()) {
            teleport(player, location);
        }
    }

    @Override
    public void teleportTeam(String team, Location location) {
        if (location == null) return;
        for (Player player : getTeamPlayers(team)) {
            teleport(player, location);
        }
    }

    @Override
    public void teleportAllToLobby() {
        teleportAll(lobbySpawn);
    }

    @Override
    public void teleportToLobby(Player player) {
        teleport(player, lobbySpawn);
    }

    @Override
    public void teleportToTeamSpawn(Player player) {
        teleport(player, getPlayerSpawnOrLobby(player));
    }

    @Override
    public void teleportAllToTeamSpawns() {
        for (Player player : getPlayers()) {
            teleportToTeamSpawn(player);
        }
    }

    @Override
    public int getPlayerProtectionRemaining(Player player) {
        Long until = protectionUntil.get(player.getUniqueId());
        if (until == null) return 0;
        if (until < 0) return -1;

        long remaining = (until - System.currentTimeMillis()) / 1000L;
        if (remaining <= 0) {
            protectionUntil.remove(player.getUniqueId());
            return 0;
        }
        return (int) remaining;
    }

    @Override
    public void setPlayerProtection(Player player, boolean protect, int duration) {
        if (!protect) {
            protectionUntil.remove(player.getUniqueId());
            return;
        }

        if (duration < 0) {
            protectionUntil.put(player.getUniqueId(), -1L);
        } else {
            protectionUntil.put(player.getUniqueId(), System.currentTimeMillis() + (duration * 1000L));
        }
    }

    @Override
    public MiniGamePlayer getPlayer(Player player) {
        return players.get(player);
    }

    @Nullable MiniGamePlayerImpl occupantProfile(Player player) {
        MiniGamePlayerImpl active = players.get(player);
        if (active != null) {
            return active;
        }
        return spectatorProfiles.get(player);
    }

    @Override
    public void addKit(String id, String name, Material icon, Map<Material, Integer> items) {
        kits.put(id, new HashMap<>(items));
        set("kit:" + id + ":name", name);
        set("kit:" + id + ":icon", icon);
    }

    @Override
    public void removeKit(String id) {
        kits.remove(id);
        remove("kit:" + id + ":name");
        remove("kit:" + id + ":icon");
    }

    @Override
    public boolean hasKit(String id) {
        return kits.containsKey(id);
    }

    @Override
    public Map<Material, Integer> getKit(String id) {
        return kits.get(id);
    }

    @Override
    public List<String> getKits() {
        return new ArrayList<>(kits.keySet());
    }

    @Override
    public void giveKit(Player player, String id, boolean clearInventory) {
        if (player == null || id == null || id.isBlank()) {
            return;
        }

        Map<Material, Integer> kit = kits.get(id);
        if (kit == null) {
            return;
        }

        if (clearInventory) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[0]);
        }

        for (Map.Entry<Material, Integer> entry : kit.entrySet()) {
            Material material = entry.getKey();
            Integer amount = entry.getValue();
            if (material == null || amount == null || amount <= 0) {
                continue;
            }
            player.getInventory().addItem(new ItemStack(material, amount));
        }
        player.updateInventory();
    }

    @Override
    public void setUnlimitedAmmo(Material ammo, boolean enabled) {
        if (ammo == null) {
            return;
        }

        if (enabled) {
            unlimitedAmmo.add(ammo);
        } else {
            unlimitedAmmo.remove(ammo);
        }
    }

    @Override
    public boolean hasUnlimitedAmmo(Material ammo) {
        return ammo != null && unlimitedAmmo.contains(ammo);
    }

    @Override
    public Set<Material> getUnlimitedAmmo() {
        return Set.copyOf(unlimitedAmmo);
    }

    @Override
    public void setUnlimitedPlacement(Material material, boolean enabled) {
        if (material == null) {
            return;
        }

        if (enabled) {
            unlimitedPlacements.add(material);
        } else {
            unlimitedPlacements.remove(material);
        }
    }

    @Override
    public boolean hasUnlimitedPlacement(Material material) {
        return material != null && unlimitedPlacements.contains(material);
    }

    @Override
    public Set<Material> getUnlimitedPlacements() {
        return Set.copyOf(unlimitedPlacements);
    }

    @Override
    public void startCelebration(String key, Collection<Location> locations, int durationSeconds, Color... colors) {
        String safeKey = sanitizeCelebrationKey(key);
        stopCelebration(safeKey);

        if (durationSeconds <= 0 || locations == null || locations.isEmpty()) {
            return;
        }

        List<Location> anchors = locations.stream()
            .filter(Objects::nonNull)
            .filter(location -> location.getWorld() != null)
            .map(Location::clone)
            .toList();
        if (anchors.isEmpty()) {
            return;
        }

        Color[] palette = colors == null || colors.length == 0
            ? DEFAULT_CELEBRATION_COLORS
            : Arrays.stream(colors).filter(Objects::nonNull).toArray(Color[]::new);
        if (palette.length == 0) {
            palette = DEFAULT_CELEBRATION_COLORS;
        }

        String taskId = celebrationTaskId(safeKey);
        final int maxBursts = durationSeconds;
        final int[] bursts = {0};
        final Color[] celebrationPalette = palette;
        activeCelebrationKeys.add(safeKey);
        api.tasks().repeating(taskId, 0L, 20L, () -> {
            if (bursts[0] >= maxBursts) {
                stopCelebration(safeKey);
                return;
            }

            for (Location anchor : anchors) {
                if (anchor.getWorld() == null) {
                    continue;
                }
                launchCelebrationFirework(anchor, celebrationPalette);
            }
            bursts[0]++;
        });
    }

    @Override
    public void stopCelebration(String key) {
        String safeKey = sanitizeCelebrationKey(key);
        activeCelebrationKeys.remove(safeKey);
        api.tasks().cancel(celebrationTaskId(safeKey));
    }

    @Override
    public void stopAllCelebrations() {
        for (String key : new ArrayList<>(activeCelebrationKeys)) {
            stopCelebration(key);
        }
    }

    @Override
    public void trackSupplyDrop(Item item, Location markerLocation) {
        if (item == null || markerLocation == null || markerLocation.getWorld() == null) {
            return;
        }

        UUID itemId = item.getUniqueId();
        clearSupplyDrop(itemId);

        String celebrationKey = "supply-drop-" + itemId;
        Location signalLocation = markerLocation.clone();
        supplyDropMarkers.put(itemId, new SupplyDropMarker(celebrationKey));
        startCelebration(
            celebrationKey,
            signalLocation,
            3,
            Color.AQUA,
            Color.YELLOW,
            Color.WHITE
        );
        startSupplyDropSignal(itemId, signalLocation);
    }

    @Override
    public void spawnSupplyDropCrate(ItemStack item, Location landingLocation) {
        if (item == null || item.getType().isAir() || landingLocation == null || landingLocation.getWorld() == null) {
            return;
        }

        Block chestBlock = resolveSupplyDropChestBlock(landingLocation);
        if (chestBlock == null) {
            return;
        }
        UUID crateId = UUID.randomUUID();
        clearSupplyDropCrate(crateId);

        Location chestBlockLocation = chestBlock.getLocation();
        double startY = Math.max(SUPPLY_DROP_CRATE_START_Y, chestBlockLocation.getY() + 8.0d);
        Location visualLocation = chestBlockLocation.clone();
        visualLocation.setY(startY);

        SupplyDropCrate crate = new SupplyDropCrate(
            crateId,
            item.clone(),
            chestBlockLocation,
            spawnSupplyDropChestDisplay(visualLocation),
            spawnSupplyDropParachuteDisplays(visualLocation)
        );
        supplyDropCrates.put(crateId, crate);
        startSupplyDropCrateDescent(crate);
    }

    @Override
    public void clearAllSupplyDrops() {
        for (UUID itemId : new ArrayList<>(supplyDropMarkers.keySet())) {
            clearSupplyDrop(itemId);
        }
        for (UUID crateId : new ArrayList<>(supplyDropCrates.keySet())) {
            clearSupplyDropCrate(crateId);
        }
    }

    @Override
    public Location findRandomSupplyDropLocation(List<Material> allowedSurfaceMaterials, int attempts) {
        if (allowedSurfaceMaterials == null || allowedSurfaceMaterials.isEmpty() || attempts <= 0) {
            return null;
        }

        SCRegion arenaRegion = get("arenaRegion", SCRegion.class);
        if (arenaRegion == null) {
            return null;
        }

        return findRandomSupplyDropLocation(
            arenaRegion.getMinimumLocation(),
            arenaRegion.getMaximumLocation(),
            arenaRegion::contains,
            allowedSurfaceMaterials,
            attempts
        );
    }

    @Nullable Location findRandomSupplyDropLocation(
        @NotNull Location min,
        @NotNull Location max,
        @NotNull Predicate<Location> contains,
        @NotNull List<Material> allowedSurfaceMaterials,
        int attempts
    ) {
        if (allowedSurfaceMaterials.isEmpty() || attempts <= 0) {
            return null;
        }

        int width = (max.getBlockX() - min.getBlockX()) + 1;
        int depth = (max.getBlockZ() - min.getBlockZ()) + 1;
        if (width <= 0 || depth <= 0) {
            return null;
        }

        World regionWorld = min.getWorld();
        if (regionWorld == null) {
            return null;
        }

        Set<Material> allowedMaterials = EnumSet.copyOf(allowedSurfaceMaterials);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = min.getBlockX() + random.nextInt(width);
            int z = min.getBlockZ() + random.nextInt(depth);
            Location dropLocation = highestSupplyDropLocation(regionWorld, min.getBlockY(), max.getBlockY(), x, z, contains, allowedMaterials);
            if (dropLocation != null) {
                return dropLocation;
            }
        }

        return null;
    }

    boolean tracksSupplyDrop(@NotNull UUID itemId) {
        return supplyDropMarkers.containsKey(itemId);
    }

    void pruneSupplyDrops() {
        for (UUID itemId : new ArrayList<>(supplyDropMarkers.keySet())) {
            Entity entity = world.getEntity(itemId);
            if (entity instanceof Item arenaItem && arenaItem.isValid() && !arenaItem.isDead()) {
                continue;
            }
            clearSupplyDrop(itemId);
        }

        for (UUID crateId : new ArrayList<>(supplyDropCrates.keySet())) {
            SupplyDropCrate crate = supplyDropCrates.get(crateId);
            if (crate == null || !crate.landed()) {
                continue;
            }

            Block chestBlock = crate.chestBlockLocation().getBlock();
            if (!(chestBlock.getState() instanceof Chest chest)) {
                clearSupplyDropCrate(crateId);
                continue;
            }
            if (!chest.getBlockInventory().getViewers().isEmpty()) {
                crate.setOpened(true);
                continue;
            }
            if (!chest.getBlockInventory().isEmpty() || !crate.opened()) {
                continue;
            }

            chestBlock.setType(Material.AIR, false);
            clearSupplyDropCrate(crateId);
        }
    }

    void handlePlayerQuit(Player player) {
        if (players.containsKey(player)) {
            removeActivePlayer(player, false, true);
        } else if (spectators.contains(player)) {
            removeSpectatorInternal(player, false, true);
        }
    }

    void handleExternalTeleport(Player player) {
        if (players.containsKey(player)) {
            removeActivePlayer(player, false, false);
        } else if (spectators.contains(player)) {
            removeSpectatorInternal(player, false, false);
        }
    }

    private Location getPlayerSpawnOrLobby(Player player) {
        MiniGameTeam team = getPlayerTeam(player);
        if (team != null && team.getSpawn() != null) {
            return team.getSpawn();
        }
        return lobbySpawn;
    }

    private void launchCelebrationFirework(Location anchor, Color[] palette) {
        World fireworkWorld = anchor.getWorld();
        if (fireworkWorld == null) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location fireworkLocation = anchor.clone().add(
            random.nextDouble(-1.5d, 1.5d),
            1.0d + random.nextDouble(0.0d, 1.5d),
            random.nextDouble(-1.5d, 1.5d)
        );

        fireworkWorld.spawn(fireworkLocation, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.clearEffects();
            meta.addEffect(FireworkEffect.builder()
                .with(random.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.BURST)
                .flicker(random.nextBoolean())
                .trail(true)
                .withColor(randomColor(random, palette))
                .withFade(randomColor(random, palette))
                .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.setTicksToDetonate(12);
        });
    }

    private Color randomColor(ThreadLocalRandom random, Color[] palette) {
        return palette[random.nextInt(palette.length)];
    }

    private String celebrationTaskId(String key) {
        return "minigame-celebration-" + NamespaceId.sanitizePath(namespace) + "-" + NamespaceId.sanitizePath(id) + "-" + sanitizeCelebrationKey(key);
    }

    private String sanitizeCelebrationKey(String key) {
        if (key == null || key.isBlank()) {
            return "default";
        }
        return NamespaceId.sanitizePath(key);
    }

    void clearSupplyDrop(@NotNull UUID itemId) {
        SupplyDropMarker marker = supplyDropMarkers.remove(itemId);
        if (marker == null) {
            return;
        }

        api.tasks().cancel(supplyDropSignalTaskId(itemId));
        stopCelebration(marker.celebrationKey());
    }

    void clearSupplyDropCrate(@NotNull UUID crateId) {
        SupplyDropCrate crate = supplyDropCrates.remove(crateId);
        if (crate == null) {
            return;
        }

        api.tasks().cancel(supplyDropCrateTaskId(crateId));
        removeEntity(crate.chestDisplayId());
        for (UUID parachuteDisplayId : crate.parachuteDisplayIds()) {
            removeEntity(parachuteDisplayId);
        }

        if (crate.landed()) {
            Block chestBlock = crate.chestBlockLocation().getBlock();
            if (chestBlock.getType() == Material.CHEST) {
                chestBlock.setType(Material.AIR, false);
            }
        }
    }

    private void startSupplyDropSignal(@NotNull UUID itemId, @NotNull Location signalLocation) {
        String taskId = supplyDropSignalTaskId(itemId);
        api.tasks().cancel(taskId);
        api.tasks().repeating(taskId, 0L, SUPPLY_DROP_SIGNAL_PERIOD_TICKS, () -> {
            Entity entity = world.getEntity(itemId);
            if (!(entity instanceof Item item) || !item.isValid() || item.isDead()) {
                clearSupplyDrop(itemId);
                return;
            }
            launchSupplyDropSignalFirework(signalLocation);
        });
    }

    private void launchSupplyDropSignalFirework(@NotNull Location signalLocation) {
        World fireworkWorld = signalLocation.getWorld();
        if (fireworkWorld == null) {
            return;
        }

        Location launchLocation = signalLocation.clone();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        fireworkWorld.spawn(launchLocation, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.clearEffects();
            meta.addEffect(FireworkEffect.builder()
                .with(random.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.BURST)
                .trail(true)
                .flicker(random.nextBoolean())
                .withColor(Color.AQUA, Color.YELLOW)
                .withFade(Color.WHITE)
                .build());
            int powerRoll = random.nextInt(100);
            meta.setPower(powerRoll < 10 ? 4 : powerRoll < 30 ? 3 : powerRoll < 60 ? 2 : 1);
            firework.setFireworkMeta(meta);
        });
    }

    private String supplyDropSignalTaskId(@NotNull UUID itemId) {
        return "minigame-supply-drop-signal-" + NamespaceId.sanitizePath(namespace)
            + "-" + NamespaceId.sanitizePath(id)
            + "-" + itemId.toString().replace('-', '_');
    }

    private void startSupplyDropCrateDescent(@NotNull SupplyDropCrate crate) {
        String taskId = supplyDropCrateTaskId(crate.id());
        api.tasks().cancel(taskId);
        api.tasks().repeating(taskId, 0L, 1L, () -> {
            if (!crate.landed()) {
                Entity chestDisplay = world.getEntity(crate.chestDisplayId());
                if (!(chestDisplay instanceof BlockDisplay display) || !display.isValid()) {
                    clearSupplyDropCrate(crate.id());
                    return;
                }

                double currentY = display.getLocation().getY();
                double targetY = crate.chestBlockLocation().getY();
                double nextY = Math.max(targetY, currentY - SUPPLY_DROP_CRATE_DESCENT_PER_TICK);
                updateSupplyDropCrateDisplays(crate, nextY);

                if (nextY > targetY) {
                    return;
                }

                landSupplyDropCrate(crate.id());
                return;
            }

            double nextParachuteBaseY = crate.parachuteBaseY() - SUPPLY_DROP_CRATE_DESCENT_PER_TICK;
            crate.setParachuteBaseY(nextParachuteBaseY);
            if (!updateSupplyDropParachuteDisplays(crate)) {
                api.tasks().cancel(taskId);
            }
        });
    }

    private void landSupplyDropCrate(@NotNull UUID crateId) {
        SupplyDropCrate crate = supplyDropCrates.get(crateId);
        if (crate == null || crate.landed()) {
            return;
        }

        removeEntity(crate.chestDisplayId());

        Block chestBlock = crate.chestBlockLocation().getBlock();
        if (!chestBlock.isPassable() && chestBlock.getType() != Material.CHEST) {
            clearSupplyDropCrate(crateId);
            return;
        }

        api.worlds().changes(world).captureBlock(chestBlock);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            chest.getBlockInventory().clear();
            chest.getBlockInventory().setItem(13, crate.loot().clone());
            crate.setLanded(true);
            return;
        }

        chestBlock.setType(Material.AIR, false);
        clearSupplyDropCrate(crateId);
    }

    private @NotNull UUID spawnSupplyDropChestDisplay(@NotNull Location location) {
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(Material.CHEST.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setViewRange(48.0f);
            entity.setShadowRadius(0.0f);
            entity.setShadowStrength(0.0f);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
        });
        return display.getUniqueId();
    }

    private @NotNull List<UUID> spawnSupplyDropParachuteDisplays(@NotNull Location baseLocation) {
        List<UUID> displayIds = new ArrayList<>();
        for (SupplyDropParachutePart part : SUPPLY_DROP_PARACHUTE_PARTS) {
            Location canopyLocation = baseLocation.clone().add(part.xOffset(), SUPPLY_DROP_PARACHUTE_HEIGHT + part.yOffset(), part.zOffset());
            BlockDisplay display = world.spawn(canopyLocation, BlockDisplay.class, entity -> {
                entity.setBlock(part.material().createBlockData());
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setViewRange(48.0f);
                entity.setShadowRadius(0.0f);
                entity.setShadowStrength(0.0f);
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setGravity(false);
                entity.setSilent(true);
            });
            displayIds.add(display.getUniqueId());
        }
        return displayIds;
    }

    private void updateSupplyDropCrateDisplays(@NotNull SupplyDropCrate crate, double nextY) {
        Location chestLocation = crate.chestBlockLocation().clone();
        chestLocation.setY(nextY);
        Entity chestDisplay = world.getEntity(crate.chestDisplayId());
        if (chestDisplay != null && chestDisplay.isValid()) {
            chestDisplay.teleport(chestLocation);
        }

        crate.setParachuteBaseY(nextY + SUPPLY_DROP_PARACHUTE_HEIGHT);
        updateSupplyDropParachuteDisplays(crate);
    }

    private boolean updateSupplyDropParachuteDisplays(@NotNull SupplyDropCrate crate) {
        boolean anyVisible = false;
        double groundY = crate.chestBlockLocation().getY();
        for (int i = 0; i < crate.parachuteDisplayIds().size(); i++) {
            UUID displayId = crate.parachuteDisplayIds().get(i);
            Entity canopyDisplay = world.getEntity(displayId);
            if (canopyDisplay == null || !canopyDisplay.isValid()) {
                continue;
            }

            SupplyDropParachutePart part = SUPPLY_DROP_PARACHUTE_PARTS[i];
            double partY = crate.parachuteBaseY() + part.yOffset();
            if (partY <= groundY) {
                canopyDisplay.remove();
                continue;
            }

            Location canopyLocation = crate.chestBlockLocation().clone().add(part.xOffset(), partY - groundY, part.zOffset());
            canopyDisplay.teleport(canopyLocation);
            anyVisible = true;
        }
        return anyVisible;
    }

    private void removeEntity(@NotNull UUID entityId) {
        Entity entity = world.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private String supplyDropCrateTaskId(@NotNull UUID crateId) {
        return "minigame-supply-drop-crate-" + NamespaceId.sanitizePath(namespace)
            + "-" + NamespaceId.sanitizePath(id)
            + "-" + crateId.toString().replace('-', '_');
    }

    private @Nullable Block resolveSupplyDropChestBlock(@NotNull Location landingLocation) {
        Block origin = landingLocation.getBlock();
        for (int yOffset = 1; yOffset >= -3; yOffset--) {
            Block candidate = origin.getRelative(BlockFace.UP, yOffset);
            if (!candidate.isPassable()) {
                continue;
            }

            Block support = candidate.getRelative(BlockFace.DOWN);
            if (support.getType().isAir() || support.isPassable()) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static @NotNull Material parachuteStripe(int zOffset) {
        return Math.floorMod(zOffset, 2) == 0 ? Material.WHITE_WOOL : Material.RED_WOOL;
    }

    private @Nullable Location highestSupplyDropLocation(
        @NotNull World regionWorld,
        int minY,
        int maxY,
        int x,
        int z,
        @NotNull Predicate<Location> contains,
        @NotNull Set<Material> allowedMaterials
    ) {
        for (int y = maxY; y >= minY; y--) {
            Block block = regionWorld.getBlockAt(x, y, z);
            if (!contains.test(block.getLocation()) || block.getType().isAir()) {
                continue;
            }

            return supplyDropSpawnLocation(block, allowedMaterials);
        }

        return null;
    }

    private @Nullable Location supplyDropSpawnLocation(@NotNull Block block, @NotNull Set<Material> allowedMaterials) {
        if (!allowedMaterials.contains(block.getType()) || !hasSolidSupplyDropSupport(block)) {
            return null;
        }

        Block above = block.getRelative(BlockFace.UP);
        Block aboveTwo = above.getRelative(BlockFace.UP);
        if (!above.isPassable() || !aboveTwo.isPassable()) {
            return null;
        }

        BoundingBox blockBox = block.getBoundingBox();
        return new Location(
            block.getWorld(),
            block.getX() + 0.5d,
            blockBox.getMaxY() + SUPPLY_DROP_SPAWN_ABOVE_SURFACE_OFFSET,
            block.getZ() + 0.5d
        );
    }

    private boolean hasSolidSupplyDropSupport(@NotNull Block block) {
        for (BlockFace face : SUPPLY_DROP_SUPPORT_FACES) {
            if (!block.getRelative(face).getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    private void removeActivePlayer(Player player, boolean restoreLocation, boolean quitting) {
        MiniGamePlayerImpl mgPlayer = detachActiveProfile(player, true, quitting);
        if (mgPlayer == null) {
            return;
        }

        service.restorePreviousPlayerState(player, restoreLocation);
        service.applyArenaLeaveActions(this, player, false);
    }

    private void removeSpectatorInternal(Player player, boolean restoreLocation, boolean quitting) {
        MiniGamePlayerImpl spectatorProfile = detachSpectatorProfile(player, true, quitting);
        if (spectatorProfile == null) {
            return;
        }
        service.restorePreviousPlayerState(player, restoreLocation);
        service.applyArenaLeaveActions(this, player, true);
    }

    private @Nullable MiniGamePlayerImpl detachActiveProfile(Player player, boolean notifyHandler, boolean quitting) {
        MiniGamePlayerImpl mgPlayer = players.remove(player);
        if (mgPlayer == null) {
            return null;
        }

        mgPlayer.hudDispose();
        if (mgPlayer.getTeam() != null) {
            MiniGameTeamImpl team = teams.get(mgPlayer.getTeam());
            if (team != null) {
                team.removePlayer(player);
            }
        }

        if (notifyHandler) {
            try {
                MiniGameArenaHandler handler = service.getHandler(this.namespace());
                if (quitting) {
                    handler.onPlayerQuitArena(this, player);
                } else {
                    handler.onPlayerLeaveArena(this, player);
                }
            } catch (Exception e) {
                api.messages().error("Error while handling player leave from arena event", e);
            }
        }

        service.unregisterPlayerArena(player, this);
        service.teamSelectionSupport().onPlayerLeftArena(this);
        return mgPlayer;
    }

    private @Nullable MiniGamePlayerImpl detachSpectatorProfile(Player player, boolean notifyHandler, boolean quitting) {
        if (!spectators.remove(player)) {
            return null;
        }

        MiniGamePlayerImpl spectatorProfile = spectatorProfiles.remove(player);
        if (spectatorProfile != null) {
            spectatorProfile.hudDispose();
        }

        if (notifyHandler) {
            try {
                MiniGameArenaHandler handler = service.getHandler(this.namespace());
                if (quitting) {
                    handler.onPlayerQuitSpectator(this, player);
                } else {
                    handler.onPlayerLeaveSpectator(this, player);
                }
            } catch (Exception e) {
                api.messages().error("Error while handling spectator leave from arena event", e);
            }
        }

        service.unregisterPlayerArena(player, this);
        return spectatorProfile;
    }

    @Override
    public void showTitle(String title, String subtitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        players.forEach((player, minigamePlayer) -> player.showTitle(Title.title(
            TextUtil.colourise(title),
            TextUtil.colourise(subtitle),
            Title.Times.times(fadeIn, stay, fadeOut)
        )));
    }

    @Override
    public void resetTitle() {
        players.forEach((player, minigamePlayer) -> player.resetTitle());
    }

    @Override
    public @NotNull dev.stemcraft.api.message.TokenProcessor tokens() {
        return api.messages().tokens();
    }

    @Override
    public @NotNull String text(@Nullable CommandSender sender, @NotNull String key, @NotNull Object... placeholders) {
        return api.messages().text(sender, key, placeholders);
    }

    @Override
    public void debug(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        api.messages().debug(message, ex, placeholders);
    }

    @Override
    public void log(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        api.messages().log(sender, message, ex, placeholders);
    }

    @Override
    public void send(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        api.messages().send(sender, message, ex, placeholders);
    }

    @Override
    public void info(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        api.messages().info(sender, message, ex, placeholders);
    }

    @Override
    public void warn(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        api.messages().warn(sender, message, ex, placeholders);
    }

    @Override
    public void error(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        api.messages().error(sender, message, ex, placeholders);
    }

    @Override
    public void success(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        api.messages().success(sender, message, ex, placeholders);
    }

    @Override
    public void broadcast(@NotNull String message, @Nullable List<Player> exclude, @NotNull Object... placeholders) {
        List<Player> excluded = exclude == null ? new ArrayList<>() : new ArrayList<>(exclude);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!players.containsKey(player) && !spectators.contains(player) && !excluded.contains(player)) {
                excluded.add(player);
            }
        }
        api.messages().broadcast(message, excluded, placeholders);
    }

    private record SupplyDropMarker(@NotNull String celebrationKey) {
    }

    private record SupplyDropParachutePart(int xOffset, int yOffset, int zOffset, @NotNull Material material) {
    }

    private static final class SupplyDropCrate {
        private final UUID id;
        private final ItemStack loot;
        private final Location chestBlockLocation;
        private final UUID chestDisplayId;
        private final List<UUID> parachuteDisplayIds;
        private boolean landed;
        private boolean opened;
        private double parachuteBaseY;

        private SupplyDropCrate(
            @NotNull UUID id,
            @NotNull ItemStack loot,
            @NotNull Location chestBlockLocation,
            @NotNull UUID chestDisplayId,
            @NotNull List<UUID> parachuteDisplayIds
        ) {
            this.id = id;
            this.loot = loot;
            this.chestBlockLocation = chestBlockLocation.clone();
            this.chestDisplayId = chestDisplayId;
            this.parachuteDisplayIds = List.copyOf(parachuteDisplayIds);
            this.parachuteBaseY = chestBlockLocation.getY() + SUPPLY_DROP_PARACHUTE_HEIGHT;
        }

        private @NotNull UUID id() {
            return id;
        }

        private @NotNull ItemStack loot() {
            return loot;
        }

        private @NotNull Location chestBlockLocation() {
            return chestBlockLocation.clone();
        }

        private @NotNull UUID chestDisplayId() {
            return chestDisplayId;
        }

        private @NotNull List<UUID> parachuteDisplayIds() {
            return parachuteDisplayIds;
        }

        private boolean landed() {
            return landed;
        }

        private void setLanded(boolean landed) {
            this.landed = landed;
        }

        private boolean opened() {
            return opened;
        }

        private void setOpened(boolean opened) {
            this.opened = opened;
        }

        private double parachuteBaseY() {
            return parachuteBaseY;
        }

        private void setParachuteBaseY(double parachuteBaseY) {
            this.parachuteBaseY = parachuteBaseY;
        }
    }
}

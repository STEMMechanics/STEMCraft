package dev.stemcraft.service.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniGameImpl implements MiniGame {
    private final MiniGameServiceImpl service;
    private final String namespace;
    private boolean disableHungerByDefault = true;

    private final Map<MiniGameArena.ArenaStatus, MiniGameHUD> huds = new HashMap<>();

    private final Map<String, MiniGamePlaceholderProvider> arenaPlaceholders = new HashMap<>();
    private final Map<String, MiniGamePlaceholderProvider> teamPlaceholders = new HashMap<>();
    private final Map<String, MiniGamePlaceholderProvider> playerPlaceholders = new HashMap<>();

    /**
     * Constructs a MiniGameImpl instance.
     *
     * @param service   The MiniGameServiceImpl instance.
     * @param namespace The unique namespace for the mini-game.
     */
    public MiniGameImpl(MiniGameServiceImpl service, String namespace) {
        this.service = service;
        this.namespace = namespace;

        registerArenaPlaceholder("name", (arena, team, player) -> arena != null ? arena.getName() : "");
        registerArenaPlaceholder("time-remaining", (arena, team, player) -> {
            if (arena != null) {
                int seconds = arena.getCountdown();
                int mins = seconds / 60;
                int secs = seconds % 60;
                return String.format("%02d:%02d", mins, secs);
            }
            return "";
        });
        registerArenaPlaceholder("id", (arena, team, player) -> arena != null ? arena.id() : "");
        registerArenaPlaceholder("status", (arena, team, player) -> arena != null ? arena.getStatus().name() : "");
        registerArenaPlaceholder("players", (arena, team, player) -> arena != null ? String.valueOf(arena.getPlayers().size()) : "0");
        registerArenaPlaceholder("joined-players", (arena, team, player) -> arena != null ? String.valueOf(arena.getPlayers().size()) : "0");
        registerArenaPlaceholder("min-players", (arena, team, player) -> arena != null ? String.valueOf(arena.getMinPlayers()) : "0");
        registerArenaPlaceholder("max-players", (arena, team, player) -> arena != null ? String.valueOf(arena.getMaxPlayers()) : "0");

        registerTeamPlaceholder("name", (arena, team, player) -> team != null ? team.getName() : "");
        registerTeamPlaceholder("display-name", (arena, team, player) -> team != null ? team.get("displayName", String.class, team.getName()) : "");
        registerPlayerPlaceholder("score", (arena, team, player) -> player != null ? String.valueOf(player.getScore()) : "0");
        registerPlayerPlaceholder("kills", (arena, team, player) -> player != null ? String.valueOf(player.getKills()) : "0");
        registerPlayerPlaceholder("deaths", (arena, team, player) -> player != null ? String.valueOf(player.getDeaths()) : "0");
    }

    /**
     * Gets the arena handler for this mini-game.
     *
     * @return The MiniGameArenaHandler instance.
     */
    public MiniGameArenaHandler handler() {
        return service.getHandler(this.namespace);
    }

    @Override
    public boolean disablesHungerByDefault() {
        return disableHungerByDefault;
    }

    @Override
    public MiniGame setDisableHungerByDefault(boolean disableHungerByDefault) {
        this.disableHungerByDefault = disableHungerByDefault;
        return this;
    }

    /**
     * Registers HUD lines for a specific arena status.
     *
     * @param status        The arena status for which to register the HUD.
     * @param bossBarLines  The list of boss bar HUD lines.
     * @param scoreboardLines The list of scoreboard HUD lines.
     * @return The MiniGame instance for method chaining.
     */
    @Override
    public MiniGame registerHud(MiniGameArena.ArenaStatus status,
                                List<String> bossBarLines,
                                List<String> scoreboardLines,
                                int bossBarLineHoldUpdates,
                                String bossBarColor) {
        huds.put(status, new MiniGameHUD(this, bossBarLines, scoreboardLines, bossBarLineHoldUpdates, bossBarColor));
        return this;
    }

    public MiniGameHUD getArenaActiveHUD(MiniGameArena arena) {
        return huds.get(arena.getStatus());
    }

    /**
     * Registers a placeholder provider for arenas, teams, or players.
     *
     * @param key      The placeholder key.
     * @param provider The provider function for the placeholder.
     * @return The MiniGame instance for method chaining.
     */
    @Override
    public MiniGame registerArenaPlaceholder(String key, MiniGamePlaceholderProvider provider) {
        arenaPlaceholders.put(key, provider);
        return this;
    }

    /**
     * Registers a team or player placeholder.
     *
     * @param key    The placeholder key.
     * @param provider The provider function for the placeholder.
     * @return The MiniGame instance for method chaining.
     */
    @Override
    public MiniGame registerTeamPlaceholder(String key, MiniGamePlaceholderProvider provider) {
        teamPlaceholders.put(key, provider);
        return this;
    }

    /**
     * Registers a player placeholder.
     *
     * @param key    The placeholder key.
     * @param provider The provider function for the placeholder.
     * @return The MiniGame instance for method chaining.
     */
    @Override
    public MiniGame registerPlayerPlaceholder(String key, MiniGamePlaceholderProvider provider) {
        playerPlaceholders.put(key, provider);
        return this;
    }

    /**
     * Gets the list of all arenas in this mini-game.
     *
     * @return A list of MiniGameArena instances.
     */
    @Override
    public List<MiniGameArena> arenas() {
        return service.getArenas(this.namespace);
    }

    /**
     * Gets a specific arena by its ID.
     *
     * @param arenaId The ID of the arena.
     * @return The MiniGameArena instance.
     */
    @Override
    public MiniGameArena arena(String arenaId) {
        return service.getArena(this.namespace, arenaId);
    }

    /**
     * Creates a new arena with the specified ID in the given world.
     *
     * @param arenaId The ID of the arena.
     * @param world   The world where the arena will be created.
     * @return The created MiniGameArena instance.
     */
    @Override
    public MiniGameArena createArena(String arenaId, World world) {
        if (service.getArena(this.namespace, arenaId) != null) {
            throw new IllegalArgumentException("An arena with the ID '" + arenaId + "' already exists.");
        }
        if(world == null) {
            throw new IllegalArgumentException("World cannot be null when creating an arena.");
        }
        if(Bukkit.getWorld(world.getName()) == null) {
            throw new IllegalArgumentException("The world '" + world.getName() + "' does not exist.");
        }

        // MiniGameArenaImpl(@NotNull MiniGameServiceImpl service, @NotNull STEMCraftAPI api, @NotNull String namespace, @NotNull String id, @NotNull World world, SCRegion region)
        MiniGameArenaImpl arena = new MiniGameArenaImpl(service, STEMCraftAPI.api(), namespace, arenaId, world, null);
        service.addArena(this.namespace, arenaId, arena);
        return arena;
    }

    /**
     * Removes an arena by its ID.
     *
     * @param arenaId The ID of the arena to remove.
     */
    public void removeArena(String arenaId) {
        service.removeArena(this.namespace, arenaId);
    }

    /**
     * Finds the arena a player is currently in.
     *
     * @param player The player to search for.
     * @return The MiniGameArena instance the player is in, or null if not found.
     */
    public MiniGameArena findPlayer(Player player) {
        return service.findPlayerArena(player);
    }

    public String renderArenaPlaceholder(MiniGameArena arena, String key) {
        MiniGamePlaceholderProvider provider = arenaPlaceholders.get(key);
        if (provider != null) {
            return provider.provide(arena, null, null);
        }
        return null;
    }

    public String renderTeamPlaceholder(MiniGameTeam team, String key) {
        MiniGamePlaceholderProvider provider = teamPlaceholders.get(key);
        if (provider != null) {
            return provider.provide(null, team, null);
        }
        return null;
    }

    public String renderPlayerPlaceholder(MiniGamePlayer player, String key) {
        MiniGamePlaceholderProvider provider = playerPlaceholders.get(key);
        if (provider != null) {
            return provider.provide(null, null, player);
        }
        return null;
    }
}

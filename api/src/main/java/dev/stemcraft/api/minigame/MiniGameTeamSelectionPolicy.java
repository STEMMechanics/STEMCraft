package dev.stemcraft.api.minigame;

import dev.stemcraft.api.minigame.util.TeamNames;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MiniGameTeamSelectionPolicy {
    @NotNull List<MiniGameTeam> assignableTeams(@NotNull MiniGameArena arena, @NotNull Map<Player, String> preferences);
    default @NotNull List<MiniGameTeam> assignableTeams(@NotNull MiniGameArena arena) {
        return assignableTeams(arena, Map.of());
    }

    default @NotNull List<MiniGameTeam> selectableTeams(@NotNull MiniGameArena arena) {
        return assignableTeams(arena);
    }

    default int teamCapacity(@NotNull MiniGameArena arena, @NotNull MiniGameTeam team) {
        return Math.max(1, arena.getMaxPlayers());
    }

    default int requiredActiveTeams(@NotNull MiniGameArena arena) {
        return assignableTeams(arena).size() > 1 ? 2 : 1;
    }

    default @NotNull Set<MiniGameTeamSelectionInput> supportedInputs(@NotNull MiniGameArena arena) {
        return Set.of(MiniGameTeamSelectionInput.FLOOR, MiniGameTeamSelectionInput.HOTBAR);
    }

    default @NotNull String renderTeamLabel(@NotNull MiniGameArena arena, @NotNull MiniGameTeam team) {
        return defaultTeamLabel(team);
    }

    default @NotNull String renderLobbyTeamLine(@NotNull MiniGameArena arena,
                                                @NotNull MiniGameTeam team,
                                                int activePlayers,
                                                int maxPlayers,
                                                boolean viewerTeam) {
        return renderTeamLabel(arena, team) + " <gray>(" + activePlayers + "/" + maxPlayers + ")</gray>";
    }

    default @NotNull Set<Material> selectorMaterials(@NotNull MiniGameArena arena, @NotNull MiniGameTeam team) {
        return switch (TeamNames.normalize(team.getName())) {
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

    static @NotNull String defaultTeamLabel(@NotNull MiniGameTeam team) {
        String displayName = team.get("displayName", String.class, team.getName());
        return switch (TeamNames.normalize(team.getName())) {
            case TeamNames.TEAM_BLACK -> "<black>" + displayName + "</black>";
            case TeamNames.TEAM_BLUE -> "<blue>" + displayName + "</blue>";
            case TeamNames.TEAM_BROWN, TeamNames.TEAM_ORANGE -> "<gold>" + displayName + "</gold>";
            case TeamNames.TEAM_CYAN -> "<dark_aqua>" + displayName + "</dark_aqua>";
            case TeamNames.TEAM_GRAY -> "<dark_gray>" + displayName + "</dark_gray>";
            case TeamNames.TEAM_GREEN -> "<dark_green>" + displayName + "</dark_green>";
            case TeamNames.TEAM_LIGHT_BLUE -> "<aqua>" + displayName + "</aqua>";
            case TeamNames.TEAM_LIGHT_GRAY -> "<gray>" + displayName + "</gray>";
            case TeamNames.TEAM_LIME -> "<green>" + displayName + "</green>";
            case TeamNames.TEAM_MAGENTA, TeamNames.TEAM_PINK -> "<light_purple>" + displayName + "</light_purple>";
            case TeamNames.TEAM_PURPLE -> "<dark_purple>" + displayName + "</dark_purple>";
            case TeamNames.TEAM_RED -> "<red>" + displayName + "</red>";
            case TeamNames.TEAM_YELLOW -> "<yellow>" + displayName + "</yellow>";
            default -> "<white>" + displayName + "</white>";
        };
    }
}

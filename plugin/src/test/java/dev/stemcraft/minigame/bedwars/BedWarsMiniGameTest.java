package dev.stemcraft.minigame.bedwars;

import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedWarsMiniGameTest {
    @Test
    void scoreboardTeamsHideUnusedTeamsButKeepEliminatedTeamsVisible() {
        BedWarsMiniGame game = new BedWarsMiniGame(null);
        MiniGameArena arena = mock(MiniGameArena.class);

        MiniGameTeam active = mock(MiniGameTeam.class);
        when(active.getName()).thenReturn("blue");
        when(active.get("bedAlive", Boolean.class, true)).thenReturn(true);

        MiniGameTeam unused = mock(MiniGameTeam.class);
        when(unused.getName()).thenReturn("green");
        when(unused.get("bedAlive", Boolean.class, true)).thenReturn(true);

        MiniGameTeam eliminated = mock(MiniGameTeam.class);
        when(eliminated.getName()).thenReturn("red");
        when(eliminated.get("bedAlive", Boolean.class, true)).thenReturn(false);

        when(arena.getTeams()).thenReturn(List.of(unused, eliminated, active));
        when(arena.getTeamPlayers("blue")).thenReturn(List.of(mock(Player.class)));
        when(arena.getTeamPlayers("green")).thenReturn(List.of());
        when(arena.getTeamPlayers("red")).thenReturn(List.of());

        assertEquals(List.of(active, eliminated), game.scoreboardTeams(arena));
    }

    @Test
    void renderTeamLineMarksViewingPlayersTeam() {
        BedWarsMiniGame game = new BedWarsMiniGame(null);
        MiniGameArena arena = mock(MiniGameArena.class);
        MiniGamePlayer viewer = mock(MiniGamePlayer.class);

        MiniGameTeam active = mock(MiniGameTeam.class);
        when(active.getName()).thenReturn("blue");
        when(active.get("bedAlive", Boolean.class, true)).thenReturn(true);
        when(active.get("displayName", String.class, "Blue")).thenReturn("Blue");

        MiniGameTeam eliminated = mock(MiniGameTeam.class);
        when(eliminated.getName()).thenReturn("red");
        when(eliminated.get("bedAlive", Boolean.class, true)).thenReturn(false);
        when(eliminated.get("displayName", String.class, "Red")).thenReturn("Red");

        when(viewer.getTeam()).thenReturn("blue");
        when(arena.getTeams()).thenReturn(List.of(eliminated, active));
        when(arena.getTeamPlayers("blue")).thenReturn(List.of(mock(Player.class)));
        when(arena.getTeamPlayers("red")).thenReturn(List.of());
        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.RUNNING);

        assertEquals("&9blue: &abed &7(1) &7(You)", game.renderTeamLine(arena, viewer, 0));
        assertEquals("&cred: eliminated", game.renderTeamLine(arena, viewer, 1));
    }

    @Test
    void refreshArenaKitsMarksTeamWoolAsUnlimitedPlacement() {
        BedWarsMiniGame game = new BedWarsMiniGame(null);
        MiniGameArena arena = mock(MiniGameArena.class);
        MiniGameTeam team = mock(MiniGameTeam.class);

        when(team.getName()).thenReturn("blue");
        when(team.get("displayName", String.class, "blue")).thenReturn("Blue");
        when(arena.getTeams()).thenReturn(List.of(team));

        game.refreshArenaKits(arena);

        verify(arena).addKit(eq("blue"), eq("Blue Kit"), eq(Material.BLUE_WOOL), anyMap());
        verify(arena).setUnlimitedPlacement(Material.BLUE_WOOL, true);
    }

    @Test
    void teamDisplayNameBeautifiesDefaultLowercaseTeamNames() {
        BedWarsMiniGame game = new BedWarsMiniGame(null);
        MiniGameTeam team = mock(MiniGameTeam.class);

        when(team.getName()).thenReturn("yellow");
        when(team.get("displayName", String.class, "")).thenReturn("yellow");

        assertEquals("Yellow", game.teamDisplayName(team, "yellow"));
    }
}

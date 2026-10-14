package dev.stemcraft.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.List;

import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.RUNNING;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TimedMiniGameInfoTest {
    @ParameterizedTest
    @CsvSource({"floorshuffle,FloorShuffle", "volcano,Volcano", "horserace,HorseRace", "minecartrace,MinecartRace",
            "mobshooter,MobShooter", "punchthebat,PunchTheBat", "quake,Quake"})
    @SuppressWarnings("unchecked")
    void infoReportsCommonAndGameSpecificSettingsWithoutValidatingLiveArena(String namespace, String name) throws Exception {
        String prefix = "dev.stemcraft.minigame." + namespace + "." + name;
        Class<?> ownerType = Class.forName(prefix + "MiniGame");
        Object owner = ownerType.getConstructor(STEMCraftAPI.class).newInstance((Object) null);
        var settingsConstructor = Class.forName(prefix + "ArenaSettings").getDeclaredConstructor();
        settingsConstructor.setAccessible(true);
        Object settings = settingsConstructor.newInstance();
        MiniGame game = mock(MiniGame.class);
        field(ownerType, "game").set(owner, game);
        MiniGameArena arena = mock(MiniGameArena.class);
        doReturn(settings).when(arena).getOrCreate(anyString(), any(), any());
        when(game.arena("test")).thenReturn(arena);
        World world = mock(World.class);
        when(world.getName()).thenReturn("test-world");
        when(arena.world()).thenReturn(world);
        when(arena.id()).thenReturn("test");
        when(arena.getName()).thenReturn("Test Arena");
        when(arena.getStatus()).thenReturn(RUNNING);
        when(arena.getMinPlayers()).thenReturn(2);
        when(arena.getMaxPlayers()).thenReturn(8);
        when(arena.numPlayers()).thenReturn(3);
        when(arena.numSpectators()).thenReturn(1);
        Location location = new Location(world, 1, 64, 2);
        SCRegion region = mock(SCRegion.class);
        when(region.getMinimumLocation()).thenReturn(location);
        when(region.getMaximumLocation()).thenReturn(new Location(world, 5, 70, 6));
        when(arena.getRegion()).thenReturn(region);
        when(arena.getLobbySpawn()).thenReturn(location);
        List<Location> spawns = (List<Location>) field(settings.getClass(), "spawns").get(settings);
        spawns.add(location);
        if (namespace.equals("horserace") || namespace.equals("minecartrace")) {
            ((List<SCRegion>) field(settings.getClass(), "checkpoints").get(settings)).add(region);
        } else if (namespace.equals("mobshooter") || namespace.equals("punchthebat")) {
            ((List<Location>) field(settings.getClass(), "targets").get(settings)).add(location);
        }
        CommandContext ctx = mock(CommandContext.class);
        when(ctx.numArgs()).thenReturn(2);
        when(ctx.getArgLower(0)).thenReturn("info");
        when(ctx.getArg(1)).thenReturn("test");
        var constructor = Class.forName(prefix + "Command").getDeclaredConstructor(STEMCraftAPI.class, ownerType);
        constructor.setAccessible(true);
        Object command = constructor.newInstance(null, owner);
        var execute = command.getClass().getDeclaredMethod("execute", CommandContext.class);
        execute.setAccessible(true);
        execute.invoke(command, ctx);
        verify(ctx).info(" - Name: Test Arena");
        verify(ctx).info(" - World: test-world");
        verify(ctx).info(" - Players: 3/8");
        verify(ctx).info(" - Spectators: 1");
        verify(ctx).info(" - Min players: 2");
        verify(ctx).info(" - Start countdown: 30 sec");
        verify(ctx).info(" - Reset countdown: 20 sec");
        verify(ctx).info(" - Round duration: 180 sec");
        verify(ctx).info(" - Lobby: test-world 1,64,2");
        verify(ctx).info(" - Spectator: unset");
        verify(ctx).info(" - Arena region: test-world 1,64,2 -> 5,70,6");
        verify(ctx).info(" - Starting spawns: 1");
        verify(arena, never()).validate();
        switch (namespace) {
            case "floorshuffle", "volcano" -> verify(ctx).info(" - Floor region: unset");
            case "horserace", "minecartrace" -> {
                verify(ctx).info(" - Laps: 3");
                verify(ctx).info(" - Checkpoints (last is the finish): 1");
                verify(ctx).info("   - 1: test-world 1,64,2 -> 5,70,6");
            }
            case "mobshooter", "punchthebat" -> {
                verify(ctx).info(" - Target count: 12");
                verify(ctx).info(" - Target spawns: 1");
            }
            case "quake" -> {
                verify(ctx).info(" - Kill target: 20");
                verify(ctx).info(" - Reload: 30 ticks");
            }
        }
        if (namespace.equals("floorshuffle") || namespace.equals("volcano")) {
            verify(ctx).info(" - Floor start delay: 15 sec");
            verify(ctx).info(" - Spawn mode: configured starting spawns");
            spawns.clear();
            execute.invoke(command, ctx);
            verify(ctx).info(" - Spawn mode: random floor blocks, one block above the floor");
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

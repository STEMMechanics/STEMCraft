package dev.stemcraft.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.command.CommandException;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.config.ConfigFileImpl;
import dev.stemcraft.service.command.CommandContextImpl;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.DISABLED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MiniGameStartDelayCommandTest {
    @TempDir Path directory;
    private STEMCraftAPI previousApi;

    @BeforeEach
    void setupMessages() {
        previousApi = STEMCraftAPI.api();
        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        when(api.locales().resolve(anyString())).thenAnswer(call -> call.getArgument(0));
        InstanceHolder.set(api);
    }

    @AfterEach
    void restoreApi() {
        InstanceHolder.set(previousApi);
    }

    @ParameterizedTest
    @CsvSource({"floorshuffle,FloorShuffle,0", "floorshuffle,FloorShuffle,25", "floorshuffle,FloorShuffle,60",
        "volcano,Volcano,0", "volcano,Volcano,25", "volcano,Volcano,60"})
    void settingSavesTheExactDelayWithoutChangingTheLobbyCountdown(String namespace, String name, int seconds) throws Exception {
        Fixture fixture = fixture(namespace, name);
        fixture.execute(Integer.toString(seconds));
        ConfigFileImpl saved = loadConfig(namespace);
        assertEquals(seconds, saved.getInt("arenas.test.start-delay-seconds"));
        assertEquals(30, saved.getInt("arenas.test.countdown"));
    }

    @ParameterizedTest
    @CsvSource({"floorshuffle,FloorShuffle,-1", "floorshuffle,FloorShuffle,61", "floorshuffle,FloorShuffle,abc",
        "volcano,Volcano,-1", "volcano,Volcano,61", "volcano,Volcano,abc"})
    void invalidDelayDoesNotChangeOrSaveTheArena(String namespace, String name, String value) throws Exception {
        Fixture fixture = fixture(namespace, name);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> fixture.execute(value));
        assertInstanceOf(CommandException.class, failure.getCause());
        assertEquals(15, field(fixture.settings().getClass(), "startDelaySeconds").getInt(fixture.settings()));
        assertFalse(loadConfig(namespace).contains("arenas.test"));
    }

    private Fixture fixture(String namespace, String name) throws Exception {
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
        when(arena.getStatus()).thenReturn(DISABLED);
        when(arena.getOccupants()).thenReturn(List.of());
        when(arena.getMinPlayers()).thenReturn(2);
        when(arena.getMaxPlayers()).thenReturn(8);
        var configConstructor = Class.forName(prefix + "Config")
            .getDeclaredConstructor(STEMCraftAPI.class, ownerType, ConfigFile.class);
        configConstructor.setAccessible(true);
        field(ownerType, "config").set(owner, configConstructor.newInstance(null, owner, loadConfig(namespace)));
        var commandConstructor = Class.forName(prefix + "Command").getDeclaredConstructor(STEMCraftAPI.class, ownerType);
        commandConstructor.setAccessible(true);
        Object command = commandConstructor.newInstance(null, owner);
        Method execute = command.getClass().getDeclaredMethod("execute", CommandContext.class);
        execute.setAccessible(true);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        Command registeredCommand = mock(Command.class);
        when(registeredCommand.getUsage()).thenReturn("/" + namespace + " set <arena> startdelay <seconds>");
        return new Fixture(command, execute, settings, namespace, sender, registeredCommand);
    }

    private ConfigFileImpl loadConfig(String namespace) {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(directory.toFile(), namespace + ".yml", true));
        return config;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record Fixture(Object command, Method executor, Object settings, String namespace,
                           CommandSender sender, Command registeredCommand) {
        void execute(String value) throws Exception {
            executor.invoke(command, new CommandContextImpl(registeredCommand, sender, namespace,
                List.of("set", "test", "startdelay", value)));
        }
    }
}

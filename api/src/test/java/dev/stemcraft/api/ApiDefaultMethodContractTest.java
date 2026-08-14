package dev.stemcraft.api;

import dev.stemcraft.api.capability.HasMessages;
import dev.stemcraft.api.capability.HasMeta;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.factory.ChunkGeneratorFactory;
import dev.stemcraft.api.message.TokenProcessor;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.hologram.HologramService;
import dev.stemcraft.api.service.locale.LocaleService;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import dev.stemcraft.api.service.resourcepack.ResourcePackWriter;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildTarget;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.api.service.selection.SelectionService;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ApiDefaultMethodContractTest {
    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("default-tests");
        player = server.addPlayer("Alex");
        resetPlayerUtilBedrockCache();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetPlayerUtilBedrockCache();
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void hasMetaDefaultGetterUsesNullFallback() {
        @SuppressWarnings("unchecked")
        HasMeta<MiniGameArena> meta = mock(HasMeta.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        when(meta.get("score", Integer.class, null)).thenReturn(42);

        assertEquals(42, meta.get("score", Integer.class));
        verify(meta).get("score", Integer.class, null);
    }

    @Test
    void hasMessagesDefaultOverloadsDelegateToCoreMethods() {
        RecordingHasMessages messages = new RecordingHasMessages();
        RuntimeException cause = new RuntimeException("boom");

        messages.debug("debug-key", "one", 2);
        assertEquals("debug", messages.invocation);
        assertNull(messages.sender);
        assertNull(messages.throwable);
        assertArrayEquals(new Object[]{"one", 2}, messages.placeholders);

        messages.log("log-key", cause, "name", "Alex");
        assertEquals("log", messages.invocation);
        assertSame(cause, messages.throwable);
        assertArrayEquals(new Object[]{"name", "Alex"}, messages.placeholders);

        messages.send(player, "send-key", "value");
        assertEquals("send", messages.invocation);
        assertSame(player, messages.sender);
        assertArrayEquals(new Object[]{"value"}, messages.placeholders);

        messages.info("info-key", "value");
        assertEquals("info", messages.invocation);

        messages.warn("warn-key", cause, "value");
        assertEquals("warn", messages.invocation);
        assertSame(cause, messages.throwable);

        messages.error(player, "error-key", "value");
        assertEquals("error", messages.invocation);
        assertSame(player, messages.sender);

        messages.success("success-key", "value");
        assertEquals("success", messages.invocation);

        messages.broadcast("broadcast-key", player, "value");
        assertEquals("broadcast", messages.invocation);
        assertEquals(List.of(player), messages.exclude);
        assertArrayEquals(new Object[]{"value"}, messages.placeholders);

        messages.broadcast("broadcast-key", "value");
        assertNull(messages.exclude);
    }

    @Test
    void messageServiceDefaultOverloadsDelegateToCoreMethods() {
        RecordingMessageService service = new RecordingMessageService();
        RuntimeException cause = new RuntimeException("boom");

        service.debug("debug-key", "one");
        assertEquals("debug", service.invocation);
        assertArrayEquals(new Object[]{"one"}, service.placeholders);

        service.log("log-key", cause, "one");
        assertEquals("log", service.invocation);
        assertSame(cause, service.throwable);

        service.send(player, "send-key", "one");
        assertEquals("send", service.invocation);
        assertSame(player, service.sender);

        service.info("info-key", "one");
        assertEquals("info", service.invocation);

        service.warn("warn-key", cause, "one");
        assertEquals("warn", service.invocation);

        service.error(player, "error-key", "one");
        assertEquals("error", service.invocation);
        assertSame(player, service.sender);

        service.success("success-key", "one");
        assertEquals("success", service.invocation);

        service.broadcast("broadcast-key", player, "one");
        assertEquals(List.of(player), service.exclude);

        service.broadcast("broadcast-key", "one");
        assertNull(service.exclude);
    }

    @Test
    void localeServiceDefaultResolversUseDefaultAndPlayerLocales() {
        LocaleService localeService = mock(LocaleService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        Player localePlayer = mock(Player.class);
        when(localeService.getDefaultLocale()).thenReturn("en-AU");
        when(localeService.resolve("en-AU", "GREETING")).thenReturn("default");
        when(localeService.resolve("fr-FR", "GREETING")).thenReturn("french");
        when(localePlayer.locale()).thenReturn(Locale.FRANCE);

        assertEquals("default", localeService.resolve("GREETING"));
        assertEquals("french", localeService.resolve(localePlayer, "GREETING"));
        assertEquals("default", localeService.resolve((CommandSender) null, "GREETING"));
    }

    @Test
    void eventServiceDefaultRegisterUsesNormalPriorityAndCancelableFalse() {
        EventService service = mock(EventService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        @SuppressWarnings("unchecked")
        EventHandler<Event> handler = mock(EventHandler.class);
        Listener listener = mock(Listener.class);
        when(service.register(Event.class, handler, EventPriority.NORMAL, false)).thenReturn(listener);

        assertSame(listener, service.register(Event.class, handler));
    }

    @Test
    void selectionServiceDefaultHighlightMethodsUseIndefiniteDuration() {
        SelectionService service = mock(SelectionService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        SCRegion region = mock(SCRegion.class);
        Location location = new Location(world, 1, 2, 3);

        service.highlightRegion("r1", player, region);
        verify(service).highlightRegion("r1", player, region, -1L);

        service.highlightRegion("r2", world, region);
        verify(service).highlightRegion("r2", world, region, -1L);

        service.highlightLocation("l1", player, location);
        verify(service).highlightLocation("l1", player, location, -1L);

        service.highlightLocation("l2", world, location);
        verify(service).highlightLocation("l2", world, location, -1L);

        service.flashBlock("b1", player, location);
        verify(service).flashBlock("b1", player, location, -1L);
    }

    @Test
    void hologramServiceDefaultHelpersUseSharedDefaults() {
        HologramService service = mock(HologramService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        Location location = new Location(world, 1, 2, 3);
        when(service.closest(location, HologramService.DEFAULT_RANGE)).thenReturn(7);

        assertEquals(7, service.closest(location));
        service.saveAll();
        verify(service).save(null);
    }

    @Test
    void playerServiceDefaultWhitelistOverloadsComputePlatform() {
        RecordingPlayerService service = new RecordingPlayerService();

        assertTrue(service.isWhitelisted(player.getUniqueId(), player.getName()));
        assertEquals(player.getUniqueId(), service.lastUuid);
        assertEquals(player.getName(), service.lastUsername);
        assertEquals("java", service.lastPlatform);

        assertTrue(service.isWhitelisted(player));
        assertEquals(player.getUniqueId(), service.lastUuid);
        assertEquals(player.getName(), service.lastUsername);
        assertEquals("java", service.lastPlatform);
    }

    @Test
    void chunkGeneratorFactoryDefaultTabCompletionIsEmpty() {
        ChunkGeneratorFactory factory = options -> mock(ChunkGenerator.class);

        assertNotNull(factory.create("flat"));
        assertEquals(List.of(), factory.tabCompleteOptions("anything"));
    }

    @Test
    void miniGameDefaultHudOverloadsUsePurpleDefaults() {
        MiniGame game = mock(MiniGame.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        List<String> simple = List.of("A");
        List<String> detailed = List.of("B");
        when(game.registerHud(MiniGameArena.ArenaStatus.WAITING, simple, detailed, 1, "PURPLE")).thenReturn(game);
        when(game.registerHud(MiniGameArena.ArenaStatus.STARTING, simple, detailed, 5, "PURPLE")).thenReturn(game);

        assertSame(game, game.registerHud(MiniGameArena.ArenaStatus.WAITING, simple, detailed));
        assertSame(game, game.registerHud(MiniGameArena.ArenaStatus.STARTING, simple, detailed, 5));
    }

    @Test
    void miniGameArenaHandlerDefaultsTransitionArenaStateAndChooseSpawn() {
        MiniGameArenaHandler handler = new MiniGameArenaHandler() { };
        Location lobby = new Location(world, 10, 70, 10);
        Location spectator = new Location(world, 20, 70, 20);

        MiniGameArena idleArena = mock(MiniGameArena.class);
        when(idleArena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.IDLE);
        when(idleArena.getLobbySpawn()).thenReturn(lobby);
        assertSame(lobby, handler.onPlayerJoinArena(idleArena, player));
        verify(idleArena).setStatus(MiniGameArena.ArenaStatus.WAITING);

        MiniGameArena waitingArena = mock(MiniGameArena.class);
        when(waitingArena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.WAITING);
        when(waitingArena.numPlayers()).thenReturn(2);
        when(waitingArena.getMinPlayers()).thenReturn(2);
        when(waitingArena.getLobbySpawn()).thenReturn(lobby);
        assertSame(lobby, handler.onPlayerJoinArena(waitingArena, player));
        verify(waitingArena).setStatus(MiniGameArena.ArenaStatus.STARTING, 30);

        MiniGameArena spectatorArena = mock(MiniGameArena.class);
        when(spectatorArena.getSpectatorSpawn()).thenReturn(spectator);
        when(spectatorArena.getLobbySpawn()).thenReturn(lobby);
        assertSame(spectator, handler.onPlayerJoinSpectator(spectatorArena, player));

        MiniGameArena fallbackArena = mock(MiniGameArena.class);
        when(fallbackArena.getSpectatorSpawn()).thenReturn(null);
        when(fallbackArena.getLobbySpawn()).thenReturn(lobby);
        assertSame(lobby, handler.onPlayerJoinSpectator(fallbackArena, player));

        when(waitingArena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.WAITING);
        assertTrue(handler.isActive(waitingArena));
        assertTrue(handler.isJoinable(waitingArena));

        MiniGameArena setupArena = mock(MiniGameArena.class);
        when(setupArena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.SETUP);
        assertFalse(handler.isActive(setupArena));
        assertFalse(handler.isJoinable(setupArena));
    }

    @Test
    void miniGamePlayerAndTeamDefaultScoreHelpersUseUnitDelta() {
        MiniGamePlayer miniGamePlayer = mock(MiniGamePlayer.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        miniGamePlayer.addScore();
        miniGamePlayer.subScore();
        verify(miniGamePlayer).addScore(1);
        verify(miniGamePlayer).subScore(1);

        MiniGameTeam team = mock(MiniGameTeam.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        team.addScore();
        team.subScore();
        verify(team).addScore(1);
        verify(team).subScore(1);
    }

    @Test
    void miniGameArenaDefaultHelpersDelegateAndComputeStatuses() {
        MiniGameArena arena = mock(MiniGameArena.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        AtomicInteger countdown = new AtomicInteger(3);
        when(arena.getCountdown()).thenAnswer(invocation -> countdown.get());
        doAnswer(invocation -> {
            countdown.set(invocation.getArgument(0));
            return arena;
        }).when(arena).setCountdown(anyInt());
        doAnswer(invocation -> arena).when(arena).setStatus(any(MiniGameArena.ArenaStatus.class), anyInt());

        Player spectator = server.addPlayer("Spectator");
        Location celebrationLocation = new Location(world, 5, 70, 5);

        assertSame(arena, arena.setStatus(MiniGameArena.ArenaStatus.RUNNING));
        verify(arena).setStatus(MiniGameArena.ArenaStatus.RUNNING, 0);

        assertEquals(2, arena.decrementCountdown());
        assertEquals(2, countdown.get());

        when(arena.getPlayers()).thenReturn(List.of(player));
        when(arena.getSpectators()).thenReturn(List.of(spectator));
        assertEquals(List.of(player, spectator), arena.getOccupants());

        when(arena.hasPlayer(player)).thenReturn(true);
        assertTrue(arena.hasOccupant(player));
        arena.removeOccupant(player);
        verify(arena).removePlayer(player);
        clearInvocations(arena);

        arena.removeAllOccupants();
        verify(arena).removePlayer(player);
        verify(arena).removeSpectator(spectator);

        when(arena.getPlayerProtectionRemaining(player)).thenReturn(5);
        assertTrue(arena.getPlayerProtection(player));
        arena.setPlayerProtection(player, true);
        verify(arena).setPlayerProtection(player, true, -1);

        arena.giveKit(player, "starter");
        verify(arena).giveKit(player, "starter", true);

        arena.setUnlimitedAmmo(Material.ARROW);
        verify(arena).setUnlimitedAmmo(Material.ARROW, true);

        arena.setUnlimitedPlacement(Material.STONE);
        verify(arena).setUnlimitedPlacement(Material.STONE, true);

        arena.startCelebration("celebration", celebrationLocation, 10, Color.RED);
        verify(arena).startCelebration("celebration", List.of(celebrationLocation), 10, Color.RED);

        arena.startWinnerCelebration(celebrationLocation, 20, Color.BLUE);
        verify(arena).startCelebration("winner", List.of(celebrationLocation), 20, Color.BLUE);

        arena.stopWinnerCelebration();
        verify(arena).stopCelebration("winner");

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.WAITING);
        assertTrue(arena.isActive());
        assertTrue(arena.isJoinable());
        assertFalse(arena.isInConfigMode());

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.SETUP);
        assertFalse(arena.isActive());
        assertFalse(arena.isJoinable());
        assertTrue(arena.isInConfigMode());
    }

    @Test
    void commandContextDefaultHelpersDelegateToUnderlyingMethods() {
        CommandContext ctx = mock(CommandContext.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        Command command = mock(Command.class);
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
        RuntimeException marker = new RuntimeException("stop");

        when(command.getUsage()).thenReturn("/root usage");
        when(ctx.getCommand()).thenReturn(command);
        when(ctx.getArg(0, null)).thenReturn("Hello");
        when(ctx.getArgAsBoolean(1, false)).thenReturn(true);
        when(ctx.getArgAsInt(2, 0, null, null)).thenReturn(7);
        when(ctx.getArgAsFloat(3, 0.0f, null, null)).thenReturn(1.5f);
        when(ctx.getArgAsDouble(4, 0.0d, null, null)).thenReturn(2.5d);
        when(ctx.getArgsAsString(1, "")).thenReturn("joined");
        when(ctx.getPlayer(5, null)).thenReturn(player);
        when(ctx.getPlayer(6, null)).thenReturn(null);
        when(ctx.isPlayer()).thenReturn(true);
        when(ctx.asPlayer()).thenReturn(player);
        when(ctx.getArgAsOfflinePlayer(7, null)).thenReturn(offlinePlayer);
        when(ctx.getArgAsDuration(8, null)).thenReturn(Duration.ofSeconds(30));
        when(ctx.getArgAsWorld(9, null)).thenReturn(world);
        when(ctx.hasFlag("silent", false)).thenReturn(true);
        when(ctx.getOption("mode", null)).thenReturn("test");
        doThrow(marker).when(ctx).returnError(eq("/root usage"), any(Object[].class));

        ctx.dropArg();
        verify(ctx).dropArgs(1);
        assertTrue(ctx.hasFlag("silent"));
        assertEquals("test", ctx.getOption("mode"));
        assertEquals("hello", ctx.getArgLower(0));
        assertEquals("HELLO", ctx.getArgUpper(0));
        assertTrue(ctx.getArgAsBoolean(1));
        assertEquals(7, ctx.getArgAsInt(2));
        assertEquals(1.5f, ctx.getArgAsFloat(3));
        assertEquals(2.5d, ctx.getArgAsDouble(4));
        assertEquals("joined", ctx.getArgsAsString());
        assertSame(player, ctx.getPlayer(5));
        assertSame(player, ctx.getArgAsPlayerOrSender(6));
        assertSame(offlinePlayer, ctx.getArgAsOfflinePlayer(7));
        assertEquals(Duration.ofSeconds(30), ctx.getArgAsDuration(8));
        assertSame(world, ctx.getArgAsWorld(9));
        assertSame(marker, assertThrows(RuntimeException.class, ctx::returnUsage));
        assertEquals("Alex", ctx.getSenderName());

        ctx.checkArgsNotEmpty();
        verify(ctx).checkArgsNotEmpty("");
        ctx.checkNotConsole();
        verify(ctx).checkNotConsole("");
        ctx.checkArgsSizeAtLeast(2);
        verify(ctx).checkArgsSizeAtLeast(2, "");
        ctx.checkArgIsPlayer(1);
        verify(ctx).checkArgIsPlayer(1, "", "");
        ctx.checkArgIsPlayerIfConsole(2);
        verify(ctx).checkArgIsPlayerIfConsole(2, "", "");
        ctx.checkArgIsWorld(3);
        verify(ctx).checkArgIsWorld(3, "", "");

        when(ctx.asPlayer()).thenReturn(null);
        assertEquals("SERVER", ctx.getSenderName());
    }

    @Test
    void regionListenerOverloadsDelegateToSimplerCallbacks() {
        RegionListener listener = mock(RegionListener.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        SCRegion region = mock(SCRegion.class);
        Location from = new Location(world, 1, 2, 3);
        Location to = new Location(world, 4, 5, 6);

        listener.onEnter(player, region, from, to);
        verify(listener).onEnter((LivingEntity) player, region);

        listener.onExit(player, region, from, to);
        verify(listener).onExit((LivingEntity) player, region);

        listener.onEnterWorld(player, world, from, to);
        verify(listener).onEnterWorld(player, world);

        listener.onExitWorld(player, world, from, to);
        verify(listener).onExitWorld(player, world);
    }

    @Test
    void worldBaseSettingDefaultsNormalizeConfigValuesAndNoOpLifecycle() {
        WorldBaseSetting setting = mock(WorldBaseSetting.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        ConfigSection config = mock(ConfigSection.class);

        when(setting.key()).thenReturn("difficulty");
        when(config.getString("difficulty", "unset")).thenReturn("Hard");

        assertEquals("hard", setting.get(world, config));
        assertDoesNotThrow(setting::onDisable);
        assertDoesNotThrow(() -> setting.onWorldLoad(world, config));
        assertDoesNotThrow(() -> setting.onWorldUnload(world, config));
        assertDoesNotThrow(() -> setting.onWorldDeleted("world", config));
    }

    @Test
    void resourcePackGeneratorDefaultsReturnNoOpLifecycleAndUniversalSupport() {
        TestResourcePackGenerator generator = new TestResourcePackGenerator();
        ConfigSection config = mock(ConfigSection.class);
        ResourcePackWriter writer = mock(ResourcePackWriter.class);
        ResourcePackBuildTarget target = new ResourcePackBuildTarget("26.2", 88);

        assertEquals("test-generator", generator.id());
        assertDoesNotThrow(() -> generator.onLoad(config));
        assertDoesNotThrow(generator::onUnload);
        assertDoesNotThrow(() -> generator.generate(new ResourcePackBuildContext(target, writer, config)));
        assertTrue(generator.requiredGenerators().isEmpty());
        assertEquals(PackFormatRange.all(), generator.supportedFormats());
        assertTrue(generator.supports(target));
    }

    @Test
    void commandDefaultUnregisterIsNoOp() {
        Command command = mock(Command.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        assertDoesNotThrow(command::unregister);
    }

    private static void resetPlayerUtilBedrockCache() throws Exception {
        Field installed = PlayerUtil.class.getDeclaredField("isGeyserInstalled");
        installed.setAccessible(true);
        installed.set(null, null);

        Field apiField = PlayerUtil.class.getDeclaredField("geyserApi");
        apiField.setAccessible(true);
        apiField.set(null, null);
    }

    private static final class RecordingHasMessages implements HasMessages {
        private String invocation;
        private CommandSender sender;
        private Throwable throwable;
        private Object[] placeholders;
        private List<Player> exclude;

        @Override
        public void debug(String message, Throwable ex, Object... placeholders) {
            record("debug", null, ex, placeholders, null);
        }

        @Override
        public void log(CommandSender sender, String message, Throwable ex, Object... placeholders) {
            record("log", sender, ex, placeholders, null);
        }

        @Override
        public void send(CommandSender sender, String message, Throwable ex, Object... placeholders) {
            record("send", sender, ex, placeholders, null);
        }

        @Override
        public void info(CommandSender sender, String message, Throwable ex, Object... placeholders) {
            record("info", sender, ex, placeholders, null);
        }

        @Override
        public void warn(CommandSender sender, String message, Throwable ex, Object... placeholders) {
            record("warn", sender, ex, placeholders, null);
        }

        @Override
        public void error(CommandSender sender, String message, Throwable ex, Object... placeholders) {
            record("error", sender, ex, placeholders, null);
        }

        @Override
        public void success(CommandSender sender, String message, Throwable ex, Object... placeholders) {
            record("success", sender, ex, placeholders, null);
        }

        @Override
        public void broadcast(String message, List<Player> exclude, Object... placeholders) {
            record("broadcast", null, null, placeholders, exclude);
        }

        private void record(String invocation, CommandSender sender, Throwable throwable, Object[] placeholders, List<Player> exclude) {
            this.invocation = invocation;
            this.sender = sender;
            this.throwable = throwable;
            this.placeholders = placeholders;
            this.exclude = exclude;
        }
    }

    private static final class RecordingMessageService implements MessageService {
        private String invocation;
        private CommandSender sender;
        private Throwable throwable;
        private Object[] placeholders;
        private List<Player> exclude;

        @Override
        public @NotNull TokenProcessor tokens() {
            return mock(TokenProcessor.class);
        }

        @Override
        public @NotNull String text(@Nullable CommandSender sender, @NotNull String key, @NotNull Object... placeholders) {
            return key;
        }

        @Override
        public void debug(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
            record("debug", null, ex, placeholders, null);
        }

        @Override
        public void log(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
            record("log", sender, ex, placeholders, null);
        }

        @Override
        public void send(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
            record("send", sender, ex, placeholders, null);
        }

        @Override
        public void info(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
            record("info", sender, ex, placeholders, null);
        }

        @Override
        public void warn(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
            record("warn", sender, ex, placeholders, null);
        }

        @Override
        public void error(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
            record("error", sender, ex, placeholders, null);
        }

        @Override
        public void success(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
            record("success", sender, ex, placeholders, null);
        }

        @Override
        public void broadcast(@NotNull String message, @Nullable List<Player> exclude, @NotNull Object... placeholders) {
            record("broadcast", null, null, placeholders, exclude);
        }

        private void record(String invocation, CommandSender sender, Throwable throwable, Object[] placeholders, List<Player> exclude) {
            this.invocation = invocation;
            this.sender = sender;
            this.throwable = throwable;
            this.placeholders = placeholders;
            this.exclude = exclude;
        }
    }

    private static final class RecordingPlayerService implements PlayerService {
        private UUID lastUuid;
        private String lastUsername;
        private String lastPlatform;

        @Override
        public void hide(@NotNull Player player) {
        }

        @Override
        public void show(@NotNull Player player) {
        }

        @Override
        public @Nullable ResolvedPlayer resolveIdentity(@Nullable String input) {
            return null;
        }

        @Override
        public boolean isWhitelisted(@Nullable UUID uuid, @Nullable String username, @Nullable String platform) {
            this.lastUuid = uuid;
            this.lastUsername = username;
            this.lastPlatform = platform;
            return true;
        }
    }

    private static final class TestResourcePackGenerator implements ResourcePackGenerator {
        @Override
        public @NotNull String id() {
            return "test-generator";
        }

        @Override
        public void generate(@NotNull ResourcePackBuildContext context) {
        }
    }
}

package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.service.AuditServiceImpl;
import dev.stemcraft.service.ConfigServiceImpl;
import dev.stemcraft.service.DatabaseServiceImpl;
import dev.stemcraft.service.EventServiceImpl;
import dev.stemcraft.service.HologramServiceImpl;
import dev.stemcraft.service.ItemServiceImpl;
import dev.stemcraft.service.LocaleServiceImpl;
import dev.stemcraft.service.MotdServiceImpl;
import dev.stemcraft.service.PlaceholderServiceImpl;
import dev.stemcraft.service.PlacedObjectServiceImpl;
import dev.stemcraft.service.ProfanityFilterServiceImpl;
import dev.stemcraft.service.ProtectionServiceImpl;
import dev.stemcraft.service.PlayerServiceImpl;
import dev.stemcraft.service.PlayerStatsServiceImpl;
import dev.stemcraft.service.PunishmentServiceImpl;
import dev.stemcraft.service.RecipeServiceImpl;
import dev.stemcraft.service.RegionServiceImpl;
import dev.stemcraft.service.DialogServiceImpl;
import dev.stemcraft.api.service.mailbox.MailboxService;
import dev.stemcraft.service.resourcepack.ResourcePackServiceImpl;
import dev.stemcraft.service.SelectionServiceImpl;
import dev.stemcraft.service.TaskServiceImpl;
import dev.stemcraft.service.WebServiceImpl;
import dev.stemcraft.service.command.CommandServiceImpl;
import dev.stemcraft.service.message.MessageServiceImpl;
import dev.stemcraft.service.minigame.MiniGameServiceImpl;
import dev.stemcraft.service.tabcompletion.TabCompleteServiceImpl;
import dev.stemcraft.service.world.WorldServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class STEMCraftAPIImplTest {
    private static final List<String> SERVICE_ACCESSOR_NAMES = List.of(
        "audit",
        "commands",
        "config",
        "database",
        "dialogs",
        "events",
        "holograms",
        "items",
        "locales",
        "mailboxes",
        "messages",
        "minigames",
        "motd",
        "placedObjects",
        "players",
        "placeholders",
        "protections",
        "profanityFilter",
        "punishments",
        "playerStats",
        "resourcePacks",
        "recipes",
        "selections",
        "regions",
        "tabComplete",
        "tasks",
        "web",
        "worlds"
    );

    private STEMCraft plugin;
    private STEMCraftAPIImpl api;
    private File dataFolder;
    private Map<String, Object> expectedDelegates;

    @BeforeEach
    void setUp() {
        plugin = mock(STEMCraft.class);
        api = new STEMCraftAPIImpl(plugin);
        dataFolder = new File("build/tmp/tests/stemcraft-api");

        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.isMaintenanceMode()).thenReturn(true);

        AuditServiceImpl audit = mock(AuditServiceImpl.class);
        CommandServiceImpl commands = mock(CommandServiceImpl.class);
        ConfigServiceImpl config = mock(ConfigServiceImpl.class);
        DatabaseServiceImpl database = mock(DatabaseServiceImpl.class);
        DialogServiceImpl dialogs = mock(DialogServiceImpl.class);
        EventServiceImpl events = mock(EventServiceImpl.class);
        HologramServiceImpl holograms = mock(HologramServiceImpl.class);
        ItemServiceImpl items = mock(ItemServiceImpl.class);
        LocaleServiceImpl locales = mock(LocaleServiceImpl.class);
        MailboxService mailboxes = mock(MailboxService.class);
        MessageServiceImpl messages = mock(MessageServiceImpl.class);
        MiniGameServiceImpl minigames = mock(MiniGameServiceImpl.class);
        MotdServiceImpl motd = mock(MotdServiceImpl.class);
        PlacedObjectServiceImpl placedObjects = mock(PlacedObjectServiceImpl.class);
        PlayerServiceImpl players = mock(PlayerServiceImpl.class);
        PlaceholderServiceImpl placeholders = mock(PlaceholderServiceImpl.class);
        ProtectionServiceImpl protections = mock(ProtectionServiceImpl.class);
        ProfanityFilterServiceImpl profanityFilter = mock(ProfanityFilterServiceImpl.class);
        PunishmentServiceImpl punishments = mock(PunishmentServiceImpl.class);
        PlayerStatsServiceImpl playerStats = mock(PlayerStatsServiceImpl.class);
        ResourcePackServiceImpl resourcePacks = mock(ResourcePackServiceImpl.class);
        RecipeServiceImpl recipes = mock(RecipeServiceImpl.class);
        SelectionServiceImpl selections = mock(SelectionServiceImpl.class);
        RegionServiceImpl regions = mock(RegionServiceImpl.class);
        TabCompleteServiceImpl tabComplete = mock(TabCompleteServiceImpl.class);
        TaskServiceImpl tasks = mock(TaskServiceImpl.class);
        WebServiceImpl web = mock(WebServiceImpl.class);
        WorldServiceImpl worlds = mock(WorldServiceImpl.class);

        when(plugin.audit()).thenReturn(audit);
        when(plugin.commands()).thenReturn(commands);
        when(plugin.config()).thenReturn(config);
        when(plugin.database()).thenReturn(database);
        when(plugin.dialogs()).thenReturn(dialogs);
        when(plugin.events()).thenReturn(events);
        when(plugin.holograms()).thenReturn(holograms);
        when(plugin.items()).thenReturn(items);
        when(plugin.locales()).thenReturn(locales);
        when(plugin.mailboxes()).thenReturn(mailboxes);
        when(plugin.messages()).thenReturn(messages);
        when(plugin.minigames()).thenReturn(minigames);
        when(plugin.motd()).thenReturn(motd);
        when(plugin.placedObjects()).thenReturn(placedObjects);
        when(plugin.players()).thenReturn(players);
        when(plugin.placeholders()).thenReturn(placeholders);
        when(plugin.protections()).thenReturn(protections);
        when(plugin.profanityFilter()).thenReturn(profanityFilter);
        when(plugin.punishments()).thenReturn(punishments);
        when(plugin.playerStats()).thenReturn(playerStats);
        when(plugin.resourcePack()).thenReturn(resourcePacks);
        when(plugin.recipes()).thenReturn(recipes);
        when(plugin.selections()).thenReturn(selections);
        when(plugin.regions()).thenReturn(regions);
        when(plugin.tabComplete()).thenReturn(tabComplete);
        when(plugin.tasks()).thenReturn(tasks);
        when(plugin.web()).thenReturn(web);
        when(plugin.worlds()).thenReturn(worlds);

        expectedDelegates = new LinkedHashMap<>();
        expectedDelegates.put("audit", audit);
        expectedDelegates.put("commands", commands);
        expectedDelegates.put("config", config);
        expectedDelegates.put("database", database);
        expectedDelegates.put("dialogs", dialogs);
        expectedDelegates.put("events", events);
        expectedDelegates.put("holograms", holograms);
        expectedDelegates.put("items", items);
        expectedDelegates.put("locales", locales);
        expectedDelegates.put("mailboxes", mailboxes);
        expectedDelegates.put("messages", messages);
        expectedDelegates.put("minigames", minigames);
        expectedDelegates.put("motd", motd);
        expectedDelegates.put("placedObjects", placedObjects);
        expectedDelegates.put("players", players);
        expectedDelegates.put("placeholders", placeholders);
        expectedDelegates.put("protections", protections);
        expectedDelegates.put("profanityFilter", profanityFilter);
        expectedDelegates.put("punishments", punishments);
        expectedDelegates.put("playerStats", playerStats);
        expectedDelegates.put("resourcePacks", resourcePacks);
        expectedDelegates.put("recipes", recipes);
        expectedDelegates.put("selections", selections);
        expectedDelegates.put("regions", regions);
        expectedDelegates.put("tabComplete", tabComplete);
        expectedDelegates.put("tasks", tasks);
        expectedDelegates.put("web", web);
        expectedDelegates.put("worlds", worlds);
    }

    @AfterEach
    void tearDown() {
        InstanceHolder.set(null, null);
    }

    @Test
    void getVersionDelegatesToPluginStaticVersion() {
        try (MockedStatic<STEMCraft> stemCraft = mockStatic(STEMCraft.class)) {
            stemCraft.when(STEMCraft::getVersion).thenReturn("1.2.3-test");

            assertEquals("1.2.3-test", api.getVersion());
        }
    }

    @Test
    void parseMinecraftVersionReturnsMajorMinorPatchComponents() {
        assertArrayEquals(new int[] {1, 21, 11}, STEMCraft.parseMinecraftVersion("1.21.11"));
        assertArrayEquals(new int[] {26, 2, 0}, STEMCraft.parseMinecraftVersion("26.2"));
        assertArrayEquals(new int[] {26, 2, 0}, STEMCraft.parseMinecraftVersion("26.2.build.60-beta"));
    }

    @Test
    void parseMinecraftVersionDefaultsMissingOrInvalidComponentsToZero() {
        assertArrayEquals(new int[] {0, 0, 0}, STEMCraft.parseMinecraftVersion(null));
        assertArrayEquals(new int[] {0, 0, 0}, STEMCraft.parseMinecraftVersion(""));
        assertArrayEquals(new int[] {0, 0, 0}, STEMCraft.parseMinecraftVersion("release"));
    }

    @Test
    void getDataFolderDelegatesToPlugin() {
        assertSame(dataFolder, api.getDataFolder());
    }

    @Test
    void isMaintenanceModeDelegatesToPlugin() {
        assertTrue(api.isMaintenanceMode());
    }

    @ParameterizedTest
    @MethodSource("serviceAccessorNames")
    void serviceAccessorsDelegateToMatchingPluginImplementation(String methodName) throws Exception {
        Method method = STEMCraftAPIImpl.class.getMethod(methodName);

        assertSame(expectedDelegates.get(methodName), method.invoke(api));
    }

    @Test
    void serviceAccessorListMatchesApiInterface() {
        Set<String> expectedMethodNames = new HashSet<>(SERVICE_ACCESSOR_NAMES);
        Set<String> actualMethodNames = Stream.of(STEMCraftAPI.class.getDeclaredMethods())
            .filter(method -> !Modifier.isStatic(method.getModifiers()))
            .filter(method -> method.getParameterCount() == 0)
            .map(Method::getName)
            .filter(methodName -> !Set.of("getVersion", "getDataFolder", "isMaintenanceMode").contains(methodName))
            .collect(Collectors.toSet());

        assertEquals(actualMethodNames, expectedMethodNames);
    }

    @Test
    void staticApiAccessorReturnsHeldInstance() {
        InstanceHolder.set(api, plugin);

        assertSame(api, STEMCraftAPI.api());
    }

    private static Stream<String> serviceAccessorNames() {
        return SERVICE_ACCESSOR_NAMES.stream();
    }
}

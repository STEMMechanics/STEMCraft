package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.dialog.DialogBuilder;
import dev.stemcraft.config.ConfigFileImpl;
import dev.stemcraft.service.command.CommandBuilderImpl;
import dev.stemcraft.service.tabcompletion.TabCompleteServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InteractiveMenusTest {
    @TempDir Path directory;
    private STEMCraftAPI previousApi;
    private STEMCraftAPI api;
    private ServerMock server;
    private PlayerMock player;
    private MockedStatic<STEMCraft> pluginAccess;
    private InteractiveMenus menus;

    @BeforeEach
    void setup() {
        previousApi = STEMCraftAPI.api();
        server = MockBukkit.mock();
        server.addSimpleWorld("interactive-menu-tests");
        player = spy(server.addPlayer());
        doNothing().when(player).openBook(any(ItemStack.class));
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        when(api.locales().resolve(anyString())).thenAnswer(call -> call.getArgument(0));
        InstanceHolder.set(api);
    }

    @AfterEach
    void cleanup() {
        if (menus != null) menus.onDisable();
        if (pluginAccess != null) pluginAccess.close();
        InstanceHolder.set(previousApi);
        MockBukkit.unmock();
    }

    @Test
    void featureDiscoveryWorksWithoutGeyserOrCumulusInstalled() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.geysermc.geyser.api.GeyserApi", false, loader));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.geysermc.cumulus.form.util.FormBuilder", false, loader));
        LinkageError loadingFailure = null;
        Class<?> menuClass = null;
        try {
            menuClass = Class.forName("dev.stemcraft.feature.InteractiveMenus", true, loader);
        } catch (LinkageError error) {
            loadingFailure = error;
        }
        assertNull(loadingFailure, "An optional Bedrock integration must not prevent Java feature discovery");
        assertNotNull(menuClass);
        assertTrue(BaseFeature.class.isAssignableFrom(menuClass));
        assertInstanceOf(BaseFeature.class, menuClass.getConstructor(STEMCraftAPI.class).newInstance(mock(STEMCraftAPI.class)));
    }

    @Test
    void javaPlayersCanOpenMenuBooksWithoutTheBedrockIntegration() throws Exception {
        enableMenus();
        // MockBukkit does not implement BookMeta.addPages(Component...).
        BookMeta meta = mock(BookMeta.class);
        try (MockedConstruction<ItemStack> books = mockConstruction(ItemStack.class, (book, context) -> {
            when(book.getItemMeta()).thenReturn(meta);
            when(book.setItemMeta(meta)).thenReturn(true);
        })) {
            assertTrue(server.dispatchCommand(player, "imenu open test -test"));

            assertEquals(1, books.constructed().size());
            verify(player).openBook(books.constructed().getFirst());
            verify(meta).setTitle("Test Menu");
            ArgumentCaptor<Component[]> pages = ArgumentCaptor.forClass(Component[].class);
            verify(meta).addPages(pages.capture());
            assertTrue(java.util.Arrays.stream(pages.getValue()).map(PlainTextComponentSerializer.plainText()::serialize)
                .anyMatch(page -> page.contains("First item") && page.contains("Second item")));
        }
        verify(api, never()).dialogs();
    }

    @Test
    void bedrockMenusUseDialogActionsWithTheCorrectItemAndTestMode() throws Exception {
        enableMenus();
        DialogBuilder dialog = mock(DialogBuilder.class, RETURNS_SELF);
        when(api.dialogs().create("imenu:test")).thenReturn(dialog);
        when(dialog.open(player)).thenReturn(true);
        assertTrue(openBedrockForm());

        ArgumentCaptor<Component> labels = ArgumentCaptor.forClass(Component.class);
        ArgumentCaptor<Runnable> selections = ArgumentCaptor.forClass(Runnable.class);
        verify(dialog, times(2)).action(labels.capture(), selections.capture());
        assertEquals(List.of("First item\nFirst description", "Second item"), labels.getAllValues().stream()
            .map(PlainTextComponentSerializer.plainText()::serialize).toList());
        selections.getAllValues().get(1).run();
        assertEquals("Test selection: test.second", PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()));
        assertEquals("No commands configured.", PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()));
        verify(player, never()).openBook(any(ItemStack.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unavailableBedrockFormsReturnFalseForTheChatFallback(boolean linkageFailure) throws Exception {
        enableMenus();
        DialogBuilder dialog = mock(DialogBuilder.class, RETURNS_SELF);
        when(api.dialogs().create("imenu:test")).thenReturn(dialog);
        if (linkageFailure) {
            when(dialog.open(player)).thenThrow(new NoClassDefFoundError("org/geysermc/cumulus/form/util/FormBuilder"));
        }
        assertFalse(openBedrockForm());
        verify(player, never()).openBook(any(ItemStack.class));
    }

    private boolean openBedrockForm() throws Exception {
        var field = InteractiveMenus.class.getDeclaredField("menus");
        field.setAccessible(true);
        Object menu = ((Map<?, ?>) field.get(menus)).get("test");
        var method = InteractiveMenus.class.getDeclaredMethod("sendBedrockForm", Player.class, menu.getClass(), boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(menus, player, menu, true);
    }

    @SuppressWarnings("deprecation") // Bukkit's command error reporting still reads the plugin description.
    private void enableMenus() throws Exception {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(directory.toFile(), "interactive-menus.yml", true));
        config.set("menus.test.title", "<gold>Test Menu</gold>");
        config.set("menus.test.body", "Choose an item");
        config.set("menus.test.items.first.title", "First item");
        config.set("menus.test.items.first.description", "First description");
        config.set("menus.test.items.second.title", "Second item");
        STEMCraft plugin = mock(STEMCraft.class);
        when(plugin.getName()).thenReturn("STEMCraft");
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("STEMCraft", "test", STEMCraft.class.getName()));
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        pluginAccess = mockStatic(STEMCraft.class);
        pluginAccess.when(STEMCraft::getPlugin).thenReturn(plugin);
        when(api.commands().create(anyString())).thenAnswer(call -> new CommandBuilderImpl(api, call.getArgument(0)));
        TabCompleteServiceImpl completions = new TabCompleteServiceImpl(plugin, api);
        when(api.tabComplete()).thenReturn(completions);
        completions.onEnable();
        when(api.config().load("interactive-menus.yml")).thenReturn(config);
        menus = new InteractiveMenus(api);
        menus.onEnable();
    }
}

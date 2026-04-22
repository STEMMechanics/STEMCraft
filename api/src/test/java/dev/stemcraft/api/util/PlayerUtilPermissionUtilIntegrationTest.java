package dev.stemcraft.api.util;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.service.task.TaskService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.util.Tristate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.geysermc.geyser.api.GeyserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class PlayerUtilPermissionUtilIntegrationTest {
    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("player-util-tests");
        player = server.addPlayer("Alex");
        resetPlayerUtilState();
        resetPermissionUtilState();
    }

    @AfterEach
    void tearDown() throws Exception {
        InstanceHolder.set(null, null);
        resetPlayerUtilState();
        resetPermissionUtilState();
        MockBukkit.unmock();
    }

    @Test
    void playerUtilHandlesHealthTeleportItemsWhitelistAndNameResolution() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        TaskService tasks = mock(TaskService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        MessageService messages = mock(MessageService.class);
        PlayerService playerService = mock(PlayerService.class);
        when(api.tasks()).thenReturn(tasks);
        when(api.messages()).thenReturn(messages);
        when(api.players()).thenReturn(playerService);
        InstanceHolder.set(api, mock(Plugin.class));

        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(tasks).runLater(anyLong(), any(Runnable.class));

        assertEquals(20.0d, PlayerUtil.getMaxHealth(player));
        player.setHealth(5.0d);
        PlayerUtil.setMaxHealth(player);
        assertEquals(PlayerUtil.getMaxHealth(player), player.getHealth());

        ItemStack head = PlayerUtil.getHead(player);
        assertNotNull(head);
        assertEquals(Material.PLAYER_HEAD, head.getType());
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        assertTrue(meta.hasOwner());
        assertNotNull(meta.getOwnerProfile());
        assertEquals(player.getName(), meta.getOwnerProfile().getName());
        assertNull(PlayerUtil.getHead(null));

        Location target = new Location(world, 10, 70, 10);
        AtomicBoolean callbackRan = new AtomicBoolean(false);
        PlayerUtil.teleport(player, target, () -> callbackRan.set(true));
        assertEquals(target, player.getLocation());
        assertTrue(callbackRan.get());

        assertTrue(PlayerUtil.give(player, new ItemStack(Material.DIAMOND), false, false));

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            player.getInventory().setItem(i, new ItemStack(Material.DIAMOND_SWORD));
        }

        assertFalse(PlayerUtil.give(player, new ItemStack(Material.DIAMOND_SWORD), false, true));
        verify(messages).error(player, "INV_NO_ROOM");

        assertTrue(PlayerUtil.give(player, new ItemStack(Material.DIAMOND_SWORD), true, false));
        verifyNoMoreInteractions(messages);

        when(playerService.isWhitelisted(player)).thenReturn(true);
        assertTrue(PlayerUtil.isWhitelisted(player));
        verify(playerService).isWhitelisted(player);

        when(playerService.isWhitelisted(player.getUniqueId(), player.getName(), "java")).thenReturn(true);
        assertTrue(PlayerUtil.isWhitelisted(player.getUniqueId(), player.getName(), "java"));
        verify(playerService).isWhitelisted(player.getUniqueId(), player.getName(), "java");

        AtomicReference<String> resolvedOnline = new AtomicReference<>();
        assertEquals("Alex", PlayerUtil.name(player.getUniqueId(), resolvedOnline::set));
        assertEquals("Alex", resolvedOnline.get());

        UUID cachedUuid = UUID.randomUUID();
        cachedNames().put(cachedUuid, "CachedName");
        AtomicReference<String> resolvedCached = new AtomicReference<>();
        assertEquals("CachedName", PlayerUtil.name(cachedUuid, resolvedCached::set));
        assertEquals("CachedName", resolvedCached.get());

        server.setWhitelist(false);
        assertTrue(PlayerUtil.isWhitelistedVanilla(UUID.randomUUID(), "missing"));
        server.setWhitelist(true);
        player.setWhitelisted(true);
        assertTrue(PlayerUtil.isWhitelistedVanilla(player.getUniqueId(), null));
        assertFalse(PlayerUtil.isWhitelistedVanilla(null, "missing"));
    }

    @Test
    void playerUtilDetectsBedrockOnlyWhenGeyserIsPresent() throws Exception {
        assertFalse(PlayerUtil.isBedrock((Player) null));
        assertFalse(PlayerUtil.isBedrock((UUID) null));
        assertFalse(PlayerUtil.isBedrock(player));
        assertFalse(PlayerUtil.isBedrock(player.getUniqueId()));

        PluginMock geyserPlugin = PluginMock.builder().withPluginName("Geyser-Spigot").build();
        server.getPluginManager().registerLoadedPlugin(geyserPlugin);
        resetPlayerUtilState();
        GeyserApi geyserApi = mock(GeyserApi.class);
        when(geyserApi.isBedrockPlayer(player.getUniqueId())).thenReturn(true);

        try (MockedStatic<GeyserApi> mocked = mockStatic(GeyserApi.class)) {
            mocked.when(GeyserApi::api).thenReturn(geyserApi);

            assertTrue(PlayerUtil.isBedrock(player));
            assertTrue(PlayerUtil.isBedrock(player.getUniqueId()));
        }
    }

    @Test
    void permissionUtilUsesLuckPermsWhenPluginIsInstalled() throws Exception {
        UUID uuid = UUID.randomUUID();
        assertFalse(PermissionUtil.hasPermission(uuid, "stem.use"));

        PluginMock luckPermsPlugin = PluginMock.builder().withPluginName("LuckPerms").build();
        server.getPluginManager().registerLoadedPlugin(luckPermsPlugin);
        resetPermissionUtilState();
        LuckPerms luckPerms = mock(LuckPerms.class);
        UserManager userManager = mock(UserManager.class);
        User user = mock(User.class);
        CachedDataManager cachedData = mock(CachedDataManager.class);
        CachedPermissionData permissionData = mock(CachedPermissionData.class);

        when(luckPerms.getUserManager()).thenReturn(userManager);
        when(userManager.getUser(uuid)).thenReturn(user);
        when(user.getCachedData()).thenReturn(cachedData);
        when(cachedData.getPermissionData()).thenReturn(permissionData);
        when(permissionData.checkPermission("stem.use")).thenReturn(Tristate.TRUE);
        when(permissionData.checkPermission("stem.deny")).thenReturn(Tristate.FALSE);

        try (MockedStatic<LuckPermsProvider> mocked = mockStatic(LuckPermsProvider.class)) {
            mocked.when(LuckPermsProvider::get).thenReturn(luckPerms);

            assertTrue(PermissionUtil.hasPermission(uuid, "stem.use"));
            assertFalse(PermissionUtil.hasPermission(uuid, "stem.deny"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, String> cachedNames() throws Exception {
        Field field = PlayerUtil.class.getDeclaredField("NAME_CACHE");
        field.setAccessible(true);
        return (Map<UUID, String>) field.get(null);
    }

    private static void resetPlayerUtilState() throws Exception {
        Field installed = PlayerUtil.class.getDeclaredField("isGeyserInstalled");
        installed.setAccessible(true);
        installed.set(null, null);

        Field apiField = PlayerUtil.class.getDeclaredField("geyserApi");
        apiField.setAccessible(true);
        apiField.set(null, null);

        cachedNames().clear();
    }

    private static void resetPermissionUtilState() throws Exception {
        Field installed = PermissionUtil.class.getDeclaredField("isLuckPermsInstalled");
        installed.setAccessible(true);
        installed.set(null, null);

        Field apiField = PermissionUtil.class.getDeclaredField("luckPermsApi");
        apiField.setAccessible(true);
        apiField.set(null, null);
    }
}

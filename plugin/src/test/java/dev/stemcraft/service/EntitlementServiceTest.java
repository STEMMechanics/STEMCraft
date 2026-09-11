package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.config.ConfigFileImpl;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.model.data.DataMutateResult;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntitlementServiceTest {
    @TempDir Path temp;
    Connection connection;
    DatabaseServiceImpl db;
    EntitlementService service;
    ConfigFileImpl config;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<LuckPermsProvider> provider;
    Map<UUID, User> users = new HashMap<>();
    static final String BADGE = "stemcraft.badge.event-winner";

    @BeforeEach void setup() throws Exception {
        var api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        var plugin = mock(STEMCraft.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        db = new DatabaseServiceImpl(plugin, api);
        var field = DatabaseServiceImpl.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(db, connection);
        when(api.database()).thenReturn(db);
        config = new ConfigFileImpl();
        assertTrue(config.load(temp.toFile(), "config.yml", true));
        config.set("entitlements.badges.event-winner.display", ":trophy:");
        config.set("entitlements.definitions.event-winner.grants.badges", List.of("event-winner"));
        // Keep unrelated bundled-default restoration out of this fixture.
        for (String id : List.of("qol-tree-felling", "qol-vein-mining")) {
            config.set("entitlements.definitions." + id + ".when.stat.key", "skill_mining_xp");
            config.set("entitlements.definitions." + id + ".when.stat.at-least", 100);
        }
        when(api.config().load("config.yml")).thenReturn(config);
        service = new EntitlementService(plugin, api);
        bukkit = mockStatic(Bukkit.class);
        var manager = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        when(manager.getPlugin("LuckPerms")).thenReturn(plugin);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        provider = mockStatic(LuckPermsProvider.class);
        var lp = mock(LuckPerms.class, RETURNS_DEEP_STUBS);
        provider.when(LuckPermsProvider::get).thenReturn(lp);
        when(lp.getUserManager().loadUser(any(UUID.class))).thenAnswer(call -> {
            UUID uuid = call.getArgument(0);
            User user = users.computeIfAbsent(uuid, ignored -> {
                User created = mock(User.class, RETURNS_DEEP_STUBS);
                when(created.getNodes(NodeType.INHERITANCE)).thenReturn(List.of());
                when(created.data().remove(any(Node.class))).thenReturn(DataMutateResult.SUCCESS);
                when(created.data().add(any(Node.class))).thenReturn(DataMutateResult.SUCCESS);
                return created;
            });
            return CompletableFuture.completedFuture(user);
        });
        invoke("createTables");
    }

    @AfterEach void cleanup() throws Exception {
        if (provider != null) provider.close();
        if (bukkit != null) bukkit.close();
        if (connection != null) connection.close();
    }

    @Test void missingAndPreviouslyCreatedEmptyConditionsNeverAutoAward() throws Exception {
        for (boolean emptySection : List.of(false, true)) {
            if (emptySection) config.getSection("entitlements.definitions.event-winner.when.stat");
            invoke("loadConfig");
            assertEquals(emptySection, config.contains("entitlements.definitions.event-winner.when"));
            UUID player = UUID.randomUUID();
            service.recalculate(player);
            assertEquals(0, count("player_entitlements"));
            assertTrue(service.appliedBadges(player).isEmpty());
        }
    }

    @Test void cleanupPreservesManualCustomAndDirectAwardsAndUnmanagedPermissions() throws Exception {
        UUID accidental = award("calculated", true);
        UUID manual = award("manual", true);
        UUID custom = award("event-command", true);
        UUID direct = award("calculated", true);
        UUID external = award("calculated", false);
        db.update("INSERT INTO player_badges VALUES(?, 'event-winner', 1)", ps -> ps.setString(1, direct.toString()));
        invoke("loadConfig");
        invoke("loadApplied");
        invoke("repairCalculatedManualAwards");
        assertEquals(2, count("player_entitlements"));
        assertEquals(1, count("player_badges"));
        assertTrue(service.appliedBadges(accidental).isEmpty());
        for (UUID uuid : List.of(manual, custom, direct)) {
            assertEquals("event-winner", service.appliedBadges(uuid).getFirst().id());
            verify(users.get(uuid).data(), never()).remove(Node.builder(BADGE).build());
        }
        verify(users.get(accidental).data()).remove(Node.builder(BADGE).build());
        assertFalse(users.containsKey(external)); // No tracked node: preserve external LP grants.
        invoke("repairCalculatedManualAwards");
        assertEquals(2, count("player_entitlements"));
        verify(users.get(accidental).data(), times(1)).remove(Node.builder(BADGE).build());
        service.recalculate(accidental);
        assertEquals(2, count("player_entitlements"));
    }

    @Test void cleanupRetriesTrackedPermissionsWithoutAnEntitlementRow() throws Exception {
        UUID player = award("calculated", true);
        db.execute("DELETE FROM player_entitlements");
        invoke("loadConfig");
        invoke("loadApplied");
        invoke("repairCalculatedManualAwards");
        verify(users.get(player).data()).remove(Node.builder(BADGE).build());
        assertEquals(0, count("player_managed_permissions"));
    }

    @Test void calculatedAwardWithRealConditionsKeepsSharedBadgePermission() throws Exception {
        UUID player = award("calculated", true);
        config.set("entitlements.definitions.real-award.when.stat.key", "skill_mining_xp");
        config.set("entitlements.definitions.real-award.when.stat.at-least", 100);
        config.set("entitlements.definitions.real-award.grants.badges", List.of("event-winner"));
        db.update("INSERT INTO player_entitlements VALUES(?, 'real-award', 'calculated', 1)",
            ps -> ps.setString(1, player.toString()));
        invoke("loadConfig");
        invoke("loadApplied");
        invoke("repairCalculatedManualAwards");
        assertEquals(1, count("player_entitlements"));
        assertEquals("event-winner", service.appliedBadges(player).getFirst().id());
        verify(users.get(player).data(), never()).remove(Node.builder(BADGE).build());
    }

    private UUID award(String source, boolean managed) {
        UUID uuid = UUID.randomUUID();
        db.update("INSERT INTO player_entitlements VALUES(?, 'event-winner', ?, 1)", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, source);
        });
        if (managed) db.update("INSERT INTO player_managed_permissions VALUES(?, ?, 1)", ps -> {
            ps.setString(1, uuid.toString()); ps.setString(2, BADGE);
        });
        return uuid;
    }

    private int count(String table) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.getInt(1);
        }
    }

    private void invoke(String method) throws Exception {
        var declared = EntitlementService.class.getDeclaredMethod(method);
        declared.setAccessible(true);
        declared.invoke(service);
    }
}

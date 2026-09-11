package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.api.service.playerreset.PlayerResetContext;
import dev.stemcraft.api.service.playerreset.PlayerResetScope;
import dev.stemcraft.feature.QuestFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerResetServiceImplTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @ParameterizedTest
    @EnumSource(PlayerResetScope.class)
    void previewAndResetPreserveSharedNpcStateAndOtherPlayers(PlayerResetScope scope) throws Exception {
        var api=mock(STEMCraftAPI.class);
        var plugin=mock(STEMCraft.class);
        var messages=mock(MessageService.class);
        when(api.messages()).thenReturn(messages);
        try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var database=new DatabaseServiceImpl(plugin,api);
            var connectionField=DatabaseServiceImpl.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            connectionField.set(database,connection);
            when(api.database()).thenReturn(database);

            // Use the production schema, including the NPC table with no player_uuid.
            var ensureStorage=QuestFeature.class.getDeclaredMethod("ensureStorage");
            ensureStorage.setAccessible(true);
            ensureStorage.invoke(new QuestFeature(api));

            UUID target=UUID.randomUUID();
            UUID other=UUID.randomUUID();
            assertTrue(database.execute("INSERT INTO quest_npc_death_day VALUES ('shared-npc',42)"));
            for(UUID player:new UUID[]{target,other}) {
                database.update("INSERT INTO quest_progress(player_uuid,quest_id) VALUES (?, 'starter')",
                    statement->statement.setString(1,player.toString()));
            }

            var service=new PlayerResetServiceImpl(plugin,api);
            var register=PlayerResetServiceImpl.class.getDeclaredMethod("registerSqlHandlers");
            register.setAccessible(true);
            register.invoke(service);

            var plan=service.plan(target,"target",scope,"admin");
            assertEquals(1,plan.entries().stream().mapToInt(entry->entry.preview().records()).sum());
            // Preview must not delete anything.
            assertEquals(2,count(database,"quest_progress"));
            var context=new PlayerResetContext(target,"target",scope,"admin");
            for(var handler:service.handlers()) {
                if(handler.scopes().contains(scope)) handler.reset(context);
            }
            assertEquals(1,count(database,"quest_progress"));
            assertEquals(other.toString(),database.querySingleMapped(
                "SELECT player_uuid FROM quest_progress",null,result->result.getString(1)));
            assertEquals(1,count(database,"quest_npc_death_day"));
            assertEquals(42,database.querySingleMapped(
                "SELECT minecraft_day FROM quest_npc_death_day WHERE profile_id='shared-npc'",
                null,result->result.getInt(1),-1));
            assertEquals(0,service.plan(target,"target",scope,"admin").entries().stream()
                .mapToInt(entry->entry.preview().records()).sum());
            verifyNoInteractions(messages); // SQL errors must not be swallowed as zero counts.
        }
    }

    private int count(DatabaseServiceImpl database,String table) {
        return database.querySingleMapped("SELECT COUNT(*) FROM "+table,null,result->result.getInt(1),-1);
    }
}

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.database.DatabaseResultSetHandler;
import dev.stemcraft.api.service.database.DatabaseService;
import dev.stemcraft.api.service.message.MessageService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatServiceImplReportTest {
    @Test
    void unresolvedReportsStillNotifyStaffAfterAnEarlierAlert() throws Exception {
        var api = mock(STEMCraftAPI.class);
        var database = mock(DatabaseService.class);
        var messages = mock(MessageService.class);
        when(api.database()).thenReturn(database);
        when(api.messages()).thenReturn(messages);
        var service = new ChatServiceImpl(mock(STEMCraft.class), api);
        var staff = mock(Player.class);
        try (var row = mock(ResultSet.class)) {
            when(row.getLong("id")).thenReturn(42L);
            when(row.getString("reporter_uuid")).thenReturn(UUID.randomUUID().toString());
            when(row.getString("reporter_name")).thenReturn("Reporter");
            when(row.getString("message")).thenReturn("Please review this");
            when(row.getInt("alerted")).thenReturn(1);
            doAnswer(call -> {
                DatabaseResultSetHandler handler = call.getArgument(2);
                handler.accept(row);
                return null;
            }).when(database).queryEach(anyString(), any(), any());

            var alert = ChatServiceImpl.class.getDeclaredMethod("alertPendingReports", Player.class);
            alert.setAccessible(true);
            alert.invoke(service, staff);
            verify(messages).warn(eq(staff), contains("unresolved player report"));
            // Another staff member must also receive a reminder for the same unresolved report.
            var otherStaff = mock(Player.class);
            alert.invoke(service, otherStaff);
            verify(messages).warn(eq(otherStaff), contains("unresolved player report"));
            verify(database, times(2)).queryEach(contains("AND resolved = ?"), any(), any());
        }
    }

    @Test
    void delayedReminderChecksOnlineStatusAndConfiguredPermission() throws Throwable {
        var api = mock(STEMCraftAPI.class);
        var database = mock(DatabaseService.class);
        when(api.database()).thenReturn(database);
        var service = new ChatServiceImpl(mock(STEMCraft.class), api);
        var lookup = java.lang.invoke.MethodHandles.privateLookupIn(ChatServiceImpl.class, java.lang.invoke.MethodHandles.lookup());
        lookup.findSetter(ChatServiceImpl.class, "reportsEnabled", boolean.class).invoke(service, true);
        lookup.findSetter(ChatServiceImpl.class, "reportsStaffAlertPermission", String.class)
            .invoke(service, "stemcraft.moderation.alerts");
        var staff = mock(Player.class);
        when(staff.isOnline()).thenReturn(true);
        service.remindPendingReports(staff);
        verifyNoInteractions(database);

        when(staff.hasPermission("stemcraft.moderation.alerts")).thenReturn(true);
        when(staff.isOnline()).thenReturn(false);
        service.remindPendingReports(staff);
        verifyNoInteractions(database);

        when(staff.isOnline()).thenReturn(true);
        service.remindPendingReports(staff);
        verify(database).queryEach(contains("AND resolved = ?"), any(), any());
        // Empty unresolved result sets need no messages service.
        verify(api, never()).messages();
    }
}

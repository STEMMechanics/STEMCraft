package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.message.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseTransactionTest {
    private Connection connection;
    private DatabaseServiceImpl database;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        STEMCraft plugin = mock(STEMCraft.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.messages()).thenReturn(mock(MessageService.class));
        database = new DatabaseServiceImpl(plugin, api);
        var connectionField = DatabaseServiceImpl.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(database, connection);
        assertTrue(database.execute("CREATE TABLE transaction_test (id INTEGER PRIMARY KEY, value TEXT NOT NULL)"));
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
        connection.close();
    }

    @Test
    void commitsAllStatementsAsOneUnit() {
        assertTrue(database.transaction(this::insertRows));
        assertEquals(2, countRows());
    }

    @Test
    void rollsBackAllStatementsWhenOneTransactionFails() {
        assertFalse(database.transaction(connection -> {
            insertRows(connection);
            throw new java.sql.SQLException("forced failure");
        }));
        assertEquals(0, countRows());
    }

    private void insertRows(Connection connection) throws java.sql.SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO transaction_test (id, value) VALUES (?, ?)")) {
            statement.setInt(1, 1);
            statement.setString(2, "one");
            statement.executeUpdate();
            statement.setInt(1, 2);
            statement.setString(2, "two");
            statement.executeUpdate();
        }
    }

    private int countRows() {
        return database.querySingleMapped("SELECT COUNT(*) FROM transaction_test", null,
            result -> result.getInt(1), -1);
    }
}

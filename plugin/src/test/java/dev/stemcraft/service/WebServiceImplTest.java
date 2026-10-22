package dev.stemcraft.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.web.WebServiceRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebServiceImplTest {
    private ServerMock server;
    private STEMCraft plugin;
    private WebServiceImpl service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(STEMCraft.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isMaintenanceMode()).thenReturn(false);
        service = new WebServiceImpl(plugin, mock(STEMCraftAPI.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void builtInStatusEndpointReturnsExpectedPayload() {
        server.setMaxPlayers(20);
        server.addPlayer("One");
        server.addPlayer("Two");
        server.addPlayer("Three");
        service.refreshOnlinePlayerSnapshot();

        Object result = service.handleStatusEndpoint(new WebServiceRequest(
            "GET",
            "/status",
            "/status",
            Map.of(),
            Map.of(),
            new byte[0],
            "127.0.0.1"
        ));

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        assertEquals(200, response.get("responseCode"));
        assertEquals("application/json; charset=utf-8", response.get("contentType"));

        JsonObject json = JsonParser.parseString(String.valueOf(response.get("body"))).getAsJsonObject();
        assertTrue(json.get("online").getAsBoolean());
        assertEquals(3, json.get("players_online").getAsInt());
        assertEquals(20, json.get("max_players").getAsInt());
        assertEquals(server.getMinecraftVersion(), json.get("version").getAsString());
        assertFalse(json.get("maintenance").getAsBoolean());
        assertTrue(json.get("message").isJsonNull());
        assertFalse(json.has("players"));
        assertDoesNotThrowIsoOffsetDateTime(json.get("checked_at").getAsString());
    }

    @Test
    void statusCanExposeSortedOnlineUsernamesWhenConfigured() {
        server.addPlayer("Zed");
        server.addPlayer("Alex");
        ConfigSection settings = mock(ConfigSection.class);
        when(settings.getBoolean("status.players.enabled", false)).thenReturn(true);

        WebServiceImpl exposedService = new WebServiceImpl(plugin, mock(STEMCraftAPI.class)) {
            @Override
            protected ConfigSection getConfigSection() {
                return settings;
            }
        };
        exposedService.loadSettings();
        exposedService.refreshOnlinePlayerSnapshot();

        Object result = exposedService.handleStatusEndpoint(new WebServiceRequest(
            "GET",
            "/status",
            "/status",
            Map.of(),
            Map.of(),
            new byte[0],
            "127.0.0.1"
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        JsonObject json = JsonParser.parseString(String.valueOf(response.get("body"))).getAsJsonObject();
        assertEquals(List.of("Alex", "Zed"), List.of(
            json.getAsJsonArray("players").get(0).getAsString(),
            json.getAsJsonArray("players").get(1).getAsString()
        ));
        assertEquals(2, json.get("players_online").getAsInt());
        assertFalse(json.has("ip"));
        assertFalse(json.has("uuid"));
    }

    @Test
    void nonStatusRequestFallsThrough() {
        Object result = service.handleStatusEndpoint(new WebServiceRequest(
            "GET",
            "/health",
            "/health",
            Map.of(),
            Map.of("Accept", List.of("application/json")),
            new byte[0],
            "127.0.0.1"
        ));

        assertNull(result);
    }

    @Test
    void statusEndpointIncludesMaintenanceMessageWhenEnabled() {
        when(plugin.isMaintenanceMode()).thenReturn(true);

        Object result = service.handleStatusEndpoint(new WebServiceRequest(
            "GET",
            "/status",
            "/status",
            Map.of(),
            Map.of(),
            new byte[0],
            "127.0.0.1"
        ));

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        JsonObject json = JsonParser.parseString(String.valueOf(response.get("body"))).getAsJsonObject();

        assertTrue(json.get("maintenance").getAsBoolean());
        assertEquals("Server is under maintenance. Please try again later.", json.get("message").getAsString());
    }

    private static void assertDoesNotThrowIsoOffsetDateTime(String value) {
        assertNotNull(value);
        OffsetDateTime parsed = OffsetDateTime.parse(value);
        assertNotNull(parsed);
    }
}

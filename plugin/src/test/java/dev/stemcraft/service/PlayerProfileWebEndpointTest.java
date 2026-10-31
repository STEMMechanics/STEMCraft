package dev.stemcraft.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.playerprofile.PlayerProfileSection;
import dev.stemcraft.api.service.playerprofile.PlayerProfileSectionDefinition;
import dev.stemcraft.api.service.playerprofile.PlayerProfileService;
import dev.stemcraft.api.service.playerprofile.PlayerProfileSnapshot;
import dev.stemcraft.api.service.web.WebServiceRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerProfileWebEndpointTest {
    private static final String PATH_PREFIX = "/api/v1/players/";
    private static final String API_KEY = "profile-secret";
    private final UUID playerUuid = UUID.fromString("4d2d2d4c-8f1e-4f6d-9dd1-3c4b9e8d8f1a");
    private ServerMock server;
    private STEMCraftAPI api;
    private ConfigSection settings;
    private WebServiceImpl service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        STEMCraft plugin = mock(STEMCraft.class);
        api = mock(STEMCraftAPI.class);
        settings = mock(ConfigSection.class);
        when(plugin.getServer()).thenReturn(server);
        when(settings.getBoolean("player-api.enabled", false)).thenReturn(true);
        when(settings.getString("player-api.key", "")).thenReturn(API_KEY);

        PlayerProfileService profiles = mock(PlayerProfileService.class);
        when(api.playerProfiles()).thenReturn(profiles);
        when(profiles.definitions()).thenReturn(List.of(
            new PlayerProfileSectionDefinition("skills", "Skills", true),
            new PlayerProfileSectionDefinition("stats", "Statistics", true)
        ));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("period", "all");
        stats.put("updated_at", null);
        stats.put("items", List.of(Map.of("key", "deaths", "value", 2.0)));
        Map<String, Object> skills = new LinkedHashMap<>();
        skills.put("period", "lifetime");
        skills.put("items", List.of(Map.of("key", "mining", "level", 12)));
        when(profiles.snapshot(playerUuid, java.util.Set.of(), "all"))
            .thenReturn(new PlayerProfileSnapshot(
                playerUuid,
                "Alex",
                "java",
                false,
                Instant.parse("2026-10-31T00:00:00Z"),
                List.of(
                    new PlayerProfileSection("stats", "Statistics", true, stats),
                    new PlayerProfileSection("skills", "Skills", true, skills)
                )
            ));

        service = new WebServiceImpl(plugin, api) {
            @Override
            protected ConfigSection getConfigSection() {
                return settings;
            }
        };
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void capabilitiesReportProfileSections() {
        JsonObject body = responseJson(service.handleCapabilitiesEndpoint(request(
            "GET", "/api/v1/capabilities", Map.of(), "Bearer " + API_KEY
        )));

        assertEquals(1, body.get("api_version").getAsInt());
        assertTrue(body.getAsJsonObject("player_profile").get("available").getAsBoolean());
        assertEquals(2, body.getAsJsonObject("player_profile").getAsJsonArray("sections").size());
    }

    @Test
    void profileEndpointReturnsRegisteredSections() {
        JsonObject body = responseJson(service.handlePlayerProfileEndpoint(request(
            "GET", PATH_PREFIX + playerUuid + "/profile", Map.of(), "Bearer " + API_KEY
        )));

        assertEquals("Alex", body.getAsJsonObject("player").get("name").getAsString());
        assertEquals(2, body.getAsJsonObject("sections").entrySet().size());
        assertEquals(12, body.getAsJsonObject("sections")
            .getAsJsonObject("skills").getAsJsonArray("items").get(0)
            .getAsJsonObject().get("level").getAsInt());
    }

    @Test
    void profileEndpointRequiresTheScopedApiKey() {
        Map<String, Object> response = responseMap(service.handlePlayerProfileEndpoint(request(
            "GET", PATH_PREFIX + playerUuid + "/profile", Map.of(), "Bearer wrong"
        )));

        assertEquals(401, response.get("responseCode"));
    }

    private WebServiceRequest request(String method, String path, Map<String, String> query, String authorization) {
        return new WebServiceRequest(
            method,
            path,
            path,
            query,
            Map.of("Authorization", List.of(authorization)),
            new byte[0],
            "127.0.0.1"
        );
    }

    private static JsonObject responseJson(Object response) {
        return JsonParser.parseString(String.valueOf(responseMap(response).get("body"))).getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responseMap(Object response) {
        return (Map<String, Object>) response;
    }
}

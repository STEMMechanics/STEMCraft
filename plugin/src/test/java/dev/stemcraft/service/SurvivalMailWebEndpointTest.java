package dev.stemcraft.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.item.ItemService;
import dev.stemcraft.api.service.mailbox.MailSendRequest;
import dev.stemcraft.api.service.mailbox.MailSendResult;
import dev.stemcraft.api.service.mailbox.MailboxService;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.service.web.WebServiceRequest;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SurvivalMailWebEndpointTest {
    private static final String PATH = "/api/v1/survival/mail";
    private static final String API_KEY = "test-secret";
    private static final UUID RECIPIENT_UUID = UUID.fromString("4d2d2d4c-8f1e-4f6d-9dd1-3c4b9e8d8f1a");

    private ServerMock server;
    private STEMCraftAPI api;
    private MailboxService mailboxes;
    private PlayerService players;
    private ItemService items;
    private STEMCraft plugin;
    private ConfigSection settings;
    private WebServiceImpl service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(STEMCraft.class);
        api = mock(STEMCraftAPI.class);
        mailboxes = mock(MailboxService.class);
        players = mock(PlayerService.class);
        items = mock(ItemService.class);
        settings = mock(ConfigSection.class);

        when(plugin.getServer()).thenReturn(server);
        when(api.mailboxes()).thenReturn(mailboxes);
        when(api.players()).thenReturn(players);
        when(api.items()).thenReturn(items);
        when(settings.getBoolean("mail-api.enabled", false)).thenReturn(true);
        when(settings.getString("mail-api.key", "")).thenReturn(API_KEY);
        when(settings.getString("mail-api.sender", "Discord Rewards")).thenReturn("Discord Rewards");
        when(settings.getInt("mail-api.max-body-bytes", 32 * 1024)).thenReturn(32 * 1024);
        when(settings.getInt("mail-api.max-items", 26)).thenReturn(26);
        when(settings.getInt("mail-api.max-item-amount", 1024)).thenReturn(1024);
        when(settings.getLong("mail-api.max-delay-seconds", 604800L)).thenReturn(604800L);
        when(players.resolveIdentity("Alex"))
            .thenReturn(new PlayerService.ResolvedPlayer(RECIPIENT_UUID, "Alex", "java"));
        when(items.createCustomItem(anyString(), anyInt())).thenReturn(null);
        when(mailboxes.send(any(MailSendRequest.class))).thenReturn(MailSendResult.success());

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
    void authenticatedRequestConvertsSecondsToTicksAndCreatesItems() {
        Object response = service.handleSurvivalMailEndpoint(request(
            "{\"player\":\"Alex\",\"message\":\"Event reward\","
                + "\"delay_seconds\":10,\"items\":["
                + "{\"id\":\"diamond\",\"amount\":3}]}"
        ));

        assertEquals(202, responseMap(response).get("responseCode"));
        JsonObject body = responseJson(response);
        assertTrue(body.get("queued").getAsBoolean());
        assertEquals("Alex", body.get("player").getAsString());
        assertEquals(10, body.get("delay_seconds").getAsInt());

        ArgumentCaptor<MailSendRequest> request = ArgumentCaptor.forClass(MailSendRequest.class);
        verify(mailboxes).send(request.capture());
        MailSendRequest sent = request.getValue();
        assertEquals(RECIPIENT_UUID, sent.recipientUuid());
        assertEquals("Discord Rewards", sent.senderName());
        assertEquals("Event reward", sent.message());
        assertEquals(200L, sent.deliveryDelayTicks());
        assertEquals(List.of(new ItemStack(Material.DIAMOND, 3)), sent.items());
    }

    @Test
    void omittedDelayUsesConfiguredMailboxDefault() {
        Object response = service.handleSurvivalMailEndpoint(request(
            "{\"player\":\"Alex\",\"message\":\"Default timing\"}"
        ));

        assertEquals(202, responseMap(response).get("responseCode"));
        assertTrue(responseJson(response).get("delay_seconds").isJsonNull());

        ArgumentCaptor<MailSendRequest> request = ArgumentCaptor.forClass(MailSendRequest.class);
        verify(mailboxes).send(request.capture());
        assertEquals(-1L, request.getValue().deliveryDelayTicks());
    }

    @Test
    void zeroDelayIsAcceptedAsImmediateDelivery() {
        Object response = service.handleSurvivalMailEndpoint(request(
            "{\"player\":\"Alex\",\"delay_seconds\":0,\"items\":[]}"
        ));

        assertEquals(202, responseMap(response).get("responseCode"));
        ArgumentCaptor<MailSendRequest> request = ArgumentCaptor.forClass(MailSendRequest.class);
        verify(mailboxes).send(request.capture());
        assertEquals(0L, request.getValue().deliveryDelayTicks());
    }

    @Test
    void invalidKeyDoesNotSendMail() {
        Object response = service.handleSurvivalMailEndpoint(request(
            "{\"player\":\"Alex\"}", "Bearer wrong-key", null
        ));

        assertEquals(401, responseMap(response).get("responseCode"));
        verify(mailboxes, times(0)).send(any(MailSendRequest.class));
    }

    @Test
    void blankKeyIsGeneratedSavedAndAnnounced() {
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(settings.getString("mail-api.key", "")).thenReturn("");

        service.loadSettings();

        verify(settings).set(eq("mail-api.key"), argThat(value -> value instanceof String
            && ((String) value).matches("[A-Za-z0-9_-]{43}")));
        verify(settings).save();
        verify(logger).info("[web-server] Generated a new mail-api key and saved it to config.yml. Keep this key secret.");
    }

    @Test
    void idempotencyKeyPreventsDuplicateDelivery() {
        WebServiceRequest request = request(
            "{\"player\":\"Alex\",\"message\":\"Only once\",\"delay_seconds\":0}",
            "Bearer " + API_KEY,
            "discord-event-1"
        );

        Object first = service.handleSurvivalMailEndpoint(request);
        Object second = service.handleSurvivalMailEndpoint(request);

        assertEquals(responseJson(first), responseJson(second));
        verify(mailboxes, times(1)).send(any(MailSendRequest.class));
    }

    @Test
    void unknownItemIsRejectedBeforeSending() {
        Object response = service.handleSurvivalMailEndpoint(request(
            "{\"player\":\"Alex\",\"items\":[{\"id\":\"not_real\",\"amount\":1}]}"
        ));

        assertEquals(400, responseMap(response).get("responseCode"));
        verify(mailboxes, times(0)).send(any(MailSendRequest.class));
    }

    private WebServiceRequest request(String body) {
        return request(body, "Bearer " + API_KEY, null);
    }

    private WebServiceRequest request(String body, String authorization, String idempotencyKey) {
        Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
        headers.put("Authorization", List.of(authorization));
        headers.put("Content-Type", List.of("application/json; charset=utf-8"));
        if (idempotencyKey != null) {
            headers.put("Idempotency-Key", List.of(idempotencyKey));
        }
        return new WebServiceRequest(
            "POST", PATH, PATH, Map.of(), headers,
            body.getBytes(StandardCharsets.UTF_8), "127.0.0.1"
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responseMap(Object response) {
        return (Map<String, Object>) response;
    }

    private static JsonObject responseJson(Object response) {
        return JsonParser.parseString(String.valueOf(responseMap(response).get("body"))).getAsJsonObject();
    }
}

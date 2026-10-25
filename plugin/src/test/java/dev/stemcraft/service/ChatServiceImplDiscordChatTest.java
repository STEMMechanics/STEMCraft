package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.web.WebServiceRequest;
import dev.stemcraft.service.message.TokenProcessorImpl;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

class ChatServiceImplDiscordChatTest {
    private STEMCraft plugin;
    private STEMCraftAPI api;
    private ChatServiceImpl service;
    private TokenProcessorImpl tokens;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        plugin = mock(STEMCraft.class);
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        tokens = new TokenProcessorImpl();
        tokens.add("discord", "\uE12F");
        when(api.messages().tokens()).thenReturn(tokens);

        service = new ChatServiceImpl(plugin, api);
        service.configureDiscordChatInboundForTest("bridge-token", "§1[DISCORD] {displayname}: {message} ");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void authenticatedDiscordChatIsScheduledAndDuplicateMessageIdsAreIgnored() {
        WebServiceRequest request = request(
            "{\"event\":\"discord.chat\",\"event_id\":\"discord-1\","
                + "\"username\":\"alex\",\"displayname\":\"Alex\","
                + "\"message\":\"Hello from Discord\"}",
            "Bearer bridge-token"
        );

        Map<String, Object> first = response(service.handleDiscordChatEndpoint(request));
        Map<String, Object> duplicate = response(service.handleDiscordChatEndpoint(request));

        assertEquals(202, first.get("responseCode"));
        assertEquals(200, duplicate.get("responseCode"));
    }

    @Test
    void invalidBearerTokenIsRejectedBeforeScheduling() {
        Map<String, Object> response = response(service.handleDiscordChatEndpoint(request(
            "{}", "Bearer wrong-token"
        )));

        assertEquals(401, response.get("responseCode"));
    }

    @Test
    void configuredFormatUsesDiscordUsernameAndDisplayNamePlaceholders() {
        assertEquals(
            "§1[DISCORD] Alex: Hello ",
            ChatServiceImpl.formatDiscordChatMessage(
                "§1[DISCORD] {displayname}: {message} ", "alex", "Alex", "Hello"
            )
        );
    }

    @Test
    void inboundDiscordChatExpandsRegisteredGlyphTokens() {
        PlayerMock player = MockBukkit.getMock().addPlayer("Steve");
        service.configureDiscordChatInboundForTest(
            "bridge-token", ":discord: §b{displayname}: §f{message}"
        );

        Map<String, Object> response = response(service.handleDiscordChatEndpoint(request(
            "{\"event\":\"discord.chat\",\"event_id\":\"discord-glyph-1\","
                + "\"username\":\"alex\",\"displayname\":\"Alex\","
                + "\"message\":\"Hello from Discord\"}",
            "Bearer bridge-token"
        )));

        assertEquals(202, response.get("responseCode"));
        assertEquals(
            "\uE12F Alex: Hello from Discord",
            PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage())
        );
    }

    private WebServiceRequest request(String body, String authorization) {
        return new WebServiceRequest(
            "POST",
            "/api/v1/chat/discord",
            "/api/v1/chat/discord",
            Map.of(),
            Map.of(
                "Authorization", List.of(authorization),
                "Content-Type", List.of("application/json; charset=utf-8")
            ),
            body.getBytes(StandardCharsets.UTF_8),
            "127.0.0.1"
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> response(Object response) {
        return (Map<String, Object>) response;
    }

}

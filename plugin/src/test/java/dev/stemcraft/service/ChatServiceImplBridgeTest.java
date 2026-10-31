package dev.stemcraft.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.api.service.web.WebServiceEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatServiceImplBridgeTest {
    private static final WebServiceImpl.EventSubscriptionSettings SETTINGS = new WebServiceImpl.EventSubscriptionSettings(
        true,
        URI.create("https://bot.example.test/minecraft/event"),
        "token",
        "survival",
        "global",
        Duration.ofSeconds(3),
        0,
        Map.of(),
        List.of("login", "register")
    );

    @Test
    void lifecyclePayloadUsesEventNameAndStructuredData() {
        String payload = WebServiceImpl.eventPayload(new WebServiceEvent(
            UUID.fromString("7b5e1d0e-8d86-4a93-9ea2-92a57fb1b40e"),
            Instant.parse("2026-10-24T01:02:03Z"),
            "minecraft.minigame_end",
            null,
            null,
            Map.of("minigame", "bedwars", "winner_team", "red")
        ), SETTINGS);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        assertEquals("minecraft.minigame_end", json.get("event").getAsString());
        assertFalse(json.has("player"));
        assertEquals("bedwars", json.getAsJsonObject("data").get("minigame").getAsString());
        assertEquals("red", json.getAsJsonObject("data").get("winner_team").getAsString());
    }

    @Test
    void endpointNormalisationRemovesTrailingSlashes() {
        assertEquals(
            "https://bot.example.test/minecraft/event",
            WebServiceImpl.normalizeEventEndpoint("https://bot.example.test/minecraft/event///")
        );
    }

    @Test
    void chatPayloadKeepsTheExistingTopLevelMessageShape() {
        UUID playerId = UUID.randomUUID();
        String payload = WebServiceImpl.eventPayload(new WebServiceEvent(
            UUID.randomUUID(),
            Instant.parse("2026-10-24T01:02:03Z"),
            "minecraft.chat",
            playerId,
            "Alex",
            Map.of("message", "Hello from Minecraft")
        ), SETTINGS);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        assertEquals("Hello from Minecraft", json.get("message").getAsString());
        assertEquals("Alex", json.getAsJsonObject("player").get("name").getAsString());
        assertEquals(playerId.toString(), json.getAsJsonObject("player").get("uuid").getAsString());
        assertFalse(json.has("data"));
    }

    @Test
    void serverSayCommandsBecomePlainServerMessages() {
        assertEquals("Server restarting to apply updates", ChatServiceImpl.serverMessageFromCommand(
            "say Server restarting to apply updates"));
        assertEquals("Maintenance complete", ChatServiceImpl.serverMessageFromCommand(
            "/minecraft:say Maintenance complete"));
        assertEquals("", ChatServiceImpl.serverMessageFromCommand("tellraw @a {}"));
        assertEquals("", ChatServiceImpl.serverMessageFromCommand("say"));
    }

    @Test
    void commandsWithSensitiveArgumentsAreRedactedBeforeForwarding() {
        assertEquals("/login <redacted>", WebServiceImpl.redactCommand(
            "/login secret-value", List.of("login")));
        assertEquals("/spawn home", WebServiceImpl.redactCommand(
            "/spawn home", List.of("login")));
        assertEquals("/minecraft:login <redacted>", WebServiceImpl.redactCommand(
            "/minecraft:login secret-value", List.of("login")));
    }
}

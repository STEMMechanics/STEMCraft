package dev.stemcraft.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatServiceImplBridgeTest {
    private static final ChatServiceImpl.ChatBridgeSettings SETTINGS = new ChatServiceImpl.ChatBridgeSettings(
        true,
        URI.create("https://bot.example.test/minecraft/events"),
        "token",
        "survival",
        "global",
        Duration.ofSeconds(3),
        0,
        true,
        true,
        true,
        true,
        true,
        true,
        true
    );

    @Test
    void lifecyclePayloadUsesEventNameAndStructuredData() {
        String payload = ChatServiceImpl.chatBridgePayload(new ChatServiceImpl.ChatBridgeEvent(
            UUID.fromString("7b5e1d0e-8d86-4a93-9ea2-92a57fb1b40e"),
            Instant.parse("2026-10-24T01:02:03Z"),
            "minecraft.minigame_end",
            null,
            null,
            Map.of("minigame", "bedwars", "winner_team", "red"),
            SETTINGS
        ));

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        assertEquals("minecraft.minigame_end", json.get("event").getAsString());
        assertFalse(json.has("player"));
        assertEquals("bedwars", json.getAsJsonObject("data").get("minigame").getAsString());
        assertEquals("red", json.getAsJsonObject("data").get("winner_team").getAsString());
    }

    @Test
    void chatPayloadKeepsTheExistingTopLevelMessageShape() {
        UUID playerId = UUID.randomUUID();
        String payload = ChatServiceImpl.chatBridgePayload(new ChatServiceImpl.ChatBridgeEvent(
            UUID.randomUUID(),
            Instant.parse("2026-10-24T01:02:03Z"),
            "minecraft.chat",
            playerId,
            "Alex",
            Map.of("message", "Hello from Minecraft"),
            SETTINGS
        ));

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        assertEquals("Hello from Minecraft", json.get("message").getAsString());
        assertEquals("Alex", json.getAsJsonObject("player").get("name").getAsString());
        assertEquals(playerId.toString(), json.getAsJsonObject("player").get("uuid").getAsString());
        assertFalse(json.has("data"));
    }
}

package dev.stemcraft.service.message;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageServiceImplTest {
    @Test
    void broadcastConsoleFormatUsesAsciiTag() {
        String rendered = PlainTextComponentSerializer.plainText().serialize(
            MessageServiceImpl.formatBroadcastConsoleMessage("The player nomadjimbob is no longer banned.")
        );

        assertEquals("[broadcast] The player nomadjimbob is no longer banned.", rendered);
    }

    @Test
    void broadcastPlayerFormatKeepsConfiguredPrefix() {
        String rendered = PlainTextComponentSerializer.plainText().serialize(
            MessageServiceImpl.formatBroadcastPlayerMessage("<dark_purple><bold>[ ! ]</bold></dark_purple> ", "The player nomadjimbob is no longer banned.")
        );

        assertEquals("[ ! ] The player nomadjimbob is no longer banned.", rendered);
    }
}

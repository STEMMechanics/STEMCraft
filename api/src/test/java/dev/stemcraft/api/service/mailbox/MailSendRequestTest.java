package dev.stemcraft.api.service.mailbox;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MailSendRequestTest {
    @Test
    void acceptsPlayerUuidAsSenderWithoutNames() {
        UUID senderUuid = UUID.randomUUID();
        UUID recipientUuid = UUID.randomUUID();

        MailSendRequest request = new MailSendRequest(senderUuid, recipientUuid, "Hello", List.of());

        assertEquals(senderUuid, request.senderUuid());
        assertNull(request.senderName());
        assertEquals(recipientUuid, request.recipientUuid());
    }

    @Test
    void acceptsSystemSenderNameAndRecipientUuid() {
        UUID recipientUuid = UUID.randomUUID();

        MailSendRequest request = new MailSendRequest("STEMCraft", recipientUuid, "Hello", List.of());

        assertNull(request.senderUuid());
        assertEquals("STEMCraft", request.senderName());
        assertEquals(recipientUuid, request.recipientUuid());
    }
}

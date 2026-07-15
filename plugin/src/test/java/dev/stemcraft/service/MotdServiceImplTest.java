package dev.stemcraft.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotdServiceImplTest {

    @Test
    void formatLegacyMotdKeepsTwoLinesForLegacyConfiguredValues() {
        String formatted = MotdServiceImpl.formatLegacyMotd(
            "§9■ §e§lSTEMCRAFT",
            "§r                   §e★ §bEnjoy your stay! §e★"
        );

        assertEquals("§9■ §e§lSTEMCRAFT\n                   §e★ §bEnjoy your stay! §e★", formatted);
        assertTrue(formatted.contains("\n"));
    }

    @Test
    void formatLegacyMotdConvertsMiniMessageMarkupToLegacySections() {
        assertEquals(
            "§9STEMCRAFT\n§eWelcome",
            MotdServiceImpl.formatLegacyMotd(
                "<blue>STEMCRAFT</blue>",
                "<yellow>Welcome</yellow>"
            )
        );
    }
}

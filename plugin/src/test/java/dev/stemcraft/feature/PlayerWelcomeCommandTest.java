package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerWelcomeCommandTest {

    @Test
    void remainingRawTextUsesZeroBasedRawArgIndex() {
        assertEquals(
            "&eWelcome back",
            PlayerWelcomeCommand.remainingRawText(
                List.of("welcome", "addline", "first", "&eWelcome", "back"),
                3
            )
        );
    }

    @Test
    void remainingRawTextReturnsEmptyOutsideBounds() {
        assertEquals("", PlayerWelcomeCommand.remainingRawText(List.of("welcome", "addline"), 2));
        assertEquals("", PlayerWelcomeCommand.remainingRawText(List.of("welcome", "addline"), -1));
    }
}

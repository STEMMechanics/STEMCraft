package dev.stemcraft.minigame.tntrun;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TntRunMiniGameTest {
    @Test
    void heightToBottomCountsBlocksAboveArenaMinimumY() {
        TntRunMiniGame game = new TntRunMiniGame(null);

        assertEquals(40, game.heightToBottom(4, 44));
        assertEquals(0, game.heightToBottom(4, 4));
        assertEquals(0, game.heightToBottom(4, 2));
    }
}

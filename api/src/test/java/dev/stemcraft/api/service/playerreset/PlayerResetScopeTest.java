package dev.stemcraft.api.service.playerreset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerResetScopeTest {
    @Test void scopesOnlyIncludeEqualOrLowerLevels() {
        assertTrue(PlayerResetScope.PROGRESSION.includes(PlayerResetScope.PROGRESSION));
        assertFalse(PlayerResetScope.PROGRESSION.includes(PlayerResetScope.GAMEPLAY));
        assertTrue(PlayerResetScope.GAMEPLAY.includes(PlayerResetScope.PROGRESSION));
        assertTrue(PlayerResetScope.COMPLETE.includes(PlayerResetScope.GAMEPLAY));
    }
}

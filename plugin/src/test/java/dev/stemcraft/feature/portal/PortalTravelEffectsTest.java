package dev.stemcraft.feature.portal;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PortalTravelEffectsTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }
    @Test void distortionIsClientOnlyAndAlwaysRemovedOnCleanup() {
        Player player = player(); var effects = new PortalTravelEffects();
        effects.start(player);
        verify(player).sendPotionEffectChange(eq(player), argThat(effect -> effect.getType().equals(PotionEffectType.NAUSEA)
                && !effect.hasParticles() && !effect.hasIcon()));
        verify(player, never()).addPotionEffect(any());
        effects.stopAll(); effects.stop(player);
        verify(player).sendPotionEffectChangeRemove(player, PotionEffectType.NAUSEA);
    }
    @Test void realNauseaIsNotOverwrittenOrRemoved() {
        Player player = player(); var effects = new PortalTravelEffects();
        when(player.hasPotionEffect(PotionEffectType.NAUSEA)).thenReturn(true);
        effects.start(player); effects.stopAll();
        verify(player, never()).sendPotionEffectChange(any(), any());
        verify(player, never()).sendPotionEffectChangeRemove(any(), any());
    }
    @Test void realEffectAddedDuringTravelIsRestored() {
        Player player = player(); var effects = new PortalTravelEffects();
        effects.start(player);
        var real = new PotionEffect(PotionEffectType.NAUSEA, 400, 1);
        when(player.getPotionEffect(PotionEffectType.NAUSEA)).thenReturn(real);
        effects.stopAll();
        verify(player).sendPotionEffectChange(player, real);
        verify(player, never()).sendPotionEffectChangeRemove(any(), any());
    }
}

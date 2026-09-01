package dev.stemcraft.feature;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NightVisionFeatureTest {
    @Test
    void managedNightVisionHasNoParticlesOrStatusIcon() {
        PotionEffect effect = NightVisionFeature.managedEffect();

        assertTrue(effect.isInfinite());
        assertFalse(effect.isAmbient());
        assertFalse(effect.hasParticles());
        assertFalse(effect.hasIcon());
        assertTrue(NightVisionFeature.isManagedEffect(effect));
    }

    @Test
    void ordinaryNightVisionIsNotMistakenForTheManagedEffect() {
        PotionEffect ordinary = new PotionEffect(PotionEffectType.NIGHT_VISION, 1200, 0);

        assertFalse(NightVisionFeature.isManagedEffect(ordinary));
        assertFalse(NightVisionFeature.isManagedEffect(null));
    }
}

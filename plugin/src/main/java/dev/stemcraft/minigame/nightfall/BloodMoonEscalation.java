package dev.stemcraft.minigame.nightfall;

public record BloodMoonEscalation(
    int tntIncreasePerNight,
    int tntMaximumChance,
    int bucketStartNight,
    int spongeStartNight,
    int spongeRadius,
    int builderStartNight,
    int axeStartNight,
    int knockbackStartNight,
    double knockbackResistance
) {
    public static BloodMoonEscalation defaults() {
        return new BloodMoonEscalation(2, 25, 1, 5, 2, 3, 5, 3, 0.65d);
    }
}

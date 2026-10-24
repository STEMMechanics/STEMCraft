package dev.stemcraft.minigame.floorshuffle;

import dev.stemcraft.config.ConfigFileImpl;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FloorShuffleTextFormatTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"RED_CONCRETE,#ff5555", "BLUE_CONCRETE,#5555ff", "YELLOW_CONCRETE,#ffff55",
            "LIME_CONCRETE,#55ff55", "ORANGE_CONCRETE,#ffaa00", "PURPLE_CONCRETE,#aa00aa"})
    void everyColourUsesMatchingMiniMessageColour(Material material, String hex) {
        String rendered = FloorShuffleTextFormat.render(FloorShuffleTextFormat.defaults().chat(), material);
        assertEquals("<" + hex + ">Stand on " + material.name().replace("_CONCRETE", "") + "!</" + hex + ">", rendered);
        assertEquals(TextColor.fromHexString(hex), MiniMessage.miniMessage().deserialize(rendered).color());
    }

    @Test
    void savesDefaultsAndPreservesConfiguredText() {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(directory.toFile(), "floorshuffle.yml", true));
        config.set("strings.colour.chat", "Choose {colour}");
        config.set("strings.colour.subtitle", "");
        FloorShuffleTextFormat format = FloorShuffleTextFormat.load(config);
        config.save();
        assertEquals("Choose {colour}", format.chat());
        assertEquals("", format.subtitle());
        assertEquals(FloorShuffleTextFormat.defaults().title(), config.getString("strings.colour.title"));
        assertEquals(FloorShuffleTextFormat.defaults().objective(), config.getString("strings.colour.objective"));
    }
}

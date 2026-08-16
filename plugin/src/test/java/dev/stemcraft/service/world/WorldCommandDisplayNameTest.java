package dev.stemcraft.service.world;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldCommandDisplayNameTest {
    @Test
    void setsAndPersistsMultiWordDisplayName() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        WorldService worlds = mock(WorldService.class);
        WorldServiceImpl worldService = mock(WorldServiceImpl.class);
        ConfigSection config = mock(ConfigSection.class);
        CommandContext ctx = mock(CommandContext.class);
        when(api.worlds()).thenReturn(worlds);
        when(worlds.worldExists("challenge_build")).thenReturn(true);
        when(worldService.getConfigSection("challenge_build")).thenReturn(config);
        when(ctx.getArg(1)).thenReturn("challenge_build");
        when(ctx.getArgsAsString(3, "")).thenReturn("Amazing Builds");

        new WorldCommand(api, worldService).handleSubCommandDisplayName(ctx);

        verify(config).set("display-name", "Amazing Builds");
        verify(config).save();
        verify(ctx).returnSuccess(
            "WORLD_DISPLAY_NAME_SET", "world", "challenge_build", "display_name", "Amazing Builds"
        );
    }

    @Test
    void clearRemovesConfiguredNameAndReportsAutomaticName() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        WorldService worlds = mock(WorldService.class);
        WorldServiceImpl worldService = mock(WorldServiceImpl.class);
        ConfigSection config = mock(ConfigSection.class);
        CommandContext ctx = mock(CommandContext.class);
        when(api.worlds()).thenReturn(worlds);
        when(worlds.worldExists("bedwars_amazon")).thenReturn(true);
        when(worldService.getConfigSection("bedwars_amazon")).thenReturn(config);
        when(ctx.getArg(1)).thenReturn("bedwars_amazon");
        when(ctx.getArgsAsString(3, "")).thenReturn("clear");

        new WorldCommand(api, worldService).handleSubCommandDisplayName(ctx);

        verify(config).set("display-name", null);
        verify(config).save();
        verify(ctx).returnSuccess(
            "WORLD_DISPLAY_NAME_CLEARED", "world", "bedwars_amazon", "display_name", "Bedwars: Amazon"
        );
    }

    @Test
    void describesCustomAndAutomaticNamesForWorldInfo() {
        ConfigSection config = mock(ConfigSection.class);
        when(config.getString("display-name", "")).thenReturn("Amazon Arena");

        assertEquals("Amazon Arena (custom)", WorldCommand.describeDisplayName("bedwars_amazon", config));
        assertEquals("Bedwars: Amazon (automatic)", WorldCommand.describeDisplayName("bedwars_amazon", null));
    }
}

package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.service.message.TokenProcessorImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormattedSignsTest {
    private final TokenProcessorImpl tokens = new TokenProcessorImpl();

    @Test void requestedExamplesRespectSupportedCodesAndEscaping() {
        assertEquals("Hello d&m", legacy("Hello d&m"));
        assertEquals("Hello \u00a7bMan", legacy("Hello &bMan"));
        assertEquals("Hello d\u00a7b Man", legacy("Hello d&b Man"));
        assertEquals("Hello d&b Man", legacy("Hello d&&b Man"));
    }

    @Test void escapesAreConsumedOnceAndUnknownCodesRemainLiteral() {
        for (String text : List.of("&m", "&k", "&x", "&z", "Fish & Chips", "End &")) {
            assertEquals(text, legacy(text));
        }
        assertEquals("&b &m &", legacy("&&b &&m &&"));
        assertEquals("&\u00a7bBlue", legacy("&&&bBlue"));
        assertEquals("&&b", legacy("&&&&b"));
        assertEquals("\u00a7bAqua", legacy("&BAqua"));
    }

    @Test void colorsAndResetClearDecorations() {
        assertEquals("\u00a7lBold\u00a7bAqua\u00a7r plain", legacy("&lBold&bAqua&r plain"));
        assertEquals("\u00a7nUnderlined", legacy("&nUnderlined"));
        assertEquals("\u00a7oItalic", legacy("&oItalic"));
        for (char color : "0123456789abcdef".toCharArray()) {
            assertEquals("\u00a7" + color + "Text", legacy("&" + color + "Text"));
        }
    }

    @Test void glyphsUseLiveRegistryWithoutParsingTheirValuesAsFormatting() {
        tokens.add("stembot", "\ue123");
        assertEquals("\u00a7b\ue123 Welcome :missing:", legacy("&b:stembot: Welcome :missing:"));
        tokens.add("stembot", "\ue124");
        assertEquals("\ue124", legacy(":stembot:"));
        tokens.add("literal", "&b<red>");
        assertEquals("&b<red>", legacy(":literal:"));
        assertEquals("<red>literal</red>", legacy("<red>literal</red>"));
    }

    @Test void formatsEitherSignSideWithoutLeakingStylesAcrossLines() {
        var api = mock(STEMCraftAPI.class);
        var messages = mock(MessageService.class);
        when(api.messages()).thenReturn(messages);
        when(messages.tokens()).thenReturn(tokens);
        tokens.add("stembot", "\ue123");
        var feature = new FormattedSigns(api);
        for (Side side : Side.values()) {
            var untouched = Component.text("Already styled", NamedTextColor.GREEN);
            var player = mock(Player.class);
            when(player.hasPermission("stemcraft.sign.format")).thenReturn(true);
            var event = new SignChangeEvent(mock(Block.class), player, new ArrayList<>(List.of(
                Component.text("&bHello"), Component.text("Plain"), Component.text(":stembot:"), untouched)), side);
            feature.formatSign(event);
            assertEquals("\u00a7bHello", LegacyComponentSerializer.legacySection().serialize(event.line(0)));
            assertEquals(Component.text("Plain"), event.line(1));
            assertEquals("\ue123", PlainTextComponentSerializer.plainText().serialize(event.line(2)));
            assertSame(untouched, event.line(3));
            assertEquals(side, event.getSide());
        }
    }

    @Test void cancelledSignEditsAreNotTouched() {
        var feature = new FormattedSigns(mock(STEMCraftAPI.class));
        var original = Component.text("&bHello");
        var event = new SignChangeEvent(mock(Block.class), mock(Player.class),
            new ArrayList<>(List.of(original, Component.empty(), Component.empty(), Component.empty())), Side.FRONT);
        event.setCancelled(true);
        feature.formatSign(event);
        assertSame(original, event.line(0));
    }

    @Test void deniedPermissionLeavesCodesAndGlyphsLiteral() {
        var feature = new FormattedSigns(mock(STEMCraftAPI.class));
        var original = Component.text("&b:stembot:");
        var event = new SignChangeEvent(mock(Block.class), mock(Player.class),
            new ArrayList<>(List.of(original, Component.empty(), Component.empty(), Component.empty())), Side.BACK);
        feature.formatSign(event);
        assertSame(original, event.line(0));
    }

    @Test void formattingRequiresExplicitGrantEvenForOperatorsAcrossReload() {
        var server = org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            var plugin = org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin();
            var feature = new FormattedSigns(mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS));
            var player = server.addPlayer();
            player.setOp(false);
            feature.onEnable();
            assertFalse(player.hasPermission("stemcraft.sign.format"));
            player.setOp(true);
            assertFalse(player.hasPermission("stemcraft.sign.format"));
            var grant = player.addAttachment(plugin, "stemcraft.sign.format", true);
            assertTrue(player.hasPermission("stemcraft.sign.format"));
            grant.setPermission("stemcraft.sign.format", false);
            assertFalse(player.hasPermission("stemcraft.sign.format"));
            feature.onDisable();
            assertNull(server.getPluginManager().getPermission("stemcraft.sign.format"));
            feature.onEnable();
            assertFalse(player.hasPermission("stemcraft.sign.format"));
            assertFalse(server.addPlayer().hasPermission("stemcraft.sign.format"));
            grant.setPermission("stemcraft.sign.format", true);
            assertTrue(player.hasPermission("stemcraft.sign.format"));
            feature.onDisable();
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }

    private String legacy(String input) {
        return LegacyComponentSerializer.legacySection().serialize(FormattedSigns.format(input, tokens));
    }
}

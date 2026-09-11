package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.message.TokenProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

/** Player-authored sign colors and resource-pack glyphs, without parsing MiniMessage. */
public final class FormattedSigns extends BaseFeature {
    private static final NamedTextColor[] COLORS = {
        NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN,
        NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE,
        NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY,
        NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
        NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE
    };
    private Listener listener;

    public FormattedSigns(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        listener = api.events().register(SignChangeEvent.class, this::formatSign, EventPriority.HIGHEST, true);
    }

    @Override
    public void onDisable() {
        if (listener != null) HandlerList.unregisterAll(listener);
        listener = null;
    }

    void formatSign(SignChangeEvent event) {
        if (event.isCancelled()) return;
        TokenProcessor tokens = api.messages().tokens();
        for (int line = 0; line < event.lines().size(); line++) {
            Component original = event.line(line);
            if (original == null) continue;
            String text = PlainTextComponentSerializer.plainText().serialize(original);
            // Leave unrelated components supplied by other plugins intact.
            if (text.indexOf('&') >= 0 || !text.equals(tokens.apply(text))) {
                event.line(line, format(text, tokens));
            }
        }
    }

    static Component format(String text, TokenProcessor tokens) {
        Component result = Component.empty();
        StringBuilder literal = new StringBuilder();
        Style style = Style.empty();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character != '&' || index + 1 == text.length()) {
                literal.append(character);
                continue;
            }
            char next = text.charAt(index + 1);
            if (next == '&') {
                literal.append('&');
                index++;
                continue;
            }
            char code = Character.toLowerCase(next);
            int color = "0123456789abcdef".indexOf(code);
            if (color < 0 && "lnor".indexOf(code) < 0) {
                literal.append('&');
                continue;
            }
            result = result.append(Component.text(tokens.apply(literal.toString())).style(style));
            literal.setLength(0);
            // Colors and reset clear previous decorations, matching conventional color codes.
            style = color >= 0 ? Style.style(COLORS[color]) : switch (code) {
                case 'l' -> style.decorate(TextDecoration.BOLD);
                case 'n' -> style.decorate(TextDecoration.UNDERLINED);
                case 'o' -> style.decorate(TextDecoration.ITALIC);
                default -> Style.empty();
            };
            index++;
        }
        result = result.append(Component.text(tokens.apply(literal.toString())).style(style));
        return result;
    }
}

package dev.stemcraft.api.utils.chatmenu;

import net.kyori.adventure.text.Component;

import java.util.List;

public interface SCChatMenuRenderer {
    List<Component> render(int start, int count, boolean isPlayer);
}

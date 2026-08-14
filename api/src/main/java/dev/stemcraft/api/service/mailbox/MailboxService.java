/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.stemcraft.api.service.mailbox;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Public API for sending and querying mailbox deliveries. */
public interface MailboxService {
    /**
     * Queues mail for delivery. A written letter is generated automatically and
     * included with any supplied items; an empty item list sends a letter only.
     */
    @NotNull MailSendResult send(@NotNull MailSendRequest request);

    /** Returns whether the player's delivered inbox currently contains mail. */
    boolean hasMail(@NotNull UUID playerUuid);
}

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

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A request to deliver items and an accompanying letter through the mailbox system. */
public record MailSendRequest(@Nullable UUID senderUuid,
                              @Nullable String senderName,
                              @NotNull UUID recipientUuid,
                              @NotNull String message,
                              @NotNull List<ItemStack> items,
                              @Nullable Location sourceLocation,
                              long deliveryDelayTicks,
                              @Nullable String title) {
    public MailSendRequest {
        recipientUuid = Objects.requireNonNull(recipientUuid, "recipientUuid");
        senderName = senderName == null ? null : senderName.trim();
        if ((senderUuid == null) == (senderName == null || senderName.isBlank())) {
            throw new IllegalArgumentException("Provide either a sender UUID or sender name");
        }
        message = Objects.requireNonNullElse(message, "");
        title = title == null || title.isBlank() ? null : title.trim();
        items = Objects.requireNonNull(items, "items");
        items = items.stream().filter(Objects::nonNull).map(ItemStack::clone).toList();
        sourceLocation = sourceLocation == null ? null : sourceLocation.clone();
        if (deliveryDelayTicks < -1L) {
            throw new IllegalArgumentException("deliveryDelayTicks must be -1 or greater");
        }
    }

    public MailSendRequest(@Nullable UUID senderUuid,
                           @Nullable String senderName,
                           @NotNull UUID recipientUuid,
                           @NotNull String message,
                           @NotNull List<ItemStack> items,
                           @Nullable Location sourceLocation) {
        this(senderUuid, senderName, recipientUuid, message, items, sourceLocation, -1L, null);
    }

    public MailSendRequest(@NotNull UUID senderUuid,
                           @NotNull UUID recipientUuid,
                           @NotNull String message,
                           @NotNull List<ItemStack> items,
                           @Nullable Location sourceLocation) {
        this(senderUuid, null, recipientUuid, message, items, sourceLocation);
    }

    public MailSendRequest(@NotNull String senderName,
                           @NotNull UUID recipientUuid,
                           @NotNull String message,
                           @NotNull List<ItemStack> items,
                           @Nullable Location sourceLocation) {
        this(null, senderName, recipientUuid, message, items, sourceLocation);
    }

    public MailSendRequest(@NotNull UUID senderUuid,
                           @NotNull UUID recipientUuid,
                           @NotNull String message,
                           @NotNull List<ItemStack> items,
                           long deliveryDelayTicks) {
        this(senderUuid, null, recipientUuid, message, items, null, deliveryDelayTicks, null);
    }

    public MailSendRequest(@NotNull String senderName,
                           @NotNull UUID recipientUuid,
                           @NotNull String message,
                           @NotNull List<ItemStack> items,
                           long deliveryDelayTicks) {
        this(null, senderName, recipientUuid, message, items, null, deliveryDelayTicks, null);
    }

    public MailSendRequest(@NotNull UUID senderUuid,
                           @NotNull UUID recipientUuid,
                           @NotNull String message,
                           @NotNull List<ItemStack> items) {
        this(senderUuid, recipientUuid, message, items, null);
    }

    public MailSendRequest(@NotNull String senderName,
                           @NotNull UUID recipientUuid,
                           @NotNull String message,
                           @NotNull List<ItemStack> items) {
        this(senderName, recipientUuid, message, items, null);
    }

    /**
     * Creates a system-sender request with an explicit written-letter title.
     *
     * @param senderName sender shown on the letter
     * @param recipientUuid recipient UUID
     * @param message letter message
     * @param title written-book title; blank values use the mailbox default
     * @param items item attachments
     */
    public MailSendRequest(@NotNull String senderName,
                           @NotNull UUID recipientUuid,
                           @NotNull String message,
                           @Nullable String title,
                           @NotNull List<ItemStack> items) {
        this(null, senderName, recipientUuid, message, items, null, -1L, title);
    }

    /**
     * Returns a copy of this request with a different written-letter title.
     *
     * @param title written-book title; blank values use the mailbox default
     * @return request copy
     */
    public @NotNull MailSendRequest withTitle(@Nullable String title) {
        return new MailSendRequest(senderUuid, senderName, recipientUuid, message, items,
            sourceLocation, deliveryDelayTicks, title);
    }

    @Override
    public @NotNull List<ItemStack> items() {
        return items.stream().map(ItemStack::clone).toList();
    }

    @Override
    public @Nullable Location sourceLocation() {
        return sourceLocation == null ? null : sourceLocation.clone();
    }
}

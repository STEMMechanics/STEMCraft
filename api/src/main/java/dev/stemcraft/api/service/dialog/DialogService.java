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

package dev.stemcraft.api.service.dialog;

/** Creates cross-platform dialogs for Java and Bedrock players. */
public interface DialogService {
    /**
     * Creates a new dialog builder.
     *
     * @param reference caller-defined identifier returned with submitted responses
     * @return a new builder
     */
    DialogBuilder create(String reference);
}

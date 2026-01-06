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
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.api.minigame.util;

import java.util.Set;

/**
 * Utility class defining predefined arena lifecycle statuses.
 *
 * These statuses represent the various phases an arena can be in
 * during its lifecycle within the STEMCraft minigame framework,
 * however custom statuses may also be used by plugins.
 */
public final class ArenaStatus {

    public static final String WAITING  = "waiting";
    public static final String LOADING  = "loading";
    public static final String IN_GAME  = "in-game";
    public static final String ENDING   = "ending";
    public static final String CLEANUP  = "cleanup";
    public static final String DISABLED = "disabled";

    /**
     * Set of predefined arena lifecycle statuses supported by the core API.
     */
    private static final Set<String> PREDEFINED = Set.of(
            WAITING,
            LOADING,
            IN_GAME,
            ENDING,
            CLEANUP,
            DISABLED
    );

    /**
     * Utility class; no instances allowed.
     */
    private ArenaStatus() {}

    /**
     * Checks whether the given status matches one of the predefined
     * arena lifecycle statuses.
     *
     * @param status the status to check, may be {@code null}
     * @return {@code true} if the status is predefined
     */
    public static boolean isPredefined(String status) {
        if (status == null) return false;
        return PREDEFINED.contains(status.toLowerCase());
    }

    /**
     * Normalises a status string to the canonical lowercase form used by the API.
     *
     * @param status the status to normalise, may be {@code null}
     * @return the normalised status, or {@code null} if input was {@code null}
     */
    public static String normalize(String status) {
        return status == null ? null : status.toLowerCase();
    }

    /**
     * Returns an immutable view of all predefined arena statuses.
     *
     * @return an unmodifiable set of predefined statuses
     */
    public static Set<String> predefined() {
        return PREDEFINED;
    }
}
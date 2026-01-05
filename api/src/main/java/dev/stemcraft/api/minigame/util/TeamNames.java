package dev.stemcraft.api.minigame.util;

import org.bukkit.Material;

import java.util.Set;

/**
 * Utility class for working with predefined MiniGame team names.
 *
 * <p>This class provides helpers for validating and normalising the
 * built-in team identifiers exposed by {@link dev.stemcraft.api.minigame.MiniGameTeam}.
 * It does <b>not</b> restrict plugins from defining their own custom team names.</p>
 *
 * <p>All checks are case-insensitive. Team names are stored internally
 * in lowercase form.</p>
 */
public final class TeamNames {

    public static final String TEAM_ORANGE = "orange";
    public static final String TEAM_MAGENTA = "magenta";
    public static final String TEAM_LIGHT_BLUE = "light_blue";
    public static final String TEAM_YELLOW = "yellow";
    public static final String TEAM_LIME   = "lime";
    public static final String TEAM_PINK   = "pink";
    public static final String TEAM_GRAY   = "gray";
    public static final String TEAM_LIGHT_GRAY = "light_gray";
    public static final String TEAM_CYAN   = "cyan";
    public static final String TEAM_PURPLE = "purple";
    public static final String TEAM_BLUE   = "blue";
    public static final String TEAM_BROWN  = "brown";
    public static final String TEAM_GREEN  = "green";
    public static final String TEAM_RED    = "red";
    public static final String TEAM_BLACK  = "black";
    public static final String TEAM_WHITE  = "white";   // Usually the default/no team
    public static final String TEAM_AUTO  = "auto";   // Usually the default/no team

    /**
     * Set of predefined team identifiers supported by the core API.
     */
    private static final Set<String> PREDEFINED = Set.of(
            TEAM_ORANGE,
            TEAM_MAGENTA,
            TEAM_LIGHT_BLUE,
            TEAM_YELLOW,
            TEAM_LIME,
            TEAM_PINK,
            TEAM_GRAY,
            TEAM_LIGHT_GRAY,
            TEAM_CYAN,
            TEAM_PURPLE,
            TEAM_BLUE,
            TEAM_BROWN,
            TEAM_GREEN,
            TEAM_RED,
            TEAM_BLACK,
            TEAM_WHITE,
            TEAM_AUTO
    );

    /**
     * Utility class; no instances allowed.
     */
    private TeamNames() {}

    /**
     * Checks whether the given team name matches one of the predefined
     * MiniGame team identifiers.
     *
     * @param name the team name to check, may be {@code null}
     * @return {@code true} if the name matches a predefined team identifier,
     *         {@code false} otherwise
     */
    public static boolean isPredefinedName(String name) {
        if (name == null) return false;
        return PREDEFINED.contains(name.toLowerCase());
    }

    /**
     * Normalises a team name to the canonical lowercase form used by the API.
     *
     * @param name the team name to normalise, may be {@code null}
     * @return the normalised team name, or {@code null} if the input was {@code null}
     */
    public static String normalize(String name) {
        return name == null ? null : name.toLowerCase();
    }

    /**
     * Returns an immutable view of all predefined team identifiers.
     *
     * <p>This can be used for UI, validation, or documentation purposes.</p>
     *
     * @return an unmodifiable set of predefined team identifiers
     */
    public static Set<String> predefined() {
        return PREDEFINED;
    }

    /**
     * Get the wool material associated with a predefined team name.
     *
     * @param name the team name
     * @return the corresponding wool Material, or WHITE_WOOL if the name is not recognized
     */
    public static Material getMaterial(String name) {
        return switch (name.toLowerCase()) {
            case TEAM_ORANGE -> Material.ORANGE_WOOL;
            case TEAM_MAGENTA -> Material.MAGENTA_WOOL;
            case TEAM_LIGHT_BLUE -> Material.LIGHT_BLUE_WOOL;
            case TEAM_YELLOW -> Material.YELLOW_WOOL;
            case TEAM_LIME -> Material.LIME_WOOL;
            case TEAM_PINK -> Material.PINK_WOOL;
            case TEAM_GRAY -> Material.GRAY_WOOL;
            case TEAM_LIGHT_GRAY -> Material.LIGHT_GRAY_WOOL;
            case TEAM_CYAN -> Material.CYAN_WOOL;
            case TEAM_PURPLE -> Material.PURPLE_WOOL;
            case TEAM_BLUE -> Material.BLUE_WOOL;
            case TEAM_BROWN -> Material.BROWN_WOOL;
            case TEAM_GREEN -> Material.GREEN_WOOL;
            case TEAM_RED -> Material.RED_WOOL;
            case TEAM_BLACK -> Material.BLACK_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }
}
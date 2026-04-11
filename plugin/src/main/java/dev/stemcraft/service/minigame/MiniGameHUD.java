package dev.stemcraft.service.minigame;

import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import org.bukkit.boss.BarColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MiniGameHUD {
    private final MiniGameImpl game;
    private final List<CompiledLine> bossBarLines;
    private final List<CompiledLine> scoreboardLines;
    private final int bossBarLineHoldUpdates;
    private final CompiledLine bossBarColor;

    public MiniGameHUD(MiniGameImpl game, List<String> bossBarLines, List<String> scoreboardLines) {
        this(game, bossBarLines, scoreboardLines, 1, "PURPLE");
    }

    public MiniGameHUD(MiniGameImpl game, List<String> bossBarLines, List<String> scoreboardLines, int bossBarLineHoldUpdates) {
        this(game, bossBarLines, scoreboardLines, bossBarLineHoldUpdates, "PURPLE");
    }

    public MiniGameHUD(MiniGameImpl game,
                       List<String> bossBarLines,
                       List<String> scoreboardLines,
                       int bossBarLineHoldUpdates,
                       String bossBarColor) {
        this.game = game;
        this.bossBarLines = compileAll(bossBarLines);
        this.scoreboardLines = compileAll(scoreboardLines);
        this.bossBarLineHoldUpdates = Math.max(1, bossBarLineHoldUpdates);
        this.bossBarColor = compileLine(bossBarColor == null ? "PURPLE" : bossBarColor);
    }

    public List<String> scoreboard(MiniGamePlayer player) {
        List<String> out = new ArrayList<>(scoreboardLines.size());
        for (CompiledLine cl : scoreboardLines) {
            String rendered = cl.render(game, player);
            if (rendered != null) {
                out.add(rendered);
            }
        }
        return out;
    }

    public String bossbar(MiniGamePlayer player) {
        if (bossBarLines.isEmpty()) {
            return "";
        }

        List<String> visibleLines = new ArrayList<>(bossBarLines.size());
        for (CompiledLine bossBarLine : bossBarLines) {
            String rendered = bossBarLine.render(game, player);
            if (rendered != null) {
                visibleLines.add(rendered);
            }
        }
        if (visibleLines.isEmpty()) {
            return "";
        }

        int index = 0;
        if (player instanceof MiniGamePlayerImpl impl) {
            index = impl.nextBossBarLineIndex(visibleLines.size(), bossBarLineHoldUpdates);
        }
        return visibleLines.get(index);
    }

    public BarColor bossbarColor(MiniGamePlayer player) {
        String rendered = bossBarColor.render(game, player);
        if (rendered == null || rendered.isBlank()) {
            return BarColor.PURPLE;
        }

        try {
            return BarColor.valueOf(rendered.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarColor.PURPLE;
        }
    }

    private List<CompiledLine> compileAll(List<String> lines) {
        List<CompiledLine> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(compileLine(line));
        return out;
    }

    private CompiledLine compileLine(String line) {
        Condition condition = null;
        int tokenStart = -1;
        boolean negate = false;
        if (line.startsWith("?!{")) {
            tokenStart = 2;
            negate = true;
        } else if (line.startsWith("?{")) {
            tokenStart = 1;
        }

        if (tokenStart >= 0) {
            int end = line.indexOf('}', tokenStart);
            if (end > tokenStart + 1) {
                PlaceholderToken token = PlaceholderToken.parse(line.substring(tokenStart + 1, end));
                if (token != null) {
                    condition = new Condition(token, negate);
                    int contentStart = end + 1;
                    while (contentStart < line.length() && Character.isWhitespace(line.charAt(contentStart))) {
                        contentStart++;
                    }
                    line = line.substring(contentStart);
                }
            }
        }

        List<Segment> segments = new ArrayList<>();
        int idx = 0;
        while (idx < line.length()) {
            int start = line.indexOf('{', idx);
            if (start == -1) {
                segments.add(new TextSeg(line.substring(idx)));
                break;
            }
            if (start > idx) {
                segments.add(new TextSeg(line.substring(idx, start)));
            }
            int end = line.indexOf('}', start);
            if (end == -1) {
                segments.add(new TextSeg(line.substring(start)));
                break;
            }
            String tokenStr = line.substring(start + 1, end);
            PlaceholderToken token = PlaceholderToken.parse(tokenStr);
            if (token != null) {
                segments.add(new PlaceholderSeg(token));
            } else {
                segments.add(new TextSeg(line.substring(start, end + 1)));
            }
            idx = end + 1;
        }
        boolean suppressIfBlank = segments.size() == 1 && segments.getFirst() instanceof PlaceholderSeg;
        return new CompiledLine(condition, segments, suppressIfBlank);
    }

    private static final class CompiledLine {
        private final Condition condition;
        private final List<Segment> segments;
        private final boolean suppressIfBlank;

        public CompiledLine(Condition condition, List<Segment> segments, boolean suppressIfBlank) {
            this.condition = condition;
            this.segments = segments;
            this.suppressIfBlank = suppressIfBlank;
        }

        public String render(MiniGameImpl game, MiniGamePlayer player) {
            if (condition != null && !condition.matches(game, player)) {
                return null;
            }

            StringBuilder sb = new StringBuilder(64);
            for (Segment s : segments) s.append(sb, game, player);
            String rendered = sb.toString();
            if (suppressIfBlank && rendered.isBlank()) {
                return null;
            }
            return rendered;
        }
    }

    private interface Segment {
        void append(StringBuilder sb, MiniGameImpl game, MiniGamePlayer player);
    }

    private static record TextSeg(String text) implements Segment {
        @Override
        public void append(StringBuilder sb, MiniGameImpl game, MiniGamePlayer player) {
            sb.append(text);
        }
    }

    private static record PlaceholderSeg(PlaceholderToken token) implements Segment {
        @Override
        public void append(StringBuilder sb, MiniGameImpl game, MiniGamePlayer player) {
            String value = resolveToken(game, player, token);
            if (value != null) sb.append(value);
        }
    }

    private static String resolveToken(MiniGameImpl game, MiniGamePlayer player, PlaceholderToken token) {
        if (player == null) {
            return null;
        }

        return switch (token.scope) {
            case ARENA -> {
                if (player.arena() == null) yield "";
                yield game.renderArenaPlaceholder(player.arena(), token.key);
            }
            case PLAYER -> game.renderPlayerPlaceholder(player, token.key);
            case TEAM -> {
                if (player.arena() == null) yield "";
                MiniGameTeam team = player.arena().getTeam(token.teamId);
                if (team == null) yield "";
                yield game.renderTeamPlaceholder(team, token.key);
            }
        };
    }

    private static final class Condition {
        private final PlaceholderToken token;
        private final boolean negate;

        private Condition(PlaceholderToken token, boolean negate) {
            this.token = token;
            this.negate = negate;
        }

        private boolean matches(MiniGameImpl game, MiniGamePlayer player) {
            boolean truthy = isTruthy(resolveToken(game, player, token));
            return negate ? !truthy : truthy;
        }

        private boolean isTruthy(String value) {
            if (value == null) {
                return false;
            }

            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return false;
            }

            return !normalized.equals("false")
                && !normalized.equals("0")
                && !normalized.equals("no")
                && !normalized.equals("off")
                && !normalized.equals("null");
        }
    }

    static final class PlaceholderToken {
        enum Scope { ARENA, TEAM, PLAYER }

        final Scope scope;
        final String teamId; // only for TEAM
        final String key;

        private PlaceholderToken(Scope scope, String teamId, String key) {
            this.scope = scope;
            this.teamId = teamId;
            this.key = key;
        }

        static PlaceholderToken parse(String raw) {
            if (raw == null) return null;
            String s = raw.trim();
            if (s.isEmpty()) return null;

            String[] parts = s.split(":");
            if (parts.length < 2) return null;

            String scopeStr = parts[0].trim().toLowerCase(Locale.ROOT);
            return switch (scopeStr) {
                case "arena" -> {
                    if (parts.length != 2) yield null;
                    String key = parts[1].trim();
                    if (key.isEmpty()) yield null;
                    yield new PlaceholderToken(Scope.ARENA, null, key);
                }
                case "player" -> {
                    if (parts.length != 2) yield null;
                    String key = parts[1].trim();
                    if (key.isEmpty()) yield null;
                    yield new PlaceholderToken(Scope.PLAYER, null, key);
                }
                case "team" -> {
                    if (parts.length != 3) yield null;
                    String teamId = parts[1].trim();
                    String key = parts[2].trim();
                    if (teamId.isEmpty() || key.isEmpty()) yield null;
                    yield new PlaceholderToken(Scope.TEAM, teamId, key);
                }
                default -> null;
            };
        }
    }
}

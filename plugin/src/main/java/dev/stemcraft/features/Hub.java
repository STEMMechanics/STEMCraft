package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.utils.SCPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Hub implements STEMCraftFeature {
    STEMCraftAPI api;
    private World hubWorld;
    private final List<ExitCommandRule> exitCommandRules = new ArrayList<>();

    @Override
    public void onEnable(STEMCraftAPI api) {
        this.api = api;
        loadHubConfig();

        api.worlds().setDefaultWorld(hubWorld);

        api.registerEvent(PlayerJoinEvent.class, event -> {
            if(hubWorld != null) {
                Player player = event.getPlayer();

                if(!SCPlayer.isWhitelisted(player)) {
                    return;
                }

                api.tasks().runLater(10L, () -> {
                    player.teleport(hubWorld.getSpawnLocation());
                });
            }
        });

        api.registerCommand("hub")
            .setDescription("HUB_DESCRIPTION")
            .setUsage("HUB_USAGE")
            .setPermission("stemcraft.command.hub")
            .setExecutor((unused, cmd, ctx) -> {
                // check if console called without args
                if(ctx.isConsole() && ctx.args().isEmpty()) {
                    cmd.error("CONSOLE_PLAYER_REQUIRED");
                    return;
                }

                // check permission for others (if args given)
                if(!ctx.args().isEmpty() && !ctx.hasPermission("stemcraft.command.hub.others")) {
                    cmd.error(ctx.getSender(), "HUB_TELEPORT_OTHER_DENY");
                    return;
                }

                // get target player
                Player target = ctx.getArgAsPlayer(1, ctx.getSender());
                if(target == null) {
                    cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.getArg(1));
                    return;
                }

                // get hub world from config, else first world
                if (hubWorld == null) {
                    cmd.error(ctx.getSender(), "HUB_WORLD_NOT_FOUND");
                    return;
                }

                if (tryRunExitCommand(target)) {
                    return;
                }

                Location hubLocation = hubWorld.getSpawnLocation();
                SCPlayer.teleport(target, hubLocation);

                if (target.equals(ctx.getSender())) {
                    cmd.success(ctx.getSender(), "HUB_TELEPORT_SUCCESS");
                } else {
                    String senderName = ctx.isConsole() ? api.locale().get("CONSOLE_NAME") : ctx.getSender().getName();
                    cmd.success(ctx.getSender(), "HUB_TELEPORT_OTHER_SUCCESS_SENDER", "player", target.getName());
                    cmd.success(target, "HUB_TELEPORT_OTHER_SUCCESS_PLAYER", "player", senderName);
                }

            })
            .register(STEMCraft.getInstance());
    }

    private void loadHubConfig() {
        String hubWorldName = api.config().getString("hub.world", "world");

        if (!hubWorldName.isEmpty()) {
            hubWorld = Bukkit.getWorld(hubWorldName);
        }

        if (hubWorld == null) {
            hubWorld = Bukkit.getWorlds().getFirst();
        }

        exitCommandRules.clear();
        ConfigurationSection sec = api.config().getConfigurationSection("hub.exit_commands");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection ruleSec = sec.getConfigurationSection(key);
            if (ruleSec == null) continue;

            String cmd = ruleSec.getString("command", "");
            if (cmd == null || cmd.isBlank()) continue;

            boolean asPlayer = ruleSec.getBoolean("as-player", false);

            // glob -> regex (case-sensitive like Bukkit world names)
            Pattern p = globToRegex(key);

            exitCommandRules.add(new ExitCommandRule(key, p, cmd, asPlayer));
        }
    }

    private static Pattern globToRegex(String glob) {
        // Convert '*' wildcard to '.*' and escape everything else.
        StringBuilder out = new StringBuilder();
        out.append('^');
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                out.append(".*");
            } else {
                out.append(Pattern.quote(String.valueOf(c)));
            }
        }
        out.append('$');
        return Pattern.compile(out.toString());
    }

    private boolean tryRunExitCommand(Player player) {
        String currentWorld = player.getWorld().getName();

        for (ExitCommandRule rule : exitCommandRules) {
            if (!rule.worldPattern.matcher(currentWorld).matches()) continue;

            String cmd = rule.command;
            cmd = cmd.replace("{hub-world}", hubWorld.getName());
            cmd = cmd.replace("{world}", currentWorld);
            cmd = cmd.replace("{player}", player.getName());

            if (rule.asPlayer) {
                Bukkit.dispatchCommand(player, cmd);
            } else {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                Bukkit.dispatchCommand(console, cmd);
            }

            // matched rule: do not teleport
            return true;
        }

        return false;
    }

    private static final class ExitCommandRule {
        final String rawPattern;
        final Pattern worldPattern;
        final String command;
        final boolean asPlayer;

        ExitCommandRule(String rawPattern, Pattern worldPattern, String command, boolean asPlayer) {
            this.rawPattern = rawPattern;
            this.worldPattern = worldPattern;
            this.command = command;
            this.asPlayer = asPlayer;
        }
    }
}

package dev.stemcraft.services;

import dev.stemcraft.api.services.SCLogService;
import dev.stemcraft.api.utils.SCText;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class SCLogServiceImpl implements SCLogService {

    private final JavaPlugin plugin;

    private final Component prefixLog;
    private final Component prefixInfo;
    private final Component prefixWarn;
    private final Component prefixError;
    private final Component prefixSuccess;

    public SCLogServiceImpl(JavaPlugin plugin) {
        this.plugin = plugin;

        var cfg = plugin.getConfig();

        this.prefixLog = SCText.colourise(cfg.getString("logging.prefixes.log", "&7[STEM]&r "));
        this.prefixInfo = SCText.colourise(cfg.getString("logging.prefixes.info", "&9[INFO]&r "));
        this.prefixWarn = SCText.colourise(cfg.getString("logging.prefixes.warn", "&e[WARN]&r "));
        this.prefixError = SCText.colourise(cfg.getString("logging.prefixes.error", "&c[ERROR]&r "));
        this.prefixSuccess = SCText.colourise(cfg.getString("logging.prefixes.success", "&a[SUCCESS]&r "));
    }

    // console only

    @Override
    public void log(String message) {
        plugin.getComponentLogger().info(message);
    }

    @Override
    public void info(String message) {
        plugin.getComponentLogger().info(message);
    }

    @Override
    public void warn(String message) {
        plugin.getComponentLogger().warn(message);
    }

    @Override
    public void error(String message) {
        plugin.getComponentLogger().error(message);
    }

    @Override
    public void success(String message) {
        plugin.getComponentLogger().info(message);
    }

    // send to CommandSender

    @Override
    public void log(CommandSender sender, String message) {
        sender.sendMessage(prefixLog.append(SCText.colourise(message)));
    }

    @Override
    public void info(CommandSender sender, String message) {
        sender.sendMessage(prefixInfo.append(SCText.colourise(message)));
    }

    @Override
    public void warn(CommandSender sender, String message) {
        sender.sendMessage(prefixWarn.append(SCText.colourise(message)));
    }

    @Override
    public void error(CommandSender sender, String message) {
        sender.sendMessage(prefixError.append(SCText.colourise(message)));
    }

    @Override
    public void success(CommandSender sender, String message) {
        sender.sendMessage(prefixSuccess.append(SCText.colourise(message)));
    }
}
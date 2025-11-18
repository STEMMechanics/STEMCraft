package dev.stemcraft.services;

import dev.stemcraft.api.services.LogService;
import dev.stemcraft.api.utils.SCText;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class LogServiceImpl implements LogService {

    private final JavaPlugin plugin;

    private final Component prefixLog;
    private final Component prefixInfo;
    private final Component prefixWarn;
    private final Component prefixError;
    private final Component prefixSuccess;

    public LogServiceImpl(JavaPlugin plugin) {
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
        plugin.getComponentLogger().info(SCText.colourise(message));
    }

    @Override
    public void log(Component message) {
        plugin.getComponentLogger().info(message);
    }

    @Override
    public void log(String message, Throwable ex) { plugin.getComponentLogger().error(SCText.colourise(message), ex); }

    @Override
    public void info(String message) {
        plugin.getComponentLogger().info(SCText.colourise(message));
    }

    @Override
    public void info(Component message) {
        plugin.getComponentLogger().info(message);
    }

    @Override
    public void warn(String message) {
        plugin.getComponentLogger().warn(SCText.colourise(message));
    }

    @Override
    public void warn(Component message) {
        plugin.getComponentLogger().warn(message);
    }

    @Override
    public void warn(String message, Throwable ex) { plugin.getComponentLogger().error(SCText.colourise(message), ex); }

    @Override
    public void error(String message) {
        plugin.getComponentLogger().error(SCText.colourise(message));
    }

    @Override
    public void error(Component message) {
        plugin.getComponentLogger().error(message);
    }

    @Override
    public void error(String message, Throwable ex) { plugin.getComponentLogger().error(SCText.colourise(message), ex); }

    @Override
    public void success(String message) {
        plugin.getComponentLogger().info(SCText.colourise(message));
    }

    @Override
    public void success(Component message) {
        plugin.getComponentLogger().info(message);
    }

    // send to CommandSender

    @Override
    public void log(CommandSender sender, String message) {
        sender.sendMessage(prefixLog.append(SCText.colourise(message)));
    }

    @Override
    public void log(CommandSender sender, Component message) {
        sender.sendMessage(prefixLog.append(message));
    }

    @Override
    public void info(CommandSender sender, String message) {
        sender.sendMessage(prefixInfo.append(SCText.colourise(message)));
    }

    @Override
    public void info(CommandSender sender, Component message) {
        sender.sendMessage(prefixInfo.append(message));
    }

    @Override
    public void warn(CommandSender sender, String message) {
        sender.sendMessage(prefixWarn.append(SCText.colourise(message)));
    }

    @Override
    public void warn(CommandSender sender, Component message) {
        sender.sendMessage(prefixWarn.append(message));
    }

    @Override
    public void error(CommandSender sender, String message) {
        sender.sendMessage(prefixError.append(SCText.colourise(message)));
    }

    @Override
    public void error(CommandSender sender, Component message) {
        sender.sendMessage(prefixError.append(message));
    }

    @Override
    public void success(CommandSender sender, String message) {
        sender.sendMessage(prefixSuccess.append(SCText.colourise(message)));
    }

    @Override
    public void success(CommandSender sender, Component message) {
        sender.sendMessage(prefixSuccess.append(message));
    }
}
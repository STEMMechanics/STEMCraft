package dev.stemcraft.api.utils;

import dev.stemcraft.api.services.LogService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class STEMCraftUtil {
    protected static JavaPlugin plugin;
    protected static LogService logger = null;

    public static void onLoad(JavaPlugin p) {
        plugin = p;
    }

    public static LogService getLogger() {
        if(logger != null) {
            return logger;
        }

        RegisteredServiceProvider<LogService> rsp = Bukkit.getServicesManager().getRegistration(LogService.class);
        if (rsp == null) {
            throw new IllegalStateException("LogService not found");
        }

        logger = rsp.getProvider();
        return logger;
    }
}

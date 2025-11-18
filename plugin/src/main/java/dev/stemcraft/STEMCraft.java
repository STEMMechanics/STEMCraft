package dev.stemcraft;

import dev.stemcraft.api.services.SCLogService;
import dev.stemcraft.services.SCLogServiceImpl;
import org.bukkit.plugin.java.JavaPlugin;

public final class STEMCraft extends JavaPlugin {
    private SCLogService logService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.logService = new SCLogServiceImpl(this);

        getServer().getServicesManager().register(SCLogService.class, logService, this, org.bukkit.plugin.ServicePriority.Normal);
        logService.info("STEMCraft enabled");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }
}

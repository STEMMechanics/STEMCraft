package dev.stemcraft.services;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.MOTDService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class MOTDServiceImpl implements MOTDService, Listener {
    private final STEMCraft plugin;
    private final Map<String, String> motds = new LinkedHashMap<>();
    private String motdTitle = "§6§lSTEMCraft";
    private String motdText = "";

    public MOTDServiceImpl(STEMCraft plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        motdTitle = plugin.getConfig().getString("motd.title", "§6§lSTEMCraft");
        motdText = plugin.getConfig().getString("motd.text", "");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void onDisable() { }

    public void setMOTD(String key, String motd) {
        if (motd == null) {
            clearMOTD(key);
            return;
        }

        // remove first so re-put moves it to the end (last = most recent)
        motds.remove(key);
        motds.put(key, motd);
    }

    public void clearMOTD(String key) {
        motds.remove(key);
    }

    public String getMOTD(String key) {
        if(key == null) {
            return motdText;
        }

        return motds.getOrDefault(key, null);
    }

    public String getActiveMOTD() {
        if (motds.isEmpty()) {
            return motdText;
        }

        String last = null;
        for (String value : motds.values()) {
            last = value; // iteration order = insertion order, last = most recent
        }
        return last;
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        event.setMotd(motdTitle + "\n" + getActiveMOTD());
    }
}

package dev.stemcraft.api.services;

public interface MOTDService extends STEMCraftService {
    public String getMOTD(String key);
    public String getActiveMOTD();
    public void setMOTD(String key, String motd);
    public void clearMOTD(String key);
}

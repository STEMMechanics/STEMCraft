package dev.stemcraft.api.services.web;

import dev.stemcraft.api.services.STEMCraftService;

public interface WebService extends STEMCraftService {
    public void start();
    public void stop();
    public void registerEndpointHandler(String path, WebServiceEndpointHandler handler);
}

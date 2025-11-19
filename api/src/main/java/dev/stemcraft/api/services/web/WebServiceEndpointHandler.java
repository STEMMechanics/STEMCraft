package dev.stemcraft.api.services.web;

import java.io.IOException;

public interface WebServiceEndpointHandler {
    Object handle(String method, String uri) throws IOException;
}
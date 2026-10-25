package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandBuilder;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.command.CommandExecutor;
import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebServiceTransportTest {
    @TempDir Path dataFolder;
    private ServerMock server;
    private STEMCraft plugin;
    private STEMCraftAPI api;
    private WebServiceImpl service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(STEMCraft.class);
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        ConfigSection settings = mock(ConfigSection.class);
        when(settings.getString("path", "www")).thenReturn("www");
        when(settings.getString("ip", "127.0.0.1")).thenReturn("127.0.0.1");
        when(settings.getString("public-url", "")).thenReturn("");
        when(settings.getInt("port", 8950)).thenReturn(0);
        service = new WebServiceImpl(plugin, api) {
            @Override protected ConfigSection getConfigSection() { return settings; }
        };
    }

    @AfterEach
    void tearDown() {
        try {
            service.stop();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void startAndStopCommandsConfirmTheOutcomeToThePlayer() {
        CommandBuilder builder = mock(CommandBuilder.class, RETURNS_SELF);
        when(api.commands().create("webserver")).thenReturn(builder);
        service.onEnable();
        ArgumentCaptor<CommandExecutor> callback = ArgumentCaptor.captor();
        verify(builder).executor(callback.capture());
        Player player = server.addPlayer("WebAdmin");
        CommandContext context = mock(CommandContext.class);
        when(context.getSender()).thenReturn(player);
        when(context.args()).thenReturn(List.of("start"));

        callback.getValue().execute(api, mock(Command.class), context);
        assertTrue(service.isRunning());
        verify(api.messages()).success(eq(player), eq("WEB_SERVER_STARTED"), eq("url"), anyString());

        when(context.args()).thenReturn(List.of("stop"));
        callback.getValue().execute(api, mock(Command.class), context);
        assertFalse(service.isRunning());
        verify(api.messages()).success(player, "WEB_SERVER_STOPPED");
    }

    @Test
    void encodedPercentInAValidFileNameDoesNotDropTheConnection() throws Exception {
        Path root = Files.createDirectories(dataFolder.resolve("www"));
        Files.writeString(root.resolve("percent%.txt"), "percent file");
        service.start();

        HttpResponse<String> response = get("/percent%25.txt");

        assertEquals(200, response.statusCode());
        assertEquals("percent file", response.body());
    }

    @Test
    void rootServesTheBundledLandingPageWhenNoCustomIndexExists() throws Exception {
        when(plugin.getResource("www/index.html"))
            .thenAnswer(invocation -> getClass().getResourceAsStream("/www/index.html"));
        service.start();

        HttpResponse<String> response = get("/");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("play.stemcraft.com.au"));
        assertTrue(response.body().contains("Connect to the server"));
    }

    @Test
    void rootServesACustomIndexInsteadOfRejectingTheDirectory() throws Exception {
        Path root = Files.createDirectories(dataFolder.resolve("www"));
        Files.writeString(root.resolve("index.html"), "<html>custom landing page</html>");
        service.start();

        HttpResponse<String> response = get("/");

        assertEquals(200, response.statusCode());
        assertEquals("<html>custom landing page</html>", response.body());
    }

    @Test
    void robotsTxtDisallowsCrawlersByDefault() throws Exception {
        when(plugin.getResource("www/robots.txt"))
            .thenAnswer(invocation -> getClass().getResourceAsStream("/www/robots.txt"));
        service.start();

        HttpResponse<String> response = get("/robots.txt");

        assertEquals(200, response.statusCode());
        assertEquals("User-agent: *\nDisallow: /\n", response.body());
    }

    @Test
    void throwingEndpointReturnsAnHttpErrorInsteadOfDroppingTheConnection() throws Exception {
        service.registerEndpointHandler("/broken", request -> {
            throw new IllegalStateException("endpoint failed");
        });
        service.start();

        HttpResponse<String> response = get("/broken");

        assertEquals(500, response.statusCode());
        assertEquals("Internal server error", response.body());
        verify(api.messages()).error(eq("WEB_SERVER_REQUEST_FAILED"), isA(IllegalStateException.class),
            eq("method"), eq("GET"), eq("path"), eq("/broken"), eq("ip"), anyString());
        assertEquals(200, get("/status").statusCode());
    }

    @Test
    void literalPlusInAFileNameIsPreserved() throws Exception {
        Path root = Files.createDirectories(dataFolder.resolve("www"));
        Files.writeString(root.resolve("plus+name.txt"), "plus file");
        service.start();

        HttpResponse<String> response = get("/plus+name.txt");

        assertEquals(200, response.statusCode());
        assertEquals("plus file", response.body());
    }

    @Test
    void encodedQueryValuesAreDecodedOnceWithoutSplittingTheirContents() throws Exception {
        service.registerEndpointHandler("/echo", request -> request.queryParams().get("value"));
        service.start();

        HttpResponse<String> response = get("/echo?value=%2B%26%25");

        assertEquals(200, response.statusCode());
        assertEquals("+&%", response.body());
    }

    @Test
    void encodedTraversalCannotReadFilesOutsideTheWebRoot() throws Exception {
        Files.writeString(dataFolder.resolve("private.txt"), "private data");
        service.start();

        HttpResponse<String> response = get("/%2e%2e/private.txt");

        assertEquals(403, response.statusCode());
        assertEquals("Forbidden", response.body());
    }

    @Test
    void headRequestHasTheFileLengthWithoutWritingABody() throws Exception {
        Path root = Files.createDirectories(dataFolder.resolve("www"));
        Files.writeString(root.resolve("download.txt"), "download body");
        service.start();

        HttpResponse<String> response = request("/download.txt", "HEAD");

        assertEquals(200, response.statusCode());
        assertEquals("13", response.headers().firstValue("Content-Length").orElseThrow());
        assertEquals("", response.body());
    }

    @Test
    void endpointCanStreamADownloadOutsideTheStaticWebRoot() throws Exception {
        Path download = dataFolder.resolve("download.bin");
        Files.writeString(download, "download body");
        service.registerEndpointHandler("/download", request -> Map.of(
            "file", download.toFile(), "contentType", "application/octet-stream"));
        service.start();

        HttpResponse<String> response = get("/download");

        assertEquals(200, response.statusCode());
        assertEquals("application/octet-stream", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("download body", response.body());
    }

    @Test
    void rejectedEndpointPathFallsThroughToNotFound() throws Exception {
        service.start();

        assertEquals(404, get("/status-other").statusCode());
    }

    @Test
    void stoppingTheServiceOnDisableClosesItsListener() {
        service.start();

        service.onDisable();

        assertFalse(service.isRunning());
    }

    @Test
    void failedStartReportsFailureToThePlayer() throws Exception {
        Files.writeString(dataFolder.resolve("www"), "not a directory");
        CommandBuilder builder = mock(CommandBuilder.class, RETURNS_SELF);
        when(api.commands().create("webserver")).thenReturn(builder);
        service.onEnable();
        ArgumentCaptor<CommandExecutor> callback = ArgumentCaptor.captor();
        verify(builder).executor(callback.capture());
        CommandContext context = mock(CommandContext.class);
        Player player = server.addPlayer("WebAdmin");
        when(context.getSender()).thenReturn(player);
        when(context.args()).thenReturn(List.of("start"));

        callback.getValue().execute(api, mock(Command.class), context);

        assertFalse(service.isRunning());
        verify(api.messages()).error(player, "WEB_SERVER_START_FAILED_PLAYER");
    }

    @Test
    void commandWithoutArgumentsReportsTheBoundAddressAndDiagnosticUrl() {
        CommandBuilder builder = mock(CommandBuilder.class, RETURNS_SELF);
        when(api.commands().create("webserver")).thenReturn(builder);
        service.onEnable();
        service.start();
        ArgumentCaptor<CommandExecutor> callback = ArgumentCaptor.captor();
        verify(builder).executor(callback.capture());
        Player player = server.addPlayer("WebAdmin");
        CommandContext context = mock(CommandContext.class);
        when(context.getSender()).thenReturn(player);
        when(context.args()).thenReturn(List.of());
        String publicUrl = service.getPublicUrl();

        callback.getValue().execute(api, mock(Command.class), context);

        verify(api.messages()).info(player, "WEB_SERVER_STATUS",
            "enabled_disabled", "{WEB_SERVER_STATE_DISABLED}",
            "running_not", "{WEB_SERVER_STATE_RUNNING}",
            "ip", "127.0.0.1", "port", URI.create(publicUrl).getPort(), "url", publicUrl);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return request(path, "GET");
    }

    private HttpResponse<String> request(String path, String method) throws Exception {
        URI uri = URI.create(service.getPublicUrl() + path);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        }
    }
}

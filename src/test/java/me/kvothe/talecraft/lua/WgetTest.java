package me.kvothe.talecraft.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.kvothe.talecraft.net.HttpService;
import me.kvothe.talecraft.os.VirtualFileSystem;

class WgetTest {

    private static final Path ROM_DIR = Path.of("src/main/resources/lua/rom");

    @TempDir
    Path dir;

    private HttpServer server;
    private HttpService http;
    private LuaMachine machine;
    private Path computerRoot;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok.lua", exchange -> respond(exchange, 200, "print('NO_DEBE_EJECUTARSE')"));
        server.createContext("/missing.lua", exchange -> respond(exchange, 404, "missing"));
        server.createContext("/big.lua", exchange -> respond(exchange, 200, "x".repeat(2048)));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        Path config = dir.resolve("config.json");
        Files.writeString(config, """
                {"http":{"enabled":true,"max_requests":2,"timeout_seconds":5,
                "max_redirects":2,"bandwidth":{"global_download":0},"rules":[
                {"host":"127.0.0.1","action":"allow","max_download":1024}]}}
                """);
        http = new HttpService(config);

        computerRoot = dir.resolve("computer");
        VirtualFileSystem fs = new VirtualFileSystem(computerRoot, 1_000_000);
        Map<String, String> rom = new HashMap<>();
        try (var walk = Files.walk(ROM_DIR)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                rom.put("rom/" + ROM_DIR.relativize(path).toString().replace('\\', '/'),
                        Files.readString(path));
            }
        }
        fs.mountRom(rom);
        machine = new LuaMachine(200_000, fs);
        machine.setHttpBridge(http);
        machine.load(rom.get("rom/bios.lua"), "taleos:bios");
        machine.tick();
        machine.drainOutput();
    }

    @AfterEach
    void tearDown() {
        if (machine != null) {
            machine.cancelPendingHttp();
        }
        if (http != null) {
            http.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void wgetDownloadsWithoutExecutingAndRejectsHttpErrorsAndOversize() throws Exception {
        machine.queueEvent("stdin", "wget " + baseUrl + "/ok.lua hola.lua");
        String output = awaitOutput("Saved hola.lua");
        assertEquals("print('NO_DEBE_EJECUTARSE')", Files.readString(computerRoot.resolve("hola.lua")));
        assertFalse(output.contains("NO_DEBE_EJECUTARSE\n"), "downloaded code must not run automatically");

        machine.queueEvent("stdin", "wget " + baseUrl + "/missing.lua missing.lua");
        awaitOutput("HTTP 404");
        assertFalse(Files.exists(computerRoot.resolve("missing.lua")));

        machine.queueEvent("stdin", "wget " + baseUrl + "/big.lua big.lua");
        awaitOutput("max_download");
        assertFalse(Files.exists(computerRoot.resolve("big.lua")));
    }

    private String awaitOutput(String expected) throws Exception {
        StringBuilder output = new StringBuilder();
        for (int attempt = 0; attempt < 500; attempt++) {
            machine.tick();
            output.append(machine.drainOutput());
            if (output.toString().contains(expected)) {
                return output.toString();
            }
            Thread.sleep(5);
        }
        fail("Expected output containing '" + expected + "', got: " + output);
        return "";
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

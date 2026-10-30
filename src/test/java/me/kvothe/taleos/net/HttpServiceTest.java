package me.kvothe.taleos.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServiceTest {
    @TempDir
    Path tempDir;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private HttpService service;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/ok", exchange -> respond(exchange, 200, "hola 🌍", false));
        server.createContext("/missing", exchange -> respond(exchange, 404, "no existe", false));
        server.createContext("/large", exchange -> respond(exchange, 200, "x".repeat(128), false));
        server.createContext("/chunked", exchange -> respond(exchange, 200, "x".repeat(128), true));
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/private-redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl + "/ok");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (service != null) {
            service.close();
        }
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void generatesDefaultsWhenConfigIsMissing() {
        Path config = tempDir.resolve("nested/config.json");
        service = new HttpService(config);

        assertTrue(Files.exists(config));
        assertTrue(service.isEnabled());
        assertTrue(read(config).contains("\"max_requests\": 16"));
    }

    @Test
    void invalidGlobalConfigFailsClosed() {
        Path config = writeConfig("not json");
        service = new HttpService(config);

        assertFalse(service.isEnabled());
        Throwable error = failure(service.get(new Object(), baseUrl + "/ok"));
        assertInstanceOf(IllegalStateException.class, error);
        assertTrue(error.getMessage().contains("disabled"));
    }

    @Test
    void outOfRangeTimeoutFailsClosed() {
        service = new HttpService(writeConfig(config(16, 61, 16, 1024, "127.0.0.1")));

        assertFalse(service.isEnabled());
    }

    @Test
    void invalidRuleDeniesEverything() {
        Path config = tempDir.resolve("config.json");
        write(config, """
            {"http":{"enabled":true,"max_requests":16,"timeout_seconds":30,"max_redirects":16,
            "bandwidth":{"global_download":0},"rules":[{"host":"*","action":"invalid"}]}}
            """);
        service = new HttpService(config);

        assertTrue(service.isEnabled());
        assertInstanceOf(SecurityException.class, failure(service.get(new Object(), baseUrl + "/ok")));
    }

    @Test
    void privateAddressesAreDeniedByDefault() {
        service = new HttpService(tempDir.resolve("config.json"));

        Throwable error = failure(service.get(new Object(), baseUrl + "/ok"));
        assertInstanceOf(SecurityException.class, error);
        assertTrue(error.getMessage().contains("denied"));
    }

    @Test
    void getsUtf8TextAndHttpErrors() {
        service = localService(16, 16, 1024);

        HttpResult result = await(service.get(new Object(), baseUrl + "/ok"));
        assertEquals(200, result.statusCode());
        assertEquals("OK", result.reason());
        assertEquals("hola 🌍", result.body());

        HttpResult missing = await(service.get(new Object(), baseUrl + "/missing"));
        assertEquals(404, missing.statusCode());
        assertEquals("Not Found", missing.reason());
        assertEquals("no existe", missing.body());
    }

    @Test
    void rejectsOversizeBodiesIncludingChunkedResponses() {
        service = localService(16, 16, 4);

        Throwable fixed = failure(service.get(new Object(), baseUrl + "/large"));
        assertTrue(fixed.getMessage().contains("max_download"));
        Throwable chunked = failure(service.get(new Object(), baseUrl + "/chunked"));
        assertTrue(chunked.getMessage().contains("max_download"));
    }

    @Test
    void followsAndRevalidatesRedirects() {
        service = localService(16, 16, 1024);
        assertEquals("hola 🌍", await(service.get(new Object(), baseUrl + "/redirect")).body());
        service.close();

        service = new HttpService(writeConfig(config(16, 5, 0, 1024, "127.0.0.1")));
        assertTrue(failure(service.get(new Object(), baseUrl + "/redirect")).getMessage().contains("redirect limit"));
        service.close();

        Path config = tempDir.resolve("redirect-private.json");
        write(config, configWithRules(16, 1, 16, 1024, """
            {"host":"localhost","action":"allow","max_download":1024},
            {"host":"$private","action":"deny"},
            {"host":"*","action":"allow","max_download":1024}
            """));
        service = new HttpService(config);
        String localhost = "http://localhost:" + server.getAddress().getPort() + "/private-redirect";
        assertInstanceOf(SecurityException.class, failure(service.get(new Object(), localhost)));
    }

    @Test
    void queuesRequestsAboveOwnerLimit() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/slow", exchange -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
                respond(exchange, 200, "done", false);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        service = localService(1, 16, 1024);
        Object owner = new Object();

        CompletableFuture<HttpResult> first = service.get(owner, baseUrl + "/slow");
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        CompletableFuture<HttpResult> second = service.get(owner, baseUrl + "/slow");
        Thread.sleep(150);
        assertFalse(second.isDone());
        assertEquals(1, calls.get());

        release.countDown();
        assertEquals("done", await(first).body());
        assertEquals("done", await(second).body());
        assertEquals(2, calls.get());
    }

    @Test
    void cancelReleasesActiveAndQueuedOwnerRequests() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/hold", exchange -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
                respond(exchange, 200, "late", false);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        service = localService(1, 16, 1024);
        Object owner = new Object();
        CompletableFuture<HttpResult> active = service.get(owner, baseUrl + "/hold");
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        CompletableFuture<HttpResult> queued = service.get(owner, baseUrl + "/ok");

        service.cancel(owner);

        assertThrows(java.util.concurrent.CancellationException.class, active::join);
        assertThrows(java.util.concurrent.CancellationException.class, queued::join);
        assertTrue(active.isCompletedExceptionally());
        assertTrue(queued.isCompletedExceptionally());
        release.countDown();
    }

    @Test
    void rejectsUnsupportedSchemesWithoutNetworkAccess() {
        service = localService(16, 16, 1024);

        Throwable error = failure(service.get(new Object(), "file:///etc/passwd"));
        assertInstanceOf(IllegalArgumentException.class, error);
        assertTrue(error.getMessage().contains("http"));
    }

    private HttpService localService(int maxRequests, int redirects, long maxDownload) {
        return new HttpService(writeConfig(config(maxRequests, 5, redirects, maxDownload, "127.0.0.1")));
    }

    private Path writeConfig(String content) {
        Path path = tempDir.resolve("config-" + System.nanoTime() + ".json");
        write(path, content);
        return path;
    }

    private static String config(int maxRequests, int timeout, int redirects, long maxDownload, String localHost) {
        return configWithRules(maxRequests, timeout, redirects, maxDownload, """
            {"host":"%s","action":"allow","max_download":%d},
            {"host":"$private","action":"deny"},
            {"host":"*","action":"allow","max_download":%d}
            """.formatted(localHost, maxDownload, maxDownload));
    }

    private static String configWithRules(int maxRequests, int timeout, int redirects, long maxDownload, String rules) {
        return """
            {"http":{"enabled":true,"max_requests":%d,"timeout_seconds":%d,"max_redirects":%d,
            "bandwidth":{"global_download":0},"rules":[%s]}}
            """.formatted(maxRequests, timeout, redirects, rules);
    }

    private static HttpResult await(CompletableFuture<HttpResult> future) {
        return future.orTimeout(5, TimeUnit.SECONDS).join();
    }

    private static Throwable failure(CompletableFuture<HttpResult> future) {
        CompletionException error = assertThrows(CompletionException.class,
                () -> future.orTimeout(5, TimeUnit.SECONDS).join());
        return error.getCause();
    }

    private static void respond(HttpExchange exchange, int status, String body, boolean chunked) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, chunked ? 0 : bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void write(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}

package me.kvothe.taleos.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class HttpService implements HttpBridge, AutoCloseable {
    private final HttpConfig.Values config;
    private final HttpClient client;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;
    private final DownloadLimiter limiter;
    private final Object lock = new Object();
    private final Map<Object, OwnerState> owners = new IdentityHashMap<>();
    private volatile boolean closed;

    public HttpService(Path configFile) {
        this(HttpConfig.load(configFile));
    }

    HttpService(HttpConfig.Values config) {
        this.config = config;
        ThreadFactory threads = new DaemonThreadFactory();
        this.workers = Executors.newCachedThreadPool(threads);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(threads);
        this.limiter = new DownloadLimiter(config.globalDownload());
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(workers);
        if (config.timeoutSeconds() > 0) {
            builder.connectTimeout(Duration.ofSeconds(config.timeoutSeconds()));
        }
        this.client = builder.build();
    }

    @Override
    public boolean isEnabled() {
        return config.enabled() && !closed;
    }

    @Override
    public CompletableFuture<HttpResult> get(Object owner, String url) {
        if (owner == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("HTTP owner cannot be null"));
        }
        if (url == null || url.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("HTTP URL cannot be empty"));
        }
        synchronized (lock) {
            if (!config.enabled()) {
                return CompletableFuture.failedFuture(new IllegalStateException("HTTP support is disabled by configuration"));
            }
            if (closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("HTTP service is closed"));
            }
            OwnerState state = owners.get(owner);
            if (state == null) {
                state = new OwnerState(owner);
                owners.put(owner, state);
            }
            Operation operation = new Operation(state, url);
            state.queued.add(operation);
            operation.result.whenComplete((ignored, error) -> {
                if (operation.result.isCancelled()) {
                    operation.cancel("HTTP request cancelled");
                }
            });
            pump(state);
            return operation.result;
        }
    }

    @Override
    public void cancel(Object owner) {
        if (owner == null) {
            return;
        }
        List<Operation> operations;
        synchronized (lock) {
            OwnerState state = owners.get(owner);
            if (state == null) {
                return;
            }
            operations = new ArrayList<>(state.queued);
            operations.addAll(state.active);
        }
        for (Operation operation : operations) {
            operation.cancel("HTTP requests cancelled for owner");
        }
    }

    @Override
    public void close() {
        List<Operation> operations = new ArrayList<>();
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            for (OwnerState state : owners.values()) {
                operations.addAll(state.queued);
                operations.addAll(state.active);
            }
        }
        for (Operation operation : operations) {
            operation.cancel("HTTP service closed");
        }
        scheduler.shutdownNow();
        workers.shutdownNow();
        client.close();
    }

    private void pump(OwnerState state) {
        synchronized (lock) {
            while (!closed && (config.maxRequests() == 0 || state.active.size() < config.maxRequests())) {
                Operation next = state.queued.pollFirst();
                if (next == null) {
                    if (state.active.isEmpty()) {
                        owners.remove(state.owner, state);
                    }
                    return;
                }
                if (next.done) {
                    continue;
                }
                state.active.add(next);
                next.active = true;
                try {
                    workers.execute(next::run);
                } catch (RejectedExecutionException exception) {
                    next.fail(new IllegalStateException("HTTP service is shutting down", exception));
                }
            }
        }
    }

    private final class Operation {
        private final OwnerState owner;
        private final String initialUrl;
        private final CompletableFuture<HttpResult> result = new CompletableFuture<>();
        private boolean active;
        private boolean done;
        private Thread worker;
        private InputStream body;
        private ScheduledFuture<?> timeout;

        private Operation(OwnerState owner, String initialUrl) {
            this.owner = owner;
            this.initialUrl = initialUrl;
        }

        private void run() {
            synchronized (lock) {
                if (done || closed) {
                    finish();
                    return;
                }
                worker = Thread.currentThread();
                if (config.timeoutSeconds() > 0) {
                    timeout = scheduler.schedule(
                            () -> fail(new TimeoutException("HTTP request timed out after " + config.timeoutSeconds() + " seconds")),
                            config.timeoutSeconds(), TimeUnit.SECONDS);
                }
            }
            try {
                complete(execute());
            } catch (Throwable error) {
                if (error instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    fail(new CancellationException("HTTP request cancelled"));
                } else if (error instanceof IOException) {
                    fail(new IOException("HTTP GET failed for " + initialUrl + ": " + usefulMessage(error), error));
                } else if (error instanceof IllegalArgumentException || error instanceof SecurityException) {
                    fail(error);
                } else {
                    fail(new IOException("HTTP GET failed for " + initialUrl + ": " + usefulMessage(error), error));
                }
            } finally {
                finish();
            }
        }

        private HttpResult execute() throws IOException, InterruptedException {
            URI current = parseUri(initialUrl);
            int redirects = 0;
            while (true) {
                ensureRunning();
                HttpRules.Rule rule = authorize(current);
                HttpRequest.Builder request = HttpRequest.newBuilder(current).GET();
                if (config.timeoutSeconds() > 0) {
                    request.timeout(Duration.ofSeconds(config.timeoutSeconds()));
                }
                HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream responseBody = response.body()) {
                    synchronized (lock) {
                        if (done) {
                            throw new CancellationException("HTTP request cancelled");
                        }
                        body = responseBody;
                    }
                    if (isRedirect(response.statusCode())) {
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> new IOException("Redirect response has no Location header"));
                        if (redirects >= config.maxRedirects()) {
                            throw new IOException("HTTP redirect limit exceeded at " + current);
                        }
                        try {
                            current = parseUri(current.resolve(new URI(location)).toString());
                        } catch (java.net.URISyntaxException exception) {
                            throw new IOException("Invalid HTTP redirect Location: " + location, exception);
                        }
                        redirects++;
                        continue;
                    }
                    long maximum = rule.maxDownload();
                    long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    if (maximum > 0 && declared > maximum) {
                        throw new IOException("HTTP response exceeds max_download of " + maximum + " bytes");
                    }
                    return readBody(response, responseBody, maximum);
                } finally {
                    synchronized (lock) {
                        body = null;
                    }
                }
            }
        }

        private HttpResult readBody(HttpResponse<InputStream> response, InputStream input, long maximum)
                throws IOException, InterruptedException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            while (true) {
                ensureRunning();
                int count = input.read(buffer);
                if (count < 0) {
                    break;
                }
                total += count;
                if (maximum > 0 && total > maximum) {
                    throw new IOException("HTTP response exceeds max_download of " + maximum + " bytes");
                }
                limiter.acquire(count);
                output.write(buffer, 0, count);
            }
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(output.toByteArray()))
                        .toString();
            } catch (CharacterCodingException exception) {
                throw new IOException("HTTP response body is not valid UTF-8", exception);
            }
            return new HttpResult(response.statusCode(), reason(response.statusCode()), text);
        }

        private HttpRules.Rule authorize(URI uri) throws IOException {
            String host = normalizedHost(uri);
            InetAddress[] resolved;
            try {
                resolved = InetAddress.getAllByName(host);
            } catch (IOException exception) {
                throw new IOException("Could not resolve HTTP host " + host, exception);
            }
            if (resolved.length == 0) {
                throw new IOException("HTTP host resolved to no addresses: " + host);
            }
            List<InetAddress> addresses = List.of(resolved);
            for (HttpRules.Rule rule : config.rules()) {
                if (rule.matches(host, addresses)) {
                    if (!rule.allow()) {
                        throw new SecurityException("HTTP address denied by rule for host " + host);
                    }
                    return rule;
                }
            }
            throw new SecurityException("HTTP address denied: no rule matched host " + host);
        }

        private URI parseUri(String value) {
            try {
                URI uri = new URI(value);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if (!scheme.equals("http") && !scheme.equals("https")) {
                    throw new IllegalArgumentException("Only http and https URLs are supported");
                }
                if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("Invalid HTTP URL: " + value);
                }
                normalizedHost(uri);
                return uri;
            } catch (Exception exception) {
                if (exception instanceof IllegalArgumentException illegal) {
                    throw illegal;
                }
                throw new IllegalArgumentException("Invalid HTTP URL: " + value, exception);
            }
        }

        private String normalizedHost(URI uri) {
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("HTTP URL has no host");
            }
            if (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
            return IDN.toASCII(host).toLowerCase(Locale.ROOT);
        }

        private void ensureRunning() {
            synchronized (lock) {
                if (done || closed) {
                    throw new CancellationException("HTTP request cancelled");
                }
            }
        }

        private void complete(HttpResult value) {
            synchronized (lock) {
                if (!done) {
                    done = true;
                    result.complete(value);
                }
            }
        }

        private void fail(Throwable error) {
            synchronized (lock) {
                if (!done) {
                    done = true;
                    closeBody();
                    Thread currentWorker = worker;
                    if (currentWorker != null && currentWorker != Thread.currentThread()) {
                        currentWorker.interrupt();
                    }
                    result.completeExceptionally(error);
                }
            }
        }

        private void cancel(String message) {
            fail(new CancellationException(message));
        }

        private void closeBody() {
            if (body != null) {
                try {
                    body.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void finish() {
            synchronized (lock) {
                closeBody();
                if (timeout != null) {
                    timeout.cancel(false);
                }
                if (active) {
                    active = false;
                    owner.active.remove(this);
                }
                pump(owner);
            }
        }
    }

    private static final class OwnerState {
        private final Object owner;
        private final ArrayList<Operation> active = new ArrayList<>();
        private final ArrayDeque<Operation> queued = new ArrayDeque<>();

        private OwnerState(Object owner) {
            this.owner = owner;
        }
    }

    private static String usefulMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "";
        };
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final ThreadFactory delegate = Executors.defaultThreadFactory();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = delegate.newThread(runnable);
            thread.setDaemon(true);
            thread.setName("taleos-http-" + thread.threadId());
            return thread;
        }
    }
}

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final String HOST = resolveHost();
    private static final String PAGE = loadPage();
    private static final AtomicLong REQUESTS = new AtomicLong();

    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/status", Main::status);
        server.createContext("/api", Main::api);
        server.createContext("/health", Main::health);
        server.createContext("/favicon.svg", Main::favicon);
        server.createContext("/", Main::page);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        System.out.println("java-demo listening on :" + PORT);
        server.start();
    }

    private static void page(HttpExchange exchange) throws IOException {
        if (!allowed(exchange)) {
            return;
        }
        var path = exchange.getRequestURI().getPath();
        if ("/favicon.ico".equals(path)) {
            favicon(exchange);
            return;
        }
        if (!"/".equals(path)) {
            send(exchange, 404, "text/plain; charset=utf-8", "introuvable\n");
            return;
        }
        var accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept != null && accept.contains("application/json") && !accept.contains("text/html")) {
            api(exchange);
            return;
        }
        REQUESTS.incrementAndGet();
        send(exchange, 200, "text/html; charset=utf-8", PAGE.replace("__BOOT__", statusJson()));
    }

    private static void api(HttpExchange exchange) throws IOException {
        if (!allowed(exchange)) {
            return;
        }
        REQUESTS.incrementAndGet();
        send(exchange, 200, "application/json; charset=utf-8", identityJson());
    }

    private static void status(HttpExchange exchange) throws IOException {
        if (!allowed(exchange)) {
            return;
        }
        send(exchange, 200, "application/json; charset=utf-8", statusJson());
    }

    private static void health(HttpExchange exchange) throws IOException {
        if (!allowed(exchange)) {
            return;
        }
        send(exchange, 200, "application/json; charset=utf-8", "{\"status\":\"ok\"}\n");
    }

    private static void favicon(HttpExchange exchange) throws IOException {
        if (!allowed(exchange)) {
            return;
        }
        send(exchange, 200, "image/svg+xml", """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
              <rect width="32" height="32" rx="7" fill="#0c322c"/>
              <rect x="6" y="7" width="20" height="18" rx="3" fill="#30ba78"/>
              <path d="M11 12h6.5a3.2 3.2 0 0 1 0 6.4H14" fill="none" stroke="#07110e" stroke-width="2.2" stroke-linecap="round"/>
            </svg>
            """);
    }

    private static boolean allowed(HttpExchange exchange) throws IOException {
        var method = exchange.getRequestMethod();
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", "GET, HEAD");
        send(exchange, 405, "text/plain; charset=utf-8", "méthode refusée\n");
        return false;
    }

    private static String identityJson() {
        return """
            {"app":"java-demo","message":"Hello from Java 21","stack":"Docker + Kubernetes","formation":"SUSE Rancher 2026"}
            """.trim() + "\n";
    }

    private static String statusJson() {
        var runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long reserved = runtime.totalMemory();
        long max = runtime.maxMemory();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        return """
            {"app":"java-demo","message":"Hello from Java 21","stack":"Docker + Kubernetes","formation":"SUSE Rancher 2026","hostname":%s,"java":%s,"pid":%d,"uptimeMs":%d,"heapUsed":%d,"heapReserved":%d,"heapMax":%d,"requests":%d,"port":%d}
            """.formatted(
                quote(HOST),
                quote(System.getProperty("java.version", "")),
                ProcessHandle.current().pid(),
                uptime,
                used,
                reserved,
                max,
                REQUESTS.get(),
                PORT
            ).trim();
    }

    private static void send(HttpExchange exchange, int code, String type, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(code, head ? -1 : bytes.length);
        if (!head) {
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } else {
            exchange.close();
        }
    }

    private static String loadPage() {
        for (var path : new Path[] { Path.of("web/index.html"), Path.of("src/web/index.html") }) {
            if (Files.isRegularFile(path)) {
                try {
                    return Files.readString(path);
                } catch (IOException error) {
                    throw new IllegalStateException("lecture impossible : " + path, error);
                }
            }
        }
        throw new IllegalStateException("web/index.html introuvable");
    }

    private static String resolveHost() {
        var fromEnv = System.getenv("HOSTNAME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception error) {
            return "localhost";
        }
    }

    private static String quote(String value) {
        var out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }
}

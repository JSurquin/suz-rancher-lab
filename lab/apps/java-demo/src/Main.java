public class Main {
    public static void main(String[] args) throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(8080), 0);
        server.createContext("/", exchange -> {
            var body = """
                {"app":"java-demo","message":"Hello from Java 21","stack":"Docker + Kubernetes","formation":"SUSE Rancher 2026"}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.setExecutor(null);
        System.out.println("java-demo listening on :8080");
        server.start();
    }
}

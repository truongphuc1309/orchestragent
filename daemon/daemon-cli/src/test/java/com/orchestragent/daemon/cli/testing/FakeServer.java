package com.orchestragent.daemon.cli.testing;

import com.orchestragent.daemon.cli.client.DaemonClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * A tiny HTTP server for tests, on the JDK's {@link HttpServer}: routes answer with canned or computed replies, and
 * every request is recorded. A request no route handles gets 500, and is listed in {@link #unhandled()}.
 */
public final class FakeServer implements AutoCloseable {

    public record Call(String method, String path, String authorization, String userAgent, String body) {
    }

    public record Reply(int status, String body) {
    }

    @FunctionalInterface
    public interface Handler {
        Reply handle(Call call);
    }

    private record Route(String method, Pattern path, Handler handler) {
    }

    private final HttpServer server;
    private final List<Route> routes = new CopyOnWriteArrayList<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final List<Call> unhandled = new CopyOnWriteArrayList<>();

    public FakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::serve);
        server.start();
    }

    public URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Adds a route; a later route for the same request wins over an earlier one. */
    public FakeServer on(String method, String pathRegex, Handler handler) {
        routes.addFirst(new Route(method, Pattern.compile(pathRegex), handler));
        return this;
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public List<Call> calls(String method, String pathRegex) {
        Pattern pattern = Pattern.compile(pathRegex);
        List<Call> matching = new ArrayList<>();
        for (Call call : calls) {
            if (call.method().equals(method) && pattern.matcher(call.path()).matches()) {
                matching.add(call);
            }
        }
        return matching;
    }

    public List<Call> unhandled() {
        return List.copyOf(unhandled);
    }

    public static Reply json(Object body) {
        return new Reply(200, DaemonClient.JSON.writeValueAsString(body));
    }

    public static Reply status(String status) {
        return new Reply(200, "{\"status\":\"" + status + "\"}");
    }

    public static Reply problem(int status, String code) {
        return new Reply(status, "{\"type\":\"about:blank\",\"title\":\"Error\",\"status\":" + status
                + ",\"detail\":\"Detail of " + code + "\",\"code\":\"" + code + "\"}");
    }

    private void serve(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Call call = new Call(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("User-Agent"),
                body);
        calls.add(call);
        Reply reply = null;
        for (Route route : routes) {
            if (route.method().equals(call.method()) && route.path().matcher(call.path()).matches()) {
                try {
                    reply = route.handler().handle(call);
                } catch (RuntimeException e) {
                    reply = new Reply(500, "{\"detail\":\"" + e + "\"}");
                }
                break;
            }
        }
        if (reply == null) {
            unhandled.add(call);
            reply = new Reply(500, "{\"detail\":\"no route\"}");
        }
        byte[] bytes = reply.body() == null ? new byte[0] : reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

package com.cobbledex.web.handlers;

import com.cobbledex.web.SseManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SseHandler implements HttpHandler {

    private final SseManager sseManager;

    public SseHandler(SseManager sseManager) {
        this.sseManager = sseManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().add("Content-Type",  "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection",    "keep-alive");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0); // 0 = streaming, no fixed content-length

        OutputStream os = exchange.getResponseBody();
        try {
            os.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();
            sseManager.addClient(os);

            // Block this thread; heartbeat every 30 s detects dead connections.
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(30_000);
                os.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (IOException | InterruptedException ignored) {
            // Client closed the connection or server is shutting down.
        } finally {
            sseManager.removeClient(os);
            try { exchange.close(); } catch (Exception ignored) {}
        }
    }
}

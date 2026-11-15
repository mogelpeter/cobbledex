package com.cobbledex.web.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class StaticHandler implements HttpHandler {

    private static final Map<String, String> MIME = Map.of(
        ".css", "text/css; charset=utf-8",
        ".js",  "application/javascript; charset=utf-8"
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        String path = exchange.getRequestURI().getPath(); // e.g. /static/style.css
        String filename = path.substring(path.lastIndexOf('/'));
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
        String mime = MIME.getOrDefault(ext, "application/octet-stream");
        String resource = "/assets/cobbledex/web" + filename;

        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = stream.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", mime);
            exchange.getResponseHeaders().add("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}

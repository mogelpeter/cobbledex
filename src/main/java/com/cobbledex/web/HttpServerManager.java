package com.cobbledex.web;

import com.cobbledex.config.CobbleDexConfig;
import com.cobbledex.startup.StartupValidator;
import com.cobbledex.data.DataCache;
import com.cobbledex.web.handlers.DataHandler;
import com.cobbledex.web.handlers.IndexHandler;
import com.cobbledex.web.handlers.SpeciesHandler;
import com.cobbledex.web.handlers.SseHandler;
import com.cobbledex.web.handlers.StaticHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class HttpServerManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private final DataCache dataCache;
    private final SseManager sseManager;
    private HttpServer server;

    public HttpServerManager(DataCache dataCache, SseManager sseManager) {
        this.dataCache = dataCache;
        this.sseManager = sseManager;
    }

    public void start() {
        try {
            String host = CobbleDexConfig.HOST.get();
            int port = StartupValidator.findAvailablePort(CobbleDexConfig.PORT.get());
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            // Register most specific paths first — HttpServer matches by longest prefix.
            server.createContext("/events",       new SseHandler(sseManager));
            server.createContext("/api/data",    new DataHandler(dataCache));
            server.createContext("/api/species", new SpeciesHandler());
            server.createContext("/static/",     new StaticHandler());
            server.createContext("/",            new IndexHandler());
            // CachedThreadPool: each SSE client blocks one thread, unbounded growth needed.
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            LOGGER.info("CobbleDex web server started — http://{}:{}", host, port);
        } catch (IOException e) {
            LOGGER.error("CobbleDex web server failed to start", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            LOGGER.info("CobbleDex web server stopped");
        }
    }
}

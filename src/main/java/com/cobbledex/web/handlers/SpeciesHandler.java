package com.cobbledex.web.handlers;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpeciesHandler implements HttpHandler {

    private static final Gson GSON = new GsonBuilder().create();
    private volatile String cachedJson = null;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String json = getOrBuildJson();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "max-age=3600");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getOrBuildJson() {
        if (cachedJson != null) return cachedJson;
        List<Map<String, Object>> list = new ArrayList<>();
        for (Species species : PokemonSpecies.getImplemented()) {
            int dex = species.getNationalPokedexNumber();
            if (dex < 1 || dex > 1025) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dexNumber", dex);
            entry.put("species", species.showdownId());
            entry.put("displayName", species.getName());
            list.add(entry);
        }
        list.sort(Comparator.comparingInt(e -> (int) e.get("dexNumber")));
        cachedJson = GSON.toJson(list);
        return cachedJson;
    }
}

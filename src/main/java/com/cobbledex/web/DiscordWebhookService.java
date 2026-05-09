package com.cobbledex.web;

import com.cobbledex.config.CobbleDexConfig;
import com.cobbledex.data.PokemonEntry;
import com.cobbledex.startup.StartupValidator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiscordWebhookService {

    public static final DiscordWebhookService INSTANCE = new DiscordWebhookService();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    private final HttpClient http;

    private DiscordWebhookService() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Called when a player catches a Pokemon. No-op if webhook is disabled or URL invalid. */
    public void notifyCatch(String playerName, PokemonEntry pokemon) {
        if (!CobbleDexConfig.DISCORD_WEBHOOK_ENABLED.get()) return;
        String url = CobbleDexConfig.DISCORD_WEBHOOK_URL.get();
        if (!StartupValidator.validateWebhookUrl(url)) return;

        String payload = buildPayload(playerName, pokemon);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(10))
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(res -> {
                    if (res.statusCode() < 200 || res.statusCode() >= 300) {
                        LOGGER.warn("CobbleDex: Discord webhook returned HTTP {}", res.statusCode());
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("CobbleDex: Discord webhook POST failed: {}", e.getMessage());
                    return null;
                });
    }

    /** Package-private for unit testing without network calls. */
    static String buildPayload(String playerName, PokemonEntry p) {
        boolean shiny = p.shiny();
        int color = shiny ? 0xFFD700 : 0x3FB950;
        String title = (shiny ? "✨ Shiny " : "") + p.displayName() + " was caught!";

        List<Map<String, Object>> fields = new ArrayList<>();
        addField(fields, "Player",  playerName, true);
        addField(fields, "Pokemon",
                "#" + String.format("%03d", p.dexNumber()) + " " + p.displayName(), true);
        addField(fields, "Level",   String.valueOf(p.level()),  true);
        addField(fields, "Nature",  p.nature(),                 true);
        addField(fields, "Ball",    (p.ball() == null || p.ball().isBlank()) ? "Unknown" : p.ball(), true);
        addField(fields, "Shiny",   shiny ? "Yes ✨" : "No", true);

        if (p.nickname() != null && !p.nickname().isBlank()) {
            addField(fields, "Nickname", p.nickname(), true);
        }

        if (p.ivs() != null && !p.ivs().isEmpty()) {
            Map<String, Integer> iv = p.ivs();
            String ivStr = iv.getOrDefault("hp",  0) + "/" +
                           iv.getOrDefault("atk", 0) + "/" +
                           iv.getOrDefault("def", 0) + "/" +
                           iv.getOrDefault("spa", 0) + "/" +
                           iv.getOrDefault("spd", 0) + "/" +
                           iv.getOrDefault("spe", 0);
            addField(fields, "IVs (HP/Atk/Def/SpA/SpD/Spe)", ivStr, false);
        }

        if (p.moves() != null && !p.moves().isEmpty()) {
            String moveStr = p.moves().stream()
                    .map(m -> m.getOrDefault("name", "?"))
                    .reduce((a, b) -> a + " · " + b)
                    .orElse("—");
            addField(fields, "Moves", moveStr, false);
        }

        String spriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/"
                + (shiny ? "shiny/" : "") + p.dexNumber() + ".png";

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title",     title);
        embed.put("url",       "https://dex.lyx.wtf/");
        embed.put("color",     color);
        embed.put("thumbnail", Map.of("url", spriteUrl));
        embed.put("fields",    fields);
        embed.put("footer",    Map.of("text", "CobbleDex"));
        embed.put("timestamp", Instant.now().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username",   "CobbleDex");
        payload.put("avatar_url", "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png");
        payload.put("embeds",     List.of(embed));
        return GSON.toJson(payload);
    }

    private static void addField(List<Map<String, Object>> fields,
                                  String name, String value, boolean inline) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name",   name);
        m.put("value",  value);
        m.put("inline", inline);
        fields.add(m);
    }
}

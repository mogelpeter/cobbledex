package com.cobbledex.web;

import com.cobbledex.data.PokemonEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiscordWebhookServiceTest {

    private static PokemonEntry makeEntry(boolean shiny, String nickname) {
        return new PokemonEntry(
            "charizard", 6, "Charizard",
            List.of("Fire", "Flying"),
            nickname,
            50, "Adamant", "MALE", shiny,
            List.of(
                Map.of("name", "Flamethrower", "type", "Fire"),
                Map.of("name", "Earthquake",   "type", "Ground")
            ),
            Map.of("hp", 31, "atk", 31, "def", 31, "spa", 31, "spd", 31, "spe", 31),
            Map.of("hp", 0,  "atk", 252,"def", 0,  "spa", 0,  "spd", 4,  "spe", 252),
            Map.of("hp", 297,"atk", 299,"def", 193,"spa", 236,"spd", 176,"spe", 328),
            "Ultra Ball", "party"
        );
    }

    private static PokemonEntry makeDefensiveEntry() {
        return new PokemonEntry(
            "bulbasaur", 1, "Bulbasaur",
            List.of("Grass", "Poison"),
            null, 5, "Bashful", "MALE", false,
            null, null, null, null, "", "party"
        );
    }

    @Test
    void buildPayload_normalPokemon_containsPlayerAndSpecies() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(false, null), "");
        assertTrue(json.contains("Steve"));
        assertTrue(json.contains("Charizard"));
        assertTrue(json.contains("Charizard was caught!"));
    }

    @Test
    void buildPayload_shinyPokemon_hasShinyInTitle() {
        String json = DiscordWebhookService.buildPayload("Alex", makeEntry(true, null), "");
        assertTrue(json.contains("Shiny Charizard was caught!"));
    }

    @Test
    void buildPayload_withNickname_includesNickname() {
        String json = DiscordWebhookService.buildPayload("Bob", makeEntry(false, "Blaze"), "");
        assertTrue(json.contains("Blaze"));
    }

    @Test
    void buildPayload_hasIvField() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(false, null), "");
        assertTrue(json.contains("31/31/31/31/31/31"));
    }

    @Test
    void buildPayload_hasMoveNames() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(false, null), "");
        assertTrue(json.contains("Flamethrower"));
        assertTrue(json.contains("Earthquake"));
    }

    @Test
    void buildPayload_nullMovesAndIvs_doesNotThrow() {
        assertDoesNotThrow(() -> DiscordWebhookService.buildPayload("Dave", makeDefensiveEntry(), ""));
    }

    @Test
    void buildPayload_blankBall_showsUnknown() {
        String json = DiscordWebhookService.buildPayload("Dave", makeDefensiveEntry(), "");
        assertTrue(json.contains("Unknown"));
    }

    @Test
    void buildPayload_withPublicUrl_embedContainsSpeciesLink() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(false, null), "https://dex.example.com");
        assertTrue(json.contains("https://dex.example.com#charizard"));
    }

    @Test
    void buildPayload_withoutPublicUrl_embedHasNoUrlField() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(false, null), "");
        assertFalse(json.contains("#charizard"));
    }

    @Test
    void buildPayload_shinyWithPublicUrl_linkContainsSpecies() {
        String json = DiscordWebhookService.buildPayload("Alex", makeEntry(true, null), "https://dex.example.com");
        assertTrue(json.contains("https://dex.example.com#charizard"));
    }

    @Test
    void buildPayload_hasThumbnailWithSpriteUrl() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(false, null), "");
        assertTrue(json.contains("\"thumbnail\""));
        assertTrue(json.contains("sprites/pokemon/6.png"));
    }

    @Test
    void buildPayload_shinyHasShinySpriteUrl() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(true, null), "");
        assertTrue(json.contains("sprites/pokemon/shiny/6.png"));
    }

    @Test
    void buildPayload_hasFooter() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(false, null), "");
        assertTrue(json.contains("\"footer\""));
        assertTrue(json.contains("CobbleDex"));
    }

    @Test
    void buildPayload_hasTimestamp() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(false, null), "");
        assertTrue(json.contains("\"timestamp\""));
    }

    @Test
    void buildPayload_hasWebhookUsernameAndAvatar() {
        String json = DiscordWebhookService.buildPayload("Steve", makeEntry(false, null), "");
        assertTrue(json.contains("\"username\""));
        assertTrue(json.contains("\"avatar_url\""));
        assertTrue(json.contains("poke-ball.png"));
    }
}

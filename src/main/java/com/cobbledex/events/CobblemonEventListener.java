package com.cobbledex.events;

import com.cobbledex.data.DataCache;
import com.cobbledex.data.PlayerSnapshot;
import com.cobbledex.data.PokemonEntry;
import com.cobbledex.web.DiscordWebhookService;
import com.cobbledex.web.SseManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.pc.ConstantsKt;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.PokemonStats;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class CobblemonEventListener {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pushSnapshot(player);
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DataCache.INSTANCE.remove(player.getUUID());
        SseManager.INSTANCE.broadcast(
            GSON.toJson(Map.of("type", "playerLeft", "uuid", player.getUUID().toString()))
        );
    }

    public static void onPokemonCaptured(PokemonCapturedEvent event) {
        pushSnapshot(event.getPlayer());
        PokemonEntry caught = buildEntry(event.getPokemon(), "party");
        DiscordWebhookService.INSTANCE.notifyCatch(
                event.getPlayer().getGameProfile().getName(), caught);
    }

    private static void pushSnapshot(ServerPlayer player) {
        PlayerSnapshot snapshot = snapshotPlayer(player);
        DataCache.INSTANCE.put(player.getUUID(), snapshot);
        SseManager.INSTANCE.broadcast(
            GSON.toJson(Map.of("type", "playerUpdate", "player", snapshot))
        );
    }

    private static PlayerSnapshot snapshotPlayer(ServerPlayer player) {
        List<PokemonEntry> pokemonList = new ArrayList<>();
        Set<String> caughtIds = new LinkedHashSet<>();

        // Party (6 slots; use get(int) to avoid Kotlin Iterable's KMappedMarker)
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int i = 0; i < 6; i++) {
            Pokemon p = party.get(i);
            if (p == null) continue;
            pokemonList.add(buildEntry(p, "party"));
            caughtIds.add(p.getSpecies().showdownId());
        }

        // PC boxes (use index loops to avoid Kotlin KMappedMarker on Iterable)
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
        if (pc != null) {
            for (PCBox box : pc.getBoxes()) {
                String label = (box.getName() != null && !box.getName().isEmpty())
                    ? box.getName()
                    : "Box " + (box.getBoxNumber() + 1);
                for (int slot = 0; slot < ConstantsKt.POKEMON_PER_BOX; slot++) {
                    Pokemon p = box.get(slot);
                    if (p == null) continue;
                    pokemonList.add(buildEntry(p, "pc:" + label));
                    caughtIds.add(p.getSpecies().showdownId());
                }
            }
        }

        // Seen (encountered but not yet caught) from Pokédex data
        List<String> seenSpecies = new ArrayList<>();
        try {
            PokedexManager dex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
            if (dex != null) {
                for (var entry : dex.getSpeciesRecords().entrySet()) {
                    PokedexEntryProgress progress = dex.getHighestKnowledgeForSpecies(entry.getKey());
                    String id = entry.getKey().getPath();
                    if (progress == PokedexEntryProgress.ENCOUNTERED && !caughtIds.contains(id)) {
                        seenSpecies.add(id);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read Cobblemon dex data for {}: {}", player.getName().getString(), e.getMessage());
        }

        return new PlayerSnapshot(
            player.getGameProfile().getName(),
            player.getUUID().toString(),
            List.copyOf(caughtIds),
            seenSpecies,
            pokemonList
        );
    }

    private static PokemonEntry buildEntry(Pokemon pokemon, String location) {
        List<String> types = new ArrayList<>();
        types.add(capitalize(pokemon.getPrimaryType().getName()));
        ElementalType secondary = pokemon.getSecondaryType();
        if (secondary != null) types.add(capitalize(secondary.getName()));

        // Moves with type info (use stream to avoid Kotlin KMappedMarker)
        List<Map<String, String>> moves = new ArrayList<>();
        pokemon.getMoveSet().getMoves().stream().forEach(m -> {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("name", toDisplayName(m.getName()));
            entry.put("type", capitalize(m.getType().getName()));
            moves.add(entry);
        });

        Map<String, Integer> ivs   = buildStatMap(pokemon.getIvs());
        Map<String, Integer> evs   = buildStatMap(pokemon.getEvs());
        Map<String, Integer> stats = buildActualStats(pokemon);

        String nickname = null;
        if (pokemon.getNickname() != null) nickname = pokemon.getNickname().getString();

        // Nature: getDisplayName() returns translation key like "cobblemon.nature.quiet"
        String natureName = pokemon.getEffectiveNature().getDisplayName();
        if (natureName.contains(".")) {
            natureName = capitalize(natureName.substring(natureName.lastIndexOf('.') + 1).replace('_', ' '));
        }

        // Ball: getCaughtBall() can be null for starters / command-spawned Pokémon
        var caughtBall = pokemon.getCaughtBall();
        String ball = (caughtBall != null) ? toTitleCase(caughtBall.getName().getPath()) : "";

        return new PokemonEntry(
            pokemon.getSpecies().showdownId(),
            pokemon.getSpecies().getNationalPokedexNumber(),
            pokemon.getSpecies().getName(),
            types,
            nickname,
            pokemon.getLevel(),
            natureName,
            pokemon.getGender().name(),
            pokemon.getShiny(),
            moves,
            ivs,
            evs,
            stats,
            ball,
            location
        );
    }

    private static Map<String, Integer> buildStatMap(PokemonStats stats) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("hp",  orZero(stats.get(Stats.HP)));
        map.put("atk", orZero(stats.get(Stats.ATTACK)));
        map.put("def", orZero(stats.get(Stats.DEFENCE)));
        map.put("spa", orZero(stats.get(Stats.SPECIAL_ATTACK)));
        map.put("spd", orZero(stats.get(Stats.SPECIAL_DEFENCE)));
        map.put("spe", orZero(stats.get(Stats.SPEED)));
        return map;
    }

    private static Map<String, Integer> buildActualStats(Pokemon pokemon) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("hp",  pokemon.getStat(Stats.HP));
        map.put("atk", pokemon.getStat(Stats.ATTACK));
        map.put("def", pokemon.getStat(Stats.DEFENCE));
        map.put("spa", pokemon.getStat(Stats.SPECIAL_ATTACK));
        map.put("spd", pokemon.getStat(Stats.SPECIAL_DEFENCE));
        map.put("spe", pokemon.getStat(Stats.SPEED));
        return map;
    }

    private static int orZero(Integer v) {
        return v != null ? v : 0;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    // "dragon-claw" → "Dragon Claw" (hyphen-separated showdown IDs)
    private static String toDisplayName(String showdownId) {
        if (showdownId == null || showdownId.isEmpty()) return showdownId;
        String[] parts = showdownId.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    // "poke_ball" → "Poke Ball" (underscore-separated identifiers like ball names)
    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] parts = s.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}

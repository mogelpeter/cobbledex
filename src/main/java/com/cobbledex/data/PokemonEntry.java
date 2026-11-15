package com.cobbledex.data;

import java.util.List;
import java.util.Map;

public record PokemonEntry(
    String species,                     // showdown ID, e.g. "charizard"
    int dexNumber,                      // e.g. 6
    String displayName,                 // e.g. "Charizard"
    List<String> types,                 // e.g. ["Fire", "Flying"]
    String nickname,                    // null if none set
    int level,
    String nature,                      // e.g. "Adamant"
    String gender,                      // "MALE", "FEMALE", or "GENDERLESS"
    boolean shiny,
    List<Map<String, String>> moves,    // [{name, type}] — up to 4 moves
    Map<String, Integer> ivs,           // keys: hp, atk, def, spa, spd, spe
    Map<String, Integer> evs,
    Map<String, Integer> stats,         // actual calculated battle stats
    String ball,                        // Poké Ball used to catch, e.g. "Ultra Ball"
    String location                     // "party" or "pc:Box N"
) {}

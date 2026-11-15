package com.cobbledex.data;

import java.util.List;

public record PlayerSnapshot(
    String playerName,
    String uuid,
    List<String> caughtSpecies,  // showdown IDs of caught species
    List<String> seenSpecies,    // showdown IDs seen but not caught
    List<PokemonEntry> pokemon   // all individual Pokémon (party + PC)
) {}

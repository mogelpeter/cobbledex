package com.cobbledex.data;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataCache {

    public static final DataCache INSTANCE = new DataCache();

    private final ConcurrentHashMap<UUID, PlayerSnapshot> players = new ConcurrentHashMap<>();

    public void put(UUID uuid, PlayerSnapshot snapshot) {
        players.put(uuid, snapshot);
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public Map<UUID, PlayerSnapshot> snapshot() {
        return Collections.unmodifiableMap(players);
    }
}

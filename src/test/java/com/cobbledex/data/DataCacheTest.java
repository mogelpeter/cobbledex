package com.cobbledex.data;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DataCacheTest {

    private static PlayerSnapshot makeSnap(String name, UUID uuid) {
        return new PlayerSnapshot(name, uuid.toString(), List.of(), List.of(), List.of());
    }

    @Test
    void putAndRetrieve() {
        DataCache cache = new DataCache();
        UUID uuid = UUID.randomUUID();
        cache.put(uuid, makeSnap("Steve", uuid));
        assertTrue(cache.snapshot().containsKey(uuid));
        assertEquals("Steve", cache.snapshot().get(uuid).playerName());
    }

    @Test
    void remove() {
        DataCache cache = new DataCache();
        UUID uuid = UUID.randomUUID();
        cache.put(uuid, makeSnap("Alex", uuid));
        cache.remove(uuid);
        assertFalse(cache.snapshot().containsKey(uuid));
    }

    @Test
    void snapshotIsUnmodifiable() {
        DataCache cache = new DataCache();
        Map<UUID, PlayerSnapshot> snap = cache.snapshot();
        assertThrows(UnsupportedOperationException.class,
            () -> snap.put(UUID.randomUUID(), makeSnap("X", UUID.randomUUID())));
    }

    @Test
    void multiplePlayersStoredIndependently() {
        DataCache cache = new DataCache();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        cache.put(u1, makeSnap("P1", u1));
        cache.put(u2, makeSnap("P2", u2));
        assertEquals(2, cache.snapshot().size());
        assertEquals("P1", cache.snapshot().get(u1).playerName());
        assertEquals("P2", cache.snapshot().get(u2).playerName());
    }

    @Test
    void overwriteUpdatesSnapshot() {
        DataCache cache = new DataCache();
        UUID uuid = UUID.randomUUID();
        cache.put(uuid, makeSnap("OldName", uuid));
        cache.put(uuid, makeSnap("NewName", uuid));
        assertEquals("NewName", cache.snapshot().get(uuid).playerName());
        assertEquals(1, cache.snapshot().size());
    }
}

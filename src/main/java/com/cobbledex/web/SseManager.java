package com.cobbledex.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SseManager {

    public static final SseManager INSTANCE = new SseManager();

    private final CopyOnWriteArrayList<OutputStream> clients = new CopyOnWriteArrayList<>();

    public void addClient(OutputStream os) {
        clients.add(os);
    }

    public void removeClient(OutputStream os) {
        clients.remove(os);
    }

    /** Broadcasts a raw JSON string as an SSE data frame. Dead clients are removed. */
    public void broadcast(String json) {
        byte[] bytes = ("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
        List<OutputStream> dead = new ArrayList<>();
        for (OutputStream client : clients) {
            try {
                client.write(bytes);
                client.flush();
            } catch (IOException e) {
                dead.add(client);
            }
        }
        clients.removeAll(dead);
    }

    public int clientCount() {
        return clients.size();
    }
}

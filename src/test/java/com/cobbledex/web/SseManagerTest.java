package com.cobbledex.web;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SseManagerTest {

    @Test
    void broadcastWritesCorrectSseFormat() {
        SseManager mgr = new SseManager();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mgr.addClient(out);
        mgr.broadcast("{\"test\":true}");
        assertEquals("data: {\"test\":true}\n\n", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void deadClientIsRemovedOnNextBroadcast() {
        SseManager mgr = new SseManager();
        OutputStream dead = new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("dead"); }
            @Override public void write(byte[] b) throws IOException { throw new IOException("dead"); }
        };
        mgr.addClient(dead);
        assertEquals(1, mgr.clientCount());
        mgr.broadcast("{}");
        assertEquals(0, mgr.clientCount());
    }

    @Test
    void multipleClientsAllReceiveBroadcast() {
        SseManager mgr = new SseManager();
        ByteArrayOutputStream b1 = new ByteArrayOutputStream();
        ByteArrayOutputStream b2 = new ByteArrayOutputStream();
        mgr.addClient(b1);
        mgr.addClient(b2);
        mgr.broadcast("hello");
        assertEquals("data: hello\n\n", b1.toString(StandardCharsets.UTF_8));
        assertEquals("data: hello\n\n", b2.toString(StandardCharsets.UTF_8));
    }

    @Test
    void deadClientDoesNotAffectLiveClients() {
        SseManager mgr = new SseManager();
        OutputStream dead = new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("dead"); }
            @Override public void write(byte[] b) throws IOException { throw new IOException("dead"); }
        };
        ByteArrayOutputStream live = new ByteArrayOutputStream();
        mgr.addClient(dead);
        mgr.addClient(live);
        mgr.broadcast("test");
        assertEquals("data: test\n\n", live.toString(StandardCharsets.UTF_8));
        assertEquals(1, mgr.clientCount());
    }

    @Test
    void removeClientStopsReceivingBroadcasts() {
        SseManager mgr = new SseManager();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mgr.addClient(out);
        mgr.removeClient(out);
        mgr.broadcast("should-not-arrive");
        assertEquals("", out.toString(StandardCharsets.UTF_8));
    }
}

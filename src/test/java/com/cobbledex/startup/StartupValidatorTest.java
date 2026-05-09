package com.cobbledex.startup;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

class StartupValidatorTest {

    @Test
    void findAvailablePort_freePort_returnsThatPort() {
        int port = StartupValidator.findAvailablePort(19999);
        assertEquals(19999, port);
    }

    @Test
    void findAvailablePort_portInUse_returnsNextPort() throws IOException {
        try (ServerSocket bound = new ServerSocket()) {
            bound.setReuseAddress(false);
            bound.bind(new InetSocketAddress(19998));
            int result = StartupValidator.findAvailablePort(19998);
            assertEquals(19999, result);
        }
    }

    @Test
    void validateWebhookUrl_discordComUrl_returnsTrue() {
        assertTrue(StartupValidator.validateWebhookUrl(
                "https://discord.com/api/webhooks/123456789/abcdefghijk"));
    }

    @Test
    void validateWebhookUrl_discordappComUrl_returnsTrue() {
        assertTrue(StartupValidator.validateWebhookUrl(
                "https://discordapp.com/api/webhooks/123456789/token"));
    }

    @Test
    void validateWebhookUrl_emptyString_returnsFalse() {
        assertFalse(StartupValidator.validateWebhookUrl(""));
    }

    @Test
    void validateWebhookUrl_null_returnsFalse() {
        assertFalse(StartupValidator.validateWebhookUrl(null));
    }

    @Test
    void validateWebhookUrl_arbitraryUrl_returnsFalse() {
        assertFalse(StartupValidator.validateWebhookUrl("https://evil.com/api/webhooks/foo/bar"));
    }
}

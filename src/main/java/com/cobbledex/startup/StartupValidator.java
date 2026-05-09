package com.cobbledex.startup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class StartupValidator {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_PORT_RETRIES = 10;

    /**
     * Scans upward from basePort and returns the first available port.
     * Returns 0 (OS-assigned) if no port is free within MAX_PORT_RETRIES attempts.
     */
    public static int findAvailablePort(int basePort) {
        for (int delta = 0; delta < MAX_PORT_RETRIES; delta++) {
            int candidate = basePort + delta;
            if (isPortAvailable(candidate)) {
                if (delta > 0) {
                    LOGGER.warn("CobbleDex: port {} in use; using {} instead", basePort, candidate);
                }
                return candidate;
            }
        }
        LOGGER.error("CobbleDex: no free port in [{}, {}]; letting OS assign one",
                basePort, basePort + MAX_PORT_RETRIES - 1);
        return 0;
    }

    /** Returns true only if the URL is a real Discord webhook endpoint. */
    public static boolean validateWebhookUrl(String url) {
        if (url == null || url.isBlank()) return false;
        return url.startsWith("https://discord.com/api/webhooks/") ||
               url.startsWith("https://discordapp.com/api/webhooks/");
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(false);
            ss.bind(new InetSocketAddress(port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

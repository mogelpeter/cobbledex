package com.cobbledex.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CobbleDexConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue PORT;
    public static final ModConfigSpec.ConfigValue<String> HOST;
    public static final ModConfigSpec.BooleanValue DISCORD_WEBHOOK_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> DISCORD_WEBHOOK_URL;
    public static final ModConfigSpec.ConfigValue<String> COBBLEDEX_PUBLIC_URL;

    static {
        BUILDER.comment("CobbleDex web server settings").push("server");
        PORT = BUILDER
            .comment("Port the web server listens on (default 8080)")
            .defineInRange("port", 8080, 1, 65535);
        HOST = BUILDER
            .comment("Bind address. Use 0.0.0.0 for LAN access, 127.0.0.1 for localhost only.")
            .define("host", "0.0.0.0");
        BUILDER.pop();

        BUILDER.comment("Discord webhook settings").push("discord");
        DISCORD_WEBHOOK_ENABLED = BUILDER
            .comment("Set to true to push caught Cobblemon to a Discord channel")
            .define("enabled", false);
        DISCORD_WEBHOOK_URL = BUILDER
            .comment("Webhook URL from: Discord channel -> Edit Channel -> Integrations -> Webhooks")
            .define("webhookUrl", "");
        BUILDER.pop();

        BUILDER.comment("Web interface settings").push("web");
        COBBLEDEX_PUBLIC_URL = BUILDER
            .comment("Public URL of the CobbleDex web interface (no trailing slash). " +
                     "If set, Discord embed title links directly to the caught Cobblemon. " +
                     "Example: https://dex.example.com")
            .define("publicUrl", "");
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}

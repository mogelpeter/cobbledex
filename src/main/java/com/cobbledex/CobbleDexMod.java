package com.cobbledex;

import com.cobbledex.config.CobbleDexConfig;
import com.cobbledex.data.DataCache;
import com.cobbledex.events.CobblemonEventListener;
import com.cobbledex.web.HttpServerManager;
import com.cobbledex.web.SseManager;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import java.util.function.Consumer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("cobbledex")
public class CobbleDexMod {

    public static final String MOD_ID = "cobbledex";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private HttpServerManager httpServerManager;

    public CobbleDexMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, CobbleDexConfig.SPEC, "cobbledex-server.toml");

        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CobblemonEventListener::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(CobblemonEventListener::onPlayerLogout);

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Explicit cast to Consumer to resolve ambiguity with Kotlin's Function1 overload
        event.enqueueWork(() ->
            CobblemonEvents.POKEMON_CAPTURED.subscribe(
                Priority.NORMAL,
                (Consumer<PokemonCapturedEvent>) CobblemonEventListener::onPokemonCaptured
            )
        );
    }

    private void onServerStarted(ServerStartedEvent event) {
        httpServerManager = new HttpServerManager(DataCache.INSTANCE, SseManager.INSTANCE);
        httpServerManager.start();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (httpServerManager != null) httpServerManager.stop();
    }
}

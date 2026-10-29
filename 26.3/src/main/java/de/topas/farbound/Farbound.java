package de.topas.farbound;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Farbound implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("farbound");
    public static ExpeditionService service;
    public static final TicketType SEARCH_TICKET = new TicketType(2400, TicketType.FLAG_LOADING);
    public void onInitialize() {
        Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("farbound", "search"), SEARCH_TICKET);
        FarboundNetwork.register();
        FarboundHandshake.registerServer();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> FarboundCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> service = new ExpeditionService(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (service != null) service.tick(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (service != null) service.close(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> service = null);
        ServerPlayerEvents.JOIN.register(player -> { if (service != null) service.joined(player); });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> ((VaultCarrier)newPlayer).farbound$vault(PlayerVault.data(oldPlayer).copy()));
    }
}


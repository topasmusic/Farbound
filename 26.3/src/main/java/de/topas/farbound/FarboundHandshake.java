package de.topas.farbound;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Exact version check during login, before a player or expedition profile is loaded. */
public final class FarboundHandshake {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("farbound", "version_v1");
    private FarboundHandshake() {}
    public static String version() {
        return FabricLoader.getInstance().getModContainer("farbound").orElseThrow().getMetadata().getVersion().getFriendlyString();
    }
    public static void registerServer() {
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL, (server, handler, understood, buf, synchronizer, sender) -> {
            String problem = problem(understood, buf, version());
            if (problem != null) handler.disconnect(Component.literal(problem));
        });
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) ->
            sender.sendPacket(CHANNEL, new FriendlyByteBuf(Unpooled.buffer()).writeUtf(version())));
    }
    static String problem(boolean understood, FriendlyByteBuf buf, String required) {
        String client = "missing or outdated";
        if (understood && buf != null) {
            try {
                client = buf.readUtf(128);
                if (!buf.isReadable() && required.equals(client)) return null;
            } catch (RuntimeException malformed) { client = "invalid version response"; }
        }
        return "Farbound version mismatch. This server requires Farbound " + required
            + ". Install that exact version for this Minecraft version before joining. Client: " + client;
    }
}

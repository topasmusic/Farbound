package de.topas.farbound;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class FarboundNetwork {
    public static final Gson JSON = new Gson();
    private FarboundNetwork() {}
    public record Action(String action, String value, int revision) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath("farbound", "action_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), Action::action, ByteBufCodecs.stringUtf8(128), Action::value,
            ByteBufCodecs.VAR_INT, Action::revision, Action::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath("farbound", "state_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(262144), State::json, State::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static class View {
        public int revision, distance, countdown, remaining, homeRadius=100, cooldownSeconds;
        public boolean customDestination;
        public int customX, customZ;
        public boolean admin, canPause, homeReached, occupied;
        public String adminInfo="";
        public List<Online> online=new ArrayList<>();
        public List<Online> recovery=new ArrayList<>();
        public List<String> inspection=new ArrayList<>();
        public String status = "NONE", phase = "HOME", inventory = "STORE", notice = "";
        public boolean leader, invited, ready, worldSpawn, homeward, netherBlocked, endBlocked, enderStore = true, xpStore = true, rulesChanged, voted, open;
        public Boolean proposedNether, proposedEnd;
        public long elapsedTicks, homeDistance;
        public List<String> members = new ArrayList<>();
        public List<Person> people = new ArrayList<>();
        public static View finished() { return new View(); }
    }
    public record Person(UUID id, String name, boolean online, boolean leader, boolean ready, String phase) {}
    public record Online(UUID id,String name,boolean invited){}
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> {
            if (Farbound.service == null) return;
            if (payload.revision() < 0) { ExpeditionService.message(context.player(), "Invalid menu revision."); Farbound.service.sendView(context.player()); return; }
            long now = System.nanoTime();
            Long last = LAST_ACTION.get(context.player().getUUID());
            if (last != null && now - last < 80000000) {
                // Reconcile a rejected rapid click; it must not silently stall the UI
                // or move the acceptance window forward with every rejected packet.
                Farbound.service.sendView(context.player());
                return;
            }
            LAST_ACTION.put(context.player().getUUID(), now);
            Farbound.service.handle(context.player(), payload.action(), payload.value(), payload.revision());
        });
    }
    public static void send(ServerPlayer p, View view) {
        if (ServerPlayNetworking.canSend(p, State.TYPE)) ServerPlayNetworking.send(p, new State(JSON.toJson(view)));
        else if (view.open) {
            ExpeditionService.message(p, "Status: " + view.status + ", distance: " + view.distance + ". The dashboard requires Farbound on the client.");
            ExpeditionService.message(p, "Install Farbound on this client, then open /farbound. All expedition and operator actions are in the dashboard.");
        }
    }
}


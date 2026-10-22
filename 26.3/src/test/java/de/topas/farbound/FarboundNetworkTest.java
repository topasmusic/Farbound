package de.topas.farbound;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FarboundNetworkTest {
    @TempDir Path root;
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test @SuppressWarnings("unchecked")
    void throttledActionReturnsCurrentStateAndDoesNotExtendThrottle() throws Exception {
        var server=mock(MinecraftServer.class,RETURNS_DEEP_STUBS);when(server.getWorldPath(LevelResource.ROOT)).thenReturn(root);
        var player=mock(ServerPlayer.class);var id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);
        when(player.getPlainTextName()).thenReturn("Leader");when(server.getPlayerList().getPlayer(id)).thenReturn(player);
        var service=new ExpeditionService(server);service.store.expedition=new Expedition(id);
        var context=mock(ServerPlayNetworking.Context.class);when(context.player()).thenReturn(player);when(context.server()).thenReturn(server);
        var handlers=new ArrayList<ServerPlayNetworking.PlayPayloadHandler<FarboundNetwork.Action>>();
        var replies=new ArrayList<FarboundNetwork.View>();
        var old=Farbound.service;Farbound.service=service;
        var field=FarboundNetwork.class.getDeclaredField("LAST_ACTION");field.setAccessible(true);
        var last=(Map<UUID,Long>)field.get(null);
        try(var registry=mockStatic(PayloadTypeRegistry.class,RETURNS_DEEP_STUBS);var api=mockStatic(ServerPlayNetworking.class)) {
            api.when(()->ServerPlayNetworking.registerGlobalReceiver(eq(FarboundNetwork.Action.TYPE),any())).thenAnswer(c->{handlers.add(c.getArgument(1));return true;});
            api.when(()->ServerPlayNetworking.canSend(player,FarboundNetwork.State.TYPE)).thenReturn(true);
            api.when(()->ServerPlayNetworking.send(eq(player),any(FarboundNetwork.State.class))).thenAnswer(c->{replies.add(FarboundNetwork.JSON.fromJson(((FarboundNetwork.State)c.getArgument(1)).json(),FarboundNetwork.View.class));return null;});
            FarboundNetwork.register();assertEquals(1,handlers.size());var receiver=handlers.getFirst();
            // Force a closed rate-limit window deterministically, without sleeping.
            long blockedUntil=System.nanoTime()+1_000_000_000L;last.put(id,blockedUntil);
            receiver.receive(new FarboundNetwork.Action("configure","distance 50000",1),context);
            assertEquals(10000,service.expedition().distance);
            assertEquals(1,replies.size(),"A rejected click must resync the UI instead of disappearing silently");
            assertEquals(10000,replies.getFirst().distance);assertEquals(blockedUntil,last.get(id));
            last.put(id,System.nanoTime()-100_000_000L);replies.clear();
            receiver.receive(new FarboundNetwork.Action("configure","distance 50000",1),context);
            assertEquals(50000,service.expedition().distance);assertEquals(50000,replies.getFirst().distance);
        } finally {last.remove(id);Farbound.service=old;}
    }
}

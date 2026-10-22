package de.topas.farbound.mixin;

import de.topas.farbound.Farbound;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "respawn", at = @At("HEAD"), cancellable = true)
    private void farbound$holdUnsafeRespawn(ServerPlayer player, boolean alive, net.minecraft.world.entity.Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayer> cir) {
        if (Farbound.service != null && !Farbound.service.respawnReady(player)) cir.setReturnValue(player);
    }
    @Inject(method = "placeNewPlayer", at = @At("HEAD"), cancellable = true)
    private void farbound$checkBeforeRelease(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (Farbound.service == null) return;
        String problem = Farbound.service.loginProblem(player);
        if (problem != null) {
            Farbound.LOG.error("Farbound login blocked for {}: {}", player.getUUID(), problem);
            connection.disconnect(Component.literal("Farbound: Profile reconciliation required. Contact the server operator."));
            ci.cancel();
        }
    }
}


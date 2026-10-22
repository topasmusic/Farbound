package de.topas.farbound.mixin;
import de.topas.farbound.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements VaultCarrier {
    @Unique private CompoundTag farbound$stored = new CompoundTag();
    public CompoundTag farbound$vault() { return farbound$stored; }
    public void farbound$vault(CompoundTag value) { farbound$stored = value; }
    @Inject(method="readAdditionalSaveData", at=@At("TAIL"))
    private void farbound$load(ValueInput input, CallbackInfo ci) {
        farbound$stored = input.read(PlayerVault.KEY, CompoundTag.CODEC).orElseGet(CompoundTag::new);
    }
    @Inject(method="addAdditionalSaveData", at=@At("TAIL"))
    private void farbound$save(ValueOutput output, CallbackInfo ci) {
        if (!farbound$stored.isEmpty()) output.store(PlayerVault.KEY, CompoundTag.CODEC, farbound$stored);
    }
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at=@At("HEAD"), cancellable=true)
    private void farbound$portal(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        if (Farbound.service != null && !Farbound.service.dimensionAllowed((ServerPlayer)(Object)this, transition.newLevel())) cir.setReturnValue(null);
    }
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at=@At("HEAD"), cancellable=true)
    private void farbound$teleport(ServerLevel level, double x, double y, double z, Set<Relative> relatives, float yaw, float pitch, boolean camera, CallbackInfoReturnable<Boolean> cir) {
        if (Farbound.service != null && !Farbound.service.dimensionAllowed((ServerPlayer)(Object)this, level)) cir.setReturnValue(false);
    }
    @Inject(method="findRespawnPositionAndUseSpawnBlock", at=@At("RETURN"), cancellable=true)
    private void farbound$respawn(boolean alive, TeleportTransition.PostTeleportTransition action, CallbackInfoReturnable<TeleportTransition> cir) {
        if (Farbound.service != null) cir.setReturnValue(Farbound.service.respawn((ServerPlayer)(Object)this, cir.getReturnValue(), action));
    }
}


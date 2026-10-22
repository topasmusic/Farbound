package de.topas.farbound.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccess {
    @Accessor("lastDamageStamp") long farbound$lastDamageStamp();
    @Accessor("lastDamageSource") DamageSource farbound$lastDamageSource();
}

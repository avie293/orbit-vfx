package cc.avie.orbitvfx.mixin;

import cc.avie.orbitvfx.particle.WaterEffects;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "doWaterSplashEffect", at = @At("TAIL"))
    private void orbitvfx$addSplash(CallbackInfo ci) {
        WaterEffects.onSplash((Entity) (Object) this);
    }
}

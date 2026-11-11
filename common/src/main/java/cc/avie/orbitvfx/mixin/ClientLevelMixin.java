package cc.avie.orbitvfx.mixin;

import cc.avie.orbitvfx.particle.FireEffects;
import cc.avie.orbitvfx.render.BlockAnimations;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"))
    private void orbitvfx$animateDestroyedBlock(BlockPos pos, BlockState blockState, CallbackInfo ci) {
        BlockAnimations.onBlockDestroyed((ClientLevel) (Object) this, pos, blockState);
    }

    @Inject(method = "doAnimateTick", at = @At("TAIL"))
    private void orbitvfx$addAmbientEffects(int xt, int yt, int zt, int r, RandomSource animateRandom,
            @Nullable Block markerParticleTarget, BlockPos.MutableBlockPos pos, CallbackInfo ci,
            @Local BlockState state, @Local FluidState fluidState) {
        FireEffects.animateTick((ClientLevel) (Object) this, pos, state, fluidState, animateRandom);
    }
}

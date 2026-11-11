package cc.avie.orbitvfx.mixin;

import cc.avie.orbitvfx.render.BlockAnimations;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
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
}

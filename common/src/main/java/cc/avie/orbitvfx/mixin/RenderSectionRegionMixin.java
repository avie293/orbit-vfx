package cc.avie.orbitvfx.mixin;

import cc.avie.orbitvfx.render.BlockAnimations;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderSectionRegion.class)
abstract class RenderSectionRegionMixin {
    // Hiding the block from the whole region, not just from the model pass, keeps neighbouring faces from being
    // culled against a block that is not drawn yet.
    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState orbitvfx$hideAnimatedBlock(BlockState original, BlockPos pos) {
        return BlockAnimations.filterChunkBlock(pos, original);
    }
}

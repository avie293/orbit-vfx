package cc.avie.orbitvfx.mixin;

import cc.avie.orbitvfx.render.BlockAnimations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @WrapOperation(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"
        )
    )
    private boolean orbitvfx$animatePlacement(BlockItem item, BlockPlaceContext context, BlockState state,
            Operation<Boolean> original) {
        if (!(context.getLevel() instanceof ClientLevel level)
                || !BlockAnimations.beginPlacement(level, context.getClickedPos())) {
            return original.call(item, context, state);
        }
        boolean placed = original.call(item, context, state);
        BlockAnimations.endPlacement(level, context.getClickedPos(), context.getClickedFace(), placed);
        return placed;
    }
}

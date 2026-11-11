package cc.avie.orbitvfx.mixin;

import cc.avie.orbitvfx.render.AtmosphereRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    @Final
    private LevelTargetBundle targets;

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    // Right after the main pass the frame graph holds the finished world (terrain, entities, particles, weather),
    // but not the hand or the GUI.
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V",
            shift = At.Shift.AFTER
        )
    )
    private void orbitvfx$addAtmospherePass(GraphicsResourceAllocator resourceAllocator, boolean renderOutline,
            CameraRenderState cameraState, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky,
            boolean consistentDepthRequired, CallbackInfo ci, @Local FrameGraphBuilder frame) {
        AtmosphereRenderer.addPass(frame, this.targets, cameraState, terrainFog, this.levelRenderState);
    }
}

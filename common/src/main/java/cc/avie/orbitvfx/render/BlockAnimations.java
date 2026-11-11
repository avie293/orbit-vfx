package cc.avie.orbitvfx.render;

import cc.avie.orbitvfx.config.OrbitConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Short scale animations for blocks that are broken. The animated block is drawn through the same moving block
 * path vanilla uses for pistons, so it is lit and tinted like the real block.
 */
public final class BlockAnimations {
    private static final int MAX_ANIMATIONS = 64;
    private static final long BREAK_DURATION_NS = 150_000_000L;

    private static final ObjectArrayList<Animation> ANIMATIONS = new ObjectArrayList<>();
    private static @Nullable ClientLevel currentLevel;

    private BlockAnimations() {
    }

    public static void tick(Minecraft client) {
        if (client.level != currentLevel) {
            currentLevel = client.level;
            ANIMATIONS.clear();
        }
    }

    /**
     * Called for every block destroy effect, including blocks broken by other players and mobs.
     */
    public static void onBlockDestroyed(ClientLevel level, BlockPos pos, BlockState state) {
        if (!OrbitConfig.enabled || !OrbitConfig.breakAnimation || level != currentLevel
                || state.getRenderShape() != RenderShape.MODEL || ANIMATIONS.size() >= MAX_ANIMATIONS) {
            return;
        }
        ANIMATIONS.add(new Animation(level, pos, state, Util.getNanos(), scaledDuration(BREAK_DURATION_NS)));
    }

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
        if (ANIMATIONS.isEmpty()) {
            return;
        }
        ClientLevel level = currentLevel;
        long now = Util.getNanos();
        for (int i = ANIMATIONS.size() - 1; i >= 0; i--) {
            Animation animation = ANIMATIONS.get(i);
            float progress = (float) (now - animation.startNs) / animation.durationNs;
            if (progress >= 1.0F || level == null) {
                ANIMATIONS.remove(i);
                continue;
            }
            // The destroy effect fires just before the block is removed, and also when a sheep eats grass. Only
            // draw once the position is really empty.
            BlockState current = level.getBlockState(animation.state.blockPos);
            if (!current.isAir() && !(current.getBlock() instanceof LiquidBlock)) {
                continue;
            }

            float scale = 1.0F - progress * progress * 0.9F;
            BlockPos pos = animation.state.blockPos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - cameraPos.x + 0.5, pos.getY() - cameraPos.y + 0.5,
                pos.getZ() - cameraPos.z + 0.5);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            collector.submitMovingBlock(poseStack, animation.state, 0);
            poseStack.popPose();
        }
    }

    private static long scaledDuration(long durationNs) {
        return (long) (durationNs / Mth.clamp(OrbitConfig.animationSpeed, 0.25F, 4.0F));
    }

    private static final class Animation {
        private final MovingBlockRenderState state = new MovingBlockRenderState();
        private final long startNs;
        private final long durationNs;

        private Animation(ClientLevel level, BlockPos pos, BlockState blockState, long startNs, long durationNs) {
            BlockPos immutablePos = pos.immutable();
            this.state.randomSeedPos = immutablePos;
            this.state.blockPos = immutablePos;
            this.state.blockState = blockState;
            this.state.biome = level.getBiome(immutablePos);
            this.state.cardinalLighting = level.cardinalLighting();
            this.state.lightEngine = level.getLightEngine();
            this.startNs = startNs;
            this.durationNs = durationNs;
        }
    }
}

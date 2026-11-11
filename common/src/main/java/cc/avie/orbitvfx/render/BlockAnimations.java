package cc.avie.orbitvfx.render;

import cc.avie.orbitvfx.config.OrbitConfig;
import cc.avie.orbitvfx.platform.Services;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Short scale animations for blocks that are placed or broken. The animated block is drawn through the same moving
 * block path vanilla uses for pistons, so it is lit and tinted like the real block.
 *
 * <p>A placed block is left out of the chunk mesh while it animates. Once the animation is over the block is put
 * back, and the animated copy stays until a section rebuild that started after that point has finished, the same
 * way vanilla hands transient blocks over to the chunk mesh. That avoids both a doubled block and a frame without
 * any block.
 */
public final class BlockAnimations {
    private static final int MAX_ANIMATIONS = 64;
    private static final long PLACE_DURATION_NS = 180_000_000L;
    private static final long BREAK_DURATION_NS = 150_000_000L;
    private static final long HANDOFF_TIMEOUT_NS = 500_000_000L;
    private static final long NOT_HANDED_OFF = Long.MAX_VALUE;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    // Sodium builds chunk meshes without the vanilla section compiler, so placed blocks can not be hidden there.
    private static final boolean CAN_HIDE_BLOCKS = !Services.PLATFORM.isModLoaded("sodium");

    private static final ObjectArrayList<Animation> ANIMATIONS = new ObjectArrayList<>();
    private static @Nullable ClientLevel currentLevel;

    // Read by the chunk compile threads. Both arrays are tiny and replaced as a whole whenever they change.
    private static volatile long[] hiddenBlocks = new long[0];
    private static volatile Animation[] handoffs = new Animation[0];

    private BlockAnimations() {
    }

    public static void tick(Minecraft client) {
        if (client.level != currentLevel) {
            currentLevel = client.level;
            ANIMATIONS.clear();
            hiddenBlocks = new long[0];
            handoffs = new Animation[0];
        }
    }

    /**
     * Called right before a block item places its block on the client. Hides the position from the chunk mesh, so
     * the section rebuild caused by the placement already leaves the block out.
     *
     * @return {@code true} if {@link #endPlacement} has to be called afterwards
     */
    public static boolean beginPlacement(ClientLevel level, BlockPos pos) {
        if (!OrbitConfig.enabled || !OrbitConfig.placeAnimation || !CAN_HIDE_BLOCKS || level != currentLevel
                || ANIMATIONS.size() >= MAX_ANIMATIONS) {
            return false;
        }
        hide(pos.asLong());
        return true;
    }

    public static void endPlacement(ClientLevel level, BlockPos pos, Direction clickedFace, boolean placed) {
        BlockState state = level.getBlockState(pos);
        // Blocks with a second half or a block entity renderer would only animate partly.
        if (!placed || state.getRenderShape() != RenderShape.MODEL || state.hasBlockEntity()
                || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            unhide(level, pos, state);
            return;
        }
        // Grow out of the face the block was placed against.
        Direction support = clickedFace.getOpposite();
        ANIMATIONS.add(new Animation(level, pos, state, true, Util.getNanos(), scaledDuration(PLACE_DURATION_NS),
            0.5F + support.getStepX() * 0.5F, 0.5F + support.getStepY() * 0.5F, 0.5F + support.getStepZ() * 0.5F));
    }

    /**
     * Called for every block destroy effect, including blocks broken by other players and mobs.
     */
    public static void onBlockDestroyed(ClientLevel level, BlockPos pos, BlockState state) {
        if (!OrbitConfig.enabled || !OrbitConfig.breakAnimation || level != currentLevel
                || state.getRenderShape() != RenderShape.MODEL || ANIMATIONS.size() >= MAX_ANIMATIONS) {
            return;
        }
        ANIMATIONS.add(new Animation(level, pos, state, false, Util.getNanos(), scaledDuration(BREAK_DURATION_NS),
            0.5F, 0.5F, 0.5F));
    }

    /**
     * Lets the chunk compiler see a hidden block as air. Called from any compile thread.
     */
    public static BlockState filterChunkBlock(BlockPos pos, BlockState state) {
        long[] hidden = hiddenBlocks;
        if (hidden.length == 0) {
            return state;
        }
        long key = pos.asLong();
        for (long hiddenPos : hidden) {
            if (hiddenPos == key) {
                return AIR;
            }
        }
        return state;
    }

    /**
     * Called whenever a section has been rebuilt, from any thread.
     */
    public static void onSectionCompiled(long sectionNode, long compileStartNs) {
        for (Animation animation : handoffs) {
            if (animation.sectionNode == sectionNode && compileStartNs > animation.handoffNs) {
                animation.replaced = true;
            }
        }
    }

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
        if (ANIMATIONS.isEmpty()) {
            return;
        }
        ClientLevel level = currentLevel;
        long now = Util.getNanos();
        for (int i = ANIMATIONS.size() - 1; i >= 0; i--) {
            Animation animation = ANIMATIONS.get(i);
            float scale = level == null ? -1.0F
                : animation.placement ? placementScale(level, animation, now) : breakScale(level, animation, now);
            if (scale < 0.0F) {
                ANIMATIONS.remove(i);
                continue;
            }
            if (scale == 0.0F) {
                continue;
            }

            BlockPos pos = animation.state.blockPos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - cameraPos.x + animation.pivotX,
                pos.getY() - cameraPos.y + animation.pivotY, pos.getZ() - cameraPos.z + animation.pivotZ);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-animation.pivotX, -animation.pivotY, -animation.pivotZ);
            collector.submitMovingBlock(poseStack, animation.state, 0);
            poseStack.popPose();
        }
    }

    /**
     * @return the scale to draw with, {@code 0} to skip this frame or a negative value once the animation is over
     */
    private static float placementScale(ClientLevel level, Animation animation, long now) {
        BlockPos pos = animation.state.blockPos;
        BlockState current = level.getBlockState(pos);
        if (current.getBlock() != animation.state.blockState.getBlock()) {
            // Broken again, or the server rejected the placement.
            if (animation.handoffNs == NOT_HANDED_OFF) {
                unhide(level, pos, current);
            } else {
                removeHandoff(animation);
            }
            return -1.0F;
        }
        // Neighbour updates such as fence connections change the state while the block animates.
        animation.state.blockState = current;

        float progress = (float) (now - animation.startNs) / animation.durationNs;
        if (animation.handoffNs == NOT_HANDED_OFF) {
            if (progress < 1.0F) {
                return 0.6F + 0.4F * easeOutBack(progress);
            }
            animation.handoffNs = now;
            addHandoff(animation);
            unhide(level, pos, current);
        } else if (animation.replaced || now - animation.handoffNs > HANDOFF_TIMEOUT_NS) {
            removeHandoff(animation);
            return -1.0F;
        }
        return 1.0F;
    }

    private static float breakScale(ClientLevel level, Animation animation, long now) {
        float progress = (float) (now - animation.startNs) / animation.durationNs;
        if (progress >= 1.0F) {
            return -1.0F;
        }
        // The destroy effect fires just before the block is removed, and also when a sheep eats grass. Only
        // draw once the position is really empty.
        BlockState current = level.getBlockState(animation.state.blockPos);
        if (!current.isAir() && !(current.getBlock() instanceof LiquidBlock)) {
            return 0.0F;
        }
        return 1.0F - progress * progress * 0.9F;
    }

    private static float easeOutBack(float t) {
        float x = t - 1.0F;
        return 1.0F + 2.3F * x * x * x + 1.3F * x * x;
    }

    private static long scaledDuration(long durationNs) {
        return (long) (durationNs / Mth.clamp(OrbitConfig.animationSpeed, 0.25F, 4.0F));
    }

    private static void hide(long pos) {
        long[] hidden = hiddenBlocks;
        long[] updated = Arrays.copyOf(hidden, hidden.length + 1);
        updated[hidden.length] = pos;
        hiddenBlocks = updated;
    }

    private static void unhide(ClientLevel level, BlockPos pos, BlockState state) {
        long key = pos.asLong();
        long[] hidden = hiddenBlocks;
        for (int i = 0; i < hidden.length; i++) {
            if (hidden[i] == key) {
                long[] updated = new long[hidden.length - 1];
                System.arraycopy(hidden, 0, updated, 0, i);
                System.arraycopy(hidden, i + 1, updated, i, hidden.length - i - 1);
                hiddenBlocks = updated;
                break;
            }
        }
        // Immediate, so the section and its neighbours are rebuilt right away instead of on a worker thread.
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
    }

    private static void addHandoff(Animation animation) {
        Animation[] pending = handoffs;
        Animation[] updated = Arrays.copyOf(pending, pending.length + 1);
        updated[pending.length] = animation;
        handoffs = updated;
    }

    private static void removeHandoff(Animation animation) {
        Animation[] pending = handoffs;
        for (int i = 0; i < pending.length; i++) {
            if (pending[i] == animation) {
                Animation[] updated = new Animation[pending.length - 1];
                System.arraycopy(pending, 0, updated, 0, i);
                System.arraycopy(pending, i + 1, updated, i, pending.length - i - 1);
                handoffs = updated;
                return;
            }
        }
    }

    private static final class Animation {
        private final MovingBlockRenderState state = new MovingBlockRenderState();
        private final boolean placement;
        private final long startNs;
        private final long durationNs;
        private final float pivotX;
        private final float pivotY;
        private final float pivotZ;
        private final long sectionNode;
        private volatile long handoffNs = NOT_HANDED_OFF;
        private volatile boolean replaced;

        private Animation(ClientLevel level, BlockPos pos, BlockState blockState, boolean placement, long startNs,
                long durationNs, float pivotX, float pivotY, float pivotZ) {
            BlockPos immutablePos = pos.immutable();
            this.state.randomSeedPos = immutablePos;
            this.state.blockPos = immutablePos;
            this.state.blockState = blockState;
            this.state.biome = level.getBiome(immutablePos);
            this.state.cardinalLighting = level.cardinalLighting();
            this.state.lightEngine = level.getLightEngine();
            this.placement = placement;
            this.startNs = startNs;
            this.durationNs = durationNs;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.sectionNode = SectionPos.blockToSection(immutablePos.asLong());
        }
    }
}

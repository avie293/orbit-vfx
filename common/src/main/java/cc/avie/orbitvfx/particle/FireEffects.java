package cc.avie.orbitvfx.particle;

import cc.avie.orbitvfx.config.OrbitConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Adds rising embers to the vanilla ambient effects of fire, torches, campfires and lava. Hooked into the random
 * block display ticks of the client level, so the cost scales with the same budget vanilla uses for its own
 * ambient particles.
 */
public final class FireEffects {
    private static final Identifier EMBER_SPRITE = Identifier.withDefaultNamespace("generic_0");

    private FireEffects() {
    }

    public static void animateTick(ClientLevel level, BlockPos pos, BlockState state, FluidState fluid,
            RandomSource random) {
        if (!OrbitConfig.enabled || !OrbitConfig.embers) {
            return;
        }
        ParticleStatus particles = Minecraft.getInstance().options.particles().get();
        if (particles == ParticleStatus.MINIMAL || particles == ParticleStatus.DECREASED && random.nextBoolean()) {
            return;
        }

        Block block = state.getBlock();
        // Each block only gets a display tick every second or so, hence several embers per tick.
        if (block instanceof TorchBlock) {
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                torchEmber(level, pos, state, random);
            }
        } else if (block instanceof BaseFireBlock) {
            int count = 4 + random.nextInt(4);
            for (int i = 0; i < count; i++) {
                spawn(level, pos.getX() + random.nextDouble(), pos.getY() + 0.1 + random.nextDouble() * 0.6,
                    pos.getZ() + random.nextDouble(), 0.05, random, block == Blocks.SOUL_FIRE);
            }
        } else if (block instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            int count = 3 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                spawn(level, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.08, random, block == Blocks.SOUL_CAMPFIRE);
            }
        } else if (fluid.is(FluidTags.LAVA) && random.nextInt(3) == 0 && level.getBlockState(pos.above()).isAir()) {
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                spawn(level, pos.getX() + random.nextDouble(), pos.getY() + fluid.getOwnHeight(),
                    pos.getZ() + random.nextDouble(), 0.06, random, false);
            }
        }
    }

    private static void torchEmber(ClientLevel level, BlockPos pos, BlockState state, RandomSource random) {
        // Same flame position as the vanilla torch particles.
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.08;
        double y = pos.getY() + 0.7;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.08;
        if (state.getBlock() instanceof WallTorchBlock) {
            Direction opposite = state.getValue(WallTorchBlock.FACING).getOpposite();
            x += 0.27 * opposite.getStepX();
            y += 0.22;
            z += 0.27 * opposite.getStepZ();
        }
        Block block = state.getBlock();
        spawn(level, x, y, z, 0.03, random, block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH);
    }

    private static void spawn(ClientLevel level, double x, double y, double z, double speed, RandomSource random,
            boolean soul) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
            .getAtlasOrThrow(AtlasIds.PARTICLES)
            .getSprite(EMBER_SPRITE);
        double xd = (random.nextDouble() - 0.5) * 0.02;
        double yd = speed * (0.6 + random.nextDouble() * 0.8);
        double zd = (random.nextDouble() - 0.5) * 0.02;
        Minecraft.getInstance().particleEngine.add(new EmberParticle(level, x, y, z, xd, yd, zd, sprite, soul));
    }
}

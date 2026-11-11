package cc.avie.orbitvfx.particle;

import cc.avie.orbitvfx.config.OrbitConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Rain ripples on open water and a stronger splash when something falls into water.
 */
public final class WaterEffects {
    private static final Identifier[] RIPPLE_SPRITES = {
        Identifier.withDefaultNamespace("bubble_pop_0"),
        Identifier.withDefaultNamespace("bubble_pop_1"),
        Identifier.withDefaultNamespace("bubble_pop_2"),
        Identifier.withDefaultNamespace("bubble_pop_3"),
        Identifier.withDefaultNamespace("bubble_pop_4")
    };
    private static final int RAIN_RADIUS = 16;
    private static final int RAIN_ATTEMPTS = 40;

    private static final BlockPos.MutableBlockPos SAMPLE_POS = new BlockPos.MutableBlockPos();
    private static final TextureAtlasSprite[] RIPPLE_FRAMES = new TextureAtlasSprite[RIPPLE_SPRITES.length];
    private static final RandomSource RANDOM = RandomSource.create();

    private WaterEffects() {
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        Entity camera = client.getCameraEntity();
        if (!OrbitConfig.enabled || !OrbitConfig.rainRipples || level == null || camera == null || client.isPaused()) {
            return;
        }
        float rain = level.getRainLevel(1.0F);
        int attempts = (int) (RAIN_ATTEMPTS * rain * particleFactor(client));
        if (attempts == 0) {
            return;
        }

        TextureAtlasSprite[] frames = rippleFrames(client);
        int seaLevel = level.getSeaLevel();
        for (int i = 0; i < attempts; i++) {
            int x = camera.getBlockX() + RANDOM.nextInt(RAIN_RADIUS * 2 + 1) - RAIN_RADIUS;
            int z = camera.getBlockZ() + RANDOM.nextInt(RAIN_RADIUS * 2 + 1) - RAIN_RADIUS;
            // Fluids block motion, so the motion blocking height map ends right above a water surface.
            SAMPLE_POS.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
            FluidState fluid = level.getFluidState(SAMPLE_POS);
            if (!fluid.is(FluidTags.WATER)) {
                continue;
            }
            Biome biome = level.getBiome(SAMPLE_POS).value();
            if (biome.getPrecipitationAt(SAMPLE_POS, seaLevel) != Biome.Precipitation.RAIN) {
                continue;
            }
            double y = SAMPLE_POS.getY() + fluid.getHeight(level, SAMPLE_POS) + 0.01;
            client.particleEngine.add(new RippleParticle(level, x + RANDOM.nextDouble(), y, z + RANDOM.nextDouble(),
                frames, 0.25F + RANDOM.nextFloat() * 0.2F, 8 + RANDOM.nextInt(5), 0.55F));
        }
    }

    /**
     * Called after the vanilla splash of an entity that just entered water.
     */
    public static void onSplash(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (!OrbitConfig.enabled || !OrbitConfig.splashes || !(entity.level() instanceof ClientLevel level)
                || particleFactor(client) == 0.0F) {
            return;
        }
        Vec3 movement = entity.getDeltaMovement();
        float speed = Math.min(1.0F, (float) movement.length());
        float width = entity.getBbWidth();

        SAMPLE_POS.set(entity.getBlockX(), Mth.floor(entity.getY()), entity.getBlockZ());
        FluidState fluid = level.getFluidState(SAMPLE_POS);
        double surface = fluid.is(FluidTags.WATER)
            ? SAMPLE_POS.getY() + fluid.getHeight(level, SAMPLE_POS) + 0.02
            : SAMPLE_POS.getY() + 1.02;

        // Two rings that widen at different rates read as one wave front followed by a smaller one.
        TextureAtlasSprite[] frames = rippleFrames(client);
        float size = 1.0F + width * 1.5F + speed * 2.0F;
        client.particleEngine.add(new RippleParticle(level, entity.getX(), surface, entity.getZ(), frames, size,
            14 + (int) (speed * 10.0F), 0.8F));
        client.particleEngine.add(new RippleParticle(level, entity.getX(), surface, entity.getZ(), frames, size * 0.6F,
            20 + (int) (speed * 10.0F), 0.6F));

        // A crown of droplets thrown outwards, on top of the vanilla splash that mostly stays in place.
        int droplets = (int) ((4 + width * 10.0F + speed * 30.0F) * particleFactor(client));
        for (int i = 0; i < droplets; i++) {
            float angle = RANDOM.nextFloat() * Mth.TWO_PI;
            double strength = 0.06 + RANDOM.nextDouble() * (0.08 + speed * 0.2);
            double radius = width * 0.5 + RANDOM.nextDouble() * 0.2;
            level.addParticle(ParticleTypes.SPLASH, entity.getX() + Mth.cos(angle) * radius, surface,
                entity.getZ() + Mth.sin(angle) * radius, Mth.cos(angle) * strength, 0.0, Mth.sin(angle) * strength);
        }
    }

    private static float particleFactor(Minecraft client) {
        ParticleStatus particles = client.options.particles().get();
        return particles == ParticleStatus.ALL ? 1.0F : particles == ParticleStatus.DECREASED ? 0.5F : 0.0F;
    }

    private static TextureAtlasSprite[] rippleFrames(Minecraft client) {
        TextureAtlas atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
        TextureAtlasSprite first = atlas.getSprite(RIPPLE_SPRITES[0]);
        // Sprites are replaced on resource reloads, the first one tells whether the cache is still valid.
        if (RIPPLE_FRAMES[0] != first) {
            for (int i = 0; i < RIPPLE_SPRITES.length; i++) {
                RIPPLE_FRAMES[i] = atlas.getSprite(RIPPLE_SPRITES[i]);
            }
        }
        return RIPPLE_FRAMES;
    }
}

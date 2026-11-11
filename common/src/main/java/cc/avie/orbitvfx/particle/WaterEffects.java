package cc.avie.orbitvfx.particle;

import cc.avie.orbitvfx.config.OrbitConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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
 * Rain ripples on open water and splashes when something falls into water.
 */
public final class WaterEffects {
    private static final SpriteSet RIPPLE = new SpriteSet("bubble_pop_0", "bubble_pop_1", "bubble_pop_2",
        "bubble_pop_3", "bubble_pop_4");
    private static final SpriteSet DROPLET = new SpriteSet("splash_0", "splash_1", "splash_2", "splash_3");
    private static final int RAIN_RADIUS = 16;
    private static final int RAIN_ATTEMPTS = 40;

    private static final BlockPos.MutableBlockPos SAMPLE_POS = new BlockPos.MutableBlockPos();
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
            spawnRipple(level, x + RANDOM.nextDouble(), y, z + RANDOM.nextDouble(), 0.25F + RANDOM.nextFloat() * 0.2F,
                8 + RANDOM.nextInt(5), 0.55F);
        }
    }

    /**
     * Called after the vanilla splash of an entity that just entered water.
     */
    public static void onSplash(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        float factor = particleFactor(client);
        if (!OrbitConfig.enabled || !OrbitConfig.splashes || !(entity.level() instanceof ClientLevel level)
                || factor == 0.0F) {
            return;
        }
        // Like the vanilla splash sound, the impact is mostly decided by how fast the entity falls.
        Vec3 movement = entity.getDeltaMovement();
        float impact = Math.min(1.0F, (float) Math.sqrt(movement.x * movement.x * 0.2 + movement.y * movement.y
            + movement.z * movement.z * 0.2) / 1.2F);
        float width = entity.getBbWidth();
        double x = entity.getX();
        double z = entity.getZ();

        SAMPLE_POS.set(entity.getBlockX(), Mth.floor(entity.getY()), entity.getBlockZ());
        FluidState fluid = level.getFluidState(SAMPLE_POS);
        double surface = fluid.is(FluidTags.WATER)
            ? SAMPLE_POS.getY() + fluid.getHeight(level, SAMPLE_POS) + 0.02
            : SAMPLE_POS.getY() + 1.02;

        // Two rings that widen at different rates read as one wave front followed by a smaller one.
        float size = 1.0F + width * 1.5F + impact * 2.5F;
        spawnRipple(level, x, surface, z, size, 14 + (int) (impact * 12.0F), 0.8F);
        spawnRipple(level, x, surface, z, size * 0.6F, 20 + (int) (impact * 12.0F), 0.6F);

        // A crown of drops thrown outwards from the edge of the entity.
        int crown = (int) ((6.0F + width * 12.0F + impact * 40.0F) * factor);
        for (int i = 0; i < crown; i++) {
            float angle = RANDOM.nextFloat() * Mth.TWO_PI;
            double radius = width * 0.5 + RANDOM.nextDouble() * 0.15;
            double outwards = 0.04 + RANDOM.nextDouble() * (0.06 + impact * 0.12);
            double up = 0.08 + RANDOM.nextDouble() * (0.1 + impact * 0.3);
            spawnDroplet(level, x + Mth.cos(angle) * radius, surface, z + Mth.sin(angle) * radius,
                Mth.cos(angle) * outwards, up, Mth.sin(angle) * outwards);
        }

        // Hard landings also throw up a narrow column in the middle.
        if (impact > 0.35F) {
            int column = (int) (impact * 16.0F * factor);
            for (int i = 0; i < column; i++) {
                double spread = width * 0.25;
                spawnDroplet(level, x + (RANDOM.nextDouble() - 0.5) * spread, surface,
                    z + (RANDOM.nextDouble() - 0.5) * spread, (RANDOM.nextDouble() - 0.5) * 0.04,
                    0.25 + RANDOM.nextDouble() * impact * 0.45, (RANDOM.nextDouble() - 0.5) * 0.04);
            }
        }
    }

    static void spawnRipple(ClientLevel level, double x, double y, double z, float size, int lifetime, float alpha) {
        Minecraft.getInstance().particleEngine.add(new RippleParticle(level, x, y, z, RIPPLE.all(), size, lifetime,
            alpha));
    }

    static void spawnDroplet(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        // Only some drops leave a ring when they land, otherwise a big splash would turn into a carpet of rings.
        Minecraft.getInstance().particleEngine.add(new DropletParticle(level, x, y, z, xd, yd, zd,
            DROPLET.random(RANDOM), RANDOM.nextInt(4) == 0));
    }

    static float particleFactor(Minecraft client) {
        ParticleStatus particles = client.options.particles().get();
        return particles == ParticleStatus.ALL ? 1.0F : particles == ParticleStatus.DECREASED ? 0.5F : 0.0F;
    }
}

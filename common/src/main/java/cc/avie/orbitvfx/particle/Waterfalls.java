package cc.avie.orbitvfx.particle;

import cc.avie.orbitvfx.config.OrbitConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Finds the spots where falling water lands in water and keeps spray, drops and mist coming out of them.
 *
 * <p>The search walks over the chunk sections around the camera, a few per tick. The block palette of a section
 * tells whether it can contain falling water at all, so only the rare sections that do are looked at block by
 * block. Found waterfalls are kept in a small fixed size list and checked again every second.
 */
public final class Waterfalls {
    // The light generic puffs, the campfire smoke sprites read as grey even when tinted white.
    private static final SpriteSet SPRAY = new SpriteSet("generic_0", "generic_1", "generic_2", "generic_3");
    private static final Predicate<BlockState> FALLING_WATER = Waterfalls::isFallingWater;
    private static final int MAX_WATERFALLS = 48;
    private static final int SECTION_RADIUS = 3;
    private static final int SECTION_HEIGHT = 2;
    private static final int SCAN_WIDTH = SECTION_RADIUS * 2 + 1;
    private static final int SCAN_SECTIONS = SCAN_WIDTH * SCAN_WIDTH * (SECTION_HEIGHT * 2 + 1);
    private static final int SECTIONS_PER_TICK = 6;
    private static final int MAX_COLUMN = 16;
    private static final double MAX_DISTANCE_SQR = 64.0 * 64.0;
    private static final double EMIT_DISTANCE_SQR = 48.0 * 48.0;
    private static final double NEAR_DISTANCE_SQR = 24.0 * 24.0;

    private static final long[] LANDINGS = new long[MAX_WATERFALLS];
    private static final int[] HEIGHTS = new int[MAX_WATERFALLS];
    private static final boolean[] WARM_OCEAN = new boolean[MAX_WATERFALLS];
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    private static int count;
    private static int scanCursor;
    private static int ticks;
    private static @Nullable ClientLevel currentLevel;

    private Waterfalls() {
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level != currentLevel) {
            currentLevel = level;
            count = 0;
        }
        Entity camera = client.getCameraEntity();
        float factor = WaterEffects.particleFactor(client);
        if (!OrbitConfig.enabled || !OrbitConfig.waterfalls || level == null || camera == null || client.isPaused()
                || factor == 0.0F) {
            return;
        }
        ticks++;
        scan(level, camera);
        emit(level, camera, factor);
    }

    private static void scan(ClientLevel level, Entity camera) {
        int cameraX = SectionPos.blockToSectionCoord(camera.getBlockX());
        int cameraY = SectionPos.blockToSectionCoord(camera.getBlockY());
        int cameraZ = SectionPos.blockToSectionCoord(camera.getBlockZ());
        for (int i = 0; i < SECTIONS_PER_TICK; i++) {
            int index = scanCursor;
            scanCursor = (scanCursor + 1) % SCAN_SECTIONS;
            int sectionX = cameraX + index % SCAN_WIDTH - SECTION_RADIUS;
            int sectionZ = cameraZ + index / SCAN_WIDTH % SCAN_WIDTH - SECTION_RADIUS;
            int sectionY = cameraY + index / (SCAN_WIDTH * SCAN_WIDTH) - SECTION_HEIGHT;
            int sectionIndex = level.getSectionIndexFromSectionY(sectionY);
            if (sectionIndex < 0 || sectionIndex >= level.getSectionsCount()) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(sectionIndex);
            if (section.hasOnlyAir() || !section.getStates().maybeHas(FALLING_WATER)) {
                continue;
            }
            scanSection(level, section, SectionPos.sectionToBlockCoord(sectionX),
                SectionPos.sectionToBlockCoord(sectionY), SectionPos.sectionToBlockCoord(sectionZ));
        }
    }

    private static void scanSection(ClientLevel level, LevelChunkSection section, int minX, int minY, int minZ) {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (isFallingWater(section.getBlockState(x, y, z))) {
                        tryRegister(level, minX + x, minY + y, minZ + z);
                    }
                }
            }
        }
    }

    /**
     * Registers the landing spot below a falling water block, if that block is the bottom of its column.
     */
    private static void tryRegister(ClientLevel level, int x, int y, int z) {
        FluidState below = level.getFluidState(POS.set(x, y - 1, z));
        if (!below.is(FluidTags.WATER) || isFalling(below)) {
            return;
        }
        long landing = BlockPos.asLong(x, y - 1, z);
        for (int i = 0; i < count; i++) {
            if (LANDINGS[i] == landing) {
                return;
            }
        }
        if (count == MAX_WATERFALLS) {
            return;
        }
        int height = 1;
        while (height < MAX_COLUMN && isFalling(level.getFluidState(POS.set(x, y + height, z)))) {
            height++;
        }
        LANDINGS[count] = landing;
        HEIGHTS[count] = height;
        WARM_OCEAN[count] = WaterEffects.isWarmOcean(level.getBiome(POS.set(landing)));
        count++;
    }

    private static void emit(ClientLevel level, Entity camera, float factor) {
        for (int i = count - 1; i >= 0; i--) {
            POS.set(LANDINGS[i]);
            double x = POS.getX() + 0.5;
            double z = POS.getZ() + 0.5;
            double distanceSqr = camera.distanceToSqr(x, POS.getY(), z);
            // Each waterfall is checked again once a second, spread over the ticks.
            if ((ticks + i) % 20 == 0 && (distanceSqr > MAX_DISTANCE_SQR || !isStillWaterfall(level))) {
                count--;
                LANDINGS[i] = LANDINGS[count];
                HEIGHTS[i] = HEIGHTS[count];
                WARM_OCEAN[i] = WARM_OCEAN[count];
                continue;
            }
            if (distanceSqr > EMIT_DISTANCE_SQR || distanceSqr > NEAR_DISTANCE_SQR && (ticks & 1) == 0) {
                continue;
            }
            FluidState fluid = level.getFluidState(POS);
            double surface = POS.getY() + fluid.getHeight(level, POS);
            boolean glow = WARM_OCEAN[i] && OrbitConfig.glowingPlankton && level.isDarkOutside();
            emitAt(level, x, surface, z, Math.min(1.0F, HEIGHTS[i] / 8.0F), factor, glow);
        }
    }

    private static boolean isStillWaterfall(ClientLevel level) {
        if (!level.getFluidState(POS).is(FluidTags.WATER)) {
            return false;
        }
        boolean falling = isFalling(level.getFluidState(POS.move(0, 1, 0)));
        POS.move(0, -1, 0);
        return falling;
    }

    private static void emitAt(ClientLevel level, double x, double surface, double z, float strength, float factor,
            boolean glow) {
        int puffs = Math.round((1.0F + strength * 2.0F) * factor);
        for (int i = 0; i < puffs; i++) {
            float angle = RANDOM.nextFloat() * Mth.TWO_PI;
            double outwards = 0.02 + RANDOM.nextDouble() * 0.05 * (0.5 + strength);
            add(new CascadeParticle(level,
                x + Mth.cos(angle) * 0.4, surface + 0.05, z + Mth.sin(angle) * 0.4,
                Mth.cos(angle) * outwards, 0.02 + RANDOM.nextDouble() * 0.04, Mth.sin(angle) * outwards,
                SPRAY.random(RANDOM), 0.4F, 1.1F + strength, 0.55F, 18 + RANDOM.nextInt(14)), glow);
        }
        if (RANDOM.nextFloat() < 0.5F * factor) {
            float angle = RANDOM.nextFloat() * Mth.TWO_PI;
            double outwards = 0.03 + RANDOM.nextDouble() * 0.06;
            double up = 0.1 + RANDOM.nextDouble() * 0.2 * (0.5 + strength);
            WaterEffects.spawnDroplet(level, x + Mth.cos(angle) * 0.3, surface, z + Mth.sin(angle) * 0.3,
                Mth.cos(angle) * outwards, up, Mth.sin(angle) * outwards, glow);
        }
        // Slow, faint mist that drifts away from bigger waterfalls.
        if (RANDOM.nextFloat() < 0.08F * factor * (0.5F + strength)) {
            add(new CascadeParticle(level,
                x + (RANDOM.nextDouble() - 0.5) * 1.5, surface + 0.3, z + (RANDOM.nextDouble() - 0.5) * 1.5,
                (RANDOM.nextDouble() - 0.5) * 0.04, 0.015 + RANDOM.nextDouble() * 0.015,
                (RANDOM.nextDouble() - 0.5) * 0.04,
                SPRAY.random(RANDOM), 1.5F, 3.0F + strength, 0.18F, 50 + RANDOM.nextInt(30)), glow);
        }
    }

    private static void add(CascadeParticle particle, boolean glow) {
        Minecraft.getInstance().particleEngine.add(glow ? particle.glow() : particle);
    }

    private static boolean isFallingWater(BlockState state) {
        FluidState fluid = state.getFluidState();
        return fluid.is(FluidTags.WATER) && isFalling(fluid);
    }

    private static boolean isFalling(FluidState fluid) {
        return !fluid.isSource() && fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING);
    }
}

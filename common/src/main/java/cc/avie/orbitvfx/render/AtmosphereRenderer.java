package cc.avie.orbitvfx.render;

import cc.avie.orbitvfx.Orbit;
import cc.avie.orbitvfx.compat.ShaderPackCompat;
import cc.avie.orbitvfx.config.OrbitConfig;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Draws low lying mist as a single full screen pass on top of the finished world image. The fog amount is the
 * analytic integral of an exponential height fog along each view ray, so the cost is one depth read and a few
 * arithmetic operations per pixel. The pass is skipped entirely whenever the mist would be invisible.
 */
public final class AtmosphereRenderer {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
        .withLocation(Orbit.id("pipeline/atmosphere"))
        .withVertexShader(Orbit.id("post/atmosphere"))
        .withFragmentShader(Orbit.id("post/atmosphere"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Atmosphere", UniformType.UNIFORM_BUFFER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM,
            ColorTargetState.WRITE_COLOR))
        .build();
    private static final Supplier<String> LABEL = () -> "Orbit atmosphere";
    private static final Runnable EXECUTE = AtmosphereRenderer::execute;
    // 5 vec4 + 1 mat4, std140
    private static final int UBO_SIZE = 5 * 16 + 64;

    // The noise in the shader tiles every 2048 blocks, so positions can be wrapped without visible seams.
    private static final double NOISE_PERIOD = 2048.0;
    private static final float BASE_DENSITY = 0.018F;
    private static final float LAYER_FALLOFF = 0.2F;
    private static final float MAX_OPACITY = 0.85F;
    private static final float SKY_DISTANCE = 512.0F;
    private static final int GROUND_SAMPLE_OFFSET = 12;

    private static final BlockPos.MutableBlockPos SAMPLE_POS = new BlockPos.MutableBlockPos();
    private static final Matrix4f INV_VIEW_ROTATION = new Matrix4f();

    private static @Nullable MappableRingBuffer uniforms;
    private static @Nullable CompiledRenderPipeline compiledPipeline;
    private static boolean pipelineFailed;
    private static @Nullable ResourceHandle<RenderTarget> mainTarget;
    private static @Nullable GpuBufferSlice terrainFog;

    private static @Nullable Holder<Biome> lastBiome;
    private static float lastBiomeMist;
    private static float biomeMist;
    private static float groundHeight = Float.NaN;
    private static float exposure;
    private static float rain;

    private AtmosphereRenderer() {
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        Entity camera = client.getCameraEntity();
        if (level == null || camera == null) {
            lastBiome = null;
            biomeMist = 0.0F;
            groundHeight = Float.NaN;
            return;
        }

        int x = camera.getBlockX();
        int z = camera.getBlockZ();
        Holder<Biome> biome = level.getBiome(SAMPLE_POS.set(x, camera.getBlockY(), z));
        if (biome != lastBiome) {
            lastBiome = biome;
            lastBiomeMist = biomeMist(biome);
        }
        biomeMist += (lastBiomeMist - biomeMist) * 0.03F;

        // Averaging a few columns keeps single trees or houses from lifting the mist layer.
        float surface = (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
            + level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + GROUND_SAMPLE_OFFSET, z)
            + level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x - GROUND_SAMPLE_OFFSET, z)
            + level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z + GROUND_SAMPLE_OFFSET)
            + level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z - GROUND_SAMPLE_OFFSET)) / 5.0F;
        groundHeight = Float.isNaN(groundHeight) ? surface : groundHeight + (surface - groundHeight) * 0.05F;

        // Fades the mist out in caves, where the surface is far above the camera.
        exposure = smoothstep(-10.0F, -3.0F, (float) camera.getY() - surface);
        rain = level.getRainLevel(1.0F);
    }

    public static void addPass(FrameGraphBuilder frame, LevelTargetBundle targets, CameraRenderState camera,
            GpuBufferSlice fog, LevelRenderState state) {
        if (!OrbitConfig.enabled || !OrbitConfig.groundFog || pipelineFailed || ShaderPackCompat.isShaderPackActive()) {
            return;
        }
        SkyRenderState sky = state.skyRenderState;
        if (sky.skybox != DimensionType.Skybox.OVERWORLD || camera.fogType != FogType.NONE || Float.isNaN(groundHeight)) {
            return;
        }

        float sunX = -Mth.sin(sky.sunAngle);
        float sunY = Mth.cos(sky.sunAngle);
        float density = BASE_DENSITY * OrbitConfig.groundFogDensity * biomeMist * exposure * timeOfDay(sunX, sunY)
            * (1.0F + rain * 0.75F);
        if (density < 0.0004F) {
            return;
        }

        CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(PIPELINE);
        if (pipeline == null) {
            pipelineFailed = true;
            Orbit.LOGGER.error("Failed to compile the atmosphere shader, ground fog is disabled");
            return;
        }

        writeUniforms(camera, sky, state, sunX, sunY, density);
        compiledPipeline = pipeline;
        terrainFog = fog;
        FramePass pass = frame.addPass("orbitvfx_atmosphere");
        targets.main = pass.readsAndWrites(targets.main);
        mainTarget = targets.main;
        pass.executes(EXECUTE);
    }

    private static void writeUniforms(CameraRenderState camera, SkyRenderState sky, LevelRenderState state,
            float sunX, float sunY, float density) {
        MappableRingBuffer buffer = uniforms;
        if (buffer == null) {
            uniforms = buffer = new MappableRingBuffer(() -> "Orbit atmosphere UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }

        float scatter = 0.0F;
        float scatterR = 0.0F;
        float scatterG = 0.0F;
        float scatterB = 0.0F;
        if (OrbitConfig.lightScattering) {
            // Warm glow towards the sun: the vanilla sunrise colour near the horizon, a faint white during the day.
            float daylight = smoothstep(-0.05F, 0.3F, sunY) * 0.3F;
            Vector4fc sunrise = sky.sunriseAndSunsetColor;
            float sunriseAlpha = sunrise != null ? sunrise.w() : 0.0F;
            scatterR = daylight + (sunrise != null ? sunrise.x() * sunriseAlpha : 0.0F);
            scatterG = daylight * 0.95F + (sunrise != null ? sunrise.y() * sunriseAlpha : 0.0F);
            scatterB = daylight * 0.85F + (sunrise != null ? sunrise.z() * sunriseAlpha : 0.0F);
            scatter = 1.0F - rain * 0.8F;
        }

        Vec3 cameraPos = camera.pos;
        double time = (state.gameTime + state.worldPartialTicks) / 20.0;
        float windX = (float) wrap(time * 0.55);
        float windZ = (float) wrap(time * 0.35);
        INV_VIEW_ROTATION.set(camera.viewRotationMatrix).invert();

        try (GpuBufferSlice.MappedView view = buffer.currentBuffer().map(false, true)) {
            ByteBuffer data = view.data();
            putVec4(data, 0, sunX, sunY, 0.0F, scatter);
            putVec4(data, 16, scatterR, scatterG, scatterB, 0.0F);
            putVec4(data, 32, density, groundHeight + 0.5F - (float) cameraPos.y, LAYER_FALLOFF, MAX_OPACITY);
            putVec4(data, 48, (float) wrap(cameraPos.x), (float) wrap(cameraPos.z), windX, windZ);
            boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
            putVec4(data, 64, zeroToOne ? 1.0F : 0.0F, SKY_DISTANCE, 0.0F, 0.0F);
            INV_VIEW_ROTATION.get(80, data);
        }
    }

    private static void execute() {
        RenderTarget main = mainTarget.get();
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(LABEL, main.getColorTextureView(), Optional.empty())) {
            renderPass.setPipeline(compiledPipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("Fog", terrainFog);
            renderPass.setUniform("Atmosphere", uniforms.currentBuffer());
            renderPass.setUniform("DepthSampler", main.getDepthTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.draw(3, 1, 0, 0);
        }
        uniforms.rotate();
    }

    /**
     * Mist builds up during the second half of the night, peaks around sunrise and burns off as the sun climbs.
     * Evenings get a lighter version of the same curve.
     */
    private static float timeOfDay(float sunX, float sunY) {
        float morning = Mth.lerp(smoothstep(-0.3F, 0.3F, sunX), 0.6F, 1.0F);
        float lowSun = 1.0F - smoothstep(0.0F, 0.55F, sunY);
        float deepNight = smoothstep(-0.05F, -0.4F, sunY);
        return Mth.lerp(lowSun, 0.06F, morning) * (1.0F - deepNight * 0.35F);
    }

    private static float biomeMist(Holder<Biome> biome) {
        if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            return 1.0F;
        }
        if (biome.is(Biomes.PALE_GARDEN) || biome.is(Biomes.DARK_FOREST)) {
            return 0.9F;
        }
        if (biome.is(BiomeTags.IS_RIVER)) {
            return 0.8F;
        }
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return 0.75F;
        }
        if (!biome.value().hasPrecipitation()) {
            return 0.0F;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE)) {
            return 0.6F;
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)) {
            return 0.5F;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
            return 0.45F;
        }
        return 0.35F;
    }

    private static double wrap(double value) {
        return value - Math.floor(value / NOISE_PERIOD) * NOISE_PERIOD;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void putVec4(ByteBuffer data, int offset, float x, float y, float z, float w) {
        data.putFloat(offset, x);
        data.putFloat(offset + 4, y);
        data.putFloat(offset + 8, z);
        data.putFloat(offset + 12, w);
    }
}

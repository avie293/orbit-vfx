package cc.avie.orbitvfx.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * A ring that lies flat on a water surface, widens and fades out.
 */
public final class RippleParticle extends SingleQuadParticle {
    // Particles are only extracted on the render thread, so one shared rotation is enough.
    private static final Quaternionf FLAT = new Quaternionf().rotationX(-Mth.HALF_PI);

    private final TextureAtlasSprite[] frames;
    private final float maxSize;
    private final float startAlpha;

    public RippleParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite[] frames, float maxSize,
            int lifetime, float alpha) {
        super(level, x, y, z, frames[0]);
        this.frames = frames;
        this.maxSize = maxSize;
        this.startAlpha = alpha;
        this.lifetime = lifetime;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.quadSize = maxSize * 0.2F;
        this.setColor(0.85F, 0.92F, 1.0F);
        this.setAlpha(alpha);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float age = (float) this.age / this.lifetime;
        this.setSprite(this.frames[Math.min((int) (age * this.frames.length), this.frames.length - 1)]);
        this.setAlpha(this.startAlpha * (1.0F - age * age));
    }

    @Override
    public float getQuadSize(float a) {
        float age = Math.min((this.age + a) / this.lifetime, 1.0F);
        return this.maxSize * (0.2F + 0.8F * Mth.sqrt(age));
    }

    @Override
    public void extract(QuadParticleRenderState particleTypeRenderState, Camera camera, float partialTickTime) {
        this.extractRotatedQuad(particleTypeRenderState, camera, FLAT, partialTickTime);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
}

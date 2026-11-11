package cc.avie.orbitvfx.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * A soft puff of white water spray that billows up where a waterfall hits water, grows and fades out. Also used
 * with a larger size and lower opacity for the drifting mist around big waterfalls.
 */
public final class CascadeParticle extends SingleQuadParticle {
    private final float startSize;
    private final float endSize;
    private final float startAlpha;

    public CascadeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            TextureAtlasSprite sprite, float startSize, float endSize, float alpha, int lifetime) {
        super(level, x, y, z, sprite);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.startSize = startSize;
        this.endSize = endSize;
        this.startAlpha = alpha;
        this.lifetime = lifetime;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.92F;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.setColor(0.94F, 0.97F, 1.0F);
        this.setAlpha(alpha);
    }

    @Override
    public void tick() {
        super.tick();
        float age = (float) this.age / this.lifetime;
        float remaining = 1.0F - age;
        this.setAlpha(this.startAlpha * remaining * Mth.sqrt(remaining));
    }

    @Override
    public float getQuadSize(float a) {
        float age = Math.min((this.age + a) / this.lifetime, 1.0F);
        return Mth.lerp(Mth.sqrt(age), this.startSize, this.endSize);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
}

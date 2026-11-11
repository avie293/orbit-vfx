package cc.avie.orbitvfx.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * A tiny glowing spark that rises from a flame, drifts and cools down before it fades out. Regular embers go from
 * yellow to a dark red, soul fire embers from pale cyan to a deep blue.
 */
public final class EmberParticle extends SingleQuadParticle {
    private static final float[] FIRE = {1.0F, 0.8F, 0.35F, 0.75F, 0.12F, 0.02F};
    private static final float[] SOUL = {0.5F, 0.95F, 1.0F, 0.05F, 0.35F, 0.65F};

    private final float[] palette;
    private final float wobblePhase;

    public EmberParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            TextureAtlasSprite sprite, boolean soul) {
        super(level, x, y, z, sprite);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.palette = soul ? SOUL : FIRE;
        this.wobblePhase = this.random.nextFloat() * Mth.TWO_PI;
        this.quadSize = 0.05F + this.random.nextFloat() * 0.035F;
        this.lifetime = 30 + this.random.nextInt(35);
        this.friction = 0.96F;
        // Negative gravity: the hot air keeps lifting the spark until it has cooled down.
        this.gravity = -0.06F;
        this.setSize(0.02F, 0.02F);
        this.setColor(this.palette[0], this.palette[1], this.palette[2]);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        float age = (float) this.age / this.lifetime;
        this.xd += Mth.sin(this.age * 0.35F + this.wobblePhase) * 0.0015F;
        this.zd += Mth.cos(this.age * 0.3F + this.wobblePhase) * 0.0015F;
        this.gravity = Mth.lerp(age, -0.06F, 0.03F);
        float cooling = age * age;
        float[] p = this.palette;
        this.setColor(Mth.lerp(cooling, p[0], p[3]), Mth.lerp(cooling, p[1], p[4]), Mth.lerp(cooling, p[2], p[5]));
        this.setAlpha(age > 0.7F ? 1.0F - (age - 0.7F) / 0.3F : 1.0F);
    }

    @Override
    public float getQuadSize(float a) {
        // Shrinks to half its size while it cools down.
        float age = (this.age + a) / this.lifetime;
        return this.quadSize * (1.0F - age * 0.5F);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public int getLightCoords(float a) {
        return LightCoordsUtil.FULL_BRIGHT;
    }
}

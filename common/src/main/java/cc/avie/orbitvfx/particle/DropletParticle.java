package cc.avie.orbitvfx.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.FluidState;

/**
 * A water drop thrown out of a splash or a waterfall. It follows a ballistic arc and leaves a small ring when it
 * falls back into water.
 */
public final class DropletParticle extends SingleQuadParticle {
    // Particles only tick on the render thread.
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    private final boolean leavesRipple;
    private boolean glowing;

    public DropletParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            TextureAtlasSprite sprite, boolean leavesRipple) {
        super(level, x, y, z, sprite);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.leavesRipple = leavesRipple;
        this.gravity = 1.0F;
        this.friction = 0.98F;
        this.lifetime = 60;
        this.quadSize = 0.06F + this.random.nextFloat() * 0.05F;
        this.setSize(0.02F, 0.02F);
        this.setColor(0.85F, 0.93F, 1.0F);
    }

    /**
     * Makes the drop glow like the plankton in warm oceans at night.
     */
    public DropletParticle glow() {
        this.glowing = true;
        this.setColor(WaterEffects.PLANKTON_R, WaterEffects.PLANKTON_G, WaterEffects.PLANKTON_B);
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        if (this.onGround) {
            this.remove();
            return;
        }
        if (this.yd < 0.0) {
            POS.set(this.x, this.y, this.z);
            FluidState fluid = this.level.getFluidState(POS);
            if (fluid.is(FluidTags.WATER)) {
                double surface = POS.getY() + fluid.getHeight(this.level, POS);
                if (this.y <= surface) {
                    if (this.leavesRipple) {
                        WaterEffects.spawnRipple(this.level, this.x, surface + 0.01, this.z, 0.25F, 8, 0.4F,
                            this.glowing);
                    }
                    this.remove();
                }
            }
        }
    }

    @Override
    public int getLightCoords(float a) {
        return this.glowing ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(a);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
}

package cc.avie.orbitvfx;

import cc.avie.orbitvfx.compat.ShaderPackCompat;
import cc.avie.orbitvfx.config.OrbitConfig;
import cc.avie.orbitvfx.render.AtmosphereRenderer;
import net.minecraft.client.Minecraft;

/**
 * Loader independent client setup, called from the loader entrypoints.
 */
public final class OrbitClient {
    private OrbitClient() {
    }

    public static void init(String modId) {
        OrbitConfig.init(modId);
    }

    public static void tick(Minecraft client) {
        ShaderPackCompat.tick();
        AtmosphereRenderer.tick(client);
    }
}

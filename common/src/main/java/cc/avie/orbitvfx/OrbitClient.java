package cc.avie.orbitvfx;

import cc.avie.orbitvfx.config.OrbitConfig;

/**
 * Loader independent client setup, called from the loader entrypoints.
 */
public final class OrbitClient {
    private OrbitClient() {
    }

    public static void init(String modId) {
        OrbitConfig.init(modId);
    }
}

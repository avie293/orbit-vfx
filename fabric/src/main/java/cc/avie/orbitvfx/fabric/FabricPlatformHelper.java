package cc.avie.orbitvfx.fabric;

import cc.avie.orbitvfx.platform.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric implementation of {@link PlatformHelper}.
 */
public final class FabricPlatformHelper implements PlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}

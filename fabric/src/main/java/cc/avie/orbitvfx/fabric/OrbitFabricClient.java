package cc.avie.orbitvfx.fabric;

import cc.avie.orbitvfx.Orbit;
import cc.avie.orbitvfx.OrbitClient;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entrypoint of Orbit.
 */
public final class OrbitFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OrbitClient.init(Orbit.MOD_ID);
    }
}

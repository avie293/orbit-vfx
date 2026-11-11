package cc.avie.orbitvfx.fabric;

import cc.avie.orbitvfx.Orbit;
import cc.avie.orbitvfx.OrbitClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric client entrypoint of Orbit.
 */
public final class OrbitFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OrbitClient.init(Orbit.MOD_ID);
        ClientTickEvents.END_CLIENT_TICK.register(OrbitClient::tick);
    }
}

package cc.avie.orbitvfx.neoforge;

import cc.avie.orbitvfx.Orbit;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge client entrypoint of Orbit. Not loaded on dedicated servers.
 */
@Mod(value = Orbit.MOD_ID, dist = Dist.CLIENT)
public final class OrbitNeoForgeClient {
    public OrbitNeoForgeClient(IEventBus modBus, ModContainer container) {
    }
}

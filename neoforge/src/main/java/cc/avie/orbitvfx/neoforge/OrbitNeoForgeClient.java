package cc.avie.orbitvfx.neoforge;

import cc.avie.orbitvfx.Orbit;
import cc.avie.orbitvfx.OrbitClient;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client entrypoint of Orbit. Not loaded on dedicated servers.
 */
@Mod(value = Orbit.MOD_ID, dist = Dist.CLIENT)
public final class OrbitNeoForgeClient {
    public OrbitNeoForgeClient(IEventBus modBus, ModContainer container) {
        OrbitClient.init(container.getModId());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> OrbitClient.tick(Minecraft.getInstance()));
    }
}

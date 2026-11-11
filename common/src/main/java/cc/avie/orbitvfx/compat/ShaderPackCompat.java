package cc.avie.orbitvfx.compat;

import cc.avie.orbitvfx.Orbit;
import cc.avie.orbitvfx.platform.Services;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Tracks whether an Iris shader pack is active. Shader packs replace the vanilla world rendering, so Orbit's own
 * shader passes would draw over their output and have to step aside.
 */
public final class ShaderPackCompat {
    private static @Nullable MethodHandle isShaderPackInUse = findIrisApi();
    private static boolean shaderPackActive;

    private ShaderPackCompat() {
    }

    public static boolean isShaderPackActive() {
        return shaderPackActive;
    }

    public static void tick() {
        MethodHandle handle = isShaderPackInUse;
        if (handle == null) {
            return;
        }
        try {
            shaderPackActive = (boolean) handle.invokeExact();
        } catch (Throwable t) {
            Orbit.LOGGER.error("Failed to query the Iris shader pack state", t);
            isShaderPackInUse = null;
            shaderPackActive = false;
        }
    }

    private static @Nullable MethodHandle findIrisApi() {
        if (!Services.PLATFORM.isModLoaded("iris")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                .bindTo(instance);
        } catch (ReflectiveOperationException e) {
            Orbit.LOGGER.warn("Iris is installed but its API could not be accessed, shader effects stay on", e);
            return null;
        }
    }
}

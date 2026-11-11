package cc.avie.orbitvfx;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants of Orbit.
 */
public final class Orbit {
    public static final String MOD_ID = "orbitvfx";
    public static final String MOD_NAME = "Orbit";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private Orbit() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

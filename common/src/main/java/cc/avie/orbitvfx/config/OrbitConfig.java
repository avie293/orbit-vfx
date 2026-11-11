package cc.avie.orbitvfx.config;

import cc.avie.tabbylib.api.TabbyConfig;
import cc.avie.tabbylib.api.option.BooleanOption;
import cc.avie.tabbylib.api.option.DoubleOption;
import cc.avie.tabbylib.api.option.Options;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class OrbitConfig {
    public static final BooleanOption ENABLED = Options.toggle("enabled", true);

    public static final BooleanOption GROUND_FOG = Options.toggle("groundFog", true)
        .dependsOn(ENABLED);
    public static final DoubleOption GROUND_FOG_DENSITY = Options.decimal("groundFogDensity", 1.0)
        .slider(0.25, 2.0, 0.05)
        .format(value -> Component.literal(String.format(Locale.ROOT, "%.0f%%", value * 100.0)))
        .dependsOn(GROUND_FOG);
    public static final BooleanOption LIGHT_SCATTERING = Options.toggle("lightScattering", true)
        .dependsOn(GROUND_FOG);

    // Plain copies of the option values, read by the render code every frame without boxing.
    public static boolean enabled;
    public static boolean groundFog;
    public static float groundFogDensity;
    public static boolean lightScattering;

    private static TabbyConfig config;

    private OrbitConfig() {
    }

    public static void init(String modId) {
        config = TabbyConfig.builder(modId)
            .fileName("orbitvfx")
            .onSave(OrbitConfig::apply)
            .category("general", category -> category.add(ENABLED))
            .category("atmosphere", category -> category
                .section("groundFog", section -> section.add(GROUND_FOG, GROUND_FOG_DENSITY, LIGHT_SCATTERING)))
            .build();
        apply();
    }

    public static TabbyConfig get() {
        return config;
    }

    private static void apply() {
        enabled = ENABLED.get();
        groundFog = GROUND_FOG.get();
        groundFogDensity = GROUND_FOG_DENSITY.get().floatValue();
        lightScattering = LIGHT_SCATTERING.get();
    }
}

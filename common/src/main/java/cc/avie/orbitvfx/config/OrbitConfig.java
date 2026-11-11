package cc.avie.orbitvfx.config;

import cc.avie.tabbylib.api.TabbyConfig;
import cc.avie.tabbylib.api.option.BooleanOption;
import cc.avie.tabbylib.api.option.Options;

public final class OrbitConfig {
    public static final BooleanOption ENABLED = Options.toggle("enabled", true);

    // Plain copies of the option values, read by the render code every frame without boxing.
    public static boolean enabled;

    private static TabbyConfig config;

    private OrbitConfig() {
    }

    public static void init(String modId) {
        config = TabbyConfig.builder(modId)
            .fileName("orbitvfx")
            .onSave(OrbitConfig::apply)
            .category("general", category -> category.add(ENABLED))
            .build();
        apply();
    }

    public static TabbyConfig get() {
        return config;
    }

    private static void apply() {
        enabled = ENABLED.get();
    }
}

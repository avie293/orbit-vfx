package cc.avie.orbitvfx.platform;

import java.util.ServiceLoader;

/**
 * Loads the loader specific service implementations declared in {@code META-INF/services}.
 */
public final class Services {
    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);

    private Services() {
    }

    private static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation found for " + type.getName()));
    }
}

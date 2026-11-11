package cc.avie.orbitvfx.platform;

/**
 * Loader specific functionality that common code can use. Each loader provides its own implementation.
 */
public interface PlatformHelper {
    /**
     * @return the name of the current mod loader, for example {@code "Fabric"}
     */
    String getPlatformName();

    /**
     * @param modId the id of the mod to check
     * @return {@code true} if a mod with the given id is loaded
     */
    boolean isModLoaded(String modId);
}

package xyz.geik.farmer.modules.autoseller.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import xyz.geik.farmer.api.FarmerCompatibilityAPI;
import xyz.geik.farmer.api.handlers.FarmerItemCollectEvent;
import xyz.geik.farmer.api.handlers.FarmerStorageFullEvent;

/**
 * Verifies every Paper and Farmer surface used by AutoSeller before listeners
 * or scheduled work are published.
 */
public final class PaperPlatform {

    private static final String[] REQUIRED_CLASSES = {
            "io.papermc.paper.threadedregions.scheduler.RegionScheduler",
            "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler",
            "io.papermc.paper.threadedregions.scheduler.AsyncScheduler",
            "io.papermc.paper.threadedregions.scheduler.EntityScheduler"
    };

    private PaperPlatform() {}

    public static boolean isSupported() {
        try {
            FarmerCompatibilityAPI.requireModuleApi(2);
            for (String className : REQUIRED_CLASSES)
                Class.forName(className, false, PaperPlatform.class.getClassLoader());
            Bukkit.class.getMethod("getRegionScheduler");
            Bukkit.class.getMethod("getGlobalRegionScheduler");
            Bukkit.class.getMethod("getAsyncScheduler");
            Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
            Entity.class.getMethod("getScheduler");
            return Cancellable.class.isAssignableFrom(FarmerStorageFullEvent.class)
                    && Cancellable.class.isAssignableFrom(FarmerItemCollectEvent.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
